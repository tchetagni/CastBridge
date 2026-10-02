# w15-06 — Persistance : corbeille sous horloge suspecte, index `.filing` tolérant, registre de confiance avec secours, état des téléchargements atomique
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus par échantillon (formats de fichiers persistants) · statut : PRÊT
> **Groupe : W15-S0-b** (vague W15, tranche S0 ; fichiers disjoints de w15-05) · prérequis : w15-03 fusionné (`TrustFiles` écriture) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TvAgentTest*' --tests 'castbridge.core.tv.FilingTest' --tests '*HelloCompat*' --tests 'castbridge.core.dl.*'`
> **Jauge : ≈ 300 k jetons entrée / 16 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 15 S0 (cœur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-06`. Rapport : `docs/agent-reports/sonnet-w15-06.md`. Règle : test rouge d'abord.

## Objectif
Aucune donnée persistante ne disparaît à cause d'un **saut d'horloge**, d'une **lecture en échec** ou d'une **coupure entre deux renommages** : la corbeille ne purge jamais sous horloge suspecte, un `.filing` illisible est reconstruit au lieu d'être écrasé, un registre de confiance absent est repris depuis `.bak`, l'état des téléchargements est écrit atomiquement.

## Pourquoi (preuves)
- B-03 : `C/library/agent/TvTrash.kt:107` `id = now()-uuid`, `:168` regex `\d{10,14}` (horloge < 1e9 ms = 1970 ⇒ id invisible, irrestaurable, jamais purgé) ; `:76-78` `items(purgeExpired=true)` supprime tout élément plus vieux de 30 j à chaque `list`/`put`/`restore` ⇒ horloge corrigée après 1970 ⇒ **corbeille entière effacée**.
- B-05 : `C/tv/Filing.kt:231-242` `runCatching` global sur la lecture ⇒ ligne malformée = fin de lecture, `loaded=true` sans `adopt()` ; `:319-325` `save()` réécrit la mémoire ⇒ index écrasé au prochain `put`.
- L-20 : `C/trust/TrustFiles.kt:23-28` `file→bak` puis `tmp→file` ; `TrustRegistry.kt:135` `load() ?: return` sans `loadBackup()` ⇒ tous les téléphones oubliés, nouvel `installId`.
- B-10b : `C/dl/DownloadManager.kt:157-161` `writeText` + `renameTo` sans fsync, `:132-144` erreur avalée ⇒ tâches perdues, données cachées orphelines ; `C/tv/LibraryStore.kt:93-97` idem (`LibraryDb`). Modèle correct : `C/tv/UsbStore.kt:81` `AtomicFile`.
- Règle déjà posée : une date d'avant 2020 n'est pas fiable (`docs/STORAGE.md:177`).
- Tests existants : `CT/library/agent/TvAgentTest.kt:57-137`, `CT/tv/FilingTest.kt`, `CT/HelloCompatTest.kt:102`, `CT/dl/DownloadManagerTest.kt`.

## Fichiers possédés
`C/library/agent/TvTrash.kt`, `C/tv/Filing.kt` (classe `FiledIndex` : lecture/`save` seulement), `C/trust/TrustFiles.kt` (lecture), `C/trust/TrustRegistry.kt` (**zone** `load`/`restore` `:133-150` seulement), `C/dl/DownloadManager.kt` (zone `atomicWrite`/`load` `:130-165`), `C/tv/LibraryStore.kt` (zone `LibraryDb.save` `:90-100`), `CT/library/agent/TvAgentTest.kt`, `CT/tv/FilingTest.kt`, `CT/HelloCompatTest.kt`, `CT/dl/DownloadManagerTest.kt`. **Hors zone** : `TrustRegistry.issueToken`/`verifyToken` (w15-03), `ReceiverServer`, `Mover`, `R/**`, `S/**`.

## Étapes
1. **Rouge** : `TvAgentTest.clockJumpNeverPurgesTheTrash` (`now` = 1970 à `put`, puis 2026 à `items` ⇒ rien supprimé, élément listé et restaurable) ; `TvAgentTest.shortIdsAreStillListed` ; `FilingTest.aCorruptLineDoesNotEraseTheIndexOnNextPut` (`.filing` avec `%zz` au milieu, puis `put` ⇒ toutes les entrées précédentes visibles, `.filing.bak` écrit) ; `FilingTest.truncatedIndexIsRebuiltFromFolders` ; `HelloCompatTest.registryIsRestoredFromBackupWhenMainIsMissing` (dossier avec `.bak` seul ⇒ mêmes téléphones, même `installId`) ; `DownloadManagerTest.emptyStateFileDoesNotLoseTasks` (fichier de 0 octet + `.bak` ⇒ tâches restaurées ; dossiers `.cb-downloads/<id>` sans tâche signalés).
2. `TvTrash` : horodatage **de confiance** = `max(now, lastSeen)` persisté dans `.castbridge-trash/.clock` ; purge seulement si `now ≥ 2020-01-01` **et** `now - lastSeen ≤ 1 j` (sinon suspendue, journal) ; id = `<seq>-<uuid>` avec séquence monotone persistée, regex relâchée (`\d{1,14}`), lecture des anciens ids inchangée.
3. `FiledIndex.load` : `runCatching` **par ligne**, lignes illisibles ignorées et comptées ; si ≥ 1 ligne illisible ou fichier tronqué ⇒ `adopt()` fusionne depuis les dossiers et écrit `.filing.bak` avant `save()`.
4. `TrustFiles.load` : principal absent ou checksum faux ⇒ `loadBackup()` ; écriture : `tmp→file` direct (atomique) puis copie `.bak` après coup (plus de fenêtre sans principal).
5. `DownloadManager`/`LibraryDb` : `AtomicFile.write` (fsync + rename) et `.bak` relu en secours ; au démarrage, dossiers sans tâche ⇒ liste `orphans` exposée (pas de suppression).
6. Vert : porte + `:core:test` complet.

## Critères d'acceptation (hors ligne)
Porte verte ; 6 tests cités rouges puis verts ; `grep -n "writeText" C/dl/DownloadManager.kt C/tv/LibraryStore.kt` vide ; anciens fichiers (fixtures de `FilingTest`, `HelloCompatTest`, `TvAgentTest`) toujours lus ; aucun format changé de façon non rétro-lisible (une fixture par ancien format, jamais modifiée).

## Cas limites
Horloge qui recule après la première observation (purge suspendue, pas de suppression) ; `.filing` et `.filing.bak` tous deux corrompus (⇒ `adopt()` seul) ; `.bak` plus ancien que le principal corrompu (prendre `.bak`, journaliser).

## À ne pas faire
Pas de nouvelle dépendance ; ne pas toucher aux formats de `rentals.json`/`RentalLedger` (hors périmètre, audit A6-2) ; pas de suppression automatique des orphelins ; pas de texte utilisateur.

## Rapport
`STATUT`, sorties rouge/vert, formats touchés (avant/après, rétro-lisibilité prouvée par fixture), question : faut-il exposer `orphans` des téléchargements dans l'écran Téléchargements (hors zone) ?
