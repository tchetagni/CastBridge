# w12-09 — CastBridge-TV : branchement du moteur de politiques (`PolicyHub.init`, trames Bluetooth 16-21 du canal propriétaire) et aiguillage par type d'enveloppe (`order` → `PolicyEngine`, `settings` → `SettingsHub`) ; « À propos > Politiques et réglages appliqués »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (canal Bluetooth, anneau de clés, moteur de politiques) · statut : PRÊT
> **Groupe : W12-c** (vague W12) · prérequis : w12-07 fusionné (`SettingsHub`) ; `R/OwnerBtHost.kt`, `C/owner/OwnerChannel.kt`, `R/PolicyHub.kt` **après** w2-01, w4-15, w6-12 s'ils sont lancés ; `R/PlayerActivity.kt` après w11-11 (page « À propos ») · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Policy*' --tests '*OwnerChannel*' --tests 'castbridge.core.settings.*' && cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : **oui**

**Vague 12c (TV, Bluetooth) · Effort L (≈ 2,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.7, § 5.6 ; `docs/ORDRES.md` § 8, § 9, § 13 (points 2-4). Branche `claude/sonnet-w12-09`. Rapport : `docs/agent-reports/sonnet-w12-09.md`.

## Objectif
Faire **arriver à la TV hors ligne par Bluetooth** les ordres et les réglages que le téléphone transporte déjà : (1) `OwnerChannelServer` accepte les trames **16 à 21** (`OrderFrames`) et les délègue à `PolicyHub.onOwnerFrame` (aujourd'hui « Non pris en charge », `C/owner/OwnerChannel.kt:21-38`) ; (2) `PolicyHub.init(ctx, keyRing, deviceContext)` appelé au démarrage de la TV avec l'anneau et les facteurs de l'activation ; `observeClock()` au démarrage et chaque heure (ORDRES § 13.2) ; (3) **aiguillage par type** : un jeton `cbx1` reçu par les trames `ORDER_*` est décodé une fois ; `type=order` ⇒ `PolicyEngine.receive`, `type=settings` ⇒ `SettingsHub.receive` (l'accusé `SettingsAck.toText()` voyage dans la même trame `ORDER_ACK`, champ `reason`) ; `ORDER_HELLO/STATE` portent en plus `settingsSeq=<kid>:<seq>` pour que le téléphone sache quoi pousser ; (4) `PolicyGate.effective` branché là où la TV calcule son `GateState` (ORDRES § 13.3 ; `R/ActivationCenter.kt:44,92` : **une** composition, comportement inchangé tant qu'aucun ordre n'est reçu) ; (5) écran **« À propos > Politiques et réglages appliqués »** (lecture seule : `PolicyEngine.journal()` + `SettingsEngine.journal()`), accessible depuis le menu existant (`R/PlayerActivity.kt:181,737`) ; (6) **téléphone** : `OrderCourier` envoie aussi les jetons `settings` dans la file (seq propre) — **si** `S/OrdersRuntime.kt` doit changer, le faire **par une interface additive** et le noter (fichier non possédé ici : demander au coordinateur ou faire une seconde passe w12-09b).

## Pourquoi (preuves)
- `R/PolicyHub.kt:11-17` : « NOT COMPILED… wiring left to the coordinator » ; `:19-34` (`init`, `onOwnerFrame`) ; **aucun appelant**. `R/OwnerBtHost.kt:29-42` (hôte du service `…0005`) ; `OwnerFrames.kt:13-24` (types 1-8) ; `OrderFrames.kt:7-40` (16-21) ; `OrderCourier.kt`, `OrderQueue.kt` (téléphone, branché : `S/PhoneConnect.kt:44`).
- `PolicyEngine.receive` (`C/policy/PolicyEngine.kt:71-83`) attend un jeton ; `OrderVerifier` refuse `UNKNOWN_TYPE` pour tout type autre qu'`order` (`C/owner/Order.kt:54-77`) : l'aiguillage doit se faire **avant**, sur `Envelope.decode(token).type`, sans toucher aux vérificateurs.
- Anneau et facteurs : `R/ActivationCenter.kt:49` (`TRUSTED_KEYS`), `:44,90,261` (grâce, gate) ; `DeviceContext` (`C/owner/*`).
- ORDRES § 12 : les accusés ne sont pas signés (informatifs) ; § 2 : rien n'est détruit, un ordre ne verrouille que si toutes les licences sont suspendues (`PolicyGate.kt:12-13`).

## Fichiers possédés
- Existants (zones précises) : `C/owner/OwnerChannel.kt` (trames 16-21 → délégué), `R/OwnerBtHost.kt` (passage du délégué), `R/PolicyHub.kt` (init, aiguillage, `settingsSeq`), `C/policy/OrderFrames.kt` (champ additif `settingsSeq` dans HELLO/STATE, rétrocompatible : absent = 0), `C/policy/OrderCourier.kt` (envoi des jetons `settings` **si** possible sans toucher `S/OrdersRuntime.kt`), `R/ActivationCenter.kt` (**une** composition `PolicyGate.effective`), `R/TvApp.kt` ou le point d'init de la TV (**une** ligne `PolicyHub.init`, une `observeClock` horaire), `R/PlayerActivity.kt` (**une** entrée de menu), nouveau `R/PoliciesAboutActivity.kt`, `CT/policy/{FrameRoutingTest,OrderFramesSettingsSeqTest}.kt`, `CT/owner/OwnerChannelOrdersTest.kt`, `docs/ORDRES.md` § 13 (cocher ce qui est fait).
- Hors zone : `C/policy/PolicyEngine.kt`, `PolicyActions.kt`, `PolicyState.kt`, `Order.kt`, `Envelope.kt` (**inchangés**), `R/SettingsHub.kt` (w12-07 : appeler `receive`/`seq`), `S/OrdersRuntime.kt` (voir point 6).

## Étapes
1. `OwnerChannel` : délégué `OrderFrameSink?` ; types 16-21 ; un ancien téléphone n'envoie jamais ces types (rétrocompatible) ; test : trame 18 sans délégué ⇒ « Non pris en charge » inchangé.
2. `PolicyHub.init` + horloge ; `onOwnerFrame` : HELLO/STATE avec `settingsSeq` ; BEGIN/CHUNK/NEED inchangés ; jeton complet ⇒ aiguillage par type ; ACK.
3. `FrameRoutingTest` : `order` ⇒ journal du `PolicyEngine` ; `settings` ⇒ journal du `SettingsEngine` ; type inconnu ⇒ `UNKNOWN_TYPE` ; activation présentée en trame 18 ⇒ refusée (test ORDRES § 10 « activation présentée comme un ordre »).
4. `PolicyGate.effective` dans `ActivationCenter` : test de non-régression (aucun ordre ⇒ même `GateState`).
5. Écran « À propos » (D-pad, textes ≥ 28 px, lecture seule, Retour ferme).
6. Point 6 (téléphone) : évaluer ; au minimum **documenter** l'interface attendue par w12-06 (`SettingsRelay` peut déjà pousser par Wi-Fi ; le Bluetooth est un second chemin).

## Critères d'acceptation (hors ligne)
- Porte verte ; compilation `receiver` ; tests ORDRES § 10 toujours verts ; aucun nouveau service Bluetooth (UUID inchangé `…0005`/`…0004` selon ce que le téléphone utilise : **vérifier** `OrderCourier` et noter, ORDRES § 9 dit `…0004`, `OwnerBtHost` sert `…0005` : si les deux diffèrent, c'est un **BLOQUÉ** à remonter, pas à résoudre seul).
- Rapport : ce que l'audit Opus doit relire (délégué de trames, aiguillage, composition du gate), BLOQUÉ éventuel sur l'UUID.
