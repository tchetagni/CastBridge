package castbridge.core.wallet.ui

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.InstallSigner
import castbridge.core.wallet.WalletCache
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletRefusal
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Un échange HTTPS avec l'API : l'application fournit l'adresse, le jeton d'appareil et les chemins réseau (direct, puis passerelle SOCKS du téléphone) ; les tests fournissent un transport scripté. */
interface WalletTransport {
    /** [path] commence par `/api/v1/wallet/…`. Lève [IOException] si le serveur est injoignable ; une réponse du serveur, même un refus, est rendue. */
    @Throws(IOException::class)
    fun send(method: String, path: String, body: String?): HttpLite.Response
}

sealed class WalletResult<out T> {
    data class Ok<T>(val value: T) : WalletResult<T>()
    /** [network] : injoignable (rien n'est connu de l'opération : on peut la renvoyer avec la MÊME clé) ; sinon le serveur a répondu [status] avec le motif [reason]. */
    data class Fail(val shown: WalletMessages.Shown, val status: Int?, val reason: String?, val network: Boolean) : WalletResult<Nothing>()
}

/** Ce que la TV annonce d'elle-même : son code d'appareil, l'identifiant public de son appareil API, ses activations `cbx1` et sa clé d'installation (preuve de possession). */
class WalletIdentity(val deviceCode: String, val apiDeviceId: String?, val activations: List<String>, val signer: InstallSigner?)

/**
 * Le client HTTPS du portefeuille (routes de `backend/.../wallet`) : `sync`, `policy`, `history`, `convert`, `transfer`, `receive-code`. Il ne calcule et ne garde AUCUN solde : chaque `cbw1`
 * reçu est vérifié par le cache signé ([WalletCache]) et seul le cache le garde. Aucun montant à créditer n'est jamais envoyé (la TV ne crée pas de jeton).
 *
 * Idempotence : une coupure réseau renvoie la MÊME requête (même clé `idem`) jusqu'à [maxAttempts] fois ; une réponse du serveur, même un refus, n'est jamais renvoyée.
 * Preuve de possession : `sync` joint `bind` (voir [WalletBind]) ; si le serveur la refuse (`BIND_PROOF`) et donne son heure (en-tête `Date`), elle est refaite UNE fois à l'heure du serveur.
 */
class WalletClient(private val transport: WalletTransport, private val identity: () -> WalletIdentity?, private val cache: WalletCache, private val nowMs: () -> Long, private val maxAttempts: Int = 3) {
    private sealed class Raw {
        class Answer(val resp: HttpLite.Response) : Raw()
        object Offline : Raw()
    }

    private fun exchange(method: String, path: String, body: String?): Raw {
        repeat(maxAttempts) {
            try { return Raw.Answer(transport.send(method, path, body)) } catch (e: IOException) { /* coupure : on renvoie la même requête */ }
        }
        return Raw.Offline
    }

    private fun noIdentity(): WalletResult.Fail = WalletResult.Fail(WalletMessages.of(409, "ACTIVATE"), null, "ACTIVATE", false)

    /** Réponse du serveur -> résultat : 2xx lu par [parse] (illisible : « le serveur ne répond pas correctement »), sinon refus avec son motif fermé. */
    private fun <T> result(raw: Raw, available: Long? = null, cur: WalletCurrency = WalletCurrency.NDEM, parse: (String) -> T?): WalletResult<T> {
        if (raw !is Raw.Answer) return WalletResult.Fail(WalletMessages.offline(), null, null, true)
        val r = raw.resp
        if (r.code in 200..299) return parse(r.body)?.let { WalletResult.Ok(it) } ?: WalletResult.Fail(WalletMessages.of(500, null), r.code, null, false)
        val f = WalletReplies.parseFailure(r.code, r.body)
        return WalletResult.Fail(WalletMessages.of(r.code, f.reason, f.message, available, cur), r.code, f.reason, false)
    }

    private fun code(id: WalletIdentity) = DeviceCode.parse(id.deviceCode) ?: id.deviceCode

    private fun available(cur: WalletCurrency): Long? = cache.snapshot?.let { if (cur == WalletCurrency.NDEM) it.n else it.m }

    // ---- sync ----

    fun sync(): WalletResult<SyncData> {
        val id = identity() ?: return noIdentity()
        val code = code(id)
        var at = nowMs()
        var retried = false
        while (true) {
            val bind = if (id.signer != null && id.apiDeviceId != null) WalletBind.proof(id.signer, code, id.apiDeviceId, at) else null
            val raw = exchange("POST", "$BASE/sync", JsonLite.write(linkedMapOf("deviceCode" to code, "activations" to id.activations, "bind" to bind)))
            if (!retried && raw is Raw.Answer && raw.resp.code == 409 && WalletReplies.parseFailure(409, raw.resp.body).reason == "BIND_PROOF") {
                val server = serverTime(raw.resp)
                if (server != null) { at = server; retried = true; continue }
            }
            val r = result(raw) { WalletReplies.parseSync(it) }
            if (r !is WalletResult.Ok) return r
            val refused = offer(r.value.snapshotToken)
            return refused ?: r
        }
    }

    /** L'heure du serveur (en-tête `Date`, à la seconde), ou null. */
    private fun serverTime(r: HttpLite.Response): Long? =
        r.header("Date")?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }

    /** Donne l'instantané au cache signé. Refusé (autre TV, clé inconnue, altéré) : l'échange échoue, rien n'est gardé. */
    private fun offer(token: String?): WalletResult.Fail? {
        if (token == null) return null
        val o = cache.offerSnapshot(token)
        if (o !is WalletCache.SnapshotOffer.Refused) return null
        val text = when (o.reason) {
            WalletRefusal.OTHER_TV -> "Réponse du serveur refusée : instantané d'une autre TV (code OTHER_TV)"
            WalletRefusal.UNKNOWN_KEY -> "Cette TV ne connaît pas la clé du portefeuille : mettez la TV à jour (code UNKNOWN_KEY)"
            else -> "Réponse du serveur refusée : instantané illisible (code ${o.reason.name})"
        }
        return WalletResult.Fail(WalletMessages.Shown(text, o.reason.name, WalletMessages.Kind.REFUSED), 200, null, false)
    }

    // ---- lectures ----

    fun policy(): WalletResult<PolicyView> {
        if (identity() == null) return noIdentity()
        return result(exchange("GET", "$BASE/policy", null)) { WalletReplies.parsePolicy(it) }
    }

    fun history(before: Long?): WalletResult<HistoryPage> {
        if (identity() == null) return noIdentity()
        return result(exchange("GET", "$BASE/history" + (before?.let { "?before=$it" } ?: ""), null)) { WalletReplies.parseHistory(it) }
    }

    // ---- opérations qui bougent des jetons ----

    /** [q] : nombre de MBOKO (le serveur calcule les NDEM, le taux et les frais). [idem] : clé générée UNE fois par l'opération, rejouée telle quelle. */
    fun convert(dir: ConvertDir, q: Long, idem: String): WalletResult<ConvertDone> {
        val id = identity() ?: return noIdentity()
        val cur = if (dir == ConvertDir.N2M) WalletCurrency.NDEM else WalletCurrency.MBOKO      // la monnaie payée
        val raw = exchange("POST", "$BASE/convert", JsonLite.write(linkedMapOf("deviceCode" to code(id), "dir" to dir.wire, "q" to q, "idem" to idem)))
        val r = result(raw, available(cur), cur) { WalletReplies.parseConvert(it) }
        if (r is WalletResult.Ok) offer(r.value.snapshotToken)           // l'opération est faite : un instantané refusé se corrigera à la prochaine synchronisation
        return r
    }

    fun transfer(cur: WalletCurrency, code: String, amt: Long, idem: String): WalletResult<TransferDone> {
        val id = identity() ?: return noIdentity()
        val raw = exchange("POST", "$BASE/transfer", JsonLite.write(linkedMapOf("deviceCode" to code(id), "cur" to cur.name, "amt" to amt, "code" to code, "idem" to idem)))
        val r = result(raw, available(cur), cur) { WalletReplies.parseTransfer(it) }
        if (r is WalletResult.Ok) offer(r.value.snapshotToken)
        return r
    }

    fun receiveCode(): WalletResult<ReceiveCodeView> {
        val id = identity() ?: return noIdentity()
        return result(exchange("POST", "$BASE/receive-code", JsonLite.write(linkedMapOf("deviceCode" to code(id))))) { WalletReplies.parseReceive(it) }
    }

    companion object { const val BASE = "/api/v1/wallet" }
}
