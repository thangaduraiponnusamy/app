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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.domain.Money
import com.mybook.app.domain.Reports
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.PeriodSelector
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.display
import com.mybook.app.ui.theme.BrandGradient
import com.mybook.app.ui.theme.BrandPink
import com.mybook.app.ui.theme.BrandViolet
import java.time.YearMonth
import kotlin.math.roundToInt

private enum class ReportMode(val label: String) { MONTH("Month"), YEAR("Year"), YEARS("All years") }

@Composable
fun ReportsTab(expenses: List<ExpenseEntity>, names: Map<String, String>, currency: String) {
    var mode by rememberSaveable { mutableStateOf(ReportMode.YEAR) }
    var period by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val now = YearMonth.now()
    val spending = remember(expenses) { Reports.spending(expenses) }
    val inPeriod = remember(spending, mode, period) {
        when (mode) {
            ReportMode.MONTH -> spending.filter { YearMonth.from(it.date) == period }
            ReportMode.YEAR -> spending.filter { it.date.year == period.year }
            ReportMode.YEARS -> spending
        }
    }
    val total = inPeriod.sumOf { it.amountMinor }
    val format = { value: Long -> Money.format(value, currency) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMode.entries.forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) })
            }
        }
        when (mode) {
            ReportMode.MONTH -> PeriodSelector(
                label = period.display(),
                onPrevious = { period = period.minusMonths(1) },
                onNext = { period = period.plusMonths(1) },
                nextEnabled = period < now,
            )
            ReportMode.YEAR -> PeriodSelector(
                label = period.year.toString(),
                onPrevious = { period = period.minusYears(1) },
                onNext = { period = period.plusYears(1) },
                nextEnabled = period.year < now.year,
            )
            ReportMode.YEARS -> Unit
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(BrandGradient, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Column {
                Text("Total spent", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                Text(format(total), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                if (mode == ReportMode.YEAR) {
                    val months = if (period.year == now.year) now.monthValue else 12
                    Text(
                        "Monthly average: ${format(total / months)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }

        when (mode) {
            ReportMode.YEAR -> {
                SectionHeader("By month")
                BarChart(Reports.byMonth(inPeriod, period.year), format, Modifier.padding(horizontal = 16.dp))
            }
            ReportMode.YEARS -> {
                val years = Reports.yearRange(spending, now.year)
                val byYear = Reports.byYear(spending, years)
                SectionHeader("By year (${years.first}–${years.last})")
                BarChart(byYear, format, Modifier.padding(horizontal = 16.dp))
                SectionHeader("Yearly totals")
                BreakdownList(byYear.reversed(), total, format)
            }
            ReportMode.MONTH -> Unit
        }

        SectionHeader("By category")
        BreakdownList(Reports.byCategory(inPeriod), total, format)
        SectionHeader("Paid by")
        BreakdownList(Reports.byPayer(inPeriod, names), total, format)
    }
}

@Composable
private fun BarChart(entries: List<Pair<String, Long>>, formatValue: (Long) -> String, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    val max = entries.maxOf { it.second }.coerceAtLeast(1L)
    var selected by remember(entries) { mutableIntStateOf(entries.indices.maxBy { entries[it].second }) }
    val active = Brush.verticalGradient(listOf(BrandPink, BrandViolet))
    val inactive = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    Column(modifier.fillMaxWidth()) {
        Text(
            "${entries[selected].first}: ${formatValue(entries[selected].second)}",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
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
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BreakdownList(entries: List<Pair<String, Long>>, total: Long, formatValue: (Long) -> String) {
    if (entries.isEmpty() || total == 0L) {
        Hint("No spending in this period.")
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        entries.forEach { (label, value) ->
            val fraction = (value.toFloat() / total).coerceIn(0f, 1f)
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(label, Modifier.weight(1f))
                    Text("${formatValue(value)} · ${(fraction * 100).roundToInt()}%")
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction).fillMaxHeight()
                            .background(BrandGradient, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
