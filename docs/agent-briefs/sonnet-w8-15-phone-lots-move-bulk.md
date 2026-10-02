# w8-15 — Lots et déplacement de bibliothèque par le moteur en vrac (`BulkLotTransport`, `MoveToTv`)

**Vague 8c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-07 ; en parallèle de w8-14 : coder contre le contrat `BulkTransfer.send`/`LinkSet` du rapport w8-07).** Conception : § 7.3, 8.1. Branche `claude/sonnet-w8-15`. Rapport : `docs/agent-reports/sonnet-w8-15.md`.

## Objectif
Qu'un lot de plusieurs centaines de Mo (W4/W5, scellé par TV) parte par **toutes les voies** et par blocs de 1-8 Mio plutôt que par 1 000 requêtes POST de 512 Kio, puis soit installé par la route existante ; que le « déplacer vers la TV » utilise le même moteur. Repli automatique sur les chemins actuels.

## Pourquoi (preuves)
- `HttpLotTransport.send` : boucle `POST /api/lots/upload?offset=` par 512 Kio (`core/lots/LotPush.kt:135-159`, `TvLotApi.CHUNK`) ; `POST /api/lots/install?name=` adopte le fichier (`LotPush.kt:74-77`) ; `LotsHub.adopt` prend les fichiers du dossier de réception (`receiver/BtServer.kt:123`).
- `LotsRuntime.transport()` choisit HTTP ou CBT1 (`sender/LotsRuntime.kt:186-195`) et diffère la livraison pendant un envoi (`116-127, 200`).
- `MoveToTv` = deuxième moitié d'un `UploadService` en mode `move` (`sender/MoveToTv.kt:19-23`, `UploadService.checkMoved`).

## Fichiers possédés
`C/lots/LotPush.kt` (**classe additive** `BulkLotTransport : LotTransport`), `CT/lots/LotsDeliveryTest.kt`, `S/{LotsRuntime,MoveToTv}.kt`. **Hors zone** : `S/UploadService.kt` (w8-14), `C/xfer/**` (8a), `TvLotStore` (vérifier seulement qu'`installReceived` accepte un fichier arrivé par `/api/transfer` dans le même dossier : si non, **demander** une accroche dans le rapport, ne pas éditer).

## Étapes
1. `BulkLotTransport(api: TransferApi, links: LinkSet, factory: LaneFactory, http: HttpLotTransport)` : `manifest/setPriority/remove` délégués à `http` ; `send(meta, file, proofJson, …)` : `BulkTransfer.send(source = FileBlockSource(file), name = LotNames.fileName(meta), target = null, verdict, …)` → `Done` → `POST /api/lots/install` (existant) → `Installed` ; `Unsupported` → `http.send` (repli) ; `Refused(msg)` → `SendResult.Refused(msg)` ; `Failed`/`Cancelled` → `LinkDown(confirmedBytes, reason)` avec `confirmedBytes` = octets de la carte de blocs (progression visible).
2. `LotsRuntime.transport()` : `BulkLotTransport` quand `caps` répond (une TV v1 ou v2) ; sinon l'ordre actuel (HTTP 512 Kio, puis CBT1). La règle « livraison différée pendant un envoi » reste (**un seul transfert à la fois** côté téléphone).
3. Les lots d'essai seulement sans `Linked` (w6-17) : inchangé ; le `Verdict` vient de `LotsRuntime` qui l'obtient de `SendGuard` (ou du stub w8-07).
4. `MoveToTv` : rien à changer dans la suppression ; vérifier que `checkMoved` s'appuie sur `TvInfo` (taille exacte) et non sur `.part` : déjà le cas (`UploadService.kt:216-232`) ; ajouter le cas « fichier découpé » (w8-13) seulement si w8-13 est fusionné.
5. Tests `LotsDeliveryTest` : `BulkLotTransport` avec une fausse `TransferApi`/`FakeLane` : `Installed` ; TV v0 → repli HTTP ; refus d'essai → `Refused` sans nouvel essai ; coupure → `LinkDown(confirmed > 0)` et reprise à la carte.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.LotsDeliveryTest' --tests 'castbridge.core.lots.LotsTvTest'   # vert, ≥ 5 nouveaux cas
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -n "class BulkLotTransport" android/core/src/main/kotlin/castbridge/core/lots/LotPush.kt | wc -l   # 1
```

## Cas limites
Lot déjà présent (même nom + taille : `begin` répond `done` → aller directement à `install`) ; `install` qui refuse (signature) après un transfert réussi → `Refused`, fichier laissé à la TV pour son nettoyage ; TV de confiance par jeton mais `/api/transfer` fermé par le parental : `Refused(M-PARENTAL-BLOCKED)` via le catalogue w6.

## À ne pas faire
Ne pas changer les routes `/api/lots/*` ; ne pas envoyer un lot par CBT1 **et** par le moteur en même temps ; ne pas toucher à `DeliveryQueue`.

## Rapport
`STATUT`, accroche demandée à `TvLotStore` si besoin, tests, compilation.
