-- Retour arrière MANUEL de V63__wallet.sql (module portefeuille, W22).
-- À N'EXÉCUTER QUE seulement si aucune écriture réelle n'existe : ce script SUPPRIME le grand livre (soldes, écritures, blocages, politique, bons).
-- Avant : sauvegarder la base (mysqldump), vérifier SELECT COUNT(*) FROM wallet_txn; = 0 (ou ne contenir que des essais), éteindre le module (CASTBRIDGE_WALLET_ENABLED=0).
-- Le plus souvent, le bon retour arrière est SEULEMENT d'éteindre le module (routes en 404) : les tables sont additives et restent en place.
-- Après ce script, la ligne de V63 est retirée de l'historique de Flyway pour que la migration puisse être rejouée plus tard.
DROP TABLE wallet_voucher;
DROP TABLE wallet_voucher_batch;
DROP TABLE wallet_policy;
DROP TABLE wallet_recv_code;
DROP TABLE wallet_result;
DROP TABLE wallet_escrow;
DROP TABLE wallet_identity;
DROP TABLE wallet_entry;
DROP TABLE wallet_txn;
DROP TABLE wallet_balance;
DROP TABLE wallet_account;
DELETE FROM flyway_schema_history WHERE version = '63';
