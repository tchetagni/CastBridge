# w7-07 — Résultat d'activation structuré (`RESULT` v2), trames `KNOCK`/`KEYRING` du canal propriétaire, message signé `keyring`

**Vague 7a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W7-PLUG-AND-PLAY-SYNC.md` § 2 (problème 1), D-W7-4. Branche `claude/sonnet-w7-07`. Rapport : `docs/agent-reports/sonnet-w7-07.md`.

## Objectif
(1) Le `RESULT` du canal propriétaire porte, **après** le texte libre (rétrocompatible), des lignes structurées `reason=<Rejection>`, `kid=<signataire>`, `knownKids=<liste>`, `tvVersion=`, `action=<UPDATE_TV|USE_DESK_TOOL|CHECK_DEVICE_CODE|ACCEPT_TERMS_ON_TV|CONFIRM_ON_TV|NONE>` ; (2) `ActivationScreenState.sendOutcome` produit un `SendOutcome` avec **action** et texte français précis (« Cette TV ne connaît pas la clé qui a signé (kid 3566…) : mettez CastBridge-TV à jour, ou émettez la clé avec l'outil de bureau ») ; (3) nouveau type d'enveloppe **`keyring`** (`cbx1`, portée `REGISTRY`) qui ajoute/retire une clé de confiance sur la TV sans recompiler ; (4) trames `KNOCK` (11) et `KEYRING` (12) dans `OwnerFrames`/`OwnerChannel`.

## Pourquoi (preuves)
- `C/owner/OwnerChannel.kt:36-40` (`resultFrame` = `[ok]` + message), `C/owner/OwnerFrames.kt:17-24` (types 1-8), `C/owner/ActivationScreenState.kt:43-44` (« Refusée par la TV : … » sans action), `C/owner/Activation.kt:143` (texte UNKNOWN_KEY), `R/ActivationCenter.kt:49-51` (`TRUSTED_KEYS` compilées), `docs/HANDOFF.md:26` (kid `35662fecbf07dbdf` ajouté… dans un build).
- `docs/ACTIVATION-FORMAT.md` § 3 (enveloppe : ajouter un type = un analyseur de corps + une portée, **jamais un second format**), § 7 (révocation : même mécanique de fusion), § 2 (portée `REGISTRY`).
- DESIGN-W6 § 4.5 recommandait déjà un message `keyring`.

## Fichiers possédés
Modifiés : `C/owner/OwnerFrames.kt`, `C/owner/OwnerChannel.kt`, `C/owner/ActivationScreenState.kt`, `C/owner/Envelope.kt` (type `keyring` dans la liste), `CT/owner/ActivationScreenStateTest.kt`, `CT/owner/OwnerChannelTest.kt` (s'il existe, sinon créer). Nouveaux : `C/owner/Keyring.kt`, `CT/owner/KeyringTest.kt`, vecteurs **ajoutés** à `tools/activation/test-vectors.json` (cas `keyring`, `build-keyring`) — **vérifier** que les cas existants restent octet pour octet identiques. `docs/ACTIVATION-FORMAT.md` : nouveau § 3.5 « Type `keyring` » et ligne `RESULT` v2 en § 5.2. **Hors zone** : `Activation.kt`, `Keys.kt`, `R/**`, `S/**`, `OL/**`.

## Signatures à respecter (contrat pour w7-13, w7-14, w7-22)
```kotlin
// OwnerFrames
const val KNOCK = 11        // console → TV : nom du téléphone (UTF-8) + 32 oct. pub X25519 (facultatif)
const val KEYRING = 12      // console → TV : jeton cbx1 type keyring ; réponse RESULT
data class ResultV2(val ok: Boolean, val text: String, val reason: Rejection?, val kid: String?, val knownKids: List<String>, val tvVersion: String?, val action: ResultAction)
enum class ResultAction { NONE, UPDATE_TV, USE_DESK_TOOL, CHECK_DEVICE_CODE, ACCEPT_TERMS_ON_TV, CONFIRM_ON_TV, RETRY }
object OwnerFrames { fun encodeResult(r: ResultV2): ByteArray; fun decodeResult(payload: ByteArray): ResultV2 /* v1 : reason=null, action déduite du texte = NONE */ }
// OwnerChannelServer : constructeur + onKnock: (name: String, pub: ByteArray?) -> Unit = {}, keyring: (String) -> ActivationResult = { Rejected(UNKNOWN_TYPE, "…") }, knownKids: () -> List<String> = { emptyList() }, tvVersion: String = ""
// Keyring (type keyring) : corps  add=<kid>|<pub b64>|<scopes,…>  (une ligne par clé, triées) / remove=<kid> (triées) ; cible any ; seq strictement croissant par clé ; portée REGISTRY
object Keyring { fun verify(token: String, ring: KeyRing, revoked: RevocationState, seq: SeqState, now: Long): KeyringResult; fun apply(result, ring): KeyRing }
```
Format RESULT v2 : `[ok:1]` + texte UTF-8 + `\n--\n` + lignes `clé=valeur` ; un ancien téléphone affiche le texte et ignore la suite (vérifier qu'il ne montre pas `--` : le texte v1 doit se terminer avant `\n--\n` et `OwnerChannelClient.sendActivation` existant lit `f.text.substringBefore("\n--\n")`).

## Étapes
1. `ResultV2` + codage ; `OwnerChannelServer` remplit `reason/kid/knownKids/tvVersion/action` (table `Rejection → action` figée par test : UNKNOWN_KEY→UPDATE_TV|USE_DESK_TOOL, WRONG_DEVICE→CHECK_DEVICE_CODE, WINDOW_CLOSED→USE_DESK_TOOL, « conditions non acceptées » (texte de `RentalHub`/`ActivationCenter`, passé par `activate`) → ACCEPT_TERMS_ON_TV, Accepted + stage → CONFIRM_ON_TV).
2. `ActivationScreenState.sendOutcome(r: ResultV2)` : textes français par action (catalogue dans le fichier, un par action, nommant le `kid` tronqué à 8 et la version TV) ; l'ancienne signature `sendOutcome(ok, message)` reste.
3. `KNOCK` : le serveur appelle `onKnock` et répond `RESULT ok` « Demande envoyée : validez sur la TV » ; limite 1 toc / 10 s par lien.
4. `Keyring` : enveloppe type `keyring`, vérification ordre `MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED (REGISTRY), BAD_KEYRING (syntaxe), STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED` ; `apply` = union/retrait, **jamais** retirer la clé qui a signé ni une clé du build (les clés compilées restent toujours) ; persistance par l'appelant (w7-13 : `files/keyring.txt`, `SafeFile`).
5. Vecteurs : ≥ 6 cas `keyring` (valide, portée absente, seq ancien, retrait de la clé signataire refusé, ajout + retrait, syntaxe) + 2 `build-keyring` ; Python : w7-11.
6. Doc ACTIVATION-FORMAT § 3.5 et § 5.2.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'    # vert, dont ActivationVectorsTest avec les anciens vecteurs intacts
git diff origin/integration/agents -- tools/activation/test-vectors.json | grep '^-' | grep -v '^---' | wc -l   # 0 (ajout seulement)
grep -c 'action=' android/core/src/main/kotlin/castbridge/core/owner/ActivationScreenState.kt   # ≥ 6 textes
```

## Cas limites
Texte v1 contenant `--` isolé : échapper (`\n--\n` seulement comme séparateur, texte sans ligne `--`) ; `knownKids` vide (build sans clé : impossible en verrouillé, mais tester) ; `KEYRING` reçu sur une TV ancienne ⇒ « Non pris en charge » (existant `:36`).

## À ne pas faire
Ne pas toucher `ActivationVerifier` ; ne pas écrire d'écran ; ne pas modifier les cas existants des vecteurs ; pas de clé secrète dans les vecteurs (graines de test publiques).

## Rapport
`STATUT`, table `Rejection → action → texte`, vecteurs ajoutés, signatures.
