package castbridge.core.owner

import castbridge.core.lots.Right
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.system.exitProcess

/**
 * « castbridge-owner » : the desk tool that turns what a TV shows into an activation (docs/ACTIVATION-FORMAT.md, docs/ACTIVATION-TOOLS.md).
 * Uses the ONE issuing library ([ActivationIssuer]): the same bytes as the owner phone and the server, checked by the shared test vectors.
 *
 *   init     creates the signing key (sealed by YOUR passphrase) and prints the public key line the TVs must trust
 *   pubkey   prints it again
 *   compact  device code -> typed key (trial or production product set): the practical manual path
 *   activation   device request (the file the TV exports) -> signed activation (file for the USB drive, Bluetooth, grouped text)
 *   inspect  reads a device request and shows what it contains
 *
 * The passphrase is read from the terminal (never as an argument) or, for scripts, from CB_OWNER_PASSPHRASE. The key never leaves the vault file.
 */
object OwnerCli {
    private const val VAULT_HEADER = "castbridge-owner-vault-v1"
    private const val ITERATIONS = 600_000
    private const val EPOCH_2026_MS = 1767225600000L
    private const val DAY = 24L * 3600 * 1000
    private val kdf = Pbkdf2Kdf(ITERATIONS)

    interface Io { fun out(s: String); fun err(s: String); fun passphrase(prompt: String): CharArray? }
    private object Real : Io {
        override fun out(s: String) = println(s)
        override fun err(s: String) = System.err.println(s)
        override fun passphrase(prompt: String): CharArray? =
            System.getenv("CB_OWNER_PASSPHRASE")?.toCharArray() ?: System.console()?.readPassword(prompt)
    }

    @JvmStatic fun main(args: Array<String>) { exitProcess(run(args.toList(), Real)) }

    private class Fail(msg: String) : Exception(msg)

    fun run(args: List<String>, io: Io, now: () -> Long = System::currentTimeMillis): Int = try {
        val cmd = args.firstOrNull() ?: return help(io)
        val o = Opts(args.drop(1))
        when (cmd) {
            "init" -> init(o, io)
            "pubkey" -> { val s = open(o, io); io.out(publicLine(s)); 0 }
            "compact" -> compact(o, io, now)
            "activation" -> activation(o, io, now)
            "inspect" -> inspect(o, io)
            "help", "-h", "--help" -> help(io)
            else -> throw Fail("Commande inconnue : $cmd (voir « help »)")
        }
    } catch (e: Fail) { io.err("Erreur : ${e.message}"); 2 } catch (e: IssueException) { io.err("Refusé : ${e.message}"); 3 }

    private fun help(io: Io): Int {
        io.out("""castbridge-owner : générateur d'activations (hors ligne)
  init       --vault FICHIER                     crée la clé (protégée par votre code) et affiche la clé publique à faire accepter par les TV
  pubkey     --vault FICHIER
  compact    --vault F --device XXXX-XXXX-XXXX-XXXX [--kind trial|production] [--set N]
  activation --vault F --request FICHIER_DEMANDE [--kind trial|production] [--license ID] [--super oui] [--start AAAA-MM-JJ]
             [--purchase PRODUIT:BOUQUET[,BOUQUET]]... [--subscription PRODUIT:BOUQUET:AAAA-MM-JJ]... [--open-all PRODUIT --open-days N]
             [--out-file DOSSIER]                 écrit le fichier « activation » pour Download/CastBridge de la clé USB
  inspect    --request FICHIER_DEMANDE           affiche le contenu d'une demande d'appareil
Le code de déverrouillage est demandé au clavier (jamais en argument) ; en script : variable CB_OWNER_PASSPHRASE.""")
        return 0
    }

    private class Opts(a: List<String>) {
        val single = HashMap<String, String>(); val multi = HashMap<String, MutableList<String>>()
        init {
            var i = 0
            while (i < a.size) {
                val k = a[i]
                if (!k.startsWith("--")) throw Fail("Argument inattendu : $k")
                val v = a.getOrNull(i + 1)?.takeIf { !it.startsWith("--") } ?: throw Fail("Valeur manquante pour $k")
                single[k] = v; multi.getOrPut(k) { ArrayList() } += v; i += 2
            }
        }
        fun need(k: String) = single[k] ?: throw Fail("Option obligatoire : $k")
        fun opt(k: String) = single[k]
        fun all(k: String): List<String> = multi[k].orEmpty()
    }

    private fun init(o: Opts, io: Io): Int {
        val f = File(o.need("--vault"))
        if (f.exists()) throw Fail("Le coffre existe déjà : ${f.path} (rien n'est écrasé)")
        val p1 = io.passphrase("Code de déverrouillage (nouveau) : ") ?: throw Fail("Aucun terminal : utilisez CB_OWNER_PASSPHRASE")
        if (p1.size < 10) throw Fail("Code trop court (10 caractères au moins ; plus long = plus sûr : il protège la clé si le fichier est volé)")
        if (System.getenv("CB_OWNER_PASSPHRASE") == null) { val p2 = io.passphrase("Confirmez le code : "); if (!p1.contentEquals(p2)) throw Fail("Les deux codes diffèrent") }
        val seed = ByteArray(32).also(SecureRandom()::nextBytes)
        val signer = Ed25519Signer(seed)
        val b = OwnerVault.seal(seed, p1, kdf)
        f.parentFile?.mkdirs()
        f.writeText(listOf(VAULT_HEADER, "kdf=${b.kdf}", "salt=${hex(b.salt)}", "nonce=${hex(b.nonce)}", "ct=${hex(b.ciphertext)}", "check=${hex(b.check)}",
            "kid=${signer.keyId}", "pub=${signer.publicKeyBase64}").joinToString("\n") + "\n")
        runCatching { f.setReadable(false, false); f.setReadable(true, true); f.setWritable(false, false); f.setWritable(true, true) }
        seed.fill(0)
        io.out("Coffre créé : ${f.path}")
        io.out("Clé publique à faire accepter par les TV (identifiant de clé, clé, portées) :")
        io.out(publicLine(signer))
        io.out("SAUVEGARDEZ ce fichier hors ligne (clé USB chiffrée, coffre) : sans lui, vous perdez la capacité d'activer. NE le partagez pas.")
        return 0
    }

    private fun publicLine(s: Ed25519Signer) = "kid=${s.keyId} pub=${s.publicKeyBase64} scopes=${KeyScope.ALL.joinToString(",") { it.name }}"

    private fun open(o: Opts, io: Io): Ed25519Signer {
        val f = File(o.need("--vault")); if (!f.isFile) throw Fail("Coffre introuvable : ${f.path}")
        val kv = f.readLines().drop(1).associate { l -> l.substringBefore('=') to l.substringAfter('=') }
        val blob = VaultBlob(kv["kdf"] ?: throw Fail("Coffre illisible"), unhex(kv["salt"]), unhex(kv["nonce"]), unhex(kv["ct"]), unhex(kv["check"]))
        val pass = io.passphrase("Code de déverrouillage : ") ?: throw Fail("Aucun terminal : utilisez CB_OWNER_PASSPHRASE")
        Thread.sleep(300)   // one slow try: guessing costs the KDF AND this delay
        val seed = OwnerVault.open(blob, pass, kdf) ?: throw Fail("Code incorrect")
        return Ed25519Signer(seed).also { seed.fill(0) }
    }

    private fun startOf(o: Opts, now: Long): Long = o.opt("--start")?.let {
        runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() ?: throw Fail("Date invalide : $it (AAAA-MM-JJ)")
    } ?: (now / DAY * DAY)

    private fun kindOf(o: Opts) = when (o.opt("--kind") ?: "trial") {
        "trial", "essai" -> ActivationKind.TRIAL; "production", "prod" -> ActivationKind.PRODUCTION
        else -> throw Fail("--kind : trial ou production")
    }

    /** `--super oui` adds the SUPER_UNLIMITED right (reads and unlocks everything, rentals included, for good): super administrator key only, the TV refuses it from any other key. */
    private fun superOf(o: Opts) = when (o.opt("--super")) { null, "non" -> false; "oui" -> true; else -> throw Fail("--super : oui ou non") }

    private fun compact(o: Opts, io: Io, now: () -> Long): Int {
        val device = o.need("--device"); val kind = kindOf(o)
        val code = DeviceCode.parse(device) ?: throw Fail("Code d'appareil mal formé (16 caractères, contrôle compris)")
        val signer = open(o, io)
        o.opt("--super")?.let { throw Fail("--super : une clé compacte ne porte aucun droit ; utilisez « activation » (fichier ou Bluetooth)") }
        val t = now(); val hour = ((t - EPOCH_2026_MS) / ActivationPolicy.HOUR_MS).toInt()      // the window starts at creation (to the hour)
        val key = ActivationIssuer(signer).issueCompact(kind, device, hour, ActivationIssuer.MAX_WINDOW_HOURS, (o.opt("--set") ?: "0").toIntOrNull() ?: throw Fail("--set : nombre"))
        io.out(key)
        journal(o, "compact", code, kind.name, "-", ActivationIssuer.MAX_WINDOW_HOURS, t)
        io.err("Clé compacte émise pour $code (${kind.name.lowercase()}, " + "à installer dans les ${ActivationIssuer.MAX_WINDOW_HOURS} h). À saisir sur la TV ou le téléphone.")
        return 0
    }

    private fun activation(o: Opts, io: Io, now: () -> Long): Int {
        val (code, _, fp) = parseRequest(File(o.need("--request")))
        val kind = kindOf(o); val signer = open(o, io)
        val t = now(); val start = startOf(o, t)
        val rights = ArrayList<Right>()
        o.all("--purchase").forEach { rights += purchase(it, t) }
        o.all("--subscription").forEach { rights += subscription(it, start) }
        o.opt("--open-all")?.let { p ->
            val n = (o.opt("--open-days") ?: "30").toIntOrNull() ?: throw Fail("--open-days : nombre")
            rights += Right.OpenAll(p, start, start + n * DAY)
        }
        if (superOf(o)) {
            if (kind == ActivationKind.TRIAL) throw Fail("--super : licence de production seulement (--kind production)")
            rights += Right.Super("super-illimite", t)
        }
        val license = o.opt("--license") ?: if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else LicenseIds.generate().also { io.err("Licence $it (générée)") }
        val issued = ActivationIssuer(signer).issue(ActivationIssuer.Request(kind, code, fp, issuedAt = t, rights = rights, license = license))
        io.out(issued.token)
        o.opt("--out-file")?.let {
            val dir = File(it).also { d -> d.mkdirs() }; File(dir, issued.fileName).writeText(issued.fileContent)
            io.err("Fichier écrit : ${File(dir, issued.fileName).path} (à copier dans Download/CastBridge de la clé USB de la TV)")
        }
        journal(o, "activation", code, kind.name, license, ActivationIssuer.MAX_WINDOW_HOURS, t)
        io.err("Activation émise pour $code (${kind.name.lowercase()}, licence $license, ${rights.size} droit(s), " + (if (superOf(o)) "SUPER_UNLIMITED, " else "") + "à installer dans les ${ActivationIssuer.MAX_WINDOW_HOURS} h).")
        return 0
    }

    private fun purchase(s: String, now: Long): Right {
        val (p, b) = s.split(':', limit = 2).let { if (it.size < 2) throw Fail("--purchase PRODUIT:BOUQUET[,BOUQUET]") else it[0] to it[1] }
        return Right.Purchase(p, b.split(',').filter { it.isNotBlank() }, now)
    }

    private fun subscription(s: String, start: Long): Right {
        val p = s.split(':'); if (p.size != 3) throw Fail("--subscription PRODUIT:BOUQUET:AAAA-MM-JJ")
        val end = runCatching { LocalDate.parse(p[2]).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() ?: throw Fail("Date invalide : ${p[2]}")
        return Right.Subscription(p[0], p[1].split(',').filter { it.isNotBlank() }, start, end, 7 * DAY, false)
    }

    private fun inspect(o: Opts, io: Io): Int {
        val (code, k, fp) = parseRequest(File(o.need("--request")))
        io.out("Code d'appareil : $code · k=$k sur n=${fp.n} · identité ${if (fp.byKind.keys.any { it.strong }) "solide" else "FAIBLE (aucun facteur soudé)"}")
        fp.byKind.forEach { (kind, h) -> io.out("  ${kind.name.padEnd(14)} $h") }
        return 0
    }

    private fun parseRequest(f: File): Triple<String, Int, Fingerprints> {
        if (!f.isFile) throw Fail("Demande introuvable : ${f.path}")
        @Suppress("DEPRECATION") val r = OwnerFrames.parseDeviceInfoLegacy(f.readText().trim().replace("\r", "")) ?: throw Fail("Demande d'appareil illisible (attendu : code=…, k=…, factor=TYPE|empreinte)")
        if (DeviceCode.of(r.third) != r.first) throw Fail("Le code d'appareil ne correspond pas aux empreintes de la demande (fichier altéré ?)")
        return r
    }

    private fun journal(o: Opts, what: String, code: String, kind: String, license: String, days: Int, at: Long) {
        val f = File(o.opt("--journal") ?: "castbridge-owner-journal.log")
        runCatching { f.appendText("${java.time.Instant.ofEpochMilli(at)}\t$what\t$code\t$kind\t$license\t${days}j\n") }   // never the key, never a passphrase
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String?): ByteArray = s?.takeIf { it.length % 2 == 0 && it.all { c -> c in "0123456789abcdef" } }?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: throw Fail("Coffre illisible")
}
