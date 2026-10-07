package castbridge.core.tv

/** Une copie qui écrit sur la clé, telle que la TV la montre : le nom du fichier (jamais un chemin) et son avancement ([percent] -1 = inconnu). */
data class WriteInfo(val name: String, val percent: Int = -1)

/**
 * Ce dont « Préparer le retrait de la clé USB » a besoin de la TV (le serveur de réception, les téléchargements, le système). Interface pure : les tests en donnent une fausse.
 * [flush] BLOQUE (quelques secondes sur une clé lente) : le déroulement est conduit depuis un fil de fond.
 */
interface RemovalHost {
    /** Les copies qui écrivent sur la clé maintenant (réceptions, déplacements, téléchargements). */
    fun writes(volumeId: String): List<WriteInfo>
    /** Plus rien de nouveau n'est écrit sur la clé ; [stop] faux = les copies en cours finissent ; [stop] vrai = elles s'arrêtent au bloc suivant (leur reprise est gardée). Idempotent. */
    fun fence(volumeId: String, stop: Boolean)
    /** La clé sert de nouveau. */
    fun unfence(volumeId: String)
    /** Force tout ce qui a été écrit sur la clé à atteindre le support (`fsync` de chaque fichier partiel, puis `sync`) ; faux = non confirmé. */
    fun flush(volumeId: String): Boolean
    /** La clé est-elle toujours là, montée ? */
    fun present(volumeId: String): Boolean
}

/**
 * « Retrait sûr » (docs/STORAGE.md § « Clé USB mal éjectée : ce que la TV peut et ne peut pas faire »). La ligne MENU « Préparer le retrait de la clé USB » :
 *
 *  1. des copies écrivent sur la clé : la TV les dit (nom, avancement) et propose d'ATTENDRE leur fin (plus aucune nouvelle copie n'est acceptée sur la clé, les copies en cours finissent) ou de les
 *     METTRE EN PAUSE (elles s'arrêtent au bloc suivant, leur reprise est gardée sur la clé et le téléphone la reprend au retour de la clé) ;
 *  2. quand plus rien n'écrit : arrêt net de toute écriture, puis vidage (`fsync` de chaque fichier partiel + `sync`) ;
 *  3. alors seulement : « Vous pouvez retirer la clé » (et, pour une éjection complète, « Réglages › Stockage › Éjecter » avec son bouton). Jamais « prête » sans la confirmation du vidage.
 *
 * La clé retirée, ou dix minutes sans qu'elle le soit, remet la clé en service. Pur : le déroulement, les mots et leur ORDRE ; la TV n'apporte que l'[RemovalHost]. Une instance suit une clé,
 * depuis un seul fil à la fois ([start], [press], [tick] peuvent bloquer sur [RemovalHost.flush]).
 */
class UsbSafeRemoval(private val host: RemovalHost, private val now: () -> Long) {
    enum class Step { ASK, WAITING, STOPPING, FLUSHING, READY, FAILED, GONE, EXPIRED, CLOSED }

    enum class Button(val label: String) {
        WAIT("Attendre la fin de la copie"), PAUSE("Mettre en pause et préparer le retrait"), PAUSE_NOW("Mettre en pause maintenant"),
        RETRY("Réessayer"), SETTINGS(UsbVolumeState.SETTINGS_LABEL), RESUME("Reprendre l'utilisation de la clé"), CANCEL("Annuler"), CLOSE("Fermer"),
    }

    /** [busy] = la TV travaille (arrêt des copies, vidage) : aucun bouton, la personne attend. */
    data class View(val step: Step, val title: String, val lines: List<String>, val buttons: List<Button>, val busy: Boolean)

    private var id = ""
    private var label = ""
    private var step = Step.CLOSED
    private var since = 0L
    /** Des copies ont été mises en pause par cette préparation : on promet leur reprise. */
    private var paused = false
    private var writes: List<WriteInfo> = emptyList()
    private var failure: List<String> = emptyList()
    private var ending = ""

    val view: View get() = render()

    /** Ouvre la préparation de la clé [volumeId] : sans copie en cours, la clé est préparée tout de suite ; sinon la TV demande quoi faire. */
    fun start(volumeId: String, label: String): View {
        id = volumeId; this.label = label.trim(); paused = false; failure = emptyList(); ending = ""
        if (!host.present(id)) { enter(Step.GONE); return render() }
        writes = host.writes(id)
        if (writes.isEmpty()) { host.fence(id, true); flush() } else enter(Step.ASK)
        return render()
    }

    fun press(b: Button): View {
        if (b !in offered(step)) return render()
        when (b) {
            Button.WAIT -> { host.fence(id, false); enter(Step.WAITING); return tick() }
            Button.PAUSE, Button.PAUSE_NOW -> { paused = true; host.fence(id, true); enter(Step.STOPPING); return tick() }
            Button.RETRY -> { host.fence(id, true); enter(Step.STOPPING); return tick() }
            Button.RESUME -> { host.unfence(id); ending = "Les copies reprennent sur la clé."; enter(Step.CLOSED) }
            Button.CANCEL -> { host.unfence(id); ending = "Préparation annulée : la clé reste utilisable."; enter(Step.CLOSED) }
            Button.SETTINGS, Button.CLOSE -> Unit            // des gestes de l'écran : rien ne change ici
        }
        return render()
    }

    /** À appeler à peu près chaque seconde tant que la préparation n'est pas finie : suit les copies, vide la clé quand plus rien n'écrit, rend la clé après dix minutes ou quand elle est retirée. */
    fun tick(): View {
        if (step == Step.CLOSED || step == Step.GONE || step == Step.EXPIRED) return render()
        if (!host.present(id)) { host.unfence(id); enter(Step.GONE); return render() }
        when (step) {
            Step.ASK -> Unit
            Step.WAITING -> {
                writes = host.writes(id)
                if (writes.isEmpty()) { host.fence(id, true); flush() }
            }
            Step.STOPPING -> {
                writes = host.writes(id)
                if (writes.isEmpty()) flush()
                else if (now() - since >= STOP_TIMEOUT_MS) {
                    // at 100 % the copy is not running any more: the TV reads the whole file back before it takes its name (minutes on a key), and never cuts that
                    val verifying = writes.all { it.percent == 100 }
                    failure = listOf(when {
                        verifying && writes.size == 1 -> "La copie ${names(writes)} finit sa vérification : attendez-la, puis réessayez."
                        verifying -> "Les copies ${names(writes)} finissent leur vérification : attendez-les, puis réessayez."
                        else -> "Une copie ne s'arrête pas : ${names(writes)}."
                    }, NOT_YET)
                    enter(Step.FAILED)
                }
            }
            Step.READY -> if (now() - since >= READY_HOLD_MS) { host.unfence(id); ending = "La clé n'a pas été retirée : les copies reprennent."; enter(Step.EXPIRED) }
            else -> Unit
        }
        return render()
    }

    private fun flush() {
        enter(Step.FLUSHING)
        if (host.flush(id)) enter(Step.READY)
        else { failure = listOf("La TV n'a pas pu confirmer que tout est écrit sur la clé.", NOT_YET); enter(Step.FAILED) }
    }

    private fun enter(s: Step) { step = s; since = now() }

    private fun offered(s: Step): List<Button> = when (s) {
        Step.ASK -> listOf(Button.WAIT, Button.PAUSE, Button.CANCEL)
        Step.WAITING -> listOf(Button.PAUSE_NOW, Button.CANCEL)
        Step.READY -> listOf(Button.SETTINGS, Button.RESUME, Button.CLOSE)
        Step.FAILED -> listOf(Button.RETRY, Button.SETTINGS, Button.RESUME)
        Step.GONE, Step.EXPIRED, Step.CLOSED -> listOf(Button.CLOSE)
        Step.STOPPING, Step.FLUSHING -> emptyList()
    }

    private fun render(): View {
        val the = if (label.isEmpty()) "la clé USB" else "la clé « $label »"
        val title = "Préparer le retrait de $the"
        val lines: List<String> = when (step) {
            Step.ASK -> listOf(describe(writes), if (writes.size == 1) "Attendez la fin de la copie, ou mettez-la en pause : elle reprendra quand la clé sera de retour."
                else "Attendez la fin des copies, ou mettez-les en pause : elles reprendront quand la clé sera de retour.")
            Step.WAITING -> listOf(describe(writes, running = true), "Aucune nouvelle copie n'est acceptée sur la clé. Le retrait sera prêt à la fin.")
            Step.STOPPING -> listOf("Mise en pause des copies…")
            Step.FLUSHING -> listOf("Écriture des dernières données sur la clé… ne la retirez pas encore.")
            Step.READY -> listOfNotNull("Vous pouvez retirer ${if (label.isEmpty()) "la clé" else "la clé « $label »"}.",
                if (paused) "Les copies en pause reprendront quand vous remettrez la clé." else null,
                "Pour une éjection complète : Réglages › Stockage › Éjecter.")
            Step.FAILED -> failure
            Step.GONE -> listOf("La clé a été retirée.")
            Step.EXPIRED, Step.CLOSED -> listOf(ending)
        }
        return View(step, title, lines, offered(step), busy = step == Step.STOPPING || step == Step.FLUSHING)
    }

    private fun one(w: WriteInfo) = "« ${w.name} »" + if (w.percent in 0..100) " (${w.percent} %)" else ""

    private fun names(w: List<WriteInfo>): String = w.take(3).joinToString(", ") { one(it) } + if (w.size > 3) " et ${w.size - 3} autre(s)" else ""

    private fun describe(w: List<WriteInfo>, running: Boolean = false): String = when {
        w.isEmpty() -> "Plus aucune copie n'écrit sur la clé."
        w.size == 1 -> (if (running) "Copie en cours : " else "Une copie écrit sur la clé : ") + one(w[0]) + "."
        else -> (if (running) "${w.size} copies en cours : " else "${w.size} copies écrivent sur la clé : ") + names(w) + "."
    }

    companion object {
        /** Une copie qui n'a pas lâché la clé 20 s après l'ordre d'arrêt : la préparation échoue (jamais un faux « prête »). */
        const val STOP_TIMEOUT_MS = 20_000L
        /** La clé préparée mais pas retirée : au bout de 10 minutes les copies reprennent. */
        const val READY_HOLD_MS = 10 * 60_000L
        private const val NOT_YET = "Ne retirez pas la clé tout de suite : réessayez, ou utilisez Réglages › Stockage › Éjecter."
    }
}
