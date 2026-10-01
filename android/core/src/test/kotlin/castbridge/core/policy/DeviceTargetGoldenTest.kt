package castbridge.core.policy

import castbridge.core.owner.*
import kotlin.test.*

/** A device-targeted order built by the Kotlin core equals, byte for byte, the one the Java server builds (same golden string in OrderEnvelopeTest.java). */
class DeviceTargetGoldenTest {
    private val golden = "cbx1.Y2FzdGJyaWRnZS1lbnZlbG9wZS12MQp0eXBlPW9yZGVyCmtpZD1jZTIwMmRmZGJlNTdiOTlhCnNlcT03Cm5vbmNlPTAxMDIwMzA0MDUwNjA3MDgKaXNzdWVkQXQ9MTgwMDAwMDAwMDAwMApub3RCZWZvcmU9MTgwMDAwMDAwMDAwMApleHBpcmVzQXQ9MTgwMjU5MjAwMDAwMAp0YXJnZXQ9ZGV2aWNlCms9NApmYWN0b3I9RkxBU0h8YzQ4Y2FjZDgxYjk2NjNkMGJhOTdjMjIxY2JkMTdmNGYKZmFjdG9yPUVUSEVSTkVUfGY5MWVmZDg5ZTAwMjVjMjI2MmRhMTJjZmJlZDQzNjMyCmZhY3Rvcj1XSUZJfGVhOGI1NTJlMjI0MGJiZGQ4Yzg4YzQyMzEzYzRkZWFiCmZhY3Rvcj1TWVNURU1fU0VSSUFMfDc3NGJlYWNiZjM1MWIwZDIwNWY5MTY2NjY5ZTRkM2M3CmZhY3Rvcj1CTFVFVE9PVEh8ZDM2NWVhNjdlMDA1ODVlZGFmOGJlZjRkNzM3ODg1MGQKLS0KYWN0aW9uPWZsYWcuc2V0CnBhcmFtPW5hbWV8bGVhcm4uYmV0YQpwYXJhbT12YWx1ZXwx.FFwxZHf+FsiqluuOZ6Zxt8w/ayCTwF7suCDrEurolk32etrdYD5OczSLpxWX9kvcHmu7OzUje6JCRtTy5SR3BQ=="

    @Test fun deviceTargetedOrderIsByteIdenticalToTheServersOne() {
        val signer = Ed25519Signer(java.util.HexFormat.of().parseHex("ba644f4d626ec4d740001dda44c15a137227798345349498f5a7318958ebc5ad"))
        val f = mapOf(FactorKind.FLASH to "c48cacd81b9663d0ba97c221cbd17f4f", FactorKind.ETHERNET to "f91efd89e0025c2262da12cfbed43632", FactorKind.WIFI to "ea8b552e2240bbdd8c88c42313c4deab",
            FactorKind.SYSTEM_SERIAL to "774beacbf351b0d205f9166669e4d3c7", FactorKind.BLUETOOTH to "d365ea67e00585edaf8bef4d7378850d")
        assertEquals(golden, Orders.issue(signer, 7, "0102030405060708", 1800000000000, 1800000000000, 1802592000000, Envelope.Target.Device(4, f), "flag.set", mapOf("value" to "1", "name" to "learn.beta")))
    }
}
