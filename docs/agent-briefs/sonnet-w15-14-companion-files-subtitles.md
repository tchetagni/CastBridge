# w15-14 — Fichiers compagnons : les sous-titres suivent leur vidéo (rangement, déplacement, renommage, corbeille, suppression) ; encodage et recherche des sous-titres
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune · statut : PRÊT
> **Groupe : W15-S2-c** (vague W15, tranche S2 ; fichiers disjoints de w15-13/15/16) · prérequis : w15-05 et w15-06 fusionnés (`Mover`, `Filing`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PlayerFeatures*' --tests 'castbridge.core.tv.Filing*' --tests '*MultiVolume*' --tests '*TvAgentTest*' --tests 'castbridge.core.tv.CompanionsTest'`
> **Jauge : ≈ 300 k jetons entrée / 16 k sortie** (effort M, ≈ 1,5 j) · audit Opus : non

**Vague 15 S2 (cœur + TV + téléphone lecteur) · Effort M · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-14`. Rapport : `docs/agent-reports/sonnet-w15-14.md`. Règle : test rouge d'abord.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.5-2.6)
P-02 / B-06 (`C/tv/ReceiverServer.kt:1235-1281` `startMove`, `:428-451` `rename`, `:459-475` `delete`, `C/library/agent/TvTrash.kt:98-117`, `C/tv/Filing.classify` : le `.srt` ne suit pas ; collision « (2) » sépare vidéo et sous-titre) · P-01 (`R/PlayerActivity.kt:269-275`, `S/player/Media.kt:44-46` : `.srt` Windows-1252 illisible) · P-03 (`R/PlayerActivity.kt:886,912` : dossier nul en progressif/SAF) · P-03b (`C/tv/PlayerFeatures.kt:171-182` : `.txt` proposé, `.sub` sans `.idx`).

## Fichiers possédés
Nouveaux `C/tv/Companions.kt` (pur : `companionsOf(name, siblings)` = sous-titres et `.idx/.sub` du même nom de base, variantes de langue ; `renamePlan(oldName, newName, siblings)`) + `CT/tv/CompanionsTest.kt` ; nouveau `C/tv/SubtitleCharset.kt` (`detect(bytes)`: UTF-8 strict ⇒ UTF-8, BOM, sinon Windows-1252 ; `toUtf8(bytes)`) + `CT/tv/SubtitleCharsetTest.kt` ; `C/tv/PlayerFeatures.kt` (`SubtitleFinder.find` ⇒ `isSubtitle`) ; `C/tv/Filing.kt` (zone `place`/`classify` : le compagnon prend le nom **final** de la vidéo après collision) ; `C/tv/ReceiverServer.kt` (**zones** `startMove`, `rename`, `delete`) ; `C/tv/Mover.kt` (liste de fichiers) ; `C/library/agent/TvTrash.kt` (`put`/`restore` avec compagnons) ; `R/PlayerExtras.kt` (recherche dans le dossier du volume pour `.part`, `DocumentFile` pour SAF ; charset) ; `R/PlayerActivity.kt` (**zone** options libVLC `:269-275` : `--subsdec-encoding` selon `SubtitleCharset`) ; `S/player/Media.kt` (zone sous-titres : `SubtitleConfiguration` avec charset converti en cache) ; tests `CT/PlayerFeaturesTest.kt`, `CT/tv/FilingServerTest.kt`, `CT/MultiVolumeTest.kt`, `CT/library/agent/TvAgentTest.kt`. **Hors zone** : `PlayerPolicy` (w15-13), `TransferHost`, écrans de bibliothèque.

## Étapes (test rouge, correctif, vert)
1. `Companions` + tests (`Film.srt`, `Film.fr.srt`, `Film.forced.srt`, `Film.idx`+`Film.sub`, pas `Film.txt`, pas `Film2.srt`, casse) ; `SubtitleCharset` + tests (« é » en CP1252, UTF-8 avec BOM, UTF-8 invalide).
2. `FilingServerTest.subtitleFollowsItsVideoEvenAfterCollision` (film + srt, un homonyme déjà rangé ⇒ `Titre (2).mkv` et `Titre (2).srt` dans le même dossier) ; `MultiVolumeTest.moveCarriesCompanions` ; `TvAgentTest.trashAndRestoreCarryCompanions` ; `ReceiverServer` : `rename` renomme les compagnons, `delete` les supprime (après w15-16, `delete` passera par la corbeille : ici seulement les compagnons).
3. `Filing.place` : compagnons nommés d'après le nom final ; fichier compagnon reçu **avant** la vidéo : rangé avec elle au rangement de la vidéo (reprise d'index par `adopt`).
4. Lecteur TV : `PlayerExtras.load` reçoit le dossier du volume pour `.part` et un `DocumentFile` pour SAF ; `--subsdec-encoding` ajouté seulement si `detect` ≠ UTF-8 ; lecteur téléphone : conversion en UTF-8 dans `cacheDir` (fichier ≤ 2 Mo) avant `SubtitleConfiguration`.
5. Vert : porte ; `:receiver:compileDebugKotlin`, `:sender:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 10 tests nouveaux rouges puis verts ; `find("Film.mkv", ["Film.txt"])` vide (test) ; `grep -n "subsdec-encoding" R/PlayerActivity.kt` présent ; aucune modification de `PlayerPolicy.kt`.

## À ne pas faire
Pas de téléchargement de sous-titres ; pas de conversion en place sur la TV (lecture avec option seulement) ; pas de texte utilisateur nouveau ; ne pas changer la règle « fichier en lecture reste à plat ».

## Rapport
`STATUT`, table défaut ⇒ test, liste des extensions considérées compagnons, à valider sur la vraie TV (accents d'un `.srt` CP1252).
