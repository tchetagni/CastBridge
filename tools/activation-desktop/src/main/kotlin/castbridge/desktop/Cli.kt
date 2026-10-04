package castbridge.desktop

import castbridge.core.lots.LotId
import castbridge.core.lots.Right
import castbridge.core.net.JsonLite
import castbridge.core.owner.Activation
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.IssueSpec
import castbridge.core.owner.RightsSyntax
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.ActivationVerifier
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.IssueException
import castbridge.core.owner.KeyScope
import castbridge.core.owner.Power
import castbridge.core.owner.Subject
import castbridge.core.owner.TrustedKey
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/** Environment of one CLI run: injectable for the tests (streams, home folder, clock, passphrase source). */
class Env(
    val out: PrintStream = PrintStream(System.out, true, "UTF-8"), val err: PrintStream = PrintStream(System.err, true, "UTF-8"), val stdin: InputStream = System.`in`,
    val getenv: (String) -> String? = System::getenv, val console: () -> CharArray? = { System.console()?.readPassword("Code de déverrouillage : ") },
    val clock: () -> Long = System::currentTimeMillis, val kdf: castbridge.core.owner.Kdf = ScryptKdf(),
    val httpGet: ((String) -> castbridge.core.net.HttpLite.Response)? = null,
)

class UsageException(message: String) : Exception(message)

/** Command-line front end. Exit codes: 0 ok, 1 refused by a rule (message in French), 2 usage error, 3 wrong unlock code. */
class Cli(private val env: Env) {
    private class Args(list: List<String>) {
        val positional = ArrayList<String>(); val opts = LinkedHashMap<String, MutableList<String>>(); val flags = HashSet<String>()
        init {
            var i = 0
            while (i < list.size) {
                val a = list[i]
                if (a.startsWith("--")) {
                    val name = a.removePrefix("--"); val eq = name.indexOf('=')
                    if (eq > 0) opts.getOrPut(name.substring(0, eq)) { ArrayList() } += name.substring(eq + 1)
                    else if (name in FLAGS) flags += name
                    else { if (i + 1 >= list.size) throw UsageException("L'option --$name attend une valeur"); opts.getOrPut(name) { ArrayList() } += list[++i] }
                } else positional += a
                i++
            }
        }
        fun get(k: String) = opts[k]?.last()
        fun all(k: String) = opts[k] ?: emptyList()
        fun need(k: String) = get(k) ?: throw UsageException("Option obligatoire : --$k")
        companion object { val FLAGS = setOf("qr", "production", "essai", "sans-confirmation", "json", "super", "sans-lots-essai", "sans-controle-catalogue", "enveloppe-v1") }
    }

    private fun home(a: Args) = File(a.get("dossier") ?: env.getenv("CASTBRIDGE_ACTIVATION_HOME") ?: (System.getProperty("user.home") + "/.castbridge-activation"))

    private fun readSource(path: String): String = if (path == "-") env.stdin.readBytes().toString(Charsets.UTF_8) else File(path).readText()

    private fun passphrase(a: Args): CharArray {
        a.get("code-env")?.let { n -> return (env.getenv(n) ?: throw UsageException("Variable d'environnement $n absente")).toCharArray() }
        a.get("code-fichier")?.let { return File(it).readText().trimEnd('\n', '\r').toCharArray() }
        return env.console() ?: throw UsageException("Pas de terminal pour saisir le code : utilisez --code-env NOM ou --code-fichier CHEMIN (jamais le code en argument)")
    }

    private fun date(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms)) + " UTC"

    fun run(argv: List<String>): Int {
        if (argv.isEmpty() || argv[0] in setOf("aide", "help", "--help", "-h")) { env.out.println(HELP); return 0 }
        return try { dispatch(argv[0], Args(argv.drop(1))) }
        catch (e: UsageException) { env.err.println("Erreur : ${e.message}\n(« aide » liste les commandes)"); 2 }
        catch (e: WrongCode) { 3 }
        catch (e: IssueException) { env.err.println("Refusé : ${e.message}"); 1 }
        catch (e: castbridge.core.lots.SignedBundleCatalog.Refused) { env.err.println("Refusé : ${e.message}"); 1 }
        catch (e: IllegalArgumentException) { env.err.println("Refusé : ${e.message}"); if (env.getenv("CASTBRIDGE_DEBUG") != null) e.printStackTrace(env.err); 1 }
    }

    private fun dispatch(cmd: String, a: Args): Int = when (cmd) {
        "cle-creer", "keygen" -> keygen(a)
        "cle", "key" -> keyInfo(a)
        "faire-confiance", "trust" -> trust(a)
        "appareil", "device" -> device(a)
        "licence", "license" -> license(a)
        "emettre", "issue" -> issue(a)
        "cle-saisissable", "compact" -> compact(a)
        "lot-chiffrer", "seal-lot" -> sealLot(a)
        "commande", "command" -> command(a)
        "verifier", "verify" -> verify(a)
        "journal" -> journal(a)
        "registre", "registry" -> registry(a)
        "autotest", "selftest" -> selftest(a)
        "catalogue-serveur", "server-catalog" -> serverCatalog(a)
        "experts-ajouter" -> expertsAdd(a)
        "experts-retirer" -> expertsRemove(a)
        "experts-lister" -> expertsList(a)
        "experts-signer" -> expertsSign(a)
        "experts-verifier" -> expertsVerify(a)
        "gui", "interface" -> { Gui.launch(home(a)); GUI_RUNNING }
        else -> throw UsageException("Commande inconnue : $cmd")
    }

    /** The catalogue text behind `--catalogue`: a file, or `serveur` = the kept copy of « catalogue-serveur » (verified again, never trusted blindly). */
    private fun readCatalogue(path: String, a: Args): String = if (path == ServerCatalogStore.KEYWORD)
        ServerCatalogStore.readVerified(home(a), ServerCatalogStore.keys(a.all("cle-publique"))).json else readSource(path)

    /** Imports the signed bundle catalogue from the server (HTTPS), verifies the signature, keeps it in the folder. Only on request. */
    private fun serverCatalog(a: Args): Int {
        val v = ServerCatalogStore.update(home(a), a.get("serveur"), ServerCatalogStore.keys(a.all("cle-publique")), env.httpGet ?: { u -> castbridge.core.net.HttpLite(userAgent = "CastBridge-desktop").request("GET", u) })
        env.out.println("Catalogue du ${v.dateFr} : ${v.catalog.bundles.size} bouquets (signé) : ${ServerCatalogStore.file(home(a)).path}")
        env.out.println("Pour l'utiliser : emettre … --catalogue ${ServerCatalogStore.KEYWORD} --lots-libres FICHIER")
        return 0
    }

    private fun experts(a: Args) = ExpertsStore(home(a), env.clock)
    private fun kidOrNone(a: Args) = keyFile(a).let { if (it.exists()) it.info().kid else "-" }

    private fun expertsAdd(a: Args): Int {
        val id = a.positional.firstOrNull() ?: throw UsageException("Identifiant de l'expert attendu : experts-ajouter ID --cle-ssh \"ssh-ed25519 AAAA…\" [--jusqu-au AAAA-MM-JJ]")
        val e = experts(a).add(id, a.need("cle-ssh"), a.get("jusqu-au"), kidOrNone(a))
        env.out.println("Expert $id ajouté${if (e.notAfter != 0L) " jusqu'au ${date(e.notAfter - 1)}" else " sans date de fin"}. Pas encore actif : « experts-signer » puis publication (docs/REMOTE-TUNNEL.md).")
        return 0
    }

    private fun expertsRemove(a: Args): Int {
        val id = a.positional.firstOrNull() ?: throw UsageException("Identifiant de l'expert attendu : experts-retirer ID")
        experts(a).remove(id, kidOrNone(a))
        env.out.println("Expert $id retiré de la liste locale. L'accès ne se ferme qu'après « experts-signer » et la publication de la nouvelle liste.")
        return 0
    }

    private fun expertsList(a: Args): Int {
        val l = experts(a).list()
        if (l.isEmpty()) { env.out.println("Aucun expert."); return 0 }
        val now = env.clock()
        for (e in l.sortedBy { it.id }) env.out.println("${e.id.padEnd(32)} ${e.publicKey.split(' ')[1].takeLast(12).padStart(14)}  ${if (e.notAfter == 0L) "sans date de fin" else (if (e.expiredAt(now)) "EXPIRÉ le " else "jusqu'au ") + date(e.notAfter - 1)}")
        return 0
    }

    /** Signs the local list with the desk key (REGISTRY scope required) and writes the experts.json to publish. */
    private fun expertsSign(a: Args): Int {
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé : « cle-creer » d'abord (${kf.file.path})")
        if (KeyScope.REGISTRY !in kf.info().scopes) throw IssueException("La clé du bureau n'a pas la portée REGISTRY : elle ne peut pas signer la liste des experts")
        val l = experts(a).list()
        val pass = passphrase(a); val s = kf.unlock(pass, env.kdf); pass.fill('\u0000')
        s ?: run { env.err.println("Code de déverrouillage faux."); throw WrongCode() }
        val signed = castbridge.core.tunnel.ExpertsList.sign(s.signer, s.signer.keyId, l, env.clock())
        castbridge.core.tunnel.ExpertsList.verify(signed.toJson(), listOf(kf.trusted()))      // never write a file the TVs would refuse
        val out = File(a.get("sortie") ?: "experts.json"); out.absoluteFile.parentFile?.mkdirs(); out.writeText(signed.toJson() + "\n")
        experts(a).logSigned(s.signer.keyId, l.size)
        env.out.println("Liste signée : ${out.path} (${l.size} expert(s), ${date(signed.generatedAt)}). À publier sur le serveur : docs/REMOTE-TUNNEL.md.")
        if (l.isEmpty()) env.out.println("ATTENTION : liste vide = plus aucun expert autorisé (le propriétaire garde son accès).")
        return 0
    }

    private fun expertsVerify(a: Args): Int {
        val file = a.positional.firstOrNull() ?: throw UsageException("Fichier experts.json attendu")
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé (nécessaire pour connaître la clé publique de confiance)")
        val l = castbridge.core.tunnel.ExpertsList.verify(readSource(file), listOf(kf.trusted())).list
        val now = env.clock()
        env.out.println("Liste VALIDE : signée par ${l.keyId} le ${date(l.generatedAt)} ; ${l.experts.size} expert(s), ${l.authorizedKeysLines(now).size} actif(s) maintenant.")
        l.experts.sortedBy { it.id }.forEach { env.out.println("  ${it.id}${if (it.notAfter != 0L) " jusqu'au ${date(it.notAfter - 1)}" else ""}${if (it.expiredAt(now)) " (expiré)" else ""}") }
        return 0
    }

    private fun keyFile(a: Args) = KeyFile(File(home(a), "desk.key.json"))

    private fun keygen(a: Args): Int {
        val kf = keyFile(a)
        val pass = passphrase(a)
        val scopes = a.get("portees")?.split(',')?.map { KeyScope.valueOf(it.trim().uppercase()) }?.toSet() ?: KeyScope.ALL
        val info = kf.create(pass, env.kdf, scopes, a.get("nom") ?: "bureau")
        pass.fill('\u0000')
        env.out.println("Clé du bureau créée : ${kf.file.path}")
        env.out.println("kid : ${info.kid}")
        env.out.println("Clé publique : ${info.publicKey}")
        env.out.println("IMPORTANT : sauvegardez ce fichier hors ligne (clé USB dans un coffre) ET le code séparément. Sans les deux, la clé est perdue ; avec le fichier seul, personne ne peut signer.")
        env.out.println("Pour que la TV l'accepte, ajoutez la clé publique et ses portées à son anneau de clés (docs/ACTIVATION-TOOLS.md).")
        return 0
    }

    private fun keyInfo(a: Args): Int {
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé : « cle-creer » d'abord (${kf.file.path})")
        val i = kf.info()
        if (a.flags.contains("json")) env.out.println(JsonLite.write(linkedMapOf("kid" to i.kid, "publicKey" to i.publicKey, "scopes" to i.scopes.map { it.name }.sorted())))
        else { env.out.println("kid : ${i.kid}"); env.out.println("Clé publique : ${i.publicKey}"); env.out.println("Portées : ${i.scopes.map { it.name }.sorted().joinToString(", ")}") }
        return 0
    }

    private fun desk(a: Args): Desk {
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé : « cle-creer » d'abord (${kf.file.path})")
        val pass = passphrase(a)
        val s = kf.unlock(pass, env.kdf); pass.fill('\u0000')
        s ?: run { env.err.println("Code de déverrouillage faux."); throw WrongCode() }
        return Desk(home(a), s.signer, s.scopes, kf.trusted(), env.clock)
    }

    private class WrongCode : Exception()

    private fun trust(a: Args): Int {
        val file = a.positional.firstOrNull() ?: throw UsageException("Fichier JSON de la clé publique attendu (sortie de « cle --json »)")
        val m = JsonLite.obj(readSource(file))
        val key = TrustedKey(m["kid"] as String, m["publicKey"] as String, (m["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet())
        val kf = keyFile(a)
        // the registry replay needs the public keys of the other tools; no secret is involved
        val desk = Desk(home(a), unlockless(), KeyScope.ALL, if (kf.exists()) kf.trusted() else key)
        desk.trust(key)
        env.out.println("Clé ${key.keyId} ajoutée à l'anneau de ce bureau (portées : ${key.scopes.map { it.name }.sorted().joinToString(", ")})")
        return 0
    }

    private fun unlockless(): castbridge.core.owner.Signer = object : castbridge.core.owner.Signer {
        override val keyId = "0000000000000000"
        override fun sign(message: ByteArray) = throw IllegalStateException("clé non déverrouillée")
    }

    private fun device(a: Args): Int {
        val d = DeviceRequest.parse(readSource(a.positional.firstOrNull() ?: "-"))
        env.out.println("Code d'appareil : ${d.code}"); env.out.println("k = ${d.k} sur n = ${d.factors.n}")
        env.out.println("Clé d'installation : ${if (d.installPub != null) "présente" else "absente (CastBridge-TV ancienne : location refusée sans --enveloppe-v1)"}")
        env.out.println("Clé de signature de la TV : ${d.installFingerprint?.let { "empreinte $it (à comparer avec l'écran d'activation de la TV avant d'émettre)" } ?: "absente (activation sans clé liée : le serveur attend le propriétaire)"}")
        d.factors.byKind.forEach { (kind, fp) -> env.out.println("  ${kind.name.padEnd(14)} $fp${if (kind.strong) "  (soudé)" else ""}") }
        if (castbridge.core.owner.DeviceIdentity.isWeak(d.factors)) env.out.println("Identité FAIBLE : aucun facteur soudé ; l'activation reste possible, signalée sur la TV.")
        return 0
    }

    private fun license(a: Args): Int {
        val id = a.positional.firstOrNull() ?: throw UsageException("Identifiant de licence attendu")
        desk(a).createLicense(id, (a.get("postes") ?: "1").toInt(), (a.get("transferts") ?: "2").toInt())
        env.out.println("Licence $id créée (${a.get("postes") ?: "1"} poste(s)).")
        return 0
    }

    private fun rights(a: Args, now: Long): List<Right> =
        a.all("achat").map { RightsSyntax.purchase(it, now) } + a.all("abonnement").map { RightsSyntax.subscription(it, now) } +
            a.all("tout-ouvert").map { RightsSyntax.openAll(it, now) } + a.all("droit").map { Activation.parseRight(it) }

    /** `--usage-jours N` or `--usage-jours illimitee`: how long the activation key works (a trial: 1 to 365, default 30; a production key: unlimited = never locks again, or 1 to 3660 days). */
    private fun usageDays(a: Args, kind: ActivationKind): Int? {
        val v = a.get("usage-jours") ?: return null
        if (v.lowercase() in setOf("illimitee", "illimitée", "illimite")) { if (kind == ActivationKind.TRIAL) throw UsageException("Un essai a toujours une durée (1 à 365 jours)"); return null }
        return v.toIntOrNull() ?: throw UsageException("--usage-jours attend un nombre de jours ou « illimitee »")
    }

    private fun issue(a: Args): Int {
        val device = DeviceRequest.parse(readSource(a.need("appareil")))
        val d = desk(a)
        val kind = if (a.flags.contains("production")) ActivationKind.PRODUCTION else ActivationKind.TRIAL
        val now = env.clock()
        a.get("jours")?.let { throw UsageException("--jours n'existe plus : une clé s'installe dans les 48 h suivant sa création (--super : SUPER_UNLIMITED, clés super administrateur seulement)") }
        val period = a.get("periode")?.toLongOrNull()
        if (a.get("periode") != null && period == null) throw UsageException("--periode attend le début (ms) de la location à prolonger")
        val explicit = a.all("location").map { RightsSyntax.rental(it, period) }
        if (explicit.isNotEmpty() && a.get("catalogue") == null && !a.flags.contains("sans-controle-catalogue"))
            throw IssueException("Location : la durée est fixée par le serveur ; chargez le catalogue du serveur (« catalogue-serveur », puis --catalogue serveur --lots-libres FICHIER), ou --sans-controle-catalogue (déconseillé : aucune durée vérifiée)")
        val bouquets = a.all("location-bouquet").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
        val rentals = explicit + if (bouquets.isEmpty()) emptyList() else {
            val catPath = a.get("catalogue") ?: throw IssueException("--location-bouquet : chargez le catalogue du serveur (« catalogue-serveur », puis --catalogue serveur --lots-libres FICHIER) ; la durée vient du catalogue, jamais de la ligne de commande")
            castbridge.core.lots.RentalDurations.specsFor(castbridge.core.lots.BundleCatalog.parse(readCatalogue(catPath, a)), bouquets).map { it.copy(period = period) }
        }
        val check = if (rentals.isEmpty()) null else rentalCheck(a)
        val boxV1 = a.flags.contains("enveloppe-v1")
        if (boxV1 && !castbridge.core.lots.RentalKeys.isV1Accepted(now)) throw IssueException("Enveloppe v1 périmée : mettez CastBridge-TV à jour (la TV doit fournir sa clé d'installation)")
        val trialLots = kind == ActivationKind.TRIAL && !a.flags.contains("sans-lots-essai")      // every trial key carries its one-time 12 h window of rented lots
        val spec = IssueSpec(kind, if (a.get("sujet") == "phone") Subject.PHONE else Subject.TV, rights(a, now) + permanent(a, kind, now), a.get("licence") ?: if (kind == ActivationKind.PRODUCTION) "" else Activation.TRIAL_LICENSE,      // production without --licence: a new licence is generated
            rentals = rentals, rentalMaster = if (rentals.isEmpty() && !trialLots) null else d.rentalMaster(), rentalCheck = check, usageDays = usageDays(a, kind), trialLots = trialLots, boxV1 = boxV1)
        val r = d.issue(device, spec)
        val dir = File(a.get("sortie") ?: "."); dir.mkdirs()
        val fileOut = File(dir, r.issued.fileName); fileOut.writeText(r.issued.fileContent)
        env.out.println("Activation ${if (kind == ActivationKind.TRIAL) "d'essai" else "de production"} pour ${device.code}")
        if (kind == ActivationKind.PRODUCTION) env.out.println("Licence ${r.issued.activation.license}${if (a.get("licence") == null) " (générée)" else ""}")
        env.out.println("Poste : ${r.seat}${if (r.reused) " (ré-activation : aucun poste consommé)" else ""}${r.seatsLeft?.let { " ; postes restants : $it" } ?: ""}")
        env.out.println("Valable à l'installation jusqu'au ${date(r.issued.activation.notAfter)}")
        r.installKeyFingerprint?.let { env.out.println("Clé d'installation de la TV liée à cette activation : empreinte $it (à comparer avec l'écran d'activation de la TV avant de la remettre)") }
        env.out.println("Fichier pour la clé USB de la TV : ${fileOut.path}  (à copier dans Download/CastBridge/)")
        env.out.println("Jeton :"); env.out.println(r.issued.token)
        r.issued.activation.rights.filterIsInstance<Right.Rental>().forEach { l ->
            if (l.productId == castbridge.core.lots.RentalLines.TRIAL_PRODUCT) { env.out.println("Fenêtre de lots d'essai (usage unique) : ${l.maxUsageMinutes} min d'usage, dans les ${l.durationDays} jours"); return@forEach }
            env.out.println("Location ${l.productId} (${l.bundleIds.joinToString(",")}) : ${l.durationDays} jour(s) à partir du ${date(l.startsAt)}, fin le ${date(l.endsAt)}" +
                (if (l.maxUsageMinutes > 0) ", usage maximal ${l.maxUsageMinutes} min" else "") + (if (l.graceMs > 0) ", tolérance ${l.graceMs / Desk.DAY_MS} j" else "") + " ; période ${l.period}")
        }
        if (rentals.isNotEmpty() && a.get("catalogue") == null) env.out.println("ATTENTION : durées de location NON vérifiées (--sans-controle-catalogue) et lots libres (CC BY-SA) NON vérifiés (--catalogue et --lots-libres absents) : un lot libre ne doit jamais être loué.")
        if (a.flags.contains("qr")) { val png = File(dir, "activation.png"); Qr.png(r.issued.token, png); env.out.println("Code QR : ${png.path}") }
        return 0
    }

    /** `--super`: the SUPER_UNLIMITED right (reads and unlocks everything, rentals included, for good); the TV refuses it unless the signing key holds SUPER_UNLIMITED. */
    private fun permanent(a: Args, kind: ActivationKind, now: Long): List<castbridge.core.lots.Right> =
        if (!a.flags.contains("super")) emptyList()
        else if (kind != ActivationKind.PRODUCTION) throw UsageException("--super : licence de production seulement (--production)")
        else listOf(castbridge.core.lots.Right.Super("super-illimite", now))

    /** Refuses a rental whose bundles hold a free lot (CC BY-SA) or an unknown bundle, from the bundle catalogue (`--catalogue TRIAL-MANIFEST.json`) and the list of free lots (`--lots-libres FICHIER`, one lot « fonction:périmètre » per line). */
    private fun rentalCheck(a: Args): ((castbridge.core.owner.RentalSpec) -> String?)? {
        val catPath = a.get("catalogue") ?: return null
        val catalog = castbridge.core.lots.BundleCatalog.parse(readCatalogue(catPath, a))
        val free = a.get("lots-libres")?.let { readSource(it).lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() && !l.startsWith("#") }.toSet() }
            ?: throw UsageException("--catalogue demande aussi --lots-libres (la liste des lots libres : jamais louables)")
        val all = catalog.bundles.flatMap { it.lots }.toSet()
        val families = castbridge.core.lots.LotFamilies.explicit(free, all - free)
        return { spec ->
            // the duration of a rental is fixed by the server's catalogue (rentalDays per bundle): the tightest bundle limit wins
            val dur = castbridge.core.lots.RentalDurations.check(spec, catalog)      // exact: « la durée est fixée par le serveur »
            if (dur != null) "durée fixée par le catalogue du serveur : $dur" else {
                val metas = catalog.lotsOf(spec.bundleIds).map { castbridge.core.lots.LotMeta(it, 1, 0, "0".repeat(64), castbridge.core.lots.LotNames.key(it)) }
                castbridge.core.lots.RentalPolicy.refusals(spec.bundleIds, catalog, metas, families).firstOrNull()
            }
        }
    }

    /**
     * `lot-chiffrer --activation FICHIER --produit P --lot castbridge-lot-…-vN.lot [--sortie DOSSIER]`: seals a lot for the TV of an issued rental activation: the file written next to
     * [--sortie] has the same name and opens ONLY on that TV with that rental's key (docs/RENTAL-LOTS.md § 3). Free lots are refused by the catalogue check at issuing time.
     */
    private fun sealLot(a: Args): Int {
        val act = castbridge.core.owner.Activation.decode(readSource(a.need("activation")).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "")
            ?: throw UsageException("Activation illisible")
        val product = a.need("produit")
        val rental = act.rights.filterIsInstance<Right.Rental>().firstOrNull { it.productId == product } ?: throw UsageException("Cette activation ne porte pas la location « $product »")
        val lot = File(a.need("lot")); val (id, version) = castbridge.core.lots.LotNames.parseFileName(lot.name) ?: throw UsageException("Nom de lot invalide : ${lot.name}")
        val d = desk(a)
        val key = castbridge.core.lots.RentalKeys.rentalKey(d.rentalMaster(), act.license, act.seat, rental.productId, rental.period)
        val dir = File(a.get("sortie") ?: "."); dir.mkdirs()
        File(dir, lot.name).writeBytes(castbridge.core.lots.RentalKeys.seal(key, id, version, lot.readBytes()))
        env.out.println("Lot chiffré pour cette TV : ${File(dir, lot.name).path} ; contrat ${castbridge.core.lots.RentalEngine.contractKey(rental.productId, rental.period)}")
        return 0
    }

    private fun compact(a: Args): Int {
        a.get("jours")?.let { throw UsageException("--jours n'existe plus : une clé s'installe dans les 48 h suivant sa création (--illimitee : réservé aux clés superadmin)") }
        val code = DeviceCode.parse(a.need("code")) ?: throw UsageException("Code d'appareil mal formé")
        val d = desk(a)
        val prod = a.flags.contains("production")
        env.out.println(d.compact(code, if (prod) ActivationKind.PRODUCTION else ActivationKind.TRIAL, setId = (a.get("ensemble") ?: "0").toInt()))
        return 0
    }

    private fun command(a: Args): Int {
        val device = DeviceRequest.parse(readSource(a.need("appareil")))
        val power = Power.valueOf(a.need("pouvoir").uppercase())
        val d = desk(a)
        env.out.println(d.command(device, power, a.need("defi"), (a.get("jours") ?: "0").toInt(), a.get("action") ?: "", a.get("bouquets")?.split(',') ?: emptyList(),
            a.get("lots")?.split(',')?.map { LotId(it.substringBefore(':'), it.substringAfter(':')) } ?: emptyList()))
        return 0
    }

    private fun verify(a: Args): Int {
        val token = (a.positional.firstOrNull() ?: throw UsageException("Jeton ou fichier attendu")).let { if (File(it).isFile) File(it).readText().trim() else it }
        val device = DeviceRequest.parse(readSource(a.need("appareil")))
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé (nécessaire pour connaître la clé publique de confiance)")
        val ring = Desk(home(a), unlockless(), KeyScope.ALL, kf.trusted()).ring()
        val now = a.get("maintenant")?.toLong() ?: env.clock()
        return when (val r = ActivationVerifier(ring).verify(token, device.factors, now)) {
            is ActivationResult.Accepted -> { env.out.println("ACCEPTÉ : ${r.activation.kind.name.lowercase()}, licence ${r.activation.license}, poste ${r.activation.seat}${if (r.weakIdentity) " (identité faible)" else ""}"); 0 }
            is ActivationResult.Rejected -> { env.out.println("REFUSÉ (${r.reason}) : ${r.message}"); 1 }
        }
    }

    private fun journal(a: Args): Int {
        val kf = keyFile(a)
        val rows = Desk(home(a), unlockless(), KeyScope.ALL, if (kf.exists()) kf.trusted() else TrustedKey("0000000000000000", "", emptySet())).journal()
        if (rows.isEmpty()) { env.out.println("Journal vide."); return 0 }
        for (r in rows) env.out.println("${date((r["at"] as Number).toLong())} | ${r["kind"]} | ${r["subject"]} | ${r["device"]} | licence ${r["license"]} | poste ${r["seat"]} | droits : ${(r["rights"] as List<*>).size}")
        return 0
    }

    private fun registry(a: Args): Int {
        val kf = keyFile(a); if (!kf.exists()) throw UsageException("Aucune clé")
        val d = Desk(home(a), unlockless(), KeyScope.ALL, kf.trusted())
        when (a.positional.firstOrNull()) {
            "exporter" -> { val out = a.positional.getOrNull(1); if (out == null) env.out.println(d.exportRegistry()) else { File(out).writeText(d.exportRegistry()); env.out.println("Registre exporté : $out (${d.events().size} événements)") } }
            "importer" -> { val n = d.importRegistry(readSource(a.positional.getOrNull(1) ?: throw UsageException("Fichier de registre attendu"))); env.out.println("$n événement(s) nouveau(x) fusionné(s)") }
            "etat" -> {
                val s = castbridge.core.owner.LicenseBook.replay(d.events(), d.ring())
                env.out.println("Licences : ${s.licenses.size} ; postes : ${s.seats.size} ; rejets : ${s.rejected.size}")
                s.licenses.values.forEach { l -> env.out.println("  ${l.id} : ${s.usedSeats(l.id)}/${l.seats} postes") }
            }
            else -> throw UsageException("registre exporter [fichier] | importer fichier | etat")
        }
        return 0
    }

    private fun selftest(a: Args): Int {
        val f = File(a.get("vecteurs") ?: System.getProperty("activation.vectors") ?: "tools/activation/test-vectors.json")
        if (!f.isFile) throw UsageException("Fichier de vecteurs introuvable : ${f.path} (--vecteurs CHEMIN)")
        val fails = Vectors.run(f)
        if (fails.isEmpty()) { env.out.println("Autotest : ${Vectors.lastCount} vecteurs rejoués, tous identiques à la référence (mêmes octets)."); return 0 }
        fails.forEach { env.err.println("ÉCHEC $it") }; env.err.println("Autotest : ${fails.size} échec(s)."); return 1
    }

    companion object {
        /** Returned by « gui »: the window keeps the process alive (the caller must not exit). */
        const val GUI_RUNNING = -1
        val HELP = """CastBridge — outil d'activation de bureau (Mac, Windows, Linux ; Java 17+)

Commandes (français ; alias anglais : keygen key trust device license issue compact command verify registry selftest) :
  cle-creer          crée la clé du bureau (code de déverrouillage ≥ 10 caractères ; scrypt, 32 Mio par essai)
  cle [--json]       affiche le kid, la clé publique et les portées (JAMAIS la clé privée)
  faire-confiance F  ajoute la clé publique d'un autre outil (téléphone propriétaire, serveur) à l'anneau
  appareil [F|-]     lit la « demande d'appareil » donnée par la TV (code=…, k=…, factor=TYPE|empreinte, install=x25519|… pour une CastBridge-TV récente)
  licence ID --postes N [--transferts N]    crée une licence (un achat)
  emettre --appareil F [--production] [--usage-jours N|illimitee] [--licence ID] [--super] [--sujet tv|phone]   (clé à installer dans les 48 h ; --super : SUPER_UNLIMITED, lit et débloque tout, locations permanentes, clé super administrateur seulement)
          Production : sans --licence, une licence « lic-… » est GÉNÉRÉE (1 poste) et affichée ; la durée (--usage-jours : 30, 60, 62, 90, 180, 300, 365, autre 1 à 3660, ou illimitee par défaut) est le seul réglage ; aucun droit de contenu n'est nécessaire.
          Options AVANCÉES (tests, outils de location ; l'interface graphique et la console du téléphone ne les proposent plus) :
          [--achat produit=b1,b2] [--abonnement produit=b1:jours[:tolérance[:auto]]] [--tout-ouvert produit:jours] [--droit ligne]
          [--location produit=b1,b2:JOURS[:MINUTES_D_USAGE_MAX[:TOLERANCE_JOURS[:SIMULTANEES]]]]   (avancé ; répétable ; 1 à 366 jours ; production seulement ; avec --catalogue la durée doit être EXACTEMENT celle du serveur)
          [--location-bouquet b1,b2]   une location par bouquet (produit loc-<bouquet>), durée EXACTE du catalogue (rentalDays) ; exige --catalogue ; sans durée à saisir
          [--enveloppe-v1]  TV ancienne (sans « install= » dans sa demande) : emballe les clés de location en enveloppe v1, faible ; refusé après le 1er janvier 2027 ; sans cette option une location pour une TV sans clé d'installation est refusée
          [--sans-controle-catalogue]  autorise --location sans catalogue (déconseillé : la durée n'est pas vérifiée ; --location sans --catalogue est sinon refusé)
          [--periode MS]  prolonge la location commencée à cet instant (même clé, pas de doublon) au lieu d'en commencer une nouvelle
          [--catalogue serveur|TRIAL-MANIFEST.json --lots-libres FICHIER]   durée exacte du serveur (« serveur » = la copie de catalogue-serveur) ; refuse la location d'un lot libre (CC BY-SA)
          [--sortie DOSSIER] [--qr]       jeton, fichier « activation » (clé USB de la TV) et code QR
  lot-chiffrer --activation F --produit P --lot FICHIER.lot [--sortie DOSSIER]   chiffre un lot pour la TV d'une location émise
  catalogue-serveur [--serveur URL] [--cle-publique B64]   importe le catalogue des bouquets DEPUIS LE SERVEUR (HTTPS, signature vérifiée avant d'être gardée dans le dossier) ; ensuite --catalogue serveur
  cle-saisissable --code XXXX-XXXX-XXXX-XXXX [--production --ensemble N]   dernier recours : 165 caractères à taper
  commande --appareil F --pouvoir support|unlock|open_all --defi HEX [--jours N] [--action A] [--bouquets a,b] [--lots fn:scope,…]
  verifier JETON --appareil F [--maintenant MS]   vérifie un jeton avec l'anneau de ce bureau
  journal            jetons émis (date, TV, droits ; jamais la clé)
  registre exporter [F] | importer F | etat    registre signé des licences (synchronisation des trois outils)
  autotest [--vecteurs F]   rejoue les vecteurs communs : mêmes entrées, mêmes octets
  experts-ajouter ID --cle-ssh "ssh-ed25519 AAAA…" [--jusqu-au AAAA-MM-JJ]   ajoute un expert de l'assistance à distance (liste locale, non signée)
  experts-retirer ID | experts-lister          retire / liste les experts
  experts-signer [--sortie experts.json]       signe la liste (clé du bureau, portée REGISTRY) : le fichier à publier sur le serveur
  experts-verifier FICHIER                     vérifie un experts.json avec la clé publique de ce bureau
  gui                interface graphique

Options communes : --dossier D (défaut ~/.castbridge-activation) ; code de déverrouillage : saisie au terminal, ou --code-env NOM, ou --code-fichier CHEMIN.
Le code n'est jamais accepté en argument (il resterait dans l'historique du terminal)."""
    }
}
