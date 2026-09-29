package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import castbridge.core.ssh.SshTunnel
import java.io.IOException
import java.util.UUID

/**
 * "SSH over Bluetooth" on the TV: a second RFCOMM service ("CastBridge SSH", its own UUID, separate from the file service)
 * whose every connection is relayed, byte for byte, to the local SSH server (127.0.0.1:2222) by [SshTunnel]. It runs only
 * while SSH is on; the SSH server still does all the checking (keys, lockout per Bluetooth device, timeouts).
 */
@SuppressLint("MissingPermission")
class BtSshBridge(private val ctx: Context, private val tunnel: SshTunnel, private val status: (String?) -> Unit) {
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile var running = false; private set

    private fun allowed() = Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @Synchronized fun start() {
        if (running) return
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        when {
            ad == null -> { status(null); return }
            !allowed() -> { status("SSH par Bluetooth : permission Bluetooth refusée"); return }
            !ad.isEnabled -> { status("SSH par Bluetooth : Bluetooth désactivé"); return }
        }
        val ss = try { ad!!.listenUsingRfcommWithServiceRecord("CastBridge SSH", SSH_UUID) }
        catch (e: Exception) { Log.w(TAG, "listen: ${e.javaClass.simpleName}"); status("SSH par Bluetooth : écoute impossible"); return }
        server = ss; running = true
        refresh()
        Thread({ acceptLoop(ss) }, "bt-ssh-accept").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        status(null)
    }

    private fun acceptLoop(ss: BluetoothServerSocket) {
        while (running) {
            val sock = try { ss.accept() } catch (e: IOException) { break }
            val addr = runCatching { sock.remoteDevice.address }.getOrDefault("?")
            val name = runCatching { sock.remoteDevice.name }.getOrNull() ?: addr
            Thread({
                val ok = tunnel.serve(addr, name, sock.inputStream, sock.outputStream, { runCatching { sock.close() } }) { refresh() }
                if (!ok) Log.i(TAG, "bt-ssh: link refused (limit or SSH off)")
                refresh()
            }, "bt-ssh-link").apply { isDaemon = true; start() }
        }
        if (running) { running = false; status("SSH par Bluetooth : arrêté") }
    }

    private fun refresh() {
        if (!running) return
        val a = tunnel.active()
        status(if (a.isEmpty()) "SSH par Bluetooth : prêt (service « CastBridge SSH »)"
               else "SSH par Bluetooth : connecté (${a.joinToString(", ") { it.name }})")
    }

    companion object {
        private const val TAG = "CastBridgeSSH"
        /** RFCOMM service of the SSH tunnel (the file service is ...0001). Also in tools/bt-ssh-bridge.py and the phone app. */
        val SSH_UUID: UUID = UUID.fromString(castbridge.core.tv.BtProtocol.SSH_SERVICE_UUID)
    }
}
