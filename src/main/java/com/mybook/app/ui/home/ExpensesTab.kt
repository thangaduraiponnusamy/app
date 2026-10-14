package com.mybook.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Category
import com.mybook.app.domain.Money
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.PeriodSelector
import com.mybook.app.ui.common.display
import com.mybook.app.ui.theme.BrandGradient
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !yearly, onClick = { yearly = false }, label = { Text("Month") })
                FilterChip(selected = yearly, onClick = { yearly = true }, label = { Text("Year") })
            }
        }
        item {
            PeriodSelector(
                label = if (yearly) period.year.toString() else period.display(),
                onPrevious = { period = if (yearly) period.minusYears(1) else period.minusMonths(1) },
                onNext = { period = if (yearly) period.plusYears(1) else period.plusMonths(1) },
            )
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(BrandGradient, RoundedCornerShape(24.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Text("Total spent", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(
                        Money.format(spent, currency),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${visible.size} entries", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (visible.isEmpty()) item { Hint("No expenses in this period. Tap + to add one.") }
        items(visible, key = { it.id }) { expense ->
            ExpenseRow(expense, names, currency) { onOpen(expense.id) }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, names: Map<String, String>, currency: String, onClick: () -> Unit) {
    val payer = names[expense.paidBy] ?: "Unknown"
    val isSettlement = expense.type == ExpenseType.SETTLEMENT
    val category = Category.from(expense.category)
    val supporting = if (isSettlement) {
        val recipient = expense.splitWith.firstOrNull()?.let { names[it] }.orEmpty()
        "$payer paid $recipient · ${expense.date.display()}"
    } else {
        "${category.label} · ${expense.date.display()} · paid by $payer"
    }
    ListItem(
        leadingContent = {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isSettlement) "🤝" else category.emoji)
            }
        },
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
