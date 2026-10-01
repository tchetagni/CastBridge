# Outil de bureau « castbridge-owner » (ligne de commande)

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
java -jar castbridge-owner.jar compact --vault ~/castbridge-owner/coffre.txt --device XXXX-XXXX-XXXX-XXXX --kind trial --days 30
```
Donne un texte à **saisir** sur la TV (ou à coller dans le téléphone, qui le remet à la TV par Bluetooth). Liée strictement à ce code d'appareil ; pas de droits ni de clés de lots.

**B. Activation complète** (la TV exporte sa « demande d'appareil » : `code=…`, `k=…`, `factor=TYPE|empreinte`) :
```
java -jar castbridge-owner.jar activation --vault ~/castbridge-owner/coffre.txt --request demande.txt \
  --kind production --license LIC-0001 --purchase math:bouquet-6e,bouquet-5e --days 365 --out-file ./usb
```
Écrit le fichier `activation` à copier dans `Download/CastBridge/` de la clé USB de la TV, et affiche le jeton (Bluetooth, saisie). Droits : `--purchase PRODUIT:BOUQUET[,…]`, `--subscription PRODUIT:BOUQUET:AAAA-MM-JJ`, `--open-all PRODUIT --open-days N` (**30 jours au plus**).
`inspect --request demande.txt` montre le contenu d'une demande.

## Garde-fous
- Le code d'appareil doit correspondre aux empreintes de la demande (un fichier altéré est refusé).
- Fenêtre d'installation : 1 à 366 jours ; « tout ouvert » : 30 jours au plus.
- Un **journal** `castbridge-owner-journal.log` note chaque émission (date, code d'appareil, type, licence, durée) ; **jamais la clé ni le code**.
- Un seul essai de code à la fois, avec un délai (la dérivation de clé est volontairement lente) ; en script : variable `CB_OWNER_PASSPHRASE`.

## Ce qui n'est PAS encore en place (ne pas s'y fier)
- Les **applications TV et téléphone ne vérifient pas encore** ces jetons et n'affichent pas l'écran de départ : l'exigence d'activation (`REQUIRE_ACTIVATION`) reste **éteinte** ; rien n'est verrouillé aujourd'hui.
- La **TV n'exporte pas encore** sa demande d'appareil ; la clé publique de votre coffre n'est **pas encore embarquée** dans les TV.
- Une interface graphique, les installeurs Windows/Linux, le registre des licences et la version du téléphone propriétaire sont en cours (agents `activation-tools`, `license-admin`).
