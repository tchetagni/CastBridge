package castbridge.play.entitlement

import castbridge.core.owner.Envelope
import castbridge.core.owner.KeyRing
import castbridge.core.owner.RevocationNotice
import castbridge.core.owner.RevocationState
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * La liste signée des révocations (`GET /api/v1/revocations`, publique, `cbx1`) relue toutes les [refreshMs] (15 min). Elle n'est crue que si elle est signée par une clé
 * de confiance qui a la portée REVOKE et si elle n'est pas plus ANCIENNE que la dernière acceptée. Échec (réseau, signature, vieille liste) : la DERNIÈRE liste valide
 * continue de s'appliquer ; passé [STALE_MS] sans rafraîchissement, [staleNotice] dit « Révocations non rafraîchies » (signe orange du service). Le service n'appelle
 * l'API qu'en lecture publique, sans privilège (T-10).
 */
class RevocationsFeed(private val ring: KeyRing, private val fetch: () -> String?, private val clock: () -> Long = System::currentTimeMillis, val refreshMs: Long = 15 * 60_000L, private val file: java.io.File? = null) {
    @Volatile private var state = RevocationState()
    @Volatile private var lastOkMs = 0L
    @Volatile private var lastIssuedAt = 0L
    private val started = clock()

    init { loadFile() }

    /** Relit la dernière liste valide du volume : signature et clé de confiance revérifiées, `issuedAt` repris comme plancher (une liste plus ancienne sera refusée). */
    private fun loadFile() {
        val f = file ?: return
        val text = runCatching { if (f.isFile && f.length() <= MAX_BYTES) f.readText(Charsets.UTF_8).trim() else null }.getOrNull() ?: return
        val env = Envelope.decode(text) ?: return
        val st = RevocationNotice.verify(text, ring) ?: return
        state = st; lastIssuedAt = env.issuedAt
        lastOkMs = minOf(clock(), f.lastModified()).coerceAtLeast(1L)   // date de la dernière liste prise, jamais dans le futur
    }

    /** Écriture atomique (fichier temporaire du même dossier puis renommage) : un arrêt en plein écrit ne laisse jamais une liste coupée. */
    private fun persist(text: String) {
        val f = file ?: return
        runCatching {
            f.absoluteFile.parentFile?.mkdirs()
            val tmp = java.io.File(f.absoluteFile.parentFile, f.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            try { java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            catch (e: java.nio.file.AtomicMoveNotSupportedException) { java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    fun current(): RevocationState = state

    /** Un rafraîchissement ; vrai si une liste valide, au moins aussi récente que la précédente, a été prise. Ne lève jamais. */
    fun refresh(): Boolean {
        val text = runCatching { fetch() }.getOrNull()?.trim() ?: return false
        if (text.isEmpty() || text.length > MAX_BYTES) return false
        val env = Envelope.decode(text) ?: return false
        val st = RevocationNotice.verify(text, ring) ?: return false
        if (env.issuedAt < lastIssuedAt) return false
        state = st; lastIssuedAt = env.issuedAt; lastOkMs = clock()
        persist(text)
        return true
    }

    fun lastRefreshMs(): Long = lastOkMs

    /** Le service peut juger des droits : une liste valide a été prise (ou relue du volume) il y a moins de [MAX_AGE_MS]. */
    fun usable(): Boolean = lastOkMs > 0 && clock() - lastOkMs <= MAX_AGE_MS

    /** Texte du signe orange quand la liste est vieille (ou jamais reçue depuis plus d'une heure de service) ; null = à jour. */
    fun staleNotice(): String? {
        val ref = if (lastOkMs > 0) lastOkMs else started
        return if (clock() - ref > STALE_MS) "Révocations non rafraîchies" else null
    }

    companion object {
        const val STALE_MS = 60 * 60_000L
        const val MAX_BYTES = 256 * 1024
        /** Au-delà, une liste n'est plus crue : le service refuse d'ouvrir des salles (fermé). */
        const val MAX_AGE_MS = 24 * 3_600_000L

        /** Lecteur HTTP(S) du service, borné : 10 s, corps ≤ [MAX_BYTES], aucune redirection suivie. Jamais appelé dans les tests (lecteur injecté). */
        fun httpFetcher(url: String): () -> String? {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
            val uri = URI.create(url)
            require(uri.scheme == "https" || uri.host == "127.0.0.1" || uri.host == "localhost") { "révocations : https obligatoire" }
            return {
                val r = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Accept", "text/plain").GET().build(), HttpResponse.BodyHandlers.ofInputStream())
                r.body().use { body ->
                    // lecture BORNÉE : jamais plus de MAX_BYTES + 1 octets en mémoire, quelle que soit la taille annoncée
                    val bytes = body.readNBytes(MAX_BYTES + 1)
                    if (r.statusCode() == 200 && bytes.size <= MAX_BYTES) String(bytes, Charsets.UTF_8) else null
                }
            }
        }
    }
}
