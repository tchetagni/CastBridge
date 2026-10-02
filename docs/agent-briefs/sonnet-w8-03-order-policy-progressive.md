# w8-03 — Cœur : ordre des blocs (`OrderPolicy`), lecture pendant l'envoi multivoie, position contiguë

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w8-01)
> **Groupe : W8a-2** (vague W8a) · prérequis : w8-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OrderPolicy*' --tests '*SparseStream*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-01 : `Scheduler.pick` pluggable).** Conception : § 4.5, 6.1 (`contiguous`). Branche `claude/sonnet-w8-03`. Rapport : `docs/agent-reports/sonnet-w8-03.md`.

## Objectif
Permettre « lire pendant l'envoi » avec plusieurs voies : aujourd'hui le transfert rapide est **désactivé** dès que la lecture progressive est demandée (`sender/UploadService.kt:153-155`) parce que les blocs n'arrivent pas dans l'ordre et que `GrowingStream` attend un `.part` qui grossit (`core/tv/Progressive.kt:34-91`).

## Pourquoi (preuves)
- `Scheduler.take` : voies rapides par le début, lentes par la fin (`Scheduler.kt:99`) ; aucune notion de position de lecture.
- `Progressive.bootstrapBytes` = max(2 Mio, 3 s de débit) (`Progressive.kt:97-99`) ; `Mp4Atoms.layout` sait si le `moov` est devant (`Progressive.kt:151-174`) mais ne donne pas sa plage d'octets.
- `PartAssembler.map` (carte de blocs) est la seule vérité de ce qui est sur le disque (`PartAssembler.kt:61`).

## Fichiers possédés
Nouveaux `C/xfer/{OrderPolicy,SparseGrowingStream}.kt`, `CT/xfer/{OrderPolicyTest,SparseStreamTest}.kt` ; `C/tv/Progressive.kt` (ajouter `Mp4Atoms.moovRange(total, readAt): LongRange?`, rien d'autre). **Hors zone** : `Scheduler.kt` (w8-01 expose `pick`), `PartAssembler.kt` (w8-08 ajoute `contiguous()` ; coder contre `BlockMap`), `ReceiverServer.kt` (w8-10 branche `/stream` sur `SparseGrowingStream`).

## Étapes
1. `OrderPolicy` (pur) : `Bulk` (comportement actuel) et `Progressive(playheadBytes: () -> Long, leadBytes: Long, priority: List<IntRange>)`. `pick(lane: LaneView, pending: SortedSet<Int>, inFlight: Map<Int, Int /*copies*/>): Int?` : en progressif, toutes les voies prennent le premier bloc manquant ≥ `bloc(playhead)` ; une voie dont `unitBytes < blockSize` prend à partir de `bloc(playhead + lead + 32 Mio)` ; les plages `priority` (0..2 Mio et `moov`) passent **avant tout**, dupliquées sur une seconde voie si `inFlight[idx] < 2`. Blocs derrière la tête de lecture : jamais pris en premier (ils sont déjà lus ou inutiles), mais pris en dernier pour que le fichier soit complet.
2. `Mp4Atoms.moovRange` : même marche d'atomes que `layout`, renvoie `[pos, pos + size)` du `moov` quand il précède `mdat` ; `null` sinon.
3. `SparseGrowingStream(dir, id, manifest, map: () -> BlockMap, start, end, waitMs, pollMs, sleep, clock, alive)` : lecteur séquentiel qui bloque tant que `map().has(bloc(pos))` est faux ; lit `.cbx/<id>.data` par `RandomAccessFile` positionné, 64 Kio par appel ; mêmes règles d'expiration que `GrowingStream` (`waitMs` sans progrès → `IOException`, `alive` faux → échec immédiat) ; après le renommage final (`.data` disparu) : relire par nom (`<name>` ou `<name>.part`) comme `GrowingStream`.
4. `contiguousBytes(map, manifest): Long` (fonction pure dans `OrderPolicy.kt`) : nombre d'octets contigus depuis 0 ; w8-08 l'expose dans `state`.
5. Tests : ordre produit pour un fichier de 40 blocs avec tête de lecture à 10, avance 4, voie lente et voie rapide ; `moov` prioritaire et dupliqué ; `moovRange` sur un MP4 synthétique (`ftyp`+`moov`+`mdat`) et sur `mdat` d'abord (`null`) ; `SparseGrowingStream` : lecture qui bloque puis reprend quand le bloc arrive, expiration, bloc 0 absent mais bloc 1 présent (attend), bascule `.data` → nom final.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.OrderPolicyTest' --tests 'castbridge.core.xfer.SparseStreamTest'   # vert, ≥ 10 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.ProgressiveTest*'   # tests existants de Progressive toujours verts (nom à vérifier : grep -rl Mp4Atoms CT/)
```

## Cas limites
Tête de lecture qui recule (seek arrière) : les blocs derrière redeviennent prioritaires ; `lead` > taille du fichier ; fichier plus petit que 2 Mio ; `moov` plus grand qu'un bloc (plage multi-blocs) ; une seule voie lente en progressif : elle prend par la tête (pas de « loin devant » s'il n'y a qu'elle).

## À ne pas faire
Pas de réécriture faststart (décision D-W8-3) ; ne pas modifier `GrowingStream` (le chemin classique reste) ; ne pas toucher `Scheduler.kt`.

## Rapport
`STATUT`, signature de `OrderPolicy.pick`, ce que w8-10 doit brancher pour `/stream`, tests avant/après.
