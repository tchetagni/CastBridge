"""Maths figure helpers (coordinate figures drawn with shapes) shared by the anglophone maths packs."""
import math
from _kit import *

class Axes:
    def __init__(self, xmin, xmax, ymin, ymax, w=400, h=260, xticks=(), yticks=(), pad=34, unit=None):
        if unit: w = int(2 * pad + (xmax - xmin) * unit); h = int(2 * pad + (ymax - ymin) * unit)
        self.xmin, self.xmax, self.ymin, self.ymax, self.w, self.h, self.pad = xmin, xmax, ymin, ymax, w, h, pad
        self.items = []
        self.items.append(LINE(self.sx(xmin) - 6, self.sy(0 if ymin <= 0 <= ymax else ymin), self.sx(xmax) + 6,
                               self.sy(0 if ymin <= 0 <= ymax else ymin), width=1.5, arrow="end"))
        self.items.append(LINE(self.sx(0 if xmin <= 0 <= xmax else xmin), self.sy(ymin) + 6,
                               self.sx(0 if xmin <= 0 <= xmax else xmin), self.sy(ymax) - 6, width=1.5, arrow="end"))
        ax = 0 if xmin <= 0 <= xmax else xmin
        ay = 0 if ymin <= 0 <= ymax else ymin
        for v in xticks:
            self.items += [LINE(self.sx(v), self.sy(ay) - 3, self.sx(v), self.sy(ay) + 3, width=1),
                           T(self.sx(v), self.sy(ay) + 17, fx(v), 11)]
        for v in yticks:
            self.items += [LINE(self.sx(ax) - 3, self.sy(v), self.sx(ax) + 3, self.sy(v), width=1),
                           T(self.sx(ax) - 8, self.sy(v) + 4, fx(v), 11, anchor="end")]
    def sx(self, x): return self.pad + (x - self.xmin) * (self.w - 2 * self.pad) / (self.xmax - self.xmin)
    def sy(self, y): return self.h - self.pad - (y - self.ymin) * (self.h - 2 * self.pad) / (self.ymax - self.ymin)
    def curve(self, f, lo=None, hi=None, n=60, color="blue", width=2.5):
        lo = self.xmin if lo is None else lo; hi = self.xmax if hi is None else hi
        pts = []
        for i in range(n + 1):
            x = lo + (hi - lo) * i / n
            try: y = f(x)
            except Exception: continue
            if self.ymin - 1e-9 <= y <= self.ymax + 1e-9: pts += [round(self.sx(x), 1), round(self.sy(y), 1)]
        self.items.append(POLY(pts, stroke=color, width=width, closed=False)); return self
    def param(self, fx_, fy_, t0, t1, n=80, color="blue", width=2.5):
        pts = []
        for i in range(n + 1):
            t = t0 + (t1 - t0) * i / n
            x, y = fx_(t), fy_(t)
            if self.xmin <= x <= self.xmax and self.ymin <= y <= self.ymax: pts += [round(self.sx(x), 1), round(self.sy(y), 1)]
        self.items.append(POLY(pts, stroke=color, width=width, closed=False)); return self
    def line(self, x1, y1, x2, y2, color="ink", width=2, dash=False, arrow="none"):
        self.items.append(LINE(self.sx(x1), self.sy(y1), self.sx(x2), self.sy(y2), color=color, width=width, dash=dash, arrow=arrow)); return self
    def pt(self, x, y, label=None, color="red", dx=0, dy=-9, anchor="middle", r=4):
        self.items.append(CIRCLE(self.sx(x), self.sy(y), r, fill=color, stroke=color))
        if label: self.items.append(T(self.sx(x) + dx, self.sy(y) + dy, label, 12, anchor=anchor)); 
        return self
    def text(self, x, y, s, size=12, anchor="middle", color="ink", bold=False):
        self.items.append(T(self.sx(x), self.sy(y), s, size, anchor=anchor, color=color, bold=bold)); return self
    def circle(self, cx, cy, r, color="blue", fill=None):
        self.items.append(CIRCLE(self.sx(cx), self.sy(cy), r * (self.w - 2 * self.pad) / (self.xmax - self.xmin), fill=fill, stroke=color)); return self
    def poly(self, pts, fill=None, color="ink", closed=True, width=2):
        self.items.append(POLY([round(v, 1) for x, y in pts for v in (self.sx(x), self.sy(y))], fill=fill, stroke=color, width=width, closed=closed)); return self
    def fig(self): return shapes(self.items, self.w, self.h)

def arrow_fig(items, w=400, h=240): return shapes(items, w, h)

# ---- shorthand fix: \frac12 -> \frac{1}{2} (the TeX subset needs braces)
import json as _json, re as _re, types as _types, lessonlib as _ll
def _fixtol(o):
    # the content test types the answer rounded to 3 decimals: the tolerance must accept it
    if isinstance(o, dict):
        if o.get("kind") == "numeric" and isinstance(o.get("answer"), (int, float)):
            a = o["answer"]; d = abs(round(a * 1000) / 1000 - a)
            if d > o.get("tolerance", 0): o["tolerance"] = d + 1e-9
        for v in o.values(): _fixtol(v)
    elif isinstance(o, list):
        for v in o: _fixtol(v)
def _dump(obj, f, **kw):
    _fixtol(obj)
    s = _json.dumps(obj, default=lambda o: o.fig(), **kw)
    s = _re.sub(r'\\\\frac(\d)(\d)', r'\\\\frac{\1}{\2}', s)
    s = _re.sub(r'\\\\ddot ?\{?([a-z])\}?', r"\1''", s)
    s = _re.sub(r'\\\\dot ?\{?([a-z])\}?', r"\1'", s)
    s = _re.sub(r'\\\\mathbf\{([^}]*)\}', r'\\\\vec{\1}', s)
    s = _re.sub(r'\\\\mathbf ?([A-Za-z0-9])', r'\\\\vec{\1}', s)
    s = _re.sub(r'\\\\(det|arg|sinh|cosh|tanh|arcsin|arccos|arctan|cot|sec|csc|max|min)(?![a-zA-Z])', r'\\\\text{\1}\\\\,', s)
    f.write(s)
_ll.json = _types.SimpleNamespace(dump=_dump, load=_json.load, dumps=_json.dumps)
