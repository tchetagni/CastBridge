package castbridge.core.ux

import castbridge.core.langues.LangCatalog
import castbridge.core.parental.Category
import castbridge.core.parental.ChildProfile
import castbridge.core.parental.ParentalRules
import castbridge.core.phone.CastAction
import castbridge.core.phone.MediaKind
import castbridge.core.trust.CopyAndPlay
import castbridge.core.tv.QueueItem
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.QueueTexts
import kotlin.test.*

/**
 * Ergonomie et navigation (2026-10-03, docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md) : un seul vocabulaire pour envoyer
 * un fichier, une ligne d'explication par façon, aucun blocage muet sur l'accueil du téléphone, la file visible depuis tous les onglets,
 * le focus D-pad de l'accueil de la TV jamais perdu. Les écrans ne font que dessiner ce que ces fonctions renvoient.
 */
class UiTextsTest {

    // ---- Les façons d'envoyer : un libellé et une ligne d'explication chacune, partout les mêmes ----

    @Test fun theFourWaysHaveTheOwnersLabelsAndAOneLineHint() {
        assertEquals("Copier sur la TV et lire", SendWay.COPY_AND_PLAY.label)
        assertEquals("Copier sur la TV", SendWay.COPY.label)
        assertEquals("Déplacer vers la TV", SendWay.MOVE.label)
        assertEquals("Lire en direct", SendWay.LIVE.label)
        for (w in SendWay.values()) {
            assertTrue(w.hint.isNotBlank(), "$w sans explication")
            assertFalse('\n' in w.hint, "$w : une seule ligne")
            assertTrue(w.hint.split(' ').size <= 20, "$w : explication trop longue (${w.hint})")
            assertTrue(w.hint.endsWith("."), "$w : phrase complète")
        }
        // ce qui distingue les façons est dit, pas deviné
        assertTrue("pendant" in SendWay.COPY_AND_PLAY.hint)
        assertTrue("reste" in SendWay.COPY.hint && "sans" in SendWay.COPY.hint)
        assertTrue("effacé du téléphone" in SendWay.MOVE.hint)
        assertTrue("rien" in SendWay.LIVE.hint && "Wi-Fi" in SendWay.LIVE.hint)
    }

    @Test fun labelsAreUniqueAndNoLabelIsAPrefixOfAnotherOneThatMeansSomethingElse() {
        val labels = SendWay.values().map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        // « Copier sur la TV » est un préfixe de « Copier sur la TV et lire » : acceptable seulement parce que chaque bouton porte son explication
        assertTrue(SendWay.values().all { it.hint !in labels })
    }

    @Test fun castActionsSpeakTheSameVocabulary() {
        assertEquals(SendWay.COPY_AND_PLAY, SendWays.of(CastAction.COPY))
        assertEquals(SendWay.LIVE, SendWays.of(CastAction.LIVE))
        assertEquals(SendWay.MOVE, SendWays.of(CastAction.MOVE))
        assertEquals(SendWay.LIVE.label, SendWays.castLabel(CastAction.LIVE, MediaKind.VIDEO))
        assertEquals(SendWay.COPY_AND_PLAY.label, SendWays.castLabel(CastAction.COPY, MediaKind.VIDEO))
        assertEquals(CopyAndPlay.LABEL, SendWay.COPY_AND_PLAY.label)          // « Ouvrir avec » et la feuille disent la même chose
        // une photo n'est pas « lue » : elle est gardée
        assertEquals(SendWay.COPY.label, SendWays.castLabel(CastAction.COPY, MediaKind.IMAGE))
        assertEquals(SendWay.LIVE.hint, SendWays.castHint(CastAction.LIVE, MediaKind.VIDEO, isWeb = false))
        assertTrue("lien" in SendWays.castHint(CastAction.LIVE, MediaKind.VIDEO, isWeb = true))
        assertEquals(SendWay.MOVE.hint, SendWays.castHint(CastAction.MOVE, MediaKind.VIDEO, isWeb = false))
    }

    @Test fun theCastSheetOffersCopyAndPlayFirstThenLiveThenMove() {
        val core = listOf(CastAction.LIVE, CastAction.COPY, CastAction.MOVE)
        assertEquals(listOf(CastAction.COPY, CastAction.LIVE, CastAction.MOVE), SendWays.castOrder(core))
        assertEquals(listOf(CastAction.LIVE), SendWays.castOrder(listOf(CastAction.LIVE)))              // lien web, TV DLNA
        assertEquals(listOf(CastAction.COPY, CastAction.LIVE), SendWays.castOrder(listOf(CastAction.LIVE, CastAction.COPY)))
    }

    @Test fun jargonIsGoneFromTheSendingWords() {
        val words = SendWay.values().flatMap { listOf(it.label, it.hint) } +
            listOf(SendWays.HOME_COPY_HINT, SendWays.HOME_MOVE_HINT, SendWays.SHEET_TITLE, SendWays.MENU_PLAY_ON_TV)
        for (w in words) {
            assertTrue(w.isNotBlank())
            for (j in listOf("Caster", "caster", "DLNA", "upload", "HTTP", "Diffuser")) assertFalse(j in w, "« $j » dans « $w »")
        }
        assertEquals("Lire sur la TV", SendWays.SHEET_TITLE)
        assertTrue(SendWays.MENU_PLAY_ON_TV.startsWith("Lire sur la TV"))
    }

    @Test fun queueTextsNameTheButtonByItsNewName() {
        assertTrue("« ${SendWay.COPY.label} »" in QueueTexts.SOURCE_LOST, QueueTexts.SOURCE_LOST)
        assertFalse("Copier vers la TV" in QueueTexts.SOURCE_LOST)
    }

    // ---- Accueil du téléphone : aucun blocage muet ----

    @Test fun aTaskThatNeedsTheTvSaysWhyAndWhatToDo() {
        val n = HomeNotices.needsTv("Bibliothèque de la TV")
        assertTrue(n.error)
        assertTrue("« Bibliothèque de la TV »" in n.text, n.text)
        assertTrue("pas jointe" in n.text, "la cause : ${n.text}")
        assertTrue("allumée" in n.text && "même Wi-Fi" in n.text && "CastBridge-TV" in n.text, "l'action : ${n.text}")
    }

    @Test fun thePickerOpensOnlyWhenTheFilesHaveSomewhereToGo() {
        assertTrue(HomeNotices.canPick(session = true, pinTvName = null))
        assertTrue(HomeNotices.canPick(session = false, pinTvName = "CastBridge TV SALON"))
        assertFalse(HomeNotices.canPick(session = false, pinTvName = null))
        val trusted = HomeNotices.pickBlocked(trustedSaved = 1)
        assertTrue(trusted.error && "pas encore jointe" in trusted.text && "Réessayez" in trusted.text, trusted.text)
        val none = HomeNotices.pickBlocked(trustedSaved = 0)
        assertTrue(none.error && "Ajouter ma TV" in none.text, none.text)
    }

    @Test fun successIsNotPaintedAsAnError() {
        assertFalse(HomeNotices.info("3 fichiers ajoutés").error)
        assertTrue(HomeNotices.error("Impossible").error)
        val ok = HomeNotices.queued(3, refused = null)
        assertFalse(ok.error); assertTrue("3 fichiers" in ok.text && "l'un après l'autre" in ok.text, ok.text)
        val partly = HomeNotices.queued(2, refused = "« a.mp4 » est déjà dans la file")
        assertTrue(partly.error); assertTrue("2 fichiers" in partly.text && "déjà dans la file" in partly.text, partly.text)
        val one = HomeNotices.queued(1, refused = null)
        assertFalse(one.error); assertTrue("1 fichier" in one.text && "file" in one.text, one.text)
    }

    // ---- Onglets : l'app s'ouvre sur l'accueil des tâches ----

    @Test fun thePhoneOpensOnTheCastBridgeTvTabNotOnDlna() {
        assertEquals(PhoneTabs.Tab.HOME, PhoneTabs.at(PhoneTabs.START))
        assertEquals("CastBridge TV", PhoneTabs.Tab.HOME.label)
        // ordre inchangé (mémoire des usagers, ancres des cahiers w11-01/w17-07) : seul l'onglet de départ change
        assertEquals(listOf("TV DLNA", "CastBridge TV", "Jeux", "Sur le téléphone", "Apprendre", "Parental"), PhoneTabs.ORDER.map { it.label })
        assertEquals(1, PhoneTabs.index(PhoneTabs.Tab.HOME))
        // télémétrie inchangée : mêmes ids d'écran, l'onglet Parental n'est jamais rapporté
        assertEquals(listOf("cast", "home", "games", "player", "learn", null), PhoneTabs.ORDER.map { it.screen })
        assertEquals(listOf("cast", null, "games", "player", "learn", null), PhoneTabs.ORDER.map { it.feature })
        assertEquals(PhoneTabs.Tab.HOME, PhoneTabs.at(99))         // index inconnu (état restauré d'une autre version) : l'accueil
    }

    // ---- « Où en est ma copie » : la file visible depuis tous les onglets ----

    private fun q(id: Long, status: QueueStatus, name: String = "Film_$id.mkv", move: Boolean = false, playOnTv: Boolean = false, autoPlay: Boolean = false, error: String? = null) =
        QueueItem(id, "content://x/$id", name, 1000, move, autoPlay = autoPlay, status = status, playOnTv = playOnTv, error = error)

    @Test fun anEmptyOrFinishedQueueShowsNothing() {
        assertNull(QueueGlances.of(emptyList(), null))
        assertNull(QueueGlances.of(listOf(q(1, QueueStatus.DONE), q(2, QueueStatus.CANCELLED)), null))
        assertFalse(QueueGlances.stripVisible(onHome = false, glance = null))
    }

    @Test fun theGlanceSaysWhatRunsWhatWaitsAndWhatFailed() {
        val g = QueueGlances.of(listOf(q(1, QueueStatus.RUNNING, "Mon_film.mkv"), q(2, QueueStatus.WAITING), q(3, QueueStatus.WAITING)), null)!!
        assertEquals("Envoi vers la TV : « Mon film » · 2 en attente", g.title)
        assertFalse(g.error); assertEquals("Voir", g.action); assertNull(g.detail)
        val waitOnly = QueueGlances.of(listOf(q(2, QueueStatus.WAITING)), QueueTexts.PAUSED_BACKGROUND)!!
        assertEquals("Envoi vers la TV : 1 en attente", waitOnly.title)
        assertEquals(QueueTexts.PAUSED_BACKGROUND, waitOnly.detail)               // pourquoi ça n'avance pas : jamais muet
        val failed = QueueGlances.of(listOf(q(1, QueueStatus.RUNNING, "a.mp4"), q(4, QueueStatus.FAILED, error = QueueTexts.NO_TV)), null)!!
        assertTrue(failed.error); assertTrue(failed.title.endsWith("· 1 échec"), failed.title)
        assertEquals("Voir et réessayer", failed.action)
        assertEquals(QueueTexts.NO_TV, failed.detail)                              // la cause du premier échec
        val moving = QueueGlances.of(listOf(q(1, QueueStatus.RUNNING, "b.mp4", move = true)), null)!!
        assertTrue(moving.title.startsWith("Déplacement vers la TV"), moving.title)
    }

    @Test fun theStripIsShownOnEveryTabButTheHomeWhichHasTheFullCard() {
        val g = QueueGlances.of(listOf(q(1, QueueStatus.WAITING)), null)
        assertTrue(QueueGlances.stripVisible(onHome = false, glance = g))
        assertFalse(QueueGlances.stripVisible(onHome = true, glance = g))
    }

    @Test fun eachQueuedFileSaysHowItLeaves() {
        assertEquals("copie et lecture", QueueGlances.kind(q(1, QueueStatus.WAITING, playOnTv = true)))
        assertEquals("copie puis lecture", QueueGlances.kind(q(1, QueueStatus.WAITING, autoPlay = true)))
        assertEquals("copie", QueueGlances.kind(q(1, QueueStatus.WAITING)))
        assertEquals("déplacement", QueueGlances.kind(q(1, QueueStatus.WAITING, move = true)))
        assertEquals("déplacement puis lecture", QueueGlances.kind(q(1, QueueStatus.WAITING, move = true, autoPlay = true)))
    }

    // ---- TV : l'aide et l'écran Langues nomment les vrais boutons du téléphone ----

    @Test fun theTvHelpNamesThePhoneButtonByItsCurrentLabel() {
        val t = TvHelpTexts.send("12••••", "http://192.168.0.5:8080")
        assertTrue("« ${SendWay.COPY.label} »" in t, t)
        assertFalse("Envoyer une vidéo" in t)
        assertTrue("12••••" in t && "http://192.168.0.5:8080" in t, t)
        assertEquals(TvHelpTexts.SEND_TITLE, "Copier une vidéo sur la TV")
    }

    @Test fun theEmptyLanguesScreenSaysWhereToGoOnThePhone() {
        val m = LangCatalog.EMPTY_MESSAGE
        assertTrue(m.startsWith("Aucune langue installée"), m)
        assertTrue("Apprendre" in m && "Données hors ligne" in m, "chemin exact sur le téléphone : $m")
    }

    // ---- Profil enfant : la tuile « Jeux » ne disparaît plus (KID_HOME citait « Quiz »/« Échecs », plus des tuiles) ----

    @Test fun kidModeKeepsTheGamesTileUnlessGamesAreBlocked() {
        val kid = ChildProfile("k1", "Léa", blocked = emptySet())
        val tiles = listOf("Bibliothèque", "Apprendre", "Langues", "Jeux", "Téléchargements", "Aide", "Contrôle parental")
        assertTrue("Jeux" in ParentalRules.kidHome(tiles, kid), ParentalRules.kidHome(tiles, kid).toString())
        assertFalse("Jeux" in ParentalRules.kidHome(tiles, kid.copy(blocked = setOf(Category.GAMES))))
    }

    // ---- TV : le focus D-pad de l'accueil n'est jamais perdu ----

    @Test fun backFromATileComesBackToThatTile() {
        assertEquals(TvHomeFocus.Target.Tool(3), TvHomeFocus.onShow(TvHomeFocus.Opened.TOOL, toolIndex = 3, toolCount = 18, hasMediaRow = true))
        assertEquals(TvHomeFocus.Target.Tool(17), TvHomeFocus.onShow(TvHomeFocus.Opened.TOOL, toolIndex = 40, toolCount = 18, hasMediaRow = true))
    }

    @Test fun afterAVideoOrAtStartTheFirstCardIsFocusedElseTheFirstTile() {
        // la vidéo la plus récente (ou à reprendre) : OK la lit sans aucun déplacement
        assertEquals(TvHomeFocus.Target.FirstCard, TvHomeFocus.onShow(TvHomeFocus.Opened.CARD, 0, 18, hasMediaRow = true))
        assertEquals(TvHomeFocus.Target.FirstCard, TvHomeFocus.onShow(TvHomeFocus.Opened.NOTHING, 0, 18, hasMediaRow = true))
        assertEquals(TvHomeFocus.Target.Tool(0), TvHomeFocus.onShow(TvHomeFocus.Opened.NOTHING, 0, 18, hasMediaRow = false))
        assertEquals(TvHomeFocus.Target.None, TvHomeFocus.onShow(TvHomeFocus.Opened.NOTHING, 0, 0, hasMediaRow = false))
    }

    @Test fun rebuildingTheRowsNeverDropsTheFocus() {
        // une rangée apparaît (premier fichier reçu, « Reprendre », clé USB) pendant que le focus était dedans
        assertEquals(TvHomeFocus.Target.Tool(5), TvHomeFocus.afterRebuild(hadFocusInRows = true, focusedToolIndex = 5, toolCount = 18, hasMediaRow = true))
        assertEquals(TvHomeFocus.Target.FirstCard, TvHomeFocus.afterRebuild(hadFocusInRows = true, focusedToolIndex = null, toolCount = 18, hasMediaRow = true))
        assertEquals(TvHomeFocus.Target.Tool(0), TvHomeFocus.afterRebuild(hadFocusInRows = true, focusedToolIndex = null, toolCount = 18, hasMediaRow = false))
        // le focus était ailleurs (puce d'état, panneau) : on ne le vole pas
        assertEquals(TvHomeFocus.Target.None, TvHomeFocus.afterRebuild(hadFocusInRows = false, focusedToolIndex = null, toolCount = 18, hasMediaRow = true))
    }
}
