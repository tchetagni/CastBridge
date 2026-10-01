package castbridge.core.remote.smart

import castbridge.core.quiz.Json
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteKey.*
import java.io.IOException
import java.util.Base64

/** Next JSON text message as a map, or null (timeout, binary or malformed frame). */
internal fun readJson(c: WsClient, timeoutMs: Int): Map<String, Any?>? = c.read(timeoutMs)?.text?.let { t -> runCatching { Json.obj(t) }.getOrNull() }

private fun jstr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ------------------------------------------------------------------------------------------------------------------------
// Samsung Tizen (2016+): WebSocket 8002 (wss) / 8001 (ws), authorisation token granted once on the TV. Community-documented. EXPERIMENTAL.
// ------------------------------------------------------------------------------------------------------------------------
class SamsungStrategy(
    tv: TvTarget, private val secrets: SecretStore, private val ports: List<Pair<Int, Boolean>> = listOf(8002 to true, 8001 to false),
    private val pairWaitMs: Int = 20_000, log: (String) -> Unit = {},
) : BaseStrategy(tv, log) {
    override val id = StrategyIds.SAMSUNG
    override val label = "Samsung (Tizen)"
    override val status = StrategyStatus.EXPERIMENTAL
    override val capabilities = Capabilities(KEYS.keys)
    override val limits = "La première fois, la TV demande d'autoriser « CastBridge » (accepter à l'écran). Pas de texte ni de pavé tactile."

    @Volatile private var ws: WsClient? = null
    private val tokenKey get() = "samsung-token:${tv.id}"

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.SAMSUNG || fp.vendor == Vendor.UNKNOWN && fp.openPorts != null && ports.any { it.first in fp.openPorts } || StrategyIds.SAMSUNG in fp.candidates
    override fun probe() = ProbeResult(ports.any { tcpOpen(it.first) }, "ports 8001/8002")

    @Synchronized override fun connect() {
        runCatching { ws?.close() }; ws = null
        state = StrategyState(StrategyState.Kind.CONNECTING)
        val name = Base64.getEncoder().encodeToString("CastBridge".toByteArray())
        var last: IOException? = null
        for ((port, tls) in ports) {
            val token = secrets.get(tokenKey)
            val path = "/api/v2/channels/samsung.remote.control?name=$name" + if (token != null) "&token=${java.net.URLEncoder.encode(token, "UTF-8")}" else ""
            val c = try { WsClient.connect(tv.host, port, path, tls, timeoutMs = tv.connectTimeoutMs) } catch (e: IOException) { last = e; continue }
            try {
                val deadline = System.currentTimeMillis() + if (token == null) pairWaitMs else 4000
                while (System.currentTimeMillis() < deadline) {
                    val o = readJson(c, 500) ?: continue
                    when (o["event"]) {
                        "ms.channel.connect" -> {
                            @Suppress("UNCHECKED_CAST") (o["data"] as? Map<String, Any?>)?.get("token")?.toString()?.takeIf { it.isNotEmpty() }?.let { secrets.put(tokenKey, it) }
                            ws = c; ready(); log("samsung: connectée (port $port)"); return
                        }
                        "ms.channel.unauthorized", "ms.channel.timeOut" -> { c.close(); secrets.remove(tokenKey); needsPairing("La TV n'a pas autorisé CastBridge : acceptez la demande à l'écran, puis réessayez.") }
                    }
                }
                c.close()
                if (token == null) needsPairing("Acceptez la demande « CastBridge » affichée sur la TV, puis réessayez.") else last = IOException("la TV ne répond pas")
            } catch (e: StrategyException) { throw e } catch (e: IOException) { c.close(); last = e }
        }
        failed("connexion impossible : ${last?.message ?: "ports fermés"}")
    }

    @Synchronized override fun send(key: RemoteKey) {
        val k = KEYS[key] ?: throw KeyUnsupported(key, label)
        if (ws?.closed != false) connect()
        try { ws!!.sendText(message(k)); drain() } catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    private fun drain() { runCatching { ws?.read(1) } }          // surfaces a close; the TV sends nothing for a key press
    override fun close() { runCatching { ws?.close() }; ws = null; super.close() }

    companion object {
        fun message(samsungKey: String) = """{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":${jstr(samsungKey)},"Option":"false","TypeOfRemote":"SendRemoteKey"}}"""
        val KEYS: Map<RemoteKey, String> = buildMap {
            put(DPAD_UP, "KEY_UP"); put(DPAD_DOWN, "KEY_DOWN"); put(DPAD_LEFT, "KEY_LEFT"); put(DPAD_RIGHT, "KEY_RIGHT"); put(DPAD_CENTER, "KEY_ENTER")
            put(BACK, "KEY_RETURN"); put(HOME, "KEY_HOME"); put(MENU, "KEY_MENU"); put(INFO, "KEY_INFO"); put(GUIDE, "KEY_GUIDE")
            put(VOLUME_UP, "KEY_VOLUP"); put(VOLUME_DOWN, "KEY_VOLDOWN"); put(VOLUME_MUTE, "KEY_MUTE"); put(CHANNEL_UP, "KEY_CHUP"); put(CHANNEL_DOWN, "KEY_CHDOWN")
            put(PLAY, "KEY_PLAY"); put(PAUSE, "KEY_PAUSE"); put(STOP, "KEY_STOP"); put(REWIND, "KEY_REWIND"); put(FAST_FORWARD, "KEY_FF")
            for (d in 0..9) put(RemoteKey.parse("$d")!!, "KEY_$d")
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// LG webOS: SSAP over WebSocket 3000 (ws) / 3001 (wss), pairing prompt on the TV, then a client key. Community-documented. EXPERIMENTAL.
// Volume / media / channel = SSAP requests; direction keys, OK, Back, Home, digits = the "pointer input" socket (text lines).
// ------------------------------------------------------------------------------------------------------------------------
class LgStrategy(
    tv: TvTarget, private val secrets: SecretStore, private val ports: List<Pair<Int, Boolean>> = listOf(3000 to false, 3001 to true),
    private val pairWaitMs: Int = 30_000, log: (String) -> Unit = {},
) : BaseStrategy(tv, log) {
    override val id = StrategyIds.LG
    override val label = "LG (webOS)"
    override val status = StrategyStatus.EXPERIMENTAL
    override val capabilities = Capabilities(SSAP.keys + BUTTONS.keys, text = false)
    override val limits = "La première fois, la TV demande d'accepter l'appairage (à l'écran). Pas de texte pour l'instant."

    private val clientKey get() = "lg-key:${tv.id}"
    @Volatile private var ws: WsClient? = null
    @Volatile private var pointer: WsClient? = null
    private var counter = 0
    private var muted = false

    override fun applicable(fp: TvFingerprint) = fp.vendor == Vendor.LG || fp.vendor == Vendor.UNKNOWN && fp.openPorts != null && ports.any { it.first in fp.openPorts } || StrategyIds.LG in fp.candidates
    override fun probe() = ProbeResult(ports.any { tcpOpen(it.first) }, "ports 3000/3001")

    @Synchronized override fun connect() {
        closeLinks()
        state = StrategyState(StrategyState.Kind.CONNECTING)
        var last: IOException? = null
        for ((port, tls) in ports) {
            val c = try { WsClient.connect(tv.host, port, "/", tls, timeoutMs = tv.connectTimeoutMs) } catch (e: IOException) { last = e; continue }
            try {
                val key = secrets.get(clientKey)
                c.sendText(registerMessage(key))
                val deadline = System.currentTimeMillis() + if (key == null) pairWaitMs else 5000
                var prompted = false
                while (System.currentTimeMillis() < deadline) {
                    val o = readJson(c, 500) ?: continue
                    when (o["type"]) {
                        "registered" -> {
                            @Suppress("UNCHECKED_CAST") (o["payload"] as? Map<String, Any?>)?.get("client-key")?.toString()?.takeIf { it.isNotEmpty() }?.let { secrets.put(clientKey, it) }
                            ws = c; ready(); log("lg: connectée (port $port)"); return
                        }
                        "error" -> { c.close(); secrets.remove(clientKey); failed("la TV a refusé l'appairage") }
                        "response" -> prompted = true
                    }
                }
                c.close()
                if (prompted || key == null) needsPairing("Acceptez la demande d'appairage affichée sur la TV, puis réessayez.") else last = IOException("la TV ne répond pas")
            } catch (e: StrategyException) { throw e } catch (e: IOException) { c.close(); last = e }
        }
        failed("connexion impossible : ${last?.message ?: "ports fermés"}")
    }

    @Synchronized override fun send(key: RemoteKey) {
        if (ws?.closed != false) connect()
        try {
            SSAP[key]?.let { request(it); return }
            val b = BUTTONS[key] ?: throw KeyUnsupported(key, label)
            if (key == VOLUME_MUTE) { muted = !muted }
            pointerSocket().sendText("type:button\nname:$b\n\n")
        } catch (e: IOException) { if (e !is KeyUnsupported) state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    private fun request(uri: String, payload: String? = null) {
        ws!!.sendText("""{"type":"request","id":"cb_${++counter}","uri":${jstr(uri)}${payload?.let { ",\"payload\":$it" } ?: ""}}""")
        runCatching { ws!!.read(1) }
    }

    /** Second socket on the TV's own "networkinput" service; opened on first use. */
    private fun pointerSocket(): WsClient {
        pointer?.takeIf { !it.closed }?.let { return it }
        val c = ws!!
        c.sendText("""{"type":"request","id":"cb_ptr","uri":"ssap://com.webos.service.networkinput/getPointerInputSocket"}""")
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            val o = readJson(c, 300) ?: continue
            if (o["id"] != "cb_ptr") continue
            @Suppress("UNCHECKED_CAST") val path = (o["payload"] as? Map<String, Any?>)?.get("socketPath")?.toString() ?: throw IOException("pas de socket de pointeur")
            val u = java.net.URI(path)
            if (!tv.sameHost(u.host)) throw IOException("socket de pointeur hors de la TV choisie")
            return WsClient.connect(tv.host, u.port, u.rawPath + (u.rawQuery?.let { "?$it" } ?: ""), u.scheme == "wss", timeoutMs = tv.connectTimeoutMs).also { pointer = it }
        }
        throw IOException("socket de pointeur indisponible")
    }

    private fun closeLinks() { runCatching { pointer?.close() }; runCatching { ws?.close() }; pointer = null; ws = null }
    override fun close() { closeLinks(); super.close() }

    companion object {
        /** The `register` request; [clientKey] is a secret and is only ever sent to the chosen TV. */
        fun registerMessage(clientKey: String?): String {
            val perms = listOf("LAUNCH", "CONTROL_AUDIO", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_INPUT_TEXT").joinToString(",") { jstr(it) }
            return """{"type":"register","id":"register_0","payload":{"forcePairing":false,"pairingType":"PROMPT",${clientKey?.let { "\"client-key\":${jstr(it)}," } ?: ""}"manifest":{"manifestVersion":1,"permissions":[$perms]}}}"""
        }
        val SSAP: Map<RemoteKey, String> = mapOf(
            VOLUME_UP to "ssap://audio/volumeUp", VOLUME_DOWN to "ssap://audio/volumeDown", CHANNEL_UP to "ssap://tv/channelUp", CHANNEL_DOWN to "ssap://tv/channelDown",
            PLAY to "ssap://media.controls/play", PAUSE to "ssap://media.controls/pause", STOP to "ssap://media.controls/stop",
            REWIND to "ssap://media.controls/rewind", FAST_FORWARD to "ssap://media.controls/fastForward",
        )
        val BUTTONS: Map<RemoteKey, String> = buildMap {
            put(DPAD_UP, "UP"); put(DPAD_DOWN, "DOWN"); put(DPAD_LEFT, "LEFT"); put(DPAD_RIGHT, "RIGHT"); put(DPAD_CENTER, "ENTER")
            put(BACK, "BACK"); put(HOME, "HOME"); put(MENU, "MENU"); put(INFO, "INFO"); put(VOLUME_MUTE, "MUTE")
            for (d in 0..9) put(RemoteKey.parse("$d")!!, "$d")
        }
    }
}
