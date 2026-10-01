# Brief : données lourdes de CastBridge-TV dans `Download/` de la clé USB (persistantes après désinstallation)

Agent cloud. Base : branche `integration/agents` (PAS main). Créer `claude/usb-data` depuis `origin/integration/agents`, petits
commits, pousser cette branche. Pas de pull request. Ne pas toucher `main`, `feat/ssh`, `integration/agents`.
Apps : « CastBridge » (téléphone), « CastBridge-TV » (TV). Textes utilisateur en français.
Lire d'abord : `docs/STORAGE.md`, `docs/HANDOFF.md`, `core/.../tv/{Storage,Folders,Background}.kt`, les classes de volumes
(`VolumeRegistry`, `StoragePolicy`, `AndroidVolumeProvider`, `FolderIndex`), `receiver/.../{UsbImporter,TvDownloads,
LibraryScreen}.kt`, `TvService.kt`, la gestion des téléchargements aria2 (`core/.../dl/*`), `docs/agent-briefs/` (autres chantiers).

## Demande de l'owner
« L'app TV stocke ses données dans `Download` de la clé USB, pour qu'après désinstallation les contenus lourds multimédias
persistent. » Aujourd'hui les volumes `internal` et `removable` existent, mais les données de la clé sont dans
`Android/data/castbridge.receiver/` (dossier privé de l'app, **effacé à la désinstallation**).

## Faits mesurés sur la TV de référence (Android 14, Amlogic/CVTE)
- Clé branchée : « UBUNTU 24_0 », exFAT, ~62 Go, écriture mesurée ~1,4 Mo/s (lente), id de volume `883F-EEB4`.
- Le compte de l'app peut **créer et écrire directement** dans `/storage/883F-EEB4/Download/` (test réussi, fichier supprimé).
  Le dossier appartient à `root:media_rw` (droits `rwxrwx---`). Un nouveau téléchargement par la même app fonctionne donc
  sans permission supplémentaire ; vérifier malgré tout le comportement sur d'autres Android (10, 11, 13) et prévoir un repli.
- La mémoire interne est petite (≈2,4 Go, ≈0,6 Go libres) : les contenus lourds doivent aller sur la clé.

## À livrer
1. **Nouvelle racine de données sur la clé** : `<clé>/Download/CastBridge/` avec des sous-dossiers `Bibliotheque/` (vidéos
   et médias reçus), `Telechargements/` (aria2), `Medias/` (futurs lots multimédias lourds, hors budget 10 Mo des lots de
   données), `index/` (fichiers d'index et de réadoption). Politique de stockage : si une clé accessible est présente, y mettre
   par défaut les contenus lourds ; sinon retomber sur la mémoire interne avec un avertissement clair ; réglage utilisateur
   « Contenus lourds sur la clé USB » (oui par défaut quand une clé est là) et choix de la clé si plusieurs.
2. **Réadoption après réinstallation** : au démarrage et à l'insertion d'une clé, analyser UNIQUEMENT
   `Download/CastBridge/` (profondeur et durée bornées, jamais toute la clé), reconstruire la bibliothèque, l'index des
   dossiers virtuels et les vignettes (cache régénérable), reprendre les `.part` des transferts interrompus, dédoublonner.
   Fichier `index/castbridge-store.json` (version de format, identifiant de la clé, date) écrit atomiquement ; tolérer un
   index absent ou corrompu (reconstruction depuis les fichiers).
3. **Sauvegarde non secrète des réglages** (optionnelle, activable) : `index/settings.json` avec uniquement des préférences
   non sensibles (langue, profil de stockage, quota, tuiles). **Jamais** sur la clé : code PIN, jetons, clés SSH, code
   parental, liste des téléphones de confiance, rapports parentaux, jetons d'appairage. Un test automatique vérifie qu'aucun
   de ces champs ne peut être écrit sur la clé.
4. **Migration** : action « Déplacer les contenus vers la clé » (mémoire interne → clé) avec progression, reprise, vérification
   de taille/empreinte avant suppression de la source, jamais de suppression sans confirmation, et inverse « Rapatrier ».
5. **Robustesse** : retrait/réinsertion de la clé en cours d'écriture (fichiers `.part`, écriture atomique, `fsync`), clé en
   lecture seule ou pleine, exFAT « sale », FAT32 (limite 4 Go par fichier : gérer comme l'existant), clé lente (avertir,
   écrire en tâche de fond, ne pas bloquer la lecture), plusieurs clés, noms de fichiers invalides (caractères exFAT/FAT),
   chemins relatifs dangereux (`..`), fichiers exécutables/APK trouvés sur la clé (ignorés, jamais installés
   automatiquement), liens symboliques, limites de longueur, horodatage faux de la TV.
6. **Sécurité des données de la clé** : une clé est un support amovible non fiable ; ne rien exécuter ; valider tout nom et tout
   index lu ; limiter la taille des index lus ; pas de lecture hors `Download/CastBridge/`.
7. **API et téléphone** : `/api/storage` indique la racine utilisée, la clé, l'espace, la vitesse, l'état de réadoption (additif) ;
   l'écran « Données » du téléphone (agent frère `lots-framework`) ou l'onglet TV affiche « Contenus lourds : clé USB … »
   avec les mêmes avertissements. La livraison différée de lots (≤ 10 Mo) peut rester en mémoire interne ; documenter le choix.
8. **Tests** : JVM (faux système de fichiers/volumes) pour la politique de choix de volume, la réadoption (index présent,
   absent, corrompu, doublons, `.part`), la migration (interruption à chaque étape), la sécurité (aucun secret écrit,
   rejet des chemins dangereux), les limites FAT32/exFAT. Lancer `cd android && gradle :core:test` ; si le plugin Android ne
   se résout pas dans le cloud (Maven 429/proxy), banc « core seul » et dire ce qui n'a pas pu être compilé (`:receiver`).
   Le code Android doit rester petit et évidemment correct : l'owner compile et teste sur sa TV et sa clé.
9. **Protocole de test manuel** à écrire dans `docs/STORAGE.md` : copier un fichier par le téléphone, vérifier son emplacement
   sur la clé, désinstaller CastBridge-TV, réinstaller, constater la réadoption, retirer/réinsérer la clé.
10. Docs : `docs/STORAGE.md`, `docs/HANDOFF.md` (entrée datée, sans secret).

## Rapport final (français, court)
Ce qui est livré, tests et résultats, ce qui reste à valider sur la TV et la clé, risques connus, branche poussée.
