#!/bin/bash
# Validates packs with the real LessonValidator (core-only harness, no Android plugin needed).
#   tools/learn-authoring/check.sh [pack id ...]        (no id = every pack)
S=/tmp/claude-0/-home-user-CastBridge/810643db-fe96-5ce6-aec6-b298ff5cb7e7/scratchpad
java -cp "$S/h/build/classes/kotlin/main:$(cat $S/h/cp.txt)" castbridge.core.learn.LearnTool check "${LEARN_CONTENT:-/home/user/CastBridge/content/learn}" "$@" 2>&1 | grep -v "^Picked"
