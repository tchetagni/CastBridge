#!/usr/bin/env bash
# tv_navigation_bench.sh : banc de fluidité de CastBridge-TV (accueil, Quiz…) sur ÉMULATEUR SEULEMENT (docs/agent-reports/tv-perf.md, R-11).
#
# Mesure, sur l'écran actuellement affiché de castbridge.receiver :
#   1. « repos »   : images produites pendant N s SANS toucher (une interface statique doit en produire ~0/s) ;
#   2. « D-pad »   : jank et percentiles (50/90/95/99) de `dumpsys gfxinfo` pendant K appuis de flèches ;
#   3. fils chauds (`top -H`), mémoire (`dumpsys meminfo`), « Skipped N frames » du logcat pendant la mesure.
# Options : coupure du réseau (`svc wifi/data disable`, RESTAURÉ à la fin, même sur Ctrl-C) et réception simulée par la boucle locale
# (PUT /upload/ du serveur de la TV via `adb forward`, débit limité, fichier de test supprimé à la fin).
#
# Usage : tools/perf/tv_navigation_bench.sh -s emulator-5580 [--label avant] [--screen home|current] [--idle 20] [--keys 200]
#                                            [--nonet] [--receive] [--out DIR]
#   --screen home    (défaut) ouvre l'accueil de CastBridge-TV ; « current » mesure l'écran déjà affiché (ex. le Quiz, ouvert à la main)
#   --nonet          coupe Wi-Fi et données pendant la mesure (réglages d'origine restaurés)
#   --receive        envoie un fichier factice de 400 Mo à 4 Mo/s pendant la mesure (lecture du code de la TV par run-as : build debug)
# Refuse tout appareil qui n'est pas un émulateur (jamais le téléphone du propriétaire ni une vraie TV).
set -euo pipefail

SERIAL=""; LABEL="mesure"; SCREEN="home"; IDLE=20; KEYS=200; NONET=0; RECEIVE=0; OUT=""
PKG=castbridge.receiver
while [ $# -gt 0 ]; do
  case "$1" in
    -s|--serial) SERIAL="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    --screen) SCREEN="$2"; shift 2 ;;
    --idle) IDLE="$2"; shift 2 ;;
    --keys) KEYS="$2"; shift 2 ;;
    --nonet) NONET=1; shift ;;
    --receive) RECEIVE=1; shift ;;
    --out) OUT="$2"; shift 2 ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) echo "option inconnue : $1" >&2; exit 2 ;;
  esac
done
[ -n "$SERIAL" ] || { echo "numéro de série obligatoire : -s emulator-XXXX" >&2; exit 2; }
case "$SERIAL" in emulator-*) ;; *) echo "refusé : $SERIAL n'est pas un émulateur (banc réservé à l'émulateur)" >&2; exit 2 ;; esac
ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
a() { "$ADB" -s "$SERIAL" "$@"; }
a get-state >/dev/null || { echo "émulateur $SERIAL injoignable" >&2; exit 2; }
OUT="${OUT:-${TMPDIR:-/tmp}/tv-bench-$LABEL-$(date +%H%M%S)}"; mkdir -p "$OUT"

WIFI0=$(a shell settings get global wifi_on | tr -d '\r'); DATA0=$(a shell settings get global mobile_data | tr -d '\r')
FWD=""; UPLOAD_PID=""; UPNAME=""
restore() {
  [ -n "$UPLOAD_PID" ] && { pkill -P "$UPLOAD_PID" 2>/dev/null; kill "$UPLOAD_PID" 2>/dev/null; } || true
  rm -f "$OUT/upload.bin"
  if [ -n "$UPNAME" ]; then a shell run-as $PKG sh -c "find files cache -name '*$UPNAME*' -exec rm -f {} + 2>/dev/null; find /sdcard/Android/data/$PKG -name '*$UPNAME*' -exec rm -f {} + 2>/dev/null" >/dev/null 2>&1 || true; fi
  [ -n "$FWD" ] && a forward --remove "tcp:$FWD" >/dev/null 2>&1 || true
  if [ "$NONET" = 1 ]; then
    [ "$WIFI0" = 1 ] && a shell svc wifi enable || true
    [ "$DATA0" = 1 ] && a shell svc data enable || true
  fi
}
trap restore EXIT INT TERM

if [ "$NONET" = 1 ]; then a shell svc wifi disable; a shell svc data disable; sleep 5; fi
if [ "$SCREEN" = home ]; then a shell am start -n $PKG/.PlayerActivity >/dev/null; sleep 6; fi
PID=$(a shell pidof $PKG | tr -d '\r'); [ -n "$PID" ] || { echo "$PKG ne tourne pas" >&2; exit 1; }

if [ "$RECEIVE" = 1 ]; then
  PIN=$(a shell run-as $PKG cat shared_prefs/castbridge_tv.xml | sed -n 's/.*name="pin">\([0-9]*\)<.*/\1/p' | tr -d '\r')
  FWD=$(( 18000 + RANDOM % 1000 )); a forward "tcp:$FWD" tcp:8765 >/dev/null
  UPNAME="cbbench-$RANDOM"
  # 400 Mo de zéros à 4 Mo/s : ~100 s de réception, plus longue que la mesure
  dd if=/dev/zero of="$OUT/upload.bin" bs=1 count=0 seek=419430400 2>/dev/null     # fichier creux : aucun disque consommé
  ( curl -s -o /dev/null --limit-rate 4M -T "$OUT/upload.bin" "http://127.0.0.1:$FWD/upload/$UPNAME.mp4?offset=0&total=419430400" -H "X-CB-Pin: $PIN" || true ) &
  UPLOAD_PID=$!; sleep 4
fi

T0=$(a shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')
frames() { a shell dumpsys gfxinfo $PKG | sed -n 's/^Total frames rendered: \([0-9]*\).*/\1/p' | head -1 | tr -d '\r'; }

# CPU (ticks de 10 ms, utime+stime) du fil principal et du RenderThread : ce que l'app consomme sans qu'on la touche
cpu() {
  a shell "for t in /proc/$PID/task/*; do [ \"\$(cat \$t/comm)\" = RenderThread ] && echo R \$(cut -d' ' -f14,15 \$t/stat); done; echo M \$(cut -d' ' -f14,15 /proc/$PID/task/$PID/stat)" \
    | tr -d '\r' | awk '{t[$1]+=$2+$3} END {printf "%d %d", t["M"]*10, t["R"]*10}'
}

# 1. repos
C0=$(cpu)
a shell dumpsys gfxinfo $PKG reset >/dev/null; sleep "$IDLE"
IDLEF=$(frames); IDLEF=${IDLEF:-0}
C1=$(cpu)
MAINMS=$(( ${C1% *} - ${C0% *} )); RTMS=$(( ${C1#* } - ${C0#* } ))
a shell top -H -b -n 1 -p "$PID" | head -14 > "$OUT/top-idle.txt" || true

# 2. D-pad : droite ×3, bas, gauche ×3, haut… (reste sur l'écran, ne valide jamais)
a shell dumpsys gfxinfo $PKG reset >/dev/null
SEQ=""; i=0; for k in 22 22 22 20 21 21 21 19; do SEQ="$SEQ $k"; done
a shell "n=0; while [ \$n -lt $KEYS ]; do for k in $SEQ; do input keyevent \$k; n=\$((n+1)); [ \$n -ge $KEYS ] && break; done; done"
a shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo-dpad.txt"
a shell top -H -b -n 1 -p "$PID" | head -14 > "$OUT/top-dpad.txt" || true
a shell dumpsys meminfo "$PID" > "$OUT/meminfo.txt" || true
a logcat -d -T "$T0" 2>/dev/null > "$OUT/logcat.txt" || true

g() { sed -n "s/^$1: *\(.*\)/\1/p" "$OUT/gfxinfo-dpad.txt" | head -1 | tr -d '\r'; }
SKIP=$(grep -c "Skipped [0-9]* frames" "$OUT/logcat.txt" || true)
PSS=$(sed -n 's/^ *TOTAL PSS: *\([0-9]*\).*/\1/p; s/^ *TOTAL: *\([0-9]*\).*/\1/p' "$OUT/meminfo.txt" | head -1)
printf '%s | écran=%s réseau=%s réception=%s | repos: %s images en %ss (%s/s), CPU fil principal %s ms, RenderThread %s ms | D-pad %s appuis: images=%s jank=%s p50=%s p90=%s p95=%s p99=%s | Skipped-frames=%s | PSS=%s ko | %s\n' \
  "$LABEL" "$SCREEN" "$([ $NONET = 1 ] && echo coupé || echo actif)" "$([ $RECEIVE = 1 ] && echo oui || echo non)" \
  "$IDLEF" "$IDLE" "$(awk "BEGIN{printf \"%.1f\", $IDLEF/$IDLE}")" "$MAINMS" "$RTMS" "$KEYS" "$(g 'Total frames rendered')" "$(g 'Janky frames')" \
  "$(g '50th percentile')" "$(g '90th percentile')" "$(g '95th percentile')" "$(g '99th percentile')" "$SKIP" "${PSS:-?}" "$OUT" | tee -a "$OUT/summary.txt"
