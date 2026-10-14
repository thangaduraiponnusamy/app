package com.mybook.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.data.MemberEntity
import com.mybook.app.domain.MAX_NAME
import com.mybook.app.domain.Transfer
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val bookId: String, private val container: AppContainer) : ActionViewModel() {
    private val repo = container.repository

    val book = repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val members = repo.observeMembers(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = repo.observeExpenses(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (repo.getBook(bookId) == null) container.prefs.setActiveBook(null)
        }
    }

    fun settle(transfer: Transfer) = action {
        repo.settle(bookId, transfer.from, transfer.to, transfer.amountMinor)
        message = "Payment recorded"
    }

    fun addMember(name: String) = action {
        val clean = name.trim().take(MAX_NAME)
        require(clean.isNotEmpty()) { "Enter a name" }
        repo.addMember(bookId, clean)
        message = "Added $clean"
    }

    fun removeMember(member: MemberEntity) = action {
        repo.removeMember(member)
        message = "Removed ${member.name}"
    }

    fun export(uri: Uri) = action {
        repo.exportCsv(bookId, uri)
        message = "Exported to CSV"
    }

    fun switchBook() = container.prefs.setActiveBook(null)

    fun deleteBook() = action { repo.deleteBook(bookId) }
}

private enum class HomeTab(val label: String, val icon: ImageVector) {
    EXPENSES("Expenses", Icons.AutoMirrored.Filled.List),
    BALANCES("Balances", Icons.Filled.Share),
    REPORTS("Reports", Icons.Filled.DateRange),
    MEMBERS("Members", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(bookId: String, onAddExpense: () -> Unit, onOpenExpense: (String) -> Unit) {
    val vm = appViewModel { HomeViewModel(bookId, it) }
    val book by vm.book.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.EXPENSES) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    val currency = book?.currency ?: "INR"
    val names = remember(members) { members.associate { it.id to it.name } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = vm::switchBook) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All books")
                    }
                },
                title = {
                    Column {
                        Text(book?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${members.size} member(s) · $currency",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export to CSV (Excel)") },
                                onClick = {
                                    menuOpen = false
                                    val safeName = book?.name.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_")
                                    exportLauncher.launch("mybook-$safeName-${LocalDate.now()}.csv")
                                },
                            )
                            DropdownMenuItem(text = { Text("Delete book") }, onClick = { menuOpen = false; confirmDelete = true })
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
                HomeTab.BALANCES -> BalancesTab(expenses, members, names, currency, vm::settle)
                HomeTab.REPORTS -> ReportsTab(expenses, names, currency)
                HomeTab.MEMBERS -> MembersTab(members, vm::addMember, vm::removeMember)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${book?.name.orEmpty()}\"?") },
            text = { Text("All expenses, members and receipts in this book are permanently deleted from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteBook() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
