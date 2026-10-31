package io.github.akrishna87.sievr.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.akrishna87.sievr.SievrApp
import io.github.akrishna87.sievr.core.alerts.AlertEngine
import io.github.akrishna87.sievr.core.model.Market
import io.github.akrishna87.sievr.data.UserStore
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Fetches the evening's data in the background and sends the alerts it triggers. */
class DailyUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SievrApp
        app.market.refresh(force = false)
        val market = app.market.market ?: return Result.retry()
        evaluate(applicationContext, market, app.user)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Runs every alert against [market] once per data release. */
        fun evaluate(context: Context, market: Market, store: UserStore) {
            val data = store.data.value
            val stamp = market.snapshot.generatedAt.ifEmpty { market.snapshot.asOf }
            if (data.alertsCheckedFor == stamp) return

            var sent = 0
            for (screen in data.screens.filter { it.alerts }) {
                val change = AlertEngine.screenChange(screen, market, data.screenMatches[screen.id])
                store.setMatches(screen.id, change.current)
                AlertEngine.screenNotification(change)?.let {
                    Notifier.show(context, Notifier.SCREENS, it.key, it.title, it.text)
                    sent++
                }
            }
            for (alert in data.priceAlerts) {
                val n = AlertEngine.triggered(alert, market) ?: continue
                Notifier.show(context, Notifier.PRICES, n.key, n.title, n.text, alert.symbol)
                store.setAlertActive(alert.id, false)
                sent++
            }
            if (data.watchMoveAlert > 0) {
                val watched = data.watchlists.flatMap { it.symbols }.distinct()
                val movers = watched.mapNotNull { sym -> market[sym]?.let { s -> s["chg1d"]?.let { sym to it } } }
                    .filter { abs(it.second) >= data.watchMoveAlert }
                    .sortedByDescending { abs(it.second) }
                if (movers.isNotEmpty()) {
                    val text = movers.take(8).joinToString(", ") { (s, c) -> "$s ${if (c >= 0) "+" else ""}${"%.1f".format(c)}%" }
                    Notifier.show(context, Notifier.PRICES, "movers", "Big moves in your watchlist", text, movers.singleOrNull()?.first)
                    sent++
                }
            }
            if (data.dailyDigest) {
                val stocks = market.stocks
                val up = stocks.count { (it["chg1d"] ?: 0.0) > 0 }
                val down = stocks.count { (it["chg1d"] ?: 0.0) < 0 }
                val highs = stocks.count { it["newHigh52"] == 1.0 }
                val nifty = market.snapshot.indices.firstOrNull { it.name == "NIFTY 50" }?.c?.filterNotNull()
                val niftyChg = if (nifty != null && nifty.size >= 2) (nifty.last() / nifty[nifty.size - 2] - 1) * 100 else null
                val text = buildString {
                    if (niftyChg != null) append("Nifty 50 ${if (niftyChg >= 0) "+" else ""}${"%.2f".format(niftyChg)}%. ")
                    append("$up up, $down down, $highs at 52-week highs.")
                }
                Notifier.show(context, Notifier.DIGEST, "digest", "Market data for ${market.snapshot.asOf}", text)
            }
            store.update { it.copy(alertsCheckedFor = stamp) }
        }
    }
}
