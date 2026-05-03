package com.dscreate_app.gpstracker.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.dscreate_app.gpstracker.MainActivity
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.utils.CaloriesUtils
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import java.util.*

class LocationService : Service() {

    private lateinit var locationProvider: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var lastLocation: Location? = null
    private var distance = 0.0f
    private var calories = 0.0f
    private var weight = 70.0f
    private var activityType = "Ходьба"
    private var formulaType = "met"
    private lateinit var geoPointsList: ArrayList<GeoPoint>

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        geoPointsList = ArrayList()
        initLocation()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.hasExtra(WEIGHT_KEY)) weight = intent.getFloatExtra(WEIGHT_KEY, 70.0f)
        if (intent.hasExtra(ACTIVITY_TYPE_KEY)) activityType = intent.getStringExtra(ACTIVITY_TYPE_KEY) ?: "Ходьба"
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        formulaType = prefs.getString("calorie_formula_key", "met") ?: "met"

        when (intent.action) {
            ACTION_PAUSE -> {
                isPaused = true
                lastLocation = null
                updateNotification("Запись приостановлена")
                sendStateUpdate()
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification("Запись возобновлена")
                sendStateUpdate()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            else -> {
                startForeground(NOTIFICATION_ID, getNotification("Начинаем движение..."))
                startLocationUpdates()
                isRunning = true
                isPaused = false
            }
        }
        return START_STICKY
    }

    private fun sendStateUpdate() {
        // Отправляем пустой LocModel чтобы обновить состояние UI во фрагменте
        val intent = Intent(LOC_MODEL_INTENT)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isPaused = false
        locationProvider.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            if (isPaused) return

            val currentLocation = locationResult.lastLocation
            if (lastLocation != null && currentLocation != null) {
                val timeInMillis = currentLocation.time - lastLocation!!.time
                val stepDistance = lastLocation?.distanceTo(currentLocation) ?: 0.0f
                distance += stepDistance
                
                calories += when (formulaType) {
                    "distance" -> CaloriesUtils.calculateByDistance(stepDistance, weight, activityType)
                    "acsm" -> {
                        val altitudeDiff = currentLocation.altitude - lastLocation!!.altitude
                        CaloriesUtils.calculateAdaptive(timeInMillis, currentLocation.speed, weight, activityType, altitudeDiff)
                    }
                    else -> CaloriesUtils.calculateMET(timeInMillis, currentLocation.speed, weight, activityType)
                }

                geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))

                val locModel = LocationModel(
                    currentLocation.speed,
                    distance,
                    geoPointsList,
                    calories
                )
                sendLocData(locModel)
                
                val distanceKm = String.format("%.2f", distance / 1000)
                updateNotification("Дистанция: $distanceKm км | $activityType")
            }
            lastLocation = currentLocation
        }
    }

    private fun sendLocData(locModel: LocationModel) {
        val intent = Intent(LOC_MODEL_INTENT)
        intent.putExtra(LOC_MODEL_INTENT, locModel)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val trackerChannel = NotificationChannel(
                CHANNEL_ID, "GPS Трекер (Активность)", NotificationManager.IMPORTANCE_LOW
            )
            val coachChannel = NotificationChannel(
                COACH_CHANNEL_ID, "Виртуальный тренер (Советы)", NotificationManager.IMPORTANCE_DEFAULT
            )
            val nManager = getSystemService(NotificationManager::class.java) as NotificationManager
            nManager.createNotificationChannel(trackerChannel)
            nManager.createNotificationChannel(coachChannel)
        }
    }

    private fun getNotification(content: String): android.app.Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, LocationService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseAction = if (isPaused) {
            val resumeIntent = Intent(this, LocationService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 2, resumeIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_play, "Продолжить", resumePendingIntent)
        } else {
            val pauseIntent = Intent(this, LocationService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 3, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(android.R.drawable.ic_media_pause, "Пауза", pausePendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Gps Tracker в работе")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(pauseAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val nManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nManager.notify(NOTIFICATION_ID, getNotification(content))
    }

    private fun initLocation() {
        val updateInterval = (PreferenceManager.getDefaultSharedPreferences(this)
            .getString(SHARED_PREF_KEY, SHARED_PREF_DEF_VALUE) ?: SHARED_PREF_DEF_VALUE).toLong()
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
            .setMinUpdateIntervalMillis(updateInterval).build()
        locationProvider = LocationServices.getFusedLocationProviderClient(baseContext)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationProvider.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    companion object {
        private const val CHANNEL_ID = "channel_tracker"
        const val COACH_CHANNEL_ID = "channel_coach"
        private const val REQUEST_CODE = 10
        private const val NOTIFICATION_ID = 99
        private const val SHARED_PREF_KEY = "update_time_key"
        private const val SHARED_PREF_DEF_VALUE = "3000"
        const val LOC_MODEL_INTENT = "loc_intent"
        const val WEIGHT_KEY = "weight_key"
        const val ACTIVITY_TYPE_KEY = "activity_type_key"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_STOP = "action_stop"

        var isRunning = false
        var isPaused = false
        var startTime = 0L
    }
}
