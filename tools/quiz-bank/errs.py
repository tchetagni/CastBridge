#!/usr/bin/env python3
"""Developer aid: which templates produce rejected questions, with one example each."""
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402

qs, good, res, _ = quizbank.prepare()
by = defaultdict(list)
for q in qs:
    if q["id"] in res["errors"]:
        by[(q.get("tpl"), tuple(res["errors"][q["id"]]))].append(q)
for (tpl, e), v in sorted(by.items(), key=lambda kv: -len(kv[1])):
    print(len(v), tpl, list(e)[:2], "|", v[0]["question"][:100], v[0]["choices"])
warn = Counter()
for i, w in res["warnings"].items():
    for x in w:
        warn[x[:50]] += 1
print(warn.most_common(10))
