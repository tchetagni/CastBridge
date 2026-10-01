# Contrôle parental

Branche `feat/parental` (partie de `feat/ssh`). Réécriture propre : la première version de `wip/external-ai-changes` n'a servi que de spécification fonctionnelle (l'audit `docs/AUDIT-EXTERNAL-CHANGES.md`, constat S6, la jugeait « À corriger »). Rien n'en est repris tel quel.

**Tout reste sur la TV** (et sur le téléphone du parent, pour l'affichage et pour les rapports que la TV lui livre par Bluetooth, jamais par un serveur). Aucune donnée parentale n'est envoyée au serveur : aucun appel `TvConnect`, aucun événement de statistiques, aucune tuile comptée. Les profils, règles, classements et rapports n'existent que dans les préférences privées de l'app TV.

## Ce que fait le contrôle

| Fonction | Où |
|---|---|
| Code parental de 4 à 6 chiffres, créé par le parent, distinct du code de la TV | TV (tuile « Contrôle parental »), téléphone (Réglages) |
| Profils d'enfants (prénom, tranche d'âge : -12 / 12-15 / 16-17 / adulte), import des élèves d'Apprendre | TV, téléphone |
| Classement des vidéos : tous publics, -12, -16, adulte, par vidéo, par mot-clé du nom, ou par stockage (TV / clé USB) ; « non classée » = adulte par défaut | TV (actions d'une vidéo : « Classer… », écran « Classer les vidéos »), téléphone |
| Vidéo au-delà de l'âge : masquée de la bibliothèque, ou visible mais verrouillée par le code | TV, téléphone |
| Heures autorisées et durée quotidienne par profil, sur la lecture, les jeux, les téléchargements (au choix) ; avertissement 5 min avant la fin | TV, téléphone |
| Blocage par catégorie : Jeux, Téléchargements, Internet/tests, Administration, SSH, Réglages | TV, téléphone |
| Mode enfant : accueil simplifié (Apprendre, jeux autorisés, bibliothèque filtrée, Aide, porte « Contrôle parental »), le code de connexion de la TV y est masqué | TV |
| Déverrouillage parent (30 min par défaut) : les règles sont suspendues | TV (écran verrouillé), téléphone |
| Rapport d'activité : minutes par jour et par profil, contenus bloqués récemment | téléphone (et TV), local |
| **Surveillance de toute la TV** : temps par application (YouTube, Netflix, navigateur, jeux…), règles par application, nouvelles applications, état réel affiché | TV (section « Surveillance de toute la TV »), téléphone |
| **Rapports envoyés au téléphone du parent** : résumé du jour, de la semaine, alertes immédiates, par Bluetooth | TV (désignation), téléphone (réception, notification, historique) |

Apprendre et la navigation (accueil, RETOUR) ne sont jamais bloquables.

## Sécurité

- **Aucun code par défaut.** Tant que le parent n'a pas créé de code, le contrôle est inactif. Le code est refusé s'il est vide, hors de 4 à 6 chiffres, un chiffre répété (4444) ou une suite (1234).
- **Stockage** : PBKDF2-HMAC-SHA256, sel aléatoire de 16 octets, 60 000 itérations (`pbkdf2-sha256$iter$sel$hash`). Jamais en clair, jamais journalisé. Comparaison à temps constant. Un code de 4 à 6 chiffres reste devinable hors ligne par quelqu'un qui lit le stockage privé de l'app (root) : le hachage protège des journaux, sauvegardes et lectures simples ; en ligne, c'est le verrouillage qui protège.
- **Verrouillage progressif, persistant** (survit au redémarrage et à la fermeture de l'app) : 5 échecs → 1 min, puis 5 min, 15 min, 1 h (palier conservé). Pendant le verrou, même le bon code est refusé. Global (pas par adresse IP). Un réglage de l'heure en arrière ne rallonge pas le verrou au-delà d'une heure ; une avance de l'horloge le raccourcit (les Réglages de la TV sont bloquables pour l'enfant).
- **Réinitialisation** : seulement par l'administrateur de la TV (code de connexion à 6 chiffres, sur la TV ou via l'API avec `{"confirm":"RESET"}`) ou par l'effacement des données de l'app. Elle efface le code **et désactive** le contrôle (profils et règles gardés) ; l'écran prévient clairement. Les essais du code administrateur sur l'écran TV ont leur propre verrouillage progressif.
- **L'administrateur peut toujours désactiver** le contrôle (`POST /api/parental/disable`, code de la TV seul).
- **API** : toutes les routes sont derrière le code de la TV (en-tête `X-CB-Pin`, déjà imposé par le serveur) **et**, pour les actions sensibles, le code parental. Le code parental voyage **uniquement dans le corps JSON d'un POST**. Tout paramètre d'URL dont le nom contient « pin » (ou `new`, `old`, `code`) est refusé en 400, même avec la bonne valeur. Les réponses sont en JSON strict.
- BACK et HOME ne sont jamais interceptés. Un écran verrouillé propose toujours « Saisir le PIN parental » et « Retour à l'accueil » (et RETOUR y mène).
- `ParentalActivity`, `ParentalLockActivity` et `SupervisionSetupActivity` sont `exported=false`. Une seule permission ajoutée pour la surveillance de toute la TV : `PACKAGE_USAGE_STATS` (accès spécial, accordé par l'utilisateur ou par adb), plus des déclarations `<queries>` (applications avec icône de lanceur, accueil, réglages). Le service d'accessibilité utilisé en option est celui de la télécommande, déjà présent.
- Le code de connexion de la TV n'est plus affiché (tuile, bandeau, aide, réglages) tant qu'un profil enfant est actif (sinon un enfant le lirait à l'écran et pourrait réinitialiser).

## Routes (`/api/parental`)

| Route | Corps | Effet |
|---|---|---|
| `GET /api/parental` | — | état sans secret : code défini ?, activé ?, profil actif, verrou, séance parent |
| `POST /pin/create` | `{pin}` | premier code seulement |
| `POST /pin/change` | `{pin, new}` | |
| `POST /unlock`, `/lock` | `{pin}` / `{}` | ouvre / ferme la séance parent sur la TV |
| `POST /config/get` | `{pin}` | configuration + élèves d'Apprendre importables |
| `POST /config/set` | `{pin, rev, config}` | remplace la configuration (409 si `rev` périmé) |
| `POST /report` | `{pin, days}` | rapport local |
| `POST /history/clear` | `{pin}` | |
| `GET /supervision` | — | état réel de la surveillance de toute la TV + aide à la mise en place (sans secret) |
| `POST /apps/list` | `{pin}` | applications de la TV (nouvelles signalées) + réglages par application |
| `POST /apps/rules/set` | `{pin, rev, settings}` | `supervise`, `newApp`, règles par profil, `reviewed` (409 si `rev` périmé) |
| `POST /reports/config/get` | `{pin}` | options des rapports, téléphones de confiance (identifiant opaque `id`, jamais l'adresse Bluetooth), désignés ou non |
| `POST /reports/config/set` | `{pin, rev, config}` | heure du résumé, jour de la semaine, options par profil (409 si `rev` périmé) |
| `POST /reports/recipients/add` / `remove` | `{pin, phoneId}` | désigne / retire un téléphone **de confiance** (code parental obligatoire) |
| `POST /reports/now` | `{pin}` | prépare le résumé du jour de chaque profil (test de la liaison) |
| `POST /disable` | `{}` | administrateur (code TV) |
| `POST /reset` | `{"confirm":"RESET"}` | administrateur (code TV) : efface le code et désactive |

Erreurs : 403 `{error, attemptsLeft}` code incorrect ; 429 `{error, retryAfter}` verrouillé ; 400 invalide ; 409 conflit.

## Architecture

- `core/.../parental/` (JVM, testé) : `ParentalPin.kt` (hachage, verrou), `ParentalModel.kt` (profils, règles, catégories, décisions pures), `ParentalEngine.kt` (code, configuration, usage du jour, rapport, décisions), `ParentalApi.kt` (routes), `ParentalClient.kt` (côté téléphone).
- `core/.../parental/` (suite) : `AppModel.kt` (états d'application, catégories, règles par profil, décision pure `AppRules.decide`), `Supervision.kt` (état réel, suivi du premier plan, évènements), `ParentalReports.kt` (destinataires, outbox, signature, corps des rapports, planificateur), `ParentalSync.kt` (message `CBTP`, boîte de réception du téléphone, textes).
- `receiver/` : `ForegroundWatcher` (détecteur), `AppCatalog` (PackageManager), `SupervisionSetupActivity` (mise en place guidée) ; `ParentalHub` pilote le tout (voir la section ci-dessous).
- `sender/` : `ParentalInbox` (réception, stockage, notification, tâche périodique), `ParentalWholeTv.kt` (panneau « Toute la TV » dans `ParentalScreen.kt`).
- `receiver/` : `ParentalHub` (colle Android : rappels d'activité, compteur de temps toutes les 15 s, garde de lecture, filtre de bibliothèque), `ParentalActivity` (réglages TV), `ParentalLockActivity` (écran verrouillé), `ParentalUi` (clavier numérique à la télécommande, lignes).
- Modifications minimales des fichiers partagés : `TvApp` (2 lignes), `TvService` (gardes `play*`, routes), `PlayerActivity` (tuile, enveloppes de tuiles et de menus, filtre, masquage du code), `LibraryScreen` (PIN avant lecture d'une vidéo verrouillée, action « Classer »), manifeste, icône. Téléphone : écran dédié `ParentalActivity` (cadenas dans la barre du haut, ou lien dans Réglages), contenu `ParentalScreen.kt`, masqué des captures (FLAG_SECURE).
- Points d'application : une activité de catégorie bloquée est fermée au moment où elle reprend la main (couvre aussi les ouvertures depuis le téléphone) ; le pont de lecture de la TV refuse les vidéos (bibliothèque TV, téléphone, liste de lecture) ; le compteur arrête la lecture et affiche l'écran verrouillé à la fin du temps ou des heures.

## Limites (à dire aux parents)

- **Sans la surveillance de toute la TV** (désactivée par défaut), un enfant peut contourner en changeant d'application (YouTube, Netflix, lecteur externe, autre app du système) : CastBridge-TV ne contrôle alors que ce qui se passe dans CastBridge-TV. **Avec** elle, voir les limites de la section « Surveillance de toute la TV » (elle dépend d'autorisations que l'enfant peut retirer).
- Sans la surveillance de toute la TV, le temps d'écran ne compte que le temps passé **dans CastBridge-TV** (vidéo en lecture, jeux ouverts, écran Téléchargements). Apprendre n'est pas compté ni bloqué.
- Le classement est manuel ; il suit le **nom du fichier** : renommer une vidéo la remet « non classée » (adulte par défaut).
- Un téléphone qui connaît le code de la TV voit toute la bibliothèque (`/api/library`) : le filtre s'applique à l'écran de la TV et à la lecture, pas à la liste envoyée au téléphone.
- Un enfant qui a accès au code de connexion de la TV (autre écran, autre téléphone appairé) peut réinitialiser le code parental.
- Le verrou s'appuie sur l'horloge de la TV.
- Sauvegarde Android (`allowBackup`) : le code haché et la configuration peuvent être sauvegardés avec l'app selon le réglage système.

## Tests

`gradle :core:test --tests 'castbridge.core.Parental*'` (aussi `ParentalAppsTest`, `ParentalReportsTest`) : hachage et comparaison, verrouillage progressif et sa persistance (redémarrage), réinitialisation administrateur, règles horaires (fenêtre passant minuit) et quota, avertissement 5 min, filtrage par classification, catégories, mode enfant, « BACK/HOME jamais bloqués », API refusant code vide/par défaut/en URL, serveur réel + client téléphone. Captures d'émulateur : `docs/img/parental/` (TV 1280x720 à 160 dpi et 1920x1080, téléphone).

## Surveillance de toute la TV

Jusqu'ici le contrôle ne voyait que CastBridge-TV. La surveillance de toute la TV (réglage **désactivé par défaut**, à activer par le parent avec son code) compte aussi le temps passé dans **n'importe quelle autre application** (YouTube, Netflix, navigateur, jeux, applications du système), le fait entrer dans la durée quotidienne et les heures autorisées du profil, applique des règles par application et peut bloquer. Tout reste local : aucune donnée ne part vers un serveur.

### Comment la TV sait quelle application est au premier plan

| Détecteur | Principe | Avantages | Limites |
|---|---|---|---|
| **Statistiques d'utilisation** (`UsageStatsManager`, accès spécial `PACKAGE_USAGE_STATS`) — principal | une petite requête des évènements « application passée au premier plan / en arrière-plan » toutes les 4 s, uniquement écran allumé et surveillance activée ; l'état est reconstruit par `ForegroundTracker` (testé) | léger (une requête, pas de boucle lourde : adapté au boîtier 32 bits à peu de RAM), ne lit rien du contenu | délai de quelques secondes ; l'écran de réglages « Accès aux données d'utilisation » manque ou est caché sur certaines TV |
| **Service d'accessibilité** de la télécommande (`RemoteAccessibilityService`) — optionnel | reçoit le **nom du paquet** de la fenêtre qui vient de s'ouvrir (rien d'autre : ni texte, ni contenu) | immédiat ; seule solution si l'écran « données d'utilisation » n'existe pas ; sert aussi à renvoyer à l'accueil de façon fiable | l'utilisateur doit l'activer ; l'enfant peut le désactiver ; les fenêtres du système (barre système, clavier, demandes de permission) sont ignorées pour ne pas masquer l'application dessous |

Le signal d'accessibilité l'emporte quand il est connecté et plus récent que le dernier changement vu par les statistiques. Les deux se complètent ; aucun n'est « sûr » contre un enfant qui a accès aux Réglages.

### Mise en place (écran « Surveillance de toute la TV » sur la TV)

Tuile « Contrôle parental » > « Surveillance de toute la TV » (ou depuis la section « Toute la TV »). L'écran affiche l'état réel et trois étapes, chacune avec un lien direct vers l'écran Android quand il existe :

1. **Accès aux données d'utilisation** : Réglages > Applications > Accès spécial > Accès aux données d'utilisation > CastBridge-TV > Autoriser.
2. **Afficher par-dessus les autres applications** (Android 10 et plus) : nécessaire pour que l'écran « Accès protégé » apparaisse devant une autre application.
3. *(Facultatif)* **Détecteur rapide** : service d'accessibilité de la télécommande (écran d'aide existant).

Si l'écran d'une étape n'existe pas sur la TV, l'application l'indique et affiche la commande **adb** exacte (le nom du paquet est celui de l'application installée) :

```
adb shell appops set <paquet> GET_USAGE_STATS allow
adb shell appops set <paquet> SYSTEM_ALERT_WINDOW allow
```

Puis le parent active la surveillance (code parental demandé, même pendant une séance parent) et vérifie l'état.

**État affiché** (TV, API `GET /api/parental/supervision`, téléphone) — jamais plus optimiste que la réalité :

- « Surveillance de toute la TV : **active** » : un détecteur fonctionne ET l'écran verrouillé peut être amené devant une autre application (accès affichage par-dessus, ou accessibilité), ET la boucle de détection tourne.
- « … **non autorisée** » : surveillance demandée mais accès retiré/non accordé, ou affichage par-dessus non autorisé, ou détecteur arrêté (la raison est affichée).
- « … **indisponible** » : la TV n'a pas de statistiques d'utilisation et le service d'accessibilité n'est pas connecté.
- « … **désactivée** ».

### Règles par application

Par profil, pour chaque application avec icône de lanceur (TV, y compris le lanceur « leanback », ou téléphone) : **Autorisée**, **Bloquée**, **Durée limitée** (minutes par jour sur cette application), **Code parental requis** (le code ouvre cette seule application pour la durée d'une séance parent). Sans règle : autorisée.

- **Jamais bloquables** (grisées dans les éditeurs) : CastBridge-TV lui-même (donc Apprendre et la navigation), le ou les lanceurs d'accueil, l'interface système (`com.android.systemui`, `android`). On peut toujours revenir à l'accueil.
- **Réglages et installateur de paquets** : suivent la catégorie « Réglages » du profil (déjà bloquée par défaut). Si le fabricant a fusionné l'accueil et les réglages dans une même application, elle reste non bloquable (sécurité avant tout).
- **Catégorie facultative** (vidéo et streaming, jeux, navigateurs, autres) : devinée d'après le paquet et les indications Android ; elle sert à régler d'un coup toute une catégorie.
- **Nouvelle application** : la première liste affichée fixe le point de départ (tout est « connu » le jour de la mise en place). Une application installée ensuite (détectée au plus tard 5 minutes après, écran allumé) apparaît « **Nouvelle** » et suit le choix du parent : *bloquer jusqu'à validation* (par défaut) ou *autoriser (signalée aux parents)*. Donner une règle à l'application, ou la valider, la rend « connue ». Une alerte part au téléphone.
- Le temps passé dans une application autorisée compte dans la **durée quotidienne** et les **heures autorisées** du profil (nouvelle catégorie de temps « Autres applications », cochée par défaut, à décocher par profil). Avertissement 5 minutes avant la fin, comme avant ; les fenêtres qui passent minuit et le changement de jour fonctionnent comme pour le reste (testés).

### Ce que fait la TV quand une application est refusée

Application bloquée, nouvelle application bloquée, durée de l'application ou durée/heures du profil dépassées : la TV est renvoyée à l'accueil (service d'accessibilité si connecté, sinon intention « accueil ») puis l'écran « Accès protégé » apparaît devant, avec toujours **« Saisir le PIN parental »** et **« Retour à l'accueil »** ; RETOUR et ACCUEIL ne sont jamais interceptés. Le code parental rouvre l'application (séance parent, ou accès à cette seule application pour l'état « Code parental requis »). Un refus est consigné (rapport, alerte) au plus une fois par minute pour la même application.

### Après un redémarrage

`BootReceiver` démarre `TvService` (service d'avant-plan, donc toléré par les limites d'Android 8+) ; `TvApp` initialise `ParentalHub`, qui relance le détecteur et le compteur. Aucune alarme ni tâche de fond supplémentaire n'est nécessaire. **À vérifier sur la vraie TV** (économie d'énergie de GaiaOS, voir HANDOFF).

### Limites honnêtes

- **Rien de tout cela n'est inviolable sans root ni administrateur d'appareil.** Un enfant qui ouvre les Réglages de la TV peut retirer l'accès aux données d'utilisation, l'affichage par-dessus les autres applications, ou désactiver le service d'accessibilité. CastBridge-TV **le détecte** (passage de « active » à « non autorisée », mémorisé : détecté aussi après redémarrage), l'affiche et **envoie une alerte au téléphone du parent** ; il ne peut pas l'empêcher. Recommandation : garder la catégorie **« Réglages » bloquée** pour le profil (le blocage des Réglages passe par la même surveillance : tant qu'elle est active, l'enfant n'y accède pas).
- Détection avec un délai de quelques secondes (statistiques) : une application peut rester visible brièvement avant d'être renvoyée à l'accueil.
- Depuis Android 10 (strict en 15), démarrer un écran depuis l'arrière-plan exige « Afficher par-dessus les autres applications » (ou un service d'accessibilité) : sans cela, la TV ne peut pas bloquer, et l'état affiché n'est plus « active ».
- Le temps est attribué à l'application au premier plan : une vidéo qui continue en arrière-plan n'est pas comptée deux fois, mais un lecteur en incrustation n'est pas distingué.
- L'écran éteint, rien n'est compté ni détecté ; l'horloge de la TV fait foi.
- Les applications sans icône de lanceur n'apparaissent pas dans la liste mais sont comptées (nom de paquet) et soumises à l'état par défaut.

### Autres voies évaluées (non implémentées)

- **Profil restreint Android TV** : le plus solide pour le contenu (le système cache les applications non autorisées), mais il dépend de la version du système (absent de beaucoup de boîtiers GaiaOS), impose un changement de profil de la TV, et ne permet ni quota par application ni rapport. À recommander quand il existe, en complément.
- **Propriétaire d'appareil (device owner / DPC)** : permettrait de masquer ou suspendre des applications, d'interdire la désinstallation et le retrait des autorisations (`setApplicationHidden`, `setPackagesSuspended`, restrictions utilisateur). Mais cela exige un appareil sans compte Google, mis en service à zéro (`dpm set-device-owner` par adb avant toute configuration) ou une réinitialisation d'usine : trop intrusif pour une TV déjà en service. Piste à étudier pour une TV dédiée à un enfant.

### Écarts à la conception demandée

- Les réglages par application sont stockés **à part** de la configuration générale (clé `apps`, propre `rev`) : un ancien téléphone qui enregistre toute la configuration (`config/set`) effacerait sinon les règles qu'il ne connaît pas. Les champs ajoutés aux réponses sont additifs.
- Les règles sont **par profil** (deux enfants n'ont pas les mêmes) ; la décision « nouvelle application » est commune.
- Le détecteur interroge toutes les 4 s mais le temps n'est écrit (préférences) qu'au changement d'application et toutes les 15 s : peu d'écritures sur la mémoire de la TV.
- Les nouvelles applications sont cherchées toutes les 5 minutes, pas par diffusion système (pas de récepteur supplémentaire).
- La livraison des rapports se fait par **Bluetooth uniquement** (pas par le Wi-Fi) : voir ci-dessous.

## Rapports envoyés au téléphone du parent

La TV construit des rapports de l'activité de toute la TV (par profil et par application : minutes, tentatives bloquées, durée ou heures atteintes, nouvelles applications, état de la surveillance, altérations comme « accès aux données d'utilisation retiré ») et les remet au téléphone du parent. **Aucun serveur, aucun service de notification** (ni FCM) : la liaison est la liaison Bluetooth de confiance du plug-and-play (`docs/BT-PLUG-AND-PLAY.md`).

### Poussée ou tirage : pourquoi le téléphone tire

La TV est le serveur Bluetooth (RFCOMM), le téléphone le client : la TV ne peut pas joindre un téléphone qui n'écoute pas, et un service d'écoute permanent sur le téléphone coûterait de la batterie. Choix : **le téléphone vient chercher** (nouveau message `CBTP`, additif : une ancienne TV répond « protocole inconnu », un ancien téléphone ne l'envoie jamais), et la TV garde une **outbox** : un rapport attend sa livraison, que la TV soit éteinte ou hors de portée au moment où il est prêt.

- Outbox (préférences privées, hors sauvegardes) : bornée à **40 rapports par téléphone, 200 Ko au total, 14 jours** (les plus anciens partent d'abord), livrée **dans l'ordre**, **retentée à chaque visite** jusqu'à l'**accusé de réception** du téléphone (qui n'est envoyé qu'après stockage : un lien coupé entre-temps provoque une seconde livraison, que le téléphone écarte).
- Le téléphone vient : à l'ouverture de l'app, par le bouton « Recevoir maintenant », et par une tâche périodique légère (toutes les 15 minutes, `JobScheduler`) **qui ne tourne que sur un téléphone désigné** (le téléphone d'un enfant n'interroge jamais la TV). Délai d'une alerte : au plus 15 minutes, TV et téléphone à portée.
- Trames : `"CBTP" | u16 longueur | requête JSON` ; réponse `statut | u32 longueur | JSON` ; puis `u16 longueur | {"ack":[…],"key":bool}` ; réponse `statut`. Seulement sur le lien RFCOMM sécurisé (appairé, chiffré) : le pair est celui du socket, jamais ce qu'il écrit.

### Qui reçoit

- Seul un téléphone **de confiance ET désigné** par le parent reçoit des rapports ; **personne n'est désigné par défaut** (le téléphone de l'enfant n'en reçoit pas). 3 téléphones au plus.
- **Désigner ou retirer** un destinataire demande le **code parental** (écran de la TV : code redemandé même pendant une séance parent ; téléphone : `POST /reports/recipients/add|remove`, code dans le corps). Un téléphone qui n'est plus de confiance cesse d'être destinataire et ses rapports en attente sont supprimés.
- L'API n'expose jamais l'adresse Bluetooth : un téléphone de confiance apprend son **identifiant opaque** par `CBTP` et le parent le désigne par cet identifiant.
- Jamais dans un rapport : code parental, code de la TV, jeton, adresse Bluetooth (testé).

### Déclencheurs et options (par profil, modifiables sur la TV et sur le téléphone)

- **Résumé du jour** à l'heure choisie (20:00 par défaut) et **résumé de la semaine** le jour choisi (dimanche par défaut) ; un résumé manqué parce que la TV était éteinte part au premier passage après l'heure, le même jour.
- **Alertes immédiates** : temps ou heures atteints, application bloquée essayée, surveillance affaiblie (accès retiré…), nouvelle application installée. « Surveillance affaiblie » et « nouvelle application » valent pour toute la TV (envoyées si un profil les demande). Même alerte dans les 10 minutes : une seule ; au plus 12 alertes par heure.
- « Envoyer un rapport maintenant » prépare le résumé du jour (test de la liaison).

### Intégrité (contre l'usurpation depuis un autre appareil)

Le lien RFCOMM appairé authentifie la TV. En plus, chaque rapport porte un **HMAC-SHA256** (clé de 256 bits propre au destinataire, créée à la désignation, remise au téléphone **une seule fois, sur le lien sécurisé**, jusqu'à l'accusé de réception ; nouvelle clé à chaque nouvelle désignation). Le téléphone rejette tout rapport dont la signature ne correspond pas (testé : clé inconnue, corps modifié, signature absente). Limite : la première remise de la clé repose sur l'appairage Bluetooth (comparaison de code des deux écrans) ; quelqu'un qui lit le stockage privé du téléphone ou de la TV (root) lit la clé.

### Sur le téléphone

Réception et stockage local (**150 rapports, 90 jours à compter de la réception, hors sauvegarde**), **notification locale** (visibilité « privée » : le prénom et les durées ne s'affichent pas sur l'écran verrouillé), historique dans le panneau « Toute la TV » de l'écran « Contrôle parental » (Réglages) : vue par enfant et par application, état de la surveillance, alertes d'altération, désignation de ce téléphone, heure du résumé, options par enfant.

> L'écran parental dédié ouvert par le cadenas de la barre d'application (`ParentalActivity.kt`) n'existe pas sur cette branche (il n'y a que `ParentalScreen.kt`, dans Réglages). Rien de conflictuel n'a été inventé : `WholeTvPanel` (`ParentalWholeTv.kt`) est un composable autonome à placer dans cet écran s'il est créé (`WholeTvPanel(client, profils, codeParental, scope, onMsg)`), et `ParentalInbox` (réception, historique, notification) ne dépend d'aucun écran. Pour le cadenas : une icône qui ouvre l'écran dédié, qui appelle `ParentalInbox.sync` à l'ouverture et affiche le nombre de rapports non lus (`ParentalInbox.inbox.unread()`).

### Limites

- Un téléphone éteint, hors de portée ou sans Bluetooth ne reçoit rien tant que la TV est loin (les rapports attendent jusqu'à 14 jours).
- Pas de livraison par le Wi-Fi/réseau local dans cette version : le HTTP de la TV ne sait pas, d'après le jeton, quel téléphone il a en face pour appliquer la désignation sans code parental. Le Bluetooth prouve l'identité du téléphone ; le Wi-Fi serait une suite possible (jeton de téléphone + même signature).
- Si le parent perd son téléphone, retirer le téléphone de la liste de confiance de la TV (« Retirer ») coupe aussi les rapports.

## Onglet Parental (téléphone)

Un **onglet « Parental »** de l'app CastBridge (après « Apprendre ») détaille toute l'activité de la TV, avec tous les rapports possibles. Le cadenas de la barre d'application ouvre **le même contenu** (`ParentalActivity` → `ParentalTab`) : un seul endroit. L'ancien éditeur des règles (profils, horaires, limites, règles par application, surveillance de toute la TV) est la section « Règles de la TV » de l'onglet.

### Accès et sécurité

- **Code parental** demandé **une fois par séance** ; il est vérifié **par la TV** (`POST /config/get`, même verrouillage progressif), gardé **en mémoire seulement**, jamais écrit ni journalisé, oublié au verrouillage. Aucun code parental : création guidée dans l'onglet.
- **Verrouillage automatique** après 3 minutes en arrière-plan (`TabLock`, testé) ou par le bouton « Verrouiller ». `FLAG_SECURE` : pas de capture d'écran ni d'aperçu dans les apps récentes.
- **TV éteinte ou hors de portée** : la TV ne peut pas vérifier le code. L'onglet s'ouvre alors en **lecture seule** avec le verrouillage d'écran du téléphone (aucun code stocké) : pas de purge, pas de réglage de la TV, pas de règles.

### Écrans

| Écran | Contenu |
|---|---|
| Tableau de bord | Par profil, aujourd'hui (ou le dernier jour reçu, dit clairement) : temps contre limite, répartition (anneau), activités principales, blocages, alertes, état de la surveillance, âge de la dernière synchronisation. |
| Activité | Chronologie par jour et par profil, filtres (vidéos, jeux, Apprendre, Quiz, téléchargements, applications, blocages, alertes), détail au toucher. |
| Par application | Minutes par application, jour / semaine / mois / plage, **MEILLEUR EFFORT**. |
| Apprendre et Quiz | Classe, fiches, étoiles, série, temps, réussite par matière, points faibles, épreuves blanches, badges ; parties de Quiz. |
| Rapports | Jour / semaine / mois / plage, par profil et tous profils, comparaison avec la période précédente (écart et tendance, **par jour reçu** : une période incomplète ne ressemble pas à une baisse), objectifs contre réalisé, classements, carte d'usage par heure et jour, heures de pointe, séances les plus longues, usage tardif (22 h – 6 h), hors des heures autorisées, blocages, tentatives de déverrouillage, alertes de manipulation. |
| Exports | Résumé en **texte**, en **PDF** (`PdfDocument`), **CSV** des événements : feuille de partage d'Android, **rien n'est envoyé nulle part**. |
| Alertes | Historique avec gravité et action, préférences par profil et horaires des rapports (réglages existants de la TV), statut du téléphone destinataire. |
| Données | Conservation (90 jours par défaut, 30 à 365, et 20 000 événements au plus), purge par profil / par TV / totale (**code redemandé**), dernière synchronisation, « Demander les rapports à la TV », sélecteur de TV s'il y en a plusieurs. |

Graphiques dessinés avec `Canvas` (barres, ligne, anneau, carte de chaleur), couleurs du thème (clair et sombre), alternative textuelle pour chacun, et chaque nombre est aussi écrit en toutes lettres. Une donnée manquante est un **trou en pointillés**, jamais une barre à zéro.

### Qualité des données (règle absolue)

Chaque chiffre porte son étiquette (symbole + mot, jamais la couleur seule) :

- **● MESURÉ** : CastBridge-TV, compté par la TV elle-même, systématiquement : sessions de vidéos (titre, durée), jeux, Quiz, téléchargements, Apprendre (fiches, exercices, épreuves, score, série), tentatives de déverrouillage, blocages, dépassements de temps ou d'heures.
- **◐ MEILLEUR EFFORT** : les autres applications de la TV, vues par la surveillance de toute la TV **quand elle est active et autorisée** (minutes par application).
- **○ INDISPONIBLE** : surveillance inactive ou non autorisée, TV éteinte, aucun rapport reçu, journal non reçu. **Jamais un zéro, jamais un nombre d'apparence mesurée** : l'écran écrit « indisponible » et pourquoi. Un jour sans rapport est un **trou**, pas un zéro ; une période avec des trous est dite « incomplète : X j sur Y reçus ».

L'état « Surveillance de toute la TV : active / non autorisée / indisponible sur cette TV » est en haut de chaque écran, avec ce qu'il implique. Sur la TV d'Esaie le service d'accessibilité ne peut pas être activé : les minutes des autres applications peuvent manquer (dites **indisponibles**), le suivi de CastBridge-TV reste complet. Le temps total n'additionne jamais les deux sources en un seul chiffre : « CastBridge-TV (mesuré) » et « autres applications (~, meilleur effort) » sont séparés ; une limite est comparée à « au moins X min » quand les autres applications ne sont pas mesurées.

### Hors ligne d'abord, aucun serveur

Tout se lit depuis la **copie locale** du téléphone (`ParentalLedger`, préférences privées `castbridge_parental_ledger`, **hors sauvegardes**). Les rapports y arrivent par la livraison différée **existante** (outbox de la TV, tirage Bluetooth du téléphone désigné) et par la demande manuelle (Bluetooth, et rapport en direct par Wi-Fi si la TV est liée et le code connu) : un seul chemin, `absorb`, idempotent (doublons par identifiant de rapport et d'événement, rapports en retard ou dans le désordre sans effet sur le résultat, un jour = le rapport le plus récent de la TV, un rapport quotidien l'emporte sur le total d'un hebdomadaire). L'âge des données et les périodes incomplètes sont affichés. **Règle documentée : aucun envoi des données parentales à un serveur, aucun cloud.** Elles ne quittent le téléphone que par les boutons de partage du parent. Le code parental n'est ni stocké ni journalisé. Aucun composant exporté nouveau (le `FileProvider` du partage n'est pas exporté ; l'onglet n'est jamais compté dans la télémétrie).

### Ce que la TV envoie en plus (champs additifs du rapport quotidien, anciens téléphones : ignorés)

- `events` : journal de CastBridge-TV des dernières 26 h (`TvJournal`, borné à 400 événements / 8 jours, 80 par rapport) : `{id, ts, t, title, min, score, detail}`. Les sessions viennent du compteur d'usage existant (`SessionTracker` : vidéo ouverte, Quiz, Échecs, téléchargements), les tentatives de déverrouillage du code refusé (jamais le code).
- `learn` : résumé d'Apprendre de l'élève lié au profil, construit depuis le suivi existant (`LearnProgress.dashboard`) ; fiches et épreuves du jour dans `events`.
- Rien de nouveau n'est collecté. **Pas encore branché côté TV** : le score d'une partie de Quiz (la durée l'est ; le score reste « non transmis »), la télécommande (utilisation) ; le journal les accepte déjà (`TvJournal.record`).

### Code

Logique pure, testée en JVM : `castbridge.core.parental.tab` (`ParentalLedger`, `ReportAggregator`, `Trends`/`Goals`, `Heatmap`/`UseStats`, `Period`, `Exports` (texte, PDF paginé, CSV), `LearnInsights`, `TabLock`, `LiveReport`, `TvJournal`, `SessionTracker`). Android : `ParentalTab.kt`, `ParentalReportsUi.kt`, `ParentalCharts.kt`, `ParentalExport.kt`, `ParentalData.kt` (module `:sender`).
