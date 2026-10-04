package castbridge.server.wallet.core;

import java.util.Set;
import java.util.regex.Pattern;

/** Compte = (titulaire, monnaie, poche). Titulaire : identité de TV {@code XXXX-XXXX-XXXX-XXXX} ou titulaire système {@code SYS:…} (poche SYS). */
public record AccountRef(String holder, Currency currency, Pocket pocket) implements Comparable<AccountRef> {
    public static final Pattern IDENTITY = Pattern.compile("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}");
    public static final String GRANT = "SYS:GRANT", VOUCHER = "SYS:VOUCHER", REWARD = "SYS:REWARD", CONVERT = "SYS:CONVERT",
            FEE = "SYS:FEE", ADJUST = "SYS:ADJUST", POT = "SYS:POT";
    public static final Set<String> SYSTEM_HOLDERS = Set.of(GRANT, VOUCHER, REWARD, CONVERT, FEE, ADJUST, POT);

    public AccountRef {
        if (holder == null || currency == null || pocket == null) throw new LedgerException(WalletReason.BAD_TXN, "Compte incomplet");
        if (pocket == Pocket.SYS) {
            if (!SYSTEM_HOLDERS.contains(holder)) throw new LedgerException(WalletReason.BAD_TXN, "Titulaire système inconnu : " + holder);
        } else if (!IDENTITY.matcher(holder).matches()) {
            throw new LedgerException(WalletReason.BAD_TXN, "Identité invalide (format XXXX-XXXX-XXXX-XXXX) : " + holder);
        }
    }

    public static AccountRef dispo(String id, Currency c) { return new AccountRef(id, c, Pocket.DISPO); }

    public static AccountRef bloque(String id, Currency c) { return new AccountRef(id, c, Pocket.BLOQUE); }

    public static AccountRef sys(String holder, Currency c) { return new AccountRef(holder, c, Pocket.SYS); }

    public boolean isSystem() { return pocket == Pocket.SYS; }

    /** Clé canonique stable (tri, empreinte, ordre de verrouillage). */
    public String key() { return holder + "|" + currency + "|" + pocket; }

    @Override public int compareTo(AccountRef o) { return key().compareTo(o.key()); }
}
