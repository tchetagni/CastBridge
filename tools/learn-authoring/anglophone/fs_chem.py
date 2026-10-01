"""Chemistry helpers for the anglophone secondary packs: equation balance checker, formulae, subscripts."""
import re
from collections import Counter
from math import gcd

SUB = str.maketrans("0123456789", "₀₁₂₃₄₅₆₇₈₉")
SUP = {"+": "⁺", "-": "⁻", "2": "²", "3": "³"}


def sub(f):
    """'Ca(OH)2' -> 'Ca(OH)₂' (digits after a letter or ')' become subscripts; leading coefficients stay)."""
    return re.sub(r"(?<=[A-Za-z\)])(\d+)", lambda m: m.group(1).translate(SUB), f)


def atoms(f):
    """Atom counter of a formula such as 'Ca(OH)2', 'CuSO4.5H2O' (hydrate dot) or 'Al2(SO4)3' (no charges, no state symbols)."""
    f = re.sub(r"\((s|l|g|aq)\)", "", f).strip()
    if "." in f:
        c = Counter()
        for part in f.split("."):
            m = re.match(r"(\d*)(.*)", part); k = int(m.group(1) or 1)
            for a, n in atoms(m.group(2)).items(): c[a] += n * k
        return c

    def parse(s, i=0):
        c = Counter()
        while i < len(s):
            ch = s[i]
            if ch == "(":
                inner, i = parse(s, i + 1)
                m = re.match(r"\d+", s[i:]); k = int(m.group(0)) if m else 1
                if m: i += len(m.group(0))
                for a, n in inner.items(): c[a] += n * k
            elif ch == ")":
                return c, i + 1
            else:
                m = re.match(r"[A-Z][a-z]?", s[i:]); el = m.group(0); i += len(el)
                m = re.match(r"\d+", s[i:]); n = int(m.group(0)) if m else 1
                if m: i += len(m.group(0))
                c[el] += n
        return c, i
    return parse(f)[0]


def side(s):
    tot = Counter()
    for term in [t.strip() for t in s.split(" + ")]:
        m = re.match(r"(\d*)\s*(.*)", term); k = int(m.group(1) or 1)
        for a, n in atoms(m.group(2)).items(): tot[a] += n * k
    return tot


def E(eq, arrow="→"):
    """Checks that an ASCII equation 'a A + b B -> c C' is balanced and returns the Unicode version."""
    l, r = re.split(r"\s*(?:->|=>)\s*", eq)
    assert side(l) == side(r), "unbalanced: %s  %s vs %s" % (eq, dict(side(l)), dict(side(r)))
    out = sub(eq).replace("->", arrow).replace("=>", arrow)
    return out


def ionic(cat, ca, an, ana, group_an=False):
    """Formula from charges (criss-cross): ionic('Mg',2,'O',2) -> 'MgO'; ionic('Al',3,'SO4',2,True) -> 'Al2(SO4)3'."""
    g = gcd(ca, ana); nc, na = ana // g, ca // g
    def part(s, n, grp):
        if n == 1: return s
        return (("(%s)" % s) if grp else s) + str(n)
    return part(cat, nc, False) + part(an, na, group_an)
