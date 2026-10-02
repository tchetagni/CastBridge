package castbridge.desktop

import castbridge.core.owner.Activation
import castbridge.core.owner.Delivered
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.IssueSpec
import castbridge.core.owner.RightsSyntax
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.Right
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.IssueException
import castbridge.core.owner.Subject
import java.awt.BorderLayout
import java.awt.Dimension
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
            tabs.addTab("Émettre", issuePanel()); tabs.addTab("Clé", keyPanel()); tabs.addTab("Licences", licencePanel()); tabs.addTab("Journal", journalPanel())
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
            val license = JTextField("trial")
            val permanent = JCheckBox("SUPER_UNLIMITED : lit et débloque tout, locations permanentes (clé super administrateur seulement) ; le code reste valable 48 h pour l'installer")
            val rights = JTextArea(3, 50).apply { toolTipText = "Avancé, une ligne par droit : tout-ouvert produit:jours | droit LIGNE | location produit=bouquet:JOURS (durée EXACTE du catalogue chargé ; refusée sans catalogue)" }
            val trialDays = JTextField("${castbridge.core.owner.ActivationPolicy.TRIAL_DEFAULT_DAYS}").apply { toolTipText = "Essai : durée de la clé en jours (1 à ${castbridge.core.owner.ActivationPolicy.TRIAL_MAX_DAYS}, jamais illimitée). L'essai ouvre aussi, une seule fois, 12 h de lots locatifs." }
            val prodDays = JComboBox(KeyDuration.CHOICES.toTypedArray()).apply { toolTipText = "Production : « ${KeyDuration.UNLIMITED} » (la TV ne se reverrouille jamais), un nombre de jours usuel, ou « ${KeyDuration.OTHER} »." }
            val otherDays = JTextField("90", 6).apply { isEnabled = false; toolTipText = "Nombre de jours (1 à ${castbridge.core.owner.ActivationPolicy.PRODUCTION_MAX_DAYS})" }
            prodDays.addActionListener { otherDays.isEnabled = prodDays.selectedItem == KeyDuration.OTHER && prodDays.isEnabled }
            permanent.addActionListener { if (permanent.isSelected) prodDays.selectedItem = KeyDuration.UNLIMITED; prodDays.isEnabled = !permanent.isSelected; otherDays.isEnabled = false }
            val prodRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(prodDays); add(JLabel("  ")); add(otherDays) }
            var catalog: BundleCatalog? = null
            val checklist = Checklist()
            val catLabel = JLabel("Aucun catalogue chargé : les achats, abonnements et locations par bouquet sont indisponibles.")
            val loadCat = JButton("Catalogue…").apply {
                addActionListener {
                    val f = chooseFile(false) ?: return@addActionListener
                    try { catalog = BundleCatalog.parse(f.readText()).also { checklist.fill(it) }; catLabel.text = "Catalogue : ${f.name} (${catalog!!.bundles.size} bouquets)" }
                    catch (e: Exception) { catalog = null; checklist.fill(null); catLabel.text = "Catalogue illisible."; error("Catalogue illisible (TRIAL-MANIFEST.json ou fichier contenant le tableau « bundles ») : ${e.message}") }
                }
            }
            val catRow = JPanel(BorderLayout(8, 0)).apply { add(loadCat, BorderLayout.WEST); add(catLabel, BorderLayout.CENTER) }
            val catScroll = JScrollPane(checklist.panel).apply { preferredSize = Dimension(600, 190) }
            val pass = JPasswordField()
            val load = JButton("Ouvrir une demande…").apply { addActionListener { chooseFile(false)?.let { request.text = it.readText() } } }
            kind.addActionListener { license.isEnabled = kind.selectedIndex == 1; if (kind.selectedIndex == 0) license.text = "trial" else if (license.text == "trial") license.text = "" }
            license.isEnabled = false
            val go = JButton("Générer l'activation").apply {
                addActionListener {
                    val p = pass.password
                    try {
                        val device = DeviceRequest.parse(request.text)
                        val d = unlocked(p) ?: return@addActionListener
                        val now = System.currentTimeMillis()
                        val k = if (kind.selectedIndex == 0) ActivationKind.TRIAL else ActivationKind.PRODUCTION
                        val usage: Int? = if (k == ActivationKind.TRIAL) KeyDuration.trial(trialDays.text) else KeyDuration.production(prodDays.selectedItem as String, otherDays.text, permanent.isSelected)
                        val plan = if (k == ActivationKind.TRIAL) RightsPlan(emptyList(), emptyList()).also { if (rights.text.isNotBlank() || checklist.any()) throw IssueException("Un essai ne porte aucun droit : choisissez « Production » pour des achats, abonnements ou locations") }
                            else checklist.let { c -> GuiRights.plan(c.purchases(), c.subscriptions(), c.rentals(), rights.text, catalog, now) }
                        val rentalSpecs = plan.rentals
                        val spec = IssueSpec(k, if (subject.selectedIndex == 0) Subject.TV else Subject.PHONE, plan.rights + (if (permanent.isSelected && k == ActivationKind.PRODUCTION) listOf(Right.Super("super-illimite", now)) else emptyList()), license.text.trim().ifEmpty { Activation.TRIAL_LICENSE }, rentals = rentalSpecs, rentalMaster = if (rentalSpecs.isEmpty() && k != ActivationKind.TRIAL) null else d.rentalMaster(), usageDays = usage, trialLots = k == ActivationKind.TRIAL)
                        val r = d.issue(device, spec)
                        last = r; token.text = r.issued.token
                        qr.icon = ImageIcon(Qr.image(r.issued.token, 4))
                        info("Activation émise pour ${device.code} — poste ${r.seat}${if (r.reused) " (ré-activation, aucun poste consommé)" else ""} — installable jusqu'au ${fmt(r.issued.activation.notAfter)}")
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
            val form = JPanel(); gb(form, listOf("Demande d'appareil (collée depuis la TV)" to JScrollPane(request), "" to load, "Type" to kind, "Pour" to subject, "Licence" to license,
                "Privilège" to permanent, "Essai : durée de la clé (jours)" to trialDays, "Production : durée de la clé" to prodRow, "Catalogue du serveur" to catRow, "Achats / abonnements / locations" to catScroll, "Avancé : autres droits (une ligne par droit)" to JScrollPane(rights), "Code de déverrouillage" to pass, "" to go))
            val out = JPanel(BorderLayout()).apply {
                add(JScrollPane(token), BorderLayout.NORTH); add(qr, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(copy); add(save); add(savePng) }, BorderLayout.SOUTH)
            }
            return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH); add(out, BorderLayout.CENTER) }
        }

        /** Checklist of the catalogue's bundles: purchase, subscription (end date) and rental (duration fixed by the server, read-only: the catalogue's rentalDays, else the default of 30 days; disabled only when out of bounds). */
        class Checklist {
            val panel = JPanel(GridBagLayout())
            private class Row(val id: String, val buy: JCheckBox, val sub: JCheckBox, val end: JTextField, val rent: JCheckBox)
            private var rows = emptyList<Row>()
            init { fill(null) }
            fun fill(c: BundleCatalog?) {
                panel.removeAll()
                fun cell(comp: java.awt.Component, x: Int, y: Int, w: Double = 0.0) = panel.add(comp, GridBagConstraints().apply { gridx = x; gridy = y; weightx = w; anchor = GridBagConstraints.WEST; insets = Insets(1, 4, 1, 4) })
                listOf("Bouquet", "Achat", "Abonnement jusqu'au (AAAA-MM-JJ)", "", "Location").forEachIndexed { i, t -> cell(JLabel(t), i, 0, if (i == 0) 1.0 else 0.0) }
                val fresh = ArrayList<Row>()
                c?.bundles?.sortedBy { it.id }?.forEachIndexed { n, b ->
                    val y = n + 1
                    val days = RentalDurations.daysOf(c, b.id)
                    val buy = JCheckBox(); val sub = JCheckBox(); val end = JTextField(10).apply { isEnabled = false }
                    sub.addActionListener { end.isEnabled = sub.isSelected }
                    val rent = JCheckBox(days.getOrNull()?.let { "fixé par le serveur : $it jours" + (if (b.rentalDays <= 0) " (défaut)" else "") } ?: "durée hors bornes : non louable").apply { isEnabled = days.isSuccess }
                    cell(JLabel(b.title.ifBlank { b.id } + "  [${b.id}]"), 0, y, 1.0); cell(buy, 1, y); cell(sub, 2, y); cell(end, 3, y); cell(rent, 4, y)
                    fresh += Row(b.id, buy, sub, end, rent)
                }
                rows = fresh; panel.revalidate(); panel.repaint()
            }
            fun purchases() = rows.filter { it.buy.isSelected }.map { it.id }
            fun subscriptions() = rows.filter { it.sub.isSelected }.associate { it.id to it.end.text }
            fun rentals() = rows.filter { it.rent.isSelected }.map { it.id }
            fun any() = rows.any { it.buy.isSelected || it.sub.isSelected || it.rent.isSelected }
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
