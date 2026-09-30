package castbridge.server.quiz;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TombstoneRepository extends JpaRepository<QuestionTombstone, String> {

    List<QuestionTombstone> findByDeletedAtAfterOrderByDeletedAtAsc(Instant since);

    @Query("select max(t.deletedAt) from QuestionTombstone t")
    Instant lastDeletion();

    @Modifying
    @Query("delete from QuestionTombstone t where t.deletedAt < :before")
    int purgeBefore(@Param("before") Instant before);
}
