#!/bin/sh
# Exports the content layout of the dedicated PRIVATE repository (suggested name: castbridge-content, created by the OWNER).
#
#   tools/content-split-repo/split_repo.sh <target dir> [--force]
#
# Copies content/ (learn, quiz/dist, graph, MEDIA-MANIFEST.json, LOT-VERSIONS.json), the content tools, the four content documents
# and the templates (.gitattributes with Git LFS rules, README, workflow) into <target dir>, runs the size gate in "content" mode,
# and prints the commands the owner runs to create the repository. It never runs git push, never creates a remote and never touches
# the code repository: the code repository keeps NO heavy media (check_sizes.py --mode code).
set -eu
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
TARGET="${1:-}"; FORCE="${2:-}"
[ -n "$TARGET" ] || { sed -n '2,10p' "$0"; exit 2; }
if [ -e "$TARGET" ] && [ -n "$(ls -A "$TARGET" 2>/dev/null)" ] && [ "$FORCE" != "--force" ]; then echo "refusé : $TARGET n'est pas vide (--force pour réécrire)" >&2; exit 2; fi
mkdir -p "$TARGET/content" "$TARGET/tools" "$TARGET/docs"
cp -R "$ROOT/content/." "$TARGET/content/"
for t in content-lib content-budget content-media content-lots content-map content-graph content-split-repo pedagogy-report tests core-harness; do cp -R "$ROOT/tools/$t" "$TARGET/tools/"; done
cp "$ROOT/tools/publish-content.sh" "$TARGET/tools/"
for d in CONTENT-ARCHITECTURE CONTENT-PUBLISH MEDIA-POLICY PEDAGOGY-RUBRIC; do cp "$ROOT/docs/$d.md" "$TARGET/docs/"; done
cp "$ROOT/tools/content-split-repo/templates/gitattributes" "$TARGET/.gitattributes"
cp "$ROOT/tools/content-split-repo/templates/README.md" "$TARGET/README.md"
mkdir -p "$TARGET/.github/workflows"; cp "$ROOT/tools/content-split-repo/templates/validate-content.yml" "$TARGET/.github/workflows/"
( cd "$ROOT" && git rev-parse HEAD 2>/dev/null ) > "$TARGET/CODE-REF" || echo unknown > "$TARGET/CODE-REF"
find "$TARGET" -name __pycache__ -type d -prune -exec rm -rf {} + 2>/dev/null || true
python3 "$ROOT/tools/content-split-repo/check_sizes.py" --root "$TARGET" --mode content
echo
echo "Export prêt dans $TARGET ($(du -sh "$TARGET" | cut -f1)). Étapes du propriétaire :"
echo "  1. créer le dépôt PRIVÉ castbridge-content sur GitHub (vide)"
echo "  2. cd $TARGET && git init -b main && git lfs install && git add -A && git commit -m 'Base de contenu CastBridge'"
echo "  3. git remote add origin git@github.com:<compte>/castbridge-content.git && git push -u origin main   # à la main : ce script ne pousse rien"
echo "  4. dans le dépôt de code : python3 tools/content-split-repo/check_sizes.py --mode code (aucun média lourd)"
