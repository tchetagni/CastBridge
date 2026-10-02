# w6-07 — Rapports v2 (cœur) : corps `daily/weekly/alert/snapshot`, signature Ed25519 de la TV + HMAC, CBTP v2, vérification côté téléphone, liste noire

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (interfaces de w6-05/06)
> **Groupe : W6a-2** (vague W6a) · prérequis : w6-05, w6-06, w6-03 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*ReportV2*' --tests '*ReportSyncV2*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 6a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w6-05/w6-06 : codez contre leurs API décrites dans la conception ; si elles ne sont pas fusionnées, des interfaces locales minimales + le dire).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.5, § 2.7 (Bluetooth), § 2.6 (alertes détenteurs). Branche `claude/sonnet-w6-07`. Rapport : `docs/agent-reports/sonnet-w6-07.md`.

## Objectif
(1) `ReportV2` : constructeurs purs `daily`, `weekly`, `alert`, `snapshot` (champs exacts de § 2.5, additifs sur v1, `v = 2`) à partir des structures de w6-05 (tranches, écran, connexions, inventaire), des compteurs existants (`engine.report`), de w5-18 (jetons) et des options de w6-06 (`shareTitles`) ; `quality` calculé (mesuré / meilleur effort / indisponible) ; (2) **signature** : `OutMsg` gagne `sig` (`CBR2`, Ed25519 par `InstallSigner` de w6-03, `kid`) en plus de `mac` ; (3) CBTP v2 : requête `{"v":2,"want":[…]}`, réponse avec `installPub`, `holders`, `consent`, `snapshot` ; un ancien téléphone / une ancienne TV restent compatibles ; (4) `ReportInbox` : vérifie `sig` quand une clé d'installation est épinglée pour la TV (`setInstallKey(tv, kid, pub)`), sinon HMAC seul ; (5) alertes `holder_added/removed`, `late_use` ; (6) test de **liste noire** : aucun nom de champ de `ParentalPrivacy.BLACKLIST_KEYS` dans un corps v2.

## Pourquoi (preuves)
- `C/parental/ParentalReports.kt:139-142` (`OutMsg.envelope()`), `:128-137` (`ReportMac` `CBR1`), `:222-268` (`ReportBuilder` v1), `:279-390` (`ParentalReports` : `extras`, `onEvent`, `tick`, `sendDaily`) ; `C/parental/ParentalSync.kt:62-104` (`ParentalSyncProtocol` : la requête ne porte que `v`), `:25-49` (`ReportSyncHost.fetch/ack`), `:125-185` (`ReportInbox.accept`), `:194-218` (`ReportSync.run`) ; `C/parental/tab/TvJournal.kt:26` (`forReport`) ; w5-18 (compteurs `tokensSpent`, `purchasesDenied`, `purchasesWithPin`) ; w6-03 (`InstallSigner.sign`, `keyId`).

## Fichiers possédés
Nouveau `C/parental/ReportV2.kt`, `CT/parental/ReportV2Test.kt`, `CT/parental/ReportSyncV2Test.kt` ; modifiés `C/parental/ParentalReports.kt`, `C/parental/ParentalSync.kt`. Les tests racine `CT/Parental*Test.kt` appartiennent à w6-06 : vos tests vont dans `CT/parental/` (si un test racine existant casse à cause d'un champ additif, le signaler dans le rapport, ne pas l'éditer). **Hors zone** : `Holders.kt`, `ParentalPrivacy.kt`, `ParentalApi.kt` (w6-06), `Collect.kt`, `tab/**` (w6-05, w6-08), `R/**`, `S/**`.

## Étapes
1. `ReportV2.Inputs` (tout ce qu'il faut, injecté : `engineReport`, `slotsOfDay`, `screenOfDay`, `connectionsOfDay`, `inventoryDiff`, `games`, `tokens`, `downloads`, `holdersCount`, `supervision`, `clockDoubt`, `shareTitles`, `installKid`) ; `daily(inputs, tv, tvId, day, profile)`, `weekly(...)`, `alert(type, …)`, `snapshot(fgPkg, label, profileName, screenOn, now)` ; `tvId` = SHA-256 tronqué (12 hex) de la clé publique d'installation.
2. `quality` : `apps` ∈ `bestEffort` si `supervision.state == active`, sinon `unavailable` et `apps = []` (jamais des zéros) ; `screen` mesuré ; `tokens` mesuré si w5-18 présent sinon absent (pas `0`).
3. `ParentalReports` : `signer: (() -> InstallSigner?)` injecté ; `ReportOutbox.enqueue` ajoute `sig` quand un signataire existe ; `OutMsg.envelope()` ajoute `"sig"` ; `onEvent` : `HolderChanged` ⇒ alerte `holder_added/removed` (sans dédoublonnage par 10 min : chaque changement compte) ; `LateUse` (événement nouveau ou calcul dans `tick` : usage entre 22 h et 6 h si `lateUseAlert`) ; `snapshotFor(peer)` limité à 1/min et journalisé via un rappel `onSnapshotServed(peerName)` (la TV l'écrit dans `TvJournal`, w6-13).
4. `ParentalSyncProtocol` : lit la requête (`v`, `want`) ; `ReportSyncHost.fetch(peer, want)` renvoie `v:2`, `installPub` + `kid` **une fois** (comme la clé HMAC, jusqu'à l'ack `keys:true`), `holders`, `consent` (`pending|ok|none`), `reports` seulement si `consent == ok`, `snapshot` si demandé et détenteur ; TV v1 : réponse inchangée ; téléphone v1 : requête `{"v":1}` ⇒ réponse v1.
5. `ReportInbox` : `setInstallKey(tv, kid, pubBase64)`, `installKey(tv)` ; `accept` : si clé épinglée ⇒ `sig` **obligatoire** et vérifiée (`TrustedKey.verify`) ; sinon HMAC seul (ancienne TV) ; `Outcome.REJECTED` sur mauvaise signature ; `ReportSync.run` stocke `installPub` si `designated` et le signale dans `SyncResult(installKeyStored, consent, holders)`.
6. Tests : corps v2 complets (champs présents, additifs, `v = 2`), `quality` sans zéros fantômes, `shareTitles = false` ⇒ titres « Vidéo » dans `events`, liste noire (`BLACKLIST_KEYS` sur toutes les clés JSON, récursif), signature + HMAC (corps modifié ⇒ rejeté ; `sig` absente avec clé épinglée ⇒ rejeté ; ancienne TV sans `sig` et sans clé épinglée ⇒ accepté), CBTP v1↔v2 (quatre combinaisons, tuyaux en mémoire comme `ParentalReportsTest` existant), consentement `pending` ⇒ aucun rapport livré, `snapshot` 1/min, alertes détenteurs.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.parental.ReportV2Test' --tests 'castbridge.core.parental.ReportSyncV2Test' --tests 'castbridge.core.Parental*'   # vert
grep -n '"v" to 2' android/core/src/main/kotlin/castbridge/core/parental/ReportV2.kt   # ≥ 3
grep -rn 'TvConnect\|ServerLink\|HttpLite' android/core/src/main/kotlin/castbridge/core/parental   # 0 hit
```

## Cas limites
Corps > 60 Ko (tranches de 60 applications) : `apps` tronqué aux 25 premières par minutes, `slots` omis au-delà de 10 (le téléphone affiche « détail horaire : 10 premières ») ; outbox 200 Ko : inchangé (les corps v2 sont plus gros : vérifier qu'un `daily` v2 typique ≤ 12 Ko ; sinon réduire) ; TV sans `InstallSigner` (w6-12 non déployé) ⇒ pas de `sig`, HMAC seul ; horloge TV douteuse ⇒ `clockDoubt` renseigné.

## À ne pas faire
Ne pas casser le format v1 (un ancien téléphone doit toujours lire `daily` : tests existants intacts) ; aucune route HTTP ici (w6-15) ; pas d'Android.

## Rapport
`STATUT`, schéma JSON final v2 (à recopier dans PARENTAL.md par w6-20), tailles mesurées, API pour w6-08, w6-13, w6-15, w6-18.
