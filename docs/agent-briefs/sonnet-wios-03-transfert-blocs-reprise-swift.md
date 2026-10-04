# wios-03 — Swift : moteur de copie vers la TV (`CBTransfer`) : blocs SHA-256, reprise, envoi simple, réessais bornés, ligne d'état
<!-- routage architecte 2026-10-04 (vague iOS, ordre 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (chemin de perte de données : « Terminé » jamais annoncé sans vérification de la TV) · statut : **ATTEND wios-01, wios-tv-01**
> **Groupe : WIOS** (ordre 2) · porte : `tools/agents/gradle-lock.sh -- swift test --package-path ios/CastBridgeKit --filter CBTransferTests` + intégration TV factice
> **Jauge : ≈ 550 k jetons entrée / 30 k sortie** (effort L, ≈ 2,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F4/F8, § 7). Protocole : `docs/TRANSFER.md` § 2-5, `docs/ADMIN.md` § 4 (envoi simple). Garanties : `docs/coordination/DESIGN-W19-SYMBIOSE-PHONE-TV-2026-10-03.md` S-1, S-2, S-4, S-REPRISE. Branche `claude/wios-03-transfert`. Rapport : `docs/agent-reports/sonnet-wios-03.md`.

## Objectif (autonome)
Réécrire en Swift, **à l'identique sur le fil**, le client de copie du téléphone : `C/xfer/Blocks.kt` (tailles de bloc 1/4/8 Mio, tranches 256 Kio, `Manifest.root`), `C/xfer/TransferClient.kt` (begin/chunk/state/finish/abort, reprise), `C/xfer/WriteFailureClassifier.kt`, la reprise simple `/upload?offset&total` + `/api/part` (TV sans `/api/transfer/caps` ⇒ 404 ⇒ envoi simple), et la ligne d'état (`C/ux/TransferStatusLine.kt`). Pas d'interface : un moteur pur testable, piloté par l'app (wios-07).

## Fichiers possédés
- **Nouveaux** : `ios/CastBridgeKit/Sources/CBTransfer/{Manifest,BlockMap,BlockReader,TransferApi,TransferEngine,SimpleUpload,RetryPolicy,TransferState,TransferStatusLine,WriteFailure}.swift` ; tests `ios/CastBridgeKit/Tests/CBTransferTests/{ManifestVectorTests,TransferEngineTests,RetryPolicyTests,SimpleUploadTests,TransferIntegrationTests}.swift`.
- **Remplace** : `ios/CastBridgeKit/Sources/CBTransfer/Placeholder.swift`.
- **Lecture seule** : `CBTv` (wios-02 : utiliser `TvCredential`/`TvHttp` par protocole injecté ; si wios-02 n'est pas fusionné, définir un protocole minimal `TransferHttp` que wios-02 satisfera).
- **Interdit** : `ios/CastBridge/**`, tout fichier hors `ios/CastBridgeKit/**/CBTransfer*`.

## Spécification
1. `Manifest` : mêmes règles que Kotlin (vecteurs `transfer-vectors.json` : taille de bloc, nombre, dernier bloc, tranches, empreintes, racine) ; décalages `Int64` (> 4 Gio).
2. `BlockReader` : lecture par position (`FileHandle`), jamais tout le fichier en mémoire ; empreinte d'un bloc calculée à la demande, réutilisée à la reprise ; mémoire ≤ 2 blocs.
3. `TransferEngine` : `caps` (404 ⇒ `SimpleUpload`) → `storage/check` → `have` (doublon ⇒ état final « Déjà sur la TV ») → `begin` (carte de la TV autorité sur ce qu'elle a vérifié) → blocs manquants (1 à `maxStreams` connexions ; v1 : **2** au plus) avec `X-CB-Sha256` → `finish(root)` ; 409/422 ⇒ blocs renvoyés selon la carte rendue ; 429 ⇒ attente ; 503 volume retiré / 507 place ⇒ échec avec raison ; perte de liaison ⇒ `state` **avant** tout renvoi.
4. Quatre états finaux seulement (S-REPRISE) : **Terminé vérifié** (après `finish` 200), **Repris**, **Abandonné avec raison** (code + phrase française), **En attente de la TV** (durée + borne) ; **jamais** « Terminé » sur une simple fin d'envoi.
5. `RetryPolicy` : ≤ 12 tentatives **ou** ≤ 10 min par élément, courbe 2, 4, 8, 15, 30, 60 s ± 25 %, refus non réessayable ⇒ 0 réessai ; compteur exposé (« essai 3/12 »).
6. Interface pour le fond (consommée par wios-07) : `nextBlockJob() -> (url, headers, fileRange)` pour qu'un bloc puisse être écrit en fichier temporaire et confié à une `URLSession` d'arrière-plan ; `onBlockResult(idx, status, body)` idempotent (S-4).
7. `TransferStatusLine` : mêmes phrases que `signal-texts-vectors.json`.

## Critères d'acceptation (mutations au rapport)
- `ManifestVectorTests` : 100 % des vecteurs ; mutation : racine calculée sur les empreintes en majuscules ⇒ rouge.
- `TransferEngineTests` (faux serveur) : coupure au bloc 3 ⇒ reprise sans renvoyer 0-2 ; 422 sur le bloc 5 ⇒ renvoyé une fois ; `finish` 409 ⇒ blocs manquants renvoyés ; TV redémarrée (404 transfert inconnu) ⇒ `begin` renégocié ; aucun chemin n'atteint « Terminé » sans `finish` 200.
- `TransferIntegrationTests` (TV factice) : fichier de 50 Mio copié, coupé (arrêt/redémarrage du serveur), repris, fichier identique sur la TV (SHA-256) ; TV sans `caps` simulée ⇒ envoi simple repris par `/api/part`.
- `RetryPolicyTests` : 72 h simulées ⇒ jamais plus de 12 tentatives par élément.

## À ne pas faire
- Plus de 2 connexions en v1 ; compression ; écrire dans la photothèque ; dépendance externe ; changer le protocole de la TV.
