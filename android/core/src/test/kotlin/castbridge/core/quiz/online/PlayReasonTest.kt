package castbridge.core.quiz.online

import kotlin.test.*

class PlayReasonTest {
    @Test fun stableCodesWithFrenchMessages() {
        assertEquals(listOf("PLAY_BAD_CODE", "PLAY_ROOM_FULL", "PLAY_ROOM_GONE", "PLAY_TICKET_REFUSED", "PLAY_BANNED", "PLAY_TLS_INVALID", "PLAY_SCOPE_FORBIDDEN", "PLAY_BUSY", "BAD_NAME"),
            PlayReason.values().map { it.code })
        PlayReason.values().forEach { assertTrue(it.message.length > 10 && it.http in 400..599, it.code) }
        assertEquals(PlayReason.PLAY_BAD_CODE, PlayReason.of("PLAY_BAD_CODE")); assertNull(PlayReason.of("NOPE"))
        assertTrue(PlayReason.PLAY_BAD_CODE.retryable); assertFalse(PlayReason.PLAY_BANNED.retryable); assertFalse(PlayReason.PLAY_ROOM_GONE.retryable)
    }
}
