STATUT: TERMINÉ
CAHIER: sonnet-w4-03 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w4-03 · COMMIT: voir git log
JETONS: inconnu
PORTE: gradle :core:test --tests RentalKeyedInstallTest + :receiver:compileDebugKotlin → VERT (34 s / 11 s)
SUITE COMPLÈTE: gradle :core:test → VERT (2097 tests, 1 ignoré, 0 échec) ; écarts : aucun
FICHIERS: android/receiver/.../KeystoreWrapper.kt (neuf), RentalHub.kt, ActivationCenter.kt, TvService.kt (ligne /api/activation), android/core/src/main/resources/castbridge/admin.html, android/core/src/main/kotlin/castbridge/core/lots/RentalKeyedInstall.kt (neuf), android/core/src/test/kotlin/castbridge/core/lots/RentalKeyedInstallTest.kt (neuf), ce rapport
CHOIX:
- Exigence d'audit w4-01 : `onActivation` passe la clé via `RentalLedger.installKeyed(..., key: InstallKey)` (paramètre NON nul, dans le cœur, fichier neuf) : l'oubli devient une erreur de compilation. RentalHub n'étant pas testable en JVM (pas de source de test receiver), le test porte sur ce chemin : v1 après la date de péremption refusé avec clé, installé sans clé (témoin).
- `RentalNotes.of` (cœur, neuf) : lignes françaises non routinières de `install` ; `ActivationCenter.lastRentalNotes` les expose (ActivationActivity non touchée) ; clé illisible/régénérée ajoute une note « demandez la réémission ».
- Clé générée dans `RentalHub.ensure` (pas dans `init`), utilisable verrouillé ; `requestText()` retombe sur une demande sans `install=` si la clé échoue.
- `KeystoreWrapper.wrap` recrée l'alias une fois si la clé est invalidée ; `unwrap` renvoie null sur toute exception ; protection de fichiers illisibles d'`InstallKeyStore` utilisée telle quelle.
- `orPlain()` sans paramètre contexte (inutile) ; repli `PlainWrapper` + Log.w.
- /api/activation : `installKeyProtection` (keystore|plain|unknown) et `installId` (16 hex).
NON FAIT / À VALIDER SUR MATÉRIEL: Keystore réel (émulateur/TV GaiaOS armeabi-v7a), flux rental_test.py (non exécuté), « effacer les données » avec alias survivant ; première génération Keystore possiblement sur le fil principal si `requestText()` est le premier appel (ActivationActivity).
QUESTION: aucune
AUTOCONTRÔLE: [x] zone (core: 2 fichiers neufs + admin.html) [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
