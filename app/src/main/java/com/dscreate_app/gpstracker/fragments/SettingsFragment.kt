package com.dscreate_app.gpstracker.fragments

import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.database.TrackItem
import com.dscreate_app.gpstracker.database.UserProfile
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.utils.showToast
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preference, rootKey)
        setupClickListeners()
        observeUserProfile()
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

    private fun setupClickListeners() {
        val namePreference = findPreference<Preference>("name_key")
        val weightPreference = findPreference<Preference>("weight_key")
        val exportCsvPreference = findPreference<Preference>("export_csv_key")

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
    }

    private fun generateCsv(tracks: List<TrackItem>): String {
        val header = "\"Дата\",\"Активность\",\"Дистанция (км)\",\"Время\",\"Калории\",\"Сред. скорость (км/ч)\"\n"
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