package castbridge.core.tv

/** [folder] = the real folder the TV filed it in ("Films"), [origin] = the name the phone sent it under when the TV renamed it (both empty/null on an older TV). */
data class TvFile(val name: String, val size: Long, val received: Long, val complete: Boolean, val folder: String = "", val origin: String? = null)

/**
 * Never copy what the TV already holds: the same name, complete, with exactly the same size. A same-named file of another size is a DIFFERENT file (not skipped, not overwritten blindly).
 * A file the TV filed under a clean name is found by the name it was sent under ([TvInfo.file], `origin`).
 */
object TvDedupe {
    fun alreadyThere(f: TvFile?, size: Long): Boolean = f != null && f.complete && size > 0 && f.size == size
}

/** Parsed GET /api/info. */
data class TvInfo(
    val files: List<TvFile>, val free: Long, val used: Long, val quota: Long,
    val state: String, val playing: String?, val pos: Long, val dur: Long,
) {
    /** The file called [name], or the one the TV renamed on reception that was sent as [name]. */
    fun file(name: String?) = files.firstOrNull { it.name == name } ?: name?.let { n -> files.firstOrNull { it.origin == n && it.complete } }

    companion object {
        private val ENTRY = Regex("\\{\"name\":\"((?:[^\"\\\\]|\\\\.)*)\",\"size\":(\\d+),\"received\":(\\d+),\"complete\":(true|false)[,}]")

        fun parse(j: String): TvInfo {
            val files = ENTRY.findAll(j).map { m ->
                // additive fields of the same object (`folder`, `origin`), absent on an older TV
                val tail = j.substring(m.range.last, j.indexOf('}', m.range.last).let { if (it < 0) j.length else it })
                TvFile(m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\"), m.groupValues[2].toLong(), m.groupValues[3].toLong(), m.groupValues[4] == "true",
                    TvClient.str(tail, "folder").orEmpty(), TvClient.str(tail, "origin"))
            }.toList()
            val player = j.substringAfter("\"player\":", "{}")
            return TvInfo(files, TvClient.num(j, "free") ?: 0, TvClient.num(j, "used") ?: 0, TvClient.num(j, "quota") ?: 0,
                TvClient.str(player, "state") ?: "idle", TvClient.str(player, "name"),
                TvClient.num(player, "pos") ?: 0, TvClient.num(player, "dur") ?: 0)
        }
    }
}
