package castbridge.server.wallet.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Transaction à double entrée : pour chaque monnaie, la somme des montants est nulle (sinon {@code UNBALANCED}). {@code refs} = références de blocage
 * (eid) pour ESCROW_LOCK, SETTLE, ESCROW_REFUND ; vide sinon. {@code contentSha} = SHA-256 de la forme canonique (genre, références triées,
 * écritures triées) : même clé + même empreinte = rejeu ; même clé + autre empreinte = {@code IDEM_CONFLICT}. Les constructeurs statiques sont purs.
 */
public record Txn(TxnKind kind, String idemKey, List<Entry> entries, List<String> refs, String contentSha) {
    public static final int MAX_SEATS = 8;

    public Txn {
        if (kind == null || idemKey == null || idemKey.isBlank() || idemKey.length() > 200) throw new LedgerException(WalletReason.BAD_TXN, "Genre ou clé d'opération invalide");
        if (entries == null || entries.isEmpty()) throw new LedgerException(WalletReason.BAD_TXN, "Transaction sans écriture");
        entries = List.copyOf(entries);
        refs = refs == null ? List.of() : List.copyOf(refs);
        Map<Currency, Long> sums = new EnumMap<>(Currency.class);
        for (Entry e : entries) {
            if (e.amount() == 0) throw new LedgerException(WalletReason.BAD_TXN, "Écriture de montant nul");
            try {
                sums.merge(e.account().currency(), e.amount(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new LedgerException(WalletReason.BAD_TXN, "Montant hors limites");
            }
        }
        for (Map.Entry<Currency, Long> s : sums.entrySet()) {
            if (s.getValue() != 0) throw new LedgerException(WalletReason.UNBALANCED, "Transaction déséquilibrée en " + s.getKey() + " : " + s.getValue());
        }
        String computed = sha(kind, entries, refs);
        if (contentSha != null && !contentSha.equals(computed)) throw new LedgerException(WalletReason.BAD_TXN, "Empreinte de contenu inexacte");
        contentSha = computed;
    }

    public Txn(TxnKind kind, String idemKey, List<Entry> entries, List<String> refs) { this(kind, idemKey, entries, refs, null); }

    private static String sha(TxnKind kind, List<Entry> entries, List<String> refs) {
        StringBuilder sb = new StringBuilder("castbridge-txn-v1\n").append(kind).append('\n');
        refs.stream().sorted().forEach(r -> sb.append("ref:").append(r).append('\n'));
        entries.stream().sorted(Comparator.comparing((Entry e) -> e.account().key()).thenComparingLong(Entry::amount))
                .forEach(e -> sb.append(e.account().key()).append('|').append(e.amount()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ---- constructeurs de transactions (purs) ----

    private static void positive(long a, String what) {
        if (a < 1) throw new LedgerException(WalletReason.BAD_TXN, what + " : le montant doit être positif");
    }

    /** Attribution : {@code SYS:GRANT −a ; id:DISPO +a}. */
    public static Txn grant(String id, Currency cur, long amount, String key) {
        positive(amount, "Attribution");
        return new Txn(TxnKind.GRANT, key, List.of(new Entry(AccountRef.sys(AccountRef.GRANT, cur), -amount), new Entry(AccountRef.dispo(id, cur), amount)), List.of());
    }

    /** Attribution en plusieurs monnaies sous une même clé (ouverture illimitée : {@code grant:<id>:open-unlimited}). */
    public static Txn grantMulti(String id, Map<Currency, Long> amounts, String key) {
        List<Entry> es = new ArrayList<>();
        for (Map.Entry<Currency, Long> a : amounts.entrySet()) {
            positive(a.getValue(), "Attribution");
            es.add(new Entry(AccountRef.sys(AccountRef.GRANT, a.getKey()), -a.getValue()));
            es.add(new Entry(AccountRef.dispo(id, a.getKey()), a.getValue()));
        }
        return new Txn(TxnKind.GRANT, key, es, List.of());
    }

    /** Conversion à la demande du joueur, {@code q} MBOKO, dans le sens {@code dir} (formules de {@link Conversion}). */
    public static Txn convert(String id, Conversion.Direction dir, long q, WalletPolicy policy, String key) {
        Conversion.Quote c = Conversion.quote(dir, q, policy);
        AccountRef nDispo = AccountRef.dispo(id, Currency.NDEM), mDispo = AccountRef.dispo(id, Currency.MBOKO);
        AccountRef conN = AccountRef.sys(AccountRef.CONVERT, Currency.NDEM), conM = AccountRef.sys(AccountRef.CONVERT, Currency.MBOKO);
        List<Entry> es = new ArrayList<>();
        if (dir == Conversion.Direction.N2M) {
            es.add(new Entry(nDispo, -c.ndemGross())); es.add(new Entry(conN, c.ndemGross()));
            es.add(new Entry(conM, -q)); es.add(new Entry(mDispo, q));
        } else {
            es.add(new Entry(mDispo, -q)); es.add(new Entry(conM, q));
            es.add(new Entry(conN, -c.ndemGross()));
            if (c.fee() > 0) es.add(new Entry(AccountRef.sys(AccountRef.FEE, Currency.NDEM), c.fee()));
            if (c.ndemNet() > 0) es.add(new Entry(nDispo, c.ndemNet()));
        }
        return new Txn(TxnKind.CONVERT, key, es, List.of());
    }

    /** Transfert libre entre deux identités de TV (src ≠ dst), une seule transaction. */
    public static Txn transfer(String src, String dst, Currency cur, long amount, String key) {
        positive(amount, "Transfert");
        if (src == null || src.equals(dst)) throw new LedgerException(WalletReason.BAD_TXN, "Transfert : l'émetteur et le destinataire doivent différer");
        return new Txn(TxnKind.TRANSFER, key, List.of(new Entry(AccountRef.dispo(src, cur), -amount), new Entry(AccountRef.dispo(dst, cur), amount)), List.of());
    }

    /** Blocage d'une mise : {@code per × k} passe de DISPO à BLOQUE ; la clé d'opération est le blocage {@code eid} (ex. {@code lock:<id>:<clé TV>}). */
    public static Txn lock(String id, Currency cur, long per, int k, String eid) {
        positive(per, "Blocage");
        if (k < 1 || k > MAX_SEATS) throw new LedgerException(WalletReason.BAD_TXN, "Blocage : 1 à " + MAX_SEATS + " sièges");
        long a = per * k;
        return new Txn(TxnKind.ESCROW_LOCK, eid, List.of(new Entry(AccountRef.dispo(id, cur), -a), new Entry(AccountRef.bloque(id, cur), a)), List.of(eid));
    }

    /** Règlement d'une partie en une transaction : chaque blocage sort de BLOQUE, la cagnotte transite par {@code SYS:POT} (retour à 0), le non-utilisé est rendu. */
    public static Txn settle(String rid, List<Settlement.Line> lines, Currency cur) {
        if (lines == null || lines.isEmpty()) throw new LedgerException(WalletReason.BAD_TXN, "Règlement sans blocage");
        AccountRef pot = AccountRef.sys(AccountRef.POT, cur);
        List<Entry> es = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        for (Settlement.Line l : lines) {
            refs.add(l.eid());
            es.add(new Entry(AccountRef.bloque(l.id(), cur), -l.amount()));
            long back = l.amount() - l.used();
            if (back > 0) es.add(new Entry(AccountRef.dispo(l.id(), cur), back));
            if (l.used() > 0) es.add(new Entry(pot, l.used()));
            if (l.pay() > 0) {
                es.add(new Entry(pot, -l.pay()));
                es.add(new Entry(AccountRef.dispo(l.id(), cur), l.pay()));
            }
        }
        return new Txn(TxnKind.SETTLE, "settle:" + rid, es, refs);
    }

    /** Rendu d'un blocage entier (abandon, échéance) : {@code BLOQUE −a ; DISPO +a}. */
    public static Txn refund(String eid, String id, Currency cur, long amount) {
        positive(amount, "Rendu");
        return new Txn(TxnKind.ESCROW_REFUND, "refund:" + eid, List.of(new Entry(AccountRef.bloque(id, cur), -amount), new Entry(AccountRef.dispo(id, cur), amount)), List.of(eid));
    }

    /** Bon hors ligne : clé globale {@code vch:<nonce>} (un bon ne sert qu'une fois, toutes identités confondues). */
    public static Txn voucher(String id, Currency cur, long amount, String nonce) {
        positive(amount, "Bon");
        return new Txn(TxnKind.VOUCHER, "vch:" + nonce, List.of(new Entry(AccountRef.sys(AccountRef.VOUCHER, cur), -amount), new Entry(AccountRef.dispo(id, cur), amount)), List.of());
    }

    /** Correction de l'administrateur : {@code id:DISPO +delta ; SYS:ADJUST −delta} (delta signé, non nul). */
    public static Txn adjust(String id, Currency cur, long delta, String key) {
        if (delta == 0) throw new LedgerException(WalletReason.BAD_TXN, "Correction de montant nul");
        return new Txn(TxnKind.ADJUST, key, List.of(new Entry(AccountRef.dispo(id, cur), delta), new Entry(AccountRef.sys(AccountRef.ADJUST, cur), -delta)), List.of());
    }
}
