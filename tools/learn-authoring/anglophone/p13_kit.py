"""Compact kit for the anglophone primary packs (Class 1-3). Unique module name (p13_)."""
import sys, os, json, glob
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

_REG = None
def _registry(pack_id):
    global _REG
    if _REG is None:
        _REG = set()
        for f in glob.glob(os.path.join(ROOT, "*", "lessons", "*.json")):
            if "/%s/" % pack_id in f: continue
            try:
                for e in json.load(open(f, encoding="utf-8"))["exercises"]:
                    if e.get("tier") == "autoeval": _REG.add(e["prompt"])
            except Exception: pass
    return _REG

def fit(f):
    if f and f.get("w") and f.get("h"):
        if f["w"] / f["h"] > 3.8: f["h"] = int(f["w"] / 3.8) + 1
        if f["h"] / f["w"] > 2.8: f["w"] = int(f["h"] / 2.8) + 1
    return f

def _part(s):
    k = s[0]
    if k == "m": return part_mcq(s[1], s[2], s[3], s[4])
    if k == "n": return part_num(s[1], s[2], s[3], unit=(s[4] if len(s) > 4 else None))
    if k == "t": return part_tf(s[1], s[2], s[3])
    raise ValueError(k)

def _ex(L, s, tier):
    kw = {}
    if isinstance(s[-1], dict): kw = dict(s[-1]); s = s[:-1]
    if kw.get("figure"): fit(kw["figure"])
    k = s[0]
    if k == "m": assert len({c.lower() for c in [s[2]] + list(s[3])}) == len(s[3]) + 1, ("dup choice", s[1])
    if k == "x": assert len({b for a, b in s[2]}) == len(s[2]) and len({a for a, b in s[2]}) == len(s[2]), ("dup match", s[1])
    if k == "m": return L.mcq(s[1], s[2], s[3], s[4], tier=tier, **kw)
    if k == "n": return L.num(s[1], s[2], s[3], unit=(s[4] if len(s) > 4 else None), tier=tier, **kw)
    if k == "t": return L.tf(s[1], s[2], s[3], tier=tier, **kw)
    if k == "x": return L.match(s[1], s[2], s[3], tier=tier, **kw)
    if k == "o": return L.open(s[1], s[2], s[3], tier=tier, **kw)
    if k == "p": return L.problem(s[1], [_part(q) for q in s[2]], tier="examen", **kw)
    raise ValueError(k)

def lesson(ch, slug, title, obj, keys, exs, fig, app, appr, exam, sc, notes=(), prereq=(), minutes=12, text=None):
    """keys: [(style,title,md)]; exs: [(title,stmt,steps,answer[,figure])]; fig: (figure,caption,alt);
    app: 3 specs; appr: 2 specs; exam: 1 spec; sc: 5 x (q,right,[3 wrongs],expl)."""
    pk = ch.pack
    assert len(keys) >= 2 and any(k[0] in ("pieges", "attention") for k in keys), slug
    assert len(exs) == 2 and len(app) == 3 and len(appr) == 2 and len(sc) == 5, slug
    L = ch.lesson(slug, title, minutes=minutes, objectives=obj, prerequisites=prereq, notes=list(notes), read_aloud=True)
    if text: L.text(text)
    L.key(*keys[0])
    fit(fig[0])
    L.illustration(*fig)
    for k in keys[1:]: L.key(*k)
    for e in exs:
        if len(e) > 4 and e[4]: fit(e[4])
        L.example(*e)
    for s in app: _ex(L, s, "application")
    for s in appr: _ex(L, s, "approfondissement")
    _ex(L, exam, "examen")
    reg = _registry(pk.id)
    for q, r, w, ex in sc:
        assert len({c.lower() for c in [r] + list(w)}) == 4, ("dup sc choice", q)
        if q in reg: q = "%s (%s, %s)" % (q, pk.level, title)
        n = 2
        while q in reg: q = "%s [%d]" % (q.split(" [")[0], n); n += 1
        reg.add(q)
        L.sc(q, r, w, ex)
    return L

def newpack(pid, title, level, subject, desc, ref, wanted=None):
    return Pack(pid, title, level=level, subject=subject, cursus="primary", lang="en", description=desc, programRef=ref, wanted=wanted)

REF = "MINEDUB primary school curriculum (English-speaking subsystem), %s; to be checked against the official texts"

# ----------------------------------------------------------- figures
def box(x, y, w, h, text, fill="lightyellow", size=14, bold=True, color="ink"):
    return [RECT(x, y, w, h, fill=fill, radius=6), T(x + w / 2, y + h / 2 + size * 0.35, text, size, bold=bold, color=color)]

def cards(items, cols=2, w=400, cw=None, ch=40, size=14, fills=("lightyellow", "lightgreen", "lightblue", "pink")):
    """items = list of text; grid of labelled boxes."""
    cw = cw or (w - 16) / cols - 8
    rows = (len(items) + cols - 1) // cols
    its = []
    for i, t in enumerate(items):
        x = 8 + (i % cols) * (cw + 8); y = 8 + (i // cols) * (ch + 8)
        its += box(x, y, cw, ch, t, fills[i % len(fills)], size)
    return shapes(its, w, 16 + rows * (ch + 8))

def dots(x, y, n, r=9, gap=26, per=10, color="orange", rows_gap=26):
    out = []
    for i in range(n): out.append(CIRCLE(x + (i % per) * gap, y + (i // per) * rows_gap, r, fill=color))
    return out

def groups(a, b, sign="+", color1="orange", color2="blue"):
    """a objects, sign, b objects (<= 10 each)."""
    its = dots(20, 40, a, 9, 22, 5, color1, 24) + [T(150, 52, sign, 30, bold=True)] + dots(190, 40, b, 9, 22, 5, color2, 24)
    return shapes(its, 340, 130)

def numline(lo, hi, marks=(), w=400, step=1, label_every=1):
    n = (hi - lo) // step
    x0, x1 = 24, w - 24
    its = [LINE(x0 - 10, 60, x1 + 10, 60, arrow="end", width=2)]
    for i in range(n + 1):
        v = lo + i * step; x = x0 + (x1 - x0) * i / n
        its.append(LINE(x, 54, x, 66, width=2))
        if i % label_every == 0: its.append(T(x, 86, str(v), 12))
    for m in marks:
        x = x0 + (x1 - x0) * (m - lo) / (hi - lo)
        its.append(CIRCLE(x, 40, 7, fill="red"))
    return shapes(its, w, 110)

def clockface(h, m=0):
    import math
    cx, cy, r = 100, 100, 80
    its = [CIRCLE(cx, cy, r, fill="white", width=3)]
    for i in range(1, 13):
        a = math.radians(90 - i * 30)
        its.append(T(cx + (r - 17) * math.cos(a), cy - (r - 17) * math.sin(a) + 5, str(i), 14, bold=True))
    ha = math.radians(90 - (h % 12 + m / 60) * 30); ma = math.radians(90 - m * 6)
    its.append(LINE(cx, cy, cx + 38 * math.cos(ha), cy - 38 * math.sin(ha), color="red", width=5))
    its.append(LINE(cx, cy, cx + 60 * math.cos(ma), cy - 60 * math.sin(ma), color="blue", width=3))
    its.append(CIRCLE(cx, cy, 4, fill="ink"))
    return shapes(its, 200, 200)

def table_fig(rows, w=400, cw=None, ch=34, size=14, head=True):
    """rows = list of lists of strings."""
    cols = len(rows[0]); cw = cw or (w - 16) / cols
    its = []
    for r, row in enumerate(rows):
        for c, t in enumerate(row):
            fill = "lightblue" if (head and r == 0) else "white"
            its += [RECT(8 + c * cw, 8 + r * ch, cw, ch, fill=fill), T(8 + c * cw + cw / 2, 8 + r * ch + ch / 2 + 5, t, size, bold=(head and r == 0))]
    return shapes(its, w, 16 + len(rows) * ch)

def picto(rows, w=400, key="= 2 pupils"):
    """rows = [(label, n symbols)]"""
    its = []
    for i, (lab, n) in enumerate(rows):
        y = 24 + i * 34
        its.append(T(8, y + 5, lab, 14, anchor="start", bold=True))
        for k in range(n): its.append(CIRCLE(120 + k * 24, y, 9, fill="orange"))
    its.append(T(8, 24 + len(rows) * 34 + 8, "Each circle " + key, 13, anchor="start"))
    return shapes(its, w, 24 + len(rows) * 34 + 24)
