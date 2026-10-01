"""Shared helpers for the anglophone English/Literature generators (spec-driven lesson builder + figure helpers)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

TIERS6 = ["application"] * 3 + ["approfondissement"] * 2 + ["examen"]
DIFF6 = [1, 1, 1, 2, 2, 3]


def add_ex(L, e, tier, diff, mock=False, points=None):
    k = e[0]
    kw = dict(tier=tier, difficulty=diff, mock=mock)
    if points is not None: kw["points"] = points
    if k == "mcq":
        return L.mcq(e[1], e[2], e[3], e[4], **kw)
    if k == "tf":
        return L.tf(e[1], e[2], e[3], **kw)
    if k == "open":
        return L.open(e[1], e[2], e[3], **kw)
    if k == "match":
        return L.match(e[1], e[2], e[3], **kw)
    if k == "problem":
        kw.pop("points", None)
        return L.problem(e[1], e[2], tier=tier, difficulty=diff, mock=mock)
    raise ValueError(k)


def build(ch, s):
    """s: slug,title,min,obj,notes,blocks,ex(6 tuples),sc(5 tuples)
    blocks: ("text", md) ("key", style, title, md) ("ex", title, statement, steps, answer[, figure]) ("fig", figure, caption, alt)
            ("audio", text) ("more", items...)
    ex: ("mcq", prompt, right, wrongs, expl) ("tf", prompt, ans, expl) ("open", prompt, model, rubric)
        ("match", prompt, pairs, expl) ("problem", prompt, parts)"""
    L = ch.lesson(s["slug"], s["title"], minutes=s.get("min", 20), objectives=s["obj"], notes=s.get("notes", []),
                  prerequisites=s.get("pre", []))
    for b in s["blocks"]:
        k = b[0]
        if k == "text": L.text(b[1])
        elif k == "key": L.key(b[1], b[2], b[3])
        elif k == "ex": L.example(b[1], b[2], b[3], b[4], figure=b[5] if len(b) > 5 else None)
        elif k == "fig": L.illustration(b[1], b[2], b[3])
        elif k == "audio": L.audio(b[1])
        elif k == "more": L.more(*b[1:])
        else: raise ValueError(k)
    assert len(s["ex"]) == 6, s["slug"]
    for e, t, d in zip(s["ex"], TIERS6, DIFF6):
        add_ex(L, e, t, d)
    assert len(s["sc"]) == 5, s["slug"]
    for q in s["sc"]:
        L.sc(*q)
    return L


# ------------------------------------------------------------------ figures
def flow(title, labels, colors=None, w=400, h=None, sub=None):
    """Vertical flow of boxes joined by arrows. labels: list of str (<= 44 chars). sub: optional list of small captions (right of box)."""
    n = len(labels)
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    bh, gap, top = 30, 16, 40
    h = h or top + n * bh + (n - 1) * gap + 14
    it = [T(w / 2, 24, title, 15, bold=True)]
    for i, lab in enumerate(labels):
        y = top + i * (bh + gap)
        it.append(RECT(40, y, w - 80, bh, fill=colors[i % len(colors)], radius=6))
        it.append(T(w / 2, y + 20, lab, 13))
        if i < n - 1:
            it.append(LINE(w / 2, y + bh, w / 2, y + bh + gap, arrow="end", width=2))
    return shapes(it, w, h)


def hflow(title, labels, colors=None, w=420, h=130):
    """Horizontal row of boxes with arrows; labels <= 11 chars each (n <= 4)."""
    n = len(labels)
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    gap = 22
    bw = (w - 20 - gap * (n - 1)) / n
    it = [T(w / 2, 26, title, 15, bold=True)]
    for i, lab in enumerate(labels):
        x = 10 + i * (bw + gap)
        it.append(RECT(x, 56, bw, 44, fill=colors[i % len(colors)], radius=6))
        parts = lab.split("|")
        if len(parts) == 1:
            it.append(T(x + bw / 2, 84, lab, 13))
        else:
            it.append(T(x + bw / 2, 76, parts[0], 13)); it.append(T(x + bw / 2, 93, parts[1], 13))
        if i < n - 1:
            it.append(LINE(x + bw, 78, x + bw + gap, 78, arrow="end"))
    return shapes(it, w, h)


def cols(title, heads, rows, w=420, colors=None, size=13):
    """Table-like figure: heads (list), rows (list of lists). Each cell short."""
    n = len(heads)
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    cw = (w - 20) / n
    rh = 28
    h = 44 + rh * (len(rows) + 1) + 14
    it = [T(w / 2, 24, title, 15, bold=True)]
    for i, hd in enumerate(heads):
        it.append(RECT(10 + i * cw, 40, cw, rh, fill=colors[i % len(colors)]))
        it.append(T(10 + i * cw + cw / 2, 59, hd, size, bold=True))
    for r, row in enumerate(rows):
        for i, c in enumerate(row):
            it.append(RECT(10 + i * cw, 40 + rh * (r + 1), cw, rh, stroke="grey", width=1))
            it.append(T(10 + i * cw + cw / 2, 59 + rh * (r + 1), c, size))
    return shapes(it, w, h)


def tree(title, top, kids, w=420, h=190, colors=None):
    """Top box with up to 4 child boxes below. kids: list of (label1, label2)."""
    colors = colors or ["lightblue", "lightgreen", "lightyellow", "lightorange"]
    n = len(kids)
    it = [T(w / 2, 22, title, 15, bold=True)]
    it.append(RECT(w / 2 - 80, 36, 160, 32, fill="lightgrey", radius=6)); it.append(T(w / 2, 57, top, 13, bold=True))
    cw = (w - 20 - 10 * (n - 1)) / n
    for i, (a, b) in enumerate(kids):
        x = 10 + i * (cw + 10)
        it.append(LINE(w / 2, 68, x + cw / 2, 100, arrow="end"))
        it.append(RECT(x, 100, cw, 70, fill=colors[i % len(colors)], radius=6))
        it.append(T(x + cw / 2, 124, a, 13, bold=True)); it.append(T(x + cw / 2, 148, b, 12))
    return shapes(it, w, h)


def spokes(title, center, items, w=420, h=210):
    """Centre box with up to 4 satellites (corners). items: list of str <= 14 chars."""
    pos = [(10, 44), (w - 130, 44), (10, h - 64), (w - 130, h - 64)]
    it = [T(w / 2, 22, title, 15, bold=True)]
    cx, cy = w / 2, h / 2 + 10
    it.append(RECT(cx - 55, cy - 18, 110, 36, fill="lightyellow", radius=8)); it.append(T(cx, cy + 5, center, 14, bold=True))
    for (x, y), lab in zip(pos, items):
        it.append(LINE(cx + (-55 if x < cx else 55), cy, x + (120 if x < cx else 0), y + 20, arrow="end", color="grey"))
        it.append(RECT(x, y, 120, 36, fill="lightblue", radius=6)); it.append(T(x + 60, y + 23, lab, 13))
    return shapes(it, w, h)
