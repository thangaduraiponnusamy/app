package com.aurum.expense.data.repo

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.local.AurumDatabase
import com.aurum.expense.data.local.ExpenseEntity
import com.aurum.expense.data.local.ExpenseType
import com.aurum.expense.data.sync.SyncScheduler
import com.aurum.expense.domain.Category
import com.aurum.expense.domain.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class ExpenseRepository(
    private val context: Context,
    private val db: AurumDatabase,
    private val session: SessionStore,
    private val receiptsDir: File,
) {
    private val dao get() = db.expenseDao()

    fun observeActive(tenantId: String): Flow<List<ExpenseEntity>> = dao.observeActive(tenantId)
    fun observePendingCount(tenantId: String): Flow<Int> = dao.observePendingCount(tenantId)
    suspend fun get(id: String): ExpenseEntity? = dao.get(id)

    suspend fun save(expense: ExpenseEntity) {
        val me = session.requireEmail()
        val previous = dao.get(expense.id)
        val updatedAt = maxOf(System.currentTimeMillis(), (previous?.updatedAt ?: 0L) + 1)
        dao.upsert(expense.copy(updatedBy = me, updatedAt = updatedAt, dirty = true))
        SyncScheduler.requestSync(context)
    }

    suspend fun delete(expense: ExpenseEntity) = save(expense.copy(deleted = true))

    suspend fun settle(tenantId: String, from: String, to: String, amountMinor: Long) {
        val me = session.requireEmail()
        val now = System.currentTimeMillis()
        save(
            ExpenseEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                title = "Settlement",
                amountMinor = amountMinor,
                category = Category.OTHER.name,
                date = LocalDate.now(),
                paidBy = from,
                splitWith = listOf(to),
                type = ExpenseType.SETTLEMENT,
                note = "",
                receiptFileId = null,
                receiptLocalPath = null,
                createdBy = me,
                createdAt = now,
                updatedBy = me,
                updatedAt = now,
                deleted = false,
                dirty = true,
            ),
        )
    }

    /** Copies a picked image into app storage so it can be uploaded later by the background sync. */
    suspend fun importReceipt(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        val extension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
        receiptsDir.mkdirs()
        val target = File(receiptsDir, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read the selected image")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target.absolutePath
    }

    suspend fun exportCsv(tenantId: String, uri: Uri, currency: String, names: Map<String, String>) {
        val expenses = dao.listActive(tenantId)
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open the export file")
            output.use { CsvExporter.write(it, expenses, currency, names) }
        }
    }
}
