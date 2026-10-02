# w2-01 — Révocation et numéro de séquence persistés sur CastBridge-TV

**Vague 2 · Effort M (≈ 2-3 j) · Statut PRÊT** (après fusion de w1-05, qui stabilise `ActivationCenter`). Branche `claude/sonnet-w2-01`. Rapport : `docs/agent-reports/sonnet-w2-01.md`.

## Objectif
1. La TV **persiste** le dernier numéro de séquence vu par clé (`SeqState`) et une liste de révocation (`RevocationState` : clés et postes), via `SafeFile`.
2. Elle accepte une **liste de révocation `cbr1`** par les trois canaux existants (fichier `revocations` dans `Download/CastBridge/`, trame Bluetooth du canal propriétaire, `POST /api/activation/install` avec un corps `cbr1.…`), signée par une clé de l'anneau ayant la portée `REVOKE`.
3. Une activation signée par une clé révoquée, ou pour un poste révoqué, est refusée **et** une activation déjà installée dont la clé/le poste est révoqué cesse de compter (la TV se verrouille proprement, rien n'est supprimé).
4. L'identifiant d'une **clé de secours hors ligne** est accepté dès maintenant (ligne dans le fichier de clés de confiance : hors dépôt ; ici seulement la prise en charge).

## Pourquoi (preuves)
- `android/receiver/src/main/kotlin/castbridge/receiver/ActivationCenter.kt:105` : `receiver() = ActivationReceiver(ring, trusted, fp, Subject.TV)` → `C/owner/FeatureGate.kt:83-85` : `revocations = RevocationState()` vide et `ActivationVerifier(... )` avec `C/owner/Activation.kt:134` `seqState = SeqState()` neuf à chaque appel ; `ActivationCenter.kt:47` `ring = KeyRing(trusted)` sans `revoked`.
- `grep -rn 'Revocation\|cbr1' android/receiver` : aucun traitement ; `C/owner/License.kt:215` `RevocationNotice.verify(token, ring): RevocationState?` existe et n'est appelé que par les tests.
- Conséquence : un outil d'émission volé (téléphone propriétaire, bureau) reste accepté par tout le parc jusqu'à une nouvelle APK ; un poste transféré garde ses droits sur l'ancienne TV. Modèle de menace : profil (d), le plus grave.
- Audit : SE-1 (amélioration n° 1).

## Fichiers possédés
`R/ActivationCenter.kt`, `R/OwnerBtHost.kt`, `C/owner/Activation.kt` (`ActivationVerifier`, `RevocationState`, `TvGate` : ajout d'un filtre « révoqué »), `C/owner/Envelope.kt` (`SeqState` : `encode/decode`), `C/owner/License.kt` (`RevocationNotice` : `merge`), `C/owner/OwnerFrames.kt` (nouveau type de trame `REVOCATION`, additif), `C/owner/FeatureGate.kt` (**`ActivationReceiver` seulement** : paramètres `seqState`, `revocations`, `onRevocation`), `R/RentalHub.kt` (**`ActivationInstallApi` seulement** : accepter un corps `cbr1`), `android/core/src/test/kotlin/castbridge/core/owner/LicenseAndGateTest.kt`, nouveau `android/core/src/test/kotlin/castbridge/core/owner/RevocationOnTvTest.kt`, `docs/ACTIVATION-FORMAT.md` (§ « Révocation côté TV »). **Hors zone** : `TrialPolicy`, `KeyBadge`, `ActivationActivity` (w2-03), `TvService` (w2-07), `Keys.kt` (w1-05/w2-15 ; utiliser `KeyRing(trusted, revoked)` tel quel).

## Étapes
1. Cœur : `SeqState.encode(): String` / `SeqState.decode(text)` (format `kid<TAB>seq` par ligne) ; `RevocationState.encode/decode` (`key<TAB>kid` / `seat<TAB>licence|poste<TAB>ms`) ; `RevocationState.merge(other)` (union, date max) ; `ActivationReceiver` reçoit `seqState` et `revocations` **partagés** (même instance pour toute la vie du process) et un `onRevocation: (RevocationState) -> Unit`.
2. `ActivationReceiver.receive` : si le corps commence par `cbr1.` → `RevocationNotice.verify` avec l'anneau ; portée `REVOKE` exigée ; fusion ; `onRevocation` ; résultat `ActivationResult.RevocationApplied(n)` (nouveau, additif). Dans `OwnerFrames`, type `REVOCATION` (même charge utile).
3. `TvGate.evaluate` : paramètre `revocations: RevocationState` ; une activation dont `kid ∈ revoked.keys` ou `(licenseId, seat) ∈ revoked.seats` avec date ≥ `issuedAt` ne compte plus.
4. `ActivationCenter` : fichiers `seq.txt` et `revocations.txt` dans `filesDir` via `SafeFile` (lecture au `init`, écriture après chaque acceptation) ; `ring = KeyRing(trusted, revocations.keys)` reconstruit après chaque révocation ; `scanFiles()` lit aussi `Download/CastBridge/revocations` (mêmes volumes que le fichier `activation`) ; `receiver()` devient une valeur unique.
5. `OwnerBtHost` : dispatch de la trame `REVOCATION` vers `ActivationCenter.receive`. `ActivationInstallApi` : si le corps est `cbr1`, même chemin (PIN requis, inchangé).
6. Tests (`RevocationOnTvTest`, JVM, avec les clés de test des vecteurs) : (a) activation acceptée, puis `cbr1` révoquant sa clé → `TvGate` ne compte plus ; (b) poste révoqué ; (c) `cbr1` signé par une clé sans `REVOKE` refusé ; (d) `cbr1` plus ancien qu'un déjà appliqué : idempotent ; (e) séquence : rejouer une activation de `seq` inférieur → `STALE_SEQUENCE` **après** décodage de `seq.txt` (nouvelle instance) ; (f) redémarrage (ré-encodage/décodage) conserve tout ; (g) la clé de secours (kid présent dans l'anneau, jamais révoquée) révoque la clé principale.
7. `docs/ACTIVATION-FORMAT.md` : § « La TV applique une liste de révocation » (canaux, fichiers, idempotence, ce qui se passe pour les activations installées).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*'     # vert, ≥ 7 nouveaux tests
python3 tools/activation/verify_vectors.py                                       # inchangé (vecteurs `revocation` déjà présents : 5 cas)
cd android && gradle --offline :receiver:compileDebugKotlin                      # compile (si SDK)
```
Observable (campagne, étape 25) : installer une clé de production, puis déposer `Download/CastBridge/revocations` contenant un `cbr1` qui révoque son `kid` : la TV passe « SANS CLÉ » / « révoquée », `GET /api/activation` → `keyInstalled:false`.

## Cas limites
- Révoquer la **seule** clé connue bloque toute activation future : le refuser si l'anneau n'a plus aucune clé non révoquée avec `ISSUE_*` (message dans le rapport), sauf si une clé de secours reste.
- Une révocation de poste ne doit jamais supprimer des lots/locations (seul `RentalSweeper` supprime) : vérifier que `RentalHub.statuses` continue de fonctionner avec une activation révoquée (location suspendue, pas détruite).
- `seq.txt` perdu : on repart à zéro (comme aujourd'hui) ; le signaler dans le journal.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret ; ne pas changer le format `cbx1`/`cbr1` ; ne pas modifier les vecteurs ; textes utilisateur en français (« Clé révoquée : contactez le vendeur »).

## Rapport
`STATUT`, formats de `seq.txt`/`revocations.txt`, tests ajoutés, limites.
