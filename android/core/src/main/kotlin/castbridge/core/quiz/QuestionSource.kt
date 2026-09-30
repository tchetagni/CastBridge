package castbridge.core.quiz

import java.io.File
import java.io.IOException

/**
 * Where the questions come from: the bank bundled in the app ([EmbeddedQuestionSource]) and the CastBridge server, whose
 * answers are kept in a small bounded cache on the TV ([CachedQuestionSource], filled by [QuizSync]) with the bundled bank
 * as the offline fallback. The game only ever sees a [QuizBank] snapshot. Exchange format: docs/QUIZ.md.
 */
interface QuestionSource {
    /** Short description for logs / the about screen ("embarquée", "cache serveur du 2026-09-30"…). */
    val origin: String
    /** The questions available now (never blocks on the network). */
    fun bank(): QuizBank
}

/** The banks shipped in the app's resources (general knowledge + school tracks). */
class EmbeddedQuestionSource(private val resources: List<String> = DEFAULT_RESOURCES) : QuestionSource {
    override val origin = "embarquée"
    private val bank: QuizBank by lazy {
        resources.mapNotNull { r -> EmbeddedQuestionSource::class.java.getResourceAsStream(r)?.use { String(it.readBytes(), Charsets.UTF_8) } }
            .map { QuizBank.parse(it) }
            .fold(QuizBank(emptyList())) { acc, b -> acc.merge(b) }
    }
    override fun bank() = bank

    companion object {
        val DEFAULT_RESOURCES = listOf("/castbridge/quiz/questions.json", "/castbridge/quiz/questions-school.json")
    }
}

/** A remote question source; the CastBridge server is reached through [QuizSync] (GET /api/v1/quiz/questions). */
interface RemoteQuestionApi {
    /** Exchange-format JSON of the questions updated after [sinceIso] (null = everything), page by page; null = unreachable. */
    fun fetch(sinceIso: String?, page: Int): String?
}

/**
 * Bundled bank + questions received from the server, stored in ONE cache file of at most [maxBytes] (the TV has little
 * storage). A server question replaces the bundled one with the same id; questions whose status is not "approved" are
 * kept out of games. Any problem with the cache (missing, too big, corrupt, unknown version) = the bundled bank alone:
 * the quiz always works offline.
 */
class CachedQuestionSource(
    private val cacheFile: File,
    private val fallback: QuestionSource = EmbeddedQuestionSource(),
    private val maxBytes: Long = 2L shl 20,
) : QuestionSource {
    @Volatile private var merged: QuizBank? = null
    @Volatile override var origin: String = fallback.origin; private set

    override fun bank(): QuizBank = merged ?: synchronized(this) {
        merged ?: load().also { merged = it }
    }

    private fun load(): QuizBank {
        val base = fallback.bank()
        var deleted: Set<String> = emptySet()
        val cached = runCatching {
            if (!cacheFile.isFile || cacheFile.length() > maxBytes) null
            else {
                val text = cacheFile.readText(Charsets.UTF_8)
                QuizBank.parse(text).takeIf { it.validate().isEmpty() }?.also {
                    // questions the server withdrew (deleted, rejected, back to draft) are also taken out of the bundled bank
                    deleted = (Json.obj(text)["deleted"] as? List<*>).orEmpty().filterIsInstance<String>().toSet()
                }
            }
        }.getOrNull()
        origin = if (cached != null) "${fallback.origin} + serveur (${cached.all.size} questions)" else fallback.origin
        serverQuestions = cached?.all?.size ?: 0
        if (cached == null) return base
        return QuizBank(base.all.filter { it.id !in deleted }).merge(cached)
    }

    /** Number of questions received from the server (0 = bundled bank only). */
    fun serverCount(): Int { bank(); return serverQuestions }
    @Volatile private var serverQuestions = 0

    /**
     * Stores a server answer as the new cache, after checking it (size, format, every question valid). Written to a
     * temporary file then renamed, so a power cut never leaves a half-written cache. Returns null if stored, else why not.
     */
    fun update(json: String): String? {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) return "cache trop gros (${bytes.size} octets, max $maxBytes)"
        val bank = try { QuizBank.parse(json) } catch (e: Json.ParseError) { return "format invalide : ${e.message}" }
        bank.validate().takeIf { it.isNotEmpty() }?.let { return "questions invalides : ${it.take(3).joinToString("; ")}" }
        try {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); if (!tmp.renameTo(cacheFile)) throw IOException("rename failed") }
        } catch (e: IOException) { return "écriture impossible : ${e.message}" }
        synchronized(this) { merged = null }
        return null
    }

    /** Forgets the server questions (back to the bundled bank). */
    fun clear() { cacheFile.delete(); synchronized(this) { merged = null } }
}
