# Protocole `play` — Quiz en ligne (ébauche)

> Ébauche créée par w20-01 ; w20-02 la complète (messages, codec, reprise). Conception : `docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md`.

## Timing

Exigence du propriétaire (2026-10-03) : « entre 2 questions laisse 1 ou 2 s de latence pour pouvoir synchroniser les parties en ligne ».

Code : `PlayTiming` (`android/core/src/main/kotlin/castbridge/core/quiz/online/PlayTiming.kt`), fonctions pures, horloge du **serveur** seulement.

| Élément | Valeur |
|---|---|
| `PlayTiming.INTER_QUESTION_GAP_MS` | 1 500 ms |
| Plage permise | 1 000 à 2 000 ms ; `PlayTiming.gap(demandé)` ramène toute valeur dans la plage |
| `PlayTiming.gapFor(scope)` | `TV_ONLY` : 0 · `LAN` : 0 (comportement actuel inchangé, prouvé par test) · `INTERNET` : délai borné |

Règles :

1. Après la **révélation** d'une question (à `revealAtServerMs`), la question suivante est **annoncée tout de suite**.
2. L'annonce porte l'instant absolu `opensAtServerMs = revealAtServerMs + gap`, sur l'horloge du serveur.
3. Les clients affichent « Question suivante dans 1,5 s » et peuvent pré-afficher la question ; **personne ne peut répondre avant `opensAtServerMs`** (le serveur refuse : `PlayTiming.gate`).
4. Le temps de réponse (donc les points) se compte **depuis `opensAtServerMs`**, jamais depuis l'arrivée d'un message (`PlayTiming.scoringElapsedMs`).
5. Annonce tardive : un client qui reçoit l'annonce après `opensAtServerMs` démarre aussitôt ; la fenêtre n'est raccourcie que du temps mesuré par le **serveur** (l'annonce porte l'heure serveur), jamais par l'horloge du client (`PlayTiming.start`).
6. Les parties `TV_ONLY` et `LAN` n'ajoutent aucun délai.
