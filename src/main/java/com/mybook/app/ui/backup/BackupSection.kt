package com.mybook.app.ui.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
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
import com.mybook.app.data.backup.ConsentRequiredException
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class BackupViewModel(container: AppContainer) : ActionViewModel() {
    private val prefs = container.prefs
    private val backup = container.backupManager
    private val consent = Channel<PendingIntent>(Channel.BUFFERED)
    private var afterConsent: (suspend () -> Unit)? = null

    val email = prefs.backupEmail
    val frequency = prefs.backupFrequency
    val lastBackupAt = prefs.lastBackupAt
    val consentRequests = consent.receiveAsFlow()

    fun connect() = action {
        val result = backup.auth.authorize(null)
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            afterConsent = null
            consent.send(pending)
        } else {
            finishConnect(result.accessToken)
        }
    }

    fun onConsentResult(data: Intent?) = action {
        val result = backup.auth.resultFromIntent(data)
        val next = afterConsent
        afterConsent = null
        if (next != null) next() else finishConnect(result.accessToken)
    }

    fun disconnect() {
        prefs.setBackupEmail(null)
        backup.applySchedule()
        message = "Google Drive backup turned off"
    }

    fun setFrequency(value: BackupFrequency) {
        prefs.setBackupFrequency(value)
        backup.applySchedule()
    }

    fun backupNow() = withConsent {
        backup.backupNow()
        message = "Backed up to Google Drive"
    }

    fun restore() = withConsent {
        val account = prefs.backupEmail.value ?: error("Choose a Google account first")
        backup.restore(account)
        prefs.setRestoreDecided(true)
        backup.restartApp()
    }

    private suspend fun finishConnect(token: String?) {
        val accessToken = token ?: error("Google did not grant Drive access")
        val account = backup.drive.email(accessToken) ?: error("Could not read your Google account")
        prefs.setBackupEmail(account)
        if (prefs.backupFrequency.value == BackupFrequency.OFF) prefs.setBackupFrequency(BackupFrequency.DAILY)
        backup.applySchedule()
        message = "Backups will be saved to $account"
    }

    private fun withConsent(block: suspend () -> Unit) = action {
        try {
            block()
        } catch (e: ConsentRequiredException) {
            val pending = e.pendingIntent ?: throw e
            afterConsent = block
            consent.send(pending)
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")

/** Google Drive backup settings, shown on the Admin page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSection(snackbar: SnackbarHostState) {
    val vm = appViewModel { BackupViewModel(it) }
    val email by vm.email.collectAsStateWithLifecycle()
    val frequency by vm.frequency.collectAsStateWithLifecycle()
    val lastBackupAt by vm.lastBackupAt.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    MessageEffect(vm, snackbar)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onConsentResult(result.data)
    }
    LaunchedEffect(vm) {
        vm.consentRequests.collect { launcher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
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
        Text("☁️ Google Drive backup", fontWeight = FontWeight.Bold)
        val account = email
        if (account == null) {
            Text(
                "Back up all books, entries and receipts to your Google Drive, like WhatsApp. " +
                    "After reinstalling, MyBook restores them automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = vm::connect, enabled = !vm.busy) { Text("Choose Google account") }
        } else {
            Text("Account: $account", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (lastBackupAt > 0) {
                    "Last backup: " + Instant.ofEpochMilli(lastBackupAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
                } else {
                    "No backup yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text("Back up automatically", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BackupFrequency.entries.forEach { option ->
                    FilterChip(selected = frequency == option, onClick = { vm.setFrequency(option) }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::backupNow, enabled = !vm.busy) { Text(if (vm.busy) "Working…" else "Back up now") }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !vm.busy) { Text("Restore") }
            }
            TextButton(onClick = vm::disconnect) { Text("Turn off Drive backup") }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?") },
            text = { Text("All data on this phone is replaced with the backup from $email. The app restarts when done.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restore() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}
