package com.example.mentalworkloadapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.ImageView
import android.widget.TextView

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        // To hidden the logo if the user switch to landscape
        val logo = findViewById<ImageView>(R.id.appLogo)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) logo.visibility = View.GONE
        else logo.visibility = View.VISIBLE

        // If you are not registered, you can register in the signup activity
        val registrationLink = findViewById<TextView>(R.id.areYouNotRegistered)
        registrationLink.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }
    }
}