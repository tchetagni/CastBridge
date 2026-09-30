-- CastBridge server schema v1. Dates are stored in UTC (DATETIME(6)); shown in Africa/Douala by the API.
-- Portable between MySQL 8.4 (production) and H2 in MySQL mode (tests): no engine/charset clauses
-- (MySQL 8.4 defaults: InnoDB, utf8mb4), indexes created separately.

CREATE TABLE app_release (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    app             VARCHAR(16)  NOT NULL,
    abi             VARCHAR(16)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    version_code    INT          NOT NULL,
    version_name    VARCHAR(64)  NOT NULL,
    notes           TEXT         NULL,
    package_name    VARCHAR(255) NULL,
    min_sdk         INT          NULL,
    mandatory       BOOLEAN      NOT NULL DEFAULT FALSE,
    file_name       VARCHAR(255) NOT NULL,
    sha256          CHAR(64)     NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    rollout_percent INT          NOT NULL DEFAULT 100,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at      DATETIME(6)  NULL,
    published_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_release UNIQUE (app, abi, channel, version_code),
    CONSTRAINT uq_release_file UNIQUE (file_name)
);
CREATE INDEX ix_release_lookup ON app_release (app, channel, revoked, version_code);

-- Per app and channel: devices below min_supported_version_code must update (forced update).
CREATE TABLE update_policy (
    app                        VARCHAR(16) NOT NULL,
    channel                    VARCHAR(16) NOT NULL,
    min_supported_version_code INT         NOT NULL DEFAULT 0,
    updated_at                 DATETIME(6) NOT NULL,
    CONSTRAINT pk_update_policy PRIMARY KEY (app, channel)
);

CREATE TABLE question (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid          VARCHAR(64)   NOT NULL,
    lang          VARCHAR(8)    NOT NULL,
    track         VARCHAR(16)   NOT NULL,
    school_level  VARCHAR(32)   NULL,
    field_key     VARCHAR(32)   NULL,
    region        VARCHAR(8)    NOT NULL,
    category      VARCHAR(64)   NOT NULL,
    difficulty    INT           NOT NULL,
    question_text VARCHAR(1000) NOT NULL,
    choice_1      VARCHAR(500)  NOT NULL,
    choice_2      VARCHAR(500)  NOT NULL,
    choice_3      VARCHAR(500)  NOT NULL,
    choice_4      VARCHAR(500)  NOT NULL,
    answer_index  INT           NOT NULL,
    explanation   VARCHAR(2000) NOT NULL,
    source        VARCHAR(500)  NOT NULL,
    review_status VARCHAR(16)   NOT NULL,
    dedup_key     CHAR(64)      NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    row_version   INT           NOT NULL DEFAULT 1,
    CONSTRAINT uq_question_uuid UNIQUE (uuid),
    CONSTRAINT uq_question_dedup UNIQUE (dedup_key)
);
CREATE INDEX ix_question_filter ON question (review_status, track, school_level, field_key);
CREATE INDEX ix_question_updated ON question (updated_at, id);

-- A question that left the published set (deleted, or no longer "reviewed"): devices remove it on their next sync.
CREATE TABLE question_tombstone (
    uuid       VARCHAR(64) NOT NULL PRIMARY KEY,
    deleted_at DATETIME(6) NOT NULL
);
CREATE INDEX ix_tombstone_deleted ON question_tombstone (deleted_at);
