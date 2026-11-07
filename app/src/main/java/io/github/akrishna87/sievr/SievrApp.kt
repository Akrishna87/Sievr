package io.github.akrishna87.sievr

import android.app.Application
import io.github.akrishna87.sievr.data.MarketRepository
import io.github.akrishna87.sievr.data.MfRepository
import io.github.akrishna87.sievr.data.UpdateChecker
import io.github.akrishna87.sievr.data.UserStore
import io.github.akrishna87.sievr.work.Notifier
import io.github.akrishna87.sievr.work.DailyUpdateWorker

class SievrApp : Application() {
    lateinit var market: MarketRepository
        private set
    lateinit var user: UserStore
        private set
    lateinit var updates: UpdateChecker
        private set
    lateinit var mf: MfRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        market = MarketRepository(this)
        user = UserStore(this)
        updates = UpdateChecker(this)
        mf = MfRepository(this)
        Notifier.createChannels(this)
        DailyUpdateWorker.schedule(this)
    }

    companion object {
        lateinit var instance: SievrApp
            private set
    }
}
