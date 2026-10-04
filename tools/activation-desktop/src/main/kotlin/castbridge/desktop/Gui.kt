package castbridge.desktop

import castbridge.core.owner.Activation
import castbridge.core.owner.Delivered
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.IssueSpec
import castbridge.core.lots.Right
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.IssueException
import castbridge.core.owner.Subject
import java.awt.BorderLayout
import java.awt.Dimension
import castbridge.core.owner.KeyScope
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.table.DefaultTableModel

/**
 * Simple Swing window over the same [Desk] engine as the command line: paste the TV's device request, choose the rights and the duration, type the unlock code,
 * get the token to copy, the `activation` file for the TV's USB drive and a QR code. The code is read from a password field, used once and wiped; nothing secret is shown or logged.
 */
object Gui {
    fun launch(home: File?) {
        System.setProperty("apple.awt.application.name", "CastBridge Activations")
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        SwingUtilities.invokeLater { Window(home ?: File(System.getProperty("user.home"), ".castbridge-activation")).show() }
    }

    private class Window(val home: File) {
        val frame = JFrame("CastBridge — Activations (outil du propriétaire)")
        val kf = KeyFile(File(home, "desk.key.json"))
        val status = JLabel(" ")
        val token = JTextArea(6, 60).apply { lineWrap = true; isEditable = false; font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11) }
        val qr = JLabel().apply { horizontalAlignment = JLabel.CENTER }
        var last: Delivered? = null
        val journalModel = DefaultTableModel(arrayOf("Date", "Type", "Appareil", "Licence", "Poste", "Droits"), 0)

        fun fmt(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms)) + " UTC"
        fun info(msg: String) { status.text = msg }
        fun error(msg: String) { JOptionPane.showMessageDialog(frame, msg, "Refusé", JOptionPane.ERROR_MESSAGE); status.text = "Refusé : $msg" }

        fun show() {
            frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            val tabs = JTabbedPane()
            tabs.addTab("Émettre", issuePanel()); tabs.addTab("Clé", keyPanel()); tabs.addTab("Licences", licencePanel()); tabs.addTab("Experts", expertsPanel()); tabs.addTab("Journal", journalPanel())
            frame.contentPane.add(tabs, BorderLayout.CENTER); frame.contentPane.add(status.also { it.border = BorderFactory.createEmptyBorder(4, 8, 4, 8) }, BorderLayout.SOUTH)
            frame.size = Dimension(900, 760); frame.setLocationRelativeTo(null); frame.isVisible = true
            if (!kf.exists()) info("Aucune clé : onglet « Clé » pour la créer.")
        }

        fun unlocked(pass: CharArray): Desk? {
            if (!kf.exists()) { error("Aucune clé : créez-la dans l'onglet « Clé »"); return null }
            val s = kf.unlock(pass) ?: run { error("Code de déverrouillage faux"); return null }
            return Desk(home, s.signer, s.scopes, kf.trusted())
        }

        private fun gb(panel: JPanel, rows: List<Pair<String, java.awt.Component>>) {
            panel.layout = GridBagLayout()
            rows.forEachIndexed { i, (label, c) ->
                panel.add(JLabel(label), GridBagConstraints().apply { gridx = 0; gridy = i; anchor = GridBagConstraints.NORTHWEST; insets = Insets(4, 6, 4, 6) })
                panel.add(c, GridBagConstraints().apply { gridx = 1; gridy = i; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = Insets(4, 6, 4, 6) })
            }
        }

        fun issuePanel(): JPanel {
            val request = JTextArea(7, 50).apply { font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11) }
            val kind = JComboBox(arrayOf("Essai (aucun droit)", "Production (licence et droits)"))
            val subject = JComboBox(arrayOf("TV", "Téléphone"))
            val license = JTextField("").apply { toolTipText = "Production : laisser vide, une licence « lic-… » est générée (1 poste). Saisir un identifiant existant pour ré-activer ou ajouter un poste." }
            val licenseRow = JPanel(BorderLayout(6, 0)).apply { add(license, BorderLayout.CENTER); add(JLabel("(laisser vide : générée)"), BorderLayout.EAST) }
            val permanent = JCheckBox("SUPER_UNLIMITED : lit et débloque tout, locations permanentes (clé super administrateur seulement) ; le code reste valable 48 h pour l'installer")
            val trialDays = JTextField("${castbridge.core.owner.ActivationPolicy.TRIAL_DEFAULT_DAYS}").apply { toolTipText = "Essai : durée de la clé en jours (1 à ${castbridge.core.owner.ActivationPolicy.TRIAL_MAX_DAYS}, jamais illimitée). L'essai ouvre aussi, une seule fois, 12 h de lots locatifs." }
            val prodDays = JComboBox(KeyDuration.CHOICES.toTypedArray()).apply { toolTipText = "Production : « ${KeyDuration.UNLIMITED} » (la TV ne se reverrouille jamais), un nombre de jours usuel, ou « ${KeyDuration.OTHER} »." }
            val otherDays = JTextField("90", 6).apply { isEnabled = false; toolTipText = "Nombre de jours (1 à ${castbridge.core.owner.ActivationPolicy.PRODUCTION_MAX_DAYS})" }
            prodDays.addActionListener { otherDays.isEnabled = prodDays.selectedItem == KeyDuration.OTHER && prodDays.isEnabled }
            permanent.addActionListener { if (permanent.isSelected) prodDays.selectedItem = KeyDuration.UNLIMITED; prodDays.isEnabled = !permanent.isSelected; otherDays.isEnabled = false }
            val prodRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(prodDays); add(JLabel("  ")); add(otherDays) }
            val boxV1 = JCheckBox("Enveloppe v1 (TV ancienne) : seulement si la demande n'a pas de ligne « install= » ; refusée après le 1er janvier 2027")
            val requestState = JLabel(" ")
            fun syncRequest() {
                requestState.text = try { if (request.text.isBlank()) " " else DeviceRequest.parse(request.text).let { if (it.installPub != null) "Demande lue : clé d'installation présente (enveloppe v2)" else "Demande lue : clé d'installation absente (CastBridge-TV ancienne : activez « Enveloppe v1 » pour émettre l'essai)" }.plus(DeviceRequest.parse(request.text).installFingerprint?.let { " — empreinte de la clé de signature $it : comparez-la avec l'écran de la TV" } ?: "") }
                catch (e: IssueException) { e.message ?: "Demande illisible" }
            }
            request.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = syncRequest()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = syncRequest()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = syncRequest()
            })
            val pass = JPasswordField()
            val load = JButton("Ouvrir une demande…").apply { addActionListener { chooseFile(false)?.let { request.text = it.readText() } } }
            fun syncKind() { val prod = kind.selectedIndex == 1; license.isEnabled = prod; if (!prod) license.text = ""; prodDays.isEnabled = prod && !permanent.isSelected; otherDays.isEnabled = prod && prodDays.selectedItem == KeyDuration.OTHER && prodDays.isEnabled; trialDays.isEnabled = !prod; permanent.isEnabled = prod }
            kind.addActionListener { syncKind() }
            syncKind()
            val go = JButton("Générer l'activation").apply {
                addActionListener {
                    val p = pass.password
                    try {
                        val device = DeviceRequest.parse(request.text)
                        val d = unlocked(p) ?: return@addActionListener
                        val now = System.currentTimeMillis()
                        val k = if (kind.selectedIndex == 0) ActivationKind.TRIAL else ActivationKind.PRODUCTION
                        val usage: Int? = if (k == ActivationKind.TRIAL) KeyDuration.trial(trialDays.text) else KeyDuration.production(prodDays.selectedItem as String, otherDays.text, permanent.isSelected)
                        val spec = IssueSpec(k, if (subject.selectedIndex == 0) Subject.TV else Subject.PHONE,
                            if (permanent.isSelected && k == ActivationKind.PRODUCTION) listOf(Right.Super("super-illimite", now)) else emptyList(),
                            if (k == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else license.text.trim(),      // blank = generated by the desk (LicensedIssuer)
                            rentalMaster = if (k == ActivationKind.TRIAL) d.rentalMaster() else null, usageDays = usage, trialLots = k == ActivationKind.TRIAL,
                            boxV1 = boxV1.isSelected)
                        val r = d.issue(device, spec)
                        last = r; token.text = r.issued.token
                        qr.icon = ImageIcon(Qr.image(r.issued.token, 4))
                        info("Activation émise pour ${device.code} — licence ${r.issued.activation.license}${if (k == ActivationKind.PRODUCTION && license.text.isBlank()) " (générée)" else ""} — poste ${r.seat}${if (r.reused) " (ré-activation, aucun poste consommé)" else ""} — installable jusqu'au ${fmt(r.issued.activation.notAfter)}${r.installKeyFingerprint?.let { " — clé d'installation liée, empreinte $it (à comparer avec l'écran de la TV)" } ?: ""}")
                        refreshJournal(d)
                    } catch (e: IssueException) { error(e.message ?: "Refusé") }
                    catch (e: IllegalArgumentException) { error(e.message ?: "Refusé") }
                    finally { p.fill('\u0000'); pass.text = "" }
                }
            }
            val copy = JButton("Copier le jeton").apply { addActionListener { last?.let { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(it.issued.token), null); info("Jeton copié") } } }
            val save = JButton("Enregistrer le fichier « activation »…").apply {
                addActionListener { last?.let { r -> chooseFile(true, r.issued.fileName)?.let { f -> f.writeText(r.issued.fileContent); info("Fichier enregistré : ${f.path} — à copier dans Download/CastBridge/ de la clé USB de la TV") } } }
            }
            val savePng = JButton("Enregistrer le code QR…").apply { addActionListener { last?.let { r -> chooseFile(true, "activation.png")?.let { f -> Qr.png(r.issued.token, f); info("Code QR enregistré : ${f.path}") } } } }
            val form = JPanel(); gb(form, listOf("Demande d'appareil (collée depuis la TV)" to JScrollPane(request), "" to load, "État de la demande" to requestState, "Type" to kind, "Pour" to subject, "Licence (laisser vide : générée)" to licenseRow,
                "Privilège" to permanent, "Avancé" to boxV1, "Essai : durée de la clé (jours)" to trialDays, "Production : durée de la clé" to prodRow, "Code de déverrouillage" to pass, "" to go))
            val out = JPanel(BorderLayout()).apply {
                add(JScrollPane(token), BorderLayout.NORTH); add(qr, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(copy); add(save); add(savePng) }, BorderLayout.SOUTH)
            }
            return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH); add(out, BorderLayout.CENTER) }
        }

        fun chooseFile(save: Boolean, name: String? = null): File? {
            val c = JFileChooser(); name?.let { c.selectedFile = File(it) }
            val r = if (save) c.showSaveDialog(frame) else c.showOpenDialog(frame)
            return if (r == JFileChooser.APPROVE_OPTION) c.selectedFile else null
        }

        fun keyPanel(): JPanel {
            val info = JTextArea(8, 60).apply { isEditable = false; font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 11) }
            fun refresh() { info.text = if (kf.exists()) kf.info().let { "kid : ${it.kid}\nClé publique : ${it.publicKey}\nPortées : ${it.scopes.map { s -> s.name }.sorted().joinToString(", ")}\n\nCes trois lignes sont publiques : la TV les place dans son anneau de clés." } else "Aucune clé." }
            val pass = JPasswordField(); val again = JPasswordField()
            val create = JButton("Créer la clé du bureau").apply {
                addActionListener {
                    val a = pass.password; val b = again.password
                    try {
                        if (!a.contentEquals(b)) { error("Les deux saisies du code diffèrent"); return@addActionListener }
                        kf.create(a); info("Clé créée. Sauvegardez ${kf.file.path} hors ligne, et le code à part."); refresh()
                    } catch (e: IllegalArgumentException) { error(e.message ?: "Refusé") } finally { a.fill('\u0000'); b.fill('\u0000'); pass.text = ""; again.text = "" }
                }
            }
            val p = JPanel(); gb(p, listOf("Code de déverrouillage (≥ 10 caractères)" to pass, "Le même, encore" to again, "" to create, "Clé actuelle" to JScrollPane(info)))
            refresh(); return p
        }

        fun licencePanel(): JPanel {
            val id = JTextField("lic-"); val seats = JSpinner(SpinnerNumberModel(1, 1, 1000, 1)); val pass = JPasswordField()
            val create = JButton("Créer la licence").apply {
                addActionListener {
                    val p = pass.password
                    try { unlocked(p)?.let { it.createLicense(id.text.trim(), seats.value as Int); info("Licence ${id.text.trim()} créée (${seats.value} poste(s))") } }
                    catch (e: IssueException) { error(e.message ?: "Refusé") } finally { p.fill('\u0000'); pass.text = "" }
                }
            }
            val export = JButton("Exporter le registre…").apply { addActionListener { if (kf.exists()) chooseFile(true, "registre.json")?.let { f -> f.writeText(Desk(home, castbridge.core.owner.Ed25519Signer(ByteArray(32)), emptySet(), kf.trusted()).exportRegistry()); info("Registre exporté : ${f.path}") } } }
            val import = JButton("Importer un registre…").apply { addActionListener { if (kf.exists()) chooseFile(false)?.let { f -> val n = Desk(home, castbridge.core.owner.Ed25519Signer(ByteArray(32)), emptySet(), kf.trusted()).importRegistry(f.readText()); info("$n événement(s) nouveau(x) fusionné(s)") } } }
            val p = JPanel(); gb(p, listOf("Identifiant de la licence" to id, "Postes" to seats, "Code de déverrouillage" to pass, "" to create, "Registre (synchronisation)" to JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(export); add(import) }))
            return p
        }

        /** « Experts » : the remote-assistance experts (local list, public keys); « Signer et enregistrer… » writes the signed experts.json (key with REGISTRY scope). */
        fun expertsPanel(): JPanel {
            val store = ExpertsStore(home)
            val model = DefaultTableModel(arrayOf("Identifiant", "Clé SSH (fin)", "Fin de validité"), 0)
            fun refresh() { model.rowCount = 0; store.list().forEach { model.addRow(arrayOf(it.id, "…" + it.keyBase64.takeLast(12), if (it.notAfter == 0L) "sans date" else fmt(it.notAfter - 1))) } }
            val table = JTable(model)
            val id = JTextField(); val key = JTextField(); val until = JTextField(); val pass = JPasswordField()
            fun kid() = if (kf.exists()) kf.info().kid else "-"
            val add = JButton("Ajouter").apply { addActionListener {
                try { store.add(id.text.trim(), key.text, until.text.trim().ifEmpty { null }, kid()); id.text = ""; key.text = ""; until.text = ""; refresh(); info("Expert ajouté : à signer et publier pour qu'il soit actif") }
                catch (e: IllegalArgumentException) { error(e.message ?: "Refusé") } } }
            val remove = JButton("Retirer la ligne choisie").apply { addActionListener {
                val r = table.selectedRow; if (r < 0) return@addActionListener
                try { store.remove(model.getValueAt(r, 0) as String, kid()); refresh(); info("Expert retiré : signez et publiez la nouvelle liste pour fermer son accès") } catch (e: IllegalArgumentException) { error(e.message ?: "Refusé") } } }
            val sign = JButton("Signer et enregistrer…").apply { addActionListener {
                val p = pass.password
                try {
                    if (!kf.exists()) { error("Aucune clé : créez-la dans l'onglet « Clé »"); return@addActionListener }
                    if (KeyScope.REGISTRY !in kf.info().scopes) { error("La clé du bureau n'a pas la portée REGISTRY : elle ne peut pas signer la liste des experts"); return@addActionListener }
                    val f = chooseFile(true, "experts.json") ?: return@addActionListener
                    val s = kf.unlock(p) ?: run { error("Code de déverrouillage faux"); return@addActionListener }
                    val l = store.list(); val signed = castbridge.core.tunnel.ExpertsList.sign(s.signer, s.signer.keyId, l, System.currentTimeMillis())
                    castbridge.core.tunnel.ExpertsList.verify(signed.toJson(), listOf(kf.trusted()))
                    f.writeText(signed.toJson() + "\n"); store.logSigned(s.signer.keyId, l.size); info("Liste signée : ${f.path} (${l.size} expert(s)) ; à publier sur le serveur (docs/REMOTE-TUNNEL.md)")
                } catch (e: IllegalArgumentException) { error(e.message ?: "Refusé") } finally { p.fill('\u0000'); pass.text = "" } } }
            val form = JPanel(); gb(form, listOf("Identifiant (a-z, 0-9, -)" to id, "Clé SSH (ssh-ed25519 AAAA… commentaire)" to key, "Jusqu'au (AAAA-MM-JJ, facultatif)" to until, "" to JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(add); add(remove) },
                "Code de déverrouillage" to pass, "" to sign))
            val p = JPanel(BorderLayout()); p.add(JScrollPane(table), BorderLayout.CENTER); p.add(form, BorderLayout.SOUTH)
            refresh(); return p
        }

        fun refreshJournal(d: Desk) {
            journalModel.rowCount = 0
            for (r in d.journal()) journalModel.addRow(arrayOf(fmt((r["at"] as Number).toLong()), "${r["kind"]} / ${r["subject"]}", r["device"], r["license"], r["seat"], (r["rights"] as List<*>).size))
        }

        fun journalPanel(): JPanel {
            val p = JPanel(BorderLayout()); p.add(JScrollPane(JTable(journalModel)), BorderLayout.CENTER)
            p.add(JButton("Actualiser").apply { addActionListener { if (kf.exists()) refreshJournal(Desk(home, castbridge.core.owner.Ed25519Signer(ByteArray(32)), emptySet(), kf.trusted())) } }, BorderLayout.SOUTH)
            if (kf.exists()) refreshJournal(Desk(home, castbridge.core.owner.Ed25519Signer(ByteArray(32)), emptySet(), kf.trusted()))
            return p
        }
    }

    @Suppress("unused") private val keep = DeviceCode
}
