package castbridge.core.gateway

import castbridge.core.gateway.BtAdapterWatch.STATE_OFF
import castbridge.core.gateway.BtAdapterWatch.STATE_ON
import castbridge.core.gateway.BtAdapterWatch.STATE_TURNING_OFF
import castbridge.core.gateway.BtAdapterWatch.STATE_TURNING_ON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-42 (I-12) : éteindre le Bluetooth tue toutes les sockets serveur ; la TV doit réécouter quand l'adaptateur revient (`STATE_ON`), pas seulement tenter de ré-ouvrir pendant la panne.
 */
class BtAdapterWatchTest {
    @Test fun theValuesAreThoseOfAndroidsBluetoothAdapter() {
        // android.bluetooth.BluetoothAdapter : STATE_OFF = 10, STATE_TURNING_ON = 11, STATE_ON = 12, STATE_TURNING_OFF = 13
        assertEquals(listOf(10, 11, 12, 13), listOf(STATE_OFF, STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF))
    }

    @Test fun theGatewayListensAgainWhenTheAdapterComesBack() {
        assertTrue(BtAdapterWatch.listenAgain(STATE_TURNING_ON, STATE_ON), "le cas normal : TURNING_ON puis ON")
        assertTrue(BtAdapterWatch.listenAgain(STATE_OFF, STATE_ON), "ON sans passer par TURNING_ON (certains boîtiers)")
        assertTrue(BtAdapterWatch.listenAgain(STATE_TURNING_OFF, STATE_ON), "redémarrage rapide de la pile")
        assertTrue(BtAdapterWatch.listenAgain(null, STATE_ON), "premier avis vu, état d'avant inconnu : on réécoute, c'est sans danger")
    }

    @Test fun nothingElseMakesItListenAgain() {
        assertFalse(BtAdapterWatch.listenAgain(STATE_ON, STATE_ON), "un ON répété ne coupe pas une écoute qui marche")
        for (now in listOf(STATE_OFF, STATE_TURNING_ON, STATE_TURNING_OFF)) for (prev in listOf(null, STATE_OFF, STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF))
            assertFalse(BtAdapterWatch.listenAgain(prev, now), "$prev -> $now")
        assertFalse(BtAdapterWatch.listenAgain(STATE_OFF, -1), "valeur d'erreur d'Android (BluetoothAdapter.ERROR)")
        assertFalse(BtAdapterWatch.listenAgain(STATE_OFF, 99))
    }
}
