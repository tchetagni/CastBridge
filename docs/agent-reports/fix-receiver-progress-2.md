# fix-receiver-progress-2 (audit Opus de R-04)

Branche `claude/fix-receiver-progress-2` (depuis integration/agents, fusionné avec w14-05).

1. MAJEUR sweep : `TvService.sweepTick` (Handler principal, 30 s tant que `reception.active()` n'est pas vide, armé par le premier événement en cours, s'arrête quand vide) ; `onDestroy` retire l'écouteur et annule toutes les notifications 5000+.
2. `transferChunk` : `progress.begin(...)` si `!isRunning(pid)` (même seq). Test `chunksAfterTheSilenceSweepMakeTheCopyVisibleAgain` (horloge injectée via nouveau paramètre `progress` de `ReceiverServer` / `Rig`).
3. Bluetooth sans serveur HTTP : `TvService.reception` (TransferProgress) appartient au service, passé à `ReceiverServer(progress=)` et à `BtServer`; `PlayerActivity.status()` lit `svc.reception`.
4. Notification : canal créé dans `onCreate`, publication sur le looper principal, un item plus ancien que le dernier affiché pour l'id est ignoré.
5. `ReceiverServer` : `fileReceived` encadré (IOException -> non rangé mais `finish`), flux clos trop court -> `interrupted` tout de suite (test `aShortUploadIsMarkedWaitingAtOnce`).
6. Porte W14 : `ReceiveCards.of(items, partials, serverUp)` / `ready` / `headline` (pures, `core/xfer/ReceiveCard.kt`, source `TransferProgress.shown()`, ligne `1-bt` supprimée, titre lisible). `ReceiveCardTest` réécrit ; `r04_...` écrit d'abord : sortie ROUGE obtenue contre une implémentation neutralisée (4 échecs sur 4 dont r04), puis verte. Assertion `||` de `TransferProgressTest` remplacée par une égalité exacte.

Risques : un PUT partiel légitime affiche « reprise en attente » entre deux morceaux (effacé au suivant) ; « Démarrage… » reste si le serveur HTTP ne démarre pas (le Bluetooth s'affiche quand même). Non vérifié sur appareil.
