#!/usr/bin/env bash
# gradle-lock.sh : un seul build JVM (Gradle ou Maven) à la fois sur ce Mac (8 Go de RAM).
# Verrou par `mkdir` (atomique, sans flock : absent de macOS), avec délai d'attente et détection de verrou orphelin.
#
# Usage :
#   tools/agents/gradle-lock.sh [--timeout S] [--lock DIR] [--dry-run] -- <commande…>
#   tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Foo*'      # le « -- » est facultatif
#   tools/agents/gradle-lock.sh --status                                          # qui tient le verrou ?
#   tools/agents/gradle-lock.sh --release                                         # libérer un verrou orphelin (vérifie le PID)
#
# Variables : CB_LOCK_DIR (défaut /tmp/castbridge-jvm-build.lock), CB_LOCK_TIMEOUT (défaut 1800 s), CB_LOCK_POLL (défaut 5 s).
# Règle de coordination (docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md § 4) : tout agent préfixe ses commandes
# `gradle` / `./mvnw` par ce script ; deux Gradle concurrents ont déjà corrompu des résultats de tests.
set -euo pipefail

LOCK="${CB_LOCK_DIR:-/tmp/castbridge-jvm-build.lock}"
TIMEOUT="${CB_LOCK_TIMEOUT:-1800}"
POLL="${CB_LOCK_POLL:-5}"
DRY=0
MODE=run

usage() { sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    --lock) LOCK="$2"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    --status) MODE=status; shift ;;
    --release) MODE=release; shift ;;
    --) shift; break ;;
    -*) echo "option inconnue : $1" >&2; usage >&2; exit 2 ;;
    *) break ;;
  esac
done

holder() { cat "$LOCK/owner" 2>/dev/null || echo "inconnu"; }
holder_pid() { cut -d' ' -f1 "$LOCK/owner" 2>/dev/null || echo ""; }
alive() { local p="$1"; [ -n "$p" ] && kill -0 "$p" 2>/dev/null; }

case "$MODE" in
  status)
    if [ -d "$LOCK" ]; then
      p="$(holder_pid)"
      if alive "$p"; then echo "VERROUILLÉ par : $(holder)"; else echo "ORPHELIN (processus $p absent) : $(holder)"; fi
    else echo "LIBRE ($LOCK)"; fi
    exit 0 ;;
  release)
    if [ ! -d "$LOCK" ]; then echo "déjà libre"; exit 0; fi
    p="$(holder_pid)"
    if alive "$p"; then echo "refus : le processus $p tient encore le verrou ($(holder))" >&2; exit 1; fi
    rm -rf "$LOCK"; echo "verrou orphelin libéré"; exit 0 ;;
esac

[ $# -gt 0 ] || { echo "aucune commande" >&2; usage >&2; exit 2; }

if [ "$DRY" = 1 ]; then
  echo "[dry-run] verrou=$LOCK délai=${TIMEOUT}s ; commande : $*"
  if [ -d "$LOCK" ]; then echo "[dry-run] le verrou est tenu par : $(holder)"; else echo "[dry-run] le verrou est libre"; fi
  exit 0
fi

start=$(date +%s)
while ! mkdir "$LOCK" 2>/dev/null; do
  p="$(holder_pid)"
  if [ -n "$p" ] && ! alive "$p"; then
    echo "verrou orphelin de $(holder) : repris" >&2
    rm -rf "$LOCK"; continue
  fi
  now=$(date +%s)
  if [ $((now - start)) -ge "$TIMEOUT" ]; then
    echo "délai dépassé (${TIMEOUT}s) : verrou tenu par $(holder)" >&2
    exit 75
  fi
  sleep "$POLL"
done
echo "$$ $(date -u +%Y-%m-%dT%H:%M:%SZ) ${CB_AGENT_ID:-${USER:-?}} : $*" > "$LOCK/owner"
trap 'rm -rf "$LOCK"' EXIT INT TERM
"$@"
