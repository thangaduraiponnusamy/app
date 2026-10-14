package com.mybook.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.CategoryTotal
import com.mybook.app.domain.Money
import com.mybook.app.domain.Period
import com.mybook.app.domain.Reports
import com.mybook.app.ui.common.CategoryBadge
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.PeriodBar
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.TransactionRow
import com.mybook.app.ui.theme.BlueGradient
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardTab(
    entries: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    names: Map<String, String>,
    period: Period,
    anchor: LocalDate,
    onPeriod: (Period) -> Unit,
    onAnchor: (LocalDate) -> Unit,
    dueCount: Int,
    onOpenAlerts: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onDetails: () -> Unit,
) {
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val inRange = remember(entries, period, anchor) {
        val range = period.range(anchor)
        entries.filter { it.date in range }
    }
    val income = inRange.filter { it.type == ExpenseType.INCOME }.sumOf { it.amountMinor }
    val spent = inRange.filter { it.type == ExpenseType.EXPENSE }.sumOf { it.amountMinor }
    val totals = remember(inRange, categoryMap) { Reports.byCategory(Reports.spending(inRange), categoryMap) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { BalanceCard(balance = income - spent, income = income, expenses = spent, brush = BlueGradient) }
        if (dueCount > 0) item { DueBanner(dueCount, onOpenAlerts) }
        item { PeriodBar(period, anchor, onPeriod, onAnchor) }
        item { SpendingCard("${period.label}ly Spending", spent, totals, onDetails) }
        item { SectionHeader("Category usage") }
        if (totals.isEmpty()) item { Hint("No spending in this period yet. Tap + to add an expense.") }
        items(totals) { total ->
            CategoryUsageRow(total, spent) { total.category?.let { onOpenCategory(it.id) } }
        }
        val recent = inRange.take(5)
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recent transactions", action = "See all") { onOpenCategory("all") } }
            items(recent, key = { it.id }) { entry ->
                TransactionRow(entry, names, categoryMap) { onOpenEntry(entry.id) }
            }
        }
    }
}

@Composable
internal fun BalanceCard(balance: Long, income: Long, expenses: Long, brush: Brush, title: String = "Total Balance") {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .padding(24.dp),
    ) {
        Text(title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(Money.format(balance), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            BalanceFigure("↙  INCOME", Money.format(income), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.25f)))
            BalanceFigure("↗  EXPENSES", Money.format(expenses), Modifier.weight(1f).padding(start = 20.dp))
        }
    }
}

@Composable
private fun BalanceFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DueBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF3A2A0A))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏰", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Text(
            if (count == 1) "1 payment is due. Tap to review." else "$count payments are due. Tap to review.",
            color = Color(0xFFFCD34D),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = Color(0xFFFCD34D), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SpendingCard(title: String, spent: Long, totals: List<CategoryTotal>, onDetails: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Details ›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onDetails),
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = totals.map { Color(it.color) to it.amountMinor },
                centerValue = Money.compact(spent),
                modifier = Modifier.size(190.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        totals.take(6).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                pair.forEach { total -> LegendItem(total, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendItem(total: CategoryTotal, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Color(total.color), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(total.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DonutChart(slices: List<Pair<Color, Long>>, centerValue: String, modifier: Modifier = Modifier, centerLabel: String = "SPENT") {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val total = slices.sumOf { it.second }
            if (total <= 0L) {
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEach { (color, value) ->
                val sweep = 360f * value / total
                drawArc(
                    color = color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
internal fun CategoryUsageRow(total: CategoryTotal, outOf: Long, onClick: () -> Unit) {
    val fraction = if (outOf > 0) (total.amountMinor.toFloat() / outOf).coerceIn(0f, 1f) else 0f
    val color = Color(total.color)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(total.emoji, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(total.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.format(total.amountMinor), fontWeight = FontWeight.SemiBold)
            }
            Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
            }
        }
    }
}
