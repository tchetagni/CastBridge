package castbridge.core.owner

import castbridge.core.FakePlayer
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.TvProfile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.*

/** The trial is an ALLOWLIST: every route of the TV that is not listed is closed (so a new route is denied by default). */
class TrialRoutesTest {
    private val allowed = listOf(
        "/", "/api/hello", "/api/info", "/api/sysinfo", "/api/playurl", "/api/pause", "/api/resume", "/api/stop", "/api/seek", "/api/volume",
        "/api/player/tracks", "/api/player/next", "/api/player/repeat", "/api/remote/key", "/api/remote/state",
        "/api/activation", "/api/activation/install", "/api/activation/request", "/api/rental", "/api/rental/install", "/api/lots", "/api/lots/upload", "/api/lots/install",
        "/api/learn", "/api/learn/open", "/api/sudoku", "/api/sudoku/open", "/api/games", "/api/games/open", "/api/bluetooth", "/api/bluetooth/tunnel", "/api/bluetooth/tunnel/enable",
        "/api/connections", "/api/server/me")
    private val denied = listOf(
        "/api/play", "/api/playlist", "/api/library", "/api/library/watched", "/api/thumb", "/api/part", "/api/reset", "/api/delete", "/api/rename", "/api/folders", "/api/folders/set",
        "/api/storage", "/api/storage/move", "/api/storage/target", "/api/storage/saf/pick", "/api/transfer/start", "/api/transfer/caps", "/api/upload", "/upload/film.mp4",
        "/api/trash", "/api/trash/put", "/api/downloads", "/api/downloads/add", "/api/downloads/upload", "/api/dl", "/api/usb", "/api/usb/import", "/api/ssh", "/api/ssh/enable",
        "/api/ssh/key", "/api/apk", "/api/apk/install", "/api/update", "/api/update/install", "/api/server/install", "/api/server/erase", "/api/screenshot", "/api/devsettings",
        "/api/quiz", "/api/quiz/open", "/api/chess", "/api/chess/open", "/quiz", "/quiz/api/join", "/chess", "/chess/api/state", "/stream/film.mp4", "/stream/",
        "/api/player/subfile", "/api/new-future-route", "/api/hello/../ssh", "/api//ssh", "/api/%73sh", "/admin", "/api")

    @Test fun routeTable() {
        for (r in allowed) assertTrue(TrialPolicy.routeAllowed(r), "allowed: $r")
        for (r in denied) assertFalse(TrialPolicy.routeAllowed(r), "denied: $r")
        assertTrue(allowed.size + denied.size >= 40)
    }

    @Test fun prefixesNeedAPathBoundary() {
        assertFalse(TrialPolicy.routeAllowed("/api/lotsx")); assertFalse(TrialPolicy.routeAllowed("/api/activationx")); assertFalse(TrialPolicy.routeAllowed("/api/playerx"))
    }

    @Test fun onlyTheLoopbackPlayerTokenReadsStream() {
        assertTrue(TrialPolicy.streamAllowed(true)); assertFalse(TrialPolicy.streamAllowed(false))
    }

    @Test fun bluetoothKeepsOnlyLots() {
        assertTrue(TrialPolicy.btFileAllowed("castbridge-lot-learn-cm2-trial-v1.lot")); assertTrue(TrialPolicy.btFileAllowed("castbridge-lot-learn-cm2-trial-v1.lot.json"))
        for (n in listOf("film.mp4", "x.apk", "castbridge-lot-learn.lot", "a.json")) assertFalse(TrialPolicy.btFileAllowed(n), n)
    }

    private fun serveBt(name: String, accept: (String) -> Boolean): Pair<Int, ByteArray> {
        val req = ByteArrayOutputStream(); val d = DataOutputStream(req)
        d.write("CBT1".toByteArray()); d.write("000000".toByteArray()); val n = name.toByteArray(); d.writeShort(n.size); d.write(n); d.writeLong(10)
        val out = ByteArrayOutputStream()
        val dir = kotlin.io.path.createTempDirectory("bt").toFile()
        return try { BtProtocol.serve(dir, ByteArrayInputStream(req.toByteArray()), out, null, "AA", 0, acceptFile = accept) to out.toByteArray() } finally { dir.deleteRecursively() }
    }

    @Test fun bluetoothFileIsRefusedWithAClearErrorInTrial() {
        val (r, out) = serveBt("film.mp4") { TrialPolicy.btFileAllowed(it) }
        assertEquals(BtProtocol.ERR_TRIAL, r); assertEquals(BtProtocol.ERR_TRIAL, out[0].toInt())
        assertTrue(BtProtocol.describe(r).startsWith("Version d'essai : ")); assertTrue(BtProtocol.isFatal(r))
        // outside the trial the transfer starts as before (the fake link then ends: an IOException, never ERR_TRIAL)
        assertFailsWith<java.io.IOException> { serveBt("film.mp4") { true } }
    }

    private fun get(base: String, path: String): Int = (URL(base + path).openConnection() as HttpURLConnection).run { responseCode.also { disconnect() } }

    @Test fun serverGuardAppliesTheAllowlistAndClosesStreamWithoutTheToken() {
        var trial = true
        val dir = kotlin.io.path.createTempDirectory("tv").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(castbridge.core.tv.VolumeRegistry.single(dir), FakePlayer(), port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0),
            routeGuard = { p -> if (trial && TrialPolicy.routeBlocked(p)) TrialPolicy.MESSAGE else null }).apply { start(5000, false) }
        try {
            java.io.File(dir, "film.mp4").writeBytes(ByteArray(100))
            val base = "http://127.0.0.1:$port"
            assertEquals(200, get(base, "/api/hello")); assertEquals(200, get(base, "/api/info"))
            for (p in listOf("/api/library", "/stream/film.mp4", "/stream/film.mp4?t=guess", "/api/usb/import", "/api/ssh")) assertEquals(403, get(base, p), p)
            trial = false
            assertEquals(200, get(base, "/api/library")); assertEquals(200, get(base, "/stream/film.mp4"))
        } finally { server.stop(); dir.deleteRecursively() }
    }
}
