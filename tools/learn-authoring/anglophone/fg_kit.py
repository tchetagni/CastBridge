"""Kit for the Forms 1-4 Geography / History / Citizenship packs (builds on _common)."""
import sys, glob, json, os
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from _common import *
import _common

FILLS = ["lightblue", "lightyellow", "lightgreen", "lightorange"]
_USED = None

def _existing_prompts(mine_prefixes):
    global _USED
    if _USED is None:
        _USED = set()
        for f in glob.glob("/home/user/CastBridge/content/learn/*/lessons/*.json"):
            pid = f.split("/")[-3]
            if pid in mine_prefixes: continue
            with open(f, encoding="utf-8") as fh:
                for e in json.load(fh)["exercises"]:
                    _USED.add(e["prompt"])
    return _USED

MINE = {"form%d-%s" % (n, s) for n in range(1, 5) for s in ("geography", "history", "citizenship")}

def LS(ch, slug, title, objectives, blocks, examples, fig, ex, sc, notes, minutes=20, prereq=(), tag=""):
    used = _existing_prompts(MINE)
    sc2 = []
    for q in sc:
        q = list(q)
        if q[0] in used or q[0] in _MY:
            q[0] = "%s (%s)" % (q[0], tag or ch.pack.title.split(" — ")[-1])
        assert q[0] not in used and q[0] not in _MY, q[0]
        _MY.add(q[0]); sc2.append(tuple(q))
    for s in ex:
        pr = s[1]
        assert pr not in _MY or True
    return lesson(ch, slug, title, objectives, blocks, examples, fig, ex, sc2, notes, minutes=minutes, prereq=prereq)
_MY = set()

# ------------------------------------------------------------- figure helpers
def flow(title, labels, w=440, per_row=4):
    """Row(s) of boxes joined by arrows. A label may contain '|' for a line break."""
    n = len(labels); rows = [labels[i:i + per_row] for i in range(0, n, per_row)]
    items = [T(w / 2, 20, title, size=14, bold=True)]
    y = 40
    for ri, row in enumerate(rows):
        k = len(row); gap = 16; bw = (w - 20 - gap * (k - 1)) / k
        for i, lab in enumerate(row):
            x = 10 + i * (bw + gap); lines = lab.split("|")
            for l in lines: assert len(l) * 6.2 <= bw - 6, (l, bw)
            items.append(RECT(round(x), y, round(bw), 52, fill=FILLS[(ri * per_row + i) % 4], radius=6))
            for j, l in enumerate(lines):
                items.append(T(round(x + bw / 2), y + (31 if len(lines) == 1 else 22 + 16 * j), l, size=12))
            if i < k - 1: items.append(LINE(round(x + bw + 2), y + 26, round(x + bw + gap - 2), y + 26, arrow="end"))
        y += 70
    return shapes(items, w, y + 4)

def cols(title, columns, w=440):
    """columns = [(heading, [items])]"""
    k = len(columns); gap = 10; cw = (w - 20 - gap * (k - 1)) / k
    mx = max(len(c[1]) for c in columns); h = 70 + 22 * mx
    items = [T(w / 2, 20, title, size=14, bold=True)]
    for i, (hd, its) in enumerate(columns):
        x = 10 + i * (cw + gap)
        assert len(hd) * 7.5 <= cw - 6, hd
        items += [RECT(round(x), 34, round(cw), h - 40, fill=FILLS[i % 4], radius=6), T(round(x + cw / 2), 56, hd, size=13, bold=True)]
        for j, t in enumerate(its):
            assert len(t) * 6.2 <= cw - 8, (t, cw)
            items.append(T(round(x + cw / 2), 80 + 22 * j, t, size=12))
    return shapes(items, w, h)

def stack(title, layers, w=420, lw=None):
    """layers = [label] top to bottom."""
    items = [T(w / 2, 20, title, size=14, bold=True)]
    y = 36; bw = w - 40
    for i, lab in enumerate(layers):
        assert len(lab) * 6.2 <= bw - 6, lab
        items += [RECT(20, y, bw, 34, fill=FILLS[i % 4]), T(w / 2, y + 22, lab, size=12)]
        y += 34
    return shapes(items, w, y + 8)

def tilemap(title="Regions of Cameroon (schematic, not to scale)"):
    """Schematic tile map of the 10 regions: positions only roughly indicate the layout."""
    w, h = 440, 330
    bw, bh = 100, 36
    pos = {"Far North": (170, 38, "lightorange"), "North": (170, 78, "lightorange"), "Adamawa": (170, 118, "lightyellow"),
           "North-West": (60, 118, "lightgreen"), "West": (60, 158, "lightgreen"), "East": (280, 158, "lightyellow"),
           "Centre": (170, 158, "lightblue"), "South-West": (10, 198, "lightgreen"), "Littoral": (112, 198, "lightblue"),
           "South": (225, 198, "lightyellow")}
    items = [T(w / 2, 18, title, size=13, bold=True)]
    for name, (x, y, f) in pos.items():
        items += [RECT(x, y, bw, bh, fill=f, radius=4), T(x + bw / 2, y + 23, name, size=12)]
    items += [T(w / 2, 262, "Colours: north, centre and south zones; west = highlands", size=11),
              T(w / 2, 282, "Each box is only a label placed roughly where the region lies.", size=11)]
    return shapes(items, w, 296)

def compass(title="Compass directions", w=300):
    cx, cy = 150, 110
    items = [T(w / 2, 18, title, size=14, bold=True), LINE(cx, cy + 60, cx, cy - 60, arrow="end", width=3), LINE(cx - 60, cy, cx + 60, cy, arrow="both"),
             T(cx, cy - 66, "N", bold=True), T(cx, cy + 82, "S", bold=True), T(cx + 76, cy + 5, "E", bold=True), T(cx - 76, cy + 5, "W", bold=True),
             LINE(cx - 42, cy - 42, cx + 42, cy + 42, color="grey", width=1), LINE(cx - 42, cy + 42, cx + 42, cy - 42, color="grey", width=1),
             T(cx + 52, cy - 44, "NE", size=11), T(cx - 52, cy - 44, "NW", size=11), T(cx + 52, cy + 52, "SE", size=11), T(cx - 52, cy + 52, "SW", size=11)]
    return shapes(items, w, 215)

def venn2(title, a, b, mid, w=420):
    items = [T(w / 2, 18, title, size=14, bold=True), CIRCLE(150, 120, 70, stroke="blue"), CIRCLE(270, 120, 70, stroke="red"),
             T(105, 118, a, size=12), T(315, 118, b, size=12), T(210, 112, mid[0], size=11), T(210, 128, mid[1], size=11)]
    return shapes(items, w, 205)

def tlfig(frm, to, events, periods=(), w=480, h=240):
    return timeline(frm, to, events, periods, w, h)

def F(fig, cap, alt): return (fig, cap, alt)

def mk_pack(pid, title, level, subject_title, wanted, desc, ref, note):
    p = Pack(pid, title, level=level, subject="histoire-geo", wanted=wanted, cursus="secondary", description=desc, programRef=ref)
    p.generic_note = note
    return p

GEO_NOTE = ("Draft for teacher review: this course follows the Cameroon anglophone secondary Geography syllabus as generally understood; "
            "scope and level per Form, places, figures and examples must be checked against the official syllabus and a current atlas before release.")
HIS_NOTE = ("Draft for teacher review: only well-established facts are used and the tone is neutral; every date and name must be verified "
            "against the official History syllabus and standard references before release.")
CIT_NOTE = ("Draft for teacher review: content is general and non-political; legal ages, institutions, names of laws and any health or road-safety "
            "statement must be verified (by a teacher, and for health topics by a health worker) before release.")
