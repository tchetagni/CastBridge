package castbridge.core.tunnel

import castbridge.core.connect.NetState
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-45 (audit anti-régression 2026-10-07 b, I-7, latent tant que le tunnel serveur est éteint) : le tunnel d'assistance montait sur le tuyau du téléphone (`VIA_RELAY` → `GATEWAY`) et y
 * gardait sa session SSH pour toujours (un tunnel inverse permanent qui attend un expert). Le tuyau du téléphone se ferme 10 minutes après sa dernière connexion ouverte : celle-ci comptait,
 * donc il ne se fermait jamais (notification permanente, garde-vivant sur les données mobiles, 5 Mo par jour mangés, puis le jeu refusé). Maintenant le tuyau n'est celui du tunnel que
 * pendant une assistance demandée (« Se connecter maintenant ») ou pour le partage manuel du propriétaire du téléphone ; sinon la machine ferme la session et libère le tuyau.
 */
class TunnelConnectivityTest {
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L

    // ------------------------------------------------------------------ la règle

    @Test fun viaRelayWithoutAnAssistanceIsOffline() {
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.path(NetState.VIA_RELAY))
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.path(NetState.VIA_RELAY, assistPipe = false))
    }

    @Test fun viaRelayWithAnAssistanceIsTheGateway() {
        assertEquals(TunnelPath.GATEWAY, TunnelConnectivity.path(NetState.VIA_RELAY, assistPipe = true))
    }

    @Test fun theOwnNetworkAndNoNetworkDoNotDependOnIt() {
        for (a in listOf(true, false)) {
            assertEquals(TunnelPath.DIRECT, TunnelConnectivity.path(NetState.DIRECT, a), "assist=$a")
            assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.path(NetState.NONE, a), "assist=$a : un drapeau ne fait pas apparaître Internet")
        }
    }

    // ------------------------------------------------------------------ la politique : qui peut employer le tuyau

    @Test fun anAssistanceRequestOpensAWindowOfHalfAnHourThatAskingAgainRenews() {
        val p = AssistPipePolicy()
        assertFalse(p.allowed(t0, attachId = 1), "rien demandé")
        p.requested(t0)
        assertTrue(p.allowed(t0, 1)); assertTrue(p.allowed(t0 + 29 * min, 1))
        assertEquals(30 * min, AssistPipePolicy.ASSIST_WINDOW_MS)
        assertFalse(p.allowed(t0 + 30 * min, 1), "la fenêtre est finie : le tunnel lâche le tuyau")
        p.requested(t0 + 29 * min)
        assertTrue(p.allowed(t0 + 58 * min, 1), "une nouvelle demande la renouvelle")
        p.released()
        assertFalse(p.allowed(t0 + 59 * min, 1), "assistance terminée ou retirée")
    }

    @Test fun aClockWoundBackNeverStretchesTheWindow() {
        val p = AssistPipePolicy(); p.requested(t0)
        assertFalse(p.allowed(t0 - 2 * 3_600_000L, 1), "l'horloge a reculé de deux heures : ce n'est pas une demande de plus de trente minutes")
    }

    @Test fun aPipeTheTvAskedForIsNeverTheTunnels() {
        val p = AssistPipePolicy()
        p.attached(attachId = 1, tvAskedForIt = true)           // la TV l'a demandé pour une partie, le portefeuille, une mise à jour
        assertFalse(p.allowed(t0, 1))
        p.requested(t0)
        assertTrue(p.allowed(t0, 1), "…sauf si l'utilisateur demande en plus l'assistance")
    }

    @Test fun aManualSharingIsTheOwnersOwnAndEndsWithItsLink() {
        val p = AssistPipePolicy()
        p.attached(attachId = 3, tvAskedForIt = false)          // le propriétaire du téléphone partage lui-même : la TV n'avait rien demandé
        assertTrue(p.allowed(t0, 3), "il contrôle ce partage : le tunnel peut l'employer")
        assertFalse(p.allowed(t0, 4), "un autre tuyau (identité différente) n'est pas ce partage")
        p.detached()
        assertFalse(p.allowed(t0, 3), "le lien a fini")
        p.attached(attachId = 4, tvAskedForIt = true)
        assertFalse(p.allowed(t0, 4))
    }

    @Test fun noPipeAtAllIsNeverAllowedByTheManualRule() {
        val p = AssistPipePolicy()
        assertFalse(p.allowed(t0, attachId = -1))
        p.attached(attachId = -1, tvAskedForIt = false)
        assertFalse(p.allowed(t0, attachId = -1), "pas de tuyau : pas d'autorisation")
    }

    // ------------------------------------------------------------------ la machine : la session ne reste pas sur le tuyau

    private class Env(var net: NetState, val policy: AssistPipePolicy, var attach: Int, var t: Long) : TunnelEnv {
        override fun now() = t
        override fun termsAccepted() = true
        override fun locked() = false
        override fun activation() = "cbx1.AAA.BBB"
        override fun connectivity() = TunnelConnectivity.path(net, policy.allowed(t, attach))
        override fun keyId() = "key1"
    }

    private class Session : TunnelSession {
        var up = true; var closed = 0
        override fun alive() = up
        override fun close() { up = false; closed++ }
    }

    private class Transport : TunnelTransport {
        val enrolls = ArrayList<TunnelPath>(); val opens = ArrayList<TunnelPath>(); var session: Session? = null
        override fun enroll(activation: String, path: TunnelPath): EnrollOutcome { enrolls += path; return EnrollOutcome.Ok(Enrollment("bridge.sti-cm.com", 2200, "cbtunnel", 22100, null, "", "")) }
        override fun open(e: Enrollment, path: TunnelPath, onClosed: () -> Unit): TunnelSession { opens += path; return Session().also { session = it } }
        override fun refreshExperts(path: TunnelPath) = ExpertsSync.Result(true, "ok", 1)
    }

    @Test fun aPipeOpenedForAGameIsNeverTakenByTheTunnel() {
        val policy = AssistPipePolicy(); val env = Env(NetState.VIA_RELAY, policy, attach = 1, t = t0); val tr = Transport()
        policy.attached(1, tvAskedForIt = true)
        val m = TunnelMachine(env, tr, null, TunnelBackoff(Random(1)))
        m.step()
        assertEquals(TunnelState.IDLE, m.state)
        assertTrue(tr.enrolls.isEmpty() && tr.opens.isEmpty(), "ni enrôlement ni session sur le tuyau d'un téléphone qui joue")
    }

    @Test fun anAssistanceSessionOnThePipeIsClosedWhenTheAssistanceIsOverSoThePipeCanIdleOut() {
        val policy = AssistPipePolicy(); val env = Env(NetState.VIA_RELAY, policy, attach = 1, t = t0); val tr = Transport()
        policy.attached(1, tvAskedForIt = true)
        val m = TunnelMachine(env, tr, null, TunnelBackoff(Random(1)))
        policy.requested(env.t)                                 // « Se connecter maintenant »
        m.step()
        assertEquals(TunnelState.UP, m.state); assertEquals(listOf(TunnelPath.GATEWAY), tr.opens, "l'assistance demandée passe par le tuyau")
        val s = tr.session!!
        env.t += 20 * min; m.step()
        assertEquals(TunnelState.UP, m.state); assertEquals(0, s.closed, "pendant la fenêtre la session reste")
        env.t += 11 * min; m.step()                             // 31 minutes : la fenêtre est finie
        assertEquals(1, s.closed, "la session SSH qui tenait le tuyau est fermée : le téléphone n'a plus de connexion ouverte et son arrêt sur inactivité peut jouer")
        assertEquals(TunnelState.IDLE, m.state)
        assertEquals(1, tr.opens.size, "et le tunnel ne remonte pas sur ce tuyau tout seul")
        env.t += 5 * min; m.step()
        assertEquals(1, tr.opens.size)
    }

    @Test fun theOwnNetworkStillCarriesTheTunnelForGood() {
        val policy = AssistPipePolicy(); val env = Env(NetState.DIRECT, policy, attach = -1, t = t0); val tr = Transport()
        val m = TunnelMachine(env, tr, null, TunnelBackoff(Random(1)))
        m.step()
        assertEquals(TunnelState.UP, m.state); assertEquals(listOf(TunnelPath.DIRECT), tr.opens)
        env.t += 2 * 3_600_000L; m.step()
        assertEquals(TunnelState.UP, m.state, "le réseau propre de la TV : aucune limite de durée")
    }

    @Test fun aManualSharingCarriesTheTunnelAsBefore() {
        val policy = AssistPipePolicy(); val env = Env(NetState.VIA_RELAY, policy, attach = 2, t = t0); val tr = Transport()
        policy.attached(2, tvAskedForIt = false)
        val m = TunnelMachine(env, tr, null, TunnelBackoff(Random(1)))
        m.step()
        assertEquals(listOf(TunnelPath.GATEWAY), tr.opens)
    }
}
