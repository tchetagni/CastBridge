package castbridge.core.link

import castbridge.core.trust.Redact
import java.util.Locale

/**
 * Le relevé de terrain du Wi-Fi Direct (demande du propriétaire du 2026-10-03, docs/agent-reports/wd-manual-button.md) : après la jonction, la latence de
 * `GET /api/hello` à l'adresse du groupe (×20), un petit transfert réel pour estimer le débit, la lecture de la clé USB si un fichier de la clé est lisible,
 * et l'état du Wi-Fi de la TV. Pur : les mesures passent par [Probe] (faux en test) ; le texte ne reçoit JAMAIS le mot de passe du groupe ni un code
 * (aucun paramètre n'en porte) et ne garde du nom du réseau que « DIRECT-xx ». Ce qui ne peut pas être su s'écrit « inconnu », jamais une valeur devinée.
 *
 * Aucune route de test n'existe sur la TV (pas de puits d'envoi sans trace : `/upload/` écrirait un vrai fichier dans la bibliothèque) : le débit est donc
 * ESTIMÉ par une lecture de 2 Mo (`/stream/`, plage d'un fichier connu) dans le sens TV vers téléphone, et le relevé le dit.
 */
object WdDiagnostic {
    const val UNKNOWN = "inconnu"
    const val PINGS = 20
    /** Au moins la moitié des sondes doivent répondre pour qu'une médiane soit dite. */
    const val MIN_ANSWERS = PINGS / 2
    const val SAMPLE_BYTES = 2L shl 20

    /** [bytes] lus en [ms] millisecondes (depuis le premier octet). */
    data class Sample(val bytes: Long, val ms: Long)

    /** Ce que le téléphone peut mesurer sur le groupe ; chaque méthode renvoie null quand la mesure est impossible (TV d'essai, profil enfant, aucun fichier, erreur). */
    interface Probe {
        /** Un `GET /api/hello` : vrai s'il répond « castbridge-tv ». */
        fun hello(): Boolean
        /** Lecture d'environ [SAMPLE_BYTES] d'un fichier de la mémoire interne de la TV (ou d'une vignette si c'est tout ce qui existe). */
        fun networkSample(): Sample?
        /** Lecture d'environ [SAMPLE_BYTES] d'un fichier qui se trouve sur la clé USB de la TV. */
        fun usbSample(): Sample?
        /** `link` de `GET /api/net` de la TV : wifi, ethernet, other, none ; null = pas de réponse. */
        fun tvNetLink(): String?
    }

    data class Measures(val latenciesMs: List<Long>, val network: Sample?, val usb: Sample?, val tvLinkAfter: String?)

    fun measure(p: Probe, nowMs: () -> Long, pings: Int = PINGS): Measures {
        val lat = ArrayList<Long>()
        repeat(pings) { val t0 = nowMs(); if (p.hello()) lat += (nowMs() - t0).coerceAtLeast(0) }
        return Measures(lat, p.networkSample(), p.usbSample(), p.tvNetLink())
    }

    /** Tout ce que le relevé dit ; chaque champ null = inconnu. [ssid] : le nom complet du réseau (réduit à « DIRECT-xx » à l'écriture). */
    data class Report(
        val ssid: String?, val ownerIp: String?, val joinedMs: Long?,
        val latenciesMs: List<Long>, val network: Sample?, val usb: Sample?,
        /** La TV avait-elle un réseau commun avant le groupe (adresses de son HELLO) ? null = inconnu. */
        val tvHadLanBefore: Boolean?, val tvLinkAfter: String?,
    )

    fun report(ssid: String?, ownerIp: String?, joinedMs: Long?, hadLanBefore: Boolean?, m: Measures) =
        Report(ssid, ownerIp, joinedMs, m.latenciesMs, m.network, m.usb, hadLanBefore, m.tvLinkAfter)

    /** « DIRECT-xx » (9 caractères), jamais le reste du nom ; null si ce n'est pas un nom de groupe. */
    fun groupName(ssid: String?): String? = ssid?.takeIf { it.startsWith("DIRECT-") && it.length >= 9 }?.take(9)

    fun median(l: List<Long>): Long? {
        if (l.size < MIN_ANSWERS) return null
        val s = l.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Mo/s (1 Mo = 2^20 octets) ; null si la durée ou la taille ne permet pas de conclure (moins de 256 ko, ou durée nulle). */
    fun mbPerSec(s: Sample?): Double? =
        if (s == null || s.bytes < 256 * 1024 || s.ms <= 0) null else s.bytes / 1048576.0 / (s.ms / 1000.0)

    /** La TV a-t-elle gardé son réseau ? null = inconnu (elle n'en avait pas avant, ou elle n'a pas répondu). */
    fun tvWifiKept(hadLanBefore: Boolean?, linkAfter: String?): Boolean? {
        if (hadLanBefore != true) return null
        return when (linkAfter) { "wifi", "ethernet" -> true; "none" -> false; else -> null }
    }

    /** Verdict du propriétaire : bon (≥ 5 Mo/s), acceptable pour la copie (1 à 5), niveau Bluetooth (< 1). */
    fun grade(mbps: Double?): String? = when {
        mbps == null -> null
        mbps >= 5.0 -> "bon"
        mbps >= 1.0 -> "acceptable pour la copie"
        else -> "niveau Bluetooth"
    }

    private fun fr1(d: Double) = String.format(Locale.ROOT, "%.1f", d).replace('.', ',')
    private fun yn(b: Boolean?) = when (b) { true -> "oui"; false -> "non"; null -> UNKNOWN }

    /** Le relevé, en français. Le même texte sert à l'écran, au journal et au presse-papiers (« Copier le rapport » : aucun secret n'y entre dans aucun cas). */
    fun format(r: Report): String {
        val group = groupName(r.ssid)?.let { "$it (créé par la TV)" } ?: UNKNOWN
        val ip = r.ownerIp ?: UNKNOWN
        val join = r.joinedMs?.takeIf { it >= 0 }?.let { "Joint en ${fr1(it / 1000.0)} s" } ?: "Joint en : $UNKNOWN"
        val med = median(r.latenciesMs)
        val lat = med?.let { "Latence médiane $it ms (${r.latenciesMs.size}/$PINGS réponses)" } ?: "Latence médiane : $UNKNOWN (${r.latenciesMs.size}/$PINGS réponses)"
        val net = mbPerSec(r.network)
        val rate = net?.let { "Débit estimé ${fr1(it)} Mo/s (TV→téléphone, lecture de ${fr1(r.network!!.bytes / 1048576.0)} Mo : la TV n'a pas de route d'envoi de test)" }
            ?: "Débit estimé : $UNKNOWN"
        val usb = mbPerSec(r.usb)?.let { "Clé USB : lecture ${fr1(it)} Mo/s" } ?: "Clé USB : $UNKNOWN"
        val line = listOf("Groupe : $group", "Adresse : $ip", join, lat, rate, "Wi-Fi de la TV conservé : ${yn(tvWifiKept(r.tvHadLanBefore, r.tvLinkAfter))}", usb).joinToString(" · ")
        val verdict = grade(net)?.let { "\nVerdict : $it." } ?: ""
        return Redact.scrub(line + verdict)
    }
}
