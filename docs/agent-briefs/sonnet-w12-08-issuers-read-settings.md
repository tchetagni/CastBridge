# w12-08 — Outils d'émission : la console « CastBridge Propriétaire », l'outil de bureau, `OwnerCli` et le serveur (`ActivationService`) lisent `trial.defaultDays`, `trial.lotsWindow*`, `rental.defaultDays`, `price.variant` depuis le document de réglages ; « Réglages v<seq> » affiché
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (les durées émises sont des valeurs €, bornées par le schéma et par les vérificateurs existants) · statut : PRÊT
> **Groupe : W12-c** (vague W12) · prérequis : w12-01 fusionné ; w12-05 (`read_settings.py`) souhaité ; `OL/ConsoleActivity.kt`, `OL/OwnerStore.kt`, `DK/*`, `C/owner/OwnerCli.kt` **après** w4-02, w4-12, w5-14, w6-19 s'ils sont lancés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*OwnerCli*' --tests '*RentalDurations*' && cd tools/activation-desktop && tools/agents/gradle-lock.sh gradle --offline test && cd android && tools/agents/gradle-lock.sh gradle --offline :ownerlib:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : échantillon
> **Amendement W16 (architecte, 2026-10-03)** : lire `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 4.1-4.2. Les émetteurs lisent aussi `rental.userChosen`, `rental.pickerDays`, `rental.maxDays`, `rental.hourly.*`, `rental.cooldownMin`, `pilot.*` et les passent à `PilotRules` (w16-04) ; `rental.defaultDays` vaut « sans durée indiquée » quand `userChosen = 1` ; le bureau remplace `--pilote pilot.json` (w16-05) par le document de réglages quand il est présent (le fichier garde la priorité s'il est explicitement donné, et l'outil le dit).

**Vague 12c (émetteurs) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.6, § 2.7, § 4.4, § 5.2, § 5.4, § 7, § 9 (D-W12-1, D-W12-7). Branche `claude/sonnet-w12-08`. Rapport : `docs/agent-reports/sonnet-w12-08.md`.

## Objectif
Les quatre émetteurs **lisent** les réglages (ils ne les signent jamais, D-W12-1) : (1) **console téléphone** (`OL/`) : `OwnerStore` garde le dernier jeton vérifié (`settings=`), import par fichier / collage (l'APK `:owner` n'a pas de réseau, `android/owner/build.gradle.kts:7-8`) ou automatiquement depuis `SettingsRuntime` quand la console tourne dans l'app téléphone ; l'onglet **Activer** pré-remplit « Durée de la clé » d'essai avec `trial.defaultDays`, construit la fenêtre d'essai avec `trial.lotsWindowDays`/`Minutes`, affiche « Réglages v<seq> · essai 30 j · lots 3 j / 720 min » (ou « défauts ») et **avertit** si le document est périmé de plus de 30 j (D-W12-7) ; (2) **bureau** (`DK/`) : commande `reglages-serveur|server-settings` (tire `GET /api/v1/settings/current`, vérifie, garde `~/.castbridge-activation/settings.txt`, comme `catalogue-serveur`), option `--reglages <fichier>` ; `emettre` utilise les défauts du document et affiche la version ; (3) **`OwnerCli`** : `--reglages <fichier>` ; (4) **serveur** : `ActivationService.issue` (essai) lit `trial.defaultDays` et la fenêtre depuis le dernier `settings_doc` publié (via `SettingsService` de w12-03 ; si le module est éteint : constantes) ; (5) `RentalDurations.DEFAULT_DAYS` devient un **paramètre** (`defaultDays = Settings.int("rental.defaultDays")`), constante conservée comme défaut.

## Pourquoi (preuves)
- Console : onglets `OL/ConsoleActivity.kt:116` ; construction de la clé d'essai avec la fenêtre `:183-198` ; durée par défaut 30 j (`docs/OWNER-CONSOLE.md:96`) ; `OwnerStore` cache catalogue `:68-80` (patron pour `settings=`), ligne publique sans `POLICY` `:54`.
- Bureau : `DK/Cli.kt:59` (dossier), `:81-101` (sous-commandes, dont `catalogue-serveur`), `DK/ServerCatalogStore.kt:14-39` (patron), `DK/Desk.kt` ; `C/owner/OwnerCli.kt:44-60`.
- Constantes : `C/owner/Activation.kt:127` (`TRIAL_DEFAULT_DAYS`), `C/lots/RentalLines.kt:22-23,45`, `C/lots/RentalDurations.kt:14,37-43`.
- Serveur : `B/licenses/ActivationService.java` (émission), scopes `ScopedActivationSigner.java:14-18`.
- Les vérificateurs existants bornent déjà (`TRIAL_MAX_DAYS` 365, fenêtre d'essai 1..3 j / 1..720 min, `RentalLines.kt:45`) : **un réglage hors bornes ne peut pas produire une clé invalide** (test).

## Fichiers possédés
- Existants (zones précises) : `OL/ConsoleActivity.kt` (onglet Activer : pré-remplissage, ligne de version, import), `OL/OwnerStore.kt` (`settings=`), `DK/Cli.kt`, `DK/Desk.kt`, nouveau `DK/SettingsStore.kt`, tests desktop existants (+ `SettingsStoreTest`), `C/owner/OwnerCli.kt`, `C/lots/RentalDurations.kt`, `CT/lots/RentalDurationsTest.kt` (si existant, sinon créer), `B/licenses/ActivationService.java` (**une** lecture), `BT/licenses/ActivationServiceSettingsTest.java`, `docs/OWNER-CONSOLE.md` (§ « Réglages »), `docs/ACTIVATION-TOOLS.md` (§ `reglages-serveur`).
- Hors zone : `SettingsSchema`/`SettingsEngine` (w12-01), `B/settings/**` (w12-03 : appeler, ne pas modifier), signature de réglages (**jamais** ici), `S/**`.

## Étapes
1. `RentalDurations` paramétré + test (bouquet sans `rentalDays` ⇒ `Settings.int("rental.defaultDays")`).
2. Console : import, cache, pré-remplissage, version, avertissement périmé ; test JVM du pré-remplissage via `PhoneConsole`/`ProductionForm` (cœur) si ces classes portent la logique (sinon noter).
3. Bureau : `SettingsStore` (tirage, vérification avec l'anneau du bureau, cache, version), `emettre` (défauts), `--reglages`.
4. `OwnerCli --reglages`.
5. Serveur : lecture dans `issue` (essai) ; test : avec un document publié `trial.defaultDays=14`, la clé d'essai émise a un `usage` de 14 j ; module éteint ⇒ 30.
6. Docs.

## Critères d'acceptation (hors ligne)
- Porte verte ; une valeur hors bornes dans un document (impossible après w12-01, test de défense) ne produit jamais une clé refusée par le vérificateur ; le téléphone propriétaire **ne contient aucune** fonction de signature de réglages (grep `SettingsIssuer` absent de `OL/` et `S/`).
- Rapport : commandes du bureau, captures textuelles de l'onglet Activer, ligne exacte dans `ActivationService`.
