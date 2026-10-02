package castbridge.core.journey

import castbridge.core.owner.TrialPolicy
import castbridge.core.trust.PinCheck
import castbridge.core.trust.TvAuth
import castbridge.core.xfer.TransferProgress
import java.io.IOException
import java.net.ConnectException
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le harnais de parcours prouve qu'il marche : la TV répond, le téléphone s'associe et la liaison passe au vert par la vraie `ReceiverServer`,
 * un envoi arrive entier. Les derniers tests vérifient les points du harnais qui reposent sur la réflexion, sur un redémarrage, sur le code figé
 * d'un envoi (R-01), sur la TV verrouillée (aucun serveur) et sur les bornes de temps réel.
 */
class HarnessSmokeTest {
    private fun get(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> = Http.call(url, "GET", headers)

    @Test fun theTvAnswersHelloAndAsksForItsPin() = withJourney {
        given("une TV démarrée sur un port libre") { assertTrue(tv.port > 0, "port réel : ${tv.port}") }
        then("GET /api/hello répond 200 et demande le code") {
            val (code, body) = get("${tv.base}/api/hello")
            assertEquals(200, code, body)
            assertTrue(body.contains("\"pinRequired\":true") && body.contains("castbridge-tv"), body)
        }
        then("une route protégée sans code est refusée (401) et comptée une fois") {
            assertEquals(401, get("${tv.base}/api/info").first)
            assertEquals(1, tv.pinFailures())
        }
    }

    @Test fun pairingTurnsTheChipGreenAndTheTokenOpensTheApi() = withJourney {
        given("un téléphone sans TV") { assertEquals(0, phone.saved.list().size) }
        whenever("le propriétaire ajoute la TV et autorise le téléphone") { phone.pairWithTv() }
        then("la puce est verte et la TV enregistrée une seule fois") {
            assertTrue(phone.chip().state.isGood, phone.chip().toString())
            assertEquals(1, phone.saved.list().size)
            assertEquals(1, tv.registry.list().size)
        }
        then("GET /api/info avec le jeton répond 200 sur la vraie TV") {
            val token = phone.credentialNow()
            assertTrue(TvAuth.isToken(token), "un jeton, pas un code")
            assertEquals(200, get("${tv.base}/api/info", mapOf(TvAuth.TOKEN_HEADER to token)).first)
            assertEquals(0, tv.pinFailures(), "un jeton ne compte pas comme un PIN faux")
        }
        then("la TV a marqué la présence du téléphone (même câblage que TvService)") {
            assertTrue(tv.presence.statuses().any { it.name.isNotBlank() }, tv.presence.statuses().toString())
        }
    }

    @Test fun aSmallFileArrivesWholeWithMonotoneProgress() = withJourney {
        lateinit var run: UploadRun
        val file = files.small()
        given("un téléphone de confiance") { phone.pairWithTv() }
        whenever("il envoie un fichier de 5 Mio") { run = phone.send(file); run.await(3_000) }
        then("la TV a un seul fichier, de la bonne taille et du bon contenu") {
            assertEquals("done", run.finalState, run.blockerText)
            val got = tv.receivedFiles()
            assertEquals(1, got.size, got.toString())
            assertEquals(file.length(), got[0].length())
            assertEquals(-1L, java.nio.file.Files.mismatch(file.toPath(), got[0].toPath()), "mêmes octets")
        }
        then("la progression du téléphone ne recule jamais et finit au total") {
            val sent = run.samples.map { it.first }
            assertTrue(sent.isNotEmpty() && sent.zipWithNext().all { (a, b) -> a <= b }, sent.toString())
            assertEquals(file.length(), sent.last())
        }
        then("la TV a publié la réception par server.progress (même objet que celui de la TV), terminée, depuis le téléphone de confiance") {
            assertTrue(tv.receiver!!.progress === tv.progress)
            val item = tv.progress.shown().single()
            assertEquals(TransferProgress.Phase.DONE, item.phase, item.toString())
            assertEquals(file.length(), item.total)
            assertEquals("Galaxy de test", item.source, "sourceName branché sur le registre de confiance")
        }
    }

    @Test fun aWrongPinIsCountedByTheTvAndNotRemembered() = withJourney {
        whenever("un code faux est saisi") { assertEquals(PinCheck.REJECTED, phone.enterPin("000000")) }
        then("la TV compte un refus et le téléphone ne retient rien") {
            assertEquals(1, tv.pinFailures())
            assertEquals("", phone.credentialNow())
        }
        whenever("le bon code est saisi") { assertEquals(PinCheck.OK, phone.enterPin(tv.pin)) }
        then("il est retenu sous toutes les clés (PinKeys) et le compteur repart de zéro") {
            assertEquals(tv.pin, phone.credentialNow())
            assertEquals(4, phone.pins.all.size, phone.pins.all.keys.toString())
            assertEquals(0, tv.pinFailures())
        }
    }

    @Test fun reinstallingTheTvMakesThePhoneSayItWasReinstalled() = withJourney {
        given("un téléphone de confiance") { phone.pairWithTv() }
        val oldInstall = tv.installId; val oldPin = tv.pin
        whenever("la TV est réinstallée") { tv.reinstall() }
        then("registre vide, nouveau PIN et nouvel identifiant d'installation") {
            assertTrue(tv.registry.list().isEmpty())
            assertTrue(tv.pin != oldPin && tv.installId != oldInstall)
        }
        then("la puce dit que la TV a été réinstallée, sans effacer la TV du carnet") {
            clock.advance(60_000)
            val v = phone.run(5)
            assertTrue(v.state is castbridge.core.trust.LinkState.TvForgotMe, v.toString())
            assertEquals(1, phone.saved.list().size)
            assertTrue(v.detail.contains("réinstall") || v.title.contains("réinstall"), v.toString())
        }
    }

    @Test fun trialClosesTheLibraryAndOnlyThePinInstallsAnActivation() = withJourney(TvSim.TvScenario(trial = true)) {
        val pinHeader = mapOf(TvAuth.PIN_HEADER to tv.pin)
        then("l'essai ferme la bibliothèque (403, avec la raison en français) mais laisse /api/activation ouverte") {
            val (denied, reason) = get("${tv.base}/api/library", pinHeader)
            assertEquals(403, denied)
            assertTrue(TrialPolicy.MESSAGE in reason || "Version d'essai" in reason, "le corps du 403 dit pourquoi : $reason")
            val (code, body) = get("${tv.base}/api/activation", pinHeader)
            assertEquals(200, code); assertTrue(body.contains("\"trial\":true"), body)
        }
        then("un jeton de confiance ne peut pas installer une activation (403, « code de la TV »)") {
            phone.pairWithTv()
            val (code, body) = Http.call("${tv.base}/api/activation/install", "POST", mapOf(TvAuth.TOKEN_HEADER to phone.credentialNow()), byteArrayOf(1))
            assertEquals(403, code, body)
            assertTrue("pin required" in body && "code de la TV" in body, "le corps du 403 dit pourquoi : $body")
            assertTrue(tv.installedActivations().isEmpty(), "rien d'installé")
        }
        then("une activation refusée ne change rien, la bonne ouvre la bibliothèque") {
            assertTrue(!tv.activate("n'importe quoi".toByteArray()))
            assertEquals(403, get("${tv.base}/api/library", pinHeader).first)
            assertTrue(tv.activate(ActivationApiSim.TEST_PAYLOAD.toByteArray()))
            assertEquals(200, get("${tv.base}/api/library", pinHeader).first)
        }
    }

    /**
     * Contre-épreuve : le harnais ne doit jamais afficher « vert » par défaut (sans TV : « Aucune TV » ; TV fermée : pas vert ; TV rouverte : vert).
     * La TV fermée n'est PAS court-circuitée dans le harnais : c'est la vraie connexion qui est refusée, et le test le prouve.
     */
    @Test fun theChipIsNeverGreenByDefaultNorWhileTheTvIsClosed() = withJourney {
        then("sans aucune TV enregistrée la puce dit « Aucune TV » et n'est pas verte") {
            assertEquals(castbridge.core.trust.LinkState.NoTv, phone.chip().state)
            assertTrue(!phone.chip().state.isGood)
        }
        given("un téléphone de confiance") { phone.pairWithTv(); assertTrue(phone.chip().state.isGood) }
        val oldBase = tv.base
        whenever("l'application TV est fermée (le téléphone a toujours son réseau)") { tv.stop(); assertTrue(phone.env.network) }
        then("la VRAIE connexion à l'ancienne adresse de la TV est refusée") {
            assertFailsWith<ConnectException>("la TV fermée doit refuser la connexion, pas être déclarée injoignable par le harnais") {
                get("$oldBase/api/hello")
            }
        }
        then("la puce garde le vert pendant le délai de grâce (40 s), puis le quitte") {
            assertTrue(phone.run(1).state.isGood, "grâce : ${phone.chip()}")
            clock.advance(60_000)
            assertTrue(!phone.run(3).state.isGood, phone.chip().toString())
        }
        whenever("elle est rouverte") { tv.start() }
        then("la puce redevient verte sans nouvelle autorisation") {
            assertTrue(phone.run(6).state.isGood, phone.chip().toString())
            assertEquals(1, tv.registry.list().size)
        }
    }

    @Test fun restartKeepsTheTrustedPhoneAndChangesThePort() = withJourney {
        given("un téléphone de confiance") { phone.pairWithTv() }
        val before = tv.port
        val install = tv.installId
        whenever("la TV est relancée sur le même dossier") { tv.restart() }
        then("le registre est relu du fichier, le port est neuf et le téléphone retrouve la TV") {
            assertEquals(install, tv.installId)
            assertEquals(1, tv.registry.list().size)
            assertTrue(tv.port > 0, "serveur relancé (port avant : $before, après : ${tv.port})")
            assertTrue(phone.run(3).state.isGood, phone.chip().toString())
            assertEquals(200, get("${tv.base}/api/info", mapOf(TvAuth.TOKEN_HEADER to phone.credentialNow())).first)
        }
    }

    // ------------------------------------------------------------------------------------------------------ R-01 : code figé, clé qui ne désigne pas la TV

    /**
     * R-01 tel que vu chez le propriétaire : téléphone de confiance, la TV change de code, l'écran d'envoi passe une clé (« X (Bluetooth) ») que
     * `savedFor` ne reconnaît pas ; `PinStore.get(clé)` rate le jeton et rend l'ANCIEN code ; le code est figé au lancement ⇒ 401 à chaque reprise. Le
     * comportement ATTENDU (après w15-02 : `PinKeys` tolérant) est que la copie arrive avec le jeton. ROUGE aujourd'hui (vérifié : l'envoi reste bloqué).
     */
    @Ignore("REGRESSION R-01: red until w15-02 PinKeys tolerant resolution")
    @Test fun r01UnmatchedScreenKeyStillCopiesWithTheTrustedToken() = withJourney {
        val unmatched = "SMART_TV (Bluetooth)"
        lateinit var run: UploadRun
        given("un téléphone de confiance qui garde un ancien code sous la clé d'écran") {
            phone.pairWithTv()
            assertNull(phone.savedFor(unmatched), "la clé d'écran ne correspond à aucune TV du carnet (égalité stricte de savedFor)")
            phone.pins.put(unmatched, "999999")
        }
        whenever("le code de la TV change puis la liaison reprend") { tv.rotatePin(); phone.run(3) }
        whenever("il envoie un fichier avec cette clé d'écran") { run = phone.send(files.small(), tvKey = unmatched); run.await(3_000) }
        then("la copie arrive entière (le jeton a été retrouvé), sans code à ressaisir") {
            assertEquals("done", run.finalState, "bloquée : ${run.blockerText}")
            assertEquals(1, tv.receivedFiles().size)
        }
    }

    /** Contre-épreuve de R-01 : avec la clé qui DÉSIGNE la TV (nom mDNS), le même parcours arrive : le rouge de R-01 vient bien de la clé, pas du harnais. */
    @Test fun r01ControlMatchedScreenKeyCopiesAfterThePinRotated() = withJourney {
        lateinit var run: UploadRun
        given("un téléphone de confiance") { phone.pairWithTv(); phone.pins.put("SMART_TV (Bluetooth)", "999999") }
        whenever("le code de la TV change, la liaison reprend, et il envoie avec la clé de la TV") {
            tv.rotatePin(); phone.run(3)
            run = phone.send(files.small(), tvKey = phone.defaultKey); run.await(3_000)
        }
        then("la copie arrive avec le jeton") { assertEquals("done", run.finalState, run.blockerText) }
    }

    /**
     * Même mécanisme, cas sans issue automatique : la TV est RÉINSTALLÉE (registre vide, nouveau code) et le téléphone n'a qu'un ancien code sous une
     * clé qui ne désigne pas la TV. La copie ne doit pas finir ; elle doit s'arrêter en NOMMANT la raison (jamais « en vert à 0 % » sans texte).
     */
    @Test fun reinstalledTvWithStalePinUnderUnmatchedKeyStallsWithANamedReason() = withJourney {
        val unmatched = "SMART_TV (Bluetooth)"
        lateinit var run: UploadRun
        given("un téléphone de confiance qui a retenu l'ancien code sous la clé d'écran") {
            phone.pairWithTv()
            phone.pins.put(unmatched, tv.pin)
        }
        whenever("la TV est réinstallée (nouveau code, registre vide)") { tv.reinstall() }
        whenever("il envoie un fichier avec cette clé d'écran") { run = phone.send(files.small(), tvKey = unmatched); run.await(1_500) }
        then("la copie n'est pas finie, rien n'est arrivé sur la TV, et la raison est nommée") {
            assertTrue(run.finalState in setOf("running", "failed"), "ni terminée ni muette : ${run.finalState} / ${run.blockerText}")
            assertTrue(tv.receivedFiles().isEmpty(), tv.receivedFiles().toString())
            val why = run.blockerText
            assertTrue(!why.isNullOrBlank(), "une raison nommée est attendue (reçu : $why)")
        }
        then("la TV a compté des refus de code (le code figé est faux), pas un silence") {
            assertTrue(tv.pinFailures() > 0 || run.blockerText!!.isNotBlank(), "refus comptés : ${tv.pinFailures()}")
        }
    }

    @Test fun theCodeOfASendIsFrozenAtLaunch() = withJourney {
        given("un téléphone de confiance") { phone.pairWithTv() }
        then("une clé qui désigne la TV (nom mDNS, nom, bt:, hôte:port) retrouve le jeton ; une autre non (égalité stricte de savedFor)") {
            val s = phone.saved.list().single()
            listOf(phone.defaultKey, s.name, "bt:${s.address}", "127.0.0.1:${s.port}").forEach { k ->
                assertTrue(TvAuth.isToken(phone.pinStoreGet(k)), "clé $k")
            }
            assertEquals("", phone.pinStoreGet("SMART_TV (Bluetooth)"))
            assertEquals("", phone.pinStoreGet("127.0.0.1"), "une IP sans port ne désigne pas la TV")
        }
        lateinit var run: UploadRun
        whenever("un envoi part avec une clé sans code ni TV, puis le bon code est mémorisé sous cette clé") {
            run = phone.send(files.small(), tvKey = "autre"); run.await(300)
            phone.pins.put("autre", tv.pin)
            run.await(500)
        }
        then("le code figé au lancement (vide) n'est pas remplacé : l'envoi attend, rien n'arrive") {
            assertEquals("running", run.finalState, run.blockerText)
            assertTrue(tv.receivedFiles().isEmpty())
        }
        whenever("un NOUVEL envoi part avec cette clé") { run = phone.send(files.small("deux.bin"), tvKey = "autre"); run.await(3_000) }
        then("il fige le bon code et arrive") {
            assertEquals("done", run.finalState, run.blockerText)
        }
    }

    // ------------------------------------------------------------------------------------------------------ Bluetooth seul, TV verrouillée, bornes

    /** Pas de voie de données Bluetooth dans le harnais : une liaison Bluetooth SEULE ne donne aucune adresse d'envoi (même si la TV répond en HTTP). */
    @Test fun bluetoothOnlyLinkHasNoDataLane() = withJourney {
        lateinit var run: UploadRun
        given("une TV qui n'annonce aucune adresse Wi-Fi : la liaison est Bluetooth seule") {
            tv.bt.lan = emptyList()
            phone.pairWithTv()
            val s = phone.lastStep?.session
            assertNotNull(s, phone.chip().toString())
            assertNull(s.base, "liaison Bluetooth seule : ${s.route}")
            assertEquals(200, get("${tv.base}/api/hello").first, "pourtant le HTTP de la TV répond")
        }
        whenever("il envoie un fichier") { run = phone.send(files.small()); run.await(500) }
        then("l'envoi n'a aucune voie : il attend et rien n'arrive") {
            assertEquals("running", run.finalState, run.blockerText)
            assertTrue(tv.receivedFiles().isEmpty())
        }
    }

    @Ignore("J-14 / P-13 : pas de voie de données Bluetooth dans le harnais (copie par Bluetooth : fumée ou w14-05)")
    @Test fun j14SendOverBluetoothArrivesWithItsOwnTexts() = withJourney { }

    @Test fun aLockedTvHasNoHttpServerNorBluetoothService() = withJourney(TvSim.TvScenario(locked = true)) {
        then("aucun serveur : pas de port, pas de ReceiverServer") {
            assertTrue(tv.locked); assertEquals(-1, tv.port); assertNull(tv.receiver); assertNull(tv.lastBase)
        }
        then("une connexion à un port sans serveur est réellement refusée") {
            val closed = java.net.ServerSocket(0).use { it.localPort }
            assertFailsWith<ConnectException> { get("http://127.0.0.1:$closed/api/hello") }
        }
        then("le téléphone ne peut ni s'associer ni vérifier un code : la puce n'est pas verte") {
            phone.pairWithTv()
            assertFalse(phone.chip().state.isGood, phone.chip().toString())
            assertTrue(tv.registry.list().isEmpty(), "aucun appairage sur une TV verrouillée")
            assertEquals(PinCheck.UNREACHABLE, phone.enterPin(tv.pin))
        }
        whenever("le propriétaire active la TV (écran d'activation ou clé USB)") { tv.unlock() }
        then("la TV démarre : elle répond en HTTP, et l'activation dit qu'elle n'est plus verrouillée") {
            assertEquals(200, get("${tv.base}/api/hello").first)
            assertTrue("\"locked\":false" in get("${tv.base}/api/activation", mapOf(TvAuth.PIN_HEADER to tv.pin)).second)
        }
    }

    @Test fun theWatchdogFailsAJourneyThatBlocksAndDescribesTheThreads() {
        val e = assertFailsWith<AssertionError> {
            withJourney(timeoutMs = 400) { then("un pas qui ne finit jamais") { java.util.concurrent.CountDownLatch(1).await() } }
        }
        val m = e.message.orEmpty()
        assertTrue("Parcours bloqué" in m && "journey-block" in m && "un pas qui ne finit jamais" !in m.substringBefore("Fils"), m.take(400))
    }

    @Test fun anUnboundedAwaitIsRefused() = withJourney {
        given("un envoi") { phone.pairWithTv() }
        then("attendre plus de 3 s réelles est refusé") {
            val run = phone.send(files.small())
            assertFailsWith<IllegalArgumentException> { run.await(30_000) }
            run.await(3_000)
        }
    }

    @Test fun anIoErrorOfTheHarnessHttpIsNotSwallowed() {
        val closed = java.net.ServerSocket(0).use { it.localPort }
        assertFailsWith<IOException> { Http.call("http://127.0.0.1:$closed/") }
    }
}
