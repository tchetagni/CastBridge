package castbridge.core.sales

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Signer
import castbridge.core.update.Ed25519
import java.security.MessageDigest
import java.util.Base64

/**
 * The sales ledger of a field agent (docs/coordination/DESIGN-W4-VENTE-TERRAIN.md § 4): an append-only list of entries, each CHAINED to the previous one (`prev` = hash of the previous
 * entry) and SIGNED by the agent key. Nothing is ever deleted or rewritten: a correction is a new entry (`REFUND`, `NOTE`); a hole in `seq` or a `prev` that does not match is an anomaly the
 * server reports. The ledger holds a fingerprint of each activation (`fp`), never the token. Pure: the file store lives in the app.
 */
object SalesLedger {
    const val FORMAT = "castbridge-sale-v1"
    /** `prev` of the first entry. */
    const val GENESIS = "0"
    private val HEX16 = Regex("^[0-9a-f]{16}$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val SAFE = Regex("^[^\\r\\n]+$")
    private val ITEM = Regex("^[a-z0-9][a-z0-9-]*(\\|[A-Za-z0-9._-]+){1,2}$")

    enum class Kind { SALE, REFUND, REMIT, NOTE }

    /**
     * One signed line of the ledger. `SALE`: [device], [license], [seat], [item] (`cle-production|<jours>`, `cle-essai|<jours>`, `bon|<article>|<serial>`, `commande|<ref>|<montant>`),
     * [price] (the grid), [cash] (collected; equal to the price except for a commercial gesture, which deserves a `NOTE`), [grid] (its `generatedAt`), [receipt], [fp]. `REFUND`: [ref] (the
     * `seq` of the sale) and [cash] refunded. `REMIT`: [cash] handed over to the owner (counted only once the owner confirmed it on the server). `NOTE`: [note].
     */
    data class Entry(
        val seq: Long, val at: Long, val kind: Kind, val agent: String, val device: String? = null, val license: String? = null, val seat: String? = null, val item: String? = null,
        val price: Long? = null, val cash: Long? = null, val grid: String? = null, val receipt: String? = null, val fp: String? = null, val ref: Long? = null, val note: String? = null,
        val prev: String = GENESIS, val hash: String = "", val sig: String = "",
    ) {
        init {
            require(seq >= 1 && at > 0 && HEX16.matches(agent)) { "entrée de journal invalide" }
            require(prev == GENESIS || HEX16.matches(prev)) { "prev invalide" }
            listOfNotNull(device, license, seat, grid, receipt, note).forEach { require(SAFE.matches(it)) { "valeur invalide" } }
            item?.let { require(ITEM.matches(it)) { "article invalide" } }
            fp?.let { require(HEX64.matches(it)) { "empreinte invalide" } }
            when (kind) {
                Kind.SALE -> require(listOf(device, license, seat, item, price, cash, grid, receipt, fp).none { it == null } && price!! >= 0 && cash!! >= 0) { "vente incomplète" }
                Kind.REFUND -> require(ref != null && ref >= 1 && cash != null && cash >= 0) { "remboursement incomplet" }
                Kind.REMIT -> require(cash != null && cash > 0) { "versement incomplet" }
                Kind.NOTE -> require(!note.isNullOrBlank()) { "note vide" }
            }
        }

        /** The lines hashed (up to and including `prev`): fixed order, absent fields omitted. */
        fun canonical(): String = buildList {
            add(FORMAT); add("seq=$seq"); add("at=$at"); add("kind=${kind.name}"); add("agent=$agent")
            device?.let { add("device=$it") }; license?.let { add("license=$it") }; seat?.let { add("seat=$it") }; item?.let { add("item=$it") }
            price?.let { add("price=$it") }; cash?.let { add("cash=$it") }; grid?.let { add("grid=$it") }; receipt?.let { add("receipt=$it") }; fp?.let { add("fp=$it") }
            ref?.let { add("ref=$it") }; note?.let { add("note=$it") }
            add("prev=$prev")
        }.joinToString("\n")

        /** 16 hex characters: SHA-256 of [canonical]. */
        fun computeHash(): String = MessageDigest.getInstance("SHA-256").digest(canonical().toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

        private fun signedText() = canonical() + "\nhash=" + computeHash()

        /** This entry with its `hash` and the `sig` of [signer] (the agent key) over [canonical] and the hash. */
        fun sign(signer: Signer): Entry {
            require(signer.keyId == agent) { "l'entrée est celle d'un autre point focal" }
            return copy(hash = computeHash(), sig = Base64.getEncoder().encodeToString(signer.sign(signedText().toByteArray(Charsets.UTF_8))))
        }

        /** True when `hash` matches the content and `sig` is a valid signature of the agent key [publicKeyBase64]. */
        fun verify(publicKeyBase64: String): Boolean = hash == computeHash() && try {
            Ed25519.verify(Base64.getDecoder().decode(publicKeyBase64.trim()), signedText().toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(sig))
        } catch (e: IllegalArgumentException) { false }

        /** The full text of the entry: [canonical], `hash=`, `sig=`. */
        fun text(): String = canonical() + "\nhash=$hash\nsig=$sig"

        /** One line of `ledger.jsonl`. */
        fun toLine(): String = JsonLite.write(linkedMapOf("entry" to text()))

        companion object {
            /** The entry in [text] (as [text] writes it), or null when it is not canonical. */
            fun parse(text: String): Entry? = runCatching {
                val lines = text.split('\n')
                require(lines.first() == FORMAT && lines[lines.size - 2].startsWith("hash=") && lines.last().startsWith("sig="))
                val f = lines.drop(1).dropLast(2).map { it.substringBefore('=') to it.substringAfter('=') }.also { l -> require(l.map { it.first }.distinct().size == l.size) }.toMap()
                Entry(f.getValue("seq").toLong(), f.getValue("at").toLong(), Kind.valueOf(f.getValue("kind")), f.getValue("agent"), f["device"], f["license"], f["seat"], f["item"],
                    f["price"]?.toLong(), f["cash"]?.toLong(), f["grid"], f["receipt"], f["fp"], f["ref"]?.toLong(), f["note"], f.getValue("prev"),
                    lines[lines.size - 2].removePrefix("hash="), lines.last().removePrefix("sig="))
                    .also { require(it.text() == text) }
            }.getOrNull()

            fun fromLine(line: String): Entry? = runCatching { parse(JsonLite.obj(line).str("entry")!!) }.getOrNull()
        }
    }

    sealed class ChainResult {
        /** [last] is the hash to put in `prev` of the next entry ([GENESIS] for an empty ledger). */
        data class Ok(val count: Int, val last: String) : ChainResult()
        /** Entries are missing: the entry expected at [seq] is not there. */
        data class Gap(val seq: Long) : ChainResult()
        /** The chain diverges at [seq]: wrong `prev`, or content that does not match its `hash`. */
        data class Diverged(val seq: Long) : ChainResult()
        data class BadSignature(val seq: Long) : ChainResult()
    }

    /** Checks [entries] (in order, from the first one): contiguous `seq`, `prev` chain, `hash`, then the signature against [publicKeyBase64]. The first fault found is reported. */
    fun chain(entries: List<Entry>, publicKeyBase64: String): ChainResult {
        var prev = GENESIS
        for ((i, e) in entries.withIndex()) {
            val expected = i + 1L
            if (e.seq > expected) return ChainResult.Gap(expected)
            if (e.seq < expected || e.prev != prev || e.hash != e.computeHash()) return ChainResult.Diverged(e.seq)
            if (!e.verify(publicKeyBase64)) return ChainResult.BadSignature(e.seq)
            prev = e.hash
        }
        return ChainResult.Ok(entries.size, prev)
    }

    /** Where the next entry goes: the `seq` and `prev` to use after [entries]. */
    fun next(entries: List<Entry>): Pair<Long, String> = entries.lastOrNull()?.let { it.seq + 1 to it.hash } ?: (1L to GENESIS)

    /** What the agent owes the owner: cash of the sales minus cash refunded minus the remittances the owner CONFIRMED ([remittancesConfirmed]; an unconfirmed `REMIT` counts for nothing). */
    fun balance(entries: List<Entry>, remittancesConfirmed: Long): Long =
        entries.filter { it.kind == Kind.SALE }.sumOf { it.cash ?: 0L } - entries.filter { it.kind == Kind.REFUND }.sumOf { it.cash ?: 0L } - remittancesConfirmed
}

/** The ledger storage: append and read, nothing else (no way to remove or change an entry). */
interface LedgerStore {
    /** Adds [entry]; refused ([IllegalArgumentException]) when its `seq` is not the last + 1, its `prev` is not the last hash, or its `hash` does not match its content. */
    fun append(entry: SalesLedger.Entry)
    fun all(): List<SalesLedger.Entry>

    companion object {
        /** The rule every store applies before writing [entry] after [existing]. */
        fun checkAppend(existing: List<SalesLedger.Entry>, entry: SalesLedger.Entry) {
            val (seq, prev) = SalesLedger.next(existing)
            require(entry.seq == seq) { "numéro d'entrée attendu : $seq" }
            require(entry.prev == prev) { "l'entrée ne suit pas la précédente" }
            require(entry.hash == entry.computeHash() && entry.sig.isNotEmpty()) { "entrée non signée ou altérée" }
            require(existing.isEmpty() || existing.last().agent == entry.agent) { "entrée d'un autre point focal" }
        }
    }
}

/** In-memory [LedgerStore] (tests, and the base of the app's file store). */
class MemoryLedgerStore : LedgerStore {
    private val entries = ArrayList<SalesLedger.Entry>()
    override fun append(entry: SalesLedger.Entry) { LedgerStore.checkAppend(entries, entry); entries += entry }
    override fun all(): List<SalesLedger.Entry> = entries.toList()
}
