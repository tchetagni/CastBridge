# sonnet-w4-03-fix : correctifs de l'audit Opus (branche claude/sonnet-w4-03-fix)

1. KeystoreWrapper : l'alias n'est généré que s'il est absent (`containsAlias`) ; supprimé puis recréé uniquement sur `KeyPermanentlyInvalidatedException` ; toute autre erreur est relancée. La sonde `orPlain` utilise son propre alias `castbridge-probe`. Décisions pures dans `core/lots/InstallKeyPolicy`.
2. RentalHub.ensure : si `install.key` porte `wrap=keystore` et que le repli est `plain`, exception (parts reste null, nouvel essai au prochain appel) ; le repli plain ne sert qu'à une première création (`InstallKeyStore.storedWrap`, `InstallKeyPolicy.mayUseWrapper`).
3. Latence : `RentalHub.warm` (fil d'arrière-plan) appelé en fin d'`ActivationCenter.init` ; `statuses()` sur le fil principal retourne une liste vide (et lance le préchauffage) tant que les clés ne sont pas prêtes.
4. `POST /api/activation/install` : la réponse 200 porte `"notes":[...]` (`ActivationCenter.lastRentalNotes`).
5. Note « clé illisible » émise une seule fois par processus.
6. admin.html : « inconnue » pour une protection autre que keystore/plain.

Tests : `InstallKeyPolicyTest` (4 tests). `:core:test` : 2101 tests, 1 échec `DeviceTest.withoutDeviceReports501` (BindException réseau de l'environnement, sans rapport). `:receiver:compileDebugKotlin` OK.
Risques : la partie Android (Keystore) n'est pas testable en JVM ; `statuses()` vide au tout premier appel du fil principal (quelques centaines de ms au démarrage).
