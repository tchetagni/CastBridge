package castbridge.core.content

import castbridge.core.learn.Exercise
import castbridge.core.learn.Lesson
import castbridge.core.learn.ReviewStatus
import castbridge.core.quiz.Question

/**
 * Release channel of a device, as set by the server per device (ServerLink.state.channel / DeviceReport.channel / UpdatePolicy):
 * "beta" testers get content that nobody validated yet, everybody else ("stable", and anything unknown) only validated content.
 */
enum class Channel(val key: String) {
    STABLE("stable"), BETA("beta");
    companion object { fun of(key: String?): Channel = if (key?.trim().equals("beta", ignoreCase = true)) BETA else STABLE }
}

/** Kind of an item that can be validated, reported and measured. */
enum class ContentKind(val key: String) {
    QUESTION("question"), LESSON("lesson"), EXERCISE("exercise");
    companion object { fun of(key: String?) = values().firstOrNull { it.key == key } }
}

/**
 * Validation state of a question, lesson or exercise (docs/CONTENT-VALIDATION.md):
 * `review` (written, nobody validated it) → `validated` | `rejected` | `needs-fix`.
 */
enum class ContentState(val key: String) {
    REVIEW("review"), VALIDATED("validated"), REJECTED("rejected"), NEEDS_FIX("needs-fix");

    /** States a reviewer (or a content change) may move this item to; staying in the same state (new note) is always allowed. */
    val next: Set<ContentState> get() = when (this) {
        REVIEW -> setOf(VALIDATED, REJECTED, NEEDS_FIX)
        VALIDATED -> setOf(REVIEW, NEEDS_FIX, REJECTED)   // REVIEW: the content changed since it was validated
        NEEDS_FIX -> setOf(REVIEW, REJECTED)              // REVIEW: the author fixed it, a reviewer must look again
        REJECTED -> setOf(REVIEW, NEEDS_FIX)              // reopened / rewritten
    }

    fun canMoveTo(to: ContentState) = to == this || to in next

    companion object {
        /** Spellings found in the sources and servers; null for an unknown word. */
        fun of(raw: String?): ContentState? = when (raw?.trim()?.lowercase()) {
            "review", "draft", "beta", "reviewed" -> REVIEW
            "validated", "approved" -> VALIDATED
            "rejected" -> REJECTED
            "needs-fix", "needs_fix", "needsfix" -> NEEDS_FIX
            else -> null
        }
    }
}

/**
 * The single place that decides what is played / shown on a channel.
 *
 * | state \ channel | stable              | beta                                  |
 * |-----------------|---------------------|---------------------------------------|
 * | validated       | playable            | playable                              |
 * | review          | not playable        | playable, marked « bêta : non validé » |
 * | needs-fix       | not playable        | not playable (a reviewer found a defect) |
 * | rejected        | never               | never                                 |
 *
 * Content whose answer was computed and tested by the content pipeline (`verif = computed`) arrives without the review flag
 * (QuizBank.parse computedPlayable), i.e. as VALIDATED: that is today's behaviour and it is kept.
 */
object PlayPolicy {
    const val BETA_MARK = "bêta : non validé"

    fun isPlayable(state: ContentState, channel: Channel): Boolean = when (state) {
        ContentState.VALIDATED -> true
        ContentState.REVIEW -> channel == Channel.BETA
        ContentState.NEEDS_FIX, ContentState.REJECTED -> false
    }

    /** The visible mark of an item on [channel]: [BETA_MARK] when it is played without being validated, otherwise null. */
    fun mark(state: ContentState, channel: Channel): String? =
        if (channel == Channel.BETA && state == ContentState.REVIEW) BETA_MARK else null

    // ---- quiz
    fun stateOf(q: Question): ContentState {
        val declared = ContentState.of(q.status)
        return when {
            declared == ContentState.REJECTED || declared == ContentState.NEEDS_FIX -> declared
            q.review -> ContentState.REVIEW
            else -> ContentState.VALIDATED
        }
    }

    fun isPlayable(q: Question, channel: Channel): Boolean = isPlayable(stateOf(q), channel)
    /** Beta mark of a question; a question whose answer was computed and tested but never read by a human is marked too. */
    fun mark(q: Question, channel: Channel): String? =
        mark(stateOf(q), channel) ?: if (channel == Channel.BETA && stateOf(q) == ContentState.VALIDATED && q.verif == "computed" && q.status != "approved") BETA_MARK else null

    // ---- Apprendre
    /** A lesson is validated when its source says so (`state`, else `status: validated`); `draft` / `reviewed` (read by the author) stay under review. */
    fun stateOf(l: Lesson): ContentState = l.state ?: if (l.status == ReviewStatus.VALIDATED) ContentState.VALIDATED else ContentState.REVIEW

    /** An exercise is under review when it carries `review: true` (or an explicit `state`). */
    fun stateOf(x: Exercise): ContentState = x.state ?: if (x.review) ContentState.REVIEW else ContentState.VALIDATED

    /**
     * Lessons and exercises. [legacyStable]: until the stable channel is really opened (docs/CONTENT-VALIDATION.md § 6) the app
     * keeps showing content under review on the stable channel, with its existing « à vérifier » mark — today's behaviour —
     * but rejected and needs-fix content is already hidden everywhere.
     */
    fun isVisible(state: ContentState, channel: Channel, legacyStable: Boolean = true): Boolean =
        isPlayable(state, channel) || (legacyStable && state == ContentState.REVIEW)

    fun isVisible(l: Lesson, channel: Channel, legacyStable: Boolean = true) = isVisible(stateOf(l), channel, legacyStable)
    fun isVisible(x: Exercise, channel: Channel, legacyStable: Boolean = true) = isVisible(stateOf(x), channel, legacyStable)
    fun mark(l: Lesson, channel: Channel): String? = mark(stateOf(l), channel)
    fun mark(x: Exercise, channel: Channel): String? = mark(stateOf(x), channel)
}
