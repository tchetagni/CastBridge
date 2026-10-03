# w19-10 — TV (câblage mince, après le gel) : journal INFO `CB-REFUS` sans secret, carte « Refusé : … » et états `paused/refused/aborted` de la réception, page « Téléphone » (version du téléphone + encouragement « Mettre à jour le téléphone »), époques alimentées par `TvService`, `bootId`, `appVersion`/`versionCode`/`caps` dans `/api/hello`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (journalisation : secrets ; registre persisté) · statut : **ATTEND la sortie du gel W15**
> **Groupe : W19-S3** · prérequis : w19-01, 02, 06 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin -PrequireActivation=true :core:test --tests 'castbridge.core.sync.*' --tests 'castbridge.core.lint.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · audit Opus : obligatoire

**Vague 19 · Effort M · Modèle : sonnet.** Conception : DESIGN-W19 § 1.3, § 2.2 (`UpdatePhoneNudge`), § 3.1-3.2 (journal, registre), § 3.4 (sources d'époques), § 3.5.5 (états TV). Branche `claude/sonnet-w19-10`. Rapport : `docs/agent-reports/sonnet-w19-10.md`. Builds TV **verrouillés seulement** (`-PrequireActivation=true`). Règle R9 W15 : un seul cahier sur `R/TvService.kt`.

## Objectif
(1) `R/TvService.kt` (une zone) : construit `ReceiverServer` avec `appVersion = BuildConfig.VERSION_NAME`, `versionCode`, `caps = Caps.current(...)` (réels : `wd` seulement si `WifiDirectGroup` disponible, etc.), `ledger = RejectionLedger(dir = filesDir/sync)`, `onRefusal = { Log.i("CastBridge", it.line) }` (format `CB-REFUS …`, `Redact.scrub`) ; `Epochs(dir)` avec les sources : `LibraryRootSource` = `ContentIndex.root()` (w19-07), `TrustEpochSource` = `TrustRegistry` (+1 à chaque écriture), `EditionSource` = activation, `FilingHashSource`, `LotsHashSource` = `LotStore.manifest()` ; enregistre `SyncRoutes`. (2) `TransferProgress`/carte de réception (`R/ReceiveCards`) : états `paused(reason, since)` (« Réception en pause : le téléphone ne répond plus · 0:20 »), `refused(code)` (« Refusé : nom déjà pris — film.mkv (Galaxy) »), `aborted(code)` (« Annulé par le téléphone : le fichier a changé ») : mots et couleurs de `SignalAgreement`. (3) Page « Téléphone » (`R/PhonePage` W11, ou écran « Ajouter un téléphone ») : ligne par téléphone vu (`X-CB-App`) : « CastBridge 1.2.39 (69) · proto 7 » + `UpdatePhoneNudge` (« mise à jour conseillée : doublons, file, rangement »), jamais bloquant ; ligne Cohérence côté TV : « Téléphone Galaxy : 1 envoi en cours · concordant ». (4) Écran « Diagnostic » existant : section « Derniers refus » (registre, masqué) dans « Copier le rapport ».

## Pourquoi (preuves)
- R-17 : « rien côté TV » : aucune journalisation des refus (`grep` de `ReceiverServer.kt` : aucun `log`) ; R-04 : la carte TV ne connaît que « Prêt à recevoir » / « en cours ».
- `R/TvService.kt:1032` nomme la TV ; c'est lui qui construit le serveur (lecture W18 § 1).
- `docs/BT-PLUG-AND-PLAY.md` « Diagnostic » : « Copier le rapport » sans secret existe : la section « Derniers refus » s'y ajoute.

## Fichiers possédés
`R/TvService.kt` (zone de construction du serveur + sources d'époques, ≤ 80 lignes), `R/ReceiveCards*.kt` (états), `R/PhonePage*.kt` ou l'écran « Ajouter un téléphone » (zone liste), écran Diagnostic (zone « Derniers refus »), `CT/sync/TvWiringTest.kt` (décisions pures extraites : format de ligne de journal, masquage). **Hors zone** : `C/` (sauf `QUESTION:`), `S/`, `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `R/ActivationCenter.kt`, `R/RentalHub.kt`.

## Étapes
1. Journal + registre + `onRefusal` ; test JVM de la ligne (aucun PIN/jeton/adresse complète même si injectés dans le message).
2. `/api/hello` enrichi par les vraies valeurs ; `caps` réelles (table : capacité ⇒ condition runtime).
3. Sources d'époques ; `SyncRoutes` enregistrée ; `bootId` par processus.
4. Carte de réception + page « Téléphone » + Diagnostic.
5. **Vert** : porte ; `FUMÉE:` ; **à valider sur TV réelle** : `adb logcat -s CastBridge` montre `CB-REFUS` sur un `NAME_TAKEN` ; `GET /api/hello` (TVro) montre `protocol:8` ; `GET /api/sync/state` ≤ 2 Kio.

## Critères d'acceptation
- Aucune capacité annoncée qui n'est pas réellement servie (test : pour chaque cap de `Caps.current()`, la route répond ≠ 404/501).
- Ligne de journal sans secret : test avec PIN/jeton/adresse injectés.
- Aucun texte français nouveau dans `R/` (tout vient de `SignalAgreement`, `Reason`, `UpdateNudge`).

## Cas limites
Registre illisible au démarrage ⇒ vide, rien n'est cru ; TV d'essai ⇒ `/api/sync/state` 200 ; téléphone ancien sans `X-CB-App` ⇒ « CastBridge (version inconnue) » sans encouragement.

## À ne pas faire
Construire une TV non verrouillée ; journaliser un corps de requête ; annoncer `wd` sans le vérifier ; toucher `PlayerActivity`/`HomeScreen`.

## Rapport
RAPPORT + `FUMÉE:` + `SYMBIOSE: cap=<Caps.current() réelles> · proto=8 · reason=journalisées · deux écrans=carte « Refusé » ↔ notification (P-53)`.
