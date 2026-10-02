# w15-11 — Durcissement de la liaison côté téléphone : `connect()` sous verrou et délai, appairage annulable, refus non comptés, homonymes, permissions sans boucle
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (`LinkDriver`, `PairFlow`, `PairingSession`) · statut : PRÊT
> **Groupe : W15-S2-a** (vague W15, tranche S2, risqué ; `LinkDriver` : un seul cahier à la fois, après w15-03) · prérequis : w15-03 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LinkDriver*' --tests '*PairFlow*' --tests '*Trust*' --tests 'castbridge.core.trust.PairingSessionTest' --tests '*Diagnostics*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S2 (cœur + téléphone + ownerlib) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-11`. Rapport : `docs/agent-reports/sonnet-w15-11.md`. Règle : test rouge d'abord.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.3)
L-04 (`S/TvLink.kt:71,259`, `OD/TvBluetooth.kt:119,125`, `C/trust/LinkDriver.kt:142,215`, `C/tv/BtProtocol.kt:271` : `connect()` sans `BtConnectLock` ni délai ; `step` tient le verrou pendant `connect` ; boucle sans `try/catch`) · L-10 côté téléphone (`HINT_OTHER_INSTALL` ⇒ bouton « Reconnecter » = `PairFlow` direct ; la TV est w15-17) · L-12 (`C/trust/PairingSession.kt:55,110`, `R/PairActivity.kt:161,167` : fermer l'écran = refus compté ; la partie cœur est ici, l'activité TV reste w15-17) · L-13 (`S/TvPairScreen.kt:163-166` « Annuler » n'arrête pas `PairFlow` ; `TvLink.kt:226-229` `prepareReassociate` asynchrone) · L-14 (`S/LinkAndroid.kt:133-135` `cancelDiscovery` dans le même `runCatching` que `createBond` ; `SecurityException` laisse la socket ouverte ; `BtSshGateway.kt:105` `RejectedExecutionException`) · L-21 (`C/trust/PhoneLink.kt:127-130`, `LinkDriver.kt:249-253` : `addressChanges` par nom ⇒ TV homonymes confondues ; `SavedTvs.save` `apply()`) · L-22 (sondes empilées `S/TvHome.kt:65-77`, `S/TvScreen.kt:88-91` : une seule sonde partagée, en attendant W13 `HealthProbe` : si w13-09 est fusionné, **ne rien faire** ici) · L-03 reliquat (`S/LinkAndroid.kt:91` : 5xx ⇒ `UNREACHABLE`, règle exposée par w15-03).

## Fichiers possédés
`C/trust/LinkDriver.kt`, `C/trust/PairFlow.kt`, `C/trust/PairingSession.kt`, `C/trust/PhoneLink.kt`, `S/TvLink.kt` (hors zone `PinKeys` de w15-02 : zones `AndroidBtTransport`, boucle, `pair`, `requestReassociate`), `S/LinkAndroid.kt`, `S/TvPairScreen.kt` (zone Annuler/Reconnecter), `S/BtSshGateway.kt` (zone `dial`/timers), `OD/TvBluetooth.kt`, tests `CT/LinkDriverTest.kt`, `CT/PairFlowTest.kt`, nouveau `CT/trust/PairingSessionTest.kt`, `CT/TrustTest.kt`. **Hors zone** : `TrustRegistry` (w15-03), `LinkMachine`, `C/tunnel/**` (w15-12), `R/**` (w15-17), `TvDiscovery` (w15-12), textes W13.

## Étapes (test rouge, correctif, vert)
1. `AndroidBtTransport.connect` : `BtConnectLock.of(address)` + garde 20 s (socket fermée dans `finally`, toute exception ⇒ `IOException` nommée) ; `TvBluetooth.with` idem ; `LinkDriver.step` : `connect` hors du moniteur (session locale puis `synchronized` pour appliquer) ; boucle `TvLink` sous `try/catch` + journal ; `BtProtocol.hello` lecture avec délai (test : transport factice qui compte les `connect` simultanés ⇒ max 1 ; `adopt` n'attend pas un `connect` bloqué 30 s).
2. `PairFlow` : drapeau `cancelled` consulté à chaque attente ; `prepareReassociate` synchrone dans le fil du flot (test : annulation pendant l'attente de la fenêtre ⇒ aucun `adopt`).
3. `PairingSession.close()` pendant `Asking` ⇒ `TIMEOUT` non compté (test `PairingSessionTest`).
4. `addressChanges` : exiger `installId` ou refuser la bascule entre homonymes ; `SavedTvs.save` par `commit()` sur adoption (test TrustTest homonymes).
5. `LinkAndroid` : `runCatching` séparés ; `SecurityException` ⇒ `PairOutcome.PermissionMissing(SCAN)` avec texte existant de `LinkText` ; `BtSshGateway` timers protégés.
6. Téléphone : `HINT_OTHER_INSTALL` ⇒ action « Reconnecter » (lance `pair()` sur la même adresse) ; sondes empilées (voir réserve W13).
7. Vert : porte ; `:sender:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 7 tests nouveaux rouges puis verts ; `grep -n "BtConnectLock" S/TvLink.kt OD/TvBluetooth.kt` non vide ; `grep -n "@Synchronized fun step" C/trust/LinkDriver.kt` : `connect` n'est plus dans sa portée (relecture d'audit) ; `LinkMachine.kt` sans diff.

## À ne pas faire
Pas de nouveau texte hors `LinkText` ; ne pas changer le format HELLO ; ne pas toucher `TrustRegistry`, `TvDiscovery`, `LinkPool` ; ne pas modifier `R/PairActivity` (w15-17).

## Rapport
`STATUT`, table défaut ⇒ test, délais choisis, questions d'audit (verrou par adresse et deux TV).
