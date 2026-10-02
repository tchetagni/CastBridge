# w1-13 — Documentation et commentaires honnêtes sur le chiffrement des locations

**Vague 1 · Effort S (≈ 2 h) · Statut PRÊT** (décision D4 recommandée : accepter la limite pour l'instant). Branche `claude/sonnet-w1-13`. Rapport : `docs/agent-reports/sonnet-w1-13.md`.

## Objectif
La documentation, les commentaires de code et les noms de tests disent exactement ce que la protection des lots loués fait et ne fait pas, pour que le propriétaire décide en connaissance de cause et que personne ne s'appuie sur une garantie fausse.

## Pourquoi (preuves)
- `android/core/src/main/kotlin/castbridge/core/lots/RentalKeys.kt:70` : commentaire « the factor VALUES stay secret to whoever lacks the TV » — **faux** : la KEK (`:75`, `LotKeys.kek(s.associateWith { fp.byKind.getValue(it) })`) se calcule à partir des empreintes qui figurent en clair dans la cible de l'activation (`C/owner/Envelope.kt:57` `factor=NOM|empreinte`) et dans `device-request.txt`.
- `android/core/src/main/kotlin/castbridge/core/lots/RentalApi.kt:35-41` : le lot est déchiffré et **réécrit en clair** dans `TvLotStore` ; `RentalVault.readLot/putLot` (`RentalVault.kt:67,75`) ne sont appelés nulle part. `docs/RENTAL-LOTS.md:61,110` et le test `RentalTest.afterTheKeyIsGoneACopiedOrRestoredFileIsNoise` (`android/core/src/test/kotlin/castbridge/core/lots/RentalTest.kt:384`) affirment une protection que le chemin réel n'offre pas.
- `docs/TRIAL-EDITION.md` § 9 décrit déjà honnêtement la limite « qui connaît les facteurs dérive la KEK », mais pas que **le fichier d'activation les contient**.
- Audit : SE-9, désaccord n° 1 avec l'audit Opus.

## Fichiers possédés
`docs/RENTAL-LOTS.md` (§ 3, § 11, § 12), `docs/TRIAL-EDITION.md` (§ 9 seulement), `android/core/src/main/kotlin/castbridge/core/lots/RentalKeys.kt` (**commentaires seulement**, aucun changement de code), `android/core/src/test/kotlin/castbridge/core/lots/RentalTest.kt` (**nom et KDoc du test seulement** — coordonner : w1-02 possède ce fichier pour ses ajouts ; ici ne changer qu'une ligne de nom + un commentaire, en fin de cahier, et le signaler). **Hors zone** : tout le reste.

## Étapes
1. `RentalKeys.kt:70` : remplacer par « the KEK is derived from the factor fingerprints, which are PUBLIC to whoever holds the activation file (target `factor=` lines) or `device-request.txt`: this box only binds the key to a TV identity, it does not keep the key secret from someone who has the token. See docs/RENTAL-LOTS.md § 11. »
2. `docs/RENTAL-LOTS.md` § 3 : encadré « Ce que l'enveloppe garantit / ne garantit pas » ; § 11 « Limites honnêtes » : ajouter (a) « le fichier `activation` + le lot scellé suffisent, sans la TV, à déchiffrer le lot (≈ 20 lignes de script à partir du § 10.4) », (b) « le lot installé est stocké en clair dans le magasin de lots de la TV (`RentalApi`) ; le coffre chiffré `RentalVault.putLot/readLot` existe mais n'est pas utilisé » ; (c) la décision D4 (accepter jusqu'à contenu relu payant ; correction cible : X25519 Keystore, format v2, trois vérificateurs, effort L) ; (d) conseil pratique : ne pas conserver le fichier `activation` sur la clé USB du client après installation (et pourquoi) ; (e) rappeler que le vrai bien précieux est la **clé d'émission** (voir w2-01).
3. § 12 : marquer le test `afterTheKeyIsGoneACopiedOrRestoredFileIsNoise` comme couvrant **le coffre** et non le chemin HTTP, et lister le test manquant (restauration après expiration par `/api/rental/install` → 422, prévu dans w3-14 étape 30).
4. `docs/TRIAL-EDITION.md` § 9 : une phrase « les empreintes figurent dans l'activation elle-même ».
5. `RentalTest.kt` : renommer le test en `afterTheKeyIsGoneTheVaultCopyIsNoise` (KDoc : « vault only; the installed copy in TvLotStore is clear text, see RENTAL-LOTS § 11 »).

## Critères d'acceptation
```sh
grep -n 'stay secret' android/core/src/main/kotlin/castbridge/core/lots/RentalKeys.kt    # 0 hit
grep -n 'en clair' docs/RENTAL-LOTS.md                                                      # ≥ 2
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.RentalTest'       # vert (renommage seulement)
git diff --stat android/core/src/main                                                      # seul RentalKeys.kt, et seulement des lignes de commentaire
```

## Cas limites
- Ne pas supprimer `putLot/readLot` (w3-03 ou une vague ultérieure pourra les brancher).

## À ne pas faire
Pas de commit sur les branches partagées, pas de changement de code, pas de secret, pas de modification des vecteurs.

## Rapport
`STATUT`, extraits des paragraphes ajoutés, confirmation que la suite `lots.*` est verte.
