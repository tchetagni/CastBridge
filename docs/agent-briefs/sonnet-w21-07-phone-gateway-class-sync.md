# w21-07 — CastBridge (téléphone) : classe cellulaire grossière de la passerelle Bluetooth (sans permission), métriques `sync.pair` (R-10) et `sync.xfer` (R-09/R-12/R-17), Wi-Fi Direct côté téléphone
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (aucune permission nouvelle, aucun identifiant de téléphone, champ additif de la passerelle) · statut : **ATTEND w21-01**
> **Groupe : W21-B** (ordre 3) · porte : `:core:test --tests 'castbridge.core.telemetry.*' --tests 'castbridge.core.gateway.*'` + `:sender:testDebugUnitTest` (par `tools/agents/gradle-lock.sh`)
> **Jauge : ≈ 250 k jetons entrée / 15 k sortie** (effort S, ≈ 0,8 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 2 (M-24, M-25, M-28, M-29, M-32, M-45), § 3.6 (classe cellulaire). Branche `claude/w21-07-phone-tech`. Rapport : `docs/agent-reports/sonnet-w21-07.md`.

## Objectif (autonome)
La TV jouant en ligne par la passerelle Bluetooth du téléphone ne sait pas si le téléphone est en EDGE, 3G, 4G ou 5G. Le téléphone (`android/sender/src/main/kotlin/castbridge/sender/BtGatewayService.kt`, qui émet déjà `gateway_session`) peut l'estimer **sans permission nouvelle** : `ConnectivityManager.getNetworkCapabilities(activeNetwork)` ⇒ `TRANSPORT_CELLULAR` + `getLinkDownstreamBandwidthKbps()`. Le transmettre à la TV par un **champ additif** de l'attache de la passerelle (côté TV : `android/receiver/src/main/kotlin/castbridge/receiver/BtGatewayHost.kt`, lecture posée par w21-04 ; absent ⇒ `unk`). Ajouter les évènements téléphone `sync.pair`, `sync.xfer`, `link.wd` (côté téléphone), mêmes portes que la TV (consentement usage **et** `pocMetrics` de la directive du heartbeat du téléphone).

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/gateway/CellClass.kt` (pur : `fromBandwidth(kbps: Int?, cellular: Boolean)` ⇒ `2g` < 150, `3g` < 2 000, `4g` < 50 000, `5g` sinon, `unk` si inconnu ou non cellulaire) ; `android/sender/src/main/kotlin/castbridge/sender/PhoneTech.kt` (fabrique de `TechMetrics` côté téléphone) ; tests `android/core/src/test/kotlin/castbridge/core/gateway/CellClassTest.kt`, `android/sender/src/test/kotlin/castbridge/sender/PhoneTechTest.kt`.
- **Zone additive** : `BtGatewayService.kt` (calcul de la classe à l'attache et sur `onCapabilitiesChanged`, envoi par la trame/le message d'attache existant : champ `cell` ∈ {`2g`,`3g`,`4g`,`5g`,`unk`} ; à localiser par l'exécutant, en respectant la règle de symbiose W19 : additif, ignoré par une TV ancienne) ; `PinStore.kt` / point d'association (issue `sync.pair` : `token_renewed`, `pin_reused`, `pin_asked_once`, `pin_asked_again`, `locked` ; `tvs` = 1, 2, 3+) ; `TransferQueue.kt` / point d'état final (`sync.xfer` : état final, code `Reason`, réessais, reprises, doublon évité, octets repris, durée, débit, canal) ; point Wi-Fi Direct du téléphone (`link.wd`) s'il existe ; `PhoneConnect.kt` (passage du flush par `UploadGate`, sans partie Internet côté téléphone : seules les règles « copie active » et voie s'appliquent).
- **Interdit** : écrans et chaînes affichées (`*Screen.kt`, `*Card.kt` hors lecture), `AndroidManifest.xml` (**aucune** permission), `R/` (sauf la lecture de `cell` si w21-04 ne l'a pas posée : alors coordination avec w21-04, pas de double modification), `backend/`, `server-play/`.

## Étapes
1. **Rouge** (sortie collée) : `CellClassTest.edgeBandwidthIs2g`.
2. `CellClass` + tests de bornes.
3. Champ additif `cell` dans l'attache de la passerelle ; test de compatibilité : un téléphone nouveau avec une TV ancienne (champ ignoré) et l'inverse (absent ⇒ `unk`).
4. `PhoneTech` : objet nul hors porte ; `sync.pair`, `sync.xfer`, `link.wd`.
5. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `CellClassTest` : 100 kbps ⇒ `2g`, 1 000 ⇒ `3g`, 20 000 ⇒ `4g`, 200 000 ⇒ `5g`, `null` ⇒ `unk`, Wi-Fi ⇒ `unk` (mutation : borne 150 ⇒ 1 500 ⇒ échec).
- `PhoneTechTest.offWithoutCohortOrConsent` : 0 évènement technique sans `pocMetrics` ou sans consentement (mutation).
- `PhoneTechTest.pinAskedAgainIsCounted` : scénario R-10 (deux TV, un 401 de la TV B) ⇒ aucune `pin_asked_again` pour la TV A.
- Manifeste : **aucune** permission ajoutée (test qui compare la liste des `uses-permission` avant/après, ou vérification consignée).
- Aucun identifiant de téléphone (ni opérateur, ni IMEI, ni identifiant de cellule, ni nom de réseau) dans aucun évènement (test sur la liste des propriétés émises).

## Interdits
`READ_PHONE_STATE` et toute autre permission ; `TelephonyManager` ; nom d'opérateur ; aucune sonde active.

## Rapport
Rouge, vert, mutations, emplacement exact du champ additif, comportement observé sans `getLinkDownstreamBandwidthKbps` (version d'Android minimale de l'app).
