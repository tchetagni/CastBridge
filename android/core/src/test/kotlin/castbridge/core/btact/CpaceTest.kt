package castbridge.core.btact

import castbridge.core.owner.X25519
import java.math.BigInteger
import kotlin.test.*

/**
 * CPACE-X25519-SHA512 against the test vector of the draft itself (draft-irtf-cfrg-cpace-13, Appendix B.1, initiator-responder setting): the generator string, the generator, the two
 * messages, the shared point and the intermediate session key must come out byte for byte. A wrong Elligator 2, a wrong padding, a wrong hash truncation or a wrong transcript changes
 * every later value, so these few lines pin the whole primitive.
 */
class CpaceTest {
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    // Appendix B.1 of the draft
    private val prs = "Password".toByteArray()
    private val ci = unhex("6f630b425f726573706f6e6465720b415f696e69746961746f72")
    private val sid = unhex("7e4b4791d6a8ef019b936c79fb7f2c57")
    private val ya = unhex("21b4f4bd9e64ed355c3eb676a28ebedaf6d8f17bdc365995b319097153044080")
    private val yb = unhex("848b0779ff415f0af4ea14df9dd1d3c29ac41d836c7808896c4eba19c51ac40a")
    private val ada = unhex("414461")
    private val adb = unhex("414462")

    @Test fun theGeneratorStringIsTheOneOfTheDraft() {
        val s = Cpace.generatorString(prs, ci, sid)
        assertEquals(172, s.size, "9 + 9 + 110 + 27 + 17 octets : DSI, PRS, 109 zéros, CI, sid, chacun précédé de sa longueur")
        assertTrue(hex(s).startsWith("0843506163653235350850617373776f72646d00"), "len=8 « CPace255 », len=8 « Password », len=109 puis les zéros")
        assertEquals(109, s.drop(9 + 9 + 1).takeWhile { it == 0.toByte() }.size)
    }

    @Test fun theGeneratorIsTheOneOfTheDraft() {
        assertEquals("64e8099e3ea682cfdc5cb665c057ebb514d06bf23ebc9f743b51b82242327074", hex(Cpace.generator(prs, ci, sid)))
    }

    @Test fun theMessagesTheSharedPointAndTheSessionKeyAreThoseOfTheDraft() {
        val g = Cpace.generator(prs, ci, sid)
        val a = Cpace.message(ya, g); val b = Cpace.message(yb, g)
        assertEquals("1b02dad6dbd29a07b6d28c9e04cb2f184f0734350e32bb7e62ff9dbcfdb63d15", hex(a))
        assertEquals("20cda5955f82c4931545bcbf40758ce1010d7db4db2a907013d79c7a8fcf957f", hex(b))
        val k1 = assertNotNull(Cpace.sharedPoint(ya, b)); val k2 = assertNotNull(Cpace.sharedPoint(yb, a))
        assertEquals("f97fdfcfff1c983ed6283856a401de3191ca919902b323c5f950c9703df7297a", hex(k1))
        assertContentEquals(k1, k2, "les deux côtés trouvent le même point")
        assertEquals("a051ee5ee2499d16da3f69f430218b8ea94a18a45b67f9e86495b382c33d14a5c38cecc0cc834f960e39e0d1bf7d76b9ef5d54eecc5e0f386c97ad12da8c3d5f", hex(Cpace.isk(sid, k1, a, ada, b, adb)))
    }

    // ------------------------------------------------------------------ what the vector does not cover

    @Test fun leb128LengthsAreRightBeyondOneByte() {
        assertContentEquals(byteArrayOf(0), Cpace.prependLen(ByteArray(0)))
        assertEquals(1 + 127, Cpace.prependLen(ByteArray(127)).size)
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), Cpace.prependLen(ByteArray(128)).copyOf(2), "128 = 0x80 0x01")
        assertEquals(2 + 300, Cpace.prependLen(ByteArray(300)).size)
        assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02), Cpace.prependLen(ByteArray(300)).copyOf(2), "300 = 0xAC 0x02")
    }

    @Test fun aLongPasswordGetsNoPaddingAndTheGeneratorStillWorks() {
        val long = ByteArray(200) { 7 }
        val s = Cpace.generatorString(long, ci, sid)
        assertEquals(9 + (2 + 200) + 1 + 27 + 17, s.size, "au-delà d'un bloc, len_zpad = 0 : un octet de longueur (0) reste")
        assertTrue(Cpace.onCurve(Cpace.generator(long, ci, sid)))
    }

    @Test fun theGeneratorIsAlwaysAPointOfTheCurveAndDependsOnTheCodeTheSessionAndTheChannel() {
        val seen = HashSet<String>()
        for (n in 0 until 300) {
            val code = "%06d".format(n * 3331 % 1_000_000).toByteArray()
            val s = ByteArray(16) { (n + it).toByte() }
            val g = Cpace.generator(code, ci, s)
            assertTrue(Cpace.onCurve(g), "n=$n : le générateur est sur la courbe (pas sur sa torsion)")
            seen += hex(g)
        }
        assertEquals(300, seen.size, "300 entrées distinctes, 300 générateurs distincts")
        val base = hex(Cpace.generator("482913".toByteArray(), ci, sid))
        assertNotEquals(base, hex(Cpace.generator("482914".toByteArray(), ci, sid)), "un autre code")
        assertNotEquals(base, hex(Cpace.generator("482913".toByteArray(), ci, ByteArray(16) { 1 })), "une autre session")
        assertNotEquals(base, hex(Cpace.generator("482913".toByteArray(), unhex("00"), sid)), "un autre canal")
    }

    @Test fun elligatorFollowsRfc9380OnTheEdgesOfTheField() {
        val p = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
        for (u in listOf(BigInteger.ZERO, BigInteger.ONE, BigInteger.valueOf(2), p.subtract(BigInteger.ONE), p, p.add(BigInteger.ONE), BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE))) {
            val x = Cpace.elligator2(u)
            assertTrue(x.signum() >= 0 && x < p)
            val le = ByteArray(32).also { b -> val be = x.toByteArray(); for (i in 0 until 32) { val j = be.size - 1 - i; if (j >= 0) b[i] = be[j] } }
            assertTrue(Cpace.onCurve(le), "u=$u")
        }
        // u = 0: 1 + 2u² = 1, so x1 = -J ; if -J is not a square the map returns x2 = -x1 - J = 0 (RFC 9380: inv0(1) = 1)
        assertTrue(Cpace.elligator2(BigInteger.ZERO).let { it == p.subtract(BigInteger.valueOf(486_662)) || it.signum() == 0 })
    }

    @Test fun aPeerMessageOfSmallOrderIsRefused() {
        val g = Cpace.generator(prs, ci, sid)
        for (bad in listOf(ByteArray(32), ByteArray(32).also { it[0] = 1 }, unhex("e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800"))) assertNull(Cpace.sharedPoint(ya, bad))
        assertNotNull(Cpace.sharedPoint(ya, Cpace.message(yb, g)))
    }

    @Test fun twoSidesWithTheSameCodeAgreeAndWithAnotherCodeTheyDoNot() {
        val ci = BtActWire.CHANNEL_ID
        fun point(code: String, mine: ByteArray, peerCode: String, peer: ByteArray): ByteArray? {
            val gPeer = Cpace.generator(peerCode.toByteArray(), ci, sid)
            return Cpace.sharedPoint(mine, Cpace.message(peer, gPeer))
        }
        // « mine » computes with the code it typed, the peer message was made with ITS code
        val same = point("482913", ya, "482913", yb)!!
        assertContentEquals(same, Cpace.sharedPoint(yb, Cpace.message(ya, Cpace.generator("482913".toByteArray(), ci, sid)))!!)
        val other = Cpace.sharedPoint(yb, Cpace.message(ya, Cpace.generator("482914".toByteArray(), ci, sid)))!!
        assertFalse(same.contentEquals(other), "un code faux ne donne pas le même point")
    }

    @Test fun x25519StillMatchesTheJdkOnTheValuesUsedHere() {
        // the JDK 11+ `XDH` is not on every Android: it only serves here as an independent check of the ladder at the generator of the vector
        val kf = java.security.KeyFactory.getInstance("XDH")
        val g = Cpace.generator(prs, ci, sid)
        val pub = kf.generatePublic(java.security.spec.XECPublicKeySpec(java.security.spec.NamedParameterSpec.X25519, BigInteger(1, g.reversedArray()).clearBit(255)))
        val priv = kf.generatePrivate(java.security.spec.XECPrivateKeySpec(java.security.spec.NamedParameterSpec.X25519, ya))
        val ka = javax.crypto.KeyAgreement.getInstance("XDH"); ka.init(priv); ka.doPhase(pub, true)
        assertContentEquals(ka.generateSecret(), X25519.scalarMult(ya, g))
    }
}
