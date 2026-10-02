# w17-11 — Outil du propriétaire : `tools/pilot/rentreq.py` (lire une demande `castbridge-rent-request-v1` reçue en fichier ou en code court, la vérifier, imprimer la commande `louer.py` W16 correspondante) et rejeu Python des vecteurs

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune (Python, bibliothèque standard) · statut : PRÊT (après w17-03 ; pendant le gel : outils seuls)
> **Groupe : W17b-3** (vague W17b, outils) · prérequis : w17-03 (`store-vectors.json`) ; w16-05 (`tools/pilot/louer.py`) **souhaité** : sinon la commande est imprimée telle que DESIGN-W16 § 3.2 la décrit · porte : `python3 -m unittest tools/tests/test_pilot_rentreq.py && python3 tools/activation/verify_vectors.py --only store`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 17b · Effort S · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W17 § 4.1, § 2.4, § 6 (W16). Branche `claude/sonnet-w17-11`. Rapport : `docs/agent-reports/sonnet-w17-11.md`. Tranche 1 du pilote W16 : le propriétaire exécute les demandes **à la main** ; cet outil lui évite toute saisie.

## Pourquoi (preuves)
- DESIGN-W16 § 3.2 (`louer.py location --tv <code|URL> --bouquet classe-cm2 --choix 6h|7j|defaut --pilote tools/pilot/pilot.json [--prolonger]`) ; `tools/pilot/louer.py` (w16-05, si fusionné : **lecture seule**, une ligne `--demande FICHIER|CODE` proposée au rapport, jamais éditée ici).
- `tools/activation/store-vectors.json` (w17-03 : canonique, code court, refus) ; `tools/activation/verify_vectors.py` (section `store`, w17-03).
- Registre `~/.castbridge-activation/pilot-rentals.csv` (W16 § 3.2) : colonne `tv` = code masqué ; l'outil **n'écrit pas** le registre (c'est `louer.py`).

## Fichiers possédés
Nouveaux `tools/pilot/rentreq.py`, `tools/pilot/README-rentreq.md` (10 lignes), `tools/tests/test_pilot_rentreq.py`. **Hors zone** : `tools/pilot/louer.py`, `tools/activation/verify_vectors.py`, `tools/activation/store-vectors.json`, `android/**`.

## Étapes
1. **Rouge** : `test_pilot_rentreq.py` : (a) fichier canonique ⇒ champs lus, commande imprimée exacte (`louer.py location --tv <tv> --bouquet classe-cm2 --choix 12h --pilote tools/pilot/pilot.json`), `--prolonger --periode <ms>` quand `kind=extend` ; (b) fichier altéré (ligne en trop, champ manquant, `choice` hors grammaire) ⇒ refus avec phrase FR et code de sortie 2 ; (c) code court `CM2-12H-7K3Q` + `--tv <installId> --bouquet classe-cm2` ⇒ commande imprimée **et** avertissement « code court : non vérifiable (nonce inconnu) : confirmez avec le foyer » ; (d) code court dont les 4 caractères ne correspondent pas au `nonce` donné en `--nonce` ⇒ refus ; (e) vecteurs `store-vectors.json` rejoués (canonique ⇒ code court, SHA-256 + Crockford identiques au Kotlin).
2. `rentreq.py` : `lire FICHIER` (texte canonique), `code CODE --tv ID --bouquet B [--nonce N]`, `--pilote` (chemin de `pilot.json`, défaut `tools/pilot/pilot.json`), sortie = la commande sur une ligne + résumé FR (« Demande : CM2 · 12 heures d'utilisation · née sur la TV le 03/10 ») ; `--json` ; aucune exécution (jamais `subprocess` vers `louer.py` : le propriétaire relit et lance) ; bibliothèque standard seulement (`hashlib`, `argparse`, `json`).
3. README : les deux usages, le rappel « une demande ne vaut pas droit : vérifier le foyer et les règles du pilote ; `louer.py` applique `PilotRules` ».
4. **Vert** : porte ; ligne proposée pour `louer.py` (`--demande`) au rapport.

## Critères d'acceptation
Porte verte ; ≥ 8 tests rouges puis verts ; `grep -n "subprocess\|requests\|urllib" tools/pilot/rentreq.py` vide ; `python3 tools/pilot/rentreq.py --help` en français.

## Cas limites
Fichier en CRLF ⇒ normalisé avant vérification canonique (dit dans le README) ; `choice=defaut` ⇒ `--choix defaut` ; alias inconnu dans un code court ⇒ demande du `--bouquet` explicite.

## À ne pas faire
Aucun réseau ; aucune écriture de registre ; ne pas éditer `louer.py` ni `verify_vectors.py` ; aucun secret.

## Rapport
`STATUT`, sorties rouge/vert, ligne proposée pour `louer.py`, exemple de sortie.
