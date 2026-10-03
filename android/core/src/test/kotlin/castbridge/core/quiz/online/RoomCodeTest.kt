package castbridge.core.quiz.online

import castbridge.core.quiz.EmbeddedQuestionSource
import kotlin.test.*

class RoomCodeTest {
    private fun room() = ServerRoom("r1", PlayScope.INTERNET, EmbeddedQuestionSource().bank(), java.util.Random(3), createdAt = 0)
    private fun err(o: List<ServerRoom.Out>) = (o.single().msg as ServerMsg.Error).reason

    @Test fun alphabetIsCrockford32WithoutIlou() {
        assertEquals(32, RoomCode.ALPHABET.length); assertEquals(32, RoomCode.ALPHABET.toSet().size)
        assertTrue("ILOU".none { it in RoomCode.ALPHABET })
        assertEquals(40, 5 * RoomCode.LENGTH, "8 caractères × 5 bits = 40 bits")
    }

    @Test fun generatedCodesAreEightValidSymbolsAndSpreadOver40Bits() {
        val r = java.util.Random(1)
        val codes = (1..2_000).map { RoomCode.generate(r) }
        assertTrue(codes.all { it.length == 8 && it.all { c -> c in RoomCode.ALPHABET } })
        assertTrue(codes.toSet().size >= 1_999, "pas de collision attendue sur 2 000 tirages de 40 bits")
        assertEquals(32, codes.flatMap { it.toList() }.toSet().size, "tous les symboles sortent")
        assertTrue(codes.all { RoomCode.normalize(it) == it })
    }

    @Test fun displayAndNormalize() {
        assertEquals("K7M2-QX4T", RoomCode.display("K7M2QX4T"))
        for (typed in listOf("K7M2QX4T", "k7m2qx4t", "K7M2-QX4T", " k7m2 - qx4t ")) assertEquals("K7M2QX4T", RoomCode.normalize(typed), typed)
        assertEquals("1100ABCD", RoomCode.normalize("ILOoabcd"), "I et L lus comme 1, O comme 0")
        for (bad in listOf(null, "", "K7M2QX4", "K7M2QX4TT", "K7M2QXUT", "K7M2QX4!")) assertNull(RoomCode.normalize(bad), "$bad")
    }

    @Test fun ttlIsTwoHours() {
        assertEquals(2 * 60 * 60_000L, RoomCode.TTL_MS)
        assertFalse(RoomCode.expired(0, RoomCode.TTL_MS - 1)); assertTrue(RoomCode.expired(0, RoomCode.TTL_MS))
    }

    @Test fun badCodeCounterPerIpAndPerRoom() {
        val c = BadCodeCounter()
        for (i in 1..9) assertFalse(c.ipFail("1.2.3.4", i * 1_000L), "essai $i")
        assertTrue(c.ipFail("1.2.3.4", 10_000)); assertTrue(c.ipBlocked("1.2.3.4", 11_000))
        assertFalse(c.ipBlocked("5.6.7.8", 11_000), "une autre IP n'est pas touchée")
        assertFalse(c.ipBlocked("1.2.3.4", 10_000 + 5 * 60_000L), "fenêtre de 5 minutes")
        var rotated = 0; repeat(100) { if (c.roomFail()) rotated++ }
        assertEquals(2, rotated, "50 essais faux sur la salle ⇒ nouveau code, puis le compteur repart")
    }

    @Test fun fiftyWrongTriesRotateTheCodeAndWriteTheEventRing() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, null), 0)
        val old = r.code
        repeat(49) { assertEquals(PlayReason.PLAY_BAD_CODE.name, err(r.handle("x$it", ClientMsg.Join("ZZZZZZZZ", "Awa", null, null, false), 1_000L))) }
        assertEquals(old, r.code)
        r.handle("x50", ClientMsg.Join("ZZZZZZZZ", "Awa", null, null, false), 1_000)
        assertNotEquals(old, r.code, "50e essai faux : nouveau code")
        assertTrue(r.eventsSince(0)!!.any { it.kind == "codeRotated" })
        assertEquals(PlayReason.PLAY_BAD_CODE.name, err(r.handle("late", ClientMsg.Join(old, "Awa", null, null, false), 2_000)), "l'ancien code ne marche plus")
    }

    @Test fun theHostIsToldTheNewCodeByAState() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, null), 0)
        val old = r.code
        var outs = emptyList<ServerRoom.Out>()
        repeat(50) { outs = r.handle("x$it", ClientMsg.Join("ZZZZZZZZ", "Awa", null, null, false), 1_000L) }
        val st = outs.filter { it.to == "tv" }.map { it.msg }.filterIsInstance<ServerMsg.State>().single()
        @Suppress("UNCHECKED_CAST") assertEquals(RoomCode.display(r.code), (st.view["room"] as Map<String, Any?>)["code"])
        assertNotEquals(RoomCode.display(old), RoomCode.display(r.code))
    }

    @Test fun perIpBlockStopsGuessingEvenWithTheRightRoom() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, null), 0)
        repeat(10) { r.handle("x", ClientMsg.Join("ZZZZZZZZ", "Awa", null, null, false), 1_000L + it, ip = "9.9.9.9") }
        assertEquals(PlayReason.PLAY_BAD_CODE.name, err(r.handle("x", ClientMsg.Join(r.code, "Awa", null, null, false), 2_000, ip = "9.9.9.9")), "IP bloquée même avec le bon code")
        assertEquals("PLAYER", (r.handle("y", ClientMsg.Join(r.code, "Awa", null, null, false), 2_000, ip = "8.8.8.8").map { it.msg }.filterIsInstance<ServerMsg.Welcome>().single().role.name))
    }

    @Test fun codeExpiresAfterTwoHoursRoomGone() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, null), 0)
        assertEquals(PlayReason.PLAY_ROOM_GONE.name, err(r.handle("late", ClientMsg.Join(r.code, "Awa", null, null, false), RoomCode.TTL_MS)))
        val gone = r.tick(RoomCode.TTL_MS)
        assertTrue(gone.any { it.msg is ServerMsg.RoomGone && it.to == "tv" })
        assertEquals(ServerRoom.State.GONE, r.phase())
        assertEquals(PlayReason.PLAY_ROOM_GONE.name, err(r.handle("tv", ClientMsg.Pong("x"), RoomCode.TTL_MS + 1)))
    }
}
