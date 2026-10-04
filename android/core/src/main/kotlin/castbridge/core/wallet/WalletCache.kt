package castbridge.core.wallet

import castbridge.core.owner.KeyRing

/** Stockage injecté du cache (un fichier sur la TV, une chaîne en mémoire dans les tests). Écriture ATOMIQUE à la charge de l'appelant. */
interface WalletStore {
    fun read(): String?
    fun write(text: String)
}

/**
 * Cache du portefeuille de CETTE TV (conception W22 § 3.5, § 4.1). Pur : aucune horloge, aucun réseau, aucun fichier.
 *
 * - Garde le DERNIER instantané `cbw1` valide de cette identité ([identity]) : un instantané de `seq` plus petit est ignoré, un instantané altéré ou d'une autre TV est refusé.
 * - Garde les bons `cbv1` EN ATTENTE (≤ [MAX_PENDING] bons, ≤ [MAX_PENDING_NDEM] NDEM, ≤ [MAX_PENDING_MBOKO] MBOKO) et les nonces déjà vus (≤ [MAX_SEEN]).
 * - N'AUGMENTE JAMAIS un solde : le solde affiché est celui de l'instantané signé, l'attente est un AUTRE champ ([pendingNdem], [pendingMboko]) que le serveur confirme à la
 *   synchronisation ; aucune opération publique ne crédite quoi que ce soit à partir d'une entrée locale (la TV ne crée pas de valeur).
 * - Ce n'est pas une autorité : effacé il se reconstruit, et chaque ligne est RE-vérifiée (signature) à la lecture ; une ligne altérée est ignorée.
 */
class WalletCache(private val store: WalletStore, private val ring: KeyRing, private val voucherKeys: VoucherKeys, private val identity: String) {
    private var snapshotToken: String? = null
    private var current: Snapshot? = null
    private val pendingList = ArrayList<Pair<String, Voucher>>()   // (texte canonique, bon)
    private val seen = ArrayList<String>()                         // nonces, les plus anciens d'abord

    /** Dernier instantané signé valide de cette TV, ou null. */
    val snapshot: Snapshot? get() = current

    /** Bons vérifiés, en attente de confirmation par le serveur (jamais comptés dans le solde). */
    val pending: List<Voucher> get() = pendingList.map { it.second }
    val pendingNdem: Long get() = pending.filter { it.currency == WalletCurrency.NDEM }.sumOf { it.amount }
    val pendingMboko: Long get() = pending.filter { it.currency == WalletCurrency.MBOKO }.sumOf { it.amount }

    sealed class SnapshotOffer {
        object Stored : SnapshotOffer()
        /** Même `seq` ou plus ancien : le cache garde l'actuel. */
        object Ignored : SnapshotOffer()
        data class Refused(val reason: WalletRefusal) : SnapshotOffer()
    }

    sealed class VoucherOffer {
        data class Added(val voucher: Voucher) : VoucherOffer()
        /** Déjà en attente ou déjà vu sur cette TV : une seule ligne. */
        object Duplicate : VoucherOffer()
        /** Plafonds d'attente atteints : « Connectez la TV pour confirmer vos bons ». */
        object Full : VoucherOffer()
        data class Refused(val reason: WalletRefusal) : VoucherOffer()
        /** Faute de frappe localisée (code tapé). */
        data class BadGroup(val group: Int) : VoucherOffer()
        object Malformed : VoucherOffer()
    }

    init { load() }

    /** Propose un instantané reçu du serveur. */
    fun offerSnapshot(token: String?): SnapshotOffer {
        val v = Snapshot.verify(token, ring, identity)
        if (v is Verdict.Rejected) return SnapshotOffer.Refused(v.reason)
        val s = (v as Verdict.Accepted).value
        val cur = current
        if (cur != null && s.seq <= cur.seq) return SnapshotOffer.Ignored
        snapshotToken = token; current = s; save()
        return SnapshotOffer.Stored
    }

    /** Propose un bon saisi, lu d'un fichier ou d'un QR (le même [text]). [nowMs] : temps de la TV (`TvClock.now`) ; [deviceCode] : code d'appareil de cette TV. */
    fun receiveVoucher(text: String, nowMs: Long, deviceCode: String): VoucherOffer {
        val v = when (val d = VoucherCode.decode(text)) {
            is VoucherCode.Decoded.Ok -> d.voucher
            is VoucherCode.Decoded.BadGroup -> return VoucherOffer.BadGroup(d.group)
            VoucherCode.Decoded.Malformed -> return VoucherOffer.Malformed
        }
        val r = Voucher.verify(v, voucherKeys, deviceCode, nowMs)
        if (r is Verdict.Rejected) return VoucherOffer.Refused(r.reason)
        if (v.nonceHex in seen || pendingList.any { it.second.nonceHex == v.nonceHex }) return VoucherOffer.Duplicate
        if (pendingList.size >= MAX_PENDING || pendingNdem + (if (v.currency == WalletCurrency.NDEM) v.amount else 0) > MAX_PENDING_NDEM ||
            pendingMboko + (if (v.currency == WalletCurrency.MBOKO) v.amount else 0) > MAX_PENDING_MBOKO) return VoucherOffer.Full
        pendingList += VoucherCode.encode(v) to v
        seen += v.nonceHex
        while (seen.size > MAX_SEEN) seen.removeAt(0)
        save()
        return VoucherOffer.Added(v)
    }

    /** Le serveur a répondu pour ce bon (confirmé ou refusé) : la ligne d'attente est retirée (le nonce reste « vu »). Ne modifie AUCUN solde. */
    fun dropPending(nonceHex: String) { if (pendingList.removeAll { it.second.nonceHex == nonceHex }) save() }

    /** Efface tout (« Réinitialiser ») : le cache se reconstruira à la synchronisation. */
    fun clear() { snapshotToken = null; current = null; pendingList.clear(); seen.clear(); save() }

    private fun save() {
        val lines = ArrayList<String>().apply {
            add(HEADER)
            snapshotToken?.let { add("S $it") }
            pendingList.forEach { add("P ${it.first}") }
            seen.forEach { add("N $it") }
        }
        store.write(lines.joinToString("\n") + "\n")
    }

    private fun load() {
        val text = runCatching { store.read() }.getOrNull() ?: return
        val lines = text.lines()
        if (lines.firstOrNull() != HEADER) return
        for (l in lines.drop(1)) {
            val tag = l.take(2); val rest = l.drop(2)
            when (tag) {
                "S " -> { val v = Snapshot.verify(rest, ring, identity); if (v is Verdict.Accepted && (current == null || v.value.seq > current!!.seq)) { current = v.value; snapshotToken = rest } }
                "P " -> {
                    val d = VoucherCode.decode(rest) as? VoucherCode.Decoded.Ok ?: continue
                    if (d.voucher.version == 1 && d.voucher.signatureValid(voucherKeys) && pendingList.size < MAX_PENDING && pendingList.none { it.second.nonceHex == d.voucher.nonceHex }) pendingList += VoucherCode.encode(d.voucher) to d.voucher
                }
                "N " -> if (rest.length == 20 && rest.all { it in "0123456789abcdef" } && rest !in seen && seen.size < MAX_SEEN) seen += rest
            }
        }
    }

    companion object {
        const val HEADER = "castbridge-wallet-cache-v1"
        const val MAX_PENDING = 10
        const val MAX_PENDING_NDEM = 20_000L
        const val MAX_PENDING_MBOKO = 200L
        const val MAX_SEEN = 500
    }
}
