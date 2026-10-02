# w4-17 — Miroirs Java et Python : type `delegation`, activation avec ticket, journal chaîné, grille, reçus (vecteurs `agent-vectors.json`)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT **amendé W5** (Delegation.java sans master=)
> **Groupe : W4c-2** (vague W4c) · prérequis : w4-11 · porte : `python3 tools/activation/verify_vectors.py && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest=AgentVectorsTest`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 4c · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-11 ; **avant** w4-16 qui l'utilise).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 3, § 4, § 6. Branche `claude/sonnet-w4-17`. Rapport : `docs/agent-reports/sonnet-w4-17.md`.

## Objectif
Le serveur (Java) et le vérificateur Python savent analyser et vérifier une délégation, vérifier une activation avec ticket (contraintes comprises), vérifier une entrée de journal (hash, signature, chaîne), une grille de prix signée et un code de reçu ; les trois implémentations passent **tous** les cas de `tools/activation/agent-vectors.json`.

## Pourquoi (preuves)
- `B/licenses/Envelope.java` (codec `cbx1` serveur), `EnvelopeVerifier.java` (w1-10), `WireActivation.java` ; `tools/activation/verify_vectors.py:176-240` (`envelope_text`, `parse_envelope`), `:303` (`verify_activation`), `:681` (`replay_licences`).
- `tools/activation/agent-vectors.json` (w4-11).

## Fichiers possédés
Nouveaux `B/licenses/Delegation.java` (parse/verify/contraintes, `withDelegated` sur l'anneau), `B/licenses/TicketedActivation.java`, `B/licenses/AgentLedgerEntry.java` (hash, signature, chaîne ; **jamais** sous `B/agents/`, paquet possédé par w4-16), `B/licenses/PriceGrid.java`, `B/licenses/ReceiptCode.java`, `BT/licenses/AgentVectorsTest.java` ; modifiés `tools/activation/verify_vectors.py` (fonctions `verify_delegation`, `verify_ticketed`, `verify_ledger`, `verify_price_grid`, `receipt_code`, `replay_licences` avec clés déléguées et fenêtre), `B/licenses/EnvelopeVerifier.java` (**point d'extension additif** pour le type `delegation`, si nécessaire). **Hors zone** : `C/**`, `B/agents/**` au-delà du fichier cité (w4-16), Android, vecteurs (lecture seule).

## Étapes
1. `Delegation.java` : corps canonique, bornes, `verify(token, ring, revoked, nowMs)` dans l'ordre de la conception § 3 ; `master=` : **pas ouvert** côté serveur (il n'a pas de clé X25519 d'agent ; seule la forme est vérifiée).
2. `TicketedActivation.java` : `split`, vérification avec l'anneau étendu, contraintes (mêmes messages qu'en Kotlin, en français).
3. Journal, grille, reçu : ports directs (SHA-256, Ed25519 via `Ed25519ActivationSigner`/vérificateur existant, Base32 Crockford `Crockford.java`).
4. `replay` serveur (`RegistryEvent`/`LedgerService` : lire comment le serveur rejoue ; si la logique est dans `LicenseService`, ajouter un **anneau paramétrable** par méthode additive) : événement signé par une clé déléguée accepté dans sa fenêtre, rejeté hors fenêtre.
5. `AgentVectorsTest` : tous les types de cas ; Python : idem ; `verify_vectors.py` imprime « agent : N cas OK ».

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='AgentVectorsTest'   # vert
cd backend && ./mvnw -q -o test   # suite verte
python3 tools/activation/verify_vectors.py   # « agent : N cas OK », N = nombre de cas du fichier
git diff --quiet tools/activation/*.json && echo "vecteurs intacts"
```

## Cas limites
- Délégation signée par la clé **serveur** de test (sans `DELEGATE`) ⇒ `KEY_NOT_ALLOWED` (vecteur).
- Ticket dont la délégation est valide mais l'activation signée par une **autre** clé que `agent` ⇒ `UNKNOWN_KEY`.

## À ne pas faire
Pas de déploiement ; pas de commit sur les branches partagées ; ne pas modifier les vecteurs ; aucune clé réelle.

## Rapport
`STATUT`, nombre de cas rejoués, classes livrées (pour w4-16), différences de comportement relevées entre Kotlin/Java/Python (doivent être nulles).
