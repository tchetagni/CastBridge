"""Mathématiques du second cycle francophone, deuxième série de modèles (2nde, 1re, Tle) : calcul numérique, systèmes,
dénombrement, géométrie dans l'espace, probabilités conditionnelles, suites récurrentes, aires. Réponses recalculées dans le générateur."""
import math
from fractions import Fraction
from itertools import combinations, permutations

from .. import lycee
from ..core import MINUS, Draft, fr, gen
from .lycee_common import fraction_wrongs, interval, ints
from .mathfmt import dpoly, ev, nz, num, poly, sup

C2, C1, CT = lycee.fr("2nde", "maths"), lycee.fr("1re", "maths"), lycee.fr("tle", "maths")
S2 = "Programme MINESEC de 2nde (maths) - réponse calculée et vérifiée"
S1 = "Programme MINESEC de 1re (maths) - réponse calculée et vérifiée"
ST = "Programme MINESEC de Tle (maths) - réponse calculée et vérifiée"
TRIPLES = [(1, 2, 2, 3), (2, 3, 6, 7), (2, 6, 9, 11), (1, 4, 8, 9), (4, 4, 7, 9), (2, 10, 11, 15)]   # a² + b² + c² = d²


def pt(*xs):
    return "(" + " ; ".join(num(x) for x in xs) + ")"


def lin(a, b, var="x"):
    return poly([a, b], var)


def lincomb(coefs, names):
    """'5x + 2y − z' : signs and unit coefficients written the usual way."""
    out = ""
    for i, (c, n) in enumerate(zip(coefs, names)):
        if c == 0:
            continue
        body = ("" if abs(c) == 1 else str(abs(c))) + n
        out += (MINUS if c < 0 else "") + body if not out else (" − " if c < 0 else " + ") + body
    return out or "0"


def numdiff(f, x, h=1e-6):
    return (f(x + h) - f(x - h)) / (2 * h)


# ================================================================================================ 2nde
@gen(C2, "m2-fraction-add", cap=600, cat="Calcul numérique · séries A, C, D, E, TI", diffs=(1, 2, 3))
def fraction_add(rng, d):
    a, b = rng.randint(1, 7), rng.randint(2, 9)
    c, e = rng.randint(1, 7), rng.randint(2, 9)
    if b == e or math.gcd(a, b) != 1 or math.gcd(c, e) != 1:
        return None
    op = rng.choice(["+", "−", "×"])
    f1, f2 = Fraction(a, b), Fraction(c, e)
    v = f1 + f2 if op == "+" else f1 - f2 if op == "−" else f1 * f2
    wr = [Fraction(a + c, b + e), Fraction(a * c, b + e), f1 + f2 if op != "+" else f1 * f2, f1 - f2 if op != "−" else f1 + f2, Fraction(a + c, b * e) if op != "×" else Fraction(a * c, b + e)]
    return Draft(f"Quel est le résultat de {a}/{b} {op} {c}/{e} ?", num(v), [num(w) for w in wr if w != v] + [num(v * 2)], "On réduit au même dénominateur pour + et − ; on multiplie numérateurs et dénominateurs pour ×.", src=S2)


@gen(C2, "m2-sqrt-simplify", cap=600, cat="Calcul numérique · séries A, C, D, E, TI", diffs=(2, 3, 4))
def sqrt_simplify(rng, d):
    k, m = rng.randint(2, 9), rng.choice([2, 3, 5, 6, 7, 10])
    n = k * k * m
    assert math.isclose(math.sqrt(n), k * math.sqrt(m))
    right = f"{k}√{m}"
    return Draft(f"Quelle est la forme simplifiée de √{n} ?", right, [f"{k * m}√{m}", f"{k}√{m * 2}", f"{m}√{k}", f"{k * k}√{m}", f"{k + 1}√{m}"], f"{n} = {k * k} × {m}, donc √{n} = {k}√{m}.", src=S2)


@gen(C2, "m2-linear-system", cap=600, cat="Systèmes · séries A, C, D, E, TI", diffs=(2, 3, 4))
def linear_system(rng, d):
    x, y = rng.randint(-6, 8), rng.randint(-6, 8)
    a, b, c, e = nz(rng, -4, 5), nz(rng, -4, 5), nz(rng, -4, 5), nz(rng, -4, 5)
    det = a * e - b * c
    if det == 0:
        return None
    r1, r2 = a * x + b * y, c * x + e * y
    ask = rng.choice(["x", "y", "x + y"])
    ans = {"x": x, "y": y, "x + y": x + y}[ask]
    def eq(p, q, r):
        cx = "" if p == 1 else (MINUS if p == -1 else num(p))
        cy = "" if abs(q) == 1 else str(abs(q))
        return f"{cx}x {'+' if q > 0 else '−'} {cy}y = {num(r)}"
    s1, s2 = eq(a, b, r1), eq(c, e, r2)
    return Draft(f"On considère le système {s1} et {s2}. Que vaut {ask} ?", num(ans), [num(w) for w in (x, y, -x, -y, x - y, x * y, x + y + 1) if w != ans][:6], f"En résolvant le système (par substitution ou combinaison) on trouve x = {num(x)} et y = {num(y)}.", src=S2)


@gen(C2, "m2-vector-collinear", cap=600, cat="Géométrie analytique · séries A, C, D, E, TI", diffs=(3, 4))
def vector_collinear(rng, d):
    a, b = nz(rng, -5, 6), nz(rng, -5, 6)
    k = nz(rng, -3, 3)
    c = a * k
    # u(a, b) and v(c, m) collinear : a m - b c = 0 -> m = b c / a = b k
    m = b * k
    return Draft(f"Les vecteurs u({num(a)} ; {num(b)}) et v({num(c)} ; m) sont colinéaires. Quelle est la valeur de m ?", num(m), [num(w) for w in (-m, b + c, a * b, m + 1, Fraction(a * c, b) if b else 0, c) if w != m],
                 "u et v sont colinéaires si leur déterminant est nul : a·m − b·c = 0.", src=S2)


@gen(C2, "m2-weighted-mean", cap=600, cat="Statistiques · séries A, C, D, E, TI", diffs=(2, 3))
def weighted_mean(rng, d):
    vals = sorted(rng.sample(range(5, 20), 3))
    effs = [rng.randint(1, 6) for _ in range(3)]
    tot = sum(effs)
    s = sum(v * e for v, e in zip(vals, effs))
    if s % tot:
        return None
    m = s // tot
    table = ", ".join(f"{v} ({e} élève{'s' if e > 1 else ''})" for v, e in zip(vals, effs))
    return Draft(f"Notes obtenues : {table}. Quelle est la moyenne de la classe ?", str(m), [str(w) for w in (sum(vals) // 3 if sum(vals) % 3 == 0 else m + 2, m + 1, m - 1, vals[1], m + 3) if w != m], "moyenne = somme(valeur × effectif) ÷ effectif total.", src=S2)


@gen(C2, "m2-circle-area", cap=600, cat="Géométrie · séries A, C, D, E, TI", diffs=(1, 2, 3))
def circle_area(rng, d):
    r = rng.randint(2, 12)
    kind = rng.choice(["aire", "périmètre"])
    if kind == "aire":
        right, wr = f"{r * r}π", [f"{2 * r}π", f"{r}π", f"{r * r * 2}π", f"{r * r // 2 if r * r % 2 == 0 else r * r + 1}π"]
    else:
        right, wr = f"{2 * r}π", [f"{r * r}π", f"{r}π", f"{4 * r}π", f"{r + 2}π"]
    return Draft(f"Quel est {'l’aire' if kind == 'aire' else 'le périmètre'} d'un cercle (disque) de rayon {r} cm, exprimé avec π (en cm² pour l'aire, en cm pour le périmètre) ?", right, wr, "Aire = πr², périmètre = 2πr.", src=S2)


@gen(C2, "m2-degrees-radians", cap=200, cat="Trigonométrie · séries C, D, E, TI", diffs=(2, 3))
def degrees_radians(rng, d):
    deg = rng.choice([30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 360])
    f = Fraction(deg, 180)
    right = "π" if f == 1 else f"{f.numerator if f.numerator != 1 else ''}π/{f.denominator}" if f.denominator != 1 else f"{f.numerator}π"
    wr = []
    for g in (deg + 30, deg - 30, deg * 2, deg // 2, deg + 15, 180 - deg if deg < 180 else deg - 60):
        ff = Fraction(g, 180)
        if ff <= 0 or g == deg:
            continue
        wr.append("π" if ff == 1 else (f"{ff.numerator if ff.numerator != 1 else ''}π/{ff.denominator}" if ff.denominator != 1 else f"{ff.numerator}π"))
    return Draft(f"Quelle est la mesure en radians d'un angle de {deg}° ?", right, wr, "180° = π radians.", src=S2)


# ================================================================================================ 1re
@gen(C1, "m1-quadratic-sign", cap=600, cat="Second degré · séries C, D, E, TI", diffs=(3, 4, 5))
def quadratic_sign(rng, d):
    r1, r2 = sorted(rng.sample(range(-8, 9), 2))
    a = rng.choice([1, 2, -1, -2])
    s, p = r1 + r2, r1 * r2
    co = [a, -a * s, a * p]
    neg = rng.random() < 0.5
    f = lambda x: ev(co, x)
    sign = "<" if neg else ">"
    inside = (a > 0) == neg              # solution set between the roots ?
    if inside:
        right = interval(r1, r2, False, False)
        wr = [f"]−∞ ; {num(r1)}[ ∪ ]{num(r2)} ; +∞[", interval(r1, r2, True, True), f"]−∞ ; {num(r1)}] ∪ [{num(r2)} ; +∞[", interval(-r2, -r1, False, False)]
    else:
        right = f"]−∞ ; {num(r1)}[ ∪ ]{num(r2)} ; +∞["
        wr = [interval(r1, r2, False, False), interval(r1, r2, True, True), f"]−∞ ; {num(r1)}] ∪ [{num(r2)} ; +∞[", f"]−∞ ; {num(-r2)}[ ∪ ]{num(-r1)} ; +∞["]
    x_in, x_out = (r1 + r2) / 2, r2 + 1
    sol = lambda x: (f(x) < 0) if neg else (f(x) > 0)
    assert sol(x_in) == inside and sol(x_out) == (not inside)
    return Draft(f"Quel est l'ensemble des solutions de {poly(co)} {sign} 0 ?", right, wr, f"Les racines sont {num(r1)} et {num(r2)} ; on applique la règle du signe du trinôme (signe de a hors des racines).", src=S1)


@gen(C1, "m1-deriv-product", cap=600, cat="Dérivation · séries C, D, E, TI", diffs=(3, 4))
def deriv_product(rng, d):
    a, b, c, e = nz(rng, -4, 5), rng.randint(-5, 5), nz(rng, -4, 5), rng.randint(-5, 5)
    f = lambda x: (a * x + b) * (c * x + e)
    co = [a * c, a * e + b * c, b * e]
    dc = dpoly(co)
    assert all(abs(numdiff(f, t) - ev(dc, t)) < 1e-4 for t in (0.5, 2.0))
    u, v = lin(a, b), lin(c, e)
    return Draft(f"Quelle est la dérivée de f(x) = ({u})({v}) ?", poly(dc), [poly([a * c, a * e + b * c + 1]), poly([a * c, a * e - b * c]), poly([2 * a * c, a * e + b * c + 1]), poly([a * c, 0]), poly([a + c, b + e])],
                 f"On développe f(x) = {poly(co)} puis f'(x) = {poly(dc)} (ou règle du produit u'v + uv').", src=S1)


@gen(C1, "m1-polynomial-root", cap=600, cat="Polynômes · séries C, D, E, TI", diffs=(3, 4))
def polynomial_root(rng, d):
    a = rng.randint(-4, 4)
    k = rng.randint(-6, 6)
    c3, c2, c1 = nz(rng, 1, 3), rng.randint(-4, 4), rng.randint(-5, 5)
    # P(x) = c3 x^3 + c2 x^2 + c1 x + k with P(a) = 0  -> k = -(c3 a^3 + c2 a^2 + c1 a)
    k = -(c3 * a ** 3 + c2 * a ** 2 + c1 * a)
    co = [c3, c2, c1, k]
    assert ev(co, a) == 0
    return Draft(f"Pour quelle valeur du réel k le polynôme P(x) = {poly([c3, c2, c1])} + k admet-il {num(a)} pour racine ?", num(k), ints(rng, k, 4) + [num(-k), num(ev([c3, c2, c1, 0], a))],
                 f"On veut P({num(a)}) = 0 : {num(ev([c3, c2, c1, 0], a))} + k = 0, donc k = {num(k)}.", src=S1)


@gen(C1, "m1-combinations", cap=600, cat="Dénombrement · séries C, D, E, TI", diffs=(2, 3, 4))
def combinations_(rng, d):
    n, p = rng.randint(4, 12), rng.randint(2, 4)
    if p >= n:
        return None
    kind = rng.choice(["comb", "arr", "perm"])
    if kind == "comb":
        v = math.comb(n, p)
        t = f"De combien de façons peut-on choisir {p} élèves parmi {n}, sans tenir compte de l'ordre ?"
        wr = [math.perm(n, p), n ** p, math.comb(n, p) * 2, math.comb(n + 1, p)]
        e = f"C({n},{p}) = {v}."
    elif kind == "arr":
        v = math.perm(n, p)
        t = f"{n} élèves se présentent à un concours où les {p} premières places sont classées 1ᵉʳ, 2ᵉ, 3ᵉ… De combien de classements possibles dispose-t-on ?"
        wr = [math.comb(n, p), n ** p, math.factorial(n), math.perm(n, p) // 2 if math.perm(n, p) % 2 == 0 else v + 1]
        e = f"A({n},{p}) = {v}."
    else:
        n = rng.randint(3, 8)
        v = math.factorial(n)
        t = f"De combien de façons peut-on ranger {n} livres différents sur une étagère ?"
        wr = [n * n, n * 2, math.factorial(n - 1), math.factorial(n) * n]
        e = f"Il y a {n}! = {v} rangements possibles."
    return Draft(t, str(v), [str(w) for w in wr if w != v], e, src=S1)


@gen(C1, "m1-point-line-distance", cap=600, cat="Géométrie analytique · séries C, D, E, TI", diffs=(4, 5))
def point_line_distance(rng, d):
    a, b, h = rng.choice([(3, 4, 5), (4, 3, 5), (5, 12, 13), (12, 5, 13), (6, 8, 10), (8, 15, 17)])
    a, b = a * rng.choice([1, -1]), b * rng.choice([1, -1])
    x0, y0 = rng.randint(-5, 6), rng.randint(-5, 6)
    # line a x + b y + c = 0 chosen so that the signed distance is an integer: a x0 + b y0 + c = h * k
    k = nz(rng, -4, 4)
    c = h * k - (a * x0 + b * y0)
    dist = abs(a * x0 + b * y0 + c) / math.hypot(a, b)
    assert math.isclose(dist, abs(k))
    eq = f"{num(a)}x {'+' if b > 0 else '−'} {abs(b)}y {'+' if c >= 0 else '−'} {abs(c)} = 0" if c else f"{num(a)}x {'+' if b > 0 else '−'} {abs(b)}y = 0"
    return Draft(f"Quelle est la distance du point A{pt(x0, y0)} à la droite d'équation {eq} (repère orthonormé) ?", str(abs(k)), [str(w) for w in (abs(k) * h, abs(a * x0 + b * y0 + c), abs(k) + 1, abs(k) * 2, h) if w != abs(k)],
                 "d = |ax₀ + by₀ + c| / √(a² + b²).", src=S1)


@gen(C1, "m1-trig-equation", cap=300, cat="Trigonométrie · séries C, D, E, TI", diffs=(3, 4))
def trig_equation(rng, d):
    table = [("cos x = 1/2", "π/3 et 5π/3", ["π/6 et 11π/6", "π/3 et 2π/3", "π/6 et 5π/6", "π/4 et 7π/4"], lambda x: math.cos(x) - 0.5, [math.pi / 3, 5 * math.pi / 3]),
             ("sin x = 1/2", "π/6 et 5π/6", ["π/3 et 2π/3", "π/6 et 7π/6", "π/6 et 11π/6", "π/4 et 3π/4"], lambda x: math.sin(x) - 0.5, [math.pi / 6, 5 * math.pi / 6]),
             ("cos x = −1/2", "2π/3 et 4π/3", ["π/3 et 5π/3", "π/6 et 5π/6", "5π/6 et 7π/6", "π/3 et 2π/3"], lambda x: math.cos(x) + 0.5, [2 * math.pi / 3, 4 * math.pi / 3]),
             ("sin x = −1/2", "7π/6 et 11π/6", ["π/6 et 5π/6", "5π/6 et 7π/6", "4π/3 et 5π/3", "π/6 et 11π/6"], lambda x: math.sin(x) + 0.5, [7 * math.pi / 6, 11 * math.pi / 6]),
             ("cos x = √2/2", "π/4 et 7π/4", ["π/4 et 3π/4", "π/3 et 5π/3", "3π/4 et 5π/4", "π/6 et 11π/6"], lambda x: math.cos(x) - math.sqrt(2) / 2, [math.pi / 4, 7 * math.pi / 4]),
             ("sin x = √3/2", "π/3 et 2π/3", ["π/6 et 5π/6", "π/3 et 5π/3", "2π/3 et 4π/3", "π/4 et 3π/4"], lambda x: math.sin(x) - math.sqrt(3) / 2, [math.pi / 3, 2 * math.pi / 3]),
             ("cos x = 0", "π/2 et 3π/2", ["0 et π", "π/2 seulement", "π et 2π", "π/4 et 3π/4"], lambda x: math.cos(x), [math.pi / 2, 3 * math.pi / 2]),
             ("sin x = 0", "0 et π", ["π/2 et 3π/2", "π et 2π", "0 seulement", "π/2 et π"], lambda x: math.sin(x), [0, math.pi])]
    t, r, w, f, roots = rng.choice(table)
    assert all(abs(f(x)) < 1e-9 for x in roots)
    return Draft(f"Quelles sont les solutions de {t} dans l'intervalle [0 ; 2π[ ?", r, w, "On lit les angles correspondants sur le cercle trigonométrique.", src=S1)


# ================================================================================================ Tle
@gen(CT, "mt-space-distance", cap=600, cat="Géométrie dans l'espace · séries C, D, E", diffs=(3, 4))
def space_distance(rng, d):
    a, b, c, h = rng.choice(TRIPLES)
    sx, sy, sz = (rng.choice([1, -1]) for _ in range(3))
    A = [rng.randint(-4, 5) for _ in range(3)]
    B = [A[0] + sx * a, A[1] + sy * b, A[2] + sz * c]
    assert math.isclose(math.dist(A, B), h)
    return Draft(f"Dans un repère orthonormé de l'espace, quelle est la distance AB avec A{pt(*A)} et B{pt(*B)} ?", str(h), [str(w) for w in (a + b + c, h * h, h + 1, h - 1, abs(a - b) + c) if w != h], f"AB = √({a}² + {b}² + {c}²) = √{h * h} = {h}.", src=ST)


@gen(CT, "mt-plane-equation", cap=600, cat="Géométrie dans l'espace · séries C, D, E", diffs=(3, 4))
def plane_equation(rng, d):
    n = [nz(rng, -4, 5), nz(rng, -4, 5), nz(rng, -4, 5)]
    A = [rng.randint(-3, 4) for _ in range(3)]
    k = sum(n[i] * A[i] for i in range(3))
    def eq(kk):
        return f"{lincomb(n, 'xyz')} = {num(kk)}"
    return Draft(f"Quelle est l'équation du plan de vecteur normal n{pt(*n)} passant par A{pt(*A)} ?", eq(k), [eq(-k), eq(k + n[0]), eq(sum(A)), eq(k + 1), eq(sum(n[i] * A[(i + 1) % 3] for i in range(3)) if sum(n[i] * A[(i + 1) % 3] for i in range(3)) != k else k + 2)],
                 f"ax + by + cz = d avec d = n·A = {num(k)}.", src=ST)


@gen(CT, "mt-point-plane-distance", cap=600, cat="Géométrie dans l'espace · séries C, D, E", diffs=(4, 5))
def point_plane_distance(rng, d):
    a, b, c, h = rng.choice(TRIPLES)
    a, b, c = a * rng.choice([1, -1]), b * rng.choice([1, -1]), c * rng.choice([1, -1])
    P = [rng.randint(-4, 5) for _ in range(3)]
    k = nz(rng, -3, 3)
    dd = h * k - (a * P[0] + b * P[1] + c * P[2])
    dist = abs(a * P[0] + b * P[1] + c * P[2] + dd) / math.sqrt(a * a + b * b + c * c)
    assert math.isclose(dist, abs(k))
    eq = f"{lincomb([a, b, c], 'xyz')} {'+' if dd >= 0 else '−'} {abs(dd)} = 0"
    return Draft(f"Quelle est la distance du point P{pt(*P)} au plan d'équation {eq} ?", str(abs(k)), [str(w) for w in (abs(k) * h, abs(k) + 1, abs(k) * 2, h, abs(a * P[0] + b * P[1] + c * P[2] + dd)) if w != abs(k)],
                 "d = |ax₀ + by₀ + cz₀ + d| / √(a² + b² + c²).", src=ST)


@gen(CT, "mt-conditional-probability", cap=600, cat="Probabilités · séries C, D, E", diffs=(3, 4))
def conditional_probability(rng, d):
    pb, pab = rng.choice([20, 25, 40, 50, 60, 80]), None
    pab = rng.choice([x for x in (5, 10, 12, 15, 20, 24, 30, 40) if x <= pb])
    v = Fraction(pab, pb)
    wr = [Fraction(pb, pab), Fraction(pab, 100), Fraction(pb - pab, pb), Fraction(pab, 100 - pb) if pb < 100 else Fraction(1, 2), v + Fraction(1, 10)]
    return Draft(f"On a P(B) = {pb} % et P(A ∩ B) = {pab} %. Quelle est la probabilité de A sachant B ?", num(v), [num(w) for w in wr if w != v], "P(A|B) = P(A ∩ B) / P(B).", src=ST)


@gen(CT, "mt-sequence-fixed-point", cap=600, cat="Suites · séries C, D, E", diffs=(3, 4, 5))
def sequence_fixed_point(rng, d):
    a = rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(1, 4), Fraction(3, 4), Fraction(-1, 2), Fraction(1, 5)])
    b = rng.randint(1, 9)
    ell = Fraction(b) / (1 - a)
    u0 = rng.randint(0, 10)
    # check by iterating
    u = float(u0)
    for _ in range(400):
        u = float(a) * u + b
    assert abs(u - float(ell)) < 1e-9
    return Draft(f"La suite (uₙ) vérifie u₀ = {u0} et uₙ₊₁ = {num(a)}·uₙ + {b}. Vers quelle limite converge-t-elle ?", num(ell), [num(w) for w in (Fraction(b) / (1 + a), Fraction(b) * a, Fraction(b) / a, Fraction(u0), Fraction(b) * (1 - a), ell + 1) if w != ell],
                 "La limite ℓ vérifie ℓ = aℓ + b, donc ℓ = b/(1 − a) (car |a| < 1).", src=ST)


@gen(CT, "mt-area-between", cap=600, cat="Intégration · séries C, D, E", diffs=(4, 5))
def area_between(rng, d):
    a = rng.randint(2, 9)
    # area between y = a x and y = x² from 0 to a : a^3/6
    area = Fraction(a ** 3, 6)
    n = 400
    f = lambda x: a * x - x * x
    simpson = sum(((1 if i in (0, n) else (4 if i % 2 else 2)) * f(a * i / n)) for i in range(n + 1)) * a / (3 * n)
    assert abs(simpson - float(area)) < 1e-6
    return Draft(f"Quelle est l'aire (en unités d'aire) du domaine compris entre les courbes y = x² et y = {a}x ?", num(area), [num(w) for w in (Fraction(a ** 3, 3), Fraction(a ** 3, 2), Fraction(a ** 2, 2), Fraction(a ** 3, 6) + 1, Fraction(a ** 3, 12)) if w != area],
                 f"Les courbes se coupent en 0 et {a} ; l'aire est ∫ de 0 à {a} de ({a}x − x²) dx = {num(area)}.", src=ST)


@gen(CT, "mt-volume-revolution", cap=600, cat="Intégration · séries C, D, E", diffs=(4, 5))
def volume_revolution(rng, d):
    a = rng.randint(1, 9)
    k = rng.choice([1, 2, 3])
    v = Fraction(k * k * a ** 3, 3)
    n = 400
    f = lambda x: (k * x) ** 2
    simpson = sum(((1 if i in (0, n) else (4 if i % 2 else 2)) * f(a * i / n)) for i in range(n + 1)) * a / (3 * n)
    assert abs(simpson - float(v)) < 1e-6 * max(1, float(v))
    return Draft(f"On fait tourner autour de l'axe des abscisses la courbe de f(x) = {k if k > 1 else ''}x sur [0 ; {a}]. Quel est le volume du solide engendré, en unités de volume ?", f"{num(v)}π", [f"{num(w)}π" for w in (Fraction(k * k * a ** 3, 2), Fraction(k * a ** 2, 2), Fraction(k * k * a ** 3, 1), v + 1, Fraction(k * k * a ** 2, 3)) if w != v],
                 "V = π ∫ de 0 à a de f(x)² dx.", src=ST)


@gen(CT, "mt-complex-division", cap=600, cat="Nombres complexes · séries C, D, E", diffs=(3, 4))
def complex_division(rng, d):
    a, b, c, e = (rng.randint(-5, 6) for _ in range(4))
    if c == 0 and e == 0:
        return None
    z = complex(a, b) / complex(c, e)
    re, im = Fraction(a * c + b * e, c * c + e * e), Fraction(b * c - a * e, c * c + e * e)
    assert abs(float(re) - z.real) < 1e-9 and abs(float(im) - z.imag) < 1e-9

    def zt(r, i):
        r, i = Fraction(r), Fraction(i)
        if i == 0:
            return num(r)
        ia = abs(i)
        itxt = ("" if ia == 1 else (f"({num(ia)})" if ia.denominator != 1 else num(ia))) + "i"
        if r == 0:
            return ("−" if i < 0 else "") + itxt
        return f"{num(r)} {'+' if i > 0 else '−'} {itxt}"

    def zt_in(a_, b_):
        return f"{num(a_)} {'+' if b_ >= 0 else '−'} {abs(b_) if abs(b_) != 1 else ''}i"

    right = zt(re, im)
    wr = [zt(re, -im), zt(Fraction(a * c - b * e, c * c + e * e), Fraction(b * c + a * e, c * c + e * e)), zt(Fraction(a, c) if c else 1, Fraction(b, e) if e else 2), zt(-re, im), zt(re + 1, im)]
    return Draft(f"Quelle est la forme algébrique de ({zt_in(a, b)}) / ({zt_in(c, e)}) ?", right, [w for w in wr if w != right], "On multiplie numérateur et dénominateur par le conjugué du dénominateur.", src=ST)


@gen(CT, "mt-ln-derivative", cap=600, cat="Logarithme · séries C, D, E", diffs=(3, 4))
def ln_derivative(rng, d):
    a = rng.randint(1, 9)
    f = lambda x: math.log(x * x + a)
    assert abs(numdiff(f, 1.3) - 2 * 1.3 / (1.3 ** 2 + a)) < 1e-6
    return Draft(f"Quelle est la dérivée de f(x) = ln(x² + {a}) sur ℝ ?", f"2x/(x² + {a})", [f"1/(x² + {a})", f"x/(x² + {a})", f"2x·ln(x² + {a})", f"2x + {a}", f"(2x)/({a})"], "(ln u)' = u'/u avec u = x² + a.", src=ST)
