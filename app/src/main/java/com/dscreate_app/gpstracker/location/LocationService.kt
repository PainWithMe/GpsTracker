package com.dscreate_app.gpstracker.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
    private lateinit var geoPointsList: ArrayList<GeoPoint>

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        geoPointsList = ArrayList()
        initLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra(WEIGHT_KEY)) weight = it.getFloatExtra(WEIGHT_KEY, 70.0f)
            if (it.hasExtra(ACTIVITY_TYPE_KEY)) activityType = it.getStringExtra(ACTIVITY_TYPE_KEY) ?: "Ходьба"
            
            when (it.action) {
                ACTION_PAUSE -> {
                    isPaused = true
                    lastLocation = null
                }
                ACTION_RESUME -> {
                    isPaused = false
                }
                else -> {
                    startNotification()
                    startLocationUpdates()
                    isRunning = true
                    isPaused = false
                }
            }
        }
        return START_STICKY
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
                distance += lastLocation?.distanceTo(currentLocation) ?: 0.0f
                calories += calculateCalories(timeInMillis, currentLocation.speed)
                geoPointsList.add(GeoPoint(currentLocation.latitude, currentLocation.longitude))

                val locModel = LocationModel(
                    currentLocation.speed,
                    distance,
                    geoPointsList,
                    calories
                )
                sendLocData(locModel)
            }
            lastLocation = currentLocation
        }
    }

    private fun calculateCalories(timeInMillis: Long, speed: Float): Float {
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

    private fun sendLocData(locModel: LocationModel) {
        val intent = Intent(LOC_MODEL_INTENT)
        intent.putExtra(LOC_MODEL_INTENT, locModel)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun startNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nManager = getSystemService(NotificationManager::class.java) as NotificationManager
            nManager.createNotificationChannel(notificationChannel)
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(
            this, CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Gps Tracker running!")
            .setContentText("Активность: $activityType")
            .setContentIntent(pendingIntent).build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initLocation() {
        val updateInterval = (PreferenceManager.getDefaultSharedPreferences(this)
            .getString(SHARED_PREF_KEY, SHARED_PREF_DEF_VALUE) ?: SHARED_PREF_DEF_VALUE).toLong()

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval).apply {
            setMinUpdateIntervalMillis(updateInterval)
        }.build()

        locationProvider = LocationServices.getFusedLocationProviderClient(baseContext)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationProvider.requestLocationUpdates(
            locationRequest, locationCallback, Looper.myLooper()
        )
    }

    companion object {
        private const val CHANNEL_ID = "channel_1"
        private const val CHANNEL_NAME = "Location Service"
        private const val REQUEST_CODE = 10
        private const val NOTIFICATION_ID = 99
        private const val SHARED_PREF_KEY = "update_time_key"
        private const val SHARED_PREF_DEF_VALUE = "3000"
        const val LOC_MODEL_INTENT = "loc_intent"
        const val WEIGHT_KEY = "weight_key"
        const val ACTIVITY_TYPE_KEY = "activity_type_key"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"

        var isRunning = false
        var isPaused = false
        var startTime = 0L
    }
}