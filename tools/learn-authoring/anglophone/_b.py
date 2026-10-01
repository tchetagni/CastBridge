"""Small data-driven layer over lessonlib for the anglophone Class 6 packs."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

def M(tier, prompt, right, wrongs, expl, **kw): return ("mcq", (prompt, right, wrongs, expl), dict(tier=tier, **kw))
def N(tier, prompt, ans, expl, tol=0, unit=None, **kw): return ("num", (prompt, ans, expl), dict(tier=tier, tolerance=tol, unit=unit, **kw))
def TF(tier, prompt, ans, expl, **kw): return ("tf", (prompt, ans, expl), dict(tier=tier, **kw))
def O(prompt, model, rubric, tier="approfondissement", **kw): return ("open", (prompt, model, rubric), dict(tier=tier, **kw))
def MA(tier, prompt, pairs, expl, **kw): return ("match", (prompt, pairs, expl), dict(tier=tier, **kw))
def P(prompt, parts, **kw): return ("problem", (prompt, parts), dict(tier="examen", **kw))
def PM(prompt, right, wrongs, expl, points=1): return part_mcq(prompt, right, wrongs, expl, points)
def PN(prompt, ans, expl, tol=0, unit=None, points=1): return part_num(prompt, ans, expl, tol, unit, points)

def add(L, e, mock=False):
    kind, args, kw = e
    kw = dict(kw)
    if mock: kw["mock"] = True
    if kind == "mcq": return L.mcq(*args, **kw)
    if kind == "num": return L.num(*args, **kw)
    if kind == "tf": return L.tf(*args, **kw)
    if kind == "open": return L.open(*args, **kw)
    if kind == "match": return L.match(*args, **kw)
    if kind == "problem":
        kw.pop("tier", None)
        return L.problem(*args, **kw)
    raise ValueError(kind)

def build(ch, slug, title, minutes, objectives, blocks, ex, sc, notes, prereq=(), ref="", ra=True):
    L = ch.lesson(slug, title, minutes=minutes, objectives=objectives, programRef=ref, prerequisites=prereq, notes=notes, read_aloud=ra)
    keys = 0; styles = set(); exs = 0
    for b in blocks:
        k = b[0]
        if k == "text": L.text(b[1])
        elif k == "key": L.key(b[1], b[2], b[3]); keys += 1; styles.add(b[1])
        elif k == "ex": L.example(b[1], b[2], b[3], b[4], b[5] if len(b) > 5 else None); exs += 1
        elif k == "fig": L.illustration(b[1], b[2], b[3])
        elif k == "more": L.more(*b[1:])
        elif k == "audio": L.audio(b[1])
        else: raise ValueError(k)
    assert keys >= 2 and (styles & {"pieges", "attention"}), slug + ": need >=2 keys incl. pieges/attention"
    assert exs == 2, slug + ": exactly 2 examples"
    tiers = [e[2]["tier"] for e in ex]
    assert tiers.count("application") == 3 and tiers.count("approfondissement") == 2 and tiers.count("examen") == 1, (slug, tiers)
    for e in ex: add(L, e)
    assert len(sc) == 5, slug
    for q in sc: L.sc(*q)
    return L

def reset_extension(pack_id, n_orig, orig_mocks):
    """Make the generator idempotent: drop chapters (order > n_orig), mocks and lesson files added by a previous run."""
    import json, os, re
    base = os.path.join(ROOT, pack_id)
    pj = os.path.join(base, "pack.json")
    d = json.load(open(pj, encoding="utf-8"))
    d["chapters"] = [c for c in d["chapters"] if c["order"] <= n_orig]
    d["mockExams"] = [m for m in d.get("mockExams", []) if m["id"] in orig_mocks]
    json.dump(d, open(pj, "w", encoding="utf-8"), ensure_ascii=False, indent=2); open(pj, "a").write("\n")
    ld = os.path.join(base, "lessons")
    for fn in os.listdir(ld):
        m = re.match(r"(\d+)-", fn)
        if m and int(m.group(1)) > n_orig: os.remove(os.path.join(ld, fn))

def grid(items, cols=4, cw=92, ch_=62, gap=8, fills=("lightblue", "lightyellow", "lightgreen"), x0=None, y0=14, size=13):
    """boxes with (title, example) text; returns a shapes figure"""
    rows = (len(items) + cols - 1) // cols
    w = 400; x0 = x0 if x0 is not None else (w - cols * cw - (cols - 1) * gap) // 2
    its = []
    for i, (a, b) in enumerate(items):
        r, c = divmod(i, cols); x = x0 + c * (cw + gap); y = y0 + r * (ch_ + gap)
        its += [RECT(x, y, cw, ch_, fill=fills[i % len(fills)], radius=6), T(x + cw / 2, y + 26, a, size, bold=True), T(x + cw / 2, y + 48, b, size - 1)]
    return shapes(its, w, max(110, y0 + rows * (ch_ + gap) + 6))
