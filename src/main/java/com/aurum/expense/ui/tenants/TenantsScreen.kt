package com.aurum.expense.ui.tenants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.drive.DriveFile
import com.aurum.expense.data.drive.DriveLayout
import com.aurum.expense.data.repo.TenantRepository
import com.aurum.expense.domain.Money
import com.aurum.expense.ui.common.ActionViewModel
import com.aurum.expense.ui.common.Hint
import com.aurum.expense.ui.common.MessageEffect
import com.aurum.expense.ui.common.SectionHeader
import com.aurum.expense.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class TenantsViewModel(
    private val repo: TenantRepository,
    private val session: SessionStore,
) : ActionViewModel() {
    val tenants = repo.observeTenants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var joinable by mutableStateOf<List<DriveFile>>(emptyList())
        private set

    init {
        refresh()
    }

    fun refresh() = action { joinable = repo.findJoinable() }

    fun open(tenantId: String) = session.setActiveTenant(tenantId)

    fun create(name: String, currency: String) = action {
        val cleanName = name.trim()
        val cleanCurrency = currency.trim().uppercase()
        require(cleanName.isNotEmpty()) { "Enter a group name" }
        require(Money.isValidCurrency(cleanCurrency)) { "Unknown currency code: $cleanCurrency" }
        repo.create(cleanName.take(60), cleanCurrency)
    }

    fun join(folder: DriveFile) = action { repo.join(folder) }

    fun signOut() = action { repo.signOut() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantsScreen() {
    val vm = appViewModel { TenantsViewModel(it.tenantRepository, it.session) }
    val tenants by vm.tenants.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aurum groups") },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !vm.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = vm::signOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New group") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (vm.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { SectionHeader("Your groups") }
            if (tenants.isEmpty()) item { Hint("No groups yet. Create one, or join a group someone shared with you.") }
            items(tenants, key = { it.id }) { tenant ->
                ListItem(
                    headlineContent = { Text(tenant.name) },
                    supportingContent = { Text("${tenant.currency} · owner ${tenant.ownerEmail}") },
                    modifier = Modifier.clickable { vm.open(tenant.id) },
                )
            }
            item { SectionHeader("Shared with you") }
            if (vm.joinable.isEmpty()) {
                item { Hint("Nothing new. Ask a group owner to invite your Google account, then tap refresh.") }
            }
            items(vm.joinable, key = { "join-" + it.id }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name.removePrefix(DriveLayout.FOLDER_PREFIX)) },
                    supportingContent = { Text("Owner: ${folder.owners?.firstOrNull()?.emailAddress ?: "unknown"}") },
                    trailingContent = {
                        TextButton(onClick = { vm.join(folder) }, enabled = !vm.busy) { Text("Join") }
                    },
                )
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, currency ->
                showCreate = false
                vm.create(name, currency)
            },
        )
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(Money.defaultCurrency()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Group name (e.g. Home, Flat 4B)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.take(3).uppercase() },
                    label = { Text("Currency (ISO code, e.g. EUR, INR)") },
                    singleLine = true,
                )
                Text("A shared folder is created in your Google Drive. You can invite members afterwards.")
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, currency) }, enabled = name.isNotBlank() && currency.length == 3) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
