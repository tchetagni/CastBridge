package castbridge.play

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ré-audit final : `.env.play.example`, PLAY-OPS.md § 4.2, server-play/README et le compose listent LES MÊMES variables de production, le conteneur en lecture seule a son
 * volume inscriptible, et les textes ne disent plus de choses fausses (clés de confiance, lots réservés, image, module de licences).
 */
class ProductionDocsTest {
    private fun read(p: String) = File(p).readText()
    private val envExample = read("../backend/.env.play.example")
    private val compose = read("../backend/docker-compose.play.yml")
    private val ops = read("../docs/PLAY-OPS.md")
    private val req = read("../docs/PLAY-OPS-REQUIREMENTS.md")
    private val readme = read("README.md")
    private val dockerfile = read("Dockerfile")
    private val section42 = ops.substringAfter("### 4.2 ").substringBefore("### 4.3")

    private val production = listOf("TRUSTED_PROXIES", "REVOCATIONS_URL", "REVOCATIONS_FILE", "TICKET_PUBKEY", "TRUSTED_KEYS", "ORIGINS", "CREATES_PER_IP_HOUR", "CREATES_PER_IDENTITY_DAY",
        "CREATES_PER_48_HOUR", "MAX_PER_IP", "MAX_PER_48", "MAX_PER_IP_SHARED", "RESERVED_DIR", "RESERVED_IDS").map { "CASTBRIDGE_PLAY_$it" }

    @Test fun everyProductionVariableIsInTheFourPlaces() {
        for (v in production) {
            assertTrue(v in envExample, "$v absent de .env.play.example")
            assertTrue(v in section42, "$v absent de PLAY-OPS.md § 4.2")
            assertTrue(v in readme, "$v absent de server-play/README.md")
            assertTrue(v in compose, "$v absent de docker-compose.play.yml")
            assertTrue(v in PlayConfig.ENV_NAMES, "$v n'est pas lue par le service")
        }
    }

    @Test fun theReadOnlyContainerHasAWritableVolumeForTheRevocationsFile() {
        assertTrue(Regex("play-state:/var/lib/castbridge-play").containsMatchIn(compose), "volume play-state absent du compose")
        assertTrue(Regex("(?m)^volumes:\\s*\\n\\s+play-state:").containsMatchIn(compose), "volume nommé play-state non déclaré")
        assertTrue("/var/lib/castbridge-play" in dockerfile && Regex("chown[^\\n]*10002[^\\n]*/var/lib/castbridge-play|chown[^\\n]*/var/lib/castbridge-play").containsMatchIn(dockerfile.replace("\\\n", " ")), "Dockerfile : mkdir + chown 10002 du dossier d'état")
        assertTrue(dockerfile.indexOf("/var/lib/castbridge-play") < dockerfile.indexOf("USER 10002"), "avant USER")
        assertTrue("CASTBRIDGE_PLAY_REVOCATIONS_FILE" in envExample && "/var/lib/castbridge-play/revocations.txt" in (envExample + dockerfile))
    }

    @Test fun trustedKeysAreThePublicKeyOfTheServerNotTheOfflineToolsList() {
        assertFalse(Regex("même valeur que `?CASTBRIDGE_LICENSES_TRUSTED_KEYS").containsMatchIn(req), "PLAY-OPS-REQUIREMENTS § 14 : affirmation fausse")
        assertFalse("format de `CASTBRIDGE_LICENSES_TRUSTED_KEYS`" in readme && "même valeur" in readme)
        for ((name, text) in listOf("REQUIREMENTS" to req, "PLAY-OPS" to ops)) {
            assertTrue("/api/v1/admin/licenses/signing" in text, "$name : où lire la clé publique du serveur")
            assertTrue("REVOKE" in text && "license-signing.key" in text, "$name : portée REVOKE et clé de signature")
        }
    }

    @Test fun theLicencesModuleIsOffByDefaultAndTheOwnerMustDecide() {
        assertTrue("503" in section42 + ops.substringAfter("## 4. ").substringBefore("### 4.1") && "CASTBRIDGE_LICENSES_ENABLED" in ops, "PLAY-OPS : GET /api/v1/revocations répond 503 tant que le module licences n'est pas actif")
        assertTrue(Regex("liste de révocations signée statique", RegexOption.IGNORE_CASE).containsMatchIn(ops), "l'autre issue : une liste statique signée")
        assertTrue("PRÉREQUIS" in ops.uppercase())
    }

    @Test fun reservedLotsAreNeverMountedOnTheFreeLotsFolder() {
        assertFalse("lots de questions réservés (lecture seule)" in compose, "ancien commentaire du compose : faux (les réservées sur LOTS_DIR)")
        assertTrue("RESERVED_DIR=/reserved" in compose && ":/reserved:ro" in compose, "les réservées se montent sur RESERVED_DIR")
        assertTrue("jamais" in compose.substringAfter("RESERVED").lowercase())
    }

    @Test fun healthVerificationRequiresARealOkAndTheImageIsJava25() {
        assertTrue(Regex("\"revocations\"\\s*:\\s*\"ok\"|revocations.*== ?\"ok\"|\\[\"revocations\"\\] != \"ok\"").containsMatchIn(req + ops), "la vérification exige \"revocations\":\"ok\"")
        assertFalse("base `eclipse-temurin:21-jre`" in readme, "README : image Java 25")
        assertTrue("eclipse-temurin:25-jre" in readme)
        assertTrue("n'est pas facultative" in (readme + ops + req), "REVOCATIONS_URL n'est pas facultative en production")
    }

    @Test fun theStaleLineOfPlayOpsIsGone() {
        assertFalse("à confirmer à sa fusion" in ops, "ligne périmée sur CASTBRIDGE_PLAY_TICKET_KEY_FILE")
    }
}
