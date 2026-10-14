package com.mybook.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.launch

enum class StartupState { CHECKING, ASK, RESTORING, DONE }

/** On a fresh install with no books, offers to restore a backup once, like WhatsApp. */
class StartupViewModel(container: AppContainer) : ActionViewModel() {
    private val prefs = container.prefs
    private val repo = container.repository
    private val backup = container.backupManager

    var state by mutableStateOf(StartupState.CHECKING)
        private set

    init {
        viewModelScope.launch {
            // Android's own backup may already have restored the database after a reinstall.
            if (prefs.restoreDecided || repo.hasBooks()) finish() else state = StartupState.ASK
        }
    }

    fun skip() = finish()

    fun restore(uri: Uri) = action {
        state = StartupState.RESTORING
        try {
            backup.restore(uri)
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
    when (vm.state) {
        StartupState.DONE -> content()
        StartupState.CHECKING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        StartupState.ASK, StartupState.RESTORING -> RestorePrompt(vm)
    }
}

@Composable
private fun RestorePrompt(vm: StartupViewModel) {
    val restoring = vm.state == StartupState.RESTORING || vm.busy
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(uri)
    }
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
                "Restoring your backup… Please keep the app open."
            } else {
                "If you backed up MyBook before, tap Restore, open Google Drive in the file picker and choose " +
                    "MyBook-backup.zip. You can also restore later from Admin."
            },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        if (restoring) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { openFile.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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
