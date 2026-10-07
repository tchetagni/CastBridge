package castbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import castbridge.core.tv.MediaState
import castbridge.core.tv.MountRetry
import castbridge.core.tv.UsbPhase
import castbridge.core.tv.UsbVerdict
import castbridge.core.tv.UsbVolumeState
import castbridge.core.tv.UsbVolumeTracker
import castbridge.core.ux.UsbNote
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * L'état de chaque clé USB, tel qu'Android le dit (docs/STORAGE.md § « Clé USB mal éjectée : ce que la TV peut et ne peut pas faire »). Au branchement `vold` lance `fsck` (`checking`), puis monte
 * (`mounted`) ou refuse (`unmountable`) ; retirée sans éjection, la clé donne `bad_removal`. Une application ne peut ni réparer ni démonter un volume : ce récepteur LIT l'état (diffusions
 * `ACTION_MEDIA_*`, rappel de volume, `StorageManager.storageVolumes` au démarrage et toutes les 5 s tant qu'une clé est là) et le publie, un état par clé ; toutes les décisions
 * (lignes, durées, priorités) sont dans le cœur pur ([UsbVolumeTracker], [UsbVolumeState], [MountRetry]), testé.
 *
 * Lecteurs de cet état : la ligne d'état et la pastille de l'accueil, la bannière de l'écran d'activation (attend le montage et dit pourquoi), la bibliothèque et l'explorateur
 * (« patientez » pendant la vérification, le guide si la clé est illisible), la boîte « Préparer le retrait de la clé USB ». Un lecteur qui a échoué pendant la vérification est relancé UNE FOIS
 * au montage ([readerFailed], [setMountListener]). Aucune permission nouvelle, aucun `su`, aucun nom de fichier dans les journaux.
 */
object UsbVolumeWatch {
    private const val TAG = "UsbVolumeWatch"
    private const val PREF_DIRTY = "usb_bad_removal"
    private const val STORAGE_EXTRA = "android.os.storage.extra.STORAGE_VOLUME"
    /** The name under which the library waits for a mount (the explorer and the activation search have their own: [setMountListener]). */
    private const val LIBRARY = "bibliothèque"

    /** Une clé et son verdict ; [volumeId] = l'identifiant du volume pour le registre de la TV (« usb-A379-E209 »). */
    data class Entry(val id: String, val label: String, val verdict: UsbVerdict) { val volumeId: String get() = "usb-$id" }

    private val started = AtomicBoolean(false)
    private lateinit var app: Context
    private lateinit var tracker: UsbVolumeTracker
    private val retry = MountRetry()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    /** Ce que chaque lecteur refait au montage (une fois par montage) : « activation », « explorateur », « bibliothèque ». */
    private val mountListeners = ConcurrentHashMap<String, () -> Unit>()
    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var signature = ""
    private var ticking = false
    private var ticks = 0

    /** Une seule fois, depuis `TvApp.onCreate` (fil principal). */
    fun start(ctx: Context) {
        if (!started.compareAndSet(false, true)) return
        app = ctx.applicationContext
        val prefs = TvPrefs(app)
        tracker = UsbVolumeTracker(SystemClock::elapsedRealtime, (prefs.getString(PREF_DIRTY, "") ?: "").split(',').filter { it.isNotEmpty() }.toSet()) { ids ->
            prefs.putString(PREF_DIRTY, ids.joinToString(","))
        }
        // la bibliothèque se relit une fois au montage : la TV le fait déjà (diffusion « media mounted ») ; ce réessai couvre la diffusion perdue pendant la vérification
        setMountListener(LIBRARY) { TvService.running?.rescanAsync(remeasure = true) }
        registerBroadcasts()
        registerVolumeCallback()
        refresh()
    }

    // ---- les lecteurs de l'état ----

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Les clés qui ont quelque chose à dire (la liste est celle du dernier relevé). */
    fun entries(): List<Entry> = if (started.get()) entries else emptyList()

    private fun verdicts(): List<UsbVerdict> = entries().map { it.verdict }

    /** La ligne d'état de l'accueil (null = rien à dire) : voir [UsbVolumeState.chipLine]. [receiving] = la ligne de réception en cours, [activationLine] = celle de la clé d'activation. */
    fun chipLine(receiving: String?, activationLine: String?): String? = UsbVolumeState.chipLine(receiving, verdicts(), activationLine)

    /** Ce que la pastille de l'accueil retient de l'état des clés (null = rien). */
    fun signalNote(): UsbNote? = UsbVolumeState.signal(verdicts())

    /** Une clé qu'Android vérifie maintenant, après une lecture fraîche du système (null = aucune). */
    fun checking(): Entry? { refresh(); return entries().firstOrNull { it.verdict.phase == UsbPhase.CHECKING } }

    /** Une clé qu'Android n'a pas pu monter (illisible, ou sans système de fichiers connu). */
    fun damaged(): Entry? = entries().firstOrNull { it.verdict.phase == UsbPhase.DAMAGED || it.verdict.phase == UsbPhase.NO_FILESYSTEM }

    /** Le premier avis à montrer à l'explorateur ou à la bibliothèque : vérification en cours, ou clé illisible ; null = tout va bien. */
    fun attention(): Entry? = entries().firstOrNull { it.verdict.phase == UsbPhase.CHECKING || it.verdict.phase == UsbPhase.DAMAGED || it.verdict.phase == UsbPhase.NO_FILESYSTEM }

    /**
     * Les réglages de stockage de la TV, le plus précis d'abord (« Réparer », « Éjecter » et « Formater » y sont quand la TV les propose : une application ne peut rien de tout cela elle-même).
     * Faux = aucun écran de ce genre sur cette TV. Ouvre depuis l'écran qui le demande ([ctx] = cet écran).
     */
    fun openStorageSettings(ctx: Context): Boolean {
        for (action in listOf(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "android.settings.MEMORY_CARD_SETTINGS", android.provider.Settings.ACTION_SETTINGS)) {
            val i = Intent(action).apply { if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        }
        return false
    }

    /**
     * La ligne de la bibliothèque : « patientez » pendant la vérification, le guide si la clé est illisible ; null = rien à dire. Appelée par la bibliothèque AFFICHÉE : tant que la clé est
     * vérifiée, elle ne peut pas la lister ; elle se relit donc UNE fois quand la clé est montée ([readerFailed], un seul réessai par montage).
     */
    fun libraryLine(): String? {
        val e = attention() ?: return null
        if (e.verdict.phase == UsbPhase.CHECKING) readerFailed(LIBRARY)
        return e.verdict.line
    }

    /** Le mot court de la tuile « Clé USB » quand aucune clé n'est montée mais qu'Android s'en occupe (« Aucune clé » serait faux) ; null = pas de clé en cours de route. */
    fun tileStatus(): String? = when (attention()?.verdict?.phase) {
        UsbPhase.CHECKING -> "Vérification par Android…"
        UsbPhase.DAMAGED, UsbPhase.NO_FILESYSTEM -> "Clé illisible"
        else -> null
    }

    /** Les clés montées (lisibles) : celles qu'on peut préparer au retrait. */
    fun mountedKeys(): List<Entry> = entries().filter { it.verdict.readable }

    fun stateOf(id: String): MediaState? = if (started.get()) tracker.stateOf(id) else null

    /** « Préparer le retrait » est fini ([on]) ou abandonné pour la clé [id]. */
    fun setPullReady(id: String, on: Boolean) { if (started.get()) { tracker.setPullReady(id, on); publish() } }

    // ---- le réessai au montage ----

    /** Ce que [reader] refait au montage d'une clé (un seul réessai par montage). */
    fun setMountListener(reader: String, again: () -> Unit) { mountListeners[reader] = again }
    fun removeMountListener(reader: String) { mountListeners.remove(reader) }

    /** [reader] vient d'échouer : s'il y a une clé en vérification, il sera relancé une fois quand elle sera montée. */
    fun readerFailed(reader: String) {
        if (!started.get()) return
        for (e in entries().filter { it.verdict.phase == UsbPhase.CHECKING }) retry.failed(reader, e.id, MediaState.CHECKING)
    }

    // ---- les sources de l'état ----

    private fun registerBroadcasts() {
        val f = IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_CHECKING, Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTABLE, Intent.ACTION_MEDIA_BAD_REMOVAL, Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_REMOVED, Intent.ACTION_MEDIA_NOFS, Intent.ACTION_MEDIA_SHARED).forEach { addAction(it) }
            addDataScheme("file")
        }
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { runCatching { onBroadcast(i) }.onFailure { Log.w(TAG, "diffusion : ${it.javaClass.simpleName}") } } }
        runCatching { if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f) }
            .onFailure { Log.w(TAG, "récepteur : ${it.javaClass.simpleName}") }
    }

    private fun registerVolumeCallback() {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val cb = object : StorageManager.StorageVolumeCallback() { override fun onStateChanged(volume: StorageVolume) { observe(volume) } }
            app.getSystemService(StorageManager::class.java).registerStorageVolumeCallback(app.mainExecutor, cb)
        }.onFailure { Log.w(TAG, "rappel de volume : ${it.javaClass.simpleName}") }
    }

    /** Une clé amovible, pas la mémoire interne ni une clé « adoptée » (qu'Android traite comme de la mémoire interne). */
    private fun isKey(v: StorageVolume): Boolean = v.isRemovable && !v.isPrimary

    /** Identifiant stable : l'UUID du volume ; sans UUID (clé illisible) : le nom qu'Android lui donne. */
    private fun keyOf(v: StorageVolume): String = v.uuid ?: ("x" + (v.getDescription(app) ?: "").hashCode().toUInt().toString(16))

    private fun labelOf(v: StorageVolume): String = runCatching { v.getDescription(app) }.getOrNull().orEmpty()

    @Suppress("DEPRECATION")
    private fun volumeOf(i: Intent): StorageVolume? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(STORAGE_EXTRA, StorageVolume::class.java) else i.getParcelableExtra<StorageVolume>(STORAGE_EXTRA)
    }.getOrNull()

    private fun onBroadcast(i: Intent) {
        val state = MediaState.ofAction(i.action, i.getBooleanExtra("read-only", false)) ?: return
        val v = volumeOf(i)
        val id = (if (v != null) { if (!isKey(v)) return; keyOf(v) } else i.data?.path?.substringAfter("/storage/", "")?.substringBefore('/')?.takeIf { it.isNotEmpty() && it != "emulated" && it != "self" }) ?: return
        val label = v?.let(::labelOf) ?: entries().firstOrNull { it.id == id }?.label.orEmpty()
        // une clé retirée en pleine copie : le serveur sait quel fichier était en cours (jamais journalisé)
        val cut = if (state == MediaState.BAD_REMOVAL) interruptedOn(id) else null
        Log.i(TAG, "clé : ${state.wire}")
        apply(tracker.seen(id, label, state, cut))
        publish()
    }

    private fun observe(v: StorageVolume) {
        if (!isKey(v)) return
        val st = MediaState.parse(v.state)
        if (st == MediaState.UNKNOWN) return
        apply(tracker.seen(keyOf(v), labelOf(v), st))
        publish()
    }

    /** Relit l'état de toutes les clés auprès du système (au démarrage, à chaque question d'un lecteur, et toutes les 5 s). */
    @Synchronized fun refresh() {
        if (!started.get()) return
        val sm = app.getSystemService(StorageManager::class.java) ?: return
        val keys = runCatching { sm.storageVolumes.filter(::isKey) }.getOrDefault(emptyList())
        val present = HashSet<String>()
        for (v in keys) {
            val id = keyOf(v); present += id
            val st = MediaState.parse(v.state)
            if (st != MediaState.UNKNOWN) apply(tracker.seen(id, labelOf(v), st))
        }
        tracker.gone(present).forEach { apply(it) }
        publish()
    }

    /** Une clé qui change d'état : une vérification qui commence renouvelle le réessai, un montage le donne (une fois), un départ l'annule. */
    private fun apply(ch: UsbVolumeTracker.Change?) {
        ch ?: return
        when {
            ch.to == MediaState.CHECKING -> retry.checkingStarted(ch.id)
            ch.to.mounted -> retry.mounted(ch.id).forEach { reader -> mountListeners[reader]?.let { again -> main.post { runCatching(again) } } }
            else -> retry.gone(ch.id)
        }
    }

    // ---- publication ----

    /** Copies, téléchargements et déplacements qui écrivent sur la clé [id] (l'UUID) : le serveur de la TV le sait ; les téléchargements aussi. */
    private fun writesOn(id: String): Int = runCatching { UsbRemoval.writes("usb-$id").size }.getOrDefault(0)

    private fun interruptedOn(id: String): String? = runCatching { UsbRemoval.interrupted("usb-$id") }.getOrNull()

    private fun publish() {
        // a key pulled during a copy: the writer may notice a moment AFTER the broadcast, the file whose copy was cut is learned then (the notice stays ten minutes)
        for (e in tracker.entries(::writesOn)) {
            if (e.verdict.phase == UsbPhase.REMOVED_BADLY && e.facts.interruptedCopy == null) interruptedOn(e.id)?.let { tracker.seen(e.id, e.facts.label, MediaState.BAD_REMOVAL, it) }
        }
        val list = tracker.entries(::writesOn).map { Entry(it.id, it.facts.label, it.verdict) }
        val sig = list.joinToString("|") { "${it.id}:${it.verdict.phase}:${it.verdict.line}" }
        entries = list
        if (sig != signature) {
            signature = sig
            // the line stays readable in « Connexion & réglages » (null clears it)
            val line = UsbVolumeState.pick(list.map { it.verdict })?.line
            main.post { runCatching { TvService.running?.setStatus("0-usbnote", line) }; for (l in listeners) runCatching { l() } }
        }
        scheduleTick(list.isNotEmpty())
    }

    private val tick = Runnable {
        ticking = false
        ticks++
        if (entries.isEmpty() || ticks % 5 == 0) refresh() else publish()
    }

    /**
     * Une seconde de battement tant qu'une clé est là (le compteur de vérification, « prête » qui s'efface, les copies), avec un relevé du système toutes les 5 s ; sans clé, un relevé
     * toutes les 15 s seulement (une diffusion perdue par un boîtier ne laisse pas la TV aveugle à une clé qu'Android vérifie).
     */
    private fun scheduleTick(wanted: Boolean) {
        main.post {                                                       // every access to [ticking] and [tickDelay] is on the main thread
            val delay = if (wanted) 1_000L else IDLE_REFRESH_MS
            if (ticking && tickDelay <= delay) return@post                // already waiting, as soon or sooner
            main.removeCallbacks(tick)                                    // a key appeared while the slow beat was waiting: the one-second beat takes over
            ticking = true; tickDelay = delay
            main.postDelayed(tick, delay)
        }
    }

    private var tickDelay = 0L
    private const val IDLE_REFRESH_MS = 15_000L
}
