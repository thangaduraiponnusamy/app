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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.mybook.app.domain.Money
import com.mybook.app.ui.common.ActionViewModel
import com.mybook.app.ui.common.Hint
import com.mybook.app.ui.common.MessageEffect
import com.mybook.app.ui.common.appViewModel
import com.mybook.app.ui.theme.BrandGradient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class BooksViewModel(private val repo: BookRepository, private val prefs: Prefs) : ActionViewModel() {
    val books = repo.observeBooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(id: String) = prefs.setActiveBook(id)

    fun create(name: String, currency: String, membersText: String) = action {
        val cleanName = name.trim()
        val cleanCurrency = currency.trim().uppercase()
        require(cleanName.isNotEmpty()) { "Enter a book name" }
        require(Money.isValidCurrency(cleanCurrency)) { "Unknown currency code: $cleanCurrency" }
        val names = membersText.split(',')
            .map { it.trim().take(MAX_NAME) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .ifEmpty { listOf("Me") }
        repo.createBook(cleanName.take(60), cleanCurrency, names)
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New book") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { Header() }
            if (books.isEmpty()) {
                item {
                    Hint("No books yet. Create one for your home, flatmates or next trip, and add who shares the expenses.")
                }
            }
            items(books, key = { it.id }) { book -> BookCard(book) { vm.open(book.id) } }
        }
    }

    if (showCreate) {
        CreateBookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, currency, members ->
                showCreate = false
                vm.create(name, currency, members)
            },
        )
    }
}

@Composable
private fun Header() = Column {
    Box(
        Modifier
            .fillMaxWidth()
            .background(BrandGradient, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("MyBook", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Split, track & settle together", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Your books",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BookCard(book: BookEntity, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(BrandGradient, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    book.name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.titleMedium)
                Text(book.currency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CreateBookDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(Money.DEFAULT_CURRENCY) }
    var members by rememberSaveable { mutableStateOf("Me") }
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
                    value = currency,
                    onValueChange = { currency = it.take(3).uppercase() },
                    label = { Text("Currency (INR, EUR, USD...)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = members,
                    onValueChange = { members = it.take(400) },
                    label = { Text("Members, comma separated") },
                    supportingText = { Text("e.g. Durai, Thanga") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, currency, members) }, enabled = name.isNotBlank() && currency.length == 3) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
