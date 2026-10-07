package castbridge.core.connect

import castbridge.core.FakeCastBridge
import castbridge.core.device.DeviceFacts
import castbridge.core.quiz.CachedQuestionSource
import castbridge.core.quiz.QuizPackManager
import castbridge.core.quiz.QuizSync
import castbridge.core.telemetry.EventQueue
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * relay-R1 (REL-F7) : tant que la TV n'a Internet que par le téléphone ET que ce tuyau est précieux (une partie en cours, ou un téléphone sur données mobiles), ses tâches de FOND
 * volumineuses (mise à jour de l'application, questions, lots de questions) attendent. Le battement de cœur (quelques centaines d'octets) continue, et une action de l'utilisateur
 * (« Vérifier maintenant ») n'est jamais retenue.
 */
class ServerLinkBulkGateTest {
    private val dir = kotlin.io.path.createTempDirectory("bulkgate").toFile()
    private var now = 1_790_000_000_000L
    private var allowed = true
    private var packCalls = 0

    private fun link(srv: FakeCastBridge, quiz: QuizSync? = null, packs: Boolean = false): ServerLink {
        val state = ConnectState(MemoryKeyValueStore()).also { it.baseUrl = srv.base }
        return ServerLink(
            app = "tv", installed = ServerLink.Installed(7, "0.7", listOf("armeabi-v7a"), 34), state = state,
            facts = { object : DeviceFacts { override val app = "tv"; override val installId = "x"; override val versionCode = 7 } },
            salt = "castbridge-tv", routes = Routes(clock = { now }), queue = EventQueue(File(dir, "events.jsonl")), crashes = CrashStore(File(dir, "crashes")),
            keys = listOf(srv.publicKey),
            hooks = object : ServerLink.Hooks {
                override fun downloadDir(size: Long) = File(dir, "apk")
                override fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean) = true
                override fun backgroundBulkAllowed() = allowed
            },
            quiz = quiz,
            quizPacks = if (packs) QuizPackHook { _, _, _, _ -> packCalls++; QuizPackManager.Report("cm", 9, 9, emptyList(), emptyList(), null, "ok") } else null,
            clock = { now }, sleep = {},
        )
    }

    @Test fun theDefaultHookAllowsEverythingAsBefore() {
        val h = object : ServerLink.Hooks {
            override fun downloadDir(size: Long) = null
            override fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean) = true
        }
        assertTrue(h.backgroundBulkAllowed())
    }

    @Test fun aClosedGateKeepsTheHeartbeatButDefersTheUpdateAndTheQuizBackgroundWork() = FakeCastBridge().use { srv ->
        val source = CachedQuestionSource(File(dir, "quiz/cache.json"))
        val l = link(srv, quiz = QuizSync(source, File(dir, "quiz/cache.json"), pageSize = 2), packs = true)
        l.setConsent(usage = false)
        srv.latestVersion = 9
        for (i in 1..3) srv.putQuestion(srv.question("srv-$i", "Question serveur numéro $i ?"))
        allowed = false
        l.onStartup(); l.tick(startup = true)
        assertTrue(srv.log.contains("heartbeat") || srv.log.contains("register"), "le petit appel de vie continue : ${srv.log}")
        assertTrue(srv.log.none { it.startsWith("latest") || it == "dl" || it.startsWith("quiz") }, "rien de volumineux : ${srv.log}")
        assertEquals(0, packCalls, "pas de lots de questions non plus")
        assertEquals(ServerLink.Phase.IDLE, l.update.phase)
    }

    @Test fun whenTheGateOpensAgainTheDeferredWorkRunsAndNothingWasConsumedMeanwhile() = FakeCastBridge().use { srv ->
        val source = CachedQuestionSource(File(dir, "quiz/cache.json"))
        val l = link(srv, quiz = QuizSync(source, File(dir, "quiz/cache.json"), pageSize = 2), packs = true)
        l.setConsent(usage = false)
        srv.latestVersion = 9
        srv.putQuestion(srv.question("srv-1", "Question serveur numéro 1 ?"))
        allowed = false
        l.onStartup(); l.tick(startup = true)
        now += 60_000; l.tick()
        assertTrue(srv.log.none { it.startsWith("latest") }, srv.log.toString())
        allowed = true
        now += 60_000; l.tick()
        assertTrue(srv.log.any { it.startsWith("latest") }, "la mise à jour attendait : ${srv.log}")
        assertTrue(srv.log.any { it.startsWith("quiz") }, "les questions aussi")
        assertEquals(1, packCalls)
    }

    @Test fun anExplicitCheckByTheUserIsNeverHeldBack() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = false)
        srv.latestVersion = 9
        allowed = false
        l.tick(startup = true)
        assertFalse(srv.log.any { it.startsWith("latest") })
        now += 20_000
        l.checkUpdate(UpdateSchedule.Trigger.USER)
        assertTrue(srv.log.any { it.startsWith("latest") }, "« Vérifier maintenant » : l'utilisateur l'a demandé, il a sa réponse")
    }

    @Test fun theServersCheckNowDirectiveWaitsForTheGateToo() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = false)
        srv.latestVersion = 9; srv.checkUpdateOnce = true
        allowed = false
        l.tick(startup = true)
        assertTrue(srv.log.none { it.startsWith("latest") }, srv.log.toString())
        allowed = true
        now += 60_000; l.tick()
        assertTrue(srv.log.any { it.startsWith("latest") }, "la demande du serveur (checkUpdate) n'est pas perdue : ${srv.log}")
    }
}
