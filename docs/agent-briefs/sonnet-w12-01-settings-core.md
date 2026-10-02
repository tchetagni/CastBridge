# w12-01 — Cœur : `castbridge.core.settings` — schéma à bornes, document `settings` (enveloppe `cbx1`), vérificateur, moteur, vecteurs partagés
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (vérification de signature, bornes €/🔒) · statut : PRÊT
> **Groupe : W12-a** (vague W12) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.settings.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : **oui**
> **Amendement W16 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 4.1. Déclarer dès la tranche 1, avec leurs bornes et défauts : `rental.userChosen` (BOOL, 1 au pilote / 0 après), `rental.defaultDays` (30, 1–366, existante), `rental.pickerDays` (TEXT `1,3,7,14`), `rental.maxDays` (30, 1–60), `rental.hourly.maxUseHours` (96, 1–720), `rental.hourly.pickerHours` (TEXT `1,3,6,12,24,48,96`), `rental.hourly.validityDays` (30, 1–60), `rental.hourly.weeklyQuotaHours` (192, 0–672), `rental.cooldownMin` (0, 0–1440), `pilot.start`, `pilot.end` (ms, `end − start` ≤ 90 j), `pilot.graceDays` (14, 0–30) ; les noms sont ceux de `C/lots/PilotRules.kt` (w16-04 : `PilotParams`), qui lit `Settings` quand ce cahier est fusionné, sinon `tools/pilot/pilot.json`.

**Vague 12a (cœur, JVM, testé) · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 1.2, § 1.3, § 2 (tout), § 7, § 9 (D-W12-1, 2, 4, 6, 7). Branche `claude/sonnet-w12-01`. Rapport : `docs/agent-reports/sonnet-w12-01.md`. Textes en français ; dire « CastBridge » (téléphone) / « CastBridge-TV ».

## Objectif
Un module **pur Kotlin** (aucune dépendance Android) qui définit : (1) `SettingsSchema` : la **liste fermée** des clés réglables avec type, bornes, défaut compilé, consommateurs, sensibilité ; (2) `SettingsDoc` : le corps du nouveau type d'enveloppe `settings` (lignes `schema=`, `basedOn=`, `reset=all`, `set=`, `exp=`), forme canonique, émission (pour les tests et l'outil de bureau) ; (3) `SettingsVerifier` : les 12 étapes de vérification (conception § 2.3) sur l'enveloppe commune ; (4) `SettingsEngine` + `SettingsState` : réception idempotente, cache, journal, repli sur les défauts, expiration (D-W12-7), règle « plus récent par `issuedAt` » entre deux clés ; (5) `Settings` : l'API de lecture utilisée par les apps et les outils (`Settings.int("trial.defaultDays")`, `bool`, `id`, `text`, `version()`) ; (6) les **vecteurs partagés** `tools/activation/settings-vectors.json` et le fichier de parité `tools/settings/schema.json`.

## Pourquoi (preuves)
- L'enveloppe `cbx1` existe avec un seul codec et un seul vérificateur de signature (`C/owner/Envelope.kt:5-60`) ; `ACTIVATION-FORMAT.md` § 3 : « ajouter un type ajoute un analyseur de corps et une portée, jamais un second format ». Le vérificateur d'ordres (`C/owner/Order.kt:54-77`) est le modèle à suivre (portée, cible, séquence, fenêtre).
- Le moteur de politiques (`C/policy/PolicyEngine.kt:32-115`) montre la mécanique de réception idempotente, de persistance atomique (`QueueStore`, `:17-20`, `:132-139`) et de journal (`PolicyState.kt:93-96`) ; `PolicyActions.BUDGETS` (`C/policy/PolicyActions.kt:32`) est le patron des bornes.
- Les défauts compilés à réexporter : `C/owner/Activation.kt:127-128` (`TRIAL_DEFAULT_DAYS` 30, `TRIAL_MAX_DAYS` 365), `C/lots/RentalLines.kt:11,22,23,45` (366 ; 720 ; 3), `C/lots/RentalDurations.kt:14` (30), `C/lots/LotApi.kt:30` (10 Mio), `C/langues/LangBudget.kt:75-76` (2048 / 100 : **le défaut du schéma est 500**, D-W12-6), `C/trust/PairingSession.kt:19` (10 min), `docs/TELEMETRY.md:63` (15 min).
- Aucune classe `TrialWindow` n'existe (seul `CT/lots/TrialWindowTest.kt:10`) : ne pas en créer.

## Fichiers possédés
- Nouveaux : `C/settings/SettingsSchema.kt`, `C/settings/SettingsDoc.kt`, `C/settings/SettingsVerifier.kt`, `C/settings/SettingsEngine.kt`, `C/settings/SettingsState.kt`, `C/settings/Settings.kt`, `CT/settings/{SettingsSchemaTest,SettingsDocTest,SettingsVerifierTest,SettingsEngineTest,SettingsVectorsTest}.kt`, `tools/activation/settings-vectors.json`, `tools/settings/schema.json`.
- **Hors zone** : tout fichier existant (`Envelope.kt`, `Order.kt`, `PolicyEngine.kt`, `Keys.kt`, les constantes citées restent intactes : le schéma les **réexporte**, il ne les déplace pas). Les expériences et les cohortes sont **w12-02** (`Experiments.kt`, `Cohort.kt`) : ici, `exp=` est seulement **analysé et conservé** dans `SettingsState.experiments` (liste de `ExperimentLine`).

## Signatures à respecter (contrat pour w12-02 … w12-10)
```kotlin
package castbridge.core.settings

enum class SettingType { INT, BOOL, ID, TEXT }
enum class Consumer { TV, PHONE, ISSUER, SERVER }
enum class Sensitivity { MONEY, SECURITY, UX }
data class SettingSpec(val key: String, val type: SettingType, val default: String, val min: Long? = null, val max: Long? = null,
                       val pattern: Regex? = null, val consumers: Set<Consumer>, val sensitivity: Sensitivity, val since: Int = 1)
object SettingsSchema {
    const val VERSION = 1
    val KEYS: Map<String, SettingSpec>          // les 41 clés de la conception § 1.2 (+ price.variant, settings.message) ; défaut = constante compilée
    val NEVER: List<String>                     // préfixes refusés : key., scope., trust., grace., super., tunnel.consent, update.verify
    fun spec(key: String): SettingSpec?
    fun check(key: String, value: String): CheckResult   // OK | UNKNOWN | OUT_OF_BOUNDS(message FR) | NEVER
    fun toJson(): String                        // = tools/settings/schema.json, octet pour octet (test de parité)
}
data class ExperimentLine(val name: String, val key: String, val arms: List<Pair<String, String>>, val salt: String, val startMs: Long, val endMs: Long, val guardrail: String?)
data class SettingsBody(val schema: Int, val basedOn: Long, val reset: Boolean, val set: Map<String, String>, val exp: List<ExperimentLine>) {
    fun canonical(): String                     // lignes triées, LF, sans LF final (ACTIVATION-FORMAT § 0)
    companion object { fun parse(body: String): SettingsBody? ; const val MAX_SET = 64 ; const val MAX_EXP = 8 ; const val MAX_VALUE = 128 ; const val TYPE = "settings" }
}
object SettingsIssuer { fun issue(signer: Ed25519Signer, kid: String, seq: Long, nonce: String, issuedAt: Long, validMs: Long, body: SettingsBody): String }   // jeton cbx1, target=any
sealed class SettingsResult { data class Accepted(val env: Envelope, val body: SettingsBody) : SettingsResult(); data class Rejected(val reason: Rejection) : SettingsResult() }
class SettingsVerifier(ring: KeyRing, seq: SeqState /* espace « settings » */, revocations: RevocationState, skewMs: Long = 24h) { fun verify(token: String, nowMs: Long): SettingsResult }
data class SettingsState(val seq: Long = 0, val kid: String = "", val issuedAt: Long = 0, val expiresAt: Long = 0, val schema: Int = 0,
                         val values: Map<String, String> = emptyMap(), val experiments: List<ExperimentLine> = emptyList(), val ignored: List<String> = emptyList()) {
    fun stale(nowMs: Long): Boolean ; fun toJson(): String ; companion object { fun fromJson(s: String): SettingsState }
}
data class SettingsAck(val kid: String, val seq: Long, val nonce: String, val reason: AckReason, val ignoredKeys: List<String>, val atMs: Long) { fun toText(): String }
class SettingsEngine(baseKeys: KeyRing, storage: QueueStore, wallClock: () -> Long = System::currentTimeMillis, trustedNow: (() -> Long)? = null) {
    fun receive(token: String): SettingsAck     // idempotent : même jeton ⇒ même accusé ; fail-closed ; règle « plus récent par issuedAt » entre deux kid acceptés
    val current: SettingsState ; fun journal(): List<String> ; fun token(): String?    // le jeton courant tel quel (pour le relais)
}
object Settings {                                // façade de lecture, thread-safe, branchée par les apps (SettingsEngine) ou les outils (fichier)
    fun bind(state: () -> SettingsState?)
    fun int(key: String): Long ; fun bool(key: String): Boolean ; fun id(key: String): String ; fun text(key: String): String?   // défaut compilé si absent / hors bornes
    fun version(): String                        // « v<seq> · JJ/MM/AAAA · <kid[0:4]> · expire le … [périmé] » ou « défauts (aucun document) »
}
```
Réutiliser `Envelope`, `KeyRing`, `KeyScope.POLICY`, `SeqState`, `RevocationState`, `Rejection`, `AckReason`, `TvClock` (`C/owner/*`, `C/policy/*`) **sans les modifier**. Le `SeqState` des réglages est **une instance distincte** de celui des ordres (fichier séparé : conception § 2.3 étape 8).

## Étapes
1. `SettingsSchema` : écrire les 43 clés (§ 1.2 + `price.variant` + `settings.message`) ; **chaque défaut est la constante compilée existante** (import, pas copie) sauf `langues.phoneQuotaMb` = 500 (D-W12-6) et les clés W5/W10 (défauts de conception) ; bornes du tableau § 1.2 ; `NEVER` ; `toJson()` déterministe (clés triées).
2. `SettingsBody.parse/canonical` : grammaire § 2.2 ; `reset=all` exclusif ; refus `MALFORMED` sur ordre non trié, doublon, ligne inconnue, > 64 `set=`, > 8 `exp=`.
3. `SettingsVerifier` : étapes 1-12 de § 2.3 dans l'ordre, **premier échec = motif** ; étapes 11-12 ne refusent pas le document (clés ignorées listées).
4. `SettingsEngine` : persistance atomique (`{state, seqByKid, acks(≤ 64), journal(≤ 100)}`), rejeu du même jeton ⇒ même accusé (copier la logique `PolicyEngine.kt:71-83,85-96`), un document d'une **autre** clé acceptée remplace l'état seulement si `issuedAt` plus récent ; `reset=all` ⇒ `values` vide (défauts), `experiments` vide, seq avancé.
5. `Settings` : façade ; `version()` en français.
6. Vecteurs `tools/activation/settings-vectors.json` (`castbridge-settings-vectors-v1`, clés de test **dérivées de textes publics** comme `test-vectors.json`) : ≥ 24 cas : `build-settings` (mêmes entrées → mêmes octets, dont 2 refus : hors bornes, clé NEVER), `settings` (accepté ; signature fausse ; clé sans `POLICY` ; `target=device` ; `seq` égal / inférieur ; expiré ; pas encore valable (+ 24 h) ; schéma supérieur avec clé inconnue → acceptée et ignorée ; valeur hors bornes → ignorée ; `reset=all` ; deux kid, `issuedAt` plus ancien refusé de remplacer ; jeton > 4 000 → `TOO_LARGE` ; rejeu → même accusé). `SettingsVectorsTest` rejoue tout ; `CASTBRIDGE_WRITE_VECTORS=1` régénère.
7. Tests : parité `SettingsSchema.toJson() == schema.json` ; `NEVER` (≥ 8 identifiants refusés même signés) ; fichier abîmé ⇒ état vide sans plantage ; `stale()` ; `Settings.int` renvoie le défaut si absent.

## Critères d'acceptation (hors ligne)
- Porte verte ; `SettingsVectorsTest` ≥ 24 cas ; parité `schema.json` ; aucun fichier existant modifié (`git diff --stat` ne montre que les fichiers possédés).
- Un jeton signé par une clé sans `POLICY` est refusé `KEY_NOT_ALLOWED` ; `target=device` refusé `WRONG_TARGET` ; signature fausse ⇒ état inchangé (pas les défauts).
- Rapport : liste des 43 clés avec défaut et bornes ; ce que l'audit Opus doit relire (étapes 4-9 du vérificateur, bornes €).
