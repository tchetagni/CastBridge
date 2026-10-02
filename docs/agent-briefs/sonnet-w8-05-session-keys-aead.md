# w8-05 — Cœur : clés de session (HKDF), enregistrements AEAD de 64 Kio, nonces uniques entre voies, choix du chiffre mesuré

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (indépendant)
> **Groupe : W8a-1** (vague W8a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*SessionKeys*' --tests '*Aead*' --tests '*XferVectors*'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : oui

**Vague 8a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (indépendant).** Conception : § 5.4, 5.5. Branche `claude/sonnet-w8-05`. Rapport : `docs/agent-reports/sonnet-w8-05.md`.

## Objectif
Chiffrement de bout en bout du vrac, **sans** tampon de bloc entier sur une TV à 1 Go, avec des nonces qui ne se répètent jamais entre voies ni entre nouveaux essais, une dérivation de clés qui réutilise le secret déjà partagé (jeton de téléphone de confiance), et un choix de chiffre **mesuré** (AES-GCM contre ChaCha20-Poly1305) plutôt que supposé.

## Pourquoi (preuves)
- Le jeton `cbk_` + 64 hex est le seul secret partagé réutilisable (`core/trust/TrustRegistry.kt:153-166`) ; la TV ne garde que son haché mais reçoit le jeton en clair à chaque requête (`X-CB-Token`, `docs/BT-PLUG-AND-PLAY.md:25`).
- AES/GCM et HMAC-SHA256 déjà utilisés en JCA (`core/owner/LotKeys.kt:12-43`) ; pas de X25519 avant W4 (`PROTECTION-TV-FABLE § 1`).
- Conscrypt met en tampon tout le texte chiffré GCM jusqu'à `doFinal` : d'où les enregistrements de 64 Kio.

## Fichiers possédés
Nouveaux `C/xfer/{SessionKeys,AeadRecords,CipherPick}.kt`, `CT/xfer/{SessionKeysTest,AeadRecordsTest,XferVectorsTest}.kt`, `tools/activation/xfer-vectors.json`. **Hors zone** : tout le reste (w8-08 branche les clés dans `TransferHost`, w8-10 dans les routes).

## Étapes
1. `SessionKeys.derive(ikm: ByteArray, noncePhone: ByteArray(32), nonceTv: ByteArray(32), transferId: ByteArray(8)): Session` : HKDF-SHA256 (extract + expand, RFC 5869, sur `Mac "HmacSHA256"`), `info = "cbx-session-v1" ‖ transferId ‖ 0x00`, sortie 36 o = clé 32 o + préfixe de nonce 4 o. `ikm` = octets du jeton (décoder l'hex après `cbk_`) ; **refuser** un PIN (`ikm.size < 16` → `IllegalArgumentException`, l'appelant passe en « non chiffré »). `Session` ne journalise jamais sa clé (`toString` = `"Session(enc)"`).
2. Nonces : `NonceSeq(prefix, laneId)` → `next(): ByteArray(12)` = `prefix 4 o ‖ laneId u8 ‖ compteur u56` ; `laneId ∈ 1..255` ; compteur jamais remis à zéro ; dépassement → `SessionExhausted` (l'appelant ouvre une nouvelle session). Côté réception : `ReplayWindow(laneId, size = 1024)` rejette un compteur déjà vu ou plus vieux que la fenêtre.
3. `AeadRecords` : `encrypt(session, seq, src: ByteBuffer(≤ 64 Kio), aad: ByteArray, dst: ByteBuffer)` / `decrypt(…)` par enregistrement `len u16 | ciphertext | tag 16 o` ; flux `RecordInputStream(in, session, laneId, window, aad)` qui **ne rend un octet de clair qu'après vérification de la balise** de son enregistrement ; `RecordOutputStream`. Tampons directs réutilisés, aucune allocation par enregistrement.
4. `CipherPick.measure(): Pick` : chiffre 4 Mio avec `AES/GCM/NoPadding` puis `ChaCha20-Poly1305` (si le fournisseur l'a ; sinon AES seul), renvoie `(cipher, bytesPerSec)` ; `CipherPick.verdict(pick, diskBps): "on" | "degraded"` (dégradé si `bytesPerSec < 1,5 × diskBps`). Résultat **mis en cache** par l'appelant (w8-10 dans les préférences de la TV).
5. Vecteurs figés `tools/activation/xfer-vectors.json` : 3 dérivations (ikm, nonces, transferId → clé, préfixe), 3 enregistrements (clé, nonce, aad, clair → chiffré) pour chaque chiffre, 2 cas de rejeu attendus refusés. `XferVectorsTest` les rejoue ; **ne jamais régénérer** sans le dire dans le rapport.
6. Tests : unicité des nonces sur 10^6 trames réparties sur 5 voies (ensemble de hachés) ; rejeu refusé ; balise fausse → `AEADBadTagException` et **aucun** octet livré ; AAD différent → refus ; aller-retour 10 Mio par enregistrements ; PIN refusé ; dérivation stable sur vecteurs.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.SessionKeysTest' --tests 'castbridge.core.xfer.AeadRecordsTest' --tests 'castbridge.core.xfer.XferVectorsTest'   # vert, ≥ 14 cas
python3 - <<'EOF'
import json; v=json.load(open('tools/activation/xfer-vectors.json')); assert v['version']==1 and len(v['derive'])>=3 and len(v['records'])>=6; print('ok')
EOF
grep -rn "key.contentToString\|Log\.\|println" android/core/src/main/kotlin/castbridge/core/xfer/SessionKeys.kt android/core/src/main/kotlin/castbridge/core/xfer/AeadRecords.kt | wc -l   # 0
```

## Cas limites
Enregistrement final plus court que 64 Kio ; enregistrement vide (refusé) ; `ChaCha20-Poly1305` absent du fournisseur (API 26-27, ou fournisseur réduit) → AES seul, sans erreur ; deux sessions pour le même transfert (reprise) : préfixes différents (les nonces passés ne comptent plus, nouvelle clé).

## À ne pas faire
Pas de X25519 maintenant (W4) ; pas de bibliothèque crypto (BouncyCastle, Tink) ; aucune clé ni jeton dans un journal, un `toString`, un message d'exception ; ne pas écrire dans `TrustRegistry`.

## Rapport
`STATUT`, débits mesurés AES/ChaCha sur la machine de l'agent (indicatif, pas la TV), format JSON des vecteurs, tests avant/après.
