package castbridge.core.dl

import castbridge.core.tv.Fs
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.test.*

/** Minimal bencode writer for building test torrents. */
object Benc {
    fun enc(v: Any): ByteArray = ByteArrayOutputStream().also { w(it, v) }.toByteArray()
    private fun w(o: ByteArrayOutputStream, v: Any) {
        when (v) {
            is Int -> o.write("i${v}e".toByteArray())
            is Long -> o.write("i${v}e".toByteArray())
            is String -> { val b = v.toByteArray(); o.write("${b.size}:".toByteArray()); o.write(b) }
            is ByteArray -> { o.write("${v.size}:".toByteArray()); o.write(v) }
            is List<*> -> { o.write('l'.code); v.forEach { w(o, it!!) }; o.write('e'.code) }
            is Map<*, *> -> { o.write('d'.code); v.entries.sortedBy { it.key.toString() }.forEach { w(o, it.key.toString()); w(o, it.value!!) }; o.write('e'.code) }
            else -> error("bad $v")
        }
    }

    fun info(name: String, files: List<Pair<String, Long>>? = null, length: Long = 1000): Map<String, Any> {
        val info = LinkedHashMap<String, Any>()
        info["name"] = name; info["piece length"] = 16384; info["pieces"] = ByteArray(20)
        if (files == null) info["length"] = length else info["files"] = files.map { (p, l) -> mapOf("length" to l, "path" to p.split('/')) }
        return info
    }

    fun torrent(name: String, files: List<Pair<String, Long>>? = null, length: Long = 1000): ByteArray =
        enc(mapOf("announce" to "http://tracker.example/announce", "info" to info(name, files, length)))
}

class JsonTest {
    @Test fun roundTrip() {
        val s = """{"a":[1,2.5,"x\n\"y\"",true,null,{"b":"é\u0001"}],"n":-12}"""
        val v = Json.parse(s)
        assertEquals(v, Json.parse(Json.write(v)))
        assertEquals(-12L, v.obj()["n"])
        assertEquals("x\n\"y\"", v.obj()["a"].list()[2])
    }

    @Test fun rejectsGarbage() {
        assertFailsWith<Json.ParseError> { Json.parse("{\"a\":") }
        assertFailsWith<Json.ParseError> { Json.parse("[1,2] x") }
        assertFailsWith<Json.ParseError> { Json.parse("[".repeat(100) + "]".repeat(100)) }
    }

    @Test fun lenientNumbers() {
        val m = Json.parse("""{"totalLength":"1234","x":5,"b":"true"}""").obj()
        assertEquals(1234, m.n("totalLength")); assertEquals(5, m.n("x")); assertTrue(m.b("b")); assertEquals(0, m.n("missing"))
    }
}

class Aria2RpcTest {
    @Test fun talksJsonRpcOverHttpWithTheSecret() {
        val fake = FakeAria2("abc")
        val port = ServerSocket(0).use { it.localPort }
        val http = fake.serve(port)
        try {
            val rpc = Aria2Rpc(port, "abc")
            assertEquals("1.37.0", rpc.getVersion())
            val dir = kotlin.io.path.createTempDirectory("rpc").toFile()
            val gid = rpc.addUri(listOf("https://example.org/film.mkv"), mapOf("dir" to dir.path))
            assertEquals(1, rpc.tellActive().size)
            rpc.pause(gid)
            assertEquals("paused", rpc.tellStatus(gid).s("status"))
            assertEquals(1, rpc.tellWaiting().size)
            rpc.unpause(gid)
            rpc.changeOption(gid, mapOf("max-download-limit" to "1M"))
            assertEquals("1M", fake.dls[gid]!!.options["max-download-limit"])
            rpc.changeGlobalOption(mapOf("max-overall-upload-limit" to "100K"))
            assertEquals("100K", fake.globalOptions["max-overall-upload-limit"])
            assertEquals(1, rpc.getFiles(gid).size)
            assertEquals(1, rpc.getPeers(gid).size)
            assertNotNull(rpc.getGlobalStat()["downloadSpeed"])
            val t = rpc.addTorrent(Benc.torrent("Film", length = 5000), mapOf("dir" to dir.path))
            assertEquals(5000, rpc.tellStatus(t).n("totalLength"))
            assertTrue(rpc.addMetalink("<metalink/>".toByteArray(), mapOf("dir" to dir.path)).isNotEmpty())
            rpc.remove(gid); rpc.removeDownloadResult(gid); rpc.purgeDownloadResult(); rpc.saveSession()
            assertEquals(2, rpc.tellAll().size)
            assertFailsWith<Aria2Rpc.RpcError> { rpc.tellStatus("ffffffffffffffff") }
            // A wrong secret is refused by aria2.
            val e = assertFailsWith<Aria2Rpc.RpcError> { Aria2Rpc(port, "wrong").getVersion() }
            assertEquals("Unauthorized", e.message)
            assertFalse(e.message!!.contains("abc"))
        } finally { http.stop() }
    }

    @Test fun sendsTokenFirstAndNumbersAsGiven() {
        var seen = ""
        val rpc = Aria2Rpc(1, "tok", transport = { b -> seen = b; """{"jsonrpc":"2.0","id":"1","result":3}""" })
        assertEquals(3, rpc.changePosition("g", -1, "POS_CUR"))
        val req = Json.parse(seen).obj()
        assertEquals("aria2.changePosition", req.s("method"))
        assertEquals(listOf("token:tok", "g", -1L, "POS_CUR"), req["params"])
    }
}

class StateMapperTest {
    private fun st(status: String, total: Long = 100, done: Long = 0, speed: Long = 0, seeder: Boolean = false, meta: Boolean = false) = mapOf(
        "status" to status, "totalLength" to "$total", "completedLength" to "$done", "downloadSpeed" to "$speed", "seeder" to "$seeder",
        "files" to listOf(mapOf("path" to if (meta) "[METADATA]abc" else "/x/f.mkv")))

    @Test fun mapsAria2StatesToPlainWords() {
        assertEquals(DlState.CONNECTING, StateMapper.map(st("active"), null))
        assertEquals(DlState.DOWNLOADING, StateMapper.map(st("active", done = 10, speed = 5), null))
        assertEquals(DlState.METADATA, StateMapper.map(st("active", total = 0, meta = true, speed = 1), null))
        assertEquals(DlState.SEEDING, StateMapper.map(st("active", done = 100, seeder = true), null))
        assertEquals(DlState.QUEUED, StateMapper.map(st("waiting"), null))
        assertEquals(DlState.PAUSED, StateMapper.map(st("paused"), PausedBy.USER))
        assertEquals(DlState.WAITING_SPACE, StateMapper.map(st("paused"), PausedBy.SPACE))
        assertEquals(DlState.WAITING_DRIVE, StateMapper.map(st("paused"), PausedBy.DRIVE))
        assertEquals(DlState.CHECKING, StateMapper.map(st("paused"), PausedBy.CHECK))
        assertEquals(DlState.DONE, StateMapper.map(st("complete"), null))
        assertEquals(DlState.ERROR, StateMapper.map(st("error"), null))
        assertEquals(DlState.WAITING_DRIVE, StateMapper.map(st("error"), PausedBy.DRIVE))
        assertTrue(DlState.DOWNLOADING.canPause); assertTrue(DlState.PAUSED.canResume); assertFalse(DlState.WAITING_DRIVE.canResume)
    }

    @Test fun etaAndErrors() {
        assertEquals(10, StateMapper.eta(1000, 0, 100))
        assertEquals(-1, StateMapper.eta(1000, 0, 0))
        assertEquals(-1, StateMapper.eta(0, 0, 10))
        assertTrue(StateMapper.error(3, null).contains("introuvable"))
        assertTrue(StateMapper.error(9, null).contains("place"))
        assertTrue(StateMapper.error(99, "boom").contains("boom"))
    }
}

class DownloadSpaceTest {
    private val G = 1L shl 30
    private fun internal(free: Long) = StorageVolume("internal", "Mémoire interne", File("/i"), VolumeKind.INTERNAL, Fs.EXT4, free, 8 * G, false)
    private fun usb(free: Long, fs: Fs = Fs.EXFAT, id: String = "usb") = StorageVolume(id, "Clé USB", File("/u"), VolumeKind.REMOVABLE, fs, free, 58 * G, true)

    @Test fun keepsOneGigabyteFreeAndPrefersTheUsbDrive() {
        val c = DownloadSpace.choose(listOf(internal(300L shl 20), usb(50 * G)), "auto", 4 * G, emptyMap())
        assertEquals("usb", (c as DownloadSpace.Choice.Ok).volume.id)
        // 300 MB free inside: nothing fits there with 1 GB to keep.
        assertIs<DownloadSpace.Choice.Refused>(DownloadSpace.choose(listOf(internal(300L shl 20)), "auto", 10L shl 20, emptyMap()))
        // Exactly at the line: 5 GB free, 4 GB file -> 1 GB left: accepted; one byte more: refused.
        assertIs<DownloadSpace.Choice.Ok>(DownloadSpace.choose(listOf(usb(5 * G)), "auto", 4 * G, emptyMap()))
        val r = DownloadSpace.choose(listOf(usb(5 * G)), "auto", 4 * G + 1, emptyMap())
        assertIs<DownloadSpace.Choice.Refused>(r)
        assertTrue(r.message.contains("1,0 Go"))
    }

    @Test fun countsWhatOtherDownloadsWillStillWrite() {
        val vols = listOf(internal(20 * G), usb(10 * G))
        // 8 GB still to come on the USB drive: a 3 GB download no longer fits there, it goes inside.
        val c = DownloadSpace.choose(vols, "auto", 3 * G, mapOf("usb" to 8 * G))
        assertEquals("internal", (c as DownloadSpace.Choice.Ok).volume.id)
    }

    @Test fun fallsBackToAnotherVolumeWhenTheChosenOneIsFull() {
        val c = DownloadSpace.choose(listOf(internal(20 * G), usb(G)), "usb", 2 * G, emptyMap())
        c as DownloadSpace.Choice.Ok
        assertEquals("internal", c.volume.id); assertNotNull(c.note)
    }

    @Test fun fat32RefusesFourGigabyteFilesAndSafIsNeverUsed() {
        val r = DownloadSpace.choose(listOf(usb(50 * G, Fs.FAT32)), "auto", 5 * G, emptyMap())
        assertTrue((r as DownloadSpace.Choice.Refused).message.contains("FAT32"))
        val saf = StorageVolume("saf", "Dossier", File("/s"), VolumeKind.SAF, Fs.UNKNOWN, -1, 0)
        assertIs<DownloadSpace.Choice.Refused>(DownloadSpace.choose(listOf(saf), "auto", null, emptyMap()))
    }

    @Test fun unknownSizeNeedsOnlyTheMargin() {
        assertIs<DownloadSpace.Choice.Ok>(DownloadSpace.choose(listOf(usb(2 * G)), "auto", null, emptyMap()))
        assertIs<DownloadSpace.Choice.Refused>(DownloadSpace.choose(listOf(usb(G - 1)), "auto", null, emptyMap()))
    }

    @Test fun pausesInPriorityOrderAndResumesWithMargin() {
        val R = DownloadSpace::Running
        // 5 GB free; first download needs 3 GB (leaves 2), second 2 GB more would leave 0 -> paused.
        val d = DownloadSpace.check(listOf(R("a", "usb", 3 * G, false), R("b", "usb", 2 * G, false)), { 5 * G })
        assertEquals(setOf("b"), d.pause); assertTrue(d.resume.isEmpty())
        // Unknown size: paused only once the volume itself is under the line.
        assertEquals(setOf("c"), DownloadSpace.check(listOf(R("c", "usb", null, false)), { G - 1 }).pause)
        assertTrue(DownloadSpace.check(listOf(R("c", "usb", null, false)), { G + 1 }).pause.isEmpty())
        // Paused for space: resumes only with the hysteresis margin, never flapping at the line.
        assertTrue(DownloadSpace.check(listOf(R("b", "usb", G, true)), { 2 * G + 1 }).resume.isEmpty())
        assertEquals(setOf("b"), DownloadSpace.check(listOf(R("b", "usb", G, true)), { 2 * G + DownloadSpace.HYSTERESIS }).resume)
        // Volumes are independent; unknown free space decides nothing.
        val m = DownloadSpace.check(listOf(R("a", "usb", 3 * G, false), R("i", "internal", G, false)), { if (it == "usb") 10 * G else -1 })
        assertTrue(m.pause.isEmpty())
    }
}

class OptionsWhitelistTest {
    @Test fun acceptsSafeOptions() {
        val ok = Aria2Config.check(mapOf("max-download-limit" to "500K", "split" to "4", "select-file" to "1,3-5", "seed-ratio" to "1.5",
            "out" to "Film (2024).mkv", "checksum" to "sha-256=" + "a".repeat(64), "bt-tracker" to "udp://t.example:80/announce"), Aria2Config.TASK)
        assertIs<Aria2Config.Check.Ok>(ok)
    }

    @Test fun refusesHooksPathsAndInjection() {
        for (bad in listOf(
            mapOf("on-download-complete" to "/system/bin/sh"), mapOf("dir" to "/data/data/x"), mapOf("rpc-secret" to "x"),
            mapOf("conf-path" to "/sdcard/a"), mapOf("input-file" to "/x"), mapOf("log" to "/sdcard/log"), mapOf("save-session" to "/x"),
            mapOf("index-out" to "1=/etc/passwd"), mapOf("out" to "../../escape.mkv"), mapOf("out" to ".hidden"),
            mapOf("referer" to "a\non-download-complete=/bin/sh"), mapOf("split" to "999"), mapOf("max-download-limit" to "fast"),
            mapOf("select-file" to "1;rm"), mapOf("bt-tracker" to "file:///etc"), mapOf("max-concurrent-downloads" to "2"),
        )) assertIs<Aria2Config.Check.Bad>(Aria2Config.check(bad, Aria2Config.TASK), "$bad")
        assertIs<Aria2Config.Check.Bad>(Aria2Config.check(mapOf("max-concurrent-downloads" to "50"), Aria2Config.GLOBAL))
        assertIs<Aria2Config.Check.Ok>(Aria2Config.check(mapOf("max-concurrent-downloads" to "3"), Aria2Config.GLOBAL))
    }

    @Test fun commandLineKeepsTheSecretOutAndRpcLocal() {
        val args = Aria2Config.args(File("/w/aria2.conf"), File("/w/session"), File("/d"), 6800, 1234, DlSettings(), File("/w/ca.pem"), emptyList(), File("/w/dht.dat"))
        assertTrue("--rpc-listen-all=false" in args); assertTrue("--stop-with-process=1234" in args)
        assertTrue("--file-allocation=none" in args); assertTrue("--seed-time=0" in args); assertTrue(args.none { it.startsWith("--async-dns") })
        assertTrue(args.none { it.contains("secret") })
        assertTrue(args.none { it.startsWith("--on-") })
        // offline profile: no DHT, no listening port, no BitTorrent tuning
        assertTrue("--enable-dht=false" in args); assertTrue("--enable-dht6=false" in args); assertTrue("--enable-peer-exchange=false" in args)
        assertTrue("--bt-enable-lpd=false" in args); assertTrue("--follow-torrent=false" in args)
        assertTrue(args.none { it == "--enable-dht=true" }); assertTrue(args.none { it.startsWith("--listen-port") || it.startsWith("--dht-listen-port") || it.startsWith("--dht-file-path") })
        assertTrue(args.none { it.startsWith("--bt-") && it != "--bt-enable-lpd=false" })
        assertEquals("rpc-secret=zz\n", Aria2Config.conf("zz"))
        assertTrue("--seed-time=${Aria2Config.SEED_TIME_ON_MIN}" in Aria2Config.args(File("c"), File("s"), File("d"), 1, 1, DlSettings(seeding = true), null, listOf("1.1.1.1"), File("h")))
    }

    @Test fun speeds() {
        assertEquals(512L * 1024, Aria2Config.speedBytes("512K")); assertEquals(2L shl 20, Aria2Config.speedBytes("2M"))
        assertEquals(0, Aria2Config.speedBytes("0")); assertNull(Aria2Config.speedBytes("-1"))
    }
}

class LinkParsingTest {
    @Test fun recognisesLinks() {
        val u = LinkParser.parse("  https://example.org/films/Mon%20film.mkv ")
        assertEquals(listOf("https://example.org/films/Mon%20film.mkv"), ((u as LinkParser.Result.Ok).source as Source.Url).uris)
        assertEquals("Mon film.mkv", LinkParser.nameFromUrl("https://example.org/films/Mon%20film.mkv"))
        assertIs<LinkParser.Result.Ok>(LinkParser.parse("sftp://user:pw@nas.local/share/a.mp4"))
        assertIs<LinkParser.Result.Ok>(LinkParser.parse("ftp://ftp.example.org/pub/a.iso"))
        assertEquals("sftp://nas.local/share/a.mp4", LinkParser.display("sftp://user:pw@nas.local/share/a.mp4"))
    }

    @Test fun refusesWhatIsNotADownload() {
        for (bad in listOf("", "file:///etc/passwd", "javascript:alert(1)", "content://x/y", "http://127.0.0.1:6800/jsonrpc",
            "http://localhost:8765/api/info", "http://[::1]/x", "not a link", "magnet:?dn=x"))
            assertIs<LinkParser.Result.Bad>(LinkParser.parse(bad), bad)
    }

    @Test fun magnets() {
        val hex = "0123456789abcdef0123456789abcdef01234567"
        val m = (LinkParser.parse("magnet:?xt=urn:btih:${hex.uppercase()}&dn=Big+Buck+Bunny&xl=276445467&tr=udp%3A%2F%2Ft") as LinkParser.Result.Ok).source as Source.Magnet
        assertEquals(hex, m.infoHash); assertEquals("Big Buck Bunny", m.name); assertEquals(276445467L, m.size)
        // Base32 form of the same hash.
        val b32 = "AERUKZ4JVPG66AJDIVTYTK6N54ASGRLH"
        assertEquals(hex, ((LinkParser.parse("magnet:?xt=urn:btih:$b32") as LinkParser.Result.Ok).source as Source.Magnet).infoHash)
    }
}

class TorrentParsingTest {
    @Test fun singleFileTorrent() {
        val b = Benc.torrent("Big Buck Bunny.mp4", length = 276445467)
        val t = TorrentInfo.parse(b)
        assertEquals("Big Buck Bunny.mp4", t.name); assertEquals(276445467L, t.totalLength); assertEquals(1, t.files.size)
        // Info hash = SHA-1 of the bencoded info dictionary, byte for byte.
        val info = Benc.enc(Benc.info("Big Buck Bunny.mp4", length = 276445467))
        assertEquals(MessageDigest.getInstance("SHA-1").digest(info).joinToString("") { "%02x".format(it) }, t.infoHash)
    }

    @Test fun multiFileTorrent() {
        val t = TorrentInfo.parse(Benc.torrent("Série", listOf("S01/E01.mkv" to (700L shl 20), "S01/E02.mkv" to (650L shl 20), "info.nfo" to 1024)))
        assertEquals("Série", t.name); assertEquals((700L shl 20) + (650L shl 20) + 1024, t.totalLength)
        assertEquals("S01/E02.mkv", t.files[1].path)
    }

    @Test fun refusesGarbage() {
        assertFails { TorrentInfo.parse("not bencode".toByteArray()) }
        assertFails { TorrentInfo.parse(Benc.enc(mapOf("announce" to "x"))) }
        assertFails { TorrentInfo.parse("l".repeat(200).toByteArray()) }
    }

    @Test fun metalink() {
        val x = """<?xml version="1.0"?><metalink xmlns="urn:ietf:params:xml:ns:metalink"><file name="a &amp; b.iso"><size>1000</size>
            <url>http://x/a.iso</url></file><file name="c.iso"><size>24</size></file></metalink>"""
        val m = MetalinkInfo.parse(x.toByteArray())
        assertEquals(listOf("a & b.iso", "c.iso"), m.names); assertEquals(1024L, m.totalLength)
        assertFails { MetalinkInfo.parse("<!DOCTYPE x [<!ENTITY a 'b'>]><metalink/>".toByteArray()) }
        assertFails { MetalinkInfo.parse("<html/>".toByteArray()) }
    }
}
