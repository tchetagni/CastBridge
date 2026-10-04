-- Retour arrière de V65 (correctifs de l'audit Opus w23-05). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh). À faire APRÈS U66 et AVANT U64.
-- Perd les réclamations de période (wallet_period_claim) : un code d'avant V65 ne reconnaîtrait plus les périodes déjà payées comme telles (risque de double versement entre deux licences
-- d'une même TV) ; lister d'abord les TV qui ont plusieurs licences : SELECT device_code, COUNT(DISTINCT license_pk) FROM lic_seat WHERE state = 'ACTIVE' GROUP BY device_code HAVING COUNT(DISTINCT license_pk) > 1;
-- REJOUABLE (second audit LOW-C) : chaque colonne n'est retirée que si elle existe, les tables avec IF EXISTS, et la ligne Flyway est effacée par le script lui-même.
DROP TABLE IF EXISTS wallet_period_claim;
DROP TABLE IF EXISTS lic_key_gate;
SET @cb_sql = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE lic_registration DROP COLUMN signer_creates', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lic_registration' AND COLUMN_NAME = 'signer_creates');
PREPARE cb_stmt FROM @cb_sql;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
SET @cb_sql = (SELECT IF(COUNT(*) > 0, 'ALTER TABLE lic_registration DROP COLUMN ik_signed', 'SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lic_registration' AND COLUMN_NAME = 'ik_signed');
PREPARE cb_stmt FROM @cb_sql;
EXECUTE cb_stmt;
DEALLOCATE PREPARE cb_stmt;
DELETE FROM flyway_schema_history WHERE version = '65';
