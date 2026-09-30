package castbridge.core

import castbridge.core.phone.*
import kotlin.test.*

class PhonePlayerTest {
    // ---- resume position per file ----

    @Test fun resumeKeepsMiddlePositionsOnly() {
        val b = ResumeBook()
        val k = ResumeBook.key("Film.mkv", 1_000_000, "content://x/1")
        assertEquals(0, b.update(k, 5_000, 3_600_000), "the first 10 s are not worth remembering")
        assertEquals(0, b.get(k))
        assertEquals(754_000, b.update(k, 754_000, 3_600_000))
        assertEquals(754_000, b.startAt(k, 3_600_000))
        assertEquals(0, b.update(k, 3_590_000, 3_600_000), "watched to the credits: next time from the start")
        assertEquals(0, b.startAt(k, 3_600_000))
    }

    @Test fun resumeKeyIgnoresTheUriSoGalleryAndWhatsappShareOnePosition() {
        assertEquals(ResumeBook.key("VID_01.mp4", 42, "content://media/1"), ResumeBook.key("vid_01.MP4", 42, "content://com.whatsapp/9"))
        assertEquals("content://a/b", ResumeBook.key(null, -1, "content://a/b"), "no name/size: the URI")
        assertNotEquals(ResumeBook.key("a.mp4", 1, "u"), ResumeBook.key("a.mp4", 2, "u"), "same name, other file")
    }

    @Test fun resumeBookSurvivesSerializationAndDropsOldest() {
        val b = ResumeBook(max = 3)
        for (i in 1..4) b.update("f$i", 60_000L * i, 3_600_000)
        assertEquals(3, b.size())
        assertEquals(0, b.get("f1"), "least recently used entry dropped")
        val back = ResumeBook.parse(b.serialize() + "\ngarbage line\n\t12", max = 3)
        assertEquals(120_000, back.get("f2")); assertEquals(240_000, back.get("f4")); assertEquals(3, back.size())
        assertEquals(0, back.startAt("f4", 100_000), "a position beyond the real duration is ignored")
    }

    // ---- which cast actions ----

    private fun local(auth: String = "media", kind: MediaKind = MediaKind.VIDEO) = CastSource(kind, "content", auth, 10_000_000)

    @Test fun castActionsForLocalFiles() {
        assertEquals(listOf(CastAction.LIVE, CastAction.COPY, CastAction.MOVE), CastPlan.actions(TargetKind.CASTBRIDGE, local()))
        assertEquals(listOf(CastAction.LIVE), CastPlan.actions(TargetKind.DLNA, local()), "a DLNA TV has no storage")
        // A file shared by WhatsApp belongs to WhatsApp: copy yes, move (delete) never
        assertEquals(listOf(CastAction.LIVE, CastAction.COPY), CastPlan.actions(TargetKind.CASTBRIDGE, local("com.whatsapp.provider.media")))
        assertEquals(listOf(CastAction.LIVE, CastAction.COPY),
            CastPlan.actions(TargetKind.CASTBRIDGE, CastSource(MediaKind.VIDEO, "file")), "file:// of another app: not ours to delete")
        assertTrue(CastPlan.canMove(local("com.android.externalstorage.documents")))
        assertEquals(emptyList(), CastPlan.actions(TargetKind.CASTBRIDGE, local(kind = MediaKind.OTHER)))
    }

    @Test fun webLinksAreOnlyPlayedLiveAndNeverServedByThePhone() {
        val web = CastSource(MediaKind.VIDEO, "https")
        assertEquals(listOf(CastAction.LIVE), CastPlan.actions(TargetKind.CASTBRIDGE, web))
        assertEquals(listOf(CastAction.LIVE), CastPlan.actions(TargetKind.DLNA, web))
        assertFalse(CastPlan.needsPhoneServer(CastAction.LIVE, web))
        assertTrue(CastPlan.needsPhoneServer(CastAction.LIVE, local()))
        assertFalse(CastPlan.needsPhoneServer(CastAction.COPY, local()))
    }

    @Test fun photosCopiedAreStoredNotPlayedAndCopiesWaitForTheTv() {
        val photo = local(kind = MediaKind.IMAGE)
        assertTrue(CastPlan.playsOnTv(CastAction.LIVE, photo)); assertFalse(CastPlan.playsOnTv(CastAction.COPY, photo))
        assertTrue(CastPlan.playsOnTv(CastAction.MOVE, local()))
        assertTrue(CastPlan.phoneWaitsForTv(CastAction.COPY)); assertFalse(CastPlan.phoneWaitsForTv(CastAction.LIVE))
    }

    // ---- phone position -> TV position ----

    @Test fun positionHandOver() {
        assertEquals(0, Handoff.phoneToTv(1_500, 600_000), "just started: from the beginning")
        assertEquals(298_000, Handoff.phoneToTv(300_000, 600_000), "2 s replayed to cover the switch")
        assertEquals(0, Handoff.phoneToTv(595_000, 600_000), "the very end: the TV starts over")
        assertEquals(58_000, Handoff.phoneToTv(60_000, 0), "unknown duration (live link)")
        assertEquals(298, Handoff.toDlnaSeconds(298_900)); assertEquals(0, Handoff.toDlnaSeconds(-5))
    }

    @Test fun backToThePhoneUsesTheTvPosition() {
        assertEquals(420_000, Handoff.tvToPhone(420_000, 400_000, 600_000))
        assertEquals(400_000, Handoff.tvToPhone(0, 400_000, 600_000), "TV already stopped: its last known position")
        assertEquals(0, Handoff.tvToPhone(599_500, 0, 600_000), "ended on the TV: from the start")
        assertEquals(0, Handoff.tvToPhone(700_000, 0, 600_000), "past the local end: from the start")
        assertEquals(700_000, Handoff.tvToPhone(700_000, 0, 0), "unknown local duration: as is")
    }

    @Test fun copyStartsOnTheTvOnlyWithDataBeyondThePhonePosition() {
        val total = 1_000_000_000L; val dur = 1_000_000L    // 1 GB, 1000 s
        // phone at 500 s: the TV needs ~ half the file + 30 s + bootstrap
        assertFalse(Handoff.copyReady(400_000_000, total, dur, 500_000, false, 0))
        assertTrue(Handoff.copyReady(540_000_000, total, dur, 500_000, false, 0))
        assertFalse(Handoff.copyReady(999_000_000, total, dur, 500_000, true, 0), "MP4 index at the end: the whole file")
        assertTrue(Handoff.copyReady(total, total, dur, 500_000, true, 0))
        assertFalse(Handoff.copyReady(0, 0, dur, 0, false, 0), "size unknown yet")
    }

    @Test fun remoteClockInterpolatesBetweenPolls() {
        val c = RemoteClock(posMs = 10_000, durMs = 11_000, playing = true, atMs = 1_000)
        assertEquals(10_500, c.now(1_500)); assertEquals(11_000, c.now(9_000), "never past the end")
        assertEquals(10_000, c.copy(playing = false).now(9_000)); assertEquals(10_000, c.now(0), "clock skew")
    }

    // ---- folder playlist ----

    @Test fun folderPlaylistNaturalOrderSameFamily() {
        fun e(id: String, n: String, k: MediaKind = MediaKind.VIDEO) = FolderPlaylist.Entry(id, n, k)
        val folder = listOf(e("3", "Episode 10.mkv"), e("1", "Episode 2.mkv"), e("9", "cover.jpg", MediaKind.IMAGE),
            e("5", "episode 1.mp4"), e("7", "Générique.mp3", MediaKind.AUDIO), e("4", "Épisode 3.mkv"))
        val (list, idx) = FolderPlaylist.build(folder, e("1", "Episode 2.mkv"))
        assertEquals(listOf("episode 1.mp4", "Episode 2.mkv", "Épisode 3.mkv", "Episode 10.mkv"), list.map { it.name })
        assertEquals(1, idx)
        assertEquals(2, FolderPlaylist.next(list.size, idx)); assertEquals(0, FolderPlaylist.prev(list.size, idx))
        assertNull(FolderPlaylist.next(4, 3)); assertNull(FolderPlaylist.prev(4, 0))
        // shared file not in the listing: alone, index 0
        val (solo, i) = FolderPlaylist.build(emptyList(), e("x", "clip.mp4"))
        assertEquals(1, solo.size); assertEquals(0, i)
        // photos with photos only
        assertEquals(listOf("cover.jpg"), FolderPlaylist.build(folder, e("9", "cover.jpg", MediaKind.IMAGE)).first.map { it.name })
    }

    @Test fun naturalOrder() {
        assertTrue(NaturalOrder.compare("a2", "a10") < 0); assertTrue(NaturalOrder.compare("B", "a") > 0)
        assertTrue(NaturalOrder.compare("x007", "x7") > 0 && NaturalOrder.compare("x7", "x8") < 0)
        assertEquals(0, NaturalOrder.compare("Été", "ete"))
    }

    // ---- kinds, streams, formats, gestures ----

    @Test fun kindsAndStreams() {
        assertEquals(MediaKind.VIDEO, MediaKind.of("video/x-matroska", null)); assertEquals(MediaKind.AUDIO, MediaKind.of(null, "Song.FLAC"))
        assertEquals(MediaKind.IMAGE, MediaKind.of(null, "IMG_1.heic"))
        assertEquals(MediaKind.VIDEO, MediaKind.of("application/vnd.apple.mpegurl", "x"))
        assertEquals(MediaKind.OTHER, MediaKind.of(null, "doc.pdf"))
        assertEquals(StreamType.HLS, StreamType.of("https://cdn/x/master.m3u8?token=1", null))
        assertEquals(StreamType.DASH, StreamType.of("https://cdn/manifest", "application/dash+xml"))
        assertEquals(StreamType.PROGRESSIVE, StreamType.of("https://site/v.mp4", "video/mp4"))
    }

    @Test fun formatExplanations() {
        assertTrue(PhoneFormats.explain(emptyList(), listOf("audio/ac3"), false, "film.mkv").contains("Dolby Digital (AC3)"))
        assertTrue(PhoneFormats.explain(listOf("video/hevc"), emptyList(), true, "x.mkv").startsWith("Ce téléphone ne sait pas décoder l'image (HEVC"))
        assertTrue(PhoneFormats.explain(emptyList(), emptyList(), true, "vieux.rmvb").contains(".rmvb"))
        assertEquals("", PhoneFormats.explain(emptyList(), emptyList(), false, "ok.mp4"))
        assertTrue(PhoneFormats.riskyAudio("audio/vnd.dts")); assertFalse(PhoneFormats.riskyAudio("audio/mp4a-latm"))
    }

    @Test fun gestures() {
        assertEquals(45_000, Gestures.seekDelta(540f, 1080f, 3_600_000), "half the width = 45 s")
        assertEquals(-10_000, Gestures.seekDelta(-1080f, 1080f, 10_000), "short clip: at most its duration")
        assertEquals(0.75f, Gestures.level(0.5f, -500f, 2000f)); assertEquals(1f, Gestures.level(0.9f, -2000f, 2000f))
        assertEquals(Gestures.Zone.BRIGHTNESS, Gestures.zone(100f, 1000f)); assertEquals(Gestures.Zone.VOLUME, Gestures.zone(600f, 1000f))
        assertEquals(-10_000, Gestures.doubleTapSkip(10f, 900f)); assertEquals(10_000, Gestures.doubleTapSkip(890f, 900f))
        assertEquals(0, Gestures.doubleTapSkip(450f, 900f))
        assertEquals("1,5x", Speeds.label(1.5f)); assertEquals("2x", Speeds.label(2f)); assertEquals("Normale", Speeds.label(1f))
    }

    @Test fun subtitles() {
        assertEquals("application/x-subrip", SubtitleTypes.mimeOf("Film.FR.srt")); assertEquals("text/x-ssa", SubtitleTypes.mimeOf("a.ass"))
        assertNull(SubtitleTypes.mimeOf("a.sub"))
        assertEquals("fr", SubtitleTypes.language("Film.mkv", "Film.fr.srt")); assertNull(SubtitleTypes.language("Film.mkv", "Film.srt"))
    }
}
