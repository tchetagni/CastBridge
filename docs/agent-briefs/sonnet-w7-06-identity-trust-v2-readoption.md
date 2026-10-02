# w7-06 — Identité par clés, registre de confiance v2, ré-adoption, politique de la fenêtre d'association (cœur)

**Vague 7a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (prend `StaticKey` de w7-03 ; si absent, un `typealias` local sur `ByteArray` et le dire).** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 4.2, § 4.4, § 2 (problèmes 3 et 6). Branche `claude/sonnet-w7-06`. Rapport : `docs/agent-reports/sonnet-w7-06.md`.

## Objectif
(1) `TrustRegistry` reconnaît un téléphone par **adresse OU clé publique** (ligne `K`), charge les fichiers v1 sans clé ; (2) `HelloHandler`/`HelloInfo` transportent `pub=`, `caps=`, `act=` (additif, anciens téléphones/TV inchangés) et une constante `SYNC_SERVICE_UUID …0006` ; (3) `Readoption` : décision pure « ce téléphone (clé inconnue, adresse connue / clé connue, adresse inconnue / tout inconnu) + SAS + code foyer ⇒ dialogue direct, fenêtre, ou refus » ; (4) `PairingWindowPolicy` : auto-ouverture 10 min sans téléphone de confiance, ouverture 2 min sur toc, remise à zéro du compteur de refus à la fin du blocage ; (5) `PhoneKey`/`TvIdentity` (épinglage TOFU, empreinte `xxxx-xxxx`, `IDENTITY_CHANGED`).

## Pourquoi (preuves)
- `C/trust/TrustRegistry.kt:8-9` (`TrustedPhone(address,name,addedAt,lastSeen)`), `:122-150` (lignes `I/P/T/C`, somme SHA-256) : ajouter `K` sans casser `intact()` ni les fichiers existants.
- `C/trust/HelloHandler.kt:36-54` : décision par `isBonded`/`isTrusted(address)` ; `C/tv/BtProtocol.kt:435-463` (`HelloInfo` clés inconnues ignorées : extension sûre).
- `C/trust/PairingSession.kt:21-27` (états), `:106-111` (compteur de refus jamais remis à zéro : correctif demandé).
- `S/TvLink.kt:153-173` (`recoverKnownTv`) : la reprise d'un téléphone réinstallé repose sur l'adresse seule ; `C/trust/LinkMachine.kt:30` (`TvForgotMe`) et `BtProtocol.HINT_*` : conserver.
- DESIGN-W6 § 3.3 (TOFU, M-IDENTITY-CHANGED) : même règle, même empreinte.

## Fichiers possédés
Modifiés : `C/trust/TrustRegistry.kt`, `C/trust/HelloHandler.kt`, `C/trust/PairingSession.kt`, `C/tv/BtProtocol.kt` (**seulement** `HelloInfo` + constante `SYNC_SERVICE_UUID`), `CT/TrustTest.kt`. Nouveaux : `C/link/Identity.kt`, `C/link/Readoption.kt`, `C/link/PairingWindowPolicy.kt`, `CT/link/IdentityTest.kt`, `CT/link/ReadoptionTest.kt`. **Hors zone** : `C/link/SecureSession.kt`, `R/**`, `S/**`.

## Signatures à respecter (contrat pour w7-13, w7-14, w7-16, w7-18)
```kotlin
// TrustRegistry (additif)
fun trust(address: String, name: String, pub: ByteArray? = null)
fun find(address: String?, pub: ByteArray?): TrustedPhone?      // adresse OU clé
fun bindKey(address: String, pub: ByteArray): Boolean            // apprend la clé d'un téléphone v1
val TrustedPhone.pub: ByteArray?  // nouveau champ, null = inconnu
// HelloInfo (additif) : pub: ByteArray? (b64url), caps: Int, actDigest: String? ; encode/decode rétrocompatibles
object BtProtocol { const val SYNC_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000006" }
// C/link/Identity.kt
data class TvIdentity(val installId: String?, val pub: ByteArray, val fingerprint: String)
sealed class PinCheck { object Ok; object FirstSeen; data class Changed(val oldFp: String, val newFp: String) }
object Identity { fun fingerprint(pub: ByteArray): String; fun check(pinned: ByteArray?, seen: ByteArray): PinCheck }
// C/link/Readoption.kt
enum class Known { ADDRESS_ONLY, KEY_ONLY, BOTH, NONE }
sealed class ReadoptDecision { object DirectDialog /* SAS + Autoriser */; object NeedWindow; data class Refuse(val reason: String) }
object Readoption { fun decide(known: Known, householdOk: Boolean?, windowOpen: Boolean, blocked: Boolean): ReadoptDecision }
// C/link/PairingWindowPolicy.kt
data class WindowPlan(val open: Boolean, val durationMs: Long, val askVisible: Boolean, val banner: String?)
object PairingWindowPolicy { fun onHome(trustedCount: Int, visibleAskedThisProcess: Boolean): WindowPlan; fun onKnock(trustedCount: Int): WindowPlan; const val AUTO_MS = 600_000L; const val KNOCK_MS = 120_000L }
```

## Étapes
1. Registre : ligne `K\t<addr>\t<b64url pub>\t<fp>` ; `find` ; `bindKey` ; sauvegarde atomique inchangée ; test : fichier v1 chargé, fichier v2 relu, `intact` toujours vrai, clé liée à une seule adresse (une clé qui réapparaît sous une autre adresse ⇒ `moveAddress` + ancienne adresse retirée).
2. `HelloHandler.handle(peer, name, requestTrust, peerPub: ByteArray?)` (paramètre additif, défaut `null`) : adresse inconnue **mais clé connue** ⇒ `ERR_UNTRUSTED` avec `HINT_SAME_INSTALL` **et** signal `readoptable` (callback `onReadoptable(peer, pub)`) pour que la TV ouvre le dialogue direct (w7-14) ; clé fournie et adresse connue sans clé ⇒ `bindKey`.
3. `PairingSession` : à l'expiration d'un blocage, `denials = 0` ; nouveau `openFor(ms)` (10 min auto / 2 min toc) ; `Decision` inchangée.
4. `Identity`, `Readoption`, `PairingWindowPolicy` : fonctions pures, textes du bandeau en français (« Prêt à être associé · Sur votre téléphone, ouvrez CastBridge et touchez « Ajouter ma TV » »).
5. Tests (≥ 45) : matrice `Known × householdOk × windowOpen × blocked` figée (`EXPECTED_READOPT`), `onHome(0)` ⇒ ouvert 10 min, `onHome(1)` ⇒ fermé, `onKnock` ⇒ 2 min, hello v1/v2 croisés (ancien téléphone : réponse **octet pour octet** identique à aujourd'hui : test de non-régression sur `HelloCompatTest` existant), compteur de refus remis à zéro.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.TrustTest' --tests 'castbridge.core.link.IdentityTest' --tests 'castbridge.core.link.ReadoptionTest' --tests '*HelloCompat*'   # vert
git diff origin/integration/agents -- android/core/src/main/kotlin/castbridge/core/tv/BtProtocol.kt | grep '^-' | grep -v '^---' | wc -l   # 0 ligne supprimée (additif)
```

## Cas limites
Deux téléphones avec la même clé (clonage) ⇒ refus `Refuse("clé déjà liée à un autre téléphone")` ; `pub` mal formée (≠ 32 octets) ⇒ ignorée ; registre endommagé ⇒ comportement existant (`installId` neuf).

## À ne pas faire
Pas de poignée de main ici (w7-03) ; pas d'écran ; ne pas changer la durée des jetons ni `TvAuth`.

## Rapport
`STATUT`, format de la ligne `K`, matrice `EXPECTED_READOPT`, signatures.
