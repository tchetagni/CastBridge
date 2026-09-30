package castbridge.server.devices;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** The history tables of a device (plain data holders). */
public final class DeviceRecords {
    private DeviceRecords() {}

    @Entity(name = "DeviceInstall")
    @Table(name = "device_install")
    public static class Install {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "device_id", nullable = false) public long deviceId;
        @Column(name = "install_id", nullable = false) public String installId;
        @Column(name = "version_code") public Integer versionCode;
        @Column(name = "first_seen", nullable = false) public Instant firstSeen;
        @Column(name = "last_seen", nullable = false) public Instant lastSeen;
    }

    @Entity(name = "DeviceVersion")
    @Table(name = "device_version")
    public static class Version {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "device_id", nullable = false) public long deviceId;
        @Column(name = "version_code", nullable = false) public int versionCode;
        @Column(name = "version_name") public String versionName;
        @Column(name = "first_seen", nullable = false) public Instant firstSeen;
    }

    @Entity(name = "DeviceHeartbeat")
    @Table(name = "device_heartbeat")
    public static class Heartbeat {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "device_id", nullable = false) public long deviceId;
        @Column(name = "seen_at", nullable = false) public Instant seenAt;
        @Column(name = "version_code") public Integer versionCode;
        @Column(name = "storage_free_mb") public Integer storageFreeMb;
        @Column(name = "usb_free_mb") public Integer usbFreeMb;
        @Column(name = "video_count") public Integer videoCount;
        @Column(name = "bt_gateway") public Boolean btGateway;
        @Column(name = "ssh_enabled") public Boolean sshEnabled;
        @Column(name = "wifi_direct") public Boolean wifiDirect;
    }

    @Entity(name = "DeviceDaily")
    @Table(name = "device_daily")
    @IdClass(DailyKey.class)
    public static class Daily {
        @Id @Column(name = "device_id") public long deviceId;
        @Id @Column(name = "stat_day") public LocalDate day;
        @Column(nullable = false) public int heartbeats;
        @Column(name = "version_code") public Integer versionCode;
        @Column(name = "min_storage_free_mb") public Integer minStorageFreeMb;
        @Column(name = "max_video_count") public Integer maxVideoCount;
    }

    public static class DailyKey implements Serializable {
        public long deviceId;
        public LocalDate day;

        public DailyKey() {}

        public DailyKey(long deviceId, LocalDate day) {
            this.deviceId = deviceId;
            this.day = day;
        }

        @Override
        public boolean equals(Object o) { return o instanceof DailyKey k && k.deviceId == deviceId && Objects.equals(k.day, day); }

        @Override
        public int hashCode() { return Objects.hash(deviceId, day); }
    }

    @Entity(name = "DeviceCrash")
    @Table(name = "device_crash")
    public static class Crash {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "device_id", nullable = false) public long deviceId;
        @Column(name = "crashed_at", nullable = false) public Instant crashedAt;
        @Column(name = "version_code") public Integer versionCode;
        @Column(nullable = false) public String message;
        public String detail;
    }
}
