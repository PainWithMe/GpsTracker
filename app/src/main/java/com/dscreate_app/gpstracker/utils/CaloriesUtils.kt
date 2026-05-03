package com.dscreate_app.gpstracker.utils

import kotlin.math.abs

object CaloriesUtils {

    // 1. Стандартная формула (MET)
    fun calculateMET(timeInMillis: Long, speed: Float, weight: Float, activityType: String): Float {
        val hours = timeInMillis / 1000.0f / 3600.0f
        val met = getMetForActivity(activityType, speed)
        return (met * weight * hours)
    }

    // 2. Упрощенная формула (По дистанции)
    fun calculateByDistance(distanceMeters: Float, weight: Float, activityType: String): Float {
        val distanceKm = distanceMeters / 1000f
        val factor = when (activityType) {
            "Бег" -> 1.03f
            "Велосипед" -> 0.4f
            else -> 0.5f // Ходьба и прочее
        }
        return factor * weight * distanceKm
    }

    // 3. Адаптивная формула (ACSM) - учитывает вертикальный подъем
    fun calculateAdaptive(
        timeInMillis: Long, 
        speedMs: Float, 
        weight: Float, 
        activityType: String,
        altitudeDiff: Double
    ): Float {
        if (speedMs < 0.1f) return 0f
        
        val timeSec = timeInMillis / 1000f
        val distance = speedMs * timeSec
        // Уклон в процентах
        val grade = if (distance > 0) abs(altitudeDiff) / distance else 0.0
        
        // VO2 = (0.1 * speed) + (1.8 * speed * grade) + 3.5
        val vo2 = (0.1f * speedMs * 60) + (1.8f * speedMs * 60 * grade.toFloat()) + 3.5f
        
        // Перевод VO2 в калории: (VO2 * вес / 1000) * 5 ккал * время в минутах
        val caloriesPerMin = (vo2 * weight / 1000f) * 5f
        return caloriesPerMin * (timeSec / 60f)
    }

    private fun getMetForActivity(activity: String, speed: Float): Float {
        val speedInKmH = speed * 3.6f
        return when (activity) {
            "Ходьба" -> when {
                speedInKmH < 4 -> 2.8f
                speedInKmH < 6 -> 3.5f
                else -> 5.0f
            }
            "Скандинавская ходьба" -> 4.8f
            "Бег" -> when {
                speedInKmH < 8 -> 7.0f
                speedInKmH < 11 -> 9.8f
                speedInKmH < 14 -> 12.3f
                else -> 15.0f
            }
            "Велосипед" -> when {
                speedInKmH < 15 -> 5.8f
                speedInKmH < 20 -> 8.0f
                else -> 10.0f
            }
            else -> 3.5f
        }
    }
}
