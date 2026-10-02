# w15-10 — Durcissement du moteur de transfert (téléphone et TV) : issues de l'ordonnanceur, 429/503, `finish` répété, exclusion classique/rapide, préallocation FAT, séries hors Compose
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (verrous de nom, `finish`, exclusion) · statut : PRÊT
> **Groupe : W15-S2-a** (vague W15, tranche S2, risqué) · prérequis : S0 et W13 w13-04 fusionnés (ce cahier **conserve** les `Blocker` additifs) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Multipath*' --tests '*InFlight*' --tests '*Transfer*' --tests '*Receiver*' --tests '*TvHardening*'`
> **Jauge : ≈ 500 k jetons entrée / 25 k sortie** (effort M-L, ≈ 2,5 j) · audit Opus : oui

**Vague 15 S2 (cœur + téléphone + TV) · Effort M-L · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-10`. Rapport : `docs/agent-reports/sonnet-w15-10.md`. Règle : test rouge d'abord pour chaque défaut.

## Défauts traités (preuves dans `PLAN-STABILISATION` § 2.2 et § 2.4)
X-06 (`C/xfer/Scheduler.kt:64,89,145-153` : aucune issue quand toutes les voies échouent non fatalement) · X-09 (`S/TransferQueue.kt:68-76` `pumping` non atomique) · X-10 (notification orpheline, doublon 2 + 9) · X-12 (`TransferClient.kt:41,48,127` : 429/503 sur `caps`/`begin`, `Retry-After` ignoré) · X-17 (`TransferClient.kt:122,162` récursion) · X-22 (`C/xfer/HttpConn.kt:17,27,82-93` : `dead` non volatile, seuil 20 s sur disque lent) · X-08 (`job.pin` figé `UploadService.kt:141,170,219`) · X-11 (`play()` concurrents `:139`) · X-13 (séries dans Compose `S/TvTransferScreen.kt:98-104`, `S/TvHub.kt:232-258`) · T-03 (`ReceiverServer.kt:573-578` : `/upload` et `/api/transfer` du même nom non exclus ; `PartAssembler.kt:165-166` `delete` puis rename) · T-04 (`ReceiverServer.kt:747,794` : `finish` répété ⇒ 500) · T-05 (`PartAssembler.kt:229` `setLength` sur vfat ; EFBIG ⇒ 507 au lieu de 413) · T-16 (`Volumes.kt:248-253` commit sans fsync, non atomique) · T-17 (`/api/storage/check` ignore `.cbx`) · T-18 (`/api/delete` et session rapide).

## Fichiers possédés
`C/xfer/{Scheduler,TransferClient,HttpConn,PartAssembler,TransferHost}.kt`, `C/tv/ReceiverServer.kt` (**zones** `uploadCounted`, `finish`, `storage/check`, `delete`), `C/tv/Volumes.kt` (zone `commit`), `S/TransferQueue.kt`, `S/TransferQueueService.kt`, `S/UploadService.kt` (zones `play`, `checkMoved` crédential), `S/TvTransferScreen.kt`, `S/TvHub.kt` (zone série `:224-258`), tests `CT/MultipathTransferTest.kt`, `CT/MultipathServerTest.kt`, `CT/TransferQueueTest.kt`, `CT/TvHardeningTest.kt`. **Hors zone** : `C/trust/**`, `S/TvLink.kt`, `R/**`, `Mover`, formats.

## Étapes (chacune : test rouge, correctif, vert)
1. Scheduler : compteur de mises à l'écart consécutives sans bloc confirmé (10) ⇒ `Failed` ; `IOException` du `BlockSource` fatale (test « source qui échoue toujours termine en `Failed` »).
2. `TransferClient` : boucle `while` au lieu de `waitThen { run() }` ; `caps`/`begin` 429/503 ⇒ attente `Retry-After`/`retryMs` (≤ 6) puis repli/échec nommé ; `Blocker` conservés.
3. `HttpConn` : `@Volatile dead` ; seuil = `max(20 s, 8 × 1 Mio / débit mesuré)` ; thread de surveillance recréé s'il meurt.
4. TV : `uploadCounted` ⇒ 409 si `transfers.hasName(name)` ; `finish` : revérifier la session après le verrou, répondre `done` si le final existe à la bonne taille, jamais 500 ; `finish` long ⇒ 202 + `state.finishing` (le téléphone sonde `state` ≤ 10 min) ; pas de `setLength` sur un volume dont `fs ∉ {ext4, f2fs}` (écriture creuse par position) ; EFBIG ⇒ 413 ; `commit` interne : `force(true)` + rename direct ; `storage/check` compte les sessions `.cbx` ; `delete`/`reset` invalident la session (`transfers.dropName`).
5. Téléphone : `pumping` en `AtomicBoolean.compareAndSet` + relecture ; `credential()` partout (X-08) ; `play()` « un en vol » ; notification : une seule pendant la file (la file masque celle de l'envoi, `setGroup` + `setGroupSummary`) ; séries de `TvTransferScreen`/`TvHub` déplacées dans `TransferQueue.enqueueAll(items, destination, thenInstall)` (la boîte peut se fermer), comparaison par `AgentAuto.originalOf` ; texte « une seule connexion » (`TvTransferScreen.kt:195`) remplacé.
6. Vert : porte ; `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 10 tests nouveaux cités rouges puis verts ; `grep -n "waitThen" C/xfer/TransferClient.kt` vide ; `grep -n "setLength" C/xfer/PartAssembler.kt` gardé par un test de système de fichiers ; `MultipathServerTest` existant inchangé et vert.

## À ne pas faire
Pas de changement de format `.cbx` (champ `fs` dans `state` toléré) ; ne pas retirer un `Blocker` W13 ; ne pas toucher `LinkDriver`/`TvLink` ; pas de persistance de la file (W7).

## Rapport
`STATUT`, table défaut ⇒ test ⇒ commit, mesures (durée `finish` 2 Go simulé), questions d'audit : écriture creuse sur exFAT (comportement attendu : taille logique sans remplissage) et ordre 409 vs 429 dans `begin`.
