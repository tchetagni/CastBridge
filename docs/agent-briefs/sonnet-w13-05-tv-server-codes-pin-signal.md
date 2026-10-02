# w13-05 — TV (cœur serveur) : champ `code` dans les corps d'erreur, PIN absent non compté dans le verrou, signal « un téléphone attend le code », `PinNeededPolicy`, `/api/link/blockers`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (authentification, verrou PIN, fuite d'information) · statut : PRÊT
> **Groupe : W13-b** (vague W13, tranche S1) · prérequis : w13-01 fusionné (noms `wire`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Security*' --tests '*Server*' --tests '*Trial*' --tests 'castbridge.core.link.PinNeededPolicyTest' && python3 -m unittest tools.tests.test_routes`
> **Jauge : ≈ 400 k jetons entrée / 18 k sortie** (effort M) · audit Opus : oui

**Vague 13b (TV, cœur) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 3.1 règle (5), § 3.5 (TV), § 3.7, décisions D-W13-2, D-W13-3. Branche `claude/sonnet-w13-05`. Rapport : `docs/agent-reports/sonnet-w13-05.md`.

## Objectif
(1) Chaque corps d'erreur JSON de `ReceiverServer` (401, 403, 404 transfert, 409, 413, 429, 503, 507) porte un champ additif `"code":"<wire>"` identique aux `BlockerCode.wire` ; (2) une requête **sans aucun secret** (ni `X-CB-Pin`, ni `X-CB-Token`) reste 401 (`"error":"pin missing","code":"pin-missing"`) mais **n'incrémente pas** le compteur de verrou (D-W13-2) ; (3) `ReceiverServer` signale chaque refus d'authentification à un crochet `onAuthRefused(peerKey, kind)` ; `PinNeededPolicy` (pur) décide si un bandeau TV peut être montré (1 / 60 s / pair, 3 / 10 min au total) ; (4) `BlockerLogTv` : les 10 derniers refus (sans IP, sans secret) exposés par `GET /api/link/blockers` **derrière PIN ou jeton** et à l'écran D-pad (w13-06) ; (5) `routes.txt` mis à jour.

## Pourquoi (preuves)
- Verrou : `C/tv/Security.kt:38-49` (`check(ip, given)` : `given == null` ⇒ `Pin.matches` faux ⇒ `failures++`) ; `C/tv/ReceiverServer.kt:535-541`.
- Corps actuels sans code : `ReceiverServer.kt:349` (trial), `:530` (pin required), `:533` (bad token), `:538-540` (bad pin / locked), `:552-558`, `:574,578`, `:694`, `:721,729,738`, `:790-793`, `:848`, `:1196,1200,1206`.
- La TV est muette sur HTTP (seuls les refus Bluetooth parlent : `C/trust/PhonePresence.kt:58-67`) ; `TvService.notice` existe (`R/TvService.kt:608`).
- Mode enfant masque le PIN : `R/ParentalHub.kt:283` (`shownPin`) — le bandeau TV ne doit pas le révéler.
- Liste des routes : `tools/routes/routes.txt`, `tools/routes/list_routes.py`, `tools/tests/test_routes.py` (règle w1-06 : toute route nouvelle y figure).

## Fichiers possédés
`C/tv/ReceiverServer.kt`, `C/tv/Security.kt`, `CT/SecurityTest.kt`, `tools/routes/routes.txt` ; nouveaux : `C/link/PinNeededPolicy.kt`, `C/link/BlockerLogTv.kt`, `CT/link/PinNeededPolicyTest.kt`, `CT/tv/ErrorCodesTest.kt`. **Hors zone** : `R/**` (w13-06), `TrialPolicy.kt`, `TrustRegistry.kt`, `BtProtocol.kt`.

## Signatures (contrat pour w13-06)
```kotlin
// C/tv/Security.kt
class PinGuard(…) { enum class Result { OK, BAD, LOCKED, MISSING }; fun check(ip: String, given: String?): Result }   // MISSING: given == null, aucun compteur touché
// C/tv/ReceiverServer.kt (constructeur, additif)
onAuthRefused: ((peerKey: String, kind: AuthRefusal) -> Unit)? = null
enum class AuthRefusal { PIN_MISSING, PIN_WRONG, PIN_LOCKED, TOKEN_REJECTED, PIN_REQUIRED_ROUTE, TRIAL, PARENTAL }
// C/link/PinNeededPolicy.kt
class PinNeededPolicy(private val now: () -> Long, val perPeerMs: Long = 60_000, val globalMax: Int = 3, val globalWindowMs: Long = 600_000) { enum class Show { NONE, PIN_NEEDED, TOKEN_RENEWING, TRIAL } ; fun onRefusal(peerKey: String, kind: AuthRefusal): Show }
// C/link/BlockerLogTv.kt
class BlockerLogTv(store: TextStore, now: () -> Long, max: Int = 10) { fun add(kind: AuthRefusal, http: Int, path: String); fun json(): String /* [{t,code,http,path}] sans IP */ ; fun lines(): List<String> /* phrases TV */ }
```

## Étapes
1. `PinGuard.check` : `given == null` ⇒ `MISSING` (ni `failures++`, ni `lockedUntil`) ; un verrou en cours reste `LOCKED` même pour `MISSING` ; `SecurityTest` : 10 requêtes sans secret ne verrouillent pas ; 5 mauvaises puis bon ⇒ `LOCKED` (inchangé).
2. `denied()` : `MISSING` ⇒ 401 `{"error":"pin missing","code":"pin-missing"}` ; `BAD` ⇒ `{"error":"bad pin","code":"pin-wrong"}` ; `LOCKED` ⇒ `{…,"code":"pin-locked","retryAfter":N}` ; jeton refusé ⇒ `{"error":"bad token","code":"token-expired"}` ; `pin required` ⇒ `"code":"pin-required-route"` ; chaque cas appelle `onAuthRefused`.
3. Autres corps : trial `"code":"trial-closed"` ; `child active` `"code":"parental-child-active"` ; `volume removed` `"code":"usb-removed"` ; `read-only` `"code":"storage-readonly"` ; 507 `"code":"storage-full"` ; 413 `"code":"file-too-big-fs"` ; 429 `"code":"tv-busy-429"` ; 404 `unknown transfer` `"code":"session-lost"` ; 409 `moving`/offset `"code":"upload-conflict"` ; `needsForeground` `"code":"needs-foreground"` ; `Hôte non autorisé` `"code":"host-guard"` ; `bad name` `"code":"name-refused"` ; `storage/check` `ok:false` ⇒ `"code"` selon `status`. **Aucun autre champ** ; les corps restent rétro-compatibles (les clients anciens ignorent `code`).
4. `PinNeededPolicy` : `PIN_MISSING`/`PIN_WRONG`/`PIN_LOCKED` ⇒ `PIN_NEEDED` (même sortie pour absent et faux : ne jamais distinguer à l'écran) ; `TOKEN_REJECTED` ⇒ `TOKEN_RENEWING` ; `TRIAL` ⇒ `TRIAL` ; limites par pair et globale ; `PARENTAL`/`PIN_REQUIRED_ROUTE` ⇒ `NONE`.
5. `GET /api/link/blockers` (authentifié comme les autres routes, **pas** dans la liste blanche d'essai) ⇒ `BlockerLogTv.json()` ; ajouter la route à `routes.txt` ; `test_routes.py` vert.
6. `ErrorCodesTest` (serveur réel sur port 0, modèle `CT/TransferTest.kt:28`) : pour chaque statut émis, le corps contient `"code":"…"` ∈ `BlockerCode.values().map { it.wire }` ; requête sans secret ×10 puis bon PIN ⇒ 200 ; `onAuthRefused` reçoit `(peerKey, kind)` sans adresse complète (`peerKey` = `peers?.keyOfAddress` existant ou `"ip:<hash4>"`).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c '"code":"' android/core/src/main/kotlin/castbridge/core/tv/ReceiverServer.kt` ≥ 16 ; `grep -n 'link/blockers' tools/routes/routes.txt` ⇒ 1 ligne ; `TrialPolicyTest`/`*Trial*` verts (la nouvelle route est fermée en essai).

## Cas limites
`legacyPinQuery` (`?pin=`) : même traitement que l'en-tête ; tunnel Bluetooth (`peers`) : clé `bt:<adresse>` déjà fournie ⇒ hachée avant le journal ; corps 401 avec `Connection: close` inchangé.

## À ne pas faire
Ne jamais ajouter d'indice sur la proximité d'un PIN ; ne pas affaiblir le verrou pour un PIN **présent** ; pas d'IP ni d'adresse dans `BlockerLogTv` ; pas d'écran (w13-06).

## Rapport
`STATUT`, table statut → `code`, preuve « sans secret ⇒ pas de verrou », questions pour l'audit Opus.
