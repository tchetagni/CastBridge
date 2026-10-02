# w8-08 — Cœur : `TransferHost` v2 (sessions, voies, part équitable, stats), `PartAssembler` (fsync cadencé, enregistrements chiffrés, contiguïté), test de coupure de courant

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w8-05, w8-03)
> **Groupe : W8a-3** (vague W8a) · prérequis : w8-05, w8-03 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TransferHostV2*' --tests '*PowerCut*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-05 et w8-03).** Conception : § 4.6 (part équitable), 4.7, 5.4, 5.5, 6.1, 6.4, 10. Branche `claude/sonnet-w8-08`. Rapport : `docs/agent-reports/sonnet-w8-08.md`.

## Objectif
Côté TV, indépendamment de HTTP : attribuer une session chiffrée et des identifiants de voie, partager les flux entre téléphones, publier les statistiques par voie et la position contiguë, écrire les enregistrements chiffrés sans tampon de bloc, et garantir qu'après une coupure de courant un bloc marqué « reçu » est réellement sur le disque.

## Pourquoi (preuves)
- `TransferHost` (`core/xfer/TransferHost.kt:19-110`) : sessions par identifiant de manifeste, `maxStreams` global, `stateJson` sans voies.
- `PartAssembler.persistSoon` (toutes les 1 s) écrit `.state` sans `force()` préalable (`PartAssembler.kt:198-205`) ; `finish` relit tout (`144-170`) et rattrape : coût = renvoi de blocs après coupure.
- `writeBlock` lit le corps par 256 Kio et écrit en place (`76-106`) : à étendre pour un corps en enregistrements AEAD.

## Fichiers possédés
`C/xfer/{TransferHost,PartAssembler}.kt`, nouveaux `CT/xfer/{TransferHostV2Test,PowerCutTest}.kt` ; `CT/MultipathTransferTest.kt` (**adaptation seulement** si une signature change ; dire laquelle). **Hors zone** : `ReceiverServer.kt` (w8-10), `SessionKeys`/`AeadRecords` (w8-05 : utiliser), `OrderPolicy.contiguousBytes` (w8-03 : utiliser).

## Étapes
1. `TransferHost.openSession(id, noncePhone, ikm: ByteArray?, wantEnc, pick: CipherPick.Pick?): SessionInfo(sessionId, nonceTv, encOn, cipher)` ; `addLane(id, sessionId, kind): Int` (laneId 1..255, unique par session, jamais réattribué), `dropLane`, `lanes(id): List<LaneStats>` (octets, débit EWMA par voie mesuré **côté TV**, erreurs) ; `ReplayWindow` par voie.
2. Part équitable : `streamsFor(sessionId) = max(1, maxStreams / sessionsActives)` ; `mayAccept(session, blockBytes)` tient compte du quota par session ; `stateJson` publie ce `maxStreams` par session.
3. `PartAssembler.writeBlock/writeSlice` : paramètre `records: RecordInputStream?` (w8-05) : si présent, lire par enregistrements vérifiés puis écrire ; sinon chemin actuel. `contiguous()` via `OrderPolicy.contiguousBytes`. `fsync` : `ch.force(false)` quand ≥ 32 Mio écrits depuis le dernier ou ≥ 5 s, **avant** `persist()` ; constante réglable (`fsyncEveryBytes`, 0 = jamais, pour le banc).
4. `stateJson` : ajouter `lanes:[…]`, `contiguous`, `encOn`, `cipher`, `maxStreams` (par session) ; ne rien retirer.
5. `PowerCutTest` : simuler la coupure en **ne rappelant jamais `close()`** et en rouvrant le dossier avec un nouveau `PartAssembler.open` ; variante « écriture perdue » : tronquer `.data` sur les derniers octets écrits avant la persistance (simulation du cache non vidé) et vérifier que, avec `fsync` actif, `.state` n'annonce que des blocs réellement lisibles (`finish` ne renvoie rien) ; sans `fsync` : `finish` les renvoie (comportement actuel, toujours sûr).
6. `TransferHostV2Test` : deux sessions → 3 flux chacune ; laneId unique et jamais réutilisé après `dropLane` ; rejeu refusé ; session avec PIN (ikm null) → `encOn=false` ; stats par voie après 10 blocs.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.TransferHostV2Test' --tests 'castbridge.core.xfer.PowerCutTest'   # vert, ≥ 10 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.MultipathTransferTest' --tests 'castbridge.core.MultipathServerTest'   # vert
```

## Cas limites
`force()` qui échoue (clé retirée) → `DiskFail` comme une écriture ; session expirée (12 h) pendant un transfert : nouvelle session, même carte ; 256 voies demandées (refus `laneId` épuisé) ; `discard=true` (banc réseau seul) : pas de fsync, pas de disque.

## À ne pas faire
Ne pas changer le format de `.state` (`CBX1`, `PartAssembler.kt:203`) : ajouter une **ligne 6** optionnelle si besoin, les anciens fichiers restent lisibles ; pas de HTTP ici ; aucune clé en journal.

## Rapport
`STATUT`, signatures ajoutées à `TransferHost`, coût mesuré du fsync sur disque local (indicatif), tests avant/après.
