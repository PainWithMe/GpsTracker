package com.dscreate_app.gpstracker.database

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dscreate_app.gpstracker.utils.CoachReminderWorker
import java.util.concurrent.TimeUnit

class MainApp: Application() {
    val database by lazy { MainDb.getInstanceDb(this) }

    override fun onCreate() {
        super.onCreate()
        setupCoachReminders()
    }

    private fun setupCoachReminders() {
        val workRequest = PeriodicWorkRequestBuilder<CoachReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(12, TimeUnit.HOURS) // Запуск не сразу, а через полдня
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CoachReminder",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}