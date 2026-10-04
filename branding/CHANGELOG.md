# Historique de la charte CastBridge

## Charte v1.2 (2026-10-04)

Décisions du propriétaire du 2026-10-04, documentées dans le guide PDF (11 -> 24 pages).

- Logo : la silhouette du Cameroun (Natural Earth, domaine public, 92 points, `tools/cameroun-contour.json`) entre les deux écouteurs du casque jaune, sous l'arc ; toutes les variantes (horizontal, vertical, clair, sombre, monochromes, icône 512, adaptatif avant-plan / arrière-plan / monochrome, TV et téléphone, bannière 320 x 180, favicon, notification). Dégagements mesurés : 11 des écouteurs, 10 de l'arc, minimum 6. Petites tailles : contour allégé (36 points), reconnaissable à 48 px, distinct à 32 px.
- Mot-symbole : « CastBridge » (téléphone) et « CastBridge-TV » (TV) avec le sous-titre « MBOKO » juste dessous (capitales, graisse 700, interlettrage 0,18 em, 38 % de la ligne 1, vert `#2E9E6B` sur sombre, `#1C7C53` sur clair, une couleur sur les monochromes) ; absent du lanceur, de la notification, du favicon, de l'icône Play Store et de l'en-tête de l'accueil TV (variante compacte, 56 dp). Libellé du lanceur et identifiants d'application inchangés.
- Couleurs signalétiques des pastilles d'état : noir (fond noir + anneau clair, 3:1 minimum), vert, orange, rouge, bleu, gris ; toujours avec l'icône, le libellé français et le mot d'état. Tableau des règles et seuils par pastille.
- Accueil TV en 5 boutons de groupe ouvrant une grille de 3 colonnes ; règles UX (libellé français sur toute icône, contraste >= 4,5:1, zone de sécurité 5 %, texte >= 28 sp, icônes >= 40 dp, focus `#FFE1A6` 3 dp, échelle 1,04, télécommande à 5 touches, barre du lecteur).
- Nouvelles pages du guide : construction et dégagements, mot-symbole et MBOKO, couleurs signalétiques, accueil TV et règles UX, à faire / à ne pas faire, sources et licence du contour ; sommaire renuméroté (18 chapitres).
- Pages corrigées : logo (l'ancien « pont-arc entre deux écrans » et les tailles minimales), architecture de marque, couleurs sémantiques (noir et gris ajoutés), typographie (minimum 28 sp sur TV), sous-marques, livrables, pied de page v1.2.
- `mockups/index.html` : logo compact, pastilles d'état à 6 couleurs, boutons de groupe, grille « Médias ».
- `tools/build-branding-guide` : nouvelles pages, import de `gen_cameroun_mark.py` pour les dégagements, dépendance Pillow.
- Aucun token modifié (`design-tokens.json` inchangé) : `gen_tokens.py` non relancé.
- Non couvert : captures d'écran du chapitre 15 antérieures au 2026-10-04 (ancien logo, ancien accueil) ; lisibilité réelle sur une vraie TV non vérifiée.

## Charte v1.1 (2026-10-01)

Contrastes WCAG AA corrigés, icônes complétées, application aux apps et au serveur.

## Charte v1.0

Palette, typographie, logo (pont), icônes, sous-marques.
