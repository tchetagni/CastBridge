package castbridge.core.lots

import castbridge.core.langues.LangLotBuilder
import castbridge.core.langues.LangLotConsumer
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

/** The TV downloads its own Langues lots (server -> TV): selection, verification, resume, refusals. Fake remote, real consumer, real store. No network. */
class TvLotFetcherTest {
    private val langues = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private val scopes = listOf("zh-a0-salut-fr", "de-a0-famille-en", "fr-a0-salut-en")
    private fun real(scope: String): ByteArray = LangLotBuilder.zip(langues.resolve(scope))
    /** The version written in the pack itself (the lot must carry it). */
    private fun versionOf(scope: String): Int = langues.resolve(scope).resolve("langue.json").takeIf { it.isFile }
        ?.let { Regex("\"version\":\\s*(\\d+)").find(it.readText())?.groupValues?.get(1)?.toInt() } ?: 1
    private fun meta(scope: String, data: ByteArray, version: Int = versionOf(scope)) = LotMeta(LotId("langues", scope), version, data.size.toLong(), LotHash.sha256Hex(data), "Lot $scope")
    private fun lots(vararg s: String) = (if (s.isEmpty()) scopes else s.toList()).map { sc -> real(sc).let { Published(meta(sc, it), it) } }

    /** The pack of [scope] re-versioned: the lot of a later release. */
    private fun v2(scope: String): ByteArray {
        val dir = langues.resolve(scope)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for (n in listOf("langue.json", "media.json")) {
                val f = File(dir, n); if (!f.isFile) continue
                var t = f.readText(); if (n == "langue.json") t = t.replace(Regex("\"version\":\\s*\\d+"), "\"version\": ${versionOf(scope) + 1}")
                z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private class Env(val max: Long = LotBudget.TV_MAX_BYTES, val keys: List<String> = listOf(Kit.pub), app: Int = 10) {
        val dir = Kit.tmp()
        val consumer = LangLotConsumer(File(dir, "langues"))
        val learn = FakeConsumer("learn")
        val store = TvLotStore(dir, mapOf("langues" to consumer, "learn" to learn), keys, app, { 0L }, max)
    }

    private var online = true
    private fun fetcher(e: Env, r: LotRemote, families: LotFamilies? = null, attempts: Int = 3, deadline: Long = 60_000, now: () -> Long = System::currentTimeMillis) =
        TvLotFetcher(r, e.store, e.keys, 10, { online }, families, sleep = {}, now = now, maxAttempts = attempts, lotDeadlineMs = deadline)

    @Test fun downloadsOnlyLanguesLotsAndInstallsThemThroughTheStore() {
        val e = Env()
        val learn = Kit.bytes(1, 500).let { Published(Kit.meta("learn", "cm2", 1, it), it) }
        val quiz = Kit.bytes(2, 500).let { Published(Kit.meta("quiz", "cm2", 1, it), it) }
        val notLang = Kit.bytes(3, 500).let { Published(Kit.meta("langues", "cm2", 1, it), it) }   // a `langues` entry that is not a Langues scope
        val remote = FakeRemote(lots() + listOf(learn, quiz, notLang))
        val rep = fetcher(e, remote).run()
        assertTrue(rep.ok, rep.message()); assertNull(rep.blocked)
        assertEquals(scopes.toSet(), e.consumer.installed().map { it.id.scope }.toSet())
        assertEquals(3, rep.installed); assertEquals(3, remote.downloads.size)
        assertTrue(remote.downloads.none { it.startsWith("learn:") || it.startsWith("quiz:") || it.contains(":cm2") })
        assertTrue(e.learn.held.isEmpty())
        assertEquals("3 leçon(s) de langues installée(s).", rep.message())
        // second press: nothing to do, nothing downloaded
        remote.downloads.clear()
        val again = fetcher(e, remote).run()
        assertTrue(again.ok); assertEquals(0, again.installed); assertTrue(remote.downloads.isEmpty()); assertEquals(3, again.upToDate)
        assertEquals("Les leçons de langues sont à jour.", again.message())
        assertTrue(e.store.manifest().lots.size == 3)
    }

    @Test fun anOutdatedLotIsUpdatedAndNeverDowngraded() {
        val e = Env()
        val remote = FakeRemote(lots("zh-a0-salut-fr"))
        assertTrue(fetcher(e, remote).run().ok)
        val d2 = v2("zh-a0-salut-fr")
        remote.published = listOf(Published(meta("zh-a0-salut-fr", d2, versionOf("zh-a0-salut-fr") + 1), d2))
        val rep = fetcher(e, remote).run()
        assertEquals(listOf(TvLotFetcher.Status.UPDATED), rep.results.map { it.status }, rep.message())
        assertEquals(versionOf("zh-a0-salut-fr") + 1, e.consumer.installedOne("zh-a0-salut-fr")!!.meta.version)
        // an older catalog (replay) changes nothing
        remote.published = lots("zh-a0-salut-fr"); remote.downloads.clear()
        assertEquals(0, fetcher(e, remote).run().installed); assertTrue(remote.downloads.isEmpty())
        assertEquals(versionOf("zh-a0-salut-fr") + 1, e.consumer.installedOne("zh-a0-salut-fr")!!.meta.version)
    }

    @Test fun refusesWithoutInternetBeforeAnyRequest() {
        val e = Env(); val remote = FakeRemote(lots()); online = false
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Blocked.NO_INTERNET, rep.blocked); assertEquals(0, remote.catalogCalls)
        assertContains(rep.message(), "pas accès à Internet")
    }

    @Test fun anUnreachableServerIsSaidPlainly() {
        val e = Env(); val remote = FakeRemote(lots()).also { it.down = true }
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Blocked.SERVER_UNREACHABLE, rep.blocked); assertContains(rep.message(), "ne répond pas")
        assertTrue(e.consumer.installed().isEmpty())
    }

    @Test fun aCatalogNotSignedByTheServerKeyDownloadsNothing() {
        val e = Env(keys = listOf(Kit.otherPub)); val remote = FakeRemote(lots())
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Blocked.SIGNATURE_INVALID, rep.blocked); assertTrue(remote.downloads.isEmpty())
        assertContains(rep.message(), "pas signé")
        // a catalog altered after signing is refused too
        val e2 = Env(); val good = lots()
        val forged = FakeRemote(good) { ms -> Kit.sign(ms).let { c -> c.copy(lots = c.lots.map { it.copy(bytes = it.bytes + 1) }) } }
        assertEquals(TvLotFetcher.Blocked.SIGNATURE_INVALID, fetcher(e2, forged).run().blocked); assertTrue(forged.downloads.isEmpty())
        // no key at all
        val e3 = Env(keys = emptyList()); assertEquals(TvLotFetcher.Blocked.NO_KEY, fetcher(e3, FakeRemote(good)).run().blocked)
    }

    @Test fun aSignedCatalogOfAnotherFeatureOrChannelIsNotUsed() {
        val e = Env()
        val other = FakeRemote(lots()) { ms -> Kit.sign(ms, feature = "learn") }
        assertEquals(TvLotFetcher.Blocked.CATALOG_UNREADABLE, fetcher(e, other).run().blocked); assertTrue(other.downloads.isEmpty())
        val beta = FakeRemote(lots()) { ms -> Kit.sign(ms, channel = "beta") }
        assertEquals(TvLotFetcher.Blocked.CATALOG_UNREADABLE, fetcher(e, beta).run().blocked)
        val filtered = FakeRemote(lots()) { ms -> Kit.sign(ms, feature = "langues") }
        assertTrue(fetcher(e, filtered).run().ok)
    }

    @Test fun aCutDownloadResumesFromWhatWasReceived() {
        val e = Env(); val remote = FakeRemote(lots("zh-a0-salut-fr")); remote.cutAfter = 300
        val rep = fetcher(e, remote).run()
        assertTrue(rep.ok, rep.message())
        assertEquals(2, remote.downloads.size)
        assertEquals("langues:zh-a0-salut-fr@0", remote.downloads[0]); assertEquals("langues:zh-a0-salut-fr@300", remote.downloads[1])
        assertEquals(1, e.consumer.installed().size)
        // out of retries: the part stays for the next press
        val e2 = Env(); val r2 = FakeRemote(lots("zh-a0-salut-fr")); r2.cutAfter = 300
        val rep2 = fetcher(e2, r2, attempts = 1).run()
        assertEquals(TvLotFetcher.Status.FAILED, rep2.results.single().status); assertContains(rep2.message(), "reprise possible")
        assertEquals(300, e2.store.received(LotNames.fileName(r2.published[0].meta)))
        val rep3 = fetcher(e2, r2, attempts = 1).run()
        assertTrue(rep3.ok, rep3.message()); assertEquals("langues:zh-a0-salut-fr@300", r2.downloads.last())
    }

    @Test fun aCorruptedDownloadIsNeverInstalledNorKept() {
        val e = Env(); val good = real("zh-a0-salut-fr"); val bad = good.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val remote = FakeRemote(listOf(Published(meta("zh-a0-salut-fr", good), bad)))
        val rep = fetcher(e, remote, attempts = 2).run()
        assertEquals(TvLotFetcher.Status.FAILED, rep.results.single().status); assertContains(rep.message(), "SHA-256")
        assertTrue(e.consumer.installed().isEmpty()); assertEquals(0, e.store.received(LotNames.fileName(meta("zh-a0-salut-fr", good))))
        assertEquals(2, remote.downloads.size)        // retried from scratch
    }

    @Test fun aLotTheStoreWouldRefuseIsReported() {
        val e = Env()
        val junk = Kit.bytes(5, 400)                  // signed and hash-correct, but not a Langues pack
        val rep = fetcher(e, FakeRemote(listOf(Published(meta("zh-a0-salut-fr", junk), junk)))).run()
        assertEquals(TvLotFetcher.Status.REFUSED, rep.results.single().status)
        assertTrue(e.consumer.installed().isEmpty())
    }

    @Test fun aFullTvSkipsTheLotAndEvictsNothing() {
        val e = Env(max = 1000)
        val keep = Kit.bytes(7, 900); val km = Kit.meta("learn", "cm2", 1, keep)
        val part = e.store.partFile(LotNames.fileName(km)).also { it.parentFile.mkdirs(); it.writeBytes(keep) }
        assertTrue(e.store.installReceived(LotNames.fileName(km), Kit.sign(listOf(km)).toJson()) is TvLotStore.Result.Ok, "setup"); assertFalse(part.exists())
        val rep = fetcher(e, FakeRemote(lots("zh-a0-salut-fr"))).run()
        assertEquals(TvLotFetcher.Status.SKIPPED_FULL, rep.results.single().status); assertContains(rep.message(), "Pas assez de place")
        assertEquals(listOf(LotId("learn", "cm2")), e.learn.installed().map { it.id })      // the phone's data is still there
        assertTrue(e.consumer.installed().isEmpty())
    }

    @Test fun aLotPushedByThePhoneMeanwhileNeverCausesAnEviction() {
        val d = real("zh-a0-salut-fr"); val e = Env(max = d.size + 100L)
        val remote = FakeRemote(lots("zh-a0-salut-fr"))
        remote.onOpen = {
            // the phone fills the TV between the space check and the install
            val l = Kit.bytes(8, 200); val lm = Kit.meta("learn", "cm2", 1, l)
            e.store.partFile(LotNames.fileName(lm)).also { it.parentFile.mkdirs(); it.writeBytes(l) }
            assertTrue(e.store.installReceived(LotNames.fileName(lm), Kit.sign(listOf(lm)).toJson()) is TvLotStore.Result.Ok, "phone push")
            remote.onOpen = null
        }
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Status.REFUSED, rep.results.single().status); assertContains(rep.results.single().message, "rien n'a été supprimé")
        assertEquals(listOf(LotId("learn", "cm2")), e.learn.installed().map { it.id })      // the phone's lot was NOT evicted
        assertTrue(e.consumer.installed().isEmpty())
    }

    @Test fun aTlsCertificateFailureIsAPlainFrenchMessageOnTheCatalogAndOnADownload() {
        val chain = javax.net.ssl.SSLHandshakeException("Chain validation failed").apply { initCause(java.security.cert.CertificateNotYetValidException("NotBefore: 1970")) }
        val e = Env(); val remote = FakeRemote(lots("zh-a0-salut-fr"))
        remote.failWith = chain
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Blocked.CERTIFICATE_REFUSED, rep.blocked)
        assertEquals("Certificat du serveur refusé : vérifiez la date et l'heure de la TV", rep.message()); assertFalse(rep.message().contains("Chain"))
        // the cause chain is searched (an IOException wrapping the real error), a plain network error stays « serveur injoignable »
        remote.failWith = IOException("wrap", java.security.cert.CertificateExpiredException("expired"))
        assertEquals(TvLotFetcher.Blocked.CERTIFICATE_REFUSED, fetcher(e, remote).run().blocked)
        remote.failWith = IOException("hors ligne"); assertEquals(TvLotFetcher.Blocked.SERVER_UNREACHABLE, fetcher(e, remote).run().blocked)
        // failing only at the download: no retry (3 attempts would change nothing), same message
        val r2 = FakeRemote(lots("zh-a0-salut-fr")); var opens = 0; r2.onOpen = { opens++; r2.failWith = chain }
        val rep2 = fetcher(e, r2).run()
        assertEquals(TvLotFetcher.Status.FAILED, rep2.results.single().status); assertEquals(TvLotFetcher.CERT_MESSAGE, rep2.results.single().message); assertEquals(1, opens)
    }

    @Test fun anAppTooOldIsToldAndTheLotIsNotDownloaded() {
        val e = Env(); val d = real("zh-a0-salut-fr")
        val remote = FakeRemote(listOf(Published(meta("zh-a0-salut-fr", d).copy(minAppVersion = 99), d)))
        val rep = fetcher(e, remote).run()
        assertEquals(TvLotFetcher.Status.SKIPPED_APP_TOO_OLD, rep.results.single().status); assertTrue(remote.downloads.isEmpty())
    }

    @Test fun onlyFreeLotsWhenFamiliesAreGiven() {
        val e = Env(); val remote = FakeRemote(lots("zh-a0-salut-fr", "de-a0-famille-en"))
        val fam = LotFamilies.explicit(setOf("langues:zh-a0-salut-fr"), setOf("langues:de-a0-famille-en"))
        val rep = fetcher(e, remote, fam).run()
        assertEquals(listOf("zh-a0-salut-fr"), e.consumer.installed().map { it.id.scope }); assertEquals(1, remote.downloads.size)
        assertTrue(rep.ok)
        val unknown = Env(); val r2 = FakeRemote(lots("zh-a0-salut-fr"))
        fetcher(unknown, r2, LotFamilies.explicit(emptySet(), emptySet())).run(); assertTrue(r2.downloads.isEmpty())
    }

    @Test fun anOversizedAnnouncedLotAndTheLotCapAreRespected() {
        val e = Env(); val big = Published(LotMeta(LotId("langues", "zh-a0-gros-fr"), 1, (3L shl 20) + 1, "0".repeat(64), "gros"), ByteArray(0))
        val remote = FakeRemote(listOf(big))
        assertTrue(fetcher(e, remote).run().results.isEmpty()); assertTrue(remote.downloads.isEmpty())
        val many = (1..10).map { i -> Kit.bytes(i, 50).let { Published(meta("zh-a0-t$i-fr", it), it) } }
        val f = TvLotFetcher(FakeRemote(many), e.store, e.keys, 10, { true }, maxLots = 4, sleep = {})
        assertEquals(4, f.select(Kit.sign(many.map { it.meta }), emptyList()).lots.size)
    }

    @Test fun cancelAndRetiredLots() {
        val e = Env(); val remote = FakeRemote(lots())
        val rep = fetcher(e, remote).run(cancelled = { true })
        assertTrue(rep.results.all { it.status == TvLotFetcher.Status.CANCELLED }); assertTrue(remote.downloads.isEmpty())
        val gone = FakeRemote(lots("zh-a0-salut-fr")).also { it.gone = true }
        val r2 = fetcher(e, gone).run()
        assertEquals(TvLotFetcher.Status.FAILED, r2.results.single().status); assertContains(r2.message(), "n'est plus proposé")
    }

    @Test fun theDeadlineStopsASlowLot() {
        val e = Env(); var t = 0L
        val remote = FakeRemote(lots("zh-a0-salut-fr")).also { it.cutAfter = 100 }
        val rep = fetcher(e, remote, attempts = 5, deadline = 1000, now = { t += 600; t }).run()
        assertEquals(TvLotFetcher.Status.FAILED, rep.results.single().status)
        assertTrue(remote.downloads.size < 5, "stopped by the deadline: ${remote.downloads}")
    }

    /** The TV stays offline unless the user presses the button: nothing but the Langues screen may build or run the fetcher (no service, no receiver, no job, no timer). */
    @Test fun onlyTheLanguesScreenUsesTheTvFetcher() {
        val recv = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.parentFile.resolve("android/receiver/src/main")
        assertTrue(recv.resolve("kotlin/castbridge/receiver/LanguesHub.kt").isFile, "receiver sources found")
        val users = recv.walkTopDown().filter { it.isFile && it.extension == "kt" && Regex("TvLotFetcher|SecureHttpLotRemote|LanguesHub\\.fetcher").containsMatchIn(it.readText()) }.map { it.name }.toSet()
        assertEquals(setOf("LanguesHub.kt", "LanguesActivity.kt"), users)
        val act = recv.resolve("kotlin/castbridge/receiver/LanguesActivity.kt").readText()
        assertEquals(1, Regex("LanguesHub\\.fetcher\\(").findAll(act).count())
        assertTrue(Regex("JobScheduler|AlarmManager|WorkManager|Handler\\(.*postDelayed|Timer\\(").findAll(act).none())
        val manifest = recv.resolve("AndroidManifest.xml").readText()
        assertFalse(manifest.contains("TvLotFetcher"))
    }

    @Test fun progressIsReported() {
        val e = Env(); val seen = ArrayList<TvLotFetcher.Progress>()
        fetcher(e, FakeRemote(lots("zh-a0-salut-fr", "de-a0-famille-en"))).run(progress = { seen += it })
        assertTrue(seen.any { it.done == it.total && it.total > 0 }); assertEquals(setOf(1, 2), seen.map { it.index }.toSet()); assertTrue(seen.all { it.count == 2 })
    }
}

/** The hardened HTTP remote, against a loopback server (no Internet). */
class SecureHttpLotRemoteTest {
    private val data = Kit.bytes(11, 3000)
    private val meta = Kit.meta("langues", "zh-a0-salut-fr", 1, data)
    private var server: HttpServer? = null
    @AfterTest fun stop() { server?.stop(0) }

    private fun serve(setup: (HttpServer) -> Unit): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); setup(s); s.start(); server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @Test fun onlyHttpsExceptLoopbackForTests() {
        assertFailsWith<IllegalArgumentException> { SecureHttpLotRemote("http://bridge.sti-cm.com") }
        assertFailsWith<IllegalArgumentException> { SecureHttpLotRemote("http://127.0.0.1:1") }                       // loopback http needs the explicit test flag
        assertFailsWith<IllegalArgumentException> { SecureHttpLotRemote("https://user:pw@bridge.sti-cm.com") }
        assertFailsWith<IllegalArgumentException> { SecureHttpLotRemote("ftp://bridge.sti-cm.com") }
        SecureHttpLotRemote("https://bridge.sti-cm.com"); SecureHttpLotRemote("http://127.0.0.1:1", allowLoopbackHttp = true)
    }

    @Test fun asksTheFilteredCatalogAndRangesTheLot() {
        var query: String? = null; var range: String? = null; var ifRange: String? = null
        val base = serve { s ->
            s.createContext("/api/v1/lots/catalog") { x -> query = x.requestURI.query; val b = "{\"ok\":1}".toByteArray(); x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) } }
            s.createContext("/api/v1/lots/langues/zh-a0-salut-fr/1") { x ->
                range = x.requestHeaders.getFirst("Range"); ifRange = x.requestHeaders.getFirst("If-Range")
                val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                val b = data.copyOfRange(from, data.size)
                if (from > 0) x.responseHeaders.add("Content-Range", "bytes $from-${data.size - 1}/${data.size}")
                x.sendResponseHeaders(if (from > 0) 206 else 200, b.size.toLong()); x.responseBody.use { it.write(b) }
            }
        }
        val r = SecureHttpLotRemote(base, allowLoopbackHttp = true)
        assertEquals("{\"ok\":1}", r.catalogJson("stable", "langues")); assertEquals("channel=stable&feature=langues", query)
        r.open(meta, 0).use { assertContentEquals(data, it.input.readBytes()); assertEquals(0, it.from) }
        r.open(meta, 1000).use { assertContentEquals(data.copyOfRange(1000, data.size), it.input.readBytes()); assertEquals(1000, it.from) }
        assertEquals("bytes=1000-", range); assertEquals("\"${meta.sha256}\"", ifRange)
    }

    @Test fun aRedirectIsRefusedEvenToTheSameHost() {
        val base = serve { s ->
            s.createContext("/api/v1/lots/catalog") { x -> x.responseHeaders.add("Location", "http://127.0.0.1:9/evil"); x.sendResponseHeaders(302, -1); x.close() }
            s.createContext("/api/v1/lots/langues/zh-a0-salut-fr/1") { x -> x.responseHeaders.add("Location", "https://example.org/x"); x.sendResponseHeaders(301, -1); x.close() }
        }
        val r = SecureHttpLotRemote(base, allowLoopbackHttp = true)
        assertContains(assertFailsWith<IOException> { r.catalogJson("stable", "langues") }.message!!, "redirection refusée")
        assertContains(assertFailsWith<IOException> { r.open(meta, 0) }.message!!, "redirection refusée")
    }

    @Test fun errorsAreMappedAndTheCatalogIsBounded() {
        val base = serve { s ->
            s.createContext("/api/v1/lots/catalog") { x -> val b = ByteArray(SecureHttpLotRemote.MAX_CATALOG + 10); x.sendResponseHeaders(200, b.size.toLong()); x.responseBody.use { it.write(b) } }
            s.createContext("/api/v1/lots/langues/zh-a0-salut-fr/1") { x -> x.sendResponseHeaders(404, -1); x.close() }
        }
        val r = SecureHttpLotRemote(base, allowLoopbackHttp = true)
        assertContains(assertFailsWith<IOException> { r.catalogJson("stable", "langues") }.message!!, "trop gros")
        assertFailsWith<LotRemote.Gone> { r.open(meta, 0) }
        assertFailsWith<IOException> { r.open(meta.copy(id = LotId("langues", "../x")), 0) }
        assertFailsWith<IOException> { SecureHttpLotRemote("http://127.0.0.1:1", connectTimeoutMs = 300, allowLoopbackHttp = true).catalogJson("stable") }
    }
}

/** The phone refuses to store a Langues lot the TV would refuse (content check hook of [LotSync]). */
class LotSyncContentCheckTest {
    @Test fun aLotFailingTheFeatureCheckIsNeverStored() {
        val dir = Kit.tmp(); val store = LotStore(File(dir, "phone"), 10_000_000)
        val junk = Kit.bytes(21, 500); val m = Kit.meta("langues", "zh-a0-salut-fr", 1, junk)
        val remote = FakeRemote(listOf(Published(m, junk)))
        val sync = LotSync(store, remote, listOf(Kit.pub), 10, { Net.UNMETERED }, sleep = {}, check = { meta, f ->
            if (meta.id.feature == "langues") (LangLotConsumer.verifyContent(meta, f) as? LangLotConsumer.Companion.Verified.Bad)?.reason else null
        })
        val rep = sync.sync(listOf(m.id))
        assertEquals(LotSync.Outcome.FAILED, rep.results.single().outcome)
        assertNull(store.get(m.id)); assertFalse(store.partFile(m).exists())
        // the default (Apprendre, Quiz) is unchanged: no check, the lot is stored
        val plain = LotSync(LotStore(File(dir, "phone2"), 10_000_000), remote, listOf(Kit.pub), 10, { Net.UNMETERED }, sleep = {})
        assertEquals(LotSync.Outcome.INSTALLED, plain.sync(listOf(m.id)).results.single().outcome)
        dir.deleteRecursively()
    }
}
