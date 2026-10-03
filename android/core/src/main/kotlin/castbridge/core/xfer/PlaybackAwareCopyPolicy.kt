package castbridge.core.xfer

/**
 * R-15 (docs/REGRESSIONS.md) : « la vidéo lague pendant la copie vers la TV, alors qu'elle est déjà locale ». Les faits que la table de
 * [PlaybackPriority] ne connaissait pas : le disque lu par le lecteur est-il celui où la copie écrit ? Une clé USB et sa puce Wi-Fi partagent
 * UN bus ; l'eMMC interne n'est pas sur ce bus.
 *
 *  - même disque : débit de copie borné à 40 % du débit d'écriture mesuré (le reste est au lecteur), jamais sous [FLOOR_BPS] (la copie finit),
 *    au plus [SAME_VOLUME_MAX_BPS] : l'écriture mesurée « dans write() » surestime un disque lent (cache de pages), un plafond absolu s'impose ;
 *    la relecture finale est ralentie à [SAME_VOLUME_VERIFY_BPS], jamais supprimée ;
 *  - autre disque : aucun bridage du disque ni de la relecture (rien à partager), mais priorité de fond et connexions réduites restent ;
 *  - volume inconnu : valeurs de R-06 (prudence).
 * Pur : aucune horloge, aucun Android.
 */
object PlaybackAwareCopyPolicy {
    /** Plancher garanti pour qu'une copie finisse même sous lecture. */
    const val FLOOR_BPS = 512_000L
    const val SAME_VOLUME_FRACTION_PCT = 40L
    const val SAME_VOLUME_MAX_BPS = 3_000_000L
    /** Tant que le débit d'écriture n'est pas mesuré. */
    const val SAME_VOLUME_UNKNOWN_BPS = 1_500_000L
    const val SAME_VOLUME_VERIFY_BPS = 8_000_000L
    /** Plus longue attente imposée sans un octet : sous la surveillance du téléphone ([HttpConn.DEFAULT_STALL_MS] = 20 s) et sous 30 s. */
    const val MAX_HOLD_MS = 10_000L
    const val SLOWED_TEXT = "Copie ralentie pour ne pas gêner la lecture"

    /** true = même disque, false = disques différents, null = on ne sait pas (un des deux identifiants manque). */
    fun sameVolume(playingVolumeId: String?, targetVolumeId: String?): Boolean? =
        if (playingVolumeId == null || targetVolumeId == null) null else playingVolumeId == targetVolumeId

    /** Plafond de réception (octets/s, 0 = aucun) de la copie qui ne nourrit pas la vidéo. */
    fun receiveCap(writeBps: Long, same: Boolean?): Long = when (same) {
        null -> PlaybackPriority.receiveCap(writeBps)
        false -> 0
        true -> if (writeBps <= 0) SAME_VOLUME_UNKNOWN_BPS else (writeBps * SAME_VOLUME_FRACTION_PCT / 100).coerceIn(FLOOR_BPS, SAME_VOLUME_MAX_BPS)
    }

    /** Rythme de la relecture finale (0 = libre). Jamais supprimée : seulement ralentie. */
    fun verifyCap(same: Boolean?): Long = when (same) {
        null -> PlaybackPriority.VERIFY_CAP_BPS
        false -> 0
        true -> SAME_VOLUME_VERIFY_BPS
    }

    /** Le fsync de la copie gêne le lecteur seulement s'il est sur le même disque. */
    fun deferFsync(same: Boolean?): Boolean = same != false

    /**
     * Pire intervalle entre deux progrès vus par le téléphone : un morceau de [pieceBytes] au plancher partagé entre [streams] connexions
     * (le téléphone ne voit un progrès qu'après chaque morceau). À ajouter à [MAX_HOLD_MS] pour la pire pause : doit rester sous le chien de garde.
     */
    fun worstProgressGapMs(pieceBytes: Long = 1L shl 20, streams: Int = PlaybackPriority.PLAYING_MAX_STREAMS): Long =
        pieceBytes * 1000 * streams.coerceAtLeast(1) / FLOOR_BPS
}
