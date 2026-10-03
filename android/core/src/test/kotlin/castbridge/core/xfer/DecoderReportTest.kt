package castbridge.core.xfer

import kotlin.test.*

/** R-16 : le relevé du décodeur dit ce qui est sûr, et marque l'indice comme un indice. */
class DecoderReportTest {
    @Test fun theVerdictIsAHintScaledTo720pAndNeverCertain() {
        assertTrue(DecoderReport.verdict(1.5, 1280, 720).startsWith("matériel probable"))
        assertTrue(DecoderReport.verdict(14.0, 1280, 720).startsWith("logiciel probable"))
        assertTrue(DecoderReport.verdict(6.0, 1280, 720).startsWith("indéterminé"))
        assertTrue(DecoderReport.verdict(14.0, 1920, 1080).startsWith("indéterminé"), "a 1080p frame costs 2.25x a 720p one")
        assertTrue(DecoderReport.verdict(null, 1280, 720).contains("indisponible"))
    }

    @Test fun theReportSaysWhatWasAskedWhatTheTvCanAndWhatCannotBeKnown() {
        val forced = PlayerTuning.decide(PlayerTuning.Facts("xvid", 1280, 720, true, true, 0, 4, true))
        val l = DecoderReport.lines("xvid", 1280, 720, forced, true, 2.0, 750, 3, 0).joinToString("\n")
        assertTrue(l.contains("video/mp4v-es")); assertTrue(l.contains("matériel forcé")); assertTrue(l.contains("non lisible dans libVLC"))
        assertTrue(l.contains("750 affichées, 3 perdues")); assertFalse(l.contains("Lecture allégée"))
        val sw = PlayerTuning.decide(PlayerTuning.Facts("DIV3", 640, 480, true, true, 2, 4, false))
        val s = DecoderReport.lines("DIV3", 640, 480, sw, false, null, 0, 0, 2).joinToString("\n")
        assertTrue(s.contains("aucun décodeur matériel")); assertTrue(s.contains("logiciel (libavcodec)")); assertTrue(s.contains("palier 2"))
    }
}
