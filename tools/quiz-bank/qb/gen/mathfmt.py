"""Text formatting for mathematics (no generator here): polynomials, fractions, exponents, distractor helpers."""
from fractions import Fraction

from ..core import MINUS, fr

SUP = str.maketrans("0123456789-+n", "⁰¹²³⁴⁵⁶⁷⁸⁹⁻⁺ⁿ")


def sup(n):
    return str(n).translate(SUP)


def num(c):
    """int / Fraction -> text with the typographic minus."""
    if isinstance(c, Fraction):
        if c.denominator == 1:
            return num(int(c))
        return ("" if c >= 0 else MINUS) + "%d/%d" % (abs(c.numerator), c.denominator)
    return fr(c, group=False)


def term(c, var="x", p=1, first=False):
    """One monomial with its sign: '+ 3x²', '− x', '+ 5'. `first` drops the plus sign."""
    if c == 0:
        return ""
    sign = MINUS if c < 0 else "+"
    a = abs(c)
    if p == 0:
        body = num(a)
    else:
        body = ("" if a == 1 else num(a)) + var + ("" if p == 1 else sup(p))
    if first:
        return (MINUS if c < 0 else "") + body
    return " %s %s" % (sign, body)


def poly(coeffs, var="x"):
    """coeffs from the highest degree: [3, -5, 2] -> '3x² − 5x + 2'."""
    deg = len(coeffs) - 1
    out, first = "", True
    for i, c in enumerate(coeffs):
        t = term(c, var, deg - i, first=first)
        if t:
            out += t
            first = False
    return out or "0"


def ev(coeffs, x):
    r = 0
    for c in coeffs:
        r = r * x + c
    return r


def dpoly(coeffs):
    deg = len(coeffs) - 1
    return [c * (deg - i) for i, c in enumerate(coeffs[:-1])]


def ipoly(coeffs):
    """Primitive (no constant) with Fraction coefficients."""
    deg = len(coeffs)
    return [Fraction(c, deg - i) for i, c in enumerate(coeffs)] + [Fraction(0)]


def poly_variants(rng, coeffs, n=6):
    """Plausible wrong polynomials: one coefficient changed by the usual slips (sign, +-1, forgotten term)."""
    out, seen = [], {tuple(coeffs)}
    tries = 0
    while len(out) < n and tries < 60:
        tries += 1
        c = list(coeffs)
        i = rng.randrange(len(c))
        kind = rng.choice(["sign", "inc", "dec", "zero", "double", "half"])
        if kind == "sign":
            c[i] = -c[i]
        elif kind == "inc":
            c[i] += 1
        elif kind == "dec":
            c[i] -= 1
        elif kind == "zero":
            c[i] = 0
        elif kind == "double":
            c[i] *= 2
        else:
            c[i] = c[i] // 2 if c[i] % 2 == 0 else c[i] + 2
        t = tuple(c)
        if t in seen or all(x == 0 for x in c):
            continue
        seen.add(t)
        out.append(poly(c))
    return out


def sci(x, dec=2):
    """Number in decimal scientific notation text: 4,5 × 10⁴."""
    import math
    e = int(math.floor(math.log10(abs(x))))
    m = x / 10 ** e
    return "%s × 10%s" % (fr(round(m, dec), dec), sup(e))


def par(n):
    """Number in brackets when negative: (−15)."""
    return "(%s)" % num(n) if n < 0 else num(n)


def nz(rng, lo, hi):
    """Random non-zero integer."""
    v = 0
    while v == 0:
        v = rng.randint(lo, hi)
    return v
