package com.aurum.expense.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurum.expense.data.local.ExpenseEntity
import com.aurum.expense.data.local.ExpenseType
import com.aurum.expense.domain.Category
import com.aurum.expense.domain.Money
import com.aurum.expense.ui.common.Hint
import com.aurum.expense.ui.common.PeriodSelector
import com.aurum.expense.ui.common.display
import java.time.YearMonth

@Composable
fun ExpensesTab(
    expenses: List<ExpenseEntity>,
    names: Map<String, String>,
    currency: String,
    onOpen: (String) -> Unit,
) {
    var yearly by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val visible = remember(expenses, period, yearly) {
        expenses.filter { if (yearly) it.date.year == period.year else YearMonth.from(it.date) == period }
    }
    val spent = remember(visible) { visible.filter { it.type == ExpenseType.EXPENSE }.sumOf { it.amountMinor } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !yearly, onClick = { yearly = false }, label = { Text("Month") })
            FilterChip(selected = yearly, onClick = { yearly = true }, label = { Text("Year") })
        }
        PeriodSelector(
            label = if (yearly) period.year.toString() else period.display(),
            onPrevious = { period = if (yearly) period.minusYears(1) else period.minusMonths(1) },
            onNext = { period = if (yearly) period.plusYears(1) else period.plusMonths(1) },
        )
        Text(
            "Spent: ${Money.format(spent, currency)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        HorizontalDivider(Modifier.padding(top = 8.dp))
        if (visible.isEmpty()) Hint("No expenses in this period. Tap + to add one.")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(visible, key = { it.id }) { expense ->
                ExpenseRow(expense, names, currency) { onOpen(expense.id) }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, names: Map<String, String>, currency: String, onClick: () -> Unit) {
    val payer = names[expense.paidBy] ?: expense.paidBy
    val isSettlement = expense.type == ExpenseType.SETTLEMENT
    val supporting = if (isSettlement) {
        val recipient = expense.splitWith.firstOrNull()?.let { names[it] ?: it }.orEmpty()
        "$payer paid $recipient · ${expense.date.display()}"
    } else {
        "${Category.from(expense.category).label} · ${expense.date.display()} · paid by $payer"
    }
    ListItem(
        headlineContent = {
            Text(if (isSettlement) "Settlement" else expense.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Text(
                Money.format(expense.amountMinor, currency),
                style = MaterialTheme.typography.titleSmall,
                color = if (isSettlement) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
