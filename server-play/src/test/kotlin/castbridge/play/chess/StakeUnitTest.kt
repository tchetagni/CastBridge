package castbridge.play.chess

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.StakeSpec
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.stake.EscrowGate
import castbridge.play.stake.ResultKey
import castbridge.play.stake.ResultSpool
import castbridge.play.stake.WalletKeys
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La porte des blocages, la clé « résultat », le dépôt des résultats, la configuration et les capacités annoncées. */
class StakeUnitTest {
    private val id = TestRights.CODE
    private val spec = StakeSpec("NDEM", 20)
    private val now = System.currentTimeMillis()

    // ------------------------------------------------------------------ EscrowGate

    @Test fun theGateIsClosedWithoutAKeyAndOpenWithARawOrSpkiKey() {
        assertFalse(EscrowGate(emptyList()).configured)
        assertFalse(EscrowGate(listOf("pas une clé", "")).configured)
        val raw = StakeKit.walletPub
        val spki = Base64.getEncoder().encodeToString(byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + Base64.getDecoder().decode(raw))
        for (k in listOf(raw, spki)) {
            val g = EscrowGate(listOf(k)); assertTrue(g.configured, k)
            assertTrue(g.check(StakeKit.escrow(id), now, id, spec) is EscrowGate.Result.Ok, "clé $k")
        }
        assertEquals(EscrowGate.Why.NO_KEY, (EscrowGate(emptyList()).check(StakeKit.escrow(id), now, id, spec) as EscrowGate.Result.Refused).why)
        assertEquals(raw, WalletKeys.rawBase64(spki)); assertNull(WalletKeys.rawBase64("AAAA")); assertNull(WalletKeys.rawBase64("!!!!"))
    }

    @Test fun aValidEscrowGivesTheIdentityTheEscrowIdAndTheAmount() {
        val token = StakeKit.escrow(id, "MBOKO", 5)
        val r = EscrowGate(listOf(StakeKit.walletPub)).check(token, now, id, StakeSpec("MBOKO", 5)) as EscrowGate.Result.Ok
        assertEquals(castbridge.core.chess.online.ChessEscrow(StakeKit.eidOf(token), id, 5), r.escrow())
        assertEquals(1, r.ticket.k)
    }

    @Test fun everyRefusalHasItsOwnReason() {
        val g = EscrowGate(listOf(StakeKit.walletPub), maxNdem = 100)
        fun why(token: String?, who: String = id, s: StakeSpec = spec) = (g.check(token, now, who, s) as EscrowGate.Result.Refused).why
        assertEquals(EscrowGate.Why.UNKNOWN_KEY, why(StakeKit.escrow(id, signer = StakeKit.otherWalletSigner)), "une clé qui n'est pas dans le jeu de clés")
        val good = StakeKit.escrow(id); val sig = good.substringAfterLast('.')
        val forged = good.substringBeforeLast('.') + "." + sig.substring(0, 40) + (if (sig[40] == 'A') 'B' else 'A') + sig.substring(41)
        assertEquals(EscrowGate.Why.BAD_SIGNATURE, why(forged), "la bonne clé, une signature altérée")
        assertEquals(EscrowGate.Why.EXPIRED, why(StakeKit.escrow(id, now = now - 7_200_000, lifeMs = 60_000)))
        assertEquals(EscrowGate.Why.NOT_YET_VALID, why(StakeKit.escrow(id, iat = now + 600_000)))
        assertEquals(EscrowGate.Why.UNREADABLE, why("cbe1.xxx.yyy")); assertEquals(EscrowGate.Why.UNREADABLE, why(null)); assertEquals(EscrowGate.Why.UNREADABLE, why("v1." + "a".repeat(10) + ".b"))
        assertEquals(EscrowGate.Why.OTHER_TV, why(StakeKit.escrow("ZZZZ-ZZZZ-ZZZZ-ZZZZ")))
        assertEquals(EscrowGate.Why.OTHER_STAKE, why(StakeKit.escrow(id, cur = "MBOKO", per = 20)))
        assertEquals(EscrowGate.Why.OTHER_STAKE, why(StakeKit.escrow(id, per = 50)))
        assertEquals(EscrowGate.Why.BAD_SEATS, why(StakeKit.escrow(id, k = 2)))
        assertEquals(EscrowGate.Why.BAD_AMOUNT, why(StakeKit.escrow(id, per = 500), s = StakeSpec("NDEM", 500)), "au-dessus des bornes du service")
        assertEquals(EscrowGate.Why.OTHER, why(StakeKit.escrow(id, aud = "x")))
    }

    @Test fun aRotatedKeySetAcceptsBothKeys() {
        val g = EscrowGate(listOf(StakeKit.walletPub, StakeKit.otherWalletSigner.publicKeyBase64))
        assertTrue(g.check(StakeKit.escrow(id), now, id, spec) is EscrowGate.Result.Ok)
        assertTrue(g.check(StakeKit.escrow(id, signer = StakeKit.otherWalletSigner), now, id, spec) is EscrowGate.Result.Ok)
    }

    @Test fun anEscrowServesOneRoomAndIsReleasedOnlyOnPurpose() {
        val g = EscrowGate(listOf(StakeKit.walletPub))
        assertTrue(g.reserve("EID1", now + 60_000, now))
        assertFalse(g.reserve("EID1", now + 60_000, now), "déjà employé")
        g.release("EID1")
        assertTrue(g.reserve("EID1", now + 60_000, now), "rendu : réutilisable")
        // un blocage échu reste mémorisé un délai de grâce (la salle qui l'a employé peut durer), puis il part
        assertFalse(g.reserve("EID1", now + 60_000, now + 3_600_000))
        assertTrue(g.reserve("EID1", now + 60_000, now + 60_000 + EscrowGate.GRACE_MS + 1))
        assertEquals(1, g.reservedCount())
    }

    @Test fun theReservationTableIsBoundedAndNeverEvictsAValidEscrow() {
        val g = EscrowGate(listOf(StakeKit.walletPub), maxReserved = 3)
        for (i in 1..3) assertTrue(g.reserve("E$i", now + 60_000, now))
        assertFalse(g.reserve("E4", now + 60_000, now), "pleine : refus, jamais d'éviction d'un blocage valable")
        assertFalse(g.reserve("E1", now + 60_000, now))
    }

    // ------------------------------------------------------------------ ResultKey

    private val seed = MessageDigest.getInstance("SHA-256").digest("castbridge-result-key-file-test".toByteArray())
    private val expected = Ed25519Signer(seed)
    private fun file(text: String) = File.createTempFile("result-key", ".key").also { it.writeText(text); it.deleteOnExit() }
    private val pkcs8 = byteArrayOf(0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20) + seed

    @Test fun theResultKeyReadsASeedAPkcs8AndAPemAndNothingElse() {
        val formats = listOf(
            Base64.getEncoder().encodeToString(seed),
            Base64.getEncoder().encodeToString(pkcs8),
            "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pkcs8).chunked(64).joinToString("\n") + "\n-----END PRIVATE KEY-----\n",
        )
        for (f in formats) assertEquals(expected.keyId, ResultKey.load(file(f))?.keyId, f.take(24))
        for (bad in listOf("", "pas du base64 !!", Base64.getEncoder().encodeToString(ByteArray(40)), Base64.getEncoder().encodeToString(ByteArray(33)))) assertNull(ResultKey.load(file(bad)), bad.take(12))
        assertNull(ResultKey.load(null)); assertNull(ResultKey.load(File("/nonexistent/result.key"))); assertNull(ResultKey.load(Files.createTempDirectory("dir").toFile()))
        // la clé signe un résultat que la clé publique relit (le contrat avec l'API)
        val signer = ResultKey.load(file(formats[1]))!!
        val ring = KeyRing(listOf(signer.trusted()))
        val pr = castbridge.core.chess.online.ChessSettlement.result(signer.keyId, "room-1", castbridge.core.wallet.WalletCurrency.NDEM, 20, 1L,
            castbridge.core.chess.online.ChessEscrow("EidAAAAAAAAAAAAAAAAAAA", "AAAA-AAAA-AAAA-AAAA", 20), null, null)
        assertTrue(castbridge.core.wallet.PlayResult.verify(castbridge.core.wallet.PlayResult.sign(pr, signer), ring) is castbridge.core.wallet.Verdict.Accepted)
    }

    // ------------------------------------------------------------------ ResultSpool

    private val rid = "0123456789abcdef0123456789abcdef"

    @Test fun theSpoolWritesAtomicallyUnderTheResultId() {
        val dir = StakeKit.tempDir()
        val s = ResultSpool(dir)
        assertTrue(s.active)
        assertTrue(s.write(rid, "cbr1.abc.def"))
        assertEquals(listOf(rid), s.list()); assertEquals("cbr1.abc.def", s.read(rid))
        assertEquals("cbr1.abc.def\n", File(dir, "$rid.cbr1").readText())
        assertEquals(listOf("$rid.cbr1"), dir.list()!!.sorted(), "aucun fichier temporaire ne reste")
        // rejeu : même nom, même contenu (écrasement atomique)
        assertTrue(s.write(rid, "cbr1.abc.def")); assertEquals(1, s.list().size)
        assertTrue(File(dir, "$rid.cbr1").length() < 9 * 1024, "le collecteur ne garde que les fichiers de moins de 9 Ko")
    }

    @Test fun theSpoolRefusesBadNamesAndBadTokensAndNeverThrows() {
        val dir = StakeKit.tempDir()
        val s = ResultSpool(dir)
        for (bad in listOf("../../etc/passwd", "ABCDEF", rid.uppercase(), rid + "0", "")) assertFalse(s.write(bad, "cbr1.a.b"), bad)
        assertFalse(s.write(rid, "cbr1 avec espace")); assertFalse(s.write(rid, "é")); assertFalse(s.write(rid, "x".repeat(ResultSpool.MAX_TOKEN + 1)))
        assertTrue(dir.list()!!.isEmpty())
        assertNull(s.read("../x")); assertNull(s.read(rid))
        assertFalse(ResultSpool(null).write(rid, "cbr1.a.b")); assertFalse(ResultSpool(null).active)
        val file = File.createTempFile("not-a-dir", "x").also { it.deleteOnExit() }
        assertFalse(ResultSpool(File(file, "sub")).write(rid, "cbr1.a.b"), "un dossier impossible : faux, pas d'exception")
    }

    @Test fun theSpoolForgetsOldFilesAndKeepsAtMostMaxFiles() {
        val dir = StakeKit.tempDir()
        var t = System.currentTimeMillis()
        val s = ResultSpool(dir, maxFiles = 3, maxAgeMs = 7L * 24 * 3_600_000) { t }
        val ids = (1..5).map { "%032x".format(it) }
        ids.forEachIndexed { i, r -> assertTrue(s.write(r, "cbr1.a.b")); File(dir, "$r.cbr1").setLastModified(t - (5 - i) * 1_000L) }
        t += 120_000; s.purgeIfDue()
        assertEquals(ids.takeLast(3), s.list(), "les plus anciens partent au-delà de maxFiles")
        File(dir, "${ids.last()}.cbr1").setLastModified(t - 8L * 24 * 3_600_000)
        t += 120_000; s.purgeIfDue()
        assertEquals(ids.dropLast(1).takeLast(2), s.list(), "plus de 7 jours : retiré")
    }

    // ------------------------------------------------------------------ configuration et capacités

    @Test fun theConfigurationReadsTheStakeVariablesAndRefusesNonsense() {
        fun cfg(vararg kv: Pair<String, String>) = PlayConfig.fromEnv({ k -> kv.toMap()[k] ?: if (k == "CASTBRIDGE_PLAY_DIRECT") "1" else null })
        val d = cfg()
        assertTrue(d.chess && d.stakes && d.walletPubKeys.isEmpty() && d.resultKeyFile == null)
        assertEquals(File(PlayConfig.DEFAULT_RESULTS_DIR), d.resultsDir); assertEquals(1_000L, d.stakeMaxNdem); assertEquals(100L, d.stakeMaxMboko)
        val c = cfg("CASTBRIDGE_PLAY_CHESS" to "off", "CASTBRIDGE_PLAY_STAKES" to "0", "CASTBRIDGE_PLAY_WALLET_PUBKEY" to "AAA", "CASTBRIDGE_PLAY_WALLET_PUBKEY_2" to "BBB",
            "CASTBRIDGE_PLAY_RESULT_KEY_FILE" to "/run/k", "CASTBRIDGE_PLAY_RESULTS_DIR" to "/r", "CASTBRIDGE_PLAY_STAKE_MAX_NDEM" to "500", "CASTBRIDGE_PLAY_STAKE_MAX_MBOKO" to "50")
        assertFalse(c.chess); assertFalse(c.stakes); assertEquals(listOf("AAA", "BBB"), c.walletPubKeys)
        assertEquals(File("/run/k"), c.resultKeyFile); assertEquals(File("/r"), c.resultsDir); assertEquals(500L, c.stakeMaxNdem); assertEquals(50L, c.stakeMaxMboko)
        assertTrue(runCatching { cfg("CASTBRIDGE_PLAY_STAKES" to "peut-être") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { cfg("CASTBRIDGE_PLAY_CHESS" to "2") }.exceptionOrNull() is IllegalStateException)
    }

    @Test fun theServiceHoldsOnePrivateKeyAndNoLedgerAddress() {
        val names = PlayConfig.ENV_NAMES
        assertEquals(listOf("CASTBRIDGE_PLAY_RESULT_KEY_FILE"), names.filter { Regex("(PRIVATE|SECRET|PASS|TOKEN)|_KEY(_FILE)?$").containsMatchIn(it) && !it.contains("PUBKEY") && !it.contains("TRUSTED_KEYS") },
            "une seule variable porte une clé privée : la clé « résultat » (le service ne signe que cbr1)")
        assertFalse(names.any { Regex("WALLET").containsMatchIn(it) && Regex("URL|API|HOST|ADDR|DB|JDBC|ADMIN").containsMatchIn(it) }, "aucune adresse ni identifiant de l'API portefeuille")
        val src = File("src/main/kotlin/castbridge/play").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(Regex("/api/v1/wallet|wallet/escrow|wallet/settle|/api/v1/admin").containsMatchIn(src), "aucune route du portefeuille dans le service")
        val stakeSrc = File("src/main/kotlin/castbridge/play/stake").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(Regex("java\\.net|HttpURLConnection|Socket|ProcessBuilder|getenv").containsMatchIn(stakeSrc), "le code des mises n'ouvre aucune connexion : il vérifie des signatures et écrit un fichier")
    }

    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() } }
    private val http = HttpClient.newHttpClient()
    private fun get(srv: PlayServer, path: String) = http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}$path")).GET().build(), HttpResponse.BodyHandlers.ofString())
    private fun server(vararg more: Pair<String, Any?>): PlayServer {
        val m = more.toMap()
        val cfg = PlayConfig(requireProof = false, webPlay = false, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
            chess = m["chess"] as Boolean? ?: true, stakes = m["stakes"] as Boolean? ?: true, walletPubKeys = m["wallet"] as List<String>? ?: emptyList(), resultKeyFile = m["key"] as File?, resultsDir = StakeKit.tempDir())
        return PlayServer(cfg).also { it.start(); servers += it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun caps(srv: PlayServer) = Json.parse(get(srv, "/play/.well-known/caps").body()) as Map<String, Any?>

    @Test fun capsAdvertiseChessAndStakesOnlyWhenTheServiceCanReallyServeThem() {
        val plain = caps(server())
        assertEquals(true, plain["chess"]); assertEquals(false, plain["stakes"], "sans clés : pas de mises") ; assertEquals(PlayProtocol.CAPS, plain["caps"]); assertEquals(listOf("chess"), plain["games"])
        assertEquals(false, plain["quizStakes"], "sans clés : pas de Quiz misé non plus (la TV ne propose que « Libre »)")
        val keyFile = file(Base64.getEncoder().encodeToString(seed))
        val staked = caps(server("wallet" to listOf(StakeKit.walletPub), "key" to keyFile))
        assertEquals(true, staked["stakes"]); assertEquals(PlayProtocol.CAPS + PlayProtocol.CAP_STAKES + PlayProtocol.CAP_QUIZ_STAKES, staked["caps"])
        assertEquals(true, staked["quizStakes"], "games-G5 : le service arbitre aussi un Quiz misé, et le dit")
        val noSwitch = caps(server("wallet" to listOf(StakeKit.walletPub), "key" to keyFile, "stakes" to false))
        assertEquals(false, noSwitch["stakes"], "interrupteur d'exploitation"); assertEquals(false, noSwitch["quizStakes"], "le même interrupteur coupe le Quiz misé")
        assertEquals(false, caps(server("wallet" to listOf(StakeKit.walletPub)))["stakes"], "clé « résultat » absente")
        assertEquals(false, caps(server("key" to keyFile))["stakes"], "clé publique du portefeuille absente")
        assertEquals(false, caps(server("wallet" to listOf(StakeKit.walletPub)))["quizStakes"], "clé « résultat » absente : pas de Quiz misé")
        val off = caps(server("chess" to false, "wallet" to listOf(StakeKit.walletPub), "key" to keyFile))
        assertEquals(false, off["chess"]); assertEquals(false, off["stakes"]); assertEquals(emptyList<String>(), off["games"])
        assertEquals(true, off["quizStakes"], "les échecs coupés ne coupent pas le Quiz misé : ce sont deux jeux")
        assertEquals(PlayProtocol.CAPS.filter { it != "chess" } + PlayProtocol.CAP_QUIZ_STAKES, off["caps"])
        val health = Json.parse(get(server(), "/play/health").body()) as Map<*, *>
        assertEquals(true, health["chess"]); assertEquals(0, (health["chessRooms"] as Number).toInt()); assertNotNull(health["stakes"])
        assertEquals(false, health["quizStakes"]); assertEquals(0, (health["stakedRooms"] as Number).toInt())
    }

    @Test fun anEscrowCoversOneSeatForChessAndUpToEightForTheQuiz() {
        val g = EscrowGate(listOf(StakeKit.walletPub))
        fun why(token: String, maxSeats: Int) = (g.check(token, now, id, spec, maxSeats) as? EscrowGate.Result.Refused)?.why
        assertEquals(EscrowGate.Why.BAD_SEATS, why(StakeKit.escrow(id, k = 2), 1), "aux échecs : une mise par TV")
        for (k in 1..8) assertNull(why(StakeKit.escrow(id, k = k), 8), "$k sièges au Quiz")
        val ok = g.check(StakeKit.escrow(id, k = 3), now, id, spec, 8) as EscrowGate.Result.Ok
        assertEquals(castbridge.core.quiz.online.QuizEscrow(ok.ticket.eid, id, 3, 60), ok.quizEscrow(), "le blocage du Quiz porte ses sièges et son montant (mise × sièges)")
        assertEquals(EscrowGate.Why.OTHER, why(StakeKit.escrow(id, k = 9, amt = 180), 8), "neuf sièges : refusé dès la lecture du blocage")
        assertEquals(EscrowGate.Why.OTHER, why(StakeKit.escrow(id, k = 0, amt = 0), 8))
        assertEquals(EscrowGate.Why.BAD_SEATS, why(StakeKit.escrow(id, k = 5), 4), "au-dessus de ce que la salle permet")
    }
}
