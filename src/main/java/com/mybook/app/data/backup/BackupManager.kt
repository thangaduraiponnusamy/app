package com.mybook.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mybook.app.MyBookApp
import com.mybook.app.data.BackupFrequency
import com.mybook.app.data.MyBookDatabase
import com.mybook.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess

/**
 * Backs up the database and receipts as one zip file at a user-chosen location (e.g. Google Drive via Android's
 * "Save to" screen). No Google sign-in or Cloud setup is needed: the Drive app handles the account.
 */
class BackupManager(
    private val context: Context,
    private val db: MyBookDatabase,
    private val prefs: Prefs,
) {
    private val mutex = Mutex()
    private val dbFile get() = context.getDatabasePath(DB_NAME)
    private val receiptsDir get() = File(context.filesDir, "receipts")

    /** Remembers [uri] (from the system "Save to" screen) and keeps write access across restarts. */
    fun setLocation(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        prefs.setBackupLocation(uri.toString(), displayName(uri))
        applySchedule()
    }

    fun clearLocation() {
        prefs.backupUri.value?.let { old ->
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags)
            }
        }
        prefs.setBackupLocation(null, null)
        applySchedule()
    }

    /** True when the saved location is still writable (it is not after a reinstall). */
    fun hasWritableLocation(): Boolean {
        val saved = prefs.backupUri.value ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri.toString() == saved && it.isWritePermission }
    }

    suspend fun backupNow(): Unit = mutex.withLock {
        val target = prefs.backupUri.value?.let(Uri::parse) ?: error("Choose a backup location first")
        if (!hasWritableLocation()) error("Backup location is no longer accessible. Please choose it again.")
        val zip = createZip()
        try {
            withContext(Dispatchers.IO) {
                val output = runCatching { context.contentResolver.openOutputStream(target, "wt") }.getOrNull()
                    ?: context.contentResolver.openOutputStream(target, "w")
                    ?: throw IOException("Cannot write to the backup location")
                output.use { out -> zip.inputStream().use { it.copyTo(out) } }
            }
        } finally {
            zip.delete()
        }
        prefs.setLastBackupAt(System.currentTimeMillis())
    }

    /** Replaces all local data with the backup in [source]. Call [restartApp] afterwards. */
    suspend fun restore(source: Uri): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            val zipFile = File(context.cacheDir, "restore.zip")
            val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
            try {
                val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot open the backup file")
                input.use { src -> zipFile.outputStream().use { src.copyTo(it) } }
                unzipValidated(zipFile, staging)
                val restoredDb = File(staging, "db/$DB_NAME")
                if (!restoredDb.exists()) throw IOException("This is not a MyBook backup file")
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
        if (prefs.backupUri.value == null || frequency == BackupFrequency.OFF) {
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

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

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
                if (!allowed) throw IOException("This is not a MyBook backup file")
                val out = File(target, name)
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    companion object {
        const val FILE_NAME = "MyBook-backup.zip"
        private const val DB_NAME = "mybook.db"
        private const val WORK_NAME = "mybook-backup"
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MyBookApp).container
        if (container.prefs.backupUri.value == null) return Result.success()
        return try {
            container.backupManager.backupNow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
