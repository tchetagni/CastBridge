package castbridge.core.tv

/**
 * Chien de garde de CastBridge-TV (docs/TV-SERVICE-DEMARRAGE.md) : règles PURES. La partie Android (JobScheduler, AlarmManager)
 * ne fait que poser des réveils ; c'est ici qu'on décide si le service doit être relancé.
 */
object KeepAlivePolicy {
    enum class Decision { RESTART, ALIVE, AUTOSTART_OFF, STOPPED_BY_OWNER }

    /** Période du chien de garde (minimum du JobScheduler périodique). */
    const val PERIOD_MS = 15 * 60_000L
    /** Relance différée après un balayage de l'app (onTaskRemoved). */
    const val TASK_REMOVED_DELAY_MS = 3_000L
    /** Wake lock court pris pendant le démarrage au boot. */
    const val BOOT_WAKE_MS = 30_000L

    /** Faut-il relancer le service ? Jamais contre la volonté du propriétaire, jamais s'il vit déjà. */
    fun decide(alive: Boolean, autostart: Boolean, stoppedByOwner: Boolean): Decision = when {
        stoppedByOwner -> Decision.STOPPED_BY_OWNER
        !autostart -> Decision.AUTOSTART_OFF
        alive -> Decision.ALIVE
        else -> Decision.RESTART
    }

    fun shouldRestart(alive: Boolean, autostart: Boolean, stoppedByOwner: Boolean) =
        decide(alive, autostart, stoppedByOwner) == Decision.RESTART
}

/** Exemption d'optimisation de batterie : proposée UNE fois, seulement si utile et possible. */
object BatteryExemptionPolicy {
    fun shouldOffer(ignoringOptimizations: Boolean, screenExists: Boolean, alreadyAsked: Boolean, sdk: Int): Boolean =
        sdk >= 23 && !ignoringOptimizations && screenExists && !alreadyAsked

    const val LINE = "Autoriser CastBridge-TV à rester actif en arrière-plan"
}

/** Ligne « Service » de l'écran INFO. */
object ServiceInfoText {
    fun duration(ms: Long): String {
        val min = (ms.coerceAtLeast(0) / 60_000L)
        return when {
            min < 1 -> "moins d'une minute"
            min < 60 -> "$min min"
            else -> "${min / 60} h ${(min % 60).toString().padStart(2, '0')}"
        }
    }

    /** « Service : vivant depuis 1 h 12 · démarré au boot » ; [aliveSinceMs] null = service non démarré. */
    fun line(aliveSinceMs: Long?, nowMs: Long, startReason: String?): String {
        if (aliveSinceMs == null) return "Service : arrêté"
        val base = "Service : vivant depuis ${duration(nowMs - aliveSinceMs)}"
        return when (startReason) {
            "BOOT" -> "$base · démarré au boot"
            "REPLACED" -> "$base · relancé après une mise à jour"
            "WATCHDOG" -> "$base · relancé par le chien de garde"
            "TASK_REMOVED" -> "$base · relancé après fermeture de l'app"
            else -> base
        }
    }

    /** Raison de démarrage déduite de l'action de diffusion (null = ouverture normale). */
    fun reasonOf(action: String?): String? = when (action) {
        BootPolicy.BOOT, BootPolicy.QUICKBOOT, BootPolicy.HTC_QUICKBOOT -> "BOOT"
        BootPolicy.REPLACED -> "REPLACED"
        else -> null
    }
}
