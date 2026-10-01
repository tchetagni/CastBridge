"""Mathématiques / Mathematics, premier cycle : 6e, 5e, 4e (francophone) et Form 1-3 (anglophone). Réponses calculées (fractions
exactes, valeurs vérifiées par substitution) ; mauvaises réponses = erreurs typiques (signe, priorité, oubli de la puissance)."""
import math
from fractions import Fraction

from ..core import Draft, MINUS
from .mathfmt import num, poly, sup
from .pc_common import N, ask, money, name, plaus, q_end, reg, wrongs_int
from . import pc_maths_prim as P

SEC = ["6e", "5e", "4e", "form1", "form2", "form3"]
S78 = ["5e", "4e", "form2", "form3"]          # g >= 8
S9 = ["4e", "form3"]                           # g == 9
S7 = ["6e", "5e", "form1", "form2"]            # g <= 8


def sg(x):
    """Integer with the typographic minus."""
    return (MINUS if x < 0 else "") + str(abs(x))


def sgp(x):
    """Integer in brackets when negative."""
    return "(" + sg(x) + ")" if x < 0 else str(x)


def fr_s(f):
    return num(Fraction(f))


# ------------------------------------------------------------------------------------------------ nombres relatifs
def rel_op(rng, d, g, lg):
    m = 5 + 4 * d
    a, b = rng.randint(-m, m), rng.randint(-m, m)
    if a == 0 or b == 0:
        return None
    op = rng.choice(["+", "−", "×"] if d >= 3 else ["+", "−"])
    r = a + b if op == "+" else a - b if op == "−" else a * b
    wr = [a + b, a - b, abs(a) + abs(b), -r, abs(r), a * b if op != "×" else a + b, abs(abs(a) - abs(b))]
    t = ask(lg, f"Combien fait {sgp(a)} {op} {sgp(b)}", f"What is {sgp(a)} {op} {sgp(b)}")
    return Draft(q_end(lg, t), sg(r), [sg(w) for w in wr if w != r], ask(lg, f"{sgp(a)} {op} {sgp(b)} = {sg(r)} (règle des signes).", f"{sgp(a)} {op} {sgp(b)} = {sg(r)} (rules for signs)."))


def rel_compare(rng, d, g, lg):
    m = 5 + 6 * d
    vals = rng.sample(range(-m, m + 1), 4)
    big = rng.random() < 0.5
    r = max(vals) if big else min(vals)
    sh = vals[:]
    t = ask(lg, f"Quel est le plus {'grand' if big else 'petit'} de ces nombres : {' ; '.join(sg(v) for v in sh)}", f"Which is the {'greatest' if big else 'smallest'} of these numbers: {', '.join(sg(v) for v in sh)}")
    return Draft(q_end(lg, t), sg(r), [sg(v) for v in vals if v != r], ask(lg, "Sur la droite graduée, plus on va vers la gauche, plus le nombre est petit.", "On the number line, the further left, the smaller the number."))


def rel_opposite(rng, d, g, lg):
    a = rng.randint(-9 * d, 9 * d)
    if a == 0:
        return None
    t = ask(lg, f"Quel est l'opposé de {sgp(a)}", f"What is the opposite of {sgp(a)}")
    return Draft(q_end(lg, t), sg(-a), [sg(v) for v in (a, -a + 1, a + 1, -a - 1, abs(a) + 10) if v != -a], ask(lg, f"L'opposé de {sgp(a)} est {sg(-a)} : leur somme vaut 0.", f"The opposite of {sgp(a)} is {sg(-a)}: they add up to 0."))


def temp_rel(rng, d, g, lg):
    a, b = rng.randint(-12, 25), rng.randint(-12, 25)
    if a == b:
        return None
    nm = rng.choice(["Ngaoundéré", "Douala", "Bamenda", "Garoua", "Buea", "Yaoundé"]) if False else None
    t = ask(lg, f"La température passe de {sg(a)} °C à {sg(b)} °C. De combien de degrés a-t-elle varié (en valeur absolue)", f"The temperature changes from {sg(a)} °C to {sg(b)} °C. By how many degrees did it change")
    r = abs(b - a)
    return Draft(q_end(lg, t), f"{r} °C", [f"{w} °C" for w in (abs(a) + abs(b) if a * b > 0 else abs(abs(a) - abs(b)), r + 1, r - 1, r + 10, a + b if a + b > 0 else r + 2) if w != r and w > 0],
                 f"|{sg(b)} − {sgp(a)}| = {r}.")


# ------------------------------------------------------------------------------------------------ équations et calcul littéral
def eq1(rng, d, g, lg):
    x = rng.randint(1, 12 if g <= 7 else 15) * (1 if g <= 7 else rng.choice([1, -1]))
    kind = rng.choice(["x+a", "x-a", "ax"]) if g <= 7 else rng.choice(["ax+b", "ax-b", "ax+b2"])
    if kind == "x+a":
        a = rng.randint(1, 20)
        eq, ans, e = f"x + {a} = {x + a}", x, f"x = {x + a} − {a} = {x}"
    elif kind == "x-a":
        a = rng.randint(1, 20)
        eq, ans, e = f"x − {a} = {x - a}", x, f"x = {x - a} + {a} = {x}"
    elif kind == "ax":
        a = rng.randint(2, 9)
        eq, ans, e = f"{a}x = {a * x}", x, f"x = {a * x} ÷ {a} = {x}"
    elif kind == "ax+b":
        a, b = rng.randint(2, 9), rng.randint(1, 15)
        eq, ans, e = f"{a}x + {b} = {a * x + b}", x, f"{a}x = {a * x + b} − {b} = {a * x}, donc x = {x}"
    elif kind == "ax-b":
        a, b = rng.randint(2, 9), rng.randint(1, 15)
        eq, ans, e = f"{a}x − {b} = {a * x - b}", x, f"{a}x = {a * x - b} + {b} = {a * x}, donc x = {x}"
    else:
        a, b = rng.randint(2, 9), rng.randint(1, 15)
        c = rng.randint(1, a - 1)
        eq, ans, e = f"{a}x + {b} = {c}x + {b + (a - c) * x}", x, f"{a - c}x = {(a - c) * x}, donc x = {x}"
    eq, e = eq.replace("-", MINUS), e.replace("-", MINUS)
    wr = [ans + 1, ans - 1, -ans, ans * 2, ans + 2, ans + 3]
    return Draft(q_end(lg, ask(lg, f"Quelle est la solution de l'équation {eq}", f"What is the solution of the equation {eq}")), sg(ans), [sg(w) for w in wr if w != ans],
                 (e if lg == "fr" else e.replace("donc", "so")) + ".")


def reduce_expr(rng, d, g, lg):
    a, b, c = rng.randint(2, 9), rng.randint(1, 9), rng.randint(1, 9)
    if rng.random() < 0.5:
        sgn = rng.choice([1, -1])
        r = [a + sgn * b, 0]
        expr = f"{a}x {'+' if sgn > 0 else '−'} {b}x + {c}"
        right = poly([a + sgn * b, c]) if a + sgn * b != 0 else str(c)
        wr = [poly([a + sgn * b, 0]) if False else f"{a + sgn * b + c}x", poly([a * b, c]), f"{a + sgn * b}x²" if a + sgn * b != 1 else "x²", poly([a - sgn * b, c]), poly([a + sgn * b + 1, c])]
    else:
        e = rng.randint(1, 9)
        expr = f"{a}x + {b} + {c}x + {e}"
        right = poly([a + c, b + e])
        wr = [poly([a + c, b]), poly([a * c, b + e]), poly([a + c + b + e, 0]) if False else f"{a + c + b + e}x", poly([a + c, b - e]) if b != e else poly([a + c, b + e + 1]), poly([a + c + 1, b + e])]
    return Draft(q_end(lg, ask(lg, f"Quelle est la forme réduite de {expr}", f"Which is the simplified form of {expr}")), right, [w for w in wr if w != right],
                 ask(lg, "On regroupe les termes en x entre eux et les nombres entre eux.", "Collect the x terms together and the numbers together."))


def expand(rng, d, g, lg):
    k = rng.randint(2, 9)
    a, b = rng.randint(1, 9), rng.randint(1, 9)
    sgn = rng.choice([1, -1])
    expr = f"{k}(x {'+' if sgn > 0 else '−'} {b})"
    right = poly([k, sgn * k * b])
    wr = [poly([k, sgn * b]), poly([1, sgn * k * b]), poly([k, -sgn * k * b]), poly([k + 1, sgn * k * b]), poly([k * k, sgn * k * b])]
    return Draft(q_end(lg, ask(lg, f"Quelle est la forme développée de {expr}", f"Which is the expanded form of {expr}")), right, [w for w in wr if w != right],
                 ask(lg, f"On multiplie chaque terme par {k} : {right}.", f"Multiply each term by {k}: {right}."))


def evaluate(rng, d, g, lg):
    a, b = rng.randint(2, 9), rng.randint(1, 12)
    x = rng.randint(-5, 8)
    sgn = rng.choice([1, -1])
    v = a * x + sgn * b
    expr = f"{a}x {'+' if sgn > 0 else '−'} {b}"
    wr = [a * x - sgn * b, a + x + sgn * b, a * (x + sgn * b), v + 1, -v, a * abs(x) + sgn * b]
    return Draft(q_end(lg, ask(lg, f"Quelle est la valeur de {expr} pour x = {sg(x)}", f"What is the value of {expr} when x = {sg(x)}")), sg(v), [sg(w) for w in wr if w != v],
                 f"{a} × {sgp(x)} {'+' if sgn > 0 else '−'} {b} = {sg(v)}.")


def problem_eq(rng, d, g, lg):
    nm = name(rng, lg)
    x = rng.randint(3, 40)
    a = rng.randint(2, 6)
    b = rng.randint(5, 60)
    tot = a * x + b
    t = ask(lg, f"{nm} pense à un nombre. On le multiplie par {a} puis on ajoute {b} : on obtient {tot}. Quel est ce nombre", f"{nm} thinks of a number. It is multiplied by {a} and then {b} is added, giving {tot}. What is the number")
    return Draft(q_end(lg, t), str(x), [str(v) for v in plaus(rng, x, 1)] + [str((tot - b)), str(tot // a)], f"{a}x + {b} = {tot} donne {a}x = {tot - b}, donc x = {x}." if lg == "fr" else f"{a}x + {b} = {tot} gives {a}x = {tot - b}, so x = {x}.")


# ------------------------------------------------------------------------------------------------ arithmétique
PRIMES = [p for p in range(2, 200) if all(p % q for q in range(2, int(p ** .5) + 1))]


def prime_q(rng, d, g, lg):
    top = 30 + 25 * d
    pr = rng.choice([p for p in PRIMES if p < top and p > 4])
    comps = rng.sample([n for n in range(9, top) if n not in PRIMES and n % 2 and n % 5], 3) if rng.random() < 0.8 else None
    if comps is None:
        return None
    return Draft(q_end(lg, ask(lg, "Lequel de ces nombres est premier", "Which of these numbers is a prime number")), str(pr), [str(c) for c in comps],
                 ask(lg, f"{pr} n'est divisible que par 1 et par lui-même.", f"{pr} is divisible only by 1 and itself."))


def composite_q(rng, d, g, lg):
    top = 30 + 25 * d
    ps = rng.sample([p for p in PRIMES if p < top], 3)
    comp = rng.choice([n for n in range(9, top) if n not in PRIMES and n % 2 and n % 5])
    f = next(q for q in range(3, comp) if comp % q == 0)
    return Draft(q_end(lg, ask(lg, "Lequel de ces nombres n'est pas premier", "Which of these numbers is not a prime number")), str(comp), [str(p) for p in ps],
                 ask(lg, f"{comp} = {f} × {comp // f} : il a d'autres diviseurs que 1 et lui-même.", f"{comp} = {f} × {comp // f}: it has divisors other than 1 and itself."))


def divisible(rng, d, g, lg):
    k = rng.choice([2, 3, 5, 9, 10, 4] if d > 2 else [2, 5, 10, 3])
    pool = [n for n in range(20, 2000) if n % k == 0]
    r = rng.choice(pool)
    others = rng.sample([n for n in range(20, 2000) if n % k != 0 and abs(n - r) > 3], 3)
    return Draft(q_end(lg, ask(lg, f"Lequel de ces nombres est divisible par {k}", f"Which of these numbers is divisible by {k}")), str(r), [str(o) for o in others],
                 ask(lg, f"{r} = {k} × {r // k}.", f"{r} = {k} × {r // k}."))


def gcd_q(rng, d, g, lg):
    g0 = rng.randint(2, 6 + d)
    a0, b0 = rng.sample([k for k in range(2, 14) if True], 2)
    if math.gcd(a0, b0) != 1:
        return None
    a, b = g0 * a0, g0 * b0
    wr = [a * b // g0, g0 * 2 if g0 * 2 < min(a, b) else g0 + 1, g0 + 1, g0 - 1 if g0 > 2 else g0 + 2, math.gcd(a0, b0) or 1, min(a, b)]
    return Draft(q_end(lg, ask(lg, f"Quel est le PGCD de {a} et {b}", f"What is the highest common factor (HCF) of {a} and {b}")), str(g0), [str(w) for w in wr if w != g0 and w > 0],
                 ask(lg, f"{a} = {g0} × {a0} et {b} = {g0} × {b0}, avec {a0} et {b0} sans diviseur commun : PGCD = {g0}.", f"{a} = {g0} × {a0} and {b} = {g0} × {b0}, and {a0}, {b0} share no factor: HCF = {g0}."))


def lcm_q(rng, d, g, lg):
    a, b = rng.sample(range(2, 9 + 2 * d), 2)
    l = a * b // math.gcd(a, b)
    wr = [a * b if a * b != l else l + a, l * 2, l // 2 if l % 2 == 0 and l // 2 not in (a, b) else l + b, max(a, b), l + 1, a + b]
    return Draft(q_end(lg, ask(lg, f"Quel est le plus petit multiple commun non nul de {a} et {b}", f"What is the lowest common multiple (LCM) of {a} and {b}")), str(l), [str(w) for w in wr if w != l and w > 0],
                 ask(lg, f"Les multiples de {max(a, b)} : on cherche le premier qui est aussi multiple de {min(a, b)} : {l}.", f"Look for the first multiple of {max(a, b)} that is also a multiple of {min(a, b)}: {l}."))


def divisors_count(rng, d, g, lg):
    n = rng.choice([n for n in range(6, 20 + 20 * d) if n not in PRIMES])
    divs = [k for k in range(1, n + 1) if n % k == 0]
    return Draft(q_end(lg, ask(lg, f"Combien le nombre {n} a-t-il de diviseurs", f"How many divisors does {n} have")), str(len(divs)), [str(v) for v in plaus(rng, len(divs), 1, None, 1, 6) if v != len(divs)],
                 ask(lg, f"Diviseurs de {n} : {', '.join(map(str, divs))}.", f"Divisors of {n}: {', '.join(map(str, divs))}."))


# ------------------------------------------------------------------------------------------------ pourcentages et proportionnalité
def percent_of(rng, d, g, lg):
    p = rng.choice([10, 20, 25, 50, 5, 15, 30, 40, 75, 12, 8][: 5 + 2 * d])
    base = rng.choice([40, 60, 80, 100, 120, 200, 240, 300, 500, 800, 1200, 2000])
    if (p * base) % 100:
        return None
    r = p * base // 100
    wr = [base - r, r * 10, r // 10 if r >= 10 else r + 5, p + base, r + p, base // p if base % p == 0 else r + 20]
    return Draft(q_end(lg, ask(lg, f"Combien font {p} % de {N(base, lg)}", f"What is {p}% of {N(base, lg)}")), N(r, lg), [N(w, lg) for w in wr if w != r and w > 0],
                 f"{p} % de {N(base, lg)} = {N(base, lg)} × {p} ÷ 100 = {N(r, lg)}." if lg == "fr" else f"{p}% of {N(base, lg)} = {N(base, lg)} × {p} ÷ 100 = {N(r, lg)}.")


def discount(rng, d, g, lg):
    nm = name(rng, lg)
    p = rng.choice([10, 20, 25, 30, 50, 5, 15])
    price = rng.choice([2000, 4000, 5000, 8000, 10000, 12000, 15000, 20000, 25000, 40000])
    if price * p % 100:
        return None
    up = rng.random() < 0.4
    delta = price * p // 100
    r = price + delta if up else price - delta
    what_fr = ["un sac", "une paire de chaussures", "une chemise", "un pagne", "un téléphone"]
    what_en = ["a bag", "a pair of shoes", "a shirt", "a wrapper", "a phone"]
    i = rng.randrange(5)
    t = ask(lg, f"{what_fr[i].capitalize()} coûte {money(price, lg)}. Son prix {'augmente' if up else 'baisse'} de {p} %. Quel est le nouveau prix",
            f"{what_en[i].capitalize()} costs {money(price, lg)}. Its price {'rises' if up else 'drops'} by {p}%. What is the new price")
    wr = [price - delta if up else price + delta, delta, price + delta * 2 if up else price - delta * 2, r + 1000, r - 500 if r > 1000 else r + 500, price * p // 10]
    return Draft(q_end(lg, t), money(r, lg), [money(w, lg) for w in wr if w != r and w > 0], f"{p} % de {N(price, lg)} = {N(delta, lg)} ; nouveau prix : {N(r, lg)} FCFA." if lg == "fr" else f"{p}% of {N(price, lg)} = {N(delta, lg)}; new price: {N(r, lg)} FCFA.")


def percent_what(rng, d, g, lg):
    tot = rng.choice([20, 25, 40, 50, 60, 80, 100, 200, 250, 400])
    p = rng.choice([10, 20, 25, 30, 40, 50, 60, 75, 5])
    if tot * p % 100:
        return None
    part = tot * p // 100
    what_fr, what_en = rng.choice([("élèves", "pupils"), ("billes", "marbles"), ("mangues", "mangoes"), ("livres", "books")])
    t = ask(lg, f"Dans une classe de {tot} {what_fr}, {part} sont des filles. Quel pourcentage cela représente-t-il", f"Out of {tot} {what_en}, {part} are girls. What percentage is that") if what_fr == "élèves" else \
        ask(lg, f"Sur {tot} {what_fr}, {part} sont abîmées. Quel pourcentage cela représente-t-il", f"Out of {tot} {what_en}, {part} are damaged. What percentage is that")
    if what_fr == "livres":
        t = ask(lg, f"Sur {tot} livres, {part} sont en français. Quel pourcentage cela représente-t-il", f"Out of {tot} books, {part} are in French. What percentage is that")
    wr = [100 - p, p + 10, p * 2 if p * 2 < 100 else p - 5, part, tot - part, p - 5 if p > 5 else p + 15]
    return Draft(q_end(lg, t), f"{p} %" if lg == "fr" else f"{p}%", [(f"{w} %" if lg == "fr" else f"{w}%") for w in wr if w != p and 0 < w <= 100],
                 f"{part} ÷ {tot} × 100 = {p}.")


def ratio_share(rng, d, g, lg):
    a, b = rng.sample(range(1, 7), 2)
    unit = rng.choice([100, 200, 250, 500, 1000, 2000])
    tot = (a + b) * unit
    nm1, nm2 = rng.sample(P.__dict__["NAMES"] if False else ["Awa", "Kofi", "Mbarga", "Fanta", "Ngono", "Bih", "Hawa", "Brice"], 2)
    t = ask(lg, f"{nm1} et {nm2} se partagent {money(tot, lg)} en parts proportionnelles à {a} et {b} ({nm1} : {a} ; {nm2} : {b}). Combien reçoit {nm1}",
            f"{nm1} and {nm2} share {money(tot, lg)} in the ratio {a} : {b} ({nm1} : {a}, {nm2} : {b}). How much does {nm1} get")
    r = a * unit
    return Draft(q_end(lg, t), money(r, lg), [money(w, lg) for w in (b * unit, tot // 2, r + unit, max(r - unit, unit), tot // a if tot % a == 0 else r + 2 * unit, tot - unit) if w != r and w > 0],
                 f"{a} + {b} = {a + b} parts ; une part = {N(unit, lg)} FCFA ; {a} × {N(unit, lg)} = {N(r, lg)} FCFA." if lg == "fr" else f"{a} + {b} = {a + b} parts; one part = {N(unit, lg)} FCFA; {a} × {N(unit, lg)} = {N(r, lg)} FCFA.")


def speed(rng, d, g, lg):
    kind = rng.choice(["v", "d", "t"])
    v = rng.choice([30, 40, 45, 50, 60, 70, 80, 90, 100, 120])
    t = rng.choice([1, 2, 3, 4, 5, 6])
    dist = v * t
    if kind == "v":
        q = ask(lg, f"Un car parcourt {dist} km en {t} h à vitesse constante. Quelle est sa vitesse moyenne", f"A bus travels {dist} km in {t} h at constant speed. What is its average speed")
        right, ws, e = f"{v} km/h", [dist + t, dist * t, v + 10, v - 10 if v > 10 else v + 20, t], f"{dist} ÷ {t} = {v} km/h."
        wr = [f"{w} km/h" for w in ws]
    elif kind == "d":
        q = ask(lg, f"Un car roule à {v} km/h pendant {t} h. Quelle distance parcourt-il", f"A bus travels at {v} km/h for {t} h. What distance does it cover")
        right, ws, e = f"{dist} km", [v + t, v // t if v % t == 0 else v + 20, dist + v, dist - v if dist > v else dist + 20, dist * 2], f"{v} × {t} = {dist} km."
        wr = [f"{w} km" for w in ws]
    else:
        q = ask(lg, f"Un car roule à {v} km/h. Combien de temps met-il pour parcourir {dist} km", f"A bus travels at {v} km/h. How long does it take to cover {dist} km")
        right, ws, e = f"{t} h", [t + 1, t + 2, max(t - 1, 1) if t > 1 else 7, dist // 10 if dist >= 10 else 8, v], f"{dist} ÷ {v} = {t} h."
        wr = [f"{w} h" for w in ws]
    return Draft(q_end(lg, q), right, [w for w in wr if w != right], e)


def scale_q(rng, d, g, lg):
    sc = rng.choice([100, 200, 500, 1000, 2000, 5000, 10000, 25000, 50000, 100000])
    cm = rng.randint(2, 30)
    real_cm = cm * sc
    if real_cm >= 100000:
        real, unit = real_cm / 100000, "km"
        val = N(round(real, 3), lg, 3)
    elif real_cm >= 100:
        real, unit = real_cm / 100, "m"
        val = N(round(real, 3), lg, 3)
    else:
        return None
    ws = [real * 10, real / 10, real * 100, real / 100, real + cm]
    t = ask(lg, f"Sur une carte à l'échelle 1/{N(sc, lg)}, deux villes sont distantes de {cm} cm. Quelle est la distance réelle", f"On a map with scale 1/{N(sc, lg)}, two towns are {cm} cm apart. What is the real distance")
    return Draft(q_end(lg, t), f"{val} {unit}", [f"{N(round(w, 3), lg, 3)} {unit}" for w in ws if abs(w - real) > 1e-9], f"{cm} × {N(sc, lg)} = {N(real_cm, lg)} cm = {val} {unit}." if lg == "fr" else f"{cm} × {N(sc, lg)} = {N(real_cm, lg)} cm = {val} {unit}.")


# ------------------------------------------------------------------------------------------------ fractions
def frac_diff_den(rng, d, g, lg):
    d1, d2 = rng.choice([(2, 3), (2, 4), (3, 6), (4, 6), (2, 5), (3, 4), (4, 8), (5, 10), (2, 6), (3, 9), (6, 12), (4, 12), (5, 4), (3, 5)])
    a, b = rng.randint(1, d1 - 1 if d1 > 1 else 1), rng.randint(1, d2 - 1 if d2 > 1 else 1)
    plus = rng.random() < 0.65
    f1, f2 = Fraction(a, d1), Fraction(b, d2)
    if not plus and f1 <= f2:
        f1, f2 = f2, f1
        a, d1, b, d2 = f1.numerator, f1.denominator, f2.numerator, f2.denominator
        a, d1, b, d2 = a, d1, b, d2
    r = f1 + f2 if plus else f1 - f2
    if r == 0 or r.denominator == 1:
        return None
    right = fr_s(r)
    wr = [f"{a + b}/{d1 + d2}", f"{abs(a - b)}/{d1 + d2}" if not plus else f"{a + b}/{d1 * d2}", fr_s(r + Fraction(1, 12)), fr_s(abs(r - Fraction(1, d1 * d2))) if r - Fraction(1, d1 * d2) != 0 else "1/7", fr_s(f1 * f2) if f1 * f2 != r else "5/7", fr_s(r * 2)]
    t = ask(lg, f"Combien font {a}/{d1} {'+' if plus else '−'} {b}/{d2} (en fraction irréductible)", f"What is {a}/{d1} {'+' if plus else '−'} {b}/{d2} (as a fraction in lowest terms)")
    return Draft(q_end(lg, t), right, [w for w in wr if w != right and not w.startswith("0/") and not w.endswith("/0")],
                 ask(lg, f"Dénominateur commun {math.lcm(d1, d2)} : {a * (math.lcm(d1, d2) // d1)}/{math.lcm(d1, d2)} {'+' if plus else '−'} {b * (math.lcm(d1, d2) // d2)}/{math.lcm(d1, d2)} = {right}.",
                     f"Common denominator {math.lcm(d1, d2)}: {a * (math.lcm(d1, d2) // d1)}/{math.lcm(d1, d2)} {'+' if plus else '−'} {b * (math.lcm(d1, d2) // d2)}/{math.lcm(d1, d2)} = {right}."))


def frac_mul(rng, d, g, lg):
    a, b = rng.randint(1, 6), rng.randint(2, 9)
    c, e = rng.randint(1, 6), rng.randint(2, 9)
    f = Fraction(a, b) * Fraction(c, e)
    if f.denominator == 1:
        return None
    right = fr_s(f)
    div = rng.random() < 0.4 and g >= 8 and c != e
    if div:
        f = Fraction(a, b) / Fraction(c, e)
        right = fr_s(f)
        if f.denominator == 1:
            return None
        wr = [fr_s(Fraction(a, b) * Fraction(c, e)), fr_s(Fraction(b, a) * Fraction(c, e)), f"{a // math.gcd(a, c) if False else a}/{b + e}", f"{a * e + 1}/{b * c}", fr_s(f + 1)]
        t = ask(lg, f"Combien font {a}/{b} ÷ {c}/{e} (en fraction irréductible)", f"What is {a}/{b} ÷ {c}/{e} (as a fraction in lowest terms)")
        ex = ask(lg, f"Diviser par {c}/{e}, c'est multiplier par {e}/{c} : {right}.", f"Dividing by {c}/{e} means multiplying by {e}/{c}: {right}.")
    else:
        wr = [f"{a + c}/{b + e}", f"{a * c}/{b + e}", fr_s(Fraction(a, b) / Fraction(c, e)), f"{a + c}/{b * e}", fr_s(f + Fraction(1, b * e))]
        t = ask(lg, f"Combien font {a}/{b} × {c}/{e} (en fraction irréductible)", f"What is {a}/{b} × {c}/{e} (as a fraction in lowest terms)")
        ex = ask(lg, f"On multiplie les numérateurs et les dénominateurs : {a * c}/{b * e}" + (f" = {right}." if f"{a * c}/{b * e}" != right else "."), f"Multiply numerators and denominators: {a * c}/{b * e}" + (f" = {right}." if f"{a * c}/{b * e}" != right else "."))
    return Draft(q_end(lg, t), right, [w for w in wr if w != right], ex)


def frac_compare2(rng, d, g, lg):
    ds = [2, 3, 4, 5, 6, 7, 8, 9, 10, 12]
    fs = set()
    while len(fs) < 4:
        dn = rng.choice(ds)
        fs.add(Fraction(rng.randint(1, dn - 1), dn))
    fs = sorted(fs)
    big = rng.random() < 0.5
    r = fs[-1] if big else fs[0]
    sh = fs[:]
    rng.shuffle(sh)
    t = ask(lg, f"Quelle est la plus {'grande' if big else 'petite'} de ces fractions : {' ; '.join(fr_s(f) for f in sh)}", f"Which is the {'greatest' if big else 'smallest'} of these fractions: {', '.join(fr_s(f) for f in sh)}")
    return Draft(q_end(lg, t), fr_s(r), [fr_s(f) for f in fs if f != r], ask(lg, "On réduit au même dénominateur (ou on passe en nombre décimal) avant de comparer.", "Write them with the same denominator (or as decimals) before comparing."))


# ------------------------------------------------------------------------------------------------ puissances
def power_val(rng, d, g, lg):
    base = rng.randint(2, 9 if d < 4 else 12)
    n = rng.randint(2, 4 if base > 5 else 5)
    r = base ** n
    t = ask(lg, f"Combien vaut {base}{sup(n)}", f"What is {base}{sup(n)}")
    return Draft(q_end(lg, t), N(r, lg), [N(w, lg) for w in (base * n, base ** (n - 1), base ** (n + 1), base + n, r + base, n ** base) if w != r], f"{base}{sup(n)} = " + " × ".join([str(base)] * n) + f" = {N(r, lg)}.")


def power_rule(rng, d, g, lg):
    base = rng.randint(2, 10)
    m, n = rng.randint(2, 7), rng.randint(2, 7)
    kind = rng.choice(["mul", "div", "pow"])
    if kind == "mul":
        r, expr = m + n, f"{base}{sup(m)} × {base}{sup(n)}"
        wr = [m * n, r + 1, r - 1, abs(m - n) or 1]
    elif kind == "div":
        if m <= n:
            m, n = n + 1, m
        r, expr = m - n, f"{base}{sup(m)} ÷ {base}{sup(n)}"
        wr = [m + n, m // n if m % n == 0 else r + 1, r + 1, r - 1 if r > 1 else r + 2]
    else:
        r, expr = m * n, f"({base}{sup(m)}){sup(n)}"
        wr = [m + n, m ** n if m ** n < 100 else m + n + 1, r + 1, r - 1]
    right = f"{base}{sup(r)}"
    return Draft(q_end(lg, ask(lg, f"Écrire {expr} sous la forme d'une seule puissance : laquelle est correcte", f"Write {expr} as a single power: which is correct")), right, [f"{base}{sup(w)}" for w in wr if w != r and w > 0],
                 ask(lg, f"On applique la règle des exposants : {expr} = {right}.", f"Using the laws of indices: {expr} = {right}."))


def ten_pow(rng, d, g, lg):
    n = rng.randint(2, 7)
    kind = rng.choice(["val", "sci"])
    if kind == "val":
        r = 10 ** n
        return Draft(q_end(lg, ask(lg, f"Combien vaut 10{sup(n)}", f"What is 10{sup(n)}")), N(r, lg), [N(w, lg) for w in (10 * n, 10 ** (n - 1), 10 ** (n + 1), n ** 10 if n < 4 else 10 ** n // 2) if w != r],
                     ask(lg, f"10{sup(n)} s'écrit 1 suivi de {n} zéros.", f"10{sup(n)} is 1 followed by {n} zeros."))
    mant = rng.choice([2, 3, 4, 5, 6, 7, 8, 9]) + rng.choice([0, 0.5, 0.25, 0.1, 0.7])
    x = round(mant * 10 ** n)
    right = f"{N(mant, lg)} × 10{sup(n)}"
    wr = [f"{N(mant, lg)} × 10{sup(n + 1)}", f"{N(mant, lg)} × 10{sup(n - 1)}", f"{N(mant * 10, lg)} × 10{sup(n)}", f"{N(mant / 10, lg)} × 10{sup(n + 1)}"]
    return Draft(q_end(lg, ask(lg, f"Quelle est l'écriture scientifique de {N(x, lg)}", f"What is {N(x, lg)} in standard form (scientific notation)")), right, [w for w in wr if w != right],
                 ask(lg, f"{N(x, lg)} = {right} (un seul chiffre non nul avant la virgule).", f"{N(x, lg)} = {right} (one non-zero digit before the point)."))


def square_root(rng, d, g, lg):
    n = rng.randint(2, 12 + 3 * d)
    kind = rng.choice(["sq", "rt"])
    if kind == "sq":
        return Draft(q_end(lg, ask(lg, f"Quel est le carré de {n}", f"What is the square of {n}")), N(n * n, lg), [N(w, lg) for w in (n * 2, n * n + n, n * n - n, n + n * 2, n * n + 1) if w != n * n],
                     f"{n}² = {n} × {n} = {N(n * n, lg)}.")
    return Draft(q_end(lg, ask(lg, f"Quelle est la racine carrée de {N(n * n, lg)}", f"What is the square root of {N(n * n, lg)}")), N(n, lg), [N(w, lg) for w in (n * n // 2, n + 1, n - 1, n * 2, n + 2) if w != n and w > 0],
                 f"{n} × {n} = {N(n * n, lg)}, donc √{N(n * n, lg)} = {n}." if lg == "fr" else f"{n} × {n} = {N(n * n, lg)}, so √{N(n * n, lg)} = {n}.")


# ------------------------------------------------------------------------------------------------ géométrie
TRIPLES = [(3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25), (20, 21, 29), (9, 40, 41), (12, 35, 37), (6, 8, 10), (9, 12, 15), (12, 16, 20), (15, 20, 25), (10, 24, 26), (15, 36, 39), (18, 24, 30)]


def pythagoras(rng, d, g, lg):
    a, b, c = rng.choice(TRIPLES)
    assert a * a + b * b == c * c
    if rng.random() < 0.5:
        t = ask(lg, f"Un triangle est rectangle ; ses côtés de l'angle droit mesurent {a} cm et {b} cm. Quelle est la longueur de l'hypoténuse", f"A right-angled triangle has perpendicular sides of {a} cm and {b} cm. What is the length of the hypotenuse")
        right, ws = c, [a + b, c + 1, c - 1, abs(b - a), a * b // 2 if a * b // 2 != c else c + 2]
        e = f"{a}² + {b}² = {a * a} + {b * b} = {c * c} = {c}², donc l'hypoténuse mesure {c} cm." if lg == "fr" else f"{a}² + {b}² = {a * a} + {b * b} = {c * c} = {c}², so the hypotenuse is {c} cm."
    else:
        t = ask(lg, f"Un triangle est rectangle ; son hypoténuse mesure {c} cm et un côté de l'angle droit {a} cm. Quelle est la longueur de l'autre côté de l'angle droit", f"A right-angled triangle has a hypotenuse of {c} cm and one perpendicular side of {a} cm. What is the length of the other perpendicular side")
        right, ws, e = b, [c - a, c + a, b + 1, b - 1, math.isqrt(c * c + a * a)], f"{c}² − {a}² = {c * c} − {a * a} = {b * b} = {b}², donc {b} cm." if lg == "fr" else f"{c}² − {a}² = {c * c} − {a * a} = {b * b} = {b}², so {b} cm."
    return Draft(q_end(lg, t), f"{right} cm", [f"{w} cm" for w in ws if w != right and w > 0], e)


def is_right(rng, d, g, lg):
    a, b, c = rng.choice(TRIPLES)
    k = rng.choice([1, 1, 2]) if False else 1
    yes = rng.random() < 0.5
    if not yes:
        c2 = c + rng.choice([1, 2, -1])
        sides = (a, b, c2)
    else:
        sides = (a, b, c)
    sh = list(sides)
    rng.shuffle(sh)
    right = "Oui" if yes else "Non"
    if lg == "en":
        right = "Yes" if yes else "No"
    s = ask(lg, f"Un triangle a pour côtés {sh[0]} cm, {sh[1]} cm et {sh[2]} cm. Est-il rectangle", f"A triangle has sides {sh[0]} cm, {sh[1]} cm and {sh[2]} cm. Is it right-angled")
    big = max(sides)
    sm = sorted(sides)
    ok = sm[0] ** 2 + sm[1] ** 2 == sm[2] ** 2
    assert ok == yes
    cho = ["Oui", "Non", "Seulement si on mesure les angles", "On ne peut pas savoir"] if lg == "fr" else ["Yes", "No", "Only if the angles are measured", "It is impossible to know"]
    return Draft(q_end(lg, s), right, [x for x in cho if x != right], f"{sm[0]}² + {sm[1]}² = {sm[0] ** 2 + sm[1] ** 2} et {sm[2]}² = {sm[2] ** 2} : " + ("égaux, donc rectangle (réciproque de Pythagore)." if yes else "différents, donc pas rectangle.") if lg == "fr" else
                 f"{sm[0]}² + {sm[1]}² = {sm[0] ** 2 + sm[1] ** 2} and {sm[2]}² = {sm[2] ** 2}: " + ("equal, so it is right-angled (converse of Pythagoras)." if yes else "not equal, so it is not right-angled."))


def angle_comp(rng, d, g, lg):
    a = rng.randint(10, 80)
    kind = rng.choice(["comp", "supp", "tri", "iso"] + (["quad"] if g >= 8 else []))
    if kind == "comp":
        t = ask(lg, f"Quel est le complémentaire d'un angle de {a}°", f"What is the complement of an angle of {a}°")
        r, e = 90 - a, f"90 − {a} = {90 - a}."
        ws = [180 - a, 90 + a, r + 10, r - 10, a]
    elif kind == "supp":
        a = rng.randint(20, 160)
        t = ask(lg, f"Quel est le supplémentaire d'un angle de {a}°", f"What is the supplement of an angle of {a}°")
        r, e = 180 - a, f"180 − {a} = {180 - a}."
        ws = [90 - a if a < 90 else a - 90, 360 - a, r + 10, r - 10, 90 + a if a + 90 < 180 else a]
    elif kind == "tri":
        b = rng.randint(20, 90)
        c = 180 - a - b
        if c <= 5:
            return None
        t = ask(lg, f"Dans un triangle, deux angles mesurent {a}° et {b}°. Combien mesure le troisième", f"Two angles of a triangle measure {a}° and {b}°. How many degrees is the third angle")
        r, e = c, f"180 − ({a} + {b}) = {c}."
        ws = [360 - a - b, 90 - a, c + 10, c - 10, a + b]
    elif kind == "iso":
        a = rng.randint(20, 80)
        t = ask(lg, f"Un triangle isocèle a un angle au sommet de {2 * a}°. Combien mesure chaque angle de la base", f"An isosceles triangle has an apex angle of {2 * a}°. How many degrees is each base angle")
        r, e = (180 - 2 * a) // 2, f"(180 − {2 * a}) ÷ 2 = {(180 - 2 * a) // 2}."
        if (180 - 2 * a) % 2:
            return None
        ws = [180 - 2 * a, 90 - 2 * a if 90 - 2 * a > 0 else 5, r + 10, r - 10, a + 45 if a + 45 != r else 33]
    else:
        b, c = rng.randint(60, 120), rng.randint(60, 120)
        dd = 360 - a - b - c
        if dd <= 10 or dd >= 170:
            return None
        t = ask(lg, f"Dans un quadrilatère, trois angles mesurent {a}°, {b}° et {c}°. Combien mesure le quatrième", f"Three angles of a quadrilateral measure {a}°, {b}° and {c}°. How many degrees is the fourth angle")
        r, e = dd, f"360 − ({a} + {b} + {c}) = {dd}."
        ws = [180 - a - b - c if 180 - a - b - c > 0 else 20, dd + 10, dd - 10, 180 - dd, 360 - a - b]
    return Draft(q_end(lg, t), f"{r}°", [f"{w}°" for w in ws if w != r and w > 0], e)


def polygon_angles(rng, d, g, lg):
    n = rng.choice([5, 6, 7, 8, 9, 10, 12])
    r = (n - 2) * 180
    nm = {5: ("un pentagone", "a pentagon"), 6: ("un hexagone", "a hexagon"), 7: ("un heptagone", "a heptagon"), 8: ("un octogone", "an octagon"), 9: ("un ennéagone", "a nonagon"), 10: ("un décagone", "a decagon"), 12: ("un dodécagone", "a dodecagon")}[n]
    t = ask(lg, f"Quelle est la somme des angles intérieurs d'{nm[0]}", f"What is the sum of the interior angles of {nm[1]}")
    return Draft(q_end(lg, t), f"{r}°", [f"{w}°" for w in (n * 180, (n - 1) * 180, (n - 3) * 180, 360, r + 180, r - 90) if w != r and w > 0], f"({n} − 2) × 180 = {r}.")


def circle_q(rng, d, g, lg):
    rr = rng.choice([1, 2, 3, 5, 7, 10, 14, 20, 21, 25, 35, 50])
    kind = rng.choice(["circ", "area"] if g >= 8 else ["circ"])
    diam = rng.random() < 0.4
    sh = (f"de diamètre {2 * rr} cm" if lg == "fr" else f"with a diameter of {2 * rr} cm") if diam else (f"de rayon {rr} cm" if lg == "fr" else f"with a radius of {rr} cm")
    if kind == "circ":
        v = round(2 * 3.14 * rr, 2)
        ws = [round(3.14 * rr, 2), round(3.14 * rr * rr, 2), round(2 * 3.14 * rr * rr, 2), round(3.14 * 2 * rr * 2, 2), v + 3.14, round(v * 2, 2)]
        t = ask(lg, f"Quel est le périmètre d'un cercle {sh} (π ≈ 3,14)", f"What is the circumference of a circle {sh} (π ≈ 3.14)")
        e = f"2 × 3,14 × {rr} = {N(v, lg, 2)} cm." if lg == "fr" else f"2 × 3.14 × {rr} = {N(v, lg, 2)} cm."
        unit = "cm"
    else:
        v = round(3.14 * rr * rr, 2)
        ws = [round(2 * 3.14 * rr, 2), round(3.14 * rr, 2), round(3.14 * 2 * rr * rr, 2), round(3.14 * (2 * rr) ** 2, 2), v + 3.14, round(rr * rr, 2)]
        t = ask(lg, f"Quelle est l'aire d'un disque {sh} (π ≈ 3,14)", f"What is the area of a circle {sh} (π ≈ 3.14)")
        e = f"3,14 × {rr} × {rr} = {N(v, lg, 2)} cm²." if lg == "fr" else f"3.14 × {rr} × {rr} = {N(v, lg, 2)} cm²."
        unit = "cm²"
    return Draft(q_end(lg, t), f"{N(v, lg, 2)} {unit}", [f"{N(w, lg, 2)} {unit}" for w in ws if abs(w - v) > 1e-9 and w > 0], e)


def volume_q(rng, d, g, lg):
    kind = rng.choice(["cube", "pave", "litre"])
    if kind == "cube":
        a = rng.randint(2, 12)
        v = a ** 3
        t = ask(lg, f"Quel est le volume d'un cube d'arête {a} cm", f"What is the volume of a cube of edge {a} cm")
        ws = [3 * a, 6 * a * a, a * a, 3 * a * a, v + a]
        e = f"{a} × {a} × {a} = {v} cm³."
    elif kind == "pave":
        a, b, c = rng.randint(2, 12), rng.randint(2, 12), rng.randint(2, 12)
        v = a * b * c
        t = ask(lg, f"Quel est le volume d'un pavé droit de {a} cm de long, {b} cm de large et {c} cm de haut", f"What is the volume of a cuboid {a} cm long, {b} cm wide and {c} cm high")
        ws = [a + b + c, 2 * (a * b + b * c + a * c), a * b, v + a, v - c]
        e = f"{a} × {b} × {c} = {v} cm³."
    else:
        k = rng.randint(2, 40)
        v = k
        t = ask(lg, f"Combien de litres contient un récipient de {k} dm³", f"How many litres does a container of {k} dm³ hold")
        ws = [k * 10, k * 100, k * 1000, k // 10 if k % 10 == 0 else k + 5, k + 1]
        e = ask(lg, f"1 dm³ = 1 L donc {k} dm³ = {k} L.", f"1 dm³ = 1 L so {k} dm³ = {k} L.")
        return Draft(q_end(lg, t), f"{v} L", [f"{w} L" for w in ws if w != v and w > 0], e)
    return Draft(q_end(lg, t), f"{v} cm³", [f"{w} cm³" for w in ws if w != v and w > 0], e)


def thales(rng, d, g, lg):
    k = rng.choice([2, 3, 4, 5, Fraction(3, 2), Fraction(5, 2)])
    ad = rng.randint(2, 6)
    ab = ad * k if Fraction(ad * k).denominator == 1 else None
    if ab is None:
        ad, ab = ad * 2, int(Fraction(ad * 2) * k)
    ab = int(ab)
    ac = rng.randint(2, 7) * (1 if k == int(k) else 2)
    ae = ac * ab / ad
    if ae != int(ae):
        return None
    ae = int(ae)
    t = ask(lg, f"Dans un triangle ABC, D est sur [AB] et E sur [AC] avec (DE) // (BC). On a AB = {ab} cm, AD = {ad} cm et AC = {ae} cm. Combien mesure AE", f"In triangle ABC, D is on [AB] and E on [AC] with (DE) // (BC). AB = {ab} cm, AD = {ad} cm and AC = {ae} cm. What is AE")
    r = ae * ad // ab if (ae * ad) % ab == 0 else None
    if r is None:
        return None
    return Draft(q_end(lg, t), f"{r} cm", [f"{w} cm" for w in (ae - r, r + 1, r - 1 if r > 1 else r + 2, ae * ab // ad if (ae * ab) % ad == 0 else r + 3, ab - ad) if w != r and w > 0],
                 f"AE/AC = AD/AB : AE = {ae} × {ad} ÷ {ab} = {r} cm." if lg == "fr" else f"AE/AC = AD/AB: AE = {ae} × {ad} ÷ {ab} = {r} cm.")


# ------------------------------------------------------------------------------------------------ statistiques, fonctions, coordonnées
def median_q(rng, d, g, lg):
    n = rng.choice([5, 7, 9, 6, 8] if d > 2 else [5, 7, 3])
    vals = [rng.randint(1, 20) for _ in range(n)]
    sv = sorted(vals)
    med = Fraction(sv[n // 2]) if n % 2 else Fraction(sv[n // 2 - 1] + sv[n // 2], 2)
    right = num(med).replace("/", ",5") if False else (N(float(med), lg) if med.denominator != 1 else N(int(med), lg))
    wr = [N(int(v) if float(v).is_integer() else float(v), lg) for v in (sum(vals) / n, sv[0] + 1, sv[-1] - 1, sv[n // 2 - 1], sv[n // 2 + (1 if n > 2 else 0)], sv[n // 2] + 1) if v != med]
    wr = [w for w in wr if w != right]
    mean_ = round(sum(vals) / n, 2)
    wr = [N(float(mean_), lg)] + wr
    t = ask(lg, f"Quelle est la médiane de cette série : {' ; '.join(map(str, vals))}", f"What is the median of this data set: {', '.join(map(str, vals))}")
    return Draft(q_end(lg, t), right, wr, ask(lg, f"On range les valeurs : {' ; '.join(map(str, sv))} ; la médiane est {right}.", f"Put the values in order: {', '.join(map(str, sv))}; the median is {right}."))


def mode_q(rng, d, g, lg):
    m = rng.randint(1, 9)
    others = rng.sample([v for v in range(1, 15) if v != m], 4)
    vals = [m] * 3 + [o for o in others for _ in range(rng.choice([1, 2]))]
    from collections import Counter
    cnt = Counter(vals)
    if sorted(cnt.values())[-1] == sorted(cnt.values())[-2]:
        return None
    rng.shuffle(vals)
    t = ask(lg, f"Quel est le mode de cette série : {' ; '.join(map(str, vals))}", f"What is the mode of this data set: {', '.join(map(str, vals))}")
    return Draft(q_end(lg, t), str(m), [str(o) for o in others] + [str(sum(vals) // len(vals))], ask(lg, f"{m} apparaît {cnt[m]} fois : c'est la valeur la plus fréquente.", f"{m} appears {cnt[m]} times: it is the most frequent value."))


def range_q(rng, d, g, lg):
    vals = [rng.randint(2, 40) for _ in range(rng.choice([5, 6, 7]))]
    r = max(vals) - min(vals)
    t = ask(lg, f"Quelle est l'étendue de cette série : {' ; '.join(map(str, vals))}", f"What is the range of this data set: {', '.join(map(str, vals))}")
    return Draft(q_end(lg, t), str(r), [str(w) for w in (max(vals), min(vals), max(vals) + min(vals), r + 1, r - 1 if r > 1 else r + 3, sum(vals) // len(vals)) if w != r and w > 0], f"{max(vals)} − {min(vals)} = {r}.")


def linear_fn(rng, d, g, lg):
    a, b = rng.randint(-6, 8), rng.randint(-9, 9)
    if a == 0:
        return None
    x = rng.randint(-4, 6)
    fx = a * x + b
    f = poly([a, b]) if b else poly([a, 0])
    fexpr = f
    wr = [a * x - b, a + x + b, a * (x + b), fx + 1, -fx, a * abs(x) + b, fx - 2]
    t = ask(lg, f"Soit f(x) = {fexpr}. Combien vaut f({sg(x)})", f"Let f(x) = {fexpr}. What is f({sg(x)})")
    return Draft(q_end(lg, t), sg(fx), [sg(w) for w in wr if w != fx], f"f({sg(x)}) = {a} × {sgp(x)} {'+' if b >= 0 else '−'} {abs(b)} = {sg(fx)}.")


def slope(rng, d, g, lg):
    a = rng.choice([-4, -3, -2, -1, 1, 2, 3, 4, 5])
    b = rng.randint(-5, 5)
    x1, x2 = rng.randint(-4, 2), rng.randint(3, 7)
    y1, y2 = a * x1 + b, a * x2 + b
    t = ask(lg, f"Une droite passe par A({sg(x1)} ; {sg(y1)}) et B({sg(x2)} ; {sg(y2)}). Quel est son coefficient directeur", f"A line passes through A({sg(x1)}, {sg(y1)}) and B({sg(x2)}, {sg(y2)}). What is its gradient (slope)")
    return Draft(q_end(lg, t), sg(a), [sg(w) for w in (-a, a + 1, a - 1, Fraction(1, a) if False else a * 2, y2 - y1, x2 - x1) if w != a], f"({sg(y2)} − {sgp(y1)}) ÷ ({sg(x2)} − {sgp(x1)}) = {sg(y2 - y1)} ÷ {x2 - x1} = {sg(a)}.")


def midpoint(rng, d, g, lg):
    x1, y1 = rng.randint(-8, 8), rng.randint(-8, 8)
    x2, y2 = x1 + 2 * rng.randint(-5, 5), y1 + 2 * rng.randint(-5, 5)
    if (x1, y1) == (x2, y2):
        return None
    mx, my = (x1 + x2) // 2, (y1 + y2) // 2

    def pt(x, y):
        return f"({sg(x)} ; {sg(y)})" if lg == "fr" else f"({sg(x)}, {sg(y)})"
    ws = [pt(x2 - x1, y2 - y1), pt(x1 + x2, y1 + y2), pt(my, mx), pt(mx + 1, my), pt(mx, my - 1)]
    t = ask(lg, f"Quelles sont les coordonnées du milieu de [AB] avec A{pt(x1, y1)} et B{pt(x2, y2)}", f"What are the coordinates of the midpoint of [AB] with A{pt(x1, y1)} and B{pt(x2, y2)}")
    return Draft(q_end(lg, t), pt(mx, my), [w for w in ws if w != pt(mx, my)], f"(({sg(x1)} + {sgp(x2)}) ÷ 2 ; ({sg(y1)} + {sgp(y2)}) ÷ 2) = {pt(mx, my)}.")


def probability(rng, d, g, lg):
    red, blue, green = rng.randint(1, 8), rng.randint(1, 8), rng.randint(0, 6)
    tot = red + blue + green
    f = Fraction(red, tot)
    t = ask(lg, f"Un sac contient {red} boules rouges, {blue} boules bleues et {green} boules vertes. On tire une boule au hasard. Quelle est la probabilité qu'elle soit rouge" if green else
            f"Un sac contient {red} boules rouges et {blue} boules bleues. On tire une boule au hasard. Quelle est la probabilité qu'elle soit rouge",
            f"A bag contains {red} red balls, {blue} blue balls and {green} green balls. One ball is picked at random. What is the probability that it is red?".rstrip("?") if green else
            f"A bag contains {red} red balls and {blue} blue balls. One ball is picked at random. What is the probability that it is red?".rstrip("?"))
    ws = [Fraction(red, blue + (green or 0) or 1), Fraction(blue, tot), Fraction(1, tot), Fraction(red, tot + 1), Fraction(tot - red, tot) if tot - red != red else Fraction(red + 1, tot)]
    right = fr_s(f)
    return Draft(q_end(lg, t), right, [fr_s(w) for w in ws if w != f and fr_s(w) != right and w < 1 and w > 0],
                 ask(lg, f"{red} cas favorables sur {tot} cas possibles : {right}.", f"{red} favourable outcomes out of {tot}: {right}."))


def reg_all():
    # primary-type models extended to the first cycle
    sec = P.PRIM[:0] + SEC
    reg("add", P.add, SEC, 70)
    reg("sub", P.sub, SEC, 70)
    reg("mul", P.mul, SEC, 110)
    reg("div", P.div, SEC, 110)
    reg("dec-compare", P.dec_compare, SEC, 140, cat="Nombres décimaux")
    reg("dec-add", P.dec_add, SEC, 150, cat="Nombres décimaux")
    reg("times10", P.times10, SEC, 150, cat="Nombres décimaux")
    reg("rounding", P.rounding, SEC, 150, cat="Nombres décimaux")
    reg("fraction-of", P.fraction_of, SEC, 150, cat="Fractions")
    reg("simplify", P.simplify, SEC, 100, cat="Fractions")
    reg("same-den", P.same_den, SEC, 100, cat="Fractions")
    reg("units", P.units, SEC, 130, cat="Mesures")
    reg("perimeter", P.perimeter, SEC, 140, cat="Géométrie")
    reg("area", P.area, SEC, 150, cat="Géométrie")
    reg("euclid", P.euclid, SEC, 100)
    reg("order-ops", P.order_ops, SEC, 160)
    reg("mean", P.mean, SEC, 160, cat="Statistiques")
    reg("prop-simple", P.prop_simple, SEC, 140, cat="Proportionnalité")
    reg("three-step", P.three_step, SEC, 160, cat="Problèmes")
    reg("story-share", P.story_share, SEC, 100, cat="Problèmes")
    # specific models
    reg("rel-op", rel_op, S78, 220, cat="Nombres relatifs")
    reg("rel-compare", rel_compare, S78, 140, cat="Nombres relatifs")
    reg("rel-opposite", rel_opposite, S78, 110, cat="Nombres relatifs")
    reg("temp-rel", temp_rel, S78, 160, cat="Nombres relatifs")
    reg("eq1", eq1, SEC, 230, cat="Algèbre")
    reg("reduce-expr", reduce_expr, S78, 190, cat="Algèbre")
    reg("expand", expand, S9, 170, cat="Algèbre")
    reg("evaluate", evaluate, S78, 200, cat="Algèbre")
    reg("problem-eq", problem_eq, SEC, 200, cat="Algèbre")
    reg("prime", prime_q, SEC, 130, cat="Arithmétique")
    reg("composite", composite_q, SEC, 130, cat="Arithmétique")
    reg("divisible", divisible, SEC, 160, cat="Arithmétique")
    reg("gcd", gcd_q, SEC, 170, cat="Arithmétique")
    reg("lcm", lcm_q, SEC, 120, cat="Arithmétique")
    reg("divisors", divisors_count, SEC, 80, cat="Arithmétique")
    reg("percent-of", percent_of, SEC, 160, cat="Pourcentages")
    reg("discount", discount, SEC, 200, cat="Pourcentages")
    reg("percent-what", percent_what, SEC, 120, cat="Pourcentages")
    reg("ratio-share", ratio_share, SEC, 170, cat="Proportionnalité")
    reg("speed", speed, S78, 160, cat="Proportionnalité")
    reg("scale", scale_q, S78, 120, cat="Proportionnalité")
    reg("frac-diff-den", frac_diff_den, SEC, 200, cat="Fractions")
    reg("frac-mul", frac_mul, S78, 200, cat="Fractions")
    reg("frac-compare", frac_compare2, SEC, 120, cat="Fractions")
    reg("power-val", power_val, S78, 130, cat="Algèbre")
    reg("power-rule", power_rule, S9, 160, cat="Algèbre")
    reg("ten-pow", ten_pow, S9, 150, cat="Algèbre")
    reg("square-root", square_root, S78, 120, cat="Calcul")
    reg("pythagoras", pythagoras, S9, 160, cat="Géométrie")
    reg("is-right", is_right, S9, 80, cat="Géométrie")
    reg("angle", angle_comp, SEC, 200, cat="Géométrie")
    reg("polygon-angles", polygon_angles, S78, 60, cat="Géométrie")
    reg("circle", circle_q, SEC, 160, cat="Géométrie")
    reg("volume", volume_q, SEC, 160, cat="Géométrie")
    reg("thales", thales, S9, 140, cat="Géométrie")
    reg("median", median_q, S78, 200, cat="Statistiques")
    reg("mode", mode_q, S78, 120, cat="Statistiques")
    reg("range", range_q, S78, 100, cat="Statistiques")
    reg("linear-fn", linear_fn, S9, 160, cat="Algèbre")
    reg("slope", slope, S9, 120, cat="Algèbre")
    reg("midpoint", midpoint, S9, 140, cat="Géométrie")
    reg("probability", probability, S9, 150, cat="Statistiques")


reg_all()
