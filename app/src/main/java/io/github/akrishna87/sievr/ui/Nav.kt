package io.github.akrishna87.sievr.ui

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import io.github.akrishna87.sievr.core.screen.Screen

object Routes {
    const val MARKET = "market"
    const val SCREENS = "screens"
    const val WATCHLIST = "watchlist"
    const val PORTFOLIO = "portfolio"
    const val MORE = "more"
    const val STOCK = "stock/{symbol}"
    const val SCREEN = "screen"
    const val BACKTEST = "backtest"
    const val ALERTS = "alerts"
    const val COMPARE = "compare?symbols={symbols}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val FORMULA_HELP = "formula-help"
    const val SECTORS = "sectors"
}

/** The screen being edited or backtested; passed in memory because it can be large. */
object ScreenDraft {
    var screen: Screen? = null
    /** True when the draft is a saved screen being edited rather than a preset or a new one. */
    var saved: Boolean = false
}

class Navigator(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun stock(symbol: String) = nav.navigate("stock/" + Uri.encode(symbol))
    fun screen(screen: Screen, saved: Boolean) {
        ScreenDraft.screen = screen
        ScreenDraft.saved = saved
        nav.navigate(Routes.SCREEN)
    }
    fun backtest(screen: Screen) {
        ScreenDraft.screen = screen
        nav.navigate(Routes.BACKTEST)
    }
    fun compare(symbols: List<String> = emptyList()) = nav.navigate("compare?symbols=" + Uri.encode(symbols.joinToString(",")))
    fun go(route: String) = nav.navigate(route)
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No navigator") }
