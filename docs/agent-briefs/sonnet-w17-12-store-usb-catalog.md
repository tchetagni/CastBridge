# w17-12 — Catalogue de la boutique par clé USB (décision D-W17-9 passée à « oui », 2026-10-03)

**Modèle · Groupe · Jauge** : sonnet · 17a (cœur, pendant le gel) · ≈ 350 k entrée / 18 k sortie · audit Opus : échantillon (vérification de signature, chemins de fichiers)
**Dépend de** : w17-01 (StoreCatalog), w17-04 (StoreApi, StoreFiles). **Aucun fichier `R/` ni `S/`** (le branchement sur la TV sera une ligne de w17-08, après le gel).

## Pourquoi
Décision du propriétaire : « le mode offline représente 80 % des cas ». La TV ne doit pas dépendre du téléphone pour VOIR la boutique : un catalogue peut arriver par **clé USB** (comme les APK et les lots), sans réseau ni téléphone.

## À livrer (cœur pur, JVM)
1. `C/store/StoreUsbImport.kt` : lit, depuis un dossier donné (la TV fournira `<clé>/CastBridge/store/`), les DEUX documents signés déjà définis par W17 (`castbridge-lot-catalog-v1` feature=* et `castbridge-bundle-catalog-v1`) et les fichiers de lots (`*.learn.zip` etc.) ; **vérifie la signature de chaque document avec les clés de confiance embarquées**, l'anti-retour (`generatedAt` > celui déjà gardé), les plafonds (256 Ko / 64 Ko), ignore tout autre fichier ; sortie : `StoreUsbImport.Result` (accepté / refusé + cause française par document, jamais d'exception) puis écrit via `StoreFiles` (même chemin que `POST /api/store/catalog`, même verrou, mêmes refus).
2. Aucun lot n'est installé sans passer par `TvLotStore.installReceived` (signature + SHA-256 + budget) : l'import USB ne contourne AUCUNE vérification de W4/W17 ; un lot loué reste inaccessible sans contrat/clé (l'import ne copie que du contenu libre ou déjà scellé).
3. Chemins : refuser `..`, liens symboliques, noms hors liste blanche, fichiers > plafond ; clé exFAT/FAT : tolérer les noms en casse différente, pas de zéros de remplissage (voir docs/agent-reports sur exFAT).
4. Tests, rouge d'abord par assertion : catalogue valide accepté ; signature invalide refusée ; ancien (anti-retour) refusé ; fichier surdimensionné refusé ; dossier vide/clé absente = « Aucun catalogue sur la clé » sans erreur ; deux documents dont un seul valide = le bon est gardé, l'autre expliqué ; chemin piégé refusé ; équivalence avec `POST /api/store/catalog` (mêmes octets ⇒ même état).
5. Outil bureau : `tools/pilot/store_usb_pack.py` (Python) qui prépare le dossier `CastBridge/store/` à copier sur la clé à partir des catalogues signés et des lots (aucune clé privée : il ne signe pas), + test Python.
6. Docs : une section dans `docs/STORE.md` (w17-09 la rédige ; ce cahier ne crée que l'outil et le cœur) et le journal `docs/test-plans/STORE-PILOT.md` (w17-10) reçoit le point humain « boutique visible sur la TV après copie du catalogue sur la clé, téléphone absent ».

## Interdits
Pas de réseau, pas d'écriture hors `files/store/`, pas de nouvelle clé, pas de format signé nouveau, pas de modification des 11 autres cahiers (propriétaires disjoints : `C/store/StoreUsbImport.kt`, `CT/store/StoreUsbImportTest.kt`, `tools/pilot/store_usb_pack.py`, `tools/tests/test_store_usb_pack.py`).
