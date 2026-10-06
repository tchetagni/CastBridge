package castbridge.core.device

/**
 * Profil de ressources d'une TV : ÉCONOME (boîtier 512 Mo, Android 32 bits, processeur lent, heap 64-128 Mo) ou NORMAL. Décision PURE, testée en JVM ;
 * la couche Android ne fait que lire `ActivityManager` / `Runtime` et appliquer les bornes. Le profil NORMAL reproduit EXACTEMENT les valeurs
 * d'avant ce profil (aucune régression sur une TV normale). Voir docs/TV-RESSOURCES-FAIBLES.md.
 *
 * Entrées inconnues : 0 (RAM, heap, memoryClass) = on ne sait pas, ce critère ne compte pas.
 * Économe si l'UN de ces critères est vrai : le système se déclare « low RAM » ; RAM totale connue <= [LOW_RAM_BYTES] (768 Mo : un boîtier 512 Mo
 * annonce ~450-500 Mo) ; heap d'application (memoryClass) connu <= [LOW_HEAP_MB] (96 Mo) ; 32 bits avec 1 ou 2 coeurs et <= 1 Go de RAM.
 * Le 32 bits seul ne suffit pas : une TV 32 bits de 1 Go à 192 Mo de heap et 4 coeurs reste NORMALE.
 */
class ResourceProfile private constructor(
    val economy: Boolean,
    val ramBytes: Long,
    val heapBytes: Long,
    val is64Bit: Boolean,
) {
    /** Valeur du champ optionnel `profile` de `/api/transfer/caps` : « low » ou « normal ». Une TV ancienne sans champ = normal. */
    val capsName: String get() = if (economy) "low" else "normal"

    /** Flux de réception simultanés (chunks) : 2 en économe, 6 sinon (= l'ancien `maxHttpThreads - 2`). Reflété par `maxStreams` des caps. */
    val maxStreams: Int get() = if (economy) 2 else 6

    /** Fils du serveur HTTP (NanoHTTPD, `BoundedRunner`, file d'attente bornée) : 8 sinon (valeur d'avant) ; 5 en économe (2 flux + lecture + interrogations). */
    val httpThreads: Int get() = if (economy) maxStreams + 3 else 8

    /** Tampon de lecture/copie (téléchargement, Bluetooth, import USB) : 32 Ko en économe, 64 Ko sinon. */
    val receiveBufferBytes: Int get() = if (economy) 32 * 1024 else 64 * 1024

    /** Tampon d'écriture d'un envoi HTTP : 128 Ko en économe, 256 Ko sinon (valeur d'avant). */
    val writeBufferBytes: Int get() = if (economy) 128 * 1024 else 256 * 1024

    /** Cache LRU des vignettes décodées, EN OCTETS : 4 Mo sinon (valeur d'avant) ; en économe 1/8 du heap, entre 1 et 3 Mo. */
    val thumbCacheBytes: Long get() = if (!economy) 4L shl 20 else (heapBytes / 8).coerceIn(1L shl 20, 3L shl 20)

    /** Fiches gardées en mémoire par la base de la bibliothèque (`LibraryDb.maxEntries`) : 500 en économe, 2000 sinon. */
    val libraryEntries: Int get() = if (economy) 500 else 2000

    /** Empreintes gardées en mémoire par l'index de contenu (`ContentIndex.maxEntries`) : 20 000 en économe, 100 000 sinon. */
    val indexEntries: Int get() = if (economy) 20_000 else 100_000

    /** Qualité du lecteur par défaut « légère » : pas de désentrelacement automatique, pas de mise à l'échelle logicielle, cache plus court. */
    val lightPlayer: Boolean get() = economy

    /** Plafond (ms) du cache libVLC d'un fichier local en cours de copie : 2 s en économe, 4 s sinon (valeur d'avant). */
    val playerCachingCapMs: Int get() = if (economy) 2_000 else 4_000

    /** Animations et effets de l'accueil (fondu enchaîné de l'arrière-plan, zoom des cartes) : coupés en économe. */
    val homeAnimations: Boolean get() = !economy

    /** Le décodeur et la surface sont rendus entièrement quand l'écran passe en arrière-plan sans lecture. */
    val releasePlayerInBackground: Boolean get() = economy

    /** Archives de contenu embarqué ouvertes à la fois (lecture à la demande, jamais tout décompressé). */
    val packsOpenAtOnce: Int get() = if (economy) 1 else 3

    /** Ligne de journal (une seule, au démarrage). */
    fun logLine(): String = "profil ressources : ${if (economy) "économe" else "normal"} (RAM ${mb(ramBytes)}, heap ${mb(heapBytes)})"

    /** Ligne de l'écran INFO de CastBridge-TV. */
    fun infoLine(): String =
        (if (economy) "économe" else "normal") + " · RAM ${mb(ramBytes)} · heap ${mb(heapBytes)} · ${if (is64Bit) "64" else "32"} bits · $maxStreams flux simultanés"

    private fun mb(b: Long) = if (b > 0) "${b shr 20} Mo" else "?"

    companion object {
        const val LOW_RAM_BYTES = 768L shl 20
        const val LOW_HEAP_MB = 96
        const val ONE_GB = 1L shl 30

        /**
         * [ramTotalBytes] : `MemoryInfo.totalMem` (0 = inconnu) ; [memoryClassMb] : `ActivityManager.memoryClass` (0 = inconnu) ; [isLowRamDevice] ;
         * [cores] : `availableProcessors()` ; [is64Bit] : au moins une ABI 64 bits ; [heapBytes] : `Runtime.maxMemory()` (0 = reprendre memoryClass).
         */
        fun decide(ramTotalBytes: Long, memoryClassMb: Int, isLowRamDevice: Boolean, cores: Int, is64Bit: Boolean, heapBytes: Long = 0): ResourceProfile {
            val ram = ramTotalBytes.coerceAtLeast(0)
            val heap = if (heapBytes > 0) heapBytes else memoryClassMb.coerceAtLeast(0).toLong() shl 20
            val slow32 = !is64Bit && cores in 1..2 && ram in 1..ONE_GB
            val low = isLowRamDevice || ram in 1..LOW_RAM_BYTES || memoryClassMb in 1..LOW_HEAP_MB || slow32
            return ResourceProfile(low, ram, heap, is64Bit)
        }

        /** Tout inconnu : normal (comportement d'avant). */
        val NORMAL: ResourceProfile = ResourceProfile(false, 0, 0, false)
    }
}
