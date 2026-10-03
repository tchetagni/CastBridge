STATUT: TERMINÉ
CAHIER: w16-07 · MODÈLE: sonnet · BRANCHE: claude/w16-07-telemetry · COMMIT: voir git log
JETONS: inconnu
PORTE: :core:test --tests '*TelemetryTest*' + mvn -o -q test -Dtest=TelemetryApiTest → VERT
SUITE COMPLÈTE: :core:test complet + :sender:compileDebugKotlin + :receiver:compileDebugKotlin → VERT ; core 2641 tests, 0 échec ; backend TelemetryApiTest 4/4
FICHIERS: android/core/src/main/kotlin/castbridge/core/telemetry/Telemetry.kt, android/core/src/test/kotlin/castbridge/core/TelemetryTest.kt, backend/src/main/java/castbridge/server/telemetry/EventCatalog.java, backend/src/test/java/castbridge/server/TelemetryApiTest.java, docs/TELEMETRY.md, docs/agent-reports/w16-07-telemetry.md
CHOIX: cinq événements `rental_*` en catégorie usage (jamais essentiels) ; `exp` (W12) non ajoutée (absente du catalogue fusionné) ; `Telemetry.rentalSurvey(..., childProfile)` renvoie false sous profil enfant (le cœur n'a pas de notion de profil : l'appelant passe le drapeau) ; côté serveur `unit` et `reason` sont des énumérations fermées, `bundle` un code ≤ 64, `q`/`answer` des codes ≤ 32 ; dim1/dim2 : bundle/unit (start, use, extend), bundle/reason (end), q/answer (survey).
ROUGE: TelemetryTest.rentalEventsNeedUsageConsent et rentalEventsRejectForbiddenAndUnknownProps (AssertionError, événements absents du catalogue) ; TelemetryApiTest.rentalEvents... (« événement inconnu : rental_start », 5 rejets au lieu de 5 acceptés).
VERT: les mêmes tests après ajout des événements.
PARITÉ: les deux catalogues listent les mêmes cinq clés (rental_start, rental_use, rental_end, rental_extend, rental_survey).
ORDRE DE DÉPLOIEMENT: serveur avant les apps (un événement inconnu est refusé par le serveur).
CONFLIT: aucun (w17-02 fusionné, `store` déjà présent ; ajouts en fin de zone EVENTS).
NON FAIT / À VALIDER SUR MATÉRIEL: émission réelle des événements (appelants dans RentalHub/écrans, hors gel) ; agrégation journalière de `rental_use` à faire côté appelant.
FUMÉE: sans objet (aucun fichier android/sender ni android/receiver modifié).
QUESTION: aucune
