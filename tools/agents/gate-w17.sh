#!/usr/bin/env bash
# gate-w17.sh : porte de la vague W17 (la Boutique : vitrine téléphone + TV, demandes de location, parcours J).
# Aucun appareil, aucun serveur : tests JVM, vecteurs, routes, et le contrôle « aucun prix dans le cœur de la Boutique ».
#
# Usage :   tools/agents/gate-w17.sh
# Variable : GATE_W17_CORE_HARNESS=1 lance les tests JVM par tools/core-harness/run.sh (conteneur sans le plugin Android) au lieu de gradle d'android/.
# Sortie :  « GATE W17 : VERT » (code 0) ou « GATE W17 : ROUGE (<étape>) » (code 1).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

fail() { echo "GATE W17 : ROUGE ($1)"; exit 1; }

echo "== 1/4 tests JVM : store, parcours de la Boutique, lint =="
TESTS=(--tests 'castbridge.core.store.*' --tests 'castbridge.core.journey.Store*' --tests 'castbridge.core.lint.*')
if [ "${GATE_W17_CORE_HARNESS:-0}" = "1" ]; then
  tools/core-harness/run.sh :core:test "${TESTS[@]}" || fail "tests JVM"
else
  (cd android && bash "$ROOT/tools/agents/gradle-lock.sh" gradle --offline :core:test "${TESTS[@]}") || fail "tests JVM"
fi

echo "== 2/4 vecteurs de la Boutique =="
python3 tools/activation/verify_vectors.py --only store || fail "vecteurs"

echo "== 3/4 routes classées =="
python3 tools/tests/test_routes.py || fail "routes"

echo "== 4/4 aucun prix dans le cœur de la Boutique =="
if grep -rn 'XAF' android/core/src/main/kotlin/castbridge/core/store; then fail "prix trouvé dans le cœur de la Boutique"; fi

echo "GATE W17 : VERT"
