# w13-08 — CastBridge (téléphone) : saisie du code de la TV **dans la carte du transfert**, `PinStore.putAll` (toutes les clés), feuille « Réparer », lien profond `castbridge://repair`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (stockage et flux du PIN, lien profond, une tentative par saisie) · statut : PRÊT
> **Groupe : W13-c** (vague W13, tranche S1) · prérequis : w13-07 fusionné (`BlockerState`, `retryWithCredential`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M) · audit Opus : oui

**Vague 13c (téléphone) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 1.2, § 3.5 (téléphone), § 3.6, § 3.7, D-W13-1, D-W13-4, D-W13-7. Branche `claude/sonnet-w13-08`. Rapport : `docs/agent-reports/sonnet-w13-08.md`.

## Objectif
(1) `PinPrompt` (composable) : affiché par les cartes de transfert quand `Blocker.fix == ASK_PIN` : phrase du blocage, champ 6 chiffres, aide « où lire le code sur la TV », bouton « Envoyer avec ce code » ; vérifie par **un seul** `GET /api/info`, puis `PinStore.putAll(tv, code)` et `UploadService.retryWithCredential` ; (2) `PinStore.putAll` / `keysOf(tv)` : enregistre le code sous **toutes** les clés de la TV (nom, mDNS, `bt:<adresse>`, `<ip>:8765` pour chaque `lastIps`, IP manuelle sans port) et efface l'ancien code de ces clés sur `PIN_WRONG` confirmé ; (3) `RepairSheet` : feuille « Réparer la connexion » (rejoue `Preflight`, montre le `Blocker` + bouton `Fix` + « Copier le code support ») ; (4) lien profond `castbridge://repair?b=<wire>&tv=<nom>` ⇒ `MainActivity` ⇒ onglet CastBridge TV ⇒ `RepairSheet` (ou `PinPrompt` si `b == pin-wrong|pin-missing`).

## Pourquoi (preuves)
- Clé par nom qui rate : `S/PinStore.kt:22` (`get`), `S/TvLink.kt:191-206` (`credentialFor`, `savedFor`, `credentialForBase`) ; clés utilisées par les écrans : `S/TvHome.kt:87` (nom), `S/TvScreen.kt:77` (IP manuelle **sans port**), `S/TvHub.kt:96` (`bt:`), `S/player/CastSession.kt:39,159`, `S/DlnaHandoff.kt:50`.
- Saisie actuelle hors contexte : `S/TvHome.kt:106-111,130-132` (assistant sans explication), `:435-452` (`FirstConnection`, aide « Il est affiché sur la TV… » à **réutiliser mot pour mot**), `:445-450` (vérification par `info()`, 401 `locked` ⇒ « Trop d'essais »).
- Règle verrou : `C/trust/ResilientCall.kt:44` ; `C/tv/Security.kt:27-33`.
- Préférences : `castbridge_pins` privées, exclues des sauvegardes (`android/sender/src/main/res/xml/backup_rules.xml` à vérifier : si `castbridge_pins` n'y est pas exclu, l'ajouter — **lecture seule du fichier, signaler au rapport** : il appartient à w7-16 plus tard, à w13-08 maintenant).
- Lien profond : aucun `intent-filter castbridge://` dans `android/sender/src/main/AndroidManifest.xml` (vérifié par grep ; W7 w7-17 prévoit `castbridge://tv` : garder le même schéma, hôte `repair`).

## Fichiers possédés
`S/PinStore.kt`, `S/MainActivity.kt` (zone : traitement de l'intent `castbridge://repair` — ≤ 15 lignes), `android/sender/src/main/AndroidManifest.xml` (un `intent-filter`), `android/sender/src/main/res/xml/backup_rules.xml` et `data_extraction_rules.xml` (exclusion de `castbridge_pins` si absente) ; nouveaux : `S/link/PinPrompt.kt`, `S/link/RepairSheet.kt`, `S/link/RepairDeepLink.kt`. **Hors zone** : `S/TvHome.kt`, `S/TvScreen.kt`, `S/TvPairScreen.kt`, `S/TvLink.kt` (w13-09 branche `PinPrompt`/`RepairSheet` dans les cartes), `S/UploadService.kt` (w13-07), `C/**`.

## Signatures (contrat pour w13-09)
```kotlin
// S/PinStore.kt (additif)
fun keysOf(tv: SavedTv?, name: String?, base: String?): Set<String>
fun putAll(keys: Set<String>, pin: String); fun forgetAll(keys: Set<String>)
// S/link/PinPrompt.kt
@Composable fun PinPrompt(blocker: Blocker, tvName: String, base: String?, keys: Set<String>, onDone: (String) -> Unit, onCancel: () -> Unit)
// S/link/RepairSheet.kt
@Composable fun RepairSheet(blocker: Blocker?, tvName: String, onClose: () -> Unit)   // bouton Fix → RepairActions.perform(ctx, fix, tv)
object RepairActions { fun perform(ctx: Context, fix: Fix, tv: SavedTv?) }
// S/link/RepairDeepLink.kt
object RepairDeepLink { const val SCHEME = "castbridge"; const val HOST = "repair"; fun parse(uri: Uri?): Pair<BlockerCode, String>?; fun build(b: Blocker, tv: String): Uri }
```

## Étapes
1. `PinStore.keysOf` : union de `tv.name`, `tv.mdns`, `"bt:${tv.address}"`, `"$ip:${tv.port}"` et `ip` nu pour chaque `lastIps`, `name`, hôte de `base` avec et sans port ; `putAll` écrit chaque clé ; `forgetAll` les efface.
2. `PinPrompt` : champ `NumberPassword`, 6 chiffres, bouton actif seulement si `Pin.isValidFormat` et pas d'essai en cours ; **une** vérification `TvClient(base, code).info()` (IO) : 200 ⇒ `putAll` + `onDone(code)` ; 401 `locked` ⇒ texte `PIN_LOCKED` avec le délai, bouton désactivé jusqu'à `retryAfter` ; 401 autre ⇒ « Code incorrect » (champ vidé, pas de nouvel essai automatique) ; `base == null` ⇒ enregistrer sans vérifier et `onDone` (le transfert vérifiera) ; aide = texte de `TvHome.kt:436-437` + « En mode enfant, il est masqué : saisissez d'abord le code parental sur la TV. » ; jamais pré-rempli.
3. `RepairSheet` : état `BlockerState.last` ; bouton « Vérifier à nouveau » ⇒ `Preflight.run` (via `PreflightAndroid` de w13-07) ; `RepairActions.perform` : `ASK_PIN` ⇒ `PinPrompt` ; `REPAIR_PAIRING` ⇒ `TvLinkManager.requestReassociate` ; `WIFI_SETTINGS` ⇒ `Settings.ACTION_WIFI_SETTINGS` ; `BATTERY_SETTINGS` ⇒ `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` ; `DISABLE_VPN` ⇒ `ACTION_VPN_SETTINGS` ; `RETRY*` ⇒ `TvLinkManager.retryNow()` + `UploadService.retryWithCredential(dernier secret)` ; autres ⇒ fermer ; « Copier le code support » ⇒ presse-papiers.
4. `RepairDeepLink` + `intent-filter` (`android:scheme="castbridge" android:host="repair"`, `autoVerify` absent) ; `MainActivity.onNewIntent/onCreate` ⇒ `RepairDeepLink.parse` ⇒ sélectionner l'onglet CastBridge TV et poser `BlockerState.last` si absent ⇒ w13-09 ouvre la feuille ; le lien ne porte **jamais** de code.
5. Sauvegardes : vérifier l'exclusion de `castbridge_pins` ; l'ajouter si absente.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'castbridge' android/sender/src/main/AndroidManifest.xml | grep -c 'repair'` ⇒ 1 ; `grep -n 'castbridge_pins' android/sender/src/main/res/xml/*.xml` ≥ 1 ; `grep -n 'pin=' android/sender/src/main/kotlin/castbridge/sender/link/RepairDeepLink.kt` vide ; parcours manuel décrit au rapport (émulateur TV avec faux PIN).

## Cas limites
TV sans `base` (Bluetooth seul) ; plusieurs TV sauvegardées (clés de la TV **cible** seulement) ; rotation d'écran pendant la saisie (`rememberSaveable` **sans** le code : champ vidé) ; code parental ≠ code de la TV (texte explicite).

## À ne pas faire
Pas de nouvelle dépendance (`security-crypto` refusé, D-W13-4) ; pas de PIN dans un log, un intent, un `Bundle` persistant, un rapport ; pas de second essai automatique ; ne pas modifier les cartes (w13-09).

## Rapport
`STATUT`, clés écrites par `keysOf` pour une TV type, flux exact du code (mémoire → préférences), questions pour l'audit Opus.
