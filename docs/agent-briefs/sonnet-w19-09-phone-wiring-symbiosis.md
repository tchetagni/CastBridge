# w19-09 — Téléphone (câblage mince, après le gel) : codes `Reason` et états finaux dans la file, la notification et la barre « où en est ma copie » ; `TransferIdentity` + `SourceGuard` dans la file ; encouragements « Mettre à jour la TV » ; carte « Cohérence » sur la fiche de la TV ; bouton « Wi-Fi Direct » et autres fonctions gardés par capacité ; en-tête `X-CB-App`

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (câblage ; toute décision est dans le cœur) · statut : **ATTEND la sortie du gel W15** (ou l'exception « correctif terrain » pour la partie R-17 : file + notification)
> **Groupe : W19-S3** (câblage) · prérequis : w19-01, 02, 06, 13 fusionnés ; `claude/copy-queue-fix` (R-09) et `claude/pin-persistence` (R-10) fusionnées · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :core:test --tests 'castbridge.core.journey.*' --tests 'castbridge.core.lint.*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 1,5 j) · audit Opus : non (échantillon si la file est touchée au-delà du câblage)

**Vague 19 · Effort M · Modèle : sonnet.** Conception : DESIGN-W19 § 2.2 (encouragements), § 2.4 (replis), § 3.3, § 3.4 (carte), § 3.5.5 (états). Branche `claude/sonnet-w19-09`. Rapport : `docs/agent-reports/sonnet-w19-09.md`. Règle W18 R4 / W19 : **aucune décision** dans un écran ou un service ; tout vient de `C/sync/`, `C/xfer/`.

## Objectif
(1) `S/TransferQueue.kt` + `S/UploadService.kt` : `TransferClient.Result` ⇒ `XferState` ; élément de file : champ additif `identity` (`TransferIdentity.encode()`), relevé `SourceGuard` au premier `begin` ; états finaux affichés par `XferTexts` (« Reprise de … (38 %) », « En attente de la TV · 0:42 », « En pause : … · reprise automatique au prochain contact », « Abandon : <raison> » + « Réessayer ») ; le détecteur de blocage **suspendu** tant que `slowed` et `received` avance ; « essai n/12 » dans la notification. (2) `S/TvLink.kt` (une zone) : `hello` ⇒ `HelloV2.parse` ⇒ `PinBook.cap.<tvId>` ; `AppIdentity` depuis `BuildConfig` injectée dans `TvCredential` (en-tête `X-CB-App`) ; `SyncPace` ⇒ `GET /api/sync/state` ⇒ `PhoneSyncState` ⇒ `Coherence.compare` ⇒ état publié. (3) Fiche de la TV (`S/TvHome.kt` zone carte, ou composant neuf `S/CoherenceCard.kt`) : carte « Cohérence » repliée en vert (« TV et téléphone concordent »), dépliée en orange/rouge avec les lignes de `Coherence` et **une** action par réparation humaine ; encouragement `UpdateNudge` (une fois par jour, jamais pendant une copie) avec le bouton « Comment mettre à jour » (texte : Download de la clé USB). (4) `FeatureGate` : bouton « Wi-Fi Direct » (`S/WdManualCard.kt`) **masqué** avec sa ligne si `wd` absent ; `/api/have` jamais appelé sans `dedup` ; envoi de lots jamais sans `lots1` ; seuil de blocage 90 s sans `slowed`. (5) Barre « où en est ma copie » (`QueueGlances`, UX W11) : même mot et même couleur que `SignalAgreement`.

## Pourquoi (preuves)
- R-17 : `S/UploadService.kt` ne voit qu'une `IOException` ; la notification ne compte pas les essais ; R-09 : la file persistée existe (format JSON, champ additif possible).
- `S/TvLink.kt` est le seul endroit qui boucle (`step()`), `S/WdManualCard.kt:158` lit déjà `/api/hello` sans rien garder.
- `C/ux/UiTexts.kt` (UX 2026-10-03) : barre « où en est ma copie » sur tous les onglets : le lieu existe.

## Fichiers possédés
`S/TransferQueue.kt`, `S/UploadService.kt`, `S/TvLink.kt` (zone `hello`/sync seulement), `S/TvHome.kt` (zone carte de la TV) ou `S/CoherenceCard.kt` (neuf), `S/WdManualCard.kt` (visibilité seulement), `S/QueueGlances*.kt` (si séparé), tests JVM dans `CT/sync/PhoneWiringTest.kt` (décisions pures extraites si une logique apparaît). **Hors zone** : `C/` (si un besoin de décision apparaît ⇒ `QUESTION:` et interface locale), `R/`, `S/player/CastSession.kt`, `S/OpenWithActivity.kt`.

## Étapes
1. File + notification + états finaux (R-17 visible) ; `compileDebugKotlin`.
2. `hello`/caps/`X-CB-App`/sync dans `TvLink` ; carte Cohérence ; encouragement.
3. `FeatureGate` sur les quatre points ; barre.
4. **Vert** : porte ; `FUMÉE: à lancer par le coordinateur` ; liste **à valider sur matériel** : P-53 (refus visible des deux côtés), P-54 (Cohérence), H-REPRISE (1)…(6).

## Critères d'acceptation
- Aucune chaîne française nouvelle dans `S/` (toutes viennent de `Reason`, `XferTexts`, `Coherence`, `UpdateNudge`, `FeatureGate`) : test de source.
- Un ancien élément de file (sans `identity`) est relu et fonctionne.
- Un refus `NAME_TAKEN` arrive dans la notification en ≤ 5 s sur l'émulateur (coordinateur) ; `GAVE_UP` après 12 essais ; « Terminé » jamais sans `DoneVerified`.

## Cas limites
TV sans `sync` ⇒ carte grise « cohérence non vérifiable » ; deux TV ⇒ une carte par TV ; app tuée pendant « En pause » ⇒ relecture = même état ; encouragement déjà montré aujourd'hui ⇒ rien.

## À ne pas faire
Décider dans un écran ; appeler une route non déclarée ; bloquer un envoi sur une version ; afficher une adresse ou un code.

## Rapport
RAPPORT + `FUMÉE:` + `SYMBIOSE: cap=envelope,sync,dedup,wd,lots1,slowed (consommées) · proto=8 · reason=tous (affichés) · deux écrans=P-53/P-54 à valider` .
