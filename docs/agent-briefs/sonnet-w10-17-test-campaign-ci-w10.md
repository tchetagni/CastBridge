# w10-17 — Campagne de test « Œuvres et trois familles » (TV + téléphone + propriétaire) et CI des outils

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT
> **Groupe : W10e-3** (vague W10e) · prérequis : 10a-10d fusionnées · porte : `python3 -m unittest discover -s tools/tests`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 10e · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT.** Conception : DESIGN-W10 § 5, § 8, § 10.4 B, § 12 (risques techniques). Branche `claude/sonnet-w10-17`. Rapport : `docs/agent-reports/sonnet-w10-17.md`. Après 10a-10d (lire les rapports).

## Objectif
Une liste de contrôle d'environ 35 étapes, exécutable par le propriétaire sur la TV de référence (32 bits) et un téléphone, qui prouve le parcours complet des trois familles ; et la CI qui exécute les tests Python de la vague (Studio, catalogues, `livrer.py` simulé, `releve.py`) et rejoue les vecteurs des œuvres.

## Pourquoi (preuves)
- `docs/TEST-CAMPAIGN.md` (w3-14 / w5-22 / w6-22 : format des étapes, scripts `curl`/adb) ; `.github/workflows/tools.yml` (w1-07, w5-24, w6-23) ; `tools/requirements-dev.txt`.
- `tools/shop-test/**` (w5-22, **si présent**) : fumée de la boutique à étendre d'un article `loc-oeuvre-*`.

## Fichiers possédés
`docs/TEST-CAMPAIGN.md` (nouveau § « W10 — Œuvres locales, Langues, Apprendre ensemble »), `.github/workflows/tools.yml`, `tools/requirements-dev.txt`, `tools/shop-test/**` (si présent), `docs/COORDINATION.md` (§ CI : une ligne). **Hors zone** : tout le reste.

## Étapes
1. § W10 de `TEST-CAMPAIGN.md`, étapes numérotées avec « attendu » et « comment vérifier » (commande `curl`/adb quand possible, PIN jamais dans la commande : variable) : (1-5) préparation : build TV verrouillé faisant confiance à la clé de bureau réelle, catalogue d'exemple signé, APK installé, télémétrie « statistiques » acceptée sur une TV et refusée sur l'autre (ou profil) ; (6-10) aperçus : `livrer.py apercus`, tuile « Œuvres » visible en essai, aperçu lu, mémoire mesurée, aperçu -16 invisible sous profil enfant -12 ; (11-18) location : `livrer.py location` d'une œuvre audio puis d'une chaîne, lecture, compte à rebours, reprise de position, retrait de la clé USB en lecture, redémarrage de la TV (œuvres toujours là), horloge +31 j ⇒ balayage (aperçu conservé), `GET /api/oeuvres` cohérent ; (19-24) téléphone (si w10-11) : catalogue, onglets trois familles, fiche, aperçu local, « il manque X Mo », livraison d'un aperçu par Bluetooth ; (25-29) avec W5 (si fusionné) : commande `loc-oeuvre` avec bon de test ⇒ lot scellé ⇒ livraison ⇒ reçu ; achat `achat-pack-langues` ⇒ ligne `purchase` ; (30-33) serveur : import d'une soumission, doublon, revue, relevé du mois, retrait ⇒ ordre `work.takedown` relayé ⇒ œuvre retirée sur la TV ; (34-35) télémétrie : `work_play` reçu seulement de la TV consentante ; aucun titre dans les événements.
2. `tools.yml` : jobs `python3 -m unittest discover -s tools/tests -p 'test_producer_studio.py'`, `test_livrer.py`, `test_releve.py`, `tools/trial-edition` ; `verify_vectors.py` rejoue `works-vectors.json` **si** w10-05/w5-05 ont ajouté son support (sinon noter) ; ffmpeg absent en CI ⇒ tests sautés **doivent rester verts** (vérifier le message de saut).
3. `requirements-dev.txt` : rien de nouveau attendu (bibliothèque standard) ; le confirmer.
4. `tools/shop-test` (si présent) : un scénario `loc-oeuvre-<exemple>|30` avec `FakeShopApi`/serveur factice.
5. `COORDINATION.md` § CI : une ligne pour les nouveaux tests.

## Critères d'acceptation
```sh
grep -c '^[0-9]\+\.' docs/TEST-CAMPAIGN.md    # ≥ 35 étapes de plus qu'avant (noter avant/après)
python3 -m unittest discover -s tools/tests    # vert localement
grep -n 'producer-studio\|test_livrer\|test_releve' .github/workflows/tools.yml   # présents
```

## Cas limites
- TV sans clé USB : étapes « clé USB » marquées « non applicable » avec la raison.
- W5/W6 non fusionnés : étapes 25-29 marquées « en attente ».

## À ne pas faire
Pas de commit sur les branches partagées ; aucun PIN ni secret dans les commandes ; ne pas modifier les tests existants ; français.

## Rapport
`STATUT`, nombre d'étapes, ce qui n'a pas pu être exécuté en CI, questions.
