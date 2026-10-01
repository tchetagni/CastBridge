#!/bin/bash
# idempotent regeneration of the gceol-maths extension (restores the original pack.json first)
cd /home/user/CastBridge
S=/tmp/claude-0/-home-user-CastBridge/810643db-fe96-5ce6-aec6-b298ff5cb7e7/scratchpad
cp $S/gceol-maths.pack.orig.json content/learn/gceol-maths/pack.json
git ls-files --others --exclude-standard content/learn/gceol-maths/lessons | xargs -r rm -f
python3 tools/learn-authoring/anglophone/gceol-maths.py
