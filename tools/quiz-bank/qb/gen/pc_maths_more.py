"""Compléments de mathématiques : écriture des nombres en lettres, chiffres romains, suites, rangements, calendrier, heures,
comparaison de longueurs, monnaie. Bilingue (fr / en). Réponses calculées."""
from ..core import Draft
from .cm2_french import PERS  # noqa: F401  (keeps the import graph identical to the other modules)
from .pc_common import FR_COURSES, N, ask, money, name, plaus, q_end, reg
from .primary import roman, words_fr

PRIM = ["cp", "ce1", "ce2", "cm1", "class1", "class2", "class3", "class4", "class5", "class6"]
P2 = [c for c in PRIM if c not in ("cp", "class1")]
P3 = [c for c in P2 if c not in ("ce1", "class2")]
SEC = ["6e", "5e", "4e", "form1", "form2", "form3"]
ALL = PRIM + SEC


def words_en(n):
    U = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"]
    T = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]
    if n < 20:
        return U[n]
    if n < 100:
        t, u = divmod(n, 10)
        return T[t] + ("" if u == 0 else "-" + U[u])
    if n < 1000:
        h, r = divmod(n, 100)
        return U[h] + " hundred" + ("" if r == 0 else " and " + words_en(r))
    th, r = divmod(n, 1000)
    return words_en(th) + " thousand" + ("" if r == 0 else (" and " + words_en(r) if r < 100 else " " + words_en(r)))


def wd(n, lg):
    return words_fr(n) if lg == "fr" else words_en(n)


def top(g, d):
    return {1: [20, 30, 60, 80, 99], 2: [60, 99, 200, 500, 999], 3: [99, 500, 999, 5000, 9999], 4: [99, 999, 5000, 9999, 99999], 5: [999, 5000, 9999, 99999, 999999],
            6: [999, 9999, 99999, 999999, 999999], 7: [9999, 99999, 999999, 999999, 999999], 8: [9999, 99999, 999999, 999999, 999999], 9: [9999, 99999, 999999, 999999, 999999]}[g][d - 1]


def num_to_words(rng, d, g, lg):
    m = top(g, d)
    n = rng.randint(max(11, m // 10), m)
    if lg == "fr" and n % 1000 == 0 and n >= 1000:
        return None
    ws = [wd(v, lg) for v in (n + 1, n - 1, n + 10, n - 10, n + 100 if n > 200 else n + 7, int(str(n)[::-1]) if int(str(n)[::-1]) != n and int(str(n)[::-1]) > 10 else n + 3) if v > 0 and v != n]
    return Draft(q_end(lg, ask(lg, f"Comment écrit-on {N(n, lg)} en lettres", f"How do we write {N(n, lg)} in words")), wd(n, lg), ws, ask(lg, f"{N(n, lg)} s'écrit « {wd(n, lg)} ».", f"{N(n, lg)} is written '{wd(n, lg)}'."))


def words_to_num(rng, d, g, lg):
    m = top(g, d)
    n = rng.randint(max(11, m // 10), m)
    if lg == "fr" and n % 1000 == 0 and n >= 1000:
        return None
    wr = [v for v in (n + 1, n - 1, n + 10, n - 10, int(str(n)[::-1]) if int(str(n)[::-1]) != n else n + 100, n * 10 if n < 1000 else n + 100) if v > 0 and v != n]
    return Draft(q_end(lg, ask(lg, f"Quel nombre s'écrit « {wd(n, lg)} »", f"Which number is written '{wd(n, lg)}'")), N(n, lg), [N(v, lg) for v in wr],
                 ask(lg, f"« {wd(n, lg)} » s'écrit {N(n, lg)} en chiffres.", f"'{wd(n, lg)}' is {N(n, lg)} in figures."))


def roman_q(rng, d, g, lg):
    n = rng.randint(2, 20 if d <= 2 else 99 if d <= 4 else 399)
    r = roman(n)
    if rng.random() < 0.5:
        wr = [roman(v) for v in (n + 1, n - 1, n + 5, n - 5, n + 10, n - 10) if v > 0 and roman(v) != r]
        return Draft(q_end(lg, ask(lg, f"Comment écrit-on {n} en chiffres romains", f"How is {n} written in Roman numerals")), r, wr, f"{n} s'écrit {r} en chiffres romains." if lg == "fr" else f"{n} is {r} in Roman numerals.")
    vals = [v for v in (n + 1, n - 1, n + 5, n - 5, n + 10, n - 10, n * 2) if v > 0 and v != n]
    return Draft(q_end(lg, ask(lg, f"Quel nombre représente {r} en chiffres romains", f"Which number does {r} represent")), str(n), [str(v) for v in vals], ask(lg, f"En chiffres romains, {r} correspond au nombre {n}.", f"In Roman numerals, {r} stands for the number {n}."))


def sequence(rng, d, g, lg):
    kinds = ["add"] + (["mul"] if g >= 4 and d >= 3 else []) + (["sub"] if g >= 2 else [])
    kind = rng.choice(kinds)
    if kind == "mul":
        a, r = rng.randint(1, 5), rng.choice([2, 3])
        seq = [a * r ** i for i in range(5)]
        step_txt = ask(lg, f"on multiplie par {r}", f"multiply by {r}")
    else:
        stp = rng.choice([1, 2, 3, 5, 10] if g <= 2 else [2, 3, 4, 5, 6, 7, 8, 9, 10, 25, 50, 100, 12, 15])
        a = rng.randint(1, 30 * g)
        if kind == "sub":
            a = a + stp * 5
            stp = -stp
        seq = [a + stp * i for i in range(5)]
        step_txt = ask(lg, f"on {'ajoute' if stp > 0 else 'retranche'} {abs(stp)} à chaque fois", f"{'add' if stp > 0 else 'subtract'} {abs(stp)} each time")
    if min(seq) < 0:
        return None
    nxt = seq[4]
    shown = ", ".join(N(v, lg) for v in seq[:4])
    last_step = seq[4] - seq[3]
    wr = [nxt + abs(seq[1] - seq[0]) if kind != "mul" else nxt * 2, nxt - 1, nxt + 1, nxt + 2, seq[3] + (seq[3] - seq[2]) * 2 if kind != "mul" else seq[3] * 2, nxt + 10, nxt - 10]
    return Draft(q_end(lg, ask(lg, f"Quel nombre continue la suite : {shown}, …", f"Which number continues the sequence: {shown}, …")), N(nxt, lg), [N(v, lg) for v in dict.fromkeys(wr) if v != nxt and v >= 0],
                 ask(lg, f"Dans cette suite {step_txt} : après {N(seq[3], lg)} vient {N(nxt, lg)}.", f"In this sequence we {step_txt}: after {N(seq[3], lg)} comes {N(nxt, lg)}."))


def seq_missing(rng, d, g, lg):
    stp = rng.choice([2, 3, 4, 5, 10, 6, 7, 8, 9, 20, 25, 50])
    a = rng.randint(1, 20 * g)
    seq = [a + stp * i for i in range(6)]
    hole = rng.randint(1, 4)
    shown = ", ".join("…" if i == hole else N(v, lg) for i, v in enumerate(seq))
    r = seq[hole]
    return Draft(q_end(lg, ask(lg, f"Quel nombre remplace les points dans la suite : {shown}", f"Which number replaces the dots in the sequence: {shown}")), N(r, lg),
                 [N(v, lg) for v in (r + 1, r - 1, r + stp, r - stp, r + 2, r + 10) if v != r and v > 0], ask(lg, f"On ajoute {stp} à chaque fois : le terme manquant est {N(r, lg)}.", f"We add {stp} each time: the missing term is {N(r, lg)}."))


def ordering(rng, d, g, lg):
    m = top(g, d)
    vals = rng.sample(range(max(1, m // 10), m), 4)
    asc = rng.random() < 0.5
    srt = sorted(vals, reverse=not asc)
    right = " < ".join(N(v, lg) for v in srt) if asc else " > ".join(N(v, lg) for v in srt)
    sym = "<" if asc else ">"
    shown = ", ".join(N(v, lg) for v in vals)
    wr = []
    import itertools
    perms = list(itertools.permutations(srt))
    import random
    rr = random.Random(shown)
    rr.shuffle(perms)
    for p in perms:
        s = f" {sym} ".join(N(v, lg) for v in p)
        if s != right and len(wr) < 5 and sum(1 for a, b in zip(p, srt) if a == b) <= 2:
            wr.append(s)
    return Draft(q_end(lg, ask(lg, f"Comment ranger du {'plus petit au plus grand' if asc else 'plus grand au plus petit'} les nombres {shown}", f"How do we arrange the numbers {shown} from {'smallest to greatest' if asc else 'greatest to smallest'}")),
                 right, wr, ask(lg, f"On compare les nombres : {right}.", f"Comparing the numbers: {right}."))


DAYS_FR = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
DAYS_EN = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
MONTHS_FR = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
MONTHS_EN = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
MDAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
ORD_FR = ["premier", "deuxième", "troisième", "quatrième", "cinquième", "sixième", "septième", "huitième", "neuvième", "dixième", "onzième", "douzième"]
ORD_EN = ["first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth"]


def calendar(rng, d, g, lg):
    days, months, ords = (DAYS_FR, MONTHS_FR, ORD_FR) if lg == "fr" else (DAYS_EN, MONTHS_EN, ORD_EN)
    kind = rng.choice(["day-after", "day-before", "month-after", "month-before", "days-in", "nth-month", "nth-day"] if d >= 2 else ["day-after", "day-before", "month-after", "days-in"])
    if kind in ("day-after", "day-before"):
        i = rng.randrange(7)
        after = kind == "day-after"
        r = days[(i + (1 if after else -1)) % 7]
        t = ask(lg, f"Quel jour vient juste {'après' if after else 'avant'} {days[i]}", f"Which day comes just {'after' if after else 'before'} {days[i]}")
        wr = [x for x in days if x != r and x != days[i]]
        return Draft(q_end(lg, t), r, wr, ask(lg, f"Les jours de la semaine : {', '.join(days)}.", f"The days of the week: {', '.join(days)}."))
    if kind in ("month-after", "month-before"):
        i = rng.randrange(12)
        after = kind == "month-after"
        r = months[(i + (1 if after else -1)) % 12]
        t = ask(lg, f"Quel mois vient juste {'après' if after else 'avant'} {months[i]}", f"Which month comes just {'after' if after else 'before'} {months[i]}")
        wr = [months[(i + k) % 12] for k in (2, -2, 3, 6) if months[(i + k) % 12] != r]
        return Draft(q_end(lg, t), r, wr, ask(lg, f"Les mois de l'année : {', '.join(months)}.", f"The months of the year: {', '.join(months)}."))
    if kind == "days-in":
        i = rng.choice([k for k in range(12) if k != 1])
        r = MDAYS[i]
        t = ask(lg, f"Combien de jours y a-t-il en {months[i]}" if months[i][0] not in "aeiouéhAÉ" else f"Combien de jours y a-t-il en {months[i]}", f"How many days are there in {months[i]}")
        return Draft(q_end(lg, t), str(r), [str(x) for x in (28, 29, 30, 31, 32) if x != r][:4], ask(lg, f"{months[i]} compte {r} jours.", f"{months[i]} has {r} days."))
    if kind == "nth-month":
        i = rng.randrange(12)
        t = ask(lg, f"Quel est le {ords[i]} mois de l'année", f"Which is the {ords[i]} month of the year")
        return Draft(q_end(lg, t), months[i], [months[(i + k) % 12] for k in (1, -1, 2, 3)], ask(lg, f"Le {ords[i]} mois est {months[i]}.", f"The {ords[i]} month is {months[i]}."))
    i = rng.randrange(7)
    t = ask(lg, f"Quel est le {ords[i]} jour de la semaine (en commençant par lundi)", f"Which is the {ords[i]} day of the week (starting from Monday)")
    return Draft(q_end(lg, t), days[i], [days[(i + k) % 7] for k in (1, -1, 2, 3)], ask(lg, f"En commençant par lundi, le {ords[i]} jour est {days[i]}.", f"Starting from Monday, the {ords[i]} day is {days[i]}."))


def clock_hands(rng, d, g, lg):
    h = rng.randint(1, 12)
    mm, hand = rng.choice([(0, 12), (15, 3), (30, 6), (45, 9)])

    def fmt(hh, m_):
        return f"{hh} h {m_:02d}" if lg == "fr" else f"{hh}:{m_:02d}"
    r = fmt(h, mm)
    wr = [fmt(h, (mm + 30) % 60), fmt(h % 12 + 1, mm), fmt((h - 2) % 12 + 1, mm), fmt(hand if hand != h else 5, h * 5 % 60), fmt(h, (mm + 15) % 60)]
    t = ask(lg, f"La petite aiguille est sur le {h} et la grande aiguille est sur le {hand}. Quelle heure est-il", f"The short hand points to {h} and the long hand points to {hand}. What time is it")
    return Draft(q_end(lg, t), r, [x for x in dict.fromkeys(wr) if x != r], ask(lg, f"La grande aiguille sur le {hand} indique {mm} minutes : {r}.", f"The long hand on {hand} means {mm} minutes: {r}."))


def longest(rng, d, g, lg):
    base = rng.randint(2, 9)
    items = [(f"{base} m", base * 100), (f"{base * 100 + rng.randint(10, 90)} cm", None), (f"{base * 10} dm", base * 100), (f"{rng.randint(1, 9)} km", None)]
    vals = []
    seen = set()
    while len(vals) < 4:
        k = rng.choice(["m", "cm", "dm", "mm"])
        v = rng.randint(2, 900 if k in ("cm", "mm") else 99)
        cm = {"m": v * 100, "cm": v, "dm": v * 10, "mm": v / 10}[k]
        if cm in seen:
            continue
        seen.add(cm)
        vals.append((f"{N(v, lg)} {k}", cm))
    big = rng.random() < 0.5
    right = max(vals, key=lambda x: x[1]) if big else min(vals, key=lambda x: x[1])
    t = ask(lg, f"Quelle est la plus {'grande' if big else 'petite'} de ces longueurs : {' ; '.join(v[0] for v in vals)}", f"Which is the {'longest' if big else 'shortest'} of these lengths: {', '.join(v[0] for v in vals)}")
    srt = sorted(vals, key=lambda x: x[1])
    return Draft(q_end(lg, t), right[0], [v[0] for v in vals if v[0] != right[0]], ask(lg, "On convertit tout dans la même unité (en cm : " + " ; ".join(f"{N(round(v[1], 1), lg, 1)}" for v in vals) + ").", "Convert everything to the same unit (in cm: " + ", ".join(f"{N(round(v[1], 1), lg, 1)}" for v in vals) + ")."))


def money_mix(rng, d, g, lg):
    a, b = rng.randint(1, 4 + d), rng.randint(1, 5)
    ua, ub = rng.choice([(1000, 500), (500, 100), (2000, 500), (5000, 500), (1000, 100), (5000, 100), (500, 50), (100, 25) if g >= 2 else (100, 50)])
    if ua <= ub:
        return None
    tot = a * ua + b * ub
    t = ask(lg, f"{name(rng, lg)} a {a} billet{'s' if a > 1 else ''} de {money(ua, lg)} et {b} pièce{'s' if b > 1 else ''} de {money(ub, lg)}. Combien cela fait-il en tout" if ua >= 1000 else f"{name(rng, lg)} a {a} pièce{'s' if a > 1 else ''} de {money(ua, lg)} et {b} pièce{'s' if b > 1 else ''} de {money(ub, lg)}. Combien cela fait-il en tout",
            f"{name(rng, lg)} has {a} note{'s' if a > 1 else ''} of {money(ua, lg)} and {b} coin{'s' if b > 1 else ''} of {money(ub, lg)}. How much is that altogether" if ua >= 1000 else f"{name(rng, lg)} has {a} coin{'s' if a > 1 else ''} of {money(ua, lg)} and {b} coin{'s' if b > 1 else ''} of {money(ub, lg)}. How much is that altogether")
    return Draft(q_end(lg, t), money(tot, lg), [money(v, lg) for v in plaus(rng, tot, 1, None, ub)] + [money(a * ub + b * ua, lg)], f"{a} × {N(ua, lg)} + {b} × {N(ub, lg)} = {N(tot, lg)} FCFA.")


def reg_all():
    reg("num-to-words", num_to_words, ALL[:9] + ["6e", "form1"], 260, cat="Numération")
    reg("words-to-num", words_to_num, ALL[:9] + ["6e", "form1"], 260, cat="Numération")
    reg("roman", roman_q, [c for c in ALL if c in ("cm1", "class4", "class5", "class6", "6e", "form1", "5e", "form2")], 120, cat="Numération")
    reg("sequence", sequence, P2 + SEC[:3], 240, cat="Numération")
    reg("sequence-gap", seq_missing, PRIM + ["6e", "form1"], 240, cat="Numération")
    reg("ordering", ordering, PRIM, 220, cat="Numération")
    reg("calendar", calendar, PRIM + ["6e", "form1"], 200, cat="Temps")
    reg("clock-hands", clock_hands, [c for c in PRIM if c in ("ce1", "ce2", "cm1", "class2", "class3", "class4")], 40, cat="Temps")
    reg("longest", longest, P3, 160, cat="Mesures")
    reg("money-mix", money_mix, P2, 200, cat="Argent")


reg_all()
