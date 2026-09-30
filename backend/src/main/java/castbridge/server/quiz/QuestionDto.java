package castbridge.server.quiz;

import castbridge.server.CastbridgeApplication;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * A question in the exchange format of docs/QUIZ.md (format version 2, read by the TV's QuizBank.parse): {@code id}
 * and {@code uuid} carry the same stable id; {@code status} = "approved" and {@code review} = false mean "published"
 * for the TV, {@code reviewStatus} is the server-side workflow (draft | reviewed | rejected); {@code version} is the
 * row version (optimistic locking on PUT).
 *
 * <p>On input, {@code uuid} or {@code id} is accepted, and the status comes from {@code reviewStatus}, else
 * {@code status} ("approved" → reviewed), else {@code review} (false → reviewed, true → draft).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionDto(
        String id,
        String uuid,
        String lang,
        String track,
        String level,
        String field,
        String region,
        String category,
        Integer difficulty,
        String question,
        List<String> choices,
        Integer answer,
        String explanation,
        String source,
        String reviewStatus,
        String status,
        Boolean review,
        OffsetDateTime updatedAt,
        Integer version) {

    public static QuestionDto of(Question q) {
        return new QuestionDto(q.getUuid(), q.getUuid(), q.getLang(), q.getTrack(), q.getLevel(), q.getField(), q.getRegion(),
                q.getCategory(), q.getDifficulty(), q.getText(), q.getChoices(), q.getAnswer(), q.getExplanation(), q.getSource(),
                q.getReviewStatus(), q.isReviewed() ? "approved" : q.getReviewStatus(), !q.isReviewed(),
                q.getUpdatedAt() == null ? null : q.getUpdatedAt().atZone(CastbridgeApplication.ZONE).toOffsetDateTime(),
                q.getVersion());
    }

    /** Same question with its choices in another order (draws), the answer index following its choice. */
    public QuestionDto withChoices(List<String> newChoices, int newAnswer) {
        return new QuestionDto(id, uuid, lang, track, level, field, region, category, difficulty, question, newChoices, newAnswer,
                explanation, source, reviewStatus, status, review, updatedAt, version);
    }

    /** The id given on input: uuid first, then id. */
    public String inputId() {
        return uuid != null && !uuid.isBlank() ? uuid.trim() : id != null && !id.isBlank() ? id.trim() : null;
    }

    /** The review status given on input (see class comment); {@code fallback} if none. */
    public String inputStatus(String fallback) {
        if (reviewStatus != null && !reviewStatus.isBlank()) return reviewStatus.trim().toLowerCase();
        if (status != null && !status.isBlank()) {
            String s = status.trim().toLowerCase();
            return s.equals("approved") ? "reviewed" : s;
        }
        if (review != null) return review ? "draft" : "reviewed";
        return fallback;
    }
}
