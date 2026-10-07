package castbridge.core.owner

import castbridge.core.quiz.QrCode
import castbridge.core.trust.DeviceRequestParse
import kotlin.test.*

/**
 * « Demande d'appareil » lue depuis le téléphone par la route de la TV verrouillée (`GET /api/activation/device-request`, code requis, texte brut) :
 * lecture stricte du texte, réponses de la TV traduites en causes, texte partagé et QR (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F4).
 */
class LockedRequestRouteTest {
    private val fp3 = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd", FactorKind.BLUETOOTH to "00112233445566778899aabbccddeeff"))
    private val fp5 = Fingerprints(FactorKind.values().associateWith { k -> k.ordinal.toString().repeat(32) })
    private val sig = ByteArray(32) { (it * 3 + 2).toByte() }
    private val pub = ByteArray(32) { (it * 7 + 1).toByte() }
    private val code = DeviceCode.of(fp3)
    private val bom = 0xFEFF.toChar().toString()
    /** What the locked route of the TV gives: the text for the server, without the `install=` line. */
    private val routeText = OwnerFrames.deviceInfo(code, fp3, null, sig)

    private fun request(text: String = routeText) = (LockedRequestRoute.parse(text) as DeviceRequestParse.Ok).request

    @Test fun theTextOfTheLockedRouteIsReadBackToTheSameRequest() {
        val r = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, routeText)).request
        assertEquals(code, r.code)
        assertEquals(DeviceIdentity.kFor(3), r.k)
        assertEquals(fp3.byKind.toList(), r.factors)
        assertEquals(routeText, r.serverText(), "octet pour octet")
        assertEquals(routeText, LockedRequestRoute.shareText(r))
        assertNull(r.installHex)
    }

    @Test fun crlfBomAndBlankLinesAreTolerated() {
        val noisy = bom + routeText.replace("\n", "\r\n\r\n") + "\r\n"
        assertEquals(routeText, request(noisy).serverText())
    }

    @Test fun aJsonAnswerOfTheShapeOfTheFullApiIsAcceptedToo() {
        // tolerance toward the TV side: the same five fields as « /api/tv/device-request » (docs/TV-DEMANDE-APPAREIL.md), read by the same strict parser
        val json = castbridge.core.tv.TvDeviceRequestApi.json(routeText)!!
        val r = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, json)).request
        assertEquals(routeText, r.serverText())
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(200, json.replace(code, "AAAA-AAAA-AAAA-AAAA")))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(200, "{ pas du json"))
    }

    @Test fun theSharedTextNeverHasTheInstallLineEvenWhenTheTvSentIt() {
        val withInstall = OwnerFrames.deviceInfo(code, fp3, pub, sig)
        val r = request(withInstall)
        assertTrue("install=x25519|" in r.fullText(), "the console prefill keeps the public key when the TV gave it")
        for (t in listOf(LockedRequestRoute.shareText(r), r.serverText())) {
            assertFalse("install=" in t.replace("install_sig=", ""), t)
            assertTrue("install_sig=ed25519|" in t)
        }
        assertTrue(LockedRequestRoute.shareText(r).lines().all { it.startsWith("code=") || it.startsWith("k=") || it.startsWith("factor=") || it.startsWith("install_sig=") })
    }

    @Test fun unknownLinesAreIgnoredAndNeverShared() {
        val r = request(routeText + "\nsecret=CANARY-9f3\nname=SMART_TV\nfuture=1")
        for (t in listOf(LockedRequestRoute.shareText(r), r.fullText(), r.viewLines().joinToString("\n"))) {
            assertFalse("CANARY" in t); assertFalse("SMART_TV" in t); assertFalse("future" in t)
        }
        assertEquals(routeText, LockedRequestRoute.shareText(r))
    }

    @Test fun anAnswerThatIsNotARequestIsUnreadableAndSaysWhy() {
        val other = Fingerprints(mapOf(FactorKind.FLASH to "ffffffffffffffffffffffffffff0001"))
        val bad = listOf(
            "", "pas une demande", "<html>403</html>", "code=" + code,
            routeText.replace("code=$code", "code=AAAA-AAAA-AAAA-AAAA"),                              // wrong check character
            routeText.replace("code=$code", "code=${DeviceCode.of(other)}"),                          // a real code, but not the one of these fingerprints
            routeText.replace("0a1b2c3d4e5f60718293a4b5c6d7e8f9", "0a1b2c3d4e5f6071"),                // fingerprint too short
            routeText.replace("0a1b2c3d4e5f60718293a4b5c6d7e8f9", "0A1B2C3D4E5F60718293A4B5C6D7E8F9"), // not lower case hex
            routeText.replace("k=2", "k=9"), routeText.replace("k=2", "k=0"),
            routeText.replace("factor=FLASH|", "factor=EVIL|"),
            routeText + "\nligne sans signe egal",
        )
        for (b in bad) {
            val reply = LockedRequestRoute.interpret(200, b)
            val u = assertIs<LockedRequestRoute.Reply.Unreadable>(reply, b)
            assertTrue(u.message.isNotBlank() && u.message.first().isUpperCase(), "message français : ${u.message}")
            assertFalse(code in u.message)
        }
    }

    @Test fun fingerprintsMustBeThirtyTwoLowerCaseHexEvenWhenTheCodeAgreesWithThem() {
        // the code is computed from these very strings: only the shape check can refuse them
        for (bad in listOf("<b>bold</b>-not-hex-but-32-chars!!", "0A1B2C3D4E5F60718293A4B5C6D7E8F9", "0a1b2c3d4e5f60718293a4b5c6d7e8f", "0a1b2c3d4e5f60718293a4b5c6d7e8f9a")) {
            val junk = Fingerprints(mapOf(FactorKind.FLASH to bad))
            val reply = LockedRequestRoute.interpret(200, OwnerFrames.deviceInfo(DeviceCode.of(junk), junk, null, sig))
            assertIs<LockedRequestRoute.Reply.Unreadable>(reply, bad)
        }
        val fine = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9"))
        assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, OwnerFrames.deviceInfo(DeviceCode.of(fine), fine, null, sig)))
    }

    @Test fun theRequestOfAnActivatedOrTrialTvIsReadThroughTheExistingJsonRoute() {
        // a TV the phone is linked to (full API): GET /api/tv/device-request with the code as PIN
        val json = castbridge.core.tv.TvDeviceRequestApi.json(OwnerFrames.deviceInfo(code, fp3, pub, sig))!!
        val r = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpretFull(200, json)).request
        assertEquals(code, r.code); assertNotNull(r.installHex)
        assertFalse("install=" in LockedRequestRoute.shareText(r).replace("install_sig=", ""))
        assertEquals(LockedRequestRoute.Reply.CodeRefused, LockedRequestRoute.interpretFull(401, """HTTP 401: {"error":"bad pin"}"""))
        assertEquals(LockedRequestRoute.Reply.LockedOut(12), LockedRequestRoute.interpretFull(401, """{"error":"locked","retryAfter":12}"""))
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.interpretFull(404, "{}"))
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.interpretFull(403, "{}"))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpretFull(200, "pas du json"))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpretFull(500, json))
    }

    @Test fun anOversizeAnswerIsRefusedWithoutBeingParsed() {
        val big = routeText + "\n" + "x=".padEnd(LockedRequestRoute.MAX_CHARS, 'y')
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(200, big))
        assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, routeText))
    }

    @Test fun everyStatusOfTheLockedRouteHasItsOwnReply() {
        assertEquals(LockedRequestRoute.Reply.CodeRefused, LockedRequestRoute.interpret(401, """HTTP 401: {"error":"bad pin"}"""))
        assertEquals(LockedRequestRoute.Reply.LockedOut(37), LockedRequestRoute.interpret(401, """HTTP 401: {"error":"locked","retryAfter":37}"""))
        assertEquals(LockedRequestRoute.Reply.LockedOut(60), LockedRequestRoute.interpret(401, """{"error":"locked"}"""), "60 s when the TV does not say")
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.interpret(403, """{"error":"Usage soumis à autorisation : seule l'activation est ouverte sur cette TV","locked":true}"""))
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.interpret(404, "{}"))
        assertEquals(LockedRequestRoute.Reply.TermsNotAccepted, LockedRequestRoute.interpret(409, "{}"))
        assertEquals(LockedRequestRoute.Reply.Closed, LockedRequestRoute.interpret(429, "{}"))
        for (s in listOf(400, 500, 503, 302)) assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(s, routeText), "$s")
    }

    @Test fun anAnswerNeverEchoesTheCodeOrTheKeyInItsMessages() {
        val canary = "482913"
        for (r in listOf(LockedRequestRoute.interpret(401, """{"error":"bad pin","echo":"$canary"}"""), LockedRequestRoute.interpret(500, canary), LockedRequestRoute.interpret(200, canary + "\n" + canary)))
            assertFalse(canary in r.toString(), r.toString())
    }

    @Test fun theQrCarriesTheSharedTextWhenItFitsAndNothingWhenItDoesNot() {
        val r = request()
        val q = assertNotNull(LockedRequestRoute.qr(r), "3 facteurs : ${LockedRequestRoute.shareText(r).length} octets, tient en version 10 (correction L)")
        assertEquals(QrCode.encode(LockedRequestRoute.shareText(r), q.ecl).size, q.size)
        val five = request(OwnerFrames.deviceInfo(DeviceCode.of(fp5), fp5, null, sig))
        assertNull(LockedRequestRoute.qr(five), "5 facteurs : trop long pour la version 10, le texte partagé reste la seule voie (pas de QR tronqué)")
        assertNotNull(LockedRequestRoute.shareText(five))
        // a QR is made from the text for the server only
        val withInstall = request(OwnerFrames.deviceInfo(code, fp3, pub, sig))
        assertEquals(LockedRequestRoute.qr(r)!!.size, LockedRequestRoute.qr(withInstall)!!.size)
    }

    @Test fun theShareSubjectNamesTheAppWithoutTheOldWords() {
        assertTrue("CastBridge-TV" in LockedRequestRoute.SHARE_SUBJECT)
        assertFalse(LockedRequestRoute.SHARE_SUBJECT.contains("sender", true) || LockedRequestRoute.SHARE_SUBJECT.contains("receiver", true))
        assertEquals("/api/activation/device-request", LockedRequestRoute.PATH)
    }
}
