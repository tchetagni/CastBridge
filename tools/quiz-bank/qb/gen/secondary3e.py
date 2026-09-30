"""3e (BEPC / Troisième): algebra, geometry, statistics, probability, physics-chemistry. All answers computed and
self-checked (substitution, brute force or exact fractions). Programme: MINESEC, classe de 3e."""
import math
from fractions import Fraction

from ..core import MINUS, Draft, fr, gen, near_ints
from .mathfmt import dpoly, ev, num, nz, par, poly, poly_variants, sci, sup

C = "3e"
SRC = "Programme MINESEC 3e - réponse calculée et vérifiée par substitution"


def ints(rng, right, n=8, **kw):
    return [fr(v) for v in near_ints(rng, right, n, **kw)]


@gen(C, "3e-eq1", cap=300, cat="Algèbre")
def eq1(rng, d):
    x = rng.randint(-3 * d - 3, 4 * d + 6)
    a = nz(rng, 2, 3 + 2 * d) * rng.choice([1, 1, -1] if d > 2 else [1])
    b = rng.randint(-5 * d, 5 * d)
    c = a * x + b
    assert a * x + b == c
    eq = "%s = %s" % (poly([a, b]), num(c))
    return Draft(f"Quelle est la solution de l'équation {eq} ?", f"x = {num(x)}", [f"x = {num(v)}" for v in near_ints(rng, x, 8)] + [f"x = {num(-x)}"],
                 f"On isole x : {poly([a, 0])} = {num(c - b)}, donc x = {num(c - b)} ÷ {par(a)} = {num(x)}. Vérification : {par(a)} × {par(x)} + {par(b)} = {num(c)}.", src=SRC)


@gen(C, "3e-eq1-both", cap=300, cat="Algèbre")
def eq1_both(rng, d):
    x = rng.randint(-4 - d, 6 + 2 * d)
    a, c = nz(rng, 2, 5 + d), nz(rng, -4 - d, 4 + d)
    if a == c:
        return None
    b = rng.randint(-8 - 3 * d, 8 + 3 * d)
    dd = a * x + b - c * x
    assert a * x + b == c * x + dd
    return Draft(f"Quelle est la solution de l'équation {poly([a, b])} = {poly([c, dd])} ?", f"x = {num(x)}", [f"x = {num(v)}" for v in near_ints(rng, x, 8)] + [f"x = {num(-x)}"],
                 f"On regroupe les x d'un côté : {poly([a - c, 0])} = {num(dd - b)}, donc x = {num(dd - b)} ÷ {par(a - c)} = {num(x)}.", src=SRC)


@gen(C, "3e-system", cap=300, cat="Algèbre")
def system(rng, d):
    x, y = rng.randint(-5 - d, 8 + d), rng.randint(-5 - d, 8 + d)
    a1, b1 = nz(rng, 1, 3 + d), nz(rng, -3 - d, 3 + d)
    a2, b2 = nz(rng, -3 - d, 3 + d), nz(rng, 1, 3 + d)
    if a1 * b2 - a2 * b1 == 0:
        return None
    c1, c2 = a1 * x + b1 * y, a2 * x + b2 * y
    wrong = [(x + 1, y), (x, y + 1), (y, x), (-x, -y), (x - 1, y + 1), (x + 2, y - 1)]
    wrong = [w for w in wrong if w != (x, y)]
    f = lambda p: f"x = {num(p[0])} et y = {num(p[1])}"
    s1 = "%s = %s" % (poly([a1, 0], "x").replace("x", "x") + (" + " if b1 > 0 else " − ") + (("" if abs(b1) == 1 else str(abs(b1))) + "y"), num(c1))
    s2 = "%s = %s" % (poly([a2, 0], "x") + (" + " if b2 > 0 else " − ") + (("" if abs(b2) == 1 else str(abs(b2))) + "y"), num(c2))
    return Draft(f"Quelle est la solution du système {s1} et {s2} ?", f(( x, y)), [f(w) for w in wrong],
                 f"En combinant les équations (substitution ou addition) on trouve x = {num(x)} et y = {num(y)} ; vérification : {par(a1)} × {par(x)} + {par(b1)} × {par(y)} = {num(c1)}.", src=SRC)


@gen(C, "3e-expand-square", cap=240, cat="Algèbre")
def expand_square(rng, d):
    a = nz(rng, 1, 4 + 2 * d)
    sgn = rng.choice([1, -1])
    k = rng.choice([1, 1, 2, 3]) if d > 2 else 1
    # (kx + sgn*a)^2 = k²x² + 2 k sgn a x + a²
    right = [k * k, 2 * k * sgn * a, a * a]
    base = poly([k, sgn * a])
    for x in (2, -3, 5):
        assert (k * x + sgn * a) ** 2 == ev(right, x)
    wrongs = [poly([k * k, 0, a * a]), poly([k * k, k * sgn * a, a * a]), poly([k * k, 2 * k * sgn * a, -a * a]), poly([k * k, -2 * k * sgn * a, a * a]), poly([k, 2 * sgn * a, a * a])]
    return Draft(f"Quelle est la forme développée de ({base})² ?", poly(right), wrongs, f"({base})² = ({poly([k, 0])})² + 2×({poly([k, 0])})×({num(sgn * a)}) + ({num(sgn * a)})² = {poly(right)}.", src=SRC)


@gen(C, "3e-expand-product", cap=240, cat="Algèbre")
def expand_product(rng, d):
    a, b = nz(rng, -6 - d, 6 + d), nz(rng, -6 - d, 6 + d)
    if d > 2:
        p, q = nz(rng, 1, 3), nz(rng, 1, 3)
    else:
        p = q = 1
    right = [p * q, p * b + q * a, a * b]
    for x in (1, -2, 4):
        assert (p * x + a) * (q * x + b) == ev(right, x)
    wrongs = [poly([p * q, p * b + q * a, -a * b]), poly([p * q, a + b, a * b]), poly([p * q, p * b - q * a, a * b]), poly([p * q, p * b + q * a, a + b])]
    return Draft(f"Quelle est la forme développée de ({poly([p, a])})({poly([q, b])}) ?", poly(right), wrongs, f"On distribue : {poly(right)}.", src=SRC)


@gen(C, "3e-difference-squares", cap=200, cat="Algèbre")
def diff_squares(rng, d):
    a = rng.randint(2, 6 + 2 * d)
    k = rng.choice([1, 1, 2, 3, 4])
    right = [k * k, 0, -a * a]
    for x in (1, 3, -2):
        assert (k * x - a) * (k * x + a) == ev(right, x)
    return Draft(f"Quelle est la forme factorisée de {poly(right)} ?", f"({poly([k, -a])})({poly([k, a])})",
                 [f"({poly([k, -a])})²", f"({poly([k, a])})²", f"({poly([k, -a])})({poly([k, -a])})", f"({poly([k * k, -a])})({poly([1, a])})"],
                 f"{poly(right)} = ({poly([k, 0])})² − {a}² = ({poly([k, -a])})({poly([k, a])}) (identité a² − b²).", src=SRC)


@gen(C, "3e-factor-common", cap=200, cat="Algèbre")
def factor_common(rng, d):
    k = rng.randint(2, 4 + d)
    a, b = nz(rng, 1, 6), nz(rng, -6, 6)
    if math.gcd(a, abs(b)) != 1:
        return None
    expr = [k * a, k * b]
    return Draft(f"Quelle est la factorisation de {poly(expr)} par le plus grand facteur commun ?", f"{k}({poly([a, b])})",
                 [f"{k * a}({poly([1, b // math.gcd(k * a, abs(k * b)) if False else b])})", f"{a}({poly([k, k * b // a if (k * b) % a == 0 else b])})", f"{k}({poly([a, -b])})", f"({poly([k, a])})({poly([1, b])})"],
                 f"Le plus grand facteur commun de {k * a} et {abs(k * b)} est {k}: {poly(expr)} = {k}({poly([a, b])}).", src=SRC)


@gen(C, "3e-pythagoras-hyp", cap=240, cat="Géométrie")
def pyth_hyp(rng, d):
    a, b, c = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25), (20, 21, 29), (9, 40, 41)][: 2 + d])
    k = rng.randint(1, 4 + d)
    a, b, c = a * k, b * k, c * k
    assert a * a + b * b == c * c
    return Draft(f"Un triangle ABC est rectangle en A avec AB = {a} cm et AC = {b} cm. Quelle est la longueur de l'hypoténuse BC ?", f"{c} cm",
                 [f"{x} cm" for x in near_ints(rng, c, 8, lo=1)] + [f"{a + b} cm"], f"BC² = AB² + AC² = {a}² + {b}² = {a * a + b * b} = {c}², donc BC = {c} cm.", src=SRC)


@gen(C, "3e-pythagoras-side", cap=240, cat="Géométrie")
def pyth_side(rng, d):
    a, b, c = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25), (20, 21, 29), (9, 40, 41)][: 2 + d])
    k = rng.randint(1, 4 + d)
    a, b, c = a * k, b * k, c * k
    return Draft(f"Un triangle est rectangle avec une hypoténuse de {c} cm et un côté de l'angle droit de {a} cm. Quelle est la longueur de l'autre côté ?", f"{b} cm",
                 [f"{x} cm" for x in near_ints(rng, b, 8, lo=1)] + [f"{c - a} cm"], f"{c}² − {a}² = {c * c - a * a} = {b}², donc le côté mesure {b} cm.", src=SRC)


@gen(C, "3e-pythagoras-sqrt", cap=200, cat="Géométrie")
def pyth_sqrt(rng, d):
    a, b = rng.randint(1, 9), rng.randint(1, 9)
    s = a * a + b * b
    if int(math.isqrt(s)) ** 2 == s:
        return None
    return Draft(f"Un triangle est rectangle avec des côtés de l'angle droit de {a} cm et {b} cm. Quelle est la valeur exacte de l'hypoténuse ?", f"√{s} cm",
                 [f"√{s + 1} cm", f"{a + b} cm", f"√{a + b} cm", f"{s} cm"], f"h² = {a}² + {b}² = {s}, donc h = √{s} cm (valeur exacte).", src=SRC)


@gen(C, "3e-thales", cap=240, cat="Géométrie")
def thales(rng, d):
    am = rng.randint(2, 8)
    ab = am + rng.randint(2, 8)
    k = Fraction(ab, am)
    ac = int(k.denominator * rng.randint(1, 4))
    an = Fraction(ac) / k
    if an.denominator != 1:
        return None
    an = int(an)
    if an == ac:
        return None
    assert Fraction(am, ab) == Fraction(an, ac)
    return Draft(f"Dans le triangle ABC, M est sur [AB] et N sur [AC] avec (MN) // (BC). On a AM = {am} cm, AB = {ab} cm et AC = {ac} cm. Quelle est la longueur AN ?", f"{an} cm",
                 [f"{x} cm" for x in near_ints(rng, an, 8, lo=1)] + [f"{ab * ac // am if (ab * ac) % am == 0 else ac + 1} cm"],
                 f"Thalès : AM/AB = AN/AC, donc AN = AM × AC ÷ AB = {am} × {ac} ÷ {ab} = {an} cm.", src=SRC)


TRIPLES = [(3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25), (20, 21, 29)]


@gen(C, "3e-trig-ratio", cap=200, cat="Géométrie")
def trig_ratio(rng, d):
    a, b, c = rng.choice(TRIPLES[: 2 + d // 2 + 1])
    fn = rng.choice(["sin", "cos", "tan"])
    adj, opp = (a, b) if rng.random() < 0.5 else (b, a)
    val = {"sin": Fraction(opp, c), "cos": Fraction(adj, c), "tan": Fraction(opp, adj)}[fn]
    allv = {Fraction(opp, c), Fraction(adj, c), Fraction(opp, adj), Fraction(adj, opp), Fraction(c, adj), Fraction(c, opp)} - {val}
    return Draft(f"ABC est rectangle en A, AB = {adj}, AC = {opp}, BC = {c}. Quelle est la valeur de {fn} B̂ (écrite en fraction irréductible) ?", num(val), [num(v) for v in sorted(allv)],
                 {"sin": "sin = côté opposé ÷ hypoténuse", "cos": "cos = côté adjacent ÷ hypoténuse", "tan": "tan = côté opposé ÷ côté adjacent"}[fn] + f" ; ici côté opposé à B = AC = {opp}, adjacent = AB = {adj}.", src=SRC)


@gen(C, "3e-sqrt-simplify", cap=200, cat="Algèbre")
def sqrt_simplify(rng, d):
    a = rng.randint(2, 3 + 2 * d)
    b = rng.choice([2, 3, 5, 6, 7, 10, 11])
    n = a * a * b
    assert math.isqrt(n) ** 2 != n
    return Draft(f"Quelle écriture a√b, avec b le plus petit possible, est égale à √{n} ?", f"{a}√{b}", [f"{a + 1}√{b}", f"{a}√{b + 1}", f"{b}√{a}", f"√{a}√{b}", f"{a * b}√{a}"][:5],
                 f"{n} = {a * a} × {b}, donc √{n} = √{a * a} × √{b} = {a}√{b}.", src=SRC)


@gen(C, "3e-powers", cap=260, cat="Algèbre")
def powers(rng, d):
    a = rng.choice([2, 3, 5, 10, 7])
    m, n = rng.randint(2, 4 + d), rng.randint(2, 4 + d)
    kind = rng.choice(["mul", "div", "pow"])
    if kind == "mul":
        e, right, expr = m + n, m + n, f"{a}{sup(m)} × {a}{sup(n)}"
    elif kind == "div":
        if m <= n:
            m, n = n + 1, m
        e, expr = m - n, f"{a}{sup(m)} ÷ {a}{sup(n)}"
    else:
        e, expr = m * n, f"({a}{sup(m)}){sup(n)}"
    right = f"{a}{sup(e)}"
    wrongs = [f"{a}{sup(m + n if kind != 'mul' else m * n)}", f"{a}{sup(abs(m - n) if kind == 'mul' else m + n)}", f"{a * 2 if a < 6 else a + 1}{sup(e)}", f"{a}{sup(e + 1)}", f"{a}{sup(e - 1)}"]
    return Draft(f"Quelle écriture en une seule puissance correspond à {expr} ?", right, wrongs,
                 {"mul": "aᵐ × aⁿ = aᵐ⁺ⁿ", "div": "aᵐ ÷ aⁿ = aᵐ⁻ⁿ", "pow": "(aᵐ)ⁿ = aᵐˣⁿ"}[kind] + f" : on obtient {a}{sup(e)}.", src=SRC)


@gen(C, "3e-scientific", cap=200, cat="Algèbre")
def scientific(rng, d):
    m = round(rng.uniform(1, 9.99), 2)
    e = rng.choice([-6, -5, -4, -3, -2, 2, 3, 4, 5, 6, 7, 8])
    x = m * 10 ** e
    text = fr(x, 10) if e < 0 else fr(round(x))
    right = "%s × 10%s" % (fr(m, 2), sup(e))
    wrongs = ["%s × 10%s" % (fr(m, 2), sup(e + 1)), "%s × 10%s" % (fr(m, 2), sup(e - 1)), "%s × 10%s" % (fr(round(m * 10, 1), 1), sup(e)), "%s × 10%s" % (fr(round(m / 10, 3), 3), sup(e))]
    return Draft(f"Quelle est l'écriture scientifique de {text} ?", right, wrongs, f"On écrit un nombre entre 1 et 10 multiplié par une puissance de 10 : {right}.", src=SRC)


@gen(C, "3e-affine-value", cap=220, cat="Fonctions")
def affine_value(rng, d):
    a, b, x = nz(rng, -5 - d, 6 + d), rng.randint(-9, 9), rng.randint(-5 - d, 6 + d)
    y = a * x + b
    return Draft(f"Soit f(x) = {poly([a, b])}. Quelle est la valeur de f({num(x)}) ?", num(y), [num(v) for v in near_ints(rng, y, 8)] + [num(a * x - b), num(a + x + b)],
                 f"f({num(x)}) = {num(a)} × ({num(x)}) + ({num(b)}) = {num(y)}.", src=SRC)


@gen(C, "3e-affine-slope", cap=220, cat="Fonctions")
def affine_slope(rng, d):
    a, b = nz(rng, -5 - d, 6 + d), rng.randint(-9, 9)
    x1, x2 = rng.randint(-4, 2), rng.randint(3, 8)
    y1, y2 = a * x1 + b, a * x2 + b
    return Draft(f"La droite passe par A({num(x1)} ; {num(y1)}) et B({num(x2)} ; {num(y2)}). Quel est son coefficient directeur ?", num(a),
                 [num(v) for v in near_ints(rng, a, 8)] + [num(-a), num(y2 - y1)], f"a = (yB − yA) ÷ (xB − xA) = ({num(y2)} − ({num(y1)})) ÷ ({num(x2)} − ({num(x1)})) = {num(a)}.", src=SRC)


@gen(C, "3e-affine-antecedent", cap=200, cat="Fonctions")
def affine_antecedent(rng, d):
    a, b, x = nz(rng, 2, 5 + d) * rng.choice([1, -1]), rng.randint(-9, 9), rng.randint(-5, 8)
    y = a * x + b
    return Draft(f"Soit f(x) = {poly([a, b])}. Quel est l'antécédent de {num(y)} par f ?", num(x), [num(v) for v in near_ints(rng, x, 8)] + [num(-x)],
                 f"On résout {poly([a, b])} = {num(y)} : x = ({num(y)} − ({num(b)})) ÷ {num(a)} = {num(x)}.", src=SRC)


@gen(C, "3e-percent-increase", cap=220, cat="Nombres")
def percent_inc(rng, d):
    p = rng.choice([5, 10, 12, 15, 20, 25, 30, 40])
    base = rng.choice([2000, 4000, 5000, 8000, 10000, 12000, 15000, 20000, 25000, 40000])
    up = rng.random() < 0.5
    r = base * (100 + p if up else 100 - p) // 100
    if base * (100 + p if up else 100 - p) % 100:
        return None
    return Draft(f"Un prix de {fr(base)} FCFA {'augmente' if up else 'baisse'} de {p} %. Quel est le nouveau prix ?", f"{fr(r)} FCFA",
                 [f"{fr(v)} FCFA" for v in (base * p // 100, base * (100 - p if up else 100 + p) // 100, r + base // 100 * 5, base + p)] ,
                 f"{fr(base)} × {fr((100 + p if up else 100 - p) / 100, 2)} = {fr(r)} FCFA.", src=SRC)


@gen(C, "3e-tva", cap=200, cat="Nombres")
def tva(rng, d):
    ht = rng.choice([10000, 20000, 25000, 40000, 50000, 80000, 100000, 120000, 200000])
    ttc = ht * 19.25 / 100 + ht
    right = f"{fr(ttc, 2)} FCFA" if ttc != int(ttc) else f"{fr(int(ttc))} FCFA"
    tax = ht * 0.1925
    return Draft(f"Au Cameroun, la TVA (avec centimes additionnels) est de 19,25 %. Un article coûte {fr(ht)} FCFA hors taxes. Quel est son prix toutes taxes comprises ?", right,
                 [f"{fr(ht + ht * 0.175, 2)} FCFA", f"{fr(ht * 1.1925 + 100, 2)} FCFA", f"{fr(ht + tax / 2, 2)} FCFA", f"{fr(ht * 0.8075, 2)} FCFA"],
                 f"Taxe : {fr(ht)} × 0,1925 = {fr(tax, 2)} FCFA ; prix TTC = {fr(ht)} + {fr(tax, 2)} = {fr(ttc, 2)} FCFA.", src="Taux de TVA au Cameroun : 17,5 % + 10 % de centimes additionnels = 19,25 % (Code général des impôts) - à vérifier", region="CM")


def median(v):
    s = sorted(v)
    n = len(s)
    return s[n // 2] if n % 2 else Fraction(s[n // 2 - 1] + s[n // 2], 2)


@gen(C, "3e-stats-mean", cap=220, cat="Statistiques")
def stats_mean(rng, d):
    n = rng.choice([5, 6, 7, 8])
    vals = [rng.randint(2, 20) for _ in range(n)]
    tot = sum(vals)
    m = Fraction(tot, n)
    if m.denominator not in (1, 2):
        vals[-1] += (-tot) % n if vals[-1] + ((-tot) % n) <= 20 else -(tot % n)
        tot = sum(vals)
        m = Fraction(tot, n)
        if m.denominator != 1 or min(vals) < 0 or max(vals) > 20:
            return None
    return Draft(f"Quelle est la moyenne de la série {', '.join(map(str, vals))} ?", num(m), [num(m + k) for k in (1, -1, 2, -2)] + [fr(float(m) + 0.5, 1), fr(float(median(vals)), 1)],
                 f"({' + '.join(map(str, vals))}) ÷ {n} = {tot} ÷ {n} = {num(m)}.", src=SRC)


@gen(C, "3e-stats-median", cap=220, cat="Statistiques")
def stats_median(rng, d):
    n = rng.choice([5, 7, 9, 6, 8])
    vals = [rng.randint(1, 30) for _ in range(n)]
    med = median(vals)
    wrongs = [Fraction(sum(vals), n), Fraction(max(vals) - min(vals)), Fraction(sorted(vals)[n // 2 + 1 if n // 2 + 1 < n else 0])]
    wrongs = [w for w in wrongs if w != med]
    shown = vals[:]
    rng.shuffle(shown)
    wr = [num(w) if w.denominator == 1 else f"{float(w):.1f}".replace(".", ",") for w in wrongs]
    right = num(med) if med.denominator == 1 else f"{float(med):.1f}".replace(".", ",")
    return Draft(f"Quelle est la médiane de la série {', '.join(map(str, shown))} ?", right, wr + [str(min(vals)), str(sorted(vals)[0] + 1)],
                 f"On range : {', '.join(map(str, sorted(vals)))}. La valeur du milieu (ou la moyenne des deux du milieu) est {right}.", src=SRC)


@gen(C, "3e-stats-range", cap=180, cat="Statistiques")
def stats_range(rng, d):
    vals = [rng.randint(1, 50) for _ in range(rng.choice([5, 6, 7]))]
    r = max(vals) - min(vals)
    if r == 0:
        return None
    return Draft(f"Quelle est l'étendue de la série {', '.join(map(str, vals))} ?", str(r), [str(v) for v in near_ints(rng, r, 8, lo=1)] + [str(max(vals))],
                 f"Étendue = plus grande valeur − plus petite valeur = {max(vals)} − {min(vals)} = {r}.", src=SRC)


@gen(C, "3e-proba-urn", cap=220, cat="Probabilités")
def proba_urn(rng, d):
    r, b, v = rng.randint(1, 8), rng.randint(1, 8), rng.randint(0, 6)
    tot = r + b + v
    col = rng.choice(["rouge", "bleue", "verte"] if v else ["rouge", "bleue"])
    k = {"rouge": r, "bleue": b, "verte": v}[col]
    if k == 0:
        return None
    p = Fraction(k, tot)
    others = {Fraction(tot - k, tot), Fraction(k, tot - k) if tot - k else Fraction(1), Fraction(1, tot), Fraction(k + 1, tot), Fraction(k, tot + 1)}
    others.discard(p)
    desc = f"{r} boules rouges, {b} boules bleues" + (f" et {v} boules vertes" if v else "")
    return Draft(f"Une urne contient {desc}. On tire une boule au hasard. Quelle est la probabilité d'obtenir une boule {col} ?", num(p), [num(o) for o in sorted(others)],
                 f"Probabilité = cas favorables ÷ cas possibles = {k}/{tot}" + ("" if p.denominator == tot else f" = {num(p)}") + ".", src=SRC)


@gen(C, "3e-proba-dice", cap=120, cat="Probabilités")
def proba_dice(rng, d):
    kind = rng.choice(["pair", "multiple", "sup", "prem"])
    if kind == "pair":
        fav, txt = 3, "un nombre pair"
    elif kind == "multiple":
        k = rng.choice([2, 3])
        fav, txt = 6 // k, f"un multiple de {k}"
    elif kind == "sup":
        k = rng.randint(1, 5)
        fav, txt = 6 - k, f"un nombre strictement supérieur à {k}"
    else:
        fav, txt = 3, "un nombre premier"
    p = Fraction(fav, 6)
    return Draft(f"On lance un dé équilibré à 6 faces. Quelle est la probabilité d'obtenir {txt} ?", num(p),
                 [num(o) for o in {Fraction(1, 6), Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(5, 6)} - {p}], f"{fav} cas favorables sur 6 : {num(p)}.", src=SRC)


@gen(C, "3e-cylinder", cap=180, cat="Géométrie")
def cylinder(rng, d):
    r, h = rng.randint(2, 9), rng.randint(2, 15)
    kind = rng.choice(["cylindre", "cône", "sphère"])
    if kind == "cylindre":
        v, expr = r * r * h, f"π × {r}² × {h}"
        txt = f"Quel est le volume (en cm³, en fonction de π) d'un cylindre de rayon {r} cm et de hauteur {h} cm ?"
        wrong = [f"{v // 3 if v % 3 == 0 else v + 1}π cm³", f"{2 * r * h}π cm³", f"{(r * h) ** 2}π cm³", f"{v * 4 // 3 if (v * 4) % 3 == 0 else v + 2}π cm³"]
        right = f"{v}π cm³"
    elif kind == "cône":
        h = 3 * rng.randint(1, 6)
        v = r * r * h // 3
        txt = f"Quel est le volume (en cm³, en fonction de π) d'un cône de rayon {r} cm et de hauteur {h} cm ?"
        wrong = [f"{r * r * h}π cm³", f"{r * r * h // 2 if (r * r * h) % 2 == 0 else r * r * h + 1}π cm³", f"{v + r}π cm³", f"{2 * v}π cm³"]
        right = f"{v}π cm³"
    else:
        r = 3 * rng.randint(1, 4)
        v = 4 * r ** 3 // 3
        txt = f"Quel est le volume (en cm³, en fonction de π) d'une sphère de rayon {r} cm ?"
        wrong = [f"{4 * r * r}π cm³", f"{r ** 3}π cm³", f"{v // 4}π cm³", f"{2 * v}π cm³"]
        right = f"{v}π cm³"
    return Draft(txt, right, wrong, {"cylindre": "V = π r² h", "cône": "V = (1/3) π r² h", "sphère": "V = (4/3) π r³"}[kind] + f" = {right}.", src=SRC)


@gen(C, "3e-circle", cap=120, cat="Géométrie")
def circle(rng, d):
    r = rng.randint(2, 15 + 5 * d)
    if rng.random() < 0.5:
        return Draft(f"Quel est le périmètre d'un cercle de rayon {r} cm (en fonction de π) ?", f"{2 * r}π cm", [f"{r}π cm", f"{r * r}π cm", f"{4 * r}π cm", f"{r * r * 2}π cm"], f"P = 2πr = {2 * r}π cm.", src=SRC)
    return Draft(f"Quelle est l'aire d'un disque de rayon {r} cm (en fonction de π) ?", f"{r * r}π cm²", [f"{2 * r}π cm²", f"{r}π cm²", f"{2 * r * r}π cm²", f"{4 * r * r}π cm²"], f"A = πr² = {r * r}π cm².", src=SRC)


@gen(C, "3e-pgcd-ppcm", cap=220, cat="Nombres")
def pgcd_ppcm(rng, d):
    g = rng.choice([2, 3, 4, 5, 6, 7, 8, 9, 12])
    a, b = g * rng.randint(2, 9), g * rng.randint(2, 9)
    if math.gcd(a, b) != g or a == b:
        return None
    if rng.random() < 0.5:
        return Draft(f"Quel est le PGCD de {a} et {b} ?", str(g), [str(v) for v in near_ints(rng, g, 8, lo=1)] + [str(a * b // g)], f"Les deux nombres sont multiples de {g} et on ne peut pas aller plus loin : PGCD({a} ; {b}) = {g}.", src=SRC)
    l = a * b // g
    return Draft(f"Quel est le PPCM de {a} et {b} ?", str(l), [str(v) for v in near_ints(rng, l, 8, lo=1)] + [str(a * b), str(g)], f"PPCM = {a} × {b} ÷ PGCD = {a * b} ÷ {g} = {l}.", src=SRC)


@gen(C, "3e-inequation", cap=200, cat="Algèbre")
def inequation(rng, d):
    a = nz(rng, 2, 4 + d) * rng.choice([1, -1])
    x0 = rng.randint(-5, 8)
    b = rng.randint(-8, 8)
    c = a * x0 + b
    op = rng.choice(["<", ">", "≤", "≥"])
    flip = a < 0
    res = {"<": ">", ">": "<", "≤": "≥", "≥": "≤"}[op] if flip else op
    right = f"x {res} {num(x0)}"
    flipped = {"<": ">", ">": "<", "≤": "≥", "≥": "≤"}[res]
    return Draft(f"Quelle est la solution de l'inéquation {poly([a, b])} {op} {num(c)} ?", right,
                 [f"x {flipped} {num(x0)}", f"x {res} {num(-x0)}", f"x {flipped} {num(-x0)}", f"x {res} {num(x0 + 1)}"],
                 f"On isole x : {num(a)}x {op} {num(c - b)}" + (" ; on divise par un nombre négatif donc le sens change" if flip else "") + f" : {right}.", src=SRC)


@gen(C, "3e-kmh-ms", cap=160, cat="Physique-Chimie")
def kmh_ms(rng, d):
    v = rng.choice([18, 36, 54, 72, 90, 108, 126, 144, 180])
    return Draft(f"À combien de m/s correspondent {v} km/h ?", f"{fr(v // 3.6 if v % 18 else v / 3.6, 1)} m/s" if v % 36 else f"{v // 36 * 10} m/s",
                 [f"{fr(v * 3.6, 1)} m/s", f"{fr(v / 6, 1)} m/s", f"{fr(v / 36, 1)} m/s", f"{fr(v + 3.6, 1)} m/s"], f"1 km/h = 1 000 m ÷ 3 600 s = 1/3,6 m/s : {v} ÷ 3,6 = {fr(v / 3.6, 1)} m/s.", src=SRC) if v % 18 == 0 else None


@gen(C, "3e-polygon-angles", cap=100, cat="Géométrie")
def polygon_angles(rng, d):
    n = rng.randint(5, 14)
    if rng.random() < 0.5:
        s = (n - 2) * 180
        return Draft(f"Quelle est la somme des angles intérieurs d'un polygone convexe à {n} côtés ?", f"{s}°", [f"{x}°" for x in ((n - 1) * 180, (n - 3) * 180, n * 180, 360)], f"(n − 2) × 180° = {n - 2} × 180° = {s}°.", src=SRC)
    if 360 % n:
        return None
    return Draft(f"Quelle est la mesure de chaque angle au centre d'un polygone régulier à {n} côtés ?", f"{360 // n}°", [f"{x}°" for x in (360 // n + 10, 180 - 360 // n, 360 // (n + 1), 180 // n)],
                 f"360° ÷ {n} = {360 // n}°.", src=SRC)


@gen(C, "3e-inscribed-angle", cap=120, cat="Géométrie")
def inscribed(rng, d):
    a = rng.randint(10, 85)
    return Draft(f"Dans un cercle, un angle inscrit intercepte le même arc qu'un angle au centre de {2 * a}°. Quelle est la mesure de l'angle inscrit ?", f"{a}°", [f"{x}°" for x in (2 * a, 4 * a if 4 * a < 360 else 3 * a, 180 - a, a + 10)],
                 f"L'angle inscrit vaut la moitié de l'angle au centre : {2 * a} ÷ 2 = {a}°.", src=SRC)


@gen(C, "3e-product-zero", cap=160, cat="Algèbre")
def product_zero(rng, d):
    a, b = nz(rng, -8, 8), nz(rng, -8, 8)
    if a == b:
        return None
    e = f"({poly([1, -a])})({poly([1, -b])}) = 0"
    right = "{" + "; ".join(num(v) for v in sorted({a, b})) + "}"
    wrongs = ["{" + "; ".join(num(v) for v in sorted({-a, -b})) + "}", "{" + "; ".join(num(v) for v in sorted({a, -b})) + "}", "{" + num(a * b) + "}", "{" + "; ".join(num(v) for v in sorted({a + b, 0})) + "}"]
    return Draft(f"Quel est l'ensemble des solutions de l'équation {e} ?", right, wrongs, f"Un produit est nul si l'un des facteurs est nul : x = {num(a)} ou x = {num(b)}.", src=SRC)


# -------------------------------------------------------------------------------------- physique-chimie
PC = "Programme MINESEC 3e, physique-chimie - réponse calculée"


@gen(C, "3e-ohm-u", cap=220, cat="Physique-Chimie", source=PC)
def ohm_u(rng, d):
    r, i = rng.choice([10, 20, 22, 47, 50, 100, 150, 220, 330, 470, 1000]), rng.choice([0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 1.5, 0.25])
    u = round(r * i, 4)
    return Draft(f"Un conducteur ohmique de résistance {fr(r)} Ω est traversé par un courant de {fr(i, 3)} A. Quelle est la tension à ses bornes ?", f"{fr(u, 3)} V",
                 [f"{fr(x, 3)} V" for x in (r / i if i else 1, r + i, u * 10, u / 10)] , f"Loi d'Ohm : U = R × I = {fr(r)} × {fr(i, 3)} = {fr(u, 3)} V.", src=PC)


@gen(C, "3e-ohm-r", cap=220, cat="Physique-Chimie", source=PC)
def ohm_r(rng, d):
    r, i = rng.choice([5, 10, 12, 20, 25, 40, 50, 100, 200, 500]), rng.choice([0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2])
    u = r * i
    return Draft(f"Un dipôle soumis à une tension de {fr(u, 3)} V est traversé par un courant de {fr(i, 3)} A. Quelle est sa résistance ?", f"{fr(r)} Ω",
                 [f"{fr(x, 3)} Ω" for x in (u * i, u + i, r * 10, r / 10, i / u)], f"R = U ÷ I = {fr(u, 3)} ÷ {fr(i, 3)} = {fr(r)} Ω.", src=PC)


@gen(C, "3e-power-ui", cap=200, cat="Physique-Chimie", source=PC)
def power_ui(rng, d):
    u, i = rng.choice([6, 12, 24, 110, 220, 230]), rng.choice([0.1, 0.25, 0.5, 1, 2, 5, 10, 0.2])
    p = u * i
    return Draft(f"Un appareil fonctionne sous {u} V avec un courant de {fr(i, 3)} A. Quelle est sa puissance ?", f"{fr(p, 3)} W",
                 [f"{fr(x, 3)} W" for x in (u / i, u + i, p * 10, p / 10)], f"P = U × I = {u} × {fr(i, 3)} = {fr(p, 3)} W.", src=PC)


@gen(C, "3e-energy", cap=220, cat="Physique-Chimie", source=PC)
def energy(rng, d):
    p, h = rng.choice([5, 9, 11, 15, 20, 40, 60, 75, 100, 150, 1000, 1500]), rng.choice([0.5, 1, 2, 3, 4, 5, 8, 10, 12, 24])
    e = p * h
    return Draft(f"Une lampe de {p} W fonctionne pendant {fr(h, 1)} h. Quelle énergie électrique consomme-t-elle (en Wh) ?", f"{fr(e, 2)} Wh", [f"{fr(x, 2)} Wh" for x in (p / h, p + h, e * 10, e / 10, e * 3.6)],
                 f"E = P × t = {p} × {fr(h, 1)} = {fr(e, 2)} Wh.", src=PC)


@gen(C, "3e-density", cap=220, cat="Physique-Chimie", source=PC)
def density(rng, d):
    rho = rng.choice([0.8, 1.0, 2.7, 7.8, 8.9, 11.3, 13.6, 19.3, 0.9, 1.2])
    v = rng.choice([5, 10, 20, 25, 40, 50, 100, 200])
    m = round(rho * v, 3)
    return Draft(f"Un objet de {fr(m, 3)} g a un volume de {v} cm³. Quelle est sa masse volumique ?", f"{fr(rho, 2)} g/cm³", [f"{fr(x, 2)} g/cm³" for x in (v / m, m + v, rho * 10, rho / 10, rho + 1)],
                 f"ρ = m ÷ V = {fr(m, 3)} ÷ {v} = {fr(rho, 2)} g/cm³.", src=PC)


@gen(C, "3e-weight", cap=200, cat="Physique-Chimie", source=PC)
def weight(rng, d):
    m = rng.choice([0.5, 1, 2, 2.5, 5, 8, 10, 12, 20, 25, 50, 60, 75, 80])
    w = m * 10
    return Draft(f"Quel est le poids d'un objet de masse {fr(m, 2)} kg sur Terre (on prendra g = 10 N/kg) ?", f"{fr(w, 2)} N", [f"{fr(x, 2)} N" for x in (m / 10, m + 10, w * 10, w / 100)],
                 f"P = m × g = {fr(m, 2)} × 10 = {fr(w, 2)} N.", src=PC)


@gen(C, "3e-resistors", cap=220, cat="Physique-Chimie", source=PC)
def resistors(rng, d):
    r1, r2 = rng.choice([10, 20, 30, 40, 50, 100, 220]), rng.choice([10, 20, 30, 40, 60, 100, 330])
    if rng.random() < 0.5:
        s = r1 + r2
        return Draft(f"Deux résistors de {r1} Ω et {r2} Ω sont montés en série. Quelle est la résistance équivalente ?", f"{fr(s)} Ω", [f"{fr(x)} Ω" for x in (r1 * r2, abs(r1 - r2) or 5, s + 10, (r1 * r2) // s or 1)],
                     f"En série les résistances s'ajoutent : {r1} + {r2} = {s} Ω.", src=PC)
    if r1 == r2:
        r2 += 20
    p = Fraction(r1 * r2, r1 + r2)
    if p.denominator != 1:
        return None
    return Draft(f"Deux résistors de {r1} Ω et {r2} Ω sont montés en dérivation (parallèle). Quelle est la résistance équivalente ?", f"{fr(int(p))} Ω", [f"{fr(x)} Ω" for x in (r1 + r2, abs(r1 - r2) or 5, int(p) + 5, r1 * r2)],
                 f"1/R = 1/{r1} + 1/{r2} donc R = ({r1} × {r2}) ÷ ({r1} + {r2}) = {int(p)} Ω.", src=PC)


@gen(C, "3e-nodes", cap=160, cat="Physique-Chimie", source=PC)
def nodes(rng, d):
    i1, i2 = rng.choice([0.1, 0.2, 0.25, 0.3, 0.5, 1, 1.5, 2]), rng.choice([0.1, 0.2, 0.4, 0.5, 0.75, 1, 2])
    return Draft(f"Un courant se partage en deux branches : I₁ = {fr(i1, 2)} A et I₂ = {fr(i2, 2)} A. Quelle est l'intensité du courant principal (loi des nœuds) ?", f"{fr(i1 + i2, 2)} A",
                 [f"{fr(x, 2)} A" for x in (abs(i1 - i2) or 0.05, i1 * i2, i1 + i2 + 0.5, (i1 + i2) / 2)], f"Loi des nœuds : I = I₁ + I₂ = {fr(i1, 2)} + {fr(i2, 2)} = {fr(i1 + i2, 2)} A.", src=PC)


@gen(C, "3e-mole", cap=220, cat="Physique-Chimie", source=PC + " (masses molaires atomiques usuelles)")
def mole(rng, d):
    M = {"H₂O": 18, "CO₂": 44, "NaCl": 58.5, "O₂": 32, "H₂SO₄": 98, "CaCO₃": 100, "NaOH": 40, "CH₄": 16, "NH₃": 17, "C₂H₆O": 46, "HCl": 36.5, "N₂": 28}
    f = rng.choice(list(M))
    m = M[f] * rng.choice([0.5, 1, 2, 3, 4, 5, 0.1, 0.25, 10])
    n = m / M[f]
    return Draft(f"Quelle quantité de matière représentent {fr(m, 2)} g de {f} (M = {fr(M[f], 1)} g/mol) ?", f"{fr(n, 3)} mol", [f"{fr(x, 3)} mol" for x in (m * M[f], M[f] / m, n * 10, n / 10)],
                 f"n = m ÷ M = {fr(m, 2)} ÷ {fr(M[f], 1)} = {fr(n, 3)} mol.", src=PC)


@gen(C, "3e-concentration", cap=200, cat="Physique-Chimie", source=PC)
def concentration(rng, d):
    n, v = rng.choice([0.1, 0.2, 0.5, 1, 2, 0.05, 0.25]), rng.choice([0.25, 0.5, 1, 2, 0.1, 0.2])
    c = n / v
    return Draft(f"On dissout {fr(n, 3)} mol de soluté dans {fr(v, 3)} L de solution. Quelle est la concentration molaire ?", f"{fr(c, 3)} mol/L", [f"{fr(x, 3)} mol/L" for x in (n * v, v / n, c * 10, c / 10)],
                 f"C = n ÷ V = {fr(n, 3)} ÷ {fr(v, 3)} = {fr(c, 3)} mol/L.", src=PC)


@gen(C, "3e-dilution", cap=160, cat="Physique-Chimie", source=PC)
def dilution(rng, d):
    c1, v1, v2 = rng.choice([1, 2, 4, 5, 0.5, 10]), rng.choice([10, 20, 25, 50, 100]), rng.choice([100, 200, 250, 500, 1000])
    if v2 <= v1:
        return None
    c2 = c1 * v1 / v2
    return Draft(f"On prélève {v1} mL d'une solution à {fr(c1, 2)} mol/L et on complète avec de l'eau pour obtenir {v2} mL. Quelle est la concentration de la solution diluée ?", f"{fr(c2, 4)} mol/L",
                 [f"{fr(x, 4)} mol/L" for x in (c1 * v2 / v1, c1 / 2, c2 * 10, c2 / 10)], f"C₁V₁ = C₂V₂ donc C₂ = {fr(c1, 2)} × {v1} ÷ {v2} = {fr(c2, 4)} mol/L.", src=PC)


@gen(C, "3e-work", cap=160, cat="Physique-Chimie", source=PC)
def work(rng, d):
    f, dist = rng.choice([10, 20, 50, 100, 200, 500, 25, 150]), rng.choice([0.5, 1, 2, 3, 5, 10, 20])
    w = f * dist
    return Draft(f"Une force constante de {f} N déplace son point d'application de {fr(dist, 1)} m dans sa direction. Quel est son travail ?", f"{fr(w, 1)} J", [f"{fr(x, 1)} J" for x in (f / dist, f + dist, w * 10, w / 10)],
                 f"W = F × d = {f} × {fr(dist, 1)} = {fr(w, 1)} J.", src=PC)


@gen(C, "3e-pressure", cap=160, cat="Physique-Chimie", source=PC)
def pressure(rng, d):
    f, s = rng.choice([100, 200, 500, 600, 800, 1000, 1200]), rng.choice([0.01, 0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 2])
    p = f / s
    return Draft(f"Une force pressante de {f} N s'exerce sur une surface de {fr(s, 3)} m². Quelle est la pression ?", f"{fr(p, 1)} Pa", [f"{fr(x, 1)} Pa" for x in (f * s, p * 10, p / 10, f + s)],
                 f"p = F ÷ S = {f} ÷ {fr(s, 3)} = {fr(p, 1)} Pa.", src=PC)


@gen(C, "3e-speed-dist", cap=160, cat="Physique-Chimie", source=PC)
def speed_dist(rng, d):
    v, t = rng.choice([5, 10, 15, 20, 25, 30, 340]), rng.choice([2, 3, 4, 5, 6, 8, 10, 12])
    return Draft(f"Un mobile se déplace à {v} m/s pendant {t} s. Quelle distance parcourt-il ?", f"{fr(v * t)} m", [f"{fr(x)} m" for x in (v + t, v * t * 10, max(1, v // t) if v // t else 1, v * t // 2)], f"d = v × t = {v} × {t} = {fr(v * t)} m.", src=PC)


@gen(C, "3e-vergence", cap=100, cat="Physique-Chimie", source=PC)
def vergence(rng, d):
    f = rng.choice([0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 0.125, 0.4])
    c = 1 / f
    return Draft(f"Une lentille mince a une distance focale de {fr(f, 3)} m. Quelle est sa vergence ?", f"{fr(c, 2)} δ", [f"{fr(x, 2)} δ" for x in (f, c * 10, c / 10, f * 100)],
                 f"C = 1 ÷ f = 1 ÷ {fr(f, 3)} = {fr(c, 2)} dioptries (δ).", src=PC)
