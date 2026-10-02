# w4-02 — Outils d'émission (console téléphone, bureau, `OwnerCli`) : demande v2 et boîte v2

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w4-01)
> **Groupe : W4a-2** (vague W4a) · prérequis : w4-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnerCli*' && cd ../tools/activation-desktop && tools/agents/gradle-lock.sh gradle --offline test`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non
> **Amendement W16 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 1, § 4.3. La durée saisie `--location …:JOURS` et la règle « exacte » (`--catalogue`) restent le mode `rental.userChosen = 0` ; le pilote ajoute `--location-choix b=<defaut|Nj|Nh>`, `--pilote`, `--registre` (cahier **w16-05**, qui possède `DK/Cli.kt` pour ces zones) : ne pas les écrire ici.
> **Amendement (architecte, 2026-10-02 ; cahier fusionné → correctif `claude/sonnet-w4-02-fix`)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L2/L3/L4, § 3 « w4-02 »). Il **prévaut**. Les trois outils (console, bureau, `OwnerCli`) lisent la ligne **`sign=ed25519|…`** de la demande (`DeviceInfo.signPub`, `DeviceRequest.signPub`) et émettent la ligne de corps **`install=<kid>`** pour toute **production** et tout **`super`** (jamais pour un essai) ; sans `sign=` (TV ancienne) : refus par défaut « cette TV n'a pas fourni sa clé de signature : mettez CastBridge-TV à jour », option explicite « sans liaison (TV ancienne) » journalisée et refusée après le coucher 2027-01-01 ; le récapitulatif affiche « clé liée : oui/non ». Dépend de `claude/sonnet-w6-03-fix` (`Activation.installKid`).

**Vague 4a · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-01).** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` § 4, § 6. Branche `claude/sonnet-w4-02`. Rapport : `docs/agent-reports/sonnet-w4-02.md`.

## Objectif
Les trois outils du propriétaire lisent la demande d'appareil v2 (ligne `install=`), émettent la boîte v2 pour la fenêtre d'essai des lots et les locations (options avancées), refusent par défaut d'émettre une location pour une TV sans clé d'installation, et offrent l'option explicite « enveloppe v1 (TV ancienne) » jusqu'au coucher v1. Ils doivent être **déployés avant** la TV v2 (w4-03).

## Pourquoi (preuves)
- `android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt:184-195` : `OwnerFrames.parseDeviceInfo(input)` (ancien `Triple`), fenêtre d'essai `RentalIssuing.right(…, full.third, RentalKeys.masterFrom(signer))` sans clé d'installation.
- `tools/activation-desktop/src/main/kotlin/castbridge/desktop/Cli.kt:220,246,258-264,319,336,346` : `DeviceRequest.parse`, `--location`, `--location-bouquet`, `lot-chiffrer` ; `Desk.kt:66` `rentalMaster()` ; `Gui.kt:116`.
- `C/owner/OwnerCli.kt:143,181,187-189` : `parseRequest` → `parseDeviceInfo` ; `C/owner/PhoneConsole.kt:62` `DeviceRequest.parse`.
- `S/OrdersRuntime.kt:43` lit seulement le code : il utilise la surcharge dépréciée tolérante (aucun changement requis ; **vérifier** qu'il compile).

## Fichiers possédés
`android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt`, `C/owner/OwnerCli.kt`, `C/owner/PhoneConsole.kt`, `C/owner/ProductionForm.kt` (si un champ « enveloppe v1 » y est ajouté), `tools/activation-desktop/src/main/kotlin/castbridge/desktop/{Cli,Gui,GuiRights,Desk,Vectors}.kt`, `tools/activation-desktop/src/test/**`, `CT/owner/OwnerCliTest.kt`, `CT/owner/PhoneConsoleTest.kt`, `CT/owner/ProductionFormTest.kt`. **Hors zone** : tout `C/lots/**`, `C/owner/OwnerFrames.kt`, `C/owner/LicensedIssuer.kt` (w4-01 : utiliser leurs nouvelles API telles quelles), `R/**`, `backend/`, docs (w4-06).

## Étapes
1. Console (`ConsoleActivity.Issue`) : `OwnerFrames.parseDeviceInfo` v2 ; si `installPub == null` et la clé est un **essai** (fenêtre de lots) ou porte une location : message « Cette TV n'a pas fourni sa clé d'installation (CastBridge-TV trop ancien) : mettez-la à jour, ou activez « Enveloppe v1 (TV ancienne) » » + bascule `Switch` (visible seulement si `issuedAt < V1_BOX_SUNSET_MS`) ; **une clé de production sans location** s'émet normalement (aucune boîte à construire). Journal : colonne `kind` suffixée `+v2` ou `+v1`.
2. `Desk.issue`/`Cli` : option `--enveloppe-v1` (refusée après le coucher : « enveloppe v1 périmée ») ; `appareil` imprime « clé d'installation : présente/absente » ; `lot-chiffrer` inchangé (il scelle avec la clé de location, pas avec la boîte) ; `Gui` : case « Enveloppe v1 (TV ancienne) » dans « Avancé » + ligne d'état de la demande.
3. `OwnerCli` (CLI cœur, tests JVM) : mêmes règles ; `parseRequest` via `DeviceInfo`.
4. `PhoneConsole` : `DeviceRequest.parse` (déjà tolérant via w4-01) ; propager `boxV1` dans `IssueSpec`.
5. Tests : `OwnerCliTest` (demande v2 ⇒ boîte `v2:` dans la ligne `rental` ; demande v1 ⇒ refus sans option ; avec option avant coucher ⇒ v1 ; après coucher ⇒ refus), `PhoneConsoleTest` idem, test bureau `VectorsTest` rejoue **aussi** `rental-vectors-v2.json` via `RentalVectorsV2.run` (`Vectors.kt`).
6. Vérifier la compilation d'`:ownerlib` et `:sender` si le SDK est là (`gradle --offline :ownerlib:compileDebugKotlin :sender:compileDebugKotlin`), sinon le dire.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.OwnerCliTest' --tests 'castbridge.core.owner.PhoneConsoleTest'   # vert
cd tools/activation-desktop && ../../android/gradlew --offline test 2>/dev/null || (cd android && gradle --offline :activation-desktop:test)   # vert, dont les vecteurs v2 (voir tools/core-harness/run.sh si le projet est multi-racine)
grep -n 'enveloppe-v1\|Enveloppe v1' tools/activation-desktop/src/main/kotlin/castbridge/desktop/Cli.kt android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt   # ≥ 2
grep -n 'parseDeviceInfoLegacy\|Triple' android/ownerlib/src/main/kotlin/castbridge/owner/ConsoleActivity.kt   # 0 hit (la console lit DeviceInfo)
```

## Cas limites
- Demande v2 collée avec CRLF/BOM/lignes vides : `DeviceRequest.parse` nettoie déjà ; ajouter `install=` au test de nettoyage.
- Demande v2 dont `install=` est mal formé (hex impair) : « Demande d'appareil illisible (clé d'installation) », pas une v1 silencieuse.
- Clé **compacte** (saisie) : jamais de boîte ; inchangée.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun secret ; ne pas changer la dérivation du maître (`masterFrom`) ici (W4-C s'en charge) ; ne pas toucher les vecteurs ; textes en français ; dire « CastBridge-TV ».

## Rapport
`STATUT`, captures ou sorties CLI, confirmation que `:ownerlib`/`:sender` compilent (ou non compilés : le dire).
