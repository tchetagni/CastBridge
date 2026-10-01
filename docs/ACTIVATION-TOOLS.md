# Outils d'activation (bureau, téléphone propriétaire, serveur)

> Guide du **propriétaire** : comment activer une TV, pas à pas. Format exact : [ACTIVATION-FORMAT.md](ACTIVATION-FORMAT.md) ; cadre : [TRIAL-EDITION.md](TRIAL-EDITION.md), [OWNER-CONSOLE.md](OWNER-CONSOLE.md).
> **Aucun secret dans le dépôt** : la clé privée du bureau n'existe que chiffrée dans votre dossier personnel, jamais ici.

## 1. Les trois outils, une seule définition
| Outil | Où | Clé | État |
|---|---|---|---|
| **Bureau** (Mac, Windows, Linux) | `tools/activation-desktop/` (Kotlin/JVM, Java 17+) : ligne de commande **et** fenêtre | clé **maîtresse**, toutes les portées, chiffrée sur disque (scrypt 32 Mio) | livré, testé |
| **Téléphone propriétaire** | noyau `core/.../owner/PhoneConsole.kt` (testé) ; l'écran Android est à compiler | clé **du téléphone**, toutes les portées sauf `REGISTRY` | noyau livré, **écran à écrire/compiler** |
| **Serveur** | `backend/` (agent `license-admin`) | clé **serveur** : essai, production, révocation, registre ; **jamais** « tout ouvert » ni transfert | autre chantier |

Les trois partagent **la même bibliothèque** (`ActivationIssuer`, `LicensedIssuer`, `RightsSyntax` dans `core`) et **les mêmes 65 vecteurs** (`tools/activation/test-vectors.json`) : mêmes entrées, mêmes octets. Chaque outil a **sa propre clé** : la compromission d'un outil se règle en révoquant sa seule clé.

## 2. Activer une TV avec le bureau (pas à pas)
Prérequis : Java 17 ou plus. Lancer `java -jar castbridge-activation-desktop.jar` (fenêtre) ou ajouter une commande (ligne de commande ; `aide` les liste).

1. **Une fois** : créer votre clé. Fenêtre : onglet « Clé » ; ligne de commande : `cle-creer`. Choisissez un code de déverrouillage de **10 caractères au moins** (une phrase). **Notez** le `kid` et la clé publique affichés. Les deux sont publics : la TV les met dans son anneau de clés (jamais le fichier de clé).
2. **Sauvegarde, tout de suite** : copiez `~/.castbridge-activation/desk.key.json` sur une clé USB **hors ligne**, rangée dans un lieu sûr ; notez le **code à part** (autre lieu). Sans le fichier **ou** sans le code, la clé est perdue (voir § 5 : clé de secours).
3. **Obtenir la demande d'appareil de la TV** : sur la TV (écran d'activation de CastBridge-TV, ou par le téléphone propriétaire en Bluetooth), copiez le texte `code=… / k=… / factor=TYPE|empreinte` (c'est la « demande d'appareil », sans aucun secret). Le **code d'appareil** seul (`XXXX-XXXX-XXXX-XXXX`) ne suffit pas, sauf pour la clé saisissable (§ 4).
4. **Essai** : fenêtre, onglet « Émettre », collez la demande, type « Essai », durée (jours), saisissez votre code, « Générer ». En ligne de commande :
   `emettre --appareil demande.txt --jours 90 --sortie ./usb --qr`
5. **Production** : créez d'abord la licence (`licence lic-0001 --postes 2`, ou onglet « Licences »), puis
   `emettre --appareil demande.txt --production --licence lic-0001 --achat p-classe-cm2=classe-cm2 --jours 60 --sortie ./usb`
   Droits : `--achat produit=bouquet1,bouquet2` · `--abonnement produit=bouquets:jours[:tolérance[:auto]]` · `--tout-ouvert produit:jours` (30 jours au plus, clé avec la portée « tout ouvert » ; **pas** la clé serveur).
6. **Livrer** (une seule voie suffit) : copiez le fichier **`activation`** dans `Download/CastBridge/` de la **clé USB de la TV** (lu au démarrage et à l'insertion) ; ou lisez le **code QR** avec le téléphone propriétaire ; ou envoyez le jeton par le téléphone propriétaire en Bluetooth ; ou copiez le texte du jeton.
7. **Vérifier** : `verifier activation --appareil demande.txt` (ou « ACCEPTÉ » à l'écran de la TV). `autotest` rejoue les vecteurs communs : il doit dire « tous identiques ».

**Règles de licence appliquées par l'outil** : un poste = « cette licence sur ce matériel » ; **ré-activer le même matériel ne consomme pas de poste** (même après le remplacement d'un module, grâce au k parmi n) ; un autre matériel prend un poste libre ; sans poste libre l'émission est **refusée** ; l'essai (`trial`) n'est jamais décompté.

## 3. Registre des licences (synchroniser les trois outils)
Tout ce que fait un outil est un **événement signé** ; le registre est l'ensemble des événements (`registre exporter fichier.json` / `registre importer fichier.json`, ou onglet « Licences »). **Fusionner = union**, sans doublon, dans n'importe quel ordre. Pour que le bureau reconnaisse les événements des autres outils : `cle --json` sur l'autre outil, puis `faire-confiance fichier.json` sur le bureau (clé **publique** seulement). `registre etat` : postes utilisés par licence et rejets (événement d'une clé inconnue, révoquée ou sans portée).
**Limite honnête** : hors ligne, une TV transférée ne sait pas qu'elle a perdu ses droits ; la protection est **comptable** (le registre dit qui a quel poste), voir ACTIVATION-FORMAT.md § 8.4.

## 4. Dernier recours : la clé saisissable
`cle-saisissable --code XXXX-XXXX-XXXX-XXXX --jours 30` produit 165 caractères (33 groupes) à taper sur la TV : liée au seul code d'appareil, sans liste de droits (essai ou ensemble de produits). La TV désigne le groupe fautif.

## 5. Risques et procédures
| Risque | Mesure |
|---|---|
| **La clé du bureau est le point unique de défaillance** (elle signe tout) | fichier chiffré (AES-GCM, clé dérivée du code par **scrypt N=2^15, r=8, p=1 = 32 Mio** par essai : une attaque hors ligne coûte de la mémoire, pas seulement du temps) ; permissions du fichier réduites au propriétaire ; **aucune** copie de la clé ou du code dans le journal, le registre ou les messages (testé) ; sauvegarde hors ligne du fichier **et** du code, séparément |
| Vol du fichier **et** du code | **rotation** : générer une nouvelle clé, l'ajouter à l'anneau des TV, **révoquer** l'ancienne (liste de révocation `cbr1`, ACTIVATION-FORMAT.md § 7). Les activations déjà installées restent valables (un achat est définitif) ; seules les **nouvelles** de l'ancienne clé sont refusées |
| Perte du fichier ou du code | **clé de secours hors ligne** (autre clé maîtresse, déjà dans l'anneau des TV) : l'utiliser pour émettre, puis faire la rotation. Prévoir la clé de secours **avant** le déploiement des TV (l'ajouter après exige de toucher chaque TV) |
| Téléphone propriétaire volé | clé propre au téléphone, chiffrée, compteur d'essais (1 s, 2 s, 4 s… puis blocage 30 min doublé) : si volé **et** code deviné, **révoquer la clé du téléphone seule** ; le bureau et le serveur ne changent pas |
| Serveur exposé | clé serveur **sans** « tout ouvert » ni transfert ; compromission = révoquer la clé serveur |
| Bureau infecté (enregistreur de frappe) | le code se saisit sur une machine de confiance ; le code ne s'écrit jamais en argument de commande (historique) : saisie au terminal, ou variable d'environnement / fichier **temporaires** |
| Attaque par le code court | 10 caractères minimum imposés ; préférer une phrase de 5 mots. **Le compteur d'essais n'existe pas sur le bureau** (un attaquant qui a le fichier l'attaque hors de l'application) : seul scrypt protège |

**Rotation pas à pas** : (1) `cle-creer --dossier nouveau` ; (2) `cle --json --dossier nouveau` → ajouter ce `kid`/clé publique/portées à l'anneau de chaque TV (mise à jour de CastBridge-TV ou commande signée par une clé encore valide) ; (3) émettre avec la nouvelle clé ; (4) quand toutes les TV connaissent la nouvelle, **révoquer** l'ancienne (`cbr1`). **Révocation** : une liste signée par une clé portant `REVOKE` (ACTIVATION-FORMAT.md § 7) ; la TV l'applique à sa prochaine réception (fichier USB, Bluetooth ou serveur).

## 6. Construire et distribuer l'outil de bureau
- **JAR exécutable** (toutes plateformes) : `cd android && gradle :activation-desktop:fatJar` → `tools/activation-desktop/build/libs/castbridge-activation-desktop.jar` (≈ 8 Mo). Aucun installateur ni Java inclus : Java 17+ requis.
- **Installateurs natifs** (`.dmg` Mac, `.msi` Windows, `.deb` Linux, avec Java embarqué) : `jpackage` doit tourner **sur chaque système** (on ne construit pas un `.msi` sur un Mac). Le flux GitHub Actions `.github/workflows/activation-desktop.yml` le fait pour les trois ; **le propriétaire doit l'activer** (déclenchement manuel, aucun secret requis ; les installateurs ne sont **pas signés** : macOS et Windows afficheront un avertissement de développeur inconnu tant que vous n'ajoutez pas vos certificats).
- **Non éprouvé ici** : seuls le JAR et la fenêtre sous Linux (écran virtuel) ont été essayés. Mac et Windows, jpackage et les installateurs : **à essayer par le propriétaire**.

## 7. Ce qui reste à faire
- Écran Android « Générer un jeton » de la console propriétaire (gabarit `owner`, autre identifiant d'application) au-dessus de `PhoneConsole` : lecture du code (saisie, QR, ou trame DEVICE_INFO reçue en Bluetooth), envoi de la trame ACTIVATION, coffre du téléphone ; **à compiler et essayer sur le téléphone**.
- Serveur : `license-admin` (hors de ce chantier).
- Transfert et révocation de poste en ligne de commande/fenêtre (événements `transfer` et `revoke` existent dans la bibliothèque `LicenseEvent`, pas encore exposés par l'outil).
