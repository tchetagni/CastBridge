# Migrations Flyway : règle de numérotation

- **Nouvelle migration = plus haut numéro existant + 1.** Jamais de trou volontaire, jamais de numéro réutilisé, y compris un numéro vu dans une autre branche.
- **Plages réservées** : V1-V4 socle (V1-V3 déjà déployées) ; V30 validation de contenu ; V50-V59 licences ; V60 ordres différés ; V61 tunnel d'administration à distance ; **V62 = portefeuille W22 (w22-02)** ; **au-delà : libre** (prendre le suivant). **AUCUN numéro n'est réservé** : chaque vague prend « plus haut numéro existant + 1 » au moment de sa fusion (une V62 arrivée après une V63 déjà appliquée bloque le démarrage : Flyway `out-of-order=false`).
- **Avant de choisir un numéro**, vérifier toutes les branches : `git log --all --oneline -- backend/src/main/resources/db/migration` et `git ls-tree -r --name-only <branche> backend/src/main/resources/db/migration`.
- **Ne jamais éditer une migration déjà appliquée** (en production ou sur une base partagée) : en écrire une nouvelle. Flyway refuse sinon de démarrer (somme de contrôle).
- **Collision connue** : la branche `wip/external-ai-changes` porte un `V4__bt_address.sql` qui entre en collision avec `V4__lots.sql` : **ne jamais fusionner** cette branche telle quelle ni copier ce fichier.
- Les migrations restent rétrocompatibles (colonnes ajoutées nullables ou avec valeur par défaut) pour permettre le retour arrière du code.
