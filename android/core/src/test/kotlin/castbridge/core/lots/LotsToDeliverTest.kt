package castbridge.core.lots

import kotlin.test.*

class LotsToDeliverTest {
    private fun m(scope: String, v: Int = 1, size: Long = 100, feature: String = "langues") = LotMeta(LotId(feature, scope), v, size, "ab".repeat(32), "t $scope")
    private val prof = LotId("langues", "fr-en-a1")
    private val pin = LotId("langues", "fr-es-a1")
    private fun profileNeed() = listOf(Need(prof, 100))

    @Test fun profileLotOnly() {
        val d = LotsToDeliver.decide(profileNeed(), emptyList(), listOf(m("fr-en-a1")), emptyList())
        assertEquals(listOf(prof), d.toSend)
    }
    @Test fun pinnedOnlyIsDeliveredWithoutAnyProfile() {
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1")), emptyList())
        assertEquals(listOf(pin), d.toSend)
        assertEquals(LotsToDeliver.PINNED_PRIORITY, d.needs.single().priority)
    }
    @Test fun bothAreDeliveredProfileFirstAndNoDuplicate() {
        val d = LotsToDeliver.decide(profileNeed(), listOf(pin, prof), listOf(m("fr-en-a1"), m("fr-es-a1")), emptyList())
        assertEquals(setOf(prof, pin), d.toSend.toSet()); assertEquals(2, d.needs.size)
        assertEquals(100, d.needs.first { it.id == prof }.priority, "the profile keeps its own priority")
    }
    @Test fun pinnedNotInstalledYetWaitsAndIsNotAnError() {
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), emptyList(), emptyList())
        assertEquals(LotsToDeliver.Action.NOT_INSTALLED, d.of(pin)!!.action); assertTrue(d.toSend.isEmpty())
    }
    @Test fun alreadyOnTvSameVersionIsNotSentAgain() {
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1")), listOf(m("fr-es-a1")))
        assertEquals(LotsToDeliver.Action.ALREADY_ON_TV, d.of(pin)!!.action); assertTrue(d.toSend.isEmpty())
    }
    @Test fun olderVersionOnTvIsSentAgain() {
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1", v = 2)), listOf(m("fr-es-a1", v = 1)))
        assertEquals(LotsToDeliver.Action.SEND, d.of(pin)!!.action)
    }
    @Test fun overBudgetIsSkippedWithAReasonNeverEvictingTheTv() {
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1", size = 900)), listOf(m("autre", size = 500)), starterBytes = 600, budget = 1000)
        val it = d.of(pin)!!
        assertEquals(LotsToDeliver.Action.OVER_BUDGET, it.action); assertTrue(it.reason!!.isNotBlank())
        assertTrue(d.toSend.isEmpty())
    }
    @Test fun trialTvRefusingLanguesIsReportedWithTheTvReason() {
        val rej = listOf(LotRejection(pin, 1, "Édition d'essai : les leçons de langue ne sont pas incluses", 5))
        val d = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1")), emptyList(), rejected = rej)
        assertEquals(LotsToDeliver.Action.REFUSED_BY_TV, d.of(pin)!!.action)
        assertEquals("Édition d'essai : les leçons de langue ne sont pas incluses", d.of(pin)!!.reason)
        val newer = LotsToDeliver.decide(emptyList(), listOf(pin), listOf(m("fr-es-a1", v = 2)), emptyList(), rejected = rej)
        assertEquals(LotsToDeliver.Action.SEND, newer.of(pin)!!.action, "a newer version is worth another try")
    }
    @Test fun nothingAtAllMeansNothingToSend() {
        val d = LotsToDeliver.decide(emptyList(), emptyList(), listOf(m("x")), emptyList())
        assertTrue(d.items.isEmpty() && d.toSend.isEmpty())
    }
}

class PinnedLotsTest {
    private val a = LotId("langues", "fr-en-a1"); private val b = LotId("langues", "fr-es-a1")
    @Test fun addRemoveListAndSurviveRestart() {
        val f = Kit.tmp().resolve("pins.json")
        val p = PinnedLots(FileQueueStore(f))
        assertTrue(p.add(b)); assertTrue(p.add(a)); assertFalse(p.add(a), "idempotent")
        assertEquals(listOf(a, b), p.list()); assertTrue(p.contains(a))
        assertTrue(p.remove(b)); assertFalse(p.remove(b))
        val again = PinnedLots(FileQueueStore(f))
        assertEquals(listOf(a), again.list(), "survives a restart")
        f.parentFile.deleteRecursively()
    }
    @Test fun aCorruptFileIsAnEmptySetNotACrash() {
        val f = Kit.tmp().resolve("pins.json"); f.writeText("{ not json")
        assertTrue(PinnedLots(FileQueueStore(f)).list().isEmpty()); f.parentFile.deleteRecursively()
    }
}

class SendButtonTest {
    @Test fun rules() {
        fun b(stage: LotStage, held: Boolean = true) = LotsToDeliver.sendButton(stage, held)
        assertFalse(b(LotStage.NOT_DOWNLOADED, held = false).visible)
        assertTrue(b(LotStage.ON_PHONE).let { it.visible && it.enabled && it.label == "Envoyer à la TV" })
        assertTrue(b(LotStage.WAITING_TV).let { it.visible && it.enabled })
        assertTrue(b(LotStage.UP_TO_DATE).let { it.visible && !it.enabled && it.label == "Déjà sur la TV" })
        assertTrue(b(LotStage.SENDING).let { it.visible && !it.enabled && it.label == "Envoi en cours" })
        assertFalse(b(LotStage.REFUSED).visible, "« Réessayer » already covers it")
    }
}
