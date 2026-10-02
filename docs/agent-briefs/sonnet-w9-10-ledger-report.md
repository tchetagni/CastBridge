# w9-10 — Grand livre des coûts et temps (`ledger.py`) et rapport de provenance / crédits (`report.py`)

**Vague 9c · Effort S (≈ 0,5-1 j) · Modèle : haiku · Statut PRÊT.** Conception : guide § 2.2, § 6.1, § 7 (points 2, 6) ; contrats C1, C2. Branche `claude/sonnet-w9-10`. Rapport : `docs/agent-reports/sonnet-w9-10.md`.

## Objectif
(1) `ledger.py` : un CSV `work/ledger.csv` à ajout seul, une ligne par étape exécutée : `ts, stage, backend, fingerprint, seconds, chars, images, videoSeconds, cost, currency, pricingRef` ; `cost` = **0** pour tout backend local ; pour un backend réseau, calculé **uniquement** depuis un `pricing.json` fourni (format de `tools/media-pipeline/templates/pricing.json`), sinon `cost = ""` et `pricingRef = "inconnu"` (jamais 0 par défaut pour le réseau) ; `ledger.py summary` : totaux par backend et par lot, temps machine total, coût total ou « coût inconnu » ; `ledger.py estimate --requests work/requests.jsonl --backend X` : estimation par excès (caractères, secondes de vidéo) avant exécution. (2) `report.py` : depuis `work/provenance/` et `work/qc/` : `work/out/RAPPORT-PROVENANCE.md` (par lot : nombre d'assets par genre, backends, modèles et versions, voix et licences, nombre `synthetic`, nombre `humanReplacement: pending`, assets rejetés et pourquoi) ; `work/out/<lot>/CREDITS.md` et `CREDITS.json` (attribution par licence : « Voix de synthèse : Kokoro-82M (Apache-2.0), voix zf_xiaobei ; Images : générées par programme (CastBridge, CC BY-SA 4.0) ; Polices : Noto Sans CJK (OFL-1.1) » ; CC BY / CC BY-SA : auteur, source, URL obligatoires) ; `report.py pending-human` : liste des pistes synthétiques à remplacer par une voix humaine, triée par priorité (A0 d'abord, mots avant dialogues).

## Pourquoi (preuves)
- `docs/MEDIA-PIPELINE.md:31-33` (estimation par excès, « aucune dépense sans tarif », reprise comptée) ; `tools/media-pipeline/mp/estimate.py` (lire ; même logique, ne pas modifier).
- `docs/LANGUES.md:246,294` (écran « Crédits » généré depuis `media.json`, chaque auteur cité comme sa licence l'exige) ; `tools/free-content/build_free_archive.py` (`ATTRIBUTION.md` : forme à rester compatible).
- Marquage et remplacement humain : guide § 2.1 point 5.

## Fichiers possédés
`tools/content-gen/ledger.py`, `tools/content-gen/report.py`, `tools/content-gen/tests/test_report.py`.

## Étapes
1. `ledger.py add --stage tts --backend kokoro --fingerprint … --seconds 1.8 --chars 12` (appelé par `gen.py`), `summary`, `estimate` ; verrou de fichier simple (`os.open` O_APPEND) ; CSV avec en-tête stable.
2. `report.py build --work work --out work/out` ; `pending-human` ; Markdown déterministe (tri par lot puis id) ; aucune donnée personnelle, aucun chemin absolu.
3. Tests : ledger avec 3 lignes ⇒ résumé exact ; backend réseau sans pricing ⇒ « coût inconnu » ; estimation par excès (durée max demandée) ; rapport sur 4 enregistrements de provenance fictifs (2 synthétiques `pending`, 1 `done`, 1 image) ⇒ compteurs et crédits attendus ; CC BY sans `author` ⇒ erreur.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_report.py'
python3 tools/content-gen/ledger.py summary --ledger tools/content-gen/tests/fixtures/ledger.csv   # totaux ; « coût inconnu » si une ligne réseau sans tarif
python3 tools/content-gen/report.py build --work /tmp/w --out /tmp/w/out && test -s /tmp/w/out/RAPPORT-PROVENANCE.md
```

## Cas limites
Ledger absent ⇒ créé ; ligne corrompue ⇒ ignorée avec avertissement, jamais de plantage ; provenance sans `qc` ⇒ compté « non contrôlé » ; lot sans asset ⇒ rapport vide mais présent.

## À ne pas faire
Aucun tarif en dur ; aucun appel réseau ; ne pas écrire dans `content/` ; ne pas modifier `tools/media-pipeline` ni `tools/free-content`.

## Rapport
`STATUT`, colonnes du CSV, exemple de `CREDITS.md`, points d'appel attendus de `gen.py`.
