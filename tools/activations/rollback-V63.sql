-- Retour arrière de V63__activation_tracking.sql (suivi des activations, w23-01).
-- ATTENTION : supprime l'historique des activations, qui par décision n'est jamais purgé.
-- Faire d'abord un mysqldump des tables act_* et adm_read_audit (voir docs/ACTIVATION-TRACKING.md).
-- Idempotent : peut être rejoué sans erreur. La migration ne crée aucune clé étrangère ;
-- l'ordre ci-dessous (inverse de la création) reste celui à respecter si une contrainte venait à être ajoutée.
-- Avant : arrêter le serveur ou positionner CASTBRIDGE_ACTIVATIONS_ENABLED=0.
DROP TABLE IF EXISTS act_cursor;
DROP TABLE IF EXISTS act_policy;
DROP TABLE IF EXISTS act_archive;
DROP TABLE IF EXISTS act_checkpoint;
DROP TABLE IF EXISTS act_tv_monthly;
DROP TABLE IF EXISTS act_daily;
DROP TABLE IF EXISTS adm_read_audit;
DROP TABLE IF EXISTS act_report;
DROP TABLE IF EXISTS act_alert;
DROP TABLE IF EXISTS act_event_head;
DROP TABLE IF EXISTS act_event;
DROP TABLE IF EXISTS act_reg_issue;
DROP TABLE IF EXISTS act_command;
DROP TABLE IF EXISTS act_tv_device;
DROP TABLE IF EXISTS act_tv;
DROP TABLE IF EXISTS act_key;
DROP TABLE IF EXISTS act_journal_gap;
DROP TABLE IF EXISTS act_journal_batch;
DROP TABLE IF EXISTS act_tool;
DELETE FROM flyway_schema_history WHERE version = '63' AND script = 'V63__activation_tracking.sql';
