# Outil de bureau « castbridge-owner » (ligne de commande de dépannage)

> **Outil officiel : `castbridge-activation-desktop.jar`** (module `tools/activation-desktop`, guide `docs/ACTIVATION-TOOLS.md`) : coffre protégé par **scrypt** (mémoire-dure), **licences et postes**, **registre signé**, **codes QR**, fenêtre graphique, journal d'audit. **Utilisez celui-là.** Le présent outil (`castbridge-owner.jar`, PBKDF2) reste comme **dépannage minimal** : ses coffres ne sont **pas** compatibles avec ceux de l'outil officiel. Aucun coffre n'ayant encore été créé, il n'y a rien à migrer : créez votre clé avec l'outil officiel (`cle-creer`).


Génère les activations à partir de ce que la TV affiche ou exporte. Fonctionne **hors ligne**, sur Mac, Windows et Linux (Java 17 ou plus). Même bibliothèque d'émission que le téléphone propriétaire et le serveur (`docs/ACTIVATION-FORMAT.md`).
Fichier : `castbridge-owner.jar` (construit par `cd android && gradle :core:ownerToolJar`, résultat `android/core/build/libs/`), copié dans `~/CastBridge-release/owner/` avec un lanceur.

## Première fois : créer VOTRE coffre (vous seul tapez votre code)
```
java -jar castbridge-owner.jar init --vault ~/castbridge-owner/coffre.txt
```
- Le code de déverrouillage est demandé au clavier, **jamais dans la commande** (10 caractères au moins ; **plus long = plus sûr** : c'est ce qui protège la clé si le fichier est volé).
- La commande affiche la **clé publique** (`kid=… pub=… scopes=…`) : c'est elle que les TV doivent accepter.
- **Sauvegardez le coffre hors ligne** (clé USB chiffrée, coffre-fort) ; ne le partagez pas ; ne le mettez pas dans le dépôt.

## Activer une TV
**A. Clé compacte** (la TV ne montre que son code `XXXX-XXXX-XXXX-XXXX`) :
```
java -jar castbridge-owner.jar compact --vault ~/castbridge-owner/coffre.txt --device XXXX-XXXX-XXXX-XXXX --kind trial
```
Donne un texte à **saisir** sur la TV (ou à coller dans le téléphone, qui le remet à la TV par Bluetooth). Liée strictement à ce code d'appareil ; pas de droits ni de clés de lots.

**B. Activation complète** (la TV exporte sa « demande d'appareil » : `code=…`, `k=…`, `factor=TYPE|empreinte`) :
```
java -jar castbridge-owner.jar activation --vault ~/castbridge-owner/coffre.txt --request demande.txt \
  --kind production --license LIC-0001 --purchase math:bouquet-6e,bouquet-5e --out-file ./usb
```
Écrit le fichier `activation` à copier dans `Download/CastBridge/` de la clé USB de la TV, et affiche le jeton (Bluetooth, saisie). Droits : `--purchase PRODUIT:BOUQUET[,…]`, `--subscription PRODUIT:BOUQUET:AAAA-MM-JJ`, `--open-all PRODUIT --open-days N` (**30 jours au plus**).
`inspect --request demande.txt` montre le contenu d'une demande.

## Garde-fous
- Le code d'appareil doit correspondre aux empreintes de la demande (un fichier altéré est refusé).
- Fenêtre d'installation : **48 h à partir de la création** (plus d'option `--days`) ; `--super oui` : privilège **SUPER_UNLIMITED** (lit et débloque tout, locations permanentes), réservé à la clé super administrateur (portée `SUPER_UNLIMITED`), avec `activation` seulement ; un compte **illimité** non super s'obtient avec `--purchase illimite:tout` (ses locations expirent) ; « tout ouvert » : 30 jours au plus.
- Un **journal** `castbridge-owner-journal.log` note chaque émission (date, code d'appareil, type, licence, durée) ; **jamais la clé ni le code**.
- Un seul essai de code à la fois, avec un délai (la dérivation de clé est volontairement lente) ; en script : variable `CB_OWNER_PASSPHRASE`.

## Ce qui n'est PAS encore en place (ne pas s'y fier)
- Les **applications TV et téléphone ne vérifient pas encore** ces jetons et n'affichent pas l'écran de départ : l'exigence d'activation (`REQUIRE_ACTIVATION`) reste **éteinte** ; rien n'est verrouillé aujourd'hui.
- La **TV n'exporte pas encore** sa demande d'appareil ; la clé publique de votre coffre n'est **pas encore embarquée** dans les TV.
- Une interface graphique, les installeurs Windows/Linux, le registre des licences et la version du téléphone propriétaire sont en cours (agents `activation-tools`, `license-admin`).

## Console du téléphone « CastBridge Propriétaire » (module `:owner`)
Application **séparée** (`castbridge.owner`), **jamais publiée** sur le canal de mise à jour, **sans permission réseau ni stockage**. Fichier : `~/CastBridge-release/owner/CastBridge-Proprietaire-0.1.0.apk` (installé sur le téléphone du propriétaire).
- **Première ouverture : créez la clé de ce téléphone** avec **votre** code (12 caractères ou plus, neuf, jamais écrit ailleurs). Cette clé est **propre au téléphone** (une clé par outil) : ce n'est pas celle du bureau. Elle est dérivée de votre code et chiffrée dans les fichiers privés de l'application, exclus de toute sauvegarde.
- **Déverrouillage** par le code, avec délais croissants puis verrouillage temporaire après des erreurs répétées ; **verrouillage automatique** après 2 minutes d'inactivité et dès que l'application quitte l'écran ; capture d'écran et aperçu des applications récentes interdits.
- **Onglet Activer** : collez le code d'appareil (clé compacte à saisir) ou la demande d'appareil complète (activation complète : droits, durée, `--purchase`/abonnement/« tout ouvert » ≤ 30 jours) ; résultat à **copier** (presse-papiers marqué sensible), **partager** ou **enregistrer en fichier** `activation` pour la clé USB.
- **Champ « Durée de la clé »** : essai = 30 jours par défaut (1 à 365, jamais illimité) ; production = vide ou « illimitée » (la TV ne se reverrouille jamais) ou 1 à 3660 jours ; SUPER_UNLIMITED refuse une durée. Toute clé d'essai porte aussi, sans rien régler, la fenêtre unique de 12 h des lots locatifs (ce n'est pas une location). Le champ du code d'appareil insère les tirets tout seul. Détails : OWNER-CONSOLE.md (dernière section).
- **Onglet Clé publique** : la ligne `kid=… pub=… scopes=…` à faire accepter par les TV ; elle liste **toutes** les portées (REVOKE, REGISTRY, REACTIVATE, SUPER_UNLIMITED comprises, jamais POLICY) et la TV ne se fie qu'à une ligne présente dans `activation-trusted-keys.txt` à la compilation. **Onglet Journal** : qui, quoi, quand (jamais la clé, ni le code, ni le jeton), chaîné par empreintes.
- **Pas encore** : envoi direct à la TV par Bluetooth (le canal propriétaire de la TV n'existe pas encore), lecture par QR, mémoire-dure (Argon2) pour le code, biométrie. Prévus avec les écrans de la TV.
- Testé sur le téléphone : création de la clé, déverrouillage, écran d'émission, message d'erreur, onglet clé publique (essai avec un code jetable, données effacées ensuite). La logique d'émission est celle de `castbridge-owner` (9 tests) et des vecteurs communs.

## Note : options récentes (outil officiel)
Le dépannage `castbridge-owner.jar` n'a pas ces options ; elles sont dans l'outil de bureau officiel (`emettre`) : `--usage-jours N|illimitee` (durée de la clé), `--sans-lots-essai`, `--catalogue` (durée de location plafonnée par `rentalDays`). Voir ACTIVATION-TOOLS.md § 8.

## Note : production (2026-10-02)
Dans l'outil de bureau officiel, `emettre --production` **sans `--licence`** génère une licence `lic-…` (affichée) ; la durée se règle par `--usage-jours` ; aucun droit de contenu n'est nécessaire. Les options `--achat`, `--abonnement`, `--tout-ouvert`, `--droit`, `--location`, `--location-bouquet`, `--catalogue`, `--sans-controle-catalogue` sont **avancées** (tests, outils de location). Voir ACTIVATION-TOOLS.md § 2 et § 8.
