# Intentions de montage — Astra, 29 octobre 2026

Aucun nouvel appel image_gen : réemploi des deux illustrations Astra déjà générées pour ces packs, copiées dans `sources/`. Les prompts exacts d’origine sont archivés dans `sources/PROMPTS-ILLUSTRATIONS.md` (ce document historique décrit aussi une troisième image non utilisée ici).

## Consignes exécutées par le montage déterministe

- **Salutations** : illustration de rencontre intacte, fondu d’entrée de 0,6 s ; trois cartes successives Bonjour / Merci / Au revoir, chacune 3 s. Ni déformation, ni mouvement corporel inventé. Le geste fixe est un support de vocabulaire, pas une scène jouée.
- **Tons** : grille 1–5 ; courbes schématiques 55, 35, 214, 51 tracées successivement ; segments calculés, jamais générés par image_gen. Chaque écran dure 3,6 s : pause de 0,4 s, tracé de 1,65 s, maintien de 1,55 s. Dernier écran : ton neutre bref et contextuel, sans hauteur universelle. Schéma du troisième ton en isolation, pas une mesure acoustique.
- **Nombres** : cahiers séparés, rectangles et traits vectoriels rasterisés par Pillow ; un nouvel objet toutes les 3 s. Deux séquences 1–5 et 6–10, la seconde reprend avec six objets visibles. Chiffres, caractères et pinyin composés par polices système. Aucun geste chinois de main.
- **Famille** : six cartes lexicales de 3 s ; image familiale globale conservée comme contexte. Aucun lien de parenté attribué automatiquement aux personnages. Aînesse explicitée par rapport à « moi », seconde syllabe neutre indiquée.

Palette crème, bleu-vert, terre cuite ; zones de sécurité de 42 px horizontalement ; éléments principaux 32–74 px. Textes secondaires 18–29 px à contrôler à distance. Images sources en 3:2 conservées sans déformation.

## Fabrication reproductible

Depuis la racine du dépôt :

```sh
python3 work/astra-zh-a0-video/sources/render.py
```

Dépendances utilisées : Python 3, Pillow 12.3.0, ffmpeg/ffprobe 9.0.2 à `/opt/homebrew/bin/`, encodeur libx264. Polices macOS : Hiragino Sans GB W3 pour les caractères chinois, Arial pour le français et le pinyin. Les fichiers de polices ne sont pas redistribués. Sur une autre machine, adapter leurs chemins en conservant tous les glyphes et vérifier à nouveau les aperçus.

Le script dessine les primitives géométriques directement en trames RGB, les transmet à ffmpeg, vérifie les sorties avec ffprobe et un décodage complet, puis extrait les aperçus des MP4 compressés. Aucune dépendance aux packs après copie des illustrations. Les courbes et cahiers ont pour source le code géométrique du script, pas des SVG intermédiaires. Le rendu est déterministe sur cet environnement ; les octets encodés peuvent varier avec les versions de Pillow, des polices et de x264.

Encodage : H.264, 854×480, 24 i/s, yuv420p, CRF 26, plafond 480 kbit/s, tampon 960 kbit/s, `+faststart`, sans piste audio. Aucun audio, voix synthétique ou voix macOS produit.

## Provenance

Salutations et famille : **synthétique (Astra, image_gen + ffmpeg)** ; image_gen désigne les illustrations antérieures réutilisées, pas une génération vidéo.

Tons et nombres : **synthétique (Astra, dessin déterministe + ffmpeg)**. La mention globale demandée « synthétique (Astra, image_gen + ffmpeg) » décrit la livraison multimédia, mais ne doit pas faire croire que ces trois vidéos géométriques ont nécessité image_gen.

Toutes les sorties : **bêta : non validé**.
