package castbridge.core.quiz.online

import castbridge.core.relay.RelayText
import kotlin.test.Test
import kotlin.test.assertEquals

/** relay-R1 : la tuile « Partie Internet » reste proposée quand la TV n'a pas Internet MAIS qu'un téléphone synchronisé peut lui en donner ; sinon elle dit pourquoi, avec les mots du relais. */
class PlayGateRelayTest {
    private fun tile(hasInternet: Boolean, relay: PlayRelay, serviceUp: Boolean? = null, flagOn: Boolean = true, edition: HostEdition = HostEdition.PROD, child: Boolean = false) =
        PlayGate.tile(flagOn, edition, hasInternet, child, serviceUp = serviceUp, relay = relay)

    @Test fun withInternetTheRelayStateIsIrrelevant() {
        for (r in PlayRelay.values()) assertEquals(PlayTile.Available, tile(true, r), r.name)
    }

    @Test fun withoutInternetAPossibleRelayKeepsTheTileAvailable() {
        assertEquals(PlayTile.Available, tile(false, PlayRelay.POSSIBLE), "l'utilisateur appuie : la TV demande le tuyau au téléphone")
    }

    @Test fun withoutInternetAndWithoutRelayTheReasonIsSaid() {
        assertEquals(PlayTile.Blocked(PlayGate.MSG_NO_INTERNET), tile(false, PlayRelay.UNKNOWN), "comportement d'avant pour qui ne sait pas")
        assertEquals(PlayTile.Blocked(RelayText.NO_PHONE), tile(false, PlayRelay.NO_PHONE))
        assertEquals(PlayTile.Blocked(RelayText.OLD_PHONE), tile(false, PlayRelay.OLD_PHONE), "« Mettez CastBridge à jour pour l'Internet par relais »")
    }

    @Test fun theOtherGatesComeFirstWhateverTheRelay() {
        assertEquals(PlayTile.Hidden, tile(false, PlayRelay.POSSIBLE, flagOn = false))
        assertEquals(PlayTile.Blocked(PlayRules.MSG_ACTIVATE), tile(false, PlayRelay.POSSIBLE, edition = HostEdition.NONE))
        assertEquals(PlayTile.Blocked(PlayRules.MSG_CHILD), tile(false, PlayRelay.POSSIBLE, child = true))
    }

    @Test fun aRelayThatCannotBeProbedYetDoesNotTurnIntoServiceDown() {
        // le service n'a pas encore été sondé (aucun réseau pour le faire) : on essaie, comme avant (serviceUp = null)
        assertEquals(PlayTile.Available, tile(false, PlayRelay.POSSIBLE, serviceUp = null))
        assertEquals(PlayTile.Blocked(PlayGate.MSG_SERVICE_DOWN), tile(true, PlayRelay.POSSIBLE, serviceUp = false))
    }

    @Test fun theLegacyCallStillCompilesAndBehavesAsBefore() {
        assertEquals(PlayTile.Blocked(PlayGate.MSG_NO_INTERNET), PlayGate.tile(true, HostEdition.PROD, false, false))
        assertEquals(PlayTile.Available, PlayGate.tile(true, HostEdition.PROD, true, false))
    }
}
