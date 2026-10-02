# w16-10 — CastBridge-TV : compteur par lot réel (Apprendre, Quiz), toutes les locations mesurées, phrases par unité, bandeau 10 min, question de fin de location, télémétrie

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (fichiers chauds `R/RentalHub.kt`, `R/LearnActivity.kt` ; parcours J obligatoire) · statut : **ATTEND la sortie du gel** (plan de stabilisation § 5) et w15-17 fusionné (`R/RentalHub.kt` zone `TvClock`)
> **Groupe : W16d-1** (vague W16d, TV) · prérequis : w16-01, 02, 03, 07 fusionnés ; w15-17 ; harnais J (W14 w14-01) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.*Rental*' && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : **oui**

**Vague 16d · Effort M · Modèle : sonnet · Statut ATTEND.** Conception : DESIGN-W16 § 2, § 1.3, § 1.5, § 5.2, § 6.2. Branche `claude/sonnet-w16-10`. Rapport : `docs/agent-reports/sonnet-w16-10.md`. Règle W15 R2 : un seul cahier à la fois sur `R/TvService.kt` (ici : **aucune** modification de `TvService`).

## Objectif
La TV compte le temps d'utilisation **du lot réellement ouvert**, pour **toute** location (à l'heure : plafond ; en jours : mesure), dans Apprendre **et** le Quiz, avec `UseMeter` (pause, inactivité) ; affiche les phrases par unité et le bandeau « il vous reste 10 min d'utilisation » ; à la fin d'une location, propose (une fois, jamais sous profil enfant) la question « auriez-vous payé ? » ; émet les événements `rental_*` sous consentement.

## Pourquoi (preuves)
- `R/RentalHub.kt:153-158` (`meterOneMinute` : premier contrat plafonné, premier lot : à remplacer) ; `:42` (`TvClock(mono = elapsedRealtime)`) ; `:161-164` (`sweep`).
- `R/LearnActivity.kt:89-92` (tick 60 s au premier plan) ; Quiz : aucun compteur (vérifié).
- `R/ActivationCenter.kt:91, 108` (`RentalHub.statuses`, badge) ; `R/TvService.kt:187, 221` (balayage 15 min, démarrage : **ne pas toucher**).
- `UseMeter`, `RentalLedger.usageReport` (w16-02) ; phrases (w16-01) ; `rental_*` (w16-07).
- Parental : `docs/PARENTAL.md` (profil enfant, code) ; `R/ParentalHub.kt` expose le profil actif (**zone lue seulement**).

## Fichiers possédés
`R/RentalHub.kt` (zone `meter*` : remplace `meterOneMinute` par `meter(ctx): UseMeter` + `onLotOpened/Closed/Paused/Resumed/Input` + `tick`), `R/LearnActivity.kt` (zone `meter`), `R/QuizActivity.kt` (zone compteur, à créer), nouveau `R/RentalEndPrompt.kt` (question), `R/RentalViews.kt` ou l'écran existant des locations (zone phrases/bandeau), nouveau `CT/journey/RentalJourneyTest.kt` (harnais J). **Hors zone** : `R/TvService.kt`, `R/ActivationCenter.kt`, `C/**` (sauf ajout du parcours J), `R/ParentalHub.kt` (lecture).

## Étapes
1. **Rouge (J)** : `RentalJourneyTest` : « louer 1 heure d'utilisation, ouvrir le lot, 61 ticks ⇒ fin par usage, lots effacés au balayage » ; « 7 jours puis horloge +8 j ⇒ fin par date, minutes comptées sans plafond » ; « deux locations, chaque lot ouvert reçoit ses minutes » ; « 12 heures, borne atteinte avec 5 h restantes ⇒ fin par date, phrase “heures non utilisées” » ; « inactivité 31 min ⇒ compteur arrêté ».
2. `RentalHub` : un `UseMeter` par processus, `tick` toutes les 60 s depuis l'écran ouvert (Learn/Quiz) **et** à `onPause` ; `recordUsage` pour tout lot loué ; `EXPIRED` ⇒ `sweep(LEARN_SCREEN, lessonActive = true)` (comportement existant) ; `usageReport` servi par `RentalApi` (déjà, w16-03).
3. `LearnActivity`/`QuizActivity` : `onLotOpened(lotId)` quand une fiche/un lot loué s'ouvre (le lot courant est connu de l'écran : `LotId("learn", <classe>)`, `LotId("quiz", <lot>)`), `input` sur toute touche, `onPause` ⇒ `close`.
4. Phrases et bandeau : utiliser les constantes de w16-01 ; bandeau discret à `HOUR_1` (10 min) ; ligne « Test gratuit : les heures se terminent au plus tard le JJ/MM » déduite de `contract.endsAt` des contrats `HOURS`.
5. `RentalEndPrompt` : une fois par contrat (clé dans les préférences privées), jamais si profil enfant actif, « oui / non / à un autre prix / ne pas répondre » ⇒ `rental_survey` (consentement).
6. Télémétrie : `rental_start` à l'installation d'une location, `rental_use` agrégé par jour (compteur local), `rental_end`, `rental_extend`.
7. Vert : porte ; `compileDebugKotlin` ; fumée W14 `--tv fake` si disponible.

## Critères d'acceptation
Porte verte ; 5 parcours J rouges puis verts ; `grep -n "meterOneMinute" R/` vide ; aucune modification de `R/TvService.kt`, `C/owner/Keys.kt` ; sur la TV de référence (propriétaire, D-W16-9) : louer 1 heure, lire 61 min ⇒ fin ; texte lisible à 3 m (≥ 28 px, focus visible).

## Cas limites
Lot ouvert non loué (zéro) ; Keystore indisponible (compteur et carnet marchent sans clé : `R/RentalHub.kt:21`) ; `SUSPENDED` (compte quand même) ; profil enfant (pas de question) ; deux écrans Learn ouverts (impossible : une activité).

## À ne pas faire
Pas de `TvService` ; pas de minuteur dans le cœur ; pas de conversion heures ↔ jours ; pas de montant dans la question tant que D9-bis est BLOQUÉ.

## Rapport
`STATUT`, sorties J rouge/vert, captures textuelles des phrases, question : seuils pause/inactivité pour les œuvres (W10) quand elles arriveront.
