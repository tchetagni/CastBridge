package castbridge.core.learn

/**
 * Meeting of two copies of the student data (the phone's and the TV's, each stored locally and kept through lot updates).
 * The merge loses nothing and can be repeated or done in either order with the same result (docs/LEARN.md § Fusion):
 *
 * - students: the same student = same first name (case apart; names are unique on a device); a different student whose id collides
 *   gets a free id on the receiving side (up to [LearnProgress.MAX_PROFILES]; beyond that it is left out, reported in [Report.skippedProfiles]);
 * - per exercise: **last write wins** for the state of the spaced review (last result, Leitner box, due date = those of the most
 *   recent attempt), while the **best score is kept** (best, number of successes and attempts = the larger of the two: both copies
 *   usually share the same history, so adding would count it twice);
 * - per lesson: seen / completed if either; stars, time = the larger; page, hash = those of the most recent visit;
 * - mock exams: union (same exam, same date = the same one); badges: union; streak: that of the most recent day;
 *   time per subject: the larger; where to resume: the most recent.
 * Telemetry events stay local (each device uploads its own).
 */
object LearnMerge {
    class Report(val profiles: Int, val lessons: Int, val exercises: Int, val skippedProfiles: List<String>)

    /** Merges [other] into [into] (in place). */
    fun merge(into: LearnState, other: LearnState): Report {
        var lessons = 0; var exercises = 0; val skipped = ArrayList<String>(); var profiles = 0
        val idMap = HashMap<String, String>()
        for (p in other.profiles) {
            val same = into.profiles.firstOrNull { it.name.equals(p.name, ignoreCase = true) }
            if (same != null) { idMap[p.id] = same.id; profiles++; continue }
            val id = if (into.profiles.none { it.id == p.id }) p.id else (1..99).map { "p$it" }.firstOrNull { c -> into.profiles.none { it.id == c } }
            if (into.profiles.size >= LearnProgress.MAX_PROFILES || id == null) { skipped += p.name; continue }
            into.profiles += p.copy(id = id); idMap[p.id] = id; profiles++
        }
        for (sp in other.progress.values) {
            val id = idMap[sp.profile] ?: continue
            val mine = into.of(id)
            for (l in sp.lessons.values) { merge(mine, l); lessons++ }
            for (x in sp.exercises.values) { merge(mine, x); exercises++ }
            for (m in sp.mocks) if (mine.mocks.none { it.pack == m.pack && it.mock == m.mock && it.at == m.at }) mine.mocks += m
            mine.mocks.sortBy { it.at }
            mine.badges += sp.badges
            if ((sp.lastDay ?: "") > (mine.lastDay ?: "")) { mine.lastDay = sp.lastDay; mine.streak = sp.streak }
            else if (sp.lastDay == mine.lastDay) mine.streak = maxOf(mine.streak, sp.streak)
            mine.reviewsDone = maxOf(mine.reviewsDone, sp.reviewsDone)
            for ((s, t) in sp.timeBySubject) mine.timeBySubject[s] = maxOf(mine.timeBySubject[s] ?: 0, t)
            val r = sp.resume
            if (r != null && r.at > (mine.resume?.at ?: -1)) mine.resume = r
        }
        return Report(profiles, lessons, exercises, skipped)
    }

    private fun merge(into: StudentProgress, l: LessonState) {
        val m = into.lessons[l.lesson]
        if (m == null) { into.lessons[l.lesson] = copy(l); return }
        val newer = l.lastAt > m.lastAt
        m.seen = m.seen || l.seen; m.completed = m.completed || l.completed
        m.stars = maxOf(m.stars, l.stars); m.timeMs = maxOf(m.timeMs, l.timeMs)
        if (newer) { m.page = l.page; m.pages = l.pages; m.hash = l.hash ?: m.hash } else if (m.hash == null) m.hash = l.hash
        m.lastAt = maxOf(m.lastAt, l.lastAt)
    }

    private fun merge(into: StudentProgress, x: ExerciseState) {
        val m = into.exercises[x.exercise]
        if (m == null) { into.exercises[x.exercise] = copy(x); return }
        if (x.lastAt > m.lastAt) { m.lastCorrect = x.lastCorrect; m.box = x.box; m.dueAt = x.dueAt; m.lastAt = x.lastAt }
        m.best = maxOf(m.best, x.best); m.correct = maxOf(m.correct, x.correct); m.attempts = maxOf(m.attempts, x.attempts)
    }

    private fun copy(l: LessonState) = LessonState(l.lesson, l.pack, l.subject).also {
        it.page = l.page; it.pages = l.pages; it.seen = l.seen; it.completed = l.completed; it.stars = l.stars; it.timeMs = l.timeMs; it.lastAt = l.lastAt; it.hash = l.hash
    }

    private fun copy(x: ExerciseState) = ExerciseState(x.exercise, x.pack, x.subject, x.lesson).also {
        it.attempts = x.attempts; it.correct = x.correct; it.lastCorrect = x.lastCorrect; it.box = x.box; it.dueAt = x.dueAt; it.lastAt = x.lastAt; it.best = x.best
    }
}
