# w19-13 — Cœur : le protocole de reprise unique (S-REPRISE) : `TransferIdentity`, `SourceGuard`, relecture de la carte après toute coupure, `slice` figé par session, quatre états finaux `XferState`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (reprise = chemin de perte de données) · statut : PRÊT (après w19-01)
> **Groupe : W19-S0** (refus et reprise) · prérequis : w19-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.xfer.*' --tests 'castbridge.core.*ReceiverServer*' --tests 'castbridge.core.journey.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2,5 j) · audit Opus : obligatoire

**Vague 19 · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-REPRISE), § 1.4 (RS-04, 07, 09, 16, 18, 26, 29, 30), § 3.5 (tout), D-W19-12, D-W19-13, D-W19-14. Branche `claude/sonnet-w19-13`. Rapport : `docs/agent-reports/sonnet-w19-13.md`. Règle W15 R5 : aucun `S/`, `R/`. Règle R2 : seul cahier sur `C/tv/ReceiverServer.kt` et `C/xfer/TransferClient.kt` après w19-01.

## Objectif
Rendre vraie, et prouvée sur JVM, la phrase « la TV est autorité sur ce qu'elle a écrit et vérifié, le téléphone sur la source ; on interroge avant de renvoyer ; un partiel n'est jamais terminé ». (1) `C/xfer/TransferIdentity.kt` : `(manifestRoot, queueEpoch, tvId)` + `sessionId` éphémère ; la racine du manifeste est **écrite dans le sidecar** `.cbx.meta` (champ additif `root`) et `begin` retrouve un partiel par **nom + taille + racine** (un homonyme de même taille mais d'autre contenu ⇒ nouveau partiel, l'ancien gardé jusqu'à purge). (2) `C/xfer/SourceGuard.kt` : relevé au premier `begin` (taille, `lastModified`, SHA-256 des deux bords de 64 Kio, ou du fichier entier ≤ 64 Mio comme `MoveProof`) ; `check()` à chaque reprise ; différence ⇒ `abort` + `Result.Failed(SOURCE_CHANGED)` ; source absente ⇒ `SOURCE_GONE` (texte R-09 conservé). (3) **Relecture de la carte après toute coupure** : quand le `Scheduler` rend `Failed(non fatal)` ou qu'une voie meurt, `TransferClient` relit `state(id, hashes=1)` **avant** de renvoyer ; test : zéro bloc déjà confirmé renvoyé (hors `duplicateCandidate` voulu). (4) **`slice` figé par session** : `begin` répond `slice` ; un `caps()` ultérieur qui annonce un autre `slice` n'altère pas la session ; une TV qui répond une carte dans une autre granularité ⇒ le téléphone recommence les blocs non alignés et émet `onEvent("taille de bloc renégociée : n blocs à renvoyer")`. (5) `C/xfer/XferState.kt` : `sealed class XferState { DoneVerified ; Resumed(pct) ; Abandoned(reason) ; WaitingTv(sinceMs, boundMs) ; Paused(reason) }` + `XferTexts` : une phrase par état ; test de source : aucune phrase « Terminé » hors `DoneVerified`. (6) Purge des partiels (`maxAgeMs`, 7 j) **dite** : `begin` d'un fichier dont le partiel a été purgé répond `{"resumed":false,"purged":true}` (additif) et le téléphone affiche « Reprise à zéro : le partiel de la TV a expiré ».

## Pourquoi (preuves)
- `C/xfer/PartAssembler.kt:41-73` : map + hashes dans un sidecar, survit au redémarrage ; `:271` purge par âge. `C/tv/ReceiverServer.kt:939` : « Looked up by id AND by name » : un homonyme de même taille reprendrait le partiel d'un autre contenu.
- `C/xfer/TransferClient.kt:146` : `state(withHashes)` lu **au début** d'une tentative seulement ; `Scheduler.kt:92-145` : une voie `Failed` non fatale est remise en file sans relire la carte TV.
- `S/UploadService.kt`, `S/TransferQueue.kt` : aucun `lastModified` lu, aucune garde de source (grep 2026-10-03) ; R-09 ne couvre que « accès perdu ».
- `C/xfer/Scheduler.kt:51, 103` : `duplicates` = renvoi volontaire de fin de fichier sur voies parallèles (tolérable : `Already`).

## Fichiers possédés
Nouveaux : `C/xfer/TransferIdentity.kt`, `C/xfer/SourceGuard.kt`, `C/xfer/XferState.kt`, `CT/xfer/ResumeProtocolTest.kt` (vraie `ReceiverServer` port 0, `TvSim.restart()`), `CT/xfer/SourceGuardTest.kt`, `CT/xfer/XferStateTextsTest.kt`. Zones additives : `C/xfer/TransferClient.kt` (relecture après coupure, `SourceGuard`, `slice` par session, `Result` ⇒ `XferState`), `C/xfer/PartAssembler.kt` (sidecar : `root`, lecture tolérante d'un sidecar sans `root`), `C/xfer/TransferHost.kt` (`begin` par racine, `purged`), `C/tv/ReceiverServer.kt` (`transferBegin` : passe la racine ; ≤ 30 lignes), `C/xfer/XferTexts.kt` (phrases des états). **Hors zone** : `C/sync/*` (lire seulement), `S/`, `R/`, `C/link/*`.

## Étapes
1. **Rouge** (`ResumeProtocolTest`, horloge injectée) : (a) RS-07/08 : coupure à 40 % puis nouvelle instance de `TransferClient` sur la même source ⇒ `state()` lu **avant** tout `chunk` ; blocs renvoyés = blocs manquants exactement ; `TvSim.restart()` ⇒ 404 ⇒ `begin` ⇒ même carte ; fichier final identique ; (b) homonyme même taille autre contenu ⇒ **pas** de reprise croisée (rouge aujourd'hui) ; (c) RS-29 : source modifiée (octets au milieu, même taille, mtime +1) ⇒ `Abandoned(SOURCE_CHANGED)`, `abort` reçu par la TV, partiel supprimé, registre `SOURCE_CHANGED` ; (d) RS-30 : `slice` différent annoncé après `begin` ⇒ session inchangée ; carte d'une autre granularité ⇒ blocs non alignés renvoyés avec évènement ; (e) RS-09/26 : purge par âge ⇒ `purged:true` ⇒ texte « Reprise à zéro » ; (f) `XferStateTextsTest` : chaque état a une phrase ; « Terminé » n'apparaît que pour `DoneVerified` ; `Resumed(38)` ⇒ « Reprise de « x » (38 %) » ; `WaitingTv` ⇒ « En attente de la TV · 0:42 » ; `Paused` ⇒ « En pause : <raison> · reprise automatique au prochain contact ».
2. `TransferIdentity` : `data class`, `encode()`/`decode()` (ligne `identity=` additive pour la file R-09 : **le format de la file n'est pas modifié ici**, seulement la fonction ; w19-09 l'écrira).
3. `SourceGuard` : pur sur `(size, mtime, edgesSha | wholeSha)` + `Reader` injecté ; jamais plus de 128 Kio lus pour un gros fichier.
4. `PartAssembler` : `root` dans le sidecar, lecture tolérante ; `TransferHost.find(name, size, root)`.
5. `TransferClient` : relecture après coupure ; `slice` figé ; `SourceGuard.check` à chaque reprise ; `toState(): XferState` ; `Result.Paused` (w19-01) ⇒ `Paused`.
6. **Vert** : porte ; `:core:test` complet ; `CopyQueue*`, `MoveFallbackServerTest`, `CopyAndPlayHandoffServerTest`, `FluidPlaybackFixTest` verts.

## Critères d'acceptation
- (a)…(f) rouges par assertion avant, verts après (sortie dans le rapport).
- Aucun bloc déjà confirmé renvoyé après une coupure (compteur de `chunk` reçus par la TV = blocs manquants + `duplicates` voulus).
- Un sidecar **ancien** (sans `root`) est encore repris (fixture).
- Aucun secret ni chemin absolu dans un `XferState`/texte.

## Cas limites
Source sur `content://` sans `lastModified` fiable ⇒ bords seulement ; fichier ≤ 128 Kio ⇒ entier ; racine différente mais nom et taille égaux et **fichier final** déjà présent ⇒ `NAME_TAKEN` (jamais écrasé) ; `restart()` pendant `finish` ⇒ `verifying` puis `done` ; deux téléphones, même fichier ⇒ sessions distinctes, le second obtient `done` à la fin.

## À ne pas faire
Supprimer un original (« Déplacer » reste `MoveProof`) ; modifier le format JSON de la file du téléphone ; toucher `S/`, `R/` ; changer `slice` par défaut ; retirer `Result.Failed` (compatibilité des appelants Android).

## Rapport
RAPPORT + `SYMBIOSE: cap=resume2 (déclarée en w19-02) · proto=inchangé · reason=SOURCE_CHANGED,SOURCE_GONE · deux écrans=ResumeProtocolTest (états TV `TransferProgress` ↔ `XferState`)`.
