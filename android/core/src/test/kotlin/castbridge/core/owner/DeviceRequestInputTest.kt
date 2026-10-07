package castbridge.core.owner

import castbridge.core.owner.DeviceRequestInput.Kind
import kotlin.test.*

/**
 * Ce que l'on tape ou colle dans le champ de la console du propriétaire (« Activer »), et dans l'outil de bureau (`emettre --appareil`, onglet « Émettre ») : jamais plus d'« erreur de format » sans
 * explication. Le lecteur strict ([OwnerFrames.parseDeviceInfo]) exige `code=`, `k=` puis des lignes `factor=` ; l'émetteur d'une activation COMPLÈTE ne peut pas partir du code seul (le code est le haché
 * des empreintes des facteurs : `ActivationIssuer.issue` les exige). [DeviceRequestInput.classify] dit ce que l'on a devant soi et [DeviceRequestInput.message] l'explique en français :
 *  (a) un code d'appareil seul et valide : il identifie la TV mais ne suffit pas, où trouver la demande complète ;
 *  (b) un code d'appareil mal recopié : le format attendu ;
 *  (c) une demande complète : lue comme avant ;
 *  (d) une demande sans `k=` ou dont une ligne est illisible : la ligne fautive est nommée.
 */
class DeviceRequestInputTest {
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd"))
    private val code = DeviceCode.of(fp)
    private val pub = ByteArray(32) { (it + 3).toByte() }
    private val sig = ByteArray(32) { (it + 7).toByte() }
    private val full = OwnerFrames.deviceInfo(code, fp, pub, sig)
    private val bom = 0xFEFF.toChar().toString()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun problemOf(text: String) = assertIs<Kind.Unreadable>(DeviceRequestInput.classify(text), text).problem
    private fun messageOf(text: String) = assertNotNull(DeviceRequestInput.message(DeviceRequestInput.classify(text)), text)

    // ---- (c) une demande complète

    @Test fun aCompleteRequestIsReadAsBeforeWithOrWithoutTheOptionalLines() {
        for (t in listOf(full, OwnerFrames.deviceInfo(code, fp), OwnerFrames.deviceInfo(code, fp, null, sig), OwnerFrames.deviceInfo(code, fp, pub, sig, withFingerprint = true))) {
            val r = assertIs<Kind.Request>(DeviceRequestInput.classify(t), t)
            assertEquals(code, r.info.code); assertEquals(fp.byKind, r.info.fp.byKind)
            assertNull(DeviceRequestInput.message(r), "a readable request needs no explanation")
        }
        assertContentEquals(pub, assertIs<Kind.Request>(DeviceRequestInput.classify(full)).info.installPub)
    }

    @Test fun crlfBomBlankLinesAndIndentationAreTolerated() {
        val noisy = bom + "\r\n  " + full.replace("\n", "\r\n\r\n   ") + "\r\n\r\n"
        assertEquals(code, assertIs<Kind.Request>(DeviceRequestInput.classify(noisy)).info.code)
        assertEquals(code, assertIs<Kind.Request>(DeviceRequestInput.classify("$full\nmodel=Bravia\nfuture=1")).info.code, "unknown key=value lines are ignored, as by the reader")
    }

    // ---- (a) le code d'appareil seul

    @Test fun aValidDeviceCodeAloneIsNamedAsOneAndExplained() {
        val sample = "BRX4-W1C5-4WKB-6DGQ"            // the code the owner typed (valid: its check character is right)
        for (t in listOf(sample, sample.lowercase(), sample.replace("-", ""), "  $sample \n", " BRX4 W1C5 4WKB 6DGQ", "code=$sample", "code=${sample.lowercase()}\n", code, bom + sample)) {
            val k = assertIs<Kind.CodeOnly>(DeviceRequestInput.classify(t), t)
            assertEquals(DeviceCode.parse(t.removePrefix(bom).trim().removePrefix("code=")), k.code)
            assertEquals(DeviceRequestInput.CODE_ONLY, DeviceRequestInput.message(k))
        }
        assertEquals(sample, assertIs<Kind.CodeOnly>(DeviceRequestInput.classify("brx4w1c54wkb6dgq")).code, "normalised XXXX-XXXX-XXXX-XXXX")
    }

    @Test fun theMessageForACodeAloneSaysWhyAndWhereToFindTheCompleteRequest() {
        assertEquals("Ce code identifie la TV mais ne suffit pas : la clé est liée aux empreintes de la TV. Collez la demande d'appareil complète (lignes code=, k=, factor=…) : " +
            "sur le téléphone, CastBridge › Activer la TV la lit pour vous (par le code à 6 chiffres sur une TV à jour, sinon par Bluetooth) et ouvre cette console pré-remplie.", DeviceRequestInput.CODE_ONLY)
    }

    // ---- (b) un code d'appareil mal recopié

    @Test fun aWrongDeviceCodeIsNamedAsMisCopiedWithTheExpectedFormat() {
        val wrongCheck = "BRX4-W1C5-4WKB-6DGO"          // O is read as 0: the check character no longer matches
        for (t in listOf(wrongCheck, "BRX4-W1C5-4WKB", "BRX4-W1C5-4WKB-6DGQ-1", "BRX4-W1C5-4WKB-6DG!", "bonjour", "123", "code=BRX4-W1C5-4WKB-6DGO", "code=", "U" + "A".repeat(15))) {
            assertEquals(Kind.BadCode, DeviceRequestInput.classify(t), t)
            assertEquals(DeviceRequestInput.BAD_CODE, DeviceRequestInput.message(Kind.BadCode))
        }
        assertEquals("Code d'appareil mal recopié : 16 caractères XXXX-XXXX-XXXX-XXXX avec son caractère de contrôle", DeviceRequestInput.BAD_CODE)
    }

    // ---- (d) une demande dont une ligne manque ou est illisible : la ligne fautive est nommée

    @Test fun aRequestWithoutKNamesTheMissingLine() {
        val noK = full.lines().filterNot { it.startsWith("k=") }.joinToString("\n")
        val m = messageOf(noK)
        assertTrue(m.startsWith("Demande d'appareil illisible") && "« k=… »" in m && m.endsWith("."), m)
        // k=… present but not right after code=…: the strict reader wants it second
        val late = full.lines().filterNot { it.startsWith("k=") }.let { it.take(2) + "k=2" + it.drop(2) }.joinToString("\n")
        assertTrue("« k=… »" in messageOf(late) && "code=" in messageOf(late) && "plus bas" in messageOf(late), messageOf(late))
        assertFalse("plus bas" in m, "a request with no k= at all is not told its k= is further down: $m")
        val nan = full.replace("k=2", "k=deux")
        assertTrue("« k=deux »" in messageOf(nan), messageOf(nan))
    }

    @Test fun theFirstLineMustBeTheCodeAndAnInvalidCodeLineIsNamed() {
        val swapped = full.lines().let { listOf(it[1], it[0]) + it.drop(2) }.joinToString("\n")
        assertTrue("première ligne" in messageOf(swapped) && "code=" in messageOf(swapped) && "k=2" in messageOf(swapped), messageOf(swapped))
        val badCodeLine = full.replace("code=$code", "code=AAAA-AAAA-AAAA-AAAB")                   // « AAAA-AAAA-AAAA-AAAA » est un code valide (son caractère de contrôle est A) : B ne l'est pas
        val m = messageOf(badCodeLine)
        assertTrue("« code=AAAA-AAAA-AAAA-AAAB »" in m && "caractère de contrôle" in m, m)
    }

    @Test fun aRequestWithoutAnyFactorNamesTheMissingFactorLines() {
        val onlyHead = full.lines().take(2).joinToString("\n")
        val m = messageOf(onlyHead)
        assertTrue("factor=TYPE|empreinte" in m && "manque" in m, m)
        assertTrue("factor=" in messageOf(OwnerFrames.deviceInfo(code, fp).lines().filterNot { it.startsWith("factor=") }.joinToString("\n")))
        assertTrue("manque" in problemOf("code=$code\nk=2"))
    }

    @Test fun aMalformedFactorLineIsNamed() {
        for (bad in listOf("factor=EVIL|0a1b2c3d4e5f60718293a4b5c6d7e8f9", "factor=FLASH", "factor=FLASH|", "factor=FLASH|a|b", "factor=|abc")) {
            val t = full.replace("factor=FLASH|0a1b2c3d4e5f60718293a4b5c6d7e8f9", bad)
            val m = messageOf(t)
            val quoted = bad.take(40) + if (bad.length > 40) "…" else ""                                          // a long line is cut at 40 characters
            assertTrue("« $quoted »" in m && "factor=TYPE|empreinte" in m && "FLASH, ETHERNET, WIFI, SYSTEM_SERIAL" in m, "$bad\n$m")
        }
    }

    @Test fun aMalformedInstallOrSigningLineIsNamedAndADoubledOneToo() {
        val h = hex(pub); val s = hex(sig)
        val cases = listOf(
            full.replace("install=x25519|$h", "install=x25519|zz") to "install=x25519|zz",
            full.replace("install=x25519|$h", "install=x25519|" + h.dropLast(2)) to "install=x25519|" + h.dropLast(2),
            full.replace("install=x25519|$h", "install=x25519|" + h.uppercase()) to "install=x25519|" + h.uppercase(),
            full + "\ninstall=x25519|$h" to "install=x25519|$h",
            full.replace("install_sig=ed25519|$s", "install_sig=ed25519|zz") to "install_sig=ed25519|zz",
            full + "\ninstall_sig=ed25519|$s" to "install_sig=ed25519|$s",
        )
        for ((t, named) in cases) {
            val m = messageOf(t)
            assertTrue("« ${named.take(40)}${if (named.length > 40) "…" else ""} »" in m && "64 chiffres hexadécimaux" in m, "$named\n$m")
        }
        val badFp = full + "\ninstall_fp=pas-une-empreinte"
        assertTrue("« install_fp=pas-une-empreinte »" in messageOf(badFp), messageOf(badFp))
    }

    @Test fun aLineWithoutAnEqualsSignIsNamed() {
        val m = messageOf(full + "\nune ligne de trop")
        assertTrue("« une ligne de trop »" in m && "clé=valeur" in m, m)
    }

    @Test fun onlyTheFaultyLineIsEverQuotedAndNeverMoreThanFortyCharacters() {
        val long = "factor=FLASH|" + "z".repeat(300)
        val m = messageOf(full.replace("factor=FLASH|0a1b2c3d4e5f60718293a4b5c6d7e8f9", "factor=EVIL|" + "z".repeat(300)))
        assertTrue(m.length < 400, "no 300-character echo: ${m.length}")
        assertFalse("z".repeat(41) in m); assertFalse(long in m)
        assertFalse(code in m, "the rest of the request is not echoed back")
        val ctl = messageOf(full.replace("factor=FLASH|", "factor=EV\u0007IL|"))
        assertFalse(ctl.any { it.isISOControl() && it != '\n' }, "no control character on the screen")
    }

    // ---- vide

    @Test fun nothingTypedIsEmptyAndSaysWhatToPaste() {
        for (t in listOf("", "   ", "\n\r\n ", bom)) {
            assertEquals(Kind.Empty, DeviceRequestInput.classify(t), "[$t]")
            assertTrue("code=" in assertNotNull(DeviceRequestInput.message(Kind.Empty)))
        }
    }

    // ---- le classeur dit la même chose que le lecteur strict

    @Test fun theClassifierNeverContradictsTheStrictReader() {
        val lines = full.lines()
        val texts = ArrayList<String>()
        for (i in lines.indices) {
            texts += lines.filterIndexed { j, _ -> j != i }.joinToString("\n")                                               // a line missing
            texts += lines.mapIndexed { j, l -> if (j == i) l + "x" else l }.joinToString("\n")                              // a line spoiled at its end
            texts += lines.mapIndexed { j, l -> if (j == i) l.substringBefore('=') + "=" else l }.joinToString("\n")         // a line emptied
            texts += (lines.take(i + 1) + lines[i] + lines.drop(i + 1)).joinToString("\n")                                  // a line doubled
        }
        for (t in texts) {
            val strict = OwnerFrames.parseDeviceInfo(t)
            val k = DeviceRequestInput.classify(t)
            if (strict != null && strict.fp.n >= 1) assertIs<Kind.Request>(k, t) else {
                assertFalse(k is Kind.Request, "the strict reader refuses it, so the classifier must not read it:\n$t")
                val m = assertNotNull(DeviceRequestInput.message(k), t)
                assertTrue(m.first().isUpperCase() && !m.contains("null"), m)
                if (k is Kind.Unreadable) assertFalse("attendu : code=…, k=…, puis des lignes factor=TYPE|empreinte" in m, "the faulty line must be named, not the generic fallback:\n$t\n$m")
            }
        }
    }

    // ---- les lecteurs du propriétaire disent la même chose

    @Test fun theIssuersReaderSaysTheSameThingForTheDeskToolsAndTheConsole() {
        val sample = "BRX4-W1C5-4WKB-6DGQ"
        assertEquals(DeviceRequestInput.CODE_ONLY, assertFailsWith<IssueException> { DeviceRequest.parse(sample) }.message)
        assertEquals(DeviceRequestInput.BAD_CODE, assertFailsWith<IssueException> { DeviceRequest.parse("BRX4-W1C5-4WKB-6DGO") }.message)
        val noK = full.lines().filterNot { it.startsWith("k=") }.joinToString("\n")
        assertTrue("« k=… »" in assertFailsWith<IssueException> { DeviceRequest.parse(noK) }.message.orEmpty())
        assertTrue("illisible" in assertFailsWith<IssueException> { DeviceRequest.parse(full.replace("install=x25519|${hex(pub)}", "install=x25519|a")) }.message.orEmpty(), "the desk tool tests rely on « illisible »")
        assertEquals(code, DeviceRequest.parse(full).code)
        assertTrue(assertFailsWith<IssueException> { DeviceRequest.parse("") }.message.orEmpty().contains("code="))
        // a request whose code does not follow from its fingerprints keeps its own sentence
        assertTrue("ne correspond pas aux empreintes" in assertFailsWith<IssueException> { DeviceRequest.parse(full.replace("code=$code", "code=${DeviceCode.of(Fingerprints(mapOf(FactorKind.FLASH to "f".repeat(32))))}")) }.message.orEmpty())
    }
}
