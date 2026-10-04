package castbridge.core.wallet.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les corps de réponse sont ceux de `WalletSyncController`, `WalletOpsController`, `WalletHistoryController` (rapports w22-02 et w22-05). */
class WalletRepliesTest {
    @Test fun syncReply() {
        val body = """{"snapshot":"cbw1.AAA.BBB","history":[{"id":12,"kind":"GRANT","currency":"NDEM","amount":3000,"at":1790000000000,"label":"Attribution mensuelle","counterparty":null},
            {"id":11,"kind":"TRANSFER","currency":"MBOKO","amount":-2,"at":1789999999000,"label":"Transfert envoyé","counterparty":"TV …4F2Q"}],
            "contributions":{"escrow-expiry":{"refunded":0}},"notices":[{"reason":"LICENSE_PENDING","text":"Licence en attente d'enregistrement"}],
            "edition":{"ed":"PROD","license":"PENDING","grace":false,"boundOther":false},"futureField":{"x":1}}"""
        val s = WalletReplies.parseSync(body)!!
        assertEquals("cbw1.AAA.BBB", s.snapshotToken)
        assertEquals(2, s.history.size)
        assertEquals(HistoryLine(12, "GRANT", "NDEM", 3000, 1_790_000_000_000L, "Attribution mensuelle", null), s.history[0])
        assertEquals("TV …4F2Q", s.history[1].counterparty); assertEquals(-2, s.history[1].amount)
        assertEquals(listOf(Notice("LICENSE_PENDING", "Licence en attente d'enregistrement")), s.notices)
        assertEquals(EditionInfo("PROD", "PENDING", false, false), s.edition)
    }

    @Test fun syncReplyWithoutSnapshotIsNotAReply() {
        assertNull(WalletReplies.parseSync("""{"history":[]}"""))
        assertNull(WalletReplies.parseSync("""{"snapshot":""}"""))
        assertNull(WalletReplies.parseSync("pas du json"))
        assertNull(WalletReplies.parseSync("[]"))
        assertNull(WalletReplies.parseSync(""))
    }

    @Test fun syncReplyToleratesMissingOrOddOptionalParts() {
        val s = WalletReplies.parseSync("""{"snapshot":"cbw1.A.B","history":"oops","notices":[{"reason":1},{"reason":"X","text":"y"},"z"],"edition":[]}""")!!
        assertTrue(s.history.isEmpty()); assertEquals(listOf(Notice("X", "y")), s.notices); assertNull(s.edition)
    }

    @Test fun policyReply() {
        val p = WalletReplies.parsePolicy("""{"rate":1000,"reverseFeeBp":200,"stake":{"NDEM":{"min":10,"max":500},"MBOKO":{"min":1,"max":10}},
            "transferDailyCap":{"NDEM":5000,"MBOKO":50},"switches":{"stakesNdem":true,"stakesMboko":false,"transfer":true,"convert":false,"vouchers":true},"extra":1}""")!!
        assertEquals(PolicyView(1000, 200, convert = false, transfer = true, vouchers = true, stakesNdem = true, stakesMboko = false, transferCapNdem = 5000, transferCapMboko = 50), p)
    }

    @Test fun policyWithoutRateIsUnusable() {
        assertNull(WalletReplies.parsePolicy("""{"reverseFeeBp":200}"""))
        assertNull(WalletReplies.parsePolicy("""{"rate":0,"reverseFeeBp":0}"""))
        assertNull(WalletReplies.parsePolicy("""{"rate":-5,"reverseFeeBp":0}"""))
        assertNotNull(WalletReplies.parsePolicy("""{"rate":1000,"reverseFeeBp":0}"""))             // interrupteurs absents : tout ouvert (le serveur juge)
        assertTrue(WalletReplies.parsePolicy("""{"rate":1000,"reverseFeeBp":0}""")!!.convert)
    }

    @Test fun historyReply() {
        val h = WalletReplies.parseHistory("""{"lines":[{"id":3,"kind":"CONVERT","currency":"NDEM","amount":-5000,"at":1,"label":"Conversion de jetons","counterparty":null}],"next":3}""")!!
        assertEquals(1, h.lines.size); assertEquals(3L, h.next)
        assertNull(WalletReplies.parseHistory("""{"lines":[],"next":null}""")!!.next)
        assertNull(WalletReplies.parseHistory("""{"nolines":1}"""))
        // une ligne illisible est ignorée, pas la page
        assertEquals(1, WalletReplies.parseHistory("""{"lines":[{"id":"x"},{"id":4,"kind":"GRANT","currency":"NDEM","amount":1,"at":2,"label":"Attribution mensuelle"}],"next":null}""")!!.lines.size)
    }

    @Test fun convertAndTransferAndReceiveReplies() {
        val c = WalletReplies.parseConvert("""{"dir":"M2N","q":5,"rate":1000,"reverseFeeBp":200,"ndemGross":5000,"fee":100,"ndemNet":4900,"replayed":false,"snapshot":"cbw1.A.B"}""")!!
        assertEquals(ConvertDone("M2N", 5, 1000, 200, 5000, 100, 4900, false, "cbw1.A.B"), c)
        val t = WalletReplies.parseTransfer("""{"replayed":true,"cur":"NDEM","amt":50,"to":"TV …4F2Q","snapshot":"cbw1.A.B"}""")!!
        assertEquals(TransferDone("NDEM", 50, "TV …4F2Q", true, "cbw1.A.B"), t)
        assertEquals(ReceiveCodeView("R123-4567-89", 1_790_000_900_000L), WalletReplies.parseReceive("""{"code":"R123-4567-89","exp":1790000900000}"""))
        assertNull(WalletReplies.parseReceive("""{"code":"","exp":1}"""))
        assertNull(WalletReplies.parseReceive("""{"code":"R123-4567-89"}"""))
        assertNull(WalletReplies.parseConvert("""{"dir":"M2N"}"""))
    }

    @Test fun failureReadsTheClosedReasonAndTheFrenchMessage() {
        val f = WalletReplies.parseFailure(409, """{"status":409,"erreur":"Conflit","message":"Solde insuffisant : 120 NDEM disponibles","details":["INSUFFICIENT"],"chemin":"/x","date":"d"}""")
        assertEquals(ApiFailure(409, "INSUFFICIENT", "Solde insuffisant : 120 NDEM disponibles"), f)
        assertEquals(ApiFailure(404, null, "Introuvable"), WalletReplies.parseFailure(404, """{"status":404,"message":"Introuvable","details":[]}"""))
        assertEquals(ApiFailure(502, null, null), WalletReplies.parseFailure(502, "<html>Bad gateway</html>"))
        assertEquals(ApiFailure(500, null, null), WalletReplies.parseFailure(500, ""))
        assertEquals(ApiFailure(409, null, null), WalletReplies.parseFailure(409, """{"details":[12]}"""))
        assertEquals("NEW_ONE", WalletReplies.parseFailure(409, """{"details":["NEW_ONE","X"]}""").reason)
    }
}
