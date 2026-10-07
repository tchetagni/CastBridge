package castbridge.core.owner

import castbridge.core.lots.Right
import castbridge.core.owner.ActivationRoutePlan.Route
import castbridge.core.owner.KeyAcquisition.Check
import castbridge.core.owner.KeyAcquisition.Effect
import castbridge.core.owner.KeyAcquisition.Event
import castbridge.core.owner.KeyAcquisition.Model
import castbridge.core.owner.KeyAcquisition.Phase
import castbridge.core.owner.KeyAcquisition.ResultKind
import castbridge.core.owner.KeyAcquisition.Server
import castbridge.core.owner.KeyAcquisition.Source
import castbridge.core.owner.KeyAcquisition.Tv
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.trust.TvDeviceRequest
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val NOW = 1_800_000_000_000L

/**
 * Obtenir la clé puis l'installer (DESIGN-ACTIVATION-SIMPLE § 2 B, § 3 F3/F4) : les sources (console, serveur préparé mais éteint, collage, fichier), la détection d'une clé dans le
 * presse-papiers (proposée, jamais installée sans geste), ce que dit le téléphone de la clé avant l'envoi, les bornes, les messages, une seule notification « TV activée ».
 * Pur : les clés sont de vraies activations signées par une clé de test.
 */
class KeyAcquisitionTest {
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd", FactorKind.BLUETOOTH to "00112233445566778899aabbccddeeff"))
    private val otherFp = Fingerprints(mapOf(FactorKind.FLASH to "ffffffffffffffffffffffffffffff01", FactorKind.SYSTEM_SERIAL to "eeeeeeeeeeeeeeeeeeeeeeeeeeeeee02", FactorKind.BLUETOOTH to "dddddddddddddddddddddddddddddd03"))
    private val sig = ByteArray(32) { (it * 3 + 2).toByte() }
    private val signer = Ed25519Signer(ByteArray(32) { (it + 9).toByte() })
    private val issuer = ActivationIssuer(signer)
    private val request: TvDeviceRequest = (LockedRequestRoute.parse(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, sig)) as DeviceRequestParse.Ok).request
    private val tv = Tv("CastBridge TV salon", Route.LAN)

    private fun key(f: Fingerprints = fp, kind: ActivationKind = ActivationKind.PRODUCTION, days: Int? = null, issuedAt: Long = NOW): String {
        val rights = if (days != null) listOf<Right>(Right.Usage(issuedAt, issuedAt + days * DAY)) else emptyList()
        return issuer.issue(ActivationIssuer.Request(kind, DeviceCode.of(f), f, issuedAt = issuedAt, rights = rights,
            license = if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else "lic-0000000001")).token
    }
    private val mine = key()
    private val trial30 = key(kind = ActivationKind.TRIAL, days = 30)
    private val others = key(otherFp)

    private fun model(serverEnabled: Boolean = false) = Model(serverEnabled = serverEnabled)
    private fun joined(m: Model = model()) = KeyAcquisition.reduce(m, Event.TvFound(tv, request, NOW)).model
    private fun Model.on(e: Event) = KeyAcquisition.reduce(this, e)

    // ------------------------------------------------------------------ les sources

    @Test fun theConsoleIsOfferedOnlyWhenThisBuildHasIt() {
        assertEquals(listOf(Source.CONSOLE, Source.PASTE, Source.FILE), KeyAcquisition.offers(consolePresent = true))
        assertEquals(listOf(Source.PASTE, Source.FILE), KeyAcquisition.offers(consolePresent = false))
    }

    @Test fun theServerWayExistsButItsCapabilityIsOff() {
        assertFalse(KeyAcquisition.ServerActivationRequests.ENABLED, "route serveur non décidée : aucune requête réelle")
        assertFalse(Source.SERVER in KeyAcquisition.offers(consolePresent = true))
        assertTrue(Source.SERVER in KeyAcquisition.offers(consolePresent = true, serverEnabled = true))
        val m = joined()
        assertEquals(Server.Disabled, m.server)
        val s = m.on(Event.ServerRequest(NOW))
        assertTrue(s.effects.isEmpty(), "capacité éteinte : aucun effet, donc aucune requête réseau")
        assertEquals(m, s.model)
        for (e in listOf(Event.ServerSent("7421", NOW), Event.ServerKey(mine, NOW), Event.ServerFailed("x"), Event.Tick(NOW + KeyAcquisition.SERVER_WAIT_MS))) {
            val r = m.on(e); assertEquals(m, r.model, "$e"); assertTrue(r.effects.isEmpty(), "$e")
        }
    }

    @Test fun theServerWayStatesWhenItIsSwitchedOn() {
        val m0 = joined(model(serverEnabled = true))
        assertEquals(Server.Idle, m0.server)
        val ask = m0.on(Event.ServerRequest(NOW))
        assertEquals(Server.Sending, ask.model.server)
        assertEquals(listOf<Effect>(Effect.SendServerRequest(request)), ask.effects)
        assertTrue(ask.model.on(Event.ServerRequest(NOW)).effects.isEmpty(), "une demande à la fois")
        val sent = ask.model.on(Event.ServerSent("7421", NOW))
        assertEquals(Server.Waiting("7421", NOW, NOW), sent.model.server)
        assertEquals("Demande n° 7421 envoyée, en attente de l'approbation de CastBridge.", KeyAcquisition.serverLine(sent.model.server))
        assertTrue(sent.model.on(Event.Tick(NOW + KeyAcquisition.SERVER_POLL_MS - 1)).effects.isEmpty())
        val poll = sent.model.on(Event.Tick(NOW + KeyAcquisition.SERVER_POLL_MS))
        assertEquals(listOf<Effect>(Effect.PollServer("7421")), poll.effects)
        assertTrue(poll.model.on(Event.Tick(NOW + KeyAcquisition.SERVER_POLL_MS + 1)).effects.isEmpty(), "pas deux sondages dans la même période")
        val key = poll.model.on(Event.ServerKey(mine, NOW + 6_000))
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.SERVER)), key.effects, "la clé existe : elle est installée (la demande était le geste)")
        assertEquals(Server.Idle, key.model.server)
        val late = sent.model.on(Event.Tick(NOW + KeyAcquisition.SERVER_WAIT_MS))
        assertIs<Server.Failed>(late.model.server)
        assertEquals(KeyAcquisition.SERVER_WAIT_TIMEOUT, (late.model.server as Server.Failed).message)
        assertIs<Server.Failed>(ask.model.on(Event.ServerFailed("Le serveur ne répond pas.")).model.server)
    }

    // ------------------------------------------------------------------ ce que le téléphone dit de la clé avant de l'envoyer

    @Test fun aKeyForThisTvIsRecognisedWithItsKindAndItsDuration() {
        assertEquals(Check.Valid(ActivationKind.PRODUCTION, null, false), KeyAcquisition.assess(mine, request, NOW))
        assertEquals(Check.Valid(ActivationKind.TRIAL, 30, false), KeyAcquisition.assess(trial30, request, NOW))
        assertEquals(Check.Valid(ActivationKind.PRODUCTION, 90, false), KeyAcquisition.assess(key(days = 90), request, NOW))
        assertEquals("Clé de production illimitée, faite pour cette TV.", KeyAcquisition.describe(KeyAcquisition.assess(mine, request, NOW), request))
        assertEquals("Clé d'essai de 30 jours, faite pour cette TV.", KeyAcquisition.describe(KeyAcquisition.assess(trial30, request, NOW), request))
        assertEquals("Clé de production de 90 jours, faite pour cette TV.", KeyAcquisition.describe(KeyAcquisition.assess(key(days = 90), request, NOW), request))
    }

    @Test fun withoutTheRequestTheTargetCannotBeCheckedButTheKindIsStillSaid() {
        val c = KeyAcquisition.assess(trial30, null, NOW)
        assertEquals(Check.Valid(ActivationKind.TRIAL, 30, false), c)
        assertTrue("la TV vérifiera" in KeyAcquisition.describe(c, null), KeyAcquisition.describe(c, null))
        assertFalse("pour cette TV" in KeyAcquisition.describe(c, null))
    }

    @Test fun aKeyForAnotherTvCannotBeInstalledAndTheMessageGivesThisTvsDeviceCode() {
        val c = KeyAcquisition.assess(others, request, NOW)
        assertEquals(Check.OtherTv, c)
        assertFalse(KeyAcquisition.installable(c))
        val d = KeyAcquisition.describe(c, request)
        assertTrue("autre TV" in d && request.code in d, d)
    }

    @Test fun anExpiredKeyIsOnlyAWarningBecauseTheTvClockHasTheLastWord() {
        val c = KeyAcquisition.assess(mine, request, NOW + 49 * 3600_000L)
        assertEquals(Check.Valid(ActivationKind.PRODUCTION, null, true), c)
        assertTrue(KeyAcquisition.installable(c))
        assertTrue("périmée" in KeyAcquisition.describe(c, request))
        assertEquals(Check.Valid(ActivationKind.PRODUCTION, null, false), KeyAcquisition.assess(mine, request, NOW + 47 * 3600_000L))
    }

    @Test fun aSignedTextThatIsNotAnActivationIsNotAKey() {
        val order = Envelope("order", "0123456789abcdef", 1, "00112233", NOW, NOW, NOW + DAY, Envelope.Target.Any, listOf("action=x"), "c2ln").encode()
        assertEquals(Check.NotAnActivation, KeyAcquisition.assess(order, request, NOW))
        assertEquals(Check.NotAnActivation, KeyAcquisition.assess("cbx1.AAAA.BBBB", request, NOW))
        assertFalse(KeyAcquisition.installable(Check.NotAnActivation))
        assertTrue(KeyAcquisition.installable(Check.Unknown))
    }

    @Test fun anActivationForAPhoneIsNotAKeyForATv() {
        val phoneKey = issuer.issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, issuedAt = NOW, subject = Subject.PHONE, license = "lic-0000000002")).token
        assertEquals(Check.NotAnActivation, KeyAcquisition.assess(phoneKey, request, NOW))
        assertFalse(KeyAcquisition.installable(KeyAcquisition.assess(phoneKey, request, NOW)))
        assertEquals(Check.Valid(ActivationKind.PRODUCTION, null, false), KeyAcquisition.assess(mine, request, NOW), "la même clé pour une TV est reconnue")
    }

    @Test fun theKeyIsFoundInsideAMessageAndTheKeyForThisTvIsPreferred() {
        val msg = "Bonjour, voici la clé de votre TV :\n$mine\nMerci de confirmer."
        val p = assertNotNull(KeyAcquisition.pick(msg, request, NOW))
        assertEquals(mine, p.key); assertIs<Check.Valid>(p.check)
        val two = KeyAcquisition.pick("clé 1 : $others\nclé 2 : $mine", request, NOW)
        assertEquals(mine, two!!.key, "la clé d'une autre TV placée avant ne gêne pas")
        val onlyOther = KeyAcquisition.pick("voici : $others", request, NOW)
        assertEquals(Check.OtherTv, onlyOther!!.check)
        assertFalse(onlyOther.installable)
    }

    @Test fun aGroupedOrCompactKeyIsAcceptedAndLeftToTheTv() {
        val grouped = GroupedText.encode(mine.toByteArray(Charsets.US_ASCII))
        val p = assertNotNull(KeyAcquisition.pick("Clé : $grouped", request, NOW))
        assertEquals(Check.Unknown, p.check)
        assertTrue(p.installable)
        assertEquals("Clé d'un autre format : la TV la vérifiera.", KeyAcquisition.describe(p.check, request))
    }

    @Test fun textWithoutAKeyGivesNothingAndTheBoundsHold() {
        for (t in listOf(null, "", "   ", "bonjour", "cbx1", "12345-678")) assertNull(KeyAcquisition.pick(t, request, NOW), "$t")
        val huge = "cbx1." + "A".repeat(KeyAcquisition.MAX_KEY_CHARS) + ".BBBB"
        assertNull(KeyAcquisition.pick(huge, request, NOW), "plus grosse que ce que la TV accepte (16 Kio)")
        val ok = "cbx1." + "A".repeat(KeyAcquisition.MAX_KEY_CHARS - 20) + ".BBBB"
        assertNotNull(KeyAcquisition.pick(ok, request, NOW))
        assertEquals(16_384, KeyAcquisition.MAX_KEY_CHARS)
    }

    // ------------------------------------------------------------------ le presse-papiers : proposer, jamais installer sans geste

    @Test fun theClipboardProposesAKeyButNeverInstallsByItself() {
        val m = joined()
        val s = m.on(Event.Clipboard("copié depuis WhatsApp :\n$mine", NOW))
        assertEquals(mine, s.model.clipboard!!.key)
        assertTrue(s.effects.isEmpty(), "proposer ne lance rien")
        assertIs<Phase.Choosing>(s.model.phase)
        assertFalse(s.model.confirmed)
        assertNull(s.model.pick, "la clé n'est pas dans le champ tant que l'usager ne l'a pas voulu")
    }

    @Test fun onlyTheGestureOnTheProposalInstallsIt() {
        val proposed = joined().on(Event.Clipboard(mine, NOW)).model
        val go = proposed.on(Event.ClipboardAccepted(NOW + 1))
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.PASTE)), go.effects)
        assertIs<Phase.Installing>(go.model.phase)
        assertNull(go.model.clipboard)
    }

    @Test fun theClipboardWithoutAKeyProposesNothing() {
        val order = Envelope("order", "0123456789abcdef", 1, "00112233", NOW, NOW, NOW + DAY, Envelope.Target.Any, listOf("action=x"), "c2ln").encode()
        for (t in listOf(null, "", "rendez-vous à 18 h", "https://exemple.org/page", "cbx1.pas.unecle", order, "A".repeat(500))) {
            val s = joined().on(Event.Clipboard(t, NOW)); assertNull(s.model.clipboard, "$t"); assertTrue(s.effects.isEmpty())
        }
    }

    @Test fun aKeyOfAnotherTvInTheClipboardIsSaidNotProposed() {
        val s = joined().on(Event.Clipboard(others, NOW))
        assertNull(s.model.clipboard)
        assertTrue("autre TV" in s.model.message.orEmpty(), s.model.message.orEmpty())
        assertTrue(s.effects.isEmpty())
    }

    @Test fun aDismissedKeyIsNotProposedAgainButAnotherOneIs() {
        var m = joined().on(Event.Clipboard(mine, NOW)).model.on(Event.ClipboardDismissed).model
        assertNull(m.clipboard)
        assertNull(m.on(Event.Clipboard(mine, NOW + 5)).model.clipboard, "même clé, déjà écartée")
        assertNotNull(m.on(Event.Clipboard(trial30, NOW + 6)).model.clipboard)
    }

    @Test fun aHugeClipboardIsNeverRead() {
        val big = mine + " " + "x".repeat(KeyAcquisition.MAX_CLIPBOARD_CHARS)
        assertNull(joined().on(Event.Clipboard(big, NOW)).model.clipboard)
        assertEquals(65_536, KeyAcquisition.MAX_CLIPBOARD_CHARS)
    }

    @Test fun onceInstalledTheClipboardIsLeftAlone() {
        var m = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model.on(Event.Result(ResultKind.OK, "", NOW + 1)).model
        assertIs<Phase.Installed>(m.phase)
        assertNull(m.on(Event.Clipboard(trial30, NOW + 2)).model.clipboard)
    }

    @Test fun theClipboardKeyIsNotProposedWhenItIsAlreadyInTheField() {
        val m = joined().on(Event.Pasted(mine, NOW)).model
        assertNull(m.on(Event.Clipboard(mine, NOW + 1)).model.clipboard)
    }

    // ------------------------------------------------------------------ coller, installer

    @Test fun pastingFillsTheFieldWithAWordAboutTheKeyAndInstallsNothing() {
        val s = joined().on(Event.Pasted("voici $trial30", NOW))
        assertEquals(trial30, s.model.pick!!.key)
        assertEquals("Clé d'essai de 30 jours, faite pour cette TV.", s.model.message)
        assertTrue(s.effects.isEmpty())
        assertTrue(s.model.canInstall())
        val bad = s.model.on(Event.Pasted("rien d'utile", NOW))
        assertNull(bad.model.pick); assertEquals(KeyAcquisition.NOT_A_KEY, bad.model.message); assertFalse(bad.model.canInstall())
        assertNull(s.model.on(Event.Pasted("", NOW)).model.message, "un champ vidé n'affiche aucune erreur")
    }

    @Test fun installingNeedsAGestureAndAJoinedTv() {
        val pasted = model().on(Event.Pasted(mine, NOW)).model                       // pas encore de TV
        val tried = pasted.on(Event.Install(NOW))
        assertTrue(tried.effects.isEmpty(), "pas de TV : rien ne part")
        assertEquals(KeyAcquisition.NEED_TV, tried.model.message)
        assertTrue(tried.model.confirmed)
        val joinedLater = tried.model.on(Event.TvFound(tv, request, NOW + 20_000))
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.PASTE)), joinedLater.effects, "le geste est déjà donné : la clé part dès que la TV est jointe")
        // sans geste, la TV jointe ne déclenche rien
        assertTrue(pasted.on(Event.TvFound(tv, request, NOW)).effects.isEmpty())
    }

    @Test fun aKeyThatIsNotInstallableNeverGoesOut() {
        val m = joined().on(Event.Pasted(others, NOW)).model
        assertFalse(m.canInstall())
        val s = m.on(Event.Install(NOW))
        assertTrue(s.effects.isEmpty())
        assertTrue("autre TV" in s.model.message.orEmpty())
        assertTrue(joined().on(Event.Install(NOW)).effects.isEmpty(), "aucune clé : rien à installer")
    }

    @Test fun oneInstallEffectThenOneResultAndOneNotification() {
        val m = joined().on(Event.Pasted(mine, NOW)).model
        val go = m.on(Event.Install(NOW))
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.PASTE)), go.effects)
        assertEquals(Phase.Installing(NOW, Source.PASTE), go.model.phase)
        assertTrue(go.model.on(Event.Install(NOW + 1)).effects.isEmpty(), "pas deux installations à la fois")
        val done = go.model.on(Event.Result(ResultKind.OK, "Clé : lic-0000000001.", NOW + 2))
        val p = assertIs<Phase.Installed>(done.model.phase)
        assertEquals("Clé acceptée par la TV. Clé : lic-0000000001.", p.text)
        assertFalse(p.staged)
        assertEquals(listOf<Effect>(Effect.Notify("TV activée", KeyAcquisition.noticeText(tv.name))), done.effects)
        assertTrue(done.model.notified)
        assertTrue(done.model.offerAddTv, "puis « Ajouter ma TV »")
        val again = done.model.on(Event.Result(ResultKind.OK, "Clé : lic-0000000001.", NOW + 3))
        assertTrue(again.effects.isEmpty(), "une seule notification, même si le résultat est rapporté deux fois")
    }

    @Test fun aSecondKeyInstalledAfterTheFirstDoesNotNotifyAgain() {
        val first = joined().on(Event.Pasted(trial30, NOW)).model.on(Event.Install(NOW)).model.on(Event.Result(ResultKind.OK, "", NOW + 1))
        assertEquals(1, first.effects.count { it is Effect.Notify })
        val second = first.model.on(Event.Pasted(mine, NOW + 2)).model.on(Event.Install(NOW + 3)).model.on(Event.Result(ResultKind.OK, "", NOW + 4))
        assertTrue(second.effects.none { it is Effect.Notify }, "une seule notification « TV activée » par activation, même pour l'essai puis la production")
        assertIs<Phase.Installed>(second.model.phase)
    }

    @Test fun aTvThatIsAlreadyAddedIsNotOfferedAgain() {
        val linked = Model().on(Event.TvFound(tv.copy(alreadyLinked = true), request, NOW)).model.on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
            .on(Event.Result(ResultKind.OK, "", NOW)).model
        assertFalse(linked.offerAddTv)
        assertTrue(linked.notified)
    }

    @Test fun aKeyWaitingForTheTvToBeValidatedIsNotAnActivationYet() {
        val m = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
        val s = m.on(Event.Result(ResultKind.OK, "Clé reçue : appuyez sur « Valider la clé » sur la TV.", NOW + 1))
        val p = assertIs<Phase.Installed>(s.model.phase)
        assertTrue(p.staged)
        assertTrue(s.effects.isEmpty(), "pas de notification « TV activée » avant la validation sur la TV")
        assertFalse(s.model.notified)
        assertTrue("Valider la clé" in p.text)
    }

    @Test fun refusalsKeepTheTvWordsAndAWrongCodeIsFlagged() {
        val m = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
        val refused = m.on(Event.Result(ResultKind.REFUSED, "clé d'une autre TV", NOW + 1))
        assertEquals(Phase.Failed("Refusée par la TV : clé d'une autre TV", codeRefused = false), refused.model.phase)
        assertTrue(refused.effects.isEmpty())
        assertFalse(refused.model.notified)
        val code = m.on(Event.Result(ResultKind.CODE_REFUSED, "", NOW + 1))
        assertEquals(Phase.Failed(ActivationSend.WRONG_PIN_TEXT, codeRefused = true), code.model.phase)
        val down = m.on(Event.Result(ResultKind.UNREACHABLE, "", NOW + 1))
        assertEquals(KeyAcquisition.UNREACHABLE, (down.model.phase as Phase.Failed).text)
        // after a failure the same key can be sent again
        assertTrue(refused.model.canInstall())
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.PASTE)), refused.model.on(Event.Install(NOW + 2)).effects)
    }

    @Test fun theInstallationGivesUpAtItsBound() {
        val go = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
        assertEquals(30_000L, KeyAcquisition.INSTALL_MS)
        assertEquals(go, go.on(Event.Tick(NOW + 29_999)).model)
        val late = go.on(Event.Tick(NOW + 30_000))
        assertEquals(Phase.Failed(KeyAcquisition.TIMED_OUT, codeRefused = false), late.model.phase)
        assertTrue(late.effects.isEmpty())
        // a result that comes after the bound is ignored: nothing is activated twice, nothing is announced after a failure said otherwise
        val ignored = late.model.on(Event.Result(ResultKind.OK, "", NOW + 31_000))
        assertEquals(late.model, ignored.model); assertTrue(ignored.effects.isEmpty())
    }

    @Test fun aLinkLostDuringTheInstallationResumesWhenTheTvIsJoinedAgain() {
        val installing = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
        val lost = installing.on(Event.TvLost).model
        assertIs<Phase.Failed>(lost.phase)
        assertTrue(lost.confirmed, "le geste est gardé : la clé reste prête")
        val back = lost.on(Event.TvFound(tv, request, NOW + 60_000))
        assertEquals(listOf<Effect>(Effect.Install(mine, Source.PASTE)), back.effects)
        assertIs<Phase.Installing>(back.model.phase)
    }

    @Test fun aFailedInstallationIsNeverRetriedByAReconnection() {
        for (kind in listOf(ResultKind.REFUSED, ResultKind.CODE_REFUSED, ResultKind.UNREACHABLE)) {
            val failed = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model.on(Event.Result(kind, "x", NOW + 1)).model
            assertFalse(failed.confirmed, "$kind")
            assertTrue(failed.on(Event.TvFound(tv, request, NOW + 2)).effects.isEmpty(), "$kind : la clé ne repart pas toute seule après un échec")
        }
        val timedOut = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model.on(Event.Tick(NOW + KeyAcquisition.INSTALL_MS)).model
        assertFalse(timedOut.confirmed)
        assertTrue(timedOut.on(Event.TvFound(tv, request, NOW + 31_000)).effects.isEmpty())
    }

    @Test fun anotherTvAfterAnActivationStartsAFreshSession() {
        val dismissed = joined().on(Event.Clipboard(trial30, NOW)).model.on(Event.ClipboardDismissed).model
        assertEquals(1, dismissed.dismissed.size)
        val done = dismissed.on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model.on(Event.Result(ResultKind.OK, "", NOW + 1)).model
        assertTrue(done.notified && done.offerAddTv)
        val next = done.on(Event.TvFound(Tv("TV chambre", Route.GROUP), request, NOW + 5))
        assertTrue(next.effects.isEmpty())
        assertIs<Phase.Choosing>(next.model.phase)
        assertNull(next.model.pick); assertFalse(next.model.notified); assertFalse(next.model.offerAddTv); assertFalse(next.model.confirmed)
        assertEquals("TV chambre", next.model.tv!!.name)
        assertEquals(dismissed.dismissed, next.model.dismissed, "les clés écartées restent écartées")
        // the same TV found again while it is still being set up changes nothing for the key
        val again = joined().on(Event.Pasted(mine, NOW)).model.on(Event.TvFound(tv, request, NOW + 1))
        assertEquals(mine, again.model.pick!!.key)
    }

    @Test fun losingTheLinkWhileInstallingSaysSoAndKeepsTheKey() {
        val go = joined().on(Event.Pasted(mine, NOW)).model.on(Event.Install(NOW)).model
        val lost = go.on(Event.TvLost)
        assertEquals(Phase.Failed(KeyAcquisition.LINK_LOST, codeRefused = false), lost.model.phase)
        assertEquals(mine, lost.model.pick!!.key)
        assertNull(lost.model.tv)
        assertTrue(joined().on(Event.TvLost).model.phase is Phase.Choosing)
    }

    @Test fun theKeyFromTheConsoleIsInstalledAtOnceBecauseTheOwnerAlreadyTouchedInstall() {
        val s = joined().on(Event.ConsoleKey(trial30, NOW))
        assertEquals(listOf<Effect>(Effect.Install(trial30, Source.CONSOLE)), s.effects)
        assertEquals(Phase.Installing(NOW, Source.CONSOLE), s.model.phase)
        val wait = model().on(Event.ConsoleKey(trial30, NOW))
        assertTrue(wait.effects.isEmpty()); assertTrue(wait.model.confirmed)
        assertEquals(listOf<Effect>(Effect.Install(trial30, Source.CONSOLE)), wait.model.on(Event.TvFound(tv, request, NOW + 1)).effects)
        // a console that returns something that is not a key installs nothing
        assertTrue(joined().on(Event.ConsoleKey("n'importe quoi", NOW)).effects.isEmpty())
        // nor a key for another TV
        assertTrue(joined().on(Event.ConsoleKey(others, NOW)).effects.isEmpty())
    }

    // ------------------------------------------------------------------ un fichier

    @Test fun aFileGivesItsFirstKeyForThisTvOrSaysWhy() {
        val mail = "De : agent\nObjet : votre clé\n\nBonjour,\nvoici la clé : $mine\n\nCordialement"
        val s = joined().on(Event.FilePicked(mail, NOW))
        assertEquals(mine, s.model.pick!!.key)
        assertTrue(s.effects.isEmpty(), "choisir un fichier n'installe rien")
        assertEquals("Aucun fichier choisi.", joined().on(Event.FilePicked(null, NOW)).model.message)
        assertEquals(castbridge.core.tv.activation.KeyScan.NO_KEY, joined().on(Event.FilePicked("rien ici", NOW)).model.message)
        assertEquals(256 * 1024, KeyAcquisition.MAX_FILE_BYTES)
        assertTrue("trop gros" in KeyAcquisition.FILE_TOO_BIG)
    }

    // ------------------------------------------------------------------ discrétion (ACT-NF2) et mots

    @Test fun noKeyAndNoRequestAppearsInAnyDump() {
        val m = joined(model(serverEnabled = true)).on(Event.Clipboard(mine, NOW)).model.on(Event.Pasted(trial30, NOW)).model
        val installing = m.on(Event.Install(NOW))
        val texts = listOf(m.toString(), installing.model.toString(), installing.effects.toString(), KeyAcquisition.pick(mine, request, NOW).toString(), Effect.Install(mine, Source.PASTE).toString(),
            m.clipboard.toString(), Event.Pasted(mine, NOW).toString(), Event.ClipboardAccepted(NOW).toString(), Event.ConsoleKey(mine, NOW).toString(), Event.ServerKey(mine, NOW).toString(),
            Effect.SendServerRequest(request).toString())
        for (t in texts) {
            assertFalse(mine in t || trial30 in t, "une clé dans un texte de débogage : $t")
            assertFalse(request.code in t, "la demande d'appareil dans un texte de débogage : $t")
            assertFalse("0a1b2c3d4e5f60718293a4b5c6d7e8f9" in t, "une empreinte dans un texte de débogage : $t")
        }
    }

    @Test fun theNotificationNamesTheTvAndNothingElse() {
        assertEquals("TV activée", KeyAcquisition.NOTICE_TITLE)
        val t = KeyAcquisition.noticeText("CastBridge TV salon")
        assertEquals("CastBridge TV salon est activée.", t)
        assertFalse(mine in t)
    }

    @Test fun everyFixedTextUsesTheAppNames() {
        val all = listOf(KeyAcquisition.NOT_A_KEY, KeyAcquisition.NEED_TV, KeyAcquisition.LINK_LOST, KeyAcquisition.TIMED_OUT, KeyAcquisition.UNREACHABLE, KeyAcquisition.NO_FILE,
            KeyAcquisition.FILE_TOO_BIG, KeyAcquisition.SERVER_WAIT_TIMEOUT, KeyAcquisition.NOTICE_TITLE, KeyAcquisition.serverLine(Server.Idle), KeyAcquisition.serverLine(Server.Sending))
        for (t in all) { assertFalse(t.contains("sender", true) || t.contains("receiver", true) || t.contains("émetteur", true) || t.contains("récepteur", true), t); assertTrue(t.isNotBlank()) }
        assertEquals("Demander l'activation à CastBridge", KeyAcquisition.SERVER_BUTTON)
    }
}
