package castbridge.core

import castbridge.core.activation.Activation
import kotlin.test.*

class ActivationTest {
    @Test fun requestCodeIsStableAndReadable() {
        val mac = "74:24:CA:06:00:46"
        val c1 = Activation.requestCode(mac)
        val c2 = Activation.requestCode("7424ca060046")
        assertEquals(c1, c2, "normalization: same MAC, same code")
        assertTrue(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}").matches(c1))
    }

    @Test fun tokenDependsOnCodeAndSecret() {
        val c = Activation.requestCode("7424ca060046")
        val t1 = Activation.token(c, "secret-a")
        val t2 = Activation.token(c, "secret-b")
        assertNotEquals(t1, t2, "a different secret gives a different token")
        assertTrue(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}").matches(t1))
    }

    @Test fun verifyAcceptsTheRightTokenAndRejectsOthers() {
        val c = Activation.requestCode("7424ca060046")
        val t = Activation.token(c, "s3cret")
        assertTrue(Activation.verify(c, t, "s3cret"))
        assertTrue(Activation.verify(c, t.lowercase(), "s3cret"), "token is case-insensitive")
        assertFalse(Activation.verify(c, "AAAA-AAAA-AAAA", "s3cret"))
        assertFalse(Activation.verify("BBBB-BBBB", t, "s3cret"), "a token for another machine does not work")
    }

    @Test fun normalizationDropsSeparators() {
        assertEquals("AA:BB:CC".filter { it.isLetterOrDigit() }.uppercase(), Activation.normalize("aa:bb:cc"))
        assertEquals("7424CA060046", Activation.normalize("74-24-CA-06-00-46"))
    }

    @Test fun matchesTheReferenceAlgorithm() {
        // Reference values (Crockford base32 + HMAC-SHA256), shared with the server /admin/activation generator.
        assertEquals("QHPP-J8YN", Activation.requestCode("7424ca060046"))
        assertEquals("CAYN-SH5S-9QQD", Activation.token("QHPP-J8YN", "s3cret"))
    }
}
