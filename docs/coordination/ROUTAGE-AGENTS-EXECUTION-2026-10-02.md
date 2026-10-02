# Routage des agents d'exécution : modèle le moins coûteux compétent, meilleur livrable (Fable, 2026-10-02)

> Demande du propriétaire : « choisis et instruis les agents d'exécution pour une consommation optimale de tokens et la production du meilleur livrable possible ; le choix s'appuie sur le moins coûteux des modèles compétents pour la tâche, en allant jusqu'aux versions antérieures ».
> Document d'orchestration (lecture seule sur le code ; rien n'a été lancé, compilé, commité ni poussé). Table lisible par machine : `docs/agent-briefs/routing.json` ; outils : `tools/agents/dispatch-plan.py`, `tools/agents/gradle-lock.sh` ; gabarits de prompt : `docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md`. Chaque cahier porte désormais un en-tête de 3 lignes « Modèle · Groupe · Jauge » (seul l'en-tête a été ajouté ; le contenu technique est intact).

## 0. Ce qui est disponible, et ce que « versions antérieures » veut dire ici

| Modèle (outil `Agent`, paramètre `model`) | Prix API (entrée / sortie, $ par million de jetons ; lecture de cache ≈ 0,1 × entrée) | Rôle dans ce projet |
|---|---|---|
| `haiku` = Claude Haiku 4.5 | 1 / 5 | **plancher** : tâches mécaniques et bornées (fichiers nommés, éditions avant/après, scripts, docs cadrées, mesures) |
| `sonnet` = Claude Sonnet 5.5 | 2 / 10 | implémentation non triviale avec tests, intégration entre modules, UX, rédaction exigeante |
| `opus` = Claude Opus 5.5 | 4 / 20 | **audits indépendants** seulement (diff + cahier), jamais exécutant de première intention |
| `fable` = Claude Fable 5.1 | 10 / 50 | conception et orchestration seulement (règle du propriétaire) ; ne figure dans aucune ligne de routage |

**Les générations antérieures (Sonnet 4.6, Haiku 3.5, etc.) ne sont pas sélectionnables dans ce harnais** : l'outil `Agent` n'accepte que `haiku`, `sonnet`, `opus`, `fable`. Haiku 4.5 est donc le plancher. On obtient « moins cher qu'Haiku » autrement, et c'est ce que ce document organise :

1. **Rétrécir le contexte** : un cahier Haiku ne lit que ses fichiers possédés et les sections de conception nommées (jamais le dépôt entier). Diviser par deux le contexte vaut une génération de modèle en moins.
2. **Rétrécir le périmètre** : découper les cahiers L en deux (§ 2.3), chaque moitié tenant dans une session courte sans compaction.
3. **Remplacer le modèle par un script** : tout ce qui est répétitif et déterministe (relecture d'ordres, rejeu de vecteurs, génération de tables de routes, sommes de contrôle, vérification de manifestes, listes de contrôle) est un script Python/bash écrit une fois, et non une boucle d'agent. `tools/agents/dispatch-plan.py` en est l'exemple : il remplace un agent « quels cahiers maintenant ? ».
4. **Baisser l'effort** : pour un exécutant Sonnet sur une tâche de routine, l'effort `low`/`medium` (quand le harnais le permet) coûte moins que le passage à un autre modèle et garde la même génération.

## 1. Grille de routage (critères mesurables)

Noter chaque cahier de 0 à 2 sur huit critères ; **chaque note est vérifiable à la lecture du cahier et de la matrice de propriété**, pas à l'intuition.

| # | Critère | 0 | 1 | 2 |
|---|---|---|---|---|
| A | **Ambiguïté** du cahier | étapes « avant/après » exactes, fichiers et commandes nommés | objectif clair, moyens à choisir | objectif à interpréter, décisions de conception à prendre |
| B | **Rayon d'action** (blast radius) | ≤ 3 fichiers, aucun appelant à mettre à jour | 4-10 fichiers ou un module | > 10 fichiers, plusieurs modules, API partagée |
| C | **Sensibilité** sécurité / crypto / argent / licence | aucune (docs, CI, UI pure) | effleure (lecture d'un format signé, flag de build) | cœur : clés, signatures, portes de licence, grand livre, serveur |
| D | **Couplage** entre fichiers | fichiers neufs ou indépendants | un contrat avec un autre cahier en parallèle | refactor d'un fichier partagé (TvService, ActivationCenter, ReceiverServer) |
| E | **Jugement** vs édition mécanique | mécanique (copier, renommer, lister, mesurer) | choix locaux (nommage, messages FR, cas limites listés) | conception (faux positifs, concurrence, UX à 3 m, textes juridiques) |
| F | **Boucle de test hors ligne** | commande de porte verte/rouge en < 2 min (unittest, `:core:test --tests`) | compilation seule (`compileDebugKotlin`) | matériel ou propriétaire requis (TV, téléphone, serveur) |
| G | **Contexte à charger** | < 20 k jetons (cahier + 3 fichiers) | 20-60 k (cahier + § de conception + 10 fichiers) | > 60 k (conception entière + module) |
| H | **Réversibilité** | fichiers neufs ou docs : un `git checkout` suffit | modifie du code existant couvert par des tests | migration, format de fichier persistant, protocole, base |

**Décision** (somme S = A+…+H, et deux gardes absolues) :

- **haiku** si S ≤ 5 **et** C = 0 **et** E = 0 **et** aucun fichier de la liste sensible (§ 4.4) n'est possédé. Le cahier doit alors être réécrit en forme **mécanique** (§ 4.7) : si cette réécriture est impossible, ce n'est pas un cahier Haiku.
- **sonnet** sinon (tout cahier avec C ≥ 1 ou E ≥ 1 ou S ≥ 6).
- **opus** n'exécute jamais en première intention ; il **audite** (§ 5) tout diff où C = 2, et tout diff Sonnet escaladé.
- **fable** : jamais exécutant.

**Escalade** : un Haiku qui échoue deux fois à sa porte d'acceptation, ou qui doit toucher un fichier hors de sa zone, ou un fichier sensible → le cahier est relancé en **sonnet** avec le rapport du Haiku joint (ne pas laisser Haiku insister). Un Sonnet qui échoue à sa porte après deux itérations, ou dont le diff touche C = 2 → **audit opus** avant fusion ; si l'audit demande une reprise de conception → retour à Fable (conception), pas un troisième exécutant.
**Désescalade** : un cahier Sonnet dont le rapport montre qu'il n'a fait que des éditions mécaniques (diff ≤ 60 lignes, aucun test ajouté, aucun choix noté) est **reclassé haiku** pour ses frères de la même famille (docs, CI, vecteurs) ; un audit Opus « RAS » deux fois de suite sur une famille (par ex. miroirs Java/Python de vecteurs) lève l'audit obligatoire pour cette famille, l'audit devenant un échantillon (1 sur 3).

Application de la grille aux 129 cahiers présents (W1-W6 : 107, PROTECT : 9, W8a : 9 déjà écrits sur 21 annoncés, hors vagues : 4) : **18 haiku, 103 sonnet, 8 non lançables** (faits, fusionnés, remplacés ou déjà lancés), **38 diffs à auditer**, regroupés en 19 sessions d'audit (§ 5). Les 18 Haiku sont tous des cahiers S (docs, CI, scripts, vecteurs, ressources, campagnes) ; aucun cahier avec C ≥ 1 n'est en Haiku.

## 2. Table de routage par cahier

Légende : **Jauge** = jetons effectifs cumulés estimés sur toute la session (entrée, cache compris / sortie) ; hypothèses § 2.2. **Groupe** = sous-vague ; les cahiers d'un même groupe ont des fichiers disjoints (matrices des index) et tournent en parallèle dans la limite de § 6. **Porte** = commande la plus étroite qui doit être verte avant la suite complète ; les critères complets restent ceux du cahier. **Audit** = audit Opus indépendant avant fusion. Un cahier barré (**FAIT**, **FUSIONNÉ**, **REMPLACÉ**, **LANCÉ**) n'est pas à lancer.

### W1

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w1-01 | sonnet | manifestes + défaut super-admin : fichier sensible, cahier exact | 150 k / 8 k | W1-A | manifestes TV/tél., res/xml, ownerlib/build.gradle.kts, tools/tests/test_backup_rules.py | `python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'` | oui |
| w1-02 | sonnet | écritures atomiques dans 5 magasins : couplage, tests cœur | 150 k / 8 k | W1-A | C/lots/{RentalVault,TvLotStore,DeliveryQueue}, C/learn/Progress, C/tv/Storage, CT/lots/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Rental*'` | non |
| w1-03 | sonnet | sécurité de l'API TV (PIN, Host) : jugement | 150 k / 8 k | W1-A | C/tv/{ReceiverServer,Security}, R/UpdateInstaller, CT/{TvHardeningTest,SecurityTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TvHardening*'` | oui |
| w1-04 | sonnet | aria2/DHT, sonde réseau : zone netTick délicate | 150 k / 8 k | W1-A | R/TvNetDiag, R/TvService (netTick), R/TvPrefs, C/dl/Aria2Config, CT/dl/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Aria2*'` | non |
| w1-05 | sonnet | horloge, grâce, TvGate : sécurité de licence | 400 k / 20 k | W1-A | C/owner/{Keys,FeatureGate(FleetMigration),Activation(clockDoubt)}, R/{ActivationCenter,PolicyHub}, receiver/build.gradle.kts (grâce) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Clock*'` | oui |
| w1-06 | sonnet | table des routes dérivée du code : lecture transversale | 150 k / 8 k | W1-A | C/owner/TrialPolicy, CT/owner/TrialRoutesTest, tools/routes/*, tools/tests/test_routes.py | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TrialRoutes*'` | non |
| w1-07 | haiku | YAML CI + requirements : mécanique, vérifiable hors ligne | 60 k / 5 k | W1-A | .github/workflows/*.yml, tools/requirements-dev.txt, docs/COORDINATION.md (§ CI) | `python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]" && python3 -m unittest discover -s tools/tests -p 'test_*.py'` | non |
| w1-08 | haiku | doc + .gitignore + script shasum : mécanique | 60 k / 5 k | W1-A | docs/RELEASES.md, .gitignore, tools/release/sha256sums.sh, version.properties (commentaire) | `bash -n tools/release/sha256sums.sh && git check-ignore -q secrets/x.jks` | non |
| w1-09 | sonnet | tests instables : diagnostic de concurrence, jugement | 150 k / 8 k | W1-A | android/sshd/**, C/ssh/SshPolicy, C/trust/TrustRegistry, C/chess/ChessTransport, CT/{TrustTest,ChessRelayTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :sshd:test :core:test --tests '*Trust*' --tests '*ChessRelay*'` | non |
| w1-10 | sonnet | rejeu de vecteurs en Java/Python : crypto, deux langages | 150 k / 8 k | W1-A | tools/activation/verify_vectors.py, B/licenses/{WireActivation,EnvelopeVerifier}, BT/licenses/RentalVectorsTest | `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=RentalVectorsTest` | non |
| w1-11 | sonnet | serveur : données personnelles, Flyway, purge : jugement | 150 k / 8 k | W1-A | B/devices/**, B/telemetry/**, B/lots/BundleCatalogController, R/TvConnect (deviceName), C/connect/*, migration README | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Device*Test,Telemetry*Test'` | non |
| w1-12 — **FAIT** | sonnet | déjà livré | 150 k / 8 k | — | ops/monitoring/**, backend/backup.sh, backend/README.md | `—` | non |
| w1-13 — **REMPLACÉ** | haiku | remplacé | 60 k / 5 k | — | docs/RENTAL-LOTS.md, docs/TRIAL-EDITION.md § 9 | `—` | non |

### W2

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w2-01 | sonnet | révocation persistée, canaux, clé de secours : sécurité | 400 k / 20 k | W2-A | R/{ActivationCenter,OwnerBtHost}, C/owner/{Activation,Envelope,License,OwnerFrames,FeatureGate(ActivationReceiver)}, R/RentalHub (ActivationInstallApi), CT/owner/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Revocation*'` | oui |
| w2-02 | sonnet | SSH en release : surface d'attaque, drapeau Gradle | 400 k / 20 k | W2-A | android/sshd/**, R/SshControl, receiver/build.gradle.kts (castbridge.sshShell), docs/ADMIN.md § SSH | `cd android && tools/agents/gradle-lock.sh gradle --offline :sshd:test` | oui |
| w2-03 | sonnet | textes de refus actionnables, écrans TV/tél. : UX, jugement | 400 k / 20 k | W2-B | C/owner/FeatureGate (LockedTexts), C/owner/RejectionTexts, R/Activation*, S/{ActivateTvActivity,MainActivity}, build.gradle.kts (OWNER_CONTACT) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LicenseAndGate*'` | non |
| w2-04 | sonnet | écran fin de clé, rappels, badge accessible : UI TV | 400 k / 20 k | W2-A | C/owner/KeyBadge, R/{KeyBadgeOverlay,PlayerActivity,HomeScreen}, CT/owner/KeyBadgeTest, res/values/cb_colors.xml | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*KeyBadge*'` | non |
| w2-05 | sonnet | UX Apprendre à 3 m, threads/handlers : couplage UI | 400 k / 20 k | W2-A | R/Learn{Activity,Views,Reader,Hub}, C/learn/BaseContent (chaînes), C/lots/{RentalEngine,RentalSweeper}, R/RentalHub (hors ActivationInstallApi) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Learn*'` | non |
| w2-06 — **REMPLACÉ** | sonnet | remplacé | 800 k / 40 k | — | S/shop/** | `—` | non |
| w2-07 | sonnet | découpage de TvService : refactor à large rayon | 400 k / 20 k | W2-A | R/TvService, R/{TvNetMonitor,TvExtraRoutes,ScreenLauncher,StorageEvents,IconSync} | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w2-08 | sonnet | threads bruts → exécuteur : concurrence, 8 fichiers | 150 k / 8 k | W2-A | R/TvExecutors, R/{Chess,Sudoku}Activity, R/ParentalUi, R/{LibraryScreen,TvCards,DownloadsActivity} | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w2-09 | sonnet | diagnostic réseau en français, écran unique : jugement UX | 400 k / 20 k | W2-A | S/{TvHome,UploadService,TvPairScreen,MyTvActivity,BtRoutesScreen,TroubleshootScreen}, C/tv/TvReachability, CT/tv/TvReachabilityTest | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TvReachability*'` | non |
| w2-10 — **REMPLACÉ** | sonnet | remplacé | 800 k / 40 k | — | B/shop/** | `—` | non |
| w2-11 | haiku | badge + accesseur + focus : 3 fichiers, édition bornée | 60 k / 5 k | W2-A | R/{LanguesActivity,LanguesHub}, C/langues/LangPack (synthetic), docs/LANGUES.md | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Lang*' :receiver:compileDebugKotlin` | non |
| w2-12 | sonnet | validateur Kotlin + Python miroir : règles, jugement | 400 k / 20 k | W2-A | C/learn/{LessonValidator,LessonModel,LearnTool}, tools/content-validation/{cbvalidate,test_cbvalidate}.py, CT/LearnContentTest | `python3 -m unittest discover -s tools/content-validation -p 'test_cbvalidate.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LearnContent*'` | non |
| w2-13 | sonnet | registre de versions, manifeste, script strict : plusieurs outils | 400 k / 20 k | W2-A | tools/trial-edition/**, tools/publish-content.sh, tools/content-lots/*, tools/tests/test_content_tools.py, content/{TRIAL-MANIFEST,LOT-VERSIONS}.json | `python3 -m unittest discover -s tools/tests -p 'test_content_tools.py'` | non |
| w2-14 | sonnet | code mort, JSON unique, drawables : jugement, 15+ fichiers | 400 k / 20 k | W2-A | C/trust/ResilientCall, C/learn/LearnQuiz, C/{net,quiz,dl}/Json*, C/tv/{Library,TvClient}, S/{TvLibraryScreen,StoragePanel,TvPlayerSettings}, drawables | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test` | non |
| w2-15 | sonnet | tunnel, portée EXPERTS, notAfter : sécurité serveur/cœur | 150 k / 8 k | W2-B | B/tunnel/**, C/tunnel/ExpertsList, C/owner/Keys (EXPERTS), DK/ExpertsStore, CT/tunnel/**, BT/tunnel/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Tunnel*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Tunnel*Test'` | oui |

### W3

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w3-01 | sonnet | RouteTable + découpage ReceiverServer : refactor profond | 800 k / 40 k | W3-A | C/tv/{ReceiverServer,RouteTable,UploadHandler,StorageApi,StreamHandler}, R/TvExtraRoutes, CT/tv/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Tv*'` | non |
| w3-02 | sonnet | démarrage hors fil principal, FGS API 34 : Android fin | 400 k / 20 k | W3-B | R/{ActivationCenter,TvService,TvApp,PlayerActivity(onCreate)}, receiver/AndroidManifest.xml | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w3-03 | sonnet | catalogue signé à la TV, cap 3 Mo : lots, tests | 400 k / 20 k | W3-A | C/lots/{OwnedLots,RentalApi,TvLotStore,RentalDelivery}, R/RentalHub, CT/lots/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Lots*'` | non |
| w3-04 | sonnet | export CSV, rapports QA, import des décisions : scripts | 400 k / 20 k | W3-A | content/validation/**, content/qa/**, tools/content-validation/review_export.py, tools/pedagogy-report/** | `python3 -m unittest discover -s tools/content-validation -p 'test_*.py'` | non |
| w3-05 | sonnet | 18 unités + validateur : contenu pédagogique, jugement | 800 k / 40 k | W3-C | content/langues/<lang>-a0/a1-*, tools/langues/{languelib,test_languelib}.py, C/langues/LangValidator, lots.json, embedded.txt | `python3 -m unittest discover -s tools/langues -p 'test_languelib.py'` | non |
| w3-06 | sonnet | format programmes + porte 70 % : règles, jugement | 400 k / 20 k | W3-C | docs/curriculum/programmes/**, tools/content-validation/cbvalidate.py (programme), test_cbvalidate.py | `python3 -m unittest discover -s tools/content-validation -p 'test_cbvalidate.py'` | non |
| w3-07 | sonnet | rédaction de fiches lycée : qualité pédagogique | 800 k / 40 k | W3-A | content/learn/2nde-*, 1ere-*, tle-* (listés), content/learn/lots.json | `python3 tools/content-validation/cbvalidate.py content/learn --lot <lot>` | non |
| w3-08 | sonnet | fiches primaire anglophone + droit : qualité, avertissements | 800 k / 40 k | W3-A | content/learn/class1-5-*, droit-l1/l2-*, scopes.txt, lots.json | `python3 tools/content-validation/cbvalidate.py content/learn --lot <lot>` | non |
| w3-09 | sonnet | relais de révocations tél.→TV + abus serveur : sécurité | 400 k / 20 k | W3-A | S/RevocationRelay, C/connect/ServerLink, B/licenses/{PublicLicenseController,AbuseService}, BT/licenses/**, CT/connect/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ServerLink*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Abuse*Test,PublicLicense*Test'` | oui |
| w3-10 | haiku | procédure écrite, aucune commande : rédaction cadrée | 60 k / 5 k | W3-C | docs/RELEASES.md § signature, tools/release/migrate-signing.md, docs/HANDOFF.md § 6 | `grep -c 'keystore' docs/RELEASES.md` | non |
| w3-11 | sonnet | extraction vers cœur + tests + écrans Quiz : refactor | 400 k / 20 k | W3-A | R/{PlayerActivity,QuizActivity}, R/QuizScreens/*, C/tv/HomeTools, C/quiz/QuizText, CT/tv/HomeToolsTest, CT/QuizTextTest, R/ParentalHub (labels) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*HomeTools*'` | non |
| w3-12 | sonnet | ProGuard -keep, libvlc, mesure APK : build délicat | 400 k / 20 k | W3-A | receiver/build.gradle.kts, receiver/proguard-rules.pro, sshd/build.gradle.kts, docs/RELEASES.md § taille | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:assembleRelease -PrequireActivation=true` | non |
| w3-13 | sonnet | brouillons CGV/consentement/divulgation : rédaction exigeante | 400 k / 20 k | W3-C | docs/legal/{CGV-location,DIVULGATION-tunnel,CONSENTEMENT-parent,CREDITS-licences}.md, docs/TELEMETRY.md § 7 | `ls docs/legal/*.md && grep -L 'pas un avis juridique' docs/legal/*.md | wc -l` | non |
| w3-14 | sonnet | liste de contrôle 40 étapes + scripts : synthèse transversale | 400 k / 20 k | W3-B | docs/TEST-CAMPAIGN.md, tools/rental-test/**, tools/device/**, docs/HANDOFF.md § 9 | `bash -n tools/device/*.sh && python3 -m unittest discover -s tools/rental-test -p 'test_*.py'` | non |

### W4a

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w4-01 | sonnet | X25519 pur Kotlin, boîte v2 : crypto, vecteurs RFC 7748 | 400 k / 20 k | W4a-1 | C/owner/X25519, C/crypto/SecretWrapper, C/lots/{InstallKey,RentalVectorsV2,RentalKeys,RentalLedger}, C/owner/{OwnerFrames,LicensedIssuer,LotKeys}, tools/activation/rental-vectors-v2.json, CT/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*X25519*' --tests '*InstallKey*' --tests '*RentalVectorsV2*'` | oui |
| w4-02 | sonnet | console tél., bureau, OwnerCli : 3 outils, UI | 400 k / 20 k | W4a-2 | OL/ConsoleActivity, C/owner/{OwnerCli,PhoneConsole,ProductionForm}, DK/{Cli,Gui,GuiRights,Desk,Vectors}, tests desktop, CT/owner/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnerCli*' && cd ../tools/activation-desktop && tools/agents/gradle-lock.sh gradle --offline test` | non |
| w4-03 | sonnet | Keystore TV, clé d'installation, ouverture v2 : sécurité | 400 k / 20 k | W4a-2 | R/KeystoreWrapper, R/{ActivationCenter,RentalHub,TvService(/api/activation)}, core resources admin.html | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | oui |
| w4-04 | sonnet | lots chiffrés au repos, lecture mémoire : crypto + couplage | 800 k / 40 k | W4a-3 | C/lots/{LotCrypt,LotApi,TvLotStore,RentalApi,LotAdapters}, C/learn/{LearnLotConsumer,LearnLots}, C/langues/LangLotConsumer, C/quiz/QuizLots, R/{Lots,Learn,Langues}Hub, CT/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LotCrypt*' --tests '*Lots*'` | oui |
| w4-05 | sonnet | XDH Java + cryptography Python : miroirs crypto | 400 k / 20 k | W4a-2 | B/licenses/{DeviceIdentity,WireActivation,RentalBoxV2}, BT/licenses/*, tools/activation/verify_vectors.py, tools/tests/test_verify_vectors.py | `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='RentalVectorsV2Test,DeviceIdentityTest'` | oui |
| w4-06 | haiku | 8 docs à mettre à jour depuis les rapports : mécanique | 60 k / 5 k | W4a-4 | docs/{RENTAL-LOTS,ACTIVATION-FORMAT,TRIAL-EDITION,OWNER-CONSOLE,ACTIVATION-TOOLS,LICENSE-ADMIN,ADMIN,HANDOFF}.md | `grep -l 'v2:' docs/RENTAL-LOTS.md docs/ACTIVATION-FORMAT.md | wc -l` | non |

### W4b

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w4-07 | sonnet | TvGate/DegradedPolicy, droits durables : cœur de licence | 400 k / 20 k | W4b-1 | C/owner/{Activation(TvAccess,TvGate),FeatureGate,DegradedPolicy,KeyBadge,KeyStatusJson,TrialPolicy}, CT/owner/*, tools/routes/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Degraded*' --tests '*LicenseAndGate*'` | oui |
| w4-08 | sonnet | garde de routes, 23 sites relus, écran renouvellement : UI | 400 k / 20 k | W4b-2 | R/{ActivationCenter,TvService,PlayerActivity,HomeScreen,ActivationActivity,BtServer,UsbImporter,SshControl,Games,ParentalHub,LanguesActivity,KeyBadgeOverlay}, admin.html | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w4-09 | sonnet | OwnedLots en mode réduit, 403 : petit mais lié aux droits | 150 k / 8 k | W4b-2 | C/lots/{OwnedLots,EditionPolicy,RentalApi,RentalSweeper}, CT/lots/*, docs/TEST-CAMPAIGN.md | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnedLots*'` | non |
| w4-10 | haiku | 5 docs + clause CGV : édition documentaire | 60 k / 5 k | W4b-3 | docs/{ACTIVATION-FORMAT,TRIAL-EDITION,OWNER-CONSOLE,ADMIN,HANDOFF}.md, docs/legal/CGV-location.md | `grep -c 'mode réduit' docs/TRIAL-EDITION.md` | non |

### W4c

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w4-11 | sonnet | délégation signée, tickets, journal : crypto + argent | 800 k / 40 k | W4c-1 | C/owner/{Keys,License,Delegation,DelegatedVerifier,TicketedActivation,AgentVectors}, C/sales/{PriceGrid,SalesLedger,Receipt}, tools/activation/agent-vectors.json, CT/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Delegation*' --tests '*Ticketed*' --tests '*Sales*'` | oui |
| w4-12 | sonnet | onglet console, CLI bureau, sign_prices.py : 3 outils | 400 k / 20 k | W4c-2 | OL/{ConsoleActivity,OwnerStore,DelegationsTab}, C/owner/{OwnerCli,PhoneConsole}, DK/*, tools/prices/*, content/prices.json, CT/owner/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnerCli*' && python3 -m unittest discover -s tools/tests -p 'test_sign_prices.py'` | non |
| w4-13 | sonnet | mode point focal du téléphone : écrans + SaleFlow | 800 k / 40 k | W4c-2 | S/focal/**, C/sales/SaleFlow, CT/sales/SaleFlowTest, S/MainActivity, sender/build.gradle.kts, S/RentalDeliveryActivity | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SaleFlow*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | non |
| w4-14 | sonnet | journal à ajout seul + synchronisation : fsync, formats | 400 k / 20 k | W4c-2 | C/sales/{LedgerFile,LedgerSync}, S/focal/{LedgerFile,LedgerSyncJob,LedgerScreen,ReceiptShare}, CT/sales/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Ledger*'` | non |
| w4-15 | sonnet | TV accepte délégation|activation, révocation : sécurité | 400 k / 20 k | W4c-2 | R/{ActivationCenter,DelegationStore,ActivationActivity,OwnerBtHost}, R/RentalHub (ActivationInstallApi) | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin :core:test --tests '*Delegat*'` | oui |
| w4-16 | sonnet | tables agents, sync, anomalies, admin TOTP : serveur, argent | 800 k / 40 k | W4c-3 | B/agents/**, migration V6x__agents.sql, templates admin/agents*, application.yml (agents), B/licenses/{LicenseKeyring,RegistryStore} (additif), BT/agents/** | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Agent*Test,LicenseKeyringTest'` | oui |
| w4-17 | sonnet | miroirs Java/Python des formats d'agent : deux langages | 400 k / 20 k | W4c-2 | B/licenses/{Delegation,TicketedActivation,AgentLedgerEntry,PriceGrid,ReceiptCode}, BT/licenses/AgentVectorsTest, EnvelopeVerifier (additif), verify_vectors.py | `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=AgentVectorsTest` | non |
| w4-18 | haiku | docs + fiche terrain depuis les rapports : rédaction cadrée | 150 k / 10 k | W4c-4 | docs/{VENTE-TERRAIN,FICHE-POINT-FOCAL}.md, docs/{ACTIVATION-FORMAT,RENTAL-LOTS,OWNER-CONSOLE,ACTIVATION-TOOLS,LICENSE-ADMIN,API-SERVER,TELEMETRY,HANDOFF}.md | `ls docs/VENTE-TERRAIN.md docs/FICHE-POINT-FOCAL.md` | non |

### W5a

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w5-01 | sonnet | 10 classes de formats + vecteurs : conception de formats | 800 k / 40 k | W5a-1 | C/shop/**, CT/shop/**, tools/activation/shop-vectors.json, C/sales/{PriceGrid,Receipt}, CT/sales/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Shop*'` | non |
| w5-02 | sonnet | porte-jetons chaîné HMAC, enveloppe tokens : argent, crypto | 800 k / 40 k | W5a-1 | C/tokens/**, CT/tokens/**, tools/activation/tokens-vectors.json | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Token*'` | oui |
| w5-03 | sonnet | commodités du Quiz, points de défi : logique de jeu | 400 k / 20 k | W5a-1 | C/quiz/{QuizGame,QuizRoom,Wallet,QuizHttp,QuizBoosts}, CT/quiz/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Quiz*'` | non |
| w5-04 | sonnet | Feature.SHOP, portes essai/réduit, émission sans maître : licence | 400 k / 20 k | W5a-1 | C/owner/{TrialPolicy,DegradedPolicy,FeatureGate,LicensedIssuer,ActivationProof}, C/lots/{RentalKeys,RentalDurations}, tools/routes/routes.txt, CT/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Routes*' --tests '*ActivationProof*' --tests '*Rental*'` | oui |
| w5-05 | sonnet | miroirs Java/Python + rejeu de 2 fichiers de vecteurs | 400 k / 20 k | W5a-2 | B/shop/wire/**, BT/shop/wire/ShopVectorsTest, verify_vectors.py, tools/tests/test_verify_vectors.py | `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=ShopVectorsTest` | non |

### W5b

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w5-06 | sonnet | KEK serveur, contrats, boîte v2 XDH, scellement : crypto serveur | 800 k / 40 k | W5b-1 | B/shop/{ShopProperties,ShopFeature}, B/shop/rental/**, V6x__shop.sql, BT/shop/rental/**, ActivationService (1 méthode), application.yml (shop), backend/README.md | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Rental*Test'` | oui |
| w5-07 | sonnet | commandes, paiements, bons, reçus : argent, états | 800 k / 40 k | W5b-2 | B/shop/order/**, B/shop/agent/**, BT/shop/order/**, BT/shop/agent/** | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Order*Test,Voucher*Test,Receipt*Test'` | oui |
| w5-08 | sonnet | grand livre des jetons, bons signés, réconciliation : argent | 800 k / 40 k | W5b-2 | B/shop/tokens/**, BT/shop/tokens/**, EnvelopeIssuer (méthode additive) | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Token*Test'` | oui |
| w5-09 | sonnet | console admin boutique, TOTP, CSV : pages + contrôleurs | 400 k / 20 k | W5b-3 | B/shop/admin/**, B/shop/ShopAnomalies, TPL/shop*.html, BT/shop/admin/**, TPL/lic-nav.html (1 lien) | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='ShopAdmin*Test'` | non |
| w5-10 | haiku | 4 champs additifs cœur + serveur : mécanique | 60 k / 5 k | W5b-3 | C/device/DeviceReport, B/devices/{DeviceReport,DeviceService} (additif), BT/DevicesApiTest | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*DeviceReport*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=DevicesApiTest` | non |

### W5c

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w5-11 | sonnet | onglet Boutique du téléphone : 6 écrans, UX | 800 k / 40 k | W5c-1 | S/shop/{ShopScreen,ShopPayScreen,ShopOrdersScreen,ShopTokensScreen,VoucherEntry,ShopTexts}, S/{MainActivity,LearnScreen,GamesScreen} | `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | non |
| w5-12 | sonnet | passerelle HTTP, livraison activation+lots+bons : couplage, sécurité | 800 k / 40 k | W5c-1 | S/shop/{ShopRuntime,HttpShopApi,TvShopCache,ShopDelivery,ShopSyncJob,PendingVoucherRelay}, C/lots/RentalDelivery, S/{LotsRuntime,RentalDeliveryActivity}, CT/lots/RentalDeliveryTest | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalDelivery*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | oui |
| w5-13 | sonnet | réduction du mode point focal, bons, confirmations : refactor | 400 k / 20 k | W5c-1 | S/focal/**, C/sales/{SaleFlow,LedgerSync}, CT/sales/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SaleFlow*' --tests '*LedgerSync*'` | non |
| w5-14 | sonnet | délégation sans maître, make_vouchers.py, vecteurs régénérés | 400 k / 20 k | W5c-1 | OL/*, C/owner/{Delegation,OwnerCli,PhoneConsole}, DK/*, tools/prices/**, tools/vouchers/**, agent-vectors.json, CT/owner/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Delegation*' && python3 -m unittest discover -s tools/tests -p 'test_make_vouchers.py'` | non |

### W5d

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w5-15 | sonnet | écran Boutique D-pad, clavier de bon : UI TV à 3 m | 800 k / 40 k | W5d-1 | R/shop/{ShopActivity,ShopViews,VoucherKeypad,ShopTexts}, drawable ic_t_shop, R/{PlayerActivity,HomeScreen}, receiver/AndroidManifest.xml | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w5-16 | sonnet | routes /api/shop, client en ligne, installeur : sécurité | 800 k / 40 k | W5d-1 | R/shop/{ShopHub,ShopStore,ShopClient,RentalOnlineInstaller}, R/{TvService,RentalHub,LotsHub}, C/lots/RentalApi, CT/lots/RentalApiTest, C/telemetry/Telemetry, B/telemetry/EventCatalog | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalApi*' && cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | oui |
| w5-17 | sonnet | TokenHub, clé HMAC dérivée, écrans Quiz : argent, UI | 800 k / 40 k | W5d-1 | R/tokens/{TokenHub,WalletKeys}, R/{QuizActivity,QuizHub,Games,QuizViews}, R/QuizScreens/** | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | oui |
| w5-18 | sonnet | catégorie Achats, allocation enfant, PIN : parental | 400 k / 20 k | W5d-1 | R/{ParentalHub,ParentalActivity,ParentalUi}, C/parental/{ParentalModel,ParentalEngine,ParentalApi}, CT/Parental*Test, S/ParentalScreen | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Parental*'` | non |
| w5-19 | haiku | acceptation CGV + magasin de reçus : stockage simple | 60 k / 5 k | W5d-2 | C/shop/{ShopTerms,ReceiptStore}, R/shop/ShopTermsView, S/shop/ShopTermsScreen, CT/shop/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ShopTerms*' --tests '*ReceiptStore*'` | non |

### W5e

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w5-20 | sonnet | SHOP.md, TOKENS.md + 14 docs : synthèse des rapports | 400 k / 20 k | W5e-1 | docs/{SHOP,TOKENS}.md + 14 docs listés | `ls docs/SHOP.md docs/TOKENS.md` | non |
| w5-21 | sonnet | 4 brouillons juridiques boutique/jetons/mineurs : rédaction | 400 k / 20 k | W5e-1 | docs/legal/{CGV-boutique,BON-DE-RECHARGE,REGISTRE-TRAITEMENTS-boutique,JETONS-MINEURS-INDICATEURS}.md | `grep -L 'pas un avis juridique' docs/legal/*.md | wc -l` | non |
| w5-22 | sonnet | fumée bout en bout + 40 étapes : scripts contre émulateur | 400 k / 20 k | W5e-1 | tools/shop-test/**, tools/rental-test/**, docs/TEST-CAMPAIGN.md § Boutique | `python3 tools/shop-test/smoke.py --fake` | non |
| w5-23 | haiku | fiche terrain v2 : rédaction cadrée | 60 k / 5 k | W5e-1 | docs/FICHE-POINT-FOCAL.md, docs/VENTE-TERRAIN.md | `grep -c 'bon' docs/FICHE-POINT-FOCAL.md` | non |
| w5-24 | haiku | CI + requirements + check_server_key.sh : mécanique | 60 k / 5 k | W5e-1 | .github/workflows/tools.yml, tools/requirements-dev.txt, tools/release/check_server_key.sh, docs/COORDINATION.md § CI | `bash -n tools/release/check_server_key.sh` | non |

### W6a

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w6-01 | sonnet | porte du téléphone + matrice figée : logique pure, exhaustive | 400 k / 20 k | W6a-1 | C/owner/PhoneGate, CT/owner/{PhoneGateTest,PhoneMatrixTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PhoneGate*' --tests '*PhoneMatrix*'` | non |
| w6-02 | sonnet | machine d'états par TV + catalogue de messages FR | 400 k / 20 k | W6a-2 | C/owner/{PhoneSync,PhoneGateTexts}, CT/owner/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PhoneSync*' --tests '*PhoneGateTexts*'` | non |
| w6-03 | sonnet | InstallSigner, preuve au défi, cache 14 j : crypto | 800 k / 40 k | W6a-1 | C/owner/{InstallSigner,TvProof,ProofCache}, CT/owner/*, tools/activation/proof-vectors.json, C/owner/OwnerFrames (2 constantes) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Proof*'` | oui |
| w6-04 | sonnet | session super, 5 échecs, AuditChain : sécurité, petit | 150 k / 8 k | W6a-1 | C/owner/SuperSession, CT/owner/SuperSessionTest | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SuperSession*'` | oui |
| w6-05 | sonnet | collecte toute la TV, journal 800/14 j : modèle de données | 400 k / 20 k | W6a-1 | C/parental/Collect, C/parental/tab/{TvJournal,SessionTracker,TabModel}, CT/parental/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Collect*' --tests '*TvJournal*'` | non |
| w6-06 | sonnet | détenteurs, consentement, confidentialité : vie privée | 400 k / 20 k | W6a-1 | C/parental/{Holders,ParentalPrivacy,ParentalApi,ParentalModel,ParentalEngine,Supervision}, CT/Parental* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ParentalHolders*'` | non |
| w6-07 | sonnet | rapports v2 signés Ed25519+HMAC, CBTP v2 : crypto | 800 k / 40 k | W6a-2 | C/parental/{ReportV2,ParentalReports,ParentalSync}, CT/parental/{ReportV2Test,ReportSyncV2Test} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ReportV2*' --tests '*ReportSyncV2*'` | oui |
| w6-08 | sonnet | agrégation v2, heatmap, tendances : calculs, v1 préservé | 400 k / 20 k | W6a-3 | C/parental/tab/{WholeTvModel,ParentalLedger,ReportAggregator,Heatmap,Trends,Exports,…}, CT/parental/tab/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*WholeTv*' --tests '*Aggregation*'` | non |

### W6b

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w6-09 | sonnet | en-tête X-CB-TV-Proof exigé côté serveur : sécurité | 400 k / 20 k | W6b-1 | B/licenses/{TvProofHeader,TvProof}, BT/licenses/*, contrôleur des lots, B/shop/ShopController (si w5), docs/API-SERVER.md | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='TvProof*Test'` | oui |
| w6-10 | haiku | rejeu Python d'un fichier de vecteurs : mécanique | 60 k / 5 k | W6b-1 | tools/activation/verify_vectors.py, tools/tests/test_verify_vectors.py | `python3 tools/activation/verify_vectors.py && python3 -m unittest discover -s tools/tests -p 'test_verify_vectors.py'` | non |
| w6-11 | haiku | drapeau Gradle, lignes version.properties, règles XML : mécanique | 60 k / 5 k | W6b-1 | sender/build.gradle.kts, version.properties, sender res/xml/*, tools/tests/test_backup_rules.py, docs/RELEASES.md | `python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin -PrequireTvProof=true` | non |

### W6c

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w6-12 | sonnet | route de preuve au défi, trames BT, empreinte : sécurité | 400 k / 20 k | W6c-1 | R/{ActivationCenter,RentalHub,OwnerBtHost,KeystoreWrapper,ActivationActivity}, admin.html, C/owner/OwnerChannel (additif) | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin :core:test --tests '*Proof*'` | oui |
| w6-13 | sonnet | collecte branchée, magasin chiffré, 7 crochets : couplage | 800 k / 40 k | W6c-1 | R/{ParentalHub,ParentalStore,ForegroundWatcher,TvService,UsbImporter,SshControl,TunnelHub,SudokuActivity}, receiver res/xml/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin && python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'` | non |
| w6-14 | sonnet | consentement, indicateur permanent, À propos : UI TV | 400 k / 20 k | W6c-1 | R/{ParentalActivity,ParentalUi,KeyBadgeOverlay,HomeScreen,PlayerActivity,HolderConsentActivity}, manifeste, drawable | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin` | non |
| w6-15 | sonnet | routes détenteur, CBTP v2, routes interdites au tunnel : sécurité | 400 k / 20 k | W6c-1 | C/parental/{HolderHttp,ParentalApi}, CT/parental/HolderHttpTest, R/BtServer, tools/routes/routes.txt, tools/tests/test_routes.py | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*HolderHttp*' && python3 -m unittest discover -s tools/tests -p 'test_routes.py'` | oui |

### W6d

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w6-16 | sonnet | ProofStore, TOFU, mur, cadenas : sécurité + UI | 800 k / 40 k | W6d-1 | S/gate/{ProofStore,ProofSync,PhoneGateRuntime,GateWall,TargetTvChip}, S/{MainActivity,TvLink,Ui,TvPairScreen} | `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | oui |
| w6-17 | sonnet | SendGuard + 25 chemins de données : large rayon | 800 k / 40 k | W6d-1 | C/xfer/SendGuard, CT/xfer/SendGuardTest, S/gate/GateStub, 20+ écrans/services du téléphone | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SendGuard*' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | oui |
| w6-18 | sonnet | onglet Parental v2, 4 vues, tirage Wi-Fi : UI | 800 k / 40 k | W6d-1 | S/Parental{Tab,Inbox,ReportsUi,Charts,Export,Data,WholeTv,Screen,Activity}, S/{ParentalHolders,ParentalPrivacyScreen,ParentalWholeTvV2} | `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin` | non |
| w6-19 | sonnet | session super UI, Keystore, check_no_superadmin.sh : sécurité | 400 k / 20 k | W6d-1 | OL/{SuperAdmin,ConsoleActivity,OwnerStore,SuperSessionStore,PhoneKeystoreWrapper,SuperBanner}, tools/release/check_no_superadmin.sh, tools/tests/test_check_no_superadmin.py, docs/RELEASES.md | `python3 -m unittest discover -s tools/tests -p 'test_check_no_superadmin.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :ownerlib:compileDebugKotlin` | oui |

### W6e

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w6-20 | sonnet | PHONE-GATE.md + 8 docs : synthèse des rapports | 400 k / 20 k | W6e-1 | docs/{PARENTAL,ACTIVATION-FORMAT,OWNER-CONSOLE,TRIAL-EDITION,API-SERVER,TELEMETRY,REMOTE-TUNNEL-TV,HANDOFF,PHONE-GATE}.md | `ls docs/PHONE-GATE.md` | non |
| w6-21 | sonnet | 4 brouillons juridiques parental/foyer : rédaction | 400 k / 20 k | W6e-1 | docs/legal/{INFORMATION-FOYER-CONTROLE-PARENTAL,DIVULGATION-ACCES-DONNEES-UTILISATION,POLITIQUE-CONFIDENTIALITE-complement-W6,REGISTRE-TRAITEMENTS-parental}.md | `grep -L 'pas un avis juridique' docs/legal/*.md | wc -l` | non |
| w6-22 | haiku | ≈ 45 étapes de campagne : rédaction cadrée | 60 k / 5 k | W6e-1 | docs/TEST-CAMPAIGN.md § W6 | `grep -c '^- \[ \]' docs/TEST-CAMPAIGN.md` | non |
| w6-23 | haiku | 3 workflows + COORDINATION § CI : mécanique | 60 k / 5 k | W6e-1 | .github/workflows/{tools,android,release}.yml, docs/COORDINATION.md § CI, tools/requirements-dev.txt | `python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]"` | non |

### PROTECT

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| protect-01 | sonnet | Gradle, placeholders, exceptions dev : build délicat | 400 k / 20 k | P-A | receiver/build.gradle.kts, receiver/AndroidManifest.xml | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:assembleDebug` | non |
| protect-02 | sonnet | classe d'appareil multi-OS, faux positifs : jugement | 400 k / 20 k | P-B | C/tv/DeviceClass + test, R/DeviceClassGate, R/PlayerActivity (1 appel) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*DeviceClass*'` | oui |
| protect-03 | sonnet | épinglage de signature, PackageManager multi-API : sécurité | 400 k / 20 k | P-B | C/owner/ApkIntegrity + test, R/SelfIntegrity, R/ActivationCenter (1 appel) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ApkIntegrity*'` | oui |
| protect-04 | sonnet | heuristiques d'environnement, refus ciblé : faux positifs | 400 k / 20 k | P-B | C/owner/EnvTrust + test, R/TamperSignals, R/RentalHub (refus minimal) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*EnvTrust*'` | oui |
| protect-05 | sonnet | battement de cœur + anomalies serveur : Kotlin + Java | 400 k / 20 k | P-C | B/devices/{DeviceReport,DeviceService}, B/licenses/AbuseService, C/device/DeviceReport | `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Device*Test,Abuse*Test'` | non |
| protect-06 | haiku | 3 fichiers texte/ressources : mécanique | 60 k / 5 k | P-C | LICENSE-NOTICE, receiver assets/NOTICE, receiver res/values/strings.xml | `ls LICENSE-NOTICE android/receiver/src/main/assets/NOTICE && grep -c 'license_notice_short' android/receiver/src/main/res/values/strings.xml` | non |
| protect-07 | haiku | SECURITY.md + ETHICS.md : rédaction cadrée | 60 k / 5 k | P-D | SECURITY.md, ETHICS.md | `ls SECURITY.md ETHICS.md` | non |
| protect-08 | haiku | build + mesure + rapport, aucune source modifiée | 60 k / 5 k | P-E | docs/agent-reports/protect-mesure-apk.md | `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:assembleRelease -PrequireActivation=true` | non |
| protect-09 | sonnet | ordre signé, cibles, séquence, persistance : moteur de politiques | 400 k / 20 k | P-C2 | C/policy/{PolicyActions,PolicyState,PolicyGate}, CT DeviceClassOrderTest, R/DeviceClassBlockActivity, R/PolicyHub, docs/ORDRES.md | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*DeviceClassOrder*' --tests '*Policy*'` | oui |

### W8a

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| w8-01 | sonnet | LaneSet dynamique, Scheduler, pause/reprise : concurrence | 800 k / 40 k | W8a-1 | C/xfer/{Lane,Scheduler,LaneSet}, CT/xfer/{LaneSetTest,SchedulerDynamicTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LaneSet*' --tests '*SchedulerDynamic*'` | non |
| w8-02 | sonnet | EWMA, score, garde « jamais plus lent » : heuristiques | 400 k / 20 k | W8a-2 | C/xfer/{LaneHealth,Guard,RateLimiter,Lanes(limitRate)}, CT/xfer/{LaneHealthTest,GuardTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LaneHealth*' --tests '*Guard*'` | non |
| w8-03 | sonnet | ordre progressif, moov, flux clairsemé : algorithmique | 400 k / 20 k | W8a-2 | C/xfer/{OrderPolicy,SparseGrowingStream}, C/tv/Progressive (moovRange), CT/xfer/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OrderPolicy*' --tests '*SparseStream*'` | non |
| w8-04 | sonnet | codec de trame 24 o + CRC32C pur Kotlin, vecteurs figés | 400 k / 20 k | W8a-1 | C/xfer/{CbxFrame,Crc32c}, CT/xfer/{CbxFrameTest,Crc32cTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*CbxFrame*' --tests '*Crc32c*'` | non |
| w8-05 | sonnet | HKDF, nonces, anti-rejeu, AEAD : crypto de transport | 800 k / 40 k | W8a-1 | C/xfer/{SessionKeys,AeadRecords,CipherPick}, CT/xfer/*, tools/activation/xfer-vectors.json | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SessionKeys*' --tests '*Aead*' --tests '*XferVectors*'` | oui |
| w8-06 | sonnet | voies factices + tests de propriété : tests seulement, jugement | 400 k / 20 k | W8a-3 | CT/xfer/{FakeLane,FakeClock,PropertyTest,ResumePropertyTest} | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Property*'` | non |
| w8-07 | sonnet | façade BulkTransfer, Refused, contrat SendGuard : API partagée | 400 k / 20 k | W8a-3 | C/xfer/{BulkTransfer,LinkSnapshot,LaneStats,TransferClient}, CT/xfer/* | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BulkTransfer*' --tests '*LaneStats*'` | non |
| w8-08 | sonnet | sessions, part équitable, fsync cadencé, coupure simulée | 400 k / 20 k | W8a-3 | C/xfer/{TransferHost,PartAssembler}, CT/xfer/{TransferHostV2Test,PowerCutTest}, CT/MultipathTransferTest (adaptation) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TransferHostV2*' --tests '*PowerCut*'` | non |
| w8-09 | sonnet | serveur de vrac sur Link, voie BT, chien de garde : protocole | 800 k / 40 k | W8a-4 | C/xfer/{BulkStream,BulkBtLane}, CT/xfer/BulkStreamTest, C/tv/BtProtocol (UUID, ERR_*) | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BulkStream*'` | oui |

### X

| id | Modèle | Pourquoi | Jauge (entrée / sortie) | Groupe | Fichiers possédés | Porte | Audit |
|---|---|---|---|---|---|---|---|
| smart-remote — **FUSIONNÉ** | sonnet | fusionné | 800 k / 40 k | — | C/remote/strategy* | `—` | non |
| bt-remote — **FUSIONNÉ** | sonnet | fusionné | 800 k / 40 k | — | C/remote/vendor*, R/RemoteHub | `—` | non |
| bt-tunnel-keepalive — **LANCÉ** | sonnet | liaison RFCOMM persistante, hystérésis : concurrence, matériel | 400 k / 20 k | X-1 | C/tv/BtTunnel*, S/BtSshGatewayService, R/BtApiControl, BtTunnelBridge | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*BtTunnel*'` | non |
| multipath-transfer — **LANCÉ** | sonnet | moteur multivoie, API TV, banc : L, mesure d'abord | 800 k / 40 k | X-1 | C/tv/*Transfer*, S/UploadService, R serveur d'envoi, tools/transfer-bench | `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Transfer*'` | non |

### 2.1 Totaux par vague, et économie par rapport à « sonnet pour tous »

| Vague | Cahiers vivants | haiku | sonnet | Jetons entrée (routage) | Jetons sortie | Coût routage ($) | Coût naïf tout-sonnet ($) | Économie |
|---|---|---|---|---|---|---|---|---|
| W1 | 11 | 2 | 9 | 1.72 M | 94 k | 4.21 | 7.55 | 44 % |
| W2 | 13 | 1 | 12 | 4.36 M | 221 k | 10.85 | 17.30 | 37 % |
| W3 | 14 | 1 | 13 | 6.86 M | 345 k | 17.09 | 26.95 | 37 % |
| W4a | 6 | 1 | 5 | 2.46 M | 125 k | 6.08 | 9.90 | 39 % |
| W4b | 4 | 1 | 3 | 1.01 M | 53 k | 2.46 | 4.30 | 43 % |
| W4c | 8 | 1 | 7 | 4.15 M | 210 k | 10.20 | 17.05 | 40 % |
| W5a | 5 | 0 | 5 | 2.80 M | 140 k | 7.00 | 10.85 | 35 % |
| W5b | 5 | 1 | 4 | 2.86 M | 145 k | 7.08 | 11.45 | 38 % |
| W5c | 4 | 0 | 4 | 2.40 M | 120 k | 6.00 | 9.30 | 35 % |
| W5d | 5 | 1 | 4 | 2.86 M | 145 k | 7.08 | 11.45 | 38 % |
| W5e | 5 | 2 | 3 | 1.32 M | 70 k | 3.17 | 5.85 | 46 % |
| W6a | 8 | 0 | 8 | 3.75 M | 188 k | 9.38 | 14.55 | 36 % |
| W6b | 3 | 2 | 1 | 0.52 M | 30 k | 1.17 | 2.75 | 57 % |
| W6c | 4 | 0 | 4 | 2.00 M | 100 k | 5.00 | 7.75 | 35 % |
| W6d | 4 | 0 | 4 | 2.80 M | 140 k | 7.00 | 10.85 | 35 % |
| W6e | 4 | 2 | 2 | 0.92 M | 50 k | 2.17 | 4.30 | 50 % |
| PROTECT | 9 | 3 | 6 | 2.58 M | 135 k | 6.25 | 11.10 | 44 % |
| W8a | 9 | 0 | 9 | 4.80 M | 240 k | 12.00 | 18.60 | 35 % |
| X | 0 | 0 | 0 | 0.00 M | 0 k | 0.00 | 0.00 | — |
| **Total** | 121 | 18 | 103 | 50.17 M | 2551 k | 124.20 | 201.85 | 38 % |

Lecture : l'économie vient de deux sources, **routage** (18 cahiers Haiku à ≈ 0,09-0,20 $ au lieu de 0,38-1,00 $) et surtout **tactiques de contexte** (§ 3 : un Sonnet discipliné consomme ≈ 40 % de moins qu'un Sonnet qui explore le dépôt). Les deux sont dans la colonne « routage » ; la base naïve suppose Sonnet partout, sans tactiques. **Ordre de grandeur total : ≈ 124 $ pour les 121 cahiers vivants (≈ 50 M jetons effectifs en entrée, 2,6 M en sortie), contre ≈ 202 $ en naïf, soit ≈ 38 % d'économie ; plus ≈ 7 $ d'audits Opus (§ 5).** Ces chiffres sont des estimations non vérifiées (§ 8) ; le propriétaire lira les vraies valeurs dans les rapports (`JETONS:` ligne obligatoire, § 4.8) et `dispatch-plan.py --cost` se recalera sur `routing.json` une fois les jauges corrigées.

### 2.2 Hypothèses de jauge

| Modèle · effort | Entrée (jetons effectifs cumulés) | Sortie | Coût | Base naïve (sonnet, sans tactiques) |
|---|---|---|---|---|
| haiku · S | 60 k | 5 k | 0,09 $ | 250 k / 10 k = 0,60 $ |
| haiku · M | 150 k | 10 k | 0,20 $ | 650 k / 25 k = 1,55 $ |
| sonnet · S | 150 k | 8 k | 0,38 $ | 0,60 $ |
| sonnet · M | 400 k | 20 k | 1,00 $ | 1,55 $ |
| sonnet · L | 800 k | 40 k | 2,00 $ | 1 300 k / 50 k = 3,10 $ |
| opus · audit | 60 k (diff + cahier) | 6 k | 0,36 $ | — |

« Jetons effectifs » : somme sur tous les tours de (jetons non cachés + 0,1 × jetons lus en cache) ; un préfixe stable (§ 3.10) fait que 80-90 % de l'entrée est lue en cache après le premier tour. Effort S/M/L = celui des cahiers (S ≈ 0,5-1 j, M ≈ 2 j, L ≈ 3-4 j d'agent).

### 2.3 Cahiers mal dimensionnés : découpages proposés (à créer par Fable sur demande ; les cahiers d'origine ne sont pas réécrits)

| Cahier | Problème | Découpage (fichiers disjoints, même sous-vague, séquence a → b) |
|---|---|---|
| w4-04 (L, audit) | crypto + 4 consommateurs + 3 hubs TV dans un seul diff | **w4-04a** `LotCrypt` + `TvLotStore` + `RentalApi` + tests (cœur, audité) ; **w4-04b** `LearnLotConsumer`, `LangLotConsumer`, `QuizLots`, `LotsHub`/`LearnHub`/`LanguesHub` |
| w5-06 (L, audit) | KEK + contrats + scellement + routes + fenêtre d'essai | **w5-06a** `ShopProperties`, KEK, `rental_contract`, émission de boîte v2, migration ; **w5-06b** scellement + cache disque, routes des lots scellés, réémission, fenêtre d'essai |
| w5-07 (L, audit) | commandes + paiements + bons + reçus + routes agent | **w5-07a** `ShopRequestVerifier`, `OrderService`, `PaymentMethod`, `ShopController` ; **w5-07b** `VoucherService`, `ReceiptService`, `/api/v1/agent/*` |
| w5-12 (L, audit) | passerelle HTTP + livraison + relais + tâche périodique | **w5-12a** `HttpShopApi`, `TvShopCache`, sondage des commandes ; **w5-12b** `ShopDelivery` (lots scellés, reprise Wi-Fi), relais, `ShopSyncJob` |
| w6-17 (L, audit) | 25 chemins de données du téléphone | **w6-17a** `SendGuard` (cœur, test) + `TransferQueue*` + `UploadService`/`BtUploadService` ; **w6-17b** lecteur, jeux, Apprendre, bibliothèque/admin TV, boutique, tunnel, `DownloadService` |
| smart-remote (fusionné) | 12 stratégies dans un cahier | si reprise : **-a** empreinte + orchestrateur + CVTE ; **-b** Android TV v2, Samsung, LG, Roku, DLNA ; **-c** BT HID, IR, écran « Ma TV » |
| bt-remote (fusionné) | TV + téléphone | si reprise : **-a** cas A + B (TV) ; **-b** cas C (HID téléphone) |
| multipath-transfer (lancé) | moteur + API TV + banc | si relance : **-a** moteur cœur + ordonnanceur + tests JVM ; **-b** API TV + `UploadService` + banc |

Règle : un cahier Sonnet ne devrait pas dépasser **≈ 1 500 lignes de diff** ni **12 fichiers modifiés** (hors fichiers neufs de test) ; au-delà, découper. Un cahier Haiku : **≤ 300 lignes de diff, ≤ 6 fichiers**.

## 3. Tactiques d'économie de jetons (à mettre dans chaque prompt d'exécutant)

Toutes figurent dans le préfixe stable de `EXECUTOR-PROMPT-TEMPLATE.md` ; les voici avec leur raison.

1. **Lire seulement** : les fichiers possédés, les documents nommés dans le cahier (sections indiquées, pas le document entier), et les tests qui portent la porte. **Aucune exploration du dépôt** (« pour comprendre l'architecture ») : si une information manque, `grep -n` ciblé sur un symbole, puis lecture de la plage de lignes utile. Un `Read` de fichier entier > 400 lignes doit être justifié dans le rapport.
2. **Grep avant Read** ; `Read` avec `offset`/`limit` ; jamais `cat` d'un répertoire ; jamais `git log` sans `-n`.
3. **Défauts sans question** : les décisions du propriétaire sont déjà dans l'en-tête des index (D1…D17, PD1…PD4, P1…P5, D-W5-*, D-W6-*). Ce qui n'y est pas se tranche par la règle du cahier (« ne devine pas ») : faire la partie indépendante, écrire `QUESTION:` **une fois**, ne pas attendre, ne pas relancer.
4. **Conditions d'arrêt explicites** : la porte est verte **et** la suite nommée est verte **et** le rapport est écrit → s'arrêter. Deux échecs consécutifs de la porte sur la même cause → s'arrêter, rapporter `STATUT: BLOQUÉ`, ne pas tenter une troisième approche (c'est l'orchestrateur qui escalade).
5. **Éditions groupées** : préparer toutes les éditions d'un fichier, puis une seule passe d'`Edit` par fichier ; pas de relecture du fichier après édition (le harnais rapporte les échecs d'édition).
6. **Tester étroit, puis large, une fois** : la porte (`--tests '<filtre>'`) à chaque itération ; la suite complète (`:core:test`, `./mvnw test`) **une seule fois**, à la fin ; jamais `assembleDebug` pour vérifier une édition Kotlin (`compileDebugKotlin` suffit) ; jamais deux builds JVM en même temps (`gradle-lock.sh`).
7. **Rapport concis** (≤ 25 lignes, § 4.8), sans journal recopié, sans diff recopié (le diff est dans git).
8. **Ne pas redériver les décisions** : les conceptions W4/W5/W6 et PROTECT ont déjà tranché (« Décisions prises par l'architecte ») ; un exécutant qui « reconsidère » une décision la cite et l'applique, il n'écrit pas d'alternative.
9. **Scripts plutôt que boucles d'agent** : un contrôle répété (routes, vecteurs, règles XML, sommes) s'écrit en Python `unittest` ou bash et se relance par commande, pas par relecture d'un agent.
10. **Préfixe de prompt stable et partagé** : les agents d'une même vague reçoivent **exactement** le même préfixe (règles, interdits, format de rapport, commandes), puis le bloc propre au cahier **à la fin**. Même ordre, mêmes octets : le cache de prompt sert à tous les agents de la vague et à tous leurs tours. Rien de variable (date, id, nom de branche) **avant** le bloc du cahier.
11. **Pas de compaction subie** : un cahier qui approche 150 k jetons de contexte est trop gros ; l'agent écrit un jalon et s'arrête ; l'orchestrateur découpe (§ 2.3).
12. **Modèle explicite à chaque lancement** (`model: "haiku"` / `"sonnet"` / `"opus"`), jamais hérité.

## 4. Tactiques de qualité

### 4.1 Commandes d'acceptation
Chaque cahier a une **porte** (une commande, < 2 min, dans `routing.json` et l'en-tête) et des **critères complets** (section « Critères d'acceptation » du cahier). L'ordre est : porte à chaque itération → critères complets une fois → suite complète une fois → rapport.

### 4.2 Définition de « terminé »
Porte verte ; critères du cahier cochés ; suite complète verte (ou l'écart nommé avec sa cause) ; aucun fichier hors zone modifié (`git status --porcelain` listé dans le rapport) ; textes utilisateur en français ; « CastBridge » / « CastBridge-TV » ; `docs/HANDOFF.md` mis à jour **seulement si** le cahier le possède ; rapport ≤ 25 lignes avec la ligne `JETONS:`.

### 4.3 Liste d'autocontrôle (à cocher dans le rapport)
- [ ] je n'ai modifié que les fichiers possédés (liste exacte) ;
- [ ] la porte est verte, la suite complète a tourné une fois ;
- [ ] aucun secret, chemin de clé, adresse privée, jeton dans le code, les tests, les docs, le rapport ;
- [ ] aucune nouvelle dépendance (Gradle, Maven, pip) ; sinon nommée et justifiée ;
- [ ] pas de `TODO` sans ticket, pas de code mort laissé, pas de test désactivé ;
- [ ] messages utilisateur en français, sans espace réservé « lorem » (placeholders de D7 marqués `[Contact à fournir — D7]`) ;
- [ ] diff ≤ plafond (§ 2.3) ;
- [ ] un seul commit, message conforme (§ 4.6), **pas de push** sauf consigne.

### 4.4 Actions interdites (préfixe de tous les prompts)
Toucher `main`, `integration/agents`, `wip/external-ai-changes` ; pousser sans consigne ; toute connexion au serveur (`ssh`, `docker`, `scp` vers `bridge.sti-cm.com`), à la TV ou au téléphone (`adb`) sans la consigne « propriétaire présent » ; lire ou citer `~/.castbridge-signing`, `backend/.env`, `secrets/`, `*.jks`, `*.pem` ; construire une TV non verrouillée (`-PrequireActivation=true` toujours) ; lancer `gradle`/`mvnw` sans `tools/agents/gradle-lock.sh` ; `git add -A` / `git add .` ; modifier `version.properties` hors cahier w6-11 ; renommer un module ; installer une dépendance ; `git push --force` ; modifier le cahier d'un autre agent. **Fichiers sensibles** (interdits à Haiku, audit Opus si modifiés) : `C/owner/{Activation,Keys,License,Envelope,FeatureGate,TrialPolicy,DegradedPolicy,Delegation*,X25519,InstallSigner,TvProof,SuperSession}.kt`, `C/crypto/**`, `C/lots/{RentalKeys,LotCrypt,InstallKey}.kt`, `C/tokens/**`, `C/sales/**`, `B/licenses/**`, `B/shop/**`, `B/agents/**`, `B/tunnel/**`, `android/sshd/**`, `*/build.gradle.kts` (blocs de signature, grâce, super-admin), `tools/activation/*-vectors.json`.

### 4.5 Plafonds de diff et anti-conflit
Plafonds § 2.3. Conflits : (1) **fichiers disjoints par sous-vague** (matrices des index ; `dispatch-plan.py` ne propose jamais deux cahiers d'un même groupe partageant un chemin : les matrices ont été vérifiées par les index) ; (2) un fichier « point chaud partagé » (`PlayerActivity`, `ActivationCenter`, `RentalHub`, `TvService`, `HomeScreen`) n'est édité que par **un** cahier par sous-vague, les autres codent contre son contrat et le nomment dans le rapport ; (3) **un seul build JVM à la fois** : `tools/agents/gradle-lock.sh <commande>` (verrou `mkdir`, délai 30 min, reprise d'un verrou orphelin, `--status`, `--release`) ; `flock` n'existe pas sur ce Mac ; (4) chaque agent sur sa branche `claude/<id>` depuis `origin/integration/agents` ; fusion par le coordinateur, une sous-vague à la fois, après ses portes.

### 4.6 Protocole de commit
Un seul commit par cahier (squash si plusieurs jalons), sujet Conventional Commits en anglais (`feat(core): …`, `fix(tv): …`, `docs: …`, scopes `tv`, `phone`, `owner`, `core`, `server`, `content`, `tools`, `docs`, `ci`), corps en français de 3-6 lignes (quoi, pourquoi, porte), pieds :
```
Co-Authored-By: Claude <modèle> <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_<identifiant>
```
(`<modèle>` = Haiku 4.5 / Sonnet 5.5 / Opus 5.5 selon l'exécutant). **Pas de push** sauf si le prompt le dit ; jamais de PR par l'agent ; jamais `main`.

### 4.7 Cahiers Haiku : forme mécanique obligatoire ; cahiers Sonnet : forme par objectif
Un prompt Haiku ne contient que des **instructions exécutables** : pour chaque fichier, le chemin exact ; pour chaque édition, le texte **avant** (ancre unique) et le texte **après** ; pour chaque fichier neuf, son contenu intégral ou un gabarit fermé ; les commandes à lancer dans l'ordre et le résultat attendu (chaîne exacte, code de sortie) ; une règle d'arrêt. Aucune phrase « choisis », « améliore », « si nécessaire ». Les 18 cahiers Haiku actuels sont déjà proches de cette forme (fichiers nommés, commandes de porte) ; **avant lancement**, l'orchestrateur (ou un Sonnet de préparation, 0,10 $) convertit les étapes rédigées en paires avant/après : c'est le « bloc cahier » du gabarit Haiku. Un cahier Sonnet garde ses sections « Objectif / Étapes / Critères / Cas limites / À ne pas faire » : Sonnet choisit les moyens dans la zone possédée, et note chaque choix dans le rapport (une ligne par choix).

### 4.8 Format de rapport (`docs/agent-reports/<id>.md`, ≤ 25 lignes, français)
```
STATUT: TERMINÉ | BLOQUÉ | ÉCHEC
CAHIER: <id> · MODÈLE: <haiku|sonnet> · BRANCHE: claude/<id> · COMMIT: <sha court>
JETONS: entrée ≈ <n> k (dont cache ≈ <n> k) · sortie ≈ <n> k · tours : <n>     ← lu dans le harnais ; sinon « inconnu »
PORTE: <commande> → VERT|ROUGE (<durée>)
SUITE COMPLÈTE: <commande> → VERT|ROUGE ; écarts : <liste ou « aucun »>
FICHIERS: <liste exacte, hors zone = aucun>
CHOIX: <une ligne par décision locale prise, ou « aucun »>
NON FAIT / À VALIDER SUR MATÉRIEL: <liste>
QUESTION: <une phrase avec options, ou « aucune »>
AUTOCONTRÔLE: [x] zone [x] porte [x] secrets [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
```

## 5. Plan d'audit Opus

Principe : l'auditeur reçoit **le diff** (`git diff origin/integration/agents...claude/<id>`), **le cahier** (et son en-tête), **rien d'autre** (pas d'accès au dépôt entier ; `Read` autorisé sur les seuls fichiers du diff si une ligne de contexte manque). Il ne modifie rien ; il rend un verdict `ACCEPTER | ACCEPTER AVEC CORRECTIONS | REFUSER` avec des constats numérotés (fichier:ligne, gravité, correction proposée en une phrase). Gabarit : `EXECUTOR-PROMPT-TEMPLATE.md` § C. Coût ≈ 0,36 $ par session (60 k entrée, 6 k sortie).

| Session d'audit | Après fusion locale de | Diffs audités (C = 2) | Points d'attention |
|---|---|---|---|
| A-W1 | W1-A | w1-01, w1-03, w1-05 | `allowBackup`, défaut super-admin ; PIN en en-tête, `Host` ; uptime persisté, AHEAD 45 j, grâce sans `clock.txt` |
| A-W2a | W2-A | w2-01, w2-02 | `SeqState`/`RevocationState` persistés, clé de secours ; SSH release (D5 = NON : drapeau + SFTP) |
| A-W2b | W2-B | w2-15 | enrôlement `production` seul, `notAfter` obligatoire |
| A-W3 | W3-A | w3-09 | relais `cbr1`, comptage des essais par appareil |
| A-W4a | W4a | w4-01, w4-03, w4-04, w4-05 | X25519 vs RFC 7748, `SecretWrapper`, Keystore et repli, lots au repos, miroirs XDH |
| A-W4b | W4b | w4-07 | `TvGate` dégradé, droits durables, aucune ouverture involontaire |
| A-W4c | W4c | w4-11, w4-15, w4-16 | délégation sans maître (amendement W5), tickets, TOTP, anomalies |
| A-W5a | W5a | w5-02, w5-04 | porte-jetons HMAC chaîné, plafond hors ligne 60 ; `Feature.SHOP`, émission sans maître |
| A-W5b | W5b | w5-06, w5-07, w5-08 | KEK, contrats, états de commande, bons, grand livre, remboursements |
| A-W5c | W5c | w5-12 | livraison activation + lots + bons ; aucun secret dans l'URL ; reprise |
| A-W5d | W5d | w5-16, w5-17 | routes `/api/shop`, installeur en ligne ; clé HMAC dérivée de la clé d'installation |
| A-W6a | W6a | w6-03, w6-04, w6-07 | preuve au défi, cache 14 j ; session super, 5 échecs, AuditChain ; rapports v2 signés |
| A-W6b | W6b | w6-09 | `X-CB-TV-Proof` exigé, rejeu |
| A-W6c | W6c | w6-12, w6-15 | route de preuve avec nonce, trames BT ; routes parentales interdites au tunnel |
| A-W6d | W6d | w6-16, w6-17, w6-19 | TOFU, identité changée, mur ; `SendGuard` sans contournement ; `check_no_superadmin` |
| A-P-B | PROTECT B | protect-02, 03, 04 | faux positifs (émulateur, debug, DEV_BUILD verts), épinglage vide = ignoré, PD2 |
| A-P-C | PROTECT C | protect-09 | ordre signé : cible, portée, séquence, persistance, PD4 |
| A-W8a | W8a | w8-05, w8-09 | HKDF, nonces uniques entre voies, fenêtre anti-rejeu, choix du chiffre ; serveur de vrac sur `Link` (HELLO/ACK, chien de garde, aucune route sans confiance) |
| A-X | fusion de bt-tunnel-keepalive / multipath | (échantillon) | une liaison RFCOMM, hystérésis ; `.part`, quotas, auth des routes de transfert |

19 sessions ≈ **7 $**. Un audit « RAS » deux fois de suite sur une famille → échantillonnage (§ 1, désescalade). Un `REFUSER` → le cahier repart en Sonnet avec les constats (pas en Opus exécutant) ; si le constat est de conception → Fable.

## 6. Protocole de dispatch (session principale)

**Contraintes matérielles** : Mac 8 Go ; un démon Gradle ≈ 2-3 Go, Maven ≈ 1 Go ; **au plus 3 agents en parallèle**, **un seul build JVM** (verrou) ; préférer 1 agent Gradle + 2 agents sans JVM (docs, Python, serveur Maven sous le même verrou). TV hors ligne ; aucune installation sur la TV sans confirmation du propriétaire ; builds TV verrouillées seulement.

**Ordre des vagues** (prérequis vérifiés par `dispatch-plan.py --done …`) :

1. **W1-A** (11 cahiers ; w1-12 fait, w1-13 remplacé, w1-09 à vérifier d'abord : HANDOFF dit les 3 tests déjà déterministes). Lancer par lots de 3 : {w1-07 h, w1-08 h, w1-01} → {w1-02, w1-03, w1-06} → {w1-04, w1-05, w1-10} → {w1-11, w1-09 si utile}. **Point de contrôle 1** : fusion W1, suite complète, audit A-W1, compilation `:receiver`/`:sender`, **propriétaire** : installation sur la TV de référence (copie de l'APK dans `Download/` de la clé USB).
2. **PROTECT A → B ∥ D** (protect-01 seul ; puis protect-02/03/04 + protect-07 h) ; **C** (05 ∥ 06 h) ; audit A-P-B. protect-09 attend w2-01 ; protect-08 h après B.
3. **W2-A** (w2-01 après w1-05 ; w2-07 après w1-04 ; w2-02 après w1-09) par lots de 3 ; **W2-B** (w2-03 partiel, w2-04, w2-15) ; audits A-W2a/b ; **point de contrôle 2** : fusion W2, TV de référence (**propriétaire**).
4. **W3-A** (8 cahiers) puis **W3-B** (w3-02 ; w3-14 **avec le propriétaire**) ; W3-C selon décisions (D12, D14, D15). protect-09 et protect-08 ici.
5. **W4a → W4b → W4c** (ordre des index ; w4-01 seul d'abord ; **lire d'abord les amendements W5** pour w4-11/12/13/16/17/18) ; audits A-W4a/b/c ; **point de contrôle 3** : Keystore affiché, réinstallation ⇒ réémission (**propriétaire**).
6. **W5a → W5e** ; audits ; **point de contrôle 4** : préprod avec `castbridge.shop.enabled=true` et clé de test ; clé publique du serveur dans `activation-trusted-keys.txt` ; première commande avec bon de test (**propriétaire**).
7. **W6a → W6e** ; audits ; **point de contrôle 5** : build `-PrequireTvProof=true` à côté, TV d'essai ⇒ murs (**propriétaire**).
8. **W8a** (w8-01…09, présents) : indépendante des vagues 1-6 (module `core.xfer`), elle peut s'intercaler dès W3 quand les 3 places d'agents sont libres : {w8-01, w8-04, w8-05} → {w8-02, w8-03} → {w8-06, w8-07, w8-08} → w8-09 ; audit A-W8a ; w8-07 préfère w6-17 fusionné (contrat `SendGuard`), sinon contrat local.
9. **W7, W8b-d, W9, W10, W11** : dès que leurs cahiers existent, l'auteur applique § 7 (règle + gabarit) ; ils entrent dans `routing.json` et l'en-tête de 3 lignes est posé avant tout lancement. Au 2026-10-02 : index W8 et W9 écrits, cahiers w8-10…21 et w9-* absents ; conceptions W7, W10, W11 présentes sans cahiers.

**Ce que le propriétaire approuve** : (a) le lancement de chaque vague (« go W1-A lot 1 ») ; (b) chaque fusion dans `integration/agents` après le point de contrôle ; (c) toute installation sur la TV ou le téléphone ; (d) toute action serveur ; (e) les décisions ouvertes : D7, D12, D14, D15, D9-bis, D-W5-1, D-W5-2, D-W6-4 (juriste) ; (f) la création des cahiers découpés de § 2.3 ; (g) le passage de `main` (jamais par un agent).

## 7. Règle pour les cahiers à venir (W7-W10) et gabarit d'en-tête

L'auteur d'un nouveau cahier (Fable) : (1) note les 8 critères de § 1 dans le cahier (une ligne) ; (2) choisit le modèle par la règle de décision ; (3) si haiku, écrit le cahier en forme mécanique (§ 4.7) ; (4) ajoute l'entrée dans `docs/agent-briefs/routing.json` (`id, file, wave, group, model, effort, deps, status, audit, why, files, gate, split`) ; (5) pose l'en-tête :

```
<!-- routage Fable AAAA-MM-JJ -->
> **Modèle : <haiku|sonnet>** · escalade : <règle> · statut : <PRÊT|BLOQUÉ …>
> **Groupe : <Wn-x>** (vague Wn) · prérequis : <ids ou aucun> · porte : `<commande>`
> **Jauge : ≈ <n> k jetons entrée / <n> k sortie** (effort S|M|L) · audit Opus : oui|non
```

Un cahier sans en-tête ni entrée dans `routing.json` **ne se lance pas** (`dispatch-plan.py` ne le voit pas).

## 8. Ce qui n'a pas pu être vérifié, et décisions attendues

Non vérifié : les jauges de jetons (aucune session mesurée ; modèle de § 2.2 ; à recaler avec les lignes `JETONS:` des premiers rapports) ; la disjonction fine des fichiers **entre** cahiers « points chauds » (`ActivationCenter`, `RentalHub`) : prise des index, non recontrôlée fichier par fichier ; l'état réel de w1-09 (HANDOFF dit « fait », aucun rapport) ; l'avancement de `bt-tunnel-keepalive` et `multipath-transfer` (BOARD : lancés le 2026-10-01 ; aucun rapport dans `docs/agent-reports/`) ; `gradle-lock.sh` et `dispatch-plan.py` testés avec `--help`, `--dry-run`, `--status`, `true` sous verrou et deux scénarios `--done` : **jamais avec un vrai Gradle** ; l'arbre de travail contenait des modifications non commitées d'un autre agent dans `android/core` (`ReceiverServer.kt`, `Volumes.kt`, `Filing.kt`…) pendant cette rédaction : elles ne sont pas de ce document. `docs/HANDOFF.md` n'a pas été modifié (lecture seule imposée à cette tâche) : **à faire par la session principale** (une ligne : routage posé, outils `tools/agents/`, rien lancé).

Décisions du propriétaire : (1) valider la règle « Haiku = forme mécanique seulement, 18 cahiers » ou élargir (candidats suivants, à un cran : w2-08, w3-14, w5-20, w6-20 : docs/campagnes de synthèse) ; (2) accepter le plafond de 3 agents / 1 build JVM ; (3) accepter le plan d'audit (19 sessions, ≈ 7 $) ou le réduire à l'échantillon ; (4) autoriser la création des cahiers découpés (§ 2.3) avant W4a/W5b/W6d ; (5) premier lot à lancer : **W1-A lot 1 = w1-07 (haiku), w1-08 (haiku), w1-01 (sonnet)** — trois agents, un seul Gradle (w1-01), deux sans JVM.
