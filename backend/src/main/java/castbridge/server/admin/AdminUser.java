package castbridge.server.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An account of the admin web interface (BCrypt password, locked for a while after repeated failures). */
@Entity
@Table(name = "admin_user")
public class AdminUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false) public String username;
    @Column(name = "password_hash", nullable = false) public String passwordHash;
    @Column(name = "failed_attempts", nullable = false) public int failedAttempts;
    @Column(name = "locked_until") public Instant lockedUntil;
    @Column(name = "last_login") public Instant lastLogin;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    /** OWNER, SUPPORT or READONLY (licence module); existing accounts are OWNER. */
    @Column(nullable = false) public String role = "OWNER";
    @Column(name = "totp_enabled", nullable = false) public boolean totpEnabled;
}
