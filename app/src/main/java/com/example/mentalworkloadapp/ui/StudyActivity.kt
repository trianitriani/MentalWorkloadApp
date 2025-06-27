package com.example.mentalworkloadapp.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.data.local.db.entitiy.SampleEeg
import com.example.mentalworkloadapp.service.EegSamplingService
import com.example.mentalworkloadapp.service.FineTuningService
import com.example.mentalworkloadapp.util.LanguageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudyActivity : BaseActivity() {
    private lateinit var buttonStart: Button
    private lateinit var buttonEnd: Button
    private lateinit var emojiContainer: LinearLayout
    private lateinit var emojiFresh: ImageView
    private lateinit var emojiNeutral: ImageView
    private lateinit var emojiTired: ImageView
    private lateinit var emojiSleep: ImageView
    private lateinit var checkboxNotification: CheckBox
    private lateinit var checkboxFeedback: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.study)

        buttonStart = findViewById<Button>(R.id.btn_start)
        buttonEnd = findViewById<Button>(R.id.btn_end)
        emojiContainer = findViewById<LinearLayout>(R.id.emoji_container)
        emojiFresh = findViewById<ImageView>(R.id.emoji_fresh)
        emojiNeutral = findViewById<ImageView>(R.id.emoji_neutral)
        emojiTired = findViewById<ImageView>(R.id.emoji_tired)
        emojiSleep = findViewById<ImageView>(R.id.emoji_sleep)
        checkboxNotification = findViewById<CheckBox>(R.id.checkbox_notification)
        checkboxFeedback = findViewById<CheckBox>(R.id.checkbox_feedback)

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

        // click for lunching the training in background
        findViewById<ImageButton>(R.id.btn_weight).setOnClickListener {
            // if any service is running, is possible to run the fineTuningService
            if (!FineTuningService.isRunning && !EegSamplingService.isRunning) {
                val intent = Intent(this, FineTuningService::class.java)
                ContextCompat.startForegroundService(this, intent)
                Log.d("Session Study", "Il service per training dovrebbe runnare")
            } else {
                Log.d("Session Study", "Il service per training non sta runnando")
            }
        }

        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        // is the phase of the session of study [rest, study, vote]
        val phase = sharedPref.getString("phase", "rest")
        // indicate if the user want to training the model or not
        val voting = sharedPref.getBoolean("voting", true)
        checkboxFeedback.isChecked = voting
        // indicate if the user want the suggest when do a pause
        val pause = sharedPref.getBoolean("pause", false)
        checkboxNotification.isChecked = pause
        // username of the user
        val username = sharedPref.getString("username", "#user")
        findViewById<TextView>(R.id.greeting).text = getString(R.string.greeting, username)

        // check the visibility and the enable of the component of the page
        if(phase.equals("rest")) goToRest()
        else if(phase.equals("study"))  goToStudy()
        else if(phase.equals("vote")) goToVote()

        // if the user studying runs the service if is not running
        if(phase.equals("study")){
            if (!EegSamplingService.isRunning) {
                val intent = Intent(this, EegSamplingService::class.java)
                ContextCompat.startForegroundService(this, intent)
                Log.d("Session Study", "Il service dovrebbe runnare")
            } else {
                Log.d("Session Study", "Il service non sta runnando")
            }
        }

        // when the user click this button, he wants to stating a study session
        buttonStart.setOnClickListener {
            if(!sharedPref.getString("phase", "rest").equals("rest")) return@setOnClickListener
            Log.d("Session Study", "I'm starting now to study")
            // now we have to starting the service
            if (!EegSamplingService.isRunning) {
                val intent = Intent(this, EegSamplingService::class.java)
                ContextCompat.startForegroundService(this, intent)
                Log.d("Session Study", "Il service dovrebbe runnare")
            } else {
                Log.d("Session Study", "Il service non sta runnando")
            }
            sharedPref.edit() {
                putString("phase", "study")
            }
            goToStudy()
        }

        // when the user click this button, he wants to stopping his study session
        buttonEnd.setOnClickListener {
            if(!sharedPref.getString("phase", "rest").equals("study")) return@setOnClickListener
            Log.d("Session Study", "I'm stopping now the session of study")
            // now we have to stopping the service
            if (EegSamplingService.isRunning) {
                val intent = Intent(this, EegSamplingService::class.java)
                stopService(intent)
            }
            // if the user want to vote his session and training the model
            if(sharedPref.getBoolean("voting", true)){
                sharedPref.edit() {
                    putString("phase", "vote")
                }
                goToVote()
            } else {
                sharedPref.edit() {
                    putString("phase", "rest")
                }
                goToRest()
            }

        }

        // when the user click one emoji, he wants to vote his mental workload at the end
        // of the session of study
        emojiFresh.setOnClickListener {
            vote(sharedPref, 0)
        }
        emojiNeutral.setOnClickListener {
            vote(sharedPref, 1)
        }
        emojiTired.setOnClickListener {
            vote(sharedPref, 2)
        }
        emojiSleep.setOnClickListener {
            vote(sharedPref, 3)
        }

        checkboxNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit() { putBoolean("pause", isChecked) }
        }

        checkboxFeedback.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit() { putBoolean("voting", isChecked) }
        }

    }

    private fun goToRest(){
        // enable the first button, nothing else
        buttonStart.isEnabled = true
        buttonStart.alpha = 1.0f
        buttonEnd.isEnabled = false
        buttonEnd.alpha = 0.4f
        emojiContainer.visibility = View.GONE
    }

    private fun goToStudy(){
        // enable the second button, nothing else
        buttonStart.isEnabled = false
        buttonStart.alpha = 0.4f
        buttonEnd.isEnabled = true
        buttonEnd.alpha = 1.0f
        emojiContainer.visibility = View.GONE
    }

    private fun goToVote() {
        // enable the emoji, nothing else
        buttonStart.isEnabled = false
        buttonStart.alpha = 0.4f
        buttonEnd.isEnabled = false
        buttonEnd.alpha = 0.4f
        emojiContainer.visibility = View.VISIBLE
    }

    private fun vote(sharedPref: SharedPreferences, vote: Int) {
        val phase = sharedPref.getString("phase", "rest")
        if(phase != "vote") return
        // now we have to vote that emoji
        val eegDao = DatabaseProvider.getSampleEegDao(context = this)
        CoroutineScope(Dispatchers.IO).launch {
            // assign a vote to the last samples (last 180 * 100 samples)
            val samples = eegDao.getLastNSamplesOfLastSession(180 * 100);
            val updatedSamples = samples.map { sample ->
                SampleEeg (
                    timestamp = sample.timestamp,
                    ch_c1 = sample.ch_c1,
                    ch_c2 = sample.ch_c2,
                    ch_c3 = sample.ch_c3,
                    ch_c4 = sample.ch_c4,
                    ch_c5 = sample.ch_c5,
                    ch_c6 = sample.ch_c6,
                    ch_r_ear = sample.ch_r_ear,
                    ch_l_ear = sample.ch_l_ear,
                    tiredness = vote
                )
            }
            eegDao.updateSamplesEeg(updatedSamples)
            // remove all the samples without vote
            eegDao.deleteSamplesWithoutTiredness()
        }
        sharedPref.edit() {
            putString("phase", "rest")
        }
        goToRest()
        Log.d("Session Study", "I vote $vote")
    }
}