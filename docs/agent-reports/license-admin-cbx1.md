STATUT: TERMINÉ

Résumé : le module des licences du serveur parle `cbx1` (octets identiques au cœur) et l'ancien `cba1`/`cbr1` est supprimé (rien en production).
- Livré : `Envelope`, `EnvelopeIssuer`, `EnvelopeVerifier`, portées `REACTIVATE`/`POLICY`, `seq` par clé, événement `issue` avec `seq`, révocation `cbx1`, `server-issued.json`, `ServerIssuedActivationTest` (core), docs.
- Tests backend : avant 130 (127 verts, 1 échec, 2 erreurs) ; après 138, 0 échec, 0 erreur (2 ignorés comme avant).
- À faire par le coordinateur : `gradle :core:test --tests '*ServerIssuedActivationTest*'` (non exécutable ici, plugin Android hors ligne).
- Branche `claude/license-admin-cbx1`, dernier commit : voir `git log`.

Cahier : `docs/agent-briefs/license-admin-cbx1.md` · branche `claude/license-admin-cbx1` (base `origin/integration/agents`).
Note d'environnement : `./mvnw` ne peut pas télécharger Maven ici (réseau) ; les tests sont lancés avec le `mvn` du système (même version de module, hors ligne).

- 2026-10-01 18:40 | port Java de l'enveloppe `cbx1` (Envelope, EnvelopeIssuer, EnvelopeVerifier), portées POLICY/REACTIVATE, 3 tests rouges verts | commit à venir
- 2026-10-01 18:55 | bout en bout : `tools/activation/server-issued.json` + `ServerIssuedVectorsTest` (Java) + `ServerIssuedActivationTest` (core, non exécutable ici : plugin Android indisponible hors ligne, à lancer par le coordinateur) ; docs LICENSE-ADMIN et HANDOFF | commit à venir
NOTE (non bloquante) : le cahier dit « ISSUE_TRIAL, ISSUE_PRODUCTION, REVOKE et POLICY seulement » ; j'ai gardé aussi `REACTIVATE` et `REGISTRY` car `ACTIVATION-FORMAT.md` § 2 et la clé « server » des vecteurs les listent (et `WireFormatVectorsTest` exige l'égalité avec les vecteurs). Ni `TRANSFER` ni `COMMAND_OPEN_ALL` (testé).
