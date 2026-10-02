# w2-14 — Hygiène du cœur : code mort, un seul analyseur JSON, analyseurs du téléphone déplacés en cœur, drawables inutilisés

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W2-A** (vague W2) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 2 · Effort M (≈ 1,5 j) · Statut PRÊT.** Branche `claude/sonnet-w2-14`. Rapport : `docs/agent-reports/sonnet-w2-14.md`.

## Objectif
1. Supprimer les types sans aucune référence hors de leur fichier (vérifié sur `android/*/src/main`, `tools/`, `backend/`), déplacer les doublures `Memory*` en `src/test`.
2. Un seul analyseur JSON en cœur (`JsonLite`) ; `org.json` disparaît du sender pour les schémas de la TV.
3. `LibraryItem.parse(json)` et `StorageSnapshot.parse(json)` en cœur, à côté des sérialiseurs, avec tests aller-retour ; le sender les utilise.
4. 16 drawables non référencés supprimés.

## Pourquoi (preuves)
- Sans référence : `C/trust/ResilientCall.kt` (118 l.), `C/learn/LearnQuiz.kt`, `C/curriculum/LevelPick.kt` (`LevelPick` ; `Placement` a 1 réf.), `C/owner/PhoneConsole.kt` (sauf `RegistryStore`, utilisé ×9), `C/xfer/Lanes.kt` (`ChunkClient/KController/WifiDirectLane`), `C/xfer/Lane.kt:84` `UsbLane.IMPLEMENTED=false`, et ≈ 20 types : `MuxFrame`, `Bencode`, `TorrentFile`, `HttpJson` (chess), `AndroidTvChannel/Messages`, `CvteInfo/CvteMessage/SonyIrcc`, `HttpSignature`, `ImportProgress`, `Entitlements/ProductKind`, `MediaInfo/RoutePlanner`, `StoreIndex`, `MemoryLinkStore`.
- Trois analyseurs JSON : `C/net/JsonLite.kt`, `C/quiz/Json.kt`, `C/dl/Json.kt` ; `org.json` dans `S/StoragePanel.kt:13`, `S/TvLibraryScreen.kt:40`, `S/TvPlayerSettings.kt:24`, `S/ActivateTvActivity.kt:68,160` (ce dernier **hors zone**, w2-03).
- Schémas dupliqués : `S/TvLibraryScreen.kt:43,53-64` (`TvLibItem`, `TvLibraryParser`) vs `C/tv/Library.kt:46` `LibraryItem` sérialisé par `ReceiverServer.libraryJson` (`:798`) ; `S/StoragePanel.kt:16-34` vs `ReceiverServer.volumesJson` (`:851`).
- 16 drawables non référencés dans `android/receiver/src/main/res/drawable*` (`ic_cb_avance_10s, ic_cb_copier, ic_cb_deplacer_vers_tv, ic_cb_envoyer, ic_cb_lecture, ic_cb_pause, ic_cb_pistes_audio, ic_cb_recul_10s, ic_cb_sous_titres, logo_apprendre_horizontal, logo_castbridge_logo_horizontal, logo_castbridge_mark, logo_castbridge_tv, logo_echecs, logo_echecs_horizontal, logo_quiz_des_millions`) — **revérifier** chacun avec `grep -rn '<nom>' android/receiver/src android/sender/src` avant suppression (les logos peuvent être référencés par l'app téléphone ou `branding/`).
- Audit : AR-6, AR-7.

## Fichiers possédés
`C/trust/ResilientCall.kt`, `C/learn/LearnQuiz.kt`, `C/curriculum/LevelPick.kt`, `C/owner/PhoneConsole.kt`, `C/xfer/Lanes.kt`, `C/xfer/Lane.kt`, `C/net/JsonLite.kt`, `C/quiz/Json.kt`, `C/dl/Json.kt`, `C/tv/Library.kt`, `C/tv/TvClient.kt`, `S/TvLibraryScreen.kt`, `S/StoragePanel.kt`, `S/TvPlayerSettings.kt`, les autres fichiers cœur hébergeant les types listés (uniquement pour retirer le type), `android/receiver/src/main/res/drawable*/` (les 16 fichiers), tests cœur correspondants. **Hors zone** : `C/owner/License.kt` (w2-01 : laisser `LicenseInfo/LicenseState/SeatInfo/TransferInfo`), `S/ActivateTvActivity.kt` (w2-03), `ReceiverServer.kt`, `TrialPolicy.kt`.

## Étapes
1. Pour chaque candidat : `grep -rn '\bNom\b' android/*/src/main tools backend --include=*.kt --include=*.java --include=*.py` → 0 hit hors du fichier ⇒ supprimer (et son test s'il ne teste que lui) ; 1+ hit ⇒ garder et le dire.
2. `RegistryStore` extrait de `PhoneConsole.kt` vers `C/owner/RegistryStore.kt` ; le reste du fichier supprimé.
3. `Memory*` (doublures) : déplacer vers `android/core/src/test/kotlin/...` (même paquet).
4. JSON : `C/quiz/Json.kt` et `C/dl/Json.kt` deviennent des alias/délégués de `JsonLite` (ou leurs appelants migrent) ; supprimer quand plus aucun appelant.
5. `LibraryItem.parse(json)` / `LibrarySnapshot.parse` et `StorageSnapshot.parse` en cœur (à côté de `libraryJson`/`volumesJson` : si ces fonctions sont dans `ReceiverServer.kt`, **ne pas les déplacer** ; écrire les analyseurs dans `Library.kt`/un nouveau `C/tv/StorageSnapshot.kt` et un test aller-retour qui appelle le sérialiseur existant par l'API publique) ; `TvLibraryScreen`/`StoragePanel`/`TvPlayerSettings` utilisent ces analyseurs ; `TvClient` gagne les méthodes typées pour les 18 routes littérales de `TvPlayerSettings`.
6. Drawables : supprimer après vérification ; `gradle :receiver:assembleDebug` doit passer (si SDK).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test                                   # suite complète verte (moins les tests des types supprimés)
grep -rn 'import org.json' android/sender/src/main/kotlin/castbridge/sender/{StoragePanel,TvLibraryScreen,TvPlayerSettings}.kt   # 0 hit
grep -rn 'class ResilientCall\|object LevelPick\|class LearnQuiz' android/core/src/main   # 0 hit
cd android && gradle --offline :sender:compileDebugKotlin :receiver:assembleDebug   # compile (si SDK)
```

## Cas limites
- `Entitlement.kt` : ne retirer que `Entitlements/ProductKind` si réellement sans référence (`Right.Purchase/Subscription` sont utilisés).
- `shrinkResources` retire déjà les drawables en release : la suppression est de l'hygiène, pas une économie mesurable.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne supprimer aucun type référencé par `tools/` ou `backend/` ; ne pas toucher aux fichiers hors zone.

## Rapport
`STATUT`, tableau candidat → supprimé/gardé (raison), lignes supprimées, résultats de compilation.
