"""Helpers for the GCE Advanced Level Geography / History / Literature generators (compact spec tuples + figure helpers)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

_POLY = POLY
def POLY(pts, **kw):
    flat = [c for q in pts for c in q] if pts and isinstance(pts[0], (tuple, list)) else list(pts)
    return _POLY(flat, **kw)

# ---- exercise specs: ("m", prompt, right, [wrongs], expl) ("t", prompt, bool, expl) ("o", prompt, model, rubric)
# ("mt", prompt, [(a,b)..], expl) ("n", prompt, ans, expl, tol) ("p", prompt, [parts])
def M(prompt, right, wrongs, expl): return ("m", prompt, right, wrongs, expl)
def TF(prompt, ans, expl): return ("t", prompt, ans, expl)
def O(prompt, model, rubric): return ("o", prompt, model, rubric)
def MT(prompt, pairs, expl): return ("mt", prompt, pairs, expl)
def N(prompt, ans, expl, tol=0, unit=None): return ("n", prompt, ans, expl, tol, unit)
def P(prompt, parts): return ("p", prompt, parts)
def pm(prompt, right, wrongs, expl, pts=1): return part_mcq(prompt, right, wrongs, expl, pts)
def pt(prompt, ans, expl, pts=1): return part_tf(prompt, ans, expl, pts)
def pn(prompt, ans, expl, tol=0, pts=1): return part_num(prompt, ans, expl, tol, None, pts)
def po(prompt, model, rubric, pts=2): return part_open(prompt, model, rubric, pts)

DIFF = {"application": 1, "approfondissement": 2, "examen": 3}

def add(L, tier, s, mock=False, points=None):
    k = s[0]
    kw = dict(tier=tier, mock=mock)
    if k == "m":
        kw["difficulty"] = DIFF[tier]; kw["points"] = points or (1 if tier == "application" else 2)
        return L.mcq(s[1], s[2], s[3], s[4], **kw)
    if k == "t":
        kw["difficulty"] = DIFF[tier]; kw["points"] = points or 1
        return L.tf(s[1], s[2], s[3], **kw)
    if k == "o":
        kw["difficulty"] = DIFF[tier]; kw["points"] = points or (3 if tier != "examen" else 4)
        return L.open(s[1], s[2], s[3], **kw)
    if k == "mt":
        kw["difficulty"] = DIFF[tier]; kw["points"] = points or 2
        return L.match(s[1], s[2], s[3], **kw)
    if k == "n":
        kw["difficulty"] = DIFF[tier]; kw["points"] = points or 2
        return L.num(s[1], s[2], s[3], tolerance=s[4], unit=s[5], **kw)
    if k == "p":
        return L.problem(s[1], s[2], tier=tier, difficulty=DIFF[tier], mock=mock)
    raise ValueError(k)


def lesson(ch, slug, title, obj, keys, ex, exs, sc, ill, notes, minutes=25, prereq=()):
    """keys: [(style,title,md)] (>=2, one 'pieges'/'attention'); ex: 2 worked examples (title, statement, steps, answer[, figure]);
    exs: 6 exercise specs (3 application, 2 approfondissement, 1 examen); sc: 5 self-check (prompt, right, [3 wrongs], expl);
    ill: (figure, caption, alt)."""
    assert len(ex) == 2 and len(exs) == 6 and len(sc) == 5 and len(keys) >= 2, slug
    assert any(k[0] in ("pieges", "attention") for k in keys), slug
    L = ch.lesson(slug, title, minutes=minutes, objectives=list(obj), notes=list(notes), prerequisites=list(prereq))
    for k in keys[:2]: L.key(*k)
    L.illustration(*ill)
    for k in keys[2:]: L.key(*k)
    for e in ex: L.example(*e)
    tiers = ["application"] * 3 + ["approfondissement"] * 2 + ["examen"]
    for t, s in zip(tiers, exs): add(L, t, s)
    for s in sc: L.sc(*s)
    return L


def mock_fill(L, specs):
    """specs: list of (spec, points) -> exercise ids (mock=True)."""
    return [add(L, "examen", s, mock=True, points=p) for s, p in specs]


# ---- figure helpers (kept simple and well spaced)
def flow(labels, w=420, color="blue", size=14, bw=300, bh=34, gap=22):
    """vertical chain of boxes joined by arrows"""
    it = []
    x = (w - bw) / 2
    y = 8
    for i, lab in enumerate(labels):
        it.append(RECT(x, y, bw, bh, stroke=color, width=2, radius=6))
        it.append(T(w / 2, y + bh / 2 + 5, lab, size))
        if i < len(labels) - 1:
            it.append(LINE(w / 2, y + bh, w / 2, y + bh + gap, width=2, arrow="end"))
        y += bh + gap
    return shapes(it, w, int(y - gap + 10))


def boxes(rows, w=440, color="blue", size=13, cols=2, bh=40, gap=12):
    """grid of labelled boxes (rows = list of strings); cols per row"""
    bw = (w - 16 - gap * (cols - 1)) / cols
    it = []
    for i, lab in enumerate(rows):
        r, c = divmod(i, cols)
        x = 8 + c * (bw + gap); y = 8 + r * (bh + gap)
        it.append(RECT(x, y, bw, bh, stroke=color, width=2, radius=6))
        it.append(T(x + bw / 2, y + bh / 2 + 5, lab, size))
    nr = (len(rows) + cols - 1) // cols
    return shapes(it, w, 16 + nr * bh + (nr - 1) * gap)


def table(headers, rows, w=440, size=13, rh=30, color="blue"):
    """simple table: columns of equal width; text must be short"""
    n = len(headers); cw = (w - 16) / n
    it = []
    for j, h in enumerate(headers):
        it.append(RECT(8 + j * cw, 8, cw, rh, fill=None, stroke=color, width=2))
        it.append(T(8 + j * cw + cw / 2, 8 + rh / 2 + 5, h, size, bold=True))
    for i, r in enumerate(rows):
        for j, c in enumerate(r):
            it.append(RECT(8 + j * cw, 8 + (i + 1) * rh, cw, rh, stroke="grey", width=1))
            it.append(T(8 + j * cw + cw / 2, 8 + (i + 1) * rh + rh / 2 + 5, c, size))
    return shapes(it, w, 16 + (len(rows) + 1) * rh)


def venn_free(): return None
