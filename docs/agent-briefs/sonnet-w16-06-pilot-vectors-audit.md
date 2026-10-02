# w16-06 — Vecteurs du pilote (`rental-pilot-vectors.json`) rejoués par Kotlin, Java et Python, et audit du format, du compteur et des clés

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** (exécution) · escalade : **audit Opus obligatoire** (format de contrat, compteur, clés : ce cahier EST la relecture croisée) · statut : PRÊT (après w16-01, 02, 03, 04)
> **Groupe : W16a-5** (vague W16a, cœur + miroirs, autorisé pendant le gel) · prérequis : w16-01…04 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalPilotVectors*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='RentalPilotVectorsTest' && python3 tools/activation/verify_vectors.py --pilot`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort S-M, ≈ 1 j) · audit Opus : **oui**

**Vague 16a · Effort S-M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 2.5 (vecteurs), § 1.4, § 2.2. Branche `claude/sonnet-w16-06`. Rapport : `docs/agent-reports/sonnet-w16-06.md`.

## Objectif
Un fichier de vecteurs **nouveau** (les v1 et v2 ne bougent pas) qui fige : les lignes signées des trois choix, la prolongation, le clamp, la troncature par la borne de sûreté, les refus de l'émetteur, les séquences du compteur, les phrases, le relevé ; rejoué par le cœur Kotlin, par le serveur Java (analyse de ligne et bornes : `B/licenses/WireActivation.java:34-44`) et par Python (`tools/activation/verify_vectors.py`). Plus une **liste de contrôle d'audit** remise à l'auditeur Opus.

## Pourquoi (preuves)
- Patron existant : `C/lots/RentalVectorsV2.kt`, `CT/lots/RentalVectorsV2Test.kt`, `tools/activation/rental-vectors-v2.json` (`format: castbridge-rental-vectors-v2`, générés par `CASTBRIDGE_WRITE_VECTORS=1`).
- `B/licenses/WireActivation.java:43-44` (`rentalBounds`, miroir de `RentalLines.bounds`) ; vecteurs serveur existants dans `backend/src/test`.
- `docs/RENTAL-LOTS.md:97` (régénération) ; `DESIGN-W4:84` (vecteurs v2).

## Fichiers possédés
Nouveaux `tools/activation/rental-pilot-vectors.json`, `C/lots/RentalPilotVectors.kt`, `CT/lots/RentalPilotVectorsTest.kt`, `backend/src/test/java/castbridge/server/licenses/RentalPilotVectorsTest.java`, `docs/coordination/AUDIT-W16-CHECKLIST.md` ; `tools/activation/verify_vectors.py` (section `--pilot` additive). **Hors zone** : `rental-vectors.json`, `rental-vectors-v2.json`, le code cœur des autres cahiers.

## Étapes
1. Cas (ids stables) : `line-default-30d`, `line-7d`, `line-1h`, `line-12h-bound-25oct`, `line-96h`, `extend-6h-plus-6h`, `clamp-sum-above-96h` (deux lignes 60 h : contrat à 5 760), `mixed-units-refused` (émetteur) et `mixed-units-engine-no-budget` (moteur : documenté), `after-pilot-end-refused`, `fourth-contract-refused`, `meter-59s-59s-58s`, `meter-close-after-30s`, `meter-pause-6min`, `meter-idle-31min`, `meter-reboot`, `status-strings-hours-active/expired-usage/expired-date`, `status-strings-days`, `usage-report-two-contracts`, `build-activation-12h` (jeton complet, octets, avec la graine éphémère donnée comme en v2).
2. Générateur Kotlin (`RentalPilotVectors.write`) ; test de rejeu ; Java : rejoue `line-*` (analyse, bornes) et `build-activation-12h` (vérification de signature) ; Python : `line-*`, `build-activation-12h`, `usage-report-*`.
3. `AUDIT-W16-CHECKLIST.md` : (a) une activation peut-elle donner > 96 h à l'heure ? (b) une ligne à 0 peut-elle effacer un budget sans refus émetteur ? (c) le compteur peut-il compter deux fois une minute ? perdre > 1 min ? compter TV éteinte ? (d) le relevé porte-t-il une donnée personnelle ? (e) la clé de contrat est-elle dérivée comme avant (même `rentalKey`) ? (f) un contrat en jours a-t-il bien `maxUsageMinutes = 0` ? (g) phrases sans conversion heures ↔ jours.
4. Vert : porte.

## Critères d'acceptation
Porte verte ; `git diff --stat tools/activation/rental-vectors.json tools/activation/rental-vectors-v2.json` vide ; ≥ 20 cas ; les trois rejoueurs verts ; la liste de contrôle remplie par l'auditeur (section « Réponses de l'audit » laissée vide ici).

## À ne pas faire
Ne pas modifier un vecteur existant ; pas de clé réelle (tout dérivé de textes publics, avec l'avertissement habituel) ; pas de serveur lancé.

## Rapport
`STATUT`, nombre de cas, écarts Kotlin/Java/Python trouvés (et corrigés **dans ce cahier seulement** si le miroir Java/Python est faux ; sinon question), questions d'audit non résolues.
