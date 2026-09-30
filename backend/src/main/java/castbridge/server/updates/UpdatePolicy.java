package castbridge.server.updates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** Oldest version still supported for one app and channel: devices below it are told the update is mandatory. */
@Entity
@Table(name = "update_policy")
@IdClass(UpdatePolicy.Key.class)
public class UpdatePolicy {
    @Id private String app;
    @Id private String channel;
    @Column(name = "min_supported_version_code", nullable = false) private int minSupportedVersionCode;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    /** Composite key (app, channel). */
    public static class Key implements Serializable {
        private String app;
        private String channel;

        public Key() {}

        public Key(String app, String channel) {
            this.app = app;
            this.channel = channel;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && java.util.Objects.equals(app, k.app) && java.util.Objects.equals(channel, k.channel);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(app, channel); }
    }

    protected UpdatePolicy() {}

    public UpdatePolicy(String app, String channel) {
        this.app = app;
        this.channel = channel;
    }

    public String getApp() { return app; }
    public String getChannel() { return channel; }
    public int getMinSupportedVersionCode() { return minSupportedVersionCode; }
    public void setMinSupportedVersionCode(int v) { this.minSupportedVersionCode = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
