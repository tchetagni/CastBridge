package castbridge.core.remote

import castbridge.core.tv.OpenTvScreen
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * La voie Bluetooth de « Ouvrir sur la TV » quand la télécommande est déjà reliée en Bluetooth (R-35, audit anti-régression 2026-10-07 b, I-14).
 *
 * La télécommande tient ouverte une liaison CBTR vers le service Bluetooth de la TV ; la voie Bluetooth de « Ouvrir sur la TV » ouvrait une SECONDE liaison vers le même service (même
 * canal RFCOMM : refusée), d'où « La TV ne répond pas : allumez-la » alors que la télécommande marchait. Si la liaison de la télécommande vers CETTE TV est vivante ([live]), la commande
 * `open` part dessus ([OpenTvWire.send], la ligne `POST open?screen=…` du canal ; les envois de la liaison sont sérialisés : la ping de la télécommande n'est pas mêlée à la réponse) ;
 * sinon, ou si elle casse sous nos yeux, la liaison à part ([fresh]) avec ce qui reste du temps.
 *
 * Bornée comme toute voie : si la TV ne répond pas dans le temps donné, la liaison de la télécommande est fermée (elle se rebranche seule : une liaison muette est une liaison morte) et
 * la voie échoue comme les autres. Seule une liaison Bluetooth CBTR ([RemoteBt.Transport]) est réutilisée : la voie Wi-Fi a sa propre voie.
 */
class OpenTvSessionLink(
    /** La liaison vivante de la télécommande vers la TV demandée, ou null. */
    private val live: () -> RemoteTransport?,
    private val fresh: OpenTvLink,
    private val now: () -> Long = System::currentTimeMillis,
    private val spawn: (Runnable) -> Unit = { r -> Thread(r, "open-tv-session").apply { isDaemon = true }.start() },
) : OpenTvLink {
    override val route = OpenTvRoute.BLUETOOTH

    @Throws(IOException::class)
    override fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer {
        val t = live()?.takeIf { it is RemoteBt.Transport } ?: return fresh.open(screen, timeoutMs)
        val start = now()
        try {
            return viaRemote(t, screen, timeoutMs)
        } catch (e: IOException) {
            val left = timeoutMs - (now() - start)
            if (left < OpenTvFlow.MIN_ATTEMPT_MS) throw e
            return fresh.open(screen, left)             // the remote's link broke (or was closed for being mute): a link of our own, with the time that is left
        }
    }

    private fun viaRemote(t: RemoteTransport, screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer {
        val result = AtomicReference<Result<OpenTvAnswer>?>(null)
        val done = CountDownLatch(1)
        spawn(Runnable { result.set(runCatching { OpenTvWire.send(t, screen) }); done.countDown() })
        if (!done.await(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
            runCatching { t.close() }                    // frees the blocked sender ; the remote's session reconnects by itself
            throw IOException("la TV ne répond pas par la liaison de la télécommande")
        }
        return result.get()?.getOrThrow() ?: throw IOException("la TV ne répond pas par la liaison de la télécommande")
    }
}
