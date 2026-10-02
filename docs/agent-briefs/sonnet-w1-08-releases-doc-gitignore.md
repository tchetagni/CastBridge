# w1-08 — `docs/RELEASES.md`, `.gitignore` des secrets, `SHA256SUMS`

**Vague 1 · Effort S (≈ 3 h) · Statut PRÊT.** Branche `claude/sonnet-w1-08`. Rapport : `docs/agent-reports/sonnet-w1-08.md`.

## Objectif
1. Écrire le document de release cité partout mais absent.
2. Protéger le dépôt contre un commit accidentel de secrets/artefacts.
3. Un script produit `SHA256SUMS` à côté des APK publiés.

## Pourquoi (preuves)
- `version.properties:2` : « Règles : docs/RELEASES.md » — `ls docs/RELEASES.md` → inexistant ; `docs/coordination/BILAN-2026-10-02.md` point 6 le confirme.
- `.gitignore` : `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `*.log`, `release.pass*`, `.env` racine, `secrets/`, `*.apk`, `*.ab` absents (seuls `backend/.env` et `backend/secrets/` ignorés). Fichier non suivi et non ignoré : `android/core/castbridge-owner-journal.log` (artefact de `OwnerCliTest`).
- `git tag` : vide ; sha256 notés à la main dans `docs/HANDOFF.md` (`ffe31c24…`) ; APK publiés dans `~/CastBridge-release/` et `Download/` de la clé USB.
- Audit : OP-8, SE-14.

## Fichiers possédés
Nouveau `docs/RELEASES.md`, `.gitignore`, nouveau `tools/release/sha256sums.sh`, `version.properties` (**commentaire** seulement, pas les valeurs). **Hors zone** : `docs/HANDOFF.md` (le coordinateur l'alignera), workflows (w1-07).

## Étapes
1. `docs/RELEASES.md` (français), sections : 1 Portée (CastBridge, CastBridge-TV, CastBridge Propriétaire, CastBridge Dev : lesquelles sont publiées) ; 2 Numérotation (SemVer, `-beta`, `versionCode` strictement croissant, variante verrouillée = code+1 et `-verrouillee`, règle `lock.graceStartMs` jamais reculée pour un build publié, `version.properties` seule source, `-Pcastbridge.versionName/Code` pour les builds de test seulement) ; 3 Variantes TV (non verrouillée = dev, verrouillée = `-PrequireActivation=true` + fichier de clés de confiance, builds de test `-test` jamais publiés) ; 4 Pré-requis (`:core:test` + `:sshd:test` verts, 3 instables connus, `verify_vectors.py`, `:core:checkStarterBudget`, `cbvalidate.py check`, HANDOFF à jour, version de consentement télémétrie) ; 5 Construction (commandes exactes de `docs/HANDOFF.md` § 5, ABI `armeabi-v7a` obligatoire, chemins de sortie) ; 6 Signature (emplacement du keystore **par chemin seulement**, qui le détient, sauvegarde, `apksigner verify`, règle « jamais en CI » ; mentionner la migration debug → release comme **décision D12 en attente**) ; 7 Contrôles (`aapt2 dump badging`, installation par-dessus sur la TV de référence, badge d'activation, `SHA256SUMS`) ; 8 Publication (clé USB `Download/` par SSH, `~/CastBridge-release/<app>-<ver>-<abi>.apk` + `SHA256SUMS`, page `/admin/releases` du serveur, tags `tv-x.y.z` / `phone-x.y.z`) ; 9 Journal des versions (tableau initialisé avec TV 0.14.15-beta/56, téléphone 1.2.27-beta/57, propriétaire 0.2.1/3, d'après `version.properties` et `docs/HANDOFF.md` § 2) ; 10 Retour arrière ; 11 Hotfix.
2. `.gitignore` : ajouter les motifs ci-dessus + `/.core-harness/` si absent + `*.ab`. Vérifier qu'aucun fichier **suivi** ne devient ignoré (`git ls-files -i -c --exclude-standard` doit rester vide).
3. `tools/release/sha256sums.sh DIR` : écrit `DIR/SHA256SUMS` (format `shasum -a 256`), compatible macOS/Linux, et vérifie avec `--check`.
4. `version.properties` : remplacer le commentaire « Règles : docs/RELEASES.md » par une phrase qui dit aussi « tags `tv-<ver>` / `phone-<ver>` ».

## Critères d'acceptation
```sh
test -f docs/RELEASES.md && grep -c '^## ' docs/RELEASES.md     # ≥ 11
git ls-files -i -c --exclude-standard | wc -l                   # 0 (aucun fichier suivi n'est désormais ignoré)
git check-ignore -q android/core/castbridge-owner-journal.log secrets/x.pem foo.jks && echo OK
bash tools/release/sha256sums.sh /tmp && cat /tmp/SHA256SUMS | head -1   # fonctionne sur un dossier quelconque
```

## Cas limites
- Ne pas ignorer `tools/activation/*.json` ni `content/**/*.zip` (artefacts voulus).
- `*.key` pourrait masquer un fichier source légitime : vérifier `git ls-files '*.key'` (attendu vide).

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas créer de tag ; ne pas écrire de chemin de secret autre que ceux déjà cités dans `docs/HANDOFF.md` § 6 ; aucune valeur de clé.

## Rapport
`STATUT`, plan du document, sorties des commandes.
