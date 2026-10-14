package com.mybook.app.ui.books

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mybook.app.R
import com.mybook.app.data.BookEntity
import com.mybook.app.data.BookRepository
import com.mybook.app.data.Prefs
import com.mybook.app.domain.MAX_NAME
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.ScreenHeader
import com.mybook.app.ui.common.SectionHeader
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.theme.BrandGradient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class BooksViewModel(private val repo: BookRepository, private val prefs: Prefs) : ActionViewModel() {
    val books = repo.observeBooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(id: String) = prefs.setActiveBook(id)

    fun create(name: String, membersText: String) = action {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Enter a book name" }
        val names = membersText.split(',')
            .map { it.trim().take(MAX_NAME) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .ifEmpty { listOf("Me") }
        repo.createBook(cleanName.take(60), names)
    }
}

@Composable
fun BooksScreen() {
    val vm = appViewModel { BooksViewModel(it.repository, it.prefs) }
    val books by vm.books.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    Scaffold(
        topBar = { ScreenHeader(title = "MyBook", subtitle = "Split, track & settle together") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Filled.Add, contentDescription = "New book", tint = Color.White) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { Hero() }
            item { SectionHeader("Your books") }
            if (books.isEmpty()) {
                item { Hint("No books yet. Tap + to create one for your home, flatmates or next trip.") }
            }
            items(books, key = { it.id }) { book -> BookRow(book) { vm.open(book.id) } }
        }
    }

    if (showCreate) {
        CreateBookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, members ->
                showCreate = false
                vm.create(name, members)
            },
        )
    }
}

@Composable
private fun Hero() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BrandGradient)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Track every rupee", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Expenses, income, reminders and settle-ups in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun BookRow(book: BookEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(BrandGradient, CircleShape), contentAlignment = Alignment.Center) {
            Text(book.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Text(book.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreateBookDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Book name (e.g. Home, Goa trip)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = members,
                    onValueChange = { members = it.take(400) },
                    label = { Text("Members, comma separated") },
                    placeholder = { Text("Durai, Thanga") },
                    supportingText = { Text("e.g. Durai, Thanga · amounts are in ₹ INR") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, members) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
