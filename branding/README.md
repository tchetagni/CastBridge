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
- `tools/` - scripts reproductibles (`gen_cameroun_mark.py`, `svg2vd.py`, `gen_app_assets.py`, `gen_tokens.py`, `subset_fonts.py`, `gen_android_icons.py`) ; le guide PDF se régénère avec `tools/build-branding-guide` (racine du dépôt).

## Exports Android

- `export/android/mipmap-*/` - calques d'icône adaptative en PNG (mdpi -> xxxhdpi).
- `export/android/drawable/` - drawables vectoriels : avant-plan, arrière-plan, monochrome (Android 13+).
- `export/android/mipmap-anydpi-v26/` - manifestes `ic_launcher.xml` / `ic_launcher_round.xml`.
- `export/png/` - Play Store 512, bannière TV 320 x 180, favicons.

## Palette (source de vérité)

- Sombre (TV) : fond `#0A0F1E`, surface `#151D37`, primaire `#F5B025`, secondaire `#2E9E6B`, accent `#FF8A3D`.
- Clair (téléphone) : fond `#F7F8FC`, surface `#FFFFFF`, primaire `#946219` (v1.1 ; `#B7791F` reste la teinte de marque pour les grands éléments et icônes), accent `#B05123`, secondaire `#1C7C53`.
- Sémantique : succès `#35C08A` / `#1C7C53`, alerte `#F5B025` / `#9A6500`, erreur `#FF6B6B` / `#C5343A`, info `#6CB6FF` / `#1668C7`.

## Sous-marques

- CastBridge TV - le pont (or / indigo).
- Quiz des Millions - corail `#FF5C39` + ambre `#FFB020` sur vert d'eau `#0B1B1E` (Sora).
- Échecs - vert `#2FA96B` + or `#F2C14E` sur vert forêt `#0C1B14` (Manrope).

## Notes techniques

- Le thème sombre est premier (TV) ; le thème clair sert au téléphone.
- Aucun fond vidéo ni image lourde : uniquement vecteurs et dégradés simples (TV ~ 1 Go de RAM).
- Focus télécommande : anneau `#FFE1A6` 3 dp + échelle 1,04, visible à 3 m.

## Version 1.1 (application aux apps et au serveur)

- Contrastes WCAG AA corrigés dans `design-tokens.json` (voir `contrast.note`) ; le test JVM `BrandContrastTest` (module `:core`) vérifie toutes les paires déclarées dans `contrast.pairs`.
- `python3 branding/tools/gen_tokens.py` génère `BrandTokens.kt` (Kotlin partagé), les couleurs XML des apps et `cb-tokens.css` (pages /admin).
- `python3 branding/tools/gen_app_assets.py` génère les ressources Android (icônes `ic_cb_*`, logos `logo_*`, lanceur, bannière TV, notification, polices).
- `fonts/subset/` : sous-ensembles latin des polices (embarqués dans les apps) ; `tools/build-branding-guide` régénère le PDF.
- Icônes créées : télécommande, sur le téléphone, passerelle Bluetooth, options développeur.

## Marque : casque + Cameroun, sous-titre MBOKO

- Le casque jaune porte la silhouette du Cameroun (nord en haut) entre les écouteurs, sous l'arc ; dégagement >= 6 unités vérifié par le script (`--check`). Jaune `#F5B025` sur fond sombre, `#946219` sur fond clair, une seule couleur sur les monochromes.
- Sous-titre MBOKO : une ligne « MBOKO » (capitales, graisse 700, interlettrage 0,18 em, ~38 % de la ligne 1 ; 47 % sur la bannière TV) juste sous « CastBridge », vert `#2E9E6B` (fond sombre) / `#1C7C53` (fond clair). Absent des icônes (lanceur, notification, favicon, Play Store) : le symbole seul y suffit. Le libellé et la mise en page sont dans la configuration en tête de `tools/gen_cameroun_mark.py` (`WORDMARK`, `LAYOUTS`).
- Petites tailles (<= 96 px : symbole, lanceur, notification, favicon) : contour allégé (36 points) et joint plus épais ; lisible à 48 px, simple forme à 32 px.
- Régénérer : `python3 branding/tools/gen_cameroun_mark.py` (SVG + PNG/ICO, copies /admin du serveur), puis `gen_android_icons.py` et `gen_app_assets.py` (ressources Android). Dépendances : `fonttools`, `pillow`, `rsvg-convert`.

## Sources

- Contour du Cameroun : Natural Earth (domaine public), via github.com/datasets/geo-countries (ODC-PDDL), polygone CMR, simplifié (Douglas-Peucker 0,08 degré, 92 points) dans `tools/cameroun-contour.json` ; lissage Visvalingam-Whyatt dans `gen_cameroun_mark.py`. Mention reprise en commentaire dans chaque SVG concerné.
