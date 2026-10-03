# Liste de contrôle d'audit W16 — location au choix, compteur d'usage, clés (cahier w16-06)

À remplir par l'auditeur Opus. Les vecteurs `tools/activation/rental-pilot-vectors.json` (rejoués par `RentalPilotVectorsTest` en Kotlin et par `python3 tools/activation/verify_vectors.py --pilot`) donnent, pour chaque question, les cas qui la verrouillent. Fuseau du pilote : Africa/Douala (UTC+1).

| # | Question | Cas de vecteurs qui la verrouillent | Où lire le code |
|---|---|---|---|
| a | Une activation peut-elle donner plus de 96 h à l'heure ? | `line-96h`, `extend-37h-after-60h-refused`, `extend-12h-after-90h-refused`, `clamp-60h-plus-36h-no-note` (60 h + 36 h = 5 760, sans note), `clamp-sum-above-96h`, `clamp-90h-plus-12h` (plafonné à 96 h, note « 6 h non applicables ») | `PilotRules.extend`, `RentalEngine.contracts` (`maxUseMinutesPerContract`) |
| b | Une ligne à 0 peut-elle effacer un budget sans refus de l'émetteur ? | `mixed-units-engine-no-budget` (le moteur ignore la ligne en jours), `extend-days-on-hourly-contract-refused`, `extend-hours-on-day-contract-refused` (l'émetteur refuse) | `PilotRules.extend` (unité), `RentalEngine.contracts` (ligne d'une autre unité) |
| c | Le compteur peut-il compter deux fois une minute ? perdre plus d'une minute ? compter TV éteinte ? | `meter-59s-59s-58s` (reste reporté, jamais arrondi vers le haut), `meter-close-after-30s` (le reste survit à `close`), `meter-pause-6min` (pause comptée 5 min au plus), `meter-idle-31min` (30 min sans touche, pas 31), `meter-reboot` (le redémarrage perd moins d'une minute et ne double jamais) | `UseMeter`, `RentalLedger.recordTick` (seau à jetons sur l'horloge monotone) |
| d | Le relevé porte-t-il une donnée personnelle ? | `usage-report-two-contracts` (ni licence, ni siège, ni enveloppe, ni facteur d'appareil, ni profil ; identifiant d'installation = 16 hex de `install.key`) | `RentalLedger.usageReport` |
| e | La clé de contrat est-elle dérivée comme avant (même `rentalKey`) ? | `build-activation-12h` (enveloppe v2 scellée pour l'installation, graine éphémère fixée) ; les vecteurs v1/v2 (`rental-vectors*.json`) restent intacts et rejoués | `RentalIssuing.right`, `RentalKeys.rentalKey` |
| f | Un contrat en jours a-t-il bien `maxUsageMinutes = 0` ? | `line-default-30d`, `line-7d`, `line-ten-fields-days-no-budget`, `mixed-units-engine-days-first` (une ligne en heures ne donne jamais de budget à un contrat en jours) | `PilotRules.spec` (jours : 0), `RentalEngine.contracts` |
| g | Les phrases évitent-elles toute conversion heures ↔ jours ? | `status-strings-hours-active` (« 10 h 20 d'utilisation · à utiliser avant le 15/11 »), `status-strings-hours-expired-usage`, `status-strings-hours-expired-date`, `status-strings-days`, `status-strings-days-ended` | `RentalEngine.unitMessage` |

## Points supplémentaires que les vecteurs rendent vérifiables

1. Fenêtre du pilote : `line-12h-bound-12oct-first-instant` (12/10 00:00 accepté), `before-pilot-refused` (11/10 23:59:59.999 refusé), `line-1h-last-instant-of-pilot` (01/11 23:59:59.999 accepté, 14 jours de sûreté), `after-pilot-end-refused` (02/11 00:00 refusé).
2. Heures à utiliser avant le 15/11 : `line-12h-bound-25oct` (21 jours), `line-96h` (20/10 : 26 jours). Jours et défaut honorés jusqu'au 01/12 : `line-default-honoured-until-1dec`, `line-30d-last-instant-of-pilot`.
3. Prolongation en heures : une seule fois jusqu'au 16/11 23:59:59.999 (`extend-hours-to-16nov-once` accepté au bord exact, `extend-hours-edge-plus-1ms-refused`, `extend-hours-after-16nov-limit-refused`).
4. Quota : 192 h sur 168 h glissantes (`quota-192h-168h-last-hour-accepted`, `quota-192h-168h-refused`, `extend-quota-168h-refused`).
5. 3 contrats actifs au plus (`third-contract-accepted`, `fourth-contract-refused`) ; Langues jamais loué (`langues-refused`) ; famille inconnue refusée (`unknown-family-refused`).
6. Réémission : refusée avec la même clé d'installation (`reissue-same-key-refused`), heures = reste, jours = arrondi inférieur (`reissue-hours-other-key`, `reissue-days-floor`), possible après le 01/11 jusqu'à la fin d'origine (`reissue-after-pilot-allowed-until-original-end`).
7. Une ligne à 10 champs reste analysable par `RentalLines.parse` (`line-ten-fields-*`, `line-nine-fields-refused`) et par le miroir Python (`rental_ok`).

## Écarts connus à trancher par l'auditeur

- Le miroir serveur Java (`backend/.../WireActivation.rentalBounds`) ne rejoue pas ces vecteurs : non demandé dans ce passage (voir le rapport w16-06).
- Les phrases françaises sont verrouillées côté Kotlin seulement ; Python vérifie les nombres, les états et les bornes, pas les textes.

## Réponses de l'audit

(à remplir par l'auditeur Opus)
