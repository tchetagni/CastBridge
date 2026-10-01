"""Helpers of the lycée / GCE fact tables: rows -> facts, bilingual date tables.
Every fact is `review` + verif `fact` and points at a source fiche; nothing here claims a human has checked it."""
import random

from .. import lycee
from ..core import norm, sha
from ..facts_engine import fq, pick, source


def put(course, tpl, rows, src, cat, region="WORLD"):
    """rows = (question, right, [wrong...], explanation, difficulty)."""
    for q, right, wr, expl, d in rows:
        fq(course, tpl, q, right, wr, expl, src, region, cat, d)


def put_levels(make, subject, tpl, rows, src, cat, region="WORLD"):
    """rows = (level, question, right, wrongs, explanation, difficulty); `make` is lycee.fr or lycee.en."""
    for lvl, q, right, wr, expl, d in rows:
        fq(make(lvl, subject), tpl, q, right, wr, expl, src, region, cat, d)


def year_wrongs(key, year, pool, n=8):
    """Plausible wrong years: other years of the table close to `year`, then year ± a few (never `year` itself)."""
    y = int(year)
    near = sorted({int(p) for p in pool if int(p) != y and abs(int(p) - y) <= 25}, key=lambda v: (abs(v - y), v))
    r = random.Random(sha(key))
    cands = near[:4] + [y + k for k in (-1, 1, -2, 2, -3, 3, -5, 5, -10, 10) if y + k > 0 and y + k != y]
    r.shuffle(cands)
    out = []
    for c in near[:3] + cands:
        if c != y and str(c) not in out:
            out.append(str(c))
    return out[:n]


def date_questions(make, subject, tpl, rows, src, cat, lang, wording):
    """rows = (level, text_fr, text_en, year, region).  wording = (fwd_template, rev_template) with {e} / {y}.
    The reverse question (which event happened in year y?) is built only when y is unique in the table."""
    years = [r[3] for r in rows]
    uniq = {y for y in years if years.count(y) == 1}
    for lvl, tf, te, year, region in rows:
        e = tf if lang == "fr" else te
        course = make(lvl, subject)
        fq(course, tpl + "-year", wording[0].format(e=e), str(year), year_wrongs(tpl + e, year, years), "%s : %s." % (e[0].upper() + e[1:], year), src, region, cat, 2)
        if year in uniq:
            others = [r for r in rows if r[3] != year and abs(int(r[3]) - int(year)) > 3]
            r = random.Random(sha(tpl + "rev" + e))
            r.shuffle(others)
            wr = [(o[1] if lang == "fr" else o[2]) for o in others[:4]]
            fq(course, tpl + "-event", wording[1].format(y=year), e, wr, "%s : %s." % (e[0].upper() + e[1:], year), src, region, cat, 3)


LEVEL_EN = {"2nde": "f5", "1re": "l6", "tle": "u6"}


def bilingual(rows, fr_subject, en_subject, tpl, src_fr, src_en, cat_fr, cat_en):
    """rows = (niveau fr, difficulté, région, (question, bonne, [mauvaises], explication) fr, idem en).
    Each row gives one question in the francophone course and one in the GCE course of the matching level (2nde→Form 5, 1re→Lower Sixth, Tle→Upper Sixth)."""
    for lvl, d, region, fr_row, en_row in rows:
        q, r, w, e = fr_row
        fq(lycee.fr(lvl, fr_subject), tpl, q, r, w, e, src_fr, region, cat_fr, d)
        q, r, w, e = en_row
        fq(lycee.en(LEVEL_EN[lvl], en_subject), tpl, q, r, w, e, src_en, region, cat_en, d)
