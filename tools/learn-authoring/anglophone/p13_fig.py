"""Extra figure helpers for the Class 1-3 packs."""
from p13_kit import *

def blocks(h, t, o, w=400):
    """base-ten blocks: hundreds (squares), tens (rods), ones (small squares)."""
    its = []
    x = 10
    for i in range(h):
        its += [RECT(x, 10, 50, 50, fill="lightblue")]; x += 58
    x = max(x, 10) + 8 if h else 10
    for i in range(t):
        its += [RECT(x, 10, 12, 50, fill="lightgreen")]; x += 18
    x += 10
    for i in range(o):
        its += [RECT(x + (i % 5) * 16, 10 + (i // 5) * 18, 12, 12, fill="orange")]
    its += [T(10, 82, "%d hundreds  %d tens  %d ones" % (h, t, o), 13, anchor="start", bold=True)]
    return shapes(its, w, 96)

def frac_bar(parts, shaded, w=300, color="orange"):
    cw = (w - 20) / parts
    its = []
    for i in range(parts):
        its.append(RECT(10 + i * cw, 10, cw, 50, fill=(color if i < shaded else "white")))
    its.append(T(w / 2, 82, "%d out of %d parts" % (shaded, parts), 13, bold=True))
    return shapes(its, w, 96)

def frac_circle(parts, shaded, r=40, w=200, color="orange"):
    import math
    cx, cy = w / 2, 52
    its = [CIRCLE(cx, cy, r, fill="white", width=3)]
    for i in range(parts):
        a0 = 2 * math.pi * i / parts - math.pi / 2; a1 = 2 * math.pi * (i + 1) / parts - math.pi / 2
        if i < shaded:
            n = 12; pts = [cx, cy]
            for k in range(n + 1):
                a = a0 + (a1 - a0) * k / n; pts += [cx + r * math.cos(a), cy + r * math.sin(a)]
            its.append(POLY(pts, fill=color, width=2))
    if parts > 1:
        for i in range(parts):
            a = 2 * math.pi * i / parts - math.pi / 2
            its.append(LINE(cx, cy, cx + r * math.cos(a), cy + r * math.sin(a), width=2))
    its.append(CIRCLE(cx, cy, r, fill=None, width=3))
    return shapes(its, w, 110)

def array(rows, cols, color="orange", w=300):
    cell = min(28, (w - 20) / cols)
    its = [CIRCLE(20 + c * cell, 20 + r * cell, cell * 0.38, fill=color) for r in range(rows) for c in range(cols)]
    return shapes(its, w, int(30 + rows * cell))

def rect_dim(wd, ht, wlab, hlab, scale=30, fill="lightyellow"):
    W, H = wd * scale, ht * scale
    its = [RECT(40, 20, W, H, fill=fill), T(40 + W / 2, 14, wlab, 13, bold=True), T(34, 20 + H / 2 + 5, hlab, 13, anchor="end", bold=True)]
    return shapes(its, int(60 + W + 40), int(40 + H))

def ruler(n, shade=None, w=400):
    cw = (w - 30) / n
    its = [RECT(10, 40, n * cw, 36, fill="lightyellow")]
    for i in range(n + 1):
        its.append(LINE(10 + i * cw, 40, 10 + i * cw, 58, width=2))
        its.append(T(10 + i * cw, 94, str(i), 12))
    if shade: its.append(RECT(10, 8, shade * cw, 22, fill="orange", radius=5))
    return shapes(its, w, 110)
