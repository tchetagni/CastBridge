# w8-01 — Cœur : voies dynamiques (`LaneSet`), ajout/retrait à chaud, pause/reprise

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT
> **Groupe : W8a-1** (vague W8a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LaneSet*' --tests '*SchedulerDynamic*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 8a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W8-TRANSPORT-MULTIVOIE.md` § 4.1, 4.2, 4.3, 4.8, 8.1. Branche `claude/sonnet-w8-01`. Rapport : `docs/agent-reports/sonnet-w8-01.md`. Règles communes : `SONNET-WAVE8-INDEX.md`.

## Objectif
Lever la limite principale du moteur actuel : la liste des voies est **figée** au constructeur de `Scheduler` (`core/xfer/Scheduler.kt:12-23`, threads créés une fois à `run()`, lignes 60-62). Après ce cahier, une voie peut **rejoindre** ou **quitter** un transfert en cours sans perte ni blocage, et un transfert peut être mis en pause et repris.

## Pourquoi (preuves)
- `Scheduler(manifest, done, lanes: List<Lane>, …)` et `for (lane in lanes) for (w in 0 until lane.maxWorkers) threads += Thread(…)` (`Scheduler.kt:12-15, 61-62`).
- `Lane.slow` booléen (`Lane.kt:70`) : remplacé par une **unité d'envoi** en octets (§ 4.3), la voie lente prenant des tranches de 256 Kio (`PartAssembler.writeSlice`, existant).
- `UploadService.runFast` ne construit qu'une `WifiLane` (`sender/UploadService.kt:196`) : la vague 8c en ajoutera à chaud.

## Fichiers possédés
`C/xfer/{Lane,Scheduler}.kt`, nouveau `C/xfer/LaneSet.kt`, nouveaux `CT/xfer/{LaneSetTest,SchedulerDynamicTest}.kt` (créer le dossier `CT/xfer/`). **Hors zone** : `Lanes.kt` (w8-02), `PartAssembler.kt`/`TransferHost.kt` (w8-08), `TransferClient.kt` (w8-07), `Blocks.kt` (w8-04), tout fichier Android.

## Étapes
1. `Lane` : ajouter `val kind: LaneKind` (`WIFI, DIRECT, BT, USB, FAKE`), `var laneId: Int` (0 = non attribué ; attribué par la TV, w8-08), `fun unitBytes(): Int` (défaut `maxBlock`), `fun idle(on: Boolean)` (défaut no-op), conserver `send`, `sent`, `maxWorkers`, `allowedWorkers`, `close`. Supprimer `slow` **après** avoir remplacé ses deux usages (`Scheduler.take`, `Lanes.kt` : laisser dans `Lanes.kt` un `override fun unitBytes() = Manifest.SLICE` pour `BluetoothLane` sans toucher au reste du fichier ; si l'édition de `Lanes.kt` dépasse cette ligne, la laisser à w8-02 et garder `slow` en `@Deprecated` jusqu'à la fusion). Supprimer `UsbLane` (interface vide, `Lane.kt:83-85`) ; garder `LaneSwitches`.
2. `LaneSet` : `add(lane)`, `remove(id: String, drain: Boolean)`, `snapshot(): List<Lane>`, `onChange(listener: (Change) -> Unit)` ; thread-safe ; un identifiant déjà présent est refusé (`IllegalArgumentException`).
3. `Scheduler` : constructeur sur `LaneSet` ; `run()` écoute `onChange` : voie ajoutée → ouvriers démarrés (`xfer-<id>-<n>`) ; retirée avec `drain=true` → les ouvriers finissent l'unité en cours puis sortent ; `drain=false` → `abort` immédiat des `Run` de cette voie, blocs **requeués devant** (`pending.addFirst`). `take(lane)` : une voie dont `unitBytes() < blockSize` prend des **tranches** (index de tranche, comme `BluetoothLane` aujourd'hui) **par la fin** de la file en mode vrac ; sinon des blocs par le début. L'ordre devient pluggable (`OrderPolicy`, w8-03) : exposer `protected open fun pick(lane): Int?` que w8-03 remplacera.
4. `pause()`/`resume()` : les ouvriers ne prennent plus rien, finissent l'unité en cours ; `run()` ne rend pas la main en pause ; `cancelled()` reste prioritaire.
5. Quand **toutes** les voies sont retirées, `run()` attend (`listener.waiting("aucune voie")`) jusqu'à un `add` ou l'annulation : jamais `Failed`.
6. Tests JVM (fausses voies **locales au test**, en attendant `FakeLane` de w8-06 : une voie en mémoire qui copie dans un `PartAssembler` ou un tableau) : ajout à 30 % du transfert, retrait propre à 60 %, retrait brutal avec blocs en vol (tous renvoyés, aucun doublon perdu), toutes les voies retirées puis une rajoutée, pause 2 s puis reprise, identifiant en double refusé, 2 voies × 3 ouvriers sans interblocage (test borné 10 s), fichier de 0 octet, dernier bloc court. Les tests existants de `CT/MultipathTransferTest.kt` qui construisent `Scheduler(…, lanes = listOf(…))` : fournir un constructeur secondaire `Scheduler(…, lanes: List<Lane>, …)` qui enveloppe dans un `LaneSet` pour **ne pas** éditer ce fichier (il appartient à w8-08 pour l'adaptation).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.LaneSetTest' --tests 'castbridge.core.xfer.SchedulerDynamicTest'   # vert, ≥ 10 cas
cd android && gradle --offline :core:test --tests 'castbridge.core.MultipathTransferTest' --tests 'castbridge.core.MultipathServerTest'    # toujours vert (33 + 18 cas)
grep -n "slow" android/core/src/main/kotlin/castbridge/core/xfer/Lane.kt | wc -l   # 0 (ou 1 ligne @Deprecated si Lanes.kt est laissé à w8-02 : le dire)
```

## Cas limites
Retrait d'une voie pendant qu'elle envoie le **dernier** bloc (doublon de fin déjà en cours sur une autre voie : le premier arrivé gagne, l'autre `Cancelled`) ; `add` après `Done` (ignoré, voie fermée) ; `remove` d'une voie mise à l'écart (`benchedUntil`) ; ouvriers `allowedWorkers()` qui diminue en cours de route (existant : ils s'endorment).

## À ne pas faire
Ne pas réécrire le vol de travail, le doublon de fin ni la mise à l'écart (`Scheduler.kt:96-159`) : les **conserver** ; ne pas toucher à `PartAssembler`/`TransferHost` ; aucun `Thread.sleep` non injecté dans les tests ; pas de dépendance nouvelle.

## Rapport
`STATUT`, signature finale de `Lane` et `LaneSet` (copiée), ce qui reste dans `Lanes.kt` pour w8-02, tests avant/après.
