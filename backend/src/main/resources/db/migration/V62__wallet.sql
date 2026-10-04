-- Module « portefeuille » (W22) : grand livre à double entrée des jetons NDEM et MBOKO (conception W22 § 3.1, § 3.7).
-- Toutes les tables sont NOUVELLES ; aucune table existante n'est modifiée (retour arrière = module éteint ; tools/wallet/rollback-V62.sql seulement si aucune écriture réelle).
-- Numérotation : « plus haut existant + 1 » au moment de la fusion (jamais de numéro réservé, jamais de trou volontaire) : V62 ici, renumérotée depuis V63 avant tout déploiement (audit Opus H4).
-- Les clés textuelles sont comparées EXACTEMENT (ascii_bin) : une clé d'idempotence, un identifiant de blocage ou un nonce sont sensibles à la casse (base64url, hexadécimal).

-- un compte = (titulaire, monnaie, poche) ; titulaire = identité de TV XXXX-XXXX-XXXX-XXXX ou titulaire système SYS:…
CREATE TABLE wallet_account (
    id     BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    holder VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur    VARCHAR(5)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pocket VARCHAR(6)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uq_wallet_account UNIQUE (holder, cur, pocket),
    CONSTRAINT ck_wallet_account_cur CHECK (cur IN ('NDEM', 'MBOKO')),
    CONSTRAINT ck_wallet_account_pocket CHECK (pocket IN ('DISPO', 'BLOQUE', 'BONUS', 'SYS'))
);

-- solde = cache transactionnel de la somme des écritures du compte (I-8, vérifié par la réconciliation) ; jamais négatif pour un compte de joueur (floor_zero)
CREATE TABLE wallet_balance (
    account_id BIGINT  NOT NULL PRIMARY KEY,
    balance    BIGINT  NOT NULL DEFAULT 0,
    version    BIGINT  NOT NULL DEFAULT 0,
    floor_zero BOOLEAN NOT NULL,
    CONSTRAINT fk_wallet_balance_account FOREIGN KEY (account_id) REFERENCES wallet_account (id),
    CONSTRAINT ck_wallet_balance_floor CHECK (floor_zero = FALSE OR balance >= 0)
);

-- une transaction = un ensemble d'écritures de somme nulle par monnaie ; idem_key UNIQUE = idempotence (I-4), content_sha = empreinte comparée sur doublon
CREATE TABLE wallet_txn (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind        VARCHAR(16)  NOT NULL,
    idem_key    VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_sha CHAR(64)     NOT NULL,
    holder      VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    ref         VARCHAR(64)  NULL,
    reason      VARCHAR(200) NULL,
    created_at  DATETIME(6)  NOT NULL,
    -- tv | play | admin:<nom> | system
    actor       VARCHAR(32)  NOT NULL,
    CONSTRAINT uq_wallet_txn_idem UNIQUE (idem_key),
    CONSTRAINT ck_wallet_txn_kind CHECK (kind IN ('GRANT', 'CONVERT', 'TRANSFER', 'ESCROW_LOCK', 'SETTLE', 'ESCROW_REFUND', 'VOUCHER', 'ADJUST'))
);
CREATE INDEX ix_wallet_txn_holder ON wallet_txn (holder, id);

-- écritures immuables (jamais mises à jour ni supprimées par le code)
CREATE TABLE wallet_entry (
    id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    txn_id     BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    amount     BIGINT NOT NULL,
    CONSTRAINT fk_wallet_entry_txn FOREIGN KEY (txn_id) REFERENCES wallet_txn (id),
    CONSTRAINT fk_wallet_entry_account FOREIGN KEY (account_id) REFERENCES wallet_account (id),
    CONSTRAINT ck_wallet_entry_amount CHECK (amount <> 0)
);
CREATE INDEX ix_wallet_entry_account ON wallet_entry (account_id, id);
CREATE INDEX ix_wallet_entry_txn ON wallet_entry (txn_id);

-- identité de TV : liaison à l'appareil API (anti-vol), ancre des tranches, ouverture illimitée prise une fois pour toutes
CREATE TABLE wallet_identity (
    holder           VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    api_device_id    BIGINT       NOT NULL,
    anchor_at        DATETIME(6)  NULL,
    opened_unlimited BOOLEAN      NOT NULL DEFAULT FALSE,
    edition          VARCHAR(10)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_sync_at     DATETIME(6)  NULL,
    frozen           BOOLEAN      NOT NULL DEFAULT FALSE,
    frozen_reason    VARCHAR(200) NULL,
    -- clé publique d'installation de la TV (Ed25519, 32 octets en base64) : preuve de possession à la liaison (M5) ; NULL = liaison sans preuve (ancienne) ou réaffectée par l'administrateur
    install_pub      VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    -- seq de cbw1 = dernière écriture + snap_bump ; snap_bump croît à chaque changement d'un champ signé hors écritures (fin d'essai, gel, interrupteurs de mise) ; snap_digest = condensé des champs signés (M4)
    snap_bump        BIGINT       NOT NULL DEFAULT 0,
    snap_digest      CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NULL,
    -- droits de mise : jamais déduits de `edition` (étiquette d'affichage). super_key = clé « super » lue à la dernière synchronisation ; trial_end_at = fin du dernier intervalle d'essai lu dans cbx1 (audit w22-05, E1)
    super_key        BOOLEAN      NOT NULL DEFAULT FALSE,
    trial_end_at     DATETIME(6)  NULL
);
CREATE INDEX ix_wallet_identity_device ON wallet_identity (api_device_id);

-- UNE licence ne paie qu'UNE identité (règle du propriétaire du 2026-10-04 : les licences ne se transfèrent jamais) : la première identité qui reçoit une tranche de la licence la réclame pour toujours
CREATE TABLE wallet_license_claim (
    license_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    holder     VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claimed_at DATETIME(6) NOT NULL
);
CREATE INDEX ix_wallet_license_claim_holder ON wallet_license_claim (holder);

-- intervalles pendant lesquels la licence était ACTIVE, figés à la première observation (H1) : une suspension ou une révocation ferme l'intervalle, une reprise en ouvre un NOUVEAU (aucun rattrapage)
CREATE TABLE wallet_license_span (
    license_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    start_at   DATETIME(6) NOT NULL,
    end_at     DATETIME(6) NULL,
    PRIMARY KEY (license_id, start_at)
);

-- dons de l'administration : une ligne par demande (H2). APPLIED = posé ; PENDING = attend un second administrateur ; REJECTED. idem_key = clé du don (UNIQUE : rejeu sans doublon)
CREATE TABLE wallet_admin_grant (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    idem_key     VARCHAR(80)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_by VARCHAR(64)  NOT NULL,
    identity     VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur          VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount       BIGINT       NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    state        VARCHAR(8)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approved_by  VARCHAR(64)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    decided_at   DATETIME(6)  NULL,
    CONSTRAINT uq_wallet_admin_grant_idem UNIQUE (idem_key),
    CONSTRAINT ck_wallet_admin_grant_cur CHECK (cur IN ('NDEM', 'MBOKO')),
    CONSTRAINT ck_wallet_admin_grant_amount CHECK (amount > 0),
    CONSTRAINT ck_wallet_admin_grant_state CHECK (state IN ('PENDING', 'APPLIED', 'REJECTED'))
);
CREATE INDEX ix_wallet_admin_grant_time ON wallet_admin_grant (created_at);

-- blocage de mise : réglé ou rendu une seule fois (I-6) ; per et k sont renseignés par le service de blocage (w22-05), le grand livre seul ne connaît que le montant
CREATE TABLE wallet_escrow (
    eid         VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    holder      VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur         VARCHAR(5)   CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    per         BIGINT       NULL,
    k           INT          NULL,
    -- salle déclarée au blocage (hors format signé cbe1) : le règlement doit citer la même (audit w22-05, M3/M4)
    room        VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    amount      BIGINT       NOT NULL,
    state       VARCHAR(8)   NOT NULL DEFAULT 'OPEN',
    exp_at      DATETIME(6)  NULL,
    settled_rid VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT ck_wallet_escrow_state CHECK (state IN ('OPEN', 'SETTLED', 'REFUNDED')),
    CONSTRAINT ck_wallet_escrow_cur CHECK (cur IN ('NDEM', 'MBOKO')),
    CONSTRAINT ck_wallet_escrow_amount CHECK (amount > 0)
);
CREATE INDEX ix_wallet_escrow_holder ON wallet_escrow (holder, state);
CREATE INDEX ix_wallet_escrow_settled ON wallet_escrow (settled_rid);

-- résultats de service de jeu déjà reçus (w22-05) : un résultat ne s'applique qu'une fois
CREATE TABLE wallet_result (
    rid         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    sha         CHAR(64)    NOT NULL,
    kind        VARCHAR(8)  NOT NULL,
    received_at DATETIME(6) NOT NULL,
    outcome     VARCHAR(24) NULL,
    -- monnaie et total payé du règlement : plafond du jour et alerte de gain anormal (audit w22-05, M3)
    cur         VARCHAR(5)  NULL,
    paid        BIGINT      NULL
);
CREATE INDEX ix_wallet_result_received ON wallet_result (received_at);

-- alertes d'exploitation du portefeuille (gain anormal au règlement, etc.) : lues par l'administrateur, jamais effacées par l'API
CREATE TABLE wallet_alert (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    at         DATETIME(6)  NOT NULL,
    kind       VARCHAR(24)  NOT NULL,
    ref        VARCHAR(64)  NULL,
    detail     VARCHAR(400) NOT NULL
);
CREATE INDEX ix_wallet_alert_at ON wallet_alert (at);

-- codes de réception de transfert (w22-05)
CREATE TABLE wallet_recv_code (
    code    VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    holder  VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exp_at  DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    -- clé du transfert qui a consommé le code (lien code <-> transfert, audit) ; posée dans la transaction du transfert (audit w22-05, M2)
    used_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NULL
);
CREATE INDEX ix_wallet_recv_code_holder ON wallet_recv_code (holder, used_at, exp_at);
CREATE INDEX ix_wallet_recv_code_exp ON wallet_recv_code (exp_at);

-- politique d'exploitation : toutes les valeurs ont des bornes vérifiées à l'écriture (jamais dans le code)
CREATE TABLE wallet_policy (
    name       VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    val        BIGINT      NOT NULL,
    min_value  BIGINT      NOT NULL,
    max_value  BIGINT      NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(32) NOT NULL,
    CONSTRAINT ck_wallet_policy_bounds CHECK (min_value <= val AND val <= max_value)
);

-- bons hors ligne (w22-06) : lots importés et nonces connus
CREATE TABLE wallet_voucher_batch (
    batch_id     VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    cur          VARCHAR(5)   NOT NULL,
    cnt          INT          NOT NULL,
    issued_at    DATETIME(6)  NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    manifest_sha CHAR(64)     NOT NULL,
    imported_at  DATETIME(6)  NOT NULL,
    revoked_at   DATETIME(6)  NULL
);

CREATE TABLE wallet_voucher (
    nonce          CHAR(20)    CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    batch_id       VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur            VARCHAR(5)  NOT NULL,
    amount         BIGINT      NOT NULL,
    target         CHAR(16)    NULL,
    redeemed_by    VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    redeemed_at    DATETIME(6) NULL,
    rejected_dupes INT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_wallet_voucher_batch FOREIGN KEY (batch_id) REFERENCES wallet_voucher_batch (batch_id)
);

-- valeurs de lancement (conception § 1.2, § 1.3, § 5.3), avec leurs bornes ; la période d'attribution est FIGÉE à 30 jours (min = max : les clés de tranche en dépendent, audit M1) ; interrupteurs d'exploitation ACTIFS par défaut
INSERT INTO wallet_policy (name, val, min_value, max_value, updated_at, updated_by) VALUES
    ('convert.rate', 1000, 1, 1000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('convert.reverseFeeBp', 0, 0, 2000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.minPerSeat.NDEM', 1, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.maxPerSeat.NDEM', 1000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.minPerSeat.MBOKO', 1, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.maxPerSeat.MBOKO', 100, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.dailyCap.NDEM', 10000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.dailyCap.MBOKO', 100, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.periodDays', 30, 30, 30, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.trial.ndem', 100, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.production.ndem', 1000, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.production.mboko', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.unlimited.ndem', 1000, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.unlimited.mboko', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.open.ndem', 5000, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.open.mboko', 50, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.stakes.NDEM', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.stakes.MBOKO', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.transfer', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.convert', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.vouchers', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('switch.settle', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.maxPerSettle.NDEM', 20000, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.maxPerSettle.MBOKO', 1000, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.dailyMax.NDEM', 5000000, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.dailyMax.MBOKO', 50000, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.alert.NDEM', 200000, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('settle.alert.MBOKO', 200, 1, 1000000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.dailyCap.NDEM', 1000, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.dailyCap.MBOKO', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.minAgeHours', 72, 0, 8760, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.maxDonors', 3, 1, 1000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.pairCap.NDEM', 5000, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.trial.pairCap.MBOKO', 50, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('admin.grantMax.NDEM', 10000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('admin.grantMax.MBOKO', 100, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('admin.dailyMax.NDEM', 100000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('admin.dailyMax.MBOKO', 1000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('admin.grantsPerHour', 10, 1, 1000, CURRENT_TIMESTAMP(6), 'migration');
