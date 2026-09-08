package com.snatik.storage.app.feature.transfer

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.snatik.storage.app.BuildConfig
import com.snatik.storage.core.fs.FileSystem
import com.snatik.storage.core.net.PeerClient
import com.snatik.storage.core.net.PeerDiscovery
import com.snatik.storage.core.net.TransferServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** Wires the server, discovery and client together with this device's identity. */
class TransferHub(
    context: Context,
    fs: FileSystem,
    apiRoutes: io.ktor.server.routing.Route.(com.snatik.storage.core.net.TransferServer) -> Unit,
    private val scope: CoroutineScope,
) {

    val deviceName: String = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

    val inboxDir: File = File("/storage/emulated/0/Download/Storage Received")

    val server = TransferServer(fs, inboxDir, deviceName, BuildConfig.VERSION_NAME, extraRoutes = apiRoutes)
    val discovery = PeerDiscovery(context, deviceName)
    val client = PeerClient(deviceName)

    fun startReceiving() {
        scope.launch {
            server.start()
            if (server.state.value.running) discovery.advertise(server.state.value.port)
        }
    }

    fun stopReceiving() {
        discovery.stopAdvertising()
        scope.launch { server.stop() }
    }
}
