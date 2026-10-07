package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.owner.OwnerChannelClient
import castbridge.core.owner.OwnerFrames
import castbridge.core.relay.PhoneNet
import castbridge.core.relay.PipeNeed
import castbridge.core.relay.RelayAnswer
import castbridge.core.relay.RelayDecision
import castbridge.core.relay.RelayFrames
import castbridge.core.relay.RelayInput
import castbridge.core.relay.RelayMeter
import castbridge.core.relay.RelayPolicy
import castbridge.core.relay.RelayReason
import castbridge.core.trust.SavedTv
import castbridge.core.trust.TrustRegistry
import castbridge.core.trust.TvAuth
import castbridge.core.tunnel.BtConnectLock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le relais côté téléphone (relay-R1, docs/coordination/DESIGN-RELAIS-TELEPHONE-2026-10-07.md § 2.4, § 2.6) : le téléphone SYNCHRONISÉ avec une TV ouvre pour elle le tuyau Internet
 * quand elle le demande, sans question à son propriétaire (la synchronisation par PIN EST le consentement), dans la politique de coût ([RelayPolicy]) et la portée ([castbridge.core.relay.RelayScope]).
 *
 * Comment le téléphone apprend que la TV veut un tuyau :
 *  1. la TV frappe à sa porte en Bluetooth ([RelayWakeReceiver] : « appareil connecté », même CastBridge fermé) ; il va lire la demande sur le canal propriétaire de la TV
 *     ([OwnerFrames.RELAY_STATE] envoyé, [OwnerFrames.RELAY_ASK_PIPE] reçu) ;
 *  2. la liaison de confiance existante porte le drapeau `pipeWanted` (HELLO sur Bluetooth, en-tête `X-CB-Pipe` du garde-vivant sur le Wi-Fi) : [onHint] ; même lecture sur le canal propriétaire.
 * Puis la politique décide ; si elle dit oui, [BtGatewayService] démarre en silence (une notification neutre « CastBridge relaie pour <TV> », visibilité privée, bouton Arrêter) et se
 * ferme 10 minutes après la dernière connexion. Si elle dit non, le téléphone dit pourquoi à la TV (état de relais), sans écran.
 *
 * Non vérifié sur appareil : voir docs/REMOTE-TUNNEL-TV.md § 5 (Android 12+ n'autorise un service au premier plan démarré depuis l'arrière-plan que dans quelques cas : la diffusion
 * Bluetooth « appareil connecté » en est un, dans les 10 secondes ; sinon le téléphone répond `background` et la TV demande d'ouvrir CastBridge).
 */
@SuppressLint("MissingPermission")
object RelayRuntime {
    private const val TAG = "CastBridgeRelay"
    /** Au plus une lecture de la demande de la TV par TV toutes les 15 secondes (la TV frappe au plus toutes les 15 s, la liaison de confiance relit chaque minute). */
    private const val MIN_POLL_GAP_MS = 15_000L
    private const val CONNECT_LIMIT_S = 8L
    /** Un « Arrêter » de la notification met le relais de CETTE TV en sommeil : elle redemande dans quinze secondes pendant une partie, l'utilisateur vient de dire non. */
    private const val SNOOZE_MS = 10 * 60_000L

    private lateinit var app: Context
    private lateinit var settings: RelaySettings
    private lateinit var meter: RelayMeter
    private val io: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-relay").apply { isDaemon = true } }
    /** Gardes des connexions sur un fil à part : `io` est justement bloqué dans `connect()` quand le garde doit tirer. */
    private val guards: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-relay-guard").apply { isDaemon = true } }
    private val lastPoll = ConcurrentHashMap<String, Long>()

    @Synchronized fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        settings = RelaySettings(app)
        meter = RelayMeter(settings.meterStore, System::currentTimeMillis)
    }

    fun settings(ctx: Context): RelaySettings { init(ctx); return settings }
    fun meter(ctx: Context): RelayMeter { init(ctx); return meter }

    // ------------------------------------------------------------------ comment la demande arrive

    /** La liaison de confiance a dit que la TV veut un tuyau (HELLO ou en-tête du garde-vivant) : aller lire sa demande sur le canal propriétaire. */
    fun onHint(ctx: Context, tv: SavedTv) {
        init(ctx)
        if (BtGatewayService.running || !gapOk(tv.address)) return
        io.execute { runCatching { poll(app, tv, fromTrustedLink = true) }.onFailure { Log.w(TAG, "hint: ${it.javaClass.simpleName}") } }
    }

    /** Même chose quand seul l'en-tête du garde-vivant Wi-Fi arrive : la TV est désignée par l'adresse de base qu'on a interrogée. */
    fun onHintForBase(ctx: Context, base: String) {
        init(ctx)
        TvLinkManager.savedFor(base)?.let { onHint(ctx, it) }
    }

    /** La TV vient de se connecter en Bluetooth (peut-être : elle frappe à la porte) : lire sa demande, dans les 10 secondes pendant lesquelles Android autorise un service au premier plan. */
    fun onAcl(ctx: Context, tv: SavedTv, done: () -> Unit) {
        init(ctx)
        val finished = AtomicBoolean(false)
        val finish: () -> Unit = { if (finished.compareAndSet(false, true)) { runCatching { done() } } }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 9_000)             // une diffusion qui dépasse ~10 s serait comptée comme bloquée : on la rend
        if (BtGatewayService.running || !gapOk(tv.address)) { finish(); return }
        io.execute {
            try { poll(app, tv, fromTrustedLink = false) } catch (e: Exception) { Log.w(TAG, "acl: ${e.javaClass.simpleName}") } finally { finish() }
        }
    }

    private fun gapOk(address: String): Boolean {
        val a = TrustRegistry.norm(address); val now = System.currentTimeMillis()
        val last = lastPoll[a] ?: 0L
        if (now - last < MIN_POLL_GAP_MS) return false
        lastPoll[a] = now
        return true
    }

    // ------------------------------------------------------------------ lire la demande de la TV, décider, répondre

    /**
     * Une conversation avec la TV sur son canal propriétaire : le téléphone dit son état, la TV répond si elle veut un tuyau ; la politique décide ; le téléphone dit ce qu'il a fait.
     * [fromTrustedLink] : le drapeau est venu de la liaison de confiance (authentifiée) : si le canal propriétaire est injoignable, ce drapeau suffit pour une demande « petite ».
     */
    private fun poll(ctx: Context, tv: SavedTv, fromTrustedLink: Boolean) {
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!ad.isEnabled || !hasBtPermission(ctx)) return
        val address = TrustRegistry.norm(tv.address)
        val sock = try { connectOwner(ad, address) } catch (e: IOException) {
            if (fromTrustedLink) decideAndStart(ctx, tv, RelayFrames.Ask(listOf(PipeNeed.PLAY), RelayFrames.DEFAULT_TTL_SEC))   // pas de canal : le drapeau authentifié suffit
            return
        }
        sock.use { s ->
            val c = OwnerChannelClient(s.inputStream, s.outputStream)
            if (!c.hello()) return
            when (val answer = c.relayState(currentState(ctx))) {
                is RelayAnswer.Wanted -> {
                    val state = decideAndStart(ctx, tv, answer.ask)
                    c.relayState(state)                                  // dit à la TV ce qui s'est passé (ouverture, ou pourquoi pas) : la réponse n'est pas utilisée
                }
                RelayAnswer.NotWanted, RelayAnswer.Unsupported, RelayAnswer.LinkLost -> {}
            }
        }
    }

    private fun currentState(ctx: Context): RelayFrames.State {
        val net = phoneNet(ctx)
        val metered = if (net == PhoneNet.NONE) null else net == PhoneNet.METERED
        return RelayFrames.State(if (BtGatewayService.running) RelayFrames.Phase.OPEN else RelayFrames.Phase.IDLE, metered = metered)
    }

    /** Un seul `connect()` à la fois vers une même TV, comme le tunnel d'API et la télécommande : le verrou est partagé ([BtConnectLock]). */
    private fun connectOwner(ad: BluetoothAdapter, address: String): BluetoothSocket {
        runCatching { ad.cancelDiscovery() }
        val s = ad.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(OwnerFrames.SERVICE_UUID))
        val guard = guards.schedule(Runnable { runCatching { s.close() } }, CONNECT_LIMIT_S, TimeUnit.SECONDS)
        try { synchronized(BtConnectLock.of(address)) { s.connect() }; guard.cancel(false); return s }
        catch (e: IOException) { guard.cancel(false); runCatching { s.close() }; throw e }
    }

    /** La politique de coût et de retrait ([RelayPolicy], testée en JVM) puis, si elle dit oui, le démarrage silencieux. Rend l'état à dire à la TV. */
    private fun decideAndStart(ctx: Context, tv: SavedTv, ask: RelayFrames.Ask): RelayFrames.State {
        val address = TrustRegistry.norm(tv.address)
        val credential = runCatching { PinStore(ctx).get(address, tv.name) }.getOrDefault("")
        val net = phoneNet(ctx)
        val withdrawn = settings.optedOut(address) || System.currentTimeMillis() < settings.snoozedUntil(address)
        val need = ask.needs.firstOrNull { it.bulk } ?: ask.needs.firstOrNull()
        val decision = RelayPolicy.decide(RelayInput(
            synced = TvAuth.isUsable(credential), optedOut = withdrawn, net = net, allowMobile = settings.allowMobile,
            usedTodayBytes = meter.usedToday(), need = need,
        ))
        val metered = if (net == PhoneNet.NONE) null else net == PhoneNet.METERED
        return when (decision) {
            is RelayDecision.Refuse -> RelayFrames.State(RelayFrames.Phase.REFUSED, decision.reason, metered)
            is RelayDecision.Open ->
                if (BtGatewayService.running) RelayFrames.State(RelayFrames.Phase.OPEN, metered = metered)
                else if (BtGatewayService.startAuto(ctx, address, tv.name, decision.limitBytes, metered == true))
                    RelayFrames.State(RelayFrames.Phase.OPENING, metered = metered, leftKb = decision.limitBytes?.let { it / 1024 })
                else RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.BACKGROUND, metered)    // Android refuse un service au premier plan depuis l'arrière-plan
        }
    }

    /** Le réseau du téléphone vu par la politique : validé par le système (Internet réellement atteint), facturé ou non. */
    fun phoneNet(ctx: Context): PhoneNet = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return PhoneNet.NONE) ?: return PhoneNet.NONE
        when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> PhoneNet.NONE
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> PhoneNet.UNMETERED
            else -> PhoneNet.METERED
        }
    }.getOrDefault(PhoneNet.NONE)

    // ------------------------------------------------------------------ appelé par le service

    /** L'utilisateur a touché « Arrêter » dans la notification d'un tuyau automatique : cette TV attend dix minutes avant de redemander. */
    fun userStopped(address: String) { settings.snooze(address, System.currentTimeMillis() + SNOOZE_MS) }

    /** Le service n'a pas pu passer au premier plan (Android) : la TV doit le savoir pour demander d'ouvrir CastBridge. */
    fun serviceRefused(ctx: Context, address: String) { report(ctx, address, RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.BACKGROUND)) }

    /** Dit à la TV, sur son canal propriétaire, pourquoi le tuyau s'est arrêté ou n'a pas pu s'ouvrir (plafond atteint, réseau perdu…). Au mieux : aucune erreur ne remonte. */
    fun report(ctx: Context, address: String, state: RelayFrames.State) {
        init(ctx)
        io.execute {
            runCatching {
                val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return@runCatching
                if (!ad.isEnabled) return@runCatching
                connectOwner(ad, TrustRegistry.norm(address)).use { s -> val c = OwnerChannelClient(s.inputStream, s.outputStream); if (c.hello()) c.relayState(state) }
            }
        }
    }
}

/**
 * Manifeste, non exporté : la diffusion système « appareil Bluetooth connecté » d'une TV enregistrée réveille CastBridge même fermé (comme le font déjà la reprise de la liaison et la
 * livraison des lots) : la TV qui veut un tuyau frappe à la porte du téléphone, qui vient lire sa demande.
 */
class RelayWakeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        @Suppress("DEPRECATION")
        val dev = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val app = c.applicationContext
        TvLinkManager.init(app)
        val tv = TvLinkManager.saved.get(dev.address) ?: return                  // une TV enregistrée seulement
        val pending = goAsync()
        RelayRuntime.onAcl(app, tv) { pending.finish() }
    }
}
