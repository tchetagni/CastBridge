# w6-06 — Téléphones détenteurs (`Holders`), consentement sur la TV, `ParentalPrivacy` (collecté / jamais), routes `holders/*`, option « sans titres »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (s'empile sur w5-18)
> **Groupe : W6a-1** (vague W6a) · prérequis : w5-18 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ParentalHolders*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 6a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w5-18 si fusionné : relire ses champs de `ParentalModel`/`ParentalApi` et s'y empiler).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.1, § 2.2 (liste « jamais »), § 2.6. Branche `claude/sonnet-w6-06`. Rapport : `docs/agent-reports/sonnet-w6-06.md`.

## Objectif
(1) `Holders` : composition autour de `ReportRecipients` (sans le modifier) ajoutant **l'état de consentement** (`PENDING`/`OK`, date, nom du téléphone), l'identifiant de jeton de téléphone de confiance (`tokenId`, hachage) à côté du `phoneId` Bluetooth, la révocation par le téléphone lui-même, les événements `holder_added/removed` ; (2) `ParentalPrivacy` : listes figées `COLLECTED`, `NEVER`, textes français adulte et enfant, `KID_INDICATOR`, `ADULT_INDICATOR(n)` ; (3) `ChildProfile.shareTitles: Boolean = true` et `lateUseAlert: Boolean = false` (additifs, JSON tolérant) ; (4) routes `POST /api/parental/holders/{list,add,remove,consent}` (les `reports/recipients/*` restent des alias) ; `GET /api/parental` ajoute `holders` (nombre) et `kidIndicator` (sans secret) ; (5) `ParentalEvent.HolderChanged` ; tests.

## Pourquoi (preuves)
- `C/parental/ParentalReports.kt:55-125` (`Recipient`, `ReportRecipients` : `designate(pinVerified)`, `remove`, `phoneId`, `MAX = 3`) ; `C/parental/ParentalApi.kt` (routes `reports/recipients/add|remove`, PIN dans le corps seulement, `rev`) ; `C/parental/ParentalModel.kt` (`ChildProfile`, `ParentalConfig.MAX_PROFILES`) ; `C/parental/Supervision.kt:67-75` (`ParentalEvent`) ; `C/trust/TrustRegistry.kt:189-196` (`TvAuth` : forme d'un jeton de téléphone) ; `R/ParentalHub.kt:114-116` (`trustedPhones` injecté dans `ParentalApi`) ; PARENTAL.md § « Qui reçoit ».

## Fichiers possédés
Nouveaux `C/parental/Holders.kt`, `C/parental/ParentalPrivacy.kt`, `CT/ParentalHoldersTest.kt` ; modifiés `C/parental/ParentalApi.kt`, `C/parental/ParentalModel.kt`, `C/parental/ParentalEngine.kt` (champ `shareTitles` dans `report()` : titres remplacés par « Vidéo » quand faux), `C/parental/Supervision.kt` (`ParentalEvent.HolderChanged(added: Boolean, phoneName)`), `CT/Parental*Test.kt` (racine). **Hors zone** : `ParentalReports.kt`, `ParentalSync.kt` (w6-07), `Collect.kt`, `tab/**` (w6-05, w6-08), `R/**`, `S/**`.

## Étapes
1. `Holders(recipients: ReportRecipients, store: KvStore, now)` : `list(): List<Holder(phoneId, tokenId?, name, consent: Consent, since)>` ; `designate(address, name, pinVerified)` ⇒ `PENDING` (délègue à `recipients.designate`, **aucun rapport ne part tant que `consent != OK`** : exposer `activeForReports()` = détenteurs `OK` ∩ `recipients.active()`) ; `consentGiven(phoneId)` (appelé par l'écran TV **seulement**, w6-14) ; `remove(address, pinVerified)` ; `selfRevoke(address)` (sans PIN) ; `bindToken(address, tokenId)` ; `byTokenId(tokenId)` ; `count()`.
2. `ParentalPrivacy` : `COLLECTED` (liste de § 2.2 en français court), `NEVER` (liste de § 2.2), `BLACKLIST_KEYS` (`password, pin, token, key, mac, address, ssid, bssid, url, uri, path, screenshot, text, message, search`) utilisée par le test de w6-07 ; `aboutText(holders: List<Holder>)` ; `consentText(phoneName)` (texte exact de § 2.1) ; `kidIndicator = "Tes parents reçoivent un résumé de ce que fait la TV"`, `adultIndicator(n)`.
3. `ParentalModel` : `shareTitles`, `lateUseAlert` ; `toMap/fromMap` tolérants ; `ParentalEngine.report` respecte `shareTitles` pour `blocked[].what` et pour les titres transmis par le journal (fournir `titleFilter(profileId): (String) -> String`).
4. `ParentalApi` : routes `holders/*` (PIN pour `add`/`remove`, aucun pour `consent` **mais** `consent` est refusée en HTTP : 403 « Le consentement se donne sur la TV » ; la TV l'appelle en direct) ; alias `reports/recipients/*` ; `GET /api/parental` : `holders`, `kidIndicator` ; `GET /api/parental/privacy` (sans secret) renvoie `COLLECTED`/`NEVER`.
5. Événements : `HolderChanged` émis par `Holders` via un `onEvent` injecté (w6-07 le transforme en alerte).
6. Tests : désignation ⇒ `PENDING`, aucun actif ; consentement ⇒ actif ; retrait par PIN / par soi-même / perte de confiance ; 3 max ; `shareTitles = false` ⇒ aucun titre dans `report()` ; routes : PIN dans l'URL refusé (inchangé), `consent` en HTTP ⇒ 403, alias équivalents ; `ParentalPrivacy.NEVER` contient « captures d'écran », « mots de passe », « adresses Bluetooth ».

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.Parental*'   # vert (anciens + nouveaux)
grep -n 'holders/consent' android/core/src/main/kotlin/castbridge/core/parental/ParentalApi.kt   # ≥ 1 (refus 403)
grep -rn 'TvConnect\|ServerLink' android/core/src/main/kotlin/castbridge/core/parental   # 0 hit
```

## Cas limites
Ancien téléphone qui appelle `reports/recipients/add` : désigné `PENDING`, et la réponse contient `"consent":"pending"` + phrase « validez sur la TV » (il l'affichera au mieux) ; TV sans profil : consentement possible (TV-wide) ; un détenteur `PENDING` depuis > 7 j est **oublié** (balayage).

## À ne pas faire
Ne pas modifier `ReportRecipients` (composition) ; ne pas accepter le consentement par HTTP ou Bluetooth ; aucune route serveur ; aucune adresse dans une réponse.

## Rapport
`STATUT`, API (pour w6-07, w6-14, w6-15, w6-18), textes finaux de `ParentalPrivacy` (à relire par w6-21).
