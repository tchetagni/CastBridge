# Journal des modifications de CastBridge

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) ; numérotation [SemVer](https://semver.org/lang/fr/) avec le suffixe `-beta` (règles : `docs/RELEASES.md`, `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`).
Trois produits livrés ont chacun leur numéro : **CastBridge-TV** (`tv-<version>`), **CastBridge** téléphone (`phone-<version>`), **CastBridge Propriétaire** (`owner-<version>`, jamais distribué). Le serveur (`backend/`, `pom.xml` 1.0.0) n'a pas encore de numéro de version propre (proposition : lignes `server.*` dans `version.properties`).

> **Comment ce journal a été reconstruit, et sa limite.** Aucun tag git n'existait au 2026-10-02 : la correspondance version / commit est reconstituée à partir de l'historique premier-parent de `integration/agents` (`git log --first-parent`) et de l'historique de `version.properties` (`git log -p -- version.properties`). Le contenu de chaque entrée vient **uniquement des sujets de commits** (rien n'est inventé) et peut être **approximatif** : les APK ont parfois été construits avant le commit qui pose leur numéro, et plusieurs numéros n'ont jamais été commités (voir « Numéros sans commit »). Avant le 2026-10-01 (création de `version.properties`, commit `0ff64ed`), les numéros ne sont connus que par les sujets de commits et `docs/HANDOFF.md` : ils sont listés sans promesse de tag. Le plan d'étiquetage prouvable est produit par `tools/release/tag-plan.sh`.
>
> Les tags `tv-…`, `phone-…`, `owner-…` listés ci-dessous sont **proposés** (aucun n'est créé à la date de ce document).

## [Non publié]

Travail du 2026-10-02 présent dans l'arbre de travail de `integration/agents` (82 fichiers modifiés et environ 100 non suivis au moment de la rédaction), **non commité** : il n'appartient à aucune version numérotée. `version.properties` annonce encore TV 0.14.17-beta / téléphone 1.2.29-beta : un APK construit depuis cet arbre porterait ces numéros avec un contenu différent du commit `faf8636`. **Commiter puis incrémenter avant tout nouveau build distribué.**

### Rangement réel par catégorie et par fichier (`integration/agents`, non validé sur TV)
- Les fichiers reçus par la CastBridge-TV sont rangés à la réception dans de vrais dossiers (`Films`, `Séries/<Titre>/Saison NN`, `Musique`, `Photos`, `Captures`, `Documents`, `Archives`, `Cours`, `Famille`, `À trier`) sous un nom propre, sur la mémoire interne et sur la clé (`Download/CastBridge/Bibliotheque/…`) : `Filing`/`FiledIndex` (`core/tv`), `FileStore.fileInto`. Jamais d'écrasement (numéro ajouté), chemins sûrs, FAT32 respecté, installateurs et paquets laissés à plat, reprise et « même nom, même taille » inchangés (le nom d'origine reste reconnu), API compatible (champs `folder`, `finalName`, `origin` ajoutés).
- Action « Ranger ma bibliothèque » : plan (`GET /api/library/organize`) puis application (`POST /api/library/organize/apply`) pour les fichiers déjà reçus à plat ; refusée avec un profil enfant, jamais sur un fichier protégé ; entrée dans la Bibliothèque de la TV du téléphone.
- Le quota et l'éviction comptent aussi les fichiers rangés ; `routeGuard` ferme la connexion après un refus d'envoi (corps non lu).
- Tests : `FilingTest` (15), `FilingServerTest` (17). Voir `docs/STORAGE.md` § 10 et `docs/LIBRARY-AGENT.md` § 17.

### Activation, essai, production
- Fenêtre d'activation, droit de plafond d'usage, essai toujours borné, règles clé d'essai / clé de production : durcissements dans `core/owner` (`Activation`, `Keys`, `FeatureGate`, `TrialPolicy`), console Propriétaire (`ownerlib`), outil de bureau et miroirs Java/Python (vecteurs de test étendus dans `tools/activation/verify_vectors.py`).
- `version.properties` : nouvelle clé `lock.graceDays` (30 par défaut, 0 après activation du parc : décision D1 du propriétaire, `docs/TRIAL-EDITION.md` § 16).
- Écran d'activation de la TV (`ActivationActivity`, `ActivationCenter`, `PolicyHub`, `TvService`) et manifestes.

### Locations et lots
- Coffre de location, file de livraison, magasin de lots de la TV (`RentalVault`, `DeliveryQueue`, `TvLotStore`) et leurs tests (`RentalTest`, `LotsTvTest`).
- Conception de la boutique (téléphone + TV), des locations gérées en ligne par le serveur, des jetons virtuels du Quiz et du paiement en espèces : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` et cahiers `sonnet-w4-*` / `sonnet-w5-*` (**en conception, rien de construit**).
- Conceptions : enveloppe de location, mode dégradé en fin de clé, vente sur le terrain (`DESIGN-W4-*`).

### Langues
- Lots de langues a0 (japonais, chinois : `famille`, `nombres`, `salut`, versions fr et en), `zh-a0-salut-fr` enrichi, `content/langues/embedded.txt` et `lots.json`.
- 66 fichiers audio du propriétaire dans `content/langues-media/` : **non suivis, à ne jamais committer dans le dépôt de code** (contenu lourd : dépôt privé `castbridge-content`).

### Contenus libres (CC BY-SA)
- Archive des contenus libres téléchargeable même sans activation : outil `tools/free-content`, `core/free`, route serveur (`FreeContentController`), export TV (`FreeContentExport`), écran téléphone (`FreeContentScreen`), `docs/FREE-CONTENT.md`.

### Sécurité et audit
- Durcissement de la TV : `core/tv/Security.kt`, `ReceiverServer`, `Storage`, `TvSshServer`, règles de sauvegarde Android (`backup_rules`, `data_extraction_rules`) et tests (`TvHardeningTest`, `SecurityTest`, `ClockRollbackTest`, `TrialRoutesTest`, `GraceMigrationTest`).
- Conception de la protection de la TV (déploiement TV uniquement, anti-rétro-ingénierie, étiquettes éthiques) : `PROTECTION-TV-FABLE-2026-10-02.md`, cahiers `protect-01` à `protect-09`.
- Audits : `AUDIT-PROJET-2026-10-02.md`, `AUDIT-CONTENU-APPRENDRE-2026-10-02.md`, recommandations Fable.

### Serveur
- `BundleCatalogController` (import du catalogue de lots signé), `TelemetryService`, `DeviceService`, `WireActivation`, `EnvelopeVerifier`, `application.yml`, `backup.sh`, `backend/README.md` (+71 lignes), tests associés (`BundleCatalogApiTest`, `TelemetryApiTest`, `DevicesApiTest`, `RentalVectorsTest`, `FreeContentApiTest`).
- Exploitation : `ops/monitoring/`, `ops/first-run/check-server.sh`, `docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md`.

### Intégration continue et outillage de versions
- `.github/workflows/android.yml`, `release.yml` (modifiés), `tools.yml` (nouveau, non suivi), `.gitignore`.
- Documents : `docs/RELEASES.md`, ce journal, `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md` ; outils `tools/release/check_versions.py`, `tag-plan.sh`, `deploy-server.sh` et leurs tests (`tools/tests/test_release_tools.py`).

## CastBridge-TV

### Numéros sans commit
Les numéros **0.14.12 et 0.14.13** n'ont jamais été posés dans `version.properties` par un commit (leurs codes exacts ne sont pas prouvables : ils sont entre 51 et 53) : `0.14.12-test` est un build d'essai documenté dans `docs/HANDOFF.md`, à ne jamais publier. Le saut de 0.14.11 (code 49) à 0.14.14 (code 54) est donc normal. Les numéros **0.13.4 à 0.14.5** (et téléphone **1.2.6 à 1.2.18**) correspondent à la vague de fusions du 2026-10-01 : aucun commit ne les nomme.

### [0.14.17-beta] (code 60 ; verrouillée 0.14.17-beta-verrouillee, code 61) : 2026-10-02
Tag proposé `tv-0.14.17-beta` sur `faf8636`.
- Ajouté : client TV du tunnel d'assistance à distance (écran des conditions, enrôlement, SSH inverse direct ou par la passerelle du téléphone, liste d'experts) ; brouillon `CONDITIONS-ASSISTANCE-A-DISTANCE`.
- Modifié : aria2 désactivé dans l'édition d'essai.

### [0.14.16-beta] (code 58 ; verrouillée 59) : 2026-10-02
Tag proposé `tv-0.14.16-beta` sur `ea0d28c`.
- Sécurité : correctifs d'audit (grâce en date absolue, fichiers d'état écrits de façon atomique, droits refusés par défaut, retour d'horloge détecté avec temps monotone, plafond d'essai implicite, liste blanche de routes en essai, BT/USB/SSH fermés en essai).
- Modifié : clé de production = durée seule, licence automatique, aucun droit obligatoire ; import du catalogue de lots signé depuis le serveur ; tunnel d'administration à distance (côté serveur, désactivé par défaut) et signature de la liste d'experts.

### [0.14.15-beta] (code 56 ; verrouillée 57) : 2026-10-02
Tag proposé `tv-0.14.15-beta` sur `1c26a11`.
- Ajouté : politiques de production dans les outils Propriétaire (durée de clé au choix, durée de location fixée par le serveur par lot, 30 jours par défaut, lots choisis dans le catalogue) ; passage essai vers production depuis l'application téléphone ; contenu de base pour chaque classe et matière (mode classe).

### [0.14.14-beta] (code 54 ; verrouillée 55) : 2026-10-02
Tag proposé `tv-0.14.14-beta` sur `17a79f2` (regroupe aussi les commits intermédiaires depuis 0.14.11, numéros 0.14.12 et 0.14.13 jamais commités).
- Ajouté : droit de plafond d'usage (`usage|duree|from|to`), fenêtre unique de 12 h pour les lots d'essai, restrictions de l'édition d'essai (streaming et Sudoku seulement), badge de clé permanent sur chaque écran, écran de passage essai vers production, verrouillage à l'expiration ; module Langues sur la TV (tuile, consommateur, démarrage) ; locations de lots livrées depuis le téléphone ; bandeau de la page d'administration de la TV ; contenu nursery, tle-a, droit-l3 (+ lots).
- Ajouté (commits intermédiaires) : locations branchées sur la TV (coffre, grand livre, balayage `RentalHub`, routes `/api/rental`, `/api/activation/install` par PIN seul et `/request`, toutes les activations acceptées conservées) ; consommateur Apprendre enregistré dans le magasin de lots, outillage de test de location prouvé sur émulateur ; champ du code d'appareil de la console qui garde le curseur.
- Corrigé : deux figures de leçon ramenées sous le budget de 8 Ko ; 7 roues Python (75 Mo) retirées des sources Kotlin.

### [0.14.11-beta] (code 49 ; verrouillée 50) : 2026-10-02
Tag proposé `tv-0.14.11-beta` sur `e40173a`.
- Ajouté : `SUPER_UNLIMITED` (la clé du super administrateur signe le droit `super` : lit et déverrouille tout, locations permanentes ; les comptes temporaires et illimités continuent d'expirer et de supprimer les locations), miroirs Java et Python.
- Fusionné : `naming-patterns`, `rental-lots` (les locations gardent leurs dates, fenêtre d'installation toujours 48 h), `media-pipeline`, `deferred-orders`, `activation-tools`.

### [0.14.10-beta] (code 47 ; verrouillée 48) : 2026-10-01
Tag proposé `tv-0.14.10-beta` sur `ce052c8`.
- Modifié : l'accueil de la TV montre ce qui arrive même quand le nom existe déjà complet ; serveur aligné sur le format commun `cbx1` (fenêtre de 48 h en heures, pas de licence permanente par la clé serveur).

### [0.14.9-beta] (code 45 ; verrouillée 46) : 2026-10-01
Tag proposé `tv-0.14.9-beta` sur `88b3035`.
- Modifié : licence d'usage permanente (achat du lot « tout », `ISSUE_UNLIMITED` seulement) remplace la fenêtre d'installation illimitée ; fenêtre de 48 h pour tout le monde ; vecteurs et vérificateur Python à jour.

### [0.14.8-beta] (code 43 ; verrouillée 44) : 2026-10-01
Tag proposé `tv-0.14.8-beta` sur `e6825e7`.
- Modifié : codes d'activation avec fenêtre d'installation de 48 h depuis la création (appliquée par l'émetteur et la TV), clés illimitées réservées au super administrateur, clé compacte v2 en heures ; vecteurs régénérés, recoupement Python 125/125.

### [0.14.7-beta] (code 41 ; verrouillée 42) : 2026-10-01
Tag proposé `tv-0.14.7-beta` sur `86ad3f2`.
- Corrigé : l'écran d'activation demande toutes les permissions Bluetooth (annonce) pour que les services démarrent.

### [0.14.6-beta] (code 39 ; verrouillée 40) : 2026-10-01
Tag proposé `tv-0.14.6-beta` sur `0ff64ed` (première version de `version.properties`).
- Ajouté : source unique des versions ; variante verrouillée (code +1, suffixe `-verrouillee`).
- Contenu cumulé depuis 0.13.2 (fusions du 2026-10-01, versions intermédiaires non nommées) : verrou d'activation de la TV (interrupteur de compilation, clés publiques de confiance hors dépôt, grâce pour les installations mises à jour, écran d'activation avec fichier USB ou clé saisie) ; canal Bluetooth propriétaire (la TV reçoit les demandes d'appareil et accepte les activations même verrouillée ; la clé est seulement « posée » et le propriétaire valide) ; `GET /api/activation` ; écran d'activation affichant la version et l'état du canal ; entrée super administrateur locale (barrière bcrypt injectée à la compilation hors dépôt, essais limités, geste caché) ; CastBridge Dev (SSH par clé seule + `cbdev install/uninstall/start/status`) ; lots, télécommande intelligente, animations réseau, onglet parental, validation bêta, télécommande Bluetooth seule, tunnel Bluetooth, enveloppe générique ; contenus Apprendre et Quiz (primaire, collège, lycée, anglophone, technique/supérieur) ; clé USB (marque, modèle, série, débit) dans les écrans de stockage ; classeur de séries (Titre/Saison).

### [0.13.x] : 2026-10-01
Numéros cités par les sujets de commits (aucun n'est dans `version.properties`, pas de tag proposé sauf accord) :
- 0.13.0 (code 24), `2d6446f` : tout le lot charte graphique, Jeux/Sudoku, Bluetooth plug and play, contrôle parental, quiz 300 parties + packs, assistant de bibliothèque (dossiers TV, corbeille), adresse serveur masquée. Publiée sur la clé USB, installée par le propriétaire.
- 0.13.1 (code 25), `a3bfc56` : badge du nombre de connexions SSH en haut à droite.
- 0.13.2 (code 26), `ddcefa1` : accès à l'écran d'accessibilité de CVTE LiteSettings depuis l'aide de la télécommande.
- 0.13.3 (code 27) : Bluetooth plug and play robuste (branche `claude/plug-and-play-robust`, commit `21af017`, fusionnée par `073f3c7`).

### Versions antérieures (citées par les sujets de commits, 2026-09-30 et 2026-10-01)
0.4.2 et 0.4.3 (passerelle Internet Bluetooth, diagnostics), 0.7 (échecs, téléchargements aria2), 0.7.1 (lecteur du téléphone), 0.8 (télécommande du téléphone), 0.9 (Apprendre), 0.9.1 (code 19), 0.11 (code 20, chemin de mise à jour propre), 0.11.1 (code 21, correctif JSON de `/api/library`), 0.12.0 (code 22), 0.12.1 (code 23, adresse serveur officielle masquée).

## CastBridge (téléphone)

### [1.2.29-beta] (code 59) : 2026-10-02
Tag proposé `phone-1.2.29-beta` sur `faf8636`. Synchronisée avec le tunnel d'assistance à distance de la TV 0.14.17 (passerelle du téléphone).

### [1.2.28-beta] (code 58) : 2026-10-02
Tag proposé `phone-1.2.28-beta` sur `ea0d28c`. Envoi de la clé par Wi-Fi ; import du catalogue de lots signé ; correctifs d'audit.

### [1.2.27-beta] (code 57) : 2026-10-02
Tag proposé `phone-1.2.27-beta` sur `1c26a11`. Passage essai vers production depuis l'application ; politiques de production.

### [1.2.26-beta] (code 56) : 2026-10-02
Tag proposé `phone-1.2.26-beta` sur `17a79f2` (le code saute de 35 à 56 : builds intermédiaires jamais commités). Planification Langues ; livraison des lots loués depuis le téléphone.

### [1.2.25-beta] (code 35) : 2026-10-02
Tag proposé `phone-1.2.25-beta` sur `b92998e`. Ne copie jamais un fichier que la TV détient déjà complet (même nom et même taille).

### [1.2.24-beta] (code 34) : 2026-10-02
Tag proposé `phone-1.2.24-beta` sur `e40173a`. `SUPER_UNLIMITED` (voir TV 0.14.11).

### [1.2.23-beta] (code 33) : 2026-10-01
Tag proposé `phone-1.2.23-beta` sur `ce052c8`. Bandeau de copie sous la barre du lecteur, pourcentage dans les notifications de transfert.

### [1.2.22-beta] (code 32) à [1.2.21-beta] (code 31) : 2026-10-01
Tags proposés `phone-1.2.22-beta` (`88b3035`) et `phone-1.2.21-beta` (`e6825e7`). Codes d'activation : fenêtre de 48 h, licence d'usage permanente (voir TV 0.14.8 et 0.14.9).

### [1.2.20-beta] (code 30) : 2026-10-01
Tag proposé `phone-1.2.20-beta` sur `86ad3f2`. Récupération automatique d'une TV déjà appairée après une réinstallation.

### [1.2.19-beta] (code 29) : 2026-10-01
Tag proposé `phone-1.2.19-beta` sur `0ff64ed`. « Activer la TV » montre l'état d'activation propre de la TV ; ouverture de l'écran « Activer la TV » (clé collée, TV appairée, envoi par Bluetooth) avec appairage automatique ; contenus et fonctions fusionnés le 2026-10-01 (file de copie ou déplacement en arrière-plan, sélecteur multi-fichiers, « Ouvrir avec », progression de copie pendant la diffusion, classeur de séries).

### [1.2.x] : 1.2-beta à 1.2.5-beta, 2026-10-01
Numéros cités par les sujets de commits, aucun dans `version.properties` : 1.2-beta (code 10, `2d6446f`), 1.2.1-beta (11, `a3bfc56`), 1.2.2-beta (12, `285f593` : carte « introuvable » stable et « Réassocier » en un geste), 1.2.3-beta (13, `23e9257` : écran parental avec le jeton du téléphone de confiance), 1.2.4-beta (14, `a6cc6b0` : correctif d'un plantage de la télécommande), 1.2.5-beta (15, branche `claude/plug-and-play-robust`).

### Versions antérieures
0.7 (échecs, téléchargements), 0.9 (télécommande), 1.0-beta (Apprendre), 1.1-beta (code 9), 1.1.1-beta (adresse serveur officielle masquée).

## CastBridge Propriétaire (console des activations)

### [0.2.3] (code 5) : 2026-10-02
Tag proposé `owner-0.2.3` sur `faf8636`.

### [0.2.2] (code 4) : 2026-10-02
Tag proposé `owner-0.2.2` sur `ea0d28c`. Clé de production = durée seule ; envoi de clé par Wi-Fi ; import du catalogue signé ; signature de la liste d'experts.

### [0.2.1] (code 3) : 2026-10-02
Tag proposé `owner-0.2.1` sur `1c26a11`. Durée de clé au choix, durée de location fixée par le serveur par lot, lots choisis dans le catalogue.

### [0.2.0] (code 2) : 2026-10-02
Tag proposé `owner-0.2.0` sur `e40173a`. Droit `SUPER_UNLIMITED`.

### [0.1.0] (code 1) : 2026-10-01
Tag proposé `owner-0.1.0` sur `0ff64ed`. Console du téléphone propriétaire (coffre, garde de déverrouillage, verrouillage automatique, émission d'activation, clé publique, journal chaîné), outil `castbridge-owner` en ligne de commande, bibliothèque partagée `:ownerlib`, entrée super administrateur locale.

## Serveur (`backend/`, pom 1.0.0)

Pas de version propre ni de tag à ce jour. Jalons tirés de l'historique : module de gestion des licences (désactivé par défaut) fusionné le 2026-10-01 puis aligné sur `cbx1` ; fenêtre d'installation de 48 h ; service d'ordres différés ; locations (règles de dates, vecteurs) ; correctif de l'adresse `/admin/` ; tunnel d'administration à distance côté serveur (désactivé par défaut) ; WireGuard ouvert sans pair. Le serveur de production n'a pas de révision traçable avant le premier déploiement par `tools/release/deploy-server.sh` (« révision initiale inconnue »).
