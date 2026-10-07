package castbridge.core.games

import castbridge.core.FakePlayer
import castbridge.core.chess.ChessAi
import castbridge.core.chess.ChessHttp
import castbridge.core.chess.ChessRoom
import castbridge.core.chess.ClockMode
import castbridge.core.tv.CombinedRoutes
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeRegistry
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executor

/**
 * Un déroulé complet et DÉTERMINISTE de salles d'échecs (horloge fausse, aléa à graine, ordinateur sur le fil appelant) :
 * chaque étape range une ligne « étiquette : résultat » dont les états JSON complets (versions `v`, codes, identifiants, jetons, notices).
 * Les fichiers `castbridge/games/chess-golden-rooms.txt` ([run]) et `chess-golden-http.txt` ([runHttp]) gardent la sortie de l'implémentation d'AVANT la migration de `ChessRoom` sur `GameRoom` :
 * [ChessGoldenTest] exige que la sortie d'aujourd'hui soit identique, octet pour octet (comportement observable inchangé).
 * Pas de partie contre l'ordinateur ici (sa réflexion est bornée par le temps, donc pas reproductible à l'octet) : `ChessRoomTest` la couvre.
 */
object ChessScenario {
    private val direct = Executor { it.run() }

    fun run(): List<String> {
        val out = ArrayList<String>()
        fun rec(label: String, value: Any?) { out += "$label : $value" }
        var now = 1_000L
        fun room(secs: Int = 30, seed: Long = 5) = ChessRoom(clock = { now }, random = java.util.Random(seed), maxPlayers = 4, autoTick = false, aiExecutor = direct,
            ai = ChessAi(ttBits = 12, random = java.util.Random(1))).also { it.configure(ChessRoom.Seat.PHONE, ChessRoom.Seat.PHONE, level = 1, perMoveSeconds = secs) }

        // ---------------------------------------------------------------- salle de deux téléphones : entrées, places, spectateurs
        val r = room()
        rec("code", r.code)
        rec("lobby vide (TV)", r.viewJson(null))
        rec("mauvais code", r.join(if (r.code == "0000") "1111" else "0000", "Ali").status)
        rec("mauvais pseudo", r.join(r.code, " <> ").status)
        val a = r.join(r.code, "Ali").player!!
        val b = r.join(r.code, "Ali").player!!
        rec("joueurs", listOf(a, b).joinToString(" ; ") { "${it.id}/${it.token}/${it.name}/${r.colorOf(it)}" })
        val s = r.join(r.code, "Carine").player!!
        rec("spectateur", "${s.id}/${s.token}/${s.name}/${r.colorOf(s)}")
        r.join(r.code, "Dan")
        rec("salle pleine", r.join(r.code, "Eve").status)
        rec("lobby (Ali)", r.viewJson(a.token))
        rec("lobby (Carine)", r.viewJson(s.token))
        rec("manque", r.missing())
        rec("changer de place : Ali debout", r.act(a.token, "stand"))
        rec("changer de place : Carine s'assoit sur les Blancs", r.act(s.token, "sit", "w"))
        rec("changer de place : Ali veut les Blancs (occupés)", r.act(a.token, "sit", "w"))
        rec("changer de place : mauvais argument", r.act(a.token, "sit", "x"))
        rec("changer de place : Ali reprend les Blancs libres ?", r.act(a.token, "sit", "b"))
        rec("lobby après changements (TV)", r.viewJson(null))
        rec("échange des couleurs", r.swapColors())
        rec("lobby après échange (TV)", r.viewJson(null))
        r.act(s.token, "stand"); r.act(b.token, "stand")
        rec("remettre Ali blancs, Bea noirs", listOf(r.act(a.token, "sit", "w"), r.act(b.token, "sit", "b")).joinToString())

        // ---------------------------------------------------------------- partie : coups, doublon, nulle, abandon
        rec("démarrage", r.start())
        rec("démarrage deux fois", r.start())
        rec("pas de changement de place en partie", r.act(a.token, "sit", "b"))
        rec("blancs", r.viewJson(a.token))
        rec("noirs", r.viewJson(b.token))
        rec("spectateur en partie", r.viewJson(s.token))
        now += 5_000
        rec("hors tour", r.act(b.token, "move", "e7e5", 0))
        rec("coup illégal", r.act(a.token, "move", "e2e5", 0))
        rec("spectateur ne joue pas", r.act(s.token, "move", "e2e4", 0))
        rec("jeton inconnu", r.act("forged", "move", "e2e4", 0))
        rec("TV ne joue pas la couleur d'un téléphone", r.hostMove("e2e4"))
        rec("e2e4", r.act(a.token, "move", "e2e4", 0))
        rec("doublon", r.act(a.token, "move", "e2e4", 0))
        now += 3_000
        rec("e7e5", r.act(b.token, "move", "e7e5", 1))
        rec("après deux coups (noirs)", r.viewJson(b.token))
        now += 1_500
        rec("nulle proposée", r.act(a.token, "draw", "offer"))
        rec("nulle : l'auteur ne l'accepte pas", r.act(a.token, "draw", "accept"))
        rec("nulle vue par les noirs", r.viewJson(b.token))
        rec("nulle refusée", r.act(b.token, "draw", "decline"))
        rec("mauvaise action de nulle", r.act(b.token, "draw", "maybe"))
        rec("action inconnue", r.act(b.token, "dance"))
        rec("abandon des noirs", r.act(b.token, "resign"))
        rec("fin de partie (blancs)", r.viewJson(a.token))
        rec("fin de partie (TV)", r.viewJson(null))
        rec("coup après la fin", r.act(a.token, "move", "d2d4", 2))

        // ---------------------------------------------------------------- revanche : retour au salon, départ d'un joueur, nulle d'un commun accord
        rec("retour au salon", r.backToLobby())
        rec("Dan quitte", r.leave(r.players().first { it.name == "Dan" }.token))
        rec("inconnu quitte", r.leave("nobody"))
        rec("salon après départ", r.viewJson(null))
        rec("revanche", r.start())
        r.act(a.token, "draw", "offer"); r.act(b.token, "draw", "accept")
        rec("nulle d'un commun accord", r.viewJson(null))
        rec("reconnexion avec le jeton", "${r.join(r.code, null, a.token).player === a} ${r.join(r.code, "Alice", a.token).player?.name}")
        rec("salon fermé", run { r.close(); "${r.join(r.code, "X").status} ${r.act(a.token, "move", "e2e4", 0)} ${r.viewJson(null)}" })

        // ---------------------------------------------------------------- horloge de la TV : perte au temps, puis mode entraînement
        now = 50_000
        val t = room(secs = 90, seed = 9)
        val w = t.join(t.code, "W").player!!; val k = t.join(t.code, "K").player!!
        t.start()
        rec("horloge : 90 s demandées", t.viewJson(w.token))
        now += 59_999; t.tick(); rec("horloge : 59,999 s", t.stage)
        now += 1; t.tick(); rec("horloge : 60 s", t.viewJson(k.token))
        now = 100_000
        val p = ChessRoom(clock = { now }, random = java.util.Random(11), maxPlayers = 4, autoTick = false, aiExecutor = direct, ai = ChessAi(ttBits = 12, random = java.util.Random(1)))
        p.configure(ChessRoom.Seat.REMOTE, ChessRoom.Seat.REMOTE, perMoveSeconds = 10, mode = ClockMode.PRACTICE)
        p.start()
        rec("entraînement : début (TV)", p.viewJson(null))
        now += 10_000; p.tick()
        rec("entraînement : coup d'office (TV)", p.viewJson(null))
        rec("entraînement : pause", p.hostPause(true))
        now += 60_000; p.tick()
        rec("entraînement : pause jusqu'à la reprise", p.viewJson(null))
        rec("entraînement : reprise", p.hostPause(false))
        rec("entraînement : TV joue", p.hostMove("e7e5") .toString() + " " + p.hostMove("e2e4"))
        rec("entraînement : nulle à deux sur la TV", p.hostOfferDraw())
        rec("entraînement : fin", p.viewJson(null))
        p.close()

        // ---------------------------------------------------------------- la télécommande contre un téléphone
        now = 200_000
        val m = ChessRoom(clock = { now }, random = java.util.Random(13), maxPlayers = 4, autoTick = false, aiExecutor = direct, ai = ChessAi(ttBits = 12, random = java.util.Random(1)))
        m.configure(ChessRoom.Seat.REMOTE, ChessRoom.Seat.PHONE, perMoveSeconds = 20)
        rec("TV/téléphone : manque", m.missing())
        val tel = m.join(m.code, "Tel").player!!
        rec("TV/téléphone : manque après l'arrivée", m.missing())
        rec("TV/téléphone : départ", m.start())
        rec("TV/téléphone : la pause est refusée", m.hostPause(true))
        rec("TV/téléphone : d4", m.hostMove("d2d4"))
        rec("TV/téléphone : d5", m.act(tel.token, "move", "d7d5", 1))
        rec("TV/téléphone : nulle proposée par la TV", m.hostOfferDraw())
        rec("TV/téléphone : vue du téléphone", m.viewJson(tel.token))
        rec("TV/téléphone : le téléphone accepte", m.act(tel.token, "draw", "accept"))
        rec("TV/téléphone : fin", m.viewJson(null))
        rec("TV/téléphone : noms", m.names())
        // présence : flux ouverts puis fermés, délai de présence
        m.backToLobby()
        m.streamOpened(tel); rec("présence : flux ouvert", m.isConnected(tel))
        m.streamClosed(tel); now += 20_000; rec("présence : 20 s après", m.isConnected(tel))
        now += 20_000; rec("présence : 40 s après", m.isConnected(tel))
        rec("présence : version", m.version)
        m.close()
        return out
    }

    /**
     * Les routes `/chess/…` de la vraie `ReceiverServer` : code HTTP + corps exacts de chaque réponse (trois petits scénarios, chacun avec son propre
     * `ChessHttp`, car le limiteur de débit par adresse est lié à l'instance et au temps réel).
     */
    fun runHttp(): List<String> {
        val out = ArrayList<String>()
        fun rec(label: String, value: Any?) { out += "$label : $value" }
        var now = 1_000L
        fun newRoom(seed: Long) = ChessRoom(clock = { now }, random = java.util.Random(seed), maxPlayers = 3, autoTick = false, aiExecutor = direct,
            ai = ChessAi(ttBits = 12, random = java.util.Random(1))).also { it.configure(ChessRoom.Seat.PHONE, ChessRoom.Seat.PHONE, level = 1, perMoveSeconds = 30) }

        class Rig(seed: Long?) {
            val dir = kotlin.io.path.createTempDirectory("chessgolden").toFile()
            val port = ServerSocket(0).use { it.localPort }
            @Volatile var room: ChessRoom? = seed?.let(::newRoom)
            val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456", publicRoutes = CombinedRoutes(ChessHttp({ room }, pingMs = 300))).apply { start(5000, false) }
            val base = "http://127.0.0.1:$port"
            fun call(method: String, path: String): String {
                val c = URL(base + path).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = method; c.connectTimeout = 5_000; c.readTimeout = 10_000
                    if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
                    val code = c.responseCode
                    val body = (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
                    return "$code ${if (path == "/chess" || path == "/chess/") "page sha256=" + MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) } + " " + body.length else body}"
                } finally { c.disconnect() }
            }
            fun tokenOf(reply: String) = Regex("\"token\":\"([0-9a-f]+)\"").find(reply)!!.groupValues[1]
            fun close() { room?.close(); server.stop(); dir.deleteRecursively() }
        }

        // ---- 1. salle ouverte : page, hello, entrées et erreurs d'entrée
        Rig(7).run {
            try {
                val r = room!!
                rec("http page", call("GET", "/chess"))
                rec("http page (barre finale)", call("GET", "/chess/"))
                rec("http hello", call("GET", "/chess/api/hello"))
                rec("http join en GET", call("GET", "/chess/api/join"))
                rec("http mauvais code", call("POST", "/chess/api/join?code=0000x&name=Awa"))
                rec("http mauvais pseudo", call("POST", "/chess/api/join?code=${r.code}&name=%20%3C%3E"))
                val awa = call("POST", "/chess/api/join?code=${r.code}&name=Awa"); rec("http entrée Awa", awa)
                val bello = call("POST", "/chess/api/join?code=${r.code}&name=Bello"); rec("http entrée Bello", bello)
                rec("http entrée spectateur", call("POST", "/chess/api/join?code=${r.code}&name=Carine"))
                rec("http salle pleine", call("POST", "/chess/api/join?code=${r.code}&name=Dan"))
                rec("http retour avec le jeton", call("POST", "/chess/api/join?code=${r.code}&name=Awa&token=${tokenOf(awa)}"))
                rec("http état (Awa)", call("GET", "/chess/api/state?token=${tokenOf(awa)}"))
                rec("http état avec faux jeton", call("GET", "/chess/api/state?token=forged"))
                rec("http état en POST", call("POST", "/chess/api/state?token=${tokenOf(awa)}"))
                rec("http route inconnue", call("GET", "/chess/api/nope"))
                rec("http hello salle ouverte (partie)", run { r.start(); call("GET", "/chess/api/hello") })
                val aw = tokenOf(awa); val be = tokenOf(bello)
                rec("http coup illégal", call("POST", "/chess/api/act?token=$aw&action=move&arg=e2e5&ply=0"))
                rec("http hors tour", call("POST", "/chess/api/act?token=$be&action=move&arg=e7e5&ply=0"))
                rec("http coup", call("POST", "/chess/api/act?token=$aw&action=move&arg=e2e4&ply=0"))
                rec("http doublon", call("POST", "/chess/api/act?token=$aw&action=move&arg=e2e4&ply=0"))
                rec("http requête sans coup", call("POST", "/chess/api/act?token=$aw&action=move"))
                rec("http jeton inconnu", call("POST", "/chess/api/act?token=forged&action=move&arg=e2e4&ply=1"))
                rec("http place interdite en partie", call("POST", "/chess/api/act?token=$be&action=sit&arg=w"))
                rec("http act en GET", call("GET", "/chess/api/act?token=$be&action=resign"))
                rec("http abandon", call("POST", "/chess/api/act?token=$be&action=resign"))
                rec("http leave", call("POST", "/chess/api/leave?token=$aw"))
                rec("http leave en GET", call("GET", "/chess/api/leave?token=$aw"))
            } finally { close() }
        }
        // ---- 2. flux d'évènements (première trame) et verrouillage après dix codes faux
        Rig(8).run {
            try {
                val r = room!!
                val j = call("POST", "/chess/api/join?code=${r.code}&name=Awa"); rec("http sse : entrée", j)
                val c = URL("$base/chess/api/events?token=${tokenOf(j)}").openConnection() as HttpURLConnection
                c.readTimeout = 5_000
                val lines = ArrayList<String>()
                c.inputStream.bufferedReader().let { rd -> while (lines.size < 3) lines += rd.readLine() ?: break }
                rec("http sse : en-têtes", "${c.responseCode} ${c.getHeaderField("Content-Type")} ${c.getHeaderField("Cache-Control")} ${c.getHeaderField("X-Accel-Buffering")}")
                rec("http sse : trame", lines.joinToString(" | "))
                c.disconnect()
                rec("http sse : flux inconnu", call("GET", "/chess/api/events?token=forged"))
                val codes = (1..11).map { call("POST", "/chess/api/join?code=bad$it&name=Awa").substringBefore(' ') }
                rec("http dix codes faux puis verrou", codes.joinToString(","))
                rec("http verrouillé : même le bon code", call("POST", "/chess/api/join?code=${r.code}&name=Zoe"))
            } finally { close() }
        }
        // ---- 3. aucune salle, puis salle fermée
        Rig(null).run {
            try {
                rec("http sans salle : hello", call("GET", "/chess/api/hello"))
                rec("http sans salle : entrée", call("POST", "/chess/api/join?code=1234&name=Awa"))
                rec("http sans salle : état", call("GET", "/chess/api/state?token=x"))
                rec("http sans salle : act", call("POST", "/chess/api/act?token=x&action=resign"))
                rec("http sans salle : flux", call("GET", "/chess/api/events?token=x"))
                room = newRoom(9).also { it.close() }
                rec("http salle fermée : hello", call("GET", "/chess/api/hello"))
                rec("http salle fermée : entrée", call("POST", "/chess/api/join?code=1234&name=Awa"))
            } finally { close() }
        }
        return out
    }
}
