package castbridge.core.library.agent

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.str
import java.io.IOException

/** The optional "model" for the ambiguous names. Off by default; the rules engine works without it and without any network. */
interface NamingModel {
    val id: String
    /** True when the requests leave the phone (server model): the UI says so, and the consent is separate and explicit. */
    val remote: Boolean
    @Throws(ModelUnavailable::class)
    fun suggest(req: SuggestRequest): SuggestResponse
}

class ModelUnavailable(message: String) : IOException(message)

/**
 * What would be sent: cleaned names and minimal metadata. NEVER the content of a file, a path or folder, a size or date,
 * the name of a contact, or any device identifier (the server authenticates the request with the device token, in a header).
 * [i] is only the position in this request.
 */
data class SuggestItem(val i: Int, val text: String, val ext: String, val kindHint: String?, val durationMin: Int?)

data class SuggestRequest(val lang: String, val items: List<SuggestItem>) {
    fun toJson(): String = JsonLite.write(linkedMapOf(
        "consent" to AiConsent.TAG, "lang" to lang,
        "items" to items.map { linkedMapOf("i" to it.i, "t" to it.text, "x" to it.ext, "k" to it.kindHint, "d" to it.durationMin) }))

    /** The exact text shown to the user before anything is sent ("Voir ce qui serait envoyé"). */
    fun preview(): String = items.joinToString("\n") { "• ${it.text}.${it.ext}" + (it.durationMin?.let { d -> " (${d} min)" } ?: "") }
}

data class Suggestion(
    val i: Int,
    val kind: Kind,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val artist: String? = null,
    val confidence: Double = 0.5,
)

/** [costUsd] is the server's estimate for this request (null: unknown or local); [promptVersion] and [rejected] (answers the server refused) are for the history screen. */
data class SuggestResponse(val model: String, val available: Boolean, val suggestions: List<Suggestion>, val costUsd: Double? = null, val promptVersion: String = "", val rejected: Int = 0)

/**
 * The wording of the separate consent for the AI layer: what leaves the phone, in plain French. FOR REVIEW by Esaie / a lawyer (docs/LIBRARY-AGENT.md § 12)
 * before the AI layer is switched on. Changing any word raises [VERSION], which asks every user again.
 */
object AiConsent {
    const val TAG = "library-ai-v1"
    const val VERSION = "2026-10-b"
    const val TITLE = "Aide de l'intelligence artificielle (facultatif)"
    const val WHAT_LEAVES = "Pour les seuls fichiers que les règles de l'application ne comprennent pas, l'assistant peut envoyer des informations au serveur CastBridge, " +
        "qui les transmet à un service d'intelligence artificielle pour proposer un titre, une saison, un épisode. Voici la liste complète de ce qui part, pour chaque fichier concerné :"
    /** One line per piece of information sent. Nothing else leaves the phone. */
    val LEAVES_LIST = listOf(
        "le nom du fichier, déjà nettoyé : sans lien Internet, sans adresse e-mail et sans suite de 6 chiffres ou plus (par exemple « prison break s01e04 »), 120 caractères au plus ;",
        "son extension (mkv, mp4, mp3…) ;",
        "un type supposé par l'application (série, film, musique, clip, cours) quand elle en a un ;",
        "sa durée, arrondie à 5 minutes, quand elle est connue ;",
        "son numéro dans la liste envoyée (0, 1, 2…), sans autre sens.",
    )
    const val LEAVES_ONCE = "Une fois par envoi : la langue de l'application (français ou anglais) et la mention de votre accord (« library-ai-v1 »). " +
        "Comme toute connexion Internet, l'envoi laisse aussi au serveur l'adresse IP du téléphone et l'heure, et le serveur reconnaît l'appareil par son jeton (déjà utilisé pour les mises à jour) afin de refuser les appareils bloqués et de limiter le nombre de demandes. Au plus 100 noms par analyse."
    const val THIRD_PARTY = "Le service d'intelligence artificielle est un fournisseur tiers choisi par l'administrateur de CastBridge. CastBridge n'enregistre ni ne journalise les noms envoyés ; " +
        "le fournisseur peut appliquer ses propres règles de conservation, indiquées dans la documentation de mise en service."
    val NEVER_LIST = listOf(
        "le contenu de vos fichiers ;", "leurs dossiers, chemins, tailles et dates ;", "vos vidéos et photos personnelles (WhatsApp, appareil photo, captures) et vos documents ;",
        "tout ce que le contrôle parental protège, et rien du tout quand un profil enfant est actif ;", "vos corrections et vos habitudes de visionnage ;", "votre nom, vos contacts, votre numéro, un identifiant publicitaire.",
    )
    const val WHAT_STAYS = "Ne quitte jamais le téléphone : " // completed by NEVER_LIST in [paragraphs]
    const val CHOICE = "Vous pouvez l'activer et le désactiver quand vous voulez, et voir la liste exacte de ce qui serait envoyé avant chaque analyse. " +
        "Désactivé, l'assistant fonctionne entièrement sur le téléphone, sans réseau."
    val paragraphs get() = listOf(WHAT_LEAVES, LEAVES_LIST.joinToString("\n") { "• $it" }, LEAVES_ONCE, THIRD_PARTY, WHAT_STAYS + "\n" + NEVER_LIST.joinToString("\n") { "• $it" }, CHOICE)
    /** The whole text as one string (for the review and for a test that no wording is dropped). */
    fun fullText(): String = paragraphs.joinToString("\n\n")
}

/** The local "model": the rules engine behind the same interface. Nothing leaves the phone. */
class LocalRulesModel(private val currentYear: Int = java.time.LocalDate.now().year) : NamingModel {
    override val id = "local-rules"
    override val remote = false
    override fun suggest(req: SuggestRequest): SuggestResponse {
        val out = req.items.mapNotNull { it ->
            val p = NameParser.parse(it.text + "." + it.ext, durationMs = (it.durationMin ?: 0) * 60_000L, currentYear = currentYear)
            if (p.kind == Kind.UNKNOWN || p.title.isBlank()) null
            else Suggestion(it.i, p.kind, p.title, p.year, p.season, p.episode, p.episodeTitle, p.artist, p.confidence)
        }
        return SuggestResponse(id, true, out)
    }
}

/** The model on the CastBridge server (POST /api/v1/library/suggest, device token, rate limited). Only used after the separate consent. */
class ServerNamingModel(
    baseUrl: String,
    private val token: () -> String?,
    private val http: HttpLite = HttpLite(userAgent = "CastBridge-library-agent"),
    private val currentYear: Int = java.time.LocalDate.now().year,
) : NamingModel {
    override val id = "server"
    override val remote = true
    private val base = baseUrl.trimEnd('/')

    override fun suggest(req: SuggestRequest): SuggestResponse {
        val t = token() ?: throw ModelUnavailable("Appareil non enregistré auprès du serveur : ouvrez l'application quelques instants avec Internet.")
        val r = try {
            http.request("POST", "$base/api/v1/library/suggest", req.toJson(), mapOf("Authorization" to "Bearer $t"))
        } catch (e: IOException) { throw ModelUnavailable("Serveur injoignable : ${e.message}") }
        when (r.code) {
            200 -> {}
            401 -> throw ModelUnavailable("Le serveur ne reconnaît pas cet appareil.")
            429 -> throw ModelUnavailable("Trop de demandes : réessayez plus tard.")
            else -> throw ModelUnavailable("Le serveur répond ${r.code}.")
        }
        return parse(r.body, req.items.size)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parse(body: String, n: Int): SuggestResponse {
        val m = try { JsonLite.obj(body) } catch (e: Exception) { throw ModelUnavailable("Réponse illisible du serveur") }
        val list = (m["suggestions"] as? List<Any?>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.let { o -> AiApply.sanitize(o, n, currentYear) } }
        val usage = m["usage"] as? Map<String, Any?>
        return SuggestResponse(m.str("model") ?: "server", m["available"] as? Boolean ?: true, list, (usage?.get("estimatedCostUsd") as? Number)?.toDouble(), m.str("promptVersion").orEmpty(), m.int("rejected") ?: 0)
    }
}

/** Chooses what (if anything) is worth asking a model, and applies the answers with suspicion: an answer is a proposal, never a fact. */
object AiApply {
    const val MAX_ITEMS = 100
    const val BATCH = 40
    private val ALLOWED = mapOf("series" to Kind.SERIES, "movie" to Kind.MOVIE, "music" to Kind.MUSIC, "clip" to Kind.CLIP, "course" to Kind.COURSE)
    private val RX_URL = Regex("(?i)(?:https?://|www\\.)\\S+|\\S+@\\S+\\.\\S+")
    private val RX_LONG_DIGITS = Regex("\\d{6,}")

    /** The text sent for one file: its cleaned title, without links, e-mail addresses or long numbers (phone numbers, ids). */
    fun safeText(p: Parsed): String? {
        val t = (p.stem.ifBlank { p.title }).replace(RX_URL, " ").replace(RX_LONG_DIGITS, " ").replace(Regex("[^\\p{L}\\p{N} '&,!.()\\-–]"), " ").replace(Regex("\\s{2,}"), " ").trim().take(120)
        return t.takeIf { it.length >= 3 && it.any { c -> c.isLetter() } }
    }

    /** Ambiguous names only: video / audio the rules could not classify with confidence. Never personal media, photos, documents, protected files. */
    fun select(items: List<ItemInfo>, guard: ContentGuard, max: Int = MAX_ITEMS): List<Pair<ItemInfo, SuggestItem>> {
        val out = ArrayList<Pair<ItemInfo, SuggestItem>>()
        for (it in items) {
            if (out.size >= max) break
            val p = it.parsed
            if (p.media != Media.VIDEO && p.media != Media.AUDIO) continue
            if (p.kind == Kind.PERSONAL || p.kind == Kind.PHOTO) continue
            if (p.kind != Kind.UNKNOWN && p.confidence >= 0.6) continue
            if (guard.isProtected(it.file) || guard.childProfileActive) continue
            val text = safeText(p) ?: continue
            out += it to SuggestItem(out.size, text, p.ext, p.kind.takeIf { k -> k != Kind.UNKNOWN }?.name?.lowercase(), (it.file.durationMs / 60_000L / 5 * 5).toInt().takeIf { d -> d > 0 })
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun sanitize(o: Map<String, Any?>, n: Int, currentYear: Int): Suggestion? {
        val i = o.int("i") ?: return null
        if (i !in 0 until n) return null
        val kind = ALLOWED[o.str("kind")?.lowercase()] ?: return null
        val title = SafeName.clean(o.str("title").orEmpty(), 100).takeIf { it.length >= 2 } ?: return null
        val year = o.int("year")?.takeIf { it in 1900..currentYear + 1 }
        val season = o.int("season")?.takeIf { it in 0..99 }
        val episode = o.int("episode")?.takeIf { it in 0..9999 }
        val ep = o.str("episodeTitle")?.let { SafeName.clean(it, 100) }?.takeIf { it.length >= 2 }
        val artist = o.str("artist")?.let { SafeName.clean(it, 80) }?.takeIf { it.length >= 2 }
        val conf = ((o["confidence"] as? Number)?.toDouble() ?: 0.5).coerceIn(0.0, 1.0)
        return Suggestion(i, kind, title, year, season, episode, ep, artist, conf)
    }

    /** Turns a suggestion into a [Parsed] of modest confidence (never above 0.6: an AI answer is never ticked by default). */
    fun apply(p: Parsed, s: Suggestion): Parsed = p.copy(
        kind = s.kind, title = Text.titleCaseIfNeeded(s.title, p.nameLang), year = s.year ?: p.year, season = s.season, episode = s.episode,
        episodeTitle = s.episodeTitle, artist = s.artist, confidence = minOf(s.confidence, 0.6).coerceAtLeast(0.5), rule = "ai", hadJunk = true,
        stem = s.title, subject = p.subject,
    )
}
