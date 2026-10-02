package castbridge.core.owner

import kotlin.test.*

class DeviceCodeTypingTest {
    @Test fun dashesAppearEveryFourCharactersWhileTyping() {
        assertEquals("ABCD", DeviceCode.typing("abcd"))
        assertEquals("ABCD-E", DeviceCode.typing("abcde"))
        assertEquals("ABCD-EFGH-JKMN-PQRS", DeviceCode.typing("abcdefghjkmnpqrs"))
        assertEquals("ABCD-EFGH-JKMN-PQRS", DeviceCode.typing("abcdefghjkmnpqrsXYZ"), "at most 16 characters")
    }

    @Test fun typedDashesAndSpacesAreIgnoredAndBackspaceKeepsWorking() {
        assertEquals("ABCD-EFGH", DeviceCode.typing("ABCD EFGH"))
        assertEquals("ABCD-EFGH", DeviceCode.typing("ABCD-EF-GH"))
        assertEquals("ABCD", DeviceCode.typing("ABCD-"), "a trailing dash is dropped, the next character brings it back")
        assertEquals("", DeviceCode.typing(""))
    }

    @Test fun aFullRequestOrAnythingElseIsLeftAlone() {
        val request = "code=9BBW-XAV4-VT8G-4Z8N\nk=2\nfactor=FLASH|770f"
        assertEquals(request, DeviceCode.typing(request))
        assertEquals("code=ABCD", DeviceCode.typing("code=ABCD"))
        assertEquals("é!", DeviceCode.typing("é!"))
    }
}
