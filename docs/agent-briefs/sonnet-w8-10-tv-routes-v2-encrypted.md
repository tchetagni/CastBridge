# w8-10 — CastBridge-TV : routes `/api/transfer` v2 (session, voies, corps chiffré, stats), `caps` v2, `/stream` sur la carte de blocs

**Vague 8b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après fusion 8a).** Conception : § 5.1, 5.4, 5.5 (SO_RCVBUF), 6.1, 6.4, 8.2, 8.3, 11. Branche `claude/sonnet-w8-10`. Rapport : `docs/agent-reports/sonnet-w8-10.md`.

## Objectif
Brancher sur HTTP ce que 8a a livré en pur : négociation v2, session chiffrée, identifiants de voie, corps de bloc en enregistrements AEAD, statistiques par voie dans `state`, lecture `/stream` d'un fichier qui arrive **dans le désordre**. **Les routes v1 restent strictement identiques** (un téléphone d'aujourd'hui ne doit rien voir).

## Pourquoi (preuves)
- `ReceiverServer.transfer` (`core/tv/ReceiverServer.kt:594-604`), `transferChunk` (`648-672`), `caps` (`596`), `stream` (`1130-1163`) sur `GrowingStream`.
- Garde d'essai et refus `403 trial` avant toute route (`279-280`) : rien à faire, à **tester**.
- `createClientHandler` (`ReceiverServer.kt:84`) : endroit pour `SO_RCVBUF`.

## Fichiers possédés
`C/tv/ReceiverServer.kt` (§ transfert + `createClientHandler` + `stream` seulement), `CT/MultipathServerTest.kt`, nouveau `CT/xfer/ServerV2Test.kt`, `tools/routes/routes.txt`, `tools/tests/test_routes.py`. **Hors zone** : `TransferHost`/`PartAssembler` (8a, utiliser), `BtServer.kt` (w8-11), tout fichier `sender/`.

## Étapes
1. `GET /api/transfer/caps` → `{"version":2,"maxStreams":…,"slice":…,"encrypt":"on|degraded|off","cipher":"aes-gcm|chacha","bulkBt":true,"usbIp":[…]}` ; `encrypt`/`cipher` à partir de `CipherPick.measure()` **une fois** (mis en cache dans les préférences du `device`, pas à chaque requête : utiliser le crochet `device` existant du serveur, `ReceiverServer.kt:242`) et de `writeBps` du volume cible ; `usbIp` vide tant que w8-12 n'a rien (clé présente mais liste vide).
2. `POST /api/transfer/session?id=` corps JSON `{nonce, enc}` (≤ 1 Kio) → `TransferHost.openSession` avec `ikm` = jeton présenté dans `X-CB-Token` **si** la requête est authentifiée par jeton (PIN → `encOn=false`) ; réponse `{session, nonceTv, encOn, cipher}`. `POST /api/transfer/lanes?id=&session=` corps `{kind}` → `{laneId}` ; `DELETE /api/transfer/lanes?id=&lane=`.
3. `PUT /api/transfer/chunk` : si `X-CB-Enc: cbx1` et `X-CB-Lane` présents : corps = enregistrements AEAD (`RecordInputStream` de la session, fenêtre anti-rejeu de la voie) ; `X-CB-Sha256` reste l'empreinte du **clair** ; réponse inchangée + `laneId`. Sans ces en-têtes : chemin v1 **inchangé**.
4. `GET /api/transfer/state` : `TransferHost.stateJson` v2 (voies, `contiguous`, `encOn`).
5. `/stream/<name>` : si un transfert multivoie est en cours pour ce nom (`transfers.hasName`), servir par `SparseGrowingStream` (w8-03) sur `.cbx/<id>.data` avec la carte ; sinon chemin actuel. `Range` identique.
6. `createClientHandler` : `finalAccept.receiveBufferSize = 512 Kio`, `tcpNoDelay = true` (en `runCatching`).
7. `routes.txt` : ajouter `POST /api/transfer/session`, `POST /api/transfer/lanes`, `DELETE /api/transfer/lanes` (marquées fermées en essai) ; `test_routes.py` vert.
8. Tests `ServerV2Test` (boucle locale, comme `MultipathServerTest`) : caps v2 ; session avec jeton → chiffré de bout en bout (client de test qui chiffre avec `AeadRecords`) ; session avec PIN → clair ; rejeu refusé (422/409 avec code `replay`) ; voie inconnue → 400 ; téléphone v1 (sans en-têtes) → comportement identique au test existant ; essai → 403 `trial` sur `session` et `chunk` ; `/stream` progressif pendant un transfert dans le désordre (bloc 1 avant bloc 0 : la lecture attend puis continue) ; deux sessions → `maxStreams` par session.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.ServerV2Test' --tests 'castbridge.core.MultipathServerTest'   # vert (18 anciens + ≥ 10 nouveaux)
python3 -m unittest tools/tests/test_routes.py   # vert
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (si le SDK est disponible ; sinon le dire)
grep -c "api/transfer" tools/routes/routes.txt   # ≥ 9
```

## Cas limites
`X-CB-Enc: cbx1` sans session ouverte → 400 ; session d'un autre transfert → 403 ; corps chiffré plus long que le bloc (enregistrements en trop) → 400, connexion fermée (`Connection: close` existant) ; volume retiré pendant un chunk chiffré → 503 comme aujourd'hui ; `/stream` d'un nom dont le transfert vient de finir (renommage) → bascule par nom (`SparseGrowingStream` le gère).

## À ne pas faire
Ne pas changer une seule réponse v1 (le test existant est la preuve) ; ne pas toucher à l'authentification (`denied`, `routeGuard`) ; aucun jeton en journal ; pas de nouvelle route hors des trois listées.

## Rapport
`STATUT`, JSON exacts de `caps`/`session`/`lanes`/`state` (copiés), ce que w8-12 doit remplir (`usbIp`), tests avant/après, compilation receiver oui/non.
