package castbridge.server.updates;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRepository extends JpaRepository<Release, Long> {

    List<Release> findByAppAndRevokedFalseAndChannelInAndAbiInOrderByVersionCodeDesc(
            String app, Collection<String> channels, Collection<String> abis);

    List<Release> findAllByOrderByAppAscVersionCodeDescIdDesc();

    List<Release> findByAppOrderByVersionCodeDescIdDesc(String app);

    Optional<Release> findByAppAndFileName(String app, String fileName);

    boolean existsByAppAndAbiAndChannelAndVersionCode(String app, String abi, String channel, int versionCode);

    long countByFileName(String fileName);
}
