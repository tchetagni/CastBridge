package castbridge.core.tv

data class TvFile(val name: String, val size: Long, val received: Long, val complete: Boolean)

/** Never copy what the TV already holds: the same name, complete, with exactly the same size. A same-named file of another size is a DIFFERENT file (not skipped, not overwritten blindly). */
object TvDedupe {
    fun alreadyThere(f: TvFile?, size: Long): Boolean = f != null && f.complete && size > 0 && f.size == size
}

/** Parsed GET /api/info. */
data class TvInfo(
    val files: List<TvFile>, val free: Long, val used: Long, val quota: Long,
    val state: String, val playing: String?, val pos: Long, val dur: Long,
) {
    fun file(name: String?) = files.firstOrNull { it.name == name }

    companion object {
        private val ENTRY = Regex("\\{\"name\":\"((?:[^\"\\\\]|\\\\.)*)\",\"size\":(\\d+),\"received\":(\\d+),\"complete\":(true|false)[,}]")

        fun parse(j: String): TvInfo {
            val files = ENTRY.findAll(j).map { m ->
                TvFile(m.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\"), m.groupValues[2].toLong(), m.groupValues[3].toLong(), m.groupValues[4] == "true")
            }.toList()
            val player = j.substringAfter("\"player\":", "{}")
            return TvInfo(files, TvClient.num(j, "free") ?: 0, TvClient.num(j, "used") ?: 0, TvClient.num(j, "quota") ?: 0,
                TvClient.str(player, "state") ?: "idle", TvClient.str(player, "name"),
                TvClient.num(player, "pos") ?: 0, TvClient.num(player, "dur") ?: 0)
        }
    }
}
