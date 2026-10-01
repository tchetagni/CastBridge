package castbridge.core

import castbridge.core.tv.UsbHardware
import castbridge.core.tv.UsbNode
import castbridge.core.tv.UsbSysfs
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsbHardwareTest {
    // The values read on the reference TV (2026-10-01): a Kingston key and the TV's own Wi-Fi module on the same USB 2.0 controller.
    private val key = UsbNode("1-1", "0951", "1666", "Kingston", "DataTraveler 3.0", "E0D55E6CBD23E7B0F83A0113", 480, "00", "xhci-hcd-meson.0.auto", " 2.10", "300mA")
    private val wifi = UsbNode("1-2", "a69c", "8801", "aicsemi", "AIC Wlan", "20190227", 480, "00", "xhci-hcd-meson.0.auto")
    private val hub1 = UsbNode("usb1", "1d6b", "0002", "Linux xhci-hcd", "xHCI Host Controller", "xhci-hcd-meson.0.auto", 480, "09", "xhci-hcd-meson.0.auto")
    private val hub3 = UsbNode("usb3", "1d6b", "0002", "Linux xhci-hcd", "xHCI Host Controller", "xhci-hcd-meson.1.auto", 480, "09", "xhci-hcd-meson.1.auto")

    @Test fun referenceTvKeySharesItsBusWithTheWifiModule() {
        val i = assertNotNull(UsbHardware.analyze(listOf(hub1, key, wifi, hub3), "1-1"))
        assertEquals("0951:1666", i.vidPid); assertEquals("Kingston", i.manufacturer)
        assertEquals("E0D55E6CBD23E7B0F83A0113", i.serial)               // in full for the owner
        assertTrue(i.sharesBusWithWifi); assertEquals(listOf("AIC Wlan"), i.sharesBusWith)
        assertEquals("USB 2.0 (480 Mbit/s)", i.speedLabel)
        assertTrue(i.info().contains("Kingston DataTraveler 3.0") && i.info().contains("E0D55E6CBD23E7B0F83A0113"))
        val w = i.warnings()
        assertEquals(2, w.size, w.toString())
        assertTrue("même bus USB" in w[0] && "autre prise" in w[0]); assertTrue("annoncée USB 3" in w[1])
        assertTrue(i.details().any { it.first == "Numéro de série" && it.second == "E0D55E6CBD23E7B0F83A0113" })
    }

    @Test fun maskedSerialForSharing() {
        assertEquals("E0…0113", UsbHardware.maskSerial("E0D55E6CBD23E7B0F83A0113"))
        assertEquals("…56", UsbHardware.maskSerial("123456"))
        assertNull(UsbHardware.maskSerial("  "))
    }

    @Test fun otherControllerMeansNoSharingAndNoWarning() {
        val moved = key.copy(name = "3-1", controller = "xhci-hcd-meson.1.auto", speedMbps = 480)
        val i = assertNotNull(UsbHardware.analyze(listOf(hub1, wifi, hub3, moved), "3-1"))
        assertFalse(i.sharesBusWithWifi); assertTrue(i.sharesBusWith.isEmpty())
        assertTrue(i.warnings().none { "même bus" in it })
    }

    @Test fun superSpeedKeyOnAnUsb3PortHasNoSpeedWarning() {
        val fast = key.copy(speedMbps = 5000)
        val i = assertNotNull(UsbHardware.analyze(listOf(hub1, fast), "1-1"))
        assertEquals("USB 3 (5 Gbit/s)", i.speedLabel); assertTrue(i.warnings().isEmpty())
    }

    @Test fun unknownKeyGivesNull() = assertNull(UsbHardware.analyze(listOf(hub1, wifi), "9-9"))

    @Test fun majorMinorFromTheMountDevice() {
        assertEquals(8 to 1, UsbSysfs.majorMinor("/dev/block/vold/public:8,1"))
        assertNull(UsbSysfs.majorMinor("/dev/fuse"))
    }

    @Test fun readsAFakeSysfsTree() {
        val root = Files.createTempDirectory("sys").toFile()
        fun dev(name: String, vid: String, pid: String, man: String, prod: String, serial: String, speed: String, cls: String = "00") {
            val d = File(root, "devices/soc/xhci-hcd-meson.0.auto/usb1/$name").apply { mkdirs() }
            mapOf("idVendor" to vid, "idProduct" to pid, "manufacturer" to man, "product" to prod, "serial" to serial, "speed" to speed, "bDeviceClass" to cls, "version" to " 2.10", "bMaxPower" to "300mA")
                .forEach { (k, v) -> File(d, k).writeText(v + "\n") }
            File(root, "bus/usb/devices").mkdirs()
            Files.createSymbolicLink(File(root, "bus/usb/devices/$name").toPath(), d.toPath())
        }
        dev("usb1", "1d6b", "0002", "Linux xhci-hcd", "xHCI Host Controller", "xhci-hcd-meson.0.auto", "480", "09")
        dev("1-1", "0951", "1666", "Kingston", "DataTraveler 3.0", "E0D55E6CBD23E7B0F83A0113", "480")
        dev("1-2", "a69c", "8801", "aicsemi", "AIC Wlan", "20190227", "480")
        // block device 8:0 (disk) and 8:1 (partition) whose device link points under usb1/1-1
        val disk = File(root, "devices/soc/xhci-hcd-meson.0.auto/usb1/1-1/1-1:1.0/host0/target0:0:0/0:0:0:0/block/sda").apply { mkdirs() }
        val part = File(disk, "sda1").apply { mkdirs() }; File(part, "partition").writeText("1")
        val scsi = File(root, "devices/soc/xhci-hcd-meson.0.auto/usb1/1-1/1-1:1.0/host0/target0:0:0/0:0:0:0")
        Files.createSymbolicLink(File(disk, "device").toPath(), scsi.toPath())
        File(root, "dev/block").mkdirs(); Files.createSymbolicLink(File(root, "dev/block/8:1").toPath(), part.toPath())
        val sys = UsbSysfs(root)
        assertEquals("1-1", sys.usbNameOfBlock(8, 1))
        val info = assertNotNull(sys.infoForBlock(8, 1))
        assertEquals("Kingston", info.manufacturer); assertTrue(info.sharesBusWithWifi); assertEquals("xhci-hcd-meson.0.auto", info.controller)
        assertNull(sys.usbNameOfBlock(9, 9)); assertTrue(UsbSysfs(File("/nonexistent")).nodes().isEmpty())
        root.deleteRecursively()
    }
}
