package com.dscreate_app.gpstracker.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dscreate_app.gpstracker.database.MainApp
import kotlinx.coroutines.flow.first

class CoachReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dao = (applicationContext as MainApp).database.getDao()
        val lastTrackDate = dao.getLastTrackDate().first()

        if (lastTrackDate != null) {
            val diff = System.currentTimeMillis() - lastTrackDate
            if (diff > 259200000L) { // Больше 3 дней
                NotificationUtils.showCoachNotification(
                    applicationContext,
                    "Виртуальный тренер",
                    "Мы не тренировались уже 3 дня. Пора выйти на небольшую прогулку! 💪"
                )
            }
        }
        return Result.success()
    }
}
