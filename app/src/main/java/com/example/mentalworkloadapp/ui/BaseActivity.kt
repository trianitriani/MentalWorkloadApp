package com.example.mentalworkloadapp.ui

import android.content.Context
import androidx.activity.ComponentActivity
import com.example.mentalworkloadapp.util.LanguageUtil

open class BaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // create the context before the starting of the creation the the activity
        val lang = LanguageUtil.getSavedLanguage(newBase)
        val context = LanguageUtil.setLocale(newBase, lang)
        super.attachBaseContext(context)
    }
}
