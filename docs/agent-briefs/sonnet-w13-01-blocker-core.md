# w13-01 — Cœur : taxonomie `Blocker` et `BlockerMap` (un seul producteur pour tout échec HTTP / Bluetooth / E-S)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (classement des 401/403, règle du verrou PIN) · statut : PRÊT
> **Groupe : W13-a** (vague W13, tranche S1) · prérequis : aucun (vagues 1-3 fusionnées) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.BlockerMapTest' --tests '*ResilientCall*'`
> **Jauge : ≈ 250 k jetons entrée / 15 k sortie** (effort M) · audit Opus : oui

**Vague 13a (cœur) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W13-AUCUN-BLOCAGE-SILENCIEUX-2026-10-02.md` § 2 (catalogue), § 3.1, § 3.7. Branche `claude/sonnet-w13-01`. Rapport : `docs/agent-reports/sonnet-w13-01.md`.

## Objectif
Créer, dans le cœur (JVM pur, aucun `import android`), la taxonomie `Blocker` (`BlockerCode` × `Severity` × `Fix` × `autoFix`) et l'objet `BlockerMap` qui transforme **tout** échec en un `Blocker` : statut HTTP + corps (`ofHttp`), exception (`ofThrowable`), code Bluetooth + indice (`ofBt`), pré-vol stockage (`ofCheck`), refus du transfert rapide (`ofTransferRefused`). Exposer `KNOWN_HTTP` (tous les statuts que la TV émet) pour le test lint de w13-10. Ne **pas** brancher les appelants (w13-04).

## Pourquoi (preuves)
- Six fuites d'information décrites en § 1.3 de la conception : `S/UploadService.kt:255-265`, `S/TvHome.kt:130-132,347`, `C/xfer/TransferClient.kt:38-43,122`, `C/tv/TvClient.kt:256-259,282-289`, `C/xfer/Lanes.kt:42-49`, `C/tv/ReceiverServer.kt:522-542`.
- Base à réutiliser : `C/trust/ResilientCall.kt:93-116` (`classify`, quatre familles), `C/trust/LinkText.kt:111-135` (`failure`, `http`), `C/tv/TvClient.kt:162-163` (`isBadToken`), `C/tv/BtProtocol.kt:79-98,103` (codes `ERR_*`, `isFatal`), `C/tv/TvClient.kt:70-71` (`StorageCheck.status` 507/503/413 dans un corps 200).
- Corps émis par la TV (à mapper) : 401 `bad pin` / `locked`+`retryAfter` / `bad token` (`ReceiverServer.kt:533,538-540`) ; 403 `trial:true` (`:349`), `pin required` (`:530`), `child active` (`:848`), `Hôte non autorisé` (`:342`) ; 409 `moving`/`partJson`/`needsForeground` (`:574,578,334`) ; 413/503/507 (`:551-558,783-796`) ; 429 `busy`+`retryMs` / `too many transfers` (`:729`, `C/xfer/TransferHost.kt:42,50`) ; 404 `unknown transfer` (`:721`) ; 422 (`:738`) ; 501 (`:716`) ; 500 (`:336`).

## Fichiers possédés
Nouveaux : `C/link/Blocker.kt` (enums, `BlockerCtx`, `Blocker`), `C/link/BlockerMap.kt`, `CT/link/BlockerMapTest.kt`. **Hors zone** : tout le reste (en particulier `TvClient.kt`, `Lanes.kt`, `ResilientCall.kt`, `LinkText.kt` : w13-04 ; textes français : w13-02).

## Signatures à respecter (contrat pour w13-02 … w13-10)
```kotlin
package castbridge.core.link
enum class Severity { INFO, WAIT, ACTION, FATAL }
enum class CredKind { PIN, TOKEN, NONE }
enum class XferRoute { LAN, DIRECT, BT, TUNNEL, UNKNOWN }
enum class Phase { PROBE, PREFLIGHT, BEGIN, CHUNK, FINISH, PLAY }
enum class Fix { NONE, HELLO_AGAIN, REDISCOVER_IP, WAIT_WAKE, RETRY_AFTER, ASK_PIN, REPAIR_PAIRING, OPEN_TV_APP, FREE_SPACE, REINSERT_USB, CHECK_TV_CLOCK, UPDATE_TV, UPDATE_PHONE, PARENTAL_ON_TV, PRODUCTION_KEY, ACTIVATE_TV, RENEW_KEY, WIFI_SETTINGS, DISABLE_VPN, BATTERY_SETTINGS, KEEP_SCREEN_ON, RENAME_FILE, REPICK_FILE, OTHER_VOLUME, FORMAT_EXFAT, USE_IP, RETRY }
enum class BlockerCode(val wire: String, val severity: Severity, val fix: Fix, val autoFix: Boolean) { /* les 47 codes du § 2, dans l'ordre du tableau ; wire = kebab-case */ }
data class BlockerCtx(val tv: String = "TV", val credential: CredKind = CredKind.NONE, val route: XferRoute = XferRoute.UNKNOWN, val phase: Phase = Phase.PROBE,
    val http: Int? = null, val btCode: Int? = null, val retryAfterMs: Long? = null, val volume: String? = null, val missingBytes: Long? = null, val tvMessage: String? = null)
data class Blocker(val code: BlockerCode, val ctx: BlockerCtx, val at: Long) { val severity get() = code.severity; val fix get() = code.fix; val autoFix get() = code.autoFix }
object BlockerMap {
    fun ofHttp(status: Int, body: String, ctx: BlockerCtx, now: Long = 0): Blocker
    fun ofThrowable(t: Throwable, ctx: BlockerCtx, now: Long = 0): Blocker
    fun ofBt(code: Int, hint: Int, ctx: BlockerCtx, now: Long = 0): Blocker
    fun ofCheck(c: castbridge.core.tv.TvClient.StorageCheck, ctx: BlockerCtx, now: Long = 0): Blocker?
    fun ofTransferRefused(http: Int, message: String, ctx: BlockerCtx, now: Long = 0): Blocker
    val KNOWN_HTTP: Set<Int>   // 400, 401, 403, 404, 405, 409, 413, 422, 429, 500, 501, 503, 507 (+ ceux trouvés à la lecture de ReceiverServer)
    fun wireOf(body: String): String?   // champ "code" ajouté par w13-05 ; null sur une TV ancienne
}
```

## Étapes
1. `Blocker.kt` : enums et données ; `BlockerCtx.tvMessage` est passé par `castbridge.core.trust.Redact.scrub` dans le constructeur de `Blocker` (jamais de secret).
2. `BlockerMap.ofHttp` : d'abord `wireOf(body)` (champ `"code"`), puis l'ordre des indices § 3.1 (401 : `locked` → `bad token` → `credential==PIN` ⇒ `PIN_WRONG` → sinon `PIN_MISSING` ; 403 : `trial` → `pin required` → `child active` → `Hôte` → message mode réduit → `UNKNOWN_HTTP`) ; 409 : `moving`/`upload in progress` ⇒ `UPLOAD_CONFLICT`, `needsForeground` ⇒ `NEEDS_FOREGROUND` (ou `PARENTAL_LOCKED_FILE` si le message contient « parental ») ; 413 ⇒ `FILE_TOO_BIG_FS` ; 429 ⇒ `TV_BUSY_429` (+ `retryAfterMs`) ; 503 : `removed` ⇒ `USB_REMOVED`, `read-only` ⇒ `STORAGE_READONLY`, sinon `TV_RESTARTING` (classer sous `PORT_CLOSED`/`WAIT`) ; 507 ⇒ `STORAGE_FULL` ; 404 : `unknown transfer` ⇒ `SESSION_LOST`, sinon `TV_TOO_OLD` ; 422 ⇒ `CORRUPT_REPEATED` ; 501 ⇒ `TV_TOO_OLD` ; 400 `bad name` ⇒ `NAME_REFUSED` ; 500/502/504 ⇒ `UNKNOWN_HTTP` (`WAIT`).
3. `ofThrowable` : `TvClient.HttpError` ⇒ `ofHttp` (statut et corps extraits du message `HTTP <code>: <corps>`) ; `TvClient.Conflict` ⇒ `UPLOAD_CONFLICT` ; `TvCredential.Missing` ⇒ `PIN_MISSING` ; `BtProtocol.Refused` ⇒ `ofBt` ; `BtUnavailable` ⇒ `BT_OFF` (ajouter ce code `ACTION`/`WIFI_SETTINGS`→ non : `Fix.NONE` avec action « Activer le Bluetooth » : réutiliser `LinkText.bluetooth` dans w13-02) ; `SocketTimeoutException` ⇒ `TV_ASLEEP` ; `ConnectException`/`NoRouteToHost` ⇒ `PORT_CLOSED` ; `UnknownHostException` ⇒ `TV_OTHER_IP` ; `InterruptedIOException` ⇒ `CANCELLED` (code `INFO`, à ajouter) ; `SecurityException` ⇒ `PHONE_PERMISSION` (code `ACTION`, à ajouter) ; `IOException` ⇒ `UNKNOWN_IO`.
4. `ofBt` : `ERR_PIN` ⇒ `PIN_WRONG` ; `ERR_LOCKED` ⇒ `PIN_LOCKED` ; `ERR_UNTRUSTED` + `HINT_OTHER_INSTALL` ⇒ `TV_REINSTALLED`, `HINT_SAME_INSTALL` ⇒ `TOKEN_REVOKED`, sinon `TOKEN_REVOKED` ; `ERR_DENIED` ⇒ `TOKEN_REVOKED` (ctx.btCode conservé) ; `ERR_TRIAL` ⇒ `TRIAL_CLOSED` ; `ERR_SPACE` ⇒ `STORAGE_FULL` ; `ERR_NAME` ⇒ `NAME_REFUSED` ; `ERR_SIZE` ⇒ `SOURCE_UNREADABLE` ; `ERR_IO` ⇒ `STORAGE_READONLY` ; `ERR_MAGIC` ⇒ `TV_TOO_OLD` ; `ERR_BUSY`/`ERR_TIMEOUT`/`ERR_NOT_OPEN` ⇒ `TV_BUSY_429` (`WAIT`).
5. `ofCheck` : `ok` ⇒ null ; `status` 507 ⇒ `STORAGE_FULL` (`missingBytes = minFreeAfter - freeAfter` si > 0) ; 503 ⇒ `USB_REMOVED` ; 413 ⇒ `FILE_TOO_BIG_FS` ; autre ⇒ `UNKNOWN_HTTP`.
6. `BlockerMapTest` : **table** (≥ 47 lignes, un cas par `BlockerCode`), plus : équivalence avec `ResilientCall.classify` (`TRANSIENT ⇔ WAIT`, `CREDENTIAL ⇔ ACTION` avec `ASK_PIN`/`REPAIR_PAIRING`, `TOKEN ⇔ TOKEN_EXPIRED`, `PERMANENT ⇔ FATAL`) ; aucun `KNOWN_HTTP` ne donne `UNKNOWN_HTTP` **sauf** 500/502/504 ; `tvMessage` est passé par `Redact` (un corps contenant `cbk_…` ou 6 chiffres n'apparaît pas).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/ | wc -l` ⇒ 0 ; `grep -c 'BlockerCode\.' android/core/src/test/kotlin/castbridge/core/link/BlockerMapTest.kt` ≥ 47 ; aucune chaîne française dans `Blocker.kt`/`BlockerMap.kt` (les textes sont w13-02).

## Cas limites
Corps vide ou non JSON ⇒ classement par statut seul ; message `HttpError` tronqué à 200 car. (`TvClient.kt:134`) ⇒ `wireOf` doit tolérer un JSON coupé ; statut inconnu ⇒ `UNKNOWN_HTTP` avec `ctx.http` renseigné.

## À ne pas faire
Aucun texte français ; aucun appelant modifié ; aucune nouvelle dépendance ; ne pas déplacer `ResilientCall.classify` (w13-04 le fera déléguer).

## Rapport
`STATUT`, table code → ligne de test, écarts avec le § 2, questions pour l'audit Opus (cas 401 ambigus).
