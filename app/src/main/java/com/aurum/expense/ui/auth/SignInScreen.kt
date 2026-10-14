package com.aurum.expense.ui.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.drive.DriveApi
import com.aurum.expense.data.drive.DriveAuth
import com.aurum.expense.ui.common.ActionViewModel
import com.aurum.expense.ui.common.appViewModel
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class SignInViewModel(
    private val auth: DriveAuth,
    private val drive: DriveApi,
    private val session: SessionStore,
) : ActionViewModel() {
    private val consent = Channel<PendingIntent>(Channel.BUFFERED)
    val consentRequests = consent.receiveAsFlow()

    fun signIn() = action {
        val result = auth.authorize(null)
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) consent.send(pending) else complete(result)
    }

    fun onConsentResult(data: Intent?) = action { complete(auth.resultFromIntent(data)) }

    fun onConsentCancelled() {
        message = "Sign-in was cancelled."
    }

    private suspend fun complete(result: AuthorizationResult) {
        val token = result.accessToken ?: error("Google did not return an access token.")
        val user = drive.about(token)
        val email = user.emailAddress ?: error("Could not read your Google account email.")
        session.signIn(email, user.displayName ?: email)
    }
}

@Composable
fun SignInScreen() {
    val vm = appViewModel { SignInViewModel(it.driveAuth, it.drive, it.session) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onConsentResult(result.data) else vm.onConsentCancelled()
    }
    LaunchedEffect(vm) {
        vm.consentRequests.collect { pending ->
            launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Aurum",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text("Shared expense tracking for your household or group.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            if (vm.busy) CircularProgressIndicator() else Button(onClick = vm::signIn) { Text("Sign in with Google") }
            vm.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Your data is stored in a Google Drive folder that you share with your group. " +
                    "Aurum has no servers of its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
