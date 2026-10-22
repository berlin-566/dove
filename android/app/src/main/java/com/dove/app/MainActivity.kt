package com.dove.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dove.app.ui.screens.MainScreen
import com.dove.app.ui.theme.DoveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoveTheme {
                MainScreen(lifecycleOwner = this)
            }
        }
    }
}
