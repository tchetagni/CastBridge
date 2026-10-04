-- Retour arrière de V66 (clé d'installation attendue après une réaffectation). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh). À faire AVANT U65.
-- Rejouable : la colonne n'est retirée que si elle existe, puis la ligne Flyway est effacée (sans quoi le redémarrage de V66 échouerait « Validate failed »).
SET @cb_sql = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE wallet_identity DROP COLUMN expected_install_fp', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_identity' AND COLUMN_NAME = 'expected_install_fp');
PREPARE cb_stmt FROM @cb_sql;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
DELETE FROM flyway_schema_history WHERE version = '66';
