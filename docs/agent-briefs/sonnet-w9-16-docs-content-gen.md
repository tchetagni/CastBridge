# w9-16 — Documentation : `docs/CONTENT-GEN.md`, entrée HANDOFF, renvois (MEDIA-PIPELINE, LANGUES § 12, MEDIA-POLICY § 5)

**Vague 9d · Effort S (≈ 0,5-1 j) · Modèle : haiku · Statut PRÊT (après w9-11 et w9-13 ; sinon rédiger « à venir » pour les scripts absents).** Source : `docs/coordination/GUIDE-PRODUCTION-LOTS-GENERATIFS-2026-10-02.md`, `SONNET-WAVE9-INDEX.md`, les `README.md` et `--help` des scripts livrés. Branche `claude/sonnet-w9-16`. Rapport : `docs/agent-reports/sonnet-w9-16.md`.

## Objectif
(1) `docs/CONTENT-GEN.md` (≤ 200 lignes, même style que `docs/MEDIA-PIPELINE.md`) : en-tête (statut, décisions du propriétaire D1-D12 et leur état), flux en un schéma, les commandes (`gen.py plan/run/status/allow-network`, chaque script seul), disposition de `work/`, formats techniques (copie du § 1.4 du guide, **une seule source** : renvoyer à `tools/content-gen/config.json`), manifeste de provenance (champs C2), contrôles et seuils (C4, § 4 du guide), remplacement d'une voix synthétique par une humaine, règle « zéro réseau » et comment l'ouvrir (plafond + tarif), limites honnêtes (ce qui exige le Mac, ce que la TV ne fait pas sans w9-14, licences « de mémoire » à dater), tests. (2) `docs/HANDOFF.md` : une entrée datée `2026-10-0x` décrivant la vague 9 (livré, non vérifié, à valider sur matériel, aucun secret), **sans recopier** le guide. (3) Renvois d'une ligne : `docs/MEDIA-PIPELINE.md` (en tête : « chaîne locale libre : voir CONTENT-GEN.md ; même manifeste de provenance »), `docs/LANGUES.md` § 12 (ligne `tools/content-gen/` ; `tts_synth.py` marqué « remplacé par content-gen/tts.py, conservé ») et `docs/MEDIA-POLICY.md` § 5 (une phrase : « un média généré par un modèle est `generated` avec `generator`, `model`, `promptSha256`, `seed` dans sa provenance »).

## Pourquoi (preuves)
- Mémoire du projet : « toujours tenir `docs/HANDOFF.md` à jour, sans secret » ; `docs/HANDOFF.md:198-200` (forme des entrées existantes : branche, livré, tests, non compilé, à valider).
- `docs/LANGUES.md:422-436` (§ 12 : tableau des outils) ; `docs/MEDIA-PIPELINE.md:1-4` (en-tête) ; `docs/MEDIA-POLICY.md:86-88` (§ 5 provenance).

## Fichiers possédés
`docs/CONTENT-GEN.md` (nouveau), `docs/HANDOFF.md` (une entrée), `docs/MEDIA-PIPELINE.md` (une ligne), `docs/LANGUES.md` § 12 (une ligne), `docs/MEDIA-POLICY.md` § 5 (une phrase).

## Étapes
1. Lire les `--help` réels des scripts fusionnés ; ne documenter que ce qui existe ; marquer « à venir (w9-0N) » le reste.
2. Rédiger ; vérifier chaque chemin cité par `test -e`.
3. Relire : aucun prix, aucun nom de voix présenté comme approuvé, aucun secret, français, noms « CastBridge » / « CastBridge-TV ».

## Critères d'acceptation (hors ligne)
```sh
for p in $(grep -oE '`(tools|docs|content|android)/[^`]+`' docs/CONTENT-GEN.md | tr -d '`' | sort -u); do test -e "$p" || echo "MANQUE $p"; done   # aucune ligne MANQUE (hors « à venir »)
wc -l docs/CONTENT-GEN.md   # ≤ 200
grep -n "content-gen" docs/HANDOFF.md docs/MEDIA-PIPELINE.md docs/LANGUES.md docs/MEDIA-POLICY.md | wc -l   # ≥ 4
grep -nE "[0-9]{3,} ?(XAF|FCFA|€|\\$)" docs/CONTENT-GEN.md   # 0
```

## Cas limites
Scripts non fusionnés ⇒ sections « à venir » explicites ; décision du propriétaire non prise ⇒ « en attente (D-n) », jamais une valeur devinée.

## À ne pas faire
Ne pas modifier le guide de coordination ni l'index de vague ; ne pas documenter un backend réseau comme utilisable ; ne pas dupliquer les tableaux de licences (renvoyer à `licenses.json` et `LICENCES-GENERATIF.md`).

## Rapport
`STATUT`, fichiers touchés, chemins vérifiés, sections « à venir ».
