"""Shared pieces of the question-bank pipeline: courses, formatting, distractors, question building, registries.

Everything here is deterministic (seeded by the template name and the question text): rebuilding gives the same
ids and the same packs, so a diff of content/quiz/dist shows exactly what changed.
"""
import hashlib
import random
import re
import unicodedata

NB = " "   # non-breaking space: thousands separator and before ? : ; (French typography)
MINUS = "−"

# ---------------------------------------------------------------------------------------------- courses
# One course = one line of the choice screen of the TV (Parcours > Niveau > Filière). `prefix` starts every id.
COURSES = {
    "general":   dict(track="general",   level=None,  field=None,            prefix="g",  label="Culture générale"),
    "cm2":       dict(track="primary",   level="CM2", field=None,            prefix="p2", label="Primaire · CM2"),
    "3e":        dict(track="secondary", level="3e",  field=None,            prefix="s3", label="Secondaire · 3e"),
    "tle":       dict(track="secondary", level="Tle", field=None,            prefix="st", label="Secondaire · Tle"),
    "l1-droit":  dict(track="higher",    level="L1",  field="droit",         prefix="hd", label="Supérieur · L1 Droit"),
    "l1-eco":    dict(track="higher",    level="L1",  field="economie",      prefix="he", label="Supérieur · L1 Économie"),
    "l1-maths":  dict(track="higher",    level="L1",  field="mathematiques", prefix="hm", label="Supérieur · L1 Mathématiques"),
}

STATUS_REVIEW, STATUS_APPROVED = "review", "approved"
VERIF = ("computed", "fact", "import")
# 1 game = 15 questions (3 per difficulty level); 300 games without repeat = 4 500 questions.
GAMES_TARGET, PER_GAME = 300, 15
NEEDED = GAMES_TARGET * PER_GAME


def sha(s):
    return hashlib.sha1(s.encode("utf-8")).hexdigest()


def norm(s):
    """Text compared for duplicates: lower case, no accents, single spaces, no punctuation."""
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^\w\s]", " ", s)).strip()


# ---------------------------------------------------------------------------------------------- numbers
def fr(x, dec=None, group=True):
    """French number: 1 234 567 (non-breaking spaces), 3,5 (decimal comma), minus sign U+2212."""
    if isinstance(x, bool):
        raise TypeError(x)
    if isinstance(x, float):
        if dec is None:
            dec = 6
        s = ("%.*f" % (dec, abs(x))).rstrip("0").rstrip(".") if dec > 0 else "%d" % round(abs(x))
        if s == "":
            s = "0"
        ip, _, fp = s.partition(".")
        if group and len(ip) > 4:
            ip = f"{int(ip):,}".replace(",", NB)
        s = ip + ("," + fp if fp else "")
        neg = x < 0 and float(s.replace(NB, "").replace(",", ".")) != 0
    else:
        s = str(abs(int(x)))
        if group and len(s) > 4:
            s = f"{int(s):,}".replace(",", NB)
        neg = x < 0
    return (MINUS if neg else "") + s


def fcfa(x):
    return fr(int(x)) + NB + "FCFA"


def frac(n, d):
    return f"{fr(n)}/{fr(d)}"


def near_ints(rng, right, n=8, lo=None, hi=None):
    """Plausible wrong integers around `right` (off-by-one, digit slips, power-of-ten errors...), nearest first."""
    mag = max(1, len(str(abs(int(right)))))
    steps = [1, 2, 3, 5, 10, 2 * 10 ** (mag - 2) if mag > 2 else 4, 10 ** (mag - 1), 10 ** (mag - 1) // 2 or 6]
    cands = []
    for st in steps:
        for sg in (1, -1):
            cands.append(right + sg * st)
    rng.shuffle(cands)
    cands.sort(key=lambda v: abs(v - right) // max(1, 10 ** max(0, mag - 2)) + rng.random())
    out = []
    for v in cands:
        if v == right or v in out:
            continue
        if lo is not None and v < lo:
            continue
        if hi is not None and v > hi:
            continue
        out.append(v)
        if len(out) >= n:
            break
    return out


def near_floats(rng, right, dec=1, n=8):
    unit = 10 ** -dec
    cands = [right + k * unit for k in (-10, -5, -2, -1, 1, 2, 5, 10)] + [right * 10, right / 10, right * 2, right / 2]
    rng.shuffle(cands)
    cands.sort(key=lambda v: abs(v - right) + rng.random() * unit)
    out = []
    for v in cands:
        v = round(v, dec + 1)
        if abs(v - right) < unit / 2 or v in out:
            continue
        out.append(v)
        if len(out) >= n:
            break
    return out


# ---------------------------------------------------------------------------------------------- drafts & questions
class Draft:
    """What a generator returns; `build_question` turns it into the bank format (4 shuffled choices, id, status...)."""

    def __init__(self, text, right, wrongs, expl, region=None, cat=None, src=None, diff=None, alt=()):
        self.text, self.right, self.wrongs, self.expl = text, str(right), [str(w) for w in wrongs], expl
        self.region, self.cat, self.src, self.diff = region, cat, src, diff
        self.alt = [str(a) for a in alt]      # other answers that would also be correct: never used as distractors


def pick_distractors(text, right, wrongs, alt=()):
    """3 distinct wrong choices (none equal to the right answer or to an accepted alternative), stable for a given text."""
    bad = {norm(right)} | {norm(a) for a in alt}
    seen, pool = set(), []
    for w in wrongs:
        k = norm(w)
        if not k or k in bad or k in seen:
            continue
        seen.add(k)
        pool.append(w)
    if len(pool) < 3:
        return None
    if len(pool) == 3:
        return pool
    r = random.Random(sha("d|" + text))
    head = pool[:3]              # nearest candidates first; one random swap into the other candidates keeps some variety
    tail = pool[3:]
    if tail and r.random() < 0.6:
        head[r.randrange(3)] = tail[r.randrange(len(tail))]
    return head


def build_question(course, d, tpl, verif, source_text, region=None, category=None, difficulty=None):
    c = COURSES[course]
    wrongs = pick_distractors(d.text, d.right, d.wrongs, d.alt)
    if wrongs is None:
        return None
    pos = int(sha("p|" + c.get("salt", "") + d.text + d.right)[:4], 16) % 4      # `salt` (optional, per course) re-draws the positions: see tools/quiz-bank/lycee_caps.py
    choices = wrongs[:]
    choices.insert(pos, d.right)
    region = d.region or region or ("WORLD" if course != "general" else "CM")
    return {
        "id": f"{c['prefix']}-{sha(course + '|' + d.text + '|' + d.right)[:8]}",
        "track": c["track"], "level": c["level"], "field": c["field"], "region": region,
        "category": d.cat or category, "difficulty": d.diff or difficulty, "question": d.text, "choices": choices,
        "answer": pos, "explanation": d.expl, "source": d.src or source_text, "status": STATUS_REVIEW, "verif": verif,
        "lang": getattr(d, "lang", None) or c.get("lang", "fr"), "tpl": tpl,
    }


# ---------------------------------------------------------------------------------------------- generator registry
class Gen:
    def __init__(self, course, tpl, fn, cap, diffs, cat, region, source):
        self.course, self.tpl, self.fn, self.cap, self.diffs, self.cat, self.region, self.source = course, tpl, fn, cap, diffs, cat, region, source


GENS = []
# Courses that need more questions per template to reach 4 500 (maths / physics / economics have room: numbers vary freely)
CAP_SCALE = {"tle": 1.5, "l1-maths": 1.6, "l1-eco": 1.6, "3e": 1.0}


def gen(course, tpl, cap=250, diffs=(1, 2, 3, 4, 5), cat="Mathématiques", region=None, source="Généré par calcul (tools/quiz-bank)"):
    """Decorator: fn(rng, diff) -> Draft | None. The answer must come from a computation, never from memory."""
    def deco(fn):
        GENS.append(Gen(course, tpl, fn, cap, tuple(diffs), cat, region, source))
        return fn
    return deco


def run_generators(only=None, strict=True):
    """Runs every registered generator; returns (questions, failures). Deterministic."""
    out, fails = [], []
    for g in GENS:
        if only and g.course not in only:
            continue
        rng = random.Random(sha(g.course + "/" + g.tpl))
        seen, n, tries = set(), 0, 0
        cap = int(g.cap * CAP_SCALE.get(g.course, 1.0))
        while n < cap and tries < cap * 12:
            diff = g.diffs[tries % len(g.diffs)]
            tries += 1
            try:
                d = g.fn(rng, diff)
            except AssertionError as e:          # a self-check of the generator failed: a bug, never a question
                fails.append((g.course, g.tpl, "self-check: %s" % e))
                if strict:
                    raise
                continue
            if d is None:
                continue
            if d.diff is None:
                d.diff = diff
            q = build_question(g.course, d, g.tpl, "computed", g.source, g.region, g.cat, diff)
            if q is None:
                continue
            k = norm(q["question"])
            if k in seen:
                continue
            seen.add(k)
            out.append(q)
            n += 1
    return out, fails
