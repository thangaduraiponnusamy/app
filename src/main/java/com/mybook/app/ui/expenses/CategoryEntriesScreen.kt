package com.mybook.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.data.ExpenseType
import com.mybook.app.domain.Money
import com.mybook.app.domain.Period
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.PeriodBar
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.TransactionRow
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.theme.BlueGradient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class CategoryEntriesViewModel(bookId: String, container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val entries = repo.observeExpenses(bookId).stateIn(viewModelScope, started, emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, started, emptyList())
    val members = repo.observeMembers(bookId).stateIn(viewModelScope, started, emptyList())
}

/** Entries of one category, or all expenses when [categoryId] is null. */
@Composable
fun CategoryEntriesScreen(bookId: String, categoryId: String?, onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    val vm = appViewModel { CategoryEntriesViewModel(bookId, it) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val names = remember(members) { members.associate { it.id to it.name } }
    val category = categoryId?.let { categoryMap[it] }
    val visible = remember(entries, categoryId, period, anchor) {
        val range = period.range(anchor)
        entries.filter {
            it.date in range && if (categoryId == null) it.type != ExpenseType.INCOME else it.categoryId == categoryId
        }
    }
    val total = visible.filter { it.type != ExpenseType.SETTLEMENT }.sumOf { it.amountMinor }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = category?.let { "${it.emoji} ${it.name}" } ?: "All expenses",
                subtitle = period.title(anchor),
                navigation = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { PeriodBar(period, anchor, { period = it }, { anchor = it }) }
            item { TotalCard(total, visible.size) }
            if (visible.isEmpty()) item { Hint("Nothing recorded in this period.") }
            items(visible, key = { it.id }) { entry ->
                TransactionRow(entry, names, categoryMap) { onOpenEntry(entry.id) }
            }
        }
    }
}

@Composable
private fun TotalCard(total: Long, count: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BlueGradient)
            .padding(22.dp),
    ) {
        Text("Total", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        Text(Money.format(total), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("$count entries", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}
