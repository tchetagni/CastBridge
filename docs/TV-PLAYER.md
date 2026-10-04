# Lecteur de CastBridge-TV : affichage et qualité d'image

Demande du propriétaire (2026-10-03) : « côté TV, améliorer la qualité d'image et faire que le format de la vidéo s'ajuste parfaitement au format de la TV par défaut, et dans un réglage permettre d'afficher aussi en format et qualité natives ». Rien ici ne touche au protocole téléphone ↔ TV (règle de symbiose : additif, aucune route HTTP, aucun champ échangé ; la mémoire par fichier gagne seulement une clé `fm=` que les anciennes versions ignorent).

## Affichage (`C/tv/VideoFit`, pur)
La taille réelle de la dalle (surface vidéo, sinon `DisplayMetrics`) est lue à l'exécution : aucun modèle de TV en dur (720p, 1080p, 4K, 21:9 ou autre).

| Mode | Libellé | Effet |
|---|---|---|
| FILL (défaut depuis la 0.14.29) | Remplir l'écran | couvre toute la dalle, rogne le surplus au centre, jamais étiré ; 16:9 sur 16:9 = plein écran sans rognage ; 4:3 = rogné en haut et en bas (25 % de la hauteur) ; 2,39:1 = rogné sur les côtés (décision du propriétaire du 2026-10-04 : « écran entièrement rempli ») |
| FIT | Ajusté à l'écran | le plus grand possible, proportions vraies, image entière : 4:3 = bandes à gauche et à droite ; 2,39:1 = bandes fines en haut et en bas (c'était le défaut de la 0.14.28, identique au comportement natif de libVLC : rien de visible hors 16:9 exact) |
| STRETCH | Étirer | remplit la dalle sans respecter le format : déforme (dit à l'écran) |
| NATIVE | Natif | taille native 1:1 centrée, sans mise à l'échelle ; plus grande que la dalle : réduite seulement pour tenir ; aucun filtre |

- Le format de pixel de la piste (SAR) et la rotation sont honorés (AVI anamorphique non écrasé, vidéo de téléphone en portrait). Deux formats à moins de 0,5 % l'un de l'autre sont tenus pour identiques (853x480 remplit 16:9 sans bande d'un pixel).
- Réglages : `MENU > Affichage` (par fichier : « Comme le réglage par défaut » ou un mode, mémorisé comme les pistes et les décalages, clé `fm=`) et `Affichage par défaut` (global, préférence `video_fit`, « Remplir l'écran » au départ ; le réglage global n'est mémorisé qu'après un choix explicite, marque `video_fit_set=1` : sans marque, une valeur « fit » écrite par une ancienne version suit le nouveau défaut). L'ancien menu « Format d'image » (16:9, 4:3, rogner...) reste et, tant qu'il n'est pas sur « Automatique », reste prioritaire ; choisir un mode d'Affichage le remet sur « Automatique ».
- Effet immédiat (appels libVLC `setVideoScale`/`setAspectRatio`), rejoué à chaque événement `Vout` et à chaque changement de taille de la surface. Seul le passage vers ou depuis « Natif » relit le fichier à la même position (les options de qualité sont des options de média).
- INFO : « Affichage : Ajusté à l'écran 1920×1080 → 1280×720 ».

## Qualité d'image (`C/tv/PictureQuality`, pur)
- Désentrelacement : coupé si la piste est progressive ; forcé (`blend` sur petit processeur ou pendant une copie, `yadif` sinon) si la piste le dit ; « automatique » de libVLC si elle ne le dit pas (cas actuel : libVLC 3.6 n'expose pas l'indicateur) ; jamais en Natif sans indication.
- `--swscale-mode` lanczos (9) seulement si le convertisseur est logiciel ET le processeur n'est pas faible (plus de 4 coeurs) ET pas de copie en cours ET pas en Natif. Chemin matériel : rien à régler.
- Rien n'est forcé de dégradant (ni RV16 ni `--android-display-chroma`), `--avcodec-skiploopfilter` reste au défaut au repos (seul R-16 le relève), audio inchangé.
- La détresse (R-16) GAGNE TOUJOURS : en détresse, aucune option de qualité (`:deinterlace=0`). La qualité n'écrit jamais d'option `:avcodec-*` (propriété de `PlayerTuning`).

## Ce qui n'est pas vérifié (aucun appareil ici ; P-54)
- Que `MediaPlayer.ScaleType.SURFACE_ORIGINAL` de libVLC 3.6.5 donne bien le 1:1 sans lissage (et tienne compte du SAR) sur chaque TV ; sinon le « Natif » reste ajusté.
- Que `:deinterlace=` / `:deinterlace-mode=` / `:swscale-mode=` soient pris en compte comme options de média, et que le désentrelacement s'applique sur une surface MediaCodec opaque (probablement pas : indiqué dans INFO).
- 10 bits / HDR : libVLC 3.6 pour Android n'a pas de tone mapping ; l'image dépend du décodeur et de la TV (dit dans INFO si connu ; l'indicateur n'est pas lisible, donc jamais affirmé).
- Aucune amélioration « magique » (netteté, réduction de bruit) : ajouter un filtre coûte du processeur sur les TV faibles et fausse le « natif ».


## Diagnostic (0.14.29)
La touche INFO du lecteur affiche : « Affichage : <mode> (par défaut) · source WxH SAR n:d · dalle WxH · image WxH », avec l'origine du mode (défaut, réglage global ou réglage du fichier) ; « image non appliquée » si aucune mise à l'échelle n'a été faite. L'ajustement est réessayé aux événements ESAdded, ESSelected et TimeChanged tant que la piste vidéo n'est pas connue.

## Télécommande de base : toutes les options sans touche MENU ni INFO (0.14.30)
Décision pure : `C/tv/PlayerRemote.decide` (testée) ; vues : `PlayerControls`. Une télécommande à 5 touches (haut, bas, gauche, droite, OK) + RETOUR suffit.

| Touche | Barre cachée | Barre visible |
|---|---|---|
| OK (appui court) | affiche la barre de commandes (le focus est sur « Pause ») | le bouton qui a le focus agit |
| OK tenu (appui long) | ouvre les réglages de lecture | (le bouton agit) |
| GAUCHE / DROITE | recul / avance de 10 s | déplacent le focus entre les boutons |
| HAUT | avance de 60 s | avance de 60 s |
| BAS | recul de 60 s | ouvre les réglages de lecture |
| RETOUR | arrête la lecture | cache la barre (n'arrête pas) |
| MENU, INFO, CAPTIONS, AUDIO, lecture/pause, suivant/précédent | inchangés quand la télécommande les a | idem |

Pour mettre en pause : OK (la barre apparaît), OK (« Pause » a le focus). Barre : « Pause/Lecture », « Audio », « Sous-titres », « Affichage », « Infos », chacun avec son icône et son libellé (28 sp, icône 40 dp), plus la ligne de progression et le temps. La barre se cache seule après 5 s sans touche, et reste affichée tant que la vidéo est en pause. Les boutons ouvrent les mêmes choix que le panneau MENU (`PlayerPanel`, aucune logique en double) ; « Affichage » : Remplir l'écran / Ajusté à l'écran / Étirer / Natif. Un fichier n'ayant qu'une piste audio dit « Une seule piste audio ». Le défaut d'affichage reste « Remplir l'écran ».

Remarque : sur la barre cachée, GAUCHE/DROITE/HAUT/BAS cherchent comme avant et montrent la ligne de progression ; seul OK fait apparaître la barre.

### Diagnostic sans aucune touche
Pendant 6 s après le début de la lecture, et à chaque changement d'affichage, une ligne discrète en haut à gauche (sous l'icône de copie) dit : « Affichage : <mode> · source WxH · dalle WxH · image WxH » (« image non appliquée » si aucune mise à l'échelle n'a eu lieu, « source inconnue » avant la piste vidéo ; `VideoFit.overlayLine`). La ligne se met à jour toute seule le temps qu'elle est visible. Le bouton « Infos » (ou la touche INFO) la ramène 6 s et ouvre la « Légende des icônes » ; « Infos techniques » ouvre l'ancienne fenêtre.

## Options multimédia (wtv-01, 0.14.31)
Règles pures : `C/tv/` (`ResumePolicy`, `SleepTimer`, `LoopAB`, `Bookmarks`, `PictureTuning` + `PerformanceGuard`, `AudioTuning`, `SubtitleStyle`, `NextEpisode`, `PlayerSeek`, `PlayerSettings`, `PlayerPrefs`), toutes testées (`PlayerOptionsTest`). Câblage : `PlayerExtras`, `PlayerPanel`, `PlayerActivity` (vérifié par compilation seulement). Réglages stockés localement, par fichier, avec la même clé `taille:nom` que l'affichage (`fm=`) : nouvelles clés `pc=` (image), `at=` (son), `st=` (sous-titres), `bm=` (marque-pages) ; les anciennes données se lisent inchangées. Aucun réseau, aucune télémétrie.

### Touche → action, télécommande à 5 touches (haut, bas, gauche, droite, OK) + RETOUR
| Touche | Barre cachée | Barre visible | Dans « Réglages du lecteur » |
|---|---|---|---|
| OK court | affiche la barre | le bouton actif agit | (valeur : change vers la droite ; action : s'exécute) |
| OK long | ouvre le panneau « Réglages de lecture », dont la 1re ligne est **Réglages du lecteur** | — | — |
| GAUCHE / DROITE | saut du pas choisi (10 s par défaut) ; appui long ou deux appuis rapides : trois fois le pas (30 s) | déplacent le focus | changent la valeur de la ligne (◀ ▶) |
| HAUT / BAS | +60 s / -60 s | HAUT : +60 s ; BAS : ouvre le panneau | se déplacent dans la liste (tourne aux extrémités) |
| RETOUR | arrête la lecture | cache la barre | ferme l'écran |
| RETOUR ou OK pendant « Épisode suivant dans N s » | annule le compte à rebours | idem | — |

Un appui tenu sur GAUCHE/DROITE ne fait plus qu'un seul grand saut (avant : un saut de 10 s à chaque répétition). Toujours disponibles quand la télécommande les a : MENU (panneau), INFO, CAPTIONS, AUDIO, suivant/précédent.

### Les 10 fonctions et où les trouver (écran « Réglages du lecteur », par rubriques)
1. **Reprise** : bibliothèque > OK sur une vidéo > « Reprendre à 55:04 » (par défaut) / « Recommencer » ; rien sous 30 s ni au-delà de 95 %.
2. **Sauts** : Lecture > « Saut des flèches » 10/20/30 s ; « Vidéo suivante / précédente de la liste ».
3. **Minuteur d'arrêt** : Lecture > 15, 30, 60, 90 min ou fin de la vidéo ; « Arrêt dans 14:59 » sur la barre ; fondu du son de 10 s puis PAUSE (la TV ne s'éteint jamais ; le minuteur « fin de la vidéo » coupe aussi la liste et l'épisode suivant).
4. **Boucle A-B** (1er appui A, 2e appui B, 3e efface ; B avant A : échangés) et **marque-pages** (20 au plus par fichier, ajouter ici / aller à / supprimer).
5. **Image** (tout à zéro par défaut, jamais de filtre au repos) : luminosité 50-150 %, contraste 50-150 %, saturation 0-200 %, gamma 50-300 % et désentrelacement forcé = filtres libVLC (la vidéo se rouvre à la même seconde) ; zoom 1x-3x, déplacement, rotation 90° = transformations de la vue (aucun coût de décodage) ; « Réinitialiser l'image ». **Garde de performance** : un filtre actif et plus de 10 % d'images perdues (au moins 120 images jugées) : les filtres se coupent seuls, « Filtres d'image coupés : la TV perdait des images ».
6. **Son** : mode nuit (compresseur libVLC), amplification 100-200 % (avertissement au-delà de 100 %), « Vitesse sans changer la voix » (correction de hauteur, désactivée par défaut).
7. **Sous-titres** : couleur, contour/ombre, position (0-40 % depuis le bas), encodage (automatique, UTF-8, Latin-1, Windows-1252, Latin-9, UTF-16), police ; aperçu. Un style non modifié n'ajoute aucune option libVLC.
8. **Épisode suivant automatique** : à la fin d'une vidéo hors liste, la suivante du MÊME dossier (ordre naturel : Ep2 avant Ep10) démarre après 8 s, annulable ; réglage « Épisode suivant automatique » (oui par défaut).
9. **Sous-titres du dossier** (déjà en place : `SubtitleFinder`) et **mémoire par fichier** de tous les réglages ; « comme le réglage par défaut » (efface le choix du fichier) et « enregistrer comme réglage par défaut » par rubrique (Image, Son, Sous-titres).
10. **Écran « Réglages du lecteur »** : une liste par rubriques (Lecture, Image, Son, Sous-titres, Affichage), libellé français + valeur + « (par défaut) ».

Contrôle à distance (additif, anciens téléphones non cassés) : `POST /api/player/<sleep|loop|mark|picture|night|gain|pitch|substyle|autonext|skipstep>` ; `GET /api/player/tracks` gagne les clés `sleep, loopA, loopB, bookmarks, picture, night, gain, keepPitch, subStyle, autoNext, skipStep`.

### Ce que seule une vraie TV (GaiaOS 32 bits, 720p) peut confirmer
- La **performance** des filtres d'image (la garde est une sécurité, pas une preuve) et le fait que le filtre « adjust » s'applique avec le décodage matériel (MediaCodec) ; sinon l'effet est nul.
- Le **désentrelacement** forcé (aucun fichier entrelacé testé ici).
- La **normalisation** (mode nuit) : réglages du compresseur choisis sans écoute.
- Zoom/déplacement/rotation sur la vue vidéo (SurfaceView) et le fondu du minuteur ; la correction de hauteur (coût processeur).
- Le défilement de la liste de réglages à la télécommande et la lisibilité à 3 m.

## Icônes du lecteur
Règle (propriétaire, 2026-10-04) : jamais d'icône sans libellé texte en français ; texte >= 28 sp, icône >= 40 dp, pastille sombre à 80 % d'opacité (contraste du texte blanc >= 4,5:1 même sur une image blanche : testé, `PlayerIcons.contrastOverWhite`), marges de sécurité de 5 % de la dalle (`PlayerIcons.safe` : 64 x 36 dp sur 720p). Rien n'a été supprimé.

| Élément | Fichier | Quand | Sens | Taille / contraste / zone de sécurité |
|---|---|---|---|---|
| Pastilles de la barre d'état (13 types : Internet, Téléphone, Télécommande, SSH, Internet du téléphone, Diffusion, Clé USB, Wi-Fi Direct, Quiz, Échecs, Téléchargement, Mode enfant, Mise à jour) | `StatusBarView`, `core/status/StatusIcons` | haut à droite ; AVANT : toujours visibles, libellé seulement 4 s ou au focus (cause du retour du propriétaire : pastilles bleues sans nom) | voir la légende | AVANT : glyphe 26 dp, libellé 14 sp, marge 24 dp (coupée par le surbalayage). MAINTENANT en lecture : libellé toujours écrit, 28 sp, glyphe 40 dp, marge 5 % ; la zone apparaît avec la barre de commandes, ou sur erreur/reconnexion, ou 4 s après un changement |
| Petite marque technique (Wi-Fi, Bluetooth, Ethernet, Wi-Fi Direct, USB) | `StatusBarView` | bas droite de la pastille | par où passe la liaison | 14 dp : trop petite pour être lue seule, d'où le libellé « Wi-Fi · ... » et la légende |
| « +N » | `StatusBarView` | plus de pastilles que de place | N autres connexions | libellé « +N » |
| Étiquette de licence « PRODUCTION · Clé illimitée » | `KeyBadgeOverlay` | haut centre, tous les écrans | édition et durée de la clé | AVANT 14 sp, marge 6 px (coupée) ; MAINTENANT 20 sp, marge 5 % |
| Copie en cours | `CopyBadgeView`, `core/xfer/CopyBadge` | haut gauche, sur la vidéo seulement (cachée quand la barre de progression est affichée) | un fichier arrive sur la TV | AVANT 14 sp, ⬇ + « 42 % » ; MAINTENANT « Copie en cours 42 % », 28 sp, marge 5 % |
| Envoi progressif | `activity_player.xml` (`lead`) | haut droite | « Envoi N % - encore ... de lecture sans réseau » | AVANT 16 sp, marge 24 dp ; MAINTENANT 20 sp, marge 64 x 36 dp |
| Bandeau de message | `Banner` | haut centre, 3 s | messages (« Pause », « Audio : ... ») | 19 sp ; marge haute portée à 5 % |
| Progression (titre, barre, temps) | `ProgressOverlay` | bas, 4 s (maintenant tant que la barre de commandes est visible) | position et durée | 24 sp ; marge basse de sécurité ajoutée |
| Barre de commandes | `PlayerControls` | OK | voir plus haut | 28 sp, icônes 40 dp |
| Ligne de diagnostic | `PlayerControls` | 6 s | voir plus haut | 22 sp, pastille sombre |
| Icône dorée en bas à droite (photo du propriétaire) | NON IDENTIFIÉE dans ce code | inconnu | inconnu | à identifier sur la TV (P-54, étape de la légende) ; ce n'est pas la barre d'état ni la copie |

Deux zones : en haut à droite, les connexions (avec libellés) ; en haut à gauche, copie et diagnostic. Une seule zone n'a pas été faite (la copie reste à gauche pour ne pas chevaucher la colonne des pastilles, de hauteur variable). Le dessin n'est vérifié que par compilation ; la lisibilité réelle à 3 m, le surbalayage et la superposition avec la barre de progression ne peuvent être jugés que sur la TV.

