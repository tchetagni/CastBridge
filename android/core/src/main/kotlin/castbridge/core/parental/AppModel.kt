package castbridge.core.parental

import castbridge.core.net.JsonLite

/** What a parent decides for an app of the TV other than CastBridge TV (docs/PARENTAL.md, « Surveillance de toute la TV »). */
enum class AppState(val code: String, val label: String) {
    ALLOWED("allow", "Autorisée"), BLOCKED("block", "Bloquée"), LIMITED("limit", "Durée limitée"), PIN("pin", "Code parental requis");
    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

/** Optional family of an installed app, to set several apps at once. A guess from the package, the parent can correct it. */
enum class AppCategory(val code: String, val label: String) {
    VIDEO("video", "Vidéo et streaming"), GAMES("games", "Jeux"), BROWSER("browser", "Navigateurs"), OTHER("other", "Autres");

    companion object {
        fun of(code: String?) = values().firstOrNull { it.code == code }
        private val BROWSERS = listOf("com.android.chrome", "org.chromium", "org.mozilla", "com.opera", "com.android.browser", "com.brave", "com.microsoft.emmx",
            "com.google.android.apps.chrome", "com.puffin", "com.tvwebbrowser", "com.stoutner", "com.duckduckgo")
        private val VIDEOS = listOf("com.google.android.youtube", "com.netflix", "com.amazon.amazonvideo", "com.disney", "com.hbo", "tv.twitch", "com.spotify.tv",
            "com.google.android.videos", "com.apple.atve", "com.canal", "com.dailymotion", "org.videolan", "com.mxtech", "com.plexapp", "org.xbmc.kodi", "com.wbd", "com.hulu", "com.vimeo")

        /**
         * A first guess. [isGame] is ApplicationInfo.FLAG_IS_GAME / CATEGORY_GAME on Android; [androidCategory] is ApplicationInfo.category
         * (CATEGORY_VIDEO = 2, CATEGORY_GAME = 0) or -1 when unknown. Pure so that it is testable.
         */
        fun guess(pkg: String, isGame: Boolean = false, androidCategory: Int = -1): AppCategory = when {
            isGame || androidCategory == 0 -> GAMES
            BROWSERS.any { pkg.startsWith(it) } -> BROWSER
            androidCategory == 2 || VIDEOS.any { pkg.startsWith(it) } -> VIDEO
            else -> OTHER
        }
    }
}

/** What a newly installed app gets until a parent looked at it. */
enum class NewAppDefault(val code: String, val label: String) {
    ALLOW("allow", "Autoriser (signalée aux parents)"), BLOCK("block", "Bloquer jusqu'à validation par un parent");
    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

/** A rule on one app for one profile. [limitMin] counts only for [AppState.LIMITED] (minutes per day on this app). */
data class AppRule(val pkg: String, val state: AppState, val limitMin: Int = 0, val category: AppCategory? = null)

/** An app installed after the setup, waiting for a parent's review ("nouvelle application"). */
data class NewApp(val pkg: String, val label: String, val at: Long)

/**
 * The per-app settings. Kept apart from [ParentalConfig] on purpose: a phone that predates this feature saves the whole
 * [ParentalConfig] (config/set) and would silently erase app rules stored inside it.
 */
data class AppSettings(
    val rev: Int = 0,
    /** The parent switched on the supervision of the whole TV. Off = nothing outside CastBridge TV is looked at. */
    val supervise: Boolean = false,
    val newApp: NewAppDefault = NewAppDefault.BLOCK,
    /** profile id -> rules. */
    val rules: Map<String, List<AppRule>> = emptyMap(),
    /** Packages present at the baseline or reviewed by a parent: not « nouvelles ». */
    val known: Set<String> = emptySet(),
    val baselined: Boolean = false,
    val news: List<NewApp> = emptyList(),
) {
    fun rule(profileId: String, pkg: String): AppRule? = rules[profileId]?.firstOrNull { it.pkg == pkg }
    fun isNew(pkg: String) = news.any { it.pkg == pkg }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "rev" to rev, "supervise" to supervise, "newApp" to newApp.code, "baselined" to baselined,
        "rules" to rules.map { (id, l) -> linkedMapOf("profile" to id, "apps" to l.map { ruleMap(it) }) },
        "known" to known.sorted(),
        "news" to news.map { linkedMapOf("pkg" to it.pkg, "label" to it.label, "at" to it.at) },
    )

    fun toJson(): String = JsonLite.write(toMap())

    companion object {
        const val MAX_RULES_PER_PROFILE = 300
        const val MAX_KNOWN = 800
        const val MAX_NEWS = 60
        const val MAX_LIMIT_MIN = 1440
        private val PKG = Regex("[A-Za-z0-9_.]{1,150}")
        fun validPkg(p: String?) = p != null && PKG.matches(p)

        fun ruleMap(r: AppRule) = linkedMapOf("pkg" to r.pkg, "state" to r.state.code, "limitMin" to r.limitMin, "category" to r.category?.code)

        /** Labels come from other apps: never trusted to be short or free of control characters. */
        fun cleanLabel(s: String?, pkg: String): String =
            (s ?: "").map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(40).ifEmpty { pkg.take(40) }

        fun parse(json: String): AppSettings = fromMap(runCatching { JsonLite.obj(json) }.getOrElse { throw IllegalArgumentException("Réglages des applications illisibles.") })

        @Suppress("UNCHECKED_CAST")
        fun parseRules(list: List<Any?>?): List<AppRule> {
            val out = (list.orEmpty()).map { raw ->
                val m = raw as? Map<String, Any?> ?: throw IllegalArgumentException("Règle d'application invalide.")
                val pkg = (m["pkg"] as? String)?.takeIf { validPkg(it) } ?: throw IllegalArgumentException("Nom d'application invalide.")
                val state = AppState.of(m["state"] as? String) ?: throw IllegalArgumentException("État d'application inconnu.")
                val limit = (m["limitMin"] as? Number)?.toInt() ?: 0
                if (limit < 0 || limit > MAX_LIMIT_MIN) throw IllegalArgumentException("Durée d'application invalide.")
                if (state == AppState.LIMITED && limit <= 0) throw IllegalArgumentException("Une application à durée limitée a besoin d'une durée.")
                AppRule(pkg, state, if (state == AppState.LIMITED) limit else 0, AppCategory.of(m["category"] as? String))
            }
            if (out.size > MAX_RULES_PER_PROFILE) throw IllegalArgumentException("$MAX_RULES_PER_PROFILE règles d'applications au maximum par profil.")
            if (out.map { it.pkg }.toSet().size != out.size) throw IllegalArgumentException("Une application a deux règles.")
            return out
        }

        @Suppress("UNCHECKED_CAST")
        fun fromMap(o: Map<String, Any?>): AppSettings {
            val rules = LinkedHashMap<String, List<AppRule>>()
            for (e in (o["rules"] as? List<Any?>).orEmpty()) {
                val m = e as? Map<String, Any?> ?: throw IllegalArgumentException("Réglages des applications invalides.")
                val id = (m["profile"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,16}")) } ?: throw IllegalArgumentException("Identifiant de profil invalide.")
                rules[id] = parseRules(m["apps"] as? List<Any?>)
            }
            val known = (o["known"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.takeIf(::validPkg) }.take(MAX_KNOWN).toSet()
            val news = (o["news"] as? List<Any?>).orEmpty().mapNotNull { n ->
                val m = n as? Map<String, Any?> ?: return@mapNotNull null
                val pkg = (m["pkg"] as? String)?.takeIf(::validPkg) ?: return@mapNotNull null
                NewApp(pkg, cleanLabel(m["label"] as? String, pkg), (m["at"] as? Number)?.toLong() ?: 0L)
            }.take(MAX_NEWS)
            return AppSettings(
                rev = (o["rev"] as? Number)?.toInt() ?: 0, supervise = o["supervise"] as? Boolean ?: false,
                newApp = NewAppDefault.of(o["newApp"] as? String) ?: NewAppDefault.BLOCK, rules = rules,
                known = known, baselined = o["baselined"] as? Boolean ?: false, news = news,
            )
        }
    }
}

/** An app seen on the TV (launcher-visible, leanback launcher included), as the API lists it. */
data class InstalledApp(val pkg: String, val label: String, val category: AppCategory = AppCategory.OTHER)

/**
 * What the TV knows about the system, given by the Android side: it keeps the pure rules free of PackageManager.
 * [neverBlock]: CastBridge TV itself, the launcher(s) and the system UI. [settingsPkgs]: the Settings and the package installer,
 * blocked only through the « Réglages » category of the profile.
 */
data class AppEnv(val selfPkg: String, val neverBlock: Set<String> = emptySet(), val settingsPkgs: Set<String> = emptySet()) {
    fun isEssential(pkg: String) = pkg == selfPkg || pkg in neverBlock || pkg in ESSENTIAL
    fun isSettings(pkg: String) = pkg in settingsPkgs || pkg in SETTINGS

    companion object {
        /** Never blockable whatever the TV: without them there is no way back to the home screen. */
        val ESSENTIAL = setOf("android", "com.android.systemui", "com.android.launcher", "com.android.launcher3", "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher", "com.android.tv.launcher")
        val SETTINGS = setOf("com.android.settings", "com.android.tv.settings", "com.google.android.tv.settings", "com.android.packageinstaller",
            "com.google.android.packageinstaller")
    }
}

/** Pure per-app decision: no clock, no storage. */
object AppRules {
    /**
     * May [pkg] be in front now for [p]? Order: essentials (always) -> Settings (the « Réglages » category) -> the rule of the app (or the
     * default of a new app) -> the hours and the daily time of the profile (kind [UseKind.APPS]).
     *
     * @param usedMs total ms today over the profile's kinds; [appUsedMs] ms today on this app; [granted] the parent typed the PIN for this app.
     */
    fun decide(p: ChildProfile, s: AppSettings, pkg: String, env: AppEnv, nowMin: Int, usedMs: Long, appUsedMs: Long, granted: Boolean): Decision {
        if (env.isEssential(pkg)) return Decision.ALLOW
        if (env.isSettings(pkg)) {
            if (ParentalRules.categoryBlocked(p, Category.SETTINGS)) return Decision.deny("category", "« ${Category.SETTINGS.label} » est désactivé pour ${p.name}.")
            return ParentalRules.timeVerdict(p, UseKind.APPS, nowMin, usedMs)
        }
        val rule = s.rule(p.id, pkg)
        val state = when {
            rule != null -> rule.state
            s.isNew(pkg) -> if (s.newApp == NewAppDefault.BLOCK) AppState.BLOCKED else AppState.ALLOWED
            else -> AppState.ALLOWED
        }
        when (state) {
            AppState.BLOCKED -> return Decision.deny(if (rule == null) "newapp" else "app",
                if (rule == null) "Nouvelle application : un parent doit la valider d'abord." else "Cette application est bloquée pour ${p.name}.")
            AppState.PIN -> if (!granted) return Decision.deny("apppin", "Cette application demande le code parental.")
            AppState.LIMITED -> {
                val lim = rule!!.limitMin * 60_000L
                if (appUsedMs >= lim) return Decision.deny("applimit", "Le temps de cette application est terminé (${rule.limitMin} min par jour). À demain !")
            }
            AppState.ALLOWED -> {}
        }
        val v = ParentalRules.timeVerdict(p, UseKind.APPS, nowMin, usedMs)
        if (!v.allowed) return v
        if (state == AppState.LIMITED) {
            val left = ((rule!!.limitMin * 60_000L - appUsedMs + 59_999) / 60_000).toInt()
            return Decision(true, "ok", null, minOf(left, v.minutesLeft ?: Int.MAX_VALUE))
        }
        return v
    }

    /** Is this package one a parent can never lock out (shown greyed in the editor)? */
    fun neverBlockable(pkg: String, env: AppEnv) = env.isEssential(pkg)
}
