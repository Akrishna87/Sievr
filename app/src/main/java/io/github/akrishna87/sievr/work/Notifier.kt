package io.github.akrishna87.sievr.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.sievr.MainActivity
import io.github.akrishna87.sievr.R

object Notifier {
    const val SCREENS = "screens"
    const val PRICES = "prices"
    const val DIGEST = "digest"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(SCREENS, "Screen alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Stocks entering or leaving your saved screens"
        })
        nm.createNotificationChannel(NotificationChannel(PRICES, "Price alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Your price, RSI and 52-week alerts, and big moves in your watchlist"
        })
        nm.createNotificationChannel(NotificationChannel(DIGEST, "Daily digest", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A summary when the evening's market data arrives"
        })
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, key: String, title: String, text: String, symbol: String? = null) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (symbol != null) putExtra(MainActivity.EXTRA_SYMBOL, symbol)
        }
        val pending = PendingIntent.getActivity(
            context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
