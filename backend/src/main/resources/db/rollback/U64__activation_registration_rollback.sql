-- Retour arrière de V64 (enregistrement des activations de production notifiées, W23-05). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh),
-- et APRÈS U65 (V65 ajoute une colonne à cette table). Aucune autre table ne référence lic_registration.
-- ATTENTION : revenir au code 1.2.0 ou 1.2.1 garde les licences « report: » déjà créées et supprime toute limite de rattrapage : l'ancien code verserait les tranches RETENUES sans limite.
-- Avant de revenir au code, lister : SELECT license_id, created_by, state FROM lic_license WHERE created_by LIKE 'report:%';
-- Ensuite : DELETE FROM flyway_schema_history WHERE version = '64';
DROP TABLE IF EXISTS lic_registration;
