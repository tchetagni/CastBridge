package castbridge.core.lots

import castbridge.core.crypto.PlainWrapper
import java.io.File
import kotlin.test.*
import castbridge.core.lots.InstallKeyPolicy.Step

class InstallKeyPolicyTest {
    @Test fun aliasIsGeneratedOnlyWhenTheLookupSaysItIsAbsent() {
        assertEquals(Step.USE, InstallKeyPolicy.forLookup(true))
        assertEquals(Step.GENERATE, InstallKeyPolicy.forLookup(false))
        assertEquals(Step.FAIL, InstallKeyPolicy.forLookup(null))
    }
    @Test fun onlyAPermanentlyInvalidatedKeyIsRecreated() {
        assertEquals(Step.RECREATE, InstallKeyPolicy.forEncryptFailure(true))
        assertEquals(Step.FAIL, InstallKeyPolicy.forEncryptFailure(false))
    }
    @Test fun aKeystoreKeyIsNeverHandledByAWeakerWrapper() {
        assertTrue(InstallKeyPolicy.mayUseWrapper(null, "plain"))
        assertTrue(InstallKeyPolicy.mayUseWrapper("keystore", "keystore"))
        assertFalse(InstallKeyPolicy.mayUseWrapper("keystore", "plain"))
        assertTrue(InstallKeyPolicy.mayUseWrapper("plain", "plain"))
        assertTrue(InstallKeyPolicy.mayUseWrapper("plain", "keystore"))
    }
    @Test fun storedWrapReadsTheLabelOfTheKeyFile() {
        val dir = Kit.tmp()
        assertNull(InstallKeyStore.storedWrap(dir))
        InstallKeyStore(dir, PlainWrapper()).loadOrCreate()
        assertEquals("plain", InstallKeyStore.storedWrap(dir))
        File(dir, "install.key").writeText("wrap=keystore")                      // no header: not a key file
        assertNotEquals("keystore", InstallKeyStore.storedWrap(dir))
    }
}
