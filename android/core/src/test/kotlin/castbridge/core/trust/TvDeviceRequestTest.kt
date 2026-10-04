package castbridge.core.trust

import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.OwnerFrames
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvDeviceRequestApi
import java.io.IOException
import kotlin.test.*

/**
 * « Demande d'appareil de la TV » (docs/TV-DEMANDE-APPAREIL.md): the phone reads ONLY what the owner's tools need to build an activation key.
 * Pure parts: parser of the TV's answer, texts, and the authorisation gate (not authorised: nothing is sent).
 */
class TvDeviceRequestTest {
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd"))
    private val pub = ByteArray(32) { (it * 7 + 1).toByte() }
    private val code = DeviceCode.of(fp)
    /** What `ActivationCenter.requestText()` gives on the TV (common vector: the same function builds it). */
    private val tvText = OwnerFrames.deviceInfo(code, fp, pub)
    private val tvJson = TvDeviceRequestApi.json(tvText)!!
    private fun parsed(json: String = tvJson) = (TvDeviceRequestParser.parse(json) as DeviceRequestParse.Ok).request

    @Test fun mainCopyIsByteForByteWhatTheTvProduces() {
        assertEquals(tvText, parsed().fullText())
        assertTrue(tvText.contains("install=x25519|"))
    }

    @Test fun fullTextIsAcceptedByTheOwnersParser() {
        val r = DeviceRequest.parse(parsed().fullText())
        assertEquals(code, r.code); assertEquals(fp, r.factors); assertContentEquals(pub, r.installPub)
    }

    @Test fun serverCopyHasNoInstallLineAndIsAcceptedByTheOwnersParser() {
        val t = parsed().serverText()
        assertFalse("install" in t)
        assertTrue(t.lines().all { it.startsWith("code=") || it.startsWith("k=") || it.startsWith("factor=") }, "the server refuses any other line: $t")
        val r = DeviceRequest.parse(t)
        assertEquals(code, r.code); assertNull(r.installPub)
    }

    @Test fun oldTvWithoutInstallKeyIsTolerated() {
        val old = TvDeviceRequestApi.json(OwnerFrames.deviceInfo(code, fp, null))!!
        val r = parsed(old)
        assertNull(r.installHex)
        assertEquals(OwnerFrames.deviceInfo(code, fp, null), r.fullText())
    }

    @Test fun unknownFieldsAreIgnoredAndNeverCopied() {
        val canary = "CANARY-SECRET-9f3"
        val extended = tvJson.removeSuffix("}") + ",\"pin\":\"123456\",\"token\":\"cbk_$canary\",\"future\":{\"a\":[1,2]},\"name\":\"$canary\"}"
        val r = parsed(extended)
        for (t in listOf(r.fullText(), r.serverText(), r.viewLines().joinToString("\n"))) {
            assertFalse(canary in t); assertFalse("123456" in t); assertFalse("cbk_" in t)
        }
        assertEquals(tvText, r.fullText())
    }

    @Test fun oversizeAnswerIsRefused() {
        val big = tvJson.removeSuffix("}") + ",\"pad\":\"" + "x".repeat(TvDeviceRequestParser.MAX_CHARS) + "\"}"
        assertIs<DeviceRequestParse.Refused>(TvDeviceRequestParser.parse(big))
        assertIs<DeviceRequestParse.Ok>(TvDeviceRequestParser.parse(tvJson))
    }

    @Test fun missingOrBrokenFieldsAreRefusedWithAFrenchMessage() {
        val bad = listOf(
            "", "pas du json", "[]", "{}",
            tvJson.replace("\"code\":\"$code\"", "\"code\":\"AAAA-AAAA-AAAA-AAAA\""),
            tvJson.replace(Regex("\"factors\":\\[.*?\\]"), "\"factors\":[]"),
            tvJson.replace(Regex("\"k\":\\d+"), "\"k\":\"deux\""),
            tvJson.replace("\"FLASH\"", "\"EVIL\""),
            tvJson.replace("0a1b2c3d4e5f60718293a4b5c6d7e8f9", "abc\\ndef\\u0000"),
            tvJson.replace("0a1b2c3d4e5f60718293a4b5c6d7e8f9", "<b>0123456789abcdef01234567</b>"),
            tvJson.replace(Regex("\"install\":\"[0-9a-f]+\""), "\"install\":\"zz\""),
        )
        for (b in bad) {
            val r = TvDeviceRequestParser.parse(b)
            assertIs<DeviceRequestParse.Refused>(r, b)
            assertTrue(r.message.isNotBlank(), b)
        }
    }

    @Test fun aTvWhoseCodeMatchesHostileFingerprintsIsStillRefused() {
        // a consistent code over non-hex fingerprints passes every checksum: only the strict hex check keeps the text clean
        for (evil in listOf("<script>alert(1)</script>", "line1\nsecret=1", "a\"b", "0a1b2c3d4e5f60718293a4b5c6d7e8f9ff")) {
            val hostile = Fingerprints(mapOf(FactorKind.FLASH to evil))
            val json = """{"code":"${DeviceCode.of(hostile)}","k":1,"factors":[{"type":"FLASH","fingerprint":${castbridge.core.net.JsonLite.quote(evil)}}],"install":null}"""
            assertIs<DeviceRequestParse.Refused>(TvDeviceRequestParser.parse(json), evil)
        }
    }

    @Test fun stringsAreNeverRenderedUnescaped() {
        // whatever the TV sends, the only characters that reach the text are the strict ones
        val r = parsed()
        val all = r.fullText() + r.serverText() + r.viewLines().joinToString("\n")
        assertTrue(r.factors.all { (_, h) -> h.matches(Regex("[0-9a-f]{32}")) })
        assertFalse('<' in all || '"' in all || '\\' in all || '\u0000' in all)
    }

    @Test fun everyDisplayedLineIsFrenchAndKeepsTheFullFingerprints() {
        val lines = parsed().viewLines()
        assertTrue(lines.any { it.startsWith("Code d'appareil") && code in it })
        assertTrue(lines.any { it.startsWith("Seuil") || it.startsWith("Facteurs") })
        for ((_, h) in parsed().factors) assertTrue(lines.any { h in it }, "full fingerprint $h shown")
        assertTrue(lines.any { it.contains("clé d'installation", ignoreCase = true) })
        for (l in lines) assertFalse(Regex("\\b(copy|share|the|device|request)\\b", RegexOption.IGNORE_CASE).containsMatchIn(l), l)
    }

    @Test fun textsAreFrenchAndNamedAsTheOwnerAsked() {
        assertEquals("Demande d'appareil de la TV", TvDeviceRequestTexts.TITLE)
        assertEquals("Copier la demande complète", TvDeviceRequestTexts.COPY_FULL)
        assertEquals("Copier pour le serveur", TvDeviceRequestTexts.COPY_SERVER)
        assertEquals("Partager la demande complète", TvDeviceRequestTexts.SHARE)
        assertTrue("install=" in TvDeviceRequestTexts.EXPLAIN_SERVER && "serveur" in TvDeviceRequestTexts.EXPLAIN_SERVER)
        assertTrue("pas secrète" in TvDeviceRequestTexts.EXPLAIN_FULL)
        assertTrue(TvDeviceRequestTexts.forStatus(401).isNotBlank() && TvDeviceRequestTexts.forStatus(404).isNotBlank() && TvDeviceRequestTexts.forStatus(500).isNotBlank())
    }

    // ---- authorisation gate ----

    private val pin = "654321"
    private val token = TrustRegistry.TOKEN_PREFIX + "a".repeat(64)

    @Test fun notAuthorisedSendsNothing() {
        var calls = 0
        val fetch = { _: String, _: String -> calls++; tvJson }
        for ((base, cred) in listOf<Pair<String?, String?>>(null to null, "http://192.168.1.20:8765" to null, "http://192.168.1.20:8765" to "", "http://192.168.1.20:8765" to "abc",
            "http://192.168.1.20:8765" to TvAuth.NO_PIN, null to pin, " " to pin, "http://192.168.1.20:8765" to "cbk_short")) {
            val r = TvDeviceRequestReader.read(base, cred, fetch)
            assertIs<DeviceRequestResult.Refused>(r, "$base/$cred")
            assertEquals(TvDeviceRequestTexts.NOT_AUTHORISED, r.message)
        }
        assertEquals(0, calls, "no request is sent when the phone is not authorised")
    }

    @Test fun authorisedByPinOrTrustedTokenReadsOnce() {
        for (cred in listOf(pin, token)) {
            val seen = ArrayList<Pair<String, String>>()
            val r = TvDeviceRequestReader.read("http://192.168.1.20:8765", cred) { b, c -> seen += b to c; tvJson }
            assertIs<DeviceRequestResult.Shown>(r)
            assertEquals(tvText, r.request.fullText())
            assertEquals(listOf("http://192.168.1.20:8765" to cred), seen)
        }
    }

    @Test fun refusalsAreExplainedAndNeverEchoTheCredential() {
        val errors = listOf<Throwable>(TvClient.HttpError(401, """{"error":"bad pin"}"""), TvClient.HttpError(403, "x"), TvClient.HttpError(404, "x"), TvClient.HttpError(429, "x"),
            TvClient.HttpError(500, pin), IOException("connect timed out $pin"), IllegalStateException(pin))
        val seenMessages = HashSet<String>()
        for (e in errors) {
            val r = TvDeviceRequestReader.read("http://192.168.1.20:8765", pin) { _, _ -> throw e }
            assertIs<DeviceRequestResult.Refused>(r)
            assertTrue(r.message.isNotBlank()); assertFalse(pin in r.message, r.message)
            seenMessages += r.message
        }
        assertTrue(seenMessages.size >= 4, "distinct explanations: $seenMessages")
        assertIs<DeviceRequestResult.Refused>(TvDeviceRequestReader.read("http://192.168.1.20:8765", pin) { _, _ -> "n'importe quoi" })
    }

    // ---- W23-05 audit HIGH-1: the TV's signing key travels in the request (`install_sig=ed25519|…`) so the issuers can sign it into the activation ----

    private val sig = ByteArray(32) { (it * 5 + 3).toByte() }
    private val sigHex = sig.joinToString("") { "%02x".format(it) }

    @Test fun theSigningKeyTravelsInTheJsonAndInBothTexts() {
        val text = OwnerFrames.deviceInfo(code, fp, pub, sig)
        val json = TvDeviceRequestApi.json(text)!!
        assertTrue("\"installSig\":\"$sigHex\"" in json, json)
        val r = parsed(json)
        assertEquals(sigHex, r.installSigHex)
        assertEquals(text, r.fullText(), "the main copy stays byte for byte what the TV produces")
        val server = r.serverText()
        assertFalse("install=" in server, "no X25519 line for the server")
        assertTrue("install_sig=ed25519|$sigHex" in server, "the server signs this key into the activation it issues")
        assertTrue(server.lines().all { it.startsWith("code=") || it.startsWith("k=") || it.startsWith("factor=") || it.startsWith("install_sig=") }, server)
        val d = DeviceRequest.parse(server)
        assertContentEquals(sig, d.installSig); assertNull(d.installPub)
        assertTrue(r.viewLines().any { it.contains(sigHex) })
    }

    @Test fun aTvWithoutTheSigningKeyOrWithAMalformedOneIsHandledStrictly() {
        assertNull(parsed().installSigHex, "the TV text of this fixture has none")
        assertTrue(TvDeviceRequestApi.json(tvText)!!.contains("\"installSig\":null"))
        val bad = TvDeviceRequestApi.json(OwnerFrames.deviceInfo(code, fp, pub, sig))!!.replace(sigHex, "zz" + sigHex.drop(2))
        assertTrue(TvDeviceRequestParser.parse(bad) is DeviceRequestParse.Refused, "a malformed key never reaches a text")
    }
}
