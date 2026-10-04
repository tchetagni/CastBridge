package castbridge.core.wallet

import castbridge.core.owner.Ed25519Signer
import castbridge.core.quiz.Json
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Vecteurs communs Kotlin ↔ Java (`tools/wallet/wallet-vectors.json`, `castbridge-wallet-vectors-v1`) : clés de TEST déterministes (graines fixes, publiques, sans valeur), pièces signées
 * DORÉES (acceptées) et pièces FAUSSES avec leur motif de refus ([WalletRefusal], noms stables). Le côté Java (cahier w22-01) relit CE fichier ; il n'est jamais édité à la main :
 * `WALLET_VECTORS_WRITE=1` le régénère, et le test échoue s'il diffère de ce que le code produit (dérive détectée).
 */
class WalletVectorsTest {
    private val file = File(System.getProperty("wallet.vectors") ?: "../../tools/wallet/wallet-vectors.json")
    private val now = WalletTestKeys.NOW
    private val tv = WalletTestKeys.TV
    private val dev = WalletTestKeys.DEVICE_CODE
    private val mine = Voucher.targetOf(dev)!!
    private val wallet = WalletTestKeys.wallet

    private fun map(vararg p: Pair<String, Any?>) = linkedMapOf(*p)
    private fun snap(id: String = tv, n: Long = 3450, nb: Long = 0, m: Long = 12, mb: Long = 0, seq: Long = 7, ed: String = "PROD", frozen: Boolean = false) =
        Snapshot(wallet.keyId, id, ed, n, nb, m, mb, seq, now, Snapshot.Flags(frozen, true, true))
    private fun esc(amt: Long = 40, k: Int = 2, iat: Long = now, exp: Long = now + 600_000, cur: WalletCurrency = WalletCurrency.MBOKO) =
        EscrowTicket(wallet.keyId, "AAAAAAAAAAAAAAAAAAAAAA", tv, cur, 20, k, amt, iat, exp)
    private val lines = listOf(PlayResult.Line("AAAAAAAAAAAAAAAAAAAAAA", "tv-a", 20, 30), PlayResult.Line("BBBBBBBBBBBBBBBBBBBBBB", "tv-b", 20, 10))
    private fun res(l: List<PlayResult.Line> = lines, kind: PlayResult.Kind = PlayResult.Kind.END, rid: String = "0123456789abcdef0123456789abcdef") =
        PlayResult(WalletTestKeys.result.keyId, rid, "ROOM1", "quiz", WalletCurrency.NDEM, 10, kind, now, l)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun text(v: Voucher) = VoucherCode.encode(v)

    private fun key(s: Ed25519Signer, role: String) = map("role" to role, "seedHex" to hex(seedOf(s)), "publicKeyBase64" to s.publicKeyBase64, "kid" to s.keyId)
    private fun seedOf(s: Ed25519Signer) = when (s) { WalletTestKeys.wallet -> WalletTestKeys.walletSeed; WalletTestKeys.result -> WalletTestKeys.resultSeed; WalletTestKeys.voucher -> WalletTestKeys.voucherSeed; else -> WalletTestKeys.strangerSeed }

    private fun build(): String {
        val goldenW = listOf(
            map("name" to "snapshot-prod", "token" to TestMint.snapshot(snap()), "expect" to map("id" to tv, "ed" to "PROD", "n" to 3450, "nb" to 0, "m" to 12, "mb" to 0, "seq" to 7, "at" to now)),
            map("name" to "snapshot-frozen-with-blocked", "token" to TestMint.snapshot(snap(nb = 40, mb = 5, seq = 8, frozen = true)), "expect" to map("id" to tv, "ed" to "PROD", "n" to 3450, "nb" to 40, "m" to 12, "mb" to 5, "seq" to 8, "at" to now)),
            map("name" to "snapshot-max-amount", "token" to TestMint.snapshot(snap(n = 1_000_000_000_000L, seq = 0)), "expect" to map("id" to tv, "ed" to "PROD", "n" to 1_000_000_000_000L, "nb" to 0, "m" to 12, "mb" to 0, "seq" to 0, "at" to now)),
        )
        val compact = Json.write(snap().payload())
        val forgedPayload = String(Base64.getUrlDecoder().decode(TestMint.snapshot(snap()).split('.')[1])).replace("\"n\":3450", "\"n\":9450")
        val t = TestMint.snapshot(snap()).split('.')
        val refusedW = listOf(
            map("name" to "tampered-payload", "token" to "${t[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.toByteArray())}.${t[2]}", "expect" to "BAD_SIGNATURE"),
            map("name" to "wrong-domain", "token" to TestMint.snapshot(snap(), domain = EscrowTicket.DOMAIN), "expect" to "BAD_SIGNATURE"),
            map("name" to "no-domain", "token" to TestMint.snapshot(snap(), domain = ""), "expect" to "BAD_SIGNATURE"),
            map("name" to "unknown-key", "token" to TestMint.snapshot(snap().copy(kid = WalletTestKeys.stranger.keyId), WalletTestKeys.stranger), "expect" to "UNKNOWN_KEY"),
            map("name" to "other-tv", "token" to TestMint.snapshot(snap(id = "tv-autre")), "expect" to "OTHER_TV"),
            map("name" to "negative-amount", "token" to TestMint.snapshot(snap(n = -1)), "expect" to "OUT_OF_BOUNDS"),
            map("name" to "amount-above-1e12", "token" to TestMint.snapshot(snap(m = 1_000_000_000_001L)), "expect" to "OUT_OF_BOUNDS"),
            map("name" to "negative-seq", "token" to TestMint.snapshot(snap(seq = -1)), "expect" to "OUT_OF_BOUNDS"),
            map("name" to "extra-field", "token" to TestMint.raw("cbw1", Snapshot.DOMAIN, compact.dropLast(1) + ",\"extra\":1}", wallet), "expect" to "UNREADABLE"),
            map("name" to "duplicate-key", "token" to TestMint.raw("cbw1", Snapshot.DOMAIN, compact.dropLast(1) + ",\"seq\":99}", wallet), "expect" to "UNREADABLE"),
            map("name" to "whitespace-in-payload", "token" to TestMint.raw("cbw1", Snapshot.DOMAIN, compact.replace(",", ", "), wallet), "expect" to "UNREADABLE"),
            map("name" to "leading-zero-number", "token" to TestMint.raw("cbw1", Snapshot.DOMAIN, compact.replace("\"seq\":7", "\"seq\":07"), wallet), "expect" to "UNREADABLE"),
            map("name" to "decimal-number", "token" to TestMint.raw("cbw1", Snapshot.DOMAIN, compact.replace("\"seq\":7", "\"seq\":7.0"), wallet), "expect" to "UNREADABLE"),
            map("name" to "wrong-prefix-cbp1", "token" to TestMint.token("cbp1", "castbridge-play-ticket-v1", snap().payload(), wallet), "expect" to "UNREADABLE"),
            map("name" to "garbage", "token" to "cbw1.!!.!!", "expect" to "UNREADABLE"),
        )
        val goldenE = listOf(
            map("name" to "escrow-mboko-2-seats", "token" to TestMint.escrow(esc()), "expect" to map("eid" to "AAAAAAAAAAAAAAAAAAAAAA", "id" to tv, "cur" to "MBOKO", "per" to 20, "k" to 2, "amt" to 40, "iat" to now, "exp" to now + 600_000)),
            map("name" to "escrow-ndem-max-life", "token" to TestMint.escrow(esc(exp = now + 1_800_000, cur = WalletCurrency.NDEM)), "expect" to map("eid" to "AAAAAAAAAAAAAAAAAAAAAA", "id" to tv, "cur" to "NDEM", "per" to 20, "k" to 2, "amt" to 40, "iat" to now, "exp" to now + 1_800_000)),
        )
        val refusedE = listOf(
            map("name" to "amount-not-per-times-k", "token" to TestMint.escrow(esc(amt = 41)), "expect" to "AMOUNT_MISMATCH"),
            map("name" to "life-over-30-minutes", "token" to TestMint.escrow(esc(exp = now + 1_800_001)), "expect" to "TOO_LONG_LIFE"),
            map("name" to "expired", "token" to TestMint.escrow(esc(iat = now - 700_000, exp = now - 100_000)), "expect" to "EXPIRED"),
            map("name" to "k-above-8", "token" to TestMint.escrow(esc(k = 9, amt = 180)), "expect" to "OUT_OF_BOUNDS"),
            map("name" to "signed-by-result-key", "token" to TestMint.escrow(esc().copy(kid = WalletTestKeys.result.keyId), WalletTestKeys.result), "expect" to "UNKNOWN_KEY"),
            map("name" to "wrong-audience", "token" to TestMint.raw("cbe1", EscrowTicket.DOMAIN, Json.write(esc().payload()).replace("castbridge-play", "castbridge-autre"), wallet), "expect" to "WRONG_AUDIENCE"),
        )
        val goldenR = listOf(
            map("name" to "result-end", "token" to PlayResult.sign(res(), WalletTestKeys.result), "expect" to map("rid" to "0123456789abcdef0123456789abcdef", "kind" to "END", "cur" to "NDEM", "per" to 10, "lines" to lines.map { listOf(it.eid, it.id, it.used, it.pay) })),
            map("name" to "result-abort", "token" to PlayResult.sign(res(lines.map { it.copy(used = 0, pay = 0) }, PlayResult.Kind.ABORT, "fedcba9876543210fedcba9876543210"), WalletTestKeys.result),
                "expect" to map("rid" to "fedcba9876543210fedcba9876543210", "kind" to "ABORT", "cur" to "NDEM", "per" to 10, "lines" to lines.map { listOf(it.eid, it.id, 0, 0) })),
        )
        val refusedR = listOf(
            map("name" to "sum-pay-not-sum-used", "token" to TestMint.result(res(lines.map { it.copy(pay = it.pay + 1) })), "expect" to "BAD_TOTALS"),
            map("name" to "duplicate-eid", "token" to TestMint.result(res(listOf(lines[0], lines[0].copy(pay = 20)))), "expect" to "BAD_TOTALS"),
            map("name" to "abort-moves-value", "token" to TestMint.result(res(lines, PlayResult.Kind.ABORT)), "expect" to "BAD_TOTALS"),
            map("name" to "more-than-16-lines", "token" to TestMint.result(res(List(17) { PlayResult.Line("%022d".format(it), "tv-a", 1, 1) })), "expect" to "OUT_OF_BOUNDS"),
            map("name" to "signed-by-wallet-key", "token" to TestMint.result(res().copy(kid = wallet.keyId), wallet), "expect" to "UNKNOWN_KEY"),
        )
        fun vg(name: String, v: Voucher) = map("name" to name, "text" to text(v), "hex" to hex(v.encode()), "expect" to map("currency" to v.currency.name, "amount" to v.amount, "nonceHex" to v.nonceHex, "expiryDay" to v.expiryDay, "targetHex" to v.targetHex))
        fun vr(name: String, v: Voucher, why: String, device: String = dev) = map("name" to name, "text" to text(v), "deviceCode" to device, "expect" to why)
        val goldenV = listOf(
            vg("ndem-any-tv", TestMint.voucher()),
            vg("ndem-targeted", TestMint.voucher(amount = 20_000, nonceHex = "a1a2a3a4a5a6a7a8a9aa", targetHex = mine)),
            vg("mboko-targeted", TestMint.voucher(cur = WalletCurrency.MBOKO, amount = 50, nonceHex = "b1b2b3b4b5b6b7b8b9ba", targetHex = mine)),
        )
        val badSig = TestMint.voucher().encode().also { it[6] = (it[6] + 1).toByte() }
        val refusedV = listOf(
            vr("mboko-any-tv", TestMint.voucher(cur = WalletCurrency.MBOKO, amount = 10), "VOUCHER_BAD"),
            vr("expired", TestMint.voucher(day = 200), "VOUCHER_EXPIRED"),
            vr("other-tv", TestMint.voucher(targetHex = mine), "VOUCHER_OTHER_TV", WalletTestKeys.OTHER_DEVICE_CODE),
            map("name" to "amount-changed-after-signing", "text" to text(Voucher.decode(badSig)!!), "deviceCode" to dev, "expect" to "VOUCHER_BAD"),
            vr("signed-by-stranger", TestMint.voucher(signer = WalletTestKeys.stranger), "VOUCHER_BAD"),
            vr("version-2", TestMint.voucher(version = 2), "VOUCHER_BAD"),
            vr("unknown-key-index", TestMint.voucher(keyIndex = 2), "VOUCHER_BAD"),
            vr("zero-amount", TestMint.voucher(amount = 0), "VOUCHER_BAD"),
        )
        val goodText = text(TestMint.voucher()).split('-').toMutableList().also { it[6] = it[6][0].toString() + (if (it[6][1] == 'Z') 'Y' else 'Z') + it[6].substring(2) }.joinToString("-")
        val typos = listOf(map("name" to "typo-in-group-7", "text" to goodText, "expectBadGroup" to 7))
        val root = map(
            "format" to "castbridge-wallet-vectors-v1",
            "note" to "CLÉS DE TEST SEULEMENT : graines fixes et publiques, sans aucune valeur. Fichier généré par WalletVectorsTest (WALLET_VECTORS_WRITE=1), ne pas éditer à la main.",
            "encoding" to map(
                "token" to "PREFIX.PAYLOAD.SIGNATURE ; PAYLOAD = base64url sans bourrage de la charge JSON compacte ; SIGNATURE = base64url sans bourrage de la signature Ed25519 (64 octets) sur ASCII(DOMAINE + LF + PREFIX + point + PAYLOAD)",
                "json" to "objet plat compact (aucun espace), ensemble de clés exact, entiers sans zéro de tête ni décimale, aucune clé en double ; relire = réécrire la charge identique octet pour octet",
                "voucher" to "91 octets : version, index de clé, monnaie (1 NDEM, 2 MBOKO), montant u32 gros-boutiste, nonce 10 o, échéance u16 (jours depuis 2026-01-01 UTC, valable jusqu'à la fin de ce jour), cible 8 o (0 = toute TV, sinon SHA-256(code d'appareil normalisé XXXX-XXXX-XXXX-XXXX en UTF-8)[0..8]), signature 64 o sur ASCII(\"castbridge-wallet-voucher-v1\\n\") + les 27 premiers octets ; texte = Crockford en groupes de 4 + 1 contrôle (Base32C.check, sel = rang du groupe, 1-based), 37 groupes, tirets",
                "voucherExpiryDay0" to "2026-01-01",
            ),
            "keys" to listOf(key(wallet, "wallet"), key(WalletTestKeys.result, "result"), key(WalletTestKeys.voucher, "voucher"), key(WalletTestKeys.stranger, "stranger-not-trusted")),
            "voucherKeyIndex" to map("1" to WalletTestKeys.voucher.keyId),
            "domains" to map("cbw1" to Snapshot.DOMAIN, "cbe1" to EscrowTicket.DOMAIN, "cbr1" to PlayResult.DOMAIN, "cbv1" to Voucher.DOMAIN),
            "context" to map("nowMs" to now, "expectedId" to tv, "deviceCode" to dev, "otherDeviceCode" to WalletTestKeys.OTHER_DEVICE_CODE),
            "golden" to map("cbw1" to goldenW, "cbe1" to goldenE, "cbr1" to goldenR, "cbv1" to goldenV),
            "refused" to map("cbw1" to refusedW, "cbe1" to refusedE, "cbr1" to refusedR, "cbv1" to refusedV, "cbv1-typed" to typos),
        )
        return Json.write(root).replace("},{", "},\n{").replace("],\"", "],\n\"").replace("}],", "}],\n") + "\n"
    }

    @Test fun fileIsExactlyWhatTheCodeProduces() {
        val expected = build()
        if (System.getenv("WALLET_VECTORS_WRITE") == "1") { file.parentFile.mkdirs(); file.writeText(expected) }
        assertTrue(file.isFile, "vecteurs absents : ${file.absolutePath} (WALLET_VECTORS_WRITE=1 pour les générer)")
        assertEquals(expected, file.readText(), "dérive : regénérer avec WALLET_VECTORS_WRITE=1")
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun everyGoldenPieceIsAcceptedAndEveryFakeIsRefusedWithItsReason() {
        val root = Json.obj(file.readText())
        assertEquals("castbridge-wallet-vectors-v1", root["format"])
        val ctx = root["context"] as Map<String, Any?>
        val nowMs = ctx["nowMs"] as Long; val id = ctx["expectedId"] as String; val device = ctx["deviceCode"] as String
        val golden = root["golden"] as Map<String, List<Map<String, Any?>>>; val refused = root["refused"] as Map<String, List<Map<String, Any?>>>
        // anneaux reconstruits DU FICHIER (clés publiques seulement) : on ne réutilise pas les objets du générateur
        val keys = (root["keys"] as List<Map<String, Any?>>).associateBy { it["role"] as String }
        fun ring(role: String) = castbridge.core.owner.KeyRing(listOf(castbridge.core.owner.TrustedKey(keys[role]!!["kid"] as String, keys[role]!!["publicKeyBase64"] as String)))
        val vk = VoucherKeys(ring("voucher"), (root["voucherKeyIndex"] as Map<String, String>).mapKeys { it.key.toInt() })
        val walletRing = ring("wallet"); val resultRing = ring("result")
        var checked = 0

        for (g in golden["cbw1"]!!) {
            val v = Snapshot.verify(g["token"] as String, walletRing, id); val e = g["expect"] as Map<String, Any?>
            val s = (v as? Verdict.Accepted)?.value ?: fail("cbw1 doré refusé : ${g["name"]} → $v")
            assertEquals(listOf(e["n"], e["nb"], e["m"], e["mb"], e["seq"], e["at"], e["ed"], e["id"]), listOf(s.n, s.nb, s.m, s.mb, s.seq, s.at, s.ed, s.id), g["name"] as String); checked++
        }
        for (g in golden["cbe1"]!!) {
            val s = (EscrowTicket.verify(g["token"] as String, walletRing, nowMs) as? Verdict.Accepted)?.value ?: fail("cbe1 doré refusé : ${g["name"]}"); val e = g["expect"] as Map<String, Any?>
            assertEquals(listOf(e["eid"], e["id"], e["cur"], e["per"], e["k"], e["amt"], e["iat"], e["exp"]), listOf(s.eid, s.id, s.cur.name, s.per, s.k.toLong(), s.amt, s.iat, s.exp), g["name"] as String); checked++
        }
        for (g in golden["cbr1"]!!) {
            val s = (PlayResult.verify(g["token"] as String, resultRing) as? Verdict.Accepted)?.value ?: fail("cbr1 doré refusé : ${g["name"]}"); val e = g["expect"] as Map<String, Any?>
            assertEquals(listOf(e["rid"], e["kind"], e["cur"], e["per"], e["lines"]), listOf(s.rid, s.kind.name, s.cur.name, s.per, s.lines.map { listOf(it.eid, it.id, it.used, it.pay) }), g["name"] as String); checked++
        }
        for (g in golden["cbv1"]!!) {
            val d = VoucherCode.decode(g["text"] as String) as? VoucherCode.Decoded.Ok ?: fail("cbv1 illisible : ${g["name"]}"); val e = g["expect"] as Map<String, Any?>
            assertEquals(g["hex"], hex(d.voucher.encode()), g["name"] as String)
            assertTrue(Voucher.verify(d.voucher, vk, device, nowMs) is Verdict.Accepted, "cbv1 doré refusé : ${g["name"]}")
            assertEquals(listOf(e["currency"], e["amount"], e["nonceHex"], e["expiryDay"].let { (it as Long).toInt() }, e["targetHex"]), listOf(d.voucher.currency.name, d.voucher.amount, d.voucher.nonceHex, d.voucher.expiryDay, d.voucher.targetHex)); checked++
        }
        fun reason(v: Verdict<*>) = (v as? Verdict.Rejected)?.reason?.name ?: "ACCEPTED"
        for (r in refused["cbw1"]!!) { assertEquals(r["expect"], reason(Snapshot.verify(r["token"] as String, walletRing, id)), "cbw1 faux : ${r["name"]}"); checked++ }
        for (r in refused["cbe1"]!!) { assertEquals(r["expect"], reason(EscrowTicket.verify(r["token"] as String, walletRing, nowMs)), "cbe1 faux : ${r["name"]}"); checked++ }
        for (r in refused["cbr1"]!!) { assertEquals(r["expect"], reason(PlayResult.verify(r["token"] as String, resultRing)), "cbr1 faux : ${r["name"]}"); checked++ }
        for (r in refused["cbv1"]!!) {
            val d = VoucherCode.decode(r["text"] as String) as? VoucherCode.Decoded.Ok ?: fail("cbv1 faux illisible : ${r["name"]}")
            assertEquals(r["expect"], reason(Voucher.verify(d.voucher, vk, r["deviceCode"] as String, nowMs)), "cbv1 faux : ${r["name"]}"); checked++
        }
        for (r in refused["cbv1-typed"]!!) { assertEquals(VoucherCode.Decoded.BadGroup((r["expectBadGroup"] as Long).toInt()), VoucherCode.decode(r["text"] as String)); checked++ }
        assertTrue(checked >= 40, "trop peu de vecteurs : $checked")
    }
}
