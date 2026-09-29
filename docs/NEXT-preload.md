# Prochaine tâche : précharger la vidéo sur la TV (résiste au changement de réseau)

## Besoin (Esaie)
Envoyer la vidéo *entière* sur la TV pour qu'elle la lise depuis son stockage : la lecture ne dépend plus
du téléphone ni du Wi-Fi. Le téléphone peut changer de réseau sans interrompre la vidéo.
Une TV DLNA standard ne sait pas faire cela (elle lit en direct depuis le téléphone) : on passe par une app
réceptrice **CastBridge TV** (module `android/receiver`), cible = Android TV / box Android (ex. Amlogic).

## Conception retenue
**Récepteur (`:receiver`, à créer)** — NanoHTTPD :8765, stockage `getExternalFilesDir("videos")`
- `PUT /upload/<nom>?offset=N&total=T` : corps = octets restants, écrits en append dans `<nom>.part` ;
  409 + `{"length":cur}` si offset != taille actuelle ; renomme en `<nom>` quand taille == total ; refuse si < 100 Mo libres.
- `GET /api/part?name=`, `POST /api/reset?name=`, `GET /api/info` (fichiers, espace libre, état lecture),
  `POST /api/play?name=&pos=`, `/api/pause`, `/api/resume`, `/api/seek?pos=`, `/api/stop`, `/api/delete?name=`.
- Lecture libVLC 3.6.5 (`org.videolan.android:libvlc-all`) plein écran, télécommande (OK = pause, ←/→ = ±10 s).
- Annonce mDNS `_castbridge._tcp.` avec attribut `role=receiver` (le serveur PC annonce `role=server` : à filtrer).
- Manifest Leanback (`LEANBACK_LAUNCHER`), splits ABI armeabi-v7a + arm64-v8a.

**Téléphone (`:sender`)**
- `TvDiscovery` (NsdManager, résolutions sérialisées, filtre `role=receiver`) + saisie manuelle d'IP.
- `UploadService` (foreground `dataSync`) : téléversement **reprenable** — à chaque échec réseau, attendre
  (backoff ≤ 5 s), re-résoudre la TV par son nom mDNS, interroger `/api/part`, reprendre à cet offset.
  État exposé par un `StateFlow` (Idle / Uploading / Waiting / Done / Failed) ; lecture auto à la fin.
- Nouvel onglet « CastBridge TV » dans `MainActivity` : choix TV, fichier, progression, contrôles
  (sondage `/api/info` toutes les secondes, tolérant aux coupures : « TV injoignable – la lecture continue »).

## Limites à afficher clairement
- Le préchargement exige que téléphone et TV soient sur le même réseau *pendant l'envoi* ; il reprend seul au retour.
- Une fois le fichier sur la TV, la lecture continue quoi que fasse le téléphone ; le pilotage se rétablit à la reconnexion.

## Déjà fait / état
- Crash de `ServerService` corrigé (permission `WAKE_LOCK` manquante) et vérifié sur un Samsung (Android 14).
- La découverte DLNA voit deux moteurs sur le réseau d'Esaie : « Smart TV » (`192.168.0.247`) et
  « DLNA-Player-MediaCenter » (Amlogic, `192.168.0.117`).
- Compilation locale : Gradle 8.14.3 déjà installé dans `~/.gradle` (pas de wrapper dans le dépôt, ne pas en ajouter),
  SDK dans `~/Library/Android/sdk`. Ne jamais laisser de dossiers `build/` dans le dossier OneDrive.
