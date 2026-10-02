# w5-08 — Serveur : grand livre des jetons du Quiz, bons de jetons signés pour la TV, rapport de dépenses, réconciliation et anomalies

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w5-06)
> **Groupe : W5b-2** (vague W5b) · prérequis : w5-06, w5-05 · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Token*Test'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W5-PORTE-JETONS-REPRISE-2026-10-02.md` (§ 2, § 4, § 7). Il **prévaut** sur ce cahier et sur la conception § 3.4 (c) / § 6.5 : corps du bon à **6 lignes** (`fresh=0|1`), fenêtre **72 h** (48 h pour un bon d'ouverture) avec renouvellement de fenêtre, premier bon d'un `install_pub` toujours `fresh=1`, reprise `TokenReconciler.recover` (reliquat restitué dans les limites 2 / installation / 30 j et 3 / licence / 90 j, `HELD` au-delà de 120 jetons cumulés / 90 j), tables additives `token_install` et `token_recovery` + colonnes `token_grant.fresh/chain_no/window_renewals` (migration « plus haut + 1 »), anomalies `TOKEN_CHAIN_REPLAY`, `TOKEN_WALLET_LOST`, `TOKEN_RECOVERY_ABUSE`, `TOKEN_RECOVERY_HELD`, comparaison de `seq` dans la chaîne courante seulement, réponse enrichie de `recovery`. Prérequis supplémentaire : correctif cœur `claude/sonnet-w5-02-fix` et miroir w5-05 du corps à 6 lignes.

**Vague 5b · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w5-06 ; w5-05 pour `TokenGrant.java`).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.3, § 3.4 (c), § 6.4, § 6.5, § 10. Branche `claude/sonnet-w5-08`. Rapport : `docs/agent-reports/sonnet-w5-08.md`. **Décision du propriétaire (P4) : seul le Quiz consomme des jetons.**

## Objectif
Le serveur tient un **compte de jetons par licence** (`token_account`, `token_ledger` à séquence), crédite (commande, bon, accueil, ajustement), prépare des **bons de jetons** (`type=tokens`, ≤ `offlineGrantMax`, liés à la TV et à sa clé d'installation, `grant_seq` croissant) pour que la TV dépense **hors ligne**, reçoit le **rapport de dépenses** chaîné de la TV, l'inscrit (idempotent), **réapprovisionne**, détecte `TOKEN_OVERSPEND` et `TOKEN_REPLAY`, coupe les bons hors ligne à une installation suspecte, réémet les bons non épuisés pour une **nouvelle installation**.

## Pourquoi (preuves)
- `B/licenses/EnvelopeIssuer.java` (commandes, ordres, révocations signés : modèle d'émission d'un nouveau type) ; `B/licenses/LicenseKeyring.java` (`sign`) ; w5-05 `B/shop/wire/TokenGrant.java` ; w5-06 schéma (`token_account`, `token_ledger`, `token_grant`), `ShopProperties` ; w5-07 interface `TokenCrediting`.
- `C/tokens/TokenWallet.report()` (w5-02) : format du rapport (`license`, `install`, `lastGrant`, `spentTotal`, lignes `spend`, `mac`) : le serveur **ne peut pas** vérifier le HMAC (clé dérivée de la clé privée d'installation, jamais transmise) ; il vérifie la **cohérence** (séquence, totaux, bons livrés).

## Fichiers possédés
Nouveaux `B/shop/tokens/TokenLedgerService.java`, `TokenGrantService.java`, `TokenReportController.java` (`POST /api/v1/shop/tokens/report`), `TokenReconciler.java`, `TokenAnomalies.java` (ou utilisation d'`AnomalySink` de w5-06), `TokenCreditingImpl.java` (implémente l'interface de w5-07), `BT/shop/tokens/**` ; modifié `B/licenses/EnvelopeIssuer.java` (**une** méthode additive `tokensGrant(...)`). **Hors zone** : `B/shop/order/**`, `B/shop/rental/**`, `B/shop/admin/**`, `B/shop/wire/**`, Android, docs.

## Étapes
1. `TokenLedgerService` : `credit(license, amount, kind=CREDIT|ADJUST, ref, note)` (verrou de ligne `token_account`, `seq` = dernier + 1, `UNIQUE(license, seq)`), `spend(license, device, seq, amount, ref)` idempotent par `(license, device, seq)` (`ref = "tv:<device>:<seq>"`), `balance(license)` = `{balance, reserved, available = balance − reserved}`, `history(license, limit)`. Crédit d'accueil : `welcomeIfFirst(license)` (`tokens.welcome` de la grille, une fois par licence, `kind=CREDIT ref=welcome`), appelé par w5-07 à la première `me` d'une production.
2. `TokenGrantService.topUp(proof): List<String>` : `target = available − reservedForDevice` ; si `available > 0` et `offlineAllowed(install)` : `amount = min(available, offlineGrantMax − (livré non dépensé sur cette installation))` ; si `amount ≥ 1` : `token_grant` (`grant_seq` = dernier + 1 pour `(license, device)`), enveloppe via `EnvelopeIssuer.tokensGrant(target = Device(k, empreintes), license, grantSeq, amount, installPub, expiry, window 30 j)`, `reserved += amount`, état `ISSUED` ; retourne les bons `ISSUED|DELIVERED` non `SETTLED` (réémis tels quels à chaque `me` jusqu'à accusé). `markDelivered(grantIds)` sur accusé de la TV.
3. `TokenReportController` : corps = `{request, report}` ; `ShopRequestVerifier` (w5-07) ; `TokenReconciler.apply(proof, report)` : analyse les lignes `spend` (format w5-02), **ignore** celles déjà inscrites, inscrit les nouvelles (`spend`), met à jour `token_grant.spent_reported` (FIFO sur les bons de l'installation), `reserved −= dépensé`, `state = SETTLED` quand épuisé ; **anomalies** : Σ dépenses rapportées > Σ bons livrés à cette installation ⇒ `TOKEN_OVERSPEND` (dépenses au-delà **non** inscrites, `offlineAllowed(install) = false`) ; `lastSpendSeq` rapporté < dernier connu ⇒ `TOKEN_REPLAY` (idem) ; `walletState = unreadable` ⇒ `TOKEN_WALLET_UNREADABLE` (information) ; réponse `{ackedSeq, grants: [...], balanceServer, offlineAllowed, message}`.
4. **Nouvelle installation** (`installPub` différent de celui des bons `ISSUED|DELIVERED` de ce `device_code`) : les bons non épuisés passent `VOID` (leur reliquat revient à `available`), une réémission vers la nouvelle clé part au prochain `topUp` ; au plus **2 réinstallations par 12 mois** avec reliquat > 0, sinon `TOKEN_REINSTALL_ABUSE` (reliquat perdu pour l'hors ligne ; dépense en ligne possible plus tard : hors vague).
5. `offlineAllowed(install)` : vrai par défaut ; faux après anomalie, rétabli par le propriétaire (w5-09).
6. Tests : crédit/solde ; `topUp` plafonne à 60 ; deux `topUp` sans rapport ⇒ pas de doublon ; rapport de 3 dépenses ⇒ inscrites une fois même rejoué ; dépassement ⇒ `TOKEN_OVERSPEND` et plus de bons ; séquence qui recule ⇒ `TOKEN_REPLAY` ; nouvelle installation ⇒ `VOID` + réémission ; accueil une fois ; enveloppe `tokens` vérifiée par `TokenGrant.verify` (w5-05) avec les empreintes et la clé d'installation de test ; `UNIQUE(license, seq)` sous concurrence (deux threads).

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='TokenLedgerServiceTest,TokenGrantServiceTest,TokenReconcilerTest,TokenReportControllerTest'   # vert
cd backend && ./mvnw -q -o test   # suite complète verte
grep -rn 'quiz\|QUIZ' backend/src/main/java/castbridge/server/shop/tokens | grep -v 'game = "quiz"' | head   # le seul jeu connu est « quiz »
grep -rn 'BigDecimal\|xaf\|XAF' backend/src/main/java/castbridge/server/shop/tokens   # 0 hit (le grand livre ne connaît pas l'argent)
```

## Cas limites
- Rapport sans aucune ligne (TV neuve) : `ackedSeq = 0`, `topUp` normal.
- Rapport d'une installation inconnue mais licence connue (TV réinstallée **avant** tout bon) : traité comme nouvelle installation.
- Horodatage `at` incohérent dans les lignes : ignoré (la séquence fait foi), noté.
- `available < 1` : aucun bon ; message « solde épuisé : rechargez depuis la Boutique ».

## À ne pas faire
Pas de déploiement ; pas de commit sur les branches partagées ; aucune conversion en argent ; aucun jeton créé sans `kind` et `ref` ; ne pas vérifier un HMAC qu'on ne peut pas vérifier (ne pas prétendre) ; ne pas modifier `B/licenses/**` au-delà de la méthode additive.

## Rapport
`STATUT`, JSON exact de `/tokens/report` (pour w5-12, w5-16), règles de réapprovisionnement finales, liste des anomalies (pour w5-09, w5-10).
