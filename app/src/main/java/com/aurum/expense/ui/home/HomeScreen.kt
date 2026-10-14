package com.aurum.expense.ui.home

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aurum.expense.AppContainer
import com.aurum.expense.data.drive.ConsentRequiredException
import com.aurum.expense.data.local.MemberEntity
import com.aurum.expense.domain.Transfer
import com.aurum.expense.ui.common.ActionViewModel
import com.aurum.expense.ui.common.MessageEffect
import com.aurum.expense.ui.common.appViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HomeViewModel(private val tenantId: String, private val container: AppContainer) : ActionViewModel() {
    private val tenants = container.tenantRepository
    private val expenseRepo = container.expenseRepository
    private val consent = Channel<PendingIntent>(Channel.BUFFERED)

    val me: String = container.session.current.email.orEmpty()
    val consentRequests = consent.receiveAsFlow()
    val tenant = tenants.observeTenant(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val members = tenants.observeMembers(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = expenseRepo.observeActive(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pending = expenseRepo.observePendingCount(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        sync()
    }

    fun sync() = action {
        try {
            container.syncManager.sync(tenantId)
        } catch (e: ConsentRequiredException) {
            val result = container.driveAuth.authorize(me)
            val intent = result.pendingIntent
            if (result.hasResolution() && intent != null) consent.send(intent) else throw e
        }
    }

    fun onConsentResult(data: Intent?) = action {
        container.driveAuth.resultFromIntent(data)
        container.syncManager.sync(tenantId)
    }

    fun settle(transfer: Transfer) = action {
        expenseRepo.settle(tenantId, transfer.from, transfer.to, transfer.amountMinor)
    }

    fun invite(email: String) = action {
        val clean = email.trim().lowercase()
        require(Patterns.EMAIL_ADDRESS.matcher(clean).matches()) { "Enter a valid email address" }
        tenants.invite(tenantId, clean)
        message = "Invitation sent to $clean"
    }

    fun removeMember(member: MemberEntity) = action {
        tenants.removeMember(tenantId, member)
        message = "Removed ${member.displayName}"
    }

    fun export(uri: Uri) = action {
        val current = tenants.get(tenantId) ?: return@action
        val names = tenants.listMembers(tenantId).associate { it.email to it.displayName }
        expenseRepo.exportCsv(tenantId, uri, current.currency, names)
        message = "Exported to CSV"
    }

    fun switchGroup() = container.session.setActiveTenant(null)

    fun leave() = action { tenants.leave(tenantId) }

    fun signOut() = action { tenants.signOut() }
}

private enum class HomeTab(val label: String, val icon: ImageVector) {
    EXPENSES("Expenses", Icons.AutoMirrored.Filled.List),
    BALANCES("Balances", Icons.Filled.Share),
    REPORTS("Reports", Icons.Filled.DateRange),
    MEMBERS("Members", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(tenantId: String, onAddExpense: () -> Unit, onOpenExpense: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(tenantId, it) }
    val tenant by vm.tenant.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.EXPENSES) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    val currency = tenant?.currency ?: "EUR"
    val names = remember(members) { members.associate { it.email to it.displayName } }
    val isOwner = tenant?.ownerEmail == vm.me

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onConsentResult(result.data)
    }
    LaunchedEffect(vm) {
        vm.consentRequests.collect { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tenant?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (pending > 0) "$pending change(s) waiting to sync" else "Synced with Google Drive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::sync) { Icon(Icons.Filled.Refresh, contentDescription = "Sync") }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export to CSV (Excel)") },
                                onClick = {
                                    menuOpen = false
                                    val safeName = tenant?.name.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_")
                                    exportLauncher.launch("aurum-$safeName-${LocalDate.now()}.csv")
                                },
                            )
                            DropdownMenuItem(text = { Text("Switch group") }, onClick = { menuOpen = false; vm.switchGroup() })
                            if (!isOwner) {
                                DropdownMenuItem(text = { Text("Leave group") }, onClick = { menuOpen = false; confirmLeave = true })
                            }
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; confirmSignOut = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == HomeTab.EXPENSES) {
                FloatingActionButton(onClick = onAddExpense) { Icon(Icons.Filled.Add, contentDescription = "Add expense") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                HomeTab.EXPENSES -> ExpensesTab(expenses, names, currency, onOpenExpense)
                HomeTab.BALANCES -> BalancesTab(expenses, members, names, currency, vm.me, vm::settle)
                HomeTab.REPORTS -> ReportsTab(expenses, names, currency)
                HomeTab.MEMBERS -> MembersTab(members, vm.me, isOwner, vm::invite, vm::removeMember)
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave group?") },
            text = { Text("The group is removed from this device. The shared Drive folder stays; ask the owner to remove your access.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (pending > 0) {
                        "$pending change(s) have not been synced yet and will be lost."
                    } else {
                        "Local data is removed from this device. Everything synced stays in Google Drive."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
