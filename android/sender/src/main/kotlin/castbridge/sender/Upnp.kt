package castbridge.sender

import android.content.Context
import android.net.wifi.WifiManager
import castbridge.core.upnp.Soap
import castbridge.core.upnp.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*

data class Renderer(val name: String, val avtControlUrl: String, val rcControlUrl: String?)

object Upnp {
    fun localIp(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .sortedByDescending { it.name.startsWith("wlan") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress

    /** SSDP M-SEARCH for MediaRenderers; returns them after [timeoutMs]. */
    suspend fun discover(ctx: Context, timeoutMs: Int = 4000): List<Renderer> = withContext(Dispatchers.IO) {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("castbridge").apply { setReferenceCounted(false); acquire() }
        val locations = linkedSetOf<String>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 500
                val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                    "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n").toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { s.send(DatagramPacket(msg, msg.size, group, 1900)) }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        Regex("(?im)^location:\\s*(\\S+)").find(String(p.data, 0, p.length))
                            ?.let { locations += it.groupValues[1] }
                    } catch (_: SocketTimeoutException) {}
                }
            }
        } finally { lock.release() }
        locations.mapNotNull { runCatching { describe(it) }.getOrNull() }
    }

    private fun describe(location: String): Renderer? {
        val xml = http(location, "GET", null, null)
        val base = URL(location)
        fun abs(p: String) = URL(base, p).toString()
        fun control(type: String) = Regex("<service>.*?</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.value }.firstOrNull { "service:$type:" in it }
            ?.let { Xml.tag(it, "controlURL") }?.let(::abs)
        val avt = control("AVTransport") ?: return null
        return Renderer(Xml.tag(xml, "friendlyName") ?: base.host, avt, control("RenderingControl"))
    }

    fun http(url: String, method: String, body: String?, soapAction: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 8000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            soapAction?.let { c.setRequestProperty("SOAPACTION", it) }
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val stream = if (c.responseCode < 400) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        if (c.responseCode >= 400) throw java.io.IOException("HTTP ${c.responseCode}: ${text.take(200)}")
        return text
    }

    fun avt(r: Renderer, action: String, vararg args: Pair<String, String>): String = http(
        r.avtControlUrl, "POST",
        Soap.envelope(Soap.AVT, action, listOf("InstanceID" to "0") + args), Soap.soapAction(Soap.AVT, action))

    suspend fun play(r: Renderer, url: String, didl: String) = withContext(Dispatchers.IO) {
        runCatching { avt(r, "Stop") }
        avt(r, "SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to didl)
        avt(r, "Play", "Speed" to "1")
    }
    suspend fun pause(r: Renderer) = withContext(Dispatchers.IO) { avt(r, "Pause") }
    suspend fun resume(r: Renderer) = withContext(Dispatchers.IO) { avt(r, "Play", "Speed" to "1") }
    suspend fun stop(r: Renderer) = withContext(Dispatchers.IO) { avt(r, "Stop") }
    suspend fun seek(r: Renderer, sec: Long) = withContext(Dispatchers.IO) {
        avt(r, "Seek", "Unit" to "REL_TIME", "Target" to Soap.hms(sec))
    }
    /** PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING, NO_MEDIA_PRESENT... */
    suspend fun transportState(r: Renderer): String? = withContext(Dispatchers.IO) {
        Xml.tag(avt(r, "GetTransportInfo"), "CurrentTransportState")
    }
    /** Returns (position, duration) in seconds. */
    suspend fun position(r: Renderer): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val x = avt(r, "GetPositionInfo")
        Soap.parseHms(Xml.tag(x, "RelTime")) to Soap.parseHms(Xml.tag(x, "TrackDuration"))
    }
}
