package com.mybook.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackupFrequency(val label: String, val minutes: Long) {
    OFF("Off", 0),
    MIN_15("15 min", 15),
    HOURLY("Hourly", 60),
    DAILY("Daily", 24 * 60),
    WEEKLY("Weekly", 7 * 24 * 60),
    MONTHLY("Monthly", 30 * 24 * 60),
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("mybook_prefs", Context.MODE_PRIVATE)

    // Excluded from Android backup (see res/xml), so a reinstall asks about restoring again.
    private val device = context.getSharedPreferences("mybook_device", Context.MODE_PRIVATE)

    private val _activeBookId = MutableStateFlow(prefs.getString(KEY_ACTIVE_BOOK, null))
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _smsAutoAdd = MutableStateFlow(prefs.getBoolean(KEY_SMS_AUTO_ADD, false))
    val smsAutoAdd: StateFlow<Boolean> = _smsAutoAdd.asStateFlow()

    private val _backupEmail = MutableStateFlow(prefs.getString(KEY_BACKUP_EMAIL, null))
    val backupEmail: StateFlow<String?> = _backupEmail.asStateFlow()

    private val _backupFrequency = MutableStateFlow(
        BackupFrequency.entries.firstOrNull { it.name == prefs.getString(KEY_BACKUP_FREQUENCY, null) } ?: BackupFrequency.DAILY,
    )
    val backupFrequency: StateFlow<BackupFrequency> = _backupFrequency.asStateFlow()

    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    val restoreDecided: Boolean get() = device.getBoolean(KEY_RESTORE_DECIDED, false)

    fun setActiveBook(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_BOOK, id).apply()
        _activeBookId.value = id
    }

    fun setSmsAutoAdd(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_AUTO_ADD, enabled).apply()
        _smsAutoAdd.value = enabled
    }

    fun setBackupEmail(email: String?) {
        prefs.edit().putString(KEY_BACKUP_EMAIL, email).commit()
        _backupEmail.value = email
    }

    fun setBackupFrequency(frequency: BackupFrequency) {
        prefs.edit().putString(KEY_BACKUP_FREQUENCY, frequency.name).apply()
        _backupFrequency.value = frequency
    }

    fun setLastBackupAt(time: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, time).apply()
        _lastBackupAt.value = time
    }

    // commit() because the app restarts right after a restore.
    fun setRestoreDecided(decided: Boolean) {
        device.edit().putBoolean(KEY_RESTORE_DECIDED, decided).commit()
    }

    private companion object {
        const val KEY_ACTIVE_BOOK = "active_book"
        const val KEY_SMS_AUTO_ADD = "sms_auto_add"
        const val KEY_BACKUP_EMAIL = "backup_email"
        const val KEY_BACKUP_FREQUENCY = "backup_frequency"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_RESTORE_DECIDED = "restore_decided"
    }
}
