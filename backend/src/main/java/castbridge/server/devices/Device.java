package castbridge.server.devices;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One installed app (tv or phone) on one physical device. Identified by the salted hash of ANDROID_ID when the app
 * sends it (survives a reinstallation), else by the install id. Plain public fields: a data holder, no lazy relations.
 */
@Entity
@Table(name = "device")
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "public_id", nullable = false) public String publicId;
    @Column(nullable = false) public String app;
    @Column(name = "android_id_hash") public String androidIdHash;
    @Column(name = "install_id", nullable = false) public String installId;
    @Column(name = "token_hash", nullable = false) public String tokenHash;

    public String label;
    public String note;
    @Column(name = "group_name") public String groupName;
    @Column(nullable = false) public boolean blocked;
    @Column(name = "channel_override") public String channelOverride;
    @Column(name = "force_update_check", nullable = false) public boolean forceUpdateCheck;

    @Column(name = "device_name") public String deviceName;
    public String manufacturer;
    public String model;
    public String platform;
    @Column(name = "os_name") public String osName;
    @Column(name = "os_build") public String osBuild;
    public String fingerprint;
    public Integer sdk;
    public String abi;
    @Column(name = "supported_abis") public String supportedAbis;
    @Column(name = "density_dpi") public Integer densityDpi;
    @Column(name = "version_code") public Integer versionCode;
    @Column(name = "version_name") public String versionName;
    public String channel;
    public String screen;
    @Column(name = "ram_total_mb") public Integer ramTotalMb;
    @Column(name = "storage_free_mb") public Integer storageFreeMb;
    @Column(name = "storage_total_mb") public Integer storageTotalMb;
    @Column(name = "usb_present") public Boolean usbPresent;
    @Column(name = "usb_free_mb") public Integer usbFreeMb;
    @Column(name = "bt_gateway") public Boolean btGateway;
    @Column(name = "ssh_enabled") public Boolean sshEnabled;
    @Column(name = "wifi_direct") public Boolean wifiDirect;
    @Column(name = "video_count") public Integer videoCount;
    @Column(name = "last_error") public String lastError;
    @Column(name = "last_error_at") public Instant lastErrorAt;
    public String country;
    public String city;
    public String ip;
    @Column(name = "ip_seen_at") public Instant ipSeenAt;
    /** The user accepted the usage statistics (else only the essential events are kept). */
    @Column(name = "usage_consent", nullable = false) public boolean usageConsent;
    @Column(name = "consent_at") public Instant consentAt;
    @Column(name = "consent_version") public String consentVersion;
    @Column(name = "first_seen", nullable = false) public Instant firstSeen;
    @Column(name = "last_seen", nullable = false) public Instant lastSeen;

    /** Name shown in the admin: the admin's label, else the device's own name, else the model. */
    public String displayName() {
        if (label != null && !label.isBlank()) return label;
        if (deviceName != null && !deviceName.isBlank()) return deviceName;
        String m = ((manufacturer == null ? "" : manufacturer + " ") + (model == null ? "" : model)).trim();
        return m.isEmpty() ? "Appareil " + publicId.substring(0, 8) : m;
    }

    public boolean online(Instant now, int onlineMinutes) {
        return lastSeen != null && lastSeen.isAfter(now.minusSeconds(onlineMinutes * 60L));
    }
}
