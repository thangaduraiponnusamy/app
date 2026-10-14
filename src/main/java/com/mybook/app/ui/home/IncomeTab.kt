package com.mybook.app.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Period
import com.mybook.app.domain.Reports
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.PeriodBar
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.TransactionRow
import com.mybook.app.ui.theme.GreenGradient
import java.time.LocalDate

@Composable
fun IncomeTab(
    entries: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    names: Map<String, String>,
    period: Period,
    anchor: LocalDate,
    onPeriod: (Period) -> Unit,
    onAnchor: (LocalDate) -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val inRange = remember(entries, period, anchor) {
        val range = period.range(anchor)
        entries.filter { it.date in range }
    }
    val incomes = inRange.filter { it.type == ExpenseType.INCOME }
    val received = incomes.sumOf { it.amountMinor }
    val spent = inRange.filter { it.type == ExpenseType.EXPENSE }.sumOf { it.amountMinor }
    val bySource = remember(incomes, categoryMap) { Reports.byCategory(incomes, categoryMap) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { PeriodBar(period, anchor, onPeriod, onAnchor) }
        item { BalanceCard(balance = received, income = received, expenses = spent, brush = GreenGradient, title = "Total Income") }
        item { SectionHeader("By source") }
        if (bySource.isEmpty()) item { Hint("No income in this period. Tap + to add salary, business or other income.") }
        items(bySource) { total -> CategoryUsageRow(total, received) {} }
        if (incomes.isNotEmpty()) {
            item { SectionHeader("Income entries") }
            items(incomes, key = { it.id }) { entry ->
                TransactionRow(entry, names, categoryMap) { onOpenEntry(entry.id) }
            }
        }
    }
}
