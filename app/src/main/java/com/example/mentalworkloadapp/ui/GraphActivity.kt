package com.example.mentalworkloadapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.service.EegSamplingService
import com.example.mentalworkloadapp.util.LanguageUtil
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate

class GraphActivity : BaseActivity() {
    private lateinit var lineChart: LineChart
    private var currentDayOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.graph)
        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        val username = sharedPref.getString("username", "#user")
        findViewById<TextView>(R.id.greeting).text = getString(R.string.greeting, username)
        
        findViewById<ImageView>(R.id.flag_it).setOnClickListener {
            LanguageUtil.setLocale(this, "it")
            recreate()
        }

        findViewById<ImageView>(R.id.flag_en).setOnClickListener {
            LanguageUtil.setLocale(this, "en")
            recreate()
        }

        findViewById<ImageView>(R.id.flag_pl).setOnClickListener {
            LanguageUtil.setLocale(this, "pl")
            recreate()
        }

        lineChart = findViewById(R.id.line_chart)

        // arrows to change the day of the graph
        findViewById<ImageView>(R.id.arrow_left).setOnClickListener {
            currentDayOffset -= 1
            updateChartForDay(currentDayOffset)
        }

        findViewById<ImageView>(R.id.arrow_right).setOnClickListener {
            currentDayOffset += 1
            updateChartForDay(currentDayOffset)
        }

        // initialize the graph within data of today
        updateChartForDay(currentDayOffset)

        // change to study activity if the user click on the button in the footer
        val navStudy = findViewById<ImageView>(R.id.nav_study)
        navStudy.setOnClickListener {
            val intent = Intent(this, StudyActivity::class.java)
            startActivity(intent)
        }
    }

    // update the graph
    private fun updateChartForDay(offset: Int) {
        // put the value of the mental workload on the graph
        val entries = generateFakeData(offset)
        generateGraphTitle(offset)

        // if the offset is 0 (today) hidden the right arrow
        val right_arrow = findViewById<ImageView>(R.id.arrow_right)
        if(offset == 0) right_arrow.visibility = View.INVISIBLE
        else right_arrow.visibility = View.VISIBLE

        // if the offset is -6 (the last day that we remember) hidden the left arrow
        val left_arrow = findViewById<ImageView>(R.id.arrow_left)
        if(offset == -6) left_arrow.visibility = View.INVISIBLE
        else left_arrow.visibility = View.VISIBLE

        // now we setup the legend graph
        val dataSet = LineDataSet(entries, "Mental workload per hours during the day")
        dataSet.color = ColorTemplate.MATERIAL_COLORS[0]
        dataSet.valueTextColor = ColorTemplate.MATERIAL_COLORS[0]
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 2f
        dataSet.setDrawValues(false)
        val lineData = LineData(dataSet)
        lineChart.data = lineData

        // remove the default description
        lineChart.description.isEnabled = false

        // update graph
        lineChart.invalidate()
    }

    // fake generation of the data
    private fun generateFakeData(offset: Int): List<Entry> {
        val values = mutableListOf<Entry>()
        for (i in 0..24) {
            val y = (0..5).random() + offset
            values.add(Entry(i.toFloat(), y.toFloat()))
        }
        return values
    }

    // modify the title of the graph dynamically
    private fun generateGraphTitle(offset: Int) {
        val chartTitle = findViewById<TextView>(R.id.chart_title)
        chartTitle.text = when (offset) {
            0 -> "Daily mental workload [Today]"
            -1 -> "Daily mental workload [Yesterday]"
            else -> "Daily mental workload [$offset]"
        }
    }
}