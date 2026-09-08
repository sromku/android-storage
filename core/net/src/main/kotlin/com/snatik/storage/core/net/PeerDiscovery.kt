package com.snatik.storage.core.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

data class Peer(val name: String, val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}

/** Announces this device's server and finds other devices running the app on the same network. */
class PeerDiscovery(context: Context, private val deviceName: String) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var ownServiceName: String? = null

    fun advertise(port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = deviceName.take(60)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { ownServiceName = i.serviceName }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopAdvertising() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    fun startDiscovery() {
        stopDiscovery()
        _peers.value = emptyList()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) {}
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (i.serviceName == ownServiceName) return
                resolve(i)
            }
            override fun onServiceLost(i: NsdServiceInfo) {
                _peers.update { list -> list.filterNot { it.name == i.serviceName } }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(code: Int) {}
                override fun onServiceUpdated(i: NsdServiceInfo) {
                    val host = i.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: return
                    add(Peer(i.serviceName, host, i.port))
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
            runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                override fun onServiceResolved(i: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = i.host?.hostAddress ?: return
                    add(Peer(i.serviceName, host, i.port))
                }
            })
        }
    }

    private fun add(peer: Peer) {
        _peers.update { list -> (list.filterNot { it.name == peer.name } + peer).sortedBy { it.name } }
    }

    companion object {
        const val SERVICE_TYPE = "_storageapp._tcp."
    }
}
