# w6-10 — Python : rejouer `proof-vectors.json` dans `verify_vectors.py` (implémentation indépendante)

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT (après w6-03)
> **Groupe : W6b-1** (vague W6b) · prérequis : w6-03 · porte : `python3 tools/activation/verify_vectors.py && python3 -m unittest discover -s tools/tests -p 'test_verify_vectors.py'`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L2/L5/L8/L9, § 7). Il **prévaut**. En plus : `parse_device_request` lit la ligne `sign=ed25519|…` ; corps `activation` avec `install=<16 hex>` en position 4 (facultatif, forme canonique) ; cas additifs `proof-bound-*`, `proof-unbound-*`, `proof-seq-*`, `proof-compact-only`, `nonce-*` (contrat du défi : dépensé à la première sortie après signature valide, jamais sur `NeedsPin`). Prérequis : `claude/sonnet-w6-03-fix` fusionné. Toujours écrit depuis la description, jamais depuis le Kotlin.

**Vague 6b · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT (après w6-03 fusionné).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.3, § 5 (tests). Branche `claude/sonnet-w6-10`. Rapport : `docs/agent-reports/sonnet-w6-10.md`.

## Objectif
`tools/activation/verify_vectors.py` rejoue les cas `proof` et `build-proof` de `tools/activation/proof-vectors.json` (`castbridge-proof-vectors-v1`) avec une implémentation **écrite à partir de la description du format** (rapport de w6-03 / ACTIVATION-FORMAT § type `proof` quand w6-20 l'aura écrit ; en attendant, le rapport de w6-03 fait foi), sans lire le code Kotlin.

## Pourquoi (preuves)
- `tools/activation/verify_vectors.py` (référence Python des enveloppes `cbx1`, `activation`, `command`, `order`, `revocation`, `licence` ; w4-05/w5-05 y ont ajouté `rental-v2`/`shop`) ; `tools/tests/test_verify_vectors.py` ; `docs/ACTIVATION-FORMAT.md § 11` (qui rejoue quoi).

## Fichiers possédés
`tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py`. **Hors zone** : tout le reste (en particulier `proof-vectors.json` : lecture seule ; s'il est faux, dire lequel des deux a tort dans le rapport).

## Étapes
1. Charger `proof-vectors.json` quand il existe ; cas `proof` : entrée (jeton, nonce attendu, nonces consommés, clé publique épinglée, anneau de clés de confiance pour l'activation embarquée, instant) ⇒ résultat attendu (`accepted` ou `rejected` + raison) ; cas `build-proof` : entrées ⇒ octets attendus (signature déterministe : Ed25519 l'est).
2. Vérifications dans l'ordre de w6-03 (lisible, type, nonce, signature, `state`, activation embarquée, code = facteurs, plafond, fenêtre).
3. `test_verify_vectors.py` : le fichier est rejoué et compte ≥ 12 cas.

## Critères d'acceptation
```sh
python3 tools/activation/verify_vectors.py   # imprime « proof: N cas verts »
python3 -m unittest discover -s tools/tests -p 'test_verify_vectors.py'   # vert
```

## Cas limites
Fichier absent ⇒ le script ne plante pas (message « proof-vectors.json absent ») ; cas inconnu ⇒ échec explicite.

## À ne pas faire
Ne pas modifier les vecteurs ; ne pas importer le Kotlin ; pas de nouvelle dépendance (reste `cryptography`).

## Rapport
`STATUT`, nombre de cas, divergences éventuelles avec le Kotlin.
