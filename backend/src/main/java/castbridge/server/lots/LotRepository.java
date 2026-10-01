package castbridge.server.lots;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, Long> {
    List<Lot> findByPublishedTrueAndRevokedFalseAndChannelIn(Collection<String> channels);

    List<Lot> findByPublishedTrueAndRevokedFalseAndFeatureAndChannelIn(String feature, Collection<String> channels);

    List<Lot> findAllByOrderByFeatureAscScopeAscVersionDesc();

    Optional<Lot> findByFeatureAndScopeAndVersion(String feature, String scope, int version);

    boolean existsByFeatureAndScopeAndVersion(String feature, String scope, int version);
}
