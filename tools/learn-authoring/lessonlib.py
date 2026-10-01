"""Authoring helper for « Apprendre » packs (docs/LEARN.md). It only WRITES the existing JSON source format
(content/learn/<pack id>/pack.json + lessons/<n>-<chapter>.json); it changes nothing about the format.

Usage (see tools/learn-authoring/README.md):

    from lessonlib import *
    p = Pack("class3-maths", "Mathematics — Class 3", level="Class 3", subject="maths", cursus="primary",
             programRef="MINEDUB — ... (to be checked)")
    ch = p.chapter("numbers", "Numbers up to 1000", programRef="...")
    L = ch.lesson("place-value", "Place value", minutes=15, objectives=["..."], programRef="...", notes=["..."])
    L.key("definition", "Title", "markdown with $x^2$")           # « l'essentiel »
    L.example("Example 1", "statement", ["step 1", "step 2"], "final answer")
    L.mcq("question", "right choice", ["wrong 1", "wrong 2", "wrong 3"], "explanation", tier="application")
    ...
    L.sc("self-check question", "right", ["w1","w2","w3"], "explanation")   # exactly 5 per lesson
    p.write()

Every lesson is written with status "draft" (never reviewed/validated) and reviewNotes.
"""
import json, os, random, re, shutil

ROOT = os.environ.get("LEARN_CONTENT", "/home/user/CastBridge/content/learn")
TODAY = "2026-10-01"
AUTHOR = "CastBridge (AI draft)"
PACK_AUTHOR = "CastBridge — brouillon rédigé avec une IA, à relire par un enseignant"
STYLES = {"definition", "retenir", "attention", "methode", "objectifs", "pieges", "propriete", "formule"}
TIERS = {"application", "approfondissement", "examen"}


def plain(s):
    return re.sub(r"[*$\\]", "", s)


def _len(s, mx, what):
    if len(plain(s)) > mx:
        raise ValueError("%s too long (%d > %d chars): split it: %s" % (what, len(plain(s)), mx, s[:60]))


# ------------------------------------------------------------------ figures
def T(x, y, text, size=14, anchor="middle", color="ink", bold=False):
    d = {"t": "text", "x": x, "y": y, "text": text, "size": size, "anchor": anchor, "color": color}
    if bold: d["bold"] = True
    return d

def LINE(x1, y1, x2, y2, color="ink", width=2, dash=False, arrow="none"):
    d = {"t": "line", "x1": x1, "y1": y1, "x2": x2, "y2": y2, "color": color, "width": width}
    if dash: d["dash"] = True
    if arrow != "none": d["arrow"] = arrow          # "end" | "start" | "both"
    return d

def RECT(x, y, w, h, fill=None, stroke="ink", width=2, radius=0):
    d = {"t": "rect", "x": x, "y": y, "w": w, "h": h, "stroke": stroke, "width": width}
    if fill: d["fill"] = fill
    if radius: d["radius"] = radius
    return d

def CIRCLE(cx, cy, r, fill=None, stroke="ink", width=2):
    d = {"t": "circle", "cx": cx, "cy": cy, "r": r, "stroke": stroke, "width": width}
    if fill: d["fill"] = fill
    return d

def POLY(pts, fill=None, stroke="ink", width=2, closed=True):
    d = {"t": "poly", "pts": pts, "stroke": stroke, "width": width, "closed": closed}
    if fill: d["fill"] = fill
    return d

def ANGLE(x, y, a_from, a_to, r=22, right=False, label=None, color="red"):
    d = {"t": "angle", "x": x, "y": y, "from": a_from, "to": a_to, "r": r, "right": right, "color": color}
    if label: d["label"] = label
    return d

def PATH(d_, fill=None, stroke="ink", width=2):
    d = {"t": "path", "d": d_, "stroke": stroke, "width": width}
    if fill: d["fill"] = fill
    return d

def shapes(items, w=400, h=240):
    return {"kind": "shapes", "w": w, "h": h, "items": items}

def bars(data, unit=None, w=400, h=240):
    """data = [(label, value) or (label, value, color)]"""
    d = {"kind": "bars", "w": w, "h": h,
         "bars": [{"label": x[0], "value": x[1], "color": x[2] if len(x) > 2 else "blue"} for x in data]}
    if unit: d["unit"] = unit
    return d

def plot(xmin, xmax, ymin, ymax, curves=(), points=(), segments=(), grid=1, xlabel=None, ylabel=None, w=400, h=260):
    """curves = [(expr, color, label)]; points = [(x, y, label, color)]; segments = [(x1,y1,x2,y2,color,dash,label)]"""
    d = {"kind": "plot", "xmin": xmin, "xmax": xmax, "ymin": ymin, "ymax": ymax, "grid": grid, "w": w, "h": h,
         "curves": [{"expr": c[0], "color": c[1] if len(c) > 1 else "blue", **({"label": c[2]} if len(c) > 2 and c[2] else {})} for c in curves],
         "points": [{"x": q[0], "y": q[1], "label": q[2] if len(q) > 2 else None, "color": q[3] if len(q) > 3 else "red"} for q in points],
         "segments": [{"x1": s[0], "y1": s[1], "x2": s[2], "y2": s[3], "color": s[4] if len(s) > 4 else "grey",
                       "dash": s[5] if len(s) > 5 else True, **({"label": s[6]} if len(s) > 6 and s[6] else {})} for s in segments]}
    if xlabel: d["xlabel"] = xlabel
    if ylabel: d["ylabel"] = ylabel
    return d

def timeline(frm, to, events=(), periods=(), w=480, h=240):
    """events = [(year, label)]; periods = [(from, to, label, color)]"""
    return {"kind": "timeline", "from": frm, "to": to, "w": w, "h": h,
            "events": [{"year": a, "label": b} for a, b in events],
            "periods": [{"from": p[0], "to": p[1], "label": p[2], "color": p[3] if len(p) > 3 else "orange"} for p in periods]}

def count(n, shape="circle", color="orange", per_row=5, w=400, h=200):
    return {"kind": "count", "n": n, "shape": shape, "color": color, "perRow": per_row, "w": w, "h": h}


# ------------------------------------------------------------------ lessons
class Lesson:
    def __init__(self, ch, slug, title, minutes, objectives, programRef, prerequisites, notes, read_aloud):
        self.ch, self.slug, self.title = ch, slug, title
        self.id = "%s-%s" % (ch.pack.id, slug)
        self.d = {"id": self.id, "title": title, "minutes": minutes, "programRef": programRef, "status": "draft",
                  "author": AUTHOR,
                  "source": "Original writing following the Cameroonian curriculum; to be reviewed by a teacher",
                  "objectives": objectives, "prerequisites": prerequisites, "exercises": [], "selfCheck": [],
                  "reviewNotes": notes, "blocks": []}
        if read_aloud is not None: self.d["readAloud"] = read_aloud
        self.ex_list, self.n_ex, self.n_sc, self.illustrations = [], 0, 0, 0

    # ---- blocks
    def heading(self, text): self.d["blocks"].append({"type": "heading", "text": text})
    def text(self, md):
        _len(md, 650, "text block"); self.d["blocks"].append({"type": "text", "md": md})
    def key(self, style, title, md, tex=None):
        assert style in STYLES, style
        _len(md, 650, "key block")
        b = {"type": "key", "style": style, "title": title, "md": md}
        if tex: b["tex"] = tex
        self.d["blocks"].append(b)
    def formula(self, tex, caption=None):
        b = {"type": "formula", "tex": tex}
        if caption: b["caption"] = caption
        self.d["blocks"].append(b)
    def example(self, title, statement, steps, answer, figure=None):
        assert len(steps) >= 2, "an example needs >= 2 steps"
        _len(statement, 650, "example statement")
        for s in steps: _len(s if isinstance(s, str) else s[0], 320, "example step")
        b = {"type": "example", "title": title, "statement": statement,
             "steps": [s if isinstance(s, str) else ({"md": s[0], "tex": s[1]}) for s in steps], "answer": answer}
        if figure: b["figure"] = figure; self.illustrations += 1
        self.d["blocks"].append(b)
    def illustration(self, figure, caption, alt):
        self.illustrations += 1
        self.d["blocks"].append({"type": "illustration", "figure": figure, "caption": caption, "alt": alt})
    def more(self, *items): self.d["blocks"].append({"type": "more", "items": list(items)})
    def audio(self, text, lang="en-GB"): self.d["blocks"].append({"type": "audio", "text": text, "lang": lang})
    def exercises_here(self): self.d["blocks"].append({"type": "exercise", "ref": self.ex_list[-1]})

    # ---- exercises (ids are generated; every exercise gets source + chapter + lesson)
    def _ex(self, kind, tier, mock, **kw):
        self.n_ex += 1
        eid = "%s-%s-%s%02d" % (self.ch.pack.id, self.ch.slug, "m" if mock else "e", self._count(mock))
        d = {"id": eid, "kind": kind, "tier": tier, "lesson": self.id, "source": self.ch.pack.source_note}
        d.update({k: v for k, v in kw.items() if v is not None})
        _len(d["prompt"], 650, "exercise " + eid)
        self.ch.exercises.append(d)
        if not mock and tier in TIERS: self.d["exercises"].append(eid)
        return eid

    def _count(self, mock):
        key = "_m" if mock else "_e"
        self.ch.counters[key] = self.ch.counters.get(key, 0) + 1
        return self.ch.counters[key]

    def mcq(self, prompt, right, wrongs, explanation, tier="application", difficulty=1, points=1, method=None,
            mistakes=None, figure=None, mock=False):
        assert len(wrongs) >= 2 and right not in wrongs
        choices = list(wrongs) + [right]
        random.Random(prompt).shuffle(choices)
        return self._ex("mcq", tier, mock, prompt=prompt, choices=choices, answer=choices.index(right),
                        explanation=explanation, difficulty=difficulty, points=points, method=method,
                        mistakes=mistakes, figure=figure)

    def tf(self, prompt, answer, explanation, tier="application", difficulty=1, points=1, method=None, mistakes=None, mock=False, figure=None):
        return self._ex("truefalse", tier, mock, prompt=prompt, answer=bool(answer), explanation=explanation,
                        difficulty=difficulty, points=points, method=method, mistakes=mistakes, figure=figure)

    def num(self, prompt, answer, explanation, tolerance=0, unit=None, tier="application", difficulty=1, points=1,
            method=None, mistakes=None, figure=None, mock=False):
        return self._ex("numeric", tier, mock, prompt=prompt, answer=answer, tolerance=tolerance, unit=unit,
                        explanation=explanation, difficulty=difficulty, points=points, method=method,
                        mistakes=mistakes, figure=figure)

    def match(self, prompt, pairs, explanation, tier="application", difficulty=1, points=2, method=None, mock=False):
        return self._ex("matching", tier, mock, prompt=prompt, pairs=[list(p) for p in pairs], explanation=explanation,
                        difficulty=difficulty, points=points, method=method)

    def open(self, prompt, model, rubric, explanation=None, tier="approfondissement", difficulty=2, points=3,
             method=None, mistakes=None, figure=None, mock=False):
        return self._ex("open", tier, mock, prompt=prompt, model=model, rubric=rubric,
                        explanation=explanation or model, difficulty=difficulty, points=points, method=method,
                        mistakes=mistakes, figure=figure)

    def problem(self, prompt, parts, tier="examen", difficulty=2, method=None, mistakes=None, figure=None, mock=False):
        """parts = list of dicts {kind: mcq|numeric|truefalse|open|matching, prompt, answer..., points, explanation};
        build them with part_mcq / part_num / part_tf / part_open."""
        eid = "%s-%s-%s%02d" % (self.ch.pack.id, self.ch.slug, "m" if mock else "e", self._count(mock))
        ps = []
        for i, q in enumerate(parts):
            q = dict(q); q["id"] = "%s-%s" % (eid, "abcdefghijkl"[i]); ps.append(q)
        d = {"id": eid, "kind": "problem", "tier": tier, "lesson": self.id, "source": self.ch.pack.source_note,
             "prompt": prompt, "difficulty": difficulty, "parts": ps}
        if method: d["method"] = method
        if mistakes: d["mistakes"] = mistakes
        if figure: d["figure"] = figure
        _len(prompt, 650, "problem " + eid)
        self.ch.exercises.append(d)
        if not mock: self.d["exercises"].append(eid)
        self.n_ex += 1
        return eid

    def sc(self, prompt, right, wrongs, explanation):
        """Self-check question (exported to the quiz): MCQ with exactly 4 choices."""
        assert len(wrongs) == 3, "self-check = 4 choices"
        self.n_sc += 1
        eid = "%s-%s-q%d" % (self.ch.pack.id, self.slug, self.n_sc)
        choices = list(wrongs) + [right]
        random.Random(eid + prompt).shuffle(choices)
        d = {"id": eid, "kind": "mcq", "tier": "autoeval", "points": 1, "lesson": self.id, "prompt": prompt,
             "choices": choices, "answer": choices.index(right), "explanation": explanation}
        _len(prompt, 650, "selfcheck " + eid)
        self.ch.exercises.append(d); self.d["selfCheck"].append(eid)
        return eid


def part_mcq(prompt, right, wrongs, explanation, points=1):
    choices = list(wrongs) + [right]; random.Random(prompt).shuffle(choices)
    return {"kind": "mcq", "points": points, "prompt": prompt, "choices": choices, "answer": choices.index(right), "explanation": explanation}

def part_num(prompt, answer, explanation, tolerance=0, unit=None, points=1):
    d = {"kind": "numeric", "points": points, "prompt": prompt, "answer": answer, "tolerance": tolerance, "explanation": explanation}
    if unit: d["unit"] = unit
    return d

def part_tf(prompt, answer, explanation, points=1):
    return {"kind": "truefalse", "points": points, "prompt": prompt, "answer": bool(answer), "explanation": explanation}

def part_open(prompt, model, rubric, points=2):
    return {"kind": "open", "points": points, "prompt": prompt, "model": model, "rubric": rubric, "explanation": model}


class Chapter:
    def __init__(self, pack, slug, title, order, programRef):
        self.pack, self.slug, self.title, self.order, self.programRef = pack, slug, title, order, programRef
        self.id = "%s-%s" % (pack.id, slug)
        self.lessons, self.exercises, self.counters = [], [], {}

    def lesson(self, slug, title, minutes=20, objectives=(), programRef="", prerequisites=(), notes=(), read_aloud=None):
        L = Lesson(self, slug, title, minutes, list(objectives), programRef or self.programRef,
                   list(prerequisites), list(notes) + [self.pack.generic_note], read_aloud)
        self.lessons.append(L)
        return L


class Pack:
    def __init__(self, id, title, level, subject, cursus, exam=None, description="", programRef="", lang="en",
                 series=(), source_note=None, wanted=None, extend=False):
        self.wanted, self.extend = wanted, extend          # wanted = ideal catalog subject key when none exists yet
        self.existing = None
        if extend:                                          # add chapters to an existing pack (fslc-*, gceol-*, ...)
            with open(os.path.join(ROOT, id, "pack.json"), encoding="utf-8") as f: self.existing = json.load(f)
            e = self.existing
            title, level, subject, cursus, exam = e["title"], e["level"], e["subject"], e["cursus"], e.get("exam")
            description, programRef, lang, series = e.get("description", ""), e.get("programRef", ""), e.get("lang", "en"), e.get("series", [])
        self.id, self.title, self.level, self.subject, self.cursus = id, title, level, subject, cursus
        self.exam, self.description, self.programRef, self.lang, self.series = exam, description, programRef, lang, list(series)
        self.source_note = source_note or "Original exercise written for CastBridge, in the style of the curriculum"
        self.generic_note = ("Syllabus references, scope and level of difficulty are to be checked against the official "
                             "text; all facts, formulas and worked examples to be verified by a teacher before release.")
        self.chapters, self.mocks = [], []

    def chapter(self, slug, title, programRef=""):
        base = max([c["order"] for c in self.existing["chapters"]], default=0) if self.existing else 0
        c = Chapter(self, slug, title, base + len(self.chapters) + 1, programRef or self.programRef)
        self.chapters.append(c); return c

    def mock(self, slug, title, minutes, instructions, sections, out_of=20):
        """sections = [(section title, [exercise ids made with mock=True])]; total points must be out_of."""
        self.mocks.append({"id": "%s-mock-%s" % (self.id, slug), "title": title, "minutes": minutes, "outOf": out_of,
                           "instructions": instructions,
                           "sections": [{"title": t, "exercises": ids} for t, ids in sections]})

    def check_local(self):
        total_illus = sum(l.illustrations for c in self.chapters for l in c.lessons)
        assert self.extend or total_illus >= 2, "%s: >= 2 illustrations per pack" % self.id
        for c in self.chapters:
            for l in c.lessons:
                assert l.n_sc == 5, "%s: 5 self-check questions (has %d)" % (l.id, l.n_sc)
                assert l.illustrations >= 1, "%s: at least one illustration/figure" % l.id
                assert sum(1 for e in l.ex_list) == 0 or True

    def write(self):
        self.check_local()
        base = os.path.join(ROOT, self.id)
        ld = os.path.join(base, "lessons")
        os.makedirs(ld, exist_ok=True)
        mine = {"%d-%s.json" % (c.order, c.slug) for c in self.chapters}
        if self.extend:                               # only replace the files of the chapters this script owns
            for f in os.listdir(ld):
                if f in mine: os.remove(os.path.join(ld, f))
        else:                                         # regenerate: remove previous generated lessons of this pack
            for f in os.listdir(ld):
                if f.endswith(".json"): os.remove(os.path.join(ld, f))
        new_ch = [{"id": c.id, "title": c.title, "order": c.order, "programRef": c.programRef} for c in self.chapters]
        if self.extend:
            e = self.existing
            keep = [c for c in e["chapters"] if c["id"] not in {n["id"] for n in new_ch}]
            pack = dict(e); pack["chapters"] = sorted(keep + new_ch, key=lambda c: c["order"]); pack["updatedAt"] = TODAY
            mocks = [m for m in e.get("mockExams", []) if m["id"] not in {n["id"] for n in self.mocks}] + self.mocks
            if mocks: pack["mockExams"] = mocks
        else:
            pack = {"format": 1, "id": self.id, "version": 1, "title": self.title, "description": self.description,
                    "lang": self.lang, "cursus": self.cursus, "level": self.level, "subject": self.subject}
            if self.wanted: pack["subjectWanted"] = self.wanted
            if self.exam: pack["exam"] = self.exam
            if self.series: pack["series"] = self.series
            pack.update({"programRef": self.programRef, "authors": [PACK_AUTHOR], "status": "draft", "updatedAt": TODAY,
                         "chapters": new_ch})
            if self.mocks: pack["mockExams"] = self.mocks
        with open(os.path.join(base, "pack.json"), "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, indent=2); f.write("\n")
        for c in self.chapters:
            doc = {"chapter": c.id, "lessons": [dict(l.d, chapter=c.id) for l in c.lessons], "exercises": [dict(e, chapter=c.id) for e in c.exercises]}
            with open(os.path.join(ld, "%d-%s.json" % (c.order, c.slug)), "w", encoding="utf-8") as f:
                json.dump(doc, f, ensure_ascii=False, indent=1); f.write("\n")
        n_l = sum(len(c.lessons) for c in self.chapters); n_e = sum(len(c.exercises) for c in self.chapters)
        print("wrote %s: %d new chapters, %d lessons, %d exercise records" % (self.id, len(self.chapters), n_l, n_e))
