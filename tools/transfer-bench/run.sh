#!/bin/sh
# Banc de mesure du transfert multivoie (voir docs/TRANSFER.md).
#   tools/transfer-bench/run.sh --tv http://192.168.1.20:8765 --pin 123456 --size 128M
#   tools/transfer-bench/run.sh --simulate
# Sans argument : l'aide. Le code PIN peut aussi venir de la variable CASTBRIDGE_PIN (il n'apparaît alors pas dans l'historique du shell).
cd "$(dirname "$0")/../../android" || exit 1
exec gradle -q :core:transferBench -Pargs="$*"
