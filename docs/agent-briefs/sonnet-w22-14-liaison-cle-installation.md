# w22-14 — Liaison forte du compte à la clé d'installation de la TV (niveau 2) : opérations sortantes signées par la TV, une activation copiée ne vide plus le compte
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (cryptographie, rejeu, migration des comptes liés au niveau 1) · statut : **ATTEND la fin du niveau 1 et D-W22-7**
> **Groupe : W22-N2** · porte : `:core:test --tests 'castbridge.core.wallet.WalletOpSigner*'` + `cd backend && ./mvnw -q test -Dtest='castbridge.server.wallet.binding.**'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` (§ 3.2, § 5.2 S-3, R-E3). Branche `claude/w22-14-liaison-installation`. Rapport : `docs/agent-reports/sonnet-w22-14.md`.

## Constat
Au niveau 1, le compte est lié à l'appareil API d'ouverture (`deviceId`). Une activation `cbx1` copiée (audit w20-04 B1) ne vide pas le compte tant que le jeton d'appareil n'est pas copié aussi. La TV possède une **clé d'installation** protégée par le Keystore (W4-A ; lire `android/core/src/main/kotlin/castbridge/core/owner/InstallSigner.kt` et `castbridge/core/lots/InstallKey.kt` avant tout) : la lier au compte rend la copie inutile.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/wallet/WalletOpSigner.kt` (signe `{op, idem, corps SHA-256, ts}` avec la clé d'installation ; domaine `castbridge-wallet-op-v1`) + test ; `backend/src/main/java/castbridge/server/wallet/binding/{InstallBinding,OpSignatureFilter}.java` + migration additive (`wallet_identity.install_pub`) + tests.
- **Zone additive** : `R/wallet/WalletClient.kt` (en-tête `X-Wallet-Op`), routes de sortie de w22-05 (filtre).

## Spécification
À la première synchronisation après livraison, la TV présente sa clé publique d'installation **et** une preuve (signature d'un défi du serveur) ; le compte la retient (une fois ; changement = réaffectation d'administrateur de w22-10). Toute opération sortante exige `X-Wallet-Op` valide (ts ± 10 min, `idem` jamais réutilisé avec un autre corps). Comptes liés au niveau 1 sans clé : période de transition de 30 jours (avertissement), puis signature obligatoire.

## Critères d'acceptation
- Copie de l'activation **et** du jeton d'appareil sur une autre TV : lecture possible, toute sortie refusée (`BOUND_OTHER_TV`) ; rejeu d'une signature ⇒ refus ; corps modifié ⇒ refus.
