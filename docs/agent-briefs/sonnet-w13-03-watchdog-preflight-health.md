# w13-03 — Cœur : `TransferWatchdog` (aucun octet depuis N s), `Preflight` (nommer le blocage), `LinkHealth` (trois vérités), `BlockerLog`, `SupportCode`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (`Preflight` ne doit jamais renvoyer un PIN refusé) · statut : PRÊT
> **Groupe : W13-a** (vague W13, tranche S1) · prérequis : w13-01 fusionné (contrat `Blocker`) ; w13-02 souhaité (textes, sinon tests sur les codes) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.*'`
> **Jauge : ≈ 350 k jetons entrée / 22 k sortie** (effort M) · audit Opus : échantillon

**Vague 13a (cœur) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 3.2, § 3.3, § 3.6, § 2 (classe « faux état initial »). Branche `claude/sonnet-w13-03`. Rapport : `docs/agent-reports/sonnet-w13-03.md`.

## Objectif
Cinq objets purs (aucun Android, horloge et transport injectés) : (1) `TransferWatchdog` : fenêtre N adaptative au débit, décisions `NONE / PREFLIGHT / ESCALATE / GIVE_UP` ; (2) `Preflight` : séquence de sondes (§ 3.2) qui s'arrête au **premier** `Blocker` ; (3) `LinkHealth` : `reachable / authorized / ready` + `Blocker` + fraîcheur, `chip()` jamais `GOOD` sans sonde authentifiée fraîche, **état initial `UNKNOWN` quand une TV est sauvegardée** ; (4) `BlockerLog` : anneau de 200 entrées sans secret, export redigé ; (5) `SupportCode` : `CB-<3 lettres>-<MMJJ>-<4 hex>`.

## Pourquoi (preuves)
- Chien de garde existant **par connexion** seulement : `C/xfer/HttpConn.kt:12-14,82-93` (20 s) ; aucune détection « transfert sans progrès » au niveau du transfert (`C/tv/TvClient.kt:232-297`, `C/xfer/Scheduler.kt:54-76`).
- Sondes existantes à réutiliser par `PreflightEnv` : `/api/hello` (`S/LinkAndroid.kt:75-82`), `/api/info` authentifié (`:85-93`, `C/trust/LinkDriver.kt:174-181`), `/api/storage/check` (`C/tv/TvClient.kt:78-81`, `ReceiverServer.kt:1182-1218` : `ok:false` + `status`), `/api/library` en-tête `childActive` (`ReceiverServer.kt:961`), `/api/transfer/state` (`:671`), `TvInfo.state` (`C/tv/TvInfo.kt:17-19`).
- Faux état initial : `S/TvLink.kt:104` (`_state = NoTv`), `:244-245` ; correctif ciblé confié ailleurs : **ne pas le refaire**, mais `LinkHealth.initial(hasSavedTv=true)` doit être `UNKNOWN × 3`.
- Journal : style `C/tunnel/TunnelJournal.kt` ; redaction `C/trust/Diagnostics.kt:45-53` ; `SafeFile` (w1-02) si présent dans `C/`, sinon écriture dans un `TextStore` injecté.

## Fichiers possédés
Nouveaux : `C/link/{TransferWatchdog,Preflight,LinkHealth,BlockerLog,SupportCode}.kt`, `CT/link/{TransferWatchdogTest,PreflightTest,LinkHealthTest,BlockerLogTest,SupportCodeTest}.kt`. **Hors zone** : `Blocker*.kt` (w13-01/02), `S/**`, `R/**`, `C/tv/**`, `C/xfer/**`.

## Signatures à respecter (contrat pour w13-07, w13-08, w13-09, w13-10)
```kotlin
package castbridge.core.link
class TransferWatchdog(val cfg: Config = Config(), private val now: () -> Long) {
    data class Config(val minMs: Long = 12_000, val maxMs: Long = 90_000, val firstByteLanMs: Long = 15_000, val firstByteBtMs: Long = 45_000, val firstByteTunnelMs: Long = 30_000, val escalateFactor: Int = 3, val giveUpMs: Long = 10 * 60_000, val probeBytes: Long = 1L shl 20, val factor: Int = 8)
    enum class Decision { NONE, PREFLIGHT, ESCALATE, GIVE_UP }
    fun start(route: XferRoute); fun onBytes(n: Long); fun bytesPerSec(): Long; fun windowMs(): Long; fun tick(): Decision; fun onBlocker(b: Blocker?)   // un Blocker WAIT remet la fenêtre, un ACTION/FATAL arme GIVE_UP
}
interface PreflightEnv { fun netUp(): Boolean; fun vpn(): Boolean; fun powerSave(): Boolean; fun hello(base: String): Boolean; fun btHelloIps(): List<String>?; fun info(base: String, credential: String?): HttpReply; fun storageCheck(base: String, credential: String?, name: String, size: Long): HttpReply; fun libraryHead(base: String, credential: String?): HttpReply; fun transferState(base: String, credential: String?, id: String?): HttpReply?; fun now(): Long }
data class HttpReply(val status: Int, val body: String, val error: Throwable? = null)
object Preflight { data class Probe(val base: String?, val credential: String?, val credKind: CredKind, val route: XferRoute, val fileName: String, val size: Long, val transferId: String?, val tv: String, val lastBps: Long); fun run(env: PreflightEnv, p: Probe): Blocker? }
enum class Level { YES, NO, UNKNOWN }
data class LinkHealth(val reachable: Level, val authorized: Level, val ready: Level, val blocker: Blocker?, val credential: CredKind, val route: XferRoute, val checkedAt: Long) {
    enum class Tone { GOOD, WARN, BAD, NEUTRAL }
    fun tone(now: Long, maxAgeMs: Long): Tone; fun chipKey(now: Long, maxAgeMs: Long): String /* ready|code|trial|checking|away|blocked */
    companion object { fun initial(hasSavedTv: Boolean): LinkHealth; fun fromPreflight(b: Blocker?, probeOk: Boolean, authOk: Boolean, readyOk: Boolean, …): LinkHealth }
}
class BlockerLog(private val store: TextStore, private val now: () -> Long, val max: Int = 200) { data class Entry(val t: Long, val code: String, val http: Int?, val phase: String, val route: String, val cred: String, val tvHash4: String, val ms: Long?); fun add(b: Blocker, ms: Long? = null); fun recent(n: Int): List<Entry>; fun export(): String; fun count(code: BlockerCode, sinceMs: Long): Int }
interface TextStore { fun load(): String?; fun save(text: String) }
object SupportCode { fun of(b: Blocker, tvHash4: String, minute: Long): String; fun tvHash4(tvName: String): String }
```

## Étapes
1. `TransferWatchdog` : EWMA du débit (α = 0,3) ; `windowMs = clamp(factor × probeBytes / bps, minMs, maxMs)` ; avant le premier octet : `firstByte*` selon la route ; `tick()` rend `PREFLIGHT` une fois par fenêtre, `ESCALATE` à `escalateFactor × window`, `GIVE_UP` à `giveUpMs` seulement si le dernier `Blocker` est `ACTION`/`FATAL`.
2. `Preflight.run` dans l'ordre § 3.2 ; **interdit** : appeler `info`/`storageCheck` avec un `credential` dont `credKind == PIN` si `p` indique un blocage `PIN_*` déjà vu (`Probe.lastBlocker`) ; mapper chaque réponse par `BlockerMap` ; `hello` KO + `btHelloIps` différent de `base` ⇒ `TV_OTHER_IP` ; aucune réponse ⇒ `TV_ASLEEP` (ou `PORT_CLOSED` sur `ConnectException`).
3. `LinkHealth` : `tone == GOOD` ⇔ `reachable==YES && authorized==YES && ready!=NO && now-checkedAt<=maxAge` ; `initial(true)` ⇒ `UNKNOWN×3`, tone `NEUTRAL`, chipKey `checking` ; `initial(false)` ⇒ `NO×3` chipKey `away` (l'écran dit « Aucune TV ajoutée » **seulement** sur `saved.list().isEmpty()`).
4. `BlockerLog` : une ligne tab par entrée, `export()` passe par `Redact.scrub`, `tvHash4 = SupportCode.tvHash4`, aucune IP complète (`192.168.0.•••`), fichier corrompu ⇒ vidé et noté.
5. `SupportCode.of` : `"CB-" + code.wire.take(3).uppercase() + "-" + MMJJ + "-" + sha256(code|http|tvHash4|minute)[0:2].hex` ; stable à la minute.
6. Tests : seuils (150 Ko/s ⇒ 56 s ; 5 Mo/s ⇒ 12 s ; Bluetooth premier octet 45 s) ; `PREFLIGHT` puis `ESCALATE` à 3N ; `Preflight` s'arrête au premier `Blocker` et ne rappelle pas `info` avec un PIN refusé ; `LinkHealth` : TV sauvegardée + rien publié ⇒ `UNKNOWN`, jamais `NoTv` ; sonde authentifiée datée de 21 s au premier plan ⇒ `NEUTRAL` ; `authorized==NO` ⇒ jamais `GOOD` ; `BlockerLog.export()` sans `cbk_`, sans 6 chiffres, sans IP ; `SupportCode` stable et sans secret.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/ | wc -l` ⇒ 0 ; ≥ 1 test par décision du chien de garde et par vérité de `LinkHealth`.

## Cas limites
Débit nul avant le premier octet ; horloge qui recule (fenêtre jamais négative) ; `transferId` null ; `storageCheck` 404 (TV ancienne) ⇒ `ready = UNKNOWN`, pas `NO`.

## À ne pas faire
Aucun appel réseau réel ; pas de texte français (sauf tests) ; ne pas toucher `TvLink.kt` (correctif de l'état initial confié ailleurs).

## Rapport
`STATUT`, constantes retenues (à ajuster avec les mesures B2 de W7), signatures, ce que w13-07/09 doivent brancher.
