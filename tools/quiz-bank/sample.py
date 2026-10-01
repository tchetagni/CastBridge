#!/usr/bin/env python3
"""Prints a few random questions per template (or per category) to read them: sample.py <course> [n] [tpl-substring]."""
import random
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402

course = sys.argv[1]
n = int(sys.argv[2]) if len(sys.argv) > 2 else 2
only = sys.argv[3] if len(sys.argv) > 3 else ""
qs, good, res, _ = quizbank.prepare()
c = quizbank.core.COURSES[course]
by = defaultdict(list)
for q in good:
    if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"]):
        by[q.get("tpl") or q["category"]].append(q)
r = random.Random(1)
for k, v in sorted(by.items()):
    if only not in k:
        continue
    print("==", k, len(v))
    for q in r.sample(v, min(n, len(v))):
        print("  [d%d %s] %s" % (q["difficulty"], q["region"], q["question"]))
        print("     ", " | ".join(("*" if i == q["answer"] else "") + x for i, x in enumerate(q["choices"])), "::", q["explanation"][:110])
