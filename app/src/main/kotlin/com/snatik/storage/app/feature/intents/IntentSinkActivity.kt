package com.snatik.storage.app.feature.intents

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.snatik.storage.app.R
import com.snatik.storage.app.ui.theme.StorageTheme
import com.snatik.storage.core.intents.IntentLog
import com.snatik.storage.core.intents.IntentSpec
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Registered for share and view intents. Shows what arrived, logs it, and can hand it on to the
 * app the user actually wanted, minus this one.
 */
class IntentSinkActivity : ComponentActivity() {

    private val log: IntentLog by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A bottom-anchored dialog window: it covers only the sheet, so the app behind stays visible
        // (with its own status/nav bars) and is dimmed by the window's background dim — no black bars.
        window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(android.view.Gravity.BOTTOM)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.5f)
        // Let the sheet reach under the navigation bar so its surface fills that strip.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setFinishOnTouchOutside(true)
        val received = intent
        val spec = IntentSpec.describe(received)
        val referrer = referrer?.toString()
        val clip = received.clipData?.let { c -> (0 until c.itemCount).joinToString("\n") { i -> c.getItemAt(i).uri?.toString() ?: c.getItemAt(i).text?.toString() ?: "" } }
        lifecycleScope.launch { log.load(); log.add(spec, referrer, clip) }

        setContent {
            StorageTheme {
                SinkSheet(
                    spec = spec,
                    referrer = referrer,
                    clip = clip,
                    onForward = { forward(received) },
                    onCopy = {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("intent", spec.toJson()))
                        Toast.makeText(this, R.string.sink_copied, Toast.LENGTH_SHORT).show()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun forward(original: Intent) {
        val copy = Intent(original).apply {
            component = null
            setPackage(null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(copy, getString(R.string.sink_forward)).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this@IntentSinkActivity, IntentSinkActivity::class.java)))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(chooser)
        finish()
    }
}

@Composable
private fun SinkSheet(spec: IntentSpec, referrer: String?, clip: String?, onForward: () -> Unit, onCopy: () -> Unit, onClose: () -> Unit) {
    // The activity window is a bottom dialog (see onCreate); this is just the sheet surface. The dim
    // and tap-outside-to-dismiss are handled by the window.
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).align(Alignment.CenterHorizontally).size(width = 32.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            Text(stringResource(R.string.sink_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            referrer?.let { Text(stringResource(R.string.sink_from, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                IntentDetails(spec, clip)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.sink_close)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCopy) { Text(stringResource(R.string.sink_copy)) }
                Button(onClick = onForward) { Text(stringResource(R.string.sink_forward)) }
            }
        }
    }
}
