# w8-11 — CastBridge-TV : cinquième service RFCOMM « CastBridge Bulk » (`BtBulkBridge`), ligne d'état, diagnostic Bluetooth

**Vague 8b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w8-09).** Conception : § 5.3, 5.6, 7.4, 11. Branche `claude/sonnet-w8-11`. Rapport : `docs/agent-reports/sonnet-w8-11.md`.

## Objectif
Servir `BulkStream` (8a) sur un socket RFCOMM **sécurisé** dédié, avec exactement les règles d'admission des autres services (appairés seulement, téléphone de confiance → identifiant ignoré, essai → `ERR_TRIAL`), un chien de garde propre, et une ligne d'état lisible sur la TV.

## Pourquoi (preuves)
- `BtServer` ne sert que le service …0001 (`receiver/BtServer.kt:77, 137`) ; le tunnel a son propre pont (`receiver/BtTunnelBridge.kt:19-25`) : copier ce modèle, pas `BtServer.handle`.
- Règle d'essai : `acceptFile = { n -> !ActivationCenter.trial() || TrialPolicy.btFileAllowed(n) }` (`BtServer.kt:122`) ; confiance : `trusted` (`BtServer.kt:34`).
- `TvService` démarre les services Bluetooth (`receiver/TvService.kt:216`) et fournit l'allocation de volume par `ReceiverServer` (`allocateTransfer`, `ReceiverServer.kt:630-646`) : exposer une méthode publique `allocateFor(manifest, target)` **si** elle n'existe pas (ligne unique, à coordonner avec w8-10 par le rapport : w8-10 possède `ReceiverServer.kt` ; demander l'accroche, ne pas éditer le fichier).

## Fichiers possédés
`R/BtServer.kt` (crochet : état `bulk` dans `stateJson`), nouveau `R/BtBulkBridge.kt`, `R/TvService.kt` (**3 lignes** : création, démarrage, arrêt du pont), `docs/ADMIN.md` (§ Bluetooth : le nouveau service). **Hors zone** : `C/**` (8a), `ReceiverServer.kt` (w8-10), `BtTunnelBridge.kt` (lecture seule, modèle).

## Étapes
1. `BtBulkBridge(ctx, host: TransferHost, allocate, guard: PinGuard, trusted, acceptFile, status)` : `listenUsingRfcommWithServiceRecord("CastBridge Bulk", BULK_SERVICE_UUID)`, 2 liaisons au plus (sémaphore), un thread par liaison → `BulkStream.serve(link, host, peer, trusted(peer), pinCheck = guard, acceptFile, …)` ; chien de garde 60 s / 10 min si occupé ; verrou de réveil tenu pendant une liaison (comme `BtServer.busy`).
2. Ligne d'état : `setStatus("1-bt", "Réception : 0,2 Mo/s par Bluetooth · <nom> 37 %")` toutes les 2 s au plus, « Bluetooth : prêt » à la fin ; jamais l'adresse du pair.
3. `GET /api/bluetooth` (`BtServer.stateJson`) : ajouter `"bulk":{"listening":…,"active":n,"lastError":…}`.
4. Pas de permission nouvelle (`BLUETOOTH_CONNECT` déjà déclaré) ; `start()` ignore proprement l'absence d'adaptateur/permission (même texte que `BtServer.start`).
5. Essai sur la **TV de référence** (le propriétaire, ou l'agent s'il a `adb`) : service visible dans le SDP (`sdptool`/journal), liaison depuis un téléphone 8c (ou l'outil de bureau si w8-18 fournit un client CBX : sinon dire « non essayé »).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile
grep -n "BULK_SERVICE_UUID" android/receiver/src/main/kotlin/castbridge/receiver/BtBulkBridge.kt | wc -l   # ≥ 1
grep -n "remoteDevice.address" android/receiver/src/main/kotlin/castbridge/receiver/BtBulkBridge.kt | grep -v "peer" | wc -l   # 0 (l'adresse sert au pair, jamais au journal)
```
Observable sur TV : `GET /api/bluetooth` montre `bulk.listening=true` ; en essai, un transfert de média par ce service répond `ERR_TRIAL` avec `TrialPolicy.BT_MESSAGE` et la ligne d'état le dit.

## Cas limites
`listen` qui échoue (UUID déjà pris par un ancien processus) → statut clair, pas de boucle ; pair non appairé (socket sécurisé : refusé par Android avant `accept`) ; liaison qui tombe pendant `finish` du transfert HTTP (le `TransferHost` est partagé : la session reste, le téléphone finit par une autre voie).

## À ne pas faire
Ne pas passer par le mux v2 ni par `BtServer.handle` ; ne pas dupliquer la logique CBX (elle est dans `core`) ; ne pas journaliser le contenu des trames ni l'adresse.

## Rapport
`STATUT`, accroche demandée à w8-10, essai matériel (fait / non fait, journal masqué), compilation.
