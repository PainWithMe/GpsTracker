package com.dscreate_app.gpstracker.utils

object CaloriesUtils {

    fun calculate(timeInMillis: Long, speed: Float, weight: Float, activityType: String): Float {
        val hours = timeInMillis / 1000.0f / 3600.0f
        val met = getMetForActivity(activityType, speed)
        return (met * weight * hours)
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
