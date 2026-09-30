# Lecteur du téléphone (« Ouvrir avec » + Caster)

L'app téléphone est aussi un lecteur multimédia : elle apparaît dans « Ouvrir avec » et « Partager » (Galerie, Fichiers,
WhatsApp, Telegram, navigateur) et ouvre directement le lecteur (`player/PlayerActivity`, tâche à part), sans passer par l'accueil.

## Ce qui ouvre le lecteur

| Intent | Données |
|---|---|
| `VIEW` | `content://`, `file://` en `video/*`, `audio/*`, `image/*`, `application/ogg`, HLS (`application/x-mpegURL`, `application/vnd.apple.mpegurl`), DASH (`application/dash+xml`) |
| `VIEW` | `http(s)://` annoncé `video/*`, `audio/*`, HLS, DASH ; liens directs `…​.m3u8`, `.mpd`, `.mp4`, `.mkv`, `.webm`, `.mp3` |
| `SEND` / `SEND_MULTIPLE` | `video/*`, `audio/*`, `image/*` (plusieurs fichiers = liste de lecture / diaporama) |

Une page web ordinaire (`.html`) n'est pas interceptée.

## Pourquoi Media3/ExoPlayer (et pas libVLC) sur le téléphone

- **Taille** : Media3 (exoplayer + hls + dash + ui + session) et tout le lecteur ajoutent ≈ 3,4 Mo à l’APK release (11,2 → 14,6 Mo, sans minification) ; libVLC ajoute 20 à 30 Mo **par ABI**
  (le module TV `libvlc-all` fait ~ 80 Mo toutes ABI). Pour une app d'envoi, c'est disproportionné.
- **Intégration système** : MediaSession/notification/écran verrouillé/Bluetooth/Android Auto, Picture-in-Picture,
  décodage matériel (HEVC, HDR sur S21) et économie de batterie viennent « gratuitement » avec Media3.
- **Repli propre** : ce que le téléphone ne décode pas (souvent AC3/E-AC3/DTS, WMV/VC-1, RMVB, parfois HEVC 10 bits selon
  l'appareil) est détecté (pistes non prises en charge ou erreur du lecteur) : message clair + bouton **« Caster vers la TV »**
  (la TV a libVLC et lit presque tout).

Formats couverts par Media3 sur téléphone : conteneurs MP4/M4V/MOV, MKV/WebM, TS/M2TS, FLV, AVI (support de base), MP3, AAC/M4A,
FLAC, Ogg/Opus/Vorbis, WAV, AMR ; flux HLS, DASH, HTTP progressif. Codecs : ceux du téléphone (H.264, HEVC, VP9, AV1 selon
puce ; AAC, MP3, Opus, Vorbis, FLAC). Sous-titres : internes (SRT/SSA/ASS basiques, VobSub/PGS dans MKV, WebVTT, TTML)
et externes `.srt`, `.ass/.ssa`, `.vtt`, `.ttml`.

## Lecteur

Gestes : glisser horizontal = avancer/reculer (largeur d'écran = 90 s), vertical à gauche = luminosité, à droite = volume,
double-tap gauche/droite = ∓10 s (centre : pause). Verrouillage de l'écran, rotation (auto selon la vidéo, suivre le téléphone,
paysage, portrait), format d'image (ajuster, remplir/étirer, zoom/rogner), vitesse 0,5×–2×, pistes audio et sous-titres,
« Charger des sous-titres… », reprise à la dernière position (clé nom + taille, règle de la TV : rien avant 10 s, oublié une
fois vu), liste de lecture du dossier (ordre naturel : « Épisode 2 » avant « Épisode 10 »), PiP (auto sur Android 12+), lecture
en arrière-plan (musique toujours, vidéo si l'option est cochée) avec notification MediaSession. Photos : balayage entre les
photos du dossier, pincer/double-tap pour zoomer, diaporama (4 s).

Sous-titres « à côté » : Android 11+ indexe `.srt/.ass/.vtt` comme sous-titres dans le MediaStore, lisibles avec la permission
vidéo sur la plupart des téléphones (à confirmer sur le S21) ; sinon « Charger des sous-titres… ».

## Caster

Bouton cast (lecteur et visionneuse) → feuille « Diffuser sur » :

- **TV CastBridge** (mDNS, PIN mémorisé) : « Lire en direct (sans copier) » (la TV lit le petit serveur HTTP du téléphone,
  route TV `POST /api/playurl`, TV ≥ 0.6.5), « Copier sur la TV et lire » et « Déplacer vers la TV » (`UploadService` ; le
  téléphone continue de lire pendant la copie et la TV prend le relais dès qu'elle a 30 s d'avance sur la position du téléphone ;
  le déplacement ne supprime qu'après vérification TV fichier complet + taille exacte, avec la confirmation d'Android).
  Déplacer n'est proposé que pour les fichiers que l'app peut supprimer (MediaStore / Documents), jamais un fichier partagé par WhatsApp.
- **TV DLNA** : lecture en direct (MediaServer + Upnp, comme l'onglet « TV DLNA »), puis Seek à la position.

La TV démarre à la position du téléphone moins 2 s (0 si on est dans les 10 dernières secondes), le téléphone se met en pause et
devient la télécommande (lecture/pause, ±10 s, barre synchronisée, volume TV pour CastBridge, « Revenir sur le téléphone » qui
reprend ici à la position de la TV — après un déplacement, depuis la copie de la TV). Mini-contrôleur en bas de l'app tant que la TV joue.

## Onglet « Sur le téléphone »

MediaStore : Récents, Vidéos, Musique, Photos, Dossiers, recherche, vignettes. Permissions : `READ_MEDIA_VIDEO/AUDIO/IMAGES`
(13+), `READ_MEDIA_VISUAL_USER_SELECTED` (14+, accès partiel signalé avec « Modifier la sélection »), `READ_EXTERNAL_STORAGE`
(≤ 12). Appui long : Caster / Copier / Déplacer vers la TV.
