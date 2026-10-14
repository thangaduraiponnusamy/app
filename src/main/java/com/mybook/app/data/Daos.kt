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
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE bookId = :bookId ORDER BY date DESC, createdAt DESC")
    fun observe(bookId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE bookId = :bookId ORDER BY date")
    suspend fun list(bookId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM expenses WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
