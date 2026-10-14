package com.mybook.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mybook.app.MyBookApp
import com.mybook.app.ui.books.BooksScreen
import com.mybook.app.ui.expenses.ExpenseEditScreen
import com.mybook.app.ui.home.HomeScreen

@Composable
fun MyBookRoot() {
    val container = (LocalContext.current.applicationContext as MyBookApp).container
    val activeBookId by container.prefs.activeBookId.collectAsStateWithLifecycle()
    val bookId = activeBookId
    if (bookId == null) {
        BooksScreen()
    } else {
        key(bookId) { BookNavHost(bookId) }
    }
}

@Composable
private fun BookNavHost(bookId: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                bookId = bookId,
                onAddExpense = { nav.navigate("expense/new") },
                onOpenExpense = { id -> nav.navigate("expense/$id") },
            )
        }
        composable("expense/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            ExpenseEditScreen(
                bookId = bookId,
                expenseId = id?.takeIf { it != "new" },
                onDone = { if (nav.previousBackStackEntry != null) nav.popBackStack() },
            )
        }
    }
}
