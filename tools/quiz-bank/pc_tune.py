#!/usr/bin/env python3
"""Computes qb/gen/pc_caps.json: the number of questions each generated model may keep so that no model exceeds 7.5 % of its
course (the quality control allows 8 %), counting the fact questions already written. Run after adding facts or models:
    PC_TUNE=1 python3.12 tools/quiz-bank/pc_tune.py
"""
import json
import os
import sys
from collections import Counter
from pathlib import Path

os.environ["PC_TUNE"] = "1"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import core, facts_engine  # noqa: E402

SHARE = 0.075
TARGET = 5400          # questions per course (fact + computed): 4 500 needed for 300 games, +20 % margin
LOW = {"cp", "ce1", "class1", "class2"}      # courses whose number space is small: measured with a higher ceiling
quizbank.load_generators()
facts = Counter()
for q in facts_engine.run():
    for k, c in core.COURSES.items():
        if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"]):
            facts[k] += 1
qs, fails = core.run_generators(only=set(core.PC_COURSES), strict=False)
by = Counter(q["tpl"] for q in qs)
caps = {}
for course in core.PC_COURSES:
    tpls = {t: n for t, n in by.items() if t.rsplit("-", 0)[0].split("-")[0] == course and t.startswith(course + "-")}
    f = facts[course]
    raw = sum(tpls.values())
    room = max(0, TARGET - f)
    lo, hi = 1, max(tpls.values())
    if raw <= room:
        lim = hi
    else:                                     # water-filling: the largest per-model cap that keeps the course within TARGET
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if sum(min(n, mid) for n in tpls.values()) <= room:
                lo = mid
            else:
                hi = mid - 1
        lim = lo
    lim = min(lim, max(1, int(SHARE * (f + sum(min(n, lim) for n in tpls.values())))))
    for t, n in tpls.items():
        caps[t] = min(n, lim)
    kept = f + sum(caps[t] for t in tpls)
    print("%-8s facts %4d models %3d raw %5d -> kept %5d (cap per model %d, games %d)" % (course, f, len(tpls), raw, kept, lim, kept // 15))
out = Path(__file__).resolve().parent / "qb" / "gen" / "pc_caps.json"
out.write_text(json.dumps(dict(sorted(caps.items())), indent=0) + "\n", encoding="utf-8")
