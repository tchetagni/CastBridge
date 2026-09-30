package castbridge.core.learn

import castbridge.core.quiz.Json
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply

/**
 * PIN-protected routes of « Apprendre » on the TV (the phone app knows the PIN), docs/LEARN.md § API:
 *
 * - GET  /api/learn                         what the TV shows (screen, pack, lesson, page, exercise) + profiles
 * - POST /api/learn/open[?profile=]         opens « Apprendre » on the TV
 * - POST /api/learn/cmd?action=…            remote control / teacher mode: next, prev, ok, back, choice&value=<shown index>,
 *                                           number&value=<text>, bool&value=true|false, self&value=0|0.5|1, speak,
 *                                           teacher&value=on|off, lesson&pack=&lesson=[&page=]
 * - GET  /api/learn/dashboard?profile=      parent dashboard of one student (or all when absent)
 * - GET  /api/learn/packs                   packs found (where, size, version) and refused packs (reason)
 * - POST /api/learn/packs/install           body = a pack zip (≤ 4 MB): checked, then installed on a volume
 * - POST /api/learn/packs/import?name=      installs a pack sent to the TV library (file exchange) or found on the USB drive
 * - POST /api/learn/packs/remove?id=        deletes an installed pack (to free space)
 * - GET  /api/learn/events?since=           telemetry events (stable names), for a future upload
 */
class LearnApi(private val host: Host) : ApiExtension {
    interface Host {
        /** JSON of the TV screen state, or null when « Apprendre » is not open. */
        fun screenJson(): String?
        /** Opens « Apprendre » (null = done, else why not: the TV screen must be visible). */
        fun open(profile: String?): String?
        /** A remote command; null = done, else the error. */
        fun command(action: String, params: Map<String, String>): String?
        fun progress(): LearnProgress
        fun library(): LearnLibrary
        fun install(bytes: ByteArray): PackInstaller.Result
        fun importFile(name: String): PackInstaller.Result
        fun remove(id: String): String?
        fun now(): Long = System.currentTimeMillis()
    }

    override fun wantsBody(path: String) = path == "/api/learn/packs/install"

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? =
        if (path == "/api/learn/packs/install" && method == "POST") installed(host.install(body)) else null

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != "/api/learn" && !path.startsWith("/api/learn/")) return null
        val get = method == "GET"; val post = method == "POST"
        return when (path) {
            "/api/learn" -> if (get) ApiReply(200, status()) else bad()
            "/api/learn/open" -> if (post) host.open(params["profile"])?.let { ApiReply(409, err(it, "\"needsForeground\":true")) } ?: ApiReply(200, status()) else bad()
            "/api/learn/cmd" -> if (post) {
                val a = params["action"].orEmpty()
                if (a !in ACTIONS) ApiReply(400, err("action inconnue : $a"))
                else host.command(a, params)?.let { ApiReply(409, err(it)) } ?: ApiReply(200, status())
            } else bad()
            "/api/learn/dashboard" -> if (get) ApiReply(200, dashboard(params["profile"])) else bad()
            "/api/learn/packs" -> if (get) ApiReply(200, packs()) else bad()
            "/api/learn/packs/import" -> if (post) params["name"]?.takeIf { it.isNotBlank() }?.let { installed(host.importFile(it)) } ?: ApiReply(400, err("name manquant")) else bad()
            "/api/learn/packs/remove" -> if (post) params["id"]?.let { id -> host.remove(id)?.let { ApiReply(409, err(it)) } ?: ApiReply(200, packs()) } ?: ApiReply(400, err("id manquant")) else bad()
            "/api/learn/events" -> if (get) ApiReply(200, events(params["since"]?.toLongOrNull() ?: 0)) else bad()
            else -> ApiReply(404, err("route inconnue"))
        }
    }

    private fun bad() = ApiReply(405, err("méthode non prise en charge"))
    private fun err(m: String, extra: String? = null) = "{\"error\":${Json.quote(m)}${extra?.let { ",$it" } ?: ""}}"

    private fun installed(r: PackInstaller.Result): ApiReply = when (r) {
        is PackInstaller.Result.Installed -> {
            host.library().forget()
            ApiReply(200, Json.write(linkedMapOf("installed" to r.manifest.id, "version" to r.manifest.version, "where" to r.where, "file" to r.file.name)))
        }
        is PackInstaller.Result.Refused -> ApiReply(422, err(r.reason))
    }

    fun status(): String {
        val pr = host.progress()
        return "{\"open\":${host.screenJson() != null},\"screen\":${host.screenJson() ?: "null"},\"profiles\":" +
            Json.write(pr.profiles.map { linkedMapOf("id" to it.id, "name" to it.name, "avatar" to it.avatar, "level" to it.level, "exam" to it.exam, "examDate" to it.examDate) }) + "}"
    }

    fun dashboard(profile: String?): String {
        val pr = host.progress(); val now = host.now()
        val ids = if (profile != null) listOf(profile) else pr.profiles.map { it.id }
        return Json.write(linkedMapOf("students" to ids.filter { pr.profile(it) != null }.map { pr.dashboard(it, now) }))
    }

    fun packs(): String {
        val lib = host.library()
        return Json.write(linkedMapOf(
            "packs" to lib.all().map { r -> linkedMapOf("id" to r.id, "version" to r.version, "title" to r.manifest.title, "exam" to r.manifest.exam,
                "level" to r.manifest.level, "subject" to r.manifest.subject, "size" to r.manifest.size, "where" to r.origin,
                "file" to r.file?.name, "removable" to (r.file != null), "lessons" to r.manifest.lessons, "exercises" to r.manifest.exercises) },
            "refused" to lib.problems.map { (k, v) -> linkedMapOf("pack" to k, "reason" to v) },
        ))
    }

    fun events(since: Long): String = Json.write(linkedMapOf("events" to host.progress().state.events.filter { it.at > since }
        .map { linkedMapOf("name" to it.name, "at" to it.at, "profile" to it.profile, "data" to it.data) }))

    companion object {
        val ACTIONS = setOf("next", "prev", "ok", "back", "choice", "number", "bool", "self", "speak", "teacher", "lesson")
    }
}
