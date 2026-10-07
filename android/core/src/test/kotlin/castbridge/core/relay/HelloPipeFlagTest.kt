package castbridge.core.relay

import castbridge.core.FakeClock
import castbridge.core.FakeTv
import castbridge.core.trust.PhoneLink
import castbridge.core.trust.SavedTv
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloInfo
import castbridge.core.tv.LinkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** relay-R1 § 2 : « la TV veut un tuyau » dans l'état échangé par la liaison de confiance (HELLO), et la capacité « tuyau à la demande » des téléphones. */
class HelloPipeFlagTest {
    private val token = "cbk_" + "a".repeat(64)
    private fun info(pipe: Boolean = false) = HelloInfo("TV", "0.14", null, token, 3600, LinkInfo(8765, listOf("10.0.0.2")), installId = "b".repeat(32), maxPhones = 8, pipeWanted = pipe)

    @Test fun theFlagIsOneAdditiveLineAndAbsentWhenFalse() {
        val off = info(false).encode()
        assertFalse(off.lines().any { it.startsWith("pipe=") }, "une TV qui ne demande rien dit exactement ce qu'elle disait avant")
        assertEquals(off, HelloInfo("TV", "0.14", null, token, 3600, LinkInfo(8765, listOf("10.0.0.2")), installId = "b".repeat(32), maxPhones = 8).encode(), "défaut = faux")
        val on = info(true).encode()
        assertTrue(on.lines().contains("pipe=1"))
        assertEquals(on.lines().filterNot { it == "pipe=1" }, off.lines(), "rien d'autre ne change")
    }

    @Test fun theFlagRoundTripsAndOldTextsDecodeToFalse() {
        assertTrue(HelloInfo.decode(info(true).encode())!!.pipeWanted)
        assertFalse(HelloInfo.decode(info(false).encode())!!.pipeWanted)
        assertFalse(HelloInfo.decode(info(true).encode().replace("pipe=1", "pipe=2"))!!.pipeWanted, "seule la valeur 1 est une demande")
        assertFalse(HelloInfo.decode(info(true).encode().replace("pipe=1", "pipe="))!!.pipeWanted)
        // une TV sans la ligne : l'état d'avant
        assertFalse(HelloInfo.decode(info(true).encode().lines().filterNot { it.startsWith("pipe=") }.joinToString("\n"))!!.pipeWanted)
    }

    @Test fun anOlderPhoneReadsTheNewTextAsBefore() {
        // un décodeur ancien ignore les clés qu'il ne connaît pas : ici le même décodeur sans les clés récentes
        val older = info(true).encode().lines().filterNot { it.startsWith("pipe=") || it.startsWith("maxphones=") || it.startsWith("id=") }.joinToString("\n")
        val d = HelloInfo.decode(older)
        assertNotNull(d); assertEquals("TV", d.tvName); assertEquals(token, d.token)
    }

    // ------------------------------------------------------------------ de bout en bout : un vrai HELLO entre un faux téléphone et une fausse TV

    @Test fun aSynchronizedPhoneSeesTheDemandInItsSession() {
        val tv = FakeTv(FakeClock())
        tv.reg.trust(tv.phone, "Galaxy")
        val quiet = PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV")) as castbridge.core.trust.PhoneLink.Result.Connected
        assertFalse(quiet.session.info.pipeWanted)
        tv.pipeWanted = true
        val asked = PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV")) as castbridge.core.trust.PhoneLink.Result.Connected
        assertTrue(asked.session.info.pipeWanted, "la liaison de confiance porte la demande jusqu'au téléphone")
    }

    @Test fun anUnknownPhoneNeverLearnsWhatTheTvWants() {
        val tv = FakeTv(FakeClock()); tv.pipeWanted = true
        val r = PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV"))
        assertTrue(r is castbridge.core.trust.PhoneLink.Result.Refused, "HELLO d'un téléphone non synchronisé : rien, pas même cette information")
    }

    @Test fun everyNewPhoneAdvertisesTheCapabilityInItsHelloFlags() {
        val tv = FakeTv(FakeClock()); tv.reg.trust(tv.phone, "Galaxy")
        PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV"))
        val flags = tv.helloFlags.single()
        assertTrue(flags and BtProtocol.HELLO_RELAY != 0, "le bit « tuyau à la demande » est levé par ce téléphone")
        assertEquals(4, BtProtocol.HELLO_RELAY)
        assertEquals(0, flags and BtProtocol.HELLO_REQUEST_TRUST, "un HELLO ordinaire ne demande pas la confiance")
    }

    @Test fun theFlagsCombineWithTheOthersAndOldTvsIgnoreTheNewBit() {
        val tv = FakeTv(FakeClock())
        // « Ajouter ma TV » : demande de confiance + identifiant mémorisé + capacité
        PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV", installId = "c".repeat(32)), requestTrust = true)
        val f = tv.helloFlags.single()
        assertEquals(BtProtocol.HELLO_REQUEST_TRUST or BtProtocol.HELLO_HAS_INSTALL_ID or BtProtocol.HELLO_RELAY, f)
        // ce qu'un TV ancien lit : seuls les bits 0 et 1 comptent
        assertTrue(f and BtProtocol.HELLO_REQUEST_TRUST != 0 && f and BtProtocol.HELLO_HAS_INSTALL_ID != 0)
    }
}
