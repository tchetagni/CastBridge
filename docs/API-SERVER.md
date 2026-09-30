# API du serveur CastBridge (`backend/`)

Serveur central (Spring Boot + MySQL, en Docker) : mises à jour des apps, banque de questions du quiz, suivi des
appareils, interface d'administration web. Installation et exploitation : [`backend/README.md`](../backend/README.md).

Conventions :

- JSON UTF-8 ; dates ISO 8601 avec le fuseau du Cameroun (`2026-09-30T10:00:00+01:00`).
- Erreurs : `{"status":400,"erreur":"Requête invalide","message":"…","details":["…"],"chemin":"/api/…","date":"…"}`,
  messages en français. `429` (trop de requêtes) porte `Retry-After`.
- Routes publiques limitées par IP (120/min par défaut, rafale 60). CORS fermé.
- Administration : `Authorization: Bearer $TOKEN` (`CASTBRIDGE_ADMIN_TOKEN`). Appareils : `Authorization: Bearer
  <deviceToken>` reçu à l'enregistrement.

```sh
export CB=https://castbridge.exemple.org   # ou http://127.0.0.1:7090 sur le serveur
export TOKEN=...                           # CASTBRIDGE_ADMIN_TOKEN
alias cba='curl -sS -H "Authorization: Bearer $TOKEN"'
```

## 1. Mises à jour des apps

### Publier un APK (admin, aussi utilisé par `.github/workflows/release.yml`)

`POST /api/v1/admin/releases` (multipart) :

| Champ | Valeurs |
|---|---|
| `app` | `tv` \| `phone` |
| `abi` | `armeabi-v7a` \| `arm64-v8a` \| `x86` \| `x86_64` \| `universal` |
| `versionCode`, `versionName` | entier > 0 ; texte (1 à 64 caractères) |
| `channel` | `stable` (défaut) \| `beta` |
| `notes` | notes de version en français (≤ 4000) |
| `mandatory` | `true` \| `false` (défaut) |
| `rollout` | pourcentage de déploiement initial 0..100 (défaut 100) |
| `minSdk` | facultatif (sinon lu dans l'APK) |
| `file` | l'APK (≤ 200 Mo) |

Le serveur calcule SHA-256 et taille, lit le manifeste binaire de l'APK (paquet, versionCode, versionName,
minSdkVersion) et refuse l'envoi si le paquet n'est pas celui de l'app (`castbridge.receiver` / `castbridge.sender`)
ou si le versionCode diffère du formulaire. Manifeste illisible : le formulaire fait foi (`inspection` le dit).
`409` si ce versionCode existe déjà pour cette app/ABI/canal.

```sh
cba -F app=tv -F abi=armeabi-v7a -F versionCode=8 -F versionName=0.6 -F channel=beta -F rollout=20 \
    -F notes="Lecteur plus rapide, corrections Wi-Fi Direct." -F file=@receiver-armeabi-v7a-release.apk \
    $CB/api/v1/admin/releases
# 201 {"id":3,"app":"tv","abi":"armeabi-v7a","channel":"beta","versionCode":8,"versionName":"0.6","sha256":"…",
#      "size":31457280,"rolloutPercent":20,"packageName":"castbridge.receiver","minSdk":26,
#      "downloadPath":"/dl/tv/castbridge-tv-0.6-8-armeabi-v7a-1a2b3c4d.apk","inspection":"vérifié : castbridge.receiver 8 (0.6)",…}
```

Gestion :

```sh
cba "$CB/api/v1/admin/releases?app=tv"                       # liste
cba $CB/api/v1/admin/releases/3                              # détail
cba -X POST "$CB/api/v1/admin/releases/3/rollout?percent=50" # déploiement progressif
cba -X POST $CB/api/v1/admin/releases/3/revoke               # retrait : plus proposée, /dl répond 410
cba -X DELETE $CB/api/v1/admin/releases/3                    # suppression (et du fichier)
cba -X PUT "$CB/api/v1/admin/update-policies/tv/stable?minSupportedVersionCode=7"   # version minimale supportée
cba $CB/api/v1/admin/update-policies
```

### Demander la dernière version (appareils)

`GET /api/v1/updates/{app}/latest?abis=&channel=&versionCode=&deviceId=&sdk=`

- `abis` : `Build.SUPPORTED_ABIS` séparées par des virgules, préférée d'abord (ou `abi` : une seule) ; l'APK de l'ABI
  la mieux classée gagne, l'APK `universal` sert de repli ;
- `channel` : `stable` (défaut) ou `beta` (reçoit aussi les versions stables) ; un canal forcé par l'admin sur
  l'appareil l'emporte ;
- `versionCode` : version installée ; `sdk` : `Build.VERSION.SDK_INT` (écarte les APK dont `minSdk` est plus grand) ;
- `deviceId` : identifiant stable de l'appareil (celui rendu par l'enregistrement), sert au **déploiement
  progressif** : l'appareil reçoit une version à `p %` si `SHA-256(deviceId:app:versionCode)` tombe dans les `p`
  premiers centièmes (stable, et différent d'une version à l'autre). Sans `deviceId`, seules les versions à 100 %.
- En-tête facultatif `Authorization: Bearer <deviceToken>` : appareil bloqué par l'admin → `403`.

Réponses : `204` à jour ; `200` manifeste signé :

```json
{"app":"tv","channel":"stable","abi":"armeabi-v7a","versionCode":8,"versionName":"0.6",
 "url":"https://castbridge.exemple.org/dl/tv/castbridge-tv-0.6-8-armeabi-v7a-1a2b3c4d.apk",
 "sha256":"1a2b3c4d…","size":31457280,"minSdk":26,"notes":"Lecteur plus rapide…","mandatory":false,
 "minSupportedVersionCode":7,"publishedAt":"2026-09-30T10:00:00+01:00","keyId":"9f86d081884c7d65",
 "signature":"base64(64 octets)"}
```

`mandatory` = une version obligatoire se trouve entre la version installée et celle proposée, ou la version
installée est sous `minSupportedVersionCode` (dans ce cas le déploiement progressif ne s'applique pas).

**Signature** : Ed25519 sur le texte UTF-8 suivant (lignes séparées par `\n`, sans `\n` final), reconstruit par
l'app à partir des champs reçus (`castbridge.core.update.UpdateManifest.canonicalPayload`) :

```
castbridge-update-manifest-v1
app=tv
channel=stable
abi=armeabi-v7a
versionCode=8
versionName=0.6
url=https://castbridge.exemple.org/dl/tv/castbridge-tv-0.6-8-armeabi-v7a-1a2b3c4d.apk
sha256=1a2b3c4d…
size=31457280
minSdk=26                       (vide si inconnu)
mandatory=false
minSupportedVersionCode=7
publishedAt=2026-09-30T10:00:00+01:00
notesSha256=<SHA-256 hexadécimal des notes en UTF-8>
```

Clé publique (à embarquer dans `UpdateKeys.PUBLIC_KEY`) :

```sh
curl -s $CB/api/v1/updates/public-key
# {"algorithm":"Ed25519","format":"raw-32-bytes-base64","publicKey":"…","keyId":"9f86d081884c7d65","manifestFormat":"castbridge-update-manifest-v1"}
```

### Télécharger l'APK

`GET|HEAD /dl/{app}/{fichier}` : `Accept-Ranges: bytes`, `ETag: "<sha256>"`, `Content-Length`,
`Cache-Control: public, max-age=31536000, immutable` (un nom de fichier ne change jamais de contenu),
`X-Content-SHA256`. `Range: bytes=N-` → `206` + `Content-Range` ; `If-Range: "<sha256>"` différent → `200` complet ;
plage hors du fichier → `416` ; `If-None-Match` → `304` ; version retirée → `410`.

```sh
curl -C - -o app.apk "$CB/dl/tv/castbridge-tv-0.6-8-armeabi-v7a-1a2b3c4d.apk"    # reprend un téléchargement coupé
```

## 2. Banque de questions du quiz

Format d'échange = celui de la TV (`QuizBank.parse`, format version 2, branche `feat/tv-quiz`) :

```json
{"id":"cm-geo-001","uuid":"cm-geo-001","lang":"fr","track":"general","level":null,"field":null,"region":"CM",
 "category":"Géographie","difficulty":1,"question":"Quelle est la capitale politique du Cameroun ?",
 "choices":["Douala","Garoua","Yaoundé","Bamenda"],"answer":2,
 "explanation":"Yaoundé est la capitale politique ; Douala est la capitale économique.","source":"Constitution de 1996",
 "reviewStatus":"reviewed","status":"approved","review":false,"updatedAt":"2026-09-30T10:00:00+01:00","version":1}
```

- `track` : `general` | `primary` | `secondary` | `higher` ; `level` : `SIL`…`CM2`, `Class 1`…`Class 6`,
  `6e`…`Tle`, `Form 1`…`Form 5`, `Lower Sixth`, `Upper Sixth`, `L1`…`L3` (obligatoire hors culture générale) ;
  `field` (obligatoire au supérieur) : `droit`, `economie`, `mathematiques`, `physique`, `psychologie`, `geographie`,
  `litterature`, `histoire`, `informatique`, `chimie`, `biologie`, `philosophie`, `sociologie` (les accents sont
  acceptés en entrée : « Économie » → `economie`) ; `region` : `CM` | `AF` | `WORLD` ; `difficulty` 1..5 ;
  `answer` = index 0..3 du bon choix.
- Statut serveur `reviewStatus` : `draft` | `reviewed` | `rejected`. Seules les `reviewed` sont servies aux appareils
  (avec `status:"approved"` et `review:false`, ce que la TV attend). En entrée, le statut vient de `reviewStatus`,
  sinon de `status` (`approved` → `reviewed`), sinon de `review` (`false` → `reviewed`).
- Validation stricte : exactement 4 choix non vides et distincts (casse et espaces ignorés), index valide, niveau
  cohérent avec le parcours, explication et source obligatoires, pas de doublon (même texte normalisé, langue,
  parcours, niveau et filière).

### Appareils

`GET /api/v1/quiz/questions?track=&level=&field=&lang=&since=&page=0&size=200` (size ≤ 500) :

```json
{"version":2,"page":0,"size":200,"totalPages":2,"total":318,"syncToken":"2026-09-30T10:00:00.123456+01:00",
 "since":null,"resetRequired":false,"questions":[…],"deleted":[]}
```

Synchronisation incrémentale : garder `syncToken` de la page 0 et le renvoyer en `since` la fois suivante ; la réponse
ne contient alors que les questions modifiées après, et (page 0) `deleted` = identifiants à retirer du cache
(supprimées, rejetées ou repassées en brouillon). `resetRequired:true` = l'appareil n'est pas venu depuis plus
longtemps que la mémoire des suppressions (365 jours) : vider le cache et tout reprendre sans `since`. `ETag` /
`If-None-Match` → `304` si rien n'a changé. La réponse peut être stockée telle quelle par
`CachedQuestionSource.update` de la TV.

```sh
curl -s "$CB/api/v1/quiz/questions?size=500" -o bank.json
curl -s "$CB/api/v1/quiz/questions?since=2026-09-30T10:00:00.123456%2B01:00"
```

`GET /api/v1/quiz/draw?track=general&level=&field=&lang=&count=15&seed=&exclude=id1,id2&shuffle=true` : tirage côté
serveur, difficulté croissante (cible 1 → 5 selon la position), pour `general` répartition 70 % Cameroun / 20 %
Afrique / 10 % monde (±1), reproductible avec `seed`, choix mélangés (l'index `answer` suit), `exclude` = déjà posées
dans la session.

```sh
curl -s "$CB/api/v1/quiz/draw?track=higher&level=L1&field=droit&count=15&seed=42"
# {"seed":42,"track":"higher","level":"L1","field":"droit","count":15,"questions":[…]}
```

Un appareil bloqué (jeton d'appareil en `Authorization`, ou `deviceId`) reçoit `403`.

### Administration

```sh
cba "$CB/api/v1/admin/quiz/questions?status=draft&track=&level=&field=&region=&q=capitale&page=0&size=50"
cba $CB/api/v1/admin/quiz/questions/cm-geo-001
cba -H 'Content-Type: application/json' -d @question.json $CB/api/v1/admin/quiz/questions      # création (draft par défaut)
cba -X PUT -H 'Content-Type: application/json' -d @question.json $CB/api/v1/admin/quiz/questions/cm-geo-001
#   "version" dans le corps = celle lue : 409 si quelqu'un a modifié la question entre-temps
cba -X POST "$CB/api/v1/admin/quiz/questions/cm-geo-001/status?value=reviewed"
cba -X DELETE $CB/api/v1/admin/quiz/questions/cm-geo-001
cba $CB/api/v1/admin/quiz/stats
```

Import en lot, **tout ou rien** (une erreur = rien d'enregistré, `400` avec la liste des erreurs par question) ; les
identifiants existants sont mis à jour, les questions identiques ne changent pas (pas de synchro inutile) :

```sh
cba -H 'Content-Type: application/json' --data-binary @questions.json "$CB/api/v1/admin/quiz/import?dryRun=true"
cba -H 'Content-Type: application/json' --data-binary @questions.json "$CB/api/v1/admin/quiz/import?defaultStatus=draft"
cba -H 'Content-Type: text/csv' --data-binary @questions.csv "$CB/api/v1/admin/quiz/import"
# {"total":120,"created":100,"updated":15,"unchanged":5,"dryRun":false}
cba "$CB/api/v1/admin/quiz/export?format=json&status=reviewed" -o questions.json
cba "$CB/api/v1/admin/quiz/export?format=csv&sep=;" -o questions.csv
```

JSON accepté : `{"version":2,"questions":[…]}` ou un tableau. CSV (séparateur `,` ou `;` détecté sur l'en-tête,
guillemets RFC 4180, UTF-8) :

```
uuid,lang,track,level,field,region,category,difficulty,question,choice1,choice2,choice3,choice4,answer,explanation,source,reviewStatus
```

(`uuid`, `lang`, `track`, `level`, `field`, `reviewStatus` facultatifs ; `answer` = index 0..3.)

Au premier démarrage sur une base vide, la banque embarquée de la TV (`seed/questions.json` et
`seed/questions-school.json`, copiés de `feat/tv-quiz`) est importée : 320 questions, les `review:true` en brouillon.

## 3. Appareils

Chaque app (TV ou téléphone) sur chaque appareil est un enregistrement. Identité : `installId` (UUID tiré au premier
lancement) et, si possible, `androidIdHash` = SHA-256 hexadécimal de `sel_de_l_app + ":" + ANDROID_ID` (jamais la
valeur brute ; le serveur le re-hache avant stockage). Un appareil réinstallé (nouvel `installId`, même ANDROID_ID)
retrouve sa fiche ; l'historique des installations le montre.

`POST /api/v1/devices/register` → `201` :

```sh
curl -s -H 'Content-Type: application/json' $CB/api/v1/devices/register -d '{
  "installId":"3b241101-e2bb-4255-8caf-4136c566a962","androidIdHash":"<64 hex>","app":"tv",
  "versionCode":7,"versionName":"0.5","channel":"stable","abi":"armeabi-v7a","supportedAbis":["armeabi-v7a","armeabi"],
  "sdk":34,"platform":"android-tv","manufacturer":"Hisense","model":"43A4K","deviceName":"TV salon",
  "osName":"GaiaOS","osBuild":"<Build.DISPLAY>","fingerprint":"<Build.FINGERPRINT>","screen":"1920x1080","densityDpi":320,
  "ramTotalMb":1536,"storageFreeMb":2048,"storageTotalMb":8192,"usbPresent":true,"usbFreeMb":30000,
  "btGateway":true,"sshEnabled":false,"wifiDirect":false,"videoCount":12,"lastError":null}'
# {"deviceId":"5e0c…","deviceToken":"<secret, 43 caractères>","heartbeatSeconds":900,
#  "directives":{"serverTime":"…","checkUpdate":false,"blocked":false,"channel":"stable","heartbeatSeconds":900}}
```

Tous les champs sauf `installId` et `app` sont facultatifs. `platform` : `android-tv`, `google-tv`, `fire-os`,
`android-box`, `phone`, `tablet`, `other` (détectée par l'app : `castbridge.core.device.Platform`). Chaque
enregistrement renvoie un **nouveau** jeton (l'ancien ne vaut plus rien).

`POST /api/v1/devices/heartbeat` (au démarrage, après une mise à jour, puis toutes les 15 min), même corps (ou `{}`),
`Authorization: Bearer <deviceToken>` → `200` :

```json
{"serverTime":"2026-09-30T10:15:00+01:00","checkUpdate":true,"blocked":false,"channel":"beta","heartbeatSeconds":900}
```

`checkUpdate` : l'admin demande une vérification de mise à jour (donné une seule fois) ; `blocked` : plus de mises à
jour ni de quiz ; `channel` : canal à utiliser. `401` = jeton inconnu (appareil oublié par l'admin, réinstallation) :
se réenregistrer (`DeviceClient` le fait seul).

`POST /api/v1/devices/crash` (`Authorization: Bearer <deviceToken>`) :
`{"message":"NullPointerException dans le lecteur","detail":"<haut de la pile, ≤ 4000>","versionCode":8}` → `204`.

Rétention : heartbeats détaillés 30 jours (puis seul l'agrégat par jour reste), IP brute 30 jours, plantages
180 jours, appareils sans nouvelles depuis 365 jours oubliés. « En ligne » = contact depuis moins de 15 minutes.
Pays (et ville avec une base GeoLite2 locale) déduits de l'IP publique, sans service externe.

Administration (mêmes fonctions que l'interface web) :

```sh
cba "$CB/api/v1/admin/devices?state=online&platform=android-tv&manufacturer=Hisense&abi=armeabi-v7a&q=salon&sort=seen&page=0"
cba $CB/api/v1/admin/devices/stats          # total, en ligne, versions, pays, plateformes
cba $CB/api/v1/admin/devices/<deviceId>     # fiche + jours + versions + installations + plantages
cba -X PUT -H 'Content-Type: application/json' -d '{"label":"Salon Esaie","group":"Famille","note":"TV du salon"}' $CB/api/v1/admin/devices/<deviceId>
cba -X POST $CB/api/v1/admin/devices/<deviceId>/check-update
cba -X POST "$CB/api/v1/admin/devices/<deviceId>/block?blocked=true"
cba -X POST "$CB/api/v1/admin/devices/<deviceId>/channel?channel=beta"     # vide = choix de l'app
cba -X DELETE $CB/api/v1/admin/devices/<deviceId>
```

## 4. Divers

- `GET /admin` : interface d'administration web (connexion par identifiant/mot de passe, voir `backend/README.md`).
- `GET /v3/api-docs` : description OpenAPI, avec le jeton admin seulement.
- Santé : `/actuator/health/liveness` et `/actuator/health/readiness` sur le port interne 8081 du conteneur
  (non publié ; utilisé par le healthcheck Docker et `deploy.sh`).
