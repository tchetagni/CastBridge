"""Compact spec-based helpers on top of lessonlib (shared by the anglophone content scripts)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

def MCQ(prompt, right, wrongs, expl, **kw): return ("mcq", prompt, right, wrongs, expl, kw)
def TF(prompt, ans, expl, **kw): return ("tf", prompt, ans, expl, kw)
def NUM(prompt, ans, expl, tol=0, unit=None, **kw): return ("num", prompt, ans, expl, tol, unit, kw)
def MATCH(prompt, pairs, expl, **kw): return ("match", prompt, pairs, expl, kw)
def OPEN(prompt, model, rubric, **kw): return ("open", prompt, model, rubric, kw)
def PROB(prompt, parts, **kw): return ("prob", prompt, parts, kw)

def _part(s):
    k = s[0]
    if k == "mcq":
        return part_mcq(s[1], s[2], s[3], s[4], points=s[5].get("points", 1))
    if k == "tf":
        return part_tf(s[1], s[2], s[3], points=s[4].get("points", 1))
    if k == "num":
        return part_num(s[1], s[2], s[3], tolerance=s[4], unit=s[5], points=s[6].get("points", 1))
    if k == "open":
        return part_open(s[1], s[2], s[3], points=s[4].get("points", 2))
    raise ValueError(k)

def add_ex(L, s, tier, mock=False):
    k = s[0]; kw = dict(s[-1]); kw.setdefault("tier", tier)
    if k == "mcq": return L.mcq(s[1], s[2], s[3], s[4], mock=mock, **kw)
    if k == "tf": return L.tf(s[1], s[2], s[3], mock=mock, **kw)
    if k == "num": return L.num(s[1], s[2], s[3], tolerance=s[4], unit=s[5], mock=mock, **kw)
    if k == "match": return L.match(s[1], s[2], s[3], mock=mock, **kw)
    if k == "open": return L.open(s[1], s[2], s[3], mock=mock, **kw)
    if k == "prob":
        return L.problem(s[1], [_part(p) for p in s[2]], mock=mock, **kw)
    raise ValueError(k)

TIERS_SEQ = ["application"] * 3 + ["approfondissement"] * 2 + ["examen"]

def lesson(ch, slug, title, objectives, blocks, examples, fig, ex, sc, notes, minutes=20, prereq=()):
    """blocks = [("text", md) | ("key", style, title, md)]; fig = (figure, caption, alt) shown after block 0;
    examples = [(title, statement, steps, answer[, figure])] (2); ex = 6 specs; sc = 5 MCQ specs (q, right, wrongs, expl)."""
    L = ch.lesson(slug, title, minutes=minutes, objectives=objectives, notes=notes, prerequisites=list(prereq))
    for i, b in enumerate(blocks):
        if b[0] == "text": L.text(b[1])
        else: L.key(b[1], b[2], b[3], tex=(b[4] if len(b) > 4 else None))
        if i == 0: L.illustration(fig[0], fig[1], fig[2])
    for e in examples:
        L.example(e[0], e[1], e[2], e[3], figure=(e[4] if len(e) > 4 else None))
    assert len(ex) == 6 and len(sc) == 5, (slug, len(ex), len(sc))
    for s, t in zip(ex, TIERS_SEQ): add_ex(L, s, t)
    for q in sc: L.sc(*q)
    return L

def mock_questions(pack, slug, title, minutes, instructions, lessons_and_questions):
    """lessons_and_questions = [(section title, lesson object, PROB spec)] ; each PROB's parts must sum to its marks; total 20."""
    sections = []
    for t, L, spec in lessons_and_questions:
        sections.append((t, [add_ex(L, spec, "examen", mock=True)]))
    pack.mock(slug, title, minutes, instructions, sections)
