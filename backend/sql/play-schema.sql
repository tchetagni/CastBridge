-- castbridge_play : base et utilisateur DÉDIÉS du service de jeu en ligne (DESIGN-W20 § 2.9, O-5).
--
-- À NE PAS APPLIQUER AVANT w20-09 : castbridge-play (w20-03) n'utilise aucune base aujourd'hui. Ce script ne crée donc QUE la base vide et
-- l'utilisateur à droits limités ; les TABLES (salons, classements, rétention) seront créées par les migrations Flyway de w20-09, pas ici.
-- Aucun mot de passe dans le dépôt : le marqueur __MOT_DE_PASSE_PLAY__ est remplacé À LA VOLÉE par la commande du runbook (docs/PLAY-OPS.md § SQL),
-- depuis une variable de l'interpréteur, sans écrire le mot de passe dans un fichier ni dans l'historique.
--
-- Pour appliquer (une seule fois, par le propriétaire, sur le serveur) : voir docs/PLAY-OPS.md, section « Base de données (facultatif, après w20-09) ».

CREATE DATABASE IF NOT EXISTS castbridge_play
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- Hôte '%' : MySQL n'est joignable que depuis le réseau Docker interne (castbridge-internal), il n'a aucun port publié.
-- Le service castbridge-play devra être rattaché à ce réseau par w20-09 (son compose actuel ne l'est pas, volontairement).
CREATE USER IF NOT EXISTS 'castbridge_play'@'%' IDENTIFIED BY '__MOT_DE_PASSE_PLAY__';

-- Droits limités à CE schéma : jamais *.*, jamais la base « castbridge » de l'API, pas de GRANT OPTION, pas de FILE, pas de SUPER.
-- Flyway (w20-09) a besoin de créer et modifier des tables dans ce schéma seulement.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON castbridge_play.* TO 'castbridge_play'@'%';
FLUSH PRIVILEGES;

-- Vérifications (lecture seule) :
--   SHOW GRANTS FOR 'castbridge_play'@'%';
--   SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE 'castbridge%';
--
-- Retour arrière (supprime TOUTES les données du service de jeu : sauvegarder d'abord avec backend/backup.sh) :
--   DROP USER 'castbridge_play'@'%';
--   DROP DATABASE castbridge_play;
