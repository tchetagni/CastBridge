"""CM2 (primary): arithmetic, fractions, decimals, measures, money in FCFA, time, number writing.
Every answer is computed; distractors are typical slips (carry forgotten, wrong operation, digit swap, power of ten).
Programme: MINEDUB, cycle moyen (calcul, mesures, géométrie, problèmes)."""
import math

from ..core import NB, fcfa, fr, gen, near_floats, near_ints, Draft

SRC = "Programme MINEDUB CM2 (calcul, mesures, problèmes) - réponse calculée"
C = "cm2"


def ints(rng, right, n=8, **kw):
    return [fr(v) for v in near_ints(rng, right, n, **kw)]


def words_fr(n, final=True):
    U = ["zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze",
         "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf"]
    T = ["", "", "vingt", "trente", "quarante", "cinquante", "soixante"]

    def b100(m):
        if m < 20:
            return U[m]
        if m < 70:
            t, u = divmod(m, 10)
            return T[t] + ("" if u == 0 else " et un" if u == 1 else "-" + U[u])
        if m < 80:
            r = m - 60
            return "soixante et onze" if r == 11 else "soixante-" + U[r]
        r = m - 80
        return ("quatre-vingts" if final else "quatre-vingt") if r == 0 else "quatre-vingt-" + U[r]

    if n < 100:
        return b100(n)
    if n < 1000:
        h, r = divmod(n, 100)
        s = "cent" if h == 1 else U[h] + " cent" + ("s" if r == 0 and final else "")
        return s + ("" if r == 0 else " " + words_fr(r, final))
    th, r = divmod(n, 1000)
    s = "mille" if th == 1 else words_fr(th, False) + " mille"
    return s + ("" if r == 0 else " " + words_fr(r, final))


def roman(n):
    out = ""
    for v, s in ((1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"), (50, "L"), (40, "XL"), (10, "X"),
                 (9, "IX"), (5, "V"), (4, "IV"), (1, "I")):
        while n >= v:
            out += s
            n -= v
    return out


def rnd(rng, digits):
    return rng.randint(10 ** (digits - 1), 10 ** digits - 1)


@gen(C, "cm2-add", cap=260, cat="Calcul")
def add(rng, d):
    a, b = {1: (rnd(rng, 2), rnd(rng, 2)), 2: (rnd(rng, 3), rnd(rng, 3)), 3: (rnd(rng, 4), rnd(rng, 3)),
            4: (rnd(rng, 5), rnd(rng, 4)), 5: (rnd(rng, 6), rnd(rng, 5))}[d]
    r = a + b
    return Draft(f"Combien font {fr(a)} + {fr(b)} ?", fr(r), ints(rng, r, lo=0), f"{fr(a)} + {fr(b)} = {fr(r)} (addition posée, avec les retenues).", src=SRC)


@gen(C, "cm2-sub", cap=260, cat="Calcul")
def sub(rng, d):
    a, b = {1: (rnd(rng, 2), rnd(rng, 2)), 2: (rnd(rng, 3), rnd(rng, 3)), 3: (rnd(rng, 4), rnd(rng, 3)),
            4: (rnd(rng, 5), rnd(rng, 4)), 5: (rnd(rng, 6), rnd(rng, 5))}[d]
    a, b = max(a, b), min(a, b)
    r = a - b
    if r == 0:
        return None
    return Draft(f"Combien font {fr(a)} − {fr(b)} ?", fr(r), ints(rng, r, lo=0), f"{fr(a)} − {fr(b)} = {fr(r)} (soustraction posée, avec les retenues).", src=SRC)


@gen(C, "cm2-mul", cap=260, cat="Calcul")
def mul(rng, d):
    a, b = {1: (rng.randint(2, 9), rng.randint(2, 9)), 2: (rnd(rng, 2), rng.randint(2, 9)), 3: (rnd(rng, 2), rnd(rng, 2)),
            4: (rnd(rng, 3), rnd(rng, 2)), 5: (rnd(rng, 3), rnd(rng, 3))}[d]
    r = a * b
    wrongs = ints(rng, r, lo=0) + [fr(a + b), fr(a * (b + 1))]
    return Draft(f"Combien font {fr(a)} × {fr(b)} ?", fr(r), wrongs, f"{fr(a)} × {fr(b)} = {fr(r)}.", src=SRC)


@gen(C, "cm2-div", cap=220, cat="Calcul")
def div(rng, d):
    q = {1: rng.randint(2, 9), 2: rng.randint(6, 19), 3: rng.randint(12, 60), 4: rng.randint(40, 200), 5: rng.randint(100, 900)}[d]
    b = {1: rng.randint(2, 9), 2: rng.randint(2, 9), 3: rng.randint(3, 12), 4: rng.randint(6, 15), 5: rng.randint(11, 25)}[d]
    a = q * b
    return Draft(f"Combien font {fr(a)} ÷ {fr(b)} ?", fr(q), ints(rng, q, lo=1) + [fr(b)], f"{fr(a)} ÷ {fr(b)} = {fr(q)} car {fr(q)} × {fr(b)} = {fr(a)}.", src=SRC)


@gen(C, "cm2-euclid-rest", cap=200, cat="Calcul")
def euclid_rest(rng, d):
    b = rng.randint(3, 9 + 3 * d)
    q = rng.randint(3, 10 * d + 5)
    r = rng.randint(1, b - 1)
    a = q * b + r
    return Draft(f"Quel est le reste de la division euclidienne de {fr(a)} par {fr(b)} ?", fr(r),
                 [fr(x) for x in range(0, b) if x != r][:6] + [fr(q)],
                 f"{fr(a)} = {fr(b)} × {fr(q)} + {fr(r)} avec {fr(r)} < {fr(b)} : le reste est {fr(r)}.", src=SRC)


@gen(C, "cm2-euclid-quot", cap=200, cat="Calcul")
def euclid_quot(rng, d):
    b = rng.randint(3, 9 + 3 * d)
    q = rng.randint(3, 10 * d + 5)
    r = rng.randint(1, b - 1)
    a = q * b + r
    return Draft(f"Quel est le quotient entier de la division de {fr(a)} par {fr(b)} ?", fr(q), ints(rng, q, lo=0) + [fr(r)],
                 f"{fr(a)} = {fr(b)} × {fr(q)} + {fr(r)} : le quotient est {fr(q)}.", src=SRC)


ITEMS = [("un cahier", "cahiers", 250), ("un stylo", "stylos", 100), ("un pain", "pains", 150), ("un sachet d'eau", "sachets d'eau", 50),
         ("une boîte de craies", "boîtes de craies", 500), ("un ballon", "ballons", 2500), ("un kilo de riz", "kilos de riz", 600),
         ("un kilo d'oignons", "kilos d'oignons", 500), ("un régime de plantains", "régimes de plantains", 2000),
         ("une bouteille de jus", "bouteilles de jus", 400), ("un beignet", "beignets", 50), ("un cahier de dessin", "cahiers de dessin", 450),
         ("un sac d'arachides", "sacs d'arachides", 1500), ("une règle", "règles", 200), ("un kilo de tomates", "kilos de tomates", 800)]
NAMES = ["Awa", "Kofi", "Mbarga", "Fanta", "Ngono", "Tchoumi", "Amina", "Junior", "Nkolo", "Bih", "Yannick", "Hawa", "Essomba", "Mireille"]


@gen(C, "cm2-fcfa-total", cap=240, cat="Problèmes")
def fcfa_total(rng, d):
    name, (item, plural, price) = rng.choice(NAMES), rng.choice(ITEMS)
    n = rng.randint(2, 4 + 2 * d)
    t = price * n
    return Draft(f"{name} achète {n} {plural} à {fcfa(price)} l'unité. Combien cela coûte-t-il en tout ?", fcfa(t),
                 [fcfa(v) for v in near_ints(rng, t, 8, lo=0)] + [fcfa(price + n)], f"{n} × {fr(price)} = {fr(t)} : {fcfa(t)}.", src=SRC)


@gen(C, "cm2-fcfa-change", cap=240, cat="Problèmes")
def fcfa_change(rng, d):
    name, (item, plural, price) = rng.choice(NAMES), rng.choice(ITEMS)
    n = rng.randint(1, 2 + d)
    total = price * n
    bill = next((b for b in (500, 1000, 2000, 5000, 10000, 20000) if b > total), None)
    if bill is None:
        return None
    r = bill - total
    what = item if n == 1 else f"{n} {plural}"
    return Draft(f"{name} achète {what} pour {fcfa(total)} en payant avec {fcfa(bill)}. Combien lui rend-on ?", fcfa(r),
                 [fcfa(v) for v in near_ints(rng, r, 8, lo=1)] + [fcfa(bill + total)],
                 f"{fr(bill)} − {fr(total)} = {fr(r)} : on rend {fcfa(r)}.", src=SRC)


@gen(C, "cm2-two-step", cap=220, cat="Problèmes")
def two_step(rng, d):
    name = rng.choice(NAMES)
    (i1, pl1, p1), (i2, pl2, p2) = rng.sample(ITEMS, 2)
    n1, n2 = rng.randint(1, 3 + d), rng.randint(1, 3 + d)
    spent = n1 * p1 + n2 * p2
    start = (spent // 1000 + 1) * 1000 + rng.choice([0, 500, 1000])
    left = start - spent
    return Draft(f"{name} a {fcfa(start)}. On lui vend {n1} {pl1} à {fcfa(p1)} l'unité et {n2} {pl2} à {fcfa(p2)} l'unité. Combien lui reste-t-il après avoir tout payé ?",
                 fcfa(left), [fcfa(v) for v in near_ints(rng, left, 8, lo=0)] + [fcfa(spent)],
                 f"Dépense : {n1} × {fr(p1)} + {n2} × {fr(p2)} = {fr(spent)} ; reste : {fr(start)} − {fr(spent)} = {fr(left)} FCFA.", src=SRC)


@gen(C, "cm2-fraction-of", cap=200, cat="Fractions")
def fraction_of(rng, d):
    n, dn = rng.choice([(1, 2), (1, 3), (1, 4), (3, 4), (2, 3), (1, 5), (2, 5), (3, 5), (1, 10), (3, 10)][: 4 + d])
    base = dn * rng.randint(2, 6 * d + 4) * (1 if dn != 10 else 1)
    r = base // dn * n
    label = {(1, 2): "la moitié", (1, 3): "le tiers", (1, 4): "le quart"}.get((n, dn), f"les {n}/{dn}")
    return Draft(f"Quelle est {label.replace('le ', 'le ')} de {fr(base)} ?" if (n, dn) in ((1, 2), (1, 3), (1, 4)) else f"Que valent les {n}/{dn} de {fr(base)} ?",
                 fr(r), ints(rng, r, lo=1) + [fr(base // dn), fr(base - r)], f"{fr(base)} ÷ {dn} × {n} = {fr(r)}.", src=SRC)


@gen(C, "cm2-simplify", cap=150, cat="Fractions")
def simplify(rng, d):
    n, dn = rng.choice([(1, 2), (1, 3), (2, 3), (3, 4), (1, 4), (3, 5), (2, 5), (4, 5), (5, 6), (3, 8), (5, 8), (7, 10), (1, 6), (5, 12), (7, 9)])
    k = rng.randint(2, 3 + d)
    a, b = n * k, dn * k
    wrongs = [frac_s(n + 1, dn), frac_s(n, dn + 1), frac_s(a // 2 if a % 2 == 0 else a + 1, b // 2 if b % 2 == 0 else b), frac_s(dn, n), frac_s(n + 1, dn + 1)]
    return Draft(f"Quelle est la forme irréductible de la fraction {a}/{b} ?", f"{n}/{dn}", wrongs,
                 f"On divise {a} et {b} par {k} : {a}/{b} = {n}/{dn}, qui ne se simplifie plus.", alt=(), src=SRC)


def frac_s(n, d):
    return f"{n}/{d}"


@gen(C, "cm2-compare-decimals", cap=220, cat="Nombres décimaux")
def compare_decimals(rng, d):
    base = rng.randint(1, 9 * d)
    vals = set()
    while len(vals) < 4:
        ip = base + rng.randint(0, 1)
        vals.add(round(ip + rng.randint(1, 999) / 1000, 3) if d > 2 else round(ip + rng.randint(1, 99) / 100, 2))
    vals = sorted(vals)
    big = vals[-1]
    shuffled = vals[:]
    rng.shuffle(shuffled)
    return Draft(f"Quel est le plus grand de ces nombres : {' ; '.join(fr(v) for v in shuffled)} ?", fr(big), [fr(v) for v in vals[:-1]],
                 f"On compare d'abord les parties entières puis les chiffres après la virgule : {fr(big)} est le plus grand.", src=SRC)


@gen(C, "cm2-compare-decimals-small", cap=220, cat="Nombres décimaux")
def compare_small(rng, d):
    vals = set()
    base = rng.randint(0, 6 * d)
    while len(vals) < 4:
        vals.add(round(base + rng.randint(1, 999) / 1000, 3) if d >= 3 else round(base + rng.randint(1, 99) / 100, 2))
    vals = sorted(vals)
    small = vals[0]
    shuffled = vals[:]
    rng.shuffle(shuffled)
    return Draft(f"Quel est le plus petit de ces nombres : {' ; '.join(fr(v) for v in shuffled)} ?", fr(small), [fr(v) for v in vals[1:]],
                 f"{fr(small)} est le plus petit : on compare les parties entières, puis les dixièmes, centièmes, millièmes.", src=SRC)


@gen(C, "cm2-round", cap=260, cat="Nombres décimaux")
def rounding(rng, d):
    x = round(rng.uniform(1, 10 ** min(d + 1, 4)), 3)
    kind = rng.choice(["l'unité", "le dixième", "le centième"][: 1 + min(d, 2)])
    kind_a = {"l'unité": "à l'unité", "le dixième": "au dixième", "le centième": "au centième"}[kind]
    dec = {"l'unité": 0, "le dixième": 1, "le centième": 2}[kind]
    r = round(x + 1e-9, dec)
    if dec == 0:
        r = int(math.floor(x + 0.5))
    right = fr(r, dec) if dec else fr(int(r))
    others = []
    step = 10 ** -dec
    for v in (r + step, r - step, math.floor(x * 10 ** dec) / 10 ** dec, math.ceil(x * 10 ** dec) / 10 ** dec):
        s = fr(v, dec) if dec else fr(int(v))
        if s != right:
            others.append(s)
    others += [fr(x, 3)]
    return Draft(f"Quel est l'arrondi de {fr(x, 3)} {kind_a} près ?", right, others,
                 f"On regarde le chiffre suivant : {fr(x, 3)} arrondi {kind_a} donne {right}.", src=SRC)


@gen(C, "cm2-times10", cap=250, cat="Nombres décimaux")
def times10(rng, d):
    x = round(rng.uniform(0.01, 99.99) if d < 3 else rng.uniform(0.001, 999.999), 2 if d < 3 else 3)
    k = rng.choice([10, 100, 1000])
    div_ = rng.random() < 0.4
    r = x / k if div_ else x * k
    r = round(r, 6)
    right = fr(r, 6)
    wrongs = [fr(round(x * (k * 10 if not div_ else k / 10) if not div_ else x / (k * 10), 6), 6), fr(round(x * (k / 10 if not div_ else 1), 6), 6)]
    wrongs += [fr(round(x / k if not div_ else x * k, 6), 6), fr(round(r * 10, 6), 6), fr(round(r / 10, 6), 6)]
    op = "÷" if div_ else "×"
    return Draft(f"Combien font {fr(x, 3)} {op} {fr(k)} ?", right, wrongs,
                 f"{op} {fr(k)} déplace la virgule de {len(str(k)) - 1} rang{'s' if k > 10 else ''} vers la {'gauche' if div_ else 'droite'} : {right}.", src=SRC)


@gen(C, "cm2-decimal-add", cap=240, cat="Nombres décimaux")
def decimal_add(rng, d):
    dec = 1 if d < 3 else 2
    a = round(rng.uniform(1, 10 ** (1 + d // 2)), dec)
    b = round(rng.uniform(1, 10 ** (1 + d // 2)), dec)
    sub_ = rng.random() < 0.5 and a > b
    r = round(a - b if sub_ else a + b, 3)
    return Draft(f"Combien font {fr(a, dec)} {'−' if sub_ else '+'} {fr(b, dec)} ?", fr(r, dec), [fr(v, dec) for v in near_floats(rng, r, dec)],
                 f"On aligne les virgules : {fr(a, dec)} {'−' if sub_ else '+'} {fr(b, dec)} = {fr(r, dec)}.", src=SRC)


@gen(C, "cm2-decimal-mul", cap=200, cat="Nombres décimaux")
def decimal_mul(rng, d):
    a = round(rng.uniform(1, 9 * d), 1)
    b = rng.randint(2, 5 + d)
    r = round(a * b, 2)
    return Draft(f"Combien font {fr(a, 1)} × {b} ?", fr(r, 2), [fr(v, 2) for v in near_floats(rng, r, 1)] + [fr(round(a * b / 10, 3), 3)],
                 f"{fr(a, 1)} × {b} = {fr(r, 2)}.", src=SRC)


@gen(C, "cm2-perimeter", cap=220, cat="Géométrie")
def perimeter(rng, d):
    shape = rng.choice(["rectangle", "carré", "triangle"])
    k = 4 + 6 * d
    if shape == "rectangle":
        L, l = rng.randint(6, k + 6), rng.randint(2, k)
        L, l = max(L, l + 1), min(l, L - 1)
        p = 2 * (L + l)
        t, e = f"Un rectangle mesure {L} cm de long et {l} cm de large. Quel est son périmètre ?", f"2 × ({L} + {l}) = {p} cm."
        w = [p - L, L * l, p + 2, L + l]
    elif shape == "carré":
        c = rng.randint(3, k + 4)
        p = 4 * c
        t, e = f"Un carré a un côté de {c} cm. Quel est son périmètre ?", f"4 × {c} = {p} cm."
        w = [c * c, 2 * c, p + 4, c + 4]
    else:
        a, b, c = rng.randint(4, k + 6), rng.randint(4, k + 6), rng.randint(4, k + 6)
        if not (a < b + c and b < a + c and c < a + b):
            return None
        p = a + b + c
        t, e = f"Un triangle a pour côtés {a} cm, {b} cm et {c} cm. Quel est son périmètre ?", f"{a} + {b} + {c} = {p} cm."
        w = [p - 1, p + 2, a * b * c // 10 or 3, 2 * p]
    return Draft(t, f"{p} cm", [f"{x} cm" for x in w if x > 0], e, src=SRC)


@gen(C, "cm2-area", cap=220, cat="Géométrie")
def area(rng, d):
    shape = rng.choice(["rectangle", "carré", "triangle"])
    k = 4 + 5 * d
    if shape == "rectangle":
        L, l = rng.randint(4, k + 6), rng.randint(2, k)
        a = L * l
        t, e, w = f"Un rectangle mesure {L} m de long et {l} m de large. Quelle est son aire ?", f"{L} × {l} = {a} m².", [2 * (L + l), a + L, a - l, a * 2]
    elif shape == "carré":
        c = rng.randint(3, k + 4)
        a = c * c
        t, e, w = f"Un carré a un côté de {c} m. Quelle est son aire ?", f"{c} × {c} = {a} m².", [4 * c, 2 * c, a + c, a - c]
    else:
        b, h = rng.randint(4, k + 6) * 2, rng.randint(3, k + 3)
        a = b * h // 2
        t, e, w = f"Un triangle a une base de {b} m et une hauteur de {h} m. Quelle est son aire ?", f"{b} × {h} ÷ 2 = {a} m².", [b * h, a + h, b + h, a * 2 + 2]
    return Draft(t, f"{a} m²", [f"{x} m²" for x in w if x > 0], e, src=SRC)


UNITS = [("km", "m", 1000), ("m", "cm", 100), ("cm", "mm", 10), ("kg", "g", 1000), ("t", "kg", 1000), ("L", "cL", 100), ("L", "mL", 1000), ("m", "dm", 10), ("hL", "L", 100)]


@gen(C, "cm2-units", cap=260, cat="Mesures")
def units(rng, d):
    big, small, k = rng.choice(UNITS)
    x = rng.choice([rng.randint(2, 99), round(rng.uniform(1, 30), 1), round(rng.uniform(0.1, 9.9), 2)][: 1 + (d > 1) + (d > 3)])
    down = rng.random() < 0.6
    r = round(x * k if down else x / k, 6)
    right = f"{fr(r, 6)} {small if down else big}"
    tgt = small if down else big
    src_u = big if down else small
    wrongs = [f"{fr(round(x * k * 10 if down else x / (k * 10), 6), 6)} {tgt}", f"{fr(round(x * k / 10 if down else x / k * 10, 6), 6)} {tgt}",
              f"{fr(round(x / k if down else x * k, 6), 6)} {tgt}", f"{fr(round(x * 10 if down else x / 10, 6), 6)} {tgt}"]
    return Draft(f"Combien y a-t-il de {tgt} dans {fr(x, 6)} {src_u} ?", right, wrongs, f"1 {big} = {fr(k)} {small} : {fr(x, 6)} {src_u} = {right}.", src=SRC)


def hm(m):
    return f"{m // 60} h {m % 60:02d}"


@gen(C, "cm2-time-end", cap=240, cat="Mesures")
def time_end(rng, d):
    start = rng.randint(6 * 60, 20 * 60) // 5 * 5
    dur = rng.choice([25, 35, 40, 45, 50, 75, 85, 95, 110, 130]) + rng.randint(0, 5) * 5 * (d > 2)
    end = start + dur
    if end >= 24 * 60:
        return None
    wrongs = [hm(end + 10), hm(end - 10), hm(end + 60 if end + 60 < 1440 else end - 60), hm(start + dur + 40 - 10 * (dur % 3))]
    return Draft(f"Une séance commence à {hm(start)} et dure {dur // 60} h {dur % 60:02d} min. À quelle heure finit-elle ?", hm(end), wrongs,
                 f"{hm(start)} + {dur // 60} h {dur % 60:02d} min = {hm(end)} (attention : 1 h = 60 min).", src=SRC)


@gen(C, "cm2-time-convert", cap=200, cat="Mesures")
def time_convert(rng, d):
    h, m = rng.randint(1, 4 + d), rng.choice([5, 10, 15, 20, 30, 40, 45, 50])
    tot = h * 60 + m
    return Draft(f"Combien de minutes y a-t-il dans {h} h {m:02d} min ?", f"{tot} min", [f"{v} min" for v in near_ints(rng, tot, 8, lo=1)] + [f"{h * 100 + m} min"],
                 f"{h} × 60 + {m} = {tot} minutes.", src=SRC)


@gen(C, "cm2-proportion", cap=240, cat="Problèmes")
def proportion(rng, d):
    item, plural, price = rng.choice(ITEMS)
    n1 = rng.randint(2, 6)
    n2 = rng.randint(n1 + 1, n1 + 3 + d)
    t1, t2 = n1 * price, n2 * price
    return Draft(f"{n1} {plural} coûtent {fcfa(t1)} au total. Combien coûtent {n2} {plural} au même prix ?", fcfa(t2),
                 [fcfa(v) for v in near_ints(rng, t2, 8, lo=1)] + [fcfa(t1 + n2)],
                 f"Prix d'un : {fr(t1)} ÷ {n1} = {fr(price)} ; pour {n2} : {n2} × {fr(price)} = {fr(t2)} FCFA.", src=SRC)


@gen(C, "cm2-mean", cap=220, cat="Statistiques")
def mean(rng, d):
    n = rng.choice([3, 4, 5])
    m = rng.randint(6, 16)
    vals = [m + rng.randint(-4, 4) for _ in range(n - 1)]
    vals.append(m * n - sum(vals))
    if any(v < 0 or v > 20 for v in vals):
        return None
    rng.shuffle(vals)
    return Draft(f"Un élève a eu les notes {', '.join(map(str, vals))}. Quelle est sa moyenne ?", fr(m), ints(rng, m, lo=0, hi=20) + [str(max(vals))],
                 f"({' + '.join(map(str, vals))}) ÷ {n} = {sum(vals)} ÷ {n} = {m}.", src=SRC)


@gen(C, "cm2-percent", cap=240, cat="Problèmes")
def percent(rng, d):
    p = rng.choice([10, 20, 25, 50, 75, 5, 30, 40][: 3 + d])
    base = rng.choice([40, 60, 80, 100, 120, 200, 240, 400, 600, 800, 1000, 2000, 4000, 5000])
    if base * p % 100:
        return None
    r = base * p // 100
    return Draft(f"Quel est {p} % de {fr(base)} ?", fr(r), ints(rng, r, lo=1) + [fr(base - r), fr(base // p)], f"{fr(base)} × {p} ÷ 100 = {fr(r)}.", src=SRC)


@gen(C, "cm2-discount", cap=220, cat="Problèmes")
def discount(rng, d):
    p = rng.choice([10, 20, 25, 50])
    base = rng.choice([1000, 2000, 2500, 4000, 5000, 8000, 10000, 12000, 20000])
    red = base * p // 100
    r = base - red
    return Draft(f"Un article coûte {fcfa(base)}. Son prix baisse de {p} %. Quel est le nouveau prix ?", fcfa(r),
                 [fcfa(red), fcfa(base + red), fcfa(r + 500), fcfa(base - p)], f"Réduction : {p} % de {fr(base)} = {fr(red)} ; nouveau prix : {fr(base)} − {fr(red)} = {fr(r)} FCFA.", src=SRC)


@gen(C, "cm2-multiple", cap=200, cat="Nombres")
def multiple_of(rng, d):
    k = rng.choice([3, 4, 6, 7, 8, 9, 11, 12][: 3 + d])
    m = k * rng.randint(3, 10 * d + 6)
    lo = m - rng.randint(1, k - 1)
    hi = lo + k - 1                       # k consecutive numbers: exactly one multiple of k
    cands = [x for x in range(lo, hi + 1)]
    assert sum(1 for x in cands if x % k == 0) == 1
    wrongs = [fr(x) for x in cands if x != m]
    rng.shuffle(wrongs)
    return Draft(f"Quel nombre compris entre {fr(lo)} et {fr(hi)} (inclus) est un multiple de {k} ?", fr(m), wrongs,
                 f"{fr(m)} = {k} × {m // k} : c'est le seul multiple de {k} entre {fr(lo)} et {fr(hi)}.", src=SRC)


@gen(C, "cm2-divisible", cap=160, cat="Nombres")
def divisible(rng, d):
    k = rng.choice([3, 4, 9, 6][: 1 + d] if d < 4 else [3, 4, 9, 6])
    m = k * rng.randint(10, 300 + 100 * d)
    lo = m - rng.randint(0, k - 1)
    hi = lo + k - 1
    cands = list(range(lo, hi + 1))
    assert sum(1 for x in cands if x % k == 0) == 1
    wrongs = [fr(x) for x in cands if x != m]
    rule = {3: "la somme de ses chiffres est divisible par 3", 4: "ses deux derniers chiffres forment un multiple de 4",
            9: "la somme de ses chiffres est divisible par 9", 6: "il est pair et la somme de ses chiffres est divisible par 3"}[k]
    return Draft(f"Quel nombre compris entre {fr(lo)} et {fr(hi)} (inclus) est divisible par {k} ?", fr(m), wrongs, f"{fr(m)} est divisible par {k} : {rule}.", src=SRC)


PRIMES = [p for p in range(2, 400) if all(p % q for q in range(2, int(p ** .5) + 1))]


@gen(C, "cm2-prime", cap=120, cat="Nombres")
def prime(rng, d):
    p = rng.choice([p for p in PRIMES if 10 < p < 40 + 55 * d])
    lo = p - rng.randint(1, 5)
    hi = lo + 6
    cands = [x for x in range(lo, hi + 1)]
    primes_in = [x for x in cands if x in PRIMES]
    if primes_in != [p]:
        return None
    wrongs = [fr(x) for x in cands if x != p]
    return Draft(f"Quel nombre premier est compris entre {fr(lo)} et {fr(hi)} (inclus) ?", fr(p), wrongs,
                 f"{p} n'est divisible que par 1 et par lui-même ; les autres nombres de cet intervalle ont d'autres diviseurs.", src=SRC)


@gen(C, "cm2-digit-value", cap=260, cat="Nombres")
def digit_value(rng, d):
    n = rnd(rng, 3 + min(d, 3))
    s = str(n)
    names = ["unités", "dizaines", "centaines", "milliers", "dizaines de mille", "centaines de mille"]
    pos = rng.randrange(len(s))
    k = len(s) - 1 - pos
    if rng.random() < 0.5:
        others = [names[i] for i in range(len(s)) if i != k]
        return Draft(f"Dans le nombre {fr(n)}, le chiffre {s[pos]} est à quel rang ?" if s.count(s[pos]) == 1 else f"Dans le nombre {fr(n)}, quel est le rang du {pos + 1}e chiffre en partant de la gauche ?",
                     f"les {names[k]}", [f"les {o}" for o in others], f"Dans {fr(n)}, en partant de la droite : unités, dizaines, centaines… le chiffre {s[pos]} est celui des {names[k]}.", src=SRC)
    v = int(s[pos]) * 10 ** k
    if s.count(s[pos]) > 1 or v == 0:
        return None
    return Draft(f"Quelle est la valeur du chiffre {s[pos]} dans le nombre {fr(n)} ?", fr(v), [fr(int(s[pos]) * 10 ** j) for j in range(len(s)) if j != k],
                 f"Le chiffre {s[pos]} est au rang des {names[k]} : sa valeur est {fr(v)}.", src=SRC)


@gen(C, "cm2-words", cap=230, cat="Nombres")
def words(rng, d):
    n = rng.choice([rng.randint(11, 99), rng.randint(70, 99), rng.randint(100, 999), rng.randint(1000, 9999)][: d])
    right = words_fr(n)
    wrongs = [words_fr(v) for v in near_ints(rng, n, 10, lo=1) if v < 10000] + [words_fr(int(str(n)[::-1]))]
    return Draft(f"Comment écrit-on {fr(n)} en lettres ?", right, wrongs, f"{fr(n)} s'écrit « {right} ».", src=SRC, cat="Français")


@gen(C, "cm2-roman-to-num", cap=150, cat="Nombres")
def roman_to_num(rng, d):
    n = rng.randint(4, 40 * d + 10)
    return Draft(f"Quel nombre représente l'écriture romaine {roman(n)} ?", fr(n), ints(rng, n, lo=1), f"{roman(n)} = {fr(n)} (I = 1, V = 5, X = 10, L = 50, C = 100, D = 500, M = 1 000).", src=SRC)


@gen(C, "cm2-num-to-roman", cap=150, cat="Nombres")
def num_to_roman(rng, d):
    n = rng.randint(4, 40 * d + 10)
    wrongs = [roman(v) for v in near_ints(rng, n, 10, lo=1)]
    return Draft(f"Comment écrit-on {fr(n)} en chiffres romains ?", roman(n), wrongs, f"{fr(n)} s'écrit {roman(n)} en chiffres romains.", src=SRC)


@gen(C, "cm2-sequence", cap=240, cat="Logique")
def sequence(rng, d):
    geo = d >= 3 and rng.random() < 0.5
    if geo:
        a, r = rng.randint(1, 5), rng.choice([2, 3])
        seq = [a * r ** i for i in range(6)]
        rule = f"on multiplie par {r}"
    else:
        a, r = rng.randint(1, 50), rng.choice([2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 25])
        if d >= 4 and rng.random() < 0.5:
            r = -r
            a += 6 * abs(r)
        seq = [a + r * i for i in range(6)]
        rule = f"on {'ajoute' if r > 0 else 'retire'} {abs(r)} à chaque fois"
    shown, nxt = seq[:4], seq[4]
    return Draft(f"Quel nombre complète la suite {' ; '.join(map(str, shown))} ; … ?", fr(nxt), ints(rng, nxt, lo=0) + [fr(shown[-1] + (shown[-1] - shown[-2]) + 1)],
                 f"Règle : {rule}. Après {shown[-1]} vient {fr(nxt)}.", src=SRC)


@gen(C, "cm2-complement", cap=200, cat="Calcul")
def complement(rng, d):
    target = {1: 100, 2: 100, 3: 1000, 4: 1000, 5: 10000}[d]
    a = rng.randint(target // 10 + 1, target - 1)
    r = target - a
    return Draft(f"Quel nombre faut-il ajouter à {fr(a)} pour obtenir {fr(target)} ?", fr(r), ints(rng, r, lo=1), f"{fr(target)} − {fr(a)} = {fr(r)}.", src=SRC)


@gen(C, "cm2-double-half", cap=200, cat="Calcul")
def double_half(rng, d):
    x = rng.randint(6, 40 * d) * 2
    kind = rng.choice(["double", "moitié", "triple", "quart"])
    if kind == "quart":
        x *= 2
    r = {"double": 2 * x, "moitié": x // 2, "triple": 3 * x, "quart": x // 4}[kind]
    art = {"double": "le double", "moitié": "la moitié", "triple": "le triple", "quart": "le quart"}[kind]
    return Draft(f"Quel est {art} de {fr(x)} ?".replace("Quel est la", "Quelle est la"), fr(r), ints(rng, r, lo=1) + [fr(x * 2 if kind not in ("double",) else x // 2)],
                 f"{art.capitalize()} de {fr(x)} est {fr(r)}.", src=SRC)


@gen(C, "cm2-fraction-add", cap=200, cat="Fractions")
def fraction_add(rng, d):
    den = rng.choice([4, 5, 6, 8, 10, 12, 7, 9])
    a = rng.randint(1, den - 2)
    b = rng.randint(1, den - 1 - a)
    if a + b >= den:
        return None
    n, r = a + b, frac_s(a + b, den)
    return Draft(f"Combien font {a}/{den} + {b}/{den} ?", r, [frac_s(a + b, 2 * den), frac_s(a * b, den), frac_s(a + b + 1, den), frac_s(a + b, den + 1), frac_s(abs(a - b) or 1, den)],
                 f"Même dénominateur : on additionne les numérateurs, {a} + {b} = {n}, donc {n}/{den}.", src=SRC, cat="Fractions")


@gen(C, "cm2-fraction-compare", cap=200, cat="Fractions")
def fraction_compare(rng, d):
    for _ in range(20):
        a, b, c, e = rng.randint(1, 5), rng.randint(2, 9), rng.randint(1, 5), rng.randint(2, 9)
        if a < b and c < e and a * e != c * b:
            break
    else:
        return None
    big = (a, b) if a * e > c * b else (c, e)
    small = (c, e) if big == (a, b) else (a, b)
    return Draft(f"Quelle fraction est la plus grande : {a}/{b} ou {c}/{e} ?", frac_s(*big), [frac_s(*small), "Elles sont égales", "On ne peut pas savoir"],
                 f"On compare {a} × {e} = {a * e} et {c} × {b} = {c * b} : {frac_s(*big)} est la plus grande.", src=SRC)


@gen(C, "cm2-speed", cap=220, cat="Problèmes")
def speed(rng, d):
    v = rng.choice([10, 12, 15, 20, 30, 40, 45, 50, 60, 80, 90])
    t = rng.randint(2, 4 + d)
    kind = rng.choice(["dist", "time"])
    if kind == "dist":
        return Draft(f"Un véhicule roule à {v} km/h pendant {t} h. Quelle distance parcourt-il ?", f"{fr(v * t)} km", [f"{fr(x)} km" for x in near_ints(rng, v * t, 8, lo=1)] + [f"{fr(v + t)} km"],
                     f"Distance = vitesse × temps = {v} × {t} = {v * t} km.", src=SRC)
    return Draft(f"Un véhicule roule à {v} km/h. Combien de temps met-il pour parcourir {fr(v * t)} km ?", f"{t} h", [f"{x} h" for x in near_ints(rng, t, 8, lo=1)] + [f"{v} h"],
                 f"Temps = distance ÷ vitesse = {v * t} ÷ {v} = {t} h.", src=SRC)


@gen(C, "cm2-successor", cap=160, cat="Nombres")
def successor(rng, d):
    n = rng.choice([rng.randint(100, 999), rng.randint(1000, 9999), rng.randint(10000, 99999), 10 ** rng.randint(2, 5) - 1, 10 ** rng.randint(2, 5)])
    up = rng.random() < 0.5
    r = n + 1 if up else n - 1
    return Draft(f"Quel est le nombre qui {'suit' if up else 'précède'} immédiatement {fr(n)} ?", fr(r), ints(rng, r, lo=0) + [fr(n + 10 if up else n - 10)],
                 f"{fr(n)} {'+ 1' if up else '− 1'} = {fr(r)}.", src=SRC)


@gen(C, "cm2-weeks-days", cap=160, cat="Mesures")
def weeks_days(rng, d):
    w = rng.randint(2, 6 + 3 * d)
    unit = rng.choice([("semaines", "jours", 7), ("ans", "mois", 12), ("jours", "heures", 24), ("heures", "minutes", 60), ("minutes", "secondes", 60)])
    r = w * unit[2]
    return Draft(f"Combien y a-t-il de {unit[1]} dans {w} {unit[0]} ?", fr(r), ints(rng, r, lo=1) + [fr(w + unit[2])], f"{w} × {unit[2]} = {fr(r)} {unit[1]}.", src=SRC)


@gen(C, "cm2-shopping-unit-price", cap=200, cat="Problèmes")
def unit_price(rng, d):
    item, plural, price = rng.choice(ITEMS)
    n = rng.randint(3, 6 + 2 * d)
    total = price * n
    return Draft(f"Pour {n} {plural}, on a payé {fcfa(total)}. Quel est le prix d'un seul ?", fcfa(price), [fcfa(v) for v in near_ints(rng, price, 8, lo=10)] + [fcfa(total - n)],
                 f"{fr(total)} ÷ {n} = {fr(price)} : {fcfa(price)} l'unité.", src=SRC)


@gen(C, "cm2-rect-missing-side", cap=180, cat="Géométrie")
def rect_missing(rng, d):
    L, l = rng.randint(6, 12 + 6 * d), rng.randint(3, 5 + 4 * d)
    if L <= l:
        return None
    if rng.random() < 0.5:
        p = 2 * (L + l)
        return Draft(f"Un rectangle a un périmètre de {p} cm et une largeur de {l} cm. Quelle est sa longueur ?", f"{L} cm", [f"{x} cm" for x in near_ints(rng, L, 8, lo=1)] + [f"{p // 2} cm"],
                     f"Demi-périmètre : {p} ÷ 2 = {p // 2} ; longueur : {p // 2} − {l} = {L} cm.", src=SRC)
    a = L * l
    return Draft(f"Un rectangle a une aire de {a} cm² et une largeur de {l} cm. Quelle est sa longueur ?", f"{L} cm", [f"{x} cm" for x in near_ints(rng, L, 8, lo=1)] + [f"{a - l} cm"],
                 f"Longueur = aire ÷ largeur = {a} ÷ {l} = {L} cm.", src=SRC)


@gen(C, "cm2-capacity", cap=140, cat="Mesures")
def capacity(rng, d):
    n = rng.randint(2, 6 + 2 * d)
    unit = rng.choice([25, 33, 50, 75, 150])
    tot = n * unit
    return Draft(f"Une bouteille contient {unit} cL. Combien de litres contiennent {n} bouteilles ?", f"{fr(tot / 100, 2)} L",
                 [f"{fr(v / 100, 2)} L" for v in near_ints(rng, tot, 8, lo=1)] + [f"{fr(tot)} L"],
                 f"{n} × {unit} = {tot} cL = {fr(tot / 100, 2)} L (100 cL = 1 L).", src=SRC)


@gen(C, "cm2-age-year", cap=160, cat="Problèmes")
def age_year(rng, d):
    y = rng.randint(1960, 2010)
    now = rng.randint(2020, 2030)
    if rng.random() < 0.5:
        return Draft(f"Une école a été fondée en {y}. Quel âge a-t-elle en {now} ?", f"{now - y} ans", [f"{v} ans" for v in near_ints(rng, now - y, 8, lo=1)],
                     f"{now} − {y} = {now - y} ans.", src=SRC)
    age = rng.randint(6, 12)
    return Draft(f"Un enfant a {age} ans en {now}. En quelle année est-il né ?", str(now - age), [str(v) for v in near_ints(rng, now - age, 8)], f"{now} − {age} = {now - age}.", src=SRC)
