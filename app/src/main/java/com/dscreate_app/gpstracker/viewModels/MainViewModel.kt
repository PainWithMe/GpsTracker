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

    // --- Расширенная логика Виртуального Тренера ---

    private val defaultGreetings = listOf(
        "Хороший день для прогулки, %s! Какую цель поставим сегодня?",
        "Привет, %s! Твои кроссовки уже заждались тренировки.",
        "Движение — это жизнь. %s, может, пройдем пару километров?",
        "Как настрой, %s? Сегодня отличный момент, чтобы стать немного сильнее.",
        "Привет! Твой организм скажет спасибо за активность сегодня.",
        "Погода шепчет: пора выйти на улицу! Что выберешь сегодня, %s?",
        "Каждый шаг приближает тебя к цели. Готов начать, %s?",
        "Привет, чемпион! Твоя прошлая статистика впечатляет. Попробуем превзойти её?",
        "Заряд бодрости на весь день начинается с первого шага. Вперёд, %s!"
    )

    private val recordCongratulations = listOf(
        "Невероятно! Это твой новый рекорд по дистанции! 🎉",
        "Браво! Ты ещё никогда не двигался так быстро. Максимальная скорость побита! ⚡",
        "Фантастика! Сегодня ты сжег рекордное количество калорий! 🔥",
        "Новое достижение! Этот маршрут стал самым длинным в твоей истории. Горжусь тобой!",
        "Ты в отличной форме! Твой средний темп сегодня просто поражает."
    )

    private val motivationTips = listOf(
        "Знаешь ли ты, что 30 минут ходьбы в день улучшают работу сердца?",
        "Попробуй сегодня новый маршрут, это отлично тренирует мозг!",
        "Не забывай пить воду во время активности, это важно для выносливости.",
        "Маленькие шаги ведут к большим результатам. Главное — регулярность!",
        "Хорошая музыка в наушниках добавляет +10% к выносливости. Проверим?"
    )

    fun getTrainingAdvice(name: String): LiveData<String?> {
        val resultAdvice = MediatorLiveData<String?>()

        val lastDateLD = dao.getLastTrackDate().asLiveData()
        val lastSpeedLD = dao.getLastTrackSpeed().asLiveData()
        val avgSpeedLD = dao.getAverageSpeed().asLiveData()
        val maxDistLD = dao.getMaxDistance().asLiveData()
        val maxSpeedLD = dao.getMaxSpeed().asLiveData()
        val maxCaloriesLD = dao.getMaxCalories().asLiveData()

        fun update() {
            val lastDate = lastDateLD.value
            val lastSpeed = lastSpeedLD.value
            val avgSpeed = avgSpeedLD.value
            val maxDist = maxDistLD.value
            val maxSpeed = maxSpeedLD.value
            val maxCalories = maxCaloriesLD.value
            val currentTracks = tracks.value

            if (currentTracks.isNullOrEmpty()) {
                resultAdvice.value = "Привет, $name! Давай запишем твой первый маршрут сегодня?"
                return
            }

            val lastTrack = currentTracks.first()

            // 1. Проверка на СВЕЖИЙ рекорд (если трек был записан менее 2 минут назад)
            val isJustFinished = (System.currentTimeMillis() - lastTrack.date) < 120000L
            if (isJustFinished) {
                if (maxDist != null && lastTrack.distance >= maxDist) {
                    resultAdvice.value = recordCongratulations[0]
                    return
                }
                if (maxSpeed != null && lastTrack.speed >= maxSpeed) {
                    resultAdvice.value = recordCongratulations[1]
                    return
                }
                if (maxCalories != null && lastTrack.calories >= maxCalories) {
                    resultAdvice.value = recordCongratulations[2]
                    return
                }
            }

            // 2. Проверка на долгий перерыв (3 дня)
            if (lastDate != null && (System.currentTimeMillis() - lastDate) > 259200000L) {
                resultAdvice.value = "Мы не тренировались уже несколько дней. Пора размяться, $name!"
                return
            }

            // 3. Проверка на прогресс скорости
            if (lastSpeed != null && avgSpeed != null && lastSpeed > avgSpeed) {
                val diff = (((lastSpeed - avgSpeed) / avgSpeed) * 100).toInt()
                if (diff > 10) {
                    resultAdvice.value = "Отличный прогресс! Твой темп вырос на $diff%. Так держать!"
                    return
                }
            }

            // 4. Рандомный полезный совет или приветствие
            val random = Random(System.currentTimeMillis() / 3600000) // Меняем раз в час
            val choice = random.nextInt(10)
            if (choice < 7) {
                val index = random.nextInt(defaultGreetings.size)
                resultAdvice.value = String.format(defaultGreetings[index], name)
            } else {
                val index = random.nextInt(motivationTips.size)
                resultAdvice.value = motivationTips[index]
            }
        }

        resultAdvice.addSource(lastDateLD) { update() }
        resultAdvice.addSource(lastSpeedLD) { update() }
        resultAdvice.addSource(avgSpeedLD) { update() }
        resultAdvice.addSource(maxDistLD) { update() }
        resultAdvice.addSource(maxSpeedLD) { update() }
        resultAdvice.addSource(maxCaloriesLD) { update() }
        resultAdvice.addSource(tracks) { update() }

        return resultAdvice
    }
}