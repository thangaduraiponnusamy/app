package com.aurum.expense.ui.expenses

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.aurum.expense.AppContainer
import com.aurum.expense.data.drive.DriveLayout
import com.aurum.expense.data.local.ExpenseEntity
import com.aurum.expense.data.local.ExpenseType
import com.aurum.expense.data.sync.MAX_NOTE
import com.aurum.expense.data.sync.MAX_TITLE
import com.aurum.expense.domain.Category
import com.aurum.expense.domain.Money
import com.aurum.expense.ui.common.ActionViewModel
import com.aurum.expense.ui.common.MessageEffect
import com.aurum.expense.ui.common.appViewModel
import com.aurum.expense.ui.common.display
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class ExpenseEditViewModel(
    private val tenantId: String,
    private val expenseId: String?,
    private val container: AppContainer,
) : ActionViewModel() {
    private val repo = container.expenseRepository
    private val tenants = container.tenantRepository

    val me: String = container.session.current.email.orEmpty()
    val members = tenants.observeMembers(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tenant = tenants.observeTenant(tenantId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var category by mutableStateOf(Category.FOOD)
    var date by mutableStateOf(LocalDate.now())
    var paidBy by mutableStateOf(me)
    var splitWith by mutableStateOf(setOf<String>())
    var note by mutableStateOf("")
    var newReceipt by mutableStateOf<Uri?>(null)
    var existing by mutableStateOf<ExpenseEntity?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val currency = tenants.get(tenantId)?.currency ?: "EUR"
            val expense = expenseId?.let { repo.get(it) }?.takeIf { it.tenantId == tenantId }
            if (expense != null) {
                existing = expense
                title = expense.title
                amount = Money.toPlain(expense.amountMinor, currency)
                category = Category.from(expense.category)
                date = expense.date
                paidBy = expense.paidBy
                splitWith = expense.splitWith.toSet()
                note = expense.note
            } else {
                splitWith = tenants.listMembers(tenantId).map { it.email }.ifEmpty { listOf(me) }.toSet()
            }
            loaded = true
        }
    }

    fun toggleSplit(email: String) {
        splitWith = if (email in splitWith) splitWith - email else splitWith + email
    }

    fun save() = action {
        require(title.isNotBlank()) { "Enter a title" }
        val currency = tenants.get(tenantId)?.currency ?: "EUR"
        val minor = Money.parse(amount, currency) ?: throw IllegalArgumentException("Enter a valid amount")
        require(splitWith.isNotEmpty()) { "Select who shares this expense" }
        val base = existing
        val receiptPath = newReceipt?.let { repo.importReceipt(it) } ?: base?.receiptLocalPath
        val now = System.currentTimeMillis()
        repo.save(
            ExpenseEntity(
                id = base?.id ?: UUID.randomUUID().toString(),
                tenantId = tenantId,
                title = title.trim(),
                amountMinor = minor,
                category = category.name,
                date = date,
                paidBy = paidBy,
                splitWith = splitWith.sorted(),
                type = base?.type ?: ExpenseType.EXPENSE,
                note = note.trim(),
                receiptFileId = base?.receiptFileId,
                receiptLocalPath = receiptPath,
                createdBy = base?.createdBy ?: me,
                createdAt = base?.createdAt ?: now,
                updatedBy = me,
                updatedAt = now,
                deleted = false,
                dirty = true,
            ),
        )
        finished = true
    }

    fun delete() = action {
        existing?.let { repo.delete(it) }
        finished = true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(tenantId: String, expenseId: String?, onDone: () -> Unit) {
    val vm = appViewModel { ExpenseEditViewModel(tenantId, expenseId, it) }
    val members by vm.members.collectAsStateWithLifecycle()
    val tenant by vm.tenant.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.newReceipt = uri
    }
    MessageEffect(vm, snackbar)
    LaunchedEffect(vm.finished) { if (vm.finished) onDone() }

    val names = members.associate { it.email to it.displayName }
    val people = (members.map { it.email } + vm.paidBy + vm.splitWith).filter { it.isNotBlank() }.distinct()
    val isSettlement = vm.existing?.type == ExpenseType.SETTLEMENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isSettlement -> "Edit settlement"
                            vm.existing == null -> "Add expense"
                            else -> "Edit expense"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (vm.existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = vm.title,
                    onValueChange = { vm.title = it.take(MAX_TITLE) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.amount,
                    onValueChange = { input -> vm.amount = input.filter { it.isDigit() || it == '.' || it == ',' }.take(15) },
                    label = { Text("Amount") },
                    suffix = { Text(tenant?.currency.orEmpty()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(vm.date.display())
                }

                if (!isSettlement) {
                    Label("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach { item ->
                            FilterChip(selected = vm.category == item, onClick = { vm.category = item }, label = { Text(item.label) })
                        }
                    }
                }

                Label("Paid by")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { email ->
                        FilterChip(
                            selected = vm.paidBy == email,
                            onClick = { vm.paidBy = email },
                            label = { Text(names[email] ?: email) },
                        )
                    }
                }

                Label(if (isSettlement) "Paid to" else "Split equally between")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { email ->
                        FilterChip(
                            selected = email in vm.splitWith,
                            onClick = { vm.toggleSplit(email) },
                            label = { Text(names[email] ?: email) },
                        )
                    }
                }

                OutlinedTextField(
                    value = vm.note,
                    onValueChange = { vm.note = it.take(MAX_NOTE) },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!isSettlement) {
                    Label("Receipt")
                    val preview: Any? = vm.newReceipt ?: vm.existing?.receiptLocalPath?.let { File(it) }
                    if (preview != null) {
                        AsyncImage(
                            model = preview,
                            contentDescription = "Receipt",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        )
                    }
                    val hasReceipt = preview != null || vm.existing?.receiptFileId != null
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text(if (hasReceipt) "Replace receipt" else "Attach receipt")
                        }
                        vm.existing?.receiptFileId?.let { fileId ->
                            TextButton(onClick = { uriHandler.openUri(DriveLayout.receiptViewUrl(fileId)) }) {
                                Text("View in Drive")
                            }
                        }
                    }
                }

                Button(onClick = vm::save, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = vm.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("It is removed for everyone in the group.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
