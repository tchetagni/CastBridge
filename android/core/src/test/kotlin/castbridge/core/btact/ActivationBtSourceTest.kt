package castbridge.core.btact

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * « Activer par Bluetooth sans appairage » : ce que les tests JVM ne peuvent pas rejouer (le code Android : annonce BLE, sockets, balayage) est tenu par des gardes de SOURCE, qui échouent si un fichier
 * manque (jamais « au vert à vide ») :
 *  - AUCUNE boîte d'appairage : seules les variantes « insecure » des sockets, jamais `createBond`, jamais la socket sécurisée sur ce service ;
 *  - rien de secret dans le journal : ni le code de connexion, ni le tag, ni une adresse Bluetooth, ni la clé, ni la demande ;
 *  - la TV partage UNE porte de tentatives avec sa route HTTP, et rend l'annonce avec l'écran (grâce de 20 s du groupe) ;
 *  - l'annonce ne porte ni le nom de la TV ni le code, seulement l'UUID du service et la charge de [BtActAd].
 */
class ActivationBtSourceTest {
    private val host = "receiver/src/main/kotlin/castbridge/receiver/ActivationBtHost.kt"
    private val client = "sender/src/main/kotlin/castbridge/sender/BtActivationClient.kt"
    private val service = "receiver/src/main/kotlin/castbridge/receiver/TvService.kt"
    private val screen = "receiver/src/main/kotlin/castbridge/receiver/ActivationActivity.kt"
    private val driver = "sender/src/main/kotlin/castbridge/sender/ActivationDriver.kt"

    private fun logLines(rel: String) = PhoneSources.code(rel).lines().filter { "Log." in it }

    @Test fun noPairingBoxAnywhereOnThisService() {
        for (rel in listOf(host, client)) {
            val code = PhoneSources.code(rel)
            assertFalse(Regex("""\bcreateBond\(|ACTION_PAIRING_REQUEST|setPairingConfirmation|ACTION_REQUEST_DISCOVERABLE|setPin\(""").containsMatchIn(code), "$rel : aucune boîte d'appairage ni de visibilité")
            assertFalse(Regex("""\blistenUsingRfcommWithServiceRecord\(|\bcreateRfcommSocketToServiceRecord\(|\blistenUsingL2capChannel\(|\bcreateL2capChannel\(""").containsMatchIn(code),
                "$rel : les sockets SÉCURISÉES demandent l'appairage : seules les variantes « insecure » servent ici")
        }
        assertTrue("listenUsingInsecureRfcommWithServiceRecord(" in PhoneSources.code(host), "la TV écoute en RFCOMM insecure")
        assertTrue("listenUsingInsecureL2capChannel()" in PhoneSources.code(host), "et en L2CAP insecure (Android 10 et plus)")
        assertTrue("createInsecureRfcommSocketToServiceRecord(" in PhoneSources.code(client) && "createInsecureL2capChannel(" in PhoneSources.code(client), "le téléphone ouvre les deux canaux insecure")
    }

    @Test fun theServiceIsNamedAndNumberedOnlyThroughTheTable() {
        val hostCode = PhoneSources.code(host); val clientCode = PhoneSources.code(client)
        assertTrue("BtProtocol.ACTIVATION_SERVICE_UUID" in hostCode && "BtProtocol.ACTIVATION_SERVICE_UUID" in clientCode)
        assertTrue("BtActAd.SERVICE_UUID" in hostCode && "BtActAd.SERVICE_UUID" in clientCode, "l'UUID annoncé et filtré est celui de la table")
        assertTrue("\"CastBridge Activation\"" in hostCode, "le nom SDP de la table")
        for (code in listOf(hostCode, clientCode)) assertFalse(Regex("7c5e3b9a-4d2f-4c61-9b0e-cb00000000").containsMatchIn(code), "aucun UUID de service écrit hors de la table")
    }

    @Test fun nothingSecretReachesTheJournalFromTheTvOrThePhone() {
        val hostLogs = logLines(host)
        assertTrue(hostLogs.size >= 5, "l'hôte dit ce qu'il fait : ${hostLogs.size} lignes")
        for (l in hostLogs) {
            for (bad in listOf("code()", "lockedPin", "peer", "address", "payload", "tag(", "BtActAd.", "remoteDevice", "request", "key")) assertFalse(bad in l, "« $bad » dans une ligne de journal de la TV : $l")
            // an interpolation is a boolean, the class name of an exception, a word of this file or the end word of a session (BtActServer.End): nothing else
            for (m in Regex("""\$(\{[^}]*}|[A-Za-z_][A-Za-z0-9_]*)""").findAll(l)) {
                val expr = m.groupValues[1].removePrefix("{").removeSuffix("}")
                assertTrue(expr in setOf("e.javaClass.simpleName", "psm != 0", "rfcomm != null", "errorCode", "end", "why"), "interpolation inattendue « $expr » dans : $l")
            }
        }
        assertEquals(emptyList(), logLines(client), "le client du téléphone n'écrit AUCUNE ligne de journal (il voit le code, la clé et la demande)")
    }

    @Test fun theAdvertisementCarriesOnlyTheServiceUuidAndTheAdPayload() {
        val code = PhoneSources.code(host)
        assertTrue("setIncludeDeviceName(false)" in code, "le nom de la TV n'est pas annoncé")
        assertEquals(2, Regex("""setIncludeDeviceName\(false\)""").findAll(code).count(), "ni dans l'annonce, ni dans la réponse de balayage de repli")
        assertTrue("addServiceUuid(ParcelUuid(UUID.fromString(BtActAd.SERVICE_UUID)))" in code)
        assertTrue("val record = BtActAd.payload(c, psm)" in code, "le tag du code et le PSM, par l'encodage testé")
        assertEquals(2, Regex("""addManufacturerData\(BtActAd\.COMPANY_ID, record\)""").findAll(code).count(), "l'enregistrement : dans l'annonce, ou (repli) dans la réponse de balayage, jamais ailleurs")
        assertTrue("ADVERTISE_FAILED_DATA_TOO_LARGE" in code && "recordInScanResponse" in code, "repli sur la réponse de balayage quand le boîtier refuse les 30 octets")
        assertTrue("bluetoothLeAdvertiser" in code && "cette TV ne sait pas annoncer en BLE" in code, "une TV sans annonceur BLE n'a pas la voie : elle n'est pas listée")
        assertFalse(Regex("""\bBtActAd\.tag\(|\bwdCode|WdCode\.""").containsMatchIn(code), "ni le tag à la main ni le code dérivé du groupe")
    }

    @Test fun everyConnectionIsBoundedAndNeverHoldsASlotForEver() {
        val code = PhoneSources.code(host)
        assertTrue("SESSION_MS = 30_000L" in code && "watchdog.schedule(" in code && "slots.tryAcquire()" in code && "slots.release()" in code, "deux sessions à la fois, chacune coupée par un chien de garde")
        assertTrue("RejectedExecutionException" in code, "un chien de garde rendu ne tue jamais un fil (audit B1) : la connexion est fermée")
        val c = PhoneSources.code(client)
        assertTrue("BleSearch.ATTEMPT_MS" in c && "BtConnectLock.of(" in c, "chaque ouverture est bornée et prise sous le verrou de la TV")
        assertTrue("RejectedExecutionException" in c, "une minuterie rendue ne tue jamais un fil (audit B1)")
    }

    @Test fun theTvSharesOneAttemptGateWithItsHttpRouteAndReleasesTheAdvertisementWithTheGroup() {
        val s = PhoneSources.code(service)
        assertTrue("gate = api.gate" in s, "la porte de tentatives de la route HTTP est celle du canal Bluetooth : un seul budget de codes faux")
        assertTrue("termsAccepted = { TunnelHub.termsAccepted(this) }" in s && "deviceRequest = { ActivationCenter.requestText() }" in s && "install = { key -> ActivationCenter.installFromWifi(key) }" in s,
            "les mêmes conditions d'usage, la même demande, le même vérificateur de clé que la route HTTP")
        assertTrue("code = { lockedPin }" in s, "le code est celui de l'écran")
        val stop = s.substringAfter("fun stopActivationGroup()").substringBefore("private val stopActivationGroupLater")
        assertTrue("activationBt?.stop()" in stop, "l'annonce est rendue avec le groupe (à la fermeture de l'écran, après la grâce de 20 s, à l'activation)")
        assertTrue("activationBt?.tick()" in s.substringAfter("fun activationTick()").substringBefore("\n"), "un Bluetooth allumé tard ou une autorisation accordée tard rejoint la voie")
        assertTrue("fun activationBleReady()" in s)
        val stopHttp = s.substringAfter("private fun stopLockedHttp()").substringBefore("runCatching { s.stop() }")
        assertTrue("activationBt?.release()" in stopHttp, "l'arrêt de la route verrouillée rend aussi l'annonce et le chien de garde")
    }

    @Test fun theScreenListsTheWayOnlyWhileTheTvAdvertises() {
        val a = PhoneSources.code(screen)
        assertTrue("phoneView(info?.first, group, bluetooth = TvService.running?.activationBleReady() == true)" in a)
        assertTrue("bluetoothView.visibility = if (v.bluetoothLine != null) View.VISIBLE else View.GONE" in a)
    }

    @Test fun thePhoneDriverRunsTheRouteThroughTheGuardedExecutorsAndAsksForItsPermissionOnce() {
        val d = PhoneSources.code(driver)
        assertTrue("Route.BLE -> onIo { bleAttempt(g) }" in d && "f.route == Route.BLE -> installBle(code, key, g)" in d && "bleFact()" in d)
        assertTrue("ble.release()" in d, "les minuteries du client sont rendues avec le pilote")
        val act = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/ActivateTvActivity.kt")
        assertTrue("ActivationRoutePlan.BLE_PERMISSION_WHY" in act && "BLE_PERMISSION_WHY_LOCATION" in act, "la phrase de l'autorisation est à l'écran")
        assertTrue("Cause.Kind.BLE_PERMISSION in causes" in act, "« Autoriser » est proposé après un refus")
        assertTrue("if (missing.isNotEmpty() && !askedNearby)" in act, "l'autorisation n'est demandée qu'une fois par ouverture")
    }

    @Test fun theManifestsDeclareBleAsOptionalAndTheScanCannotBeUsedForLocation() {
        val phone = PhoneSources.text("sender/src/main/AndroidManifest.xml"); val tv = PhoneSources.text("receiver/src/main/AndroidManifest.xml")
        for (m in listOf(phone, tv)) assertTrue("""android:name="android.hardware.bluetooth_le" android:required="false"""" in m, "BLE facultatif : sans lui la voie est absente")
        assertTrue("""android.permission.BLUETOOTH_ADVERTISE""" in tv, "la TV demande l'autorisation d'annoncer")
        val lib = PhoneSources.text("ownerlib/src/main/AndroidManifest.xml")
        assertTrue("""BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation"""" in lib && """ACCESS_FINE_LOCATION" android:maxSdkVersion="30"""" in lib, "balayage sans localisation depuis Android 12, localisation seulement avant")
        assertFalse("android.permission.CAMERA" in phone)
    }

    @Test fun nothingButATypedResultLeavesThePhoneClientAndTheChannelNeverThrowsAnythingButAnIoException() {
        val c = PhoneSources.code(client)
        assertTrue("fun find(code: String, active: () -> Boolean, observed: (Cause) -> Unit): Search? = try { findNow(" in c && "fun install(code: String, key: String, active: () -> Boolean): BleSearch.Install? = try { installNow(" in c,
            "find et install rendent un résultat typé, jamais une exception : « onIo » ne rattrape rien et une exception de plus dans un fil du pool tue l'application (audit B1)")
        assertTrue(Regex("""catch \(e: Exception\)""").findAll(c).count() >= 3 && "InterruptedException" in c, "chaque étape rattrape l'imprévu et laisse passer l'interruption")
        val channel = PhoneSources.code("core/src/main/kotlin/castbridge/core/btact/BtActChannel.kt")
        assertTrue("catch (e: GeneralSecurityException) { throw java.io.IOException(" in channel, "un échec de chiffrement est une coupure de lien (IOException), pas une exception qui échappe au client")
    }
}
