package io.github.akrishna87.sievr.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.ui.backtest.BacktestScreen
import io.github.akrishna87.sievr.ui.market.MarketScreen
import io.github.akrishna87.sievr.ui.market.SectorsScreen
import io.github.akrishna87.sievr.ui.more.AboutScreen
import io.github.akrishna87.sievr.ui.more.AlertsScreen
import io.github.akrishna87.sievr.ui.more.CompareScreen
import io.github.akrishna87.sievr.ui.more.FormulaHelpScreen
import io.github.akrishna87.sievr.ui.more.MoreScreen
import io.github.akrishna87.sievr.ui.more.SettingsScreen
import io.github.akrishna87.sievr.ui.portfolio.PortfolioScreen
import io.github.akrishna87.sievr.ui.screener.ScreenEditor
import io.github.akrishna87.sievr.ui.screener.ScreensHome
import io.github.akrishna87.sievr.ui.stock.StockScreen
import io.github.akrishna87.sievr.ui.theme.SievrTheme
import io.github.akrishna87.sievr.ui.watchlist.WatchlistScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.MARKET, "Market", Icons.Filled.Insights),
    Tab(Routes.SCREENS, "Screens", Icons.Filled.FilterAlt),
    Tab(Routes.WATCHLIST, "Watchlist", Icons.AutoMirrored.Filled.List),
    Tab(Routes.PORTFOLIO, "Portfolio", Icons.Filled.PieChart),
    Tab(Routes.MORE, "More", Icons.Filled.MoreHoriz),
)

@Composable
fun SievrRoot(pendingSymbol: MutableState<String?>) {
    val app = SievrApp.instance
    val user by app.user.data.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { app.market.start() }
    LaunchedEffect(Unit) { app.updates.check() }
    val update by app.updates.available.collectAsStateWithLifecycle()

    // Run alerts once new data has loaded while the app is open.
    val marketState by app.market.state.collectAsStateWithLifecycle()
    LaunchedEffect(app.market.market?.snapshot?.generatedAt) {
        app.market.market?.let { io.github.akrishna87.sievr.work.DailyUpdateWorker.evaluate(context, it, app.user) }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    SievrTheme(user.theme, user.fontScale, user.fontFamily) {
        val nav = rememberNavController()
        val navigator = remember(nav) { Navigator(nav) }

        LaunchedEffect(pendingSymbol.value, marketState) {
            val sym = pendingSymbol.value ?: return@LaunchedEffect
            if (app.market.market != null) {
                navigator.stock(sym)
                pendingSymbol.value = null
            }
        }

        if (!user.acceptedDisclaimer) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Welcome to Sievr") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "Sievr is a free stock screener for companies listed on NSE and BSE. It works out every ratio, score and " +
                                "screen on your phone from end-of-day market data that updates each weekday evening.\n\n" +
                                "Sievr is a research tool, not investment advice. It is not registered with SEBI as an investment " +
                                "adviser or research analyst. Data can be late, incomplete or wrong; check important figures with " +
                                "company filings before you act, and consider talking to a registered adviser.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        app.user.update { it.copy(acceptedDisclaimer = true) }
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("I understand") }
                },
            )
        }

        CompositionLocalProvider(LocalNav provides navigator) {
            val entry by nav.currentBackStackEntryAsState()
            val current = entry?.destination
            val showBar = tabs.any { t -> current?.hierarchy?.any { it.route == t.route } == true }
            Scaffold(
                bottomBar = {
                    if (showBar) Column {
                    update?.let { v -> UpdateBar(v.versionName) { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(app.updates.apkUrl))) } }
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current?.hierarchy?.any { it.route == tab.route } == true,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                    }
                },
            ) { padding ->
                NavHost(nav, startDestination = Routes.MARKET, modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
                    .consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))) {
                    composable(Routes.MARKET) { MarketScreen() }
                    composable(Routes.SCREENS) { ScreensHome() }
                    composable(Routes.WATCHLIST) { WatchlistScreen() }
                    composable(Routes.PORTFOLIO) { PortfolioScreen() }
                    composable(Routes.MORE) { MoreScreen() }
                    composable(Routes.STOCK, arguments = listOf(navArgument("symbol") { type = NavType.StringType })) {
                        StockScreen(it.arguments?.getString("symbol").orEmpty())
                    }
                    composable(Routes.SCREEN) { ScreenEditor() }
                    composable(Routes.BACKTEST) { BacktestScreen() }
                    composable(Routes.ALERTS) { AlertsScreen() }
                    composable(Routes.COMPARE, arguments = listOf(navArgument("symbols") { type = NavType.StringType; defaultValue = "" })) {
                        CompareScreen(it.arguments?.getString("symbols").orEmpty().split(',').filter { s -> s.isNotBlank() })
                    }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(Routes.ABOUT) { AboutScreen() }
                    composable(Routes.FORMULA_HELP) { FormulaHelpScreen() }
                    composable(Routes.SECTORS) { SectorsScreen() }
                }
            }
        }
    }
}

/** "A new version is ready" strip above the tabs; tapping downloads the APK in the browser. */
@Composable
private fun UpdateBar(versionName: String, onUpdate: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Sievr $versionName is ready", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onUpdate) { Text("Update") }
        }
    }
}
