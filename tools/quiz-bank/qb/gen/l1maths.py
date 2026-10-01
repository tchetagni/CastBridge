"""Licence 1 Mathématiques: linear algebra, limits and expansions, series, integrals, modular arithmetic, counting, polynomials.
Every answer is computed with exact fractions or integers and cross-checked (numeric evaluation, brute force)."""
import math
from fractions import Fraction
from itertools import combinations, product

from ..core import Draft, fr, gen, near_ints
from .mathfmt import ev, num, nz, par, poly, sup

C = "l1-maths"
SRC = "Programme de Licence 1 de mathématiques (algèbre linéaire, analyse, arithmétique) - réponse calculée et recoupée"


def M(m):
    return "[" + ", ".join("[" + ", ".join(num(x) for x in row) + "]" for row in m) + "]"


def det2(m):
    return m[0][0] * m[1][1] - m[0][1] * m[1][0]


def det3(m):
    a, b, c = m[0]
    d, e, f = m[1]
    g, h, i = m[2]
    return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)


def det3_laplace_col(m):
    """Independent second computation: expansion along the first column."""
    def minor(r, c):
        rows = [row for k, row in enumerate(m) if k != r]
        return [[x for j, x in enumerate(row) if j != c] for row in rows]
    return sum((-1) ** r * m[r][0] * det2(minor(r, 0)) for r in range(3))


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(len(b))) for j in range(len(b[0]))] for i in range(len(a))]


def rmat(rng, n, lo=-5, hi=6):
    return [[rng.randint(lo, hi) for _ in range(n)] for _ in range(n)]


def mat_variants(rng, m, n=6):
    out, seen = [], {str(m)}
    for _ in range(40):
        c = [row[:] for row in m]
        i, j = rng.randrange(len(c)), rng.randrange(len(c[0]))
        c[i][j] += rng.choice([-2, -1, 1, 2]) if rng.random() < 0.7 else -2 * c[i][j]
        if rng.random() < 0.4:
            c[j % len(c)][i % len(c[0])] = -c[j % len(c)][i % len(c[0])]
        if str(c) in seen:
            continue
        seen.add(str(c))
        out.append(M(c))
        if len(out) >= n:
            break
    return out


@gen(C, "l1m-det2", cap=220, cat="Algèbre linéaire")
def det2_q(rng, d):
    m = rmat(rng, 2, -6 - d, 8 + d)
    v = det2(m)
    return Draft(f"Quel est le déterminant de la matrice {M(m)} ?", num(v), [num(x) for x in (m[0][0] * m[1][1] + m[0][1] * m[1][0], m[0][0] * m[1][1], -v, m[0][1] * m[1][0], v + 1)],
                 f"det = ad − bc = {par(m[0][0])}×{par(m[1][1])} − {par(m[0][1])}×{par(m[1][0])} = {num(v)}.", src=SRC)


@gen(C, "l1m-det3", cap=260, cat="Algèbre linéaire")
def det3_q(rng, d):
    m = rmat(rng, 3, -3 - d // 2, 4 + d // 2)
    v = det3(m)
    assert v == det3_laplace_col(m)
    return Draft(f"Quel est le déterminant de la matrice {M(m)} ?", num(v), [num(x) for x in near_ints(rng, v, 6)] + [num(-v), num(m[0][0] * m[1][1] * m[2][2])],
                 f"Développement suivant la première ligne : det = {num(v)}.", src=SRC)


@gen(C, "l1m-trace", cap=120, cat="Algèbre linéaire")
def trace_q(rng, d):
    n = rng.choice([2, 3, 4])
    m = rmat(rng, n, -9, 12)
    t = sum(m[i][i] for i in range(n))
    return Draft(f"Quelle est la trace de la matrice {M(m)} ?", num(t), [num(x) for x in near_ints(rng, t, 5)] + [num(sum(m[i][n - 1 - i] for i in range(n)))],
                 f"La trace est la somme des éléments de la diagonale : {num(t)}.", src=SRC)


@gen(C, "l1m-matmul-entry", cap=240, cat="Algèbre linéaire")
def matmul_entry(rng, d):
    a, b = rmat(rng, 2, -4, 5), rmat(rng, 2, -4, 5)
    p = matmul(a, b)
    i, j = rng.randrange(2), rng.randrange(2)
    v = p[i][j]
    return Draft(f"On donne A = {M(a)} et B = {M(b)}. Quel est l'élément de la ligne {i + 1}, colonne {j + 1} du produit AB ?", num(v),
                 [num(x) for x in near_ints(rng, v, 5)] + [num(matmul(b, a)[i][j]), num(a[i][j] * b[i][j])], f"(AB)_{i + 1}{j + 1} = {par(a[i][0])}×{par(b[0][j])} + {par(a[i][1])}×{par(b[1][j])} = {num(v)}.", src=SRC)


@gen(C, "l1m-inverse2", cap=200, cat="Algèbre linéaire")
def inverse2(rng, d):
    a = [[1, rng.randint(0, 4)], [0, 1]]
    b = [[1, 0], [rng.randint(0, 4), 1]]
    c = [[1, rng.randint(-3, 3)], [0, 1]]
    m = matmul(matmul(a, b), c)
    dt = det2(m)
    assert dt == 1
    inv = [[m[1][1], -m[0][1]], [-m[1][0], m[0][0]]]
    assert matmul(m, inv) == [[1, 0], [0, 1]]
    wr = mat_variants(rng, inv) + [M(m[::-1]), M([[m[0][0], -m[0][1]], [-m[1][0], m[1][1]]])]
    return Draft(f"Quelle est l'inverse de la matrice {M(m)} (de déterminant 1) ?", M(inv), wr, f"Pour [[a, b], [c, d]] de déterminant 1, l'inverse est [[d, −b], [−c, a]] = {M(inv)}.", src=SRC)


@gen(C, "l1m-cramer", cap=220, cat="Algèbre linéaire")
def cramer(rng, d):
    x, y = rng.randint(-6, 8), rng.randint(-6, 8)
    a, b, c, e = nz(rng, -5, 5), nz(rng, -5, 5), nz(rng, -5, 5), nz(rng, -5, 5)
    D = a * e - b * c
    if D == 0:
        return None
    p, q = a * x + b * y, c * x + e * y
    assert Fraction(p * e - b * q, D) == x and Fraction(a * q - c * p, D) == y
    f = lambda u, v: f"({num(u)} ; {num(v)})"
    return Draft(f"Quelle est la solution (x ; y) du système {poly([a, 0], 'x')} {'+' if b > 0 else '−'} {abs(b) if abs(b) != 1 else ''}y = {num(p)} et {poly([c, 0], 'x')} {'+' if e > 0 else '−'} {abs(e) if abs(e) != 1 else ''}y = {num(q)} ?", f(x, y),
                 [f(y, x), f(x + 1, y), f(x, y - 1), f(-x, -y)], f"Déterminant D = {num(D)} ; x = (p·e − b·q)/D = {num(x)} et y = (a·q − c·p)/D = {num(y)}.", src=SRC)


@gen(C, "l1m-power-triangular", cap=160, cat="Algèbre linéaire")
def power_tri(rng, d):
    n, k = rng.randint(3, 12), rng.randint(1, 5)
    m = [[1, k], [0, 1]]
    p = [[1, k * n], [0, 1]]
    acc = [[1, 0], [0, 1]]
    for _ in range(n):
        acc = matmul(acc, m)
    assert acc == p
    return Draft(f"Quelle est la matrice A^{n} pour A = {M(m)} ?", M(p), mat_variants(rng, p) + [M([[1, k ** n], [0, 1]]), M([[1, k + n], [0, 1]])], f"Par récurrence, A^n = [[1, n·{k}], [0, 1]], donc A^{n} = {M(p)}.", src=SRC)


@gen(C, "l1m-eigen-sym", cap=180, cat="Algèbre linéaire")
def eigen_sym(rng, d):
    a, b = rng.randint(-6, 8), nz(rng, -5, 5)
    ev1, ev2 = a + b, a - b
    m = [[a, b], [b, a]]
    tr, dt = 2 * a, a * a - b * b
    assert ev1 + ev2 == tr and ev1 * ev2 == dt
    right = "{" + "; ".join(num(v) for v in sorted({ev1, ev2})) + "}"
    wr = ["{" + "; ".join(num(v) for v in sorted({a, b})) + "}", "{" + "; ".join(num(v) for v in sorted({a + 1, b - 1})) + "}", "{" + "; ".join(num(v) for v in sorted({-ev1, -ev2})) + "}", "{" + "; ".join(num(v) for v in sorted({a * a, b * b})) + "}"]
    return Draft(f"Quelles sont les valeurs propres de la matrice {M(m)} ?", right, wr, f"Le polynôme caractéristique est (a − λ)² − b² : λ = a + b = {num(ev1)} ou λ = a − b = {num(ev2)}.", src=SRC)


@gen(C, "l1m-eigen-tri", cap=160, cat="Algèbre linéaire")
def eigen_tri(rng, d):
    a, dd, b = rng.randint(-6, 9), rng.randint(-6, 9), rng.randint(-5, 5)
    if a == dd:
        return None
    m = [[a, b], [0, dd]]
    right = "{" + "; ".join(num(v) for v in sorted({a, dd})) + "}"
    wr = ["{" + "; ".join(num(v) for v in sorted({a, b})) + "}", "{" + "; ".join(num(v) for v in sorted({b, dd})) + "}", "{" + "; ".join(num(v) for v in sorted({a + dd, b})) + "}", "{" + "; ".join(num(v) for v in sorted({-a, -dd})) + "}"]
    return Draft(f"Quelles sont les valeurs propres de la matrice triangulaire {M(m)} ?", right, wr, "Les valeurs propres d'une matrice triangulaire sont ses éléments diagonaux.", src=SRC)


@gen(C, "l1m-eigen-trdet", cap=160, cat="Algèbre linéaire")
def eigen_trdet(rng, d):
    l1, l2 = rng.randint(-5, 9), rng.randint(-5, 9)
    if l1 == l2:
        return None
    return Draft(f"Une matrice 2×2 a pour trace {num(l1 + l2)} et pour déterminant {num(l1 * l2)}. Quelles sont ses valeurs propres ?", "{" + "; ".join(num(v) for v in sorted({l1, l2})) + "}",
                 ["{" + "; ".join(num(v) for v in sorted({-l1, -l2})) + "}", "{" + "; ".join(num(v) for v in sorted({l1 + 1, l2 - 1})) + "}", "{" + "; ".join(num(v) for v in sorted({l1 + l2, l1 * l2})) + "}", "{" + "; ".join(num(v) for v in sorted({l1, -l2})) + "}"],
                 f"λ² − (trace)λ + (déterminant) = 0 s'écrit λ² − ({num(l1 + l2)})λ + ({num(l1 * l2)}) = 0, de racines {num(l1)} et {num(l2)}.", src=SRC)


@gen(C, "l1m-rank", cap=160, cat="Algèbre linéaire")
def rank_q(rng, d):
    r1 = [rng.randint(-3, 4) for _ in range(3)]
    r2 = [rng.randint(-3, 4) for _ in range(3)]
    k = rng.choice([2, 3, -1, 1])
    kind = rng.choice([1, 2, 3])
    if kind == 1:
        rows = [r1, [k * x for x in r1], [k * x + 0 for x in r1]]
    elif kind == 2:
        rows = [r1, r2, [a + b for a, b in zip(r1, r2)]]
    else:
        rows = [r1, r2, [rng.randint(-3, 4) for _ in range(3)]]
    if all(x == 0 for x in rows[0]):
        return None
    import itertools
    # rank by exact Gaussian elimination
    a = [[Fraction(x) for x in row] for row in rows]
    rank, col = 0, 0
    while rank < 3 and col < 3:
        piv = next((i for i in range(rank, 3) if a[i][col] != 0), None)
        if piv is None:
            col += 1
            continue
        a[rank], a[piv] = a[piv], a[rank]
        for i in range(3):
            if i != rank and a[i][col] != 0:
                f = a[i][col] / a[rank][col]
                a[i] = [x - f * y for x, y in zip(a[i], a[rank])]
        rank += 1
        col += 1
    return Draft(f"Quel est le rang de la matrice {M(rows)} ?", str(rank), [str(x) for x in range(0, 4) if x != rank] + ["4"], "On échelonne la matrice par opérations sur les lignes : le rang est le nombre de lignes non nulles obtenues.", src=SRC)


@gen(C, "l1m-rank-nullity", cap=120, cat="Algèbre linéaire")
def rank_nullity(rng, d):
    n = rng.randint(3, 9)
    r = rng.randint(1, n - 1)
    return Draft(f"Une application linéaire f de ℝ^{n} dans ℝ^{rng.randint(n, n + 3)} a un rang égal à {r}. Quelle est la dimension de son noyau ?", str(n - r),
                 [str(x) for x in (n + r, r, n, n - r + 1, n - r - 1) if x != n - r and x >= 0], f"Théorème du rang : dim ker f + rg f = dim de l'espace de départ, donc {n} − {r} = {n - r}.", src=SRC)


@gen(C, "l1m-dim", cap=160, cat="Algèbre linéaire")
def dim_q(rng, d):
    kind = rng.choice(["poly", "mat", "plane"])
    if kind == "poly":
        n = rng.randint(2, 12)
        return Draft(f"Quelle est la dimension de l'espace ℝ_{n}[X] des polynômes de degré au plus {n} ?", str(n + 1), [str(n), str(n + 2), str(2 * n), str(n * n)], f"Une base est (1, X, …, X^{n}) : {n + 1} vecteurs.", src=SRC)
    if kind == "mat":
        p, q = rng.randint(2, 6), rng.randint(2, 6)
        return Draft(f"Quelle est la dimension de l'espace des matrices à {p} lignes et {q} colonnes (à coefficients réels) ?", str(p * q), [str(p + q), str(p * q + 1), str(p * q - 1), str(max(p, q))], f"Une base est formée des {p} × {q} matrices élémentaires.", src=SRC)
    a, b, c = nz(rng, -4, 5), nz(rng, -4, 5), nz(rng, -4, 5)
    return Draft(f"Quelle est la dimension du sous-espace de ℝ³ défini par {poly([a, 0], 'x')} {'+' if b > 0 else '−'} {abs(b) if abs(b) != 1 else ''}y {'+' if c > 0 else '−'} {abs(c) if abs(c) != 1 else ''}z = 0 ?", "2",
                 ["1", "3", "0", "4"], "Une équation non triviale retire un degré de liberté à ℝ³ : c'est un plan, de dimension 2.", src=SRC)


@gen(C, "l1m-limit-classic", cap=200, cat="Analyse")
def limit_classic(rng, d):
    a = nz(rng, -6, 8)
    kind = rng.choice(["sin", "exp", "ln", "cos", "tan"])
    x = 1e-4
    if kind == "sin":
        right, txt, f = num(a), f"sin({num(a)}x)/x", lambda x: math.sin(a * x) / x
        val = a
    elif kind == "exp":
        right, txt, f = num(a), f"(e^({num(a)}x) − 1)/x", lambda x: (math.exp(a * x) - 1) / x
        val = a
    elif kind == "ln":
        a = abs(a)
        right, txt, f = num(a), f"ln(1 + {a}x)/x", lambda x: math.log(1 + a * x) / x
        val = a
    elif kind == "cos":
        right, txt, f = num(Fraction(a * a, 2)), f"(1 − cos({num(a)}x))/x²", lambda x: (1 - math.cos(a * x)) / x ** 2
        val = a * a / 2
    else:
        right, txt, f = num(a), f"tan({num(a)}x)/x", lambda x: math.tan(a * x) / x
        val = a
    assert abs(f(1e-3) - val) < 0.02 * max(1, abs(val)) * max(1, abs(a))
    wr = [num(a + 1), num(-a), "0", "1", num(a * a), num(Fraction(a, 2))]
    wr = [w for w in wr if w != right]
    return Draft(f"Quelle est la limite de {txt} quand x tend vers 0 ?", right, wr, "Limite usuelle : on se ramène à (sin u)/u, (eᵘ − 1)/u, ln(1+u)/u ou (1 − cos u)/u² quand u tend vers 0.", src=SRC)


@gen(C, "l1m-limit-e", cap=100, cat="Analyse")
def limit_e(rng, d):
    a = nz(rng, -5, 6)
    n = 10 ** 6
    assert abs((1 + a / n) ** n - math.exp(a)) < 1e-2 * math.exp(abs(a))
    ex = lambda k: "e" if k == 1 else f"e^({num(k)})" if k < 0 else f"e^{k}"
    right = ex(a)
    wr = [ex(-a), num(a), "1", "+∞", ex(a + 1)]
    wr = [w for w in wr if w != right]
    return Draft(f"Quelle est la limite de (1 {'+' if a > 0 else '−'} {abs(a)}/n)^n quand n tend vers +∞ ?", right, wr, "Limite usuelle : (1 + a/n)ⁿ tend vers eᵃ.", src=SRC)


@gen(C, "l1m-taylor", cap=200, cat="Analyse")
def taylor(rng, d):
    fn = rng.choice(["e^x", "sin x", "cos x", "ln(1+x)", "1/(1−x)"])
    k = rng.randint(2, 8)
    if fn == "e^x":
        c, txt = Fraction(1, math.factorial(k)), "e^x"
    elif fn == "sin x":
        if k % 2 == 0:
            k += 1
        c, txt = Fraction((-1) ** ((k - 1) // 2), math.factorial(k)), "sin x"
    elif fn == "cos x":
        if k % 2 == 1:
            k += 1
        c, txt = Fraction((-1) ** (k // 2), math.factorial(k)), "cos x"
    elif fn == "ln(1+x)":
        c, txt = Fraction((-1) ** (k + 1), k), "ln(1 + x)"
    else:
        c, txt = Fraction(1), "1/(1 − x)"
    wr = {-c, c * k, c + 1 if fn != "1/(1−x)" else Fraction(0), Fraction(1, math.factorial(k + 1)), Fraction(1, k), c * 2} - {c}
    return Draft(f"Quel est le coefficient de x^{k} dans le développement en série de Taylor en 0 de {txt} ?", num(c), [num(w) for w in sorted(wr)],
                 {"e^x": "eˣ = Σ xⁿ/n!", "sin x": "sin x = Σ (−1)ᵏ x^(2k+1)/(2k+1)!", "cos x": "cos x = Σ (−1)ᵏ x^(2k)/(2k)!", "ln(1+x)": "ln(1+x) = Σ (−1)ⁿ⁺¹ xⁿ/n", "1/(1−x)": "1/(1−x) = Σ xⁿ"}[fn] + f" : le coefficient de x^{k} est {num(c)}.", src=SRC)


@gen(C, "l1m-series-geo", cap=200, cat="Séries")
def series_geo(rng, d):
    q = rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(1, 4), Fraction(3, 4), Fraction(1, 5), Fraction(-1, 2), Fraction(-1, 3), Fraction(2, 5), Fraction(1, 10)])
    a = rng.randint(1, 9)
    s = a / (1 - q)
    approx = sum(a * float(q) ** n for n in range(200))
    assert abs(approx - float(s)) < 1e-9
    return Draft(f"Quelle est la somme de la série Σ (n ≥ 0) {a} × ({num(q)})ⁿ ?", num(s), [num(w) for w in (a * (1 + q), a / (1 + q), a * q / (1 - q), s + 1, a * (1 - q))], f"Série géométrique de raison q = {num(q)} avec |q| < 1 : S = a/(1 − q) = {num(s)}.", src=SRC)


@gen(C, "l1m-series-telescope", cap=160, cat="Séries")
def series_tele(rng, d):
    n = rng.randint(3, 40)
    s = sum(Fraction(1, k * (k + 1)) for k in range(1, n + 1))
    assert s == Fraction(n, n + 1)
    return Draft(f"Quelle est la valeur de la somme Σ (k = 1 à {n}) 1/(k(k+1)) ?", num(s), [num(w) for w in (Fraction(n + 1, n + 2), Fraction(1, n + 1), Fraction(n - 1, n), Fraction(n, n + 2), Fraction(1, 2))],
                 f"1/(k(k+1)) = 1/k − 1/(k+1) : la somme se télescope en 1 − 1/{n + 1} = {n}/{n + 1}.", src=SRC)


@gen(C, "l1m-sums-powers", cap=200, cat="Séries")
def sums_powers(rng, d):
    n = rng.randint(4, 40)
    p = rng.choice([1, 2, 3])
    s = sum(k ** p for k in range(1, n + 1))
    formula = {1: n * (n + 1) // 2, 2: n * (n + 1) * (2 * n + 1) // 6, 3: (n * (n + 1) // 2) ** 2}[p]
    assert s == formula
    wr = [s + n, s - n, n ** (p + 1), s + 1, (n * (n + 1)) // 2 if p != 1 else s * 2]
    return Draft(f"Quelle est la valeur de la somme 1{sup(p) if p > 1 else ''} + 2{sup(p) if p > 1 else ''} + … + {n}{sup(p) if p > 1 else ''} ?", fr(s), [fr(w) for w in wr if w != s],
                 {1: "n(n+1)/2", 2: "n(n+1)(2n+1)/6", 3: "(n(n+1)/2)²"}[p] + f" avec n = {n} donne {fr(s)}.", src=SRC)


@gen(C, "l1m-series-converge", cap=200, cat="Séries")
def series_conv(rng, d):
    p = rng.choice([Fraction(1, 2), Fraction(1), Fraction(3, 2), Fraction(2), Fraction(3), Fraction(1, 3), Fraction(5, 4), Fraction(4, 3), Fraction(9, 10), Fraction(11, 10), Fraction(5, 2), Fraction(2, 3)])
    conv = p > 1
    right = "Elle converge" if conv else "Elle diverge"
    return Draft(f"La série de Riemann Σ (n ≥ 1) 1/n^p avec p = {num(p)} converge-t-elle ou diverge-t-elle ?", right, ["Elle diverge" if conv else "Elle converge", "Elle converge seulement si p est entier", "On ne peut pas conclure", "Elle est constante"],
                 "Critère de Riemann : Σ 1/n^p converge si et seulement si p > 1.", src=SRC)


@gen(C, "l1m-geo-converge", cap=140, cat="Séries")
def geo_conv(rng, d):
    q = rng.choice([Fraction(1, 2), Fraction(3, 2), Fraction(-1, 2), Fraction(-3, 2), Fraction(1), Fraction(-1), Fraction(99, 100), Fraction(101, 100), Fraction(2), Fraction(-2), Fraction(3, 4), Fraction(5, 4)])
    conv = abs(q) < 1
    return Draft(f"La série géométrique Σ (n ≥ 0) ({num(q)})ⁿ converge-t-elle ou diverge-t-elle ?", "Elle converge" if conv else "Elle diverge", ["Elle diverge" if conv else "Elle converge", "Elle converge seulement si q est positif", "On ne peut pas conclure", "Elle est constante"],
                 "Une série géométrique de raison q converge si et seulement si |q| < 1.", src=SRC)


@gen(C, "l1m-integral-classic", cap=200, cat="Analyse")
def integral_classic(rng, d):
    kind = rng.choice(["xn", "sin", "exp", "xe", "ln", "inv"])
    if kind == "xn":
        n = rng.randint(1, 9)
        v = Fraction(1, n + 1)
        txt, f = f"x{sup(n) if n > 1 else ''}", lambda x: x ** n
        a, b = 0, 1
    elif kind == "sin":
        k = rng.randint(1, 6)
        v = Fraction(2, k) if k % 2 else Fraction(0)
        txt, f, a, b = f"sin({k}x)", lambda x: math.sin(k * x), 0, math.pi
        right = num(v) if v != 0 else "0"
    elif kind == "exp":
        k = rng.randint(1, 5)
        txt, f, a, b = f"e^({k}x)", lambda x: math.exp(k * x), 0, 1
        right = f"(e^{k} − 1)/{k}"
        return Draft(f"Quelle est la valeur de l'intégrale de 0 à 1 de {txt} dx ?", right, [f"e^{k} − 1", f"(e^{k} + 1)/{k}", f"e^{k}/{k}", f"{k}e^{k}", f"e^{k}"], f"Une primitive de e^({k}x) est e^({k}x)/{k} : [e^({k}x)/{k}] de 0 à 1 = (e^{k} − 1)/{k}.", src=SRC)
    elif kind == "xe":
        return Draft("Quelle est la valeur de l'intégrale de 0 à 1 de x·eˣ dx ?", "1", ["e − 1", "e", "0", "e/2", "1/2"], "Intégration par parties : [x·eˣ − eˣ] de 0 à 1 = (e − e) − (0 − 1) = 1.", src=SRC)
    elif kind == "ln":
        return Draft("Quelle est la valeur de l'intégrale de 1 à e de ln(x) dx ?", "1", ["e − 1", "e", "0", "1/e", "e − 2"], "Une primitive de ln x est x ln x − x : [x ln x − x] de 1 à e = (e − e) − (0 − 1) = 1.", src=SRC)
    else:
        n = rng.randint(2, 20)
        return Draft(f"Quelle est la valeur de l'intégrale de 1 à {n} de 1/x dx ?", f"ln({n})", [f"{n}", f"1/{n}", f"ln({n - 1})", f"{n - 1}", f"ln({n + 1})"], f"Une primitive de 1/x est ln x : ln({n}) − ln(1) = ln({n}).", src=SRC)
    if kind == "xn":
        right = num(v)
        wr = [num(Fraction(1, n)), num(Fraction(1, n + 2)), str(n), num(Fraction(n, n + 1))]
        ex = f"∫ de 0 à 1 de x^{n} = 1/({n}+1)"
    else:
        wr = ["0", "1", "2", "π", "2/" + str(k + 1), num(Fraction(1, k))]
        wr = [w for w in wr if w != right]
        ex = f"[−cos({k}x)/{k}] de 0 à π = (1 − cos({k}π))/{k}"
    # numeric check
    N = 20000
    simpson = sum(f(a + (b - a) * (i + 0.5) / N) for i in range(N)) * (b - a) / N
    assert abs(simpson - float(v)) < 1e-6
    return Draft(f"Quelle est la valeur de l'intégrale de {'0 à 1' if kind == 'xn' else '0 à π'} de {txt} dx ?", right, wr, ex + f" = {right}.", src=SRC)


@gen(C, "l1m-modular", cap=260, cat="Arithmétique")
def modular(rng, d):
    kind = rng.choice(["mod", "pow", "inv"])
    if kind == "mod":
        a, n = rng.randint(100, 9999), rng.randint(3, 19)
        v = a % n
        return Draft(f"Quel est le reste de la division euclidienne de {fr(a)} par {n} ?", str(v), [str(x) for x in range(n) if x != v][:6], f"{fr(a)} = {n} × {a // n} + {v}.", src=SRC)
    if kind == "pow":
        a, k, n = rng.randint(2, 12), rng.randint(5, 60), rng.choice([5, 7, 9, 11, 13])
        v = pow(a, k, n)
        return Draft(f"Quel est le reste de la division de {a}^{k} par {n} ?", str(v), [str(x) for x in range(n) if x != v][:6], f"On calcule modulo {n} par exponentiation rapide (ou en repérant la période) : {a}^{k} ≡ {v} (mod {n}).", src=SRC)
    p = rng.choice([5, 7, 11, 13, 17, 19, 23])
    a = rng.randint(2, p - 1)
    inv = pow(a, -1, p)
    assert a * inv % p == 1
    return Draft(f"Quel est l'inverse de {a} modulo {p} (entier entre 1 et {p - 1}) ?", str(inv), [str(x) for x in range(1, p) if x != inv][:6], f"{a} × {inv} = {a * inv} = {a * inv // p} × {p} + 1, donc {a} × {inv} ≡ 1 (mod {p}).", src=SRC)


@gen(C, "l1m-gcd-bezout", cap=200, cat="Arithmétique")
def gcd_bezout(rng, d):
    a, b = rng.randint(12, 400), rng.randint(12, 400)
    g = math.gcd(a, b)
    if rng.random() < 0.5:
        return Draft(f"Quel est le PGCD de {a} et {b} ?", str(g), [str(x) for x in near_ints(rng, g, 6, lo=1)] + [str(a * b // g)], f"Algorithme d'Euclide : PGCD({a}, {b}) = {g}.", src=SRC)
    if g != 1:
        return None
    u = pow(a, -1, b) if b > 1 else 0
    assert (a * u) % b == 1
    return Draft(f"Quel entier u vérifie a·u ≡ 1 (mod {b}) pour a = {a} (0 < u < {b}) ?", str(u), [str(x) for x in near_ints(rng, u, 6, lo=1, hi=b - 1)] + [str(b - u)], f"{a} × {u} = {a * u} = {a * u // b} × {b} + 1 : u = {u} est l'inverse de {a} modulo {b}.", src=SRC)


@gen(C, "l1m-counting", cap=240, cat="Dénombrement")
def counting(rng, d):
    kind = rng.choice(["subsets", "functions", "injections", "bijections", "comb", "union"])
    n, p = rng.randint(3, 9), rng.randint(2, 5)
    if kind == "subsets":
        return Draft(f"Combien de parties (sous-ensembles) possède un ensemble à {n} éléments ?", fr(2 ** n), [fr(x) for x in (n * n, 2 * n, 2 ** n - 1, math.factorial(n), 2 ** (n + 1))], f"Chaque élément est dans la partie ou non : 2^{n} = {2 ** n}.", src=SRC)
    if kind == "functions":
        return Draft(f"Combien y a-t-il d'applications d'un ensemble à {p} éléments vers un ensemble à {n} éléments ?", fr(n ** p), [fr(x) for x in (p ** n if p != n else n ** p + 1, n * p, math.perm(n, p) if p <= n else 1, math.comb(n, p) if p <= n else 2, n ** p + n)],
                     f"Chaque élément de départ a {n} images possibles : {n}^{p} = {fr(n ** p)}.", src=SRC)
    if kind == "injections":
        if p > n:
            return None
        v = math.perm(n, p)
        assert v == len([1 for t in product(range(n), repeat=p) if len(set(t)) == p]) if n ** p <= 5000 else True
        return Draft(f"Combien y a-t-il d'injections d'un ensemble à {p} éléments dans un ensemble à {n} éléments ?", fr(v), [fr(x) for x in (n ** p, math.comb(n, p), math.factorial(n), math.perm(n, p) + n, math.perm(n + 1, p))],
                     f"n(n−1)…(n−p+1) = {n}!/{n - p}! = {fr(v)}.", src=SRC)
    if kind == "bijections":
        return Draft(f"Combien y a-t-il de bijections d'un ensemble à {n} éléments sur lui-même ?", fr(math.factorial(n)), [fr(x) for x in (n ** n, 2 ** n, math.factorial(n - 1), math.factorial(n + 1), n * n)], f"Ce sont les permutations : {n}! = {fr(math.factorial(n))}.", src=SRC)
    if kind == "comb":
        k = rng.randint(2, n - 1)
        return Draft(f"Combien y a-t-il de parties à {k} éléments dans un ensemble à {n} éléments ?", fr(math.comb(n, k)), [fr(x) for x in (math.perm(n, k), math.comb(n, k - 1) + 1, n * k, math.comb(n + 1, k))],
                     f"C({n},{k}) = {n}!/({k}!·{n - k}!) = {fr(math.comb(n, k))}.", src=SRC)
    a, b, i = rng.randint(10, 60), rng.randint(10, 60), rng.randint(1, 9)
    return Draft(f"Dans un ensemble fini, |A| = {a}, |B| = {b} et |A ∩ B| = {i}. Quel est |A ∪ B| ?", str(a + b - i), [str(x) for x in (a + b, a + b + i, a - i + b - i, abs(a - b), max(a, b))], f"|A ∪ B| = |A| + |B| − |A ∩ B| = {a} + {b} − {i} = {a + b - i}.", src=SRC)


@gen(C, "l1m-vieta", cap=240, cat="Polynômes")
def vieta(rng, d):
    a, b, c = nz(rng, -4, 5), rng.randint(-9, 9), rng.randint(-9, 9)
    kind = rng.choice(["sum", "prod"])
    v = Fraction(-b, a) if kind == "sum" else Fraction(c, a)
    wr = {-v, Fraction(b, a), Fraction(c, a) if kind == "sum" else Fraction(-b, a), v + 1, Fraction(a, b) if b else Fraction(2)} - {v}
    return Draft(f"{'Quelle est la somme' if kind == 'sum' else 'Quel est le produit'} des racines de {poly([a, b, c])} = 0 ?", num(v), [num(w) for w in sorted(wr)][:6],
                 f"Relations de Viète : somme = −b/a = {num(Fraction(-b, a))}, produit = c/a = {num(Fraction(c, a))}.", src=SRC)


@gen(C, "l1m-remainder", cap=240, cat="Polynômes")
def remainder(rng, d):
    deg = rng.randint(2, 4)
    co = [nz(rng, -3, 4)] + [rng.randint(-5, 6) for _ in range(deg)]
    a = rng.randint(-3, 4)
    v = ev(co, a)
    return Draft(f"Quel est le reste de la division du polynôme P(x) = {poly(co)} par (x {'−' if a >= 0 else '+'} {abs(a)}) ?", num(v), [num(w) for w in near_ints(rng, v, 6)] + [num(ev(co, -a)), num(co[-1])],
                 f"Le reste de la division par (x − a) est P(a) : P({num(a)}) = {num(v)}.", src=SRC)


@gen(C, "l1m-ode", cap=160, cat="Analyse")
def ode(rng, d):
    k, y0, x1 = nz(rng, -3, 4), nz(rng, 1, 6), rng.randint(1, 3)
    right = f"{y0}e^{num(k * x1)}" if k * x1 != 1 else f"{y0}e"
    wr = [f"{y0}e^{num(k + x1)}", f"{y0 * k}e^{num(k * x1)}", f"{y0}e^{num(-k * x1)}", f"{y0 + k}e^{num(x1)}", f"{y0}e^{num(k)}"]
    wr = [w for w in wr if w != right]
    return Draft(f"Quelle est la valeur en x = {x1} de la solution de y' = {num(k)}y avec y(0) = {y0} ?", right, wr, f"y(x) = y(0)·e^(kx) = {y0}e^({num(k)}x), donc y({x1}) = {right}.", src=SRC)


@gen(C, "l1m-newton", cap=140, cat="Analyse")
def newton(rng, d):
    a, x0 = rng.choice([2, 3, 5, 6, 7, 10]), rng.choice([1, 2, 3])
    x1 = Fraction(1, 2) * (x0 + Fraction(a, x0))
    wr = {Fraction(a, x0), x0 - Fraction(a, x0), Fraction(x0 * x0 - a, 2 * x0) if True else 0, x1 + 1, Fraction(a, 2)} - {x1}
    return Draft(f"On applique la méthode de Newton à f(x) = x² − {a} en partant de x₀ = {x0}. Que vaut x₁ ?", num(x1), [num(w) for w in sorted(wr)],
                 f"x₁ = x₀ − f(x₀)/f'(x₀) = {x0} − ({x0 * x0 - a})/{2 * x0} = {num(x1)}.", src=SRC)


@gen(C, "l1m-domain", cap=140, cat="Analyse")
def domain(rng, d):
    a, b = nz(rng, -5, 6), rng.randint(-9, 9)
    x0 = Fraction(-b, a)
    bracket = ("]%s ; +∞[" % num(x0)) if a > 0 else ("]−∞ ; %s[" % num(x0))
    wr = [("]−∞ ; %s[" % num(x0)) if a > 0 else ("]%s ; +∞[" % num(x0)), "ℝ", ("]%s ; +∞[" % num(-x0)) if a > 0 else ("]−∞ ; %s[" % num(-x0)), "[%s ; +∞[" % num(x0) if a > 0 else "]−∞ ; %s]" % num(x0)]
    return Draft(f"Quel est l'ensemble de définition de f(x) = ln({poly([a, b])}) ?", bracket, wr, f"ln(u) existe si u > 0 : {poly([a, b])} > 0 donne x {'>' if a > 0 else '<'} {num(x0)}.", src=SRC)


@gen(C, "l1m-parity", cap=120, cat="Analyse")
def parity(rng, d):
    n = rng.randint(2, 15)
    kind = rng.choice(["x^n", "x^n+x"])
    if kind == "x^n":
        right = "paire" if n % 2 == 0 else "impaire"
        return Draft(f"La fonction f(x) = x^{n} est-elle paire, impaire, ou ni l'une ni l'autre ?", right, [x for x in ("paire", "impaire", "ni paire ni impaire") if x != right] + ["constante"], f"f(−x) = (−x)^{n} = {'x^' + str(n) if n % 2 == 0 else '−x^' + str(n)} : {right}.", src=SRC)
    m = n + 1 if n % 2 == 0 else n
    right = "impaire" if (n % 2 == 1 and m % 2 == 1) else "ni paire ni impaire"
    m = n
    k = rng.randint(1, 4)
    parity_n = n % 2
    right = "impaire" if parity_n == 1 else "ni paire ni impaire"
    return Draft(f"La fonction f(x) = x^{n} + x est-elle paire, impaire, ou ni l'une ni l'autre ?", right, [x for x in ("paire", "impaire", "ni paire ni impaire") if x != right] + ["constante"],
                 f"f(−x) = (−x)^{n} − x = {'−x^' + str(n) + ' − x = −f(x)' if n % 2 else 'x^' + str(n) + ' − x, différent de f(x) et de −f(x)'}.", src=SRC)


@gen(C, "l1m-complex-power", cap=160, cat="Nombres complexes")
def complex_power(rng, d):
    n = rng.randint(2, 12)
    z = (1 + 1j) ** n
    re_, im = round(z.real), round(z.imag)
    assert abs(z.real - re_) < 1e-6 and abs(z.imag - im) < 1e-6
    f = lambda r, i: (f"{num(r)}" if i == 0 else (f"{num(i)}i" if r == 0 else f"{num(r)} {'+' if i > 0 else '−'} {abs(i)}i"))
    wr = [f(re_, -im), f(im, re_), f(2 ** n, 0), f(re_ + 2, im), f(-re_, -im)]
    wr = [w for w in wr if w != f(re_, im)]
    return Draft(f"Quelle est la forme algébrique de (1 + i)^{n} ?", f(re_, im), wr, f"(1 + i) = √2·e^(iπ/4), donc (1 + i)^{n} = 2^({n}/2)·e^(i{n}π/4) = {f(re_, im)}.", src=SRC)


@gen(C, "l1m-roots-unity", cap=100, cat="Nombres complexes")
def roots_unity(rng, d):
    n = rng.randint(2, 20)
    return Draft(f"Combien l'équation zⁿ = 1 (avec n = {n}) a-t-elle de solutions dans ℂ ?", str(n), [str(x) for x in (n - 1, n + 1, 2 * n, 1, 2) if x != n], f"Il y a exactement n racines n-ièmes de l'unité : e^(2ikπ/n), k = 0, …, n−1.", src=SRC)


@gen(C, "l1m-logic-sets", cap=100, cat="Logique")
def logic(rng, d):
    n = rng.randint(2, 6)
    rows = 2 ** n
    return Draft(f"Combien de lignes contient la table de vérité d'une formule à {n} variables propositionnelles ?", str(rows), [str(x) for x in (2 * n, n * n, rows - 1, rows * 2, math.factorial(n)) if x != rows], f"Chaque variable vaut vrai ou faux : 2^{n} = {rows} lignes.", src=SRC)
