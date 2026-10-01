"""Questions built from structured facts. Every fact belongs to a source fiche (SOURCES) and is `review` until a human
approves it (content/quiz/approvals.json). The fiche says where the fact should be checked; it never claims the
check was done: these facts were written by the assistant from general knowledge and need a reader.
"""
import random
from collections import Counter

from .core import STATUS_REVIEW, Draft, build_question, norm, sha

SOURCES = {}      # id -> dict(title, kind, check)
FACTS = []        # (course, tpl, Draft, source id, region, category, difficulty)


def source(id_, title, kind, check):
    """Declares a source fiche: `check` tells the reviewer what to compare the facts with."""
    SOURCES[id_] = dict(id=id_, title=title, kind=kind, check=check)


def fq(course, tpl, text, right, wrongs, expl, src, region, cat, diff, alt=()):
    assert src in SOURCES, "fiche source inconnue : " + src
    FACTS.append((course, tpl, Draft(text, right, wrongs, expl, alt=alt), src, region, cat, diff))


def pick(key, pool, right, k=8, exclude=()):
    """Deterministic choice of up to k wrong answers from pool (right answer and `exclude` left out)."""
    bad = {norm(right)} | {norm(x) for x in exclude}
    cands = sorted({x for x in pool if norm(x) not in bad})
    random.Random(sha(key)).shuffle(cands)
    return cands[:k]


def pairs(course, tpl, rows, fwd=None, rev=None, *, region, cat, src, diff=2, extra_a=(), extra_b=(), expl="{a} : {b}.", rev_expl=None, k=3):
    """Questions from (a, b[, difficulty]) rows. `fwd` asks for b given a (uses {a}), `rev` asks for a given b (uses {b}).
    The reverse question is only built when b is unique in the table (otherwise several answers would be right).
    Wrong choices come from the same column (plus extra_a / extra_b), nearest to a stable shuffle."""
    rows = [(r[0], r[1], r[2] if len(r) > 2 else diff) for r in rows]
    bs = Counter(norm(r[1]) for r in rows)
    as_ = Counter(norm(r[0]) for r in rows)
    col_a, col_b = [r[0] for r in rows] + list(extra_a), [r[1] for r in rows] + list(extra_b)
    for a, b, d in rows:
        same_a = {r[1] for r in rows if norm(r[0]) == norm(a)}          # all the right answers for this a
        if fwd and as_[norm(a)] == 1:
            fq(course, tpl + "-fwd", fwd.format(a=a, b=b), b, pick(tpl + a, col_b, b, 9, same_a), expl.format(a=a, b=b), src, region, cat, d)
        if rev and bs[norm(b)] == 1:
            same_b = {r[0] for r in rows if norm(r[1]) == norm(b)}
            fq(course, tpl + "-rev", rev.format(a=a, b=b), a, pick(tpl + b, col_a, a, 9, same_b), (rev_expl or expl).format(a=a, b=b), src, region, cat, d)


def run():
    """Builds the questions; unique by normalized text per course."""
    out, seen = [], set()
    for course, tpl, d, src, region, cat, diff in FACTS:
        s = SOURCES[src]
        q = build_question(course, d, tpl, "fact", "[%s] %s" % (src, s["title"]), region=region, category=cat, difficulty=diff)
        if q is None:
            raise AssertionError("pas assez de mauvaises réponses : " + d.text)
        k = (course, norm(q["question"]))
        if k in seen:
            continue
        seen.add(k)
        q["status"] = STATUS_REVIEW
        out.append(q)
    return out


def sources_report(questions):
    """Per fiche: how many questions rely on it."""
    n = Counter()
    for q in questions:
        if q["verif"] == "fact" and q["source"].startswith("["):
            n[q["source"][1:q["source"].index("]")]] += 1
    return [dict(SOURCES[i], questions=n.get(i, 0), status="review") for i in sorted(SOURCES)]
