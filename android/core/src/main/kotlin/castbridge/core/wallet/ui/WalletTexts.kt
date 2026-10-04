package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletReason
import castbridge.core.wallet.WalletView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Textes français des écrans « Mes jetons ». Aucune logique de solde : tout vient de l'instantané signé ou du serveur ; les montants sont toujours en chiffres (milliers en espace fine) ET, pour une confirmation, en lettres. */
object WalletTexts {
    private fun t(n: Long) = WalletView.thousands(n)
    private fun cur(name: String) = if (name == "MBOKO") WalletCurrency.MBOKO else WalletCurrency.NDEM

    /** 0 -> « sans frais », 200 -> « 2 % », 150 -> « 1,5 % », 25 -> « 0,25 % ». */
    fun feeText(bp: Long): String {
        if (bp <= 0) return "sans frais"
        val whole = bp / 100; val frac = (bp % 100).toString().padStart(2, '0').trimEnd('0')
        return (if (frac.isEmpty()) "$whole" else "$whole,$frac") + " %"
    }

    fun directionLabel(dir: ConvertDir, p: PolicyView?): String = when (dir) {
        ConvertDir.N2M -> "NDEM → MBOKO" + (p?.let { " : ${t(it.rate)} NDEM = 1 MBOKO" } ?: "")
        ConvertDir.M2N -> "MBOKO → NDEM" + (p?.let { " : 1 MBOKO = ${t(it.rate)} NDEM, " + if (it.reverseFeeBp <= 0) "sans frais" else "frais ${feeText(it.reverseFeeBp)}" } ?: "")
    }

    fun editionLabel(ed: String): String = when (ed) {
        "TRIAL" -> "Édition d'essai"; "PROD" -> "Édition de production"; "UNLIMITED" -> "Édition illimitée"; "NONE" -> "Aucune édition active"; else -> "Édition : $ed"
    }

    /** Avis à montrer sous les soldes : licence en attente, en grâce, suspendue…, compte lié à une autre TV, compte gelé, puis les avis du serveur (sans doublon). */
    fun notes(edition: EditionInfo?, notices: List<Notice>, snapshot: Snapshot?): List<String> {
        val out = ArrayList<String>(); val covered = HashSet<String>()
        fun add(reason: String?, text: String) { if (reason != null) covered += reason; if (text !in out) out += text }
        covered += "LICENSE_PENDING"                                          // dit par WalletStatus (bandeau « Jetons en attente de notification de votre activation »)
        if (edition != null && edition.grace) add(null, "Licence en période de grâce : renouvelez-la")
        else when (edition?.license) { "SUSPENDED" -> add(null, "Licence suspendue"); "REVOKED" -> add(null, "Licence révoquée"); "EXPIRED" -> add(null, "Licence expirée"); else -> {} }
        if (edition != null && (edition.boundOther || edition.license == "OTHER_TV")) add("BOUND_OTHER_TV", WalletReason.BOUND_OTHER_TV.text())
        if (snapshot?.flags?.frozen == true) add("FROZEN", WalletReason.FROZEN.text())
        for (n in notices) if (n.reason !in covered && n.text.isNotBlank()) add(n.reason, n.text.take(120))
        return out
    }

    /** Un solde par monnaie ET par poche, tel que signé : disponible, puis bloqué en mise. */
    fun pocketLines(s: Snapshot): List<String> = listOf(
        "NDEM : ${t(s.n)} disponibles · ${t(s.nb)} bloqués en mise",
        "MBOKO : ${t(s.m)} disponibles · ${t(s.mb)} bloqués en mise",
    )

    fun previewLines(p: ConvertPreview): List<String> = buildList {
        add("Vous payez : ${t(p.pay)} ${p.payCur}")
        add("Vous recevez : ${t(p.gain)} ${p.gainCur}")
        if (p.fee > 0) add("Frais : ${t(p.fee)} NDEM")
        add("Avant : ${t(p.beforeN)} NDEM · ${t(p.beforeM)} MBOKO")
        if (p.enough) add("Après : ${t(p.afterN)} NDEM · ${t(p.afterM)} MBOKO")
        else add(WalletMessages.of(409, "INSUFFICIENT", available = if (p.payCur == "NDEM") p.beforeN else p.beforeM, cur = cur(p.payCur)).text)
    }

    /** Confirmation d'une conversion : montants en chiffres ET en lettres. */
    fun convertConfirm(dir: ConvertDir, q: Long, p: ConvertPreview): List<String> =
        listOf("Vous payez ${AmountWords.confirmation(p.pay, cur(p.payCur))}", "et recevez ${AmountWords.confirmation(p.gain, cur(p.gainCur))}", "OK : confirmer")

    fun sendConfirm(cur: WalletCurrency, amt: Long, code: String): List<String> = listOf("Envoyer ${AmountWords.confirmation(amt, cur)}", "au code $code", "OK : confirmer")

    fun convertDone(d: ConvertDone): String = when {
        d.replayed -> "Déjà fait : conversion de ${t(d.q)} MBOKO déjà enregistrée"
        d.dir == "M2N" -> "Conversion faite : vous avez payé ${t(d.q)} MBOKO et reçu ${t(d.ndemNet)} NDEM" + if (d.fee > 0) " (frais : ${t(d.fee)} NDEM)" else ""
        else -> "Conversion faite : vous avez payé ${t(d.ndemGross)} NDEM et reçu ${t(d.q)} MBOKO"
    }

    fun transferDone(d: TransferDone): String =
        if (d.replayed) "Déjà fait : envoi de ${t(d.amt)} ${d.cur} déjà enregistré" else "Envoyé : ${t(d.amt)} ${d.cur}" + (d.to?.let { " à $it" } ?: "")

    fun historyLine(l: HistoryLine, zone: ZoneId): String {
        val date = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(l.at).atZone(zone))
        val sign = if (l.amount > 0) "+" else if (l.amount < 0) "−" else ""
        return "$date · ${l.label} · $sign${t(Math.abs(l.amount))} ${l.currency}" + (l.counterparty?.let { " · $it" } ?: "")
    }

    /** Compte à rebours du code de réception. */
    fun expiry(expMs: Long, nowMs: Long): String {
        val rem = expMs - nowMs
        if (rem <= 0) return WalletReason.CODE_EXPIRED.text()
        val secs = (rem + 999) / 1000
        val min = secs / 60; val s = secs % 60
        return "Valable encore " + when { min > 0 && s > 0 -> "$min min $s s"; min > 0 -> "$min min"; else -> "$s s" }
    }
}
