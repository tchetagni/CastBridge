package castbridge.core.remote.hid

import castbridge.core.remote.BtRemoteStrategy
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RouteStatus

/**
 * "Bluetooth HID" strategy. [enabled] is false until the user has confirmed by a test that their TV reacts (the TV may accept
 * the pairing and still ignore a phone as a keyboard); [confirmed] is that test result, stored by the app.
 */
class HidStrategy(transport: HidTransport, @Volatile var enabled: Boolean = false, @Volatile var confirmed: Boolean = false, private val hostRefused: () -> Boolean = { false }) : BtRemoteStrategy {
    private val sender = HidKeySender(transport)
    private val transport = transport
    override val id = "bt-hid"
    override val keys: Set<RemoteKey> get() = HidRemote.supportedKeys.toSet()
    override val state: RouteStatus get() = when {
        hostRefused() -> RouteStatus.UNSUPPORTED
        confirmed -> RouteStatus.CONFIRMED
        transport.connected -> RouteStatus.TO_TEST
        else -> RouteStatus.AVAILABLE
    }

    override fun send(k: RemoteKey, action: KeyAction): Boolean {
        if (!enabled) return false
        return when (action) {
            KeyAction.PRESS, KeyAction.LONG -> sender.tap(k)
            KeyAction.DOWN -> sender.down(k)
            KeyAction.UP -> sender.up(k)
        }
    }

    override fun close() = sender.releaseAll()
}
