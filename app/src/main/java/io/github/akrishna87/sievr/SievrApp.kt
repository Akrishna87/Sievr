package io.github.akrishna87.sievr

import android.app.Application
import io.github.akrishna87.sievr.data.MarketRepository
import io.github.akrishna87.sievr.data.UserStore
import io.github.akrishna87.sievr.work.Notifier
import io.github.akrishna87.sievr.work.DailyUpdateWorker

class SievrApp : Application() {
    lateinit var market: MarketRepository
        private set
    lateinit var user: UserStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        market = MarketRepository(this)
        user = UserStore(this)
        Notifier.createChannels(this)
        DailyUpdateWorker.schedule(this)
    }

    companion object {
        lateinit var instance: SievrApp
            private set
    }
}
