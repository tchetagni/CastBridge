# w8-07 — Cœur : façade `BulkTransfer`, garde W6 obligatoire, refus d'essai distinct, contrat W7 (`LinkSnapshot`), statistiques par voie

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (w6-17 souhaité)
> **Groupe : W8a-3** (vague W8a) · prérequis : w8-01, w8-02 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BulkTransfer*' --tests '*LaneStats*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 8a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-01, w8-02 ; w6-17 souhaité).** Conception : § 3, 6.2 (contrat), 7.1, 7.2, 7.4, 11. Branche `claude/sonnet-w8-07`. Rapport : `docs/agent-reports/sonnet-w8-07.md`.

## Objectif
Une **seule porte d'entrée** vers le moteur pour le téléphone (envoi de fichier, lots, déplacement), qui (1) exige par son **type** que la garde W6 ait été passée, (2) remonte un refus de la TV (essai, mode réduit, parental) comme `Refused(message)` et non comme une panne de liaison, (3) consomme les liens que W7 fournit (`LinkSnapshot`) sans rien découvrir elle-même, (4) expose l'état et les statistiques par voie.

## Pourquoi (preuves)
- `TransferClient.run()` (`core/xfer/TransferClient.kt:120-160`) est l'appel actuel : il reçoit une fabrique de voies figée et mélange `Failed("autorisation refusée par la TV (403)")` (`Lanes.kt:46`) avec les pannes.
- La TV refuse en essai avec `403 {"error":…,"trial":true}` (`ReceiverServer.kt:280`) ; `SendGuard` (w6-17) doit être consulté **avant d'enfiler** (`DESIGN-W6 § 3.7`, ligne 225).
- W7 absent : contrat § 7.1 (champs, pas noms).

## Fichiers possédés
Nouveaux `C/xfer/{BulkTransfer,LinkSnapshot,LaneStats}.kt`, `CT/xfer/{BulkTransferTest,LaneStatsTest}.kt` ; `C/xfer/TransferClient.kt` (voies via `LaneSet`, `Refused`, sondage `state` étendu). **Hors zone** : `C/xfer/SendGuard.kt` (w6-17) : si absent, créer **dans `BulkTransfer.kt`** un `sealed class Verdict { object Allowed }` marqué `// TODO w6-17 : remplacer par SendGuard.Verdict` et le dire ; tout fichier Android.

## Étapes
1. `LinkSnapshot(tv, lanBase, directBase, btAddress, btAlive, usbBase, credential: () -> String?, trusted, wifiGhz)` et `interface LinkSet { snapshot(); onChange(l); requestWifiDirect(): Boolean }` ; `interface TransportListener { laneUp(id, kind); laneDown(id, reason); rate(id, bps) }` (pour W7/`LinkMachine`).
2. `BulkTransfer.send(api: TransferApi, source: BlockSource, name, target, verdict: Verdict.Allowed, links: LinkSet, laneFactory: LaneFactory, options: Options(progressive, encrypt, capBps, compress), listener): Handle` ; `Handle` : `progress: () -> Progress(done, total, bps, lanes: List<LaneStats>)`, `pause()`, `resume()`, `cancel()`, `await(): Result`. `Result` = `Done | Cancelled | Unsupported | Refused(message, trial: Boolean) | Failed(reason)`.
3. `LaneFactory` (interface) : `wifi(base, laneId)`, `direct(base, laneId)`, `bt(address, laneId)`, `usb(base, laneId)` : les implémentations Android viennent en 8c ; les tests passent des `FakeLane`.
4. `TransferClient` : construire un `LaneSet` vide, le remplir à partir de `links.snapshot()` **et** à chaque `onChange` (ajout d'une voie quand une adresse apparaît, retrait quand elle disparaît) ; la politique « instantané puis accéléré » proprement dite (délais, CBTN) reste en 8c (`LaneBringup`) : ici, seulement « une adresse présente = une voie ». Appeler `/api/transfer/session` et `lanes` si `caps.version ≥ 2` (interface `TransferApi` étendue : `session(id, noncePhone, wantEnc)`, `lane(id, session, kind)`, `dropLane` ; l'implémentation HTTP vient de w8-08/w8-10 : ici des méthodes `default` qui renvoient `null` pour une TV v1).
5. Refus : `ChunkClient.outcome` 403 avec `"trial":true` → `Outcome.Failed(msg, fatal=true, refused=true)` (champ ajouté) ; `TransferApi.Refused` 403 `trial` → `Result.Refused(message, trial=true)` ; CBX `ERR_TRIAL` idem (w8-09 l'émettra).
6. `LaneStats(id, kind, bytes, bps, rttMs, errors, state)` + `toJson()` (JSON à la main, comme `TransferHost.stateJson`) + `fromJson` (lecture de `state.lanes` de la TV).
7. Tests : refus d'essai → `Refused` sans nouvel essai ; `Verdict` obligatoire (test de compilation par réflexion : le paramètre existe) ; voie ajoutée par `onChange` à mi-transfert ; TV v1 (pas de `session`) → non chiffré, fonctionne ; `Handle.pause/resume/cancel` ; `LaneStats` aller-retour JSON.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.BulkTransferTest' --tests 'castbridge.core.xfer.LaneStatsTest'   # vert, ≥ 10 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.MultipathServerTest'   # vert (chemin v1 intact)
grep -n "fun send(" android/core/src/main/kotlin/castbridge/core/xfer/BulkTransfer.kt | grep -c "Verdict"   # 1
```

## Cas limites
`links.snapshot()` sans aucune adresse : `Handle` en attente (« aucune voie »), pas `Failed` ; jeton renouvelé pendant le transfert (`credential()` relu à chaque requête, existant) ; `Refused` reçu pendant qu'une autre voie envoie : tout s'arrête, résultat `Refused`.

## À ne pas faire
Ne pas découvrir la TV ni joindre Wi-Fi Direct ici (W7/8c) ; pas d'Android ; ne pas inventer de phrase utilisateur hors du message de la TV (le catalogue est à W6).

## Rapport
`STATUT`, signatures `BulkTransfer.send`/`Handle`/`LinkSet` (copiées : contrat pour 8c et pour W7), présence ou non de `SendGuard`, tests avant/après.
