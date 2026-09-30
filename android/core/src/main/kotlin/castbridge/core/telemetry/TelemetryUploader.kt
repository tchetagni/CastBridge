package castbridge.core.telemetry

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.util.zip.GZIPOutputStream

/**
 * Sends the queued events to POST /api/v1/events/batch: gzip, at most 500 events per batch, authenticated by the
 * device token. A batch leaves the queue only when the server answered (accepted, duplicate or refused); on a network
 * error it stays for the next flush (the server de-duplicates a replayed batch). Call [flush] every 15 minutes, when the
 * network comes back, or when the Bluetooth gateway of the phone is available.
 */
class TelemetryUploader(
    baseUrl: String,
    proxy: Proxy? = null,
    private val http: HttpLite = HttpLite(proxy, userAgent = "CastBridge-telemetry"),
) {
    private val base = baseUrl.trimEnd('/')

    sealed class Result {
        data class Sent(val accepted: Int, val duplicates: Int, val rejected: Int, val batches: Int) : Result()
        /** The server does not know the token: register again (DeviceClient), then flush. */
        object NeedsRegistration : Result()
        data class Failed(val reason: String) : Result()
    }

    fun flush(queue: EventQueue, deviceToken: String, app: String, versionCode: Int, maxBatches: Int = 20): Result {
        var accepted = 0; var duplicates = 0; var rejected = 0; var batches = 0
        while (batches < maxBatches) {
            val events = queue.peek()
            if (events.isEmpty()) break
            val body = "{\"app\":${JsonLite.quote(app)},\"versionCode\":$versionCode,\"events\":[${events.joinToString(",")}]}"
            val r = try {
                post(gzip(body), deviceToken)
            } catch (e: IOException) {
                return Result.Failed("serveur injoignable : ${e.message ?: e.javaClass.simpleName}")
            }
            when (r.code) {
                200 -> {
                    val m = runCatching { JsonLite.obj(r.body) }.getOrDefault(emptyMap())
                    accepted += m.int("accepted") ?: 0; duplicates += m.int("duplicates") ?: 0; rejected += m.int("rejected") ?: 0
                    queue.drop(events.size)
                    batches++
                }
                401 -> return Result.NeedsRegistration
                400, 413 -> { queue.drop(events.size); return Result.Failed("lot refusé : ${HttpLite.errorMessage(r)}") } // never resend it
                else -> return Result.Failed(HttpLite.errorMessage(r))
            }
        }
        return Result.Sent(accepted, duplicates, rejected, batches)
    }

    private fun post(gz: ByteArray, token: String): HttpLite.Response {
        val c = http.open("$base/api/v1/events/batch")
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Content-Encoding", "gzip")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setFixedLengthStreamingMode(gz.size)
            c.outputStream.use { it.write(gz) }
            val code = c.responseCode
            val body = (if (code >= 400) c.errorStream else c.inputStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return HttpLite.Response(code, body, emptyMap())
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val FLUSH_EVERY_MS = 15 * 60_000L

        fun gzip(s: String): ByteArray {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(s.toByteArray(Charsets.UTF_8)) }
            return bos.toByteArray()
        }

        fun shouldFlush(lastFlushAt: Long, now: Long, queued: Int): Boolean = queued > 0 && (now - lastFlushAt >= FLUSH_EVERY_MS || queued >= 400)
    }
}
