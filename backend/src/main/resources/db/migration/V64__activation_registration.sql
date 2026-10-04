-- Enregistrement d'une activation de PRODUCTION notifiée au serveur (W23-05) : une ligne par jeton vérifié (empreinte SHA-256, JAMAIS le jeton), avec les revendications signées
-- utiles à l'idempotence, au plafond par clé, au rattrapage du portefeuille et à la décision du propriétaire. Table NOUVELLE : aucune table existante n'est modifiée.
-- Numérotation : « plus haut existant + 1 » (V63 = suivi des activations déjà sur la branche). Retour arrière : DROP TABLE lic_registration (aucune autre table ne la référence).
CREATE TABLE lic_registration (
    fp                    CHAR(64)     NOT NULL PRIMARY KEY,
    kid                   CHAR(16)     NOT NULL,
    nonce                 VARCHAR(64)  NOT NULL,
    license_id            VARCHAR(64)  NOT NULL,
    seat_id               CHAR(16)     NOT NULL,
    device_code           VARCHAR(24)  NOT NULL,
    factors               VARCHAR(400) NOT NULL,
    k                     INT          NOT NULL,
    issued_at             DATETIME(6)  NOT NULL,
    expires_at            DATETIME(6)  NOT NULL,
    usage_from            DATETIME(6)  NULL,
    usage_to              DATETIME(6)  NULL,
    unlimited             BOOLEAN      NOT NULL,
    install_pub           VARCHAR(64)  NULL,
    install_time_unproven BOOLEAN      NOT NULL DEFAULT TRUE,
    first_server_at       DATETIME(6)  NOT NULL,
    last_server_at        DATETIME(6)  NOT NULL,
    -- premier instant où le jeton a ouvert ou rattaché une licence (REGISTERED/ATTACHED) : origine de la limite de rattrapage du portefeuille (« première notification verte ») et du plafond par clé
    registered_at         DATETIME(6)  NULL,
    via                   VARCHAR(8)   NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    reason                VARCHAR(32)  NULL,
    declared              BOOLEAN      NOT NULL DEFAULT FALSE,
    decided_by            VARCHAR(64)  NULL,
    decided_at            DATETIME(6)  NULL,
    decision_reason       VARCHAR(500) NULL,
    CONSTRAINT ck_lic_registration_status CHECK (status IN ('REGISTERED', 'ATTACHED', 'PENDING_DECISION', 'REFUSED'))
);
CREATE INDEX ix_lic_registration_device ON lic_registration (device_code);
CREATE INDEX ix_lic_registration_license ON lic_registration (license_id);
CREATE INDEX ix_lic_registration_kid ON lic_registration (kid, registered_at);
CREATE INDEX ix_lic_registration_status ON lic_registration (status);
