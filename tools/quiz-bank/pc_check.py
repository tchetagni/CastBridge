#!/usr/bin/env python3
"""Fast check of the fact tables of the scope « primaire et collège » (facts only, a few seconds):
    python3.12 tools/quiz-bank/pc_check.py [course ...]      e.g. cp ce1 class3
Prints, per course, the number of fact questions, the quality-control errors/warnings and the answer-length bias."""
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import core, facts_engine, qc  # noqa: E402

quizbank.load_generators()
only = set(sys.argv[1:]) or set(core.PC_COURSES) | {"cm2", "3e"}
qs = [q for q in facts_engine.run() if (q["track"], q["level"], q["field"]) in {(core.COURSES[c]["track"], core.COURSES[c]["level"], core.COURSES[c]["field"]) for c in only}]
res = qc.check_bank(qs)
by = Counter((q["level"], q["category"]) for q in qs)
lvl = Counter(q["level"] for q in qs)
print("fact questions:", dict(lvl))
for (l, c), n in sorted(by.items(), key=lambda x: (str(x[0][0]), x[0][1])):
    print("  %-12s %-22s %d" % (l, c, n))
print("ERRORS:", len(res["errors"]))
for i, e in list(res["errors"].items())[:40]:
    q = next(x for x in qs if x["id"] == i)
    print("  ", i, e, "|", q["question"][:90])
print("WARNINGS:", len(res["warnings"]))
for i, w in list(res["warnings"].items())[:40]:
    q = next(x for x in qs if x["id"] == i)
    print("  ", i, w, "|", q["question"][:90])
longest = sum(1 for q in qs if len(q["choices"][q["answer"]]) > max(len(x) for i, x in enumerate(q["choices"]) if i != q["answer"]))
print("right answer is the longest in %.0f %% of questions (keep under ~40 %%)" % (100 * longest / max(1, len(qs))))
sys.exit(1 if res["errors"] else 0)
