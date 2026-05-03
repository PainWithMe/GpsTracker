package com.dscreate_app.gpstracker.fragments

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.dscreate_app.gpstracker.R
import com.dscreate_app.gpstracker.database.MainApp
import com.dscreate_app.gpstracker.databinding.FragmentStatisticsBinding
import com.dscreate_app.gpstracker.utils.TimeUtils
import com.dscreate_app.gpstracker.viewModels.MainViewModel
import com.dscreate_app.gpstracker.viewModels.ViewModelFactory
import com.github.mikephil.charting.charts.BarChart
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
            binding.barChartCalories.visibility = View.VISIBLE
            binding.barChartCaloriesTitle.visibility = View.VISIBLE
            observeActivityCountChart()
            observeCaloriesByActivityChart()
        } else {
            binding.toggleDateFilter.visibility = View.VISIBLE
            binding.barChart.visibility = View.VISIBLE
            binding.barChartCalories.visibility = View.VISIBLE
            binding.barChartCaloriesTitle.visibility = View.VISIBLE
            binding.barChart.clear()
            binding.barChartCalories.clear()
            binding.toggleDateFilter.check(R.id.buttonWeek)
            observeBarChartForActivity(activityType, "week")
        }
    }

    private fun observeTextStats(activityType: String?) {
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
                binding.tvAverageSpeed.text = "Средняя скорость: ${String.format("%.1f", it ?: 0.0f)} м/с"
            }
            viewModel.getMaxDistance().observe(viewLifecycleOwner) {
                binding.tvMaxDistance.text = "Макс. дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getMaxTime().observe(viewLifecycleOwner) {
                binding.tvMaxTime.text = "Макс. время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getMaxSpeed().observe(viewLifecycleOwner) {
                binding.tvMaxSpeed.text = "Макс. скорость: ${String.format("%.1f", it ?: 0.0f)} м/с"
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
                binding.tvAverageSpeed.text = "Средняя скорость: ${String.format("%.1f", it ?: 0.0f)} м/с"
            }
            viewModel.getMaxDistanceByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxDistance.text = "Макс. дистанция: ${String.format("%.1f", (it ?: 0.0f) / 1000)} км"
            }
            viewModel.getMaxTimeByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxTime.text = "Макс. время: ${TimeUtils.getTime(it ?: 0)}"
            }
            viewModel.getMaxSpeedByType(activityType).observe(viewLifecycleOwner) {
                binding.tvMaxSpeed.text = "Макс. скорость: ${String.format("%.1f", it ?: 0.0f)} м/с"
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
            setupBarChart(binding.barChart, BarData(dataSet), labels)
        }
    }

    private fun observeCaloriesByActivityChart() {
        binding.barChartCaloriesTitle.text = "Калории по активностям"
        viewModel.getCaloriesByActivity().observe(viewLifecycleOwner) { caloriesData ->
            if (caloriesData.isNullOrEmpty()) {
                binding.barChartCalories.clear()
                binding.barChartCalories.invalidate()
                return@observe
            }
            val entries = caloriesData.mapIndexed { index, activityCalories ->
                BarEntry(index.toFloat(), activityCalories.totalCalories)
            }
            val labels = caloriesData.map { it.activityType.replace(" ", "\n") }
            val dataSet = BarDataSet(entries, "").apply {
                colors = ColorTemplate.MATERIAL_COLORS.toList()
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value == 0f) "" else value.toInt().toString()
                    }
                }
                valueTextSize = 12f
            }
            setupBarChart(binding.barChartCalories, BarData(dataSet), labels)
        }
    }

    private fun observeBarChartForActivity(activityType: String, period: String) {
        val title = "Дистанция за ${if (period == "week") "неделю" else if (period == "month") "месяц" else "год"}"
        binding.barChartTitle.text = title
        val caloriesTitle = "Калории за ${if (period == "week") "неделю" else if (period == "month") "месяц" else "год"}"
        binding.barChartCaloriesTitle.text = caloriesTitle

        val (startDate, endDate) = when(period) {
            "week" -> getWeekPeriod()
            "month" -> getMonthPeriod()
            "year" -> getYearPeriod()
            else -> return
        }

        viewModel.getTracksForPeriod(activityType, startDate, endDate).observe(viewLifecycleOwner) { tracks ->
            val (distEntries, distLabels) = processDataForPeriod(tracks, period, false)
            setupBarChart(binding.barChart, BarData(BarDataSet(distEntries, "")), distLabels)

            val (calEntries, calLabels) = processDataForPeriod(tracks, period, true)
            setupBarChart(binding.barChartCalories, BarData(BarDataSet(calEntries, "")), calLabels)
        }
    }

    private fun processDataForPeriod(tracks: List<com.dscreate_app.gpstracker.database.TrackItem>, period: String, isCalories: Boolean): Pair<List<BarEntry>, List<String>> {
        val calendar = Calendar.getInstance()
        val (startDate, _) = when(period) {
            "week" -> getWeekPeriod()
            "month" -> getMonthPeriod()
            "year" -> getYearPeriod()
            else -> return Pair(emptyList(), emptyList())
        }

        return when (period) {
            "week" -> {
                val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                val entries = (0 until 7).map { i ->
                    calendar.timeInMillis = startDate
                    calendar.add(Calendar.DAY_OF_YEAR, i)
                    val dayTracks = tracks.filter {
                        val trackCal = Calendar.getInstance().apply { timeInMillis = it.date }
                        trackCal.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                        trackCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
                    }
                    val value = if(isCalories) dayTracks.sumOf { it.calories.toDouble() }.toFloat() else dayTracks.sumOf { it.distance.toDouble() }.toFloat() / 1000
                    BarEntry(i.toFloat(), value)
                }
                val labels = (0 until 7).map {
                    calendar.timeInMillis = startDate
                    calendar.add(Calendar.DAY_OF_YEAR, it)
                    dateFormat.format(calendar.time)
                }
                Pair(entries, labels)
            }
            "month" -> {
                val labels = listOf("Нед 1", "Нед 2", "Нед 3", "Нед 4")
                val entries = labels.mapIndexed { index, _ ->
                    calendar.timeInMillis = startDate
                    calendar.add(Calendar.WEEK_OF_YEAR, index)
                    val weekStart = calendar.timeInMillis
                    calendar.add(Calendar.DAY_OF_YEAR, 6)
                    val weekEnd = calendar.timeInMillis

                    val weekTracks = tracks.filter { it.date in weekStart..weekEnd }
                    val value = if(isCalories) weekTracks.sumOf { it.calories.toDouble() }.toFloat() else weekTracks.sumOf { it.distance.toDouble() }.toFloat() / 1000
                    BarEntry(index.toFloat(), value)
                }.toMutableList()
                Pair(entries, labels)
            }
            "year" -> {
                val labels = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
                val entries = (0..11).map { monthIndex ->
                    val monthTracks = tracks.filter {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                        cal.get(Calendar.MONTH) == monthIndex
                    }
                    val value = if(isCalories) monthTracks.sumOf { it.calories.toDouble() }.toFloat() else monthTracks.sumOf { it.distance.toDouble() }.toFloat() / 1000
                    BarEntry(monthIndex.toFloat(), value)
                }.toMutableList()
                Pair(entries, labels)
            }
            else -> Pair(emptyList(), emptyList())
        }
    }

    private fun setupBarChart(chart: BarChart, data: BarData, labels: List<String>) {
        val dataSet = data.getDataSetByIndex(0) as BarDataSet
        dataSet.apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else String.format("%.0f", value)
                }
            }
            valueTextSize = 12f
        }

        chart.data = data
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.animateY(1000)

        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            labelRotationAngle = -45f
            labelCount = labels.size
        }
        chart.axisLeft.apply {
            setDrawGridLines(false)
            axisMinimum = 0f
        }
        chart.axisRight.isEnabled = false
        chart.setExtraBottomOffset(60f)
        chart.invalidate()
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