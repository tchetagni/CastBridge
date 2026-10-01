-- Module « licences », 2/3 : licences, droits, postes, émissions, transferts, révocations.
-- Le nombre de postes est protégé deux fois : verrou de ligne sur la licence (transaction) ET contrainte d'unicité
-- (licence, numéro de poste) avec numéro de poste borné par le quota côté service : jamais plus de postes que permis.

CREATE TABLE lic_license (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- identifiant de la licence tel qu'il est écrit dans les activations : [a-z0-9][a-z0-9-]{0,63} (une licence d'ESSAI s'écrit « trial » dans l'activation)
    license_id     VARCHAR(64)  NOT NULL,
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

-- un poste = « cette licence sur ce matériel » (docs/ACTIVATION-FORMAT.md § 8) : l'identifiant de poste (8 octets, hex) vit dans l'activation, le matériel
-- peut changer (module remplacé : k parmi n ; transfert signé hors serveur) sans changer de poste.
CREATE TABLE lic_seat (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk      BIGINT       NOT NULL,
    seat_id         CHAR(16)     NOT NULL,
    -- tv ou phone : un téléphone et une TV sont deux postes distincts
    subject         VARCHAR(8)   NOT NULL DEFAULT 'tv',
    -- code d'appareil dérivé des facteurs (XXXX-XXXX-XXXX-XXXX) ; anonymisé (ANON-…) par le droit à l'effacement
    device_code     VARCHAR(24)  NOT NULL,
    -- empreintes hachées des facteurs « TYPE|32 hex » séparées par des virgules ; jamais de valeur brute ; vide après effacement
    factors         VARCHAR(400) NOT NULL,
    k               INT          NOT NULL DEFAULT 1,
    -- numéro de poste 1..quota tant que le poste est ACTIVE, NULL sinon (plusieurs NULL autorisés par l'unicité)
    slot_no         INT          NULL,
    -- ACTIVE, RELEASED (libéré)
    state           VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE',
    anonymized      BOOLEAN      NOT NULL DEFAULT FALSE,
    first_seen      DATETIME(6)  NOT NULL,
    last_seen       DATETIME(6)  NOT NULL,
    released_at     DATETIME(6)  NULL,
    released_reason VARCHAR(500) NULL,
    CONSTRAINT uq_lic_seat_id UNIQUE (license_pk, seat_id),
    CONSTRAINT uq_lic_seat_slot UNIQUE (license_pk, slot_no),
    CONSTRAINT fk_lic_seat_license FOREIGN KEY (license_pk) REFERENCES lic_license (id),
    CONSTRAINT ck_lic_seat_state CHECK (state IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_lic_seat_subject CHECK (subject IN ('tv', 'phone')),
    CONSTRAINT ck_lic_seat_slot CHECK ((state = 'ACTIVE' AND slot_no IS NOT NULL AND slot_no BETWEEN 1 AND 1000)
                                       OR (state <> 'ACTIVE' AND slot_no IS NULL))
);
CREATE INDEX ix_lic_seat_device ON lic_seat (device_code);

-- deux identifiants de poste émis pour le même matériel par deux outils : le plus récent devient l'alias du plus ancien (compté une fois)
CREATE TABLE lic_seat_alias (
    license_pk     BIGINT   NOT NULL,
    alias_seat_id  CHAR(16) NOT NULL,
    seat_pk        BIGINT   NOT NULL,
    PRIMARY KEY (license_pk, alias_seat_id),
    CONSTRAINT fk_lic_alias_seat FOREIGN KEY (seat_pk) REFERENCES lic_seat (id)
);

-- activation émise : jamais la clé, jamais le jeton lui-même (seulement son empreinte SHA-256)
CREATE TABLE lic_issuance (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk        BIGINT      NOT NULL,
    seat_pk           BIGINT      NULL,
    seat_id           CHAR(16)    NOT NULL,
    device_code       VARCHAR(24) NOT NULL,
    -- TRIAL ou PRODUCTION (docs/ACTIVATION-FORMAT.md § 3) ; le serveur n'émet jamais de transfert ni de « tout ouvert »
    kind              VARCHAR(12) NOT NULL,
    subject           VARCHAR(8)  NOT NULL DEFAULT 'tv',
    kid               VARCHAR(16) NOT NULL,
    nonce             VARCHAR(64) NOT NULL,
    issued_at         DATETIME(6) NOT NULL,
    not_before        DATETIME(6) NULL,
    -- fin de la fenêtre d'installation (notAfter), pas la fin des droits
    not_after         DATETIME(6) NULL,
    issuer            VARCHAR(64) NOT NULL,
    -- server-web, server-api, ledger-<outil>
    channel           VARCHAR(24) NOT NULL,
    token_fingerprint CHAR(64)    NOT NULL,
    -- SERVER (émis ici) ou IMPORT (journal hors ligne)
    source            VARCHAR(8)  NOT NULL DEFAULT 'SERVER',
    -- empreinte de ce qui rend deux demandes « identiques » (poste, droits, matériel, durée) : une réémission redonne la même activation
    idem_key          CHAR(64)    NULL,
    CONSTRAINT uq_lic_issuance_nonce UNIQUE (license_pk, nonce),
    CONSTRAINT fk_lic_issuance_license FOREIGN KEY (license_pk) REFERENCES lic_license (id),
    CONSTRAINT fk_lic_issuance_seat FOREIGN KEY (seat_pk) REFERENCES lic_seat (id)
);
CREATE INDEX ix_lic_issuance_device ON lic_issuance (device_code);
CREATE INDEX ix_lic_issuance_time ON lic_issuance (issued_at);
CREATE INDEX ix_lic_issuance_idem ON lic_issuance (idem_key);

-- transfert d'un poste vers un autre matériel : SIGNÉ par le bureau ou le téléphone propriétaire (portée TRANSFER), jamais par le serveur,
-- qui l'enregistre seulement quand il vient du registre importé
CREATE TABLE lic_transfer (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_pk       BIGINT      NOT NULL,
    seat_id          CHAR(16)    NOT NULL,
    from_device_code VARCHAR(24) NOT NULL,
    to_device_code   VARCHAR(24) NOT NULL,
    -- kid de la clé qui a signé
    signed_by        VARCHAR(64) NOT NULL,
    at               DATETIME(6) NOT NULL,
    -- faux = refusé par le propriétaire après un conflit (plafond dépassé)
    accepted         BOOLEAN     NOT NULL DEFAULT TRUE,
    ledger_import_id BIGINT      NULL,
    CONSTRAINT fk_lic_transfer_license FOREIGN KEY (license_pk) REFERENCES lic_license (id)
);
CREATE INDEX ix_lic_transfer_license ON lic_transfer (license_pk, at);

-- révocation (liste signée « cbr1 » servie aux appareils) : une CLÉ (kid) ou un POSTE (licence + poste, à une date : l'activation émise avant cette date est révoquée)
CREATE TABLE lic_revocation (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    license_id   VARCHAR(64)  NULL,
    seat_id      CHAR(16)     NULL,
    kid          VARCHAR(16)  NULL,
    reason       VARCHAR(500) NOT NULL,
    revoked_by   VARCHAR(64)  NOT NULL,
    revoked_at   DATETIME(6)  NOT NULL,
    CONSTRAINT ck_lic_revocation_target CHECK (kid IS NOT NULL OR (license_id IS NOT NULL AND seat_id IS NOT NULL))
);
CREATE INDEX ix_lic_revocation_seat ON lic_revocation (license_id, seat_id);
CREATE INDEX ix_lic_revocation_kid ON lic_revocation (kid);

-- observations d'un code d'appareil (détection d'abus) : source = empreinte tronquée de l'IP ou du téléphone, jamais la valeur
CREATE TABLE lic_sighting (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_code VARCHAR(24) NOT NULL,
    source_ref  CHAR(16)    NOT NULL,
    channel     VARCHAR(16) NOT NULL,
    seen_at     DATETIME(6) NOT NULL
);
CREATE INDEX ix_lic_sighting_device ON lic_sighting (device_code, seen_at);
