STATUT: TERMINÉ (audit Opus à faire ; miroir Java non fait, voir plus bas)
CAHIER: w16-06 · MODÈLE: sonnet · BRANCHE: claude/cloud-w16-06 · COMMIT: voir git log

FICHIERS (tous neufs sauf le script Python ; rien dans android/sender ni android/receiver ; vecteurs v1/v2 intacts : `git diff --stat` des deux fichiers vide)
- tools/activation/rental-pilot-vectors.json (format `castbridge-rental-pilot-vectors-v1`, 58 cas)
- android/core/src/main/kotlin/castbridge/core/lots/RentalPilotVectors.kt (rejeu Kotlin : `run`, `compute`)
- android/core/src/test/kotlin/castbridge/core/lots/RentalPilotVectorsTest.kt (générateur `CASTBRIDGE_WRITE_VECTORS=1`, rejeu, 8 tests dont 6 d'attentes écrites À LA MAIN)
- tools/activation/verify_vectors.py (section additive `run_pilot_vectors`, option `--pilot`, aussi dans l'exécution par défaut)
- docs/coordination/AUDIT-W16-CHECKLIST.md (questions a à g, cas qui les verrouillent, section « Réponses de l'audit » vide)

ROUGE PUIS VERT
- Rouge : test écrit avec un rejeu réduit à un squelette (`run` renvoie « non implémenté ») → 6 tests sur 10 rouges (4 par assertion, 2 par NullPointerException car l'expectation du squelette était vide). Le Python n'a pas eu de rouge propre : il a été écrit après le Kotlin ; à la place, 12 mutations du fichier (jours 26→30, refus→accepté, quota, Langues, même clé de réémission, plafond 5761, budget effacé, jeton altéré, date mal étiquetée, donnée personnelle dans le relevé, bord 16/11) sont TOUTES détectées.
- Vert : trois attentes écrites à la main étaient fausses (mon calcul, pas le code) et corrigées : 96 h le 20/10 = 26 jours de sûreté ; une pause de 6 min compte 5 min puis 1 min après la reprise ; ajout du bord +1 ms.

COMPTES RÉELS
- `tools/core-harness/run.sh :core:test --tests '*Rental*' --tests '*Pilot*'` → 190 tests, 0 échec (gradlew absent ; `--offline` échoue faute de cache de plugin, relancé avec réseau via le proxy).
- `python3 tools/activation/verify_vectors.py --pilot` → 177 contrôles, 0 échec ; exécution complète → 381 contrôles, 0 échec (v1 : 52, test-vectors : 139, inchangés).
- Suite complète `:core:test` → 2878 tests, 11 échecs, TOUS dans LearnLotsTest/BaseContentTest (« le contenu du lot maternelle a changé… --update »). Reproduits à l'identique sur la base sans mes fichiers : préexistants, sans lien.
- Non lancé : compilation sender/receiver (non touchés), fumée (`FUMÉE: à lancer par le coordinateur (tools/smoke/smoke.py --tv fake)` seulement si ces modules changent : sans objet ici), porte Java.

COUVERTURE (58 cas) : fenêtre 12/10 00:00 et 01/11 23:59:59.999 (+ bords refusés à −1 ms et 02/11 00:00) ; heures avant le 15/11 (30/21/26/14 jours de sûreté) ; jours et défaut jusqu'au 01/12 ; plafond 96 h (60+36 = 5760 sans note, 90+12 plafonné avec note « 6 h », 37 h et 12 h refusés à l'émission) ; prolongation d'une autre unité refusée (deux sens) et ignorée par le moteur ; prolongation en heures jusqu'au 16/11 23:59:59.999 une fois (bord exact accepté, +1 ms refusé) ; quota 192 h sur 168 h glissantes ; 3 contrats actifs (4e refusé) ; Langues et famille inconnue refusés ; réémission refusée avec la même clé, heures = reste, jours = arrondi inférieur, possible après le 01/11 ; compteur (5 scénarios) ; phrases (5) ; relevé ; jeton complet de 12 h ; lignes à 10 champs (3 lisibles, 1 à 9 champs refusée) par `RentalLines.parse` et `rental_ok`.

CHOIX
- Les dates à l'ancre sont écrites `T` (période) et `T2` (instant d'émission quand il diffère) dans les lignes, comme `rental|loc-classe-cm2|classe-cm2|T|T|30|0|720|3|` ; chaque date importante porte une étiquette Douala (+01:00) que Kotlin et Python recalculent séparément.
- Le Python est une seconde implémentation écrite depuis la conception (règles d'émission, fusion du moteur, relevé, jeton signé vérifié avec la vraie signature Ed25519), pas un portage. Les phrases françaises et le compteur restent rejoués par Kotlin seul ; Python borne seulement les minutes du compteur.
- Le jeton du cas `build-activation-12h` utilise l'enveloppe v2 : Python vérifie la signature, la fenêtre, la ligne (9 premiers champs égaux à l'attendu, boîte commençant par `v2:`) mais ne rouvre pas la boîte (pas d'X25519 dans verify_vectors.py).

NON FAIT / QUESTION
- Miroir Java (`WireActivation.rentalBounds`, `RentalPilotVectorsTest.java`) : le brief le cite mais la consigne de ce lancement demandait Kotlin et Python seulement ; non fait, aucun écart Kotlin/Python trouvé. À planifier (porte `./mvnw -o test -Dtest=RentalPilotVectorsTest`).
- `--offline` : cache Gradle absent dans ce conteneur ; à relancer sur le Mac avec `gradle-lock.sh` si la barrière W14 l'exige (`journey`/`lint` couverts ici par la suite complète, sans échec hors contenu maternelle).

À REGARDER PAR L'AUDIT OPUS
1. `PilotRulesTest` et ces vecteurs partent de la même `PilotRules` : seul le miroir Python est indépendant. Relire ses 4 fonctions (`pilot_new/extend/reissue/engine`) contre la conception, surtout : fin fusionnée d'une prolongation ≤ 16/11, réémission jours = plancher, quota compté sur la réémission.
2. `reissue-days-floor` et `reissue-hours-other-key` : la fin d'origine n'est jamais dépassée, mais `days` de la ligne réémise recompte depuis l'instant d'émission.
3. `meter-reboot` fige une perte (jusqu'à moins d'une minute par lot) : confirmer qu'elle est acceptée.
4. L'étiquette/ancre `T2` : une ligne extension a startsAt ≠ period ; vérifier que la liste « 10 champs » couvre bien ce cas côté serveur Java quand il sera rejoué.
AUTOCONTRÔLE: [x] zone [x] porte [x] suite (11 échecs préexistants) [x] secrets [x] dépendances [x] FR [x] diff [x] un commit
