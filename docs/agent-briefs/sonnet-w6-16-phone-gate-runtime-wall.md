# w6-16 — CastBridge (téléphone) : `PhoneGateRuntime`, synchronisation de la preuve (Bluetooth + Wi-Fi), `GateWall`, onglets cadenassés, puce « TV cible », avertissement de preuve périmée

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après 6a, w6-11, w6-12)
> **Groupe : W6d-1** (vague W6d) · prérequis : w6-01, w6-02, w6-03, w6-11, w6-12 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L4/L5/L9, § 6). Il **prévaut**. En plus : puce « production · **non liée** » (`Proof.trust = UNBOUND`, cache 3 j) ; messages **M-ACT-UNBOUND**, **M-TV-REINSTALLED**, **M-TV-COMPACT** dans `PhoneGateTexts` (seul endroit) ; **aucun dialogue TOFU** pour une preuve liée (l'empreinte reste affichée en information ; `NeedsPin`/`IdentityChanged` ne surviennent que pour une activation non liée) ; `ProofSync` tient le `NonceBook` (8 ouverts, 10 min). La matrice § 3.7 ne change pas. Prérequis supplémentaire : `claude/sonnet-w6-03-fix`.

**Vague 6d · Effort L (≈ 3,5 j) · Modèle : sonnet · Statut PRÊT (après 6a, w6-11, w6-12 fusionnés ; w6-17/18/19 en parallèle sur des fichiers disjoints : ils consomment `GateWall` et `PhoneGateRuntime` ; tant que ce cahier n'est pas fusionné, ils codent contre les signatures ci-dessous).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.3 à § 3.9. Branche `claude/sonnet-w6-16`. Rapport : `docs/agent-reports/sonnet-w6-16.md`.

## Objectif
(1) `S/gate/ProofStore.kt` : `ProofCache` (w6-03) + `PhoneSync` (w6-02) persistés dans `files/proof/` et `files/sync/` (`SafeFile`), horloge `TvClock` dans `files/proof/clock.txt` observée toutes les 5 min et au démarrage ; (2) `S/gate/ProofSync.kt` : obtenir une preuve d'une TV : **Bluetooth** (canal propriétaire `…0005`, `OwnerFrames.PROOF_REQUEST/PROOF`, via `castbridge.owner.TvBluetooth`) ou **Wi-Fi** (`GET /api/activation/proof?nonce=` avec le jeton de la `LinkSession`), nonce aléatoire, `TvProof.verify`, TOFU (première fois sur lien sécurisé : affichage de l'empreinte et confirmation), `IdentityChanged` ⇒ dialogue de confirmation avec l'empreinte ; déclenché à chaque liaison (`TvLinkManager.state` → `Connected`), à l'ouverture si > 24 h, par bouton ; (3) `S/gate/PhoneGateRuntime.kt` : `state: StateFlow<PhoneGateState>`, `targetTv()`, `cell(feature, tv?)`, `refusal(feature, tv?)` (délègue à `PhoneGate` + `PhoneGateTexts`), `superActive` lu depuis w6-19 (`SuperSessionStore.active()` ; stub `false` tant qu'absent), `agentActive` depuis `FocalStore.hasDelegation()` (w4-13/w5-13 ; stub `false`) ; `BuildConfig.REQUIRE_TV_PROOF` + grâce (`FleetMigration.of(firstInstallTime, PHONE_LOCK_GRACE_START_MS)`) ; (4) `S/gate/GateWall.kt` : `@Composable GateWall(feature, tv = targetTv(), content)` : mur § 3.5 avec `Refusal` (titre, corps, âge de la preuve, boutons d'`Action` ⇒ intents : `PAIR_TV` → `TvPairScreen`, `SYNC_NOW` → `ProofSync`, `GET_PRODUCTION_KEY` → partage `LockedTexts.shareMessage`, `OPEN_TV_UPGRADE` → texte + télécommande si liée, `RENEW_KEY` → `ActivateTvActivity`, `ACTIVATE_TV` idem, `ENABLE_BLUETOOTH`, `OPEN_WIFI_SETTINGS`, `CHECK_CLOCK` → réglages date/heure, `CONFIRM_TV_IDENTITY`, `UPDATE_TV` → `TvHub` APK, `FREE_CONTENT` → `FreeContentScreen`, `SEND_TO_OTHER_TV`) ; (5) `MainActivity` : onglets fermés **visibles avec cadenas** (Jeux, Sur le téléphone, Apprendre, Parental, Boutique si présent) ⇒ `GateWall` ; **puce « TV cible : <nom> · production (preuve il y a 2 h) / essai / clé terminée / jamais synchronisée »** sous la barre, sélecteur si plusieurs TV ; bandeau `M-PROOF-STALE` ; ligne « Mode : minimal / complet / super / point focal » dans Réglages.

## Pourquoi (preuves)
- `S/MainActivity.kt:114-131` (onglets, `when (tab)`), `:101-102` (geste super) ; `S/TvLink.kt` (`TvLinkManager.state`, `LinkUi.Connected.session.credential`, `AndroidBtTransport`) ; `C/trust/PhoneLink.kt:23-31` (`SavedTv`, `LinkSession`) ; `OL/TvBluetooth.kt` (liaison au canal propriétaire : `TvBluetooth.with`, cf. w4-13 § Pourquoi) ; `S/ActivateTvActivity.kt:78-115` (envoi BT/Wi-Fi : même mécanique de transport) ; `C/owner/{PhoneGate,PhoneSync,PhoneGateTexts,TvProof,ProofCache}.kt` (6a) ; `BuildConfig.REQUIRE_TV_PROOF`, `PHONE_LOCK_GRACE_START_MS`, `PHONE_GRACE_DAYS`, `TRUSTED_KEYS` (w6-11) ; `S/FreeContentScreen.kt` (toujours ouvert).

## Fichiers possédés
Nouveaux `S/gate/{ProofStore,ProofSync,PhoneGateRuntime,GateWall,TargetTvChip}.kt` ; modifiés `S/MainActivity.kt`, `S/TvLink.kt` (un crochet `onConnected`), `S/Ui.kt` (si un composant commun est nécessaire), `S/TvPairScreen.kt` (bouton « Synchroniser » sur la carte TV). **Hors zone** : `S/LotsRuntime.kt`, `S/DownloadService.kt`, `S/TransferQueue*.kt`, `S/UploadService.kt`, `S/BtUploadService.kt`, `S/FastTransfer.kt`, `S/MoveToTv.kt`, `S/DlnaHandoff.kt`, `S/ShareToTvActivity.kt`, `S/player/**`, `S/GamesScreen.kt`, `S/LearnScreen.kt`, `S/QuizScreen.kt`, `S/ChessScreen.kt`, `S/TvHub.kt`, `S/TvLibraryScreen.kt`, `S/shop/**` (w6-17), `S/Parental*.kt` (w6-18), `OL/**` (w6-19), `C/**`.

## Signatures à respecter (contrat pour w6-17/18/19)
```kotlin
object PhoneGateRuntime { val state: StateFlow<PhoneGateState>; fun init(ctx: Context); fun targetTv(): TvSummary?; fun tvs(): List<TvSummary>
  fun cell(feature: PhoneFeature, tv: TvSummary? = targetTv()): Cell; fun refusal(feature: PhoneFeature, tv: TvSummary? = targetTv()): Refusal?
  fun allowed(feature: PhoneFeature, tv: TvSummary? = targetTv()): Boolean; fun syncNow(ctx: Context, tv: TvSummary? = null) }
@Composable fun GateWall(feature: PhoneFeature, tv: TvSummary? = PhoneGateRuntime.targetTv(), content: @Composable () -> Unit)
```

## Étapes
1. `ProofStore` (fichiers, `TvClock`, exclusions de sauvegarde posées par w6-11).
2. `ProofSync.sync(ctx, tv)` : choisir le transport (session Wi-Fi si `base != null`, sinon Bluetooth) ; nonce ; vérification ; TOFU/IdentityChanged ; mise à jour `PhoneSync` (`proofOk`/`proofFailed(reason)` avec mappage `BtUnavailable.Reason`→`BT_OFF`, IOException→`TV_UNREACHABLE`, `ERR_MAGIC`/404→`TV_OLD_VERSION`, `Rejected(TRIAL|…)`→raisons) ; jamais sur le fil principal ; notification silencieuse en cas de `M-PROOF-STALE` (une par jour).
3. `PhoneGateRuntime` : recalcul sur `ProofStore`, `TvLinkManager.state`, session super, agent ; `NotRequired` quand `!REQUIRE_TV_PROOF`.
4. `GateWall` + `TargetTvChip` + onglets ; **aucune chaîne de refus** écrite ici (tout vient de `PhoneGateTexts`).
5. Captures : mur (A, B, C, E, G), puce, dialogue d'identité → `docs/img/phone/w6-*.png`.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -rn 'exige une TV\|Synchronisez le téléphone' android/sender/src/main/kotlin | grep -v 'PhoneGateTexts' | wc -l   # 0 (aucune phrase hors catalogue)
grep -n 'GateWall(' android/sender/src/main/kotlin/castbridge/sender/MainActivity.kt   # ≥ 3 onglets
```
Observable (émulateur + TV de référence) : interrupteur éteint ⇒ rien ne change ; `-PrequireTvProof=true` : TV d'essai liée ⇒ onglets cadenassés, mur M-TV-TRIAL nommant la TV ; clé de production posée puis « Synchroniser » ⇒ tout s'ouvre, puce « production (preuve à l'instant) » ; TV éteinte 15 jours (horloge avancée **sur le téléphone** sans toucher la TV) ⇒ mur M-PROOF-EXPIRED avec « hors connexion depuis 15 jours ».

## Cas limites
Plusieurs TV : fonctions propres au téléphone ouvertes si l'une est prouvée ; puce = TV de la liaison, sinon `saved.default()` ; TV retirée de l'app ⇒ `ProofStore.forget` ; mode enfant sur la TV (lu dans `GET /api/parental` quand lié) ⇒ bandeau « La TV est en mode enfant (<prénom>) », jamais de saisie du code parental sur le téléphone ; preuve refusée pour `IDENTITY_CHANGED` ⇒ dialogue avec l'empreinte des deux côtés ; `REQUIRE_TV_PROOF` éteint ⇒ `NotRequired` partout.

## À ne pas faire
Ne pas masquer d'onglet ; pas de `enabled=false` muet ; aucune suppression de données en `Minimal` ; ne pas toucher aux fichiers de w6-17/18/19 ; ne pas allumer l'interrupteur.

## Rapport
`STATUT`, captures, où chaque `Action` mène, écarts avec la matrice.
