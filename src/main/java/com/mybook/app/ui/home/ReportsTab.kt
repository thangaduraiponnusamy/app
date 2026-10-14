package com.mybook.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.domain.Money
import com.mybook.app.domain.Reports
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.theme.BlueDeep
import com.mybook.app.ui.theme.BlueGradient
import com.mybook.app.ui.theme.BlueLight
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class ReportMode(val label: String) { MONTH("Month"), YEAR("Year"), YEARS("All years") }

@Composable
fun ReportsTab(entries: List<ExpenseEntity>, categories: List<CategoryEntity>, names: Map<String, String>) {
    var mode by rememberSaveable { mutableStateOf(ReportMode.YEAR) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val today = LocalDate.now()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val inPeriod = remember(entries, mode, anchor) {
        when (mode) {
            ReportMode.MONTH -> entries.filter { it.date.year == anchor.year && it.date.monthValue == anchor.monthValue }
            ReportMode.YEAR -> entries.filter { it.date.year == anchor.year }
            ReportMode.YEARS -> entries
        }
    }
    val spending = Reports.spending(inPeriod)
    val spent = spending.sumOf { it.amountMinor }
    val income = Reports.income(inPeriod).sumOf { it.amountMinor }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportMode.entries.forEach { item ->
                val active = mode == item
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { mode = item }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(item.label, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (mode != ReportMode.YEARS) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                    anchor = if (mode == ReportMode.MONTH) anchor.minusMonths(1) else anchor.minusYears(1)
                }.padding(8.dp))
                Text(
                    if (mode == ReportMode.MONTH) "${anchor.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${anchor.year}" else anchor.year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                val nextAllowed = if (mode == ReportMode.MONTH) anchor.withDayOfMonth(1) < today.withDayOfMonth(1) else anchor.year < today.year
                Text("›", style = MaterialTheme.typography.headlineSmall, color = if (nextAllowed) Color.Unspecified else MaterialTheme.colorScheme.outline, modifier = Modifier.clickable(enabled = nextAllowed) {
                    anchor = if (mode == ReportMode.MONTH) anchor.plusMonths(1) else anchor.plusYears(1)
                }.padding(8.dp))
            }
        }

        BalanceCard(balance = income - spent, income = income, expenses = spent, brush = BlueGradient, title = "Net savings")

        when (mode) {
            ReportMode.YEAR -> {
                SectionHeader("Spending by month")
                BarChart(Reports.byMonth(spending, anchor.year), Modifier.padding(horizontal = 20.dp))
            }
            ReportMode.YEARS -> {
                val years = Reports.yearRange(spending, today.year)
                val byYear = Reports.byYear(spending, years)
                SectionHeader("Spending by year (${years.first}–${years.last})")
                BarChart(byYear, Modifier.padding(horizontal = 20.dp))
                SectionHeader("Yearly totals")
                BreakdownList(byYear.reversed().map { Triple(it.first, it.second, 0xFF3B82F6) }, spent)
            }
            ReportMode.MONTH -> Unit
        }

        SectionHeader("By category")
        BreakdownList(
            Reports.byCategory(spending, categoryMap).map { Triple("${it.emoji} ${it.name}", it.amountMinor, it.color) },
            spent,
        )
        SectionHeader("Paid by")
        BreakdownList(Reports.byPayer(spending, names).map { Triple(it.first, it.second, 0xFF60A5FA) }, spent)
    }
}

@Composable
private fun BarChart(entries: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val max = entries.maxOf { it.second }.coerceAtLeast(1L)
    var selected by remember(entries) { mutableIntStateOf(entries.indices.maxBy { entries[it].second }) }
    val active = Brush.verticalGradient(listOf(BlueLight, BlueDeep))
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(
            "${entries[selected].first}: ${Money.format(entries[selected].second)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEachIndexed { index, (label, value) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { selected = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val bar = Modifier.fillMaxWidth().fillMaxHeight(value.toFloat() / max)
                        val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        Box(if (index == selected) bar.background(active, shape) else bar.background(inactive, shape))
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BreakdownList(entries: List<Triple<String, Long, Long>>, total: Long) {
    if (entries.isEmpty() || total == 0L) {
        Hint("No spending in this period.")
        return
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        entries.forEach { (label, value, color) ->
            val fraction = (value.toFloat() / total).coerceIn(0f, 1f)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(label, Modifier.weight(1f))
                    Text("${Money.format(value)} · ${(fraction * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color(color), RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
