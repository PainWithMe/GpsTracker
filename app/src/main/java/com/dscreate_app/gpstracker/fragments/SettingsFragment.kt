package com.dscreate_app.gpstracker.fragments

import android.app.Activity
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Xml
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.database.TrackItem
import com.dscreate_app.gpstracker.database.UserProfile
import com.dscreate_app.gpstracker.location.GeoPointItem
import com.dscreate_app.gpstracker.utils.CaloriesUtils
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.utils.openFragment
import com.dscreate_app.gpstracker.utils.showToast
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import com.google.gson.Gson
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importGpxFile(uri)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference, rootKey)
        setupClickListeners()
        observeUserProfile()
        initFormulaSummary()
    }

    private fun observeUserProfile() {
        viewModel.userProfile.observe(this) { userProfile ->
            userProfile?.let { updateProfilePreferences(it) }
        }
    }

    private fun updateProfilePreferences(userProfile: UserProfile) {
        val namePreference = findPreference<Preference>("name_key")
        val weightPreference = findPreference<Preference>("weight_key")

        namePreference?.summary = userProfile.name
        weightPreference?.summary = userProfile.weight.toString()
    }

    private fun initFormulaSummary() {
        val formulaPref = findPreference<ListPreference>("calorie_formula_key")
        formulaPref?.summary = formulaPref?.entry
        formulaPref?.setOnPreferenceChangeListener { preference, newValue ->
            val index = (preference as ListPreference).findIndexOfValue(newValue as String)
            preference.summary = preference.entries[index]
            true
        }
    }

    private fun setupClickListeners() {
        val namePreference = findPreference<Preference>("name_key")
        val weightPreference = findPreference<Preference>("weight_key")
        val exportCsvPreference = findPreference<Preference>("export_csv_key")
        val downloadMapPreference = findPreference<Preference>("download_map_key")
        val importGpxPreference = findPreference<Preference>("import_gpx_key")

        namePreference?.setOnPreferenceChangeListener { _, newValue ->
            val name = newValue as String
            val currentProfile = viewModel.userProfile.value
            viewModel.insertUserProfile(UserProfile(currentProfile?.id, name, currentProfile?.weight ?: 70.0f))
            true
        }

        weightPreference?.setOnPreferenceChangeListener { _, newValue ->
            val weight = (newValue as String).toFloatOrNull() ?: 70.0f
            val currentProfile = viewModel.userProfile.value
            viewModel.insertUserProfile(UserProfile(currentProfile?.id, currentProfile?.name ?: "", weight))
            true
        }

        exportCsvPreference?.setOnPreferenceClickListener {
            viewModel.tracks.observe(viewLifecycleOwner) { tracks ->
                if (tracks.isNotEmpty()) {
                    val csvContent = generateCsv(tracks)
                    shareCsvFile(csvContent)
                }
            }
            true
        }

        downloadMapPreference?.setOnPreferenceClickListener {
            openFragment(DownloadMapFragment.newInstance())
            true
        }

        importGpxPreference?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importLauncher.launch(intent)
            true
        }
    }

    private fun importGpxFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(inputStream, null)

            val points = mutableListOf<GeoPointItem>()
            var activityName = "Импорт"
            var isInsideTrkpt = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "name" && !isInsideTrkpt) {
                            activityName = parser.nextText()
                        } else if (tagName == "trkpt") {
                            isInsideTrkpt = true
                            val latStr = parser.getAttributeValue(null, "lat").replace(",", ".")
                            val lonStr = parser.getAttributeValue(null, "lon").replace(",", ".")
                            val lat = latStr.toDoubleOrNull() ?: 0.0
                            val lon = lonStr.toDoubleOrNull() ?: 0.0
                            points.add(GeoPointItem(lat, lon, ""))
                        } else if (tagName == "time" && isInsideTrkpt) {
                            val timeStr = parser.nextText()
                            if (points.isNotEmpty()) {
                                points[points.size - 1] = points.last().copy(time = timeStr)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "trkpt") isInsideTrkpt = false
                    }
                }
                eventType = parser.next()
            }
            inputStream?.close()

            if (points.size > 1) {
                val sortedPoints = points.filter { it.time.isNotEmpty() }.sortedBy { it.time }
                saveImportedTrack(if (sortedPoints.isNotEmpty()) sortedPoints else points, activityName)
            } else {
                showToast("Файл содержит недостаточно точек")
            }

        } catch (e: Exception) {
            showToast("Ошибка импорта: ${e.message}")
            Log.e("MyLog", "Import error", e)
        }
    }

    private fun saveImportedTrack(points: List<GeoPointItem>, activityType: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        var totalDistance = 0f
        for (i in 0 until points.size - 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude,
                results
            )
            totalDistance += results[0]
        }

        val firstPointTime = try { 
            if (points.first().time.isNotEmpty()) sdf.parse(points.first().time)?.time ?: System.currentTimeMillis()
            else System.currentTimeMillis()
        } catch (e: Exception) { System.currentTimeMillis() }
        
        val lastPointTime = try { 
            if (points.last().time.isNotEmpty()) sdf.parse(points.last().time)?.time ?: firstPointTime
            else firstPointTime + 1000
        } catch (e: Exception) { firstPointTime + 1000 }
        
        var duration = lastPointTime - firstPointTime
        
        if (totalDistance > 10 && duration < 5000) {
             duration = ((totalDistance / 1.4f) * 1000).toLong()
        }
        
        if (duration <= 0) duration = 1000

        val avgSpeed = totalDistance / (duration / 1000f)
        
        val weight = viewModel.userProfile.value?.weight ?: 70.0f
        // Используем метод calculateMET для импорта
        val calories = CaloriesUtils.calculateMET(duration, avgSpeed, weight, activityType)

        val track = TrackItem(
            null,
            duration,
            firstPointTime,
            totalDistance,
            avgSpeed,
            Gson().toJson(points),
            activityType,
            calories,
            weight.toString()
        )

        viewModel.insertTrack(track)
        showToast("Маршрут успешно импортирован!")
    }

    private fun generateCsv(tracks: List<TrackItem>): String {
        val header = "\"Дата\",\"Активность\",\"Дистанция (км)\",\"Время\",\"Калории\",\"Сред. скорость (м/с)\"\n"
        val rows = tracks.joinToString(separator = "\n") {
            val date = TimeUtils.getFormattedDateTime(it.date)
            val distance = String.format("%.2f", it.distance / 1000)
            val time = TimeUtils.getTime(it.time)
            "\"$date\",\"${it.activityType}\",\"$distance\",\"$time\",\"${it.calories.toInt()}\",\"${String.format("%.1f", it.speed)}\""
        }
        return header + rows
    }

    private fun shareCsvFile(csvContent: String) {
        try {
            val file = File(requireContext().cacheDir, "all_tracks.csv")
            file.writeText(csvContent)

            val contentUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "text/csv"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Экспортировать CSV"))
        } catch (e: Exception) {
            showToast("Не удалось экспортировать файл.")
        }
    }
}
