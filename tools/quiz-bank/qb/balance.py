"""Keeps a course at a reasonable size: when generated questions exceed MAX_PER_COURSE, the generated questions of each
template are thinned out proportionally (deterministically, by id hash); facts and imported questions are always kept."""
from collections import defaultdict

from .core import COURSES, sha

MAX_PER_COURSE = 6500     # 6 500 questions = 433 games of 15; about 3 MB of JSON, 0.6 MB zipped
MIN_PER_TEMPLATE = 40


def balance(questions, max_per_course=MAX_PER_COURSE):
    out = []
    for course, c in COURSES.items():
        qs = [q for q in questions if (q["track"], q["level"], q["field"]) == (c["track"], c["level"], c["field"])]
        keep = [q for q in qs if q["verif"] != "computed"]
        comp = [q for q in qs if q["verif"] == "computed"]
        room = max(0, max_per_course - len(keep))
        if len(comp) <= room:
            out += qs
            continue
        by = defaultdict(list)
        for q in comp:
            by[q["tpl"]].append(q)
        ratio = room / len(comp)
        for tpl, group in by.items():
            n = min(len(group), max(MIN_PER_TEMPLATE, int(len(group) * ratio)))
            group.sort(key=lambda q: sha(q["id"]))
            keep += group[:n]
        out += keep
    return out
