package castbridge.core.wallet.ui

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.owner.InstallSigner
import castbridge.core.update.Ed25519
import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.WalletCache
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletStore
import castbridge.core.wallet.WalletTestKeys
import castbridge.core.wallet.ui.WalletMessages.Kind
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletClientTest {
    private class Mem(var text: String? = null) : WalletStore { override fun read() = text; override fun write(text: String) { this.text = text } }
    private class Req(val method: String, val path: String, val body: String?) { val json: Map<String, Any?> get() = JsonLite.obj(body ?: "{}") }

    /** Transport scripté : chaque appel prend la réponse suivante (ou lève l'erreur réseau). */
    private class Script(vararg steps: Any) : WalletTransport {
        private val queue = ArrayDeque(steps.toList())
        val requests = ArrayList<Req>()
        override fun send(method: String, path: String, body: String?): HttpLite.Response {
            requests += Req(method, path, body)
            return when (val s = queue.removeFirstOrNull() ?: throw IOException("plus de réponse scriptée")) {
                is IOException -> throw s
                is HttpLite.Response -> s
                else -> error("étape inconnue")
            }
        }
    }

    private val dev = WalletTestKeys.DEVICE_CODE
    private val now = WalletTestKeys.NOW
    private val signer = InstallSigner(ByteArray(32) { 0x55 })
    private fun resp(code: Int, body: String, headers: Map<String, List<String>> = emptyMap()) = HttpLite.Response(code, body, headers)
    private fun snap(seq: Long, n: Long = 3450, id: String = dev) = TestMint.snapshot(Snapshot(WalletTestKeys.wallet.keyId, id, "PROD", n, 0, 12, 0, seq, now, Snapshot.Flags(false, true, true)))
    private fun syncBody(token: String) = """{"snapshot":"$token","history":[],"contributions":{},"notices":[],"edition":{"ed":"PROD","license":"ACTIVE","grace":false,"boundOther":false}}"""
    private fun err(status: Int, reason: String?, msg: String = "m") = resp(status, """{"status":$status,"message":"$msg","details":[${reason?.let { "\"$it\"" } ?: ""}]}""")
    private fun cache(store: Mem = Mem()) = WalletCache(store, WalletTestKeys.walletRing, WalletTestKeys.voucherKeys, dev)
    private fun client(t: WalletTransport, c: WalletCache = cache(), id: WalletIdentity? = WalletIdentity(dev, "api-dev-1", listOf("cbx1.a.b", "cbx1.c.d"), signer), at: () -> Long = { now }) =
        WalletClient(t, { id }, c, at)

    @Test fun syncSendsActivationsAndTheProofOfPossessionThenStoresTheSignedSnapshot() {
        val t = Script(resp(200, syncBody(snap(5))))
        val c = cache()
        val r = client(t, c).sync()
        assertTrue(r is WalletResult.Ok, r.toString())
        val q = t.requests.single()
        assertEquals("POST", q.method); assertEquals("/api/v1/wallet/sync", q.path)
        assertEquals(dev, q.json["deviceCode"]); assertEquals(listOf("cbx1.a.b", "cbx1.c.d"), q.json["activations"])
        @Suppress("UNCHECKED_CAST") val bind = q.json["bind"] as Map<String, Any?>
        assertEquals(signer.publicKeyBase64, bind["key"]); assertEquals(now, bind["at"])
        assertTrue(Ed25519.verify(Base64.getDecoder().decode(bind["key"] as String), WalletBind.message(dev, "api-dev-1", now).toByteArray(), Base64.getDecoder().decode(bind["sig"] as String)))
        assertEquals(3450, c.snapshot!!.n); assertEquals(5, c.snapshot!!.seq)                // le cache est la seule mémoire du solde
        assertEquals("PROD", (r as WalletResult.Ok).value.edition!!.ed)
    }

    @Test fun noBindWithoutAnInstallKeyOrAnApiDeviceId() {
        val t = Script(resp(200, syncBody(snap(1))), resp(200, syncBody(snap(2))))
        client(t, id = WalletIdentity(dev, "api-dev-1", emptyList(), null)).sync()
        client(t, id = WalletIdentity(dev, null, emptyList(), signer)).sync()
        t.requests.forEach { assertFalse(it.json.containsKey("bind")); assertEquals(emptyList<Any?>(), it.json["activations"]) }
    }

    @Test fun deviceCodeIsSentInItsCanonicalForm() {
        val t = Script(resp(200, syncBody(snap(1))))
        client(t, id = WalletIdentity(dev.lowercase().replace("-", " "), "api-dev-1", emptyList(), signer)).sync()
        assertEquals(dev, t.requests.single().json["deviceCode"])
    }

    @Test fun bindProofRefusedIsRetriedOnceWithTheServerClock() {
        val serverMs = now + 20 * 60_000L                                                    // la TV retarde de 20 min
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(serverMs).atZone(ZoneOffset.UTC))
        val t = Script(resp(409, """{"message":"x","details":["BIND_PROOF"]}""", mapOf("Date" to listOf(date))), resp(200, syncBody(snap(3))))
        val r = client(t).sync()
        assertTrue(r is WalletResult.Ok, r.toString())
        assertEquals(2, t.requests.size)
        @Suppress("UNCHECKED_CAST") assertEquals(serverMs / 1000 * 1000, (t.requests[1].json["bind"] as Map<String, Any?>)["at"])
    }

    @Test fun bindProofRefusedTwiceIsShownWithAClearText() {
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC))
        val t = Script(resp(409, """{"details":["BIND_PROOF"]}""", mapOf("Date" to listOf(date))), resp(409, """{"details":["BIND_PROOF"]}""", mapOf("Date" to listOf(date))))
        val r = client(t).sync() as WalletResult.Fail
        assertEquals(Kind.BIND_PROOF, r.shown.kind); assertEquals(2, t.requests.size); assertFalse(r.network)
    }

    @Test fun bindProofRefusedWithoutADateHeaderIsNotRetried() {
        val t = Script(err(409, "BIND_PROOF"))
        assertEquals(Kind.BIND_PROOF, (client(t).sync() as WalletResult.Fail).shown.kind)
        assertEquals(1, t.requests.size)
    }

    @Test fun missingServiceIsReportedAsUnavailable() {
        for (code in listOf(404, 503)) {
            val r = client(Script(err(code, null))).sync() as WalletResult.Fail
            assertEquals(Kind.UNAVAILABLE, r.shown.kind, "$code"); assertEquals(code, r.status); assertEquals("Jetons : service indisponible", r.shown.text)
        }
    }

    @Test fun activateRefusalIsAClearText() {
        val r = client(Script(err(409, "ACTIVATE"))).sync() as WalletResult.Fail
        assertEquals(Kind.ACTIVATE, r.shown.kind); assertEquals("Activez la TV pour recevoir des jetons", r.shown.text)
    }

    @Test fun aSnapshotOfAnotherTvIsRefusedAndNeverCached() {
        val c = cache()
        val r = client(Script(resp(200, syncBody(snap(9, id = "tv-autre")))), c).sync() as WalletResult.Fail
        assertTrue(r.shown.text.contains("OTHER_TV")); assertNull(c.snapshot)
    }

    @Test fun aSnapshotSignedByAnUnknownKeyIsRefused() {
        val c = cache()
        val forged = TestMint.snapshot(Snapshot(WalletTestKeys.stranger.keyId, dev, "PROD", 999_999, 0, 0, 0, 9, now, Snapshot.Flags(false, true, true)), signer = WalletTestKeys.stranger)
        val r = client(Script(resp(200, syncBody(forged))), c).sync() as WalletResult.Fail
        assertTrue(r.shown.text.contains("UNKNOWN_KEY")); assertNull(c.snapshot)
    }

    @Test fun aGarbledSuccessBodyIsAFailureNotACrash() {
        val r = client(Script(resp(200, "<html>proxy</html>"))).sync() as WalletResult.Fail
        assertEquals(Kind.RETRY_LATER, r.shown.kind); assertFalse(r.network)
    }

    @Test fun networkErrorsRetryWithTheSameKeyAndBody() {
        val t = Script(IOException("coupé"), IOException("coupé"), resp(200, """{"dir":"N2M","q":2,"rate":1000,"reverseFeeBp":200,"ndemGross":2000,"fee":0,"ndemNet":2000,"replayed":true,"snapshot":"${snap(6, n = 1450)}"}"""))
        val c = cache(); c.offerSnapshot(snap(5))
        val r = client(t, c).convert(ConvertDir.N2M, 2, "tv-abc123")
        assertTrue(r is WalletResult.Ok)
        assertEquals(3, t.requests.size)
        assertEquals(1, t.requests.map { it.body }.toSet().size)                              // octet pour octet la même requête
        assertEquals("tv-abc123", t.requests[0].json["idem"])
        assertEquals(1450, c.snapshot!!.n)
    }

    @Test fun threeNetworkErrorsGiveUpWithoutInventingASuccess() {
        val t = Script(IOException("a"), IOException("b"), IOException("c"), resp(200, "{}"))
        val c = cache(); c.offerSnapshot(snap(5))
        val r = client(t, c).convert(ConvertDir.M2N, 1, "tv-k1") as WalletResult.Fail
        assertTrue(r.network); assertEquals(Kind.NETWORK, r.shown.kind); assertEquals(3, t.requests.size)
        assertEquals(3450, c.snapshot!!.n)                                                    // rien n'a bougé
    }

    @Test fun anAnswerOfTheServerIsNeverRetried() {
        val t = Script(err(409, "INSUFFICIENT", "Solde insuffisant : 3 450 NDEM disponibles"), resp(200, "{}"))
        val c = cache(); c.offerSnapshot(snap(5))
        val r = client(t, c).convert(ConvertDir.N2M, 9, "tv-k2") as WalletResult.Fail
        assertEquals(1, t.requests.size); assertEquals("INSUFFICIENT", r.reason); assertEquals(409, r.status)
        assertEquals("Solde insuffisant : 3 450 NDEM disponibles", r.shown.text)         // le solde affiché est celui de l'instantané signé
    }

    @Test fun insufficientUsesTheRightCurrencyOfTheOperation() {
        val c = cache(); c.offerSnapshot(snap(5))
        val r = client(Script(err(409, "INSUFFICIENT")), c).convert(ConvertDir.M2N, 99, "tv-k3") as WalletResult.Fail
        assertEquals("Solde insuffisant : 12 MBOKO disponibles", r.shown.text)
        val r2 = client(Script(err(409, "INSUFFICIENT")), c).transfer(WalletCurrency.NDEM, "R123-4567-89", 99_999, "tv-k4") as WalletResult.Fail
        assertEquals("Solde insuffisant : 3 450 NDEM disponibles", r2.shown.text)
    }

    @Test fun unknownReasonsAreTolerated() {
        val r = client(Script(err(409, "SOMETHING_NEW"))).convert(ConvertDir.N2M, 1, "tv-k5") as WalletResult.Fail
        assertEquals("Opération refusée (code SOMETHING_NEW)", r.shown.text); assertEquals("SOMETHING_NEW", r.reason)
    }

    @Test fun requestBodiesCarryNoBalanceAndTheExactServerFields() {
        val t = Script(resp(200, """{"dir":"N2M","q":1,"rate":1000,"reverseFeeBp":0,"ndemGross":1000,"fee":0,"ndemNet":1000,"replayed":false}"""),
            resp(200, """{"replayed":false,"cur":"MBOKO","amt":2,"to":"TV …4F2Q"}"""), resp(200, """{"code":"R123-4567-89","exp":1790000900000}"""),
            resp(200, """{"lines":[],"next":null}"""), resp(200, """{"rate":1000,"reverseFeeBp":200}"""))
        val cl = client(t)
        cl.convert(ConvertDir.N2M, 1, "tv-a1"); cl.transfer(WalletCurrency.MBOKO, "R123-4567-89", 2, "tv-a2"); val rc = cl.receiveCode(); cl.history(77L); cl.policy()
        assertEquals(setOf("deviceCode", "dir", "q", "idem"), t.requests[0].json.keys)
        assertEquals("N2M", t.requests[0].json["dir"]); assertEquals(1L, t.requests[0].json["q"])
        assertEquals("/api/v1/wallet/convert", t.requests[0].path)
        assertEquals(setOf("deviceCode", "cur", "amt", "code", "idem"), t.requests[1].json.keys)
        assertEquals("/api/v1/wallet/transfer", t.requests[1].path); assertEquals("MBOKO", t.requests[1].json["cur"])
        assertEquals("/api/v1/wallet/receive-code", t.requests[2].path); assertEquals(setOf("deviceCode"), t.requests[2].json.keys)
        assertEquals(ReceiveCodeView("R123-4567-89", 1_790_000_900_000L), (rc as WalletResult.Ok).value)
        assertEquals("GET", t.requests[3].method); assertEquals("/api/v1/wallet/history?before=77", t.requests[3].path); assertNull(t.requests[3].body)
        assertEquals("GET", t.requests[4].method); assertEquals("/api/v1/wallet/policy", t.requests[4].path)
    }

    @Test fun historyWithoutCursorHasNoQuery() {
        val t = Script(resp(200, """{"lines":[],"next":null}"""))
        client(t).history(null)
        assertEquals("/api/v1/wallet/history", t.requests.single().path)
    }

    @Test fun noIdentityMeansNoCall() {
        val t = Script(resp(200, "{}"))
        val r = client(t, id = null).sync() as WalletResult.Fail
        assertEquals(0, t.requests.size); assertEquals(Kind.ACTIVATE, r.shown.kind)
    }

    @Test fun anOlderSnapshotInAReplyNeverLowersTheCache() {
        val c = cache(); c.offerSnapshot(snap(10, n = 500))
        val r = client(Script(resp(200, syncBody(snap(4, n = 9_999)))), c).sync()
        assertTrue(r is WalletResult.Ok)
        assertEquals(500, c.snapshot!!.n)
        assertNotNull(c.snapshot)
    }
}
