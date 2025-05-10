package com.example.mentalworkloadapp

import android.R.attr.logo
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SignupActivity : ComponentActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup)

        // To hidden the logo if the user switch to landscape
        val logo = findViewById<ImageView>(R.id.appLogo)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) logo.visibility = View.GONE
        else logo.visibility = View.VISIBLE

        // If you are already registered, you can login in the login activity
        val registrationLink = findViewById<TextView>(R.id.areYouAlreadyRegistered)
        registrationLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}