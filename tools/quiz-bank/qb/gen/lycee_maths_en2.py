"""GCE Mathematics in English, second set of models (Ordinary Level and Pure Mathematics / Statistics of the Advanced Level).
Answers are recomputed in each generator (exact fractions, numerical derivative, substitution)."""
import math
from fractions import Fraction

from ..core import MINUS, Draft
from .lycee_common import E, ints
from .lycee_maths_en import A6, A12, O5, SA, SO, close, lin, numdiff, reg
from .mathfmt import dpoly, ev, nz, num, poly, sup


# ================================================================================================ O Level
@reg([O5], "en-m-fractions", SO, "Number", diffs=(1, 2, 3))
def fractions_(rng, d, src):
    a, b, c, e = rng.randint(1, 7), rng.randint(2, 9), rng.randint(1, 7), rng.randint(2, 9)
    if math.gcd(a, b) != 1 or math.gcd(c, e) != 1 or b == e:
        return None
    op = rng.choice(["+", "−", "×", "÷"])
    f1, f2 = Fraction(a, b), Fraction(c, e)
    v = {"+": f1 + f2, "−": f1 - f2, "×": f1 * f2, "÷": f1 / f2}[op]
    wr = [Fraction(a + c, b + e), Fraction(a * c, b + e), f1 * f2 if op != "×" else f1 + f2, f1 / f2 if op != "÷" else f1 - f2, v * 2]
    return Draft(f"What is {a}/{b} {op} {c}/{e}, written as a fraction in its simplest form?", num(v), [num(w) for w in wr if w != v],
                 "Use a common denominator for + and −, multiply for ×, and multiply by the reciprocal for ÷.", src=src)


@reg([O5], "en-m-ratio-share", SO, "Ratio and proportion", diffs=(2, 3))
def ratio_share(rng, d, src):
    a, b = rng.randint(1, 5), rng.randint(2, 7)
    if a == b:
        return None
    unit = rng.choice([100, 200, 250, 400, 500, 1000])
    total = (a + b) * unit
    big = max(a, b) * unit
    return Draft(f"{total} FCFA is shared between two people in the ratio {a} : {b}. What is the larger share, in FCFA?", str(big), [str(w) for w in (min(a, b) * unit, total // 2, total * max(a, b) // 10, big + unit, (total // (a + b)))], "Divide the total by the sum of the parts, then multiply by the larger part.", src=src)


@reg([O5], "en-m-reverse-percentage", SO, "Percentages", diffs=(3, 4))
def reverse_percentage(rng, d, src):
    orig, off = rng.choice([1000, 2000, 2500, 4000, 5000, 8000]), rng.choice([10, 20, 25, 30, 40])
    sale = orig * (100 - off) // 100
    return Draft(f"After a discount of {off} %, a shirt costs {sale} FCFA. What was the original price, in FCFA?", str(orig), [str(w) for w in (sale + sale * off // 100, sale * 100 // (100 + off) if sale * 100 % (100 + off) == 0 else sale + off, sale + off, orig + off * 10, sale * 2)],
                 f"The sale price is {100 - off} % of the original: original = sale ÷ {E((100 - off) / 100)}.", src=src, diff=4)


@reg([O5], "en-m-average-speed", SO, "Speed and measurement", diffs=(3, 4))
def average_speed(rng, d, src):
    s1, v1, s2, v2 = rng.choice([(60, 30, 60, 60), (100, 50, 100, 100), (120, 40, 120, 60), (90, 30, 90, 90), (80, 40, 80, 80)])
    t = s1 / v1 + s2 / v2
    avg = (s1 + s2) / t
    return Draft(f"A car travels {s1} km at {v1} km/h and then {s2} km at {v2} km/h. What is its average speed for the whole journey, in km/h?", E(round(avg, 2)), [E(round(w, 2)) for w in ((v1 + v2) / 2, v1 + v2, avg + 5, (s1 + s2) / 2, avg - 5) if round(w, 2) != round(avg, 2)],
                 "Average speed = total distance ÷ total time (not the mean of the speeds).", src=src)


@reg([O5], "en-m-bearing-back", SO, "Geometry", diffs=(2, 3))
def bearing_back(rng, d, src):
    b = rng.choice([30, 45, 60, 75, 110, 130, 150, 200, 225, 250, 290, 320, 340])
    back = b + 180 if b < 180 else b - 180
    return Draft(f"The bearing of town B from town A is {b:03d}°. What is the bearing of A from B?", f"{back:03d}°", [f"{w % 360:03d}°" for w in (b + 90, b - 90, 360 - b, b + 170, back + 10)],
                 "A back bearing differs by 180°.", src=src)


@reg([O5], "en-m-linear-equation", SO, "Algebra", diffs=(1, 2, 3))
def linear_equation(rng, d, src):
    x = rng.randint(-6, 9)
    a, b, c = nz(rng, -5, 6), rng.randint(-9, 9), None
    c = a * x + b
    return Draft(f"What is the solution of the equation {lin(a, b)} = {num(c)}?", num(x), [num(w) for w in (-x, x + 1, x - 1, c - b, Fraction(c + b, a) if (c + b) % a else x + 2, a * x) if w != x],
                 f"Rearrange to isolate x: x = ({num(c)} − ({num(b)})) ÷ ({num(a)}).", src=src)


@reg([O5], "en-m-expand-brackets", SO, "Algebra", diffs=(2, 3))
def expand_brackets(rng, d, src):
    a, b, c, e = nz(rng, -4, 5), rng.randint(-6, 6), nz(rng, -4, 5), rng.randint(-6, 6)
    co = [a * c, a * e + b * c, b * e]
    u, v = lin(a, b), lin(c, e)
    return Draft(f"What is ({u})({v}) when expanded and simplified?", poly(co), [poly([a * c, a * e - b * c, b * e]), poly([a * c, a * e + b * c, -b * e]), poly([a * c, a * e + b * c + 1, b * e]), poly([a + c, a * e + b * c, b * e]), poly([a * c, 0, b * e])],
                 "Multiply each term in the first bracket by each term in the second and collect like terms.", src=src)


@reg([O5], "en-m-arc-length", SO, "Mensuration", diffs=(3, 4))
def arc_length(rng, d, src):
    r, ang = rng.choice([7, 14, 21, 28, 35]), rng.choice([30, 45, 60, 90, 120, 180, 270])
    arc = 22 / 7 * 2 * r * ang / 360
    return Draft(f"A sector of a circle has radius {r} cm and angle {ang}°. Taking π = 22/7, what is the length of its arc, in cm?", E(round(arc, 2)), [E(round(w, 2)) for w in (22 / 7 * r * r * ang / 360, 22 / 7 * 2 * r, arc * 2, arc / 2, 22 / 7 * r * ang / 360 * 4) if round(w, 2) != round(arc, 2)],
                 "Arc length = (angle ÷ 360) × 2πr.", src=src)


@reg([O5], "en-m-trapezium-area", SO, "Mensuration", diffs=(2, 3))
def trapezium_area(rng, d, src):
    a, b, h = rng.randint(3, 12), rng.randint(3, 12), rng.randint(2, 10)
    if a == b:
        return None
    area = (a + b) * h / 2
    return Draft(f"A trapezium has parallel sides of {a} cm and {b} cm and a height of {h} cm. What is its area, in cm²?", E(area), [E(w) for w in (a * b * h / 2, (a + b) * h, (a + b) / 2 + h, (a + b) * h / 4, a * h) if w != area], "Area = ½ (a + b) h.", src=src)


# ================================================================================================ A Level
@reg([A6, A12], "en-m-chain-rule", SA, "Differentiation", diffs=(3, 4))
def chain_rule(rng, d, src):
    a, b, n = nz(rng, -4, 5), rng.randint(-5, 5), rng.randint(2, 6)
    f = lambda x: (a * x + b) ** n
    dd = lambda x: n * a * (a * x + b) ** (n - 1)
    assert close(numdiff(f, 0.6), dd(0.6))
    u = lin(a, b)
    ex = lambda k: f"({u}){sup(k)}" if k > 1 else f"({u})"
    right = f"{num(n * a)}{ex(n - 1)}"
    wrongs = [f"{n}{ex(n - 1)}", f"{num(a)}{ex(n - 1)}", f"{num(n * a)}{ex(n)}", f"{num(n * a + 1)}{ex(n - 1)}", f"{num(n * a)}{ex(max(1, n - 2))}"]
    return Draft(f"What is dy/dx when y = ({u}){sup(n)}?", right, [w for w in wrongs if w != right],
                 "Chain rule: d/dx [f(u)] = f'(u) × u'.", src=src)


@reg([A6, A12], "en-m-integrate-indefinite", SA, "Integration", diffs=(2, 3))
def integrate_indefinite(rng, d, src):
    n, k = rng.randint(1, 6), nz(rng, -6, 8)
    # ∫ k x^n dx = k/(n+1) x^(n+1) + c  : choose k as a multiple of n + 1
    k = (n + 1) * nz(rng, -4, 5)
    def term(c, p):
        body = "x" + (sup(p) if p > 1 else "") if p > 0 else ""
        return (("" if c == 1 else (MINUS if c == -1 else num(c))) + body if p > 0 else num(c)) + " + c"
    right = term(k // (n + 1), n + 1)
    f = lambda x: k // (n + 1) * x ** (n + 1)
    assert abs(numdiff(f, 1.2) - k * 1.2 ** n) < 1e-4
    wr = [term(k, n + 1), term(k * (n + 1), n + 1), term(k // (n + 1), n), term(k * n, n - 1), term(k // (n + 1) + 1, n + 1)]
    return Draft(f"What is the integral of {num(k)}x{sup(n) if n > 1 else ''} with respect to x?", right, [w for w in wr if w != right],
                 "Add 1 to the power and divide by the new power.", src=src)


@reg([A6, A12], "en-m-cosine-rule", SA, "Trigonometry", diffs=(3, 4))
def cosine_rule(rng, d, src):
    a, b, ang, c = rng.choice([(3, 8, 60, 7), (5, 8, 60, 7), (3, 5, 120, 7), (7, 8, 120, 13), (5, 3, 120, 7), (8, 3, 60, 7)])
    cos = 0.5 if ang == 60 else -0.5
    assert math.isclose(math.sqrt(a * a + b * b - 2 * a * b * cos), c)
    return Draft(f"In triangle ABC, AC = {a} cm, BC = {b} cm and angle C = {ang}°. What is the length of AB, in cm?", str(c), [str(w) for w in (a + b, abs(a - b), c + 1, c - 1, a * b // 2 if (a * b) % 2 == 0 else c + 2) if w != c], "Cosine rule: c² = a² + b² − 2ab cos C.", src=src)


@reg([A6, A12], "en-m-sine-rule", SA, "Trigonometry", diffs=(3, 4))
def sine_rule(rng, d, src):
    a = rng.choice([4, 5, 6, 8, 10, 12])
    A, B = rng.choice([(30, 90), (90, 30)])
    sA, sB = {30: 0.5, 90: 1.0}[A], {30: 0.5, 90: 1.0}[B]
    b = a * sB / sA
    assert math.isclose(a / math.sin(math.radians(A)), b / math.sin(math.radians(B)))
    return Draft(f"In triangle ABC, angle A = {A}°, angle B = {B}° and BC = {a} cm (opposite A). What is the length of AC (opposite B), in cm?", E(b), [E(w) for w in (a * 2 if b != a * 2 else a * 3, a / 2 if b != a / 2 else a / 4, a, a + 1, b + 2) if w != b], "Sine rule: a / sin A = b / sin B.", src=src)


@reg([A6, A12], "en-m-sector-radians", SA, "Trigonometry", diffs=(3, 4))
def sector_radians(rng, d, src):
    r = rng.randint(2, 12)
    k, m = rng.choice([(1, 3), (1, 2), (2, 3), (1, 4), (1, 6), (3, 4), (5, 6)])
    arc = Fraction(k * r, m)
    area = Fraction(k * r * r, 2 * m)
    ask_area = rng.random() < 0.5
    pit = lambda fr_: (f"{fr_.numerator if fr_.numerator != 1 else ''}π" + (f"/{fr_.denominator}" if fr_.denominator != 1 else "")) if fr_.numerator != 0 else "0"
    th = pit(Fraction(k, m))
    if ask_area:
        right = pit(area)
        wr = [pit(arc), pit(area * 2), pit(area / 2), pit(Fraction(k * r * r, m)) if Fraction(k * r * r, m) != area else pit(area + 1)]
        return Draft(f"A sector has radius {r} cm and angle {th} radians. What is its area, in cm², in terms of π?", right, wr, "Area = ½ r² θ.", src=src)
    right = pit(arc)
    wr = [pit(area), pit(arc * 2), pit(arc / 2), pit(arc + 1)]
    return Draft(f"A sector has radius {r} cm and angle {th} radians. What is its arc length, in cm, in terms of π?", right, wr, "Arc length = rθ.", src=src)


@reg([A6, A12], "en-m-z-score", SA, "Probability and statistics", diffs=(2, 3))
def z_score(rng, d, src):
    mu, sigma = rng.choice([50, 60, 70, 100, 120]), rng.choice([2, 4, 5, 8, 10, 15])
    z = rng.choice([-2, -1.5, -1, -0.5, 0.5, 1, 1.5, 2, 2.5])
    x = mu + z * sigma
    if x != int(x):
        return None
    return Draft(f"A variable X is normally distributed with mean {mu} and standard deviation {sigma}. What is the z-value of X = {int(x)}?", E(z), [E(w) for w in (-z, z * 2, z / 2, x - mu, (x - mu) / (sigma * sigma)) if w != z], "z = (x − μ) / σ.", src=src)


@reg([A6, A12], "en-m-expectation", SA, "Probability and statistics", diffs=(3, 4))
def expectation(rng, d, src):
    vals = [1, 2, 3, 4]
    w = [rng.randint(1, 4) for _ in vals]
    tot = sum(w)
    probs = [Fraction(x, tot) for x in w]
    e = sum(v * p for v, p in zip(vals, probs))
    table = ", ".join(f"P(X = {v}) = {num(p)}" for v, p in zip(vals, probs))
    return Draft(f"A random variable X takes the values 1, 2, 3 and 4 with {table}. What is E(X)?", num(e), [num(x) for x in (Fraction(5, 2), sum(probs), sum(v * v * p for v, p in zip(vals, probs)), e + 1, e - Fraction(1, 2)) if x != e], "E(X) = Σ x P(X = x).", src=src)


@reg([A6, A12], "en-m-complete-square", SA, "Quadratics", diffs=(3, 4))
def complete_square(rng, d, src):
    p = rng.randint(-6, 6) * 2
    q = rng.randint(-9, 9)
    h = p // 2
    r = q - h * h
    fmt = lambda hh, rr: f"(x {'+' if hh >= 0 else '−'} {abs(hh)})² {'+' if rr >= 0 else '−'} {abs(rr)}".replace("(x + 0)", "x").replace("(x − 0)", "x")
    wr = [fmt(-h, r), fmt(h, q), fmt(h, -r) if r else fmt(h, 1), fmt(p, r), fmt(h, q + h * h)]
    return Draft(f"What is {poly([1, p, q])} in the form (x + a)² + b?", fmt(h, r), [w for w in wr if w != fmt(h, r)], f"x² + {num(p)}x = (x + {num(h)})² − {h * h}, so b = {num(r)}.".replace("+ -", "− "), src=src)


@reg([A6, A12], "en-m-geometric-term", SA, "Sequences and series", diffs=(2, 3))
def geometric_term(rng, d, src):
    a, r, n = rng.choice([1, 2, 3, 5, -2, 4]), rng.choice([2, 3, -2, 5, Fraction(1, 2)]), rng.randint(4, 8)
    r = Fraction(r)
    v = a * r ** (n - 1)
    return Draft(f"A geometric progression has first term {num(a)} and common ratio {num(r)}. What is its {n}th term?", num(v), [num(x) for x in (a * r ** n, a * r * n, a * r ** (n - 2), (a * r) ** (n - 1), v + 1) if x != v], "uₙ = a rⁿ⁻¹.", src=src)


@reg([A6, A12], "en-m-modulus-equation", SA, "Functions", diffs=(3, 4))
def modulus_equation(rng, d, src):
    a, b = rng.randint(-6, 6), rng.randint(1, 8)
    inner = f"x {'−' if a > 0 else '+'} {abs(a)}" if a else "x"
    sol = sorted((a - b, a + b))
    return Draft(f"What are the solutions of |{inner}| = {b}?", f"x = {num(sol[0])} or x = {num(sol[1])}", [f"x = {num(-sol[0])} or x = {num(-sol[1])}", f"x = {num(a + b)} only", f"x = {num(a - b)} only", f"x = {num(b)} or x = {num(-b)}" if a else f"x = {num(b + 1)} or x = {num(-b - 1)}", f"x = {num(sol[0] - 1)} or x = {num(sol[1] + 1)}"],
                 f"|{inner}| = {b} means {inner} = {b} or {inner} = −{b}.", src=src)


@reg([A6, A12], "en-m-log-equation", SA, "Logarithms", diffs=(3, 4))
def log_equation(rng, d, src):
    base, n, a = rng.choice([2, 3, 4, 5, 10]), rng.randint(2, 4), rng.randint(1, 9)
    x = base ** n - a
    return Draft(f"What is the solution of log base {base} of (x + {a}) = {n}?", str(x), [str(w) for w in (base * n - a, base ** n, base ** n + a, n - a, x + 1) if w != x], f"x + {a} = {base}{sup(n)} = {base ** n}.", src=src)


@reg([A6, A12], "en-m-exponential-equation", SA, "Logarithms", diffs=(3, 4))
def exponential_equation(rng, d, src):
    base, k, c = rng.choice([2, 3, 5]), rng.randint(2, 5), rng.randint(0, 3)
    x = k - c
    return Draft(f"What is the solution of {base}^(x + {c}) = {base ** k}?" if c else f"What is the solution of {base}^x = {base ** k}?", str(x), [str(w) for w in (k + c, base ** k - c, k, x + 1, x - 1 if x > 1 else x + 2) if w != x],
                 f"Write both sides as powers of {base}: x + {c} = {k}.", src=src)
