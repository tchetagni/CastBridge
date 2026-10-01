-- « Lots » (docs/LOTS.md): homogeneous, versioned bundles of Apprendre / Quiz data (one class or level of one feature),
-- published by the admin, signed in a catalog, downloaded by the phones (which then push them to the TVs).
-- A version belongs to ONE channel; a lot that is not published (or is revoked) is never served.
CREATE TABLE lot (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    feature         VARCHAR(16)  NOT NULL,
    scope           VARCHAR(32)  NOT NULL,
    version         INT          NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    min_app_version INT          NOT NULL DEFAULT 0,
    file_name       VARCHAR(255) NOT NULL,
    rollout_percent INT          NOT NULL DEFAULT 100,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_at     DATETIME(6)  NOT NULL,
    published_at    DATETIME(6)  NULL,
    revoked_at      DATETIME(6)  NULL,
    CONSTRAINT uq_lot UNIQUE (feature, scope, version),
    CONSTRAINT uq_lot_file UNIQUE (file_name)
);
CREATE INDEX ix_lot_lookup ON lot (feature, channel, published, revoked);
