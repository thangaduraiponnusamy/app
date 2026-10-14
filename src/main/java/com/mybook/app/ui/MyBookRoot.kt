package com.mybook.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mybook.app.MyBookApp
import com.mybook.app.ui.admin.AdminScreen
import com.mybook.app.ui.alerts.AlertsScreen
import com.mybook.app.ui.balances.BalancesScreen
import com.mybook.app.ui.books.BooksScreen
import com.mybook.app.ui.expenses.CategoryEntriesScreen
import com.mybook.app.ui.expenses.EntryEditScreen
import com.mybook.app.ui.expenses.EntryKind
import com.mybook.app.ui.home.HomeNav
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
    val back: () -> Unit = { nav.popIfPossible() }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                bookId = bookId,
                nav = HomeNav(
                    addExpense = { nav.navigate("entry/${EntryKind.EXPENSE.name}/new") },
                    addIncome = { nav.navigate("entry/${EntryKind.INCOME.name}/new") },
                    openEntry = { id -> nav.navigate("entry/${EntryKind.EXPENSE.name}/$id") },
                    openCategory = { id -> nav.navigate("category/$id") },
                    openAlerts = { nav.navigate("alerts") },
                    openAdmin = { nav.navigate("admin") },
                    openBalances = { nav.navigate("balances") },
                ),
            )
        }
        composable("entry/{kind}/{id}") { entry ->
            val kind = EntryKind.entries.firstOrNull { it.name == entry.arguments?.getString("kind") } ?: EntryKind.EXPENSE
            EntryEditScreen(
                bookId = bookId,
                entryId = entry.arguments?.getString("id")?.takeIf { it != "new" },
                kind = kind,
                onDone = back,
            )
        }
        composable("category/{id}") { entry ->
            CategoryEntriesScreen(
                bookId = bookId,
                categoryId = entry.arguments?.getString("id")?.takeIf { it != "all" },
                onBack = back,
                onOpenEntry = { id -> nav.navigate("entry/${EntryKind.EXPENSE.name}/$id") },
            )
        }
        composable("alerts") { AlertsScreen(bookId, onBack = back) }
        composable("admin") { AdminScreen(bookId, onBack = back) }
        composable("balances") { BalancesScreen(bookId, onBack = back) }
    }
}

private fun NavHostController.popIfPossible() {
    if (previousBackStackEntry != null) popBackStack()
}
