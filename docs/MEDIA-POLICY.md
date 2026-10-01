# Politique et schéma des médias (illustrations, animations, images, audio, clips)

> Complète [CONTENT-ARCHITECTURE](CONTENT-ARCHITECTURE.md) (règle B : un média appartient à **exactement un** lot). Contrôles : `python3 tools/content-media/check_media.py` (manifeste) et `python3 tools/content-budget/content_budget.py` (tailles, figures et animations intégrées).

## 1. Les cinq types

| Type | Format | Où | Budget | Lot |
|---|---|---|---|---|
| **figure** | figure vectorielle existante (`Figure` / `Scene`, JSON dans la leçon) ou fichier `.json`/`.svg` autonome | dans la leçon ou la bibliothèque | **≤ 8 Ko** par figure | lot de la leçon (TV compris) |
| **animation** | animation vectorielle à images-clés (JSON, nouveau ; moteur : agent « animation-engine ») | dans la leçon (clé `animation`) ou fichier `.json` | **≤ 40 Ko** par animation | lot de la leçon (TV compris) |
| **image** | WebP | fichier | **≤ 120 Ko** et **≤ 1 280 px** (grand côté) | **lot média** `<classe>-media` (téléphone) |
| **audio** | Opus (voix) | fichier | **≤ 200 Ko par 30 s** | lot média |
| **clip** | vidéo courte, 480p H.264 (MP4) ou WebM, **avec affiche** (`poster`, WebP) | fichier | **≤ 15 Mo** et **≤ 30 s** | lot média |

Une TV n'a donc jamais d'image, d'audio ni de clip dans un lot compatible TV : seulement du texte, des figures et des animations vectorielles, ensemble sous **3 Mo** par lot. Un lot média est **facultatif et jamais requis** : la leçon doit rester complète sans lui. Le téléphone garde au plus 100 Mo de lots en tout (la somme des lots d'un parcours est contrôlée par `content-budget`).

## 2. Où se déclarent les médias

- **Figure / animation intégrées à une leçon** : dans le JSON de la leçon, non listées dans le manifeste ; mesurées par `content-budget` (échec au-delà de 8 Ko / 40 Ko).
- **Tout fichier** (image, audio, clip, figure ou animation autonomes) : **`content/MEDIA-MANIFEST.json`**, un objet par fichier. Un fichier média non déclaré est une erreur.
- Une leçon ou un exercice **référence** un média par son id dans le champ additif `media: ["img-cm2-marche-1"]` (ignoré par les anciens lecteurs). Un id absent du manifeste est une erreur.

### Manifeste (`content/MEDIA-MANIFEST.json`)

```json
{"format":1,"assets":[{
  "id":"img-cm2-marche-1", "path":"learn/cep-maths/media/marche.webp", "kind":"image",
  "origin":"generated", "generator":"tools/media-gen/marche.py", "licence":"CastBridge-original",
  "author":"agent-media-primaire", "sha256":"…", "bytes":48212, "width":960, "height":640,
  "lot":"learn:cm2-media", "lessons":["cep-maths-fractions"],
  "alt":{"fr":"Une vendeuse au marché partage un régime de bananes en quatre parts égales.","en":"A market seller shares a bunch of bananas into four equal parts."}}]}
```

| Champ | Règle |
|---|---|
| `id` | `[a-z0-9][a-z0-9._-]{2,80}`, unique |
| `path` | relatif à `content/`, sans `..` ; extension cohérente avec `kind` |
| `kind` | `figure` \| `animation` \| `image` \| `audio` \| `clip` |
| `origin` / `licence` | `original` ou `generated` → `CastBridge-original` ; `cc0` → `CC0-1.0` **et** `sourceUrl` ; `generated` exige `generator` (le script qui produit le fichier) |
| `author` | l'agent (ou la personne) auteur |
| `sha256`, `bytes` | exacts : le vérificateur les recalcule |
| `lot` | `feature:scope` ; image/audio/clip **uniquement** dans un scope de type média ; le scope doit exister dans `content/graph/scopes.json` |
| `lessons` | ids des leçons qui l'utilisent (vérifiés) |
| `alt` | `{fr,en}` **obligatoire** pour figure, animation, image, clip |
| `caption` | légende / sous-titres obligatoires pour audio et clip ; `transcript` (texte intégral) obligatoire pour audio |
| `durationS` | obligatoire pour audio et clip ; `height` ≤ 480 pour un clip ; `poster` obligatoire pour un clip |
| `reducedMotion` | obligatoire pour animation : `"first-frame"` ou chemin d'une figure statique |
| `flashHz` | obligatoire pour animation et clip : clignotements par seconde, **≤ 3** |
| `depictsPerson` | si vrai : refusé (aucune photo de personne identifiable) |

## 3. Le créneau JSON de l'animation (contrat pour l'agent « animation-engine »)

Les lecteurs actuels **échouent** sur un type de bloc inconnu (`LessonJson.block`). L'animation s'ajoute donc **sans nouveau type de bloc** : un bloc `illustration` existant reçoit une clé `animation` que les anciens lecteurs ignorent ; sa `figure` statique sert de **repli** (anciens lecteurs, mouvement réduit, TV lente, capture d'écran).

```json
{"type":"illustration","figure":{ "…figure statique ≤ 8 Ko…" },
 "alt":"Le curseur glisse de 0 à 1 : la fraction 3/4 se remplit par quarts.","caption":"Fractions sur la droite graduée",
 "animation":{
   "v":1, "w":400, "h":260, "duration":6.0, "fps":12, "loop":false, "autoplay":false,
   "scene":[ {"id":"bar","t":"rect","x":20,"y":120,"w":360,"h":30,"fill":"blue"} , {"id":"cur","t":"circle","cx":20,"cy":135,"r":12,"fill":"orange"} ],
   "keys":[ {"target":"cur","prop":"cx","at":[[0,20],[2,110],[4,200],[6,290]],"ease":"inOut"},
            {"target":"bar","prop":"w","at":[[0,0],[6,270]]} ],
   "steps":[ {"at":2,"text":"1/4"},{"at":4,"text":"2/4"},{"at":6,"text":"3/4"} ],
   "reducedMotion":"last-frame", "flashHz":0}}
```

- `scene` réutilise **les formes de `Figure.Shapes`** (palette nommée, coordonnées de la figure) avec un `id` pour les animer.
- `keys` : propriétés animables (`x`, `y`, `cx`, `cy`, `w`, `h`, `r`, `rotate`, `scale`, `opacity`, `d` pour un tracé de même structure), interpolation `linear` | `inOut` | `step`.
- `steps` : sous-titres synchronisés (lus aussi par la synthèse vocale) ; `reducedMotion` : image à montrer si l'utilisateur a réduit les animations (`first-frame` | `last-frame` | `figure` = la figure statique du bloc) ;
- `autoplay:false` par défaut (OK / bouton pour lancer sur TV) ; pas de lecture en boucle infinie sans commande de pause.
- **Budgets** : `animation` ≤ **40 Ko** (JSON compact), ≤ 30 s, ≤ 15 images/s, ≤ 60 objets ; toute information portée par l'animation figure aussi dans `alt` et dans les `steps`.
- Le moteur peut **étendre** le schéma de façon additive ; il ne retire ni ne renomme ces clés. Le validateur de budgets mesure déjà toute clé `animation`.

Image, audio et clip sont référencés par `media: ["id"]` sur la leçon, l'exercice ou le bloc `illustration` (jamais comme nouveau type de bloc).

## 4. Accessibilité (obligatoire partout)

- **Texte alternatif** : `alt` en français et en anglais pour tout média visuel ; décrit **ce que l'image enseigne**, pas son apparence ; jamais « image de… ».
- **Audio** : transcription intégrale (`transcript`) et légende ; **clip** : sous-titres (`caption`) ; voix claire, débit lent pour les enfants.
- **Mouvement réduit** : toute animation a un repli statique (`reducedMotion`) ; l'utilisateur qui a réduit les animations (réglage système) ne voit jamais de mouvement automatique.
- **Pas de clignotement** au-dessus des seuils de sécurité : ≤ **3 flashs par seconde** (WCAG 2.3.1), surfaces clignotantes petites ; déclaré dans `flashHz`.
- **Couleur** : jamais la seule porteuse de sens (✓/✗, motifs, étiquettes) ; palette de la charte testée pour les contrastes ; compatible daltonisme (éviter rouge/vert seuls).
- **Lisibilité à 3 m sur TV** : textes des figures ≥ 18 px de la grille 1280×720 ; aucune étiquette ne recouvre une autre (test existant).
- **Dyslexie** : polices sans empattement, interlignage généreux, pas de texte justifié ni de texte incrusté dans les images.

## 5. Provenance et licences

Seuls sont admis : des médias **ORIGINAUX** (créés pour CastBridge), **générés par programme** (le script est versionné et listé dans `generator`, reproductible), ou **CC0** (avec `sourceUrl` et date). **Interdits** : récupération automatique de pages (scraping), images protégées par le droit d'auteur, logos et marques d'autrui, **photos de personnes identifiables** (enfants compris), voix d'une personne réelle sans accord écrit. Les personnages sont des dessins ; les noms et situations sont d'ici (Cameroun) sans identifier une personne réelle. Tout asset est dans `MEDIA-MANIFEST.json` avec auteur, origine, licence, empreinte, taille, lot et leçons.

## 6. Contrôles automatiques

`check_media.py` échoue sur : fichier absent, empreinte ou taille différente, extension incohérente, dépassement de budget de son type (octets, pixels, durée, hauteur), WebP illisible, alt/légende/transcription manquants, animation sans `reducedMotion` ni `flashHz`, `flashHz` > 3, origine ou licence refusées, `sourceUrl` / `generator` manquants, `depictsPerson`, lot invalide ou média lourd hors lot média, leçon inconnue, média référencé mais non déclaré, média présent mais non déclaré. `content_budget.py` échoue sur : figure > 8 Ko, animation > 40 Ko, clip ou audio dans un lot TV, lot TV > 3 Mo. Tests : `tools/tests/test_content_tools.py`.
