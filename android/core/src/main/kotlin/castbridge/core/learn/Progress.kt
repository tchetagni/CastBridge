package castbridge.core.learn

import castbridge.core.owner.SafeFile
import castbridge.core.quiz.Json
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Students of the TV (at most [LearnProgress.MAX_PROFILES]), and what each one did: lessons seen, exercises, stars,
 * badges, where to resume, spaced reviews, mock exams, time per subject, and an exam date for the countdown.
 * No sensitive data: a first name (or nickname), an avatar number and a class. Stored in one JSON file on the TV.
 */
data class Profile(val id: String, val name: String, val avatar: Int, val level: String?, val exam: String? = null,
                   val examDate: String? = null, val createdAt: Long = 0)

class LessonState(val lesson: String, val pack: String, val subject: String) {
    var page = 0; var pages = 0; var seen = false; var completed = false; var stars = 0; var timeMs = 0L; var lastAt = 0L
    /** Content hash (lot index) of the version of the lesson the student last went through; null = before lots / unknown. */
    var hash: String? = null
}

/** An exercise tried by a student. [box] = Leitner box of the spaced review (0 = not in review). */
class ExerciseState(val exercise: String, val pack: String, val subject: String, val lesson: String?) {
    var attempts = 0; var correct = 0; var lastCorrect = false; var box = 0; var dueAt = 0L; var lastAt = 0L
    /** Best score ever (0..1, points earned / points of the exercise); kept when two devices are merged ([LearnMerge]). */
    var best = 0.0
}

data class MockRecord(val pack: String, val mock: String, val subject: String, val score: Double, val at: Long, val durationMs: Long)
data class Resume(val pack: String, val lesson: String, val page: Int, val at: Long)
/** Telemetry event (names are stable: see [LearnProgress.EVENTS] and docs/LEARN.md). */
data class LearnEvent(val name: String, val at: Long, val profile: String, val data: Map<String, Any?>)

class StudentProgress(val profile: String) {
    val lessons = LinkedHashMap<String, LessonState>()
    val exercises = LinkedHashMap<String, ExerciseState>()
    val mocks = ArrayList<MockRecord>()
    val badges = LinkedHashSet<String>()
    var resume: Resume? = null
    var streak = 0; var lastDay: String? = null; var reviewsDone = 0
    val timeBySubject = LinkedHashMap<String, Long>()
}

class LearnState {
    val profiles = ArrayList<Profile>()
    val progress = LinkedHashMap<String, StudentProgress>()
    /** Last events, oldest first (ring of [LearnProgress.MAX_EVENTS]), for the future upload to the server. */
    val events = ArrayList<LearnEvent>()
    fun of(profile: String) = progress.getOrPut(profile) { StudentProgress(profile) }
}

/** Rules of the progression; pure logic over [LearnState] (saved by [LearnStore]). [now] in ms, [zone] for day boundaries. */
class LearnProgress(val state: LearnState = LearnState(), private val zone: ZoneId = ZoneId.systemDefault()) {
    companion object {
        const val MAX_PROFILES = 6
        const val MAX_EVENTS = 500
        const val MAX_NAME = 20
        /** Leitner intervals (days) of boxes 1..5. */
        val INTERVAL_DAYS = intArrayOf(0, 1, 2, 4, 8, 16)
        /** Time counted for one page at most (the TV may stay on a page while nobody is there). */
        const val MAX_PAGE_MS = 5 * 60_000L
        /** Stable telemetry event names. */
        val EVENTS = listOf("profile_created", "lesson_view", "lesson_complete", "exercise_result", "review_result", "mock_exam_result", "badge_earned", "pack_installed")
        val AVATARS = listOf("🦁", "🐘", "🦒", "🐢", "🦜", "🐒", "🐆", "🦓", "🐊", "🦉", "🐝", "🐬")

        val BADGES: Map<String, String> = linkedMapOf(
            "first_lesson" to "Première fiche terminée", "lessons_5" to "5 fiches terminées", "lessons_20" to "20 fiches terminées",
            "perfect" to "Sans faute (3 étoiles)", "streak_3" to "3 jours de suite", "streak_7" to "7 jours de suite",
            "mock_10" to "Épreuve blanche ≥ 10/20", "mock_15" to "Épreuve blanche ≥ 15/20", "reviewer" to "10 révisions réussies",
        )
    }

    val profiles: List<Profile> get() = state.profiles

    /** Adds a student; null + reason when full or the name is not acceptable. */
    fun addProfile(name: String, avatar: Int, level: String?, now: Long): Pair<Profile?, String?> {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (state.profiles.size >= MAX_PROFILES) return null to "$MAX_PROFILES profils au maximum"
        if (n.isEmpty()) return null to "prénom vide"
        if (n.length > MAX_NAME) return null to "prénom trop long ($MAX_NAME caractères au plus)"
        if (n.any { it.isDigit() } && n.count { it.isDigit() } > 4) return null to "juste un prénom ou un surnom (pas de numéro)"
        if (state.profiles.any { it.name.equals(n, ignoreCase = true) }) return null to "ce prénom existe déjà"
        if (level != null && LearnCatalog.level(level) == null) return null to "classe inconnue"
        val id = "p" + (1..99).first { k -> state.profiles.none { it.id == "p$k" } }
        val p = Profile(id, n, avatar.coerceIn(0, AVATARS.size - 1), level, createdAt = now)
        state.profiles += p
        event("profile_created", now, id, mapOf("level" to level))
        return p to null
    }

    fun updateProfile(p: Profile) { val i = state.profiles.indexOfFirst { it.id == p.id }; if (i >= 0) state.profiles[i] = p }
    fun removeProfile(id: String) { state.profiles.removeAll { it.id == id }; state.progress.remove(id) }
    fun profile(id: String?) = state.profiles.firstOrNull { it.id == id }

    /** Days left before the exam date of [p] (0 = today, negative = past), null without a date. */
    fun daysToExam(p: Profile, now: Long): Long? = p.examDate?.let { d ->
        runCatching { ChronoUnit.DAYS.between(day(now), LocalDate.parse(d)) }.getOrNull()
    }

    private fun day(now: Long) = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    private fun touchDay(sp: StudentProgress, now: Long) {
        val today = day(now).toString()
        if (sp.lastDay == today) return
        sp.streak = if (sp.lastDay != null && runCatching { LocalDate.parse(sp.lastDay).plusDays(1).toString() == today }.getOrDefault(false)) sp.streak + 1 else 1
        sp.lastDay = today
        if (sp.streak >= 3) earn(sp, "streak_3", now); if (sp.streak >= 7) earn(sp, "streak_7", now)
    }

    /** The student is on [page] of [pages] of a lesson for [dtMs]; the last page completes the lesson. */
    fun lessonPage(profile: String, pack: Pack, lesson: Lesson, page: Int, pages: Int, dtMs: Long, now: Long, hash: String? = null): LessonState {
        val sp = state.of(profile); touchDay(sp, now)
        val st = sp.lessons.getOrPut(lesson.id) { LessonState(lesson.id, pack.id, pack.subject) }
        // the lot's content hash of this lesson: adopted at the first visit, and again once the student reached the end of the new version
        if (hash != null && (st.hash == null || (st.hash != hash && page >= pages - 1))) st.hash = hash
        val first = !st.seen
        st.seen = true; st.page = page; st.pages = pages; st.lastAt = now
        val dt = dtMs.coerceIn(0, MAX_PAGE_MS)
        st.timeMs += dt; sp.timeBySubject[pack.subject] = (sp.timeBySubject[pack.subject] ?: 0) + dt
        sp.resume = if (page >= pages - 1) null else Resume(pack.id, lesson.id, page, now)
        if (first) event("lesson_view", now, profile, mapOf("pack" to pack.id, "lesson" to lesson.id, "subject" to pack.subject))
        if (page >= pages - 1 && !st.completed) {
            st.completed = true
            event("lesson_complete", now, profile, mapOf("pack" to pack.id, "lesson" to lesson.id, "timeMs" to st.timeMs))
            updateStars(sp, pack, lesson, now)
            val done = sp.lessons.values.count { it.completed }
            if (done >= 1) earn(sp, "first_lesson", now); if (done >= 5) earn(sp, "lessons_5", now); if (done >= 20) earn(sp, "lessons_20", now)
        }
        return st
    }

    /**
     * Result of an exercise. Spaced review (Leitner): a failed exercise goes to box 1 (again tomorrow); a success in
     * review moves it up (2, 4, 8, 16 days later) and out of the reviews after box 4; a first-time success never enters.
     */
    fun exerciseResult(profile: String, pack: Pack, x: Exercise, mark: Mark, now: Long, review: Boolean = false): ExerciseState {
        val sp = state.of(profile); touchDay(sp, now)
        val st = sp.exercises.getOrPut(x.id) { ExerciseState(x.id, pack.id, pack.subject, x.lesson) }
        st.attempts++; st.lastAt = now; st.lastCorrect = mark.correct
        if (mark.max > 0) st.best = maxOf(st.best, (mark.earned / mark.max).coerceIn(0.0, 1.0))
        if (mark.correct) st.correct++
        if (!mark.correct) { st.box = 1; st.dueAt = now + INTERVAL_DAYS[1] * 86_400_000L }
        else if (st.box > 0) {
            if (st.box >= 4) { st.box = 0; st.dueAt = 0 } else { st.box++; st.dueAt = now + INTERVAL_DAYS[st.box] * 86_400_000L }
        }
        if (review) {
            if (mark.correct) { sp.reviewsDone++; if (sp.reviewsDone >= 10) earn(sp, "reviewer", now) }
            event("review_result", now, profile, mapOf("pack" to pack.id, "exercise" to x.id, "correct" to mark.correct, "box" to st.box))
        }
        event("exercise_result", now, profile, mapOf("pack" to pack.id, "exercise" to x.id, "lesson" to x.lesson, "subject" to pack.subject,
            "correct" to mark.correct, "points" to mark.earned, "max" to mark.max, "attempt" to st.attempts))
        pack.lessons.filter { it.id == x.lesson || x.id in it.exercises || x.id in it.selfCheck }.forEach { updateStars(sp, pack, it, now) }
        return st
    }

    /** A lesson the student already went through whose content changed with a lot update: the score stays, the screen says « mise à jour ». */
    fun lessonUpdated(profile: String, lesson: String, currentHash: String?): Boolean {
        val h = state.progress[profile]?.lessons?.get(lesson)?.hash
        return h != null && currentHash != null && h != currentHash
    }

    /** Exercises to review now (due first), at most [limit]. */
    fun dueReviews(profile: String, now: Long, limit: Int = 10): List<ExerciseState> =
        state.of(profile).exercises.values.filter { it.box > 0 && it.dueAt <= now }.sortedBy { it.dueAt }.take(limit)

    fun reviewCount(profile: String, now: Long) = state.of(profile).exercises.values.count { it.box > 0 && it.dueAt <= now }

    /**
     * Stars of a lesson: 1 when all its pages were seen, 2 when at least 60 % of its exercises (graded + self-check)
     * were right at the last attempt, 3 at 90 % with at least half of them tried.
     */
    fun stars(sp: StudentProgress, lesson: Lesson): Int {
        val st = sp.lessons[lesson.id]
        val ids = lesson.exercises + lesson.selfCheck
        val tried = ids.mapNotNull { sp.exercises[it] }
        val ratio = if (ids.isEmpty()) 0.0 else tried.count { it.lastCorrect }.toDouble() / ids.size
        var s = if (st?.completed == true) 1 else 0
        if (ratio >= 0.6) s = maxOf(s, 2)
        if (ratio >= 0.9 && tried.size * 2 >= ids.size) s = 3
        return s
    }

    private fun updateStars(sp: StudentProgress, pack: Pack, lesson: Lesson, now: Long) {
        val st = sp.lessons.getOrPut(lesson.id) { LessonState(lesson.id, pack.id, pack.subject) }
        st.stars = maxOf(st.stars, stars(sp, lesson))
        if (st.stars == 3) earn(sp, "perfect", now)
    }

    fun mockResult(profile: String, pack: Pack, mock: MockExamSpec, result: MockExamSession.Result, durationMs: Long, now: Long) {
        val sp = state.of(profile); touchDay(sp, now)
        sp.mocks += MockRecord(pack.id, mock.id, pack.subject, result.score, now, durationMs)
        if (result.score >= 10) earn(sp, "mock_10", now); if (result.score >= 15) earn(sp, "mock_15", now)
        event("mock_exam_result", now, profile, mapOf("pack" to pack.id, "mock" to mock.id, "score" to result.score, "outOf" to result.outOf, "durationMs" to durationMs))
        for (l in result.lines) exerciseResult(profile, pack, l.exercise, l.mark, now)
    }

    private fun earn(sp: StudentProgress, badge: String, now: Long) {
        if (sp.badges.add(badge)) event("badge_earned", now, sp.profile, mapOf("badge" to badge))
    }

    /** Also told of every event (the TV forwards them to the server's usage statistics, without the profile). */
    @Volatile var onEvent: ((name: String, data: Map<String, Any?>) -> Unit)? = null

    fun event(name: String, now: Long, profile: String, data: Map<String, Any?>) {
        require(name in EVENTS) { "événement inconnu $name" }
        state.events += LearnEvent(name, now, profile, data)
        while (state.events.size > MAX_EVENTS) state.events.removeAt(0)
        onEvent?.let { l -> runCatching { l(name, data) } }
    }

    /** Parent dashboard (phone): per subject time, lessons completed, success rate; mocks; reviews due; badges. */
    fun dashboard(profile: String, now: Long): Map<String, Any?> {
        val p = profile(profile)
        val sp = state.of(profile)
        val subjects = (sp.timeBySubject.keys + sp.lessons.values.map { it.subject } + sp.exercises.values.map { it.subject }).distinct()
        return linkedMapOf(
            "profile" to p?.let { linkedMapOf("id" to it.id, "name" to it.name, "avatar" to it.avatar, "level" to it.level, "exam" to it.exam, "examDate" to it.examDate,
                "daysToExam" to daysToExam(it, now)) },
            "timeMs" to sp.timeBySubject.values.sum(),
            "lessonsCompleted" to sp.lessons.values.count { it.completed },
            "lessonsSeen" to sp.lessons.values.count { it.seen },
            "stars" to sp.lessons.values.sumOf { it.stars },
            "streak" to sp.streak, "lastDay" to sp.lastDay,
            "reviewsDue" to reviewCount(profile, now),
            "badges" to sp.badges.map { linkedMapOf("id" to it, "label" to BADGES[it]) },
            "subjects" to subjects.map { s ->
                val ex = sp.exercises.values.filter { it.subject == s }
                val attempts = ex.sumOf { it.attempts }
                linkedMapOf("subject" to s, "label" to (LearnCatalog.subject(s)?.fr ?: s), "timeMs" to (sp.timeBySubject[s] ?: 0L),
                    "lessonsCompleted" to sp.lessons.values.count { it.subject == s && it.completed },
                    "exercises" to ex.size, "attempts" to attempts,
                    "successRate" to if (attempts == 0) null else Math.round(ex.sumOf { it.correct } * 100.0 / attempts).toInt())
            },
            "mocks" to sp.mocks.takeLast(10).map { linkedMapOf("pack" to it.pack, "mock" to it.mock, "subject" to it.subject, "score" to it.score, "at" to it.at) },
            "resume" to sp.resume?.let { linkedMapOf("pack" to it.pack, "lesson" to it.lesson, "page" to it.page) },
        )
    }
}

/** JSON persistence of [LearnState] (one file, written atomically; a damaged file = a fresh start, never a crash). */
object LearnStore {
    private fun valid(text: String) = runCatching { parse(text) }.isSuccess

    /** The main file, else its `.bak` (last good copy) when the main one is missing, truncated or damaged, else a fresh start. */
    fun load(f: File): LearnState = SafeFile.read(f, ::valid)?.let { runCatching { parse(it.text) }.getOrNull() } ?: LearnState()

    fun save(f: File, s: LearnState) {
        SafeFile.write(f, write(s), ::valid)
    }

    fun write(s: LearnState): String = Json.write(linkedMapOf(
        "version" to 1,
        "profiles" to s.profiles.map { linkedMapOf("id" to it.id, "name" to it.name, "avatar" to it.avatar, "level" to it.level, "exam" to it.exam, "examDate" to it.examDate, "createdAt" to it.createdAt) },
        "progress" to s.progress.values.map { sp -> linkedMapOf(
            "profile" to sp.profile, "streak" to sp.streak, "lastDay" to sp.lastDay, "reviewsDone" to sp.reviewsDone,
            "badges" to sp.badges.toList(), "time" to sp.timeBySubject,
            "resume" to sp.resume?.let { linkedMapOf("pack" to it.pack, "lesson" to it.lesson, "page" to it.page, "at" to it.at) },
            "lessons" to sp.lessons.values.map { linkedMapOf("l" to it.lesson, "p" to it.pack, "s" to it.subject, "page" to it.page, "pages" to it.pages,
                "seen" to it.seen, "done" to it.completed, "stars" to it.stars, "t" to it.timeMs, "at" to it.lastAt, "h" to it.hash) },
            "exercises" to sp.exercises.values.map { linkedMapOf("x" to it.exercise, "p" to it.pack, "s" to it.subject, "l" to it.lesson, "n" to it.attempts,
                "ok" to it.correct, "last" to it.lastCorrect, "box" to it.box, "due" to it.dueAt, "at" to it.lastAt, "best" to it.best) },
            "mocks" to sp.mocks.map { linkedMapOf("p" to it.pack, "m" to it.mock, "s" to it.subject, "score" to it.score, "at" to it.at, "d" to it.durationMs) },
        ) },
        "events" to s.events.map { linkedMapOf("name" to it.name, "at" to it.at, "profile" to it.profile, "data" to it.data) },
    ))

    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): LearnState {
        val root = Json.obj(json); val s = LearnState()
        fun Map<String, Any?>.str(k: String) = this[k] as? String
        fun Map<String, Any?>.num(k: String) = (this[k] as? Number)
        fun Map<String, Any?>.list(k: String) = (this[k] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
        for (p in root.list("profiles")) s.profiles += Profile(p.str("id") ?: continue, p.str("name") ?: "?", p.num("avatar")?.toInt() ?: 0,
            p.str("level"), p.str("exam"), p.str("examDate"), p.num("createdAt")?.toLong() ?: 0)
        for (g in root.list("progress")) {
            val sp = s.of(g.str("profile") ?: continue)
            sp.streak = g.num("streak")?.toInt() ?: 0; sp.lastDay = g.str("lastDay"); sp.reviewsDone = g.num("reviewsDone")?.toInt() ?: 0
            (g["badges"] as? List<Any?>).orEmpty().filterIsInstance<String>().forEach { sp.badges += it }
            (g["time"] as? Map<String, Any?>).orEmpty().forEach { (k, v) -> sp.timeBySubject[k] = (v as? Number)?.toLong() ?: 0 }
            (g["resume"] as? Map<String, Any?>)?.let { r -> sp.resume = Resume(r.str("pack") ?: "", r.str("lesson") ?: "", r.num("page")?.toInt() ?: 0, r.num("at")?.toLong() ?: 0) }
            for (l in g.list("lessons")) {
                val st = LessonState(l.str("l") ?: continue, l.str("p") ?: "", l.str("s") ?: "")
                st.page = l.num("page")?.toInt() ?: 0; st.pages = l.num("pages")?.toInt() ?: 0; st.seen = l["seen"] == true; st.completed = l["done"] == true
                st.stars = l.num("stars")?.toInt() ?: 0; st.timeMs = l.num("t")?.toLong() ?: 0; st.lastAt = l.num("at")?.toLong() ?: 0; st.hash = l.str("h")
                sp.lessons[st.lesson] = st
            }
            for (x in g.list("exercises")) {
                val st = ExerciseState(x.str("x") ?: continue, x.str("p") ?: "", x.str("s") ?: "", x.str("l"))
                st.attempts = x.num("n")?.toInt() ?: 0; st.correct = x.num("ok")?.toInt() ?: 0; st.lastCorrect = x["last"] == true
                st.box = x.num("box")?.toInt() ?: 0; st.dueAt = x.num("due")?.toLong() ?: 0; st.lastAt = x.num("at")?.toLong() ?: 0
                st.best = x.num("best")?.toDouble() ?: (if (st.correct > 0) 1.0 else 0.0)
                sp.exercises[st.exercise] = st
            }
            for (m in g.list("mocks")) sp.mocks += MockRecord(m.str("p") ?: "", m.str("m") ?: "", m.str("s") ?: "", m.num("score")?.toDouble() ?: 0.0,
                m.num("at")?.toLong() ?: 0, m.num("d")?.toLong() ?: 0)
        }
        for (e in root.list("events")) s.events += LearnEvent(e.str("name") ?: continue, e.num("at")?.toLong() ?: 0, e.str("profile") ?: "",
            (e["data"] as? Map<String, Any?>).orEmpty())
        return s
    }
}
