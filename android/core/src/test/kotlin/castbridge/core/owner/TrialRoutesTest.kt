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
        "/api/connections", "/api/server/me",
        "/api/restart", "/api/net", "/api/background", "/api/autostart", "/api/overlay-permission", "/api/bluetooth/discoverable", "/api/bluetooth/tunnel/disable",
        "/api/gateway", "/api/gateway/test", "/api/gateway/speed", "/api/gateway/diag", "/api/content/reports", "/api/content/reports/ack",
        "/api/lots/part", "/api/lots/priority", "/api/lots/remove", "/api/rental/sweep", "/api/player/prev",
        "/api/player/audio", "/api/player/subtitle", "/api/player/subdelay", "/api/player/audiodelay", "/api/player/subsize", "/api/player/rate", "/api/player/aspect",
        "/api/player/chapter", "/api/player/title", "/api/player/hw", "/api/player/eq",
        "/api/learn/cmd", "/api/learn/dashboard", "/api/learn/events", "/api/learn/packs", "/api/learn/packs/remove", "/api/sudoku/cmd",
        "/api/server", "/api/server/url", "/api/server/contact",
        "/api/remote/text", "/api/remote/pointer", "/api/remote/global", "/api/remote/ping", "/api/remote/system/setup",
        "/api/parental", "/api/parental/supervision", "/api/parental/disable", "/api/parental/reset", "/api/parental/lock", "/api/parental/pin/create", "/api/parental/pin/change",
        "/api/parental/unlock", "/api/parental/config/get", "/api/parental/config/set", "/api/parental/report", "/api/parental/history/clear", "/api/parental/apps/list",
        "/api/parental/apps/rules/set", "/api/parental/reports/config/get", "/api/parental/reports/config/set", "/api/parental/reports/recipients/add",
        "/api/parental/reports/recipients/remove", "/api/parental/reports/now")
    private val denied = listOf(
        "/api/play", "/api/playlist", "/api/library", "/api/library/organize", "/api/library/organize/apply", "/api/library/watched", "/api/thumb", "/api/part", "/api/reset", "/api/delete", "/api/rename", "/api/folders", "/api/folders/set",
        "/api/storage", "/api/storage/move", "/api/storage/target", "/api/storage/saf/pick", "/api/transfer/start", "/api/transfer/caps", "/api/upload", "/upload/film.mp4",
        "/api/trash", "/api/trash/put", "/api/downloads", "/api/downloads/add", "/api/downloads/upload", "/api/dl", "/api/usb", "/api/usb/import", "/api/ssh", "/api/ssh/enable",
        "/api/ssh/key", "/api/apk", "/api/apk/install", "/api/update", "/api/update/install", "/api/server/install", "/api/server/erase", "/api/screenshot", "/api/devsettings",
        "/api/quiz", "/api/quiz/open", "/api/quiz/packs", "/api/quiz/packs/push", "/api/quiz/packs/remove", "/api/quiz/packs/status",
        "/api/chess/config", "/chess/api/act", "/chess/api/events", "/chess/api/hello", "/chess/api/join", "/chess/api/leave", "/quiz/api/act", "/quiz/api/events",
        "/quiz/api/hello", "/quiz/api/join", "/quiz/api/leave", "/quiz/api/state", "/upload/",
        "/api/downloads/about", "/api/downloads/accept", "/api/downloads/clear", "/api/downloads/files", "/api/downloads/options", "/api/downloads/pause", "/api/downloads/pauseall",
        "/api/downloads/peers", "/api/downloads/priority", "/api/downloads/remove", "/api/downloads/resume", "/api/downloads/resumeall", "/api/downloads/select", "/api/downloads/settings",
        "/api/folders/rename", "/api/library/watched", "/api/playlist", "/api/ssh/disable", "/api/ssh/key/remove",
        "/api/storage/check", "/api/storage/move/cancel", "/api/storage/open-settings", "/api/storage/rescan", "/api/trash/empty", "/api/trash/purge", "/api/trash/restore",
        "/api/transfer/begin", "/api/transfer/chunk", "/api/transfer/state", "/api/transfer/finish", "/api/transfer/abort",
        "/api/server/check-update", "/api/server/quiz-sync", "/api/chess", "/api/chess/open", "/quiz", "/quiz/api/join", "/chess", "/chess/api/state", "/stream/film.mp4", "/stream/",
        "/api/player/subfile", "/api/learn/packs/import", "/api/learn/packs/install", "/api/new-future-route", "/api/hello/../ssh", "/api//ssh", "/api/%73sh", "/admin", "/api")

    @Test fun routeTable() {
        for (r in allowed) assertTrue(TrialPolicy.routeAllowed(r), "allowed: $r")
        for (r in denied) assertFalse(TrialPolicy.routeAllowed(r), "denied: $r")
        assertTrue(allowed.size + denied.size >= 40)
    }

    /** `tools/routes/routes.txt` (generated from the code by tools/routes/list_routes.py) lists every route the TV serves: each one must be classified above. */
    private fun repoFile(rel: String): java.io.File {
        var d: java.io.File? = java.io.File("").absoluteFile
        while (d != null && !java.io.File(d, rel).exists()) d = d.parentFile
        return java.io.File(d ?: error("repo root not found"), rel)
    }
    private val servedRoutes: List<String> by lazy {
        repoFile("tools/routes/routes.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    @Test fun everyServedRouteIsClassified() {
        val classified = (allowed + denied).toSet()
        val missing = servedRoutes.filter { it !in classified }
        assertTrue(missing.isEmpty(), "routes servies non classées (ouvertes ou fermées en essai) : $missing")
        assertTrue(servedRoutes.size >= 150, "routes.txt incomplet : ${servedRoutes.size}")
        assertEquals(servedRoutes.size, servedRoutes.toSet().size)
    }

    @Test fun learnPackInstallsAreClosedInTrial() {
        assertFalse(TrialPolicy.routeAllowed("/api/learn/packs/import")); assertFalse(TrialPolicy.routeAllowed("/api/learn/packs/install"))
        assertTrue(TrialPolicy.routeAllowed("/api/learn/packs")); assertTrue(TrialPolicy.routeAllowed("/api/learn/open"))
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
