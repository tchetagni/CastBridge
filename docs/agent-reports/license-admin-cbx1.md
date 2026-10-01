STATUT: EN COURS

Cahier : `docs/agent-briefs/license-admin-cbx1.md` · branche `claude/license-admin-cbx1` (base `origin/integration/agents`).
Note d'environnement : `./mvnw` ne peut pas télécharger Maven ici (réseau) ; les tests sont lancés avec le `mvn` du système (même version de module, hors ligne).

- 2026-10-01 18:40 | port Java de l'enveloppe `cbx1` (Envelope, EnvelopeIssuer, EnvelopeVerifier), portées POLICY/REACTIVATE, 3 tests rouges verts | commit à venir
- 2026-10-01 18:55 | bout en bout : `tools/activation/server-issued.json` + `ServerIssuedVectorsTest` (Java) + `ServerIssuedActivationTest` (core, non exécutable ici : plugin Android indisponible hors ligne, à lancer par le coordinateur) ; docs LICENSE-ADMIN et HANDOFF | commit à venir
NOTE (non bloquante) : le cahier dit « ISSUE_TRIAL, ISSUE_PRODUCTION, REVOKE et POLICY seulement » ; j'ai gardé aussi `REACTIVATE` et `REGISTRY` car `ACTIVATION-FORMAT.md` § 2 et la clé « server » des vecteurs les listent (et `WireFormatVectorsTest` exige l'égalité avec les vecteurs). Ni `TRANSFER` ni `COMMAND_OPEN_ALL` (testé).
