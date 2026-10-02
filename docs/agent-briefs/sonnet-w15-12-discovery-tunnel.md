# w15-12 — Découverte mDNS unique et tunnel Bluetooth : résolutions sérialisées, repli v1 seulement sur refus, mux sans tête de ligne, réveils non perdus
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus par échantillon · statut : PRÊT
> **Groupe : W15-S2-b** (vague W15, tranche S2 ; fichiers disjoints de w15-11) · prérequis : w15-03 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BtMuxTunnel*' --tests '*BtApiTunnel*' --tests 'castbridge.core.tunnel.*' --tests 'castbridge.core.net.ResolveQueueTest' --tests '*Connect*'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 15 S2 (cœur + téléphone + TV tunnel) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-12`. Rapport : `docs/agent-reports/sonnet-w15-12.md`. Règle : test rouge d'abord.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.3)
L-08 (`S/TvDiscovery.kt:36-96` : 15 instances, `FAILURE_ALREADY_ACTIVE`, `resolving` bloqué, IPv6 sans crochets, doublons « (2) », `onStartDiscoveryFailed` sans re-essai) · L-09 (`C/tunnel/LinkPool.kt:31,98-107,134-139` : repli v1 mémorisé 10 min sur panne passagère ; fermeture 45 s) · L-16 (`C/tunnel/Mux.kt:24,39,123,145` : tête de ligne, trame non bornée, réutilisation d'id) · L-18 (`R/TunnelHub.kt:106-114` réveil perdu) · L-19 (`C/connect/ServerLink.kt:141-178,250-312` HTTP sous verrou).

## Fichiers possédés
Nouveau `C/net/ResolveQueue.kt` (file pure de résolution : re-essai ×3 à 1 s, délai 10 s, dédoublonnage par adresse/port, filtre IPv4) + `CT/net/ResolveQueueTest.kt` ; `S/TvDiscovery.kt` (singleton applicatif `TvDiscovery.shared(ctx)` à compteur de références, API publique inchangée pour les 15 appelants : `TvDiscovery(ctx)` devient une façade sur le singleton, **sans éditer les appelants**) ; `C/tunnel/LinkPool.kt`, `C/tunnel/Mux.kt`, `C/tunnel/TunnelMachine.kt`, `R/TunnelHub.kt` (zone boucle), `C/connect/ServerLink.kt`, tests `CT/BtMuxTunnelTest.kt`, `CT/BtApiTunnelTest.kt`, `CT/tunnel/TunnelMachineTest.kt`, `CT/ConnectTest.kt`. **Hors zone** : `LinkDriver`, `TrustRegistry`, `R/TvService.kt` (annonce mDNS : rien à changer), écrans.

## Étapes (test rouge, correctif, vert)
1. `ResolveQueue` pur (tests : échec puis succès, résolution qui ne rappelle jamais ⇒ délai ⇒ suivant, doublon « (2) » même adresse ⇒ une entrée, IPv6 ignorée, `restart` vide la file).
2. `TvDiscovery` : singleton, `ResolveQueue`, re-essai de `startDiscovery` (3 ×, 2 s), `Tv.base` IPv4 ; `onServiceLost` par adresse.
3. `LinkPool` : v1 seulement sur `SDP absent`/`refus` explicite (exception nommée), pas sur délai ; `dialShared` rappelé à 60 s ; `wanted(session)` garde la liaison 5 min ; test « échec transitoire puis succès ⇒ v2 à la tentative suivante ».
4. `Mux` : `len ≤ MAX_PAYLOAD` sinon fermeture de la liaison avec motif ; flux saturé ⇒ fermeture **du flux** (RST) sans bloquer la lecture ; `lastRx` sur lecture réelle ; ids : ne jamais réattribuer un id ouvert ; test consommateur lent + flux rapide ⇒ PONG reçus.
5. `TunnelHub` : drapeau `woken` testé sous moniteur avant `wait` (test `TunnelMachineTest`) ; `ServerLink` : état copié sous verrou, réseau dehors (test : `setConsent` pendant un `contact()` lent < 50 ms).
6. Vert : porte ; `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 8 tests nouveaux rouges puis verts ; `grep -rn "TvDiscovery(" android/sender/src/main | wc -l` inchangé (appelants non édités) ; `grep -n "legacyRecheckMs" C/tunnel/LinkPool.kt` : le repli n'est plus déclenché par `IOException` générique.

## À ne pas faire
Pas de changement de protocole mux (trames identiques) ; pas de BLE ; ne pas toucher `LinkDriver` ni les écrans ; pas de texte utilisateur.

## Rapport
`STATUT`, table défaut ⇒ test, nombre d'instances `TvDiscovery` effectives après (1), questions.
