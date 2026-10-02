package castbridge.core

import castbridge.core.phone.*
import castbridge.core.trust.CopyAndPlay.Edition
import castbridge.core.tv.Mp4Atoms.Layout
import castbridge.core.tv.Progressive
import kotlin.test.*

/**
 * R-08 « Copier et lire ne lance pas la lecture sur la TV avant la fin de la copie » : quel chemin d'envoi « Copier sur la TV et lire » prend
 * (table), les seuils de relais du téléphone (durée inconnue depuis « Ouvrir avec ») et les phrases affichées.
 */
class CopyRouteTest {
    private val video = CastSource(MediaKind.VIDEO, "content", "media", 1_000_000_000)
    private val audio = CastSource(MediaKind.AUDIO, "content", "media", 8_000_000)
    private val photo = CastSource(MediaKind.IMAGE, "content", "media", 3_000_000)

    private data class Row(val label: String, val f: CopyRoute.Facts, val transport: CopyTransport, val handoff: Boolean)

    private val rows = listOf(
        Row("vidéo lue sur la TV, transfert rapide activé : dans l'ordre", CopyRoute.Facts(CastAction.COPY, video, Layout.FASTSTART), CopyTransport.ORDERED, true),
        Row("vidéo non ISO (mkv)", CopyRoute.Facts(CastAction.COPY, video, Layout.NOT_ISO), CopyTransport.ORDERED, true),
        Row("vidéo, disposition inconnue", CopyRoute.Facts(CastAction.COPY, video, null), CopyTransport.ORDERED, true),
        Row("audio lu sur la TV", CopyRoute.Facts(CastAction.COPY, audio, Layout.NOT_ISO), CopyTransport.ORDERED, true),
        Row("déplacer une vidéo : la TV lit aussi", CopyRoute.Facts(CastAction.MOVE, video, Layout.FASTSTART), CopyTransport.ORDERED, true),
        Row("transfert rapide désactivé : classique (dans l'ordre)", CopyRoute.Facts(CastAction.COPY, video, Layout.FASTSTART, fastEnabled = false), CopyTransport.ORDERED, true),
        Row("MP4 index à la fin : la TV attend tout, le plus rapide", CopyRoute.Facts(CastAction.COPY, video, Layout.MOOV_AT_END), CopyTransport.FAST, false),
        Row("MP4 index à la fin, rapide désactivé", CopyRoute.Facts(CastAction.COPY, video, Layout.MOOV_AT_END, fastEnabled = false), CopyTransport.ORDERED, false),
        Row("photo : seulement rangée, le plus rapide", CopyRoute.Facts(CastAction.COPY, photo), CopyTransport.FAST, false),
        Row("photo, rapide désactivé", CopyRoute.Facts(CastAction.COPY, photo, fastEnabled = false), CopyTransport.ORDERED, false),
        Row("lecture en direct : aucune copie", CopyRoute.Facts(CastAction.LIVE, video, Layout.FASTSTART), CopyTransport.NONE, false),
        Row("Bluetooth seul : pas de copie Wi-Fi", CopyRoute.Facts(CastAction.COPY, video, Layout.FASTSTART, ipRoute = false), CopyTransport.NONE, false),
        Row("TV d'essai : la copie est fermée (lecture seule)", CopyRoute.Facts(CastAction.COPY, video, Layout.FASTSTART, edition = Edition.TRIAL), CopyTransport.NONE, false),
        Row("TV de production", CopyRoute.Facts(CastAction.COPY, video, Layout.FASTSTART, edition = Edition.PRODUCTION), CopyTransport.ORDERED, true),
    )

    @Test fun whichTransportCopyAndPlayUses() {
        for (r in rows) {
            val d = CopyRoute.decide(r.f)
            assertEquals(r.transport, d.transport, r.label)
            assertEquals(r.handoff, d.handoffDuringCopy, r.label)
            assertTrue(d.why.isNotBlank(), r.label)
        }
    }

    @Test fun aVideoThatPlaysOnTheTvNeverTakesTheInvisibleFastPathUnlessItsIndexIsAtTheEnd() {
        for (a in CastAction.values()) for (k in MediaKind.values()) for (l in Layout.values().toList() + null) for (fast in listOf(true, false)) {
            val src = CastSource(k, "content", "media", 50_000_000)
            val d = CopyRoute.decide(CopyRoute.Facts(a, src, l, fastEnabled = fast))
            if (a != CastAction.LIVE && CastPlan.playsOnTv(a, src) && l != Layout.MOOV_AT_END)
                assertEquals(CopyTransport.ORDERED, d.transport, "$a/$k/$l/$fast : la TV doit voir les octets arriver")
            if (d.handoffDuringCopy) assertEquals(CopyTransport.ORDERED, d.transport, "$a/$k/$l/$fast : relais pendant la copie = chemin ordonné")
        }
    }

    // ---- seuils du relais (Handoff.copyReady)

    private val total = 1_000_000_000L          // 1 Go
    private val dur = 5_400_000L                // 90 min : ≈ 185 ko/s

    @Test fun aKnownDurationHandsOffAfterAboutThirtySecondsOfLeadNotAtTheEnd() {
        val need = Progressive.handoffBytes(total, dur, 0, 30_000, false, 2_000_000)
        assertTrue(need < total / 10, "le relais à 0 s demande ${need} octets : bien avant la fin")
        assertFalse(Handoff.copyReady(need - 1, total, dur, 0, false, 2_000_000))
        assertTrue(Handoff.copyReady(need, total, dur, 0, false, 2_000_000))
    }

    @Test fun anUnknownDurationFromOpenWithStillHandsOffEarlyFromTheStart() {
        // « Ouvrir avec » → « Copier sur la TV et lire » : rien n'est lu sur le téléphone, durée inconnue (0), position 0
        val speed = 3_000_000L
        val need = Handoff.bytesNeeded(total, 0, 0, false, speed)
        assertTrue(need < total / 10, "durée inconnue : $need octets demandés, la TV ne doit pas attendre la fin")
        assertTrue(need >= Progressive.bootstrapBytes(total, speed) + Handoff.UNKNOWN_DURATION_LEAD_BYTES, "assez d'avance tout de même")
        assertTrue(Handoff.copyReady(need, total, 0, 0, false, speed))
        assertFalse(Handoff.copyReady(need - 1, total, 0, 0, false, speed))
        val w = Handoff.waitMs(0, total, 0, 0, false, speed)
        assertNotNull(w); assertTrue(w < 60_000, "relais estimé dans $w ms")
    }

    @Test fun anUnknownDurationInTheMiddleOfTheFileWaitsForTheWholeFile() {
        // le téléphone lit à 10 min mais ne connaît pas la durée : impossible de placer l'octet, on ne devine pas
        assertEquals(total, Handoff.bytesNeeded(total, 0, 600_000, false, 3_000_000))
    }

    @Test fun moovAtEndAlwaysNeedsTheWholeFile() {
        assertEquals(total, Handoff.bytesNeeded(total, dur, 0, true, 3_000_000))
        assertEquals(total, Handoff.bytesNeeded(total, 0, 0, true, 3_000_000))
        assertFalse(Handoff.copyReady(total - 1, total, dur, 0, true, 3_000_000))
        assertTrue(Handoff.copyReady(total, total, dur, 0, true, 3_000_000))
    }

    @Test fun aSmallFileNeverAsksMoreThanItsSize() {
        assertEquals(5_000_000L, Handoff.bytesNeeded(5_000_000, 0, 0, false, 3_000_000))
        assertTrue(Handoff.copyReady(5_000_000, 5_000_000, 0, 0, false, 0))
    }

    // ---- phrases

    @Test fun theScreenSaysPlainlyWhatHappens() {
        assertEquals("Copie en cours · la TV démarrera la lecture dès qu'elle aura assez d'avance", CopyHandoff.line(false, null))
        val moov = CopyHandoff.line(true, 125_000)
        assertTrue("index" in moov && "copie complète" in moov && "2 min 05 s" in moov, moov)
        assertTrue("estimation" in CopyHandoff.line(true, null))
        assertEquals("La lecture continue ici en attendant.", CopyHandoff.phoneLine(true))
        assertNull(CopyHandoff.phoneLine(false), "depuis « Ouvrir avec », rien n'est lu sur le téléphone : ne pas le prétendre")
    }

    @Test fun theProgressDetailOfAMoovAtEndFileGivesTheWaitForTheWholeCopy() {
        val c = CopyProgress(sent = 250_000_000, total = 1_000_000_000, bytesPerSec = 5_000_000, handoffInMs = 150_000, moovAtEnd = true)
        val d = c.detail()
        assertTrue("fin de la copie" in d && "2 min 30 s" in d, d)
        assertFalse("assez d'avance" in d, d)
        val normal = CopyProgress(sent = 0, total = 1_000_000, bytesPerSec = 0, handoffInMs = null).detail()
        assertTrue("assez d'avance" in normal, normal)
    }
}
