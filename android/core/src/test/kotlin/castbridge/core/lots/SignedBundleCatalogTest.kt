package castbridge.core.lots

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignedBundleCatalogTest {
    /** Produced by `trial_edition.py sign-catalog` with the TEST seed 00 01 02 … 1f (never a real key). Proves Python and Kotlin sign and verify the same text. */
    private val pythonSigned = """{"bundles":[{"id":"classe-cm2","type":"classe","lots":["learn:cm2","quiz:cm2"],"title":"Classe « CM2 »","rawBytes":9000,"rentalDays":14},{"id":"quiz-cm2","type":"quiz","lots":["quiz:cm2"],"title":"Quiz CM2","rawBytes":3000,"rentalDays":30}],"generatedAt":"2026-10-02T10:00:00Z","keyId":"56475aa75463474c","signature":"pMsrhcaPyjw8iDempb35bKTrTF79AKrAk5WUQ0FfsZKUjCkAu0Oaz8gLACnzkY5DLRe1kCm2cSb/HRVTVIrCCQ=="}"""
    private val pythonKey = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg="

    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded.takeLast(32).toByteArray())

    private fun signed(at: String = "2026-10-02T10:00:00Z", rental: Int = 14, title: String = "Classe CM2"): String {
        val bundles = listOf(Bundle("quiz-cm2", "quiz", setOf("quiz:cm2"), "Quiz CM2", 3000, 30), Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2"), title, 9000, rental))
        val s = Signature.getInstance("Ed25519").apply { initSign(kp.private); update(SignedBundleCatalog.canonicalPayload(at, bundles).toByteArray()) }
        val sig = Base64.getEncoder().encodeToString(s.sign())
        return """{"generatedAt":"$at","keyId":"t","signature":"$sig","bundles":[
            {"id":"quiz-cm2","type":"quiz","lots":["quiz:cm2"],"title":"Quiz CM2","rawBytes":3000,"rentalDays":30},
            {"id":"classe-cm2","type":"classe","lots":["quiz:cm2","learn:cm2"],"title":"$title","rawBytes":9000,"rentalDays":$rental}]}"""
    }

    private fun refused(json: String, keys: List<String> = listOf(pub), older: String? = null) =
        assertFailsWith<SignedBundleCatalog.Refused> { SignedBundleCatalog.verify(json, keys, older) }.message!!

    @Test fun acceptsASignedCatalogueAndReturnsTheBundles() {
        val v = SignedBundleCatalog.verify(signed(), listOf("autre", pub))
        assertEquals(2, v.catalog.bundles.size)
        assertEquals(14, v.catalog.find("classe-cm2")!!.rentalDays)
        assertEquals("02/10/2026", v.dateFr)
    }

    @Test fun acceptsTheCatalogueSignedByThePythonTool() {
        val v = SignedBundleCatalog.verify(pythonSigned, listOf(pythonKey))
        assertEquals(setOf("learn:cm2", "quiz:cm2"), v.catalog.find("classe-cm2")!!.lots)
        assertTrue(refused(pythonSigned, listOf(pub)).contains("Signature"))
    }

    @Test fun refusesAnAlteredCatalogue() {
        assertTrue(refused(signed().replace("\"rentalDays\":14", "\"rentalDays\":365")).contains("Signature"))
        assertTrue(refused(signed().replace("Classe CM2", "Classe CM3")).contains("Signature"))
        assertTrue(refused(signed().replace("\"quiz:cm2\",\"learn:cm2\"", "\"quiz:cm2\",\"learn:cm2\",\"learn:cm1\"")).contains("Signature"))
        assertTrue(refused(signed().replace("2026-10-02T10:00:00Z", "2026-10-03T10:00:00Z")).contains("Signature"))
    }

    @Test fun refusesUnsignedWrongKeyAndGarbage() {
        assertTrue(refused(signed().replace(Regex("\"signature\":\"[^\"]*\","), "")).contains("non signé"))
        assertTrue(refused(signed().replace(Regex("\"signature\":\"[^\"]*\""), "\"signature\":\"UNSIGNED\"")).contains("non signé"))
        assertTrue(refused(signed().replace(Regex("\"signature\":\"[^\"]*\""), "\"signature\":\"%%%\"")).contains("Signature"))
        assertTrue(refused(signed(), listOf("A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg=")).contains("Signature"))
        assertTrue(refused(signed(), emptyList()).contains("clé"))
        assertTrue(refused("pas du json").contains("illisible"))
        assertTrue(refused("""{"bundles":[],"generatedAt":"2026-10-02T10:00:00Z","signature":"AAAA"}""").contains("vide"))
    }

    @Test fun refusesAnOlderCatalogueThanTheOneKept() {
        SignedBundleCatalog.verify(signed("2026-10-02T10:00:00Z"), listOf(pub), "2026-10-02T10:00:00Z")
        assertTrue(refused(signed("2026-10-01T10:00:00Z"), older = "2026-10-02T10:00:00Z").contains("plus ancien"))
        assertEquals("2026-10-02T10:00:00Z", SignedBundleCatalog.generatedAtOf(pythonSigned))
        assertEquals(null, SignedBundleCatalog.generatedAtOf("""{"bundles":[]}"""))
    }

    @Test fun fetchVerifiesBeforeReturningAndExplainsFailures() {
        fun resp(code: Int, body: String) = castbridge.core.net.HttpLite.Response(code, body, emptyMap())
        var asked = ""
        val ok = ServerBundleCatalog.fetch("bridge.sti-cm.com", listOf(pub)) { asked = it; resp(200, signed()) }
        assertEquals("https://bridge.sti-cm.com/api/v1/catalog/bundles", asked)
        assertEquals(2, ok.catalog.bundles.size)
        fun msg(get: (String) -> castbridge.core.net.HttpLite.Response) = assertFailsWith<SignedBundleCatalog.Refused> { ServerBundleCatalog.fetch(null, listOf(pub), null, get) }.message!!
        assertTrue(msg { throw java.net.UnknownHostException("x") }.contains("injoignable"))
        assertTrue(msg { resp(404, """{"message":"Catalogue des bouquets non publié sur le serveur"}""") }.contains("pas encore de catalogue"))
        assertTrue(msg { resp(503, """{"message":"Catalogue des bouquets illisible sur le serveur"}""") }.contains("illisible sur le serveur"))
        assertTrue(msg { resp(200, signed().replace("\"rentalDays\":14", "\"rentalDays\":99")) }.contains("Signature"))
        assertTrue(assertFailsWith<SignedBundleCatalog.Refused> { ServerBundleCatalog.fetch("http://evil.example", listOf(pub)) { resp(200, signed()) } }.message!!.contains("invalide"))
    }
}
