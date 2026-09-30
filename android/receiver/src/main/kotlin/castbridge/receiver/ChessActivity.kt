package castbridge.receiver

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import castbridge.core.chess.ChessAi
import castbridge.core.chess.ChessRelayClient
import castbridge.core.chess.ChessRoom
import castbridge.core.chess.ChessSession
import castbridge.core.chess.ClockMode
import castbridge.core.chess.MoveTimer
import castbridge.core.quiz.QrCode

/**
 * « Échecs » on the TV, played with the remote: arrows move a cursor on the board, OK takes then puts a piece down,
 * BACK cancels the selection or opens the menu. Solo against the computer (levels 1-8), two players at the TV, the
 * remote against a phone, two phones (the TV shows the game), and Internet play through the relay (behind a flag,
 * docs/CHESS.md). The TV hosts the local room ([ChessHub], [ChessRoom]): it checks every move and keeps the clock.
 * Everything is drawn on one Canvas ([ChessTvView]), sized from the screen's pixels.
 */
class ChessActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val st = ChessTvState()
    private lateinit var view: ChessTvView
    private var room: ChessRoom? = null
    private var drive: Drive? = null
    private var renderPosted = false
    private var tone: ToneGenerator? = null
    private var lastBeep = -1L
    private var endShownFor: Any? = null
    private var drawAskedFor: String? = null
    private val prefs by lazy { getSharedPreferences("castbridge_chess", MODE_PRIVATE) }

    /** Who the TV plays against. */
    private enum class Opp(val label: String) {
        AI("Ordinateur"), TV2("Deux joueurs sur la TV"), PHONE1("Un joueur sur téléphone"), PHONES("Deux téléphones (la TV affiche)"), ONLINE("En ligne (Internet)")
    }
    private enum class Side(val label: String) { WHITE("Blancs"), BLACK("Noirs"), RANDOM("Au hasard") }

    private var opp = Opp.AI
    private var level = 3
    private var side = Side.WHITE
    private var seconds = MoveTimer.DEFAULT_SECONDS
    private var mode = ClockMode.COMPETITION
    private var sound = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = ChessTvView(this, st)
        setContentView(view)
        loadSettings()
        room = ChessHub.open().also { r -> r.onChange = { postRender() } }
        runCatching { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 22) }
        showSetup()
        main.post(ticker)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        drive?.close()
        room?.onChange = null
        ChessHub.close(room)
        tone?.release()
        super.onDestroy()
    }

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { onTick() }
            main.postDelayed(this, 200)
        }
    }

    private fun postRender() { if (!renderPosted) { renderPosted = true; main.post { renderPosted = false; render() } } }

    // ------------------------------------------------------------------ settings

    private fun loadSettings() {
        opp = runCatching { Opp.valueOf(prefs.getString("opp", Opp.AI.name)!!) }.getOrDefault(Opp.AI)
        level = prefs.getInt("level", 3).coerceIn(1, 8)
        side = runCatching { Side.valueOf(prefs.getString("side", Side.WHITE.name)!!) }.getOrDefault(Side.WHITE)
        seconds = MoveTimer.clamp(prefs.getInt("seconds", MoveTimer.DEFAULT_SECONDS))
        mode = runCatching { ClockMode.valueOf(prefs.getString("mode", ClockMode.COMPETITION.name)!!) }.getOrDefault(ClockMode.COMPETITION)
        sound = prefs.getBoolean("sound", true)
    }

    private fun saveSettings() {
        prefs.edit().putString("opp", opp.name).putInt("level", level).putString("side", side.name).putInt("seconds", seconds)
            .putString("mode", mode.name).putBoolean("sound", sound).apply()
    }

    private fun levelLabel(n: Int) = "$n — " + when (n) { 1, 2 -> "débutant"; 3, 4 -> "facile"; 5, 6 -> "moyen"; else -> "fort" }

    private fun <T> cycle(values: Array<T>, cur: T, d: Int): T = values[(values.indexOf(cur) + d + values.size) % values.size]

    // ------------------------------------------------------------------ screens

    private fun showSetup() {
        drive?.close(); drive = null
        room?.backToLobby()
        st.screen = ChessTvState.Screen.SETUP
        st.overlay = null; st.promo = null; st.selected = null; st.lobby = null
        val r = room
        st.footer = r?.let { ChessHub.joinUrl(it) }?.let { "Pour regarder ou jouer avec un téléphone : ${it.substringBefore("?")} — code ${r.code}" }
        st.menu = ChessMenu("Échecs", "Réglez la partie avec ‹ ›, puis « Commencer »", listOf(
            ChessItem("Adversaire", { opp.label }, change = { d -> opp = cycle(Opp.values(), opp, d); saveSettings(); showSetupKeepFocus() }),
            ChessItem("Niveau", { levelLabel(level) }, enabled = opp == Opp.AI, change = { d -> level = ((level - 1 + d + 8) % 8) + 1; saveSettings() }),
            ChessItem("Votre couleur", { side.label }, enabled = opp == Opp.AI || opp == Opp.PHONE1 || opp == Opp.ONLINE,
                change = { d -> side = cycle(Side.values(), side, d); saveSettings() }),
            ChessItem("Compte à rebours", { "$seconds s par coup" }, change = { d -> seconds = MoveTimer.next(seconds, d); saveSettings() }),
            ChessItem("Temps dépassé", { if (mode == ClockMode.COMPETITION) "partie perdue" else "coup joué d'office" },
                change = { mode = if (mode == ClockMode.COMPETITION) ClockMode.PRACTICE else ClockMode.COMPETITION; saveSettings() }),
            ChessItem("Son du compte à rebours", { if (sound) "activé" else "coupé" }, change = { sound = !sound; saveSettings() }),
            ChessItem("Commencer la partie", ok = { startGame() }),
            ChessItem("Quitter les échecs", ok = { finish() }),
        )) { finish() }
        view.invalidate()
    }

    /** Rebuilds the setup menu (items enabled depend on the opponent) keeping the focus on the same row. */
    private fun showSetupKeepFocus() { val f = st.menu?.focus ?: 0; showSetup(); st.menu?.focus = f }

    private fun startGame() {
        st.menu = null
        when (opp) {
            Opp.ONLINE -> startOnline()
            else -> startLocal()
        }
    }

    private fun humanColor(): Int = when (side) { Side.WHITE -> 0; Side.BLACK -> 1; Side.RANDOM -> (SystemClock.uptimeMillis() % 2).toInt() }

    private fun startLocal() {
        val r = room ?: return
        val h = humanColor()
        val seats = arrayOf(ChessRoom.Seat.REMOTE, ChessRoom.Seat.REMOTE)
        when (opp) {
            Opp.AI -> seats[h xor 1] = ChessRoom.Seat.AI
            Opp.TV2 -> {}
            Opp.PHONE1 -> seats[h xor 1] = ChessRoom.Seat.PHONE
            Opp.PHONES -> { seats[0] = ChessRoom.Seat.PHONE; seats[1] = ChessRoom.Seat.PHONE }
            Opp.ONLINE -> return
        }
        r.backToLobby()
        r.configure(seats[0], seats[1], level, seconds, mode)
        drive = LocalDrive(r)
        st.flipped = opp != Opp.TV2 && opp != Opp.PHONES && h == 1
        if (r.missing() != null) showLobby() else { r.start()?.let { flash(it) }; enterGame() }
    }

    private var lobbyReady: Boolean? = null

    private fun showLobby() {
        st.screen = ChessTvState.Screen.LOBBY
        lobbyReady = null
        render()
    }

    /** The lobby's actions; « Commencer » only once the phone players are there (then it takes the focus). */
    private fun lobbyMenu(r: ChessRoom, ready: Boolean) {
        st.menu = ChessMenu("", null, listOf(
            ChessItem("Commencer la partie", enabled = ready, ok = { r.start()?.let { flash(it) } ?: enterGame() }),
            ChessItem("Échanger les couleurs", ok = { r.swapColors(); if (opp == Opp.PHONE1) st.flipped = !st.flipped; render() }),
            ChessItem("Retour aux réglages", ok = { showSetup() }),
        )) { showSetup() }
    }

    private fun lobbyFor(r: ChessRoom, s: Map<String, Any?>): ChessLobby {
        val url = ChessHub.joinUrl(r)
        fun seat(k: String, name: String): Pair<String, Boolean> {
            val m = s[k] as? Map<*, *> ?: emptyMap<String, Any?>()
            val kind = m["kind"] as? String
            val who = m["name"] as? String ?: ""
            val ok = kind != "PHONE" || (m["playerId"] != null && m["connected"] == true)
            return "$name : $who" to ok
        }
        val lines = listOf(seat("white", "Blancs"), seat("black", "Noirs"), "Spectateurs : ${s["spectators"] ?: 0}" to true)
        return ChessLobby("Partie avec les téléphones", r.code, url?.substringBefore("?")?.let { "ou ouvrez $it" }, url?.let { runCatching { QrCode.encode(it) }.getOrNull() },
            lines, r.missing())
    }

    private fun enterGame() {
        st.screen = ChessTvState.Screen.GAME
        st.menu = null; st.overlay = null; st.promo = null; st.selected = null
        st.cursor = if (st.flipped) "e7" else "e2"
        endShownFor = null; drawAskedFor = null
        // in the game, a short line for spectators (the full address is on the setup screen)
        st.footer = room?.takeIf { drive is LocalDrive && ChessHub.joinUrl(it) != null }?.let { "Regarder sur un téléphone : code ${it.code}" }
        render()
    }

    // ------------------------------------------------------------------ online (Internet relay)

    private fun startOnline() {
        val relay = ChessHub.relay(this)
        if (!relay.enabled) {
            showSetup()
            flash("Le jeu en ligne arrive bientôt : le serveur CastBridge n'a pas encore ouvert les parties d'échecs.")
            return
        }
        st.menu = ChessMenu("En ligne", "Créer une partie ou rejoindre celle d'un ami", listOf(
            ChessItem("Créer une partie (vous recevez un code)", ok = { createOnline(relay) }),
            ChessItem("Rejoindre avec un code", ok = { enterCode(relay) }),
            ChessItem("Retour", ok = { showSetup() }),
        )) { showSetup() }
        view.invalidate()
    }

    private fun createOnline(relay: ChessRelayClient) {
        st.menu = null; flash("Connexion au serveur…")
        val color = when (side) { Side.WHITE -> "white"; Side.BLACK -> "black"; Side.RANDOM -> "random" }
        Thread {
            val res = runCatching { relay.create("TV ${android.os.Build.MODEL}".take(16), seconds, color, mode) }
            main.post { res.onSuccess { onlineJoined(relay, it) }.onFailure { showSetup(); flash("En ligne impossible : ${it.message}") } }
        }.start()
    }

    private val codeChars = CharArray(6) { 'A' }

    private fun enterCode(relay: ChessRelayClient) {
        val a = ChessRelayClient.CODE_ALPHABET
        st.menu = ChessMenu("Code de la partie", "‹ › : changer le caractère", List(6) { i ->
            ChessItem("Caractère ${i + 1}", { codeChars[i].toString() }, change = { d -> codeChars[i] = a[(a.indexOf(codeChars[i]) + d + a.length) % a.length] })
        } + ChessItem("Rejoindre", ok = {
            val code = String(codeChars)
            st.menu = null; flash("Connexion…")
            Thread {
                val res = runCatching { relay.join(code, "TV ${android.os.Build.MODEL}".take(16), prefs.getString("online_token_$code", null)) }
                main.post { res.onSuccess { onlineJoined(relay, it) }.onFailure { showSetup(); flash("Impossible de rejoindre : ${it.message}") } }
            }.start()
        })) { startOnline() }
        view.invalidate()
    }

    private fun onlineJoined(relay: ChessRelayClient, s: ChessSession) {
        prefs.edit().putString("online_token_${s.code}", s.token).apply()
        drive?.close()
        drive = OnlineDrive(relay, s)
        st.flipped = s.color == "b"
        st.screen = ChessTvState.Screen.LOBBY
        st.lobby = ChessLobby("Partie en ligne", s.code, "Votre ami choisit « En ligne > Rejoindre » et saisit ce code", null,
            listOf("Vous jouez les ${if (s.color == "b") "Noirs" else "Blancs"}" to true), "En attente de l'adversaire…")
        st.menu = ChessMenu("", null, listOf(ChessItem("Annuler", ok = { showSetup() }))) { showSetup() }
        render()
    }

    // ------------------------------------------------------------------ rendering

    @Suppress("UNCHECKED_CAST")
    private fun render() {
        val d = drive
        val s = d?.view() ?: room?.view(null) ?: emptyMap()
        st.s = s; st.sAt = SystemClock.uptimeMillis()
        st.remoteColors = d?.remoteColors() ?: emptySet()
        val stage = s["stage"] as? String
        when (st.screen) {
            ChessTvState.Screen.LOBBY -> {
                val r = room
                if (d is LocalDrive && r != null) {
                    st.lobby = lobbyFor(r, s)
                    val ready = r.missing() == null
                    if (ready != lobbyReady) { lobbyReady = ready; lobbyMenu(r, ready) }
                }
                if (stage == "PLAYING") enterGame()
            }
            ChessTvState.Screen.GAME -> {
                if ((s["legal"] as? List<*>).isNullOrEmpty()) st.selected = null
                // the end of the game: once
                val gameKey = d?.gameKey()
                if (stage == "FINISHED" && endShownFor != gameKey && st.overlay == null) { endShownFor = gameKey; endMenu(s) }
                // a phone offers a draw to the remote
                val offer = s["drawOffer"] as? String
                if (offer != null && stage == "PLAYING" && st.remoteColors.isNotEmpty() && offer !in st.remoteColors && drawAskedFor != "$offer${s["ply"]}" && st.overlay == null) {
                    drawAskedFor = "$offer${s["ply"]}"
                    st.overlay = ChessMenu("Proposition de nulle", "Votre adversaire propose de finir sur une partie nulle", listOf(
                        ChessItem("Accepter la nulle", ok = { st.overlay = null; d?.answerDraw(true) }),
                        ChessItem("Refuser et continuer", ok = { st.overlay = null; d?.answerDraw(false) }),
                    )) { st.overlay = null; d?.answerDraw(false) }
                }
            }
            ChessTvState.Screen.SETUP -> {}
        }
        view.invalidate()
    }

    private var pausedNow = false

    /** A menu over a game played at the TV alone stops its clock; closing it restarts the clock. */
    private fun syncPause() {
        val want = st.screen == ChessTvState.Screen.GAME && st.overlay != null && st.s["stage"] == "PLAYING" && drive?.canPause == true
        if (want != pausedNow) { pausedNow = want; drive?.setPaused(want); render() }
    }

    private fun onTick() {
        syncPause()
        val s = st.s
        if (st.screen == ChessTvState.Screen.GAME && s["stage"] == "PLAYING") {
            // countdown beep, once per second under 10 s, only for a player at the TV
            val clock = s["clock"] as? Map<*, *>
            val left = ((clock?.get("remainingMs") as? Number)?.toLong() ?: Long.MAX_VALUE) - (SystemClock.uptimeMillis() - st.sAt)
            val secs = (left + 999) / 1000
            if (sound && left in 1..9_999 && s["turn"] in st.remoteColors && secs != lastBeep) {
                lastBeep = secs; runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
            }
            // the local room updates itself (onChange); the drawing needs the clocks refreshed
            if (drive is LocalDrive && left <= 0) render()
        }
        view.invalidate()
    }

    private fun flash(msg: String) { st.flash = msg; st.flashUntil = SystemClock.uptimeMillis() + 4000; view.invalidate() }

    // ------------------------------------------------------------------ menus in the game

    private fun pauseMenu() {
        val d = drive ?: return
        val over = st.s["stage"] != "PLAYING"
        val items = ArrayList<ChessItem>()
        items += ChessItem("Continuer la partie", ok = { st.overlay = null; view.invalidate() })
        if (d.canUndo) items += ChessItem("Annuler le dernier coup", ok = { st.overlay = null; if (!d.undo()) flash("Rien à annuler") })
        if (!over && st.remoteColors.isNotEmpty()) {
            items += ChessItem("Proposer la nulle", ok = { st.overlay = null; d.offerDraw() })
            items += ChessItem("Abandonner", ok = { confirm("Abandonner la partie ?", "La partie sera perdue.") { d.resign() } })
        }
        items += ChessItem("Nouvelle partie (réglages)", ok = { if (over) showSetup() else confirm("Nouvelle partie ?", "La partie en cours sera arrêtée.") { showSetup() } })
        items += ChessItem("Quitter les échecs", ok = { if (over) finish() else confirm("Quitter les échecs ?", "La partie en cours sera arrêtée.") { finish() } })
        val clockNote = if (over) "$seconds s par coup · ${mode.rule}"
            else if (d.canPause) "Compte à rebours arrêté pendant la pause" else "Le compte à rebours continue : votre adversaire attend"
        st.overlay = ChessMenu("Pause", clockNote, items) { st.overlay = null; view.invalidate() }
        syncPause()
        view.invalidate()
    }

    private fun confirm(title: String, sub: String, action: () -> Unit) {
        st.overlay = ChessMenu(title, sub, listOf(
            ChessItem("Non, continuer", ok = { st.overlay = null; view.invalidate() }),
            ChessItem("Oui", ok = { st.overlay = null; action(); render() }),
        )) { st.overlay = null; view.invalidate() }
        view.invalidate()
    }

    private fun endMenu(s: Map<String, Any?>) {
        val d = drive
        @Suppress("UNCHECKED_CAST") val res = s["result"] as? Map<String, Any?>
        val winner = res?.get("winner") as? String
        val title = when {
            winner == null -> "Partie nulle"
            st.remoteColors.size == 1 -> if (winner in st.remoteColors) "Vous avez gagné !" else "Vous avez perdu"
            else -> if (winner == "w") "Les Blancs gagnent" else "Les Noirs gagnent"
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
        val items = ArrayList<ChessItem>()
        if (d is LocalDrive) items += ChessItem("Rejouer (mêmes réglages)", ok = { st.overlay = null; startLocal() })
        if (d?.canUndo == true) items += ChessItem("Annuler le dernier coup", ok = { st.overlay = null; d.undo() })
        items += ChessItem("Revoir l'échiquier", ok = { st.overlay = null; view.invalidate() })
        items += ChessItem("Nouvelle partie (réglages)", ok = { showSetup() })
        items += ChessItem("Quitter les échecs", ok = { finish() })
        st.overlay = ChessMenu(title, res?.get("text") as? String, items) { st.overlay = null; view.invalidate() }
    }

    // ------------------------------------------------------------------ remote control

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return when (event.keyCode) { KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> true; else -> super.dispatchKeyEvent(event) }
        val k = event.keyCode
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A
        val back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_BUTTON_B
        val dx = when (k) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
        val dy = when (k) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
        if (!ok && !back && dx == 0 && dy == 0 && k != KeyEvent.KEYCODE_MENU) return super.dispatchKeyEvent(event)
        st.flash = null
        // 1. promotion chooser
        st.promo?.let { moves ->
            when {
                dx != 0 -> st.promoFocus = (st.promoFocus + dx + moves.size) % moves.size
                ok -> { st.promo = null; drive?.move(moves[st.promoFocus]); st.selected = null }
                back -> st.promo = null
            }
            view.invalidate(); return true
        }
        // 2. menus (overlay first)
        (st.overlay ?: st.menu)?.let { m ->
            if (st.screen != ChessTvState.Screen.GAME || st.overlay != null) {
                val item = m.items.getOrNull(m.focus)
                when {
                    dy != 0 -> m.move(dy)
                    dx != 0 -> item?.takeIf { it.enabled }?.change?.invoke(dx)
                    ok -> item?.takeIf { it.enabled }?.let { (it.ok ?: it.change?.let { c -> { c(1) } })?.invoke() }
                    back -> m.onBack()
                    k == KeyEvent.KEYCODE_MENU && st.overlay == null && st.screen == ChessTvState.Screen.GAME -> pauseMenu()
                }
                view.invalidate(); return true
            }
        }
        // 3. the board
        if (st.screen == ChessTvState.Screen.GAME) {
            when {
                back -> if (st.selected != null) st.selected = null else pauseMenu()
                k == KeyEvent.KEYCODE_MENU -> pauseMenu()
                dx != 0 || dy != 0 -> moveCursor(dx, dy)
                ok -> { if (drive is LocalDrive) render(); onSquare(st.cursor) }
            }
            view.invalidate(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveCursor(dx: Int, dy: Int) {
        var f = st.cursor[0] - 'a'; var r = st.cursor[1] - '1'
        // screen directions: up = towards the top of the screen
        val sx = if (st.flipped) -dx else dx
        val sr = if (st.flipped) dy else -dy
        f = (f + sx).coerceIn(0, 7); r = (r + sr).coerceIn(0, 7)
        st.cursor = "${'a' + f}${'1' + r}"
    }

    @Suppress("UNCHECKED_CAST")
    private fun onSquare(sq: String) {
        val s = st.s
        if (s["stage"] != "PLAYING") { if (s["stage"] == "FINISHED") endMenu(s); return }
        val legal = (s["legal"] as? List<String>).orEmpty()
        if (legal.isEmpty()) {
            flash(if (s["thinking"] == true) "L'ordinateur réfléchit…" else "Ce n'est pas à la télécommande de jouer")
            return
        }
        val sel = st.selected
        if (sel != null) {
            val cands = legal.filter { it.startsWith(sel) && it.substring(2, 4) == sq }
            when {
                cands.size == 1 -> { st.selected = null; drive?.move(cands[0]); return }
                cands.size > 1 -> { st.promo = listOf("q", "r", "b", "n").mapNotNull { p -> cands.firstOrNull { it.endsWith(p) } }; st.promoFocus = 0; return }
                sq == sel -> { st.selected = null; return }
            }
        }
        st.selected = if (legal.any { it.startsWith(sq) }) sq else {
            if (sel != null) flash("Coup impossible : choisissez une case marquée d'un point")
            null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* handled in dispatchKeyEvent */ }

    // ------------------------------------------------------------------ drivers: the local room or the Internet relay

    private interface Drive {
        fun view(): Map<String, Any?>
        fun remoteColors(): Set<String>
        fun gameKey(): Any?
        fun move(uci: String)
        fun resign()
        fun offerDraw()
        fun answerDraw(accept: Boolean)
        fun undo(): Boolean
        /** Stops / restarts the countdown while a menu is open (only games played at the TV alone). */
        fun setPaused(p: Boolean) {}
        val canPause: Boolean get() = false
        val canUndo: Boolean
        fun close()
    }

    private inner class LocalDrive(val r: ChessRoom) : Drive {
        override fun view() = r.view(null)
        override fun remoteColors() = setOfNotNull(if (r.seats[0] == ChessRoom.Seat.REMOTE) "w" else null, if (r.seats[1] == ChessRoom.Seat.REMOTE) "b" else null)
        override fun gameKey(): Any? = r.game
        override fun move(uci: String) {
            when (r.hostMove(uci)) {
                ChessRoom.Act.ILLEGAL -> flash("Coup illégal")
                ChessRoom.Act.NOT_YOUR_TURN -> flash("Ce n'est pas votre tour")
                else -> {}
            }
            render()      // the next key press must see the new position, not wait for the posted refresh
        }
        override fun resign() { r.hostResign() }
        override fun offerDraw() {
            val phone = (0..1).any { r.seats[it] == ChessRoom.Seat.PHONE }
            if (r.hostOfferDraw() && phone) flash("Nulle proposée : l'adversaire doit répondre sur son téléphone")
        }
        override fun answerDraw(accept: Boolean) { r.hostAnswerDraw(accept) }
        override fun undo() = r.hostUndo()
        override val canUndo get() = r.seats.contains(ChessRoom.Seat.AI) && r.seats.contains(ChessRoom.Seat.REMOTE)
        override fun setPaused(p: Boolean) { r.hostPause(p) }
        override val canPause get() = r.canPause
        override fun close() {}
    }

    /** Internet game: polls the relay (long-poll) on a background thread; commands also go off the UI thread. */
    private inner class OnlineDrive(val client: ChessRelayClient, val session: ChessSession) : Drive {
        @Volatile private var latest: Map<String, Any?> = emptyMap()
        @Volatile private var running = true
        private val poller = Thread {
            var since = 0L; var errors = 0
            while (running) {
                try {
                    val s = client.state(session, since, 20)
                    since = (s["v"] as? Number)?.toLong() ?: since
                    latest = s; errors = 0; postRender()
                } catch (e: Exception) {
                    if (!running) break
                    errors++
                    if (errors == 3) main.post { flash("Connexion au serveur perdue, nouvel essai…") }
                    Thread.sleep(minOf(10_000L, 1_000L * errors))
                }
            }
        }.apply { isDaemon = true; start() }

        override fun view() = latest
        override fun remoteColors() = setOfNotNull(session.color)
        override fun gameKey(): Any? = session.code
        private fun send(what: () -> castbridge.core.chess.ChessAct) {
            Thread {
                val r = runCatching(what)
                main.post {
                    r.onSuccess { a -> a.state?.let { latest = it }; if (a.result == "ILLEGAL") flash("Coup refusé par le serveur"); render() }
                        .onFailure { flash("Envoi impossible : ${it.message}") }
                }
            }.start()
        }
        override fun move(uci: String) { val ply = (latest["ply"] as? Number)?.toInt() ?: 0; send { client.move(session, uci, ply) } }
        override fun resign() = send { client.resign(session) }
        override fun offerDraw() = send { client.draw(session, "offer") }
        override fun answerDraw(accept: Boolean) = send { client.draw(session, if (accept) "accept" else "decline") }
        override fun undo() = false
        override val canUndo = false
        override fun close() { running = false; poller.interrupt(); Thread { client.leave(session) }.start() }
    }

    companion object {
        /** The computer's thinking time for a countdown (shown nowhere, kept for the logs). */
        fun aiAllowance(seconds: Int) = ChessAi.allowance(MoveTimer.clamp(seconds) * 1000L)
    }
}
