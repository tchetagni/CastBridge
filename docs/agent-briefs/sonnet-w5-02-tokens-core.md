# w5-02 — Cœur : jetons du Quiz (bon de jetons `type=tokens`, porte-jetons local chaîné et lié à l'installation, politique des commodités, protocole de réconciliation), vecteurs

**Vague 5a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 3.4 (c, f), § 6 (lire en entier). Branche `claude/sonnet-w5-02`. Rapport : `docs/agent-reports/sonnet-w5-02.md`. Dépend de w4-01 (`C/crypto/SecretWrapper.kt`, `C/lots/InstallKey.kt`, `Hkdf`). **Décision du propriétaire (P4) : seul le Quiz consomme des jetons** ; ce cahier ne connaît qu'un identifiant de jeu `quiz`.

## Objectif
Le cœur sait : (1) analyser et vérifier une enveloppe `cbx1` de **type `tokens`** (bon de jetons signé par le serveur, lié à la TV **et** à sa clé d'installation, séquence croissante) ; (2) tenir un **porte-jetons** fichier (`castbridge-token-wallet-v1`) : crédits par bon, dépenses chaînées et authentifiées par HMAC dérivé de la clé privée d'installation, solde, rapport, accusé ; (3) exposer la **politique** des commodités (coûts, plafonds par partie) lue depuis la grille ; (4) sérialiser le **protocole de réconciliation** (rapport → réponse) ; (5) figer par des vecteurs.

## Pourquoi (preuves)
- `C/owner/Envelope.kt` (un seul codec ; `type` libre `^[a-z][a-z0-9-]{0,31}$` ; `Target.Device(k, factors)` ; `SeqState`) ; `C/owner/Order.kt:12` (modèle d'un corps de type nouveau avec vérification ordonnée) ; `C/owner/LotKeys.kt:11` (`Hkdf`) ; `C/owner/SafeFile.kt` ; w4-01 `InstallKey` (clé privée X25519 enveloppée par `SecretWrapper`).
- `C/quiz/Wallet.kt` : `VirtualWallet` = jetons **de démonstration sans valeur** ; **ne pas le réutiliser** (w5-03 le renomme « points de défi »).

## Fichiers possédés
Nouveaux : `C/tokens/TokenGrant.kt`, `C/tokens/TokenWallet.kt`, `C/tokens/TokenPolicy.kt`, `C/tokens/TokenSync.kt`, `C/tokens/TokenVectors.kt`, `CT/tokens/**`, `tools/activation/tokens-vectors.json`. **Hors zone** : `C/owner/**` (utiliser `Envelope`, `KeyRing`, `Hkdf`), `C/quiz/**` (w5-03), `C/shop/**` (w5-01), `R/**`, `S/**`, `backend/` (w5-05, w5-08), docs.

## Étapes
1. `TokenGrant(license, grant: Long, amount: Long, installPub: ByteArray, expiry: Long)` : `body()` canonique (ordre : `license`, `grant`, `amount`, `install`, `expiry`), `parseBody`, `issue(signer, seq, nonce, issuedAt, notBefore, expiresAt /* ≤ issuedAt + 30 j */, target: Target.Device, grant): String` (pour les tests et le miroir serveur), `verify(token, ring: KeyRing, revocations, device: Fingerprints, installPub: ByteArray, lastGrant: Long, nowMs): Result` dans l'ordre : `MALFORMED`, `UNKNOWN_TYPE`, `UNKNOWN_KEY`, `REVOKED_KEY`, `BAD_SIGNATURE`, `KEY_NOT_ALLOWED` (portée `ISSUE_PRODUCTION` requise), `BAD_GRANT` (`amount` ∉ 1..10 000, `install` ≠ `installPub`), `WRONG_TARGET` (k parmi n via `DeviceIdentity.matches`), `STALE_SEQUENCE` (`grant ≤ lastGrant`), `NOT_YET_VALID`, `WINDOW_CLOSED`. Le bon utilise `TvClock` par l'appelant (`nowMs` fourni).
2. `WalletKey` : `derive(installPriv: ByteArray): ByteArray` = `HKDF-SHA256(extract("castbridge-wallet-v1", installPriv), "mac", 32)` ; interface `WalletKeyProvider { fun key(): ByteArray? }` (la TV la fournit via `InstallKeyStore` + `SecretWrapper` ; tests : clé mémoire).
3. `TokenWallet(file: File, keys: WalletKeyProvider, clock)` : format § 3.4 (f) ; `credit(grant: TokenGrant, envelopeFp: String)` (refus si `grant.grant ≤ lastGrant`) ; `spend(item: String, cost: Long, nowMs): SpendResult` (`OK(seq, balance)`, `INSUFFICIENT`, `UNREADABLE`) : ajoute `spend=<seq>|<at>|quiz|<item>|<cost>|<prev16>|<mac16>` avec `mac = HMAC(kWallet, ligne sans mac)` ; `balance()` ; `load()` vérifie chaque `mac` et la chaîne `prev` (hash 16 hex de la ligne précédente complète) : une faute ⇒ `state = UNREADABLE`, solde 0, rien n'est écrit ; `report(sinceSeq): String` (lignes `spend` + en-tête `license`, `install`, `lastGrant`, `spentTotal`, `mac` de l'ensemble) ; `ack(seqAcked)` (ne supprime rien : les lignes accusées sont **compactées** en une ligne `acked=<seq>|<total>|<mac>` quand > 500 lignes) ; `SafeFile` à chaque écriture ; `summary()` pour le badge/heartbeat : `lastGrant`, `lastSpendSeq`, `spentTotal`, `state`.
4. `TokenPolicy(settings: TokenSettings)` (de `PriceGrid.settings()` w5-01 ; **recopier** la data class ici si w5-01 n'est pas fusionné et le signaler) : items `SECOND_CHANCE` (coût `cost.secondChance`, 1 par partie), `EXTRA_JOKER` (`cost.extraJoker`, 2 par partie), `SWAP_QUESTION` (`cost.swapQuestion`, 1 par partie) ; `maxPerGame(item)`, `cost(item)`, `label(item)` (« Seconde chance », « Joker en plus », « Changer de question »), `explain(item)` (une phrase), `confirmText(item, balance)` = « Utiliser 5 jetons (cinq) pour « Seconde chance » ? Solde : 23 jetons » (**nombre en chiffres et en lettres**, helper `FrenchNumbers.words(n)` pour 0..10 000).
5. `TokenSync` : `Report(json)` / `Reply(json)` : la réponse du serveur porte `ackedSeq`, `grants: [enveloppes]`, `balanceServer`, `offlineAllowed: Boolean`, `message?` ; `apply(reply, wallet, verifyGrant)` idempotent ; `KidAllowance` : `data class(dailyLimit: Int, spentToday: Int, day: String)` + `canSpend(cost)` (le cœur du contrôle parental de w5-18 vit ici pour être testé en JVM).
6. `tokens-vectors.json` (`castbridge-tokens-vectors-v1`) : `build-tokens` (mêmes entrées → mêmes octets, clé de test `server` des vecteurs existants, graine), `tokens` (accepté ; autre installation ⇒ `BAD_GRANT` ; autre TV ⇒ `WRONG_TARGET` ; `grant` ancien ⇒ `STALE_SEQUENCE` ; clé sans portée ⇒ `KEY_NOT_ALLOWED` ; fenêtre fermée), `wallet` (séquence crédit/dépenses → solde, lignes attendues avec une clé HMAC de test ; mac altérée ⇒ `UNREADABLE` ; chaîne tronquée ⇒ `UNREADABLE` ; compaction), `policy` (coûts par défaut et par grille), `french-numbers` (0, 5, 21, 71, 80, 99, 100, 1 000). ≥ 20 cas.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.tokens.*'   # vert
python3 -c "import json;print(len(json.load(open('tools/activation/tokens-vectors.json'))['cases']))"   # ≥ 20
grep -rn 'VirtualWallet\|Pot\.' android/core/src/main/kotlin/castbridge/core/tokens   # 0 hit (séparation des points de défi)
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/tokens   # 0 hit
grep -rn 'fun delete\|fun reset\|fun clear' android/core/src/main/kotlin/castbridge/core/tokens/TokenWallet.kt   # 0 hit (le porte-jetons ne s'efface pas par l'app)
```

## Cas limites
- Clé d'installation régénérée (réinstallation) : l'ancien fichier devient `UNREADABLE` (HMAC) ⇒ solde 0 ; le serveur réémet les bons non épuisés avec `install` nouveau (w5-08) : documenter dans le KDoc.
- `WalletKeyProvider.key() == null` (Keystore défaillant **et** repli absent) : porte-jetons en lecture seule, `spend` ⇒ `UNREADABLE` avec message « jetons indisponibles sur cette TV ».
- Horloge reculée : `at` d'une dépense peut être < précédent : accepté (la séquence fait foi), signalé dans `summary().clockNote`.
- Deux dépenses dans la même milliseconde : `seq` distinct.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun lien avec `C/quiz/Wallet.kt` ; aucune conversion jetons ↔ argent ; aucun « jeton gagné en jouant » ; pas d'`import android` ; ne pas modifier `Envelope.kt` ; textes en français.

## Rapport
`STATUT`, API publiques (pour w5-03, w5-05, w5-08, w5-17, w5-18), nombre de vecteurs, format exact du fichier `wallet.txt` (pour w5-20).
