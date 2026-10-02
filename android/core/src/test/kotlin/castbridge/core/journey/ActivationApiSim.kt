package castbridge.core.journey

import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply

/**
 * SIMULATION des deux routes d'activation de la TV (`GET /api/activation`, `POST /api/activation/install`) : un état mutable, pas la vraie
 * `ActivationCenter` Android et aucune vérification de signature. Le seul contenu accepté est [TEST_PAYLOAD] (une valeur de test publique,
 * jamais une clé réelle) ; tout autre corps est refusé en 422. Les routes passent derrière l'authentification de `ReceiverServer` : un jeton
 * de téléphone de confiance n'ouvre jamais `/api/activation/install` (`TvAuth.tokenMayCall`), exactement comme sur la vraie TV.
 */
class ActivationApiSim(@Volatile var trial: Boolean = false) : ApiExtension {
    @Volatile var required: Boolean = true
    @Volatile var locked: Boolean = false
    @Volatile var label: String = if (trial) "Essai" else "Version complète"
    @Volatile var usageEndsAt: Long? = null

    /** Ce que `POST /api/activation/install` a reçu et accepté, dans l'ordre. */
    val installed: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

    fun switchTrial(on: Boolean) { trial = on; label = if (on) "Essai" else "Version complète" }

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? =
        if (method == "GET" && path == "/api/activation") ApiReply(200, json()) else null

    override fun wantsBody(path: String) = path == "/api/activation/install"

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != "/api/activation/install" || method != "POST") return null
        val text = body.toString(Charsets.UTF_8).trim()
        if (text != TEST_PAYLOAD) return ApiReply(422, """{"error":"clé d'activation refusée"}""")
        installed += text
        trial = false; locked = false; label = TEST_LABEL; usageEndsAt = null
        return ApiReply(200, """{"installed":true,"label":"$TEST_LABEL"}""")
    }

    private fun json() = """{"required":$required,"locked":$locked,"trial":$trial,"label":"$label","usageEndsAt":${usageEndsAt ?: "null"}}"""

    companion object {
        /** Seule activation acceptée par la simulation (valeur de test publique, sans valeur hors de ces tests). */
        const val TEST_PAYLOAD = "cbx-journey-test-activation"
        const val TEST_LABEL = "Licence de test"
    }
}
