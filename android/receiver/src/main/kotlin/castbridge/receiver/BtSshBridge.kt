package castbridge.receiver

import android.content.Context
import castbridge.core.ssh.SshTunnel
import java.util.UUID

/**
 * "SSH over Bluetooth" on the TV: a second RFCOMM service ("CastBridge SSH", its own UUID, separate from the file service)
 * whose every connection is relayed, byte for byte, to the local SSH server (127.0.0.1:2222) by [SshTunnel]. It runs only
 * while SSH is on; the SSH server still does all the checking (keys, lockout per Bluetooth device, timeouts).
 */
class BtSshBridge(ctx: Context, private val tunnel: SshTunnel, status: (String?) -> Unit) {
    private val bridge = BtTunnelBridge(ctx, "CastBridgeSSH", "SSH par Bluetooth", "CastBridge SSH", SSH_UUID,
        serve = { peer, name, i, o, close, onChange -> tunnel.serve(peer, name, i, o, close, onChange) },
        activeNames = { tunnel.active().map { it.name } }, lastError = { tunnel.lastError }, status = status)

    val running get() = bridge.running
    fun start() = bridge.start()
    fun stop() = bridge.stop()

    companion object {
        /** RFCOMM service of the SSH tunnel (the file service is ...0001). Also in tools/bt-ssh-bridge.py, tools/cbt-rfcomm and the phone app. */
        val SSH_UUID: UUID = UUID.fromString(castbridge.core.tv.BtProtocol.SSH_SERVICE_UUID)
    }
}
