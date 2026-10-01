# CastBridge - Identité de marque

Dossier complet de la charte graphique CastBridge (écosystème TV + smartphone).

## Contenu

- `logo/` - logos et pictos en SVG (marque, symbole, verrouillages horizontal/vertical, clair/sombre, monochrome, sous-marques).
- `icons/` - 24 pictogrammes SVG (trait 1,8 dp, `currentColor`), grille 24 x 24.
- `fonts/` - polices Google Fonts libres (licence OFL) : Bricolage Grotesque, Inter, Sora, Manrope.
- `design-tokens.json` - tokens exportables (couleurs, typo, espacements, rayons, élévation, focus, animations, grille). Importable dans Figma (plugin Tokens Studio).
- `guide/CastBridge-charte-graphique.pdf` - guide PDF de la charte (11 pages).
- `mockups/index.html` - maquettes clés TV (1920 x 1080) et mobile (360 x 800).
- `export/` - exports PNG et formats Android.
- `tools/` - scripts reproductibles (`svg2vd.py`, `gen_app_assets.py`, `gen_tokens.py`, `subset_fonts.py`, `gen_android_icons.py`) ; le guide PDF se régénère avec `tools/build-branding-guide` (racine du dépôt).

## Exports Android

- `export/android/mipmap-*/` - calques d'icône adaptative en PNG (mdpi -> xxxhdpi).
- `export/android/drawable/` - drawables vectoriels : avant-plan, arrière-plan, monochrome (Android 13+).
- `export/android/mipmap-anydpi-v26/` - manifestes `ic_launcher.xml` / `ic_launcher_round.xml`.
- `export/png/` - Play Store 512, bannière TV 320 x 180, favicons.

## Palette (source de vérité)

- Sombre (TV) : fond `#0A0F1E`, surface `#151D37`, primaire `#F5B025`, secondaire `#2E9E6B`, accent `#FF8A3D`.
- Clair (téléphone) : fond `#F7F8FC`, surface `#FFFFFF`, primaire `#B7791F`.
- Sémantique : succès `#35C08A` / `#1F8A5C`, alerte `#F5B025` / `#9A6500`, erreur `#FF6B6B` / `#C5343A`, info `#6CB6FF` / `#1668C7`.

## Sous-marques

- CastBridge TV - le pont (or / indigo).
- Quiz des Millions - corail `#FF5C39` + ambre `#FFB020` sur vert d'eau `#0B1B1E` (Sora).
- Échecs - vert `#2FA96B` + or `#F2C14E` sur vert forêt `#0C1B14` (Manrope).

## Notes techniques

- Le thème sombre est premier (TV) ; le thème clair sert au téléphone.
- Aucun fond vidéo ni image lourde : uniquement vecteurs et dégradés simples (TV ~ 1 Go de RAM).
- Focus télécommande : anneau `#FFE1A6` 3 dp + échelle 1,04, visible à 3 m.
