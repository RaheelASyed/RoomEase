package edu.virginia.cs4720.roomease.ui.main

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import edu.virginia.cs4720.roomease.RoomEaseApp
import edu.virginia.cs4720.roomease.ui.auth.GoogleSignIn
import edu.virginia.cs4720.roomease.ui.chat.ChatRoute
import edu.virginia.cs4720.roomease.ui.chores.AddChoreRoute
import edu.virginia.cs4720.roomease.ui.chores.ChoresRoute
import edu.virginia.cs4720.roomease.ui.expenses.AddExpenseRoute
import edu.virginia.cs4720.roomease.ui.expenses.ExpensesRoute
import edu.virginia.cs4720.roomease.ui.expenses.ReceiptScanRoute
import edu.virginia.cs4720.roomease.ui.session.SessionState
import edu.virginia.cs4720.roomease.ui.settings.SettingsRoute
import kotlinx.coroutines.launch

/** String routes, like Lecture 7's NavHost("list") / "detail/{id}". */
object Routes {
    const val CHORES = "chores"
    const val EXPENSES = "expenses"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val NEW_CHORE = "chores/new"
    const val NEW_EXPENSE = "expenses/new"
    const val SCAN_RECEIPT = "expenses/new/scan"
}

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

// Four destinations (Lecture 8 Miller's law: bottom navigation caps at 5).
private val tabs = listOf(
    Tab(Routes.CHORES, "Chores", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    Tab(Routes.EXPENSES, "Expenses", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab(Routes.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * The signed-in app. rememberNavController saves the back stack, so the current tab and
 * any open form survive rotation and process death. Back: sub-screens pop to their tab;
 * from any tab, Back returns to Chores, then leaves the app.
 */
@Composable
fun MainScreen(session: SessionState.Ready, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val container = (context.applicationContext as RoomEaseApp).container
    val scope = rememberCoroutineScope()

    // A queued write the server later rejected (e.g. by a security rule) surfaces here on any screen.
    LaunchedEffect(Unit) {
        container.writeErrors.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHORES,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        ) {
            composable(Routes.CHORES) {
                ChoresRoute(session = session, onNewChore = { navController.navigate(Routes.NEW_CHORE) { launchSingleTop = true } })
            }
            composable(Routes.NEW_CHORE) {
                AddChoreRoute(session = session, onDone = { navController.popBackStack() })
            }
            composable(Routes.EXPENSES) {
                ExpensesRoute(session = session, onNewExpense = { navController.navigate(Routes.NEW_EXPENSE) { launchSingleTop = true } })
            }
            composable(Routes.NEW_EXPENSE) { entry ->
                AddExpenseRoute(
                    session = session,
                    viewModelStoreOwner = entry,
                    onScanReceipt = { navController.navigate(Routes.SCAN_RECEIPT) { launchSingleTop = true } },
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Routes.SCAN_RECEIPT) { entry ->
                // Shares the add-expense form's ViewModel so the scan result lands in that form.
                val formEntry = remember(entry) { navController.getBackStackEntry(Routes.NEW_EXPENSE) }
                ReceiptScanRoute(
                    session = session,
                    viewModelStoreOwner = formEntry,
                    onFinished = { navController.popBackStack() }
                )
            }
            composable(Routes.CHAT) {
                ChatRoute(session = session)
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    session = session,
                    onSignOut = {
                        scope.launch {
                            GoogleSignIn.clearCredentialState(context)
                            onSignOut()
                        }
                    }
                )
            }
        }
    }
}
