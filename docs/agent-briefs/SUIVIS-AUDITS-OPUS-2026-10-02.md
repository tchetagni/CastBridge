# Suites mineures des audits Opus (2026-10-02) — à traiter en petit cahier Sonnet/Haiku

Origine : contre-audit de w4-11-fix (délégation aux agents de terrain), accepté et fusionné.
1. `TvGate` (Activation.kt ~l.215, `minOf{endsAt-startsAt}`) : faire `if (endsAt<=startsAt) 0 else subtractExact` ; refuser dans `ActivationVerifier` tout `Usage` avec `endsAt<=startsAt`. (Les clés du propriétaire qui déborderaient verrouillent la TV : échec fermé, mais le calcul doit être sûr.)
2. `withDelegated` / `License.replay` : l'union des portées de plusieurs mandats d'un même agent donne à un mandat étroit les portées d'un mandat large. Correctif exact : remplacer `validity`/`moreValidity` par `grants: List<Pair<LongRange, Set<KeyScope>>>` avec `allowsAt(scope, t)` ; `replay` utilise `allowsAt` ; ajouter un vecteur à deux mandats de portées différentes. (Mineur tant que la sanction réelle reste la révocation, qui tue tous les mandats du kid.)
3. Tests de `DelegatedVerifier` : asserter le motif du refus (`assertContains`), pas seulement `KEY_NOT_ALLOWED`.
4. `DelegatedVerifier.kt:17` : la séquence du mandat est enregistrée même si l'activation est ensuite refusée : documenter ou passer par un brouillon.
5. Quand l'application TV branchera la délégation : revérifier au démarrage par `DelegatedVerifier`, JAMAIS par `ActivationVerifier` avec `withDelegated` (les limites du mandat sauteraient).
6. Parité Java/Python (w4-17) : `lastSeq` au format `owner/agent`, `int` strict en sortie JSON de la grille de prix ; hash du journal de ventes de 16 hex à passer à 32 hex ou plus en v2.
