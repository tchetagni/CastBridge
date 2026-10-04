# w22-17 — Défi des 10 000 : écran TV (échelle, paliers, « Emporter les gains », 50:50), moteur local hors ligne, reprise après coupure, disponible et gains en attente, envoi des journaux
<!-- routage architecte 2026-10-04 (W22, niveau 2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (aucun crédit affiché comme confirmé avant le serveur, journaux jamais perdus) · statut : **ATTEND w22-07, w22-15, w22-16** ; **exception de gel** (comme D-W22-13)
> **Groupe : W22-DEFI** · porte : `:core:test` + `:receiver:assembleRelease -PrequireActivation=true`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W22-JETONS-NDEM-MBOKO-2026-10-04.md` § 13.4 (ce que voit le joueur), § 13.5 (signe), § 13.6 (écran), § 13.9 (plafonds de parties gagnées). Branche `claude/w22-17-defi-tv`. Rapport : `docs/agent-reports/sonnet-w22-17.md`.

## Fichiers possédés
- **Nouveaux** : `android/receiver/src/main/kotlin/castbridge/receiver/wallet/millions/{MillionsActivity,MillionsLadderView,MillionsStore}.kt` (`MillionsStore` : pack actif, état de partie persisté à chaque réponse, journaux non confirmés ≤ 30 j, écriture atomique dans `files/wallet/millions/`).
- **Zone additive** : `R/GamesUi.kt` ou `R/Games.kt` (tuile « Défi des 10 000 » derrière `quiz.online` ; essai : grisée « Version complète ») ; `R/wallet/WalletClient.kt` (journaux envoyés et pack reçu dans `sync`, **avant** toute autre opération) ; `R/wallet/WalletCardView.kt` (ligne « Gains en attente ») ; `android/core/src/main/resources/castbridge/quiz/play.html` (« La TV joue au Défi des 10 000 »).
- **Interdit** : `C/wallet/millions/**` (consommé), `backend/**`, `C/quiz/QuizRoom.kt`.

## Spécification
Écran du § 13.6 (échelle à droite, paliers 5/8/10/13 marqués, 50:50 une fois, 30 s, écran de choix « Emporter 1 200 NDEM » / « Continuer vers 1 600 NDEM » **seulement** aux paliers, « Mauvaise réponse : fin de la partie · gain 0 ») ; confirmation de mise « Miser 500 NDEM (cinq cents) ? » ; ligne permanente « Parties gagnées : 1/3 aujourd'hui · 4/10 cette semaine · 7/15 ce mois » (`MillionsWinCaps.progressLine`) ; plafond atteint : bouton « Jouer » désactivé **avant** toute mise, message exact de `MillionsWinCaps` avec la date de réouverture ; disponible = `MillionsOfflineBudget` ; bandeau « Hors ligne · gains confirmés à la prochaine connexion » ou, en ligne, synchronisation à la fin et « 1 200 NDEM confirmés » ; reprise automatique d'une partie interrompue au démarrage de l'écran ; pack absent ou expiré : « Connectez la TV pour charger de nouvelles questions » ; textes ≥ 28 px à 720p, focus visible.

## Critères d'acceptation
- Essai sur la TV de référence (captures au rapport) : trois parties gagnées puis « Limite atteinte … demain à 00:00 » sans mise prise ; heure de la TV avancée à la main : le mode reste fermé ; partie hors ligne avec retrait au palier 8, coupure de courant au milieu d'une autre partie puis reprise, reconnexion ⇒ gain confirmé ; une partie perdue ⇒ mise confirmée débitée ; TV d'essai : tuile grisée.
- Aucun gain affiché comme confirmé avant la réponse du serveur (test du modèle de vue pur, s'il est extrait) ; journaux conservés jusqu'à confirmation.
