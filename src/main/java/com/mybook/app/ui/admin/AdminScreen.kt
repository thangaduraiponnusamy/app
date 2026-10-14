package com.mybook.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.AppContainer
import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.CategoryKind
import com.mybook.app.data.MemberEntity
import com.mybook.app.domain.CategoryPalette
import com.mybook.app.domain.MAX_NAME
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.BackButton
import com.mybook.app.ui.common.CategoryBadge
import com.mybook.app.ui.common.CategoryDialog
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class AdminViewModel(private val bookId: String, container: AppContainer) : ActionViewModel() {
    private val repo = container.repository
    private val started = SharingStarted.WhileSubscribed(5_000)

    val categories = repo.observeCategories().stateIn(viewModelScope, started, emptyList())
    val members = repo.observeMembers(bookId).stateIn(viewModelScope, started, emptyList())

    fun saveCategory(existing: CategoryEntity?, kind: CategoryKind, name: String, emoji: String, color: Long) = action {
        val saved = repo.upsertCategory(existing, kind, name, emoji, color)
        message = if (existing == null) "Added ${saved.name}" else "Updated ${saved.name}"
    }

    fun deleteCategory(category: CategoryEntity) = action {
        repo.deleteCategory(category)
        message = "Deleted ${category.name}"
    }

    fun addMember(name: String) = action {
        val clean = name.trim().take(MAX_NAME)
        require(clean.isNotEmpty()) { "Enter a name" }
        repo.addMember(bookId, clean)
        message = "Added $clean"
    }

    fun removeMember(member: MemberEntity) = action {
        repo.removeMember(member)
        message = "Removed ${member.name}"
    }
}

/** Target of the category dialog: an existing category to edit, or a new one of [kind]. */
private data class CategoryEdit(val kind: CategoryKind, val existing: CategoryEntity?)

@Composable
fun AdminScreen(bookId: String, onBack: () -> Unit) {
    val vm = appViewModel { AdminViewModel(bookId, it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<CategoryEdit?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var removing by remember { mutableStateOf<MemberEntity?>(null) }
    var memberName by rememberSaveable { mutableStateOf("") }
    MessageEffect(vm, snackbar)

    Scaffold(
        topBar = { ScreenHeader("Admin", "Categories & members", navigation = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            CategoryKind.entries.forEach { kind ->
                item {
                    SectionHeader(
                        if (kind == CategoryKind.EXPENSE) "Expense categories" else "Income categories",
                        action = "Add",
                        onAction = { editing = CategoryEdit(kind, null) },
                    )
                }
                items(categories.filter { it.kind == kind }, key = { it.id }) { category ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(category.emoji, Color(category.color))
                        Spacer(Modifier.width(14.dp))
                        Text(category.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = CategoryEdit(kind, category) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${category.name}")
                        }
                        IconButton(onClick = { deleting = category }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
                        }
                    }
                }
            }

            item { SectionHeader("Members of this book") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = memberName,
                        onValueChange = { memberName = it.take(MAX_NAME) },
                        label = { Text("Name (e.g. Durai)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { vm.addMember(memberName); memberName = "" }, enabled = memberName.isNotBlank()) { Text("Add") }
                }
            }
            items(members, key = { it.id }) { member ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text(member.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { removing = member }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${member.name}") }
                }
            }
        }
    }

    editing?.let { edit ->
        CategoryDialog(
            existing = edit.existing,
            onDismiss = { editing = null },
            onSave = { name, emoji, color ->
                vm.saveCategory(edit.existing, edit.kind, name, emoji, color)
                editing = null
            },
        )
    }
    deleting?.let { category ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${category.name}?") },
            text = { Text("Categories that are already used by entries or reminders can't be deleted.") },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(category); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${member.name}?") },
            text = { Text("Members who already have entries in this book can't be removed.") },
            confirmButton = { TextButton(onClick = { vm.removeMember(member); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
