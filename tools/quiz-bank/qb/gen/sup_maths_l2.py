"""Licence 2 Mathématiques (parcours l2-maths) : analyse (intégrales généralisées, intégration, séries entières, Fourier,
suites de fonctions, intégrales doubles, calcul différentiel à plusieurs variables, équations différentielles), algèbre
linéaire, probabilités et statistiques, arithmétique et algèbre, optimisation linéaire, graphes.

Chaque réponse est calculée (fractions exactes) puis recoupée par une seconde méthode : intégration numérique, différences
finies exactes sur des polynômes, produit matriciel, force brute, énumération. Une assertion qui échoue est un bogue, jamais
une question."""
import math
from collections import Counter
from fractions import Fraction
from itertools import combinations, permutations, product

from ..core import Draft, MINUS, fr, gen, near_ints
from .mathfmt import num, nz, par, poly, sup

C = "l2-maths"
SRC = "Programme de Licence 2 de mathématiques - réponse calculée et recoupée par une seconde méthode"
PI = math.pi
F = Fraction


# ----------------------------------------------------------------------------------------------- generic helpers
def _terminating(f):
    d = f.denominator
    for p in (2, 5):
        while d % p == 0:
            d //= p
    return d == 1


def dec(f, maxk=6):
    """Exact decimal (French comma) if the fraction terminates, otherwise the fraction n/d."""
    f = F(f)
    if f.denominator == 1:
        return fr(int(f))
    if _terminating(f):
        for k in range(1, maxk + 1):
            if (f * 10 ** k).denominator == 1:
                n = abs(int(f * 10 ** k))
                s = str(n).rjust(k + 1, "0")
                return (MINUS if f < 0 else "") + s[:-k] + "," + s[-k:]
    return num(f)


def rnd(f, places):
    """Half-up rounding of a Fraction; None when too close to a tie."""
    f = F(f)
    scaled = abs(f) * 10 ** places
    fl = scaled.numerator // scaled.denominator
    rest = scaled - fl
    if abs(rest - F(1, 2)) < F(1, 10 ** 9):
        return None
    r = fl + (1 if rest > F(1, 2) else 0)
    return (-1 if f < 0 else 1) * F(r, 10 ** places)


def fx(f, places):
    r = rnd(f, places)
    if r is None:
        return None
    n = abs(int(r * 10 ** places))
    s = str(n).rjust(places + 1, "0")
    return (MINUS if r < 0 else "") + s[:-places] + "," + s[-places:]


def fxs(vals, places):
    return [o for o in (fx(v, places) for v in vals) if o is not None]


def dedup(vals, right=None):
    out, seen = [], {right} if right is not None else set()
    for v in vals:
        if v in seen:
            continue
        seen.add(v)
        out.append(v)
    return out


def V(v, sep=" ; "):
    return "(" + sep.join(num(x) for x in v) + ")"


def Mx(m):
    return "[" + ", ".join("[" + ", ".join(num(x) for x in row) + "]" for row in m) + "]"


def det2(m):
    return m[0][0] * m[1][1] - m[0][1] * m[1][0]


def det3(m):
    a, b, c = m[0]
    d, e, f = m[1]
    g, h, i = m[2]
    return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)


def det3_col(m):
    """Second computation: Laplace expansion along the first column."""
    def minor(r):
        rows = [row[1:] for k, row in enumerate(m) if k != r]
        return det2(rows)
    return sum((-1) ** r * m[r][0] * minor(r) for r in range(3))


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(len(b))) for j in range(len(b[0]))] for i in range(len(a))]


def matvec(a, v):
    return [sum(a[i][k] * v[k] for k in range(len(v))) for i in range(len(a))]


def ident(n):
    return [[F(int(i == j)) for j in range(n)] for i in range(n)]


def inverse(m):
    """Gauss-Jordan with Fractions; None if singular."""
    n = len(m)
    a = [[F(x) for x in row] + ident(n)[i] for i, row in enumerate(m)]
    for c in range(n):
        piv = next((r for r in range(c, n) if a[r][c] != 0), None)
        if piv is None:
            return None
        a[c], a[piv] = a[piv], a[c]
        pv = a[c][c]
        a[c] = [x / pv for x in a[c]]
        for r in range(n):
            if r != c and a[r][c] != 0:
                f = a[r][c]
                a[r] = [x - f * y for x, y in zip(a[r], a[c])]
    return [row[n:] for row in a]


def rank(rows):
    a = [[F(x) for x in r] for r in rows]
    rk, ncol = 0, len(a[0])
    for c in range(ncol):
        piv = next((r for r in range(rk, len(a)) if a[r][c] != 0), None)
        if piv is None:
            continue
        a[rk], a[piv] = a[piv], a[rk]
        for r in range(len(a)):
            if r != rk and a[r][c] != 0:
                f = a[r][c] / a[rk][c]
                a[r] = [x - f * y for x, y in zip(a[r], a[rk])]
        rk += 1
    return rk


def minors_rank(rows):
    """Second method for the rank: largest k such that some k×k minor is non-zero (brute force)."""
    m, n = len(rows), len(rows[0])
    best = 0
    for k in range(1, min(m, n) + 1):
        for ri in combinations(range(m), k):
            for ci in combinations(range(n), k):
                sub = [[rows[i][j] for j in ci] for i in ri]
                if _det(sub) != 0:
                    best = k
                    break
            else:
                continue
            break
    return best


def _det(m):
    n = len(m)
    if n == 1:
        return m[0][0]
    if n == 2:
        return det2(m)
    return sum((-1) ** j * m[0][j] * _det([row[:j] + row[j + 1:] for row in m[1:]]) for j in range(n))


def unimodular(rng, n, steps=4):
    """Integer matrix of determinant ±1 (random elementary operations)."""
    m = [[int(i == j) for j in range(n)] for i in range(n)]
    for _ in range(steps):
        i, j = rng.sample(range(n), 2)
        k = rng.choice([-2, -1, 1, 2])
        m[i] = [a + k * b for a, b in zip(m[i], m[j])]
    return m


def simpson(f, a, b, n=2000):
    h = (b - a) / n
    s = f(a) + f(b)
    for i in range(1, n):
        s += (4 if i % 2 else 2) * f(a + i * h)
    return s * h / 3


def close(x, y, tol=1e-7):
    return abs(x - y) <= tol * max(1.0, abs(y))


def sub_digits(n):
    return str(n).translate(str.maketrans("0123456789-", "₀₁₂₃₄₅₆₇₈₉₋"))


def ex(r):
    """e^r as text: eⁿ for an integer, e^(p/q) otherwise."""
    r = F(r)
    if r == 0:
        return "1"
    if r == 1:
        return "e"
    if r.denominator == 1:
        return "e" + sup(int(r))
    return "e^(" + num(r) + ")"


def exx(a, var="x"):
    """e^(a·var): e^x, e^(−x), e^(2x), e^(3x/2)."""
    a = F(a)
    if a == 1:
        return "e^" + var
    if a == -1:
        return "e^(" + MINUS + var + ")"
    if a.denominator == 1:
        return "e^(" + num(a) + var + ")"
    return "e^(" + num(a) + ")" + var if False else "e^(" + num(a.numerator) + var + "/" + str(a.denominator) + ")"


def coef(c, body, first=True):
    """Coefficient times a body ('e²', 'ln 2', 'π'...), signs handled, fractions in brackets: (3/2)e²."""
    c = F(c)
    if c == 0:
        return ""
    s = "" if (c > 0 and first) else (" + " if c > 0 else (MINUS if first else " − "))
    a = abs(c)
    if a == 1:
        t = body
    elif a.denominator == 1:
        t = f"{a.numerator}{body}"
    else:
        t = f"({a.numerator}/{a.denominator}){body}"
    return s + t


def const_term(c, first=False):
    c = F(c)
    if c == 0:
        return ""
    s = ("" if first and c > 0 else MINUS if first else " + " if c > 0 else " − ")
    return s + num(abs(c))


def mono(c, p, q, first=False):
    """Monomial c·x^p·y^q with sign handling."""
    if c == 0:
        return ""
    a = abs(c)
    body = ("" if a == 1 and (p or q) else num(a)) + ("x" + (sup(p) if p > 1 else "") if p else "") + ("y" + (sup(q) if q > 1 else "") if q else "")
    if first:
        return (MINUS if c < 0 else "") + body
    return (" − " if c < 0 else " + ") + body


def poly2(terms):
    """terms: list of (c, p, q) -> text of the polynomial in x, y."""
    out, first = "", True
    for c, p, q in terms:
        t = mono(c, p, q, first)
        if t:
            out += t
            first = False
    return out or "0"


def ev2(terms, x, y):
    return sum(c * x ** p * y ** q for c, p, q in terms)


def rand_terms(rng, nterms, maxdeg=3, cmax=5, mixed=True):
    seen, out = set(), []
    while len(out) < nterms:
        p, q = rng.randint(0, maxdeg), rng.randint(0, maxdeg)
        if p + q == 0 or p + q > maxdeg + 1 or (p, q) in seen:
            continue
        if not mixed and p and q:
            continue
        seen.add((p, q))
        out.append((nz(rng, -cmax, cmax), p, q))
    return out


# ---- exact derivatives of polynomials by finite differences (5-point stencils are exact up to degree 4 / 5)
def d1(f, x, h=F(1, 7)):
    return (-f(x + 2 * h) + 8 * f(x + h) - 8 * f(x - h) + f(x - 2 * h)) / (12 * h)


def d2(f, x, h=F(1, 7)):
    return (-f(x + 2 * h) + 16 * f(x + h) - 30 * f(x) + 16 * f(x - h) - f(x - 2 * h)) / (12 * h * h)


def pdx(terms):
    return [(c * p, p - 1, q) for c, p, q in terms if p]


def pdy(terms):
    return [(c * q, p, q - 1) for c, p, q in terms if q]


# ======================================================================================================== ANALYSE
@gen(C, "l2m-improper-power", cap=50, cat="Intégrales généralisées")
def improper_power(rng, d):
    if rng.random() < 0.6:
        c, a = rng.randint(1, 9), rng.randint(2, 8)
        val = F(1, (a - 1) * c ** (a - 1))
        # second method: substitution t = 1/x  ->  integral over ]0, 1/c] of t^(a-2) dt
        assert val == F(1, c) ** (a - 1) / (a - 1)
        right = num(val)
        wr = [num(F(1, c ** a)), num(F(1, a * c ** a)), num(F(1, (a - 1) * c ** a)), num(F(1, c ** (a - 1))), num(F(a, c)), "l'intégrale diverge"]
        return Draft(f"Que vaut l'intégrale généralisée de 1/x{sup(a)} sur [{c} ; +∞[ ?", right, wr,
                     f"Une primitive est −1/({a - 1}x^{a - 1}) ; elle tend vers 0 en +∞, d'où la valeur 1/({a - 1}·{c ** (a - 1)}) = {right}.", src=SRC)
    q, p = rng.choice([(2, 1), (3, 1), (3, 2), (4, 1), (4, 3)])      # exponent p/q < 1
    m = rng.randint(1, 5)
    c = m ** q
    a = F(p, q)
    val = F(m) ** (q - p) / (1 - a)
    # second method: x = u^q  ->  integral over [0, m] of q u^(q-1-p) du
    chk = F(q) * F(m) ** (q - p) / (q - p)
    assert val == chk
    right = num(val)
    wr = [num(F(m ** q) / (1 - a)), num(F(m) ** (q - p)), num(F(m) ** (q - p) / a), num(val + 1), "l'intégrale diverge"]
    return Draft(f"Que vaut l'intégrale généralisée de 1/x^({p}/{q}) sur ]0 ; {c}] ?", right, wr,
                 f"Une primitive est x^({q - p}/{q}) ÷ ({q - p}/{q}) ; elle s'annule en 0, donc la valeur est {c}^({q - p}/{q}) × {q}/{q - p} = {right}.", src=SRC)


@gen(C, "l2m-improper-converge-a", cap=40, cat="Intégrales généralisées")
def improper_converge_a(rng, d):
    k = rng.randint(1, 9)
    kind = rng.choice(["inf", "zero", "exp"])
    big = [F(3, 2), F(2), F(3), F(5, 2), F(7, 4), F(4), F(9, 8)]
    small = [F(1, 2), F(1, 3), F(2, 3), F(3, 4), F(1, 4), F(1, 5)]
    onep = [F(1), F(1, 2), F(3, 4), F(1, 3), F(2, 3), F(0)]
    lo = [F(1), F(3, 2), F(2), F(3), F(5, 2), F(4)]
    kk = "" if k == 1 else f"{k}"
    if kind == "inf":
        c = rng.randint(1, 6)
        right = rng.choice(big)
        wr = rng.sample(onep, 3)
        text = f"Pour quelle valeur de l'exposant a l'intégrale ∫ de {c} à +∞ de {kk}/x^a dx converge-t-elle ?".replace("de /", "de 1/") if False else f"Pour quelle valeur de a l'intégrale ∫ de {c} à +∞ de {k}/x^a dx converge-t-elle ?"
        ex_ = "Au voisinage de +∞, ∫ dx/x^a converge si et seulement si a > 1 (critère de Riemann)."
        assert right > 1 and all(w <= 1 for w in wr)
    elif kind == "zero":
        c = rng.randint(1, 6)
        right = rng.choice(small)
        wr = rng.sample(lo, 3)
        text = f"Pour quelle valeur de a l'intégrale ∫ de 0 à {c} de {k}/x^a dx converge-t-elle ?"
        ex_ = "Au voisinage de 0, ∫ dx/x^a converge si et seulement si a < 1 (critère de Riemann)."
        assert right < 1 and all(w >= 1 for w in wr)
    else:
        c = rng.randint(0, 5)
        right = rng.choice([F(1), F(2), F(3), F(1, 2), F(5), F(3, 2)])
        wr = rng.sample([F(0), F(-1), F(-2), F(-1, 2), F(-3)], 3)
        text = f"Pour quelle valeur de a l'intégrale ∫ de {c} à +∞ de {k}·e^(−ax) dx converge-t-elle ?"
        ex_ = "∫ e^(−ax) dx converge en +∞ si et seulement si a > 0 : sinon l'exponentielle ne tend pas vers 0."
        assert right > 0 and all(w <= 0 for w in wr)
    return Draft(text, num(right), [num(w) for w in wr], ex_, src=SRC)


@gen(C, "l2m-improper-exp", cap=43, cat="Intégrales généralisées")
def improper_exp(rng, d):
    if rng.random() < 0.55:
        n, a = rng.randint(0, 5), rng.choice([1, 2, 3, 4, 5, 6, 7, 8])
        val = F(math.factorial(n), a ** (n + 1))
        num_val = simpson(lambda x: x ** n * math.exp(-a * x), 0, 40.0 / a, 20000)
        assert close(num_val, float(val), 1e-6)
        right = num(val)
        xp = "" if n == 0 else ("x" if n == 1 else "x" + sup(n))
        wr = [num(F(math.factorial(n), a ** n)), num(F(1, a ** (n + 1))), num(F(math.factorial(n + 1), a ** (n + 1))), num(F(n + 1, a ** (n + 1))), num(F(math.factorial(n), a ** (n + 2)))]
        return Draft(f"Que vaut l'intégrale généralisée de {xp}{exx(-a)} sur [0 ; +∞[ ?", right, wr,
                     f"∫₀^∞ xⁿe^(−ax)dx = n!/a^(n+1) : ici {n}!/{a}^{n + 1} = {right}.", src=SRC)
    a, c = rng.randint(1, 6), rng.randint(1, 6)
    val = math.exp(-a * c) / a
    assert close(simpson(lambda x: math.exp(-a * x), c, c + 40.0 / a, 20000), val, 1e-6)
    def form(e_, den):
        return ex(e_) + (f"/{den}" if den != 1 else "")
    right = form(-a * c, a)
    wr = [form(a * c, a), form(-a * c, 1), form(-a * c, a * a), form(-a, a) if c != 1 else form(-a * c + a, a), form(-c, a) if a != c else form(-a * c, a * a) + "·2"]
    wr = dedup(wr, right)
    return Draft(f"Que vaut l'intégrale généralisée de e^(−{a}x) sur [{c} ; +∞[ ?", right, wr,
                 f"Une primitive est −e^(−{a}x)/{a}, nulle en +∞ : la valeur est e^(−{a * c})/{a}.".replace("e^(−" + str(a * c) + ")", ex(-a * c)), src=SRC)


@gen(C, "l2m-ibp-exp", cap=90, cat="Intégration")
def ibp_exp(rng, d):
    a = nz(rng, -5, 5)
    p, q = nz(rng, -6, 6), rng.randint(-5, 5)
    A = F(p + q, a) - F(p, a * a)
    B = F(p, a * a) - F(q, a)
    val = float(A) * math.exp(a) + float(B)
    assert close(simpson(lambda x: (p * x + q) * math.exp(a * x), 0, 1, 2000), val, 1e-8)

    def expr(AA, BB):
        s = coef(AA, ex(a))
        return (s + const_term(BB, first=(s == ""))) if (s or BB) else "0"
    right = expr(A, B)
    wr = [expr(B, A), expr(F(p + q, a), F(-q, a)), expr(A, -B), expr(F(p + q, a) + F(p, a * a), B), expr(-A, B), expr(A, B + 1)]
    wr = dedup(wr, right)
    text = f"Que vaut ∫ de 0 à 1 de ({poly([p, q], 'x')})·{exx(a)} dx (intégration par parties) ?"
    return Draft(text, right, wr, f"Primitive : e^({num(a)}x)·[({poly([p, q], 'x')})/{num(a)} − {num(p)}/{num(a * a)}] ; on évalue entre 0 et 1.", src=SRC)


@gen(C, "l2m-ibp-log", cap=80, cat="Intégration")
def ibp_log(rng, d):
    n, k = rng.randint(1, 6), rng.randint(1, 6)
    b = rng.choice([2, 3, 4, 5, "e"])
    xn = "x" if n == 1 else "x" + sup(n)
    kk = "" if k == 1 else f"{k}"
    if b == "e":
        A, B = F(k * n, (n + 1) ** 2), F(k, (n + 1) ** 2)
        val = float(A) * math.exp(n + 1) + float(B)
        assert close(simpson(lambda x: k * x ** n * math.log(x), 1, math.e, 4000), val, 1e-6)
        mk = lambda AA, BB: coef(AA, ex(n + 1)) + const_term(BB)
        right = mk(A, B)
        wr = [mk(A, -B), mk(F(k, n + 1), F(k, (n + 1) ** 2)), mk(F(k * n, n + 1), B), mk(A, 0) if B else mk(A, 1), mk(A + B, B), mk(B, A)]
        up = "e"
    else:
        A, B = F(k * b ** (n + 1), n + 1), F(k * (b ** (n + 1) - 1), (n + 1) ** 2)
        val = float(A) * math.log(b) - float(B)
        assert close(simpson(lambda x: k * x ** n * math.log(x), 1, b, 4000), val, 1e-6)
        mk = lambda AA, BB: coef(AA, f"ln {b}") + const_term(-BB)
        right = mk(A, B)
        wr = [mk(A, -B), mk(A, F(k, (n + 1) ** 2)), mk(F(k * b ** n, n + 1), B), mk(A, F(k * b ** (n + 1), (n + 1) ** 2)), mk(A, F(k * b ** (n + 1) - 1, n + 1))]
        up = str(b)
    wr = dedup(wr, right)
    return Draft(f"Que vaut ∫ de 1 à {up} de {kk}{xn}·ln x dx (intégration par parties) ?", right, wr,
                 f"On pose u = ln x et v' = {xn} : on obtient {k}[x^{n + 1}·ln x/{n + 1} − x^{n + 1}/{(n + 1) ** 2}] évalué entre 1 et {up}.", src=SRC)


_PI_TABLE = [   # (integrand text, upper bound text, upper bound value, (c2, c1, c0) meaning c2·π² + c1·π + c0 for the integral from 0)
    ("x·sin x", "π", PI, (0, 1, 0)), ("x·sin x", "π/2", PI / 2, (0, 0, 1)), ("x·sin x", "2π", 2 * PI, (0, -2, 0)),
    ("x·cos x", "π", PI, (0, 0, -2)), ("x·cos x", "π/2", PI / 2, (0, F(1, 2), -1)),
    ("x²·sin x", "π", PI, (1, 0, -4)), ("x²·sin x", "π/2", PI / 2, (0, 1, -2)), ("x²·cos x", "π", PI, (0, -2, 0)),
    ("x²·cos x", "π/2", PI / 2, (F(1, 4), 0, -2)), ("x²·sin x", "2π", 2 * PI, (-4, 0, 0)),
]
_PI_FUN = {"x·sin x": lambda x: x * math.sin(x), "x·cos x": lambda x: x * math.cos(x), "x²·sin x": lambda x: x * x * math.sin(x), "x²·cos x": lambda x: x * x * math.cos(x)}


def pi_expr(c2, c1, c0):
    s = ""
    for cc, body in ((c2, "π²"), (c1, "π")):
        s += coef(cc, body, first=(s == ""))
    if c0:
        s += const_term(c0, first=(s == ""))
    return s or "0"


def pi_val(t):
    return float(t[0]) * PI ** 2 + float(t[1]) * PI + float(t[2])


@gen(C, "l2m-ibp-trig", cap=28, cat="Intégration")
def ibp_trig(rng, d):
    fun, ub, ubv, (c2, c1, c0) = rng.choice(_PI_TABLE)
    k = rng.randint(1, 9)
    c2, c1, c0 = F(c2) * k, F(c1) * k, F(c0) * k
    val = pi_val((c2, c1, c0))
    assert close(simpson(lambda x: k * _PI_FUN[fun](x), 0, ubv, 4000), val, 1e-7)
    right = pi_expr(c2, c1, c0)
    cands = [(-c2, -c1, -c0), (c2, c1, -c0), (c2, -c1, c0), (c2, c1, c0 + k), (c2 * 2, c1, c0), (c2, c1 * 2, c0), (0, c2 + c1, c0), (c2, c1, c0 * 2)]
    wr = [pi_expr(*t) for t in cands if abs(pi_val(t) - val) > 1e-6]
    wr = dedup(wr, right)
    kk = "" if k == 1 else f"{k}·"
    return Draft(f"Que vaut ∫ de 0 à {ub} de {kk}{fun} dx (intégration par parties) ?", right, wr,
                 "On intègre par parties (une fois ou deux selon la puissance de x) ; le résultat est confirmé par intégration numérique.", src=SRC)


@gen(C, "l2m-subst-poly", cap=90, cat="Intégration")
def subst_poly(rng, d):
    m, n, c, b = rng.randint(1, 3), rng.randint(2, 5), rng.randint(1, 9), rng.randint(1, 2)
    val = F((b ** (m + 1) + c) ** (n + 1) - c ** (n + 1), (m + 1) * (n + 1))
    # second method: expand (x^(m+1)+c)^n, multiply by x^m, integrate term by term
    tot = F(0)
    for j in range(n + 1):
        coeff = math.comb(n, j) * c ** (n - j)           # term x^(j(m+1)) * x^m
        e = j * (m + 1) + m + 1
        tot += F(coeff * b ** e, e)
    assert tot == val
    right = num(val)
    wr = [num(F((b ** (m + 1) + c) ** (n + 1) - c ** (n + 1), n + 1)), num(F((b ** (m + 1) + c) ** (n + 1), (m + 1) * (n + 1))), num(F((b ** (m + 1) + c) ** n - c ** n, (m + 1) * n)),
          num(val + 1), num(F((b ** (m + 1) + c) ** (n + 1) - c ** (n + 1), (m + 1) * (n + 1) * 2))]
    xm = "x" if m == 1 else "x" + sup(m)
    return Draft(f"Que vaut ∫ de 0 à {b} de {xm}·(x{sup(m + 1)} + {c}){sup(n)} dx (changement de variable u = x{sup(m + 1)} + {c}) ?", right, wr,
                 f"Avec u = x{sup(m + 1)} + {c}, du = {m + 1}x^{m}dx : l'intégrale vaut [u^{n + 1}/{(m + 1) * (n + 1)}] entre {c} et {b ** (m + 1) + c}.", src=SRC)


@gen(C, "l2m-subst-log", cap=70, cat="Intégration")
def subst_log(rng, d):
    m, k, b, c = rng.randint(1, 3), rng.randint(1, 4), rng.randint(1, 4), rng.randint(1, 6)
    ratio = F(b ** (m + 1) + c, c)
    A = F(k, m + 1)
    val = float(A) * math.log(float(ratio))
    assert close(simpson(lambda x: k * x ** m / (x ** (m + 1) + c), 0, b, 4000), val, 1e-8)
    mk = lambda AA, rr: coef(AA, f"ln({num(rr)})")
    right = mk(A, ratio)
    wr = [mk(F(k), ratio), mk(A, F(b ** (m + 1) + c)), mk(F(k, m), ratio), mk(A, F(b ** (m + 1), c)), mk(A * 2, ratio), mk(F(k, m + 1), F(c, b ** (m + 1) + c))]
    wr = dedup(wr, right)
    xm = "x" if m == 1 else "x" + sup(m)
    kk = "" if k == 1 else f"{k}"
    return Draft(f"Que vaut ∫ de 0 à {b} de {kk}{xm}/(x{sup(m + 1)} + {c}) dx ?", right, wr,
                 f"Avec u = x{sup(m + 1)} + {c}, du = {m + 1}x^{m}dx : on obtient {num(A)}·[ln u] entre {c} et {b ** (m + 1) + c}.", src=SRC)


# ---- séries entières
def _radius_cases():
    """(text of the general term, function n -> |a_n| as Fraction (exact, n >= 1), radius as Fraction / 'inf' / 0)."""
    return None


@gen(C, "l2m-radius", cap=72, cat="Séries entières")
def radius_q(rng, d):
    a = rng.randint(2, 9)
    p = rng.randint(0, 3)
    kind = rng.choice(["a-pow", "a-pow-n", "inv", "inv-n", "sq", "fact-inv", "fact", "geo2", "sqrt"])
    npow = "" if p == 0 else ("n" if p == 1 else "n" + sup(p))
    if kind == "a-pow":      # sum a^n x^n / n^p
        coefn = lambda n: F(a ** n, n ** p)
        term, R = f"{a}ⁿxⁿ" + (f"/{npow}" if p else ""), F(1, a)
    elif kind == "a-pow-n":  # sum n^p x^n / a^n
        coefn = lambda n: F(n ** p, a ** n)
        term, R = (f"{npow}xⁿ/{a}ⁿ" if p else f"xⁿ/{a}ⁿ"), F(a)
    elif kind == "inv":      # sum x^n / (n^p a^n)... with n+1 shift
        coefn = lambda n: F(1, (n + 1) ** p * a ** n)
        term, R = (f"xⁿ/((n + 1){sup(p)}·{a}ⁿ)" if p > 1 else (f"xⁿ/((n + 1)·{a}ⁿ)" if p == 1 else f"xⁿ/{a}ⁿ")), F(a)
    elif kind == "inv-n":    # sum (a x)^n / n!  -> infinite
        coefn = lambda n: F(a ** n, math.factorial(n))
        term, R = f"({a}x)ⁿ/n!", "inf"
    elif kind == "sq":       # sum x^(2n) / a^n  (coefficients of x^(2n)), R = sqrt(a) for a perfect square
        a = rng.choice([4, 9, 16, 25, 36, 49])
        coefn = lambda n: F(1, a ** n)
        term, R = f"x²ⁿ/{a}ⁿ", F(math.isqrt(a))
    elif kind == "fact-inv":  # sum n! x^n
        coefn = lambda n: F(math.factorial(n))
        term, R = "n!·xⁿ", 0
    elif kind == "fact":
        coefn = lambda n: F(n ** n, 1)
        term, R = "nⁿ·xⁿ", 0
    elif kind == "geo2":     # sum (n+a)/(n+1) ... x^n -> R=1
        coefn = lambda n: F(n + a, n + 1)
        term, R = f"(n + {a})xⁿ/(n + 1)", F(1)
    else:                    # sum x^(2n) a^n  -> R = 1/sqrt(a)
        a = rng.choice([4, 9, 16, 25, 36, 49])
        coefn = lambda n: F(a ** n)
        term, R = f"{a}ⁿx²ⁿ", F(1, math.isqrt(a))
    # independent numeric check: ratio test at large n (squared radius for the x^(2n) series)
    if kind in ("sq", "sqrt"):
        r = coefn(400) / coefn(401)
        assert abs(math.sqrt(float(r)) - float(R)) < 1e-9
    elif R == "inf":
        assert coefn(600) / coefn(601) > 50
    elif R == 0:
        assert coefn(60) / coefn(61) < F(1, 20)
    else:
        r = coefn(3000) / coefn(3001)
        assert abs(float(r) - float(R)) < 0.02 * float(R) + 1e-3, (kind, r, R)
    if R == "inf":
        right = "R = +∞"
    elif R == 0:
        right = "R = 0"
    else:
        right = "R = " + num(R)
    cands = []
    if R not in ("inf", 0):
        cands += ["R = " + num(F(1) / R), "R = 1", "R = +∞", "R = 0", "R = " + num(R * R), "R = " + num(R + 1)]
    elif R == "inf":
        cands += ["R = 1", "R = 0", f"R = {a}", f"R = 1/{a}"]
    else:
        cands += ["R = 1", "R = +∞", f"R = {a}", f"R = 1/{a}"]
    wr = dedup(cands, right)
    return Draft(f"Quel est le rayon de convergence R de la série entière Σ {term} (somme sur n ≥ 1) ?", right, wr,
                 "On applique le critère de d'Alembert au rapport des coefficients successifs (puis la racine carrée pour les puissances x²ⁿ).", src=SRC)


@gen(C, "l2m-series-sum", cap=52, cat="Séries entières")
def series_sum(rng, d):
    q = rng.randint(2, 8)
    p = rng.randint(1, q - 1)
    r = F(p, q) * rng.choice([1, -1])
    kind = rng.choice(["n", "n1", "n2", "nn1"])
    one = 1 - r
    if kind == "n":
        val, name, lo = r / one ** 2, "n·rⁿ", 1
        wr = [1 / one ** 2, r / one, 1 / one, r * (1 + r) / one ** 3, r / one ** 3]
    elif kind == "n1":
        val, name, lo = 1 / one ** 2, "(n + 1)·rⁿ", 0
        wr = [r / one ** 2, 1 / one, r / one, 1 / one ** 3, (1 + r) / one ** 2]
    elif kind == "n2":
        val, name, lo = r * (1 + r) / one ** 3, "n²·rⁿ", 1
        wr = [r / one ** 2, (1 + r) / one ** 3, r * (1 + r) / one ** 2, 2 * r / one ** 3, r / one ** 3]
    else:
        val, name, lo = 2 * r ** 2 / one ** 3, "n(n − 1)·rⁿ", 2
        wr = [2 * r / one ** 3, r ** 2 / one ** 3, 2 * r ** 2 / one ** 2, r ** 2 / one ** 2, 2 * r ** 2 / one ** 3 * one]
    f = lambda n: {"n": n * r ** n, "n1": (n + 1) * r ** n, "n2": n * n * r ** n, "nn1": n * (n - 1) * r ** n}[kind]
    tot = 0.0
    for n in range(lo, 1500):
        tot += float(f(n)) if n < 120 else float(r) ** n * {"n": n, "n1": n + 1, "n2": n * n, "nn1": n * (n - 1)}[kind]
    assert abs(tot - float(val)) < 1e-9
    wr = [num(w) for w in wr]
    right = num(val)
    name = name.replace("r", f"({num(r)})")
    return Draft(f"Quelle est la somme de la série Σ {name} (n ≥ {lo}) ?", right, wr,
                 "On dérive terme à terme la série géométrique Σ rⁿ = 1/(1 − r), valable pour |r| < 1.", src=SRC)


def _series_ref(kind, a, kmax):
    """Independent Taylor coefficients by recurrences (exact Fractions)."""
    c = [F(0)] * (kmax + 1)
    if kind == "exp":           # f' = a f
        c[0] = F(1)
        for k in range(kmax):
            c[k + 1] = a * c[k] / (k + 1)
    elif kind == "geo":         # 1/(1-ax)
        for k in range(kmax + 1):
            c[k] = F(a) ** k
    elif kind == "log":         # ln(1+ax): integral of a/(1+ax)
        for k in range(1, kmax + 1):
            c[k] = (-1) ** (k + 1) * F(a) ** k / k
    elif kind == "sin":         # f'' = -a^2 f, f(0)=0, f'(0)=a
        c[1] = F(a)
        for k in range(2, kmax + 1):
            c[k] = -a * a * c[k - 2] / (k * (k - 1))
    elif kind == "cos":
        c[0] = F(1)
        for k in range(2, kmax + 1):
            c[k] = -a * a * c[k - 2] / (k * (k - 1))
    elif kind == "binom":       # (1+x)^alpha, alpha = a (Fraction)
        c[0] = F(1)
        for k in range(kmax):
            c[k + 1] = c[k] * (a - k) / (k + 1)
    return c


@gen(C, "l2m-maclaurin-coeff", cap=90, cat="Séries entières")
def maclaurin_coeff(rng, d):
    kind = rng.choice(["exp", "geo", "log", "sin", "cos", "binom", "inv2"])
    a = rng.randint(2, 6) * rng.choice([1, -1])
    k = rng.randint(2, 7)
    if kind == "exp":
        val, fn = F(a) ** k / math.factorial(k), f"e^({a}x)".replace("^(" + str(a) + "x)", "^(" + num(a) + "x)")
    elif kind == "geo":
        val, fn = F(a) ** k, f"1/(1 − {a}x)" if a > 0 else f"1/(1 + {abs(a)}x)"
    elif kind == "log":
        val, fn = (-1) ** (k + 1) * F(a) ** k / k, f"ln(1 + {a}x)" if a > 0 else f"ln(1 − {abs(a)}x)"
    elif kind == "sin":
        k = rng.choice([3, 5, 7])
        val, fn = (-1) ** ((k - 1) // 2) * F(a) ** k / math.factorial(k), f"sin({a}x)" if a > 0 else f"sin({num(a)}x)"
    elif kind == "cos":
        k = rng.choice([2, 4, 6])
        val, fn = (-1) ** (k // 2) * F(a) ** k / math.factorial(k), f"cos({a}x)" if a > 0 else f"cos({num(a)}x)"
    elif kind == "binom":
        al = F(rng.choice([1, 3, 5, 7, -1, -3]), 2)
        a = al
        val = F(1)
        for j in range(k):
            val = val * (al - j) / (j + 1)
        fn = f"(1 + x)^({num(al)})"
    else:
        val, fn = F(k + 1) * F(a) ** k, f"1/(1 − {a}x)²" if a > 0 else f"1/(1 + {abs(a)}x)²"
    if kind == "inv2":      # derivative of the geometric series
        ref = _series_ref("geo", a, k + 1)
        assert ref[k + 1] * (k + 1) == a * val
    else:
        ref = _series_ref(kind, a, k)
        assert ref[k] == val, (kind, a, k, ref[k], val)
    right = num(val)
    wr = [num(val * 2), num(val * k), num(val / k) if kind not in ("geo",) else num(val + 1), num(F(a) ** k), num(val + 1), num(val * math.factorial(k))]
    if kind == "binom":
        wr = [num(F(1) * al ** k / math.factorial(k)), num(val * 2), num(val + 1), num(al ** k), num(val * k)]
    wr = dedup(wr, right)
    return Draft(f"Quel est le coefficient de x{sup(k)} dans le développement en série entière de {fn} en 0 ?", right, wr,
                 "Le coefficient s'obtient par la formule du développement usuel (ou par la relation de récurrence des coefficients).", src=SRC)


# ---- séries de Fourier
@gen(C, "l2m-fourier-coeff", cap=80, cat="Séries de Fourier")
def fourier_coeff(rng, d):
    kind = rng.choice(["x", "x2", "abs", "x2-a0"])
    k = rng.randint(1, 6)
    n = rng.randint(1, 9)
    kk = "" if k == 1 else str(k)
    conv = "avec aₙ = (1/π)∫ f(x)cos(nx)dx et bₙ = (1/π)∫ f(x)sin(nx)dx sur [−π ; π]"
    sgn = (-1) ** n
    nx = lambda g: simpson(lambda t: g(t), -PI, PI, 20000) / PI
    if kind == "x":
        val = F(2 * k * (-sgn), n)
        got = nx(lambda t: k * t * math.sin(n * t))
        fn, lab = f"{kk}x", f"b{sub_digits(n)}"
        wr = [F(k, n), F(2 * k, n * n), F(4 * k, n), F(k * 2, 1) * (-sgn) / (n * n), F(2 * k, 1) * sgn * n]
        right = num(val)
        text = f"Soit f la fonction 2π-périodique égale à {fn} sur ]−π ; π[ ({conv}). Quelle est la valeur de {lab} ?"
        ex_ = f"Par intégration par parties, bₙ = 2k(−1)^(n+1)/n avec k = {k} et n = {n}."
    elif kind == "x2":
        val = F(4 * k * sgn, n * n)
        got = nx(lambda t: k * t * t * math.cos(n * t))
        fn, lab = f"{kk}x²", f"a{sub_digits(n)}"
        wr = [F(4 * k, n), F(2 * k, n * n), F(8 * k, n * n), F(4 * k, n ** 3), F(k, n * n)]
        right = num(val)
        text = f"Soit f la fonction 2π-périodique égale à {fn} sur ]−π ; π[ ({conv}). Quelle est la valeur de {lab} ?"
        ex_ = f"Deux intégrations par parties donnent aₙ = 4k(−1)ⁿ/n² avec k = {k} et n = {n}."
    elif kind == "x2-a0":
        val = F(2 * k, 3) * 1
        got = nx(lambda t: k * t * t) / 1
        # a0 = (1/pi) * integral = 2 k pi^2 / 3
        right = coef(F(2 * k, 3), "π²")
        got_ok = close(got, 2 * k * PI ** 2 / 3, 1e-8)
        assert got_ok
        wr = [coef(F(k, 3), "π²"), coef(F(4 * k, 3), "π²"), coef(F(2 * k, 3), "π"), coef(F(2 * k), "π²"), coef(F(k, 2), "π²")]
        fn = f"{kk}x²"
        text = f"Soit f la fonction 2π-périodique égale à {fn} sur ]−π ; π[ (avec a₀ = (1/π)∫ f(x)dx sur [−π ; π]). Quelle est la valeur de a₀ ?"
        ex_ = f"a₀ = (1/π)·{k}·[x³/3] de −π à π = {k}·2π²/3."
        return Draft(text, right, dedup(wr, right), ex_, src=SRC)
    else:
        got = nx(lambda t: k * abs(t) * math.cos(n * t))
        fn, lab = f"{kk}|x|", f"a{sub_digits(n)}"
        if n % 2 == 0:
            right = "0"
            assert abs(got) < 1e-7
            wr = ["4/π", f"{k}/π", f"2/(π·{n})".replace("2/(π·", "2/(π·"), f"{4 * k}/(π{sup(n * n)})" if False else f"{2 * k}/π"]
            wr = [f"{4 * k}/(π·{n * n})", f"{2 * k}/π", f"{k}/(π·{n})", f"{2 * k}/(π·{n * n})"]
        else:
            c = F(-4 * k, n * n)
            assert close(got, float(c) / PI, 1e-6)
            den = f"{c.denominator}π" if c.denominator != 1 else "π"
            right = f"{c.numerator}/({den})".replace(f"{c.numerator}/(π)", f"{c.numerator}/π")
            right = right.replace("-", MINUS)
            cc = lambda numr, dn: (f"{numr}/({dn}π)" if dn != 1 else f"{numr}/π")
            wr = [cc(-2 * k, n * n), cc(-4 * k, n), "0", cc(-8 * k, n * n), cc(-k, n * n)]
            wr = [w.replace("-", MINUS) for w in wr]
        text = f"Soit f la fonction 2π-périodique égale à {fn} sur ]−π ; π[ ({conv}). Quelle est la valeur de {lab} ?"
        ex_ = "aₙ = (2k/(πn²))((−1)ⁿ − 1) : nul si n est pair, égal à −4k/(πn²) si n est impair."
        return Draft(text, right, dedup(wr, right), ex_, src=SRC)
    assert close(got, float(val), 1e-6)
    wr = [num(w) for w in wr]
    return Draft(text, right, dedup(wr, right), ex_, src=SRC)


# ---- suites de fonctions : limite simple
@gen(C, "l2m-fseq-pointwise", cap=38, cat="Suites de fonctions")
def fseq_pointwise(rng, d):
    kind = rng.choice(["xn", "xn-ratio", "euler", "nx", "exp", "sq", "frac", "nxe"])
    N = 3000 if kind in ("xn", "xn-ratio") else 10 ** 6
    if kind == "xn":
        x = rng.choice([F(0), F(1, 2), F(1, 3), F(2, 3), F(3, 4), F(1), F(1, 5), F(4, 5), F(9, 10)])
        text, lim = f"fₙ(x) = xⁿ sur [0 ; 1] ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?", "1" if x == 1 else "0"
        approx = float(x ** N)
        target = float(lim)
    elif kind == "xn-ratio":
        x = rng.choice([F(1, 2), F(1), F(2), F(3), F(3, 2), F(1, 3), F(5, 2), F(4)])
        lim = "0" if x < 1 else ("1/2" if x == 1 else "1")
        v = x ** N
        approx = float(v / (1 + v)) if x <= 1 else float(1 / (1 + F(1) / v))
        target = float(F(lim))
        text = f"fₙ(x) = xⁿ/(1 + xⁿ) pour x ≥ 0 ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    elif kind == "euler":
        x = rng.choice([-3, -2, -1, 1, 2, 3, 4, 5])
        lim = ex(x)
        M_ = 10 ** 7
        approx = (1 + x / M_) ** M_
        target = math.exp(x)
        text = f"fₙ(x) = (1 + x/n)ⁿ ; quelle est la limite de fₙ({x}) quand n tend vers +∞ ?"
    elif kind == "nx":
        x = rng.choice([F(0), F(1), F(2), F(1, 2), F(3), F(1, 3), F(5), F(7, 2)])
        lim = "0" if x == 0 else "1"
        approx = float(N * x / (1 + N * x))
        target = float(lim)
        text = f"fₙ(x) = nx/(1 + nx) pour x ≥ 0 ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    elif kind == "exp":
        x = rng.choice([F(0), F(1), F(2), F(1, 2), F(3), F(1, 3), F(5), F(7, 2)])
        lim = "1" if x == 0 else "0"
        approx = math.exp(-N * float(x)) if x else 1.0
        target = float(lim)
        text = f"fₙ(x) = e^(−nx) pour x ≥ 0 ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    elif kind == "sq":
        x = rng.choice([F(1), F(2), F(3), F(1, 2), F(5), F(3, 2), F(4), F(7, 3)])
        lim = num(x * x)
        approx = float((N * x * x + 1) / N)
        target = float(x * x)
        text = f"fₙ(x) = (nx² + 1)/n ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    elif kind == "frac":
        x = rng.choice([F(1), F(2), F(3), F(1, 2), F(5), F(3, 2), F(4), F(7, 3)])
        lim = num(x)
        approx = float(N * x / (N + x * x))
        target = float(x)
        text = f"fₙ(x) = nx/(n + x²) ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    else:
        x = rng.choice([F(0), F(1), F(2), F(1, 2), F(3), F(1, 3), F(5), F(7, 2)])
        lim = "0"
        approx = N * float(x) * math.exp(-N * float(x))
        target = 0.0
        text = f"fₙ(x) = nx·e^(−nx) pour x ≥ 0 ; quelle est la limite de fₙ({num(x)}) quand n tend vers +∞ ?"
    assert abs(approx - target) < 5e-3 * max(1.0, abs(target)), (kind, x, approx, target)
    pool = ["0", "1", "1/2", "+∞", "la suite diverge", num(x) if lim != num(x) else "2", ex(x) if isinstance(x, int) or x.denominator == 1 else "e"]
    pool += [num(x * x) if kind != "sq" else num(x)]
    wr = dedup(pool, lim)
    return Draft(text, lim, wr, "On fixe x et on étudie la suite numérique (fₙ(x)) : sa limite est la valeur de la limite simple en ce point.", src=SRC)


# ---- intégrales doubles
def _ups_mul(a, b):
    out = {}
    for i, x in a.items():
        for j, y in b.items():
            out[i + j] = out.get(i + j, F(0)) + x * y
    return out


def _ups_pow(a, n):
    r = {0: F(1)}
    for _ in range(n):
        r = _ups_mul(r, a)
    return r


def _ups_int(a, lo, hi):
    return sum(c * (F(hi) ** (e + 1) - F(lo) ** (e + 1)) / (e + 1) for e, c in a.items())


def _nested(terms, outer_swap, lo_inner, hi_inner, lo_out, hi_out):
    """Integral of sum c x^p y^q. Inner variable limits are univariate polynomials (dicts) in the outer variable.
    outer_swap False: outer = x, inner = y. True: outer = y, inner = x."""
    total = {}
    for c, p, q in terms:
        eo, ei = (q, p) if outer_swap else (p, q)
        inner = {}
        for e, v in _ups_pow(hi_inner, ei + 1).items():
            inner[e] = inner.get(e, F(0)) + v
        for e, v in _ups_pow(lo_inner, ei + 1).items():
            inner[e] = inner.get(e, F(0)) - v
        inner = {e: v * c / (ei + 1) for e, v in inner.items()}
        shifted = {e + eo: v for e, v in inner.items()}
        for e, v in shifted.items():
            total[e] = total.get(e, F(0)) + v
    return _ups_int(total, lo_out, hi_out)


@gen(C, "l2m-double-rect", cap=110, cat="Intégrales doubles")
def double_rect(rng, d):
    nt = 1 if d == 1 else rng.choice([2, 2, 3])
    terms = rand_terms(rng, nt, maxdeg=2, cmax=5)
    terms = [(c, p, q) for c, p, q in terms if p <= 3 and q <= 3]
    a = rng.randint(0, 2)
    b = a + rng.randint(1, 3)
    c0 = rng.randint(0, 2)
    dd = c0 + rng.randint(1, 3)
    val = sum(c * F(b ** (p + 1) - a ** (p + 1), p + 1) * F(dd ** (q + 1) - c0 ** (q + 1), q + 1) for c, p, q in terms)
    # second method: Fubini, integrate in y first through the nested helper then in x; and numeric nested Simpson (exact up to degree 3)
    alt = _nested(terms, False, {0: F(c0)}, {0: F(dd)}, a, b)
    alt2 = _nested(terms, True, {0: F(a)}, {0: F(b)}, c0, dd)
    assert val == alt == alt2
    num_val = simpson(lambda x: simpson(lambda y: float(ev2(terms, x, y)), c0, dd, 8), a, b, 8)
    assert close(num_val, float(val), 1e-9)
    nolow = sum(c * F(b ** (p + 1), p + 1) * F(dd ** (q + 1), q + 1) for c, p, q in terms)
    xonly = sum(c * F(b ** (p + 1) - a ** (p + 1), p + 1) * F(dd - c0) * (1 if q == 0 else 0) for c, p, q in terms)
    wr = [nolow, sum(c * F(b ** (p + 1) - a ** (p + 1), p + 1) * F(dd ** (q + 1) - c0 ** (q + 1), q + 1) * (p + 1) for c, p, q in terms),
          val * 2, val + 1, val - 1, sum(c * F(b ** (p + 1) - a ** (p + 1)) * F(dd ** (q + 1) - c0 ** (q + 1)) for c, p, q in terms)]
    wr = [num(w) for w in wr]
    return Draft(f"Que vaut l'intégrale double de {poly2(terms)} sur le rectangle [{a} ; {b}] × [{c0} ; {dd}] ?", num(val), dedup(wr, num(val)),
                 "Les variables sont séparables dans chaque monôme : on intègre en x puis en y (théorème de Fubini).", src=SRC)


@gen(C, "l2m-double-triangle", cap=90, cat="Intégrales doubles")
def double_triangle(rng, d):
    region = rng.choice(["tri1", "tri2", "tri3"])
    b = rng.randint(1, 4)
    k = rng.randint(1, 3)
    nt = rng.choice([1, 1, 2])
    terms = [(c, p, q) for c, p, q in rand_terms(rng, nt, maxdeg=2, cmax=4) if p + q <= 3]
    if not terms:
        return None
    X = {1: F(1)}
    zero = {0: F(0)}
    if region == "tri1":      # 0 <= y <= x <= b
        A = _nested(terms, False, zero, X, 0, b)
        B = _nested(terms, True, X, {0: F(b)}, 0, b)
        desc = f"le triangle de sommets (0 ; 0), ({b} ; 0) et ({b} ; {b})"
    elif region == "tri2":    # x, y >= 0, x + y <= b
        A = _nested(terms, False, zero, {0: F(b), 1: F(-1)}, 0, b)
        B = _nested(terms, True, zero, {0: F(b), 1: F(-1)}, 0, b)
        desc = f"le triangle de sommets (0 ; 0), ({b} ; 0) et (0 ; {b})"
    else:                     # 0 <= x <= b, 0 <= y <= k x
        A = _nested(terms, False, zero, {1: F(k)}, 0, b)
        B = _nested(terms, True, {1: F(1, k)}, {0: F(b)}, 0, k * b)
        desc = f"le triangle de sommets (0 ; 0), ({b} ; 0) et ({b} ; {k * b})"
    assert A == B
    val = A
    wr = [val * 2, val / 2, val + 1, val * 3 if region != "tri2" else val * (b + 1), val - F(1, 2) if val > F(1, 2) else val + F(1, 3)]
    # a classic slip: integrating over the whole square [0, b]^2 (or its bounding rectangle)
    sq = sum(c * F(b ** (p + 1), p + 1) * F((k * b if region == "tri3" else b) ** (q + 1), q + 1) for c, p, q in terms)
    wr.append(sq)
    return Draft(f"Que vaut l'intégrale double de {poly2(terms)} sur {desc} ?", num(val), dedup([num(w) for w in wr], num(val)),
                 "On décrit le triangle par des bornes en escalier (y entre deux fonctions de x) puis on intègre deux fois ; l'ordre inverse donne le même résultat.", src=SRC)


@gen(C, "l2m-double-polar", cap=80, cat="Intégrales doubles")
def double_polar(rng, d):
    kind = rng.choice([0, 1, 2, 3, 4])
    R = rng.randint(1, 4)
    thetas = [(F(2), "le disque entier"), (F(1), "le demi-disque y ≥ 0"), (F(1, 2), "le quart de disque x ≥ 0, y ≥ 0"), (F(1, 3), "le secteur 0 ≤ θ ≤ π/3"),
              (F(1, 4), "le secteur 0 ≤ θ ≤ π/4"), (F(2, 3), "le secteur 0 ≤ θ ≤ 2π/3"), (F(3, 2), "le secteur 0 ≤ θ ≤ 3π/2"), (F(1, 6), "le secteur 0 ≤ θ ≤ π/6")]
    th, regtxt = rng.choice(thetas)
    fun = {0: "1", 1: "√(x² + y²)", 2: "x² + y²", 3: "(x² + y²)^(3/2)", 4: "(x² + y²)²"}[kind]
    val = th * F(R ** (kind + 2), kind + 2)          # coefficient of pi
    # numerical check by a Cartesian midpoint grid with an indicator of the region
    n = 160
    h = 2.0 * R / n
    s = 0.0
    for i in range(n):
        x = -R + (i + 0.5) * h
        for j in range(n):
            y = -R + (j + 0.5) * h
            r2 = x * x + y * y
            if r2 <= R * R:
                ang = math.atan2(y, x)
                if ang < 0:
                    ang += 2 * PI
                if ang <= float(th) * PI:
                    s += r2 ** (kind / 2.0)
    s *= h * h
    assert abs(s - float(val) * PI) < 0.05 * float(val) * PI, (s, float(val) * PI)
    right = coef(val, "π")
    wr = [coef(th * F(R ** (kind + 1), kind + 1), "π"), coef(val * 2, "π"), coef(th * F(R ** (kind + 2), 2), "π") if kind != 0 else coef(val + 1, "π"),
          coef(val / 2, "π"), coef(th * F(R ** (kind + 2)), "π") if kind else coef(val * 3, "π"), coef(th * F(R ** kind, kind + 2), "π")]
    wr = [w for w in dedup(wr, right) if w]
    return Draft(f"Que vaut l'intégrale double de {fun} sur {regtxt} de centre O et de rayon {R} (passage en coordonnées polaires) ?" if th not in (F(2),) else f"Que vaut l'intégrale double de {fun} sur le disque de centre O et de rayon {R} (passage en coordonnées polaires) ?",
                 right, wr, "En polaires dA = r dr dθ : on intègre r^(k+1) pour r de 0 à R, puis on multiplie par l'angle balayé.", src=SRC)


@gen(C, "l2m-area-between", cap=60, cat="Intégrales doubles")
def area_between(rng, d):
    kind = rng.choice(["parab-line", "powers", "lines", "under-parab"])
    if kind == "parab-line":
        k, m = rng.randint(1, 4), rng.randint(1, 8)
        x1 = F(m, k)
        val = F(m ** 3, 6 * k * k)
        fnum = float(x1)
        got = simpson(lambda x: m * x - k * x * x, 0, fnum, 2000)
        text = f"Quelle est l'aire du domaine délimité par la parabole y = {'' if k == 1 else k}x² et la droite y = {'' if m == 1 else m}x ?"
        wr = [F(m ** 3, 3 * k * k), F(m ** 3, 2 * k * k), F(m ** 2, 6 * k), F(m ** 3, 6 * k), F(m ** 3, 12 * k * k)]
    elif kind == "powers":
        p, q = rng.randint(1, 5), rng.randint(2, 7)
        if q <= p:
            return None
        val = F(1, p + 1) - F(1, q + 1)
        got = simpson(lambda x: x ** p - x ** q, 0, 1, 2000)
        text = f"Quelle est l'aire du domaine compris entre les courbes y = x{sup(p) if p > 1 else ''} et y = x{sup(q)} pour 0 ≤ x ≤ 1 ?"
        wr = [F(1, q + 1) - F(1, p + 1) + F(1, 2), F(1, p) - F(1, q), F(1, (p + 1) * (q + 1)), F(q - p, p + q + 2), F(1, p + 1)]
    elif kind == "lines":
        a, b, c = rng.randint(0, 3), rng.randint(4, 7), rng.randint(1, 6)
        val = F((b - a) * c * c, 2)
        got = simpson(lambda x: b * x - a * x, 0, c, 2000)
        text = f"Quelle est l'aire du domaine compris entre les droites y = {'' if a == 1 else a}x et y = {b}x pour 0 ≤ x ≤ {c} ?" if a else f"Quelle est l'aire du triangle délimité par l'axe des x, la droite y = {b}x et la droite x = {c} ?"
        wr = [F((b - a) * c, 2), F((b - a) * c * c), F((b + a) * c * c, 2), F((b - a) * c ** 3, 3), F((b - a) * c * c, 4)]
    else:
        k, c = rng.randint(1, 5), rng.randint(1, 6)
        val = F(k * c ** 3, 3)
        got = simpson(lambda x: k * x * x, 0, c, 2000)
        text = f"Quelle est l'aire du domaine situé sous la parabole y = {'' if k == 1 else k}x², au-dessus de l'axe des x, pour 0 ≤ x ≤ {c} ?"
        wr = [F(k * c ** 2, 2), F(k * c ** 3), F(k * c ** 3, 2), F(k * c ** 2, 3), F(k * c ** 4, 4)]
    assert close(got, float(val), 1e-7)
    return Draft(text, num(val), dedup([num(w) for w in wr], num(val)), "L'aire est l'intégrale de la hauteur entre les deux courbes, calculée entre leurs abscisses d'intersection.", src=SRC)


# ======================================================================================================== CALCUL DIFFÉRENTIEL
def _fpoly(terms):
    return lambda x, y: ev2(terms, x, y)


@gen(C, "l2m-partial-poly", cap=100, cat="Dérivées partielles")
def partial_poly(rng, d):
    terms = rand_terms(rng, rng.choice([2, 3, 3, 4]), maxdeg=3, cmax=5)
    terms = [(c, p, q) for c, p, q in terms if p + q <= 4]
    if len(terms) < 2:
        return None
    a, b = rng.randint(-3, 3), rng.randint(-3, 3)
    f = _fpoly(terms)
    kind = rng.choice(["x", "y", "xx", "xy", "yy"])
    if kind == "x":
        val = ev2(pdx(terms), a, b)
        chk = d1(lambda t: f(t, F(b)), F(a))
        sym, expl = "∂f/∂x", "On dérive par rapport à x en gardant y constant."
        others = [ev2(pdy(terms), a, b), ev2(pdx(terms), b, a), f(a, b), ev2(pdx(pdx(terms)), a, b)]
    elif kind == "y":
        val = ev2(pdy(terms), a, b)
        chk = d1(lambda t: f(F(a), t), F(b))
        sym, expl = "∂f/∂y", "On dérive par rapport à y en gardant x constant."
        others = [ev2(pdx(terms), a, b), ev2(pdy(terms), b, a), f(a, b), ev2(pdy(pdy(terms)), a, b)]
    elif kind == "xx":
        val = ev2(pdx(pdx(terms)), a, b)
        chk = d2(lambda t: f(t, F(b)), F(a))
        sym, expl = "∂²f/∂x²", "On dérive deux fois par rapport à x."
        others = [ev2(pdx(terms), a, b), ev2(pdy(pdy(terms)), a, b), ev2(pdy(pdx(terms)), a, b), ev2(pdx(pdx(terms)), b, a)]
    elif kind == "yy":
        val = ev2(pdy(pdy(terms)), a, b)
        chk = d2(lambda t: f(F(a), t), F(b))
        sym, expl = "∂²f/∂y²", "On dérive deux fois par rapport à y."
        others = [ev2(pdy(terms), a, b), ev2(pdx(pdx(terms)), a, b), ev2(pdy(pdx(terms)), a, b), ev2(pdy(pdy(terms)), b, a)]
    else:
        val = ev2(pdy(pdx(terms)), a, b)
        chk = d1(lambda s: d1(lambda t: f(s, t), F(b)), F(a))
        assert val == ev2(pdx(pdy(terms)), a, b)          # Schwarz
        sym, expl = "∂²f/∂x∂y", "On dérive en x puis en y (ou l'inverse : théorème de Schwarz)."
        others = [ev2(pdx(pdx(terms)), a, b), ev2(pdy(pdy(terms)), a, b), ev2(pdx(terms), a, b) * ev2(pdy(terms), a, b), ev2(pdy(pdx(terms)), b, a)]
    assert F(val) == chk, (kind, val, chk)
    wr = [num(F(o)) for o in others] + [num(F(val) + 1), num(F(val) * 2)]
    return Draft(f"Soit f(x, y) = {poly2(terms)}. Quelle est la valeur de {sym} au point ({a} ; {b}) ?".replace("(" + str(a) + " ;", "(" + num(a) + " ;").replace("; " + str(b) + ")", "; " + num(b) + ")"),
                 num(F(val)), dedup(wr, num(F(val))), expl, src=SRC)


@gen(C, "l2m-partial-trans", cap=75, cat="Dérivées partielles")
def partial_trans(rng, d):
    kind = rng.choice(["xmey", "lnsq", "sqrt", "xmln", "exy", "quot", "xy-y"])
    h = 1e-6
    if kind == "xmey":
        m, k, a, b = rng.randint(1, 3), nz(rng, -3, 3), rng.randint(1, 3), rng.randint(-2, 2)
        fn = lambda x, y: x ** m * math.exp(k * y)
        fexpr = f"x{sup(m) if m > 1 else ''}·{exx(k, 'y')}"
        target = "∂f/∂y"
        c = F(k * a ** m)
        right = coef(c, ex(k * b)) if c != 0 else "0"
        val = float(c) * math.exp(k * b)
        wr = [coef(F(a ** m), ex(k * b)), coef(F(m * k * a ** m), ex(k * b)), coef(c, ex(k * b + 1)), coef(F(k * m * a ** (m - 1)), ex(k * b)), coef(c, ex(k)), coef(F(k * a ** m), ex(b))]
        got = (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    elif kind == "lnsq":
        a, b = rng.randint(-4, 4), rng.randint(-4, 4)
        if a == 0 and b == 0:
            return None
        fn = lambda x, y: math.log(x * x + y * y)
        fexpr, target = "ln(x² + y²)", "∂f/∂x"
        v = F(2 * a, a * a + b * b)
        right, val = num(v), float(v)
        wr = [num(F(a, a * a + b * b)), num(F(2 * b, a * a + b * b)), num(F(2, a * a + b * b)), num(F(2 * a, a + b)) if a + b else "0", num(F(1, a * a + b * b))]
        got = (fn(a + h, b) - fn(a - h, b)) / (2 * h)
        pt = (a, b)
    elif kind == "sqrt":
        a, b, c = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (6, 8, 10), (4, 3, 5), (12, 5, 13), (9, 12, 15), (7, 24, 25), (20, 21, 29)])
        a *= rng.choice([1, -1])
        fn = lambda x, y: math.sqrt(x * x + y * y)
        fexpr, target = "√(x² + y²)", rng.choice(["∂f/∂x", "∂f/∂y"])
        v = F(a, c) if target == "∂f/∂x" else F(b, c)
        right, val = num(v), float(v)
        wr = [num(F(b, c)) if target == "∂f/∂x" else num(F(a, c)), num(F(c, a) if a else F(1)), num(F(a + b, c)), num(F(1, c)), num(F(a, c * c)), num(F(1, 2))]
        got = (fn(a + h, b) - fn(a - h, b)) / (2 * h) if target == "∂f/∂x" else (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    elif kind == "xmln":
        m, a, b = rng.randint(1, 4), rng.randint(-3, 4), rng.randint(1, 6)
        fn = lambda x, y: x ** m * math.log(y)
        fexpr, target = f"x{sup(m) if m > 1 else ''}·ln y", "∂f/∂y"
        v = F(a ** m, b)
        right, val = num(v), float(v)
        wr = [num(F(m * a ** (m - 1), b)), num(F(a ** m)), num(F(a ** m, b * b)), num(F(a ** m * b)), num(F(m * a ** m, b))]
        got = (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    elif kind == "exy":
        a, b = rng.randint(-2, 3), rng.randint(-2, 3)
        fn = lambda x, y: math.exp(x * y)
        fexpr, target = "e^(xy)", rng.choice(["∂f/∂x", "∂f/∂y"])
        coefv = b if target == "∂f/∂x" else a
        right = coef(F(coefv), ex(a * b)) if coefv else "0"
        val = coefv * math.exp(a * b)
        wr = [coef(F(a if target == "∂f/∂x" else b), ex(a * b)), coef(F(a * b), ex(a * b)), ex(a * b), coef(F(coefv), ex(a + b)), coef(F(coefv), ex(coefv))]
        got = (fn(a + h, b) - fn(a - h, b)) / (2 * h) if target == "∂f/∂x" else (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    elif kind == "quot":
        a, b = rng.randint(1, 6), rng.randint(1, 6)
        fn = lambda x, y: x * y / (x + y)
        fexpr, target = "xy/(x + y)", rng.choice(["∂f/∂x", "∂f/∂y"])
        v = F(b * b, (a + b) ** 2) if target == "∂f/∂x" else F(a * a, (a + b) ** 2)
        right, val = num(v), float(v)
        wr = [num(F(a * a, (a + b) ** 2)) if target == "∂f/∂x" else num(F(b * b, (a + b) ** 2)), num(F(b, a + b)), num(F(b * b, a + b)), num(F(a * b, (a + b) ** 2)), num(F(1, (a + b) ** 2))]
        got = (fn(a + h, b) - fn(a - h, b)) / (2 * h) if target == "∂f/∂x" else (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    else:
        a, b = rng.randint(-3, 3), nz(rng, -4, 4)
        fn = lambda x, y: x / y
        fexpr, target = "x/y", "∂f/∂y"
        v = F(-a, b * b)
        right, val = num(v), float(v)
        wr = [num(F(1, b)), num(F(a, b * b)), num(F(-1, b)), num(F(-a, b)), num(F(a, b))]
        got = (fn(a, b + h) - fn(a, b - h)) / (2 * h)
        pt = (a, b)
    assert abs(got - val) < 1e-5 * max(1.0, abs(val)), (kind, got, val)
    wr = [w for w in dedup(wr, right) if w]
    return Draft(f"Soit f(x, y) = {fexpr}. Quelle est la valeur de {target} au point ({num(pt[0])} ; {num(pt[1])}) ?", right, wr,
                 "On dérive par rapport à la variable indiquée en traitant l'autre comme une constante, puis on évalue au point.", src=SRC)


def _grad_terms(rng, nv):
    names = "xyz"[:nv]
    k = rng.choice([2, 3, 3]) + (nv - 2)
    terms = []
    seen = set()
    while len(terms) < k:
        ex_ = tuple(rng.randint(0, 3) for _ in range(nv))
        if sum(ex_) == 0 or sum(ex_) > 4 or ex_ in seen:
            continue
        seen.add(ex_)
        terms.append((nz(rng, -5, 5), ex_))
    return names, terms


def _pn_text(terms, names):
    out = ""
    for i, (c, ex_) in enumerate(terms):
        body = "".join(n + (sup(e) if e > 1 else "") for n, e in zip(names, ex_) if e)
        a = abs(c)
        t = ("" if a == 1 else str(a)) + body
        out += (MINUS if c < 0 else "") + t if i == 0 else (" − " if c < 0 else " + ") + t
    return out


def _pn_ev(terms, pt):
    return sum(c * math.prod(F(p) ** e for p, e in zip(pt, ex_)) for c, ex_ in terms)


def _pn_d(terms, i):
    return [(c * ex_[i], ex_[:i] + (ex_[i] - 1,) + ex_[i + 1:]) for c, ex_ in terms if ex_[i]]


@gen(C, "l2m-gradient", cap=90, cat="Calcul différentiel")
def gradient_q(rng, d):
    nv = 2 if d <= 3 else rng.choice([2, 3])
    names, terms = _grad_terms(rng, nv)
    pt = tuple(rng.randint(-2, 3) for _ in range(nv))
    grad = tuple(_pn_ev(_pn_d(terms, i), pt) for i in range(nv))
    # second method: 5-point finite differences along each axis (exact for polynomials of degree <= 4)
    for i in range(nv):
        def line(t, i=i):
            p = [F(v) for v in pt]
            p[i] = t
            return _pn_ev(terms, p)
        assert d1(line, F(pt[i])) == grad[i]
    wr = [tuple(reversed(grad)), tuple(_pn_ev(_pn_d(terms, i), tuple(reversed(pt))) for i in range(nv)), tuple(g + 1 for g in grad), tuple(F(g) * 2 for g in grad),
          tuple(_pn_ev(terms, pt) for _ in range(nv)), tuple(g * p for g, p in zip(grad, pt))]
    right = V(grad)
    wr = dedup([V(w) for w in wr], right)
    return Draft(f"Quel est le gradient de f({', '.join(names)}) = {_pn_text(terms, names)} au point {V(pt)} ?", right, wr,
                 "Le gradient est le vecteur des dérivées partielles, évaluées au point.", src=SRC)


@gen(C, "l2m-directional", cap=70, cat="Calcul différentiel")
def directional(rng, d):
    terms = [(c, p, q) for c, p, q in rand_terms(rng, rng.choice([2, 3]), maxdeg=3, cmax=4) if p + q <= 4]
    if len(terms) < 2:
        return None
    a, b = rng.randint(-2, 3), rng.randint(-2, 3)
    ux, uy, nn = rng.choice([(3, 4, 5), (4, 3, 5), (-3, 4, 5), (5, 12, 13), (12, 5, 13), (0, 1, 1), (1, 0, 1), (8, 15, 17), (-5, 12, 13), (3, -4, 5), (-4, -3, 5)])
    u = (F(ux, nn), F(uy, nn))
    gx, gy = ev2(pdx(terms), a, b), ev2(pdy(terms), a, b)
    val = gx * u[0] + gy * u[1]
    f = _fpoly(terms)
    chk = d1(lambda t: f(F(a) + t * u[0], F(b) + t * u[1]), F(0))
    assert val == chk
    unnorm = gx * ux + gy * uy
    wr = [unnorm, gx * u[1] + gy * u[0], gx + gy, val + 1, F(math.isqrt(int(gx * gx + gy * gy))) if False else gx * u[0] - gy * u[1]]
    right = num(val)
    return Draft(f"Soit f(x, y) = {poly2(terms)} et u = ({num(F(ux, nn))} ; {num(F(uy, nn))}), vecteur unitaire. Quelle est la dérivée directionnelle de f en ({num(a)} ; {num(b)}) suivant u ?",
                 right, dedup([num(w) for w in wr], right), "D_u f = ∇f · u : on calcule le gradient au point puis le produit scalaire avec u (unitaire).", src=SRC)


@gen(C, "l2m-chain-rule", cap=70, cat="Calcul différentiel")
def chain_rule(rng, d):
    terms = [(c, p, q) for c, p, q in rand_terms(rng, rng.choice([2, 3]), maxdeg=2, cmax=4) if p + q <= 3]
    if len(terms) < 2:
        return None
    xs = [rng.randint(-2, 2), nz(rng, -3, 3), rng.randint(-1, 1)]       # x(t) = c0 + c1 t + c2 t^2 stored low->high
    ys = [rng.randint(-2, 2), rng.randint(-3, 3), nz(rng, -2, 2)]
    t0 = rng.randint(-2, 2)
    X = lambda t: xs[0] + xs[1] * t + xs[2] * t * t
    Y = lambda t: ys[0] + ys[1] * t + ys[2] * t * t
    dX = lambda t: xs[1] + 2 * xs[2] * t
    dY = lambda t: ys[1] + 2 * ys[2] * t
    x0, y0 = X(t0), Y(t0)
    val = ev2(pdx(terms), x0, y0) * dX(t0) + ev2(pdy(terms), x0, y0) * dY(t0)
    g = lambda t: _fpoly(terms)(X(t), Y(t))
    assert val == d1(lambda t: g(F(t)), F(t0), F(1, 5)) or True
    # exact check: g is a polynomial of degree <= 6 in t; use a 7-point exact derivative via Lagrange differentiation
    pts = [F(t0) + k for k in range(-3, 4)]
    vals = [g(p) for p in pts]
    der = F(0)
    for i, pi in enumerate(pts):                    # derivative at t0 of the interpolating polynomial (exact for degree <= 6)
        li = F(0)
        for j in range(len(pts)):
            if j == i:
                continue
            prod = F(1) / (pi - pts[j])
            for k in range(len(pts)):
                if k not in (i, j):
                    prod *= (F(t0) - pts[k]) / (pi - pts[k])
            li += prod
        der += vals[i] * li
    assert der == val, (der, val)
    wr = [ev2(pdx(terms), x0, y0) * dX(t0), ev2(pdy(terms), x0, y0) * dY(t0), ev2(pdx(terms), x0, y0) + ev2(pdy(terms), x0, y0), _fpoly(terms)(x0, y0),
          ev2(pdx(terms), x0, y0) * dY(t0) + ev2(pdy(terms), x0, y0) * dX(t0)]
    right = num(F(val))
    xt = poly([xs[2], xs[1], xs[0]], "t")
    yt = poly([ys[2], ys[1], ys[0]], "t")
    return Draft(f"Soit f(x, y) = {poly2(terms)}, avec x(t) = {xt} et y(t) = {yt}. Quelle est la valeur de d/dt [f(x(t), y(t))] en t = {t0} ?".replace(f"en t = {t0} ?", f"en t = {num(t0)} ?"),
                 right, dedup([num(F(w)) for w in wr], right), "Règle de la chaîne : (f∘γ)'(t) = ∂f/∂x·x'(t) + ∂f/∂y·y'(t), évalué en t.", src=SRC)


@gen(C, "l2m-tangent-plane", cap=70, cat="Calcul différentiel")
def tangent_plane(rng, d):
    terms = [(c, p, q) for c, p, q in rand_terms(rng, rng.choice([2, 3]), maxdeg=2, cmax=4) if p + q <= 3]
    if len(terms) < 2:
        return None
    a, b = rng.randint(-2, 3), rng.randint(-2, 3)
    dx, dy = F(rng.choice([-2, -1, 1, 2, 3]), 10), F(rng.choice([-2, -1, 1, 2, 3]), 10)
    f = _fpoly(terms)
    fx, fy = ev2(pdx(terms), a, b), ev2(pdy(terms), a, b)
    assert fx == d1(lambda t: f(t, F(b)), F(a)) and fy == d1(lambda t: f(F(a), t), F(b))
    L = f(a, b) + fx * dx + fy * dy
    exact = f(a + dx, b + dy)
    if L == exact:
        return None
    wr = [exact, f(a, b), f(a, b) + fy * dx + fx * dy, f(a, b) + fx + fy, L + dx, f(a, b) + fx * dx]
    right = dec(L)
    return Draft(f"Soit f(x, y) = {poly2(terms)}. Au voisinage de ({num(a)} ; {num(b)}), quelle valeur donne l'approximation par le plan tangent pour le point ({dec(F(a) + dx)} ; {dec(F(b) + dy)}) ?",
                 right, dedup([dec(w) for w in wr], right), "Plan tangent : f(a, b) + ∂f/∂x·(x − a) + ∂f/∂y·(y − b), évalué au point voisin (ce n'est pas la valeur exacte de f).", src=SRC)


@gen(C, "l2m-critical-quadratic", cap=90, cat="Calcul différentiel")
def critical_quadratic(rng, d):
    a, b, c = nz(rng, -4, 4), nz(rng, -4, 4), rng.randint(-4, 4)
    dd, e = rng.randint(-8, 8), rng.randint(-8, 8)
    det = 4 * a * b - c * c
    if det == 0 or (dd == 0 and e == 0):
        return None
    # grad = (2a x + c y + dd, c x + 2b y + e) = 0
    x0 = F(-dd * 2 * b + c * e, det)
    y0 = F(-2 * a * e + c * dd, det)
    assert 2 * a * x0 + c * y0 + dd == 0 and c * x0 + 2 * b * y0 + e == 0
    terms = [(a, 2, 0), (b, 0, 2), (c, 1, 1), (dd, 1, 0), (e, 0, 1)]
    terms = [t for t in terms if t[0] != 0]
    right = V((x0, y0))
    wr = [V((F(-dd, 2 * a), F(-e, 2 * b))), V((y0, x0)), V((x0 + 1, y0)), V((F(dd, 2 * a), F(e, 2 * b))), V((x0, y0 + 1)), V((-x0, y0))]
    return Draft(f"Quel est l'unique point critique de f(x, y) = {poly2(terms)} ?", right, dedup([w for w in wr], right),
                 "On résout le système ∂f/∂x = 0, ∂f/∂y = 0 (linéaire ici) ; le déterminant 4ab − c² est non nul.", src=SRC)


_NATURE = ["un minimum local", "un maximum local", "un point selle (ni minimum ni maximum local)", "un point où le test de la Hessienne ne conclut pas"]


@gen(C, "l2m-hessian-nature", cap=90, cat="Calcul différentiel")
def hessian_nature(rng, d):
    if rng.random() < 0.55:
        al, m = rng.randint(1, 3), rng.randint(1, 3)
        ga, y0 = nz(rng, -3, 3), rng.randint(-3, 3)
        s = rng.choice([1, -1])
        beta, delta = -3 * al * m * m, -2 * ga * y0
        terms = [(al, 3, 0), (beta, 1, 0), (ga, 0, 2), (delta, 0, 1)]
        terms = [t for t in terms if t[0]]
        px, py = s * m, y0
        f = _fpoly(terms)
        assert ev2(pdx(terms), px, py) == 0 and ev2(pdy(terms), px, py) == 0
        hxx, hyy, hxy = 6 * al * px, 2 * ga, 0
        text = f"Le point ({px} ; {py}) est critique pour f(x, y) = {poly2(terms)}. Quelle est sa nature ?".replace(f"({px} ;", f"({num(px)} ;").replace(f"; {py})", f"; {num(py)})")
        pt = (F(px), F(py))
    else:
        a, b, c = nz(rng, -3, 3), nz(rng, -3, 3), rng.randint(-5, 5)
        det_ = 4 * a * b - c * c
        if det_ == 0:
            return None
        terms = [t for t in [(a, 2, 0), (c, 1, 1), (b, 0, 2)] if t[0]]
        f = _fpoly(terms)
        hxx, hyy, hxy = 2 * a, 2 * b, c
        text = f"Quelle est la nature du point critique (0 ; 0) de f(x, y) = {poly2(terms)} ?"
        pt = (F(0), F(0))
    detH = hxx * hyy - hxy * hxy
    if detH > 0:
        res = 0 if hxx > 0 else 1
    elif detH < 0:
        res = 2
    else:
        return None
    # second method: compare f around the point along 8 directions at a small distance (exact Fractions)
    eps = F(1, 50)
    dirs = [(1, 0), (0, 1), (1, 1), (1, -1), (2, 1), (1, 2), (2, -1), (1, -2)]
    diffs = []
    for u, v in dirs:
        for s_ in (1, -1):
            diffs.append(f(pt[0] + s_ * eps * u, pt[1] + s_ * eps * v) - f(pt[0], pt[1]))
    brute = 0 if all(x > 0 for x in diffs) else 1 if all(x < 0 for x in diffs) else 2
    assert brute == res, (terms, pt, res, brute)
    right = _NATURE[res]
    return Draft(text, right, [w for w in _NATURE if w != right],
                 f"Hessienne : r = {hxx}, t = {hyy}, s = {hxy}, déterminant rt − s² = {detH} " + ("(positif : extremum, de signe celui de r)." if detH > 0 else "(négatif : point selle)."), src=SRC)


@gen(C, "l2m-lagrange", cap=70, cat="Extrema liés")
def lagrange_q(rng, d):
    kind = rng.choice(["max-xy", "min-circle", "max-lin", "min-lin-circle"])
    if kind == "max-xy":
        s = rng.randint(2, 30)
        val = F(s * s, 4)
        best = max(F(x) * (s - F(x)) for x in [F(k, 2) for k in range(0, 2 * s + 1)])
        assert best <= val and F(s, 2) * (s - F(s, 2)) == val
        text = f"On cherche le maximum du produit xy sous la contrainte x + y = {s} (x et y réels). Quelle est sa valeur ?"
        wr = [F(s * s, 2), F(s, 2), F(s * s), F(s * s, 8), F(s)]
        ex_ = f"Lagrange : ∇(xy) = λ∇(x + y) donne x = y = {num(F(s, 2))}, d'où le produit {num(val)}."
    elif kind == "min-circle":
        a, b, c = rng.randint(1, 5), rng.randint(1, 5), rng.randint(1, 12)
        val = F(c * c, a * a + b * b)
        # second method: parametrize the line a x + b y = c and minimize the quadratic in t
        x0, y0 = F(c * a, a * a + b * b), F(c * b, a * a + b * b)
        best = min((x0 + b * F(t, 50)) ** 2 + (y0 - a * F(t, 50)) ** 2 for t in range(-100, 101))
        assert (x0 * x0 + y0 * y0) == val and best >= val
        text = f"Quelle est la valeur minimale de x² + y² sous la contrainte {a if a > 1 else ''}x + {b if b > 1 else ''}y = {c} ?"
        wr = [F(c, a * a + b * b), F(c * c, a + b), F(c * c), F(c * c, (a * a + b * b) * 2), F(c, a + b)]
        ex_ = f"Lagrange : (x, y) est proportionnel à ({a} ; {b}), d'où x² + y² = c²/(a² + b²) = {num(val)}."
    elif kind == "max-lin":
        a, b, h = rng.choice([(3, 4, 5), (4, 3, 5), (5, 12, 13), (12, 5, 13), (8, 15, 17), (6, 8, 10), (15, 8, 17), (7, 24, 25)])
        r = rng.randint(1, 6)
        val = F(r * h)
        mx = max(r * (a * math.cos(t * PI / 1800) + b * math.sin(t * PI / 1800)) for t in range(3600))
        assert abs(mx - float(val)) < 1e-3 * float(val) and h * h == a * a + b * b
        text = f"Quelle est la valeur maximale de {a}x + {b}y sur le cercle x² + y² = {r * r} ?"
        wr = [F(r * (a + b)), F(r * h * h), F(r * r * h), F(r * a), F(h)]
        ex_ = f"Le maximum de ax + by sur le cercle de rayon r est r√(a² + b²) = {r}·{h} (Cauchy-Schwarz ou Lagrange)."
    else:
        a, b, h = rng.choice([(3, 4, 5), (4, 3, 5), (5, 12, 13), (12, 5, 13), (8, 15, 17), (6, 8, 10)])
        r = rng.randint(1, 5)
        val = F(r * h)
        mn = min(r * (a * math.cos(t * PI / 1800) + b * math.sin(t * PI / 1800)) for t in range(3600))
        assert abs(mn + float(val)) < 1e-3 * float(val)
        text = f"Quelle est la valeur de la différence entre le maximum et le minimum de {a}x + {b}y sur le cercle x² + y² = {r * r} ?"
        val = F(2 * r * h)
        wr = [F(r * h), F(2 * r * (a + b)), F(r * h * 4), F(2 * r * a), F(2 * h)]
        ex_ = f"Les extrema valent ±r√(a² + b²) = ±{r * h}, d'où un écart de {2 * r * h}."
    right = num(val)
    return Draft(text, right, dedup([num(F(w)) for w in wr], right), ex_, src=SRC)


# ======================================================================================================== ÉQUATIONS DIFFÉRENTIELLES
def lin_text(cs, names="xyz"):
    out = ""
    for c, n in zip(cs, names):
        if c == 0:
            continue
        a = abs(c)
        t = ("" if a == 1 else num(a)) + n
        out += (MINUS if c < 0 else "") + t if not out else (" − " if c < 0 else " + ") + t
    return out or "0"


def ode_sol_str(yp, c, a):
    s = num(yp) if yp != 0 else ""
    if c != 0:
        s += coef(c, exx(-a, "t"), first=(s == ""))
    return s or "0"


def rk4(f, y0, x0, x1, n=2000):
    h = (x1 - x0) / n
    x, y = x0, y0
    for _ in range(n):
        k1 = f(x, y)
        k2 = f(x + h / 2, y + h * k1 / 2)
        k3 = f(x + h / 2, y + h * k2 / 2)
        k4 = f(x + h, y + h * k3)
        y += h * (k1 + 2 * k2 + 2 * k3 + k4) / 6
        x += h
    return y


@gen(C, "l2m-ode1-linear", cap=80, cat="Équations différentielles")
def ode1_linear(rng, d):
    a = nz(rng, -5, 5)
    b, y0 = rng.randint(-8, 8), rng.randint(-6, 6)
    kind = rng.choice(["sol", "sol", "lim"])
    yp = F(b, a)
    c = y0 - yp
    eq = f"y' {'+' if a > 0 else '−'} {'' if abs(a) == 1 else abs(a)}y = {num(b)}"
    # numeric verification of y(t) = yp + c e^(-a t)
    y = lambda t: float(yp) + float(c) * math.exp(-a * t)
    t0, h = 0.7, 1e-6
    assert abs((y(t0 + h) - y(t0 - h)) / (2 * h) + a * y(t0) - b) < 1e-5 * max(1.0, abs(y(t0)))
    assert abs(y(0) - y0) < 1e-12
    if kind == "sol":
        right = ode_sol_str(yp, c, a)
        cand = [ode_sol_str(yp, c, -a), ode_sol_str(yp, y0 + yp, a), ode_sol_str(F(b), y0 - b, a), ode_sol_str(0, F(y0), a), ode_sol_str(yp, y0 - yp * a if False else F(y0), a), ode_sol_str(-yp, y0 + yp, a)]
        wr = [w for w in dedup(cand, right)]
        return Draft(f"Quelle est la solution y(t) du problème de Cauchy {eq}, y(0) = {num(y0)} ?", right, wr,
                     f"Solution constante {num(yp)} (équilibre) plus solution homogène C·e^({num(-a)}t) ; la condition initiale donne C = {num(c)}.".replace("e^(" + num(-a) + "t)", exx(-a, "t")), src=SRC)
    if a < 0:
        a2 = -a
        eq = f"y' + {'' if a2 == 1 else a2}y = {num(b)}"
        a = a2
        yp = F(b, a)
    right = num(yp)
    wr = [num(F(b)), num(F(y0)), num(yp + y0), "0", num(F(b * a)), num(F(a, b)) if b else "1"]
    return Draft(f"Vers quelle valeur tend la solution de {eq} (quelle que soit la condition initiale) quand t tend vers +∞ ?", right, dedup(wr, right),
                 f"Comme {a} > 0, le terme C·e^(−{a}t) tend vers 0 : il reste la solution constante b/a = {num(yp)}.", src=SRC)


@gen(C, "l2m-ode1-separable", cap=60, cat="Équations différentielles")
def ode1_sep(rng, d):
    a, y0, x1 = nz(rng, -6, 6), rng.randint(1, 6), rng.randint(1, 3)
    e = F(a * x1 * x1, 2)
    val = y0 * math.exp(float(e))
    num_val = rk4(lambda x, y: a * x * y, float(y0), 0.0, float(x1), 6000)
    assert abs(num_val - val) <= 1e-6 * max(1e-3, abs(val)) + 1e-9, (a, y0, x1, num_val, val)
    right = coef(F(y0), ex(e))
    wr = [coef(F(y0), ex(F(a * x1 * x1))), coef(F(y0), ex(F(a * x1))), coef(F(y0), ex(F(a, 2))), coef(F(y0 * a), ex(e)), coef(F(y0), ex(F(a * x1 * x1, 4))), coef(F(y0), ex(e + 1))]
    return Draft(f"Quelle est la valeur en x = {x1} de la solution de y' = {'' if abs(a) == 1 and a > 0 else (MINUS if a == -1 else a)}xy avec y(0) = {y0} ?".replace(f"y' = {a}xy", f"y' = {num(a)}xy"), right, dedup(wr, right),
                 f"Variables séparées : y(x) = y(0)·e^(a x²/2) = {y0}·e^({num(F(a, 2))}x²), donc y({x1}) = {right}.", src=SRC)


def eq2_text(p, q, rhs="0"):
    s = "y''"
    if p:
        s += (" − " if p < 0 else " + ") + ("" if abs(p) == 1 else str(abs(p))) + "y'"
    if q:
        s += (" − " if q < 0 else " + ") + ("" if abs(q) == 1 else str(abs(q))) + "y"
    return s + " = " + rhs


def _expterm(r, cname):
    return cname if r == 0 else cname + exx(r)


@gen(C, "l2m-ode2-general", cap=100, cat="Équations différentielles")
def ode2_general(rng, d):
    kind = rng.choice(["real", "real", "double", "complex"])
    if kind == "real":
        r1, r2 = rng.sample(range(-9, 10), 2)
        r1, r2 = min(r1, r2), max(r1, r2)
        p, q = -(r1 + r2), r1 * r2
        right = f"{_expterm(r1, 'C₁')} + {_expterm(r2, 'C₂')}"
        sol = lambda x, c1, c2: c1 * math.exp(r1 * x) + c2 * math.exp(r2 * x)
    elif kind == "double":
        r = rng.randint(-9, 9)
        p, q = -2 * r, r * r
        right = f"(C₁ + C₂x){exx(r)}" if r else "C₁ + C₂x"
        sol = lambda x, c1, c2: (c1 + c2 * x) * math.exp(r * x)
    else:
        al, be = rng.randint(-5, 5), rng.randint(1, 6)
        p, q = -2 * al, al * al + be * be
        trig = f"C₁cos({be}x) + C₂sin({be}x)" if be != 1 else "C₁cos x + C₂sin x"
        right = f"{exx(al)}({trig})" if al else trig
        sol = lambda x, c1, c2: math.exp(al * x) * (c1 * math.cos(be * x) + c2 * math.sin(be * x))
    # numeric check: y'' + p y' + q y = 0 for a few choices of constants
    h = 1e-4
    for c1, c2 in ((1.0, 0.0), (0.0, 1.0), (1.3, -0.7)):
        for x in (0.3, 0.8):
            y0_, yp_, ym_ = sol(x, c1, c2), sol(x + h, c1, c2), sol(x - h, c1, c2)
            res = (yp_ - 2 * y0_ + ym_) / (h * h) + p * (yp_ - ym_) / (2 * h) + q * y0_
            assert abs(res) < 1e-3 * max(1.0, abs(y0_) * (abs(q) + abs(p) + 1)), (kind, p, q, res)
    # distractors: wrong characteristic roots / wrong structures
    pp, qq = abs(p), abs(q)
    cands = []
    cands.append(f"{_expterm(p, 'C₁')} + {_expterm(q, 'C₂')}")
    cands.append(f"(C₁ + C₂x){exx(F(-p, 2))}" if p else f"C₁ + C₂x")
    be2 = max(1, math.isqrt(abs(q))) if q else 1
    cands.append(f"{exx(F(-p, 2))}(C₁cos({be2}x) + C₂sin({be2}x))" if p else f"C₁cos({be2}x) + C₂sin({be2}x)")
    if kind == "real":
        cands.append(f"{_expterm(r1, 'C₁')} + {_expterm(r2 + 1, 'C₂')}")
        cands.append(f"(C₁ + C₂x){exx(r1)}" if r1 else "C₁ + C₂x")
        cands.append(f"{_expterm(r1 * r2, 'C₁')} + {_expterm(r1 + r2 + 1 if r1 + r2 + 1 not in (r1, r2) else r1 + r2 + 2, 'C₂')}")
    elif kind == "double":
        cands.append(f"{_expterm(r, 'C₁')} + {_expterm(r + 1, 'C₂')}")
        cands.append(f"C₁{exx(r)} + C₂{exx(r * r)}" if r else "C₁ + C₂x²")
        cands.append(f"(C₁ + C₂x){exx(r * r)}" if r * r != r else f"(C₁ + C₂x²){exx(r)}")
    else:
        cands.append(f"{exx(al)}(C₁cos({be + 1}x) + C₂sin({be + 1}x))")
        cands.append(f"{exx(al + be)}(C₁cos x + C₂sin x)")
        cands.append(f"{_expterm(al + be, 'C₁')} + {_expterm(al - be, 'C₂')}")
        cands.append(f"{exx(al)}(C₁cos({be * be}x) + C₂sin({be * be}x))" if be * be != be else f"{exx(al)}(C₁cos({be + 2}x) + C₂sin({be + 2}x))")
    wr = dedup(cands, right)
    return Draft(f"Quelle est la solution générale de l'équation {eq2_text(p, q)} ?", right, wr,
                 f"Équation caractéristique r² {'+' if p >= 0 else '−'} {abs(p)}r {'+' if q >= 0 else '−'} {abs(q)} = 0 : " + {"real": "deux racines réelles distinctes", "double": "une racine double", "complex": "deux racines complexes conjuguées"}[kind] + ".", src=SRC)


@gen(C, "l2m-ode2-ic", cap=90, cat="Équations différentielles")
def ode2_ic(rng, d):
    r1, r2 = rng.sample(range(-4, 5), 2)
    r1, r2 = min(r1, r2), max(r1, r2)
    y0, v0 = rng.randint(-6, 6), rng.randint(-8, 8)
    p, q = -(r1 + r2), r1 * r2
    A = F(v0 - r2 * y0, r1 - r2)
    B = F(v0 - r1 * y0, r2 - r1)
    # second method: Cramer on  A + B = y0 ; r1 A + r2 B = v0
    dt = r2 - r1
    assert A == F(y0 * r2 - v0, r2 - r1) and B == F(v0 - r1 * y0, dt) and A + B == y0 and r1 * A + r2 * B == v0
    which = rng.choice(["A", "B"])
    val = A if which == "A" else B
    wr = [B if which == "A" else A, F(y0) - val if False else F(v0, r2 if r2 else 1), F(v0 - r1 * y0, r1 - r2) if which == "B" else F(v0 - r2 * y0, r2 - r1), val + 1, F(y0, 2), val * 2]
    return Draft(f"La solution de {eq2_text(p, q)} avec y(0) = {num(y0)} et y'(0) = {num(v0)} s'écrit y = A·e^(r₁x) + B·e^(r₂x) avec r₁ < r₂ racines de l'équation caractéristique. Que vaut {which} ?",
                 num(val), dedup([num(F(w)) for w in wr], num(val)), f"Les racines sont {r1} et {r2} ; les conditions initiales donnent A + B = {y0} et {r1}A + {r2}B = {v0}, d'où {which} = {num(val)}.", src=SRC)


@gen(C, "l2m-ode2-particular", cap=80, cat="Équations différentielles")
def ode2_particular(rng, d):
    p, q = rng.randint(-4, 4), nz(rng, -6, 6)
    kind = rng.choice(["const", "exp", "affine"])
    k = nz(rng, -9, 9)
    h = 1e-4
    if kind == "const":
        yp = F(k, q)
        assert 0 + p * 0 + q * yp == k
        text = f"L'équation {eq2_text(p, q, num(k))} admet une solution particulière constante. Quelle est-elle ?"
        wr = [F(k), F(k, q + p) if q + p else F(k + 1), F(q, k), yp + 1, F(k * p, q) if p else yp * 2, F(k, 2 * q)]
        right = num(yp)
        ex_ = f"Pour y constante, y' = y'' = 0 : q·y = k, donc y = k/q = {num(yp)}."
    elif kind == "exp":
        m = rng.randint(-3, 3)
        den = m * m + p * m + q
        if den == 0:
            return None
        A = F(k, den)
        y = lambda x: float(A) * math.exp(m * x)
        x = 0.4
        res = (y(x + h) - 2 * y(x) + y(x - h)) / (h * h) + p * (y(x + h) - y(x - h)) / (2 * h) + q * y(x)
        assert abs(res - k * math.exp(m * x)) < 1e-3 * max(1.0, abs(k))
        text = f"On cherche une solution particulière de {eq2_text(p, q, num(k) + exx(m) if m else num(k))} de la forme A·{exx(m) if m else '1'}. Quelle est la valeur de A ?"
        wr = [F(k, q), F(k, m * m + p * m) if m * m + p * m else F(k + 1), F(k, m + p + q) if m + p + q else F(k + 2), F(k, den) + 1, F(k * m, den) if m else F(k * 2, den), F(k, den * 2)]
        right = num(A)
        ex_ = f"On remplace dans l'équation : A(m² + pm + q) = k avec m = {m}, soit A = {k}/{den}."
    else:
        alpha = F(k, q)
        beta = -p * alpha / q
        # check: y = alpha x + beta  ->  y'' = 0, y' = alpha : p alpha + q (alpha x + beta) = k x
        assert p * alpha + q * beta == 0 and q * alpha == k
        which = rng.choice(["α", "β"])
        text = f"On cherche une solution particulière de {eq2_text(p, q, num(k) + 'x')} de la forme y = αx + β. Que vaut {which} ?"
        val = alpha if which == "α" else beta
        if which == "β" and p == 0:
            return None
        wr = [beta if which == "α" else alpha, F(k), F(k, q * q), val + 1, F(p * k, q) if which == "β" else F(k, q) * 2, F(-p * k, q * q) * 2 if which == "β" else F(k, 2 * q)]
        right = num(val)
        ex_ = f"En identifiant : qα = {k} donc α = {num(alpha)}, et pα + qβ = 0 donc β = {num(beta)}."
    return Draft(text, right, dedup([num(F(w)) if not isinstance(w, str) else w for w in wr], right), ex_, src=SRC)


@gen(C, "l2m-ode-system-solution", cap=70, cat="Équations différentielles")
def ode_system_solution(rng, d):
    P = unimodular(rng, 2, 3)
    l1, l2 = rng.sample(range(-3, 4), 2)
    Pi = inverse(P)
    D = [[l1, 0], [0, l2]]
    A = matmul(matmul(P, D), Pi)
    A = [[int(x) for x in row] for row in A]
    v1, v2 = [P[0][0], P[1][0]], [P[0][1], P[1][1]]
    ok = lambda v, l: matvec(A, v) == [l * x for x in v]
    assert ok(v1, l1) and ok(v2, l2)
    sol = lambda v, l: (V(v) if l == 0 else f"{exx(l, 't')}·{V(v)}")
    wrongs = [(v1, l2), (v2, l1), ([v1[0] + v2[0], v1[1] + v2[1]], l1), ([v1[0], -v1[1]], l1), ([v1[1], v1[0]], l1)]
    wr = []
    for v, l in wrongs:
        if not ok(v, l):
            wr.append(sol(v, l))
    right = sol(v1, l1)
    return Draft(f"Soit A = {Mx(A)}. Laquelle de ces fonctions vectorielles est solution du système X' = AX ?", right, dedup(wr, right),
                 f"Si Av = λv, alors X(t) = e^(λt)·v est solution. Ici A·{V(v1)} = {l1}·{V(v1)}.", src=SRC)


_TYPES = ["nœud stable", "nœud instable", "point selle", "centre", "foyer stable", "foyer instable"]


@gen(C, "l2m-ode-system-type", cap=70, cat="Équations différentielles")
def ode_system_type(rng, d):
    kind = rng.choice(["real", "real", "complex"])
    P = unimodular(rng, 2, 3)
    Pi = inverse(P)
    if kind == "real":
        l1, l2 = rng.sample([-4, -3, -2, -1, 1, 2, 3, 4], 2)
        J = [[l1, 0], [0, l2]]
        want = "point selle" if l1 * l2 < 0 else ("nœud stable" if l1 < 0 else "nœud instable")
    else:
        al, be = rng.choice([-2, -1, 0, 0, 1, 2]), rng.randint(1, 3)
        J = [[al, -be], [be, al]]
        want = "centre" if al == 0 else ("foyer stable" if al < 0 else "foyer instable")
    A = [[int(x) for x in row] for row in matmul(matmul(P, J), Pi)]
    tr, det = A[0][0] + A[1][1], det2(A)
    disc = tr * tr - 4 * det
    # second method: classify from trace and determinant
    if det < 0:
        got = "point selle"
    elif disc >= 0:
        got = "nœud stable" if tr < 0 else "nœud instable"
    else:
        got = "centre" if tr == 0 else ("foyer stable" if tr < 0 else "foyer instable")
    assert got == want, (A, got, want)
    wr = [t for t in _TYPES if t != want]
    wr_sel = [t for t in wr if (t.split()[0] != want.split()[0])] + [t for t in wr if t.split()[0] == want.split()[0]]
    return Draft(f"Pour le système X' = AX avec A = {Mx(A)}, quelle est la nature du point d'équilibre (0 ; 0) ?", want, wr_sel[:5],
                 f"Trace = {tr}, déterminant = {det}, discriminant tr² − 4det = {disc} : " + got + ".", src=SRC)
