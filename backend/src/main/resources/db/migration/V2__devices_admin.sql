-- Device tracking (one row per installed app on one device) and the admin web accounts.
-- No personal content: technical facts only. Raw IP addresses are kept 30 days at most (purged by a scheduled job).

CREATE TABLE device (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id          VARCHAR(36)  NOT NULL,
    app                VARCHAR(16)  NOT NULL,
    -- SHA-256 of the app-salted SHA-256 of ANDROID_ID (never the raw value); NULL if the app did not send it
    android_id_hash    CHAR(64)     NULL,
    install_id         VARCHAR(36)  NOT NULL,
    token_hash         CHAR(64)     NOT NULL,
    -- admin side
    label              VARCHAR(80)  NULL,
    note               VARCHAR(1000) NULL,
    group_name         VARCHAR(80)  NULL,
    blocked            BOOLEAN      NOT NULL DEFAULT FALSE,
    channel_override   VARCHAR(16)  NULL,
    force_update_check BOOLEAN      NOT NULL DEFAULT FALSE,
    -- last report
    device_name        VARCHAR(80)  NULL,
    manufacturer       VARCHAR(64)  NULL,
    model              VARCHAR(64)  NULL,
    -- android-tv | google-tv | fire-os | android-box | phone | tablet | other (detected by the app, never assumed)
    platform           VARCHAR(24)  NULL,
    os_name            VARCHAR(64)  NULL,
    os_build           VARCHAR(160) NULL,
    fingerprint        VARCHAR(200) NULL,
    sdk                INT          NULL,
    abi                VARCHAR(16)  NULL,
    supported_abis     VARCHAR(100) NULL,
    density_dpi        INT          NULL,
    version_code       INT          NULL,
    version_name       VARCHAR(64)  NULL,
    channel            VARCHAR(16)  NULL,
    screen             VARCHAR(32)  NULL,
    ram_total_mb       INT          NULL,
    storage_free_mb    INT          NULL,
    storage_total_mb   INT          NULL,
    usb_present        BOOLEAN      NULL,
    usb_free_mb        INT          NULL,
    bt_gateway         BOOLEAN      NULL,
    ssh_enabled        BOOLEAN      NULL,
    wifi_direct        BOOLEAN      NULL,
    video_count        INT          NULL,
    last_error         VARCHAR(500) NULL,
    last_error_at      DATETIME(6)  NULL,
    country            VARCHAR(2)   NULL,
    city               VARCHAR(80)  NULL,
    ip                 VARCHAR(45)  NULL,
    ip_seen_at         DATETIME(6)  NULL,
    first_seen         DATETIME(6)  NOT NULL,
    last_seen          DATETIME(6)  NOT NULL,
    CONSTRAINT uq_device_public UNIQUE (public_id),
    CONSTRAINT uq_device_token UNIQUE (token_hash)
);
CREATE INDEX ix_device_android ON device (app, android_id_hash);
CREATE INDEX ix_device_install ON device (app, install_id);
CREATE INDEX ix_device_seen ON device (last_seen);

-- Successive installations (a new install_id on the same device = a reinstallation)
CREATE TABLE device_install (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id    BIGINT      NOT NULL,
    install_id   VARCHAR(36) NOT NULL,
    version_code INT         NULL,
    first_seen   DATETIME(6) NOT NULL,
    last_seen    DATETIME(6) NOT NULL,
    CONSTRAINT fk_install_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT uq_install UNIQUE (device_id, install_id)
);

-- Successive app versions seen on a device
CREATE TABLE device_version (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id    BIGINT      NOT NULL,
    version_code INT         NOT NULL,
    version_name VARCHAR(64) NULL,
    first_seen   DATETIME(6) NOT NULL,
    CONSTRAINT fk_version_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE,
    CONSTRAINT uq_device_version UNIQUE (device_id, version_code)
);

-- Detailed heartbeats: 30 days, then only the daily aggregate below remains
CREATE TABLE device_heartbeat (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id       BIGINT      NOT NULL,
    seen_at         DATETIME(6) NOT NULL,
    version_code    INT         NULL,
    storage_free_mb INT         NULL,
    usb_free_mb     INT         NULL,
    video_count     INT         NULL,
    bt_gateway      BOOLEAN     NULL,
    ssh_enabled     BOOLEAN     NULL,
    wifi_direct     BOOLEAN     NULL,
    CONSTRAINT fk_heartbeat_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_heartbeat_device_at ON device_heartbeat (device_id, seen_at);
CREATE INDEX ix_heartbeat_at ON device_heartbeat (seen_at);

-- One row per device and day (kept after the detailed heartbeats are purged)
CREATE TABLE device_daily (
    device_id           BIGINT  NOT NULL,
    stat_day            DATE    NOT NULL,
    heartbeats          INT     NOT NULL,
    version_code        INT     NULL,
    min_storage_free_mb INT     NULL,
    max_video_count     INT     NULL,
    CONSTRAINT pk_device_daily PRIMARY KEY (device_id, stat_day),
    CONSTRAINT fk_daily_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);

CREATE TABLE device_crash (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id    BIGINT        NOT NULL,
    crashed_at   DATETIME(6)   NOT NULL,
    version_code INT           NULL,
    message      VARCHAR(500)  NOT NULL,
    detail       VARCHAR(4000) NULL,
    CONSTRAINT fk_crash_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_crash_device_at ON device_crash (device_id, crashed_at);
CREATE INDEX ix_crash_at ON device_crash (crashed_at);

CREATE TABLE admin_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME(6)  NULL,
    last_login      DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uq_admin_user UNIQUE (username)
);
