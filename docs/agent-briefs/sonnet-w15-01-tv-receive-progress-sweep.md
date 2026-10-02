# w15-01 — TV : progression de réception visible pour le transfert rapide, balayage des sessions, fin du 429 définitif
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (zone `ReceiverServer`/`TransferHost`, verrous de nom, éviction de sessions) · statut : PRÊT (après confirmation ou infirmation de `diag-receiver-progress`)
> **Groupe : W15-S0-a** (vague W15, tranche S0, risqué n° 1) · prérequis : aucun cahier ; lire `docs/coordination/PLAN-STABILISATION-MULTIMEDIA-SYNC-2026-10-02.md` § 2.4 T-01, T-02, T-06 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*MultipathServer*' --tests '*UxTest*' --tests '*Multipath*' --tests '*TvHardening*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S0 (cœur TV + écran TV) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-01` depuis `origin/integration/agents`. Rapport : `docs/agent-reports/sonnet-w15-01.md`. Règle : **test rouge d'abord** (chaque étape cite la sortie rouge puis verte).

## Objectif
Pendant une copie LAN par `/api/transfer/*`, la TV **montre** la réception (nom, pourcentage, « vérification… » pendant la relecture du `finish`) sur l'accueil et dans les autres écrans ; les sessions abandonnées sont balayées en marche et un `begin` n'est **jamais** refusé 429 pour toujours.

## Pourquoi (preuves, lues le 2026-10-02)
- Les blocs vont dans `<vol>/.cbx/<id>.data` (`C/xfer/PartAssembler.kt:33-35,224-229`) ; aucun `.part`/`Meta` avant `finish` ; `FileStore.list()` écarte les noms en « . » (`C/tv/Volumes.kt:298`) ; `listing()` ne construit `arriving` que des `.part` à `Meta` (`C/tv/ReceiverServer.kt:914-921`) ⇒ `receiving()` (`:941`) vide.
- `R/PlayerActivity.kt:436-440` `status()` retombe sur `statuses["1-bt"]` (Bluetooth seul) ; `R/HomeScreen.kt:108,123-127` relit toutes les 4 s, accueil visible seulement ; `R/TvService.kt:611-614` `statusesChanged` ne rafraîchit pas la puce ; pas d'icône « transfert » (`C/status/StatusIcons.kt:6-20`).
- `TransferHost.sweep` n'est appelé que par `cleanOrphans` (`ReceiverServer.kt:263`) ; `maxSessions = 3` (`C/xfer/TransferHost.kt:21,50`) ; aucun `abort` côté téléphone ⇒ 429 « too many transfers in progress » (anglais) jusqu'au redémarrage de l'app TV.
- Vieux `.part` orphelin affiché « 37 % » figé (`PlayerActivity.kt:438`, T-11) ; `refreshStatus()` sur le fil principal (`HomeScreen.kt:124,130`, T-10).
- Tests existants : `CT/MultipathServerTest.kt` (18), `CT/UxTest.kt:22-37` (`receiving()` classique seulement), `CT/MultipathTransferTest.kt`.

## Fichiers possédés
`C/xfer/TransferHost.kt`, `C/tv/ReceiverServer.kt` (**zones** : `listing`/`receiving`/`activeTransfers` `:901-957,114` et la route `begin` `:666-720` ; rien d'autre), `C/tv/TvInfo.kt` (type additif `Receiving`), `R/TvService.kt` (zone `statusesChanged`/`transferTick` `:569-614`), `R/HomeScreen.kt` (zone `refreshStatus` `:108-130`), `R/PlayerActivity.kt` (zone `status()` `:436-440`), `CT/MultipathServerTest.kt`, `CT/UxTest.kt`. **Hors zone** : `PartAssembler.kt` (format `.cbx` gelé), `/upload` classique, `R/BtServer.kt`, `S/**`, `C/trust/**`.

## Signatures (additives)
```kotlin
// C/xfer/TransferHost.kt
data class Progress(val name: String, val received: Long, val size: Long, val finishing: Boolean, val lastActivityMs: Long)
fun progress(): List<Progress>              // received = Σ manifest.length(i) des blocs cochés ; finishing = relecture en cours
fun sweep(maxIdleMs: Long = 30 * 60_000L)   // existant : rendre publique et appelée périodiquement
fun active(): Int                            // sessions ni finies ni périmées
// C/tv/ReceiverServer.kt
fun receiving(): List<Receiving>            // Receiving(name, received, total, phase: ARRIVING|VERIFYING, freshMs) : fusion .part+Meta (existant) et TransferHost.progress()
```

## Étapes
1. **Rouge** : `MultipathServerTest.receivingShowsAFastTransferInFlight` : `begin` (5 blocs de 1 Mio) + 2 blocs ⇒ `server.receiving()` contient `(nom, 2 Mio, 5 Mio, ARRIVING)` ; pendant `finish` d'un gros fichier (bloquer la relecture par un `slowRead` injecté) ⇒ phase `VERIFYING`. `MultipathServerTest.fourthBeginIsAcceptedAfterSweep` : 3 `begin` sans suite, `now += 31 min` ⇒ 4ᵉ `begin` 200 ; variante : 3 `begin` récents ⇒ le 4ᵉ **évince** la plus ancienne non `finishing` au lieu de 429 (décision D-W15-01a : éviction ; 429 reste pour `finishing`). `UxTest.staleOrphanPartIsNotShownAsReceiving` : `.part` + `Meta` de `mtime` − 10 min ⇒ absent de `receiving()`.
2. `TransferHost.progress()/active()/sweep` ; `begin` : éviction puis balayage ; texte français dans `humanRefusal` (« trop d'envois en cours sur la TV, réessayez dans un instant »).
3. `ReceiverServer.receiving()` : fusion, filtre de fraîcheur (`lastActivityMs`/`mtime` < 30 s) ; `activeTransfers()` inclut `transfers.active()` (sert au wake lock, T-09, sans changer `TvService` au-delà de la zone).
4. `TvService` : `transferTick` passe à 1 s quand `receiving()` non vide, 5 s sinon ; appelle `transfers.sweep()` toutes les 5 min ; `statusesChanged` ⇒ `home.refreshStatus()` (push).
5. `HomeScreen.refreshStatus()` : calcul sur l'exécuteur `io`, affichage sur le fil principal ; texte : « Réception de <nom> · NN % » / « Vérification de <nom>… » ; `PlayerActivity.status()` : priorité à `receiving()` puis `1-bt`.
6. Vert : porte ; `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; les 3 nouveaux tests existent et sont cités rouges puis verts ; `grep -n "too many transfers" C/xfer/TransferHost.kt` ne sert plus de texte utilisateur ; aucune modification de `PartAssembler.kt` ni de `/upload` (`git diff --stat` le prouve) ; `:receiver:compileDebugKotlin` OK.

## Cas limites
Deux sessions du même nom (impossible : `hasName`) ; session `finishing` évincée (interdit) ; `.cbx` sur volume retiré (`progress()` l'ignore) ; TV sans `TransferHost` (ancienne build : `receiving()` inchangé).

## À ne pas faire
Pas de nouveau format sur disque ; pas de modification du protocole `/api/transfer` (champs additifs seulement dans `state`) ; pas de texte hors `receiving`/`humanRefusal` ; ne pas toucher au Bluetooth (`1-bt`) ni à `S/**` ; ne pas « corriger » W13 (les `Blocker` viendront après).

## Rapport
`STATUT`, sorties rouge/vert par test, lignes avant/après dans `ReceiverServer`, question pour l'audit : l'éviction dans `begin` est-elle sûre face à un téléphone qui reprend la session évincée (attendu : 404 `unknown transfer` ⇒ nouveau `begin`, reprise par `.cbx` conservé ?). Si `diag-receiver-progress` conclut autrement sur R-04, le dire et s'arrêter (`STATUT: BLOQUÉ`).
