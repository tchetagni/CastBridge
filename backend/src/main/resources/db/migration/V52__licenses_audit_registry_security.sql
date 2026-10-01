-- Module « licences », 3/3 : journal d'audit chaîné, registre importé, conflits, rôles et double authentification.

-- Journal d'audit chaîné par empreintes : hash = SHA-256(prev_hash | champs). Aucun nom, contact ni code complet dedans.
CREATE TABLE lic_audit (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    at          DATETIME(6)   NOT NULL,
    actor       VARCHAR(64)   NOT NULL,
    role        VARCHAR(16)   NOT NULL,
    channel     VARCHAR(16)   NOT NULL,
    action      VARCHAR(40)   NOT NULL,
    target_type VARCHAR(24)   NOT NULL,
    target_id   VARCHAR(64)   NOT NULL,
    reason      VARCHAR(500)  NULL,
    details     VARCHAR(1500) NULL,
    prev_hash   CHAR(64)      NOT NULL,
    hash        CHAR(64)      NOT NULL,
    CONSTRAINT uq_lic_audit_hash UNIQUE (hash)
);
CREATE INDEX ix_lic_audit_target ON lic_audit (target_type, target_id);
CREATE INDEX ix_lic_audit_actor ON lic_audit (actor, at);
CREATE INDEX ix_lic_audit_action ON lic_audit (action, at);

-- tête de la chaîne : une seule ligne, verrouillée à chaque ajout (sérialise les écritures, détecte une troncature)
CREATE TABLE lic_audit_head (
    id        INT      NOT NULL PRIMARY KEY,
    last_id   BIGINT   NOT NULL,
    last_hash CHAR(64) NOT NULL,
    CONSTRAINT ck_lic_audit_head CHECK (id = 1)
);
INSERT INTO lic_audit_head (id, last_id, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

CREATE TABLE lic_ledger_import (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sha256      CHAR(64)     NOT NULL,
    tool        VARCHAR(24)  NOT NULL,
    kid         VARCHAR(16)  NOT NULL,
    exported_at DATETIME(6)  NULL,
    imported_at DATETIME(6)  NOT NULL,
    imported_by VARCHAR(64)  NOT NULL,
    entries     INT          NOT NULL,
    applied     INT          NOT NULL,
    duplicates  INT          NOT NULL,
    conflicts   INT          NOT NULL,
    -- un même fichier ne s'importe qu'une fois
    CONSTRAINT uq_lic_ledger_sha UNIQUE (sha256)
);

CREATE TABLE lic_conflict (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    import_id   BIGINT        NOT NULL,
    -- UNKNOWN_LICENSE, OVER_QUOTA, TWO_TOOLS, TRANSFER_CAP
    type        VARCHAR(20)   NOT NULL,
    license_id  VARCHAR(40)   NULL,
    device_code VARCHAR(24)   NULL,
    detail      VARCHAR(500)  NOT NULL,
    entry_json  VARCHAR(4000) NOT NULL,
    -- OPEN, ACCEPTED, REJECTED
    status      VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    decided_by  VARCHAR(64)   NULL,
    decided_at  DATETIME(6)   NULL,
    reason      VARCHAR(500)  NULL,
    CONSTRAINT fk_lic_conflict_import FOREIGN KEY (import_id) REFERENCES lic_ledger_import (id),
    CONSTRAINT ck_lic_conflict_status CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED'))
);
CREATE INDEX ix_lic_conflict_status ON lic_conflict (status, id);

-- rôles et TOTP des comptes de l'interface web. Les comptes existants deviennent PROPRIETAIRE (comportement inchangé).
ALTER TABLE admin_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'OWNER';
ALTER TABLE admin_user ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE admin_user ADD COLUMN totp_secret_enc VARCHAR(200) NULL;
ALTER TABLE admin_user ADD COLUMN totp_last_step BIGINT NULL;
