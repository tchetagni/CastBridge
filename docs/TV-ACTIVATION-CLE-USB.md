# Activer CastBridge-TV avec un fichier sur la clé USB

## Pourquoi « aucune clé trouvée » alors que le fichier est sur la clé

Règle réelle d'Android 11 et plus (constatée le 2026-10-04 sur la TV du propriétaire, Android 14, version 0.14.35) : sans l'autorisation « Accès à tous les fichiers » (`MANAGE_EXTERNAL_STORAGE`), une application ne peut pas lire un fichier que le PC, un gestionnaire de fichiers ou une autre application a écrit dans `Download/`. La permission `READ_EXTERNAL_STORAGE` ne sert plus à rien à partir d'Android 13 (et ne suffit pas pour ces fichiers dès Android 11 sur une application récente). Un terminal en ligne de commande lit ces fichiers, l'application non : c'est pourquoi `activation` et `activation_2026_unlimited.txt` (594 octets) étaient lisibles par la ligne de commande mais invisibles dans l'explorateur de CastBridge-TV. Le dossier de `Download` n'est donc pas une voie sûre.

Ce qui est toujours lisible : le dossier propre de l'application sur chaque volume, `Android/data/castbridge.receiver/files/` (la TV y écrit déjà `device-request.txt` ; un PC peut y écrire). Les autres applications n'y voient rien.

Quand l'autorisation manque, l'écran le dit en une ligne, au lieu de « aucune clé trouvée » : « Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » (Réglages), ou déposez le fichier dans le dossier de l'application : <chemin> ». L'écran d'activation affiche aussi, pour chaque volume monté, le chemin exact du dossier de dépôt (« Clé A379-E209 : déposez le fichier « activation » dans Android/data/castbridge.receiver/files/ ») et les 6 premiers noms de fichiers que CastBridge-TV y voit : le propriétaire constate tout de suite ce que l'application voit.

## Les trois façons, dans l'ordre

1. Sur le téléphone : CastBridge > « Activer la TV » (Bluetooth). Aucun fichier à manipuler, aucune autorisation Android : c'est la seule voie qui marche sur tous les boîtiers.
2. Le fichier de la clé USB :
   - toujours lisible : le déposer dans `Android/data/castbridge.receiver/files/` de la clé (ou son sous-dossier `CastBridge/`). La recherche automatique y prend `activation`, `activation.txt` et `activation_*.txt` (texte, 16 Kio au plus ; pas d'autre nom, pas ailleurs) ;
   - dans `Download/` : seulement si « Accès à tous les fichiers » est donné à CastBridge-TV (première ligne de l'explorateur, quand l'écran de réglages existe sur le boîtier) ;
   - ou, à la télécommande, « Choisir le fichier d'activation (explorateur) » : le nom n'a aucune importance.
3. Coller la clé dans le champ de l'écran d'activation, puis « Valider la clé ».

Le fichier contient la clé sur une seule ligne (BOM, fin de ligne Windows et ligne finale tolérés), 16 Kio au plus, texte seulement. Il n'est jamais exécuté ni modifié.

## L'explorateur de fichiers de CastBridge-TV

Beaucoup de boîtiers n'ont pas d'explorateur système : CastBridge-TV a donc le sien (écran plein, gros caractères). Haut/bas : parcourir ; OK : ouvrir un dossier ou choisir un fichier ; Retour : dossier parent, puis liste des volumes (« Clé USB A379-E209 », « Stockage interne »), puis sortie. Dossiers d'abord, puis fichiers de 16 Kio au plus, puis les plus gros (affichés, refusés). Il s'ouvre sur le dossier propre de l'application (`Android/data/castbridge.receiver/files`) quand l'accès à tous les fichiers manque (Download paraît vide à l'application), sinon sur `Download/CastBridge`, `Download`, puis le dossier propre. Un fichier dont le nom commence par `activation` (même `activation_2026_unlimited.txt`), texte, 16 Kio au plus, est proposé en première ligne ; un binaire ou un gros fichier ne l'est jamais. Il ne crée, ne modifie et ne supprime rien.

- Tant que l'autorisation manque, la première ligne de la liste est « ▶ Autoriser l'accès à tous les fichiers » (ouvre l'écran de réglages de l'application). Si le boîtier n'a pas cet écran (`resolveActivity` vide), la ligne devient une explication, sans bouton : « Ce boîtier n'a pas l'écran d'autorisation : utilisez le téléphone (Bluetooth) ou le dossier de l'application ». Au retour des réglages, l'état est relu. Jamais bloquant ; le dossier propre de chaque volume reste lisible sans aucune permission.
- Fichier choisi : 16 Kio au plus, texte UTF-8 seulement (un binaire est refusé), première ligne = la clé, vérifiée comme une clé collée ; jamais exécuté ni copié ailleurs que dans le magasin privé de la TV. Annuler dit « Aucun fichier choisi » et ne change rien.
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

1. Écran d'activation : sous les trois façons, lire « Clé A379-E209 : déposez le fichier « activation » dans Android/data/castbridge.receiver/files/ » et la ligne « CastBridge-TV y voit : … » (6 noms au plus).
2. Conditions cochées, « Chercher la clé sur la clé USB » avec les fichiers dans `Download/` : attendu, la ligne « Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » … », pas « aucune clé trouvée ».
3. « Choisir le fichier d'activation (explorateur) » : il s'ouvre sur le dossier de l'application ; première ligne « ▶ Autoriser l'accès à tous les fichiers » (ou, sans écran de réglages, l'explication). Si le bouton existe : l'activer, revenir, `Download/` se liste.
4. Copier `activation_2026_unlimited.txt` dans `Android/data/castbridge.receiver/files/` par le PC : au plus 15 s, « Clé trouvée : vérification… » puis « Activée » (ou OK sur la première ligne de l'explorateur).
5. À défaut : le téléphone (Bluetooth), voie 1.
6. Mettre la clé d'une autre TV : « celle d'une autre TV ».

Vérifié en JVM : règle de permission, cause affichée, noms acceptés et refusés, première ligne de l'explorateur, ordre de départ, lignes du dossier de dépôt, absence de texte de clé (`ActivationAccessTest`, `ActivationLookupTest`, `FilePickingTest`). Vérifié par compilation seulement : écrans (`ActivationActivity`, `FilePickActivity`), lecture de `Environment.isExternalStorageManager`, présence de l'écran de réglages. À confirmer sur la TV réelle : que la TV expose le volume `A379-E209` par `getExternalFilesDirs`, que le fichier déposé par le PC dans le dossier de l'application y est lisible, et si l'écran « Accès à tous les fichiers » existe sur ce boîtier.
