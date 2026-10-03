# CastBridge-TV : « la navigation n'est plus fluide » (R-11)

Date : 2026-10-03. Branche `claude/tv-perf` (depuis `integration/agents` b405333d). Signalement du propriétaire : « l'application TV laggue et est un peu
moins réactive qu'avant. La navigation n'est plus fluide », précisé : « depuis qu'on intégrait copier-lire, particulièrement dans les quiz et la navigation
d'accueil ». TV de référence : GaiaOS, Amlogic T950 (4×A55), Android 14, 32 bits, 720p à 160 dpi, CastBridge-TV 0.14.21-beta verrouillée (69), sans Wi-Fi.

Mesures faites **uniquement sur l'émulateur** `emulator-5580` (TV 1280x720 à 160 dpi, image arm64 Android 14). Aucun ordre envoyé au téléphone du
propriétaire ni à une TV réelle, aucune action serveur.

## 1. Conclusion

La cause principale n'est ni le réseau absent ni le Bluetooth : c'est **le dessin continu de l'accueil** et **le travail de l'accueil qui continuait sous les
autres écrans** (Quiz, Jeux, Apprendre, Langues sont des activités posées au-dessus de l'accueil : celui-ci restait « visible » pour son propre code).

| # | Suspect | Preuve (fichier:ligne, avant correctif) | Effet mesuré (émulateur) | Corrigé ? |
|---|---------|------------------------------------------|---------------------------|-----------|
| 1 | Fond de l'accueil « Ken Burns » : `ValueAnimator` **infini** à 60 images/s, sur une image plein écran sous deux dégradés | `receiver/.../TvCards.kt:327-334` (depuis 18afc166, 2026-09-30) ; démarré par `HomeScreen.show()` `HomeScreen.kt:116` | accueil **au repos** : **58,3 images/s** ; RenderThread 4,9 s CPU / 20 s ; fil principal 0,93 s / 20 s | OUI : même mouvement (1 → 1,12 en 30 s, aller-retour) échantillonné à 4 images/s (`SlowZoomCurve`, pas < 2 px). Après : **4,3 images/s**, RenderThread 0,34 s, fil principal 0,10 s / 20 s |
| 2 | L'accueil caché sous le Quiz / Jeux / Apprendre / Langues gardait son zoom (rappels à chaque vsync) **et** son tic de 4 s (rechargement de la liste + recalcul de toutes les tuiles) sur le fil principal **partagé** avec ces écrans | `HomeScreen.kt:112` (`if (visible)` = visibilité du conteneur, restée VISIBLE) ; `PlayerActivity.onStop` ne l'arrêtait pas | sous l'écran Jeux, sans rien toucher : **3 440 ms de CPU/min** du fil principal (5,7 % d'un cœur de Mac ; plusieurs fois plus sur un A55) ; sous le Quiz : 2 590 ms/min | OUI : `HomeScreen.pause()/resume()` appelés par `PlayerActivity.onStop/onStart`. Après : **20 ms/min** sous Jeux, **330 ms/min** sous le Quiz |
| 3 | Le tic de 4 s recalculait les tuiles **sur le fil principal** : « Langues » ouvrait et analysait **chaque lot zip deux fois** (`status` + `hasContent`), « Bibliothèque » relistait toute la bibliothèque (un `stat` par fichier, la clé exFAT comprise) | `LanguesHub.kt:70-76` (depuis 0.14.14, 17a79f27) ; `PlayerActivity.kt:548` ; appelé par `HomeScreen.refreshTools()` `HomeScreen.kt:235-239` | inclus dans les 3 440 ms/min ci-dessus (46 lots Langues installés sur l'émulateur, comme le catalogue) ; croît avec le nombre de lots (H5) | OUI : nombre de packs gardé jusqu'à l'installation / suppression d'un lot (`LanguesHub.lotsConsumer`, seul chemin) ; compte de la bibliothèque repris de la liste calculée sur le fil `cb-home` ; signature de la liste calculée hors fil principal |
| 4 | Fond du Quiz (`StageBackground`) : plein écran redessiné à 20 images/s avec **4 dégradés alloués à chaque image** (ramasse-miettes) | `QuizViews.kt:204-225` | Quiz au repos : **15,9 images/s**, RenderThread 950 ms/min | OUI : dégradés créés une fois par taille et déplacés par matrice ; ~12 images/s (8 pendant une question), fluide pendant l'éclair de 0,9 s (`StagePace`). Après : **10,5 images/s** ; D-pad p50 21 → 17 ms, p99 24 → 20 ms |
| 5 | Puce de réception (période « copier-lire », 0.14.18+) : chaque évènement de progression (≈ 1/s **par transfert**, davantage avec plusieurs voies) recalcule `api.status()` (listing disque à cache 1 s) et refait `setText` (nouvelle mise en page de l'en-tête) même si le texte est identique | `TvService.kt:644-650` → `PlayerActivity.transfersChanged` → `HomeScreen.refreshStatus` | réception simulée (PUT `/upload/` à 4 Mo/s par la boucle locale) : pas d'écart mesurable sur l'émulateur (1 transfert) ; coût réel attendu avec la clé USB exFAT lente | OUI (cadence seulement, contenu inchangé) : `Throttle(1000)` + `StateGate` ; un appui sur la puce (révéler le code) passe tout de suite ; rien n'est calculé tant que l'accueil est couvert |
| H1 | Pas de réseau : sondes / mDNS / DNS / sockets en boucle | `TvService.kt:448-496` : sonde seulement sur test manuel, `netProbe` ou tunnel accepté ; sinon `NET_CAPABILITY_VALIDATED` toutes les 60 s + rappels `ConnectivityManager` (`watchNetwork`) ; `TvConnect` toutes les 35 s hors fil principal | accueil réseau coupé (`svc wifi/data disable`) = réseau actif : 58,1 vs 58,3 images/s, fil principal 0,99 vs 0,93 s / 20 s | ÉCARTÉE (rien à corriger) |
| H2 | Bluetooth (boucles d'accept, découverte, registre de confiance) | `BtServer.kt:93-108`, `BtGatewayHost.kt:46`, `OwnerBtHost.kt:46` : `accept()` bloquants, veille de 5 s par connexion ; `TrustRegistry.list()` en mémoire | sous Jeux après correctif : 20 ms/min de fil principal au total (Bluetooth virtuel de l'émulateur actif) | ÉCARTÉE sur l'émulateur ; le vrai Bluetooth de la TV n'est pas mesurable ici |
| H4 | Rescans de la clé (`rescanAsync`, test de vitesse) | `TvService.kt:239` au démarrage, sur évènements de montage et `storageTick` 15 s hors fil principal | pas de clé sur l'émulateur | NON MESURÉ (TV réelle) |
| — | `/api/info` enrichi interrogé par le téléphone, écritures de la file persistante, notifications de progression 1/s | — | téléphone non relié (la TV n'a pas de Wi-Fi) | NON MESURÉ ; inchangé |

Les garanties restent : lecture fluide pendant la copie (R-06/R-08 : `PlaybackPriority`, priorités des fils, caches libVLC non touchés ; la copie n'est
pas bridée), file de copie (R-09 non touchée), confiance / PIN inchangés, contenu de la puce d'état inchangé (seule sa cadence de rafraîchissement l'est).
Le Quiz n'est pas bridé : seul son décor de fond est redessiné moins souvent ; minuteur, sons, réponses et réseau de jeu inchangés.

## 2. Mesures (émulateur, mêmes données : 12 vidéos, 46 lots Langues)

Commande : `tools/perf/tv_navigation_bench.sh -s emulator-5580 --label <nom> [--nonet] [--receive] [--screen current --idle 60]`
(20 s de repos puis 200 appuis D-pad droite/bas/gauche/haut ; CPU = utime+stime de `/proc/<pid>/task/*`).

| Scénario | Avant (0.14.21 debug) | Après |
|----------|-----------------------|-------|
| Accueil au repos, réseau actif | 58,3 images/s ; fil principal 930 ms ; RenderThread 4 900 ms (sur 20 s) | 4,3 images/s ; 100 ms ; 340 ms |
| Accueil au repos, réseau coupé | 58,1 images/s ; 990 ms ; 4 710 ms | 4,3 images/s ; 120 ms ; 380 ms |
| Accueil au repos, réception en cours (4 Mo/s) | 58,2 images/s ; 920 ms ; 4 660 ms | 5,9 images/s ; 140 ms ; 390 ms |
| Écran Jeux (accueil dessous), 60 s | 0 image ; fil principal **3 440 ms** | 0 image ; **20 ms** |
| Quiz (écran d'accueil du Quiz), 60 s | 15,9 images/s ; fil principal 2 590 ms ; RenderThread 950 ms ; D-pad p50/p99 21/24 ms | 10,5 images/s ; 330 ms ; 970 ms ; 17/20 ms |
| Accueil D-pad (200 appuis), jank | 1,5 à 7,7 % ; p90 27-42 ms ; p99 32-65 ms | 0,5 à 3,1 % ; p90 22-36 ms ; p99 27-48 ms |

Lecture honnête : les percentiles D-pad de l'accueil varient d'une passe à l'autre (le nombre d'images dépend de l'endroit où finit le focus) ;
les chiffres solides sont les images au repos et le CPU des fils. Pas de « Skipped N frames » notable dans les deux cas (0 à 1 par passe) : l'émulateur
tourne sur un Mac bien plus rapide que le T950 ; sur la TV, la même charge pèse plusieurs fois plus (CPU A55, GPU Mali-G31, 720p, peu de RAM).
StrictMode n'est pas activé dans le build debug : non utilisé. La limitation du CPU de l'émulateur n'étant pas disponible, tout est relatif (avant / après).

Remarques de méthode : le build verrouillé (`-PrequireActivation=true`) bloque l'émulateur sur l'écran d'activation ; les mesures utilisent donc le build
debug non verrouillé, **jamais livré** (les livraisons TV restent verrouillées). L'application a été réinstallée sur l'émulateur (données de l'émulateur
effacées puis re-semées : 12 vidéos de test envoyées par l'API, 46 lots Langues construits depuis `content/langues`). L'émulateur était partagé : une autre
activité (`castbridge.sender`) est passée au premier plan une fois pendant la session ; les passes retenues ont l'écran voulu au premier plan (captures vérifiées).

## 3. Ce qui a changé (petits changements, aucun écran ni fonction nouvelle)

- `android/core/.../ux/UiPace.kt` (nouveau, pur) : `Throttle`, `StateGate`, `SlowZoomCurve`, `StagePace` ; tests `UiPaceTest` (7, **rouges par assertion**
  contre une ébauche, verts après).
- `TvCards.kt` `SlowZoom` : pas de 250 ms au lieu d'un animateur infini.
- `HomeScreen.kt` : `pause()/resume()` ; puce recalculée au plus 1/s et repeinte seulement si son texte change ; signature de la liste calculée sur `cb-home`.
- `PlayerActivity.kt` : 3 lignes (`home?.pause()` dans `onStop`, `home?.resume()` dans `onStart`, compte de la tuile Bibliothèque repris de la liste de l'accueil).
  Logique de la puce d'état non touchée (branche `claude/tv-status-signals`).
- `LanguesHub.kt` : nombre de packs mis en cache, oublié à chaque installation / suppression de lot.
- `QuizViews.kt` `StageBackground` : dégradés sans allocation par image, cadence `StagePace`.

## 4. Ce que seule la TV réelle peut confirmer (à tester par le propriétaire)

1. Accueil : la navigation D-pad entre tuiles et cartes doit être nettement plus fluide ; le fond bouge toujours, très lentement (à peine perceptible par à-coups ?
   si un léger saut se voit, le dire).
2. Quiz : menus et réponses plus réactifs ; le fond lumineux tourne toujours (un peu moins souple, voulu).
3. Avec la clé USB branchée et une copie en cours depuis le téléphone : la puce « Réception de … % » continue d'avancer (au plus une fois par seconde).
4. Après l'installation d'un lot Langues, la tuile « Langues » affiche le nouveau nombre.
5. Non mesurable ici : vrai Bluetooth (H2), clé exFAT lente et contrôleur USB partagé avec la puce Wi-Fi (H4), `/api/info` interrogé par le téléphone.
   Si une lenteur reste, lancer sur la TV `dumpsys gfxinfo castbridge.receiver` (images au repos sur l'accueil : attendu ≈ 4/s) et le joindre.
