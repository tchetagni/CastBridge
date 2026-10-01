"""Compact lesson builder + figure helpers shared by the biology generators (gceol-biology extension, form4-biology)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

COL = ["lightblue", "lightgreen", "lightorange", "lightyellow", "lightgrey"]


def flow(items, w=480, h=None, vertical=False, size=12, colors=None, bh=44, gap=30):
    """Boxes joined by arrows. items = list of strings ('|' = line break) or (string, color)."""
    n = len(items); its = [(i, None) if isinstance(i, str) else i for i in items]
    sh = []
    if not vertical:
        m = 10; bw = (w - 2 * m - (n - 1) * gap) / n; h = h or max(bh + 40, int(w / 3.8))
        y = (h - bh) / 2
        for k, (t, c) in enumerate(its):
            x = m + k * (bw + gap)
            sh.append(RECT(x, y, bw, bh, fill=c or COL[k % len(COL)], stroke="ink", width=1.5, radius=8))
            ls = t.split("|")
            for j, ln in enumerate(ls):
                sh.append(T(x + bw / 2, y + bh / 2 + 4 - (len(ls) - 1) * 7 + j * 14, ln, size=size))
            if k < n - 1:
                sh.append(LINE(x + bw + 3, h / 2, x + bw + gap - 3, h / 2, arrow="end"))
        return shapes(sh, w, h)
    h = h or (n * bh + (n - 1) * gap + 20)
    bw = w - 100
    for k, (t, c) in enumerate(its):
        y = 10 + k * (bh + gap)
        sh.append(RECT(50, y, bw, bh, fill=c or COL[k % len(COL)], stroke="ink", width=1.5, radius=8))
        ls = t.split("|")
        for j, ln in enumerate(ls):
            sh.append(T(50 + bw / 2, y + bh / 2 + 4 - (len(ls) - 1) * 7 + j * 14, ln, size=size))
        if k < n - 1:
            sh.append(LINE(w / 2, y + bh + 3, w / 2, y + bh + gap - 3, arrow="end"))
    return shapes(sh, w, h)


def lab(px, py, tx, ty, text, anchor=None, size=13, color="ink"):
    """Label with leader line from the point (px,py) to the text anchor at (tx,ty)."""
    if anchor is None: anchor = "start" if tx > px else "end"
    off = 4 if anchor == "start" else -4
    return [LINE(px, py, tx, ty, width=1.2, color=color), T(tx + off, ty + 4, text, size=size, anchor=anchor)]


def axes(x0, y0, x1, y1, xl=None, yl=None, ticks_x=(), ticks_y=()):
    """Axes with origin (x0,y0) bottom-left, x1 right end, y1 top end. ticks = [(pos, label)]"""
    sh = [LINE(x0, y0, x1, y0, arrow="end"), LINE(x0, y0, x0, y1, arrow="end")]
    for p, t in ticks_x:
        sh += [LINE(p, y0, p, y0 + 5, width=1.2), T(p, y0 + 19, t, size=12)]
    for p, t in ticks_y:
        sh += [LINE(x0 - 5, p, x0, p, width=1.2), T(x0 - 8, p + 4, t, size=12, anchor="end")]
    if xl: sh.append(T((x0 + x1) / 2, y0 + 38, xl, size=12))
    if yl: sh.append(T(x0 - 6, y1 - 8, yl, size=12, anchor="start"))
    return sh


def polyline(pts, color="blue", width=2.5):
    return POLY(pts, stroke=color, width=width, closed=False)


def _ex(L, i, spec, tier):
    k = spec[0]
    if k == "mcq":
        return L.mcq(spec[1], spec[2], spec[3], spec[4], tier=tier, difficulty={"application": 1, "approfondissement": 2, "examen": 3}[tier])
    if k == "tf":
        return L.tf(spec[1], spec[2], spec[3], tier=tier, difficulty={"application": 1, "approfondissement": 2, "examen": 3}[tier])
    if k == "num":
        return L.num(spec[1], spec[2], spec[3], tolerance=spec[4] if len(spec) > 4 else 0, unit=spec[5] if len(spec) > 5 else None, tier=tier,
                     difficulty={"application": 1, "approfondissement": 2, "examen": 3}[tier])
    if k == "match":
        return L.match(spec[1], spec[2], spec[3], tier=tier, difficulty={"application": 1, "approfondissement": 2, "examen": 3}[tier])
    if k == "open":
        return L.open(spec[1], spec[2], spec[3], tier=tier, difficulty={"application": 1, "approfondissement": 2, "examen": 3}[tier],
                      points=spec[4] if len(spec) > 4 else 3)
    if k == "prob":
        parts = []
        for q in spec[2]:
            if q[0] == "mcq": parts.append(part_mcq(q[1], q[2], q[3], q[4], points=q[5] if len(q) > 5 else 1))
            elif q[0] == "num": parts.append(part_num(q[1], q[2], q[3], tolerance=q[4] if len(q) > 4 else 0, unit=q[5] if len(q) > 5 else None))
            elif q[0] == "tf": parts.append(part_tf(q[1], q[2], q[3]))
            elif q[0] == "open": parts.append(part_open(q[1], q[2], q[3], points=q[4] if len(q) > 4 else 2))
        return L.problem(spec[1], parts, tier=tier, difficulty=3)
    raise ValueError(k)


def build(ch, slug, title, minutes, objectives, blocks, ex, sc, notes, prereq=(), more=None, ref=""):
    """blocks: ("key",style,title,md) | ("text",md) | ("fig",figure,caption,alt) | ("example",title,stmt,steps,answer[,figure])
    ex = 6 specs (3 application, 2 approfondissement, 1 examen); sc = 5 tuples (prompt,right,[3 wrongs],expl)."""
    assert len(ex) == 6 and len(sc) == 5, slug
    L = ch.lesson(slug, title, minutes=minutes, objectives=objectives, notes=notes, prerequisites=prereq, programRef=ref)
    first = None
    for b in blocks:
        if b[0] == "key": L.key(b[1], b[2], b[3])
        elif b[0] == "text": L.text(b[1])
        elif b[0] == "fig": L.illustration(b[1], b[2], b[3])
        elif b[0] == "example": L.example(b[1], b[2], b[3], b[4], figure=b[5] if len(b) > 5 else None)
    tiers = ["application"] * 3 + ["approfondissement"] * 2 + ["examen"]
    ids = [_ex(L, i, s, t) for i, (s, t) in enumerate(zip(ex, tiers))]
    L.d["blocks"].append({"type": "exercise", "ref": ids[0]})
    if more: L.more(*more)
    for q in sc: L.sc(*q)
    return L, ids


def grid(items, cols=3, w=480, bh=50, gapx=14, gapy=14, size=12, colors=None):
    """Grid of rounded boxes with text ('|' = line break)."""
    n = len(items); rows = (n + cols - 1) // cols
    bw = (w - 20 - (cols - 1) * gapx) / cols; h = 20 + rows * bh + (rows - 1) * gapy
    sh = []
    for k, t in enumerate(items):
        r, c = divmod(k, cols); x = 10 + c * (bw + gapx); y = 10 + r * (bh + gapy)
        sh.append(RECT(x, y, bw, bh, fill=(colors or COL)[k % len(colors or COL)], stroke="ink", width=1.5, radius=8))
        ls = t.split("|")
        for j, ln in enumerate(ls):
            sh.append(T(x + bw / 2, y + bh / 2 + 4 - (len(ls) - 1) * 7 + j * 14, ln, size=size))
    return shapes(sh, w, h)


_POLY = POLY
def POLY(pts, **kw):
    """Accepts [(x,y),...] or a flat list; the format wants a flat list of numbers."""
    if pts and isinstance(pts[0], (tuple, list)): pts = [v for q in pts for v in q]
    return _POLY(pts, **kw)


def S(parts, w, h):
    """shapes() from a list that may contain lists (e.g. results of lab())."""
    out = []
    for q in parts:
        if isinstance(q, list): out += q
        else: out.append(q)
    return shapes(out, w, h)
