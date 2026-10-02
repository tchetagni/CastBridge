package castbridge.core.journey

import java.io.File
import java.util.Random

/**
 * Fichiers déterministes d'un parcours : le même (nom, taille, graine) donne toujours les mêmes octets, rien n'est committé.
 * Le dossier est temporaire et supprimé par [close].
 */
class Scenario(private val seed: Long = 1) : AutoCloseable {
    val dir: File = kotlin.io.path.createTempDirectory("journey-files").toFile()

    /** Écrit (ou réutilise) un fichier de [bytes] octets pseudo-aléatoires dérivés de [seed] et du nom. */
    fun file(name: String, bytes: Long): File {
        val f = File(dir, name)
        if (f.isFile && f.length() == bytes) return f
        val rnd = Random(seed * 31 + name.hashCode())
        val chunk = ByteArray(1 shl 20)
        f.outputStream().buffered(1 shl 20).use { out ->
            var left = bytes
            while (left > 0) { rnd.nextBytes(chunk); val n = minOf(left, chunk.size.toLong()).toInt(); out.write(chunk, 0, n); left -= n }
        }
        return f
    }

    /** 5 Mio : le « petit fichier » des parcours P-11. */
    fun small(name: String = "petit.bin"): File = file(name, SMALL)

    /** 64 Mio : le « gros fichier » des parcours P-12 (blocs de 1 Mio, plusieurs voies). */
    fun big(name: String = "gros.bin"): File = file(name, BIG)

    override fun close() { dir.deleteRecursively() }

    companion object {
        const val SMALL = 5L shl 20
        const val BIG = 64L shl 20
    }
}
