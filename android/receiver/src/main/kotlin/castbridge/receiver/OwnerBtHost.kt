package castbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.util.Log
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.Channel
import castbridge.core.owner.OwnerChannelServer
import castbridge.core.owner.OwnerFrames
import java.io.IOException
import java.util.UUID

/**
 * The owner Bluetooth channel of CastBridge-TV: lets the owner's phone read the device request and push an activation (the long key nobody can type with
 * a remote). The RFCOMM service is the secure kind (the phone must be paired), and the activation itself is verified exactly like a typed or file one
 * (signature, this device, key scope), so a stranger who paired gains nothing. Started even while the TV is locked: that is when it is needed.
 */
@SuppressLint("MissingPermission")
class OwnerBtHost(private val ctx: Context, private val status: (String?) -> Unit = {}) {
    private companion object { const val TAG = "OwnerBt" }
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var running = false
    /** What the activation screen shows: is the channel the owner's phone talks to listening, and if not, why. */
    @Volatile var state: String = "pas encore démarré"; private set
    private val slots = java.util.concurrent.Semaphore(2)

    private val channel = OwnerChannelServer(
        deviceInfo = { ActivationCenter.requestText() },
        // NOT activated here: the key is verified, placed in the field of the activation screen, and the owner confirms on the TV
        activate = { token -> ActivationCenter.stage(token).also { if (it is ActivationResult.Accepted) showActivationScreen() } },
        acceptedText = "Clé reçue et valide : sur la TV, appuyez sur « Valider la clé »",
    )

    private fun showActivationScreen() { runCatching { ctx.startActivity(android.content.Intent(ctx, ActivationActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    @Synchronized fun start() {
        if (running) return
        val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: run { state = "pas de Bluetooth sur cette TV"; return }
        if (!ad.isEnabled) { state = "Bluetooth éteint (réglages de la TV)"; return }
        val ss = try { ad.listenUsingRfcommWithServiceRecord("CastBridge Owner", UUID.fromString(OwnerFrames.SERVICE_UUID)) }
        catch (e: Exception) { Log.w(TAG, "listen failed: ${e.message}"); state = if (e is SecurityException) "permission Bluetooth refusée" else "écoute impossible (${e.message})"; return }
        server = ss; running = true; state = "prêt (${runCatching { ad.name }.getOrNull() ?: "TV"})"
        Thread({
            while (running) {
                val sock = try { ss.accept() } catch (e: IOException) { break }
                if (!slots.tryAcquire()) { runCatching { sock.close() }; continue }
                Thread({
                    try { sock.use { channel.serve(it.inputStream, it.outputStream) } }
                    catch (e: Exception) { Log.w(TAG, "link: ${e.message}") }
                    finally { slots.release() }
                }, "owner-bt-link").apply { isDaemon = true; start() }
            }
            running = false; state = "arrêté"
        }, "owner-bt-accept").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() { running = false; runCatching { server?.close() }; server = null }
}
