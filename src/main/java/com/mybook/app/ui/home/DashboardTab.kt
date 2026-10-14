package com.mybook.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.mybook.app.ui.theme.BrandGradient
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val RECENT_COUNT = 5

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
    val recent = entries.take(RECENT_COUNT)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { BalanceCard(balance = income - spent, income = income, expenses = spent, brush = BrandGradient) }
        if (dueCount > 0) item { DueBanner(dueCount, onOpenAlerts) }
        item { PeriodBar(period, anchor, onPeriod, onAnchor) }
        item { SpendingCard("${period.label}ly Spending", spent, totals, onDetails) }
        item { SectionHeader("Recent transactions", action = "See all") { onOpenCategory("all") } }
        if (recent.isEmpty()) item { Hint("No transactions yet. Tap + to add your first expense.") }
        items(recent, key = { it.id }) { entry ->
            TransactionRow(entry, names, categoryMap) { onOpenEntry(entry.id) }
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
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.3f)))
            BalanceFigure("↗  EXPENSES", Money.format(expenses), Modifier.weight(1f).padding(start = 20.dp))
        }
    }
}

@Composable
private fun BalanceFigure(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
            .background(Color(0xFFFFF4D6))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏰", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Text(
            if (count == 1) "1 payment is due. Tap to review." else "$count payments are due. Tap to review.",
            color = Color(0xFF92400E),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = Color(0xFF92400E), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SpendingCard(title: String, spent: Long, totals: List<CategoryTotal>, onDetails: () -> Unit) {
    var selected by remember(totals) { mutableStateOf<Int?>(null) }
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
                totals = totals,
                spent = spent,
                selected = selected,
                onSelect = { index -> selected = if (index == selected) null else index },
                modifier = Modifier.size(200.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (totals.isEmpty()) "No spending in this period" else "Tap the circle or a category to see details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        totals.withIndex().toList().chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                pair.forEach { (index, total) ->
                    LegendItem(total, index == selected, Modifier.weight(1f)) {
                        selected = if (index == selected) null else index
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendItem(total: CategoryTotal, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) Color(total.color).copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(Color(total.color), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            total.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Spending donut; tapping a slice selects it and shows its category in the centre. */
@Composable
private fun DonutChart(
    totals: List<CategoryTotal>,
    spent: Long,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sum = totals.sumOf { it.amountMinor }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(totals) {
                    detectTapGestures { tap ->
                        if (sum <= 0L) return@detectTapGestures
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val radius = minOf(size.width, size.height) / 2f
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < radius * 0.55f || distance > radius) {
                            onSelect(null)
                            return@detectTapGestures
                        }
                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                        if (angle < 0f) angle += 360f
                        var end = 0f
                        val index = totals.indexOfFirst { total ->
                            end += 360f * total.amountMinor / sum
                            angle <= end
                        }
                        onSelect(index.takeIf { it >= 0 })
                    }
                },
        ) {
            val stroke = size.minDimension * 0.14f
            val diameter = size.minDimension - stroke * 1.4f
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            if (sum <= 0L) {
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (totals.size > 1) 2f else 0f
            var start = -90f
            totals.forEachIndexed { index, total ->
                val sweep = 360f * total.amountMinor / sum
                val isSelected = index == selected
                val color = Color(total.color).copy(alpha = if (selected == null || isSelected) 1f else 0.3f)
                drawArc(
                    color = color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(if (isSelected) stroke * 1.35f else stroke),
                )
                start += sweep
            }
        }
        val current = selected?.let { totals.getOrNull(it) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            if (current == null) {
                Text(Money.compact(spent), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("SPENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
            } else {
                val percent = if (sum > 0) (current.amountMinor * 100f / sum).roundToInt() else 0
                Text(current.emoji, style = MaterialTheme.typography.titleLarge)
                Text(current.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(Money.compact(current.amountMinor), style = MaterialTheme.typography.titleMedium, color = Color(current.color), fontWeight = FontWeight.Bold)
                Text("$percent%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
