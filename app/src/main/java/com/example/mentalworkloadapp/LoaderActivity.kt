package com.example.mentalworkloadapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class LoaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("SelenePreferences", Context.MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)
        if (isFirstRun){
            // i have to switch activity into startup activity
            startActivity(Intent(this, StartupActivity::class.java))
        } else {
            // i have to switch activity into graph activity
            startActivity(Intent(this, GraphActivity::class.java))
        }
        finish();
    }
}