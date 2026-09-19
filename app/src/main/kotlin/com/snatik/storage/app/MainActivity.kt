package com.snatik.storage.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snatik.storage.app.navigation.AppNavigation
import com.snatik.storage.app.navigation.NAV_TARGET_EXTRA
import com.snatik.storage.app.ui.theme.StorageTheme
import com.snatik.storage.app.ui.theme.ThemePreferences
import com.snatik.storage.app.R
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private var navTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phones stay portrait; tablets (sw600dp+) are free to rotate.
        requestedOrientation = if (resources.getBoolean(R.bool.is_tablet)) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        navTarget = intent?.getStringExtra(NAV_TARGET_EXTRA)
        handleOAuthRedirect(intent)
        setContent {
            val themePrefs = koinInject<ThemePreferences>()
            val settings by themePrefs.state.collectAsStateWithLifecycle()
            StorageTheme(settings) {
                AppNavigation(navTarget = navTarget, onNavConsumed = { navTarget = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NAV_TARGET_EXTRA)?.let { navTarget = it }
        handleOAuthRedirect(intent)
    }

    /** Catches the Dropbox OAuth redirect (snatikstorage://dropbox-auth?code=...) and hands the code off. */
    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "snatikstorage" && uri.host == "dropbox-auth") {
            uri.getQueryParameter("code")?.let { com.snatik.storage.app.feature.cloud.CloudAuthBus.deliver(it) }
        }
    }
}
