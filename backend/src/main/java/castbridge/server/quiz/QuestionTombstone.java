package castbridge.server.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A question devices must drop on their next sync (deleted, or no longer "reviewed"). */
@Entity
@Table(name = "question_tombstone")
public class QuestionTombstone {
    @Id private String uuid;
    @Column(name = "deleted_at", nullable = false) private Instant deletedAt;

    protected QuestionTombstone() {}

    public QuestionTombstone(String uuid, Instant deletedAt) {
        this.uuid = uuid;
        this.deletedAt = deletedAt;
    }

    public String getUuid() { return uuid; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
