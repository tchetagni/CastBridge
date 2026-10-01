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
        companion object { val FLAGS = setOf("qr", "production", "essai", "sans-confirmation", "json") }
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
        catch (e: IllegalArgumentException) { env.err.println("Refusé : ${e.message}"); 1 }
    }

    private fun dispatch(cmd: String, a: Args): Int = when (cmd) {
        "cle-creer", "keygen" -> keygen(a)
        "cle", "key" -> keyInfo(a)
        "faire-confiance", "trust" -> trust(a)
        "appareil", "device" -> device(a)
        "licence", "license" -> license(a)
        "emettre", "issue" -> issue(a)
        "cle-saisissable", "compact" -> compact(a)
        "commande", "command" -> command(a)
        "verifier", "verify" -> verify(a)
        "journal" -> journal(a)
        "registre", "registry" -> registry(a)
        "autotest", "selftest" -> selftest(a)
        "gui", "interface" -> { Gui.launch(home(a)); GUI_RUNNING }
        else -> throw UsageException("Commande inconnue : $cmd")
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

    private fun issue(a: Args): Int {
        val device = DeviceRequest.parse(readSource(a.need("appareil")))
        val d = desk(a)
        val kind = if (a.flags.contains("production")) ActivationKind.PRODUCTION else ActivationKind.TRIAL
        val now = env.clock()
        a.get("jours")?.let { throw UsageException("--jours n'existe plus : une clé s'installe dans les 48 h suivant sa création (--illimitee : réservé aux clés superadmin)") }
        val spec = IssueSpec(kind, if (a.get("sujet") == "phone") Subject.PHONE else Subject.TV, rights(a, now) + permanent(a, kind, now), a.get("licence") ?: Activation.TRIAL_LICENSE)
        val r = d.issue(device, spec)
        val dir = File(a.get("sortie") ?: "."); dir.mkdirs()
        val fileOut = File(dir, r.issued.fileName); fileOut.writeText(r.issued.fileContent)
        env.out.println("Activation ${if (kind == ActivationKind.TRIAL) "d'essai" else "de production"} pour ${device.code}")
        env.out.println("Poste : ${r.seat}${if (r.reused) " (ré-activation : aucun poste consommé)" else ""}${r.seatsLeft?.let { " ; postes restants : $it" } ?: ""}")
        env.out.println("Valable à l'installation jusqu'au ${date(r.issued.activation.notAfter)}")
        env.out.println("Fichier pour la clé USB de la TV : ${fileOut.path}  (à copier dans Download/CastBridge/)")
        env.out.println("Jeton :"); env.out.println(r.issued.token)
        if (a.flags.contains("qr")) { val png = File(dir, "activation.png"); Qr.png(r.issued.token, png); env.out.println("Code QR : ${png.path}") }
        return 0
    }

    /** `--permanente`: the PERMANENT usage licence (purchase of the bundle « tout »); the TV refuses it unless the signing key holds ISSUE_UNLIMITED. */
    private fun permanent(a: Args, kind: ActivationKind, now: Long): List<castbridge.core.lots.Right> =
        if (!a.flags.contains("permanente")) emptyList()
        else if (kind != ActivationKind.PRODUCTION) throw UsageException("--permanente : licence de production seulement (--production)")
        else listOf(castbridge.core.lots.Right.Purchase("licence-permanente", listOf(castbridge.core.owner.ActivationPolicy.ALL_BUNDLE), now))

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
  appareil [F|-]     lit la « demande d'appareil » donnée par la TV (code=…, k=…, factor=TYPE|empreinte)
  licence ID --postes N [--transferts N]    crée une licence (un achat)
  emettre --appareil F [--production] [--licence ID] [--permanente] [--sujet tv|phone]   (clé à installer dans les 48 h ; --permanente : licence d'usage sans fin, clés superadmin seulement)
          [--achat produit=b1,b2] [--abonnement produit=b1:jours[:tolérance[:auto]]] [--tout-ouvert produit:jours] [--droit ligne]
          [--sortie DOSSIER] [--qr]       jeton, fichier « activation » (clé USB de la TV) et code QR
  cle-saisissable --code XXXX-XXXX-XXXX-XXXX [--production --ensemble N]   dernier recours : 165 caractères à taper
  commande --appareil F --pouvoir support|unlock|open_all --defi HEX [--jours N] [--action A] [--bouquets a,b] [--lots fn:scope,…]
  verifier JETON --appareil F [--maintenant MS]   vérifie un jeton avec l'anneau de ce bureau
  journal            jetons émis (date, TV, droits ; jamais la clé)
  registre exporter [F] | importer F | etat    registre signé des licences (synchronisation des trois outils)
  autotest [--vecteurs F]   rejoue les vecteurs communs : mêmes entrées, mêmes octets
  gui                interface graphique

Options communes : --dossier D (défaut ~/.castbridge-activation) ; code de déverrouillage : saisie au terminal, ou --code-env NOM, ou --code-fichier CHEMIN.
Le code n'est jamais accepté en argument (il resterait dans l'historique du terminal)."""
    }
}
