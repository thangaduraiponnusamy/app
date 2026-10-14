package com.mybook.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class ExpenseType { EXPENSE, SETTLEMENT, INCOME }

enum class CategoryKind { EXPENSE, INCOME }

enum class RepeatRule(val label: String) {
    NONE("Once"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    fun next(date: LocalDate): LocalDate = when (this) {
        NONE -> date
        WEEKLY -> date.plusWeeks(1)
        MONTHLY -> date.plusMonths(1)
        YEARLY -> date.plusYears(1)
    }
}

/** A shared expense book, e.g. "Home" or "Goa trip". */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "members", indices = [Index("bookId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
)

/** Shared by all books and editable from the admin page. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: CategoryKind,
    val sortOrder: Int,
)

@Entity(tableName = "expenses", indices = [Index("bookId", "date"), Index("categoryId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val amountMinor: Long,
    val categoryId: String,
    val date: LocalDate,
    val paidBy: String,
    val splitWith: List<String>,
    val type: ExpenseType,
    val note: String,
    val receiptPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** An upcoming payment that shows up under the bell icon when it is due. */
@Entity(tableName = "reminders", indices = [Index("bookId", "dueDate")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val amountMinor: Long,
    val categoryId: String,
    val dueDate: LocalDate,
    val repeat: RepeatRule,
)
