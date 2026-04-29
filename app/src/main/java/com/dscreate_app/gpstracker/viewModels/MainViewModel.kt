package com.dscreate_app.gpstracker.viewModels

import androidx.lifecycle.*
import com.dscreate_app.gpstracker.database.*
import com.dscreate_app.gpstracker.location.LocationModel
import kotlinx.coroutines.launch
import java.util.*
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
        "Как настрой, %s? Сегодня отличный момент, чтобы стать сильнее.",
        "Привет! Твой организм скажет спасибо за активность сегодня.",
        "Погода шепчет: пора выйти на улицу! Что выберешь сегодня, %s?",
        "Привет, чемпион! Твоя статистика впечатляет. Попробуем превзойти её?",
        "Заряд бодрости на весь день начинается с первого шага. Вперёд, %s!"
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
        val totalDistLD = dao.getTotalDistance().asLiveData()
        val totalCountLD = dao.getTotalTracksCount().asLiveData()
        val morningCountLD = dao.getMorningTracksCount().asLiveData()
        val eveningCountLD = dao.getEveningTracksCount().asLiveData()
        val lastTenDatesLD = dao.getLastTenTrackDates().asLiveData()
        val maxDistLD = dao.getMaxDistance().asLiveData()
        val maxSpeedLD = dao.getMaxSpeed().asLiveData()

        fun update() {
            val lastDate = lastDateLD.value
            val totalDist = totalDistLD.value ?: 0f
            val totalCount = totalCountLD.value ?: 0
            val morningCount = morningCountLD.value ?: 0
            val eveningCount = eveningCountLD.value ?: 0
            val lastTenDates = lastTenDatesLD.value ?: emptyList()
            val maxDist = maxDistLD.value ?: 0f
            val maxSpeed = maxSpeedLD.value ?: 0f
            val currentTracks = tracks.value

            if (currentTracks.isNullOrEmpty()) {
                resultAdvice.value = "Привет, $name! Давай запишем твой первый маршрут сегодня?"
                return
            }

            val lastTrack = currentTracks.first()
            val isJustFinished = (System.currentTimeMillis() - lastTrack.date) < 120000L

            // 1. Приоритет: Мгновенные поздравления (Рекорды и Юбилеи)
            if (isJustFinished) {
                if (lastTrack.distance >= maxDist && maxDist > 0) {
                    resultAdvice.value = "Невероятно! Это твой новый личный рекорд по дистанции! 🎉"
                    return
                }
                if (lastTrack.speed >= maxSpeed && maxSpeed > 0) {
                    resultAdvice.value = "Браво! Ты ещё никогда не двигался так быстро. Рекорд скорости! ⚡"
                    return
                }
                if (totalDist > 42195f && totalDist < 43000f) {
                    resultAdvice.value = "Потрясающе! Твоя общая дистанция превысила 42 км. Ты пробежал МАРАФОН! 🏆"
                    return
                }
                if (totalCount % 10 == 0) {
                    resultAdvice.value = "Юбилей! Это твоя ${totalCount}-я тренировка. Так держать! 🎊"
                    return
                }
            }

            // 2. Дисциплина (перерыв более 3 дней) - показываем всегда, если условие верно
            if (lastDate != null && (System.currentTimeMillis() - lastDate) > 259200000L) {
                resultAdvice.value = "Мы не тренировались уже несколько дней. Пора размяться, $name!"
                return
            }

            // 3. Аналитика (Серии или Время суток) - выпадает СЛУЧАЙНО, чтобы не надоедать
            val randomChoice = Random.nextInt(10)
            if (randomChoice < 3) { // 30% шанс на глубокую аналитику
                // Серии
                if (lastTenDates.size >= 3) {
                    val cal = Calendar.getInstance()
                    val days = lastTenDates.map { cal.apply { timeInMillis = it }.get(Calendar.DAY_OF_YEAR) }.distinct().take(3)
                    if (days.size == 3 && days[0] - days[2] == 2) {
                        resultAdvice.value = "Ого, ты на волне! 3-й день тренировок подряд. Не останавливайся! 🔥"
                        return
                    }
                }
                // Время суток
                if (totalCount >= 5) {
                    if (morningCount > eveningCount + 3) {
                        resultAdvice.value = "Ты настоящий жаворонок! Утренние прогулки отлично бодрят. ☀️"
                        return
                    } else if (eveningCount > morningCount + 3) {
                        resultAdvice.value = "Предпочитаешь вечерние прогулки? Это лучший способ снять стресс! 🌙"
                        return
                    }
                }
            }

            // 4. Фон: Рандомные приветствия и советы
            val isTip = Random.nextBoolean()
            resultAdvice.value = if (isTip) {
                motivationTips[Random.nextInt(motivationTips.size)]
            } else {
                String.format(defaultGreetings[Random.nextInt(defaultGreetings.size)], name)
            }
        }

        resultAdvice.addSource(lastDateLD) { update() }
        resultAdvice.addSource(totalDistLD) { update() }
        resultAdvice.addSource(totalCountLD) { update() }
        resultAdvice.addSource(morningCountLD) { update() }
        resultAdvice.addSource(eveningCountLD) { update() }
        resultAdvice.addSource(lastTenDatesLD) { update() }
        resultAdvice.addSource(maxDistLD) { update() }
        resultAdvice.addSource(maxSpeedLD) { update() }
        resultAdvice.addSource(tracks) { update() }

        return resultAdvice
    }
}