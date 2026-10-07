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
import castbridge.core.chess.ChessAct
import castbridge.core.chess.ChessAi
import castbridge.core.chess.ChessRoom
import castbridge.core.chess.ClockMode
import castbridge.core.chess.MoveTimer
import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessOnlineTexts
import castbridge.core.chess.online.ChessOnlineTile
import castbridge.core.chess.online.ChessStakeChoice
import castbridge.core.chess.online.StakeAvailability
import castbridge.core.quiz.QrCode
import castbridge.core.quiz.online.LinkAction
import castbridge.core.quiz.online.PlayTvName
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletView
import castbridge.receiver.quiz.PlayHub
import castbridge.receiver.wallet.WalletActivity
import castbridge.receiver.wallet.WalletHub

/**
 * « Échecs » on the TV, played with the remote: arrows move a cursor on the board, OK takes then puts a piece down,
 * BACK cancels the selection or opens the menu. Solo against the computer (levels 1-8), two players at the TV, the
 * remote against a phone, two phones (the TV shows the game), and Internet play, free or with a stake of NDEM / MBOKO,
 * against another TV on the online service `castbridge-play` (docs/CHESS.md § 6, [ChessOnlineHub]). The TV hosts the local
 * room ([ChessHub], [ChessRoom]): it checks every move and keeps the clock; online, the SERVICE does. Everything is drawn on one Canvas
 * ([ChessTvView]), sized from the screen's pixels. No decision is taken here: gates, stakes and texts are the core's (tested).
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
        // an Internet game lives in the app, not in this screen: if the system recreated the screen, we come back to the game (no forfeit)
        ChessOnlineHub.game?.takeIf { it.session != null && it.view()["stage"] != "CLOSED" }?.let { attachOnline(it) }
        main.post(ticker)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        val d = drive
        if (d is OnlineDrive && !isFinishing) d.detach()      // the system destroyed the screen: the Internet game goes on (the 60 s forfeit is for a lost connection, not for this)
        else { recordGame(null); d?.close() }                 // left in the middle of a game: abandon (an Internet game is resigned or cancelled properly)
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
        recordGame(null)
        drive?.close(); drive = null; online = null
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
        gameStartAt = SystemClock.uptimeMillis(); gameRecorded = false
        st.screen = ChessTvState.Screen.GAME
        st.menu = null; st.overlay = null; st.promo = null; st.selected = null
        st.cursor = if (st.flipped) "e7" else "e2"
        endShownFor = null; drawAskedFor = null
        // in the game, a short line for spectators (the full address is on the setup screen)
        st.footer = room?.takeIf { drive is LocalDrive && ChessHub.joinUrl(it) != null }?.let { "Regarder sur un téléphone : code ${it.code}" }
        render()
    }

    // ------------------------------------------------------------------ online (castbridge-play: TV against TV, free or with a stake)

    private var online: ChessOnlineGame? = null
    private var choice: ChessStakeChoice? = null

    /** « Échecs › En ligne » : la porte (activation, Internet, profil, service) est celle du cœur ; une raison s'affiche toujours, jamais un écran vide. */
    private fun startOnline() {
        when (val tile = ChessOnlineHub.tile(this)) {
            ChessOnlineTile.Hidden -> { showSetup(); flash("Les parties en ligne sont désactivées dans les réglages de la TV."); return }
            is ChessOnlineTile.Blocked -> { showSetup(); flash(tile.reason); return }
            ChessOnlineTile.Available -> {}
        }
        ChessOnlineHub.prepare(this)
        // what the service says it hosts (chess, stakes) comes from its caps page: if it turns out not to host chess, the menu says so instead of failing later
        PlayHub.probe { main.post { if (st.menu?.title == "En ligne" && ChessOnlineHub.tile(this) is ChessOnlineTile.Blocked) startOnline() } }
        Thread { runCatching { ChessOnlineHub.retryPendingSettlements(this) } }.apply { isDaemon = true }.start()   // règlements restés en attente faute de connexion
        val saved = ChessOnlineHub.savedSeat(this)
        val items = ArrayList<ChessItem>()
        items += ChessItem("Créer une partie (vous recevez un code)", ok = { showCreateOnline(fresh = true) })
        items += ChessItem("Rejoindre avec un code", ok = { enterCode(spectate = false) })
        items += ChessItem("Regarder une partie avec un code", ok = { enterCode(spectate = true) })
        if (saved != null) items += ChessItem("Reprendre la partie en cours", ok = { resumeOnline() })
        items += ChessItem("Retour", ok = { showSetup() })
        st.menu = ChessMenu("En ligne", ChessOnlineHub.networkLine() ?: "Créer une partie ou rejoindre celle d'un ami", items) { showSetup() }
        view.invalidate()
    }

    private fun playerName() = PlayTvName.of(android.os.Build.MODEL)

    private fun balanceOf(cur: String): Long? = if (cur == "NDEM") ChessOnlineHub.balanceNdem() else ChessOnlineHub.balanceMboko()

    /** Créer : libre ou avec mise (monnaie, montant parmi l'échelle du serveur, solde), puis couleur, compte à rebours ; une mise demande une confirmation. */
    private fun showCreateOnline(fresh: Boolean = false, focus: Int = 0) {
        val c = (if (fresh) null else choice) ?: ChessStakeChoice(ChessOnlineHub.availability(), ChessOnlineHub.balanceNdem(), ChessOnlineHub.balanceMboko())
        choice = c
        val staked = c.stake() != null
        val items = ArrayList<ChessItem>()
        items += ChessItem("Mise", { c.modeText() }, enabled = c.modes.size > 1, change = { d ->
            c.cycleMode(d)
            if (c.stake() != null) flash(ChessOnlineTexts.ABANDON_WARNING)
            showCreateOnline(focus = 0)
        })
        if (staked) {
            items += ChessItem("Montant", { c.amountText() }, change = { d -> c.cycleAmount(d); showCreateOnline(focus = 1) })
            c.balanceLine()?.let { line -> items += ChessItem("Solde", { line.substringAfter(": ") }, enabled = false) }
        }
        items += ChessItem("Votre couleur", { side.label }, change = { d -> side = cycle(Side.values(), side, d); saveSettings() })
        items += ChessItem("Compte à rebours", { "$seconds s par coup" }, change = { d -> seconds = MoveTimer.next(seconds, d); saveSettings() })
        items += ChessItem("Temps dépassé", { if (staked || mode == ClockMode.COMPETITION) "partie perdue" else "coup joué d'office" }, enabled = !staked,
            change = { mode = if (mode == ClockMode.COMPETITION) ClockMode.PRACTICE else ClockMode.COMPETITION; saveSettings() })
        items += ChessItem("Créer la partie", ok = { askCreate(c) })
        items += ChessItem("Retour", ok = { startOnline() })
        val sub = c.potLine() ?: c.freeOnlyReason() ?: "Partie libre : rien n'est en jeu"
        st.menu = ChessMenu("Créer une partie en ligne", sub, items) { startOnline() }
        st.menu?.focus = focus.coerceIn(0, items.size - 1)
        view.invalidate()
    }

    private fun askCreate(c: ChessStakeChoice) {
        val stake = c.stake()
        if (stake == null) { createOnlineNow(null); return }
        if (!c.affordable()) { flash(ChessOnlineTexts.cannotAfford(stake.cur, balanceOf(stake.cur) ?: 0)); return }
        st.overlay = ChessMenu(ChessOnlineTexts.CREATE_STAKE_TITLE, ChessOnlineTexts.stakeSubtitle(stake, balanceOf(stake.cur)), listOf(
            ChessItem("Non, revenir", ok = { st.overlay = null; view.invalidate() }),
            ChessItem("Oui, bloquer ma mise et créer", ok = { st.overlay = null; createOnlineNow(stake) }),
        )) { st.overlay = null; view.invalidate() }
        flash(ChessOnlineTexts.ABANDON_WARNING)
        view.invalidate()
    }

    private fun createOnlineNow(stake: StakeSpec?) {
        st.menu = null; st.overlay = null
        flash(if (stake != null) "Blocage de la mise puis ouverture de la partie…" else "Connexion au service…")
        val g = ChessOnlineHub.newGame(this)
        val color = when (side) { Side.WHITE -> "white"; Side.BLACK -> "black"; Side.RANDOM -> "random" }
        val clock = if (stake != null) ClockMode.COMPETITION else mode
        Thread {
            val r = g.create(playerName(), seconds, color, clock, stake)
            main.post { onOpened(g, "", r) }
        }.start()
    }

    private val codePos = intArrayOf(0)
    private val codeChars = CharArray(RoomCode.LENGTH) { RoomCode.ALPHABET[10] }

    /** Saisir le code à 8 symboles « XXXX-XXXX » avec ‹ › : une position, puis son caractère (la télécommande n'a pas de clavier). */
    private fun enterCode(spectate: Boolean, focus: Int = 0) {
        val a = RoomCode.ALPHABET
        fun shown(): String = buildString {
            for (i in codeChars.indices) {
                if (i == 4) append('-')
                if (i == codePos[0]) append('[').append(codeChars[i]).append(']') else append(codeChars[i])
            }
        }
        st.menu = ChessMenu(if (spectate) "Regarder une partie" else "Code de la partie", "Code de votre ami : XXXX-XXXX", listOf(
            ChessItem("Position", { "${codePos[0] + 1} sur ${codeChars.size}" }, change = { d -> codePos[0] = (codePos[0] + d + codeChars.size) % codeChars.size; enterCode(spectate, 0) }),
            ChessItem("Caractère", { shown() }, change = { d -> codeChars[codePos[0]] = a[(a.indexOf(codeChars[codePos[0]]) + d + a.length) % a.length]; enterCode(spectate, 1) }),
            ChessItem(if (spectate) "Regarder" else "Rejoindre", ok = { joinOnline(String(codeChars), spectate) }),
            ChessItem("Retour", ok = { startOnline() }),
        )) { startOnline() }
        st.menu?.focus = focus
        view.invalidate()
    }

    private fun joinOnline(code: String, spectate: Boolean) {
        st.menu = null; st.overlay = null
        flash("Connexion au service…")
        val g = ChessOnlineHub.newGame(this)
        Thread {
            val r = g.join(code, playerName(), spectate)
            main.post { onOpened(g, code, r) }
        }.start()
    }

    private fun resumeOnline() {
        st.menu = null
        flash("Retour dans la partie…")
        val g = ChessOnlineHub.newGame(this)
        Thread {
            val r = g.resume()
            main.post { if (r == null) { ChessOnlineHub.closeGame(g); showSetup(); flash("Plus de partie à reprendre.") } else onOpened(g, "", r) }
        }.start()
    }

    private fun onOpened(g: ChessOnlineGame, code: String, r: ChessOnlineGame.Opened) {
        when (r) {
            is ChessOnlineGame.Opened.Seated -> { TvConnect.feature("chess_online", "menu"); attachOnline(g) }
            is ChessOnlineGame.Opened.NeedsStake -> askJoinStake(g, code, r.spec)
            is ChessOnlineGame.Opened.Failed -> { ChessOnlineHub.closeGame(g); showSetup(); flash(r.text) }
        }
    }

    /** La salle est misée : la mise est dite AVANT tout blocage ; le joueur choisit de miser, de regarder seulement, ou de revenir. */
    private fun askJoinStake(g: ChessOnlineGame, code: String, spec: StakeSpec) {
        val avail = ChessOnlineHub.availability()
        val bal = balanceOf(spec.cur)
        val free = avail as? StakeAvailability.FreeOnly
        val poor = bal != null && bal < spec.per
        val items = ArrayList<ChessItem>()
        items += ChessItem(
            if (free != null) "Mises indisponibles ici" else if (poor) "Solde insuffisant" else "Miser ${WalletView.thousands(spec.per)} ${spec.cur} et jouer",
            enabled = free == null && !poor,
            ok = { st.overlay = null; flash(ChessOnlineTexts.ABANDON_WARNING); finishJoinWithStake(g, code, spec) })
        items += ChessItem("Regarder seulement", ok = { st.overlay = null; g.abandonJoin(); ChessOnlineHub.closeGame(g); joinOnline(code, true) })
        items += ChessItem("Non, revenir", ok = { st.overlay = null; g.abandonJoin(); ChessOnlineHub.closeGame(g); startOnline() })
        st.overlay = ChessMenu(ChessOnlineTexts.JOIN_STAKE_TITLE, free?.reason ?: ChessOnlineTexts.stakeSubtitle(spec, bal), items) {
            st.overlay = null; g.abandonJoin(); ChessOnlineHub.closeGame(g); startOnline()
        }
        view.invalidate()
    }

    private fun finishJoinWithStake(g: ChessOnlineGame, code: String, spec: StakeSpec) {
        flash("Blocage de la mise…")
        Thread {
            val r = g.joinWithStake(code, playerName(), spec)
            main.post { onOpened(g, code, r) }
        }.start()
    }

    /** Assis (créé, rejoint ou repris) : l'écran suit la partie ; les téléphones de la maison regardent par la vitrine de la TV. */
    private fun attachOnline(g: ChessOnlineGame) {
        online = g
        g.onChange = { postRender() }
        drive = OnlineDrive(g)
        val s = g.view()
        st.flipped = g.session?.color == "b"
        st.menu = null; st.overlay = null; st.promo = null; st.selected = null
        onlineViewV = null; settlementShown = null; endShownFor = null
        if (s["stage"] == "LOBBY" || s.isEmpty()) {
            st.screen = ChessTvState.Screen.LOBBY
            st.menu = ChessMenu("", null, listOf(ChessItem(if (g.session?.color != null && (s["room"] as? Map<*, *>)?.get("role") == "HOST") "Annuler la partie" else "Quitter", ok = { showSetup() }))) { showSetup() }
        } else enterGame()
        render()
    }

    private var onlineViewV: Long? = null
    private var settlementShown: Any? = null

    /** Le salon d'attente d'une partie en ligne : le code à donner à l'ami (jamais montré à un téléphone), la mise, l'adversaire, le code local des téléphones de la maison. */
    private fun onlineLobby(g: ChessOnlineGame, s: Map<String, Any?>): ChessLobby {
        val sess = g.session
        val lines = ArrayList<Pair<String, Boolean>>()
        sess?.color?.let { lines += "Vous jouez les ${if (it == "b") "Noirs" else "Blancs"}" to true }
        g.stake?.let { lines += ChessOnlineTexts.stakeLine(it) to true }
        val oppColor = if (sess?.color == "b") "white" else "black"
        @Suppress("UNCHECKED_CAST") val opp = s[oppColor] as? Map<String, Any?>
        val joined = opp?.get("playerId") != null
        lines += (if (joined) "Adversaire : ${opp?.get("name")}" else "Adversaire : en attente…") to joined
        g.host?.let { h -> lines += "Téléphones de la maison : code ${h.code}" to true }
        return ChessLobby("Partie en ligne", sess?.code.orEmpty(), "Votre ami choisit « Échecs › En ligne › Rejoindre » et saisit ce code", null, lines,
            if (joined) null else "En attente de l'adversaire…", noQrNote = "Dites ce code à votre ami : il le saisit dans « Échecs › En ligne › Rejoindre ».")
    }

    /** Pied de l'écran de jeu en ligne : règlement de la mise, décompte du forfait de l'adversaire, liaison dégradée, ou la mise en jeu (par ordre d'importance). */
    private fun onlineFooter(g: ChessOnlineGame, s: Map<String, Any?>): String? {
        when (val st0 = g.settlement) {
            is ChessOnlineGame.Settlement.Pending -> return st0.text
            is ChessOnlineGame.Settlement.Done -> return st0.text
            is ChessOnlineGame.Settlement.Later -> return st0.text
            is ChessOnlineGame.Settlement.Refused -> return st0.text
            ChessOnlineGame.Settlement.None -> {}
        }
        val link = g.client.linkView(TvConnect.link?.routes?.lastVia, PlayHub.hasInternet(this))
        if (link.message != null) return link.message
        ChessOnlineTexts.awayLine(s, SystemClock.uptimeMillis() - st.sAt)?.let { return it }
        // l'information qui reste : la mise en jeu d'abord (elle ne doit jamais être coupée), puis, sans alarme, « Partie par relais : liaison lente » quand Internet vient du tuyau d'un téléphone
        return listOfNotNull(g.stake?.let { ChessOnlineTexts.stakeLine(it) }, ChessOnlineHub.networkLine()).joinToString(" · ").ifEmpty { null }
    }

    /** Une fois par changement de règlement : un message à l'écran (la TV montre ensuite le résultat de la mise dans « Mes jetons »). */
    private fun onlineTick(g: ChessOnlineGame, s: Map<String, Any?>) {
        val settlement = g.settlement
        if (settlement != settlementShown) {
            settlementShown = settlement
            val text = when (settlement) {
                is ChessOnlineGame.Settlement.Done -> settlement.text; is ChessOnlineGame.Settlement.Later -> settlement.text; is ChessOnlineGame.Settlement.Refused -> settlement.text
                else -> null
            }
            text?.let { flash(it) }
        }
        st.footer = onlineFooter(g, s)
        // la liaison est perdue (60 s) : la partie est perdue pour cette TV ; le service tranche (forfait), l'écran le dit une fois
        if (s["stage"] == "PLAYING" && st.overlay == null && g.client.linkView(TvConnect.link?.routes?.lastVia, PlayHub.hasInternet(this)).action == LinkAction.LEAVE) {
            st.overlay = ChessMenu("Connexion perdue", "La partie n'a pas pu être reprise : le service la tranche", listOf(
                ChessItem("Revenir aux réglages", ok = { st.overlay = null; showSetup() }),
            )) { st.overlay = null; showSetup() }
        }
    }

    // ------------------------------------------------------------------ rendering

    @Suppress("UNCHECKED_CAST")
    private fun render() {
        val d = drive
        val s = d?.view() ?: room?.view(null) ?: emptyMap()
        st.s = s
        // the clocks count from the moment a NEW position arrived: a redraw for another reason (link, settlement) must not make them jump back
        if (d is OnlineDrive) { val v = (s["v"] as? Number)?.toLong(); if (v == null || v != onlineViewV) { onlineViewV = v; st.sAt = SystemClock.uptimeMillis() } }
        else st.sAt = SystemClock.uptimeMillis()
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
                if (d is OnlineDrive) st.lobby = onlineLobby(d.g, s)
                if (stage == "PLAYING" || stage == "FINISHED") enterGame()
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
        if (d is OnlineDrive && st.screen != ChessTvState.Screen.SETUP) onlineTick(d.g, s)
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
        (drive as? OnlineDrive)?.let { if (st.screen != ChessTvState.Screen.SETUP) onlineTick(it.g, s) }   // the forfeit countdown and the link line run every tick
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
        val staked = (d as? OnlineDrive)?.g?.stake != null
        val stopsGame = if (staked) "Vous perdrez votre mise." else "La partie en cours sera arrêtée."
        if (!over && st.remoteColors.isNotEmpty()) {
            items += ChessItem("Proposer la nulle", ok = { st.overlay = null; d.offerDraw() })
            items += ChessItem("Abandonner", ok = { confirm("Abandonner la partie ?", if (staked) "La partie sera perdue avec votre mise." else "La partie sera perdue.") { d.resign() } })
        }
        items += ChessItem("Nouvelle partie (réglages)", ok = { if (over) showSetup() else confirm("Nouvelle partie ?", stopsGame) { showSetup() } })
        items += ChessItem("Quitter les échecs", ok = { if (over) finish() else confirm("Quitter les échecs ?", stopsGame) { finish() } })
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

    // ---- usage statistics (docs/TELEMETRY.md): chess_game once per game, never the players' names ----
    private var gameStartAt = 0L
    private var gameRecorded = true

    /** [s] = final state (result read from it), null = the game was left before its end (abandon). */
    private fun recordGame(s: Map<String, Any?>?) {
        if (gameRecorded || gameStartAt == 0L) return
        val cur = s ?: st.s
        if (s == null && cur["stage"] != "PLAYING") return
        gameRecorded = true
        @Suppress("UNCHECKED_CAST") val winner = (cur["result"] as? Map<String, Any?>)?.get("winner") as? String
        val result = when {
            s == null -> "abandon"
            winner == null -> "draw"
            st.remoteColors.size == 1 -> if (winner in st.remoteColors) "win" else "loss"
            else -> if (winner == "w") "win" else "loss"          // two players at the TV (or phones only): white's point of view
        }
        TvConnect.track("chess_game", mapOf(
            "mode" to when (opp) { Opp.AI -> "ai"; Opp.TV2 -> "local"; Opp.PHONE1, Opp.PHONES -> "phones"; Opp.ONLINE -> "online" },
            "ai_level" to (if (opp == Opp.AI) level else null), "time_control" to "${seconds}s",
            "result" to result, "moves" to (cur["ply"] as? Number)?.toInt(), "ms" to SystemClock.uptimeMillis() - gameStartAt))
    }

    private fun endMenu(s: Map<String, Any?>) {
        recordGame(s)
        val d = drive
        @Suppress("UNCHECKED_CAST") val res = s["result"] as? Map<String, Any?>
        val winner = res?.get("winner") as? String
        val interrupted = res?.get("reason") == "ABANDONED"       // an Internet game that could not be played to its end: never called a draw
        val title = when {
            interrupted -> "Partie interrompue"
            winner == null -> "Partie nulle"
            st.remoteColors.size == 1 -> if (winner in st.remoteColors) "Vous avez gagné !" else "Vous avez perdu"
            else -> if (winner == "w") "Les Blancs gagnent" else "Les Noirs gagnent"
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
        val items = ArrayList<ChessItem>()
        if (d is LocalDrive) items += ChessItem("Rejouer (mêmes réglages)", ok = { st.overlay = null; startLocal() })
        if (d?.canUndo == true) items += ChessItem("Annuler le dernier coup", ok = { st.overlay = null; d.undo() })
        // a staked game: the settlement is read in « Mes jetons » (the API settled it; the TV never computes a balance)
        if (d is OnlineDrive && d.g.stake != null && WalletHub.screenOpenable()) items += ChessItem("Voir « Mes jetons »", ok = { runCatching { startActivity(android.content.Intent(this, WalletActivity::class.java)) } })
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
            flash(when {
                s["thinking"] == true -> "L'ordinateur réfléchit…"
                drive is OnlineDrive -> if (st.remoteColors.isEmpty()) "Vous regardez la partie" else "Ce n'est pas votre tour"
                else -> "Ce n'est pas à la télécommande de jouer"
            })
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

    /**
     * Internet game ([ChessOnlineGame]): the service pushes every position, [ChessOnlineGame.onChange] redraws; commands go off the UI thread and the service's answer says why a move
     * was refused. The game lives in the app ([ChessOnlineHub]), not in this screen: [detach] lets the system recreate the screen without leaving the game.
     */
    private inner class OnlineDrive(val g: ChessOnlineGame) : Drive {
        override fun view() = g.view()
        override fun remoteColors() = setOfNotNull(g.session?.color)
        override fun gameKey(): Any? = g.session?.roomId
        private fun send(what: () -> ChessAct) {
            Thread {
                val r = runCatching(what)
                main.post {
                    r.onSuccess { a -> ChessOnlineTexts.ackText(a.result)?.let { flash(it) }; render() }
                        .onFailure { flash(it.message ?: "Envoi impossible") }
                }
            }.start()
        }
        override fun move(uci: String) = send { g.move(uci) }
        override fun resign() = send { g.resign() }
        override fun offerDraw() = send { g.draw("offer") }
        override fun answerDraw(accept: Boolean) = send { g.draw(if (accept) "accept" else "decline") }
        override fun undo() = false
        override val canUndo = false

        /** The screen goes away but the game goes on (system recreation): no resignation, no forfeit. */
        fun detach() { g.onChange = null }

        /** Leaving for good: resign a running game (the opponent need not wait 60 s), cancel a room nobody joined (the stake comes back), then leave. */
        override fun close() {
            g.onChange = null
            val v = g.view()
            val iPlay = g.session?.color != null
            val host = (v["room"] as? Map<*, *>)?.get("role") == "HOST"
            val stage = v["stage"]
            Thread {
                runCatching { if (stage == "PLAYING" && iPlay) g.resign() else if (stage == "LOBBY" && host) g.cancel() }
                g.leave()
                ChessOnlineHub.closeGame(g)
            }.apply { isDaemon = true }.start()
        }
    }

    companion object {
        /** The computer's thinking time for a countdown (shown nowhere, kept for the logs). */
        fun aiAllowance(seconds: Int) = ChessAi.allowance(MoveTimer.clamp(seconds) * 1000L)
    }
}
