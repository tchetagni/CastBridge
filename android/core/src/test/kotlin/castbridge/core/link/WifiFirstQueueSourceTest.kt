package castbridge.core.link

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Garde de source du branchement Android de R-54 (« la reprise de la copie est lente bien que la disponibilité d'un réseau Wi-Fi soit effective ») : les règles pures
 * ([BulkRoute.waitForWifiMs], [BulkRoute.switchBtToWifi]) sont testées dans `AutoWifiDirectTest` ; la file (`TransferQueue`) n'a aucun test JVM. Ici : l'attente du Wi-Fi vient
 * APRÈS le choix du Wi-Fi Direct et AVANT la voie ; le retour au Wi-Fi est traité AVANT l'analyse d'échec ; il ne compte que les vraies routes Wi-Fi ; il n'annule jamais l'élément
 * de la file ; la phrase de la notification ne vaut que pour un envoi Bluetooth de la file. Un fichier absent fait ÉCHOUER le test.
 */
class WifiFirstQueueSourceTest {
    private val queue by lazy { PhoneSources.code("sender/src/main/kotlin/castbridge/sender/TransferQueue.kt") }
    private val bt by lazy { PhoneSources.code("sender/src/main/kotlin/castbridge/sender/BtUploadService.kt") }

    private fun at(code: String, needle: String): Int = code.indexOf(needle).also { assertTrue(it >= 0, "« $needle » introuvable") }

    @Test fun aBigFileWaitsForTheWifiAfterWifiDirectWasTriedAndBeforeTheRouteIsChosen() {
        val direct = at(queue, "AutoWifiDirect.bulkBase(app, session0, item.size)")
        val wait = at(queue, "if (wdBase == null && session0.base == null) waitForWifi(app, item, session0) else session0")
        val route = at(queue, "val base = wdBase ?: session.base")
        assertTrue(direct < wait && wait < route, "Wi-Fi Direct d'abord, puis l'attente du Wi-Fi, puis la voie (Bluetooth seulement si rien n'est venu)")
        val waiting = queue.substring(at(queue, "private suspend fun waitForWifi("), at(queue, "private suspend fun watch("))
        assertTrue("BulkRoute.waitForWifiMs(item.size, seenAgo)" in waiting, "la durée vient de la règle pure")
        assertTrue("paused || model.cancelAsked(item.id)" in waiting, "l'attente s'arrête si la file est en pause ou si le fichier est annulé")
        assertTrue("QueueTexts.WAITING_WIFI" in waiting && "delay(" in waiting, "la file dit qu'elle attend, et relit le lien toutes les secondes au plus")
    }

    @Test fun theReturnToWifiIsHandledBeforeAnyFailureAnalysisAndNeverCancelsTheItem() {
        val watched = at(queue, "val outcome = watch(app, viaBt, item.id, linkAddress, sendName)")
        val reroute = at(queue, "if (outcome == REROUTE_WIFI && !model.cancelAsked(item.id)) { model.release(item.id); publish(); delay(1_000); return }")
        val analysis = at(queue, "QueueOutcome.of(outcome")
        assertTrue(watched < reroute && reroute < analysis, "le marqueur est traité juste après l'attente de l'envoi, AVANT QueueOutcome.of (jamais un échec affiché)")
        val watch = queue.substring(at(queue, "private suspend fun watch("))
        assertTrue("BulkRoute.switchBtToWifi(total - sent, " in watch && "return REROUTE_WIFI" in watch, "watch décide par la règle pure et rend le marqueur")
        assertTrue(watch.indexOf("BtUploadService.cancel(app)") < watch.indexOf("return REROUTE_WIFI"), "le service Bluetooth est arrêté avant le retour du marqueur")
        assertTrue(watch.indexOf("if (wifiResumes(wifi, sendName, total))") in 0 until watch.indexOf("BtUploadService.cancel(app)"),
            "la TV est interrogée (GET /api/part?size=) AVANT d'arrêter le Bluetooth : une copie commencée en Bluetooth n'a pas de .meta, la TV ne la reprend pas en Wi-Fi")
        assertTrue("BulkRoute.wifiResumes(castbridge.core.tv.TvClient(base, s.credential).part(name, total))" in queue, "la question est celle de l'envoi Wi-Fi (nom et taille de la copie)")
        assertFalse("model.cancel(" in watch || "finishCancelled" in watch || "model.finish(" in watch, "arrêter l'envoi Bluetooth n'annule ni ne termine l'élément de la file")
        assertTrue("btReroutes.remove(item.id)" in queue && "BulkRoute.MAX_BT_REROUTES" in queue, "le compteur de bascules est borné et oublié à la fin du fichier")
    }

    @Test fun theLinkIsAskedToLookAgainOnlyWhenTheTvAnswersOnTheCommonNetwork() {
        val watch = queue.substring(at(queue, "private suspend fun watch("))
        val poke = at(watch, "TvLinkManager.poke()")
        assertTrue(watch.lastIndexOf("if (answers)", poke) >= 0 && poke - watch.lastIndexOf("if (answers)", poke) < 40, "le lien n'est réveillé que si la TV a répondu (jamais un HELLO Bluetooth forcé pour rien)")
        assertTrue(watch.lastIndexOf("wifi == null && started && linkAddress != null && now >= nextProbeAt", poke) >= 0, "seulement pendant un envoi Bluetooth sans route Wi-Fi, et au plus toutes les 5 s (15 s après une réponse)")
        assertTrue("HelloIps.lanOnly(link.ips).any { TvLinkManager.reachable(" in queue, "jamais l'adresse d'un groupe Wi-Fi Direct")
    }

    @Test fun onlyARealWifiRouteCountsNotTheBluetoothTunnel() {
        val own = queue.substring(at(queue, "private fun goesOverWifi("), at(queue, "private fun noteWifi("))
        assertTrue("Route.Lan" in own && "Route.Direct" in own, "réseau commun ou groupe Wi-Fi Direct")
        assertFalse("base" in own, "le tunnel Bluetooth a une adresse `base` mais n'est pas du Wi-Fi")
        assertTrue("lastWifiRouteAt = AutoWifiDirect.clock()" in queue, "l'instant de la dernière route Wi-Fi vue est gardé (horloge monotone)")
    }

    @Test fun theNotificationPromisesTheWifiOnlyForASendOfTheQueue() {
        assertTrue("val hint = if (allowWd) null else castbridge.core.tv.QueueTexts.BT_BACK_TO_WIFI" in bt, "canal Bluetooth manuel (TvHub) : aucune promesse, la file ne le surveille pas")
        assertTrue("notification(\"Envoi Bluetooth de ${'$'}name\", pct, hint)" in bt, "la phrase est posée sur la notification de l'envoi Bluetooth")
    }
}
