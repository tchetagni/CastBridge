"""Compact helpers on top of lessonlib for anglophone packs (spec tuples -> exercises)."""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *
from fractions import Fraction

def M(prompt, right, wrongs, expl, **kw): return ("mcq", (prompt, right, wrongs, expl), kw)
def N(prompt, ans, expl, tol=0, unit=None, **kw): return ("num", (prompt, ans, expl), dict(tolerance=tol, unit=unit, **kw))
def TF(prompt, ans, expl, **kw): return ("tf", (prompt, ans, expl), kw)
def O(prompt, model, rubric, **kw): return ("open", (prompt, model, rubric), kw)
def MT(prompt, pairs, expl, **kw): return ("match", (prompt, pairs, expl), kw)
def P(prompt, parts, **kw): return ("prob", (prompt, parts), kw)
# problem parts
def pm(prompt, right, wrongs, expl, pts=1): return part_mcq(prompt, right, wrongs, expl, pts)
def pn(prompt, ans, expl, tol=0, unit=None, pts=1): return part_num(prompt, ans, expl, tol, unit, pts)
def pt(prompt, ans, expl, pts=1): return part_tf(prompt, ans, expl, pts)

def add(L, tier, spec, mock=False):
    k, a, kw = spec
    kw = dict(kw); kw.setdefault("tier", tier); kw["mock"] = mock
    dflt = {"application": 1, "approfondissement": 2, "examen": 3}[kw["tier"]] if k != "prob" else None
    if k != "prob": kw.setdefault("difficulty", dflt)
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

def lesson(ch, slug, title, obj, keys, ex, app, apr, exam, sc, ill=None, notes=(), minutes=25, prereq=(), ref=""):
    assert len(app) == 3 and len(apr) == 2 and len(sc) == 5 and len(ex) == 2, slug
    ch.pack.__dict__.setdefault('LS', {})
    L = ch.lesson(slug, title, minutes=minutes, objectives=obj, notes=list(notes), prerequisites=list(prereq), programRef=ref)
    ks = list(keys)
    for i, k in enumerate(ks[:2]):
        L.key(*k)
    if ill: L.illustration(*ill)
    for i, k in enumerate(ks[2:]): L.key(*k)
    for e in ex:
        e = list(e); e[2] = [(s[0] if (not isinstance(s, str) and not s[1]) else s) for s in e[2]]
        L.example(*e)
    for s in app: add(L, "application", s)
    for s in apr: add(L, "approfondissement", s)
    add(L, "examen", exam)
    for s in sc: L.sc(*s)
    ch.pack.LS[slug] = L
    return L

def mockq(L, spec): return add(L, "examen", spec, mock=True)

# ---- figure helpers
def fx(v, nd=2):
    """format number without trailing zeros"""
    s = ("%." + str(nd) + "f") % v
    if "." in s: s = s.rstrip("0").rstrip(".")
    return s.replace("-", "−")

def num_line(lo, hi, marks, step=1, w=400, h=100, filled=(), hollow=(), arrow_from=None, arrow_to=None):
    """number line: marks = tick labels; filled/hollow points (value) ; shading from arrow_from..arrow_to"""
    x0, x1, y = 30, w - 30, 50
    sx = lambda v: x0 + (v - lo) * (x1 - x0) / (hi - lo)
    it = [LINE(x0 - 10, y, x1 + 10, y, width=2, arrow="both")]
    v = lo
    while v <= hi + 1e-9:
        it += [LINE(sx(v), y - 5, sx(v), y + 5, width=1), T(sx(v), y + 24, fx(v), 13)]
        v += step
    if arrow_from is not None or arrow_to is not None:
        a = sx(arrow_from) if arrow_from is not None else x0 - 10
        b = sx(arrow_to) if arrow_to is not None else x1 + 10
        it.append(LINE(a, y, b, y, color="red", width=5))
    for v in filled: it.append(CIRCLE(sx(v), y, 6, fill="red", stroke="red"))
    for v in hollow: it.append(CIRCLE(sx(v), y, 6, fill="white", stroke="red"))
    return shapes(it, w, h)

import json as _json
_orig_dump = _json.dump
def _dump(o, f, **kw):
    _orig_dump(o, f, ensure_ascii=False, separators=(",", ":"))
_json.dump = _dump        # compact JSON keeps packs small

def frac_bar(n, d, label, w=400, h=110):
    cw = 320 / d
    it = [RECT(40 + i * cw, 25, cw, 40, fill="lightblue" if i < n else "white") for i in range(d)]
    it.append(T(200, 95, label, 15))
    return shapes(it, w, h)

def chk(f, g, pts=(-3.7, -1.3, 0.4, 1.9, 2.6, 4.2, 7.1)):
    """assert two algebraic functions agree at sample points (used to verify expansions/factorisations)"""
    for x in pts:
        a, b = f(x), g(x)
        assert abs(a - b) < 1e-7 * max(1, abs(a)), (x, a, b)
    return True

def axes_fig(w, h, ox, oy, sx, sy, xr, yr, items=(), grid=True, step=1):
    """pixel-space coordinate grid for shapes: origin (ox,oy), unit scales sx,sy; xr=(xmin,xmax), yr=(ymin,ymax)"""
    it = []
    for i in range(xr[0], xr[1] + 1, step):
        it.append(LINE(ox + i * sx, oy - yr[0] * sy, ox + i * sx, oy - yr[1] * sy, color="lightgrey", width=1))
    for j in range(yr[0], yr[1] + 1, step):
        it.append(LINE(ox + xr[0] * sx, oy - j * sy, ox + xr[1] * sx, oy - j * sy, color="lightgrey", width=1))
    it.append(LINE(ox + xr[0] * sx, oy, ox + xr[1] * sx, oy, arrow="end", width=2))
    it.append(LINE(ox, oy - yr[0] * sy, ox, oy - yr[1] * sy, arrow="end", width=2))
    return it

def mat_items(x, y, rows, cw=34, rh=26, size=16, color="ink"):
    """matrix drawn at (x,y) = top-left corner; returns items and right edge x"""
    n, m = len(rows), len(rows[0]); w, h = m * cw, n * rh
    it = [LINE(x + 6, y, x, y, width=2), LINE(x, y, x, y + h, width=2), LINE(x, y + h, x + 6, y + h, width=2),
          LINE(x + w - 6, y, x + w, y, width=2), LINE(x + w, y, x + w, y + h, width=2), LINE(x + w, y + h, x + w - 6, y + h, width=2)]
    for i, r in enumerate(rows):
        for j, v in enumerate(r): it.append(T(x + cw * (j + 0.5), y + rh * (i + 0.5) + 6, str(v).replace("-", "−"), size, color=color))
    return it, x + w
