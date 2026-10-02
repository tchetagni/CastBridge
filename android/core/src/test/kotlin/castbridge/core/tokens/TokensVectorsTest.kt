package castbridge.core.tokens

import castbridge.core.lots.InstallKey
import castbridge.core.net.JsonLite
import castbridge.core.owner.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/**
 * tools/activation/tokens-vectors.json (`castbridge-tokens-vectors-v1`, conception W5 § 3.4 c et f). Les RÉSULTATS attendus (motifs de refus, soldes, textes, nombres en lettres) sont écrits À LA MAIN
 * ici ; seuls les octets (jetons signés, lignes chaînées du fichier) sont ceux que produit la bibliothèque. Régénérer après un changement voulu :
 * `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*TokensVectorsTest*'`. Clés, appareils et installations sont des valeurs de TEST dérivées de textes publics (mêmes que rental-vectors-v2.json).
 */
class TokensVectorsTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|rental|$n".toByteArray())
    private fun installSeed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-install|$n".toByteArray())
    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val keyDefs = listOf("desk" to KeyScope.ALL, "server" to setOf(KeyScope.ISSUE_PRODUCTION, KeyScope.REACTIVATE, KeyScope.REGISTRY), "noprod" to setOf(KeyScope.COMMAND_SUPPORT))
    private val devDefs = listOf(
        "tvA" to mapOf("flashSerial" to "FLASHSERIAL-R1", "flashCid" to "cid-r1", "ethernetMac" to "AA:BB:CC:00:22:01", "wifiMac" to "10:20:30:40:60:01", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0001", "bluetoothAddress" to "11:22:33:44:66:01"),
        "tvB" to mapOf("flashSerial" to "FLASHSERIAL-R2", "flashCid" to "cid-r2", "ethernetMac" to "AA:BB:CC:00:22:02", "wifiMac" to "10:20:30:40:60:02", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0002", "bluetoothAddress" to "11:22:33:44:66:02"),
    )
    private val installNames = listOf("tvA-install-1", "tvA-install-2", "tvB-install-1")

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun file() = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile; File(d ?: it, "tools/activation/tokens-vectors.json") }

    private fun skeleton(): Map<String, Any?> = J("format" to TokenVectors.FORMAT,
        "warning" to "CLÉS, APPAREILS ET INSTALLATIONS DE TEST dérivés de textes publics : ne protègent rien. Voir docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md § 3.4 et § 6.",
        "walletInstall" to "tvA-install-1",
        "keys" to keyDefs.map { (n, sc) -> J("name" to n, "seed" to hex(seed(n)), "scopes" to sc.map { it.name }.sorted()) },
        "devices" to devDefs.map { (n, raw) -> J("name" to n, "raw" to raw) },
        "installs" to installNames.map { J("name" to it, "seed" to hex(installSeed(it))) },
        "cases" to emptyList<Any?>())

    private val env get() = TokenVectors.env(skeleton())

    private val hour = 3600L * 1000
    private fun req(grant: Long, amount: Long = 20, signer: String = "server", device: String = "tvA", install: String = "tvA-install-1", license: String = "lic-w5", expiry: Long = 0,
                    notBefore: Long = t0, expiresAt: Long = t0 + 2 * day, fresh: Boolean = false) =
        J("signer" to signer, "device" to device, "install" to install, "license" to license, "grant" to grant, "amount" to amount, "expiry" to expiry, "fresh" to (if (fresh) true else null), "seq" to grant,
            "nonce" to "%016x".format(grant), "issuedAt" to t0, "notBefore" to notBefore, "expiresAt" to expiresAt)

    private fun token(r: Map<String, Any?>) = TokenVectors.buildToken(env, r)!!

    /** Un jeton `tokens` dont le corps est hors schéma, mais correctement signé (que l'émetteur normal refuserait). */
    private fun rawToken(body: List<String>, expiresAt: Long = t0 + day, anyTarget: Boolean = false): String {
        val s = env.keys.getValue("server").first; val dev = env.devices.getValue("tvA")
        val target = if (anyTarget) Envelope.Target.Any else Envelope.Target.Device(DeviceIdentity.kFor(dev.n), dev.byKind)
        val unsigned = Envelope("tokens", s.keyId, 9, "00000000000000aa", t0, t0, expiresAt, target, body, "")
        return unsigned.withSignature(java.util.Base64.getEncoder().encodeToString(s.sign(unsigned.canonicalPayload().toByteArray()))).encode()
    }

    private fun generate(): Map<String, Any?> {
        val cases = ArrayList<Map<String, Any?>>()
        val e = env
        fun build(id: String, r: Map<String, Any?>, refused: Boolean = false) {
            cases += J("id" to id, "type" to "build-tokens", "request" to r, "expect" to if (refused) J("refused" to true) else J("token" to token(r)))
        }
        build("build-tokens-welcome-20", req(1))
        build("build-tokens-60-with-expiry", req(2, 60, expiry = t0 + 90 * day))
        build("build-tokens-opening-voucher-20", req(10, fresh = true))
        build("build-tokens-ordinary-window-72-hours-exact", req(8, expiresAt = t0 + 72 * hour))
        build("build-tokens-opening-window-48-hours-exact", req(9, fresh = true, expiresAt = t0 + 48 * hour))
        build("build-tokens-refuse-window-over-72-hours", req(3, expiresAt = t0 + 72 * hour + 1), refused = true)
        build("build-tokens-refuse-opening-window-over-48-hours", req(11, fresh = true, expiresAt = t0 + 48 * hour + 1), refused = true)
        build("build-tokens-refuse-amount-zero", req(4, 0), refused = true)

        fun verify(id: String, token: String, want: String, device: String = "tvA", install: String = "tvA-install-1", ring: List<String> = listOf("server"), revoked: List<String> = emptyList(), now: Long = t0 + day, lastGrant: Long = 0) {
            val r = J("token" to token, "device" to device, "install" to install, "ring" to ring, "revoked" to revoked, "now" to now, "lastGrant" to lastGrant)
            cases += J("id" to id, "type" to "tokens", "request" to r, "expect" to J("result" to want))
        }
        val t1 = token(req(1))
        verify("tokens-accepted", t1, "ACCEPTED")
        verify("tokens-other-installation-is-bad-grant", t1, "BAD_GRANT", install = "tvA-install-2")
        verify("tokens-other-tv-is-wrong-target", t1, "WRONG_TARGET", device = "tvB")
        verify("tokens-old-grant-is-stale", t1, "STALE_SEQUENCE", lastGrant = 1)
        verify("tokens-key-without-production-scope", token(req(5, signer = "noprod")), "KEY_NOT_ALLOWED", ring = listOf("server", "noprod"))
        verify("tokens-window-closed", t1, "WINDOW_CLOSED", now = t0 + 8 * day)
        verify("tokens-not-yet-valid", token(req(6, notBefore = t0 + 2 * day, expiresAt = t0 + 3 * day)), "NOT_YET_VALID", now = t0)
        verify("tokens-unknown-key", t1, "UNKNOWN_KEY", ring = emptyList())
        verify("tokens-revoked-key", t1, "REVOKED_KEY", revoked = listOf("server"))
        val parts = t1.split('.')
        verify("tokens-altered-signature", parts[0] + "." + parts[1] + "." + (if (parts[2][0] == 'A') 'B' else 'A') + parts[2].drop(1), "BAD_SIGNATURE")
        verify("tokens-malformed", "cbx1.xx.yy", "MALFORMED")
        verify("tokens-not-a-token-type", Orders.issue(e.keys.getValue("server").first, 1, "00000000000000bb", t0, t0, t0 + day, Envelope.Target.Any, "noop"), "UNKNOWN_TYPE")
        verify("tokens-amount-over-limit", rawToken(TokenGrant("lic-w5", 9, 10_001, e.installs.getValue("tvA-install-1").pub, 0).body()), "BAD_GRANT")
        val okBody = TokenGrant("lic-w5", 9, 20, e.installs.getValue("tvA-install-1").pub, 0).body()
        verify("tokens-signed-window-over-72-hours-is-bad-grant", rawToken(okBody, expiresAt = t0 + 72 * hour + 1), "BAD_GRANT")
        verify("tokens-signed-window-72-hours-exact-is-accepted", rawToken(okBody, expiresAt = t0 + 72 * hour), "ACCEPTED")
        val openBody = TokenGrant("lic-w5", 9, 20, e.installs.getValue("tvA-install-1").pub, 0, true).body()
        verify("tokens-opening-voucher-accepted", token(req(12, fresh = true)), "ACCEPTED")
        verify("tokens-signed-opening-window-over-48-hours-is-bad-grant", rawToken(openBody, expiresAt = t0 + 48 * hour + 1), "BAD_GRANT")
        verify("tokens-signed-opening-window-48-hours-exact-is-accepted", rawToken(openBody, expiresAt = t0 + 48 * hour), "ACCEPTED")
        verify("tokens-old-five-line-body-is-bad-grant", rawToken(okBody.dropLast(1)), "BAD_GRANT")
        verify("tokens-fresh-flag-must-be-0-or-1", rawToken(okBody.dropLast(1) + "fresh=2"), "BAD_GRANT")
        verify("tokens-signed-for-any-device-is-wrong-target", rawToken(okBody, anyTarget = true), "WRONG_TARGET")
        verify("tokens-grant-expiry-passed", token(req(7, expiry = t0 + day / 2)), "WINDOW_CLOSED", now = t0 + day)
        verify("tokens-non-canonical-body", rawToken(TokenGrant("lic-w5", 9, 20, e.installs.getValue("tvA-install-1").pub, 0).body().map { it.replace("amount=20", "amount=020") }), "BAD_GRANT")

        cases += J("id" to "wallet-key-derivation", "type" to "wallet-key", "install" to "tvA-install-1", "expect" to J("key" to hex(WalletKey.derive(e.installs.getValue("tvA-install-1").priv))))

        fun credit(grant: Long, expect: String, amount: Long = 20, license: String = "lic-w5", install: String = "tvA-install-1", now: Long = t0 + day, fresh: Boolean = false) =
            J("do" to "credit", "token" to token(req(grant, amount, license = license, install = install, fresh = fresh)), "device" to "tvA", "install" to install, "ring" to listOf("server"), "now" to now, "expect" to expect)
        /** Un bon d'ouverture (`fresh=1`) sur un porte-jetons vide ou illisible : rouvre la chaîne. */
        fun open(grant: Long, amount: Long = 20) = credit(grant, "REOPENED", amount, fresh = true)
        fun checkC(state: String, cause: String, balance: Long) = J("do" to "check", "state" to state, "cause" to cause, "balance" to balance)
        fun file(name: String, exists: Boolean, startsWith: String? = null) = J("do" to "expectFile", "name" to name, "exists" to exists, "startsWith" to startsWith)
        fun mark(present: Boolean, matchesFile: Boolean? = null, chain: Long? = null) = J("do" to "checkMark", "present" to present, "matchesFile" to matchesFile, "chain" to chain)
        fun step(d: String, vararg p: Pair<String, Any?>) = J("do" to d, *p)
        val head = "castbridge-token-wallet-v1"
        fun spend(item: String, cost: Long, op: String, expect: String, balance: Long? = null, at: Long = t0 + 1000) = J("do" to "spend", "item" to item, "cost" to cost, "at" to at, "op" to op, "expect" to expect, "balance" to balance)
        fun check(state: String, balance: Long, spentTotal: Long? = null, clockNote: String? = null) = J("do" to "check", "state" to state, "balance" to balance, "spentTotal" to spentTotal, "clockNote" to clockNote)
        fun tamper(kind: String, line: Int) = J("do" to "tamper", "kind" to kind, "line" to line)
        fun restart(key: String = "main") = J("do" to "restart", "key" to key)

        /** Ajoute un vecteur `wallet` : les lignes exactes du fichier et le rapport sont relevés sur la bibliothèque ; tout le reste est écrit à la main. */
        fun wallet(id: String, steps: List<Map<String, Any?>>, compactAbove: Int? = null, keepRecent: Int? = null, keepWindowMs: Long? = null, capture: Boolean = false, reportSince: Long = 0) {
            val c = J("id" to id, "type" to "wallet", "compactAbove" to compactAbove, "keepRecent" to keepRecent, "keepWindowMs" to keepWindowMs, "steps" to steps)
            var lines: List<String>? = null; var report: List<String>? = null
            assertNull(TokenVectors.wallet(c, e, "tvA-install-1") { f, w -> if (capture) { lines = f.readText().trimEnd('\n').split('\n'); report = w.report(reportSince)!!.render().trimEnd('\n').split('\n') } }, id)
            cases += if (capture) c + J("reportSince" to reportSince, "expectLines" to lines, "expectReport" to report) else c
        }
        wallet("wallet-credit-spend-replay-conflict", listOf(
            open(1), check("OK", 20, 0),
            spend("second-chance", 5, "quiz:g1:second-chance:1", "OK", 15),
            spend("second-chance", 5, "quiz:g1:second-chance:1", "REPLAY", 15),
            spend("extra-joker", 2, "quiz:g1:extra-joker:1", "OK", 13),
            spend("extra-joker", 9, "quiz:g1:extra-joker:1", "CONFLICT"),
            spend("swap-question", 100, "quiz:g1:swap-question:1", "INSUFFICIENT", 13),
            spend("swap-question", 0, "quiz:g1:swap-question:2", "INVALID"),
            credit(1, "STALE_SEQUENCE"), check("OK", 13, 7), restart(), check("OK", 13, 7),
            spend("swap-question", 3, "quiz:g1:swap-question:1", "OK", 10),
        ), capture = true, reportSince = 1)
        wallet("wallet-never-negative", listOf(open(1, 5), spend("second-chance", 3, "quiz:g:a:1", "OK", 2), spend("second-chance", 3, "quiz:g:a:2", "INSUFFICIENT", 2), spend("extra-joker", 2, "quiz:g:b:1", "OK", 0), spend("extra-joker", 1, "quiz:g:b:2", "INSUFFICIENT", 0)))
        wallet("wallet-two-grants-increasing-only", listOf(open(1, 10), credit(3, "OK", 30), credit(2, "STALE_SEQUENCE", 99), check("OK", 40)))
        wallet("wallet-other-license-refused", listOf(open(1), credit(2, "WRONG_LICENSE", license = "lic-autre"), check("OK", 20)))
        wallet("wallet-altered-mac", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", 15), tamper("flipMac", 4), restart(), check("UNREADABLE", 0), spend("extra-joker", 2, "quiz:g:b:1", "UNREADABLE")))
        wallet("wallet-altered-grant-line", listOf(open(1), tamper("flipMac", 3), restart(), check("UNREADABLE", 0), credit(2, "NEEDS_FRESH")))
        wallet("wallet-head-line-removed", listOf(open(1), tamper("dropLine", 1), restart(), check("UNREADABLE", 0)))
        wallet("wallet-middle-spend-removed", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK"), spend("extra-joker", 2, "quiz:g:b:1", "OK"), spend("swap-question", 3, "quiz:g:c:1", "OK"), tamper("dropLine", 5), restart(), check("UNREADABLE", 0)))
        wallet("wallet-lines-swapped", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK"), spend("extra-joker", 2, "quiz:g:b:1", "OK"), tamper("swapLines", 4), restart(), check("UNREADABLE", 0)))
        wallet("wallet-copied-to-another-installation", listOf(open(1), restart("other"), check("UNREADABLE", 0), spend("second-chance", 5, "quiz:g:a:1", "UNREADABLE")))
        wallet("wallet-tail-truncation-not-detectable-locally", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", 15), tamper("truncateTail", 1), restart(), check("OK", 20, 0)))
        wallet("wallet-clock-set-back-is-noted", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", at = 9_000), spend("extra-joker", 2, "quiz:g:b:1", "OK", at = 1_000), check("OK", 13, 7, "Horloge reculée constatée entre deux dépenses")))
        wallet("wallet-compaction-keeps-balance-and-sequence", listOf(
            open(1, 100), spend("second-chance", 5, "quiz:g:a:1", "OK"), spend("extra-joker", 2, "quiz:g:b:1", "OK"), spend("extra-joker", 2, "quiz:g:b:2", "OK"), spend("swap-question", 3, "quiz:g:c:1", "OK"), spend("second-chance", 5, "quiz:h:a:1", "OK"),
            J("do" to "ack", "seq" to 99, "expect" to false), J("do" to "ack", "seq" to 3, "expect" to true),
            check("OK", 83, 17), restart(), check("OK", 83, 17), spend("swap-question", 3, "quiz:h:c:1", "OK", 80), check("OK", 80, 20)), compactAbove = 6, keepRecent = 1, keepWindowMs = 0, capture = true, reportSince = 3)
        // the last acked spends stay (keepRecent 2, spends 1 s apart, window 0): a retried operation after compaction is still a REPLAY, not a new debit
        wallet("wallet-replay-after-compaction", listOf(
            open(1, 100), spend("second-chance", 5, "quiz:g:a:1", "OK", at = t0 + 1000), spend("extra-joker", 2, "quiz:g:b:1", "OK", at = t0 + 2000), spend("extra-joker", 2, "quiz:g:b:2", "OK", at = t0 + 3000),
            spend("swap-question", 3, "quiz:g:c:1", "OK", at = t0 + 4000), spend("second-chance", 5, "quiz:h:a:1", "OK", at = t0 + 5000),
            J("do" to "ack", "seq" to 3, "expect" to true), check("OK", 83, 17), restart(),
            spend("swap-question", 3, "quiz:g:c:1", "REPLAY", 83), spend("second-chance", 5, "quiz:h:a:1", "REPLAY", 83), check("OK", 83, 17),
            spend("swap-question", 3, "quiz:h:c:1", "OK", 80, at = t0 + 6000)), compactAbove = 6, keepRecent = 2, keepWindowMs = 0, capture = true)
        // grant lines are folded into granted=<last>|<sum>: the file does not grow with the number of vouchers, the numbering still refuses an old voucher
        wallet("wallet-compaction-folds-grants", listOf(
            open(1, 10), credit(2, "OK", 20), credit(3, "OK", 30), spend("second-chance", 5, "quiz:g:a:1", "OK", at = t0 + 1000), spend("extra-joker", 2, "quiz:g:b:1", "OK", at = t0 + 2000),
            J("do" to "ack", "seq" to 1, "expect" to true), check("OK", 53, 7), restart(), check("OK", 53, 7),
            credit(3, "STALE_SEQUENCE"), credit(4, "OK", 40), check("OK", 93, 7), restart(), check("OK", 93, 7)), compactAbove = 4, keepRecent = 1, keepWindowMs = 0, capture = true)
        wallet("wallet-empty-file-is-unreadable", listOf(open(1), tamper("empty", 0), restart(), check("UNREADABLE", 0), credit(2, "NEEDS_FRESH"), spend("second-chance", 5, "quiz:g:a:1", "UNREADABLE")))

        // --- addendum W5 porte-jetons : bon d'ouverture, marque d'existence, reprise (D-W5-J1, J2, J5) ---
        wallet("wallet-opening-voucher-reopens-an-empty-wallet", listOf(checkC("EMPTY", "none", 0), open(1), check("OK", 20, 0), mark(true, true, 1)), capture = true)
        wallet("wallet-ordinary-voucher-on-empty-needs-fresh", listOf(credit(1, "NEEDS_FRESH"), check("EMPTY", 0), file("wallet.txt", false), mark(false)))
        wallet("wallet-opening-voucher-on-a-healthy-wallet-is-refused", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", 15), step("snapshot"), credit(2, "FRESH_REFUSED", 50, fresh = true), step("expectUnchanged"),
            check("OK", 15, 5), file("wallet.txt.broken-1", false), mark(true, true, 1)))
        wallet("wallet-erased-file-is-lost-then-reopened", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", 15), step("deleteFile"), restart(), checkC("UNREADABLE", "lost", 0),
            spend("extra-joker", 2, "quiz:g:b:1", "UNREADABLE"), credit(2, "NEEDS_FRESH", 30), open(3, 30), checkC("OK", "none", 30), check("OK", 30, 0), file("wallet.txt.broken-1", false), mark(true, true, 2),
            restart(), check("OK", 30, 0)), capture = true)
        wallet("wallet-broken-file-is-moved-aside-on-reopen", listOf(open(1), spend("second-chance", 5, "quiz:g:a:1", "OK", 15), tamper("flipMac", 4), restart(), checkC("UNREADABLE", "broken", 0),
            open(2, 30), check("OK", 30, 0), file("wallet.txt.broken-1", true, head), file("wallet.txt.bak.broken-1", true, head), file("wallet.txt.broken-new", false), mark(true, true, 2)))
        wallet("wallet-restored-earlier-chain-is-foreign", listOf(open(1), step("copySave"), step("deleteFile"), restart(), checkC("UNREADABLE", "lost", 0), open(3, 40), step("copyRestore"), restart(),
            checkC("UNREADABLE", "foreign_chain", 0), spend("second-chance", 5, "quiz:g:a:1", "UNREADABLE"), credit(4, "NEEDS_FRESH", 10), open(5, 15), checkC("OK", "none", 15), file("wallet.txt.broken-1", true, head)))
        wallet("wallet-forged-mark-mac-is-ignored-and-recreated", listOf(open(1), step("markFlipMac"), restart(), checkC("OK", "none", 20), mark(true, true, 1)))
        wallet("wallet-missing-mark-with-a-valid-file-is-recreated", listOf(open(1), step("markDelete"), restart(), checkC("OK", "none", 20), mark(true, true, 1)))
        wallet("wallet-mark-of-another-installation-is-ignored", listOf(step("markWrite", "installId" to "00000000000000ee", "fp" to "0123456789abcdef", "chain" to 1, "at" to 0), mark(false), checkC("EMPTY", "none", 0), credit(1, "NEEDS_FRESH")))
        wallet("wallet-keeps-five-broken-files-and-erases-the-oldest", listOf(step("plantBroken", "indices" to listOf(1, 2, 3, 4, 5)), open(1), tamper("flipMac", 3), restart(), checkC("UNREADABLE", "broken", 0), open(2, 30),
            check("OK", 30, 0), file("wallet.txt.broken-1", true, "old-2"), file("wallet.txt.broken-4", true, "old-5"), file("wallet.txt.broken-5", true, head), file("wallet.txt.broken-6", false),
            file("wallet.txt.bak.broken-1", true, "old-bak-2"), file("wallet.txt.bak.broken-4", true, "old-bak-5")))
        wallet("wallet-failed-reopen-puts-the-old-file-back", listOf(open(1), tamper("flipMac", 3), restart(), step("snapshot"), step("failWrites"), credit(2, "WRITE_FAILED", 30, fresh = true), step("expectUnchanged"),
            checkC("UNREADABLE", "broken", 0), file("wallet.txt.broken-1", false), file("wallet.txt.broken-new", false), mark(true, false, 1), step("allowWrites"), open(2, 30), checkC("OK", "none", 30), file("wallet.txt.broken-1", true, head)))
        wallet("wallet-sync-apply-opens-first-then-ordinary-by-number", listOf(
            J("do" to "apply", "tokens" to listOf(token(req(3, 30)), token(req(2, 10)), token(req(1, 20, fresh = true))), "device" to "tvA", "install" to "tvA-install-1", "ring" to listOf("server"), "now" to t0 + day,
                "expect" to J("reopened" to true, "credited" to 2, "rejected" to 0)), check("OK", 60, 0),
            J("do" to "apply", "tokens" to listOf(token(req(3, 30)), token(req(1, 20, fresh = true))), "device" to "tvA", "install" to "tvA-install-1", "ring" to listOf("server"), "now" to t0 + day,
                "expect" to J("reopened" to false, "credited" to 0, "rejected" to 2)), check("OK", 60, 0)))
        // server-side vectors (rejoués par w5-08, sans objet pour le cœur : le type « server » est ignoré par TokenVectors)
        cases += J("id" to "server-report-below-chain-grant-seq-is-chain-replay", "type" to "server", "scope" to "w5-08", "chainGrantSeq" to 5, "report" to J("lastGrant" to 3, "lastSpendSeq" to 2),
            "expect" to J("anomaly" to "TOKEN_CHAIN_REPLAY", "spendsRecorded" to false, "offlineAllowed" to false))
        cases += J("id" to "server-first-report-after-recovery-is-not-a-replay", "type" to "server", "scope" to "w5-08", "chainGrantSeq" to 5, "lastSeqSeen" to 0, "report" to J("lastGrant" to 5, "lastSpendSeq" to 1),
            "expect" to J("anomaly" to null, "spendsRecorded" to true, "offlineAllowed" to true))
        cases += J("id" to "server-wallet-absent-after-delivery-is-wallet-lost", "type" to "server", "scope" to "w5-08", "deliveredGrants" to 1, "walletState" to "absent", "walletCause" to "lost",
            "expect" to J("anomaly" to "TOKEN_WALLET_LOST"))

        fun policy(id: String, settings: Map<String, Any?>?, balance: Long, vararg x: Triple<String, Map<String, Any?>, Unit>) {
            cases += J("id" to id, "type" to "policy", "settings" to settings, "balance" to balance, "expect" to linkedMapOf<String, Any?>().also { m -> x.forEach { m[it.first] = it.second } })
        }
        fun item(w: String, cost: Long, max: Long, label: String, confirm: String) = Triple(w, J("cost" to cost, "max" to max, "label" to label, "confirm" to confirm), Unit)
        policy("policy-default-costs", null, 23,
            item("second-chance", 5, 1, "Seconde chance", "Utiliser 5 jetons (cinq) pour « Seconde chance » ? Solde : 23 jetons"),
            item("extra-joker", 2, 2, "Joker en plus", "Utiliser 2 jetons (deux) pour « Joker en plus » ? Solde : 23 jetons"),
            item("swap-question", 3, 1, "Changer de question", "Utiliser 3 jetons (trois) pour « Changer de question » ? Solde : 23 jetons"))
        policy("policy-grid-costs", J("secondChance" to 1, "extraJoker" to 10, "swapQuestion" to 71), 1,
            item("second-chance", 1, 1, "Seconde chance", "Utiliser 1 jeton (un) pour « Seconde chance » ? Solde : 1 jeton"),
            item("extra-joker", 10, 2, "Joker en plus", "Utiliser 10 jetons (dix) pour « Joker en plus » ? Solde : 1 jeton"),
            item("swap-question", 71, 1, "Changer de question", "Utiliser 71 jetons (soixante et onze) pour « Changer de question » ? Solde : 1 jeton"))

        for ((n, w) in listOf(0 to "zéro", 5 to "cinq", 21 to "vingt et un", 71 to "soixante et onze", 80 to "quatre-vingts", 81 to "quatre-vingt-un", 91 to "quatre-vingt-onze", 99 to "quatre-vingt-dix-neuf",
            100 to "cent", 200 to "deux cents", 201 to "deux cent un", 1000 to "mille", 2080 to "deux mille quatre-vingts", 10000 to "dix mille")) {
            cases += J("id" to "french-numbers-$n", "type" to "french-numbers", "n" to n, "expect" to J("words" to w))
        }
        return skeleton() + mapOf("cases" to cases)
    }

    private fun pretty(v: Any?, ind: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.filter { it.value != null }.joinToString(",\n") { "$ind  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$ind  ")}" } + "\n$ind}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it is String || it is Number }) v.joinToString(", ", "[", "]") { pretty(it) } else "[\n" + v.joinToString(",\n") { "$ind  ${pretty(it, "$ind  ")}" } + "\n$ind]"
        else -> JsonLite.write(v)
    }

    @Test fun committedFileMatchesTheCodeAndEveryVectorReplays() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/tokens-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the token vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the change is intended")
        assertEquals(emptyList(), TokenVectors.run(f.readText()))
        assertTrue((JsonLite.obj(f.readText())["cases"] as List<*>).size >= 20)
    }

    @Test fun aTamperedVectorIsDetected() {
        val text = pretty(generate())
        val tok = Regex("\"token\": \"(cbx1\\.[^\"]+)\"").find(text)!!.groupValues[1]
        assertTrue(TokenVectors.run(text.replace(tok, tok.dropLast(3) + "AA=")).isNotEmpty(), "a changed token must fail the replay")
        assertTrue(TokenVectors.run(text.replace("\"soixante et onze\"", "\"soixante-onze\"")).isNotEmpty())
        assertTrue(TokenVectors.run(text.replace("castbridge-tokens-vectors-v1", "autre")).isNotEmpty())
    }
}
