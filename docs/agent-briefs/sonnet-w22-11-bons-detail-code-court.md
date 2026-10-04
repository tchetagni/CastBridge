# w22-11 — Bons au détail à code court (niveau 2) : codes à gratter `NB-XXXX-XXXX-XXXX-XXXX` reconnus par empreinte, lots par revendeur, anti-force brute, mise en attente sur la TV hors ligne
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (entropie, verrous, empreintes seules) · statut : **ATTEND w22-06**
> **Groupe : W22-N2** · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.retail.**'` + `:core:test --tests 'castbridge.core.wallet.RetailCode*'` + `python3 -m pytest tools/wallet/test_make_retail_codes.py`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 4.2) ; modèle W5 : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4 d, § 5.3, § 5.4 (lot signé par le propriétaire, serial public, empreintes seules, rapprochement des ventes, révocation). Branche `claude/w22-11-bons-detail`. Rapport : `docs/agent-reports/sonnet-w22-11.md`.

## Fichiers possédés
- **Nouveaux** : `tools/wallet/make_retail_codes.py` (+ test) : `--cur --amount --count ≤ 5000 --expire --reseller <id|-> --key <clé hors ligne>` ⇒ manifeste signé (serial | SHA-256 du code) + fichier secret des codes (jamais importé) ; `backend/src/main/java/castbridge/server/wallet/retail/{RetailBatchImport,RetailRedeem,RetailBruteForceGuard}.java` + migration additive « plus haut + 1 » (`wallet_retail_batch`, `wallet_retail_code(serial, code_hash UNIQUE, status, reseller, redeemed_by, attempts)`) + tests ; `android/core/src/main/kotlin/castbridge/core/wallet/RetailCode.kt` (format, contrôle Crockford, mise en attente hors ligne) + test.
- **Interdit** : `C/wallet/{Voucher,VoucherCode}.kt`, classes de w22-06 (consommées).

## Spécification
16 caractères Crockford = 2 de lot + 13 secrets (≈ 65 bits) + 1 contrôle ; hors ligne : la TV vérifie le format et le met en attente (« Code enregistré : confirmé à la prochaine connexion ») ; en ligne (`sync`) : recherche par empreinte, lot non révoqué ni expiré, une seule utilisation (clé `rtl:<serial>`) ; 5 essais faux / h / identité puis verrou 24 h ; 1 000 essais faux / h global ⇒ alerte ; MBOKO au détail : **oui** mais lié à la première TV qui le rachète (pas de cible imprimée possible) ; rapport par revendeur (émis, rachetés, révoqués).

## Critères d'acceptation
- Code correct ⇒ crédité une fois ; deuxième TV ⇒ `VOUCHER_USED` ; faute de frappe détectée localement ; 6e essai faux dans l'heure ⇒ verrou ; base des empreintes seule inutilisable pour retrouver un code (test : aucune colonne ne contient le code clair).
