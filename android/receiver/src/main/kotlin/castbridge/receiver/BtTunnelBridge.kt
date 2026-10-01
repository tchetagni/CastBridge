package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * One TCP-over-Bluetooth service of the TV: a secure RFCOMM listener (its own service record and UUID) whose every link is handed
 * to [serve] (castbridge.core.tunnel.TcpTunnel) with the Bluetooth address that the paired socket proves. Used by the SSH tunnel
 * ([BtSshBridge]) and the HTTP API tunnel ([BtApiControl]). Nothing is decided here: [serve] checks, relays and releases; this class
 * only listens, survives a failed accept (re-listens) and says everything it does in the log and on the TV's status line.
 */
@SuppressLint("MissingPermission")
class BtTunnelBridge(
    private val ctx: Context,
    private val tag: String,
    /** "SSH par Bluetooth" / "API par Bluetooth": prefix of the status lines. */
    private val label: String,
    private val serviceName: String,
    private val uuid: UUID,
    private val serve: (peer: String, name: String, input: InputStream, output: OutputStream, close: () -> Unit, onChange: () -> Unit) -> Unit,
    private val activeNames: () -> List<String>,
    private val lastError: () -> String?,
    private val status: (String?) -> Unit,
) {
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile var running = false; private set

    private fun allowed() = Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun listen(): BluetoothServerSocket? {
        val ad = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        return when {
            ad == null -> { status(null); null }
            !allowed() -> { Log.i(tag, "listen: permission Bluetooth refusée"); status("$label : permission Bluetooth refusée"); null }
            !ad.isEnabled -> { Log.i(tag, "listen: Bluetooth désactivé"); status("$label : Bluetooth désactivé"); null }
            else -> try { ad.listenUsingRfcommWithServiceRecord(serviceName, uuid) }
                catch (e: Exception) { Log.i(tag, "listen: ${e.javaClass.simpleName}"); status("$label : écoute impossible (${e.javaClass.simpleName})"); null }
        }
    }

    @Synchronized fun start() {
        if (running) return
        val ss = listen() ?: return
        server = ss; running = true
        Log.i(tag, "service \"$serviceName\" published")
        refresh()
        Thread({ acceptLoop(ss) }, "$tag-accept").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        status(null)
    }

    private fun acceptLoop(first: BluetoothServerSocket) {
        var ss: BluetoothServerSocket = first
        var failures = 0
        while (running) {
            val sock: BluetoothSocket = try { ss.accept() } catch (e: IOException) {
                if (!running) break
                // a failed accept (Bluetooth toggled, stack restarted) used to end the service silently: listen again, a few times
                Log.i(tag, "accept failed (${e.message}); listening again")
                runCatching { ss.close() }
                if (++failures > 5) break
                Thread.sleep(2000L * failures)
                val n = listen() ?: break
                synchronized(this) { if (!running) { runCatching { n.close() }; return }; server = n }
                ss = n
                continue
            }
            failures = 0
            val dev: BluetoothDevice? = runCatching { sock.remoteDevice }.getOrNull()
            val addr = dev?.address ?: "?"
            val name = runCatching { dev?.name }.getOrNull() ?: addr
            Log.i(tag, "link from $addr")
            Thread({
                try { serve(addr, name, sock.inputStream, sock.outputStream, { runCatching { sock.close() } }) { refresh() } }
                catch (e: Throwable) { Log.w(tag, "link: ${e.javaClass.simpleName}") }       // never take the TV app down; the slot is released by serve()
                finally { runCatching { sock.close() }; refresh() }
            }, "$tag-link").apply { isDaemon = true; start() }
        }
        if (running) {
            running = false
            Log.i(tag, "stopped: no more accept")
            status("$label : arrêté (liaison Bluetooth perdue, réactivez-la)")
        }
    }

    fun refresh() {
        if (!running) return
        val a = activeNames()
        val err = lastError()
        status(when {
            a.isNotEmpty() -> "$label : connecté (${a.joinToString(", ")})"
            err != null -> "$label : prêt — dernier incident : $err"
            else -> "$label : prêt (service « $serviceName »)"
        })
    }

    companion object { fun uuid(s: String): UUID = UUID.fromString(s) }
}
