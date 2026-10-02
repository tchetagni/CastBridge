# w6-12 — CastBridge-TV : clé d'installation de signature, route `GET /api/activation/proof?nonce=`, trames Bluetooth `PROOF_REQUEST/PROOF`, empreinte dans « À propos »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w6-03)
> **Groupe : W6c-1** (vague W6c) · prérequis : w6-03, w4-03 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin :core:test --tests '*Proof*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (§ 2 D-W6-L2/L6/L8, § 3 « w6-12 », § 4). Il **prévaut**. En plus : la demande d'appareil (`requestText()`, `device-request.txt`, trame `DEVICE_INFO`, `/api/activation/request`) ajoute **`sign=ed25519|<64 hex>`** après `install=` ; la TV vérifie `install=` d'une activation contre `InstallSigner.keyId` et l'**accepte** en `mismatch` (jamais de refus net) ; `GET /api/activation` expose `"installBound": "bound"|"unbound"|"mismatch"` et l'écran d'activation le dit ; `seq` de preuve persistant et **strictement** croissant ; preuve d'une TV à clé compacte : `activation=` absent **et** `compact=1`. Prérequis supplémentaire : `claude/sonnet-w6-03-fix`.

**Vague 6c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w6-03 ; après w4-03 si fusionné pour `KeystoreWrapper`, sinon repli fichier).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.3, § 7 (4). Branche `claude/sonnet-w6-12`. Rapport : `docs/agent-reports/sonnet-w6-12.md`.

## Objectif
(1) `ActivationCenter.installSigner()` : graine Ed25519 créée au premier besoin, scellée par `KeystoreWrapper` (w4-03) ou, à défaut, fichier privé `files/install-signer.txt` avec état « protection logicielle » ; `seq` persistant ; (2) route **`GET /api/activation/proof?nonce=<32 hex>`** (PIN de connexion **ou** jeton de téléphone de confiance en `X-CB-Pin`, comme `/api/activation`) ⇒ corps texte = enveloppe `proof` (`TvProof.build`) ; 400 sans nonce valide ; **remplace** toute route `/api/activation/proof` sans nonce (w5-16) ; limitée à 6 réponses/min ; (3) `OwnerBtHost` : trames `PROOF_REQUEST` → `PROOF` sur le canal propriétaire (`…0005`, déjà ouvert même TV verrouillée) ; (4) « À propos » : ligne « Identité de cette installation : xxxx-xxxx-xxxx-xxxx » (`InstallSigner.fingerprintText`) pour la comparaison TOFU ; (5) `GET /api/activation` ajoute `"proof":true,"installKid":"…"`.

## Pourquoi (preuves)
- `R/ActivationCenter.kt:87-107` (`access()`, `state()`, `trial()`, `statusFields()`), `R/TvService.kt:840` (`GET /api/activation`), `R/RentalHub.kt:71-79` (`/api/activation/request|install` : **même style** pour la nouvelle route), `R/OwnerBtHost.kt:29` (`OwnerChannelServer` : ajouter le traitement des types 9/10 **dans la colle Android** ; `C/owner/OwnerChannel.kt` reste « non pris en charge » pour les autres types : si vous devez l'étendre, c'est **une** branche `when` additive, à signaler), `C/owner/OwnerFrames.kt` (types 9/10 de w6-03), `C/owner/TvProof.kt` (w6-03), `R/KeystoreWrapper.kt` (w4-03).
- `ActivationActivity`/`À propos` : `grep -rn "À propos" android/receiver/src/main/kotlin` pour l'emplacement exact (nommer le fichier dans le rapport ; s'il s'agit de `PlayerActivity.kt`, **ne pas l'éditer** : w6-14 le possède ; fournir `ActivationCenter.identityLine()` et demander à w6-14 de l'afficher).

## Fichiers possédés
`R/ActivationCenter.kt`, `R/RentalHub.kt`, `R/OwnerBtHost.kt`, `R/KeystoreWrapper.kt` (si w4-03 fusionné : méthode additive `sealSeed/openSeed`), `R/ActivationActivity.kt`, `android/core/src/main/resources/castbridge/admin.html` (ligne « identité »), `C/owner/OwnerChannel.kt` (branche additive seulement). **Hors zone** : `R/TvService.kt` (w6-13), `R/PlayerActivity.kt`/`R/HomeScreen.kt`/`R/KeyBadgeOverlay.kt` (w6-14), `R/BtServer.kt` (w6-15), `C/owner/TvProof.kt`.

## Étapes
1. `InstallSignerStore` Android (scellé Keystore / fichier) ; `ActivationCenter.installSigner()`, `identityLine()`, `proofSeq()`.
2. `ActivationCenter.proof(nonce): String` : `TvProof.build(signer, fingerprints, nonce, seq++, TvClock.now, access(), productionTokenOrNull(), tvName)` où `productionTokenOrNull()` = le jeton `cbx1` de l'activation de production **qui compte** (`installed.all()` filtrée comme `TvGate.evaluate` : la plus récente dont le plafond n'est pas passé) ; `grace` si `state() is Grace`.
3. Route dans `RentalHub` (ou l'`ApiExtension` existante la plus proche) : `GET /api/activation/proof` ; limite 6/min ; nonce `[0-9a-f]{32}` ; journal `TvJournal`? **non** (pas parental) ; télémétrie : aucune.
4. `OwnerBtHost` : `PROOF_REQUEST(payload = nonce)` ⇒ `PROOF(payload = enveloppe)` ; 3 demandes/min par liaison.
5. Vérification manuelle : `curl -H 'X-CB-Pin: <code>' 'http://<tv>:<port>/api/activation/proof?nonce=00…01'` ⇒ `cbx1.` ; `python3 tools/activation/verify_vectors.py` ne change pas (pas de vecteur TV ici).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile (SDK)
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'   # vert
grep -n 'activation/proof' android/receiver/src/main/kotlin/castbridge/receiver/RentalHub.kt   # ≥ 1 ; et 0 route sans nonce
```
Observable (émulateur) : la route répond, deux appels avec le même nonce donnent deux enveloppes différentes (`seq`), « À propos » montre l'empreinte.

## Cas limites
TV verrouillée (`Locked`) : la route répond quand même (`state=locked`, pas de jeton) : le téléphone a besoin de savoir ; TV en grâce ⇒ `state=grace` ; `KeystoreWrapper` absent/raté ⇒ repli fichier + `installKeyProtection=software` ; réinstallation ⇒ nouvelle clé (attendu : le téléphone demandera confirmation).

## À ne pas faire
Ne pas signer avec la clé X25519 ; ne pas exposer la graine ; pas de route sans nonce ; ne pas toucher `TvService.kt`.

## Rapport
`STATUT`, chemin exact de la route et des trames (pour w6-16), fichier de « À propos ».
