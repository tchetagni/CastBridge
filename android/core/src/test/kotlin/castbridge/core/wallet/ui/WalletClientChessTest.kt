package castbridge.core.wallet.ui

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.owner.InstallSigner
import castbridge.core.wallet.WalletCache
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletStore
import castbridge.core.wallet.WalletTestKeys
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les appels « blocage » et « règlement » du client portefeuille de la TV (échecs en ligne, option B) et la lecture des jeux misés de la politique. */
class WalletClientChessTest {
    private class Mem(var text: String? = null) : WalletStore { override fun read() = text; override fun write(text: String) { this.text = text } }
    private class Req(val method: String, val path: String, val body: String?) { val json: Map<String, Any?> get() = JsonLite.obj(body ?: "{}") }
    private class Script(vararg steps: Any) : WalletTransport {
        private val queue = ArrayDeque(steps.toList())
        val requests = ArrayList<Req>()
        override fun send(method: String, path: String, body: String?): HttpLite.Response {
            requests += Req(method, path, body)
            return when (val s = queue.removeFirstOrNull() ?: throw IOException("plus de réponse scriptée")) { is IOException -> throw s; is HttpLite.Response -> s; else -> error("étape inconnue") }
        }
    }

    private val dev = WalletTestKeys.DEVICE_CODE
    private val signer = InstallSigner(ByteArray(32) { 0x55 })
    private fun resp(code: Int, body: String) = HttpLite.Response(code, body, emptyMap())
    private fun err(status: Int, reason: String?, msg: String = "m") = resp(status, """{"status":$status,"message":"$msg","details":[${reason?.let { "\"$it\"" } ?: ""}]}""")
    private fun client(t: WalletTransport, id: WalletIdentity? = WalletIdentity(dev, "api-dev-1", listOf("cbx1.a.b"), signer)) =
        WalletClient(t, { id }, WalletCache(Mem(), WalletTestKeys.walletRing, WalletTestKeys.voucherKeys, dev), { WalletTestKeys.NOW })

    private val CBE1 = "cbe1." + "A".repeat(150) + "." + "B".repeat(86)
    private fun escrowBody(replayed: Boolean = false) = """{"cbe1":"$CBE1","eid":"EidAAAAAAAAAAAAAAAAAAA","iat":1000,"exp":1800000,"replayed":$replayed,"snapshot":null}"""

    @Test fun escrowAsksForOneSeatOnTheChessScaleWithTheIdempotencyKeyAndTheActivations() {
        val t = Script(resp(200, escrowBody()))
        val r = client(t).escrow(WalletCurrency.NDEM, 20, 1, "idem-1", game = "chess")
        assertTrue(r is WalletResult.Ok, r.toString())
        val q = t.requests.single()
        assertEquals("POST", q.method); assertEquals("/api/v1/wallet/escrow", q.path)
        assertEquals(dev, q.json["deviceCode"]); assertEquals("NDEM", q.json["cur"]); assertEquals(20L, q.json["per"]); assertEquals(1L, q.json["k"])
        assertEquals("idem-1", q.json["idem"]); assertEquals("chess", q.json["game"]); assertEquals(listOf("cbx1.a.b"), q.json["activations"])
        val d = (r as WalletResult.Ok).value
        assertEquals(CBE1, d.cbe1); assertEquals("EidAAAAAAAAAAAAAAAAAAA", d.eid); assertEquals(1_800_000L, d.exp); assertFalse(d.replayed)
    }

    @Test fun anEscrowWithoutAGameIsAQuizEscrowAsBefore() {
        val t = Script(resp(200, escrowBody()))
        client(t).escrow(WalletCurrency.MBOKO, 5, 4, "idem-2")
        assertFalse(t.requests.single().json.containsKey("game"))
    }

    @Test fun aNetworkCutReplaysTheSameRequestAndTheServerAnswersWithTheSameBlock() {
        val t = Script(IOException("coupure"), resp(200, escrowBody(replayed = true)))
        val r = client(t).escrow(WalletCurrency.NDEM, 20, 1, "idem-3", game = "chess") as WalletResult.Ok
        assertEquals(2, t.requests.size); assertEquals(t.requests[0].body, t.requests[1].body, "la MÊME requête est rejouée")
        assertTrue(r.value.replayed)
    }

    @Test fun escrowRefusalsCarryTheServersClosedReasonAndAFrenchText() {
        val cases = mapOf(
            "TRIAL_FREE_ONLY" to "Version d'essai", "STAKE_NOT_OFFERED" to "Cette mise n'est pas proposée", "INSUFFICIENT" to "Solde insuffisant", "STAKE_WIN_CAP" to "Limite de parties gagnées",
        )
        for ((reason, start) in cases) {
            val r = client(Script(err(409, reason, "m"))).escrow(WalletCurrency.NDEM, 20, 1, "i", "chess") as WalletResult.Fail
            assertEquals(reason, r.reason); assertTrue(r.shown.text.startsWith(start), "$reason -> ${r.shown.text}"); assertFalse(r.network)
        }
    }

    @Test fun theWinCapShowsTheServersOwnSentenceSoThePlayerKnowsWhenToComeBack() {
        val msg = "Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie avec mise : demain à 00:00."
        val r = client(Script(err(409, "STAKE_WIN_CAP", msg))).escrow(WalletCurrency.NDEM, 20, 1, "i", "chess") as WalletResult.Fail
        assertEquals(msg, r.shown.text)
        // un texte qui ne commence pas par « Limite atteinte » n'est jamais montré tel quel (jamais du texte libre du serveur)
        val other = client(Script(err(409, "STAKE_WIN_CAP", "<script>x</script>"))).escrow(WalletCurrency.NDEM, 20, 1, "i", "chess") as WalletResult.Fail
        assertTrue(other.shown.text.startsWith("Limite de parties gagnées"), other.shown.text)
    }

    @Test fun anUnreadableEscrowAnswerIsATechnicalFailureNotACrash() {
        val bad = listOf("{}", """{"cbe1":"nope","eid":"E","iat":1,"exp":2}""", """{"cbe1":"$CBE1"}""", "pas du json")
        for (b in bad) assertTrue(client(Script(resp(200, b))).escrow(WalletCurrency.NDEM, 20, 1, "i") is WalletResult.Fail, b)
    }

    @Test fun noActivationNoEscrow() {
        val r = client(Script(), id = null).escrow(WalletCurrency.NDEM, 20, 1, "i") as WalletResult.Fail
        assertEquals("ACTIVATE", r.reason)
    }

    // ------------------------------------------------------------------ règlement

    private val CBR1 = "cbr1.abc.def"
    private fun settleBody(fee: Long = 0) = """{"rid":"${"a".repeat(32)}","kind":"END","cur":"NDEM","game":"chess","fee":$fee,"lines":[{"eid":"EidAAAAAAAAAAAAAAAAAAA","id":"AAAA-AAAA-AAAA-AAAA","used":20,"pay":0,"fee":0},{"eid":"EidBBBBBBBBBBBBBBBBBBB","id":"BBBB-BBBB-BBBB-BBBB","used":20,"pay":40,"fee":$fee}]}"""

    @Test fun settlePostsTheSignedResultAndReadsTheLinesWithTheirFees() {
        val t = Script(resp(200, settleBody(fee = 2)))
        val r = client(t).settle(CBR1)
        val q = t.requests.single()
        assertEquals("POST", q.method); assertEquals("/api/v1/wallet/settle", q.path); assertEquals(CBR1, q.json["cbr1"])
        val d = (r as WalletResult.Ok).value
        assertEquals("END", d.kind); assertEquals("NDEM", d.cur); assertEquals(2L, d.fee); assertEquals(2, d.lines.size)
        assertEquals(2L, d.lines[1].fee); assertEquals(40L, d.lines[1].pay)
    }

    @Test fun settleWorksForAReplayAndForOldServersWithoutFeeFields() {
        val old = """{"rid":"${"a".repeat(32)}","kind":"ABORT","cur":"MBOKO","lines":[{"eid":"EidAAAAAAAAAAAAAAAAAAA","id":"AAAA-AAAA-AAAA-AAAA","used":0,"pay":0}]}"""
        val d = (client(Script(resp(200, old))).settle(CBR1) as WalletResult.Ok).value
        assertEquals(0L, d.fee); assertEquals(0L, d.lines.single().fee); assertNull(d.game)
    }

    @Test fun settleRefusalsAreReadAndANetworkCutIsOffline() {
        assertEquals("RESULT_AFTER_REFUND", (client(Script(err(409, "RESULT_AFTER_REFUND"))).settle(CBR1) as WalletResult.Fail).reason)
        val off = client(Script(IOException("a"), IOException("b"), IOException("c"))).settle(CBR1) as WalletResult.Fail
        assertTrue(off.network)
    }

    // ------------------------------------------------------------------ politique : les jeux misés

    private fun policyBody(games: String) =
        """{"rate":100,"reverseFeeBp":300,"switches":{"convert":true,"transfer":true,"vouchers":true,"stakesNdem":true,"stakesMboko":true},"transferDailyCap":{"NDEM":1000,"MBOKO":500}$games}"""

    @Test fun thePolicyTellsTheChessScaleFeeAndWinCaps() {
        val body = policyBody(""","games":{"chess":{"enabled":true,"stakes":{"NDEM":[200,10,20,50,100],"MBOKO":[1,2,5,10]},"feeBp":250,"winCaps":{"day":3,"week":10,"month":15},"seats":1,"trialStakes":false}}""")
        val p = WalletReplies.parsePolicy(body)!!
        val g = p.games.getValue("chess")
        assertTrue(g.enabled); assertEquals(listOf(10L, 20L, 50L, 100L, 200L), g.stakesNdem, "triée"); assertEquals(listOf(1L, 2L, 5L, 10L), g.stakesMboko)
        assertEquals(250, g.feeBp); assertEquals(3, g.capDay); assertEquals(10, g.capWeek); assertEquals(15, g.capMonth)
    }

    @Test fun anOlderServerWithoutGamesStillGivesAPolicy() {
        val p = WalletReplies.parsePolicy(policyBody(""))!!
        assertTrue(p.games.isEmpty())
    }

    @Test fun anIllegibleGameEntryIsIgnoredAndNegativeOrAbsurdValuesAreBounded() {
        val body = policyBody(""","games":{"chess":{"enabled":false,"stakes":{"NDEM":[0,-5,20,"x"],"MBOKO":[]},"feeBp":99999,"winCaps":{"day":-1}},"bogus":42,"other":{"enabled":true}}""")
        val g = WalletReplies.parsePolicy(body)!!.games
        assertEquals(setOf("chess"), g.keys, "ni un jeu sans échelle ni une valeur qui n'est pas un objet")
        val c = g.getValue("chess")
        assertFalse(c.enabled); assertEquals(listOf(20L), c.stakesNdem); assertTrue(c.stakesMboko.isEmpty()); assertEquals(2_000, c.feeBp); assertEquals(0, c.capDay)
        assertNotNull(c)
    }
}
