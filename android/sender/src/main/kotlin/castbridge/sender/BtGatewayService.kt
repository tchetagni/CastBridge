package castbridge.sender

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import castbridge.core.gateway.Exit
import castbridge.core.gateway.GatewayService
import castbridge.core.gateway.Mux
import castbridge.core.relay.IdleStop
import castbridge.core.relay.MeteredAnnouncer
import castbridge.core.relay.PhoneNet
import castbridge.core.relay.PipeNeed
import castbridge.core.relay.RelayCost
import castbridge.core.relay.RelayDecision
import castbridge.core.relay.RelayDialer
import castbridge.core.relay.RelayFrames
import castbridge.core.relay.RelayInput
import castbridge.core.relay.RelayMeter
import castbridge.core.relay.RelayPolicy
import castbridge.core.relay.RelayReason
import castbridge.core.relay.RelayText
import castbridge.core.relay.meteredFlag
import castbridge.core.trust.PinKeys
import castbridge.core.trust.TvAuth
import castbridge.core.tunnel.BtConnectLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Shares the phone's Internet with CastBridge TV over Bluetooth: connects to the TV's gateway service and opens,
 * on the phone's network, the connections the TV app asks for. Reconnects by itself until stopped.
 *
 * relay-R1 (docs/coordination/DESIGN-RELAIS-TELEPHONE-2026-10-07.md § 2.4, § 2.6): two ways to start it.
 *  - by the user's own switch (« Partager l'Internet du téléphone avec la TV »): as before, no cap, no idle stop ([Run.auto] false);
 *  - by the TV's request ([RelayRuntime], [startAuto]): silent, a single neutral notification « CastBridge relaie pour <TV> » (private on the lock screen, LOW, button « Arrêter »), bounded by
 *    the cost policy ([RelayPolicy]: free on an unmetered network; on a metered one only the project's server, 5 MB a day by default, re-judged every 5 s), closed 10 minutes after the last
 *    connection, resumed by the system (START_STICKY) after a kill while the session is recent. The pipe only opens towards the project's server and never reaches the phone's own network ([RelayDialer]).
 * Every connect() to the TV goes through [BtConnectLock], shared with the API tunnel and the remote: never two at once to the same TV (« already at opened state »).
 */
@SuppressLint("MissingPermission")
class BtGatewayService : Service() {
    @Volatile private var stopping = false
    private var worker: Thread? = null
    @Volatile private var sock: BluetoothSocket? = null
    @Volatile private var run: Run? = null

    /**
     * One run of the pipe. [auto] = opened by the TV's request; [limitBytes] = what the cost policy allowed at the start (null = no ceiling); [metered] = the phone's network was metered at the start;
     * [need] = what the TV asked the pipe for (R-33 / audit I-13: the watchdog judges the cost policy with it, so a pipe opened for a big download on Wi-Fi stops when the phone moves to mobile data
     * instead of eating the 5 MB of the day kept for the game and the wallet).
     */
    private class Run(val address: String, val name: String, val auto: Boolean, val limitBytes: Long?, val metered: Boolean, val need: PipeNeed?)

    /** What the TV may open from here: the project's hosts on their ports, nothing of the phone's own network (castbridge.core.relay.RelayScope). */
    private val dialer = RelayDialer(
        resolve = { host -> InetAddress.getAllByName(host).toList() },
        open = { a, p -> Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(a, p), 15_000) } },
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { userStop(); return START_NOT_STICKY }
        // a null intent = the system restarted a START_STICKY service that was killed: resume only a recent automatic session
        val resumed = if (intent == null) RelayRuntime.settings(this).loadSession()?.takeIf { System.currentTimeMillis() - it.lastActivityAt < RelayPolicy.IDLE_STOP_MS } else null
        if (intent == null && resumed == null) { stopSelf(); return START_NOT_STICKY }
        val address = intent?.getStringExtra(EXTRA_ADDR) ?: resumed?.address ?: return START_NOT_STICKY
        val auto = intent?.getBooleanExtra(EXTRA_AUTO, false) ?: true
        val pin = intent?.getStringExtra(EXTRA_PIN)
        if (!auto && pin == null) return START_NOT_STICKY
        if (worker?.isAlive == true) return if (auto) START_STICKY else START_NOT_STICKY
        val name = intent?.getStringExtra(EXTRA_NAME) ?: resumed?.name ?: TvLinkManager.saved.get(address)?.name ?: "la TV"
        val limit = intent?.getLongExtra(EXTRA_LIMIT, -1L)?.takeIf { it >= 0 } ?: resumed?.limitBytes
        val metered = intent?.getBooleanExtra(EXTRA_METERED, false) ?: resumed?.metered ?: false
        val need = intent?.getStringExtra(EXTRA_NEED)?.let { PipeNeed.of(it) } ?: resumed?.need
        try {
            val n = notification(name)
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(NOTIF, n)
        } catch (e: Exception) {
            // Android 12+: a foreground service may not start from the background except in a few cases; the TV is told to ask the user to open CastBridge
            Log.e(TAG, "startForeground: ${e.javaClass.simpleName}")
            if (auto) RelayRuntime.serviceRefused(this, address)
            stopSelf(); return START_NOT_STICKY
        }
        stopping = false
        val r = Run(address, name, auto, limit, metered, need)
        run = r
        running = true
        if (auto) RelayRuntime.settings(this).saveSession(RelaySettings.Session(address, name, limit, metered, System.currentTimeMillis(), need))
        worker = thread(name = "bt-gateway") { loop(r, pin) }
        return if (auto) START_STICKY else START_NOT_STICKY
    }

    /** « Arrêter » of the notification, or the user's switch turned off: an automatic pipe puts THIS TV to sleep for ten minutes (the user just said no). */
    private fun userStop() {
        run?.takeIf { it.auto }?.let { RelayRuntime.userStopped(it.address) }
        stopping = true; runCatching { sock?.close() }; _state.value = "Partage arrêté"
        stopSelf()
    }

    /**
     * One secure RFCOMM connection to the gateway service of the TV: its own service (…0007) first, the old one it shared with the SSH tunnel (…0002) only for a TV that is, or
     * may be, old (R-28; the choice, the pause between attempts and the memory of an old TV are [GatewayService.PhoneChoice], tested on the JVM). The connect itself goes through the
     * lock shared with the API tunnel and the remote control ([BtConnectLock]): two connect() to the same TV at once make the Bluetooth stack answer « already at opened state ».
     */
    private fun connectGateway(adapter: BluetoothAdapter, address: String, choice: GatewayService.PhoneChoice): BluetoothSocket {
        val dev = adapter.getRemoteDevice(address)
        val advertised = runCatching { dev.uuids?.map { it.uuid.toString() } }.getOrNull()      // Android's cached SDP answer; null when unknown
        return choice.connect(advertised, stopping = { stopping }, log = { Log.i(TAG, it) }) { uuid ->
            val s = dev.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
            sock = s
            try { synchronized(BtConnectLock.of(address)) { s.connect() }; s } catch (e: IOException) { runCatching { s.close() }; throw e }
        }
    }

    /**
     * R-33 (audit I-13): tells the TV, on its owner channel (RELAY_STATE), whether the phone's network is billed: after every handshake (the TV forgets it at each disconnection) and at
     * each change while the pipe is up. Best effort, rate-limited by [MeteredAnnouncer]; an unknown network says nothing (the TV then counts it as billed).
     */
    private fun announce(r: Run, a: MeteredAnnouncer, net: PhoneNet) {
        a.next(net, System.currentTimeMillis())?.let { RelayRuntime.report(this, r.address, it) }
    }

    private fun loop(r: Run, manualPin: String?) {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        var backoff = 1000L
        val choice = GatewayService.PhoneChoice()            // remembers « old TV » for a while (R-28), once the gateway HELLO was answered (R-36)
        val announcer = MeteredAnnouncer()                   // what the TV was told about the phone's network during this run (R-33)
        val idle = IdleStop(start = System.currentTimeMillis())
        while (!stopping) {
            if (r.auto && idle.expired(System.currentTimeMillis())) { Log.i(TAG, "idle: the pipe closes"); break }
            if (adapter == null || !adapter.isEnabled) { _state.value = "Bluetooth désactivé"; Thread.sleep(3000); continue }
            try {
                runCatching { adapter.cancelDiscovery() }   // needs BLUETOOTH_SCAN on Android 12+: optional, never fatal
                _state.value = "Connexion à la TV…"
                val s = connectGateway(adapter, r.address, choice)
                backoff = 1000
                _state.value = "La TV utilise l'Internet du téléphone"
                notify(r.name)
                val mux = Mux(s.inputStream, s.outputStream)
                val t0 = System.currentTimeMillis()
                active = true
                // automatic: the credential is read from the phone's own keeping (the PIN typed once, or the trust token) each time, never carried in an Intent
                // R-30: a TV known by its Bluetooth address keeps its code under « bt:<ADDRESS> » (PinKeys.btKey), not under the bare address
                val credential = manualPin ?: runCatching { PinStore(applicationContext).get(PinKeys.btKey(r.address), r.name) }.getOrDefault("")
                val exit = Exit(mux, TvAuth.btPin(credential), connect = dialer::connect, log = { Log.i(TAG, it) }, diag = ::runDiag,
                    // the TV answered HELLO_OK: this connection IS its gateway (R-36: only now an old UUID means an old TV) and it forgot the phone's network (R-33: say it again)
                    onHello = { choice.helloAnswered(); announcer.connected(); announce(r, announcer, RelayRuntime.phoneNet(this)) })
                val watch = if (r.auto) watchdog(r, mux, exit, idle, announcer) else null
                try {
                    exit.run()
                } finally {
                    watch?.interrupt()
                    active = false
                    PhoneConnect.track("gateway_session", mapOf("ms" to System.currentTimeMillis() - t0,
                        "bytes" to mux.received.get() + mux.sent.get()))
                }
            } catch (e: Exception) {
                Log.w(TAG, "gateway link ended: ${e.javaClass.simpleName}")
                if (e.message?.contains("PIN") == true) {
                    _state.value = "Code PIN refusé par la TV"; stopping = true
                    if (r.auto) { RelayRuntime.userStopped(r.address); RelayRuntime.report(this, r.address, RelayFrames.State(RelayFrames.Phase.REFUSED, RelayReason.NOT_SYNCED)) }
                    break
                }
                if (!stopping) _state.value = "Liaison perdue (${e.javaClass.simpleName}: ${e.message}), nouvel essai…"
            } finally { runCatching { sock?.close() } }
            if (!stopping) { Thread.sleep(backoff); backoff = minOf(backoff * 2, 15_000) }
        }
        running = false
        if (r.auto) RelayRuntime.settings(this).clearSession()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    /**
     * Every 5 s while an automatic pipe is linked: counts the bytes on a metered network (REL-F7), re-judges the cost policy (the network may have become metered, the daily cap reached,
     * the relay withdrawn for this TV), closes the pipe 10 minutes after the last connection, and keeps the session fresh for a restart by the system. Stops with the reason told to the TV.
     */
    private fun watchdog(r: Run, mux: Mux, exit: Exit, idle: IdleStop, announcer: MeteredAnnouncer): Thread = thread(name = "bt-gateway-watch", isDaemon = true) {
        val settings = RelayRuntime.settings(this)
        val meter: RelayMeter = RelayRuntime.meter(this)
        var last = mux.received.get() + mux.sent.get()
        var offlineSince = 0L
        var savedAt = 0L
        try {
            while (!stopping) {
                Thread.sleep(5_000)
                val now = System.currentTimeMillis()
                val total = mux.received.get() + mux.sent.get()
                val delta = (total - last).coerceAtLeast(0L); last = total
                val net = RelayRuntime.phoneNet(this)
                // framing included: a few per cent more than what the carrier counts ; a network that is not SURELY free counts (R-33: unknown = billed, e.g. mobile data not yet validated)
                if (RelayCost.countsAgainstCap(net, settings.allowMobile)) meter.add(delta)
                announce(r, announcer, net)                                                    // the phone moved from Wi-Fi to mobile data (or back): the TV must know (REL-F7)
                idle.update(now, exit.openStreams, total)
                offlineSince = if (net == PhoneNet.NONE) (if (offlineSince == 0L) now else offlineSince) else 0L
                if ((exit.openStreams > 0 || delta >= IdleStop.NOISE_BYTES) && now - savedAt >= 60_000L) {
                    savedAt = now; settings.saveSession(RelaySettings.Session(r.address, r.name, r.limitBytes, r.metered, now, r.need))
                }
                // judged with the need the pipe was opened for (R-33): a big download opened on Wi-Fi stops on mobile data instead of eating the cap kept for the game and the wallet
                val d = RelayPolicy.decide(RelayInput(synced = true, optedOut = settings.optedOut(r.address), net = net, allowMobile = settings.allowMobile, usedTodayBytes = meter.usedToday(), need = r.need))
                val why: RelayReason? = when {
                    d is RelayDecision.Refuse && d.reason != RelayReason.OFFLINE -> d.reason
                    offlineSince != 0L && now - offlineSince > 2 * 60_000L -> RelayReason.OFFLINE
                    else -> null
                }
                if (why != null) {
                    Log.i(TAG, "pipe stopped: ${why.wire}")
                    RelayRuntime.report(this, r.address, RelayFrames.State(RelayFrames.Phase.REFUSED, why, net.meteredFlag()))
                    stopping = true; runCatching { sock?.close() }
                    return@thread
                }
                if (idle.expired(now)) { Log.i(TAG, "idle for 10 minutes: the pipe closes"); stopping = true; runCatching { sock?.close() }; return@thread }
            }
        } catch (_: InterruptedException) {}
    }

    /** ping / traceroute from the phone (the TV's way out to the Internet); host already validated by the gateway. */
    private fun runDiag(kind: String, host: String, line: (String) -> Unit) {
        val ip = runCatching { InetAddress.getByName(host).hostAddress }.getOrElse { line("Nom introuvable : $host"); return }
        if (kind == "ping") {
            line("PING $host ($ip) depuis le téléphone")
            val p = ProcessBuilder("/system/bin/ping", "-c", "4", "-W", "2", ip).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) line(it) }
            p.waitFor()
            return
        }
        // traceroute with ping's TTL: each hop answers "Time to live exceeded" from its own address.
        line("TRACEROUTE $host ($ip) depuis le téléphone, 20 sauts max")
        for (ttl in 1..20) {
            val t0 = System.nanoTime()
            val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", "-t", ttl.toString(), ip).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText(); p.waitFor()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val from = Regex("[Ff]rom ([0-9a-fA-F.:]+)").find(out)?.groupValues?.get(1)?.trimEnd(':')
            val time = Regex("time=([0-9.]+) ?ms").find(out)?.groupValues?.get(1)
            when {
                time != null -> { line("%2d  %-39s %s ms".format(ttl, ip, time)); line("Arrivé en $ttl sauts."); return }
                from != null -> line("%2d  %-39s ~%d ms".format(ttl, from, ms))
                else -> line("%2d  *".format(ttl))
            }
        }
        line("Destination non atteinte en 20 sauts.")
    }

    /**
     * The only notification of the relay: neutral (« CastBridge relaie pour <TV> »: no file name, no content), LOW, private on the lock screen (the public version says nothing but
     * « CastBridge »), with a button « Arrêter ». The same text for both ways to start it.
     */
    private fun notification(tvName: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, RelayText.NOTIFICATION_CHANNEL, NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        val stop = PendingIntent.getService(this, 1, Intent(this, BtGatewayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val generic = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle(RelayText.NOTIFICATION_PUBLIC).build()
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge)
            .setContentTitle("CastBridge").setContentText(RelayText.notification(tvName)).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(generic).setCategory(Notification.CATEGORY_SERVICE).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, RelayText.STOP, stop).build()).build()
    }
    private fun notify(tvName: String) = getSystemService(NotificationManager::class.java).notify(NOTIF, notification(tvName))

    companion object {
        private const val TAG = "CastBridgeGW"
        private const val CHANNEL = "gateway"
        private const val NOTIF = 43
        private const val EXTRA_ADDR = "addr"
        private const val EXTRA_PIN = "pin"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_AUTO = "auto"
        private const val EXTRA_LIMIT = "limit"
        private const val EXTRA_METERED = "metered"
        private const val EXTRA_NEED = "need"
        private const val ACTION_STOP = "castbridge.sender.GATEWAY_STOP"
        private val _state = MutableStateFlow("Partage inactif")
        /** A TV is using the phone's Internet right now (reported to the server as btGateway). */
        @Volatile var active = false; private set
        /** The service is up (linked or reconnecting): the TV's request needs no second start. */
        @Volatile var running = false; private set
        val state: StateFlow<String> = _state

        fun start(ctx: Context, tvAddress: String, pin: String) {
            PhoneConnect.feature("bt_gateway")
            ctx.startForegroundService(Intent(ctx, BtGatewayService::class.java).putExtra(EXTRA_ADDR, tvAddress).putExtra(EXTRA_PIN, pin))
        }

        /**
         * relay-R1: the TV asked for a pipe and the policy allowed it: starts the service silently (no credential in the Intent: the service reads it from the phone's own keeping).
         * False when Android refuses to start a foreground service from the background (the phone then answers « background » to the TV).
         */
        fun startAuto(ctx: Context, tvAddress: String, tvName: String, limitBytes: Long?, metered: Boolean, need: PipeNeed?): Boolean = try {
            PhoneConnect.feature("bt_gateway")
            ctx.startForegroundService(Intent(ctx, BtGatewayService::class.java).putExtra(EXTRA_ADDR, tvAddress).putExtra(EXTRA_NAME, tvName)
                .putExtra(EXTRA_AUTO, true).putExtra(EXTRA_LIMIT, limitBytes ?: -1L).putExtra(EXTRA_METERED, metered).putExtra(EXTRA_NEED, need?.wire))
            true
        } catch (e: Exception) { Log.w(TAG, "automatic start refused: ${e.javaClass.simpleName}"); false }

        fun stop(ctx: Context) = ctx.startService(Intent(ctx, BtGatewayService::class.java).setAction(ACTION_STOP))
    }
}
