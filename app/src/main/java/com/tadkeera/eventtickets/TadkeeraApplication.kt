package com.tadkeera.eventtickets

import android.app.Application
import androidx.work.*
import com.tadkeera.eventtickets.util.SyncQueueWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class TadkeeraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Setup automatic background queue sync with Network constraints!
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncQueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TadkeeraSyncQueueWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
