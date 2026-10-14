package com.mybook.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybook.app.AppContainer
import com.mybook.app.data.BackupFrequency
import com.mybook.app.data.backup.BackupManager
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.appViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupViewModel(container: AppContainer) : ActionViewModel() {
    private val prefs = container.prefs
    private val backup = container.backupManager

    val location = prefs.backupLocationName
    val locationUri = prefs.backupUri
    val frequency = prefs.backupFrequency
    val lastBackupAt = prefs.lastBackupAt

    fun chooseLocation(uri: Uri) = action {
        backup.setLocation(uri)
        if (prefs.backupFrequency.value == BackupFrequency.OFF) setFrequency(BackupFrequency.DAILY)
        backup.backupNow()
        message = "Backed up. MyBook will keep this file updated."
    }

    fun setFrequency(value: BackupFrequency) {
        prefs.setBackupFrequency(value)
        backup.applySchedule()
    }

    fun backupNow() = action {
        backup.backupNow()
        message = "Backup saved"
    }

    fun restore(uri: Uri) = action {
        backup.restore(uri)
        prefs.setRestoreDecided(true)
        backup.restartApp()
    }

    fun turnOff() {
        backup.clearLocation()
        message = "Automatic backup turned off"
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/** Backup settings for the Admin page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSection(snackbar: SnackbarHostState) {
    val vm = appViewModel { BackupViewModel(it) }
    val location by vm.location.collectAsStateWithLifecycle()
    val locationUri by vm.locationUri.collectAsStateWithLifecycle()
    val frequency by vm.frequency.collectAsStateWithLifecycle()
    val lastBackupAt by vm.lastBackupAt.collectAsStateWithLifecycle()
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    MessageEffect(vm, snackbar)

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.chooseLocation(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("☁️ Backup to Google Drive", fontWeight = FontWeight.Bold)
        Text(
            if (locationUri == null) {
                "Tap \"Choose backup location\", pick Google Drive in the menu, then your account and folder. " +
                    "MyBook keeps that file updated automatically."
            } else {
                "Saving to: ${location ?: BackupManager.FILE_NAME}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (locationUri != null) {
            Text(
                if (lastBackupAt > 0) {
                    "Last backup: " + Instant.ofEpochMilli(lastBackupAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
                } else {
                    "No backup yet"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Back up automatically", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BackupFrequency.entries.forEach { option ->
                FilterChip(selected = frequency == option, onClick = { vm.setFrequency(option) }, label = { Text(option.label) })
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (locationUri == null) {
                Button(onClick = { createFile.launch(BackupManager.FILE_NAME) }, enabled = !vm.busy) { Text("Choose backup location") }
            } else {
                Button(onClick = vm::backupNow, enabled = !vm.busy) { Text(if (vm.busy) "Working…" else "Back up now") }
                OutlinedButton(onClick = { createFile.launch(BackupManager.FILE_NAME) }, enabled = !vm.busy) { Text("Change location") }
            }
        }
        OutlinedButton(
            onClick = { openFile.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
            enabled = !vm.busy,
        ) { Text("Restore from backup file") }
        if (locationUri != null) TextButton(onClick = vm::turnOff) { Text("Turn off automatic backup") }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("All data on this phone is replaced with the selected backup. The app restarts when done.") },
            confirmButton = { TextButton(onClick = { restoreUri = null; vm.restore(uri) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
}
