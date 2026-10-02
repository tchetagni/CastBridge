# w13-09 — CastBridge (téléphone) : puce à trois vérités (`LinkHealth`) dans `TvHome`/`TvLinkStatus`/`TvScreen`, bandeau de blocage dans les cartes, « Réparer », état initial « vérification »
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : aucune (écrans) · statut : PRÊT
> **Groupe : W13-c** (vague W13, tranche S1) · prérequis : w13-07 et w13-08 fusionnés (`BlockerState`, `PinPrompt`, `RepairSheet`) ; le correctif ciblé « état initial `NoTv` » (confié ailleurs) fusionné **ou** absent : ce cahier ne le refait pas · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin && cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.LinkHealthTest'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M) · audit Opus : non

**Vague 13c (téléphone) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W13` § 1.1, § 1.3 (F2), § 3.3, § 3.6, § 2 (classe « faux état initial »). Branche `claude/sonnet-w13-09`. Rapport : `docs/agent-reports/sonnet-w13-09.md`.

## Objectif
(1) `TvLinkManager.health: StateFlow<LinkHealth>` : calculé par `HealthProbe` (sonde `/api/hello` + `/api/info` **avec le secret que `PinStore.get(clé)` donnerait pour cet écran** + `storage/check` d'un octet), toutes les 20 s au premier plan, 5 min en fond, et à chaque `Step` de la liaison ; **initial = `LinkHealth.initial(saved.list().isNotEmpty())`** publié avant le premier `step()` ; (2) la **couleur** du point de `TvLinkStatus` et de la ligne « code » de `TvHome` vient de `health.tone()` (texte : `LinkText`/`BlockerTexts.chip`) ; jamais vert sans sonde authentifiée fraîche ; **jamais « Aucune TV ajoutée » avec une TV sauvegardée** ; (3) **F2 corrigée** : sur 401 avec PIN, `TvHome` **n'ouvre plus** l'assistant : la carte affiche `PinPrompt` ; (4) les cartes de transfert (`TvHome`, `TvScreen`, `TvHub`, `TvTransferScreen`, `DlnaHandoff`, `CastSheet`) affichent le bandeau `BlockerBanner` (phrase + bouton `Fix` + code support) depuis `BlockerState` ; « Réparer » ouvre `RepairSheet` ; la file montre `Échec : <phrase>` + bouton ; (5) `OpenWithActivity` : « Copier vers la TV » reste actif sur `UNKNOWN` (texte « Vérification de la liaison… »).

## Pourquoi (preuves)
- Vert aujourd'hui : `S/TvPairScreen.kt:64-70` (tone ⇒ point), `S/TvHome.kt:125-137,170-182` (ligne « code » : `reachable`), `S/TvScreen.kt:61,107-119,236` ; état initial : `S/TvLink.kt:104,240-249` ; sondes : `S/LinkAndroid.kt:75-93` ; secret par écran : `S/PinStore.kt:22`, `S/TvLink.kt:191-206`.
- F2 : `S/TvHome.kt:130-132` (`wizard = true`), `:106-111` (retour anticipé), `:221` (`Failed` petit texte), `:347` (`msg` inatteignable) ; file : `:223-250`.
- Entrées : `S/TvHub.kt:96-144,200-277`, `S/TvTransferScreen.kt:94`, `S/DlnaHandoff.kt:48-63`, `S/player/CastSession.kt:159,273`, `S/OpenWithActivity.kt:77` (+ texte « Aucune TV ajoutée »).
- Contrats : `C/link/LinkHealth.kt` (w13-03), `S/link/BlockerState.kt` (w13-07), `S/link/{PinPrompt,RepairSheet}.kt` (w13-08).

## Fichiers possédés
`S/TvLink.kt` (zone : `health` + `HealthProbe` ; **pas** la boucle `loop()` ni `publish()` si le correctif ciblé les a touchés : lire d'abord `git log -1 -- S/TvLink.kt` et relire les lignes), `S/LinkAndroid.kt` (`storageCheck` sonde), `S/TvHome.kt`, `S/TvPairScreen.kt` (`TvLinkStatus`), `S/TvScreen.kt`, `S/TvHub.kt`, `S/TvTransferScreen.kt`, `S/DlnaHandoff.kt`, `S/player/CastSheet.kt` (bandeau seulement), `S/OpenWithActivity.kt` (état `UNKNOWN`) ; nouveaux : `S/link/BlockerBanner.kt`, `S/link/HealthProbe.kt`, `CT/link/LinkHealthSourceTest.kt` (test de source). **Hors zone** : `S/UploadService.kt`, `S/TransferQueue*.kt`, `S/PinStore.kt`, `S/MainActivity.kt`, `S/link/{PinPrompt,RepairSheet,RepairDeepLink}.kt`, `C/**` (sauf le test de source).

## Étapes
1. `HealthProbe(env: AndroidLinkEnv, credentialFor: (clé) -> String?)` : `run(tv, base, clé)` ⇒ `hello` ⇒ `check(base, secret)` (`/api/info`) ⇒ `storageCheck(base, secret, "cb-probe.bin", 1)` ⇒ `LinkHealth.fromPreflight(...)` ; `authorized = NO` + `Blocker` `PIN_*` si 401 ; `ready = NO` + `TRIAL_CLOSED` si 403 trial ; TV ancienne (404 sur check) ⇒ `ready = UNKNOWN`.
2. `TvLinkManager.health` : `MutableStateFlow(LinkHealth.initial(saved.list().isNotEmpty()))` dans `init` ; mise à jour par `HealthProbe` sur minuterie (20 s / 5 min) et après chaque `publish(step)` ; `setForeground(true)` ⇒ sonde immédiate.
3. `TvLinkStatus` : `dot = health.tone(now, maxAge)` (GOOD/WARN/BAD/NEUTRAL) ; `view.detail` complété par `BlockerTexts.chip(...)` quand `health.blocker != null` ; bouton = `Fix` du blocage s'il existe, sinon `view.action` ; « Diagnostic » ouvre aussi `RepairSheet`.
4. `TvHome` ligne « code » : point et texte depuis `health` ; sur 401 PIN : **ne pas** poser `wizard = true` ; `BlockerState.last = PIN_WRONG` ⇒ la carte « Envoi » (ou une carte « Liaison ») montre `PinPrompt` ; `msg` rendu **au-dessus** de la carte (déplacer la ligne `:347`).
5. `BlockerBanner(blocker, tvName)` : icône par gravité, phrase, bouton `Fix` (⇒ `RepairActions.perform` ou `PinPrompt`), « Code support : CB-… » copiable ; inséré dans chaque carte de transfert ; `Failed` de la file ⇒ phrase `BlockerTexts` + bouton.
6. `TvScreen` : `reachable` initial `false` ⇒ remplacé par `health` ; `badPin` ⇒ `PinPrompt` sous le champ existant.
7. `OpenWithActivity` : `LinkUi.NoTv` **et** `TvLinkManager.saved.list().isEmpty()` ⇒ « Aucune TV ajoutée » ; sinon `UNKNOWN` ⇒ « Vérification de la liaison avec <TV>… », bouton actif (la file attend ≤ 60 s puis `QUEUE_NO_LINK`).
8. `LinkHealthSourceTest` (JVM, lit les sources) : `S/OpenWithActivity.kt` ne contient plus `LinkUi.NoTv ->` sans `saved.list().isEmpty()` ; `S/TvHome.kt` ne contient plus `wizard = true` dans le bloc 401 ; `TvPairScreen.kt` lit `health`.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -n 'Aucune TV ajoutée' android/sender/src/main/kotlin/castbridge/sender/OpenWithActivity.kt` ⇒ gardé par `isEmpty()` ; `grep -c 'BlockerBanner(' android/sender/src/main/kotlin/castbridge/sender/*.kt android/sender/src/main/kotlin/castbridge/sender/player/CastSheet.kt` ≥ 6 ; `grep -n 'wizard = true' android/sender/src/main/kotlin/castbridge/sender/TvHome.kt` ⇒ seulement l'initialisation et le bouton « Changer ».

## Cas limites
Plusieurs TV (santé de la TV **par défaut**/cible) ; TV Bluetooth seule (`base == null` ⇒ `reachable` par HELLO, `authorized` = jeton valide, `ready = UNKNOWN`) ; écran en fond (pas de sonde 20 s) ; sonde pendant un envoi (ne pas doubler : si `BlockerState.upload` est récent, l'utiliser).

## À ne pas faire
Pas de texte français hors `BlockerTexts`/`LinkText` ; ne pas réécrire `LinkMachine`/`LinkDriver` ; ne pas sonder avec un PIN déjà refusé (`CredentialGate` du driver + `BlockerState`) ; ne pas refaire le correctif « état initial » de la boucle.

## Rapport
`STATUT`, lignes modifiées par écran, capture impossible ⇒ parcours décrit, interaction constatée avec le correctif ciblé de `TvLink.kt` (fusionné ou non), contrats pour w7-20 (`LinkChip` lit `health`).
