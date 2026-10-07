package castbridge.play.stake

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Le dépôt des résultats `cbr1` dans le volume d'état du service (`CASTBRIDGE_PLAY_RESULTS_DIR`, `/var/lib/castbridge-play/results`) : un fichier `<rid>.cbr1` par résultat (le nom est l'identifiant
 * de résultat : le collecteur de l'hôte, `tools/wallet/collect-results.sh`, dédoublonne par nom), écrit de façon ATOMIQUE (fichier temporaire puis renommage), au plus [maxFiles] fichiers et
 * [maxAgeMs] de vie (7 jours). C'est la voie de secours : la voie rapide est la TV gagnante qui poste le résultat elle-même ; le collecteur attrape les TV hors ligne. Sans dossier configuré, le
 * dépôt est inactif ([write] rend faux) et le service continue (les TV portent le résultat).
 *
 * Le contenu est le jeton `cbr1` seul (≈ 700 octets) : signé, donc sans secret ; le collecteur ne garde que des fichiers ordinaires de moins de 9 Ko.
 */
class ResultSpool(private val dir: File?, private val maxFiles: Int = 10_000, private val maxAgeMs: Long = 7L * 24 * 3_600_000, private val clock: () -> Long = System::currentTimeMillis) {
    private val rid = Regex("^[0-9a-f]{32}$")
    @Volatile private var lastPurge = 0L

    val active: Boolean get() = dir != null

    /** Dépose [token] sous `<rid>.cbr1` ; faux si le dépôt est inactif, si [resultId] n'est pas 32 hexadécimaux ou si l'écriture échoue (jamais d'exception : la partie ne dépend pas du disque). */
    fun write(resultId: String, token: String): Boolean {
        val d = dir ?: return false
        if (!rid.matches(resultId) || token.length > MAX_TOKEN || token.any { it.code !in 33..126 }) return false
        return runCatching {
            Files.createDirectories(d.toPath())
            val target = File(d, "$resultId.cbr1").toPath()
            val tmp = Files.createTempFile(d.toPath(), ".tmp-", ".part")
            try {
                Files.write(tmp, (token + "\n").toByteArray(Charsets.US_ASCII))
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { runCatching { Files.deleteIfExists(tmp) } }
            purgeIfDue()
            true
        }.getOrDefault(false)
    }

    /** Les identifiants de résultat présents (tests, santé). */
    fun list(): List<String> = dir?.listFiles { f -> f.isFile && f.name.endsWith(".cbr1") }?.map { it.name.removeSuffix(".cbr1") }?.sorted().orEmpty()

    fun read(resultId: String): String? = dir?.let { File(it, "$resultId.cbr1") }?.takeIf { rid.matches(resultId) && it.isFile }?.let { runCatching { it.readText(Charsets.US_ASCII).trim() }.getOrNull() }

    /** Retire les fichiers de plus de [maxAgeMs], puis les plus anciens au-delà de [maxFiles] ; au plus une fois par minute. */
    fun purgeIfDue() {
        val now = clock()
        if (now - lastPurge < 60_000L) return
        lastPurge = now
        purge(now)
    }

    internal fun purge(now: Long = clock()) {
        val files = dir?.listFiles { f -> f.isFile && (f.name.endsWith(".cbr1") || f.name.startsWith(".tmp-")) }?.sortedBy { it.lastModified() } ?: return
        var left = files.size
        for (f in files) {
            val tooOld = now - f.lastModified() > maxAgeMs
            val tooMany = left > maxFiles
            if (tooOld || tooMany) { if (runCatching { f.delete() }.getOrDefault(false)) left-- }
        }
    }

    companion object { const val MAX_TOKEN = 6_000 }
}
