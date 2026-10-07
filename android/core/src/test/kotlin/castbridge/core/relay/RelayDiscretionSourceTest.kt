package castbridge.core.relay

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Discrétion du relais (DESIGN-RELAIS § 2.6) : « aucun nom de fichier ni identifiant dans les journaux ». Un test de SOURCE sur les fichiers que relay-R1 a écrits ou réécrits :
 * aucune ligne de journal ne cite un nom de TV, une adresse, un code, un jeton ni une clé, aucun journal ne reçoit une exception entière (son message peut citer un chemin ou une adresse),
 * et le côté téléphone de la passerelle (`Exit`) n'écrit jamais où il se connecte. (Les journaux plus anciens du téléphone et de la TV, dont l'entrée `Entry` de la passerelle, sont l'objet de relay-R3.)
 */
class RelayDiscretionSourceTest {
    private val files = listOf(
        "sender/src/main/kotlin/castbridge/sender/RelayRuntime.kt", "sender/src/main/kotlin/castbridge/sender/RelaySettings.kt", "sender/src/main/kotlin/castbridge/sender/BtGatewayService.kt",
        "receiver/src/main/kotlin/castbridge/receiver/TvNet.kt",
    )
    private fun text(rel: String): String = File("../$rel").also { assertTrue(it.isFile, "source introuvable depuis ${File(".").absolutePath} : $rel") }.readText()

    private val log = Regex("""\bLog\.[diwev]\(""")
    private val identifier = Regex("""\$\{?\s*(address|addr|name|tvName|tv\.name|tv\.address|r\.name|r\.address|credential|pin|manualPin|token|peer|phoneName)\b""")

    @Test fun noLogLineCarriesANameAnAddressOrACredential() {
        var seen = 0
        for (f in files) text(f).lines().forEachIndexed { i, l ->
            if (log.containsMatchIn(l) && !l.trim().startsWith("//") && !l.trim().startsWith("*")) {
                seen++
                assertFalse(identifier.containsMatchIn(l), "$f:${i + 1} : une ligne de journal cite un nom, une adresse ou un secret : $l")
            }
        }
        assertTrue(seen >= 5, "le test ne voit plus les journaux ($seen) : l'expression a cessé de les reconnaître")
    }

    @Test fun noLogReceivesAWholeException() {
        // Log.w(TAG, "message", e) écrit la trace et le message de l'exception (chemin, adresse) : seul le nom de la classe est écrit
        val withThrowable = Regex("""\bLog\.[diwev]\([^"]*"[^"]*"\s*,\s*[a-z]+\)""")
        for (f in files) text(f).lines().forEachIndexed { i, l ->
            assertFalse(withThrowable.containsMatchIn(l) && !l.contains("simpleName"), "$f:${i + 1} : une exception entière dans un journal : $l")
        }
    }

    @Test fun nothingPrintsToTheConsole() {
        for (f in files) {
            val src = text(f)
            for (bad in listOf("println(", "System.out", "System.err", "printStackTrace")) assertFalse(src.contains(bad), "$f : « $bad »")
        }
    }

    @Test fun thePhoneSideOfTheGatewayNeverLogsWhereItConnects() {
        val gw = text("core/src/main/kotlin/castbridge/core/gateway/BtGateway.kt")
        val exit = gw.substringAfter("class Exit(").substringBefore("class Entry(")
        val calls = exit.lines().filter { Regex("""\blog\(""").containsMatchIn(it) }
        assertTrue(calls.isNotEmpty(), "Exit ne journalise plus : le test ne voit plus rien")
        for (l in calls) assertFalse(l.contains("$"), "Exit : une ligne de journal interpole une valeur (hôte, port, adresse) : $l")
    }

    @Test fun theNotificationNamesNoFileAndTheIntentCarriesNoCredential() {
        val svc = text("sender/src/main/kotlin/castbridge/sender/BtGatewayService.kt")
        // l'identifiant d'une TV synchronisée n'est jamais mis dans l'Intent du démarrage automatique : le service le lit lui-même
        val startAuto = svc.substringAfter("fun startAuto(").substringBefore("fun stop(")
        assertTrue(startAuto.contains("putExtra"), "startAuto n'a pas été trouvé : le test ne voit plus rien")
        assertFalse(startAuto.contains("EXTRA_PIN") || startAuto.contains("credential"), "startAuto ne porte aucun identifiant")
        assertTrue(svc.contains("RelayText.notification("), "la notification vient du texte neutre du cœur")
        assertTrue(svc.contains("VISIBILITY_PRIVATE"), "privée sur l'écran verrouillé")
    }
}
