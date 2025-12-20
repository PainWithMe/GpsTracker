package com.dscreate_app.gpstracker.fragments

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.databinding.FragmentStatisticsBinding
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding: FragmentStatisticsBinding
        get() = _binding ?: throw RuntimeException("FragmentStatisticsBinding is null")

    private val viewModel: MainViewModel by activityViewModels {
        ViewModelFactory((requireContext().applicationContext as MainApp).database)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpinner()
        setupToggle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.activity_types_statistics,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spActivityType.adapter = adapter

        binding.spActivityType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = if (position == 0) null else adapter.getItem(position).toString()
                updateUI(selectedType)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupToggle() {
        binding.toggleDateFilter.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val selectedType = binding.spActivityType.selectedItem.toString()
                when (checkedId) {
                    R.id.buttonWeek -> observeBarChartForActivity(selectedType, "week")
                    R.id.buttonMonth -> observeBarChartForActivity(selectedType, "month")
                    R.id.buttonYear -> observeBarChartForActivity(selectedType, "year")
                }
            }
        }
    }

    private fun updateUI(activityType: String?) {
        observeTextStats(activityType)
        if (activityType == null) {
            binding.toggleDateFilter.visibility = View.GONE
            binding.barChart.visibility = View.VISIBLE
            observeActivityCountChart()
        } else {
            binding.toggleDateFilter.visibility = View.VISIBLE
            binding.barChart.visibility = View.VISIBLE
            binding.barChart.clear()
            binding.toggleDateFilter.check(R.id.buttonWeek)
            observeBarChartForActivity(activityType, "week")
        }
    }

    private fun observeTextStats(activityType: String?) {
        // This function remains the same as before
        if (activityType == null) {
            viewModel.getTotalDistance().observe(viewLifecycleOwner) {
                binding.tvTotalDistance.text = "Общая дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getTotalTime().observe(viewLifecycleOwner) {
                binding.tvTotalTime.text = "Общее время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getTotalCalories().observe(viewLifecycleOwner) {
                binding.tvTotalCalories.text = "Всего сожжено: ${(it ?: 0.0f).toInt()} ккал"
            }
            viewModel.getAverageSpeed().observe(viewLifecycleOwner) {
                binding.tvAverageSpeed.text = "Средняя скорость: ${String.format("%.1f", it ?: 0.0f)} км/ч"
            }
            viewModel.getMaxDistance().observe(viewLifecycleOwner) {
                binding.tvMaxDistance.text = "Макс. дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getMaxTime().observe(viewLifecycleOwner) {
                binding.tvMaxTime.text = "Макс. время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getMaxSpeed().observe(viewLifecycleOwner) {
                binding.tvMaxSpeed.text = "Макс. скорость: ${String.format("%.1f", it ?: 0.0f)} км/ч"
            }
            viewModel.getMaxCalories().observe(viewLifecycleOwner) {
                binding.tvMaxCalories.text = "Макс. калорий: ${(it ?: 0.0f).toInt()} ккал"
            }
        } else {
            viewModel.getTotalDistanceByType(activityType).observe(viewLifecycleOwner) {
                binding.tvTotalDistance.text = "Общая дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getTotalTimeByType(activityType).observe(viewLifecycleOwner) {
                binding.tvTotalTime.text = "Общее время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getTotalCaloriesByType(activityType).observe(viewLifecycleOwner) {
                binding.tvTotalCalories.text = "Всего сожжено: ${(it ?: 0.0f).toInt()} ккал"
            }
            viewModel.getAverageSpeedByType(activityType).observe(viewLifecycleOwner) {
                binding.tvAverageSpeed.text = "Средняя скорость: ${String.format("%.1f", it ?: 0.0f)} км/ч"
            }
            viewModel.getMaxDistanceByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxDistance.text = "Макс. дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getMaxTimeByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxTime.text = "Макс. время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getMaxSpeedByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxSpeed.text = "Макс. скорость: ${String.format("%.1f", it ?: 0.0f)} км/ч"
            }
            viewModel.getMaxCaloriesByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxCalories.text = "Макс. калорий: ${(it ?: 0.0f).toInt()} ккал"
            }
        }
    }

    private fun observeActivityCountChart() {
        binding.barChartTitle.text = "Частота Активностей"
        viewModel.getActivityCount().observe(viewLifecycleOwner) { activityData ->
            if (activityData.isNullOrEmpty()) {
                binding.barChart.clear()
                binding.barChart.invalidate()
                return@observe
            }
            val entries = activityData.mapIndexed { index, activityCount ->
                BarEntry(index.toFloat(), activityCount.count.toFloat())
            }
            val labels = activityData.map { it.activityType.replace(" ", "\n") }

            val dataSet = BarDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value == 0f) "" else value.toInt().toString()
                    }
                }
                valueTextSize = 12f
            }
            val data = BarData(dataSet)
            binding.barChart.apply {
                this.data = data
                description.isEnabled = false
                legend.isEnabled = false
                animateY(1000)

                xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    labelRotationAngle = 0f
                    labelCount = labels.size
                }
                axisLeft.apply {
                    setDrawGridLines(false)
                    axisMinimum = 0f
                }
                axisRight.isEnabled = false
                setExtraBottomOffset(40f)
                invalidate()
            }
        }
    }

    private fun observeBarChartForActivity(activityType: String, period: String) {
        val title = "Активность за ${if (period == "week") "неделю" else if (period == "month") "месяц" else "год"}"
        binding.barChartTitle.text = title

        when (period) {
            "week" -> {
                val (startDate, endDate) = getWeekPeriod()
                viewModel.getTracksForPeriod(activityType, startDate, endDate).observe(viewLifecycleOwner) { tracks ->
                    val calendar = Calendar.getInstance()
                    val dbDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateList = (0 until 7).map {
                        calendar.timeInMillis = startDate
                        calendar.add(Calendar.DAY_OF_YEAR, it)
                        dbDateFormat.format(calendar.time)
                    }
                    val entries = dateList.mapIndexed { index, date ->
                        val dayTracks = tracks.filter { dbDateFormat.format(it.date) == date }
                        val distance = dayTracks.sumOf { it.distance.toDouble() }.toFloat()
                        BarEntry(index.toFloat(), distance / 1000)
                    }
                    val labels = dateList.map { it.substring(5).replace("-", "/") }
                    setupBarChart(entries, labels)
                }
            }
            "month" -> {
                val (startDate, endDate) = getMonthPeriod()
                viewModel.getTracksForPeriod(activityType, startDate, endDate).observe(viewLifecycleOwner) { tracks ->
                    val labels = listOf("Неделя 4", "Неделя 3", "Неделя 2", "Неделя 1")
                    val calendar = Calendar.getInstance()
                    val entries = labels.mapIndexed { index, _ ->
                        calendar.timeInMillis = endDate
                        calendar.add(Calendar.WEEK_OF_YEAR, -index)
                        val weekEnd = calendar.timeInMillis
                        calendar.add(Calendar.DAY_OF_YEAR, -6)
                        val weekStart = calendar.timeInMillis
                        
                        val weekTracks = tracks.filter { it.date in weekStart..weekEnd }
                        val distance = weekTracks.sumOf { it.distance.toDouble() }.toFloat()
                        BarEntry(index.toFloat(), distance / 1000)
                    }.reversed().toMutableList()
                    setupBarChart(entries, labels.reversed())
                }
            }
            "year" -> {
                val (startDate, endDate) = getYearPeriod()
                viewModel.getTracksForPeriod(activityType, startDate, endDate).observe(viewLifecycleOwner) { tracks ->
                    val labels = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
                    val entries = (0..11).map { monthIndex ->
                        val monthTracks = tracks.filter {
                            val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                            cal.get(Calendar.MONTH) == monthIndex
                        }
                        val distance = monthTracks.sumOf { it.distance.toDouble() }.toFloat()
                        BarEntry(monthIndex.toFloat(), distance / 1000)
                    }.toMutableList()
                    setupBarChart(entries, labels)
                }
            }
        }
    }

    private fun setupBarChart(entries: List<BarEntry>, labels: List<String>) {
        val dataSet = BarDataSet(entries, "").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else String.format("%.1f", value)
                }
            }
            valueTextSize = 12f
        }
        val data = BarData(dataSet)
        binding.barChart.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = false
            animateY(1000)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -45f
                labelCount = labels.size
            }
            axisLeft.apply {
                setDrawGridLines(false)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            setExtraBottomOffset(50f)
            invalidate()
        }
    }

    private fun getWeekPeriod(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = calendar.timeInMillis
        return startDate to endDate
    }

    private fun getMonthPeriod(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -1)
        val startDate = calendar.timeInMillis
        return startDate to endDate
    }

    private fun getYearPeriod(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis
        calendar.add(Calendar.YEAR, -1)
        val startDate = calendar.timeInMillis
        return startDate to endDate
    }
}