package castbridge.core.tv

import java.util.Locale

/**
 * Lecture automatique de l'épisode suivant (wtv-01 n°8) : pur. Le suivant est cherché UNIQUEMENT dans le dossier du fichier (jamais ailleurs),
 * parmi les vidéos, dans l'ordre naturel des noms (« Ep2 » avant « Ep10 »). Compte à rebours de 8 s annulable ; désactivable.
 */
object NextEpisode {
    const val COUNTDOWN_S = 8
    val VIDEO_EXT = setOf("mkv", "mp4", "avi", "mov", "m4v", "ts", "webm", "wmv", "mpg", "mpeg", "flv", "3gp", "ogv", "m2ts")

    fun folderOf(path: String) = path.replace('\\', '/').substringBeforeLast('/', "")
    private fun nameOf(path: String) = path.replace('\\', '/').substringAfterLast('/')
    fun isVideo(path: String) = nameOf(path).substringAfterLast('.', "").lowercase(Locale.ROOT) in VIDEO_EXT

    /** Le fichier à lire après [current] parmi [all] (chemins ou noms) ; null = c'était le dernier du dossier. */
    fun next(current: String, all: Collection<String>): String? {
        val folder = folderOf(current)
        val siblings = all.filter { folderOf(it) == folder && isVideo(it) }.distinct().sortedWith { a, b -> naturalCompare(nameOf(a), nameOf(b)) }
        val i = siblings.indexOfFirst { naturalCompare(nameOf(it), nameOf(current)) == 0 }
        return if (i < 0) siblings.firstOrNull { naturalCompare(nameOf(it), nameOf(current)) > 0 } else siblings.getOrNull(i + 1)
    }

    /** Ordre « humain » : les nombres se comparent par leur valeur, le reste sans tenir compte de la casse. */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0; var j = 0
        val x = a.lowercase(Locale.ROOT); val y = b.lowercase(Locale.ROOT)
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                var ie = i; while (ie < x.length && x[ie].isDigit()) ie++
                var je = j; while (je < y.length && y[je].isDigit()) je++
                val na = x.substring(i, ie).trimStart('0'); val nb = y.substring(j, je).trimStart('0')
                if (na.length != nb.length) return na.length.compareTo(nb.length)
                val c = na.compareTo(nb); if (c != 0) return c
                i = ie; j = je
            } else {
                if (x[i] != y[j]) return x[i].compareTo(y[j])
                i++; j++
            }
        }
        return (x.length - i).compareTo(y.length - j)
    }
}

/** Compte à rebours « Épisode suivant dans 8 s » : annulable (RETOUR / OK). Pur : l'horloge est donnée. */
class AutoNextCountdown(private val startMs: Long, private val seconds: Int = NextEpisode.COUNTDOWN_S) {
    var cancelled = false; private set
    fun cancel() { cancelled = true }
    fun remainingS(nowMs: Long): Int = if (cancelled) 0 else (seconds - ((nowMs - startMs) / 1000).toInt()).coerceIn(0, seconds)
    /** Vrai quand il faut lancer l'épisode suivant. */
    fun due(nowMs: Long): Boolean = !cancelled && nowMs - startMs >= seconds * 1000L
    fun label(nowMs: Long, title: String) = "Épisode suivant dans ${remainingS(nowMs)} s : $title (RETOUR pour annuler)"
}
