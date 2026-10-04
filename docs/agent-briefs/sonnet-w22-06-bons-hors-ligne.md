# w22-06 — Bons hors ligne `cbv1` : outil du propriétaire (clé hors ligne, lots), import au serveur, confirmation à la synchronisation (doublons refusés), rachat sans Internet sur la TV (bons en attente)
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (double emploi, clé hors ligne, lot importé, cible) · statut : **ATTEND w22-02 et w22-03**
> **Groupe : W22-N1** (ordre 3 bis, en parallèle de w22-05) · porte : `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.voucher.**'` + `:core:test --tests 'castbridge.core.wallet.VoucherRedeemer*'` + `python3 -m pytest tools/wallet/test_make_wallet_vouchers.py`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 4.1, § 3.4 `cbv1`, § 5.2 S-9, R-E11, D-W22-8). Branche `claude/w22-06-bons-hors-ligne`. Rapport : `docs/agent-reports/sonnet-w22-06.md`.

## Objectif (autonome)
Un bon signé par la **clé hors ligne du propriétaire** s'active sur une TV **sans Internet** (crédit « en attente », visible) et n'est crédité au grand livre qu'à la synchronisation suivante, si sa signature est valide **et** son nonce appartient à un **lot importé** non révoqué **et** n'a jamais servi. Comme toute dépense passe par l'API après la synchronisation, un bon montré à deux TV ou rejoué après une restauration ne coûte rien : le second est refusé.

## Fichiers possédés
- **Nouveaux** : `tools/wallet/make_wallet_vouchers.py` (+ `tools/wallet/test_make_wallet_vouchers.py`) : `--cur NDEM|MBOKO --amount N --count C --expire AAAA-MM-JJ --target any|<code d'appareil> --key <fichier de clé hors ligne>` ⇒ `lot-<id>.manifest.signed` (en-tête, liste triée des nonces avec monnaie, montant, cible ; signature Ed25519 de la clé hors ligne) et `lot-<id>-BONS-SECRET.txt` (un bon `cbv1` texte par ligne, **à imprimer puis détruire**, jamais commité) ; refuse MBOKO avec `--target any`, `count` > 5 000, montant > 5 000 NDEM ou > 50 MBOKO ; `--check` revérifie un manifeste. `backend/src/main/java/castbridge/server/wallet/voucher/{VoucherVerifier,VoucherBatchImport,VoucherSyncContributor,VoucherAdminController}.java` + tests ; `android/core/src/main/kotlin/castbridge/core/wallet/VoucherRedeemer.kt` + `android/core/src/test/kotlin/castbridge/core/wallet/VoucherRedeemerTest.kt`.
- **Interdit** : `C/wallet/{Voucher,VoucherCode,WalletCache}.kt` (w22-03, consommés), classes de w22-02/05, `R/`, `S/`.

## Spécification
1. **Clé** : portée « bons portefeuille », jamais sur le serveur ; sa clé **publique** est compilée dans l'APK TV (liste comme `activation-trusted-keys.txt`, nom `wallet-voucher-keys.txt` ; le câblage de build est dans w22-07) et dans `castbridge.wallet.voucher-pubkeys` côté API.
2. `POST /admin/wallet/vouchers/import` (TOTP) : vérifie la signature du manifeste, insère `wallet_voucher_batch` + `wallet_voucher` (nonces), idempotent par `manifest_sha` ; `POST /admin/wallet/vouchers/revoke-batch`.
3. `VoucherSyncContributor` (interface `SyncContributor` de w22-02) : pour chaque bon en attente reçu dans `sync` (≤ 10) : signature (Java, même domaine `castbridge-wallet-voucher-v1`, vecteurs `tools/wallet/wallet-vectors.json`), échéance (horloge serveur, +24 h de tolérance), cible = identité, nonce présent dans un lot **importé non révoqué** ; transaction `VOUCHER` à clé **globale** `vch:<nonce>` ; nonce déjà racheté **par la même identité** ⇒ `OK` silencieux (restauration) ; **par une autre** ⇒ `VOUCHER_USED`, `rejected_dupes + 1` ; interrupteur `switch.vouchers`.
4. `VoucherRedeemer` (cœur, TV, pur ; horloge `TvClock` et stockage injectés) : `redeemOffline(text, deviceCode, nowMs)` ⇒ vérifie (`Voucher.verify` de w22-03), refuse un nonce déjà vu, ajoute à l'attente de `WalletCache` dans la limite des plafonds (10 bons, 20 000 NDEM, 200 MBOKO ⇒ « Connectez la TV pour confirmer vos bons ») ; `pendingForSync()` ; `applySyncResult(results)` : retire les confirmés et les refusés, rend les messages français à afficher.

## Critères d'acceptation (mutations au rapport)
- Python : un lot de 100 bons, manifeste vérifié ; chaque bon du fichier secret vérifie contre la clé publique ; MBOKO `any` refusé ; codes non dérivés d'un secret (aléa `secrets`).
- Java : bon valide d'un lot importé ⇒ crédité une fois ; **deux identités** présentent le même bon ⇒ la première créditée, la seconde `VOUCHER_USED` ; même identité deux fois (restauration) ⇒ une seule écriture, pas d'erreur ; bon signé par la bonne clé mais nonce **hors lot importé** ⇒ refusé (mutation : ne pas exiger le lot ⇒ échec) ; lot révoqué ⇒ refusé ; bon d'une autre cible ⇒ `VOUCHER_OTHER_TV`.
- Kotlin : rachat hors ligne affiché « en attente », jamais ajouté au solde ; le même bon deux fois ⇒ une ligne ; plafond d'attente respecté ; résultat de synchronisation appliqué.
- Scénario « deux TV hors ligne, même bon » écrit en test de bout en bout Java (deux `sync` successifs) : conservation tenue, un seul crédit.

## À ne pas faire
- Mettre la clé privée des bons sur le serveur ou dans un dépôt ; rendre un bon dépensable avant confirmation ; accepter un bon MBOKO non ciblé.
