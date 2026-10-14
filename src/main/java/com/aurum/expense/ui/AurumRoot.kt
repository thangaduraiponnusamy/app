package com.aurum.expense.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurum.expense.AurumApp
import com.aurum.expense.ui.auth.SignInScreen
import com.aurum.expense.ui.expenses.ExpenseEditScreen
import com.aurum.expense.ui.home.HomeScreen
import com.aurum.expense.ui.tenants.TenantsScreen

@Composable
fun AurumRoot() {
    val container = (LocalContext.current.applicationContext as AurumApp).container
    val session by container.session.state.collectAsStateWithLifecycle()
    val tenantId = session.activeTenantId
    when {
        !session.isSignedIn -> SignInScreen()
        tenantId == null -> TenantsScreen()
        else -> key(tenantId) { TenantNavHost(tenantId) }
    }
}

@Composable
private fun TenantNavHost(tenantId: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                tenantId = tenantId,
                onAddExpense = { nav.navigate("expense/new") },
                onOpenExpense = { id -> nav.navigate("expense/$id") },
            )
        }
        composable("expense/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            ExpenseEditScreen(
                tenantId = tenantId,
                expenseId = id?.takeIf { it != "new" },
                onDone = { if (nav.previousBackStackEntry != null) nav.popBackStack() },
            )
        }
    }
}
