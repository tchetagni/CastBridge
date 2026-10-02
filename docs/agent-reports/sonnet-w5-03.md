STATUT: TERMINÉ
CAHIER: sonnet-w5-03 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w5-03 · COMMIT: voir git log
JETONS: inconnu
PORTE: gradle-lock.sh gradle --offline :core:test --tests '*Quiz*' → VERT (1 min 10)
SUITE COMPLÈTE: :core:test → 1989 tests, 1 échec, 1 ignoré ; écart : TrialRoutesTest.everyServedRouteIsClassified (routes /api/library/organize et /api/library/organize/apply non classées : hors zone, étranger à ce cahier). Tests ajoutés : 13 (QuizBoostsTest), tests Quiz existants inchangés et verts.
FICHIERS: android/core/src/main/kotlin/castbridge/core/quiz/{QuizBoosts(neuf),QuizGame,QuizRoom,Wallet,QuizHttp,HighScores}.kt ; android/core/src/test/kotlin/castbridge/core/QuizBoostsTest.kt (tests Quiz à plat dans castbridge.core, pas de paquet quiz) ; docs/agent-reports/sonnet-w5-03.md
CHOIX:
- Interface finale : enum Boost(label, maxPerGame 1/2/1) ; QuizBoosts{available, cost, balance, charge(b, gameId)} ; NoBoosts. QuizGame(boosts=NoBoosts, gameId="", swapProvider=null) ; canBoost, applyBoost(b, now), declineBoost, lastBoostRefusal ; QuizRoom(boosts=NoBoosts), hostBoost(b): String? (null = ok, sinon message FR), hostDeclineBoost().
- Phase ajoutée LOST_OFFER (10 s, QuizGame.LOST_OFFER_MS) : déclenchée seulement si SECOND_CHANCE disponible ; l'échéance réutilise `deadline`/remainingMs.
- Joker en plus : rend FIFTY en priorité, sinon AUDIENCE ; jamais PHONE.
- Changer de question : le fournisseur est appelé avant le débit (pas de débit sans remplaçant) ; fournisseur de la salle = bank.draw hors asked et hors questions de la partie.
- `boosted` = vrai dès qu'UNE commodité est achetée (plus strict que « seconde chance seule »). HighScores.Entry gagne `boosted` (défaut faux, JSON additif) : une entrée avec jetons se classe toujours après toutes les entrées sans jetons.
- act=boost/declineBoost depuis un téléphone : Act.FORBIDDEN (409) + "error":"réservé à la TV" ; VirtualWallet conservé comme fonction dépréciée ; TOKENS_LABEL = « Points de défi — sans valeur » (constante gardée pour R/**).
NON FAIT / À VALIDER SUR MATÉRIEL: texte et boutons TV (w5-17) ; QuizActivity/QuizHub (hors zone) ne connaissent pas la phase LOST_OFFER ni la commande hostBoost : à brancher ; QuizRoom.noteShown enregistre l'ancienne question (posée) mais pas la remplaçante dans l'historique.
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite (1 échec étranger) [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
