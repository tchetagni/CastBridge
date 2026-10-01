# Console du propriétaire (version propriétaire du téléphone) : spécification et risques

> **Statut : conception + vérificateur testé dans `core`** (`castbridge.core.owner`, `OwnerTest`). L'écran Android, le service Bluetooth de la TV et la lecture des facteurs matériels ne sont **pas** écrits ici (le coordinateur compile). Cadre général : [TRIAL-EDITION.md](TRIAL-EDITION.md).
> **Le code de déverrouillage du propriétaire ne figure nulle part dans le dépôt** (ni code, ni cahier, ni journal, ni commit) : il est choisi par le propriétaire à la mise en service de la version propriétaire et jamais demandé ni supposé.

## 1. Ce que veut le propriétaire
Une application téléphone **indistribuable**, à droits de superadmin, à accès **authentifié**, qui commande par **Bluetooth** n'importe quelle appli CastBridge-TV et peut tout débloquer jusqu'à « tout ouvert ». Conception retenue (rien n'est altéré sans le dire).

## 2. Principes
1. **Aucune porte dérobée.** L'APK distribué (téléphone et TV) ne contient **aucun secret** ni chemin superadmin caché. La TV n'embarque que des **clés publiques** (`KeyRing` : plusieurs clés, chacune avec un **pouvoir maximal**, plus une **liste de révocation**) et n'exécute une commande que si elle est **signée** (Ed25519).
2. **Une commande est liée à une TV et à un instant** : elle porte l'**ensemble des empreintes** de la TV cible (acceptée si k parmi n correspondent), et le **défi** que **cette TV** vient d'émettre (aléatoire, **usage unique**, valable 2 minutes sur une **horloge monotone** : une horloge murale fausse ne change rien). Un rejeu, ou une commande pour une autre TV, est refusé.
3. **Gabarit « propriétaire »** (build flavor) du **même code téléphone**, autre identifiant d'application, **jamais publié** sur le canal de mise à jour (le serveur de mises à jour ne le référence pas ; la signature de l'APK propriétaire est distincte).
4. **Pouvoirs gradués** (`Power`) : `SUPPORT` (diagnostic, remise à zéro de l'essai : aucun contenu), `UNLOCK` (lots ou bouquets précis, durée limitée **≤ 30 jours**), `OPEN_ALL` (« tout ouvert », **≤ 30 jours, réglable à chaque déblocage** dans le champ `days` de la commande signée, **borné à 30 jours par le vérificateur côté TV**, renouvelable par une nouvelle commande). À l'expiration, la TV revient aux droits acquis (essai + achats à la carte + abonnement valide) **sans rien supprimer**. (La limite de 30 jours de `UNLOCK` est mon choix par défaut, **à confirmer** : `Power.maxDays`.)
5. **Plusieurs clés publiques + révocation + rotation dès la première version + clé de secours hors ligne** : la TV accepte tout identifiant de clé du `KeyRing` ; la rotation = ajouter la nouvelle clé publique, révoquer l'ancienne (testé : l'ancienne est refusée, la clé de secours reste valable). La **clé de secours** est gardée **hors ligne** (jamais sur un téléphone), son identifiant est déjà dans l'APK TV.

## 3. Format des commandes (`OwnerCommand`, `cbo1.…`)
Texte canonique signé, relu depuis le texte même qui a été signé :
```
castbridge-owner-command-v1
keyId=<16 hex>            power=support|unlock|open_all      action=<diagnostic|reset-trial|vide>
challenge=<hex>           k=<n>        factor=<FACTEUR>|<empreinte>   (une ligne par facteur, triées)
bundles=<ids>   lots=<fonction:scope,…>   days=<n>
```
**Vérification côté TV** (`OwnerCommandVerifier`, dans cet ordre ; une commande refusée **ne consomme pas** le défi, donc une fausse commande ne peut pas brûler le défi d'une vraie) : forme canonique, clé connue, clé non révoquée, signature, **pouvoir ≤ pouvoir maximal de la clé**, TV cible conforme, action valide, **défi émis par cette TV, non expiré, jamais utilisé** ; durée **bornée** à 30 jours (`clamped` signalé à la console). Les défis dépensés sont persistés (512 derniers) pour qu'un redémarrage ne rouvre pas un rejeu.

## 4. Activation hors ligne (la console produit les clés sans serveur)
Format et vérificateur : TRIAL-EDITION.md § 6 (`Activation`, `CompactActivation`). Fenêtre d'installation **≤ 1 an**. La console, hors ligne, produit : (a) l'activation signée (essai ou production), (b) les **clés de lots enveloppées pour la TV** à partir de la **demande d'appareil** (code + empreintes, `OwnerFrames.deviceInfo`), (c) la clé saisissable en dernier recours.

## 5. Canal Bluetooth propriétaire (additif)
Un **service dédié** (`7c5e3b9a-4d2f-4c61-9b0e-cb0000000004`, après le service API `…03`), jamais mélangé aux services existants : les anciennes TV et les anciens téléphones **ne le voient pas** (rétrocompatible, rien à migrer). Trames (`OwnerFrames`) : magique `CBTO` une fois, puis `[type:1][longueur:2][charge utile]`, 4 096 octets au plus :

| Type | Sens | Contenu |
|---|---|---|
| `CHALLENGE_REQUEST` / `CHALLENGE` | console → TV / TV → console | le défi (32 hex) |
| `COMMAND` | console → TV | jeton `cbo1.…` |
| `RESULT` | TV → console | `ok` + message en français |
| `DEVICE_INFO_REQUEST` / `DEVICE_INFO` | console ↔ TV | code d'appareil + `k` + empreintes (la « demande d'appareil ») |
| `PAIR` | console → TV | le code d'appairage court affiché sur le panneau caché |
| `ACTIVATION` | console → TV | jeton `cba1.…` (phase hors ligne) |

Le service n'est ouvert que lorsque le panneau caché est révélé (écoute en permanence côté TV selon la décision du coordinateur ; l'écoute n'ouvre que **ce protocole signé**, rien d'autre).

## 6. Le panneau caché de la TV
- **Révélation** : sur l'écran de départ, **↑ ↑ ↓ ↓ ← → ← → puis OK en moins de 8 s** ; **remise à zéro à la première erreur** (si la touche d'erreur est la première de la séquence, elle démarre un nouvel essai) ; **aucun son, aucun message** sur une erreur ; les touches qui ne sont pas de navigation (volume, chiffres) sont ignorées. La séquence est une **constante paramétrable à la compilation** (`SecretSequence.DEFAULT`), changeable sans toucher au protocole. Accessible aussi plus tard par **MENU > À propos > même combinaison** (`OwnerPanelGate`).
- **Contenu une fois révélé** : nom Bluetooth de la TV, adresse, état d'écoute du service propriétaire, **code d'appairage court (6 chiffres) à usage unique** (le Bluetooth n'a pas de « fréquence » à montrer).
- **Fermeture** : seul **après 60 s**, ou à **Retour**. **Limite de tentatives : 5 révélations par minute** (silencieusement ignorées au-delà) pour que ce ne soit pas un jouet.
- **Sécurité honnête** : **la combinaison ne protège rien, elle masque seulement l'interface.** Elle ne donne **aucun pouvoir** : toute commande reste exigée signée, liée à la TV et à défi unique. Un curieux qui trouverait la séquence verrait un nom Bluetooth et un code, rien d'exploitable.
- Testé dans `core` : délai, erreur, répétition, touches parasites, séquences imbriquées, limite par minute, fermeture automatique, Retour, code d'appairage à usage unique.

## 7. Authentification de la console (le code de déverrouillage du propriétaire)
- À la **mise en service** de la version propriétaire, le propriétaire choisit lui-même son **code de déverrouillage** (une phrase de passe). **Il n'est jamais enregistré** : l'application ne garde que `VaultBlob.check` (un contrôle de validité qui coûte une dérivation complète par essai).
- La **clé de signature** (graine Ed25519) est **chiffrée au repos** (AES-256-GCM) sous une clé dérivée de la phrase de passe par une dérivation **gourmande en mémoire** : **Argon2id, m = 64 Mio, t = 3, p = 1** (≈ 0,5 à 1 s sur un téléphone moyen : un essai coûte de la mémoire, pas seulement du temps ; à ajuster à la mesure sur le S21+), ou **scrypt N = 2¹⁵, r = 8, p = 1** (32 Mio) si une bibliothèque Argon2 est refusée. Le JDK n'a ni l'un ni l'autre : le cœur définit l'interface `Kdf` et la teste avec PBKDF2 (`Pbkdf2Kdf`, **repli non gourmand en mémoire**, à ne pas utiliser en production sans beaucoup d'itérations).
- **En plus**, la clé est protégée par le **coffre de clés de l'appareil** (biométrie ou code d'écran exigé à chaque usage) : une clé du coffre **enveloppe** le blob (deux facteurs : ce que je sais + ce que je suis/possède). Ce coffre ne contient **pas** l'identité des TV (qui doit survivre à une désinstallation) : il ne protège que la **console**.
- **Compteur d'essais** (`UnlockGuard`, testé) : 3 essais libres, puis 1 s, 2 s, 4 s… (plafond 5 min), puis **verrouillage temporaire de 30 minutes après 10 échecs**, doublant à chaque nouveau lot d'échecs (plafond 24 h) ; un succès remet tout à zéro.
- **Journal d'audit** (`AuditChain`) : chaque commande (action, TV cible, résultat) est ajoutée à un journal **chaîné par hachage** : modifier ou retirer une ligne casse la chaîne (détection, pas prévention). Le journal ne contient **ni code ni clé**.

## 8. Phase hors ligne (jusqu'à 1 an) : exception assumée et ses conséquences
Pendant cette phase **aucun serveur n'est dans la boucle** : la console produit elle-même les clés d'activation et détient un **carnet de clés de lots** (les clés de contenu de chaque lot, fabriquées à la construction du contenu), **chiffré au repos avec les mêmes protections** que la clé de signature (§ 7) et **présent seulement dans la version propriétaire**.
**C'est une exception assumée** à la règle « la clé maîtresse des lots ne vit jamais dans le téléphone », **limitée à cette phase**.

| Risque | Mesure |
|---|---|
| Vol du téléphone **et** du code → tous les lots déchiffrables, toutes les activations signables | carnet et clé chiffrés (§ 7), version non publiée, compteur d'essais, biométrie/code d'écran en plus ; **rotation à la fin de la phase** (nouvelles clés de lots, nouvelle clé de signature, ancienne révoquée) |
| **Pas de détection de doublon par le serveur** | **non couvert pendant la phase** : une même demande d'appareil peut produire deux activations ; le journal d'audit de la console aide à repérer a posteriori ; le serveur reprend la détection à la migration |
| **Pas de révocation immédiate** d'un droit installé | la TV hors ligne ne verra une révocation (liste de clés) qu'à son prochain message signé ; les déblocages « tout ouvert » sont **limités à 30 jours** précisément pour cela |
| **Horloge des TV peu fiable** | `TvClock` (TRIAL-EDITION.md § 7) : dernier instant vu, détection de retour en arrière, marge, plancher signé ; les commandes n'utilisent aucune heure murale |
| Durée d'une activation hors ligne | **1 an maximum**, vérifié par la TV |
| **Migration vers le serveur** | à la fin de la phase : le serveur prend la délivrance (jetons, détection de doublon, révocation) ; les activations hors ligne arrivent à échéance ; **ce qui a été acheté reste** |

## 9. Analyse des risques
- **Clé privée de la console = point unique de défaillance.** Perte (téléphone cassé sans sauvegarde) : plus aucune nouvelle activation ; **mesure** : la **clé de secours hors ligne**, déjà acceptée par toutes les TV (`KeyRing`), et une procédure écrite de remplacement. Vol : voir § 8 ; **révocation** par message signé de la clé de secours (la TV l'applique au prochain contact).
- **TV de test en `userdebug` / APK TV modifié** : la vérification de signature **peut être retirée** d'un APK modifié ; un attaquant qui contrôle la TV contrôle ce qu'elle ouvre. Cela relève de l'**audit serveur** (le serveur ne livre que des lots chiffrés par TV et surveille les anomalies) et non d'une protection dans l'application.
- **Clonage d'identité** : celui qui connaît les facteurs d'une TV peut dériver sa KEK (TRIAL-EDITION.md § 6.5). Les facteurs soudés (flash, Ethernet) sont plus durs à usurper que les facteurs faibles.
- **Bluetooth** : le canal est exposé à toute personne à portée ; il n'accepte que des **trames signées**, le défi est à usage unique et court, les tentatives sont limitées ; un attaquant qui ne fait qu'écouter ne peut rien rejouer. Un déni de service (spam d'appairage) est limité par la limite de 5 révélations par minute.
- **Séquence cachée découverte** : sans effet (§ 6).
- **Aucun prix, aucun prestataire** n'est lié à cette console.

## 10. Écran de la version propriétaire (spécification)
1. **Verrou** : demande du code ; délai affiché si le compteur l'impose ; biométrie/code d'écran ensuite.
2. **TV** : appairage par le code court (§ 6) ; liste des TV connues (nom, code d'appareil, identité faible/forte, dernier contact).
3. **Actions** : *Diagnostic*, *Remise à zéro de l'essai*, **Débloquer** (choix de lots ou bouquets + durée ≤ 30 j), **Tout ouvert** (durée réglable ≤ 30 j, avec rappel : « revient aux droits acquis à la fin, rien n'est supprimé »), **Activation** (essai / production, canal Bluetooth / fichier pour clé USB / clé saisissable à lire à voix haute par groupes de 5).
4. **Journal d'audit** visible, exportable sans secret ; **rotation** et **révocation** de clés.
5. Bandeau permanent « Version propriétaire : ne pas diffuser ».
