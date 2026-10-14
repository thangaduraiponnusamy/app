package com.mybook.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class ExpenseType { EXPENSE, SETTLEMENT }

/** A shared expense book, e.g. "Home" or "Goa trip". */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val createdAt: Long,
)

@Entity(tableName = "members", indices = [Index("bookId")])
data class MemberEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val name: String,
)

@Entity(tableName = "expenses", indices = [Index("bookId", "date")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val amountMinor: Long,
    val category: String,
    val date: LocalDate,
    val paidBy: String,
    val splitWith: List<String>,
    val type: ExpenseType,
    val note: String,
    val receiptPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
