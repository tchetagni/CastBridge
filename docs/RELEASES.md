# Procédure de publication des versions de CastBridge

Ce document décrit le processus complet de publication des applications CastBridge (téléphone), CastBridge-TV et CastBridge Propriétaire.

## 1. Portée

### Applications publiées
- **CastBridge** (app téléphone, module Gradle `:sender`) : version phone (client mobile)
- **CastBridge-TV** (app TV, module Gradle `:receiver`) : version tv (client TV)
- **CastBridge Propriétaire** (console des activations, module `:owner`, jamais distribué publiquement)

### Applications non publiées
- **CastBridge Dev** (outils SSH + installation, `:dev`, jamais distribué) : version dev
- **Backend** (serveur) : versionné indépendamment (tags `server-<version>`), déployé par `tools/release/deploy-server.sh` (voir § 14 et `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`)

### Architecture
Les applications téléphone et TV partagent le module `:core` et les outils `tools/`. Les trois applications publiées lisent leurs versions dans `version.properties` (source unique).

## 2. Numérotation

### Versioning scheme

- **Format SemVer strict** : `MAJOR.MINOR.PATCH` (ex. `1.2.29`)
- **Versions bêta** : suffixe `-beta` tant que le produit n'est pas offert en vente
  - Ex. : `0.14.17-beta`, `1.2.29-beta`
  - Le suffixe `-beta` indique une version en cours de développement/test
- **Variante verrouillée (TV uniquement)** : suffixe `-verrouillee` après la version
  - Ex. : `0.14.17-beta-verrouillee`
  - La variante verrouillée a un code d'installation (+1) distinct

### Version Code

- **Règle** : le `versionCode` doit être **strictement croissant**, jamais réutilisé, même après revert
- **Format** : entier positif démarrant à 1
- **Variante verrouillée TV** : `code_nonverrouille + 1`
  - Ex. : variante sans verrou = 60, variante verrouillée = 61

### Source unique

`version.properties` est l'unique source de vérité pour :
- `phone.versionName` et `phone.versionCode`
- `tv.versionName` et `tv.versionCode`
- `owner.versionName` et `owner.versionCode`
- `dev.versionName` et `dev.versionCode`

### Override de test

Les builds de test **seulement** peuvent surcharger via `-Pcastbridge.versionName` et `-Pcastbridge.versionCode` :
```bash
gradle :receiver:assembleRelease \
  -Pcastbridge.versionName=0.14.17-test \
  -Pcastbridge.versionCode=999
```

**Jamais** en production.

### Règle de grâce pour le verrouillage

- **`lock.graceStartMs`** : horodatage UTC (millisecondes) marquant le début absolu de la grâce pour la variante verrouillée
  - Format : ms depuis epoch (ex. `1790899200000` = 2026-10-02 00:00 UTC)
- **Règle** : cet horodatage **ne doit jamais reculer** pour un build publié
- **Justification** : garantir que le parc existant (installé avant cette date) a une grâce d'activation
- **Grâce durée** : `-Pcastbridge.graceDays` (défaut 30 jours, jamais reculée après publication)

## 3. Variantes CastBridge-TV

### Variante sans verrou (dev/test)

- **Nom du build** : `-PrequireActivation=false` (défaut)
- **Code d'installation** : ex. 60
- **Suffix du nom** : aucun (ex. `0.14.17-beta`)
- **Signature** : clé de debug
- **Utilisation** : développement, tests, ciblage locataire ou agent
- **Publication** : jamais en tant que version stable

### Variante verrouillée (production)

- **Nom du build** : `-PrequireActivation=true`
- **Code d'installation** : code_sans_verrou + 1 (ex. 61)
- **Suffix du nom** : `-verrouillee` (ex. `0.14.17-beta-verrouillee`)
- **Signature** : clé de release (production)
- **Fichier de clés de confiance** : `tools/activation/trusted-keys.json` (ou équivalent)
  - Liste des clés publiques de confiance pour valider les activations serveur
  - Mise à jour synchronisée avec le serveur
- **Utilisation** : déploiement production, clients finaux
- **Publication** : vers la clé USB `Download/` et serveur `/admin/releases`

### Builds de test identifiés

- **Convention** : suffixe `-test` dans le nom de version (ex. `0.14.12-test`)
- **Signature** : clé de debug ou release, selon le contexte
- **Distribution** : historique `~/CastBridge-release/` seulement, **jamais sur le serveur `/admin/releases`**
- **Destruction** : après validation, supprimer du répertoire d'historique

## 4. Pré-requis avant construction

### Tests obligatoires

- [ ] **`:core:test` vert** : tous les tests du moteur core doivent passer
- [ ] **`:sshd:test` vert** : tests du serveur SSH de la TV
- [ ] **Backend `mvn test` vert** (si modification) : tests du serveur

### Tests instables connus

Aucun test instable connu depuis le correctif w15-07 (fusion `7da821a9`, 2026-10-02). En cas d'échec, lire `docs/agent-reports/sonnet-w15-07.md` avant de conclure à une régression ; ne pas relancer à l'aveugle.

### Validations additionnelles

- [ ] **`python3 tools/activation/verify_vectors.py`** : valide les vecteurs d'activation contre la spécification
- [ ] **`:core:checkStarterBudget`** : vérifie le budget du contenu d'apprentissage
- [ ] **`python3 tools/content-validation/cbvalidate.py check`** : valide le contenu
- [ ] **`docs/HANDOFF.md` à jour** : le document doit refléter l'état avant build
- [ ] **Version de consentement télémétrie** : confirmée et relue par juriste si modifiée

### Vérifications environnement

```bash
export ANDROID_HOME=~/Library/Android/sdk
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3/bin/gradle)
test -x "$G" || echo "Gradle 8.14.3 introuvable"
$G --version
```

### ABI obligatoire

- **CastBridge-TV** : **`armeabi-v7a` (32 bits) obligatoire**
  - Raison : la TV de référence est GaiaOS 32 bits
  - **Jamais** ARM64 ou x86 seul
  - Les cibles sont de **tout type** : Android TV, Fire TV, box

## 5. Construction

### Commandes de test (avant release)

```bash
export ANDROID_HOME=~/Library/Android/sdk
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.14.3-bin/*/gradle-8.14.3/bin/gradle)

# Tous les tests
$G :core:test :sshd:test :receiver:assembleDebug :sender:assembleDebug
```

### Build production (variante non verrouillée - test/dev)

```bash
# CastBridge-TV (débogauge)
$G :receiver:assembleRelease

# CastBridge (téléphone)
$G :sender:assembleRelease

# Chemins de sortie :
# TV:      android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release-unsigned.apk
# Téléphone: android/sender/build/outputs/apk/release/sender-release-unsigned.apk
```

### Build avec verrou (production)

```bash
# Variante verrouillée (TV uniquement, pour production)
$G :receiver:assembleRelease -PrequireActivation=true
# Chemins de sortie :
# TV verrouillée: android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release-unsigned.apk
# (le code de version et le nom de version reflètent -PrequireActivation=true : +1 au code, suffixe « -verrouillee » ; le nom du fichier ne change pas)
```

### Chemins de sortie standards

| App | Variant | Path |
|-----|---------|------|
| CastBridge-TV | Debug | `android/receiver/build/outputs/apk/debug/receiver-armeabi-v7a-debug.apk` |
| CastBridge-TV | Release | `android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release-unsigned.apk` |
| CastBridge | Debug | `android/sender/build/outputs/apk/debug/sender-debug.apk` |
| CastBridge | Release | `android/sender/build/outputs/apk/release/sender-release-unsigned.apk` |

Les APK `release` sortent non signés de Gradle (aucun `signingConfig` dans les `build.gradle.kts`) : la signature est décrite au § 6.

## 6. Signature

Gradle produit `…-release-unsigned.apk` (§ 5) ; les commandes ci-dessous nomment le fichier APRÈS signature, par exemple `~/CastBridge-release/SIGNED/CastBridge-TV-<version>-verrouillee-armeabi-v7a.apk` (docs/HANDOFF.md).

### ⚠ Clé réellement utilisée par les applications installées (correction du 2026-10-02)

Toutes les applications installées chez le propriétaire (CastBridge-TV 0.14.18 à 0.14.20, CastBridge téléphone 1.2.31 à 1.2.33) sont signées avec la **clé du projet** `~/.castbridge-signing/release.jks` (PKCS12, alias `castbridge`, certificat `CN=CastBridge, O=CastBridge, C=CM`, SHA-256 `ef290816819ad081dfe6b6421d3b83b4a2cdc0827142d505a6797de07a757ba0`), et NON avec la clé de debug du Mac (SHA-256 `2e0c587a…`) que ce document citait. Une mise à jour ne s'installe que si elle porte ce même certificat : **signer toute APK à installer par-dessus avec `release.jks`**, et vérifier avant d'installer : `apksigner verify --print-certs <apk>` doit afficher `ef290816…`.

Signer SANS jamais lire ni afficher le mot de passe (référence de fichier, un seul `--ks-pass`, pas de `--key-pass` pour un PKCS12) :
`apksigner sign --ks ~/.castbridge-signing/release.jks --ks-key-alias castbridge --ks-pass file:$HOME/.castbridge-signing/release.pass --out <signé.apk> <aligné.apk>`

### Clé de debug (développement)

- **Localisation** : `~/.android/debug.keystore`
- **Mot de passe** : `android` (défaut)
- **Signature automatique** : Gradle signe automatiquement les builds debug
- **Utilisation** : tests locaux, développement
- **Validité** : 30 ans

### Clé de release (production)

- **Localisation** : `secrets/` (jamais dans Git)
- **Format** : JKS, P12 ou PEM selon le setup
- **Détenteur** : **Esaie Tchetagni** (propriétaire)
- **Sauvegarde** : hors dépôt Git, backup sécurisée
- **Signature Gradle** : nécessite configuration dans `android/build.gradle.kts` ou équivalent
  - Aucun mot de passe en dur
  - Utiliser des variables d'environnement (`KEYSTORE_PASSWORD`, etc.)

### Processus de signature

1. **Jamais en CI/CD** : la signature doit rester **locale**
2. **Vérification post-signature** :
   ```bash
   apksigner verify --verbose android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release.apk
   ```
3. **Cas de déploiement** : une APK est signée une seule fois avec la clé finale avant publication

### Décision en attente (D12)

**Migration clé debug → clé release** : pas encore décidée. Impact = une seule réinstallation manuelle sur la TV (vidéos et lots conservés, données internes réinitialisées).

## 7. Contrôles

### Inspection du binaire

```bash
# Lister les ressources et informations
aapt2 dump badging android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release.apk \
  | grep -E "package=|versionName=|versionCode=|uses-permission"
```

### Installation par-dessus sur la TV de référence

1. Connecter la TV en SSH ou via USB
2. Installer l'APK :
   ```bash
   adb install -r android/receiver/build/outputs/apk/release/receiver-armeabi-v7a-release.apk
   # Ou via l'API interne de la TV (PIN requis)
   curl -H "X-CB-Pin: 123456" -F "file=@receiver-armeabi-v7a-release.apk" \
     http://<TV_IP>:8765/upload/castbridge.apk
   curl -H "X-CB-Pin: 123456" http://<TV_IP>:8765/api/apk/install?names=castbridge.apk
   ```

### Vérification après installation

- [ ] Badge d'activation visible (ESSAI / PRODUCTION / SANS CLÉ)
- [ ] Pas de crash au démarrage
- [ ] Pas de logs d'erreur pertinentes
- [ ] Tests fonctionnels de base (bibliothèque, streaming, apprentissage)

### Sommes de contrôle SHA256

Générer et vérifier :
```bash
bash tools/release/sha256sums.sh ~/CastBridge-release/
cat ~/CastBridge-release/SHA256SUMS
# Vérifier
bash tools/release/sha256sums.sh ~/CastBridge-release/ --check
```

Format `SHA256SUMS` (compatible `shasum -a 256 --check`) :
```
a1b2c3d4e5f6g7h8i9j0... receiver-armeabi-v7a-release.apk
a1b2c3d4e5f6g7h8i9j0... sender-release.apk
```

## 8. Publication

### Répertoires de sortie

- **Local** : `~/CastBridge-release/<app>-<version>-<abi>.apk`
  - Ex. : `CastBridge-TV-0.14.17-beta-armeabi-v7a.apk`
  - Associé : `SHA256SUMS`
- **Clé USB** : `Download/` du répertoire de la clé USB (`exFAT`)
  - Téléchargement par SSH depuis la TV
  - Vérification d'intégrité via `SHA256SUMS`

### Processus de publication

1. **Construire** les APK (variante non verrouillée pour test/beta, verrouillée pour production)
2. **Générer SHA256SUMS** :
   ```bash
   bash tools/release/sha256sums.sh ~/CastBridge-release/
   ```
3. **Copier sur la clé USB** :
   ```bash
   ssh user@<TV_IP> "cp ~/CastBridge-release/*.apk /mnt/usb-key/Download/"
   ssh user@<TV_IP> "cp ~/CastBridge-release/SHA256SUMS /mnt/usb-key/Download/"
   ```
4. **Serveur** : publier chaque APK signé avec `tools/release/publish-apk.sh` (ci-dessous), ou par la page `/admin/releases`. Les adresses publiques stables et la page `/telecharger` se mettent à jour toutes seules
5. **Tags Git** :
   - `tv-<version>` pour CastBridge-TV (ex. `tv-0.14.17-beta`)
   - `phone-<version>` pour CastBridge (ex. `phone-1.2.29-beta`)
   - Format : `git tag -a tv-0.14.17-beta -m "Release TV 0.14.17-beta (code 60)"`

### Serveur : page `/admin/releases`

- Liste les APK disponibles
- Affiche les sommes SHA256
- Permet le téléchargement direct
- Historique des versions publiées

### Publier sur le serveur : `tools/release/publish-apk.sh` (ajouté le 2026-10-07)

Un seul ordre publie un APK signé sur `bridge.sti-cm.com` (route admin `POST /api/v1/admin/releases`, `docs/API-SERVER.md` § 1) et affiche son adresse publique :

```bash
tools/release/publish-apk.sh tv    ~/CastBridge-release/SIGNED/CastBridge-TV-0.14.44-beta-verrouillee-armeabi-v7a.apk 115 0.14.44-beta-verrouillee "Notes de version en français."
tools/release/publish-apk.sh phone ~/CastBridge-release/SIGNED/CastBridge-phone-1.2.52-beta.apk                      82  1.2.52-beta              "Notes de version en français."
```

Arguments : `app` (`tv` ou `phone`), chemin de l'APK, `versionCode`, `versionName`, notes (facultatives ; accents, apostrophes et retours à la ligne permis). Variables facultatives : `ABI` (défaut `armeabi-v7a` pour `tv`, `universal` pour `phone`), `CHANNEL` (`stable`), `ROLLOUT` (`100`), `MANDATORY` (`false`), `SERVER` (`ubuntu@bridge.sti-cm.com`), `API` (`http://127.0.0.1:7090`, vu du serveur), `PUBLIC` (`https://bridge.sti-cm.com`).

Le script refuse un nom de fichier contenant `-test`, `superadmin` ou `debug`, calcule le SHA-256 local, dépose l'APK dans `~/castbridge/incoming/` du serveur (`scp`), lit **sur le serveur, dans le conteneur** `castbridge-api`, le jeton d'administration (jamais affiché, jamais copié sur ce Mac), appelle la route de publication, efface le fichier déposé, compare le SHA-256 renvoyé par le serveur à celui du fichier local (« ALERTE » s'ils diffèrent) et affiche l'adresse `/dl/<app>/<fichier>`.

Règles (§ 3 et § 6) : seulement des builds signés avec la clé de release ; jamais un build `-test`, ni la variante superadmin du téléphone ; pour la TV, la variante verrouillée (`-PrequireActivation=true`, code +1). Un `versionCode` déjà publié pour la même app, la même architecture et le même canal est refusé (409) : on ne republie jamais un code, on en monte un nouveau.

### Adresses publiques stables et page `/telecharger` (ajouté le 2026-10-07, serveur 1.2.5)

Disponibles à partir du déploiement de `server-1.2.5` (avant : 404).

| Adresse | Mène à |
|---|---|
| `https://bridge.sti-cm.com/dl/tv/latest.apk` | la dernière CastBridge-TV |
| `https://bridge.sti-cm.com/dl/phone/latest.apk` | la dernière CastBridge (téléphone) |
| `https://bridge.sti-cm.com/telecharger` | la page de téléchargement : version, date, taille, SHA-256, bouton « Télécharger » et code QR de chaque application |

`/dl/<app>/latest.apk` répond **302** vers `/dl/<app>/<fichier>` de la version qui a la plus haute `versionCode` parmi les versions **stables, non retirées et à 100 % de déploiement**. Une version publiée avec `ROLLOUT` inférieur à 100, une bêta (`CHANNEL=beta`) ou une version retirée (`/revoke`) n'est jamais servie à cette adresse ; elle le devient (ou cesse de l'être) dès que le déploiement atteint 100 % (ou que la version est retirée), sans rien d'autre à faire. À `versionCode` égal, l'architecture se choisit ainsi : `tv` → `armeabi-v7a`, puis `universal`, puis `arm64-v8a` (une TV 64 bits exécute le 32 bits, et les TV du parc sont en `armeabi-v7a`) ; `phone` → `universal`, puis `arm64-v8a`, puis `armeabi-v7a`. `?abi=<architecture>` demande cette architecture puis, à défaut, l'APK `universal` (jamais une autre architecture) ; `x86` et `x86_64` ne sont servies que sur demande. Aucune version publiée : 404 en français.

La réponse 302 porte `Cache-Control: no-store` (la cible change à chaque publication : ne jamais figer cette adresse dans un cache), `X-Content-SHA256` (SHA-256 du fichier cible) et `X-CastBridge-Version` (son `versionName`) : `curl -sI https://bridge.sti-cm.com/dl/tv/latest.apk`. Le fichier lui-même reste sous son nom immuable `/dl/<app>/<fichier>` (reprise de téléchargement, ETag, cache d'un an). Le nom `latest.apk` est réservé : tout fichier publié s'appelle `castbridge-<app>-<version>-<code>-<abi>-<début du hash>.apk`. Pour vérifier un téléchargement : `curl -L -o CastBridge-TV.apk https://bridge.sti-cm.com/dl/tv/latest.apk && shasum -a 256 CastBridge-TV.apk` doit donner le SHA-256 de la page.

La page `/telecharger` (et `/telecharger/`) se met à jour toute seule à chaque publication ou retrait (cache navigateur de 5 minutes) : rien à régénérer, rien à copier sur le serveur, aucune modification de nginx attendue (`location /` envoie tout ce qui n'est pas `/play/` à `castbridge-api`, relevé dans `docs/PLAY-OPS.md` ; à confirmer par `nginx -T` avant le déploiement). Elle n'affiche que la version que mènent les deux adresses ci-dessus, jamais l'historique, jamais une version retirée, jamais un canal bêta. Même sécurité que `/guide/` : sans authentification ni cookie, CSP stricte, limite de débit par adresse IP. Détail des routes : `docs/API-SERVER.md` § 1.

## 9. Journal des versions

Tableau reconstruit mécaniquement à partir de l'historique premier-parent de `version.properties` (`git log --first-parent -p -- version.properties`) : une ligne par changement de `tv.versionName`, `phone.versionName`, `owner.versionName` ou `server.versionName`, avec le `versionCode` du même commit (pour la TV, celui de la variante sans verrou ; la verrouillée a +1). Le détail par version est dans `docs/CHANGELOG.md`. Des étiquettes existent depuis le 2026-10-02 (`git tag -l 'tv-*' 'phone-*' 'owner-*' 'server-*'`) ; les versions non étiquetées sont celles listées par `bash tools/release/tag-plan.sh`. Le dépôt date du 2026-09-29 et `version.properties` du 2026-10-01 : toute date antérieure serait fausse.

| Application | Version | Code | Date | Commit |
|---|---|---|---|---|
| CastBridge-TV | 0.14.6-beta | 39 | 2026-10-01 | `0ff64edc` |
| CastBridge-TV | 0.14.7-beta | 41 | 2026-10-01 | `86ad3f21` |
| CastBridge-TV | 0.14.8-beta | 43 | 2026-10-01 | `e6825e76` |
| CastBridge-TV | 0.14.9-beta | 45 | 2026-10-01 | `88b3035b` |
| CastBridge-TV | 0.14.10-beta | 47 | 2026-10-01 | `ce052c8a` |
| CastBridge-TV | 0.14.11-beta | 49 | 2026-10-02 | `e40173a5` |
| CastBridge-TV | 0.14.14-beta | 54 | 2026-10-02 | `17a79f27` |
| CastBridge-TV | 0.14.15-beta | 56 | 2026-10-02 | `1c26a114` |
| CastBridge-TV | 0.14.16-beta | 58 | 2026-10-02 | `ea0d28cc` |
| CastBridge-TV | 0.14.17-beta | 60 | 2026-10-02 | `faf86366` |
| CastBridge-TV | 0.14.18-beta | 62 | 2026-10-02 | `c81fa23a` |
| CastBridge-TV | 0.14.19-beta | 64 | 2026-10-02 | `d137b966` |
| CastBridge-TV | 0.14.20-beta | 66 | 2026-10-02 | `b5f6ed5b` |
| CastBridge-TV | 0.14.21-beta | 68 | 2026-10-02 | `7803b705` |
| CastBridge-TV | 0.14.22-beta | 69 | 2026-10-03 | `2dc05ea5` |
| CastBridge-TV | 0.14.26-beta | 78 | 2026-10-03 | `43e1c10b` |
| CastBridge-TV | 0.14.27-beta | 80 | 2026-10-03 | `d26c1ff7` |
| CastBridge-TV | 0.14.28-beta | 82 | 2026-10-04 | `e0d20299` |
| CastBridge-TV | 0.14.29-beta | 84 | 2026-10-04 | `db246a25` |
| CastBridge-TV | 0.14.30-beta | 86 | 2026-10-04 | `2cfbd2ff` |
| CastBridge-TV | 0.14.31-beta | 88 | 2026-10-04 | `1f8937e6` |
| CastBridge-TV | 0.14.32-beta | 90 | 2026-10-04 | `1c6e87e4` |
| CastBridge-TV | 0.14.33-beta | 92 | 2026-10-04 | `132a46b6` |
| CastBridge-TV | 0.14.34-beta | 94 | 2026-10-04 | `4b884064` |
| CastBridge-TV | 0.14.35-beta | 96 | 2026-10-04 | `a62aa023` |
| CastBridge-TV | 0.14.36-beta | 98 | 2026-10-04 | `c24e9b6f` |
| CastBridge-TV | 0.14.37-beta | 100 | 2026-10-04 | `2d6eb0c8` |
| CastBridge-TV | 0.14.38-beta | 102 | 2026-10-06 | `2807b6c8` |
| CastBridge-TV | 0.14.39-beta | 104 | 2026-10-06 | `c0332cb2` |
| CastBridge-TV | 0.14.40-beta | 106 | 2026-10-06 | `fff21176` |
| CastBridge-TV | 0.14.41-beta | 108 | 2026-10-06 | `4914ece3` |
| CastBridge-TV | 0.14.42-beta | 110 | 2026-10-06 | `7b9ac69f` |
| CastBridge-TV | 0.14.43-beta | 112 | 2026-10-06 | `3d36dbfd` |
| CastBridge-TV | 0.14.44-beta | 114 | 2026-10-07 | `c5590e40` |
| CastBridge-TV | 0.14.45-beta | 116 | 2026-10-07 | `e837a8f2` |
| CastBridge-TV | 0.14.46-beta | 118 | 2026-10-07 | `404d5808` |
| CastBridge-TV | 0.14.47-beta | 120 | 2026-10-07 | `9a1d2ae6` |
| CastBridge-TV | 0.14.48-beta | 122 | 2026-10-11 | `687b8e09` |
| CastBridge-TV | 0.14.49-beta | 124 | 2026-10-11 | `b70645c6` |
| CastBridge-TV | 0.14.50-beta | 126 | 2026-10-15 | `891ba1df` |
| **CastBridge-TV** | **0.14.51-beta** (courant) | **128** | **2026-10-15** | **cb83f5ff** |
| CastBridge | 1.2.19-beta | 29 | 2026-10-01 | `0ff64edc` |
| CastBridge | 1.2.20-beta | 30 | 2026-10-01 | `86ad3f21` |
| CastBridge | 1.2.21-beta | 31 | 2026-10-01 | `e6825e76` |
| CastBridge | 1.2.22-beta | 32 | 2026-10-01 | `88b3035b` |
| CastBridge | 1.2.23-beta | 33 | 2026-10-01 | `ce052c8a` |
| CastBridge | 1.2.24-beta | 34 | 2026-10-02 | `e40173a5` |
| CastBridge | 1.2.25-beta | 35 | 2026-10-02 | `b92998e2` |
| CastBridge | 1.2.26-beta | 56 | 2026-10-02 | `17a79f27` |
| CastBridge | 1.2.27-beta | 57 | 2026-10-02 | `1c26a114` |
| CastBridge | 1.2.28-beta | 58 | 2026-10-02 | `ea0d28cc` |
| CastBridge | 1.2.29-beta | 59 | 2026-10-02 | `faf86366` |
| CastBridge | 1.2.30-beta | 60 | 2026-10-02 | `c81fa23a` |
| CastBridge | 1.2.31-beta | 61 | 2026-10-02 | `d137b966` |
| CastBridge | 1.2.32-beta | 62 | 2026-10-02 | `2c07510e` |
| CastBridge | 1.2.33-beta | 63 | 2026-10-02 | `c24ad099` |
| CastBridge | 1.2.34-beta | 64 | 2026-10-02 | `7803b705` |
| CastBridge | 1.2.35-beta | 65 | 2026-10-02 | `2e20cf47` |
| CastBridge | 1.2.36-beta | 66 | 2026-10-03 | `9e1c9425` |
| CastBridge | 1.2.37-beta | 67 | 2026-10-03 | `c8be0e96` |
| CastBridge | 1.2.38-beta | 68 | 2026-10-03 | `2dc05ea5` |
| CastBridge | 1.2.39-beta | 69 | 2026-10-03 | `43e1c10b` |
| CastBridge | 1.2.40-beta | 70 | 2026-10-03 | `d26c1ff7` |
| CastBridge | 1.2.41-beta | 71 | 2026-10-04 | `e0d20299` |
| CastBridge | 1.2.42-beta | 72 | 2026-10-04 | `3ca29fe5` |
| CastBridge | 1.2.43-beta | 73 | 2026-10-04 | `4b884064` |
| CastBridge | 1.2.44-beta | 74 | 2026-10-05 | `30adb551` |
| CastBridge | 1.2.45-beta | 75 | 2026-10-06 | `5cb50967` |
| CastBridge | 1.2.46-beta | 76 | 2026-10-06 | `2807b6c8` |
| CastBridge | 1.2.47-beta | 77 | 2026-10-06 | `c0332cb2` |
| CastBridge | 1.2.48-beta | 78 | 2026-10-06 | `fff21176` |
| CastBridge | 1.2.49-beta | 79 | 2026-10-06 | `4914ece3` |
| CastBridge | 1.2.50-beta | 80 | 2026-10-06 | `3d36dbfd` |
| CastBridge | 1.2.51-beta | 81 | 2026-10-07 | `0e54a9bf` |
| CastBridge | 1.2.52-beta | 82 | 2026-10-07 | `c5590e40` |
| CastBridge | 1.2.53-beta | 83 | 2026-10-07 | `9751a8d2` |
| CastBridge | 1.2.54-beta | 84 | 2026-10-07 | `9a1d2ae6` |
| CastBridge | 1.2.55-beta | 85 | 2026-10-07 | `bad92156` |
| CastBridge | 1.2.56-beta | 86 | 2026-10-09 | `c733fc6d` |
| CastBridge | 1.2.57-beta | 87 | 2026-10-15 | `891ba1df` |
| **CastBridge** | **1.2.58-beta** (courant) | **88** | **2026-10-15** | **cb83f5ff** |
| CastBridge Propriétaire | 0.1.0 | 1 | 2026-10-01 | `0ff64edc` |
| CastBridge Propriétaire | 0.2.0 | 2 | 2026-10-02 | `e40173a5` |
| CastBridge Propriétaire | 0.2.1 | 3 | 2026-10-02 | `1c26a114` |
| CastBridge Propriétaire | 0.2.2 | 4 | 2026-10-02 | `ea0d28cc` |
| CastBridge Propriétaire | 0.2.3 | 5 | 2026-10-02 | `faf86366` |
| **CastBridge Propriétaire** | **0.2.4** (courant) | **6** | **2026-10-02** | **`c81fa23a`** |
| Serveur | 1.1.0 | 2 | 2026-10-02 | `c81fa23a` |
| Serveur | 1.1.1 | 3 | 2026-10-02 | `de562215` |
| Serveur | 1.2.0 | 4 | 2026-10-04 | `0947db17` |
| Serveur | 1.2.1 | 5 | 2026-10-04 | `0e407bc7` |
| Serveur | 1.2.2 | 6 | 2026-10-06 | `90bb91ac` |
| Serveur | 1.2.3 | 7 | 2026-10-06 | `df631911` |
| Serveur | 1.2.4 | 8 | 2026-10-07 | `afed6a30` |
| **Serveur** | **1.2.5** (courant) | **9** | **2026-10-07** | **`f790a4e9`** |

## 10. Retour arrière

### Cas : réaction rapide contre crash majeur

1. **Identifier la version stable précédente** : consulter le tableau du § 9
2. **Vérifier que les tests passent** sur cette version en branche
3. **Recompiler** à partir du tag approprié :
   ```bash
   git checkout tv-0.14.15-beta
   git checkout android/receiver/src
   $G :receiver:assembleRelease -PrequireActivation=true
   ```
4. **Valider** la signature et les sommes
5. **Republier** avec nouveau tag si la version change, sinon **mettre à jour l'horodatage**

### Procédure de backup

- Avant chaque nouvelle publication, **sauvegarder l'APK précédente** dans un dossier archives
- Conserver le `SHA256SUMS` de chaque version
- Ne pas réutiliser un `versionCode` : créer un nouveau même si on revient à du code plus ancien

### Limites

- Un revert comlet du code source **ne revient pas** au binaire antérieur (rebuild = différent)
- Réinstaller une ancienne APK sur la TV existante : elle gardera ses données si la signature est la même

## 11. Hotfix

### Processus

1. **Créer une branche** depuis le tag de la version courante :
   ```bash
   git checkout -b fix/crash-majeur tv-0.14.17-beta
   ```
2. **Corriger le défaut** (minimal, isolé)
3. **Tester** : `:core:test`, `:sshd:test`, validation manuelle
4. **Augmenter le version code** et **patch** (pas minor/major) :
   - Ex. : `0.14.17-beta` → `0.14.18-beta` (patch +1)
   - Code : 60 → 62 (pour laisser de la place)
5. **Compiler et signer** comme au § 5/6
6. **Publier et tagguer** : `tv-0.14.18-beta`
7. **Fusionner dans `main` / `feat/ssh`** après validation
8. **Documenter** dans le journal (§ 9)

### Points de vigilance

- **Jamais** de hotfix sans rebase/test sur la branche d'intégration
- **Risque** : perte de synchronisation avec la branche principale
- **Règle** : hotfix doit être commité et fusionné dans les 24 h après publication ou reverté

## 12. Outils de contrôle des versions (ajouté le 2026-10-02)

| Outil | Rôle | Écrit quelque chose ? |
|---|---|---|
| `python3 tools/release/check_versions.py [--apk FILE...] [--json] [--strict]` | Contrôle `version.properties` (forme, SemVer, versionCode croissant par rapport à HEAD et aux tags), la règle de la variante verrouillée, les APK (aapt2), l'état git, la cohérence avec `backend/pom.xml`. Codes de sortie : 0 conforme, 1 erreur, 2 usage/environnement | Non |
| `bash tools/release/tag-plan.sh [--apply] [--push] [--app tv\|phone\|owner]` | Propose les tags annotés `tv-`, `phone-`, `owner-` sur le commit qui a introduit chaque version ; sans `--apply` : plan seulement ; ne déplace jamais un tag existant ; ne pousse que avec `--apply --push` | Seulement avec `--apply` |
| `bash tools/release/deploy-server.sh <tag> [--apply]` | Déploiement traçable du serveur (voir § 14) | Seulement avec `--apply` |
| `bash tools/release/sha256sums.sh DIR` | Sommes SHA-256 des APK (déjà décrit au § 7) | `DIR/SHA256SUMS` |
| `tools/release/publish-apk.sh <tv\|phone> <apk> <versionCode> <versionName> [notes]` | Publie un APK signé sur le serveur et affiche son adresse `/dl/…` (voir § 8) ; les adresses `latest.apk` et la page `/telecharger` suivent seules | Oui : publie sur `bridge.sti-cm.com` (un `versionCode` ne se republie pas ; retrait par `/revoke`) |

Tests : `python3 -m unittest discover -s tools/tests -p 'test_release_tools.py'`.

### Ajouts à la liste de contrôle de publication (§ 4 à 8)

1. `python3 tools/release/check_versions.py` : aucune erreur (les avertissements « non commité » doivent être compris, pas ignorés).
2. Arbre de travail propre et commit de version fait AVANT de construire (un build doit venir d'un commit identifiable).
3. Après construction : `python3 tools/release/check_versions.py --apk <fichiers>` compare chaque APK à `version.properties` (variante verrouillée : code +1 et suffixe `-verrouillee`).
4. Après publication : `bash tools/release/tag-plan.sh`, relire, puis `--apply` ; `--push` seulement sur décision du propriétaire.

## 13. Étiquettes (tags) : conventions

| Tag | Objet | Exemple |
|---|---|---|
| `tv-<versionName>` | CastBridge-TV (le tag désigne la variante sans verrou ; la variante verrouillée porte le même tag, nom + `-verrouillee`, code +1) | `tv-0.14.17-beta` |
| `phone-<versionName>` | CastBridge (téléphone) | `phone-1.2.29-beta` |
| `owner-<versionName>` | Console Propriétaire | `owner-0.2.3` |
| `server-<versionName>` | Serveur (ligne `server.versionName`, à créer) ; c'est ce tag que `deploy-server.sh` déploie | `server-1.0.0` |
| `release-AAAA.MM.JJ-N` | Publication coordonnée TV + téléphone + serveur | `release-2026.10.02-1` |

Tags toujours **annotés** (`git tag -a`), jamais déplacés ni supprimés une fois poussés ; une erreur se corrige par une nouvelle version. Détails, modèle de branches et règles de décision : `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`.

## 14. Déploiement du serveur (ajouté le 2026-10-02)

Le répertoire vivant du VPS (`/home/ubuntu/castbridge/services/castbridge/backend`) n'est **pas un clone git** (copie de fichiers du 2026-09-30) : `backend/deploy.sh` (qui fait `git fetch` puis `checkout`) n'y fonctionne pas et la révision en service n'était pas traçable (image étiquetée « unknown »). `tools/release/deploy-server.sh` le remplace sur ce VPS : il pousse le tag vers le dépôt bare `bridge` (`/home/ubuntu/castbridge/castbridge.git`), en extrait une release `/home/ubuntu/castbridge/releases/<tag>-<UTC>`, y copie `.env`, `secrets/`, `geoip/`, `docker-compose.override.yml`, construit l'image avec les étiquettes `org.opencontainers.image.revision` et `.version`, sauvegarde la base, bascule seulement si le healthcheck passe (lien `current`), conserve `castbridge-api:previous` et revient seul en arrière en cas d'échec. Il ne touche ni aux autres conteneurs de l'hôte (`sti-*`, `infra-nginx`, `infra-certbot`) ni à nginx. `backend/deploy.sh` reste valable dans un vrai clone (nouvelle installation, README « Déploiement pas à pas »).

```bash
bash tools/release/deploy-server.sh server-1.0.0            # plan (aucune connexion)
bash tools/release/deploy-server.sh server-1.0.0 --apply    # push vers bridge + déploiement
bash tools/release/deploy-server.sh --status --apply        # révision en service, références du dépôt bare (lecture seule)
bash tools/release/deploy-server.sh --rollback --apply      # release précédente
```

Première exécution et migration depuis la disposition non git : `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md` § 11.

### 14.1 Service de jeu en ligne `castbridge-play` : tag `server-play-<version>` (ajouté le 2026-10-03)

Le service de jeu (`server-play/`) se version et se déploie **indépendamment** de `castbridge-api` : tag annoté `server-play-<version>` (exemple `server-play-0.1.0`), jamais `main`, jamais un commit nu. Un tag `server-<version>` déploie l'API ; un tag `server-play-<version>` déploie le service de jeu, avec l'option `--service play` (sans l'option, un tag `server-play-*` est refusé).

```bash
bash tools/release/deploy-server.sh server-play-0.1.0 --service play            # plan (aucune connexion)
bash tools/release/deploy-server.sh server-play-0.1.0 --service play --apply    # push du tag + déploiement (hors partie : refus si des salles sont ouvertes)
bash tools/release/deploy-server.sh --status --service play --apply             # lecture seule
bash tools/release/deploy-server.sh --rollback --service play --apply           # image et release précédentes
```

Projet compose `castbridge-play` (fichier `backend/docker-compose.play.yml`), image `castbridge-play:candidate` → `:current` (ancienne : `:previous`), liens `~/castbridge/current-play` et `previous-play`, `.env.play` conservé hors dépôt dans `~/castbridge/services/play/`. `castbridge-api`, la base, nginx et les conteneurs de l'autre projet de l'hôte ne sont pas touchés. La route nginx `/play/` reste un acte manuel du propriétaire. Pas à pas : `docs/PLAY-OPS.md` ; test sans connexion : `bash tools/tests/test_deploy_play.sh`.

