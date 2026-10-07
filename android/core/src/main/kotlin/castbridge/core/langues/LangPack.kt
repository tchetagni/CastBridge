package castbridge.core.langues

import castbridge.core.quiz.Json
import castbridge.core.quiz.Json.int
import castbridge.core.quiz.Json.list
import castbridge.core.quiz.Json.map
import castbridge.core.quiz.Json.str

/**
 * Language pack = one text lot's content (`type: "langue"`, own format, separate from `content/learn` so that older readers never
 * see it; docs/LANGUES.md § 3). Files: `langue.json` (header + units) and `media.json` (manifest of the media it references).
 * References to media are `m:<media id>`; the files themselves live in the media lot, never in the text lot.
 */
data class VocabItem(val id: String, val term: String, val reading: String?, val gloss: String, val pos: String?, val audio: String?, val image: String?)
data class DialogueLine(val who: String, val text: String, val reading: String?, val translation: String, val audio: String?)
data class Dialogue(val id: String, val title: String, val lines: List<DialogueLine>, val audio: String?, val video: String?)
data class GrammarNote(val id: String, val title: String, val md: String, val examples: List<Pair<String, String>>)
data class Story(val id: String, val title: String, val paragraphs: List<DialogueLine>)
data class Card(val id: String, val vocab: String, val image: String?)
data class AnimRef(val id: String, val kind: String, val label: String)

enum class LangExerciseKind(val key: String) {
    DICTATION("dictation"), MATCH("match"), ORDER("order"), MCQ("mcq"), CLOZE("cloze"), TRANSLATE("translate"),
    TRUEFALSE("truefalse"), SPEAK("speak"), WRITE("write"), STROKES("strokes");
    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } }
}

/**
 * [answers]: accepted answers (dictation, cloze, translate, order = the right sentence); [choices]/[correct]: mcq/truefalse; [pairs]: match; [words]: shuffled pieces (order).
 * Additive, optional (docs/LANGUES.md § 3.2, CONTENT-ARCHITECTURE § 5), absent = `null` / empty: [explanation] = the commented correction;
 * [wrongWhy] = why each wrong choice is wrong, ALIGNED on [choices] (same index; "" when there is nothing to say for that choice).
 */
data class LangExercise(
    val id: String, val kind: LangExerciseKind, val prompt: String, val audio: String?, val answers: List<String>,
    val choices: List<String>, val correct: Int?, val pairs: List<Pair<String, String>>, val words: List<String>, val model: String?, val skill: LangSkill,
    val explanation: String? = null, val wrongWhy: List<String> = emptyList(),
)

data class LangUnit(
    val id: String, val title: String, val minutes: Int, val skills: List<LangSkill>, val prerequisites: List<String>,
    val vocab: List<VocabItem>, val dialogues: List<Dialogue>, val grammar: List<GrammarNote>, val exercises: List<LangExercise>,
    val stories: List<Story>, val cards: List<Card>, val animations: List<AnimRef>,
)

data class MediaEntry(
    val id: String, val file: String, val kind: String, val bytes: Long, val durationMs: Int?, val license: String,
    val author: String?, val source: String?, val url: String?, val engine: String?, val synthetic: Boolean, val lang: String?, val voiceLicense: String? = null,
)

data class LangPack(val id: String, val version: Int, val parts: LangLots.Parts, val title: String, val state: String, val units: List<LangUnit>, val media: Map<String, MediaEntry>) {
    fun mediaRefs(): List<String> = units.flatMap { u ->
        u.vocab.flatMap { listOfNotNull(it.audio, it.image) } + u.dialogues.flatMap { d -> listOfNotNull(d.audio, d.video) + d.lines.mapNotNull { it.audio } } +
            u.exercises.mapNotNull { it.audio } + u.stories.flatMap { s -> s.paragraphs.mapNotNull { it.audio } } + u.cards.mapNotNull { it.image }
    }
}

object LangPackJson {
    const val FORMAT = 1
    class ParseError(msg: String) : IllegalArgumentException(msg)

    @Suppress("UNCHECKED_CAST") private fun Any?.o(w: String): Map<String, Any?> = this as? Map<String, Any?> ?: throw ParseError("$w : objet attendu")
    private fun Map<String, Any?>.req(k: String, w: String) = str(k)?.takeIf { it.isNotBlank() } ?: throw ParseError("$w : \"$k\" manquant")
    private fun Map<String, Any?>.l(k: String): List<Any?> = list(k) ?: emptyList()
    private fun Map<String, Any?>.ss(k: String): List<String> = l(k).mapNotNull { it as? String }

    fun parse(files: Map<String, String>): LangPack {
        val root = try { Json.obj(files["langue.json"] ?: throw ParseError("langue.json manquant")) } catch (e: Json.ParseError) { throw ParseError("langue.json : ${e.message}") }
        if (root.str("type") != "langue") throw ParseError("langue.json : \"type\" doit valoir \"langue\"")
        val fmt = root.int("format") ?: 1
        if (fmt > FORMAT) throw ParseError("langue.json : format $fmt non pris en charge (max $FORMAT)")
        val id = root.req("id", "langue.json")
        val parts = LangLots.parse(id.substringBeforeLast("-v")) ?: throw ParseError("langue.json : id « $id » n'est pas <cible>-<niveau>-<thème>-<départ>")
        val declared = LangLots.Parts(Lang.of(root.str("target")) ?: throw ParseError("langue.json : langue cible inconnue"), LangLevel.of(root.str("level")) ?: throw ParseError("langue.json : niveau inconnu"),
            root.req("theme", "langue.json"), Lang.of(root.str("source")) ?: throw ParseError("langue.json : langue de départ inconnue"))
        if (declared != parts) throw ParseError("langue.json : id et champs target/level/theme/source incohérents")
        val units = root.l("units").mapIndexed { k, u -> unit(u.o("unité #$k")) }
        val media = files["media.json"]?.let { t ->
            try { Json.obj(t) } catch (e: Json.ParseError) { throw ParseError("media.json : ${e.message}") }.l("media").mapIndexed { k, m -> media(m.o("media.json #$k")) }
        }.orEmpty().associateBy { it.id }
        return LangPack(id, root.int("version") ?: 1, parts, root.req("title", "langue.json"), root.str("state") ?: "review", units, media)
    }

    private fun media(m: Map<String, Any?>): MediaEntry {
        val id = m.req("id", "media.json")
        return MediaEntry(id, m.req("file", "media $id"), m.req("kind", "media $id"), (m["bytes"] as? Number)?.toLong() ?: throw ParseError("media $id : \"bytes\" manquant"),
            m.int("durationMs"), m.req("license", "media $id"), m.str("author"), m.str("source"), m.str("url"), m.str("engine"), (m["synthetic"] as? Boolean) ?: false, m.str("lang"), m.str("voiceLicense"))
    }

    private fun line(m: Map<String, Any?>, w: String) = DialogueLine(m.str("who") ?: "", m.req("text", w), m.str("reading"), m.req("tr", w), m.str("audio"))

    private fun unit(m: Map<String, Any?>): LangUnit {
        val id = m.req("id", "unité"); val w = "unité $id"
        return LangUnit(id, m.req("title", w), m.int("minutes") ?: 10, m.ss("skills").map { LangSkill.of(it) ?: throw ParseError("$w : compétence inconnue « $it »") }, m.ss("prerequisites"),
            m.l("vocab").map { v -> v.o("$w : vocab").let { VocabItem(it.req("id", w), it.req("term", w), it.str("reading"), it.req("gloss", w), it.str("pos"), it.str("audio"), it.str("image")) } },
            m.l("dialogues").map { d -> d.o("$w : dialogue").let { Dialogue(it.req("id", w), it.req("title", w), it.l("lines").map { x -> line(x.o("$w : réplique"), w) }, it.str("audio"), it.str("video")) } },
            m.l("grammar").map { g -> g.o("$w : grammaire").let { x -> GrammarNote(x.req("id", w), x.req("title", w), x.req("md", w), x.l("examples").map { e -> e.o(w).let { it.req("text", w) to it.req("tr", w) } }) } },
            m.l("exercises").map { exercise(it.o("$w : exercice")) },
            m.l("stories").map { s -> s.o("$w : histoire").let { x -> Story(x.req("id", w), x.req("title", w), x.l("paragraphs").map { p -> line(p.o(w), w) }) } },
            m.l("cards").map { c -> c.o("$w : carte").let { Card(it.req("id", w), it.req("vocab", w), it.str("image")) } },
            m.l("animations").map { a -> a.o("$w : animation").let { AnimRef(it.req("id", w), it.req("kind", w), it.str("label") ?: "") } })
    }

    private fun exercise(m: Map<String, Any?>): LangExercise {
        val id = m.req("id", "exercice"); val w = "exercice $id"
        val kind = LangExerciseKind.of(m.str("kind")) ?: throw ParseError("$w : \"kind\" inconnu « ${m.str("kind")} »")
        return LangExercise(id, kind, m.req("prompt", w), m.str("audio"), m.ss("answers"), m.ss("choices"), m.int("correct"),
            m.l("pairs").map { p -> p.o(w).let { it.req("a", w) to it.req("b", w) } }, m.ss("words"), m.str("model"), LangSkill.of(m.str("skill")) ?: defaultSkill(kind),
            m.str("explanation")?.takeIf { it.isNotBlank() }, m.l("wrongWhy").map { it as? String ?: "" })   // additive: a missing key, or a `wrongWhy` that is not a list, reads as « nothing »
    }

    fun defaultSkill(k: LangExerciseKind) = when (k) {
        LangExerciseKind.DICTATION -> LangSkill.LISTENING; LangExerciseKind.SPEAK -> LangSkill.SPEAKING
        LangExerciseKind.WRITE, LangExerciseKind.TRANSLATE, LangExerciseKind.STROKES, LangExerciseKind.ORDER -> LangSkill.WRITING
        else -> LangSkill.READING
    }
}

/** Licences we may redistribute (docs/LANGUES.md § 7); "CASTBRIDGE-ORIGINAL" = made by us, released under CC BY-SA 4.0 with the app's content. */
object LangLicences {
    /** Content licences: the maximum of free licences (owner decision 2026-10-01); never NC / ND. */
    val allowed = setOf("CC0", "PD", "CC-BY-2.0", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0", "MIT", "Apache-2.0", "BSD", "OFL-1.1", "CASTBRIDGE-ORIGINAL")
    /** Voices: the same, plus GPL-3.0 for the output of a free engine (espeak-ng). */
    val allowedVoices = allowed + "GPL-3.0"
    private val needsAttribution = setOf("CC-BY-2.0", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0")
    fun needsAttribution(l: String) = l in needsAttribution
}

/** Validation of a parsed pack: returns French messages, empty = valid. Same spirit as `LessonValidator`. */
object LangValidator {
    fun validate(p: LangPack): List<String> {
        val e = ArrayList<String>()
        val seen = HashSet<String>()
        fun uniq(id: String, w: String) { if (!seen.add(id)) e += "$w : id « $id » en double"; if (!id.startsWith(p.parts.scope)) e += "$w : id « $id » doit commencer par ${p.parts.scope}" }
        if (p.units.isEmpty()) e += "aucune unité"
        val unitIds = p.units.map { it.id }.toSet()
        for (u in p.units) {
            uniq(u.id, "unité"); val w = "unité ${u.id}"
            u.prerequisites.forEach { if (it !in unitIds) e += "$w : prérequis « $it » inconnu" else if (it == u.id) e += "$w : prérequis cyclique" }
            u.vocab.forEach { uniq(it.id, w); if (p.parts.target.cjk && it.reading == null) e += "$w : « ${it.term} » sans lecture (pinyin / kana)" }
            u.dialogues.forEach { d -> uniq(d.id, w); if (d.lines.size < 2) e += "dialogue ${d.id} : au moins 2 répliques" }
            u.grammar.forEach { uniq(it.id, w) }; u.stories.forEach { uniq(it.id, w) }; u.cards.forEach { uniq(it.id, w) }
            val vocab = u.vocab.map { it.id }.toSet()
            u.cards.forEach { if (it.vocab !in vocab) e += "carte ${it.id} : mot « ${it.vocab} » absent de l'unité" }
            for (x in u.exercises) {
                uniq(x.id, w); val xw = "exercice ${x.id}"
                when (x.kind) {
                    LangExerciseKind.MCQ -> if (x.choices.size !in 2..6 || x.choices.toSet().size != x.choices.size || x.correct !in x.choices.indices) e += "$xw : QCM invalide (2-6 choix distincts, index valide)"
                    LangExerciseKind.TRUEFALSE -> if (x.correct !in 0..1) e += "$xw : vrai/faux demande correct = 0 ou 1"
                    LangExerciseKind.MATCH -> if (x.pairs.size !in 2..6 || x.pairs.map { it.first }.toSet().size != x.pairs.size) e += "$xw : appariement 2-6 paires sans doublon"
                    LangExerciseKind.ORDER -> if (x.words.size < 3 || x.answers.isEmpty()) e += "$xw : ordre des mots demande ≥ 3 morceaux et la phrase juste"
                    LangExerciseKind.DICTATION -> if (x.audio == null || x.answers.isEmpty()) e += "$xw : dictée demande un audio et la réponse"
                    LangExerciseKind.SPEAK, LangExerciseKind.WRITE -> if (x.model.isNullOrBlank()) e += "$xw : production demande une réponse modèle"
                    else -> if (x.answers.isEmpty()) e += "$xw : réponse manquante"
                }
            }
        }
        for (ref in p.mediaRefs()) {
            if (!ref.startsWith("m:")) { e += "référence média « $ref » : préfixe m: attendu"; continue }
            if (ref.removePrefix("m:") !in p.media) e += "média « $ref » absent de media.json"
        }
        for (m in p.media.values) {
            val w = "média ${m.id}"
            if (m.license !in LangLicences.allowed) e += "$w : licence « ${m.license} » non redistribuable"
            if (LangLicences.needsAttribution(m.license) && (m.author.isNullOrBlank() || m.source.isNullOrBlank() || m.url.isNullOrBlank())) e += "$w : auteur, source et URL obligatoires pour ${m.license}"
            if (m.engine != null && !m.synthetic) e += "$w : audio produit par un moteur de synthèse ($) doit être marqué synthetic".replace("($)", "(${m.engine})")
            if (m.synthetic && m.voiceLicense !in LangLicences.allowedVoices) e += "$w : licence de la voix « ${m.voiceLicense} » absente ou non libre"
            if (m.bytes <= 0) e += "$w : taille invalide"
            if (m.kind !in setOf("audio", "video", "image")) e += "$w : kind « ${m.kind} » inconnu"
        }
        return e
    }
}
