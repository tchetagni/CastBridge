"""Terminale (Tle, séries scientifiques): analysis, complex numbers, sequences, counting and probability, physics-chemistry.
Every derivative / primitive / integral is checked numerically against the formula; counts are brute-forced when small."""
import cmath
import math
from fractions import Fraction
from itertools import permutations

from ..core import MINUS, Draft, fr, gen, near_ints
from .mathfmt import dpoly, ev, ipoly, num, nz, par, poly, poly_variants, sup

C = "tle"
SRC = "Programme MINESEC Tle (séries C, D, E) - réponse calculée, dérivée/intégrale vérifiée numériquement"
PC = "Programme MINESEC Tle, sciences physiques - réponse calculée"


def ints(rng, right, n=8, **kw):
    return [fr(v) for v in near_ints(rng, right, n, **kw)]


def numdiff(f, x, h=1e-6):
    return (f(x + h) - f(x - h)) / (2 * h)


def close(a, b, tol=1e-4):
    return abs(a - b) <= tol * max(1, abs(a), abs(b))


def lin(a, b, var="x"):
    return poly([a, b], var)


@gen(C, "tle-deriv-poly", cap=300, cat="Analyse")
def deriv_poly(rng, d):
    deg = rng.choice([2, 3, 3, 4, 5][: 2 + d])
    co = [nz(rng, -6, 6)] + [rng.randint(-7, 7) for _ in range(deg)]
    dc = dpoly(co)
    f = lambda x: ev(co, x)
    assert close(numdiff(f, 1.3), ev(dc, 1.3))
    return Draft(f"Quelle est la dérivée de f(x) = {poly(co)} ?", poly(dc), poly_variants(rng, dc), f"On dérive terme à terme : (aˣⁿ)' = n·a·xⁿ⁻¹ ; f'(x) = {poly(dc)}.", src=SRC)


@gen(C, "tle-deriv-at", cap=260, cat="Analyse")
def deriv_at(rng, d):
    deg = rng.choice([2, 3, 4][: 1 + d // 2 + 1])
    co = [nz(rng, -4, 5)] + [rng.randint(-6, 6) for _ in range(deg)]
    x0 = rng.randint(-3, 4)
    v = ev(dpoly(co), x0)
    assert close(numdiff(lambda x: ev(co, x), x0), v)
    return Draft(f"Soit f(x) = {poly(co)}. Quelle est la valeur de f'({num(x0)}) ?", num(v), [num(w) for w in near_ints(rng, v, 8)] + [num(ev(co, x0))],
                 f"f'(x) = {poly(dpoly(co))}, donc f'({num(x0)}) = {num(v)}.", src=SRC)


@gen(C, "tle-deriv-exp", cap=220, cat="Analyse")
def deriv_exp(rng, d):
    a, k = nz(rng, -5, 6), nz(rng, -3, 4)
    f = lambda x: a * math.exp(k * x)
    dd = lambda x: a * k * math.exp(k * x)
    assert close(numdiff(f, 0.4), dd(0.4))
    ex = lambda c, kk: f"{num(c) if abs(c) != 1 else ('' if c == 1 else MINUS)}e{sup(num(kk)) if kk != 1 else ''}".replace("e⁻", "e⁻") if False else None
    def form(c, kk):
        coeff = "" if c == 1 else (MINUS if c == -1 else num(c))
        ex = "x" if kk == 1 else (MINUS + "x" if kk == -1 else f"{num(kk)}x")
        return f"{coeff}e^({ex})"
    wr = [form(a, k), form(a * k, k - 1) if k != 1 else form(a * k, 0), form(a * (k + 1), k), form(-a * k, k), form(a + k, k)]
    wr = [w for w in wr if not w.startswith("0")]
    return Draft(f"Quelle est la dérivée de f(x) = {form(a, k)} ?", form(a * k, k), wr,
                 f"(e^(kx))' = k·e^(kx), donc f'(x) = {num(a)} × {num(k)} · e^({num(k)}x) = {form(a * k, k)}.", src=SRC)


@gen(C, "tle-deriv-product-exp", cap=200, cat="Analyse")
def deriv_prod_exp(rng, d):
    a = rng.randint(-6, 7)
    f = lambda x: (x + a) * math.exp(x)
    dd = lambda x: (x + a + 1) * math.exp(x)
    assert close(numdiff(f, 0.7), dd(0.7))
    sg = lambda c: f"x {'+' if c >= 0 else MINUS} {abs(c)}" if c != 0 else "x"
    return Draft(f"Quelle est la dérivée de f(x) = ({sg(a)})eˣ ?", f"({sg(a + 1)})eˣ", [f"({sg(a)})eˣ", f"eˣ", f"({sg(a - 1)})eˣ", f"{1}·eˣ + x"],
                 f"Règle du produit (uv)' = u'v + uv' avec u = {sg(a)}, v = eˣ : f'(x) = eˣ + ({sg(a)})eˣ = ({sg(a + 1)})eˣ.", src=SRC)


@gen(C, "tle-deriv-ln", cap=220, cat="Analyse")
def deriv_ln(rng, d):
    a, b = nz(rng, 1, 7) * rng.choice([1, 1, -1]), rng.randint(1, 9)
    f = lambda x: math.log(a * x + b)
    x0 = (abs(b) + 5) / max(1, abs(a)) if a > 0 else -(b + 1) / a * 0.3
    if a * x0 + b <= 0:
        x0 = 0.1 if a > 0 else -0.5 * b / a
    dd = lambda x: a / (a * x + b)
    assert close(numdiff(f, x0), dd(x0))
    den = lin(a, b)
    return Draft(f"Quelle est la dérivée de f(x) = ln({den}) sur son domaine de définition ?", f"{num(a)}/({den})", [f"1/({den})", f"{num(b)}/({den})", f"{num(a)}·ln({den})", f"{num(a)}/{num(b)}"],
                 f"(ln u)' = u'/u avec u = {den} et u' = {num(a)}.", src=SRC)


@gen(C, "tle-deriv-power", cap=220, cat="Analyse")
def deriv_power(rng, d):
    a, b, n = nz(rng, 1, 5) * rng.choice([1, -1]), rng.randint(-5, 6), rng.randint(2, 6)
    u = lin(a, b)
    f = lambda x: (a * x + b) ** n
    dd = lambda x: n * a * (a * x + b) ** (n - 1)
    assert close(numdiff(f, 0.6), dd(0.6))
    right = f"{num(n * a)}({u}){sup(n - 1) if n > 2 else ''}"
    wrongs = [f"{n}({u}){sup(n - 1) if n > 2 else ''}", f"{num(a)}({u}){sup(n - 1) if n > 2 else ''}", f"{num(n * a)}({u}){sup(n)}", f"{num(n * a)}({u}){sup(n - 2) if n > 3 else ''}", f"{num(n * b)}({u}){sup(n - 1) if n > 2 else ''}"]
    return Draft(f"Quelle est la dérivée de f(x) = ({u}){sup(n)} ?", right, wrongs, f"(uⁿ)' = n·u'·uⁿ⁻¹ avec u = {u}, u' = {num(a)} : f'(x) = {right}.", src=SRC)


@gen(C, "tle-deriv-quotient", cap=200, cat="Analyse")
def deriv_quot(rng, d):
    a, b, c, dd_ = nz(rng, -5, 5), rng.randint(-6, 6), nz(rng, 1, 4), rng.randint(1, 6)
    det = a * dd_ - b * c
    if det == 0:
        return None
    f = lambda x: (a * x + b) / (c * x + dd_)
    x0 = 0.37
    if abs(c * x0 + dd_) < 0.2:
        return None
    dv = lambda x: det / (c * x + dd_) ** 2
    assert close(numdiff(f, x0), dv(x0))
    den = lin(c, dd_)
    return Draft(f"Quelle est la dérivée de f(x) = ({lin(a, b)})/({den}) ?", f"{num(det)}/({den})²",
                 [f"{num(-det)}/({den})²", f"{num(a * c)}/({den})²", f"{num(a)}/({den})²", f"{num(a * dd_ + b * c)}/({den})²", f"{num(det)}/({den})"],
                 f"(u/v)' = (u'v − uv')/v² = ({num(a)}({den}) − ({lin(a, b)})·{num(c)})/({den})² = {num(det)}/({den})².", src=SRC)


@gen(C, "tle-deriv-trig", cap=160, cat="Analyse")
def deriv_trig(rng, d):
    k = nz(rng, 1, 5)
    a = nz(rng, -4, 5)
    fn = rng.choice(["sin", "cos"])
    if fn == "sin":
        f = lambda x: a * math.sin(k * x)
        dd = lambda x: a * k * math.cos(k * x)
        right, other = f"{num(a * k)}cos({k}x)", f"{num(-a * k)}sin({k}x)"
    else:
        f = lambda x: a * math.cos(k * x)
        dd = lambda x: -a * k * math.sin(k * x)
        right, other = f"{num(-a * k)}sin({k}x)", f"{num(a * k)}cos({k}x)"
    assert close(numdiff(f, 0.3), dd(0.3))
    wr = [other, f"{num(a)}{'cos' if fn == 'sin' else 'sin'}({k}x)", f"{num(a * k)}{'sin' if fn == 'sin' else 'cos'}({k}x)", f"{num(-a)}{'cos' if fn == 'cos' else 'sin'}({k}x)"]
    return Draft(f"Quelle est la dérivée de f(x) = {num(a)}{fn}({k}x) ?", right, wr, f"({fn}(kx))' = {'k·cos(kx)' if fn == 'sin' else '−k·sin(kx)'}, donc f'(x) = {right}.", src=SRC)


@gen(C, "tle-integral-poly", cap=260, cat="Analyse")
def integral_poly(rng, d):
    deg = rng.choice([1, 2, 2, 3])
    co = [nz(rng, -4, 6)] + [rng.randint(-5, 6) for _ in range(deg)]
    a = rng.randint(-2, 1)
    b = a + rng.randint(1, 3)
    F = ipoly(co)
    val = sum(c * Fraction(b) ** (len(F) - 1 - i) for i, c in enumerate(F)) - sum(c * Fraction(a) ** (len(F) - 1 - i) for i, c in enumerate(F))
    n = 4000
    simpson = sum(ev(co, a + (b - a) * (i + 0.5) / n) for i in range(n)) * (b - a) / n
    assert close(float(val), simpson, 1e-5)
    w = [val + k for k in (1, -1, 2, Fraction(1, 2), -val if val != 0 else 3)]
    return Draft(f"Calculer l'intégrale de {num(a)} à {num(b)} de ({poly(co)}) dx : quelle est sa valeur ?", num(val), [num(x) for x in w],
                 f"Une primitive est F(x) = {poly(F)} ; ∫ = F({num(b)}) − F({num(a)}) = {num(val)}.", src=SRC) if True else None


@gen(C, "tle-primitive", cap=200, cat="Analyse")
def primitive(rng, d):
    co = [nz(rng, -4, 5) * (rng.choice([2, 3, 4]) if True else 1)] + [rng.choice([0, 2, 4, 6, -2, -4, 8]) for _ in range(rng.choice([1, 2]))]
    deg = len(co)
    F = [Fraction(c, deg - i) for i, c in enumerate(co)]
    if any(f.denominator != 1 for f in F):
        return None
    Fi = [int(f) for f in F] + [0]
    f = lambda x: ev(co, x)
    assert close(numdiff(lambda x: ev(Fi, x), 0.9), f(0.9))
    right = poly(Fi) + " + C"
    return Draft(f"Quelle est la primitive de f(x) = {poly(co)} (avec C une constante) ?", right, [poly(dpoly(co)) + " + C", poly([c * (deg - i) for i, c in enumerate(co)] + [0]) + " + C", poly(Fi[:-1]) + " + C", poly(Fi) ],
                 f"On intègre terme à terme (xⁿ → xⁿ⁺¹/(n+1)) : {right}.", src=SRC)


@gen(C, "tle-limit-rational", cap=200, cat="Analyse")
def limit_rational(rng, d):
    a, b, c = nz(rng, -6, 7), rng.randint(-5, 5), rng.randint(-5, 5)
    p, q, r = nz(rng, -5, 6), rng.randint(-5, 5), rng.randint(-5, 5)
    kind = rng.choice(["same", "lower", "higher"])
    if kind == "same":
        f = Fraction(a, p)
        right = num(f)
        num_, den_ = poly([a, b, c]), poly([p, q, r])
        wr = [num(Fraction(a, p) + 1), num(Fraction(b, q)) if q else "0", "0", "+∞", "−∞"]
    elif kind == "lower":
        num_, den_ = poly([a, b]), poly([p, q, r])
        right, wr = "0", [num(Fraction(a, p)), "+∞", "−∞", num(Fraction(b, r)) if r else "1"]
    else:
        num_, den_ = poly([a, b, c]), poly([p, q])
        right = "+∞" if a * p > 0 else "−∞"
        wr = ["−∞" if right == "+∞" else "+∞", "0", num(Fraction(a, p)), "1"]
    return Draft(f"Quelle est la limite en +∞ de f(x) = ({num_})/({den_}) ?", right, wr, "En +∞ on ne garde que les termes de plus haut degré du numérateur et du dénominateur.", src=SRC)


@gen(C, "tle-limit-factor", cap=200, cat="Analyse")
def limit_factor(rng, d):
    a = nz(rng, -9, 9)
    kind = rng.choice(["square", "cubic"])
    if kind == "square":
        right = 2 * a
        txt = f"(x² − {a * a}) / (x − {num(a)})" if a > 0 else f"(x² − {a * a}) / (x + {-a})"
        wr = [a, a * a, 0, -2 * a]
    else:
        right = 3 * a * a
        txt = f"(x³ − {num(a ** 3)}) / (x − {num(a)})" if a > 0 else f"(x³ + {-a ** 3}) / (x + {-a})"
        wr = [a * a, 3 * a, a ** 3, 2 * a * a]
    return Draft(f"Quelle est la limite de f(x) = {txt} quand x tend vers {num(a)} ?", num(right), [num(w) for w in wr] + [num(right + 1)],
                 "On factorise le numérateur par (x − a), on simplifie, puis on remplace x par a (ou on reconnaît le nombre dérivé).", src=SRC)


@gen(C, "tle-complex-mul", cap=240, cat="Nombres complexes")
def complex_mul(rng, d):
    a, b, c, e = nz(rng, -6, 6), nz(rng, -6, 6), nz(rng, -6, 6), nz(rng, -6, 6)
    z = complex(a, b) * complex(c, e)
    re_, im = a * c - b * e, a * e + b * c
    assert z.real == re_ and z.imag == im

    def cx(r, i):
        return f"{num(r)} {'+' if i >= 0 else MINUS} {'' if abs(i) == 1 else abs(i)}i" if r != 0 else f"{num(i)}i"
    wrongs = [cx(a * c + b * e, a * e - b * c), cx(a * c, b * e), cx(re_, -im), cx(a * c - b * e, a * e - b * c), cx(im, re_)]
    return Draft(f"Quelle est la forme algébrique de ({cx(a, b)})({cx(c, e)}) ?", cx(re_, im), wrongs, f"On développe avec i² = −1 : ({a}×{par(c)} − {par(b)}×{par(e)}) + ({a}×{par(e)} + {par(b)}×{par(c)})i = {cx(re_, im)}.", src=SRC)


@gen(C, "tle-complex-modulus", cap=160, cat="Nombres complexes")
def complex_mod(rng, d):
    a, b, c = rng.choice([(3, 4, 5), (5, 12, 13), (8, 15, 17), (6, 8, 10), (9, 12, 15), (7, 24, 25)])
    sa, sb = rng.choice([1, -1]), rng.choice([1, -1])
    z = complex(sa * a, sb * b)
    assert abs(abs(z) - c) < 1e-9
    zt = f"{num(sa * a)} {'+' if sb > 0 else MINUS} {b}i"
    return Draft(f"Quel est le module de z = {zt} ?", str(c), [str(x) for x in near_ints(rng, c, 6, lo=1)] + [str(a + b)], f"|z| = √({a}² + {b}²) = √{a * a + b * b} = {c}.", src=SRC)


@gen(C, "tle-i-power", cap=140, cat="Nombres complexes")
def i_power(rng, d):
    n = rng.randint(5, 2030)
    z = 1j ** n
    val = {0: "1", 1: "i", 2: "−1", 3: "−i"}[n % 4]
    assert abs(z - {"1": 1, "i": 1j, "−1": -1, "−i": -1j}[val]) < 1e-9
    return Draft(f"Quelle est la valeur de i^{n} ?", val, [x for x in ("1", "i", "−1", "−i") if x != val], f"i⁴ = 1 ; {n} = 4 × {n // 4} + {n % 4}, donc i^{n} = i^{n % 4} = {val}.", src=SRC)


@gen(C, "tle-complex-conj", cap=140, cat="Nombres complexes")
def complex_conj(rng, d):
    a, b = nz(rng, -9, 9), nz(rng, -9, 9)
    cx = lambda r, i: f"{num(r)} {'+' if i >= 0 else MINUS} {'' if abs(i) == 1 else abs(i)}i"
    return Draft(f"Quel est le conjugué de z = {cx(a, b)} ?", cx(a, -b), [cx(-a, b), cx(-a, -b), cx(b, a), cx(a, b + 1)], f"Le conjugué change le signe de la partie imaginaire : {cx(a, -b)}.", src=SRC)


@gen(C, "tle-seq-arith", cap=220, cat="Suites")
def seq_arith(rng, d):
    u0, r, n = rng.randint(-8, 20), nz(rng, -6, 9), rng.randint(6, 40)
    un = u0 + n * r
    return Draft(f"(uₙ) est arithmétique de premier terme u₀ = {num(u0)} et de raison {num(r)}. Quelle est la valeur de u{n} ?", num(un),
                 [num(v) for v in near_ints(rng, un, 6)] + [num(u0 + (n - 1) * r), num(u0 + (n + 1) * r)], f"uₙ = u₀ + n·r = {num(u0)} + {n} × {par(r)} = {num(un)}.", src=SRC)


@gen(C, "tle-seq-arith-sum", cap=200, cat="Suites")
def seq_arith_sum(rng, d):
    u0, r, n = rng.randint(1, 10), rng.randint(1, 6), rng.randint(5, 30)
    s = sum(u0 + i * r for i in range(n))
    assert s == n * (2 * u0 + (n - 1) * r) // 2
    return Draft(f"Quelle est la somme des {n} premiers termes (u₀ à u{n - 1}) de la suite arithmétique de premier terme {u0} et de raison {r} ?", fr(s),
                 ints(rng, s, lo=1) + [fr(n * (u0 + u0 + n * r) // 2)], f"S = n × (premier + dernier) ÷ 2 = {n} × ({u0} + {u0 + (n - 1) * r}) ÷ 2 = {fr(s)}.", src=SRC)


@gen(C, "tle-seq-geo", cap=200, cat="Suites")
def seq_geo(rng, d):
    u0, q, n = rng.randint(1, 6), rng.choice([2, 3, -2, 5, 4]), rng.randint(3, 9)
    un = u0 * q ** n
    return Draft(f"(uₙ) est géométrique de premier terme u₀ = {u0} et de raison {num(q)}. Quelle est la valeur de u{n} ?", fr(un),
                 ints(rng, un) + [fr(u0 * q * n), fr(u0 * q ** (n - 1)), fr(u0 * q ** (n + 1))], f"uₙ = u₀ × qⁿ = {u0} × {par(q)}^{n} = {fr(un)}.", src=SRC)


@gen(C, "tle-seq-geo-sum", cap=160, cat="Suites")
def seq_geo_sum(rng, d):
    u0, q, n = rng.randint(1, 5), rng.choice([2, 3, 5]), rng.randint(3, 8)
    s = sum(u0 * q ** i for i in range(n))
    assert s == u0 * (q ** n - 1) // (q - 1)
    return Draft(f"Quelle est la somme des {n} premiers termes de la suite géométrique de premier terme {u0} et de raison {q} ?", fr(s), ints(rng, s, lo=1) + [fr(u0 * q ** n)],
                 f"S = u₀ × (qⁿ − 1)/(q − 1) = {u0} × ({q}^{n} − 1)/({q} − 1) = {fr(s)}.", src=SRC)


@gen(C, "tle-seq-limit", cap=120, cat="Suites")
def seq_limit(rng, d):
    q = rng.choice([Fraction(1, 2), Fraction(1, 3), Fraction(2, 3), Fraction(3, 4), Fraction(-1, 2), 2, 3, Fraction(5, 4), Fraction(-3, 2)])
    u0 = rng.randint(1, 9)
    right = "0" if abs(q) < 1 else ("+∞" if q > 1 else "n'existe pas")
    return Draft(f"Quelle est la limite de la suite géométrique uₙ = {u0} × ({num(q)})ⁿ quand n tend vers +∞ ?", right, [x for x in ("0", "+∞", "−∞", "n'existe pas", str(u0)) if x != right],
                 "Si |q| < 1, qⁿ tend vers 0 ; si q > 1, vers +∞ ; si q < −1, la suite alterne et n'a pas de limite.", src=SRC)


@gen(C, "tle-comb", cap=240, cat="Dénombrement")
def comb(rng, d):
    n, k = rng.randint(5, 14), rng.randint(2, 5)
    if k > n:
        return None
    kind = rng.choice(["C", "A", "fact"])
    if kind == "C":
        v, txt, e = math.comb(n, k), f"Combien y a-t-il de façons de choisir {k} élèves parmi {n} (sans ordre) ?", f"C({n},{k}) = {n}!/({k}!·{n - k}!) = {math.comb(n, k)}"
        wr = [math.perm(n, k), math.comb(n, k + 1) if k + 1 <= n else v + 1, n * k, math.comb(n + 1, k)]
    elif kind == "A":
        v, txt, e = math.perm(n, k), f"Combien y a-t-il de podiums ordonnés de {k} places que l'on peut former avec {n} candidats (l'ordre compte) ?", f"A({n},{k}) = {n}!/({n - k})! = {math.perm(n, k)}"
        wr = [math.comb(n, k), n ** k, math.perm(n, k - 1), math.perm(n + 1, k)]
    else:
        m = rng.randint(4, 9)
        v, txt, e = math.factorial(m), f"Combien y a-t-il de façons de ranger {m} livres différents sur une étagère ?", f"{m}! = {math.factorial(m)}"
        wr = [m * m, math.factorial(m - 1), math.factorial(m + 1), m * (m - 1)]
    return Draft(txt, fr(v), [fr(x) for x in wr], e + ".", src=SRC)


@gen(C, "tle-anagram", cap=120, cat="Dénombrement")
def anagram(rng, d):
    words = ["LION", "SOLEIL", "PAPA", "MAMAN", "BANANE", "ECOLE", "NOIR", "CACAO", "TOTO", "SISI", "ELEVE", "DOUALA", "MAMA", "KARATE", "ANANAS", "TABAC", "PAPAYE", "RADAR", "ABACA", "TOMATE"]
    w = rng.choice(words)
    if len(w) > 7:
        return None
    v = len(set(permutations(w)))
    from collections import Counter
    f = math.factorial(len(w))
    for c in Counter(w).values():
        f //= math.factorial(c)
    assert f == v
    return Draft(f"Combien d'anagrammes (mots de {len(w)} lettres, même sans sens) peut-on former avec les lettres du mot {w} ?", fr(v), ints(rng, v, lo=1) + [fr(math.factorial(len(w)))],
                 f"{len(w)}! divisé par les factorielles des lettres répétées = {fr(v)}.", src=SRC)


@gen(C, "tle-binomial", cap=160, cat="Probabilités")
def binomial(rng, d):
    n, k = rng.randint(3, 8), 0
    k = rng.randint(0, n)
    p = Fraction(math.comb(n, k), 2 ** n)
    wr = {Fraction(1, 2 ** n), Fraction(k, n) if n else 1, Fraction(math.comb(n, k), 2 ** (n - 1)) if math.comb(n, k) <= 2 ** (n - 1) else Fraction(1, 2), Fraction(math.comb(n, k) + 1, 2 ** n), Fraction(1, n + 1)} - {p}
    return Draft(f"On lance {n} fois une pièce équilibrée. Quelle est la probabilité d'obtenir exactement {k} fois « face » ?", num(p), [num(x) for x in sorted(wr)],
                 f"P = C({n},{k}) × (1/2)^{n} = {math.comb(n, k)}/{2 ** n} = {num(p)}.", src=SRC)


@gen(C, "tle-conditional", cap=160, cat="Probabilités")
def conditional(rng, d):
    pb = Fraction(rng.choice([2, 3, 4, 5, 6]), 10)
    pab = Fraction(rng.choice([1, 2, 3]), 10)
    if pab >= pb:
        return None
    p = pab / pb
    wr = {pb * pab, pab + pb, pb / pab if pb / pab <= 1 else Fraction(1, 2), 1 - p, pab}
    wr.discard(p)
    return Draft(f"On sait que P(B) = {num(pb)} et P(A ∩ B) = {num(pab)}. Quelle est la probabilité conditionnelle P(A sachant B) ?", num(p), [num(x) for x in sorted(wr)][:6],
                 f"P_B(A) = P(A ∩ B) ÷ P(B) = {num(pab)} ÷ {num(pb)} = {num(p)}.", src=SRC)


@gen(C, "tle-expectation", cap=140, cat="Probabilités")
def expectation(rng, d):
    xs = rng.sample([-5, -2, 0, 1, 2, 3, 5, 10, 20], 3)
    ps = rng.choice([(Fraction(1, 2), Fraction(3, 10), Fraction(1, 5)), (Fraction(1, 4), Fraction(1, 4), Fraction(1, 2)), (Fraction(6, 10), Fraction(3, 10), Fraction(1, 10)), (Fraction(1, 5), Fraction(2, 5), Fraction(2, 5))])
    e = sum(x * p for x, p in zip(xs, ps))
    return Draft(f"Une variable aléatoire X prend les valeurs {num(xs[0])}, {num(xs[1])}, {num(xs[2])} avec les probabilités {num(ps[0])}, {num(ps[1])}, {num(ps[2])}. Quelle est son espérance ?", num(e),
                 [num(e + k) for k in (1, -1, Fraction(1, 2), 2)] + [num(Fraction(sum(xs), 3))], "E(X) = somme des valeurs × probabilités : " + " + ".join(f"{par(x)}×{num(p)}" for x, p in zip(xs, ps)) + f" = {num(e)}.", src=SRC)


@gen(C, "tle-quadratic-roots", cap=220, cat="Algèbre")
def quad_roots(rng, d):
    r1, r2 = rng.randint(-8, 8), rng.randint(-8, 8)
    if r1 == r2:
        return None
    a = rng.choice([1, 1, 2, -1, 3])
    co = [a, -a * (r1 + r2), a * r1 * r2]
    assert ev(co, r1) == 0 and ev(co, r2) == 0
    right = "{" + "; ".join(num(v) for v in sorted({r1, r2})) + "}"
    wr = ["{" + "; ".join(num(v) for v in sorted({-r1, -r2})) + "}", "{" + "; ".join(num(v) for v in sorted({r1, -r2})) + "}", "{" + "; ".join(num(v) for v in sorted({r1 + 1, r2})) + "}",
          "{" + "; ".join(num(v) for v in sorted({r1 * r2, r1 + r2})) + "}"]
    delta = co[1] ** 2 - 4 * co[0] * co[2]
    return Draft(f"Quel est l'ensemble des solutions de l'équation {poly(co)} = 0 ?", right, wr, f"Δ = b² − 4ac = {num(delta)} = {math.isqrt(delta)}² ; x = (−b ± √Δ)/(2a) donne {right}.", src=SRC)


@gen(C, "tle-discriminant", cap=200, cat="Algèbre")
def discriminant(rng, d):
    a, b, c = nz(rng, -4, 5), rng.randint(-8, 8), rng.randint(-8, 8)
    dl = b * b - 4 * a * c
    return Draft(f"Quel est le discriminant Δ du trinôme {poly([a, b, c])} ?", num(dl), [num(v) for v in (b * b + 4 * a * c, b * b - 2 * a * c, -dl, b * b - 4 * a + c)] + [num(dl + 4)],
                 f"Δ = b² − 4ac = {par(b)}² − 4 × {par(a)} × {par(c)} = {num(dl)}.", src=SRC)


@gen(C, "tle-vertex", cap=180, cat="Analyse")
def vertex(rng, d):
    a = rng.choice([1, 2, -1, -2, 3])
    h, k = rng.randint(-5, 6), rng.randint(-9, 9)
    b, c = -2 * a * h, a * h * h + k
    return Draft(f"La fonction f(x) = {poly([a, b, c])} admet un {'minimum' if a > 0 else 'maximum'}. Pour quelle valeur de x est-il atteint ?", num(h), [num(v) for v in near_ints(rng, h, 6)] + [num(-h), num(k)],
                 f"L'extremum est atteint en x = −b/(2a) = {num(-b)}/{2 * a} = {num(h)} ; il vaut {num(k)}.", src=SRC)


@gen(C, "tle-exp-eq", cap=200, cat="Analyse")
def exp_eq(rng, d):
    kind = rng.choice(["exp", "ln", "pow2", "log10", "lnexp"])
    if kind == "exp":
        a = rng.choice([2, 3, 5, 7, 10])
        return Draft(f"Quelle est la solution de l'équation eˣ = {a} ?", f"ln({a})", [f"e^{a}", f"{a}", f"1/{a}", f"ln({a + 1})", f"log({a})"], f"On applique ln aux deux membres : x = ln({a}).", src=SRC)
    if kind == "ln":
        k = rng.randint(1, 5)
        return Draft(f"Quelle est la solution de l'équation ln(x) = {k} ?", f"e^{k}" if k > 1 else "e", [f"{k}e", f"ln({k})", f"{k}", f"e^{k + 1}", f"{k}²"], f"ln(x) = {k} équivaut à x = e^{k}.", src=SRC)
    if kind == "pow2":
        n = rng.randint(3, 12)
        return Draft(f"Quelle est la solution de l'équation 2ˣ = {2 ** n} ?", str(n), [str(v) for v in near_ints(rng, n, 6, lo=1)] + [str(2 ** n // 2)], f"{2 ** n} = 2^{n}, donc x = {n}.", src=SRC)
    if kind == "log10":
        n = rng.randint(2, 7)
        return Draft(f"Quelle est la valeur de log₁₀({10 ** n}) ?", str(n), [str(v) for v in near_ints(rng, n, 6, lo=0)] + [str(10 ** n)], f"10^{n} = {10 ** n}, donc log({10 ** n}) = {n}.", src=SRC)
    k = rng.randint(2, 9)
    return Draft(f"Quelle est la valeur de ln(e^{k}) ?", str(k), [str(v) for v in near_ints(rng, k, 6, lo=0)] + ["e", f"e^{k}"], f"ln et exp sont réciproques : ln(e^{k}) = {k}.", src=SRC)


@gen(C, "tle-radians", cap=120, cat="Trigonométrie")
def radians(rng, d):
    deg = rng.choice([30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 330, 360, 15, 75, 36, 72, 18, 20, 40, 100, 140])
    f = Fraction(deg, 180)
    def pi(fr_):
        if fr_.numerator == 1:
            return "π" if fr_.denominator == 1 else f"π/{fr_.denominator}"
        return (f"{fr_.numerator}π" if fr_.denominator == 1 else f"{fr_.numerator}π/{fr_.denominator}")
    wr = {pi(Fraction(deg, 90)), pi(Fraction(deg, 360)), pi(Fraction(180, deg)), pi(f + Fraction(1, 6)), pi(Fraction(deg + 30, 180))} - {pi(f)}
    return Draft(f"À combien de radians correspondent {deg}° ?", pi(f) + " rad", [w + " rad" for w in sorted(wr)], f"{deg}° = {deg} × π/180 = {pi(f)} rad.", src=SRC)


TRIG = {"sin": {0: "0", 30: "1/2", 45: "√2/2", 60: "√3/2", 90: "1", 120: "√3/2", 135: "√2/2", 150: "1/2", 180: "0", 210: "−1/2", 225: "−√2/2", 240: "−√3/2", 270: "−1", 300: "−√3/2", 315: "−√2/2", 330: "−1/2"},
        "cos": {0: "1", 30: "√3/2", 45: "√2/2", 60: "1/2", 90: "0", 120: "−1/2", 135: "−√2/2", 150: "−√3/2", 180: "−1", 210: "−√3/2", 225: "−√2/2", 240: "−1/2", 270: "0", 300: "1/2", 315: "√2/2", 330: "√3/2"}}


@gen(C, "tle-trig-values", cap=120, cat="Trigonométrie")
def trig_values(rng, d):
    fn = rng.choice(["sin", "cos"])
    deg = rng.choice(list(TRIG[fn]))
    # check with math
    val = {"1/2": .5, "√2/2": math.sqrt(2) / 2, "√3/2": math.sqrt(3) / 2, "0": 0, "1": 1, "−1": -1, "−1/2": -.5, "−√2/2": -math.sqrt(2) / 2, "−√3/2": -math.sqrt(3) / 2}[TRIG[fn][deg]]
    assert abs((math.sin if fn == "sin" else math.cos)(math.radians(deg)) - val) < 1e-9
    right = TRIG[fn][deg]
    wr = [x for x in ("0", "1/2", "√2/2", "√3/2", "1", "−1/2", "−√2/2", "−√3/2", "−1") if x != right]
    rng.shuffle(wr)
    return Draft(f"Quelle est la valeur exacte de {fn}({deg}°) ?", right, wr[:5], f"Valeur remarquable du cercle trigonométrique : {fn}({deg}°) = {right}.", src=SRC)


@gen(C, "tle-vectors", cap=200, cat="Géométrie dans l'espace")
def vectors(rng, d):
    a, b, c, dd = rng.choice([(2, 3, 6, 7), (1, 2, 2, 3), (2, 6, 9, 11), (4, 4, 7, 9), (1, 4, 8, 9), (6, 6, 7, 11), (2, 10, 11, 15)])
    s = [rng.choice([-1, 1]) for _ in range(3)]
    v = (s[0] * a, s[1] * b, s[2] * c)
    assert math.isqrt(sum(x * x for x in v)) == dd
    return Draft(f"Quelle est la norme du vecteur u({num(v[0])} ; {num(v[1])} ; {num(v[2])}) ?", str(dd), [str(x) for x in near_ints(rng, dd, 6, lo=1)] + [str(sum(abs(x) for x in v))],
                 f"‖u‖ = √({v[0] ** 2} + {v[1] ** 2} + {v[2] ** 2}) = √{dd * dd} = {dd}.", src=SRC)


@gen(C, "tle-dot", cap=200, cat="Géométrie dans l'espace")
def dot(rng, d):
    u = [rng.randint(-6, 7) for _ in range(3)]
    v = [rng.randint(-6, 7) for _ in range(3)]
    p = sum(x * y for x, y in zip(u, v))
    f = lambda w: f"({num(w[0])} ; {num(w[1])} ; {num(w[2])})"
    return Draft(f"Quel est le produit scalaire de u{f(u)} et v{f(v)} ?", num(p), [num(x) for x in near_ints(rng, p, 6)] + [num(u[0] * v[0] + u[1] * v[1] - u[2] * v[2]), num(sum(x + y for x, y in zip(u, v)))],
                 f"u·v = {' + '.join(f'{par(x)}×{par(y)}' for x, y in zip(u, v))} = {num(p)}.", src=SRC)


# ---------------------------------------------------------------------------------------- physique-chimie (Tle)
@gen(C, "tle-free-fall", cap=180, cat="Physique-Chimie", source=PC)
def free_fall(rng, d):
    t = rng.choice([0.5, 1, 1.5, 2, 2.5, 3, 4, 5])
    if rng.random() < 0.5:
        v = 10 * t
        return Draft(f"Un corps tombe sans vitesse initiale (chute libre, g = 10 m/s²). Quelle est sa vitesse au bout de {fr(t, 1)} s ?", f"{fr(v, 1)} m/s", [f"{fr(x, 1)} m/s" for x in (5 * t * t, 10 + t, v * 2, v / 2)], f"v = g·t = 10 × {fr(t, 1)} = {fr(v, 1)} m/s.", src=PC)
    h = 5 * t * t
    return Draft(f"Un corps tombe sans vitesse initiale (chute libre, g = 10 m/s²). Quelle distance a-t-il parcourue au bout de {fr(t, 1)} s ?", f"{fr(h, 2)} m", [f"{fr(x, 2)} m" for x in (10 * t, 10 * t * t, h / 2, h * 2)],
                 f"h = ½·g·t² = 0,5 × 10 × {fr(t, 1)}² = {fr(h, 2)} m.", src=PC)


@gen(C, "tle-kinetic", cap=200, cat="Physique-Chimie", source=PC)
def kinetic(rng, d):
    m, v = rng.choice([0.5, 1, 2, 4, 10, 50, 80, 1000]), rng.choice([2, 4, 5, 10, 20, 30])
    e = 0.5 * m * v * v
    return Draft(f"Quelle est l'énergie cinétique d'un objet de {fr(m, 2)} kg qui se déplace à {v} m/s ?", f"{fr(e, 2)} J", [f"{fr(x, 2)} J" for x in (m * v, m * v * v, e * 2, e / 2)], f"Ec = ½·m·v² = 0,5 × {fr(m, 2)} × {v}² = {fr(e, 2)} J.", src=PC)


@gen(C, "tle-potential", cap=160, cat="Physique-Chimie", source=PC)
def potential(rng, d):
    m, h = rng.choice([1, 2, 5, 10, 20, 50]), rng.choice([2, 5, 10, 20, 50, 100])
    e = m * 10 * h
    return Draft(f"Quelle est l'énergie potentielle de pesanteur d'un objet de {m} kg placé à {h} m de hauteur (g = 10 N/kg) ?", f"{fr(e)} J", [f"{fr(x)} J" for x in (m * h, e // 2 or 5, e * 10, m + 10 * h)], f"Ep = m·g·h = {m} × 10 × {h} = {fr(e)} J.", src=PC)


@gen(C, "tle-wavelength", cap=160, cat="Physique-Chimie", source=PC)
def wavelength(rng, d):
    v, f = rng.choice([340, 1500, 3e8, 300, 340]), rng.choice([100, 200, 340, 500, 1000, 2000, 50])
    lam = v / f
    return Draft(f"Une onde de fréquence {fr(f)} Hz se propage à {fr(v)} m/s. Quelle est sa longueur d'onde ?", f"{fr(lam, 4)} m", [f"{fr(x, 4)} m" for x in (v * f, f / v, lam * 10, lam / 10)], f"λ = v ÷ f = {fr(v)} ÷ {fr(f)} = {fr(lam, 4)} m.", src=PC)


@gen(C, "tle-rc", cap=160, cat="Physique-Chimie", source=PC)
def rc(rng, d):
    r, c = rng.choice([100, 200, 500, 1000, 2000, 10000]), rng.choice([1e-6, 10e-6, 100e-6, 1e-3, 22e-6, 470e-6])
    tau = r * c
    cc = {1e-6: "1 µF", 10e-6: "10 µF", 100e-6: "100 µF", 1e-3: "1 mF", 22e-6: "22 µF", 470e-6: "470 µF"}[c]
    return Draft(f"Un condensateur de {cc} est chargé à travers une résistance de {fr(r)} Ω. Quelle est la constante de temps τ = RC ?", f"{fr(tau * 1000, 3)} ms", [f"{fr(x * 1000, 3)} ms" for x in (r / c / 1e6, tau * 10, tau / 10, r + c)],
                 f"τ = R × C = {fr(r)} × {cc} = {fr(tau * 1000, 3)} ms.", src=PC)


@gen(C, "tle-half-life", cap=200, cat="Physique-Chimie", source=PC)
def half_life(rng, d):
    n0, T, k = rng.choice([80, 100, 160, 200, 320, 640, 1000, 1600]), rng.choice([2, 3, 5, 6, 8, 10, 24]), rng.randint(1, 4)
    n = Fraction(n0, 2 ** k)
    if n.denominator != 1:
        return None
    return Draft(f"Un échantillon contient {n0} noyaux radioactifs de demi-vie {T} jours. Combien en reste-t-il au bout de {k * T} jours ?", str(int(n)),
                 [str(x) for x in (n0 // 2, n0 - k * T, int(n) * 2, int(n) + 10, n0 // (2 * k) if k else 1) if x != int(n)], f"{k * T} jours = {k} demi-vie(s) : N = {n0} ÷ 2^{k} = {int(n)}.", src=PC)


@gen(C, "tle-ph", cap=160, cat="Physique-Chimie", source=PC)
def ph(rng, d):
    n = rng.randint(1, 13)
    if rng.random() < 0.5:
        return Draft(f"Une solution a une concentration en ions H₃O⁺ de 10^(−{n}) mol/L. Quel est son pH ?", str(n), [str(v) for v in near_ints(rng, n, 6, lo=0, hi=14)] + [str(14 - n)], f"pH = −log[H₃O⁺] = −log(10^(−{n})) = {n}.", src=PC)
    return Draft(f"Quelle est la concentration en ions H₃O⁺ d'une solution de pH = {n} ?", f"10^(−{n}) mol/L", [f"10^({n}) mol/L", f"10^(−{14 - n}) mol/L", f"{n} mol/L", f"10^(−{n + 1}) mol/L"], f"[H₃O⁺] = 10^(−pH) = 10^(−{n}) mol/L.", src=PC)


@gen(C, "tle-titration", cap=200, cat="Physique-Chimie", source=PC)
def titration(rng, d):
    ca, va, vb = rng.choice([0.1, 0.2, 0.05, 0.5, 1]), rng.choice([10, 20, 25, 50]), rng.choice([5, 10, 12.5, 20, 25, 40, 50])
    cb = ca * va / vb
    return Draft(f"On dose {va} mL d'acide chlorhydrique à {fr(ca, 2)} mol/L par de la soude. L'équivalence est atteinte pour {fr(vb, 1)} mL de soude versée. Quelle est la concentration de la soude ?", f"{fr(cb, 3)} mol/L",
                 [f"{fr(x, 3)} mol/L" for x in (ca * vb / va, ca, cb * 2, cb / 2, ca * va)], f"À l'équivalence Ca·Va = Cb·Vb donc Cb = {fr(ca, 2)} × {va} ÷ {fr(vb, 1)} = {fr(cb, 3)} mol/L.", src=PC)


@gen(C, "tle-lorentz-power", cap=140, cat="Physique-Chimie", source=PC)
def joule(rng, d):
    r, i, t = rng.choice([10, 20, 50, 100]), rng.choice([0.5, 1, 2, 3]), rng.choice([10, 30, 60, 120])
    e = r * i * i * t
    return Draft(f"Un conducteur de {r} Ω est parcouru par un courant de {fr(i, 1)} A pendant {t} s. Quelle énergie dissipe-t-il par effet Joule ?", f"{fr(e)} J", [f"{fr(x)} J" for x in (int(r * i * t), int(r * i * i), int(e * 10), int(r * i * i * t // 2))],
                 f"W = R·I²·t = {r} × {fr(i, 1)}² × {t} = {fr(e)} J.", src=PC)
