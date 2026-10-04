# Activer CastBridge-TV avec un fichier sur la clé USB

## Pourquoi « Aucun fichier « activation » trouvé » alors que le fichier est sur la clé

Depuis Android 11, le stockage est cloisonné (« scoped storage »). CastBridge-TV ne déclare aucun accès à tous les fichiers : elle ne peut pas lire un fichier que le PC, un gestionnaire de fichiers ou une autre application a déposé dans `Download/` de la clé. Elle voit le dossier, pas le fichier, et ne peut pas dire pourquoi. En revanche elle lit toujours son propre dossier sur chaque volume : `Android/data/castbridge.receiver/files/`.

Constat réel : sur la clé `A379-E209`, l'écran écrivait déjà `device-request.txt` dans `A379-E209/Android/data/castbridge.receiver/files/`. C'est là qu'il faut déposer le fichier.

## Les trois façons, de la plus simple à la plus manuelle

1. Sur le téléphone : CastBridge > « Activer la TV » (Bluetooth). Aucun fichier à manipuler.
2. Le fichier de la clé USB, choisi à la télécommande avec le bouton « Choisir le fichier d'activation (explorateur) » : le nom n'a aucune importance (`activation.txt` ou `activation (1)` conviennent). Ou, sans rien choisir, un fichier nommé exactement `activation` (sans extension, sans espace, sans « (1) ») :
   - `Download/CastBridge/activation` ou `Download/activation` (lisible seulement si Android l'autorise) ;
   - ou, toujours lisible : `Android/data/castbridge.receiver/files/activation` ou `Android/data/castbridge.receiver/files/CastBridge/activation` de la clé.
3. Coller la clé dans le champ de l'écran d'activation, puis « Valider la clé ».

Le fichier contient la clé sur une seule ligne (BOM, fin de ligne Windows et ligne finale tolérés), 16 ko au plus, texte seulement. Il n'est jamais exécuté ni modifié.

## L'explorateur de fichiers de CastBridge-TV

Beaucoup de boîtiers n'ont pas d'explorateur système : CastBridge-TV a donc le sien (écran plein, gros caractères). Haut/bas : parcourir ; OK : ouvrir un dossier ou choisir un fichier ; Retour : dossier parent, puis liste des volumes (« Clé USB A379-E209 », « Stockage interne »), puis sortie. Dossiers d'abord, puis fichiers de 16 Kio au plus, puis les plus gros (affichés, refusés). Il s'ouvre sur le premier emplacement utile listable : `Download/CastBridge`, `Download`, puis `Android/data/castbridge.receiver/files`. Il ne crée, ne modifie et ne supprime rien.

- Si Android refuse de lister un dossier, une ligne le dit (permission de stockage) et propose l'action : Android 12 et moins : autorisation de lecture demandée au premier usage ; Android 11 et plus : bouton facultatif « Autoriser l'accès aux fichiers » (écran système, seulement s'il existe). Jamais bloquant ; le dossier propre `Android/data/castbridge.receiver/files` de chaque volume reste lisible sans aucune permission.
- Fichier choisi : 16 Kio au plus, texte UTF-8 seulement (un binaire est refusé), première ligne = la clé, vérifiée comme une clé collée ; jamais exécuté ni copié ailleurs que dans le magasin privé de la TV. Annuler dit « Aucun fichier choisi » et ne change rien.
- Un second bouton « Explorateur du système » (sélecteur Android, `ACTION_OPEN_DOCUMENT`, ouvert sur la clé si possible) n'apparaît que si le boîtier en a un ; l'adresse du fichier n'est pas conservée. S'il disparaît entre-temps : « Cet appareil n'a pas d'explorateur de fichiers système : … » puis la recherche automatique.
- Ajouts au manifeste (APK hors magasin) : `READ_EXTERNAL_STORAGE` (jusqu'à l'API 32), `MANAGE_EXTERNAL_STORAGE` (seulement proposé par le bouton ci-dessus), requêtes `OPEN_DOCUMENT` et `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`, activité `FilePickActivity`.

## Ce que fait la recherche

- Chemins essayés, dans l'ordre : `Download/CastBridge/activation`, `Download/activation` (dossier public et chaque volume), puis le dossier propre de chaque volume monté. Le premier fichier accepté gagne ; un fichier refusé (périmé, autre TV) n'empêche pas d'en trouver un bon plus loin.
- Elle repart toutes les 15 s tant que l'écran est ouvert, et dès qu'un volume est monté (diffusion système « media mounted », 1,5 s après). Une seule recherche à la fois. Rien n'est lu avant l'acceptation des conditions d'usage.
- Sous le bouton « Chercher la clé sur la clé USB », 6 lignes au plus : clé détectée (identifiant, « lecture seule » le cas échéant), nombre d'éléments visibles dans `Download/CastBridge` et dans le dossier propre, noms mal écrits repérés (`activation.txt`, `Activation`, `activation (1)`, `activation.zip` : « renommez-le « activation » »), état du fichier (introuvable, illisible, vide, trop gros, pas une clé valable, autre TV, périmée). Jamais le texte de la clé ; un nom de fichier inhabituel n'est pas recopié.

## Dépannage

| Ligne affichée | Que faire |
|---|---|
| Aucune clé USB détectée | rebrancher la clé ; attendre 15 s ; essayer un autre port |
| introuvable (Android ne laisse pas…) | déposer le fichier dans `Android/data/castbridge.receiver/files/` de la clé (le dossier existe déjà : `device-request.txt` y est) |
| Fichier trouvé sous le nom « activation.txt » | le renommer `activation` (afficher les extensions sur le PC) |
| présent mais Android refuse de le lire | le déplacer dans le dossier propre ci-dessus, ou utiliser le téléphone |
| clé d'une autre TV | la clé a été émise pour un autre code d'appareil : refaire la demande avec le code affiché |
| périmée | une clé est valable 48 h : en demander une nouvelle |
| clé en lecture seule | normal pour la recherche (lecture seule) ; pour y déposer le fichier, retirer le verrou de la clé |

## Test par le propriétaire (clé `A379-E209`)

0. Appuyer sur « Choisir le fichier d'activation (explorateur) » : la liste s'ouvre sur la clé ; descendre avec les flèches jusqu'au fichier, OK : « Clé trouvée : vérification… » puis « Activée ». Retour jusqu'à la liste des volumes, puis sortie : « Aucun fichier choisi ».
1. Écran d'activation, conditions cochées, clé branchée, aucun fichier : appuyer sur « Chercher… ». Attendu : « Recherche… » puis « Clé détectée : A379-E209 » et « introuvable » avec l'endroit où déposer.
2. Sur le PC, déposer `activation.txt` dans `A379-E209/Android/data/castbridge.receiver/files/`, rebrancher : au plus 15 s, ligne « renommez-le « activation » ».
3. Renommer en `activation` : « Clé trouvée : vérification… » puis « Activée ».
4. Mettre la clé du fichier d'une autre TV : « celle d'une autre TV ».

Vérifié en JVM : recherche, états, lignes, absence d'écriture et de fuite (`ActivationLookupTest`), plan de choix, navigation et décision sur le fichier choisi (`FilePickingTest`). Vérifié par compilation seulement : écrans (dont `FilePickActivity`), permissions, diffusion de montage, lecture réelle d'une clé exFAT. À confirmer sur la TV réelle : que `getExternalFilesDirs` expose bien le volume `A379-E209` et que le fichier déposé par le PC y est lisible.
