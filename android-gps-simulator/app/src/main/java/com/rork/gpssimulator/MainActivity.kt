package com.rork.gpssimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rork.gpssimulator.ui.navigation.AppNavigation
import com.rork.gpssimulator.ui.privacy.AnalyticsConsent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enforce the stored decision before any Compose screen analytics can run.
        AnalyticsConsent.initialize(this)
        enableEdgeToEdge()
        setContent {
            // Theme, language and RTL direction are resolved inside AppNavigation
            // from the persisted user settings.
            AppNavigation()
        }
    }
}
