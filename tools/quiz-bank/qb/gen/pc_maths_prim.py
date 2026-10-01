"""Mathématiques / Mathematics, primaire : CP-CM1 (francophone) et Class 1-6 (anglophone). Réponses calculées ; mauvaises
réponses = erreurs typiques (oubli de retenue, mauvaise opération, chiffre décalé). Programmes MINEDUB / MINEDUB syllabus."""
import math

from ..core import Draft
from .pc_common import FR_COURSES, EN_COURSES, N, ask, item, money, name, plaus, q_end, reg, wrongs_int

PRIM = ["cp", "ce1", "ce2", "cm1", "class1", "class2", "class3", "class4", "class5", "class6"]
P2 = [c for c in PRIM if c not in ("cp", "class1")]          # from grade 2
P3 = [c for c in PRIM if c not in ("cp", "class1", "ce1", "class2")]   # from grade 3
P4 = [c for c in P3 if c not in ("ce2", "class3")]           # from grade 4

ADD_MAX = {1: [10, 20, 30, 60, 100], 2: [50, 100, 300, 600, 1000], 3: [200, 1000, 3000, 6000, 10000], 4: [1000, 10000, 30000, 100000, 500000],
           5: [10000, 100000, 500000, 1000000, 5000000], 6: [10000, 100000, 1000000, 5000000, 9000000],
           7: [1000, 100000, 1000000, 5000000, 9000000], 8: [1000, 100000, 1000000, 5000000, 9000000], 9: [1000, 100000, 1000000, 5000000, 9000000]}


def hi(g, d):
    return ADD_MAX[g][d - 1]


STORY_MAX = {7: [1000, 5000, 20000, 100000, 500000], 8: [1000, 5000, 20000, 100000, 500000], 9: [1000, 5000, 20000, 100000, 500000], 1: [10, 20, 30, 60, 100], 2: [50, 100, 300, 600, 1000], 3: [200, 1000, 2000, 3000, 5000], 4: [1000, 5000, 10000, 20000, 50000],
             5: [5000, 20000, 50000, 100000, 500000], 6: [5000, 20000, 50000, 100000, 500000]}


def spair(rng, g, d):
    m = STORY_MAX[g][d - 1]
    return rng.randint(max(2, m // 10), m - 1), rng.randint(max(2, m // 10), m - 1)


def pair(rng, g, d, lo=1):
    m = hi(g, d)
    return rng.randint(max(lo, m // 10), m - 1), rng.randint(max(lo, m // 10), m - 1)


def op_wrongs(rng, right, lg, extra=()):
    return wrongs_int(rng, right, lg, lo=0) + [N(e, lg) for e in extra if e != right and e >= 0]


def add(rng, d, g, lg):
    a, b = pair(rng, g, d)
    if g == 1 and d <= 2:
        a, b = rng.randint(1, 9), rng.randint(1, 9)
    r = a + b
    return Draft(q_end(lg, ask(lg, f"Combien font {N(a, lg)} + {N(b, lg)}", f"What is {N(a, lg)} + {N(b, lg)}")), N(r, lg), op_wrongs(rng, r, lg, [abs(a - b)]),
                 ask(lg, f"{N(a, lg)} + {N(b, lg)} = {N(r, lg)}.", f"{N(a, lg)} + {N(b, lg)} = {N(r, lg)}."))


def sub(rng, d, g, lg):
    a, b = pair(rng, g, d)
    a, b = max(a, b), min(a, b)
    if a == b:
        return None
    r = a - b
    return Draft(q_end(lg, ask(lg, f"Combien font {N(a, lg)} − {N(b, lg)}", f"What is {N(a, lg)} − {N(b, lg)}")), N(r, lg), op_wrongs(rng, r, lg, [a + b]),
                 f"{N(a, lg)} − {N(b, lg)} = {N(r, lg)}.")


MUL_RANGE = {2: ([2, 5, 10], [2, 3, 4, 5, 10]), 3: ([2, 3, 4, 5, 6, 7, 8, 9, 10], None), 4: (None, None), 5: (None, None), 6: (None, None)}


def mul(rng, d, g, lg):
    if g == 1:
        a, b = rng.choice([2, 3, 4, 5, 10]), rng.randint(1, 5)
        if d < 3:
            a, b = rng.choice([2, 5, 10]), rng.randint(1, 5)
    elif g == 2:
        a, b = rng.choice([2, 3, 4, 5, 10]), rng.randint(2, 10)
    elif g == 3:
        a, b = (rng.randint(2, 9), rng.randint(2, 9)) if d <= 3 else (rng.randint(11, 99), rng.randint(2, 9))
    elif g == 4:
        a, b = (rng.randint(6, 12), rng.randint(6, 12)) if d <= 2 else ((rng.randint(11, 99), rng.randint(2, 9)) if d == 3 else (rng.randint(11, 99), rng.randint(11, 99)))
    else:
        a, b = (rng.randint(11, 99), rng.randint(2, 9)) if d <= 2 else ((rng.randint(11, 99), rng.randint(11, 99)) if d <= 4 else (rng.randint(101, 999), rng.randint(11, 99)))
    r = a * b
    sign = "×"
    return Draft(q_end(lg, ask(lg, f"Combien font {N(a, lg)} {sign} {N(b, lg)}", f"What is {N(a, lg)} {sign} {N(b, lg)}")), N(r, lg),
                 op_wrongs(rng, r, lg, [a + b, a * (b + 1), a * (b - 1)]), f"{N(a, lg)} × {N(b, lg)} = {N(r, lg)}.")


def div(rng, d, g, lg):
    b = rng.choice({2: [2, 5, 10], 3: [2, 3, 4, 5, 6, 7, 8, 9, 10]}.get(g, list(range(2, 13) if g < 5 else range(3, 26))))
    q = rng.randint(2, {2: 10, 3: 12, 4: 30, 5: 120, 6: 300}.get(g, 300) * (1 if d < 4 else 2))
    a = q * b
    return Draft(q_end(lg, ask(lg, f"Combien font {N(a, lg)} ÷ {N(b, lg)}", f"What is {N(a, lg)} ÷ {N(b, lg)}")), N(q, lg),
                 op_wrongs(rng, q, lg, [b, a - b]), ask(lg, f"{N(a, lg)} ÷ {N(b, lg)} = {N(q, lg)} car {N(q, lg)} × {N(b, lg)} = {N(a, lg)}.",
                                                     f"{N(a, lg)} ÷ {N(b, lg)} = {N(q, lg)} because {N(q, lg)} × {N(b, lg)} = {N(a, lg)}."))


def missing_add(rng, d, g, lg):
    a, b = pair(rng, g, d)
    if g == 1 and d <= 2:
        a, b = rng.randint(1, 9), rng.randint(1, 9)
    r = a + b
    return Draft(q_end(lg, ask(lg, f"Quel nombre faut-il ajouter à {N(a, lg)} pour obtenir {N(r, lg)}", f"Which number must be added to {N(a, lg)} to get {N(r, lg)}")),
                 N(b, lg), op_wrongs(rng, b, lg, [r + a, a]),
                 ask(lg, f"{N(r, lg)} − {N(a, lg)} = {N(b, lg)}, donc {N(a, lg)} + {N(b, lg)} = {N(r, lg)}.", f"{N(r, lg)} − {N(a, lg)} = {N(b, lg)}, so {N(a, lg)} + {N(b, lg)} = {N(r, lg)}."))


def missing_sub(rng, d, g, lg):
    a, b = pair(rng, g, d)
    a, b = max(a, b), min(a, b)
    if a == b:
        return None
    r = a - b
    return Draft(q_end(lg, ask(lg, f"Quel nombre faut-il retrancher de {N(a, lg)} pour obtenir {N(r, lg)}", f"Which number must be taken away from {N(a, lg)} to get {N(r, lg)}")),
                 N(b, lg), op_wrongs(rng, b, lg, [a + r, r]), f"{N(a, lg)} − {N(r, lg)} = {N(b, lg)}.")


def biggest(rng, d, g, lg):
    m = hi(g, d)
    vals = set()
    while len(vals) < 4:
        vals.add(rng.randint(max(1, m // 10), m - 1))
    vals = sorted(vals)
    sh = vals[:]
    rng.shuffle(sh)
    big = rng.random() < 0.5
    right = vals[-1] if big else vals[0]
    others = vals[:-1] if big else vals[1:]
    return Draft(q_end(lg, ask(lg, f"Quel est le plus {'grand' if big else 'petit'} de ces nombres : {' ; '.join(N(v, lg) for v in sh)}",
                               f"Which is the {'greatest' if big else 'smallest'} of these numbers: {', '.join(N(v, lg) for v in sh)}")), N(right, lg), [N(v, lg) for v in others],
                 ask(lg, f"On compare les chiffres de la gauche vers la droite : {N(right, lg)} est le plus {'grand' if big else 'petit'}.",
                     f"Compare the digits from the left: {N(right, lg)} is the {'greatest' if big else 'smallest'}."))


def next_prev(rng, d, g, lg):
    m = hi(g, d)
    a = rng.randint(max(2, m // 10), m - 2)
    nxt = rng.random() < 0.5
    r = a + 1 if nxt else a - 1
    return Draft(q_end(lg, ask(lg, f"Quel nombre vient juste {'après' if nxt else 'avant'} {N(a, lg)}", f"Which number comes just {'after' if nxt else 'before'} {N(a, lg)}")),
                 N(r, lg), [N(v, lg) for v in (a + 2, a - 2, a + 10, a - 10, a) if v != r and v >= 0],
                 ask(lg, f"Juste {'après' if nxt else 'avant'} {N(a, lg)}, on trouve {N(r, lg)}.", f"The number just {'after' if nxt else 'before'} {N(a, lg)} is {N(r, lg)}."))


PLACES_FR = [("unités", "units", 1), ("dizaines", "tens", 10), ("centaines", "hundreds", 100), ("milliers", "thousands", 1000), ("dizaines de mille", "ten thousands", 10000),
             ("centaines de mille", "hundred thousands", 100000)]


def digit_place(rng, d, g, lg):
    ndig = {1: 2, 2: 3, 3: 4, 4: 5, 5: 6, 6: 6}.get(g, 6) - (1 if d <= 2 and g > 1 else 0)
    n = rng.randint(10 ** (ndig - 1), 10 ** ndig - 1)
    idx = rng.randrange(ndig)
    p = PLACES_FR[idx]
    digit = (n // p[2]) % 10
    return Draft(q_end(lg, ask(lg, f"Dans le nombre {N(n, lg)}, quel est le chiffre des {p[0]}", f"In the number {N(n, lg)}, which digit is in the {p[1]} place")), str(digit),
                 [str(x) for x in range(10) if x != digit], ask(lg, f"Dans {N(n, lg)}, le chiffre des {p[0]} est {digit}.", f"In {N(n, lg)}, the digit in the {p[1]} place is {digit}."), diff=None)


def compose(rng, d, g, lg):
    """a centaines + b dizaines + c unités (CE1+), or thousands too."""
    th = g >= 3 and d >= 3
    t = rng.randint(1, 9) if th else 0
    h, te, u = rng.randint(1, 9), rng.randint(0, 9), rng.randint(0, 9)
    if g == 1:
        te, u, h = rng.randint(1, 9), rng.randint(0, 9), 0
    n = t * 1000 + h * 100 + te * 10 + u
    parts_fr = ([f"{t} milliers"] if t else []) + ([f"{h} centaines"] if h else []) + [f"{te} dizaines", f"{u} unités"]
    parts_en = ([f"{t} thousands"] if t else []) + ([f"{h} hundreds"] if h else []) + [f"{te} tens", f"{u} units"]
    wrong = [n + 10, n - 10, n + 100, n - 1, int(str(n)[::-1]) if int(str(n)[::-1]) != n else n + 1000, n * 10]
    return Draft(q_end(lg, ask(lg, "Quel nombre est égal à " + " + ".join(parts_fr), "Which number is equal to " + " + ".join(parts_en))), N(n, lg),
                 [N(w, lg) for w in wrong if w > 0 and w != n], ask(lg, f"On additionne les valeurs de chaque chiffre : {N(n, lg)}.", f"Add the value of each digit: {N(n, lg)}."))


def even_odd(rng, d, g, lg):
    m = hi(g, d)
    even = rng.random() < 0.5
    rights = [rng.randint(m // 10 or 1, m - 1) for _ in range(1)]
    r = rights[0]
    if (r % 2 == 0) != even:
        r += 1
    others = set()
    while len(others) < 3:
        o = rng.randint(m // 10 or 1, m - 1)
        if (o % 2 == 0) != even and o != r:
            others.add(o)
    return Draft(q_end(lg, ask(lg, f"Lequel de ces nombres est {'pair' if even else 'impair'}", f"Which of these numbers is {'even' if even else 'odd'}")), N(r, lg),
                 [N(o, lg) for o in sorted(others)], ask(lg, f"Un nombre est pair s'il finit par 0, 2, 4, 6 ou 8 : {N(r, lg)} finit par {r % 10}.",
                                                          f"A number is even if it ends in 0, 2, 4, 6 or 8; {N(r, lg)} ends in {r % 10}."))


def double_half(rng, d, g, lg):
    m = hi(g, d)
    k = rng.randint(max(2, m // 20), max(3, m // 2 - 1))
    dbl = rng.random() < 0.5
    if dbl:
        r = 2 * k
        t = ask(lg, f"Quel est le double de {N(k, lg)}", f"What is double {N(k, lg)}")
        e = f"2 × {N(k, lg)} = {N(r, lg)}."
        w = [k + 2, 3 * k, k, 2 * k + 10, 2 * k - 2, k * 4]
    else:
        r = k
        t = ask(lg, f"Quelle est la moitié de {N(2 * k, lg)}", f"What is half of {N(2 * k, lg)}")
        e = f"{N(2 * k, lg)} ÷ 2 = {N(k, lg)}."
        w = [k + 1, k - 1, 2 * k - 2, 4 * k, k // 2 + 1, k + 10]
    return Draft(q_end(lg, t), N(r, lg), [N(x, lg) for x in w if x != r and x > 0], e)


def story_add(rng, d, g, lg):
    nm = name(rng, lg)
    a, b = spair(rng, g, d)
    a = max(a, 2)
    obj_fr, obj_en = rng.choice([("billes", "marbles"), ("mangues", "mangoes"), ("oranges", "oranges"), ("livres", "books"), ("bananes", "bananas"), ("images", "pictures"),
                                 ("œufs", "eggs"), ("poules", "hens"), ("bonbons", "sweets")])
    r = a + b
    t = ask(lg, f"{nm} a {N(a, lg)} {obj_fr} et en reçoit {N(b, lg)} de plus. Combien cela fait-il maintenant",
            f"{nm} has {N(a, lg)} {obj_en} and receives {N(b, lg)} more. How many are there now")
    return Draft(q_end(lg, t), N(r, lg), op_wrongs(rng, r, lg, [abs(a - b)]), f"{N(a, lg)} + {N(b, lg)} = {N(r, lg)}.")


def story_sub(rng, d, g, lg):
    nm = name(rng, lg)
    a, b = spair(rng, g, d)
    a, b = max(a, b), min(a, b)
    if a == b:
        return None
    obj_fr, obj_en = rng.choice([("billes", "marbles"), ("mangues", "mangoes"), ("oranges", "oranges"), ("livres", "books"), ("bananes", "bananas"), ("images", "pictures"),
                                 ("œufs", "eggs"), ("bonbons", "sweets"), ("crayons", "pencils")])
    r = a - b
    t = ask(lg, f"{nm} a {N(a, lg)} {obj_fr} et en perd {N(b, lg)}. Combien lui en reste-t-il",
            f"{nm} has {N(a, lg)} {obj_en} and loses {N(b, lg)}. How many are left")
    return Draft(q_end(lg, t), N(r, lg), op_wrongs(rng, r, lg, [a + b]), f"{N(a, lg)} − {N(b, lg)} = {N(r, lg)}.")


def story_mul(rng, d, g, lg):
    nm = name(rng, lg)
    k = rng.randint(2, {2: 5, 3: 9, 4: 12, 5: 25, 6: 40}.get(g, 40) * (1 if d < 4 else 2))
    per = rng.randint(2, {2: 5, 3: 9, 4: 15, 5: 40, 6: 60}.get(g, 60))
    what_fr, what_en = rng.choice([("rangées", "rows", "plants de manioc", "cassava plants"), ("paniers", "baskets", "mangues", "mangoes"),
                                   ("sachets", "bags", "bonbons", "sweets"), ("boîtes", "boxes", "stylos", "pens"), ("tables", "tables", "élèves", "pupils")])[:4], None
    a, b, c, e = what_fr
    r = k * per
    t = ask(lg, f"Il y a {k} {a} de {per} {c} chacun{'e' if a in ('rangées', 'boîtes', 'tables') else ''}. Combien de {c} y a-t-il en tout",
            f"There are {k} {b} with {per} {e} in each. How many {e} are there altogether")
    return Draft(q_end(lg, t), N(r, lg), op_wrongs(rng, r, lg, [k + per, r + per]), f"{k} × {per} = {N(r, lg)}.")


def story_share(rng, d, g, lg):
    nm = name(rng, lg)
    parts = rng.randint(2, {3: 6, 4: 9, 5: 12, 6: 12}.get(g, 12))
    each = rng.randint(2, {2: 10, 3: 12, 4: 25, 5: 60, 6: 120}.get(g, 120))
    tot = parts * each
    what_fr, what_en = rng.choice([("billes", "marbles"), ("bonbons", "sweets"), ("mangues", "mangoes"), ("crayons", "pencils"), ("cahiers", "exercise books")])
    t = ask(lg, f"{nm} partage {N(tot, lg)} {what_fr} en parts égales entre {parts} enfants. Combien chaque enfant reçoit-il de {what_fr}",
            f"{nm} shares {N(tot, lg)} {what_en} equally among {parts} children. How many {what_en} does each child get")
    return Draft(q_end(lg, t), N(each, lg), op_wrongs(rng, each, lg, [parts, tot - parts]), f"{N(tot, lg)} ÷ {parts} = {N(each, lg)}.")


def money_total(rng, d, g, lg):
    nm = name(rng, lg)
    it, pl, price = item(rng, lg)
    n = rng.randint(2, 3 + 2 * d)
    t = price * n
    q = ask(lg, f"{nm} achète {n} {pl} à {money(price, lg)} l'unité. Combien cela coûte-t-il en tout", f"{nm} buys {n} {pl} at {money(price, lg)} each. How much does it cost altogether")
    return Draft(q_end(lg, q), money(t, lg), [money(v, lg) for v in plaus(rng, t, 0, None, price)] + [money(price + n, lg)], f"{n} × {N(price, lg)} = {N(t, lg)} FCFA.")


def money_change(rng, d, g, lg):
    nm = name(rng, lg)
    it, pl, price = item(rng, lg)
    n = rng.randint(1, 2 + d)
    total = price * n
    bill = next((b for b in (500, 1000, 2000, 5000, 10000, 20000) if b > total), None)
    if bill is None:
        return None
    r = bill - total
    what = it if n == 1 else f"{n} {pl}"
    q = ask(lg, f"{nm} achète {what} pour {money(total, lg)} et paie avec {money(bill, lg)}. Combien lui rend-on", f"{nm} buys {what} for {money(total, lg)} and pays with {money(bill, lg)}. How much change does {nm} get")
    return Draft(q_end(lg, q), money(r, lg), [money(v, lg) for v in plaus(rng, r, 1, None, 50)] + [money(bill + total, lg)], f"{N(bill, lg)} − {N(total, lg)} = {N(r, lg)} FCFA.")


COINS = [(100, 5), (50, 10), (500, 2), (25, 4), (10, 10), (5, 20)]


def coins(rng, d, g, lg):
    nm = name(rng, lg)
    big = rng.choice([100, 500, 1000, 2000, 5000]) if g >= 2 else rng.choice([100, 500])
    small = rng.choice([x for x in (25, 50, 100, 500) if x < big and big % x == 0 and big // x <= 20 + 20 * d])
    k = big // small
    kind_fr = ("pièces" if small <= 500 else "billets")
    t = ask(lg, f"Combien faut-il de pièces de {money(small, lg)} pour faire {money(big, lg)}", f"How many coins of {money(small, lg)} are needed to make {money(big, lg)}")
    return Draft(q_end(lg, t), N(k, lg), [N(v, lg) for v in plaus(rng, k, 1)], f"{N(big, lg)} ÷ {N(small, lg)} = {N(k, lg)}.")


def time_units(rng, d, g, lg):
    kinds = [("de jours", "days", "semaines", "weeks", 7), ("d'heures", "hours", "jours", "days", 24), ("de minutes", "minutes", "heures", "hours", 60), ("de mois", "months", "années", "years", 12),
             ("de secondes", "seconds", "minutes", "minutes", 60)]
    sm_fr, sm_en, bg_fr, bg_en, k = kinds[rng.randrange(min(len(kinds), 2 + d))] if g >= 3 else kinds[rng.randrange(2)] if g == 2 else kinds[0]
    n = rng.randint(2, 4 + 2 * d)
    r = n * k
    t = ask(lg, f"Combien y a-t-il {sm_fr} dans {n} {bg_fr}", f"How many {sm_en} are there in {n} {bg_en}")
    return Draft(q_end(lg, t), N(r, lg), [N(v, lg) for v in plaus(rng, r, 1)] + [N(n + k, lg)], f"{n} × {k} = {N(r, lg)}.")


def clock_add(rng, d, g, lg):
    h, m = rng.randint(1, 11), rng.choice([0, 15, 30, 45] if d <= 2 else [5, 10, 20, 25, 35, 40, 50, 55, 15, 30, 45])
    add = rng.choice([15, 30, 45, 60, 90, 120] if d <= 3 else [20, 35, 50, 75, 105, 140])
    tot = h * 60 + m + add
    rh, rm = divmod(tot, 60)
    rh = ((rh - 1) % 12) + 1

    def fmt(hh, mm):
        return f"{hh} h {mm:02d}" if lg == "fr" else f"{hh}:{mm:02d}"
    def shift(delta):
        t2 = (rh - 1) * 60 + rm + delta
        return fmt((t2 // 60) % 12 + 1, t2 % 60)
    wr = [shift(v) for v in (-60, 60, 10, -10, 30, 15, -15, 120)]
    t = ask(lg, f"Il est {fmt(h, m)}. Quelle heure sera-t-il dans {add} minutes", f"It is {fmt(h, m)}. What time will it be in {add} minutes")
    return Draft(q_end(lg, t), fmt(rh, rm), wr, ask(lg, f"{fmt(h, m)} + {add} min = {fmt(rh, rm)}.", f"{fmt(h, m)} + {add} min = {fmt(rh, rm)}."))


LEN = [("m", "cm", 100), ("km", "m", 1000), ("cm", "mm", 10), ("kg", "g", 1000), ("L", "cL", 100), ("m", "dm", 10), ("t", "kg", 1000)]


def units(rng, d, g, lg):
    big, small, k = rng.choice(LEN[: 2 if g <= 3 else (4 if g == 4 else 7)])
    n = rng.randint(2, 20 if d < 3 else 99)
    r = n * k
    wr = [n * k * 10, n * k // 10, n + k, n * (k // 10 if k >= 10 else 1) * 100]
    t = ask(lg, f"Combien y a-t-il de {small} dans {n} {big}", f"How many {small} are there in {n} {big}")
    return Draft(q_end(lg, t), f"{N(r, lg)} {small}", [f"{N(w, lg)} {small}" for w in wr if w != r and w > 0], f"1 {big} = {N(k, lg)} {small} ; {n} × {N(k, lg)} = {N(r, lg)}.")


def perimeter(rng, d, g, lg):
    shape = rng.choice(["rect", "sq"] + (["tri"] if g >= 4 else []))
    k = 4 + 5 * d
    if shape == "rect":
        L, l = rng.randint(4, k + 6), rng.randint(2, k)
        L, l = max(L, l + 1), min(l, L - 1)
        p = 2 * (L + l)
        t = ask(lg, f"Un rectangle mesure {L} cm de long et {l} cm de large. Quel est son périmètre", f"A rectangle is {L} cm long and {l} cm wide. What is its perimeter")
        w = [p - L, L * l, p + 2, L + l]
        e = f"2 × ({L} + {l}) = {p} cm."
    elif shape == "sq":
        c = rng.randint(2, k + 4)
        p = 4 * c
        t = ask(lg, f"Un carré a un côté de {c} cm. Quel est son périmètre", f"A square has a side of {c} cm. What is its perimeter")
        w = [c * c, 2 * c, p + 4, c + 4]
        e = f"4 × {c} = {p} cm."
    else:
        a, b, c = rng.randint(4, k + 6), rng.randint(4, k + 6), rng.randint(4, k + 6)
        if not (a < b + c and b < a + c and c < a + b):
            return None
        p = a + b + c
        t = ask(lg, f"Un triangle a pour côtés {a} cm, {b} cm et {c} cm. Quel est son périmètre", f"A triangle has sides {a} cm, {b} cm and {c} cm. What is its perimeter")
        w = [p - 1, p + 2, a * b, 2 * p]
        e = f"{a} + {b} + {c} = {p} cm."
    return Draft(q_end(lg, t), f"{p} cm", [f"{x} cm" for x in w if x > 0 and x != p], e)


def area(rng, d, g, lg):
    shape = rng.choice(["rect", "sq"] + (["tri"] if g >= 5 else []))
    k = 3 + 4 * d
    if shape == "rect":
        L, l = rng.randint(3, k + 6), rng.randint(2, k)
        a = L * l
        t = ask(lg, f"Un rectangle mesure {L} m de long et {l} m de large. Quelle est son aire", f"A rectangle is {L} m long and {l} m wide. What is its area")
        w, e = [2 * (L + l), a + L, a - l, a * 2], f"{L} × {l} = {a} m²."
    elif shape == "sq":
        c = rng.randint(2, k + 4)
        a = c * c
        t = ask(lg, f"Un carré a un côté de {c} m. Quelle est son aire", f"A square has a side of {c} m. What is its area")
        w, e = [4 * c, 2 * c, a + c, a - c], f"{c} × {c} = {a} m²."
    else:
        b, h = rng.randint(4, k + 6) * 2, rng.randint(3, k + 3)
        a = b * h // 2
        t = ask(lg, f"Un triangle a une base de {b} m et une hauteur de {h} m. Quelle est son aire", f"A triangle has a base of {b} m and a height of {h} m. What is its area")
        w, e = [b * h, a + h, b + h, a * 2 + 2], f"{b} × {h} ÷ 2 = {a} m²."
    return Draft(q_end(lg, t), f"{a} m²", [f"{x} m²" for x in w if x > 0 and x != a], e)


def fraction_of(rng, d, g, lg):
    opts = [(1, 2), (1, 4), (1, 3), (3, 4), (2, 3), (1, 5), (2, 5), (3, 5), (1, 10), (3, 10), (7, 10)]
    n, dn = rng.choice(opts[: 2 + 2 * (d if g < 5 else d + 1)])
    base = dn * rng.randint(2, 6 * d + 4)
    r = base // dn * n
    name_fr = {(1, 2): "la moitié", (1, 3): "le tiers", (1, 4): "le quart"}.get((n, dn))
    name_en = {(1, 2): "half", (1, 3): "one third", (1, 4): "one quarter"}.get((n, dn))
    if name_fr:
        t = ask(lg, f"Quel est {name_fr} de {N(base, lg)}" if name_fr != "la moitié" else f"Quelle est la moitié de {N(base, lg)}", f"What is {name_en} of {N(base, lg)}")
    else:
        t = ask(lg, f"Que valent les {n}/{dn} de {N(base, lg)}", f"What is {n}/{dn} of {N(base, lg)}")
    return Draft(q_end(lg, t), N(r, lg), [N(v, lg) for v in (base // dn, base - r, r + base // dn, base // dn * (n + 1), r * 2) if v != r and v > 0],
                 f"{N(base, lg)} ÷ {dn} × {n} = {N(r, lg)}.")


def same_den(rng, d, g, lg):
    dn = rng.choice([4, 5, 6, 8, 10, 12] if d > 1 else [3, 4, 5, 10])
    a, b = rng.randint(1, dn - 2), rng.randint(1, dn - 2)
    plus = rng.random() < 0.6 or a <= b
    s = a + b if plus else a - b
    if s <= 0 or (plus and s >= dn * 2):
        return None
    right = f"{s}/{dn}"
    wr = [f"{s}/{dn * 2}", f"{a + b}/{dn + dn}" if plus else f"{abs(a - b)}/{dn - dn // 2}", f"{s + 1}/{dn}", f"{max(s - 1, 1)}/{dn}", f"{dn}/{s}"]
    t = ask(lg, f"Combien font {a}/{dn} {'+' if plus else '−'} {b}/{dn}", f"What is {a}/{dn} {'+' if plus else '−'} {b}/{dn}")
    return Draft(q_end(lg, t), right, [w for w in wr if w != right], ask(lg, f"Même dénominateur : on {'additionne' if plus else 'soustrait'} les numérateurs : {a} {'+' if plus else '−'} {b} = {s}, donc {s}/{dn}.",
                                                                      f"Same denominator: {'add' if plus else 'subtract'} the numerators: {a} {'+' if plus else '−'} {b} = {s}, so {s}/{dn}."),
                 alt=[f"{s // math.gcd(s, dn)}/{dn // math.gcd(s, dn)}"] if math.gcd(s, dn) > 1 else ())


def simplify(rng, d, g, lg):
    n, dn = rng.choice([(1, 2), (1, 3), (2, 3), (3, 4), (1, 4), (3, 5), (2, 5), (4, 5), (5, 6), (3, 8), (5, 8), (7, 10), (1, 6), (5, 12), (7, 9), (7, 8), (11, 12)])
    k = rng.randint(2, 3 + d)
    a, b = n * k, dn * k
    wr = [f"{n + 1}/{dn}", f"{n}/{dn + 1}", f"{dn}/{n}", f"{n + 1}/{dn + 1}", f"{a // 2 if a % 2 == 0 else a + 1}/{b // 2 if b % 2 == 0 else b}"]
    t = ask(lg, f"Quelle est la forme irréductible de la fraction {a}/{b}", f"What is {a}/{b} in its simplest form")
    return Draft(q_end(lg, t), f"{n}/{dn}", [w for w in wr if w != f"{n}/{dn}"], ask(lg, f"On divise {a} et {b} par {k} : {n}/{dn}, qui ne se simplifie plus.", f"Divide {a} and {b} by {k}: {n}/{dn}, which cannot be simplified further."))


def compare_fr(rng, d, g, lg):
    dn = rng.choice([4, 5, 8, 10] if d <= 3 else [6, 7, 9, 12])
    ns = rng.sample(range(1, dn), 4) if dn > 5 else rng.sample(range(1, dn + 1), min(4, dn))
    if len(ns) < 4:
        return None
    big = rng.random() < 0.5
    right = max(ns) if big else min(ns)
    sh = ns[:]
    rng.shuffle(sh)
    t = ask(lg, f"Quelle est la plus {'grande' if big else 'petite'} de ces fractions : {' ; '.join(f'{x}/{dn}' for x in sh)}",
            f"Which is the {'greatest' if big else 'smallest'} of these fractions: {', '.join(f'{x}/{dn}' for x in sh)}")
    return Draft(q_end(lg, t), f"{right}/{dn}", [f"{x}/{dn}" for x in ns if x != right], ask(lg, "Même dénominateur : la plus grande fraction a le plus grand numérateur.", "Same denominator: the greatest fraction has the greatest numerator."))


def dec_compare(rng, d, g, lg):
    base = rng.randint(0, 9)
    vals = set()
    while len(vals) < 4:
        vals.add(round(base + rng.randint(1, 99) / 100, 2) if d <= 3 else round(base + rng.randint(1, 999) / 1000, 3))
    vals = sorted(vals)
    big = rng.random() < 0.5
    right = vals[-1] if big else vals[0]
    sh = vals[:]
    rng.shuffle(sh)
    t = ask(lg, f"Quel est le plus {'grand' if big else 'petit'} de ces nombres : {' ; '.join(N(v, lg) for v in sh)}", f"Which is the {'greatest' if big else 'smallest'} of these numbers: {', '.join(N(v, lg) for v in sh)}")
    return Draft(q_end(lg, t), N(right, lg), [N(v, lg) for v in vals if v != right],
                 ask(lg, "On compare les parties entières, puis les dixièmes, centièmes, millièmes.", "Compare the whole parts, then tenths, hundredths, thousandths."))


def dec_add(rng, d, g, lg):
    dec = 1 if d < 3 else 2
    a = round(rng.uniform(1, 10 ** (1 + d // 2)), dec)
    b = round(rng.uniform(1, 10 ** (1 + d // 2)), dec)
    sub_ = rng.random() < 0.5 and a > b
    r = round(a - b if sub_ else a + b, 3)
    from ..core import near_floats
    return Draft(q_end(lg, ask(lg, f"Combien font {N(a, lg, dec)} {'−' if sub_ else '+'} {N(b, lg, dec)}", f"What is {N(a, lg, dec)} {'−' if sub_ else '+'} {N(b, lg, dec)}")),
                 N(r, lg, dec), [N(v, lg, dec) for v in near_floats(rng, r, dec)], ask(lg, "On aligne les virgules.", "Line up the decimal points."))


def times10(rng, d, g, lg):
    x = round(rng.uniform(0.01, 99.99) if d < 3 else rng.uniform(0.001, 999.999), 2 if d < 3 else 3)
    k = rng.choice([10, 100, 1000])
    div_ = rng.random() < 0.4
    r = round(x / k if div_ else x * k, 6)
    wr = [round(x / (k * 10) if div_ else x * k * 10, 6), round(x * k if div_ else x / k, 6), round(r * 10, 6), round(r / 10, 6)]
    op = "÷" if div_ else "×"
    return Draft(q_end(lg, ask(lg, f"Combien font {N(x, lg, 3)} {op} {N(k, lg)}", f"What is {N(x, lg, 3)} {op} {N(k, lg)}")), N(r, lg, 6),
                 [N(v, lg, 6) for v in wr if v != r], ask(lg, f"{op} {N(k, lg)} déplace la virgule de {len(str(k)) - 1} rang(s) vers la {'gauche' if div_ else 'droite'}.",
                                                          f"{op} {N(k, lg)} moves the decimal point {len(str(k)) - 1} place(s) to the {'left' if div_ else 'right'}."))


def rounding(rng, d, g, lg):
    x = rng.randint(1000, 99999) if g <= 4 and d < 5 else round(rng.uniform(1, 999), 3)
    if isinstance(x, int):
        base = rng.choice([10, 100, 1000][: 1 + min(2, d // 2)])
        r = int((x + base / 2) // base * base)
        lab = {10: ("à la dizaine", "to the nearest ten"), 100: ("à la centaine", "to the nearest hundred"), 1000: ("au millier", "to the nearest thousand")}[base]
        wr = [r + base, r - base, x // base * base if x // base * base != r else r + 2 * base, (x // base + 1) * base if (x // base + 1) * base != r else r - 2 * base]
        return Draft(q_end(lg, ask(lg, f"Quel est l'arrondi de {N(x, lg)} {lab[0]} près", f"What is {N(x, lg)} rounded {lab[1]}")), N(r, lg), [N(v, lg) for v in wr if v != r],
                     ask(lg, f"On regarde le chiffre suivant : {N(x, lg)} → {N(r, lg)}.", f"Look at the next digit: {N(x, lg)} → {N(r, lg)}."))
    dec = rng.choice([0, 1, 2][: 1 + min(2, d // 2)])
    r = math.floor(x * 10 ** dec + 0.5 + 1e-9) / 10 ** dec
    lab = [("à l'unité", "to the nearest whole number"), ("au dixième", "to one decimal place"), ("au centième", "to two decimal places")][dec]
    right = N(r if dec else int(r), lg, dec if dec else None)
    step = 10 ** -dec
    wr = [r + step, r - step, math.floor(x * 10 ** dec) / 10 ** dec, math.ceil(x * 10 ** dec) / 10 ** dec]
    return Draft(q_end(lg, ask(lg, f"Quel est l'arrondi de {N(x, lg, 3)} {lab[0]} près", f"What is {N(x, lg, 3)} rounded {lab[1]}")), right,
                 [N(v if dec else int(v), lg, dec if dec else None) for v in wr if abs(v - r) > 1e-9], ask(lg, f"On regarde le chiffre suivant : {right}.", f"Look at the next digit: {right}."))


def prop_simple(rng, d, g, lg):
    nm = name(rng, lg)
    it, pl, price = item(rng, lg)
    n1, n2 = rng.randint(2, 6), rng.randint(7, 12 + d)
    total1 = price * n1
    r = price * n2
    t = ask(lg, f"{n1} {pl} coûtent {money(total1, lg)}. Combien coûtent {n2} {pl}", f"{n1} {pl} cost {money(total1, lg)}. How much do {n2} {pl} cost")
    return Draft(q_end(lg, t), money(r, lg), [money(v, lg) for v in (price * (n2 - 1), price * (n2 + 1), total1 + n2, total1 * 2, r + price * 2) if v != r],
                 ask(lg, f"Un seul coûte {N(price, lg)} FCFA ; {n2} × {N(price, lg)} = {N(r, lg)} FCFA.", f"One costs {N(price, lg)} FCFA; {n2} × {N(price, lg)} = {N(r, lg)} FCFA."))


def mean(rng, d, g, lg):
    k = rng.choice([3, 4, 5])
    m = rng.randint(5, 10 * d + 10)
    vals = [m + rng.randint(-4, 6) for _ in range(k - 1)]
    last = m * k - sum(vals)
    if last < 1:
        return None
    vals.append(last)
    rng.shuffle(vals)
    return Draft(q_end(lg, ask(lg, f"Quelle est la moyenne de ces notes : {' ; '.join(map(str, vals))}", f"What is the mean (average) of these marks: {', '.join(map(str, vals))}")),
                 N(m, lg), [N(v, lg) for v in (m + 1, m - 1, m + 2, max(vals), min(vals), sum(vals)) if v != m and v > 0],
                 ask(lg, f"Somme = {sum(vals)} ; {sum(vals)} ÷ {k} = {m}.", f"Sum = {sum(vals)}; {sum(vals)} ÷ {k} = {m}."))


def three_step(rng, d, g, lg):
    nm = name(rng, lg)
    (i1, pl1, p1), (i2, pl2, p2) = item(rng, lg), item(rng, lg)
    if p1 == p2:
        return None
    n1, n2 = rng.randint(1, 3 + d), rng.randint(1, 3 + d)
    spent = n1 * p1 + n2 * p2
    start = (spent // 1000 + 1) * 1000 + rng.choice([0, 500, 1000])
    left = start - spent
    t = ask(lg, f"{nm} a {money(start, lg)} et achète {n1} {pl1} à {money(p1, lg)} l'unité et {n2} {pl2} à {money(p2, lg)} l'unité. Combien lui reste-t-il",
            f"{nm} has {money(start, lg)} and buys {n1} {pl1} at {money(p1, lg)} each and {n2} {pl2} at {money(p2, lg)} each. How much is left")
    return Draft(q_end(lg, t), money(left, lg), [money(v, lg) for v in plaus(rng, left, 0, None, 50)] + [money(spent, lg)],
                 f"{n1} × {N(p1, lg)} + {n2} × {N(p2, lg)} = {N(spent, lg)} ; {N(start, lg)} − {N(spent, lg)} = {N(left, lg)} FCFA.")


def euclid(rng, d, g, lg):
    b = rng.randint(3, 8 + 2 * d)
    q = rng.randint(3, 8 * d + 5)
    r = rng.randint(1, b - 1)
    a = q * b + r
    ask_rest = rng.random() < 0.5
    right = r if ask_rest else q
    t = ask(lg, f"Quel est le {'reste' if ask_rest else 'quotient entier'} de la division de {N(a, lg)} par {b}", f"What is the {'remainder' if ask_rest else 'whole-number quotient'} when {N(a, lg)} is divided by {b}")
    wr = [x for x in range(0, b) if x != right][:5] + [q if ask_rest else r, right + 1, right - 1]
    return Draft(q_end(lg, t), N(right, lg), [N(x, lg) for x in wr if x >= 0 and x != right], f"{N(a, lg)} = {b} × {q} + {r}.")


def mult_table_missing(rng, d, g, lg):
    a = rng.randint(2, 9)
    b = rng.randint(2, 9)
    r = a * b
    return Draft(q_end(lg, ask(lg, f"Dans {a} × … = {r}, quel est le nombre manquant", f"In {a} × … = {r}, what is the missing number")), str(b),
                 [str(x) for x in range(1, 13) if x != b][:8], f"{r} ÷ {a} = {b}.")


def order_ops(rng, d, g, lg):
    a, b, c = rng.randint(2, 9), rng.randint(2, 9), rng.randint(2, 9)
    form = rng.choice(["a+b*c", "a*b-c", "(a+b)*c"])
    if form == "a+b*c":
        txt, r = f"{a} + {b} × {c}", a + b * c
        wr = [(a + b) * c]
    elif form == "a*b-c":
        if a * b <= c:
            return None
        txt, r = f"{a} × {b} − {c}", a * b - c
        wr = [a * (b - c) if b > c else a * b + c]
    else:
        txt, r = f"({a} + {b}) × {c}", (a + b) * c
        wr = [a + b * c]
    wr += [r + 1, r - 1, r + 2, r + c]
    return Draft(q_end(lg, ask(lg, f"Combien fait {txt}", f"What is {txt}")), N(r, lg), [N(w, lg) for w in wr if w != r and w > 0],
                 ask(lg, "On fait d'abord les parenthèses, puis les multiplications, puis les additions et soustractions.", "Brackets first, then multiplication, then addition and subtraction."))


def reg_all():
    reg("add", add, PRIM, 190)
    reg("sub", sub, PRIM, 190)
    reg("mul", mul, P2, 190)
    reg("div", div, P2, 180)
    reg("missing-add", missing_add, PRIM, 170)
    reg("missing-sub", missing_sub, PRIM, 150)
    reg("biggest", biggest, PRIM, 150)
    reg("next-prev", next_prev, PRIM, 130, cat="Numération")
    reg("digit-place", digit_place, PRIM, 200, cat="Numération")
    reg("compose", compose, PRIM, 150, cat="Numération")
    reg("even-odd", even_odd, PRIM, 120, cat="Numération")
    reg("double-half", double_half, PRIM, 150)
    reg("story-add", story_add, PRIM, 190, cat="Problèmes")
    reg("story-sub", story_sub, PRIM, 190, cat="Problèmes")
    reg("story-mul", story_mul, P2, 190, cat="Problèmes")
    reg("story-share", story_share, P3, 190, cat="Problèmes")
    reg("money-total", money_total, P2, 170, cat="Argent")
    reg("money-change", money_change, P2, 170, cat="Argent")
    reg("coins", coins, PRIM, 80, cat="Argent")
    reg("time-units", time_units, PRIM, 90, cat="Temps")
    reg("clock-add", clock_add, P3, 140, cat="Temps")
    reg("units", units, P3, 160, cat="Mesures")
    reg("perimeter", perimeter, P3, 160, cat="Géométrie")
    reg("area", area, P4, 160, cat="Géométrie")
    reg("fraction-of", fraction_of, P3, 190, cat="Fractions")
    reg("same-den", same_den, P4, 140, cat="Fractions")
    reg("simplify", simplify, P4, 100, cat="Fractions")
    reg("compare-fr", compare_fr, P4, 90, cat="Fractions")
    reg("dec-compare", dec_compare, P4, 190, cat="Nombres décimaux")
    reg("dec-add", dec_add, P4, 190, cat="Nombres décimaux")
    reg("times10", times10, P4, 190, cat="Nombres décimaux")
    reg("rounding", rounding, P4, 190, cat="Nombres décimaux")
    reg("prop-simple", prop_simple, P3, 170, cat="Proportionnalité")
    reg("mean", mean, [c for c in PRIM if c in ("cm1", "class4", "class5", "class6")], 160, cat="Statistiques")
    reg("three-step", three_step, P3, 180, cat="Problèmes")
    reg("euclid", euclid, P3, 160)
    reg("table-missing", mult_table_missing, P2, 60)
    reg("order-ops", order_ops, [c for c in PRIM if c in ("cm1", "class4", "class5", "class6")], 140)


reg_all()
