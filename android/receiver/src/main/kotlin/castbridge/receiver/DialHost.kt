package castbridge.receiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import castbridge.core.cast.dial.DialHttpServer
import castbridge.core.cast.dial.DialLauncher
import castbridge.core.cast.dial.DialRouter
import castbridge.core.cast.dial.DialRules
import castbridge.core.cast.dial.SsdpServer
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID

/**
 * Récepteur DIAL (docs/TV-CAST-DIAL.md) : l'application YouTube du téléphone liste la TV et lance YouTube TV installée.
 * Toute la logique de protocole est dans castbridge.core.cast.dial (testée en JVM) ; ici seulement l'Android : verrou
 * multicast, adresses de la TV, Intent de lancement. Démarré par [TvService] quand le réglage `cast.dial` est actif (défaut : oui).
 * Vérifié par compilation seulement : la découverte et l'Intent réel ne se jugent que sur la vraie TV avec l'appli YouTube du téléphone.
 */
class DialHost(private val ctx: Context, private val prefs: TvPrefs) {
    companion object {
        const val PREF = "cast.dial"
        private const val TAG = "CbDial"
        fun enabled(p: TvPrefs) = p.getBool(PREF, true)
    }

    private var lock: WifiManager.MulticastLock? = null
    private var http: DialHttpServer? = null
    private var ssdp: SsdpServer? = null
    @Volatile private var launched = false

    private fun deviceId(): String = prefs.getString("dial_id") ?: UUID.randomUUID().toString().also { prefs.putString("dial_id", it) }

    private class LocalAddr(val ip: String, val prefix: Int, val addr: InetAddress)
    private fun localAddrs(): List<LocalAddr> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.interfaceAddresses.filter { it.address is Inet4Address && !it.address.isLoopbackAddress }
                .map { LocalAddr(it.address.hostAddress ?: "", it.networkPrefixLength.toInt(), it.address) }
        }.filter { it.ip.isNotEmpty() && DialRules.isLanSource(it.addr) }
    }.getOrDefault(emptyList())

    private fun sameNet(a: LocalAddr, other: InetAddress): Boolean {
        val x = a.addr.address; val y = other.address
        if (x.size != y.size || a.prefix <= 0) return false
        val bits = a.prefix
        for (i in 0 until bits / 8) if (x[i] != y[i]) return false
        val rem = bits % 8
        return rem == 0 || ((x[bits / 8].toInt() xor y[bits / 8].toInt()) and (0xFF shl (8 - rem)) and 0xFF) == 0
    }

    private val launcher = object : DialLauncher {
        override fun installed(): Boolean = runCatching { ctx.packageManager.getPackageInfo(DialRules.YOUTUBE_TV_PACKAGE, 0); true }.getOrDefault(false)
        override fun running(): Boolean = launched
        override fun launch(url: String): Boolean = try {
            // Intent VIEW de l'URL fixe youtube.com/tv?<données d'appairage>, paquet explicite : YouTube TV la reçoit comme un lancement DIAL.
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(DialRules.YOUTUBE_TV_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i); launched = true; true
        } catch (e: Exception) { Log.w(TAG, "lancement YouTube TV refusé : ${e.javaClass.simpleName}"); false }
        override fun stop(): Boolean {
            launched = false
            // Limite : YouTube TV n'est pas tuée (pas le droit) ; on revient à l'accueil de CastBridge-TV.
            return runCatching { ctx.startActivity(Intent(ctx, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        }
    }

    @Synchronized fun start() {
        if (http != null) return
        lock = runCatching {
            (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("castbridge-dial").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val id = deviceId()
        lateinit var h: DialHttpServer
        val router = DialRouter(id, android.os.Build.MANUFACTURER ?: "CastBridge", "CastBridge-TV", {
            localAddrs().map { "${it.ip}:${h.localPort}" }.toSet()
        }, launcher) { Log.i(TAG, it) }
        h = DialHttpServer(router) { Log.w(TAG, it) }
        http = h
        if (h.start() <= 0) { stop(); return }
        val s = SsdpServer(DialRules.udn(id), { from ->
            val all = localAddrs()
            val a = (if (from != null) all.firstOrNull { sameNet(it, from) } else null) ?: all.firstOrNull()
            a?.let { "http://${it.ip}:${h.localPort}/dd.xml" }
        }) { Log.w(TAG, it) }
        ssdp = s
        s.start()
        Log.i(TAG, "DIAL actif : ${DialRules.friendlyName(id)} port ${h.localPort}")
    }

    @Synchronized fun stop() {
        runCatching { ssdp?.stop() }; ssdp = null
        runCatching { http?.stop() }; http = null
        runCatching { lock?.release() }; lock = null
        launched = false
    }

    fun status(): String = if (http != null) "DIAL actif (${DialRules.friendlyName(deviceId())})" else "DIAL arrêté"
}
