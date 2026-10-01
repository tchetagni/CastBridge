"""GCE Mathematics in English: Ordinary Level (Form 5) and Advanced Level Pure Mathematics / Statistics (Lower and Upper Sixth).
Every answer is recomputed in the generator (substitution, exact fractions, numerical derivative or integral)."""
import math
from fractions import Fraction

from .. import lycee
from ..core import MINUS, Draft, gen
from .lycee_common import E, ints, tidy
from .mathfmt import dpoly, ev, nz, num, poly, sup

O5, A6, A12 = lycee.en("f5", "math"), lycee.en("l6", "math"), lycee.en("u6", "math")
SO = "GCE O Level Mathematics syllabus topic - answer computed and checked"
SA = "GCE A Level Mathematics syllabus topic - answer computed and checked"


def reg(courses, tpl, src, cat, **kw):
    def deco(fn):
        for c in courses:
            gen(c, tpl, cat=cat, **kw)(lambda rng, d, fn=fn, src=src: (lambda r: tidy(r) if r is not None else None)(fn(rng, d, src)))
        return fn
    return deco


def close(a, b, tol=1e-4):
    return abs(a - b) <= tol * max(1, abs(a), abs(b))


def numdiff(f, x, h=1e-6):
    return (f(x + h) - f(x - h)) / (2 * h)


def lin(a, b):
    return poly([a, b])


# ================================================================================================ O Level
@reg([O5], "en-m-indices", SO, "Indices and standard form")
def indices(rng, d, src):
    a, m, n = rng.choice([2, 3, 5, 10]), rng.randint(2, 7), rng.randint(2, 7)
    kind = rng.choice(["mul", "div", "pow"])
    if kind == "mul":
        r, wr, t = m + n, [m * n, m - n if m != n else 1, m + n + 1, m + n - 1], f"{a}{sup(m)} × {a}{sup(n)}"
    elif kind == "div":
        if m <= n:
            m, n = n + 1, m
        r, wr, t = m - n, [m + n, m * n, m // n if m % n == 0 and m // n != m - n else m - n + 2, m - n + 1], f"{a}{sup(m)} ÷ {a}{sup(n)}"
    else:
        r, wr, t = m * n, [m + n, m * n + 2, m * n + 1, m * n - 1], f"({a}{sup(m)}){sup(n)}"
    return Draft(f"Write {t} in the form {a}ⁿ. What is n?", str(r), [str(w) for w in wr if w != r and w > 0], "Laws of indices: aᵐ×aⁿ = aᵐ⁺ⁿ, aᵐ÷aⁿ = aᵐ⁻ⁿ, (aᵐ)ⁿ = aᵐⁿ.", src=src)


@reg([O5], "en-m-standard-form", SO, "Indices and standard form")
def standard_form(rng, d, src):
    m = rng.choice([1.5, 2.4, 3.6, 4.2, 5, 6.7, 7.8, 8.1, 9.2])
    e = rng.choice([-5, -4, -3, 3, 4, 5, 6, 7])
    val = m * 10 ** e
    if e < 0:
        dec = ("0." + "0" * (-e - 1) + str(m).replace(".", ""))
    else:
        dec = str(int(round(m * 10 ** e)))
    right = f"{E(m)} × 10{sup(e)}"
    wr = [f"{E(m)} × 10{sup(-e)}", f"{E(m * 10)} × 10{sup(e - 1)}" if False else f"{E(m)} × 10{sup(e + 1)}", f"{E(m / 10)} × 10{sup(e + 1)}", f"{E(m)} × 10{sup(e - 1)}"]
    return Draft(f"Write {dec} in standard form.", right, wr, "Standard form is A × 10ⁿ with 1 ≤ A < 10.", src=src)


@reg([O5], "en-m-quadratic-factor", SO, "Algebra")
def quad_factor(rng, d, src):
    a, b = rng.randint(-8, 8), rng.randint(-8, 8)
    if a == b:
        return None
    s, p = a + b, a * b
    right = " or ".join(num(v) for v in sorted((a, b)))
    return Draft(f"Solve {poly([1, -s, p])} = 0.", "x = " + right.replace(" or ", " or x = "), ["x = " + " or x = ".join(num(v) for v in sorted((-a, -b))), "x = " + " or x = ".join(num(v) for v in sorted((a, -b))), "x = " + " or x = ".join(num(v) for v in sorted((s, p))), "x = " + " or x = ".join(num(v) for v in sorted((a + 1, b)))],
                 f"Find two numbers with sum {num(s)} and product {num(p)}: {num(a)} and {num(b)}.", src=src)


@reg([O5], "en-m-simultaneous", SO, "Algebra")
def simultaneous(rng, d, src):
    x, y = rng.randint(-5, 8), rng.randint(-5, 8)
    a, b, c, e = rng.randint(1, 5), rng.randint(1, 5), rng.randint(1, 5), rng.randint(-5, -1)
    det = a * e - b * c
    if det == 0:
        return None
    r1, r2 = a * x + b * y, c * x + e * y
    t = lambda p, q: f"{p if p != 1 else ''}x {'+' if q >= 0 else '−'} {abs(q) if abs(q) != 1 else ''}y"
    return Draft(f"Solve the simultaneous equations {t(a, b)} = {num(r1)} and {t(c, e)} = {num(r2)}. What is x + y?", num(x + y), [num(x - y), num(x * y), num(x + y + 1), num(x + y - 1), num(-x - y)] if x * y != x + y else ["1", "2", "3"],
                 f"Solving gives x = {num(x)} and y = {num(y)}.", src=src, diff=3)


@reg([O5], "en-m-percent-profit", SO, "Arithmetic")
def percent_profit(rng, d, src):
    cp, pct = rng.choice([200, 400, 500, 800, 1000, 2500]), rng.choice([5, 10, 12, 15, 20, 25, 30, 40])
    sp = cp * (100 + pct) // 100 if cp * (100 + pct) % 100 == 0 else None
    if sp is None:
        return None
    return Draft(f"A trader buys an article for {cp} FCFA and sells it for {sp} FCFA. What is the percentage profit?", f"{pct} %", [f"{pct * 2} %", f"{round((sp - cp) / sp * 100, 1)} %".replace(".0", ""), f"{sp - cp} %", f"{pct + 5} %", f"{max(1, pct - 5)} %"],
                 "Percentage profit = profit ÷ cost price × 100 %.", src=src)


@reg([O5], "en-m-compound-interest", SO, "Arithmetic")
def compound_interest(rng, d, src):
    P, r, n = rng.choice([1000, 2000, 5000, 10000, 20000]), rng.choice([5, 10, 20]), rng.choice([2, 3])
    amount = P * (1 + r / 100) ** n
    simple = P * (1 + r * n / 100)
    return Draft(f"{P} FCFA is invested at {r} % compound interest per year for {n} years. What is the final amount, in FCFA?", E(round(amount, 2)), [E(round(simple, 2)), E(round(P * (1 + r / 100), 2)), E(round(amount + P * r / 100, 2)), E(round(P * (1 + r / 100) ** (n + 1), 2)), E(round(amount - P, 2))],
                 f"A = P(1 + r/100)ⁿ = {P} × {E(1 + r / 100)}{sup(n)}.", src=src, diff=3)


@reg([O5], "en-m-mean-median-mode", SO, "Statistics")
def mean_median(rng, d, src):
    n = rng.choice([5, 7, 9])
    data = [rng.randint(2, 20) for _ in range(n)]
    srt = sorted(data)
    kind = rng.choice(["median", "range", "mean"])
    if kind == "median":
        v = srt[n // 2]; ans = str(v); wr = [str(x) for x in (srt[n // 2 - 1], srt[n // 2 + 1], data[n // 2], round(sum(data) / n), v + 1) if x != v]
    elif kind == "range":
        v = srt[-1] - srt[0]; ans = str(v); wr = [str(x) for x in (srt[-1], srt[0], v + 1, v - 1, sum(data) // n) if x != v]
    else:
        s = sum(data)
        data[0] += (-s) % n
        s = sum(data)
        v = s // n; ans = str(v); wr = [str(x) for x in (sorted(data)[n // 2], max(data), v + 1, v - 1, s) if x != v]
    return Draft(f"Find the {kind} of the numbers {', '.join(map(str, data))}.", ans, wr, {"median": "Order the data: the median is the middle value.", "range": "Range = largest − smallest.", "mean": "Mean = sum ÷ number of values."}[kind], src=src)


@reg([O5], "en-m-probability-bag", SO, "Probability")
def prob_bag(rng, d, src):
    r, b, g = rng.randint(2, 9), rng.randint(2, 9), rng.randint(1, 8)
    tot = r + b + g
    p = Fraction(r, tot)
    return Draft(f"A bag contains {r} red, {b} blue and {g} green balls. One ball is picked at random. What is the probability that it is red?", num(p), [num(Fraction(r, b + g)), num(Fraction(1, 3)), num(Fraction(tot - r, tot)), num(Fraction(r, r + b)), num(Fraction(b, tot))],
                 f"P(red) = {r}/{tot}" + (f" = {num(p)}." if p.denominator != tot else "."), src=src)


@reg([O5], "en-m-sets", SO, "Sets")
def sets(rng, d, src):
    a, b, both, neither = rng.randint(8, 30), rng.randint(8, 30), rng.randint(1, 7), rng.randint(0, 8)
    total = a + b - both + neither
    return Draft(f"In a class, {a} students study French, {b} study Spanish and {both} study both. {neither} study neither. How many students are in the class?", str(total), [str(w) for w in (a + b + neither, a + b - both, a + b + both + neither, total - 1, a + b - 2 * both + neither)],
                 "n(A ∪ B) = n(A) + n(B) − n(A ∩ B); add those who study neither.", src=src)


@reg([O5], "en-m-cylinder", SO, "Mensuration")
def cylinder(rng, d, src):
    r, h = rng.choice([1, 2, 3, 5, 7, 10]), rng.choice([2, 4, 5, 10, 14])
    v = 22 / 7 * r * r * h
    ans = E(round(v, 1))
    return Draft(f"Taking π = 22/7, what is the volume of a cylinder of radius {r} cm and height {h} cm, in cm³ to 1 decimal place?", ans, [E(round(22 / 7 * r * h * 2, 1)), E(round(22 / 7 * r * r * h / 3, 1)), E(round(22 / 7 * r * h, 1)), E(round(22 / 7 * r * r, 1)), E(round(v * 2, 1))],
                 "Volume = πr²h.", src=src)


@reg([O5], "en-m-pythag-trig", SO, "Trigonometry")
def pythag_trig(rng, d, src):
    u, v, w = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (6, 8, 10), (7, 24, 25)])
    f = rng.choice(["sin", "cos", "tan"])
    val = {"sin": Fraction(u, w), "cos": Fraction(v, w), "tan": Fraction(u, v)}[f]
    others = [Fraction(v, w), Fraction(u, w), Fraction(u, v), Fraction(v, u), Fraction(w, u)]
    return Draft(f"In a right-angled triangle the side opposite angle θ is {u} cm, the adjacent side is {v} cm and the hypotenuse is {w} cm. What is {f} θ?", num(val), [num(x) for x in others if x != val][:5],
                 "SOH-CAH-TOA: sin = opp/hyp, cos = adj/hyp, tan = opp/adj.", src=src)


@reg([O5], "en-m-direct-variation", SO, "Variation")
def variation(rng, d, src):
    k, x0, x1 = rng.choice([2, 3, 4, 5, 6, 8]), rng.choice([2, 3, 4, 5]), rng.choice([6, 7, 8, 9, 10])
    y0 = k * x0
    y1 = k * x1
    return Draft(f"y varies directly as x. When x = {x0}, y = {y0}. What is y when x = {x1}?", str(y1), [str(w) for w in (y0 + x1 - x0, y0 * x1, k + x1, Fraction(y0 * x0, x1) if (y0 * x0) % x1 == 0 else y1 + 2, y1 + k)],
                 f"y = kx with k = {y0}/{x0} = {k}.", src=src)


@reg([O5], "en-m-linear-gradient", SO, "Coordinate geometry")
def gradient(rng, d, src):
    xa, ya = rng.randint(-5, 5), rng.randint(-5, 5)
    dx, dy = nz(rng, -5, 5), rng.randint(-8, 8)
    m = Fraction(dy, dx)
    return Draft(f"Find the gradient of the line through A({num(xa)}, {num(ya)}) and B({num(xa + dx)}, {num(ya + dy)}).", num(m), [num(-m), num(Fraction(dx, dy)) if dy else "5", num(m + 1), num(m - 1), num(dy + dx)],
                 "Gradient = (y₂ − y₁)/(x₂ − x₁).", src=src)


@reg([O5], "en-m-bearings-angles", SO, "Geometry")
def polygon_angles(rng, d, src):
    n = rng.choice([5, 6, 8, 9, 10, 12])
    s = (n - 2) * 180
    ask = rng.choice(["sum", "regular"])
    if ask == "sum":
        return Draft(f"What is the sum of the interior angles of a polygon with {n} sides?", f"{s}°", [f"{s + 180}°", f"{s - 180}°", f"{n * 180}°", f"{360}°", f"{s // 2}°"], "Sum = (n − 2) × 180°.", src=src)
    if s % n:
        return None
    return Draft(f"What is the size of each interior angle of a regular polygon with {n} sides?", f"{s // n}°", [f"{360 // n}°" if 360 % n == 0 else f"{s // n + 5}°", f"{180 - (s // n) // 2}°", f"{s // n + 10}°", f"{s // n - 10}°", f"{s // n // 2}°"],
                 "Interior angle = (n − 2) × 180° ÷ n.", src=src)


@reg([O5], "en-m-matrix-det", SO, "Matrices")
def matrix_det(rng, d, src):
    a, b, c, e = (rng.randint(-5, 7) for _ in range(4))
    det = a * e - b * c
    return Draft(f"Find the determinant of the 2 × 2 matrix whose first row is {num(a)}, {num(b)} and whose second row is {num(c)}, {num(e)}.", num(det), [num(a * e + b * c), num(a * b - c * e), num(a + e - b - c), num(-det) if det else "1", num(a * c - b * e)],
                 "det = ad − bc.", src=src)


@reg([O5], "en-m-log-basic", SO, "Logarithms")
def log_basic(rng, d, src):
    a, n = rng.choice([2, 3, 4, 5, 10]), rng.randint(2, 5)
    v = a ** n
    return Draft(f"What is the value of log base {a} of {v}?", str(n), [str(w) for w in (v // a, n + 1, n - 1, a, v)], f"{a}{sup(n)} = {v}, so log_{a}({v}) = {n}.", src=src)


# ================================================================================================ A Level
@reg([A6, A12], "en-m-differentiate", SA, "Differentiation")
def differentiate(rng, d, src):
    deg = rng.choice([2, 3, 4])
    co = [nz(rng, -6, 7)] + [rng.randint(-8, 8) for _ in range(deg)]
    dc = dpoly(co)
    assert all(close(numdiff(lambda x: ev(co, x), t), ev(dc, t)) for t in (0.7, 1.9))
    wrongs = []
    for i in range(len(dc)):
        c = list(dc)
        c[i] += rng.choice([-1, 1, 2])
        wrongs.append(poly(c))
    wrongs.append(poly(co[:-1]))
    return Draft(f"Differentiate f(x) = {poly(co)} with respect to x.", poly(dc), wrongs, "Differentiate term by term: d/dx(axⁿ) = naxⁿ⁻¹.", src=src)


@reg([A6, A12], "en-m-gradient-at-point", SA, "Differentiation")
def gradient_at(rng, d, src):
    deg = rng.choice([2, 3])
    co = [nz(rng, -4, 4)] + [rng.randint(-6, 6) for _ in range(deg)]
    x0 = rng.randint(-3, 4)
    v = ev(dpoly(co), x0)
    return Draft(f"Find the gradient of the curve y = {poly(co)} at x = {num(x0)}.", num(v), ints(rng, v, 5) + [num(ev(co, x0))], f"dy/dx = {poly(dpoly(co))}; substitute x = {num(x0)}.", src=src)


@reg([A6, A12], "en-m-stationary", SA, "Differentiation")
def stationary(rng, d, src):
    a, h, k = nz(rng, -3, 3), rng.randint(-5, 5), rng.randint(-8, 8)
    b, c = -2 * a * h, a * h * h + k
    kind = "minimum" if a > 0 else "maximum"
    return Draft(f"The curve y = {poly([a, b, c])} has a stationary point. What are its coordinates?", f"({num(h)}, {num(k)})", [f"({num(-h)}, {num(k)})", f"({num(h)}, {num(c)})", f"({num(k)}, {num(h)})", f"({num(h)}, {num(-k)})", f"({num(h + 1)}, {num(k)})"],
                 f"dy/dx = {poly([2 * a, b])} = 0 gives x = {num(h)}, and y = {num(k)}. It is a {kind}.", src=src, diff=3)


@reg([A6, A12], "en-m-integrate-definite", SA, "Integration")
def integrate_definite(rng, d, src):
    a, b, c = nz(rng, 1, 5), rng.randint(-4, 6), rng.randint(0, 3)
    lo, hi = rng.randint(0, 2), rng.randint(3, 5)
    F = lambda x: Fraction(a, 3) * x ** 3 + Fraction(b, 2) * x ** 2 + c * x
    v = F(hi) - F(lo)
    n = 20000
    f = lambda x: a * x * x + b * x + c
    simpson = sum(((1 if i in (0, n) else (4 if i % 2 else 2)) * f(lo + (hi - lo) * i / n)) for i in range(n + 1)) * (hi - lo) / (3 * n)
    assert abs(simpson - float(v)) < 1e-6
    alt = [F(hi) + F(lo), f(hi) - f(lo), Fraction(a, 3) * (hi ** 3 - lo ** 3), v + 1, v * 2]
    return Draft(f"Evaluate the definite integral of ({poly([a, b, c])}) dx from x = {lo} to x = {hi}.", num(v), [num(w) for w in alt], "Integrate, then F(upper) − F(lower).", src=src, diff=4)


@reg([A6, A12], "en-m-binomial-coefficient", SA, "Binomial expansion")
def binomial_coeff(rng, d, src):
    n, a = rng.randint(4, 8), rng.randint(1, 4)
    r = rng.randint(1, n - 1)
    coeff = math.comb(n, r) * a ** r
    return Draft(f"What is the coefficient of x{sup(r) if r > 1 else ''} in the expansion of (1 + {a if a > 1 else ''}x){sup(n)}?", str(coeff), [str(w) for w in (math.comb(n, r), a ** r, math.comb(n, r) * a, math.comb(n, r + 1) * a ** r if r + 1 <= n else coeff + 3, coeff + a)],
                 f"The term is C({n},{r})({a}x){sup(r)} so the coefficient is {math.comb(n, r)} × {a}{sup(r)}.", src=src, diff=3)


@reg([A6, A12], "en-m-gp-infinity", SA, "Sequences and series")
def gp_infinity(rng, d, src):
    a, r = rng.choice([2, 3, 4, 5, 6, 8, 10, 12, 20]), rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(1, 4), Fraction(2, 3), Fraction(3, 4), Fraction(1, 5)])
    s = a / (1 - r)
    return Draft(f"A geometric series has first term {a} and common ratio {num(r)}. What is its sum to infinity?", num(s), [num(a * (1 + r)), num(Fraction(a, 1) / r), num(a * r / (1 - r)), num(a / (1 + r)), num(a * 2)],
                 "S∞ = a/(1 − r) for |r| < 1.", src=src)


@reg([A6, A12], "en-m-ap-sum", SA, "Sequences and series")
def ap_sum(rng, d, src):
    a, dd, n = rng.randint(-5, 15), nz(rng, -4, 7), rng.randint(8, 30)
    s = n * (2 * a + (n - 1) * dd) // 2
    assert s == sum(a + i * dd for i in range(n))
    return Draft(f"Find the sum of the first {n} terms of the arithmetic progression with first term {num(a)} and common difference {num(dd)}.", str(s), [str(w) for w in (n * (2 * a + n * dd) // 2, n * (a + (n - 1) * dd), s + n, s - dd, n * a)],
                 "Sₙ = n/2 [2a + (n − 1)d].", src=src, diff=3)


@reg([A6, A12], "en-m-discriminant-k", SA, "Quadratics")
def discriminant_k(rng, d, src):
    k = rng.choice([2, 3, 4, 5, 6, 8, 9, 10])
    # x² + b x + c = 0 has equal roots when b² = 4c ; ask for c given b
    b = 2 * k
    c = k * k
    return Draft(f"Find the value of c for which x² + {b}x + c = 0 has equal roots.", str(c), [str(w) for w in (b, 2 * c, k, c + 1, 4 * c, b * b)], "Equal roots need b² − 4ac = 0: " + f"{b * b} − 4c = 0.", src=src, diff=3)


@reg([A6, A12], "en-m-remainder-theorem", SA, "Polynomials")
def remainder_theorem(rng, d, src):
    co = [1] + [rng.randint(-6, 6) for _ in range(3)]
    a = rng.randint(-3, 4)
    r = ev(co, a)
    f = poly(co)
    root = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    return Draft(f"What is the remainder when {f} is divided by ({root})?", num(r), ints(rng, r, 5) + [num(ev(co, -a))], "Remainder theorem: the remainder is f(a).", src=src, diff=3)


@reg([A6, A12], "en-m-log-laws", SA, "Logarithms")
def log_laws(rng, d, src):
    a, b = rng.choice([2, 3, 5]), rng.choice([2, 3, 5])
    m, n = rng.randint(1, 4), rng.randint(1, 4)
    # log(a^m b^n) in terms of x = log a, y = log b
    right = f"{m}x + {n}y" if m > 1 and n > 1 else (f"x + {n}y" if m == 1 and n > 1 else (f"{m}x + y" if n == 1 and m > 1 else "x + y"))
    v = a ** m * b ** n
    if a == b:
        return None
    wr = [f"{m + n}x" if False else f"{m * n}(x + y)", f"{m}x − {n}y".replace("1x", "x").replace("1y", "y"), f"{n}x + {m}y".replace("1x", "x").replace("1y", "y"), f"{m}xy".replace("1xy", "xy") if False else f"({m}x)({n}y)".replace("(1x)", "x").replace("(1y)", "y"), f"{m + n}(x + y)"]
    wr = [w for w in wr if w != right]
    return Draft(f"Given log {a} = x and log {b} = y, how can log {v} be written in terms of x and y?", right, wr, "log(aᵐbⁿ) = m log a + n log b.", src=src, diff=3)


@reg([A6, A12], "en-m-trig-identity", SA, "Trigonometry")
def trig_identity(rng, d, src):
    u, v, w = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25)])
    if rng.random() < 0.5:
        u, v = v, u
    which = rng.choice(["sin", "cos"])
    ans = Fraction(v, w)
    return Draft(f"θ is acute and {which} θ = {u}/{w}. What is {'cos' if which == 'sin' else 'sin'} θ?", f"{v}/{w}", [f"{u}/{w}", f"{w - u}/{w}", f"{v}/{u}", f"{v * v}/{w * w}", f"{w - v}/{w}"], "Use sin²θ + cos²θ = 1; the value is positive for an acute angle.", src=src)


@reg([A12], "en-m-complex-modulus", SA, "Complex numbers")
def complex_modulus(rng, d, src):
    u, v, w = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (6, 8, 10), (7, 24, 25)])
    su, sv = rng.choice([1, -1]), rng.choice([1, -1])
    z = f"{num(su * u)} {'+' if sv > 0 else '−'} {v}i"
    return Draft(f"What is the modulus of the complex number z = {z}?", str(w), [str(x) for x in (u + v, abs(u - v), u * v, w * w, w + 1)], "|z| = √(a² + b²).", src=src)


@reg([A12], "en-m-complex-multiply", SA, "Complex numbers")
def complex_multiply(rng, d, src):
    a, b, c, e = (rng.randint(-5, 6) for _ in range(4))
    z = complex(a, b) * complex(c, e)
    re, im = int(z.real), int(z.imag)
    def zt(r, i):
        return f"{num(r)} {'+' if i >= 0 else '−'} {abs(i)}i"
    return Draft(f"Simplify ({zt(a, b)})({zt(c, e)}).", zt(re, im), [zt(a * c + b * e, a * e - b * c), zt(a * c, b * e), zt(a * c - b * e, a * e - b * c), zt(re, -im), zt(a + c, b + e)], "Expand and use i² = −1.", src=src, diff=3)


@reg([A6, A12], "en-m-poisson-zero", SA, "Probability and statistics")
def poisson_zero(rng, d, src):
    lam = rng.choice([1, 2, 3, 0.5, 1.5, 4])
    p = math.exp(-lam)
    return Draft(f"X follows a Poisson distribution with mean {E(lam)}. What is P(X = 0), to 3 decimal places?", E(round(p, 3), 3), [E(round(w, 3), 3) for w in (1 - p, math.exp(lam) / 100, lam * p, p * lam * lam / 2, math.exp(-lam / 2))],
                 "P(X = 0) = e^(−λ).", src=src, diff=3)


@reg([A6, A12], "en-m-binomial-prob", SA, "Probability and statistics")
def binomial_prob(rng, d, src):
    n, k = rng.choice([4, 5, 6, 8, 10]), None
    k = rng.randint(1, n - 1)
    p = rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(1, 4), Fraction(3, 4)])
    prob = math.comb(n, k) * p ** k * (1 - p) ** (n - k)
    f = lambda x: E(round(float(x), 4), 4)
    return Draft(f"X ~ B({n}, {num(p)}). What is P(X = {k}) to 4 decimal places?", f(prob), [f(w) for w in (math.comb(n, k) * p ** k, p ** k * (1 - p) ** (n - k), math.comb(n, k) * (1 - p) ** k * p ** (n - k), prob * 2 if prob < 0.5 else prob / 2, 1 - prob)],
                 "P(X = k) = C(n, k) pᵏ (1 − p)ⁿ⁻ᵏ.", src=src, diff=4)


@reg([A6, A12], "en-m-vector-dot", SA, "Vectors")
def vector_dot(rng, d, src):
    a, b, c = (rng.randint(-5, 6) for _ in range(3))
    x, y, z = (rng.randint(-5, 6) for _ in range(3))
    v = a * x + b * y + c * z
    return Draft(f"Find the scalar product of the vectors ({num(a)}, {num(b)}, {num(c)}) and ({num(x)}, {num(y)}, {num(z)}).", num(v), [num(a * x - b * y + c * z), num(a + x + b + y + c + z), num(a * y + b * z + c * x), num(-v) if v else "1", num(a * x * b * y * c * z)] if v != 0 else ["1", "2", "3", "4"], "a·b = a₁b₁ + a₂b₂ + a₃b₃.", src=src)
