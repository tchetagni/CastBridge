package castbridge.core.owner

import castbridge.core.lots.LotId
import castbridge.core.lots.Right
import castbridge.core.net.JsonLite
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

/**
 * tools/activation/test-vectors.json: the vectors every issuing tool (desk application, owner phone, server) and the TV verifier must pass
 * (docs/ACTIVATION-FORMAT.md). Same inputs, same bytes: Ed25519 signatures are deterministic. The keys here are TEST KEYS derived from public strings,
 * worth nothing. Regenerate with `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ActivationVectorsTest*'` after an intentional format change.
 */
class ActivationVectorsTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    // ---- fixed inputs ----
    private fun seedOf(name: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|$name".toByteArray())
    private class KeyDef(val name: String, val signer: Ed25519Signer, val scopes: Set<KeyScope>)
    private val keys = listOf(
        KeyDef("desk", Ed25519Signer(seedOf("desk")), KeyScope.ALL),
        KeyDef("phone", Ed25519Signer(seedOf("phone")), KeyScope.ALL - KeyScope.REGISTRY),
        KeyDef("server", Ed25519Signer(seedOf("server")), setOf(KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION, KeyScope.REVOKE, KeyScope.REGISTRY)),
        KeyDef("support", Ed25519Signer(seedOf("support")), setOf(KeyScope.COMMAND_SUPPORT)),
        KeyDef("rogue", Ed25519Signer(seedOf("rogue")), KeyScope.ALL),            // never in a ring: "unknown key"
    )
    private fun key(n: String) = keys.first { it.name == n }

    private class Dev(val name: String, val raw: RawFactors) { val fp = DeviceIdentity.fingerprints(raw); val code = DeviceCode.of(fp) }
    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val usb = "/sys/devices/platform/soc/fe340000.usb/usb1/1-1/1-1.1:1.0/net/wlan0"
    private val devices = listOf(
        Dev("tvA", RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", soldered, "SYSA0001", "11:22:33:44:55:01")),
        Dev("tvA-swapped-wifi", RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "99:99:99:99:99:99", soldered, "SYSA0001", "11:22:33:44:55:01")),
        Dev("tvB-no-ethernet-usb-wifi", RawFactors("FLASHSERIAL-B2", "cid-b2", null, "10:20:30:40:50:02", usb, "SYSB0002", "11:22:33:44:55:02")),
        Dev("tvC-weak", RawFactors(systemSerial = "SYSC0003")),
        Dev("phoneP", RawFactors(systemSerial = "androidid:9774d56d682e549c")),
        Dev("tvN1", RawFactors("FLASHSERIAL-N1", "cid-n1", "AA:BB:CC:00:11:11", "10:20:30:40:50:11", soldered, "SYSN0001", "11:22:33:44:55:11")),
        Dev("tvN2", RawFactors("FLASHSERIAL-N2", "cid-n2", "AA:BB:CC:00:11:12", "10:20:30:40:50:12", soldered, "SYSN0002", "11:22:33:44:55:12")),
        Dev("tvN3", RawFactors("FLASHSERIAL-N3", "cid-n3", "AA:BB:CC:00:11:13", "10:20:30:40:50:13", soldered, "SYSN0003", "11:22:33:44:55:13")),
    )
    private fun dev(n: String) = devices.first { it.name == n }

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)

    // ---- generation ----
    private fun issuer(k: String) = ActivationIssuer(key(k).signer, key(k).scopes)
    private val purchase = Right.Purchase("p-classe-cm2", listOf("classe-cm2"), t0 - 10 * day)
    private val sub = Right.Subscription("abo-tout", listOf("tout"), t0 - day, t0 + 90 * day, 7 * day, true)
    private val openAll = Right.OpenAll("ouvert", t0, t0 + 10 * day)

    private fun req(d: String, kind: ActivationKind = ActivationKind.PRODUCTION, rights: List<Right> = listOf(purchase), license: String = "lic-0001", subject: Subject = Subject.TV,
                    nonce: String = "00112233445566778899aabbccddeeff", issuedAt: Long = t0, notBefore: Long = t0 - day, window: Int = 300, seat: String? = null) =
        ActivationIssuer.Request(kind, dev(d).code, dev(d).fp, issuedAt, subject, if (kind == ActivationKind.TRIAL) emptyList() else rights,
            if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else license, seat, notBefore, window, nonce)

    private fun reqJson(r: ActivationIssuer.Request, dev: String) = J("kind" to r.kind.name.lowercase(), "device" to dev, "issuedAt" to r.issuedAt, "subject" to r.subject.name.lowercase(),
        "rights" to r.rights.map { Activation.rightLine(it) }, "license" to r.license, "seat" to r.seat, "notBefore" to r.notBefore, "windowDays" to r.windowDays, "nonce" to r.nonce)

    private fun raw(k: String, d: String, kind: ActivationKind = ActivationKind.PRODUCTION, rights: List<Right> = listOf(purchase), issued: Long = t0, from: Long = t0 - day, to: Long = t0 + 300 * day,
                    subject: Subject = Subject.TV, license: String = "lic-0001", seat: String? = null, nonce: String = "00112233445566778899aabbccddeeff", kk: Int? = null): String {
        val fp = dev(d).fp; val s = seat ?: SeatIds.of(license, fp); val kv = kk ?: DeviceIdentity.kFor(fp.n)
        val p = Activation.payload(kind, subject, key(k).signer.keyId, nonce, issued, from, to, license, s, kv, fp.byKind, rights)
        return Activation(kind, subject, key(k).signer.keyId, nonce, issued, from, to, license, s, kv, fp.byKind, rights, Base64.getEncoder().encodeToString(key(k).signer.sign(p.toByteArray()))).encode()
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun activationCase(id: String, why: String, token: String, d: String, now: Long = t0, trusted: List<String> = listOf("desk", "phone", "server", "support"), revoked: List<String> = emptyList(),
                               seats: List<Map<String, Any?>> = emptyList(), subject: Subject = Subject.TV, ring: KeyRing? = null) =
        J("type" to "activation", "id" to id, "description" to why, "token" to token, "device" to d, "nowMs" to now, "trustedKeys" to trusted, "revokedKeys" to revoked, "revokedSeats" to seats,
            "expectSubject" to subject.name.lowercase(), "expect" to evaluate(token, d, now, trusted, revoked, seats, subject))

    private fun ringOf(trusted: List<String>, revoked: List<String>) = KeyRing(trusted.map { key(it).signer.trusted(key(it).scopes) }, revoked.map { key(it).signer.keyId }.toSet())

    private fun evaluate(token: String, d: String, now: Long, trusted: List<String>, revoked: List<String>, seats: List<Map<String, Any?>>, subject: Subject): Map<String, Any?> {
        val rev = RevocationState(emptySet(), seats.associate { "${it["license"]}|${it["seat"]}" to (it["at"] as Long) })
        return when (val r = ActivationVerifier(ringOf(trusted, revoked), revocations = rev, expect = subject).verify(token, dev(d).fp, now)) {
            is ActivationResult.Accepted -> J("result" to "accepted", "kind" to r.activation.kind.name.lowercase(), "subject" to r.activation.subject.name.lowercase(),
                "license" to r.activation.license, "seat" to r.activation.seat, "weakIdentity" to r.weakIdentity)
            is ActivationResult.Rejected -> J("result" to "rejected", "reason" to r.reason.name, "suspect" to r.suspect)
        }
    }

    private fun generate(): Map<String, Any?> {
        val cases = ArrayList<Map<String, Any?>>()
        // 1. identity
        for (d in devices) cases += J("type" to "fingerprints", "id" to "fp-${d.name}", "description" to "facteurs bruts -> empreintes, code d'appareil, k", "raw" to rawJson(d.raw),
            "expect" to J("fingerprints" to d.fp.byKind.mapKeys { it.key.name }, "code" to d.code, "k" to DeviceIdentity.kFor(d.fp.n), "weak" to DeviceIdentity.isWeak(d.fp), "setHash" to hex(d.fp.setHash())))
        cases += J("type" to "fingerprints", "id" to "fp-placeholders", "description" to "valeurs bidon ignorées, aucun facteur",
            "raw" to rawJson(RawFactors("0000000000", null, "02:00:00:00:00:00", null, null, "unknown", "00:00:00:00:00:00")), "expect" to J("fingerprints" to emptyMap<String, String>(), "code" to null, "k" to 1, "weak" to true, "setHash" to null))
        // 2. text codings
        cases += J("type" to "base32", "id" to "b32-1", "description" to "Base32 Crockford, sans remplissage", "bytesHex" to "00ff10", "expectText" to Base32C.encode(byteArrayOf(0, -1, 0x10)))
        cases += J("type" to "grouped", "id" to "grouped-1", "description" to "groupes de 5 (4 + contrôle), préfixe de longueur sur 2 octets", "bytesHex" to hex("castbridge".toByteArray()), "expectText" to GroupedText.encode("castbridge".toByteArray()))
        val g = GroupedText.encode("castbridge".toByteArray()).split('-').toMutableList(); g[1] = g[1].replaceRange(0, 1, if (g[1][0] == 'A') "B" else "A")
        cases += J("type" to "grouped-decode", "id" to "grouped-bad-group", "description" to "un caractère faux : le groupe 2 est désigné", "text" to g.joinToString("-"), "expect" to J("result" to "bad-group", "group" to 2))
        cases += J("type" to "grouped-decode", "id" to "grouped-sloppy", "description" to "minuscules, espaces, O pour 0 et I pour 1 acceptés", "text" to GroupedText.encode("castbridge".toByteArray()).lowercase().replace('0', 'o').replace('1', 'i').replace('-', ' '), "expect" to J("result" to "ok", "bytesHex" to hex("castbridge".toByteArray())))
        cases += J("type" to "device-code-parse", "id" to "code-confusions", "description" to "code d'appareil saisi avec confusions O/0 I/1 et minuscules", "text" to dev("tvA").code.lowercase().replace('0', 'o').replace('1', 'i'), "expect" to J("result" to "ok", "code" to dev("tvA").code))
        cases += J("type" to "device-code-parse", "id" to "code-bad-check", "description" to "somme de contrôle fausse", "text" to dev("tvA").code.let { it.dropLast(1) + (if (it.last() == 'A') 'B' else 'A') }, "expect" to J("result" to "invalid"))
        // 3. activations: valid
        val tvA = dev("tvA")
        val trialTok = issuer("desk").issue(req("tvA", ActivationKind.TRIAL)).token
        val prodTok = issuer("desk").issue(req("tvA", rights = listOf(purchase, sub))).token
        val openTok = issuer("desk").issue(req("tvA", rights = listOf(openAll))).token
        cases += activationCase("act-trial", "clé d'essai pour la TV (aucun droit, licence trial)", trialTok, "tvA")
        cases += activationCase("act-production", "production : achat + abonnement", prodTok, "tvA")
        cases += activationCase("act-open-all", "« tout ouvert » de 10 jours porté par la clé bureau", openTok, "tvA")
        cases += activationCase("act-swapped-module", "même activation, module Wi-Fi remplacé : k=4 sur n=5, acceptée", prodTok, "tvA-swapped-wifi")
        cases += activationCase("act-no-ethernet-tv", "TV sans Ethernet, Wi-Fi USB exclu : n=3, k=2", issuer("server").issue(req("tvB-no-ethernet-usb-wifi", rights = listOf(purchase))).token, "tvB-no-ethernet-usb-wifi")
        cases += activationCase("act-weak-identity", "identité faible (un seul facteur faible) : acceptée, signalée", issuer("desk").issue(req("tvC-weak", rights = listOf(purchase))).token, "tvC-weak")
        cases += activationCase("act-phone", "activation de téléphone (sujet phone)", issuer("desk").issue(req("phoneP", rights = listOf(purchase), subject = Subject.PHONE)).token, "phoneP", subject = Subject.PHONE)
        cases += activationCase("act-server-key", "clé serveur : essai et achats seulement", issuer("server").issue(req("tvA", rights = listOf(purchase))).token, "tvA")
        cases += activationCase("act-reissue-same-seat", "ré-émission : même matériel, même poste (même seat), autre nonce", issuer("phone").issue(req("tvA", rights = listOf(purchase), nonce = "ffeeddccbbaa99887766554433221100", issuedAt = t0 + day)).token, "tvA", now = t0 + day)
        // 4. activations: invalid
        val flip = prodTok.split('.').let { p -> p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(String(Base64.getUrlDecoder().decode(p[1])).replace("classe-cm2", "classe-6e").toByteArray()) + "." + p[2] }
        cases += activationCase("act-bad-tampered", "charge utile modifiée après signature", flip, "tvA")
        cases += activationCase("act-bad-unknown-key", "signée par une clé absente de l'anneau", issuer("rogue").issue(req("tvA")).token, "tvA")
        cases += activationCase("act-bad-revoked-key", "signée par une clé révoquée", prodTok, "tvA", revoked = listOf("desk"))
        cases += activationCase("act-bad-scope-trial-only", "clé sans le droit de délivrer la production", raw("support", "tvA"), "tvA")
        cases += activationCase("act-bad-scope-open-all", "la clé serveur ne peut pas délivrer « tout ouvert »", raw("server", "tvA", rights = listOf(openAll)), "tvA")
        cases += activationCase("act-bad-open-all-too-long", "« tout ouvert » de 40 jours refusé par la TV", raw("desk", "tvA", rights = listOf(Right.OpenAll("ouvert", t0, t0 + 40 * day))), "tvA")
        cases += activationCase("act-bad-trial-with-rights", "une clé d'essai ne porte aucun droit", raw("desk", "tvA", kind = ActivationKind.TRIAL, rights = listOf(purchase), license = "trial"), "tvA")
        cases += activationCase("act-bad-wrong-device", "activation d'une autre TV (aucun facteur ne correspond)", prodTok, "tvB-no-ethernet-usb-wifi")
        cases += activationCase("act-bad-wrong-subject", "activation de téléphone présentée à une TV", issuer("desk").issue(req("tvA", subject = Subject.PHONE)).token, "tvA")
        cases += activationCase("act-bad-window-too-long", "fenêtre d'installation de 400 jours (> 366)", raw("desk", "tvA", from = t0, to = t0 + 400 * day), "tvA")
        cases += activationCase("act-bad-not-yet-valid", "pas encore valable (début dans 100 jours)", raw("desk", "tvA", from = t0 + 100 * day, to = t0 + 130 * day), "tvA")
        cases += activationCase("act-bad-window-closed", "fenêtre close depuis 30 jours", raw("desk", "tvA", issued = t0 - 60 * day, from = t0 - 60 * day, to = t0 - 30 * day), "tvA")
        cases += activationCase("act-ok-wrong-clock", "horloge de la TV en 1970 : la date d'émission sert de plancher, acceptée", prodTok, "tvA", now = 0L)
        cases += activationCase("act-bad-revoked-seat", "poste transféré : révoqué à une date postérieure à l'émission", prodTok, "tvA",
            seats = listOf(J("license" to "lic-0001", "seat" to SeatIds.of("lic-0001", tvA.fp), "at" to t0 + day)), now = t0 + 2 * day)
        cases += activationCase("act-ok-reissued-after-revocation", "ré-émise après la révocation du poste : valide", issuer("desk").issue(req("tvA", issuedAt = t0 + 3 * day, notBefore = t0 + 3 * day, nonce = "0a0b0c0d0e0f00010203040506070809")).token, "tvA",
            seats = listOf(J("license" to "lic-0001", "seat" to SeatIds.of("lic-0001", tvA.fp), "at" to t0 + day)), now = t0 + 3 * day)
        cases += activationCase("act-bad-noncanonical", "forme non canonique (retour à la ligne final) refusée", prodTok.split('.').let { p -> p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString((String(Base64.getUrlDecoder().decode(p[1])) + "\n").toByteArray()) + "." + p[2] }, "tvA")
        cases += activationCase("act-bad-malformed", "texte quelconque", "pas-un-jeton", "tvA")
        // 5. compact
        val code = tvA.code
        val day0 = ((t0 - CompactActivation.EPOCH_MS) / day).toInt() - 1
        val compactOk = issuer("desk").issueCompact(ActivationKind.TRIAL, code, day0, 200)
        fun compactCase(id: String, why: String, text: String, c: String = code, now: Long = t0, trusted: List<String> = listOf("desk", "phone", "server"), revoked: List<String> = emptyList()): Map<String, Any?> {
            val r = CompactActivation.verify(text, ringOf(trusted, revoked), trusted.map { key(it).signer.trusted(key(it).scopes) }, c, now)
            val exp = when (r) { is ActivationResult.Accepted -> J("result" to "accepted", "kind" to r.activation.kind.name.lowercase()); is ActivationResult.Rejected -> J("result" to "rejected", "reason" to r.reason.name) }
            return J("type" to "compact", "id" to id, "description" to why, "text" to text, "deviceCode" to c, "nowMs" to now, "trustedKeys" to trusted, "revokedKeys" to revoked, "expect" to exp)
        }
        cases += compactCase("compact-ok", "clé saisissable d'essai : 33 groupes de 5", compactOk)
        cases += compactCase("compact-sloppy", "saisie approximative : minuscules, espaces, O/0, I/1", compactOk.lowercase().replace('0', 'o').replace('1', 'i').replace('-', ' '))
        cases += compactCase("compact-production", "clé saisissable de production (ensemble 3)", issuer("phone").issueCompact(ActivationKind.PRODUCTION, code, day0, 100, 3))
        val groups = compactOk.split('-').toMutableList(); groups[11] = groups[11].replaceRange(1, 2, if (groups[11][1] == 'A') "B" else "A")
        cases += compactCase("compact-bad-group", "groupe 12 mal saisi", groups.joinToString("-"))
        cases += compactCase("compact-bad-device", "liée strictement au code d'appareil", compactOk, c = dev("tvB-no-ethernet-usb-wifi").code)
        cases += compactCase("compact-bad-revoked", "clé révoquée", compactOk, revoked = listOf("desk"))
        cases += compactCase("compact-bad-closed", "fenêtre close", compactOk, now = t0 + 900 * day)
        // 6. owner commands
        val ch1 = "0123456789abcdef0123456789abcdef"; val ch2 = "fedcba9876543210fedcba9876543210"
        fun cmdCase(id: String, why: String, d: String, open: List<String>, steps: List<Triple<String, Long, String?>>, trusted: List<String> = listOf("desk", "phone", "server", "support"), revoked: List<String> = emptyList()): Map<String, Any?> {
            val book = ChallengeBook({ 5_000L })
            book.restore(open)
            val ver = OwnerCommandVerifier(ringOf(trusted, revoked), book)
            val out = steps.map { (tok, wall, _) ->
                val clock = TvClock()
                val r = ver.verify(tok, dev(d).fp, clock, wall)
                J("token" to tok, "wallMs" to wall, "expect" to when (r) {
                    is CommandResult.Accepted -> J("result" to "accepted", "power" to r.grant.power.name.lowercase(), "untilMs" to r.grant.untilMs, "clamped" to r.grant.clamped)
                    is CommandResult.Rejected -> J("result" to "rejected", "reason" to r.reason.name)
                })
            }
            return J("type" to "command", "id" to id, "description" to why, "device" to d, "openChallenges" to open, "trustedKeys" to trusted, "revokedKeys" to revoked, "steps" to out)
        }
        val openCmd = issuer("desk").issueCommand(Power.OPEN_ALL, tvA.fp, ch1, days = 7)
        cases += cmdCase("cmd-open-all", "« tout ouvert » 7 jours, défi émis par la TV", "tvA", listOf(ch1), listOf(Triple(openCmd, t0, null)))
        cases += cmdCase("cmd-replay", "le même jeton rejoué : refusé", "tvA", listOf(ch1), listOf(Triple(openCmd, t0, null), Triple(openCmd, t0, null)))
        cases += cmdCase("cmd-unknown-challenge", "défi jamais émis par cette TV", "tvA", listOf(ch2), listOf(Triple(openCmd, t0, null)))
        cases += cmdCase("cmd-wrong-tv", "commande destinée à une autre TV", "tvB-no-ethernet-usb-wifi", listOf(ch1), listOf(Triple(openCmd, t0, null)))
        cases += cmdCase("cmd-revoked-key", "clé révoquée", "tvA", listOf(ch1), listOf(Triple(openCmd, t0, null)), revoked = listOf("desk"))
        val raw90 = key("desk").signer.let { s -> val p = OwnerCommand.payload(s.keyId, Power.OPEN_ALL, "", ch1, 4, tvA.fp.byKind, emptyList(), emptyList(), 90); OwnerCommand(s.keyId, Power.OPEN_ALL, "", ch1, 4, tvA.fp.byKind, emptyList(), emptyList(), 90, Base64.getEncoder().encodeToString(s.sign(p.toByteArray()))).encode() }
        cases += cmdCase("cmd-clamped-30-days", "90 jours demandés : la TV borne à 30 jours", "tvA", listOf(ch1), listOf(Triple(raw90, t0, null)))
        cases += cmdCase("cmd-support-key-open-all", "clé de support seule : pouvoir « tout ouvert » refusé", "tvA", listOf(ch1),
            listOf(Triple(key("support").signer.let { s -> val p = OwnerCommand.payload(s.keyId, Power.OPEN_ALL, "", ch1, 4, tvA.fp.byKind, emptyList(), emptyList(), 5); OwnerCommand(s.keyId, Power.OPEN_ALL, "", ch1, 4, tvA.fp.byKind, emptyList(), emptyList(), 5, Base64.getEncoder().encodeToString(s.sign(p.toByteArray()))).encode() }, t0, null)))
        cases += cmdCase("cmd-support-reset", "support : remise à zéro de l'essai", "tvA", listOf(ch2), listOf(Triple(issuer("support").issueCommand(Power.SUPPORT, tvA.fp, ch2, action = "reset-trial"), t0, null)))
        cases += cmdCase("cmd-unlock-lots", "déblocage de lots précis, 10 jours", "tvA", listOf(ch1), listOf(Triple(issuer("phone").issueCommand(Power.UNLOCK, tvA.fp, ch1, days = 10, lots = listOf(LotId("learn", "cm2"), LotId("quiz", "cm2"))), t0, null)))
        // 7. issuer builds: same inputs, same bytes (and refusals)
        fun build(id: String, why: String, k: String, r: ActivationIssuer.Request, d: String) = J("type" to "build-activation", "id" to id, "description" to why, "signer" to k, "request" to reqJson(r, d), "expect" to J("token" to issuer(k).issue(r).token))
        cases += build("build-trial", "construire la clé d'essai", "desk", req("tvA", ActivationKind.TRIAL), "tvA")
        cases += build("build-production", "construire la production (achat + abonnement)", "desk", req("tvA", rights = listOf(purchase, sub)), "tvA")
        cases += build("build-phone", "construire une activation de téléphone", "phone", req("phoneP", rights = listOf(purchase), subject = Subject.PHONE), "phoneP")
        cases += J("type" to "build-compact", "id" to "build-compact-trial", "description" to "construire la clé saisissable", "signer" to "desk", "request" to J("kind" to "trial", "deviceCode" to code, "notBeforeDay" to day0, "windowDays" to 200, "setId" to 0), "expect" to J("text" to compactOk))
        cases += J("type" to "build-command", "id" to "build-command-open-all", "description" to "construire la commande « tout ouvert »", "signer" to "desk", "request" to J("power" to "open_all", "device" to "tvA", "challenge" to ch1, "days" to 7, "action" to "", "bundles" to emptyList<String>(), "lots" to emptyList<String>()), "expect" to J("token" to openCmd))
        fun refuse(id: String, why: String, k: String, r: ActivationIssuer.Request, d: String) = J("type" to "build-activation", "id" to id, "description" to why, "signer" to k, "request" to reqJson(r, d), "expect" to J("refused" to true))
        cases += refuse("build-refuse-window", "durée hors bornes (400 jours)", "desk", req("tvA", window = 400), "tvA")
        cases += refuse("build-refuse-zero-window", "durée nulle", "desk", req("tvA", window = 0), "tvA")
        cases += refuse("build-refuse-scope", "la clé serveur ne peut pas délivrer « tout ouvert »", "server", req("tvA", rights = listOf(openAll)), "tvA")
        cases += refuse("build-refuse-open-all-long", "« tout ouvert » de 31 jours", "desk", req("tvA", rights = listOf(Right.OpenAll("ouvert", t0, t0 + 31 * day))), "tvA")
        cases += refuse("build-refuse-no-rights", "production sans droit", "desk", req("tvA", rights = emptyList()), "tvA")
        cases += J("type" to "build-activation", "id" to "build-refuse-bad-code", "description" to "code d'appareil mal formé", "signer" to "desk",
            "request" to reqJson(req("tvA"), "tvA") + mapOf("deviceCodeOverride" to "ABCD-EFGH-JKMN-PQRZ"), "expect" to J("refused" to true))
        cases += licenceCases()
        return J("format" to "castbridge-activation-test-vectors-v1", "warning" to "CLÉS DE TEST UNIQUEMENT : ces graines sont dérivées de textes publics et ne valent rien ; ne jamais les utiliser ailleurs que dans les tests",
            "nowMs" to t0, "compactEpochMs" to CompactActivation.EPOCH_MS,
            "keys" to keys.map { J("name" to it.name, "seed" to hex(seedOf(it.name)), "publicKey" to it.signer.publicKeyBase64, "kid" to it.signer.keyId, "scopes" to it.scopes.map { s -> s.name }.sorted()) },
            "devices" to devices.map { J("name" to it.name, "raw" to rawJson(it.raw), "code" to it.code, "fingerprints" to it.fp.byKind.mapKeys { e -> e.key.name }) },
            "cases" to cases)
    }

    // ---- licences: events -> state (the three tools must apply the same accounting) ----
    private fun licenceCases(): List<Map<String, Any?>> {
        fun sg(k: String) = key(k).signer
        fun act(k: String, d: String, license: String, at: Long = t0, seat: String? = null, nonce: String = "00112233445566778899aabbccddeeff") =
            issuer(k).issue(req(d, license = license, issuedAt = at, notBefore = at - day, seat = seat, nonce = nonce)).activation
        val lic1 = LicenseEvent.license(sg("desk"), t0 - 10 * day, "lic-0001", 2, 2)
        fun evJson(e: LicenseEvent) = J("id" to e.id, "kid" to e.keyId, "text" to e.text, "signature" to e.signature)
        fun case(id: String, why: String, events: List<LicenseEvent>, plans: List<Triple<String, String, String>> = emptyList(), subjects: Map<String, Subject> = emptyMap()): Map<String, Any?> {
            val ring = ringOf(listOf("desk", "phone", "server"), emptyList())
            val st = LicenseBook.replay(events, ring)
            val planOut = plans.map { (lic, dev, subj) ->
                val p = LicenseBook.plan(st, lic, Subject.valueOf(subj.uppercase()), dev(dev).fp)
                J("license" to lic, "device" to dev, "subject" to subj, "expect" to when (p) {
                    is Plan.Reuse -> J("plan" to "reuse", "seat" to p.seat.seatId)
                    is Plan.NewSeat -> J("plan" to "new", "seat" to p.seatId, "left" to p.seatsLeftAfter)
                    is Plan.Refused -> J("plan" to "refused", "reason" to p.reason.name)
                })
            }
            return J("type" to "licence", "id" to id, "description" to why, "trustedKeys" to listOf("desk", "phone", "server"), "events" to events.map(::evJson), "plans" to planOut,
                "expect" to J("seats" to st.seats.values.map { "${it.license}|${it.seatId}" }.sorted(), "used" to st.licenses.keys.sorted().associateWith { st.usedSeats(it) },
                    "transfers" to st.transfers.size, "duplicates" to st.duplicates.size, "rejected" to st.rejected.map { it.second.name }.sorted(), "revokedSeats" to st.revocations.seats.toSortedMap(),
                    "warnings" to st.warnings.size))
        }
        val a1 = act("desk", "tvA", "lic-0001"); val a1b = act("phone", "tvA", "lic-0001", t0 + day, a1.seat, "ffeeddccbbaa99887766554433221100")
        val a2 = act("desk", "tvB-no-ethernet-usb-wifi", "lic-0001", t0 + 2 * day)
        val swapped = act("phone", "tvA-swapped-wifi", "lic-0001", t0 + day)
        val out = ArrayList<Map<String, Any?>>()
        out += case("lic-seat-reuse", "même matériel : le poste est réutilisé quel que soit l'outil (aussi avec un module remplacé), un autre matériel prend le second poste",
            listOf(lic1, LicenseEvent.issue(sg("desk"), a1), LicenseEvent.issue(sg("phone"), a1b)),
            listOf(Triple("lic-0001", "tvA", "tv"), Triple("lic-0001", "tvA-swapped-wifi", "tv"), Triple("lic-0001", "tvB-no-ethernet-usb-wifi", "tv"), Triple("lic-0001", "tvA", "phone"), Triple("nope", "tvA", "tv")))
        out += case("lic-no-seat-left", "licence à un poste : le second matériel est refusé, il faut un transfert",
            listOf(LicenseEvent.license(sg("desk"), t0 - 10 * day, "lic-0001", 1, 2), LicenseEvent.issue(sg("desk"), a1)), listOf(Triple("lic-0001", "tvB-no-ethernet-usb-wifi", "tv"), Triple("lic-0001", "tvA", "tv")))
        out += case("lic-duplicate-hardware", "deux outils ont émis le même matériel sous deux identifiants de poste : détecté, compté une fois, le plus ancien gardé",
            listOf(lic1, LicenseEvent.issue(sg("desk"), a1), LicenseEvent.issue(sg("phone"), swapped)))
        out += case("lic-over-issued", "plus de postes émis que prévu : signalé (avertissement), jamais caché", listOf(LicenseEvent.license(sg("desk"), t0 - 10 * day, "lic-0001", 1, 2), LicenseEvent.issue(sg("desk"), a1), LicenseEvent.issue(sg("desk"), a2)))
        val tr1 = LicenseEvent.transfer(sg("desk"), t0 + 5 * day, "lic-0001", a1.seat, dev("tvN1").fp, nonce = "a1a1a1a1a1a1a1a1")
        out += case("lic-transfer-ok", "transfert du poste vers un autre matériel : l'ancien poste est révoqué, le nouveau matériel retrouve le poste",
            listOf(lic1, LicenseEvent.issue(sg("desk"), a1), tr1), listOf(Triple("lic-0001", "tvN1", "tv"), Triple("lic-0001", "tvA", "tv")))
        val tr2 = LicenseEvent.transfer(sg("phone"), t0 + 6 * day, "lic-0001", a1.seat, dev("tvN2").fp, nonce = "a2a2a2a2a2a2a2a2")
        val tr3 = LicenseEvent.transfer(sg("desk"), t0 + 7 * day, "lic-0001", a1.seat, dev("tvN3").fp, nonce = "a3a3a3a3a3a3a3a3")
        out += case("lic-transfer-cap", "plafond de 2 transferts par an : le troisième est refusé", listOf(lic1, LicenseEvent.issue(sg("desk"), a1), tr1, tr2, tr3))
        val tr3late = LicenseEvent.transfer(sg("desk"), t0 + 400 * day, "lic-0001", a1.seat, dev("tvN3").fp, nonce = "a4a4a4a4a4a4a4a4")
        out += case("lic-transfer-next-year", "un an plus tard le transfert est de nouveau permis", listOf(lic1, LicenseEvent.issue(sg("desk"), a1), tr1, tr2, tr3late))
        out += case("lic-server-cannot-transfer", "la clé serveur n'a pas la portée transfer", listOf(lic1, LicenseEvent.issue(sg("desk"), a1), LicenseEvent.transfer(sg("server"), t0 + 5 * day, "lic-0001", a1.seat, dev("tvN1").fp, nonce = "b1b1b1b1b1b1b1b1")))
        val forged = LicenseEvent(lic1.keyId, lic1.text.replace("seats=2", "seats=200"), lic1.signature)
        out += case("lic-forged-and-unknown", "événement falsifié et événement d'une clé inconnue : refusés", listOf(forged, LicenseEvent.license(sg("rogue"), t0, "lic-evil", 99)))
        out += case("lic-revoke-seat", "révocation explicite d'un poste", listOf(lic1, LicenseEvent.issue(sg("desk"), a1), LicenseEvent.revokeSeat(sg("server"), t0 + 3 * day, "lic-0001", a1.seat)))
        return out
    }

    private fun rawJson(r: RawFactors) = J("flashSerial" to r.flashSerial, "flashCid" to r.flashCid, "ethernetMac" to r.ethernetMac, "wifiMac" to r.wifiMac, "wifiSysfsPath" to r.wifiSysfsPath, "systemSerial" to r.systemSerial, "bluetoothAddress" to r.bluetoothAddress)

    // ---- pretty JSON (stable, readable diffs) ----
    private fun pretty(v: Any?, indent: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.joinToString(",\n") { "$indent  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$indent  ")}" } + "\n$indent}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it !is Map<*, *> && it !is List<*> }) "[" + v.joinToString(", ") { pretty(it) } + "]" else "[\n" + v.joinToString(",\n") { "$indent  ${pretty(it, "$indent  ")}" } + "\n$indent]"
        else -> JsonLite.write(v)
    }

    private fun file(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "tools/activation").isDirectory && !File(d, "docs").isDirectory) d = d.parentFile
        return File(d ?: File("."), "tools/activation/test-vectors.json")
    }

    @Test fun vectorsAreUpToDate() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/test-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the format change is intended")
    }

    @Test fun thereAreAtLeastTwentyVectorsValidAndInvalid() {
        val cases = (JsonLite.obj(file().readText())["cases"] as List<*>).map { it as Map<*, *> }
        assertTrue(cases.size >= 20, "${cases.size} cases")
        val results = cases.mapNotNull { ((it["expect"] as? Map<*, *>)?.get("result") ?: ((it["steps"] as? List<*>)?.map { s -> ((s as Map<*, *>)["expect"] as Map<*, *>)["result"] })?.firstOrNull()) as? String }
        assertTrue(results.count { it == "accepted" || it == "ok" } >= 8 && results.count { it == "rejected" || it == "invalid" || it == "bad-group" } >= 12, results.groupingBy { it }.eachCount().toString())
    }

    /** A harness that only reads the JSON file, as another implementation would: every case must give the recorded result. */
    @Test fun everyCommittedVectorPassesTheVerifierAndTheIssuer() {
        val root = JsonLite.obj(file().readText())
        @Suppress("UNCHECKED_CAST") val ks = (root["keys"] as List<Map<String, Any?>>).associate { it["name"] as String to it }
        @Suppress("UNCHECKED_CAST") val ds = (root["devices"] as List<Map<String, Any?>>).associate { it["name"] as String to it }
        fun trusted(names: List<*>) = names.map { n -> val k = ks.getValue(n as String); TrustedKey(k["kid"] as String, k["publicKey"] as String, (k["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet()) }
        fun fp(name: String) = Fingerprints((ds.getValue(name)["fingerprints"] as Map<*, *>).entries.associate { FactorKind.valueOf(it.key as String) to it.value as String })
        fun signerOf(n: String) = Ed25519Signer(ks.getValue(n)["seed"].toString().chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        var checked = 0
        for (c in (root["cases"] as List<*>).map { @Suppress("UNCHECKED_CAST") (it as Map<String, Any?>) }) {
            val id = c["id"] as String; @Suppress("UNCHECKED_CAST") val expect = c["expect"] as? Map<String, Any?>
            when (c["type"]) {
                "activation" -> {
                    @Suppress("UNCHECKED_CAST") val seats = (c["revokedSeats"] as List<Map<String, Any?>>).associate { "${it["license"]}|${it["seat"]}" to (it["at"] as Number).toLong() }
                    val ring = KeyRing(trusted(c["trustedKeys"] as List<*>), (c["revokedKeys"] as List<*>).map { ks.getValue(it as String)["kid"] as String }.toSet())
                    val r = ActivationVerifier(ring, revocations = RevocationState(emptySet(), seats), expect = Subject.valueOf((c["expectSubject"] as String).uppercase())).verify(c["token"] as String, fp(c["device"] as String), (c["nowMs"] as Number).toLong())
                    if (expect!!["result"] == "accepted") { val a = assertIs<ActivationResult.Accepted>(r, id); assertEquals(expect["license"], a.activation.license, id); assertEquals(expect["seat"], a.activation.seat, id) }
                    else assertEquals(expect["reason"], assertIs<ActivationResult.Rejected>(r, id).reason.name, id)
                }
                "compact" -> {
                    val tk = trusted(c["trustedKeys"] as List<*>); val ring = KeyRing(tk, (c["revokedKeys"] as List<*>).map { ks.getValue(it as String)["kid"] as String }.toSet())
                    val r = CompactActivation.verify(c["text"] as String, ring, tk, c["deviceCode"] as String, (c["nowMs"] as Number).toLong())
                    if (expect!!["result"] == "accepted") assertIs<ActivationResult.Accepted>(r, id) else assertEquals(expect["reason"], assertIs<ActivationResult.Rejected>(r, id).reason.name, id)
                }
                "command" -> {
                    val book = ChallengeBook({ 5_000L }); book.restore((c["openChallenges"] as List<*>).map { it as String })
                    val ring = KeyRing(trusted(c["trustedKeys"] as List<*>), (c["revokedKeys"] as List<*>).map { ks.getValue(it as String)["kid"] as String }.toSet())
                    for (s in c["steps"] as List<*>) {
                        @Suppress("UNCHECKED_CAST") val step = s as Map<String, Any?>; @Suppress("UNCHECKED_CAST") val e = step["expect"] as Map<String, Any?>
                        val r = OwnerCommandVerifier(ring, book).verify(step["token"] as String, fp(c["device"] as String), TvClock(), (step["wallMs"] as Number).toLong())
                        if (e["result"] == "accepted") { val g = assertIs<CommandResult.Accepted>(r, id).grant; assertEquals((e["untilMs"] as Number).toLong(), g.untilMs, id) } else assertEquals(e["reason"], assertIs<CommandResult.Rejected>(r, id).reason.name, id)
                    }
                }
                "fingerprints" -> {
                    @Suppress("UNCHECKED_CAST") val raw = c["raw"] as Map<String, Any?>
                    val f = DeviceIdentity.fingerprints(RawFactors(raw["flashSerial"] as String?, raw["flashCid"] as String?, raw["ethernetMac"] as String?, raw["wifiMac"] as String?, raw["wifiSysfsPath"] as String?, raw["systemSerial"] as String?, raw["bluetoothAddress"] as String?))
                    assertEquals((expect!!["fingerprints"] as Map<*, *>).mapKeys { FactorKind.valueOf(it.key as String) }, f.byKind, id)
                    if (f.n > 0) assertEquals(expect["code"], DeviceCode.of(f), id)
                }
                "base32" -> assertEquals(c["expectText"], Base32C.encode((c["bytesHex"] as String).chunked(2).map { it.toInt(16).toByte() }.toByteArray()), id)
                "grouped" -> assertEquals(c["expectText"], GroupedText.encode((c["bytesHex"] as String).chunked(2).map { it.toInt(16).toByte() }.toByteArray()), id)
                "grouped-decode" -> {
                    val d = GroupedText.decode(c["text"] as String)
                    if (expect!!["result"] == "ok") assertEquals(expect["bytesHex"], (d as GroupedText.Decoded.Ok).bytes.joinToString("") { "%02x".format(it) }, id)
                    else assertEquals((expect["group"] as Number).toInt(), (d as GroupedText.Decoded.BadGroup).group, id)
                }
                "device-code-parse" -> {
                    val p = DeviceCode.parse(c["text"] as String)
                    if (expect!!["result"] == "ok") assertEquals(expect["code"], p, id) else assertNull(p, id)
                }
                "licence" -> {
                    @Suppress("UNCHECKED_CAST") val evs = (c["events"] as List<Map<String, Any?>>).map { LicenseEvent.fromMap(it) ?: LicenseEvent(it["kid"] as String, it["text"] as String, it["signature"] as String) }
                    val ring = KeyRing(trusted(c["trustedKeys"] as List<*>))
                    val st = LicenseBook.replay(evs, ring)
                    assertEquals(expect!!["seats"], st.seats.values.map { "${it.license}|${it.seatId}" }.sorted(), id)
                    assertEquals((expect["transfers"] as Number).toInt(), st.transfers.size, id); assertEquals((expect["duplicates"] as Number).toInt(), st.duplicates.size, id)
                    assertEquals(expect["rejected"], st.rejected.map { it.second.name }.sorted(), id)
                    for (p in c["plans"] as List<*>) {
                        @Suppress("UNCHECKED_CAST") val pm = p as Map<String, Any?>; @Suppress("UNCHECKED_CAST") val pe = pm["expect"] as Map<String, Any?>
                        val plan = LicenseBook.plan(st, pm["license"] as String, Subject.valueOf((pm["subject"] as String).uppercase()), fp(pm["device"] as String))
                        when (pe["plan"]) { "reuse" -> assertEquals(pe["seat"], (plan as Plan.Reuse).seat.seatId, id); "new" -> assertEquals(pe["seat"], (plan as Plan.NewSeat).seatId, id); else -> assertEquals(pe["reason"], (plan as Plan.Refused).reason.name, id) }
                    }
                }
                "build-activation" -> {
                    @Suppress("UNCHECKED_CAST") val rq = c["request"] as Map<String, Any?>; val dv = rq["device"] as String
                    val rights = (rq["rights"] as List<*>).map { Activation.parseRight(it as String) }
                    val issuer = ActivationIssuer(signerOf(c["signer"] as String), trusted(listOf(c["signer"])).single().scopes)
                    val request = ActivationIssuer.Request(ActivationKind.valueOf((rq["kind"] as String).uppercase()), (rq["deviceCodeOverride"] as String?) ?: ds.getValue(dv)["code"] as String, fp(dv), (rq["issuedAt"] as Number).toLong(),
                        Subject.valueOf((rq["subject"] as String).uppercase()), rights, rq["license"] as String, rq["seat"] as String?, (rq["notBefore"] as Number).toLong(), (rq["windowDays"] as Number).toInt(), rq["nonce"] as String?)
                    if (expect!!["refused"] == true) assertFailsWith<IssueException>(id) { issuer.issue(request) } else assertEquals(expect["token"], issuer.issue(request).token, id)
                }
                "build-compact" -> {
                    @Suppress("UNCHECKED_CAST") val rq = c["request"] as Map<String, Any?>
                    assertEquals(expect!!["text"], ActivationIssuer(signerOf(c["signer"] as String)).issueCompact(ActivationKind.valueOf((rq["kind"] as String).uppercase()), rq["deviceCode"] as String, (rq["notBeforeDay"] as Number).toInt(), (rq["windowDays"] as Number).toInt(), (rq["setId"] as Number).toInt()), id)
                }
                "build-command" -> {
                    @Suppress("UNCHECKED_CAST") val rq = c["request"] as Map<String, Any?>
                    assertEquals(expect!!["token"], ActivationIssuer(signerOf(c["signer"] as String)).issueCommand(Power.valueOf((rq["power"] as String).uppercase()), fp(rq["device"] as String), rq["challenge"] as String, (rq["days"] as Number).toInt()), id)
                }
                else -> fail("unknown vector type ${c["type"]}")
            }
            checked++
        }
        assertTrue(checked >= 20)
    }
}
