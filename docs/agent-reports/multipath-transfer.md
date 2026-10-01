STATUT: TERMINÉ

Livré : moteur multivoie `core/xfer` (blocs, `.part` préalloué caché, SHA-256, reprise, relecture + renommage atomique ; `WifiLane` K adaptatif 1-8, `BluetoothLane` par tranches, `WifiDirectLane` désactivée, `UsbLane` interface + pistes dans docs/TRANSFER.md ; ordonnanceur vol de travail / doublon de fin / voie morte), API `/api/transfer/*` côté TV (repli ancien envoi, contre-pression sur la vitesse d'écriture mesurée, note française si le disque borne), banc `tools/transfer-bench` (réseau seul / réseau + disque, `--simulate`), réglage « Transfert rapide » dans `UploadService`/`TvScreen`, docs `TRANSFER.md` + `HANDOFF.md`.
Tests : `:core:test` avant 1272 tests / 10 échecs (LearnLotsTest, déjà rouges) ; après 1323 tests / mêmes 10 échecs, 51 tests neufs verts.
À valider sur matériel : compiler `:sender` (UploadService, TvScreen, FastTransfer : NON compilés), lancer le banc sur la vraie TV (aucune mesure réelle faite), envoi réel 4+ Go, clé USB retirée en cours de copie. Non fait : BluetoothLane dans l'app, volumes SAF, lecture pendant l'envoi.
Branche `claude/multipath-transfer`, dernier commit 9a431d9.

## Jalons
- 2026-10-01 14:25 | départ : cahier lu, branche créée depuis integration/agents | commit (à venir)
- 2026-10-01 14:39 | moteur multivoie (core/xfer), API /api/transfer côté TV, tests JVM verts | commit voir git log
- 2026-10-01 14:50 | banc, intégration Android, docs, suite complète | commit 9a431d9
