package castbridge.core.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * « Tolérance de lecture » (2026-10-07) : quand ce qui lit une clé (la recherche du fichier d'activation, l'explorateur, la bibliothèque) échoue pendant que le volume est en
 * vérification (`checking`), il réessaie tout seul au `mounted` : UN SEUL réessai par montage, jamais de boucle.
 */
class UsbMountRetryTest {
    private val K = "A379-E209"

    @Test fun `a reader that failed during the check is told once when the key is mounted`() {
        val r = MountRetry()
        r.checkingStarted(K)
        assertTrue(r.failed("activation", K, MediaState.CHECKING))
        assertEquals(listOf("activation"), r.mounted(K))
    }

    @Test fun `the retry is given once per mount, a second mounted event gives nothing`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        assertEquals(listOf("activation"), r.mounted(K))
        assertEquals(emptyList(), r.mounted(K), "the mounted broadcast and the volume callback both fire: still one retry")
    }

    @Test fun `a retry that fails again is not retried, there is no loop`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("explorer", K, MediaState.CHECKING)
        assertEquals(listOf("explorer"), r.mounted(K))
        // the retry reads the mounted key and fails again: Android says « mounted », not « checking »
        assertFalse(r.failed("explorer", K, MediaState.MOUNTED))
        assertEquals(emptyList(), r.mounted(K))
        // even if the reader insists while the same mount is still the current one
        assertFalse(r.failed("explorer", K, MediaState.CHECKING), "the credit of this mount is spent")
        assertEquals(emptyList(), r.mounted(K))
    }

    @Test fun `a failure that did not happen during a check is nobody's business`() {
        val r = MountRetry()
        for (s in MediaState.values().filter { it != MediaState.CHECKING }) assertFalse(r.failed("activation", K, s), "$s")
        r.checkingStarted(K)
        assertEquals(emptyList(), r.mounted(K))
    }

    @Test fun `the next mount gets a new retry`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        assertEquals(listOf("activation"), r.mounted(K))
        // the key is pulled and plugged again: Android checks it again
        r.checkingStarted(K)
        assertTrue(r.failed("activation", K, MediaState.CHECKING))
        assertEquals(listOf("activation"), r.mounted(K), "one retry per mount, not one per life")
    }

    @Test fun `a reader that did not fail during this check is not retried for the previous one`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        assertEquals(listOf("activation"), r.mounted(K))
        r.checkingStarted(K)                         // the key is plugged again: this time the reader read it fine
        assertEquals(emptyList(), r.mounted(K), "nobody failed during this check: nothing to retry")
    }

    @Test fun `a new check renews the credit of a reader that already retried`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("library", K, MediaState.CHECKING)
        r.mounted(K)
        assertFalse(r.failed("library", K, MediaState.CHECKING), "same mount: spent")
        r.checkingStarted(K)
        assertTrue(r.failed("library", K, MediaState.CHECKING), "new check: renewed")
    }

    @Test fun `each reader has its own retry and each is told once`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        r.failed("explorer", K, MediaState.CHECKING)
        r.failed("activation", K, MediaState.CHECKING)          // twice before the mount: still one
        assertEquals(listOf("activation", "explorer"), r.mounted(K))
        assertEquals(emptyList(), r.mounted(K))
    }

    @Test fun `a key is not mixed up with another`() {
        val r = MountRetry()
        r.checkingStarted(K); r.checkingStarted("B")
        r.failed("activation", K, MediaState.CHECKING)
        assertEquals(emptyList(), r.mounted("B"), "the other key's mount is not a retry for this reader")
        assertEquals(listOf("activation"), r.mounted(K))
        assertEquals(emptyList(), r.mounted("never-seen"))
    }

    @Test fun `a key that goes away or cannot be mounted drops its waiting readers`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        r.gone(K)                                    // pulled during the check, or « unmountable »
        assertEquals(emptyList(), r.mounted(K), "a mount of something that came back later is a new story")
        r.checkingStarted(K)
        assertEquals(emptyList(), r.mounted(K), "nobody is waiting any more")
    }

    @Test fun `a reader that fails again in a new check waits again`() {
        val r = MountRetry()
        r.checkingStarted(K)
        r.failed("activation", K, MediaState.CHECKING)
        assertEquals(listOf("activation"), r.mounted(K))
        // the retry itself ran while Android started to check the key again
        r.checkingStarted(K)
        assertTrue(r.failed("activation", K, MediaState.CHECKING))
        assertEquals(listOf("activation"), r.mounted(K))
        assertEquals(emptyList(), r.mounted(K))
    }

    @Test fun `many failures during one check cost one retry`() {
        val r = MountRetry()
        r.checkingStarted(K)
        repeat(50) { r.failed("activation", K, MediaState.CHECKING) }
        assertEquals(listOf("activation"), r.mounted(K))
        assertEquals(emptyList(), r.mounted(K))
    }
}
