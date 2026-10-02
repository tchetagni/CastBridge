package castbridge.desktop

import castbridge.core.lots.Bundle
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.SignedBundleCatalog
import castbridge.core.net.HttpLite
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** « catalogue-serveur »: a TEST key signs a catalogue, the server answer is injected (no network). */
class ServerCatalogCliTest {
    private val dir = Files.createTempDirectory("activation-desktop-srvcat").toFile().also { it.deleteOnExit() }
    private val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val pub = Base64.getEncoder().encodeToString(kp.public.encoded.takeLast(32).toByteArray())

    private fun signed(at: String, days: Int): String {
        val bundles = listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2"), "Classe CM2", 9000, days))
        val sig = Signature.getInstance("Ed25519").apply { initSign(kp.private); update(SignedBundleCatalog.canonicalPayload(at, bundles).toByteArray()) }.sign()
        return """{"generatedAt":"$at","signature":"${Base64.getEncoder().encodeToString(sig)}","bundles":[{"id":"classe-cm2","type":"classe","lots":["learn:cm2"],"title":"Classe CM2","rawBytes":9000,"rentalDays":$days}]}"""
    }

    private fun run(body: () -> HttpLite.Response): Triple<Int, String, String> {
        val o = ByteArrayOutputStream(); val e = ByteArrayOutputStream()
        val env = Env(PrintStream(o, true, "UTF-8"), PrintStream(e, true, "UTF-8"), httpGet = { body() })
        val code = Cli(env).run(listOf("catalogue-serveur", "--cle-publique", pub, "--dossier", dir.path))
        return Triple(code, o.toString("UTF-8"), e.toString("UTF-8"))
    }

    private fun ok(b: String) = HttpLite.Response(200, b, emptyMap())
    private fun kept() = File(dir, "bundles-catalog.json").readText()

    @Test fun downloadsVerifiesKeepsAndRefusesBadOrOlderOnes() {
        val r = run { ok(signed("2026-10-02T10:00:00Z", 14)) }
        assertEquals(0, r.first, r.third); assertTrue(r.second.contains("Catalogue du 02/10/2026 : 1 bouquets (signé)"), r.second)
        val before = kept()
        assertEquals(14, BundleCatalog.parse(before).find("classe-cm2")!!.rentalDays)
        val bad = run { ok(signed("2026-10-03T10:00:00Z", 14).replace("\"rentalDays\":14", "\"rentalDays\":365")) }
        assertEquals(1, bad.first); assertTrue(bad.third.contains("Signature"), bad.third); assertEquals(before, kept())
        val old = run { ok(signed("2026-09-01T10:00:00Z", 365)) }
        assertEquals(1, old.first); assertTrue(old.third.contains("plus ancien"), old.third); assertEquals(before, kept())
        val down = run { throw java.io.IOException("réseau") }
        assertEquals(1, down.first); assertTrue(down.third.contains("injoignable"), down.third); assertEquals(before, kept())
        assertEquals(0, run { ok(signed("2026-10-05T10:00:00Z", 21)) }.first)
        assertEquals(21, BundleCatalog.parse(kept()).find("classe-cm2")!!.rentalDays)
        assertEquals(21, ServerCatalogStore.readCatalog(dir, listOf(pub)).find("classe-cm2")!!.rentalDays)
    }

    @Test fun nothingKeptMeansExplicitMessage() {
        val e = assertFailsWith<SignedBundleCatalog.Refused> { ServerCatalogStore.readVerified(File(dir, "vide"), listOf(pub)) }
        assertTrue(e.message!!.contains("catalogue-serveur"))
    }
}
