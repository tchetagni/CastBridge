package castbridge.core.parental

import castbridge.core.net.JsonLite

/** Classification of a video (manual marking by the parent): "tous publics", "-12", "-16", "adulte". */
enum class Rating(val age: Int, val code: String, val label: String) {
    ALL(0, "all", "Tous publics"), U12(12, "12", "Interdit -12 ans"), U16(16, "16", "Interdit -16 ans"), ADULT(18, "18", "Adulte");

    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

/** Age band of a child profile: decides which ratings the profile may watch. */
enum class AgeBand(val code: String, val label: String, val max: Rating) {
    KID("kid", "Moins de 12 ans", Rating.ALL),
    TEEN12("teen12", "12 à 15 ans", Rating.U12),
    TEEN16("teen16", "16 à 17 ans", Rating.U16),
    ADULT("adult", "Adulte", Rating.ADULT);

    fun allows(r: Rating) = r.age <= max.age

    companion object {
        fun of(code: String?) = values().firstOrNull { it.code == code }
        /** A first guess for a student of « Apprendre » from his class (the parent adjusts it). */
        fun guessFromLevel(level: String?): AgeBand {
            val c = castbridge.core.learn.LearnCatalog.cursusOfLevel(level) ?: return KID
            return when (c.stage) {
                castbridge.core.learn.LearnCatalog.Stage.NURSERY, castbridge.core.learn.LearnCatalog.Stage.PRIMARY -> KID
                castbridge.core.learn.LearnCatalog.Stage.SECONDARY -> TEEN12
                else -> TEEN16
            }
        }
    }
}

/** What a parent can block by category (docs/PARENTAL.md). LEARN and NAVIGATION can never be blocked. */
enum class Category(val code: String, val label: String, val blockable: Boolean) {
    GAMES("games", "Jeux (Quiz, Échecs)", true),
    DOWNLOADS("downloads", "Téléchargements", true),
    INTERNET("internet", "Internet et tests réseau", true),
    ADMIN("admin", "Administration", true),
    SSH("ssh", "SSH", true),
    SETTINGS("settings", "Réglages", true),
    LEARN("learn", "Apprendre", false),
    /** Home, BACK, help, the parental screen itself: always reachable. */
    NAVIGATION("nav", "Accueil et retour", false);

    companion object {
        fun of(code: String?) = values().firstOrNull { it.code == code }
        val BLOCKABLE: List<Category> = values().filter { it.blockable }
    }
}

/** What time rules count: playing videos, playing games, using the download screen, and (whole-TV supervision) any other app. */
enum class UseKind(val code: String, val label: String) {
    PLAY("play", "Lecture"), GAMES("games", "Jeux"), DOWNLOADS("downloads", "Téléchargements"), APPS("apps", "Autres applications");

    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

/** Allowed hours of the day, minutes since midnight. A window with [fromMin] > [toMin] crosses midnight (e.g. 20:00 to 07:00). */
data class TimeWindow(val fromMin: Int, val toMin: Int) {
    fun contains(nowMin: Int) = if (fromMin <= toMin) nowMin in fromMin until toMin else nowMin >= fromMin || nowMin < toMin
    /** Minutes left until the window closes (only meaningful when [contains]). */
    fun minutesLeft(nowMin: Int): Int = if (toMin > nowMin) toMin - nowMin else toMin + 1440 - nowMin
    fun text() = "${fmt(fromMin)} à ${fmt(toMin)}"

    companion object {
        fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
        fun valid(t: TimeWindow) = t.fromMin in 0..1439 && t.toMin in 0..1439 && t.fromMin != t.toMin
    }
}

/** A child (or teen) profile. [learnId] links it to a student of « Apprendre » when it was imported from there. */
data class ChildProfile(
    val id: String,
    val name: String,
    val age: AgeBand = AgeBand.KID,
    val learnId: String? = null,
    /** Simplified home: Apprendre, games, allowed videos. Left only with the parental PIN. */
    val kidMode: Boolean = true,
    val blocked: Set<Category> = setOf(Category.DOWNLOADS, Category.INTERNET, Category.ADMIN, Category.SSH, Category.SETTINGS),
    /** Allowed hours; null = all day. */
    val window: TimeWindow? = null,
    /** Minutes per day over [kinds]; 0 = no limit. */
    val dailyLimitMin: Int = 0,
    val kinds: Set<UseKind> = UseKind.values().toSet(),
)

enum class RuleKind(val code: String, val label: String) {
    FILE("file", "Vidéo"), KEYWORD("keyword", "Mot-clé dans le nom"), VOLUME("volume", "Dossier (TV ou clé USB)");
    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

/** "Every video whose name contains [match] is [rating]" (or this file, or everything on this volume). */
data class RatingRule(val kind: RuleKind, val match: String, val rating: Rating)

/** Above the profile's age: hidden from the library, or shown but asking the parental PIN when played. */
enum class OverAge(val code: String, val label: String) {
    HIDE("hide", "Masquer"), LOCK("lock", "Verrouiller par le code parental");
    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

data class ParentalConfig(
    /** Incremented at every save: a phone editing an old copy is told to reload. */
    val rev: Int = 0,
    val enabled: Boolean = false,
    val activeProfile: String? = null,
    val profiles: List<ChildProfile> = emptyList(),
    val rules: List<RatingRule> = emptyList(),
    /** Rating given to a video nobody classified. Adult by default: a new video is not shown to a child until a parent looked at it. */
    val unrated: Rating = Rating.ADULT,
    val overAge: OverAge = OverAge.HIDE,
    /** How long the parental PIN unlocks the TV on the TV itself. */
    val sessionMin: Int = 30,
) {
    fun profile(id: String?) = profiles.firstOrNull { it.id == id }
    fun active(): ChildProfile? = profile(activeProfile)

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "rev" to rev, "enabled" to enabled, "active" to activeProfile,
        "unrated" to unrated.code, "overAge" to overAge.code, "sessionMin" to sessionMin,
        "profiles" to profiles.map { p ->
            linkedMapOf("id" to p.id, "name" to p.name, "age" to p.age.code, "learnId" to p.learnId, "kidMode" to p.kidMode,
                "blocked" to p.blocked.map { it.code }, "window" to p.window?.let { linkedMapOf("from" to it.fromMin, "to" to it.toMin) },
                "limitMin" to p.dailyLimitMin, "kinds" to p.kinds.map { it.code })
        },
        "rules" to rules.map { linkedMapOf("k" to it.kind.code, "m" to it.match, "r" to it.rating.code) },
    )

    fun toJson(): String = JsonLite.write(toMap())

    companion object {
        const val MAX_PROFILES = 8
        const val MAX_RULES = 500
        const val MAX_NAME = 24
        const val MAX_MATCH = 80
        val LIMIT_CHOICES = listOf(0, 30, 45, 60, 90, 120, 180)

        /** Parses and validates a config (from storage or from a phone). Throws IllegalArgumentException with a French reason. */
        fun parse(json: String): ParentalConfig = fromMap(runCatching { JsonLite.obj(json) }.getOrElse { throw IllegalArgumentException("Configuration illisible.") })

        @Suppress("UNCHECKED_CAST")
        fun fromMap(o: Map<String, Any?>): ParentalConfig {
            fun list(k: String) = (o[k] as? List<Any?>).orEmpty().map { it as? Map<String, Any?> ?: throw IllegalArgumentException("Configuration invalide ($k).") }
            val profiles = list("profiles").map { m ->
                val id = (m["id"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,16}")) } ?: throw IllegalArgumentException("Identifiant de profil invalide.")
                val name = (m["name"] as? String)?.trim().orEmpty()
                if (name.isEmpty() || name.length > MAX_NAME || name.any { it.isISOControl() }) throw IllegalArgumentException("Prénom du profil invalide (1 à $MAX_NAME caractères).")
                val w = (m["window"] as? Map<String, Any?>)?.let {
                    TimeWindow((it["from"] as? Number)?.toInt() ?: -1, (it["to"] as? Number)?.toInt() ?: -1).also { t ->
                        if (!TimeWindow.valid(t)) throw IllegalArgumentException("Plage horaire invalide.")
                    }
                }
                val limit = (m["limitMin"] as? Number)?.toInt() ?: 0
                if (limit < 0 || limit > 1440) throw IllegalArgumentException("Durée quotidienne invalide.")
                ChildProfile(
                    id = id, name = name, age = AgeBand.of(m["age"] as? String) ?: AgeBand.KID,
                    learnId = (m["learnId"] as? String)?.take(32), kidMode = m["kidMode"] as? Boolean ?: true,
                    blocked = (m["blocked"] as? List<Any?>).orEmpty().mapNotNull { Category.of(it as? String) }.filter { it.blockable }.toSet(),
                    window = w, dailyLimitMin = limit,
                    kinds = (m["kinds"] as? List<Any?>)?.mapNotNull { UseKind.of(it as? String) }?.toSet() ?: UseKind.values().toSet(),
                )
            }
            if (profiles.size > MAX_PROFILES) throw IllegalArgumentException("$MAX_PROFILES profils au maximum.")
            if (profiles.map { it.id }.toSet().size != profiles.size) throw IllegalArgumentException("Deux profils ont le même identifiant.")
            val rules = list("rules").map { m ->
                val match = (m["m"] as? String)?.trim().orEmpty()
                if (match.isEmpty() || match.length > MAX_MATCH || match.any { it.isISOControl() }) throw IllegalArgumentException("Règle de classement invalide.")
                RatingRule(RuleKind.of(m["k"] as? String) ?: throw IllegalArgumentException("Type de règle inconnu."), match,
                    Rating.of(m["r"] as? String) ?: throw IllegalArgumentException("Classification inconnue."))
            }
            if (rules.size > MAX_RULES) throw IllegalArgumentException("$MAX_RULES règles au maximum.")
            val active = (o["active"] as? String)?.takeIf { a -> profiles.any { it.id == a } }
            val session = ((o["sessionMin"] as? Number)?.toInt() ?: 30).coerceIn(5, 240)
            return ParentalConfig(
                rev = (o["rev"] as? Number)?.toInt() ?: 0, enabled = o["enabled"] as? Boolean ?: false, activeProfile = active,
                profiles = profiles, rules = rules, unrated = Rating.of(o["unrated"] as? String) ?: Rating.ADULT,
                overAge = OverAge.of(o["overAge"] as? String) ?: OverAge.HIDE, sessionMin = session,
            )
        }
    }
}

/** Why something is refused (code: category, rating, window, limit, nopin...) and the French sentence for the screen. */
data class Decision(val allowed: Boolean, val code: String = "ok", val reason: String? = null, val minutesLeft: Int? = null) {
    companion object {
        val ALLOW = Decision(true)
        fun deny(code: String, reason: String) = Decision(false, code, reason)
    }
}

/** Pure rules: no clock, no storage, easy to test. */
object ParentalRules {
    /** The rating of a video: a rule on the file wins; else the strictest matching keyword / volume rule; else [ParentalConfig.unrated]. */
    fun ratingOf(cfg: ParentalConfig, name: String, volumeLabel: String? = null): Rating {
        val n = name.lowercase()
        cfg.rules.firstOrNull { it.kind == RuleKind.FILE && it.match.equals(name, ignoreCase = true) }?.let { return it.rating }
        val hits = cfg.rules.filter {
            (it.kind == RuleKind.KEYWORD && n.contains(it.match.lowercase())) ||
                (it.kind == RuleKind.VOLUME && volumeLabel != null && it.match.equals(volumeLabel, ignoreCase = true))
        }
        return hits.maxByOrNull { it.rating.age }?.rating ?: cfg.unrated
    }

    fun videoAllowed(profile: ChildProfile, rating: Rating) = profile.age.allows(rating)

    fun categoryBlocked(profile: ChildProfile, c: Category) = c.blockable && c in profile.blocked

    /** Time rules for [kind] at [nowMin] (minutes since midnight) with [usedMs] already spent today over the profile's kinds. */
    fun timeVerdict(p: ChildProfile, kind: UseKind, nowMin: Int, usedMs: Long): Decision {
        if (kind !in p.kinds) return Decision.ALLOW
        var left: Int? = null
        p.window?.let { w ->
            if (!w.contains(nowMin)) return Decision.deny("window", "${p.name} peut utiliser la TV de ${w.text()}.")
            left = w.minutesLeft(nowMin)
        }
        if (p.dailyLimitMin > 0) {
            val limitMs = p.dailyLimitMin * 60_000L
            if (usedMs >= limitMs) return Decision.deny("limit", "Le temps d'écran du jour est terminé (${p.dailyLimitMin} min). À demain !")
            val l = ((limitMs - usedMs + 59_999) / 60_000).toInt()
            left = minOf(left ?: Int.MAX_VALUE, l)
        }
        return Decision(true, "ok", null, left)
    }

    /** Tiles of the simplified home: what a child sees in kid mode (by label, see PlayerActivity.homeTools). */
    val KID_HOME = listOf("Apprendre", "Quiz", "Échecs", "Bibliothèque", "Boutique", "Aide", "Contrôle parental")

    fun kidHome(labels: List<String>, p: ChildProfile): List<String> = labels.filter { l ->
        l in KID_HOME && !(l in listOf("Quiz", "Échecs") && Category.GAMES in p.blocked)
    }
}

/** BACK and HOME are never blocked, whatever the rules (docs/PARENTAL.md). */
object ParentalKeys {
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    fun neverBlocked(keyCode: Int) = keyCode == KEYCODE_BACK || keyCode == KEYCODE_HOME
}

/** What a locked screen always offers, whatever the reason: enter the parental PIN, or leave to the home. */
object LockScreenModel {
    enum class Action(val label: String) { ENTER_PIN("Saisir le PIN parental"), GO_HOME("Retour à l'accueil") }
    fun actions(@Suppress("UNUSED_PARAMETER") reason: String?): List<Action> = listOf(Action.ENTER_PIN, Action.GO_HOME)
}
