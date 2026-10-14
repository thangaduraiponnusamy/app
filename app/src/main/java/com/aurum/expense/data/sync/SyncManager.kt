package com.aurum.expense.data.sync

import android.util.Log
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.drive.AurumJson
import com.aurum.expense.data.drive.DriveApi
import com.aurum.expense.data.drive.DriveException
import com.aurum.expense.data.drive.DriveFile
import com.aurum.expense.data.drive.DriveLayout
import com.aurum.expense.data.local.AurumDatabase
import com.aurum.expense.data.local.MemberEntity
import com.aurum.expense.data.local.SyncFileEntity
import com.aurum.expense.data.local.TenantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Two-way sync between the local Room database and a group's shared Drive folder.
 * Conflicts are resolved per expense with last-write-wins on updatedAt.
 */
class SyncManager(
    private val db: AurumDatabase,
    private val drive: DriveApi,
    private val session: SessionStore,
) {
    private val mutex = Mutex()
    private val tenantDao get() = db.tenantDao()
    private val memberDao get() = db.memberDao()
    private val expenseDao get() = db.expenseDao()
    private val syncDao get() = db.syncFileDao()

    suspend fun syncAll() {
        var firstError: Exception? = null
        for (tenant in tenantDao.list()) {
            try {
                sync(tenant.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        firstError?.let { throw it }
    }

    suspend fun sync(tenantId: String): Unit = mutex.withLock {
        val me = session.requireEmail()
        if (tenantDao.get(tenantId) == null) return@withLock
        refreshMembersLocked(tenantId)
        val children = drive.listFiles("'${DriveLayout.escape(tenantId)}' in parents and trashed=false")
        val tenant = refreshMeta(tenantDao.get(tenantId) ?: return@withLock, children)
        pull(tenantId, children)
        uploadReceipts(tenant)
        push(tenantId, me, children)
    }

    suspend fun refreshMembers(tenantId: String): Unit = mutex.withLock { refreshMembersLocked(tenantId) }

    private suspend fun refreshMembersLocked(tenantId: String) {
        val permissions = try {
            drive.listPermissions(tenantId)
        } catch (e: DriveException) {
            if (e.code == 403) return else throw e
        }
        val members = permissions
            .filter { it.type == "user" && !it.emailAddress.isNullOrBlank() }
            .map {
                val email = it.emailAddress!!.lowercase()
                MemberEntity(tenantId, email, it.displayName?.takeIf { name -> name.isNotBlank() } ?: email, it.role, it.id)
            }
        if (members.isEmpty()) return
        db.withTransaction {
            memberDao.deleteForTenant(tenantId)
            memberDao.insertAll(members)
            val owner = members.firstOrNull { it.role == "owner" }
            val tenant = tenantDao.get(tenantId)
            if (owner != null && tenant != null && tenant.ownerEmail != owner.email) {
                tenantDao.upsert(tenant.copy(ownerEmail = owner.email))
            }
        }
    }

    private suspend fun refreshMeta(tenant: TenantEntity, children: List<DriveFile>): TenantEntity {
        var updated = tenant
        children.firstOrNull { it.mimeType == DriveLayout.MIME_FOLDER && it.name == DriveLayout.RECEIPTS_FOLDER }
            ?.let { updated = updated.copy(receiptsFolderId = it.id) }

        val metaFile = children.firstOrNull { it.name == DriveLayout.TENANT_FILE && it.mimeType != DriveLayout.MIME_FOLDER }
        val metaChanged = metaFile != null && syncDao.get(metaFile.id)?.modifiedTime != metaFile.modifiedTime
        if (metaFile != null && metaChanged) {
            val meta = AurumJson.decodeFromString<TenantMeta>(drive.downloadText(metaFile.id))
            updated = updated.copy(name = meta.name.take(MAX_TITLE), currency = meta.currency)
        }
        if (updated != tenant) tenantDao.upsert(updated)
        if (metaFile != null && metaChanged) {
            syncDao.upsert(SyncFileEntity(metaFile.id, tenant.id, metaFile.modifiedTime.orEmpty()))
        }
        return updated
    }

    private suspend fun pull(tenantId: String, children: List<DriveFile>) {
        for (file in children.filter { it.prop(DriveLayout.PROP_KIND) == DriveLayout.KIND_EXPENSES }) {
            if (syncDao.get(file.id)?.modifiedTime == file.modifiedTime) continue
            val content = try {
                AurumJson.decodeFromString<ExpenseFile>(drive.downloadText(file.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: kotlinx.serialization.SerializationException) {
                Log.w(TAG, "Skipping unreadable file ${file.name}", e)
                continue
            }
            db.withTransaction {
                content.records.forEach { merge(tenantId, it) }
                syncDao.upsert(SyncFileEntity(file.id, tenantId, file.modifiedTime.orEmpty()))
            }
        }
    }

    private suspend fun merge(tenantId: String, record: ExpenseRecord) {
        val incoming = record.toEntityOrNull(tenantId) ?: return
        val local = expenseDao.get(incoming.id)
        if (local != null && local.tenantId != tenantId) return
        if (local == null || incoming.updatedAt > local.updatedAt) expenseDao.upsert(incoming)
    }

    private suspend fun uploadReceipts(tenant: TenantEntity) {
        val pending = expenseDao.pendingReceipts(tenant.id)
        if (pending.isEmpty()) return
        val folderId = tenant.receiptsFolderId ?: drive.createFolder(
            DriveLayout.RECEIPTS_FOLDER,
            tenant.id,
            mapOf(DriveLayout.PROP_KIND to DriveLayout.KIND_RECEIPTS),
        ).id.also { tenantDao.upsert(tenant.copy(receiptsFolderId = it)) }

        for (expense in pending) {
            val file = File(expense.receiptLocalPath ?: continue)
            if (!file.exists()) {
                expenseDao.attachReceipt(expense.id, expense.receiptFileId)
                continue
            }
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val uploaded = drive.createFile(
                name = "${expense.date}_${expense.id}.${file.extension}",
                parentId = folderId,
                mimeType = mime,
                content = bytes,
                properties = mapOf(DriveLayout.PROP_KIND to DriveLayout.KIND_RECEIPT),
            )
            expenseDao.attachReceipt(expense.id, uploaded.id)
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    private suspend fun push(tenantId: String, me: String, children: List<DriveFile>) {
        val dirty = expenseDao.dirty(tenantId)
        if (dirty.isEmpty()) return
        for (year in dirty.map { it.date.year }.toSortedSet()) {
            val records = expenseDao
                .authoredInRange(tenantId, me, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .map { it.toRecord() }
            val payload = AurumJson.encodeToString(ExpenseFile(tenantId = tenantId, year = year, member = me, records = records))
                .toByteArray()
            val existing = children.firstOrNull {
                it.prop(DriveLayout.PROP_KIND) == DriveLayout.KIND_EXPENSES &&
                    it.prop(DriveLayout.PROP_MEMBER) == me &&
                    it.prop(DriveLayout.PROP_YEAR) == year.toString()
            }
            val saved = if (existing != null) {
                drive.updateContent(existing.id, DriveLayout.MIME_JSON, payload)
            } else {
                drive.createFile(
                    name = DriveLayout.expenseFileName(year, me),
                    parentId = tenantId,
                    mimeType = DriveLayout.MIME_JSON,
                    content = payload,
                    properties = mapOf(
                        DriveLayout.PROP_KIND to DriveLayout.KIND_EXPENSES,
                        DriveLayout.PROP_MEMBER to me,
                        DriveLayout.PROP_YEAR to year.toString(),
                    ),
                )
            }
            syncDao.upsert(SyncFileEntity(saved.id, tenantId, saved.modifiedTime.orEmpty()))
        }
        dirty.forEach { expenseDao.markClean(it.id, it.updatedAt) }
    }

    private companion object {
        const val TAG = "AurumSync"
    }
}
