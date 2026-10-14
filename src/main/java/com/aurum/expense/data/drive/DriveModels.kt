package com.aurum.expense.data.drive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AurumJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class DriveFile(
    val id: String,
    val name: String = "",
    val mimeType: String = "",
    val modifiedTime: String? = null,
    val properties: Map<String, String>? = null,
    val owners: List<DriveUser>? = null,
) {
    fun prop(key: String): String? = properties?.get(key)
}

@Serializable
data class DriveFileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class DriveUser(val emailAddress: String? = null, val displayName: String? = null)

@Serializable
data class DriveAbout(val user: DriveUser)

@Serializable
data class DrivePermission(
    val id: String,
    val type: String = "",
    val role: String = "",
    val emailAddress: String? = null,
    val displayName: String? = null,
)

@Serializable
data class DrivePermissionList(val permissions: List<DrivePermission> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class DriveFileMetadata(
    val name: String,
    val mimeType: String? = null,
    val parents: List<String>? = null,
    val properties: Map<String, String>? = null,
)

@Serializable
data class NewPermission(val type: String, val role: String, val emailAddress: String)

class DriveException(val code: Int, body: String) : Exception("Google Drive request failed ($code): $body")

/** Folder/file conventions used inside each group's shared Drive folder. */
object DriveLayout {
    const val MIME_FOLDER = "application/vnd.google-apps.folder"
    const val MIME_JSON = "application/json"
    const val PROP_TENANT = "aurumTenant"
    const val PROP_KIND = "aurumKind"
    const val PROP_YEAR = "year"
    const val PROP_MEMBER = "member"
    const val KIND_EXPENSES = "expenses"
    const val KIND_META = "meta"
    const val KIND_RECEIPTS = "receipts"
    const val KIND_RECEIPT = "receipt"
    const val TENANT_FILE = "tenant.json"
    const val RECEIPTS_FOLDER = "receipts"
    const val FOLDER_PREFIX = "Aurum - "

    fun expenseFileName(year: Int, member: String) =
        "expenses_${year}_${member.replace(Regex("[^A-Za-z0-9]"), "_")}.json"

    fun receiptViewUrl(fileId: String) = "https://drive.google.com/file/d/$fileId/view"

    /** Escapes a value for use inside a single-quoted Drive query string. */
    fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
}
