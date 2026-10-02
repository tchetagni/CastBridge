STATUT: TERMINÉ
CAHIER: w6-03 · MODÈLE: sonnet · BRANCHE: claude/sonnet-w6-03 · COMMIT: voir `git log -1` de la branche
JETONS: inconnu
PORTE: gradle --offline :core:test --tests TvProofTest/ProofCacheTest/ProofVectorsTest → VERT (34 + 11 + 3 tests)
SUITE COMPLÈTE: gradle --offline :core:test → VERT (2142 tests, 0 échec) ; écarts : aucun ; `test-vectors.json` inchangé ; `proof-vectors.json` = 26 cas
FICHIERS: android/core/src/main/kotlin/castbridge/core/owner/{InstallSigner,TvProof,ProofCache}.kt (neufs) ; OwnerFrames.kt (PROOF_REQUEST=9, PROOF=10) ; tests TvProofTest, ProofCacheTest, ProofVectorsTest ; tools/activation/proof-vectors.json ; hors zone = aucun
CHOIX:
- Le corps `proof` embarque `installKey=<Ed25519 base64>` (absent du cahier) : sans lui le téléphone ne peut pas épingler (TOFU, NeedsPin/IdentityChanged) ; lié à `kid` (kid = hash de la clé), donc non substituable. Ordre exact : code, name, state, label, endsAt, super, uptime, installKey, [activation].
- `endsAt` et `super` du corps sont recalculés depuis l'activation et doivent être égaux (sinon MALFORMED) ; règle unique `TvProof.endsAtOf` (0 si super ou sans `usage`, sinon plus petit `usage.endsAt`).
- `ProofSummary` (w6-01, PhoneGate.kt) réutilisé tel quel ; `Proof` embarque `activationToken` (en-tête `X-CB-TV-Proof`, w6-09).
- Fenêtre 10 min contrôlée avec une tolérance de 24 h (`SKEW_MS`) : la fraîcheur réelle vient du nonce (une réponse par défi).
- Vérificateur d'activation maison (pas `ActivationVerifier`) : pas de fenêtre d'installation 48 h ni de matériel ; contrôle clé connue/révoquée/portée (production|reactivate, super, tout ouvert)/signature/sujet TV/poste révoqué (`RevocationState` optionnel).
- `lastSeq` en paramètre de `verify` (seq < dernier vu ⇒ REPLAY) ; `degraded` n'existe pas dans `TvAccess` (w4-07) : le constructeur émet `locked`, `degraded` est vérifié si la TV l'envoie.
- `InstallSigner.loadOrCreate(store, wrapper)` : repli `PlainWrapper`, graine zéroïsée, `regenerated=true` si le blob est illisible ; `FileInstallSignerStore` + `ProofCacheStore`/`FileProofCacheStore` (SafeFile).
- `ProofCache` : verifiedAt = `TvClock.now`, validUntil ne recule jamais, `put` refuse un seq plus ancien ; `dropProof` (mode réduit) garde épingle et seq ; la persistance de l'horloge reste à l'appelant.
NON FAIT / À VALIDER SUR MATÉRIEL: `verify_vectors.py` (hors zone, w6-10 : rejouer `proof-vectors.json`) ; ACTIVATION-FORMAT (w6-20) ; aucun test matériel ; trame BT non câblée (Android).
QUESTION: aucune
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets (graines de test dérivées de chaînes publiques) [x] dépendances [x] FR [x] diff ≤ plafond [x] un commit
