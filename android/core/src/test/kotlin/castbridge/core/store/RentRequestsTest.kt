package castbridge.core.store

import castbridge.core.lots.ClockDoubt
import castbridge.core.lots.ExpiryReason
import castbridge.core.lots.LotFamily
import castbridge.core.lots.MemoryQueueStore
import castbridge.core.lots.QueueStore
import castbridge.core.lots.RentalContract
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.RentalWarning
import castbridge.core.store.RentRequest.Kind
import castbridge.core.store.RentRequest.Origin
import castbridge.core.store.RentRequests.Answer
import castbridge.core.store.RentRequests.Created
import castbridge.core.store.RentRequests.Facts
import castbridge.core.store.RentRequests.Outcome
import castbridge.core.store.RentRequests.State
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.fail

/** File des demandes de location de la TV (w17-03) : refus du cœur, nonce, acquittement, expiration, rapprochement, bornes, corruption. */
class RentRequestsTest {
    private val day = 24L * 3600 * 1000
    private val t0 = 1_800_000_000_000L
    private var clock = t0
    private var counter = 0
    private val store = MemoryQueueStore()
    private val tvId = "0123456789abcdef"
    private fun rr(s: QueueStore = MemoryQueueStore(), random: () -> String = { "%08x".format(++counter) }, tv: String = tvId) = RentRequests(s, tv, { clock }, random)

    private fun status(bundle: String, period: Long = t0 - 2 * day, state: RentalState = RentalState.ACTIVE, hours: Boolean = false, endsAt: Long = t0 + 10 * day, usage: Long = 360) = RentalStatus(
        RentalContract("loc-$bundle@$period", "loc-$bundle", listOf(bundle), "lic", period, period, endsAt, 0, if (hours) usage else 0, 3, ""),
        state, if (state == RentalState.EXPIRED) ExpiryReason.DATE else null, null as ClockDoubt?, null, null, RentalWarning.NONE, "")

    private fun facts(rentals: List<RentalStatus> = emptyList(), family: LotFamily? = LotFamily.RESERVED, trial: Boolean = false, kid: Boolean = false, pilotEnd: Long? = null, max: Int = 3, origin: Origin = Origin.TV,
                      shelf: StoreCatalog.Shelf = StoreCatalog.Shelf.APPRENDRE, lots: List<LotFamily?> = listOf(family)) =
        Facts(trial, kid, shelf, lots, rentals, max, pilotEnd, origin)

    private fun RentRequests.ok(bundle: String = "classe-cm2", choice: String = "12h", f: Facts = facts(), extend: Boolean = false): RentRequests.Entry =
        (create(bundle, choice, f, extend).let { it as? Created.Ok ?: fail("refusé : $it") }).entry

    private fun RentRequests.refusal(bundle: String = "classe-cm2", choice: String = "12h", f: Facts = facts(), extend: Boolean = false): Refusal? =
        (create(bundle, choice, f, extend) as? Created.Refused)?.reason

    // ---------------------------------------------------------------- acceptation et persistance
    @Test fun createAcceptsAndStoresPending() {
        val e = rr().ok()
        assertEquals(State.PENDING, e.state)
        assertEquals(RentRequest("0123456789abcdef", "classe-cm2", "12h", Kind.NEW, 0, "00000001", t0, Origin.TV), e.request)
        assertEquals(t0, e.createdAt)
    }

    @Test fun createPersistsAndAnotherInstanceReadsIt() {
        val e = rr(store).ok()
        val again = rr(store)
        assertEquals(listOf(e.request), again.entries().map { it.request })
        assertEquals(State.PENDING, again.entries().single().state)
        assertFalse(again.degraded)
    }

    @Test fun phoneOriginIsKeptInTheRequest() {
        assertEquals(Origin.PHONE, rr().ok(f = facts(origin = Origin.PHONE)).request.origin)
    }

    // ---------------------------------------------------------------- refus
    @Test fun trialTvIsRefused() {
        val q = rr(); assertEquals(Refusal.TRIAL_TV, q.refusal(f = facts(trial = true))); assertEquals(emptyList(), q.entries())
    }

    @Test fun kidProfileIsRefused() {
        val q = rr(); assertEquals(Refusal.KID_PROFILE, q.refusal(f = facts(kid = true))); assertEquals(emptyList(), q.entries())
    }

    @Test fun freeBundleIsRefused() {
        assertEquals(Refusal.FREE_BUNDLE, rr().refusal(bundle = "langues-zh-a0", f = facts(family = LotFamily.FREE)))
    }

    @Test fun unknownFamilyIsRefused() {
        assertEquals(Refusal.UNKNOWN_FAMILY, rr().refusal(f = facts(family = null)))
    }

    @Test fun overLimitCountsOnlyUsableContracts() {
        val three = listOf(status("a"), status("b"), status("c", state = RentalState.GRACE))
        assertEquals(Refusal.OVER_LIMIT, rr().refusal(f = facts(three)))
        val withExpired = listOf(status("a"), status("b"), status("c", state = RentalState.EXPIRED))
        assertEquals(Kind.NEW, rr().ok(f = facts(withExpired)).request.kind)
        assertEquals(Refusal.OVER_LIMIT, rr().refusal(f = facts(three.take(2), max = 2)))
    }

    @Test fun overLimitDoesNotBlockAnExtension() {
        val three = listOf(status("classe-cm2"), status("b"), status("c"))
        assertEquals(Kind.EXTEND, rr().ok("classe-cm2", "7j", facts(three)).request.kind)
    }

    @Test fun sameBundleOtherUnitIsRefused() {
        assertEquals(Refusal.SAME_BUNDLE_OTHER_UNIT, rr().refusal(choice = "7j", f = facts(listOf(status("classe-cm2", hours = true)))))
        assertEquals(Refusal.SAME_BUNDLE_OTHER_UNIT, rr().refusal(choice = "6h", f = facts(listOf(status("classe-cm2")))))
        assertEquals(Refusal.SAME_BUNDLE_OTHER_UNIT, rr().refusal(choice = "defaut", f = facts(listOf(status("classe-cm2", hours = true)))))
    }

    @Test fun sameUnitIsAnExtensionCarryingThePeriodOfTheContract() {
        val p = t0 - 3 * day
        val d = rr().ok(choice = "7j", f = facts(listOf(status("classe-cm2", period = p)))).request
        assertEquals(Kind.EXTEND, d.kind); assertEquals(p, d.period)
        val h = rr().ok(choice = "6h", f = facts(listOf(status("classe-cm2", period = p, hours = true)))).request
        assertEquals(Kind.EXTEND, h.kind); assertEquals(p, h.period)
        assertEquals(Kind.EXTEND, rr().ok(choice = "defaut", f = facts(listOf(status("classe-cm2", period = p)))).request.kind)
    }

    @Test fun explicitExtensionOfAnEndedContractIsRefusedAsEnded() {
        val ended = facts(listOf(status("classe-cm2", state = RentalState.EXPIRED)))
        val r = assertIs<Created.Refused>(rr().create("classe-cm2", "7j", ended, extend = true))
        assertEquals(Refusal.ENDED, r.reason)
        assertTrue(r.message.contains("relouer"), r.message)
        assertEquals(Refusal.NO_CONTRACT, rr().refusal(f = facts(), extend = true))
    }

    @Test fun rentingAgainAfterTheEndIsANewRequest() {
        val e = rr().ok(choice = "6h", f = facts(listOf(status("classe-cm2", state = RentalState.EXPIRED)))).request
        assertEquals(Kind.NEW, e.kind); assertEquals(0L, e.period)
    }

    @Test fun pilotEndedRefusesOnlyOncePassed() {
        assertEquals(Refusal.PILOT_ENDED, rr().refusal(f = facts(pilotEnd = t0 - 1)))
        assertEquals(Kind.NEW, rr().ok(f = facts(pilotEnd = t0)).request.kind)
        assertEquals(Kind.NEW, rr().ok(f = facts(pilotEnd = null)).request.kind)
        assertEquals(Refusal.PILOT_ENDED, rr().refusal(choice = "7j", f = facts(listOf(status("classe-cm2")), pilotEnd = t0 - 1)))
    }

    @Test fun duplicateBundleAndChoiceWhilePending() {
        val q = rr(); q.ok()
        assertEquals(Refusal.DUPLICATE, q.refusal())
        assertEquals(State.PENDING, q.ok(choice = "24h").state)                     // autre choix : permis
        assertEquals(State.PENDING, q.ok(bundle = "classe-cm1").state)              // autre bouquet : permis
        q.ack(q.entries().first().request.nonce, Answer.REFUSED)
        assertEquals(State.PENDING, q.ok().state)                                   // plus en attente : permis
    }

    @Test fun queueIsFullAtTwentyPending() {
        val q = rr()
        for (i in 1..20) q.ok(bundle = "classe-$i", f = facts(max = 99))
        assertEquals(Refusal.QUEUE_FULL, q.refusal(bundle = "classe-21", f = facts(max = 99)))
        assertEquals(20, q.entries().size)
        q.ack(q.entries().first().request.nonce, Answer.REFUSED)
        assertEquals(State.PENDING, q.ok(bundle = "classe-21", f = facts(max = 99)).state)
    }

    @Test fun choiceMustBeOneOfThePickerValues() {
        for (c in listOf("5j", "13h", "30j", "97h", "2j", "999h")) assertEquals(Refusal.BAD_CHOICE, rr().refusal(choice = c), c)       // grammaire correcte, valeur hors sélecteur
        for (c in listOf("0j", "defaut2", "12", "07j", "")) assertEquals(Refusal.MALFORMED, rr().refusal(choice = c), c)              // grammaire incorrecte
        for (c in listOf("defaut", "1j", "3j", "7j", "14j", "1h", "3h", "6h", "12h", "24h", "48h", "96h")) assertEquals(State.PENDING, rr().ok(choice = c).state, c)
    }

    @Test fun malformedBundleOrTvIsRefused() {
        assertEquals(Refusal.MALFORMED, rr().refusal(bundle = "Classe CM2"))
        assertEquals(Refusal.MALFORMED, rr(tv = "zz").refusal())
    }

    @Test fun refusalPrecedenceIsPinned() {
        assertEquals(Refusal.TRIAL_TV, rr().refusal(f = facts(trial = true, kid = true, family = null)))
        assertEquals(Refusal.KID_PROFILE, rr().refusal(f = facts(kid = true, family = LotFamily.FREE)))
        assertEquals(Refusal.UNKNOWN_FAMILY, rr().refusal(f = facts(family = null, pilotEnd = t0 - 1)))
        assertEquals(Refusal.FREE_BUNDLE, rr().refusal(f = facts(family = LotFamily.FREE, pilotEnd = t0 - 1)))
        assertEquals(Refusal.PILOT_ENDED, rr().refusal(f = facts(listOf(status("a"), status("b"), status("c")), pilotEnd = t0 - 1)))
    }

    @Test fun everyRefusalCarriesAFrenchSentence() {
        val cases = listOf<Pair<Refusal, Created>>(
            Refusal.TRIAL_TV to rr().create("classe-cm2", "12h", facts(trial = true)), Refusal.KID_PROFILE to rr().create("classe-cm2", "12h", facts(kid = true)),
            Refusal.FREE_BUNDLE to rr().create("classe-cm2", "12h", facts(family = LotFamily.FREE)), Refusal.UNKNOWN_FAMILY to rr().create("classe-cm2", "12h", facts(family = null)),
            Refusal.OVER_LIMIT to rr().create("classe-cm2", "12h", facts(listOf(status("a"), status("b"), status("c")))),
            Refusal.SAME_BUNDLE_OTHER_UNIT to rr().create("classe-cm2", "7j", facts(listOf(status("classe-cm2", hours = true)))),
            Refusal.PILOT_ENDED to rr().create("classe-cm2", "12h", facts(pilotEnd = t0 - 1)), Refusal.BAD_CHOICE to rr().create("classe-cm2", "5j", facts()),
            Refusal.MALFORMED to rr().create("X", "12h", facts()), Refusal.NO_CONTRACT to rr().create("classe-cm2", "12h", facts(), extend = true),
        )
        for ((reason, c) in cases) {
            val r = assertIs<Created.Refused>(c)
            assertEquals(reason, r.reason)
            assertTrue(r.message.length > 10 && r.message.first().let { it.isUpperCase() || it.isDigit() }, "$reason : ${r.message}")
            assertFalse(Regex("sender|receiver", RegexOption.IGNORE_CASE).containsMatchIn(r.message), r.message)
        }
        val q = rr(); q.ok()
        assertTrue(assertIs<Created.Refused>(q.create("classe-cm2", "12h", facts())).message.contains("Déjà"))
    }

    // ---------------------------------------------------------------- nonce
    @Test fun nonceIsRerolledOnCollisionAndAlwaysDistinct() {
        val seq = ArrayDeque(listOf("aaaaaaaa", "aaaaaaaa", "bbbbbbbb"))
        val q = rr(random = { seq.removeFirst() })
        val a = q.ok(bundle = "classe-a").request.nonce
        val b = q.ok(bundle = "classe-b").request.nonce            // même milliseconde, même horloge
        assertEquals("aaaaaaaa", a); assertEquals("bbbbbbbb", b)
    }

    @Test fun invalidRandomGivesStorageRefusalNotACrash() {
        val q = rr(random = { "NOPE" })
        assertEquals(Refusal.STORAGE, q.refusal()); assertEquals(emptyList(), q.entries())
    }

    @Test fun failingStoreGivesStorageRefusalAndKeepsNothing() {
        val failing = object : QueueStore { override fun load(): String? = null; override fun save(json: String) { throw IOException("disque plein") } }
        val q = rr(failing)
        assertEquals(Refusal.STORAGE, q.refusal()); assertEquals(emptyList(), q.entries())
    }

    // ---------------------------------------------------------------- acquittement
    @Test fun ackIsIdempotent() {
        val q = rr(); val n = q.ok().request.nonce
        assertEquals(RentRequests.AckResult(Outcome.APPLIED, State.ACCEPTED), q.ack(n, Answer.ACCEPTED))
        assertEquals(RentRequests.AckResult(Outcome.SAME, State.ACCEPTED), q.ack(n, Answer.ACCEPTED))
        assertEquals(State.ACCEPTED, q.entries().single().state)
    }

    @Test fun aSecondDifferentAnswerNeverChangesTheState() {
        val q = rr(); val n = q.ok().request.nonce
        q.ack(n, Answer.REFUSED)
        assertEquals(RentRequests.AckResult(Outcome.CONFLICT, State.REFUSED), q.ack(n, Answer.ACCEPTED))
        assertEquals(State.REFUSED, q.entries().single().state)
        val m = q.ok().request.nonce
        q.ack(m, Answer.ACCEPTED)
        assertEquals(RentRequests.AckResult(Outcome.CONFLICT, State.ACCEPTED), q.ack(m, Answer.REFUSED))
    }

    @Test fun unknownOrMalformedNonceIsUnknown() {
        val q = rr(); q.ok()
        assertEquals(RentRequests.AckResult(Outcome.UNKNOWN, null), q.ack("deadbeef", Answer.ACCEPTED))
        assertEquals(RentRequests.AckResult(Outcome.UNKNOWN, null), q.ack("../etc", Answer.ACCEPTED))
        assertEquals(State.PENDING, q.entries().single().state)
    }

    @Test fun ackIsPersisted() {
        val q = rr(store); val n = q.ok().request.nonce
        q.ack(n, Answer.ACCEPTED); clock += 1000
        assertEquals(State.ACCEPTED, rr(store).entries().single().state)
        assertEquals(t0, rr(store).entries().single().decidedAt)
    }

    // ---------------------------------------------------------------- expiration
    @Test fun pendingExpiresAfterSevenDaysOnly() {
        val q = rr(); q.ok()
        assertEquals(0, q.expire(t0 + 7 * day))
        assertEquals(State.PENDING, q.entries().single().state)
        assertEquals(1, q.expire(t0 + 7 * day + 1))
        assertEquals(State.EXPIRED, q.entries().single().state)
        assertEquals(0, q.expire(t0 + 30 * day))
    }

    @Test fun decidedRequestsNeverExpire() {
        val q = rr(); q.ack(q.ok().request.nonce, Answer.ACCEPTED)
        assertEquals(0, q.expire(t0 + 20 * day)); assertEquals(State.ACCEPTED, q.entries().single().state)
    }

    @Test fun aClockSetBackNeverExpiresEarly() {
        val q = rr(); q.ok()
        assertEquals(0, q.expire(t0 - 30 * day)); assertEquals(State.PENDING, q.entries().single().state)
        clock = t0 - 30 * day
        assertEquals(State.PENDING, q.entries().single().state)
        assertEquals(0, q.expire())
    }

    @Test fun anAckOnAStalePendingRequestDoesNotResurrectIt() {
        val q = rr(); val n = q.ok().request.nonce
        clock = t0 + 8 * day
        assertEquals(RentRequests.AckResult(Outcome.CONFLICT, State.EXPIRED), q.ack(n, Answer.ACCEPTED))
        assertEquals(State.EXPIRED, q.entries().single().state)
    }

    @Test fun expiredRequestAllowsANewOneForTheSameBundleAndChoice() {
        val q = rr(); q.ok(); clock = t0 + 8 * day; q.expire()
        assertEquals(State.PENDING, q.ok().state)
    }

    // ---------------------------------------------------------------- rapprochement
    @Test fun acceptedNewRequestBecomesFulfilledWhenAContractCoversTheBundle() {
        val q = rr(); val n = q.ok().request.nonce
        q.ack(n, Answer.ACCEPTED)
        assertEquals(0, q.reconcile(emptyList()))
        assertEquals(0, q.reconcile(listOf(status("autre"))))
        assertEquals(0, q.reconcile(listOf(status("classe-cm2", state = RentalState.EXPIRED))))
        assertEquals(1, q.reconcile(listOf(status("classe-cm2"))))
        assertEquals(State.FULFILLED, q.entries().single().state)
        assertEquals(0, q.reconcile(listOf(status("classe-cm2"))))
    }

    @Test fun refusedAndExpiredRequestsAreNeverFulfilled() {
        val q = rr(); q.ack(q.ok().request.nonce, Answer.REFUSED)
        q.ok(choice = "24h"); clock = t0 + 8 * day; q.expire()
        assertEquals(0, q.reconcile(listOf(status("classe-cm2"))))
        assertEquals(listOf(State.REFUSED, State.EXPIRED), q.entries().map { it.state }.sortedBy { it.ordinal })
    }

    @Test fun aLostAckDoesNotLeaveAPendingRequestWhenTheDeliveryArrived() {
        val q = rr(); val n = q.ok().request.nonce
        assertEquals(1, q.reconcile(listOf(status("classe-cm2"))))
        assertEquals(State.FULFILLED, q.entries().single().state)
        assertEquals(Outcome.SAME, q.ack(n, Answer.ACCEPTED).outcome)             // l'ack qui arrive enfin
        assertEquals(Outcome.CONFLICT, q.ack(n, Answer.REFUSED).outcome)
        assertEquals(emptySet(), q.pendingBundles())
    }

    @Test fun aLostAckOnAnExtensionIsReconciledToo() {
        val p = t0 - 3 * day
        val q = rr(); q.ok(choice = "7j", f = facts(listOf(status("classe-cm2", period = p, endsAt = t0 + 10 * day))))
        assertEquals(0, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 10 * day))))
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 17 * day))))
    }

    @Test fun anHourExtensionIsFulfilledByTheUsageEvenWhenTheEndDoesNotMove() {
        val p = t0 - 3 * day
        val q = rr(); val n = q.ok(choice = "6h", f = facts(listOf(status("classe-cm2", period = p, hours = true, usage = 360)))).request.nonce
        q.ack(n, Answer.ACCEPTED)
        assertEquals(0, q.reconcile(listOf(status("classe-cm2", period = p, hours = true, usage = 360))))
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, hours = true, usage = 720))))     // même fin (borne de sûreté)
    }

    @Test fun twoAcceptedHourExtensionsAreFulfilledOnlyByTheirOwnDelivery() {
        val p = t0 - 3 * day; val base = facts(listOf(status("classe-cm2", period = p, hours = true, usage = 360)))
        val q = rr(); val a = q.ok(choice = "6h", f = base).request.nonce; val b = q.ok(choice = "12h", f = base).request.nonce
        q.ack(a, Answer.ACCEPTED); q.ack(b, Answer.ACCEPTED)
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, hours = true, usage = 720))))      // 6 h livrées
        assertEquals(mapOf(a to State.FULFILLED, b to State.ACCEPTED), q.entries().associate { it.request.nonce to it.state })
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, hours = true, usage = 1440))))     // puis 12 h
        assertEquals(State.FULFILLED, q.entries().first { it.request.nonce == b }.state)
    }

    @Test fun twoAcceptedDayExtensionsAreFulfilledOnlyByTheirOwnDelivery() {
        val p = t0 - 3 * day; val base = facts(listOf(status("classe-cm2", period = p, endsAt = t0 + 10 * day)))
        val q = rr(); val a = q.ok(choice = "7j", f = base).request.nonce; val b = q.ok(choice = "14j", f = base).request.nonce
        q.ack(a, Answer.ACCEPTED); q.ack(b, Answer.ACCEPTED)
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 17 * day))))
        assertEquals(State.ACCEPTED, q.entries().first { it.request.nonce == b }.state)
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 31 * day))))
    }

    @Test fun anExtensionIsFulfilledOnlyWhenTheEndMovesOnSamePeriod() {
        val p = t0 - 3 * day
        val q = rr(); val e = q.ok(choice = "7j", f = facts(listOf(status("classe-cm2", period = p, endsAt = t0 + 10 * day))))
        q.ack(e.request.nonce, Answer.ACCEPTED)
        assertEquals(0, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 10 * day))))     // rien n'est arrivé
        assertEquals(0, q.reconcile(listOf(status("classe-cm2", period = p + 1, endsAt = t0 + 17 * day)))) // autre période
        assertEquals(1, q.reconcile(listOf(status("classe-cm2", period = p, endsAt = t0 + 17 * day))))
        assertEquals(State.FULFILLED, q.entries().single().state)
    }

    @Test fun fulfilledIsPersistedAndAckStaysIdempotentOnIt() {
        val q = rr(store); val n = q.ok().request.nonce
        q.ack(n, Answer.ACCEPTED); q.reconcile(listOf(status("classe-cm2")))
        assertEquals(State.FULFILLED, rr(store).entries().single().state)
        assertEquals(Outcome.SAME, q.ack(n, Answer.ACCEPTED).outcome)
        assertEquals(Outcome.CONFLICT, q.ack(n, Answer.REFUSED).outcome)
    }

    @Test fun pendingBundlesListsOnlyWhatIsStillPending() {
        val q = rr(); q.ok("classe-cm2"); q.ok("classe-cm1", "7j"); q.ok("classe-cp", "3j")
        q.ack(q.entries().first { it.request.bundle == "classe-cp" }.request.nonce, Answer.REFUSED)
        assertEquals(setOf("classe-cm2", "classe-cm1"), q.pendingBundles())
        clock = t0 + 8 * day; q.expire()
        assertEquals(emptySet(), q.pendingBundles())
    }

    // ---------------------------------------------------------------- bornes et corruption
    @Test fun decidedRequestsAreKeptThirtyDaysAtMost() {
        val q = rr(store); q.ack(q.ok().request.nonce, Answer.ACCEPTED)
        clock = t0 + 29 * day; q.ok(bundle = "classe-cm1")
        assertEquals(2, rr(store).entries().size)
        clock = t0 + 31 * day; q.ok(bundle = "classe-cp")
        assertEquals(listOf("classe-cm1", "classe-cp"), rr(store).entries().map { it.request.bundle }.sorted())
    }

    @Test fun atMostFiftyDecidedRequestsAreKeptNewestFirst() {
        val q = rr(store)
        for (i in 1..60) { clock += 1000; q.ack(q.ok(bundle = "classe-$i").request.nonce, Answer.REFUSED) }
        val kept = rr(store).entries()
        assertEquals(50, kept.size)
        assertTrue(kept.any { it.request.bundle == "classe-60" } && kept.none { it.request.bundle == "classe-10" })
    }

    @Test fun pendingRequestsAreNeverDroppedByTheBounds() {
        val q = rr(store); q.ok("classe-old")
        clock = t0 + 6 * day
        for (i in 1..60) { q.ack(q.ok(bundle = "classe-$i").request.nonce, Answer.REFUSED) }
        assertTrue(rr(store).entries().any { it.request.bundle == "classe-old" && it.state == State.PENDING })
    }

    @Test fun corruptFileGivesAnEmptyQueueAndDegradedNotAnException() {
        for (text in listOf("pas du json", "[1,2]", "{\"items\": 5}", "{", "")) {
            val q = rr(MemoryQueueStore(text))
            assertEquals(emptyList(), q.entries(), text); assertTrue(q.degraded, text)
            assertEquals(State.PENDING, q.ok().state)
        }
    }

    @Test fun anInvalidItemIsDroppedAndTheOthersKept() {
        val q = rr(store); val good = q.ok()
        val text = store.text!!.replace("\"items\":[", "\"items\":[{\"request\":\"n'importe quoi\",\"state\":\"PENDING\",\"createdAt\":1},")
        val again = rr(MemoryQueueStore(text))
        assertEquals(listOf(good.request), again.entries().map { it.request }); assertTrue(again.degraded)
    }

    @Test fun anAbsentFileIsAnEmptyQueueNotDegraded() {
        val q = rr(MemoryQueueStore(null)); assertEquals(emptyList(), q.entries()); assertFalse(q.degraded)
    }

    // ---------------------------------------------------------------- jamais un droit
    @Test fun noPublicSignatureCarriesARightAnActivationOrAContract() {
        val banned = listOf("Right", "Activation", "RentalContract")
        val classes = listOf(RentRequests::class.java) + RentRequests::class.java.declaredClasses.toList() + RentRequest::class.java + RentRequest::class.java.declaredClasses.toList()
        for (c in classes) {
            val types = c.methods.filter { !it.isSynthetic }.flatMap { listOf(it.genericReturnType.typeName) + it.genericParameterTypes.map { p -> p.typeName } } +
                c.declaredFields.filter { !it.isSynthetic }.map { it.genericType.typeName } + c.constructors.flatMap { k -> k.genericParameterTypes.map { it.typeName } }
            for (t in types) for (b in banned) assertFalse(t.contains(b), "${c.simpleName} expose $t")
        }
    }

    @Test fun theStoredFileHoldsNoSecretAndOnlyTheRequestFields() {
        val q = rr(store); q.ack(q.ok().request.nonce, Answer.ACCEPTED)
        val text = store.text!!.lowercase()
        for (word in listOf("pin", "token", "secret", "key", "hmac", "password", "signature", "box")) assertFalse(text.contains(word), word)
        assertNull(Regex("[A-Za-z0-9+/]{40,}").find(store.text!!), "aucune longue chaîne opaque")
        assertNotEquals(0, text.length)
    }

    // ---------------------------------------------------------------- correctifs d'audit
    @Test fun aLanguesBundleIsFreeEvenWithAReservedLot() {
        assertEquals(Refusal.FREE_BUNDLE, rr().refusal("langues-zh-a0", f = facts(shelf = StoreCatalog.Shelf.LANGUES, lots = listOf(LotFamily.RESERVED))))
        assertEquals(Refusal.FREE_BUNDLE, rr().refusal(f = facts(lots = listOf(LotFamily.RESERVED, LotFamily.FREE))))
        assertEquals(Refusal.FREE_BUNDLE, rr().refusal("langues-x", f = facts(shelf = StoreCatalog.Shelf.LANGUES, lots = listOf(null))))
    }

    @Test fun bundleFamilyRule() {
        val a = StoreCatalog.Shelf.APPRENDRE
        assertEquals(LotFamily.RESERVED, RentRequests.familyOf(a, listOf(LotFamily.RESERVED, LotFamily.RESERVED)))
        assertNull(RentRequests.familyOf(a, emptyList()))
        assertNull(RentRequests.familyOf(a, listOf(LotFamily.RESERVED, null)))
        assertEquals(LotFamily.FREE, RentRequests.familyOf(a, listOf(null, LotFamily.FREE)))
        assertEquals(LotFamily.FREE, RentRequests.familyOf(StoreCatalog.Shelf.LANGUES, emptyList()))
        assertEquals(LotFamily.RESERVED, RentRequests.familyOf(StoreCatalog.Shelf.QUIZ, listOf(LotFamily.RESERVED)))
    }

    @Test fun badChoiceComesBeforePilotEnded() {
        assertEquals(Refusal.BAD_CHOICE, rr().refusal(choice = "5j", f = facts(pilotEnd = t0 - 1)))
        assertEquals(Refusal.MALFORMED, rr().refusal(choice = "0j", f = facts(pilotEnd = t0 - 1)))
    }

    @Test fun pilotEndFollowsTheInjectedClockWhateverItSays() {
        val end = t0 + 5 * day
        clock = t0 - 90 * day; assertEquals(State.PENDING, rr().ok(f = facts(pilotEnd = end)).state)           // horloge fausse reculée : accepté
        clock = end + 1; assertEquals(Refusal.PILOT_ENDED, rr().refusal(f = facts(pilotEnd = end)))            // horloge fausse avancée : refusé
    }

    @Test fun expirationFollowsTheInjectedClockBothWays() {
        val q = rr(); q.ok()
        clock = t0 - 90 * day; assertEquals(0, q.expire())
        clock = t0 + 90 * day; assertEquals(1, q.expire())                                                     // l'appelant passe l'heure jugée (RentalEngine.judge)
    }

    @Test fun pendingNewRequestsCountInTheQuota() {
        val q = rr(); val f = facts(max = 1)
        q.ok("classe-a", "12h", f)
        assertEquals(State.PENDING, q.ok("classe-a", "24h", f).state)                                          // même bouquet : une seule place
        assertEquals(Refusal.OVER_LIMIT, q.refusal("classe-b", "12h", f))
        q.ack(q.entries().first().request.nonce, Answer.REFUSED); q.ack(q.entries()[1].request.nonce, Answer.REFUSED)
        assertEquals(State.PENDING, q.ok("classe-b", "12h", f).state)
    }

    @Test fun pendingNewAndUsableContractsAddUp() {
        val q = rr(); val f = facts(listOf(status("classe-x")), max = 2)
        q.ok("classe-a", "12h", f)
        assertEquals(Refusal.OVER_LIMIT, q.refusal("classe-b", "12h", f))
    }

    private class BackupStore(var text: String?, val fromBackup: Boolean) : BackupAwareStore {
        override val loadedFromBackup get() = fromBackup
        override fun load() = text
        override fun save(json: String) { text = json }
    }

    @Test fun aFileReadFromTheBackupIsDegradedAndNeverBringsBackAPendingRequest() {
        val q = rr(store); q.ack(q.ok("classe-a").request.nonce, Answer.ACCEPTED); q.ok("classe-b", "7j")
        val fromBak = rr(BackupStore(store.text, true))
        assertTrue(fromBak.degraded)
        assertEquals(listOf("classe-a:ACCEPTED"), fromBak.entries().map { "${it.request.bundle}:${it.state}" })
        assertFalse(rr(BackupStore(store.text, false)).degraded)
    }

    @Test fun requestsOfAnotherTvAreDroppedOnReload() {
        rr(store).ok()
        val other = rr(store, tv = "fedcba9876543210")
        assertEquals(emptyList(), other.entries()); assertTrue(other.degraded)
    }

    @Test fun boundsAreReappliedOnReload() {
        fun item(i: Int, state: String, at: Long, decided: Long) = mapOf("request" to RentRequest(tvId, "classe-$i", "12h", Kind.NEW, 0, "%08x".format(i), at, Origin.TV).canonical(), "state" to state, "createdAt" to at, "decidedAt" to decided, "baseEnd" to 0, "baseUsage" to 0)
        val items = (1..25).map { item(it, "PENDING", t0, 0) } + (26..90).map { item(it, "REFUSED", t0, t0 - it) } + item(91, "REFUSED", t0 - 40 * day, t0 - 40 * day)
        val q = rr(MemoryQueueStore(castbridge.core.net.JsonLite.write(mapOf("format" to RentRequests.FILE_FORMAT, "items" to items))))
        assertEquals(20, q.entries().count { it.state == State.PENDING })
        assertEquals(50, q.entries().count { it.state == State.REFUSED })
        assertTrue(q.entries().none { it.request.bundle == "classe-91" })
    }

    @Test fun aFailedWriteDuringAckRestoresTheStateAndAnotherTryApplies() {
        var failing = false
        val flaky = object : QueueStore { var text: String? = null; override fun load() = text; override fun save(json: String) { if (failing) throw IOException("plein"); text = json } }
        val q = rr(flaky); val n = q.ok().request.nonce
        failing = true
        assertEquals(RentRequests.AckResult(Outcome.STORAGE, State.PENDING), q.ack(n, Answer.ACCEPTED))
        assertEquals(State.PENDING, q.entries().single().state)
        assertEquals(State.PENDING, rr(flaky).entries().single().state)
        failing = false
        assertEquals(Outcome.APPLIED, q.ack(n, Answer.ACCEPTED).outcome)
    }
}
