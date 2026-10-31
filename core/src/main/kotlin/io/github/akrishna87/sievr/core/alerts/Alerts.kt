package io.github.akrishna87.sievr.core.alerts

import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.core.screen.Screen
import io.github.akrishna87.sievr.core.screen.ScreenEngine
import kotlinx.serialization.Serializable

@Serializable
enum class PriceCondition { ABOVE, BELOW, MOVE_UP_PERCENT, MOVE_DOWN_PERCENT, RSI_BELOW, RSI_ABOVE, NEW_52W_HIGH, NEW_52W_LOW }

@Serializable
data class PriceAlert(
    val id: String,
    val symbol: String,
    val condition: PriceCondition,
    val value: Double = 0.0,
    /** Once fired, a price alert stays off until the user turns it back on. */
    val active: Boolean = true,
)

data class Notification(val key: String, val title: String, val text: String, val symbols: List<String>)

/** Works out what to tell the user after each day's data arrives. */
object AlertEngine {

    data class ScreenChange(val screen: Screen, val entered: List<String>, val exited: List<String>, val current: Set<String>)

    /** Compares a screen's results with the [previous] symbols seen. */
    fun screenChange(screen: Screen, market: Market, previous: Set<String>?): ScreenChange {
        val now = ScreenEngine.run(screen, market.stocks).map { it.symbol }.toSet()
        if (previous == null) return ScreenChange(screen, emptyList(), emptyList(), now)
        return ScreenChange(screen, (now - previous).sorted(), (previous - now).sorted(), now)
    }

    fun screenNotification(change: ScreenChange): Notification? {
        if (change.entered.isEmpty() && change.exited.isEmpty()) return null
        val parts = buildList {
            if (change.entered.isNotEmpty()) add("New: " + change.entered.take(6).joinToString(", ") + more(change.entered.size, 6))
            if (change.exited.isNotEmpty()) add("Left: " + change.exited.take(4).joinToString(", ") + more(change.exited.size, 4))
        }
        return Notification("screen:${change.screen.id}", change.screen.name, parts.joinToString(" · "), change.entered + change.exited)
    }

    fun triggered(alert: PriceAlert, market: Market): Notification? {
        if (!alert.active) return null
        val stock = market[alert.symbol] ?: return null
        val price = stock["price"] ?: return null
        val chg = stock["chg1d"] ?: 0.0
        val rsi = stock["rsi14"]
        val fired = when (alert.condition) {
            PriceCondition.ABOVE -> price >= alert.value
            PriceCondition.BELOW -> price <= alert.value
            PriceCondition.MOVE_UP_PERCENT -> chg >= alert.value
            PriceCondition.MOVE_DOWN_PERCENT -> chg <= -alert.value
            PriceCondition.RSI_BELOW -> rsi != null && rsi <= alert.value
            PriceCondition.RSI_ABOVE -> rsi != null && rsi >= alert.value
            PriceCondition.NEW_52W_HIGH -> stock["newHigh52"] == 1.0
            PriceCondition.NEW_52W_LOW -> stock["newLow52"] == 1.0
        }
        if (!fired) return null
        return Notification("price:${alert.id}", "${stock.symbol} ${describe(alert)}",
            "Closed at ₹${"%,.2f".format(price)} (${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}%)", listOf(stock.symbol))
    }

    fun describe(alert: PriceAlert): String = when (alert.condition) {
        PriceCondition.ABOVE -> "crossed above ₹${"%,.2f".format(alert.value)}"
        PriceCondition.BELOW -> "fell below ₹${"%,.2f".format(alert.value)}"
        PriceCondition.MOVE_UP_PERCENT -> "rose ${"%.1f".format(alert.value)}%+ in a day"
        PriceCondition.MOVE_DOWN_PERCENT -> "fell ${"%.1f".format(alert.value)}%+ in a day"
        PriceCondition.RSI_BELOW -> "RSI under ${"%.0f".format(alert.value)}"
        PriceCondition.RSI_ABOVE -> "RSI over ${"%.0f".format(alert.value)}"
        PriceCondition.NEW_52W_HIGH -> "hit a new 52-week high"
        PriceCondition.NEW_52W_LOW -> "hit a new 52-week low"
    }

    private fun more(n: Int, shown: Int) = if (n > shown) " +${n - shown} more" else ""
}
