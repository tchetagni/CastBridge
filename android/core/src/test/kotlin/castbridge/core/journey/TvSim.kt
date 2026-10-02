package castbridge.core.journey

import castbridge.core.FakePlayer
import castbridge.core.FakeTv
import castbridge.core.owner.TrialPolicy
import castbridge.core.trust.AttemptLimiter
import castbridge.core.trust.FileTrustPersistence
import castbridge.core.trust.HelloHandler
import castbridge.core.trust.PairingSession
import castbridge.core.trust.TrustRegistry
import castbridge.core.tv.Fs
import castbridge.core.tv.LinkInfo
import castbridge.core.tv.Pin
import castbridge.core.tv.PinGuard
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.StaticVolumes
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.TvProfile
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeRegistry
import castbridge.core.xfer.TransferHost
import java.io.File
import java.net.HttpURLConnection
import java.util.Random

/**
 * Une TV complète en processus : la VRAIE `ReceiverServer` (NanoHTTPD, port 0) sur un dossier temporaire, un `TrustRegistry` sur fichier
 * (`FileTrustPersistence`), un `PinGuard` à l'horloge du parcours, et la partie Bluetooth de `FakeTv` (HELLO réel sur flux en mémoire) branchée sur
 * LE MÊME registre. Les routes d'activation sont celles de [ActivationApiSim] (simulation).
 *
 * Fidélité : le HELLO annonce l'adresse et le PORT réels du serveur (`bt.lan = 127.0.0.1`), donc un redémarrage qui change le port est vu par le
 * téléphone comme un changement d'adresse. Limites connues : la capacité disque est DÉCLARÉE (libre = capacité - octets stockés), la vitesse
 * d'écriture [slow] n'est que ANNONCÉE à la TV (aucune lenteur réelle des octets) ; `ReceiverServer` n'a pas d'horloge injectée.
 */
class TvSim(val clock: JourneyClock, val name: String = "SMART_TV", private val scenario: TvScenario = TvScenario()) : AutoCloseable {
    data class TvScenario(
        val trial: Boolean = false,
        val freeBytes: Long = 50L shl 30,
        /** Vitesse d'écriture ANNONCÉE du disque (0 = non mesurée). */
        val writeBytesPerSec: Long = 0,
        /** La TV est saturée côté Bluetooth : le premier HELLO simple passe, les suivants reçoivent « occupé » (ERR_BUSY) jusqu'à 60 s plus tard. */
        val busy: Boolean = false,
        val pin: String = "123456",
    )

    private val dirs = ArrayList<File>()
    var dir: File = newDir(); private set
    private val media: File get() = File(dir, "media")

    /** Le HELLO Bluetooth existant ; son registre de confiance est celui de la TV (voir [registry]). */
    val bt: FakeTv = FakeTv(clock.fake).also { it.name = name; it.lan = listOf("127.0.0.1") }
    val registry: TrustRegistry get() = bt.reg
    private val activation = ActivationApiSim(scenario.trial)

    var pin: String = scenario.pin; private set
    val installId: String get() = registry.installId
    private var pinGeneration = 0

    @Volatile private var capacity: Long = scenario.freeBytes
    @Volatile private var writeBps: Long = scenario.writeBytesPerSec
    private val noticeList: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    private var server: ReceiverServer? = null
    private var guard: PinGuard = PinGuard(pin, 5, now = clock::now)

    /** Port réel du serveur en marche (-1 à l'arrêt). */
    val port: Int get() = server?.listeningPort ?: -1
    val base: String get() = "http://127.0.0.1:$port"

    /** L'hôte de transferts de la `ReceiverServer` en marche (champ privé lu par réflexion : à remplacer par un accès propre, cahier w14-05). */
    val transfers: TransferHost get() = reflect(server ?: error("TV arrêtée"), "transfers") as TransferHost

    init {
        rebuildApp()
        if (scenario.busy) { bt.useLimiter(AttemptLimiter(1, 1, now = clock::now)); installHello(); bt.limiter!!.tryAcquire(bt.phone) }
    }

    private fun newDir(): File = kotlin.io.path.createTempDirectory("tvsim").toFile().also { dirs += it }

    /**
     * « L'application démarre » : registre relu du fichier de [dir] (vide si le dossier est neuf), fenêtre d'appairage neuve, HELLO rebranché.
     * Équivaut à `FakeTv.restartApp()` mais sur un VRAI fichier (`FakeTv` ne garde qu'une persistance en mémoire).
     */
    private fun rebuildApp() {
        bt.reg = TrustRegistry(FileTrustPersistence(File(dir, "trust/trusted_phones.txt")), clock::now, tokenTtlMs = bt.ttlMs)
        bt.pairing = PairingSession(bt.reg, clock::now)
        installHello()
    }

    /** Le HELLO de `FakeTv`, reconstruit avec le port réel du serveur (`FakeTv` code 8765 en dur) ; le registre et la fenêtre d'appairage sont les siens. */
    private fun installHello() {
        bt.handler = HelloHandler(bt.reg, bt.pairing, { it in bt.bonded }, { bt.name }, "0.13", { "CastBridge TV $name" },
            { LinkInfo(port.takeIf { it > 0 } ?: 8765, bt.lan) }, {}, bt.limiter)
    }

    // ------------------------------------------------------------------------------------------------------ cycle de vie

    fun start() {
        if (server != null) return
        val provider = StaticVolumes { listOf(StorageVolume("internal", "Mémoire interne", media.also { it.mkdirs() }, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false, writeBps = writeBps)) }
        val volumes = VolumeRegistry(provider) { _ -> capacity - used(media) }.also { it.refresh() }
        val s = ReceiverServer(
            volumes = volumes, player = FakePlayer(), port = 0, profile = TvProfile(), pin = pin, guard = guard,
            extension = activation, onNotice = { noticeList += it },
            routeGuard = { path -> if (activation.trial && TrialPolicy.routeBlocked(path)) TrialPolicy.MESSAGE else null },
            tokenAuth = { t -> registry.verifyToken(t) }, hostCheck = true,
        )
        s.start(5000, false)
        server = s
        bt.appRunning = true
        installHello()
    }

    /** L'application TV est fermée : plus d'HTTP, plus de service Bluetooth (le téléphone voit « application fermée »). */
    fun stop() {
        server?.stop(); server = null
        bt.appRunning = false
    }

    /** L'application est tuée puis relancée : même dossier (registre relu du fichier, reprise des envois), NOUVEAU serveur donc nouveau port. */
    fun restart() { stop(); rebuildApp(); guard = PinGuard(pin, 5, now = clock::now); start() }

    /** Désinstallée puis réinstallée : dossier neuf, registre vide, nouveau PIN, nouvel `installId`. */
    fun reinstall() {
        stop()
        dir = newDir()
        rebuildApp()
        pin = nextPin()
        guard = PinGuard(pin, 5, now = clock::now)
        start()
    }

    /**
     * Le PIN change sans réinstallation. `ReceiverServer` reçoit son PIN à la construction : la TV est donc relancée (même dossier, même registre,
     * NOUVEAU port, compteurs de refus remis à zéro).
     */
    fun rotatePin(newPin: String = "654321") {
        require(Pin.isValidFormat(newPin) && newPin != pin) { "un nouveau PIN à six chiffres, différent de l'actuel" }
        pin = newPin
        restart()
    }

    private fun nextPin(): String {
        val rnd = Random(0x5EEDL + ++pinGeneration)
        while (true) { val p = Pin.generate(rnd); if (p != pin) return p }
    }

    // ------------------------------------------------------------------------------------------------------ scénarios

    fun setTrial(on: Boolean) = activation.switchTrial(on)

    /** Fixe l'espace libre ANNONCÉ à [freeBytes] octets à cet instant (il diminue ensuite avec les fichiers reçus). */
    fun fill(freeBytes: Long) { capacity = used(media) + freeBytes; refreshVolumes() }

    /** Annonce un disque lent ([bytesPerSec] octets/s) : la TV le dit au téléphone, aucun octet n'est réellement ralenti. */
    fun slow(bytesPerSec: Long) { writeBps = bytesPerSec; refreshVolumes() }

    private fun refreshVolumes() { runCatching { reflect(server ?: return, "volumes").let { it as VolumeRegistry }.refresh() } }

    /** Active la TV avec [payload] par `POST /api/activation/install` (PIN de la TV) ; seul [ActivationApiSim.TEST_PAYLOAD] est accepté. */
    fun activate(payload: ByteArray): Boolean {
        val c = java.net.URI.create("$base/api/activation/install").toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(payload.size)
            c.setRequestProperty("X-CB-Pin", pin)
            c.outputStream.use { it.write(payload) }
            val ok = c.responseCode == 200
            (if (ok) c.inputStream else c.errorStream)?.close()
            return ok
        } finally { c.disconnect() }
    }

    // ------------------------------------------------------------------------------------------------------ observations

    /** Les fichiers terminés reçus (dossier de la TV, sans parties `.part` ni dossiers cachés). */
    fun receivedFiles(): List<File> = media.walkTopDown()
        .filter { it.isFile && !it.name.endsWith(".part") && it.relativeTo(media).path.split(File.separatorChar).none { p -> p.startsWith(".") } }
        .sortedBy { it.relativeTo(media).path }.toList()

    /** État JSON d'un transfert multivoie en cours (équivalent de `GET /api/transfer/state`, lu sans passer par l'authentification), ou null. */
    fun transferState(id: String): String? = transfers.let { h -> h.session(id)?.let { h.stateJson(it) } }

    /**
     * Nombre de PIN faux comptés par le verrou pour l'adresse du téléphone simulé (127.0.0.1). Lu dans le `PinGuard` par réflexion (aucun accesseur
     * en production : à ajouter, cahier w14-05). Un PIN juste remet ce compteur à zéro, comme sur la vraie TV.
     */
    fun pinFailures(): Int {
        @Suppress("UNCHECKED_CAST") val entries = reflect(guard, "entries") as Map<String, Any>
        val e = entries["127.0.0.1"] ?: return 0
        return reflect(e, "failures") as Int
    }

    /** Les messages d'une ligne que la TV a affichés (fichier reçu, clé branchée…). */
    fun notices(): List<String> = synchronized(noticeList) { noticeList.toList() }

    override fun close() {
        stop()
        dirs.forEach { it.deleteRecursively() }
    }

    private fun used(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun reflect(target: Any, field: String): Any {
        val f = generateSequence<Class<*>>(target.javaClass) { it.superclass }.firstNotNullOfOrNull { c -> c.declaredFields.firstOrNull { it.name == field } }
            ?: error("champ introuvable par réflexion : ${target.javaClass.simpleName}.$field (la production a changé : brancher w14-05)")
        f.isAccessible = true
        return f.get(target)
    }
}
