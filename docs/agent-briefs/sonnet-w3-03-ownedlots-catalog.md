# w3-03 — Un lot acheté n'est jamais supprimé : catalogue signé sur la TV ; plafond 3 Mo à la réception

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w1-02)
> **Groupe : W3-A** (vague W3) · prérequis : w1-02 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Lots*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 3 · Effort M (≈ 1,5 j) · Statut PRÊT** (après w1-02). Branche `claude/sonnet-w3-03`. Rapport : `docs/agent-reports/sonnet-w3-03.md`.

## Objectif
1. La TV reçoit (avec l'activation, ou séparément par `/api/rental/catalog`) le **catalogue de bouquets signé** et résout les bouquets nommés en lots ; `OwnedLots.of(…, catalog)` conserve donc à l'échéance d'une location tout lot couvert par un achat/abonnement **par bouquet**.
2. `TvLotStore`/`RentalApi` refusent un lot > 3 Mo (`LotBudget` par lot) **avant** de lire tout le fichier en mémoire.
3. Tests cœur.

## Pourquoi (preuves)
- `C/lots/OwnedLots.kt:8-10` : « The TV currently has NO bundle catalogue … A lot covered only by a named bundle … is NOT kept » ; `R/RentalHub.kt:33` l'appelle sans catalogue → un lot acheté via un bouquet nommé pendant sa location est **supprimé** à l'échéance (A6-4, partiel).
- `C/lots/SignedBundleCatalog.kt` : vérification de signature et de fraîcheur déjà écrites (utilisées par les outils) ; `C/lots/ServerBundleCatalog.kt`.
- `C/lots/RentalApi.kt:35` : `part.readBytes()` puis `tmp.writeBytes(plain)` : chiffré + clair + tampon en mémoire ≈ 3× la taille ; `TvLotStore.receive` accepte jusqu'au budget total (10 Mo) et non 3 Mo par lot (audit Opus A6-6).
- Audit : SE-11.

## Fichiers possédés
`C/lots/OwnedLots.kt`, `C/lots/RentalApi.kt`, `C/lots/TvLotStore.kt`, `R/RentalHub.kt`, `C/lots/RentalDelivery.kt` (envoi du catalogue par le téléphone), `android/core/src/test/kotlin/castbridge/core/lots/**`. **Hors zone** : `ActivationCenter`, `SignedBundleCatalog.kt` (utiliser tel quel), `LotsHub`.

## Étapes
1. `RentalApi` : route additive `POST /api/rental/catalog` (PIN) recevant le JSON du catalogue signé ; vérification par `SignedBundleCatalog.verify` avec les clés de mise à jour (mêmes que les outils : `UpdateKeys`) ; refus si plus ancien que le gardé ; persistance `SafeFile` dans `rental/catalog.json` ; `RentalHub` le relit au démarrage et le passe à `OwnedLots.of(…, catalog)`.
2. `RentalDelivery` (cœur, côté téléphone) : avant d'envoyer la clé/les lots, envoyer le catalogue s'il est plus récent que celui que la TV annonce (`GET /api/rental` expose `catalogGeneratedAt`).
3. `TvLotStore.receive`/`LotsApi upload` : si `total > LotBudget.MAX_LOT_BYTES` (3 Mo ; constante à créer dans `LotApi.kt` si absente — **vérifier** : `tools/content-budget` et `LearnTool lots` imposent déjà 3 Mo) → 413 « Lot trop gros (max 3 Mo) » avant toute écriture ; `RentalApi` : déchiffrer par flux (`CipherInputStream`) vers le fichier `.plain` au lieu de tout charger (AES-GCM impose de lire jusqu'au tag : écrire dans un fichier temporaire puis valider — acceptable).
4. Tests : `OwnedLots` avec catalogue (lot de bouquet acheté conservé ; sans catalogue inchangé) ; `RentalApi` catalogue signé accepté / non signé refusé / plus ancien refusé ; lot de 3,1 Mo refusé par `/api/lots/upload` ; balayage après achat par bouquet : `keptBecauseOwned` contient le lot.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.*'   # vert, ≥ 5 nouveaux tests
grep -n 'readBytes()' android/core/src/main/kotlin/castbridge/core/lots/RentalApi.kt   # 0 hit
cd android && gradle --offline :receiver:compileDebugKotlin                  # compile (si SDK)
```
Observable (campagne, étape 31) : lot de 3,1 Mo → refus avec message ; `tools/rental-test/rental_test.py` passe toujours (le script envoie le catalogue : l'étendre si besoin dans w3-14).

## Cas limites
- Catalogue absent sur la TV : comportement actuel (lots `extraLots` et `tout` conservés) ; journaliser « catalogue absent : les bouquets nommés ne sont pas résolus ».
- Deux clés de catalogue (production + `EXTRA_UPDATE_KEY` de test) : réutiliser `UpdateKeys` tel quel.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas changer les règles du balayage ; textes en français.

## Rapport
`STATUT`, routes ajoutées, tests, limites.
