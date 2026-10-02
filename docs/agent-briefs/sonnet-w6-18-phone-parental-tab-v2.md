# w6-18 — CastBridge (téléphone) : onglet Parental v2 (Aujourd'hui / Semaine, « Toute la TV », parents de cette TV, confidentialité), tirage Wi-Fi, mur `PARENTAL_*`

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (contrat w6-16)
> **Groupe : W6d-1** (vague W6d) · prérequis : w6-01, w6-02, w6-15 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 6d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après 6a, w6-15 fusionnés ; w6-16 en parallèle : contrat `GateWall`/`PhoneGateRuntime`).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.7, § 2.8, § 3.7 (lignes parental), § 3.9. Branche `claude/sonnet-w6-18`. Rapport : `docs/agent-reports/sonnet-w6-18.md`.

## Objectif
(1) `ParentalInbox` : second transport **Wi-Fi** (`HolderHttp` routes de w6-15, avec le jeton de la `LinkSession`) quand la TV est joignable en Wi-Fi, Bluetooth sinon ; stockage de `installPub` (clé épinglée des rapports, `ReportInbox.setInstallKey`) ; `consent` affiché ; (2) `ParentalTab` : sections réorganisées § 2.8 : **Tableau de bord** (bascule Aujourd'hui / Cette semaine, par profil et « Toute la TV », cartes ●/◐/○, top 3 avec mini-carte horaire, détenteurs), **Toute la TV** (remplace « Par application » : minutes, lancements, carte horaire / jour × heure, nouvelles/retirées, connexions, « État en direct » avec mention « consulté, visible sur la TV »), Apprendre et Quiz (+ Sudoku, jetons), Rapports, Exports (colonnes v2), **Alertes** (+ détenteurs, `late_use`), **Parents de cette TV** (nouvelle), **Confidentialité** (nouvelle), Règles de la TV (+ `shareTitles`, `lateUseAlert`) ; (3) mur : `GateWall(PARENTAL_DASHBOARD)` (colonne G : **lecture seule** de la copie locale, comme l'existant « TV hors de portée ») et `GateWall(PARENTAL_RULES)` ; la **réception en fond continue** quel que soit l'état ; (4) `ParentalWholeTv.kt` conservé pour les TV v1 (section « ancien format ») ; (5) accessibilité : alternative textuelle des cartes (`Heatmap.textAlternative`).

## Pourquoi (preuves)
- `S/ParentalTab.kt:144` (`SECTIONS`), `:147-192` (`ParentalHome`), `:254-283` (`PullButton`, `pullReports`), `:293-336` (`DashboardSection`), `:114` (`DeviceLockEntry` lecture seule) ; `S/ParentalInbox.kt:72-90` (`sync` Bluetooth) ; `S/ParentalReportsUi.kt`, `S/ParentalCharts.kt` (Canvas : barres, anneau, carte de chaleur : **réutiliser**), `S/ParentalExport.kt`, `S/ParentalData.kt`, `S/ParentalWholeTv.kt`, `S/ParentalScreen.kt` (règles) ; `C/parental/tab/**` (w6-08 : `WholeTvSummary`, `Heatmap.perApp`), `C/parental/HolderHttp.kt` (w6-15), `C/parental/ParentalPrivacy.kt` (w6-06) ; PARENTAL.md § « Onglet Parental ».

## Fichiers possédés
`S/Parental{Tab,Inbox,ReportsUi,Charts,Export,Data,WholeTv,Screen,Activity}.kt`, nouveaux `S/ParentalHolders.kt`, `S/ParentalPrivacyScreen.kt`, `S/ParentalWholeTvV2.kt`. **Hors zone** : `S/MainActivity.kt`, `S/gate/**` (w6-16), les fichiers de w6-17, `OL/**` (w6-19), `C/**`.

## Étapes
1. `ParentalInbox.sync` : stratégie Wi-Fi d'abord si `LinkUi.Connected.session.base != null` (`HolderHttp` client : `hello` → `pull` → `ack`), sinon Bluetooth (`ReportSync.run`) ; les deux absorbent par `ParentalLedger.absorb` ; `designated`, `consent`, `installKeyStored` persistés ; tâche périodique inchangée (15 min, seulement si désigné).
2. Tableau de bord : composables sur `WholeTvSummary` ; chaque chiffre avec son mot et son symbole ; « au moins X min » quand ◐ manque ; bascule période.
3. « Toute la TV » : liste d'applications avec mini-carte (24 cases, `Canvas` existant), détail au toucher (carte jour × heure + alternative textuelle), connexions, « État en direct » (bouton → `snapshot` ; texte « Cette consultation est visible sur la TV »).
4. « Parents de cette TV » : liste des détenteurs, « Désigner ce téléphone » (code parental ; puis état « En attente : validez sur la TV » avec rafraîchissement), « Ne plus recevoir », texte du consentement.
5. « Confidentialité » : `COLLECTED`/`NEVER`, « aucun serveur », `shareTitles` par profil, rétention, purge (code redemandé).
6. Murs : `GateWall(PARENTAL_DASHBOARD)` en haut de l'onglet ; en colonne G, contenu en lecture seule ; `GateWall(PARENTAL_RULES)` sur « Règles de la TV ».
7. Captures → `docs/img/parental/w6-phone-*.png`.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -n '"Toute la TV"\|"Parents de cette TV"\|"Confidentialité"' android/sender/src/main/kotlin/castbridge/sender/ParentalTab.kt   # 3 sections
grep -rn 'holder/pull' android/sender/src/main/kotlin/castbridge/sender/ParentalInbox.kt   # ≥ 1
grep -rn 'TvConnect\|DeviceClient\|/api/v1/' android/sender/src/main/kotlin/castbridge/sender/Parental*.kt   # 0 hit (aucun serveur)
```
Observable : TV en Wi-Fi ⇒ « Recevoir maintenant » tire par Wi-Fi (journal : `via=wifi`) ; désignation depuis le téléphone ⇒ « En attente : validez sur la TV » ; après validation ⇒ rapports ; tableau de bord « Aujourd'hui » montre écran allumé, applications avec mini-cartes, connexions ; un jour sans rapport est un trou.

## Cas limites
TV v1 (sans v2) : sections v2 disent « non disponible sur cette version de CastBridge-TV » ; plusieurs TV : sélecteur existant (« Données ») ; téléphone non désigné : onglet en lecture des anciennes données + explication ; `consent == pending` depuis > 7 j ⇒ « désignation expirée : recommencez ».

## À ne pas faire
Aucun envoi réseau hors TV ; pas de saisie du code parental ailleurs que par la vérification TV existante ; pas de zéro à la place d'un trou ; ne pas toucher `MainActivity.kt`.

## Rapport
`STATUT`, captures, ce qui reste v1.
