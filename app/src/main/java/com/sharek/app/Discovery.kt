package com.sharek.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface

object NetUtil {
    /** عناوين IPv4 المحلية (Wi-Fi أو نقطة الاتصال). */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}

/** إعلان الهاتف المستقبل عبر mDNS/NSD. */
class Advertiser(ctx: Context) {
    private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun start(deviceName: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Sharek-$deviceName"
            serviceType = Proto.SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
        }
        listener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd.unregisterService(l) } }
        listener = null
    }
}

/** اكتشاف الهواتف المستقبِلة القريبة. التحديثات تصل على الخيط الرئيسي. */
class Finder(ctx: Context, private val onChange: (List<Peer>) -> Unit) {
    private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private val peers = LinkedHashMap<String, Peer>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("_sharek._tcp")) return
                synchronized(lock) { queue.addLast(info) }
                pump()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(lock) { peers.remove(info.serviceName) }
                publish()
            }
        }
        listener = l
        runCatching { nsd.discoverServices(Proto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd.stopServiceDiscovery(l) } }
        listener = null
    }

    private fun publish() {
        val list = synchronized(lock) { peers.values.toList() }
        main.post { onChange(list) }
    }

    // NSD لا يسمح بأكثر من عملية resolve واحدة في الوقت نفسه، لذلك نمرّرها بالتتابع.
    private fun pump() {
        val next = synchronized(lock) {
            if (resolving || queue.isEmpty()) return
            resolving = true
            queue.removeFirst()
        }
        val rl = object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                synchronized(lock) { resolving = false }
                pump()
            }
            override fun onServiceResolved(i: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val addr = i.host
                if (addr is Inet4Address && addr.hostAddress != null) {
                    synchronized(lock) {
                        peers[i.serviceName] = Peer(
                            i.serviceName.removePrefix("Sharek-"), addr.hostAddress!!, i.port
                        )
                    }
                    publish()
                }
                synchronized(lock) { resolving = false }
                pump()
            }
        }
        @Suppress("DEPRECATION")
        runCatching { nsd.resolveService(next, rl) }.onFailure {
            synchronized(lock) { resolving = false }
        }
    }
}
