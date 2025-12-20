package com.dscreate_app.gpstracker.fragments

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.database.UserProfile
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory

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
    }
}