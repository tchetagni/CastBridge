"""Physics / chemistry helpers for the anglophone Sixth-Form packs: spec tuples -> lessons, number formatting, figure helpers
(circuits, vectors, energy profiles, apparatus). Built on lessonlib; changes nothing about the format."""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

# ---------------------------------------------------------------- specs
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
    if k != "prob": kw.setdefault("difficulty", {"application": 1, "approfondissement": 2, "examen": 3}[kw["tier"]])
    if k == "mcq":
        kw.setdefault("points", 1 if tier == "application" else 2); return L.mcq(*a, **kw)
    if k == "num":
        kw.setdefault("points", 2 if tier == "application" else 3); return L.num(*a, **kw)
    if k == "tf": return L.tf(*a, **kw)
    if k == "open": return L.open(*a, **kw)
    if k == "match": return L.match(*a, **kw)
    if k == "prob":
        kw.pop("tier", None); kw.setdefault("difficulty", 2); return L.problem(*a, **kw)
    raise ValueError(k)


def lesson(ch, slug, title, obj, keys, ex, app, apr, exam, sc, ill, notes=(), minutes=25, prereq=(), ref=""):
    """keys: list of (style, title, md[, tex]); ex: 2 examples (title, statement, steps, answer[, figure]);
    ill = (figure, caption, alt) or list of them."""
    assert len(app) == 3 and len(apr) == 2 and len(sc) == 5 and len(ex) == 2, slug
    assert len(keys) >= 2 and any(k[0] in ("pieges", "attention") for k in keys), slug
    L = ch.lesson(slug, title, minutes=minutes, objectives=obj, notes=list(notes), prerequisites=list(prereq), programRef=ref)
    for k in keys[:2]: L.key(*k)
    ills = ill if isinstance(ill, list) else [ill]
    for i in ills: L.illustration(*i)
    for k in keys[2:]: L.key(*k)
    for e in ex: L.example(*e)
    for s in app: add(L, "application", s)
    for s in apr: add(L, "approfondissement", s)
    add(L, "examen", exam)
    for s in sc: L.sc(*(s[1] if s[0] == "mcq" else s))
    return L


def mockq(L, spec): return add(L, "examen", spec, mock=True)


# ---------------------------------------------------------------- numbers
SUP = str.maketrans("0123456789-+", "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺")
def sup(n): return str(n).translate(SUP)

def sg(x, n=3):
    """round to n significant figures (float)"""
    if x == 0: return 0.0
    return round(x, n - 1 - int(math.floor(math.log10(abs(x)))))

def fm(x, n=3):
    """string with n s.f., plain for 0.001..1e5, else standard form (unicode)"""
    if x == 0: return "0"
    v = sg(x, n)
    e = int(math.floor(math.log10(abs(v))))
    if -3 <= e < 5:
        d = max(0, n - 1 - e)
        s = ("%." + str(d) + "f") % v
        return s.replace("-", "−")
    m = v / 10 ** e
    return (("%." + str(n - 1) + "f") % m).replace("-", "−") + " × 10" + sup(e)

def near(a, b, tol=0.02):
    return abs(a - b) <= tol * abs(b) + 1e-12


# ---------------------------------------------------------------- figures
def arrow(x1, y1, x2, y2, color="red", width=3, label=None, lx=None, ly=None, size=13):
    it = [LINE(x1, y1, x2, y2, color=color, width=width, arrow="end")]
    if label: it.append(T(lx if lx is not None else x2, ly if ly is not None else y2 - 8, label, size, color=color))
    return it

def dot(x, y, r=3.5): return CIRCLE(x, y, r, fill="ink")

HL = {"res": 20, "cell": 6, "bulb": 12, "meter": 13, "switch": 16, "var": 20, "cap": 5, "gap": 10, "bat": 10, "ac": 13, "diode": 10, "lab": 0}

def _sym(kind, x, y, vert, label, lab_side):
    """symbol of a component centred (x,y); local coords (u along the wire, v across)."""
    def P_(u, v): return (x + v, y + u) if vert else (x + u, y + v)
    def ln(u1, v1, u2, v2, **k):
        a, b = P_(u1, v1), P_(u2, v2); return LINE(a[0], a[1], b[0], b[1], **k)
    it = []
    if kind in ("res", "var"):
        a = P_(-20, -8); w, h = (16, 40) if vert else (40, 16)
        it.append(RECT(a[0], a[1], w, h, width=2) if not vert else RECT(x - 8, y - 20, 16, 40, width=2))
        if kind == "var": it.append(ln(-16, 14, 16, -14, color="red", width=2, arrow="end"))
    elif kind == "cell":
        it += [ln(-4, -14, -4, 14, width=2), ln(4, -8, 4, 8, width=5)]
    elif kind == "bat":
        it += [ln(-8, -14, -8, 14, width=2), ln(-3, -8, -3, 8, width=5), ln(3, -14, 3, 14, width=2), ln(8, -8, 8, 8, width=5)]
    elif kind == "bulb":
        it += [CIRCLE(x, y, 12), ln(-8.5, -8.5, 8.5, 8.5, width=2), ln(-8.5, 8.5, 8.5, -8.5, width=2)]
    elif kind == "meter":
        it += [CIRCLE(x, y, 13, stroke="ink"), T(x, y + 5, label or "A", 14, bold=True)]
        label = None
    elif kind == "switch":
        it += [ln(-16, 0, -14, 0, width=2), ln(-14, 0, 12, -13, width=2)]
    elif kind == "cap":
        it += [ln(-4, -14, -4, 14, width=3), ln(4, -14, 4, 14, width=3)]
    elif kind == "ac":
        it += [CIRCLE(x, y, 13), PATH("M %g %g Q %g %g %g %g T %g %g" % (x - 8, y, x - 4, y - 8, x, y, x + 8, y), stroke="ink", width=2)]
    elif kind == "diode":
        a, b, c = P_(-8, -9), P_(-8, 9), P_(8, 0)
        it += [POLY([a[0], a[1], b[0], b[1], c[0], c[1]], width=2), ln(8, -9, 8, 9, width=3)]
    if label:
        off = 24 if lab_side == "up" else -24
        if vert: it.append(T(x + (30 if lab_side != "left" else -30), y + 5, label, 13, anchor="start" if lab_side != "left" else "end"))
        else: it.append(T(x, y - 22 if lab_side == "up" else y + 32, label, 13))
    return it

def branch(p1, p2, comps=(), lab_side="up"):
    """straight wire from p1 to p2 with components [(fraction 0..1 or absolute, kind, label)] (gaps left around them)."""
    (x1, y1), (x2, y2) = p1, p2
    vert = x1 == x2
    L = abs(y2 - y1) if vert else abs(x2 - x1)
    sgn = (1 if y2 > y1 else -1) if vert else (1 if x2 > x1 else -1)
    cs = sorted([(f, k, l) for f, k, l in comps], key=lambda c: c[0])
    it, pos = [], 0.0
    def pt_(s): return (x1, y1 + sgn * s) if vert else (x1 + sgn * s, y1)
    for f, k, l in cs:
        c = f * L if f <= 1 else f
        h = HL[k]
        if c - h > pos:
            a, b = pt_(pos), pt_(c - h); it.append(LINE(a[0], a[1], b[0], b[1], width=2))
        cx, cy = pt_(c)
        it += _sym(k, cx, cy, vert, l, lab_side)
        pos = c + h
    if L > pos:
        a, b = pt_(pos), pt_(L); it.append(LINE(a[0], a[1], b[0], b[1], width=2))
    return it

def rect_loop(x1, y1, x2, y2, top=(), bottom=(), left=(), right=()):
    """closed rectangular circuit; component lists as for branch (fractions along each side, direction left->right / top->bottom)."""
    it = branch((x1, y1), (x2, y1), top, "up") + branch((x1, y2), (x2, y2), bottom, "down")
    it += branch((x1, y1), (x1, y2), left, "left") + branch((x2, y1), (x2, y2), right, "right")
    return it

def energy_profile(ea_label="Ea", react="reactants", prod="products", exo=True, w=420, h=260, dh_label="ΔH"):
    """enthalpy profile (schematic, no numeric scale)"""
    y_r, y_p, y_top = (120, 190, 50) if exo else (190, 120, 50)
    it = [LINE(60, 20, 60, 230, arrow="end"), LINE(60, 230, 400, 230, arrow="end"),
          T(34, 24, "Enthalpy", 12, anchor="start"), T(400, 250, "progress of reaction", 12, anchor="end"),
          LINE(75, y_r, 150, y_r, color="blue", width=3), LINE(310, y_p, 385, y_p, color="blue", width=3),
          PATH("M 150 %d C 200 %d 200 %d 230 %d C 260 %d 270 %d 310 %d" % (y_r, y_r, y_top, y_top, y_top, y_p, y_p), stroke="red", width=3),
          T(112, (y_r + 22) if exo else (y_r - 8), react, 12), T(348, (y_p + 22) if not exo else (y_p + 22), prod, 12),
          LINE(230, y_r, 230, y_top + 2, color="green", width=2, arrow="end"), T(236, (y_r + y_top) // 2 + 5, ea_label, 13, anchor="start", color="green"),
          LINE(385, y_r, 385, y_p, color="purple", width=2, arrow="end"), T(380, (y_r + y_p) // 2 + 4, dh_label, 13, anchor="end", color="purple")]
    return shapes(it, w, h)


# ---------------------------------------------------------------- numeric answers must be typeable as shown (Scene.fmt rounds to 3 decimals)
def _fmt_shown(v):
    r = math.floor(v * 1000 + 0.5) / 1000.0
    return r

def verify_numeric(pack):
    bad = []
    for c in pack.chapters:
        for e in c.exercises:
            for x in [e] + list(e.get("parts", [])):
                if x.get("kind") == "numeric":
                    a, tol = x["answer"], x.get("tolerance", 0)
                    if abs(_fmt_shown(a) - a) > tol + 1e-9 * max(1.0, abs(a)):
                        bad.append((x["id"], a, tol))
    if bad:
        raise AssertionError("numeric answers that cannot be typed as displayed (rescale units or raise tolerance): %s" % bad)

_orig_write = Pack.write
def _write(self):
    verify_numeric(self)
    _orig_write(self)
Pack.write = _write
