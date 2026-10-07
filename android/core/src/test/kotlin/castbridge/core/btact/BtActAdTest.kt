package castbridge.core.btact

import castbridge.core.tv.BtProtocol
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

/** What the TV advertises over BLE and what the phone filters on: the service UUID, the two-byte tag of the code, the PSM; sizes that fit two legacy packets; what the tag leaks, said in numbers. */
class BtActAdTest {
    private val code = "482913"

    @Test fun theAdvertisedServiceIsTheEighthOfTheTable() {
        assertEquals("7c5e3b9a-4d2f-4c61-9b0e-cb0000000008", BtActAd.SERVICE_UUID)
        assertEquals(BtProtocol.ACTIVATION_SERVICE_UUID, BtActAd.SERVICE_UUID)
        assertTrue(BtProtocol.SERVICES.any { it.uuid == BtActAd.SERVICE_UUID && it.number == 8 })
    }

    @Test fun theTagIsTheFirstTwoBytesOfAnHmacOfTheCodeAndNeverTheCode() {
        val expected = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec("castbridge-bt-activation-v1/advert".toByteArray(), "HmacSHA256")); doFinal(code.toByteArray()) }.copyOf(2)
        assertContentEquals(expected, BtActAd.tag(code))
        assertEquals(2, BtActAd.tag(code).size); assertEquals(2, BtActAd.TAG_BYTES)
        assertContentEquals(BtActAd.tag(code), BtActAd.tag(code), "stable : il ne tourne pas")
        for (c in listOf("000000", "999999", code)) assertFalse(BtActAd.tag(c).contentEquals(c.toByteArray().copyOf(2)), "le tag n'est pas le début du code")
        assertNotEquals(BtActAd.tag(code).toList(), BtActAd.tag("482914").toList())
        for (bad in listOf("", "12345", "1234567", "12345a")) assertFailsWith<IllegalArgumentException>(bad) { BtActAd.tag(bad) }
    }

    @Test fun thePayloadIsVersionTagAndPsmAndParsesBack() {
        val p = BtActAd.payload(code, 0x0081)
        assertEquals(5, p.size); assertEquals(BtActAd.PAYLOAD_BYTES, p.size)
        assertEquals(1, p[0].toInt()); assertContentEquals(BtActAd.tag(code), p.copyOfRange(1, 3)); assertEquals(0x00, p[3].toInt()); assertEquals(0x81, p[4].toInt() and 0xFF)
        val parsed = assertNotNull(BtActAd.parse(p))
        assertEquals(1, parsed.version); assertEquals(0x81, parsed.psm); assertTrue(BtActAd.matches(parsed, code)); assertFalse(BtActAd.matches(parsed, "111111"))
        assertEquals(0xFFFF, BtActAd.parse(BtActAd.payload(code, 0xFFFF))!!.psm)
        assertEquals(0, BtActAd.parse(BtActAd.payload(code, 0))!!.psm, "0 = pas de canal L2CAP : RFCOMM seulement")
        assertFailsWith<IllegalArgumentException> { BtActAd.payload(code, -1) }; assertFailsWith<IllegalArgumentException> { BtActAd.payload(code, 0x10000) }
        assertFalse(code in parsed.toString(), "le PSM, jamais le code, dans la description")
    }

    @Test fun whatIsNotOursIsNeverGuessed() {
        assertNull(BtActAd.parse(null)); assertNull(BtActAd.parse(ByteArray(0)))
        assertNull(BtActAd.parse(ByteArray(4))); assertNull(BtActAd.parse(ByteArray(6)))
        assertNull(BtActAd.parse(BtActAd.payload(code, 5).also { it[0] = 2 }), "une autre version")
        assertFalse(BtActAd.matches(null, code))
    }

    @Test fun theUuidAndTheRecordShareOnePacketOfThirtyBytesAndTheFallbackFitsToo() {
        assertEquals(21, BtActAd.ADVERTISEMENT_BYTES, "drapeaux 3 + UUID 128 bits 18")
        assertEquals(9, BtActAd.RECORD_BYTES, "longueur 1 + type 1 + identifiant du fabricant 2 + charge 5")
        assertEquals(30, BtActAd.SINGLE_PACKET_BYTES, "l'UUID et l'enregistrement dans le MÊME paquet : un filtre matériel sur l'UUID voit aussi l'enregistrement")
        assertTrue(BtActAd.SINGLE_PACKET_BYTES <= 31, "30 octets sur les 31 d'une annonce historique")
        assertEquals(BtActAd.RECORD_BYTES, BtActAd.SCAN_RESPONSE_BYTES, "repli : l'enregistrement seul dans la réponse de balayage")
        assertTrue(BtActAd.ADVERTISEMENT_BYTES <= 31 && BtActAd.SCAN_RESPONSE_BYTES <= 31)
        assertEquals(0xFFFF, BtActAd.COMPANY_ID, "l'identifiant réservé aux essais par le Bluetooth SIG : aucune société n'est usurpée")
    }

    @Test fun whatTheTagLeaksIsNumberedHere() {
        // every one of the 10^6 codes through the tag: 65 536 buckets, about 15 codes each. The tag is public (anyone can compute it for any code), so this IS what a listener learns.
        val buckets = IntArray(65_536)
        val mac = Mac.getInstance("HmacSHA256").also { it.init(SecretKeySpec("castbridge-bt-activation-v1/advert".toByteArray(), "HmacSHA256")) }
        for (n in 0 until 1_000_000) {
            val t = mac.doFinal("%06d".format(n).toByteArray())
            buckets[((t[0].toInt() and 0xFF) shl 8) or (t[1].toInt() and 0xFF)]++
        }
        val avg = 1_000_000.0 / 65_536
        assertEquals(1_000_000, buckets.sum())
        assertTrue(avg in 15.0..15.5, "environ 15 candidats par tag : $avg")
        assertTrue(buckets.max() < 50, "aucun tag ne désigne beaucoup plus de codes que les autres : ${buckets.max()}")
        assertTrue(buckets.count { it == 0 } < 100, "presque tous les tags existent")
    }

    @Test fun anotherTvAmongTenIsOnlyAFalsePositiveOnceInSixtyFiveThousand() {
        // the shop of ten locked TVs: the phone that typed one code tries only the TVs whose tag matches; a neighbour matches with probability 2^-16
        val others = (0 until 9).map { "%06d".format(100_003 * (it + 1) % 1_000_000) }.filter { it != code }
        val mine = BtActAd.parse(BtActAd.payload(code, 3))
        val matching = others.count { BtActAd.matches(BtActAd.parse(BtActAd.payload(it, 3)), code) }
        assertTrue(BtActAd.matches(mine, code))
        assertEquals(0, matching, "aucun voisin de cet atelier ne correspond (chance de 1 sur 65 536 chacun)")
    }
}
