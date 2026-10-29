package castbridge.core.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** « Copier et lire à la suite »: the insertion rules of the TV's playlist and the phone's reading of the TV's answer (pure). */
class PlayNextTest {
    @Test fun somethingPlaysWithAListTheFileGoesRightAfterTheCurrentItem() {
        val pl = Playlist(listOf("a", "b", "c"), 1)
        val r = PlayNext.apply(pl, "b", "x")
        assertEquals(PlayNext.Kind.QUEUED, r.kind)
        assertSame(pl, r.playlist)
        assertEquals(listOf("a", "b", "x", "c"), pl.items)
        assertEquals("b", pl.current)
        assertEquals("x", pl.next())            // the playlist then plays it
    }

    @Test fun somethingPlaysWithoutAListOneIsMadeOfTheCurrentThenTheFile() {
        val r = PlayNext.apply(null, "film.mp4", "x.mp4")
        assertEquals(PlayNext.Kind.QUEUED, r.kind)
        assertEquals(listOf("film.mp4", "x.mp4"), r.playlist!!.items)
        assertEquals("film.mp4", r.playlist!!.current)
    }

    @Test fun aStaleListThatDoesNotHoldWhatPlaysIsReplaced() {
        val stale = Playlist(listOf("a", "b"), 0)
        val r = PlayNext.apply(stale, "live", "x")
        assertEquals(listOf("live", "x"), r.playlist!!.items)
    }

    @Test fun nothingPlaysTheFileStartsAtOnceAndNoListRemains() {
        val r = PlayNext.apply(Playlist(listOf("a", "b"), 0), null, "x")
        assertEquals(PlayNext.Kind.START_NOW, r.kind)
        assertNull(r.playlist)
    }

    @Test fun severalFilesKeepTheirArrivalOrderNotAllAtPlusOne() {
        val pl = PlayNext.apply(null, "cur", "first").playlist!!
        PlayNext.apply(pl, "cur", "second"); PlayNext.apply(pl, "cur", "third")
        assertEquals(listOf("cur", "first", "second", "third"), pl.items)
        // the current item moves on: the next arrival goes behind what is already queued, not before it
        pl.next(); PlayNext.apply(pl, "first", "fourth")
        assertEquals(listOf("cur", "first", "second", "third", "fourth"), pl.items)
    }

    @Test fun anArrivalAfterTheQueuedOnesPlayedGoesRightAfterTheCurrent() {
        val pl = Playlist(listOf("cur", "a", "b"), 0)
        PlayNext.apply(pl, "cur", "q1")
        pl.next(); pl.next(); pl.next()          // q1, a, b played: the current one is the last
        assertEquals("b", pl.current)
        PlayNext.apply(pl, "b", "q2")
        assertEquals(listOf("cur", "q1", "a", "b", "q2"), pl.items)
    }

    @Test fun aFileAlreadyLaterInTheListIsMovedNotDuplicated() {
        val pl = Playlist(listOf("a", "b", "x", "c"), 0)
        PlayNext.apply(pl, "a", "x")
        assertEquals(listOf("a", "x", "b", "c"), pl.items)
        PlayNext.apply(pl, "a", "c")
        assertEquals(listOf("a", "x", "c", "b"), pl.items)
    }

    @Test fun aFilePlayedEarlierIsQueuedAgain() {
        val pl = Playlist(listOf("x", "a", "b"), 1)
        PlayNext.apply(pl, "a", "x")
        assertEquals(listOf("x", "a", "x", "b"), pl.items)
    }

    @Test fun theFileThatPlaysIsLeftAlone() {
        val pl = Playlist(listOf("a", "x"), 1)
        val r = PlayNext.apply(pl, "x", "x")
        assertEquals(PlayNext.Kind.ALREADY_PLAYING, r.kind)
        assertEquals(listOf("a", "x"), pl.items)
        assertEquals(PlayNext.Kind.ALREADY_PLAYING, PlayNext.apply(null, "x", "x").kind)
    }

    @Test fun thePhoneDecidesFromTheTvsAnswer() {
        assertEquals(PlayNext.Support.SUPPORTED, PlayNext.support(200))
        for (old in listOf(404, 405, 501)) assertEquals(PlayNext.Support.TV_TOO_OLD, PlayNext.support(old))
        assertEquals(PlayNext.Support.REFUSED, PlayNext.support(403))
        assertEquals(PlayNext.Support.REFUSED, PlayNext.support(500))
        assertEquals(PlayNext.Support.UNREACHABLE, PlayNext.support(null))
    }

    @Test fun theSentencesAreTheOnesOfTheBrief() {
        assertEquals("Copier et lire à la suite", PlayNext.LABEL)
        assertEquals("Copié sur la TV, puis lu juste après la vidéo en cours ; tout de suite si rien ne joue.", PlayNext.HINT)
        assertTrue(PlayNext.failureText(404)!!.startsWith("Cette TV ne gère pas encore la lecture à la suite : mettez à jour CastBridge-TV"))
        assertTrue("« Copier sur la TV »" in PlayNext.failureText(404)!!)
        assertTrue("profil enfant" in PlayNext.failureText(403)!!)
        assertTrue("erreur 500" in PlayNext.failureText(500)!!)
        assertNull(PlayNext.failureText(200)); assertNull(PlayNext.failureText(null))
    }

    @Test fun sameStatesAsCopyExceptOverBluetoothAlone() {
        assertTrue(PlayNext.available(true, true))
        assertEquals(false, PlayNext.available(false, true)); assertEquals(false, PlayNext.available(true, false))
    }
}
