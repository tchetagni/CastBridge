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
 *  5. Un test dure moins de 3 s réelles : fichiers 5 Mio (`files.small()`), 64 Mio seulement pour le multivoie (`files.big()`).
 *  6. Un parcours qui échoue doit dire où : écrivez-le en pas `given` / `whenever` / `then` ; l'échec porte le journal des pas déjà joués.
 *  7. Une régression CONNUE qui fait rougir un parcours : `@Ignore("R-0N : raison précise, cahier qui la corrige")`, jamais une assertion affaiblie.
 */

/** L'horloge unique d'un parcours : enveloppe `FakeClock` (celle de `FakeTv`), sûre entre les fils (l'envoi tourne sur son propre fil). */
class JourneyClock(start: Long = 1_790_899_200_000L) {
    val fake: FakeClock = FakeClock(start)
    @Synchronized fun now(): Long = fake.now()
    @Synchronized fun advance(ms: Long) { fake.advance(ms) }
}

/** Un parcours : l'horloge, la TV, le téléphone, les fichiers, et le journal des pas joués. */
class Journey(val clock: JourneyClock, val tv: TvSim, val phone: PhoneSim, val files: Scenario) : AutoCloseable {
    private val journal = ArrayList<String>()
    /** Les pas joués, du plus ancien au plus récent (une ligne par pas, `OK` ou `ÉCHEC`). */
    val steps: List<String> get() = journal.toList()

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

    companion object { const val START_MS = 1_790_899_200_000L }
}

/** Monte une TV (démarrée sauf [startTv] = false) et un téléphone neufs, exécute [block] (`this` = le [Journey]), puis ferme tout, même en cas d'échec. */
fun withJourney(scenario: TvSim.TvScenario = TvSim.TvScenario(), seed: Long = 1, startTv: Boolean = true, block: Journey.() -> Unit) {
    val clock = JourneyClock()
    val files = Scenario(seed)
    val tv = TvSim(clock, scenario = scenario)
    if (startTv) tv.start()
    val phone = PhoneSim(clock, tv, seed)
    Journey(clock, tv, phone, files).use { it.block() }
}
