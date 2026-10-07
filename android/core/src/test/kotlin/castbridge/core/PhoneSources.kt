package castbridge.core

import java.io.File
import kotlin.test.assertTrue

/**
 * Lecture des SOURCES du téléphone depuis les tests du cœur (les tests JVM ne chargent ni Android ni les manifestes) : les gardes de source de fix-phone (audit anti-régression
 * 2026-10-07 b : B2, I-8 à I-15) ne passent jamais « au vert à vide » : un fichier introuvable est un échec, pas une absence de défaut.
 * Les tests tournent dans `android/core` : les autres modules sont au même niveau (`../sender`).
 */
object PhoneSources {
    /** Un fichier du dépôt Android (`android/<rel>`), obligatoire. */
    fun file(rel: String): File = File("../$rel").also { assertTrue(it.isFile, "source introuvable depuis ${File(".").absolutePath} : $rel") }

    fun text(rel: String): String = file(rel).readText()

    /** Le code d'un fichier source Kotlin : commentaires retirés (les lignes sont gardées : les numéros restent ceux du fichier), chaînes laissées telles quelles. */
    fun code(rel: String): String = stripComments(text(rel))

    /** Tous les fichiers Kotlin d'un module (`sender`, `receiver`…), sans les dossiers de construction. */
    fun kotlinFiles(module: String): List<File> =
        File("../$module/src/main").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && it.extension == "kt" }?.toList().orEmpty()

    /**
     * Retire les commentaires `//`, `/* */` (imbriqués) et la documentation d'un texte Kotlin, sans toucher aux chaînes (`"castbridge://open-tv"` reste entière) ni aux caractères.
     * Les retours à la ligne sont gardés.
     */
    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                    out.append(src, i, end); i = end
                }
                c == '"' -> {
                    var j = i + 1
                    while (j < n && src[j] != '"' && src[j] != '\n') { if (src[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, n)
                    out.append(src, i, end); i = end
                }
                c == '\'' -> {
                    var j = i + 1
                    while (j < n && src[j] != '\'' && src[j] != '\n') { if (src[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, n)
                    out.append(src, i, end); i = end
                }
                src.startsWith("//", i) -> { while (i < n && src[i] != '\n') i++ }
                src.startsWith("/*", i) -> {
                    var depth = 1
                    i += 2
                    while (i < n && depth > 0) {
                        when {
                            src.startsWith("/*", i) -> { depth++; i += 2 }
                            src.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> { if (src[i] == '\n') out.append('\n'); i++ }
                        }
                    }
                    out.append(' ')
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }
}
