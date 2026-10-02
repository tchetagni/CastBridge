# w7-03 — Canal sécurisé `CBSX` : poignée de main X25519 (XX), HKDF-SHA256, AES-256-GCM, SAS, vecteurs

**Vague 7a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 6, § 4.4. Branche `claude/sonnet-w7-03`. Rapport : `docs/agent-reports/sonnet-w7-03.md`.

## Objectif
`SecureSession` pur : poignée de main à 3 messages selon § 6 (clés statiques X25519 des deux côtés, éphémères, transcript `h`, clés `k1`, `k2`, clés de session par direction, SAS à 4 chiffres), trames AEAD avec compteur strictement croissant, détection de rejeu, et **vecteurs de test déterministes** (`tools/activation/sync-vectors.json`) rejoués ici et en Python (w7-11). Même code pour le téléphone (initiateur) et la TV (répondeur).

## Pourquoi (preuves)
- Aucun chiffrement au-dessus du RFCOMM ni du HTTP LAN aujourd'hui (grep `Cipher|AES|TLS` dans `C/tunnel`, `C/tv/BtProtocol.kt` : rien) ; limite documentée `docs/BT-PLUG-AND-PLAY.md:130`.
- `C/owner/Keys.kt:59-70` (`KeyRing`, `keyId` = SHA-256[0:8]) : même façon de dériver une empreinte ; `castbridge.core.update.Ed25519` (vérification pure) : ne pas s'en servir pour signer (Android < 13 n'a pas Ed25519 : `docs/ACTIVATION-FORMAT.md:42`) ⇒ **authentification par X25519 seulement** (modèle XX).
- w4-01 prévoit `C/owner/X25519.kt` (pur) : **s'il existe sur la branche, l'utiliser ; sinon** créer `C/link/X25519Lite.kt` (RFC 7748, vecteurs du RFC § 5.2 et § 6.1) et le dire dans le rapport (R1 de la conception).
- `javax.crypto` AES/GCM/NoPadding et HmacSHA256 : disponibles Android 8+ sans dépendance.

## Fichiers possédés
Nouveaux : `C/link/SecureSession.kt`, `C/link/Hkdf.kt`, `C/link/X25519Lite.kt` (seulement si `C/owner/X25519.kt` est absent), `CT/link/SecureSessionTest.kt`, `CT/link/X25519LiteTest.kt`, `tools/activation/sync-vectors.json` ; `docs/SYNC-PROTOCOL.md` **§ 2 seulement** (fichier créé par w7-02 ; si absent, créer avec le § 2 et laisser § 1/§ 3 vides). **Hors zone** : `C/owner/**` (lecture), `SyncFrames.kt`.

## Signatures à respecter (contrat pour w7-13, w7-18)
```kotlin
package castbridge.core.link
class StaticKey(val priv: ByteArray /*32*/) { val pub: ByteArray; companion object { fun generate(random: SecureRandom): StaticKey; fun fingerprint(pub: ByteArray): String /* "7f3a-91c2" */ } }
sealed class HsResult { data class Continue(val send: ByteArray) : HsResult(); data class Done(val session: SecureSession, val send: ByteArray?) : HsResult(); data class Fail(val reason: HsFailure) : HsResult() }
enum class HsFailure { BAD_VERSION, BAD_LENGTH, BAD_TAG, UNEXPECTED_STATIC, REPLAY, HOUSEHOLD_REJECTED }
class Handshake(val me: StaticKey, val initiator: Boolean, val expectedPeerPub: ByteArray? /* épinglé, null = TOFU */, val caps: String, val random: SecureRandom, val household: ByteArray? = null /* code foyer, initiateur */) {
    fun start(): ByteArray?            // initiateur : msg1 ; répondeur : null
    fun receive(msg: ByteArray): HsResult
    val peerPub: ByteArray?            // connu après msg2 (init.) / msg3 (rép.)
    val sas: Int?                      // 0..9999 après msg2
    val peerInfo: Map<String, String>  // id, name, ver, caps, act (répondeur → initiateur)
}
class SecureSession(val sid: String /* 16 hex */, keys…) { fun seal(plain: ByteArray): ByteArray; fun open(sealed: ByteArray): ByteArray /* lève SessionBroken(REPLAY|BAD_TAG) */ ; val peerPub: ByteArray ; val sas: Int }
```

## Étapes
1. `Hkdf.extractExpand(ikm, salt, info, len)` (RFC 5869) + vecteurs RFC.
2. `Handshake` exactement § 6 (`"castbridge-sync-v1"`, `info` `"k1"`, `"k2"`, `"session"`, SAS = `(u32 BE SHA256("sas"|h)) mod 10000`), `expectedPeerPub` ≠ reçu ⇒ `UNEXPECTED_STATIC` (jamais de TOFU silencieux quand une clé est épinglée) ; `household` ⇒ `HMAC_{k2}(code)` dans msg3 ; le répondeur reçoit un `householdCheck: (ByteArray) -> Boolean` (injecté) et répond `Fail(HOUSEHOLD_REJECTED)` sans révéler le code.
3. `SecureSession.seal/open` : nonce `u32 dir | u64 ctr`, aad `"CBSY1"`, tag 16 ; compteur hors ordre ⇒ `REPLAY` et session **morte** (toute opération suivante échoue).
4. Vecteurs : graines fixes (texte public dérivé, comme `test-vectors.json`), sorties attendues (clés, `h`, SAS, `sid`, 3 trames chiffrées, et refus : statique inattendue, tag altéré d'un octet, compteur rejoué, version 2). ≥ 25 cas. `CASTBRIDGE_WRITE_VECTORS=1` régénère.
5. Mesure JVM (information, dans le rapport) : coût d'une poignée de main et débit `seal` sur 1 Mio.
6. `docs/SYNC-PROTOCOL.md` § 2 : messages, dérivations, SAS, limites (pas de PFS côté TV si sa graine est compromise *pendant* la session, double chiffrement sur Bluetooth).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.link.SecureSessionTest' --tests 'castbridge.core.link.X25519LiteTest'   # vert
grep -c '"case"' tools/activation/sync-vectors.json    # ≥ 25
grep -rn 'Ed25519\|import android' android/core/src/main/kotlin/castbridge/core/link/SecureSession.kt | wc -l   # 0
```

## Cas limites
Clé publique « petit ordre » (tout-zéro) ⇒ `BAD_LENGTH`/refus ; msg reçu deux fois ⇒ `REPLAY` ; trame > 65 535 ⇒ refus avant déchiffrement ; `SecureRandom` injecté dans les tests (déterminisme).

## À ne pas faire
Pas de bibliothèque (BouncyCastle, Tink) ; pas de signature Ed25519 ; ne pas réutiliser la clé de session pour autre chose que `CBSY` ; ne pas écrire de texte utilisateur.

## Rapport
`STATUT`, choix X25519 (W4 ou Lite), mesures JVM, vecteurs, signatures finales.
