# Contrôle parental

Branche `feat/parental` (partie de `feat/ssh`). Réécriture propre : la première version de `wip/external-ai-changes` n'a servi que de spécification fonctionnelle (l'audit `docs/AUDIT-EXTERNAL-CHANGES.md`, constat S6, la jugeait « À corriger »). Rien n'en est repris tel quel.

**Tout reste sur la TV** (et sur le téléphone du parent, pour l'affichage). Aucune donnée parentale n'est envoyée au serveur : aucun appel `TvConnect`, aucun événement de statistiques, aucune tuile comptée. Les profils, règles, classements et rapports n'existent que dans les préférences privées de l'app TV.

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

Apprendre et la navigation (accueil, RETOUR) ne sont jamais bloquables.

## Sécurité

- **Aucun code par défaut.** Tant que le parent n'a pas créé de code, le contrôle est inactif. Le code est refusé s'il est vide, hors de 4 à 6 chiffres, un chiffre répété (4444) ou une suite (1234).
- **Stockage** : PBKDF2-HMAC-SHA256, sel aléatoire de 16 octets, 60 000 itérations (`pbkdf2-sha256$iter$sel$hash`). Jamais en clair, jamais journalisé. Comparaison à temps constant. Un code de 4 à 6 chiffres reste devinable hors ligne par quelqu'un qui lit le stockage privé de l'app (root) : le hachage protège des journaux, sauvegardes et lectures simples ; en ligne, c'est le verrouillage qui protège.
- **Verrouillage progressif, persistant** (survit au redémarrage et à la fermeture de l'app) : 5 échecs → 1 min, puis 5 min, 15 min, 1 h (palier conservé). Pendant le verrou, même le bon code est refusé. Global (pas par adresse IP). Un réglage de l'heure en arrière ne rallonge pas le verrou au-delà d'une heure ; une avance de l'horloge le raccourcit (les Réglages de la TV sont bloquables pour l'enfant).
- **Réinitialisation** : seulement par l'administrateur de la TV (code de connexion à 6 chiffres, sur la TV ou via l'API avec `{"confirm":"RESET"}`) ou par l'effacement des données de l'app. Elle efface le code **et désactive** le contrôle (profils et règles gardés) ; l'écran prévient clairement. Les essais du code administrateur sur l'écran TV ont leur propre verrouillage progressif.
- **L'administrateur peut toujours désactiver** le contrôle (`POST /api/parental/disable`, code de la TV seul).
- **API** : toutes les routes sont derrière le code de la TV (en-tête `X-CB-Pin`, déjà imposé par le serveur) **et**, pour les actions sensibles, le code parental. Le code parental voyage **uniquement dans le corps JSON d'un POST**. Tout paramètre d'URL dont le nom contient « pin » (ou `new`, `old`, `code`) est refusé en 400, même avec la bonne valeur. Les réponses sont en JSON strict.
- BACK et HOME ne sont jamais interceptés. Un écran verrouillé propose toujours « Saisir le PIN parental » et « Retour à l'accueil » (et RETOUR y mène).
- Aucune permission ajoutée, aucun service ni activité exporté(e) : `ParentalActivity` et `ParentalLockActivity` sont `exported=false`.
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
| `POST /disable` | `{}` | administrateur (code TV) |
| `POST /reset` | `{"confirm":"RESET"}` | administrateur (code TV) : efface le code et désactive |

Erreurs : 403 `{error, attemptsLeft}` code incorrect ; 429 `{error, retryAfter}` verrouillé ; 400 invalide ; 409 conflit.

## Architecture

- `core/.../parental/` (JVM, testé) : `ParentalPin.kt` (hachage, verrou), `ParentalModel.kt` (profils, règles, catégories, décisions pures), `ParentalEngine.kt` (code, configuration, usage du jour, rapport, décisions), `ParentalApi.kt` (routes), `ParentalClient.kt` (côté téléphone).
- `receiver/` : `ParentalHub` (colle Android : rappels d'activité, compteur de temps toutes les 15 s, garde de lecture, filtre de bibliothèque), `ParentalActivity` (réglages TV), `ParentalLockActivity` (écran verrouillé), `ParentalUi` (clavier numérique à la télécommande, lignes).
- Modifications minimales des fichiers partagés : `TvApp` (2 lignes), `TvService` (gardes `play*`, routes), `PlayerActivity` (tuile, enveloppes de tuiles et de menus, filtre, masquage du code), `LibraryScreen` (PIN avant lecture d'une vidéo verrouillée, action « Classer »), manifeste, icône. Téléphone : écran dédié `ParentalActivity` (cadenas dans la barre du haut, ou lien dans Réglages), contenu `ParentalScreen.kt`, masqué des captures (FLAG_SECURE).
- Points d'application : une activité de catégorie bloquée est fermée au moment où elle reprend la main (couvre aussi les ouvertures depuis le téléphone) ; le pont de lecture de la TV refuse les vidéos (bibliothèque TV, téléphone, liste de lecture) ; le compteur arrête la lecture et affiche l'écran verrouillé à la fin du temps ou des heures.

## Limites (à dire aux parents)

- **Un enfant peut contourner** en changeant de lecteur ou d'application (YouTube, Netflix, lecteur externe, autre app du système), en utilisant le lanceur de la TV, ou une autre télécommande/appli installée. CastBridge ne contrôle que ce qui se passe dans CastBridge TV. Pour le reste, il faut le contrôle parental du système (profil restreint Android TV) ou du fournisseur.
- Le temps d'écran ne compte que le temps passé **dans CastBridge** (vidéo en lecture, jeux ouverts, écran Téléchargements). Apprendre n'est pas compté ni bloqué.
- Le classement est manuel ; il suit le **nom du fichier** : renommer une vidéo la remet « non classée » (adulte par défaut).
- Un téléphone qui connaît le code de la TV voit toute la bibliothèque (`/api/library`) : le filtre s'applique à l'écran de la TV et à la lecture, pas à la liste envoyée au téléphone.
- Un enfant qui a accès au code de connexion de la TV (autre écran, autre téléphone appairé) peut réinitialiser le code parental.
- Le verrou s'appuie sur l'horloge de la TV.
- Sauvegarde Android (`allowBackup`) : le code haché et la configuration peuvent être sauvegardés avec l'app selon le réglage système.

## Tests

`gradle :core:test --tests 'castbridge.core.Parental*'` : hachage et comparaison, verrouillage progressif et sa persistance (redémarrage), réinitialisation administrateur, règles horaires (fenêtre passant minuit) et quota, avertissement 5 min, filtrage par classification, catégories, mode enfant, « BACK/HOME jamais bloqués », API refusant code vide/par défaut/en URL, serveur réel + client téléphone. Captures d'émulateur : `docs/img/parental/` (TV 1280x720 à 160 dpi et 1920x1080, téléphone).
