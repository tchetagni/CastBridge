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
   `emettre --appareil demande.txt --usage-jours 90 --sortie ./usb --qr` (durée de la clé : voir § 8)
5. **Production** (politique du 2026-10-02) : `emettre --appareil demande.txt --production --usage-jours 60 --sortie ./usb`. **Sans `--licence`, l'outil génère une licence** `lic-<10 caractères hexadécimaux aléatoires>` (1 poste), l'affiche (« Licence lic-… (générée) ») et la porte dans la clé. **Seule la durée compte** : `--usage-jours` 30, 60, 62, 90, 180, 300, 365, autre de 1 à 3660, ou `illimitee` (défaut) ; plus `--super` (privilège SUPER_UNLIMITED, à part). Une production **n'a besoin d'aucun droit de contenu** : elle ouvre toutes les fonctions (« Version complète ») ; les locations de lots sont l'affaire du **serveur** pour les TV déjà activées (RENTAL-LOTS.md § 15). Avec `--licence lic-0001` (licence créée par `licence lic-0001 --postes 2` ou l'onglet « Licences »), on ré-active un poste ou on en ajoute un, comme avant.
   *Options avancées (tests, outils de location ; ni la fenêtre graphique ni la console du téléphone ne les proposent plus)* : `--achat produit=bouquet1,bouquet2` · `--abonnement produit=bouquets:jours[:tolérance[:auto]]` · `--tout-ouvert produit:jours` (30 jours au plus, portée « tout ouvert » ; **pas** la clé serveur) · `--droit LIGNE` · `--location`, `--location-bouquet`, `--catalogue`, `--sans-controle-catalogue` (voir § 8, **avancé**).
6. **Livrer** (une seule voie suffit) : copiez le fichier **`activation`** dans `Download/CastBridge/` de la **clé USB de la TV** (lu au démarrage et à l'insertion) ; ou lisez le **code QR** avec le téléphone propriétaire ; ou envoyez le jeton par le téléphone propriétaire en Bluetooth ; ou copiez le texte du jeton. **Sur place, avec le téléphone propriétaire (2026-10-07)** : « Activer la TV » > le code à 6 chiffres affiché sur la TV > « Émettre et installer » : la demande est lue sans rien recopier, la console s'ouvre pré-remplie, « Installer sur la TV » pose la clé ; sans console, la même demande se **partage** (texte + QR) à l'agent, qui renvoie la clé à **coller** (voir [TV-ACTIVATION-CLE-USB.md](TV-ACTIVATION-CLE-USB.md) « Activer avec le code affiché sur la TV »).
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

## 8. Durée de la clé, lots d'essai, locations (2026-10-02)
**Durée de la clé** (droit `usage` : la TV se verrouille à la fin et demande un nouveau code valide) :
| Édition | Règle |
|---|---|
| Essai | toujours une durée : **30 jours par défaut**, 1 à 365 ; « illimitée » refusée |
| Production | **illimitée** (défaut : la TV ne se reverrouille jamais) ou 1 à **3660** jours |
| SUPER_UNLIMITED | permanent : aucune durée acceptée |

- **Fenêtre graphique** (onglet « Émettre ») : **« Essai : durée de la clé (jours) »** (champ, 30 au départ, 1 à 365, jamais illimitée) et **« Production : durée de la clé »** (liste : **Illimitée** par défaut, 30, 60, 62, 90, 180, 300, 365, **Autre…** avec un champ numérique de 1 à 3660). Seul le champ de l'édition choisie est lu. Cocher SUPER_UNLIMITED fige la liste sur « Illimitée » (aucune durée). Une saisie invalide est refusée avec un message.
- **Fenêtre graphique, production (2026-10-02)** : la partie production ne montre plus que la **durée** (liste), l'interrupteur **SUPER_UNLIMITED** et le champ facultatif **« Licence (laisser vide : générée) »** ; plus de catalogue, de liste à cocher, de zone de droits ni de locations. Le résultat dit « licence lic-… (générée) ». L'essai garde ses champs. L'onglet « Experts » est inchangé. *(Description antérieure, retirée de la fenêtre mais gardée pour la ligne de commande avancée :)* le bouton **« Mettre à jour depuis le serveur »** télécharge le catalogue signé des bouquets (HTTPS, signature vérifiée, copie gardée dans le dossier de l'outil et réutilisée hors ligne au démarrage) ; le bouton **« Catalogue (fichier)… »** (avancé, non vérifié) charge `TRIAL-MANIFEST.json` (ou un fichier contenant le tableau `bundles`) ; ses bouquets remplissent une liste à cocher : **Achat** (produit `ach-<bouquet>`), **Abonnement** (+ date de fin AAAA-MM-JJ, produit `abo-<bouquet>`) et **Location** (produit `loc-<bouquet>`). La durée d'une location est **affichée en lecture seule** (« fixée par le serveur : N jours », « (défaut) » quand le catalogue n'en fixe pas) : elle ne se saisit pas. La zone **« Avancé »** reste pour les autres lignes (`tout-ouvert produit:jours`, `droit LIGNE`) ; une ligne `location produit=bouquet:JOURS` y est vérifiée **exactement** (`RentalDurations.check`) et **refusée sans catalogue chargé** (« chargez le catalogue du serveur »). Un essai ne porte aucun droit. La liste des lots libres n'est pas contrôlée par la fenêtre (utiliser la ligne de commande avec `--lots-libres` pour cela).
- **Ligne de commande** : `emettre … --usage-jours N` ou `--usage-jours illimitee` (refusé pour un essai).
- **Lots d'essai** : toute clé d'**essai** porte automatiquement la **fenêtre unique de 12 h** des lots locatifs (accordée une seule fois par la TV ; ce n'est pas une location et elle n'est jamais montrée comme telle). L'émission l'indique : « Fenêtre de lots d'essai (usage unique) ». `--sans-lots-essai` l'omet (essai sans lots). La fenêtre graphique l'ajoute toujours aux essais.
- **Locations, durée fixée par le serveur et exacte (AVANCÉ : ligne de commande seulement, pour les tests et `tools/rental-test`)** : la durée d'une location est le `rentalDays` du bouquet dans le catalogue (**30 jours** quand il n'en fixe pas : décision du propriétaire ; voir `content/bundles-rental.json`), ni plus ni moins (`RentalDurations`). Une location par bouquet, produit `loc-<bouquet>`.
  - `catalogue-serveur [--serveur URL] [--cle-publique B64]` : importe le catalogue depuis le serveur et le garde dans `<dossier>/bundles-catalog.json` ; ensuite `--catalogue serveur` (relit et revérifie la copie).
  - `--location-bouquet b1,b2` (avancé, production) : une location par bouquet, **sans durée à saisir** ; exige `--catalogue`.
  - `--location produit=b1,b2:JOURS[:MINUTES[:TOLERANCE[:SIMULTANEES]]]` : avec `--catalogue TRIAL-MANIFEST.json --lots-libres FICHIER`, la durée doit être **exactement** celle du serveur (plus courte ou plus longue : refusée, « la durée est fixée par le serveur à N jour(s) ») ; un lot libre (CC BY-SA, jamais louable) est refusé aussi.
  - **Sans `--catalogue`**, `--location` est **refusée** (« chargez le catalogue du serveur ») sauf `--sans-controle-catalogue` (déconseillé : aucune durée ni lot libre vérifiés ; un avertissement le rappelle). Les tests qui émettent des locations sans catalogue passent ce drapeau.
- **Fin de clé** : la TV se verrouille (« ACTIVATION TERMINÉE ») et demande un nouveau code valide ; les achats restent.
- **Édition d'essai** : streaming, Sudoku et lots d'essai seulement (bibliothèque, réception de fichiers, USB, téléchargements, copie, déplacement et suppression fermés). Un **insigne permanent** sur chaque écran de CastBridge-TV donne l'édition et la validité de la clé.
- **Clé publique de confiance** : `cle` (ou « Clé publique » de la console) affiche `kid=… pub=… scopes=…`, avec **toutes** les portées de l'outil (REVOKE, REGISTRY, REACTIVATE, SUPER_UNLIMITED ; jamais POLICY pour le téléphone). La TV ne fait confiance qu'aux lignes présentes dans `~/.castbridge-signing/activation-trusted-keys.txt` **à la compilation** ; ajouter ou changer une clé exige donc de recompiler CastBridge-TV (ou une commande signée par une clé déjà valide, § 5).
- **Console du téléphone** : même champ « Durée de la clé » et même règle ; le champ du code d'appareil insère les tirets tout seul (OWNER-CONSOLE.md, dernière section).

## 9. Experts de l'assistance à distance
L'administration à distance de chaque CastBridge-TV passe par un tunnel SSH inversé (docs/REMOTE-TUNNEL.md, écrit côté serveur). Y ont accès **le propriétaire et les experts qu'il désigne**, révocables. La liste des experts est **signée hors ligne** par le bureau, avec la clé qui porte la portée **REGISTRY** (la clé du bureau), puis publiée sur le serveur (`/api/v1/tunnel/experts`). Chaque TV la vérifie pour installer les clés autorisées, le serveur la vérifie pour laisser passer les experts par son sshd. Aucun secret n'y figure : seulement des clés **publiques**.

Format : `{"generatedAt":<ms>,"keyId":"<kid>","experts":[{"id":"alice","publicKey":"ssh-ed25519 AAAA… commentaire","notAfter":<ms, 0 = sans fin>}],"signature":"<base64 Ed25519>"}`. Texte signé (UTF-8) : `castbridge-experts-v1\ngeneratedAt=<ms>\nexpert=<id>|<clé>|<notAfter>\n…` (experts triés par identifiant, sans saut de ligne final). Règles : identifiant `[a-z0-9][a-z0-9-]{0,31}` ; clé sur une ligne `ssh-ed25519 <base64>` de 32 octets (rsa, ecdsa et dss refusés) ; ni identifiant ni clé en double ; 50 experts au plus. Chaque TV installe, pour un expert non expiré, la ligne `restrict,pty ssh-ed25519 … <id>` : un terminal, **aucune** redirection de port, d'agent ni d'écran. Implémentation commune : `castbridge.core.tunnel.ExpertsList`.

**Ligne de commande** (le dossier et le code de déverrouillage comme les autres commandes) :
- `experts-ajouter ID --cle-ssh "ssh-ed25519 AAAA… commentaire" [--jusqu-au AAAA-MM-JJ]` : ajoute l'expert à la liste **locale non signée** `<dossier>/experts.json.src` (la date est inclusive : l'accès s'arrête à minuit UTC qui suit).
- `experts-retirer ID`, `experts-lister` : retire / affiche (identifiant, fin de la clé, fin de validité).
- `experts-signer [--sortie experts.json]` : signe la liste locale et écrit le `experts.json` à publier ; **refusé** si la clé du bureau n'a pas la portée REGISTRY. Le fichier est revérifié avant d'être écrit. Une liste vide est permise (plus aucun expert ; le propriétaire garde son accès).
- `experts-verifier FICHIER` : vérifie un `experts.json` avec la clé publique de ce bureau (signature, portée REGISTRY, experts valides) et dit combien sont actifs maintenant.
- Le journal reçoit une ligne par changement (`experts-ajouter`, `experts-retirer`, `experts-signer`), jamais une clé.

**Fenêtre graphique** : onglet **« Experts »** (liste, ajout, retrait, **« Signer et enregistrer… »** avec le code de déverrouillage).

**Révoquer un expert** : `experts-retirer ID`, `experts-signer`, puis republier. Le nouveau fichier est plus récent (`generatedAt`) : les TV et le serveur refusent un ancien fichier rejoué. L'accès ne se ferme qu'une fois la nouvelle liste publiée et récupérée par les TV.

**Publication** : copier le fichier signé sur le serveur à l'emplacement décrit dans docs/REMOTE-TUNNEL.md (`scp experts.json <serveur>:<stockage>/…` ; chemin exact et service qui relit le fichier : voir ce document, non répété ici). Vérifier ensuite `GET /api/v1/tunnel/experts`.

**La clé publique de confiance** est celle du bureau (`cle`), déjà dans l'anneau des TV avec la portée REGISTRY.
