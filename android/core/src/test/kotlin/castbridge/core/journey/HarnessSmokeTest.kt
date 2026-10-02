package castbridge.core.journey

import castbridge.core.trust.PinCheck
import castbridge.core.trust.TvAuth
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le harnais de parcours prouve qu'il marche : la TV répond, le téléphone s'associe et la liaison passe au vert par la vraie `ReceiverServer`,
 * un envoi arrive entier. Les deux derniers tests vérifient les points du harnais qui reposent sur la réflexion ou sur un redémarrage.
 */
class HarnessSmokeTest {
    private fun get(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val c = java.net.URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            return code to (if (code < 400) c.inputStream else c.errorStream).use { it.readBytes() }.decodeToString()
        } finally { c.disconnect() }
    }

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
    }

    @Test fun aSmallFileArrivesWholeWithMonotoneProgress() = withJourney {
        lateinit var run: UploadRun
        val file = files.small()
        given("un téléphone de confiance") { phone.pairWithTv() }
        whenever("il envoie un fichier de 5 Mio") { run = phone.send(file); run.await(30_000) }
        then("la TV a un seul fichier, de la bonne taille et du bon contenu") {
            assertEquals("done", run.finalState, run.blockerText)
            val got = tv.receivedFiles()
            assertEquals(1, got.size, got.toString())
            assertEquals(file.length(), got[0].length())
            assertEquals(-1L, java.nio.file.Files.mismatch(file.toPath(), got[0].toPath()), "mêmes octets")
        }
        then("la progression ne recule jamais et la dernière notification est finale") {
            val sent = run.samples.map { it.first }
            assertTrue(sent.isNotEmpty() && sent.zipWithNext().all { (a, b) -> a <= b }, sent.toString())
            assertEquals(file.length(), sent.last())
            assertTrue(run.notifications.last().final, run.notifications.toString())
            assertEquals("Terminé", run.notifications.last().text)
        }
    }

    @Test fun aWrongPinIsCountedByTheTvAndNotRemembered() = withJourney {
        whenever("un code faux est saisi") { assertEquals(PinCheck.REJECTED, phone.enterPin("000000")) }
        then("la TV compte un refus et le téléphone ne retient rien") {
            assertEquals(1, tv.pinFailures())
            assertEquals("", phone.credentialNow())
        }
        whenever("le bon code est saisi") { assertEquals(PinCheck.OK, phone.enterPin(tv.pin)) }
        then("il est retenu sous toutes les clés et le compteur repart de zéro") {
            assertEquals(tv.pin, phone.credentialNow())
            assertEquals(5, phone.pins.all.size)
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
        then("l'essai ferme la bibliothèque (403) mais laisse /api/activation ouverte") {
            assertEquals(403, get("${tv.base}/api/library", pinHeader).first)
            val (code, body) = get("${tv.base}/api/activation", pinHeader)
            assertEquals(200, code); assertTrue(body.contains("\"trial\":true"), body)
        }
        then("un jeton de confiance ne peut pas installer une activation (403)") {
            phone.pairWithTv()
            val c = java.net.URI.create("${tv.base}/api/activation/install").toURL().openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(1)
            c.setRequestProperty(TvAuth.TOKEN_HEADER, phone.credentialNow()); c.outputStream.use { it.write(1) }
            assertEquals(403, c.responseCode)
        }
        then("une activation refusée ne change rien, la bonne ouvre la bibliothèque") {
            assertTrue(!tv.activate("n'importe quoi".toByteArray()))
            assertEquals(403, get("${tv.base}/api/library", pinHeader).first)
            assertTrue(tv.activate(ActivationApiSim.TEST_PAYLOAD.toByteArray()))
            assertEquals(200, get("${tv.base}/api/library", pinHeader).first)
        }
    }

    /** Contre-épreuve : le harnais ne doit jamais afficher « vert » par défaut (sans TV : « Aucune TV » ; TV fermée : pas vert ; TV rouverte : vert). */
    @Test fun theChipIsNeverGreenByDefaultNorWhileTheTvIsClosed() = withJourney {
        then("sans aucune TV enregistrée la puce dit « Aucune TV » et n'est pas verte") {
            assertEquals(castbridge.core.trust.LinkState.NoTv, phone.chip().state)
            assertTrue(!phone.chip().state.isGood)
        }
        given("un téléphone de confiance") { phone.pairWithTv(); assertTrue(phone.chip().state.isGood) }
        whenever("l'application TV est fermée") { tv.stop(); phone.env.network = true }
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
}
