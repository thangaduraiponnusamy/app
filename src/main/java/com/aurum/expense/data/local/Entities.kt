package com.aurum.expense.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class ExpenseType { EXPENSE, SETTLEMENT }

@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val ownerEmail: String,
    val receiptsFolderId: String?,
)

@Entity(tableName = "members", primaryKeys = ["tenantId", "email"])
data class MemberEntity(
    val tenantId: String,
    val email: String,
    val displayName: String,
    val role: String,
    val permissionId: String,
)

@Entity(
    tableName = "expenses",
    indices = [Index("tenantId", "date"), Index("tenantId", "dirty")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val title: String,
    val amountMinor: Long,
    val category: String,
    val date: LocalDate,
    val paidBy: String,
    val splitWith: List<String>,
    val type: ExpenseType,
    val note: String,
    val receiptFileId: String?,
    val receiptLocalPath: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedBy: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,
)

/** Remembers the Drive modifiedTime of each downloaded file so unchanged files are skipped. */
@Entity(tableName = "sync_files")
data class SyncFileEntity(
    @PrimaryKey val fileId: String,
    val tenantId: String,
    val modifiedTime: String,
)
