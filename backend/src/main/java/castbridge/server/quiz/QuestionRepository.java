package castbridge.server.quiz;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByUuid(String uuid);

    List<Question> findByUuidIn(Collection<String> uuids);

    List<Question> findByDedupKeyIn(Collection<String> keys);

    Optional<Question> findByDedupKey(String key);

    /** Devices: published questions changed after {@code since} (null = all), oldest change first. */
    @Query("""
            select q from Question q
            where q.reviewStatus = 'reviewed'
              and (:track is null or q.track = :track)
              and (:level is null or q.level = :level)
              and (:field is null or q.field = :field)
              and (:lang is null or q.lang = :lang)
              and (:since is null or q.updatedAt > :since)
            order by q.updatedAt asc, q.id asc""")
    Page<Question> published(@Param("track") String track, @Param("level") String level, @Param("field") String field,
                             @Param("lang") String lang, @Param("since") Instant since, Pageable page);

    /** Draw pool. */
    @Query("""
            select q from Question q
            where q.reviewStatus = 'reviewed' and q.track = :track
              and (:level is null or q.level = :level)
              and (:field is null or q.field = :field)
              and (:lang is null or q.lang = :lang)""")
    List<Question> pool(@Param("track") String track, @Param("level") String level, @Param("field") String field,
                        @Param("lang") String lang);

    /** Admin search. */
    @Query("""
            select q from Question q
            where (:status is null or q.reviewStatus = :status)
              and (:track is null or q.track = :track)
              and (:level is null or q.level = :level)
              and (:field is null or q.field = :field)
              and (:region is null or q.region = :region)
              and (:text is null or lower(q.text) like :text or lower(q.category) like :text)
            order by q.id asc""")
    Page<Question> search(@Param("status") String status, @Param("track") String track, @Param("level") String level,
                          @Param("field") String field, @Param("region") String region, @Param("text") String text, Pageable page);

    @Query("select max(q.updatedAt) from Question q")
    Instant lastChange();

    @Query("select q.reviewStatus, q.track, q.level, q.region, count(q) from Question q group by q.reviewStatus, q.track, q.level, q.region")
    List<Object[]> stats();
}
