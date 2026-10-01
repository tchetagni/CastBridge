package castbridge.core.policy

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.owner.OwnerFrames
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * HTTP side of the phone (docs/ORDRES.md § Serveur), authenticated by the DEVICE token like the lots (`Authorization: Bearer`):
 *   GET  /api/v1/orders?since=<cursor>   → {"cursor":n,"orders":[{"tv":"<device code>","token":"cbx1…"}]}   only the TVs paired with this phone
 *   POST /api/v1/orders/pair             ← {"deviceInfo":"code=…\nk=…\nfactor=…"}  once per TV (the TV's device request)
 *   POST /api/v1/orders/acks             ← {"acks":[{"tv":"<device code>","ack":"<OrderAck text>"}]}           technical acknowledgements only
 * Any failure (no Internet, 4xx, 5xx, bad JSON) = null / false: the phone just tries again at the next job.
 */
class HttpOrderServer(baseUrl: String, private val deviceToken: String, private val http: HttpLite = HttpLite(userAgent = "CastBridge-orders")) : OrderServerApi {
    private val base = baseUrl.trimEnd('/')
    private fun auth() = mapOf("Authorization" to "Bearer $deviceToken")

    override fun fetch(since: Long): Fetched? = runCatching {
        val r = http.request("GET", "$base/api/v1/orders?" + HttpLite.query("since" to since), headers = auth())
        if (r.code != 200) return null
        parseFetched(r.body)
    }.getOrNull()

    /** `POST /api/v1/orders/pair` {"deviceInfo": the TV's DEVICE_INFO text}: tells the server this phone carries the orders of that TV (the server recomputes the code from the factors). */
    fun pair(deviceInfo: String): Boolean = runCatching {
        http.request("POST", "$base/api/v1/orders/pair", jsonBody = JsonLite.write(mapOf("deviceInfo" to deviceInfo)), headers = auth()).code in 200..299
    }.getOrDefault(false)

    override fun postAcks(acks: List<PendingAck>): Boolean = runCatching {
        http.request("POST", "$base/api/v1/orders/acks", jsonBody = acksBody(acks), headers = auth()).code in 200..299
    }.getOrDefault(false)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parseFetched(json: String): Fetched? = runCatching {
            val m = JsonLite.obj(json)
            Fetched((m["cursor"] as Number).toLong(), (m["orders"] as List<Map<String, Any?>>).map { ServerOrder(it["tv"] as String, it["token"] as String) })
        }.getOrNull()

        fun acksBody(acks: List<PendingAck>): String = JsonLite.write(mapOf("acks" to acks.map { mapOf("tv" to it.tv, "ack" to it.ack.toText()) }))
    }
}

/**
 * [OrderLink] over the byte streams of the owner Bluetooth channel (or of the Wi-Fi tunnel): writes frames, reads the next frame if one arrives within [quietMs], else null.
 * The `CBTO` handshake is written once at creation (an old TV that does not speak the channel closes it: the first write or read then fails and the courier reports a drop).
 */
class StreamOrderLink(private val input: InputStream, private val output: OutputStream, private val quietMs: Long = 1500, private val sleep: (Long) -> Unit = Thread::sleep) : OrderLink {
    init { output.write(OwnerFrames.hello()); output.flush() }

    override fun write(frame: ByteArray) { output.write(frame); output.flush() }

    override fun read(): OwnerFrames.Frame? {
        var waited = 0L
        while (input.available() <= 0) {
            if (waited >= quietMs) return null
            sleep(20); waited += 20
        }
        return OwnerFrames.read(input) ?: throw IOException("liaison fermée")
    }
}
