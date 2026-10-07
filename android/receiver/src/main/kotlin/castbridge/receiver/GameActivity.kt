package castbridge.receiver

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import castbridge.core.games.ActResult
import castbridge.core.games.GameCatalog
import castbridge.core.games.RulesRoom
import castbridge.core.games.SeatKind
import castbridge.core.quiz.QrCode

/**
 * Un jeu de cartes de la plateforme commune (docs/GAMES.md) sur la TV, joué à la télécommande, avec les téléphones de la maison (page web `/jeux/<id>`, QR code) ou contre l'ordinateur.
 * Aujourd'hui : la Bataille (démonstration). La TV héberge la salle ([GameRoomHost], [RulesRoom]) : elle tire la graine, vérifie chaque coup avec les règles et tient la pendule ; les
 * téléphones n'envoient que des coups. Tout est dessiné sur un seul Canvas ([GameTvView]) à partir des pixels de l'écran.
 *
 * Télécommande : OK = jouer (retourner la carte) quand c'est à elle ; RETOUR ou MENU = menu de la partie ; dans les menus haut/bas = choisir, ‹ › = changer la valeur, OK = valider.
 * Un jeu « bientôt » (règles en attente du propriétaire) ne s'ouvre pas : [GameCatalog.newRoom] n'en donne pas.
 */
class GameActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val st = GameTvState()
    private lateinit var view: GameTvView
    private var room: RulesRoom<*, *>? = null
    private var entry: GameCatalog.Entry = GameCatalog.BATAILLE
    private var renderPosted = false
    private var lobbyReady: Boolean? = null
    private var endShownFor: String? = null
    private var pausedNow = false
    private val prefs by lazy { getSharedPreferences("castbridge_games", MODE_PRIVATE) }

    /** Qui joue : la télécommande contre l'ordinateur, deux téléphones (la TV affiche), ou la télécommande contre un téléphone. */
    private enum class Mode(val label: String) {
        SOLO("Solo : vous contre l'ordinateur"), PHONES("Avec les téléphones (la TV affiche)"), TV_PHONE("La télécommande contre un téléphone")
    }
    private var mode = Mode.SOLO

    private fun kindsFor(m: Mode, seats: Int): List<SeatKind> = when (m) {
        Mode.SOLO -> List(seats) { if (it == 0) SeatKind.REMOTE else SeatKind.AI }
        Mode.PHONES -> List(seats) { SeatKind.PHONE }
        Mode.TV_PHONE -> List(seats) { if (it == 0) SeatKind.REMOTE else SeatKind.PHONE }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val e = GameCatalog.entry(intent?.getStringExtra(EXTRA_GAME) ?: GameCatalog.BATAILLE.id)?.takeIf { it.playable }
        val r = e?.let { GameRoomHost.open(it.id) }
        if (e == null || r == null) { Toast.makeText(this, GameCatalog.SOON, Toast.LENGTH_LONG).show(); finish(); return }     // « bientôt » : aucune règle, aucune salle
        entry = e; room = r
        view = GameTvView(this, st)
        setContentView(view)
        st.title = e.name
        mode = runCatching { Mode.valueOf(prefs.getString("${e.id}_mode", Mode.SOLO.name)!!) }.getOrDefault(Mode.SOLO)
        r.onChange = { postRender() }
        showSetup()
        main.post(ticker)
    }

    override fun onResume() { super.onResume(); GamesHub.foreground = this }
    override fun onPause() { if (GamesHub.foreground === this) GamesHub.foreground = null; super.onPause() }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        room?.onChange = null
        GameRoomHost.close(room)
        super.onDestroy()
    }

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { onTick() }
            main.postDelayed(this, 200)
        }
    }

    private fun postRender() { if (!renderPosted) { renderPosted = true; main.post { renderPosted = false; render() } } }

    private fun <T> cycle(values: Array<T>, cur: T, d: Int): T = values[(values.indexOf(cur) + d + values.size) % values.size]

    // ------------------------------------------------------------------ screens

    private fun showSetup() {
        val r = room ?: return
        r.backToLobby()
        st.screen = GameTvState.Screen.SETUP
        st.overlay = null; st.lobby = null; lobbyReady = null
        st.footer = GameRoomHost.joinUrl(r)?.let { "Téléphones : ouvrez ${it.substringBefore("?")} puis saisissez le code ${r.code}" }
        st.menu = ChessMenu(entry.name, "Réglez la partie avec ‹ ›, puis « Commencer »", listOf(
            ChessItem("Mode", { mode.label }, change = { d -> mode = cycle(Mode.values(), mode, d); prefs.edit().putString("${entry.id}_mode", mode.name).apply() }),
            ChessItem("Commencer la partie", ok = { startGame() }),
            ChessItem("Quitter ${entry.name}", ok = { finish() }),
        )) { finish() }
        render()
    }

    private fun startGame() {
        val r = room ?: return
        st.menu = null
        r.configure(kindsFor(mode, r.rules.minPlayers))
        if (r.missing() != null) { st.screen = GameTvState.Screen.LOBBY; lobbyReady = null; render() }
        else { r.start()?.let { flash(it) }; enterGame() }
    }

    /** The lobby's actions; « Commencer » only once the phone players are there. */
    private fun lobbyMenu(r: RulesRoom<*, *>, ready: Boolean) {
        st.menu = ChessMenu("", null, listOf(
            ChessItem("Commencer la partie", enabled = ready, ok = { r.start()?.let { flash(it) } ?: enterGame() }),
            ChessItem("Retour aux réglages", ok = { showSetup() }),
        )) { showSetup() }
    }

    private fun lobbyFor(r: RulesRoom<*, *>, s: Map<String, Any?>): ChessLobby {
        val url = GameRoomHost.joinUrl(r)
        val seats = (s["seats"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val lines = seats.map { m ->
            val ok = m["kind"] != "PHONE" || (m["playerId"] != null && m["connected"] == true)
            "Place ${((m["index"] as? Number)?.toInt() ?: 0) + 1} : ${m["name"] ?: ""}" to ok
        } + ("Spectateurs : ${s["spectators"] ?: 0}" to true)
        return ChessLobby(entry.name, r.code, url?.substringBefore("?")?.let { "ou ouvrez $it" }, url?.let { runCatching { QrCode.encode(it) }.getOrNull() }, lines, r.missing())
    }

    private fun enterGame() {
        val r = room ?: return
        st.screen = GameTvState.Screen.GAME
        st.menu = null; st.overlay = null
        endShownFor = null
        st.footer = when (mode) {
            Mode.SOLO -> "OK : retourner votre carte · Retour : menu de la partie · Pour regarder : code ${r.code}"
            Mode.PHONES -> "Chaque joueur retourne sa carte sur son téléphone · Retour : menu de la partie · Code ${r.code}"
            Mode.TV_PHONE -> "OK : retourner votre carte (l'autre joueur est sur son téléphone) · Retour : menu · Code ${r.code}"
        }
        render()
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val r = room ?: return
        val s = r.view(null)
        st.s = s; st.sAt = SystemClock.uptimeMillis()
        val stage = s["stage"] as? String
        when (st.screen) {
            GameTvState.Screen.LOBBY -> {
                st.lobby = lobbyFor(r, s)
                val ready = r.missing() == null
                if (ready != lobbyReady) { lobbyReady = ready; lobbyMenu(r, ready) }
                if (stage == "PLAYING") enterGame()
            }
            GameTvState.Screen.GAME -> if (stage == "FINISHED" && endShownFor != r.gameId && st.overlay == null) { endShownFor = r.gameId; endMenu(s) }
            GameTvState.Screen.SETUP -> {}
        }
        view.invalidate()
    }

    /** A menu over a game played at the TV alone stops its clock; closing it restarts the clock. */
    private fun syncPause() {
        val r = room ?: return
        val want = st.screen == GameTvState.Screen.GAME && st.overlay != null && st.s["stage"] == "PLAYING" && r.canPause
        if (want != pausedNow) { pausedNow = want; r.hostPause(want); render() }
    }

    private fun onTick() {
        syncPause()
        if (st.screen == GameTvState.Screen.GAME && st.s["stage"] == "PLAYING") render()      // the countdowns and the « reprise possible » delays move
        view.invalidate()
    }

    private fun flash(msg: String) { st.flash = msg; st.flashUntil = SystemClock.uptimeMillis() + 4000; view.invalidate() }

    // ------------------------------------------------------------------ menus in the game

    @Suppress("UNCHECKED_CAST")
    private fun seats(): List<Map<String, Any?>> = (st.s["seats"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

    private fun pauseMenu() {
        val r = room ?: return
        val over = st.s["stage"] != "PLAYING"
        val remote = seats().any { it["kind"] == "REMOTE" }
        val items = ArrayList<ChessItem>()
        items += ChessItem("Continuer la partie", ok = { st.overlay = null; view.invalidate() })
        if (!over && remote) items += ChessItem("Abandonner", ok = { confirm("Abandonner la partie ?", "La partie sera perdue.") { r.hostResign() } })
        if (!over && !remote) items += ChessItem("Arrêter la partie", ok = { confirm("Arrêter la partie ?", "La partie s'arrête pour tous les joueurs.") { showSetup() } })
        items += ChessItem("Nouvelle partie (réglages)", ok = { if (over) showSetup() else confirm("Nouvelle partie ?", "La partie en cours sera arrêtée.") { showSetup() } })
        items += ChessItem("Quitter ${entry.name}", ok = { if (over) finish() else confirm("Quitter ${entry.name} ?", "La partie en cours sera arrêtée.") { finish() } })
        st.overlay = ChessMenu("Pause", if (over) null else if (r.canPause) "Le jeu est en pause" else "La partie continue : les joueurs sur téléphone attendent", items) { st.overlay = null; view.invalidate() }
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

    @Suppress("UNCHECKED_CAST")
    private fun endMenu(s: Map<String, Any?>) {
        val res = s["result"] as? Map<String, Any?>
        val winners = (res?.get("winners") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val mine = seats().firstOrNull { it["me"] == true }?.get("id") as? String
        val title = when {
            res == null || res["kind"] == "DRAW" -> "Partie nulle"
            mine != null -> if (mine in winners) "Vous avez gagné !" else "Vous avez perdu"
            else -> winners.firstOrNull()?.let { w -> (seats().firstOrNull { it["id"] == w }?.get("name") as? String ?: w) + " gagne" } ?: "Partie terminée"
        }
        val items = ArrayList<ChessItem>()
        items += ChessItem("Rejouer (mêmes réglages)", ok = { st.overlay = null; startGame() })
        items += ChessItem("Revoir la table", ok = { st.overlay = null; view.invalidate() })
        items += ChessItem("Réglages", ok = { showSetup() })
        items += ChessItem("Quitter ${entry.name}", ok = { finish() })
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
        (st.overlay ?: st.menu)?.let { m ->
            if (st.screen != GameTvState.Screen.GAME || st.overlay != null) {
                val item = m.items.getOrNull(m.focus)
                when {
                    dy != 0 -> m.move(dy)
                    dx != 0 -> item?.takeIf { it.enabled }?.change?.invoke(dx)
                    ok -> item?.takeIf { it.enabled }?.let { (it.ok ?: it.change?.let { c -> { c(1) } })?.invoke() }
                    back -> m.onBack()
                    k == KeyEvent.KEYCODE_MENU && st.overlay == null && st.screen == GameTvState.Screen.GAME -> pauseMenu()
                }
                view.invalidate(); return true
            }
        }
        if (st.screen == GameTvState.Screen.GAME) {
            when {
                back || k == KeyEvent.KEYCODE_MENU -> pauseMenu()
                ok -> play()
            }
            view.invalidate(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** OK: the remote plays its only legal move (« retourner »). A game that asks for a choice needs its own screen (not yet). */
    private fun play() {
        val r = room ?: return
        val s = st.s
        if (s["stage"] == "FINISHED") { endMenu(s); return }
        if (s["stage"] != "PLAYING") return
        val legal = (s["legal"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        when {
            seats().none { it["me"] == true } -> flash("Les joueurs jouent avec leur téléphone")
            legal.isEmpty() -> if (seats().none { it["toMove"] == true && it["kind"] == "AI" }) flash("Ce n'est pas encore à vous de jouer")     // l'ordinateur joue : la phrase d'état le dit déjà (pas de message si on garde OK enfoncé)
            legal.size > 1 -> flash("Ce jeu demande un choix : pas encore possible à la télécommande")
            else -> when (r.hostMove(legal[0])) {
                ActResult.ILLEGAL -> flash("Coup illégal")
                ActResult.NOT_YOUR_TURN -> flash("Ce n'est pas votre tour")
                else -> {}
            }
        }
        render()        // the next key press must see the new state, not wait for the posted refresh
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* handled in dispatchKeyEvent */ }

    companion object {
        const val EXTRA_GAME = "game"
    }
}
