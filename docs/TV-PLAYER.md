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
