# Rapport : clé USB, un espace par TV (2026-11-04)

Branche `claude/usb-espace-par-tv` (depuis `origin/integration/agents`). Aucune PR, aucune fusion, `main` intact, `version.properties` non modifié, aucun secret lu ni écrit.
Conception : `docs/coordination/DESIGN-USB-ESPACE-PAR-TV-2026-11-04.md`. Cahier du câblage Android : `docs/agent-briefs/usb-espace-par-tv.md`.

## 1. Hypothèses à confirmer par le propriétaire (appliquées par défaut)
1. L'identifiant d'espace = 12 premiers hex de SHA-256(`castbridge-espace-v1|` + code d'appareil normalisé) ; jamais le code en clair sur la clé.
2. Une TV ne lit ni n'écrit jamais dans l'espace d'une autre ; une clé contenant d'autres espaces le signale (« Cette clé contient les données d'une autre TV ») sans les ouvrir.
3. Anciennes données à plat : la première TV qui ouvre la clé PROPOSE de les adopter, confirmation explicite (« Annuler » préselectionné), DÉPLACEMENT par renommage atomique ; jamais de copie puis suppression, jamais de suppression de donnée. Conflit de nom : les deux fichiers restent. Exécutables, liens symboliques, noms invalides : laissés en place.
4. Le fichier `activation` à la racine de `CastBridge/` reste lu automatiquement.
5. L'espace est choisi à chaque branchement à chaud, à tout moment de la vie de l'app.
6. Volume identifié par `VolumeRegistry` ; espace créé à la demande, jamais sur clé en lecture seule ni pleine (moins de 1 Mio libre).
7. Marque `CastBridge/<id>/.espace` : identifiant et date de création seulement.
8. Écrans et notifications : nom de la clé et « espace de cette TV » ; le téléphone ne sait rien de l'espace.
Choix que j'ai ajoutés (à confirmer aussi) : (a) une revendication `CastBridge/.adoption` empêche deux TV d'adopter en même temps les mêmes données ; elle devient reprenable par une autre TV après 24 h ; (b) les dossiers `index/`, `Telechargements/` (y compris les `.part`) et `Medias/` sont adoptés comme `Bibliotheque/` ; (c) les dossiers vides de l'ancienne arborescence sont retirés après adoption (jamais de suppression de contenu).

## 2. Ce qui a été fait
- `android/core/src/main/kotlin/castbridge/core/tv/UsbSpace.kt` : `UsbSpace` (identifiant, marque, `ensure`, `foreign`, `hasLegacy`) et `UsbAdoption` (`plan`, `run`, reprise, revendication). Code pur, sans Android.
- `android/core/src/test/kotlin/castbridge/core/UsbSpaceTest.kt` : 34 tests (identifiant et réponse connue, création, lecture seule, clé pleine, lien symbolique, marque trafiquée, deux TV sur la même clé, détection d'espace étranger, plan, conflits, casse exFAT, noms de 255 octets, caractères interdits, trop profond, liens symboliques non suivis, confirmation, interruption à CHAQUE point d'injection, clé retirée, fichier disparu, concurrence de revendication, 40 arborescences aléatoires coupées au hasard, 20 scénarios aléatoires à deux TV).
- Je n'ai PAS réutilisé `UsbMigration.kt` tel quel : il copie puis vérifie puis supprime, ce que l'exigence interdit pour l'adoption (renommage atomique seulement). Il reste inchangé pour « Déplacer vers la clé » / « Rapatrier ». Je ne l'ai donc pas « étendu » : décision à valider.
- Câblage Android : NON fait (voir § 4).

## 3. Ce qui a tourné
Environnement : Gradle 8.14.3 + JDK 21, mais `cd android && gradle --offline :core:test` ÉCHOUE (« could not resolve plugin com.android.application:8.9.1 » : pas de SDK Android ni de cache). Contournement : projet Gradle temporaire (hors dépôt) qui inclut seulement `:core` avec Kotlin 2.1.0 JVM, dépendances Maven Central (aucun fichier du dépôt modifié ; deux réglages mémoire dans ce projet temporaire, le compilateur Kotlin manquant de mémoire sinon).
Commande équivalente : `gradle :core:test --tests 'castbridge.core.UsbSpaceTest'`.

### Rouge d'abord (talon à comportement faux, tests écrits avant le code) : 30 tests rouges sur 34, par assertion
(4 verts par construction avec le talon : un talon qui ne fait rien satisfait « rien ne change »)
```
34 tests 30 failures 0 errors
ROUGE twoTvsOnTheSameKeyNeverOverlap | java.lang.AssertionError: Values should be different. Actual: /tmp/espace18083459601725342289/Download/CastBrid
ROUGE randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere | java.lang.AssertionError: seed 1 cut 1 expected:<{Telechargements/d0/f14=150409
ROUGE adoptionMovesEverythingExactlyOnceAndKeepsContent | java.lang.AssertionError: expected:<5> but was:<0>
ROUGE aMarkThatNamesAnotherIdIsRefused | java.lang.AssertionError: Expected value to be of type <castbridge.core.tv.UsbSpace$Ensured$Failed (Kotlin re
ROUGE aSpaceThatIsASymbolicLinkIsRefused | java.lang.AssertionError: Expected value to be of type <castbridge.core.tv.UsbSpace$Ensured$Failed (Kotlin 
ROUGE forbiddenCharactersTooDeepAndSymlinksAreIgnoredNotFollowed | java.lang.AssertionError: Expected value to be true.
ROUGE aStaleClaimOfAnotherTvCanBeTakenOver | java.lang.AssertionError: Expected value to be true.
ROUGE aReadOnlyDriveIsRefusedAndNothingChanges | java.lang.AssertionError: expected:<REFUSED_READ_ONLY> but was:<DONE>
ROUGE legacyIsDetectedOnlyWhenAFileIsThere | java.lang.AssertionError: Expected value to be true.
ROUGE planListsMovesConflictsAndIgnoredWithoutTouchingAnything | java.lang.AssertionError: expected:<[Bibliotheque/sous/b.mkv, Telechargements/c.mp4.p
ROUGE readOnlyDriveGetsNothingCreated | java.lang.AssertionError: expected:<castbridge.core.tv.UsbSpace$Ensured$ReadOnly@644baf4a> but was:<castbridge
vert  theSameTvResumesItsOwnClaim 
ROUGE nothingMovesWithoutExplicitConfirmation | java.lang.AssertionError: expected:<NOT_CONFIRMED> but was:<DONE>
ROUGE anotherTvHoldingAFreshClaimBlocksTheAdoption | java.lang.AssertionError: expected:<REFUSED_CLAIMED> but was:<DONE>
ROUGE namesDifferingOnlyByCaseCollideOnExfatButNotOnExt4 | java.lang.AssertionError: expected:<1> but was:<0>
ROUGE fullDriveIsRefusedAndNothingIsCreated | java.lang.AssertionError: expected:<castbridge.core.tv.UsbSpace$Ensured$Full@76a4ebf2> but was:<castbrid
ROUGE conflictsAreKeptOnBothSidesAndReported | java.lang.AssertionError: expected:<DONE_WITH_CONFLICTS> but was:<DONE>
ROUGE idIsShortHexStableAndNeverTheCode | java.lang.AssertionError: Values should be different. Actual: 000000000000
ROUGE idIsKnownAnswer | org.junit.ComparisonFailure: SHA-256 de « castbridge-espace-v1|TESTCODE », 12 premiers hex expected:<[ecadd8ee5d0a]> but
vert  executablesStayWhereTheyAreAndAreNeverMoved 
ROUGE aKeyPulledAfterTwoMovesStopsCleanlyAndResumes | java.lang.AssertionError: expected:<INTERRUPTED> but was:<DONE>
vert  foreignIgnoresPlainFoldersUnmarkedFoldersAndLinks 
ROUGE onlyTwelveLowerHexIsAnId | java.lang.AssertionError: Expected value to be false.
vert  aFileInsideASpaceIsNotLegacy 
ROUGE interruptionAtEveryStepLosesNothingDuplicatesNothingAndResumes | java.lang.AssertionError: 0 fault points
ROUGE ensureCreatesTheFourFoldersAndTheMark | java.lang.AssertionError: expected:</tmp/espace305503138836663643/Download/CastBridge/000000000000> but 
ROUGE anUnmarkedFolderWithFilesUnderMyIdIsNotTakenOver | java.lang.AssertionError: Expected value to be of type <castbridge.core.tv.UsbSpace$Ensured$F
ROUGE aNameOf255BytesIsAccepted | java.lang.AssertionError: expected:<255> but was:<259>
ROUGE aFullDriveIsRefusedAndNothingChanges | java.lang.AssertionError: expected:<REFUSED_FULL> but was:<DONE>
ROUGE twoTvsWritingRandomlyOnTheSameKeyNeverSeeEachOthersFiles | java.io.FileNotFoundException: /tmp/espace12325920501071981117/Download/CastBridge/Bi
ROUGE aFileThatVanishedBetweenPlanAndMoveIsSkippedNotFatal | java.lang.AssertionError: expected:<1> but was:<0>
ROUGE nothingToAdoptIsSaidSo | java.lang.AssertionError: expected:<NOTHING> but was:<DONE>
ROUGE ensureIsIdempotentAndKeepsTheCreationDate | java.lang.NullPointerException
ROUGE adoptionNeverReadsOrTouchesAnotherTvSpace | java.lang.AssertionError: expected:<[Bibliotheque/mine.mp4]> but was:<[]>
```
### Vert : 34 tests, 0 échec (après correction de 2 points)
- Le test aléatoire a trouvé un VRAI défaut du premier jet : une adoption coupée juste avant la levée de la revendication, reprise quand il ne restait que des exécutables, laissait `.adoption` en place. Corrigé (la branche « rien à faire » lève toujours sa propre revendication).
- Une erreur de test (nom de 259 octets au lieu de 255) et une attente erronée sur le chemin signalé « trop profond » ont été corrigées dans le test.
### Mutations (18 appliquées une à une puis retirées : toutes détectées)
- M1 identifiant tronqué à 8 : DÉTECTÉE (18 tests rouges, ex. twoTvsOnTheSameKeyNeverOverlap)
- M2 code non normalisé : DÉTECTÉE (2 tests rouges, ex. idIsShortHexStableAndNeverTheCode)
- M3 clé en lecture seule ignorée : DÉTECTÉE (1 tests rouges, ex. readOnlyDriveGetsNothingCreated)
- M4 clé pleine ignorée : DÉTECTÉE (1 tests rouges, ex. fullDriveIsRefusedAndNothingIsCreated)
- M5 son propre espace dit « étranger » : DÉTECTÉE (2 tests rouges, ex. twoTvsOnTheSameKeyNeverOverlap)
- M6 confirmation ignorée : DÉTECTÉE (1 tests rouges, ex. nothingMovesWithoutExplicitConfirmation)
- M7 écrasement possible (conflit de nom ignoré) : DÉTECTÉE (1 tests rouges, ex. planListsMovesConflictsAndIgnoredWithoutTouchingAnything)
- M8 revendication d'une autre TV ignorée : DÉTECTÉE (1 tests rouges, ex. anotherTvHoldingAFreshClaimBlocksTheAdoption)
- M9 nettoyage récursif destructeur : DÉTECTÉE (4 tests rouges, ex. randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere)
- M10 liens symboliques suivis : DÉTECTÉE (1 tests rouges, ex. forbiddenCharactersTooDeepAndSymlinksAreIgnoredNotFollowed)
- M11 collision de casse ignorée : DÉTECTÉE (1 tests rouges, ex. namesDifferingOnlyByCaseCollideOnExfatButNotOnExt4)
- M12 marque d'un autre identifiant acceptée : DÉTECTÉE (1 tests rouges, ex. aMarkThatNamesAnotherIdIsRefused)
- M13 revendication non levée si rien à faire : DÉTECTÉE (2 tests rouges, ex. randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere)
- M14 dossiers vides comptés comme données : DÉTECTÉE (1 tests rouges, ex. legacyIsDetectedOnlyWhenAFileIsThere)
- M15 clé retirée ignorée : DÉTECTÉE (1 tests rouges, ex. aKeyPulledAfterTwoMovesStopsCleanlyAndResumes)
- M16 revendication jamais levée en fin : DÉTECTÉE (3 tests rouges, ex. randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere)
- M17 exécutables déplacés : DÉTECTÉE (3 tests rouges, ex. randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere)
- M18 espace d'un autre dans le plan (racine entière) : DÉTECTÉE (1 tests rouges, ex. adoptionNeverReadsOrTouchesAnotherTvSpace)
M14 (dossiers vides comptés comme données) n'était PAS détectée au premier passage : trou de test comblé (dossier vide imbriqué ajouté à `legacyIsDetectedOnlyWhenAFileIsThere`), puis détectée.
Les mutations M1 à M13 et M15 ont été jouées avant ce renforcement, sur la même version du code ; M14, M16, M17, M18 ont été rejouées après. Les mutations sont appliquées par un script temporaire (hors dépôt), qui restaure le fichier à la fin.

### Autres suites USB, sans régression
`UsbStoreTest` 21 tests, `UsbMigrationTest` 12 tests : verts. Les autres suites de `:core` n'ont PAS été lancées (aucun fichier existant du dépôt n'a changé).

## 4. Ce qui n'a pas pu tourner
- `gradle --offline :core:test` dans `android/` : impossible (plugin Android 8.9.1 introuvable hors ligne). Le build officiel (`:receiver:assembleDebug`, `:sender:assembleDebug`) n'a pas été essayé : pas de SDK Android dans l'environnement.
- Aucun test sur appareil, aucune vraie clé exFAT/FAT32/NTFS : le renommage atomique a été testé sur le système de fichiers du conteneur (Linux). Sur exFAT par FUSE ou sdcardfs, le comportement du renommage (atomicité, casse, noms) est à vérifier sur la TV.
- Câblage Android (`AndroidVolumes`, `TvService.readopt`, écran et notification d'adoption, `UsbImport`) : non codé, décrit dans `docs/agent-briefs/usb-espace-par-tv.md`.
- Les tests de liens symboliques s'ignorent eux-mêmes si le système de fichiers ne sait pas en créer ; ils ont tourné ici (Linux).

## 5. Limites connues du cœur
- Entre la vérification de la destination et le renommage, un fichier créé par un autre processus pourrait être écrasé (`rename` POSIX remplace) : la destination n'est écrite que par la TV propriétaire, mais à auditer.
- La revendication est un fichier créé en exclusivité puis rempli : une coupure entre les deux laisse un fichier vide ; il est alors repris seulement après 24 h (date du fichier).
- Une TV sans horloge fiable fausse la péremption de la revendication (sans perte de données).
- 12 hex = 48 bits : collision d'identifiants négligeable pour quelques TV, non nulle en théorie.
- L'espace d'une TV dont le code d'appareil changerait (matériel modifié) serait traité comme celui d'une autre TV : ses données restent intactes mais ne sont plus visibles. À trancher : proposition de « récupérer l'espace de l'ancienne identité » ?
- `Meta.SUFFIX` et `.part` sont déplacés avec leurs fichiers (même dossier) ; l'index `index/castbridge-store.json` est déplacé aussi, puis réécrit à la réadoption.

## 6. Audit à faire faire ensuite : liste exacte
**Perte de données**
1. Aucun chemin de code ne supprime un fichier d'utilisateur : `UsbSpace.kt` n'appelle `delete` que pour `.adoption` (la sienne ou une périmée) et pour des dossiers VIDES (`File.delete` sur dossier, jamais `deleteRecursively`). Vérifier par `rg "delete" UsbSpace.kt`.
2. Aucune copie de repli : `Files.move(ATOMIC_MOVE)` seul ; `AtomicMoveNotSupportedException` laisse le fichier en place.
3. Le plan et l'exécution ne touchent que `Bibliotheque`, `Telechargements`, `Medias`, `index` à la racine de `CastBridge/` : jamais `activation`, jamais un autre dossier, jamais un espace.
4. Écrasement : fenêtre vérification/renommage (§ 5) ; cas d'un système de fichiers où `ATOMIC_MOVE` remplace sans erreur.
5. Casse (exFAT/FAT/NTFS) : deux sources ne différant que par la casse ; une destination existante dont la casse diffère.
6. Noms Unicode (normalisation NFC/NFD) : deux noms visuellement identiques mais différents en octets.

**Adoption**
7. « Annuler » préselectionné et aucun appel de `run(confirmed = true)` sans action de l'utilisateur (à vérifier dans le câblage Android).
8. Reprise à chaque étape : relire la liste des points d'injection (`claim`, `mark`, `move:`, `mkdir:`, `cleanup`, `release`) et chercher un état intermédiaire non couvert ; en particulier une coupure PENDANT `Files.move` (le test coupe avant, pas pendant : l'atomicité dépend du système de fichiers).
9. État final : plus de données à plat, sauf conflits, exécutables, liens et noms invalides, tous rapportés à l'utilisateur ; l'utilisateur est-il informé des fichiers laissés ?
10. Clés très grandes (plus de 100 000 entrées) : `plan` refuse (`FAILED`, « too many entries ») ; est-ce acceptable ?
11. Lecture seule détectée à tort (Android qui monte la clé en lecture seule après arrachage) : message et reprise.

**Concurrence entre deux TV**
12. Deux TV qui adoptent en même temps : seule celle qui crée `.adoption` en exclusivité avance ; vérifier l'atomicité de `Files.createFile` sur exFAT/FUSE.
13. Revendication périmée (24 h) reprise par une autre TV alors que la première adopte encore (clé lente, très gros volume) : deux adoptions simultanées possibles ; faut-il renouveler la date pendant l'exécution ?
14. Deux TV qui écrivent normalement dans leurs espaces : vérifier qu'aucun code Android ne reconstruit `CastBridge/Bibliotheque` à plat (`rg "rootOf|libraryOf"`).
15. `foreign` ne lit que la marque `.espace` des autres espaces ; vérifier qu'aucun autre code (réadoption `UsbReadopt.scan`) ne descend dans un espace étranger si on le lance sur `CastBridge/` au lieu de l'espace.
16. Identifiant : confirmer que deux TV ne partagent pas un code d'appareil (clones) et que `ActivationCenter.deviceCode` est prêt avant le premier `ensure`.

**Clé retirée**
17. Retrait en cours de `ensure` (marque écrite, sous-dossiers absents) : l'appel suivant complète ; vérifié par construction, pas par test de coupure dédié.
18. Retrait pendant le déplacement : `alive()` est testé avant chaque fichier, pas pendant ; le renommage échoue alors avec `IOException` → `INTERRUPTED`.
19. Remise de la clé : la réadoption reprend, l'adoption relancée termine ; l'écran propose-t-il de reprendre ?

## 7. Prochaines étapes
1. Le propriétaire confirme ou corrige les hypothèses du § 1.
2. Un agent avec le SDK Android réalise le cahier `docs/agent-briefs/usb-espace-par-tv.md` et compile `:receiver`.
3. Essai sur la TV de référence avec une clé : deux TV, une clé ; clé ancienne à plat ; clé retirée pendant l'adoption.
4. Audit selon le § 6, puis seulement ensuite une version.
