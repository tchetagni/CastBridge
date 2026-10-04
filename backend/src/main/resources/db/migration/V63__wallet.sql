-- Module « portefeuille » (W22) : grand livre à double entrée des jetons NDEM et MBOKO (conception W22 § 3.1, § 3.7).
-- Toutes les tables sont NOUVELLES ; aucune table existante n'est modifiée (retour arrière = module éteint ; tools/wallet/rollback-V63.sql seulement si aucune écriture réelle).
-- Numérotation : V62 est réservée à la télémétrie W21 (non fusionnée au moment de cette migration) ; V63 laisse donc un trou volontaire à V62 (voir le rapport de w22-02).
-- Les clés textuelles sont comparées EXACTEMENT (ascii_bin) : une clé d'idempotence, un identifiant de blocage ou un nonce sont sensibles à la casse (base64url, hexadécimal).

-- un compte = (titulaire, monnaie, poche) ; titulaire = identité de TV XXXX-XXXX-XXXX-XXXX ou titulaire système SYS:…
CREATE TABLE wallet_account (
    id     BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    holder VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur    VARCHAR(5)  NOT NULL,
    pocket VARCHAR(6)  NOT NULL,
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
    frozen_reason    VARCHAR(200) NULL
);
CREATE INDEX ix_wallet_identity_device ON wallet_identity (api_device_id);

-- blocage de mise : réglé ou rendu une seule fois (I-6) ; per et k sont renseignés par le service de blocage (w22-05), le grand livre seul ne connaît que le montant
CREATE TABLE wallet_escrow (
    eid         VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    holder      VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cur         VARCHAR(5)   NOT NULL,
    per         BIGINT       NULL,
    k           INT          NULL,
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
    outcome     VARCHAR(24) NULL
);

-- codes de réception de transfert (w22-05)
CREATE TABLE wallet_recv_code (
    code    VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    holder  VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    exp_at  DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL
);

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

-- valeurs de lancement (conception § 1.2, § 1.3, § 5.3), avec leurs bornes ; interrupteurs d'exploitation ACTIFS par défaut
INSERT INTO wallet_policy (name, val, min_value, max_value, updated_at, updated_by) VALUES
    ('convert.rate', 1000, 1, 1000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('convert.reverseFeeBp', 0, 0, 2000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.minPerSeat.NDEM', 1, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.maxPerSeat.NDEM', 1000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.minPerSeat.MBOKO', 1, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('stake.maxPerSeat.MBOKO', 100, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.dailyCap.NDEM', 10000, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('transfer.dailyCap.MBOKO', 100, 1, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('grant.periodDays', 30, 1, 365, CURRENT_TIMESTAMP(6), 'migration'),
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
    ('switch.vouchers', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration');
