package castbridge.core.journey

import castbridge.core.FakeClock

/*
 * HARNAIS DE PARCOURS JVM (W14, étage J) : une TV complète et un téléphone sans Android, ensemble, en processus.
 *
 *   withJourney {                               // this = Journey (tv, phone, files, clock) ; un TvSim démarré par test, fermé à la sortie
 *       given("une TV neuve et un téléphone sans TV") { }
 *       whenever("le propriétaire ajoute la TV") { phone.pairWithTv() }
 *       then("la puce est verte") { assertTrue(phone.chip().state.isGood) }
 *   }
 *
 * Ce que ce harnais fait VRAIMENT : la vraie `ReceiverServer` (NanoHTTPD, port 0, vraies routes, vrai `PinGuard`, vrai `TrustRegistry` sur fichiers),
 * le vrai HELLO Bluetooth (`FakeTv` : flux en mémoire), le vrai `LinkDriver`, `TransferClient` / `ResumableUpload` en HTTP réel sur 127.0.0.1.
 * Ce qu'il simule : Android (notifications, prefs, Bluetooth, Wi-Fi), l'activation (`ActivationApiSim`), le disque (capacité déclarée).
 *
 * Règles d'écriture d'un parcours :
 *  1. Aucun sommeil réel (la méthode `sleep` de `Thread`), aucune attente réelle : le temps est `JourneyClock` (le téléphone, la TV, le registre de confiance, le verrou PIN
 *     et les attentes de reprise d'envoi la partagent). Avancer le temps = `clock.advance(ms)`.
 *  2. Port 0 et dossiers temporaires uniquement (déjà le cas de `TvSim` et `Scenario`) ; jamais d'adresse privée ni de port fixe.
 *  3. Un `TvSim` par test (jamais partagé) ; `withJourney` appelle `close()` : si vous construisez les objets à la main, fermez-les.
 *  4. `ReceiverServer` lit `System.currentTimeMillis` en interne : n'attendez de lui AUCUNE logique de temps (expiration de jeton : par le registre,
 *     donc par `clock.advance`). Les limites connues de la simulation sont listées dans docs/agent-reports/sonnet-w14-01.md.
 *  5. Un test dure moins de 3 s réelles : fichiers 5 Mio (`files.small()`), 64 Mio seulement pour le multivoie (`files.big()`). `withJourney` a un
 *     chien de garde de 10 s (pile des fils puis échec) ; `UploadRun.await` n'attend jamais plus de 3 s ; tout appel HTTP du harnais passe par
 *     [Http] (délais de 2 s). Les attentes de reprise d'un envoi n'AVANCENT PAS l'horloge simulée (`PhoneSim.waitsAdvanceClock = false`) : un test
 *     qui veut l'expiration d'un jeton pendant une attente l'active EXPLICITEMENT.
 *  8. `Notice.syntheticFinal` (« Terminé » …) est une invention du harnais : AUCUNE assertion dessus tant que `XferTexts` n'est pas branché.
 *  6. Un parcours qui échoue doit dire où : écrivez-le en pas `given` / `whenever` / `then` ; l'échec porte le journal des pas déjà joués.
 *  7. Une régression CONNUE qui fait rougir un parcours : `@Ignore("R-0N : raison précise, cahier qui la corrige")`, jamais une assertion affaiblie.
 */

/**
 * L'horloge unique d'un parcours : un `AtomicLong` sûr entre les fils. [fake] est le `FakeClock` que `FakeTv` et `FakeEnv` reçoivent : ses `now` et
 * `advance` passent par le MÊME compteur atomique (aucune avance non synchronisée, d'où qu'elle vienne). `fake.t` n'est qu'un reflet tardif :
 * ne jamais le lire ni l'écrire, passer par [now] / [advance].
 */
class JourneyClock(start: Long = START_MS) {
    private val time = java.util.concurrent.atomic.AtomicLong(start)
    val fake: FakeClock = object : FakeClock(start) {
        override fun now(): Long = time.get()
        override fun advance(ms: Long) { t = time.addAndGet(ms) }
    }
    fun now(): Long = time.get()
    fun advance(ms: Long) { fake.advance(ms) }

    companion object { const val START_MS = 1_790_899_200_000L }
}

/** Appels HTTP du harnais : TOUJOURS des délais de 2 s (connexion et lecture), jamais de blocage ; le corps d'une erreur 4xx/5xx est lu aussi. */
object Http {
    const val TIMEOUT_MS = 2_000

    /** Un appel : (code, corps). Une erreur d'E/S (connexion refusée, délai) REMONTE : c'est une preuve, pas un cas à avaler. */
    fun call(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Pair<Int, String> {
        val c = java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS; c.readTimeout = TIMEOUT_MS
            c.requestMethod = method
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) { c.doOutput = true; c.setFixedLengthStreamingMode(body.size); c.outputStream.use { it.write(body) } }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() }?.decodeToString().orEmpty()
            return code to text
        } finally { c.disconnect() }
    }
}

/** Un parcours : l'horloge, la TV, le téléphone, les fichiers, et le journal des pas joués. */
class Journey(val clock: JourneyClock, val tv: TvSim, val phone: PhoneSim, val files: Scenario) : AutoCloseable {
    private val journal: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    /** Les pas joués, du plus ancien au plus récent (une ligne par pas, `OK` ou `ÉCHEC`). */
    val steps: List<String> get() = synchronized(journal) { journal.toList() }

    fun given(text: String, block: () -> Unit = {}) = run("Étant donné", text, block)
    fun whenever(text: String, block: () -> Unit) = run("Quand", text, block)
    fun then(text: String, block: () -> Unit) = run("Alors", text, block)

    private fun run(kind: String, text: String, block: () -> Unit) {
        val n = journal.size + 1
        try { block() } catch (e: Throwable) {
            journal += "$n. $kind $text : ÉCHEC"
            throw AssertionError("Parcours en échec au pas $n (« $kind $text ») : ${e.message}\nJournal des pas :\n" + journal.joinToString("\n") +
                "\nHorloge : +${clock.now() - START_MS} ms depuis le début. Journal de la TV : ${tv.notices()}", e)
        }
        journal += "$n. $kind $text : OK"
    }

    override fun close() {
        runCatching { phone.close() }
        runCatching { tv.close() }
        runCatching { files.close() }
    }

    companion object { const val START_MS = JourneyClock.START_MS }
}

/** Délai réel maximal d'un parcours entier (chien de garde de [withJourney]). */
const val JOURNEY_WATCHDOG_MS = 10_000L

/**
 * Monte une TV (démarrée sauf [startTv] = false) et un téléphone neufs, exécute [block] (`this` = le [Journey]) sous un CHIEN DE GARDE de
 * [timeoutMs] ms réelles (10 s par défaut) : au dépassement, la pile de chaque fil est décrite, le bloc est interrompu, tout est fermé et le test
 * ÉCHOUE avec cette description (jamais de blocage silencieux). Tout est fermé à la sortie, même en cas d'échec.
 */
fun withJourney(scenario: TvSim.TvScenario = TvSim.TvScenario(), seed: Long = 1, startTv: Boolean = true, timeoutMs: Long = JOURNEY_WATCHDOG_MS, block: Journey.() -> Unit) {
    val clock = JourneyClock()
    val files = Scenario(seed)
    val tv = TvSim(clock, scenario = scenario)
    val journey = try {
        if (startTv) tv.start()
        Journey(clock, tv, PhoneSim(clock, tv, seed), files)
    } catch (e: Throwable) { runCatching { tv.close() }; runCatching { files.close() }; throw e }
    val pool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "journey-block").apply { isDaemon = true } }
    try {
        val f = pool.submit { journey.block() }
        try { f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
        catch (e: java.util.concurrent.TimeoutException) {
            val dump = threadDump()
            f.cancel(true)
            throw AssertionError("Parcours bloqué : plus de $timeoutMs ms réelles. Pas joués :\n" + journey.steps.joinToString("\n") + "\nFils :\n" + dump, e)
        } catch (e: java.util.concurrent.ExecutionException) { throw e.cause ?: e }
    } finally {
        pool.shutdownNow()
        journey.close()
    }
}

/** Les piles des fils vivants (12 premiers cadres chacun), pour comprendre OÙ un parcours s'est figé. */
internal fun threadDump(): String = Thread.getAllStackTraces().entries
    .sortedBy { it.key.name }
    .joinToString("\n") { (t, st) -> "  \"${t.name}\" ${t.state}" + st.take(12).joinToString("") { "\n      at $it" } }
