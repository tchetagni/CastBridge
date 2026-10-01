"""Mathématiques du second cycle francophone : 2nde, 1re, Tle (séries A, C, D, E, TI selon le thème).
Questions originales ; chaque réponse est recalculée dans le générateur (substitution, Fractions exactes, dérivée numérique)."""
import math
from fractions import Fraction
from itertools import combinations

from .. import lycee
from ..core import MINUS, Draft, fr, gen
from .lycee_common import fraction_wrongs, interval, ints
from .mathfmt import dpoly, ev, nz, num, poly, sup

C2, C1, CT = lycee.fr("2nde", "maths"), lycee.fr("1re", "maths"), lycee.fr("tle", "maths")
S2 = "Programme MINESEC de 2nde (maths) - réponse calculée et vérifiée par substitution"
S1 = "Programme MINESEC de 1re (maths) - réponse calculée et vérifiée"
ST = "Programme MINESEC de Tle (maths) - réponse calculée et vérifiée"
PYTH = [(3, 4, 5), (5, 12, 13), (6, 8, 10), (8, 15, 17), (7, 24, 25), (9, 12, 15), (20, 21, 29)]


def pt(x, y):
    return f"({num(x)} ; {num(y)})"


def lin(a, b, var="x"):
    return poly([a, b], var)


def numdiff(f, x, h=1e-6):
    return (f(x + h) - f(x - h)) / (2 * h)


# ================================================================================================ 2nde
@gen(C2, "m2-eq2-factored", cap=600, cat="Algèbre · séries A, C, D, E, TI", diffs=(2, 3))
def eq2_factored(rng, d):
    a, b = rng.randint(-9, 9), rng.randint(-9, 9)
    if a == b:
        return None
    s, p = a + b, a * b
    assert a * a - s * a + p == 0 and b * b - s * b + p == 0
    right = " et ".join(num(v) for v in sorted((a, b)))
    wrongs = [" et ".join(num(v) for v in sorted((-a, -b)))]
    wrongs.append(" et ".join(num(v) for v in sorted((a, -b))))
    wrongs.append(" et ".join(num(v) for v in sorted((s, p))))
    wrongs.append(" et ".join(num(v) for v in sorted((a + 1, b))))
    wrongs.append(" et ".join(num(v) for v in sorted((-s, p))))
    return Draft(f"Quelles sont les solutions de l'équation {poly([1, -s, p])} = 0 ?", right, wrongs,
                 f"On cherche deux nombres de somme {num(s)} et de produit {num(p)} : {num(a)} et {num(b)} ; on vérifie en remplaçant x.", src=S2)


@gen(C2, "m2-discriminant", cap=600, cat="Algèbre · séries A, C, D, E, TI", diffs=(2, 3))
def discriminant(rng, d):
    a, b, c = nz(rng, -6, 6), rng.randint(-9, 9), rng.randint(-9, 9)
    delta = b * b - 4 * a * c
    return Draft(f"Quel est le discriminant Δ du trinôme {poly([a, b, c])} ?", num(delta),
                 [num(b * b + 4 * a * c), num(b * b - 2 * a * c), num(-b * b - 4 * a * c), num(b - 4 * a * c), num(b * b - 4 * a + c)] + ints(rng, delta, 4),
                 f"Δ = b² − 4ac = ({num(b)})² − 4×({num(a)})×({num(c)}) = {num(delta)}.", src=S2)


@gen(C2, "m2-roots-count", cap=600, cat="Algèbre · séries A, C, D, E, TI", diffs=(2, 3))
def roots_count(rng, d):
    a, b, c = nz(rng, -5, 5), rng.randint(-8, 8), rng.randint(-8, 8)
    delta = b * b - 4 * a * c
    n = 2 if delta > 0 else (1 if delta == 0 else 0)
    names = {0: "aucune solution réelle", 1: "une seule solution réelle (solution double)", 2: "deux solutions réelles distinctes"}
    return Draft(f"Combien de solutions réelles l'équation {poly([a, b, c])} = 0 admet-elle ?", names[n], [v for k, v in names.items() if k != n] + ["une infinité de solutions"],
                 f"Δ = {num(delta)} : " + ("Δ < 0, pas de racine réelle." if n == 0 else "Δ = 0, racine double." if n == 1 else "Δ > 0, deux racines."), src=S2)


@gen(C2, "m2-ineq1", cap=600, cat="Inéquations · séries A, C, D, E, TI", diffs=(1, 2))
def ineq1(rng, d):
    a, k = nz(rng, -7, 7), rng.randint(-8, 8)
    b = rng.randint(-9, 9)
    c = a * k + b                               # a x + b < c  <=>  x < k (a>0)  or  x > k (a<0)
    if a > 0:
        right, flip = f"x < {num(k)}", f"x > {num(k)}"
    else:
        right, flip = f"x > {num(k)}", f"x < {num(k)}"
    for x in (k - 1, k + 1):
        assert (a * x + b < c) == ((x < k) if a > 0 else (x > k))
    return Draft(f"Quelle est la solution de l'inéquation {lin(a, b)} < {num(c)} ?", right,
                 [flip, f"x ≤ {num(k)}" if a > 0 else f"x ≥ {num(k)}", f"x < {num(-k)}" if a > 0 else f"x > {num(-k)}", f"x < {num(c - b)}"],
                 f"On isole x : {num(a)}x < {num(c - b)}" + (" ; on divise par un nombre négatif, le sens s'inverse." if a < 0 else "."), src=S2)


@gen(C2, "m2-abs-eq", cap=600, cat="Calcul dans R · séries A, C, D, E, TI", diffs=(2, 3))
def abs_eq(rng, d):
    a, r = rng.randint(-8, 8), rng.randint(1, 9)
    sol = sorted((a - r, a + r))
    inner = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    for x in sol:
        assert abs(x - a) == r
    return Draft(f"Quelles sont les solutions de |{inner}| = {r} ?", " et ".join(num(v) for v in sol),
                 [" et ".join(num(v) for v in sorted((-a - r, -a + r))), num(a + r) + " seulement", " et ".join(num(v) for v in sorted((r - a, a))), " et ".join(num(v) for v in sorted((a - r - 1, a + r + 1)))],
                 f"|{inner}| = {r} équivaut à {inner} = {r} ou {inner} = −{r}.", src=S2)


@gen(C2, "m2-abs-ineq", cap=600, cat="Calcul dans R · séries A, C, D, E, TI", diffs=(3, 4))
def abs_ineq(rng, d):
    a, r = rng.randint(-6, 6), rng.randint(1, 8)
    inner = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    right = interval(a - r, a + r, True, True)
    for x in (a - r, a, a + r):
        assert abs(x - a) <= r
    assert abs((a + r + 1) - a) > r
    return Draft(f"Quel est l'ensemble des solutions de |{inner}| ≤ {r} ?", right,
                 [interval(a - r, a + r, False, False), f"]−∞ ; {num(a - r)}] ∪ [{num(a + r)} ; +∞[", interval(-r, r, True, True), interval(a - r, a + r + 1, True, True)],
                 f"|x − {num(a)}| ≤ {r} signifie que x est à une distance au plus {r} de {num(a)} : {right}.", src=S2)


@gen(C2, "m2-interval-inter", cap=600, cat="Ensembles et intervalles · séries A, C, D, E, TI", diffs=(2, 3))
def interval_inter(rng, d):
    a = rng.randint(-8, 3)
    b = a + rng.randint(3, 9)
    c = rng.randint(a + 1, b - 1)
    e = b + rng.randint(1, 6)
    ca, cb = rng.choice([True, False]), rng.choice([True, False])
    cc, ce = rng.choice([True, False]), rng.choice([True, False])
    A, B = interval(a, b, ca, cb), interval(c, e, cc, ce)
    right = interval(c, b, cc, cb)
    union = interval(a, e, ca, ce)
    return Draft(f"Quelle est l'intersection {A} ∩ {B} ?", right, [union, interval(a, c, ca, cc), interval(c, b, not cc, not cb), interval(b, e, cb, ce)],
                 f"On garde les nombres présents dans les deux intervalles : de {num(c)} à {num(b)}, avec les crochets de la borne la plus restrictive.", src=S2)


@gen(C2, "m2-vector-coords", cap=600, cat="Géométrie analytique · séries A, C, D, E, TI", diffs=(1, 2))
def vector_coords(rng, d):
    xa, ya, xb, yb = (rng.randint(-8, 8) for _ in range(4))
    if (xa, ya) == (xb, yb):
        return None
    right = pt(xb - xa, yb - ya)
    return Draft(f"Dans un repère, A{pt(xa, ya)} et B{pt(xb, yb)}. Quelles sont les coordonnées du vecteur AB ?", right,
                 [pt(xa - xb, ya - yb), pt(xa + xb, ya + yb), pt(xb - xa, ya - yb), pt(xa - xb, yb - ya)],
                 "Les coordonnées de AB sont (xB − xA ; yB − yA).", src=S2)


@gen(C2, "m2-midpoint", cap=600, cat="Géométrie analytique · séries A, C, D, E, TI", diffs=(1, 2))
def midpoint(rng, d):
    xa, ya = rng.randint(-8, 8), rng.randint(-8, 8)
    mx, my = rng.randint(-6, 6), rng.randint(-6, 6)
    xb, yb = 2 * mx - xa, 2 * my - ya
    return Draft(f"A{pt(xa, ya)} et B{pt(xb, yb)} sont deux points du plan. Quelles sont les coordonnées du milieu I de [AB] ?", pt(mx, my),
                 [pt(xb - xa, yb - ya), pt(mx + 1, my), pt(xa + xb, ya + yb), pt(mx, my + 1), pt(-mx, -my)],
                 "I a pour coordonnées ((xA + xB)/2 ; (yA + yB)/2).", src=S2)


@gen(C2, "m2-distance", cap=600, cat="Géométrie analytique · séries A, C, D, E, TI", diffs=(2, 3))
def distance(rng, d):
    u, v, w = rng.choice(PYTH)
    k = rng.choice([1, 1, 2])
    u, v, w = u * k, v * k, w * k
    sx, sy = rng.choice([1, -1]), rng.choice([1, -1])
    xa, ya = rng.randint(-6, 6), rng.randint(-6, 6)
    xb, yb = xa + sx * u, ya + sy * v
    assert math.hypot(xb - xa, yb - ya) == w
    return Draft(f"Dans un repère orthonormé, quelle est la distance AB avec A{pt(xa, ya)} et B{pt(xb, yb)} ?", num(w), ints(rng, w, 4) + [num(u + v), num(abs(u - v)), num(u * u + v * v)],
                 f"AB = √(({num(xb - xa)})² + ({num(yb - ya)})²) = √{num(u * u + v * v)} = {num(w)}.", src=S2)


@gen(C2, "m2-slope", cap=600, cat="Droites · séries A, C, D, E, TI", diffs=(2, 3))
def slope(rng, d):
    xa, ya = rng.randint(-6, 6), rng.randint(-6, 6)
    dx, dy = nz(rng, -6, 6), rng.randint(-9, 9)
    xb, yb = xa + dx, ya + dy
    m = Fraction(dy, dx)
    wr = fraction_wrongs(rng, m, extra=[Fraction(dx, dy) if dy else 7])
    return Draft(f"Quel est le coefficient directeur de la droite (AB) avec A{pt(xa, ya)} et B{pt(xb, yb)} ?", num(m), wr,
                 f"m = (yB − yA)/(xB − xA) = {num(dy)}/{num(dx)} = {num(m)}.", src=S2)


@gen(C2, "m2-line-equation", cap=600, cat="Droites · séries A, C, D, E, TI", diffs=(3, 4))
def line_equation(rng, d):
    m, p = nz(rng, -5, 5), rng.randint(-8, 8)
    x1, x2 = rng.randint(-4, 0), rng.randint(1, 5)
    y1, y2 = m * x1 + p, m * x2 + p
    right = "y = " + lin(m, p)
    wr = ["y = " + lin(-m, p), "y = " + lin(m, -p), "y = " + lin(m, y1), "y = " + lin(m + 1, p)]
    return Draft(f"Quelle est l'équation réduite de la droite passant par A{pt(x1, y1)} et B{pt(x2, y2)} ?", right, wr,
                 f"m = ({num(y2)} − ({num(y1)}))/({num(x2)} − ({num(x1)})) = {num(m)}, puis p = {num(y1)} − {num(m)}×({num(x1)}) = {num(p)}.", src=S2)


@gen(C2, "m2-parallel-line", cap=600, cat="Droites · séries A, C, D, E, TI", diffs=(3, 4))
def parallel_line(rng, d):
    m, p = nz(rng, -5, 5), rng.randint(-6, 6)
    x0, y0 = rng.randint(-5, 5), rng.randint(-5, 5)
    q = y0 - m * x0
    if q == p:
        return None
    return Draft(f"Quelle est l'équation réduite de la parallèle à la droite y = {lin(m, p)} passant par le point {pt(x0, y0)} ?", "y = " + lin(m, q),
                 ["y = " + lin(-m, q), "y = " + lin(m, p), "y = " + lin(m, y0), "y = " + lin(Fraction(-1, m) if abs(m) == 1 else m + 1, q)],
                 f"Deux droites parallèles ont le même coefficient directeur {num(m)} ; on calcule p = {num(y0)} − {num(m)}×({num(x0)}) = {num(q)}.", src=S2)


@gen(C2, "m2-function-value", cap=600, cat="Fonctions · séries A, C, D, E, TI", diffs=(1, 2))
def function_value(rng, d):
    a, b, c = nz(rng, -4, 4), rng.randint(-6, 6), rng.randint(-8, 8)
    x0 = rng.randint(-4, 5)
    v = ev([a, b, c], x0)
    return Draft(f"Soit f(x) = {poly([a, b, c])}. Quelle est la valeur de f({num(x0)}) ?", num(v),
                 ints(rng, v, 4) + [num(a * x0 * x0 - b * x0 + c), num(a * x0 + b * x0 + c)], f"On remplace x par {num(x0)} : f({num(x0)}) = {num(v)}.", src=S2)


@gen(C2, "m2-domain", cap=600, cat="Fonctions · séries A, C, D, E, TI", diffs=(3, 4))
def domain(rng, d):
    a = rng.randint(-8, 8)
    inner = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    if rng.random() < 0.5:
        right = f"ℝ \\ {{{num(a)}}}"
        return Draft(f"Quel est l'ensemble de définition de f(x) = 1/({inner}) ?", right, [f"ℝ \\ {{{num(-a)}}}", "ℝ", interval(a, None, True, True), f"ℝ \\ {{0}}"],
                     f"Le dénominateur ne doit pas s'annuler : x ≠ {num(a)}.", src=S2)
    right = interval(a, None, True, True)
    return Draft(f"Quel est l'ensemble de définition de f(x) = √({inner}) ?", right, [interval(a, None, False, True), interval(None, a, True, True), "ℝ", interval(-a, None, True, True)],
                 f"Il faut {inner} ≥ 0, soit x ≥ {num(a)}.", src=S2)


@gen(C2, "m2-percent-chain", cap=600, cat="Pourcentages · séries A, C, D, E, TI", diffs=(3, 4))
def percent_chain(rng, d):
    p, q = rng.choice([10, 20, 25, 30, 40, 50]), rng.choice([10, 20, 25, 30, 40, 50])
    up = rng.random() < 0.5
    f = (Fraction(100 + p, 100)) * (Fraction(100 - q, 100)) if up else Fraction(100 - p, 100) * Fraction(100 + q, 100)
    var = (f - 1) * 100
    s = "{:+}".format(float(var)).replace("+", "+").rstrip("0").rstrip(".").replace("-", MINUS).replace(".", ",") + " %"
    first = f"augmente de {p} %" if up else f"baisse de {p} %"
    second = f"baisse de {q} %" if up else f"augmente de {q} %"
    if var == 0:
        return None
    def txt(v):
        return ("{:+}".format(float(v)).rstrip("0").rstrip(".").replace("-", MINUS).replace(".", ",")) + " %"
    wr = [txt((p - q) if up else (q - p)), txt(var + 1), txt(-var), txt(var * 2 if abs(var) < 50 else var / 2)]
    return Draft(f"Un prix {first}, puis {second}. Quelle est la variation globale en pourcentage ?", s, wr,
                 f"Les coefficients multiplicateurs se multiplient : {fr(float(f), 4)} soit {s}.", src=S2)


@gen(C2, "m2-set-card", cap=600, cat="Ensembles et intervalles · séries A, C, D, E, TI", diffs=(1, 2))
def set_card(rng, d):
    a, b, both = rng.randint(10, 40), rng.randint(10, 40), rng.randint(1, 9)
    union = a + b - both
    return Draft(f"Dans une classe, {a} élèves étudient l'anglais, {b} l'espagnol et {both} les deux langues. Combien étudient au moins une de ces deux langues ?", num(union),
                 [num(a + b), num(a + b + both), num(a + b - 2 * both), num(max(a, b))], f"|A ∪ B| = |A| + |B| − |A ∩ B| = {a} + {b} − {both} = {union}.", src=S2)


@gen(C2, "m2-trig-sin-from-cos", cap=600, cat="Trigonométrie · séries C, D, E, TI", diffs=(3, 4))
def sin_from_cos(rng, d):
    u, v, w = rng.choice(PYTH[:5])
    if rng.random() < 0.5:
        u, v = v, u
    assert u * u + v * v == w * w
    ask_sin = rng.random() < 0.5
    given, want = ("cos", "sin") if ask_sin else ("sin", "cos")
    ans = Fraction(v, w)
    return Draft(f"x est un angle aigu tel que {given} x = {u}/{w}. Quelle est la valeur de {want} x ?", f"{v}/{w}",
                 [f"{u}/{w}", f"{w - u}/{w}", f"{v}/{u}", f"{w - v}/{w}", f"{v * v}/{w * w}"],
                 f"cos²x + sin²x = 1 donne {want}² x = 1 − ({u}/{w})² = {v * v}/{w * w}, et {want} x > 0 car x est aigu : {v}/{w}.", src=S2)


@gen(C2, "m2-square-poly-factor", cap=600, cat="Algèbre · séries A, C, D, E, TI", diffs=(2, 3))
def factor_remarkable(rng, d):
    a, b = rng.randint(2, 9), rng.randint(2, 9)
    kind = rng.choice(["plus", "minus", "diff"])
    if kind == "plus":
        expr = f"x² + {2 * a}x + {a * a}"; right = f"(x + {a})²"; wr = [f"(x − {a})²", f"(x + {2 * a})²", f"(x + {a})(x − {a})", f"x(x + {a})²"]
    elif kind == "minus":
        expr = f"x² − {2 * a}x + {a * a}"; right = f"(x − {a})²"; wr = [f"(x + {a})²", f"(x − {2 * a})²", f"(x + {a})(x − {a})", f"x(x − {a})²"]
    else:
        expr = f"x² − {a * a}"; right = f"(x − {a})(x + {a})"; wr = [f"(x − {a})²", f"(x + {a})²", f"(x − {a * a})(x + 1)", f"x(x − {a})"]
    return Draft(f"Quelle est la forme factorisée de {expr} ?", right, wr, "On reconnaît une identité remarquable.", src=S2)


# ================================================================================================ 1re
@gen(C1, "m1-sum-product", cap=600, cat="Second degré · séries C, D, E, TI", diffs=(3, 4))
def sum_product(rng, d):
    x1, x2 = rng.randint(-9, 9), rng.randint(-9, 9)
    a = nz(rng, 1, 4)
    b, c = -a * (x1 + x2), a * x1 * x2
    ask = rng.choice(["somme", "produit"])
    val = (x1 + x2) if ask == "somme" else x1 * x2
    other = (x1 * x2) if ask == "somme" else (x1 + x2)
    for x in (x1, x2):
        assert a * x * x + b * x + c == 0
    return Draft(f"L'équation {poly([a, b, c])} = 0 a deux racines réelles. Quel est {'la somme' if ask == 'somme' else 'le produit'} de ces racines ?", num(val),
                 [num(-val), num(other), num(b if ask == "somme" else c), num(val * a)] + ints(rng, val, 3),
                 f"Pour ax² + bx + c = 0 : S = −b/a = {num(val) if ask == 'somme' else num(x1 + x2)} et P = c/a = {num(x1 * x2)}.", src=S1)


@gen(C1, "m1-parabola-extremum", cap=600, cat="Second degré · séries C, D, E, TI", diffs=(3, 4))
def parabola_extremum(rng, d):
    a, h, k = nz(rng, -4, 4), rng.randint(-6, 6), rng.randint(-9, 9)
    b, c = -2 * a * h, a * h * h + k
    kind = "minimum" if a > 0 else "maximum"
    ask_x = rng.random() < 0.5
    right = num(h if ask_x else k)
    wr = [num(-h), num(c), num(b), num(k if ask_x else h), num(-k)]
    f = lambda x: a * x * x + b * x + c
    assert f(h) == k and f(h + 1) >= k if a > 0 else f(h + 1) <= k
    return Draft(f"La fonction f(x) = {poly([a, b, c])} admet un {kind}. {'En quelle valeur de x' if ask_x else 'Quelle est la valeur'} {'est-il atteint' if ask_x else 'de cet extremum'} ?", right, wr,
                 f"Le sommet a pour abscisse −b/(2a) = {num(h)} et pour ordonnée f({num(h)}) = {num(k)}.", src=S1)


@gen(C1, "m1-tangent", cap=600, cat="Dérivation · séries C, D, E, TI", diffs=(4, 5))
def tangent(rng, d):
    deg = rng.choice([2, 3])
    co = [nz(rng, -3, 3)] + [rng.randint(-5, 5) for _ in range(deg)]
    x0 = rng.randint(-3, 3)
    m, y0 = ev(dpoly(co), x0), ev(co, x0)
    p = y0 - m * x0
    assert abs(numdiff(lambda x: ev(co, x), x0) - m) < 1e-3 * max(1, abs(m))
    right = "y = " + lin(m, p)
    wr = ["y = " + lin(m, y0), "y = " + lin(ev(co, x0) if m != y0 else m + 1, p), "y = " + lin(-m, p), "y = " + lin(m, -p), "y = " + lin(ev(dpoly(co), 0) if ev(dpoly(co), 0) != m else m + 2, p)]
    return Draft(f"Quelle est l'équation de la tangente à la courbe de f(x) = {poly(co)} au point d'abscisse {num(x0)} ?", right, wr,
                 f"f'({num(x0)}) = {num(m)} et f({num(x0)}) = {num(y0)} ; y = f'(a)(x − a) + f(a) = {lin(m, p)}.", src=S1)


@gen(C1, "m1-critical-point", cap=600, cat="Dérivation · séries C, D, E, TI", diffs=(4, 5))
def critical_point(rng, d):
    k = rng.randint(1, 7)
    a = nz(rng, 1, 3)
    # f(x) = a x^3 - 3 a k^2 x : f'(x) = 3a(x^2 - k^2) = 0 at x = ±k
    co = [a, 0, -3 * a * k * k, rng.randint(-5, 5)]
    for x in (k, -k):
        assert ev(dpoly(co), x) == 0
    return Draft(f"Pour quelles valeurs de x la dérivée de f(x) = {poly(co)} s'annule-t-elle ?", f"{num(-k)} et {num(k)}",
                 [f"0 et {num(k)}", f"{num(k * k)} et {num(-k * k)}", f"{num(k)} seulement", f"{num(-3 * k)} et {num(3 * k)}"],
                 f"f'(x) = {poly(dpoly(co))} = {num(3 * a)}(x² − {k * k}) : elle s'annule en ±{k}.", src=S1)


@gen(C1, "m1-seq-arith-find-r", cap=600, cat="Suites · séries C, D, E, TI", diffs=(2, 3))
def seq_find_r(rng, d):
    r, u0 = nz(rng, -6, 8), rng.randint(-10, 15)
    p, q = rng.randint(0, 4), rng.randint(6, 12)
    up, uq = u0 + p * r, u0 + q * r
    return Draft(f"(uₙ) est arithmétique avec u{sup(p) and ''}_{p} = {num(up)} et u_{q} = {num(uq)}. Quelle est sa raison ?".replace("u_" + str(p), f"u{p}").replace("u_" + str(q), f"u{q}"),
                 num(r), [num(-r), num(uq - up), num(Fraction(uq - up, q + p) if (uq - up) % (q + p) else r + 1), num(r + 1), num(r - 1)],
                 f"Entre les rangs {p} et {q} on ajoute {q - p} fois la raison : r = ({num(uq)} − ({num(up)}))/{q - p} = {num(r)}.", src=S1)


@gen(C1, "m1-seq-geo-term", cap=600, cat="Suites · séries C, D, E, TI", diffs=(2, 3))
def seq_geo_term(rng, d):
    u0, q, n = rng.choice([1, 2, 3, 5, -1, -2]), rng.choice([2, 3, -2, 5]), rng.randint(3, 6)
    v = u0 * q ** n
    return Draft(f"(uₙ) est géométrique de premier terme u₀ = {num(u0)} et de raison {num(q)}. Quelle est la valeur de u{sup(n) and ''}{''.join('₀₁₂₃₄₅₆₇₈₉'[int(c)] for c in str(n))} ?", num(v),
                 [num(u0 * q * n), num(u0 * q ** (n - 1)), num(u0 * q ** (n + 1)), num(u0 + n * q), num((u0 * q) ** n) if (u0 * q) ** n != v else num(v + 1)],
                 f"uₙ = u₀ × qⁿ = {num(u0)} × ({num(q)}){sup(n)} = {num(v)}.", src=S1)


@gen(C1, "m1-seq-arith-sum", cap=600, cat="Suites · séries C, D, E, TI", diffs=(3, 4))
def seq_arith_sum(rng, d):
    u1, r, n = rng.randint(-5, 12), nz(rng, -4, 6), rng.randint(6, 20)
    s = sum(u1 + i * r for i in range(n))
    assert s == n * (2 * u1 + (n - 1) * r) // 2
    return Draft(f"Quelle est la somme des {n} premiers termes de la suite arithmétique de premier terme {num(u1)} et de raison {num(r)} ?", num(s),
                 [num(n * (u1 + (n - 1) * r)), num(s + n), num(n * (2 * u1 + n * r) // 2), num((n * (2 * u1 + (n - 1) * r)) // 4), num(s - r)],
                 f"S = n(u₁ + uₙ)/2 avec uₙ = {num(u1 + (n - 1) * r)} : S = {num(s)}.", src=S1)


@gen(C1, "m1-trig-exact", cap=600, cat="Trigonométrie · séries C, D, E, TI", diffs=(2, 3))
def trig_exact(rng, d):
    table = [("cos", "π/3", "1/2", math.cos(math.pi / 3)), ("sin", "π/6", "1/2", math.sin(math.pi / 6)), ("cos", "π/6", "√3/2", math.cos(math.pi / 6)),
             ("sin", "π/3", "√3/2", math.sin(math.pi / 3)), ("cos", "π/4", "√2/2", math.cos(math.pi / 4)), ("sin", "π/4", "√2/2", math.sin(math.pi / 4)),
             ("cos", "π/2", "0", math.cos(math.pi / 2)), ("sin", "π/2", "1", math.sin(math.pi / 2)), ("cos", "π", "−1", math.cos(math.pi)),
             ("sin", "π", "0", math.sin(math.pi)), ("cos", "2π/3", "−1/2", math.cos(2 * math.pi / 3)), ("sin", "2π/3", "√3/2", math.sin(2 * math.pi / 3)),
             ("cos", "3π/4", "−√2/2", math.cos(3 * math.pi / 4)), ("sin", "5π/6", "1/2", math.sin(5 * math.pi / 6)), ("cos", "5π/6", "−√3/2", math.cos(5 * math.pi / 6)),
             ("sin", "−π/6", "−1/2", math.sin(-math.pi / 6)), ("cos", "−π/3", "1/2", math.cos(-math.pi / 3)), ("sin", "−π/2", "−1", math.sin(-math.pi / 2))]
    f, ang, txt, val = rng.choice(table)
    ev_ = {"1/2": 0.5, "√3/2": math.sqrt(3) / 2, "√2/2": math.sqrt(2) / 2, "0": 0, "1": 1, "−1": -1, "−1/2": -0.5, "−√2/2": -math.sqrt(2) / 2, "−√3/2": -math.sqrt(3) / 2}
    assert abs(ev_[txt] - val) < 1e-9
    wr = [v for v in ["1/2", "√3/2", "√2/2", "0", "1", "−1", "−1/2", "−√2/2", "−√3/2"] if v != txt]
    rng.shuffle(wr)
    return Draft(f"Quelle est la valeur exacte de {f}({ang}) ?", txt, wr[:5], "Valeur remarquable du cercle trigonométrique.", src=S1, diff=2 if ang in ("π/2", "π", "π/3", "π/6", "π/4") else 3)


@gen(C1, "m1-dot-orthogonal", cap=600, cat="Produit scalaire · séries C, D, E, TI", diffs=(3, 4))
def dot_orth(rng, d):
    a, b, c = nz(rng, -6, 6), nz(rng, -6, 6), nz(rng, -6, 6)
    # u(a, b), v(c, k) orthogonal -> a c + b k = 0 ; choose k integer
    k = rng.randint(-6, 6)
    c = -b * k
    if c == 0 and a == 0:
        return None
    # recompute: u(a,b), v(x,k) with a x + b k = 0 ; choose x = b*t, k = -a*t
    t = nz(rng, -3, 3)
    x, k = b * t, -a * t
    assert a * x + b * k == 0
    return Draft(f"Les vecteurs u({num(a)} ; {num(b)}) et v({num(x)} ; k) sont orthogonaux. Quelle est la valeur de k ?", num(k),
                 [num(-k), num(k + 1) if k + 1 != k else "1", num(Fraction(a * x, b)) if b else "0", num(a * t), num(a * x + b)],
                 f"u·v = {num(a)}×{num(x)} + {num(b)}×k = 0, donc k = {num(k)}.", src=S1)


@gen(C1, "m1-dot-value", cap=600, cat="Produit scalaire · séries C, D, E, TI", diffs=(1, 2))
def dot_value(rng, d):
    a, b, c, e = (rng.randint(-7, 7) for _ in range(4))
    v = a * c + b * e
    return Draft(f"Quel est le produit scalaire de u({num(a)} ; {num(b)}) et v({num(c)} ; {num(e)}) ?", num(v),
                 [num(a * e + b * c), num(a * c - b * e), num(a + c + b + e), num(-v)] + ints(rng, v, 3),
                 f"u·v = xx' + yy' = {num(a)}×({num(c)}) + {num(b)}×({num(e)}) = {num(v)}.", src=S1)


@gen(C1, "m1-circle-center", cap=600, cat="Géométrie analytique · séries C, D, E, TI", diffs=(3, 4))
def circle_center(rng, d):
    a, b, r = rng.randint(-6, 6), rng.randint(-6, 6), rng.randint(1, 9)
    c = a * a + b * b - r * r
    lhs = "x² + y²" + (f" {'−' if a > 0 else '+'} {abs(2 * a)}x" if a else "") + (f" {'−' if b > 0 else '+'} {abs(2 * b)}y" if b else "") + (f" {'+' if c >= 0 else '−'} {abs(c)}" if c else "")
    assert (a - a) ** 2 + (b + r - b) ** 2 == r * r
    ask = rng.choice(["centre", "rayon"])
    if ask == "centre":
        return Draft(f"Le cercle d'équation {lhs} = 0 a pour centre :", pt(a, b), [pt(-a, -b), pt(2 * a, 2 * b), pt(b, a), pt(a, -b)],
                     f"On met sous forme canonique : (x − {num(a)})² + (y − {num(b)})² = {r * r}.", src=S1)
    return Draft(f"Quel est le rayon du cercle d'équation {lhs} = 0 ?", num(r), [num(r * r), num(r + 1), num(r - 1) if r > 1 else num(r + 2), num(r * 2)],
                 f"(x − {num(a)})² + (y − {num(b)})² = {r * r}, donc R = {r}.", src=S1)


@gen(C1, "m1-proba-union", cap=600, cat="Probabilités · séries C, D, E, TI", diffs=(2, 3))
def proba_union(rng, d):
    pa, pb, pab = rng.choice([30, 40, 50, 60, 70]), rng.choice([20, 30, 40, 50]), rng.choice([5, 10, 15, 20])
    if pab > min(pa, pb) or pa + pb - pab > 100:
        return None
    u = pa + pb - pab
    return Draft(f"On a P(A) = {pa} %, P(B) = {pb} % et P(A ∩ B) = {pab} %. Quelle est P(A ∪ B) ?", f"{u} %",
                 [f"{pa + pb} %", f"{pa * pb // 100} %" if pa * pb % 100 == 0 else f"{pa + pb + pab} %", f"{u - pab} %", f"{abs(pa - pb)} %"],
                 f"P(A ∪ B) = P(A) + P(B) − P(A ∩ B) = {pa} + {pb} − {pab} = {u} %.", src=S1)


@gen(C1, "m1-stat-variance", cap=600, cat="Statistiques · séries C, D, E, TI", diffs=(3, 4))
def stat_variance(rng, d):
    m, d1, d2 = rng.randint(5, 20), rng.randint(1, 5), rng.randint(1, 5)
    if d1 == d2:
        return None
    data = [m - d1, m + d1, m - d2, m + d2]
    rng.shuffle(data)
    var = Fraction(2 * d1 * d1 + 2 * d2 * d2, 4)
    assert sum(data) == 4 * m and sum((x - m) ** 2 for x in data) / 4 == float(var)
    txt = lambda v: fr(float(v), 2, group=False).rstrip("0").rstrip(",") if "," in fr(float(v), 2, group=False) else fr(float(v), 2, group=False)
    return Draft(f"Quelle est la variance de la série {', '.join(num(x) for x in data)} ?", txt(var),
                 [txt(var * 2), txt(Fraction(2 * d1 * d1 + 2 * d2 * d2, 3)), txt(var + 1), txt(math.sqrt(var)) if math.sqrt(var) != float(var) else txt(var + 2), txt(m)],
                 f"La moyenne est {m} ; V = moyenne des carrés des écarts = ({d1 * d1}+{d1 * d1}+{d2 * d2}+{d2 * d2})/4 = {txt(var)}.", src=S1)


@gen(C1, "m1-limit-removable", cap=600, cat="Limites · séries C, D, E, TI", diffs=(4, 5))
def limit_removable(rng, d):
    a, k = rng.randint(1, 7), nz(rng, 1, 4)
    sa = rng.choice([1, -1])
    a *= sa
    # (k x^2 - k a^2)/(x - a) -> 2 k a
    f = lambda x: (k * x * x - k * a * a) / (x - a)
    assert abs(f(a + 1e-6) - 2 * k * a) < 1e-3
    num_s = poly([k, 0, -k * a * a])
    den_s = f"x {'−' if a > 0 else '+'} {abs(a)}"
    return Draft(f"Quelle est la limite de ({num_s})/({den_s}) quand x tend vers {num(a)} ?", num(2 * k * a),
                 [num(0), num(k * a), num(2 * k), num(-2 * k * a), "+∞"] if k * a != 0 else ["1", "2", "3", "4"],
                 f"On factorise : {num_s} = {num(k)}({den_s})(x {'+' if a > 0 else '−'} {abs(a)}) ; après simplification il reste {num(k)}(x {'+' if a > 0 else '−'} {abs(a)}), de limite {num(2 * k * a)}.", src=S1)


# ================================================================================================ Tle
@gen(CT, "mt-ln-express", cap=600, cat="Logarithme · séries C, D, E", diffs=(3, 4))
def ln_express(rng, d):
    i, j, k, l = rng.randint(0, 4), rng.randint(0, 4), rng.randint(0, 3), rng.randint(0, 3)
    if (i, j) == (0, 0) or (i, j) == (k, l):
        return None
    num_ = i - k, j - l
    if num_ == (0, 0):
        return None
    ln2, ln3 = math.log(2), math.log(3)
    expr_val = (2 ** i * 3 ** j) / (2 ** k * 3 ** l)
    assert abs(math.log(expr_val) - (num_[0] * ln2 + num_[1] * ln3)) < 1e-9

    def comb(p, q):
        parts = []
        for c, n in ((p, "a"), (q, "b")):
            if c == 0:
                continue
            body = n if abs(c) == 1 else f"{abs(c)}{n}"
            parts.append((MINUS if c < 0 else "+") + body)
        s = " ".join(parts)
        s = s[1:] if s.startswith("+") else s
        return s.replace("+", "+ ").replace(MINUS, MINUS) if s else "0"

    def form(p, q):
        parts = []
        for c, n in ((p, "a"), (q, "b")):
            if c == 0:
                continue
            body = n if abs(c) == 1 else f"{abs(c)}{n}"
            parts.append((c < 0, body))
        out = ""
        for idx, (neg, body) in enumerate(parts):
            out += (MINUS if neg else "") + body if idx == 0 else (" − " if neg else " + ") + body
        return out

    frac_txt = f"{2 ** i * 3 ** j}/{2 ** k * 3 ** l}" if (k or l) else str(2 ** i * 3 ** j)
    right = form(*num_)
    wr = [form(num_[0] + 1, num_[1]), form(num_[0], num_[1] + 1), form(num_[1], num_[0]), form(-num_[0], -num_[1]), form(num_[0] - 1 if num_[0] > 1 else num_[0] + 2, num_[1])]
    return Draft(f"On pose a = ln 2 et b = ln 3. Comment s'écrit ln({frac_txt}) en fonction de a et b ?", right, wr,
                 f"ln(2ⁿ3ᵐ) = n ln 2 + m ln 3 et ln(p/q) = ln p − ln q : on obtient {right}.", src=ST)


@gen(CT, "mt-ln-equation", cap=600, cat="Logarithme · séries C, D, E", diffs=(4, 5))
def ln_equation(rng, d):
    a = rng.randint(1, 5)
    p = rng.randint(a + 1, a + 7)
    b = p * (p - a)
    # ln x + ln(x - a) = ln b  <=> x(x-a) = b, x > a : roots p and a - p (< 0 rejected)
    assert p * (p - a) == b and (a - p) < a
    return Draft(f"Quelle est la solution de l'équation ln x + ln(x − {a}) = ln {b} ?", f"x = {p}",
                 [f"x = {num(a - p)}", f"x = {p} ou x = {num(a - p)}", f"x = {b}", f"x = {p + a}", f"x = {p - a}"] if p != a else ["x = 1", "x = 2", "x = 3"],
                 f"Domaine : x > {a}. L'équation devient x(x − {a}) = {b}, d'où x = {p} ou x = {num(a - p)} ; seule {p} est dans le domaine.", src=ST)


@gen(CT, "mt-ln-ineq", cap=600, cat="Logarithme · séries C, D, E", diffs=(4, 5))
def ln_ineq(rng, d):
    a, b = rng.randint(-5, 5), rng.randint(1, 9)
    inner = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    right = interval(a, a + b, False, False)
    assert math.log(a + b - 0.01 - a) < math.log(b) < math.log(b + 0.01)
    return Draft(f"Quel est l'ensemble des solutions de ln({inner}) < ln {b} ?", right,
                 [interval(None, a + b, True, False), interval(a + b, None, False, True), interval(0, b, False, False), interval(a, a + b, True, True)],
                 f"Il faut {inner} > 0 (x > {num(a)}) et {inner} < {b} (x < {num(a + b)}) : la fonction ln est strictement croissante.", src=ST)


@gen(CT, "mt-exp-ineq", cap=600, cat="Exponentielle · séries C, D, E", diffs=(3, 4))
def exp_ineq(rng, d):
    a, b, c = nz(rng, -4, 5), rng.randint(-6, 6), rng.randint(-6, 8)
    k = Fraction(c - b, a)
    right = f"x {'>' if a > 0 else '<'} {num(k)}"
    other = f"x {'<' if a > 0 else '>'} {num(k)}"
    return Draft(f"Quelle est la solution de e^({lin(a, b)}) > e^{num(c)} ?" if c >= 0 else f"Quelle est la solution de e^({lin(a, b)}) > e^({num(c)}) ?", right,
                 [other, f"x > {num(c - b)}", f"x > {num(-k)}", f"x {'>' if a < 0 else '<'} {num(-k)}"],
                 f"L'exponentielle est strictement croissante : {lin(a, b)} > {num(c)}, soit {right} ({'on divise par un négatif : le sens change' if a < 0 else 'on divise par un positif'}).", src=ST)


@gen(CT, "mt-limit-ref", cap=600, cat="Limites · séries C, D, E", diffs=(2, 3))
def limit_ref(rng, d):
    rows = [("lim_{x→+∞} eˣ/x", "+∞", lambda x: math.exp(x) / x, 1e18), ("lim_{x→+∞} x·e⁻ˣ", "0", lambda x: x * math.exp(-x), 0),
            ("lim_{x→+∞} (ln x)/x", "0", lambda x: math.log(x) / x, 0), ("lim_{x→0⁺} x·ln x", "0", lambda x: x * math.log(x), 0),
            ("lim_{x→0⁺} ln x", "−∞", lambda x: math.log(x), -1e9), ("lim_{x→−∞} eˣ", "0", lambda x: math.exp(x), 0),
            ("lim_{x→+∞} ln x", "+∞", lambda x: math.log(x), 1e9), ("lim_{x→+∞} e^(−x)", "0", lambda x: math.exp(-x), 0),
            ("lim_{x→0} (eˣ − 1)/x", "1", lambda x: (math.exp(x) - 1) / x, 1), ("lim_{x→0} ln(1 + x)/x", "1", lambda x: math.log(1 + x) / x, 1),
            ("lim_{x→+∞} x²e⁻ˣ", "0", lambda x: x * x * math.exp(-x), 0), ("lim_{x→+∞} eˣ/x²", "+∞", lambda x: math.exp(x) / x ** 2, 1e18)]
    t, r, f, expect = rng.choice(rows)
    big = "ln x" in t and "(ln" not in t and "x·ln" not in t
    x = (1e200 if big else (1e12 if "ln x)/x" in t else 50.0)) if "→+∞" in t else (1e-200 if "→0⁺" in t else (-50.0 if "→−∞" in t else 1e-6))
    if expect in (0, 1):
        assert abs(f(x) - expect) < 1e-3
    elif expect > 0:
        assert f(x) > 100
    else:
        assert f(x) < -100
    pool = [v for v in ["+∞", "−∞", "0", "1", "e"] if v != r]
    lim_var, lim_expr = t[len("lim_{x→"):].split("} ", 1)
    return Draft(f"Quelle est la limite de {lim_expr} quand x tend vers {lim_var} ?", r, pool, "Limite de référence (croissances comparées ou limite usuelle).", src=ST, diff=3)


@gen(CT, "mt-primitive-uu", cap=600, cat="Intégration · séries C, D, E", diffs=(3, 4))
def primitive_uu(rng, d):
    k, b = nz(rng, -5, 6), rng.randint(1, 8)
    right = f"{num(k)}ln|x + {b}|" if abs(k) != 1 else (f"ln|x + {b}|" if k == 1 else f"{MINUS}ln|x + {b}|")
    f = lambda x: k * math.log(abs(x + b))
    x0 = 0.9
    assert abs(numdiff(f, x0) - k / (x0 + b)) < 1e-5
    return Draft(f"Quelle est une primitive de f(x) = {num(k)}/(x + {b}) sur ]{MINUS}{b} ; +∞[ ?", right.replace("ln|", "ln|", 1),
                 [f"ln|{num(k)}x + {b}|", f"{num(k)}/(x + {b})²", f"{num(-k)}ln|x + {b}|" if k != 0 else "0", f"{num(k * b)}ln|x + {b}|" if k * b != k else f"{num(k + 1)}ln|x + {b}|", f"{num(k)}(x + {b})"],
                 "Une primitive de 1/(x + b) est ln|x + b| ; on multiplie par la constante.", src=ST)


@gen(CT, "mt-integral-exp", cap=600, cat="Intégration · séries C, D, E", diffs=(4, 5))
def integral_exp(rng, d):
    k = rng.choice([2, 3, 4, 5, -1, -2, -3])
    c = rng.choice([1, 2, 3, 5])
    # integral from 0 to 1 of c e^{kx} dx = c (e^k - 1)/k
    v = c * (math.exp(k) - 1) / k
    n = 1000
    simpson = sum(((1 if i in (0, n) else (4 if i % 2 else 2)) * c * math.exp(k * i / n)) for i in range(n + 1)) / (3 * n)
    assert abs(simpson - v) < 1e-6
    def form(cc, kk, add=-1):
        top = f"e^{kk}" if kk >= 0 else f"e^({kk})".replace("-", MINUS)
        return f"{cc}(e^{kk} − 1)/{kk}".replace("-", MINUS) if False else None
    ks = num(k)
    top = f"(e^{ks} − 1)" if k > 0 else f"(e^({ks}) − 1)"
    right = f"{c}{top}/{ks}" if c != 1 else f"{top}/{ks}"
    pre = lambda cc, kk, a: (f"{cc}" if cc != 1 else "") + (f"(e^{num(kk)} − 1)" if kk > 0 else f"(e^({num(kk)}) − 1)").replace("− 1", a) + "/" + num(kk)
    wr = [pre(c, k, "+ 1"), (f"{c}" if c != 1 else "") + (f"(e^{ks} − 1)" if k > 0 else f"(e^({ks}) − 1)"), pre(c, -k, "− 1"), f"{c}e^{ks}/{ks}" if k > 0 else f"{c}e^({ks})/{ks}"]
    return Draft(f"Quelle est la valeur de l'intégrale de 0 à 1 de {c if c != 1 else ''}e^({ks}x) dx ?", right, wr,
                 f"Une primitive de {c if c != 1 else ''}e^({ks}x) est {c if c != 1 else ''}e^({ks}x)/{ks} ; on calcule F(1) − F(0).", src=ST)


@gen(CT, "mt-complex-roots", cap=600, cat="Nombres complexes · séries C, D, E", diffs=(3, 4))
def complex_roots(rng, d):
    a, b = rng.randint(-6, 6), rng.randint(1, 7)
    # z^2 - 2a z + a^2 + b^2 = 0, roots a ± ib
    s, p = 2 * a, a * a + b * b
    for z in (complex(a, b), complex(a, -b)):
        assert abs(z * z - s * z + p) < 1e-9
    right = f"{num(a)} + {b}i et {num(a)} − {b}i" if a != 0 else f"{b}i et {MINUS}{b}i"
    wr = [f"{num(-a)} + {b}i et {num(-a)} − {b}i" if a else f"{b}i et {b}", f"{num(a)} + {b * b}i et {num(a)} − {b * b}i", f"{num(a + b)} et {num(a - b)}", f"{num(a)} + {b}i seulement"]
    return Draft(f"Quelles sont les solutions dans ℂ de z² {'−' if s > 0 else '+'} {abs(s)}z + {p} = 0 ?".replace("z² − 0z", "z²").replace(f"z² + 0z", "z²") if s else f"Quelles sont les solutions dans ℂ de z² + {p} = 0 ?",
                 right, wr, f"Δ = {num(s * s - 4 * p)} = ({2 * b}i)² ; z = ({num(s)} ± {2 * b}i)/2 = {right}.", src=ST)


@gen(CT, "mt-complex-arg", cap=600, cat="Nombres complexes · séries C, D, E", diffs=(3, 4))
def complex_arg(rng, d):
    rows = [("1 + i", "π/4"), ("1 − i", "−π/4"), ("−1 + i", "3π/4"), ("−1 − i", "−3π/4"), ("i", "π/2"), ("−i", "−π/2"), ("−1", "π"), ("1", "0"),
            ("1 + i√3", "π/3"), ("√3 + i", "π/6"), ("−1 + i√3", "2π/3"), ("√3 − i", "−π/6"), ("1 − i√3", "−π/3"), ("−√3 + i", "5π/6"), ("2 + 2i", "π/4"), ("3i", "π/2"), ("−4", "π"), ("5 − 5i", "−π/4")]
    z = {"1 + i": 1 + 1j, "1 − i": 1 - 1j, "−1 + i": -1 + 1j, "−1 − i": -1 - 1j, "i": 1j, "−i": -1j, "−1": -1, "1": 1, "1 + i√3": 1 + 3 ** .5 * 1j, "√3 + i": 3 ** .5 + 1j,
         "−1 + i√3": -1 + 3 ** .5 * 1j, "√3 − i": 3 ** .5 - 1j, "1 − i√3": 1 - 3 ** .5 * 1j, "−√3 + i": -3 ** .5 + 1j, "2 + 2i": 2 + 2j, "3i": 3j, "−4": -4, "5 − 5i": 5 - 5j}
    txt, arg = rng.choice(rows)
    num_arg = {"π/4": math.pi / 4, "−π/4": -math.pi / 4, "3π/4": 3 * math.pi / 4, "−3π/4": -3 * math.pi / 4, "π/2": math.pi / 2, "−π/2": -math.pi / 2, "π": math.pi, "0": 0,
               "π/3": math.pi / 3, "π/6": math.pi / 6, "2π/3": 2 * math.pi / 3, "−π/6": -math.pi / 6, "−π/3": -math.pi / 3, "5π/6": 5 * math.pi / 6}
    assert abs(math.atan2(z[txt].imag, z[txt].real) - num_arg[arg]) < 1e-9
    pool = [v for v in num_arg if v != arg]
    rng.shuffle(pool)
    near = [v for v in ("π/4", "π/3", "π/6", "3π/4", "2π/3", "−π/4", "π/2") if v != arg]
    return Draft(f"Quel est un argument (dans ]−π ; π]) du nombre complexe z = {txt} ?", arg, near[:5], "On compare partie réelle et partie imaginaire : tan θ = Im/Re en tenant compte du quadrant.", src=ST, diff=3)


@gen(CT, "mt-moivre", cap=600, cat="Nombres complexes · séries C, D, E", diffs=(4, 5))
def moivre(rng, d):
    n = rng.randint(2, 8)
    v = (1 + 1j) ** n
    re, im = round(v.real), round(v.imag)
    assert abs(v - complex(re, im)) < 1e-9
    def zt(r, i):
        if r == 0:
            return f"{num(i)}i" if abs(i) != 1 else ("i" if i == 1 else MINUS + "i")
        if i == 0:
            return num(r)
        return f"{num(r)} {'+' if i > 0 else '−'} {abs(i) if abs(i) != 1 else ''}i"
    return Draft(f"Quelle est la forme algébrique de (1 + i){sup(n)} ?", zt(re, im),
                 [zt(im, re), zt(-re, -im), zt(re, -im), zt(2 ** n, 0) if (re, im) != (2 ** n, 0) else zt(2 ** (n - 1), 0), zt(2 * re, im) if re else zt(1, 1)],
                 f"On calcule de proche en proche (1 + i)² = 2i ; ou en polaire : (√2)ⁿ(cos(nπ/4) + i sin(nπ/4)) = {zt(re, im)}.", src=ST)


@gen(CT, "mt-diff-eq", cap=600, cat="Équations différentielles · séries C, D, E", diffs=(3, 4))
def diff_eq(rng, d):
    k, y0 = nz(rng, -4, 5), nz(rng, -5, 6)
    f = lambda x: y0 * math.exp(k * x)
    assert abs(numdiff(f, 0.3) - k * f(0.3)) < 1e-5
    ks = num(k)
    right = f"y(x) = {num(y0)}e^({ks}x)"
    wr = [f"y(x) = {num(y0)}e^({num(-k)}x)", f"y(x) = {num(k)}e^({num(y0)}x)", f"y(x) = {num(y0 * k)}e^({ks}x)", f"y(x) = {num(y0)}e^x + {ks}"]
    return Draft(f"Quelle est la solution de l'équation différentielle y' = {ks}y avec y(0) = {num(y0)} ?", right, wr,
                 f"Les solutions de y' = ky sont y = Ce^(kx) ; la condition y(0) = {num(y0)} donne C = {num(y0)}.", src=ST)


@gen(CT, "mt-binomial-half", cap=600, cat="Probabilités · séries C, D, E", diffs=(3, 4))
def binomial_half(rng, d):
    n = rng.randint(4, 10)
    k = rng.randint(0, n)
    v = Fraction(math.comb(n, k), 2 ** n)
    wr = [Fraction(math.comb(n, k), 2 ** (n - 1)), Fraction(k, n), Fraction(1, 2 ** n), Fraction(math.comb(n, k + 1 if k < n else k - 1), 2 ** n), Fraction(math.comb(n, k), n)]
    return Draft(f"X suit la loi binomiale B({n} ; 1/2). Quelle est P(X = {k}) ?", num(v), [num(w) for w in wr],
                 f"P(X = k) = C({n},{k}) (1/2)ᵏ (1/2)ⁿ⁻ᵏ = {math.comb(n, k)}/{2 ** n}" + (f" = {num(v)}." if v.denominator != 2 ** n else "."), src=ST)


@gen(CT, "mt-total-probability", cap=600, cat="Probabilités · séries C, D, E", diffs=(4, 5))
def total_probability(rng, d):
    pa = rng.choice([20, 30, 40, 50, 60, 70])
    p1, p2 = rng.choice([10, 20, 30, 40, 50, 60, 80]), rng.choice([5, 10, 20, 30, 40, 50])
    pb = Fraction(pa * p1 + (100 - pa) * p2, 100)
    return Draft(f"Un événement A a une probabilité de {pa} %. La probabilité de B sachant A est {p1} % et celle de B sachant non-A est {p2} %. Quelle est P(B) ?",
                 f"{fr(float(pb), 1, group=False)} %",
                 [f"{fr(float(Fraction(p1 + p2, 2)), 1, group=False)} %", f"{fr(float(Fraction(pa * p1, 100)), 1, group=False)} %", f"{fr(float(pb + 5), 1, group=False)} %", f"{fr(float(Fraction(p1 * p2, 100)), 1, group=False)} %", f"{p1 + p2} %"],
                 f"Formule des probabilités totales : P(B) = P(A)P(B|A) + P(non A)P(B|non A) = {pa/100}×{p1/100} + {(100 - pa)/100}×{p2/100}.".replace(".", ","), src=ST)


@gen(CT, "mt-modular-power", cap=600, cat="Arithmétique · séries C, E", diffs=(4, 5))
def modular_power(rng, d):
    m, a, n = rng.choice([5, 7, 9, 11, 13]), rng.randint(2, 12), rng.randint(10, 60)
    if a % m == 0:
        return None
    r = pow(a, n, m)
    wr = [v for v in range(m) if v != r]
    rng.shuffle(wr)
    return Draft(f"Quel est le reste de la division euclidienne de {a}^{n} par {m} ?", str(r), [str(v) for v in wr[:5]],
                 f"On utilise les congruences modulo {m} : les puissances de {a} se répètent ; {a}^{n} ≡ {r} (mod {m}).", src=ST)


@gen(CT, "mt-modular-inverse", cap=600, cat="Arithmétique · séries C, E", diffs=(4, 5))
def modular_inverse(rng, d):
    m = rng.choice([7, 9, 11, 13, 15, 17, 19, 23, 26])
    a = rng.randint(2, m - 1)
    if math.gcd(a, m) != 1:
        return None
    x = pow(a, -1, m)
    assert (a * x) % m == 1
    wr = [v for v in range(1, m) if v != x and (a * v) % m != 1]
    rng.shuffle(wr)
    return Draft(f"Quel entier x compris entre 1 et {m - 1} vérifie {a}x ≡ 1 (mod {m}) ?", str(x), [str(v) for v in wr[:5]],
                 f"{a}×{x} = {a * x} = {(a * x) // m}×{m} + 1 : x = {x}.", src=ST)
