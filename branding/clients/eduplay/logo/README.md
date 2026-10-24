# Logos vectorisés EduPlay et TechnoHub Enterprise

**Vectorisation automatique, à faire valider par TechnoHub Enterprise ; ne remplace pas le fichier maître.**
Les maîtres restent les JPG de `../source/`. Conformément à `../CHARTE.md`, rien n'a été redessiné, recoloré ni redimensionné : les tracés suivent les pixels du JPG.

## Fichiers

| Fichier | Contenu | Taille |
|---|---|---|
| `eduplay-logo.svg` | logo complet EduPlay, fond transparent, viewBox 1254×1254 | 782 Ko (1150 tracés) |
| `technohub-enterprise-logo.svg` | logo THE, fond transparent, viewBox 1280×803 | 299 Ko (396 tracés) |
| `eduplay-logo-render-1024.png`, `technohub-enterprise-logo-render-1024.png` | rendus simples (transparents) à 1024 px de large | |
| `eduplay-logo-on-navy-preview.png`, `technohub-on-navy-preview.png` | rendus sur bleu nuit `#082A59`, 800 px de large | |

## Méthode

1. Détourage (Pillow/numpy/scipy) : EduPlay, pixels quasi blancs (min RGB ≥ 212) reliés au bord = fond, retirés (un pixel de liseré supprimé aussi). Les contreformes blanches des lettres (zone y > 650, petites) sont retirées, car sur fond sombre elles seraient des taches blanches. L'écran blanc de la TV est conservé en blanc (région retrouvée par érosion/dilatation, 51 380 px). THE : tous les pixels sombres (max RGB ≤ 40) sont transparents, fond et contreformes de TechnoHub (o, b, p, e) compris.
2. Vectorisation avec `vtracer` 0.6 (Python 3.13), mode couleur, `hierarchical=stacked`, `mode=spline`, `corner_threshold=60`, `length_threshold=4.0`, `max_iterations=10`, `splice_threshold=45`, `path_precision=1`.
   - EduPlay : `color_precision=8`, `layer_difference=20`, `filter_speckle=8`.
   - THE : `color_precision=8`, `layer_difference=16`, `filter_speckle=6`.
3. Contrôle : rendu `resvg` rapproché du JPG (taille native).

## Fidélité (écart moyen absolu par canal 0-255 ; PSNR)

| Logo | sur blanc | sur bleu nuit |
|---|---|---|
| EduPlay | 3,3 ; 23,3 dB | 2,4 ; 28,3 dB |
| THE | 2,5 ; 24,2 dB | 1,3 ; 33,1 dB |

Le PSNR sur blanc est tiré vers le bas par les contours anti-crénelés, pas par des erreurs de forme.

## Écarts connus

- Les dégradés sont rendus par paliers de couleur (bandes visibles en zoom), pas par de vrais dégradés SVG. Le triangle « play » et les lettres sont fidèles à l'œil à taille normale.
- Le SVG EduPlay (782 Ko) dépasse la cible de 400 Ko : à ≤ 405 Ko (précision 6), les dégradés se transforment en aplats visibles (triangle play plat). Choix : fidélité.
- Halo blanc d'origine autour du dessin retiré (détourage) ; une légère ombre claire sous le téléphone subsiste.
- Les textes (« Apprendre • Jouer • Se Divertir », « par TechnoHub Enterprise », « Votre pont vers le digital. ») sont des tracés, pas de la police ; les petites lettres sont légèrement arrondies. À zoom fort, le contour peut sembler flou.
- THE : l'ombre noire autour de la virgule verte et entre les lettres est devenue transparente (la lettre H/E paraît un peu plus « découpée » sur bleu nuit) ; aucun contour sombre n'a été conservé, car il appartenait au fond.
- Sur bleu nuit, les parties bleu foncé (« Apprendre • Jouer • Se Divertir », « TechnoHub », « Votre pont vers le digital. ») sont presque invisibles : le logo n'a pas de version claire. Il faut la version « claire » demandée au client, à ne pas improviser.
- Le logo EduPlay inclut « THE » et les lignes décoratives ; rien n'a été retiré.

## Candidat pour l'icône compacte (décision du client, rien produit)

Emblème sans mot-symbole (casque de diplômé + TV/play + livre + télécommande + téléphone + personnage) : rectangle d'environ x 360–1110, y 84–670 en pixels du repère 1254×1254 du SVG (≈ 750×586). Il chevauche légèrement « THE » (livre à x ≈ 380, y ≈ 520–650), donc un recadrage propre demande un masquage du « THE » : à valider avec le client.
