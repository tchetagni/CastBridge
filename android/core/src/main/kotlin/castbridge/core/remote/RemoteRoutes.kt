package castbridge.core.remote

import castbridge.core.remote.hid.HidRemote

/**
 * Minimal stand-in for the `RemoteStrategy` interface of the sibling branch claude/smart-remote (docs/agent-briefs/smart-remote.md):
 * same idea, kept in its own file so the owner can replace it by the real one when merging. Nothing here depends on it.
 */
interface BtRemoteStrategy {
    val id: String
    val state: RouteStatus
    /** Keys this strategy can send. */
    val keys: Set<RemoteKey>
    fun send(k: RemoteKey, action: KeyAction): Boolean
    fun close()
}

/** The three Bluetooth routes of the owner's TV (docs/REMOTE.md). */
enum class BtRoute(val label: String) {
    NATIVE("CastBridge par Bluetooth"), VENDOR("Toute la TV (service du fabricant)"), HID("Le téléphone comme clavier Bluetooth")
}

/** What the "Ma TV" screen shows for a route. */
enum class RouteStatus(val label: String) {
    AVAILABLE("disponible"), UNSUPPORTED("non pris en charge"), TO_TEST("à tester"), CONFIRMED("confirmée par un test"), FAILED("test échoué")
}

/** Wi-Fi or Bluetooth, for "Bluetooth exclusivement". */
enum class LinkKind { WIFI, BLUETOOTH }

object RemotePlan {
    /** The links the phone may try, in order. [btOnly] never includes Wi-Fi; without a Bluetooth address that leaves nothing. */
    fun links(hasHost: Boolean, hasBt: Boolean, btOnly: Boolean): List<LinkKind> =
        if (btOnly) (if (hasBt) listOf(LinkKind.BLUETOOTH) else emptyList())
        else buildList { if (hasHost) add(LinkKind.WIFI); if (hasBt) add(LinkKind.BLUETOOTH) }

    /** Status line of the remote screen. */
    fun linkMessage(via: String?, btOnly: Boolean): String? = when {
        via == "bt" || via == "Bluetooth" -> if (btOnly) "Bluetooth exclusivement" else "Bluetooth seulement"
        else -> null
    }

    /** Keys worth showing for the current TV state: hidden when no route can carry them. */
    fun availableKeys(castbridgeFront: Boolean, accessibility: Boolean, vendorReady: Boolean): Set<RemoteKey> =
        RemoteKey.values().filter { k ->
            when {
                k.kind == RemoteKey.Kind.VOLUME -> true
                vendorReady -> true
                castbridgeFront -> true
                k == RemoteKey.HOME -> true
                accessibility -> k.kind in setOf(RemoteKey.Kind.NAV, RemoteKey.Kind.MEDIA) || k == RemoteKey.BACK || k.kind == RemoteKey.Kind.TEXT || k.kind == RemoteKey.Kind.DIGIT
                else -> k.kind == RemoteKey.Kind.MEDIA
            }
        }.toSet()

    /** Keys the HID route can carry (the rest is hidden when it is the only route). */
    fun hidKeys(): Set<RemoteKey> = HidRemote.supportedKeys.toSet()
}

/** Copiable diagnostic: states only, no PIN, no token, no full Bluetooth address, no IP. */
object RemoteDiagnostics {
    private val MAC = Regex("(?i)\\b([0-9a-f]{2}[:-]){5}([0-9a-f]{2})\\b")
    private val IPV4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")

    /** "AA:BB:CC:DD:EE:FF" → "**:**:**:**:**:FF". */
    fun maskMac(s: String): String = MAC.replace(s) { "**:**:**:**:**:" + it.groupValues[2].uppercase() }
    fun redact(s: String): String = IPV4.replace(maskMac(s), "x.x.x.x")

    fun report(appVersion: String, btOnly: Boolean, routes: Map<BtRoute, RouteStatus>, notes: List<String> = emptyList()): String = buildString {
        appendLine("CastBridge – diagnostic de télécommande Bluetooth")
        appendLine("Version : ${redact(appVersion)}")
        appendLine("Bluetooth exclusivement : ${if (btOnly) "oui" else "non"}")
        BtRoute.values().forEach { appendLine("${it.label} : ${(routes[it] ?: RouteStatus.TO_TEST).label}") }
        notes.forEach { appendLine("- " + redact(it)) }
    }.trimEnd()
}
