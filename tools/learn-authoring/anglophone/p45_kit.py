"""Shared helpers for the Class 4 / Class 5 anglophone packs (p45_*): data-driven lesson builder + figure helpers."""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

TAG = {"v": ""}


def M(tier, prompt, right, wrongs, expl, **kw): return ("mcq", (prompt, right, wrongs, expl), dict(tier=tier, **kw))
def N(tier, prompt, ans, expl, tol=0, unit=None, **kw): return ("num", (prompt, ans, expl), dict(tier=tier, tolerance=tol, unit=unit, **kw))
def TF(tier, prompt, ans, expl, **kw): return ("tf", (prompt, ans, expl), dict(tier=tier, **kw))
def O(prompt, model, rubric, tier="approfondissement", **kw): return ("open", (prompt, model, rubric), dict(tier=tier, **kw))
def MA(tier, prompt, pairs, expl, **kw): return ("match", (prompt, pairs, expl), dict(tier=tier, **kw))
def P(prompt, parts, **kw): return ("problem", (prompt, parts), dict(tier="examen", **kw))
def PM(prompt, right, wrongs, expl, points=1): return part_mcq(prompt, right, wrongs, expl, points)
def PN(prompt, ans, expl, tol=0, unit=None, points=1): return part_num(prompt, ans, expl, tol, unit, points)
def PT(prompt, ans, expl, points=1): return part_tf(prompt, ans, expl, points)
def PO(prompt, model, rubric, points=2): return part_open(prompt, model, rubric, points)


def add(L, e):
    kind, args, kw = e
    kw = dict(kw)
    if kind == "mcq": return L.mcq(*args, **kw)
    if kind == "num": return L.num(*args, **kw)
    if kind == "tf": return L.tf(*args, **kw)
    if kind == "open": return L.open(*args, **kw)
    if kind == "match": return L.match(*args, **kw)
    if kind == "problem":
        kw.pop("tier", None)
        return L.problem(*args, **kw)
    raise ValueError(kind)


def build(ch, slug, title, minutes, objectives, blocks, examples, ex, sc, notes, prereq=(), ref="", ra=True):
    L = ch.lesson(slug, title, minutes=minutes, objectives=objectives, programRef=ref, prerequisites=prereq, notes=notes, read_aloud=ra)
    keys = 0; styles = set(); exs = 0
    for b in blocks:
        k = b[0]
        if k == "text": L.text(b[1])
        elif k == "key": L.key(b[1], b[2], b[3]); keys += 1; styles.add(b[1])
        elif k == "ex": L.example(b[1], b[2], b[3], b[4], b[5] if len(b) > 5 else None); exs += 1
        elif k == "fig": L.illustration(b[1], b[2], b[3])
        elif k == "more": L.more(*b[1:])
        else: raise ValueError(k)
    blocks = list(blocks) + [("ex",) + tuple(e[1:]) if e[0] == "ex" else e for e in examples]
    for b in blocks[len(blocks) - len(examples):]:
        k = b[0]
        L.example(b[1], b[2], b[3], b[4], b[5] if len(b) > 5 else None); exs += 1
    assert keys >= 2 and (styles & {"pieges", "attention"}), slug + ": need >=2 keys incl. pieges/attention"
    assert exs == 2, slug + ": exactly 2 examples"
    tiers = [e[2]["tier"] for e in ex]
    assert tiers.count("application") == 3 and tiers.count("approfondissement") == 2 and tiers.count("examen") == 1, (slug, tiers)
    for e in ex: add(L, e)
    assert len(sc) == 5, slug
    pre = (TAG["v"] + " (" + title.split(":")[0][:42] + ") – ") if TAG["v"] else ""
    for q in sc: L.sc(pre + q[0], *q[1:])
    return L


# ------------------------------------------------------------------ figures
def grid(items, cols=4, cw=92, ch_=62, gap=8, fills=("lightblue", "lightyellow", "lightgreen"), x0=None, y0=14, size=13):
    """boxes with (title, example) text"""
    rows = (len(items) + cols - 1) // cols
    w = 400; x0 = x0 if x0 is not None else (w - cols * cw - (cols - 1) * gap) // 2
    its = []
    for i, (a, b) in enumerate(items):
        r, c = divmod(i, cols); x = x0 + c * (cw + gap); y = y0 + r * (ch_ + gap)
        its += [RECT(x, y, cw, ch_, fill=fills[i % len(fills)], radius=6), T(x + cw / 2, y + 26, a, size, bold=True), T(x + cw / 2, y + 48, b, size - 1)]
    return shapes(its, w, max(110, y0 + rows * (ch_ + gap) + 6))


def boxes(lines, fills=("lightblue", "lightyellow", "lightgreen", "lightorange"), size=13, w=400, bh=34, gap=8, x=20):
    """stacked wide boxes, one text line each"""
    its = []
    for i, t in enumerate(lines):
        y = 8 + i * (bh + gap)
        its += [RECT(x, y, w - 2 * x, bh, fill=fills[i % len(fills)], radius=6), T(w / 2, y + bh / 2 + 5, t, size, bold=False)]
    return shapes(its, w, max(104, 16 + len(lines) * (bh + gap)))


def table(rows, colw=None, x0=None, y0=10, rh=30, size=13, header=True, w=400):
    """rows = list of lists of strings -> simple table"""
    nc = len(rows[0]); colw = colw or [min(110, (w - 20) // nc)] * nc
    tot = sum(colw); x0 = x0 if x0 is not None else (w - tot) / 2
    its = []
    for r, row in enumerate(rows):
        x = x0
        for c, cell in enumerate(row):
            fill = "lightblue" if (header and r == 0) else ("paper" if r % 2 else "lightyellow")
            its += [RECT(x, y0 + r * rh, colw[c], rh, fill=fill, width=1)]
            if cell: its.append(T(x + colw[c] / 2, y0 + r * rh + rh / 2 + 5, cell, size, bold=(header and r == 0)))
            x += colw[c]
    return shapes(its, w, max(104, y0 + len(rows) * rh + 10))


def nline(lo, hi, step, marks=(), label_every=1, w=400, h=100, hl=()):
    """number line from lo to hi; marks = [(value, label)] drawn as red dots with labels above; hl = values with big label"""
    x0, x1, y = 30, 370, 58
    n = int(round((hi - lo) / step))
    sc = (x1 - x0) / (hi - lo)
    its = [LINE(x0 - 12, y, x1 + 12, y, width=3, arrow="both")]
    for i in range(n + 1):
        v = lo + i * step; x = x0 + (v - lo) * sc
        its.append(LINE(x, y - 7, x, y + 7, width=2))
        if i % label_every == 0:
            lab = ("%g" % round(v, 6))
            its.append(T(x, y + 26, lab, 12))
    for v, lab in marks:
        x = x0 + (v - lo) * sc
        its += [CIRCLE(x, y, 6, fill="red"), T(x, y - 18, lab, 13, bold=True, color="red")]
    return shapes(its, w, h)


def fracbars(items, w=400, bw=240, bh=26, gap=14, fill="orange", labelsize=14):
    """items = [(num, den, label)] one bar per item, divided in den parts, num shaded"""
    its = []
    for i, (a, b, lab) in enumerate(items):
        y = 10 + i * (bh + gap); x0 = 20
        pw = bw / b
        for k in range(b):
            its.append(RECT(x0 + k * pw, y, pw, bh, fill=(fill if k < a else "paper"), width=2))
        its.append(T(x0 + bw + 14, y + bh / 2 + 5, lab, labelsize, anchor="start", bold=True))
    return shapes(its, w, max(104, 20 + len(items) * (bh + gap)))


def clock(hh, mm, w=200, h=200, label=None):
    cx, cy, r = w / 2, h / 2 - (8 if label else 0), 70
    its = [CIRCLE(cx, cy, r, fill="paper", width=3)]
    for k in range(1, 13):
        a = math.radians(90 - k * 30)
        its.append(T(cx + (r - 14) * math.cos(a), cy - (r - 14) * math.sin(a) + 5, str(k), 13, bold=True))
    ah = math.radians(90 - ((hh % 12) + mm / 60) * 30); am = math.radians(90 - mm * 6)
    its += [LINE(cx, cy, cx + 38 * math.cos(ah), cy - 38 * math.sin(ah), width=5, color="ink"),
            LINE(cx, cy, cx + 56 * math.cos(am), cy - 56 * math.sin(am), width=3, color="red"), CIRCLE(cx, cy, 4, fill="ink")]
    if label: its.append(T(cx, cy + r + 20, label, 14, bold=True))
    return shapes(its, w, h)


def rectfig(wd, ht, lw, lh, fill="lightyellow", unit="cm", w=400, h=220, hide=False):
    """rectangle with side labels; scale to fit"""
    s = min(240 / wd, 130 / ht); rw, rh = wd * s, ht * s; x0 = (w - rw) / 2; y0 = 30
    its = [RECT(x0, y0, rw, rh, fill=fill, width=3), T(x0 + rw / 2, y0 - 8, lw, 14, bold=True), T(x0 + rw + 8, y0 + rh / 2 + 5, lh, 14, anchor="start", bold=True)]
    return shapes(its, w, int(y0 + rh + 20))


def safe_bars(data, unit=None, w=400, h=240):
    """bars with an assertion that the tallest bar stays below the top gridline (no overlap with the unit label)"""
    mx = max(d[1] for d in data); st = 1.0; k = 0; mults = [2.0, 2.5, 2.0]
    while mx / st > 6: st *= mults[k % 3]; k += 1
    assert mx < math.ceil(mx / st) * st - 1e-9, "tallest bar equals the top gridline: %s" % (data,)
    return bars(data, unit, w, h)


def tri_fig(pts, labels, fill="lightgreen", w=400, h=220):
    its = [POLY([c for p in pts for c in p], fill=fill, width=3)]
    its += labels
    return shapes(its, w, h)


def cuboid(x, y, a, b, d, fill="lightblue"):
    """oblique cuboid shapes: front face a (wide) x b (high) with top-left at (x, y+d); depth shown as offset d"""
    return [POLY([x, y + d, x + d, y, x + a + d, y, x + a, y + d], fill="lightyellow", width=2),
            POLY([x + a, y + d, x + a + d, y, x + a + d, y + b, x + a, y + b + d], fill="lightorange", width=2),
            RECT(x, y + d, a, b, fill=fill, width=2)]


def flow(labels, w=400, bw=None, bh=34, fills=("lightblue", "lightyellow", "lightgreen", "lightorange"), size=13, vertical=None):
    """chain of boxes joined by arrows. Horizontal if <= 4 labels and short; otherwise vertical."""
    n = len(labels)
    vertical = (n > 4 or max(len(l) for l in labels) > 11) if vertical is None else vertical
    its = []
    if vertical:
        bw = bw or 260; x = (w - bw) / 2; gap = 18
        for i, t in enumerate(labels):
            y = 6 + i * (bh + gap)
            its += [RECT(x, y, bw, bh, fill=fills[i % len(fills)], radius=6), T(w / 2, y + bh / 2 + 5, t, size, bold=True)]
            if i < n - 1: its.append(LINE(w / 2, y + bh, w / 2, y + bh + gap, arrow="end", width=2))
        return shapes(its, w, max(104, 12 + n * (bh + gap)))
    gap = 22; bw = bw or int((w - 20 - (n - 1) * gap) / n); x0 = (w - n * bw - (n - 1) * gap) / 2
    for i, t in enumerate(labels):
        x = x0 + i * (bw + gap)
        its += [RECT(x, 20, bw, 50, fill=fills[i % len(fills)], radius=6), T(x + bw / 2, 50, t, size, bold=True)]
        if i < n - 1: its.append(LINE(x + bw, 45, x + bw + gap, 45, arrow="end", width=2))
    return shapes(its, w, 104)
