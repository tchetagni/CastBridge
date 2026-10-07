package castbridge.core.games

import castbridge.core.FakePlayer
import castbridge.core.chess.ChessHttp
import castbridge.core.games.bataille.Bataille
import castbridge.core.quiz.Json
import castbridge.core.tv.CombinedRoutes
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeRegistry
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les routes `/jeux/<id>/…` de la vraie `ReceiverServer` : deux téléphones jouent une Bataille entière, coupure et reprise, jeux « bientôt ». Horloge de la salle fausse (le serveur HTTP est réel). */
class GamesHttpTest {
    private var now = 1_000L
    private val dir = kotlin.io.path.createTempDirectory("gameshttp").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    @Volatile private var room: RulesRoom<*, *>? = null
    /** L'horloge des limites de débit des routes : elle avance de 200 ms à chaque lecture, pour qu'une partie entière (centaines de requêtes) ne se heurte pas au plafond de 10 requêtes par seconde. */
    private val httpClock = java.util.concurrent.atomic.AtomicLong(0)
    private val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456",
        publicRoutes = CombinedRoutes(ChessHttp({ null }), GamesHttp({ id -> room?.takeIf { it.rules.id == id } }, clock = { httpClock.addAndGet(200) }, pingMs = 300))).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"

    @AfterTest fun tearDown() { room?.close(); server.stop(); dir.deleteRecursively() }

    private fun openRoom(vararg kinds: SeatKind = arrayOf(SeatKind.PHONE, SeatKind.PHONE)): RulesRoom<*, *> {
        val r = GameCatalog.newRoom("bataille", clock = { now }, random = java.util.Random(11), autoTick = false, aiDelayMs = 0, wallClock = { 1_790_000_000_000L + now })!!
        r.configure(kinds.toList()); room = r; return r
    }

    private fun call(method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 5_000; c.readTimeout = 10_000
            if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
            val code = c.responseCode
            return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
        } finally { c.disconnect() }
    }
    private fun get(path: String) = call("GET", path)
    private fun post(path: String) = call("POST", path)
    private fun obj(r: Pair<Int, String>) = Json.obj(r.second)
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.l(k: String) = this[k] as List<Any?>

    private fun join(name: String, token: String? = null): Map<String, Any?> {
        val r = post("/jeux/bataille/api/join?code=${room!!.code}&name=$name" + (token?.let { "&token=$it" } ?: ""))
        assertEquals(200, r.first, r.second); return obj(r)
    }
    private fun act(token: String, action: String, arg: String? = null, seq: Int? = null) =
        post("/jeux/bataille/api/act?token=$token&action=$action" + (arg?.let { "&arg=$it" } ?: "") + (seq?.let { "&seq=$it" } ?: ""))
    private fun state(token: String) = obj(get("/jeux/bataille/api/state?token=$token"))

    // ---------------------------------------------------------------- pages

    @Test fun theGamePageIsTheCommonShellWithTheGameInjected() {
        val (code, page) = get("/jeux/bataille")
        assertEquals(200, code)
        assertTrue(page.contains("\"id\":\"bataille\"") && page.contains("Bataille (démonstration)"), "le nom du jeu est dans la page")
        assertTrue(page.contains("window.GameUI=") && page.contains("Retourner ma carte"), "le dessin de la table de la Bataille est dans la page")
        assertTrue(page.contains("/jeux/\"+GAME.id") && page.contains("/api/events?token="), "la page parle aux routes du jeu")
        assertFalse(page.contains("/*GAME"), "aucun marqueur du gabarit ne reste : ${page.take(200)}")
        assertEquals(page, get("/jeux/bataille/").second, "avec ou sans barre finale")
        assertEquals(1, Regex("<title>").findAll(page).count()); assertTrue(page.contains("<title>Bataille (démonstration) — CastBridge-TV</title>"))
        val (iCode, index) = get("/jeux")
        assertEquals(200, iCode); assertTrue(index.contains("<a href=\"/jeux/bataille\">Bataille (démonstration)</a>"))
        assertTrue(index.contains("Fap-Fap") && index.contains("Agraham Tia") && index.contains(GameCatalog.SOON))
        assertFalse(index.contains("href=\"/jeux/fap-fap\""), "un jeu « bientôt » n'est pas un lien de partie")
        assertEquals(405, post("/jeux").first)
    }

    @Test fun theGameDrawingCannotCloseItsScriptTagEarly() {
        assertEquals(2, Regex("</script>").findAll(GamePages.play(GameCatalog.BATAILLE)).count(), "deux scripts (la page et le jeu), chacun fermé une seule fois à sa place")
        assertFalse(GamePages::class.java.getResourceAsStream("/castbridge/games/bataille.js")!!.use { String(it.readBytes()) }.contains("</script"))
    }

    @Test fun gamesWithoutRulesAnswerSoonAndNeverHaveARoom() {
        for (id in listOf("fap-fap", "agraham-tia")) {
            val (code, page) = get("/jeux/$id")
            assertEquals(200, code); assertTrue(page.contains(GameCatalog.SOON), page); assertFalse(page.contains("GameUI"))
            assertEquals("""{"open":false,"protocol":1,"game":"$id","soon":true}""", get("/jeux/$id/api/hello").second)
            val j = post("/jeux/$id/api/join?code=1234&name=Awa")
            assertEquals(410, j.first); assertEquals(GameCatalog.SOON, obj(j)["message"])
            assertNull(GameCatalog.newRoom(id), "aucune salle sans règles")
        }
    }

    @Test fun unknownGamesAndOtherRoutesAreNotTouched() {
        assertEquals(404, get("/jeux/inconnu").first); assertEquals(404, get("/jeux/inconnu/api/hello").first)
        assertEquals(404, get("/jeux/bataille/api/nope").first)
        assertEquals(401, get("/api/info").first, "l'API d'administration reste derrière le PIN")
        assertEquals("""{"open":false,"protocol":1}""", get("/chess/api/hello").second, "les échecs répondent comme avant à côté des jeux")
    }

    // ---------------------------------------------------------------- une salle

    @Test fun helloJoinAndStateOfARoom() {
        assertEquals("""{"open":false,"protocol":1,"game":"bataille"}""", get("/jeux/bataille/api/hello").second)
        val r = openRoom()
        assertEquals("""{"open":true,"protocol":1,"game":"bataille","stage":"LOBBY","players":0,"max":10}""", get("/jeux/bataille/api/hello").second)
        assertEquals(403, post("/jeux/bataille/api/join?code=00x0&name=Awa").first)
        assertEquals(400, post("/jeux/bataille/api/join?code=${r.code}&name=%20%3C%3E").first)
        val a = join("Awa"); val b = join("Bello"); val s = join("Carine")
        assertEquals(0L, a["seat"]); assertEquals("s1", a["seatId"]); assertEquals(1L, b["seat"]); assertNull(s["seat"]); assertNull(s["seatId"])
        assertEquals("Awa", a["name"]); assertTrue((a["token"] as String).matches(Regex("[0-9a-f]{32}")))
        val v = state(a["token"] as String)
        assertEquals("bataille", v["game"]); assertEquals("LOBBY", v.get("stage")); assertEquals("p1", v.m("me")["id"])
        assertEquals(401, get("/jeux/bataille/api/state?token=forged").first)
        assertEquals(405, get("/jeux/bataille/api/join").first); assertEquals(405, post("/jeux/bataille/api/state?token=x").first)
        assertEquals(200, post("/jeux/bataille/api/leave?token=${s["token"]}").first)
    }

    @Test fun twoPhonesPlayAWholeBatailleOverHttp() {
        val r = openRoom()
        val a = join("Awa")["token"] as String; val b = join("Bello")["token"] as String
        assertNull(r.start())
        var seq = 0
        // un coup hors tour, un faux jeton, un coup illisible : les codes HTTP des routes de jeu
        assertEquals(409, act(b, "move", "flip", 0).first); assertEquals(401, act("forged", "move", "flip", 0).first); assertEquals(400, act(a, "move", "nope", 0).first)
        while (state(a)["stage"] == "PLAYING") {
            val toMove = state(a).l("toMove").single() as String
            val token = if (toMove == "s1") a else b
            val res = act(token, "move", "flip", seq)
            assertEquals(200, res.first, res.second); assertEquals("OK", obj(res)["result"])
            if (seq % 40 == 0) { assertEquals("STALE", obj(act(token, "move", "flip", seq)).get("result"), "le doublon n'est pas rejoué"); assertEquals(200, act(token, "move", "flip", seq).first) }
            seq++
            assertTrue(seq <= Bataille.MAX_FLIPS + 60)
        }
        val end = state(b)
        assertEquals("FINISHED", end["stage"]); assertEquals(seq.toLong(), end["moveNo"])
        assertTrue(end.m("result")["text"].toString().contains("gagne"))
        // la partie est finie : on peut lire la graine dans le journal, pas dans l'état
        assertFalse(get("/jeux/bataille/api/state?token=$a").second.contains("seed"))
        val j = r.journal("0123456789abcdef")!!
        assertNull(GameJournal.impossible(j, Bataille)); assertEquals(seq, j.entries.size)
        // la table finale ne montre que des nombres de cartes
        assertTrue(end.m("table").m("piles").values.all { it is Number })
    }

    @Test fun aPhoneThatLosesItsConnectionComesBackWithItsTokenAndTheGameGoesOn() {
        val r = openRoom()
        val a = join("Awa"); val b = join("Bello"); val ta = a["token"] as String; val tb = b["token"] as String
        r.start()
        assertEquals("OK", obj(act(ta, "move", "flip", 0))["result"]); assertEquals("OK", obj(act(tb, "move", "flip", 1))["result"])
        // Awa perd le Wi-Fi 40 s (aucune requête de sa part), Bello reste en ligne
        val sse = URL("$base/jeux/bataille/api/events?token=$tb").openConnection() as HttpURLConnection
        sse.readTimeout = 5_000
        sse.inputStream.bufferedReader().let { rd -> generateSequence { rd.readLine() }.first { it.startsWith("data: ") } }
        now += 40_000; r.tick()
        val seen = state(tb).l("seats")[0] as Map<*, *>
        assertEquals(false, seen["connected"], "Bello voit que Awa est déconnectée"); assertEquals(20_000L, seen["graceLeftMs"])
        // Awa revient avec son jeton : même place, même partie
        val back = join("Awa", ta)
        assertEquals(ta, back["token"]); assertEquals(0L, back["seat"]); assertEquals("s1", back["seatId"])
        assertEquals("PLAYING", state(ta)["stage"]); assertNull((state(tb).l("seats")[0] as Map<*, *>)["graceLeftMs"])
        assertEquals("OK", obj(act(ta, "move", "flip", 2))["result"])
        assertEquals(3L, state(tb)["moveNo"])
        sse.disconnect()
    }

    @Test fun aPhoneSilentForSixtySecondsLosesAndTheOtherWins() {
        val r = openRoom()
        val ta = join("Awa")["token"] as String; val tb = join("Bello")["token"] as String
        r.start()
        val sse = URL("$base/jeux/bataille/api/events?token=$tb").openConnection() as HttpURLConnection
        sse.readTimeout = 5_000
        sse.inputStream.bufferedReader().let { rd -> generateSequence { rd.readLine() }.first { it.startsWith("data: ") } }   // flux ouvert : Bello est toujours là
        now += 100_000; r.tick()
        val v = state(tb)
        assertEquals("FINISHED", v["stage"])
        assertEquals("DISCONNECTED", v.m("result")["reason"]); assertEquals(listOf("s2"), v.m("result").l("winners")); assertEquals("s1", v.m("result")["by"])
        assertTrue(v.m("result")["text"].toString().contains("Awa est resté déconnecté trop longtemps"))
        assertEquals("FORBIDDEN", obj(post("/jeux/bataille/api/act?token=$ta&action=resign"))["result"], "la partie est finie")
        sse.disconnect()
    }

    @Test fun theEventStreamSendsTheStateAtEveryChange() {
        val r = openRoom()
        val ta = join("Awa")["token"] as String; join("Bello")
        val sse = URL("$base/jeux/bataille/api/events?token=$ta").openConnection() as HttpURLConnection
        sse.readTimeout = 5_000
        val rd = sse.inputStream.bufferedReader()
        val first = generateSequence { rd.readLine() }.first { it.startsWith("data: ") }
        assertTrue(first.contains("\"stage\":\"LOBBY\"") && first.contains("\"game\":\"bataille\""))
        assertEquals("text/event-stream; charset=utf-8", sse.getHeaderField("Content-Type"))
        Thread { Thread.sleep(150); r.start() }.start()
        val next = generateSequence { rd.readLine() }.first { it.startsWith("data: ") }
        assertTrue(next.contains("\"stage\":\"PLAYING\""), "le départ de la partie est poussé sans que le téléphone demande rien")
        sse.disconnect()
    }

    @Test fun soloGameWithTheComputerLeavesTheTvAloneAndPhonesWatch() {
        val r = openRoom(SeatKind.REMOTE, SeatKind.AI)
        val watcher = join("Carine")["token"] as String
        assertNull(r.start())
        assertEquals("OK", r.hostMove("flip").name)                 // la télécommande retourne, l'ordinateur répond sans délai
        val v = state(watcher)
        assertEquals(2L, v["moveNo"]); assertNull(v.m("me")["seat"], "un spectateur : pas de place")
        assertEquals("FORBIDDEN", obj(act(watcher, "move", "flip", 2))["result"])
        assertNotNull(v["table"])
    }
}
