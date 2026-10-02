package castbridge.core.xfer

import kotlin.test.*

class ReceiveCardTest {
    private fun obj(name: String, size: Long, blocks: Long, done: Long, ready: Boolean = false) =
        "{\"id\":\"i$name\",\"name\":\"$name\",\"size\":$size,\"blockSize\":100,\"blocks\":$blocks,\"done\":$done,\"map\":\"00\",\"volume\":\"v\",\"writeBps\":0,\"queued\":0,\"maxStreams\":4,\"ready\":$ready}"
    private fun arr(vararg o: String) = o.joinToString(",", "[", "]")

    @Test fun emptyIsReady() {
        assertEquals(emptyList(), ReceiveCards.of("[]", null))
        assertEquals(emptyList(), ReceiveCards.of("", null))
        assertEquals("Prêt à recevoir", ReceiveCards.headline(emptyList()))
    }

    @Test fun oneWifiTransfer() {
        val c = ReceiveCards.of(arr(obj("a.mp4", 1000, 10, 5)), null).single()
        assertEquals(ReceiveCard("a.mp4", 500, 1000, "Wi-Fi", "en cours"), c)
        assertEquals("Réception de a.mp4 : 50 %", ReceiveCards.headline(listOf(c)))
    }

    @Test fun readyTransferIsDone() {
        assertEquals("terminé", ReceiveCards.of(arr(obj("a", 1000, 10, 10, true)), null).single().state)
        assertEquals("terminé", ReceiveCards.of(arr(obj("a", 1000, 10, 10)), null).single().state)
    }

    @Test fun twoTransfers() {
        val cs = ReceiveCards.of(arr(obj("a", 1000, 10, 1), obj("b", 2000, 20, 20)), null)
        assertEquals(listOf("a", "b"), cs.map { it.name })
        assertEquals(listOf(100L, 2000L), cs.map { it.received })
        assertEquals("Réception de 2 fichiers", ReceiveCards.headline(cs))
    }

    @Test fun bluetoothLine() {
        val c = ReceiveCards.of("[]", "Bluetooth : réception de film.mp4 42 %").single()
        assertEquals(ReceiveCard("film.mp4", 42, 100, "Bluetooth", "en cours"), c)
        assertEquals("Réception par Bluetooth : film.mp4 42 %", ReceiveCards.headline(listOf(c)))
    }

    @Test fun bluetoothIdleLinesGiveNoCard() {
        for (l in listOf("Bluetooth : prêt", "Bluetooth : prêt (fichier reçu)", "Bluetooth : transfert interrompu, reprise possible", "", null))
            assertEquals(emptyList(), ReceiveCards.of("[]", l), "$l")
    }

    @Test fun wifiAndBluetoothTogether() {
        val cs = ReceiveCards.of(arr(obj("a", 1000, 10, 5)), "Bluetooth : réception de b.mp4 10 %")
        assertEquals(2, cs.size)
        assertEquals("Réception de 2 fichiers", ReceiveCards.headline(cs))
    }

    @Test fun escapedNameAndZeroSize() {
        val j = "[{\"id\":\"x\",\"name\":\"a \\\"q\\\" b.mp4\",\"size\":0,\"blockSize\":100,\"blocks\":0,\"done\":0,\"ready\":false}]"
        val c = ReceiveCards.of(j, null).single()
        assertEquals("a \"q\" b.mp4", c.name)
        assertEquals("Réception de a \"q\" b.mp4 : 0 %", ReceiveCards.headline(listOf(c)))
    }

    @Test fun doneOverBlocksIsCapped() {
        assertEquals(1000, ReceiveCards.of(arr(obj("a", 1000, 10, 99)), null).single().received)
    }

    @Test fun readyOnlyWhenEmpty() {
        for (cs in listOf(ReceiveCards.of(arr(obj("a", 10, 1, 0)), null), ReceiveCards.of("[]", "réception de x 1 %")))
            assertNotEquals(ReceiveCards.READY, ReceiveCards.headline(cs))
    }
}
