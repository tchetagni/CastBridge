package castbridge.core.wallet

import castbridge.core.owner.Base32C
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.owner.TrustedKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CLÉS DE TEST SEULEMENT (graines fixes, publiques, sans valeur) : elles servent aux tests et aux vecteurs communs Kotlin ↔ Java
 * (`tools/wallet/wallet-vectors.json`). Aucune clé de production n'est ici ni ne doit l'être.
 */
internal object WalletTestKeys {
    private fun seed(b: Int) = ByteArray(32) { b.toByte() }
    val walletSeed = seed(0x11); val resultSeed = seed(0x22); val voucherSeed = seed(0x33); val strangerSeed = seed(0x44)
    val wallet = Ed25519Signer(walletSeed); val result = Ed25519Signer(resultSeed); val voucher = Ed25519Signer(voucherSeed); val stranger = Ed25519Signer(strangerSeed)
    fun ring(vararg s: Ed25519Signer) = KeyRing(s.map { TrustedKey(it.keyId, it.publicKeyBase64) })
    val walletRing = ring(wallet); val resultRing = ring(result)
    val voucherKeys = VoucherKeys(ring(voucher), mapOf(1 to voucher.keyId))
    const val TV = "tv-1234567890ab"
    const val NOW = 1_790_000_000_000L   // 2026-09-20 (jour 262 depuis 2026-01-01)
    private fun code(body: String) = (body + Base32C.check(body)).chunked(4).joinToString("-")
    val DEVICE_CODE = code("7K3M9PQ2XH4TV8R")
    val OTHER_DEVICE_CODE = code("2B5D8FGH1JKMNPQ")
}

/** Fabrique de pièces signées POUR LES TESTS seulement : le code de production ne sait signer que `cbr1` (service de jeu). */
internal object TestMint {
    fun token(prefix: String, domain: String, payload: Map<String, Any?>, signer: Ed25519Signer) = WalletFormats.seal(prefix, domain, payload) { signer.sign(it) }

    /** Pièce dont la charge est un TEXTE donné tel quel (espaces, clés en double…), correctement signée. */
    fun raw(prefix: String, domain: String, payloadText: String, signer: Ed25519Signer): String {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadText.toByteArray(Charsets.UTF_8))
        return "$prefix.$b64." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign(WalletFormats.signedText(domain, prefix, b64)))
    }

    fun snapshot(s: Snapshot, signer: Ed25519Signer = WalletTestKeys.wallet, domain: String = Snapshot.DOMAIN) = token(Snapshot.PREFIX, domain, s.payload(), signer)
    fun escrow(e: EscrowTicket, signer: Ed25519Signer = WalletTestKeys.wallet) = token(EscrowTicket.PREFIX, EscrowTicket.DOMAIN, e.payload(), signer)

    /** Résultat signé SANS contrôle de cohérence (pour fabriquer des pièces fausses authentiques). */
    fun result(r: PlayResult, signer: Ed25519Signer = WalletTestKeys.result) = token(PlayResult.PREFIX, PlayResult.DOMAIN, r.payload(), signer)

    fun voucher(signer: Ed25519Signer = WalletTestKeys.voucher, cur: WalletCurrency = WalletCurrency.NDEM, amount: Long = 500, nonceHex: String = "0102030405060708090a", day: Int = 364,
                targetHex: String = Voucher.ANY_TARGET, version: Int = 1, keyIndex: Int = 1): Voucher {
        val body = Voucher.bodyOf(version, keyIndex, cur, amount, nonceHex, day, targetHex)
        return Voucher(version, keyIndex, cur, amount, nonceHex, day, targetHex, signer.sign(Voucher.signedMessage(body)))
    }
}

class WalletFormatsTest {
    private val ring = WalletTestKeys.walletRing
    private val now = WalletTestKeys.NOW

    private fun snapshot(id: String = WalletTestKeys.TV, seq: Long = 7, n: Long = 3450) =
        Snapshot(WalletTestKeys.wallet.keyId, id, "PROD", n, 0, 12, 0, seq, now, Snapshot.Flags(false, true, true))

    private fun escrow(amt: Long = 40, k: Int = 2, iat: Long = now, exp: Long = now + 600_000, per: Long = 20) =
        EscrowTicket(WalletTestKeys.wallet.keyId, "AAAAAAAAAAAAAAAAAAAAAA", WalletTestKeys.TV, WalletCurrency.MBOKO, per, k, amt, iat, exp)

    private fun result(lines: List<PlayResult.Line>, kind: PlayResult.Kind = PlayResult.Kind.END) =
        PlayResult(WalletTestKeys.result.keyId, "0123456789abcdef0123456789abcdef", "ROOM1", "quiz", WalletCurrency.NDEM, 10, kind, now, lines)

    private val goodLines = listOf(PlayResult.Line("AAAAAAAAAAAAAAAAAAAAAA", "tv-a", 20, 30), PlayResult.Line("BBBBBBBBBBBBBBBBBBBBBB", "tv-b", 20, 10))

    @Test fun snapshotRoundTripIsAccepted() {
        val token = TestMint.snapshot(snapshot())
        assertTrue(token.startsWith("cbw1."))
        val v = Snapshot.verify(token, ring, WalletTestKeys.TV)
        assertTrue(v is Verdict.Accepted && v.value.n == 3450L && v.value.m == 12L && v.value.flags.stakesM)
    }

    @Test fun snapshotOfAnotherIdentityIsRefused() {
        assertEquals(Verdict.Rejected(WalletRefusal.OTHER_TV), Snapshot.verify(TestMint.snapshot(snapshot(id = "tv-autre")), ring, WalletTestKeys.TV))
    }

    @Test fun domainSeparationIsEnforced() {
        // MUTATION : un instantané signé avec le domaine d'un autre format (ou sans domaine) ne vaut rien
        assertEquals(Verdict.Rejected(WalletRefusal.BAD_SIGNATURE), Snapshot.verify(TestMint.snapshot(snapshot(), domain = EscrowTicket.DOMAIN), ring, WalletTestKeys.TV))
        assertEquals(Verdict.Rejected(WalletRefusal.BAD_SIGNATURE), Snapshot.verify(TestMint.snapshot(snapshot(), domain = ""), ring, WalletTestKeys.TV))
        // le ticket de jeu cbp1 (autre domaine, autre préfixe) ne peut pas être présenté comme un instantané
        val p = TestMint.token("cbp1", "castbridge-play-ticket-v1", snapshot().payload(), WalletTestKeys.wallet)
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), Snapshot.verify(p, ring, WalletTestKeys.TV))
    }

    @Test fun snapshotBoundsAndTamperingAreRefused() {
        fun v(s: Snapshot) = Snapshot.verify(TestMint.snapshot(s), ring, WalletTestKeys.TV)
        assertEquals(Verdict.Rejected(WalletRefusal.OUT_OF_BOUNDS), v(snapshot(n = -1)))
        assertEquals(Verdict.Rejected(WalletRefusal.OUT_OF_BOUNDS), v(snapshot(n = 1_000_000_000_001L)))
        assertEquals(Verdict.Rejected(WalletRefusal.OUT_OF_BOUNDS), v(snapshot(seq = -1)))
        assertTrue(v(snapshot(n = 1_000_000_000_000L)) is Verdict.Accepted)
        // charge modifiée après signature
        val t = TestMint.snapshot(snapshot()).split('.')
        val forged = Base64.getUrlEncoder().withoutPadding().encodeToString(String(Base64.getUrlDecoder().decode(t[1])).replace("\"n\":3450", "\"n\":9450").toByteArray())
        assertEquals(Verdict.Rejected(WalletRefusal.BAD_SIGNATURE), Snapshot.verify("${t[0]}.$forged.${t[2]}", ring, WalletTestKeys.TV))
        // clé inconnue, clé révoquée
        assertEquals(Verdict.Rejected(WalletRefusal.UNKNOWN_KEY), Snapshot.verify(TestMint.snapshot(snapshot().copy(kid = WalletTestKeys.stranger.keyId), WalletTestKeys.stranger), ring, WalletTestKeys.TV))
        assertEquals(Verdict.Rejected(WalletRefusal.REVOKED_KEY), Snapshot.verify(TestMint.snapshot(snapshot()), ring.withRevoked(setOf(WalletTestKeys.wallet.keyId)), WalletTestKeys.TV))
    }

    @Test fun strictParsingRefusesNonCanonicalPayloads() {
        val s = snapshot()
        val compact = castbridge.core.quiz.Json.write(s.payload())
        fun v(text: String) = Snapshot.verify(TestMint.raw("cbw1", Snapshot.DOMAIN, text, WalletTestKeys.wallet), ring, WalletTestKeys.TV)
        assertTrue(v(compact) is Verdict.Accepted)
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.replace(",", ", ")))                                  // espaces
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.replace("\"seq\":7", "\"seq\":07")))                 // zéro de tête
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.replace("\"seq\":7", "\"seq\":7.0")))                // décimal
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.dropLast(1) + ",\"seq\":99}"))                        // clé en double
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.dropLast(1) + ",\"extra\":1}"))                       // clé en trop
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.replace("\"nb\":0,", "")))                            // clé manquante
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), v(compact.replace("\"n\":3450", "\"n\":\"3450\"")))             // type
        for (bad in listOf("", "cbw1", "cbw1..", "cbw1.a.b", "cbw1.!!.!!", "x.y.z.w")) assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), Snapshot.verify(bad, ring, WalletTestKeys.TV))
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), Snapshot.verify(null, ring, WalletTestKeys.TV))
        // bourrage base64 ou signature à bits de queue modifiés : pas la forme canonique
        val t = TestMint.snapshot(s)
        assertEquals(Verdict.Rejected(WalletRefusal.UNREADABLE), Snapshot.verify("$t=", ring, WalletTestKeys.TV))
    }

    @Test fun escrowWhoseAmountIsNotPerTimesKIsRefused() {
        assertTrue(EscrowTicket.verify(TestMint.escrow(escrow()), ring, now) is Verdict.Accepted)
        assertEquals(Verdict.Rejected(WalletRefusal.AMOUNT_MISMATCH), EscrowTicket.verify(TestMint.escrow(escrow(amt = 41)), ring, now))
        assertEquals(Verdict.Rejected(WalletRefusal.AMOUNT_MISMATCH), EscrowTicket.verify(TestMint.escrow(escrow(amt = 20)), ring, now))
    }

    @Test fun escrowLifeKAndAudienceAreChecked() {
        fun v(e: EscrowTicket, at: Long = now) = EscrowTicket.verify(TestMint.escrow(e), ring, at)
        assertEquals(Verdict.Rejected(WalletRefusal.TOO_LONG_LIFE), v(escrow(exp = now + 30 * 60_000L + 1)))
        assertTrue(v(escrow(exp = now + 30 * 60_000L)) is Verdict.Accepted)
        assertEquals(Verdict.Rejected(WalletRefusal.TOO_LONG_LIFE), v(escrow(exp = now)))
        assertEquals(Verdict.Rejected(WalletRefusal.EXPIRED), v(escrow(), at = now + 600_000))
        assertEquals(Verdict.Rejected(WalletRefusal.NOT_YET_VALID), v(escrow(iat = now + 61_000, exp = now + 700_000)))
        assertEquals(Verdict.Rejected(WalletRefusal.OUT_OF_BOUNDS), v(escrow(k = 9, amt = 180)))
        assertEquals(Verdict.Rejected(WalletRefusal.OUT_OF_BOUNDS), v(escrow(k = 0, amt = 0)))
        val other = castbridge.core.quiz.Json.write(escrow().payload()).replace("castbridge-play", "castbridge-autre")
        assertEquals(Verdict.Rejected(WalletRefusal.WRONG_AUDIENCE), EscrowTicket.verify(TestMint.raw("cbe1", EscrowTicket.DOMAIN, other, WalletTestKeys.wallet), ring, now))
        assertEquals(Verdict.Rejected(WalletRefusal.UNKNOWN_KEY), EscrowTicket.verify(TestMint.escrow(escrow().copy(kid = WalletTestKeys.result.keyId), WalletTestKeys.result), ring, now))
    }

    @Test fun resultWhoseTotalsDifferFailsCheck() {
        val good = result(goodLines)
        assertTrue(good.check())
        assertFalse(result(goodLines.map { it.copy(pay = it.pay + 1) }).check())
        assertFalse(result(listOf(goodLines[0], goodLines[0])).check())
        assertFalse(result(emptyList()).check())
        assertFalse(result(List(17) { PlayResult.Line("%022d".format(it), "tv-a", 1, 1) }).check())
        assertFalse(result(goodLines, PlayResult.Kind.ABORT).check())                                   // un ABORT ne déplace rien
        assertTrue(result(goodLines.map { it.copy(used = 0, pay = 0) }, PlayResult.Kind.ABORT).check())
    }

    @Test fun serviceSignsAndApiVerifies() {
        val token = PlayResult.sign(result(goodLines), WalletTestKeys.result)
        assertTrue(token.startsWith("cbr1."))
        val v = PlayResult.verify(token, WalletTestKeys.resultRing)
        assertTrue(v is Verdict.Accepted && v.value.lines.size == 2 && v.value.lines[0].pay == 30L)
        // le service ne signe pas un résultat incohérent, ni sous la clé d'un autre
        assertFailsWith<IllegalArgumentException> { PlayResult.sign(result(goodLines.map { it.copy(pay = it.pay + 1) }), WalletTestKeys.result) }
        assertFailsWith<IllegalArgumentException> { PlayResult.sign(result(goodLines), WalletTestKeys.stranger) }
        // un résultat authentique mais incohérent (fabriqué hors de sign) est refusé à la vérification
        val bad = TestMint.result(result(goodLines.map { it.copy(pay = it.pay + 1) }))
        assertEquals(Verdict.Rejected(WalletRefusal.BAD_TOTALS), PlayResult.verify(bad, WalletTestKeys.resultRing))
        // clé du portefeuille ≠ clé de résultat : anneaux séparés
        assertEquals(Verdict.Rejected(WalletRefusal.UNKNOWN_KEY), PlayResult.verify(TestMint.result(result(goodLines).copy(kid = WalletTestKeys.wallet.keyId), WalletTestKeys.wallet), WalletTestKeys.resultRing))
        assertEquals(Verdict.Rejected(WalletRefusal.BAD_SIGNATURE), PlayResult.verify(token.replace("cbr1.", "cbr1.").dropLast(2) + "AA", WalletTestKeys.resultRing))
    }

    private inline fun <reified E : Throwable> assertFailsWith(block: () -> Unit) = kotlin.test.assertFailsWith<E> { block() }
}
