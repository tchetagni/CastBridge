# Demande d'appareil de la TV, lue depuis le téléphone ordinaire

Décision du propriétaire (2026-10-04) : « le phone ne pourra lire que les infos relatives à la génération de la clé et rien d'autre », et « la demande d'appareil complète ». Le téléphone ordinaire (CastBridge, pas la variante propriétaire) peut donc lire, copier et partager la demande d'appareil de la TV. Il ne lit rien d'autre.

## Ce que le téléphone lit (uniquement cela)

| Champ | Contenu |
|---|---|
| `code` | code d'appareil `XXXX-XXXX-XXXX-XXXX` (somme de contrôle incluse) |
| `k` | seuil de reconnaissance (k facteurs sur n) |
| `factors` | une ligne par facteur : type (`FLASH`, `ETHERNET`, `WIFI`, `SYSTEM_SERIAL`, `BLUETOOTH`) et empreinte salée de 32 caractères hexadécimaux |
| `install` | clé publique d'installation X25519 (64 hexadécimaux), absente sur une TV ancienne |
| `install_sig` | clé publique Ed25519 (64 hexadécimaux) avec laquelle la TV signe sa preuve de possession du portefeuille (`bind`) : ligne `install_sig=ed25519\|…`, additive, que l'émetteur **signe dans l'activation de production** (droit `ik`, correctif de l'audit w23-05) ; absente sur une TV plus ancienne que ce correctif |

Les empreintes sont des hachés salés, jamais une valeur matérielle brute. La clé d'installation est publique : elle n'est pas secrète, mais seuls les outils du propriétaire s'en servent (clés de location).

## Ce que le téléphone ne lira jamais par ce chemin

Nom ou modèle de la TV, version, édition et durée, état d'activation ou de notification, portefeuille, réseau, stockage, nombre de téléphones, synchronisations, diagnostics, code à 6 chiffres, jeton d'un téléphone de confiance, clé privée, mot de passe propriétaire. Aucune commande du propriétaire n'est offerte (ni déverrouillage, ni remise à zéro, ni transfert) : l'écran ne fait qu'un `GET`.

## La route de la TV

`GET /api/tv/device-request` (CastBridge-TV, additive, lecture seule).

- Même authentification que toutes les routes : en-tête `X-CB-Pin` (code de la TV) ou `X-CB-Token` (téléphone de confiance). Sans l'un des deux : 401. Autre méthode que GET : 405.
- Réponse : exactement `{"code":…,"k":…,"factors":[{"type":…,"fingerprint":…}],"install":…|null,"installSig":…|null}` (la sixième clé est additive : un lecteur plus ancien ignore un champ inconnu). La TV relit sa propre demande et ne réémet que ces cinq clés : une ligne inconnue (TV plus récente, erreur) ne sort jamais. Chaînes passées par l'assistant JSON.
- Taille : moins de 1 Kio en pratique ; le téléphone refuse toute réponse de plus de 4096 caractères.
- Ouverte à l'édition d'essai (une TV d'essai est précisément celle qu'on active).
- Une TV plus ancienne répond 404 : le téléphone dit de mettre CastBridge-TV à jour.

### La même demande sur une TV verrouillée (2026-10-07)

Une TV **verrouillée** n'a pas cette route (403 « locked »). Elle en a une autre, `GET /api/activation/device-request` (`LockedActivationApi`, détail dans `docs/TV-ACTIVATION-CLE-USB.md`) : mêmes gardes que son installation de clé (code de connexion à 6 chiffres obligatoire, conditions d'usage, plafonds), réponse en `text/plain; charset=utf-8`, la demande **complète** (même texte que « Copier la demande complète ») : lignes `code=`, `k=`, `factor=`, `install=` (clé publique X25519, quand la TV a sa clé) et `install_sig=`. **Amendé le 2026-10-07 (act-fix-1)** : la route retirait d'abord `install=` (jugée privée, et un ancien serveur refusait les lignes inconnues) ; c'est une clé **publique**, et une clé d'essai en enveloppe v2 l'exige (un essai « s'installe » donc par le code). La ligne est absente, jamais vide, quand la TV n'a pas encore sa clé. Le texte est reconstruit à partir de la demande analysée (`DeviceRequestText.complete`), jamais recopié ; la variante sans `install=` (`DeviceRequestText.forServer`) ne sert qu'à l'envoi au serveur (voie B2, future). Le téléphone la lit par le groupe Wi-Fi Direct d'activation de la TV (192.168.49.1) ou par le réseau local, et la parse avec le même `OwnerFrames.parseDeviceInfo` ; son texte partagé et son QR (s'il tient : 271 octets, soit 1 facteur avec `install=`) sont cette demande complète.

## L'écran du téléphone

Nom : « Demande d'appareil de la TV ». Accès : bouton « Demande d'appareil » de la barre de l'écran d'accueil (à côté de « Activer la TV »), et menu « ⋮ » de la télécommande.

Autorisation : le téléphone doit être de confiance pour la TV (liaison HELLO Bluetooth, jeton géré par la liaison) ou détenir déjà le code de la TV dans le carnet de codes. Sinon : aucune requête n'est envoyée et l'écran explique quoi faire. Les refus de la TV (401, 403, 404, 429, injoignable) ont chacun leur explication ; aucun code ni jeton n'y figure.

Contenu : lignes françaises (code, seuil, un facteur par ligne, clé d'installation), puis le texte copiable, retours à la ligne conservés, empreintes entières en police à chasse fixe, défilement horizontal et vertical si besoin. Le texte est celui de `ActivationCenter.requestText()`, octet pour octet (même fonction `OwnerFrames.deviceInfo`).

Actions, dans l'ordre du focus :

1. **Copier la demande complète** (action principale) : `code=`, `k=`, `factor=…`, `install=x25519|…`. C'est le format que lisent les outils du propriétaire (`DeviceRequest.parse`).
2. **Partager la demande complète** : même texte, par la feuille de partage Android.
3. **Copier pour le serveur** (secondaire) : sans la ligne `install=` (le serveur n'en a pas l'usage : il la tolère et l'ignore depuis le 2026-10-04, `DeviceRequestInstallLineTest`) mais **avec** `install_sig=` (le serveur la signe dans l'activation qu'il émet). Un serveur d'avant le correctif refuse toute ligne autre que `code`, `k` et `factor` (« Demande d'appareil : ligne inattendue ») : mettez le serveur à jour avant la TV.

## Fichiers

Cœur : `core/trust/TvDeviceRequest.kt` (modèle, lecteur strict, textes, porte d'autorisation), `core/tv/TvDeviceRequestApi.kt` (route de la TV). TV : une ligne dans la chaîne d'extensions de `TvService`. Téléphone : `sender/TvDeviceRequestActivity.kt` (déclarée dans le manifeste), entrées dans `MainActivity` et `RemoteScreen`. Tests : `TvDeviceRequestTest`, `TvDeviceRequestServerTest`.

## Vérification

JVM : analyse de la réponse (champs manquants tolérés, champs inconnus ignorés, réponse trop grosse refusée, chaînes jamais rendues sans contrôle), textes (copie sans PIN ni jeton, test à valeur canari), porte d'autorisation (non autorisé ⇒ aucune requête), route sur un vrai `ReceiverServer` (authentification, liste blanche stricte de cinq clés, canaris d'autres champs absents, taille). Les écrans Android ne sont vérifiés que par compilation.

## Étapes de test (propriétaire, téléphone et TV réels)

1. Téléphone de confiance pour la TV, même Wi-Fi : ouvrir « Demande d'appareil » : le code d'appareil affiché est celui de l'écran d'activation de la TV.
2. « Copier la demande complète », coller dans une note : quatre à six lignes `code=`, `k=`, `factor=`, `install=x25519|…`, identiques à celles de la TV.
3. « Copier pour le serveur » : mêmes lignes sans `install=` ; l'émission d'une clé sur le serveur l'accepte.
4. Retirer la confiance et le code (nouveau téléphone) : l'écran dit que le téléphone n'est pas autorisé, la TV n'a reçu aucune requête.
5. Saisir un mauvais code : message d'explication, aucun code affiché.
6. TV d'essai : l'écran fonctionne aussi.
