# Guide utilisateur CastBridge (image d'abord, hors ligne)

Un seul fichier : `guide-utilisateur.html` (CSS/JS en ligne, polices système, captures en `data:` URI JPEG, aucune requête externe : les trois adresses de téléchargement `https://bridge.sti-cm.com/telecharger`, `/dl/tv/latest.apk` et `/dl/phone/latest.apk` ne sont que des liens). Les repères numérotés sont des cadres HTML/CSS posés aux bornes réelles des éléments (`uiautomator dump`), donc nets à toute taille.

## Contenu du dossier

- `guide-utilisateur.html` : le guide final (2,6 Mo, 12 sections : matériel, installer, relier, envoyer, apprendre, jeux, internet, clé USB, parental, problèmes, données, aide ; les ancres `#materiel`, `#installer`… ne changent jamais).
- `screens/sNN-nom.png` : captures brutes de l'émulateur, dans l'ordre (codes de connexion masqués par des points, voir plus bas).
- `screens/annotations.json` : par capture, le libellé réel de chaque élément annoté, ses bornes en pixels et le numéro d'étape du guide.
- `tools/spec.py` : liste des captures et des éléments à annoter (recherchés par leur texte dans le dump).
- `tools/make_screens.py` : captures brutes + dumps XML vers `screens/` et `annotations.json`.
- `tools/build_guide.py` : `screens/` vers `guide-utilisateur.html`. Contrôle que chaque phrase fait 12 mots ou moins (le build échoue en les listant), numérote les sections et les étapes tout seul, et affiche à la fin la liste des illustrations manquantes.

## Régénérer le guide (sans émulateur)

```
python3 docs/guide-utilisateur/tools/build_guide.py
```

Pillow est nécessaire (`pip install pillow`, ou dans un environnement virtuel : `python3 -m venv .venv && .venv/bin/pip install pillow`, puis `.venv/bin/python …`). Le résultat ne dépend que de `screens/` et du texte de `build_guide.py`.

Les blocs de texte du guide (`ns` étapes numérotées, `bl` listes, `say` ce que dit un écran mot pour mot, `qa` message et « Que faire », `card`/`cards`, `ver` repère de version) sont des fonctions de `build_guide.py` ; un repère « TV 0.14.45 ou plus récente · téléphone 1.2.53 ou plus récent » marque une fonction du 2026-10-07. On n'invente pas d'image : quand une capture manque, `miss("…")` laisse un cadre en pointillés avec sa légende.

## Illustrations à ajouter (2026-10-07, 16 légendes laissées dans le guide)

Le guide décrit les évolutions du 2026-10-07 avec les captures existantes (CastBridge 1.2.50, CastBridge-TV 0.14.43) ; les écrans nouveaux n'ont pas encore de capture. Pour chacune : faire la capture (voir plus bas), l'ajouter à `tools/spec.py`, puis remplacer l'appel `miss(…)` par un appel `step(…)` dans `build_guide.py`.

- Activation : écran d'activation de CastBridge-TV (code en très gros, QR code) ; écran « Activer la TV » avec « Code affiché sur la TV » ; boîte d'Android « Se connecter ? » ; « TV trouvée », « Partager la demande », « Copier », « Coller la clé reçue » ; bandeau « Clé USB : activation trouvée pour cette TV › Activer ».
- Ouvrir sur la TV : bouton « Ouvrir sur la TV » de l'onglet « CastBridge TV » et touche « TV » de la télécommande.
- Jeux : liste « Jeux » de la TV avec la Bataille, Fap-Fap et Agraham Tia (les captures 6.1 et 6.2 datent d'avant la Bataille) ; salon de la Bataille (QR code, code à 4 chiffres, places) ; table de la Bataille sur un téléphone ; Échecs › Adversaire : En ligne (Internet) ; Quiz › Partie Internet › « Créer une partie » avec « Mise », « Montant » et le nombre de joueurs de la TV qui misent (games-G5, `docs/QUIZ.md` § 5 bis).
- Internet par le téléphone : « Demande d'Internet au téléphone… » sur la TV ; notification « CastBridge relaie pour <TV> ».
- Clé USB : « vérification par Android… patientez » ; « Préparer le retrait de la clé USB » (MENU) ; guide « Clé illisible » avec « Ouvrir les réglages de stockage ».
- La capture 2.4 (écran « Activer la TV » du téléphone) est celle de la version précédente : à refaire avec le champ « Code affiché sur la TV ».

## Refaire les captures (émulateur arm64 `emulator-5580`)

Réglages de départ : écran en mode TV, `wm size 1280x720` et `wm density 160` (surcharge, pas `wm size reset`). Apps installées en debug depuis ce dépôt :

```
cd android && ANDROID_SERIAL=emulator-5580 ANDROID_HOME=$HOME/Library/Android/sdk \
  bash ../tools/agents/gradle-lock.sh --timeout 5400 gradle --offline \
  -Pkotlin.daemon.jvmargs=-Xmx3g -Dorg.gradle.jvmargs=-Xmx4g :receiver:installDebug :sender:installDebug
```

Si l'installation de `castbridge.sender` échoue (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, signature différente), désinstaller l'ancienne : `adb -s emulator-5580 uninstall castbridge.sender`.

1. Désactiver les animations, sinon `uiautomator dump` échoue (« could not get idle state ») : `settings put global window_animation_scale 0` (idem `transition_animation_scale`, `animator_duration_scale`). Les remettre à `1.0` à la fin.
2. Vidéos de test (ffmpeg) : `ffmpeg -f lavfi -i smptebars=size=640x360:rate=15 -f lavfi -i sine=frequency=440 -t 6 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest Video_de_test.mp4`, puis `adb push` vers `/sdcard/Movies/` (téléphone) et `/sdcard/Android/data/castbridge.receiver/files/videos/` (bibliothèque de la TV).
3. Capture TV : `adb -s emulator-5580 exec-out screencap -p > nom.png`, puis `adb shell uiautomator dump /sdcard/ui.xml` et `adb exec-out cat /sdcard/ui.xml > nom.xml`. Navigation à la télécommande : `input keyevent 19/20/21/22/23/4`.
4. Capture téléphone : `wm size 1080x2400; wm density 420`, ouvrir `castbridge.sender`, capturer, puis TOUJOURS restaurer `wm size 1280x720; wm density 160`.
5. Relier les deux apps sur l'émulateur : sur le téléphone, onglet « CastBridge TV », « Ma TV n'apparaît pas », cocher la case, IP `127.0.0.1`, code de la TV (champ masqué), touche « Rechercher » : « SUR LA TV » apparaît.
6. Poser tous les `nom.png` et `nom.xml` dans un dossier, puis `python3 tools/make_screens.py --raw ce/dossier` et `python3 tools/build_guide.py`.

## Limites connues

- L'onglet « Parental » du téléphone est protégé (FLAG_SECURE) : la capture est noire, il n'est donc pas montré.
- Le code de connexion de la TV est affiché en clair sur la tuile « Recevoir du téléphone » et dans son aide : `make_screens.py` le remplace par des points (zones dans `tools/spec.py`).
- Les captures de la fenêtre Bluetooth (« Allow » / « Deny ») sont en anglais : c'est une fenêtre d'Android, pas de CastBridge.
