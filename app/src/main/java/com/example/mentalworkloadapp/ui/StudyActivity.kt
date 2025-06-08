package com.example.mentalworkloadapp.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
import com.example.mentalworkloadapp.service.EegSamplingService
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

        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        // is the phase of the session of study [rest, study, vote]
        val phase = sharedPref.getString("phase", "rest")
        // indicate if the user want to training the model or not
        val voting = sharedPref.getBoolean("voting", true)
        // indicate if the user want the suggest when do a pause
        val pause = sharedPref.getBoolean("pause", false)

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
            sharedPref.edit() {
                putString("phase", "vote")
            }
            goToVote()
        }

        // when the user click one emoji, he wants to vote his mental workload at the end
        // of the session of study
        emojiFresh.setOnClickListener {
            vote(sharedPref, 1)
        }
        emojiNeutral.setOnClickListener {
            vote(sharedPref, 2)
        }
        emojiTired.setOnClickListener {
            vote(sharedPref, 3)
        }
        emojiSleep.setOnClickListener {
            vote(sharedPref, 4)
        }

        // change to graph activity if the user click on the button in the footer
        val navGraph = findViewById<ImageView>(R.id.nav_graph)
        navGraph.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
            finish()
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
        val since = System.currentTimeMillis() - 3 * 60 * 1000
        CoroutineScope(Dispatchers.IO).launch {
            eegDao.updateTirednessSince(newTiredness = vote, since = since)
        }
        sharedPref.edit() {
            putString("phase", "rest")
        }
        goToRest()
        Log.d("Session Study", "I vote $vote")
    }
}