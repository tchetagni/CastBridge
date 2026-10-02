package castbridge.core.tokens

import castbridge.core.lots.InstallKey
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.*
import java.io.File
import java.nio.file.Files

/**
 * Rejoue tools/activation/tokens-vectors.json (`castbridge-tokens-vectors-v1`, conception W5 § 3.4 c et f) : bons de jetons (octets et refus), porte-jetons (lignes exactes, altérations, compaction),
 * politique, nombres en lettres. Mêmes entrées, mêmes octets : le miroir serveur (w5-05) rejoue le même fichier. Clés, appareils et installations du fichier sont des valeurs de TEST dérivées de textes
 * publics. Rend les échecs (vide = tout est bon).
 */
object TokenVectors {
    const val FORMAT = "castbridge-tokens-vectors-v1"
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    class Env(val keys: Map<String, Pair<Ed25519Signer, Set<KeyScope>>>, val devices: Map<String, Fingerprints>, val installs: Map<String, InstallKey>) {
        fun ring(names: List<String>, revoked: List<String>) = KeyRing(names.map { keys.getValue(it).let { (s, sc) -> s.trusted(sc) } }, revoked.map { keys.getValue(it).first.keyId }.toSet())
    }

    @Suppress("UNCHECKED_CAST")
    fun env(root: Map<String, Any?>): Env {
        val keys = (root["keys"] as List<Map<String, Any?>>).associate { k -> k.str("name")!! to (Ed25519Signer(unhex(k.str("seed")!!)) to (k["scopes"] as List<String>).map { KeyScope.valueOf(it) }.toSet()) }
        val devices = (root["devices"] as List<Map<String, Any?>>).associate { d ->
            val r = d["raw"] as Map<String, Any?>
            d.str("name")!! to DeviceIdentity.fingerprints(RawFactors(r.str("flashSerial"), r.str("flashCid"), r.str("ethernetMac"), r.str("wifiMac"), r.str("wifiSysfsPath"), r.str("systemSerial"), r.str("bluetoothAddress")))
        }
        val installs = (root["installs"] as List<Map<String, Any?>>).associate { it.str("name")!! to InstallKey.fromSeed(unhex(it.str("seed")!!)) }
        return Env(keys, devices, installs)
    }

    /** Les paramètres d'un bon dans le fichier ([req] : signer, device, install, license, grant, amount, expiry, fresh (facultatif), seq, nonce, issuedAt, notBefore, expiresAt) ; null si l'émission est refusée. */
    fun buildToken(e: Env, req: Map<String, Any?>): String? = try {
        val dev = e.devices.getValue(req.str("device")!!)
        val grant = TokenGrant(req.str("license")!!, req.long("grant")!!, req.long("amount")!!, e.installs.getValue(req.str("install")!!).pub, req.long("expiry") ?: 0L, req["fresh"] == true)
        TokenGrant.issue(e.keys.getValue(req.str("signer")!!).first, req.long("seq")!!, req.str("nonce")!!, req.long("issuedAt")!!, req.long("notBefore")!!, req.long("expiresAt")!!, Envelope.Target.Device(DeviceIdentity.kFor(dev.n), dev.byKind), grant)
    } catch (x: IllegalArgumentException) { null }

    @Suppress("UNCHECKED_CAST")
    fun run(json: String): List<String> {
        val root = JsonLite.obj(json)
        if (root.str("format") != FORMAT) return listOf("format inconnu")
        val e = env(root); val walletInstall = root.str("walletInstall")!!
        val fails = ArrayList<String>()
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                when (c.str("type")) {
                    "build-tokens" -> build(c, e)
                    "tokens" -> verify(c, e)
                    "wallet-key" -> if (hex(WalletKey.derive(e.installs.getValue(c.str("install")!!).priv)) == (c["expect"] as Map<String, Any?>).str("key")) null else "clé de porte-jetons différente"
                    "wallet" -> wallet(c, e, walletInstall)
                    "server" -> null       // rejoué par le miroir serveur (w5-08), sans objet pour le cœur
                    "policy" -> policy(c)
                    "french-numbers" -> if (FrenchNumbers.words(c.long("n")!!) == (c["expect"] as Map<String, Any?>).str("words")) null else "texte différent"
                    else -> "type de vecteur inconnu"
                }
            } catch (x: Exception) { "exception ${x::class.simpleName}: ${x.message}" }
            if (problem != null) fails += "$id : $problem"
        }
        return fails
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(c: Map<String, Any?>, e: Env): String? {
        val out = buildToken(e, c["request"] as Map<String, Any?>); val exp = c["expect"] as Map<String, Any?>
        return if (exp["refused"] == true) { if (out != null) "devait être refusé" else null } else if (out == null) "refusé à tort" else if (out != exp.str("token")) "jeton différent (octets)" else null
    }

    /** Vérifie un jeton comme le fait la TV ; rend le nom du résultat (`ACCEPTED` ou le motif). */
    @Suppress("UNCHECKED_CAST")
    private fun verifyName(r: Map<String, Any?>, e: Env, token: String, lastGrant: Long): String {
        val ring = e.ring(r["ring"] as List<String>, (r["revoked"] as? List<String>).orEmpty())
        return when (val x = TokenGrant.verify(token, ring, RevocationState(), e.devices.getValue(r.str("device")!!), e.installs.getValue(r.str("install")!!).pub, lastGrant, r.long("now")!!)) {
            is TokenGrantResult.Accepted -> "ACCEPTED"
            is TokenGrantResult.Rejected -> x.reason.name
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun verify(c: Map<String, Any?>, e: Env): String? {
        val r = c["request"] as Map<String, Any?>; val want = (c["expect"] as Map<String, Any?>).str("result")
        val got = verifyName(r, e, r.str("token")!!, r.long("lastGrant") ?: 0L)
        return if (got == want) null else "attendu $want, obtenu $got"
    }

    private fun policy(c: Map<String, Any?>): String? {
        val s = c["settings"] as? Map<*, *>
        val p = TokenPolicy(if (s == null) TokenSettings.DEFAULT else TokenSettings((s["secondChance"] as Number).toLong(), (s["extraJoker"] as Number).toLong(), (s["swapQuestion"] as Number).toLong()))
        @Suppress("UNCHECKED_CAST") val exp = c["expect"] as Map<String, Any?>
        val balance = c.long("balance")!!
        for (item in TokenItem.values()) {
            val x = exp[item.wire] as Map<String, Any?>
            if (p.cost(item) != x.long("cost") || p.maxPerGame(item).toLong() != x.long("max") || p.label(item) != x.str("label") || p.confirmText(item, balance) != x.str("confirm")) return "politique différente pour ${item.wire}"
        }
        return null
    }

    private fun spendName(r: SpendResult): String = when (r) {
        is SpendResult.Ok -> if (r.replayed) "REPLAY" else "OK"
        is SpendResult.Insufficient -> "INSUFFICIENT"
        is SpendResult.Unreadable -> "UNREADABLE"
        SpendResult.WriteFailed -> "WRITE_FAILED"
        SpendResult.OpConflict -> "CONFLICT"
        SpendResult.Invalid -> "INVALID"
    }

    /** Altère le fichier [f] comme un attaquant : `flipMac`, `dropLine`, `truncateTail` (supprime les [line] dernières lignes), `swapLines`. */
    private fun tamper(f: File, kind: String, line: Int) {
        val lines = f.readText().trimEnd('\n').split('\n').toMutableList()
        when (kind) {
            "flipMac" -> lines[line] = lines[line].dropLast(1) + (if (lines[line].last() == '0') '1' else '0')
            "dropLine" -> lines.removeAt(line)
            "truncateTail" -> repeat(line) { lines.removeAt(lines.lastIndex) }
            "empty" -> lines.clear()
            "swapLines" -> { val t = lines[line]; lines[line] = lines[line + 1]; lines[line + 1] = t }
            else -> error("altération inconnue")
        }
        f.writeText(lines.joinToString("\n") + "\n")
    }

    /** Rejoue les étapes d'un vecteur `wallet` ; [onEnd] reçoit le fichier et le porte-jetons à la fin (le générateur y relève les lignes exactes). Rend l'échec ou null. */
    @Suppress("UNCHECKED_CAST")
    fun wallet(c: Map<String, Any?>, e: Env, walletInstall: String, onEnd: ((File, TokenWallet) -> Unit)? = null): String? {
        val dir = Files.createTempDirectory("tokens-vectors").toFile()
        try {
            val file = File(dir, "wallet.txt")
            val keys = mapOf("main" to WalletKey.derive(e.installs.getValue(walletInstall).priv), "other" to WalletKey.derive(ByteArray(32) { 7 }))
            var key = "main"
            val markFile = File(dir, "wallet.mark"); val installId = e.installs.getValue(walletInstall).installId
            val provider = WalletKeyProvider { keys.getValue(key) }
            val marks = FileWalletMark(markFile, provider) { installId }
            fun firstGrantFp(): String? = runCatching { file.readText().lines().firstNotNullOfOrNull { l -> if (l.startsWith("grant=")) l.split('|')[2] else if (l.startsWith("granted=")) l.split('|')[2] else null } }.getOrNull()
            fun open() = TokenWallet(file, provider, marks, c.long("compactAbove")?.toInt() ?: TokenWallet.COMPACT_ABOVE, c.long("keepRecent")?.toInt() ?: TokenWallet.KEEP_RECENT, c.long("keepWindowMs") ?: TokenWallet.KEEP_WINDOW_MS)
            var w = open()
            var snapshot: String? = null; var copy: String? = null
            for ((i, s) in (c["steps"] as List<Map<String, Any?>>).withIndex()) {
                val at = "étape $i (${s.str("do")})"
                when (s.str("do")) {
                    "credit" -> {
                        val token = s.str("token")!!
                        val acc = TokenGrant.verify(token, e.ring(s["ring"] as List<String>, emptyList()), RevocationState(), e.devices.getValue(s.str("device")!!), e.installs.getValue(s.str("install")!!).pub, w.lastGrant(), s.long("now")!!)
                        val got = when (acc) { is TokenGrantResult.Rejected -> acc.reason.name; is TokenGrantResult.Accepted -> w.credit(acc.grant, acc.fingerprint, s.long("now")!!).name }
                        if (got != s.str("expect")) return "$at : attendu ${s.str("expect")}, obtenu $got"
                    }
                    "spend" -> {
                        val r = w.spend(s.str("item")!!, s.long("cost")!!, s.long("at")!!, s.str("op")!!); val got = spendName(r)
                        if (got != s.str("expect")) return "$at : attendu ${s.str("expect")}, obtenu $got"
                        s.long("balance")?.let { b -> val real = if (r is SpendResult.Ok) r.balance else if (r is SpendResult.Insufficient) r.balance else w.balance(); if (real != b) return "$at : solde $real au lieu de $b" }
                    }
                    "ack" -> if (w.ack(s.long("seq")!!) != (s["expect"] as Boolean)) return "$at : accusé inattendu"
                    "tamper" -> tamper(file, s.str("kind")!!, s.long("line")!!.toInt())
                    "restart" -> { key = s.str("key") ?: "main"; w = open() }
                    "apply" -> {
                        val ex = s["expect"] as Map<String, Any?>
                        val ring = e.ring(s["ring"] as List<String>, emptyList()); val dev = e.devices.getValue(s.str("device")!!); val pub = e.installs.getValue(s.str("install")!!).pub
                        val a = TokenSync.apply(TokenSync.Reply(0, s["tokens"] as List<String>, 0, true, null), w, { t -> (TokenGrant.verify(t, ring, RevocationState(), dev, pub, w.lastGrant(), s.long("now")!!) as? TokenGrantResult.Accepted)?.grant }, s.long("now")!!)
                        if (a.reopened != ex["reopened"] || a.credited.toLong() != ex.long("credited") || a.rejected.toLong() != (ex.long("rejected") ?: 0L)) return "$at : reopened=${a.reopened} credited=${a.credited} rejected=${a.rejected}"
                    }
                    "deleteFile" -> { file.delete(); SafeFile.bak(file).delete() }
                    "markDelete" -> markFile.delete()
                    "markWrite" -> { val k = keys.getValue(key); markFile.parentFile.mkdirs(); val body = listOf(FileWalletMark.MAGIC, "install=${s.str("installId")}", "opened=${s.str("fp")}|${s.long("chain")}|${s.long("at") ?: 0L}")
                        markFile.writeText((body + "mac=${TokenWallet.mac(k, body.joinToString("\n"))}").joinToString("\n") + "\n") }
                    "markFlipMac" -> markFile.writeText(markFile.readText().trimEnd('\n').let { it.dropLast(1) + (if (it.last() == '0') '1' else '0') } + "\n")
                    "checkMark" -> {
                        val m = marks.read(); if ((m != null) != (s["present"] as Boolean)) return "$at : marque ${if (m != null) "présente" else "absente"}"
                        if (m != null) {
                            if (s["matchesFile"] == true && m.openedFp != firstGrantFp()) return "$at : la marque ne correspond pas au fichier"
                            s.long("chain")?.let { if (m.chain.toLong() != it) return "$at : chaîne ${m.chain} au lieu de $it" }
                            s.str("openedFp")?.let { if (m.openedFp != it) return "$at : opened ${m.openedFp}" }
                        }
                    }
                    "copySave" -> copy = file.readText()
                    "copyRestore" -> file.writeText(copy!!)
                    "snapshot" -> snapshot = file.readText()
                    "expectUnchanged" -> if (!file.isFile || file.readText() != snapshot) return "$at : le fichier a changé"
                    "plantBroken" -> for (n in (s["indices"] as List<Number>)) { File(dir, "wallet.txt.broken-$n").writeText("old-$n\n"); File(dir, "wallet.txt.bak.broken-$n").writeText("old-bak-$n\n") }
                    "failWrites" -> File(dir, "wallet.txt.tmp").mkdirs()
                    "allowWrites" -> File(dir, "wallet.txt.tmp").deleteRecursively()
                    "expectFile" -> {
                        val f = File(dir, s.str("name")!!); val want = s["exists"] as Boolean
                        if (f.exists() != want) return "$at : ${f.name} ${if (f.exists()) "existe" else "absent"}"
                        s.str("startsWith")?.let { if (!f.readText().startsWith(it)) return "$at : ${f.name} ne commence pas par $it" }
                    }
                    "check" -> {
                        s.str("cause")?.let { if (w.cause().wire != it) return "$at : cause ${w.cause().wire} au lieu de $it" }
                        if (w.state().name != s.str("state")) return "$at : état ${w.state()} au lieu de ${s.str("state")}"
                        if (w.balance() != s.long("balance")) return "$at : solde ${w.balance()} au lieu de ${s.long("balance")}"
                        s["spentTotal"]?.let { if (w.summary().spentTotal != (it as Number).toLong()) return "$at : total dépensé ${w.summary().spentTotal}" }
                        s["clockNote"]?.let { if (w.summary().clockNote != it) return "$at : note d'horloge ${w.summary().clockNote}" }
                    }
                    else -> return "$at : étape inconnue"
                }
            }
            onEnd?.invoke(file, w)
            (c["expectLines"] as? List<String>)?.let { if (file.readText().trimEnd('\n').split('\n') != it) return "lignes du fichier différentes" }
            (c["expectReport"] as? List<String>)?.let { if (w.report(c.long("reportSince") ?: 0L)?.render()?.trimEnd('\n')?.split('\n') != it) return "rapport différent" }
            return null
        } finally { dir.deleteRecursively() }
    }
}
