package com.aurum.expense.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aurum.expense.data.local.ExpenseEntity
import com.aurum.expense.data.local.MemberEntity
import com.aurum.expense.domain.Balances
import com.aurum.expense.domain.Money
import com.aurum.expense.domain.Transfer
import com.aurum.expense.ui.common.Hint
import com.aurum.expense.ui.common.SectionHeader
import kotlin.math.abs

@Composable
fun BalancesTab(
    expenses: List<ExpenseEntity>,
    members: List<MemberEntity>,
    names: Map<String, String>,
    currency: String,
    me: String,
    onSettle: (Transfer) -> Unit,
) {
    val balances = remember(expenses) { Balances.compute(expenses) }
    val transfers = remember(balances) { Balances.settleUp(balances) }
    val people = remember(balances, members) { (members.map { it.email } + balances.keys).distinct() }
    var confirm by remember { mutableStateOf<Transfer?>(null) }
    fun nameOf(email: String) = (names[email] ?: email) + if (email == me) " (you)" else ""

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader("Balances (all time)") }
        items(people, key = { it }) { email ->
            val amount = balances[email] ?: 0L
            ListItem(
                headlineContent = { Text(nameOf(email)) },
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
                        Money.format(abs(amount), currency),
                        color = when {
                            amount > 0 -> MaterialTheme.colorScheme.primary
                            amount < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
            )
        }
        item { SectionHeader("Suggested payments") }
        if (transfers.isEmpty()) item { Hint("Everyone is settled up.") }
        items(transfers) { transfer ->
            ListItem(
                headlineContent = { Text("${nameOf(transfer.from)} pays ${nameOf(transfer.to)}") },
                supportingContent = { Text(Money.format(transfer.amountMinor, currency)) },
                trailingContent = { TextButton(onClick = { confirm = transfer }) { Text("Mark paid") } },
            )
        }
    }

    confirm?.let { transfer ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Record payment?") },
            text = {
                Text(
                    "${nameOf(transfer.from)} paid ${nameOf(transfer.to)} ${Money.format(transfer.amountMinor, currency)}. " +
                        "A settlement entry is added for the whole group.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onSettle(transfer); confirm = null }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
