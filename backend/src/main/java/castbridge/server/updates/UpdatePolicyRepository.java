package castbridge.server.updates;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpdatePolicyRepository extends JpaRepository<UpdatePolicy, UpdatePolicy.Key> {
    List<UpdatePolicy> findAllByOrderByAppAscChannelAsc();
}
