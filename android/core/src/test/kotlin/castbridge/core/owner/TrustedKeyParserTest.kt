package castbridge.core.owner

import java.io.File
import kotlin.test.*

class TrustedKeyParserTest {
    @Test fun aLineWithoutScopesGrantsNothing() {
        val p = TrustedKeyParser.parse("kid=k1 pub=AAAA")
        assertEquals(1, p.keys.size); assertTrue(p.keys[0].scopes.isEmpty()); assertTrue(p.warnings.any { "sans scopes" in it })
        assertFalse(p.keys[0].allows(KeyScope.SUPER_UNLIMITED)); assertFalse(p.keys[0].allows(KeyScope.COMMAND_OPEN_ALL))
    }
    @Test fun unknownScopesAreIgnoredKnownOnesKept() {
        val p = TrustedKeyParser.parse("kid=k2 pub=BBBB scopes=ISSUE_TRIAL,NOPE,ISSUE_PRODUCTION")
        assertEquals(setOf(KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION), p.keys.single().scopes); assertTrue(p.warnings.any { "NOPE" in it })
    }
    @Test fun emptyScopesAndMalformedLinesAndComments() {
        assertTrue(TrustedKeyParser.parse("kid=k3 pub=CC scopes=").keys.single().scopes.isEmpty())
        val p = TrustedKeyParser.parse("# note\n\npub=only\nkid=k4 pub=DD scopes=REVOKE\n")
        assertEquals(listOf("k4"), p.keys.map { it.keyId }); assertEquals(1, p.warnings.size)
        assertNull(TrustedKeyParser.parseLine("garbage"))
    }
}

class SafeFileTest {
    private fun tmp() = kotlin.io.path.createTempDirectory("safe").toFile()
    private val ok: (String) -> Boolean = { it.endsWith("\n") }

    @Test fun writesAtomicallyKeepsTheLastGoodAsBackupAndLeavesNoTemp() {
        val f = File(tmp(), "a.txt")
        SafeFile.write(f, "one\n", ok); assertEquals("one\n", f.readText()); assertFalse(SafeFile.bak(f).exists())
        SafeFile.write(f, "two\n", ok); assertEquals("two\n", f.readText()); assertEquals("one\n", SafeFile.bak(f).readText())
        assertFalse(File(f.parentFile, "a.txt.tmp").exists())
    }
    @Test fun aTruncatedMainFileFallsBackToTheBackupAndNeverReplacesAGoodBackup() {
        val f = File(tmp(), "a.txt")
        SafeFile.write(f, "one\n", ok); SafeFile.write(f, "two\n", ok)
        f.writeText("tw")                                                         // power cut mid-write
        val r = SafeFile.read(f, ok)!!; assertEquals("one\n", r.text); assertTrue(r.fromBackup)
        SafeFile.write(f, "three\n", ok)                                          // the corrupt main must not overwrite the good .bak
        assertEquals("one\n", SafeFile.bak(f).readText()); assertEquals("three\n", f.readText())
        assertNull(SafeFile.read(File(tmp(), "none"), ok))
    }
    @Test fun aFailedWriteThrowsAndLeavesTheMainFileIntact() {
        val d = tmp(); val f = File(d, "a.txt"); SafeFile.write(f, "one\n", ok)
        File(d, "a.txt.tmp").mkdirs()                                              // the temp name is taken by a folder: the write cannot happen
        assertFails { SafeFile.write(f, "two\n", ok) }
        assertEquals("one\n", f.readText())
    }
}
