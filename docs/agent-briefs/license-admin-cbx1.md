# Brief : aligner le module des licences du serveur sur l'enveloppe `cbx1` (correctif)

Agent cloud. Base `origin/integration/agents` (qui contient le module `license-admin`). Branche `claude/license-admin-cbx1`. Protocole `docs/COORDINATION.md` (rapport vivant `docs/agent-reports/license-admin-cbx1.md`). Pas de PR, pas de main, aucun accès au serveur. Français, aucun secret.
Contexte : le module des licences de `backend/` a été écrit sur le format `cba1` ; entre-temps l'agent des droits a généralisé l'enveloppe en **`cbx1`** (`docs/ACTIVATION-FORMAT.md`, `core/.../owner/Envelope.kt`, `Activation.kt`, `ActivationIssuer.kt`, `tools/activation/test-vectors.json` à jour). Les outils de bureau et de téléphone et la TV parlent déjà `cbx1`. **Le serveur doit produire et lire exactement les mêmes octets.**

## État mesuré après fusion (`cd backend && ./mvnw -q -B clean test`, 2026-10-01)
130 tests, **127 verts, 1 échec et 2 erreurs**, tous dans les tests de vecteurs :
- `WireFormatVectorsTest` (2 erreurs) : `No enum constant castbridge.server.licenses.ActivationSigner.SignerScope.POLICY` : la portée `POLICY` (ordres de gestion) n'existe pas côté serveur.
- `LicenceVectorsTest.everyLicenceVectorGivesTheSameStateThroughTheServerImport` (1 échec) : l'identifiant de poste ou l'état de licence attendu diffère (`lic-0001|f36757c226dd2ee4`) : la dérivation du poste/numéro de séquence a changé avec `cbx1`.
- Tous les autres tests passent : ne les casse pas.
(Piège connu : si `Found more than one migration with version 4` apparaît, c'est un fichier périmé dans `target/classes` : `./mvnw clean`.)

## À faire
1. **Port Java de `cbx1`** : enveloppe générique (`type` = activation | commande | ordre | révocation…, `kid`, `seq` par clé, `nonce`, `issuedAt`, `notBefore`, `notAfter`, cible, corps, signature), codage exact du format, **numéro de séquence strictement croissant par clé** (une activation plus ancienne que la dernière vue est refusée ; la même est acceptée : fichier réinstallé), préfixe `cbx1`. **Tous les vecteurs `build-activation`, `build-command`, `activation`, `licence`, `revocation` de `tools/activation/test-vectors.json` doivent donner les mêmes octets ou le même refus** (rejeu des vecteurs dans `WireFormatVectorsTest` et `LicenceVectorsTest`, sans en modifier un seul : s'il te semble faux, **écris une QUESTION**, ne le corrige pas).
2. **Portées de clé** : ajoute `POLICY` (et toute portée présente dans `Keys.kt` du cœur : `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `COMMAND_*`, `TRANSFER`, `POLICY`, `REVOKE`…) ; **la clé du serveur garde `ISSUE_TRIAL`, `ISSUE_PRODUCTION`, `REVOKE` et `POLICY` seulement : ni transfert, ni « tout ouvert »** (test qui échoue si on tente de signer l'un des deux, même avec un droit « tout ouvert » dans la demande).
3. **Registre et postes** : dérivation de l'identifiant de poste et rejeu des événements identiques au cœur (`SeatIds`, `License.kt`) ; vérifie le comptage des postes, la ré-activation sans poste consommé, le transfert enregistré (jamais signé) avec les nouveaux numéros de séquence.
4. **Rétrocompatibilité** : si des activations `cba1` ont déjà été émises (aucune en production à ce jour), prévois la lecture ; sinon dis-le et supprime l'ancien code **sans casser les tests existants**.
5. **Tests** : toute la suite du module reste verte (130 → 130+), les 3 tests rouges deviennent verts ; ajoute un test de **bout en bout** : une activation émise par le serveur est **vérifiée par le vérificateur du cœur** (`castbridge.core.owner.ActivationVerifier`) — le cœur est en Kotlin : si tu ne peux pas l'appeler depuis Maven, ajoute à `tools/activation/test-vectors.json` **(sans modifier l'existant)** un fichier `tools/activation/server-issued.json` contenant des activations produites par le serveur et un test Kotlin `ServerIssuedActivationTest` dans `core` qui les vérifie (le coordinateur l'exécute).
6. Docs : `docs/LICENSE-ADMIN.md` (format `cbx1`), `docs/HANDOFF.md`.

## À ne pas faire
Aucune clé réelle ; aucun accès au serveur ; ne modifie ni `docs/ACTIVATION-FORMAT.md` ni les vecteurs existants ; ne change pas le comportement « module éteint par défaut ».

## Coordination
Rapport vivant ; relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
