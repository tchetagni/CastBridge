package castbridge.core.btact

import castbridge.core.owner.DeviceRequestText
import castbridge.core.tv.PinGuard
import castbridge.core.tv.activation.ActivationAttemptGate
import castbridge.core.tv.activation.LockedActivationApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.*

/**
 * « Activer par Bluetooth sans appairage » de bout en bout, sur deux flux en mémoire : le VRAI client du téléphone contre le VRAI serveur de la TV (`BtActServer`), la PAKE sur le code, les deux
 * confirmations, le canal chiffré, puis les deux messages de la route HTTP (la demande d'appareil complète, la clé). Écrits avant le code. Cas : succès, mauvais code, rejeu d'une session
 * enregistrée, troncature à chaque octet, un bit retourné à chaque octet, 5 refus ⇒ verrou, plafond global partagé avec la route HTTP, TV qui ne connaît pas le code, trames rejouées, déplacées,
 * supprimées. (docs/BT-PLUG-AND-PLAY.md, DESIGN-ACTIVATION-SIMPLE § 7)
 */
class BtActProtocolTest {
    private val code = "482913"
    private val peerKey = ActivationAttemptGate.bluetoothPeer(FakeTv.PEER)

    /** How many wrong codes [guard] holds for [key] (the peer must not be locked yet): it records probes until the lock, then forgets them. */
    private fun countedFailures(guard: PinGuard, key: String): Int {
        var bad = 0
        while (guard.recordFailure(key) != PinGuard.Result.LOCKED) bad++
        return 4 - bad                                                                 // the 5th failure locks: c recorded + (bad + 1) probes = 5
    }

    // ------------------------------------------------------------------ le succès

    @Test fun theRightCodeReadsTheRequestAndInstallsTheKeyThroughTheEncryptedChannel() {
        val tv = FakeTv()
        val r = live(tv, code)
        val ready = assertIs<BtActClient.Connect.Ready>(r.connect)
        assertEquals("CastBridge TV salon", ready.session.tvName); assertEquals("0.14.47-test", ready.session.tvVersion)
        assertEquals(DeviceRequestText.complete(FakeTv.fullRequest), assertIs<BtActClient.Session.Request.Text>(r.read).text, "la demande COMPLÈTE de la route HTTP, reconstruite : install= et install_sig= comprises")
        assertTrue("install=x25519|" in (r.read as BtActClient.Session.Request.Text).text)
        assertEquals("Licence 1", assertIs<BtActClient.Session.Installed.Accepted>(r.installed).label)
        assertEquals(listOf(FakeTv.GOOD_KEY), tv.installed, "la TV a reçu exactement la clé, au texte près")
        assertEquals(listOf(peerKey), tv.authorized, "« téléphone relié » : le pair, jamais le code")
        assertEquals(BtActServer.End.SERVED, r.end)
        assertFalse(tv.guard.isLocked(peerKey))
    }

    @Test fun aRejectedKeyIsToldWithTheTvsReasonAndAFourthTryIsNotServedOnTheSameLink() {
        val tv = FakeTv()
        val c2s = Pipe(); val s2c = Pipe()
        var end: BtActServer.End? = null
        val t = Thread { end = tv.server.serve(c2s.input, s2c.output, FakeTv.PEER); s2c.closeWrite() }.apply { isDaemon = true; start() }
        val s = (BtActClient(SeqEntropy(5000)).connect(s2c.input, c2s.output, code) as BtActClient.Connect.Ready).session
        repeat(3) { assertEquals("Cette clé n'est pas celle de cette TV", assertIs<BtActClient.Session.Installed.Rejected>(s.install("WRONG-$it")).message) }
        assertIs<BtActClient.Session.Installed.Lost>(s.install(FakeTv.GOOD_KEY), "trois clés refusées : la TV raccroche, comme le canal propriétaire")
        t.join(10_000); assertEquals(BtActServer.End.SERVED, end)
        assertEquals(listOf("WRONG-0", "WRONG-1", "WRONG-2"), tv.installed, "la 4e clé n'a jamais atteint le vérificateur")
    }

    @Test fun theRequestMayBeUnavailableAndTheClientSaysSo() {
        val tv = FakeTv(requestText = null)
        val r = live(tv, code, key = null)
        assertIs<BtActClient.Session.Request.Unavailable>(r.read)
        val tv2 = FakeTv(requestText = "pas une demande d'appareil")
        assertIs<BtActClient.Session.Request.Unavailable>(live(tv2, code, key = null).read, "un texte illisible n'est jamais recopié : rien")
    }

    // ------------------------------------------------------------------ rien du code, de la demande ni de la clé ne passe en clair

    @Test fun neitherTheCodeNorTheRequestNorTheKeyIsOnTheLinkInClear() {
        val r = live(FakeTv(), code)
        for ((name, bytes) in listOf("téléphone → TV" to r.c2s, "TV → téléphone" to r.s2c)) {
            val s = String(bytes, Charsets.ISO_8859_1)
            for (secret in listOf(code, FakeTv.GOOD_KEY, "factor=", "code=", "install=", "Licence 1", "CastBridge TV salon")) assertFalse(secret in s, "$name : « $secret » passe en clair")
        }
        val tvToPhone = r.s2c
        assertTrue(r.c2s.size in 100..400 && tvToPhone.size in 300..900, "des tailles plausibles : ${r.c2s.size} / ${tvToPhone.size}")
    }

    @Test fun theCodeIsNotInAnyToStringOfTheClientsOutcomes() {
        val ready = live(FakeTv(), code, key = null).connect
        for (o in listOf(ready, BtActClient.Connect.Refused(BtActWire.Err.BAD_CODE), BtActClient.Connect.NotProved, BtActClient.Connect.LinkLost)) assertFalse(code in o.toString(), o.toString())
    }

    // ------------------------------------------------------------------ un mauvais code

    @Test fun aWrongCodeIsRefusedCountedAndNothingIsRead() {
        val tv = FakeTv()
        val r = live(tv, "111111")
        val refused = assertIs<BtActClient.Connect.Refused>(r.connect)
        assertEquals(BtActWire.Err.BAD_CODE, refused.err)
        assertEquals(BtActServer.End.WRONG_CODE, r.end)
        assertEquals(0, tv.reads, "la demande n'est pas construite pour un pair qui n'a pas prouvé le code")
        assertTrue(tv.installed.isEmpty() && tv.authorized.isEmpty())
        assertEquals(1, countedFailures(tv.guard, peerKey), "exactement un code faux est compté pour ce pair")
    }

    @Test fun fiveWrongCodesLockThePeerAndTheRightCodeIsRefusedWhileLocked() {
        var now = 1_000_000L
        val tv = FakeTv(clock = { now })
        for (i in 1..4) assertEquals(BtActWire.Err.BAD_CODE, assertIs<BtActClient.Connect.Refused>(live(tv, "11111$i").connect).err, "essai $i")
        val fifth = assertIs<BtActClient.Connect.Refused>(live(tv, "111115").connect)
        assertEquals(BtActWire.Err.LOCKED, fifth.err, "le cinquième code faux verrouille le pair : la TV le dit tout de suite")
        assertEquals(60L, fifth.seconds)
        val sixth = live(tv, code)                                                                    // le BON code, pendant le verrou
        assertEquals(BtActWire.Err.LOCKED, assertIs<BtActClient.Connect.Refused>(sixth.connect).err)
        assertEquals(BtActServer.End.TURNED_AWAY, sixth.end, "renvoyé avant tout calcul")
        assertTrue(tv.authorized.isEmpty() && tv.installed.isEmpty())
        now += 59_000
        assertEquals(BtActWire.Err.LOCKED, assertIs<BtActClient.Connect.Refused>(live(tv, code).connect).err, "toujours verrouillé à 59 s")
        now += 2_000
        assertIs<BtActClient.Connect.Ready>(live(tv, code, key = null).connect, "le verrou est levé après 60 s")
    }

    @Test fun theLockOfOnePeerDoesNotTouchAnother() {
        val tv = FakeTv()
        repeat(5) { live(tv, "11111$it") }
        assertEquals(BtActWire.Err.LOCKED, assertIs<BtActClient.Connect.Refused>(live(tv, code).connect).err)
        assertIs<BtActClient.Connect.Ready>(live(tv, code, peer = "AA:BB:CC:DD:EE:02", key = null).connect, "un autre téléphone n'est pas verrouillé")
    }

    @Test fun theRightCodeForgetsThePeersEarlierFailures() {
        val tv = FakeTv()
        repeat(4) { live(tv, "11111$it") }
        assertIs<BtActClient.Connect.Ready>(live(tv, code, key = null).connect)
        repeat(4) { assertEquals(BtActWire.Err.BAD_CODE, assertIs<BtActClient.Connect.Refused>(live(tv, "22222$it").connect).err) }          // 4 de plus : pas de verrou, le compteur est reparti de zéro
    }

    // ------------------------------------------------------------------ le plafond global, partagé avec la route HTTP

    @Test fun twentyWrongCodesFromEverybodyCloseTheWayAndTheWindowSlidesBack() {
        var now = 1_000_000L
        val tv = FakeTv(clock = { now })
        for (peer in 1..5) repeat(4) { live(tv, "33333$it", peer = "AA:BB:CC:DD:EE:%02X".format(peer)) }       // 5 pairs × 4 : aucun verrouillé, 20 codes faux au total
        val closed = live(tv, code, peer = "AA:BB:CC:DD:EE:09")
        assertEquals(BtActWire.Err.CLOSED, assertIs<BtActClient.Connect.Refused>(closed.connect).err, "même le bon code, d'un pair neuf : fermé pour tous")
        assertEquals(BtActServer.End.TURNED_AWAY, closed.end)
        now += ActivationAttemptGate.WINDOW_MS - 1
        assertEquals(BtActWire.Err.CLOSED, assertIs<BtActClient.Connect.Refused>(live(tv, code, peer = "AA:BB:CC:DD:EE:09").connect).err)
        now += 2
        assertIs<BtActClient.Connect.Ready>(live(tv, code, peer = "AA:BB:CC:DD:EE:09", key = null).connect, "la fenêtre de 10 minutes a glissé")
    }

    @Test fun theBudgetOfWrongCodesIsSharedWithTheHttpRouteBothWays() {
        var now = 1_000_000L
        val tv = FakeTv(clock = { now })
        val api = LockedActivationApi(tv.guard, { LockedActivationApi.Install.Rejected("non") }, { true }, "t", now = { now }, deviceRequest = { FakeTv.fullRequest }, gate = tv.gate)
        fun http(ip: String, pin: String) = api.handle(LockedActivationApi.Request("GET", LockedActivationApi.DEVICE_REQUEST_PATH, ip, "192.168.1.20:8765", pin, null, null)) { null }
        for (i in 1..10) assertEquals(401, http("192.168.1.%d".format(40 + i), "000000").status)               // 10 codes faux par le Wi-Fi, un par adresse
        for (peer in 1..5) repeat(2) { live(tv, "44444$it", peer = "AA:BB:CC:DD:EE:%02X".format(peer)) }          // 10 par Bluetooth
        assertEquals(429, http("192.168.1.99", code).status, "la route HTTP est fermée par les codes faux du Bluetooth")
        assertEquals(BtActWire.Err.CLOSED, assertIs<BtActClient.Connect.Refused>(live(tv, code, peer = "AA:BB:CC:DD:EE:0A").connect).err, "… et le Bluetooth par ceux du Wi-Fi")
    }

    @Test fun aLockedPeerIsNotCountedAgainstTheGlobalBudget() {
        val tv = FakeTv()
        repeat(5) { live(tv, "55555$it") }                                                         // verrouille le pair : 5 codes faux comptés
        repeat(30) { assertEquals(BtActWire.Err.LOCKED, assertIs<BtActClient.Connect.Refused>(live(tv, code).connect).err) }   // 30 essais pendant le verrou : rien n'est vérifié, rien n'est compté
        assertIs<BtActClient.Connect.Ready>(live(tv, code, peer = "AA:BB:CC:DD:EE:03", key = null).connect, "un pair verrouillé qui insiste ne ferme pas la voie pour les autres")
    }

    // ------------------------------------------------------------------ les conditions d'usage d'abord, la version, ce qui n'est pas notre protocole

    @Test fun theTermsComeBeforeTheCodeAndTellNothingAboutIt() {
        val tv = FakeTv(terms = false)
        for (c in listOf(code, "111111")) {
            val r = live(tv, c)
            assertEquals(BtActWire.Err.TERMS, assertIs<BtActClient.Connect.Refused>(r.connect).err, "le bon code et le mauvais reçoivent la même réponse")
            assertEquals(BtActServer.End.TERMS, r.end)
        }
        assertFalse(tv.guard.isLocked(peerKey)); assertEquals(0, tv.reads)
        repeat(6) { live(tv, "222222") }
        tv.terms = true
        assertIs<BtActClient.Connect.Ready>(live(tv, code, key = null).connect, "les essais faits avant l'acceptation n'ont rien compté")
    }

    @Test fun aTvWithoutACodeIsUnavailable() {
        val r = live(FakeTv(code = null), code)
        assertEquals(BtActWire.Err.UNAVAILABLE, assertIs<BtActClient.Connect.Refused>(r.connect).err)
        assertEquals(BtActServer.End.UNAVAILABLE, r.end)
    }

    @Test fun anotherVersionAndAnotherProtocolAreTurnedAwayWithoutAComputation() {
        val tv = FakeTv()
        val v = ByteArrayOutputStream()
        DataOutputStream(v).run { write(BtActWire.MAGIC.toByteArray()); BtActWire.writeClear(this, BtActWire.Msg.HELLO, byteArrayOf(9) + ByteArray(48)) }
        val (end, reply) = serveBytes(tv, v.toByteArray())
        assertEquals(BtActServer.End.VERSION, end)
        assertEquals(BtActWire.Err.VERSION, BtActWire.parseError(BtActWire.readClear(DataInputStream(ByteArrayInputStream(reply))))!!.first)
        for (garbage in listOf("GET / HTTP/1.1\r\n\r\n".toByteArray(), "CBT1".toByteArray() + ByteArray(40), ByteArray(0), ByteArray(3))) {
            val (e, out) = serveBytes(tv, garbage)
            assertTrue(e == BtActServer.End.NOT_OURS || e == BtActServer.End.LINK_LOST, "$e")
            assertEquals(0, out.size, "un autre protocole ne reçoit rien")
        }
        assertFalse(tv.guard.isLocked(peerKey))
    }

    @Test fun aHelloOfTheWrongSizeOrTypeIsRefusedAndNeverAllocated() {
        val tv = FakeTv()
        for (bad in listOf(byteArrayOf(0x01) + ByteArray(10), byteArrayOf(0x55) + ByteArray(49))) {
            val b = ByteArrayOutputStream(); DataOutputStream(b).run { write(BtActWire.MAGIC.toByteArray()); BtActWire.writeClear(this, bad[0].toInt(), bad.copyOfRange(1, bad.size)) }
            assertEquals(BtActServer.End.PROTOCOL, serveBytes(tv, b.toByteArray()).first)
        }
        val huge = ByteArrayOutputStream(); DataOutputStream(huge).run { write(BtActWire.MAGIC.toByteArray()); writeShort(60_000) }
        assertEquals(BtActServer.End.PROTOCOL, serveBytes(tv, huge.toByteArray()).first, "une longueur démesurée est refusée avant toute lecture")
    }

    @Test fun aPointOfSmallOrderIsRefusedAndCountedAsAWrongCode() {
        val tv = FakeTv()
        val sid = ByteArray(16) { it.toByte() }
        for (bad in listOf(ByteArray(32), ByteArray(32).also { it[0] = 1 })) {
            val b = ByteArrayOutputStream(); DataOutputStream(b).run { write(BtActWire.MAGIC.toByteArray()); BtActWire.writeClear(this, BtActWire.Msg.HELLO, byteArrayOf(BtActWire.VERSION.toByte()) + sid + bad) }
            assertEquals(BtActServer.End.PROTOCOL, serveBytes(tv, b.toByteArray()).first)
        }
        assertFalse(tv.guard.isLocked(peerKey), "deux points hostiles : deux codes faux, pas encore de verrou")
        repeat(3) { val b = ByteArrayOutputStream(); DataOutputStream(b).run { write(BtActWire.MAGIC.toByteArray()); BtActWire.writeClear(this, BtActWire.Msg.HELLO, byteArrayOf(BtActWire.VERSION.toByte()) + sid + ByteArray(32)) }; serveBytes(tv, b.toByteArray()) }
        assertTrue(tv.guard.isLocked(peerKey), "après cinq tentatives hostiles le pair est verrouillé")
    }

    // ------------------------------------------------------------------ une session enregistrée : rejeu, troncature, bit retourné

    private val recorded: LiveRun by lazy { live(FakeTv(), code) }

    /** The layout of what the TV wrote, to know which frame a byte belongs to. */
    private val welcomeFrame = 2 + 1 + "tv=CastBridge TV salon\nv=0.14.47-test\n".toByteArray().size + 16
    private val requestFrame = 2 + 1 + DeviceRequestText.complete(FakeTv.fullRequest)!!.toByteArray().size + 16
    private val installedFrame = 2 + 1 + "Licence 1".length + 16
    private val acceptedEnd = 35 + 35 + welcomeFrame + requestFrame + installedFrame             // REPLY + CONFIRM_TV + WELCOME + REQUEST + INSTALLED
    private val finFrame = 2 + 1 + 16
    private val handshakeBytes = 4 + 52 + 35                                                          // magic + HELLO + CONFIRM
    private val readFrame = 2 + 1 + 16
    private val installFrame = 2 + 1 + FakeTv.GOOD_KEY.length + 16

    @Test fun theLayoutOfTheRecordedSessionIsWhatTheTestsAssume() {
        assertEquals(handshakeBytes + readFrame + installFrame + finFrame, recorded.c2s.size)
        assertEquals(acceptedEnd + finFrame, recorded.s2c.size)
    }

    @Test fun aRecordedSessionReplayedToAFreshTvIsRefusedAndCounted() {
        val tv = FakeTv(serverSeed = 7000)                                                           // a new TV session: a new secret, a new point
        val (end, reply) = serveBytes(tv, recorded.c2s)
        assertEquals(BtActServer.End.WRONG_CODE, end)
        assertTrue(tv.installed.isEmpty() && tv.authorized.isEmpty() && tv.reads == 0)
        assertEquals(BtActWire.Err.BAD_CODE, BtActWire.parseError(BtActWire.readClear(DataInputStream(ByteArrayInputStream(reply.copyOfRange(35, reply.size)))))!!.first, "après sa réponse REPLY de 35 octets, la TV refuse")
        assertEquals(1, countedFailures(tv.guard, peerKey), "le rejeu a coûté exactement un code faux")
    }

    @Test fun theSameRecordedBytesAreServedByTheSameTvSessionAndNotByAnotherOne() {
        val same = FakeTv(serverSeed = 1000)
        assertEquals(BtActServer.End.SERVED, serveBytes(same, recorded.c2s).first, "la même session rejouée à l'identique : le test de troncature se tient")
        assertEquals(listOf(FakeTv.GOOD_KEY), same.installed)
    }

    @Test fun cuttingTheLinkAtEveryByteTheTvNeverActsOnAHalfFrameAndNeverCountsAWrongCode() {
        val installAt = handshakeBytes + readFrame + installFrame
        for (n in 0..recorded.c2s.size) {
            val tv = FakeTv(serverSeed = 1000)                                                       // a fresh gate each time: the allowances of a peer are not what is tested here
            val (end, _) = serveBytes(tv, recorded.c2s.copyOf(n))
            if (n < installAt) assertTrue(tv.installed.isEmpty(), "n=$n : la clé n'est jamais installée avant que sa trame soit complète")
            else assertEquals(listOf(FakeTv.GOOD_KEY), tv.installed, "n=$n")
            if (n < recorded.c2s.size) assertNotEquals(BtActServer.End.WRONG_CODE, end, "n=$n : un lien coupé n'est pas un code faux")
            if (n < handshakeBytes + readFrame) assertEquals(0, tv.reads, "n=$n")
            assertEquals(0, countedFailures(tv.guard, peerKey), "n=$n : une coupure ne compte aucun code faux")
            assertFalse(tv.gate.globalClosed())
        }
    }

    @Test fun flippingAnyBitOfWhatThePhoneSendsNeverGetsAKeyInstalledBeforeItsFrameIsIntact() {
        val installEnd = handshakeBytes + readFrame + installFrame
        for (i in 0 until recorded.c2s.size) for (bit in listOf(0, 7)) {
            val tampered = recorded.c2s.copyOf().also { it[i] = (it[i].toInt() xor (1 shl bit)).toByte() }
            val tv = FakeTv(serverSeed = 1000)
            val (end, _) = serveBytes(tv, tampered)
            if (i < installEnd) {
                assertTrue(tv.installed.isEmpty(), "octet $i bit $bit : rien n'est installé quand une trame avant la clé est altérée")
                assertNotEquals(BtActServer.End.SERVED, end, "octet $i bit $bit")
            } else assertEquals(listOf(FakeTv.GOOD_KEY), tv.installed, "octet $i : la clé était déjà arrivée intacte")
        }
    }

    @Test fun anAlteredLengthPrefixOfAnEncryptedFrameIsRefusedBeforeItIsRead() {
        // the length is part of the additional data: even when the altered length still fits, the tag does not verify
        val tv = FakeTv(serverSeed = 1000)
        val tampered = recorded.c2s.copyOf().also { it[handshakeBytes + 1] = (it[handshakeBytes + 1].toInt() + 1).toByte() }
        val (end, _) = serveBytes(tv, tampered)
        assertEquals(BtActServer.End.PROTOCOL, end)
        assertTrue(tv.installed.isEmpty() && tv.reads == 0)
    }

    // ------------------------------------------------------------------ côté téléphone : ce que dit une TV coupée, altérée, ou qui ne connaît pas le code

    @Test fun cuttingWhatTheTvSendsAtEveryByteTheClientNeverReportsASuccessItDidNotHear() {
        for (n in 0..recorded.s2c.size) {
            val (connect, read, installed) = clientOnBytes(recorded.s2c.copyOf(n), code)
            val ready = connect is BtActClient.Connect.Ready
            assertEquals(n >= 35 + 35 + welcomeFrame, ready, "n=$n : « prête » seulement quand la TV a prouvé le code et dit bonjour, en entier")
            if (n < acceptedEnd) assertFalse(installed is BtActClient.Session.Installed.Accepted, "n=$n : jamais « acceptée » sans avoir entendu la TV")
            if (n >= acceptedEnd) assertIs<BtActClient.Session.Installed.Accepted>(installed, "n=$n")
            if (ready && n < 35 + 35 + welcomeFrame + requestFrame) assertFalse(read is BtActClient.Session.Request.Text, "n=$n")
        }
    }

    @Test fun flippingAnyBitOfWhatTheTvSendsNeverMakesTheClientBelieveSomethingFalse() {
        for (i in 0 until acceptedEnd) {
            val tampered = recorded.s2c.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            val (connect, read, installed) = clientOnBytes(tampered, code)
            assertFalse(installed is BtActClient.Session.Installed.Accepted, "octet $i : une trame altérée ne donne jamais « acceptée »")
            if (i < 35 + 35) assertTrue(connect !is BtActClient.Connect.Ready, "octet $i : une confirmation altérée n'ouvre pas le canal")
            if (i < 35 + 35 + welcomeFrame + requestFrame && i >= 35 + 35) assertFalse(read is BtActClient.Session.Request.Text, "octet $i")
        }
    }

    @Test fun aTvThatDoesNotKnowTheCodeIsNotTrustedAndNothingIsSentToIt() {
        // a fake TV that follows the protocol with ANOTHER code: it answers, its confirmation does not match the phone's key
        val other = FakeTv(code = "999999", serverSeed = 9000)
        assertEquals(BtActWire.Err.BAD_CODE, assertIs<BtActClient.Connect.Refused>(live(other, code).connect).err, "une TV qui suit le protocole avec un AUTRE code refuse le téléphone")
        // and a rogue that REPLIES like the TV would after a right code, without knowing it: random point, random tag
        val c2s = Pipe()
        val rogue = ByteArrayOutputStream()
        DataOutputStream(rogue).run {
            BtActWire.writeClear(this, BtActWire.Msg.REPLY, SeqEntropy(1).bytes(32).also { it[31] = (it[31].toInt() and 0x7F).toByte() })
            BtActWire.writeClear(this, BtActWire.Msg.CONFIRM_TV, SeqEntropy(2).bytes(32))
        }
        val connect = BtActClient(SeqEntropy(5000)).connect(ByteArrayInputStream(rogue.toByteArray()), c2s.output, code)
        assertIs<BtActClient.Connect.NotProved>(connect, "sa confirmation ne prouve rien : le téléphone n'envoie ni la clé ni quoi que ce soit de plus")
        val sent = c2s.bytes()
        assertEquals(4 + 52 + 35, sent.size, "magic + HELLO + CONFIRM : le téléphone n'a envoyé que le début du protocole")
        assertFalse(FakeTv.GOOD_KEY in String(sent, Charsets.ISO_8859_1))
    }

    @Test fun theTvThatSendsAPointOfSmallOrderIsNotFollowed() {
        val rogue = ByteArrayOutputStream()
        DataOutputStream(rogue).run { BtActWire.writeClear(this, BtActWire.Msg.REPLY, ByteArray(32)) }
        assertIs<BtActClient.Connect.LinkLost>(BtActClient(SeqEntropy(5000)).connect(ByteArrayInputStream(rogue.toByteArray()), ByteArrayOutputStream(), code))
    }

    @Test fun aSilentOrGarbledTvIsALostLink() {
        assertIs<BtActClient.Connect.LinkLost>(BtActClient(SeqEntropy(5000)).connect(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), code))
        assertIs<BtActClient.Connect.LinkLost>(BtActClient(SeqEntropy(5000)).connect(ByteArrayInputStream("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()), ByteArrayOutputStream(), code))
        val wrongOrder = ByteArrayOutputStream().also { DataOutputStream(it).run { BtActWire.writeClear(this, BtActWire.Msg.CONFIRM_TV, ByteArray(32)) } }
        assertIs<BtActClient.Connect.LinkLost>(BtActClient(SeqEntropy(5000)).connect(ByteArrayInputStream(wrongOrder.toByteArray()), ByteArrayOutputStream(), code))
    }

    @Test fun theClientRefusesAMalformedCodeBeforeAnythingIsSent() {
        val out = ByteArrayOutputStream()
        for (bad in listOf("", "12345", "1234567", "12345a")) assertFailsWith<IllegalArgumentException>(bad) { BtActClient().connect(ByteArrayInputStream(ByteArray(0)), out, bad) }
        assertEquals(0, out.size())
    }

    @Test fun aKeyThatCannotFitIsRefusedByTheClientAndNeverSent() {
        val tv = FakeTv()
        val r = live(tv, code, key = "K".repeat(BtActWire.MAX_PAYLOAD + 1))
        assertIs<BtActClient.Session.Installed.Rejected>(r.installed)
        assertTrue(tv.installed.isEmpty())
        val ok = live(FakeTv(), code, key = "K".repeat(BtActWire.MAX_PAYLOAD))
        assertIs<BtActClient.Session.Installed.Rejected>(ok.installed, "16 Kio passent, la TV refuse la clé fausse avec sa raison")
    }

    // ------------------------------------------------------------------ des trames chiffrées rejouées, déplacées, supprimées

    /** A hand-made phone: the same primitives step by step, so a test can send what the real client never would. */
    private class Raw(val keys: BtActKeys, val channel: BtActChannel)

    private fun rawHandshake(tvIn: java.io.InputStream, tvOut: java.io.OutputStream, entropy: BtActWire.Entropy): Raw {
        val din = DataInputStream(tvIn); val dout = DataOutputStream(tvOut)
        val sid = entropy.bytes(16); val g = Cpace.generator(code.toByteArray(), BtActWire.CHANNEL_ID, sid); val secret = entropy.bytes(32); val ya = Cpace.message(secret, g)
        dout.write(BtActWire.MAGIC.toByteArray()); BtActWire.writeClear(dout, BtActWire.Msg.HELLO, byteArrayOf(BtActWire.VERSION.toByte()) + sid + ya)
        val yb = BtActWire.readClear(din).body
        val keys = BtActKeys.derive(Cpace.isk(sid, Cpace.sharedPoint(secret, yb)!!, ya, BtActWire.AD_PHONE, yb, BtActWire.AD_TV), sid, ya, yb)
        BtActWire.writeClear(dout, BtActWire.Msg.CONFIRM_PHONE, keys.tagPhone())
        assertEquals(BtActWire.Msg.CONFIRM_TV, BtActWire.readClear(din).type)
        val ch = BtActChannel(din, dout, keys.phoneToTv, keys.tvToPhone)
        assertEquals(BtActWire.Type.WELCOME, ch.receive().type)
        return Raw(keys, ch)
    }

    /** Frames the phone would send, sealed under the session's keys but NOT written to the link: [first] is frame 0 of that direction. */
    private fun frames(keys: BtActKeys, vararg types: Pair<Int, ByteArray>): List<ByteArray> {
        val out = ByteArrayOutputStream()
        val ch = BtActChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(out), keys.phoneToTv, keys.tvToPhone)
        return types.map { (t, p) -> val before = out.size(); ch.send(t, p); out.toByteArray().copyOfRange(before, out.size()) }
    }

    private fun session(tv: FakeTv, body: (Raw, Pipe, Pipe) -> Unit): BtActServer.End {
        val c2s = Pipe(); val s2c = Pipe()
        var end: BtActServer.End? = null
        val t = Thread { end = tv.server.serve(c2s.input, s2c.output, FakeTv.PEER); s2c.closeWrite() }.apply { isDaemon = true; start() }
        val raw = rawHandshake(s2c.input, c2s.output, SeqEntropy(5000))
        body(raw, c2s, s2c)
        c2s.closeWrite()
        t.join(10_000); assertFalse(t.isAlive)
        return end!!
    }

    @Test fun aFrameReplayedInTheSameSessionIsRefusedAndAnswersNothing() {
        val tv = FakeTv()
        var secondAnswer: BtActChannel.Frame? = null
        val end = session(tv) { raw, c2s, s2c ->
            val (readFrame0) = frames(raw.keys, BtActWire.Type.READ_REQUEST to ByteArray(0))
            c2s.output.write(readFrame0)
            assertEquals(BtActWire.Type.REQUEST, raw.channel.receive().type, "la première lecture est servie")
            c2s.output.write(readFrame0)                                                         // the very same bytes, sent again
            secondAnswer = runCatching { raw.channel.receive() }.getOrNull()
        }
        assertEquals(BtActServer.End.PROTOCOL, end)
        assertNull(secondAnswer, "la trame rejouée ne reçoit aucune réponse")
        assertEquals(1, tv.reads)
    }

    @Test fun framesDeliveredOutOfOrderOrWithAGapAreRefused() {
        for (order in listOf(listOf(1, 0), listOf(1), listOf(0, 0))) {
            val tv = FakeTv()
            val end = session(tv) { raw, c2s, _ ->
                val f = frames(raw.keys, BtActWire.Type.READ_REQUEST to ByteArray(0), BtActWire.Type.INSTALL to FakeTv.GOOD_KEY.toByteArray())
                order.forEach { c2s.output.write(f[it]) }
            }
            when (order) {
                listOf(0, 0) -> { assertEquals(BtActServer.End.PROTOCOL, end, "$order"); assertEquals(1, tv.reads) }
                else -> { assertEquals(BtActServer.End.PROTOCOL, end, "$order"); assertTrue(tv.installed.isEmpty() && tv.reads == 0, "$order : ni lecture ni installation") }
            }
        }
    }

    @Test fun aFrameOfTheTvReflectedBackToTheTvIsRefused() {
        val tv = FakeTv()
        val end = session(tv) { raw, c2s, s2c ->
            c2s.output.write(frames(raw.keys, BtActWire.Type.READ_REQUEST to ByteArray(0))[0])
            raw.channel.receive()                                                                // the TV's REQUEST frame
            val tvFrame = s2c.bytes().copyOfRange(s2c.bytes().size - requestFrame, s2c.bytes().size)
            c2s.output.write(tvFrame)                                                            // sent back as if it were the phone's: another key for that direction
        }
        assertEquals(BtActServer.End.PROTOCOL, end)
        assertTrue(tv.installed.isEmpty())
    }

    @Test fun aFrameOfAnotherSessionIsRefused() {
        var foreign: ByteArray? = null
        session(FakeTv(serverSeed = 1000)) { raw, _, _ -> foreign = frames(raw.keys, BtActWire.Type.INSTALL to FakeTv.GOOD_KEY.toByteArray())[0] }
        val tv = FakeTv(serverSeed = 2000)
        val end = session(tv) { _, c2s, _ -> c2s.output.write(foreign!!) }
        assertEquals(BtActServer.End.PROTOCOL, end, "la trame d'une autre session (un autre secret, d'autres clés) ne s'authentifie pas")
        assertTrue(tv.installed.isEmpty())
    }

    @Test fun anUnknownTypeOfEncryptedFrameEndsTheLink() {
        val tv = FakeTv()
        val end = session(tv) { raw, c2s, _ -> c2s.output.write(frames(raw.keys, 0x42 to ByteArray(0))[0]) }
        assertEquals(BtActServer.End.PROTOCOL, end)
    }

    // ------------------------------------------------------------------ les plafonds de la TV pour un pair

    @Test fun readsAndKeyVerificationsAreLimitedPerPeerLikeTheHttpRoute() {
        var now = 1_000_000L
        val tv = FakeTv(clock = { now })
        // 20 reads per peer per 10 minutes, 10 key verifications: the TV says « limite » (the HTTP route's 429), counted apart
        repeat(3) { i ->
            val r = live(tv, code, clientSeed = 5000 + i * 100, key = null)
            assertIs<BtActClient.Session.Request.Text>(r.read)
        }
        val gate = tv.gate
        repeat(17) { assertTrue(gate.takeRead(peerKey), "lecture ${it + 4}") }
        val limited = live(tv, code, clientSeed = 9000, key = null)
        assertIs<BtActClient.Session.Request.Limit>(limited.read, "la 21e lecture de la fenêtre est refusée")
        repeat(10) { assertTrue(gate.takeTry(peerKey)) }
        assertIs<BtActClient.Session.Installed.Limit>(live(tv, code, clientSeed = 9100, read = false).installed, "la 11e vérification de clé aussi")
        now += ActivationAttemptGate.WINDOW_MS + 1
        assertIs<BtActClient.Session.Request.Text>(live(tv, code, clientSeed = 9200, key = null).read, "la fenêtre a glissé")
    }

    @Test fun theAllowancesOfOnePeerDoNotUseUpAnothers() {
        val tv = FakeTv()
        repeat(ActivationAttemptGate.MAX_READS) { assertTrue(tv.gate.takeRead(peerKey)) }
        assertIs<BtActClient.Session.Request.Text>(live(tv, code, peer = "AA:BB:CC:DD:EE:05", key = null).read)
    }

    @Test fun twoPeerKeysNeverCollideWithAnIpAddress() {
        assertEquals("bt:AA:BB:CC:DD:EE:01", ActivationAttemptGate.bluetoothPeer("aa:bb:cc:dd:ee:01"))
        assertEquals("bt:?", ActivationAttemptGate.bluetoothPeer(null)); assertEquals("bt:?", ActivationAttemptGate.bluetoothPeer("  "))
        assertFalse(castbridge.core.ssh.Lan.isLocal(ActivationAttemptGate.bluetoothPeer("192.168.1.5")), "a Bluetooth peer key is never taken for an address of the local network")
    }
}
