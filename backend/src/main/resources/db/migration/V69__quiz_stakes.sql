-- Quiz en ligne avec mise (chantier games-G5, suite de games-G2) : le Quiz devient un jeu misé connu du portefeuille, comme les échecs (identifiant de jeu « quiz »), avec sa propre politique.
-- ADDITIF et SANS CHANGEMENT DE SCHÉMA : seulement des lignes de politique `game.quiz.*` (la colonne `game` des blocages et le journal des parties misées existent depuis V68). Aucune donnée existante
-- n'est modifiée. Retour arrière : db/rollback/U69__quiz_stakes_rollback.sql (à lancer AVANT U68).
-- Compatible H2 (mode MySQL) et MySQL 8.4 : instructions standard seulement.

-- politique du Quiz (lue à chaque usage, jamais dans le code ; modifiable sans redéploiement) : MÊMES valeurs de lancement et MÊMES bornes que les échecs (V68) : interrupteur, échelle de mises (paliers :
-- 0 = palier libre), frais en points de base (0 au lancement), plafonds de parties GAGNÉES par identité et PAR JEU (0 = sans plafond ; 3 par jour, 10 par semaine, 15 par mois, à confirmer par le propriétaire).
-- Le nombre de sièges qui misent par TV (1 à 8 au Quiz, 1 aux échecs) est une constante du code, pas une ligne de politique.
INSERT INTO wallet_policy (name, val, min_value, max_value, updated_at, updated_by) VALUES
    ('game.quiz.switch', 1, 0, 1, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.1', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.2', 20, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.3', 50, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.4', 100, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.5', 200, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.NDEM.6', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.1', 1, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.2', 2, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.3', 5, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.4', 10, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.5', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.tier.MBOKO.6', 0, 0, 1000000000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.feeBp', 0, 0, 2000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.cap.win.day', 3, 0, 10000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.cap.win.week', 10, 0, 10000, CURRENT_TIMESTAMP(6), 'migration'),
    ('game.quiz.cap.win.month', 15, 0, 10000, CURRENT_TIMESTAMP(6), 'migration');
