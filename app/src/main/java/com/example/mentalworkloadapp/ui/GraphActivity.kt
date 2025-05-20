package com.example.mentalworkloadapp.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.mentalworkloadapp.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate

class GraphActivity : ComponentActivity() {
    private lateinit var lineChart: LineChart
    private var currentDayOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.graph)

        val greeting_text = findViewById<TextView>(R.id.greeting)
        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        val username = sharedPref.getString("username", "#user")
        greeting_text.text = "Hi! $username"

        // Trova il grafico nella view
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