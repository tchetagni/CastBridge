package castbridge.core.tunnel

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * R-32 (audit anti-régression 2026-10-07 b, I-10) : le HELLO de la liaison de confiance (`TvLink`), l'envoi Bluetooth (`BtUploadService`) et les ordres (`OrdersRuntime`) faisaient
 * `connect()` sans le verrou [BtConnectLock] que la télécommande, « Ouvrir sur la TV », le tuyau et le canal propriétaire prennent : une fois les récepteurs « appareil connecté » réellement
 * réveillés (I-9), trois d'entre eux partent sur le même lien ACL, et la pile Bluetooth répond « already at opened state » au second `connect()` simultané vers la même TV
 * (famille R-03 / R-19), ce qui fait aussi mémoriser à tort « TV ancienne » (I-8). Un seul `connect()` à la fois par TV : TOUT chemin d'ouverture d'un socket RFCOMM du téléphone passe par le verrou.
 */
class RfcommConnectLockSourceTest {
    /** Le module du téléphone, et `ownerlib` (le canal propriétaire : activation par Bluetooth, console) que le téléphone appelle. */
    private val sender = PhoneSources.kotlinFiles("sender") + PhoneSources.kotlinFiles("ownerlib")
    private val create = Regex("""\bcreate(?:Insecure)?RfcommSocketToServiceRecord\(""")
    private val connect = Regex("""\.connect\(\s*\)""")

    /** Les chemins qui ouvrent un socket RFCOMM, et ce qui les protège : chaque `connect()` qui suit est dans `synchronized(BtConnectLock.of(…))`, sauf la passerelle SSH/API, dont le `dial` est appelé par le `LinkPool` qui prend ce verrou. */
    private val poolLocked = setOf("BtSshGateway.kt")

    @Test fun theGuardSeesTheFilesItWatches() {
        val names = sender.map { it.name }.toSet()
        for (f in listOf("TvLink.kt", "BtUploadService.kt", "OrdersRuntime.kt", "RelayRuntime.kt", "BtGatewayService.kt", "RemoteController.kt", "OpenTv.kt", "BtSshGateway.kt", "TvBluetooth.kt"))
            assertTrue(f in names, "le test ne voit plus $f")
        assertTrue(sender.count { create.containsMatchIn(PhoneSources.stripComments(it.readText())) } >= 9, "le test ne reconnaît plus la création d'un socket RFCOMM")
    }

    @Test fun everyRfcommConnectOfThePhoneGoesThroughTheLockOfItsTv() {
        val offenders = mutableListOf<String>()
        for (f in sender) {
            if (f.name in poolLocked) continue
            val code = PhoneSources.stripComments(f.readText())
            for (m in create.findAll(code)) {
                // le premier connect() après la création du socket est celui de ce socket
                val rest = code.substring(m.range.last)
                val c = connect.find(rest)
                if (c == null) { offenders += "${f.name} : socket RFCOMM créé sans connect()"; continue }
                val lineStart = rest.lastIndexOf('\n', c.range.first) + 1
                val line = rest.substring(lineStart, rest.indexOf('\n', c.range.first).let { if (it < 0) rest.length else it })
                if (!Regex("""synchronized\(\s*(?:castbridge\.core\.tunnel\.)?BtConnectLock\.of\(""").containsMatchIn(line))
                    offenders += "${f.name}:${code.substring(0, m.range.first).count { it == '\n' } + 1} : connect() hors BtConnectLock : ${line.trim()}"
            }
        }
        assertEquals(emptyList(), offenders, "tout connect() RFCOMM vers une TV passe par synchronized(BtConnectLock.of(adresse)) { … }")
    }

    @Test fun theSshAndApiGatewayDialsAreCalledUnderTheSameLockByThePool() {
        val gw = PhoneSources.code("sender/src/main/kotlin/castbridge/sender/BtSshGateway.kt")
        assertTrue(Regex("""connectLock\s*=\s*(?:castbridge\.core\.tunnel\.)?BtConnectLock\.of\(""").containsMatchIn(gw), "BtSshGateway donne le verrou de la TV au TunnelGateway")
        val pool = PhoneSources.code("core/src/main/kotlin/castbridge/core/tunnel/LinkPool.kt")
        val locked = Regex("""synchronized\(connectLock\)\s*\{\s*dial(?:Shared|Legacy)\(\)""").findAll(pool).count()
        assertTrue(locked >= 3, "LinkPool prend connectLock autour de chaque dial ($locked trouvés)")
    }
}
