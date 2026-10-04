package castbridge.core.wallet.ui

/**
 * Quand synchroniser le portefeuille (cahier w22-07a) : à l'ouverture de l'écran, après chaque opération, et toutes les [periodMs] (15 min) tant que la TV est en ligne. Jamais deux à la fois,
 * jamais hors ligne. Une horloge qui recule ne bloque pas la synchronisation suivante.
 */
class WalletSyncSchedule(val periodMs: Long = 15 * 60_000L) {
    enum class Trigger { OPEN, AFTER_OPERATION, TICK }

    fun shouldSync(trigger: Trigger, online: Boolean, nowMs: Long, lastSyncAt: Long?, inFlight: Boolean): Boolean {
        if (!online || inFlight) return false
        val clockWentBack = lastSyncAt != null && nowMs < lastSyncAt
        return when (trigger) {
            Trigger.AFTER_OPERATION -> true
            Trigger.OPEN -> lastSyncAt == null || clockWentBack || nowMs - lastSyncAt >= OPEN_DEBOUNCE_MS     // pas de rafale si l'écran est rouvert à la seconde
            Trigger.TICK -> lastSyncAt == null || clockWentBack || nowMs - lastSyncAt >= periodMs
        }
    }

    companion object { const val OPEN_DEBOUNCE_MS = 2_000L }
}
