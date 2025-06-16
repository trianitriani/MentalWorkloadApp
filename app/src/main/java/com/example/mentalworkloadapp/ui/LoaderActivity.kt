package com.example.mentalworkloadapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.mentalworkloadapp.service.FineTuningService
import com.example.mentalworkloadapp.ui.StartupActivity

class LoaderActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)
        if (isFirstRun){
            // i have to switch activity into startup activity
            startActivity(Intent(this, StartupActivity::class.java))
        } else {
            // i have to switch activity into graph activity
            startActivity(Intent(this, GraphActivity::class.java))
        }
        finish()
    }
}