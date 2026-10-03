# Publier les lots Quiz sur le serveur

Règle du propriétaire (2026-10-03) : « publie sur le serveur, 30 % réservé ».

- 93 lots (`content/quiz/lots/catalog-lots.json`). RÉSERVÉ = niveaux L1, L2, L3 et Tle : 25 lots (≈ 27 % des lots, 28 % des questions).
  LIBRE = les 68 autres (publics, téléchargeables).
- `content/quiz/families.json` liste `quiz:<scope>` en `free` / `reserved` ; il est produit par
  `python3 tools/quiz/publish_lots.py --write-families` (déterministe) et commité. Inconnu = non publié (échec fermé).
- Un lot réservé n'est JAMAIS envoyé : le script refuse (code non nul, rien d'envoyé) un lot réservé demandé, un lot absent de
  `families.json`, un lot dans les deux listes, un fichier absent, > 3 Mio ou dont le SHA-256 diffère du catalogue.

## Commandes

```
python3 tools/quiz/publish_lots.py                      # simulation (par défaut), résumé en français
export CASTBRIDGE_ADMIN_TOKEN=...                       # jamais en argument, jamais affiché
python3 tools/quiz/publish_lots.py --apply --limit 1    # canari : un lot
python3 tools/quiz/publish_lots.py --apply --only 2nde-droit
python3 tools/quiz/publish_lots.py --apply              # les 68 lots libres
curl -s 'https://bridge.sti-cm.com/api/v1/lots/catalog?feature=quiz'   # vérification
```

Protocole : `POST /api/v1/admin/lots` (multipart, `publish=false`, sha256 attendu) puis `POST /api/v1/admin/lots/{id}/publish`.
Idempotent : même empreinte déjà publiée = sautée ; envoyée non publiée = seulement publiée ; même version avec autre empreinte = erreur.

## Canari

1. Simulation. 2. `--apply --limit 1`, puis contrôle du catalogue signé et téléchargement d'un lot sur la TV de référence.
3. Le reste. Une version ne change jamais : un contenu modifié passe à la version suivante.

## Les 25 lots réservés

Ils ne sont pas sur le serveur public : chiffrés par TV, ils sont livrés plus tard par la location scellée
(`core/.../lots/RentalPolicy.kt`, docs/RENTAL-LOTS.md § 7, docs/LANGUES.md § 13), à l'achat d'une location.

## Côté serveur

`feature=quiz` est accepté (`LotService.FEATURES`), taille maximale 10 Mo, validation par défaut `ZipLotValidator` (ZIP lisible, pas de
chemin sortant, ≤ 64 Mo décompressés). Pas de validateur Quiz propre : le format (manifest.json, index.json, questions.json) n'est pas contrôlé.
