-- Usage telemetry (only from devices whose user accepted "statistiques d'usage", except the essential events)
-- and pre-computed KPI tables (incremental at ingestion + nightly consolidation).

ALTER TABLE device ADD COLUMN usage_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE device ADD COLUMN consent_at DATETIME(6) NULL;
ALTER TABLE device ADD COLUMN consent_version VARCHAR(16) NULL;

-- Raw events: 13 months at most (then only the aggregates below remain)
CREATE TABLE telemetry_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(36)  NOT NULL,
    device_id    BIGINT       NOT NULL,
    app          VARCHAR(16)  NOT NULL,
    version_code INT          NULL,
    session_id   VARCHAR(36)  NULL,
    name         VARCHAR(32)  NOT NULL,
    dim1         VARCHAR(64)  NULL,
    dim2         VARCHAR(64)  NULL,
    num_ms       BIGINT       NULL,
    num_bytes    BIGINT       NULL,
    num_value    DOUBLE       NULL,
    ok           BOOLEAN      NULL,
    props        VARCHAR(2000) NULL,
    device_ts    DATETIME(6)  NOT NULL,
    server_ts    DATETIME(6)  NOT NULL,
    stat_day     DATE         NOT NULL,
    CONSTRAINT uq_event_id UNIQUE (event_id),
    CONSTRAINT fk_event_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_event_name_day ON telemetry_event (name, stat_day);
CREATE INDEX ix_event_device_ts ON telemetry_event (device_id, device_ts);
CREATE INDEX ix_event_day ON telemetry_event (stat_day);

-- Activity of a device on a day (DAU/WAU/MAU, retention, sessions)
CREATE TABLE kpi_device_day (
    stat_day     DATE        NOT NULL,
    device_id    BIGINT      NOT NULL,
    app          VARCHAR(16) NOT NULL,
    version_code INT         NULL,
    events       INT         NOT NULL DEFAULT 0,
    sessions     INT         NOT NULL DEFAULT 0,
    session_ms   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_kpi_device_day PRIMARY KEY (stat_day, device_id),
    CONSTRAINT fk_kpi_device_day FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_kpi_device_day_device ON kpi_device_day (device_id, stat_day);

-- Use of each feature / screen by a device on a day ("fonctionnalités les plus utilisées")
CREATE TABLE kpi_feature_day (
    stat_day     DATE        NOT NULL,
    device_id    BIGINT      NOT NULL,
    app          VARCHAR(16) NOT NULL,
    feature      VARCHAR(40) NOT NULL,
    version_code INT         NULL,
    uses         INT         NOT NULL DEFAULT 0,
    views        INT         NOT NULL DEFAULT 0,
    time_ms      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_kpi_feature_day PRIMARY KEY (stat_day, device_id, feature),
    CONSTRAINT fk_kpi_feature_day FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_kpi_feature_day_app ON kpi_feature_day (app, stat_day);

-- Anonymous daily counters per event and its two main dimensions (kept after the raw events are purged)
CREATE TABLE kpi_event_day (
    stat_day  DATE        NOT NULL,
    app       VARCHAR(16) NOT NULL,
    name      VARCHAR(32) NOT NULL,
    dim1      VARCHAR(64) NOT NULL DEFAULT '',
    dim2      VARCHAR(64) NOT NULL DEFAULT '',
    events    INT         NOT NULL DEFAULT 0,
    ok        INT         NOT NULL DEFAULT 0,
    ko        INT         NOT NULL DEFAULT 0,
    sum_ms    BIGINT      NOT NULL DEFAULT 0,
    sum_bytes BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_kpi_event_day PRIMARY KEY (stat_day, app, name, dim1, dim2)
);

-- Answers per quiz question (calibration of the difficulty), anonymous
CREATE TABLE kpi_question (
    question_uuid VARCHAR(64) NOT NULL PRIMARY KEY,
    answers       INT         NOT NULL DEFAULT 0,
    correct       INT         NOT NULL DEFAULT 0,
    sum_ms        BIGINT      NOT NULL DEFAULT 0,
    updated_at    DATETIME(6) NOT NULL
);
