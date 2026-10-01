"""Shared helpers for the secondary maths packs form1-maths .. form4-maths (prefix sm_)."""
import math
from fractions import Fraction
from _maths import *   # _kit helpers, Axes, number-line helpers, JSON tolerance fix

SRC_NOTE = "Original exercise written for CastBridge, in the style of the Cameroon secondary curriculum"
sind = lambda d: math.sin(math.radians(d)); cosd = lambda d: math.cos(math.radians(d)); tand = lambda d: math.tan(math.radians(d))

def mkpack(n, title_extra, ref, desc):
    return Pack("form%d-maths" % n, "Mathematics — Form %d" % n, level="Form %d" % n, subject="maths", cursus="secondary",
                programRef=ref, description=desc, lang="en", source_note=SRC_NOTE)

def ptable(headers, rows, colw=70, rowh=30, size=13, hfill="lightblue"):
    """simple table figure"""
    n = len(headers); w = colw * n + 20; h = rowh * (len(rows) + 1) + 20
    it = []
    for j, t in enumerate(headers):
        it += [RECT(10 + j * colw, 10, colw, rowh, fill=hfill, width=1), T(10 + j * colw + colw / 2, 10 + rowh / 2 + 5, str(t), size, bold=True)]
    for i, r in enumerate(rows):
        for j, t in enumerate(r):
            it += [RECT(10 + j * colw, 10 + (i + 1) * rowh, colw, rowh, width=1), T(10 + j * colw + colw / 2, 10 + (i + 1) * rowh + rowh / 2 + 5, str(t), size)]
    return shapes(it, w, max(h, int(w / 3.5)))

def venn2(a_only, both, b_only, outside, la="A", lb="B", w=400, h=240):
    it = [RECT(10, 10, w - 20, h - 20, width=2), T(28, 32, "ξ", 14),
          CIRCLE(150, 125, 70, stroke="blue"), CIRCLE(250, 125, 70, stroke="red"),
          T(105, 50, la, 14, bold=True), T(295, 50, lb, 14, bold=True),
          T(105, 130, str(a_only), 15), T(200, 130, str(both), 15), T(295, 130, str(b_only), 15), T(360, 215, str(outside), 15)]
    return shapes(it, w, h)

def right_tri(a, b, la, lb, lc, scale=1, ox=60, oy=190, w=400, h=240):
    """right angle at bottom-left; legs a (horizontal) and b (vertical), labels la lb lc"""
    x1, y1 = ox + a * scale, oy; x0, y0 = ox, oy; x2, y2 = ox, oy - b * scale
    it = [POLY([x0, y0, x1, y1, x2, y2], fill="lightyellow"), ANGLE(x0, y0, 0, 90, r=14, right=True),
          T((x0 + x1) / 2, y0 + 20, la, 14), T(x0 - 14, (y0 + y2) / 2 + 5, lb, 14, anchor="end"), T((x1 + x2) / 2 + 12, (y1 + y2) / 2 - 6, lc, 14, anchor="start")]
    return shapes(it, w, h)

def pie(parts, w=400, h=260, r=80, colors=("lightorange", "lightblue", "lightgreen", "lightyellow", "pink", "lightgrey")):
    """parts = [(label, angle_degrees)] with angles adding to 360."""
    assert abs(sum(a for _, a in parts) - 360) < 1e-6
    cx, cy = w / 2, h / 2; a0 = 0; it = []
    for i, (lab, a) in enumerate(parts):
        a1 = a0 + a
        x0, y0 = cx + r * math.sin(math.radians(a0)), cy - r * math.cos(math.radians(a0))
        x1, y1 = cx + r * math.sin(math.radians(a1)), cy - r * math.cos(math.radians(a1))
        it.append(PATH("M %g %g L %.1f %.1f A %g %g 0 %d 1 %.1f %.1f Z" % (cx, cy, x0, y0, r, r, 1 if a > 180 else 0, x1, y1), fill=colors[i % len(colors)]))
        am = math.radians(a0 + a / 2)
        it.append(T(round(cx + (r + 34) * math.sin(am), 1), round(cy - (r + 26) * math.cos(am) + 5, 1), lab, 13))
        a0 = a1
    return shapes(it, w, h)

def venn3(oa, ob, oc, ab, ac, bc, abc, outside, w=400, h=260):
    it = [RECT(10, 10, w - 20, h - 20, width=2), T(28, 32, "ξ", 14),
          CIRCLE(160, 110, 60, stroke="blue"), CIRCLE(240, 110, 60, stroke="red"), CIRCLE(200, 175, 60, stroke="green"),
          T(105, 50, "A", 14, bold=True), T(295, 50, "B", 14, bold=True), T(285, 232, "C", 14, bold=True),
          T(125, 98, str(oa), 14), T(275, 98, str(ob), 14), T(200, 218, str(oc), 14), T(200, 85, str(ab), 14),
          T(160, 165, str(ac), 14), T(240, 165, str(bc), 14), T(200, 135, str(abc), 14), T(365, 232, str(outside), 14)]
    return shapes(it, w, h)

def cuboid(l, w_, h_, lab_l, lab_w, lab_h, fill="lightyellow"):
    x0, y0, a, b, dx, dy = 60, 70, 180, 100, 60, -40
    it = [POLY([x0, y0 + b, x0 + a, y0 + b, x0 + a, y0, x0, y0], fill=fill), POLY([x0 + a, y0, x0 + a + dx, y0 + dy, x0 + dx, y0 + dy, x0, y0], fill=fill),
          POLY([x0 + a, y0 + b, x0 + a + dx, y0 + b + dy, x0 + a + dx, y0 + dy, x0 + a, y0], fill=fill),
          T(x0 + a / 2, y0 + b + 20, lab_l, 13), T(x0 + a + dx + 8, y0 + b / 2 + dy / 2 + 5, lab_h, 13, anchor="start"),
          T(x0 + a + dx / 2 + 14, y0 + dy / 2 - 6, lab_w, 13, anchor="start")]
    return shapes(it, 400, 220)

def matfig(items, w=420, h=130, cell=34, size=15):
    """items: list of matrices (list of rows) or strings (operators like '+', '=', '×') laid out left to right."""
    it = []; x = 15; cy = h / 2
    for m in items:
        if isinstance(m, str):
            it.append(T(x + 12, cy + 5, m, 18, bold=True)); x += 34; continue
        r, c = len(m), len(m[0]); mw, mh = c * cell, r * 28
        top = cy - mh / 2
        it += [LINE(x + 4, top, x, top), LINE(x, top, x, top + mh), LINE(x, top + mh, x + 4, top + mh),
               LINE(x + mw + 8, top, x + mw + 12, top), LINE(x + mw + 12, top, x + mw + 12, top + mh), LINE(x + mw + 12, top + mh, x + mw + 8, top + mh)]
        for i in range(r):
            for j in range(c):
                it.append(T(x + 6 + j * cell + cell / 2, top + i * 28 + 19, str(m[i][j]), size))
        x += mw + 12 + 8
    return shapes(it, max(w, int(x + 10)), h)

def sector_fig(r, theta, lab_r, lab_t, w=400, h=240, cx=140, cy=130):
    x1, y1 = cx + r, cy
    x2, y2 = cx + r * cosd(theta), cy - r * sind(theta)
    it = [PATH("M %g %g L %g %g A %g %g 0 %d 0 %.1f %.1f Z" % (cx, cy, x1, y1, r, r, 1 if theta > 180 else 0, x2, y2), fill="lightorange"),
          T(cx + r / 2, cy + 18, lab_r, 13), T(cx + 34 * cosd(theta / 2) + 8, cy - 34 * sind(theta / 2) + 4, lab_t, 13, color="red", bold=True, anchor="start")]
    return shapes(it, w, h)
