package castbridge.core.tokens

import castbridge.core.net.JsonLite
import castbridge.core.owner.Hkdf
import castbridge.core.owner.SafeFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
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

/**
 * Rapport de dépenses envoyé au serveur (protocole de réconciliation). [mac] couvre tout le reste (HMAC sous la clé du porte-jetons) ; c'est une EMPREINTE OPAQUE : le serveur ne connaît pas
 * `kWallet` (dérivée de la clé PRIVÉE d'installation) et ne peut donc PAS la vérifier. Elle ne prouve rien au serveur ; elle sert à la TV (détecter un rapport tronqué ou altéré) et de témoin
 * d'intégrité dans les journaux. Ne jamais en faire une preuve d'authenticité côté serveur : celui-ci s'appuie sur la séquence, les montants et le canal.
 */
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
 * granted=<dernier grant_seq>|<somme>|<prev>|<mac>                      (bons repliés à la compaction ; au plus une, juste après `install`, avant tout `grant`)
 * acked=<seq>|<total>|<prev>|<mac>                                       (dépenses accusées par le serveur, compactées ; au plus une, avant toute dépense)
 * spend=<seq>|<at>|quiz|<item>|<cost>|<op>|<prev>|<mac>                  (seq contigu ; op = clé d'opération)
 * ```
 * Règles d'argent : débit inscrit durablement AVANT que l'appelant applique l'effet ; une opération ([op]) ne débite qu'une fois ; jamais de solde négatif ; un bon ne se crédite qu'une fois.
 * Une faute (mac, chaîne, bornes, solde négatif) ⇒ [WalletState.UNREADABLE], solde 0, rien n'est écrit. Réinstallation (clé d'installation régénérée) : l'ancien fichier devient illisible ; le serveur
 * réémet les bons non épuisés avec la nouvelle `install` (w5-08). Clé absente : lecture seule, [SpendResult.Unreadable].
 *
 * Limites assumées (conception § 6.5) : retirer les DERNIÈRES lignes ou supprimer le fichier revient à restaurer un état antérieur, indétectable localement ; le serveur le voit (`TOKEN_REPLAY`,
 * séquence qui recule) et la perte est bornée par les bons déjà livrés. La compaction garde les dernières dépenses accusées ([TokenWallet.KEEP_RECENT] au moins, ou celles des dernières 24 h) : seules les clés d'opération plus anciennes sont oubliées, rejouer une
 * opération aussi ancienne n'est pas couvert (une partie dure bien moins de 24 h). Plusieurs instances ou processus sur le même fichier : voir [TokenWallet.of] ; toute opération relit le fichier sous verrou.
 * L'horloge reculée est acceptée (la séquence fait foi) et signalée par [WalletSummary.clockNote].
 */
class TokenWallet(private val file: File, private val keys: WalletKeyProvider, private val compactAbove: Int = COMPACT_ABOVE, private val keepRecent: Int = KEEP_RECENT, private val keepWindowMs: Long = KEEP_WINDOW_MS) {
    private class GrantLine(val grant: Long, val amount: Long, val fp: String)
    private class Parsed(val license: String, val install: String, val baseGrant: Long, val baseSum: Long, val grants: List<GrantLine>, val ackedSeq: Long, val ackedTotal: Long, val spends: List<SpendLine>, val lines: List<String>, val tailMac: String, val clockNote: String?) {
        val lastGrant get() = grants.lastOrNull()?.grant ?: baseGrant
        val lastSpendSeq get() = spends.lastOrNull()?.seq ?: ackedSeq
        val spentTotal get() = ackedTotal + spends.sumOf { it.cost }
        val balance get() = baseSum + grants.sumOf { it.amount } - spentTotal
    }

    /** Verrou et mémoire partagés par TOUTES les instances du même fichier (chemin canonique) dans ce processus. */
    private class Shared { val lock = ReentrantLock(); @Volatile var ackedMem = 0L }

    private val shared = SHARED.computeIfAbsent(canonical(file)) { Shared() }
    private var state = WalletState.EMPTY
    private var parsed: Parsed? = null
    private var cacheText: String? = null
    private var cacheKey: ByteArray? = null

    /** Exclusion entre threads (même verrou pour toutes les instances du fichier) ET entre processus (`FileChannel.lock` sur le fichier `.lock` voisin), réentrant. */
    private fun <T> locked(body: () -> T): T {
        shared.lock.lock()
        try {
            if (shared.lock.holdCount > 1) return body()
            val ch = runCatching { file.absoluteFile.parentFile?.mkdirs(); java.io.RandomAccessFile(lockFile(file), "rw").channel }.getOrNull() ?: return body()
            val fl = runCatching { ch.lock() }.getOrNull()
            try { return body() } finally { runCatching { fl?.release() }; runCatching { ch.close() } }
        } finally { shared.lock.unlock() }
    }

    /** Relit le fichier (toujours sous verrou) : une autre instance ou un autre processus a pu y écrire ; le cache n'est réutilisé que si texte ET clé sont identiques. */
    private fun refresh() {
        val key = keys.key()
        val text = readText()
        if (text == null) { state = WalletState.EMPTY; parsed = null; cacheText = null; return }
        if (key == null || key.size != 32) { state = WalletState.NO_KEY; parsed = null; return }
        if (parsed != null && text == cacheText && cacheKey?.contentEquals(key) == true) { state = WalletState.OK; return }
        val p = parse(text, key)
        if (p == null) { state = WalletState.UNREADABLE; parsed = null; cacheText = null; return }
        parsed = p; state = WalletState.OK; cacheText = text; cacheKey = key.copyOf(); shared.ackedMem = maxOf(shared.ackedMem, p.ackedSeq)
    }

    private fun <T> tx(body: () -> T): T = locked { refresh(); body() }

    /** Le texte du fichier principal ; la copie `.bak` seulement si le principal n'existe pas. Un fichier vide ou illisible est une faute (UNREADABLE), pas un porte-jetons neuf. */
    private fun readText(): String? {
        if (file.exists()) return runCatching { file.readText().takeIf { it.isNotBlank() } }.getOrNull() ?: "\u0000unreadable"
        val bak = SafeFile.bak(file)
        if (bak.exists()) return runCatching { bak.readText() }.getOrNull() ?: "\u0000unreadable"
        return null
    }

    /** Français : message à afficher quand le porte-jetons n'est pas utilisable, sinon null. */
    fun message(): String? = tx { when (state) { WalletState.UNREADABLE -> UNREADABLE_MESSAGE; WalletState.NO_KEY -> NO_KEY_MESSAGE; else -> null } }

    fun state(): WalletState = tx { state }

    fun balance(): Long = tx { parsed?.balance ?: 0L }

    /** Dernier numéro de bon crédité (0 si aucun) : à passer à [TokenGrant.verify]. */
    fun lastGrant(): Long = tx { parsed?.lastGrant ?: 0L }

    /** Nombre de dépenses (non compactées) dont la clé d'opération commence par [opPrefix] (pour numéroter les achats d'une partie). */
    fun spendCount(opPrefix: String): Int = tx { parsed?.spends?.count { it.op.startsWith(opPrefix) } ?: 0 }

    fun summary(): WalletSummary = tx { val p = parsed
        WalletSummary(state, p?.lastGrant ?: 0, p?.lastSpendSeq ?: 0, p?.spentTotal ?: 0, p?.balance ?: 0, if (p == null) 0 else maxOf(p.ackedSeq, shared.ackedMem), p?.tailMac ?: "", p?.clockNote) }

    /** Crédite un bon DÉJÀ vérifié par [TokenGrant.verify] ; refuse un numéro ≤ au dernier (un bon ne se crédite qu'une fois), une autre licence ou une autre installation. */
    fun credit(grant: TokenGrant, envelopeFp: String): CreditResult = tx {
        if (state == WalletState.UNREADABLE || state == WalletState.NO_KEY) return@tx CreditResult.UNREADABLE
        if (grant.amount !in 1..TokenGrant.AMOUNT_MAX || grant.grant < 1 || !FP.matches(envelopeFp)) return@tx CreditResult.BAD_GRANT
        val p = parsed
        val key = keys.key() ?: return@tx CreditResult.UNREADABLE
        if (p != null) {
            if (p.license != grant.license) return@tx CreditResult.WRONG_LICENSE
            if (p.install != grant.installId) return@tx CreditResult.WRONG_INSTALL
            if (grant.grant <= p.lastGrant) return@tx CreditResult.STALE
        }
        val payloads = (if (p == null) listOf("license=${grant.license}", "install=${grant.installId}") else emptyList()) + "grant=${grant.grant}|${grant.amount}|$envelopeFp"
        if (append(p, payloads, key)) CreditResult.OK else CreditResult.WRITE_FAILED
    }

    /**
     * Débite [cost] jetons pour [item] (clé d'opération [op], `[A-Za-z0-9._:-]{1,96}`). La même [op] ne débite qu'une fois : un rejeu rend le même `seq` ([SpendResult.Ok.replayed]) sans rien prendre.
     * L'appelant n'applique l'effet qu'après un [SpendResult.Ok]. Deux dépenses dans la même milliseconde ont des `seq` distincts.
     */
    fun spend(item: String, cost: Long, nowMs: Long, op: String): SpendResult = tx {
        if (!ITEM.matches(item) || cost !in 1..TokenGrant.AMOUNT_MAX || !OP.matches(op) || nowMs < 0) return@tx SpendResult.Invalid
        when (state) { WalletState.UNREADABLE -> return@tx SpendResult.Unreadable(UNREADABLE_MESSAGE); WalletState.NO_KEY -> return@tx SpendResult.Unreadable(NO_KEY_MESSAGE); WalletState.EMPTY -> return@tx SpendResult.Insufficient(0); else -> {} }
        val p = parsed ?: return@tx SpendResult.Unreadable(UNREADABLE_MESSAGE)
        p.spends.firstOrNull { it.op == op }?.let { return@tx if (it.item == item && it.cost == cost) SpendResult.Ok(it.seq, p.balance, true) else SpendResult.OpConflict }
        if (p.balance < cost) return@tx SpendResult.Insufficient(p.balance)
        val key = keys.key() ?: return@tx SpendResult.Unreadable(NO_KEY_MESSAGE)
        val seq = p.lastSpendSeq + 1
        if (!append(p, listOf("spend=$seq|$nowMs|$GAME|$item|$cost|$op"), key)) SpendResult.WriteFailed else SpendResult.Ok(seq, parsed?.balance ?: 0L, false)
    }

    /** Le rapport des dépenses de numéro > [sinceSeq] avec l'en-tête (licence, installation, dernier bon, total dépensé) et une empreinte opaque (voir [TokenReport]) ; null si le fichier est inutilisable. */
    fun report(sinceSeq: Long = 0): TokenReport? = tx {
        val p = parsed ?: return@tx null; val key = keys.key() ?: return@tx null
        val spends = p.spends.filter { it.seq > sinceSeq }
        val unsigned = TokenReport(p.license, p.install, p.lastGrant, p.spentTotal, p.lastSpendSeq, spends, "")
        TokenReport(p.license, p.install, p.lastGrant, p.spentTotal, p.lastSpendSeq, spends, mac(key, unsigned.lines().joinToString("\n")))
    }

    /**
     * Accusé du serveur : les dépenses de numéro ≤ [seqAcked] sont connues de lui. Ne supprime rien tant que le fichier reste court ; au-delà de [compactAbove] lignes, les dépenses accusées
     * ET anciennes (hors des [keepRecent] dernières et de la fenêtre [keepWindowMs]) sont compactées en une ligne `acked=<seq>|<total>`, et les bons sont repliés en `granted=<dernier>|<somme>`
     * (la chaîne est reconstruite). Faux si l'accusé dépasse la dernière dépense locale (copie restaurée : ignoré) ou si l'écriture échoue.
     */
    fun ack(seqAcked: Long): Boolean = tx {
        val p = parsed ?: return@tx false; val key = keys.key() ?: return@tx false
        if (seqAcked > p.lastSpendSeq || seqAcked < 0) return@tx false
        shared.ackedMem = maxOf(shared.ackedMem, seqAcked)
        if (p.lines.size <= compactAbove) return@tx true
        val sp = p.spends
        val firstUnacked = sp.indexOfFirst { it.seq > shared.ackedMem }.let { if (it < 0) sp.size else it }
        val lastAt = sp.lastOrNull()?.at ?: 0L
        val byTime = sp.indexOfFirst { it.at >= lastAt - keepWindowMs }.let { if (it < 0) sp.size else it }
        val cut = minOf(firstUnacked, sp.size - keepRecent.coerceAtLeast(0), byTime).coerceAtLeast(0)
        val gone = sp.take(cut)
        if (gone.isEmpty() && p.grants.size <= 1) return@tx true
        val keep = sp.drop(cut)
        val ackedSeq = if (gone.isEmpty()) p.ackedSeq else gone.last().seq
        val ackedTotal = p.ackedTotal + gone.sumOf { it.cost }
        val payloads = listOf("license=${p.license}", "install=${p.install}") + (if (p.lastGrant > 0) listOf("granted=${p.lastGrant}|${p.baseSum + p.grants.sumOf { it.amount }}") else emptyList()) +
            (if (ackedSeq > 0 || ackedTotal > 0) listOf("acked=$ackedSeq|$ackedTotal") else emptyList()) + keep.map { spendPayload(it) }
        write(seal(payloads, key), key)
    }

    private fun append(p: Parsed?, payloads: List<String>, key: ByteArray): Boolean {
        val all = (p?.lines ?: listOf(MAGIC)).toMutableList()
        var prev = hash16(all.last())
        for (pl in payloads) { val line = sealLine(pl, prev, key); all += line; prev = hash16(line) }
        return write(all.joinToString("\n") + "\n", key)
    }

    private fun write(text: String, key: ByteArray): Boolean {
        val next = parse(text, key) ?: return false
        return runCatching { SafeFile.write(file, text) { parse(it, key) != null } }.isSuccess.also { if (it) { parsed = next; state = WalletState.OK; cacheText = text; cacheKey = key.copyOf() } }
    }

    companion object {
        const val MAGIC = "castbridge-token-wallet-v1"
        const val GAME = "quiz"
        const val COMPACT_ABOVE = 500
        /** Dépenses les plus récentes jamais compactées (déduplication des rejeux, plafond par partie). */
        const val KEEP_RECENT = 64
        const val KEEP_WINDOW_MS = 24L * 3600 * 1000
        const val UNREADABLE_MESSAGE = "Porte-jetons illisible : reconnectez le téléphone pour le resynchroniser"
        const val NO_KEY_MESSAGE = "Jetons indisponibles sur cette TV"
        private const val MAX_SUM = 1_000_000_000_000L
        private val FP = Regex("^[0-9a-f]{16}$")
        private val ITEM = Regex("^[a-z][a-z0-9-]{0,31}$")
        private val OP = Regex("^[A-Za-z0-9._:-]{1,96}$")
        private val HEX16 = Regex("^[0-9a-f]{16}$")
        private val SHARED = ConcurrentHashMap<String, Shared>()
        private val INSTANCES = ConcurrentHashMap<String, TokenWallet>()

        private fun canonical(f: File): String = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
        private fun lockFile(f: File) = File(f.absoluteFile.parentFile, f.name.substringBeforeLast('.') + ".lock")

        /**
         * Le porte-jetons du fichier [file] : UNE instance par chemin canonique dans le processus (le premier [keys] fait foi). Utiliser ce point d'entrée dans l'application. Même avec le
         * constructeur (tests), plusieurs instances sur un même fichier partagent le verrou de thread, relisent le fichier sous verrou avant toute opération et se protègent des autres processus par
         * `FileChannel.lock` sur `wallet.lock` : aucune dépense n'est perdue.
         */
        fun of(file: File, keys: WalletKeyProvider, compactAbove: Int = COMPACT_ABOVE): TokenWallet = INSTANCES.computeIfAbsent(canonical(file)) { TokenWallet(file, keys, compactAbove) }

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
            var baseGrant = 0L; var baseSum = 0L; var seenGranted = false
            var clockNote: String? = null
            for (pl in payloads.drop(2)) when {
                pl.startsWith("granted=") -> {
                    val f = pl.removePrefix("granted=").split('|'); require(f.size == 2 && !seenGranted && grants.isEmpty() && !seenAcked && spends.isEmpty())
                    baseGrant = num(f[0])!!; baseSum = num(f[1])!!; require(baseGrant >= 1 && baseSum >= 1 && baseSum <= MAX_SUM); seenGranted = true; balance += baseSum
                }
                pl.startsWith("grant=") -> {
                    val f = pl.removePrefix("grant=").split('|'); require(f.size == 3 && FP.matches(f[2]))
                    val g = num(f[0])!!; val a = num(f[1])!!
                    require(g >= 1 && a in 1..TokenGrant.AMOUNT_MAX && g > (grants.lastOrNull()?.grant ?: baseGrant))
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
            Parsed(license, install, baseGrant, baseSum, grants, ackedSeq, ackedTotal, spends, lines, tailMac, clockNote)
        }.getOrNull()
    }
}
