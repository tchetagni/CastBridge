"""Shared helpers of the lycée / GCE generators (no generator here)."""
import re
from fractions import Fraction

from ..core import MINUS, fr, near_ints
from .mathfmt import num


def ints(rng, right, n=8, **kw):
    """Plausible wrong integers around `right`, as text."""
    return [num(v) for v in near_ints(rng, right, n, **kw)]


def E(x, dec=None):
    """English number text: 3.5, −2 (no grouping, decimal point)."""
    if isinstance(x, Fraction):
        return num(x)
    if isinstance(x, float):
        s = ("%.*f" % (dec, abs(x))) if dec is not None else ("%.6f" % abs(x)).rstrip("0").rstrip(".")
        return (MINUS if x < 0 and float(s) != 0 else "") + s
    return num(int(x))


def Fr(x, dec=2):
    """French decimal text (comma) for a float."""
    return fr(float(x), dec, group=False)


def fracs(f):
    """Fraction -> '3/4' or '5'."""
    return num(Fraction(f))


def fraction_wrongs(rng, f, extra=()):
    """Wrong fractions close to f (as text), for typical slips."""
    f = Fraction(f)
    cands = [f + Fraction(1, f.denominator), f - Fraction(1, f.denominator), -f, Fraction(f.denominator, f.numerator) if f.numerator else f + 1,
             f * 2, f / 2, f + 1, f - 1] + [Fraction(x) for x in extra]
    out, seen = [], {f}
    for c in cands:
        if c not in seen:
            seen.add(c)
            out.append(num(c))
    return out


def interval(lo, hi, lo_closed, hi_closed):
    """French interval text; lo/hi may be None for the infinities."""
    a = "−∞" if lo is None else num(lo)
    b = "+∞" if hi is None else num(hi)
    left = "[" if lo_closed and lo is not None else "]"
    right = "]" if hi_closed and hi is not None else "["
    return f"{left}{a} ; {b}{right}"


def interval_en(lo, hi, lo_closed, hi_closed):
    a = "−∞" if lo is None else num(lo)
    b = "∞" if hi is None else num(hi)
    left = "[" if lo_closed and lo is not None else "("
    right = "]" if hi_closed and hi is not None else ")"
    return f"{left}{a}, {b}{right}"


# ---------------------------------------------------------------------------------------------- bilingual generators
from .. import core as _core                      # noqa: E402
from ..core import near_floats                    # noqa: E402


_TAIL = re.compile(r"^(.*\?) \((.+)\)$", re.S)


_IMPERATIVES = [(re.compile(r"^Find (.*)\.$", re.S), r"What is \1?"), (re.compile(r"^Evaluate (.*)\.$", re.S), r"What is the value of \1?"),
                (re.compile(r"^Simplify (.*)\.$", re.S), r"What is \1 in its simplest form?"), (re.compile(r"^Solve (.*)\.$", re.S), r"What are the solutions of \1?"),
                (re.compile(r"^Differentiate (.*) with respect to x\.$", re.S), r"What is the derivative of \1 with respect to x?"),
                (re.compile(r"^Write (.*) in standard form\.$", re.S), r"How is \1 written in standard form?"), (re.compile(r"^Calculate (.*)\.$", re.S), r"What is \1?"),
                (re.compile(r"^Express (.*)\.$", re.S), r"How can you express \1?"), (re.compile(r"^State (.*)\.$", re.S), r"What is \1?")]


def questionize(text):
    """English imperatives (« Find … ») become questions: the pipeline requires the text to end with « ? »."""
    if text.endswith("?"):
        return text
    for rx, rep in _IMPERATIVES:
        if rx.match(text):
            return rx.sub(rep, text)
    return text


def tidy(d):
    """A question must end with « ? » : a trailing note in brackets (« (g = 10 N/kg) ») is moved in front of the question;
    wrong answers that are negative (when the right one is not) or 0 are dropped, as no student would hesitate on them."""
    d.text = questionize(d.text)
    m = _TAIL.match(d.text)
    if m:
        note = m.group(2)
        d.text = note[0].upper() + note[1:] + ". " + m.group(1)
    neg = d.right.startswith(MINUS)
    d.wrongs = [w for w in d.wrongs if (neg or not w.startswith(MINUS)) and not (w in ("0", "0 N", "0 %") and d.right not in ("0", "0 N", "0 %"))]
    return d


def both(courses, tpl, **kw):
    """Registers one generator body for several courses; the body is called as fn(rng, diff, lang) with lang 'fr' or 'en'.
    `courses` is a list of (course, lang). Each course has its own seed, so the variants differ."""
    def deco(fn):
        for course, lang in courses:
            _core.gen(course, tpl, **kw)(lambda rng, d, lang=lang: (lambda r: tidy(r) if r is not None else None)(fn(rng, d, lang)))
        return fn
    return deco


def T(lang, fr_text, en_text):
    return fr_text if lang == "fr" else en_text


def N(lang, x, dec=None):
    """Number text in the language: French comma / English point; trailing zeros dropped when dec is None."""
    if isinstance(x, int):
        return num(x)
    s = E(float(x), dec)
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s.replace(".", ",") if lang == "fr" else s


def numw(rng, lang, right, dec=1, n=8, extra=()):
    """Wrong numeric answers (text) near `right` plus typical-slip values in `extra`."""
    vals = [float(v) for v in extra] + list(near_floats(rng, float(right), dec, n))
    out, seen = [], {round(float(right), dec)}
    for v in vals:
        k = round(v, dec)
        if k in seen or k < 0 and right >= 0:
            continue
        seen.add(k)
        out.append(N(lang, k, dec))
    return out
