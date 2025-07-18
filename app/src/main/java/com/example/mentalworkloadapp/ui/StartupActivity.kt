package com.example.mentalworkloadapp.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.mentalworkloadapp.R
import androidx.core.content.edit
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

class StartupActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.startup)

        val start_button = findViewById<Button>(R.id.start_button)
        val username_input = findViewById<EditText>(R.id.username)

        start_button.setOnClickListener {
            // now i have to check if a username is compilated
            val username = username_input.text.toString().trim()
            if(username.isEmpty()){
                // to show to the user a fast text
                Toast.makeText(this, "Remember to insert a username", Toast.LENGTH_SHORT).show()
            } else {
                // now i have to store the information of the username in the memory of the
                // application for the next times
                val sharedPref = getSharedPreferences("SelenePreferences", MODE_PRIVATE)
                sharedPref.edit() {
                    putString("username", username)
                    putBoolean("isFirstRun", false)
                }
                // now we can change the activity and the user can be see a graph
                startActivity(Intent(this, StudyActivity::class.java))
                finish()
            }
        }

        if (!areNotificationsEnabled()) {
            showNotificationPermissionDialog()
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Allows notifications")
            .setMessage("Do you want allows notification?, this is important for your experience!")
            .setPositiveButton("Yes, I'm good guy!") { _, _ ->
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
            .setNegativeButton("No!!", null)
            .show()
    }
}