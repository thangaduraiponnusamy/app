package com.aurum.expense.data.sync

import com.aurum.expense.data.local.ExpenseEntity
import com.aurum.expense.data.local.ExpenseType
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class TenantMeta(
    val schemaVersion: Int = 1,
    val name: String,
    val currency: String,
    val ownerEmail: String,
    val createdAt: Long,
)

/** One file per member per year: each member only ever writes their own files, so there are no write conflicts. */
@Serializable
data class ExpenseFile(
    val schemaVersion: Int = 1,
    val tenantId: String,
    val year: Int,
    val member: String,
    val records: List<ExpenseRecord>,
)

@Serializable
data class ExpenseRecord(
    val id: String,
    val title: String,
    val amountMinor: Long,
    val category: String,
    val date: String,
    val paidBy: String,
    val splitWith: List<String>,
    val type: String = ExpenseType.EXPENSE.name,
    val note: String = "",
    val receiptFileId: String? = null,
    val createdBy: String,
    val createdAt: Long,
    val updatedBy: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

fun ExpenseEntity.toRecord() = ExpenseRecord(
    id = id,
    title = title,
    amountMinor = amountMinor,
    category = category,
    date = date.toString(),
    paidBy = paidBy,
    splitWith = splitWith,
    type = type.name,
    note = note,
    receiptFileId = receiptFileId,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedBy = updatedBy,
    updatedAt = updatedAt,
    deleted = deleted,
)

/** Returns null for malformed records written by other clients. */
fun ExpenseRecord.toEntityOrNull(tenantId: String): ExpenseEntity? = runCatching {
    require(id.isNotBlank() && amountMinor >= 0 && paidBy.isNotBlank())
    ExpenseEntity(
        id = id,
        tenantId = tenantId,
        title = title.take(MAX_TITLE),
        amountMinor = amountMinor,
        category = category,
        date = LocalDate.parse(date),
        paidBy = paidBy.lowercase(),
        splitWith = splitWith.map { it.lowercase() }.filter { it.isNotBlank() && '\n' !in it },
        type = ExpenseType.entries.firstOrNull { it.name == type } ?: ExpenseType.EXPENSE,
        note = note.take(MAX_NOTE),
        receiptFileId = receiptFileId,
        receiptLocalPath = null,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedBy = updatedBy,
        updatedAt = updatedAt,
        deleted = deleted,
        dirty = false,
    )
}.getOrNull()

const val MAX_TITLE = 120
const val MAX_NOTE = 500
