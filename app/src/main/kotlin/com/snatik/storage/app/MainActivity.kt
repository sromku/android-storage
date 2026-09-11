package com.snatik.storage.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.snatik.storage.app.navigation.AppNavigation
import com.snatik.storage.app.ui.theme.StorageTheme
import com.snatik.storage.app.R

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phones stay portrait; tablets (sw600dp+) are free to rotate.
        requestedOrientation = if (resources.getBoolean(R.bool.is_tablet)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StorageTheme {
                AppNavigation()
            }
        }
    }
}
