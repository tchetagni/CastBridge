package castbridge.core.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvDedupeTest {
    @Test fun aCompleteFileOfTheSameSizeIsNotCopiedAgain() = assertTrue(TvDedupe.alreadyThere(TvFile("ep.avi", 100, 100, true), 100))
    @Test fun aSameNamedFileOfAnotherSizeIsADifferentFile() = assertFalse(TvDedupe.alreadyThere(TvFile("ep.avi", 100, 100, true), 101))
    @Test fun aPartialCopyIsResumedNotSkipped() = assertFalse(TvDedupe.alreadyThere(TvFile("ep.avi", 100, 40, false), 100))
    @Test fun nothingKnownOnTheTvOrNoSizeMeansCopy() { assertFalse(TvDedupe.alreadyThere(null, 100)); assertFalse(TvDedupe.alreadyThere(TvFile("a", 0, 0, true), 0)) }
}
