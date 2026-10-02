# w7-15 — CastBridge-TV : grand livre des transferts branché (`BtServer`, `/api/upload`, `/api/transfer`), écran « Connexion » (diagnostic, journal, empreinte, mises à jour « apps inconnues »)

**Vague 7b · Effort M (≈ 2,5 j) · Modèle : sonnet · Statut PRÊT (après 7a ; w7-12 a déclaré `LinkDiagActivity` dans le manifeste et l'entrée de menu).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 2 (problèmes 5, 7), § 9. Branche `claude/sonnet-w7-15`. Rapport : `docs/agent-reports/sonnet-w7-15.md`.

## Objectif
(1) `TransferLedger` (w7-10) alimenté par `BtServer` (CBT1 : `begin/progress/interrupted/done`), par l'upload HTTP classique et par `/api/transfer` (begin/chunk/finish/abort) ; la ligne d'état `1-bt` et une nouvelle ligne `7-xfer` viennent de `ledger.statusLine()` ; **un transfert interrompu reste affiché** « Interrompu à 42 % : reprise possible » jusqu'à reprise, fin ou 24 h ; `SyncHost.changed(Dom.XFER)` à chaque changement (1 Hz) ; (2) `R/LinkDiagActivity.kt` « Connexion » (D-pad) : état du beacon (`…0005`, `…0006`, mDNS), téléphones de confiance avec présence et empreinte, identité de la TV (empreinte), routes (IP du moment, Wi-Fi Direct), sessions de synchro ouvertes et âge par domaine, derniers transferts, journal (50 derniers), boutons « Copier le rapport » (→ `/api/link/journal` et presse-papiers TV si disponible), « Auto-test » (`SelfTest` côté TV avec observations TV), « Autoriser les mises à jour » (ouvre `ACTION_MANAGE_UNKNOWN_APP_SOURCES` **depuis cette activité**), « Rendre visible 2 min » ; (3) route `GET /api/link/journal` (PIN ou jeton) et `GET /api/link/state`.

## Pourquoi (preuves)
- `R/BtServer.kt:100-133` (`handle` : `onProgress` → `status(...)`, « transfert interrompu, reprise possible » puis écrasé) ; `C/tv/ReceiverServer.kt` upload classique (`/api/upload`, `.part`) et `/api/transfer/*` (`:595-602`), `C/xfer/TransferHost.kt` (`begin` renvoie `AlreadyThere`, `stateJson`) : points d'accroche.
- `R/PlayerActivity.kt:610-636` (« Connexion & réglages » : liste de statuts, pas de diagnostic), `R/ServerActivity.kt:245-262` (mode « connection » serveur) ; `docs/HANDOFF.md:191` (réglages système depuis un fil HTTP ignorés sur GaiaOS ⇒ ouvrir depuis l'activité).
- `C/link/{TransferLedger,LinkJournal,SelfTest,LinkTexts}.kt` (7a), `R/LinkJournalTv.kt` (w7-12).

## Fichiers possédés
Modifiés : `R/BtServer.kt`, `C/xfer/TransferHost.kt` (additif : `onEvent` ledger pour `/api/transfer`), `tools/routes/routes.txt` (+ `/api/link/*` **et** `/api/sync/*` listées par le rapport de w7-13), `tools/tests/test_routes.py` si besoin. **`C/tv/ReceiverServer.kt` est à w7-14** : le crochet de l'upload HTTP classique (`/api/upload`, 2 lignes `ledger.begin/done`) est demandé par le rapport (`À BRANCHER`) et posé à la fusion ; `R/HomeScreen.kt` est à w7-14 : la ligne `7-xfer` passe par `setStatus` existant. Nouveaux : `R/LinkDiagActivity.kt`, `R/LinkDiagApi.kt` (routes `/api/link/*`), `R/TransferLedgerTv.kt` (instance, `TextStore` `SafeFile` `files/link/xfer.txt`), `android/receiver/src/main/res/layout/activity_link_diag.xml` (si la TV utilise des layouts XML ; sinon Kotlin pur comme `PairActivity`). **Hors zone** : `R/TvService.kt`, `R/TvBeacon.kt`, `R/PlayerActivity.kt` (w7-12), `R/PairActivity.kt`, `C/tv/ReceiverServer.kt` (w7-14), `R/SyncHost.kt` (w7-13).

## Étapes
1. `BtServer.handle` : `val id = ledger.begin(name, total, "Bluetooth", resumeFrom)` à la réception de l'en-tête CBT1 (hook `onBegin` à ajouter dans `BtProtocol.serve` ? **non** : `BtProtocol.kt` est hors zone ⇒ utiliser `onProgress` (premier appel = begin, `done == total` = done) et l'exception = `interrupted`) ; `status("1-bt", ledger.statusLine())`.
2. Upload HTTP classique et `/api/transfer` : mêmes appels ; `abort` ⇒ `abandoned` ; `finish` ⇒ `done` ; redémarrage de la TV ⇒ au démarrage, les `RECEIVING` deviennent `INTERRUPTED` si un `.part`/`.cbx` existe, `ABANDONED` sinon.
3. `LinkDiagActivity` : lecture seule sauf les trois boutons ; focus D-pad, ≥ 24 sp, Retour ferme ; `SelfTest` avec `Observations` TV (btAdapter/btOn/permission, netUp, IPs, mdns publié, sessions, identité) ⇒ une phrase en tête d'écran.
4. Routes `/api/link/journal` (texte, PIN/jeton, redigé), `/api/link/state` (JSON : beacon, sessions, ledger actif).
5. Tests : JVM pour `TransferLedgerTv` (mapping des hooks) ; `:receiver` compile ; émulateur : `curl` upload 20 Mo interrompu (`kill` du curl) ⇒ ligne « Interrompu à N % : reprise possible » reste ; reprise ⇒ « Reçu » ; `am force-stop` en plein transfert ⇒ au redémarrage l'entrée est `INTERRUPTED`.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.link.TransferLedgerTest' --tests 'castbridge.core.xfer.*'
python3 tools/tests/test_routes.py
grep -n 'status("Bluetooth : réception' android/receiver/src/main/kotlin/castbridge/receiver/BtServer.kt | wc -l   # 0 (texte via ledger.statusLine)
```

## Cas limites
Transfert de 0 octet ⇒ refusé avant le ledger (existant `ERR_SIZE`) ; même fichier reçu par Wi-Fi puis Bluetooth ⇒ un enregistrement ; journal vide ⇒ écran « Aucun événement » ; `ACTION_MANAGE_UNKNOWN_APP_SOURCES` absent (< 26) ⇒ bouton caché.

## À ne pas faire
Ne pas modifier `BtProtocol.kt` ni `PartAssembler.kt` ; pas de nouvelle permission ; aucune adresse complète ni jeton dans `/api/link/journal`.

## Rapport
`STATUT`, captures de l'écran Connexion, résultats des essais d'interruption, `À BRANCHER`.
