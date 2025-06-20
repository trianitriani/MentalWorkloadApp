package com.example.mentalworkloadapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
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
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.mentalworkloadapp.R
import com.example.mentalworkloadapp.data.local.db.DatabaseProvider
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted.")
            } else {
                Log.w("Permission", "Notification permission denied.")
                Toast.makeText(this, "Notification permission is needed for alerts.", Toast.LENGTH_LONG).show()
            }
        }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.study)

        askNotificationPermission()

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
            if (!FineTuningService.isRunning) {
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

        checkboxNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit() { putBoolean("pause", isChecked) }
        }

        checkboxFeedback.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit() { putBoolean("voting", isChecked) }
        }

        /*
        // change to graph activity if the user click on the button in the footer
        val navGraph = findViewById<ImageView>(R.id.nav_graph)
        navGraph.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
            finish()
        }*/
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
            // assign a vote to the last samples (3 minutes from now)
            eegDao.updateTirednessSince(newTiredness = vote, since = since)
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