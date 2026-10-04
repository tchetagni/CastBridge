-- Retour arrière de V65 (correctifs de l'audit Opus w23-05). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh). À faire AVANT U64.
-- Perd les réclamations de période (wallet_period_claim) : un code d'avant V65 ne reconnaîtrait plus les périodes déjà payées comme telles (risque de double versement entre deux licences
-- d'une même TV) ; lister d'abord les TV qui ont plusieurs licences : SELECT device_code, COUNT(DISTINCT license_pk) FROM lic_seat WHERE state = 'ACTIVE' GROUP BY device_code HAVING COUNT(DISTINCT license_pk) > 1;
-- Ensuite : DELETE FROM flyway_schema_history WHERE version = '65';
DROP TABLE IF EXISTS wallet_period_claim;
DROP TABLE IF EXISTS lic_key_gate;
ALTER TABLE lic_registration DROP COLUMN signer_creates;
ALTER TABLE lic_registration DROP COLUMN ik_signed;
