package com.mybook.app.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.mybook.app.domain.Category
import com.mybook.app.domain.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val db: MyBookDatabase,
    private val prefs: Prefs,
    private val receiptsDir: File,
) {
    private val books get() = db.bookDao()
    private val members get() = db.memberDao()
    private val expenses get() = db.expenseDao()

    fun observeBooks(): Flow<List<BookEntity>> = books.observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = books.observe(id)
    fun observeMembers(bookId: String): Flow<List<MemberEntity>> = members.observe(bookId)
    fun observeExpenses(bookId: String): Flow<List<ExpenseEntity>> = expenses.observe(bookId)
    suspend fun getBook(id: String): BookEntity? = books.get(id)
    suspend fun listMembers(bookId: String): List<MemberEntity> = members.list(bookId)
    suspend fun getExpense(id: String): ExpenseEntity? = expenses.get(id)

    suspend fun createBook(name: String, currency: String, memberNames: List<String>) {
        val bookId = UUID.randomUUID().toString()
        db.withTransaction {
            books.upsert(BookEntity(bookId, name, currency, System.currentTimeMillis()))
            memberNames.forEach { members.upsert(MemberEntity(UUID.randomUUID().toString(), bookId, it)) }
        }
        prefs.setActiveBook(bookId)
    }

    suspend fun deleteBook(id: String) {
        val receipts = expenses.list(id).mapNotNull { it.receiptPath }
        db.withTransaction {
            expenses.deleteForBook(id)
            members.deleteForBook(id)
            books.delete(id)
        }
        deleteFiles(receipts)
        if (prefs.activeBookId.value == id) prefs.setActiveBook(null)
    }

    suspend fun addMember(bookId: String, name: String) {
        require(members.list(bookId).none { it.name.equals(name, ignoreCase = true) }) { "$name is already a member" }
        members.upsert(MemberEntity(UUID.randomUUID().toString(), bookId, name))
    }

    suspend fun removeMember(member: MemberEntity) {
        val used = expenses.list(member.bookId).any { it.paidBy == member.id || member.id in it.splitWith }
        require(!used) { "${member.name} has expenses in this book and can't be removed" }
        members.delete(member.id)
    }

    suspend fun saveExpense(expense: ExpenseEntity) {
        val previous = expenses.get(expense.id)
        expenses.upsert(expense.copy(updatedAt = System.currentTimeMillis()))
        val oldReceipt = previous?.receiptPath
        if (oldReceipt != null && oldReceipt != expense.receiptPath) deleteFiles(listOf(oldReceipt))
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenses.delete(expense.id)
        expense.receiptPath?.let { deleteFiles(listOf(it)) }
    }

    suspend fun settle(bookId: String, from: String, to: String, amountMinor: Long) {
        val now = System.currentTimeMillis()
        expenses.upsert(
            ExpenseEntity(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                title = "Settlement",
                amountMinor = amountMinor,
                category = Category.OTHER.name,
                date = LocalDate.now(),
                paidBy = from,
                splitWith = listOf(to),
                type = ExpenseType.SETTLEMENT,
                note = "",
                receiptPath = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Copies a picked image into private app storage. */
    suspend fun importReceipt(uri: Uri): String = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        val extension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
        receiptsDir.mkdirs()
        val target = File(receiptsDir, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot read the selected image")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target.absolutePath
    }

    suspend fun exportCsv(bookId: String, uri: Uri) {
        val book = books.get(bookId) ?: return
        val names = members.list(bookId).associate { it.id to it.name }
        val all = expenses.list(bookId)
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open the export file")
            output.use { CsvExporter.write(it, all, book.currency, names) }
        }
    }

    private suspend fun deleteFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}
