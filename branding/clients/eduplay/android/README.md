# Superposition Android EduPlay (première itération de démonstration, débogage)

Ressources posées par `-Pbrand=eduplay` par-dessus `src/main/res` (voir `docs/WHITE-LABEL.md`). Aucun code partagé n'a été modifié. Les PNG et polices sont produits par `../tools/gen_android_overlay.py` (resvg-py, Pillow, fontTools) à partir de `../logo/eduplay-logo.svg` et de Poppins (OFL, Google Fonts).

## Ce qui est superposé

- **Couleurs XML** (`values/eduplay_colors.xml`) : téléphone clair (blanc, texte `#082A59`, primaire `#0750B8`, secondaire vert `#48B800`, accent/focus cyan `#00B8F0`) ; TV sombre (fond `#082A59`, surfaces `#0E3A75` / `#12457F`, primaire `#0750B8` texte blanc, focus cyan `#00B8F0`). Contrastes texte contrôlés (>= 4,5:1 ; focus cyan sur fond TV 6,1:1). TV : styles de dialogue et de thème (`eduplay_themes.xml`, `cb_dialog_bg.xml`) pour que les boutons de dialogue soient cyan lisibles.
- **Polices** : titres en Poppins (sous-ensemble latin) : téléphone, `font/bricolage_grotesque.ttf` remplacé par Poppins Bold (une seule graisse, la police est statique) ; TV, famille `cb_bricolage_grotesque.xml` remplacée (Medium / SemiBold / Bold). Texte courant : Inter, déjà dans le dépôt, inchangé.
- **Icône d'application PROVISOIRE** : adaptative (avant-plan, monochrome, fond blanc) et mipmaps classiques ronds/carrés, tirés de l'emblème du logo (sans « THE » ni mot-symbole), sur fond blanc. À remplacer par la version « icône d'application » du client dès réception. Icône de notification : silhouette blanche de l'emblème. Bannière TV : logo complet sur blanc.
- **Logos dans l'appli** : `logo_castbridge_*` remplacés par le logo EduPlay (PNG). Téléphone : logo seul sur fond clair, sur carte blanche pour la variante « fond sombre ». TV : logo ou emblème sur carte blanche arrondie (les textes bleu foncé du logo sont illisibles sur bleu nuit et la charte interdit de le recolorer). Écran de démarrage : blanc (téléphone) / bleu nuit avec carte (TV).

## Ce qui reste d'aspect CastBridge (non superposable sans toucher au code partagé)

- Couleurs de l'interface téléphone (Compose) : `core/.../brand/BrandTokens` via `sender/.../Theme.kt` (jeux sombre et clair). Le thème par défaut du téléphone est le **sombre** (`ThemePrefs`) ; EduPlay clair demande de choisir Clair dans Réglages ou de changer le défaut. L'or CastBridge reste la couleur primaire dans les deux.
- Couleurs de la TV construites en code (`TvCards.kt`, `TvStyle`, écrans Quiz/Échecs/Cartes/Jeux) : ni ressources ni overlay.
- Sous-marques `logo_apprendre*`, `logo_echecs*`, `logo_quiz_des_millions*`, icônes `ic_cb_*` : laissées (contenu CastBridge, hors charte EduPlay).
- Polices Sora (quiz) et Manrope (échecs) de la TV.
- Identifiants de découverte (UUID Bluetooth, mDNS `_castbridge._tcp`, DIAL) et textes dans des images : voir `docs/WHITE-LABEL.md`, phase 4.
