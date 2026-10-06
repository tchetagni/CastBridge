# Activer CastBridge-TV avec un fichier sur la clé USB

## Pourquoi « aucune clé trouvée » alors que le fichier est sur la clé

Règle réelle d'Android 11 et plus (constatée le 2026-10-04 sur la TV du propriétaire, Android 14, version 0.14.35) : sans l'autorisation « Accès à tous les fichiers » (`MANAGE_EXTERNAL_STORAGE`), une application ne peut pas lire un fichier que le PC, un gestionnaire de fichiers ou une autre application a écrit dans `Download/`. La permission `READ_EXTERNAL_STORAGE` ne sert plus à rien à partir d'Android 13 (et ne suffit pas pour ces fichiers dès Android 11 sur une application récente). Un terminal en ligne de commande lit ces fichiers, l'application non : c'est pourquoi `activation` et `activation_2026_unlimited.txt` (594 octets) étaient lisibles par la ligne de commande mais invisibles dans l'explorateur de CastBridge-TV. Le dossier de `Download` n'est donc pas une voie sûre.

Ce qui est toujours lisible : le dossier propre de l'application sur chaque volume, `Android/data/castbridge.receiver/files/` (la TV y écrit déjà `device-request.txt` ; un PC peut y écrire). Les autres applications n'y voient rien.

Quand l'autorisation manque, l'écran le dit en une ligne, au lieu de « aucune clé trouvée » : « Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » (Réglages), ou déposez le fichier dans le dossier de l'application : <chemin> ». L'écran d'activation affiche aussi, pour chaque volume monté, le chemin exact du dossier de dépôt (« Clé A379-E209 : déposez le fichier « activation » dans Android/data/castbridge.receiver/files/ ») et les 6 premiers noms de fichiers que CastBridge-TV y voit : le propriétaire constate tout de suite ce que l'application voit.

## Les trois façons, plus le Wi-Fi

1. Sur le téléphone : CastBridge > « Activer la TV ». Aucun fichier à manipuler, aucune autorisation Android.
   - **Par le Wi-Fi d'abord** (2026-10-06, décision du propriétaire) : si la TV et le téléphone sont sur le même réseau, le téléphone trouve la TV (mDNS, même s'il ne l'a jamais ajoutée) et lui envoie la clé par HTTP. Il demande le **code de connexion** de la TV (6 chiffres, affiché en gros sur l'écran d'activation : « Par le Wi-Fi : code de connexion 482915 · TV 192.168.1.20 ») ; le code n'est pas enregistré. Voir « Activation par le Wi-Fi » ci-dessous.
   - **Sinon par Bluetooth** (repli automatique si la TV ne répond pas par le Wi-Fi, ou bouton « Envoyer par Bluetooth à la place ») : c'est la seule voie qui marche sur tous les boîtiers.
2. Un fichier texte qui contient la clé :
   - toujours lisible : le déposer dans `Android/data/castbridge.receiver/files/` de la clé (ou son sous-dossier `CastBridge/`). La recherche automatique y prend `activation`, `activation.txt` et `activation_*.txt` (texte, 16 Kio au plus ; pas d'autre nom, pas ailleurs ; première ligne) ;
   - dans `Download/` : seulement si « Accès à tous les fichiers » est donné à CastBridge-TV (première ligne de l'explorateur, quand l'écran de réglages existe sur le boîtier) ;
   - ou, à la télécommande, « Choisir le fichier d'activation (explorateur) » : **n'importe quel fichier texte, quel que soit son nom, la clé à n'importe quelle ligne** (voir « Chercher la clé dans le fichier »).
3. Coller la clé dans le champ de l'écran d'activation, puis « Valider la clé ».

Quelle que soit la voie, la clé est vérifiée **de la même façon** (signature par une clé de confiance, liaison au code d'appareil de CETTE TV, fenêtre de 48 h) ; elle n'est jamais exécutée, jamais écrite dans un journal, jamais renvoyée dans une réponse.

## Activation par le Wi-Fi

Une TV verrouillée ne démarre aucun serveur (docs/TRIAL-EDITION.md). Elle ouvre **une seule** route HTTP sur le port 8765, `POST /api/activation/install` (`LockedActivationApi`, fonction `ACTIVATION_WIFI` de la liste verrouillée, ajoutée volontairement : cahier wios-tv-05, ici **avec** le code de connexion) :

- **code de connexion obligatoire** (`X-CB-Pin`), avec le même blocage que l'API complète (5 erreurs, 60 s) ; un jeton de téléphone de confiance n'ouvre jamais cette route (`TvAuth.tokenMayCall`) ;
- réseau local seulement (adresses privées, lien local, Wi-Fi Direct) et en-tête `Host` local (anti « DNS rebinding ») ;
- corps de **16 Kio au plus** (413 au-delà, sans le lire), 10 vérifications par adresse et par 10 minutes (429) ;
- conditions d'usage acceptées sur la TV (sinon 409 avec le texte à suivre) ;
- toute autre route : 403 « Usage soumis à autorisation ». `GET /api/hello` dit seulement `locked:true`.

L'annonce mDNS porte `locked=1` ; le téléphone choisit d'abord la TV à laquelle il est lié, sinon la TV trouvée sur le Wi-Fi (une TV « à activer » en premier, puces « Sur le Wi-Fi » s'il y en a plusieurs). Clé acceptée : l'écran d'activation de la TV affiche « Activée » et s'ouvre ; le serveur verrouillé s'arrête et le serveur complet prend le port. Une TV déjà activée (passage essai → production) garde sa route existante, même code de connexion, même borne de 16 Kio.

## Chercher la clé dans le fichier

Le fichier choisi à la télécommande est lu une fois, **256 Kio au plus**, texte UTF-8 seulement (BOM et fins de ligne Windows tolérés). Il est parcouru ligne par ligne (`KeyScan`) : chaque jeton `cbx1.…`, chaque suite d'au moins 5 groupes de 5 caractères (clé compacte ou texte groupé, avec ou sans étiquette « Clé : », même coupée sur plusieurs lignes) est un candidat ; la prose, les codes d'appareil et les mots isolés ne le sont pas. Les candidats (32 au plus) sont vérifiés dans l'ordre de lecture ; **le premier valable pour cette TV est installé** (une clé d'une autre TV ou périmée placée avant ne le cache pas). Aucun : « Aucune clé d'activation dans ce fichier : choisissez le fichier reçu avec la clé, ou collez la clé. » Plusieurs, aucun valable : « N clés trouvées dans ce fichier, aucune n'est valable pour cette TV. » suivi de la cause la plus utile (autre TV, puis périmée, puis illisible).

## L'explorateur de fichiers de CastBridge-TV

Beaucoup de boîtiers n'ont pas d'explorateur système : CastBridge-TV a donc le sien (écran plein, gros caractères). Haut/bas : parcourir ; OK : ouvrir un dossier ou choisir un fichier ; Retour : dossier parent, puis liste des volumes (« Clé USB A379-E209 », « Stockage interne »), puis sortie. **Tous** les éléments du dossier sont listés (fichiers cachés compris, 2 000 au plus, puis une ligne « … et N autres éléments non affichés ») : dossiers d'abord, puis les fichiers qu'on peut choisir (avec leur taille), puis, **grisés avec leur raison**, ceux qui ne peuvent pas être une activation : « pas du texte » (vidéo, image, son, archive, application, document bureautique, d'après l'extension), « trop gros » (plus de 256 Kio), « vide ». OK sur un fichier grisé redit la raison. Il s'ouvre sur le dossier propre de l'application (`Android/data/castbridge.receiver/files`) quand l'accès à tous les fichiers manque (Download paraît vide à l'application), sinon sur `Download/CastBridge`, `Download`, puis le dossier propre. Un fichier dont le nom commence par `activation` (même `activation_2026_unlimited.txt`), texte, 16 Kio au plus, est proposé en première ligne ; un binaire ou un gros fichier ne l'est jamais. Il ne crée, ne modifie et ne supprime rien.

- Tant que l'autorisation manque, Android (11 et plus) **cache** à l'application les fichiers déposés par un PC ou une autre application : l'explorateur le dit en tête de liste (« Android cache ici une partie des fichiers à CastBridge-TV. Pour tout voir : « Autoriser l'accès à tous les fichiers » ou l'explorateur du système. »), sauf dans le dossier propre de l'application qui est lisible en entier. Les lignes suivantes sont « ▶ Autoriser l'accès à tous les fichiers » (ouvre l'écran de réglages de l'application) et, si le boîtier en a un, « ▶ Ouvrir l'explorateur du système » (le sélecteur Android voit ce que l'application ne voit pas). Si le boîtier n'a pas l'écran de réglages (`resolveActivity` vide), la première ligne devient une explication, sans bouton : « Ce boîtier n'a pas l'écran d'autorisation : utilisez le téléphone (Bluetooth) ou le dossier de l'application ». Au retour des réglages, l'état est relu. Jamais bloquant ; aucune permission nouvelle (celles du manifeste ci-dessous) ; Android n'est pas contourné.
- Fichier choisi : 256 Kio au plus, texte UTF-8 seulement (un binaire est refusé), la clé cherchée dans tout le fichier (ci-dessus), vérifiée comme une clé collée ; jamais exécuté ni copié ailleurs que dans le magasin privé de la TV. Annuler dit « Aucun fichier choisi » et ne change rien.
- Un second bouton « Explorateur du système » (sélecteur Android, `ACTION_OPEN_DOCUMENT`, ouvert sur la clé si possible) n'apparaît que si le boîtier en a un ; l'adresse du fichier n'est pas conservée. S'il disparaît entre-temps : « Cet appareil n'a pas d'explorateur de fichiers système : … » puis la recherche automatique.
- Ajouts au manifeste (APK hors magasin) : `READ_EXTERNAL_STORAGE` (jusqu'à l'API 32), `MANAGE_EXTERNAL_STORAGE` (seulement proposé par la première ligne ci-dessus), requêtes `OPEN_DOCUMENT` et `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, activité `FilePickActivity`.

## Ce que fait la recherche

- Chemins essayés, dans l'ordre : `Download/CastBridge/activation`, `Download/activation` (dossier public et chaque volume), puis le dossier propre de chaque volume monté (`activation`, puis `activation.txt` et `activation_*.txt` trouvés dans ce dossier ou son sous-dossier `CastBridge`, jamais d'autres noms, jamais dans `Download`). Le premier fichier accepté gagne ; un fichier refusé (périmé, autre TV) n'empêche pas d'en trouver un bon plus loin.
- Elle repart toutes les 15 s tant que l'écran est ouvert, et dès qu'un volume est monté (diffusion système « media mounted », 1,5 s après). Une seule recherche à la fois. Rien n'est lu avant l'acceptation des conditions d'usage.
- Sous le bouton « Chercher la clé sur la clé USB », 6 lignes au plus : clé détectée (identifiant, « lecture seule » le cas échéant), nombre d'éléments visibles dans `Download/CastBridge` et dans le dossier propre, noms mal écrits repérés (`activation.txt`, `Activation`, `activation (1)`, `activation.zip` : « renommez-le « activation » »), état du fichier (si l'autorisation manque et que rien n'est lisible : la ligne de la cause ci-dessus, jamais « introuvable » seul ; sinon introuvable, illisible, vide, trop gros, pas une clé valable, autre TV, périmée)). Jamais le texte de la clé ; un nom de fichier inhabituel n'est pas recopié.

## Dépannage

| Ligne affichée | Que faire |
|---|---|
| Aucune clé USB détectée | rebrancher la clé ; attendre 15 s ; essayer un autre port |
| Android n'autorise pas CastBridge-TV à lire Download | donner « Accès à tous les fichiers » (première ligne de l'explorateur, ou Réglages), ou déposer le fichier dans le dossier de l'application affiché sur l'écran, ou utiliser le téléphone |
| introuvable (Android ne laisse pas…) | déposer le fichier dans `Android/data/castbridge.receiver/files/` de la clé (le dossier existe déjà : `device-request.txt` y est) |
| Fichier trouvé sous le nom « activation.txt » | le renommer `activation` (afficher les extensions sur le PC) |
| présent mais Android refuse de le lire | le déplacer dans le dossier propre ci-dessus, ou utiliser le téléphone |
| clé d'une autre TV | la clé a été émise pour un autre code d'appareil : refaire la demande avec le code affiché |
| périmée | une clé est valable 48 h : en demander une nouvelle |
| clé en lecture seule | normal pour la recherche (lecture seule) ; pour y déposer le fichier, retirer le verrou de la clé |

## Test par le propriétaire (clé `A379-E209`, Android 14)

1. Écran d'activation : sous les façons d'activer, lire « Clé A379-E209 : déposez le fichier « activation » dans Android/data/castbridge.receiver/files/ » et la ligne « CastBridge-TV y voit : … » (6 noms au plus).
2. Conditions cochées, « Chercher la clé sur la clé USB » avec les fichiers dans `Download/` : attendu, la ligne « Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » … », pas « aucune clé trouvée ».
3. « Choisir le fichier d'activation (explorateur) » : il s'ouvre sur le dossier de l'application ; première ligne « ▶ Autoriser l'accès à tous les fichiers » (ou, sans écran de réglages, l'explication). Si le bouton existe : l'activer, revenir, `Download/` se liste.
4. Copier `activation_2026_unlimited.txt` dans `Android/data/castbridge.receiver/files/` par le PC : au plus 15 s, « Clé trouvée : vérification… » puis « Activée » (ou OK sur la première ligne de l'explorateur).
5. À défaut : le téléphone (Bluetooth), voie 1.
6. Mettre la clé d'une autre TV : « celle d'une autre TV ».
7. Wi-Fi : TV verrouillée et téléphone sur la même box ; l'écran d'activation affiche « Par le Wi-Fi : code de connexion … · TV <adresse> ». Sur le téléphone, « Activer la TV » : la puce « Sur le Wi-Fi » montre la TV « (à activer) » ; coller la clé, « Envoyer à la TV », saisir le code : la TV s'ouvre. Code faux : « Code de connexion refusé ». Wi-Fi coupé sur la TV : envoi par Bluetooth.
8. Explorateur : enregistrer un e-mail contenant la clé au milieu du texte (`mail.txt`), le choisir : la clé est trouvée. Un dossier avec une vidéo et une photo : elles sont listées, grisées « pas du texte ».

Parcours de test : P-72 de `docs/test-plans/PARCOURS-CRITIQUES.md`.

Vérifié en JVM (2026-10-06) : recherche de la clé dans le texte et choix du premier candidat valable (`KeyScanTest`, dont un bout en bout avec le vrai vérificateur), liste complète et raisons des fichiers grisés, ligne « Android cache » et explorateur système (`FilePickingTest`, `ActivationAccessTest`), route Wi-Fi de la TV verrouillée : code, réseau local, `Host`, 16 Kio, 10 essais/10 min, conditions, toute autre route fermée, serveur HTTP réel (`LockedActivationApiTest`), choix de la TV Wi-Fi par le téléphone (`ActivationSendTest`), liste verrouillée (`FeatureGateTest`). Vérifié par compilation seulement : démarrage/arrêt du serveur verrouillé et annonce mDNS (`TvService`), écrans. **Rien n'a été essayé sur une vraie TV** pour ces ajouts.

Vérifié en JVM (avant) : règle de permission, cause affichée, noms acceptés et refusés, première ligne de l'explorateur, ordre de départ, lignes du dossier de dépôt, absence de texte de clé (`ActivationAccessTest`, `ActivationLookupTest`, `FilePickingTest`). Vérifié par compilation seulement : écrans (`ActivationActivity`, `FilePickActivity`), lecture de `Environment.isExternalStorageManager`, présence de l'écran de réglages. À confirmer sur la TV réelle : que la TV expose le volume `A379-E209` par `getExternalFilesDirs`, que le fichier déposé par le PC dans le dossier de l'application y est lisible, et si l'écran « Accès à tous les fichiers » existe sur ce boîtier.
