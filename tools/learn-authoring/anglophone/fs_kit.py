"""Shared helpers for the anglophone secondary science packs (form1-science ... form3-biology).
Builds on biolib (build/grid/flow/lab/S...). Adds circuit drawing and small figure helpers."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from biolib import *

HW = {"cell": 5, "batt": 9, "bulb": 13, "res": 20, "sw": 16, "swc": 16, "A": 13, "V": 13, "fuse": 16, "rheo": 22}


def _local(kind):
    """primitives of a component centred on (0,0) laid along local x."""
    if kind == "cell":
        return [("L", -4, -12, -4, 12, 3), ("L", 4, -6, 4, 6, 5)]
    if kind == "batt":   # two cells
        return [("L", -8, -12, -8, 12, 3), ("L", -2, -6, -2, 6, 5), ("L", 3, -12, 3, 12, 3), ("L", 9, -6, 9, 6, 5)]
    if kind == "bulb":
        return [("C", 0, 0, 12, "lightyellow"), ("L", -8, -8, 8, 8, 1.5), ("L", -8, 8, 8, -8, 1.5)]
    if kind == "res":
        return [("R", -20, -7, 40, 14, None)]
    if kind == "rheo":
        return [("R", -22, -7, 44, 14, None), ("L", -10, 16, 18, -16, 1.5)]
    if kind == "sw":     # open switch
        return [("C", -14, 0, 2.5, "ink"), ("C", 14, 0, 2.5, "ink"), ("L", -14, 0, 10, -14, 2)]
    if kind == "swc":    # closed switch
        return [("C", -14, 0, 2.5, "ink"), ("C", 14, 0, 2.5, "ink"), ("L", -14, 0, 14, 0, 2)]
    if kind in ("A", "V"):
        return [("C", 0, 0, 12, "white"), ("Tx", 0, 4, kind)]
    if kind == "fuse":
        return [("R", -16, -6, 32, 12, None), ("L", -16, 0, 16, 0, 1.5)]
    raise ValueError(kind)


class Draw:
    def __init__(self):
        self.sh = []

    def line(self, x1, y1, x2, y2, color="ink", width=2, arrow="none", dash=False):
        self.sh.append(LINE(x1, y1, x2, y2, color=color, width=width, arrow=arrow, dash=dash))

    def text(self, x, y, s, size=12, anchor="middle", color="ink", bold=False):
        self.sh.append(T(x, y, s, size=size, anchor=anchor, color=color, bold=bold))

    def comp(self, kind, cx, cy, orient="h"):
        for p in _local(kind):
            if p[0] == "L":
                a, b, c, d, w = p[1:]
                pts = (a, b, c, d) if orient == "h" else (b, a, d, c)
                self.sh.append(LINE(cx + pts[0], cy + pts[1], cx + pts[2], cy + pts[3], width=w))
            elif p[0] == "C":
                x, y, r, f = p[1:]
                (px, py) = (x, y) if orient == "h" else (y, x)
                self.sh.append(CIRCLE(cx + px, cy + py, r, fill=f, width=1.8 if r > 5 else 1))
            elif p[0] == "R":
                x, y, w, h, f = p[1:]
                if orient == "h": self.sh.append(RECT(cx + x, cy + y, w, h, fill="white", width=2))
                else: self.sh.append(RECT(cx + y, cy + x, h, w, fill="white", width=2))
            elif p[0] == "Tx":
                self.sh.append(T(cx, cy + 5, p[3], size=14, bold=True))

    def wire(self, x1, y1, x2, y2, comps=()):
        """Wire from (x1,y1) to (x2,y2) with components [(kind, t)] at fraction t (0..1) along it."""
        import math
        L = math.hypot(x2 - x1, y2 - y1); ux, uy = (x2 - x1) / L, (y2 - y1) / L
        orient = "h" if abs(x2 - x1) >= abs(y2 - y1) else "v"
        pos = 0.0
        for kind, t in sorted(comps, key=lambda c: c[1]):
            c = L * t; hw = HW[kind]
            if c - hw > pos: self.line(x1 + ux * pos, y1 + uy * pos, x1 + ux * (c - hw), y1 + uy * (c - hw))
            self.comp(kind, x1 + ux * c, y1 + uy * c, orient)
            pos = c + hw
        if pos < L: self.line(x1 + ux * pos, y1 + uy * pos, x2, y2)

    def dot(self, x, y): self.sh.append(CIRCLE(x, y, 3.5, fill="ink", width=1))

    def fig(self, w, h): return shapes(self.sh, w, h)


def circuit_loop(x0, y0, x1, y1, top=(), bottom=(), left=(), right=()):
    d = Draw()
    d.wire(x0, y0, x1, y0, top); d.wire(x1, y0, x1, y1, right); d.wire(x1, y1, x0, y1, bottom); d.wire(x0, y1, x0, y0, left)
    return d


def ray(x1, y1, x2, y2, color="red", width=2, arrow="end", dash=False):
    return LINE(x1, y1, x2, y2, color=color, width=width, arrow=arrow, dash=dash)


def segs_graph(xmin, xmax, ymin, ymax, segments, points=(), xlabel=None, ylabel=None, grid=1, w=420, h=260):
    """plot() with solid coloured segments [(x1,y1,x2,y2,color)]."""
    return plot(xmin, xmax, ymin, ymax, segments=[(s[0], s[1], s[2], s[3], s[4] if len(s) > 4 else "blue", False) for s in segments],
                points=points, grid=grid, xlabel=xlabel, ylabel=ylabel, w=w, h=h)


def close(a, b, tol=1e-9):
    return abs(a - b) <= tol
