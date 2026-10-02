# sonnet-w5-03-fix : correctifs de l'audit Opus sur les boosts du Quiz

Branche `claude/sonnet-w5-03-fix` (depuis integration/agents), un commit, non poussé.

| # | Constat | Correctif |
|---|---------|-----------|
| 1 | Deux 50:50 sur une question | `useFifty` refuse si `removed` n'est pas vide ; « Joker en plus » ne rend le 50:50 que s'il a été dépensé sur une question précédente (sinon l'avis du public, sinon non proposé). Toujours au moins 2 choix. |
| 2 | Seconde chance gratuite après mauvaise réponse | Après `End.WRONG`, une AUTRE question de même difficulté est tirée via `swapProvider` ; aucune disponible : refus sans débit ; sans fournisseur : offre non proposée. Après `TIMEOUT` : même question. |
| 3 | `inGame` modifié avant le débit | `swapQuestion` ne réserve plus ; `hostBoost` ajoute à `inGame` seulement après `applyBoost` réussi. |
| 4 | `balance` diffusé aux téléphones | Retiré de l'état ; `available`/`costs` du fournisseur mis en cache 1 s par jeu (pas d'appel par vue). |
| 5 | LOST_OFFER sans solde | Offre seulement si `balance() >= cost(SECOND_CHANCE)`. |
| 6 | Idempotence de `charge` | Documentée en KDoc (gameId, boost, numéro d'achat). |
| 7 | `error` « réservé à la TV » | Seulement pour `FORBIDDEN`. |

Tests ajoutés : 50:50 jamais < 2 choix (20 graines, joker acheté et rejoué sur la même question), seconde chance après erreur sans remplaçant, après timeout (même question), solde insuffisant sans offre, pas de `balance` dans l'état, swap non payé ne réserve pas, erreur HTTP seulement en 409 (QuizHttpTest). Tests existants adaptés (seconde chance = autre question ; joker en plus sur la question suivante).

Risque : le cache de 1 s peut afficher un prix/disponibilité périmé d'au plus 1 s (le débit reste vérifié en direct).
