package castbridge.core.owner

import castbridge.core.owner.ShareRequestPlan.Place
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.trust.TvDeviceRequest
import castbridge.core.trust.TvDeviceRequestTexts
import java.io.File
import kotlin.test.*

/**
 * R-50 (confirmé en production par le propriétaire, 2026-10-07) : « Partager la demande » ne marchait pas sur WhatsApp. L'écran envoyait UNE intention `image/png` avec `EXTRA_STREAM` (le QR) ET
 * `EXTRA_TEXT` (la demande) : WhatsApp envoie l'image et abandonne le texte, alors que le texte est ce que lisent les outils de l'agent ; en plus l'autorisation de lecture de l'URI n'était posée ni en
 * `ClipData` ni sur le sélecteur. La règle : « Partager la demande » = le texte SEUL ; « Partager le code QR » = un bouton à part, seulement quand le QR tient, l'image avec l'autorisation de lecture
 * sur l'intention ET sur le sélecteur.
 */
class ShareRequestPlanTest {
    private val fp1 = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9"))
    private val fp3 = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd", FactorKind.BLUETOOTH to "00112233445566778899aabbccddeeff"))
    private val sig = ByteArray(32) { (it * 3 + 2).toByte() }
    private val pub = ByteArray(32) { (it * 7 + 1).toByte() }
    private fun request(fp: Fingerprints, install: ByteArray?): TvDeviceRequest =
        (LockedRequestRoute.parse(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, install, sig)) as DeviceRequestParse.Ok).request

    /** La TV courante : 3 facteurs + `install=` = 343 octets, le QR ne tient pas (version 10, 271 octets au plus). */
    private val usual = request(fp3, pub)
    /** Deux demandes dont le QR tient : 1 facteur avec `install=` (239 octets) ; 3 facteurs sans `install=` (263 octets). */
    private val small = request(fp1, pub)
    private val bare = request(fp3, null)

    private val subject = LockedRequestRoute.SHARE_SUBJECT
    private fun text(r: TvDeviceRequest) = LockedRequestRoute.shareText(r)
    private fun requestPlan(r: TvDeviceRequest) = ShareRequestPlan.request(subject, text(r))
    private fun qrPlan(r: TvDeviceRequest) = ShareRequestPlan.qr(subject, text(r), LockedRequestRoute.qr(r))

    // ---- « Partager la demande » : le texte seul

    @Test fun theRequestShareIsThePlainTextAloneNeverAStream() {
        for (r in listOf(usual, small, bare)) {
            val p = requestPlan(r)
            assertEquals("text/plain", p.mime, "un partage image + texte : WhatsApp envoie l'image et abandonne le texte (R-50)")
            assertFalse(p.stream, "jamais de flux avec la demande")
            assertNull(p.image, "jamais d'image avec la demande, même quand le QR tient")
            assertTrue(p.clipData.isEmpty() && p.grantFlag.isEmpty(), "pas d'URI, donc rien à autoriser")
        }
    }

    @Test fun theRequestShareCarriesTheCompleteRequestAndTheSubjectUnchanged() {
        for (r in listOf(usual, small, bare)) {
            val p = requestPlan(r)
            assertEquals(r.fullText(), p.text, "la demande complète, octet pour octet")
            assertEquals(subject, p.subject)
        }
        assertEquals("Demande d'activation CastBridge-TV", requestPlan(usual).subject, "le sujet de l'e-mail n'a pas changé")
        assertTrue(requestPlan(usual).text.lines().any { it.startsWith("install=x25519|") }, "install= est dans ce que l'agent reçoit : un essai en enveloppe v2 l'exige")
        assertTrue(requestPlan(bare).text.lines().none { it.startsWith("install=") }, "rien d'inventé quand la TV n'a pas sa clé")
    }

    @Test fun theDeviceRequestScreenSharesTheSameWayWithItsOwnSubjectAndTitle() {
        val p = ShareRequestPlan.request(TvDeviceRequestTexts.TITLE, usual.fullText(), TvDeviceRequestTexts.SHARE)
        assertEquals("text/plain", p.mime); assertFalse(p.stream); assertNull(p.image)
        assertEquals("Demande d'appareil de la TV", p.subject, "le sujet de cet écran est inchangé")
        assertEquals("Partager la demande complète", p.chooserTitle, "le titre de la feuille de partage de cet écran est inchangé")
        assertEquals(usual.fullText(), p.text)
    }

    // ---- « Partager le code QR » : un bouton à part, seulement quand le QR tient

    @Test fun theQrShareIsASeparateImageShareAndExistsOnlyWhenTheQrFits() {
        assertNull(LockedRequestRoute.qr(usual), "3 facteurs avec install= : 343 octets, pas de QR")
        assertNull(qrPlan(usual), "pas de QR, donc pas de bouton « Partager le code QR »")
        assertNull(ShareRequestPlan.qr(subject, text(small), null), "sans QR, aucun partage d'image, jamais d'image vide")
        for (r in listOf(small, bare)) {
            val qr = assertNotNull(LockedRequestRoute.qr(r), "ce QR tient")
            val p = assertNotNull(ShareRequestPlan.qr(subject, text(r), qr), "le QR tient : le bouton existe")
            assertEquals("image/png", p.mime)
            assertTrue(p.stream)
            assertSame(qr, p.image, "c'est l'image du QR de cette demande")
            assertNotEquals(requestPlan(r).mime, p.mime, "deux partages distincts : le texte d'un côté, l'image de l'autre")
            assertNotEquals(requestPlan(r).chooserTitle, p.chooserTitle)
        }
    }

    @Test fun theQrShareLaysTheUriGrantOnTheIntentAndOnTheChooser() {
        val p = assertNotNull(qrPlan(small))
        assertEquals(setOf(Place.INTENT, Place.CHOOSER), p.clipData, "ClipData de l'URI sur l'intention ET sur le sélecteur (createChooser ne la recopie que si l'intention en a une)")
        assertEquals(setOf(Place.INTENT, Place.CHOOSER), p.grantFlag, "FLAG_GRANT_READ_URI_PERMISSION sur l'intention ET sur le sélecteur")
    }

    @Test fun theQrShareKeepsTheRequestTextAndTheSubjectAsAnIndication() {
        for (r in listOf(small, bare)) {
            val p = assertNotNull(qrPlan(r))
            assertEquals(r.fullText(), p.text, "le texte de la demande reste en EXTRA_TEXT à titre indicatif (un e-mail le prend en corps)")
            assertEquals(subject, p.subject)
        }
    }

    // ---- les libellés et ce qui ne fuit pas

    @Test fun theButtonsAndTheSheetTitlesAreTheOnesTheOwnerSees() {
        assertEquals("Partager la demande", ShareRequestPlan.REQUEST_BUTTON)
        assertEquals("Partager le code QR", ShareRequestPlan.QR_BUTTON)
        assertEquals("Copier", ShareRequestPlan.COPY_BUTTON)
        assertEquals(ShareRequestPlan.REQUEST_BUTTON, requestPlan(usual).chooserTitle, "la feuille de partage porte le nom du bouton")
        assertEquals(ShareRequestPlan.QR_BUTTON, assertNotNull(qrPlan(small)).chooserTitle)
        val all = listOf(ShareRequestPlan.REQUEST_BUTTON, ShareRequestPlan.QR_BUTTON, ShareRequestPlan.COPY_BUTTON, ShareRequestPlan.QR_FAILED)
        assertEquals(all.size, all.toSet().size, "quatre textes distincts")
        for (t in all) assertFalse(Regex("sender|receiver|émetteur|récepteur", RegexOption.IGNORE_CASE).containsMatchIn(t), t)
    }

    @Test fun aPlanNeverPrintsTheRequestInItsToString() {
        for (p in listOf(requestPlan(small), assertNotNull(qrPlan(small)))) {
            val s = p.toString()
            assertFalse("code=" in s || "factor=" in s || "install" in s || small.code in s || "0a1b2c3d" in s, "ni la demande ni ses empreintes dans un texte d'état : $s")
        }
    }

    // ---- les écrans passent par la règle (la partie Android n'a pas de test JVM : ses crochets sont tenus sur la source)

    private fun source(relative: String): String {
        val f = File("../$relative").takeIf { it.exists() } ?: File(relative)
        assertTrue(f.exists(), "source introuvable : $relative (le garde ne doit jamais passer en silence)")
        return f.readText()
    }
    private fun codeOnly(s: String) = s.lineSequence().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") || it.trim().startsWith("/*") }.joinToString("\n")

    private val activate = "sender/src/main/kotlin/castbridge/sender/ActivateTvActivity.kt"
    private val deviceRequest = "sender/src/main/kotlin/castbridge/sender/TvDeviceRequestActivity.kt"
    private val mapper = "sender/src/main/kotlin/castbridge/sender/ShareRequestIntents.kt"

    @Test fun neitherScreenBuildsAShareIntentByHand() {
        for (f in listOf(activate, deviceRequest)) {
            val hit = Regex("""ACTION_SEND|EXTRA_STREAM|EXTRA_TEXT|EXTRA_SUBJECT|createChooser|setType\(""").find(codeOnly(source(f)))
            assertNull(hit, "$f construit lui-même un partage (« ${hit?.value} ») : il doit passer par ShareRequestPlan et ShareRequestIntents (R-50)")
        }
    }

    @Test fun bothScreensTakeTheirSharesFromThePlan() {
        val a = codeOnly(source(activate)); val d = codeOnly(source(deviceRequest))
        for (needle in listOf("ShareRequestPlan.request(", "ShareRequestPlan.qr(", "ShareRequestPlan.REQUEST_BUTTON", "ShareRequestPlan.QR_BUTTON", "ShareRequestPlan.COPY_BUTTON", "ShareRequestIntents.chooser("))
            assertTrue(needle in a, "« Activer la TV » n'utilise plus $needle")
        assertTrue("ShareRequestPlan.request(" in d && "ShareRequestIntents.chooser(" in d, "« Demande d'appareil » partage par la même règle")
        assertFalse("ShareRequestPlan.qr(" in d, "« Demande d'appareil » n'envoie jamais d'image")
    }

    @Test fun theMapperLaysTheGrantWhereThePlanSaysOnTheIntentAndOnTheChooser() {
        val m = codeOnly(source(mapper))
        for (needle in listOf("Intent.createChooser(", "plan.clipData", "plan.grantFlag", "Place.INTENT", "Place.CHOOSER", "send.clipData", "send.addFlags(", "chooser.clipData", "chooser.addFlags(", "ClipData.newRawUri(", "Intent.FLAG_GRANT_READ_URI_PERMISSION"))
            assertTrue(needle in m, "ShareRequestIntents ne pose plus « $needle »")
        assertTrue("uri.takeIf { plan.stream }" in m, "un plan de texte ne porte jamais de flux, même si un appelant passe un URI : l'URI n'est lu que sous la condition du plan")
        assertEquals(1, Regex("""EXTRA_STREAM""").findAll(m).count(), "le flux n'est posé qu'à un seul endroit, et seulement quand le plan joint une image")
    }
}
