# Guide utilisateur CastBridge (image d'abord, hors ligne)

Un seul fichier : `guide-utilisateur.html` (CSS/JS en ligne, polices système, captures en `data:` URI JPEG, aucune requête externe). Les repères numérotés sont des cadres HTML/CSS posés aux bornes réelles des éléments (`uiautomator dump`), donc nets à toute taille.

## Contenu du dossier

- `guide-utilisateur.html` : le guide final (2,5 Mo).
- `screens/sNN-nom.png` : captures brutes de l'émulateur, dans l'ordre (codes de connexion masqués par des points, voir plus bas).
- `screens/annotations.json` : par capture, le libellé réel de chaque élément annoté, ses bornes en pixels et le numéro d'étape du guide.
- `tools/spec.py` : liste des captures et des éléments à annoter (recherchés par leur texte dans le dump).
- `tools/make_screens.py` : captures brutes + dumps XML vers `screens/` et `annotations.json`.
- `tools/build_guide.py` : `screens/` vers `guide-utilisateur.html` (contrôle aussi que chaque phrase fait 12 mots ou moins).

## Régénérer le guide (sans émulateur)

```
python3 docs/guide-utilisateur/tools/build_guide.py
```

Pillow est nécessaire (`pip install pillow`). Le résultat ne dépend que de `screens/` et du texte de `build_guide.py`.

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
