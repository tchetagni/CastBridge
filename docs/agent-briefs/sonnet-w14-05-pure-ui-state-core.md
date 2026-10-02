# w14-05 — Cœur : les décisions d'état des écrans deviennent des fonctions pures (`HomeLinkView`, `ManualTvView`, `PairScreenView`, `ActivateTargetView`, `XferTexts`, `ReceiveCard`, `PinKeys`)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (ces fonctions deviennent la vérité de la puce, des textes de notification et des clés de PIN) · statut : PRÊT
> **Groupe : W14-a** (vague W14, tranche S1) · prérequis : aucun (parallèle à w14-01 ; fichiers disjoints) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.trust.HomeLinkViewTest' --tests 'castbridge.core.trust.PinKeysTest' --tests 'castbridge.core.xfer.XferTextsTest' --tests 'castbridge.core.xfer.ReceiveCardTest'`
> **Jauge : ≈ 450 k jetons entrée / 28 k sortie** (effort M+) · audit Opus : oui

**Vague 14a (cœur) · Effort M+ (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 3.3 (table décision → fonction), § 3.4 (règle de pureté). Modèle à suivre : `C/trust/SendChoice.kt` (`SendFacts` → `SendChoice`, table-testé). Branche `claude/sonnet-w14-05`. Rapport : `docs/agent-reports/sonnet-w14-05.md`.

## Objectif
Créer dans le cœur, **sans toucher aux écrans** (w14-06 les câble), les fonctions pures qui remplacent chaque décision d'état prise aujourd'hui dans `:sender`/`:receiver`, avec le **même comportement** qu'aujourd'hui (extraction à l'identique, défauts compris : un défaut connu est reproduit et marqué `// REGRESSION R-0x : comportement actuel, corrigé par <cahier>`), chacune table-testée.

## Pourquoi (preuves)
- `S/TvHome.kt:137-197` : `reachable = r.isSuccess`, textes « Connectée » / « Recherche de la TV… », `wizard = true` sur 401 (F2 de W13 : `msg` jamais rendu).
- `S/TvScreen.kt:61,113-115,236` : `reachable` vrai par défaut, `badPin`, « TV injoignable — … ».
- `S/TvPairScreen.kt:65-66,93,294` : `when (link) { LinkUi.NoTv -> idleView … }`, « Aucune TV ajoutée. » sur `tvs.isEmpty()`.
- `S/ActivateTvActivity.kt:63,95` : « Aucune TV n'est ajoutée dans CastBridge… » sur `LinkUi.NoTv` (ignore le chemin PIN : même famille que R-02, erratum de `DESIGN-W13`).
- Notifications : `"$text · $pct %"` dans `S/UploadService.kt:272`, `S/TransferQueueService.kt:59`, `S/BtUploadService.kt:179`, `S/DownloadService.kt:154` ; `Failed ⇒ return` puis `stopSelf()` (`UploadService.kt:255-265`, F1).
- TV : `R/BtServer.kt:113-127` statut `1-bt` écrasé (R-04) ; `S/PinStore.kt:22` et `S/TvLink.kt:191-198` (clés de PIN : nom, mDNS, `bt:<adr>`, `ip:port` ; `ip` sans port rate).

## Fichiers possédés
Nouveaux : `C/trust/HomeLinkView.kt`, `C/trust/ManualTvView.kt`, `C/trust/PairScreenView.kt`, `C/trust/ActivateTargetView.kt`, `C/trust/PinKeys.kt`, `C/xfer/XferTexts.kt`, `C/xfer/ReceiveCard.kt`, `CT/trust/{HomeLinkViewTest,ManualTvViewTest,PairScreenViewTest,ActivateTargetViewTest,PinKeysTest}.kt`, `CT/xfer/{XferTextsTest,ReceiveCardTest}.kt`. **Hors zone** : `S/**`, `R/**`, `C/trust/SendChoice.kt` (lecture : réutiliser `LinkView`, `LinkStart`, `PinCheck`), `CT/journey/**` (w14-01).

## Signatures (contrat pour w14-06, w14-11, W13 w13-07/08/09)
```kotlin
package castbridge.core.trust
data class HomeFacts(val savedCount: Int, val defaultName: String?, val stepView: LinkView?, val pinTvName: String?, val pinStored: Boolean,
    val lastInfoOkAt: Long?, val lastInfoStatus: Int?, val nowMs: Long, val freshMs: Long = 10_000)
data class HomeView(val view: LinkView, val showWizard: Boolean, val wizardReason: String?)   // wizardReason = le « msg » de TvHome.kt:132, désormais rendu
object HomeLinkView { fun decide(f: HomeFacts): HomeView }   // règle : un état non observé = « vérification » (LinkStart.checking), jamais « Connectée » sur mémoire > freshMs
data class ManualTvFacts(val base: String?, val pinStored: Boolean, val lastStatus: Int?, val lastOkAt: Long?, val nowMs: Long)
data class ManualTvView(val reachableText: String?, val pinError: String?, val canSend: Boolean)
object ManualTvViews { fun decide(f: ManualTvFacts): ManualTvView }
object PairScreenView { fun decide(savedCount: Int, defaultName: String?, stepView: LinkView?, trustedList: List<String>): LinkView }  // délègue à LinkStart.view ; « Aucune TV ajoutée. » seulement si savedCount == 0
object ActivateTargetView { data class Target(val name: String, val base: String?, val viaPin: Boolean) ; fun decide(savedCount: Int, defaultName: String?, stepView: LinkView?, pinTvName: String?, pinBase: String?): Pair<Target?, String?> }  // (cible, message) ; jamais « Aucune TV » si pinTvName != null
object PinKeys { fun keysOf(name: String?, mdns: String?, btAddress: String?, host: String?, port: Int?): List<String> ; fun normalize(key: String): String }  // "ip" ⇒ "ip:8765"
package castbridge.core.xfer
sealed class XferState { data class Uploading(val name: String, val sent: Long, val total: Long, val via: String?) ; data class Waiting(val name: String, val reason: String) ; data class Done(val name: String) ; data class Failed(val name: String, val reason: String) ; data class Cancelled(val name: String) }
data class Notice(val title: String, val text: String, val progress: Int?, val final: Boolean, val ongoing: Boolean)
object XferTexts { fun notification(s: XferState): Notice }   // "$name · $pct %" conservé ; Failed/Cancelled ⇒ final=true avec texte (jamais null)
data class ReceiveCard(val name: String, val received: Long, val total: Long, val via: String, val state: String)
object ReceiveCards { fun of(transfersJson: String, btStatusLine: String?): List<ReceiveCard> ; fun headline(cards: List<ReceiveCard>): String }  // « Prêt à recevoir » seulement si vide
```

## Étapes
1. Lire chaque site cité, extraire la décision **à l'identique** (y compris les textes français actuels, source unique dans la fonction), remplacer les lectures Android par des champs de faits.
2. `HomeLinkView` : reproduire `TvHome.kt:106-137,174-178` ; différence **voulue** : `wizardReason` porte le message (F2) ; état non frais ⇒ `LinkStart.checking`.
3. `XferTexts` : reproduire les 4 textes ; `Failed`/`Cancelled` produisent une `Notice` finale (F1 corrigée par construction : le service **doit** l'afficher, w14-06).
4. `ReceiveCards.of` : parse `TransferHost.stateJson` (voir le format dans `C/xfer/TransferHost.kt`) + la ligne `1-bt` actuelle ⇒ liste ; `headline` pour `HomeScreen`.
5. `PinKeys` : union des clés de `S/TvLink.kt:191-198` + `S/PinStore.kt` + `S/TvScreen.kt:77` (IP sans port) ; `normalize` ajoute `:8765`.
6. Tests de table : ≥ 10 lignes par fonction ; pour `HomeLinkView` inclure les 8 états de J-19 (w14-04) ; `XferTextsTest` : chaque `XferState` ⇒ texte non vide, `final` juste.

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/trust/ android/core/src/main/kotlin/castbridge/core/xfer/ | wc -l` ⇒ 0 ; `grep -c 'fun decide\|fun notification\|fun of' <les 7 fichiers>` ≥ 7 ; `:core:test` complet vert ; aucun écran modifié (`git status` ne liste que les fichiers possédés).

## Cas limites
`LinkView`/`LinkState` sont dans `C/trust/LinkMachine.kt` : réutiliser, ne pas dupliquer ; `TvHome` a deux chemins (confiance et code) : `HomeFacts` doit porter les deux ; `DownloadService` (téléchargements, pas transferts) : même `Notice`, état `Uploading` renommé via `via = "téléchargement"`, ou sous-type dédié si plus clair (le dire).

## À ne pas faire
Aucune modification de `S/**`/`R/**` ; aucun nouveau texte utilisateur (extraction) sauf `wizardReason` ; aucune dépendance ; ne pas absorber `LinkUi` (façade Android de `S/TvLink.kt`).

## Rapport
`STATUT`, table site Android → fonction → test, défauts reproduits volontairement (avec `R-0x`), questions pour l'audit Opus (fraîcheur `freshMs`, clés de PIN normalisées).
