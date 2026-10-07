-- Retour arrière de V69 (Quiz en ligne avec mise : lignes de politique `game.quiz.*`). Hors chemin Flyway : à lancer à la main, serveur arrêté, APRÈS une sauvegarde (backend/backup.sh). À faire AVANT U68
-- (sans quoi la ligne Flyway de V69 resterait au-dessus d'une V68 retirée). V69 n'a changé AUCUN schéma : seules les lignes de politique du Quiz partent ; les blocages et les lignes du journal des parties
-- misées déjà écrits pour le jeu « quiz » restent (ils ne sont retirés que par U68, qui supprime le journal et la colonne `game`).
-- Rejouable : un second passage ne supprime rien de plus ; la ligne Flyway est effacée à la fin (sans quoi le redémarrage de V69 échouerait « Validate failed »).
DELETE FROM wallet_policy WHERE name LIKE 'game.quiz.%';
DELETE FROM flyway_schema_history WHERE version = '69';
