# Format filaire des activations (version 1)

> **Spécification exacte**, destinée aux trois outils d'émission (application de bureau, console du téléphone propriétaire, serveur) et au vérificateur de la TV/du téléphone. Elle est **vérifiée par deux implémentations indépendantes** : le code Kotlin de `core` (`castbridge.core.owner`, `ActivationIssuer`, `ActivationVerifier`) et `tools/activation/verify_vectors.py` (Python, écrit à partir de ce seul document), sur les **78 vecteurs** de `tools/activation/test-vectors.json` (valides **et** invalides, 86 contrôles : identité, codages, activations, clé compacte, commandes, émission, **licences et transferts**). **Mêmes entrées → mêmes octets** : les signatures Ed25519 sont déterministes. **Ne jamais écrire un quatrième format.**
> Cadre : [TRIAL-EDITION.md](TRIAL-EDITION.md) · console : [OWNER-CONSOLE.md](OWNER-CONSOLE.md). **Aucun secret** : les graines des vecteurs sont des clés de **test** dérivées de textes publics.

## 0. Conventions
- Texte : **UTF-8**, fins de ligne **LF** (`0x0A`), **aucun** LF final, aucune espace superflue. Entiers : décimaux ASCII, sans zéro de tête ni signe `+`. Dates : **millisecondes depuis 1970-01-01T00:00:00Z**. Hexadécimal : **minuscules**, sans séparateur.
- **Base64** (signature) : alphabet standard, **avec** remplissage `=`. **Base64url** (charge utile) : alphabet URL-safe (`-` `_`), **sans** remplissage.
- **SHA-256**, **Ed25519 pur (RFC 8032)** : la signature porte sur les **octets UTF-8 de la charge utile canonique**.
- **Forme canonique obligatoire** : le destinataire relit les champs puis **reconstruit** le texte ; s'il n'est pas identique octet pour octet à celui reçu (ligne en plus, ordre différent, majuscules…), le jeton est refusé (`MALFORMED`).

## 1. Identité de l'appareil

### 1.1 Facteurs et empreintes
Cinq **facteurs** (type, rang de bit, « soudé » ?) : `FLASH` (0, oui), `ETHERNET` (1, oui), `WIFI` (2, non), `SYSTEM_SERIAL` (3, non), `BLUETOOTH` (4, non). L'**ordre canonique** est l'ordre des rangs de bit (celui de cette liste).

Valeurs lues : `flashSerial`, `flashCid` (`/sys/block/mmcblk0/device/serial` et `cid`), `ethernetMac`, `wifiMac` + `wifiSysfsPath` (chemin sysfs résolu de `wlan0`), `systemSerial` (`ro.serialno`, ou `androidid:<ANDROID_ID>` pour un téléphone), `bluetoothAddress`.

**Normalisation** d'une valeur : `trim`, minuscules, suppression de tous les `:` et `-`. Une valeur est **ignorée** si elle est vide, composée uniquement de `0`, de `f` ou de `x`, ou égale à l'une de : `unknown`, `null`, `none`, `n/a`, `default string`, `not specified`, `0123456789abcdef`, `123456789abcdef`, `123456789abcdef0`, `020000000000` (la MAC masquée d'Android `02:00:00:00:00:00`).

**Wi-Fi soldé ?** : le chemin en minuscules ; s'il contient `/usb` → **non** (module remplaçable) ; sinon **oui** s'il contient `/sdio`, `/mmc`, `/pci` ou `/platform` ; sinon (ou chemin absent) **non**. Un Wi-Fi non soldé n'est **pas** un facteur.

**Valeur de chaque facteur présent** : `FLASH` = les valeurs normalisées non ignorées de `flashSerial` puis `flashCid`, jointes par `+` (au moins une) ; `ETHERNET`, `WIFI` (seulement si soldé), `SYSTEM_SERIAL`, `BLUETOOTH` = la valeur normalisée.
**Empreinte** = `hex( SHA-256( "castbridge-device-v1|" + TYPE + "|" + valeur )[0:16] )` : **32 caractères hexadécimaux**. L'ensemble des empreintes est `{TYPE: empreinte}`.

### 1.2 Code d'appareil `XXXX-XXXX-XXXX-XXXX`
- `masque` = somme des `1 << rang` des facteurs présents (0 à 31) ; **hachage de l'ensemble** `H` = `SHA-256( jointure par LF de "TYPE=empreinte" dans l'ordre canonique )`.
- `corps` (15 caractères) = `ALPHABET[masque]` + les **14 premiers caractères** de `Base32(H)`.
- Le code = `corps` + **caractère de contrôle** (§ 1.4), découpé en 4 groupes de 4 séparés par `-`.
- **Saisie** : minuscules, espaces et `-` acceptés ; `O` vaut `0`, `I` et `L` valent `1` ; longueur 16 après nettoyage et contrôle exact, sinon invalide.

### 1.3 k parmi n
`n` = nombre de facteurs. `k(n)` = `n − 1` si `n ≥ 3`, sinon `max(n, 1)`. Une activation (ou une commande) portant l'ensemble `F` et `k` **correspond** à un appareil d'ensemble `D` si : au moins `k` types `t` de `F` ont `D[t] == F[t]` **et**, si `F` contient un facteur soudé (`FLASH`, `ETHERNET`), **au moins un facteur soudé** figure parmi les correspondances. `F` vide ou `k < 1` : jamais.
Identité **faible** : aucun facteur soudé dans `D`.

### 1.4 Base32 Crockford et somme de contrôle
`ALPHABET = 0123456789ABCDEFGHJKMNPQRSTVWXYZ` (valeurs 0 à 31). **Encodage** : flux de bits poids fort d'abord, par tranches de 5 bits ; le dernier groupe incomplet est complété à droite par des 0 ; **pas de remplissage**. **Décodage** : on lit les valeurs de 5 bits (avec les confusions `O→0`, `I,L→1`, majuscules), on prend les `N` octets demandés, les bits en trop sont ignorés.
**Contrôle** de la chaîne `c₀…cₘ₋₁` avec un sel `s` : `ALPHABET[ ( Σ valeur(cᵢ)·(2i+1) + 7·s ) & 31 ]` (poids impairs : toute faute de frappe simple est détectée ; un échange de deux caractères distants de 16 exactement ne l'est pas). Code d'appareil : `s = 0`.

## 2. Clés, `kid` et portées
- **Clé** Ed25519 ; la clé publique brute fait 32 octets (Base64 standard dans les fichiers). **`kid`** = `hex( SHA-256( clé publique brute )[0:8] )` (16 hexadécimaux).
- **Une clé par outil, jamais partagée** : bureau (maître, hors ligne), téléphone propriétaire, serveur. Compromission d'un outil = révoquer sa seule clé.
- **Portées** (`KeyScope`), listées avec chaque clé de confiance de la TV : `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `COMMAND_SUPPORT`, `COMMAND_UNLOCK`, `COMMAND_OPEN_ALL`, `TRANSFER`, `REVOKE`, `REGISTRY`. Recommandation : bureau = toutes ; téléphone propriétaire = toutes sauf `REGISTRY` ; **serveur** = `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REVOKE`, `REGISTRY` (**pas** de « tout ouvert », **pas** de transfert) ; support = `COMMAND_SUPPORT`.
- L'**anneau de clés** de la TV = ensemble de `(kid, clé publique, portées)` + liste de **révocation** (`kid` révoqués). Une clé inconnue, révoquée ou sans la portée requise est refusée. Plusieurs clés valides à la fois (rotation, clé de secours hors ligne).

## 3. Activation (`cba1`)

### 3.1 Jeton
`cba1.` + `Base64url(charge utile)` + `.` + `Base64(signature de 64 octets)` (une seule ligne, ASCII).

### 3.2 Charge utile canonique (une ligne par champ, dans cet ordre exact)
```
castbridge-activation-v1
kind=<trial|production>
subject=<tv|phone>
kid=<16 hex>
nonce=<8 à 64 hex>
issuedAt=<ms>
notBefore=<ms>
notAfter=<ms>
license=<identifiant>
seat=<hex>
k=<entier>
factor=<TYPE>|<32 hex>         (une ligne par facteur, ordre canonique)
right=<droit>                   (une ligne par droit, triées par ordre lexicographique des octets)
```
`identifiant` = `[a-z0-9][a-z0-9-]{0,63}`. `license` vaut `trial` pour une clé d'essai ; pour la production, c'est l'identifiant de la **licence** (§ 8). `seat` = **identifiant de poste** (8 octets en hexadécimal) : `hex( SHA-256( "castbridge-seat|" + license + "|" + hex(H) )[0:8] )` pour un **nouveau** poste, où `H` est le hachage de l'ensemble de facteurs (§ 1.2) ; un poste existant **garde son identifiant** (registre, § 8).

### 3.3 Droits (`right=`)
| Droit | Syntaxe | Remarques |
|---|---|---|
| Achat à la carte | `purchase\|<produit>\|<bouquet,bouquet…>\|<grantedAt ms>` | définitif ; bouquets triés |
| Abonnement | `subscription\|<produit>\|<bouquets>\|<début ms>\|<fin ms>\|<tolérance ms>\|<1 si renouvellement auto, sinon 0>` | tolérance ≤ 30 jours |
| Tout ouvert | `openall\|<produit>\|<début ms>\|<fin ms>` | **≤ 30 jours**, bouquet `tout`, clé avec `COMMAND_OPEN_ALL` |
Une clé d'**essai** ne porte **aucun** droit ; une activation de production en porte **au moins un**. Aucun prix, aucun prestataire dans le format.

### 3.4 Durées
`notBefore`–`notAfter` = **fenêtre d'installation** (≤ **366 jours**, phase hors ligne) : au-delà l'activation ne peut plus être *installée* ; **une fois installée elle reste** (un achat est définitif, un abonnement porte sa propre fin). Marge d'horloge à l'installation : 24 h au début.

### 3.5 Vérification (dans cet ordre ; premier échec = raison)
1. `MALFORMED` : jeton ou forme canonique invalide. 2. `UNKNOWN_KEY` : `kid` absent de l'anneau. 3. `REVOKED_KEY` : `kid` révoqué. 4. `BAD_SIGNATURE`. 5. `KEY_NOT_ALLOWED` : sans `ISSUE_TRIAL` (essai) / `ISSUE_PRODUCTION` (production), ou « tout ouvert » sans `COMMAND_OPEN_ALL`. 6. `BAD_RIGHTS` : essai avec droits, « tout ouvert » > 30 jours ou de durée ≤ 0. 7. `WRONG_SUBJECT` : `subject` ≠ type de l'appareil. 8. `WINDOW_TOO_LONG` : `notAfter − notBefore` > 366 jours. 9. `WRONG_DEVICE` : le matériel ne correspond pas (§ 1.3) — c'est un appareil **suspect** : il garde ce qu'il a et propose le déblocage manuel, **jamais un refus net**. 10. `REVOKED_SEAT` : le poste a été révoqué à une date ≥ `issuedAt` (§ 7). 11. `NOT_YET_VALID` : `max(maintenant, issuedAt) + 24 h < notBefore`. 12. `WINDOW_CLOSED` : `max(maintenant, issuedAt) > notAfter`.
`maintenant` = le temps retenu par la TV (§ 6) : **`issuedAt` est un plancher** (un message signé prouve que le temps l'a atteint), donc une TV dont l'horloge indique 1970 accepte quand même une activation récente.

## 4. Codages de transport

| Canal | Contenu exact |
|---|---|
| **Fichier** | `Download/CastBridge/activation` : le jeton `cba1…` + un LF. Lu au démarrage et à l'insertion de la clé USB. |
| **Bluetooth** | service propriétaire `7c5e3b9a-4d2f-4c61-9b0e-cb0000000004` : trame `ACTIVATION` (type 8) dont la charge utile est le jeton ASCII (§ 5). |
| **QR** | le jeton tel quel (ASCII, une ligne). |
| **Texte saisissable complet** | `GroupedText` : octets ASCII du jeton précédés de leur **longueur sur 2 octets (grand-boutiste)**, en Base32 Crockford, complétés par des `0` à un multiple de 4 caractères, puis groupes de **5** = 4 de données + 1 contrôle (sel = rang du groupe, à partir de 1), séparés par `-`. Très long (≈ 1 000 caractères) : à réserver à un copier-coller. |
| **Clé compacte** (saisie manuelle) | § 4.1 ; **165 caractères**, 33 groupes. |

### 4.1 Clé compacte (dernier recours)
18 octets d'en-tête + 64 de signature = **82 octets**, Base32 Crockford complété à 132 caractères puis 33 groupes de 4 + 1 contrôle (sel = rang du groupe).
| Octets | Champ |
|---|---|
| 0 | version = 1 |
| 1 | type : 0 essai, 1 production |
| 2–3 | **étiquette de clé** : `SHA-256(kid en ASCII)[0:2]` (grand-boutiste) |
| 4–5 | début de fenêtre en **jours depuis 2026-01-01T00:00Z** (`1767225600000` ms), grand-boutiste |
| 6–7 | durée de la fenêtre en jours (≤ 366) |
| 8–9 | **ensemble de produits** (0 = clé d'essai ; autres = ensembles nommés du catalogue) |
| 10–17 | **liaison** : `SHA-256("castbridge-bind|" + code d'appareil normalisé)[0:8]` |
| 18–81 | signature Ed25519 de la chaîne ASCII `"castbridge-activation-compact-v1\n" + hex(en-tête de 18 octets)` |
**Liaison stricte** au code d'appareil (pas de k parmi n : une clé saisie ne peut pas porter l'ensemble de facteurs), **aucune** liste de droits, **aucune** clé de lot (le contenu chiffré demande l'activation complète). Vérification : groupe par groupe (le groupe fautif est désigné), clé retrouvée par étiquette, `UNKNOWN_KEY`, `REVOKED_KEY`, `BAD_SIGNATURE`, `KEY_NOT_ALLOWED`, `WRONG_DEVICE`, `WINDOW_TOO_LONG`, `NOT_YET_VALID`, `WINDOW_CLOSED`.

## 5. Commandes du propriétaire (`cbo1`) et canal Bluetooth

### 5.1 Commande
Jeton `cbo1.` + Base64url(charge utile) + `.` + Base64(signature). Charge utile :
```
castbridge-owner-command-v1
kid=<16 hex>
power=<support|unlock|open_all>
action=<diagnostic|reset-trial|(vide)>
challenge=<16 à 64 hex>
k=<entier>
factor=<TYPE>|<32 hex>         (ordre canonique)
bundles=<ids triés, séparés par des virgules, ou vide>
lots=<fonction:scope triés, séparés par des virgules, ou vide>
days=<entier>
```
**Vérification** (ordre) : `MALFORMED`, `UNKNOWN_KEY`, `REVOKED_KEY`, `BAD_SIGNATURE`, `KEY_NOT_ALLOWED` (portée `COMMAND_SUPPORT` / `COMMAND_UNLOCK` / `COMMAND_OPEN_ALL` absente), `WRONG_DEVICE`, `BAD_COMMAND` (action de support inconnue ; déblocage sans contenu ; `days < 1` hors support), puis **`REPLAY`** (défi inconnu de cette TV, périmé ou déjà utilisé) en **dernier** : une commande refusée ne consomme pas le défi. Durée retenue = `min(days, 30)` (`support` : aucune) ; fin = `temps TV + durée`.
**Défi** : généré par **la TV** (16 octets aléatoires, 32 hex), valable **120 s sur une horloge monotone** (aucune heure murale : une horloge fausse ne change rien), **usage unique**, 8 défis ouverts au plus, les 512 derniers défis dépensés mémorisés.

### 5.2 Trames du canal Bluetooth propriétaire
Après la **poignée** `CBTO` (4 octets ASCII), des trames `[type : 1 octet][longueur : 2 octets grand-boutiste][charge utile, ≤ 4096]` : `1` CHALLENGE_REQUEST (vide), `2` CHALLENGE (32 hex), `3` COMMAND (jeton `cbo1…`), `4` RESULT (`[ok : 1]` + message UTF-8), `5` DEVICE_INFO_REQUEST (vide), `6` DEVICE_INFO, `7` PAIR (code à 6 chiffres), `8` ACTIVATION (jeton `cba1…`).
`DEVICE_INFO` = le texte `code=<code d'appareil>` LF `k=<k>` LF puis une ligne `factor=<TYPE>|<empreinte>` par facteur : la **demande d'appareil**, dont l'émetteur a besoin (le code seul ne suffit pas à construire l'activation ni les clés de lots).

## 6. Temps sur la TV (rappel pour les émetteurs)
La TV retient `max(horloge, dernier instant vu, plancher signé)` ; un retour en arrière ne change rien ; un saut en avant de plus de **400 jours** n'est pas cru tant qu'un message signé ne le confirme pas (`TvClock`). Un émetteur choisit donc **`issuedAt` = l'heure réelle de l'émission** (jamais dans le futur) et une fenêtre `notBefore` ≤ `issuedAt`.

## 7. Révocation (`cbr1`)
Liste signée de ce qu'un appareil doit **oublier** : des **clés** (`kid`) et des **postes** (`licence|poste` avec une date). Jeton `cbr1.` + Base64url(charge utile) + `.` + Base64(signature) ; charge utile (une ligne par champ, clés triées puis postes triés) :
```
castbridge-revocation-v1
kid=<signataire>
issuedAt=<ms>
key=<kid révoqué>              (0 ou plusieurs, triées)
seat=<licence>|<poste>|<date ms>   (0 ou plusieurs, triées)
```
Signée par une clé **connue, non révoquée, portée `REVOKE`**, forme canonique obligatoire. L'appareil la **fusionne** à son état (`RevocationState` : union des clés, maximum des dates par poste) et l'applique à toute activation : clé révoquée → `REVOKED_KEY` ; **poste** révoqué si `issuedAt` de l'activation **≤ date** (une activation **réémise après** la révocation pour le même poste est valide : c'est ainsi que le nouveau matériel d'un transfert est accepté). Livrée par Bluetooth, par fichier sur la clé USB ou par le serveur à la connexion suivante. **Limite honnête** : hors ligne, un appareil ne l'apprend qu'à son prochain message signé.

## 8. Licences, postes et registre
Une **licence** est l'unité de décompte : un `licenseId` par achat, un **nombre de postes**, un **plafond de transferts par an** (2 par défaut, réglable), les droits portés par ses activations. Un **poste** = « cette licence sur ce matériel » (téléphone et TV sont des postes distincts : `subject`). Les clés d'**essai** (`license=trial`) sont journalisées mais **ne comptent jamais** comme poste.

Tout ce que font les outils s'écrit sous forme d'**événements signés** ; le **registre** est l'ensemble des événements (fichier JSON, § 9). Rejouer les événements donne l'état : **la fusion de deux registres est une union**, sans doublon, **indépendante de l'ordre**.

### 8.1 Événement
```
castbridge-licence-event-v1
type=<license|issue|transfer|revoke>
kid=<signataire>
at=<ms>
… champs du type …
factor=<TYPE>|<32 hex>     (issue et transfer : l'ensemble des empreintes, ordre canonique)
```
Signature **Ed25519** des octets UTF-8 du texte ; **identifiant** de l'événement = `hex( SHA-256( kid + "|" + texte )[0:8] )`. Champs : `license` (`license`, `seats`, `maxTransfersPerYear`) ; `issue` (`license`, `seat`, `subject`, `kind`, `nonce`, `notAfter`, `k`, `at` = `issuedAt` de l'activation) ; `transfer` (`license`, `seat`, `k`, `nonce`, `at`, facteurs = **nouveau** matériel) ; `revoke` (`target` = `key` ou `seat`, `value` = `kid` ou `licence|poste`).
**Portée exigée** : `license` et `issue` → `ISSUE_PRODUCTION` (`ISSUE_TRIAL` si `kind=trial`) ; `transfer` → **`TRANSFER` (jamais la clé serveur)** ; `revoke` → `REVOKE`.

### 8.2 Rejeu (règles exactes, identiques dans les trois outils)
Dédoublonner par identifiant, trier par `(at, identifiant)`, puis pour chaque événement : clé inconnue → `UNKNOWN_KEY` ; clé révoquée → `REVOKED_KEY` ; signature fausse → `BAD_SIGNATURE` ; type inconnu → `MALFORMED` ; portée absente → `KEY_NOT_ALLOWED` (l'événement est **ignoré** et listé dans les rejets). Puis :
- `license` : crée la licence (le premier gagne).
- `issue` : licence `trial` → ignoré ; licence inconnue → `UNKNOWN_LICENSE` ; poste déjà connu (ou fusionné) → mise à jour ; sinon, si un poste **du même type d'appareil** de la licence **correspond au matériel** (k parmi n, § 1.3) sous un autre identifiant → **doublon matériel** : le plus ancien est gardé, l'autre devient son alias, **compté une fois** ; sinon **nouveau poste** (avertissement si on dépasse le nombre de postes : **signalé, jamais caché**, l'activation étant déjà émise).
- `transfer` : licence ou poste inconnu → `UNKNOWN_LICENSE` ; si les transferts de la licence sur l'**année glissante** (date > `at − 365 jours` et ≤ `at`) atteignent le plafond → `TRANSFER_LIMIT` (rejeté) ; sinon le poste prend les **nouveaux facteurs** et une **révocation du poste** est enregistrée à la date `at` (l'ancienne activation, émise avant, est révoquée).
- `revoke` : ajoute la clé ou le poste (date) aux révocations.

### 8.3 Ré-activation (même matériel, sans consommer un poste)
`plan(licence, sujet, facteurs)` : si un poste de cette licence et de ce sujet **correspond** au matériel (au moins k facteurs sur n, § 1.3, donc même après le remplacement d'un module) → **réutiliser ce poste** (`seat` inchangé, nouveau `nonce`/`issuedAt` : **aucun poste consommé**, quel que soit l'outil qui répond) ; sinon, s'il reste un poste → **nouveau poste** (`seat` par défaut `SHA-256("castbridge-seat|licence|hex(H)")[0:8]`, **identique dans les trois outils**) ; sinon **refus** `NO_SEAT_LEFT` (il faut un transfert). Côté TV, une application **réinstallée** sur le même matériel **retrouve** son fichier `activation` (clé USB `Download/CastBridge/`, copie gardée par le téléphone qui l'a livré, renvoi par Bluetooth) et le **revérifie hors ligne** (§ 3.5) ; si le fichier est perdu, n'importe quel outil réémet **la même activation** pour le même code d'appareil.

### 8.4 Transfert
Événement `transfer` (portée `TRANSFER` : bureau et téléphone propriétaire, **jamais le serveur** ; un registre qui contient un transfert signé par une clé sans cette portée le **rejette** à la fusion). Effets : le poste suit le nouveau matériel, l'ancienne activation est révoquée (à `at`) dès que la **liste de révocation** (§ 7) atteint l'ancienne TV ; une **nouvelle activation** est émise pour le nouveau matériel **avec `issuedAt` > `at`** (même `license` et même `seat`). Plafond : **2 par licence et par an** (réglable par licence).
**Limite honnête** : hors ligne, l'ancienne TV **n'apprend pas** qu'elle a perdu ses droits ; la protection est **comptable** (le registre dit qui a quel poste). En ligne, le serveur compte les postes et peut pousser une révocation que la TV applique à sa prochaine connexion.

## 9. Fichier de registre (synchronisation des trois outils)
```json
{"format": "castbridge-licence-registry-v1",
 "events": [ {"id": "<16 hex>", "kid": "<16 hex>", "text": "<texte canonique de l'événement>", "signature": "<Base64>"} , … ]}
```
Export : événements triés par `(at, id)`, sans doublon. **Import** : on **ignore** (jamais on ne se fie à) toute entrée dont l'`id` ne correspond pas au texte ; les **signatures sont vérifiées au rejeu**, pas à l'import. **Fusion** = union par identifiant ; l'état (`postes`, `utilisés`, `transferts`, `doublons`, `rejets`, `révocations`) est le même quel que soit l'ordre ou le nombre de fusions (testé : trois journaux fusionnés dans deux ordres, fusion répétée). Qui a émis quoi : chaque événement `issue` porte le `kid` de l'outil ; un **même poste émis par deux outils** apparaît dans `issuedBy` et, s'il l'a été sous deux identifiants, dans les **doublons**.

## 10. Messages d'erreur d'émission
`ActivationIssuer` refuse (`IssueException`, message français) : code d'appareil mal formé ou ne correspondant pas aux empreintes, durée hors de 1 à 366 jours, droit sans produit ou sans bouquet valide, abonnement dont la fin précède le début ou tolérance > 30 jours, « tout ouvert » > 30 jours ou sans la portée, clé d'essai avec des droits, production sans droit, portée de la clé insuffisante, nonce ou poste mal formés. Les vecteurs `build-activation` contiennent des **refus** attendus.

## 11. Vecteurs de test
`tools/activation/test-vectors.json` : `keys` (graines de test, clés publiques, `kid`, portées), `devices` (facteurs bruts, empreintes, code), puis `cases` de types : `fingerprints`, `base32`, `grouped`, `grouped-decode`, `device-code-parse`, `activation` (jeton, appareil, instant, clés de confiance, clés révoquées, postes révoqués → résultat et raison), `compact`, `command` (défis ouverts, étapes successives : rejeu), `licence` (événements signés → postes utilisés, doublons, transferts, rejets, plans de ré-activation), `build-activation`, `build-compact`, `build-command` (**mêmes entrées → mêmes octets**, dont des **refus** : durée hors bornes, portée absente, code mal formé, droit hors limite). Un outil est conforme s'il passe **tous** les vecteurs. `python3 tools/activation/verify_vectors.py` en est une implémentation de référence (à lire avant d'écrire la sienne). **Régénération** (changement de format voulu) : `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ActivationVectorsTest*'`.
