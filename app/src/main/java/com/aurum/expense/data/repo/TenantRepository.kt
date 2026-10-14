package com.aurum.expense.data.repo

import androidx.room.withTransaction
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.drive.AurumJson
import com.aurum.expense.data.drive.DriveApi
import com.aurum.expense.data.drive.DriveFile
import com.aurum.expense.data.drive.DriveLayout
import com.aurum.expense.data.local.AurumDatabase
import com.aurum.expense.data.local.MemberEntity
import com.aurum.expense.data.local.TenantEntity
import com.aurum.expense.data.sync.SyncManager
import com.aurum.expense.data.sync.TenantMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

class TenantRepository(
    private val db: AurumDatabase,
    private val drive: DriveApi,
    private val session: SessionStore,
    private val sync: SyncManager,
    private val receiptsDir: File,
) {
    fun observeTenants(): Flow<List<TenantEntity>> = db.tenantDao().observeAll()
    fun observeTenant(id: String): Flow<TenantEntity?> = db.tenantDao().observe(id)
    fun observeMembers(tenantId: String): Flow<List<MemberEntity>> = db.memberDao().observe(tenantId)
    suspend fun get(id: String): TenantEntity? = db.tenantDao().get(id)
    suspend fun listMembers(tenantId: String): List<MemberEntity> = db.memberDao().list(tenantId)

    suspend fun create(name: String, currency: String) {
        val me = session.requireEmail()
        val folder = drive.createFolder(
            DriveLayout.FOLDER_PREFIX + name,
            null,
            mapOf(DriveLayout.PROP_TENANT to "true"),
        )
        val receipts = drive.createFolder(
            DriveLayout.RECEIPTS_FOLDER,
            folder.id,
            mapOf(DriveLayout.PROP_KIND to DriveLayout.KIND_RECEIPTS),
        )
        val meta = TenantMeta(name = name, currency = currency, ownerEmail = me, createdAt = System.currentTimeMillis())
        drive.createFile(
            DriveLayout.TENANT_FILE,
            folder.id,
            DriveLayout.MIME_JSON,
            AurumJson.encodeToString(meta).toByteArray(),
            mapOf(DriveLayout.PROP_KIND to DriveLayout.KIND_META),
        )
        db.tenantDao().upsert(TenantEntity(folder.id, name, currency, me, receipts.id))
        sync.sync(folder.id)
        session.setActiveTenant(folder.id)
    }

    /** Group folders visible to the user (own or shared with them) that are not joined on this device yet. */
    suspend fun findJoinable(): List<DriveFile> {
        val joined = db.tenantDao().list().map { it.id }.toSet()
        val query = "mimeType='${DriveLayout.MIME_FOLDER}' and " +
            "properties has { key='${DriveLayout.PROP_TENANT}' and value='true' } and trashed=false"
        return drive.listFiles(query).filter { it.id !in joined }
    }

    suspend fun join(folder: DriveFile) {
        db.tenantDao().upsert(
            TenantEntity(
                id = folder.id,
                name = folder.name.removePrefix(DriveLayout.FOLDER_PREFIX),
                currency = "EUR",
                ownerEmail = folder.owners?.firstOrNull()?.emailAddress?.lowercase().orEmpty(),
                receiptsFolderId = null,
            ),
        )
        sync.sync(folder.id)
        session.setActiveTenant(folder.id)
    }

    suspend fun invite(tenantId: String, email: String) {
        drive.addWriter(tenantId, email)
        sync.refreshMembers(tenantId)
    }

    suspend fun removeMember(tenantId: String, member: MemberEntity) {
        drive.deletePermission(tenantId, member.permissionId)
        sync.refreshMembers(tenantId)
    }

    /** Removes the group from this device only; the Drive folder is untouched. */
    suspend fun leave(tenantId: String) {
        db.withTransaction {
            db.expenseDao().deleteForTenant(tenantId)
            db.memberDao().deleteForTenant(tenantId)
            db.syncFileDao().deleteForTenant(tenantId)
            db.tenantDao().delete(tenantId)
        }
        if (session.current.activeTenantId == tenantId) session.setActiveTenant(null)
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            receiptsDir.deleteRecursively()
        }
        session.signOut()
    }
}
