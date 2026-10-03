package castbridge.play.entitlement

import castbridge.play.TestKeys
import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Le ticket `cbp1` (T-16, audit Opus I1) : vérifié FERMÉ, à usage unique (`jti` mémorisé jusqu'à `exp`), mémoire bornée. */
class TicketVerifierTest {
    private val v = TicketVerifier(listOf(TestKeys.pub))
    private val now = System.currentTimeMillis()
    private fun why(t: String?, at: Long = now) = (v.check(t, at) as? TicketVerifier.Result.Refused)?.why

    @Test fun aGoodTicketCarriesOnlyTheDeviceAttestation() {
        val r = v.check(TestKeys.ticket(now, deviceId = "dev-1", deviceCode = "AAAA-BBBB-CCCC-DDDD"), now)
        val t = assertIs<TicketVerifier.Result.Ok>(r).ticket
        assertEquals("dev-1", t.deviceId); assertEquals("AAAA-BBBB-CCCC-DDDD", t.deviceCode); assertEquals(32, t.jti.length)
        assertFalse(t.toString().contains(t.jti) || t.toString().contains("dev-1"), "toString ne montre ni le jti ni l'appareil (journaux)")
        assertTrue(TestKeys.ticket().split('.')[1].let { String(java.util.Base64.getUrlDecoder().decode(it)) }.let { "edition" !in it && "rights" !in it && "scopes" !in it }, "le ticket ne porte ni édition ni droits")
    }

    @Test fun expiredNotYetValidWrongKeyWrongAudienceBlockedAndMalformedAreAllRefused() {
        assertEquals(TicketVerifier.Refusal.EXPIRED, why(TestKeys.ticket(now = now - 200_000, lifeMs = 60_000)))
        assertEquals(TicketVerifier.Refusal.NOT_YET_VALID, why(TestKeys.ticket(now = now, iat = now + 600_000, lifeMs = 60_000)))
        assertEquals(TicketVerifier.Refusal.BAD_SIGNATURE, why(TestKeys.ticket(pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair())))
        assertEquals(TicketVerifier.Refusal.WRONG_AUDIENCE, why(TestKeys.ticket(aud = "castbridge-update")))
        assertEquals(TicketVerifier.Refusal.BLOCKED, why(TestKeys.ticket(blocked = true)))
        assertEquals(TicketVerifier.Refusal.TOO_LONG, why(TestKeys.ticket(lifeMs = 3_600_000)))
        assertEquals(TicketVerifier.Refusal.MALFORMED, why(TestKeys.ticket(jti = null)), "sans jti : refusé")
        assertEquals(TicketVerifier.Refusal.MALFORMED, why(TestKeys.ticket(jti = "court")), "jti de moins de 128 bits")
        assertEquals(TicketVerifier.Refusal.MALFORMED, why(TestKeys.ticket(deviceId = "")))
        for (bad in listOf(null, "", "x", "cbp1.", "cbp1.a.b", "cbp1.a.b.c", "v1.eyJ9.AA", "cbp1." + "A".repeat(2_000) + ".AA")) assertEquals(false, v.verify(bad, now), "refusé : $bad")
    }

    @Test fun aTamperedPayloadOrASwappedPrefixIsRefused() {
        val t = TestKeys.ticket(now, deviceId = "dev-1").split('.')
        val forged = String(java.util.Base64.getUrlDecoder().decode(t[1])).replace("dev-1", "dev-2")
        val forgedTicket = "cbp1." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(forged.toByteArray()) + "." + t[2]
        assertEquals(TicketVerifier.Refusal.BAD_SIGNATURE, why(forgedTicket))
        assertEquals(TicketVerifier.Refusal.MALFORMED, why("v1." + t[1] + "." + t[2]), "l'ancien préfixe v1 n'est plus accepté")
    }

    @Test fun theLifeLimitAndTheClockSkewAreExactToTheMillisecond() {
        assertTrue(v.verify(TestKeys.ticket(now = now, lifeMs = 15 * 60_000L), now), "15 min exactes : accepté")
        assertEquals(TicketVerifier.Refusal.TOO_LONG, why(TestKeys.ticket(now = now, lifeMs = 15 * 60_000L + 1)), "exp − iat > 15 min")
        assertTrue(v.verify(TestKeys.ticket(now = now, iat = now + 60_000L, lifeMs = 60_000), now), "décalage de 60 s : accepté")
        assertEquals(TicketVerifier.Refusal.NOT_YET_VALID, why(TestKeys.ticket(now = now, iat = now + 60_001L, lifeMs = 60_000)), "décalage de 60 s + 1 ms : refusé")
    }

    @Test fun base64IsDecodedStrictlyForTheSignatureAndForTheConfiguredKey() {
        val good = TestKeys.ticket(now)
        assertTrue(v.verify(good, now))
        assertEquals(TicketVerifier.Refusal.MALFORMED, why(good + "="), "signature avec bourrage : refusée")
        assertEquals(TicketVerifier.Refusal.MALFORMED, why(good.replaceFirst('.', '.') .let { it.dropLast(2) + "\n" + it.takeLast(2) }), "signature avec saut de ligne : refusée")
        assertFalse(TicketVerifier(listOf(TestKeys.pub.substring(0, 20) + "!!" + TestKeys.pub.substring(20))).configured, "clé avec caractère étranger : ignorée (le décodeur MIME les sautait)")
        assertTrue(TicketVerifier(listOf(TestKeys.pub + "\n")).configured, "un saut de ligne final de fichier reste toléré")
    }

    @Test fun withoutAnyKeyNoTicketIsValid() {
        val none = TicketVerifier(emptyList())
        assertFalse(none.configured)
        assertEquals(TicketVerifier.Refusal.NO_KEY, (none.check(TestKeys.ticket(), now) as TicketVerifier.Result.Refused).why)
        assertFalse(TicketVerifier(listOf("pas une clé", "")).configured)
    }

    @Test fun anUsedTicketIsRefusedUntilItsExpiryThenForgotten() {
        val u = UsedTickets(cap = 100)
        assertEquals(UsedTickets.Use.OK, u.use("a".repeat(32), exp = now + 10_000, now = now))
        assertEquals(UsedTickets.Use.REPLAY, u.use("a".repeat(32), exp = now + 10_000, now = now + 5_000))
        assertEquals(UsedTickets.Use.OK, u.use("b".repeat(32), exp = now + 10_000, now = now + 5_000))
        assertEquals(2, u.size())
        u.sweep(now + 10_001)
        assertEquals(0, u.size(), "les jti échus sont oubliés : la mémoire suit le temps")
    }

    @Test fun usedTicketMemoryIsBoundedAndFailsClosedWhenFull() {
        val u = UsedTickets(cap = 50)
        for (i in 0 until 50) assertEquals(UsedTickets.Use.OK, u.use("%032x".format(i), now + 600_000, now))
        assertEquals(UsedTickets.Use.FULL, u.use("f".repeat(32), now + 600_000, now), "plein : refus, jamais d'éviction d'un jti encore valable (le rejeu resterait possible)")
        assertEquals(UsedTickets.Use.REPLAY, u.use("%032x".format(3), now + 600_000, now), "un jti déjà vu reste un rejeu même quand c'est plein")
        assertEquals(UsedTickets.Use.OK, u.use("e".repeat(32), now + 600_000, now + 600_001), "après l'échéance, le balayage libère la place")
        for (i in 0 until 1_000_000 step 1) { if (i > 3_000) break; u.use("%032x".format(100_000 + i), now + 1_200_000, now + 700_000) }
        assertTrue(u.size() <= 50, "taille ${u.size()}")
    }

    @Test fun theServiceSourcesHoldNoPrivateTicketKey() {
        val root = File("src")
        val keys = root.walkTopDown().filter { it.isFile && it.name.endsWith(".key") }.map { it.path.replace('\\', '/') }.toList()
        assertTrue(keys.all { "/src/test/resources/" in "/$it" || it.startsWith("src/test/resources/") }, "aucun fichier *.key hors src/test/resources : $keys")
        val main = File("src/main").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt")) }.joinToString("\n") { it.readText() }
        assertFalse(Regex("/run/secrets/[A-Za-z0-9_.-]*\\.key|play-ticket\\.key|BEGIN (EC |ED25519 )?PRIVATE KEY|initSign").containsMatchIn(main), "aucune lecture d'un secret de clé privée, aucune signature dans le service")
    }
}
