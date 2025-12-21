package com.dscreate_app.gpstracker.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.database.TrackItem
import com.dscreate_app.gpstracker.databinding.FragmentViewTrackBinding
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.utils.showToast
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

class ViewTrackFragment : Fragment() {

    private var _binding: FragmentViewTrackBinding? = null
    private val binding: FragmentViewTrackBinding
        get() = _binding ?: throw RuntimeException("FragmentViewTrackBinding is null")

    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }
    private var startPoint: GeoPoint? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsOsm()
        _binding = FragmentViewTrackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        getTrack()
        binding.fCenter.setOnClickListener {
            startPoint?.let { binding.map.controller.animateTo(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun settingsOsm() {
        Configuration.getInstance().load(
            requireActivity(),
            activity?.getSharedPreferences(SHARED_PREF_TABLE_NAME, Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    }

    private fun setupMap() {
        binding.map.setMultiTouchControls(true)
    }

    private fun getTrack() = with(binding) {
        viewModel.currentTrack.observe(viewLifecycleOwner) { trackItem ->
            trackItem?.let { 
                val date = TimeUtils.getFormattedDateTime(it.date)
                val speed = "${String.format("%.1f", it.speed)} ${requireContext().getString(R.string.meter_in_sec)}"
                val distance = "${String.format("%.1f", it.distance / 1000)} ${requireContext().getString(R.string.distance_in_kilometer)}"
                val calories = "Калории: ${it.calories.toInt()}"

                tvData.text = date
                tvTime.text = TimeUtils.getTime(it.time)
                tvAverageSpeed.text = speed
                tvDistance.text = distance
                tvCalories.text = calories
                val polyline = getPolyline(it.geoPoints)
                if (polyline.actualPoints.isNotEmpty()) {
                    map.overlays.add(polyline)
                    setMarkers(polyline.actualPoints)
                    goToStartPosition(polyline.actualPoints[0])
                    startPoint = polyline.actualPoints[0]
                }

                fExport.setOnClickListener {
                    val gpxContent = generateGpx(trackItem)
                    shareGpxFile(gpxContent, trackItem.id)
                }
            }
        }
    }

    private fun generateGpx(track: TrackItem): String {
        val header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?><gpx version='1.1' creator='GpsTracker'><trk><name>${track.activityType}</name><trkseg>"
        val footer = "</trkseg></trk></gpx>"

        val points = track.geoPoints.split("/").filter { it.isNotEmpty() }.joinToString("") {
            val latLon = it.split(",")
            "<trkpt lat='${latLon[0]}' lon='${latLon[1]}'></trkpt>"
        }

        return header + points + footer
    }

    private fun shareGpxFile(gpxContent: String, trackId: Int?) {
        try {
            val file = File(requireContext().cacheDir, "track_${trackId ?: "export"}.gpx")
            file.writeText(gpxContent)

            val contentUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "application/gpx+xml"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Экспортировать GPX"))
        } catch (e: Exception) {
            showToast("Не удалось экспортировать файл.")
        }
    }

    private fun goToStartPosition(startPosition: GeoPoint) {
        binding.map.controller.zoomTo(15.0)
        binding.map.controller.animateTo(startPosition)
    }

    private fun setMarkers(list: List<GeoPoint>) = with(binding) {
        val startMarker = Marker(map)
        val finishMarker = Marker(map)
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        finishMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.icon = getDrawable(requireContext(), R.drawable.ic_start_position)
        finishMarker.icon = getDrawable(requireContext(), R.drawable.ic_finish_position)
        startMarker.position = list[0]
        finishMarker.position = list[list.size - 1]
        map.overlays.add(startMarker)
        map.overlays.add(finishMarker)
    }

    private fun getPolyline(geoPoints: String): Polyline {
        val polyline = Polyline()
        polyline.outlinePaint.color = Color.parseColor(
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(SHARED_PREF_COLOR_KEY, SHARED_PREF_DEF_VALUE)
        )
        val list = geoPoints.split("/")
        list.forEach {
            if (it.isEmpty()) return@forEach
            val points = it.split(",")
            if (points.size == 2) {
                 polyline.addPoint(GeoPoint(points[0].toDouble(), points[1].toDouble()))
            }
        }
        return polyline
    }

    companion object {
        private const val SHARED_PREF_TABLE_NAME = "osm_pref"
        private const val SHARED_PREF_COLOR_KEY = "color_key"
        private const val SHARED_PREF_DEF_VALUE = "#03A9F4"

        @JvmStatic
        fun newInstance() = ViewTrackFragment()
    }
}