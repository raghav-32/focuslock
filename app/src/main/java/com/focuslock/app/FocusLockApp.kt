package com.focuslock.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.focuslock.app.system.UsageSnapshotWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FocusLockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.focuslock.app.system.Diagnostics.installCrashHandler(this)
        com.focuslock.app.system.Diagnostics.log(this, "process started")
        container = AppContainer(this)
        container.notifier.ensureChannels()
        container.scope.launch {
            runCatching {
                container.rules.seedDefaultFeedRulesIfEmpty()
                container.maintenance.run()
            }
        }
        // Safety net for alarms that Doze delayed or the OS dropped. 15 minutes is WorkManager's minimum.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UsageSnapshotWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageSnapshotWorker>(30, TimeUnit.MINUTES).build(),
        )
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as FocusLockApp).container
