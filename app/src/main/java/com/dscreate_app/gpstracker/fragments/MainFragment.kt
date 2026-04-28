package com.dscreate_app.gpstracker.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.database.TrackItem
import com.dscreate_app.gpstracker.database.UserProfile
import com.dscreate_app.gpstracker.databinding.FragmentMainBinding
import com.dscreate_app.gpstracker.location.LocationModel
import com.dscreate_app.gpstracker.location.LocationService
import com.dscreate_app.gpstracker.utils.DialogManager
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.utils.checkPermission
import com.dscreate_app.gpstracker.utils.showToast
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding: FragmentMainBinding
        get() = _binding ?: throw RuntimeException("FragmentMainBinding is null")

    private lateinit var permLauncher: ActivityResultLauncher<Array<String>>
    private var isServiceRunning = false
    private var isPaused = false
    private var timer: Timer? = null
    private var startTime = 0L
    private var pausedTime = 0L
    private var polyLine: Polyline? = null
    private var firstStart: Boolean = true
    private var locationModel: LocationModel? = null
    private lateinit var myLocOverlay: MyLocationNewOverlay
    private var userProfile: UserProfile? = null
    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsOsm()
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        registerPermissions()
        setOnClicks()
        checkServiceState()
        updateTime()
        registerLocReceiver()
        locationUpdates()
        setupSpinner()
        observeUserProfile()
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermission()
        firstStart = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        LocalBroadcastManager.getInstance(requireActivity())
            .unregisterReceiver(receiver)
    }

    private fun setupMap() {
        binding.map.setMultiTouchControls(true)
    }

    private fun setupSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.activity_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spActivityType.adapter = adapter
        }
    }

    private fun observeUserProfile() {
        viewModel.userProfile.observe(viewLifecycleOwner) {
            userProfile = it
        }
    }

    private fun locationUpdates() = with(binding) {
        viewModel.locationUpdates.observe(viewLifecycleOwner) {
            val distance = String.format("%.1f", it.distance / 1000)
            val speed = String.format("%.1f", it.speed)
            val averageSpeed = String.format("%.1f", getAverageSpeed(it.distance))
            val calories = "${it.calories.toInt()} ккал"

            "Дистанция: $distance км".also { tvDistance.text = it }
            "Скорость: $speed м/с".also { tvSpeed.text = it }
            "Средняя скорость: $averageSpeed м/с".also { tvAverageSpeed.text = it }
            tvCalories.text = calories
            locationModel = it
            updatePolyLine(it.geoPointsList)
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        startTime = LocationService.startTime
        timer?.schedule(object : TimerTask() {
            override fun run() {
              activity?.runOnUiThread {
                 viewModel.timeData.value = getCurrentTime()
              }
            }
        }, 1000, 1000)
    }

    private fun getAverageSpeed(distance: Float): Float {
        val totalTime = if (isPaused) pausedTime else System.currentTimeMillis() - startTime
        val timeInSeconds = totalTime / 1000.0f
        return if (timeInSeconds > 0) {
            distance / timeInSeconds
        } else {
            0.0f
        }
    }

    private fun getCurrentTime(): String {
        val totalTime = if (isPaused) pausedTime else System.currentTimeMillis() - startTime
        return getString(R.string.time_tv) + TimeUtils.getTime(totalTime)
    }

    private fun geoPointsToString(list: List<GeoPoint>): String {
        val sBuilder = StringBuilder()
        list.forEach {
            sBuilder.append("${it.latitude},${it.longitude}/")
        }
        return sBuilder.toString()
    }

    private fun updateTime() {
      viewModel.timeData.observe(viewLifecycleOwner) {
            binding.tvTime.text = it
        }
    }

    private fun setOnClicks() = with(binding){
        val listener = onClicks()
        fStartStop.setOnClickListener(listener)
        fCenter.setOnClickListener(listener)
        fPause.setOnClickListener(listener)
    }

    private fun onClicks(): OnClickListener {
        return OnClickListener {
            when(it.id) {
                R.id.fStartStop -> { startStopService() }
                R.id.fCenter -> { centerLocation() }
                R.id.fPause -> { pauseResumeService() }
            }
        }
    }

    private fun centerLocation() {
        binding.map.controller.animateTo(myLocOverlay.myLocation)
        myLocOverlay.enableFollowLocation()
    }

    private fun checkServiceState() {
        isServiceRunning = LocationService.isRunning
        isPaused = LocationService.isPaused
        if (isServiceRunning) {
            binding.fStartStop.setImageResource(R.drawable.ic_stop)
            binding.fPause.visibility = View.VISIBLE
            if (isPaused) {
                binding.fPause.setImageResource(R.drawable.ic_play)
            } else {
                binding.fPause.setImageResource(R.drawable.ic_pause)
                startTimer()
            }
        }
    }

    private fun startStopService() {
        if (!isServiceRunning) {
            startLocService()
        } else {
            activity?.stopService(Intent(activity, LocationService::class.java))
            binding.fStartStop.setImageResource(R.drawable.ic_play)
            binding.fPause.visibility = View.GONE
            timer?.cancel()
            val finalTime = if (isPaused) pausedTime else System.currentTimeMillis() - startTime
            getTrackItem(finalTime)?.let { track ->
                DialogManager.showSaveDialog(
                    requireContext(),
                    track,
                    object : DialogManager.Listener {
                        override fun onClick() {
                            showToast("Маршрут сохранён!")
                            viewModel.insertTrack(track)
                        }
                    })
            }
            isPaused = false
            pausedTime = 0L
        }
        isServiceRunning = !isServiceRunning
    }

    private fun pauseResumeService() {
        val intent = Intent(activity, LocationService::class.java)
        if (!isPaused) {
            intent.action = LocationService.ACTION_PAUSE
            binding.fPause.setImageResource(R.drawable.ic_play)
            timer?.cancel()
            pausedTime = System.currentTimeMillis() - startTime
        } else {
            intent.action = LocationService.ACTION_RESUME
            binding.fPause.setImageResource(R.drawable.ic_pause)
            startTime = System.currentTimeMillis() - pausedTime
            LocationService.startTime = startTime
            startTimer()
        }
        isPaused = !isPaused
        LocationService.isPaused = isPaused
        activity?.startService(intent)
    }

    private fun getTrackItem(time: Long): TrackItem? {
        val activityType = if (binding.spActivityType.selectedItem != null) {
            binding.spActivityType.selectedItem.toString()
        } else {
            return null
        }

        return TrackItem(
            null,
            time,
            TimeUtils.getCurrentTimeInMillis(),
            locationModel?.distance ?: 0.0f,
            getAverageSpeed(locationModel?.distance ?: 0.0f),
            geoPointsToString(locationModel?.geoPointsList ?: listOf()),
            activityType,
            locationModel?.calories ?: 0.0f,
            userProfile?.weight.toString() ?: "70.0"
        )
    }

    private fun startLocService() {
        val weight = userProfile?.weight ?: 70.0f
        val activityType = binding.spActivityType.selectedItem.toString()

        val intent = Intent(activity, LocationService::class.java).apply {
            putExtra(LocationService.WEIGHT_KEY, weight)
            putExtra(LocationService.ACTIVITY_TYPE_KEY, activityType)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(intent)
        } else {
            activity?.startService(intent)
        }
        binding.fStartStop.setImageResource(R.drawable.ic_stop)
        binding.fPause.visibility = View.VISIBLE
        binding.fPause.setImageResource(R.drawable.ic_pause)
        LocationService.startTime = System.currentTimeMillis()
        startTimer()
    }

    private fun settingsOsm() {
        Configuration.getInstance().load(
            requireActivity(),
            activity?.getSharedPreferences(SHARED_PREF_TABLE_NAME, Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    }

    private fun initOSM() = with(binding) {
        polyLine = Polyline()
        polyLine?.outlinePaint?.color = Color.parseColor(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(SHARED_PREF_COLOR_KEY, SHARED_PREF_DEF_VALUE)
        )
        map.controller.setZoom(20.0)
        val mLocProvider = GpsMyLocationProvider(activity)
        myLocOverlay = MyLocationNewOverlay(mLocProvider, map)
        myLocOverlay.enableMyLocation()
        myLocOverlay.enableFollowLocation()
        myLocOverlay.runOnFirstFix {
            map.overlays.clear()
            map.overlays.add(polyLine)
            map.overlays.add(myLocOverlay)
        }
    }

    private fun registerPermissions() {
        permLauncher = registerForActivityResult(ActivityResultContracts
            .RequestMultiplePermissions()) {
            if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                initOSM()
                checkLocationEnabled()
            } else {
                showToast(getString(R.string.toast_need_perm))
            }
        }
    }

    private fun checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermissionAfter10()
        } else {
            checkPermissionBefore10()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkPermissionAfter10() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            initOSM()
            checkLocationEnabled()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }
    }

    private fun checkPermissionBefore10() {
        if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            initOSM()
            checkLocationEnabled()
        } else {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun checkLocationEnabled() {
        val locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isEnabled) {
            DialogManager.showLocEnabledDialog(requireActivity(),
                object : DialogManager.Listener {
                    override fun onClick() {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
            )
        } else {
            showToast("GPS включен")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationService.LOC_MODEL_INTENT) {
                val locModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(LocationService.LOC_MODEL_INTENT, LocationModel::class.java)
                } else {
                    intent.getSerializableExtra(LocationService.LOC_MODEL_INTENT) as LocationModel
                }
                locModel?.let { viewModel.locationUpdates.value = it }
            }
        }
    }

    private fun registerLocReceiver() {
        val locFilter = IntentFilter(LocationService.LOC_MODEL_INTENT)
        LocalBroadcastManager.getInstance(requireActivity())
            .registerReceiver(receiver, locFilter)
    }

    private fun addPoint(list: List<GeoPoint>) {
        if (list.isNotEmpty()) polyLine?.addPoint(list[list.size - 1])
    }

    private fun fillPolyLine(list: List<GeoPoint>) {
        list.forEach {
            polyLine?.addPoint(it)
        }
    }

    private fun updatePolyLine(list: List<GeoPoint>) {
        if (list.size > 1 && firstStart) {
            fillPolyLine(list)
            firstStart = false
        } else {
            addPoint(list)
        }
    }

    companion object {

        private const val SHARED_PREF_TABLE_NAME = "osm_pref"
        private const val SHARED_PREF_COLOR_KEY = "color_key"
        private const val SHARED_PREF_DEF_VALUE = "#03A9F4"

        @JvmStatic
        fun newInstance() = MainFragment()
    }
}