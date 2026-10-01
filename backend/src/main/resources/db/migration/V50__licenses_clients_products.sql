-- Module « licences » (docs/LICENSE-ADMIN.md), 1/3 : clients et catalogue de bouquets.
-- Rétrocompatible : uniquement des tables nouvelles (préfixe lic_), rien d'existant n'est modifié.
-- Retour arrière : db/rollback/U50-U52 (hors chemin Flyway, à lancer à la main après sauvegarde).
-- Données personnelles : le strict minimum (nom, contact, notes libres). Pas de prix : champs de tarif laissés vides.

CREATE TABLE lic_client (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    contact     VARCHAR(160)  NULL,
    notes       VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    -- renseigné par le droit à l'effacement : les champs ci-dessus sont alors anonymisés, la ligne reste (décompte des postes)
    erased_at   DATETIME(6)   NULL
);
CREATE INDEX ix_lic_client_name ON lic_client (name);

CREATE TABLE lic_product (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id    VARCHAR(48)  NOT NULL,
    title         VARCHAR(160) NOT NULL,
    -- A_LA_CARTE (définitif) ou ABONNEMENT (tant que valide)
    kind          VARCHAR(16)  NOT NULL,
    duration_days INT          NULL,
    -- champs de tarif : prévus, jamais remplis par CastBridge (le prix et le prestataire de paiement sont au propriétaire)
    tariff_ref    VARCHAR(64)  NULL,
    tariff_note   VARCHAR(200) NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_lic_product UNIQUE (product_id),
    CONSTRAINT ck_lic_product_kind CHECK (kind IN ('A_LA_CARTE', 'ABONNEMENT')),
    CONSTRAINT ck_lic_product_duration CHECK (duration_days IS NULL OR duration_days BETWEEN 1 AND 3660)
);

-- lots couverts par un bouquet (identifiants de lots de docs/LOTS.md : « feature/scope »)
CREATE TABLE lic_product_lot (
    product_pk BIGINT      NOT NULL,
    lot_id     VARCHAR(64) NOT NULL,
    PRIMARY KEY (product_pk, lot_id),
    CONSTRAINT fk_lic_product_lot FOREIGN KEY (product_pk) REFERENCES lic_product (id) ON DELETE CASCADE
);
