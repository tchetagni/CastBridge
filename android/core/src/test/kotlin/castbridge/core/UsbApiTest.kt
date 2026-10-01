package castbridge.core

import kotlin.test.*

/** /api/storage `heavy` block (additive) and the « Contenus lourds sur la clé USB » setting, through the real server. */
class UsbApiTest {
    @Test fun heavyBlockIsAdditiveAndFollowsTheSetting() {
        val r = Rig()
        try {
            var j = r.tv.storage()
            assertTrue(j.contains("\"heavy\":{\"enabled\":true,\"where\":\"usb\",\"drive\":\"usb-1234\""), j)
            assertTrue(j.contains("\"readopt\":null") && j.contains("\"volumes\":["), "old fields remain")
            assertFalse(j.contains(r.root.path), "no absolute path is ever disclosed")
            assertEquals(200, r.call("POST", "/api/storage?heavyOnUsb=false").first)
            j = r.tv.storage(); assertTrue(j.contains("\"enabled\":false,\"where\":\"internal\""), j)
            r.call("POST", "/api/storage?heavyOnUsb=true")
            r.unplug()
            j = r.tv.storage(); assertTrue(j.contains("\"where\":\"internal\"") && j.contains("Aucune clé USB"), j)
        } finally { r.close() }
    }
    @Test fun drivePickMustBeARemovableDriveId() {
        val r = Rig()
        try {
            assertEquals(400, r.call("POST", "/api/storage?heavyDrive=../../etc").first)
            assertEquals(400, r.call("POST", "/api/storage?heavyDrive=internal").first)
            assertEquals(200, r.call("POST", "/api/storage?heavyDrive=usb-1234").first)
            assertTrue(r.tv.storage().contains("\"drive\":\"usb-1234\""))
        } finally { r.close() }
    }
}
