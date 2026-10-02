# w6-15 — CastBridge-TV : tirage des rapports par Wi-Fi local (`/api/parental/holder/*`, identité par jeton de téléphone de confiance), CBTP v2 dans `BtServer`, routes parentales interdites au tunnel

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w6-06, w6-07)
> **Groupe : W6c-1** (vague W6c) · prérequis : w6-06, w6-07 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*HolderHttp*' && python3 -m unittest discover -s tools/tests -p 'test_routes.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 6c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w6-06, w6-07 fusionnés).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.7, § 2.6 (séparation tunnel), § 7 (2). Branche `claude/sonnet-w6-15`. Rapport : `docs/agent-reports/sonnet-w6-15.md`.

## Objectif
(1) `C/parental/HolderHttp.kt` (pur, testé) : `hello`, `pull`, `ack`, `snapshot` sur un `ReportSyncHost` existant, l'appelant fournissant **l'identité du téléphone** (`tokenId` du jeton `X-CB-Pin` présenté, résolu par la TV) ; (2) `ParentalApi` : routes `POST /api/parental/holder/{hello,pull,ack,snapshot}` (pas de code parental ; 403 explicite si non désigné / consentement en attente ; un PIN de connexion **ne suffit pas** : il faut un jeton de téléphone de confiance pour que la TV sache **qui** tire) ; (3) `BtServer` : CBTP v2 (requête `{"v":2,"want":…}`) via `ParentalSyncProtocol` de w6-07 (la colle lit déjà `CBTP` : vérifier qu'elle passe le `peer` et laisse le protocole lire `want`) ; (4) `TunnelHub`/`TvService` : **liste noire** `/api/parental/**` pour toute requête venant du tunnel d'assistance (si le tunnel atteint l'API HTTP de la TV : vérifier `R/TunnelHub.kt` et dire comment ; sinon test négatif documenté) ; (5) `routes.txt` : nouvelles routes classées (essai : ouvertes, comme `/api/parental` ; réduit : ouvertes).

## Pourquoi (preuves)
- `C/parental/ParentalSync.kt:25-49` (`ReportSyncHost.fetch(peer)/ack` : le `peer` est une adresse Bluetooth normalisée ; pour HTTP, w6-06 `Holders.byTokenId` fait le pont) ; `R/TvService.kt:216` (`BtServer(... parental = ParentalHub.syncHost)`) ; `R/BtServer.kt` (lecture de `CBTP`) ; `C/trust/TrustRegistry.kt:189-196` (`TvAuth`), `TvService.trust`/`btTrusted` (comment la TV reconnaît un jeton de téléphone : `grep -n "fun btTrusted\|trust\." R/TvService.kt`) ; `R/TvService.kt:258` (garde de routes essai/réduit) ; `tools/routes/routes.txt` (w1-06) ; `C/parental/ParentalApi.kt` (w6-06 : alias, PIN dans le corps).

## Fichiers possédés
Nouveaux `C/parental/HolderHttp.kt`, `CT/parental/HolderHttpTest.kt` ; modifiés `C/parental/ParentalApi.kt`, `R/BtServer.kt`, `R/TunnelHub.kt` (liste noire), `tools/routes/routes.txt`, `tools/tests/test_routes.py` (si une classification nouvelle est à tester). **Hors zone** : `R/TvService.kt`, `R/ParentalHub.kt` (w6-13 ; si `ParentalApi` a besoin d'un résolveur `tokenId -> phone`, le prendre par le constructeur de `ParentalApi` : w6-13 câble `trustedPhones`, qui existe déjà :114-116 ; sinon demander dans le rapport), `R/ParentalActivity.kt` (w6-14), `C/parental/ParentalSync.kt`, `ParentalReports.kt` (w6-07).

## Étapes
1. `HolderHttp(host: ReportSyncHost, holders: Holders)` : `hello(tokenId)` ⇒ `{v:2, you:{id, designated, consent}, installPub?, kid?, key?}` ; `pull(tokenId)` ⇒ `{reports:[…]}` (seulement `consent == ok`) ; `ack(tokenId, ids, keyStored, installKeyStored)` ; `snapshot(tokenId)` (1/min, journalisé via le rappel de w6-07) ; `tokenId` inconnu ⇒ `Refused(403, "Ce téléphone n'est pas désigné pour les rapports")`.
2. `ParentalApi` : routes ; l'identité vient d'un paramètre `callerTokenId: String?` que la TV passe (le serveur HTTP de la TV connaît le jeton qui a authentifié la requête : le trouver dans `ReceiverServer`/`TvService` et le **lire**, pas le réécrire ; si l'API d'extension ne le transmet pas, proposer l'ajout minimal à w6-13 dans le rapport et coder contre une interface).
3. `BtServer` : v2 ; un ancien téléphone ⇒ réponse v1 (tests de w6-07 déjà verts : ici, vérifier la colle).
4. Tunnel : refus 403 « Les données parentales ne sont jamais accessibles à l'assistance à distance » ; test de la liste noire (JVM si la garde est dans `C/`, sinon test de colle décrit dans le rapport).
5. `routes.txt` + test.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.parental.HolderHttpTest' --tests 'castbridge.core.Parental*'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin   # compile
python3 -m unittest discover -s tools/tests -p 'test_routes.py'   # vert (chaque route classée)
grep -n 'holder/pull' tools/routes/routes.txt   # ≥ 1
```
Observable : téléphone désigné + consentement ⇒ `POST /api/parental/holder/pull` avec son jeton ⇒ rapports ; avec le PIN de connexion seul ⇒ 403 ; téléphone non désigné ⇒ 403 avec la phrase.

## Cas limites
Deux téléphones avec le même nom : identités distinctes (jeton) ; jeton révoqué ⇒ plus détenteur (`Holders` suit `recipients.active()`) ; TV en essai : routes ouvertes (le parental n'est pas une fonction payante) ; TV verrouillée (`Locked`) : `/api/parental` est-il joignable ? (`LOCKED_WHITELIST` ne contient pas `PARENTAL`) ⇒ non, et c'est voulu : le dire.

## À ne pas faire
Pas de code parental pour tirer ; jamais d'adresse ni de jeton dans une réponse ; ne pas éditer `TvService.kt`/`ParentalHub.kt` ; aucune route serveur.

## Rapport
`STATUT`, routes exactes et corps (pour w6-18), où le jeton appelant est lu, résultat du test tunnel.
