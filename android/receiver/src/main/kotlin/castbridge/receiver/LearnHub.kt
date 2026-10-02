package castbridge.receiver

import android.content.Context
import android.content.Intent
import castbridge.core.learn.DirectoryLessonSource
import castbridge.core.learn.EmbeddedLessonSource
import castbridge.core.learn.LearnApi
import castbridge.core.learn.LearnLotConsumer
import castbridge.core.learn.LearnLotSource
import castbridge.core.learn.LearnLibrary
import castbridge.core.learn.LearnProgress
import castbridge.core.learn.LearnStore
import castbridge.core.learn.PackFormat
import castbridge.core.learn.PackInstaller
import castbridge.core.learn.PackRef
import castbridge.core.tv.TransferRule
import castbridge.core.tv.VolumeKind
import java.io.File
import java.util.concurrent.Executors

/**
 * « Apprendre » on the TV, shared by [LearnActivity] and the HTTP server (docs/LEARN.md):
 * - the content library: embedded socle + packs in `CastBridge/Packs` of every volume (USB drive first), including the
 *   drive root `CastBridge/Packs` when the TV lets the app read it;
 * - the students' progress (one JSON file in the app's private storage);
 * - the PIN routes /api/learn/… ([LearnApi]) used by the phone (remote control, teacher mode, parent dashboard, packs).
 */
object LearnHub {
    @Volatile private var app: Context? = null
    @Volatile private var service: TvService? = null
    /** The « Apprendre » screen while it is shown (remote commands go to it). */
    @Volatile var screen: LearnActivity? = null
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-learn").apply { isDaemon = true } }

    @Volatile private var progress: LearnProgress? = null
    @Volatile private var lib: LearnLibrary? = null

    fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }
    fun attach(s: TvService) { service = s; init(s) }

    private fun storeFile(): File = File(app!!.filesDir, "learn/progress.json")

    /** Every « Apprendre » event also goes to the usage statistics (docs/TELEMETRY.md, event "learn"; never the profile). */
    @Synchronized fun progress(): LearnProgress = progress ?: LearnProgress(LearnStore.load(storeFile())).also {
        it.onEvent = { name, data -> TvConnect.learn(name, data) }
        progress = it
    }

    /** Saves in the background (a few kB; atomic rename). */
    fun save() {
        val snapshot = synchronized(this) { progress?.let { LearnStore.write(it.state) } } ?: return
        runCatching { io.execute { runCatching {
            val f = storeFile(); f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp"); tmp.writeText(snapshot); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } } }
    }

    /** Folders where packs are looked for: (label, folder, removable). Removable volumes first. */
    fun packDirs(): List<Triple<String, File, Boolean>> {
        val out = ArrayList<Triple<String, File, Boolean>>()
        val vols = service?.let { s -> runCatching { s.registry.volumes() }.getOrNull() }.orEmpty().filter { it.kind != VolumeKind.SAF }
        for (v in vols.sortedBy { if (it.kind == VolumeKind.REMOVABLE) 0 else 1 }) {
            // the volume folder is <app folder on the volume>/videos: packs go next to it, in CastBridge/Packs
            v.dir.parentFile?.let { out += Triple(v.label, File(it, "CastBridge/Packs"), v.kind == VolumeKind.REMOVABLE) }
            if (v.kind == VolumeKind.REMOVABLE) driveRoot(v.dir)?.let { out += Triple("${v.label} (racine)", File(it, "CastBridge/Packs"), true) }
        }
        if (out.none { !it.third }) app?.let { out += Triple("Mémoire interne", File(it.getExternalFilesDir(null) ?: it.filesDir, "CastBridge/Packs"), false) }
        return out.distinctBy { it.second.absolutePath }
    }

    /** /storage/XXXX-XXXX from /storage/XXXX-XXXX/Android/data/<pkg>/files/videos, if the TV lets us read it. */
    private fun driveRoot(dir: File): File? {
        val p = dir.absolutePath; val i = p.indexOf("/Android/data/")
        return if (i > 0) File(p.substring(0, i)).takeIf { runCatching { File(it, "CastBridge/Packs").canRead() }.getOrDefault(false) } else null
    }

    @Volatile private var lots: LearnLotConsumer? = null

    /**
     * The installed « Apprendre » lots (one folder per class in filesDir/lots/learn). Lots arrive from the phone whenever it can
     * talk to the TV (the lots framework installs them with this consumer): the TV never needs Internet. Read first, then the
     * packs of a drive, then the starter packs of the APK.
     */
    @Synchronized fun lots(): LearnLotConsumer = lots ?: LearnLotConsumer(File(app!!.filesDir, "lots/learn")).also { lots = it }

    /**
     * The « Apprendre » consumer for the TV's lot store: installs / removes lots in [lots] and makes the Learn library read again, so a lot that arrives (or is deleted at the end of a rental)
     * shows up (or disappears) at once. Registered by [LotsHub]; without it the store would only know the starter data and refuse every lot.
     */
    fun lotsConsumer(ctx: Context): castbridge.core.lots.LotConsumer {
        init(ctx); val d = lots()
        return object : castbridge.core.lots.LotConsumer by d {
            override fun install(meta: castbridge.core.lots.LotMeta, data: File): Boolean = d.install(meta, data).also { if (it) runCatching { library().forget() } }
            override fun remove(id: castbridge.core.lots.LotId) { d.remove(id); runCatching { library().forget() } }
        }
    }

    /** Content hash of a lesson in the installed lots (null = starter/loose pack): detects « mise à jour » after a lot update. */
    fun lessonHash(lesson: String): String? = runCatching { lots().lessonHash(lesson) }.getOrNull()

    @Synchronized fun library(): LearnLibrary = lib ?: LearnLibrary(listOf(LearnLotSource(lots()), DirectoryLessonSource(::packDirs), EmbeddedLessonSource())).also { lib = it }

    /** Where a new pack can be written, with the free space (USB drive first, then the TV). */
    fun installTargets(): List<PackInstaller.Target> = packDirs().filter { !it.second.absolutePath.contains("(racine)") }
        .filter { (_, dir, _) -> dir.absolutePath.contains("/Android/data/") || dir.absolutePath.startsWith(app?.filesDir?.absolutePath ?: "/nonexistent") }
        .map { (label, dir, _) -> PackInstaller.Target(label, dir, runCatching { (dir.parentFile ?: dir).let { d -> d.mkdirs(); d.usableSpace } }.getOrDefault(-1)) }

    private fun minFree(): Long = service?.let { runCatching { TransferRule.minFree(TvPrefs(it).profile()) }.getOrNull() } ?: (1L shl 30)

    fun install(bytes: ByteArray): PackInstaller.Result =
        PackInstaller().install(bytes, installTargets(), minFree()).also { r ->
            if (r is PackInstaller.Result.Installed) { library().forget(); progress().event("pack_installed", System.currentTimeMillis(), "", mapOf("pack" to r.manifest.id, "version" to r.manifest.version)); save() }
        }

    /** A pack file sent to the TV library (phone file exchange) or lying on a drive: installed, then removed from the library. */
    fun importFile(name: String): PackInstaller.Result {
        val f = libraryPackFiles().firstOrNull { it.name == name } ?: return PackInstaller.Result.Refused("fichier « $name » introuvable dans la bibliothèque")
        val r = install(f.readBytes())
        if (r is PackInstaller.Result.Installed) runCatching { f.delete() }
        return r
    }

    /** Pack zips found in the library folders (sent from the phone like any file). */
    fun libraryPackFiles(): List<File> = service?.let { s -> runCatching { s.registry.volumes() }.getOrNull() }.orEmpty()
        .filter { it.kind != VolumeKind.SAF }.flatMap { v -> v.dir.listFiles { f -> f.isFile && f.name.endsWith(PackFormat.SUFFIX) }?.toList().orEmpty() }

    fun remove(ref: PackRef): String? {
        if (ref.file == null) return "un pack embarqué dans l'app ne peut pas être supprimé"
        return if (PackInstaller().remove(ref)) { library().forget(); null } else "suppression impossible (clé en lecture seule ?)"
    }

    /** PIN routes for the phone (chained in the server's extensions by [TvService]). */
    fun api(ctx: Context): LearnApi { init(ctx); return LearnApi(host) }

    private val host = object : LearnApi.Host {
        override fun screenJson(): String? = screen?.stateJson()
        override fun open(profile: String?): String? {
            val s = service?.screen?.takeIf { it.shown }?.activity ?: return if (screen != null) null else "Ouvrez CastBridge TV sur la TV, puis réessayez"
            TvConnect.feature("learn", "phone")
            s.runOnUiThread { runCatching { s.startActivity(Intent(s, LearnActivity::class.java).putExtra("profile", profile).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            var waited = 0
            while (screen == null && waited < 3000) { Thread.sleep(100); waited += 100 }
            return null
        }
        override fun command(action: String, params: Map<String, String>): String? {
            val a = screen ?: return "« Apprendre » n'est pas ouvert sur la TV"
            val lock = Object(); var err: String? = null; var done = false
            a.runOnUiThread { err = runCatching { a.remote(action, params) }.getOrElse { it.message }; synchronized(lock) { done = true; lock.notifyAll() } }
            synchronized(lock) { if (!done) lock.wait(2000) }
            return err
        }
        override fun progress() = LearnHub.progress()
        override fun library() = LearnHub.library()
        override fun install(bytes: ByteArray) = LearnHub.install(bytes)
        override fun importFile(name: String) = LearnHub.importFile(name)
        override fun remove(id: String): String? = library().all().firstOrNull { it.id == id && it.file != null }?.let { remove(it) } ?: "pack $id introuvable (ou embarqué)"
    }

    /** Plays a video block: « library:<name> » from the TV library, or an URL given by an administrator. */
    fun playVideo(src: String): String? {
        val s = service ?: return "service indisponible"
        val scr = s.screen ?: return "écran indisponible"
        return if (src.startsWith("library:")) {
            val name = src.removePrefix("library:")
            val f = runCatching { s.registry.volumes().filter { it.kind != VolumeKind.SAF }.map { File(it.dir, name) }.firstOrNull { it.isFile } }.getOrNull()
                ?: return "vidéo « $name » absente de la bibliothèque (clé USB branchée ?)"
            bringPlayer(scr.activity); scr.play(f, 0); null
        } else { bringPlayer(scr.activity); scr.playStream(src, src.substringAfterLast('/'), 0); null }
    }

    private fun bringPlayer(a: android.app.Activity) =
        runCatching { a.startActivity(Intent(a, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
