# w6-03 — Preuve de TV (`TvProof`), clé d'installation de signature (`InstallSigner`), cache (`ProofCache`), trames et vecteurs

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (w4-01 souhaité)
> **Groupe : W6a-1** (vague W6a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Proof*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2, § 3, § 7). Il **prévaut** sur ce cahier et sur la conception § 3.3. Suite sur `claude/sonnet-w6-03-fix` : ligne de corps **`install=<kid de signature>`** (position 4, facultative) dans `Activation` et l'émission ; `TvProof.verify` : liaison vérifiée **avant** tout épinglage (D-W6-L5), `trust = BOUND|UNBOUND`, `ACTIVATION_UNBOUND` après `BIND_SUNSET_MS` (2027-01-01), `COMPACT_ONLY`, `seq` de preuve **strictement** croissant et `seq` d'activation non régressif (D-W6-L8), **`NonceBook`** (8 ouverts, 10 min, 512 dépensés ; dépense à la première sortie après signature valide, jamais sur `NeedsPin`) (D-W6-L9) ; `ProofCache` : `pin.bound`, validité 3 j (non liée) / 14 j (liée) ; `OwnerFrames.deviceInfo(…, signPub)` et ligne `sign=ed25519|…`. Vecteurs additifs § 7 ; aucun vecteur existant ne change d'octets.

**Vague 6a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.3, § 1 (lignes « Clé d'installation », « Preuve d'activation »). Branche `claude/sonnet-w6-03`. Rapport : `docs/agent-reports/sonnet-w6-03.md`.

## Objectif
(1) `InstallSigner` : graine Ed25519 **de signature** propre à l'installation de la TV (distincte de la clé X25519 de w4-01), `keyId` = `KeyRing.idOf(pub)`, scellement par un `SecretWrapper` injecté (w4-01) ou repli fichier ; (2) `TvProof` : construction côté TV et vérification côté téléphone d'une enveloppe `cbx1` de type **`proof`** signée au **défi** ; (3) `ProofCache` : preuves par TV, clé d'installation épinglée (TOFU), validité 14 j bornée par `endsAt`, horloge `TvClock` ; (4) deux trames `OwnerFrames.PROOF_REQUEST = 9`, `PROOF = 10` ; (5) vecteurs `tools/activation/proof-vectors.json` (`castbridge-proof-vectors-v1`) rejoués par `ProofVectorsTest`.

## Pourquoi (preuves)
- `C/owner/Envelope.kt` (enveloppe générique `cbx1`, `Target.Device(k, factors)`, `canonicalPayload`, `encode/decode`) ; `C/owner/Activation.kt:133-167` (`ActivationVerifier`, ordre des contrôles ; `expect = Subject.TV`), `:170-176` (`TvAccess`), `:178-255` (`TvGate`) ; `C/owner/DeviceIdentity.kt` (`DeviceCode.of(Fingerprints)`) ; `C/owner/Keys.kt:48-69` (`TrustedKey`, `KeyRing`), `:81-153` (`TvClock`) ; `C/owner/OwnerFrames.kt:17-24` (types 1-8 : 9 et 10 sont libres) ; `C/update/Ed25519` (signature) et `Ed25519Signer` (`grep -rn "class Ed25519Signer" C/`).
- DESIGN-W5 § 2 (2) : le serveur reçoit **le jeton d'activation brut** comme preuve : la preuve W6 **embarque** ce jeton (`activation=`) pour que le téléphone le réutilise dans `X-CB-TV-Proof` (w6-09).

## Fichiers possédés
Nouveaux `C/owner/InstallSigner.kt`, `C/owner/TvProof.kt`, `C/owner/ProofCache.kt`, `CT/owner/TvProofTest.kt`, `CT/owner/ProofCacheTest.kt`, `CT/owner/ProofVectorsTest.kt`, `tools/activation/proof-vectors.json` ; modifié `C/owner/OwnerFrames.kt` (deux constantes + KDoc). **Hors zone** : `C/owner/Envelope.kt`, `Activation.kt`, `Keys.kt`, `R/**`, `S/**`, `backend/**`, `verify_vectors.py` (w6-10).

## Étapes
1. `InstallSigner(seed)` : `keyId`, `publicKeyBase64`, `sign(text)`, `fingerprintText()` (8 groupes hex lisibles « xxxx-xxxx-… » pour la comparaison à l'écran) ; `InstallSignerStore` interface (`load/save` d'un blob opaque) ; `create(random)`.
2. `TvProof.build(signer, device: Fingerprints, nonceHex, seq, tvClockNow, access: TvAccess, activationToken: String?, name): String` → enveloppe type `proof`, fenêtre `issuedAt..issuedAt+10 min`, cible `Device(DeviceIdentity.kFor(n), factors)`, corps `code=`, `name=` (≤ 40, sans retour à la ligne), `state=production|trial|grace|degraded|locked|suspended`, `label=`, `endsAt=`, `super=`, `uptime=`, `activation=` (le jeton `cbx1` de l'activation **de production** qui compte, ou absent). `state` dérive de `TvAccess` : `superUnlimited||(keyInstalled && !trial && !suspended) → production` (`degraded` si le champ existe — w4-07 — sinon `locked`), `suspended`, `trial`, `locked` ; `grace` passé par l'appelant.
3. `TvProof.verify(token, expectedNonce, pinnedKey: TrustedKey?, trusted: KeyRing, nowMs, consumed: Set<nonce>): ProofResult` dans l'ordre § 3.3 : lisible → type → nonce (égal, non consommé) → signature (clé épinglée ; sinon `NeedsPin(keyId, pub, fingerprint)` renvoyé à l'appelant qui décide du TOFU) → `state == production` (sinon `Rejected(reason = TRIAL|GRACE|LOCKED|DEGRADED|SUSPENDED)`, **avec** le code/nom de la TV pour les messages) → `activation` présente et acceptée par `ActivationVerifier(trusted, expect = Subject.TV)` **sans** le contrôle matériel (appeler `Activation.decode` + vérifier signature/clé/portée/plafond à la main, ou ajouter dans `TvProof` un mini-vérificateur qui réutilise `TrustedKey.verify` et `Activation.from` ; ne pas modifier `ActivationVerifier`) → `kind == PRODUCTION` → `DeviceCode.of(Fingerprints(a.factors)) == code` et facteurs de la cible = facteurs de l'activation → plafond `usage` non dépassé à `max(nowMs, issuedAt)` → `Accepted(Proof(tvCode, tvName, installKid, endsAt, superUnlimited, verifiedAt = nowMs, seq))`.
4. `ProofCache(store: KvStore-like, clock: TvClock, validityMs = 14 j)` : `put(proof)`, `pin(tvCode, key)`, `pinned(tvCode)`, `validProofs(now)`, `forget(tvCode)`, `dropIfEnded(tvCode)` ; `ProofSummary` (le type simple de w6-01 : si w6-01 n'est pas fusionné, définir `ProofSummary` ici et le signaler : w6-01 l'importera).
5. Vecteurs : clés de test, un appareil, cas `proof` (valide ; nonce rejoué ; nonce différent ; signature fausse ; `state=trial` ; `state=degraded` ; activation d'essai ; activation périmée (`usage`) ; code ≠ facteurs ; fenêtre fermée ; clé d'activation inconnue) et `build-proof` (mêmes entrées ⇒ mêmes octets). Régénération : `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ProofVectorsTest*'`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.TvProofTest' --tests 'castbridge.core.owner.ProofCacheTest' --tests 'castbridge.core.owner.ProofVectorsTest'   # vert
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'   # les vecteurs existants restent verts
git diff --quiet tools/activation/test-vectors.json   # inchangé
python3 -c "import json;d=json.load(open('tools/activation/proof-vectors.json'));print(len(d['cases']))"   # ≥ 12
```

## Cas limites
TV sans aucune activation (`activation` absent) ⇒ `Rejected(LOCKED)` ; activation `SUPER_UNLIMITED` ⇒ `production` et `superUnlimited = true`, `endsAt = null` ; horloge téléphone reculée : `verifiedAt` vient de `clock.now()` (monotone), `validUntil` ne recule pas ; TV réinstallée (clé différente de la clé épinglée) ⇒ `IdentityChanged(fingerprint)` sans effacer l'épinglage (w6-16 demande confirmation) ; `seq` plus ancien que le dernier vu ⇒ `Rejected(REPLAY)`.

## À ne pas faire
Ne pas réutiliser la clé X25519 pour signer ; ne pas modifier `Envelope.kt`/`Activation.kt` ; aucune horloge murale dans la décision ; pas d'Android.

## Rapport
`STATUT`, API publique (pour w6-12, w6-16, w6-09, w6-10), format exact du corps `proof` (une ligne par champ, à recopier dans ACTIVATION-FORMAT par w6-20).
