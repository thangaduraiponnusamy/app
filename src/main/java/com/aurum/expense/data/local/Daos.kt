package com.aurum.expense.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants")
    suspend fun list(): List<TenantEntity>

    @Query("SELECT * FROM tenants WHERE id = :id")
    fun observe(id: String): Flow<TenantEntity?>

    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun get(id: String): TenantEntity?

    @Upsert
    suspend fun upsert(tenant: TenantEntity)

    @Query("DELETE FROM tenants WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE tenantId = :tenantId ORDER BY displayName COLLATE NOCASE")
    fun observe(tenantId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE tenantId = :tenantId ORDER BY displayName COLLATE NOCASE")
    suspend fun list(tenantId: String): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE tenantId = :tenantId")
    suspend fun deleteForTenant(tenantId: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tenantId = :tenantId AND deleted = 0 ORDER BY date DESC, createdAt DESC")
    fun observeActive(tenantId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tenantId = :tenantId AND deleted = 0 ORDER BY date")
    suspend fun listActive(tenantId: String): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses WHERE tenantId = :tenantId AND dirty = 1")
    fun observePendingCount(tenantId: String): Flow<Int>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE tenantId = :tenantId AND dirty = 1")
    suspend fun dirty(tenantId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE tenantId = :tenantId AND updatedBy = :member AND date BETWEEN :from AND :to")
    suspend fun authoredInRange(tenantId: String, member: String, from: LocalDate, to: LocalDate): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE tenantId = :tenantId AND receiptLocalPath IS NOT NULL")
    suspend fun pendingReceipts(tenantId: String): List<ExpenseEntity>

    @Query("UPDATE expenses SET receiptFileId = :fileId, receiptLocalPath = NULL, dirty = 1 WHERE id = :id")
    suspend fun attachReceipt(id: String, fileId: String?)

    @Query("UPDATE expenses SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE tenantId = :tenantId")
    suspend fun deleteForTenant(tenantId: String)
}

@Dao
interface SyncFileDao {
    @Query("SELECT * FROM sync_files WHERE fileId = :fileId")
    suspend fun get(fileId: String): SyncFileEntity?

    @Upsert
    suspend fun upsert(file: SyncFileEntity)

    @Query("DELETE FROM sync_files WHERE tenantId = :tenantId")
    suspend fun deleteForTenant(tenantId: String)
}
