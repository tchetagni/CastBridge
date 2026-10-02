# w10-04 — Registre de contenu des œuvres, bouquets `oeuvre-*`/`chaine-*`/`pack-langues-*`, catalogue des œuvres signé, construction des lots

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10a-4** (vague W10a) · prérequis : w10-01 · porte : `python3 -m unittest discover -s tools/trial-edition && python3 -m unittest discover -s tools/tests -p 'test_content_tools.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 2.2, § 4.5, § 5.3, § 13 (ligne w5-06). Branche `claude/sonnet-w10-04`. Rapport : `docs/agent-reports/sonnet-w10-04.md`. Dépend de w10-01 (texte canonique du catalogue des œuvres).

## Objectif
Le contenu des œuvres a un **registre** (`content/oeuvres/`), ses lots sont **ramassés** par la chaîne de publication (`build_lots.py`), ses **bouquets** entrent dans le catalogue des bouquets signé (durées de `bundles-rental.json`), le bouquet `tout` **exclut** les œuvres, et un **catalogue des œuvres** signé (`castbridge-works-catalog-v1`) est produit par `trial_edition.py sign-works-catalog` avec la même clé que les autres catalogues.

## Pourquoi (preuves)
- `tools/trial-edition/trial_edition.py:556` (`bundles(inv, aliases)`), `:582-600` (durées), `:790-800` (`canonical_bundles`, `sign_catalog`) ; `content/bundles-rental.json` (`default: 30`).
- `tools/content-lots/build_lots.py` (`collect` : learn, quiz, médias du manifeste) : ne connaît pas `oeuvre`.
- `content/learn/lots.json` : registre de versions (`format: 1`, `lots: {scope: {version, hash, date}}`) ; `docs/FREE-CONTENT.md` § 2 : aucune liste `free`/`reserved` aujourd'hui.
- `C/lots/RentalPolicy.kt:13-18` : `LotFamilies.explicit(free, reserved)`.

## Fichiers possédés
Nouveaux `content/oeuvres/registry.json`, `content/oeuvres/lots.json`, `content/oeuvres/README.md`, `content/oeuvres/exemple/**` (une œuvre **synthétique** : audio `sine` 20 s + aperçu 8 s + couverture générée, ≤ 300 Ko au total, `work.json` à la main) ; `tools/trial-edition/trial_edition.py`, `tools/trial-edition/test_trial_edition.py`, `tools/content-lots/build_lots.py`, `tools/tests/test_content_tools.py`, `content/bundles-rental.json`, `tools/free-content/license-tags.json` (commentaire seulement). **Hors zone** : `tools/producer-studio/**` (w10-03), code Android/serveur.

## Étapes
1. `content/oeuvres/registry.json` : `{"format":1, "producers":[{"slug","name","bio","zone"}], "works":[{"id","producer","kind","langs","durationS","rating","license","title","synopsis","coverSha256","lotVersion","teaserVersion","rentalDays":30,"variants":[7]}]}` ; `lots.json` : `{"format":1, "free":[], "reserved":["oeuvre:<id>", …], "lots":{…versions…}}` (listes **explicites** : une œuvre réservée est dans `reserved`, une œuvre CC BY-SA dans `free` ; les deux ⇒ erreur).
2. `trial_edition.py` : `--works content/oeuvres/registry.json` (défaut si le fichier existe) ⇒ bouquets `oeuvre-<id>` (type `oeuvre`, lots `oeuvre:<id>`, `rentalDays` de `bundles-rental.json` ou du registre), `oeuvre-<id>-7j` par variante (`rentalDays: 7`), `chaine-<producteur>` (type `chaine`, tous les lots **réservés publiés** du producteur, 30 j), `pack-langues-<code>` (type `pack`, lots `langmedia:<code>-*` réservés s'ils existent ; sinon bouquet **vide autorisé avec `service: true`** : à confirmer avec w10-12) ; le bouquet `tout` **exclut** `oeuvre:*` et les œuvres ne comptent pas dans le plafond d'essai (100 Mo) ; `select` échoue si une œuvre réservée n'a pas d'aperçu `-trial` listé.
3. `trial_edition.py sign-works-catalog --registry … --key PEM --out works-catalog.json` : texte canonique **identique** à `SignedWorksCatalog.canonicalPayload` (w10-01 ; copier la spécification du cahier w10-01 étape 2) ; `--check FICHIER` revérifie ; `generatedAt` ISO Z ; refuse un registre dont une œuvre a `rating == 18` ou un id > 26 caractères.
4. `build_lots.py` : ramasse `content/oeuvres/dist/castbridge-lot-oeuvre-*.lot` (lots déjà construits par le Studio, **non reconstruits** : leur empreinte entre dans `LOT-VERSIONS.json` sous `oeuvre:<scope>`) ; `--bump` s'applique ; les lots d'aperçu sont marqués `edition: trial` dans le catalogue (ligne `|trial`, `LotManifest.canonicalPayload`).
5. `bundles-rental.json` : documenter `"oeuvre-<id>-7j": 7` ; test : bouquet `-7j` → 7 j.
6. Tests : registre d'exemple ⇒ bouquets attendus ; `tout` sans œuvre ; catalogue signé rejoué par `Work`/`SignedWorksCatalog` (ajouter le JSON produit comme **vecteur** `works-catalog-from-tool` dans `tools/activation/works-vectors.json` **seulement si** w10-01 est fusionné ; sinon le signaler).

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/trial-edition && python3 -m unittest discover -s tools/tests -p 'test_content_tools.py'   # vert
python3 tools/trial-edition/trial_edition.py select --only learn,quiz --works content/oeuvres/registry.json && grep -c '"oeuvre-' content/TRIAL-MANIFEST.json   # ≥ 1 (ou --draft si le manifeste réel échoue sur Langues : noter)
python3 tools/trial-edition/trial_edition.py sign-works-catalog --registry content/oeuvres/registry.json --key /dev/null 2>&1 | grep -q 'clé'   # refus propre sans clé
git diff --stat content/TRIAL-MANIFEST.json   # ne pas committer un manifeste réel modifié sans décision (rapport)
```

## Cas limites
- Producteur sans œuvre publiée : pas de bouquet `chaine-*`.
- Œuvre libre : jamais dans un bouquet louable ; listée dans le catalogue des œuvres avec `license=CC-BY-SA-4.0`.
- `TRIAL-MANIFEST.json` réel : `select` échoue aujourd'hui sur les Langues vides (TRIAL-EDITION § 0) : utiliser `--only learn,quiz` ou `--draft` ; ne pas « corriger » les Langues ici.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas modifier `canonical_bundles` ; ne pas signer avec une clé réelle ; ne pas toucher `content/learn`, `content/langues`, `content/quiz` ; aucun média réel de producteur dans le dépôt.

## Rapport
`STATUT`, bouquets produits pour l'exemple, texte canonique du catalogue des œuvres (copie), questions (dont `pack-langues` vide).
