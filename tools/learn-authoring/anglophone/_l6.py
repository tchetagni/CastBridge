"""Helpers for the Lower/Upper Sixth packs (biology, economics): spec tuples -> lessons, plus figure helpers."""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *


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


def lesson(ch, slug, title, obj, keys, ex, app, apr, exam, sc, ill, notes=(), minutes=25, prereq=(), ref="", more=None):
    assert len(app) == 3 and len(apr) == 2 and len(sc) == 5 and len(ex) == 2, slug
    assert len(keys) >= 2
    L = ch.lesson(slug, title, minutes=minutes, objectives=obj, notes=list(notes), prerequisites=list(prereq), programRef=ref)
    L.key(*keys[0]); L.key(*keys[1])
    L.illustration(*ill)
    for k in keys[2:]: L.key(*k)
    for e in ex: L.example(*e)
    if more: L.more(*more)
    for s in app: add(L, "application", s)
    for s in apr: add(L, "approfondissement", s)
    add(L, "examen", exam)
    for s in sc: L.sc(*s)
    return L


def mockq(L, spec): return add(L, "examen", spec, mock=True)


def mock_paper(p, L, slug, title, minutes, instr, questions):
    """questions = [(section title, spec)] ; builds the mock exercises on lesson L and registers the paper."""
    secs = []
    for t, spec in questions:
        secs.append((t, [mockq(L, spec)]))
    p.mock(slug, title, minutes, instr, secs)


def fx(v, nd=2):
    s = ("%." + str(nd) + "f") % v
    if "." in s: s = s.rstrip("0").rstrip(".")
    return s.replace("-", "−")


# ---------------------------------------------------------------- figures
def tw(text, size):  # rough text width
    return 0.58 * size * len(text)


def vflow(labels, colors=None, w=420, size=13, bw=320, bh=30, gap=16, title=None, notes=None):
    """Vertical chain of boxes with arrows. notes: optional small labels at the right of each box."""
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    top = 36 if title else 10
    it = []
    if title: it.append(T(w / 2, 22, title, 14, bold=True))
    x0 = (w - bw) / 2 if not notes else 10
    for i, lab in enumerate(labels):
        assert tw(lab, size) <= bw - 8, ("box text too wide", lab)
        y = top + i * (bh + gap)
        it.append(RECT(x0, y, bw, bh, fill=colors[i % len(colors)], radius=6))
        it.append(T(x0 + bw / 2, y + bh / 2 + 5, lab, size))
        if notes and notes[i]:
            assert tw(notes[i], 11) <= w - x0 - bw - 14, ("note too wide", notes[i])
            it.append(T(x0 + bw + 8, y + bh / 2 + 4, notes[i], 11, anchor="start", color="grey"))
        if i < len(labels) - 1:
            it.append(LINE(x0 + bw / 2, y + bh, x0 + bw / 2, y + bh + gap, arrow="end", width=2))
    h = top + len(labels) * (bh + gap) - gap + 10
    return shapes(it, w, h)


def hflow(labels, colors=None, w=460, size=12, bh=44, gap=26, title=None):
    """Horizontal chain of boxes (labels may contain one '\\n' to split on two lines)."""
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    n = len(labels); bw = (w - 20 - gap * (n - 1)) / n
    top = 40 if title else 14
    it = []
    if title: it.append(T(w / 2, 22, title, 14, bold=True))
    for i, lab in enumerate(labels):
        x = 10 + i * (bw + gap)
        lines = lab.split("\n")
        for l in lines: assert tw(l, size) <= bw - 6, ("too wide", l, bw)
        it.append(RECT(x, top, bw, bh, fill=colors[i % len(colors)], radius=6))
        for j, l in enumerate(lines):
            yy = top + bh / 2 + 4 + (j - (len(lines) - 1) / 2) * (size + 3)
            it.append(T(x + bw / 2, yy, l, size))
        if i < n - 1:
            it.append(LINE(x + bw + 2, top + bh / 2, x + bw + gap - 2, top + bh / 2, arrow="end"))
    return shapes(it, w, max(top + bh + 14, int(w / 3.8) + 1))


def ring(labels, w=420, h=300, size=12, colors=None, title=None, bw=130, bh=34):
    """Boxes placed around an ellipse with arrows clockwise (cycles)."""
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    n = len(labels); cx, cy = w / 2, h / 2 + (8 if title else 0)
    rx, ry = w / 2 - bw / 2 - 10, h / 2 - bh / 2 - (30 if title else 14)
    pts = []
    it = []
    if title: it.append(T(w / 2, 20, title, 14, bold=True))
    for i in range(n):
        a = -math.pi / 2 + 2 * math.pi * i / n
        pts.append((cx + rx * math.cos(a), cy + ry * math.sin(a)))
    for i in range(n):
        x, y = pts[i]; x2, y2 = pts[(i + 1) % n]
        dx, dy = x2 - x, y2 - y; d = math.hypot(dx, dy)
        # shorten the arrow so it stays outside the boxes
        ux, uy = dx / d, dy / d
        def cut(ux, uy):
            tx = (bw / 2 + 4) / abs(ux) if abs(ux) > 1e-6 else 1e9
            ty = (bh / 2 + 4) / abs(uy) if abs(uy) > 1e-6 else 1e9
            return min(tx, ty)
        c = cut(ux, uy)
        it.append(LINE(x + ux * c, y + uy * c, x2 - ux * c, y2 - uy * c, arrow="end"))
    for i, lab in enumerate(labels):
        x, y = pts[i]
        lines = lab.split("\n")
        for l in lines: assert tw(l, size) <= bw - 6, ("too wide", l)
        it.append(RECT(x - bw / 2, y - bh / 2, bw, bh, fill=colors[i % len(colors)], radius=6))
        for j, l in enumerate(lines):
            yy = y + 4 + (j - (len(lines) - 1) / 2) * (size + 2)
            it.append(T(x, yy, l, size))
    return shapes(it, w, h)


def tabfig(headers, rows, w=420, size=12, colw=None, title=None, rh=26):
    """Simple table drawn with rects and texts."""
    n = len(headers); colw = colw or [(w - 20) / n] * n
    top = 36 if title else 10
    it = []
    if title: it.append(T(w / 2, 22, title, 14, bold=True))
    x = 10
    allrows = [headers] + rows
    for r, row in enumerate(allrows):
        x = 10
        for c, cell in enumerate(row):
            assert tw(cell, size) <= colw[c] - 6, ("cell too wide", cell, colw[c])
            it.append(RECT(x, top + r * rh, colw[c], rh, fill="lightblue" if r == 0 else None, stroke="grey", width=1))
            if cell: it.append(T(x + colw[c] / 2, top + r * rh + rh / 2 + 4, cell, size, bold=(r == 0)))
            x += colw[c]
    return shapes(it, w, max(top + len(allrows) * rh + 10, int(w / 3.8) + 1))


def write_compact(p):
    """p.write(), then rewrite the JSON files without indentation (same content, smaller files)."""
    import json as _j, os as _o
    p.write()
    base = _o.path.join(ROOT, p.id)
    for dp, _, fs in _o.walk(base):
        for f in fs:
            if f.endswith(".json"):
                path = _o.path.join(dp, f)
                d = _j.load(open(path, encoding="utf-8"))
                with open(path, "w", encoding="utf-8") as fh:
                    _j.dump(d, fh, ensure_ascii=False, separators=(",", ":")); fh.write("\n")
