package com.mybook.app.ui.expenses

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mybook.app.AppContainer
import com.mybook.app.data.CategoryKind
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.MAX_NOTE
import com.mybook.app.domain.MAX_TITLE
import com.mybook.app.domain.Money
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SquareIconButton
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.common.display
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

enum class EntryKind { EXPENSE, INCOME }

class EntryEditViewModel(
    private val bookId: String,
    private val entryId: String?,
    kind: EntryKind,
    container: AppContainer,
) : ActionViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val members = repo.observeMembers(bookId).stateIn(viewModelScope, started, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, started, emptyList())

    var type by mutableStateOf(if (kind == EntryKind.INCOME) ExpenseType.INCOME else ExpenseType.EXPENSE)
        private set
    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var categoryId by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var paidBy by mutableStateOf("")
    var splitWith by mutableStateOf(setOf<String>())
    var note by mutableStateOf("")
    var receiptPath by mutableStateOf<String?>(null)
    var newReceipt by mutableStateOf<Uri?>(null)
    var existing by mutableStateOf<ExpenseEntity?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    val categoryKind: CategoryKind get() = if (type == ExpenseType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    init {
        viewModelScope.launch {
            val entry = entryId?.let { repo.getExpense(it) }?.takeIf { it.bookId == bookId }
            if (entry != null) {
                existing = entry
                type = entry.type
                title = entry.title
                amount = Money.toPlain(entry.amountMinor)
                categoryId = entry.categoryId
                date = entry.date
                paidBy = entry.paidBy
                splitWith = entry.splitWith.toSet()
                note = entry.note
                receiptPath = entry.receiptPath
            } else {
                val ids = repo.listMembers(bookId).map { it.id }
                paidBy = ids.firstOrNull().orEmpty()
                splitWith = if (type == ExpenseType.INCOME) emptySet() else ids.toSet()
                categoryId = repo.listCategories().firstOrNull { it.kind == categoryKind }?.id.orEmpty()
            }
            loaded = true
        }
    }

    fun toggleSplit(id: String) {
        splitWith = if (id in splitWith) splitWith - id else splitWith + id
    }

    fun removeReceipt() {
        newReceipt = null
        receiptPath = null
    }

    fun save() = action {
        require(title.isNotBlank()) { "Enter a title" }
        val minor = Money.parse(amount) ?: throw IllegalArgumentException("Enter a valid amount in ₹")
        require(paidBy.isNotEmpty()) { if (type == ExpenseType.INCOME) "Select who received it" else "Select who paid" }
        require(type == ExpenseType.INCOME || splitWith.isNotEmpty()) { "Select who shares this expense" }
        require(type == ExpenseType.SETTLEMENT || categoryId.isNotEmpty()) { "Choose a category" }
        val base = existing
        val finalReceipt = newReceipt?.let { repo.importReceipt(it) } ?: receiptPath
        val now = System.currentTimeMillis()
        repo.saveExpense(
            ExpenseEntity(
                id = base?.id ?: UUID.randomUUID().toString(),
                bookId = bookId,
                title = title.trim(),
                amountMinor = minor,
                categoryId = categoryId,
                date = date,
                paidBy = paidBy,
                splitWith = if (type == ExpenseType.INCOME) emptyList() else splitWith.sorted(),
                type = type,
                note = note.trim(),
                receiptPath = finalReceipt,
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        finished = true
    }

    fun delete() = action {
        existing?.let { repo.deleteExpense(it) }
        finished = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(bookId: String, entryId: String?, kind: EntryKind, onDone: () -> Unit) {
    val vm = appViewModel { EntryEditViewModel(bookId, entryId, kind, it) }
    val members by vm.members.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.newReceipt = uri
    }
    MessageEffect(vm, snackbar)
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    val isIncome = vm.type == ExpenseType.INCOME
    val isSettlement = vm.type == ExpenseType.SETTLEMENT
    val title = when {
        isSettlement -> "Settlement"
        vm.existing == null && isIncome -> "Add income"
        vm.existing == null -> "Add expense"
        isIncome -> "Edit income"
        else -> "Edit expense"
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = title,
                subtitle = "Amounts in ₹ INR",
                navigation = { BackButton(onDone) },
                actions = {
                    if (vm.existing != null) {
                        SquareIconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = vm.amount,
                    onValueChange = { input -> vm.amount = input.filter { it.isDigit() || it == '.' }.take(12) },
                    label = { Text("Amount") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.title,
                    onValueChange = { vm.title = it.take(MAX_TITLE) },
                    label = { Text(if (isIncome) "Source (e.g. October salary)" else "What was it for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(vm.date.display())
                }

                if (!isSettlement) {
                    Label("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.filter { it.kind == vm.categoryKind }.forEach { category ->
                            val selected = vm.categoryId == category.id
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) Color(category.color).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                    .clickable { vm.categoryId = category.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(category.emoji)
                                Spacer(Modifier.width(6.dp))
                                Text(category.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Label(if (isIncome) "Received by" else "Paid by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        FilterChip(selected = vm.paidBy == member.id, onClick = { vm.paidBy = member.id }, label = { Text(member.name) })
                    }
                }

                if (!isIncome) {
                    Label(if (isSettlement) "Paid to" else "Split equally between")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            FilterChip(
                                selected = member.id in vm.splitWith,
                                onClick = { vm.toggleSplit(member.id) },
                                label = { Text(member.name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = vm.note,
                    onValueChange = { vm.note = it.take(MAX_NOTE) },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isSettlement && !isIncome) {
                    Label("Receipt")
                    val preview: Any? = vm.newReceipt ?: vm.receiptPath?.let { File(it) }
                    if (preview != null) {
                        AsyncImage(
                            model = preview,
                            contentDescription = "Receipt",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(if (preview != null) "Replace photo" else "Attach photo") }
                        if (preview != null) TextButton(onClick = vm::removeReceipt) { Text("Remove") }
                    }
                }

                Button(
                    onClick = vm::save,
                    enabled = !vm.busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Save", modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}
