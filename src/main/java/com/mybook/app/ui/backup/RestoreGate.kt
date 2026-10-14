package com.mybook.app.ui.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.R
import com.mybook.app.data.BackupFrequency
import com.mybook.app.data.backup.ConsentRequiredException
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class StartupState { CHECKING, ASK, RESTORING, DONE }

/** Decides on first launch whether to restore from Google Drive, like WhatsApp does after a reinstall. */
class StartupViewModel(container: AppContainer) : ActionViewModel() {
    private val prefs = container.prefs
    private val repo = container.repository
    private val backup = container.backupManager
    private val consent = Channel<PendingIntent>(Channel.BUFFERED)

    var state by mutableStateOf(StartupState.CHECKING)
        private set
    var account by mutableStateOf<String?>(null)
        private set
    val consentRequests = consent.receiveAsFlow()

    init {
        viewModelScope.launch { decide() }
    }

    private suspend fun decide() {
        if (prefs.restoreDecided || repo.hasBooks()) {
            finish()
            return
        }
        // Account remembered by Android's own backup after a reinstall: restore without asking.
        val email = prefs.backupEmail.value
        if (email == null) {
            state = StartupState.ASK
            return
        }
        account = email
        try {
            if (backup.findBackup(email) == null) finish() else restoreAndRestart(email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = StartupState.ASK
            if (e !is ConsentRequiredException) message = e.message
        }
    }

    fun skip() = finish()

    fun restoreFromDrive() = action {
        val result = backup.auth.authorize(account)
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) consent.send(pending) else afterAuthorized(result.accessToken)
    }

    fun onConsentResult(data: Intent?) = action { afterAuthorized(backup.auth.resultFromIntent(data).accessToken) }

    private suspend fun afterAuthorized(token: String?) {
        val accessToken = token ?: error("Google did not grant Drive access")
        val email = backup.drive.email(accessToken) ?: error("Could not read your Google account")
        account = email
        prefs.setBackupEmail(email)
        if (prefs.backupFrequency.value == BackupFrequency.OFF) prefs.setBackupFrequency(BackupFrequency.DAILY)
        backup.applySchedule()
        if (backup.drive.find(accessToken) == null) {
            message = "No MyBook backup found in $email. Tap Skip to start fresh."
            return
        }
        restoreAndRestart(email)
    }

    private suspend fun restoreAndRestart(email: String) {
        state = StartupState.RESTORING
        try {
            backup.restore(email)
        } catch (e: Exception) {
            state = StartupState.ASK
            throw e
        }
        prefs.setRestoreDecided(true)
        backup.restartApp()
    }

    private fun finish() {
        prefs.setRestoreDecided(true)
        state = StartupState.DONE
    }
}

/** Shows the restore prompt until a decision is made, then [content]. */
@Composable
fun RestoreGate(content: @Composable () -> Unit) {
    val vm = appViewModel { StartupViewModel(it) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onConsentResult(result.data)
    }
    LaunchedEffect(vm) {
        vm.consentRequests.collect { launcher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    when (vm.state) {
        StartupState.DONE -> content()
        StartupState.CHECKING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        StartupState.ASK, StartupState.RESTORING -> RestorePrompt(vm)
    }
}

@Composable
private fun RestorePrompt(vm: StartupViewModel) {
    val restoring = vm.state == StartupState.RESTORING || vm.busy
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(24.dp))
        Text("Restore your books?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (restoring) {
                "Restoring from ${vm.account ?: "Google Drive"}… Please keep the app open."
            } else {
                "If you backed up MyBook to Google Drive before, restore your books, entries and receipts now. " +
                    "You can also restore later from Admin."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        if (restoring) {
            CircularProgressIndicator()
        } else {
            Button(onClick = vm::restoreFromDrive, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Restore from Google Drive", modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::skip, modifier = Modifier.fillMaxWidth()) { Text("Skip, start fresh") }
        }
        vm.message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
