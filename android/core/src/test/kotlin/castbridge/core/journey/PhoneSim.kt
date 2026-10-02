package castbridge.core.journey

import castbridge.core.FakeEnv
import castbridge.core.trust.BondState
import castbridge.core.trust.LinkDriver
import castbridge.core.trust.LinkEnv
import castbridge.core.trust.LinkMachine
import castbridge.core.trust.LinkSession
import castbridge.core.trust.LinkStart
import castbridge.core.trust.LinkView
import castbridge.core.trust.MemoryLinkStore
import castbridge.core.trust.MemoryTrustPersistence
import castbridge.core.trust.PairEnv
import castbridge.core.trust.PairFlow
import castbridge.core.trust.PairStep
import castbridge.core.trust.PairingSession
import castbridge.core.trust.PhoneLink
import castbridge.core.trust.PinCheck
import castbridge.core.trust.SavedTv
import castbridge.core.trust.SavedTvs
import castbridge.core.trust.SendFacts
import castbridge.core.trust.TokenCheck
import castbridge.core.trust.Trigger
import castbridge.core.trust.TrustRegistry
import castbridge.core.trust.TvAuth
import castbridge.core.trust.TvCredential
import castbridge.core.tv.Pin
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import castbridge.core.xfer.FileBlockSource
import castbridge.core.xfer.HttpConn
import castbridge.core.xfer.HttpTransferApi
import castbridge.core.xfer.TransferClient
import castbridge.core.xfer.WifiLane
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.Random
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * Ce que la barre de notification aurait montré : même texte que `S/UploadService.kt` (`"$text · $pct %"`). La notification finale est une
 * AJOUTE du harnais (le service réel n'en publie pas à la fin d'un envoi réseau) : voir le rapport w14-01.
 */
data class Notice(val title: String, val text: String, val progress: Int?, val final: Boolean)

/** Un envoi lancé par [PhoneSim.send] : ce qui est parti, ce qui a été « notifié », comment il s'est terminé. */
class UploadRun internal constructor(val file: File) {
    private val lock = Any()
    private val sampleList = ArrayList<Pair<Long, Long>>()
    private val noticeList = ArrayList<Notice>()
    /** (octets envoyés, total) dans l'ordre des mesures de progression. */
    val samples: List<Pair<Long, Long>> get() = synchronized(lock) { sampleList.toList() }
    val notifications: List<Notice> get() = synchronized(lock) { noticeList.toList() }
    /** `running`, puis `done`, `failed` ou `cancelled`. */
    @Volatile var finalState: String = "running"; internal set
    /** Le texte qui dirait POURQUOI l'envoi n'avance pas (dernière attente ou échec), ou null. */
    @Volatile var blockerText: String? = null; internal set
    /** Un déplacement terminé : la TV a le fichier complet de la bonne taille et l'original du téléphone a été retiré. */
    @Volatile var moved: Boolean = false; internal set
    @Volatile internal var cancelled = false
    internal lateinit var worker: Thread

    internal fun sample(sent: Long, total: Long) = synchronized(lock) { sampleList += sent to total }
    internal fun notify(n: Notice) = synchronized(lock) { noticeList += n }

    /** Attend la fin de l'envoi au plus [maxMs] millisecondes RÉELLES (le temps simulé n'avance que par les attentes de reprise de l'envoi). */
    fun await(maxMs: Long) { worker.join(maxMs) }
}

/** Les codes mémorisés par le téléphone, sans Android : une table clé → code (même rôle que `S/PinStore.kt`). */
class PinStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun get(key: String?): String = key?.let { map[it] }.orEmpty()
    fun put(key: String, pin: String) { map[key] = pin }
    fun clear() = map.clear()
    val all: Map<String, String> get() = map.toMap()

    /** Les clés sous lesquelles l'application range le code d'une TV : nom, nom mDNS, `ip:port`, `ip`, `bt:<adresse>`. */
    fun keysOf(tv: TvSim): List<String> = listOf(tv.name, "CastBridge TV ${tv.name}", "127.0.0.1:${tv.port}", "127.0.0.1", "bt:${tv.bt.tvAddress}")
}

/**
 * Le « téléphone » de [LinkEnv] : tout vient de `FakeEnv` (Bluetooth, appairage, réseau, premier plan : mêmes interrupteurs), SAUF les deux appels
 * HTTP qui vont à la VRAIE `ReceiverServer` avec la logique de `S/LinkAndroid.kt:70-93` : `probe` = `GET /api/hello` 200 contenant `castbridge-tv` ;
 * `check` = `GET /api/info` avec le jeton, 401 « bad token » = refusé, toute autre réponse = bon, erreur d'E/S = injoignable.
 */
internal class JourneyEnv(private val fake: FakeEnv, private val tv: TvSim) : LinkEnv by fake {
    /** La TV est-elle physiquement joignable par Wi-Fi (réseau du téléphone, TV allumée, application ouverte, Wi-Fi de la TV) ? */
    private fun reachable() = fake.network && tv.bt.apiAnswers()

    override fun probe(base: String): Boolean {
        fake.probes++
        if (!reachable()) return false
        return runCatching {
            val c = java.net.URI.create("$base/api/hello").toURL().openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 1500; c.readTimeout = 1500
                c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    override fun check(base: String, token: String): TokenCheck {
        if (!reachable()) return TokenCheck.UNREACHABLE
        return try {
            val c = java.net.URI.create("$base/api/info").toURL().openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 1500; c.readTimeout = 1500
                TvCredential.apply(c, token)
                val code = c.responseCode
                val body = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() }?.decodeToString().orEmpty()
                if (code == 401 && "bad token" in body) TokenCheck.TOKEN_REJECTED else TokenCheck.OK
            } finally { c.disconnect() }
        } catch (e: IOException) { TokenCheck.UNREACHABLE }
    }
}

/**
 * Le téléphone d'un parcours, sans Android, branché sur [tv] : le vrai `LinkDriver` (HELLO Bluetooth de `FakeTv`, puis HTTP réel pour la liaison Wi-Fi),
 * le vrai `PairFlow`, `TransferClient` / `ResumableUpload` en HTTP réel, des faits d'envoi construits pour `SendChoices.decide`.
 *
 * ÉCART au contrat du cahier : `LinkFixtures.Phone` construit son propre `FakeEnv` sans point d'injection ; le harnais refait donc le même câblage
 * (carnet de TV, mémoire de liaison, `PhoneLink`, `LinkDriver`) avec [JourneyEnv] ; [env] reste le `FakeEnv` dont les interrupteurs
 * (`bt`, `bonds`, `network`, `fg`, `candidates`) commandent la liaison, comme `Phone.env`.
 */
class PhoneSim(val clock: JourneyClock, val tv: TvSim, seed: Long = 1) : AutoCloseable {
    val env: FakeEnv = FakeEnv(clock.fake, tv.bt)
    private val jenv = JourneyEnv(env, tv)
    private val machine = LinkMachine()
    private val rnd = Random(seed)

    /** Les codes mémorisés (jamais les jetons : ceux-ci sont dans [store], comme `LinkStore` sur Android). */
    val pins = PinStore()
    var saved: SavedTvs = SavedTvs(MemoryTrustPersistence()); private set
    var store: MemoryLinkStore = MemoryLinkStore(); private set
    var link: PhoneLink = newLink(); private set
    var driver: LinkDriver = newDriver(); private set

    /** Dernier pas publié par la boucle de liaison (null avant le premier) et sa vue. */
    var lastStep: LinkDriver.Step? = null; private set
    val lastView: LinkView? get() = lastStep?.view
    /** Les clés d'état vues, sans doublons consécutifs (comme `Phone.shownHistory`). */
    val shownHistory = ArrayList<String>()
    /** Le dernier résultat de l'appairage (null : aucun). */
    var lastPair: PairStep? = null; private set

    /** Nom de la TV du chemin « code » (mDNS) une fois qu'un code a été saisi, null sinon. */
    var pinTvName: String? = null; private set
    private var pinCheck = PinCheck.UNKNOWN
    private var pinCheckedBase: String? = null
    private val runs = ArrayList<UploadRun>()

    private fun newLink() = PhoneLink(tv.bt, { jenv.probe(it) }, now = clock::now)
    private fun newDriver() = LinkDriver(link, jenv, saved, store, machine, { rnd.nextDouble() })

    private val tvAddress get() = tv.bt.tvAddress

    // ------------------------------------------------------------------------------------------------------ liaison

    /** « Ajouter ma TV » : le propriétaire ouvre « Ajouter un téléphone » et appuie sur « Autoriser » ; appairage, HELLO, session adoptée. */
    fun pairWithTv(approve: Boolean = true): LinkView {
        val pairing = tv.bt.pairing
        pairing.open()
        val owner: (PairingSession.State) -> Unit = { s -> if (s is PairingSession.State.Asking) { if (approve) pairing.approve() else pairing.deny() } }
        pairing.addListener(owner)
        try {
            val known = saved.get(tvAddress) ?: SavedTv(tvAddress, tv.name, addedAt = clock.now())
            val r = PairFlow(link, BondSim()).run(known) { }
            lastPair = r
            if (r is PairStep.Done) driver.adopt(r.session)
        } finally { pairing.removeListener(owner) }
        return step(Trigger.USER)
    }

    /** Un pas de la boucle de liaison (ce que l'application fait à chaque réveil) et la vue qui en sort. */
    fun step(trigger: Trigger = Trigger.TIMER): LinkView {
        val s = driver.step(trigger)
        lastStep = s
        if (shownHistory.lastOrNull() != s.view.state.key) shownHistory += s.view.state.key
        return s.view
    }

    /** [steps] pas, le temps avançant de ce que le pilote demande entre deux (au moins 1 s, comme `minGapMs`). */
    fun run(steps: Int): LinkView {
        var v = chip()
        repeat(steps) {
            clock.advance((lastStep?.nextInMs ?: 60_000L).coerceIn(1_000L, 3_600_000L))
            v = step(Trigger.TIMER)
        }
        return v
    }

    /** « Réassocier » mené à terme : la TV reste listée pendant l'opération ; le propriétaire autorise ; session neuve. */
    fun reassociate() {
        driver.prepareReassociate(tvAddress) ?: error("aucune TV enregistrée à réassocier")
        pairWithTv()
    }

    /** « Réassocier » abandonné : l'utilisateur quitte avant « Autoriser ». Seul ce que la TV refuse part ; la TV reste dans le carnet. */
    fun abandonReassociate() { driver.prepareReassociate(tvAddress) ?: error("aucune TV enregistrée à réassocier") }

    /** Réinstallation du téléphone : carnet de TV, jetons, codes et état de liaison vides ; l'association Bluetooth du système (`env.bonds`) reste. */
    fun wipe() {
        saved = SavedTvs(MemoryTrustPersistence()); store = MemoryLinkStore(); pins.clear()
        pinTvName = null; pinCheck = PinCheck.UNKNOWN; pinCheckedBase = null
        link = newLink(); driver = newDriver()
        lastStep = null; lastPair = null
    }

    /** Ce que l'écran d'accueil dessine : `LinkStart.view` sur les vraies données du carnet et du dernier pas ; « Aucune TV ajoutée » seulement sans TV. */
    fun chip(): LinkView = LinkStart.view(saved.list().size, saved.default()?.name, lastView) ?: machine.view(machine.initial(false))

    // ------------------------------------------------------------------------------------------------------ code (PIN)

    /**
     * Le code saisi : UNE requête `GET /api/info` avec `X-CB-Pin` (jamais répétée : la TV verrouille après cinq refus). Juste : le code est mémorisé
     * sous toutes les clés de la TV. Un code mal formé n'est pas envoyé ([PinCheck.UNKNOWN]).
     */
    fun enterPin(code: String): PinCheck {
        pinTvName = "CastBridge TV ${tv.name}"
        if (!Pin.isValidFormat(code)) return PinCheck.UNKNOWN.also { pinCheck = it }
        val r = pinRequest(code)
        pinCheck = r; pinCheckedBase = tv.base
        if (r == PinCheck.OK) pins.keysOf(tv).forEach { pins.put(it, code) }
        return r
    }

    private fun pinRequest(credential: String): PinCheck {
        if (!env.network || !tv.bt.apiAnswers() || tv.port < 0) return PinCheck.UNREACHABLE
        return try {
            TvClient(tv.base, credential).info(); PinCheck.OK
        } catch (e: TvClient.HttpError) {
            if (e.code == 401) (if ("locked" in e.message.orEmpty()) PinCheck.LOCKED else PinCheck.REJECTED) else PinCheck.UNREACHABLE
        } catch (e: IOException) { PinCheck.UNREACHABLE }
    }

    /** Ce que l'application présente à la TV : le jeton de confiance d'abord, le code sinon, `""` (inutilisable, `Missing`) sinon. */
    fun credentialNow(): String =
        driver.credential(tvAddress) ?: pins.keysOf(tv).firstNotNullOfOrNull { pins.get(it).takeIf { c -> c.isNotEmpty() } } ?: ""

    // ------------------------------------------------------------------------------------------------------ « Ouvrir avec »

    /**
     * Les faits RÉELS de `OpenWithActivity` pour `SendChoices.decide`. Comme l'écran : sans TV de confiance enregistrée mais avec un code utilisable,
     * UNE vérification du code est faite (une seule par adresse de TV vue) ; sinon aucune requête.
     */
    fun sendFacts(): SendFacts {
        val view = lastView
        val session: LinkSession? = lastStep?.session?.takeIf { view?.state?.isGood == true }
        val cred = if (pinTvName != null) credentialNow() else ""
        if (pinTvName != null && saved.list().isEmpty() && TvAuth.isUsable(cred) && pinCheckedBase != tv.base) {
            pinCheckedBase = tv.base; pinCheck = pinRequest(cred)
        }
        return SendFacts(
            savedCount = saved.list().size, defaultName = saved.default()?.name, stepView = view,
            session = session != null, sessionName = session?.tv?.name, btOnly = session != null && session.base == null,
            pinTvName = pinTvName, pinStored = pinTvName != null && TvAuth.isUsable(cred), pinCheck = pinCheck,
        )
    }

    // ------------------------------------------------------------------------------------------------------ envoi

    /** Attente d'une reprise : le temps SIMULÉ avance de [ms] ; quelques millisecondes réelles seulement, pour ne pas tourner à vide. */
    private fun sleep(ms: Long) { clock.advance(ms); LockSupport.parkNanos(5_000_000L) }

    private fun resolve(): String? = if (env.network && tv.bt.apiAnswers() && tv.port > 0) tv.base else null

    /**
     * Envoie [file] comme l'application : multivoie d'abord si [fast] (repli sur l'envoi classique si la TV ne le connaît pas), l'envoi classique
     * sinon. [move] : une fois fini, l'original n'est retiré que si la TV annonce le fichier COMPLET de la même taille.
     * Le fil de l'envoi tourne seul : [UploadRun.await] pour l'attendre.
     */
    fun send(file: File, fast: Boolean = true, move: Boolean = false): UploadRun {
        val run = UploadRun(file)
        runs += run
        val total = file.length()
        val name = file.name
        var lastSent = 0L
        fun pct(sent: Long) = if (total > 0) (sent * 100 / total).toInt().coerceIn(0, 100) else 0
        // À BRANCHER w14-05 : les textes viendront de XferTexts ; en attendant, ceux de S/UploadService.kt:272.
        fun uploading(sent: Long, t: Long) { lastSent = sent; run.sample(sent, t); run.notify(Notice("CastBridge", "Envoi vers la TV · ${pct(sent)} %", pct(sent), false)) }
        fun waiting(why: String) { run.blockerText = why; run.notify(Notice("CastBridge", "En attente du réseau ($why) · ${pct(lastSent)} %", pct(lastSent), false)) }
        fun done() { run.finalState = "done"; run.blockerText = null; run.notify(Notice("CastBridge", "Terminé", 100, true)) }
        fun failed(why: String, cancelled: Boolean) {
            run.finalState = if (cancelled) "cancelled" else "failed"; run.blockerText = why
            run.notify(Notice("CastBridge", if (cancelled) "Envoi annulé" else "Envoi interrompu : $why", null, true))
        }
        fun classic() {
            val up = ResumableUpload(name, total, { resolve() }, { off -> FileInputStream(file).also { it.channel.position(off) } },
                cancelled = { run.cancelled }, sleep = ::sleep, credential = { credentialNow() })
            when (val r = up.run { s ->
                when (s) {
                    is ResumableUpload.State.Uploading -> uploading(s.sent, s.total)
                    is ResumableUpload.State.Waiting -> { lastSent = s.sent; waiting(s.reason) }
                    else -> Unit
                }
            }) {
                ResumableUpload.State.Done -> done()
                is ResumableUpload.State.Failed -> failed(r.reason, run.cancelled)
                else -> failed("état inattendu", false)
            }
        }
        run.worker = thread(isDaemon = true, name = "journey-send-$name") {
            try {
                if (!fast) classic() else {
                    val result = FileChannel.open(file.toPath(), StandardOpenOption.READ).use { ch ->
                        val connect: () -> java.nio.channels.SocketChannel = {
                            val u = URI(resolve() ?: throw IOException("TV introuvable"))
                            HttpConn.tcp(u.host, u.port)()
                        }
                        TransferClient(HttpTransferApi({ resolve() }, { credentialNow() }), FileBlockSource(ch, total), name,
                            lanes = { id, max -> listOf(WifiLane("wifi", resolve()?.removePrefix("http://") ?: "tv", id, connect, { credentialNow() }, maxStreams = max, fixedK = 4)) },
                            cancelled = { run.cancelled }, onProgress = { s, t -> uploading(s, t) }, onWaiting = { why -> waiting(why) },
                            sleep = ::sleep).run()
                    }
                    when (result) {
                        TransferClient.Result.Done -> done()
                        TransferClient.Result.Unsupported -> classic()
                        TransferClient.Result.Cancelled -> failed("annulé", true)
                        is TransferClient.Result.Failed -> failed(result.reason, false)
                    }
                }
            } catch (e: Throwable) { failed(e.message ?: e.javaClass.simpleName, false) }
            if (move && run.finalState == "done") {
                // jamais de suppression « sur parole » : la TV doit annoncer le fichier complet, de la même taille
                val p = runCatching { TvClient(tv.base, credentialNow().takeIf { it.isNotEmpty() }).part(name) }.getOrNull()
                if (p != null && p.done && p.length == total && file.delete()) run.moved = true
            }
        }
        return run
    }

    fun cancel(run: UploadRun) { run.cancelled = true }

    override fun close() {
        runs.forEach { it.cancelled = true }
        runs.forEach { r -> runCatching { r.worker.join(3000) } }
    }

    /** L'appairage Bluetooth d'Android, simulé comme dans `PairFlowTest` : créer l'association, ou la retirer quand l'utilisateur le fait. */
    private inner class BondSim : PairEnv {
        override fun btProblem() = env.bt
        override fun bond(address: String) = env.bond(address)
        override fun createBond(address: String): Boolean { env.bonds[TrustRegistry.norm(address)] = BondState.BONDING; return true }
        override fun awaitBond(address: String, target: BondState, timeoutMs: Long): BondState {
            val a = TrustRegistry.norm(address)
            val cur = env.bond(address)
            if (target == BondState.BONDED && cur == BondState.BONDING) { env.bonds[a] = BondState.BONDED; tv.bt.bonded += tv.bt.phone; clock.advance(5_000) }
            if (target == BondState.NONE && cur == BondState.BONDED) { env.bonds[a] = BondState.NONE; tv.bt.staleBond = false; tv.bt.bonded -= tv.bt.phone; clock.advance(20_000) }
            return env.bond(address)
        }
        override fun sleep(ms: Long) = clock.advance(ms)
        override fun now() = clock.now()
    }
}
