package castbridge.core.tv.activation

import castbridge.core.quiz.QrCode
import castbridge.core.tv.WdCode
import castbridge.core.tv.WifiDirect
import castbridge.core.tv.activation.ActivationScreenPlan as P
import castbridge.core.tv.activation.UsbActivationBanner as U
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The guided activation screen (F6, docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md): numbered ways in the order the TV DETECTS (phone linked, key plugged, otherwise phone,
 * key, typing), one status line per way, the sentence under the QR, and the QR's size. Pure: the Android screen only draws what this decides.
 */
class ActivationScreenPlanTest {
    private val vol = listOf(VolumeFact("A379-E209", false))
    private fun usb(vararg p: Probe, volumes: List<VolumeFact> = vol) = U.from(LookupFacts(volumes, p.map { ProbeFact(Place.OWN_DIR, "A379-E209", it) }, emptyList()))
    private val idle = U.idle()
    private val keyUnusable = usb(Probe.WRONG_DEVICE)
    private val keyUsable = usb(Probe.ACCEPTED)
    private val noKey = usb(volumes = emptyList())

    private fun lanes(f: P.Facts) = P.order(f)

    // ---- the order ----

    @Test fun `nothing detected gives phone, key, typing, numbered 1 2 3`() {
        val plan = P.plan(P.Facts())
        assertEquals(listOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), plan.map { it.lane })
        assertEquals(listOf(1, 2, 3), plan.map { it.number })
        assertEquals(listOf('A', 'C', 'E'), plan.map { it.lane.letter })
    }

    @Test fun `a phone linked by the group or the network comes first`() {
        assertEquals(listOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), lanes(P.Facts(phoneLinked = true)))
        assertEquals(listOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), lanes(P.Facts(phoneLinked = true, usb = keyUnusable)), "a key that cannot activate does not take the lead from a linked phone")
    }

    @Test fun `a key plugged in comes first`() {
        assertEquals(listOf(P.Lane.USB, P.Lane.PHONE, P.Lane.TYPED), lanes(P.Facts(usb = keyUnusable)), "detected: its reason is what the person is looking for")
        assertEquals(listOf(P.Lane.USB, P.Lane.PHONE, P.Lane.TYPED), lanes(P.Facts(usb = usb(Probe.ABSENT))))
        assertEquals(listOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), lanes(P.Facts(usb = noKey)), "no key: nothing to put first")
        assertEquals(listOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), lanes(P.Facts(usb = idle)))
    }

    @Test fun `a key that verifies for this TV beats everything, one press away`() {
        assertEquals(listOf(P.Lane.USB, P.Lane.PHONE, P.Lane.TYPED), lanes(P.Facts(usb = keyUsable)))
        assertEquals(listOf(P.Lane.USB, P.Lane.PHONE, P.Lane.TYPED), lanes(P.Facts(phoneLinked = true, usb = keyUsable)))
    }

    @Test fun `typing is always last and every way appears exactly once, whatever is detected`() {
        val usbs = listOf(idle, noKey, keyUnusable, keyUsable, U.searching(), U.termsPending(), usb(Probe.ABSENT), usb(Probe.ABSENT, volumes = vol))
        val groups = listOf(P.Group.NotTried, P.Group.Offered, P.Group.Starting, P.Group.Ready, P.Group.Failed(WifiDirect.Err.WIFI_OFF), P.Group.Failed(null))
        for (linked in listOf(false, true)) for (u in usbs) for (g in groups) for (ips in listOf(emptyList(), listOf("192.168.1.20"))) {
            val plan = P.plan(P.Facts(phoneLinked = linked, usb = u, group = g, lanIps = ips))
            assertEquals(3, plan.size)
            assertEquals(setOf(P.Lane.PHONE, P.Lane.USB, P.Lane.TYPED), plan.map { it.lane }.toSet())
            assertEquals(P.Lane.TYPED, plan.last().lane, "the paste field is the last resort")
            assertEquals(listOf(1, 2, 3), plan.map { it.number })
            assertTrue(plan.all { it.title.isNotBlank() && it.status.isNotBlank() }, "one status line per way, never empty")
            assertTrue(plan.all { '\n' !in it.status }, "one LINE")
        }
    }

    // ---- the status of each way ----

    private fun phoneStatus(f: P.Facts) = P.plan(f).first { it.lane == P.Lane.PHONE }
    private val lan = listOf("192.168.1.20")

    @Test fun `the phone way says what the TV is doing for it`() {
        assertEquals("Téléphone relié : suivez les étapes sur le téléphone", phoneStatus(P.Facts(phoneLinked = true, group = P.Group.Ready)).status)
        assertEquals(LineTone.GOOD, phoneStatus(P.Facts(phoneLinked = true)).tone)
        val ready = phoneStatus(P.Facts(group = P.Group.Ready, lanIps = lan))
        assertTrue("Réseau direct prêt" in ready.status && "192.168.1.20" in ready.status && "en attente du téléphone" in ready.status, ready.status)
        assertEquals(LineTone.INFO, ready.tone)
        assertTrue("Réseau direct prêt" in phoneStatus(P.Facts(group = P.Group.Ready)).status)
        assertTrue("préparation" in phoneStatus(P.Facts(group = P.Group.Starting)).status)
        val lanOnly = phoneStatus(P.Facts(group = P.Group.NotTried, lanIps = lan)).status
        assertTrue("même Wi-Fi" in lanOnly && "192.168.1.20" in lanOnly, lanOnly)
        assertTrue("Bluetooth" in phoneStatus(P.Facts()).status, "no network at all: Bluetooth stays")
    }

    @Test fun `a group that cannot exist says why in one line and the local network stays open`() {
        for ((err, word) in listOf(WifiDirect.Err.WIFI_OFF to "Wi-Fi de la TV est éteint", WifiDirect.Err.UNSUPPORTED to "ne sait pas", WifiDirect.Err.PERMISSION to "permission", WifiDirect.Err.FAILED to "pas pu")) {
            val s = phoneStatus(P.Facts(group = P.Group.Failed(err), lanIps = lan))
            assertTrue("Réseau direct impossible" in s.status && word in s.status, "$err: ${s.status}")
            assertTrue("même Wi-Fi" in s.status && "192.168.1.20" in s.status, "the local network way is still offered: ${s.status}")
            assertEquals(LineTone.WARN, s.tone)
            assertTrue("Bluetooth" in phoneStatus(P.Facts(group = P.Group.Failed(err))).status, "without any network the Bluetooth way is said")
        }
        assertEquals(ActivationGroupTexts.failed(WifiDirect.Err.WIFI_OFF), ActivationGroupTexts.failed(WifiDirect.Err.WIFI_OFF))
        for (err in WifiDirect.Err.ALL + listOf(null, "unknown-word")) {
            val t = ActivationGroupTexts.failed(err)
            assertTrue(t.startsWith("Réseau direct impossible : "), t)
            assertFalse("mot de passe" in t.lowercase() || "passphrase" in t.lowercase(), "the group's secret is never in a status line")
        }
        assertNotEquals(ActivationGroupTexts.failed(WifiDirect.Err.WIFI_OFF), ActivationGroupTexts.failed(WifiDirect.Err.PERMISSION), "each cause has its own words")
    }

    @Test fun `the key way shows the key's presence, the typing way says it is the last resort`() {
        val c = P.plan(P.Facts(usb = keyUnusable)).first { it.lane == P.Lane.USB }
        assertEquals(keyUnusable.presence, c.status)
        assertTrue("Clé détectée : A379-E209" in c.status)
        assertTrue("Aucune clé USB détectée" in P.plan(P.Facts()).first { it.lane == P.Lane.USB }.status)
        val e = P.plan(P.Facts()).first { it.lane == P.Lane.TYPED }
        assertTrue("dernier recours" in e.title, e.title)
        assertTrue(P.plan(P.Facts()).first { it.lane == P.Lane.PHONE }.title.contains("téléphone"))
        assertTrue(P.plan(P.Facts()).first { it.lane == P.Lane.USB }.title.contains("clé USB"))
    }

    @Test fun `the phone way says the phone reads the device request and obtains the key, and never asks to send the code`() {
        // R-46 bis : la TV disait « fournissez ce code d'appareil à CastBridge » : faux, le code seul ne permet pas d'émettre une clé (l'émetteur exige les empreintes de facteurs)
        assertEquals("le téléphone lit la demande d'appareil et obtient la clé", P.PHONE_WAY)
        val phone = P.plan(P.Facts()).first { it.lane == P.Lane.PHONE }
        assertTrue(P.PHONE_WAY in phone.title, phone.title)
        val texts = listOf(P.Facts(), P.Facts(phoneLinked = true), P.Facts(group = P.Group.Offered, lanIps = lan), P.Facts(group = P.Group.Failed(null))).flatMap { f -> P.plan(f).flatMap { listOf(it.title, it.status) } } +
            listOf(P.phoneInstruction("482913", true), P.phoneInstruction("482913", false), P.phoneInstruction("482913", false, sameWifi = true), P.NO_ROUTE_INSTRUCTION)
        for (t in texts) for (bad in listOf("envoyez le code", "envoyer le code", "fournissez", "communiquez")) assertFalse(bad in t.lowercase(), "« $bad » dans « $t »")
    }

    @Test fun `no way's text mentions sender or receiver, only the product names`() {
        val all = P.plan(P.Facts(phoneLinked = true, usb = keyUnusable, group = P.Group.Failed(WifiDirect.Err.UNSUPPORTED), lanIps = lan)).flatMap { listOf(it.title, it.status) } +
            P.plan(P.Facts(group = P.Group.Offered, lanIps = lan)).flatMap { listOf(it.title, it.status) } +
            listOf(P.phoneInstruction("482913", true), P.phoneInstruction("482913", false), P.phoneInstruction("482913", false, sameWifi = true))
        for (t in all) assertFalse("sender" in t.lowercase() || "receiver" in t.lowercase(), t)
    }

    // ---- a TV on a Wi-Fi network (act-tv-2): the group is offered, not made ----

    @Test fun `a group kept for later says the TV waits for a phone on the same Wi-Fi, with the TV's address`() {
        val s = phoneStatus(P.Facts(group = P.Group.Offered, lanIps = lan))
        assertTrue("même Wi-Fi" in s.status && "192.168.1.20" in s.status, s.status)
        assertFalse("Réseau direct" in s.status, "no group exists yet: the line does not claim one")
        assertEquals(LineTone.INFO, s.tone)
        assertTrue("même Wi-Fi" in phoneStatus(P.Facts(group = P.Group.Offered)).status, "the sentence holds without an address too")
        assertFalse("Bluetooth" in phoneStatus(P.Facts(group = P.Group.Offered, lanIps = lan)).status)
    }

    @Test fun `the sentence under the code says same Wi-Fi when there is no QR because the group is offered, and the QR sentence wins when a QR is drawn`() {
        assertEquals("Téléphone sur le même Wi-Fi : tapez le code dans CastBridge › Activer la TV", P.phoneInstruction("482913", qrShown = false, sameWifi = true))
        assertTrue("Scannez" in P.phoneInstruction("482913", qrShown = true, sameWifi = true), "a QR on screen: the QR sentence")
        assertEquals(P.phoneInstruction("482913", qrShown = false), P.phoneInstruction("482913", qrShown = false, sameWifi = false))
        assertTrue("Scannez" !in P.phoneInstruction("482913", qrShown = false, sameWifi = true))
    }

    private val code = "482913"
    private val everyGroup = listOf(P.Group.NotTried, P.Group.Offered, P.Group.Starting, P.Group.Ready, P.Group.Failed(WifiDirect.Err.FAILED), P.Group.Failed(null))

    @Test fun `the QR is drawn only while the group exists, the code alone otherwise`() {
        for (g in everyGroup) {
            val v = P.phoneView(code, g)
            assertEquals(g is P.Group.Ready, v.qr, "$g")
            assertEquals("482 913", v.code, "the code stays, large, whatever the group: $g")
        }
        assertFalse(P.phoneView("12345", P.Group.Ready).qr, "no valid code, no group to describe")
        assertFalse(P.phoneView("48291a", P.Group.Ready).qr)
        assertTrue(P.phoneView("007042", P.Group.Ready).qr)
    }

    @Test fun `the direct network is offered with its warning only while the group is kept for later`() {
        val offer = P.DirectOffer("Le téléphone n'est pas sur ce Wi-Fi ? OK : réseau direct", "Le Wi-Fi de la TV peut se couper le temps de l'activation.")
        assertEquals(offer, P.phoneView(code, P.Group.Offered).offer)
        assertEquals(offer, P.directOffer(P.Group.Offered))
        for (g in everyGroup.filter { it != P.Group.Offered }) {
            assertEquals(null, P.phoneView(code, g).offer, "$g")
            assertEquals(null, P.directOffer(g), "$g")
        }
    }

    @Test fun `each state of the group has its sentence`() {
        assertEquals("Téléphone sur le même Wi-Fi : tapez le code dans CastBridge › Activer la TV", P.phoneView(code, P.Group.Offered).instruction)
        assertEquals(P.phoneInstruction(code, qrShown = true), P.phoneView(code, P.Group.Ready).instruction)
        for (g in listOf(P.Group.NotTried, P.Group.Starting, P.Group.Failed(WifiDirect.Err.WIFI_OFF))) {
            val i = P.phoneView(code, g).instruction
            assertEquals(P.phoneInstruction(code, qrShown = false), i, "$g")
            assertTrue("Scannez" !in i && "même Wi-Fi" !in i, i)
        }
    }

    @Test fun `the retry button shows only when the group failed`() {
        for (g in everyGroup) assertEquals(g is P.Group.Failed, P.phoneView(code, g).retry, "$g")
    }

    @Test fun `without a code (the locked route is not open yet) the phone way draws nothing but the plain sentence`() {
        for (g in everyGroup) {
            val v = P.phoneView(null, g)
            assertEquals(null, v.code, "$g")
            assertFalse(v.qr, "$g")
            assertEquals(null, v.offer, "an offer would ask for a group nothing can make yet: $g")
            assertFalse(v.retry, "$g")
            assertEquals("Sur votre téléphone, ouvrez CastBridge › Activer la TV.", v.instruction, "$g")
        }
    }

    @Test fun `the Wi-Fi line does not call the TV network-less while its own group is up, and never shows the group's address as the LAN one`() {
        assertEquals("Par le Wi-Fi : code de connexion 482915 · TV 192.168.1.20", LockedWifiTexts.line("482915", listOf("192.168.1.20")), "unchanged for a TV on a network")
        assertEquals(LockedWifiTexts.line("482915", listOf("192.168.1.20")), LockedWifiTexts.line("482915", listOf("192.168.1.20"), groupReady = true), "with a network the group is not mentioned: an older phone compares THIS address")
        val own = LockedWifiTexts.line("482915", emptyList(), groupReady = true)
        assertTrue("réseau direct" in own && "192.168.49.1" in own && "sans réseau" !in own, own)
        val none = LockedWifiTexts.line("482915", emptyList())
        assertTrue("sans réseau" in none && "Bluetooth" in none, none)
    }

    // ---- the sentence under the QR ----

    @Test fun `the sentence under the QR gives the code grouped three and three`() {
        assertEquals("Scannez avec l'appareil photo du téléphone, ou tapez le code 482 913 dans CastBridge › Activer la TV", P.phoneInstruction("482913", qrShown = true))
        val noQr = P.phoneInstruction("007042", qrShown = false)
        assertTrue("007 042" in noQr && "CastBridge › Activer la TV" in noQr && "Scannez" !in noQr, noQr)
        assertEquals("482 913", P.groupedCode("482913"))
    }

    // ---- « téléphone relié » ----

    @Test fun `a phone is linked while it is in the group or presented the right code a moment ago`() {
        val now = 1_000_000L
        assertFalse(P.phoneLinked(now, null, null))
        assertFalse(P.phoneLinked(now, null, 0))
        assertTrue(P.phoneLinked(now, null, 1), "a phone joined the Wi-Fi Direct group")
        assertTrue(P.phoneLinked(now, now - 1_000, null), "it presented the right code")
        assertTrue(P.phoneLinked(now, now - P.LINK_WINDOW_MS, 0))
        assertFalse(P.phoneLinked(now, now - P.LINK_WINDOW_MS - 1, 0), "a long-ago visit does not count")
        assertFalse(P.phoneLinked(now, 0L, null), "0 = never")
        assertFalse(P.phoneLinked(now, now + 5_000, null), "a clock that went back is not a link")
    }

    // ---- the QR ----

    @Test fun `the QR is at least a quarter of the screen height, with whole-pixel modules and a quiet zone`() {
        for (h in listOf(300, 360, 480, 540, 720, 1080, 1440, 2160)) for (modules in listOf(21, 25, 29, 33, 37, 41, 45, 57)) {
            val l = ActivationQr.layout(h, modules)
            val total = modules + 2 * ActivationQr.QUIET_MODULES
            assertTrue(l.modulePx >= 1, "h=$h modules=$modules")
            assertEquals(total * l.modulePx, l.sidePx, "whole pixels per module: crisp edges (h=$h modules=$modules)")
            assertEquals(ActivationQr.QUIET_MODULES * l.modulePx, l.quietPx)
            assertTrue(l.sidePx * 4 >= h, "side ${l.sidePx} < a quarter of $h (modules=$modules)")
            assertTrue(l.sidePx <= h / 2, "never more than half the screen (h=$h side=${l.sidePx})")
        }
        assertTrue(ActivationQr.layout(720, 33).sidePx in 180..400, "on the 720p reference screen: ${ActivationQr.layout(720, 33).sidePx}")
    }

    @Test fun `the QR carries the Wi-Fi URI of the code's group and nothing else`() {
        for (code in listOf("000000", "482913", "999999", "123456")) {
            assertEquals(WdCode.wifiUri(code), ActivationQr.uri(code))
            val qr = ActivationQr.encode(code)
            assertTrue(qr.version <= QrCode.MAX_VERSION)
            assertEquals(qr.version * 4 + 17, qr.size)
            assertTrue(ActivationQr.uri(code).startsWith("WIFI:T:WPA;S:DIRECT-CB-"), ActivationQr.uri(code))
            assertFalse(code in ActivationQr.uri(code), "the code itself is not in the QR: only what derives from it")
        }
    }
}
