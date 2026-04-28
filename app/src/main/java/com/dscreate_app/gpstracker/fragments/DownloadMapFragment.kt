package com.dscreate_app.gpstracker.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.dscreate_app.gpstracker.databinding.FragmentDownloadMapBinding
import com.dscreate_app.gpstracker.utils.showToast
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*

class DownloadMapFragment : Fragment() {

    private var _binding: FragmentDownloadMapBinding? = null
    private val binding: FragmentDownloadMapBinding
        get() = _binding ?: throw RuntimeException("FragmentDownloadMapBinding is null")

    private var cacheManager: CacheManager? = null
    private var totalTilesCount = 0
    private lateinit var myLocOverlay: MyLocationNewOverlay

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        binding.btnStartDownload.setOnClickListener { downloadOfflineMap() }
        binding.btnCancelDownload.setOnClickListener { cancelDownload() }
        binding.fCenter.setOnClickListener { centerLocation() }
    }

    private fun setupMap() {
        val osmConfig = Configuration.getInstance()
        osmConfig.load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        
        val customTileSource = XYTileSource(
            "Mapnik", 0, 19, 256, ".png", arrayOf("https://tile.openstreetmap.org/"),
            "© OpenStreetMap contributors",
            TileSourcePolicy(0, 0)
        )
        binding.mapDownload.setTileSource(customTileSource)
        binding.mapDownload.controller.setZoom(15.0)
        binding.mapDownload.setMultiTouchControls(true)

        // Добавляем слой местоположения и центрируемся
        val mLocProvider = GpsMyLocationProvider(activity)
        myLocOverlay = MyLocationNewOverlay(mLocProvider, binding.mapDownload)
        myLocOverlay.enableMyLocation()
        myLocOverlay.enableFollowLocation()
        myLocOverlay.runOnFirstFix {
            activity?.runOnUiThread {
                binding.mapDownload.controller.animateTo(myLocOverlay.myLocation)
            }
        }
        binding.mapDownload.overlays.add(myLocOverlay)
    }

    private fun centerLocation() {
        binding.mapDownload.controller.animateTo(myLocOverlay.myLocation)
        myLocOverlay.enableFollowLocation()
    }

    private fun downloadOfflineMap() {
        if (!isNetworkAvailable()) {
            showToast("Для загрузки нужен интернет")
            return
        }

        try {
            val map = binding.mapDownload
            val boundingBox = map.projection.boundingBox ?: return
            cacheManager = CacheManager(map)
            val minZoom = 10
            val maxZoom = 15

            cacheManager?.downloadAreaAsync(requireContext().applicationContext, boundingBox, minZoom, maxZoom, object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            showToast("Карта загружена")
                            binding.downloadProgressCard.visibility = View.GONE
                        }
                    }
                }

                override fun updateProgress(p0: Int, p1: Int, p2: Int, p3: Int) {
                    activity?.runOnUiThread {
                        if (isAdded && totalTilesCount > 0) {
                            val percent = (p0 * 100) / totalTilesCount
                            binding.downloadProgressCard.visibility = View.VISIBLE
                            binding.downloadProgressBar.progress = percent
                            binding.tvDownloadProgress.text = "Загрузка: $percent%"
                        }
                    }
                }

                override fun downloadStarted() {
                    activity?.runOnUiThread {
                        if (isAdded) binding.downloadProgressCard.visibility = View.VISIBLE
                    }
                }

                override fun setPossibleTilesInArea(total: Int) {
                    totalTilesCount = total
                }

                override fun onTaskFailed(errors: Int) {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            showToast("Загрузка прервана")
                            binding.downloadProgressCard.visibility = View.GONE
                        }
                    }
                }
            })
        } catch (e: Exception) {
            showToast("Ошибка при запуске")
        }
    }

    private fun cancelDownload() {
        showToast("Загрузка остановлена")
        binding.downloadProgressCard.visibility = View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = DownloadMapFragment()
    }
}