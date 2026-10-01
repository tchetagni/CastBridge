package castbridge.server.lots;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One version of one lot: the data of one feature ("learn", "quiz") for one scope (a class or level, e.g. "cm2"). */
@Entity
@Table(name = "lot")
public class Lot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String feature;
    @Column(nullable = false) private String scope;
    @Column(nullable = false) private int version;
    @Column(nullable = false) private String channel;
    @Column(nullable = false) private String title;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)") private String sha256;
    @Column(name = "min_app_version", nullable = false) private int minAppVersion;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "rollout_percent", nullable = false) private int rolloutPercent = 100;
    @Column(nullable = false) private boolean published;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    public Long getId() { return id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public int getMinAppVersion() { return minAppVersion; }
    public void setMinAppVersion(int minAppVersion) { this.minAppVersion = minAppVersion; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(int rolloutPercent) { this.rolloutPercent = rolloutPercent; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
