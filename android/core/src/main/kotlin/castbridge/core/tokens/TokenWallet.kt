package castbridge.core.tokens

import castbridge.core.net.JsonLite
import castbridge.core.owner.Hkdf
import castbridge.core.owner.SafeFile
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** La clé HMAC du porte-jetons (la TV la fournit via `InstallKeyStore` + `SecretWrapper` ; tests : clé en mémoire). */
fun interface WalletKeyProvider { fun key(): ByteArray? }

/** Clé fixe (tests, vecteurs). */
class FixedWalletKey(private val k: ByteArray?) : WalletKeyProvider { override fun key(): ByteArray? = k?.copyOf() }

object WalletKey {
    /** `HKDF-SHA256(extract("castbridge-wallet-v1", installPriv), "mac", 32)`. */
    fun derive(installPriv: ByteArray): ByteArray = Hkdf.expand(Hkdf.extract("castbridge-wallet-v1".toByteArray(), installPriv), "mac".toByteArray(), 32)
}

/** État du fichier ; [wire] est la valeur du battement de cœur (`ok|unreadable|absent`). */
enum class WalletState(val wire: String) { OK("ok"), EMPTY("absent"), UNREADABLE("unreadable"), NO_KEY("unreadable") }

/** Une dépense : [op] est la clé d'opération (idempotence). */
class SpendLine(val seq: Long, val at: Long, val game: String, val item: String, val cost: Long, val op: String)

sealed class SpendResult {
    /** Débité et inscrit durablement. [replayed] = la même opération avait déjà été débitée : rien de nouveau n'est pris, même résultat. */
    class Ok(val seq: Long, val balance: Long, val replayed: Boolean) : SpendResult()
    class Insufficient(val balance: Long) : SpendResult()
    class Unreadable(val message: String) : SpendResult()
    /** L'inscription a échoué : RIEN n'est débité, l'effet ne doit pas être appliqué. */
    object WriteFailed : SpendResult()
    /** La clé d'opération existe déjà avec un autre article ou un autre coût : refusée. */
    object OpConflict : SpendResult()
    /** Paramètres hors bornes (article, coût, clé d'opération). */
    object Invalid : SpendResult()
}

enum class CreditResult { OK, STALE, WRONG_LICENSE, WRONG_INSTALL, BAD_GRANT, UNREADABLE, WRITE_FAILED }

/** Rapport de dépenses envoyé au serveur (protocole de réconciliation). [mac] couvre tout le reste (HMAC d'installation). */
class TokenReport(val license: String, val install: String, val lastGrant: Long, val spentTotal: Long, val lastSpendSeq: Long, val spends: List<SpendLine>, val mac: String) {
    fun lines(): List<String> = listOf(REPORT_MAGIC, "license=$license", "install=$install", "lastGrant=$lastGrant", "spentTotal=$spentTotal", "lastSpendSeq=$lastSpendSeq") +
        spends.map { "spend=${it.seq}|${it.at}|${it.game}|${it.item}|${it.cost}|${it.op}" }

    fun render(): String = (lines() + "mac=$mac").joinToString("\n") + "\n"

    fun toJson(): String = JsonLite.write(linkedMapOf("format" to REPORT_MAGIC, "license" to license, "install" to install, "lastGrant" to lastGrant, "spentTotal" to spentTotal, "lastSpendSeq" to lastSpendSeq,
        "spends" to spends.map { linkedMapOf("seq" to it.seq, "at" to it.at, "game" to it.game, "item" to it.item, "cost" to it.cost, "op" to it.op) }, "mac" to mac))

    companion object { const val REPORT_MAGIC = "castbridge-token-report-v1" }
}

/** Ce que montrent le badge et le battement de cœur (jamais de secret). */
class WalletSummary(val state: WalletState, val lastGrant: Long, val lastSpendSeq: Long, val spentTotal: Long, val balance: Long, val ackedSeq: Long, val tailMac: String, val clockNote: String?)

/**
 * Porte-jetons de la TV : `castbridge-token-wallet-v1`, un fichier texte écrit par [SafeFile], qui ne s'efface pas par l'application. Chaque ligne est
 * `<contenu>|<prev16>|<mac16>` : `prev16` = 16 hex de SHA-256 de la ligne précédente COMPLÈTE (la première pointe sur la ligne d'en-tête), `mac16` = HMAC-SHA256 de `<contenu>|<prev16>` sous
 * la clé dérivée de la clé PRIVÉE d'installation ([WalletKey.derive]) : le fichier est lié à l'installation (une copie ailleurs, ou après réinstallation, est illisible) et toute altération,
 * suppression ou insertion au milieu rompt la chaîne.
 * ```
 * castbridge-token-wallet-v1
 * license=<id>|<prev>|<mac>
 * install=<installId 16 hex>|<prev>|<mac>
 * grant=<grant_seq>|<amount>|<fp du jeton 16 hex>|<prev>|<mac>          (une ligne par bon crédité ; seq strictement croissant)
 * acked=<seq>|<total>|<prev>|<mac>                                       (dépenses accusées par le serveur, compactées ; au plus une, avant toute dépense)
 * spend=<seq>|<at>|quiz|<item>|<cost>|<op>|<prev>|<mac>                  (seq contigu ; op = clé d'opération)
 * ```
 * Règles d'argent : débit inscrit durablement AVANT que l'appelant applique l'effet ; une opération ([op]) ne débite qu'une fois ; jamais de solde négatif ; un bon ne se crédite qu'une fois.
 * Une faute (mac, chaîne, bornes, solde négatif) ⇒ [WalletState.UNREADABLE], solde 0, rien n'est écrit. Réinstallation (clé d'installation régénérée) : l'ancien fichier devient illisible ; le serveur
 * réémet les bons non épuisés avec la nouvelle `install` (w5-08). Clé absente : lecture seule, [SpendResult.Unreadable].
 *
 * Limites assumées (conception § 6.5) : retirer les DERNIÈRES lignes ou supprimer le fichier revient à restaurer un état antérieur, indétectable localement ; le serveur le voit (`TOKEN_REPLAY`,
 * séquence qui recule) et la perte est bornée par les bons déjà livrés. Les clés d'opération des dépenses compactées ne sont plus mémorisées : rejouer une opération déjà accusée n'est pas couvert.
 * L'horloge reculée est acceptée (la séquence fait foi) et signalée par [WalletSummary.clockNote].
 */
class TokenWallet(private val file: File, private val keys: WalletKeyProvider, private val compactAbove: Int = COMPACT_ABOVE) {
    private class GrantLine(val grant: Long, val amount: Long, val fp: String)
    private class Parsed(val license: String, val install: String, val grants: List<GrantLine>, val ackedSeq: Long, val ackedTotal: Long, val spends: List<SpendLine>, val lines: List<String>, val tailMac: String, val clockNote: String?) {
        val lastGrant get() = grants.lastOrNull()?.grant ?: 0L
        val lastSpendSeq get() = spends.lastOrNull()?.seq ?: ackedSeq
        val spentTotal get() = ackedTotal + spends.sumOf { it.cost }
        val balance get() = grants.sumOf { it.amount } - spentTotal
    }

    private var state = WalletState.EMPTY
    private var parsed: Parsed? = null
    private var ackedMem = 0L
    private var loaded = false

    @Synchronized private fun ensure() {
        if (loaded && state != WalletState.NO_KEY && state != WalletState.UNREADABLE) return
        loaded = true; parsed = null
        val key = keys.key()
        val text = readText()
        if (text == null) { state = WalletState.EMPTY; return }
        if (key == null || key.size != 32) { state = WalletState.NO_KEY; return }
        val p = parse(text, key)
        if (p == null) { state = WalletState.UNREADABLE; return }
        parsed = p; state = WalletState.OK; ackedMem = maxOf(ackedMem, p.ackedSeq)
    }

    /** Le texte du fichier principal ; la copie `.bak` seulement si le principal n'existe pas. Un fichier vide ou illisible est une faute (UNREADABLE), pas un porte-jetons neuf. */
    private fun readText(): String? {
        if (file.exists()) return runCatching { file.readText().takeIf { it.isNotBlank() } }.getOrNull() ?: "\u0000unreadable"
        val bak = SafeFile.bak(file)
        if (bak.exists()) return runCatching { bak.readText() }.getOrNull() ?: "\u0000unreadable"
        return null
    }

    /** Français : message à afficher quand le porte-jetons n'est pas utilisable, sinon null. */
    @Synchronized fun message(): String? { ensure(); return when (state) { WalletState.UNREADABLE -> UNREADABLE_MESSAGE; WalletState.NO_KEY -> NO_KEY_MESSAGE; else -> null } }

    @Synchronized fun state(): WalletState { ensure(); return state }

    @Synchronized fun balance(): Long { ensure(); return parsed?.balance ?: 0L }

    /** Dernier numéro de bon crédité (0 si aucun) : à passer à [TokenGrant.verify]. */
    @Synchronized fun lastGrant(): Long { ensure(); return parsed?.lastGrant ?: 0L }

    /** Nombre de dépenses dont la clé d'opération commence par [opPrefix] (pour numéroter les achats d'une partie). */
    @Synchronized fun spendCount(opPrefix: String): Int { ensure(); return parsed?.spends?.count { it.op.startsWith(opPrefix) } ?: 0 }

    @Synchronized fun summary(): WalletSummary { ensure(); val p = parsed
        return WalletSummary(state, p?.lastGrant ?: 0, p?.lastSpendSeq ?: 0, p?.spentTotal ?: 0, p?.balance ?: 0, if (p == null) 0 else maxOf(p.ackedSeq, ackedMem), p?.tailMac ?: "", p?.clockNote) }

    /** Crédite un bon DÉJÀ vérifié par [TokenGrant.verify] ; refuse un numéro ≤ au dernier (un bon ne se crédite qu'une fois), une autre licence ou une autre installation. */
    @Synchronized fun credit(grant: TokenGrant, envelopeFp: String): CreditResult {
        ensure()
        if (state == WalletState.UNREADABLE || state == WalletState.NO_KEY) return CreditResult.UNREADABLE
        if (grant.amount !in 1..TokenGrant.AMOUNT_MAX || grant.grant < 1 || !FP.matches(envelopeFp)) return CreditResult.BAD_GRANT
        val p = parsed
        val key = keys.key() ?: return CreditResult.UNREADABLE
        if (p != null) {
            if (p.license != grant.license) return CreditResult.WRONG_LICENSE
            if (p.install != grant.installId) return CreditResult.WRONG_INSTALL
            if (grant.grant <= p.lastGrant) return CreditResult.STALE
        }
        val payloads = (if (p == null) listOf("license=${grant.license}", "install=${grant.installId}") else emptyList()) + "grant=${grant.grant}|${grant.amount}|$envelopeFp"
        return if (append(p, payloads, key)) CreditResult.OK else CreditResult.WRITE_FAILED
    }

    /**
     * Débite [cost] jetons pour [item] (clé d'opération [op], `[A-Za-z0-9._:-]{1,96}`). La même [op] ne débite qu'une fois : un rejeu rend le même `seq` ([SpendResult.Ok.replayed]) sans rien prendre.
     * L'appelant n'applique l'effet qu'après un [SpendResult.Ok]. Deux dépenses dans la même milliseconde ont des `seq` distincts.
     */
    @Synchronized fun spend(item: String, cost: Long, nowMs: Long, op: String): SpendResult {
        ensure()
        if (!ITEM.matches(item) || cost !in 1..TokenGrant.AMOUNT_MAX || !OP.matches(op) || nowMs < 0) return SpendResult.Invalid
        when (state) { WalletState.UNREADABLE -> return SpendResult.Unreadable(UNREADABLE_MESSAGE); WalletState.NO_KEY -> return SpendResult.Unreadable(NO_KEY_MESSAGE); WalletState.EMPTY -> return SpendResult.Insufficient(0); else -> {} }
        val p = parsed ?: return SpendResult.Unreadable(UNREADABLE_MESSAGE)
        p.spends.firstOrNull { it.op == op }?.let { return if (it.item == item && it.cost == cost) SpendResult.Ok(it.seq, p.balance, true) else SpendResult.OpConflict }
        if (p.balance < cost) return SpendResult.Insufficient(p.balance)
        val key = keys.key() ?: return SpendResult.Unreadable(NO_KEY_MESSAGE)
        val seq = p.lastSpendSeq + 1
        if (!append(p, listOf("spend=$seq|$nowMs|$GAME|$item|$cost|$op"), key)) return SpendResult.WriteFailed
        return SpendResult.Ok(seq, parsed?.balance ?: 0L, false)
    }

    /** Le rapport des dépenses de numéro > [sinceSeq] avec l'en-tête (licence, installation, dernier bon, total dépensé) et un HMAC d'ensemble ; null si le fichier est inutilisable. */
    @Synchronized fun report(sinceSeq: Long = 0): TokenReport? {
        ensure(); val p = parsed ?: return null; val key = keys.key() ?: return null
        val spends = p.spends.filter { it.seq > sinceSeq }
        val unsigned = TokenReport(p.license, p.install, p.lastGrant, p.spentTotal, p.lastSpendSeq, spends, "")
        return TokenReport(p.license, p.install, p.lastGrant, p.spentTotal, p.lastSpendSeq, spends, mac(key, unsigned.lines().joinToString("\n")))
    }

    /**
     * Accusé du serveur : les dépenses de numéro ≤ [seqAcked] sont connues de lui. Ne supprime rien tant que le fichier reste court ; au-delà de [COMPACT_ABOVE] lignes, les dépenses accusées
     * sont compactées en une ligne `acked=<seq>|<total>` (la chaîne est reconstruite). Faux si l'accusé dépasse la dernière dépense locale (copie restaurée : ignoré) ou si l'écriture échoue.
     */
    @Synchronized fun ack(seqAcked: Long): Boolean {
        ensure(); val p = parsed ?: return false; val key = keys.key() ?: return false
        if (seqAcked > p.lastSpendSeq || seqAcked < 0) return false
        ackedMem = maxOf(ackedMem, seqAcked)
        if (p.lines.size <= compactAbove) return true
        val gone = p.spends.filter { it.seq <= ackedMem }
        if (gone.isEmpty()) return true
        val keep = p.spends.filter { it.seq > ackedMem }
        val payloads = listOf("license=${p.license}", "install=${p.install}") + p.grants.map { "grant=${it.grant}|${it.amount}|${it.fp}" } +
            "acked=${maxOf(p.ackedSeq, gone.last().seq)}|${p.ackedTotal + gone.sumOf { it.cost }}" + keep.map { spendPayload(it) }
        val text = seal(payloads, key)
        return write(text, key)
    }

    private fun append(p: Parsed?, payloads: List<String>, key: ByteArray): Boolean {
        val all = (p?.lines ?: listOf(MAGIC)).toMutableList()
        var prev = hash16(all.last())
        for (pl in payloads) { val line = sealLine(pl, prev, key); all += line; prev = hash16(line) }
        return write(all.joinToString("\n") + "\n", key)
    }

    private fun write(text: String, key: ByteArray): Boolean {
        val next = parse(text, key) ?: return false
        return runCatching { SafeFile.write(file, text) { parse(it, key) != null } }.isSuccess.also { if (it) { parsed = next; state = WalletState.OK; loaded = true } }
    }

    companion object {
        const val MAGIC = "castbridge-token-wallet-v1"
        const val GAME = "quiz"
        const val COMPACT_ABOVE = 500
        const val UNREADABLE_MESSAGE = "Porte-jetons illisible : reconnectez le téléphone pour le resynchroniser"
        const val NO_KEY_MESSAGE = "Jetons indisponibles sur cette TV"
        private val FP = Regex("^[0-9a-f]{16}$")
        private val ITEM = Regex("^[a-z][a-z0-9-]{0,31}$")
        private val OP = Regex("^[A-Za-z0-9._:-]{1,96}$")
        private val HEX16 = Regex("^[0-9a-f]{16}$")

        fun hash16(line: String): String = MessageDigest.getInstance("SHA-256").digest(line.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
        fun mac(key: ByteArray, s: String): String = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(s.toByteArray(Charsets.UTF_8)) }.take(8).joinToString("") { "%02x".format(it) }
        private fun spendPayload(s: SpendLine) = "spend=${s.seq}|${s.at}|${s.game}|${s.item}|${s.cost}|${s.op}"
        private fun sealLine(payload: String, prev: String, key: ByteArray): String = "$payload|$prev".let { "$it|${mac(key, it)}" }
        private fun seal(payloads: List<String>, key: ByteArray): String {
            val out = mutableListOf(MAGIC); var prev = hash16(MAGIC)
            for (pl in payloads) { val l = sealLine(pl, prev, key); out += l; prev = hash16(l) }
            return out.joinToString("\n") + "\n"
        }

        private fun num(s: String): Long? = s.toLongOrNull()?.takeIf { it.toString() == s && it >= 0 }

        /** Le contenu du fichier, ou null à la moindre faute. */
        private fun parse(text: String, key: ByteArray): Parsed? = runCatching {
            val lines = text.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
            require(lines.firstOrNull() == MAGIC && lines.size >= 3)
            var prev = hash16(MAGIC); var tailMac = ""
            val payloads = ArrayList<String>()
            for (line in lines.drop(1)) {
                val i = line.lastIndexOf('|'); require(i > 0); val j = line.lastIndexOf('|', i - 1); require(j > 0)
                val m = line.substring(i + 1); val pv = line.substring(j + 1, i)
                require(HEX16.matches(m) && pv == prev)
                require(java.security.MessageDigest.isEqual(mac(key, line.substring(0, i)).toByteArray(), m.toByteArray()))
                payloads += line.substring(0, j); prev = hash16(line); tailMac = m
            }
            require(payloads[0].startsWith("license=") && payloads[1].startsWith("install="))
            val license = payloads[0].removePrefix("license=").also { require(castbridge.core.owner.Envelope.ID.matches(it)) }
            val install = payloads[1].removePrefix("install=").also { require(HEX16.matches(it)) }
            val grants = ArrayList<GrantLine>(); val spends = ArrayList<SpendLine>()
            var ackedSeq = 0L; var ackedTotal = 0L; var balance = 0L; var seenAcked = false
            var clockNote: String? = null
            for (pl in payloads.drop(2)) when {
                pl.startsWith("grant=") -> {
                    val f = pl.removePrefix("grant=").split('|'); require(f.size == 3 && FP.matches(f[2]))
                    val g = num(f[0])!!; val a = num(f[1])!!
                    require(g >= 1 && a in 1..TokenGrant.AMOUNT_MAX && g > (grants.lastOrNull()?.grant ?: 0L))
                    grants += GrantLine(g, a, f[2]); balance += a
                }
                pl.startsWith("acked=") -> {
                    val f = pl.removePrefix("acked=").split('|'); require(f.size == 2 && !seenAcked && spends.isEmpty())
                    ackedSeq = num(f[0])!!; ackedTotal = num(f[1])!!; seenAcked = true; balance -= ackedTotal; require(balance >= 0)
                }
                pl.startsWith("spend=") -> {
                    val f = pl.removePrefix("spend=").split('|'); require(f.size == 6)
                    val s = SpendLine(num(f[0])!!, num(f[1])!!, f[2], f[3], num(f[4])!!, f[5])
                    require(s.game == GAME && ITEM.matches(s.item) && OP.matches(s.op) && s.cost in 1..TokenGrant.AMOUNT_MAX)
                    require(s.seq == (spends.lastOrNull()?.seq ?: ackedSeq) + 1 && spends.none { it.op == s.op })
                    if (spends.isNotEmpty() && s.at < spends.last().at) clockNote = "Horloge reculée constatée entre deux dépenses"
                    spends += s; balance -= s.cost; require(balance >= 0)
                }
                else -> error("ligne inconnue")
            }
            Parsed(license, install, grants, ackedSeq, ackedTotal, spends, lines, tailMac, clockNote)
        }.getOrNull()
    }
}
