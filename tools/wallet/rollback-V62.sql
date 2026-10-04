-- Retour arrière MANUEL de V62__wallet.sql (module portefeuille, W22 ; renumérotée depuis V63 avant tout déploiement).
-- À N'EXÉCUTER QUE seulement si aucune écriture réelle n'existe : ce script SUPPRIME le grand livre (soldes, écritures, blocages, politique, bons, dons de l'administration).
-- Avant : sauvegarder la base (mysqldump), vérifier SELECT COUNT(*) FROM wallet_txn; = 0 (ou ne contenir que des essais), éteindre le module (CASTBRIDGE_WALLET_ENABLED=0).
-- Le plus souvent, le bon retour arrière est SEULEMENT d'éteindre le module (routes en 404) : les tables sont additives et restent en place.
-- Les colonnes et index ajoutés par l'audit de w22-05 (super_key, trial_end_at, room, used_key, cur/paid, index de wallet_recv_code et wallet_result) vivent dans les tables supprimées ci-dessous : rien de plus à écrire ici.
-- Rejouable : chaque DROP porte IF EXISTS (le DDL MySQL n'est pas transactionnel : un échec à mi-chemin se reprend en relançant le script).
-- Après ce script, la ligne de V62 est retirée de l'historique de Flyway pour que la migration puisse être rejouée plus tard.
DROP TABLE IF EXISTS wallet_alert;
DROP TABLE IF EXISTS wallet_admin_grant;
DROP TABLE IF EXISTS wallet_license_span;
DROP TABLE IF EXISTS wallet_license_claim;
DROP TABLE IF EXISTS wallet_voucher;
DROP TABLE IF EXISTS wallet_voucher_batch;
DROP TABLE IF EXISTS wallet_policy;
DROP TABLE IF EXISTS wallet_recv_code;
DROP TABLE IF EXISTS wallet_result;
DROP TABLE IF EXISTS wallet_escrow;
DROP TABLE IF EXISTS wallet_identity;
DROP TABLE IF EXISTS wallet_entry;
DROP TABLE IF EXISTS wallet_txn;
DROP TABLE IF EXISTS wallet_balance;
DROP TABLE IF EXISTS wallet_account;
DELETE FROM flyway_schema_history WHERE version = '62';
