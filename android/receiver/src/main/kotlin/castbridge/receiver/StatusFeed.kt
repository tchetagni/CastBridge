package castbridge.receiver

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.wifi.WifiManager
import castbridge.core.net.LinkKind
import castbridge.core.status.IconKind
import castbridge.core.status.Tech
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.status.DirectPhase
import castbridge.core.tv.status.LicenceReader
import castbridge.core.tv.status.StatusFeedRules
import castbridge.core.tv.status.StatusRules
import castbridge.core.tv.status.StatusSnapshot
import castbridge.core.tv.status.StatusThresholds
import castbridge.receiver.quiz.PlayHub
import castbridge.receiver.wallet.WalletHub

/**
 * Reads what the TV ALREADY knows into a [StatusSnapshot] (core): no network call, no probe, nothing faster than the existing 5 s tick of [TvService.syncIcons]
 * (a local read of the Wi-Fi / Bluetooth state, the volumes' free space, the trust list, the transfers, the activations and the wallet state).
 * Every source is read defensively: a source that fails leaves its field « not measured » (grey), never green.
 */
object StatusFeed {
    fun snapshot(svc: TvService): StatusSnapshot {
        val ctx: Context = svc.applicationContext
        var s = StatusSnapshot()

        // Wi-Fi and Internet (the network round of TvService: system validation, or the probe when it is allowed)
        runCatching {
            val link = TvNetDiag.linkKind(svc)
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val enabled = wm?.isWifiEnabled
            @Suppress("DEPRECATION") val rssi = if (link == LinkKind.WIFI) wm?.connectionInfo?.rssi?.takeIf { it > -127 } else null
            val checked = svc.netCheckedAt > 0
            s = s.copy(wifiEnabled = enabled, wifiConnected = link == LinkKind.WIFI, wifiRssiDbm = rssi,
                wifiHasInternet = if (link == LinkKind.WIFI && checked) svc.netDirectMs != null else null,
                internet = StatusFeedRules.internetPath(svc.netState, svc.netMeasured),
                internetExpected = link != LinkKind.NONE || svc.gateway?.connected == true,
                internetLatencyMs = svc.netDirectMs?.takeIf { it > 0 })
        }

        // Wi-Fi Direct (opt-in)
        runCatching {
            val wd = svc.wd
            val enabled = svc.prefs.getBool("wd_enabled", false) || wd?.active != null
            val phase = when { wd?.active != null -> DirectPhase.GROUP_ACTIVE; wd?.lastError != null -> DirectPhase.FAILED; else -> DirectPhase.IDLE }
            s = s.copy(wifiDirectEnabled = enabled, wifiDirect = phase)
        }

        // Bluetooth: adapter on/off, a phone linked by Bluetooth, the phone gateway (slow when its last measure is slow)
        runCatching {
            @Suppress("DEPRECATION") val adapter = BluetoothAdapter.getDefaultAdapter()
            val phoneBt = svc.icons.snapshot().all.any { it.kind == IconKind.PHONE && it.tech == Tech.BLUETOOTH }
            val gw = svc.gateway?.connected == true
            val slow = gw && (svc.netGatewayMs ?: 0L) >= StatusThresholds.SLOW_LATENCY_MS
            s = s.copy(btEnabled = adapter?.isEnabled == true, bt = StatusFeedRules.bluetoothPhase(phoneBt, gw), btSlowNetwork = slow)
        }

        // Storage: the tightest volume, read straight from the file system (statfs, cheap)
        runCatching {
            val vols = svc.registry.volumes().filter { it.kind != VolumeKind.SAF }.map {
                StatusFeedRules.Vol(it.dir.usableSpace, it.dir.totalSpace.takeIf { t -> t > 0 } ?: it.total, it.writable)
            }
            val r = StatusFeedRules.storage(vols)
            s = s.copy(storagePresent = r.present, storageFreeBytes = r.free, storageTotalBytes = r.total, storageReadOnly = r.readOnly)
        }

        // Phones synchronised with this TV (n/8)
        runCatching { s = s.copy(phones = svc.trust.list().size) }

        // Copy in progress / slowed / failed (the reception model, kept 8 s after the end)
        runCatching {
            val running = svc.reception.active()
            val m = castbridge.core.xfer.CopyBadge.of(running)
            val failed = running.isEmpty() && svc.reception.shown().any { it.phase == castbridge.core.xfer.TransferProgress.Phase.FAILED }
            s = s.copy(copyRunning = running.isNotEmpty(), copyPercent = m.percent, copySlowed = m.tone == castbridge.core.xfer.CopyBadge.Tone.SLOWED, copyFailed = failed)
        }

        // Wallet state: tokens synchronised / offline / never, and the activation waiting for its notification
        val wallet = runCatching { WalletHub.statusView() }.getOrNull()
        if (wallet != null) runCatching {
            s = s.copy(tokens = StatusFeedRules.tokens(wallet.state), tokenBalance = if (wallet.showBalances) WalletHub.snapshot()?.n?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() else null)
        }

        // Licence: the signed usage right of the installed activations (same calculation as the key badge)
        runCatching {
            val now = ActivationCenter.now()
            val r = LicenceReader.read(ActivationCenter.allActivations(), now, BuildConfig.REQUIRE_ACTIVATION, wallet?.let { StatusFeedRules.licencePending(it.state) } == true)
            s = s.copy(licence = r.state, licenceTiming = r.timing, nowMs = now)
        }

        // Online quiz: only when its flag is on
        runCatching { s = s.copy(quiz = StatusFeedRules.quiz(PlayHub.flagOn(ctx), ActivationCenter.locked(), PlayHub.serviceUp())) }
        return s
    }
}
