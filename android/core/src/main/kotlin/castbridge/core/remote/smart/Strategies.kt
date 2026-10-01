package castbridge.core.remote.smart

import castbridge.core.quiz.Json
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteKey.*
import java.io.IOException

// ------------------------------------------------------------------------------------------------------------------------
// CVTE / Amlogic « white label » TVs (docs/REMOTE-VENDOR-CVTE.md): WebSocket + protobuf, Android key codes in decimal.
// ------------------------------------------------------------------------------------------------------------------------

/** The JSON information frame the TV pushes right after the WebSocket handshake. */
data class CvteInfo(val name: String?, val width: Int?, val height: Int?, val websocketPort: Int?, val httpPort: Int?, val features: Set<String>) {
    companion object {
        /** `{"status":500,"data":{…},"msg":"successful"}`: the 500 is what the reference TV sends with `successful`, so it is NOT an error. */
        fun parse(json: String): CvteInfo? = runCatching {
            val o = Json.obj(json)
            @Suppress("UNCHECKED_CAST") val d = o["data"] as? Map<String, Any?> ?: return null
            @Suppress("UNCHECKED_CAST") val cfg = d["config"] as? Map<String, Any?> ?: emptyMap()
            fun int(k: String) = when (val v = d[k]) { is Long -> v.toInt(); is Double -> v.toInt(); is String -> v.toIntOrNull(); else -> null }
            CvteInfo(d["name"] as? String, int("width"), int("height"), int("websocketPort") ?: int("port"), int("httpPort"), cfg.filterValues { it == true }.keys)
        }.getOrNull()
    }
}

/** The protobuf message of the CVTE remote: 1 = type, 2 = action (string), 3 = pointers {1: x float, 2: y float}. */
object CvteMessage {
    const val TYPE_KEY = 1
    const val TYPE_MOUSE_CLICK = 4
    const val DEFAULT_PORT = 8125

    /** Type 1 = key event; the action is the Android key code in decimal. `08 01 12 <len> "<code>"`. */
    fun key(androidKeyCode: Int): ByteArray = Pb.Writer().int(1, TYPE_KEY).string(2, androidKeyCode.toString()).toByteArray()

    fun decode(b: ByteArray): Triple<Int, String?, List<Pair<Float, Float>>> {
        var type = 0; var action: String? = null; val pts = ArrayList<Pair<Float, Float>>()
        for (f in Pb.parse(b)) when (f.number) {
            1 -> type = f.long.toInt(); 2 -> action = f.string
            3 -> { val p = Pb.parse(f.bytes ?: ByteArray(0)); pts += (p.firstOrNull { it.number == 1 }?.float ?: 0f) to (p.firstOrNull { it.number == 2 }?.float ?: 0f) }
        }
        return Triple(type, action, pts)
    }

    /** Where to connect, from a `_share._tcp` DNS-SD record (the address falls back on the TXT `device_ip`). */
    fun endpoint(r: MdnsRecord, resolvedHost: String?): Pair<String, Int>? {
        val host = resolvedHost ?: r.txt["device_ip"] ?: return null
        val port = r.txt["websocket_port"]?.toIntOrNull() ?: DEFAULT_PORT
        return host to port
    }
}

class CvteStrategy(tv: TvTarget, private val port: Int = CvteMessage.DEFAULT_PORT, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.CVTE
    override val label = "TV « marque blanche » (CVTE / Amlogic)"
    override val status = StrategyStatus.STABLE
    // Android key codes go straight to the TV's Android; only the volume keys are verified on hardware so far.
    override val capabilities = Capabilities(RemoteKey.values().toSet())
    override val limits = "Touches Android envoyées telles quelles ; seules les touches de volume sont vérifiées sur du vrai matériel. Pas de texte ni de pavé tactile pour l'instant."
    override val warnings = listOf("Cette TV accepte les commandes de n'importe quel appareil du même Wi-Fi (aucune authentification) : un risque réseau à connaître.")

    @Volatile private var ws: WsClient? = null
    @Volatile var info: CvteInfo? = null; private set
    @Volatile private var reader: Thread? = null

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.CVTE || fp.vendor == Vendor.UNKNOWN && fp.mayUse(port) || StrategyIds.CVTE in fp.candidates
    override fun probe() = ProbeResult(tcpOpen(port), "port $port")

    @Synchronized override fun connect() {
        closeLink()
        state = StrategyState(StrategyState.Kind.CONNECTING)
        val c = try { WsClient.connect(tv.host, port, "/", timeoutMs = tv.connectTimeoutMs) } catch (e: IOException) { failed("connexion refusée : ${e.message}") }
        // The TV pushes its information frame at once; a silent TV is still usable.
        runCatching { c.read(1200)?.text?.let { info = CvteInfo.parse(it) } }
        ws = c
        reader = Thread({ try { while (!c.closed) c.read(1000) } catch (e: IOException) {} }, "cb-cvte-rx").apply { isDaemon = true; start() }
        ready(info?.name)
        log("cvte: connectée")
    }

    @Synchronized override fun send(key: RemoteKey) {
        if (ws?.closed != false) { log("cvte: reconnexion"); connect() }       // the TV drops idle links: one silent reconnection
        try { ws!!.sendBinary(CvteMessage.key(key.code)) } catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    private fun closeLink() { runCatching { ws?.close() }; ws = null }
    override fun close() { closeLink(); super.close() }
}

// ------------------------------------------------------------------------------------------------------------------------
// Roku — External Control Protocol (documented by Roku): HTTP on port 8060.
// ------------------------------------------------------------------------------------------------------------------------
class RokuStrategy(tv: TvTarget, private val port: Int = 8060, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.ROKU
    override val label = "Roku (ECP)"
    override val status = StrategyStatus.STABLE
    override val verifiesDelivery = true
    override val capabilities = Capabilities(KEYS.keys, text = true)
    override val limits = "Volume et chaînes seulement sur un Roku TV (pas sur un boîtier). Pas de chiffres, ni de touches stop/menu."

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.ROKU || fp.vendor == Vendor.UNKNOWN && fp.mayUse(port) || StrategyIds.ROKU in fp.candidates
    private fun base() = "http://${tv.host}:$port"

    override fun probe(): ProbeResult = try {
        val r = http("GET", base() + "/query/device-info")
        ProbeResult(r.status == 200 && "<device-info" in r.body, "ECP")
    } catch (e: IOException) { ProbeResult(false, e.message) }

    override fun connect() {
        state = StrategyState(StrategyState.Kind.CONNECTING)
        if (!probe().reachable) failed("Roku ECP injoignable sur le port $port")
        ready()
    }

    override fun send(key: RemoteKey) {
        val k = KEYS[key] ?: throw KeyUnsupported(key, label)
        post("/keypress/$k")
    }

    override fun sendText(text: String): Boolean {
        for (ch in text.take(200)) post("/keypress/Lit_" + java.net.URLEncoder.encode(ch.toString(), "UTF-8").replace("+", "%20"))
        return true
    }

    private fun post(path: String) {
        val r = try { http("POST", base() + path, "") } catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
        if (r.status !in 200..299) throw IOException("Roku a refusé la touche (HTTP ${r.status})")
    }

    companion object {
        val KEYS: Map<RemoteKey, String> = mapOf(
            DPAD_UP to "Up", DPAD_DOWN to "Down", DPAD_LEFT to "Left", DPAD_RIGHT to "Right", DPAD_CENTER to "Select", BACK to "Back", HOME to "Home",
            INFO to "Info", PLAY_PAUSE to "Play", REWIND to "Rev", FAST_FORWARD to "Fwd", PREVIOUS to "InstantReplay",
            VOLUME_UP to "VolumeUp", VOLUME_DOWN to "VolumeDown", VOLUME_MUTE to "VolumeMute", CHANNEL_UP to "ChannelUp", CHANNEL_DOWN to "ChannelDown",
            ENTER to "Enter", DEL to "Backspace",
        )
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// DLNA / UPnP: AVTransport + RenderingControl (standard SOAP). Any renderer: play/pause/stop/next/previous and volume.
// ------------------------------------------------------------------------------------------------------------------------
class DlnaStrategy(tv: TvTarget, private var upnp: UpnpInfo?, private val descriptionUrl: String?, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.DLNA
    override val label = "DLNA / UPnP (lecture et volume)"
    override val status = StrategyStatus.STABLE
    override val verifiesDelivery = true
    override val capabilities = Capabilities(setOf(PLAY_PAUSE, PLAY, PAUSE, STOP, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE))
    override val limits = "Seulement la lecture en cours et le volume : pas de flèches, d'OK, de retour ni d'accueil. Certaines TV exigent qu'un contenu soit en lecture."

    private var avt: Pair<String, String>? = null      // (service type, control url)
    private var rc: Pair<String, String>? = null
    private var muted = false

    override fun applicable(fp: TvFingerprint) = fp.upnp?.serviceTypes?.any { it.contains("AVTransport") || it.contains("RenderingControl") } == true || StrategyIds.DLNA in fp.candidates

    override fun probe(): ProbeResult = try {
        val info = upnp ?: descriptionUrl?.let { u -> Upnp.parseDescription(http("GET", u).body, u) }
        ProbeResult(info != null && info.serviceTypes.isNotEmpty(), info?.friendlyName)
    } catch (e: IOException) { ProbeResult(false, e.message) }

    override fun connect() {
        state = StrategyState(StrategyState.Kind.CONNECTING)
        if (upnp == null || upnp!!.controlUrls.isEmpty()) upnp = descriptionUrl?.let { u -> try { Upnp.parseDescription(http("GET", u).body, u) } catch (e: IOException) { failed("description UPnP illisible") } }
        val urls = upnp?.controlUrls ?: failed("aucun service UPnP")
        avt = urls.entries.firstOrNull { it.key.contains("AVTransport") }?.toPair()
        rc = urls.entries.firstOrNull { it.key.contains("RenderingControl") }?.toPair()
        if (avt == null && rc == null) failed("ni AVTransport ni RenderingControl")
        ready(); log("dlna: services ${listOfNotNull(avt?.let { "AVTransport" }, rc?.let { "RenderingControl" }).joinToString("+")}")
    }

    override fun send(key: RemoteKey) {
        try {
            when (key) {
                PLAY -> av("Play", "<Speed>1</Speed>")
                PAUSE -> av("Pause"); STOP -> av("Stop"); NEXT -> av("Next"); PREVIOUS -> av("Previous")
                PLAY_PAUSE -> if (transportState() == "PLAYING") av("Pause") else av("Play", "<Speed>1</Speed>")
                VOLUME_UP -> setVolume(+STEP); VOLUME_DOWN -> setVolume(-STEP)
                VOLUME_MUTE -> { muted = !muted; render("SetMute", "<Channel>Master</Channel><DesiredMute>${if (muted) 1 else 0}</DesiredMute>") }
                else -> throw KeyUnsupported(key, label)
            }
        } catch (e: KeyUnsupported) { throw e } catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    private fun setVolume(delta: Int) {
        val cur = Regex("<CurrentVolume>\\s*(\\d+)\\s*</CurrentVolume>").find(render("GetVolume", "<Channel>Master</Channel>"))?.groupValues?.get(1)?.toIntOrNull() ?: throw IOException("volume illisible")
        render("SetVolume", "<Channel>Master</Channel><DesiredVolume>${(cur + delta).coerceIn(0, 100)}</DesiredVolume>")
    }

    private fun transportState() = Regex("<CurrentTransportState>\\s*(\\w+)\\s*</CurrentTransportState>").find(av("GetTransportInfo"))?.groupValues?.get(1)

    private fun av(action: String, args: String = "") = soap(avt ?: throw KeyUnsupported(PLAY, "AVTransport absent"), action, args)
    private fun render(action: String, args: String) = soap(rc ?: throw KeyUnsupported(VOLUME_UP, "RenderingControl absent"), action, args)

    private fun soap(svc: Pair<String, String>, action: String, args: String): String {
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="${svc.first}"><InstanceID>0</InstanceID>$args</u:$action></s:Body></s:Envelope>"""
        val r = http("POST", svc.second, body, mapOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"${svc.first}#$action\""))
        if (r.status !in 200..299) throw IOException("UPnP $action refusé (HTTP ${r.status})")
        return r.body
    }

    companion object { const val STEP = 5 }
}

// ------------------------------------------------------------------------------------------------------------------------
// Sony BRAVIA: IRCC over SOAP (documented by Sony), authenticated by a pre-shared key set on the TV (Network › IP control).
// ------------------------------------------------------------------------------------------------------------------------
object SonyIrcc {
    /** IRCC code = base64 of 00 00 00 <a> 00 00 00 <b> 00 00 00 <c> 03 (a/b = code family, c = key). */
    fun code(a: Int, b: Int, c: Int): String = java.util.Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0, a.toByte(), 0, 0, 0, b.toByte(), 0, 0, 0, c.toByte(), 3))

    val CODES: Map<RemoteKey, String> = buildMap {
        put(VOLUME_UP, code(1, 1, 0x12)); put(VOLUME_DOWN, code(1, 1, 0x13)); put(VOLUME_MUTE, code(1, 1, 0x14))
        put(DPAD_UP, code(1, 1, 0x74)); put(DPAD_DOWN, code(1, 1, 0x75)); put(DPAD_LEFT, code(1, 1, 0x34)); put(DPAD_RIGHT, code(1, 1, 0x33)); put(DPAD_CENTER, code(1, 1, 0x65))
        put(HOME, code(2, 0x1A, 0x57)); put(BACK, code(2, 0x97, 0x23))
        put(PLAY, code(2, 0x1A, 0x1A)); put(PAUSE, code(2, 0x1A, 0x19)); put(STOP, code(2, 0x1A, 0x18))
        listOf(NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9, NUM_0).forEachIndexed { i, k -> put(k, code(1, 1, i)) }
    }
}

class SonyStrategy(tv: TvTarget, private val secrets: SecretStore, private val port: Int = 80, log: (String) -> Unit = {}) : BaseStrategy(tv, log), Pairable {
    override val id = StrategyIds.SONY
    override val label = "Sony BRAVIA (IRCC)"
    override val status = StrategyStatus.STABLE
    override val verifiesDelivery = true
    override val capabilities = Capabilities(SonyIrcc.CODES.keys)
    override val limits = "Exige « Contrôle IP » + une clé pré-partagée (PSK) réglées sur la TV (Réglages › Réseau). Pas de texte."

    private val pskKey get() = "sony-psk:${tv.id}"
    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.SONY || fp.vendor == Vendor.UNKNOWN && fp.mayUse(port) && fp.upnp != null || StrategyIds.SONY in fp.candidates
    override fun probe() = ProbeResult(tcpOpen(port), "port $port")

    /** [code] = the PSK typed by the user (stored in private storage, never logged). */
    override fun pair(code: String) { require(code.isNotBlank() && code.length <= 64); secrets.put(pskKey, code.trim()) }

    override fun connect() {
        state = StrategyState(StrategyState.Kind.CONNECTING)
        val psk = secrets.get(pskKey) ?: needsPairing("Saisissez la clé pré-partagée (PSK) réglée sur la TV (Réglages › Réseau › Contrôle IP).")
        val r = try { http("POST", "http://${tv.host}:$port/sony/system", """{"method":"getPowerStatus","id":1,"params":[],"version":"1.0"}""", mapOf("X-Auth-PSK" to psk, "Content-Type" to "application/json")) }
            catch (e: IOException) { failed("TV injoignable : ${e.message}") }
        when (r.status) { 200 -> ready(); 401, 403 -> { secrets.remove(pskKey); needsPairing("Clé PSK refusée par la TV : saisissez-la de nouveau.") }; else -> failed("réponse inattendue (HTTP ${r.status})") }
    }

    override fun send(key: RemoteKey) {
        val code = SonyIrcc.CODES[key] ?: throw KeyUnsupported(key, label)
        val psk = secrets.get(pskKey) ?: needsPairing("Clé PSK absente")
        val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
        val r = try { http("POST", "http://${tv.host}:$port/sony/IRCC", body, mapOf("X-Auth-PSK" to psk, "Content-Type" to "text/xml; charset=UTF-8", "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")) }
            catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
        if (r.status !in 200..299) throw IOException("Sony a refusé la touche (HTTP ${r.status})")
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Philips JointSpace v1 (HTTP, port 1925, no authentication). v6 (digest pairing on 1926) is NOT implemented. EXPERIMENTAL.
// ------------------------------------------------------------------------------------------------------------------------
class PhilipsStrategy(tv: TvTarget, private val port: Int = 1925, log: (String) -> Unit = {}) : BaseStrategy(tv, log) {
    override val id = StrategyIds.PHILIPS
    override val label = "Philips (JointSpace v1)"
    override val status = StrategyStatus.EXPERIMENTAL
    override val verifiesDelivery = true
    override val capabilities = Capabilities(KEYS.keys)
    override val limits = "API v1 seulement (TV anciennes). Les Philips récentes (API v6, appairage signé) ne sont pas prises en charge."

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.PHILIPS || fp.vendor == Vendor.UNKNOWN && fp.mayUse(port) && fp.openPorts != null || StrategyIds.PHILIPS in fp.candidates
    override fun probe(): ProbeResult = try { ProbeResult(http("GET", "http://${tv.host}:$port/1/system").status == 200, "API v1") } catch (e: IOException) { ProbeResult(false, e.message) }

    override fun connect() {
        state = StrategyState(StrategyState.Kind.CONNECTING)
        if (!probe().reachable) failed("API JointSpace v1 absente (TV récente en v6 ?)")
        ready()
    }

    override fun send(key: RemoteKey) {
        val k = KEYS[key] ?: throw KeyUnsupported(key, label)
        val r = try { http("POST", "http://${tv.host}:$port/1/input/key", """{"key":"$k"}""", mapOf("Content-Type" to "application/json")) }
            catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
        if (r.status !in 200..299) throw IOException("Philips a refusé la touche (HTTP ${r.status})")
    }

    companion object {
        val KEYS: Map<RemoteKey, String> = buildMap {
            put(DPAD_UP, "CursorUp"); put(DPAD_DOWN, "CursorDown"); put(DPAD_LEFT, "CursorLeft"); put(DPAD_RIGHT, "CursorRight"); put(DPAD_CENTER, "Confirm")
            put(BACK, "Back"); put(HOME, "Home"); put(VOLUME_UP, "VolumeUp"); put(VOLUME_DOWN, "VolumeDown"); put(VOLUME_MUTE, "Mute")
            put(PLAY, "Play"); put(PAUSE, "Pause"); put(STOP, "Stop"); put(NEXT, "Next"); put(PREVIOUS, "Previous")
            put(REWIND, "Rewind"); put(FAST_FORWARD, "FastForward"); put(CHANNEL_UP, "ChannelStepUp"); put(CHANNEL_DOWN, "ChannelStepDown"); put(INFO, "Info")
            listOf(NUM_0, NUM_1, NUM_2, NUM_3, NUM_4, NUM_5, NUM_6, NUM_7, NUM_8, NUM_9).forEachIndexed { i, k -> put(k, "Digit$i") }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Vizio SmartCast (HTTPS, port 7345 then 9000, self-signed certificate, pairing by on-screen PIN). EXPERIMENTAL.
// ------------------------------------------------------------------------------------------------------------------------
class VizioStrategy(tv: TvTarget, private val secrets: SecretStore, private val ports: List<Int> = listOf(7345, 9000), private val scheme: String = "https", log: (String) -> Unit = {}) : BaseStrategy(tv, log), Pairable {
    override val id = StrategyIds.VIZIO
    override val label = "Vizio SmartCast"
    override val status = StrategyStatus.EXPERIMENTAL
    override val verifiesDelivery = true
    override val capabilities = Capabilities(KEYS.keys)
    override val limits = "Appairage par code affiché sur la TV (une seule fois). Certificat de la TV auto-signé : accepté pour cette TV uniquement."

    private val tokenKey get() = "vizio-token:${tv.id}"
    @Volatile private var port = ports.first()
    @Volatile private var pairingToken: Int? = null
    private val deviceId get() = "castbridge-" + tv.id.filter { it.isLetterOrDigit() }.take(16)

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.VIZIO || fp.vendor == Vendor.UNKNOWN && fp.openPorts != null && ports.any { it in fp.openPorts } || StrategyIds.VIZIO in fp.candidates
    override fun probe() = ProbeResult(ports.any { tcpOpen(it) }, "ports ${ports.joinToString("/")}")

    private fun url(path: String) = "$scheme://${tv.host}:$port$path"
    private fun put(path: String, body: String, auth: String? = null) =
        http("PUT", url(path), body, buildMap { put("Content-Type", "application/json"); if (auth != null) put("AUTH", auth) }, trust = true)

    override fun connect() {
        state = StrategyState(StrategyState.Kind.CONNECTING)
        port = ports.firstOrNull { tcpOpen(it) } ?: failed("SmartCast injoignable")
        if (secrets.get(tokenKey) != null) { ready(); return }
        val r = try { put("/pairing/start", """{"DEVICE_NAME":"CastBridge","DEVICE_ID":"$deviceId"}""") } catch (e: IOException) { failed("appairage impossible : ${e.message}") }
        pairingToken = Regex("\"PAIRING_REQ_TOKEN\"\\s*:\\s*(\\d+)").find(r.body)?.groupValues?.get(1)?.toIntOrNull() ?: failed("la TV n'a pas lancé l'appairage")
        needsPairing("Entrez le code affiché sur la TV.")
    }

    override fun pair(code: String) {
        val t = pairingToken ?: throw IOException("appairage non démarré")
        val r = put("/pairing/pair", """{"DEVICE_ID":"$deviceId","CHALLENGE_TYPE":1,"RESPONSE_VALUE":"${code.filter { it.isLetterOrDigit() }}","PAIRING_REQ_TOKEN":$t}""")
        val tok = Regex("\"AUTH_TOKEN\"\\s*:\\s*\"([^\"]+)\"").find(r.body)?.groupValues?.get(1) ?: throw IOException("code refusé par la TV")
        secrets.put(tokenKey, tok); pairingToken = null
    }

    override fun send(key: RemoteKey) {
        val (set, code) = KEYS[key] ?: throw KeyUnsupported(key, label)
        val tok = secrets.get(tokenKey) ?: needsPairing("Appairage requis")
        val r = try { put("/key_command/", """{"KEYLIST":[{"CODESET":$set,"CODE":$code,"ACTION":"KEYPRESS"}]}""", tok) }
            catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
        if (r.status == 401 || r.status == 403) { secrets.remove(tokenKey); needsPairing("Appairage expiré") }
        if (r.status !in 200..299) throw IOException("Vizio a refusé la touche (HTTP ${r.status})")
    }

    companion object {
        /** key -> (codeset, code), from the community SmartCast documentation. */
        val KEYS: Map<RemoteKey, Pair<Int, Int>> = mapOf(
            VOLUME_UP to (5 to 1), VOLUME_DOWN to (5 to 0), VOLUME_MUTE to (5 to 4), CHANNEL_UP to (8 to 1), CHANNEL_DOWN to (8 to 0),
            DPAD_UP to (3 to 8), DPAD_DOWN to (3 to 0), DPAD_LEFT to (3 to 1), DPAD_RIGHT to (3 to 7), DPAD_CENTER to (3 to 2),
            BACK to (4 to 0), MENU to (4 to 8), HOME to (4 to 15), INFO to (4 to 6), PLAY to (2 to 3), PAUSE to (2 to 2),
        )
    }
}
