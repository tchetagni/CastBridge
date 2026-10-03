package castbridge.core.journey

import castbridge.core.lots.Bundle
import castbridge.core.lots.FakeConsumer
import castbridge.core.lots.Kit
import castbridge.core.lots.LotBudget
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotId
import castbridge.core.lots.LotNames
import castbridge.core.lots.RentalApi
import castbridge.core.lots.RentalConfig
import castbridge.core.lots.RentalEngine
import castbridge.core.lots.RentalKeys
import castbridge.core.lots.RentalLedger
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.RentalSweeper
import castbridge.core.lots.RentalVault
import castbridge.core.lots.SweepReport
import castbridge.core.lots.SweepTrigger
import castbridge.core.lots.TvLotApi
import castbridge.core.lots.TvLotStore
import castbridge.core.lots.TvManifest
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.RawFactors
import castbridge.core.lots.Right
import castbridge.core.owner.SeatIds
import castbridge.core.owner.TvClock
import castbridge.core.store.FileRentRequestStore
import castbridge.core.store.StoreApi
import castbridge.core.store.StoreCatalog
import castbridge.core.store.StoreFiles
import castbridge.core.store.StoreTestKit
import castbridge.core.store.StoreView
import castbridge.core.store.RentRequests
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.then
import java.io.File

/**
 * Le côté TV de la Boutique dans le harnais de parcours (w17-05) : les VRAIES routes `StoreApi` (`/api/store*`), `TvLotApi` (`/api/lots*`) et `RentalApi`
 * (`/api/rental*`) sont chaînées dans la VRAIE `ReceiverServer` d'un [TvSim] (voir `TvScenario.extensions`), sur des dossiers sous `TvSim.dir/files`
 * (`store`, `lots`, `rental`). Les faits viennent de la TV : manifeste des lots ([TvLotStore.manifest]), contrats ([RentalLedger.status]), essai
 * ([TvSim.isTrial]) ; seul le profil enfant ([kidActive]) et les bouquets acquis ([granted]) sont posés par le test. L'heure est celle du parcours (`JourneyClock`).
 *
 * Limites (dites) : l'activation d'une location est posée directement dans le registre ([installRental]) avec une VRAIE activation signée par une clé de TEST (le
 * `ActivationApiSim` du harnais n'accepte qu'une valeur fixe) ; les clés de test sont celles de `Kit` et d'`ActivationIssuer` de TEST, jamais une clé réelle.
 */
class StoreSim(@Volatile var enabled: Boolean = true) {
    /** Les routes à passer à `TvScenario(extensions = listOf(sim.extension))` ; elles suivent l'instance courante de [StoreApi] (voir [restart]). */
    val extension: ApiExtension = object : ApiExtension {
        private val route: ApiExtension get() = wired
        override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? = route.handle(path, method, params)
        override fun wantsBody(path: String) = route.wantsBody(path)
        override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? = route.handleBody(path, method, params, body)
    }

    /** Profil enfant actif sur la TV (posé par le test). */
    @Volatile var kidActive: Boolean = false

    /** Bouquets acquis (achat ou abonnement) : seuls ceux-là sont « Sur la TV » sans location (posé par le test). */
    val granted: MutableSet<String> = java.util.Collections.synchronizedSet(LinkedHashSet())

    lateinit var tv: TvSim; private set
    lateinit var clock: JourneyClock; private set

    private lateinit var lotsStore: TvLotStore
    private lateinit var ledger: RentalLedger
    private lateinit var vault: RentalVault
    private lateinit var sweeper: RentalSweeper
    private lateinit var files: StoreFiles
    private lateinit var storeApi: StoreApi
    /** L'identité de la TV dans une demande : 16 hexadécimaux. `TrustRegistry.installId` en a 32 : voir le test ignoré R-W17-05-1 ; on prend ici les 16 premiers. */
    val tvId: String get() = tv.installId.take(16)

    /** Les demandes de la TV (une seule instance par fichier) ; recréée par [restart]. */
    lateinit var requests: RentRequests; private set
    private lateinit var wired: ApiExtension
    private val activations = java.util.Collections.synchronizedList(ArrayList<Activation>())
    private var nonceCounter = 0
    val families: LotFamilies = LotFamilies.explicit(free = emptySet(), reserved = setOf("learn:cm2", "learn:3e"))

    /** Branche la simulation sur la TV du parcours (à appeler au début du bloc de `withJourney`). */
    fun attach(tv: TvSim, clock: JourneyClock) {
        this.tv = tv; this.clock = clock
        val root = File(tv.dir, "files")
        val wall = { clock.now() }
        vault = RentalVault(File(root, "rental"))
        ledger = RentalLedger(File(root, "rental"), TvClock(), RentalConfig(), wall)
        lotsStore = TvLotStore(File(root, "lots"), mapOf("learn" to learn), listOf(Kit.pub), 10, { 0 }, LotBudget.TV_MAX_BYTES, wall)
        sweeper = RentalSweeper(ledger, vault, castbridge.core.lots.TvRentedLots(lotsStore), { synchronized(activations) { activations.toList() } }, { emptySet() }, wall)
        buildStore(root)
    }

    /** La consommatrice « Apprendre » de la TV (les lots installés y sont lisibles). */
    val learn = FakeConsumer("learn")

    private fun buildStore(root: File) {
        files = StoreFiles(File(root, "store"))
        requests = RentRequests(FileRentRequestStore(files.requestsFile()), tvId, { clock.now() }, { "%08x".format(++nonceCounter + 0x1000) })
        storeApi = StoreApi(files, listOf(Kit.pub), ::facts, requests, { enabled }, { families }, tvId)
        wired = TvLotApi(lotsStore).then(RentalApi(lotsStore, ledger, vault, { synchronized(activations) { activations.toList() } }, sweeper, families)).then(storeApi)
    }

    /** « L'application TV est relancée » : les fichiers de la Boutique sont relus, les demandes reconstruites depuis `requests.json` (un seul `RentRequests` par fichier). */
    fun restart() = buildStore(File(tv.dir, "files"))

    private fun facts(s: StoreCatalog.Store) = StoreView.Facts(s, clock.now(), StoreView.Side.TV, tvManifest = lotsStore.manifest(), rentals = rentals(), granted = granted.toSet(),
        trialTv = tv.isTrial, kidActive = kidActive)

    // ------------------------------------------------------------------------------------------------------ faits réels de la TV

    /** Les contrats de location, tels que le registre de la TV les juge à l'heure du parcours. */
    fun rentals(): List<RentalStatus> = ledger.status(synchronized(activations) { activations.toList() })

    /** Les lots présents sur la TV (manifeste réel de `TvLotStore`). */
    fun manifest(): TvManifest = lotsStore.manifest()

    /** Le fichier `requests.json` de la TV (peut ne pas exister). */
    fun requestsFile(): File = files.requestsFile()

    /** Le balayage des locations (tick périodique) : supprime les lots et les clés des contrats terminés. */
    fun sweep(): SweepReport = sweeper.sweep(SweepTrigger.PERIODIC)

    /** La Boutique lue DIRECTEMENT sur les fichiers de la TV (sans HTTP) : le catalogue actuel de la TV. */
    fun store(): StoreCatalog.Store = files.read(StoreFiles.Doc.LOTS)?.let { StoreCatalog.fromJson(it, files.read(StoreFiles.Doc.BUNDLES), families) } ?: StoreCatalog.build(emptyList(), null, families)

    /**
     * Le geste « Louer » de la TV : la TV crée elle-même la demande (aucune route : c'est le code de l'écran). Mêmes faits que `StoreApi` (essai, profil enfant,
     * rayon et familles de l'article, contrats, quota), origine `tv`.
     */
    fun requestFromTv(bundle: String, choice: String): RentRequests.Created {
        val m = facts(store())
        val item = store().items.firstOrNull { it.id == bundle }
        val f = RentRequests.Facts(m.trialTv, m.kidActive, item?.shelf ?: StoreCatalog.Shelf.AUTRES, item?.lots?.map { families.of(it.id) } ?: emptyList(), m.rentals, m.maxConcurrent, m.pilotEndMs)
        return requests.create(bundle, choice, f)
    }

    // ------------------------------------------------------------------------------------------------------ une location livrée pour de vrai

    /** Un contrat de location posé sur la TV et la clé de scellement du lot de ce contrat (celle que le bureau de TEST connaît). */
    class Rented(val contract: String, val sealKey: ByteArray)

    /**
     * Pose l'activation de location `rental|[product]|…` (heures d'utilisation [usageMinutes], jours de validité [days]) signée par la clé de TEST, dans le registre de
     * la TV (comme `/api/activation/install`, que la simulation d'activation du harnais n'accepte pas). Le contrat commence à l'heure du parcours.
     */
    fun installRental(bundle: String, days: Int, usageMinutes: Int = 0): Rented {
        val product = "loc-$bundle"
        val start = clock.now()
        val key = RentalKeys.rentalKey(MASTER, LICENSE, SeatIds.of(LICENSE, FP), product, start)
        val right = Right.Rental(product, listOf(bundle), start, start, days, 0L, usageMinutes, 0, RentalKeys.makeBox(FP, DeviceIdentity.kFor(FP.n), key, product, start))
        val a = Activation.decode(ActivationIssuer(Ed25519Signer(SIGNER_SEED)).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(FP), FP, start,
            rights = listOf(right), license = LICENSE, seat = SeatIds.of(LICENSE, FP), windowHours = 48)).token)!!
        activations += a
        ledger.install(a, synchronized(activations) { activations.toList() }, FP, vault)
        return Rented(RentalEngine.contractKey(product, start), key)
    }

    /** Un lot de [bytes] octets déterministes, scellé pour la location [rented], avec le catalogue signé de TEST qui le décrit. */
    class SealedDelivery(val name: String, val sealed: ByteArray, val catalogJson: String)

    fun sealedLot(rented: Rented, id: LotId, seed: Int, bytes: Int = 6000): SealedDelivery {
        val data = Kit.bytes(seed, bytes)
        return SealedDelivery(LotNames.fileName(id, 1), RentalKeys.seal(rented.sealKey, id, 1, data), Kit.sign(listOf(Kit.meta(id.feature, id.scope, 1, data))).toJson())
    }

    companion object {
        private const val LICENSE = "lic-journey-1"
        private val MASTER = ByteArray(32) { (it * 3 + 1).toByte() }
        private val SIGNER_SEED = ByteArray(32) { (it + 7).toByte() }
        private val FP: Fingerprints = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
            wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))

        /** Les deux catalogues d'un parcours : lots `learn:cm2` (octets réels [cm2]) et `learn:3e`, bouquets `classe-cm2` et `classe-3e`. */
        fun lotsJson(cm2: ByteArray, at: String = "2026-10-01T08:00:00Z"): String =
            Kit.sign(listOf(Kit.meta("learn", "cm2", 1, cm2), Kit.meta("learn", "3e", 1, Kit.bytes(3, 7000))), at = at).toJson()

        fun bundlesJson(at: String = "2026-09-28T10:00:00Z"): String = StoreTestKit.bundlesJson(at, listOf(
            Bundle("classe-cm2", "classe", setOf("learn:cm2"), "Classe CM2", 0, 30), Bundle("classe-3e", "classe", setOf("learn:3e"), "Classe 3e", 0, 30)))
    }
}
