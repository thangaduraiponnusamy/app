package com.mybook.app.ui.common

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mybook.app.AppContainer
import com.mybook.app.MyBookApp
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Money
import com.mybook.app.domain.Period
import com.mybook.app.ui.theme.ExpenseRed
import com.mybook.app.ui.theme.IncomeGreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.coroutines.cancellation.CancellationException

/** Base ViewModel exposing a busy flag and a one-shot user message for snackbars. */
abstract class ActionViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        protected set

    fun clearMessage() {
        message = null
    }

    protected fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MyBook", "Action failed", e)
                message = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as MyBookApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun MessageEffect(vm: ActionViewModel, snackbar: SnackbarHostState) {
    val message = vm.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.clearMessage()
        }
    }
}

/** Large page title with subtitle, matching the dashboard style. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) {
            navigation()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Rounded-square icon button used in headers (bell, menu, back). */
@Composable
fun SquareIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    SquareIconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
fun PeriodPills(selected: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Period.entries.forEach { period ->
            val active = period == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    period.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Week/Month/Year pills plus a tappable period that opens a month/year/date picker. */
@Composable
fun PeriodBar(period: Period, anchor: LocalDate, onPeriod: (Period) -> Unit, onAnchor: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        PeriodPills(period, onPeriod)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { picking = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📅  ${period.title(anchor)}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose ${period.label.lowercase()}")
        }
    }
    if (picking) {
        PeriodPickerDialog(
            period = period,
            anchor = anchor,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onAnchor(it)
            },
        )
    }
}

@Composable
fun CategoryBadge(emoji: String, color: Color, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).background(color.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                "$action ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun TransactionRow(
    entry: ExpenseEntity,
    names: Map<String, String>,
    categories: Map<String, CategoryEntity>,
    onClick: () -> Unit,
) {
    val category = categories[entry.categoryId]
    val person = names[entry.paidBy] ?: "Unknown"
    val (emoji, color) = when (entry.type) {
        ExpenseType.SETTLEMENT -> "🤝" to IncomeGreen
        else -> (category?.emoji ?: "📌") to Color(category?.color ?: 0xFF94A3B8)
    }
    val supporting = when (entry.type) {
        ExpenseType.SETTLEMENT -> "$person paid ${entry.splitWith.firstOrNull()?.let { names[it] }.orEmpty()} · ${entry.date.display()}"
        ExpenseType.INCOME -> "${category?.name ?: "Income"} · ${entry.date.display()} · $person"
        ExpenseType.EXPENSE -> "${category?.name ?: "Uncategorised"} · ${entry.date.display()} · $person"
    }
    val (amountText, amountColor) = when (entry.type) {
        ExpenseType.INCOME -> "+${Money.format(entry.amountMinor)}" to IncomeGreen
        ExpenseType.EXPENSE -> "-${Money.format(entry.amountMinor)}" to ExpenseRed
        ExpenseType.SETTLEMENT -> Money.format(entry.amountMinor) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        leadingContent = { CategoryBadge(emoji, color) },
        headlineContent = {
            Text(
                if (entry.type == ExpenseType.SETTLEMENT) "Settlement" else entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Text(amountText, color = amountColor, fontWeight = FontWeight.SemiBold) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}

fun LocalDate.display(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
