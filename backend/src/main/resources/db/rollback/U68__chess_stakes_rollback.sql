-- Retour arrière de V68 (échecs en ligne avec mise : colonne `game` des blocages, journal des parties misées, lignes de politique `game.*`). Hors chemin Flyway : à lancer à la main, serveur arrêté,
-- APRÈS une sauvegarde (backend/backup.sh). À faire AVANT U67. ATTENTION : le journal des parties misées est SUPPRIMÉ (il n'est pas reconstructible : les parties n'ont d'autre trace que le grand
-- livre) ; des frais de plateforme éventuellement prélevés (SYS:FEE) restent au grand livre, ce que l'ancien code lit sans erreur (la réconciliation ne les attendait qu'en NDEM : à noter).
-- Rejouable : chaque instruction est gardée ; la ligne Flyway est effacée à la fin (sans quoi le redémarrage de V68 échouerait « Validate failed »).
DROP TABLE IF EXISTS wallet_game_log;
DELETE FROM wallet_policy WHERE name LIKE 'game.%';
SET @cb_sql = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE wallet_escrow DROP COLUMN game', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_escrow' AND COLUMN_NAME = 'game');
PREPARE cb_stmt FROM @cb_sql;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
DELETE FROM flyway_schema_history WHERE version = '68';
