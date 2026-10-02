# w13-04 — Couche transfert : plus aucun échec muet (`ResumableUpload/Download`, `TransferClient`, `HttpTransferApi`, `Lanes`, `ResilientCall`, `LinkText.http` ⇒ `BlockerMap`)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (401/403, règle « jamais deux fois le même PIN », fin de la boucle 403) · statut : PRÊT
> **Groupe : W13-b** (vague W13, tranche S1) · prérequis : w13-01 et w13-02 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Transfer*' --tests '*ResilientCall*' --tests '*Multipath*' --tests 'castbridge.core.link.*'`
> **Jauge : ≈ 450 k jetons entrée / 25 k sortie** (effort M) · audit Opus : oui

**Vague 13b (cœur, branchement) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 1.3 (F3, F4, F5), § 3.1 règles (1)-(6), § 3.4 (repli de secret), § 3.7. Branche `claude/sonnet-w13-04`. Rapport : `docs/agent-reports/sonnet-w13-04.md`.

## Objectif
Chaque chemin d'échec de la couche transfert produit un `Blocker` (champ **additif**) et respecte sa gravité : un `ACTION`/`FATAL` **arrête** (plus de boucle « En pause : HTTP 403 ») ; un `WAIT` continue avec son texte ; un `PIN_*` n'est **jamais** rejoué ; un 403 d'essai dit « essai », un 401 dit « code ». `LinkText.http` et `ResilientCall.classify` deviennent des vues de `BlockerMap`.

## Pourquoi (preuves)
- F4 : `C/tv/TvClient.kt:256-259` (403/404 au pré-vol ⇒ `checked = true`), `:282-289` (liste fatale `507/413/400/401` ; 403, 429, 409, 5xx ⇒ `Waiting` sans fin, texte = corps brut) ; `:240-243` (attente de jeton, texte générique).
- F3 : `C/xfer/TransferClient.kt:38-43` (`caps` 401/403 ⇒ « autorisation refusée par la TV ») ; `:45-49` (`begin` : `message` ?: `error`) ; `:122,127` (`Refused` ⇒ `Failed(reason)` texte) ; `:148,154` (`SessionLost`, `Corrupt` > 6).
- F5 : `C/xfer/Lanes.kt:42-49` (401/403 fatal texte technique ; 429 muet) ; `C/xfer/Scheduler.kt:142-153` (`waiting(reason)` = corps).
- Base : `C/trust/ResilientCall.kt:93-116` (`classify`), `C/trust/LinkText.kt:124-135` (`http`), `C/trust/Credentials.kt:45-51` (`CredentialGate`), `C/tv/TvClient.kt:162-163`.
- Tests existants à garder verts : `CT/TransferTest.kt` (`OneGigRuleServerTest`, `DownloadTest`), `CT/MultipathTransferTest.kt` (`SchedulerTest`), `CT/ResilientCallTest.kt`, `CT/LinkMachineTest.kt`.

## Fichiers possédés
`C/tv/TvClient.kt` (classes `ResumableUpload`, `ResumableDownload`, `HttpError` : champ `body` exposé), `C/xfer/TransferClient.kt`, `C/xfer/Lanes.kt` (`outcome` seulement), `C/xfer/Lane.kt` (`Outcome.Failed` gagne `blocker: Blocker?`), `C/xfer/Scheduler.kt` (`Listener.blocked(b)` additif), `C/trust/ResilientCall.kt` (`classify` délègue), `C/trust/LinkText.kt` (`http()` délègue à `BlockerTexts`, signature inchangée), `CT/link/TransferBlockerTest.kt` (nouveau). **Hors zone** : `ReceiverServer.kt` (w13-05), `S/**`, `R/**`, `Blocker*.kt`, `Preflight.kt`.

## Signatures (additives, contrat pour w13-07)
```kotlin
// C/tv/TvClient.kt
class HttpError(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(200)}")
sealed class State { …; data class Waiting(val sent: Long, val total: Long, val reason: String, val blocker: Blocker? = null) : State(); data class Failed(val reason: String, val blocker: Blocker? = null) : State() }
class ResumableUpload(…, private val onBlocker: (Blocker) -> Unit = {}, private val ctx: BlockerCtx = BlockerCtx())   // ctx.tv, credential kind, route renseignés par l'appelant
// C/xfer/TransferClient.kt
sealed class Result { …; class Failed(val reason: String, val blocker: Blocker? = null) : Result() }
class TransferClient(…, private val onBlocker: (Blocker) -> Unit = {}, private val ctx: BlockerCtx = BlockerCtx())
```

## Étapes
1. `TvClient.HttpError` : conserver le message, exposer `body` ; `ResumableUpload.run` : construire `BlockerCtx` (phase `PREFLIGHT`/`BEGIN`/`CHUNK`, `credential` = `CredKind` de `cred`) ; **pré-vol** : `StorageCheck` ⇒ `BlockerMap.ofCheck` ; `HttpError` ⇒ `ofThrowable` ; décision par gravité : `WAIT` ⇒ `Waiting(…, blocker)` + délai (respecter `retryAfterMs`), `ACTION`/`FATAL` ⇒ `Failed(texte, blocker)` **sauf** `TV_TOO_OLD` (404) ⇒ `checked = true` (comportement existant conservé) ; `TOKEN_EXPIRED` ⇒ `refusedToken = cred` + `Waiting` avec texte `BlockerTexts` (existant, texte remplacé) ; **`PIN_*` ⇒ `Failed` immédiat**, aucun second envoi.
2. **Repli de secret** (§ 3.4) : `ResumableUpload` reçoit un `fallbackCredential: () -> String?` (jeton pour cette base, fourni par l'appelant w13-07) ; sur `PIN_WRONG` avec `credential == PIN`, si un jeton existe et n'a pas encore été essayé ⇒ une **seule** reprise avec le jeton, puis `Failed`.
3. Même traitement dans la boucle d'envoi (`:280-294`) et dans `ResumableDownload` (`:381-394`).
4. `HttpTransferApi.caps/begin/finish` : `Refused` porte `blocker = BlockerMap.ofHttp(code, body, ctx)` ; `TransferClient.run` : `Refused.blocker.severity == WAIT` ⇒ attendre puis réessayer (≤ 6), sinon `Failed(texte, blocker)` ; `SessionLost` ⇒ `onBlocker(SESSION_LOST)` ; `Corrupt` > 6 ⇒ `CORRUPT_REPEATED`.
5. `Lanes.outcome` : 401/403/507/413/400/429/404/422 ⇒ `Outcome.*` avec `blocker` ; `Scheduler` : `failure` fatal ⇒ `listener.blocked(b)` ; `Busy` ⇒ compteur, `TV_BUSY_429` signalé après 60 s cumulés.
6. `ResilientCall.classify(e)` = `when (BlockerMap.ofThrowable(e, ctx).severity) { WAIT -> TRANSIENT; FATAL -> PERMANENT; ACTION -> if (fix == ASK_PIN || fix == REPAIR_PAIRING) CREDENTIAL else PERMANENT }` + `TOKEN_EXPIRED -> TOKEN` ; `LinkText.http(code, body)` = `BlockerTexts.of(BlockerMap.ofHttp(code, body, BlockerCtx())).sentence` ; tests existants inchangés et verts.
7. `TransferBlockerTest` (faux serveur NanoHTTPD scripté, modèle `CT/TransferTest.kt:28-100`) : 401 PIN ⇒ `Failed` + `PIN_WRONG`, **une** requête `/upload` au plus ; 401 token ⇒ `Waiting` + `TOKEN_EXPIRED` puis reprise quand `credential()` change ; 403 trial ⇒ `Failed` + `TRIAL_CLOSED` en ≤ 2 requêtes (plus de boucle) ; 503 removed ⇒ `Waiting` + `USB_REMOVED` ; 507 ⇒ `Failed` + `STORAGE_FULL` ; 429 ⇒ attente `retryMs` ; `TransferClient` : `caps` 403 trial ⇒ `Failed` + `TRIAL_CLOSED` ; `caps` 401 ⇒ `PIN_WRONG` ; `begin` 507 ⇒ `STORAGE_FULL` ; chunk 404 ⇒ reprise ; repli jeton après `PIN_WRONG`.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'HTTP 403' android/core/src/main/kotlin/castbridge/core/tv/TvClient.kt` vide ; dans `TvClient.kt`, `TransferClient.kt`, `Lanes.kt` : chaque `catch (e: TvClient.HttpError)` ou `catch (e: IOException)` contient `BlockerMap.` dans son bloc (vérifié par w13-10) ; `CT/TransferTest.kt`, `CT/MultipathTransferTest.kt`, `CT/ResilientCallTest.kt` verts sans modification.

## Cas limites
`credential` nul (TV sans PIN en test) ⇒ `CredKind.NONE`, un 401 ⇒ `PIN_MISSING` ; corps 401 tronqué ; annulation pendant le délai `retryAfter`.

## À ne pas faire
Pas de texte français nouveau (tout vient de `BlockerTexts`) ; ne pas changer les signatures publiques existantes (ajouts à valeur par défaut seulement) ; ne pas modifier `HttpConn` ; ne pas renvoyer un PIN refusé.

## Rapport
`STATUT`, liste des chemins `catch` traités (fichier:ligne avant/après), requêtes émises par cas de test, questions pour l'audit Opus.
