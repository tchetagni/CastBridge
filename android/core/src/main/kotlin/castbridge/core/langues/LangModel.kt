package castbridge.core.langues

import castbridge.core.learn.LearnScopes
import castbridge.core.lots.LotId

/** Target languages of the « Langues » category (docs/LANGUES.md). [cjk]: needs system CJK fonts and the heavier budget profile. */
enum class Lang(val code: String, val fr: String, val en: String, val cjk: Boolean = false) {
    ZH("zh", "Chinois (mandarin)", "Chinese (Mandarin)", true),
    JA("ja", "Japonais", "Japanese", true),
    EN("en", "Anglais", "English"),
    DE("de", "Allemand", "German"),
    FR("fr", "Français", "French"),
    IT("it", "Italien", "Italian"),
    ES("es", "Espagnol", "Spanish");

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code?.lowercase() }
        /** Languages the learner may START from (the pupils' languages); more can be added without changing the format. */
        val sources: List<Lang> = listOf(FR, EN)
    }
}

/** The eight levels: CEFR A0 (discovery, not in the CEFR) to C2, then « natif » (idiomatic richness, registers, culture). */
enum class Level(val key: String, val cefr: String) {
    A0("a0", "pré-A1"), A1("a1", "A1"), A2("a2", "A2"), B1("b1", "B1"), B2("b2", "B2"), C1("c1", "C1"), C2("c2", "C2"), NATIF("natif", "au-delà de C2");

    fun next(): Level? = entries.getOrNull(ordinal + 1)
    fun previous(): Level? = entries.getOrNull(ordinal - 1)

    companion object { fun of(key: String?): Level? = entries.firstOrNull { it.key == key?.lowercase() } }
}

/** The four skills (CEFR): listening, reading, speaking, writing. */
enum class Skill(val key: String) {
    LISTENING("co"), READING("ce"), SPEAKING("po"), WRITING("pe");

    companion object { fun of(key: String?): Skill? = entries.firstOrNull { it.key == key?.lowercase() } }
}

/**
 * Lot naming. A language lot is `LotId("langues", "<target>-<level>-<theme>-<source>")` (text, ≤ 3 MB, goes to the TV) and its
 * media twin is `LotId("langues-media", <same scope>)` (audio/video/images, ≤ 100 MB, phone, copied to the TV on request).
 * The translation language is in the scope because glosses and translations belong to the lot: zh-a1-salut-fr ≠ zh-a1-salut-en.
 */
object LangLots {
    const val FEATURE = "langues"
    const val MEDIA_FEATURE = "langues-media"

    data class Parts(val target: Lang, val level: Level, val theme: String, val source: Lang) {
        val scope get() = "${target.code}-${level.key}-$theme-${source.code}"
    }

    private val theme = Regex("[a-z0-9]{1,16}")

    fun scope(target: Lang, level: Level, theme: String, source: Lang): String {
        require(this.theme.matches(theme)) { "thème « $theme » invalide : 1 à 16 caractères a-z0-9, sans tiret" }
        require(target != source) { "la langue de départ doit différer de la langue cible" }
        return Parts(target, level, theme, source).scope.also { require(LearnScopes.valid(it)) { "scope « $it » invalide" } }
    }

    fun parse(scope: String): Parts? {
        val p = scope.split('-')
        if (p.size != 4) return null
        val t = Lang.of(p[0]) ?: return null; val l = Level.of(p[1]) ?: return null; val s = Lang.of(p[3]) ?: return null
        return if (theme.matches(p[2]) && t != s) Parts(t, l, p[2], s) else null
    }

    fun textId(parts: Parts) = LotId(FEATURE, parts.scope)
    fun mediaId(parts: Parts) = LotId(MEDIA_FEATURE, parts.scope)
    fun isMedia(id: LotId) = id.feature == MEDIA_FEATURE
    fun isLanguage(id: LotId) = (id.feature == FEATURE || id.feature == MEDIA_FEATURE) && parse(id.scope) != null
}
