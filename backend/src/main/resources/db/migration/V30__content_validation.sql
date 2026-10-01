-- Content validation (docs/CONTENT-VALIDATION.md): the registry of quiz questions, lessons and exercises that testers can report
-- and reviewers validate, the tester reports, the aggregated usage numbers per item, and the history of the decisions.
-- Never holds the content itself (only an excerpt for the review queue); the lots stay signed and separate.

CREATE TABLE content_item (
    kind          VARCHAR(12)  NOT NULL,
    item_id       VARCHAR(64)  NOT NULL,
    lot           VARCHAR(80)  NOT NULL,
    class_key     VARCHAR(40)  NOT NULL DEFAULT '',
    subject       VARCHAR(80)  NOT NULL DEFAULT '',
    content_hash  VARCHAR(16)  NULL,
    difficulty    INT          NULL,
    excerpt       VARCHAR(200) NOT NULL DEFAULT '',
    state         VARCHAR(12)  NOT NULL DEFAULT 'review',
    reviewer      VARCHAR(60)  NULL,
    note          VARCHAR(500) NULL,
    decided_on    DATE         NULL,
    decided_hash  VARCHAR(16)  NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_content_item PRIMARY KEY (kind, item_id)
);
CREATE INDEX ix_content_item_lot ON content_item (lot, class_key, subject);
CREATE INDEX ix_content_item_state ON content_item (state);

-- One report per device, item, reason and content version (the apps de-duplicate too); at most 200 characters of free text
CREATE TABLE content_report (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id     VARCHAR(40)  NOT NULL,
    device_id     BIGINT       NOT NULL,
    kind          VARCHAR(12)  NOT NULL,
    item_id       VARCHAR(64)  NOT NULL,
    reason        VARCHAR(20)  NOT NULL,
    note          VARCHAR(200) NOT NULL DEFAULT '',
    content_hash  VARCHAR(16)  NULL,
    lot           VARCHAR(80)  NULL,
    lot_version   INT          NULL,
    channel       VARCHAR(8)   NOT NULL,
    reported_at   DATETIME(6)  NOT NULL,
    received_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uq_content_report_id UNIQUE (report_id),
    CONSTRAINT uq_content_report_once UNIQUE (device_id, kind, item_id, reason, content_hash),
    CONSTRAINT fk_content_report_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);
CREATE INDEX ix_content_report_item ON content_report (kind, item_id);
CREATE INDEX ix_content_report_device_time ON content_report (device_id, received_at);

-- Totals of the "content_stat" events (ids and numbers only), summed over all the consenting devices
CREATE TABLE content_stat (
    kind        VARCHAR(12) NOT NULL,
    item_id     VARCHAR(64) NOT NULL,
    shown       BIGINT      NOT NULL DEFAULT 0,
    correct     BIGINT      NOT NULL DEFAULT 0,
    sum_ms      BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_content_stat PRIMARY KEY (kind, item_id)
);

-- Append-only history of the decisions (exported in the record format of tools/content-validation)
CREATE TABLE content_decision (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind          VARCHAR(12)  NOT NULL,
    item_id       VARCHAR(64)  NOT NULL,
    state         VARCHAR(12)  NOT NULL,
    reviewer      VARCHAR(60)  NOT NULL,
    decided_on    DATE         NOT NULL,
    note          VARCHAR(500) NULL,
    content_hash  VARCHAR(16)  NULL,
    lot           VARCHAR(80)  NULL,
    created_at    DATETIME(6)  NOT NULL
);
CREATE INDEX ix_content_decision_item ON content_decision (kind, item_id, id);
