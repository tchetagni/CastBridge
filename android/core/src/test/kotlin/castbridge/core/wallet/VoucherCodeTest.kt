package castbridge.core.wallet

import castbridge.core.owner.Base32C
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VoucherCodeTest {
    private val keys = WalletTestKeys.voucherKeys
    private val now = WalletTestKeys.NOW
    private val dev = WalletTestKeys.DEVICE_CODE
    private val mine = Voucher.targetOf(dev)!!

    private fun ok(d: VoucherCode.Decoded) = (d as VoucherCode.Decoded.Ok).voucher

    @Test fun binaryTextRoundTrip() {
        val v = TestMint.voucher(cur = WalletCurrency.MBOKO, amount = 50, targetHex = mine)
        assertEquals(Voucher.SIZE, v.encode().size)
        val text = VoucherCode.encode(v)
        assertEquals(VoucherCode.CHARS + VoucherCode.GROUPS - 1, text.length)       // 183 caractères + 36 tirets
        val back = ok(VoucherCode.decode(text))
        assertTrue(back.encode().contentEquals(v.encode()))
        assertEquals(WalletCurrency.MBOKO, back.currency); assertEquals(50, back.amount); assertEquals(mine, back.targetHex)
        // fichier et QR : le même texte
        assertEquals(text, VoucherCode.qrContent(v)); assertEquals(text + "\n", VoucherCode.encodeFile(v))
        assertTrue(ok(VoucherCode.decodeFile("﻿" + VoucherCode.encodeFile(v))).encode().contentEquals(v.encode()))
        assertEquals(VoucherCode.Decoded.Malformed, VoucherCode.decodeFile(text + "\n" + text))
        // tolérances : minuscules, espaces, O/0 et I/1
        assertTrue(ok(VoucherCode.decode(text.lowercase().replace("-", " "))).encode().contentEquals(v.encode()))
        assertTrue(ok(VoucherCode.decode(text.replace('0', 'O').replace('1', 'I'))).encode().contentEquals(v.encode()))
    }

    @Test fun amountIsBigEndianAndLayoutIsFixed() {
        val v = TestMint.voucher(amount = 0x01020304L, day = 0x0A0B)
        val b = v.encode()
        assertEquals(listOf<Byte>(1, 1, 1, 1, 2, 3, 4), b.take(7))
        assertEquals(listOf<Byte>(0x0A, 0x0B), b.slice(17..18))
        assertEquals(20454L, Voucher.EPOCH_DAY)
    }

    @Test fun typoInGroupSevenIsLocalised() {
        val text = VoucherCode.encode(TestMint.voucher())
        val groups = text.split('-').toMutableList()
        val g = groups[6]                                                           // groupe 7
        groups[6] = g[0].toString() + (if (g[1] == 'Z') 'Y' else 'Z') + g.substring(2)
        val d = VoucherCode.decode(groups.joinToString("-"))
        assertEquals(VoucherCode.Decoded.BadGroup(7), d)
        assertEquals("Faute de frappe dans le groupe 7", (d as VoucherCode.Decoded.BadGroup).message)
        // faute dans le dernier groupe (court)
        val last = text.split('-').toMutableList(); last[36] = (if (last[36][0] == 'Z') 'Y' else 'Z') + last[36].substring(1)
        assertEquals(VoucherCode.Decoded.BadGroup(37), VoucherCode.decode(last.joinToString("-")))
        // caractère étranger au groupe 3
        val alien = text.split('-').toMutableList(); alien[2] = "U" + alien[2].substring(1)
        assertEquals(VoucherCode.Decoded.BadGroup(3), VoucherCode.decode(alien.joinToString("-")))
        assertEquals(VoucherCode.Decoded.Malformed, VoucherCode.decode(text.dropLast(1)))
        assertEquals(VoucherCode.Decoded.Malformed, VoucherCode.decode(text + "0"))
        assertEquals(VoucherCode.Decoded.Malformed, VoucherCode.decode(""))
    }

    @Test fun mbokoForAnyTvIsRefused() {
        val any = TestMint.voucher(cur = WalletCurrency.MBOKO, amount = 10)
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(any, keys, dev, now))
        assertTrue(Voucher.verify(TestMint.voucher(cur = WalletCurrency.MBOKO, amount = 10, targetHex = mine), keys, dev, now) is Verdict.Accepted)
        assertTrue(Voucher.verify(TestMint.voucher(cur = WalletCurrency.NDEM), keys, dev, now) is Verdict.Accepted)
    }

    @Test fun expiredWrongTvBadSignatureAndClock() {
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_EXPIRED), Voucher.verify(TestMint.voucher(day = 200), keys, dev, now))
        val day = (now / 86_400_000L - Voucher.EPOCH_DAY).toInt()
        assertTrue(Voucher.verify(TestMint.voucher(day = day), keys, dev, now) is Verdict.Accepted)                  // valable jusqu'à la fin du jour d'échéance
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_EXPIRED), Voucher.verify(TestMint.voucher(day = day), keys, dev, (Voucher.EPOCH_DAY + day + 1) * 86_400_000L))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_OTHER_TV), Voucher.verify(TestMint.voucher(targetHex = mine), keys, WalletTestKeys.OTHER_DEVICE_CODE, now))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_OTHER_TV), Voucher.verify(TestMint.voucher(targetHex = mine), keys, "pas un code", now))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(TestMint.voucher(signer = WalletTestKeys.stranger), keys, dev, now))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(TestMint.voucher(version = 2), keys, dev, now))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(TestMint.voucher(keyIndex = 2), keys, dev, now))
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(TestMint.voucher(amount = 0), keys, dev, now))
        assertEquals(Verdict.Rejected(WalletRefusal.CLOCK), Voucher.verify(TestMint.voucher(), keys, dev, 0))
        // montant modifié après signature
        val b = TestMint.voucher().encode(); b[6] = (b[6] + 1).toByte()
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(Voucher.decode(b)!!, keys, dev, now))
        // clé révoquée
        val revoked = VoucherKeys(keys.ring.withRevoked(setOf(WalletTestKeys.voucher.keyId)), keys.kidByIndex)
        assertEquals(Verdict.Rejected(WalletRefusal.VOUCHER_BAD), Voucher.verify(TestMint.voucher(), revoked, dev, now))
        // longueur et monnaie inconnue
        assertEquals(null, Voucher.decode(ByteArray(90))); assertEquals(null, Voucher.decode(ByteArray(92)))
        val c = TestMint.voucher().encode(); c[2] = 3
        assertEquals(null, Voucher.decode(c))
    }

    @Test fun targetIsTheHashOfTheNormalisedDeviceCode() {
        assertEquals(Voucher.targetOf(dev), Voucher.targetOf(dev.lowercase().replace("-", " ")))
        assertNotEquals(Voucher.targetOf(dev), Voucher.targetOf(WalletTestKeys.OTHER_DEVICE_CODE))
        assertEquals(null, Voucher.targetOf("1234"))
    }

    @Test fun thousandRandomVouchersNeverPassASingleCharacterSubstitution() {
        val rnd = SecureRandom()
        repeat(1000) {
            val nonce = ByteArray(10).also(rnd::nextBytes).joinToString("") { "%02x".format(it) }
            val sig = ByteArray(64).also(rnd::nextBytes)   // signature aléatoire : seul le CONTRÔLE DE SAISIE est testé ici, pas la signature
            val v = Voucher(1, 1, WalletCurrency.values()[rnd.nextInt(2)], 1L + rnd.nextInt(10_000), nonce, rnd.nextInt(400), Voucher.ANY_TARGET, sig)
            val text = VoucherCode.encode(v)
            assertTrue(VoucherCode.decode(text) is VoucherCode.Decoded.Ok)
            val chars = text.replace("-", "")
            val pos = rnd.nextInt(chars.length)
            val other = Base32C.ALPHABET.filter { Base32C.value(it) != Base32C.value(chars[pos]) }.random()
            val d = VoucherCode.decode(chars.substring(0, pos) + other + chars.substring(pos + 1))
            assertTrue(d is VoucherCode.Decoded.BadGroup && d.group == pos / 5 + 1, "substitution en $pos non localisée au groupe ${pos / 5 + 1} : $d")
        }
    }
}
