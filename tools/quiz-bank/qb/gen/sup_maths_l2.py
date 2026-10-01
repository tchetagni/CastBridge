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
@gen(C, "l2m-improper-power", cap=80, cat="Intégrales généralisées")
def improper_power(rng, d):
    if rng.random() < 0.6:
        c, a = rng.randint(1, 6), rng.randint(2, 6)
        val = F(1, (a - 1) * c ** (a - 1))
        # second method: substitution t = 1/x  ->  integral over ]0, 1/c] of t^(a-2) dt
        assert val == F(1, c) ** (a - 1) / (a - 1)
        right = num(val)
        wr = [num(F(1, c ** a)), num(F(1, a * c ** a)), num(F(1, (a - 1) * c ** a)), num(F(1, c ** (a - 1))), num(F(a, c)), "l'intégrale diverge"]
        return Draft(f"Que vaut l'intégrale généralisée de 1/x{sup(a)} sur [{c} ; +∞[ ?", right, wr,
                     f"Une primitive est −1/({a - 1}x^{a - 1}) ; elle tend vers 0 en +∞, d'où la valeur 1/({a - 1}·{c ** (a - 1)}) = {right}.", src=SRC)
    q, p = rng.choice([(2, 1), (3, 1), (3, 2), (4, 1), (4, 3)])      # exponent p/q < 1
    m = rng.randint(1, 3)
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


@gen(C, "l2m-improper-exp", cap=80, cat="Intégrales généralisées")
def improper_exp(rng, d):
    if rng.random() < 0.55:
        n, a = rng.randint(0, 4), rng.choice([1, 2, 3, 4, 5, 6])
        val = F(math.factorial(n), a ** (n + 1))
        num_val = simpson(lambda x: x ** n * math.exp(-a * x), 0, 40.0 / a, 20000)
        assert close(num_val, float(val), 1e-6)
        right = num(val)
        xp = "" if n == 0 else ("x" if n == 1 else "x" + sup(n))
        wr = [num(F(math.factorial(n), a ** n)), num(F(1, a ** (n + 1))), num(F(math.factorial(n + 1), a ** (n + 1))), num(F(n + 1, a ** (n + 1))), num(F(math.factorial(n), a ** (n + 2)))]
        return Draft(f"Que vaut l'intégrale généralisée de {xp}{exx(-a)} sur [0 ; +∞[ ?", right, wr,
                     f"∫₀^∞ xⁿe^(−ax)dx = n!/a^(n+1) : ici {n}!/{a}^{n + 1} = {right}.", src=SRC)
    a, c = rng.randint(1, 5), rng.randint(1, 4)
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
    a = nz(rng, -3, 3)
    p, q = nz(rng, -4, 4), rng.randint(-3, 3)
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
    n, k = rng.randint(1, 5), rng.randint(1, 4)
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


@gen(C, "l2m-ibp-trig", cap=70, cat="Intégration")
def ibp_trig(rng, d):
    fun, ub, ubv, (c2, c1, c0) = rng.choice(_PI_TABLE)
    k = rng.randint(1, 5)
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
    m, n, c, b = rng.randint(1, 3), rng.randint(2, 4), rng.randint(1, 6), rng.randint(1, 2)
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
