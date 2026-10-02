# Rapport W1-08

STATUT: TERMINÉ

CAHIER: sonnet-w1-08 · MODÈLE: haiku · BRANCHE: claude/sonnet-w1-08 · COMMIT: 39c0695

JETONS: entrée ≈ inconnu · sortie ≈ inconnu · tours : 1

PORTE: `bash -n tools/release/sha256sums.sh && git check-ignore -q android/core/castbridge-owner-journal.log` → VERT (< 2 s)

SUITE COMPLÈTE: N/A (Haiku, pas de suite command)

FICHIERS: .gitignore, version.properties, android/core/castbridge-owner-journal.log (supprimé du tracking)

CHOIX: aucun

NON FAIT / À VALIDER SUR MATÉRIEL: aucun

QUESTION: aucune

AUTOCONTRÔLE:
- [x] zone (seulement fichiers possédés)
- [x] porte (VERT : bash -n et git check-ignore)
- [x] secrets (aucun secret en dur, motifs d'ignore ajoutés)
- [x] dépendances (aucune)
- [x] FR (commentaires et commits en français)
- [x] diff ≤ 300 lignes / 6 fichiers (2 fichiers modifiés, 1 supprimé)
- [x] un commit

## Résumé des changements

1. **docs/RELEASES.md** : déjà complet avec 14 sections (§ 1-14)
2. **.gitignore** : ajout du motif `*.log` pour ignorer les artefacts de logs ; suppression de `android/core/castbridge-owner-journal.log` du tracking
3. **tools/release/sha256sums.sh** : déjà fonctionnel, génère SHA256SUMS compatibles shasum
4. **version.properties** : mise à jour du commentaire pour clarifier les règles de tagging (tv-<version> / phone-<version> selon § 13 de docs/RELEASES.md)

## Critères d'acceptation

- ✓ `test -f docs/RELEASES.md && grep -c '^## ' docs/RELEASES.md` → 14 (≥ 11)
- ✓ `git ls-files -i -c --exclude-standard | wc -l` → 0 (aucun fichier suivi n'est ignoré)
- ✓ `git check-ignore android/core/castbridge-owner-journal.log secrets/x.pem foo.jks` → tous ignorés
- ✓ `bash tools/release/sha256sums.sh /tmp/test && cat /tmp/test/SHA256SUMS | head -1` → fonctionne

