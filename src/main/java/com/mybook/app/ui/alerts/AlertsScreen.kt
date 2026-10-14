package com.mybook.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.CategoryKind
import com.mybook.app.data.ReminderEntity
import com.mybook.app.data.RepeatRule
import com.mybook.app.domain.MAX_TITLE
import com.mybook.app.domain.Money
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.CategoryBadge
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.common.display
import com.mybook.app.ui.theme.ExpenseRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class AlertsViewModel(private val bookId: String, container: AppContainer) : ActionViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val reminders = repo.observeReminders(bookId).stateIn(viewModelScope, started, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, started, emptyList())

    fun save(existing: ReminderEntity?, title: String, amount: String, dueDate: LocalDate, repeat: RepeatRule, categoryId: String) = action {
        require(title.isNotBlank()) { "Enter what the payment is for" }
        val minor = Money.parse(amount) ?: throw IllegalArgumentException("Enter a valid amount in ₹")
        require(categoryId.isNotEmpty()) { "Choose a category" }
        repo.saveReminder(
            ReminderEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                bookId = bookId,
                title = title.trim().take(MAX_TITLE),
                amountMinor = minor,
                categoryId = categoryId,
                dueDate = dueDate,
                repeat = repeat,
            ),
        )
        message = if (existing == null) "Reminder added" else "Reminder updated"
    }

    fun pay(reminder: ReminderEntity) = action {
        repo.payReminder(reminder)
        message = "Recorded ${reminder.title} as paid"
    }

    fun delete(reminder: ReminderEntity) = action {
        repo.deleteReminder(reminder)
        message = "Reminder deleted"
    }
}

/** Target of the reminder dialog; [existing] is null when adding. */
private data class ReminderEdit(val existing: ReminderEntity?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(bookId: String, onBack: () -> Unit) {
    val vm = appViewModel { AlertsViewModel(bookId, it) }
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ReminderEdit?>(null) }
    var deleting by remember { mutableStateOf<ReminderEntity?>(null) }
    MessageEffect(vm, snackbar)

    val today = LocalDate.now()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val due = reminders.filter { !it.dueDate.isAfter(today) }
    val upcoming = reminders.filter { it.dueDate.isAfter(today) }

    Scaffold(
        topBar = { ScreenHeader("Payment alerts", "Bills and payments to remember", navigation = { BackButton(onBack) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ReminderEdit(null) }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Add, contentDescription = "Add reminder", tint = Color.White)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SectionHeader("Due now") }
            if (due.isEmpty()) item { Hint("Nothing due today 🎉") }
            items(due, key = { it.id }) { reminder ->
                ReminderCard(reminder, categoryMap[reminder.categoryId], isDue = true, onPay = { vm.pay(reminder) }, onEdit = { editing = ReminderEdit(reminder) }, onDelete = { deleting = reminder })
            }
            item { SectionHeader("Upcoming") }
            if (upcoming.isEmpty()) item { Hint("No upcoming payments. Tap + to add rent, EMI, school fees or bills.") }
            items(upcoming, key = { it.id }) { reminder ->
                ReminderCard(reminder, categoryMap[reminder.categoryId], isDue = false, onPay = { vm.pay(reminder) }, onEdit = { editing = ReminderEdit(reminder) }, onDelete = { deleting = reminder })
            }
        }
    }

    editing?.let { edit ->
        ReminderDialog(
            edit = edit,
            categories = categories.filter { it.kind == CategoryKind.EXPENSE },
            onDismiss = { editing = null },
            onSave = { title, amount, date, repeat, categoryId ->
                vm.save(edit.existing, title, amount, date, repeat, categoryId)
                editing = null
            },
        )
    }
    deleting?.let { reminder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete reminder?") },
            text = { Text("\"${reminder.title}\" will no longer show up in alerts.") },
            confirmButton = { TextButton(onClick = { vm.delete(reminder); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    category: CategoryEntity?,
    isDue: Boolean,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onEdit)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category?.emoji ?: "📌", Color(category?.color ?: 0xFF94A3B8))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "Due ${reminder.dueDate.display()} · ${reminder.repeat.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDue) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(reminder.amountMinor), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPay) { Text("Mark paid") }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(
    edit: ReminderEdit,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, LocalDate, RepeatRule, String) -> Unit,
) {
    val existing = edit.existing
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.let { Money.toPlain(it.amountMinor) }.orEmpty()) }
    var dueDate by rememberSaveable { mutableStateOf(existing?.dueDate ?: LocalDate.now()) }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeat ?: RepeatRule.MONTHLY) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New payment reminder" else "Edit reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(MAX_TITLE) }, label = { Text("Payment (e.g. House rent)") }, singleLine = true)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' }.take(12) },
                    label = { Text("Amount") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Due ${dueDate.display()}")
                }
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatRule.entries.forEach { rule ->
                        FilterChip(selected = repeat == rule, onClick = { repeat = rule }, label = { Text(rule.label) })
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text("${category.emoji} ${category.name}") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, amount, dueDate, repeat, categoryId) }, enabled = title.isNotBlank() && amount.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
