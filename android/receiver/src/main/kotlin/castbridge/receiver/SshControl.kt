package castbridge.receiver

import android.content.Context
import android.util.Log
import castbridge.core.ssh.AuthorizedKeys
import castbridge.core.ssh.SshPolicy
import castbridge.core.ssh.SshTunnel
import castbridge.core.tv.ApiReply
import castbridge.core.tv.ReceiverServer
import castbridge.sshd.TvSshServer
import java.io.File

/**
 * SSH administration of the TV, off until someone switches it on (MENU on the remote, or the
 * PIN-protected API). Keys and the host key live in the app's private storage.
 */
class SshControl(ctx: Context, private val onChange: (String?) -> Unit, onBtStatus: (String?) -> Unit = {}) {
    private val server: TvSshServer = TvSshServer(
        dataDir = File(ctx.filesDir, "ssh"),
        sftpRoot = ctx.getExternalFilesDir(null) ?: ctx.filesDir,
        policy = SshPolicy(),
        log = { m ->
            Log.i(TAG, m)                                       // never contains keys or the PIN
            if ("stopping" in m) { onChange(null); bridge.stop() }
        },
    )
    /** SSH over Bluetooth: an RFCOMM byte tunnel to this server, on only while SSH is on (docs/ADMIN.md). */
    private val tunnel: SshTunnel = SshTunnel(server.peers, server.port, maxConnections = 2)
    private val bridge: BtSshBridge = BtSshBridge(ctx, tunnel, onBtStatus)

    val running get() = server.running

    /** One line for the idle screen, null when SSH is off. */
    fun statusLine(): String? = if (!server.running) null else
        "SSH actif : port ${server.port}, arrêt auto dans ${(server.policy.secondsLeft() + 59) / 60} min" +
            (server.hostKeyFingerprint()?.let { "\nEmpreinte : $it" } ?: "")

    fun enable(minutes: Int? = null): String? {
        server.start(minutes)
        bridge.start()
        return statusLine().also(onChange)
    }

    fun disable() { bridge.stop(); server.stop(); onChange(null) }

    fun stop() { bridge.stop(); server.stop() }

    /** Routes /api/ssh*, or null if [path] is not one of them. */
    fun api(path: String, method: String, p: Map<String, String>): ApiReply? = try { route(path, method, p) } catch (t: Throwable) {
        // Errors (not only exceptions) from the SSH library must never take the whole TV app down.
        Log.e(TAG, "ssh api", t)
        ApiReply(500, """{"error":${ReceiverServer.q(t.javaClass.simpleName + ": " + (t.message ?: ""))}}""")
    }

    private fun route(path: String, method: String, p: Map<String, String>): ApiReply? = when {
        path == "/api/ssh" && method == "GET" -> ApiReply(200, json())
        path == "/api/ssh/enable" && method == "POST" -> {
            enable(p["minutes"]?.toIntOrNull()); ApiReply(200, json())
        }
        path == "/api/ssh/disable" && method == "POST" -> { disable(); ApiReply(200, json()) }
        path == "/api/ssh/key" && method == "POST" -> try {
            server.addKey(p["key"].orEmpty()); ApiReply(200, json())
        } catch (e: AuthorizedKeys.Invalid) {
            ApiReply(400, """{"error":${ReceiverServer.q(e.message ?: "clé invalide")}}""")
        }
        path == "/api/ssh/key/remove" && method == "POST" ->
            ApiReply(if (server.removeKey(p["fp"].orEmpty())) 200 else 404, json())
        else -> null
    }

    private fun json(): String {
        val keys = server.keys().joinToString(",") {
            """{"fp":${ReceiverServer.q(it.fingerprint)},"type":${ReceiverServer.q(it.type)},"comment":${ReceiverServer.q(it.comment)}}"""
        }
        return """{"enabled":${server.running},"port":${server.port},"idleMinutes":${server.policy.idleMinutes},""" +
            """"secondsLeft":${server.policy.secondsLeft()},"fingerprint":${server.hostKeyFingerprint()?.let(ReceiverServer::q) ?: "null"},""" +
            """"keys":[$keys],"bluetooth":{"listening":${bridge.running},"maxConnections":2,"active":[""" +
            tunnel.active().joinToString(",") { ReceiverServer.q(it.name) } + "]}}"
    }

    private companion object { const val TAG = "CastBridgeSSH" }
}
