# w1-02 — Écritures durables (fsync) pour l'état qui vaut de l'argent

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W1-A** (vague W1) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Rental*'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 1 · Effort S (≈ 4 h) · Statut PRÊT.** Branche `claude/sonnet-w1-02`. Rapport : `docs/agent-reports/sonnet-w1-02.md`.

## Objectif
Toute écriture d'un fichier dont la perte coûte au client (clé de location, lot scellé, index des lots de la TV, progression d'Apprendre, file de livraison du téléphone, liste « lus ») passe par un utilitaire atomique **avec fsync** et une relecture tolérante.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/lots/RentalVault.kt:38-39` (`putKey`) et `:69-70` (`putLot`) : `tmp.writeBytes(...)` puis `renameTo` **sans fsync** ; une coupure entre le renommage et l'écriture physique laisse un fichier vide → la location passe « suspecte » (`RentalLedger.kt:184-187`).
- `android/core/src/main/kotlin/castbridge/core/lots/TvLotStore.kt:251-252` : `lots.json.tmp` + `renameTo`, sans fsync : la TV « oublie » ses lots installés.
- `android/core/src/main/kotlin/castbridge/core/learn/Progress.kt:250-251` : progression de l'enfant, même idiome.
- `android/core/src/main/kotlin/castbridge/core/lots/DeliveryQueue.kt:44-50` (`FileQueueStore`) : file de livraison du téléphone.
- `android/core/src/main/kotlin/castbridge/core/tv/Storage.kt:142,147` : liste `played` réécrite **en place**.
- Deux utilitaires sûrs existent déjà : `android/core/src/main/kotlin/castbridge/core/owner/SafeFile.kt` (tmp + fsync + `.bak` validé + `ATOMIC_MOVE`, utilisé par `ActivationCenter` et `RentalLedger.kt:211`) et `android/core/src/main/kotlin/castbridge/core/tv/UsbStore.kt:81` (`AtomicFile`).
- Audit : OP-4 (`RECOMMANDATIONS-FABLE-2026-10-02.md`).

## Fichiers possédés
`C/lots/RentalVault.kt`, `C/lots/TvLotStore.kt`, `C/lots/DeliveryQueue.kt`, `C/learn/Progress.kt`, `C/tv/Storage.kt`, tests : `android/core/src/test/kotlin/castbridge/core/lots/RentalTest.kt`, le test existant de `TvLotStore` (chercher `grep -rln TvLotStore android/core/src/test`), `android/core/src/test/kotlin/castbridge/core/LearnLogicTest.kt` (section progression). **Ne pas modifier** `SafeFile.kt` ni `UsbStore.kt` (lecture seule) ; si un besoin manque, ajouter un **nouveau** fichier `C/io/Durable.kt`.

## Étapes
1. Lire `SafeFile.write/read` et `AtomicFile.write`. Choisir : `SafeFile` quand une validation de relecture et un `.bak` ont du sens (index `lots.json`, progression, file de livraison) ; `AtomicFile` (octets) pour les clés et lots scellés.
2. `RentalVault.putKey/putLot` : écrire via `AtomicFile.write(f, bytes)` ; conserver la vérification de chemin existante ; la **destruction** (`destroyKey`, réécriture zéro puis aléatoire) ne change pas.
3. `TvLotStore` : écriture de `lots.json` via `SafeFile.write(indexFile, json) { parseIndex(it) != null }` ; à la relecture, si le fichier est illisible mais `.bak` valide → relire `.bak` et journaliser (callback `onWarning` existant ou nouveau paramètre optionnel, défaut no-op).
4. `Progress.kt` (`LearnStore.save`) et `DeliveryQueue.FileQueueStore` : idem avec validation `parse`.
5. `Storage.kt` liste `played` : tmp + fsync + rename (via `AtomicFile`).
6. Tests (JVM) : pour chacun, un test « le fichier tronqué/vide est ignoré et `.bak` relu » et un test « rien n'est perdu après écriture » ; pour `RentalVault`, un test que `putKey` n'écrase jamais partiellement (lecture pendant l'écriture : le fichier visible est l'ancien ou le nouveau, jamais vide).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*'          # vert (176+ tests)
cd android && gradle --offline :core:test --tests 'castbridge.core.LearnLogicTest'   # vert
grep -n 'writeBytes\|writeText' android/core/src/main/kotlin/castbridge/core/lots/RentalVault.kt android/core/src/main/kotlin/castbridge/core/lots/TvLotStore.kt android/core/src/main/kotlin/castbridge/core/learn/Progress.kt   # 0 hit hors AtomicFile/SafeFile
```

## Cas limites
- `ATOMIC_MOVE` indisponible sur certains systèmes de fichiers (exFAT de la clé USB) : `SafeFile` a déjà un repli ; les fichiers de ce cahier sont tous dans `filesDir` (ext4/f2fs), sauf `played` si la bibliothèque est sur USB → garder le repli `delete + rename`.
- Ne pas changer le **format** des fichiers (compatibilité avec les TV installées).
- Ne pas introduire de fsync dans les écritures de **chunks** `.part` (performances) : hors périmètre.

## À ne pas faire
Pas de commit sur `integration/agents`/`main`, pas de déploiement, pas de secret, pas de modification de `SafeFile.kt`/`UsbStore.kt`/`ActivationCenter.kt`/`RentalLedger.kt`. Textes en français.

## Rapport
`STATUT`, fichiers, sortie des commandes, nombre de tests avant/après, limites.
