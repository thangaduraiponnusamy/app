package com.mybook.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE bookId = :bookId ORDER BY name COLLATE NOCASE")
    fun observe(bookId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE bookId = :bookId ORDER BY name COLLATE NOCASE")
    suspend fun list(bookId: String): List<MemberEntity>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM members WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun list(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE bookId = :bookId ORDER BY date DESC, createdAt DESC")
    fun observe(bookId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE bookId = :bookId ORDER BY date")
    suspend fun list(bookId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM expenses WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE bookId = :bookId ORDER BY dueDate")
    fun observe(bookId: String): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders WHERE categoryId = :categoryId")
    suspend fun countForCategory(categoryId: String): Int

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
