package castbridge.core.owner

import castbridge.core.btact.BtActAd
import castbridge.core.btact.BtActClient
import castbridge.core.btact.BtActWire
import castbridge.core.btact.FakeTv
import castbridge.core.btact.live
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.BleSearch.Attempt
import kotlin.test.*

/**
 * La voie « Bluetooth sans appairage » de bout en bout, sans radio : les annonces de plusieurs TV passent par le tri du téléphone ([BleSearch.Collector]), puis le VRAI client essaie les candidates
 * contre de VRAIES TV ([BtActServer]) sur des flux en mémoire ; les causes sont celles que l'écran dirait. Le cas qui n'arrive qu'une fois sur 65 536 est fabriqué : une TV voisine dont le tag est
 * le même que celui du code tapé.
 */
class BleRouteIntegrationTest {
    private val mine = "482913"
    /** Un AUTRE code dont le tag de 2 octets est le même que celui de [mine] (il en existe une quinzaine sur 10⁶). */
    private val colliding: String = (0 until 1_000_000).asSequence().map { "%06d".format(it) }.first { it != mine && BtActAd.tag(it).contentEquals(BtActAd.tag(mine)) }

    private fun sighting(id: String, rssi: Int, code: String, psm: Int = 0x81) = BleSearch.Sighting(id, rssi, BtActAd.payload(code, psm))

    /** Ce que fait le téléphone d'une TV candidate : le vrai client contre la vraie TV (une session), et ce que la voie en conclut. */
    private fun tryOn(tv: FakeTv, typed: String, peer: String = FakeTv.PEER): Attempt {
        val r = live(tv, typed, peer = peer, key = null)
        return when (val c = r.connect) {
            is BtActClient.Connect.Ready -> Attempt.Reached(c.session.tvName, c.session.tvVersion, (r.read as? BtActClient.Session.Request.Text)?.text)
            else -> BleSearch.attemptOf(c) ?: Attempt.Lost()
        }
    }

    @Test fun theCollidingCodeReallyHasTheSameTag() {
        assertNotEquals(mine, colliding)
        assertContentEquals(BtActAd.tag(mine), BtActAd.tag(colliding))
    }

    @Test fun aNeighbourWithAnotherTagIsNeverApproachedAndTheRightTvIsReachedAndRead() {
        val mineTv = FakeTv(code = mine); val neighbour = FakeTv(code = "111111")
        val collector = BleSearch.Collector(mine)
        collector.add(sighting("neighbour", -40, "111111", 0x91), 0)                // the nearest TV is the neighbour's
        collector.add(sighting("mine", -70, mine), 10)
        assertEquals(listOf("mine"), collector.candidates().map { it.id }, "seule la TV au tag du code tapé est une candidate")
        val a = tryOn(mineTv, mine)
        assertIs<Attempt.Reached>(a)
        assertEquals("CastBridge TV salon", a.tvName)
        assertEquals(DeviceRequestText.complete(FakeTv.fullRequest), a.requestText, "la demande complète est lue dans le canal chiffré")
        assertEquals(0, neighbour.reads); assertEquals(0, neighbour.authorized.size)
        assertEquals(0, countedFailures(neighbour), "aucun code faux n'a été compté chez la TV du voisin : elle n'a même pas été approchée")
        assertEquals(0, countedFailures(mineTv))
    }

    @Test fun twoTvsOfTheSameTagAreTriedStrongestFirstTheWrongOneRefusesAndTheRightOneIsReached() {
        val wrongTv = FakeTv(code = colliding); val mineTv = FakeTv(code = mine)
        val collector = BleSearch.Collector(mine)
        collector.add(sighting("wrong", -40, colliding, 0x91), 0); collector.add(sighting("mine", -60, mine), 5)
        val order = collector.candidates()
        assertEquals(listOf("wrong", "mine"), order.map { it.id }, "la plus forte d'abord, même si ce n'est pas la bonne")
        val observed = ArrayList<Cause>()
        val attempts = ArrayList<Attempt>()
        var reached: Attempt.Reached? = null
        for (c in order) {
            val a = if (c.id == "wrong") tryOn(wrongTv, mine) else tryOn(mineTv, mine)
            if (a is Attempt.Reached) { reached = a; break }
            attempts += a; observed += BleSearch.causeOf(a)
        }
        assertNotNull(reached, "la bonne TV est jointe après le refus de l'autre")
        assertEquals(listOf(Cause(Cause.Kind.CODE_REFUSED)), observed, "ce que la voie rapporte pendant la recherche : un code refusé")
        assertEquals(1, countedFailures(wrongTv), "le refus a coûté UN code faux chez la TV au même tag, et rien ailleurs")
        assertEquals(0, countedFailures(mineTv))
    }

    @Test fun ifOnlyTheCollidingTvIsThereTheRouteEndsOnTheWrongCodeSentence() {
        val wrongTv = FakeTv(code = colliding)
        val a = tryOn(wrongTv, mine)
        assertEquals(BtActWire.Err.BAD_CODE, assertIs<Attempt.Refused>(a).err)
        val verdict = BleSearch.verdict(listOf(a), 1)
        assertEquals(Cause(Cause.Kind.CODE_REFUSED), verdict)
        assertEquals("La TV a refusé ce code : relisez les 6 chiffres affichés sur l'écran de la TV.", verdict.text(ActivationRoutePlan.Route.BLE, ""))
    }

    @Test fun fiveRetriesOnTheCollidingTvEndOnTheLockSentenceAndTheSixthIsTurnedAwayBeforeAnyComputation() {
        val wrongTv = FakeTv(code = colliding)
        val answers = (1..5).map { tryOn(wrongTv, mine) }
        for (i in 0..3) assertEquals(BtActWire.Err.BAD_CODE, assertIs<Attempt.Refused>(answers[i]).err, "essai ${i + 1}")
        val fifth = assertIs<Attempt.Refused>(answers[4])
        assertEquals(BtActWire.Err.LOCKED, fifth.err, "le cinquième refus verrouille")
        assertEquals(60L, fifth.seconds)
        val verdict = BleSearch.verdict(listOf(fifth), 1)
        assertEquals("Trop de codes faux : la TV attend 60 s avant de reprendre.", verdict.text(ActivationRoutePlan.Route.BLE, ""))
        val sixth = assertIs<Attempt.Refused>(tryOn(wrongTv, mine))
        assertEquals(BtActWire.Err.LOCKED, sixth.err)
        // the plan turns that into its failure message with the advice to wait
        var s = ActivationRoutePlan.start(ActivationRoutePlan.Facts(mine, 34, wifiOn = false, onWifi = false, bt = ActivationRoutePlan.Bt.OFF, ble = ActivationRoutePlan.Ble.SEARCH), 0)
        s = ActivationRoutePlan.reduce(s.run, ActivationRoutePlan.Event.Failed(ActivationRoutePlan.Route.BLE, verdict, 3_000))
        val message = (s.run.phase as ActivationRoutePlan.Phase.Failed).message
        assertTrue("• Bluetooth sans appairage : Trop de codes faux : la TV attend 60 s avant de reprendre." in message, message)
        assertTrue(message.lines().last().startsWith("Attendez quelques minutes"), message)
        assertFalse(mine in message, "le code ne figure dans aucun texte")
    }

    @Test fun aTvThatIsNotThereAtAllEndsOnTheNotFoundSentence() {
        val collector = BleSearch.Collector(mine)
        collector.add(sighting("neighbour", -40, "111111"), 0)
        assertTrue(collector.candidates().isEmpty() && collector.firstAtMs == null)
        assertEquals(Cause(Cause.Kind.BLE_NOT_FOUND), BleSearch.verdict(emptyList(), 0))
    }

    @Test fun theTermsOfTheTvAreToldBeforeTheCodeAndEndOnTheirOwnSentence() {
        val tv = FakeTv(code = mine, terms = false)
        val a = assertIs<Attempt.Refused>(tryOn(tv, mine))
        assertEquals(BtActWire.Err.TERMS, a.err)
        assertEquals("Les conditions d'usage ne sont pas encore acceptées sur la TV : cochez la case sur son écran d'activation.", BleSearch.verdict(listOf(a), 1).text(ActivationRoutePlan.Route.BLE, ""))
        assertEquals(0, countedFailures(tv))
    }

    /** How many wrong codes the TV holds for the phone's peer (probes until the lock, then forgets: use on a TV whose peer is not locked). */
    private fun countedFailures(tv: FakeTv): Int {
        val key = castbridge.core.tv.activation.ActivationAttemptGate.bluetoothPeer(FakeTv.PEER)
        var bad = 0
        while (tv.guard.recordFailure(key) != castbridge.core.tv.PinGuard.Result.LOCKED) bad++
        return 4 - bad
    }
}
