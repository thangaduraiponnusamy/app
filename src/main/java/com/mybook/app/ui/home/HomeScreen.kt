package com.mybook.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.domain.Period
import com.mybook.app.ui.books.RenameBookDialog
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SquareIconButton
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Navigation callbacks the home screen needs. */
data class HomeNav(
    val addExpense: () -> Unit,
    val addIncome: () -> Unit,
    val openEntry: (String) -> Unit,
    val openCategory: (String) -> Unit,
    val openAlerts: () -> Unit,
    val openAdmin: () -> Unit,
    val openBalances: () -> Unit,
)

class HomeViewModel(private val bookId: String, private val container: AppContainer) : ActionViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val book = repo.observeBook(bookId).stateIn(viewModelScope, started, null)
    val members = repo.observeMembers(bookId).stateIn(viewModelScope, started, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, started, emptyList())
    val entries = repo.observeExpenses(bookId).stateIn(viewModelScope, started, emptyList())
    val dueCount = repo.observeReminders(bookId)
        .map { list -> list.count { !it.dueDate.isAfter(LocalDate.now()) } }
        .stateIn(viewModelScope, started, 0)

    init {
        viewModelScope.launch {
            if (repo.getBook(bookId) == null) container.prefs.setActiveBook(null)
        }
    }

    fun export(uri: Uri) = action {
        repo.exportCsv(bookId, uri)
        message = "Exported to CSV"
    }

    fun switchBook() = container.prefs.setActiveBook(null)

    fun rename(name: String) = action {
        repo.renameBook(bookId, name)
        message = "Renamed to ${name.trim()}"
    }

    fun deleteBook() = action { repo.deleteBook(bookId) }
}

private enum class HomeTab(val label: String, val title: String, val icon: ImageVector) {
    HOME("Home", "Dashboard", Icons.Filled.Home),
    CATEGORIES("Categories", "Categories", Icons.AutoMirrored.Filled.List),
    INCOME("Income", "Income", Icons.Filled.AddCircle),
    REPORTS("Reports", "Reports", Icons.Filled.DateRange),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(bookId: String, nav: HomeNav) {
    val vm = appViewModel { HomeViewModel(bookId, it) }
    val book by vm.book.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val dueCount by vm.dueCount.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    val names = remember(members) { members.associate { it.id to it.name } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val subtitle = when (tab) {
        HomeTab.REPORTS -> book?.name.orEmpty()
        else -> "${book?.name.orEmpty()} · ${period.title(anchor)}"
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = tab.title,
                subtitle = subtitle,
                navigation = {
                    Box {
                        SquareIconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("All books") }, onClick = { menuOpen = false; vm.switchBook() })
                            DropdownMenuItem(text = { Text("Balances & settle up") }, onClick = { menuOpen = false; nav.openBalances() })
                            DropdownMenuItem(text = { Text("Payment reminders") }, onClick = { menuOpen = false; nav.openAlerts() })
                            DropdownMenuItem(text = { Text("Admin · categories, members & backup") }, onClick = { menuOpen = false; nav.openAdmin() })
                            DropdownMenuItem(
                                text = { Text("Export to CSV (Excel)") },
                                onClick = {
                                    menuOpen = false
                                    val safeName = book?.name.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_")
                                    exportLauncher.launch("mybook-$safeName-${LocalDate.now()}.csv")
                                },
                            )
                            DropdownMenuItem(text = { Text("Rename book") }, onClick = { menuOpen = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete book") }, onClick = { menuOpen = false; confirmDelete = true })
                        }
                    }
                },
                actions = {
                    SquareIconButton(onClick = nav.openAlerts) {
                        BadgedBox(badge = { if (dueCount > 0) Badge { Text("$dueCount") } }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Payment alerts")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != HomeTab.REPORTS) {
                FloatingActionButton(
                    onClick = if (tab == HomeTab.INCOME) nav.addIncome else nav.addExpense,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = if (tab == HomeTab.INCOME) "Add income" else "Add expense", tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                HomeTab.HOME -> DashboardTab(
                    entries = entries,
                    categories = categories,
                    names = names,
                    period = period,
                    anchor = anchor,
                    onPeriod = { period = it },
                    onAnchor = { anchor = it },
                    dueCount = dueCount,
                    onOpenAlerts = nav.openAlerts,
                    onOpenCategory = nav.openCategory,
                    onOpenEntry = nav.openEntry,
                    onDetails = { tab = HomeTab.CATEGORIES },
                )
                HomeTab.CATEGORIES -> CategoriesTab(
                    entries = entries,
                    categories = categories,
                    period = period,
                    anchor = anchor,
                    onPeriod = { period = it },
                    onAnchor = { anchor = it },
                    onOpenCategory = nav.openCategory,
                    onOpenAdmin = nav.openAdmin,
                )
                HomeTab.INCOME -> IncomeTab(
                    entries = entries,
                    categories = categories,
                    names = names,
                    period = period,
                    anchor = anchor,
                    onPeriod = { period = it },
                    onAnchor = { anchor = it },
                    onOpenEntry = nav.openEntry,
                )
                HomeTab.REPORTS -> ReportsTab(entries, categories, names)
            }
        }
    }

    if (renaming) {
        RenameBookDialog(
            currentName = book?.name.orEmpty(),
            onDismiss = { renaming = false },
            onSave = { name ->
                renaming = false
                vm.rename(name)
            },
        )
    }

    if (confirmDelete) {
        val bookName = book?.name.orEmpty()
        var typed by remember { mutableStateOf("") }
        val matches = typed.trim().isNotEmpty() && typed.trim() == bookName.trim()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$bookName\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("All expenses, income, reminders, members and receipts in this book are permanently deleted from this phone.")
                    Text("Type the book name to confirm:", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(80) },
                        placeholder = { Text(bookName) },
                        singleLine = true,
                        isError = typed.isNotEmpty() && !matches,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vm.deleteBook() },
                    enabled = matches,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete forever") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
