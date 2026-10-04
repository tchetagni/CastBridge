# w22-03 — Cœur Kotlin : formats signés `cbw1` (instantané), `cbe1` (blocage), `cbr1` (résultat), `cbv1` (bon hors ligne) ; cache du portefeuille de la TV ; motifs et vue de la carte ; vecteurs communs Kotlin ↔ Java
<!-- routage architecte 2026-10-04 (W22, niveau 1) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (signatures, domaines, analyse stricte, aucune création de valeur sur la TV) · statut : **PRÊT** (cœur pur : permis pendant le gel)
> **Groupe : W22-N1** (ordre 1 bis, en parallèle de w22-01) · porte : `:core:test --tests 'castbridge.core.wallet.*'` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 3.4, § 3.5, § 4.1, § 7.1, § 7.6). Branche `claude/w22-03-formats-signes`. Rapport : `docs/agent-reports/sonnet-w22-03.md`.

## Objectif (autonome)
La TV et le service de jeu (`server-play`, Kotlin, dépend de `:core`) doivent lire et vérifier les pièces signées du portefeuille ; le service doit **signer** les résultats. Modèle à suivre : le ticket `cbp1` (signé en Java par `backend/src/main/java/castbridge/server/play/PlayTicketService.java`, vérifié en Kotlin par `server-play/src/main/kotlin/castbridge/play/entitlement/TicketVerifier.kt`) : `<préfixe>.<b64url charge JSON>.<b64url signature Ed25519>`, signature sur `<domaine>\n<préfixe>.<charge b64url>`, charge signée telle qu'envoyée. Réutiliser l'Ed25519 déjà présent dans le cœur (lire `android/core/src/main/kotlin/castbridge/core/owner/` : `Signer`, `KeyRing`, `Envelope`) et l'alphabet Crockford déjà présent (`Base32C` ou équivalent ; le trouver par `grep -rn Crockford android/core/src/main`).

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/wallet/{WalletFormats,Snapshot,EscrowTicket,PlayResult,Voucher,VoucherCode,WalletCache,WalletReason,WalletView}.kt` ; tests `android/core/src/test/kotlin/castbridge/core/wallet/{WalletFormatsTest,VoucherCodeTest,WalletCacheTest,WalletViewTest,WalletVectorsTest}.kt` ; `tools/wallet/wallet-vectors.json` (`castbridge-wallet-vectors-v1` : clés de test **déterministes**, pièces signées dorées, pièces fausses avec leur motif de refus).
- **Interdit** : `R/`, `S/`, `server-play/**`, `backend/**`, `C/quiz/**`, `C/tokens/**`.

## Spécification
1. `Snapshot` (`cbw1`, domaine `castbridge-wallet-snapshot-v1`) : champs `kid, id, ed, n, nb, m, mb, seq, at, flags{frozen, stakesN, stakesM}` ; `verify(token, ring, expectedId)` ⇒ `Accepted | Rejected(reason)` (illisible, clé inconnue, signature, autre TV, champs hors bornes : montants < 0 ou > 10¹², `seq` < 0).
2. `EscrowTicket` (`cbe1`, domaine `castbridge-wallet-escrow-v1`) : `aud="castbridge-play", kid, eid (22 car. b64url), id, cur, per, k (1..8), amt (= per × k, vérifié), iat, exp (exp − iat ≤ 30 min)` ; `verify(token, ring, nowMs)`.
3. `PlayResult` (`cbr1`, domaine `castbridge-play-result-v1`) : `kid, rid, room, game, cur, per, kind END|ABORT, at, lines=[[eid, id, used, pay]…]` (≤ 16 lignes) ; `sign(result, signer)` (pour le service) et `verify` ; `check()` local : Σ pay = Σ used, `eid` uniques.
4. `Voucher` (`cbv1`, domaine `castbridge-wallet-voucher-v1`) : binaire de 27 o + 64 o de signature (§ 3.4 : version, index de clé, monnaie, montant u32, nonce 10 o, échéance u16 en jours depuis 2026-01-01, cible 8 o) ; trois encodages : **texte long** Crockford en groupes de 4 + 1 caractère de contrôle par groupe (`VoucherCode`, faute de frappe localisée au groupe, O/0 et I/1 tolérés), **fichier** `.cbv1` (même texte), **contenu de QR** (même texte) ; `verify(voucher, ring, deviceCode, nowMs)` : signature, échéance (avec `TvClock` passé par l'appelant), cible (0 = toute TV, sinon SHA-256(code d'appareil)[0..8]), **MBOKO exige une cible** (bon MBOKO « toute TV » ⇒ `VOUCHER_BAD`).
5. `WalletCache` (pur, stockage injecté) : garde le dernier `cbw1` valide de **cette** identité (`seq` plus petit ⇒ ignoré ; altéré ⇒ ignoré) + liste des bons **en attente** (≤ 10, ≤ 20 000 NDEM, ≤ 200 MBOKO) + nonces vus (≤ 500) ; n'additionne **jamais** l'attente au solde (deux champs séparés).
6. `WalletReason` : motifs du § 7.6 avec textes français (mêmes clés que `WalletReason` Java de w22-01) ; `WalletView` : « 3 450 NDEM · 12 MBOKO », « au 04/10 18:42 », « +500 en attente », espaces fines insécables pour les milliers, montants en lettres pour les confirmations (réutiliser `FrenchNumbers` de `C/tokens/TokenPolicy.kt` par appel, sans le modifier ; au-delà de 10 000 : chiffres seuls).

## Critères d'acceptation (mutations au rapport)
- `WalletVectorsTest` : toutes les pièces dorées vérifiées, toutes les fausses refusées avec le bon motif (mutation : oublier le domaine dans la signature ⇒ échec).
- `WalletFormatsTest` : un `cbe1` dont `amt ≠ per × k` est refusé ; un `cbr1` dont Σ pay ≠ Σ used échoue à `check()` ; un `cbw1` d'une autre identité est refusé.
- `VoucherCodeTest` : aller-retour binaire ↔ texte ; une faute de frappe dans le groupe 7 est signalée « groupe 7 » ; MBOKO « toute TV » refusé ; bon expiré refusé ; 1 000 bons aléatoires : aucun faux positif de contrôle sur une substitution d'un caractère.
- `WalletCacheTest` : un instantané plus ancien n'écrase pas le plus récent ; l'attente n'entre jamais dans le solde affiché ; le même bon deux fois ⇒ une seule ligne en attente.
- Aucune classe de ce paquet n'expose d'opération qui **augmente** un solde à partir d'une entrée locale (test de source : pas de méthode `credit`/`add` publique sur le solde).

## À ne pas faire
- Ajouter une dépendance ; une confiance de clé non compilée ; un appel réseau.
