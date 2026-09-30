package castbridge.server.updates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One published APK of one app, for one ABI and one channel. */
@Entity
@Table(name = "app_release")
public class Release {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String app;
    @Column(nullable = false) private String abi;
    @Column(nullable = false) private String channel;
    @Column(name = "version_code", nullable = false) private int versionCode;
    @Column(name = "version_name", nullable = false) private String versionName;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "package_name") private String packageName;
    @Column(name = "min_sdk") private Integer minSdk;
    @Column(nullable = false) private boolean mandatory;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)") private String sha256;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "rollout_percent", nullable = false) private int rolloutPercent = 100;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "published_at", nullable = false) private Instant publishedAt;

    public Long getId() { return id; }
    public String getApp() { return app; }
    public void setApp(String app) { this.app = app; }
    public String getAbi() { return abi; }
    public void setAbi(String abi) { this.abi = abi; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Integer getMinSdk() { return minSdk; }
    public void setMinSdk(Integer minSdk) { this.minSdk = minSdk; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public int getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(int rolloutPercent) { this.rolloutPercent = rolloutPercent; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
