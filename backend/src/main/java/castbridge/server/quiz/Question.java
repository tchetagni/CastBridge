package castbridge.server.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;

/** One quiz question (4 choices, one right answer). */
@Entity
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String uuid;
    @Column(nullable = false) private String lang;
    @Column(nullable = false) private String track;
    @Column(name = "school_level") private String level;
    @Column(name = "field_key") private String field;
    @Column(nullable = false) private String region;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private int difficulty;
    @Column(name = "question_text", nullable = false) private String text;
    @Column(name = "choice_1", nullable = false) private String choice1;
    @Column(name = "choice_2", nullable = false) private String choice2;
    @Column(name = "choice_3", nullable = false) private String choice3;
    @Column(name = "choice_4", nullable = false) private String choice4;
    @Column(name = "answer_index", nullable = false) private int answer;
    @Column(nullable = false) private String explanation;
    @Column(nullable = false) private String source;
    @Column(name = "review_status", nullable = false) private String reviewStatus;
    @Column(name = "dedup_key", nullable = false) private String dedupKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "row_version", nullable = false) private int version;

    public Long getId() { return id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getChoices() { return List.of(choice1, choice2, choice3, choice4); }
    public void setChoices(List<String> c) {
        choice1 = c.get(0);
        choice2 = c.get(1);
        choice3 = c.get(2);
        choice4 = c.get(3);
    }
    public int getAnswer() { return answer; }
    public void setAnswer(int answer) { this.answer = answer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public int getVersion() { return version; }
    public boolean isReviewed() { return "reviewed".equals(reviewStatus); }
}
