package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One answer of the TV's HTTP API: status code and JSON text. */
class TvReply(val status: Int, val json: String)

/**
 * The phone's way to talk to the TV's HTTP API, reduced to one call. [params] are raw (NOT encoded): the implementation encodes them. Throws [IOException] when the link is down.
 * In tests it is the TV's own API objects called in process; on the phone it is [HttpTvTransport].
 */
fun interface TvTransport {
    fun call(method: String, path: String, params: Map<String, String>, body: ByteArray?): TvReply
}

/** [TvTransport] over HTTP ([base] = "http://192.168.1.20:8765", or the Bluetooth tunnel's local base URL). The credential is applied by the one place that builds the header. */
class HttpTvTransport(private val base: String, private val pin: String?, private val timeoutMs: Int = 8000) : TvTransport {
    override fun call(method: String, path: String, params: Map<String, String>, body: ByteArray?): TvReply {
        val q = if (params.isEmpty()) "" else params.entries.joinToString("&", "?") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        val c = URL(base.trimEnd('/') + path + q).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs * 4
            castbridge.core.trust.TvCredential.apply(c, pin)
            if (method == "POST") {
                c.doOutput = true; c.setFixedLengthStreamingMode(body?.size ?: 0)
                c.outputStream.use { o -> body?.let { o.write(it) } }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return TvReply(code, text)
        } finally { c.disconnect() }
    }
}

/** A SEALED lot file to deliver ([name] = LotNames.fileName), read by pieces so a big lot never sits in memory. */
class SealedLot(val name: String, val size: Long, val read: (offset: Long, length: Int) -> ByteArray) {
    companion object {
        fun ofFile(f: File, name: String = f.name) = SealedLot(name, f.length()) { off, n ->
            RandomAccessFile(f, "r").use { raf -> ByteArray(n).also { raf.seek(off); raf.readFully(it) } }
        }
        fun ofBytes(name: String, b: ByteArray) = SealedLot(name, b.size.toLong()) { off, n -> b.copyOfRange(off.toInt(), off.toInt() + n) }
    }
}

enum class DeliveryOutcome { INSTALLED, ALREADY_ON_TV, SKIPPED_ENDED, REFUSED, LINK_DOWN, CANCELLED }

data class LotDeliveryResult(val name: String, val outcome: DeliveryOutcome, val detail: String = "")

data class RentalDeliveryReport(val results: List<LotDeliveryResult>, val summary: String) {
    val installed get() = results.count { it.outcome == DeliveryOutcome.INSTALLED }
    val complete get() = results.none { it.outcome == DeliveryOutcome.REFUSED || it.outcome == DeliveryOutcome.LINK_DOWN || it.outcome == DeliveryOutcome.CANCELLED }
}

/** What the TV says about its rentals (GET /api/rental), in the words of the phone screen. */
data class TvRentalView(val reachable: Boolean, val superUnlimited: Boolean, val rentals: List<Rental>, val error: String? = null) {
    data class Rental(val contract: String, val product: String, val state: String, val usable: Boolean, val remainingMs: Long?, val message: String, val lots: List<String>,
        val unit: String? = null, val usedMinutes: Long? = null, val maxUsageMinutes: Long? = null, val remainingUsageMinutes: Long? = null, val reason: String? = null, val period: Long? = null, val endsAt: Long? = null)
    fun find(contract: String) = rentals.firstOrNull { it.contract == contract }
    fun lines(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<String> = when {
        !reachable -> listOf(error ?: "La TV n'est pas joignable.")
        rentals.isEmpty() -> listOf("Aucune location sur cette TV." + if (superUnlimited) " (droit illimité actif)" else "")
        else -> rentals.map { r ->
            val left = r.remainingUsageMinutes; val max = r.maxUsageMinutes; val end = r.endsAt
            if (r.unit == "hours" && r.usable && left != null && max != null && max > 0 && end != null)
                "${r.product} : ${RentalUsageReport.span(left)} d'utilisation restante(s) sur ${RentalUsageReport.span(max)} · à utiliser avant le " +
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM").withZone(zone).format(java.time.Instant.ofEpochMilli(end))
            else "${r.product} : ${r.message.ifBlank { r.state }} - ${r.lots.size} lot(s) sur la TV"
        } + if (superUnlimited) listOf("Droit illimité actif : aucune location ne se termine.") else emptyList()
    }
}

/** The answer of [RentalDelivery.usage]: the statement, or null and a French [message] saying why. */
data class TvUsageResult(val report: RentalUsageReport.Report?, val message: String?)

/**
 * Delivers rented lots from the phone to the TV (the TV stays OFFLINE; everything comes from the phone). Per lot: skip it if the contract has ended, skip it if the TV already holds it
 * (same lot, version and plain size/SHA-256 in the TV's manifest), send the SEALED file in [TvLotApi.CHUNK] pieces from the offset the TV already holds (resume), then ask the TV to open and
 * install it (/api/rental/install, body = the signed catalog). The sealing itself ('lot-chiffrer' of the desk) is NOT done here: the phone never holds a rental key.
 */
class RentalDelivery(private val tv: TvTransport) {

    fun status(): TvRentalView = try {
        val r = tv.call("GET", "/api/rental", emptyMap(), null)
        if (r.status != 200) TvRentalView(false, false, emptyList(), "La TV refuse de donner l'état des locations (${reason(r)}).")
        else {
            val o = JsonLite.obj(r.json)
            val rentals = (o["rentals"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map { m ->
                TvRentalView.Rental(m["contract"] as? String ?: "", m["product"] as? String ?: "", m["state"] as? String ?: "", m["usable"] == true,
                    (m["remainingMs"] as? Number)?.toLong(), m["message"] as? String ?: "", (m["lots"] as? List<*>).orEmpty().map { it.toString() },
                    m["unit"] as? String, (m["usedMinutes"] as? Number)?.toLong(), (m["maxUsageMinutes"] as? Number)?.toLong(), (m["remainingUsageMinutes"] as? Number)?.toLong(),
                    m["reason"] as? String, (m["period"] as? Number)?.toLong(), (m["endsAt"] as? Number)?.toLong())
            }
            TvRentalView(true, o["superUnlimited"] == true, rentals)
        }
    } catch (e: IOException) { TvRentalView(false, false, emptyList(), "La TV n'est pas à portée.") }
    catch (e: Exception) { TvRentalView(false, false, emptyList(), "Réponse illisible de la TV.") }

    /** The TV's usage statement (GET /api/rental/usage), parsed. An old TV has no such route (404): [TvUsageResult.message] then asks to update CastBridge-TV. */
    fun usage(): TvUsageResult = try {
        val r = tv.call("GET", "/api/rental/usage", emptyMap(), null)
        when {
            r.status == 404 -> TvUsageResult(null, "cette TV ne fournit pas de relevé : mettez CastBridge-TV à jour")
            r.status != 200 -> TvUsageResult(null, "La TV refuse de donner le relevé (${reason(r)}).")
            else -> RentalUsageReport.parse(r.json)?.let { TvUsageResult(it, null) } ?: TvUsageResult(null, "Relevé illisible de la TV.")
        }
    } catch (e: IOException) { TvUsageResult(null, "La TV n'est pas à portée.") }

    /** Lots the TV holds, read from its manifest; null if it cannot be read (then nothing is skipped as already held). */
    private fun held(): TvManifest? = try { tv.call("GET", "/api/lots", emptyMap(), null).takeIf { it.status == 200 }?.let { TvManifest.parse(it.json) } } catch (e: IOException) { null }

    fun deliver(contract: String, catalogJson: String, lots: List<SealedLot>, cancelled: () -> Boolean = { false },
                progress: (name: String, sent: Long, total: Long) -> Unit = { _, _, _ -> }): RentalDeliveryReport {
        val view = status()
        if (!view.reachable) return RentalDeliveryReport(emptyList(), view.error ?: "La TV n'est pas à portée.")
        val rental = view.find(contract)
        val ended = rental != null && !rental.usable && !view.superUnlimited && (rental.state == RentalState.EXPIRED.name || rental.state == RentalState.OVER_LIMIT.name)
        if (rental == null) return RentalDeliveryReport(emptyList(), "Cette location n'est pas encore activée sur la TV : envoyez d'abord la clé d'activation (« Activer la TV »), puis recommencez.")
        val manifest = runCatching { LotManifest.parse(catalogJson) }.getOrNull()
            ?: return RentalDeliveryReport(emptyList(), "Le catalogue signé est illisible : les lots n'ont pas été envoyés.")
        val tvLots = held()
        val results = ArrayList<LotDeliveryResult>()
        for (lot in lots) {
            if (cancelled()) { results += LotDeliveryResult(lot.name, DeliveryOutcome.CANCELLED, "interrompu"); continue }
            if (ended) { results += LotDeliveryResult(lot.name, DeliveryOutcome.SKIPPED_ENDED, "la location est terminée"); continue }
            val parsed = LotNames.parseFileName(lot.name)
            val meta = parsed?.let { (id, v) -> manifest.lots.firstOrNull { it.id == id && it.version == v } }
            if (meta == null) { results += LotDeliveryResult(lot.name, DeliveryOutcome.REFUSED, "ce lot n'est pas dans le catalogue signé"); continue }
            if (tvLots?.holds(meta) == true) { results += LotDeliveryResult(lot.name, DeliveryOutcome.ALREADY_ON_TV, "déjà sur la TV"); progress(lot.name, lot.size, lot.size); continue }
            results += try { send(lot, contract, catalogJson, cancelled, progress) } catch (e: IOException) {
                LotDeliveryResult(lot.name, DeliveryOutcome.LINK_DOWN, "la liaison avec la TV a été coupée : relancez l'envoi, il reprendra là où il s'est arrêté")
            }
        }
        return RentalDeliveryReport(results, summary(results, rental.product))
    }

    private fun send(lot: SealedLot, contract: String, catalogJson: String, cancelled: () -> Boolean, progress: (String, Long, Long) -> Unit): LotDeliveryResult {
        val name = lot.name
        var off = tv.call("GET", "/api/lots/part", mapOf("name" to name), null).let { r -> if (r.status == 200) received(r) else null } ?: 0L
        if (off > lot.size || off < 0) off = 0L
        progress(name, off, lot.size)
        while (off < lot.size) {
            if (cancelled()) return LotDeliveryResult(name, DeliveryOutcome.CANCELLED, "interrompu à ${percent(off, lot.size)} %")
            val n = minOf(TvLotApi.CHUNK.toLong(), lot.size - off).toInt()
            val r = tv.call("POST", "/api/lots/upload", mapOf("name" to name, "offset" to off.toString(), "total" to lot.size.toString()), lot.read(off, n))
            when (r.status) {
                200 -> off = received(r) ?: (off + n)
                409 -> off = received(r) ?: 0L          // the TV stands elsewhere: resume there
                else -> return LotDeliveryResult(name, DeliveryOutcome.REFUSED, reason(r))
            }
            progress(name, off, lot.size)
        }
        val r = tv.call("POST", "/api/rental/install", mapOf("name" to name, "contract" to contract), catalogJson.toByteArray(Charsets.UTF_8))
        return if (r.status == 200) LotDeliveryResult(name, DeliveryOutcome.INSTALLED) else LotDeliveryResult(name, DeliveryOutcome.REFUSED, reason(r))
    }

    private fun summary(rs: List<LotDeliveryResult>, product: String): String {
        fun n(o: DeliveryOutcome) = rs.count { it.outcome == o }
        val parts = ArrayList<String>()
        if (n(DeliveryOutcome.INSTALLED) > 0) parts += "${n(DeliveryOutcome.INSTALLED)} lot(s) installé(s) sur la TV"
        if (n(DeliveryOutcome.ALREADY_ON_TV) > 0) parts += "${n(DeliveryOutcome.ALREADY_ON_TV)} déjà présent(s)"
        if (n(DeliveryOutcome.SKIPPED_ENDED) > 0) parts += "${n(DeliveryOutcome.SKIPPED_ENDED)} ignoré(s) : la location est terminée"
        rs.filter { it.outcome == DeliveryOutcome.REFUSED }.forEach { parts += "refusé par la TV (${it.name}) : ${it.detail}" }
        if (n(DeliveryOutcome.LINK_DOWN) > 0) parts += "liaison coupée : relancez, l'envoi reprendra où il s'est arrêté"
        if (n(DeliveryOutcome.CANCELLED) > 0) parts += "${n(DeliveryOutcome.CANCELLED)} lot(s) non envoyé(s) (interrompu)"
        return if (parts.isEmpty()) "Aucun lot à envoyer pour la location « $product »." else "Location « $product » : " + parts.joinToString(" ; ") + "."
    }

    private fun percent(a: Long, b: Long) = if (b <= 0) 100 else (a * 100 / b).toInt()
    private fun received(r: TvReply) = runCatching { JsonLite.obj(r.json).long("received") }.getOrNull()
    private fun reason(r: TvReply) = runCatching { JsonLite.obj(r.json)["error"] as? String }.getOrNull() ?: "HTTP ${r.status}"
}
