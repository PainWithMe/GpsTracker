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

    // --- Статистика ---
    fun getTotalDistance() = dao.getTotalDistance().asLiveData()
    fun getTotalTime() = dao.getTotalTime().asLiveData()
    fun getTotalCalories() = dao.getTotalCalories().asLiveData()
    fun getAverageSpeed() = dao.getAverageSpeed().asLiveData()

    fun getMaxDistance() = dao.getMaxDistance().asLiveData()
    fun getMaxTime() = dao.getMaxTime().asLiveData()
    fun getMaxSpeed() = dao.getMaxSpeed().asLiveData()
    fun getMaxCalories() = dao.getMaxCalories().asLiveData()

    fun getTotalDistanceByType(type: String) = dao.getTotalDistanceByType(type).asLiveData()
    fun getTotalTimeByType(type: String) = dao.getTotalTimeByType(type).asLiveData()
    fun getTotalCaloriesByType(type: String) = dao.getTotalCaloriesByType(type).asLiveData()
    fun getAverageSpeedByType(type: String) = dao.getAverageSpeedByType(type).asLiveData()

    fun getMaxDistanceByType(type: String) = dao.getMaxDistanceByType(type).asLiveData()
    fun getMaxTimeByType(type: String) = dao.getMaxTimeByType(type).asLiveData()
    fun getMaxSpeedByType(type: String) = dao.getMaxSpeedByType(type).asLiveData()
    fun getMaxCaloriesByType(type: String) = dao.getMaxCaloriesByType(type).asLiveData()

    fun getActivityCount(): LiveData<List<ActivityCount>> = dao.getActivityCount().asLiveData()

    fun getTracksForPeriod(activityType: String, startDate: Long, endDate: Long): LiveData<List<TrackItem>> {
        return dao.getTracksForPeriod(activityType, startDate, endDate).asLiveData()
    }

    fun getCaloriesByActivity(): LiveData<List<ActivityCalories>> = dao.getCaloriesByActivity().asLiveData()

    // --- Логика Виртуального Тренера ---

    private val greetings = listOf(
        "Хороший день для прогулки, %s! Какую цель поставим сегодня?",
        "Привет, %s! Твои кроссовки уже заждались тренировки.",
        "Движение — это жизнь. %s, может, пройдем пару километров?",
        "Как настрой, %s? Сегодня отличный момент, чтобы стать сильнее.",
        "Заряд бодрости на весь день начинается с первого шага. Вперёд, %s!",
        "Твоё тело — твой храм. Давай позаботимся о нём сегодня, %s.",
        "Привет! Твой организм скажет спасибо за активность сегодня."
    )

    private val healthTips = listOf(
        "Знаешь ли ты, что 30 минут ходьбы в день улучшают работу сердца?",
        "Попробуй сегодня новый маршрут, это отлично тренирует мозг!",
        "Не забывай пить воду во время активности, это важно для выносливости.",
        "Маленькие шаги ведут к большим результатам. Главное — регулярность!",
        "Хорошая музыка в наушниках добавляет +10% к выносливости. Проверим?",
        "Свежий воздух улучшает качество сна. Прогуляемся?",
        "Регулярные прогулки снижают уровень стресса на 30%."
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
        val maxTimeLD = dao.getMaxTime().asLiveData()
        val totalCalLD = dao.getTotalCalories().asLiveData()

        fun update() {
            val lastDate = lastDateLD.value
            val totalDist = totalDistLD.value ?: 0f
            val totalCount = totalCountLD.value ?: 0
            val morningCount = morningCountLD.value ?: 0
            val eveningCount = eveningCountLD.value ?: 0
            val lastTenDates = lastTenDatesLD.value ?: emptyList()
            val maxDist = maxDistLD.value ?: 0f
            val maxSpeed = maxSpeedLD.value ?: 0f
            val maxTime = maxTimeLD.value ?: 0L
            val totalCal = totalCalLD.value ?: 0f
            val currentTracks = tracks.value

            if (currentTracks.isNullOrEmpty()) {
                resultAdvice.value = "Привет, $name! Давай запишем твой первый маршрут сегодня?"
                return
            }

            val lastTrack = currentTracks.first()
            val isJustFinished = (System.currentTimeMillis() - lastTrack.date) < 120000L

            val pool = mutableListOf<String>()

            // 1. РЕКОРДЫ (Приоритет)
            if (isJustFinished) {
                if (lastTrack.distance >= maxDist && maxDist > 0) pool.add("Невероятно! Это твой новый личный рекорд по дистанции! 🎉")
                if (lastTrack.speed >= maxSpeed && maxSpeed > 0) pool.add("Браво! Ты ещё никогда не двигался так быстро. Рекорд скорости! ⚡")
                if (lastTrack.time >= maxTime && maxTime > 0) pool.add("Сегодня была самая долгая тренировка в твоей истории! 💪")
                if (pool.isNotEmpty()) {
                    resultAdvice.value = pool.random()
                    return
                }
            }

            // 2. ЮБИЛЕИ И ГЛОБАЛЬНЫЕ ЦЕЛИ
            if (totalDist > 42195f && totalDist < 45000f) pool.add("Потрясающе! Твоя общая дистанция превысила 42 км. Ты пробежал МАРАФОН! 🏆")
            if (totalCount > 0 && totalCount % 10 == 0) pool.add("Юбилей! Это твоя ${totalCount}-я тренировка. Твоя целеустремленность поражает! 🎊")
            if (totalCal > 5000f && totalCal < 5500f) pool.add("Ты сжег уже более 5000 ккал за всё время! Это мощно! 🔥")

            // 3. АНАЛИТИКА (Серии и Время)
            if (lastTenDates.size >= 3) {
                val cal = Calendar.getInstance()
                val days = lastTenDates.map { cal.apply { timeInMillis = it }.get(Calendar.DAY_OF_YEAR) }.distinct().take(3)
                if (days.size == 3 && days[0] - days[2] == 2) pool.add("Ого, ты на волне! 3-й день тренировок подряд. Не дай серии прерваться! 🔥")
            }
            if (totalCount >= 5) {
                if (morningCount > eveningCount + 3) pool.add("Ты настоящий жаворонок! Утренние прогулки отлично бодрят. ☀️")
                if (eveningCount > morningCount + 3) pool.add("Предпочитаешь вечерние прогулки? Это лучший способ снять стресс! 🌙")
            }

            // 4. ДИНАМИКА НЕДЕЛИ (Сравнение)
            val now = System.currentTimeMillis()
            val week = 7 * 24 * 60 * 60 * 1000L
            val distThisWeek = currentTracks.filter { it.date in (now - week)..now }.sumOf { it.distance.toDouble() }
            val distLastWeek = currentTracks.filter { it.date in (now - 2 * week)..(now - week) }.sumOf { it.distance.toDouble() }
            if (distLastWeek > 0 && distThisWeek > distLastWeek * 1.1) {
                val p = (((distThisWeek - distLastWeek) / distLastWeek) * 100).toInt()
                pool.add("Мощная неделя! Ты прошел на $p%% больше, чем на прошлой. Выносливость растет! 💪")
            }

            // 5. ДИСЦИПЛИНА (Если давно не заходил)
            if (lastDate != null && (now - lastDate) > 259200000L) {
                resultAdvice.value = "Мы не тренировались уже несколько дней. Пора размяться, $name!"
                return
            }

            // ФОНОВЫЕ ПРИВЕТСТВИЯ
            pool.add(String.format(greetings.random(), name))
            pool.add(healthTips.random())

            val finalMsg = pool.random()
            if (finalMsg != resultAdvice.value) resultAdvice.value = finalMsg
        }

        resultAdvice.addSource(lastDateLD) { update() }
        resultAdvice.addSource(totalDistLD) { update() }
        resultAdvice.addSource(totalCountLD) { update() }
        resultAdvice.addSource(morningCountLD) { update() }
        resultAdvice.addSource(eveningCountLD) { update() }
        resultAdvice.addSource(lastTenDatesLD) { update() }
        resultAdvice.addSource(maxDistLD) { update() }
        resultAdvice.addSource(maxSpeedLD) { update() }
        resultAdvice.addSource(maxTimeLD) { update() }
        resultAdvice.addSource(totalCalLD) { update() }
        resultAdvice.addSource(tracks) { update() }

        return resultAdvice
    }
}