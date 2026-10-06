-- Retour arrière de V67 (plafond de transferts à 0). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh). À faire AVANT U66.
-- Restaure les plafonds d'avant (table bak_transfer_cap_v67), remet le défaut de la colonne à 2, puis efface la ligne Flyway (sans quoi le redémarrage de V67 échouerait « Validate failed »).
-- Rejouable : sans la table de sauvegarde, seules les dernières instructions agissent. Le code de la version 1.2.1 refuse de toute façon un transfert vers un autre matériel.
SET @cb_sql = (SELECT IF(COUNT(*) > 0, 'UPDATE lic_license l JOIN bak_transfer_cap_v67 b ON b.license_pk = l.id SET l.transfer_cap = b.transfer_cap', 'SELECT 1') FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bak_transfer_cap_v67');
PREPARE cb_stmt FROM @cb_sql;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
ALTER TABLE lic_license ALTER COLUMN transfer_cap SET DEFAULT 2;
DROP TABLE IF EXISTS bak_transfer_cap_v67;
DELETE FROM flyway_schema_history WHERE version = '67';
