# w7-13 — CastBridge-TV : `SyncHost` (CBSX + CBSY sur `…0006` et `/api/sync/*`), branchement des domaines, `NOTIFY`, `keyring`

**Vague 7b · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 7a ; en parallèle de w7-12 : coder contre `TvBeacon.attachSync`/`SyncLinkHandler`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 6, § 7, § 11. Branche `claude/sonnet-w7-13`. Rapport : `docs/agent-reports/sonnet-w7-13.md`.

## Objectif
La TV sert la synchronisation : (1) `R/SyncHost.kt` : par lien RFCOMM `…0006` (via `TvBeacon.attachSync`) et par HTTP (`POST /api/sync/hs1|hs2|hs3`, `POST /api/sync/frame`, `GET /api/sync/wait?sid=&timeout=`), poignée de main `CBSX` (répondeur, clé statique = `InstallKey`/fichier, `expectedPeerPub` = clé du téléphone **si** connue dans le registre, `householdCheck` = code parental si D-W7-5 = oui), `SyncEngine(side = TV)` avec les 8 domaines branchés sur leurs sources ; (2) `NOTIFY` coalescé 250 ms à chaque changement persisté (activation, lots, locations, bibliothèque, grand livre, rapports, réglages, icônes) ; (3) `proofFor(nonce)` pour `ActDomain` (W6 `TvProof` si présent, sinon texte opaque de `GET /api/activation` + jeton d'activation) ; (4) `KEYRING` reçu sur `…0005` ⇒ `Keyring.verify/apply` ⇒ `files/keyring.txt` (`SafeFile`) ⇒ `ActivationCenter.trustedKeys()` = clés du build ∪ keyring ; (5) sessions LAN inactives fermées à 10 min ; 4 sessions max.

## Pourquoi (preuves)
- `R/TvService.kt:244-261` (chaîne `ApiExtension(...).then(...)` : ajouter `.then(SyncHost.api)`), `:259` (`tokenAuth`) ; `C/tv/ReceiverServer.kt:269-297` (ordre des routes, `denied()` : les routes `/api/sync/hs*` doivent être **publiques** (avant `denied`) car l'identité est la poignée de main ; `/api/sync/frame|wait` identifiées par `sid`) ; `C/tv/ReceiverServer.kt:1236` (`SERVICE_TYPE`).
- Sources : `R/ActivationCenter.kt` (`state()`, `statusFields()`, `trustedKeys()`), `R/LotsHub.kt` (manifeste `TvLotStore`), `R/RentalHub.kt` (locations), `C/tv/LibraryStore.kt` (index), `R/ParentalHub.kt` (`ReportOutbox` via `ParentalHub.syncHost`), `C/status/StatusIcons.kt` (`icons`), `R/TvPrefs.kt`.
- `C/link/{SyncEngine,SyncDomain,domains/*,SecureSession,Keyring}.kt` (7a).

## Fichiers possédés
Nouveaux : `R/SyncHost.kt`, `R/SyncSources.kt` (adaptateurs des sources → lambdas des domaines), `R/SyncHttp.kt` (extension `ApiExtension`), `R/KeyringStore.kt`. Modifiés (crochets d'une à trois lignes chacun : `SyncHost.changed(Dom.X)`) : `R/ActivationCenter.kt` (+ `trustedKeys()` ∪ keyring, + `proofFor`), `R/LotsHub.kt`, `R/RentalHub.kt`, `R/ParentalHub.kt`, `R/TvPrefs.kt`. **`tools/routes/routes.txt` est à w7-15** : lister dans le rapport les routes `/api/sync/*` et leur classe (`hs1|hs2|hs3` publiques-contrôlées, `frame|wait` par session ; ouvertes en essai et en mode réduit) ; `test_routes.py` peut échouer jusqu'à w7-15 : le dire. **Hors zone** : `R/TvService.kt`, `R/TvBeacon.kt` (w7-12 : demander par rapport l'ajout de la ligne `.then(SyncHost.api)` et `TvBeacon.attachSync(SyncHost)` si w7-12 n'est pas encore fusionné ; sinon l'ajouter soi-même **n'est pas permis** : une ligne dans le rapport `À BRANCHER`), `R/BtServer.kt` (w7-15), `R/PairActivity.kt` (w7-14).

## Étapes
1. `SyncHost.serve(peer, name, in, out)` : `Handshake(me = clé TV, initiator = false, expectedPeerPub = trust.find(peer, null)?.pub, caps, householdCheck)` → `SecureSession` → boucle `read frame → engine.onFrame → write` ; `Hello` ⇒ si `peerPub` inconnu du registre **et** `peer` non de confiance ⇒ `Error(3)` « Refusé » (le téléphone doit d'abord être de confiance : CBTH) ; si `peer` de confiance sans clé ⇒ `trust.bindKey`.
2. HTTP : `hs1` (corps = msg1) → `hs2` ; `hs3` → `{sid}` ; `frame` (corps chiffré) → réponses chiffrées ; `wait` long-poll ≤ 25 s renvoie les trames en attente (`NOTIFY`, `Delta`) ; `sid` inconnu ⇒ 410 ; sessions LAN : `peer` = adresse IP ⇒ **l'identité est la clé** (`peerPub` doit être dans le registre, sinon 403 « ce téléphone n'est pas de confiance »).
3. `SyncSources` : chaque domaine lit sa source **sans copier** de données sensibles (`par` : ids seulement) ; `IcoDomain` ← `icons` ; `SetDomain` ← prefs + `TunnelHub` état + `NetState`.
4. Crochets `changed(dom)` : `ActivationCenter` (accept/stage/sweep), `LotsHub.adopt/install/remove`, `RentalHub.sweep/install`, `LibraryStore` (par `TvService.rescan` ⇒ **via** `LotsHub`/`SyncSources` observateur du `library` provider : si cela exige `TvService.kt`, rapport `À BRANCHER`), `ParentalHub` (outbox), `TvPrefs.save*`.
5. `KeyringStore` : `files/keyring.txt`, union au démarrage, `SeqState` persisté (`files/keyring-seq.txt`) ; `OwnerBtHost.keyring` lambda (constructeur w7-07) ; journal.
6. Tests : JVM `SyncHostTest` (faux `Link`, deux engines bout à bout : TV + téléphone jouets, NOTIFY → PULL → DELTA → ACK, long-poll simulé) ; `:receiver` compile ; émulateur : `curl` sur `/api/sync/hs1` avec un client Python jouet (w7-11 fournit la poignée de main) ⇒ `hs2` valide.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :core:test --tests '*SyncHost*' --tests 'castbridge.core.link.*'
python3 tools/tests/test_routes.py   # peut échouer tant que w7-15 n'a pas inscrit /api/sync/* : noter dans le rapport
grep -c 'SyncHost.changed' android/receiver/src/main/kotlin/castbridge/receiver/*.kt   # ≥ 6 crochets
```
Observable (émulateur TV + téléphone w7-18) : poser une clé par USB/saisie ⇒ le téléphone lié reçoit `NOTIFY act` < 3 s ; installer un lot ⇒ `NOTIFY lots` ; retirer un fichier de la bibliothèque ⇒ `NOTIFY lib`.

## Cas limites
TV verrouillée : `TvService` absent ⇒ `SyncHost` répond `Hello` avec `caps` sans `CAP_SYNC` et domaine `act` seul (état `locked`) : le téléphone peut au moins voir « sans clé » ; 5e session ⇒ `Error(5)` ; trame AEAD invalide ⇒ fermer, journal, pas de relance côté TV ; mode essai : tous les domaines ouverts (lecture d'état), `lots` limité aux lots `-trial` (règle existante de `TvLotStore`).

## À ne pas faire
Ne jamais mettre un corps de rapport parental, un PIN, un jeton de confiance ou une adresse dans une entrée ; ne pas toucher `TvService.kt`/`TvBeacon.kt`/`BtServer.kt` ; pas de nouvelle permission.

## Rapport
`STATUT`, liste `À BRANCHER` (lignes à ajouter dans les fichiers hors zone), latences mesurées sur l'émulateur, signatures de `SyncHost`.
