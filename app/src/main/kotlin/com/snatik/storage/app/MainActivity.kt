package com.snatik.storage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.snatik.storage.app.explorer.ExplorerScreen
import com.snatik.storage.app.ui.StorageTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StorageTheme {
                ExplorerScreen()
            }
        }
    }
}
