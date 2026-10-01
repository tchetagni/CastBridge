#!/bin/sh
# Publish pipeline of the content database (docs/CONTENT-PUBLISH.md). RULE A: this script only BUILDS, in the repository and in
# an output folder; nothing runs on the server. The copy to the production server is the OWNER's decision: rsync, dry-run by default.
#
#   tools/publish-content.sh [--out DIR] [--version V] [--bump] [--sign-key PEM] [--skip-gradle-checks]
#   tools/publish-content.sh --rsync user@host:/srv/castbridge/content/          # prints what rsync WOULD do (--dry-run)
#   tools/publish-content.sh --rsync user@host:/srv/... --go                     # real transfer: also needs CASTBRIDGE_CONFIRM_PRODUCTION=yes
#
# Steps: 1 validate everything (graph, Apprendre packs, quiz banks, media manifest, budgets, tool tests, QA report format)
#        2 build the Apprendre packs   3 build ALL lots and the signed catalog   4 CONTENT-RELEASE.json + SHA256SUMS + rsync tree
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/content-release"; VERSION=""; BUMP=""; KEY="${CASTBRIDGE_LOT_SIGN_KEY:-}"; RSYNC=""; GO=""; SKIPG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT="$2"; shift 2;; --version) VERSION="$2"; shift 2;; --bump) BUMP="--bump"; shift;; --sign-key) KEY="$2"; shift 2;;
    --rsync) RSYNC="$2"; shift 2;; --go) GO=1; shift;; --skip-gradle-checks) SKIPG=1; shift;;
    -h|--help) sed -n '2,12p' "$0"; exit 0;; *) echo "option inconnue: $1" >&2; exit 2;;
  esac
done
PY=${PYTHON:-python3}
RUNNER=${GRADLE_RUNNER:-"$ROOT/tools/core-harness/run.sh"}   # :core is plain JVM: no Android plugin needed
say() { printf '\n== %s ==\n' "$*"; }

say "1/4 Validation"
$PY "$ROOT/tools/content-graph/gen_graph.py" --check
if [ -z "$SKIPG" ]; then
  "$RUNNER" -q :core:checkContentGraph
  "$RUNNER" -q :core:checkLearnContent
fi
# the quiz-bank sources use Python 3.12 syntax (f-strings with backslashes): QUIZ_PY=python3.12 ; an older Python skips this step LOUDLY
QPY=${QUIZ_PY:-$(command -v python3.12 || echo "$PY")}
if "$QPY" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 12) else 1)'; then
  (cd "$ROOT/tools/quiz-bank" && "$QPY" quizbank.py check >/dev/null)
else
  echo "AVERTISSEMENT: tools/quiz-bank/quizbank.py check ignoré (Python >= 3.12 requis, $("$QPY" -V 2>&1) trouvé) : les paquets de quiz déjà construits sont vérifiés par les tests du core" >&2
fi
$PY "$ROOT/tools/content-media/check_media.py"
$PY "$ROOT/tools/content-budget/content_budget.py" --quiet
$PY -m unittest discover -s "$ROOT/tools/tests" -p 'test_*.py' >/dev/null
$PY "$ROOT/tools/pedagogy-report/validate_report.py" "$ROOT/tools/pedagogy-report/sample-report.json" >/dev/null
for r in "$ROOT"/content/qa/*.json; do [ -f "$r" ] && $PY "$ROOT/tools/pedagogy-report/validate_report.py" "$r" >/dev/null; done || true

say "2/4 Paquets Apprendre"
rm -rf "$ROOT/android/core/build/learn-packs"
"$RUNNER" -q :core:buildLearnPacks

say "3/4 Lots et catalogue"
rm -rf "$OUT"; mkdir -p "$OUT/work"
KEYARG=""; [ -n "$KEY" ] && KEYARG="--sign-key $KEY"
# shellcheck disable=SC2086
$PY "$ROOT/tools/content-lots/build_lots.py" --out "$OUT/work/lots" --learn-packs "$ROOT/android/core/build/learn-packs" $BUMP $KEYARG

say "4/4 Release"
$PY "$ROOT/tools/content-lots/make_release.py" --lots "$OUT/work/lots" --out "$OUT/tree" ${VERSION:+--version "$VERSION"}
echo "Arbre prêt à copier : $OUT/tree (CONTENT-RELEASE.json, SHA256SUMS, lots/, graph/)"

if [ -n "$RSYNC" ]; then
  command -v rsync >/dev/null || { echo "rsync introuvable : installer rsync (l'arbre est prêt dans $OUT/tree)" >&2; exit 4; }
  say "rsync vers $RSYNC"
  if [ -z "$GO" ]; then
    rsync -a --checksum --delete --dry-run --itemize-changes "$OUT/tree/" "$RSYNC"
    echo "(simulation : rien n'a été copié. Pour copier vraiment : --go ET CASTBRIDGE_CONFIRM_PRODUCTION=yes)"
  else
    [ "${CASTBRIDGE_CONFIRM_PRODUCTION:-}" = "yes" ] || { echo "refusé : CASTBRIDGE_CONFIRM_PRODUCTION=yes manquant" >&2; exit 3; }
    grep -q '"signed": true' "$OUT/tree/CONTENT-RELEASE.json" || { echo "refusé : catalogue non signé" >&2; exit 3; }
    # lots first, catalog and release file last: a phone never sees a catalog that announces a lot not yet uploaded
    rsync -a --checksum "$OUT/tree/lots/" "$RSYNC/lots/" --exclude catalog.json
    rsync -a --checksum --delete "$OUT/tree/" "$RSYNC"
  fi
fi
