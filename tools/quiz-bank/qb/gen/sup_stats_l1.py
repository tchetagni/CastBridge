"""Licence 1 Mathématiques : statistiques descriptives et probabilités (moyenne, médiane, quartiles, variance, régression,
probabilités conditionnelles, Bayes, lois usuelles, dénombrement). Chaque réponse est calculée en fractions exactes puis
recoupée par une seconde méthode (énumération, force brute, formule alternative).

Budget : le parcours l1-maths contient déjà beaucoup de questions ; ce fichier reste volontairement sous ~1 200 questions
(somme des caps = 728, multipliée par CAP_SCALE['l1-maths'] = 1,6)."""
import math
import statistics
from collections import Counter
from fractions import Fraction
from itertools import combinations, permutations, product

from ..core import Draft, MINUS, fr, gen, near_ints
from .mathfmt import num

C = "l1-maths"
SRC = "Programme de Licence 1 (statistiques et probabilités) - réponse calculée et recoupée"


# ----------------------------------------------------------------------------------------------- helpers
def _terminating(f):
    d = f.denominator
    for p in (2, 5):
        while d % p == 0:
            d //= p
    return d == 1


def dec(f, maxk=6):
    """Exact decimal (French comma) when the fraction terminates, else the fraction n/d."""
    f = Fraction(f)
    if f.denominator == 1:
        return fr(int(f))
    if _terminating(f):
        for k in range(1, maxk + 1):
            if (f * 10 ** k).denominator == 1:
                n = abs(int(f * 10 ** k))
                s = str(n).rjust(k + 1, "0")
                ip, dp = s[:-k], s[-k:]
                if len(ip) > 4:
                    ip = fr(int(ip))
                return (MINUS if f < 0 else "") + ip + "," + dp
    return num(f)


def rnd(f, places):
    """Round a Fraction half-up to `places` decimals; returns None when the value is within 1e-9 of a rounding tie."""
    f = Fraction(f)
    scaled = abs(f) * 10 ** places
    fl = scaled.numerator // scaled.denominator
    rest = scaled - fl
    if abs(rest - Fraction(1, 2)) < Fraction(1, 10 ** 9):
        return None
    r = fl + (1 if rest > Fraction(1, 2) else 0)
    return (-1 if f < 0 else 1) * Fraction(r, 10 ** places)


def fx(f, places):
    """Fixed number of decimals, French comma. None on a rounding tie."""
    r = rnd(f, places)
    if r is None:
        return None
    n = abs(int(r * 10 ** places))
    s = str(n).rjust(places + 1, "0")
    ip, dp = s[:-places], s[-places:]
    return (MINUS if r < 0 else "") + ip + "," + dp


def fxs(vals, places):
    out = [fx(v, places) for v in vals]
    return [o for o in out if o is not None]


def lst(vals):
    return ", ".join(num(v) for v in vals)


# ----------------------------------------------------------------------------------------------- statistiques descriptives
@gen(C, "l1s-weighted-mean", cap=36, cat="Statistiques")
def weighted_mean(rng, d):
    k = 3 if d <= 2 else rng.choice([3, 4])
    xs = sorted(rng.sample(range(4, 20), k))
    ns = [rng.randint(1, 8) for _ in range(k)]
    exact = Fraction(sum(x * n for x, n in zip(xs, ns)), sum(ns))
    flat = [x for x, n in zip(xs, ns) for _ in range(n)]
    assert exact == Fraction(sum(flat), len(flat))
    right = fx(exact, 2)
    if right is None:
        return None
    if rng.random() < 0.5:
        body = ", ".join(f"{x} (effectif {n})" for x, n in zip(xs, ns))
        text = f"Une série statistique prend les valeurs : {body}. Quelle est sa moyenne, arrondie au centième ?"
    else:
        body = ", ".join(f"{x} avec le coefficient {n}" for x, n in zip(xs, ns))
        text = f"Un étudiant a obtenu les notes suivantes : {body}. Quelle est sa moyenne pondérée, arrondie au centième ?"
    wr = fxs([Fraction(sum(xs), k), Fraction(sum(ns), k), Fraction(sum(x * n for x, n in zip(xs, ns)), k),
              exact + 1, exact - 1, Fraction(xs[0] + xs[-1], 2)], 2)
    return Draft(text, right, wr,
                 f"Moyenne = Σ(valeur × poids) ÷ Σ(poids) = {sum(x * n for x, n in zip(xs, ns))} ÷ {sum(ns)} ≈ {right}.", src=SRC)


@gen(C, "l1s-median", cap=36, cat="Statistiques")
def median_q(rng, d):
    n = rng.randint(5, 8) if d <= 2 else rng.randint(6, 12)
    vals = [rng.randint(2, 40) for _ in range(n)]
    s = sorted(vals)
    if n % 2:
        med = Fraction(s[n // 2])
    else:
        med = Fraction(s[n // 2 - 1] + s[n // 2], 2)
    assert float(med) == statistics.median(vals)
    right = dec(med)
    mean = Fraction(sum(vals), n)
    wr = [dec(mean) if mean.denominator <= 10 else fx(mean, 1), dec(vals[n // 2]) if n % 2 == 0 else dec(Fraction(s[0] + s[-1], 2)),
          dec(s[n // 2 - 1]), dec(s[n // 2]), dec(Fraction(s[0] + s[-1], 2)), dec(med + 1)]
    wr = [w for w in wr if w]
    return Draft(f"Quelle est la médiane de la série (non ordonnée) {lst(vals)} ?", right, wr,
                 f"On ordonne : {lst(s)}. Avec {n} valeurs, la médiane est " + ("la valeur centrale" if n % 2 else "la moyenne des deux valeurs centrales") + f", soit {right}.", src=SRC)


@gen(C, "l1s-mode-range", cap=20, cat="Statistiques")
def mode_range(rng, d):
    n = rng.randint(7, 12)
    vals = [rng.randint(1, 9) for _ in range(n)]
    cnt = Counter(vals)
    modes = statistics.multimode(vals)
    if len(modes) != 1:
        return None
    mode = modes[0]
    if rng.random() < 0.5:
        wr = [x for x in sorted(set(vals)) if x != mode]
        wr += [max(vals), min(vals), round(sum(vals) / n)]
        return Draft(f"Quel est le mode de la série {lst(vals)} ?", str(mode), [str(w) for w in wr],
                     f"La valeur {mode} apparaît {cnt[mode]} fois, plus souvent que toute autre : c'est le mode.", src=SRC)
    rg = max(vals) - min(vals)
    return Draft(f"Quelle est l'étendue de la série {lst(vals)} ?", str(rg), [str(w) for w in (max(vals), max(vals) + min(vals), rg + 1, rg - 1, max(vals) - mode, cnt[mode])],
                 f"Étendue = maximum − minimum = {max(vals)} − {min(vals)} = {rg}.", src=SRC)


@gen(C, "l1s-quartile", cap=36, cat="Statistiques")
def quartile(rng, d):
    n = rng.randint(8, 13)
    vals = [rng.randint(1, 60) for _ in range(n)]
    s = sorted(vals)
    k = rng.choice([1, 3, 0])
    q1, q3 = s[math.ceil(n / 4) - 1], s[math.ceil(3 * n / 4) - 1]

    def by_def(pct):   # independent: first value reaching at least pct % of the data
        for i, v in enumerate(s):
            if (i + 1) * 100 >= pct * n:
                return v
    assert q1 == by_def(25) and q3 == by_def(75)
    conv = "Convention : Qₖ est la plus petite valeur telle qu'au moins k × 25 % des valeurs lui soient inférieures ou égales."
    if k == 1:
        right, name, rk = q1, "le premier quartile Q₁", math.ceil(n / 4)
    elif k == 3:
        right, name, rk = q3, "le troisième quartile Q₃", math.ceil(3 * n / 4)
    else:
        right, name, rk = q3 - q1, "l'écart interquartile Q₃ − Q₁", None
    med = Fraction(s[n // 2]) if n % 2 else Fraction(s[n // 2 - 1] + s[n // 2], 2)
    wr = [s[math.floor(n / 4)] if k == 1 else s[math.floor(3 * n / 4)] if k == 3 else s[-1] - s[0], dec(med), s[0] if k == 1 else s[-1], right + 1, right - 1, right + 2, right - 2, s[n // 4 + 1] if k != 0 else q3 + q1]
    wr = [str(w) for w in wr]
    ex = (f"Avec n = {n}, Q₁ est la valeur de rang ⌈n/4⌉ = {math.ceil(n / 4)} et Q₃ celle de rang ⌈3n/4⌉ = {math.ceil(3 * n / 4)} dans la série ordonnée {lst(s)}."
          if k else f"Q₁ = {q1} (rang {math.ceil(n / 4)}) et Q₃ = {q3} (rang {math.ceil(3 * n / 4)}) : écart = {q3 - q1}.")
    return Draft(f"{conv} Quel est {name} de la série {lst(vals)} ?", str(right), wr, ex, src=SRC)


def _sd_pool():
    """Zero-sum deviation tuples whose population variance is a perfect square (integer standard deviation)."""
    global _POOL
    try:
        return _POOL
    except NameError:
        pass
    pool = []
    for n in (4, 5, 6):
        r = range(-6, 7)
        for t in product(r, repeat=n):
            if t[0] > t[-1] or sum(t) != 0:
                continue
            ss = sum(x * x for x in t)
            if ss % n == 0:
                v = ss // n
                s = math.isqrt(v)
                if s * s == v and v > 0 and len(set(t)) >= 3:
                    pool.append(t)
    _POOL = pool
    return pool


@gen(C, "l1s-variance", cap=36, cat="Statistiques")
def variance_q(rng, d):
    n = rng.choice([4, 5, 6, 8]) if d > 2 else rng.choice([4, 5])
    vals = [rng.randint(1, 15) for _ in range(n)]
    m = Fraction(sum(vals), n)
    v1 = sum((Fraction(x) - m) ** 2 for x in vals) / n
    v2 = Fraction(sum(x * x for x in vals), n) - m * m
    assert v1 == v2
    right = fx(v1, 2)
    if right is None or v1 == 0:
        return None
    wr = fxs([v1 * n / (n - 1), Fraction(math.isqrt(int(v1 * 10 ** 6)), 10 ** 3), Fraction(sum(x * x for x in vals), n), m * m, v1 + 1, v1 * 2], 2)
    return Draft(f"Quelle est la variance (formule sur la population, division par n) de la série {lst(vals)}, arrondie au centième ?", right, wr,
                 f"Moyenne = {dec(m)} ; variance = moyenne des carrés − carré de la moyenne = {dec(Fraction(sum(x * x for x in vals), n))} − {dec(m * m)} ≈ {right}.", src=SRC)


@gen(C, "l1s-std", cap=28, cat="Statistiques")
def std_q(rng, d):
    pool = _sd_pool()
    dev = list(rng.choice(pool))
    rng.shuffle(dev)
    m = rng.randint(8, 40)
    vals = [m + e for e in dev]
    n = len(vals)
    mean = Fraction(sum(vals), n)
    assert mean == m
    var = sum((Fraction(x) - mean) ** 2 for x in vals) / n
    sd = math.isqrt(int(var))
    assert sd * sd == var
    wr = [fr(x) for x in (int(var), sd + 1, sd - 1 if sd > 1 else sd + 2, sd * 2, sd + 2, int(var) + sd)]
    return Draft(f"Quel est l'écart-type (population) de la série {lst(vals)} ?", fr(sd), wr,
                 f"La moyenne est {m}, la variance vaut {int(var)} et l'écart-type est sa racine carrée : {sd}.", src=SRC)


@gen(C, "l1s-cv", cap=22, cat="Statistiques")
def cv_q(rng, d):
    mean = rng.choice([20, 25, 40, 50, 80, 125, 200, 250, 400, 500])
    sd = rng.randint(2, max(3, mean // 3))
    cv = Fraction(100 * sd, mean)
    unit = rng.choice(["kg", "cm", "FCFA", "points"])
    wr = [dec(x) + " %" for x in (Fraction(100 * mean, sd), Fraction(sd, mean), cv * 2, cv / 2, Fraction(sd * sd * 100, mean))]
    right = dec(cv) + " %"
    return Draft(f"Une série a pour moyenne {mean} {unit} et pour écart-type {sd} {unit}. Quel est son coefficient de variation (écart-type ÷ moyenne) ?", right, wr,
                 f"CV = σ ÷ moyenne = {sd} ÷ {mean} = {dec(Fraction(sd, mean))}, soit {right}.", src=SRC)


@gen(C, "l1s-covariance", cap=30, cat="Statistiques")
def covariance_q(rng, d):
    n = rng.choice([4, 5])
    xs = rng.sample(range(1, 12), n)
    ys = [rng.randint(1, 12) for _ in range(n)]
    mx, my = Fraction(sum(xs), n), Fraction(sum(ys), n)
    c1 = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / n
    c2 = Fraction(sum(x * y for x, y in zip(xs, ys)), n) - mx * my
    assert c1 == c2
    right = dec(c1)
    wr = [dec(x) for x in (c1 * n / (n - 1), Fraction(sum(x * y for x, y in zip(xs, ys)), n), mx * my, c1 + 1, c1 * 2, c1 - 1)]
    pairs = ", ".join(f"({x} ; {y})" for x, y in zip(xs, ys))
    return Draft(f"On observe les couples (x ; y) : {pairs}. Quelle est la covariance de x et y (division par n) ?", right, wr,
                 f"Cov = moyenne des produits − produit des moyennes = {dec(Fraction(sum(x * y for x, y in zip(xs, ys)), n))} − {dec(mx * my)} = {right}.", src=SRC)


@gen(C, "l1s-correlation", cap=30, cat="Statistiques")
def correlation_q(rng, d):
    sx, sy = rng.randint(1, 8), rng.randint(1, 8)
    r = Fraction(rng.choice([1, 2, 3, 4, 5, 6, 7, 8, 9]), 10) * rng.choice([1, -1])
    cov = r * sx * sy
    form = rng.choice(["sd", "var"])
    vx, vy = sx * sx, sy * sy
    assert cov * cov / (vx * vy) == r * r
    if form == "sd":
        text = f"Deux variables ont pour écarts-types {sx} et {sy} et pour covariance {dec(cov)}. Quel est leur coefficient de corrélation linéaire ?"
    else:
        text = f"Deux variables ont pour variances {vx} et {vy} et pour covariance {dec(cov)}. Quel est leur coefficient de corrélation linéaire ?"
    right = dec(r)
    ar = abs(r)
    wr = [dec(x) for x in (cov / (vx * vy), cov / (sx + sy), cov / sx, cov / (sx * sy) * 10, ar * 10 if ar < Fraction(1, 10) else ar / 10)]
    wr = [w for w in wr if w != right]
    return Draft(text, right, wr, f"r = cov(x, y) ÷ (σₓ·σᵧ) = {dec(cov)} ÷ ({sx} × {sy}) = {right}.", src=SRC)


@gen(C, "l1s-regression", cap=36, cat="Statistiques")
def regression_q(rng, d):
    n = rng.choice([4, 5])
    step = rng.choice([1, 2])
    x0 = rng.randint(0, 8)
    xs = [x0 + i * step for i in range(n)]
    slope0 = rng.choice([-3, -2, -1, 1, 2, 3, 4])
    ys = [30 + slope0 * x + rng.randint(-4, 4) for x in xs]
    sx, sy = sum(xs), sum(ys)
    mx, my = Fraction(sx, n), Fraction(sy, n)
    a = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sum((x - mx) ** 2 for x in xs)
    b = my - a * mx
    # independent: normal equations solved by Cramer
    sxx, sxy = sum(x * x for x in xs), sum(x * y for x, y in zip(xs, ys))
    det = Fraction(sxx * n - sx * sx)
    a2 = Fraction(sxy * n - sx * sy) / det
    b2 = Fraction(sxx * sy - sx * sxy) / det
    assert (a, b) == (a2, b2)
    syy = sum(y * y for y in ys)
    if syy * n == sy * sy or sxy * n == sx * sy:
        return None
    a_rev = Fraction(sxy * n - sx * sy, syy * n - sy * sy)
    kind = rng.choice(["a", "b", "eq"])
    pts = ", ".join(f"({x} ; {y})" for x, y in zip(xs, ys))
    head = f"Par la méthode des moindres carrés, on ajuste y = ax + b sur les points {pts}."
    if kind == "a":
        right = dec(a)
        wr = [dec(x) for x in (a_rev, a * n / (n - 1), a + 1, a - 1, -a, Fraction(sxy, sxx))]
        q, ex = head + " Quelle est la pente a ?", f"a = Cov(x, y) ÷ Var(x) = {right}."
    elif kind == "b":
        right = dec(b)
        wr = [dec(x) for x in (my, b + a, my + a * mx, mx - a * my, b + 1, b - 1)]
        q, ex = head + " Quelle est l'ordonnée à l'origine b ?", f"b = ȳ − a·x̄ = {dec(my)} − ({dec(a)} × {dec(mx)}) = {right}."
    else:
        def eq(aa, bb):
            return "y = " + dec(aa) + "x " + ("−" if bb < 0 else "+") + " " + dec(abs(bb))
        right = eq(a, b)
        wr = [eq(a, my), eq(a_rev, my - a_rev * mx), eq(a, b + 1), eq(a + 1, b), eq(-a, b), eq(a, mx)]
        q, ex = head + " Quelle est l'équation de la droite obtenue ?", f"Pente a = {dec(a)} ; la droite passe par le point moyen ({dec(mx)} ; {dec(my)}), d'où b = {dec(b)}."
    if len(q) > 255:
        return None
    return Draft(q, right, wr, ex, src=SRC)


@gen(C, "l1s-regress-mean-point", cap=30, cat="Statistiques")
def regress_pred(rng, d):
    mx, my = rng.randint(2, 20), rng.randint(10, 80)
    a = Fraction(rng.choice([-5, -3, -2, -1, 1, 2, 3, 4, 5]), rng.choice([1, 2, 5, 10]))
    x0 = rng.randint(mx + 1, mx + 12)
    b = my - a * mx
    pred = a * x0 + b
    assert pred == my + a * (x0 - mx)
    right = dec(pred)
    wr = [dec(x) for x in (my + a * x0, a * x0, pred + a, pred - a, b, my + a * (x0 + mx))]
    return Draft(f"La droite de régression de y en x a pour pente {dec(a)}, et les moyennes sont x̄ = {mx} et ȳ = {my}. Quelle valeur prédit-elle pour x = {x0} ?", right, wr,
                 f"La droite passe par le point moyen : ŷ = ȳ + a(x − x̄) = {my} + {dec(a)} × ({x0} − {mx}) = {right}.", src=SRC)


# ----------------------------------------------------------------------------------------------- probabilités
CTX_COND = [("une promotion", ("fille", "garçon"), ("admis", "ajourné")),
            ("un lycée", ("interne", "externe"), ("lecteur de la bibliothèque", "non lecteur")),
            ("une coopérative", ("producteur de cacao", "producteur de café"), ("membre du comité", "simple adhérent")),
            ("une entreprise", ("employé à Douala", "employé à Yaoundé"), ("en télétravail", "sur site"))]


@gen(C, "l1s-conditional", cap=40, cat="Probabilités")
def conditional(rng, d):
    org, (g1, g2), (c1, c2) = rng.choice(CTX_COND)
    a, b, c, e = (rng.randint(2, 14) for _ in range(4))
    pop = [(g1, c1)] * a + [(g1, c2)] * b + [(g2, c1)] * c + [(g2, c2)] * e
    tot = len(pop)
    kind = rng.choice(["c|g", "g|c"])
    if kind == "c|g":
        num_, den = a, a + b
        sub = [x for x in pop if x[0] == g1]
        p = Fraction(sum(1 for x in sub if x[1] == c1), len(sub))
        q = f"Dans {org} de {tot} personnes, on compte : {a} {g1} {c1}, {b} {g1} {c2}, {c} {g2} {c1} et {e} {g2} {c2}. On en choisit une au hasard : sachant qu'elle est {g1}, quelle est la probabilité qu'elle soit {c1} ?"
        wr = [Fraction(a, a + c), Fraction(a, tot), Fraction(a + c, tot), Fraction(b, a + b), Fraction(a, b)]
        ex = f"P({c1} | {g1}) = effectif({g1} et {c1}) ÷ effectif({g1}) = {a}/{a + b}" + ("" if math.gcd(a, a + b) == 1 else f" = {num(p)}") + "."
    else:
        num_, den = a, a + c
        sub = [x for x in pop if x[1] == c1]
        p = Fraction(sum(1 for x in sub if x[0] == g1), len(sub))
        q = f"Dans {org} de {tot} personnes, on compte : {a} {g1} {c1}, {b} {g1} {c2}, {c} {g2} {c1} et {e} {g2} {c2}. On en choisit une au hasard : sachant qu'elle est {c1}, quelle est la probabilité qu'elle soit {g1} ?"
        wr = [Fraction(a, a + b), Fraction(a, tot), Fraction(a + b, tot), Fraction(c, a + c), Fraction(a, c)]
        ex = f"P({g1} | {c1}) = effectif({g1} et {c1}) ÷ effectif({c1}) = {a}/{a + c}" + ("" if math.gcd(a, a + c) == 1 else f" = {num(p)}") + "."
    assert p == Fraction(num_, den)
    if len(q) > 258:
        return None
    return Draft(q, num(p), [num(w) for w in wr], ex, src=SRC)


CTX_BAYES = [("Une maladie touche", "des personnes d'une population ; un test est positif pour", "des malades et pour", "des personnes saines", "malade", "test positif"),
             ("Un atelier a une machine A qui produit", "des pièces ; une pièce est défectueuse pour", "des pièces de A et pour", "des pièces des autres machines", "produite par A", "défectueuse"),
             ("Un filtre classe comme indésirable", "", "", "", "", "")]


@gen(C, "l1s-bayes", cap=44, cat="Probabilités")
def bayes_q(rng, d):
    pa = rng.choice([1, 2, 3, 5, 10, 20, 30, 40])
    pba = rng.choice([60, 70, 80, 90, 95])
    pbn = rng.choice([1, 2, 5, 10, 15, 20])
    N = 10000
    A = N * pa // 100
    AB = A * pba // 100
    nAB = (N - A) * pbn // 100
    assert A * pba % 100 == 0 and (N - A) * pbn % 100 == 0
    post = Fraction(AB, AB + nAB)
    # independent check by Bayes' formula with exact fractions
    f = Fraction(pa, 100) * Fraction(pba, 100)
    assert post == f / (f + (1 - Fraction(pa, 100)) * Fraction(pbn, 100))
    right = fx(post, 3)
    if right is None:
        return None
    ctx = rng.choice([0, 1])
    if ctx == 0:
        text = f"Dans une population, {pa} % des personnes sont atteintes d'une maladie. Un test est positif pour {pba} % des malades et pour {pbn} % des personnes saines. Une personne est testée positive : quelle est la probabilité qu'elle soit malade (arrondie au millième) ?"
    else:
        text = f"Une machine A fabrique {pa} % des pièces d'un atelier. {pba} % des pièces de A sont défectueuses, contre {pbn} % des autres pièces. Une pièce défectueuse est tirée au hasard : quelle est la probabilité qu'elle vienne de A (arrondie au millième) ?"
    pb = Fraction(AB + nAB, N)
    wr = fxs([Fraction(pba, 100), Fraction(AB, N), pb, Fraction(pa, 100), 1 - post, Fraction(nAB, AB + nAB) if False else Fraction(pba, 100) * Fraction(pa, 100) / (Fraction(pba, 100) + Fraction(pbn, 100))], 3)
    return Draft(text, right, wr,
                 f"Sur {N} personnes : {A} concernées, dont {AB} positives ; {N - A} autres, dont {nAB} positives. Probabilité = {AB} ÷ ({AB} + {nAB}) ≈ {right}.", src=SRC)


@gen(C, "l1s-independence-union", cap=30, cat="Probabilités")
def indep_union(rng, d):
    pa, pb = Fraction(rng.randint(1, 9), 10), Fraction(rng.randint(1, 9), 10)
    kind = rng.choice(["union", "inter-from-union", "pb"])
    inter = pa * pb
    union = pa + pb - inter
    if kind == "union":
        right = dec(union)
        wr = [dec(pa + pb) if pa + pb <= 1 else dec(inter), dec(inter), dec(1 - inter), dec(max(pa, pb)), dec(union - Fraction(1, 10)) if union > Fraction(1, 10) else dec(union + Fraction(1, 10))]
        text = f"A et B sont deux événements indépendants avec P(A) = {dec(pa)} et P(B) = {dec(pb)}. Quelle est P(A ∪ B) ?"
        ex = f"P(A ∪ B) = P(A) + P(B) − P(A)P(B) = {dec(pa)} + {dec(pb)} − {dec(inter)} = {right}."
        assert union == 1 - (1 - pa) * (1 - pb)
    elif kind == "inter-from-union":
        right = dec(inter)
        wr = [dec(pa + pb - union) if False else dec(min(pa, pb)), dec(pa + pb), dec(union), dec(abs(pa - pb)), dec(1 - union)]
        text = f"A et B sont indépendants, P(A) = {dec(pa)} et P(B) = {dec(pb)}. Quelle est P(A ∩ B) ?"
        ex = f"Indépendance : P(A ∩ B) = P(A) × P(B) = {dec(pa)} × {dec(pb)} = {right}."
    else:
        pb2 = pb
        right = dec(pb2)
        text = f"A et B sont indépendants avec P(A) = {dec(pa)} et P(A ∩ B) = {dec(pa * pb2)}. Quelle est P(B) ?"
        wr = [dec(pa * pb2 / 1 * 1) if False else dec(pa * pb2 * pa), dec(pa * pb2), dec(1 - pb2), dec(pa + pb2) if pa + pb2 <= 1 else dec(pa), dec(pb2 / 2)]
        ex = f"P(B) = P(A ∩ B) ÷ P(A) = {dec(pa * pb2)} ÷ {dec(pa)} = {right}."
        assert pa * pb2 / pa == pb2
    return Draft(text, right, wr, ex, src=SRC)


@gen(C, "l1s-independence-pick", cap=30, cat="Probabilités")
def indep_pick(rng, d):
    pa, pb = Fraction(rng.randint(2, 8), 10), Fraction(rng.randint(2, 8), 10)
    case = rng.choice(["indep", "incomp", "dep"])
    if case == "indep":
        pab = pa * pb
    elif case == "incomp":
        if pa + pb > 1:
            return None
        pab = Fraction(0)
    else:
        lo, hi = max(Fraction(0), pa + pb - 1), min(pa, pb)
        cands = [Fraction(k, 100) for k in range(1, 100) if lo < Fraction(k, 100) <= hi and Fraction(k, 100) != pa * pb]
        if not cands:
            return None
        pab = rng.choice(cands)
    indep, incomp = pab == pa * pb, pab == 0
    assert (indep, incomp) == {"indep": (True, False), "incomp": (False, True), "dep": (False, False)}[case]
    opts = {(True, True): "Les événements A et B sont indépendants et incompatibles", (True, False): "Les événements A et B sont indépendants mais non incompatibles",
            (False, True): "Les événements A et B sont incompatibles mais non indépendants", (False, False): "Les événements A et B ne sont ni indépendants ni incompatibles"}
    right = opts[(indep, incomp)]
    wr = [v for k, v in opts.items() if k != (indep, incomp)]
    text = f"On donne P(A) = {dec(pa)}, P(B) = {dec(pb)} et P(A ∩ B) = {dec(pab)}. Quelle affirmation est exacte ?"
    ex = f"P(A)·P(B) = {dec(pa * pb)} et P(A ∩ B) = {dec(pab)} : " + ("égalité, donc indépendants" if indep else "pas d'égalité, donc non indépendants") + " ; " + ("l'intersection est vide, donc incompatibles." if incomp else "l'intersection n'est pas vide, donc non incompatibles.")
    return Draft(text, right, wr, ex, src=SRC)


# ----------------------------------------------------------------------------------------------- lois usuelles
PFR = [Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(1, 4), Fraction(3, 4), Fraction(1, 6), Fraction(5, 6)]


def binom_brute(n, p, pred):
    tot = Fraction(0)
    for seq in product((0, 1), repeat=n):
        w = Fraction(1)
        for s in seq:
            w *= p if s else 1 - p
        if pred(sum(seq)):
            tot += w
    return tot


@gen(C, "l1s-binomial-exact", cap=36, cat="Probabilités")
def binom_exact(rng, d):
    p = rng.choice(PFR)
    n = rng.randint(3, 6 if p.denominator == 6 else 8)
    kind = rng.choice(["eq", "ge1", "le1"])
    q = 1 - p
    k = rng.randint(1, n - 1)
    if kind == "eq":
        val = math.comb(n, k) * p ** k * q ** (n - k)
        assert val == binom_brute(n, p, lambda s: s == k)
        text = f"X suit la loi binomiale B({n} ; {num(p)}). Quelle est P(X = {k}), sous forme de fraction irréductible ?"
        wr = [p ** k * q ** (n - k), math.comb(n, k) * p ** k, math.comb(n, k) * p ** k * q ** k, math.comb(n, k) * p ** (n - k) * q ** k, val * 2, math.comb(n, k) * p ** (n - k) * q ** k]
        ex = f"P(X = k) = C({n},{k}) p^{k} (1−p)^{n - k} = {math.comb(n, k)} × ({num(p)})^{k} × ({num(q)})^{n - k} = {num(val)}."
    elif kind == "ge1":
        val = 1 - q ** n
        assert val == binom_brute(n, p, lambda s: s >= 1)
        text = f"X suit la loi binomiale B({n} ; {num(p)}). Quelle est P(X ≥ 1), sous forme de fraction irréductible ?"
        wr = [q ** n, 1 - p ** n, n * p, p ** n, 1 - n * q, val * q]
        ex = f"P(X ≥ 1) = 1 − P(X = 0) = 1 − ({num(q)})^{n} = {num(val)}."
    else:
        val = q ** n + n * p * q ** (n - 1)
        assert val == binom_brute(n, p, lambda s: s <= 1)
        text = f"X suit la loi binomiale B({n} ; {num(p)}). Quelle est P(X ≤ 1), sous forme de fraction irréductible ?"
        wr = [q ** n, n * p * q ** (n - 1), 1 - q ** n, p ** n + n * q * p ** (n - 1), q ** n + p * q ** (n - 1), val / 2]
        ex = f"P(X ≤ 1) = P(X = 0) + P(X = 1) = ({num(q)})^{n} + {n}·{num(p)}·({num(q)})^{n - 1} = {num(val)}."
    right = num(val)
    wr = [num(Fraction(w)) for w in wr]
    if len(right) > 60 or any(len(w) > 60 for w in wr):
        return None
    return Draft(text, right, wr, ex, src=SRC)


@gen(C, "l1s-binomial-round", cap=36, cat="Probabilités")
def binom_round(rng, d):
    p = Fraction(rng.choice([1, 2, 25, 3, 4, 6, 7, 8, 9]), 100 if False else 10) if False else rng.choice([Fraction(1, 10), Fraction(1, 5), Fraction(1, 4), Fraction(3, 10), Fraction(2, 5), Fraction(3, 5), Fraction(7, 10), Fraction(4, 5), Fraction(9, 10)])
    n = rng.randint(4, 9)
    kind = rng.choice(["eq", "le", "ge"])
    k = rng.randint(1, n - 1)
    q = 1 - p
    pmf = lambda j: math.comb(n, j) * p ** j * q ** (n - j)
    if kind == "eq":
        val = pmf(k)
        assert val == binom_brute(n, p, lambda s: s == k)
        label = f"P(X = {k})"
        alt = [pmf(k - 1), pmf(k + 1), p ** k * q ** (n - k)]
    elif kind == "le":
        val = sum(pmf(j) for j in range(k + 1))
        assert val == binom_brute(n, p, lambda s: s <= k)
        label = f"P(X ≤ {k})"
        alt = [sum(pmf(j) for j in range(k)), pmf(k), 1 - val]
    else:
        val = sum(pmf(j) for j in range(k, n + 1))
        assert val == binom_brute(n, p, lambda s: s >= k)
        label = f"P(X ≥ {k})"
        alt = [sum(pmf(j) for j in range(k + 1, n + 1)), pmf(k), 1 - val]
    right = fx(val, 3)
    if right is None:
        return None
    wr = fxs(alt + [Fraction(n) * p / 10, val * 2 if val < Fraction(1, 2) else val / 2, pmf(k + 1) + pmf(k)], 3)
    return Draft(f"X suit la loi binomiale B({n} ; {dec(p)}). Quelle est {label}, arrondie à 10⁻³ (au millième) ?", right, wr,
                 f"On additionne ou calcule C({n},j)·{dec(p)}ʲ·{dec(q)}^({n}−j) pour les valeurs de j concernées : {label} ≈ {right}.", src=SRC)


@gen(C, "l1s-geometric", cap=30, cat="Probabilités")
def geometric_q(rng, d):
    p = rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(1, 4), Fraction(1, 5), Fraction(1, 6), Fraction(2, 5), Fraction(1, 10)])
    q = 1 - p
    k = rng.randint(2, 6)
    kind = rng.choice(["eq", "gt", "le", "mean"])
    ctx = rng.choice(["On lance un dé équilibré jusqu'au premier succès ; à chaque lancer la probabilité de succès est", "On répète des essais indépendants jusqu'au premier succès ; la probabilité de succès à chaque essai est"])
    head = f"X est le rang du premier succès (loi géométrique). {ctx} p = {num(p)}."
    head = f"{ctx} p = {num(p)}, et X est le rang du premier succès."
    if kind == "eq":
        val = q ** (k - 1) * p
        assert abs(float(val) - float(p) * sum(0 for _ in range(1)) - float(q) ** (k - 1) * float(p)) < 1e-12
        right = num(val)
        wr = [q ** k * p, q ** (k - 1), p ** (k - 1) * q, q ** k, 1 - q ** (k - 1)]
        text, ex = f"{head} Quelle est P(X = {k}) ?", f"Il faut {k - 1} échecs puis un succès : q^{k - 1}·p = ({num(q)})^{k - 1} × {num(p)} = {right}."
    elif kind == "gt":
        val = q ** k
        # independent: 1 - sum of the first k terms
        assert val == 1 - sum(q ** (j - 1) * p for j in range(1, k + 1))
        right = num(val)
        wr = [q ** (k - 1), 1 - q ** k, q ** k * p, p ** k, q ** (k + 1)]
        text, ex = f"{head} Quelle est P(X > {k}) ?", f"X > {k} signifie {k} échecs consécutifs : q^{k} = ({num(q)})^{k} = {right}."
    elif kind == "le":
        val = 1 - q ** k
        assert val == sum(q ** (j - 1) * p for j in range(1, k + 1))
        right = num(val)
        wr = [q ** k, 1 - q ** (k - 1), q ** (k - 1) * p, 1 - q ** (k + 1), p ** k]
        text, ex = f"{head} Quelle est P(X ≤ {k}) ?", f"P(X ≤ {k}) = 1 − P(X > {k}) = 1 − ({num(q)})^{k} = {right}."
    else:
        val = 1 / p
        approx = sum(j * float(q) ** (j - 1) * float(p) for j in range(1, 4000))
        assert abs(approx - float(val)) < 1e-6
        right = num(val)
        wr = [1 / q, p, q / p, 1 / p + 1, 1 / p - 1]
        text, ex = f"{head} Quelle est l'espérance de X ?", f"Pour la loi géométrique, E(X) = 1/p = {right}."
    wr = [num(Fraction(w)) for w in wr]
    return Draft(text, right, wr, ex, src=SRC)


@gen(C, "l1s-poisson", cap=20, cat="Probabilités")
def poisson_q(rng, d):
    lam = rng.choice([0.5, 1.0, 1.5, 2.0, 2.5, 3.0])
    k = rng.randint(0, 5)
    kind = rng.choice(["eq", "ge1", "le1"])
    ctx = rng.choice([("Le nombre d'appels reçus par minute", "appels"), ("Le nombre de pannes par semaine", "pannes"), ("Le nombre de clients par quart d'heure", "clients")])
    head = f"{ctx[0]} suit une loi de Poisson de paramètre λ = {fr(lam, 1)}."
    p0 = math.exp(-lam)
    pk = p0
    for j in range(1, k + 1):          # recurrence p(j) = p(j-1) λ / j
        pk *= lam / j
    assert abs(pk - math.exp(-lam) * lam ** k / math.factorial(k)) < 1e-12
    if kind == "eq":
        v = pk
        wr = [math.exp(-lam) * lam ** (k + 1) / math.factorial(k + 1), lam ** k / math.factorial(k), math.exp(-lam * k) if k else 1 - p0, pk * lam, 1 - pk]
        text, ex = f"{head} Quelle est la probabilité d'observer exactement {k} {ctx[1]} (arrondie au millième) ?", f"P(X = {k}) = e^(−λ)·λ^{k}/{k}! ≈ {'%.3f' % v}."
    elif kind == "ge1":
        v = 1 - p0
        wr = [p0, lam * p0, 1 - lam * p0, 1 - math.exp(-1), 1 - p0 - lam * p0]
        text, ex = f"{head} Quelle est la probabilité d'observer au moins 1 des {ctx[1]} (arrondie au millième) ?", f"P(X ≥ 1) = 1 − P(X = 0) = 1 − e^(−{fr(lam, 1)}) ≈ {'%.3f' % v}."
    else:
        v = p0 * (1 + lam)
        wr = [p0, lam * p0, 1 - v, p0 * (1 + lam + lam * lam / 2), 1 - p0]
        text, ex = f"{head} Quelle est la probabilité d'observer au plus 1 des {ctx[1]} (arrondie au millième) ?", f"P(X ≤ 1) = e^(−λ)(1 + λ) = {'%.3f' % v}."
    f3 = lambda x: ("%.3f" % x).replace(".", ",")
    right = f3(v)
    if abs(v * 1000 - round(v * 1000)) > 0.49 and abs(v * 1000 % 1 - 0.5) < 1e-4:
        return None
    return Draft(text, right, [f3(w) for w in wr], ex, src=SRC)


def _distribution(rng, size, N):
    """Distribution as (values, integer weights summing to N), weights >= 1."""
    xs = sorted(rng.sample(range(-3, 10), size))
    cuts = sorted(rng.sample(range(1, N), size - 1))
    ws = [b - a for a, b in zip([0] + cuts, cuts + [N])]
    return xs, ws


@gen(C, "l1s-expectation", cap=36, cat="Probabilités")
def expectation_q(rng, d):
    size = rng.choice([3, 4]) if d > 1 else 3
    N = rng.choice([4, 5, 8, 10, 20])
    if N < size + 1:
        return None
    xs, ws = _distribution(rng, size, N)
    ps = [Fraction(w, N) for w in ws]
    e = sum(x * p for x, p in zip(xs, ps))
    pool = [x for x, w in zip(xs, ws) for _ in range(w)]          # N tickets
    assert e == Fraction(sum(pool), N)
    table = ", ".join(f"P(X = {dec(x)}) = {dec(p)}" for x, p in zip(xs, ps))
    wr = [Fraction(sum(xs), size), sum(x * x * p for x, p in zip(xs, ps)), sum(ps[i] * xs[-1 - i] for i in range(size)), e + 1, e - Fraction(1, 2), max(xs, key=lambda x: ps[xs.index(x)])]
    return Draft(f"Une variable aléatoire X vérifie {table}. Quelle est son espérance E(X) ?", dec(e), [dec(w) for w in wr],
                 f"E(X) = Σ x·P(X = x) = {dec(e)}.", src=SRC)


@gen(C, "l1s-variance-discrete", cap=30, cat="Probabilités")
def variance_discrete(rng, d):
    size = 3
    N = rng.choice([4, 5, 8, 10, 20])
    xs, ws = _distribution(rng, size, N)
    ps = [Fraction(w, N) for w in ws]
    e = sum(x * p for x, p in zip(xs, ps))
    ex2 = sum(x * x * p for x, p in zip(xs, ps))
    v = ex2 - e * e
    pool = [x for x, w in zip(xs, ws) for _ in range(w)]
    assert v == sum((Fraction(x) - e) ** 2 for x in pool) / N
    if v == 0:
        return None
    table = ", ".join(f"P(X = {dec(x)}) = {dec(p)}" for x, p in zip(xs, ps))
    wr = [ex2, e * e, v + 1, -v if False else v / 2, sum((Fraction(x) - e) ** 2 for x in xs) / size, v * 2]
    return Draft(f"Une variable aléatoire X vérifie {table}. Quelle est sa variance V(X) ?", dec(v), [dec(w) for w in wr],
                 f"E(X) = {dec(e)}, E(X²) = {dec(ex2)}, donc V(X) = E(X²) − E(X)² = {dec(v)}.", src=SRC)


# ----------------------------------------------------------------------------------------------- dénombrements
WORDS = ["ANANAS", "BANANE", "PAPAYE", "MAMAN", "PAPA", "COCO", "CACAO", "MANGUE", "ASSASSIN", "TOTO", "KILO", "BAOBAB", "KAKI", "MADAME", "ELLE", "ABACA"]


@gen(C, "l1s-anagrams", cap=28, cat="Dénombrement")
def anagrams(rng, d):
    if rng.random() < 0.5:
        w = rng.choice(WORDS)
    else:
        w = "".join(rng.choice("ABCDEMNPRS") for _ in range(rng.randint(5, 7)))
    cnt = Counter(w)
    if max(cnt.values()) == 1 or len(w) > 8 or len(w) < 4:
        return None
    n = len(w)
    val = math.factorial(n)
    for c in cnt.values():
        val //= math.factorial(c)
    assert val == len(set(permutations(w)))
    dups = [math.factorial(c) for c in cnt.values()]
    wr = [math.factorial(n), math.factorial(n) // max(dups), val * n, val + 1, math.factorial(n) // sum(math.factorial(c) for c in cnt.values()) if sum(math.factorial(c) for c in cnt.values()) else 1, val // 2]
    expl_den = " × ".join(f"{c}!" for c in cnt.values() if c > 1)
    return Draft(f"Combien de mots distincts (avec ou sans sens) peut-on former en permutant toutes les lettres de « {w} » ?", fr(val), [fr(x) for x in wr],
                 f"{n}! arrangements, divisés par {expl_den} pour les lettres répétées : {fr(val)}.", src=SRC)


@gen(C, "l1s-committee", cap=28, cat="Dénombrement")
def committee(rng, d):
    n = rng.randint(7, 12)
    m = rng.randint(2, n - 3)          # number of women
    k = rng.randint(2, 4)
    kind = rng.choice(["atleast1", "exact"])
    people = range(n)
    women = set(range(m))
    if kind == "atleast1":
        val = sum(1 for c in combinations(people, k) if women & set(c))
        assert val == math.comb(n, k) - math.comb(n - m, k)
        wr = [m * math.comb(n - 1, k - 1), math.comb(n, k), math.comb(m, k), math.comb(n, k) - math.comb(m, k), m * math.comb(n, k - 1)]
        text = f"Un groupe compte {n} personnes dont {m} femmes. De combien de façons peut-on former un comité de {k} personnes comprenant au moins une femme ?"
        ex = f"On retire des C({n},{k}) comités ceux sans aucune femme, C({n - m},{k}) : {math.comb(n, k)} − {math.comb(n - m, k)} = {val}."
    else:
        j = rng.randint(1, min(k, m))
        val = sum(1 for c in combinations(people, k) if len(women & set(c)) == j)
        assert val == math.comb(m, j) * math.comb(n - m, k - j)
        wr = [math.comb(n, k) // 2 if False else math.comb(m, j) + math.comb(n - m, k - j), math.comb(n, k), math.comb(m, j) * math.comb(n - m, k), math.comb(m, k - j) * math.comb(n - m, j), math.perm(m, j) * math.perm(n - m, k - j)]
        text = f"Un groupe compte {n} personnes dont {m} femmes. De combien de façons peut-on former un comité de {k} personnes comprenant exactement {j} femme{'s' if j > 1 else ''} ?"
        ex = f"On choisit {j} femme{'s' if j > 1 else ''} parmi {m} et {k - j} homme{'s' if k - j > 1 else ''} parmi {n - m} : C({m},{j})·C({n - m},{k - j}) = {val}."
    return Draft(text, fr(val), [fr(x) for x in wr], ex, src=SRC)
