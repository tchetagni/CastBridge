"""Kit for Computer Studies / Computer Science / Food Science packs (spec tuples -> lessons, figure helpers)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

_POLY0 = POLY
def POLY(pts, **kw):
    flat = [c for q in pts for c in q] if pts and isinstance(pts[0], (tuple, list)) else list(pts)
    return _POLY0(flat, **kw)

def M(prompt, right, wrongs, expl, **kw): return ("mcq", (prompt, right, wrongs, expl), kw)
def N(prompt, ans, expl, tol=0, unit=None, **kw): return ("num", (prompt, ans, expl), dict(tolerance=tol, unit=unit, **kw))
def TF(prompt, ans, expl, **kw): return ("tf", (prompt, ans, expl), kw)
def O(prompt, model, rubric, **kw): return ("open", (prompt, model, rubric), kw)
def MT(prompt, pairs, expl, **kw): return ("match", (prompt, pairs, expl), kw)
def P(prompt, parts, **kw): return ("prob", (prompt, parts), kw)
def pm(prompt, right, wrongs, expl, pts=1): return part_mcq(prompt, right, wrongs, expl, pts)
def pn(prompt, ans, expl, tol=0, unit=None, pts=1): return part_num(prompt, ans, expl, tol, unit, pts)
def pt(prompt, ans, expl, pts=1): return part_tf(prompt, ans, expl, pts)
def po(prompt, model, rubric, pts=2): return part_open(prompt, model, rubric, pts)

def add(L, tier, spec, mock=False):
    k, a, kw = spec
    kw = dict(kw); kw.setdefault("tier", tier); kw["mock"] = mock
    if k != "prob":
        kw.setdefault("difficulty", {"application": 1, "approfondissement": 2, "examen": 3}[kw["tier"]])
    if k == "mcq":
        kw.setdefault("points", 1 if tier == "application" else 2); return L.mcq(*a, **kw)
    if k == "num":
        kw.setdefault("points", 2 if tier == "application" else 3); return L.num(*a, **kw)
    if k == "tf":
        kw.setdefault("points", 1); return L.tf(*a, **kw)
    if k == "open": return L.open(*a, **kw)
    if k == "match": return L.match(*a, **kw)
    if k == "prob":
        kw.pop("tier", None); kw.setdefault("difficulty", 2); return L.problem(*a, **kw)
    raise ValueError(k)

def lesson(ch, slug, title, obj, keys, ill, ex, app, apr, exam, sc, notes=(), minutes=25, pre=()):
    assert len(app) == 3 and len(apr) == 2 and len(sc) == 5 and len(ex) == 2, slug
    assert len(keys) >= 2, slug
    L = ch.lesson(slug, title, minutes=minutes, objectives=obj, notes=list(notes), prerequisites=list(pre))
    for k in keys[:2]: L.key(*k)
    L.illustration(*ill[1:])
    for k in keys[2:]: L.key(*k)
    for e in ex: L.example(*e)
    for s in app: add(L, "application", s)
    for s in apr: add(L, "approfondissement", s)
    add(L, "examen", exam)
    for s in sc: L.sc(*s)
    return L

def mock(p, slug, title, minutes, instructions, sections):
    """sections = [(title, [(Lesson, spec), ...])] ; asserts total 20."""
    secs, tot = [], 0
    for t, items in sections:
        ids = []
        for L, spec in items:
            eid = add(L, "examen", spec, mock=True); ids.append(eid)
            e = [x for x in L.ch.exercises if x["id"] == eid][0]
            tot += sum(q["points"] for q in e["parts"]) if e["kind"] == "problem" else e["points"]
        secs.append((t, ids))
    assert tot == 20, "mock total %s" % tot
    p.mock(slug, title, minutes, instructions, secs)

# ---------------------------------------------------------------- figures
COL = ["lightblue", "lightgreen", "lightyellow", "lightorange"]

def flow(title, labels, colors=None, w=400):
    n = len(labels); colors = colors or COL
    bh, gap, top = 30, 16, 40
    h = top + n * bh + (n - 1) * gap + 14
    it = [T(w / 2, 24, title, 15, bold=True)]
    for i, lab in enumerate(labels):
        y = top + i * (bh + gap)
        it += [RECT(40, y, w - 80, bh, fill=colors[i % len(colors)], radius=6), T(w / 2, y + 20, lab, 13)]
        if i < n - 1: it.append(LINE(w / 2, y + bh, w / 2, y + bh + gap, arrow="end"))
    return shapes(it, w, h)

def hflow(title, labels, colors=None, w=420, h=130):
    n = len(labels); colors = colors or COL; gap = 22
    bw = (w - 20 - gap * (n - 1)) / n
    it = [T(w / 2, 26, title, 15, bold=True)]
    for i, lab in enumerate(labels):
        x = 10 + i * (bw + gap)
        it.append(RECT(x, 56, bw, 44, fill=colors[i % len(colors)], radius=6))
        parts = lab.split("|")
        if len(parts) == 1: it.append(T(x + bw / 2, 84, lab, 13))
        else: it += [T(x + bw / 2, 76, parts[0], 13), T(x + bw / 2, 93, parts[1], 13)]
        if i < n - 1: it.append(LINE(x + bw, 78, x + bw + gap, 78, arrow="end"))
    return shapes(it, w, h)

def cols(title, heads, rows, w=420, size=13):
    n = len(heads); cw = (w - 20) / n; rh = 28
    h = 44 + rh * (len(rows) + 1) + 14
    it = [T(w / 2, 24, title, 15, bold=True)]
    for i, hd in enumerate(heads):
        it += [RECT(10 + i * cw, 40, cw, rh, fill=COL[i % 4]), T(10 + i * cw + cw / 2, 59, hd, size, bold=True)]
    for r, row in enumerate(rows):
        for i, c in enumerate(row):
            it += [RECT(10 + i * cw, 40 + rh * (r + 1), cw, rh, stroke="grey", width=1), T(10 + i * cw + cw / 2, 59 + rh * (r + 1), c, size)]
    return shapes(it, w, h)

def tree(title, top, kids, w=420, h=190):
    n = len(kids)
    it = [T(w / 2, 22, title, 15, bold=True), RECT(w / 2 - 80, 36, 160, 32, fill="lightgrey", radius=6), T(w / 2, 57, top, 13, bold=True)]
    cw = (w - 20 - 10 * (n - 1)) / n
    for i, (a, b) in enumerate(kids):
        x = 10 + i * (cw + 10)
        it += [LINE(w / 2, 68, x + cw / 2, 100, arrow="end"), RECT(x, 100, cw, 70, fill=COL[i % 4], radius=6),
               T(x + cw / 2, 124, a, 13, bold=True), T(x + cw / 2, 148, b, 12)]
    return shapes(it, w, h)

def boxrow(title, cells, w=420, y=70, colors=None, size=14, sub=None, bw=None):
    """row of equal boxes with short text; sub = captions under boxes"""
    n = len(cells); bw = bw or min(60, (w - 20) / n - 4)
    x0 = (w - n * (bw + 4)) / 2
    it = [T(w / 2, 24, title, 15, bold=True)]
    for i, c in enumerate(cells):
        x = x0 + i * (bw + 4)
        it += [RECT(x, y, bw, 36, fill=(colors[i % len(colors)] if colors else "lightblue"), radius=4), T(x + bw / 2, y + 24, c, size)]
        if sub: it.append(T(x + bw / 2, y + 58, sub[i], 11))
    return shapes(it, w, y + (80 if sub else 56))

def pyramid(title, levels, w=420):
    """levels top->bottom, widest at bottom. each label short."""
    n = len(levels); lh = 40; top = 40; h = top + n * lh + 14
    it = [T(w / 2, 24, title, 15, bold=True)]
    cx = w / 2; maxw = w - 40
    for i, lab in enumerate(levels):
        y1 = top + i * lh; y2 = y1 + lh
        wt = maxw * (i / n) * 0.9 + 30; wb = maxw * ((i + 1) / n) * 0.9 + 30
        it += [POLY([(cx - wt / 2, y1), (cx + wt / 2, y1), (cx + wb / 2, y2), (cx - wb / 2, y2)], fill=COL[i % 4], stroke="ink", width=1),
               T(cx, y1 + 26, lab, 12)]
    return shapes(it, w, h)

def stackbar(title, parts, w=420, h=150):
    """single horizontal stacked bar; parts = [(label, pct, color)] ; labels below alternate"""
    it = [T(w / 2, 24, title, 15, bold=True)]
    x = 20; tw = w - 40
    for i, (lab, pct, col) in enumerate(parts):
        ww = tw * pct / 100
        it += [RECT(x, 50, ww, 36, fill=col, stroke="ink", width=1), T(x + ww / 2, 74, "%d%%" % pct, 13)]
        it.append(T(x + ww / 2, 108 + (i % 2) * 20, lab, 12))
        x += ww
    return shapes(it, w, h)

def gate(kind, x, y, w=50, h=36):
    """logic gate outline drawn with a path; returns (items, in_pts, out_pt)"""
    cy = y + h / 2
    if kind == "AND":
        d = "M%g %g H%g A%g %g 0 0 1 %g %g H%g Z" % (x, y, x + w * .5, h / 2 * 1.0, h / 2, x + w * .5, y + h, x)
        it = [PATH(d, fill="lightyellow")]; xo = x + w * .5 + h / 2
    elif kind == "OR":
        d = "M%g %g Q%g %g %g %g Q%g %g %g %g Q%g %g %g %g Q%g %g %g %g Z" % (x, y, x + w * .55, y, x + w, cy, x + w * .55, y + h, x, y + h, x + w * .2, cy, x, y, x, y, x, y)
        it = [PATH("M%g %g Q%g %g %g %g Q%g %g %g %g Q%g %g %g %g Z" % (x, y, x + w * .6, y, x + w, cy, x + w * .6, y + h, x, y + h, x + w * .22, cy, x, y), fill="lightgreen")]; xo = x + w
    elif kind == "NOT":
        it = [POLY([(x, y), (x + w - 8, cy), (x, y + h)], fill="lightblue"), CIRCLE(x + w - 4, cy, 4, fill="white")]; xo = x + w
    else: raise ValueError(kind)
    if kind == "NOT": ins = [(x, cy)]
    else: ins = [(x, y + h * .25), (x, y + h * .75)]
    return it, ins, (xo, cy)

# ---------------------------------------------------------------- escaping of lone * and $ in prose
import re as _re
_SKIP = {"figure", "tex", "id", "kind", "tier", "lesson", "chapter", "source", "style", "type", "ref", "author"}
def _esc(s):
    s = _re.sub(r"(?<![\\*])\*(?!\*)", r"\\*", s)
    return _re.sub(r"(?<!\\)\$", r"\\$", s)
def _walk(o):
    if isinstance(o, dict):
        for k, v in list(o.items()):
            if k in _SKIP: continue
            o[k] = _esc(v) if isinstance(v, str) else (_walk(v) or v)
    elif isinstance(o, list):
        for i, v in enumerate(o):
            o[i] = _esc(v) if isinstance(v, str) else (_walk(v) or v)
def write(p, escape=True):
    if escape:
        for c in p.chapters:
            for l in c.lessons: _walk(l.d)
            for e in c.exercises: _walk(e)
    p.write()
