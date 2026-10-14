package com.aurum.expense.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Minimal Google Drive REST v3 client. */
class DriveApi(private val tokenProvider: suspend () -> String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun about(token: String? = null): DriveUser {
        val url = "$API/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(emailAddress,displayName)")
            .build()
        return AurumJson.decodeFromString<DriveAbout>(call(Request.Builder().url(url), token)).user
    }

    suspend fun listFiles(query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = AurumJson.decodeFromString<DriveFileList>(call(Request.Builder().url(url)))
            files += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return files
    }

    suspend fun createFolder(name: String, parentId: String?, properties: Map<String, String>): DriveFile {
        val metadata = DriveFileMetadata(name, DriveLayout.MIME_FOLDER, parentId?.let(::listOf), properties)
        val url = "$API/files".toHttpUrl().newBuilder().addQueryParameter("fields", FILE_FIELDS).build()
        val body = AurumJson.encodeToString(metadata).toRequestBody(JSON)
        return AurumJson.decodeFromString(call(Request.Builder().url(url).post(body)))
    }

    suspend fun createFile(
        name: String,
        parentId: String,
        mimeType: String,
        content: ByteArray,
        properties: Map<String, String>,
    ): DriveFile {
        val metadata = DriveFileMetadata(name, mimeType, listOf(parentId), properties)
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(AurumJson.encodeToString(metadata).toRequestBody(JSON))
            .addPart(content.toRequestBody(mimeType.toMediaType()))
            .build()
        val url = "$UPLOAD/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()
        return AurumJson.decodeFromString(call(Request.Builder().url(url).post(body)))
    }

    suspend fun updateContent(fileId: String, mimeType: String, content: ByteArray): DriveFile {
        val url = fileUrl(UPLOAD, fileId)
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", FILE_FIELDS)
            .build()
        val request = Request.Builder().url(url).patch(content.toRequestBody(mimeType.toMediaType()))
        return AurumJson.decodeFromString(call(request))
    }

    suspend fun downloadText(fileId: String): String =
        call(Request.Builder().url(fileUrl(API, fileId).addQueryParameter("alt", "media").build()))

    suspend fun listPermissions(fileId: String): List<DrivePermission> {
        val permissions = mutableListOf<DrivePermission>()
        var pageToken: String? = null
        do {
            val url = fileUrl(API, fileId).addPathSegment("permissions")
                .addQueryParameter("fields", "nextPageToken,permissions(id,type,role,emailAddress,displayName)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = AurumJson.decodeFromString<DrivePermissionList>(call(Request.Builder().url(url)))
            permissions += page.permissions
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return permissions
    }

    suspend fun addWriter(fileId: String, email: String) {
        val url = fileUrl(API, fileId).addPathSegment("permissions")
            .addQueryParameter("sendNotificationEmail", "true")
            .addQueryParameter("emailMessage", "You have been invited to an Aurum expense group. Open the Aurum app and join it from \"Shared with you\".")
            .build()
        val body = AurumJson.encodeToString(NewPermission("user", "writer", email)).toRequestBody(JSON)
        call(Request.Builder().url(url).post(body))
    }

    suspend fun deletePermission(fileId: String, permissionId: String) {
        val url = fileUrl(API, fileId).addPathSegment("permissions").addPathSegment(permissionId).build()
        call(Request.Builder().url(url).delete())
    }

    private fun fileUrl(base: String, fileId: String): HttpUrl.Builder =
        "$base/files".toHttpUrl().newBuilder().addPathSegment(fileId)

    private suspend fun call(request: Request.Builder, token: String? = null): String {
        val accessToken = token ?: tokenProvider()
        return withContext(Dispatchers.IO) {
            http.newCall(request.header("Authorization", "Bearer $accessToken").build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw DriveException(response.code, text.take(300))
                text
            }
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_FIELDS = "id,name,mimeType,modifiedTime,properties,owners(emailAddress,displayName)"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
