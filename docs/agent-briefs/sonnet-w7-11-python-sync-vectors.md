# w7-11 — Miroir Python des vecteurs W7 (`sync-frames-vectors.json`, `sync-vectors.json`, cas `keyring`)

**Vague 7a (fin) · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après w7-02, w7-03, w7-07 fusionnés).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 6, § 7.1, D-W7-4. Branche `claude/sonnet-w7-11`. Rapport : `docs/agent-reports/sonnet-w7-11.md`.

## Objectif
`tools/activation/verify_vectors.py` rejoue, **à partir des seuls documents** (`docs/SYNC-PROTOCOL.md` § 1-2, `docs/ACTIVATION-FORMAT.md` § 3.5), les vecteurs : codage des trames `CBSY`, poignée de main `CBSX` (X25519, HKDF, AES-GCM, SAS, trames chiffrées, refus), et les cas `keyring`/`build-keyring` de `test-vectors.json`. Deux implémentations indépendantes = même règle que pour les activations (`ACTIVATION-FORMAT.md:3`).

## Pourquoi (preuves)
- `tools/activation/verify_vectors.py` (implémentation de référence Python, dépendance `cryptography` : offre X25519, HKDF, AES-GCM) ; `tools/tests/test_verify_vectors.py`.
- `docs/ACTIVATION-FORMAT.md` § 11.1 (table « qui rejoue quoi ») : à compléter.

## Fichiers possédés
`tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py`, `docs/ACTIVATION-FORMAT.md` (**§ 11.1 seulement** : trois lignes de table). **Hors zone** : les fichiers de vecteurs (lecture seule : si un vecteur semble faux, `STATUT: BLOQUÉ` + `QUESTION`, ne pas le corriger).

## Étapes
1. Lire § 1 de `SYNC-PROTOCOL.md` ; écrire `encode_frame/decode_frame` ; rejouer `sync-frames-vectors.json` (hex identique, refus attendus).
2. Lire § 2 ; implémenter la poignée de main avec `cryptography` (X25519PrivateKey.from_private_bytes, HKDF, AESGCM) ; rejouer `sync-vectors.json` (clés, `h`, SAS, trames, refus).
3. Rejouer `keyring` et `build-keyring` (octets identiques).
4. Compteur de contrôles par fichier dans la sortie ; tests unitaires Python ≥ 6.
5. Table § 11.1 : lignes `sync-frames-vectors.json`, `sync-vectors.json`, cas `keyring`.

## Critères d'acceptation
```sh
python3 tools/activation/verify_vectors.py            # affiche les totaux, aucun échec
python3 -m unittest discover -s tools/tests -p 'test_verify_vectors.py'   # vert
git diff --quiet origin/integration/agents -- tools/activation/*.json && echo intact   # intact
```

## Cas limites
Vecteur `BAD_TAG` : la bibliothèque lève `InvalidTag` : attendre l'exception ; clé « petit ordre » : `cryptography` peut refuser avant l'échange : accepter les deux comportements comme « refus ».

## À ne pas faire
Ne pas lire le Kotlin pour écrire le Python (indépendance) ; ne pas modifier les vecteurs.

## Rapport
`STATUT`, nombre de contrôles par fichier, écarts trouvés entre doc et vecteurs (le cas échéant).
