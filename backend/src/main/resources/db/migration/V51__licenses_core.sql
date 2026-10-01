-- Module « licences », 2/3 : licences, droits, postes, émissions, transferts, révocations.
-- Le nombre de postes est protégé deux fois : verrou de ligne sur la licence (transaction) ET contrainte d'unicité
-- (licence, numéro de poste) avec numéro de poste borné par le quota côté service : jamais plus de postes que permis.

CREATE TABLE lic_license (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_id     VARCHAR(40)  NOT NULL,
    client_id      BIGINT       NOT NULL,
    -- TRIAL (essai délivré sous contrôle du propriétaire) ou PAID
    kind           VARCHAR(8)   NOT NULL DEFAULT 'PAID',
    -- ACTIVE, SUSPENDED, REVOKED, EXPIRED
    state          VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    state_reason   VARCHAR(500) NULL,
    seats_allowed  INT          NOT NULL,
    start_at       DATETIME(6)  NOT NULL,
    -- NULL = sans fin (achat à la carte définitif)
    end_at         DATETIME(6)  NULL,
    grace_days     INT          NOT NULL DEFAULT 14,
    -- plafond de transferts par an et par licence (la TV, jamais le serveur, signe un transfert)
    transfer_cap   INT          NOT NULL DEFAULT 2,
    created_by     VARCHAR(64)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_lic_license UNIQUE (license_id),
    CONSTRAINT fk_lic_license_client FOREIGN KEY (client_id) REFERENCES lic_client (id),
    CONSTRAINT ck_lic_license_kind CHECK (kind IN ('TRIAL', 'PAID')),
    CONSTRAINT ck_lic_license_state CHECK (state IN ('ACTIVE', 'SUSPENDED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_lic_license_seats CHECK (seats_allowed BETWEEN 1 AND 1000),
    CONSTRAINT ck_lic_license_grace CHECK (grace_days BETWEEN 0 AND 365),
    CONSTRAINT ck_lic_license_cap CHECK (transfer_cap BETWEEN 0 AND 100)
);
CREATE INDEX ix_lic_license_state_end ON lic_license (state, end_at);
CREATE INDEX ix_lic_license_client ON lic_license (client_id);

-- droits d'une licence : bouquets (ends_at NULL = jusqu'à la fin de la licence ou définitif)
CREATE TABLE lic_license_product (
    license_pk BIGINT      NOT NULL,
    product_pk BIGINT      NOT NULL,
    added_at   DATETIME(6) NOT NULL,
    ends_at    DATETIME(6) NULL,
    PRIMARY KEY (license_pk, product_pk),
    CONSTRAINT fk_lic_lp_license FOREIGN KEY (license_pk) REFERENCES lic_license (id),
    CONSTRAINT fk_lic_lp_product FOREIGN KEY (product_pk) REFERENCES lic_product (id)
);

CREATE TABLE lic_seat (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk      BIGINT       NOT NULL,
    device_code     VARCHAR(24)  NOT NULL,
    -- numéro de poste 1..quota tant que le poste est ACTIVE, NULL sinon (plusieurs NULL autorisés par l'unicité)
    slot_no         INT          NULL,
    -- ACTIVE, RELEASED (libéré), TRANSFERRED (transféré vers un autre matériel)
    state           VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    -- empreintes hachées des facteurs matériels (hex, séparées par des virgules) ; jamais de valeur brute
    factors_hash    VARCHAR(400) NULL,
    anonymized      BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen      DATETIME(6)  NOT NULL,
    last_seen       DATETIME(6)  NOT NULL,
    released_at     DATETIME(6)  NULL,
    released_reason VARCHAR(500) NULL,
    CONSTRAINT uq_lic_seat_device UNIQUE (license_pk, device_code),
    CONSTRAINT uq_lic_seat_slot UNIQUE (license_pk, slot_no),
    CONSTRAINT fk_lic_seat_license FOREIGN KEY (license_pk) REFERENCES lic_license (id),
    CONSTRAINT ck_lic_seat_state CHECK (state IN ('ACTIVE', 'RELEASED', 'TRANSFERRED')),
    CONSTRAINT ck_lic_seat_slot CHECK ((state = 'ACTIVE' AND slot_no IS NOT NULL AND slot_no BETWEEN 1 AND 1000)
                                       OR (state <> 'ACTIVE' AND slot_no IS NULL))
);
CREATE INDEX ix_lic_seat_device ON lic_seat (device_code);

-- jeton émis : jamais la clé, jamais le jeton lui-même (seulement son empreinte SHA-256)
CREATE TABLE lic_issuance (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk        BIGINT      NOT NULL,
    seat_pk           BIGINT      NULL,
    device_code       VARCHAR(24) NOT NULL,
    -- TRIAL, PURCHASE, SUBSCRIPTION, REACTIVATION (serveur) ; TRANSFER, OPEN_ALL (hors ligne seulement, jamais le serveur)
    kind              VARCHAR(16) NOT NULL,
    kid               VARCHAR(16) NOT NULL,
    nonce             CHAR(32)    NOT NULL,
    issued_at         DATETIME(6) NOT NULL,
    expires_at        DATETIME(6) NULL,
    issuer            VARCHAR(64) NOT NULL,
    -- server-web, server-api, ledger-desktop, ledger-phone
    channel           VARCHAR(24) NOT NULL,
    token_fingerprint CHAR(64)    NOT NULL,
    -- SERVER (émis ici) ou IMPORT (journal hors ligne)
    source            VARCHAR(8)  NOT NULL DEFAULT 'SERVER',
    -- clé d'idempotence : SHA-256(licence, appareil, sorte, droits) ; NULL pour les lignes importées
    idem_key          CHAR(64)    NULL,
    CONSTRAINT uq_lic_issuance_nonce UNIQUE (license_pk, nonce),
    CONSTRAINT uq_lic_issuance_idem UNIQUE (idem_key),
    CONSTRAINT fk_lic_issuance_license FOREIGN KEY (license_pk) REFERENCES lic_license (id),
    CONSTRAINT fk_lic_issuance_seat FOREIGN KEY (seat_pk) REFERENCES lic_seat (id)
);
CREATE INDEX ix_lic_issuance_device ON lic_issuance (device_code);
CREATE INDEX ix_lic_issuance_time ON lic_issuance (issued_at);

-- transfert de licence vers un autre matériel : enregistré seulement quand il vient du registre importé
CREATE TABLE lic_transfer (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk       BIGINT      NOT NULL,
    from_device_code VARCHAR(24) NOT NULL,
    to_device_code   VARCHAR(24) NOT NULL,
    signed_by        VARCHAR(64) NOT NULL,
    at               DATETIME(6) NOT NULL,
    -- faux = refusé par le propriétaire après un conflit (plafond dépassé)
    accepted         BOOLEAN     NOT NULL DEFAULT TRUE,
    ledger_import_id BIGINT      NULL,
    CONSTRAINT fk_lic_transfer_license FOREIGN KEY (license_pk) REFERENCES lic_license (id)
);
CREATE INDEX ix_lic_transfer_license ON lic_transfer (license_pk, at);

-- révocation : d'une licence entière, ou d'un poste (code d'appareil), ou d'une émission (kid + nonce)
CREATE TABLE lic_revocation (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_id   VARCHAR(40)  NULL,
    device_code  VARCHAR(24)  NULL,
    kid          VARCHAR(16)  NULL,
    nonce        CHAR(32)     NULL,
    reason       VARCHAR(500) NOT NULL,
    revoked_by   VARCHAR(64)  NOT NULL,
    revoked_at   DATETIME(6)  NOT NULL,
    CONSTRAINT ck_lic_revocation_target CHECK (license_id IS NOT NULL OR device_code IS NOT NULL OR nonce IS NOT NULL)
);
CREATE INDEX ix_lic_revocation_license ON lic_revocation (license_id);

-- observations d'un code d'appareil (détection d'abus) : source = empreinte tronquée de l'IP ou du téléphone, jamais la valeur
CREATE TABLE lic_sighting (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_code VARCHAR(24) NOT NULL,
    source_ref  CHAR(16)    NOT NULL,
    channel     VARCHAR(16) NOT NULL,
    seen_at     DATETIME(6) NOT NULL
);
CREATE INDEX ix_lic_sighting_device ON lic_sighting (device_code, seen_at);
