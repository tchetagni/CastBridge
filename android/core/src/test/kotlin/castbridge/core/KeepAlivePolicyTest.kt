package castbridge.core

import castbridge.core.tv.*
import kotlin.test.*

class KeepAlivePolicyTest {
    @Test fun restartsOnlyWhenDeadAndWanted() {
        assertTrue(KeepAlivePolicy.shouldRestart(alive = false, autostart = true, stoppedByOwner = false))
        assertFalse(KeepAlivePolicy.shouldRestart(true, true, false), "déjà vivant : jamais deux instances")
        assertFalse(KeepAlivePolicy.shouldRestart(false, false, false), "démarrage automatique désactivé")
        assertFalse(KeepAlivePolicy.shouldRestart(false, true, true), "arrêt volontaire")
        assertEquals(KeepAlivePolicy.Decision.STOPPED_BY_OWNER, KeepAlivePolicy.decide(true, false, true))
        assertEquals(KeepAlivePolicy.Decision.AUTOSTART_OFF, KeepAlivePolicy.decide(true, false, false))
        assertEquals(KeepAlivePolicy.Decision.ALIVE, KeepAlivePolicy.decide(true, true, false))
    }

    @Test fun constants() {
        assertEquals(15 * 60_000L, KeepAlivePolicy.PERIOD_MS); assertEquals(3_000L, KeepAlivePolicy.TASK_REMOVED_DELAY_MS)
        assertTrue(KeepAlivePolicy.BOOT_WAKE_MS <= 30_000L)
    }

    @Test fun batteryOfferedOnce() {
        assertTrue(BatteryExemptionPolicy.shouldOffer(false, true, false, 34))
        assertFalse(BatteryExemptionPolicy.shouldOffer(true, true, false, 34), "déjà exempté")
        assertFalse(BatteryExemptionPolicy.shouldOffer(false, false, false, 34), "pas d'écran sur ce boîtier")
        assertFalse(BatteryExemptionPolicy.shouldOffer(false, true, true, 34), "déjà proposé (refus mémorisé)")
        assertFalse(BatteryExemptionPolicy.shouldOffer(false, true, false, 22))
    }

    @Test fun infoLine() {
        val now = 10_000_000L
        assertEquals("Service : vivant depuis 1 h 12 · démarré au boot", ServiceInfoText.line(now - 72 * 60_000L, now, "BOOT"))
        assertEquals("Service : vivant depuis 5 min", ServiceInfoText.line(now - 5 * 60_000L, now, null))
        assertEquals("Service : vivant depuis moins d'une minute · relancé par le chien de garde", ServiceInfoText.line(now - 10_000L, now, "WATCHDOG"))
        assertEquals("Service : vivant depuis 2 h 00", ServiceInfoText.line(now - 120 * 60_000L, now, "x"))
        assertEquals("Service : arrêté", ServiceInfoText.line(null, now, "BOOT"))
        assertEquals("BOOT", ServiceInfoText.reasonOf(BootPolicy.QUICKBOOT)); assertEquals("REPLACED", ServiceInfoText.reasonOf(BootPolicy.REPLACED))
        assertNull(ServiceInfoText.reasonOf("android.intent.action.SCREEN_ON"))
    }

    @Test fun bootPolicyUnchangedAndLockedBootIgnored() {
        assertFalse(BootPolicy.shouldStart("android.intent.action.LOCKED_BOOT_COMPLETED", true), "pas de démarrage en Direct Boot (stockage chiffré)")
        assertFalse(BootPolicy.shouldStart(BootPolicy.REPLACED, false))
    }
}
