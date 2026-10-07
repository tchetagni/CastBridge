package castbridge.receiver

import android.os.Handler
import android.os.Looper
import castbridge.core.tv.RemovalHost
import castbridge.core.tv.ShellSync
import castbridge.core.tv.UsbSafeRemoval
import castbridge.core.tv.WriteInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * « Préparer le retrait de la clé USB » côté TV (docs/STORAGE.md § « Clé USB mal éjectée : ce que la TV peut et ne peut pas faire », « Retrait sûr »). Une application ne peut ni démonter ni éjecter
 * un volume (`MOUNT_UNMOUNT_FILESYSTEMS` est une permission système) : ce que la TV peut faire, c'est que PLUS RIEN n'écrive sur la clé, que ce qui a été écrit soit sur le support (`fsync` de chaque
 * fichier partiel, puis `sync`), et le dire. Toutes les décisions (les étapes, les mots, leur ordre) sont dans le cœur pur [UsbSafeRemoval], testé ; ici seulement le fil, la minuterie et le pont
 * vers le serveur de réception ([castbridge.core.tv.ReceiverServer.fenceVolume] / `flushVolume`) et les téléchargements.
 *
 * L'état vit ICI (pas dans l'écran) : la clé préparée reste « prête à retirer » quand l'écran est fermé, et la minuterie de dix minutes ou le retrait de la clé la remettent en service.
 */
object UsbRemoval {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-usb-removal").apply { isDaemon = true } }
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var flow: UsbSafeRemoval? = null
    @Volatile private var keyId: String? = null
    /** La préparation en cours ou la dernière (null = jamais ouverte, ou fermée). */
    @Volatile var view: UsbSafeRemoval.View? = null; private set
    private var ticking = false

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    // ---- ce qui écrit sur la clé ----

    /** Les copies qui écrivent sur la clé [volumeId] maintenant ; [withDownloads] : aussi les téléchargements (appel d'un fil de fond seulement : il interroge aria2). */
    fun writes(volumeId: String, withDownloads: Boolean = false): List<WriteInfo> {
        val svc = TvService.running ?: return emptyList()
        val out = ArrayList<WriteInfo>(svc.server?.writesOn(volumeId).orEmpty())
        if (withDownloads) runCatching { TvDownloads.get()?.manager?.writingOn(volumeId)?.forEach { (name, pct) -> out += WriteInfo(name, pct) } }
        return out
    }

    /** Le fichier dont la copie vient d'être coupée par le retrait de la clé (son nom seulement), ou null. */
    fun interrupted(volumeId: String): String? = TvService.running?.server?.interruptedOn(volumeId)

    // ---- le pont vers la TV ----

    private object Host : RemovalHost {
        override fun writes(volumeId: String) = writes(volumeId, withDownloads = true)
        override fun fence(volumeId: String, stop: Boolean) {
            TvService.running?.server?.fenceVolume(volumeId, stop)
            if (stop) runCatching { TvDownloads.get()?.manager?.hold(volumeId, true) }          // les téléchargements écrivent aussi : en pause, reprise automatique à la levée
        }
        override fun unfence(volumeId: String) {
            TvService.running?.server?.unfenceVolume(volumeId)
            runCatching { TvDownloads.get()?.manager?.hold(volumeId, false) }
        }
        override fun flush(volumeId: String): Boolean = TvService.running?.server?.flushVolume(volumeId) ?: ShellSync.shared.now()
        override fun present(volumeId: String): Boolean = UsbVolumeWatch.entries().any { it.volumeId == volumeId && it.verdict.readable }
    }

    // ---- le déroulement ----

    /** Ouvre la préparation de la clé [id] (l'UUID) : sans copie en cours la clé est préparée tout de suite, sinon la TV demande quoi faire. */
    fun open(id: String, label: String) {
        val volumeId = "usb-$id"
        worker.execute {
            val f = UsbSafeRemoval(Host) { System.currentTimeMillis() }
            flow = f; keyId = id
            publish(f.start(volumeId, label), volumeId)
        }
    }

    fun press(b: UsbSafeRemoval.Button) {
        worker.execute { val f = flow ?: return@execute; publish(f.press(b), "usb-${keyId.orEmpty()}") }
    }

    /** L'écran ferme la préparation terminée (retirée, annulée, expirée) : plus rien à montrer. */
    fun forget() {
        val v = view ?: return
        if (v.step !in FINISHED) return                               // a key still prepared (« Vous pouvez retirer la clé ») stays prepared
        view = null
        worker.execute { flow = null }
        notifyListeners()
    }

    private val FINISHED = setOf(UsbSafeRemoval.Step.GONE, UsbSafeRemoval.Step.EXPIRED, UsbSafeRemoval.Step.CLOSED)

    private fun publish(v: UsbSafeRemoval.View, volumeId: String) {
        view = v
        // « Vous pouvez retirer la clé » reste dit sur l'accueil tant que la clé est préparée (et seulement alors)
        keyId?.let { UsbVolumeWatch.setPullReady(it, v.step == UsbSafeRemoval.Step.READY) }
        notifyListeners()
        scheduleTick(v.step !in FINISHED)
    }

    private fun notifyListeners() { main.post { for (l in listeners) runCatching { l() } } }

    private val tick = Runnable {
        ticking = false
        worker.execute { val f = flow ?: return@execute; publish(f.tick(), "usb-${keyId.orEmpty()}") }
    }

    /** Une seconde de battement tant que la préparation n'est pas finie (suivre les copies, retirer la clé, dix minutes). */
    private fun scheduleTick(wanted: Boolean) {
        if (!wanted) return
        main.post { if (!ticking) { ticking = true; main.postDelayed(tick, 1_000) } }          // every access to [ticking] is on the main thread
    }
}
