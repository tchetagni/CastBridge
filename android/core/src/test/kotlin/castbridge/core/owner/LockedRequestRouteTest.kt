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
    /** What the locked route of the TV gives: the COMPLETE request, `install=` included (the installation's key is PUBLIC: ACT-F4 amended on 2026-10-07). */
    private val routeText = OwnerFrames.deviceInfo(code, fp3, pub, sig)
    /** The same from a TV whose installation key is not ready yet (or an older one): no `install=` line. */
    private val routeTextNoInstall = OwnerFrames.deviceInfo(code, fp3, null, sig)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun request(text: String = routeText) = (LockedRequestRoute.parse(text) as DeviceRequestParse.Ok).request

    @Test fun theTextOfTheLockedRouteIsReadBackToTheSameRequest() {
        val r = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, routeText)).request
        assertEquals(code, r.code)
        assertEquals(DeviceIdentity.kFor(3), r.k)
        assertEquals(fp3.byKind.toList(), r.factors)
        assertEquals(routeText, r.fullText(), "octet pour octet")
        assertEquals(routeText, LockedRequestRoute.shareText(r), "ce qui est partagé est la demande complète")
        assertEquals(hex(pub), r.installHex, "la clé publique d'installation est lue")
        assertEquals(hex(sig), r.installSigHex)
    }

    @Test fun aTvWithoutItsInstallationKeyIsReadWithoutItAndNothingIsInvented() {
        val r = request(routeTextNoInstall)
        assertNull(r.installHex)
        assertEquals(routeTextNoInstall, r.fullText()); assertEquals(routeTextNoInstall, LockedRequestRoute.shareText(r))
        assertTrue(LockedRequestRoute.shareText(r).lines().none { it.startsWith("install=") })
    }

    @Test fun crlfBomAndBlankLinesAreTolerated() {
        val noisy = bom + routeText.replace("\n", "\r\n\r\n") + "\r\n"
        assertEquals(routeText, request(noisy).fullText())
    }

    @Test fun aJsonAnswerOfTheShapeOfTheFullApiIsAcceptedToo() {
        // tolerance toward the TV side: the same five fields as « /api/tv/device-request » (docs/TV-DEMANDE-APPAREIL.md), read by the same strict parser
        val json = castbridge.core.tv.TvDeviceRequestApi.json(routeText)!!
        val r = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.interpret(200, json)).request
        assertEquals(routeText, r.fullText())
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(200, json.replace(code, "AAAA-AAAA-AAAA-AAAA")))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.interpret(200, "{ pas du json"))
    }

    @Test fun theSharedTextIsTheCompleteRequestWithTheInstallLineWhenTheTvGaveIt() {
        val r = request(routeText)
        val shared = LockedRequestRoute.shareText(r)
        assertEquals(r.fullText(), shared)
        assertTrue("install=x25519|${hex(pub)}" in shared.lines(), "la clé publique d'installation est dans ce que l'agent reçoit : un essai en enveloppe v2 l'exige\n$shared")
        assertTrue("install_sig=ed25519|${hex(sig)}" in shared.lines())
        assertTrue(shared.lines().all { it.startsWith("code=") || it.startsWith("k=") || it.startsWith("factor=") || it.startsWith("install=x25519|") || it.startsWith("install_sig=") }, shared)
        // the licence server's form is the same text without that line, and nothing else changes
        assertEquals(shared.lines().filterNot { it.startsWith("install=") }.joinToString("\n"), r.serverText())
        assertEquals(DeviceRequestText.forServer(shared), r.serverText())
    }

    @Test fun unknownLinesAreIgnoredAndNeverShared() {
        val r = request(routeText + "\nsecret=CANARY-9f3\nname=SMART_TV\nfuture=1")
        for (t in listOf(LockedRequestRoute.shareText(r), r.fullText(), r.viewLines().joinToString("\n"))) {
            assertFalse("CANARY" in t); assertFalse("SMART_TV" in t); assertFalse("future" in t)
        }
        assertEquals(routeText, LockedRequestRoute.shareText(r))
        assertEquals(routeTextNoInstall, LockedRequestRoute.shareText(request(routeTextNoInstall + "\ninstall=rsa|0123")), "an install= of another algorithm is an unknown line: never shared")
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
            routeTextNoInstall + "\ninstall=x25519|zz", routeTextNoInstall + "\ninstall=x25519|" + hex(pub).dropLast(2),         // an install= that is not 64 lower-case hex
            routeTextNoInstall + "\ninstall=x25519|" + hex(pub).uppercase(), routeText + "\ninstall=x25519|" + hex(pub),         // upper case; doubled
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
        assertTrue("install=x25519|${hex(pub)}" in LockedRequestRoute.shareText(r).lines(), "the full API already gave the public key: the shared text is the complete request here too")
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

    @Test fun theQrCarriesTheCompleteSharedTextWhenItFitsAndNothingWhenItDoesNot() {
        // a TV without its installation key yet: 3 factors, 263 bytes, version 10 at correction L
        val bare = request(routeTextNoInstall)
        val q = assertNotNull(LockedRequestRoute.qr(bare), "3 facteurs sans install= : ${LockedRequestRoute.shareText(bare).length} octets, tient en version 10 (correction L)")
        assertEquals(QrCode.encode(LockedRequestRoute.shareText(bare), q.ecl).size, q.size)
        // one factor WITH install=: 239 bytes, it fits, and the QR carries the COMPLETE text (the installation's public key included)
        val one = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9"))
        val small = request(OwnerFrames.deviceInfo(DeviceCode.of(one), one, pub, sig))
        val smallText = LockedRequestRoute.shareText(small)
        assertTrue("install=x25519|${hex(pub)}" in smallText.lines(), smallText)
        val qs = assertNotNull(LockedRequestRoute.qr(small), "1 facteur avec install= : ${smallText.length} octets")
        assertEquals(QrCode.encode(smallText, qs.ecl).size, qs.size)
        // the complete text of a usual TV (3 factors + install= = 343 bytes) is longer than a version-10 QR holds: no QR, never a truncated one; the shared text stays the way
        val r = request()
        assertTrue(LockedRequestRoute.shareText(r).toByteArray().size > 271, "343 octets > 271 (version 10, correction L)")
        assertNull(LockedRequestRoute.qr(r), "3 facteurs avec install= : le QR ne tient plus, le texte partagé reste la voie")
        assertEquals(routeText, LockedRequestRoute.shareText(r))
        val five = request(OwnerFrames.deviceInfo(DeviceCode.of(fp5), fp5, null, sig))
        assertNull(LockedRequestRoute.qr(five), "5 facteurs : trop long pour la version 10, le texte partagé reste la seule voie (pas de QR tronqué)")
        assertNotNull(LockedRequestRoute.shareText(five))
    }

    @Test fun theShareSubjectNamesTheAppWithoutTheOldWords() {
        assertTrue("CastBridge-TV" in LockedRequestRoute.SHARE_SUBJECT)
        assertFalse(LockedRequestRoute.SHARE_SUBJECT.contains("sender", true) || LockedRequestRoute.SHARE_SUBJECT.contains("receiver", true))
        assertEquals("/api/activation/device-request", LockedRequestRoute.PATH)
    }
}
