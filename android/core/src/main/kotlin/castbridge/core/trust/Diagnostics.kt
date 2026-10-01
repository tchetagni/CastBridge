package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloInfo
import castbridge.core.tv.Link
import castbridge.core.tv.LinkPlanner

/** What the « Diagnostic Bluetooth » screen needs from the phone. */
interface DiagEnv {
    fun btProblem(): BtUnavailable.Reason?
    fun bond(address: String): BondState
    /** Does the paired device list the CastBridge service (SDP)? null = not known yet. */
    fun hasCbt1Service(address: String): Boolean?
    /** Opens the secure RFCOMM link; throws [BtUnavailable] or IOException. */
    fun connect(address: String): Link
    fun probe(base: String): Boolean
    fun canJoinWifiDirect(): Boolean
    fun now(): Long
    fun appVersion(): String
}

data class DiagStep(val id: String, val label: String, val status: Status, val detail: String, val next: Advice? = null) {
    enum class Status(val mark: String) { OK("OK"), KO("KO"), SKIPPED("--"), INFO("i") }
}

class DiagReport(val steps: List<DiagStep>, val createdAt: Long, val appVersion: String) {
    val firstFailure get() = steps.firstOrNull { it.status == DiagStep.Status.KO }
    val ok get() = firstFailure == null

    /** Plain text to copy and send: contains no token, no PIN, no key, no full Bluetooth address (see [Redact]). */
    fun text(): String = Redact.scrub(buildString {
        append("CastBridge — Diagnostic Bluetooth\n")
        append("Application ").append(appVersion).append(" · ").append(java.time.Instant.ofEpochMilli(createdAt).toString()).append("\n\n")
        steps.forEach { s ->
            append('[').append(s.status.mark).append("] ").append(s.label)
            if (s.detail.isNotEmpty()) append(" : ").append(s.detail)
            append('\n')
            if (s.status == DiagStep.Status.KO && s.next != null) append("     → ").append(s.next.title).append(" — ").append(s.next.detail).append('\n')
        }
        append('\n').append(if (ok) "Tout est en ordre." else "Premier problème : " + firstFailure!!.label + ".").append('\n')
    })
}

/** Keeps secrets and identifiers out of anything that can be copied or logged. */
object Redact {
    private val TOKEN = Regex("cbk_[0-9a-fA-F]{8,}")
    private val LONG_HEX = Regex("\\b[0-9a-fA-F]{32,}\\b")
    private val SIX_DIGITS = Regex("(?<![0-9A-Za-z.:])[0-9]{6}(?![0-9A-Za-z])")
    private val ADDRESS = Regex("\\b(?:[0-9A-Fa-f]{2}:){4}([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})\\b")
    fun scrub(s: String) = s.replace(TOKEN, "cbk_••••").replace(LONG_HEX, "••••").replace(SIX_DIGITS, TvCredential.REDACTED).replace(ADDRESS) { "XX:XX:XX:XX:${it.groupValues[1]}" }
    fun address(a: String) = scrub(a)
    fun id(id: String?) = id?.let { "…" + it.takeLast(4) } ?: "inconnu"
}

/**
 * Runs every step of the link, one after the other, and keeps going after a failure so the report shows everything:
 * Bluetooth on, permission, TV saved, bonded, TV address, SDP service, RFCOMM connect, HELLO answer, route chosen, token validity, TV version and install id.
 * Each failed step carries the exact next action ([Advice]). Pure over [DiagEnv]; the HELLO it sends never asks for trust.
 */
class Diagnostics(private val env: DiagEnv, private val credential: (String) -> StoredCredential?) {
    fun run(tv: SavedTv?, onStep: (DiagStep) -> Unit = {}): DiagReport {
        val steps = ArrayList<DiagStep>()
        fun add(s: DiagStep) { steps += s; onStep(s) }
        fun ok(id: String, label: String, detail: String = "") = add(DiagStep(id, label, DiagStep.Status.OK, detail))
        fun ko(id: String, label: String, detail: String, next: Advice) = add(DiagStep(id, label, DiagStep.Status.KO, detail, next))
        fun skip(id: String, label: String, why: String) = add(DiagStep(id, label, DiagStep.Status.SKIPPED, why))
        fun info(id: String, label: String, detail: String) = add(DiagStep(id, label, DiagStep.Status.INFO, detail))

        val problem = env.btProblem()
        val btOk = problem == null
        when (problem) {
            null -> { ok("bluetooth", "Bluetooth du téléphone allumé"); ok("permission", "Autorisation « Appareils à proximité »") }
            BtUnavailable.Reason.OFF -> { ko("bluetooth", "Bluetooth du téléphone allumé", "éteint", LinkText.bluetooth(problem)); skip("permission", "Autorisation « Appareils à proximité »", "Bluetooth éteint") }
            BtUnavailable.Reason.NO_PERMISSION -> { ok("bluetooth", "Bluetooth du téléphone allumé"); ko("permission", "Autorisation « Appareils à proximité »", "refusée", LinkText.bluetooth(problem)) }
            BtUnavailable.Reason.NO_ADAPTER -> { ko("bluetooth", "Bluetooth du téléphone allumé", "ce téléphone n'a pas de Bluetooth", LinkText.bluetooth(problem)); skip("permission", "Autorisation « Appareils à proximité »", "pas de Bluetooth") }
        }
        if (tv == null) {
            ko("tv", "TV enregistrée", "aucune", LinkText.noTv)
            for ((i, l) in listOf("bond" to "Association Bluetooth", "address" to "Adresse de la TV", "sdp" to "Service CastBridge-TV", "rfcomm" to "Connexion Bluetooth (RFCOMM)", "hello" to "Réponse de la TV (HELLO)", "route" to "Route choisie", "token" to "Autorisation du téléphone (jeton)", "version" to "Version et identité de la TV"))
                skip(i, l, "aucune TV")
            return DiagReport(steps, env.now(), env.appVersion())
        }
        ok("tv", "TV enregistrée", tv.name)
        info("address", "Adresse de la TV", Redact.address(tv.address))
        val bond = env.bond(tv.address)
        when (bond) {
            BondState.BONDED -> ok("bond", "Association Bluetooth", "associée")
            BondState.BONDING -> ko("bond", "Association Bluetooth", "en cours", LinkText.bonding)
            BondState.NONE -> ko("bond", "Association Bluetooth", "non associée", LinkText.notBonded)
        }
        if (!btOk || bond != BondState.BONDED) {
            for ((i, l) in listOf("sdp" to "Service CastBridge-TV", "rfcomm" to "Connexion Bluetooth (RFCOMM)", "hello" to "Réponse de la TV (HELLO)", "route" to "Route choisie"))
                skip(i, l, if (!btOk) "Bluetooth indisponible" else "TV non associée")
            tokenStep(tv, ::add); versionStep(tv, null, ::add)
            return DiagReport(steps, env.now(), env.appVersion())
        }
        when (env.hasCbt1Service(tv.address)) {
            true -> ok("sdp", "Service CastBridge-TV", "annoncé par la TV")
            false -> ko("sdp", "Service CastBridge-TV", "absent", LinkText.absent(AbsentKind.SERVICE_ABSENT, tv.name))
            null -> info("sdp", "Service CastBridge-TV", "pas encore vérifié par Android")
        }
        val started = env.now()
        var info: HelloInfo? = null
        var link: Link? = null
        try { link = env.connect(tv.address); ok("rfcomm", "Connexion Bluetooth (RFCOMM)", "établie en ${env.now() - started} ms") }
        catch (e: BtUnavailable) { ko("rfcomm", "Connexion Bluetooth (RFCOMM)", "Bluetooth indisponible", LinkText.bluetooth(e.reason)) }
        catch (e: Exception) {
            val kind = AbsentKind.classify(e.message, env.now() - started)
            ko("rfcomm", "Connexion Bluetooth (RFCOMM)", "refusée ou sans réponse", if (kind == AbsentKind.CLOSED_AT_ONCE) LinkText.staleBond else LinkText.absent(kind, tv.name))
        }
        if (link == null) skip("hello", "Réponse de la TV (HELLO)", "pas de liaison")
        else try {
            link.use { l -> info = BtProtocol.hello(l.input, l.output, requestTrust = false, installId = tv.installId) }
            ok("hello", "Réponse de la TV (HELLO)", "acceptée")
        } catch (e: BtProtocol.Refused) {
            val a = LinkText.refused(e.code, e.hint)
            ko("hello", "Réponse de la TV (HELLO)", "refus ${e.code} (${BtProtocol.describe(e.code)})", a)
        } catch (e: Exception) { ko("hello", "Réponse de la TV (HELLO)", "liaison coupée pendant l'échange", LinkText.absent(AbsentKind.NO_ANSWER, tv.name)) }
        val hello = info
        if (hello == null) skip("route", "Route choisie", "pas de réponse de la TV")
        else {
            val r = LinkPlanner.plan(hello.link, env::probe, env.canJoinWifiDirect()).first()
            if (r is LinkPlanner.Route.Bluetooth && hello.link.ips.isNotEmpty())
                info("route", "Route choisie", "Bluetooth seulement (le Wi-Fi de la TV n'est pas joignable depuis ce téléphone)")
            else ok("route", "Route choisie", r.label)
        }
        tokenStep(tv, ::add); versionStep(tv, hello, ::add)
        return DiagReport(steps, env.now(), env.appVersion())
    }

    private fun tokenStep(tv: SavedTv, add: (DiagStep) -> Unit) {
        val c = credential(tv.address)
        val now = env.now()
        add(when {
            c == null -> DiagStep("token", "Autorisation du téléphone (jeton)", DiagStep.Status.INFO, "aucun jeton : il sera demandé à la prochaine connexion")
            c.expiresAt <= now -> DiagStep("token", "Autorisation du téléphone (jeton)", DiagStep.Status.INFO, "expiré : il sera renouvelé à la prochaine connexion")
            else -> DiagStep("token", "Autorisation du téléphone (jeton)", DiagStep.Status.OK, "valide encore ${((c.expiresAt - now) / 60_000).coerceAtLeast(1)} min")
        })
    }

    private fun versionStep(tv: SavedTv, hello: HelloInfo?, add: (DiagStep) -> Unit) {
        if (hello == null) { add(DiagStep("version", "Version et identité de la TV", DiagStep.Status.INFO, "version inconnue, identité enregistrée : ${Redact.id(tv.installId)}")); return }
        val same = tv.installId == null || hello.installId == null || tv.installId == hello.installId
        add(if (same) DiagStep("version", "Version et identité de la TV", DiagStep.Status.OK, "CastBridge-TV ${hello.version}, identité ${Redact.id(hello.installId)}")
        else DiagStep("version", "Version et identité de la TV", DiagStep.Status.KO, "identité différente (${Redact.id(tv.installId)} → ${Redact.id(hello.installId)})",
            LinkText.untrustedAdvice(BtProtocol.HINT_OTHER_INSTALL)))
    }
}
