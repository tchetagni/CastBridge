# w14-01 — Cœur : harnais de parcours JVM (`TvSim` + `PhoneSim` + `JourneyKit`) : une TV et un téléphone complets en processus
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (le harnais devient la preuve de toute la vague ; un faux positif ici vaut une régression) · statut : PRÊT
> **Groupe : W14-a** (vague W14, tranche S1) · prérequis : aucun (`integration/agents` tel quel) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.HarnessSmokeTest'`
> **Jauge : ≈ 450 k jetons entrée / 30 k sortie** (effort M+) · audit Opus : oui

**Vague 14a (cœur) · Effort M+ (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W14-BARRIERE-ANTI-REGRESSION-2026-10-02.md` § 3.1 (harnais), § 1.2 (trous), § 8 (limites). Parcours : `docs/test-plans/PARCOURS-CRITIQUES.md`. Branche `claude/sonnet-w14-01`. Rapport : `docs/agent-reports/sonnet-w14-01.md`.

## Objectif
Créer, dans les **tests** du cœur (`CT/journey/`), un harnais qui fait tourner **ensemble** une TV complète (vraie `ReceiverServer` NanoHTTPD sur port 0, `TrustRegistry` sur fichiers, `PinGuard`, `TransferHost`, HELLO Bluetooth de `FakeTv`) et un téléphone sans Android (`LinkDriver` de `LinkFixtures.Phone` dont `LinkEnv.probe/check` appellent la **vraie** `ReceiverServer` en HTTP ; `TransferClient`/`ResumableUpload` ; `PinStore` pur), avec une seule horloge injectée. Les cahiers w14-02/03/04 écrivent les parcours dessus. Trois tests de fumée du harnais prouvent qu'il marche.

## Pourquoi (preuves)
- Les régressions R-01…R-04 vivent **entre** les deux appareils ; aucun test ne traverse le couple (`DESIGN-W14` § 1.2). `CT/LinkDriverTest.kt` utilise `FakeEnv` **sans HTTP** ; `CT/TrustTest.kt:351` instancie `ReceiverServer(... tokenAuth = reg::verifyToken)` mais avec un client ad hoc ; `CT/TransferTest.kt:105` fait tourner `TransferClient` contre `ReceiverServer` mais sans liaison.
- Briques existantes à **réutiliser, jamais recopier** : `CT/LinkFixtures.kt` (`FakeClock`, `FakeTv` avec `reinstall()`, `restartApp()`, options `power/appRunning/wifiUp`, `FakeEnv`, `Phone`) ; `CT/ReceiverTest.kt:12` `FakePlayer` ; `C/trust/TrustFiles.kt` (`FileTrustPersistence`) ; `C/tv/Security.kt` (`PinGuard(pin, maxFailures)`) ; `C/xfer/TransferHost.kt` (`now` injectable) ; `C/tv/TvClient.kt` (`info()`, `checkStorage`) ; `C/xfer/TransferClient.kt` (`HttpTransferApi(baseOf, credential)`, `sleep` injectable) ; `C/owner/TrialPolicy.kt` (`routeGuard`) ; `C/trust/SendChoice.kt` (`SendFacts`, `SendChoices.decide`).
- `ReceiverServer` n'a **pas** d'horloge injectée (lit `System.currentTimeMillis`) : le harnais ne promet rien sur le temps côté HTTP ; les expirations passent par `TrustRegistry(now = clock)`.

## Fichiers possédés
Nouveaux : `CT/journey/JourneyKit.kt` (DSL, journal des pas, `withJourney { … }`), `CT/journey/TvSim.kt`, `CT/journey/PhoneSim.kt`, `CT/journey/Scenario.kt` (fichiers déterministes 5 Mio / 64 Mio par graine, options), `CT/journey/ActivationApiSim.kt` (extension `ApiExtension` qui sert `GET /api/activation` `{required,locked,trial,label,usageEndsAt}` et `POST /api/activation/install` depuis un état mutable : **simulation**, pas la vraie `ActivationCenter` Android), `CT/journey/HarnessSmokeTest.kt`. **Hors zone** : tout `C/**` (aucune classe de production modifiée par ce cahier ; si une signature manque, le noter dans le rapport pour w14-05), `CT/LinkFixtures.kt` (lecture seule : si une option manque, l'ajouter **par extension** dans `TvSim`, pas en éditant le fichier).

## Signatures à respecter (contrat pour w14-02…04, w14-10)
```kotlin
package castbridge.core.journey
class JourneyClock(start: Long = 1_790_899_200_000L) { val fake: FakeClock; fun now(): Long; fun advance(ms: Long) }
class TvSim(val clock: JourneyClock, val name: String = "SMART_TV", scenario: TvScenario = TvScenario()) : AutoCloseable {
    data class TvScenario(val trial: Boolean = false, val freeBytes: Long = 50L shl 30, val writeBytesPerSec: Long = 0, val busy: Boolean = false, val pin: String = "123456")
    val dir: java.io.File; val port: Int; val base: String  // "http://127.0.0.1:<port>"
    val registry: TrustRegistry; val transfers: TransferHost; val bt: FakeTv       // HELLO Bluetooth existant
    fun start(); fun stop(); fun restart()                 // même dir : reprise ; nouveau ReceiverServer, nouveau port
    fun reinstall()                                        // dir neuf, registre vide, nouveau PIN, nouvel installId
    fun rotatePin(newPin: String = "654321"); fun setTrial(on: Boolean); fun fill(freeBytes: Long); fun slow(bytesPerSec: Long)
    fun activate(payload: ByteArray): Boolean               // via ActivationApiSim (clé de TEST des vecteurs, jamais de clé réelle)
    fun receivedFiles(): List<java.io.File>; fun transferState(id: String): String?; fun pinFailures(): Int; fun notices(): List<String>
    val pin: String; val installId: String
}
class PhoneSim(val clock: JourneyClock, val tv: TvSim, seed: Long = 1) {
    val phone: Phone                                        // LinkFixtures.Phone, env.probe/check branchés sur tv.base (vrai HTTP)
    val pins: PinStore                                      // pur : Map<String,String> ; keysOf(tv) = nom, mDNS, "ip:port", "ip", "bt:<adr>"
    fun pairWithTv(): LinkView                              // appairage + Autoriser + HELLO ⇒ session
    fun step(trigger: LinkDriver.Trigger = Timer): LinkView; fun run(steps: Int): LinkView
    fun reassociate(); fun abandonReassociate(); fun wipe() // réinstallation du téléphone (store + saved vides, adresse conservée)
    fun enterPin(code: String): PinCheck                    // UNE requête /api/info avec X-CB-Pin ; mémorise sous toutes les clés si OK
    fun sendFacts(): SendFacts                               // faits RÉELS pour SendChoices.decide
    fun send(file: java.io.File, fast: Boolean = true, move: Boolean = false): UploadRun; fun cancel(run: UploadRun)
    fun chip(): LinkView
}
class UploadRun { val samples: List<Pair<Long, Long>> /* (sent,total) */ ; val notifications: List<Notice>; val finalState: String; val blockerText: String? ; fun await(maxMs: Long) }
data class Notice(val title: String, val text: String, val progress: Int?, val final: Boolean)
```
`Notice` est produit par `PhoneSim` à partir des états de `ResumableUpload`/`TransferClient` avec le **même** texte que `S/UploadService.kt:272` (`"$text · $pct %"`) **en attendant** `XferTexts` (w14-05) : marquer `// À BRANCHER w14-05`.

## Étapes
1. `JourneyClock` enveloppe `FakeClock` ; `TvSim` construit `ReceiverServer(volumes = VolumeRegistry(dir), player = FakePlayer, port = 0, pin, guard = PinGuard(pin, 5), tokenAuth = registry::verifyToken, routeGuard = if (trial) TrialPolicy::guard else null, extension = ActivationApiSim, hostCheck = true)` et lit le port réel (`listeningPort`). `bt = FakeTv(clock.fake, …, tvAddress)` relié au **même** `TrustRegistry` (lire comment `FakeTv` crée le sien : si non injectable, créer `FakeTv` d'abord et prendre son registre pour `tokenAuth`).
2. `restart()` : `stop()` puis nouveau `ReceiverServer` sur le **même** `dir` et un **nouveau** `TransferHost` ; `base` change (port) : `PhoneSim` ré-résout par `tv.base` (le HELLO rapporte la nouvelle adresse, comme un changement d'IP : P-18).
3. `PhoneSim` : `FakeEnv` dérivé où `probe(base)` = `TvClient(base, null).hello()` et `check(base, token)` = `TvClient(base, token).info()` sur le vrai serveur (statut 200 ⇒ `TokenCheck.Ok`, 401 ⇒ `Rejected`, IOException ⇒ `Unreachable`) ; `wifiUp` suit l'option.
4. `send()` : `ResumableUpload` (envoi classique) ou `TransferClient` (`fast`) avec `credential = phone.driver.credential(address) ?: pins.get(key)` (**reproduire** la résolution de `S/PinStore.kt:22` : jeton d'abord, PIN sinon, `""` ⇒ `Missing`) ; `sleep` injecté = `clock.advance`.
5. `HarnessSmokeTest` : (a) `tv.start()` ⇒ `GET /api/hello` 200 et `pinRequired=true` ; (b) `phone.pairWithTv()` ⇒ `chip().state.isGood` et `GET /api/info` 200 avec jeton ; (c) `send(small)` ⇒ `receivedFiles()` = 1, taille exacte, `samples` monotone, dernier `Notice.final == true`. Chaque test ≤ 3 s réels.
6. `README` en tête de `JourneyKit.kt` (20 lignes) : comment écrire un parcours, règles (pas de `Thread.sleep`, port 0, dir temporaire, un `TvSim` par test, `close()`).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn 'Thread.sleep' android/core/src/test/kotlin/castbridge/core/journey/ | wc -l` ⇒ 0 ; `grep -rn 'import android' android/core/src/test/kotlin/castbridge/core/journey/ | wc -l` ⇒ 0 ; `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test` complet vert (rien d'existant cassé) ; durée de `HarnessSmokeTest` < 10 s (rapport Gradle).

## Cas limites
Port 0 et `hostCheck` : `ReceiverServer` doit accepter `Host: 127.0.0.1:<port>` (vérifier `HostGuard`) ; `FakeTv` ferme ses flux `Piped*` entre deux HELLO (réutiliser son cycle) ; fichier de 64 Mio aléatoire : générer par graine, ne pas committer de binaire ; `reinstall()` doit aussi changer `installId` (lire `TrustRegistry.installId`).

## À ne pas faire
Aucune classe de production modifiée ; aucun faux HTTP en mémoire (D-W14-2) ; aucune dépendance ; aucune clé réelle (les vecteurs `tools/activation/test-vectors.json` fournissent une activation de TEST) ; ne pas écrire les parcours J-01…J-20 (w14-02/03/04).

## Rapport
`STATUT`, signatures livrées vs contrat (écarts motivés), ce que `TvSim` n'a pas pu simuler (liste pour w14-05 : signatures manquantes dans `C/**`), durée des 3 tests, questions pour l'audit Opus (fidélité de `check()` vs `S/LinkAndroid.kt:85-93`).
