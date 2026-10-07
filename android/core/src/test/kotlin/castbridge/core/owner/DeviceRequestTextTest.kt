package castbridge.core.owner

import castbridge.core.trust.DeviceRequestParse
import kotlin.test.*

/**
 * La demande d'appareil sous ses deux formes (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F4 amendée le 2026-10-07 : `install=` est la clé PUBLIQUE X25519 de la TV, rien de secret ;
 * une clé d'essai en enveloppe v2 l'exige) :
 *  - COMPLÈTE ([DeviceRequestText.complete]) : `code=`, `k=`, `factor=`, `install=` quand la TV a sa clé, `install_sig=` : ce que la TV rend au téléphone, ce que celui-ci partage et ce que lisent les outils ;
 *  - POUR LE SERVEUR ([DeviceRequestText.forServer]) : la même sans `install=`, pour la seule voie B2 (envoi au serveur).
 * Toujours reconstruites depuis la demande ANALYSÉE, jamais le texte brut recopié.
 */
class DeviceRequestTextTest {
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd"))
    private val code = DeviceCode.of(fp)
    private val installPub = ByteArray(32) { (it + 3).toByte() }
    private val sigPub = ByteArray(32) { (it + 7).toByte() }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private val full = OwnerFrames.deviceInfo(code, fp, installPub, sigPub)
    private val withoutInstall = OwnerFrames.deviceInfo(code, fp, null, sigPub)
    private val canaries = listOf("CANARY-MODEL-Bravia", "CANARY-TOKEN-cbk_42", "CANARY-OWNERPW-13")
    private fun lines(t: String) = t.lines()

    // ---- la forme complète

    @Test fun theCompleteFormKeepsTheInstallLineWhenTheTvHasItsKey() {
        val t = assertNotNull(DeviceRequestText.complete(full))
        assertEquals(full, t, "octet pour octet : ce que la TV a construit")
        assertTrue(lines(t).contains("install=x25519|" + hex(installPub)), t)
        assertTrue(lines(t).last().startsWith("install_sig=ed25519|"), "la clé de signature reste, en dernier : $t")
        assertContentEquals(installPub, assertNotNull(OwnerFrames.parseDeviceInfo(t)).installPub)
    }

    @Test fun theCompleteFormHasNoInstallLineWhenTheTvHasNoKey() {
        // clé encore en préparation (coffre de clés pas prêt) ou TV plus ancienne : la ligne est ABSENTE, jamais vide ni inventée
        val t = assertNotNull(DeviceRequestText.complete(withoutInstall))
        assertEquals(withoutInstall, t)
        assertTrue(lines(t).none { it.startsWith("install=") }, t)
        assertNull(assertNotNull(OwnerFrames.parseDeviceInfo(t)).installPub)
        // sans clé de signature non plus : seulement ce que la TV a
        val bare = assertNotNull(DeviceRequestText.complete(OwnerFrames.deviceInfo(code, fp)))
        assertEquals(listOf("code=", "k=", "factor=", "factor=", "factor="), lines(bare).map { it.substringBefore('=') + "=" })
    }

    @Test fun theCompleteFormIsRebuiltFromTheParsedRequestNeverCopied() {
        // bruit : fins de ligne CRLF, lignes vides, espaces, ordre des lignes inconnues, empreinte lisible facultative, lignes d'autres choses qu'une TV plus récente pourrait ajouter
        val noisy = "\r\n  " + full.replace("\n", "\r\n\r\n  ") + "\r\n" + OwnerFrames.deviceInfo(code, fp, installPub, sigPub, withFingerprint = true).lines().last { it.startsWith("install_fp=") } +
            "\r\nmodel=${canaries[0]}\r\ntoken=${canaries[1]}\r\nowner=${canaries[2]}\r\n"
        assertTrue(canaries.all { it in noisy })
        val t = assertNotNull(DeviceRequestText.complete(noisy))
        assertEquals(full, t, "le texte canonique, sans le bruit")
        for (c in canaries) assertFalse(c in t, "$c ne sort jamais")
        assertTrue(lines(t).none { it.startsWith("install_fp=") }, "l'empreinte lisible est refaite par les lecteurs, jamais recopiée")
    }

    @Test fun anInstallLineOfAnotherAlgorithmIsDroppedAndAMalformedOneMakesTheRequestUnreadable() {
        val other = assertNotNull(DeviceRequestText.complete(withoutInstall + "\ninstall=rsa|0123"))
        assertEquals(withoutInstall, other, "un algorithme inconnu est une ligne inconnue : ignorée, jamais recopiée")
        val short = "install=x25519|" + hex(installPub).dropLast(2)
        val upper = "install=x25519|" + hex(installPub).uppercase()
        val notHex = "install=x25519|" + "zz".repeat(32)
        for (bad in listOf(short, upper, notHex, "install=x25519|" + hex(installPub) + "\ninstall=x25519|" + hex(installPub)))
            assertNull(DeviceRequestText.complete(withoutInstall + "\n" + bad), bad)
    }

    @Test fun whatIsNotADeviceRequestIsNeverReturned() {
        for (bad in listOf("", "   ", "pas une demande", "code=" + code, "code=pas-un-code\nk=2", full.replace("code=$code\n", ""), full + "\nligne sans signe egal", full.replace("factor=FLASH|", "factor=EVIL|"))) {
            assertNull(DeviceRequestText.complete(bad), bad)
            assertNull(DeviceRequestText.forServer(bad), bad)
        }
    }

    // ---- la forme pour le serveur

    @Test fun theServerFormDropsTheInstallLineAndKeepsTheSigningKey() {
        val t = assertNotNull(DeviceRequestText.forServer(full))
        assertEquals(withoutInstall, t)
        assertTrue(lines(t).none { it.startsWith("install=") }, t)
        assertFalse("x25519" in t); assertFalse(hex(installPub) in t)
        assertTrue(lines(t).all { it.startsWith("code=") || it.startsWith("k=") || it.startsWith("factor=") || it.startsWith("install_sig=") }, t)
        assertEquals("install_sig=ed25519|" + hex(sigPub), lines(t).last(), "le serveur signe cette clé dans l'activation de production")
    }

    @Test fun theServerFormIsStableAndNeverCopiesRawText() {
        assertEquals(withoutInstall, DeviceRequestText.forServer(withoutInstall))
        assertEquals(DeviceRequestText.forServer(full), DeviceRequestText.forServer(DeviceRequestText.forServer(full)!!), "idempotente")
        val t = assertNotNull(DeviceRequestText.forServer("\r\n" + full.replace("\n", "\r\n\r\n") + "\r\nmodel=${canaries[0]}\r\ntoken=${canaries[1]}"))
        assertEquals(withoutInstall, t)
        for (c in canaries) assertFalse(c in t)
    }

    @Test fun bothFormsAreTheOnesTheModelOfTheRequestBuilds() {
        // le même texte que « Partager » (modèle de la demande lue) et que « Copier pour le serveur » : une seule règle, deux niveaux
        val request = (LockedRequestRoute.parse(full) as DeviceRequestParse.Ok).request
        assertEquals(request.fullText(), DeviceRequestText.complete(full))
        assertEquals(request.serverText(), DeviceRequestText.forServer(full))
    }

    @Test fun theCodeOfTheRequestIsTheOnlyIdentityBothFormsCarry() {
        for (t in listOf(DeviceRequestText.complete(full)!!, DeviceRequestText.forServer(full)!!)) {
            val info = assertNotNull(OwnerFrames.parseDeviceInfo(t))
            assertEquals(code, info.code); assertEquals(fp.byKind, info.fp.byKind); assertContentEquals(sigPub, info.installSig); assertTrue(info.unknown.isEmpty())
        }
    }
}
