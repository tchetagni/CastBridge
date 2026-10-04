package castbridge.core.wallet

import castbridge.core.tokens.FrenchNumbers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Textes de la carte portefeuille (conception W22 § 7.1) : « 3 450 NDEM · 12 MBOKO », « au 04/10 18:42 », « +500 en attente ». Les milliers sont séparés par une espace fine insécable
 * (U+202F). Lecture seule : rien ici ne calcule ni ne modifie un solde.
 */
object WalletView {
    /** Espace fine insécable. */
    const val NNBSP = ' '

    /** 3450 → « 3 450 » (U+202F entre les groupes de trois chiffres). */
    fun thousands(n: Long): String {
        val s = Math.abs(n).toString()
        val g = s.reversed().chunked(3).joinToString(NNBSP.toString()).reversed()
        return if (n < 0) "-$g" else g
    }

    /** « 3 450 NDEM · 12 MBOKO » ; « Soldes inconnus » sans instantané. */
    fun balanceLine(s: Snapshot?): String = if (s == null) "Soldes inconnus" else "${thousands(s.n)} NDEM · ${thousands(s.m)} MBOKO"

    /** « au 04/10 18:42 » (heure de [zone], choisie par l'appelant). */
    fun asOf(atMs: Long, zone: ZoneId): String = "au " + DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(atMs).atZone(zone))

    /**
     * « +500 en attente » (NDEM seul, monnaie par défaut non nommée), « +5 MBOKO en attente », « +500 NDEM · +5 MBOKO en attente » ; null s'il n'y a rien. Les bons en attente sont
     * TOUJOURS montrés à part : jamais ajoutés au solde.
     */
    fun pendingLine(pendingNdem: Long, pendingMboko: Long): String? = when {
        pendingNdem > 0 && pendingMboko > 0 -> "+${thousands(pendingNdem)} NDEM · +${thousands(pendingMboko)} MBOKO en attente"
        pendingNdem > 0 -> "+${thousands(pendingNdem)} en attente"
        pendingMboko > 0 -> "+${thousands(pendingMboko)} MBOKO en attente"
        else -> null
    }

    /** Les deux lignes de la carte : soldes, puis « au 04/10 18:42 · +500 en attente ». Sans instantané, la seconde ligne ne parle que des bons en attente. */
    fun cardLines(s: Snapshot?, pendingNdem: Long, pendingMboko: Long, zone: ZoneId): List<String> {
        val second = listOfNotNull(s?.let { asOf(it.at, zone) }, pendingLine(pendingNdem, pendingMboko)).joinToString(" · ")
        return listOfNotNull(balanceLine(s), second.ifEmpty { null })
    }

    /** « Hors ligne : soldes au 04/10 18:42 ». */
    fun offlineLine(s: Snapshot, zone: ZoneId): String = "Hors ligne : soldes " + asOf(s.at, zone)

    /** Montant en lettres jusqu'à 10 000 (réutilise [FrenchNumbers]) ; au-delà : chiffres seuls. */
    fun words(n: Long): String? = if (n in 0..10_000) FrenchNumbers.words(n) else null

    /** Texte de confirmation : « 3 450 NDEM (trois mille quatre cent cinquante) » ; au-delà de 10 000 : « 12 000 NDEM » (chiffres seuls). */
    fun confirmation(n: Long, cur: WalletCurrency): String = "${thousands(n)} ${cur.name}" + (words(n)?.let { " ($it)" } ?: "")
}
