package com.tadkeera.qrscanner

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-network link between scanners of the same event (no internet needed).
 *
 * Messages are one JSON object per line. The HOST owns the scan history: organizers send every
 * scan to the host, the host decides (duplicate / exit / forgery), and broadcasts the result, so
 * two devices can never accept the same ticket twice.
 */
const val QRS_SERVICE_TYPE = "_tadkeera-qrs._tcp."

/** Service name shared by host and organizers of one event (only devices of that event match). */
fun serviceNameFor(eventId: String): String = "TQRS-" + eventId.replace("-", "").take(8)

data class DeviceStatus(val id: String, val name: String, val connected: Boolean)

private class Peer(val id: String, val socket: Socket) {
    private val writer = PrintWriter(socket.getOutputStream().bufferedWriter(Charsets.UTF_8), true)
    @Volatile var name: String = "جهاز"
    @Volatile var connected: Boolean = true

    fun send(obj: JSONObject) {
        if (!connected) return
        try {
            synchronized(writer) { writer.println(obj.toString()) }
        } catch (e: Exception) {
            connected = false
        }
    }

    fun close() {
        connected = false
        runCatching { socket.close() }
    }
}

/** Runs on the HOST device: accepts organizers and registers the service on the network. */
class P2PHost(
    context: Context,
    private val scope: CoroutineScope,
    private val eventId: String,
    private val onMessage: (String, JSONObject) -> Unit,
    private val onDevices: (List<DeviceStatus>) -> Unit
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val peers = ConcurrentHashMap<String, Peer>()
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        val ss = ServerSocket(0)
        server = ss
        scope.launch(Dispatchers.IO) {
            while (!ss.isClosed) {
                val socket = try {
                    ss.accept()
                } catch (e: Exception) {
                    break
                }
                val peer = Peer(UUID.randomUUID().toString(), socket)
                peers[peer.id] = peer
                launch(Dispatchers.IO) { readLoop(peer) }
            }
        }
        val info = NsdServiceInfo().apply {
            serviceName = serviceNameFor(eventId)
            serviceType = QRS_SERVICE_TYPE
            port = ss.localPort
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        publish()
    }

    private fun readLoop(peer: Peer) {
        try {
            BufferedReader(InputStreamReader(peer.socket.getInputStream(), Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val obj = try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        continue
                    }
                    if (obj.optString("t") == "HELLO") {
                        peer.name = obj.optString("name", "جهاز")
                        publish()
                    }
                    onMessage(peer.id, obj)
                }
            }
        } catch (e: Exception) {
            // connection dropped
        } finally {
            peer.connected = false
            publish()
        }
    }

    private fun publish() {
        onDevices(peers.values.map { DeviceStatus(it.id, it.name, it.connected) })
    }

    fun sendTo(peerId: String, obj: JSONObject) {
        peers[peerId]?.send(obj)
    }

    fun broadcast(obj: JSONObject) {
        peers.values.forEach { it.send(obj) }
    }

    fun nameOf(peerId: String): String = peers[peerId]?.name ?: "جهاز"

    fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        peers.values.forEach { it.close() }
        peers.clear()
        onDevices(emptyList())
    }
}

/** Runs on each ORGANIZER device: finds the host on the network and keeps the link alive. */
class P2PClient(
    context: Context,
    private val scope: CoroutineScope,
    private val eventId: String,
    private val onMessage: (JSONObject) -> Unit,
    private val onStatus: (Boolean) -> Unit
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var connecting = false
    @Volatile private var running = false
    private var discovery: NsdManager.DiscoveryListener? = null
    private var myName = "مستخدم"

    fun start(name: String) {
        running = true
        myName = name
        discover()
    }

    private fun discover() {
        if (!running || discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName != serviceNameFor(eventId)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        connect(i.host, i.port)
                    }
                })
            }
        }
        discovery = listener
        nsd.discoverServices(QRS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun restartDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        discover()
    }

    private fun connect(host: InetAddress, port: Int) {
        if (socket != null || connecting || !running) return
        connecting = true
        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket(host, port)
                socket = s
                writer = PrintWriter(s.getOutputStream().bufferedWriter(Charsets.UTF_8), true)
                onStatus(true)
                send(JSONObject().put("t", "HELLO").put("name", myName).put("eventId", eventId))
                BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val obj = try {
                            JSONObject(line)
                        } catch (e: Exception) {
                            continue
                        }
                        onMessage(obj)
                    }
                }
            } catch (e: Exception) {
                // host not reachable right now
            } finally {
                runCatching { socket?.close() }
                socket = null
                writer = null
                connecting = false
                onStatus(false)
                if (running) {
                    delay(2000)
                    restartDiscovery()
                }
            }
        }
    }

    fun send(obj: JSONObject) {
        val w = writer ?: return
        try {
            synchronized(w) { w.println(obj.toString()) }
        } catch (e: Exception) {
            // link will be re-established by discovery
        }
    }

    fun stop() {
        running = false
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        runCatching { socket?.close() }
        socket = null
        onStatus(false)
    }
}
