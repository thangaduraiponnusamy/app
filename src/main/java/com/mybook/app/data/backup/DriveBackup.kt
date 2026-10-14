package com.mybook.app.data.backup

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.mybook.app.MyBookApp
import com.mybook.app.data.BackupFrequency
import com.mybook.app.data.MyBookDatabase
import com.mybook.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess

/** Thrown when Google needs the user to (re)approve Drive access; launch [pendingIntent] to ask. */
class ConsentRequiredException(val pendingIntent: PendingIntent?) : Exception("Allow MyBook to use Google Drive to continue")

class GoogleDriveAuth(private val context: Context) {
    private val client get() = Identity.getAuthorizationClient(context)

    /** With [email] = null Google shows the account chooser if needed. */
    suspend fun authorize(email: String?): AuthorizationResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        if (email != null) builder.setAccount(Account(email, "com.google"))
        return client.authorize(builder.build()).await()
    }

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    suspend fun token(email: String): String {
        val result = authorize(email)
        if (result.hasResolution()) throw ConsentRequiredException(result.pendingIntent)
        return result.accessToken ?: throw ConsentRequiredException(null)
    }

    companion object {
        // Hidden app-only folder in the user's Drive, the same approach WhatsApp uses.
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

data class RemoteBackup(val id: String, val modifiedTime: String?, val size: Long)

/** Minimal Drive REST client restricted to the appDataFolder. */
class DriveBackupClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun email(token: String): String? {
        val url = "$API/about".toHttpUrl().newBuilder().addQueryParameter("fields", "user(emailAddress)").build()
        val body = call(token, Request.Builder().url(url))
        return JSONObject(body).optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotBlank() }
    }

    suspend fun find(token: String): RemoteBackup? {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name='$FILE_NAME' and trashed=false")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("fields", "files(id,modifiedTime,size)")
            .build()
        val files = JSONObject(call(token, Request.Builder().url(url))).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val first = files.getJSONObject(0)
        return RemoteBackup(first.getString("id"), first.optString("modifiedTime").ifBlank { null }, first.optString("size").toLongOrNull() ?: 0L)
    }

    suspend fun upload(token: String, existingId: String?, file: File) {
        val zip = "application/zip".toMediaType()
        if (existingId != null) {
            val url = "$UPLOAD/files".toHttpUrl().newBuilder().addPathSegment(existingId).addQueryParameter("uploadType", "media").build()
            call(token, Request.Builder().url(url).patch(file.asRequestBody(zip)))
        } else {
            val metadata = JSONObject().put("name", FILE_NAME).put("parents", JSONArray().put("appDataFolder")).toString()
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(file.asRequestBody(zip))
                .build()
            val url = "$UPLOAD/files".toHttpUrl().newBuilder().addQueryParameter("uploadType", "multipart").build()
            call(token, Request.Builder().url(url).post(body))
        }
    }

    suspend fun download(token: String, id: String, target: File) = withContext(Dispatchers.IO) {
        val url = "$API/files".toHttpUrl().newBuilder().addPathSegment(id).addQueryParameter("alt", "media").build()
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Google Drive download failed (${response.code})")
            val source = response.body ?: throw IOException("Empty backup file")
            target.outputStream().use { out -> source.byteStream().use { it.copyTo(out) } }
        }
    }

    private suspend fun call(token: String, request: Request.Builder): String = withContext(Dispatchers.IO) {
        http.newCall(request.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Google Drive error (${response.code})")
            text
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "mybook-backup.zip"
    }
}

/** Zips the database and receipts, uploads them to Drive, and restores them back. */
class BackupManager(
    private val context: Context,
    private val db: MyBookDatabase,
    private val prefs: Prefs,
    val auth: GoogleDriveAuth,
    val drive: DriveBackupClient,
) {
    private val mutex = Mutex()
    private val dbFile get() = context.getDatabasePath(DB_NAME)
    private val receiptsDir get() = File(context.filesDir, "receipts")

    suspend fun backupNow(): Unit = mutex.withLock {
        val email = prefs.backupEmail.value ?: error("Choose a Google account for backup first")
        val token = auth.token(email)
        val zip = createZip()
        try {
            drive.upload(token, drive.find(token)?.id, zip)
        } finally {
            zip.delete()
        }
        prefs.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun findBackup(email: String): RemoteBackup? = drive.find(auth.token(email))

    /** Replaces all local data with the Drive backup. Call [restartApp] afterwards. */
    suspend fun restore(email: String): Unit = mutex.withLock {
        val token = auth.token(email)
        val remote = drive.find(token) ?: error("No MyBook backup found in $email")
        withContext(Dispatchers.IO) {
            val zipFile = File(context.cacheDir, "restore.zip")
            val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
            try {
                drive.download(token, remote.id, zipFile)
                unzipValidated(zipFile, staging)
                val restoredDb = File(staging, "db/$DB_NAME")
                if (!restoredDb.exists()) throw IOException("The backup file is not valid")
                db.close()
                listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
                restoredDb.copyTo(dbFile, overwrite = true)
                receiptsDir.deleteRecursively()
                File(staging, "receipts").takeIf { it.exists() }?.copyRecursively(receiptsDir, overwrite = true)
                Unit
            } finally {
                staging.deleteRecursively()
                zipFile.delete()
            }
        }
    }

    fun applySchedule() {
        val manager = WorkManager.getInstance(context)
        val frequency = prefs.backupFrequency.value
        if (prefs.backupEmail.value == null || frequency == BackupFrequency.OFF) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(frequency.minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) context.startActivity(intent)
        exitProcess(0)
    }

    private suspend fun createZip(): File = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = File(context.cacheDir, "mybook-backup.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("db/$DB_NAME"))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            receiptsDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                zip.putNextEntry(ZipEntry("receipts/${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        out
    }

    // Only accepts the exact layout written by createZip, which also blocks zip-slip paths.
    private fun unzipValidated(zipFile: File, target: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                val receiptName = name.removePrefix("receipts/")
                val allowed = !entry.isDirectory && !name.contains("..") && !name.contains('\\') &&
                    (name == "db/$DB_NAME" || (name.startsWith("receipts/") && receiptName.isNotEmpty() && '/' !in receiptName))
                if (!allowed) throw IOException("The backup file is not valid")
                val out = File(target, name)
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private companion object {
        const val DB_NAME = "mybook.db"
        const val WORK_NAME = "mybook-drive-backup"
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MyBookApp).container
        if (container.prefs.backupEmail.value == null) return Result.success()
        return try {
            container.backupManager.backupNow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConsentRequiredException) {
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
