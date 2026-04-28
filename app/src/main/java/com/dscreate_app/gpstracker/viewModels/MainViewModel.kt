package com.dscreate_app.gpstracker.viewModels

import androidx.lifecycle.*
import com.dscreate_app.gpstracker.database.*
import com.dscreate_app.gpstracker.location.LocationModel
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(db: MainDb): ViewModel() {

    private val dao = db.getDao()
    val locationUpdates = MutableLiveData<LocationModel>()
    val timeData = MutableLiveData<String>()
    val tracks = dao.getAllTracks().asLiveData()
    val currentTrack = MutableLiveData<TrackItem>()
    val userProfile = dao.getUserProfile().asLiveData()

    fun insertTrack(trackItem: TrackItem) = viewModelScope.launch {
        dao.insertTrack(trackItem)
    }

    fun deleteTrack(trackItem: TrackItem) = viewModelScope.launch {
        dao.deleteTrack(trackItem)
    }

    fun insertUserProfile(userProfile: UserProfile) = viewModelScope.launch {
        dao.insertUserProfile(userProfile)
    }

    // --- Общая статистика ---
    fun getTotalDistance() = dao.getTotalDistance().asLiveData()
    fun getTotalTime() = dao.getTotalTime().asLiveData()
    fun getTotalCalories() = dao.getTotalCalories().asLiveData()
    fun getAverageSpeed() = dao.getAverageSpeed().asLiveData()

    fun getMaxDistance() = dao.getMaxDistance().asLiveData()
    fun getMaxTime() = dao.getMaxTime().asLiveData()
    fun getMaxSpeed() = dao.getMaxSpeed().asLiveData()
    fun getMaxCalories() = dao.getMaxCalories().asLiveData()

    // --- Статистика по конкретному виду активности ---
    fun getTotalDistanceByType(type: String) = dao.getTotalDistanceByType(type).asLiveData()
    fun getTotalTimeByType(type: String) = dao.getTotalTimeByType(type).asLiveData()
    fun getTotalCaloriesByType(type: String) = dao.getTotalCaloriesByType(type).asLiveData()
    fun getAverageSpeedByType(type: String) = dao.getAverageSpeedByType(type).asLiveData()

    fun getMaxDistanceByType(type: String) = dao.getMaxDistanceByType(type).asLiveData()
    fun getMaxTimeByType(type: String) = dao.getMaxTimeByType(type).asLiveData()
    fun getMaxSpeedByType(type: String) = dao.getMaxSpeedByType(type).asLiveData()
    fun getMaxCaloriesByType(type: String) = dao.getMaxCaloriesByType(type).asLiveData()

    // --- Графики ---
    fun getActivityCount(): LiveData<List<ActivityCount>> = dao.getActivityCount().asLiveData()

    fun getTracksForPeriod(activityType: String, startDate: Long, endDate: Long): LiveData<List<TrackItem>> {
        return dao.getTracksForPeriod(activityType, startDate, endDate).asLiveData()
    }

    fun getCaloriesByActivity(): LiveData<List<ActivityCalories>> = dao.getCaloriesByActivity().asLiveData()

    fun getCaloriesByDate(activityType: String, startDate: Long, endDate: Long): LiveData<List<DatePoints>> {
        return dao.getCaloriesByDate(activityType, startDate, endDate).asLiveData()
    }

    // --- Логика Виртуального Тренера ---

    private val defaultGreetings = listOf(
        "Хороший день для прогулки, %s! Какую цель поставим сегодня?",
        "Привет, %s! Твои кроссовки уже заждались тренировки.",
        "Движение — это жизнь. %s, может, пройдем пару километров?",
        "Как настрой, %s? Сегодня отличный момент, чтобы стать немного сильнее.",
        "Привет! Твой организм скажет спасибо за небольшую активность сегодня."
    )

    fun getTrainingAdvice(name: String): LiveData<String?> {
        val resultAdvice = MediatorLiveData<String?>()

        val lastDateLD = dao.getLastTrackDate().asLiveData()
        val lastSpeedLD = dao.getLastTrackSpeed().asLiveData()
        val avgSpeedLD = dao.getAverageSpeed().asLiveData()
        val maxDistLD = dao.getMaxDistance().asLiveData()

        fun update() {
            val lastDate = lastDateLD.value
            val lastSpeed = lastSpeedLD.value
            val avgSpeed = avgSpeedLD.value
            val maxDist = maxDistLD.value
            val currentTracks = tracks.value

            if (currentTracks.isNullOrEmpty()) {
                resultAdvice.value = "Привет, $name! Давай запишем твой первый маршрут сегодня?"
                return
            }

            // 1. Проверка на долгий перерыв (3 дня)
            if (lastDate != null && (System.currentTimeMillis() - lastDate) > 259200000L) {
                resultAdvice.value = "Мы не тренировались уже несколько дней. Пора размяться!"
                return
            }

            // 2. Проверка на рекорд дистанции
            val lastTrack = currentTracks.first()
            if (maxDist != null && lastTrack.distance >= maxDist && lastTrack.distance > 0) {
                resultAdvice.value = "Ух ты! Последний маршрут стал твоим рекордом по дистанции. Так держать!"
                return
            }

            // 3. Проверка на прогресс скорости
            if (lastSpeed != null && avgSpeed != null && lastSpeed > avgSpeed) {
                val diff = (((lastSpeed - avgSpeed) / avgSpeed) * 100).toInt()
                if (diff > 5) {
                    resultAdvice.value = "Отличный темп! Твоя скорость сегодня на $diff% выше средней."
                    return
                }
            }

            // Случайный совет
            val index = Random(System.currentTimeMillis() / 10000).nextInt(defaultGreetings.size)
            resultAdvice.value = String.format(defaultGreetings[index], name)
        }

        resultAdvice.addSource(lastDateLD) { update() }
        resultAdvice.addSource(lastSpeedLD) { update() }
        resultAdvice.addSource(avgSpeedLD) { update() }
        resultAdvice.addSource(maxDistLD) { update() }
        resultAdvice.addSource(tracks) { update() }

        return resultAdvice
    }
}