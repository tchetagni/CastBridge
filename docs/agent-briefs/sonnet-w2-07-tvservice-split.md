# w2-07 — Découper `TvService` sans changer le comportement

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w1-04)
> **Groupe : W2-A** (vague W2) · prérequis : w1-04 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 2 · Effort M (≈ 2 j) · Statut PRÊT** (après fusion de w1-04 qui touche la zone `netTick`). Branche `claude/sonnet-w2-07`. Rapport : `docs/agent-reports/sonnet-w2-07.md`.

## Objectif
`R/TvService.kt` (1 007 lignes, ≈ 75 fonctions, 36 champs mutables, 9 responsabilités) devient une racine de composition ≤ 400 lignes ; quatre classes extraites, **déplacement pur** (mêmes noms d'appel via délégués d'une ligne, aucun changement de comportement).

## Pourquoi (preuves)
- `R/TvService.kt:71-1000` mélange : service d'avant-plan (`:137-178`), bootstrap activation (`:171-275`), Bluetooth hello/trust (`:306-335`), icônes de statut (`:361-403`), sonde réseau avec machine d'états propre (`:421-547`, 7 `@Volatile`), lancement d'écrans (`:556-660`), stockage/USB (`:665-760`), **second routeur HTTP** `extraApi/serverApi` (`:772-866`, ≈ 40 routes), `Device` (`:875-925`).
- `:94` `val main` public, `:96` `bg` ; `companion @Volatile running :983` ; 19 handles `lateinit`/nullable (`:98-133`).
- Audit : AR-1 ; prépare w3-01 (table de routes) et w3-02 (démarrage à froid).

## Fichiers possédés
`R/TvService.kt`, nouveaux `R/TvNetMonitor.kt`, `R/TvExtraRoutes.kt`, `R/ScreenLauncher.kt`, `R/StorageEvents.kt`, `R/IconSync.kt`. **Hors zone** : tout autre fichier (en particulier `LearnHub` w2-05, `ActivationCenter` w2-01, `RentalHub`, `ReceiverServer`). Si un appelant externe utilise une fonction déplacée (`grep -rn 'TvService\.' android/receiver`), garder un **délégué** dans `TvService` plutôt que de modifier l'appelant.

## Étapes
1. Cartographier : pour chaque fonction de `TvService`, noter la classe cible et les champs qu'elle lit/écrit (tableau dans le rapport).
2. `TvNetMonitor(service)` : champs `:421-429,465-466`, fonctions `watchNetwork/unwatchNetwork/netChanged/gatewayStatus/connectivityEvent/checkNetNow/netSummary` (`:469-547`) ; `TvService` garde `fun netSummary() = net.netSummary()` etc.
3. `TvExtraRoutes(service) : ApiExtension` : `extraApi`, `serverApi`, `usbJson`, `backgroundJson` (`:768-873`) ; l'enchaînement `ApiExtension.then` reste identique (même ordre).
4. `ScreenLauncher(service)` : `launchState/launchScreen/notifyLaunch/playerIntent/onMainWait/onScreen` (`:584-663`) ; `pendingDone`/`takePending` restent où ils sont si d'autres fichiers les appellent.
5. `StorageEvents(service)` : `registerStorageEvents/readopt/updateStorageStatus/cleanUpdateFiles` (`:517-523,665-735`).
6. `IconSync(service)` : `iconsChanged/syncIcons/syncRoom/syncIconsAsync`, `roomRefs`, `iconBg` (`:361-403`).
7. Aucun changement de visibilité des champs partagés (`statuses`, `running`, `main`, `bg`) ; chaque classe reçoit `TvService` par constructeur.
8. Vérifier : `git diff --stat` montre que les lignes ajoutées ≈ lignes retirées ; aucune chaîne utilisateur modifiée (`git diff | grep '^[-+].*"' | grep -v import` relu à la main).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin      # compile (si SDK ; sinon vérifier à la main les imports et le dire)
wc -l android/receiver/src/main/kotlin/castbridge/receiver/TvService.kt   # ≤ 400
cd android && gradle --offline :core:test --tests 'castbridge.core.TvHardeningTest' --tests 'castbridge.core.ReceiverServer*'   # vert (le cœur ne change pas)
grep -c 'fun ' android/receiver/src/main/kotlin/castbridge/receiver/TvNetMonitor.kt android/receiver/src/main/kotlin/castbridge/receiver/TvExtraRoutes.kt   # > 0
```
Observable (campagne) : aucun changement attendu ; `GET /api/connections`, `/api/net`, `/api/storage`, lancement des écrans, démarrage au boot identiques.

## Cas limites
- Les callbacks réseau (`ConnectivityManager.NetworkCallback`) sont enregistrés sur le `main` handler : garder le même thread.
- `BootReceiver` (`:1002`) et `companion` restent dans `TvService`.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; **aucun** changement de comportement ni de chaîne ; pas de renommage de route ; ne pas toucher aux autres fichiers.

## Rapport
`STATUT`, tableau fonction → classe, lignes avant/après, résultat de compilation.
