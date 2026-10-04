package castbridge.core.quiz.online

import kotlin.test.*

class LimitsTest {
    private var now = 1_000_000L
    private fun limits(maxKeys: Int = 100_000, c: Limits.Config = Limits.Config()) = Limits({ now }, c, maxKeys)

    @Test fun connectionBucketsPerMinuteAndRetryAfter() {
        val l = limits(c = Limits.Config(connPerSecondGlobal = 1_000))
        repeat(60) { assertTrue(l.admitConnection("1.2.3.4").allowed, "connexion ${it + 1}") }
        val d = l.admitConnection("1.2.3.4")
        assertFalse(d.allowed); assertEquals(Limits.Scope.IP_MINUTE, d.scope)
        assertEquals(1_000L, d.retryAfterMs, "un jeton par seconde (60 / min)"); assertEquals(1L, d.retryAfterSeconds)
        assertTrue(l.admitConnection("5.6.7.8").allowed, "une autre adresse n'est pas touchée")
        now += 1_000
        assertTrue(l.admitConnection("1.2.3.4").allowed, "un jeton est revenu")
    }

    @Test fun hourlyCeilingGlobalCeilingAndNothingConsumedByARefusal() {
        val l = limits(c = Limits.Config(connPerMinute = 1_000, connPerHour = 600, connPerSecondGlobal = 100_000))
        repeat(600) { assertTrue(l.admitConnection("1.2.3.4").allowed) }
        val d = l.admitConnection("1.2.3.4")
        assertFalse(d.allowed); assertEquals(Limits.Scope.IP_HOUR, d.scope); assertEquals(6_000L, d.retryAfterMs)
        val g = limits(c = Limits.Config(connPerSecondGlobal = 3))
        repeat(3) { assertTrue(g.admitConnection("k$it").allowed) }
        assertEquals(Limits.Scope.GLOBAL, g.admitConnection("k9").scope)
        now += 400   // 1,2 jeton
        assertTrue(g.admitConnection("k10").allowed)
        assertFalse(g.admitConnection("k11").allowed, "un seul jeton est revenu")
    }

    @Test fun aRefusedConnectionDoesNotConsumeTheAddressBucket() {
        val l = limits(c = Limits.Config(connPerSecondGlobal = 1, connPerMinute = 2))
        assertTrue(l.admitConnection("a").allowed)
        repeat(10) { assertFalse(l.admitConnection("a").allowed, "refus global") }
        now += 1_000
        assertTrue(l.admitConnection("a").allowed, "le seau de l'adresse avait gardé son second jeton")
    }

    @Test fun deviceAndRoomBuckets() {
        val l = limits()
        repeat(20) { assertTrue(l.admitDevice("dev-1").allowed) }
        assertEquals(Limits.Scope.DEVICE, l.admitDevice("dev-1").scope); assertTrue(l.admitDevice("dev-2").allowed)
        repeat(600) { assertTrue(l.admitRoom("room").allowed) }
        val d = l.admitRoom("room"); assertFalse(d.allowed); assertEquals(Limits.Scope.ROOM, d.scope); assertTrue(d.retryAfterMs in 1..10)
        l.forgetRoom("room"); assertTrue(l.admitRoom("room").allowed)
    }

    @Test fun memoryIsBoundedAndEvictionNeverBlocks() {
        val l = limits(maxKeys = 1_000)
        repeat(50_000) { l.admitDevice("dev-$it") }
        assertTrue(l.size() <= 1_000, "au plus 1 000 seaux : ${l.size()}")
        assertTrue(l.admitDevice("dev-0").allowed, "une clé évincée repart avec un seau plein")
        assertEquals(100_000, Limits.MAX_KEYS)
    }

    @Test fun sharedCeilingCountsOnlyPlayersSeatedForThirtySecondsAndIsEightPlusSeated() {
        val l = limits()
        assertEquals(8, l.openCeiling("school"))
        repeat(8) { l.noteSeat("school", "room-A", "dev-$it") }
        assertEquals(8, l.openCeiling("school"), "des sièges tout neufs (une rafale d'identifiants inventés) ne relèvent rien")
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(16, l.openCeiling("school"), "8 joueurs assis depuis 30 s : 8 + 8")
        repeat(30) { l.noteSeat("school", "room-A", "more-$it") }
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(46, l.openCeiling("school"), "8 + 38 appareils assis")
        repeat(60) { l.noteSeat("big", "room-B", "d-$it") }
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(64, l.openCeiling("big"), "jamais plus de 64")
        // sept appareils dans sept salles : pas de salle commune
        repeat(7) { l.noteSeat("home", "room-$it", "dev-$it") }
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(8, l.openCeiling("home"))
        // un joueur parti ne compte plus
        l.dropSeat("school", "room-A", "dev-0")
        assertEquals(45, l.openCeiling("school"))
        repeat(20) { l.noteSeat("lone", "room-L", "same-device") }
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(8, l.openCeiling("lone"), "un seul appareil répété ne compte pas")
        now += Limits.SEAT_MEMORY_MS
        assertEquals(8, l.openCeiling("school"), "la mémoire des sièges expire")
    }

    @Test fun oneIpv6SlashFortyEightCannotSaturateTheGlobalBucket() {
        val l = limits(c = Limits.Config(connPerMinute = 100_000, connPerHour = 1_000_000, connPerSecondGlobal = 60))
        var allowed = 0
        for (i in 0 until 100) if (l.admitConnection("v6:2001:0db8:abcd:%04x::/64".format(i)).let { it.allowed }) allowed++
        assertTrue(allowed <= 60, "le global tient : $allowed")
        val l2 = limits(c = Limits.Config(connPerMinute = 100_000, connPerHour = 1_000_000, connPerSecondGlobal = 100_000))
        val results = (0 until 400).map { l2.admitConnection("v6:2001:0db8:abcd:%04x::/64".format(it)) }
        assertEquals(300, results.count { it.allowed }, "un /48 : 300 par minute")
        assertEquals(Limits.Scope.PREFIX48, results.first { !it.allowed }.scope)
        assertTrue(l2.admitConnection("v6:2001:0db8:ffff:0001::/64").allowed, "un autre /48 n'est pas touché")
    }

    @Test fun aSeatWhoseConnectionDroppedIsKeptForTheResumeGraceThenFreed() {
        val l = limits()
        repeat(10) { l.noteSeat("s", "room", "d$it") }
        now += Limits.SEAT_MIN_AGE_MS
        assertEquals(18, l.openCeiling("s"))
        repeat(10) { l.seatLeft("s", "room", "d$it") }
        now += Limits.SEAT_GRACE_MS - 1
        assertEquals(18, l.openCeiling("s"), "gardés pendant la grâce de reprise (10 minutes)")
        l.noteSeat("s", "room", "d0")   // une reprise : le siège revient avec sa date d'origine
        now += 2
        assertEquals(1, l.seatedDevices("s"), "les neuf autres, laissés plus de 10 minutes, sont libérés ; le repris reste")
        repeat(10) { l.noteSeat("t", "room2", "d$it") }
        now += Limits.SEAT_MIN_AGE_MS
        l.dropRoom("room2")
        assertEquals(8, l.openCeiling("t"), "salle disparue : ses sièges partent")
    }
}
