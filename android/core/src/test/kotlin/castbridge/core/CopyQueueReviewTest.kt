package castbridge.core

import castbridge.core.tv.QueueCancel
import castbridge.core.tv.QueueOutcome
import castbridge.core.tv.QueueRefused
import castbridge.core.tv.QueueTexts
import castbridge.core.tv.TransferQueueModel
import castbridge.core.tv.UploadSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R-09, seconde relecture Opus de 2925780 : réservation périmée récupérée, cause « limite de durée » non écrasable, annulation ciblée par génération. */
class CopyQueueReviewTest {
    private var clock = 0L

    // (A) une réservation dont le propriétaire est mort ou bloqué ne bloque pas tous les envois suivants
    @Test fun aStaleReservationIsTakenOverAndItsOldOwnerLosesIt() {
        val slot = UploadSlot(now = { clock }, staleMs = 300_000)
        val t1 = slot.tryReserve("a")!!
        clock = 200_000; assertNull(slot.tryReserve("b"), "propriétaire vivant : refusé")
        assertTrue(slot.touch(t1), "signe de vie du propriétaire")
        clock = 450_000; assertNull(slot.tryReserve("b"), "250 s depuis le dernier signe de vie : encore à lui")
        clock = 800_000
        val t2 = assertNotNull(slot.tryReserve("b"), "aucun signe de vie depuis 10 min : réservation périmée reprise")
        assertFalse(slot.release(t1), "l'ancien propriétaire ne peut plus libérer")
        assertFalse(slot.touch(t1)); assertTrue(slot.holds(t2)); assertEquals("b", slot.holder())
    }

    @Test fun aLiveOwnerKeepsItsReservationAsLongAsItGivesSignsOfLife() {
        val slot = UploadSlot(now = { clock }, staleMs = 300_000)
        val t = slot.tryReserve("long")!!
        repeat(20) { clock += 120_000; assertTrue(slot.touch(t)); assertNull(slot.tryReserve("x")) }
        assertTrue(slot.release(t)); assertNotNull(slot.tryReserve("x"))
    }

    // (B) « limite de durée » posée par le propriétaire n'est pas écrasée par un « annulé » tardif de son fil
    @Test fun theTimeLimitCauseWinsOverALateCancelledResult() {
        assertEquals(QueueTexts.TIME_LIMIT, QueueOutcome.finalCause(QueueTexts.TIME_LIMIT, "annulé"))
        assertEquals("annulé", QueueOutcome.finalCause(null, "annulé"))
        assertEquals(QueueOutcome.Kind.PAUSE_TIME_LIMIT, QueueOutcome.of(QueueTexts.TIME_LIMIT, cancelAsked = false, backgroundRefusal = false),
            "remis en attente, file en pause avec le texte du délai")
    }

    @Test fun outcomesOfALaunchedFile() {
        assertEquals(QueueOutcome.Kind.DONE, QueueOutcome.of(null, false, false))
        assertEquals(QueueOutcome.Kind.CANCELLED, QueueOutcome.of("annulé", cancelAsked = true, backgroundRefusal = false))
        assertEquals(QueueOutcome.Kind.RETRY_SOON, QueueOutcome.of(QueueTexts.PREVIOUS_ENDING, false, false), "relancé, jamais en échec (mineur 4)")
        assertEquals(QueueOutcome.Kind.PAUSE_BACKGROUND, QueueOutcome.of("Service refusé par le système : x", false, backgroundRefusal = true))
        assertEquals(QueueOutcome.Kind.FAILED, QueueOutcome.of("Un autre fichier du même nom est déjà sur la TV.", false, false))
    }

    // (C) « Annuler » d'un fichier relancé qui attend son tour n'arrête pas l'envoi lancé pour une tentative précédente
    @Test fun cancelTargetsTheLaunchedAttemptOnly() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a", 1, false)
        m.start(a.id); m.finish(a.id, false, "réseau"); m.retry(a.id)
        val again = m.item(a.id)!!
        assertFalse(QueueCancel.isLaunched(a.id, 0, again), "tentative 0 lancée, tentative 1 en attente : pas la même génération")
        assertTrue(QueueCancel.isLaunched(a.id, 1, again))
        assertFalse(QueueCancel.isLaunched(-1, 0, again), "rien de lancé")
    }

    // mineur 7 : un autre emplacement de la TV n'est pas fusionné en silence
    @Test fun theSameFileToAnotherDestinationIsRefusedWithItsReason() {
        val m = TransferQueueModel()
        m.enqueue("u1", "a.mkv", 1, false, tvName = "Salon", host = "h", target = "usb-1")
        val e = assertFailsWith<QueueRefused> { m.enqueue("u1", "a.mkv", 1, false, tvName = "Salon", host = "h", target = "internal") }
        assertTrue(e.message!!.contains("autre emplacement"), e.message)
    }
}
