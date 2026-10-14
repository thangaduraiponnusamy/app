package com.mybook.app.ui.balances

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.domain.Balances
import com.mybook.app.domain.Money
import com.mybook.app.domain.Transfer
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.theme.ExpenseRed
import com.mybook.app.ui.theme.IncomeGreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

class BalancesViewModel(private val bookId: String, container: AppContainer) : ActionViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val entries = repo.observeExpenses(bookId).stateIn(viewModelScope, started, emptyList())
    val members = repo.observeMembers(bookId).stateIn(viewModelScope, started, emptyList())

    fun settle(transfer: Transfer) = action {
        repo.settle(bookId, transfer.from, transfer.to, transfer.amountMinor)
        message = "Payment recorded"
    }
}

@Composable
fun BalancesScreen(bookId: String, onBack: () -> Unit) {
    val vm = appViewModel { BalancesViewModel(bookId, it) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Transfer?>(null) }
    MessageEffect(vm, snackbar)

    val names = remember(members) { members.associate { it.id to it.name } }
    val balances = remember(entries) { Balances.compute(entries) }
    val transfers = remember(balances) { Balances.settleUp(balances) }
    val people = remember(balances, members) { (members.map { it.id } + balances.keys).distinct() }
    fun nameOf(id: String) = names[id] ?: "Unknown"

    Scaffold(
        topBar = { ScreenHeader("Balances", "Who owes whom (all time)", navigation = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Balances") }
            items(people, key = { it }) { id ->
                val amount = balances[id] ?: 0L
                ListItem(
                    headlineContent = { Text(nameOf(id), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            when {
                                amount > 0 -> "gets back"
                                amount < 0 -> "owes"
                                else -> "settled up"
                            },
                        )
                    },
                    trailingContent = {
                        Text(
                            Money.format(abs(amount)),
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                amount > 0 -> IncomeGreen
                                amount < 0 -> ExpenseRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item { SectionHeader("Suggested payments") }
            if (transfers.isEmpty()) item { Hint("Everyone is settled up 🎉") }
            items(transfers) { transfer ->
                ListItem(
                    headlineContent = { Text("${nameOf(transfer.from)} pays ${nameOf(transfer.to)}") },
                    supportingContent = { Text(Money.format(transfer.amountMinor)) },
                    trailingContent = { TextButton(onClick = { confirm = transfer }) { Text("Mark paid") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    confirm?.let { transfer ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Record payment?") },
            text = { Text("${nameOf(transfer.from)} paid ${nameOf(transfer.to)} ${Money.format(transfer.amountMinor)}.") },
            confirmButton = { TextButton(onClick = { vm.settle(transfer); confirm = null }) { Text("Record") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
