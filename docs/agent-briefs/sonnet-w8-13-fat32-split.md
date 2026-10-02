# w8-13 — CastBridge-TV (option D-W8-5) : fichiers > 4 Gio sur FAT32 par découpage transparent (`SplitFile`)

**Vague 8b · Effort L (≈ 3 j) · Modèle : sonnet · Statut OPTION (lancer seulement sur décision D-W8-5 ; après w8-10).** Conception : § 10 (séquence FAT32), 12.3. Branche `claude/sonnet-w8-13`. Rapport : `docs/agent-reports/sonnet-w8-13.md`.

## Objectif
Qu'un film de 6 Gio puisse être envoyé sur une clé FAT32 (limite 4 Gio − 1 octet) et **lu comme un seul fichier** par la TV, sans demander de reformater la clé. Alternative gratuite (recommandée en premier) : le message existant invite à formater en exFAT.

## Pourquoi (preuves)
- `Fs.FAT32.maxFileBytes = 4 Gio − 1` et refus `"file too large for FAT32"` (`core/tv/Volumes.kt:21, 407`).
- `/stream` et le lecteur lisent un `File` unique (`ReceiverServer.kt:1130-1163`) ; la bibliothèque liste par fichier (`core/tv/Library.kt`, `LibraryStore.kt`).

## Fichiers possédés
Nouveaux `C/tv/SplitFile.kt`, `CT/SplitFileTest.kt` ; `C/tv/{Storage,Library,LibraryStore}.kt` (listage, taille, suppression, renommage d'un dossier `.cbxsplit`). **Hors zone** : `ReceiverServer.kt` (w8-10 : demander l'accroche `begin → 413 split` et `/stream` sur `SplitFile` par le rapport ; si 8b est fusionné, ce cahier peut recevoir `ReceiverServer.kt` **par décision explicite de l'index**), `sender/**` (le téléphone envoie `N` transferts nommés `name.cbxsplit/000N.bin` : w8-14 ou un cahier 8c ultérieur, à nommer dans le rapport).

## Étapes
1. `SplitFile` : dossier `<name>.cbxsplit/` avec `index.json` (`{"name","size","parts":[{"file":"0000.bin","size":…},…],"version":1}`) ; `open(dir, name): SplitFile?` ; `size`, `read(pos, buf, off, len)` (positionne dans la bonne partie, traverse les frontières), `inputStream(start, end)` ; `isComplete` (toutes les parties à la taille annoncée) ; `delete()`, `rename()` ; `partSizeMax = 4 Gio − 1 Mio`.
2. `Storage`/`Library`/`LibraryStore` : un dossier `.cbxsplit` complet est listé **une fois** comme un fichier de `size` ; supprimé/renommé comme un tout ; jamais listé comme dossier ; un dossier incomplet est un « envoi en cours » (comme un `.part`).
3. Tests : lecture à cheval sur 2 et 3 parties, `Range` au dernier octet, index corrompu (ignoré, pas de plantage), suppression, listage.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.SplitFileTest' --tests 'castbridge.core.LibraryTest*' --tests 'castbridge.core.StorageTest*'   # vert (noms existants à vérifier)
```

## Cas limites
Clé FAT32 retirée entre deux parties ; même nom déjà présent en fichier simple ; déplacement d'un `.cbxsplit` vers un volume exFAT (fusion en un fichier : **hors périmètre**, dire que la copie reste découpée).

## À ne pas faire
Ne pas lancer sans D-W8-5 ; ne pas modifier le lecteur (`PlayerActivity`) : il lit par `/stream` ; pas de format autre que `index.json` + `.bin`.

## Rapport
`STATUT`, accroches demandées (`ReceiverServer`, téléphone), tests.
