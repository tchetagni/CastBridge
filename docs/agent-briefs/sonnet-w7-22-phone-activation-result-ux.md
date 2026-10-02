# w7-22 — CastBridge (téléphone) et outil propriétaire : résultat d'activation structuré à l'écran, état d'activation lu par la synchro, envoi d'un message `keyring`

**Vague 7c · Effort S (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT (après 7a/7b ; en parallèle de w7-18 : contrat `DomainStores.act`, `SyncClient.events`).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 2 (problèmes 1, 2), D-W7-4. Branche `claude/sonnet-w7-22`. Rapport : `docs/agent-reports/sonnet-w7-22.md`.

## Objectif
(1) `ActivateTvActivity` : l'état d'activation de la TV vient de `DomainStores.act(tv)` + `SyncClient.events` (plus de sondage `GET /api/activation` toutes les 4 s **quand la synchro est `LIVE`** ; repli existant en `SIMPLE`) ; l'envoi par Bluetooth lit `ResultV2` (w7-07) et affiche **titre + action** (« Mettre à jour CastBridge-TV », « Utiliser l'outil de bureau », « Vérifier le code d'appareil », « Accepter les conditions sur la TV », « Valider sur la TV ») avec le `kid` tronqué et la version TV ; (2) `OL/TvBluetooth.kt` : `sendActivation` renvoie `ResultV2` (texte v1 toujours rempli) ; nouvelle `sendKeyring(token)` ; (3) `OL/ConsoleActivity.kt` (console propriétaire) : bouton « Ajouter ma clé à cette TV » qui émet un `keyring` signé par la clé du coffre **si** elle porte `REGISTRY` (`OwnerStore.publicLine` liste les portées : `REGISTRY` y est), l'envoie par `…0005`, et affiche le résultat ; sinon message « Votre clé n'a pas la portée REGISTRY : utilisez l'outil de bureau » ; (4) textes dans `ActivationScreenState` (déjà w7-07) — rien d'écrit ici.

## Pourquoi (preuves)
- `S/ActivateTvActivity.kt:56-75` (sondage LAN toutes les 4 s, écran ouvert seulement), `:78-88` (`sendBluetooth` ⇒ `sendOutcome(r.ok, r.message)`), `:156` (texte rouge sans action) ; `OL/TvBluetooth.kt:112-133` (`with`), `C/owner/OwnerChannel.kt:61-64` (`OwnerChannelClient.sendActivation`) ; `OL/ConsoleActivity.kt:150` (« Refusée par la T… ») ; `OL/OwnerStore.kt:53-54` (`publicLine` avec `REGISTRY`).
- `C/owner/{OwnerFrames(ResultV2),Keyring,ActivationScreenState}.kt` (w7-07), `S/link/{DomainStores,SyncClient}.kt` (w7-18).

## Fichiers possédés
Modifiés : `S/ActivateTvActivity.kt`, `OL/TvBluetooth.kt`, `OL/ConsoleActivity.kt`, `C/owner/OwnerChannel.kt` (**seulement** `OwnerChannelClient.sendActivation` → `ResultV2` et nouveau `sendKeyring` ; le serveur est à w7-07, déjà fusionné). **Hors zone** : `S/link/**`, `C/owner/Keyring.kt`.

## Étapes
1. `OwnerChannelClient.sendActivation(): ResultV2` (décodage v1/v2 par `OwnerFrames.decodeResult`), `sendKeyring(token): ResultV2`, `knock(name, pub): ResultV2`.
2. `ActivateTvActivity` : `LaunchedEffect` sur `SyncClient.events` (ActChanged) + `DomainStores.act` ; sondage existant **seulement** si `SyncClient.state(tv) !is LIVE` ; après un envoi accepté, l'écran attend `ActChanged` (plus de `refresh()` aveugle) et affiche « La TV a validé la clé » dès que `act.state == production` ; résultat : `ActivationScreenState.sendOutcome(r)` ⇒ titre, corps, bouton d'action (`UPDATE_TV` ⇒ ouvre l'écran de mise à jour TV existant si présent, sinon texte ; `USE_DESK_TOOL` ⇒ texte + copie du `kid` ; `CHECK_DEVICE_CODE` ⇒ « Demander la clé de production » existant ; `ACCEPT_TERMS_ON_TV`/`CONFIRM_ON_TV` ⇒ texte).
3. Console : émission `Keyring.build(add = [ma clé publique, portées = celles de ma clé sauf REGISTRY], seq = journal)` via `LicensedIssuer`/`OwnerStore` existants (signature Ed25519 déjà disponible dans la console : `ActivationIssuer`) ; journal du coffre `envoi-keyring`.
4. Tests : `CT/owner/OwnerChannelClientTest` (faux serveur v1 et v2) ; `:sender` et `:ownerlib` compilent ; appareil : clé inconnue ⇒ message avec `kid` + bouton ; `keyring` envoyé ⇒ la TV accepte ensuite l'activation (`knownKids` contient le `kid`).

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin && gradle --offline :ownerlib:compileDebugKotlin && gradle --offline :core:test --tests 'castbridge.core.owner.*'
grep -n 'delay(4_000)' android/sender/src/main/kotlin/castbridge/sender/ActivateTvActivity.kt | wc -l   # ≤ 1 et seulement sous condition SIMPLE
grep -n 'Refusée par la T' android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt | wc -l   # 0 (texte via ActivationScreenState)
```

## Cas limites
TV ancienne (RESULT v1) ⇒ `reason=null`, texte brut, action `NONE` + conseil générique « mettez CastBridge-TV à jour » ; `keyring` refusé (`KEY_NOT_ALLOWED`) ⇒ texte exact ; coffre verrouillé ⇒ bouton grisé.

## À ne pas faire
Pas de nouveau texte hors `ActivationScreenState`/`LinkTexts` ; ne pas modifier le serveur `OwnerChannelServer` ; aucune clé privée hors du coffre.

## Rapport
`STATUT`, captures (refus avec action, keyring accepté), signatures.
