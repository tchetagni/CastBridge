package castbridge.core.quiz.online

import castbridge.core.owner.InstallSigner
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H-3 (audit Opus) : preuve de possession de la clé d'installation de la TV, liée au ticket (`jti`), au code d'appareil et à l'activation présentée. */
class PlayProofTest {
    private val signer = InstallSigner.create()
    private val other = InstallSigner.create()
    private val code = "ABCD-EFGH-JKMN-PQR5"
    private val jti = "0123456789abcdef0123456789abcdef"
    private fun ticket(j: String = jti, c: String = code): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"aud":"castbridge-play","deviceCode":"$c","jti":"$j"}""".toByteArray())
        return "cbp1.$payload.sig"
    }
    private val activation = "cbx1.payload-A.sig"
    private val hash get() = PlayProof.installHash(signer.publicKeyBase64)!!

    @Test fun aProofBuiltByTheInstallKeyVerifiesForThatTicketCodeAndActivation() {
        val p = PlayProof.build(signer::sign, signer.publicKeyBase64, ticket(), activation)
        assertNotNull(p, "la TV sait construire la preuve")
        assertTrue(PlayProof.verify(p, jti, code, activation, hash))
    }

    @Test fun theProofIsBoundToTheTicketTheCodeTheActivationAndTheKey() {
        val p = PlayProof.build(signer::sign, signer.publicKeyBase64, ticket(), activation)
        assertFalse(PlayProof.verify(p, "f".repeat(32), code, activation, hash), "autre ticket (jti) : rejeu refusé")
        assertFalse(PlayProof.verify(p, jti, "ZZZZ-ZZZZ-ZZZZ-ZZZZ", activation, hash), "autre code d'appareil")
        assertFalse(PlayProof.verify(p, jti, code, "cbx1.payload-B.sig", hash), "autre activation : une preuve ne couvre pas une activation copiée")
        assertFalse(PlayProof.verify(p, jti, code, activation, PlayProof.installHash(other.publicKeyBase64)!!), "le ticket épingle une AUTRE clé d'installation")
        val forged = PlayProof.build(other::sign, other.publicKeyBase64, ticket(), activation)
        assertFalse(PlayProof.verify(forged, jti, code, activation, hash), "la clé d'un faussaire n'a pas l'empreinte épinglée")
        val swapped = other.publicKeyBase64 + "." + p!!.substringAfter('.')
        assertFalse(PlayProof.verify(swapped, jti, code, activation, hash), "signature de la TV sous la clé d'un autre : refusé")
    }

    @Test fun malformedProofsAreRefusedNeverThrown() {
        for (bad in listOf(null, "", "abc", ".", "a.b", "!!!.???", "x".repeat(300), signer.publicKeyBase64 + ".", "." + "A".repeat(88))) assertFalse(PlayProof.verify(bad, jti, code, activation, hash), "refusé : $bad")
    }

    @Test fun ticketFieldsAreReadStrictly() {
        assertEquals(jti, PlayProof.jtiOf(ticket()))
        assertNull(PlayProof.jtiOf("cbp1.%%%.sig")); assertNull(PlayProof.jtiOf(null)); assertNull(PlayProof.jtiOf("pas-un-ticket"))
        assertEquals(64, PlayProof.installHash(signer.publicKeyBase64)!!.length)
        assertNull(PlayProof.installHash("pas du base64 !"))
        assertNull(PlayProof.build(signer::sign, signer.publicKeyBase64, "pas-un-ticket", activation), "sans ticket lisible, pas de preuve")
        assertNull(PlayProof.build(signer::sign, signer.publicKeyBase64, ticket(), null), "sans activation, pas de preuve")
    }
}
