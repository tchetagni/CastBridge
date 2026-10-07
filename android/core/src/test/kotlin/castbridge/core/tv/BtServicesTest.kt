package castbridge.core.tv

import castbridge.core.gateway.Gw
import castbridge.core.owner.OwnerFrames
import castbridge.core.trust.RecoveryCandidates
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * R-28 (relay-R4, inventory I-1): the UUID `…0002` was the SSH tunnel's AND the Internet gateway's. The TV listens on both (the gateway at every start, the SSH when it is on), so a
 * phone asking for one could reach the other. The table of RFCOMM services ([BtProtocol.SERVICES]) is the only place where a UUID is given; these tests fail when two services
 * share a UUID, when a constant of the code is not in the table, or when a UUID of the CastBridge prefix is written anywhere else in the apps.
 */
class BtServicesTest {
    private val table = BtProtocol.SERVICES

    /** Every RFCOMM UUID that the code names with a constant, wherever the constant lives (the gateway's and the owner channel's are not in BtProtocol). */
    private val constantsOfTheCode = mapOf(
        "files" to BtProtocol.SERVICE_UUID, "ssh" to BtProtocol.SSH_SERVICE_UUID, "api" to BtProtocol.API_SERVICE_UUID, "api-v2" to BtProtocol.API_MUX_SERVICE_UUID,
        "owner" to OwnerFrames.SERVICE_UUID, "gateway" to Gw.SERVICE_UUID)

    @Test fun theInternetGatewayAndTheSshTunnelDoNotShareAnRfcommUuid() {
        assertNotEquals(BtProtocol.SSH_SERVICE_UUID, Gw.SERVICE_UUID, "a phone that asks for the SSH tunnel could reach the Internet gateway, and the other way round")
        assertEquals("7c5e3b9a-4d2f-4c61-9b0e-cb0000000007", Gw.SERVICE_UUID, "the gateway's own UUID is …0007 (documented in ADMIN.md, BT-PLUG-AND-PLAY.md)")
        assertEquals("7c5e3b9a-4d2f-4c61-9b0e-cb0000000002", BtProtocol.SSH_SERVICE_UUID, "the SSH tunnel keeps …0002: tools/bt-ssh-bridge.py, tools/cbt-rfcomm and every phone in the field ask for it")
    }

    @Test fun noTwoRfcommServicesShareAUuidOrANumber() {
        val byUuid = table.groupBy({ it.uuid }, { it.sdpName }).filterValues { it.size > 1 }
        assertEquals(emptyMap(), byUuid, "services on the same UUID: $byUuid")
        val byNumber = table.groupBy({ it.number }, { it.sdpName }).filterValues { it.size > 1 }
        assertEquals(emptyMap(), byNumber, "services with the same number: $byNumber")
        assertEquals(table.size, table.map { it.sdpName }.toSet().size, "two services with the same name: the SDP record would be ambiguous in a listing")
    }

    @Test fun everyUuidIsTheCastBridgePrefixPlusItsNumber() {
        for (s in table) assertEquals(RecoveryCandidates.UUID_PREFIX + "%02d".format(s.number), s.uuid, "$s : the UUID does not match its number (a phone finds a CastBridge TV by that prefix)")
    }

    @Test fun theConstantsOfTheCodeAreExactlyTheTable() {
        assertEquals(constantsOfTheCode.values.toSet(), table.map { it.uuid }.toSet(), "a service constant is not in BtProtocol.SERVICES, or the table lists a UUID no constant names")
        assertEquals(constantsOfTheCode.size, constantsOfTheCode.values.toSet().size, "two constants name the same UUID: ${constantsOfTheCode.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }}")
    }

    @Test fun aNumberReservedByADesignIsNotGivenToAnotherService() {
        val clash = table.filter { it.number in BtProtocol.RESERVED_NUMBERS }
        assertEquals(emptyList(), clash, "numbers reserved for a design (W7 sync = 6): $clash")
        assertEquals(setOf(6), BtProtocol.RESERVED_NUMBERS)
    }

    @Test fun theOldGatewayUuidIsTheSshTunnelsAndStaysOutOfTheTableAsAGateway() {
        assertEquals(BtProtocol.SSH_SERVICE_UUID, Gw.LEGACY_SERVICE_UUID, "the legacy gateway UUID is the one the gateway shared with the SSH tunnel before R-28")
        assertEquals(listOf("CastBridge SSH"), table.filter { it.uuid == Gw.LEGACY_SERVICE_UUID }.map { it.sdpName }, "in the table that UUID is the SSH tunnel's, nobody else's")
    }

    // ------------------------------------------------------------------ test of the sources

    private val appModules = listOf("core", "sender", "receiver", "owner", "ownerlib", "devbridge", "sshd")
    private fun sources(): List<File> = appModules.flatMap { m ->
        File("../$m/src/main").takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile && it.extension in setOf("kt", "java") }?.toList().orEmpty()
    }

    @Test fun aCastBridgeServiceUuidIsWrittenOnlyInTheTableAndTheOwnerChannel() {
        val files = sources()
        assertTrue(files.any { it.name == "BtProtocol.kt" } && files.any { it.name == "BtGatewayHost.kt" }, "sources not found from ${File(".").absolutePath}")
        val whole = Regex(Regex.escape(RecoveryCandidates.UUID_PREFIX) + "[0-9a-fA-F]{2}")       // a complete service UUID, not the prefix alone
        val where = files.filter { whole.containsMatchIn(it.readText()) }.map { it.name }.sorted()
        assertEquals(listOf("BtProtocol.kt", "OwnerFrames.kt"), where, "a service UUID written outside the table: name it through BtProtocol.SERVICES (R-28)")
    }

    @Test fun everyServiceNameOfTheTableIsRegisteredByTheTv() {
        val receiver = sources().filter { "/receiver/" in it.path.replace('\\', '/') }.joinToString("\n") { it.readText() }
        assertTrue(receiver.isNotEmpty(), "receiver sources not found")
        for (s in table) assertTrue("\"${s.sdpName}\"" in receiver, "$s : no listenUsingRfcommWithServiceRecord with that name in the receiver (the table drifted from the code)")
    }
}
