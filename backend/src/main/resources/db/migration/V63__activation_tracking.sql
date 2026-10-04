-- Module « suivi des activations » (W23) : inventaire des codes d'activation d'essai et de production, des TV qui les portent, historique immuable chaîné,
-- journal d'audit des lectures, alertes douces. Toutes les tables sont NOUVELLES : aucune table existante n'est modifiée (le module des licences est lu, jamais écrit).
-- Numérotation : production à V61, V62 = grand livre W22 (déjà sur integration/agents) ; V63 est pris par ce module (« plus haut + 1 » à la fusion).
-- Jamais un jeton cbx1, une clé compacte, un défi complet ni un jeton d'appareil : empreinte SHA-256 + étiquette de 8 hex seulement. La TV est désignée par tv_ref (HMAC) dans
-- l'historique ; act_tv.device_code est la seule correspondance lisible et peut être effacée sans casser la chaîne.

-- outils d'émission connus (une ligne par clé qui a remonté un journal) ; last_entry_* = dernière entrée connue de la chaîne du journal de cet outil
CREATE TABLE act_tool (
    kid             VARCHAR(64)  NOT NULL PRIMARY KEY,
    tool            VARCHAR(8)   NOT NULL,
    label           VARCHAR(64)  NULL,
    scopes          VARCHAR(200) NULL,
    last_entry_n    BIGINT       NOT NULL DEFAULT 0,
    last_entry_hash CHAR(64)     NULL,
    last_batch_seq  BIGINT       NOT NULL DEFAULT 0,
    last_upload_at  DATETIME(6)  NULL,
    chain_ok        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_act_tool_type CHECK (tool IN ('DESK', 'PHONE', 'SERVER', 'AGENT', 'UNKNOWN'))
);

-- lots de journal signés, gardés tels que reçus (aucun secret : le journal ne porte que des empreintes) ; QUARANTINE = refusé, gardé pour preuve
CREATE TABLE act_journal_batch (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kid         VARCHAR(64)  NOT NULL,
    seq         BIGINT       NOT NULL,
    sha256      CHAR(64)     NOT NULL,
    text        MEDIUMTEXT   NOT NULL,
    from_n      BIGINT       NULL,
    to_n        BIGINT       NULL,
    received_at DATETIME(6)  NOT NULL,
    via         VARCHAR(8)   NOT NULL,
    accepted    INT          NOT NULL DEFAULT 0,
    duplicate   INT          NOT NULL DEFAULT 0,
    rejected    INT          NOT NULL DEFAULT 0,
    status      VARCHAR(12)  NOT NULL,
    reason      VARCHAR(24)  NULL,
    text_erased BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_act_batch_sha UNIQUE (sha256),
    CONSTRAINT ck_act_batch_status CHECK (status IN ('OK', 'QUARANTINE')),
    CONSTRAINT ck_act_batch_via CHECK (via IN ('web', 'api', 'phone'))
);
CREATE INDEX ix_act_batch_kid ON act_journal_batch (kid, seq);

-- trous connus dans la numérotation des entrées d'un outil (entrées from_n..to_n jamais reçues)
CREATE TABLE act_journal_gap (
    kid       VARCHAR(64) NOT NULL,
    from_n    BIGINT      NOT NULL,
    to_n      BIGINT      NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    PRIMARY KEY (kid, from_n)
);

-- une ligne par activation connue (clé = empreinte du jeton, ou des 82 octets d'une clé compacte)
CREATE TABLE act_key (
    fp              CHAR(64)     NOT NULL PRIMARY KEY,
    tag             CHAR(8)      NOT NULL,
    form            VARCHAR(8)   NOT NULL,
    kid             VARCHAR(64)  NULL,
    kind            VARCHAR(12)  NOT NULL,
    subject         VARCHAR(8)   NOT NULL,
    license_id      VARCHAR(64)  NULL,
    seat_id         VARCHAR(16)  NULL,
    tv_ref          CHAR(16)     NULL,
    k               INT          NULL,
    aseq            BIGINT       NULL,
    nonce           VARCHAR(64)  NULL,
    issued_at       DATETIME(6)  NULL,
    not_before      DATETIME(6)  NULL,
    -- fin de la fenêtre d'installation (48 h après l'émission)
    expires_at      DATETIME(6)  NULL,
    -- fin du plafond d'usage ; unlimited = production sans plafond
    usage_to        DATETIME(6)  NULL,
    unlimited       BOOLEAN      NULL,
    super           BOOLEAN      NOT NULL DEFAULT FALSE,
    rights          VARCHAR(200) NULL,
    -- EMISE, ACTIVATED, EXPIRED_UNUSED, REPLACED, ENDED, REVOKED (dérivé des faits, jamais d'une action automatique)
    state           VARCHAR(16)  NOT NULL,
    state_at        DATETIME(6)  NOT NULL,
    -- drapeaux séparés par des virgules, bornés par des virgules (« ,a,b, ») : declared_journal, declared_registry, server_issued, seen_on_tv, delivered_bt, undeclared, clone, out_of_window
    flags           VARCHAR(160) NOT NULL DEFAULT '',
    first_seen_tv_at DATETIME(6) NULL,
    last_seen_tv_at  DATETIME(6) NULL,
    last_seen_via    VARCHAR(12) NULL,
    installed_at     DATETIME(6) NULL,
    delivered_bt_at  DATETIME(6) NULL,
    revoked_at       DATETIME(6) NULL
);
CREATE INDEX ix_act_key_state ON act_key (state, issued_at);
-- la réconciliation nocturne ne cherche que ce que le temps a fait changer (audit M2) : fenêtres closes, plafonds d'usage échus
CREATE INDEX ix_act_key_state_exp ON act_key (state, expires_at);
CREATE INDEX ix_act_key_state_usage ON act_key (state, usage_to);
CREATE INDEX ix_act_key_issued ON act_key (issued_at, fp);
CREATE INDEX ix_act_key_seen ON act_key (last_seen_tv_at, fp);
CREATE INDEX ix_act_key_tv ON act_key (tv_ref);
CREATE INDEX ix_act_key_kid ON act_key (kid, aseq);
CREATE INDEX ix_act_key_license ON act_key (license_id);
CREATE INDEX ix_act_key_tag ON act_key (tag);
CREATE INDEX ix_act_key_nonce ON act_key (kid, nonce);

-- une ligne par TV (projection : état courant). device_code est la seule correspondance lisible tv_ref <-> code, effaçable (droit à l'effacement)
CREATE TABLE act_tv (
    tv_ref          CHAR(16)    NOT NULL PRIMARY KEY,
    device_code     VARCHAR(24) NULL,
    current_fp      CHAR(64)    NULL,
    edition         VARCHAR(10) NULL,
    usage_to        DATETIME(6) NULL,
    open_all_until  DATETIME(6) NULL,
    unlock_until    DATETIME(6) NULL,
    trial_resets    INT         NOT NULL DEFAULT 0,
    first_seen_at   DATETIME(6) NULL,
    last_report_at  DATETIME(6) NULL,
    last_report_via VARCHAR(12) NULL,
    last_bt_at      DATETIME(6) NULL,
    api_devices     INT         NOT NULL DEFAULT 0,
    android_ids     INT         NOT NULL DEFAULT 0,
    app_code        INT         NULL,
    app_name        VARCHAR(64) NULL,
    -- OK, GAP, NEVER
    reco            VARCHAR(8)  NOT NULL DEFAULT 'NEVER',
    alerts_open     INT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_act_tv_code UNIQUE (device_code)
);
CREATE INDEX ix_act_tv_report ON act_tv (last_report_at, tv_ref);
CREATE INDEX ix_act_tv_edition ON act_tv (edition, last_report_at);
CREATE INDEX ix_act_tv_app ON act_tv (app_code);

-- installations de l'API (device.id) vues derrière un code d'appareil
CREATE TABLE act_tv_device (
    tv_ref    CHAR(16)    NOT NULL,
    device_id BIGINT      NOT NULL,
    first_at  DATETIME(6) NOT NULL,
    last_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (tv_ref, device_id)
);

-- clé d'installation de la TV (Ed25519, publique seulement), liée au code au premier contact qui la prouve (audit H1) ; un rapport sans jeton vérifié n'est cru que signé par cette clé
CREATE TABLE act_tv_key (
    tv_ref    CHAR(16)    NOT NULL PRIMARY KEY,
    pub_key   VARCHAR(64) NOT NULL,
    device_id BIGINT      NOT NULL,
    bound_at  DATETIME(6) NOT NULL
);

-- commandes du propriétaire vues dans un journal (declared) ou rapportées par une TV (reported), rapprochées par le défi tronqué (8 hex)
CREATE TABLE act_command (
    tv_ref       CHAR(16)    NOT NULL,
    challenge    CHAR(8)     NOT NULL,
    power        VARCHAR(12) NOT NULL,
    at_ms        BIGINT      NULL,
    days         INT         NULL,
    declared     BOOLEAN     NOT NULL DEFAULT FALSE,
    reported     BOOLEAN     NOT NULL DEFAULT FALSE,
    reported_at  DATETIME(6) NULL,
    result       VARCHAR(40) NULL,
    PRIMARY KEY (tv_ref, challenge)
);

-- émissions du registre importé (jeton absent du registre : on garde « clé + nonce » pour reconnaître plus tard l'activation vue sur une TV)
CREATE TABLE act_reg_issue (
    kid        VARCHAR(64) NOT NULL,
    nonce      VARCHAR(64) NOT NULL,
    license_id VARCHAR(64) NULL,
    seat_id    VARCHAR(16) NULL,
    kind       VARCHAR(12) NULL,
    tv_ref     CHAR(16)    NULL,
    issued_at  DATETIME(6) NOT NULL,
    not_after  DATETIME(6) NULL,
    PRIMARY KEY (kid, nonce)
);

-- L'HISTORIQUE : immuable, chaîné (tête verrouillée à chaque ajout, HMAC si act-audit.key existe). idem_key unique : rejouer une source ne double rien.
CREATE TABLE act_event (
    id          BIGINT        NOT NULL PRIMARY KEY,
    at          DATETIME(6)   NOT NULL,
    recorded_at DATETIME(6)   NOT NULL,
    type        VARCHAR(24)   NOT NULL,
    fp          CHAR(64)      NULL,
    tv_ref      CHAR(16)      NULL,
    license_id  VARCHAR(64)   NULL,
    kid         VARCHAR(64)   NULL,
    actor_type  VARCHAR(8)    NOT NULL,
    actor       VARCHAR(64)   NULL,
    source      VARCHAR(12)   NOT NULL,
    before_json VARCHAR(1000) NULL,
    after_json  VARCHAR(1000) NULL,
    idem_key    VARCHAR(96)   NOT NULL,
    prev_hash   CHAR(64)      NOT NULL,
    hash        CHAR(64)      NOT NULL,
    CONSTRAINT uq_act_event_idem UNIQUE (idem_key),
    CONSTRAINT uq_act_event_hash UNIQUE (hash),
    CONSTRAINT ck_act_event_actor CHECK (actor_type IN ('TOOL', 'SERVER', 'TV', 'ADMIN', 'JOB')),
    CONSTRAINT ck_act_event_source CHECK (source IN ('JOURNAL', 'REGISTRY', 'ISSUANCE', 'REPORT', 'COURIER', 'CONSOLE_BT', 'ADMIN', 'RECONCILE', 'LICENSE'))
);
CREATE INDEX ix_act_event_fp ON act_event (fp, id);
CREATE INDEX ix_act_event_tv ON act_event (tv_ref, id);
CREATE INDEX ix_act_event_type ON act_event (type, at);
CREATE INDEX ix_act_event_license ON act_event (license_id, id);

-- têtes des deux chaînes : 1 = act_event, 2 = adm_read_audit (même modèle que lic_audit_head)
CREATE TABLE act_event_head (
    id        INT      NOT NULL PRIMARY KEY,
    last_id   BIGINT   NOT NULL,
    last_hash CHAR(64) NOT NULL,
    CONSTRAINT ck_act_event_head CHECK (id IN (1, 2))
);
INSERT INTO act_event_head (id, last_id, last_hash) VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
INSERT INTO act_event_head (id, last_id, last_hash) VALUES (2, 0, '0000000000000000000000000000000000000000000000000000000000000000');

-- alertes DOUCES : jamais une révocation ni une libération automatique. open_key unique tant que l'alerte n'est pas classée : une seule alerte ouverte par objet.
CREATE TABLE act_alert (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type          VARCHAR(24)  NOT NULL,
    severity      VARCHAR(8)   NOT NULL,
    fp            CHAR(64)     NULL,
    tv_ref        CHAR(16)     NULL,
    kid           VARCHAR(64)  NULL,
    license_id    VARCHAR(64)  NULL,
    detail        VARCHAR(300) NULL,
    opened_at     DATETIME(6)  NOT NULL,
    last_seen_at  DATETIME(6)  NOT NULL,
    hits          INT          NOT NULL DEFAULT 1,
    state         VARCHAR(8)   NOT NULL,
    decided_by    VARCHAR(64)  NULL,
    decided_at    DATETIME(6)  NULL,
    reason        VARCHAR(500) NULL,
    open_key      CHAR(64)     NULL,
    last_evidence VARCHAR(64)  NULL,
    CONSTRAINT uq_act_alert_open UNIQUE (open_key),
    CONSTRAINT ck_act_alert_state CHECK (state IN ('OPEN', 'ACK', 'CLOSED'))
);
CREATE INDEX ix_act_alert_state ON act_alert (state, severity, opened_at);
CREATE INDEX ix_act_alert_tv ON act_alert (tv_ref);

-- rapports bruts des TV (sans aucun jeton) : bornés à 90 jours
CREATE TABLE act_report (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id     BIGINT      NOT NULL,
    tv_ref        CHAR(16)    NOT NULL,
    received_at   DATETIME(6) NOT NULL,
    via           VARCHAR(8)  NOT NULL,
    sha           CHAR(64)    NOT NULL,
    app_code      INT         NULL,
    n_activations INT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_act_report_tv ON act_report (tv_ref, received_at);
CREATE INDEX ix_act_report_time ON act_report (received_at);

-- journal d'audit des LECTURES d'administration : une ligne par lecture, chaînée comme act_event (tête n° 2)
CREATE TABLE adm_read_audit (
    id            BIGINT       NOT NULL PRIMARY KEY,
    at            DATETIME(6)  NOT NULL,
    actor         VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    channel       VARCHAR(8)   NOT NULL,
    route         VARCHAR(80)  NOT NULL,
    params        VARCHAR(300) NULL,
    target        VARCHAR(64)  NULL,
    rows_rendered INT          NOT NULL DEFAULT 0,
    export        BOOLEAN      NOT NULL DEFAULT FALSE,
    prev_hash     CHAR(64)     NOT NULL,
    hash          CHAR(64)     NOT NULL,
    CONSTRAINT uq_adm_read_hash UNIQUE (hash)
);
CREATE INDEX ix_adm_read_actor ON adm_read_audit (actor, at);
CREATE INDEX ix_adm_read_time ON adm_read_audit (at);

-- instantanés : comptes par jour, une ligne par TV et par mois
CREATE TABLE act_daily (
    snap_day DATE        NOT NULL,
    kind     VARCHAR(12) NOT NULL,
    tool     VARCHAR(8)  NOT NULL,
    state    VARCHAR(16) NOT NULL,
    n        INT         NOT NULL,
    PRIMARY KEY (snap_day, kind, tool, state)
);
CREATE TABLE act_tv_monthly (
    snap_month     CHAR(7)     NOT NULL,
    tv_ref         CHAR(16)    NOT NULL,
    edition        VARCHAR(10) NULL,
    current_fp8    CHAR(8)     NULL,
    app_code       INT         NULL,
    last_report_at DATETIME(6) NULL,
    alerts_open    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (snap_month, tv_ref)
);

-- point de contrôle quotidien : têtes des deux chaînes + compteurs, signé (Ed25519 si act-checkpoint.key, sinon HMAC, sinon SHA-256 ; sig_kid dit lequel)
CREATE TABLE act_checkpoint (
    cp_day        DATE          NOT NULL PRIMARY KEY,
    event_last_id BIGINT        NOT NULL,
    event_head    CHAR(64)      NOT NULL,
    read_last_id  BIGINT        NOT NULL,
    read_head     CHAR(64)      NOT NULL,
    counts_json   VARCHAR(2000) NOT NULL,
    sig_kid       VARCHAR(16)   NOT NULL,
    signature     VARCHAR(100)  NOT NULL,
    created_at    DATETIME(6)   NOT NULL
);

-- archive froide : jamais automatique (ordre du propriétaire, TOTP, motif) ; removed = les lignes ont quitté la base (la chaîne repart de last_hash)
CREATE TABLE act_archive (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(32)  NOT NULL,
    from_id    BIGINT       NOT NULL,
    to_id      BIGINT       NOT NULL,
    from_at    DATETIME(6)  NOT NULL,
    to_at      DATETIME(6)  NOT NULL,
    file       VARCHAR(200) NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    row_count  BIGINT       NOT NULL,
    last_hash  CHAR(64)     NULL,
    removed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    -- signature de l'ancre (table, to_id, last_hash, sha256, row_count) par la clé des points de contrôle (audit H3) : une ancre non signée n'est pas crue
    sig_kid    VARCHAR(16)  NULL,
    signature  VARCHAR(100) NULL
);
CREATE INDEX ix_act_archive_table ON act_archive (table_name, to_id);

-- seuils et cadences du module (modifiables sans livraison, dans leurs bornes ; chaque changement est un évènement POLICY_CHANGED)
CREATE TABLE act_policy (
    name       VARCHAR(40) NOT NULL PRIMARY KEY,
    val        INT         NOT NULL,
    min_val    INT         NOT NULL,
    max_val    INT         NOT NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(64) NULL
);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('undeclared_grace_hours', 72, 1, 720);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('journal_gap_days', 7, 1, 90);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('tool_stale_days', 14, 1, 180);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('silent_production_days', 30, 1, 365);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('silent_trial_days', 14, 1, 365);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('report_next_hours', 24, 0, 168);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('ended_grace_days', 7, 0, 90);
INSERT INTO act_policy (name, val, min_val, max_val) VALUES ('license_pending_days', 7, 0, 90);

-- curseurs de lecture du module des licences (id déjà traité par table)
CREATE TABLE act_cursor (
    name VARCHAR(40) NOT NULL PRIMARY KEY,
    val  BIGINT      NOT NULL
);
