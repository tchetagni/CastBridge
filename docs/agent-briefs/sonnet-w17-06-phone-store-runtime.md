# w17-06 — CastBridge (téléphone) : `StoreRuntime` (catalogue des bouquets signé, cache, relais des catalogues à la TV à chaque contact, relevé des demandes de la TV, notification, confirmation adulte, exécution par le chemin W16)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (relais de documents signés ; confirmation d'une demande) · statut : **ATTEND la sortie du gel** (plan de stabilisation § 5-6 ; exception possible D-W17-11) et w15-16 fusionné (`S/LotsRuntime.kt` zone `hasWork`)
> **Groupe : W17c-1** (vague W17c, téléphone) · prérequis : w17-01…05 fusionnés ; w16-11 **souhaité** (`PilotClient`, `RentalRuntime`) sinon exécution « fichier/texte partagé » (tranche 1 W16) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.Store*' --tests 'castbridge.core.lint.*' && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 17c · Effort M · Modèle : sonnet · Statut ATTEND.** Conception : DESIGN-W17 § 2.6, § 4.2, § 5, § 7.3. Branche `claude/sonnet-w17-06`. Rapport : `docs/agent-reports/sonnet-w17-06.md`. Ce cahier **câble** en Android ce que `CT/journey/PhoneStoreSim.kt` (w17-05) fait en JVM : **mêmes appels, même ordre** ; aucune décision dans le code Android.

## Pourquoi (preuves)
- `S/LotsRuntime.kt:133` (`keys()`), `:180-188` (`refreshCatalog` : catalogue de lots gardé `sp["catalog"]`), `:242-268` (`deliver` : **le** point de contact avec la TV, zone w15-16 : **une ligne proposée au rapport**, jamais éditée ici), `:292-304` (`LotsSyncJob` 12 h), `:213-220` (`transport()` : HTTP ou CBT1).
- `C/lots/ServerBundleCatalog.kt:14` (`fetch(baseUrl, keys, notOlderThan)`), `S/PhoneConnect` (`state.baseUrl`, `deviceToken`, consentement) ; `S/OrdersRuntime.kt` (patron d'une exécution avec cache et tâche).
- `C/store/StoreApi.kt` routes (w17-04) ; `C/store/RentRequest.kt` (w17-03) ; `C/lots/RentalDelivery.kt:96` (`deliver`) ; `S/RentalDeliveryActivity.kt:49-53` (`HttpTvTransport(session.base, session.credential)`).
- Notification : `C/xfer/XferTexts.kt` (W14 : **une** source de texte de notification) ⇒ ajouter le texte de la demande dans `StoreTexts` (w17-02, déjà là : `REQUEST_NOTIFICATION`) ; `S/UploadService.kt:272` (canal de notification existant : réutiliser le canal, pas en créer un).

## Fichiers possédés
Nouveaux `S/store/StoreRuntime.kt`, `S/store/StoreNotifier.kt` (notification « La TV demande : louer CM2 · 12 heures d'utilisation » → ouvre `StoreActivity`, w17-07 : si absent, ouvre `MainActivity`), `S/store/StoreSyncHook.kt` (fonction `onTvContact(transport)` appelée par la ligne proposée dans `LotsRuntime.deliver`) ; `S/CastBridgeApp.kt` (**une ligne** : `StoreRuntime.init(this)`). **Hors zone** : `S/LotsRuntime.kt` (w15-16 ; ligne proposée au rapport), `S/store/StoreScreen.kt` (w17-07), `S/RentalPickerScreen.kt`, `S/RentalRuntime.kt` (w16-11), `C/**`, `R/**`.

## Étapes
1. `StoreRuntime.init(ctx)` : `files/store/bundles-catalog.json` (cache), `generatedAt` gardé ; `refreshBundles()` = `ServerBundleCatalog.fetch(PhoneConnect.state.baseUrl, keys(), notOlderThan)` (Wi-Fi ou données selon `wifiOnly`, consentement exigé comme `syncNow:150-152`) ; appelé par `LotsSyncJob` **via** `StoreSyncHook.onSync()` (ligne proposée) et par « Actualiser » de l'écran ; message FR du résultat (`Refused.message`).
2. `store(): Store` = `StoreCatalog.build(LotsRuntime.catalog?.lots, bundles, families)` ; `families` = `LotFamilies.explicit(free, reserved)` lues du catalogue de lots (`family` si le champ existe, sinon `langues` ⇒ `FREE`, `learn`/`quiz` ⇒ `RESERVED` : **documenter** cette hypothèse) ; `facts()` : `phoneStages` = `LotsRuntime.status(id).stage` par lot, `tvRentals` = dernier `TvRentalView` (cache), `tvKnown`, `tvReachable` = `LotsRuntime.tvReachable()`, `kidActive`/`trialTv` = derniers `GET /api/store`, `pendingRequests`.
3. `onTvContact(t: TvTransport)` (après les lots, jamais pendant un envoi de fichier : `transferToTvRunning()`) : `GET /api/store` ⇒ si 404 : rien (drapeau éteint sur la TV, dit dans l'écran) ; si `catalogAt` plus ancien ⇒ `POST /api/store/catalog` `{lots, bundles}` ; `GET /api/store/requests` ⇒ nouvelles `PENDING` ⇒ `StoreNotifier` (une notification par demande, regroupée au-delà de 3) ; demandes `ACCEPTED` chez nous et contrat vu dans `GET /api/rental` ⇒ rien (la TV rapproche elle-même).
4. `confirm(request)` (depuis l'écran w17-07, adulte : si un code parental est configuré sur le téléphone, `S/ParentalScreen` : **lecture** de l'existence du code, la saisie est l'écran existant) ⇒ `ack ACCEPTED` sur la TV **puis** exécution : si `RentalRuntime` (w16-11) existe ⇒ `RentalRuntime.order(request)` ; sinon ⇒ fichier `demande-<alias>-<date>.txt` (texte canonique) + partage système (`ACTION_SEND`) et phrase « Envoyez ce fichier à CastBridge ; la location arrivera par “Locations sur la TV” » ; `refuse(request)` ⇒ `ack REFUSED`.
5. Demande née sur le téléphone (`origin=phone`, depuis le sélecteur W16 ou l'écran w17-07) : `POST /api/store/request` si la TV est à portée (D-W17-8), sinon gardée localement et déposée au prochain contact ; exécution identique à 4.
6. Lint de pureté : aucun texte décidé ici (tout `StoreTexts`) ; `compileDebugKotlin` ; parcours J `Store*` verts (ils testent le cœur ; ce cahier n'en ajoute pas) ; fumée W14 `--tv fake` **PASS** avec `FakeTvMain` muni de `StoreApi` (w14-10 : si `FakeTvMain` ne charge pas `StoreApi`, ajouter l'extension est **hors zone** : le dire au rapport et joindre le rapport F sans la vitrine).

## Critères d'acceptation
Porte verte ; `compileDebugKotlin` vert ; lint de pureté vert ; `grep -rn '"' android/sender/src/main/kotlin/castbridge/sender/store/StoreRuntime.kt | grep -v "StoreTexts\|Log\.\|\.json\|files/\|api/" ` ne montre aucune phrase française en dur ; rapport F joint ; ligne à ajouter dans `LotsRuntime.deliver` et `LotsSyncJob` écrite au rapport (avant/après).

## Cas limites
TV à portée en Bluetooth seulement (`Cbt1LotTransport`) : `GET /api/store` par le tunnel API Bluetooth si disponible (`TvClient` existant), sinon relais **reporté** au prochain Wi-Fi (jamais un échec visible) ; téléphone sans consentement ⇒ pas de téléchargement du catalogue (phrase existante) mais relais de ce qui est en cache **autorisé** ; serveur sans catalogue des bouquets (404) ⇒ mode dégradé, phrase « Le serveur n'a pas encore de catalogue des bouquets » ; deux TV enregistrées ⇒ la TV par défaut seulement (comme les lots).

## À ne pas faire
Ne pas éditer `LotsRuntime.kt` ; pas d'appel HTTP direct hors `ServerBundleCatalog` et `TvTransport` ; aucune décision d'état ; pas de prix ; pas de nouveau canal de notification ; ne pas lancer `adb`.

## Rapport
`STATUT`, lignes proposées (hors zone), rapport F, captures textuelles de la notification, question : exception D-W17-11 accordée ou attente de la sortie du gel ?
