package com.mybook.app.ui.sms

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mybook.app.AppContainer
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Money
import com.mybook.app.domain.SmsParser
import com.mybook.app.domain.SmsTransaction
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.common.display
import com.mybook.app.ui.theme.ExpenseRed
import com.mybook.app.ui.theme.IncomeGreen

data class SmsCandidate(val transaction: SmsTransaction, val alreadyAdded: Boolean)

class SmsImportViewModel(private val bookId: String, container: AppContainer) : ActionViewModel() {
    private val repo = container.repository
    private val reader = container.smsReader
    private val prefs = container.prefs

    val autoAdd = prefs.smsAutoAdd
    var candidates by mutableStateOf<List<SmsCandidate>>(emptyList())
        private set
    var selected by mutableStateOf(setOf<String>())
        private set
    var scanned by mutableStateOf(false)
        private set

    fun scan(days: Int) = action {
        val transactions = reader.readInbox(days).mapNotNull(SmsParser::parse).distinctBy { it.key }
        val done = repo.importedSmsKeys(transactions.map { it.key })
        candidates = transactions.map { SmsCandidate(it, it.key in done) }
        selected = candidates.filterNot { it.alreadyAdded }.map { it.transaction.key }.toSet()
        scanned = true
    }

    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }

    fun importSelected() = action {
        val chosen = candidates.filter { it.transaction.key in selected && !it.alreadyAdded }.map { it.transaction }
        val added = repo.importSmsTransactions(bookId, chosen)
        val keys = chosen.map { it.key }.toSet()
        candidates = candidates.map { if (it.transaction.key in keys) it.copy(alreadyAdded = true) else it }
        selected = emptySet()
        message = if (added == 1) "Added 1 transaction" else "Added $added transactions"
    }

    fun setAutoAdd(enabled: Boolean) = prefs.setSmsAutoAdd(enabled)
}

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
fun SmsImportScreen(bookId: String, onBack: () -> Unit) {
    val vm = appViewModel { SmsImportViewModel(bookId, it) }
    val autoAdd by vm.autoAdd.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var days by remember { mutableIntStateOf(30) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    MessageEffect(vm, snackbar)

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) pendingAction?.invoke()
        pendingAction = null
    }
    fun withPermission(block: () -> Unit) {
        pendingAction = block
        permissions.launch(SMS_PERMISSIONS)
    }

    Scaffold(
        topBar = { ScreenHeader("Bank SMS", "Track transactions automatically", navigation = { BackButton(onBack) }) },
        bottomBar = {
            val count = vm.selected.size
            if (count > 0) {
                Button(
                    onClick = vm::importSelected,
                    enabled = !vm.busy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                ) { Text("Add $count selected", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                ) {
                    Text("🔒 Private by design", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "MyBook reads bank and UPI SMS on this phone to find debits and credits. Messages never leave your phone. " +
                            "OTPs, due-date alerts and failed payments are ignored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-add new bank SMS", fontWeight = FontWeight.SemiBold)
                        Text(
                            "New transactions are added to the open book as they arrive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoAdd,
                        onCheckedChange = { enabled ->
                            if (enabled) withPermission { vm.setAutoAdd(true) } else vm.setAutoAdd(false)
                        },
                    )
                }
            }
            item { SectionHeader("Scan inbox") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(7, 30, 90).forEach { option ->
                        FilterChip(selected = days == option, onClick = { days = option }, label = { Text("$option days") })
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { withPermission { vm.scan(days) } }, enabled = !vm.busy) { Text("Scan") }
                }
            }
            if (vm.scanned) {
                item {
                    Hint(
                        if (vm.candidates.isEmpty()) "No bank transactions found in the last $days days."
                        else "Found ${vm.candidates.size} transactions. Untick any you don't want.",
                    )
                }
            }
            items(vm.candidates, key = { it.transaction.key }) { candidate ->
                CandidateRow(candidate, candidate.transaction.key in vm.selected) { vm.toggle(candidate.transaction.key) }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: SmsCandidate, checked: Boolean, onToggle: () -> Unit) {
    val t = candidate.transaction
    val income = t.type == ExpenseType.INCOME
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !candidate.alreadyAdded, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked || candidate.alreadyAdded, onCheckedChange = { onToggle() }, enabled = !candidate.alreadyAdded)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (candidate.alreadyAdded) "Already added · ${t.date.display()}" else "${t.date.display()} · ${t.categoryHint ?: "Other"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(t.body, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (income) "+" else "-") + Money.format(t.amountMinor),
            color = if (income) IncomeGreen else ExpenseRed,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
