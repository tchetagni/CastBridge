package castbridge.core.connect

import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotRemote
import java.io.IOException
import java.net.Proxy

/**
 * [LotRemote] qui passe par [Routes] (relay-R1, inventaire I-3) : le réseau propre de la TV d'abord, puis le tuyau d'un téléphone, comme tout appel de la TV vers le serveur
 * (battement de cœur, mises à jour, portefeuille, jeu). Avant, « Mettre à jour les lots Langues » n'avait pas de proxy et ne profitait donc pas du partage d'Internet.
 * [make] fabrique le client d'un chemin (`null` = réseau propre, sinon le proxy du tuyau).
 *
 * Une RÉPONSE du serveur (404, redirection refusée, lot retiré…) n'est pas une panne de réseau : un second chemin n'y changerait rien, l'erreur sort telle quelle, du même type.
 */
class RoutedLotRemote(private val routes: Routes, private val make: (Proxy?) -> LotRemote) : LotRemote {
    private fun <T> via(block: (LotRemote) -> T): T {
        val r = routes.call<Result<T>> { p ->
            try { Result.success(block(make(p))) }
            catch (e: IOException) { if (isAnswer(e)) Result.failure(e) else throw e }
        }
        return r.getOrThrow()
    }

    override fun catalogJson(channel: String): String = via { it.catalogJson(channel) }
    override fun catalogJson(channel: String, feature: String?): String = via { it.catalogJson(channel, feature) }
    override fun open(m: LotMeta, offset: Long): LotRemote.Stream = via { it.open(m, offset) }

    private fun isAnswer(e: IOException): Boolean {
        if (e is LotRemote.Gone) return true
        val m = e.message ?: return false
        return ANSWERS.any { m.startsWith(it) }
    }

    private companion object {
        /** Les messages que [SecureHttpLotRemote] lève APRÈS avoir reçu une réponse du serveur. */
        val ANSWERS = listOf("HTTP ", "redirection refusée", "reprise refusée", "catalogue trop gros", "lot invalide")
    }
}
