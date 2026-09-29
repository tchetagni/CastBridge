package castbridge.core

import castbridge.core.transcode.*
import castbridge.core.upnp.*
import kotlin.test.*

class CoreTest {
    private val caps = Capabilities()
    @Test fun directMp4() = assertEquals(Route.Direct,
        RoutePlanner.plan(MediaInfo("mp4", "h264", "aac", 1920, 1080), caps, false))
    @Test fun mkvAc3Remux() = assertEquals(Route.Transcode(Profile.REMUX_AAC, false),
        RoutePlanner.plan(MediaInfo("matroska", "h264", "ac3", 1920, 1080), caps, false))
    @Test fun hevc10bitPcVsPhone() {
        val i = MediaInfo("matroska", "hevc", "aac", 1920, 1080, tenBit = true)
        assertEquals(Route.Transcode(Profile.DLNA_1080, true), RoutePlanner.plan(i, caps, true))
        assertEquals(Route.Transcode(Profile.DLNA_720, false), RoutePlanner.plan(i, caps, false))
    }
    @Test fun audioOnly() = assertEquals(Route.Transcode(Profile.AUDIO_MP3, false),
        RoutePlanner.plan(MediaInfo("flac", audioCodec = "flac", isAudioOnly = true), caps, false))
    @Test fun soap() {
        val e = Soap.envelope(Soap.AVT, "SetAVTransportURI", listOf("CurrentURI" to "http://a/?x=1&y=2"))
        assertTrue("x=1&amp;y=2" in e)
        assertEquals(3723, Soap.parseHms("01:02:03"))
        assertEquals("01:02:03", Soap.hms(3723))
    }
    @Test fun didlRoundTrip() {
        val d = Didl.item("http://a/b?c=1&d=2", "T & <x>", "video/mpeg", Didl.protocolInfo("video/mpeg", "*"))
        val xml = Xml.esc(d)
        assertEquals("T & <x>", Didl.parse(Xml.unesc(xml)).first().title)
        assertEquals("http://a/b?c=1&d=2", Didl.parse(d).first().url)
    }
    @Test fun browseResult() {
        val resp = "<Result>" + Xml.esc("<DIDL-Lite><container id=\"1\"><dc:title>Films</dc:title></container></DIDL-Lite>") + "</Result>"
        val e = Didl.parse(Xml.tag(resp, "Result")!!)
        assertTrue(e.single().isContainer && e.single().title == "Films")
    }
}
