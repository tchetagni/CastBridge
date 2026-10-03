# w19-06 — Cœur : état de synchronisation partagé : `Epochs` (compteurs + `bootId`), `SyncState` JSON, route additive `GET /api/sync/state` (classe d'extension), miroir `PhoneSyncState`, `Coherence.compare` (carte « Cohérence » vert/orange/rouge/noir avec la divergence exacte en français), `SignalAgreement` (même mot, même couleur pour le même état des deux côtés)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon (aucun secret dans l'état ; route ouverte en essai) · statut : PRÊT (après w19-01)
> **Groupe : W19-S2** (cohérence) · prérequis : w19-01 fusionné (`RejectionLedger.json()`) ; w19-07 **non requis** (coder contre une interface locale `LibraryRootSource`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.sync.*' --tests 'castbridge.core.ux.*' --tests 'castbridge.core.*ReceiverServer*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L, ≈ 2 j) · audit Opus : échantillon

**Vague 19 · Effort L · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W19 § 1.1 (S-3, S-11, S-12), § 1.3 (table des autorités), § 3.4 (JSON, niveaux, ordre de vérité, rythme), § 3.5.5 (états TV ↔ téléphone), D-W19-4, D-W19-9. Branche `claude/sonnet-w19-06`. Rapport : `docs/agent-reports/sonnet-w19-06.md`. Règle W15 R5 : aucun `S/`, `R/`. Règle R3 : **ne pas éditer** `ReceiverServer.kt` au-delà d'une ligne d'enregistrement de l'extension.

## Objectif
(1) `C/sync/Epochs.kt` : compteurs monotones persistés (`library`, `trust`, `edition`, `queue`), hachages (`filing`, `lots`, `libraryRoot` fournis par des sources injectées), `bootId` (aléatoire par processus), `mono` ; écriture `SafeFile` (W15) ; relecture tolérante. (2) `C/sync/SyncState.kt` : `TvSyncState.json()` selon DESIGN § 3.4 (`protocol`, `appVersion`, `versionCode`, `id`, `mono`, `bootId`, `epochs`, `libraryRoot`, `filing{version,on,lang}`, `transfers[]` depuis `TransferHost` avec `slowed` et `code`, `rejections[]` depuis `RejectionLedger`, `edition`, `peers[]` depuis `X-CB-App` vus) ; **aucun secret, aucune adresse complète** (`Redact.scrub` sur tout le corps en test). (3) `C/sync/SyncRoutes.kt` : `ServerExtension` qui sert `GET /api/sync/state` (PIN ou jeton ; **ouverte en essai** en lecture : à inscrire dans la liste d'`TrialPolicy` par une constante, pas par un trou) ; enregistrée par **une ligne** dans `ReceiverServer` (ou par le constructeur `extension`, déjà prévu `:566`). (4) `C/sync/PhoneSyncState.kt` : miroir par `tvId` (époques vues, `bootId` vu, file locale, lots locaux, caps) ; `Coherence.compare(tv, phone, now): Verdict(level: SignalLevel, lines: List<String>, repairs: List<Repair>)` selon la table § 3.4 (VERT « TV et téléphone concordent » ; ORANGE bibliothèque/rangement/lots/TV ancienne ; ROUGE session orpheline 2 cycles, élément sans session 2 cycles, `trust` changé avec jeton en usage, refus répété non montré ; NOIR non jointe) ; `Repair` ∈ {`RereadLibrary`, `ReplanLots`, `AbortOrphan(id)`, `ResumeBegin(item)`, `Hello`, `MarkFailed(item, code)`} ; **ordre de vérité** : jamais de `Repair` qui modifie le disque de la TV depuis un cache. (5) `C/sync/SyncPace.kt` : rythme pur (15 s LAN/WD fusionné avec le garde-vivant : une requête de plus seulement si `/api/info` a changé, sinon 60 s ; 60 s BT ; fond = job 15 min). (6) `C/ux/SignalAgreement.kt` : table état de transfert ⇒ (mot, `SignalLevel`) **unique** pour `TransferProgress.Item` (TV) et `XferState` (téléphone, w19-13 ; interface locale si non fusionné) ; test : même entrée ⇒ même mot/couleur.

## Pourquoi (preuves)
- `C/tv/ReceiverServer.kt:1347-1357` : `/api/info` = fichiers, volumes, lecteur, `playbackPriority` : **aucune époque**, le téléphone le lit chaque seconde ; aucune route ne dit « quelque chose a changé ».
- `C/xfer/TransferHost.kt:121-124` `slowedNote` ; `C/ux/TvSignal.kt:9-13` niveaux et sévérité ; `C/lots/LotManifest.kt:64-83` charge canonique hachée ; `C/tv/ContentIndex.kt` (R-12) : les sources des hachages existent.
- R-10 (5) : un 401 d'une TV retirait le jeton d'une autre : les faits doivent être cloisonnés par `tvId` (S-12).

## Fichiers possédés
Nouveaux : `C/sync/Epochs.kt`, `C/sync/SyncState.kt`, `C/sync/SyncRoutes.kt`, `C/sync/PhoneSyncState.kt`, `C/sync/Coherence.kt`, `C/sync/SyncPace.kt`, `C/ux/SignalAgreement.kt`, `CT/sync/EpochsTest.kt`, `CT/sync/SyncStateTest.kt` (vraie `ReceiverServer` + extension), `CT/sync/CoherenceTest.kt`, `CT/sync/SyncPaceTest.kt`, `CT/ux/SignalAgreementTest.kt`. Zones additives : `C/tv/ReceiverServer.kt` (**une ligne** : enregistrement), `C/tv/TrialPolicy.kt` (constante de route lisible, ≤ 5 lignes), `C/xfer/TransferHost.kt` (`transfersJson()` pour l'état, ≤ 20 lignes). **Hors zone** : `C/sync/Reason.kt`, `RejectionLedger`, `RetryPolicy`, `Caps`, `HelloV2` (lire seulement), `S/`, `R/`.

## Étapes
1. **Rouge** : `EpochsTest` (incrément, persistance, `bootId` change par instance, fichier corrompu ⇒ compteurs à 0 **et** `bootId` neuf, jamais d'exception) ; `SyncStateTest` (route 200 avec PIN et avec jeton, 401 sans ; essai ⇒ 200 ; corps sans secret ; `transfers[].slowed` reflète `slowedNote` ; `rejections` = registre) ; `CoherenceTest` : chaque ligne de la table § 3.4 ⇒ niveau, phrase française exacte, réparation ; deux TV ⇒ verdicts indépendants ; TV sans `sync` ⇒ ORANGE « TV ancienne : cohérence non vérifiable » ; **ordre de vérité** : un cache qui dit « présent » contre une TV qui dit « absent » ⇒ `RereadLibrary`, jamais `MarkFailed` ; `SyncPaceTest` ; `SignalAgreementTest` (table complète, aucune entrée sans mot).
2. Implémenter ; sources injectées (`LibraryRootSource`, `FilingHashSource`, `LotsHashSource`, `TrustEpochSource`) : interfaces **locales** minimales, nommées dans le rapport (w19-07 et w19-10 les brancheront).
3. **Vert** : porte ; `:core:test` complet.

## Critères d'acceptation
- `GET /api/sync/state` ≤ 2 Kio pour 3 transferts, 32 refus, 2 pairs ; aucune empreinte calculée à la demande (sources en cache).
- Toute phrase de Cohérence est dans `Coherence.kt` ; aucune en dur ailleurs (test de source).
- Un `bootId` différent rend `mono` incomparable : test.

## Cas limites
TV redémarrée entre deux lectures (`bootId`) ; horloge du téléphone en arrière ; 0 transfert ; registre vide ; `peers` sans `X-CB-App` (téléphone ancien) ⇒ `app:"inconnu"`.

## À ne pas faire
Calculer un SHA-256 de fichier dans la route ; ajouter un champ à `/api/info` ; toucher `/api/hello` ; écrire un texte en dehors de `Coherence.kt`/`SignalAgreement.kt` ; modifier le disque de la TV depuis une réparation du téléphone.

## Rapport
RAPPORT + `SYMBIOSE: cap=sync · proto=inchangé (additif) · reason=— · deux écrans=SignalAgreementTest + CoherenceTest`.
