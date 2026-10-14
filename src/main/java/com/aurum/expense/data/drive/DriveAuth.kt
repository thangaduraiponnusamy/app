package com.aurum.expense.data.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.aurum.expense.data.SessionStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class ConsentRequiredException : Exception("Google Drive access needs to be granted again.")

/** Obtains Google Drive access tokens through Google Play services. */
class DriveAuth(private val context: Context, private val session: SessionStore) {

    private val client get() = Identity.getAuthorizationClient(context)

    suspend fun authorize(email: String?): AuthorizationResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        if (email != null) builder.setAccount(Account(email, "com.google"))
        return client.authorize(builder.build()).await()
    }

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    suspend fun accessToken(): String {
        val result = authorize(session.requireEmail())
        if (result.hasResolution()) throw ConsentRequiredException()
        return result.accessToken ?: throw ConsentRequiredException()
    }

    companion object {
        // Full Drive scope is needed so members can read files other members created in the shared folder.
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    }
}
