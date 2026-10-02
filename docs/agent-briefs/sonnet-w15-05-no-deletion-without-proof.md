# w15-05 — Aucune suppression sans preuve : « Déplacer », reprise classique homonyme, `Mover` entre volumes
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (perte de données ; routes `/api/part` et `/upload` ; suppression d'original) · statut : PRÊT
> **Groupe : W15-S0-b** (vague W15, tranche S0 ; après fusion de w15-01 et w15-04 : `ReceiverServer` et `UploadService` un seul cahier à la fois) · prérequis : w15-01, w15-04 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*InFlight*' --tests '*MultiVolume*' --tests '*TvHardening*' --tests '*Receiver*' --tests 'castbridge.core.tv.MoveRequestsTest'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : oui

**Vague 15 S0 (cœur + téléphone) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-05`. Rapport : `docs/agent-reports/sonnet-w15-05.md`. Règle : test rouge d'abord.

## Objectif
Un original (téléphone) ou une source (volume TV) n'est **jamais supprimé** sans preuve que la copie est **le même contenu, complet** : la reprise classique vérifie la taille, le dédoublonnage ne dit « déjà là » qu'à taille égale, « Déplacer » exige une preuve, `Mover` revérifie le `.part` à la reprise et hache avant de supprimer.

## Pourquoi (preuves)
- X-03 (a) : `C/tv/TvClient.kt:13` `part(name)` sans taille ; `C/tv/ReceiverServer.kt:880` ⇒ `findFinalOrOrigin(name, null)` (`:141-147`) renvoie `done:true` pour un fichier **fini** homonyme de taille différente ⇒ `ResumableUpload` (`TvClient.kt:269`) `Done` sans octet ⇒ autoPlay lance l'ancien.
- X-03 (b) : `ReceiverServer.kt:578` n'accepte que `offset == cur` sans comparer `total` au `Meta` ⇒ ajout à la suite d'un autre contenu ; `S/UploadService.kt:222` `checkMoved` ⇒ `complete && size == local` ⇒ **original supprimé** (`S/MoveToTv.kt:25-71`).
- X-07 : `UploadService.kt:223` `_moveReady` à une valeur ; `S/TransferQueue.kt:89,91` (« déjà là » + move ne supprime pas ; BT ignore `move`).
- B-02 : `C/tv/Mover.kt:89` reprise à `have = partSize` ; `:93-105` pas de fsync périodique ; `:114` `sameEdges` 1 Mo de tête/queue ; `:123` `deleteFinal` de la source ; `UsbMigration.kt:105` fait un SHA complet (modèle).
- Tests existants : `CT/InFlightTest.kt`, `CT/TvHardeningTest.kt` (« la reprise ne supprime jamais ce qu'elle n'a pas vérifié », chemin TV), `CT/MultiVolumeTest.kt:430-535` (Mover), `CT/tv/TvDedupeTest.kt`.

## Fichiers possédés
`C/tv/TvClient.kt` (`part(name, size)` additif), `C/tv/ReceiverServer.kt` (**zones** `partJson` `:875-890`, `uploadCounted` `:560-600`, `findFinalOrOrigin` `:141-147`), `C/tv/Mover.kt`, `C/tv/TvInfo.kt` (`TvDedupe` : taille obligatoire), `S/MoveToTv.kt`, `S/UploadService.kt` (**zone** `checkMoved`/`_moveReady` `:216-232`), `S/TransferQueue.kt` (zone `move` `:85-95`), nouveaux `C/tv/MoveRequests.kt` (file persistante pure) + `CT/tv/MoveRequestsTest.kt`, `CT/InFlightTest.kt`, `CT/MultiVolumeTest.kt`, `CT/ReceiverTest.kt`. **Hors zone** : format `.cbx`, `UsbMigration`, `TransferHost`, écrans.

## Étapes
1. **Rouge** : `InFlightTest.aFinishedHomonymOfAnotherSizeIsNotDone` (fichier final 10 Mo, envoi d'un 12 Mo de même nom ⇒ pas `Done` avant l'envoi ; attendu après correctif : reprise à 0 sous un nom dédoublonné « (2) » **ou** 409 explicite, décision D-W15-05a : **409 `name taken, different size`** que le téléphone traduit en « un autre fichier du même nom est déjà sur la TV ») ; `InFlightTest.aPartOfAnotherContentIsNeverResumed` (`.part` + `Meta.total` = 10 Mo, envoi de 12 Mo ⇒ 409, jamais d'ajout) ; `InFlightTest.moveDeletesOnlyAfterProof` (faux `MoveToTv` : `complete && size` sans octets envoyés ⇒ pas de suppression) ; `MultiVolumeTest.resumedPartWithCorruptMiddleIsNeverCommitted` (`.part` de bonne taille, 1 Mo au milieu altéré ⇒ source conservée, `.part` tronqué puis recopié) ; `MoveRequestsTest` (N demandes, traitement un par un, survie à un redémarrage simulé).
2. TV : `/api/part?size=` ⇒ `partJson` compare au `Meta.total` et à la taille du final ; `findFinalOrOrigin(name, size)` ; `/upload` refuse 409 si `Meta.total ≠ total` ; corps avec `"code":"NAME_TAKEN"` (compatible W13 w13-05 qui ajoutera les autres codes).
3. Téléphone : `part(name, size)` ; `TvDedupe.alreadyThere` exige la taille ; **preuve de déplacement** = (octets réellement envoyés par ce travail == taille) **ou** (`Result.Done` du transfert rapide avec racine SHA) ; `MoveRequests` persistée dans `filesDir` (JSON une ligne par demande), traitée par `MoveHandler` une à une, y compris pour Bluetooth et « déjà là » (dans ce dernier cas : suppression seulement si taille **et** empreinte des 64 Ko de tête/queue identiques via `/stream` Range, sinon message « déjà sur la TV : original conservé »).
4. `Mover` : à la reprise, relire les 8 derniers Mo du `.part` et les comparer à la source, tronquer au dernier bloc identique ; `fsync` tous les 64 Mo avec `Meta.safeBytes` ; avant `deleteFinal` d'une source ≥ 100 Mo : SHA-256 complet annulable (progression dans `Mover.state`), sinon `sameEdges` + taille.
5. Vert : porte ; `:sender:compileDebugKotlin`, `:receiver:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; 5 tests cités rouges puis verts ; `grep -n "deleteFinal" C/tv/Mover.kt` précédé d'une vérification nommée `proven` ; `grep -n "_moveReady" S/UploadService.kt` vide ; aucune ligne de `PartAssembler.kt` ni de `TransferHost.kt` modifiée.

## Cas limites
Ancienne TV sans paramètre `size` (répond comme avant ⇒ le téléphone considère « non prouvé » et **ne supprime pas**, message « mise à jour de la TV nécessaire pour Déplacer ») ; source modifiée pendant la copie (`TvHardeningTest` existant) ; `Mover` annulé pendant le SHA.

## À ne pas faire
Ne jamais supprimer « par défaut » ; pas de nouveau format de `Meta` autre qu'un champ additif ; ne pas toucher à `TransferHost`/`.cbx` ; ne pas changer `UsbMigration` ; pas de texte W13.

## Rapport
`STATUT`, sorties rouge/vert, tableau « situation ⇒ suppression oui/non » (8 lignes), question d'audit : le 409 `NAME_TAKEN` peut-il casser une reprise légitime après renommage côté TV (`origin`) ? (attendu : `findFinalOrOrigin` par `origin` + taille).
