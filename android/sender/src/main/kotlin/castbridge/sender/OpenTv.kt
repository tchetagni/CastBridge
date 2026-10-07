package castbridge.sender

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import castbridge.core.remote.OpenTvAnswer
import castbridge.core.remote.OpenTvFlow
import castbridge.core.remote.OpenTvHttpLink
import castbridge.core.remote.OpenTvLink
import castbridge.core.remote.OpenTvOutcome
import castbridge.core.remote.OpenTvRoute
import castbridge.core.remote.OpenTvRoutes
import castbridge.core.remote.OpenTvWire
import castbridge.core.remote.RemoteBt
import castbridge.core.trust.SavedTv
import castbridge.core.trust.TvAuth
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.EndpointKind
import castbridge.core.tv.OpenTvScreen
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.TvEndpointResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * « Ouvrir sur la TV » (docs/REMOTE.md) : le bouton de l'onglet « CastBridge TV », la touche « TV » de la télécommande, le raccourci de l'icône (appui long)
 * et le lien `castbridge://open-tv` demandent tous à CastBridge-TV de passer devant l'application qui est à l'écran de la TV, comme la touche YouTube ou
 * Netflix d'une télécommande. Toute la décision est dans le cœur (`OpenTvFlow`, testé en JVM) : ici seulement les liaisons Android et l'état montré aux écrans.
 *
 * Rien n'allume une TV éteinte (ni HDMI-CEC, ni Wake-on-LAN : impossibles sans droits système) ; aucune permission dangereuse n'est demandée au téléphone.
 */
object OpenTv {
    sealed class State {
        object Idle : State()
        object Working : State()
        /** La ligne à montrer, huit secondes (voir [OpenTvLine]). */
        data class Done(val outcome: OpenTvOutcome, val at: Long) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    /**
     * Lance la demande. Ignorée pendant qu'une autre court : un double appui, un lien qui boucle ou un autre écran ne font jamais deux demandes à la fois.
     * [onDone] s'exécute sur le fil principal, avec l'issue (la ligne est dans [OpenTvOutcome.line]).
     */
    fun open(ctx: Context, screen: OpenTvScreen? = null, onDone: (OpenTvOutcome) -> Unit = {}) {
        if (!running.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        _state.value = State.Working
        scope.launch {
            var outcome: OpenTvOutcome = OpenTvOutcome.Failed(null)
            try { outcome = run(app, screen) } catch (e: Exception) { /* Failed: the line says the TV could not do it */ }
            finally { _state.value = State.Done(outcome, System.currentTimeMillis()); running.set(false) }      // never stuck « en cours », whatever happened
            withContext(Dispatchers.Main) { runCatching { onDone(outcome) } }
        }
    }

    private fun run(app: Context, screen: OpenTvScreen?): OpenTvOutcome {
        val t = OpenTvTargets.of(app)
        if (t.needsCode) return OpenTvOutcome.Refused
        return OpenTvFlow().run(t.known, t.links, screen)
    }

    // ---------------------------------------------------------------- raccourci de l'icône et lien `castbridge://open-tv`

    const val SCHEME = "castbridge"
    const val HOST = "open-tv"
    private const val LINK_COOLDOWN_MS = 2_000L
    @Volatile private var lastLink = 0L

    fun isLink(uri: Uri?): Boolean = uri != null && uri.scheme == SCHEME && uri.host == HOST

    /**
     * Appelé par [MainActivity] : le raccourci « Ouvrir CastBridge-TV » (res/xml/shortcuts.xml) ou `castbridge://open-tv[?screen=library]`. Vrai quand l'intention en était une
     * (elle est alors consommée : une rotation de l'écran ne relance rien). Le résultat est dit par un message court, et par la ligne de l'onglet « CastBridge TV ».
     * CastBridge-TV déjà devant : on ouvre la télécommande, comme le bouton de l'onglet.
     */
    fun handleLink(activity: Activity, intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (!isLink(uri)) return false
        intent.data = null
        val now = System.currentTimeMillis()
        if (now - lastLink < LINK_COOLDOWN_MS) return true            // a page or an app that loops the link cannot make the TV pop up again and again
        lastLink = now
        TvHomeRequest.pending.value = TvHomeRequest.TV                // the CastBridge TV tab, where the line is shown
        open(activity, OpenTvScreen.parse(uri.getQueryParameter("screen"))) { o ->
            Toast.makeText(activity, o.line, Toast.LENGTH_LONG).show()
            if (o.alreadyFront) RemoteActivity.open(activity)
        }
        return true
    }
}

/** La TV que le téléphone connaît et les voies pour la joindre (seulement celles qu'il peut utiliser avec un identifiant valable). */
internal class OpenTvTarget(val known: Boolean, val links: List<OpenTvLink>, val needsCode: Boolean = false)

internal object OpenTvTargets {
    fun of(ctx: Context): OpenTvTarget {
        runCatching { TvLinkManager.init(ctx) }
        val pins = PinStore(ctx)
        val tv = TvLinkManager.saved.default()
        // the TV the remote screen drives (RemoteScreen): the trusted one, unless another TV was chosen there by its name
        val chosen = RemotePrefs(ctx).tvName
        return if (tv != null && (chosen == null || chosen == tv.mdns || chosen == tv.name)) trusted(ctx, tv, pins) else byCode(ctx, pins)
    }

    /** Une TV qui connaît ce téléphone (« Ajouter ma TV ») : Wi-Fi avec son jeton, Bluetooth sans code (la TV reconnaît l'adresse), tunnel de l'API si la passerelle tourne. */
    private fun trusted(ctx: Context, tv: SavedTv, pins: PinStore): OpenTvTarget {
        val session = (TvLinkManager.state.value as? LinkUi.Connected)?.session?.takeIf { it.tv.address == tv.address }
        val credential = runCatching { TvLinkManager.driver.credential(tv.address) }.getOrNull()?.takeIf { TvAuth.isUsable(it) }
            ?: pins.pinOnly(tv.mdns ?: tv.name).takeIf { TvAuth.isUsable(it) }
        val lan = session?.base?.takeIf { TvEndpointResolver.kindOf(it) != EndpointKind.BLUETOOTH }
            ?: castbridge.core.link.HelloIps.lanOnly(tv.lastIps).firstOrNull()?.let { "http://$it:${tv.port}" }
        return target(ctx, trusted = true, credential, lan, tv.address, BtSshGatewayService.apiBase(tv.address))
    }

    /** Une TV connue par son code (sans « Ajouter ma TV ») : l'adresse tapée ou la dernière qui a répondu, et le secours Bluetooth choisi dans la télécommande. */
    private fun byCode(ctx: Context, pins: PinStore): OpenTvTarget {
        val rp = RemotePrefs(ctx); val home = HomeTv(ctx)
        val name = rp.tvName ?: home.name
        val bt = rp.btFallback
        val manual = rp.manualHost?.takeIf { name?.startsWith("manual:") == true }
        if (name == null && bt == null && manual == null) return OpenTvTarget(false, emptyList())
        val key = when { manual != null -> "$manual:${rp.manualPort}"; name == "bt" && bt != null -> "bt:$bt"; else -> name ?: "bt:$bt" }
        val pin = pins.get(key).takeIf { TvAuth.isUsable(it) }
        val lan = listOfNotNull(manual?.let { "http://$it:${rp.manualPort}" }, name?.let { UploadService.hintFor(it) }, name?.let { runCatching { UploadService.addressMemory(ctx).recall(it) }.getOrNull() },
            home.host?.let { "http://$it:${ReceiverServer.PORT}" }).firstOrNull { TvEndpointResolver.kindOf(it) != EndpointKind.BLUETOOTH }
        return target(ctx, trusted = false, pin, lan, bt, BtSshGatewayService.apiBase(bt))
    }

    /** Les voies à bâtir : la règle pure [OpenTvRoutes] décide lesquelles (jamais un identifiant inutilisable, jamais une TV sans code contactée). */
    private fun target(ctx: Context, trusted: Boolean, credential: String?, lan: String?, btAddress: String?, tunnel: String?): OpenTvTarget {
        val c = OpenTvRoutes.choose(trusted, credential, hasLan = lan != null, hasBluetooth = btAddress != null, hasTunnel = tunnel != null)
        if (c.needsCode) return OpenTvTarget(true, emptyList(), needsCode = true)
        return OpenTvTarget(true, c.routes.map { r ->
            when (r) {
                OpenTvRoute.LAN -> OpenTvHttpLink(r, lan!!, credential)
                OpenTvRoute.BLUETOOTH -> BtOpenLink(ctx, btAddress!!, credential)
                OpenTvRoute.TUNNEL -> OpenTvHttpLink(r, tunnel!!, credential)
            }
        })
    }
}

/**
 * La voie Bluetooth : la commande `open` du canal télécommande CBTR de la TV (aucune adresse IP nécessaire ; un téléphone de confiance n'envoie pas de code, la TV
 * reconnaît son adresse Bluetooth). Une liaison courte, fermée à la fin ; `connect()` d'un socket Bluetooth n'a pas de délai : un fil de garde le coupe au temps donné.
 */
@SuppressLint("MissingPermission")
private class BtOpenLink(private val ctx: Context, private val address: String, private val credential: String?) : OpenTvLink {
    override val route = OpenTvRoute.BLUETOOTH

    override fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer {
        if (!hasBtPermission(ctx)) throw OpenTvLink.Unusable("CastBridge n'a pas l'autorisation « Appareils à proximité » : accordez-la pour joindre la TV par Bluetooth")
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw OpenTvLink.Unusable("Ce téléphone n'a pas de Bluetooth")
        if (!adapter.isEnabled) throw OpenTvLink.Unusable("Le Bluetooth du téléphone est éteint : allumez-le pour joindre la TV")
        val result = AtomicReference<Result<OpenTvAnswer>?>(null)
        val socket = AtomicReference<BluetoothSocket?>(null)
        val worker = thread(name = "open-tv-bt", isDaemon = true) {
            result.set(runCatching { exchange(adapter, socket, screen) })
        }
        worker.join(timeoutMs)
        if (worker.isAlive) { runCatching { socket.get()?.close() }; throw IOException("la TV ne répond pas par Bluetooth") }      // the closed socket frees the blocked thread
        return result.get()?.getOrThrow() ?: throw IOException("la TV ne répond pas par Bluetooth")
    }

    private fun exchange(adapter: android.bluetooth.BluetoothAdapter, socket: AtomicReference<BluetoothSocket?>, screen: OpenTvScreen?): OpenTvAnswer {
        try {
            runCatching { adapter.cancelDiscovery() }                // a running discovery slows and breaks connections; needs SCAN, optional
            val s = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
            socket.set(s)
            try {
                synchronized(castbridge.core.tunnel.BtConnectLock.of(address)) { s.connect() }       // never beside another connect() to the same TV
                RemoteBt.handshake(s.inputStream, s.outputStream, credential ?: TvAuth.NO_PIN)       // a token or nothing = no code on the wire (TvAuth.btPin): a trusted phone is known by its address
                return OpenTvWire.send(RemoteBt.Transport(s.inputStream, s.outputStream, s), screen)      // the line `POST open?screen=…` of the CBTR channel (tested in core)
            } finally { runCatching { s.close() } }
        } catch (e: BtProtocol.Refused) {
            // a wrong or missing code, a locked TV, a phone the TV does not know: not « the TV is off »
            return when (e.code) {
                BtProtocol.ERR_PIN, BtProtocol.ERR_LOCKED, BtProtocol.ERR_UNTRUSTED, BtProtocol.ERR_DENIED -> OpenTvAnswer(401, null, e.message)
                BtProtocol.ERR_MAGIC -> OpenTvAnswer(404, null, e.message)      // a TV older than the remote channel knows no « open » either
                else -> throw IOException(e.message)
            }
        } catch (e: SecurityException) {
            throw OpenTvLink.Unusable("CastBridge n'a plus l'autorisation « Appareils à proximité » : accordez-la pour joindre la TV par Bluetooth")
        }
    }
}
