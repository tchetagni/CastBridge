package castbridge.core.tv

import castbridge.core.owner.TrialPolicy
import castbridge.core.quiz.Json
import castbridge.core.tv.ReceiverServer.Companion.q

/**
 * « Ouvrir CastBridge-TV depuis le téléphone » (docs/REMOTE.md) : un geste sur le téléphone fait passer CastBridge-TV devant l'application qui est à l'écran de la
 * TV (YouTube…), comme la touche YouTube ou Netflix d'une télécommande. Ce fichier est la partie PURE de la TV : les écrans qu'on peut demander, la réponse,
 * l'ordre d'essai des façons d'ouvrir (testé sans Android) et la ligne de MENU proposée une seule fois. Le branchement Android est `TvForeground` (module TV).
 *
 * Ce que ni la TV ni le téléphone ne peuvent faire : allumer une TV éteinte (pas de HDMI-CEC sans droits système, pas de Wake-on-LAN : l'adresse MAC d'un autre
 * appareil n'est plus lisible depuis Android 10).
 */

/** Les écrans que `POST /api/tv/open` peut faire apparaître. */
enum class OpenTvScreen(val wire: String) {
    /** L'accueil de CastBridge-TV (quitte la vidéo en cours, comme la touche Accueil de la télécommande du téléphone). */
    HOME("home"),
    /** La bibliothèque en grille. */
    LIBRARY("library"),
    /** L'écran de lecture tel qu'il est (une vidéo qui joue n'est pas interrompue). */
    PLAYER("player"),
    GAMES("games"),
    QUIZ("quiz");

    companion object {
        /** Pour le message d'erreur 400. */
        const val LIST = "home, library, player, games ou quiz"

        /** Null = absent ou inconnu : l'appelant distingue les deux en regardant si le texte était vide. */
        fun parse(raw: String?): OpenTvScreen? {
            val s = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return values().firstOrNull { it.wire == s }
        }

        /**
         * L'édition d'essai n'a ni la bibliothèque ni le quiz (leurs tuiles sont fermées, [TrialPolicy]) : un téléphone ne les ouvre pas non plus, la TV montre l'accueil
         * (au lieu de la bibliothèque) ou le hub des jeux, où seul le Sudoku est ouvert (au lieu du quiz).
         */
        fun forEdition(screen: OpenTvScreen?, trial: Boolean): OpenTvScreen? = when {
            !trial -> screen
            screen == LIBRARY && !TrialPolicy.tileAllowed("library") -> HOME
            screen == QUIZ && !TrialPolicy.gameAllowed("quiz") -> GAMES
            else -> screen
        }
    }
}

/**
 * La réponse de la TV : [opened] = CastBridge-TV est à l'écran (vérifié), [how] = `direct`, `fullscreen` (notification du canal « Demandes du téléphone »,
 * avec l'intention plein écran quand Android l'autorise), `accessibility`, `already` (déjà devant : rien n'a été touché) ou `none`, [needs] = `overlay` quand
 * l'autorisation « Afficher par-dessus les autres applications » réglerait le problème (et que l'écran de réglage existe sur ce boîtier), sinon null.
 */
data class OpenTvReply(val opened: Boolean, val how: String, val needs: String?, val already: Boolean = false) {
    fun toJson(): String = buildString {
        append("{\"opened\":").append(opened)
        if (already) append(",\"already\":true")
        append(",\"how\":").append(q(how))
        append(",\"needs\":").append(needs?.let { q(it) } ?: "null")
        append('}')
    }

    companion object {
        const val HOW_NONE = "none"
        const val HOW_ALREADY = "already"
        const val NEEDS_OVERLAY = "overlay"

        fun already() = OpenTvReply(true, HOW_ALREADY, null, true)

        /** Lit la réponse d'une TV, aussi d'une autre version : champs en plus ignorés, `how` inconnu gardé tel quel. Null = ce n'est pas une réponse. */
        fun parse(json: String): OpenTvReply? = runCatching {
            val o = Json.obj(json)
            val opened = o["opened"] as? Boolean ?: return@runCatching null
            val how = (o["how"] as? String)?.takeIf { it.isNotBlank() && it.length <= 24 } ?: HOW_NONE
            val needs = (o["needs"] as? String)?.takeIf { it.isNotBlank() && it.length <= 24 }
            OpenTvReply(opened, how, needs, o["already"] == true || how == HOW_ALREADY)
        }.getOrNull()
    }
}

/**
 * Comment faire passer CastBridge-TV devant une autre application, dans l'ordre, et comment le dire au téléphone. Depuis Android 10, une application qui n'est
 * pas à l'écran ne peut plus ouvrir un écran toute seule, sauf exemptions :
 *
 *  1. **direct** : `startActivity` (NEW_TASK | CLEAR_TOP), possible si l'application a l'autorisation « Afficher par-dessus les autres applications » (ou avant
 *     Android 10) ;
 *  2. **fullscreen** : la notification du canal « Demandes du téléphone » (intention plein écran si Android l'autorise ; sinon une notification qu'on ouvre avec
 *     la télécommande) ;
 *  3. **accessibility** : le service d'accessibilité « CastBridge Télécommande », s'il est actif (Android lui permet de démarrer un écran).
 *
 * Chaque essai est VÉRIFIÉ (CastBridge-TV est-il maintenant à l'écran ?) avant de passer au suivant : un démarrage bloqué ne lève aucune erreur sous Android.
 */
object OpenTvPlan {
    enum class Way(val wire: String, val waitMs: Long) {
        // une TV 32 bits ouvre son écran en une seconde environ ; une notification ne se vérifie pas : attente courte. Au plus 4,8 s en tout : le téléphone en attend 10.
        DIRECT("direct", 2_000), FULLSCREEN("fullscreen", 800), ACCESSIBILITY("accessibility", 2_000)
    }

    data class State(
        /** Un écran de CastBridge-TV est déjà à l'écran. */
        val front: Boolean,
        val sdk: Int,
        /** « Afficher par-dessus les autres applications » accordée (SYSTEM_ALERT_WINDOW : exempte du blocage des démarrages en arrière-plan). */
        val overlayAllowed: Boolean,
        /** L'écran Android de cette autorisation existe sur ce boîtier (certaines TV n'en ont pas). */
        val overlayScreenExists: Boolean,
        /** `USE_FULL_SCREEN_INTENT` utilisable (`NotificationManager.canUseFullScreenIntent` dès Android 14). */
        val fullScreenAllowed: Boolean,
        val notificationsAllowed: Boolean,
        /** Le service d'accessibilité de CastBridge-TV est connecté. */
        val accessibilityActive: Boolean,
    )

    /** [needsOverlay] : l'autorisation « par-dessus » règlerait le problème ET le propriétaire peut l'accorder sur ce boîtier. */
    data class Plan(val already: Boolean, val ways: List<Way>, val fullScreenIntent: Boolean, val needsOverlay: Boolean)

    fun plan(s: State): Plan {
        if (s.front) return Plan(already = true, ways = emptyList(), fullScreenIntent = false, needsOverlay = false)
        val ways = buildList {
            if (s.sdk < 29 || s.overlayAllowed) add(Way.DIRECT)
            if (s.notificationsAllowed) add(Way.FULLSCREEN)
            if (s.accessibilityActive) add(Way.ACCESSIBILITY)
        }
        return Plan(false, ways, s.fullScreenAllowed, needsOverlay = s.sdk >= 29 && !s.overlayAllowed && s.overlayScreenExists)
    }

    /** Ce que l'Android de la TV sait faire (faux en test). */
    interface Actions {
        /** Tente [way] ; false = impossible à tenter (pas de service, exception) : on passe au suivant sans attendre. */
        fun start(way: Way, screen: OpenTvScreen?, fullScreenIntent: Boolean): Boolean
        /** Attend au plus [maxMs] que CastBridge-TV soit à l'écran ; vrai dès qu'il y est. */
        fun awaitFront(maxMs: Long): Boolean
        /** Retire la notification « Demandes du téléphone » (elle a fait son travail). */
        fun withdraw()
    }

    /** Essaie les façons dans l'ordre jusqu'à ce que l'écran soit là, et rend ce que la TV répond au téléphone. */
    fun execute(s: State, screen: OpenTvScreen?, a: Actions): OpenTvReply {
        val p = plan(s)
        if (p.already) return OpenTvReply.already()
        var posted = false
        for (w in p.ways) {
            if (!a.start(w, screen, p.fullScreenIntent)) continue
            if (w == Way.FULLSCREEN) posted = true
            if (a.awaitFront(w.waitMs)) {
                if (posted) a.withdraw()
                return OpenTvReply(true, w.wire, null)
            }
        }
        // rien n'a ouvert l'écran : si la notification est posée, la TV l'affiche (on peut l'ouvrir avec la télécommande de la TV)
        return OpenTvReply(false, if (posted) Way.FULLSCREEN.wire else OpenTvReply.HOW_NONE, if (p.needsOverlay) OpenTvReply.NEEDS_OVERLAY else null)
    }
}

/**
 * La ligne « Autoriser CastBridge-TV à s'afficher par-dessus les autres applications » du MENU de la TV : proposée UNE fois, seulement après qu'un téléphone a
 * demandé d'ouvrir CastBridge-TV sans que rien ne marche ([wanted]), si l'écran de réglage existe sur ce boîtier, si l'autorisation n'est pas déjà accordée, et
 * jamais une seconde fois une fois choisie (accord ou refus : le refus est mémorisé, aucune boucle). La ligne permanente « Lecture à distance : autoriser… » reste.
 */
object OverlayOfferPolicy {
    const val LINE = "Autoriser CastBridge-TV à s'afficher par-dessus les autres applications"

    fun shouldOffer(wanted: Boolean, overlayAllowed: Boolean, screenExists: Boolean, alreadyAsked: Boolean, sdk: Int): Boolean =
        sdk >= 29 && wanted && !overlayAllowed && screenExists && !alreadyAsked
}
