#!/bin/sh
# Régénère les exemples puis les planches de contact PNG (Java2D, sans appareil) dans docs/img/anim-sheets/.
# Dans le cloud sans plugin Android : voir docs/HANDOFF.md (harnais :core seul).
set -e
cd "$(dirname "$0")/../.."
python3 tools/anim/build.py
(cd android && gradle :core:test --tests 'castbridge.core.LearnAnimationExamplesTest' -PanimSheets="$PWD/../docs/img/anim-sheets")
echo "Planches : docs/img/anim-sheets/"
