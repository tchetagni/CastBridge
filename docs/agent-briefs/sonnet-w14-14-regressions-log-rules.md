# w14-14 — Docs : `docs/REGRESSIONS.md` amorcé avec R-01…R-05, règle « test rouge d'abord », portes par type de cahier dans `docs/COORDINATION.md` et les gabarits d'exécution
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si une ancre « avant » est introuvable deux fois · statut : PRÊT
> **Groupe : W14-a** (vague W14, tranche S1) · prérequis : aucun · porte : `grep -c '^| R-0' docs/REGRESSIONS.md` ⇒ 5 et `grep -c 'Barrière anti-régression' docs/COORDINATION.md docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md` ⇒ 2
> **Jauge : ≈ 100 k jetons entrée / 10 k sortie** (effort S) · audit Opus : non

**Vague 14a (docs) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W14` § 4.4 (journal), § 5 (processus), § 1.1 (les cinq cas). Branche `claude/sonnet-w14-14`. Rapport : `docs/agent-reports/sonnet-w14-14.md`.

## Objectif
Créer le journal des régressions et inscrire les règles de porte dans les deux documents que tout agent lit, **sans rien inventer** : tout le contenu est donné ci-dessous ou dans la conception.

## Fichiers possédés
Nouveau : `docs/REGRESSIONS.md`. Zones : `docs/COORDINATION.md` (**ajout** d'une section `## Barrière anti-régression (W14)` à la fin), `docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md` (**ajout**, dans les gabarits A, B et C, d'un bloc `PORTE W14` donné ci-dessous, juste avant `RAPPORT (format exact)`). **Hors zone** : tout le reste (pas `HANDOFF.md`, pas les index).

## Contenu à écrire

### `docs/REGRESSIONS.md` (exact, à compléter seulement avec les colonnes « cause » depuis `DESIGN-W14` § 1.1)
```
# Journal des régressions de terrain (une ligne par défaut vu par le propriétaire ou un testeur)

> Règle : **tout défaut de terrain commence par un test rouge** (JVM `CT/journey/*` de préférence, sinon un pas de `tools/smoke/`), cité ici avec sa sortie rouge puis verte ; aucun correctif n'est fusionné sans. Aucun secret (ni PIN, ni jeton, ni adresse complète). Parcours : `docs/test-plans/PARCOURS-CRITIQUES.md`. Conception : `docs/coordination/DESIGN-W14-BARRIERE-ANTI-REGRESSION-2026-10-02.md`.

| id | Date | Symptôme (mots du propriétaire) | Appareils / versions | Cause racine (fichier:ligne, commit) | Parcours | Test (rouge avant / vert après) | Corrigé en | Statut |
|---|---|---|---|---|---|---|---|---|
| R-01 | 2026-10-02 | copie vers la TV figée, puce verte, le code PIN de la TV devait être ressaisi, aucun message | S21+ 1.2.31-beta · TV 0.14.18-beta-verrouillee (GaiaOS 32 bits) | <§ 1.1> | P-07, P-08, P-36 | `PinJourneyTest.rotatedPinBlocksCopyWithMessageAndNotification` (w14-02) — rouge attendu jusqu'à W13 w13-07/08 | — | OUVERTE |
| R-02 | 2026-10-02 | « Ouvrir avec CastBridge » : « Aucune TV ajoutée », Copier grisé, alors que la TV est connectée par le code | idem | <§ 1.1> | P-22, P-23, P-24 | `SendChoiceTest` (commit 6885704) ; `OpenWithJourneyTest.decideFromRealStates` (w14-04) | 1.2.32-beta | CORRIGÉE (à confirmer sur appareil) |
| R-03 | 2026-10-02 | le téléphone de confiance ne marche plus ; « Réassocier » fait disparaître la TV | idem ; TV réinstallée le 2026-10-02 | <§ 1.1> | P-02, P-03, P-04 | `LinkDriverTest` (commit cf47cc0) ; `LinkJourneyTest.reassociateAbandonedKeepsTv` (w14-02) | 1.2.32-beta | CORRIGÉE (à confirmer sur appareil) |
| R-04 | 2026-10-02 | pendant une copie, la TV ne montre aucune progression et reste sur « Prêt à recevoir » (vu deux fois) | idem | <§ 1.1> | P-11, P-13, P-36 | `TransferJourneyTest.lanSmallProgressBothSides` / `bluetoothLaneProgressAndMoveDisabled` (w14-03) | — | OUVERTE |
| R-05 | 2026-10-01 | activation : échecs de connexion Bluetooth en rafale, état d'activation non vu par le téléphone, Bluetooth lent, dialogues de permission en boucle | idem | <§ 1.1> (DESIGN-W7 § 2 n° 1, 2, 7, 9) | P-25, P-26, P-35 | `ActivationJourneyTest.stateSeenByPhoneWithinOnePoll` (w14-04) ; pas de fumée `phone_permissions` (w14-08) | — | OUVERTE |

## Comment ajouter une ligne
1. Reproduire avec les mots du propriétaire, versions lues à l'écran « Version ».
2. Nommer le parcours (ou en ajouter un dans PARCOURS-CRITIQUES.md).
3. Écrire le test qui échoue ; coller la sortie rouge dans le rapport du cahier.
4. Corriger ; coller la sortie verte ; mettre « Corrigé en <version> » et « CORRIGÉE (à confirmer sur appareil) » jusqu'à la liste humaine suivante, puis « FERMÉE ».
```

### `docs/COORDINATION.md`, section à ajouter
Titre `## Barrière anti-régression (W14)`, puis la table « Type de cahier | Exécutant doit faire tourner | Coordinateur avant fusion | Opus audite » de `DESIGN-W14` § 5 recopiée **telle quelle**, puis les trois règles : (1) aucune fusion d'un cahier touchant `S/**` ou `R/**` sans `tools/smoke/out/<date>/REPORT.md` PASS cité dans le rapport ; (2) au plus 2 cahiers risqués (liaison/confiance/transfert) en parallèle, un seul sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt` ; (3) tout correctif terrain = ligne `docs/REGRESSIONS.md` + test rouge d'abord.

### `EXECUTOR-PROMPT-TEMPLATE.md`, bloc `PORTE W14` (identique dans A, B, C)
```
PORTE W14 (Barrière anti-régression) : avant le rapport, lance la porte du cahier PUIS `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*'` ; si ton cahier touche android/sender ou android/receiver, écris dans le rapport la ligne `FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)` ; si ton cahier corrige un défaut de terrain, le rapport contient la sortie ROUGE du test avant correctif puis VERTE après, et la ligne proposée pour docs/REGRESSIONS.md.
```

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'PORTE W14' docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md` ⇒ 3 ; aucun secret dans `docs/REGRESSIONS.md` (`grep -E 'cbk_|X-CB-Pin: *[0-9]|192\.168\.[0-9]+\.[0-9]+' docs/REGRESSIONS.md | wc -l` ⇒ 0 ; les identifiants de commit à 7 caractères sont permis).

## À ne pas faire
Ne pas réécrire d'autres sections ; ne pas modifier les index de vagues ; ne pas inventer de cause : recopier § 1.1.

## Rapport
`STATUT`, lignes ajoutées (numéros), rien d'autre.
