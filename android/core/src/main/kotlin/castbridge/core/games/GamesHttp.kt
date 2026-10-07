package castbridge.core.games

import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizHttp
import castbridge.core.tv.PublicRoutes
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response

/**
 * Les pages web des jeux : UNE page commune (`castbridge/games/play.html` : entrée par code et pseudo, places, état en direct, reprise après coupure) dans laquelle chaque jeu apporte son
 * dessin de la table (`castbridge/games/<id>.js`, qui définit `GameUI.render`). Rien à installer sur le téléphone.
 */
object GamePages {
    private fun resource(name: String): String? = GamePages::class.java.getResourceAsStream("/castbridge/games/$name")?.use { String(it.readBytes(), Charsets.UTF_8) }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val pages = HashMap<String, String>()

    /** La page de jeu de [e] (jouable) : le gabarit commun avec le nom du jeu et son dessin de table. */
    @Synchronized fun play(e: GameCatalog.Entry): String = pages.getOrPut(e.id) {
        (resource("play.html") ?: return "<!doctype html><meta charset=utf-8><title>${escape(e.name)}</title><p>Page des jeux indisponible.")
            .replace("/*GAME_TITLE*/", escape(e.name))
            .replace("/*GAME*/null", Json.write(linkedMapOf("id" to e.id, "name" to e.name)))
            .replace("/*GAME_JS*/", resource("${e.id}.js").orEmpty().replace("</script", "<\\/script"))
    }

    /** La page d'un jeu « bientôt » : le nom et la phrase, rien d'autre (aucune règle inventée). */
    fun soon(e: GameCatalog.Entry): String =
        "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>${escape(e.name)} — CastBridge-TV</title>" +
            "<style>body{background:#121212;color:#ededed;font:18px/1.4 system-ui,sans-serif;margin:0;padding:24px;max-width:560px;margin:auto}h1{margin:.2em 0}p{color:#a8a8a8}</style></head><body>" +
            "<h1>${escape(e.name)}</h1><p>${escape(GameCatalog.SOON)}.</p></body></html>"

    /** La liste des jeux (`/jeux`) : un lien par jeu jouable, les autres annoncés « bientôt ». */
    fun index(): String {
        val items = GameCatalog.all.joinToString("") { e ->
            if (e.playable) "<li><a href=\"/jeux/${escape(e.id)}\">${escape(e.name)}</a></li>" else "<li>${escape(e.name)} <small>(${escape(GameCatalog.SOON)})</small></li>"
        }
        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Jeux — CastBridge-TV</title>" +
            "<style>body{background:#121212;color:#ededed;font:18px/1.6 system-ui,sans-serif;margin:0;padding:24px;max-width:560px;margin:auto}a{color:#33b5e5}small{color:#a8a8a8}</style></head><body>" +
            "<h1>Jeux</h1><ul>$items</ul></body></html>"
    }
}

/**
 * Les routes publiques de TOUS les jeux à règles de la TV, sur le port 8765 sans le code PIN (code de salle + jeton de joueur) : `/jeux` (la liste) et, pour chaque jeu du
 * [GameCatalog], `/jeux/<id>` (page), `/jeux/<id>/api/hello|join|events|state|act|leave` (voir [RoomHttp]). Un jeu « bientôt » répond à tout (page, `hello` avec `"soon":true`)
 * mais n'a jamais de salle : « Bientôt : règles en attente du propriétaire ». Les limites de débit sont communes à tous les jeux. Aucune route des échecs ni du Quiz n'est touchée.
 *
 * [roomOf] donne la salle ouverte du jeu demandé (celle que la TV a ouverte pour lui), ou null.
 */
class GamesHttp(
    private val roomOf: (String) -> RoomEndpoint?,
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    streamMaxMs: Long = 10 * 60_000L,
    pingMs: Long = 15_000,
) : PublicRoutes {
    private val limits = RoomLimits(clock)
    private val mounts: Map<String, RoomHttp> = GameCatalog.all.associate { e ->
        e.id to RoomHttp("/jeux/${e.id}", { if (e.playable) roomOf(e.id) else null }, { if (e.playable) GamePages.play(e) else GamePages.soon(e) }, RulesRoom.PROTOCOL,
            if (e.playable) "Aucune partie de ${e.name} ouverte sur la TV." else GameCatalog.SOON,
            helloExtra = ",\"game\":\"${e.id}\"" + (if (e.playable) "" else ",\"soon\":true"), seqParam = "seq", limits = limits, clock = clock, streamMaxMs = streamMaxMs, pingMs = pingMs)
    }

    /** Les flux d'évènements des jeux se partagent un seul budget (celui de [limits]) : un seul jeu de fils pour tous. */
    override val extraThreads: Int = RoomHttp.MAX_STREAMS + 2

    override fun serve(s: NanoHTTPD.IHTTPSession): Response? {
        val path = s.uri
        if (path == "/jeux" || path == "/jeux/") {
            if (s.method != NanoHTTPD.Method.GET) return json(405, """{"error":"use GET"}""")
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", GamePages.index()).also { it.addHeader("Cache-Control", "no-store") }
        }
        if (!path.startsWith("/jeux/")) return null
        val id = path.removePrefix("/jeux/").substringBefore('/')
        return mounts[id]?.serve(s) ?: json(404, """{"error":"unknown game"}""")
    }

    private fun json(code: Int, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(QuizHttp.status(code), "application/json; charset=utf-8", body).also { it.addHeader("Cache-Control", "no-store") }
}
