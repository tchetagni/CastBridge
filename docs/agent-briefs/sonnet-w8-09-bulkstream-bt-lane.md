# w8-09 — Cœur : flux brut CBX (`BulkStream` côté TV, `BulkBtLane` côté téléphone), service RFCOMM « CastBridge Bulk »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w8-04, w8-05, w8-08)
> **Groupe : W8a-4** (vague W8a) · prérequis : w8-04, w8-05, w8-08 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BulkStream*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 8a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après w8-04, w8-05, w8-08).** Conception : § 5.2, 5.3, 5.6, 6.3, 7.4. Branche `claude/sonnet-w8-09`. Rapport : `docs/agent-reports/sonnet-w8-09.md`.

## Objectif
Le vrac sur un **flux d'octets brut** (`castbridge.core.tv.Link` : socket RFCOMM sur Android, tubes mémoire en test), sans HTTP ni tunnel : le téléphone envoie des trames CBX, la TV les écrit dans le `PartAssembler` de la session et acquitte. Même règle d'admission que les fichiers Bluetooth (essai, confiance), même verrou de connexion que les autres services.

## Pourquoi (preuves)
- `BluetoothLane` actuelle = HTTP par tranches à travers un `SocketChannel` du tunnel (`core/xfer/Lanes.kt:149-188`), jamais branchée ; le tunnel v2 sérialise les écritures et limite à 4 flux/256 Ko (`core/tunnel/Mux.kt:87-88, 108-116`) : un vrac y étranglerait l'API (décision D-W8-4, D-W8-7).
- Services RFCOMM existants et codes `ERR_*` (`core/tv/BtProtocol.kt:72-98`) ; `Link` (`BtProtocol.kt:323-326`) ; règle d'essai `acceptFile` (`receiver/BtServer.kt:122`).

## Fichiers possédés
Nouveaux `C/xfer/{BulkStream,BulkBtLane}.kt`, `CT/xfer/BulkStreamTest.kt` ; `C/tv/BtProtocol.kt` (**uniquement** : `const val BULK_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000005"` et un commentaire ; les codes `ERR_*` sont réutilisés tels quels). **Hors zone** : `receiver/BtServer.kt` (w8-11), `Lanes.kt` (w8-02 : `BluetoothLane` y reste en `@Deprecated`, suppression après fusion de 8c).

## Étapes
1. `BulkStream.serve(link: Link, host: TransferHost, peer: String, trusted: Boolean, pinCheck: (String) -> Int?, acceptFile: (String) -> Boolean, now, onProgress)` : lit `HELLO` (identifiant selon la règle CBT1 : `------` ignoré pour un pair de confiance, sinon PIN vérifié et compté par adresse ; `acceptFile(name)` faux → `ERR_TRIAL` + `TrialPolicy.BT_MESSAGE` dans `ERR`), ouvre/retrouve la session (`host.begin` via une fabrique d'allocation passée par l'appelant, comme `ReceiverServer.allocateTransfer`), `openSession`/`addLane`, répond `HELLO_OK` (laneId, encOn, cipher, nonce, carte, writeBps, unité max) ; boucle `CHUNK` → `PartAssembler.writeBlock/writeSlice` (enregistrements si `ENC`) → `CHUNK_ACK` (statut, done, writeBps, queued, retryMs) ; `STATE_REQ` → `STATE` ; `PING` → `PONG` ; `BYE` → `dropLane`, retour. Chien de garde 60 s sans trame (`idle`), 10 min si `busy`. Un thread, lectures `readFully`, écritures par 16-64 Kio, `flush` par trame de réponse seulement.
2. `BulkBtLane(id = "bt", connect: () -> Link, credential: () -> String?, manifest, hashes, …) : Lane` : `kind = BT`, `maxWorkers = 1`, `unitBytes() = 256 Kio` (tranches, reprise au milieu d'un bloc), `health`, `idle(on)` (envoie `PING` toutes les 15 s en veille pour garder la liaison), `send(idx, slice)` → `CHUNK` + attente de `CHUNK_ACK` ; mappe les statuts sur `Outcome` (`already`, `corrupt`, `busy(retryMs)`, `unknown → SessionLost`, `trial → Failed(fatal, refused)`), coupure → `Failed` non fatal (l'ouvrier rouvre par `connect()` fourni par 8c, qui passe par `LinkPool`/`BtConnectLock`). Nonces : `NonceSeq(prefix, laneId)` de la session reçue au `HELLO_OK`.
3. `BulkStreamTest` (tubes mémoire `PipedInputStream` **ou** un `Link` sur deux `ByteArrayOutputStream` croisés, horloge injectée) : transfert complet de 3 Mio par tranches ; `already` ; bloc corrompu renvoyé ; `busy` respecté ; essai → `ERR_TRIAL` et message exact ; PIN faux compté ; chiffré bout en bout (clé du test), rejeu refusé ; coupure en pleine trame → reprise au milieu du bloc avec un nouveau `HELLO` ; trame malformée → liaison fermée ; `PING` en veille.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.BulkStreamTest'   # vert, ≥ 12 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.BtProtocolTest' --tests 'castbridge.core.BtLinkTest'   # inchangés, verts
grep -n "BULK_SERVICE_UUID" android/core/src/main/kotlin/castbridge/core/tv/BtProtocol.kt | wc -l   # 1
```

## Cas limites
Deux `HELLO` sur la même liaison (refus, fermeture) ; `HELLO` pour un transfert inconnu de la TV (elle le crée : même `begin`) ; fichier déjà complet (`HELLO_OK` avec carte pleine, le téléphone envoie `BYE`) ; `CHUNK` d'une voie dont le laneId ne correspond pas au `HELLO_OK` (refus) ; TV sans place (`ERR_SPACE` + message).

## À ne pas faire
Pas de HTTP, pas de tunnel, pas d'Android (`BluetoothSocket` est en 8b/8c) ; ne pas modifier CBT1/CBTN/CBTH/CBTR ; aucun identifiant en journal (`peer` masqué comme `Diagnostics.scrub`).

## Rapport
`STATUT`, machine d'états de `serve` (liste), signature de `BulkBtLane`, ce que w8-11 et w8-14 doivent fournir (`connect`, `acceptFile`, `allocate`), tests avant/après.
