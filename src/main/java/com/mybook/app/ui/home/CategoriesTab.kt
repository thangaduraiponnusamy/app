package com.mybook.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.CategoryKind
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Money
import com.mybook.app.domain.Period
import com.mybook.app.ui.common.CategoryBadge
import com.mybook.app.ui.common.PeriodBar
import com.mybook.app.ui.common.SectionHeader
import java.time.LocalDate

@Composable
fun CategoriesTab(
    entries: List<ExpenseEntity>,
    categories: List<CategoryEntity>,
    period: Period,
    anchor: LocalDate,
    onPeriod: (Period) -> Unit,
    onAnchor: (LocalDate) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val inRange = remember(entries, period, anchor) {
        val range = period.range(anchor)
        entries.filter { it.date in range }
    }
    val byCategory = remember(inRange) { inRange.groupBy { it.categoryId } }
    val spent = inRange.filter { it.type == ExpenseType.EXPENSE }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { PeriodBar(period, anchor, onPeriod, onAnchor) }
        item {
            CategoryTile(
                emoji = "📒",
                color = MaterialTheme.colorScheme.primary,
                name = "All expenses",
                detail = "${spent.size} entries",
                amount = spent.sumOf { it.amountMinor },
                onClick = { onOpenCategory("all") },
            )
        }
        item { SectionHeader("Expense categories", action = "Edit", onAction = onOpenAdmin) }
        items(categories.filter { it.kind == CategoryKind.EXPENSE }, key = { it.id }) { category ->
            val items = byCategory[category.id].orEmpty()
            CategoryTile(
                emoji = category.emoji,
                color = Color(category.color),
                name = category.name,
                detail = "${items.size} entries",
                amount = items.sumOf { it.amountMinor },
                onClick = { onOpenCategory(category.id) },
            )
        }
        item { SectionHeader("Income categories", action = "Edit", onAction = onOpenAdmin) }
        items(categories.filter { it.kind == CategoryKind.INCOME }, key = { it.id }) { category ->
            val items = byCategory[category.id].orEmpty()
            CategoryTile(
                emoji = category.emoji,
                color = Color(category.color),
                name = category.name,
                detail = "${items.size} entries",
                amount = items.sumOf { it.amountMinor },
                onClick = { onOpenCategory(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryTile(emoji: String, color: Color, name: String, detail: String, amount: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(emoji, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Money.format(amount), fontWeight = FontWeight.SemiBold)
    }
}
