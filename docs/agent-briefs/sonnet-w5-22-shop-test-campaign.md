# w5-22 — Campagne de test de la boutique : outil de fumée `tools/shop-test`, `tools/rental-test` sans maître, liste de contrôle TV + téléphone + point focal dans `docs/TEST-CAMPAIGN.md`

**Vague 5e · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après la fusion de 5a…5d ; la partie « vraie TV » exige le propriétaire présent).** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 4.5, § 5.2, § 6.4-6.5, § 12 (risques). Branche `claude/sonnet-w5-22`. Rapport : `docs/agent-reports/sonnet-w5-22.md`.

## Objectif
(1) `tools/shop-test/shop_test.py` (Python 3.8+, bibliothèque standard + `cryptography`) : contre un serveur **local ou de préprod** (jamais la production : refus si l'URL contient le domaine de production, lu dans `ServerUrl.DEFAULT` : `bridge.sti-cm.com`) : enregistre un faux appareil, fabrique une demande de boutique à partir d'une **activation de test** (émise par l'outil de bureau avec une clé jetable que le serveur de test connaît), importe un lot de bons de test (`make_vouchers.py`), `quote`, `order` avec bon ⇒ `FULFILLED`, télécharge les lots scellés, **ouvre la boîte v2** avec la clé privée d'installation de test et déchiffre un lot (preuve de bout en bout), achète des jetons ⇒ bon de jetons vérifié, rapporte des dépenses ⇒ accusé, commande en espèces ⇒ confirmation par une délégation de test ⇒ `FULFILLED` ; `--emulator` : installe l'activation et les lots sur un émulateur TV via `adb`/HTTP (`/api/activation/install`, `/api/lots/upload`, `/api/rental/install`, `/api/tokens/install`) et vérifie `GET /api/rental` et `GET /api/tokens/report`. (2) `tools/rental-test` : plus de `--maitre` : la clé de contrat vient du serveur de test (ou d'un fichier de test pour le mode hors ligne) ; documentation. (3) `docs/TEST-CAMPAIGN.md` § « Boutique, locations en ligne, jetons » : ≈ 40 étapes numérotées (TV de référence 32 bits + téléphone + app point focal de test) : tuile, navigation D-pad, saisie d'un bon avec faute, hors ligne ⇒ attente ⇒ relais, location par bon, location par espèces + confirmation, renouvellement, réinstallation ⇒ réémission, budget 10 Mo dépassé, jetons : achat, seconde chance, joker en plus, changer de question, refus par défaut après 10 s, essai : partie découverte ×3, réduit : bon de clé, profil enfant : blocage, allocation, PIN, rapport parental, reçus, remboursement de test (console), anomalies (`TOKEN_REPLAY` provoqué par restauration d'un `wallet.txt` sur l'émulateur), heartbeat, télémétrie avec/sans consentement, journaux sans code de bon ni donnée personnelle (`grep`).

## Pourquoi (preuves)
- `tools/rental-test/**` (w3-14 : installe une location sur une vraie TV), `docs/TEST-CAMPAIGN.md` (40 étapes existantes : même format), `tools/activation/verify_vectors.py` (Python indépendant : réutiliser ses primitives X25519/HKDF/AES-GCM pour ouvrir la boîte v2 et le lot), `tools/vouchers/make_vouchers.py` (w5-14), `tools/activation-desktop` (`emettre`, `delegation` avec clé de test).

## Fichiers possédés
Nouveaux `tools/shop-test/shop_test.py`, `tools/shop-test/README.md`, `tools/shop-test/test_shop_test.py` (tests unitaires des fonctions pures : construction de la demande, ouverture de boîte sur les vecteurs v2, refus de l'URL de production) ; modifiés `tools/rental-test/**`, `docs/TEST-CAMPAIGN.md`. **Hors zone** : code Android/serveur, `verify_vectors.py` (importer, ne pas modifier), `.github/**` (w5-24).

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/shop-test   # vert (sans serveur)
python3 tools/shop-test/shop_test.py --server https://bridge.sti-cm.com --dry-run; echo "code=$?"   # refus explicite (≠ 0) : jamais la production
python3 tools/shop-test/shop_test.py --server http://localhost:8080 --fake   # mode factice interne : parcours complet simulé vert
grep -c '^[0-9]\+\.' docs/TEST-CAMPAIGN.md   # augmenté d'environ 40
grep -rn 'maitre\|master' tools/rental-test   # 0 hit hors commentaires « ancien »
```
Observable : le propriétaire exécute la campagne sur la TV de référence et un téléphone ; résultats consignés dans `docs/agent-reports/sonnet-w5-22.md` (tableau étape / résultat / remarque).

## Cas limites
- Serveur de préprod sans `castbridge.shop.enabled=true` : l'outil le dit (404) et s'arrête proprement.
- Clé publique du serveur absente des `TRUSTED_KEYS` de l'émulateur : l'outil détecte `UNKNOWN_KEY` à l'installation et imprime la procédure (`tools/release/check_server_key.sh` de w5-24).

## À ne pas faire
Pas de commit sur les branches partagées ; jamais la production ; aucun secret réel (clés jetables) ; ne pas laisser de fichier `CODES-SECRET` ; français dans la campagne.

## Rapport
`STATUT`, ce qui a tourné en local / préprod / vraie TV, défauts trouvés (avec étape), temps de saisie d'un bon à la télécommande (mesuré).
