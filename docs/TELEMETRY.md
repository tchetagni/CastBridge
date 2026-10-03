# Télémétrie d'usage et indicateurs (KPI)

Objectif : savoir **quelles fonctionnalités sont les plus utilisées**, comment le parc évolue et où sont les
problèmes, sans jamais collecter de contenu ni de données d'identité. Côté serveur : `backend/` (paquet
`castbridge.server.telemetry`, pages `/admin` et `/admin/kpi`). Côté apps : `android/core`, paquet
`castbridge.core.telemetry` (logique pure et testée ; l'intégration Android est à faire dans `:receiver` / `:sender`).

## 1. Identification de l'appareil

Chaque installation tire un `installId` (UUID v4) au premier lancement ; l'app envoie aussi `androidIdHash` =
SHA-256(sel de l'app + ":" + ANDROID_ID), jamais l'ANDROID_ID brut ; le serveur le re-hache avant de le stocker.
L'enregistrement (`POST /api/v1/devices/register`) rend un `deviceId` et un `deviceToken` secret, qui authentifie
les heartbeats, les plantages et les lots d'événements. Détails : `docs/API-SERVER.md` §3.

**Le nom que l'utilisateur a donné à son appareil (`deviceName`, ex. « TV de Paul ») n'est ni envoyé ni conservé** : c'est un texte
libre qui peut contenir un prénom. Les apps envoient `null` ; si une ancienne version l'envoie encore, le serveur l'ignore sans
erreur et ne le stocke pas. L'admin affiche l'étiquette qu'il a choisie, sinon fabricant + modèle.

## 2. Consentement (deux niveaux)

| Niveau | Contenu | Base | Par défaut |
|---|---|---|---|
| **Essentiel** | identification de l'appareil (identifiants ci-dessus), modèle, système, versions installées, stockage, état des fonctions, erreurs et plantages (`error`, `crash`), installations de mises à jour (`update_install`), heartbeats | nécessaire au fonctionnement des mises à jour et à la maintenance (intérêt légitime / exécution du service) | toujours |
| **Statistiques d'usage** | tous les autres événements du catalogue (sessions, écrans, fonctionnalités, envois, lectures, quiz, échecs, téléchargements, passerelle, tests de connexion) | consentement | **désactivé** tant que l'utilisateur ne l'a pas accepté sur l'écran d'information |

- L'app envoie son choix dans le rapport d'appareil : `"consent":"usage"` ou `"essential"`, avec
  `"consentVersion"` (version du texte affiché, ex. `2026-10`). Le serveur note la date du choix.
- Sans « statistiques d'usage », le client ne met en file que les événements essentiels
  (`castbridge.core.telemetry.Telemetry`) et le serveur **refuse** tout autre événement de cet appareil
  (motif « statistiques d'usage non consenties »).
- Le choix se modifie à tout moment dans les réglages de l'app (retrait : plus rien n'est collecté à partir de là ;
  l'utilisateur peut aussi demander l'effacement, §6).

## 3. Format des événements

`POST /api/v1/events/batch`, `Authorization: Bearer <deviceToken>`, `Content-Encoding: gzip` recommandé, au plus
500 événements et 2 Mo décompressés par lot :

```json
{"app":"tv","versionCode":8,"events":[
  {"id":"3f2a…(UUID)","ts":1759219200000,"sessionId":"9c1e…(UUID)","name":"feature_used","versionCode":8,
   "props":{"feature":"quiz","source":"tile"}}
]}
```

Réponse `200` : `{"accepted":1,"duplicates":0,"rejected":0,"errors":[{"id":"…","reason":"…"}],"serverTime":"…"}`.

- **Rejouable** : chaque événement a un UUID ; le serveur ignore ceux qu'il a déjà (`duplicates`). Un lot n'est
  retiré de la file de l'appareil qu'après la réponse du serveur.
- Horodatage : `ts` de l'appareil (millisecondes) et heure du serveur à la réception ; si l'horloge de l'appareil
  est à plus de 30 jours ou dans le futur, le jour de rattachement est celui du serveur.
- Schéma versionné (`EventCatalog.SCHEMA_VERSION` = 1). Propriétés : **liste blanche par événement** (les autres clés
  sont ignorées), valeurs scalaires seulement, textes courts (codes ≤ 64 caractères `[A-Za-z0-9_.:+/-]`, messages
  ≤ 200 caractères nettoyés des chemins, URL et noms de fichiers médias), nombres bornés et positifs.
- **Clés interdites** (l'événement entier est refusé, sur l'appareil comme sur le serveur) : `filename`, `file`,
  `title`, `path`, `url`, `uri`, `email`, `phone`, `contact(s)`, `password`, `pin`, `key`, `token`, `secret`, `lat`,
  `lon`, `latitude`, `longitude`, `gps`, `location`, `ip`, `ssid`, `bssid`, `mac`, `imei`, `serial`, `account`,
  `user`, `username`, `name`.
- Compter des éléments distincts (vidéos différentes…) sans les nommer : `Telemetry.hashForCounting(valeur, sel)`
  avec un sel aléatoire propre à l'appareil, jamais envoyé.

File locale (`EventQueue`) : fichier JSON-lines borné à 2 Mo (les plus anciens partent en premier), conservé entre
les redémarrages ; envoi (`TelemetryUploader.flush`) toutes les 15 minutes, au retour du réseau, ou quand la
passerelle Bluetooth du téléphone est disponible (proxy SOCKS local possible).

## 4. Catalogue des événements

| Événement | Propriétés | Niveau |
|---|---|---|
| `session_start` | — | usage |
| `session_end` | `ms` (durée de la session) | usage |
| `screen_view` | `screen` | usage |
| `screen_time` | `screen`, `ms` (temps passé sur l'écran) | usage |
| `feature_used` | `feature`, `source` = `tile` \| `menu` \| `phone` \| `remote` \| `shortcut` \| `notification` | usage |
| `cast_start` | `channel` = `wifi` \| `bluetooth` \| `wifidirect` \| `dlna`, `mode` = `copy` \| `move` \| `direct`, `bytes` | usage |
| `cast_end` | idem + `ms`, `kbps` (débit moyen), `ok`, `error` (code court) | usage |
| `playback_start` | `codec`, `resolution`, `hw` (décodage matériel), `source` = `internal` \| `usb` \| `stream` \| `phone` \| `dlna` | usage |
| `playback_end` | `ms` (durée regardée), `pct` (% vu), `codec`, `resolution`, `hw`, `source`, `abandoned`, `ok`, `error` | usage |
| `library_stats` | `files`, `bytes` | usage |
| `quiz_game` | `mode`, `duel` (format), `track`, `level`, `field`, `players`, `score`, `ms`, `jokers` | usage |
| `quiz_answer` | `question` (id), `correct`, `ms` (temps de réponse) | usage |
| `chess_game` | `mode`, `ai_level`, `time_control`, `result` = `win` \| `loss` \| `draw` \| `abandon`, `moves`, `ms` | usage |
| `sudoku_game` | `level` = `EASY` \| `MEDIUM` \| `HARD` \| `EXPERT`, `ms` (temps de jeu), `result` = `win` \| `abandon`, `hints` (0-3) | usage |
| `download` | `type` = `http` \| `magnet` \| `torrent`, `bytes`, `ms`, `ok`, `error` | usage |
| `gateway_session` | `ms`, `bytes` (passerelle Internet Bluetooth) | usage |
| `connectivity_check` | `via` = `wifi` \| `bluetooth` \| `ethernet` \| `wifidirect` \| `none`, `ok`, `latency_ms` | usage |
| `content_stat` | `kind` = `question` \| `lesson` \| `exercise`, `item` (id), `shown`, `correct`, `ms` (temps total), `reports` : totaux par élément depuis le dernier envoi, **identifiants et nombres seulement** (`docs/CONTENT-VALIDATION.md` § 5) | usage |
| `rental_start` | `bundle` (code public du catalogue), `unit` = `hours` \| `days` \| `default`, `amount`, `maxMinutes` | usage |
| `rental_use` | `bundle`, `unit`, `minutes` : agrégé **par jour et par contrat sur l'appareil, jamais par minute** ; jamais d'identifiant de contrat | usage |
| `rental_end` | `bundle`, `unit`, `reason` = `usage` \| `date` \| `over_limit`, `usedMinutes`, `maxMinutes` | usage |
| `rental_extend` | `bundle`, `unit`, `amount` | usage |
| `rental_survey` | `unit`, `q`, `answer` : réponse facultative de fin de location, une fois par contrat, **jamais sous profil enfant**, sans contrat | usage |
| `update_install` | `from`, `to` (versionCode), `ok`, `error` | essentiel |
| `error`, `crash` | `screen`, `type`, `message` (≤ 200, nettoyé) | essentiel |

**Signalements de contenu** (« Signaler une erreur ») : ce n'est **pas** un événement mais `POST /api/v1/content/reports` (jeton de l'appareil) ;
catégorie **essentielle** (geste volontaire de l'utilisateur, envoyé même sans accord pour les statistiques) : identifiant de l'élément,
empreinte, lot, motif, texte facultatif de 200 caractères nettoyé, canal. Voir `docs/CONTENT-VALIDATION.md` § 4. Le texte du § 7
(`ConsentText`, version `2026-11`) le mentionne et demande de ne mettre aucune donnée personnelle dans le texte libre.

### Identifiants de fonctionnalités (liste fermée)

Les écrans utilisent les mêmes identifiants (`screen_view` / `screen_time`), plus `home`, `onboarding`, `player`,
`privacy`. Un identifiant inconnu est refusé.

| App TV (`tv`) | | App téléphone (`phone`) | |
|---|---|---|---|
| `library` | Bibliothèque | `send` | Envoyer |
| `quiz` | Quiz | `move` | Déplacer |
| `chess` | Échecs | `watch_on_tv` | Regarder sur la TV |
| `receive` | Recevoir du téléphone | `tv_library` | Bibliothèque TV |
| `usb` | Clé USB | `file_exchange` | Échange de fichiers |
| `bluetooth` | Bluetooth | `remote` | Télécommande |
| `internet` | Internet / test | `player` | Lecteur / Ouvrir avec |
| `wifi_direct` | Wi-Fi Direct | `cast` | Caster |
| `admin` | Administration | `quiz` | Quiz |
| `downloads` | Téléchargements | `chess` | Échecs |
| `updates` | Mises à jour | `bt_gateway` | Passerelle Bluetooth |
| `settings` | Réglages | `downloads`, `updates`, `settings` | Téléchargements, Mises à jour, Réglages |

Ajouter une fonctionnalité : l'ajouter aux deux catalogues (`backend/.../EventCatalog.java` et
`android/core/.../telemetry/Telemetry.kt`) et à ce tableau, puis déployer le serveur **avant** les apps.

## 5. Indicateurs dans `/admin`

- **Accueil — « Fonctionnalités les plus utilisées »** (en premier) : TV et téléphone ensemble ou séparés ; pour
  chaque fonctionnalité : utilisations, appareils distincts (% du parc actif), temps total et moyen par appareil,
  tendance par rapport à la période précédente de même durée ; barres triées et courbe par semaine des 6 premières ;
  filtres période / version / plateforme / pays / modèle / groupe ; export CSV.
- **`/admin/kpi`** : Parc (DAU / WAU / MAU, nouveaux, perdus, rétention J1 / J7 / J30 par cohorte hebdomadaire,
  répartitions versions / plateformes / fabricants / ABI / résolutions / pays), Usage (sessions par jour, durée
  moyenne, écrans les plus vus, entonnoir « première connexion → premier envoi → première lecture »), Envois
  (nombre, volume, débit moyen et taux d'échec par canal, échecs par modèle), Lecture (heures, % moyen vu, codecs,
  résolutions, erreurs par modèle), Quiz (parties par jour, joueurs moyens, modes, taux de bonnes réponses, questions
  trop faciles ≥ 90 % / trop difficiles ≤ 25 % avec au moins 10 réponses, temps de réponse moyen), Échecs,
  Téléchargements, Mises à jour (adoption par version, échecs d'installation), Qualité (plantages par version et
  modèle, part d'appareils sans erreur), Connectivité (part d'appareils sans Internet, usage de la passerelle
  Bluetooth). Mêmes filtres, export CSV par section. Même contenu en JSON : `GET /api/v1/admin/kpi/{section}` et
  `/api/v1/admin/kpi/features` (jeton admin).
- **Fiche appareil** : chronologie de ses 200 derniers événements.

Calcul : tables d'agrégats mises à jour à chaque lot (`kpi_device_day` : activité et sessions par appareil et par
jour — alimentée aussi par les heartbeats, donc tout le parc compte pour DAU/MAU ; `kpi_feature_day` : utilisations,
affichages et temps par fonctionnalité ; `kpi_question` : réponses par question ; `kpi_event_day` : compteurs
anonymes par événement et dimensions) ; les sections détaillées lisent les événements bruts de la période (index
`(name, stat_day)`). Job nocturne (03:45, heure de Douala) : purge et reconstruction des compteurs anonymes
récents à partir des événements bruts.

## 6. Durées de conservation et droits

| Donnée | Durée |
|---|---|
| Événements bruts | 13 mois (propriété `castbridge.telemetry.raw-retention-days`, défaut 395), puis **supprimés par une tâche planifiée** chaque nuit à 03:30 (heure de Douala), par lots de 10 000 (seuls restent les agrégats) |
| Agrégats par appareil (`kpi_device_day`, `kpi_feature_day`) | 25 mois |
| Compteurs anonymes (`kpi_event_day`, `kpi_question`) | sans limite (aucun identifiant d'appareil) |
| Heartbeats détaillés | 30 jours, puis agrégat journalier |
| Adresse IP brute | 30 jours au plus (pays / ville approximatifs conservés) |
| Plantages | 180 jours |
| Appareil sans contact | oublié après 365 jours |

- **Droit d'accès** : `GET /api/v1/devices/me` (jeton d'appareil) rend la fiche de l'appareil et ses événements ;
  l'app peut l'afficher ou le partager (`DeviceClient.myData()`).
- **Droit à l'effacement** : depuis l'app, `DELETE /api/v1/devices/me` (`DeviceClient.eraseMe()`), ou par
  l'administrateur (fiche appareil → « Effacer l'appareil et ses données », ou `DELETE /api/v1/admin/devices/{id}`) :
  la fiche, l'historique, les événements et les agrégats de l'appareil sont supprimés en cascade. L'app doit aussi
  vider sa file locale et tirer un nouvel `installId`.
- Jamais collectés : noms de fichiers ou titres de vidéos en clair, contenus, contacts, mots de passe, PIN, clés,
  positions GPS, IP au-delà de 30 jours.

## 7. Conformité

La loi camerounaise n° 2024/017 du 23 décembre 2024 relative à la protection des données à caractère personnel
s'applique (et le RGPD pour d'éventuels utilisateurs dans l'Union européenne). Points pris en compte :
information préalable (écran au premier lancement), finalités déterminées, consentement distinct pour les
statistiques d'usage, minimisation (pseudonymes, pas de contenu), durées limitées, sécurité (TLS via nginx, jetons
hachés, base non exposée), droits d'accès et d'effacement. **À faire valider par un juriste** : qualification exacte
des traitements essentiels, éventuelles formalités auprès de l'autorité de protection des données prévue par la loi,
transferts hors du Cameroun (le VPS est hébergé à l'étranger : à vérifier), désignation d'un contact.

### Proposition de texte d'information (à relire par un juriste)

> **CastBridge et vos données**
>
> CastBridge envoie à son serveur des informations techniques sur cet appareil pour vous proposer les mises à jour
> et corriger les problèmes. Nous ne collectons jamais le contenu de vos fichiers, les noms ou titres de vos vidéos,
> vos contacts, vos mots de passe ni votre position.
>
> **Toujours (nécessaire au fonctionnement)** : un identifiant technique de l'appareil (tiré au hasard, et une
> empreinte non réversible de l'identifiant Android), le modèle et le système de l'appareil, les versions
> installées, l'espace de stockage, les erreurs et plantages, le résultat des mises à jour. Adresse IP : utilisée
> pour déterminer le pays approximatif, effacée après 30 jours.
>
> **Seulement si vous l'acceptez (statistiques d'usage)** : les fonctionnalités utilisées et le temps passé, les
> envois vers la TV et les lectures (durées, formats, réussite), les parties de quiz et d'échecs, les
> téléchargements et l'usage de la passerelle Bluetooth. Elles nous servent à savoir ce qui est utile et à améliorer
> l'application. Vous pouvez changer d'avis à tout moment dans les Réglages.
>
> **Durée** : 13 mois pour le détail, puis des statistiques globales ; les appareils inactifs depuis un an sont
> effacés.
>
> **Vos droits** : consulter les données de cet appareil, les faire effacer (Réglages → Confidentialité), retirer
> votre accord. Contact : [adresse e-mail du responsable à compléter]. Responsable du traitement : [nom / structure à
> compléter]. Vous pouvez aussi saisir l'autorité de protection des données à caractère personnel compétente.
>
> [Accepter les statistiques d'usage] [Seulement l'essentiel]
