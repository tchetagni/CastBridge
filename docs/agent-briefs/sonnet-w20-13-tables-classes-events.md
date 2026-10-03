# w20-13 — (CONDITIONNEL) Produit V2 : tables multiples par salle, salles de classe (enseignant hôte téléphone, présence, export CSV local), tournois horaires, salles sponsorisées **sans prix**
<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (hôte téléphone : droits) · statut : **CONDITIONNEL** (D-W20-6, D-W20-10 ; après w20-09 ; partie `S/` après le gel)
> **Groupe : W20-S5** (V2) · prérequis : w20-09 fusionné ; décisions D-W20-6 et D-W20-10 **oui** · porte : `gradle :play-server:test --tests 'castbridge.play.v2.*'` + `:core:test --tests 'castbridge.core.quiz.online.*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 3 j) · audit Opus : échantillon

**Vague 20 · Effort L · Modèle : sonnet · Statut CONDITIONNEL.** Conception : `DESIGN-W20` § 2.4 (tables), § 2.5 (enseignant, sponsor), § 2.10 (d), § 7 (D-W20-6, 8, 10). Branche `claude/sonnet-w20-13`. Rapport : `docs/agent-reports/sonnet-w20-13.md`. **Rien de ce cahier n'implique d'argent, de prix ni de publicité ciblée** (juridique 2026-12-31).

## Objectif
(1) **Tables multiples** : une salle = 1..8 tables (`ServerRoom.Tables`, w20-02 a la structure) jouant la **même** séquence (graine commune) ; joueur distant ⇒ première table libre ou choix ; classement par table **et** de salle ; l'hôte voit le tableau des tables. (2) **Salle de classe** : hôte = **téléphone** (`PlayRules` : app CastBridge avec une TV de confiance vue en 30 jours **ou** licence « école » si elle existe côté activation ; sinon TV seulement) ; pseudonymes = prénoms (liste saisie par l'enseignant, **locale au téléphone**, jamais envoyée : seuls les pseudonymes choisis par les élèves transitent), présence (qui a rejoint / répondu), **export CSV local** sur le téléphone (jamais sur le serveur), questions par niveau/filière, mode Entraînement possible (explications), aucun classement public. (3) **Tournois horaires** : `ScheduledEvent(startAt, settings, tables max)` créé depuis la page admin `play` (w20-10) : le serveur ouvre les salles à l'heure dite, les joueurs rejoignent par un code d'évènement, classement d'évènement (points), **aucun prix** ; texte « Tournoi amical : aucun lot ». (4) **Salle sponsorisée sans prix** : champ `sponsorName` (≤ 32 car.) affiché sur le bandeau de la salle et la page ; **rien d'autre** (pas de lien, pas d'image, pas de collecte) ; créé par l'admin `play`.

## Pourquoi (preuves)
- `C/quiz/QuizRoom.kt:206` `startGame(seed)` : graine injectable ⇒ même séquence sur plusieurs tables.
- `DESIGN-W20` § 2.5 : « Enseignant (salle de classe) … classement par classe, pas public » ; « salles sponsorisées sans prix » autorisées avant le juridique (D-W20-10).
- `docs/PARENTAL.md` § Profils : import des élèves d'Apprendre : la liste de prénoms **reste locale** (même principe).

## Fichiers possédés
- Cœur : `C/quiz/online/{Tables, ClassRoom, EventSchedule}.kt` (purs), tests `CT/quiz/online/{TablesTest, ClassRoomTest, EventScheduleTest}.kt`.
- Service : `server-play/src/main/kotlin/castbridge/play/v2/{TablesService, EventService, SponsorField}.kt`, `V3__events.sql`, tests `…/v2/*Test.kt`, zone additive page admin (w20-10).
- Téléphone (après le gel) : `S/quiz/ClassRoomScreen.kt` (liste locale, présence, export CSV via le partage Android), zone additive `S/QuizScreen.kt`.
- Interdit : `R/`, `backend/**`.

## Étapes
1. **Rouge** : `TablesTest` (même graine ⇒ mêmes questions sur 3 tables ; classement de salle), `ClassRoomTest` (présence, CSV local sans donnée serveur), `EventScheduleTest` (ouverture à l'heure, horloge injectée ; fuseau `Africa/Douala` affiché, UTC stocké).
2. Service, puis écran téléphone (après le gel).
3. **Vert**.

## Critères d'acceptation
- Un élève ne peut pas voir la liste de prénoms de l'enseignant (elle n'existe pas sur le serveur : test d'absence).
- `sponsorName` : texte seul, échappé, ≤ 32 car. ; aucune autre donnée de sponsor dans le schéma.
- Un tournoi ne peut pas être créé avec un champ « prix », « lot », « montant » (le schéma n'en a pas ; la page admin non plus).

## Cas limites
- Enseignant qui perd le réseau : règle hôte-perdu (§ 2.7) : auto-hôte 10 min, la classe finit sa partie. Tournoi à 2 inscrits ⇒ se tient quand même (ou annulé 5 min avant si 0 : texte).

## À ne pas faire
- Pas de prix, pas de paiement, pas de publicité, pas de collecte de prénoms/classes sur le serveur, pas de classement public des classes.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=tables,class,event · proto=play-v1 (additif) · reason=PLAY_* · deux écrans=TablesTest` + rappel explicite « aucun élément monétaire ».
