# w18-02 — Cœur : `WdPolicy` (une seule table de routes `LAN > WD ≥ BT` par usage), `WdSession` (jonction directe par identifiants, déclencheurs, cycle de vie, perte en plein cast, bascule entre deux TV), `WdLine` (signalétique)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (pendant le gel : cœur seul)
> **Groupe : W18a-2** (vague W18a, cœur) · prérequis : w18-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.link.*' --tests 'castbridge.core.trust.LinkDriverTest' --tests 'castbridge.core.BtLinkTest'`
> **Jauge : ≈ 700 k jetons entrée / 35 k sortie** (effort L, ≈ 3 j) · audit Opus : échantillon

**Vague 18a · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 1 (ligne « Route de contrôle »), § 3.2-3.5, § 4 (c), § 5.1, § 5.3, § 5.4, D-W18-1, D-W18-3, D-W18-9. Branche `claude/sonnet-w18-02`. Rapport : `docs/agent-reports/sonnet-w18-02.md`. Règle W15 R5 : aucun fichier `S/`, `R/`.

## Objectif
(1) `WdPolicy.route(use, facts)` : **une** table pour `CONTROL`, `SYNC`, `BULK(bytes)`, `CAST`, `REMOTE`, `QUIZ` : `UseLan` / `UseWd` / `UseBt` / `Wait` / `AskOnce` / `NoRoute`, qui **enveloppe** `BulkRoute.decide` (masse, gardé) et remplace `LinkPlanner.plan` pour le contrôle par `LinkPlanner2` (route `Direct` **seulement si** `wdUp`) : corrige le défaut d'audit « contrôle pollué par 192.168.49.1 » ; (2) `WdSession` : automate pur par TV, qui **étend** `WdClient` : état `Known(creds)` ⇒ jonction **directe** (`Effect.Join` sans CBTN) ; `RequestViaBt` seulement sans identifiants ou après `JOIN_DENIED` (rotation) ; déclencheurs (§ 3.2) ; tenue tant qu'un usage est actif (file, cast, télécommande ouverte, salle Quiz) ; départ après 2 min sans usage **et** app en fond ; perte en plein cast ⇒ re-jonction ×1 immédiate puis courbe `LinkMachine` ; **une seule** session `Up` par téléphone : `switchTo(tvB)` quitte A puis joint B ; (3) `WdLine.of(snapshot)` : ligne d'état et niveau `SignalLevel` (§ 5.4), textes français ; (4) `BulkRoute` : seuil 5 Mo **ignoré** quand des identifiants sont connus ou WD `Up` ; `Why.TRIAL` retiré.

## Pourquoi (preuves)
- `C/link/BulkRoute.kt` (branche R-14) : `decide` (LAN d'abord, `MIN_WD_BYTES`, `TRIAL`, permission), `WdBackoff`, `BulkLine` ; `C/link/WdClient.kt` : `Requesting → Joining → Probing → Up`, `IDLE_RELEASE_MS = 30 s`, `Effect.Join(ssid, pass, method)` ; `AutoWifiDirectTest` (27).
- `C/tv/BtProtocol.kt` `LinkPlanner.plan` (Lan → Direct → Bluetooth) ; `S/TvLink.kt` `canJoinWifiDirect = { false }` (R-14) : correctif trop large.
- `C/trust/LinkMachine.kt:193-215` (`nextAttempt` : 2→60 s), `C/trust/LinkDriver.kt:182-193` (repli de route) ; `C/ux/TvSignal.kt` (VERT/ORANGE/ROUGE/NOIR).
- Audit Opus R-14 (en attente) : « groupe jamais retiré avec une télécommande ouverte » ⇒ l'usage `REMOTE` tient la session.

## Fichiers possédés
Nouveaux `C/link/WdPolicy.kt` (contient `LinkPlanner2`), `C/link/WdSession.kt`, `C/link/WdTriggers.kt`, `C/link/WdLine.kt`, `CT/link/{WdPolicyTest,WdSessionTest,WdLineTest}.kt`. Zone : `C/link/BulkRoute.kt` (≤ 15 lignes : `Facts.wdKnown`, seuil conditionnel, retrait de `TRIAL`). **Lecture seule** : `C/link/WdClient.kt` (réutilisé, non modifié), `C/tv/BtProtocol.kt`. **Hors zone** : `S/`, `R/`, `C/trust/LinkDriver.kt`.

## Étapes
1. **Rouge** : `WdPolicyTest` (table, ≥ 20 lignes) : LAN qui répond ⇒ `UseLan` pour tout usage ; pas de LAN + identifiants connus ⇒ `UseWd(direct)` pour **tout** usage, même 1 Ko ; pas de LAN, pas d'identifiants, BT ⇒ `UseWd(viaBt)` (chemin R-14) sauf < 5 Mo ⇒ `UseBt` ; CAST sans LAN ni WD ⇒ `UseBt` **seulement** pour audio/photo (`CastKind`), `NoRoute(text)` pour vidéo ; QUIZ ⇒ jamais BT ; `wdUp=false` ⇒ `LinkPlanner2` ne rend **jamais** `Direct` ; TV d'essai ⇒ WD permis ; Android < 10 ⇒ BT ; backoff ⇒ BT.
2. **Rouge** : `WdSessionTest` (temps simulé) : (a) `Known` + déclencheur `APP_FOREGROUND` ⇒ `Effect.Join` **sans** `RequestGroup` ; `Joined` ⇒ `Probe` ⇒ `Up` ; (b) usage `REMOTE` ouvert : à T+10 min sans octet, **pas** de `Leave` ; télécommande fermée + app en fond ⇒ `Leave` à +2 min ; (c) `Lost` pendant `CAST` ⇒ `Join` immédiat ×1, puis 2 s, 4 s… ; 3 échecs en 10 min ⇒ `Failed(BACKOFF)` et `Effect.Failure` ; (d) `JOIN_DENIED` avec identifiants connus ⇒ `Effect.ForgetCreds` puis `RequestGroup` par BT (rotation) ; (e) `switchTo(B)` depuis `Up(A)` ⇒ `Leave` puis `Join(B)` ; jamais deux `Up` ; (f) déclencheur `BACKGROUND_JOB` ⇒ **aucun** effet ; (g) `Start` pendant `Joining` ⇒ rien (jamais deux demandes) ; (h) `method = NETWORK_SPECIFIER` ⇒ `joinTimeoutMs` 50 s et drapeau `rememberedApproval` qui passe à vrai après le premier succès (la ligne dit « une seule fois »).
3. **Rouge** : `WdLineTest` : chaque état ⇒ (niveau, texte) de la table § 5.4 ; aucune cause sans phrase ; « Par Bluetooth » pour un petit fichier est **VERT**.
4. `WdPolicy` : `enum Use { CONTROL, SYNC, BULK, CAST, REMOTE, QUIZ }`, `data class Facts(lanAlive, isolated, btConnected, api, permission, phoneWifiOn, tvOffersWd, tvWdError, wdKnown, wdUp, backoffUntil, trialTv, foreground, bytes, castKind, now)`, `sealed class Route { UseLan(base), UseWd(base, how: DIRECT|VIA_BT), UseBt(why), Wait(why), AskOnce(what), NoRoute(text) }` ; `LinkPlanner2.plan(info, session, wdUp)`.
5. `WdSession` : `State { Off, Known, Requesting, Joining, Probing, Up(base, uses: Set<Use>, lastUse), Lost, Failed }`, `Event` (ceux de `WdClient` + `Trigger`, `UseBegan(use)`, `UseEnded(use)`, `Background`, `Foreground`, `SwitchTo(tvId)`, `Denied`), `Effect` (ceux de `WdClient` + `ForgetCreds`, `Switch`) ; `reduce` délègue à `WdClient.reduce` pour les sous-états communs ; constantes `IDLE_LEAVE_MS = 120_000`, `LOST_RETRY_IMMEDIATE = 1`.
6. `WdTriggers.shouldJoin(trigger, facts)` : table § 3.2 (fond ⇒ jamais ; découverte DNS-SD ⇒ fenêtres 10 s / 30 s / 5 min max).
7. **Vert** : porte ; `:core:test` complet ; `AutoWifiDirectTest` inchangé et vert.

## Critères d'acceptation
Porte verte ; ≥ 35 tests rouges puis verts ; `grep -n "192.168.49.1" android/core/src/main/kotlin/castbridge/core/link/WdPolicy.kt` ne renvoie que la ligne qui exige `wdUp` ; `grep -rn "System.currentTimeMillis\|Thread.sleep" android/core/src/main/kotlin/castbridge/core/link/WdSession.kt` vide ; aucune modification hors zone.

## Cas limites
LAN qui répond **et** WD `Up` (WD quitté après 2 min : LAN gagne) ; téléphone sur Wi-Fi étranger (`lanAlive=false`, pas `isolated`) ⇒ WD ; `isolated` (W7 § 4.3) ⇒ WD ; identifiants connus mais Wi-Fi du téléphone éteint ⇒ `AskOnce(PHONE_WIFI)` puis BT ; deux TV, l'une `Up` et une copie vers l'autre ⇒ `Wait(SWITCHING)` ≤ 15 s puis bascule si la file de A est vide, sinon BT pour B (jamais interrompre une copie) ; TV R-14 (groupe frais, sans `wd.persist`) ⇒ `VIA_BT` à chaque fois.

## À ne pas faire
Ne pas réécrire `WdClient` ni `BulkRoute` (envelopper) ; aucune agrégation de voies (W8 abandonné) ; pas de texte hors `WdLine` ; pas de décision dans un écran ; ne pas toucher `LinkDriver`.

## Rapport
`STATUT`, sorties rouge/vert, la table de `WdPolicy` recopiée, les constantes de cycle de vie, ce que w18-08 doit câbler (liste d'effets), question au propriétaire : départ après **2 min** sans usage en fond (recommandé) ou tenir tant que l'app vit ?
