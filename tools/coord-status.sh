#!/usr/bin/env bash
# État de tous les agents : une ligne par branche claude/*. Usage : tools/coord-status.sh [--all]
cd "$(git rev-parse --show-toplevel)" || exit 1
git fetch -q origin 2>/dev/null
base=origin/integration/agents
printf "%-34s %-9s %-8s %-18s %s\n" BRANCHE COMMITS ÂGE STATUT "DERNIER JALON / QUESTION"
for r in $(git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/remotes/origin/claude); do
  b=${r#origin/}; n=$(git rev-list --count $base..$r 2>/dev/null)
  [ "$1" != "--all" ] && [ "${n:-0}" = 0 ] && continue
  id=${b#claude/}; rep=$(git show $r:docs/agent-reports/$id.md 2>/dev/null)
  st=$(printf '%s\n' "$rep" | sed -n '1s/^STATUT: *//p'); [ -z "$st" ] && st="(sans rapport)"
  last=$(printf '%s\n' "$rep" | grep -E '^- [0-9]{4}-' | tail -1 | cut -c1-70)
  q=$(printf '%s\n' "$rep" | grep -m1 '^QUESTION:' | cut -c1-90)
  printf "%-34s %-9s %-8s %-18s %s\n" "$b" "+$n" "$(git log -1 --format=%cr $r | sed 's/ il y a //;s/il y a //' | cut -c1-8)" "$st" "${q:-$last}"
done
