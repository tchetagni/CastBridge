-- Échecs en ligne avec mise (chantier games-G2, DESIGN-JEUX-CARTES-ET-ECHECS-EN-LIGNE) : jeu déclaré au blocage, journal des parties misées (W22 § 5), politique par jeu.
-- ADDITIF : une colonne nullable et une table nouvelle, des lignes de politique ; aucune donnée existante n'est modifiée. Retour arrière : db/rollback/U68__chess_stakes_rollback.sql.
-- Compatible H2 (mode MySQL) et MySQL 8.4 : instructions standard seulement.

-- le jeu pour lequel la mise a été bloquée (NULL = Quiz et blocages d'avant) : le règlement d'un résultat d'un autre jeu est refusé, l'échelle, les plafonds et les frais du jeu s'appliquent au blocage
ALTER TABLE wallet_escrow ADD COLUMN game VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL;

-- journal des parties avec mise : une ligne par TV et par résultat réglé (écrite dans la MÊME transaction que le règlement : jamais l'un sans l'autre). Sert aux plafonds de parties GAGNÉES par
-- identité (jour, semaine, mois civil d'Africa/Douala), à la lecture des gains répétés entre deux mêmes identités (R-E9, par GET /api/v1/admin/wallet/games : pas d'alerte automatique) et à l'audit ;
-- aucun coup ici (le service garde la partie, pas l'API).
CREATE TABLE wallet_game_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rid        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    game       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    holder     VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    opponent   VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cur        VARCHAR(5)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    per        BIGINT      NOT NULL,
    used       BIGINT      NOT NULL,
    -- pay = ce que le résultat du service attribue (avant frais) ; fee = frais de plateforme prélevés sur ce titulaire (gagnant seulement, 0 au lancement)
    pay        BIGINT      NOT NULL,
    fee        BIGINT      NOT NULL DEFAULT 0,
    outcome    VARCHAR(8)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    settled_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_wallet_game_log UNIQUE (rid, holder),
    CONSTRAINT ck_wallet_game_log_outcome CHECK (outcome IN ('WIN', 'LOSS', 'DRAW', 'ABORT')),
    CONSTRAINT ck_wallet_game_log_cur CHECK (cur IN ('NDEM', 'MBOKO'))
);
CREATE INDEX ix_wallet_game_log_holder ON wallet_game_log (holder, game, settled_at);
CREATE INDEX ix_wallet_game_log_room ON wallet_game_log (room);

-- politique du jeu d'échecs (lue à chaque usage, jamais dans le code) : interrupteur, échelle de mises (paliers : 0 = palier libre), frais en points de base (0 au lancement), plafonds de parties GAGNÉES
-- par identité (0 = sans plafond ; 3 par jour, 10 par semaine, 15 par mois : ceux du Défi, règle du propriétaire du 2026-10-04, reprise telle quelle, à confirmer pour les échecs)
INSERT INTO wallet_policy (name, val, min_value, max_value, updated_at, updated_by) VALUES
    ('game.chess.switch', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.1', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.2', 20, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.3', 50, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.4', 100, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.5', 200, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.NDEM.6', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.1', 1, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.2', 2, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.3', 5, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.4', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.5', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.tier.MBOKO.6', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.feeBp', 0, 0, 2000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.cap.win.day', 3, 0, 10000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.cap.win.week', 10, 0, 10000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.chess.cap.win.month', 15, 0, 10000, CURRENT_TIMESTAMP(6), 'migration');
