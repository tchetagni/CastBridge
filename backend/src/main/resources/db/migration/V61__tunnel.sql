-- Administration à distance (docs/REMOTE-TUNNEL.md) : un tunnel SSH inverse par CastBridge-TV, chacun sur SON port du serveur.
-- Rétrocompatible : uniquement de nouvelles tables. Aucune clé privée, aucun jeton d'activation : clés PUBLIQUES SSH et codes d'appareil seulement.

-- une ligne par CastBridge-TV enrôlée ; le port reste réservé à l'appareil même après révocation
CREATE TABLE tunnel_device (
    device_code     VARCHAR(19)  NOT NULL PRIMARY KEY,
    port            INT          NOT NULL,
    ssh_public_key  VARCHAR(120) NOT NULL,          -- « ssh-ed25519 AAAA… » normalisé (sans commentaire)
    license_id      VARCHAR(64)  NULL,              -- licence de l'activation vérifiée (identifiant de fil)
    edition         VARCHAR(12)  NULL,              -- trial | production
    state           VARCHAR(8)   NOT NULL,          -- ACTIVE | REVOKED
    enrolled_at     DATETIME(6)  NOT NULL,
    key_updated_at  DATETIME(6)  NOT NULL,
    revoked_at      DATETIME(6)  NULL,
    is_online       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at    DATETIME(6)  NULL,              -- dernière sonde réussie
    last_probe_at   DATETIME(6)  NULL,
    CONSTRAINT uq_tunnel_port UNIQUE (port),
    CONSTRAINT ck_tunnel_state CHECK (state IN ('ACTIVE', 'REVOKED'))
);

-- experts acceptés (liste signée HORS LIGNE par le propriétaire, vérifiée par le serveur)
CREATE TABLE tunnel_expert (
    expert_id       VARCHAR(32)  NOT NULL PRIMARY KEY,
    ssh_public_key  VARCHAR(120) NOT NULL,
    not_after       BIGINT       NOT NULL            -- ms, 0 = sans fin
);

CREATE TABLE tunnel_meta (
    k  VARCHAR(40)  NOT NULL PRIMARY KEY,
    v  VARCHAR(255) NOT NULL
);

-- journal : enrôlements, rotations, révocations, listes d'experts (jamais de clé ni de jeton)
CREATE TABLE tunnel_audit (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    at           DATETIME(6)  NOT NULL,
    event        VARCHAR(24)  NOT NULL,
    device_code  VARCHAR(19)  NULL,
    actor        VARCHAR(64)  NOT NULL,
    detail       VARCHAR(255) NOT NULL
);
