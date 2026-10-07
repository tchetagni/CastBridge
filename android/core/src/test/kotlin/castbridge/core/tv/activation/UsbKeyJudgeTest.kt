package castbridge.core.tv.activation

import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.RawFactors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-44 (audit anti-régression 2026-10-07 b, I-16, CONFIRMÉ) : une clé d'essai déjà installée et laissée branchée (l'outil `--cle-usb` écrit trois copies, la clé reste sur la TV)
 * revenait « activation trouvée › Passer en production » à chaque branchement pendant 48 h, puis « clé périmée : demandez-en une nouvelle » à un client bien activé. Le vérificateur
 * ne connaissait pas ce qui était installé. Ici le jugement de ce que dit la TV d'une clé qu'elle porte DÉJÀ, avec de vraies activations signées (clés de TEST).
 */
class UsbKeyJudgeTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val code = DeviceCode.of(fp)
    private val signer = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val t0 = 1_800_000_000_000L

    private fun issue(kind: ActivationKind, at: Long) = ActivationIssuer(signer).issue(ActivationIssuer.Request(kind, code, fp, issuedAt = at,
        license = if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else "lic-abcdef0123"))

    private fun judge(line: String, v: Verdict, installedTexts: List<String> = emptyList(), installed: List<Activation> = emptyList()) =
        UsbKeyJudge.verdict(line, v, installedTexts.map(UsbKeyJudge::fingerprint).toSet(), installed.map { it.signature }.toSet())

    @Test fun aKeyAlreadyInstalledIsInstalledWhateverTheVerifierSaid() {
        val trial = issue(ActivationKind.TRIAL, t0)
        for (v in Verdict.values()) assertEquals(Verdict.INSTALLED, judge(trial.token, v, installedTexts = listOf(trial.token)), "verdict du vérificateur : $v")
        // avant la fin des 48 h le vérificateur dit ACCEPTED (« trouvée › Activer »), après EXPIRED (« périmée ») : les deux étaient faux pour une clé installée
        assertEquals(Verdict.INSTALLED, judge(trial.token, Verdict.ACCEPTED, installedTexts = listOf(trial.token)))
        assertEquals(Verdict.INSTALLED, judge(trial.token, Verdict.EXPIRED, installedTexts = listOf(trial.token)))
    }

    @Test fun aDifferentKeyIsJudgedAsBefore() {
        val installed = issue(ActivationKind.TRIAL, t0); val other = issue(ActivationKind.PRODUCTION, t0 + 1)
        for (v in listOf(Verdict.ACCEPTED, Verdict.EXPIRED, Verdict.WRONG_DEVICE, Verdict.NOT_VALID))
            assertEquals(v, judge(other.token, v, installedTexts = listOf(installed.token), installed = listOf(installed.activation)), "une autre clé (production) : $v")
        // le même poste renouvelé plus tard : une autre signature, une autre clé
        val renewed = issue(ActivationKind.TRIAL, t0 + 3_600_000L)
        assertNotEquals(installed.activation.signature, renewed.activation.signature)
        assertEquals(Verdict.ACCEPTED, judge(renewed.token, Verdict.ACCEPTED, installedTexts = listOf(installed.token), installed = listOf(installed.activation)))
    }

    @Test fun nothingInstalledNothingChanges() {
        val k = issue(ActivationKind.TRIAL, t0)
        for (v in Verdict.values().filter { it != Verdict.INSTALLED }) assertEquals(v, judge(k.token, v), v.name)
    }

    @Test fun theSameActivationWrittenDifferentlyIsRecognisedByItsSignature() {
        val a = issue(ActivationKind.TRIAL, t0)
        // la TV garde ce qui lui a été donné sous une forme ; la clé USB porte la même activation sous une autre (empreintes de texte différentes)
        for (form in listOf(a.token, "  ${a.token}\r\n", a.groupedText)) {
            assertEquals(Verdict.INSTALLED, judge(form, Verdict.EXPIRED, installed = listOf(a.activation)), form.take(12))
            assertEquals(Verdict.INSTALLED, judge(form, Verdict.ACCEPTED, installed = listOf(a.activation)), form.take(12))
        }
        assertEquals(a.activation.signature, UsbKeyJudge.signatureOf(a.token))
        assertEquals(a.activation.signature, UsbKeyJudge.signatureOf(a.groupedText))
    }

    @Test fun aCompactTypedKeyIsRecognisedByItsTextOnly() {
        // une clé saisissable ne porte pas de signature lisible : la mémoire de ce qui est installé est son texte
        val compact = "ABCD2 EFGH3 JKMN4 PQRS5 TVWX6"
        assertNull(UsbKeyJudge.signatureOf(compact))
        assertEquals(Verdict.INSTALLED, judge(compact, Verdict.EXPIRED, installedTexts = listOf(compact)))
        assertEquals(Verdict.INSTALLED, judge("ABCD2 EFGH3\r\nJKMN4 PQRS5\tTVWX6", Verdict.EXPIRED, installedTexts = listOf(compact)), "les espaces et les retours à la ligne ne comptent pas")
        assertEquals(Verdict.EXPIRED, judge("ABCD2 EFGH3 JKMN4 PQRS5 TVWY6", Verdict.EXPIRED, installedTexts = listOf(compact)), "un seul caractère de différence : une autre clé")
    }

    @Test fun theFingerprintIgnoresSpacesAndLineBreaksButNotContent() {
        assertEquals(UsbKeyJudge.fingerprint("abc"), UsbKeyJudge.fingerprint(" a b\r\nc\t"))
        assertNotEquals(UsbKeyJudge.fingerprint("abc"), UsbKeyJudge.fingerprint("abd"))
        assertNotEquals(UsbKeyJudge.fingerprint("abc"), UsbKeyJudge.fingerprint("ABC"), "la casse compte : la clé est sensible à la casse")
    }

    @Test fun theFingerprintIsAHashNeverTheKeyText() {
        val k = issue(ActivationKind.TRIAL, t0).token
        val f = UsbKeyJudge.fingerprint(k)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(f), f)
        assertFalse(f in k || k.contains(f.take(12)), "rien du texte de la clé ne se lit dans l'empreinte")
        assertNotNull(Activation.decode(k), "le jeton de test est une vraie activation")
    }

    @Test fun junkIsNotASignature() {
        for (junk in listOf("", "   ", "cbx1.", "cbx1.AAAA.BBBB", "hello", "x".repeat(300))) assertNull(UsbKeyJudge.signatureOf(junk), junk.take(12))
    }
}
