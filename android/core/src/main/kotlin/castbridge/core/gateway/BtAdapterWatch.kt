package castbridge.core.gateway

/**
 * When the TV's Bluetooth adapter comes back, the services that listen on it must listen again (R-42, audit anti-régression 2026-10-07 b, I-12): switching Bluetooth off kills every
 * server socket, whatever the accept loops do ([AcceptLoop] only covers a short outage). The TV registers for `BluetoothAdapter.ACTION_STATE_CHANGED` and asks this rule.
 *
 * The values are those of `android.bluetooth.BluetoothAdapter.STATE_*`, duplicated here because the core is plain JVM (a test pins them).
 */
object BtAdapterWatch {
    const val STATE_OFF = 10
    const val STATE_TURNING_ON = 11
    const val STATE_ON = 12
    const val STATE_TURNING_OFF = 13

    /**
     * The adapter went from [previous] (null = not known: the first broadcast seen) to [now]: true when it has JUST come back ON, so that the gateway (and whatever else listens)
     * must listen again. A repeated ON, or any other transition, changes nothing.
     */
    fun listenAgain(previous: Int?, now: Int): Boolean = now == STATE_ON && previous != STATE_ON
}
