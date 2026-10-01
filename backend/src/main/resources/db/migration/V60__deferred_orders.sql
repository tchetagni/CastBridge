-- Ordres différés (docs/ORDRES.md): file d'ordres signés, remis aux TV par l'intermédiaire des téléphones.
-- Rétrocompatible : uniquement de nouvelles tables. Réservé : V50–V59 pour le module des licences (claude/license-admin).
-- Aucune donnée personnelle : codes d'appareil, numéros de séquence, résultats techniques.

-- dernier numéro de séquence attribué PAR CLÉ (verrou pessimiste à l'attribution : jamais deux fois le même numéro)
CREATE TABLE order_key_seq (
    kid       CHAR(16) NOT NULL PRIMARY KEY,
    last_seq  BIGINT   NOT NULL
);

CREATE TABLE order_msg (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    state         VARCHAR(12)  NOT NULL,             -- QUEUED (retenu, non signé) | RELEASED (signé) | CANCELLED
    kid           CHAR(16)     NULL,
    seq           BIGINT       NULL,
    target_kind   VARCHAR(8)   NOT NULL,             -- any | device | license | group
    target_value  VARCHAR(64)  NULL,
    action        VARCHAR(48)  NOT NULL,
    params        TEXT         NOT NULL,             -- JSON {"nom":"valeur"}
    priority      INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(64)  NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    released_at   DATETIME(6)  NULL,
    nonce         CHAR(16)     NULL,
    token         TEXT         NULL,                 -- le jeton cbx1 signé (jamais la clé)
    CONSTRAINT uq_order_kid_seq UNIQUE (kid, seq)
);
CREATE INDEX ix_order_state ON order_msg (state, priority, id);

-- TV connues du serveur : enregistrées par les téléphones appairés (demande d'appareil de la TV : code + facteurs hachés)
CREATE TABLE order_tv (
    tv_code        VARCHAR(19)  NOT NULL PRIMARY KEY,
    k              INT          NOT NULL,
    factors        VARCHAR(600) NOT NULL,            -- lignes TYPE|empreinte séparées par LF
    registered_at  DATETIME(6)  NOT NULL
);

-- quel téléphone peut porter les ordres de quelle TV
CREATE TABLE order_pairing (
    phone_public_id  CHAR(36)    NOT NULL,
    tv_code          VARCHAR(19) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (phone_public_id, tv_code),
    CONSTRAINT fk_pair_tv FOREIGN KEY (tv_code) REFERENCES order_tv (tv_code)
);

-- appartenance à une licence ou à un groupe (renseignée par l'administration ; le module des licences la remplace par sa table de postes)
CREATE TABLE order_membership (
    kind      VARCHAR(8)  NOT NULL,                  -- license | group
    ref_id    VARCHAR(64) NOT NULL,
    tv_code   VARCHAR(19) NOT NULL,
    PRIMARY KEY (kind, ref_id, tv_code)
);

-- état de chaque ordre pour chaque TV : PENDING -> HANDED (remis à un téléphone) -> APPLIED | REFUSED (accusé de la TV) ; EXPIRED se déduit de la date
CREATE TABLE order_delivery (
    order_id        BIGINT       NOT NULL,
    tv_code         VARCHAR(19)  NOT NULL,
    state           VARCHAR(8)   NOT NULL,
    handed_at       DATETIME(6)  NULL,
    handed_to       CHAR(36)     NULL,
    acked_at        DATETIME(6)  NULL,
    reason          VARCHAR(24)  NULL,
    policy_version  BIGINT       NULL,
    PRIMARY KEY (order_id, tv_code),
    CONSTRAINT fk_delivery_order FOREIGN KEY (order_id) REFERENCES order_msg (id)
);
CREATE INDEX ix_delivery_tv ON order_delivery (tv_code, state);

-- journal d'audit chaîné par empreintes (altération détectable) : création, publication, remise, accusé
CREATE TABLE order_audit (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    at         DATETIME(6)  NOT NULL,
    event      VARCHAR(24)  NOT NULL,
    order_id   BIGINT       NULL,
    tv_code    VARCHAR(19)  NULL,
    actor      VARCHAR(64)  NOT NULL,
    detail     VARCHAR(255) NOT NULL,
    prev_hash  CHAR(64)     NOT NULL,
    hash       CHAR(64)     NOT NULL
);
