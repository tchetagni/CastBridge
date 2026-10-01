#!/usr/bin/env python3
"""Computes qb/gen/lycee_caps.json: how many variants each calculation model of the lycée / GCE scope may keep so that no model
exceeds the 8 % share of its course (qc.MAX_TEMPLATE_SHARE). Run it again after adding or changing a lycée generator:

    python3 tools/quiz-bank/lycee_caps.py        (writes the file, prints the table)

A course with fewer than 13 models cannot satisfy the share rule above 500 questions, so its total is kept at about 480
(the rule only applies to courses of more than 500 questions); adding models is the way to grow it."""
import json
import os
import sys
from collections import Counter, defaultdict
from pathlib import Path

os.environ["LYCEE_CAPS"] = "off"
sys.path.insert(0, str(Path(__file__).resolve().parent))
import quizbank  # noqa: E402
from qb import core, facts_engine  # noqa: E402

SHARE = 0.075                     # a little under the 8 % limit
OUT = Path(__file__).resolve().parent / "qb" / "gen" / "lycee_caps.json"


MIXED = ("-fran", "-lit")          # French + English (and English Language + Literature) share one course: the English drills must not swamp the rest
MIXED_RATIO = 1.5                  # computed questions of a mixed course: at most this many times its fact questions


def main(only=None):
    quizbank.load_generators()
    mine = {k for k, c in core.COURSES.items() if c["prefix"].startswith("ly-")}
    if only:
        mine = {k for k in mine if k.endswith(tuple(only))}
    qs, fails = core.run_generators(only=mine)
    assert not fails, fails
    natural = defaultdict(Counter)
    for q in qs:
        natural[q["tpl"] and next(k for k in mine if core.COURSES[k]["level"] == q["level"] and core.COURSES[k]["field"] == q["field"])][q["tpl"]] += 1
    facts = Counter()
    for q in facts_engine.run():
        for k in mine:
            if (core.COURSES[k]["level"], core.COURSES[k]["field"]) == (q["level"], q["field"]):
                facts[k] += 1
    caps = {}
    for course in sorted(natural):
        n = natural[course]
        F = facts[course]
        T = len(n)
        tot = lambda m: sum(min(v, m) for v in n.values()) + F
        top = max(n.values())
        a_m = next((m for m in range(top, 9, -1) if all(min(v, m) <= SHARE * tot(m) for v in n.values())), None)   # share rule respected
        b_m = next((m for m in range(top, 9, -1) if tot(m) <= 500), 10)                                              # or a bank of at most 500
        chosen = a_m if a_m is not None and tot(a_m) > tot(b_m) else b_m
        if course.endswith(MIXED):
            chosen = next((m for m in range(top, 9, -1) if sum(min(v, m) for v in n.values()) <= MIXED_RATIO * max(F, 20)), 10)
        caps[course] = {t: min(v, chosen) for t, v in n.items()}
        print("%-11s modèles %2d  faits %4d  plafond %4d  total %5d" % (course, T, F, chosen, sum(caps[course].values()) + F))
    if only and OUT.is_file():
        old = json.loads(OUT.read_text(encoding="utf-8"))
        old["caps"].update(caps)
        OUT.write_text(json.dumps({"caps": old["caps"], "salts": {k: v for k, v in old.get("salts", {}).items() if k not in caps}}, indent=1, sort_keys=True) + "\n", encoding="utf-8")
        salts(only=set(caps))
        return
    OUT.write_text(json.dumps({"caps": caps, "salts": {}}, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    salts()


def positions(course, salt):
    """Share of each answer position (A..D) among the questions of the course with the given salt, caps applied."""
    from qb import facts_engine
    c = core.COURSES[course]
    c["salt"] = salt
    qs, _ = core.run_generators(only={course})
    for cr, tpl, d, src, region, cat, diff in facts_engine.FACTS:
        if cr == course:
            q = core.build_question(course, d, tpl, "fact", "x", region=region, category=cat, difficulty=diff)
            if q:
                qs.append(q)
    n = max(1, len(qs))
    return len(qs), [sum(1 for q in qs if q["answer"] == p) / n for p in range(4)]


def salts(only=None):
    """Picks, for each course of more than 300 questions, the first salt that keeps every position within 3 points of 25 %."""
    os.environ["LYCEE_CAPS"] = "on"
    import importlib
    from qb.gen import lycee_zcaps
    lycee_zcaps.apply()
    data = json.loads(OUT.read_text(encoding="utf-8"))
    out = {}
    for course in sorted(data["caps"]):
        if only and course not in only:
            continue
        best = None
        for k in range(0, 60):
            salt = "" if k == 0 else "s%d" % k
            n, share = positions(course, salt)
            dev = max(abs(x - 0.25) for x in share)
            if best is None or dev < best[0]:
                best = (dev, salt, n)
            if n <= 300 or dev <= 0.03:
                break
        out[course] = best[1]
        print("%-11s %5d questions, salt %-4r écart max %.1f points" % (course, best[2], best[1], 100 * best[0]))
    data["salts"].update({c: s for c, s in out.items() if s})
    for c, s in out.items():
        if not s:
            data["salts"].pop(c, None)
    OUT.write_text(json.dumps(data, indent=1, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    if "--only" in sys.argv:                           # recompute only the courses whose key ends with one of the given suffixes (e.g. --only -fran -lit)
        main(only=sys.argv[sys.argv.index("--only") + 1:])
    elif "--salts-only" in sys.argv:
        quizbank.load_generators()
        os.environ["LYCEE_CAPS"] = "off"
        mine = {k for k, c in core.COURSES.items() if c["prefix"].startswith("ly-")}
        salts()
    else:
        main()
