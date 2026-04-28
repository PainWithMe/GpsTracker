package com.dscreate_app.gpstracker.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import com.dscreate_app.gpstracker.location.GeoPointItem
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.utils.showToast
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ViewTrackFragment : Fragment() {

    private var _binding: FragmentViewTrackBinding? = null
    private val binding: FragmentViewTrackBinding
        get() = _binding ?: throw RuntimeException("FragmentViewTrackBinding is null")

    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }
    private var startPoint: GeoPoint? = null
    private var trackBoundingBox: BoundingBox? = null

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
            if (trackBoundingBox != null) {
                binding.map.zoomToBoundingBox(trackBoundingBox, true, 100)
            } else {
                startPoint?.let { binding.map.controller.animateTo(it) }
            }
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
                    
                    // Вычисляем границы маршрута
                    trackBoundingBox = BoundingBox.fromGeoPoints(polyline.actualPoints)
                    startPoint = polyline.actualPoints[0]
                    
                    // Зумируем карту так, чтобы весь маршрут влез в экран (с небольшим отступом 100 пикселей)
                    map.post {
                        map.zoomToBoundingBox(trackBoundingBox, true, 100)
                    }
                }

                fExport.setOnClickListener {
                    val gpxContent = generateGpx(trackItem)
                    shareGpxFile(gpxContent, trackItem.id)
                }
            }
        }
    }

    private fun generateGpx(track: TrackItem): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTimeStr = isoFormat.format(Date(track.date))

        val header = "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>\n" +
                "<gpx version='1.1' creator='GpsTracker' \n" +
                " xmlns='http://www.topografix.com/GPX/1/1' \n" +
                " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' \n" +
                " xsi:schemaLocation='http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd'>\n" +
                "  <metadata>\n" +
                "    <time>$startTimeStr</time>\n" +
                "  </metadata>\n" +
                "  <trk>\n" +
                "    <name>${track.activityType}</name>\n" +
                "    <trkseg>\n"

        val footer = "    </trkseg>\n  </trk>\n</gpx>"
        val pointsBuilder = StringBuilder()

        try {
            val gson = Gson()
            val type = object : TypeToken<List<GeoPointItem>>() {}.type
            val list: List<GeoPointItem> = gson.fromJson(track.geoPoints, type)
            
            list.forEach { point ->
                pointsBuilder.append("      <trkpt lat='${point.latitude}' lon='${point.longitude}'>\n")
                pointsBuilder.append("        <time>${point.time}</time>\n")
                pointsBuilder.append("      </trkpt>\n")
            }
        } catch (e: Exception) {
            val list = track.geoPoints.split("/").filter { it.isNotEmpty() }
            list.forEach {
                val latLon = it.split(",")
                if (latLon.size == 2) {
                    pointsBuilder.append("      <trkpt lat='${latLon[0]}' lon='${latLon[1]}'></trkpt>\n")
                }
            }
        }

        return header + pointsBuilder.toString() + footer
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
        
        try {
            val gson = Gson()
            val type = object : TypeToken<List<GeoPointItem>>() {}.type
            val list: List<GeoPointItem> = gson.fromJson(geoPoints, type)
            list.forEach {
                polyline.addPoint(GeoPoint(it.latitude, it.longitude))
            }
        } catch (e: Exception) {
            val list = geoPoints.split("/").filter { it.isNotEmpty() }
            list.forEach {
                val points = it.split(",")
                if (points.size == 2) {
                    polyline.addPoint(GeoPoint(points[0].toDouble(), points[1].toDouble()))
                }
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