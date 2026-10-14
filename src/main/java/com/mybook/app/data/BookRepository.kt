package com.mybook.app.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.withTransaction
import com.mybook.app.domain.CsvExporter
import com.mybook.app.domain.DefaultCategories
import com.mybook.app.domain.MAX_NAME
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
    private val categories get() = db.categoryDao()
    private val expenses get() = db.expenseDao()
    private val reminders get() = db.reminderDao()

    fun observeBooks(): Flow<List<BookEntity>> = books.observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = books.observe(id)
    fun observeMembers(bookId: String): Flow<List<MemberEntity>> = members.observe(bookId)
    fun observeCategories(): Flow<List<CategoryEntity>> = categories.observeAll()
    fun observeExpenses(bookId: String): Flow<List<ExpenseEntity>> = expenses.observe(bookId)
    fun observeReminders(bookId: String): Flow<List<ReminderEntity>> = reminders.observe(bookId)
    suspend fun getBook(id: String): BookEntity? = books.get(id)
    suspend fun hasBooks(): Boolean = books.count() > 0
    suspend fun listMembers(bookId: String): List<MemberEntity> = members.list(bookId)
    suspend fun listCategories(): List<CategoryEntity> = categories.list()
    suspend fun getExpense(id: String): ExpenseEntity? = expenses.get(id)

    suspend fun ensureDefaultCategories() {
        if (categories.count() == 0) categories.upsertAll(DefaultCategories.all())
    }

    suspend fun createBook(name: String, memberNames: List<String>) {
        val bookId = UUID.randomUUID().toString()
        db.withTransaction {
            books.upsert(BookEntity(bookId, name, System.currentTimeMillis()))
            memberNames.forEach { members.upsert(MemberEntity(UUID.randomUUID().toString(), bookId, it)) }
        }
        prefs.setActiveBook(bookId)
    }

    suspend fun deleteBook(id: String) {
        val receipts = expenses.list(id).mapNotNull { it.receiptPath }
        db.withTransaction {
            expenses.deleteForBook(id)
            reminders.deleteForBook(id)
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
        require(!used) { "${member.name} has entries in this book and can't be removed" }
        members.delete(member.id)
    }

    /** Creates a category (when [existing] is null) or updates it; names must be unique per kind. */
    suspend fun upsertCategory(existing: CategoryEntity?, kind: CategoryKind, name: String, emoji: String, color: Long): CategoryEntity {
        val cleanName = name.trim().take(MAX_NAME)
        require(cleanName.isNotEmpty()) { "Enter a category name" }
        val all = categories.list()
        require(all.none { it.kind == kind && it.id != existing?.id && it.name.equals(cleanName, ignoreCase = true) }) {
            "$cleanName already exists"
        }
        val order = existing?.sortOrder ?: ((all.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        val category = CategoryEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = cleanName,
            emoji = emoji.trim().ifEmpty { "📌" },
            color = color,
            kind = kind,
            sortOrder = order,
        )
        categories.upsert(category)
        return category
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        val used = expenses.countForCategory(category.id) + reminders.countForCategory(category.id)
        require(used == 0) { "${category.name} is used by $used entries and can't be deleted" }
        categories.delete(category.id)
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
                categoryId = "",
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

    suspend fun saveReminder(reminder: ReminderEntity) = reminders.upsert(reminder)

    suspend fun deleteReminder(reminder: ReminderEntity) = reminders.delete(reminder.id)

    /** Records the reminder as an expense paid by the first member, then moves it to its next due date. */
    suspend fun payReminder(reminder: ReminderEntity) {
        val payer = members.list(reminder.bookId).firstOrNull()?.id.orEmpty()
        val now = System.currentTimeMillis()
        db.withTransaction {
            expenses.upsert(
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = reminder.bookId,
                    title = reminder.title,
                    amountMinor = reminder.amountMinor,
                    categoryId = reminder.categoryId,
                    date = LocalDate.now(),
                    paidBy = payer,
                    splitWith = listOf(payer),
                    type = ExpenseType.EXPENSE,
                    note = "Paid from reminder",
                    receiptPath = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (reminder.repeat == RepeatRule.NONE) {
                reminders.delete(reminder.id)
            } else {
                reminders.upsert(reminder.copy(dueDate = reminder.repeat.next(reminder.dueDate)))
            }
        }
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
        val names = members.list(bookId).associate { it.id to it.name }
        val categoryNames = categories.list().associate { it.id to it.name }
        val all = expenses.list(bookId)
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open the export file")
            output.use { CsvExporter.write(it, all, names, categoryNames) }
        }
    }

    private suspend fun deleteFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}
