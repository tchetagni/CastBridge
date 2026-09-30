package castbridge.server.devices;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByPublicId(String publicId);

    Optional<Device> findByTokenHash(String tokenHash);

    Optional<Device> findFirstByAppAndAndroidIdHashOrderByLastSeenDesc(String app, String androidIdHash);

    Optional<Device> findFirstByAppAndInstallIdOrderByLastSeenDesc(String app, String installId);

    @Query("""
            select d from Device d
            where (:app is null or d.app = :app)
              and (:version is null or d.versionCode = :version)
              and (:country is null or d.country = :country)
              and (:group is null or d.groupName = :group)
              and (:onlineSince is null or d.lastSeen >= :onlineSince)
              and (:offlineBefore is null or d.lastSeen < :offlineBefore)
              and (:blocked is null or d.blocked = :blocked)
              and (:platform is null or d.platform = :platform)
              and (:manufacturer is null or d.manufacturer = :manufacturer)
              and (:abi is null or d.abi = :abi)
              and (:text is null or lower(d.label) like :text or lower(d.deviceName) like :text or lower(d.model) like :text
                   or lower(d.manufacturer) like :text or lower(d.groupName) like :text or lower(d.publicId) like :text)""")
    Page<Device> search(@Param("app") String app, @Param("version") Integer version, @Param("country") String country,
                        @Param("group") String group, @Param("onlineSince") Instant onlineSince,
                        @Param("offlineBefore") Instant offlineBefore, @Param("blocked") Boolean blocked,
                        @Param("platform") String platform, @Param("manufacturer") String manufacturer, @Param("abi") String abi,
                        @Param("text") String text, Pageable page);

    long countByLastSeenAfter(Instant since);

    long countByBlockedTrue();

    @Query("select d.app, d.versionCode, d.versionName, count(d) from Device d where d.lastSeen >= :since group by d.app, d.versionCode, d.versionName order by d.app, d.versionCode desc")
    List<Object[]> versions(@Param("since") Instant since);

    @Query("select d.country, count(d) from Device d where d.lastSeen >= :since group by d.country order by count(d) desc")
    List<Object[]> countries(@Param("since") Instant since);

    @Query("select d.app, count(d) from Device d group by d.app")
    List<Object[]> perApp();

    @Query("select d.platform, count(d) from Device d where d.lastSeen >= :since group by d.platform order by count(d) desc")
    List<Object[]> platforms(@Param("since") Instant since);

    @Query("select distinct d.manufacturer from Device d where d.manufacturer is not null order by d.manufacturer")
    List<String> manufacturers();

    @Query("select distinct d.abi from Device d where d.abi is not null order by d.abi")
    List<String> abis();

    @Query("select distinct d.groupName from Device d where d.groupName is not null order by d.groupName")
    List<String> groups();

    @Modifying
    @Query("update Device d set d.ip = null, d.ipSeenAt = null where d.ipSeenAt < :before")
    int forgetIps(@Param("before") Instant before);

    @Modifying
    @Query("delete from Device d where d.lastSeen < :before")
    int forgetDevices(@Param("before") Instant before);
}

interface InstallRepository extends JpaRepository<DeviceRecords.Install, Long> {
    Optional<DeviceRecords.Install> findByDeviceIdAndInstallId(long deviceId, String installId);

    List<DeviceRecords.Install> findByDeviceIdOrderByFirstSeenDesc(long deviceId);
}

interface VersionRepository extends JpaRepository<DeviceRecords.Version, Long> {
    boolean existsByDeviceIdAndVersionCode(long deviceId, int versionCode);

    List<DeviceRecords.Version> findByDeviceIdOrderByFirstSeenDesc(long deviceId);
}

interface HeartbeatRepository extends JpaRepository<DeviceRecords.Heartbeat, Long> {
    List<DeviceRecords.Heartbeat> findTop100ByDeviceIdOrderBySeenAtDesc(long deviceId);

    Optional<DeviceRecords.Heartbeat> findFirstByDeviceIdOrderBySeenAtDesc(long deviceId);

    @Modifying
    @Query("delete from DeviceHeartbeat h where h.seenAt < :before")
    int purge(@Param("before") Instant before);
}

interface DailyRepository extends JpaRepository<DeviceRecords.Daily, DeviceRecords.DailyKey> {
    List<DeviceRecords.Daily> findByDeviceIdAndDayGreaterThanEqualOrderByDayDesc(long deviceId, LocalDate from);
}

interface CrashRepository extends JpaRepository<DeviceRecords.Crash, Long> {
    List<DeviceRecords.Crash> findTop50ByDeviceIdOrderByCrashedAtDesc(long deviceId);

    List<DeviceRecords.Crash> findTop20ByOrderByCrashedAtDesc();

    @Modifying
    @Query("delete from DeviceCrash c where c.crashedAt < :before")
    int purge(@Param("before") Instant before);
}
