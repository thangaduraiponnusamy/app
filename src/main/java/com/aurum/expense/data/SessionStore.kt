package com.aurum.expense.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(
    val email: String? = null,
    val displayName: String? = null,
    val activeTenantId: String? = null,
) {
    val isSignedIn: Boolean get() = email != null
}

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("aurum_session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Session> = _state.asStateFlow()
    val current: Session get() = _state.value

    fun requireEmail(): String = current.email ?: throw IllegalStateException("Not signed in")

    fun signIn(email: String, displayName: String) {
        val normalized = email.lowercase()
        val keepTenant = current.activeTenantId.takeIf { current.email == normalized }
        update(Session(normalized, displayName, keepTenant))
    }

    fun setActiveTenant(tenantId: String?) = update(current.copy(activeTenantId = tenantId))

    fun signOut() = update(Session())

    private fun load() = Session(
        email = prefs.getString(KEY_EMAIL, null),
        displayName = prefs.getString(KEY_NAME, null),
        activeTenantId = prefs.getString(KEY_TENANT, null),
    )

    private fun update(session: Session) {
        prefs.edit()
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.displayName)
            .putString(KEY_TENANT, session.activeTenantId)
            .apply()
        _state.value = session
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "display_name"
        const val KEY_TENANT = "active_tenant"
    }
}
