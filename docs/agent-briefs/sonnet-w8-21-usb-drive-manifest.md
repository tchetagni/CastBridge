# w8-21 — Option : manifeste `.cbx` sur la clé USB (hybride clé + réseau), import TV qui reprend par carte de blocs

**Vague 8d · Effort M (≈ 2 j) · Modèle : sonnet · Statut OPTION (lancer sur décision du coordinateur ; après w8-08).** Conception : § 2 (ligne « clé USB physique »), § 10. Branche `claude/sonnet-w8-21`. Rapport : `docs/agent-reports/sonnet-w8-21.md`.

## Objectif
Qu'un fichier **commencé** par le réseau puisse être **fini** par une clé USB (et inversement) : le téléphone écrit sur la clé, à côté du fichier, le manifeste du transfert (taille de bloc, empreintes) ; la TV, à l'import, vérifie bloc par bloc et complète la copie partielle `.cbx/<id>.data` au lieu de repartir de zéro. Pas une voie temps réel : un **pont** entre les deux chemins existants.

## Pourquoi (preuves)
- Import TV par nom + taille, `.part` repris en séquentiel (`core/tv/UsbImport.kt:51-96`) ; carte de blocs persistante du transfert réseau (`PartAssembler.kt:222-231, 246-252`) ; les deux ignorent l'autre.
- Le téléphone sait déjà écrire sur une clé OTG (déplacement de bibliothèque, `S/StoragePanel.kt`).

## Fichiers possédés
Nouveaux `C/tv/UsbManifest.kt`, `CT/UsbManifestTest.kt` ; `C/tv/UsbImport.kt`, `R/UsbImporter.kt`, `S/StoragePanel.kt`. **Hors zone** : `PartAssembler`/`TransferHost` (8a : utiliser `PartAssembler.open` et `writeBlock` depuis un `FileInputStream`), `ReceiverServer.kt`.

## Étapes
1. `UsbManifest` : fichier `<name>.cbx.json` (`{"version":1,"name","size","blockSize","hashes":[…]}`) écrit par le téléphone (`HashBook.all()`) à côté du fichier copié sur la clé ; lecture/validation stricte côté TV.
2. `UsbImport.copyAll` : si un `.cbx.json` existe **et** qu'un `.cbx/<id>.data` partiel existe sur le volume cible (même `Manifest.id`), alors `PartAssembler.open` + `writeBlock` pour chaque bloc manquant lu depuis la clé (vérifié par empreinte) + `finish` (relecture) → `.part` → `commit` (règles existantes) ; sinon chemin actuel. Un fichier **complet** sur la clé avec manifeste : import bloc par bloc vérifié (détecte une clé défaillante).
3. `StoragePanel` (téléphone) : à la copie sur une clé, option « Écrire le manifeste de reprise » (défaut activé si le fichier > 64 Mio ; coût : lecture complète pour les empreintes).
4. Tests : copie partielle réseau (6/10 blocs) + clé complète → import des 4 blocs manquants seulement ; clé avec un bloc abîmé → ce bloc seul est ignoré et signalé ; manifeste absent → chemin actuel inchangé (tests existants `UsbImportTest` verts).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.UsbManifestTest' --tests 'castbridge.core.UsbImportTest'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :sender:compileDebugKotlin
```

## Cas limites
Manifeste d'une autre taille de bloc que la copie partielle (identifiants différents : repartir du chemin classique) ; clé FAT32 et fichier > 4 Gio (hors périmètre, w8-13) ; essai (import USB fermé : `TrialPolicy.USB_MESSAGE`, inchangé).

## À ne pas faire
Ne pas lancer sans décision ; ne pas modifier le format `.state` ; ne pas écrire le manifeste sans l'accord de l'utilisateur (option visible).

## Rapport
`STATUT`, format du manifeste, tests, compilation.
