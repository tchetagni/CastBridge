-- Retour arrière du module « licences » (V50..V52). Hors chemin Flyway : à lancer à la main, APRÈS une sauvegarde
-- (backend/backup.sh), serveur arrêté. Perd toutes les données des licences ; n'affecte aucune table d'avant V50
-- sauf les 4 colonnes ajoutées à admin_user. Ensuite : DELETE FROM flyway_schema_history WHERE version IN ('50','51','52');
ALTER TABLE admin_user DROP COLUMN totp_last_step;
ALTER TABLE admin_user DROP COLUMN totp_secret_enc;
ALTER TABLE admin_user DROP COLUMN totp_enabled;
ALTER TABLE admin_user DROP COLUMN role;
DROP TABLE lic_conflict;
DROP TABLE lic_ledger_import;
DROP TABLE lic_audit_head;
DROP TABLE lic_audit;
DROP TABLE lic_sighting;
DROP TABLE lic_revocation;
DROP TABLE lic_transfer;
DROP TABLE lic_issuance;
DROP TABLE lic_seat;
DROP TABLE lic_license_product;
DROP TABLE lic_license;
DROP TABLE lic_product_lot;
DROP TABLE lic_product;
DROP TABLE lic_client;
