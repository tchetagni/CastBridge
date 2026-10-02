# w11-09 — CastBridge (téléphone) : réglages en 4 sections, suppression des entrées « Données hors ligne » répétées, ligne « Mode », Langues dans Apprendre
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-c** (vague W11) · prérequis : w11-07 · porte : `grep -c '"Mode : ' android/sender/src/main/kotlin/castbridge/sender/ConnectScreens.kt  # 1`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 11c (téléphone) · Effort S (≈ 1 j) · Modèle : sonnet (quatre fichiers Compose, choix de mise en page : hors forme mécanique Haiku, ROUTAGE § 4.7) · Statut PRÊT (après w11-07 ; indépendant de w11-08 ; jamais en parallèle de w8-17 qui touche `S/ConnectScreens.kt`).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 3.4, § 6.2, § 9.3. Branche `claude/sonnet-w11-09`. Rapport : `docs/agent-reports/sonnet-w11-09.md`.

## Objectif
`SettingsScreen` montre 4 lignes qui s'ouvrent (Apparence, Confidentialité, Mises à jour, Avancé) et un pied de page (version · identifiant · « Mode : complet / minimal / super administrateur / point focal »). « Données hors ligne », « Contenus libres » et « Contrôle parental » quittent les réglages (ils sont dans « Plus » et dans « Parents ») ; les boutons « Données hors ligne » de Jeux et Apprendre disparaissent (une seule entrée dans « Plus »). Dans Apprendre, « Langues » devient un sous-onglet (pas un bouton de `LotsScreen`).

## Pourquoi (preuves)
- `android/sender/src/main/kotlin/castbridge/sender/ConnectScreens.kt:118-136` : 7 sections déroulées d'un bloc, dont `LotsEntry()` et `FreeContentEntry()` (`:127-129`) et `ParentalSection()` (`:133`).
- `GamesScreen.kt:92` et `LearnScreen.kt:56` : deux autres `LotsEntry()` ⇒ trois chemins vers le même écran.
- `LotsScreen.kt:86` : `LanguagesSection` (choix des langues) enfoui dans « Données hors ligne » ; `docs/LANGUES.md:302` prévoit « onglet Apprendre → Langues ».
- W6 (`DESIGN-W6-PARENTAL-PHONE-GATE.md:175`) : une ligne discrète « Mode : … » dans les réglages.

## Fichiers possédés
- Modifiés : `android/sender/src/main/kotlin/castbridge/sender/ConnectScreens.kt` (`SettingsScreen`, `AboutSection`, `AdvancedSection` ; les sections Confidentialité/Mises à jour/Connexion ne changent pas de contenu), `android/sender/src/main/kotlin/castbridge/sender/GamesScreen.kt` (ligne 92), `android/sender/src/main/kotlin/castbridge/sender/LearnScreen.kt` (lignes 53-64 : sous-onglets), `android/sender/src/main/kotlin/castbridge/sender/LotsScreen.kt` (`LanguagesSection` rendue `internal` et réutilisable ; `LotsEntry` conservée pour `NAV_V2=false`).
- Hors zone : `nav/Shell.kt`, `nav/PhoneHome.kt`, `ParentalTab.kt`, `FreeContentScreen.kt`, `MainActivity.kt`.

## Étapes
1. `SettingsScreen` (quand `BuildConfig.NAV_V2`) : liste de 4 `ListItem` dépliables (`Apparence` → puces de thème ; `Confidentialité` → `PrivacySection` ; `Mises à jour` → `UpdatesSection` + `TvServerPanel` (mise à jour de la TV et questions du quiz) ; `Avancé` → adresse du serveur + « Ma TV : autres marques » (`MyTvActivity.open`) + « Voies Bluetooth » (`BtRoutesScreen`)) ; pied de page : `CastBridgeLogo(32.dp)`, « Version x (code) · identifiant ab12 », « Mode : … » calculé : super (bandeau w6-19 actif) / point focal (`AGENT_WHITELIST` actif) / minimal (`PhoneGateRuntime` si présent) / complet. Quand `NAV_V2=false`, l'écran actuel reste.
2. Retirer `LotsEntry()`/`FreeContentEntry()`/`ParentalSection()` de `SettingsScreen` en `NAV_V2`.
3. `GamesScreen.kt:92` et `LearnScreen.kt:56` : retirer `LotsEntry()` quand `NAV_V2` (garder sinon).
4. `LearnScreen` : sous-onglets « Leçons · Langues · Piloter la TV · Parents » ; « Langues » = `LanguagesSection` de `LotsScreen` (choix des langues + état) — pas de nouvelle logique.
5. Vérifier `PhoneConnect.screens.enter("settings")` toujours appelé.

## Critères d'acceptation
```sh
cd android && grep -c 'LotsEntry()' sender/src/main/kotlin/castbridge/sender/ConnectScreens.kt sender/src/main/kotlin/castbridge/sender/GamesScreen.kt sender/src/main/kotlin/castbridge/sender/LearnScreen.kt   # chacun ≤ 1 et sous `if (!BuildConfig.NAV_V2)`
grep -c '"Mode : ' sender/src/main/kotlin/castbridge/sender/ConnectScreens.kt   # 1
grep -c 'Tab(sub == 3' sender/src/main/kotlin/castbridge/sender/LearnScreen.kt   # 1
```
Observable (`-PnavV2=true`) : Réglages = 4 lignes + pied de page sur un écran sans défiler ; chaque ligne se déplie ; « Données hors ligne » n'existe que dans « Plus » ; Apprendre a 4 sous-onglets dont « Langues » ; en `NAV_V2=false`, rien ne change.

## Cas limites
`PhoneGateRuntime` absent (w6-16 non fusionné) ⇒ « Mode : complet ». Pas de TV reliée ⇒ « Mises à jour » montre seulement le téléphone (comme `TvServerPanel` le fait déjà).

## À ne pas faire
Ne pas modifier le contenu de `PrivacySection`, `UpdatesSection`, `ConnectionSection`. Ne pas supprimer `LotsEntry` ni `FreeContentEntry` (utilisées par « Plus » et par l'ancien rendu). Pas de nouvelle chaîne hors § 8.1.

## Rapport
`STATUT`, captures (Réglages replié/déplié, Apprendre › Langues), les trois `grep`.
