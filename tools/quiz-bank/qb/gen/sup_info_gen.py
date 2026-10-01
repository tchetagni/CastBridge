"""Informatique L1, L2, L3 : générateurs calculés.

Chaque réponse est obtenue par simulation (boucles, structures de données, sqlite3, ipaddress, heapq, pow...) et recoupée par une
seconde méthode (assert) ; un assert qui échoue est un bogue du générateur, jamais une question.
L1 : représentation des nombres, logique, unités, images, algorithmique simple.
L2 : réseaux IPv4, RAID, systèmes (ordonnancement, pagination, cache), SQL / relationnel, tableaux, récursivité, traces.
L3 : complexité, tris, recherche, arbres, graphes, hachage, cryptographie jouet, Huffman, automates, piles/files, notation polonaise inversée.
"""
import heapq
import ipaddress
import itertools
import math
import sqlite3
import string
from collections import Counter, deque
from fractions import Fraction

from ..core import CAP_SCALE, NB, Draft, fr, near_ints
from ..core import gen as _gen
from .mathfmt import sup

CAP_SCALE["l3-info"] = 1.6

C1, C2, C3 = "l1-info", "l2-info", "l3-info"
# Réglage du volume : les plafonds `cap` ci-dessous sont multipliés par ce facteur pour viser environ 5 000 questions par parcours
# (le minimum exigé est 4 500, aucun modèle ne dépasse 8 % du parcours).
VOLUME = {C1: 1.0, C2: 0.85, C3: 0.5}


def gen(course, tpl, cap=250, **kw):
    """Comme core.gen, avec le réglage de volume et un nettoyage commun : une mauvaise réponse qui commence par un zéro
    (nombre écrit à l'envers) alors que la bonne n'en a pas est écartée, car elle serait manifestement fausse."""
    def deco(fn):
        def wrapped(rng, d):
            r = fn(rng, d)
            if r is not None and not r.right.startswith("0"):
                r.wrongs = [w for w in r.wrongs if not (len(w) > 1 and w.isdigit() and w.startswith("0"))]
            return r
        wrapped.__module__ = fn.__module__
        wrapped.__name__ = fn.__name__
        _gen(course, tpl, cap=max(10, int(cap * VOLUME[course])), **kw)(wrapped)
        return fn
    return deco

S1 = "Cours d'introduction à l'informatique (L1) - réponse calculée et recoupée"
S2 = "Cours d'informatique de L2 (réseaux, systèmes, bases de données, programmation) - réponse calculée et recoupée"
S3 = "Cours d'informatique de L3 (algorithmique, structures de données, cryptographie) - réponse calculée et recoupée"


# ------------------------------------------------------------------------------------------------ outils communs
def ints(rng, v, n=6, lo=None, hi=None):
    return [fr(x) for x in near_ints(rng, int(v), n, lo, hi)]


def dec(x, d=2):
    return fr(float(x), d)


def exact(x, d=2):
    """True si la fraction x s'écrit avec au plus d décimales."""
    return (Fraction(x) * 10 ** d).denominator == 1


def bs(v, w=0):
    return format(v, "b").zfill(w)


def pct(x):
    return fr(int(x)) + NB + "%"


def lst(a):
    return "[" + ", ".join(str(x) for x in a) + "]"


def ok_set(*xs):
    return [x for x in xs]


# ================================================================================================ L1 INFORMATIQUE
CN, CL, CS, CR, CI, CA = ("Représentation des nombres", "Logique booléenne", "Stockage et unités", "Réseaux et débits",
                          "Images et multimédia", "Algorithmique de base")


@gen(C1, "inf1-bin-dec", cap=90, cat=CN)
def bin_dec(rng, d):
    nb = rng.randint(3 + d, 5 + d + d // 2)
    v = rng.randint(1 << (nb - 1), (1 << nb) - 1)
    s = bs(v)
    assert v == sum(2 ** i for i, c in enumerate(reversed(s)) if c == "1") == int(s, 2)
    poids = " + ".join(str(2 ** i) for i, c in reversed(list(enumerate(reversed(s)))) if c == "1")
    return Draft(f"Quelle est la valeur décimale du nombre binaire {s} ?", fr(v),
                 [fr(x) for x in (int(s[::-1], 2), v + 1, v - 1, v + 2, v - 2, v * 2, v // 2)],
                 f"{s} en base 2 vaut {poids} = {fr(v)}.", src=S1)


@gen(C1, "inf1-dec-bin", cap=90, cat=CN)
def dec_bin(rng, d):
    nb = rng.randint(3 + d, 5 + d + d // 2)
    v = rng.randint(1 << (nb - 1), (1 << nb) - 1)
    s = bs(v)
    q, digs = v, ""
    while q:
        digs = str(q % 2) + digs
        q //= 2
    assert digs == s and int(s, 2) == v
    i = rng.randrange(len(s))
    flip = s[:i] + ("1" if s[i] == "0" else "0") + s[i + 1:]
    return Draft(f"Comment s'écrit {fr(v)} en binaire (base 2) ?", s,
                 [flip, s[::-1], bs(v + 1), bs(v - 1), s[1:] + s[0], bs(v + 2)],
                 f"Divisions successives par 2 : les restes lus de bas en haut donnent {s}.", src=S1)


@gen(C1, "inf1-hex-dec", cap=90, cat=CN)
def hex_dec(rng, d):
    v = rng.randint(16, 255 if d < 3 else 4095)
    h = format(v, "X")
    tot = 0
    for ch in h:
        tot = tot * 16 + "0123456789ABCDEF".index(ch)
    assert tot == v
    return Draft(f"Quelle est la valeur décimale du nombre hexadécimal {h} ?", fr(v),
                 [fr(x) for x in (int(h[::-1], 16), v + 16, v - 16, v + 1, v - 1, v + 256)],
                 f"{h} en base 16 : on additionne chaque chiffre multiplié par la puissance de 16 correspondante, soit {fr(v)}.", src=S1)


@gen(C1, "inf1-dec-hex", cap=90, cat=CN)
def dec_hex(rng, d):
    v = rng.randint(16, 255 if d < 3 else 4095)
    h = format(v, "X")
    q, out = v, ""
    while q:
        out = "0123456789ABCDEF"[q % 16] + out
        q //= 16
    assert out == h
    return Draft(f"Comment s'écrit {fr(v)} en hexadécimal (chiffres 0 à 9 puis A à F) ?", h,
                 [format(v + 1, "X"), format(v - 1, "X"), h[::-1], format(v + 16, "X"), format(v - 16, "X"), format(v, "o")],
                 f"Divisions successives par 16, restes lus de bas en haut (10 = A … 15 = F) : {h}.", src=S1)


@gen(C1, "inf1-octal", cap=90, cat=CN)
def octal(rng, d):
    v = rng.randint(8, 63 if d < 3 else 1023)
    o = format(v, "o")
    assert int(o, 8) == v
    if rng.random() < 0.5:
        return Draft(f"Quelle est la valeur décimale du nombre octal {o} (base 8) ?", fr(v),
                     [fr(x) for x in (int(o[::-1], 8), int(o), v + 1, v - 1, v + 8, v - 8)],
                     f"{o} en base 8 vaut {fr(v)} (chaque chiffre est multiplié par la puissance de 8 de son rang).", src=S1)
    return Draft(f"Comment s'écrit {fr(v)} en octal (base 8) ?", o,
                 [format(v + 1, "o"), format(v - 1, "o"), o[::-1], format(v + 8, "o"), format(v - 8, "o"), format(v, "b")],
                 f"Divisions successives par 8, restes lus de bas en haut : {o}.", src=S1)


@gen(C1, "inf1-bin-hex", cap=90, cat=CN)
def bin_hex(rng, d):
    kind = rng.choice(["bh", "hb", "bo"])
    if kind == "bh":
        nb = rng.choice([8, 8, 12])
        while True:
            v = rng.randrange(1 << (nb - 4), 1 << nb)
            if (v >> (nb - 4)) != 0:
                break
        s = bs(v, nb)
        h = "".join(format(int(s[i:i + 4], 2), "X") for i in range(0, nb, 4))
        assert h == format(v, "X")
        sw = h[1] + h[0] + h[2:] if len(h) >= 2 else h
        i = rng.randrange(len(h))
        alt = h[:i] + format((int(h[i], 16) + rng.choice([1, 2, 15])) % 16, "X") + h[i + 1:]
        return Draft(f"Comment s'écrit en hexadécimal le nombre binaire {s} (groupes de 4 bits) ?", h,
                     [sw, alt, h[::-1], format(v + 1, "X")],
                     f"On regroupe les bits par 4 depuis la droite : {' '.join(s[i:i + 4] for i in range(0, nb, 4))} donne {h}.", src=S1)
    if kind == "hb":
        k = rng.choice([2, 2, 3])
        v = rng.randrange(1 << (4 * k - 4), 1 << (4 * k))
        h = format(v, "X")
        s = "".join(bs(int(c, 16), 4) for c in h)
        assert int(s, 2) == v
        i = rng.randrange(len(s))
        flip = s[:i] + ("1" if s[i] == "0" else "0") + s[i + 1:]
        return Draft(f"Quelle est l'écriture binaire de l'hexadécimal {h}, à raison de 4 bits par chiffre hexadécimal ?", s,
                     [flip, s[::-1], bs(v + 1, len(s)), s[4:] + s[:4]],
                     f"Chaque chiffre hexadécimal devient un groupe de 4 bits : {' '.join(s[i:i + 4] for i in range(0, len(s), 4))}.", src=S1)
    nb = rng.choice([6, 9])
    v = rng.randrange(1 << (nb - 3), 1 << nb)
    s = bs(v, nb)
    o = "".join(str(int(s[i:i + 3], 2)) for i in range(0, nb, 3))
    assert o == format(v, "o")
    sw = o[::-1]
    return Draft(f"Comment s'écrit en octal le nombre binaire {s} (groupes de 3 bits) ?", o,
                 [sw, format(v + 1, "o"), format(v - 1, "o"), format(v, "X")],
                 f"On regroupe les bits par 3 depuis la droite : {' '.join(s[i:i + 3] for i in range(0, nb, 3))} donne {o}.", src=S1)


@gen(C1, "inf1-twos-neg", cap=90, cat=CN)
def twos_neg(rng, d):
    v = rng.randint(1, 127)
    r = bs((-v) & 0xFF, 8)
    inv = bs((~v) & 0xFF, 8)
    assert bs(((~v) & 0xFF) + 1 & 0xFF, 8) == r and int(r, 2) - 256 == -v
    return Draft(f"Sur 8 bits en complément à deux, comment s'écrit −{v} ?", r,
                 ["1" + bs(v, 7), inv, bs(v, 8), bs(((-v) + 1) & 0xFF, 8), bs(((-v) - 1) & 0xFF, 8)],
                 f"On inverse les bits de {bs(v, 8)} ({inv}) puis on ajoute 1 : {r}.", src=S1)


@gen(C1, "inf1-twos-val", cap=90, cat=CN)
def twos_val(rng, d):
    b = rng.randint(128, 255) if rng.random() < 0.8 else rng.randint(1, 127)
    s = bs(b, 8)
    val = -(256 - b) if s[0] == "1" else b
    assert val == b - 256 * int(s[0])
    return Draft(f"Quelle est la valeur décimale de l'octet {s} lu en complément à deux sur 8 bits ?", fr(val),
                 [fr(x) for x in (b, -(b & 127), b - 255, 256 - b, -b, b - 257) if x != val],
                 f"Bit de poids fort {s[0]} : valeur = {b} − {256 * int(s[0])} = {fr(val)}." if s[0] == "1" else f"Le bit de poids fort est 0 : le nombre est positif et vaut {b}.", src=S1)


@gen(C1, "inf1-twos-range", cap=40, cat=CN)
def twos_range(rng, d):
    n = rng.randint(3, 20)
    lo, hi = -(1 << (n - 1)), (1 << (n - 1)) - 1
    assert hi - lo + 1 == 2 ** n
    if rng.random() < 0.5:
        return Draft(f"Sur {n} bits en complément à deux, quelle est la plus petite valeur entière représentable ?", fr(lo),
                     [fr(x) for x in (-(1 << (n - 1)) + 1, -(1 << n), -(1 << (n - 1)) - 1, -(1 << n) + 1, -(1 << (n - 2)))],
                     f"La plage va de −2^{n - 1} à 2^{n - 1} − 1, donc le minimum est {fr(lo)}.", src=S1)
    return Draft(f"Sur {n} bits en complément à deux, quelle est la plus grande valeur entière représentable ?", fr(hi),
                 [fr(x) for x in ((1 << (n - 1)), (1 << n) - 1, (1 << (n - 1)) - 2, (1 << n), (1 << (n - 2)) - 1)],
                 f"La plage va de −2^{n - 1} à 2^{n - 1} − 1, donc le maximum est {fr(hi)}.", src=S1)


@gen(C1, "inf1-bin-add", cap=90, cat=CN)
def bin_add(rng, d):
    nb = rng.randint(3 + d // 2, 4 + d)
    a, b = rng.randint(3, (1 << nb) - 1), rng.randint(3, (1 << nb) - 1)
    sa, sb = bs(a, nb), bs(b, nb)
    carry, out = 0, ""
    for x, y in zip(reversed(sa), reversed(sb)):
        t = int(x) + int(y) + carry
        out, carry = str(t % 2) + out, t // 2
    if carry:
        out = "1" + out
    assert int(out, 2) == a + b
    return Draft(f"Quel est le résultat de l'addition binaire {sa} + {sb} (sans limite de largeur) ?", out,
                 [bs(a ^ b), bs(a | b), bs((a + b) & ((1 << nb) - 1), nb), bs(a + b + 1), bs(a + b - 1), bs(abs(a - b))],
                 f"{a} + {b} = {a + b}, soit {out} en binaire (retenues propagées de droite à gauche).", src=S1)


@gen(C1, "inf1-bitwise", cap=90, cat=CN)
def bitwise(rng, d):
    w = rng.choice([4, 6, 8]) if d < 3 else 8
    a, b = rng.randrange(1 << w), rng.randrange(1 << w)
    sa, sb = bs(a, w), bs(b, w)
    op = rng.choice(["ET", "OU", "OU EXCLUSIF (XOR)"])
    f = {"ET": lambda x, y: x & y, "OU": lambda x, y: x | y, "OU EXCLUSIF (XOR)": lambda x, y: x ^ y}
    res = bs(f[op](a, b), w)
    per = "".join(str(int(f[op](int(x), int(y)))) for x, y in zip(sa, sb))
    assert per == res
    wr = [bs(f[o](a, b), w) for o in f if o != op] + [bs((~f[op](a, b)) & ((1 << w) - 1), w), bs(a + b & ((1 << w) - 1), w)]
    return Draft(f"Quel est le résultat de {sa} {op} {sb}, calculé bit à bit ?", res, wr,
                 f"On applique l'opération {op} bit par bit : {res}.", src=S1)


# ---- logique
def rnd_expr(rng, vs, depth, ops):
    if depth == 0 or rng.random() < 0.25:
        return ("var", rng.choice(vs), rng.random() < 0.4)
    a, b = rnd_expr(rng, vs, depth - 1, ops), rnd_expr(rng, vs, depth - 1, ops)
    if a[0] == "var" and b[0] == "var" and a[1] == b[1]:
        b = ("var", rng.choice([v for v in vs if v != a[1]]), b[2])
    return ("op", rng.choice(ops), a, b)


def bev(t, env):
    if t[0] == "var":
        return (not env[t[1]]) if t[2] else env[t[1]]
    a, b = bev(t[2], env), bev(t[3], env)
    return (a and b) if t[1] == "ET" else (a or b) if t[1] == "OU" else (a != b)


def bshow(t, top=True):
    if t[0] == "var":
        return ("¬" if t[2] else "") + t[1]
    s = f"{bshow(t[2], False)} {t[1] if t[1] != 'XOR' else 'OU EXCLUSIF'} {bshow(t[3], False)}"
    return s if top else "(" + s + ")"


def bpy(t):
    if t[0] == "var":
        return f"(not {t[1]})" if t[2] else t[1]
    o = {"ET": "and", "OU": "or", "XOR": "!="}[t[1]]
    return f"({bpy(t[2])} {o} {bpy(t[3])})"


def bvars(t):
    return {t[1]} if t[0] == "var" else bvars(t[2]) | bvars(t[3])


def make_expr(rng, d):
    ops = ["ET", "OU"] if d < 3 else ["ET", "OU", "XOR"]
    for _ in range(60):
        t = rnd_expr(rng, ["A", "B", "C"], 2 if d < 4 else 3, ops)
        if t[0] != "var" and bvars(t) == {"A", "B", "C"}:
            return t
    return None


def rows3():
    return [dict(A=bool(a), B=bool(b), C=bool(c)) for a in (0, 1) for b in (0, 1) for c in (0, 1)]


@gen(C1, "inf1-bool-combo", cap=100, cat=CL)
def bool_combo(rng, d):
    t = make_expr(rng, d)
    if t is None:
        return None
    rows = rows3()
    for r in rows:
        assert bev(t, r) == eval(bpy(t), {}, dict(r))
    tr = [r for r in rows if bev(t, r)]
    fa = [r for r in rows if not bev(t, r)]
    if not tr or len(fa) < 3:
        return None
    right = rng.choice(tr)
    wr = rng.sample(fa, 3)
    fm = lambda r: ", ".join(f"{k} = {int(v)}" for k, v in r.items())
    return Draft(f"Soit F = {bshow(t)} (¬ désigne la négation). Pour quelles valeurs de A, B, C la fonction F vaut-elle 1 (vrai) ?", fm(right),
                 [fm(w) for w in wr], f"En évaluant F pour {fm(right)}, on obtient 1 ; pour les trois autres propositions F vaut 0.", src=S1)


@gen(C1, "inf1-bool-count", cap=90, cat=CL)
def bool_count(rng, d):
    t = make_expr(rng, d)
    if t is None:
        return None
    n = sum(bev(t, r) for r in rows3())
    assert n == sum(eval(bpy(t), {}, dict(r)) for r in rows3())
    return Draft(f"Soit F = {bshow(t)} (¬ désigne la négation). Combien de lignes de sa table de vérité (8 lignes) donnent 1 ?", str(n),
                 [str(x) for x in near_ints(rng, n, 6, lo=0, hi=8)],
                 f"On évalue F sur les 8 combinaisons de A, B, C : {n} d'entre elles donnent 1.", src=S1)


def lit_show(v, neg):
    return f"(NON {v})" if neg else v


@gen(C1, "inf1-demorgan", cap=90, cat=CL)
def demorgan(rng, d):
    k = 2 if d < 4 else 3
    vs = rng.sample(["P", "Q", "R", "S", "X", "Y"], k)
    negs = [rng.random() < 0.5 for _ in vs]
    op = rng.choice(["ET", "OU"])
    sw = "OU" if op == "ET" else "ET"
    orig = list(zip(vs, negs))

    def val(op_, forms, env):
        vals = [env[v] != n for v, n in forms]
        return all(vals) if op_ == "ET" else any(vals)

    forms = {
        "right": (sw, [(v, not n) for v, n in orig]),
        "keep": (op, [(v, not n) for v, n in orig]),
        "swap-only": (sw, orig),
        "one": (sw, [(v, (not n) if i == 0 else n) for i, (v, n) in enumerate(orig)]),
        "one2": (op, [(v, (not n) if i == 0 else n) for i, (v, n) in enumerate(orig)]),
    }
    envs = [dict(zip(vs, bits)) for bits in itertools.product([False, True], repeat=k)]
    for e in envs:
        assert (not val(op, orig, e)) == val(*forms["right"], e)

    def show(o, fs):
        return f" {o} ".join(lit_show(v, n) for v, n in fs)

    wr = []
    for key in ("keep", "swap-only", "one", "one2"):
        o, fs = forms[key]
        if any(val(o, fs, e) != (not val(op, orig, e)) for e in envs):
            wr.append(show(o, fs))
    return Draft(f"Quelle expression est équivalente à NON ({show(op, orig)}) d'après De Morgan (NON désigne la négation) ?", show(*forms["right"]),
                 wr, "De Morgan : la négation d'un ET devient un OU des négations (et inversement), chaque terme étant nié.", src=S1)


WORDS = {"<": "est inférieur à", ">": "est supérieur à", "=": "est égal à", "≠": "est différent de", "≤": "est inférieur ou égal à", "≥": "est supérieur ou égal à"}
NEG = {"<": "≥", ">": "≤", "=": "≠", "≠": "=", "≤": ">", "≥": "<"}
PY = {"<": "<", ">": ">", "=": "==", "≠": "!=", "≤": "<=", "≥": ">="}


@gen(C1, "inf1-neg-cond", cap=130, cat=CL)
def neg_cond(rng, d):
    names = rng.sample(["x", "y", "n", "age", "note", "t", "a", "b"], 2 if d < 4 else 3)
    conds = [(nm, rng.choice(list(NEG)), rng.randint(1, 20)) for nm in names]
    op = rng.choice(["ET", "OU"])
    sw = "OU" if op == "ET" else "ET"
    txt = lambda cs, o: f" {o} ".join(f"{a} {WORDS[c]} {v}" for a, c, v in cs)
    right = [(a, NEG[c], v) for a, c, v in conds]

    def ev(cs, o, env):
        r = [eval(f"{env[a]} {PY[c]} {v}") for a, c, v in cs]
        return all(r) if o == "ET" else any(r)
    pts = sorted({0, 25} | {v + k for _, _, v in conds for k in (-1, 0, 1)})
    grid = [dict(zip(names, vals)) for vals in itertools.product(pts, repeat=len(names))]
    for e in grid:
        assert ev(conds, op, e) != ev(right, sw, e)
    first = [(a, NEG[c], v) if i == 0 else (a, c, v) for i, (a, c, v) in enumerate(conds)]
    last = [(a, c, v) if i == 0 else (a, NEG[c], v) for i, (a, c, v) in enumerate(conds)]
    cands = [(right, op), (conds, sw), (first, sw), (last, sw), (first, op)]
    wr = [txt(cs, o) for cs, o in cands if any(ev(cs, o, e) != ev(right, sw, e) for e in grid)]
    return Draft(f"Quelle est la négation de la condition « {txt(conds, op)} » ?", txt(right, sw), wr,
                 "On nie chaque comparaison (inférieur à devient supérieur ou égal à, égal à devient différent de…) et on échange ET et OU.", src=S1)


# ---- stockage
def unit_lbl(base):
    return ("1 Ko = 1 024 octets, 1 Mo = 1 024 Ko, 1 Go = 1 024 Mo" if base == 1024
            else "1 Ko = 1 000 octets, 1 Mo = 1 000 Ko, 1 Go = 1 000 Mo")


@gen(C1, "inf1-stor-conv", cap=110, cat=CS)
def stor_conv(rng, d):
    base = rng.choice([1024, 1000])
    other = 1000 if base == 1024 else 1024
    ui = {"octets": 0, "Ko": 1, "Mo": 2, "Go": 3}
    big = rng.choice(["Ko", "Mo", "Go"] if d > 2 else ["Ko", "Mo"])
    small = rng.choice([u for u in ui if ui[u] < ui[big]])
    pw = ui[big] - ui[small]
    k = rng.randint(2, 40 if d > 2 else 12)
    n_small = k * base ** pw
    assert n_small // base ** pw == k and n_small % base ** pw == 0
    conv = f"Avec les conventions {unit_lbl(base)}"
    if rng.random() < 0.5:
        wr = [k * other ** pw, n_small * base, n_small // base if n_small % base == 0 else n_small + 1, k * base ** (pw + 1) if pw < 3 else n_small + k, n_small - 1]
        return Draft(f"{conv}, combien {"d'octets" if small == "octets" else "de " + small} contiennent {k} {big} ?", fr(n_small),
                     [fr(x) for x in wr], f"{k} {big} = {k} × {fr(base ** pw)} {small} = {fr(n_small)} {small}.", src=S1)
    wr = [k + 1, k - 1, k * base, k * 2, (k * base ** pw * base) // base ** (pw + 1) if False else k + 10, n_small // other ** pw if n_small % other ** pw == 0 else k + 2]
    return Draft(f"{conv}, {fr(n_small)} {small} représentent combien de {big} ?", str(k),
                 [fr(x) for x in wr if x > 0], f"{fr(n_small)} {small} ÷ {fr(base ** pw)} = {k} {big}.", src=S1)


@gen(C1, "inf1-stor-bits", cap=90, cat=CS)
def stor_bits(rng, d):
    unit = rng.choice(["octets", "Ko", "Mo"])
    base = rng.choice([1024, 1000])
    k = rng.randint(2, 500)
    mult = {"octets": 1, "Ko": base, "Mo": base * base}[unit]
    right = k * mult * 8
    assert right == k * mult * 2 ** 3
    ctx = "" if unit == "octets" else f" (1 Ko = {fr(base)} octets" + (f", 1 Mo = {fr(base)} Ko" if unit == "Mo" else "") + ")"
    return Draft(f"Un fichier pèse {fr(k)} {unit}{ctx}. Combien de bits contient-il ?", fr(right),
                 [fr(x) for x in (k * mult, k * mult * 10, k * mult * 16, k * mult // 8 if (k * mult) % 8 == 0 else k * mult * 4, right + 8, right - 8)],
                 f"1 octet = 8 bits : {fr(k)} × {fr(mult)} × 8 = {fr(right)} bits.", src=S1)


@gen(C1, "inf1-stor-fit", cap=100, cat=CS)
def stor_fit(rng, d):
    base = rng.choice([1000, 1024])
    cap_go = rng.choice([4, 8, 16, 32, 64, 128, 256])
    f = rng.randint(3, 900)
    total = cap_go * base
    right = total // f
    alt = cap_go * (1000 if base == 1024 else 1024) // f
    assert right * f <= total < (right + 1) * f
    return Draft(f"Une clé de {cap_go} Go (1 Go = {fr(base)} Mo) reçoit des fichiers de {f} Mo chacun. Combien de fichiers entiers peut-elle contenir ?", fr(right),
                 [fr(x) for x in (right + 1, right - 1, alt, cap_go // f + 1, round(total / f) + 2, right + 2) if x != right],
                 f"{fr(total)} Mo ÷ {f} Mo = {total / f:.2f}…, donc {fr(right)} fichiers entiers.".replace(".", ","), src=S1)


@gen(C1, "inf1-dl-time", cap=100, cat=CR)
def dl_time(rng, d):
    D = rng.choice([2, 4, 5, 8, 10, 16, 20, 25, 40, 50, 100])
    big = rng.random() < 0.3
    for _ in range(20):
        S = rng.randint(10, 900) if not big else rng.randint(1, 20)
        if (8 * S * (1000 if big else 1)) % D == 0:
            break
    else:
        return None
    mo = S * (1000 if big else 1)
    t = Fraction(8 * mo, D)
    assert t == Fraction(mo * 8 * 10 ** 6, D * 10 ** 6)
    sz = f"{S} Go (1 Go = 1 000 Mo)" if big else f"{S} Mo"
    return Draft(f"On télécharge un fichier de {sz} avec un débit constant de {D} Mbit/s (1 octet = 8 bits). Quelle est la durée du téléchargement, en secondes ?", fr(int(t)),
                 [fr(int(x)) for x in (Fraction(mo, D), Fraction(mo * D, 8), Fraction(mo, 8 * D), t * 2, t + 8, t // 2) if Fraction(x).denominator == 1 and x != t] +
                 [dec(x) for x in (Fraction(mo, D), Fraction(mo * D, 8), Fraction(mo, 8 * D)) if Fraction(x).denominator != 1],
                 f"Taille en Mbit : {mo} × 8 = {fr(8 * mo)} ; durée = {fr(8 * mo)} ÷ {D} = {fr(int(t))} s.", src=S1)


@gen(C1, "inf1-dl-rate", cap=90, cat=CR)
def dl_rate(rng, d):
    T = rng.choice([2, 4, 5, 8, 10, 16, 20, 25, 40, 50])
    D = rng.randint(2, 100)
    mo = D * T
    if mo % 8:
        return None
    S = mo // 8
    assert Fraction(S * 8, T) == D
    return Draft(f"Un fichier de {S} Mo est téléchargé en {T} secondes à débit constant. Quel est ce débit, en Mbit/s (1 octet = 8 bits) ?", fr(D),
                 [dec(x) for x in (Fraction(S, T), Fraction(S * T, 8), S * T, Fraction(S * 8, T) * 8, Fraction(S, T) / 8) if x != D] + [fr(D + 8), fr(D * 2)],
                 f"Débit = ({S} × 8) ÷ {T} = {fr(D)} Mbit/s.", src=S1)


@gen(C1, "inf1-nvalues", cap=50, cat=CN)
def nvalues(rng, d):
    n = rng.randint(2, 20)
    if rng.random() < 0.5:
        assert len(range(2 ** n)) == 2 ** n if n <= 16 else True
        return Draft(f"Combien de valeurs distinctes peut-on représenter avec {n} bits ?", fr(2 ** n),
                     [fr(x) for x in (2 ** n - 1, 2 * n, n * n, 2 ** (n - 1), 2 ** (n + 1), 10 ** n if n < 6 else 2 ** n + 1)],
                     f"Chaque bit a 2 états : 2^{n} = {fr(2 ** n)} valeurs.", src=S1)
    return Draft(f"Quel est le plus grand entier non signé que l'on peut écrire sur {n} bits ?", fr(2 ** n - 1),
                 [fr(x) for x in (2 ** n, 2 ** (n - 1), 2 ** (n - 1) - 1, 2 ** n - 2, 2 * n - 1, n * n - 1)],
                 f"Les valeurs vont de 0 à 2^{n} − 1 = {fr(2 ** n - 1)}.", src=S1)


@gen(C1, "inf1-minbits", cap=130, cat=CN)
def minbits(rng, d):
    N = rng.randint(3, 40 if d < 3 else 5000)
    n = 0
    while 2 ** n < N:
        n += 1
    assert n == (N - 1).bit_length() and 2 ** (n - 1) < N <= 2 ** n
    obj = rng.choice(["couleurs distinctes", "caractères distincts", "états distincts", "références de produits distinctes", "numéros d'utilisateurs distincts"])
    return Draft(f"Combien de bits faut-il au minimum pour coder {fr(N)} {obj} par un entier de longueur fixe ?", str(n),
                 [str(x) for x in (n - 1, n + 1, n + 2, max(1, N // 8), N.bit_length() + 1, n * 2) if x != n and x > 0],
                 f"Il faut le plus petit n tel que 2^n ≥ {fr(N)} : n = {n}.", src=S1)


@gen(C1, "inf1-ascii-code", cap=90, cat=CN)
def ascii_code(rng, d):
    up = rng.random() < 0.6
    base = 65 if up else 97
    L = rng.choice(string.ascii_uppercase if up else string.ascii_lowercase)
    ref = "A" if up else "a"
    code = ord(L)
    assert code == base + string.ascii_letters.index(L.lower() if not up else L.lower())
    return Draft(f"Dans le code ASCII, le code de « {ref} » est {base} et les lettres se suivent dans l'ordre de l'alphabet. Quel est le code de « {L} » ?", str(code),
                 [str(x) for x in (code + 1, code - 1, code + 32 if up else code - 32, code + 2, code - 2, code + 26)],
                 f"« {L} » est la lettre de rang {ord(L.upper()) - 64} : {base} + {code - base} = {code}.", src=S1)


@gen(C1, "inf1-ascii-letter", cap=90, cat=CN)
def ascii_letter(rng, d):
    L = rng.choice(string.ascii_letters)
    code = ord(L)
    assert chr(code) == L
    nb = lambda x: chr(x) if chr(x).isalpha() else None
    wr = [chr(code + k) for k in (1, -1, 2, -2) if chr(code + k).isalpha()] + [L.swapcase()]
    ref = "A" if L.isupper() else "a"
    base = 65 if L.isupper() else 97
    return Draft(f"Sachant que le code ASCII de « {ref} » est {base} et que les lettres se suivent, quelle lettre a pour code {code} ?", L,
                 wr, f"{code} − {base} = {code - base} : c'est la lettre située {code - base} rangs après « {ref} », soit « {L} ».", src=S1)


@gen(C1, "inf1-comp-gain", cap=90, cat=CS)
def comp_gain(rng, d):
    S = rng.choice([20, 40, 50, 60, 80, 100, 120, 200, 250, 400, 500])
    p = rng.choice(range(5, 95, 5))
    if (S * (100 - p)) % 100:
        return None
    Cc = S * (100 - p) // 100
    assert Fraction(S - Cc, S) * 100 == p
    return Draft(f"Un fichier de {S} Mo est compressé en {Cc} Mo. Quel pourcentage d'espace la compression a-t-elle économisé ?", pct(p),
                 [pct(x) for x in (100 - p, p + 10, p - 10, p + 5, p - 5, round(S / Cc * 10)) if 0 < x < 100 and x != p],
                 f"Gain = ({S} − {Cc}) ÷ {S} = {p} %.", src=S1)


@gen(C1, "inf1-comp-size", cap=90, cat=CS)
def comp_size(rng, d):
    S = rng.choice([20, 40, 60, 80, 100, 120, 160, 200, 240, 300, 500, 800])
    p = rng.choice(range(5, 95, 5))
    r = Fraction(S * (100 - p), 100)
    gain = Fraction(S * p, 100)
    assert r + gain == S
    return Draft(f"Une compression réduit la taille d'un fichier de {p} %. Quelle est la taille compressée d'un fichier de {S} Mo ?", dec(r) + NB + "Mo",
                 [dec(x) + NB + "Mo" for x in (gain, S - p, Fraction(S * (100 - p), 100) + 10, S * Fraction(p, 10), Fraction(S, 2)) if x != r],
                 f"Taille = {S} × (100 − {p}) ÷ 100 = {dec(r)} Mo.", src=S1)


@gen(C1, "inf1-comp-ratio", cap=80, cat=CS)
def comp_ratio(rng, d):
    Cc = rng.randint(2, 50)
    k = rng.randint(2, 12)
    S = Cc * k
    assert Fraction(S, Cc) == k
    return Draft(f"Un fichier de {S} Ko est compressé en {Cc} Ko. Quel est le facteur de compression (taille d'origine ÷ taille compressée) ?", fr(k),
                 [dec(x) for x in (Fraction(Cc, S), S - Cc, Fraction(S * Cc), k + 1, k - 1) if x != k and x > 0] + [fr(k * 2)],
                 f"{S} ÷ {Cc} = {k}.", src=S1)


# ---- algorithmique de base
@gen(C1, "inf1-algo-sum", cap=130, cat=CA)
def algo_sum(rng, d):
    a = rng.randint(1, 5)
    step = rng.choice([1, 1, 2, 3]) if d > 1 else 1
    b = a + step * rng.randint(2, 4 + d * 2)
    kind = rng.choice(["i", "ki", "ii"]) if d > 1 else "i"
    k = rng.randint(2, 4)
    s = 0
    for i in range(a, b + 1, step):
        s = s + (i if kind == "i" else k * i if kind == "ki" else i * i)
    ref = sum((i if kind == "i" else k * i if kind == "ki" else i * i) for i in range(a, b + 1, step))
    assert s == ref
    body = {"i": "s ← s + i", "ki": f"s ← s + {k} × i", "ii": "s ← s + i × i"}[kind]
    pas = f" par pas de {step}" if step > 1 else ""
    vals = [i for i in range(a, b + 1, step)]
    return Draft(f"Que vaut s à la fin de : s ← 0 ; pour i de {a} à {b}{pas} faire {body} ?", fr(s),
                 [fr(x) for x in (s + 1, s - 1, s + a, s - vals[-1], s + vals[-1], s - a, sum(vals[:-1]) if kind == "i" else s + 2)],
                 f"La boucle parcourt i = {', '.join(map(str, vals))} et accumule : s = {fr(s)}.", src=S1)


@gen(C1, "inf1-algo-while", cap=120, cat=CA)
def algo_while(rng, d):
    kind = rng.choice(["sub", "sub-n", "dbl"])
    if kind in ("sub", "sub-n"):
        k = rng.randint(2, 9)
        N = rng.randint(2 * k, 60 + 20 * d)
        n, c = N, 0
        while n >= k:
            n -= k
            c += 1
        assert divmod(N, k) == (c, n)
        ask, val = ("c", c) if kind == "sub" else ("n", n)
        wr = [val + 1, val - 1, N // k + 1 if ask == "c" else k - n, k, N % k + 1 if ask == "n" else N // k - 1, c if ask == "n" else n]
        return Draft(f"Que vaut {ask} à la fin de : n ← {N} ; c ← 0 ; tant que n ≥ {k} faire n ← n − {k} ; c ← c + 1 ?", fr(val),
                     [fr(x) for x in wr + [val + 2]], f"On retranche {k} tant que possible : {c} fois, il reste {n} ; donc {ask} = {val}.", src=S1)
    N = rng.randint(5, 300)
    x, c = 1, 0
    while x < N:
        x *= 2
        c += 1
    assert c == (N - 1).bit_length()
    return Draft(f"Que vaut c à la fin de : x ← 1 ; c ← 0 ; tant que x < {N} faire x ← x × 2 ; c ← c + 1 ?", str(c),
                 [str(v) for v in (c - 1, c + 1, c + 2, N // 2, c * 2) if v != c and v >= 0],
                 f"x prend les valeurs 1, 2, 4… jusqu'à atteindre {x} (≥ {N}) : {c} doublements.", src=S1)


@gen(C1, "inf1-algo-cond", cap=130, cat=CA)
def algo_cond(rng, d):
    t = rng.randint(0, 4)
    if t == 0:
        x, y = rng.randint(1, 40), rng.randint(1, 40)
        if x == y:
            return None
        z = x - y if x > y else y - x
        assert z == abs(x - y)
        return Draft(f"Que vaut z après : x ← {x} ; y ← {y} ; si x > y alors z ← x − y sinon z ← y − x ?", str(z),
                     [str(v) for v in (x + y, -z, z + 1, z - 1, x * y if x * y < 400 else x + y + 1) if v != z],
                     f"Ici {'x > y' if x > y else 'x < y'}, donc z = {z}.", src=S1)
    if t == 1:
        n = rng.randint(3, 99)
        r = n // 2 if n % 2 == 0 else 3 * n + 1
        assert r == (n // 2 if not n & 1 else 3 * n + 1)
        return Draft(f"Que vaut r après : n ← {n} ; si n est pair alors r ← n ÷ 2 sinon r ← 3 × n + 1 ?", str(r),
                     [str(v) for v in (n // 2, 3 * n + 1, 3 * n, n + 1, n * 2, (n + 1) // 2 + 1) if v != r],
                     f"{n} est {'pair' if n % 2 == 0 else 'impair'}, donc r = {r}.", src=S1)
    if t == 2:
        a, b, c = (rng.randint(1, 60) for _ in range(3))
        m = a
        if b > m:
            m = b
        if c > m:
            m = c
        assert m == max(a, b, c)
        if len({a, b, c}) < 3:
            return None
        return Draft(f"Que vaut m après : m ← {a} ; si {b} > m alors m ← {b} ; si {c} > m alors m ← {c} ?", str(m),
                     [str(v) for v in (a, b, c, a + b + c, min(a, b, c)) if v != m],
                     f"m mémorise le plus grand des trois nombres : {m}.", src=S1)
    if t == 3:
        N, k = rng.randint(10, 60), rng.randint(2, 7)
        c = 0
        for i in range(1, N + 1):
            if i % k == 0:
                c += 1
        assert c == N // k
        return Draft(f"Que vaut c à la fin de : c ← 0 ; pour i de 1 à {N} faire si i mod {k} = 0 alors c ← c + 1 ?", str(c),
                     [str(v) for v in (c + 1, c - 1, N // k + 2, k, N - c, N % k) if v != c and v >= 0],
                     f"c compte les multiples de {k} entre 1 et {N} : {c}.", src=S1)
    N = rng.randint(5, 40)
    tot = 0
    for i in range(1, N + 1):
        if i % 2 == 1:
            tot += i
    assert tot == ((N + 1) // 2) ** 2
    return Draft(f"Que vaut t à la fin de : t ← 0 ; pour i de 1 à {N} faire si i est impair alors t ← t + i ?", str(tot),
                 [str(v) for v in (tot + N, tot - 1, sum(range(1, N + 1)), N * N // 2, tot + 1, sum(range(2, N + 1, 2))) if v != tot],
                 f"t additionne les entiers impairs jusqu'à {N} : {tot}.", src=S1)


@gen(C1, "inf1-img-size", cap=120, cat=CI)
def img_size(rng, d):
    W, H = rng.choice([(640, 480), (800, 600), (1024, 768), (1280, 720), (1920, 1080), (320, 240), (256, 256), (100, 100), (200, 150)]) if rng.random() < 0.5 else (rng.randint(5, 80) * 8, rng.randint(5, 60) * 8)
    bpp = rng.choice([1, 8, 16, 24, 32])
    bits = W * H * bpp
    assert bits % 8 == 0 and bits == W * H * bpp
    o = bits // 8
    if o % 1000 == 0 and rng.random() < 0.5:
        return Draft(f"Quelle est la taille non compressée, en Ko (1 Ko = 1 000 octets), d'une image de {W} × {H} pixels codée sur {bpp} bits par pixel ?", fr(o // 1000),
                     [fr(x) for x in (bits // 1000 if bits % 1000 == 0 else o // 1000 + 1, o // 1000 * 8 if False else o * 8 // 1000, W * H // 1000 if (W * H) % 1000 == 0 else o // 1000 + 2, o // 1000 + 8, o // 1000 * 2) if x != o // 1000 and x > 0],
                     f"{W} × {H} × {bpp} bits = {fr(bits)} bits = {fr(o)} octets = {fr(o // 1000)} Ko.", src=S1)
    return Draft(f"Quelle est la taille non compressée, en octets, d'une image de {W} × {H} pixels codée sur {bpp} bits par pixel ?", fr(o),
                 [fr(x) for x in (bits, W * H * bpp // 8 * 8 if False else W * H, o * 8, W * H * (bpp // 8 + 1), o + W) if x != o],
                 f"{fr(W * H)} pixels × {bpp} bits = {fr(bits)} bits, soit {fr(o)} octets.", src=S1)


@gen(C1, "inf1-img-colors", cap=60, cat=CI)
def img_colors(rng, d):
    b = rng.choice([1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 24])
    if rng.random() < 0.5:
        return Draft(f"Combien de couleurs différentes peut-on représenter avec {b} bits par pixel ?", fr(2 ** b),
                     [fr(x) for x in (b * b, 2 * b, 2 ** b - 1, 2 ** (b + 1), 2 ** (b - 1), 10 ** b if b < 5 else 2 ** b + 2)],
                     f"2^{b} = {fr(2 ** b)} couleurs.", src=S1)
    N = 2 ** b
    n = 0
    while 2 ** n < N:
        n += 1
    assert n == b
    return Draft(f"Combien de bits par pixel faut-il pour une palette de {fr(N)} couleurs ?", str(b),
                 [str(x) for x in (b - 1, b + 1, N // 8 if N // 8 != b else b + 2, b * 2, b + 2) if x != b and x > 0],
                 f"{fr(N)} = 2^{b}, donc {b} bits.", src=S1)


@gen(C1, "inf1-pix-count", cap=60, cat=CI)
def pix_count(rng, d):
    W, H = rng.choice([800, 1024, 1280, 1600, 1920, 2560, 3840, 640, 720, 4000]), rng.choice([480, 600, 720, 768, 1080, 1440, 2160, 3000])
    n = W * H
    assert n == sum(W for _ in range(H))
    return Draft(f"Combien de pixels contient une image de {W} × {H} ?", fr(n),
                 [fr(x) for x in (W + H, 2 * (W + H), n // 10, n * 10, n + W, n - H)],
                 f"{W} × {H} = {fr(n)} pixels.", src=S1)


@gen(C1, "inf1-disk-pct", cap=100, cat=CS)
def disk_pct(rng, d):
    X = rng.choice([20, 40, 60, 80, 100, 120, 160, 200, 240, 500, 1000])
    p = rng.choice(range(5, 100, 5))
    used = Fraction(X * p, 100)
    assert used.denominator == 1 or True
    free = X - used
    if rng.random() < 0.5:
        return Draft(f"Un disque de {X} Go contient {dec(used)} Go de données. Quel pourcentage du disque reste libre ?", pct(100 - p),
                     [pct(x) for x in (p, 100 - p + 10, 100 - p - 10, 100 - p + 5, 100 - p - 5, 100 - p + 20) if 0 < x < 100],
                     f"Occupé : {dec(used)} ÷ {X} = {p} %, donc libre : {100 - p} %.", src=S1)
    return Draft(f"Un disque de {X} Go est occupé à {p} %. Combien de Go reste-t-il de libre ?", dec(free) + NB + "Go",
                 [dec(x) + NB + "Go" for x in (used, X - p, Fraction(X, 100) * (100 - p) + 10, Fraction(X, 100) * p / 2, X - Fraction(p, 10)) if x != free],
                 f"Libre : {X} × (100 − {p}) ÷ 100 = {dec(free)} Go.", src=S1)


@gen(C1, "inf1-text-size", cap=80, cat=CS)
def text_size(rng, d):
    n = rng.randint(10, 9000)
    right = n * 8
    return Draft(f"Un texte de {fr(n)} caractères est enregistré en ASCII (1 octet par caractère). Quelle est sa taille en bits ?", fr(right),
                 [fr(x) for x in (n, n * 7, n * 16, n // 8 if n % 8 == 0 else n * 4, n * 8 + 8, n * 2)],
                 f"{fr(n)} octets × 8 = {fr(right)} bits.", src=S1)


@gen(C1, "inf1-digits", cap=70, cat=CN)
def digits(rng, d):
    if rng.random() < 0.5:
        N = rng.randint(8, 60000)
        n = len(bs(N))
        assert n == (N).bit_length()
        return Draft(f"Combien de chiffres comporte l'écriture binaire de {fr(N)} (sans zéro initial) ?", str(n),
                     [str(x) for x in (n - 1, n + 1, len(str(N)), n + 2, n - 2) if x != n],
                     f"2^{n - 1} ≤ {fr(N)} < 2^{n} : {n} bits.", src=S1)
    k = rng.randint(1, 12)
    assert len(format(2 ** (8 * k) - 1, "X")) == 2 * k
    return Draft(f"Combien de chiffres hexadécimaux faut-il pour écrire en entier {k} octet{'s' if k > 1 else ''} (chaque octet comptant 8 bits) ?", str(2 * k),
                 [str(x) for x in (k, 4 * k, 8 * k, 2 * k + 1, 2 * k - 1, 3 * k) if x != 2 * k],
                 f"Un chiffre hexadécimal vaut 4 bits : {k} octets = {8 * k} bits = {2 * k} chiffres.", src=S1)


# ================================================================================================ L2 INFORMATIQUE
CRz, CSy, CBD, CPR = "Réseaux", "Systèmes d'exploitation", "Bases de données", "Programmation"


def mask_int(p):
    return (0xFFFFFFFF << (32 - p)) & 0xFFFFFFFF


def ipv4(rng):
    k = rng.random()
    if k < 0.4:
        return (192 << 24) | (168 << 16) | (rng.randint(0, 255) << 8) | rng.randint(1, 254)
    if k < 0.7:
        return (10 << 24) | (rng.randint(0, 255) << 16) | (rng.randint(0, 255) << 8) | rng.randint(1, 254)
    return (172 << 24) | (rng.randint(16, 31) << 16) | (rng.randint(0, 255) << 8) | rng.randint(1, 254)


def S_(i):
    return str(ipaddress.IPv4Address(i & 0xFFFFFFFF))


def ipmask_txt(rng, p):
    return f"/{p}" if rng.random() < 0.5 else f"{S_(mask_int(p))}"


@gen(C2, "inf2-cidr-count", cap=60, cat=CRz)
def cidr_count(rng, d):
    p = rng.randint(8, 30)
    net = ipaddress.ip_network(f"10.0.0.0/{p}")
    tot = 2 ** (32 - p)
    assert net.num_addresses == tot
    if rng.random() < 0.5:
        return Draft(f"Combien d'adresses IPv4 au total (réseau et diffusion comprises) contient un réseau de préfixe /{p} ?", fr(tot),
                     [fr(x) for x in (tot - 2, tot * 2, tot // 2, 2 ** p, 2 ** (32 - p + 1) - 2, 32 - p)],
                     f"Il reste 32 − {p} = {32 - p} bits d'hôte : 2^{32 - p} = {fr(tot)} adresses.", src=S2)
    return Draft(f"Combien de machines peut-on adresser (adresse du réseau et adresse de diffusion exclues) dans un réseau /{p} ?", fr(tot - 2),
                 [fr(x) for x in (tot, tot - 1, tot + 2, tot // 2 - 2, 2 ** p - 2, 2 * tot - 2)],
                 f"2^{32 - p} − 2 = {fr(tot - 2)} adresses utilisables.", src=S2)


@gen(C2, "inf2-cidr-mask", cap=60, cat=CRz)
def cidr_mask(rng, d):
    p = rng.randint(8, 30)
    m = S_(mask_int(p))
    assert bin(mask_int(p)).count("1") == p and ipaddress.ip_network(f"0.0.0.0/{m}").prefixlen == p
    if rng.random() < 0.5:
        return Draft(f"Quel masque de sous-réseau en notation décimale pointée correspond à /{p} ?", m,
                     [S_(mask_int(q)) for q in (p - 1, p + 1, p - 8, p + 8, 32 - p, p + 2) if 1 <= q <= 32],
                     f"{p} bits à 1 suivis de {32 - p} bits à 0 donnent {m}.", src=S2)
    return Draft(f"Quelle longueur de préfixe CIDR correspond au masque {m} ?", f"/{p}",
                 [f"/{q}" for q in (p - 1, p + 1, p - 8, p + 8, p + 2, 32 - p) if 1 <= q <= 32],
                 f"Le masque contient {p} bits à 1 consécutifs : /{p}.", src=S2)


@gen(C2, "inf2-cidr-need", cap=110, cat=CRz)
def cidr_need(rng, d):
    H = rng.randint(2, 30 if d < 3 else 4000)
    hb = 1
    while 2 ** hb - 2 < H:
        hb += 1
    p = 32 - hb
    assert ipaddress.ip_network(f"10.0.0.0/{p}").num_addresses - 2 >= H and (hb == 2 or 2 ** (hb - 1) - 2 < H)
    return Draft(f"Un réseau doit accueillir {fr(H)} machines (adresses de réseau et de diffusion exclues). Quel est le plus long préfixe CIDR suffisant ?", f"/{p}",
                 [f"/{q}" for q in (p + 1, p - 1, p + 2, p - 2, 32 - H.bit_length(), 32 - (H + 2).bit_length()) if 1 <= q <= 30 and q != p],
                 f"Il faut 2^h − 2 ≥ {fr(H)} : h = {hb} bits d'hôte, donc préfixe /{p}.", src=S2)


@gen(C2, "inf2-subnet-split", cap=70, cat=CRz)
def subnet_split(rng, d):
    m = rng.randint(8, 26)
    n = rng.randint(m + 1, min(m + 8, 30))
    k = len(list(ipaddress.ip_network(f"10.0.0.0/{m}").subnets(new_prefix=n)))
    assert k == 2 ** (n - m)
    return Draft(f"Un réseau /{m} est découpé en sous-réseaux de même taille /{n}. Combien de sous-réseaux obtient-on ?", fr(k),
                 [fr(x) for x in (n - m, 2 * (n - m), k - 1, 2 * k, k // 2, 2 ** (32 - n)) if x != k],
                 f"On emprunte {n - m} bits : 2^{n - m} = {fr(k)} sous-réseaux.", src=S2)


@gen(C2, "inf2-netaddr", cap=140, cat=CRz)
def netaddr(rng, d):
    p = rng.choice([8, 12, 16, 18, 20, 22, 24, 25, 26, 27, 28, 29, 30]) if d > 2 else rng.choice([16, 24, 25, 26, 27, 28])
    ip = ipv4(rng)
    net = ip & mask_int(p)
    assert ipaddress.ip_interface(f"{S_(ip)}/{p}").network.network_address == ipaddress.IPv4Address(net)
    size = 2 ** (32 - p)
    bc = net + size - 1
    wr = [S_(bc), S_(net + size), S_(net - size), S_(ip & mask_int(max(p - 1, 1))), S_(ip & mask_int(min(p + 1, 32))), S_(net + 1), S_(ip & 0xFFFFFF00)]
    return Draft(f"Quelle est l'adresse du réseau d'une machine d'adresse {S_(ip)} avec le masque {ipmask_txt(rng, p) if False else S_(mask_int(p))} ?", S_(net),
                 wr, f"On fait un ET bit à bit entre l'adresse et le masque : {S_(net)}.", src=S2)


@gen(C2, "inf2-broadcast", cap=140, cat=CRz)
def broadcast(rng, d):
    p = rng.choice([16, 20, 22, 24, 25, 26, 27, 28, 29, 30]) if d > 2 else rng.choice([24, 25, 26, 27, 28])
    ip = ipv4(rng)
    net = ip & mask_int(p)
    bc = net | (~mask_int(p) & 0xFFFFFFFF)
    assert ipaddress.ip_interface(f"{S_(ip)}/{p}").network.broadcast_address == ipaddress.IPv4Address(bc)
    return Draft(f"Quelle est l'adresse de diffusion (broadcast) du réseau contenant {S_(ip)}/{p} ?", S_(bc),
                 [S_(net), S_(bc - 1), S_(bc + 1), S_(net | (~mask_int(max(p - 1, 1)) & 0xFFFFFFFF)), S_(net | (~mask_int(min(p + 1, 32)) & 0xFFFFFFFF)), S_(ip | 255)],
                 f"On met à 1 tous les bits d'hôte du réseau {S_(net)}/{p} : {S_(bc)}.", src=S2)


@gen(C2, "inf2-hostrange", cap=100, cat=CRz)
def hostrange(rng, d):
    p = rng.choice([20, 22, 24, 25, 26, 27, 28, 29, 30])
    ip = ipv4(rng)
    net = ip & mask_int(p)
    bc = net + 2 ** (32 - p) - 1
    hosts = list(ipaddress.ip_network(f"{S_(net)}/{p}").hosts())
    first = rng.random() < 0.5
    if first:
        assert hosts[0] == ipaddress.IPv4Address(net + 1)
        return Draft(f"Quelle est la première adresse utilisable pour une machine dans le réseau contenant {S_(ip)}/{p} ?", S_(net + 1),
                     [S_(net), S_(net + 2), S_(bc), S_(net - 1), S_(net + 2 ** (32 - p))],
                     f"Le réseau est {S_(net)}/{p} ; on exclut l'adresse du réseau : première machine {S_(net + 1)}.", src=S2)
    assert hosts[-1] == ipaddress.IPv4Address(bc - 1)
    return Draft(f"Quelle est la dernière adresse utilisable pour une machine dans le réseau contenant {S_(ip)}/{p} ?", S_(bc - 1),
                 [S_(bc), S_(bc - 2), S_(net), S_(bc + 1), S_(net + 2 ** (32 - p) - 2 ** (32 - p - 1))],
                 f"Le réseau s'achève par la diffusion {S_(bc)} ; la dernière machine est {S_(bc - 1)}.", src=S2)


@gen(C2, "inf2-samenet", cap=120, cat=CRz)
def samenet(rng, d):
    p = rng.choice([20, 22, 24, 25, 26, 27, 28])
    ip = ipv4(rng)
    net = ip & mask_int(p)
    size = 2 ** (32 - p)
    nw = ipaddress.ip_network(f"{S_(net)}/{p}")
    off = rng.randint(1, size - 2)
    right = net + off
    wr = [net + size + off, net - size + off, ip ^ size, net + 2 * size + off, net - 2 * size + off]
    wr = [w for w in wr if ipaddress.IPv4Address(w & 0xFFFFFFFF) not in nw]
    assert ipaddress.IPv4Address(right) in nw
    return Draft(f"Parmi ces adresses, laquelle appartient au même sous-réseau que {S_(ip)}/{p} ?", S_(right),
                 [S_(w) for w in wr], f"Le sous-réseau est {S_(net)}/{p} ; {S_(right)} y figure alors que les autres adresses sont dans d'autres sous-réseaux.", src=S2)


@gen(C2, "inf2-bw-time", cap=100, cat=CRz)
def bw_time(rng, d):
    D = rng.choice([4, 8, 10, 16, 20, 25, 40, 50, 80, 100, 200])
    for _ in range(20):
        S = rng.randint(2, 500)
        if (8 * S) % D == 0:
            break
    else:
        return None
    t = 8 * S // D
    assert Fraction(S * 8, D) == t
    return Draft(f"Un lien transmet {D} Mbit/s utiles. Combien de secondes faut-il pour transférer {S} Mo (1 Mo = 8 Mbit) ?", fr(t),
                 [dec(x) for x in (Fraction(S, D), S * D, Fraction(S * D, 8), Fraction(S, 8 * D), t * 8) if x != t] + [fr(t + 8)],
                 f"{S} × 8 = {fr(8 * S)} Mbit ; {fr(8 * S)} ÷ {D} = {fr(t)} s.", src=S2)


@gen(C2, "inf2-bw-conv", cap=60, cat=CRz)
def bw_conv(rng, d):
    D = rng.choice([8, 16, 24, 40, 80, 100, 200, 400, 800, 1000, 2000, 10000])
    mo = Fraction(D, 8)
    if rng.random() < 0.5:
        return Draft(f"Un débit de {fr(D)} Mbit/s correspond à combien de Mo/s (1 octet = 8 bits) ?", dec(mo),
                     [dec(x) for x in (D * 8, mo * 10, mo / 2, Fraction(D, 10), mo * 2) if x != mo],
                     f"{fr(D)} ÷ 8 = {dec(mo)} Mo/s.", src=S2)
    return Draft(f"Un disque transfère {dec(mo)} Mo/s. Quel est ce débit en Mbit/s (1 octet = 8 bits) ?", fr(D),
                 [dec(x) for x in (mo * 4, mo * 10, mo / 8, mo * 16, D * 8) if x != D] + [fr(D // 2)],
                 f"{dec(mo)} × 8 = {fr(D)} Mbit/s.", src=S2)


@gen(C2, "inf2-bw-latency", cap=100, cat=CRz)
def bw_latency(rng, d):
    D = rng.choice([8, 16, 20, 40, 80, 100])
    for _ in range(20):
        S = rng.randint(1, 200)
        if (8 * S * 1000) % D == 0:
            break
    else:
        return None
    lat = rng.choice([10, 20, 40, 50, 100, 150, 200])
    ms = Fraction(8 * S * 1000, D) + lat
    assert ms == Fraction(8 * S, D) * 1000 + lat
    return Draft(f"Un message de {S} Mo traverse un lien à {D} Mbit/s (1 Mo = 8 Mbit) dont la latence est de {lat} ms. Quel est le temps total d'envoi (latence + émission), en ms ?", fr(int(ms)),
                 [fr(x) for x in (int(ms) - lat, 8 * S * 1000 // D * 2 + lat if False else int(ms) + lat, lat * 8 + int(ms) - lat, int(ms) // 1000 + lat, Fraction(S * 1000, D).__floor__() + lat) if x != ms],
                 f"Émission : {fr(8 * S)} Mbit ÷ {D} Mbit/s = {fr(int(ms) - lat)} ms ; plus {lat} ms de latence : {fr(int(ms))} ms.", src=S2)


@gen(C2, "inf2-bw-share", cap=80, cat=CRz)
def bw_share(rng, d):
    n = rng.choice([2, 4, 5, 8, 10, 20, 25, 50])
    D = rng.choice([100, 200, 400, 500, 1000, 2000])
    r = Fraction(D, n)
    return Draft(f"Un lien de {fr(D)} Mbit/s est partagé équitablement entre {n} utilisateurs actifs. Quel débit moyen reçoit chacun, en Mbit/s ?", dec(r),
                 [dec(x) for x in (D * n, r * 2, r / 2, D - n, Fraction(D, n + 1), Fraction(D, n * 8)) if x != r],
                 f"{fr(D)} ÷ {n} = {dec(r)} Mbit/s.", src=S2)


@gen(C2, "inf2-raid-cap", cap=110, cat=CSy)
def raid_cap(rng, d):
    lvl = rng.choice([0, 1, 5])
    n = rng.randint(3, 12) if lvl != 1 else rng.randint(2, 6)
    s = rng.choice([1, 2, 3, 4, 6, 8, 10, 12, 16])
    if lvl == 0:
        cap_, nm = n * s, "agrégat par bandes, sans redondance"
    elif lvl == 1:
        cap_, nm = s, "miroir, chaque disque contient une copie complète"
    else:
        blocks = 120
        cap_, nm = (n - 1) * s, "bandes avec parité répartie sur l'ensemble des disques"
        assert blocks * (n - 1) // blocks == n - 1
    wr = [n * s, (n - 1) * s, s, (n - 2) * s if n > 2 else n * s + s, n * s // 2 if (n * s) % 2 == 0 else n * s + 1, (n + 1) * s]
    return Draft(f"Quelle est la capacité utile de {n} disques de {s} To montés en RAID {lvl} ({nm}) ?", f"{cap_} To",
                 [f"{w} To" for w in wr if w != cap_ and w > 0],
                 {0: f"RAID 0 : capacités additionnées, {n} × {s} = {cap_} To.", 1: f"RAID 1 : tous les disques sont des copies, la capacité utile est celle d'un disque : {s} To.",
                  5: f"RAID 5 : un disque équivalent sert à la parité : ({n} − 1) × {s} = {cap_} To."}[lvl], src=S2)


@gen(C2, "inf2-raid-need", cap=80, cat=CSy)
def raid_need(rng, d):
    s = rng.choice([1, 2, 4, 6, 8])
    k = rng.randint(2, 10)
    T = s * k
    lvl = rng.choice([0, 5])
    n = k if lvl == 0 else k + 1
    return Draft(f"Combien de disques de {s} To faut-il au minimum pour obtenir {T} To utiles en RAID {lvl} ?", str(n),
                 [str(x) for x in (k, k + 1, k + 2, k - 1, 2 * k) if x != n and x > 0],
                 f"RAID 0 : {k} disques ; RAID 5 : {k} disques de données plus un de parité. Ici RAID {lvl} : {n}.", src=S2)


def sched_wait_fcfs(b):
    t, w = 0, []
    for x in b:
        w.append(t)
        t += x
    return w


def sched_wait_sjf(b):
    order = sorted(range(len(b)), key=lambda i: (b[i], i))
    w = [0] * len(b)
    t = 0
    for i in order:
        w[i] = t
        t += b[i]
    return w


def sched_wait_rr(b, q):
    dq = deque(range(len(b)))
    rem = list(b)
    t = 0
    fin = [0] * len(b)
    while dq:
        i = dq.popleft()
        run = min(q, rem[i])
        t += run
        rem[i] -= run
        if rem[i]:
            dq.append(i)
        else:
            fin[i] = t
    return [fin[i] - b[i] for i in range(len(b))]


def sched_wait_rr2(b, q):
    rem, t, last, w = list(b), 0, [0] * len(b), [0] * len(b)
    while any(rem):
        for i in range(len(b)):
            if rem[i]:
                w[i] += t - last[i]
                run = min(q, rem[i])
                t += run
                rem[i] -= run
                last[i] = t
    return w


def avg_txt(tot, n):
    f = Fraction(tot, n)
    return dec(f) if exact(f, 2) else None


@gen(C2, "inf2-sched-fcfs", cap=110, cat=CSy)
def sched_fcfs(rng, d):
    n = rng.choice([3, 4, 4, 5])
    b = [rng.randint(1, 4 + 3 * d) for _ in range(n)]
    w = sched_wait_fcfs(b)
    right = avg_txt(sum(w), n)
    if right is None:
        return None
    assert sum(w) == sum(sum(b[:i]) for i in range(n))
    wr = [avg_txt(sum(sched_wait_sjf(b)), n), avg_txt(sum(sched_wait_rr(b, 2)), n), avg_txt(sum(w) + sum(b), n), dec(Fraction(sum(b), n)), dec(sum(w))]
    return Draft(f"Les processus {', '.join('P' + str(i + 1) for i in range(n))} arrivent à t = 0 dans cet ordre, avec pour durées {lst(b)}. Quel est le temps d'attente moyen en FCFS (premier arrivé, premier servi) ?", right,
                 [x for x in wr if x], f"Attentes : {lst(w)} ; moyenne = {sum(w)} ÷ {n} = {right}.", src=S2)


@gen(C2, "inf2-sched-sjf", cap=110, cat=CSy)
def sched_sjf(rng, d):
    n = rng.choice([3, 4, 4, 5])
    b = [rng.randint(1, 4 + 3 * d) for _ in range(n)]
    w = sched_wait_sjf(b)
    right = avg_txt(sum(w), n)
    if right is None:
        return None
    assert sorted(w) == sched_wait_fcfs(sorted(b))
    wr = [avg_txt(sum(sched_wait_fcfs(b)), n), avg_txt(sum(sched_wait_rr(b, 2)), n), avg_txt(sum(w) + sum(b), n), dec(Fraction(sum(b), n)), dec(sum(w)), avg_txt(sum(sched_wait_fcfs(sorted(b, reverse=True))), n)]
    return Draft(f"Les processus {', '.join('P' + str(i + 1) for i in range(n))} arrivent tous à t = 0 avec pour durées {lst(b)}. Quel est le temps d'attente moyen avec SJF (le plus court d'abord, sans préemption) ?", right,
                 [x for x in wr if x], f"Ordre de service par durées croissantes, attentes {lst(sorted(w))} ; moyenne = {right}.", src=S2)


@gen(C2, "inf2-sched-rr", cap=110, cat=CSy)
def sched_rr(rng, d):
    n = rng.choice([3, 3, 4, 4, 5])
    q = rng.choice([2, 3, 4])
    b = [rng.randint(1, 4 + 3 * d) for _ in range(n)]
    w = sched_wait_rr(b, q)
    assert w == sched_wait_rr2(b, q)
    right = avg_txt(sum(w), n)
    if right is None:
        return None
    wr = [avg_txt(sum(sched_wait_fcfs(b)), n), avg_txt(sum(sched_wait_sjf(b)), n), avg_txt(sum(w) + sum(b), n), dec(Fraction(sum(b), n)), dec(sum(w)), avg_txt(sum(sched_wait_rr(b, q + 1)), n)]
    return Draft(f"Les processus {', '.join('P' + str(i + 1) for i in range(n))} arrivent à t = 0 dans cet ordre, avec pour durées {lst(b)}. Quel est le temps d'attente moyen en round robin de quantum {q} ?", right,
                 [x for x in wr if x], f"Attente = fin − durée pour chaque processus : {lst(w)} ; moyenne = {right}.", src=S2)


@gen(C2, "inf2-sched-turn", cap=80, cat=CSy)
def sched_turn(rng, d):
    n = rng.choice([3, 4, 5])
    b = [rng.randint(1, 5 + 3 * d) for _ in range(n)]
    pol = rng.choice(["FCFS", "SJF"])
    w = sched_wait_fcfs(b) if pol == "FCFS" else sched_wait_sjf(b)
    tr = [w[i] + b[i] for i in range(n)]
    right = avg_txt(sum(tr), n)
    if right is None:
        return None
    wr = [avg_txt(sum(w), n), dec(Fraction(sum(b), n)), avg_txt(sum(tr) + n, n), avg_txt(sum(sched_wait_fcfs(b)) + sum(b) if pol == "SJF" else sum(sched_wait_sjf(b)) + sum(b), n)]
    return Draft(f"Les processus arrivent à t = 0 avec pour durées {lst(b)}. Quel est le temps de rotation moyen (fin − arrivée) en {pol}" + (" (le plus court d'abord, sans préemption)" if pol == "SJF" else " (ordre d'arrivée P1, P2…)") + " ?",
                 right, [x for x in wr if x], f"Rotation = attente + durée ; moyenne = {sum(tr)} ÷ {n} = {right}.", src=S2)


@gen(C2, "inf2-page-split", cap=120, cat=CSy)
def page_split(rng, d):
    ps = rng.choice([256, 512, 1024, 2048, 4096])
    A = rng.randint(ps, ps * rng.randint(4, 40))
    pg, off = divmod(A, ps)
    k = ps.bit_length() - 1
    assert pg == A >> k and off == A & (ps - 1)
    if rng.random() < 0.5:
        return Draft(f"Avec des pages de {fr(ps)} octets, quel est le numéro de page de l'adresse virtuelle {fr(A)} (les pages sont numérotées depuis 0) ?", str(pg),
                     [str(x) for x in (off, pg + 1, pg - 1 if pg > 0 else pg + 2, A // (ps * 2), A % (ps * 2) if A % (ps * 2) != pg else pg + 3) if x != pg],
                     f"{fr(A)} ÷ {fr(ps)} donne le quotient {pg} : c'est le numéro de page.", src=S2)
    return Draft(f"Avec des pages de {fr(ps)} octets, quel est le déplacement (offset) de l'adresse virtuelle {fr(A)} dans sa page ?", str(off),
                 [str(x) for x in (pg, off + 1, (off - 1) % ps, A % (ps * 2) if A % (ps * 2) != off else off + 7, ps - off if off else ps - 1) if x != off],
                 f"{fr(A)} = {pg} × {fr(ps)} + {off} : le reste {off} est le déplacement.", src=S2)


@gen(C2, "inf2-page-phys", cap=120, cat=CSy)
def page_phys(rng, d):
    ps = rng.choice([256, 512, 1024, 2048])
    npg = rng.randint(3, 6)
    frames = rng.sample(range(0, 12), npg)
    pg = rng.randrange(npg)
    off = rng.randrange(ps)
    A = pg * ps + off
    phys = frames[pg] * ps + off
    assert (frames[pg] << (ps.bit_length() - 1)) | (A & (ps - 1)) == phys
    tbl = ", ".join(f"page {i} → cadre {f}" for i, f in enumerate(frames))
    wr = [frames[(pg + 1) % npg] * ps + off, pg * ps + off, frames[pg] + off, frames[pg] * ps, (frames[pg] + 1) * ps + off, frames[pg - 1] * ps + off]
    return Draft(f"Pages et cadres de {fr(ps)} octets ; table des pages : {tbl}. Quelle est l'adresse physique de l'adresse virtuelle {fr(A)} ?", fr(phys),
                 [fr(x) for x in wr if x != phys],
                 f"Page {pg}, déplacement {off}, cadre {frames[pg]} : {frames[pg]} × {fr(ps)} + {off} = {fr(phys)}.", src=S2)


@gen(C2, "inf2-cache-rate", cap=90, cat=CSy)
def cache_rate(rng, d):
    T = rng.choice([20, 40, 50, 80, 100, 200, 250, 400, 500, 1000])
    h = rng.randint(1, T - 1)
    r = Fraction(h * 100, T)
    if not exact(r, 1):
        return None
    ok = pct(r) if r.denominator == 1 else dec(r, 1) + NB + "%"
    fm = lambda x: pct(x) if Fraction(x).denominator == 1 else dec(x, 1) + NB + "%"
    return Draft(f"Sur {fr(T)} accès mémoire, {fr(h)} ont trouvé la donnée dans le cache. Quel est le taux de succès (hit ratio) ?", ok,
                 [fm(x) for x in (100 - r, Fraction(h * 100, T + h), r / 2, Fraction(h, T), r + 10) if x != r and 0 < x < 100] + [fm(r - 10) if r > 10 else fm(r + 20)],
                 f"{fr(h)} ÷ {fr(T)} = {ok}.", src=S2)


@gen(C2, "inf2-cache-miss", cap=70, cat=CSy)
def cache_miss(rng, d):
    T = rng.choice([200, 500, 1000, 2000, 5000, 10000])
    p = rng.choice(range(80, 100))
    miss = T * (100 - p) // 100
    assert Fraction(T * (100 - p), 100).denominator == 1
    return Draft(f"Un cache a un taux de succès de {p} %. Combien d'échecs (défauts de cache) attend-on sur {fr(T)} accès ?", fr(miss),
                 [fr(x) for x in (T * p // 100, miss * 10, miss // 10, T - miss - 100, miss + T // 100) if x != miss and x > 0],
                 f"Échecs : {fr(T)} × (100 − {p}) ÷ 100 = {fr(miss)}.", src=S2)


@gen(C2, "inf2-cache-amat", cap=120, cat=CSy)
def cache_amat(rng, d):
    h = rng.choice(range(50, 100, 5))
    tc = rng.choice([1, 2, 4, 5, 10])
    tm = rng.choice([50, 60, 80, 100, 120, 200])
    t = Fraction(h, 100) * tc + Fraction(100 - h, 100) * tm
    if not exact(t, 2):
        return None
    assert t == (h * tc + (100 - h) * tm) / Fraction(100)
    wr = [Fraction(h, 100) * tm + Fraction(100 - h, 100) * tc, Fraction(tc + tm, 2), Fraction(h, 100) * tc + tm, Fraction(h, 100) * (tc + tm), Fraction(100 - h, 100) * tm]
    return Draft(f"Un cache a un taux de succès de {h} %. Un succès coûte {tc} ns et un échec coûte {tm} ns au total. Quel est le temps d'accès moyen, en ns ?", dec(t) + NB + "ns",
                 [dec(x) + NB + "ns" for x in wr if x != t],
                 f"{h} % × {tc} + {100 - h} % × {tm} = {dec(t)} ns.", src=S2)


# ---- SQL
NOMS = ["Ali", "Bea", "Chi", "Dan", "Eko", "Fay", "Gus", "Hana", "Ivo", "Jo", "Kim", "Lea", "Moe", "Ned"]
SERV = ["Info", "RH", "Compta", "Vente"]


def emp_table(rng, n):
    nm = rng.sample(NOMS, n)
    sal = rng.sample(range(10, 60), n)
    return [(nm[i], rng.choice(SERV[:3]), sal[i] * 10) for i in range(n)]


def emp_show(rows):
    return "Table emp(nom, service, salaire) : " + " ; ".join(f"({a}, {b}, {c})" for a, b, c in rows)


def sql_run(rows, q):
    con = sqlite3.connect(":memory:")
    con.execute("create table emp(nom text, service text, salaire integer)")
    con.executemany("insert into emp values (?,?,?)", rows)
    r = con.execute(q).fetchall()
    con.close()
    return r


@gen(C2, "inf2-sql-agg", cap=140, cat=CBD)
def sql_agg(rng, d):
    n = rng.randint(4, 6)
    rows = emp_table(rng, n)
    sv = rng.choice(sorted({r[1] for r in rows}))
    sel = [r for r in rows if r[1] == sv]
    kind = rng.choice(["COUNT", "SUM", "AVG", "MAX", "MIN"])
    f = {"COUNT": lambda x: len(x), "SUM": lambda x: sum(x), "AVG": lambda x: Fraction(sum(x), len(x)), "MAX": max, "MIN": min}
    col = "*" if kind == "COUNT" else "salaire"
    q = f"SELECT {kind}({col}) FROM emp WHERE service = '{sv}'"
    mine = f[kind]([r[2] for r in sel])
    got = sql_run(rows, q)[0][0]
    assert abs(float(mine) - float(got)) < 1e-9
    if kind == "AVG" and not exact(mine, 2):
        return None
    allv = [r[2] for r in rows]
    wr = [len(rows), sum(allv), Fraction(sum(allv), len(allv)), max(allv), min(allv), sum(r[2] for r in sel) + 10, len(sel) + 1, sum(r[2] for r in sel) // len(sel)]
    out = dec(mine) if kind == "AVG" else fr(mine)
    wr = [dec(x) if isinstance(x, Fraction) else fr(x) for x in wr]
    if kind == "COUNT":
        wr = [str(x) for x in near_ints(rng, mine, 6, lo=0, hi=n)] + [str(len(rows))]
    return Draft(f"{emp_show(rows)}. Que renvoie : {q} ?", out, [w for w in wr if w != out],
                 f"On garde les lignes du service {sv} ({len(sel)} ligne{'s' if len(sel) > 1 else ''}) puis on applique {kind} : {out}.", src=S2)


@gen(C2, "inf2-sql-distinct", cap=90, cat=CBD)
def sql_distinct(rng, d):
    n = rng.randint(5, 6)
    rows = emp_table(rng, n)
    kind = rng.choice(["distinct", "having", "like"])
    if kind == "distinct":
        q = "SELECT COUNT(DISTINCT service) FROM emp"
        mine = len({r[1] for r in rows})
    elif kind == "having":
        q = "SELECT COUNT(*) FROM (SELECT service FROM emp GROUP BY service HAVING COUNT(*) >= 2)"
        c = Counter(r[1] for r in rows)
        mine = sum(1 for v in c.values() if v >= 2)
    else:
        t = rng.choice(range(15, 55, 5)) * 10
        q = f"SELECT COUNT(*) FROM emp WHERE salaire > {t} AND service <> 'Info'"
        mine = sum(1 for r in rows if r[2] > t and r[1] != "Info")
    assert sql_run(rows, q)[0][0] == mine
    return Draft(f"{emp_show(rows)}. Que renvoie : {q} ?", str(mine),
                 [str(x) for x in near_ints(rng, mine, 6, lo=0, hi=n)] + [str(n)],
                 f"Résultat obtenu en appliquant la requête aux {n} lignes : {mine}.", src=S2)


@gen(C2, "inf2-sql-join", cap=130, cat=CBD)
def sql_join(rng, d):
    nc = rng.randint(3, 5)
    ids = list(range(1, nc + 1))
    nm = rng.randint(4, 7)
    cmd = [(rng.choice(ids + [nc + 1] if rng.random() < 0.5 else ids), rng.randint(1, 30) * 10) for _ in range(nm)]
    kind = rng.choice(["join", "left", "sum", "nocmd"])
    con = sqlite3.connect(":memory:")
    con.execute("create table client(id integer)")
    con.execute("create table cmd(client_id integer, montant integer)")
    con.executemany("insert into client values (?)", [(i,) for i in ids])
    con.executemany("insert into cmd values (?,?)", cmd)
    if kind == "join":
        q, mine = "SELECT COUNT(*) FROM client JOIN cmd ON client.id = cmd.client_id", sum(1 for c in cmd if c[0] in ids)
    elif kind == "left":
        q = "SELECT COUNT(*) FROM client LEFT JOIN cmd ON client.id = cmd.client_id"
        mine = sum(max(1, sum(1 for c in cmd if c[0] == i)) for i in ids)
    elif kind == "sum":
        q, mine = "SELECT SUM(montant) FROM client JOIN cmd ON client.id = cmd.client_id", sum(c[1] for c in cmd if c[0] in ids)
    else:
        q = "SELECT COUNT(*) FROM client WHERE id NOT IN (SELECT client_id FROM cmd)"
        mine = sum(1 for i in ids if i not in {c[0] for c in cmd})
    got = con.execute(q).fetchone()[0]
    con.close()
    if got is None:
        return None
    assert got == mine
    show = f"Tables client(id) : {', '.join(map(str, ids))} ; cmd(client_id, montant) : " + " ; ".join(f"({a}, {b})" for a, b in cmd)
    allm = sum(c[1] for c in cmd)
    wr = [len(cmd), len(ids) * len(cmd), len(ids), len(cmd) + len(ids), allm, mine + 1, mine - 1, mine + 10] if kind != "sum" else [allm, mine + 10, mine - 10, mine * 2, mine + 20, sum(c[1] for c in cmd if c[0] == ids[0])]
    return Draft(f"{show}. Que renvoie : {q} ?", fr(mine), [fr(w) for w in wr if w != mine and w >= 0],
                 {"join": "La jointure interne garde les commandes dont le client existe.", "left": "La jointure externe garde chaque client, avec une ligne par commande, ou une ligne vide s'il n'en a pas.",
                  "sum": "On additionne les montants des commandes qui ont un client correspondant.", "nocmd": "On compte les clients qui n'apparaissent dans aucune commande."}[kind] + f" Résultat : {fr(mine)}.", src=S2)


@gen(C2, "inf2-sql-order", cap=90, cat=CBD)
def sql_order(rng, d):
    n = rng.randint(4, 6)
    rows = emp_table(rng, n)
    desc = rng.random() < 0.5
    off = rng.randint(0, 1)
    q = f"SELECT nom FROM emp ORDER BY salaire {'DESC' if desc else 'ASC'} LIMIT 1" + (" OFFSET 1" if off else "")
    srt = sorted(rows, key=lambda r: r[2], reverse=desc)
    mine = srt[off][0]
    assert sql_run(rows, q)[0][0] == mine
    return Draft(f"{emp_show(rows)}. Que renvoie : {q} ?", mine, [r[0] for r in rows if r[0] != mine],
                 f"On trie par salaire {'décroissant' if desc else 'croissant'} et on prend la ligne n° {off + 1} : {mine}.", src=S2)


@gen(C2, "inf2-rel-product", cap=90, cat=CBD)
def rel_product(rng, d):
    n, m = rng.randint(3, 40), rng.randint(3, 40)
    a, b = rng.randint(2, 6), rng.randint(2, 6)
    R = list(range(n))
    Sx = list(range(m))
    cart = sum(1 for _ in itertools.product(R, Sx)) if n * m < 2000 else n * m
    assert cart == n * m
    if rng.random() < 0.5:
        return Draft(f"Une relation R compte {n} tuples et une relation S en compte {m}. Combien de tuples contient le produit cartésien R × S ?", fr(n * m),
                     [fr(x) for x in (n + m, n * m - 1, n * m + n, max(n, m), n * m * 2) if x != n * m],
                     f"Chaque tuple de R est associé à chaque tuple de S : {n} × {m} = {fr(n * m)}.", src=S2)
    return Draft(f"R a {a} attributs et {n} tuples ; S a {b} attributs et {m} tuples. Combien d'attributs a le produit cartésien R × S ?", str(a + b),
                 [str(x) for x in (a * b, n * m, a + b - 1, a + b + 1, max(a, b)) if x != a + b],
                 f"Les attributs des deux relations sont juxtaposés : {a} + {b} = {a + b}.", src=S2)


@gen(C2, "inf2-rel-join", cap=110, cat=CBD)
def rel_join(rng, d):
    nk = rng.randint(3, 6)
    ks = list(range(1, nk + 1))
    A = rng.sample(range(1, 9), nk)
    nb = rng.randint(4, 8)
    B = [rng.randint(1, 8) for _ in range(nb)]
    nested = sum(1 for x in A for y in B if x == y)
    cnt = Counter(B)
    assert nested == sum(cnt[x] for x in A)
    return Draft(f"La relation R a pour clé id : {', '.join(map(str, A))}. La relation S a pour attribut id : {', '.join(map(str, B))}. Combien de tuples compte la jointure naturelle R ⋈ S sur id ?", str(nested),
                 [str(x) for x in (len(B), len(A) * len(B), len(A), len(A) + len(B), nested + 1, nested - 1) if x != nested and x >= 0],
                 f"Chaque tuple de S est apparié aux tuples de R de même id : {nested} appariements.", src=S2)


@gen(C2, "inf2-rel-project", cap=90, cat=CBD)
def rel_project(rng, d):
    pool = rng.choice([list("DYBN"), ["Douala", "Yaoundé", "Bafoussam", "Garoua", "Kribi"], list("ABCDE")])
    n = rng.randint(5, 9)
    vals = [rng.choice(pool) for _ in range(n)]
    k = len(set(vals))
    return Draft(f"Une relation a une colonne ville dont les valeurs sont : {', '.join(vals)}. Combien de tuples renvoie la projection sur ville avec élimination des doublons ?", str(k),
                 [str(x) for x in (n, n - k, k + 1, k - 1, n + 1, max(Counter(vals).values())) if x != k and x > 0],
                 f"Il y a {k} valeurs distinctes parmi les {n} lignes.", src=S2)


@gen(C2, "inf2-arr-index", cap=110, cat=CPR)
def arr_index(rng, d):
    n = rng.randint(5, 8)
    T = [rng.randint(0, n - 1) for _ in range(n)]
    i = rng.randrange(n)
    if rng.random() < 0.5:
        right = T[T[i]]
        txt = f"T[T[{i}]]"
    else:
        j = rng.randrange(n)
        right = T[i] + T[j]
        txt = f"T[{i}] + T[{j}]"
    assert right == eval(txt.replace("T", "T"), {"T": T})
    return Draft(f"Un tableau T est indexé à partir de 0 : T = {lst(T)}. Que vaut {txt} ?", str(right),
                 [str(x) for x in near_ints(rng, right, 6, lo=0)] + [str(T[-1])],
                 f"On évalue d'abord les indices puis les valeurs : {txt} = {right}.", src=S2)


@gen(C2, "inf2-arr-bounds", cap=70, cat=CPR)
def arr_bounds(rng, d):
    a = rng.randint(0, 5)
    b = a + rng.randint(3, 40)
    n = len(range(a, b + 1))
    assert n == b - a + 1
    if rng.random() < 0.5:
        return Draft(f"Un tableau T est déclaré avec les indices de {a} à {b} inclus. Combien d'éléments contient-il ?", str(n),
                     [str(x) for x in (b - a, b, b - a + 2, a + b, n - 1 if n > 2 else n + 3) if x != n],
                     f"{b} − {a} + 1 = {n} éléments.", src=S2)
    m = rng.randint(4, 50)
    return Draft(f"Un tableau de {m} éléments est indexé à partir de 0. Quel est l'indice de son dernier élément ?", str(m - 1),
                 [str(x) for x in (m, m - 2, m + 1, m // 2, 1) if x != m - 1],
                 f"Les indices vont de 0 à {m - 1}.", src=S2)


@gen(C2, "inf2-arr-2d", cap=90, cat=CPR)
def arr_2d(rng, d):
    r, c = rng.randint(2, 4), rng.randint(2, 4)
    M = [[rng.randint(1, 9) for _ in range(c)] for _ in range(r)]
    i, j = rng.randrange(r), rng.randrange(c)
    kind = rng.choice(["elt", "row", "col"])
    show = "M = " + "[" + ", ".join(lst(row) for row in M) + "]"
    if kind == "elt":
        right, txt = M[i][j], f"M[{i}][{j}] (ligne i, colonne j, depuis 0)"
        wr = [M[j % r][i % c], M[(i + 1) % r][j], M[i][(j + 1) % c], M[i - 1][j - 1], sum(M[i])]
        return Draft(f"{show}. Que vaut M[{i}][{j}] (ligne, colonne, indices depuis 0) ?", str(right), [str(x) for x in wr] + [str(right + 1)],
                     f"Ligne {i} : {lst(M[i])}, colonne {j} : {right}.", src=S2)
    if kind == "row":
        right = sum(M[i])
        assert right == sum(M[i][k] for k in range(c))
        return Draft(f"{show}. Quelle est la somme des éléments de la ligne d'indice {i} (indices depuis 0) ?", str(right),
                     [str(x) for x in (sum(M[(i + 1) % r]), sum(M[k][i % c] for k in range(r)), right + 1, right - 1, sum(sum(x) for x in M))],
                     f"Ligne {i} : {' + '.join(map(str, M[i]))} = {right}.", src=S2)
    right = sum(M[k][j] for k in range(r))
    return Draft(f"{show}. Quelle est la somme des éléments de la colonne d'indice {j} (indices depuis 0) ?", str(right),
                 [str(x) for x in (sum(M[j % r]), sum(M[k][(j + 1) % c] for k in range(r)), right + 1, right - 1, sum(sum(x) for x in M))],
                 f"Colonne {j} : {' + '.join(str(M[k][j]) for k in range(r))} = {right}.", src=S2)


@gen(C2, "inf2-arr-prefix", cap=90, cat=CPR)
def arr_prefix(rng, d):
    n = rng.randint(4, 7)
    T = [rng.randint(1, 9) for _ in range(n)]
    U = T[:]
    for i in range(1, n):
        U[i] = U[i] + U[i - 1]
    k = rng.randrange(1, n)
    assert U[k] == sum(T[:k + 1])
    return Draft(f"T = {lst(T)} (indices depuis 0). Après : pour i de 1 à {n - 1} faire T[i] ← T[i] + T[i − 1], que vaut T[{k}] ?", str(U[k]),
                 [str(x) for x in (T[k], sum(T), sum(T[:k]), U[k - 1], T[k] + T[k - 1], U[k] + 1) if x != U[k]],
                 f"T devient {lst(U)} : chaque case cumule les précédentes.", src=S2)


def fib(n):
    return n if n < 2 else fib(n - 1) + fib(n - 2)


@gen(C2, "inf2-rec-fact", cap=40, cat=CPR)
def rec_fact(rng, d):
    n = rng.randint(3, 12)
    def f(k):
        return 1 if k == 0 else k * f(k - 1)
    assert f(n) == math.factorial(n)
    return Draft(f"Soit f(0) = 1 et f(n) = n × f(n − 1) pour n ≥ 1. Que vaut f({n}) ?", fr(f(n)),
                 [fr(x) for x in (f(n - 1), f(n + 1), n * n, sum(range(n + 1)), f(n) // 2, f(n) + n)],
                 f"f({n}) = {n}! = {fr(f(n))}.", src=S2)


@gen(C2, "inf2-rec-fib", cap=40, cat=CPR)
def rec_fib(rng, d):
    n = rng.randint(4, 16)
    a, b = 0, 1
    for _ in range(n):
        a, b = b, a + b
    assert a == fib(n) if n < 20 else True
    return Draft(f"Soit F(0) = 0, F(1) = 1 et F(n) = F(n − 1) + F(n − 2) pour n ≥ 2. Que vaut F({n}) ?", fr(a),
                 [fr(x) for x in (fib(n - 1), fib(n + 1), fib(n - 2), a + 1, a - 1, 2 * fib(n - 1))],
                 f"On calcule 0, 1, 1, 2, 3, 5, 8… jusqu'au rang {n} : {fr(a)}.", src=S2)


@gen(C2, "inf2-rec-fibcalls", cap=40, cat=CPR)
def rec_fibcalls(rng, d):
    n = rng.randint(3, 15)
    cnt = [0]
    def f(k):
        cnt[0] += 1
        return k if k < 2 else f(k - 1) + f(k - 2)
    f(n)
    assert cnt[0] == 2 * fib(n + 1) - 1
    return Draft(f"La fonction f(n) renvoie n si n < 2, sinon f(n − 1) + f(n − 2). Combien d'appels à f (appel initial compris) l'évaluation de f({n}) provoque-t-elle ?", fr(cnt[0]),
                 [fr(x) for x in (fib(n), fib(n + 1), n + 1, 2 * n, cnt[0] + 2, 2 ** n - 1 if 2 ** n - 1 != cnt[0] else 2 ** n)],
                 f"Le nombre d'appels C(n) vérifie C(n) = 1 + C(n − 1) + C(n − 2) ; ici C({n}) = {fr(cnt[0])}.", src=S2)


@gen(C2, "inf2-rec-gen", cap=130, cat=CPR)
def rec_gen(rng, d):
    k = rng.randint(2, 6)
    c0 = rng.randint(0, 5)
    n = rng.randint(3, 12)
    kind = rng.choice(["add", "mul", "dbl"])
    def f(m):
        if m == 0:
            return c0
        return f(m - 1) + k if kind == "add" else f(m - 1) * 2 + k if kind == "dbl" else f(m - 1) + m * k
    v = c0
    for m in range(1, n + 1):
        v = v + k if kind == "add" else v * 2 + k if kind == "dbl" else v + m * k
    assert f(n) == v
    rule = {"add": f"f(n) = f(n − 1) + {k}", "dbl": f"f(n) = 2 × f(n − 1) + {k}", "mul": f"f(n) = f(n − 1) + {k} × n"}[kind]
    return Draft(f"Soit f(0) = {c0} et {rule} pour n ≥ 1. Que vaut f({n}) ?", fr(v),
                 [fr(x) for x in (v + k, v - k, f(n - 1), c0 + n * k, v + 1, v * 2) if x != v],
                 f"On applique la définition de proche en proche depuis f(0) : f({n}) = {fr(v)}.", src=S2)


@gen(C2, "inf2-rec-gcd", cap=90, cat=CPR)
def rec_gcd(rng, d):
    a, b = rng.randint(10, 400), rng.randint(5, 300)
    calls = [0]
    def g(x, y):
        calls[0] += 1
        return x if y == 0 else g(y, x % y)
    r = g(a, b)
    assert r == math.gcd(a, b)
    if rng.random() < 0.5:
        return Draft(f"La fonction pgcd(a, b) renvoie a si b = 0, sinon pgcd(b, a mod b). Que renvoie pgcd({a}, {b}) ?", str(r),
                     [str(x) for x in (a % b, min(a, b), a - b if a > b else b - a, r * 2, a * b // r if r else 1, 1) if x != r and x > 0],
                     f"Algorithme d'Euclide : le dernier reste non nul est {r}.", src=S2)
    return Draft(f"La fonction pgcd(a, b) renvoie a si b = 0, sinon pgcd(b, a mod b). Combien d'appels (appel initial compris) pour pgcd({a}, {b}) ?", str(calls[0]),
                 [str(x) for x in near_ints(rng, calls[0], 6, lo=1)],
                 f"La suite des appels est celle des divisions d'Euclide : {calls[0]} appels.", src=S2)


@gen(C2, "inf2-trace-while", cap=130, cat=CPR)
def trace_while(rng, d):
    a, b = rng.randint(1, 5), rng.randint(2, 6)
    lim = rng.randint(20, 60 + 20 * d)
    x, y, t = a, b, 0
    while x < lim:
        x, y, t = x + y, y + 1, t + 1
    ask = rng.choice(["x", "y", "t"])
    val = {"x": x, "y": y, "t": t}[ask]
    h = a
    yy = b
    tt = 0
    while True:
        if h >= lim:
            break
        h += yy
        yy += 1
        tt += 1
    assert (h, yy, tt) == (x, y, t)
    return Draft(f"Que vaut {ask} à la fin de : x ← {a} ; y ← {b} ; t ← 0 ; tant que x < {lim} faire x ← x + y ; y ← y + 1 ; t ← t + 1 ?", fr(val),
                 [fr(v) for v in (val + 1, val - 1, val + 2, {"x": x - y + 1, "y": y + 2, "t": t + 2}[ask], {"x": x + y, "y": y - 2, "t": t - 2}[ask]) if v != val and v >= 0],
                 f"La boucle s'exécute {t} fois ; à la fin x = {x}, y = {y}, t = {t}.", src=S2)


@gen(C2, "inf2-trace-for", cap=130, cat=CPR)
def trace_for(rng, d):
    n, m = rng.randint(2, 6), rng.randint(2, 6)
    kind = rng.choice(["prod", "tri", "cond"])
    s = 0
    if kind == "prod":
        for i in range(1, n + 1):
            for j in range(1, m + 1):
                s += i * j
        assert s == (n * (n + 1) // 2) * (m * (m + 1) // 2)
        txt = f"pour i de 1 à {n} faire pour j de 1 à {m} faire s ← s + i × j"
    elif kind == "tri":
        for i in range(1, n + 1):
            for j in range(1, i + 1):
                s += j
        assert s == sum(i * (i + 1) // 2 for i in range(1, n + 1))
        txt = f"pour i de 1 à {n} faire pour j de 1 à i faire s ← s + j"
    else:
        for i in range(1, n + 1):
            for j in range(1, m + 1):
                if (i + j) % 2 == 0:
                    s += 1
        assert s == sum(1 for i in range(1, n + 1) for j in range(1, m + 1) if (i + j) % 2 == 0)
        txt = f"pour i de 1 à {n} faire pour j de 1 à {m} faire si (i + j) est pair alors s ← s + 1"
    return Draft(f"Que vaut s à la fin de : s ← 0 ; {txt} ?", str(s),
                 [str(x) for x in near_ints(rng, s, 6, lo=0)] + [str(n * m)],
                 f"On additionne les contributions de chaque tour de la boucle imbriquée : s = {s}.", src=S2)


@gen(C2, "inf2-trace-digits", cap=130, cat=CPR)
def trace_digits(rng, d):
    n0 = rng.randint(100, 99999)
    n, s = n0, 0
    while n > 0:
        s += n % 10
        n //= 10
    assert s == sum(int(c) for c in str(n0))
    ask = rng.choice(["s", "c"])
    c = len(str(n0))
    if ask == "s":
        return Draft(f"Que vaut s à la fin de : n ← {n0} ; s ← 0 ; tant que n > 0 faire s ← s + (n mod 10) ; n ← n ÷ 10 (division entière) ?", str(s),
                     [str(x) for x in (n0 % 10, n0 // 10 % 100, s + 1, s - 1, int(str(n0)[::-1]) % 1000 if False else sum(int(ch) for ch in str(n0)[:-1]), s + 9) if x != s],
                     f"s additionne les chiffres de {n0} : {' + '.join(str(n0))} = {s}.", src=S2)
    return Draft(f"Combien de fois le corps de la boucle s'exécute-t-il : n ← {n0} ; tant que n > 0 faire n ← n ÷ 10 (division entière) ?", str(c),
                 [str(x) for x in (c - 1, c + 1, c + 2, n0 % 10 + 1, c * 2) if x != c and x > 0],
                 f"Chaque tour retire un chiffre : {c} tours.", src=S2)


# ================================================================================================ L3 INFORMATIQUE
CX, CT, CGr, CH, CCr = "Complexité", "Structures de données", "Graphes", "Hachage", "Cryptographie et codage"


def loops_variants():
    """(texte, fonction n -> nombre d'exécutions simulé)"""
    def a(n):
        c = 0
        for _ in range(n):
            for _ in range(n):
                c += 1
        return c
    def b(n):
        c = 0
        for i in range(1, n + 1):
            for _ in range(1, i + 1):
                c += 1
        return c
    def cc(n):
        c = 0
        i = 1
        while i < n:
            c += 1
            i *= 2
        return c
    def dd(n):
        c = 0
        for i in range(n):
            j = 1
            while j <= n:
                c += 1
                j *= 2
        return c
    def e(n):
        c = 0
        for i in range(n):
            for j in range(i + 1, n):
                c += 1
        return c
    def f(n):
        c = 0
        for _ in range(n):
            for _ in range(n):
                for _ in range(n):
                    c += 1
        return c
    def g(n):
        c = 0
        for _ in range(n):
            c += 1
        for _ in range(n):
            c += 1
        return c
    return {
        "a": ("pour i de 1 à n faire pour j de 1 à n faire {op}", a),
        "b": ("pour i de 1 à n faire pour j de 1 à i faire {op}", b),
        "c": ("i ← 1 ; tant que i < n faire {op} ; i ← i × 2", cc),
        "d": ("pour i de 1 à n faire j ← 1 ; tant que j ≤ n faire {op} ; j ← j × 2", dd),
        "e": ("pour i de 1 à n faire pour j de i + 1 à n faire {op}", e),
        "f": ("pour i de 1 à n faire pour j de 1 à n faire pour k de 1 à n faire {op}", f),
        "g": ("pour i de 1 à n faire {op} ; puis pour j de 1 à n faire {op}", g),
    }


LV = loops_variants()
BIGO = {"a": "O(n²)", "b": "O(n²)", "c": "O(log n)", "d": "O(n log n)", "e": "O(n²)", "f": "O(n³)", "g": "O(n)"}
# classe asymptotique : on cherche, parmi les candidates, celle dont le rapport compte/f(n) est le plus stable pour n = 2^k
CLASSES = {"O(1)": lambda n: 1, "O(log n)": lambda n: math.log2(n), "O(n)": lambda n: n, "O(n log n)": lambda n: n * math.log2(n),
           "O(n²)": lambda n: n * n, "O(n³)": lambda n: n ** 3}
_BIGO_CACHE = {}


def classify_loop(key):
    if key in _BIGO_CACHE:
        return _BIGO_CACHE[key]
    fn = LV[key][1]
    ns = [2 ** k for k in (4, 5, 6, 7, 8)] if key != "f" else [2 ** k for k in (3, 4, 5, 6)]
    best, spread = None, None
    for name, g in CLASSES.items():
        r = [fn(n) / g(n) for n in ns]
        sp = max(r) / min(r) if min(r) > 0 else 1e9
        # le rapport doit se stabiliser: le dernier écart relatif est faible
        tail = abs(r[-1] - r[-2]) / r[-1]
        score = sp * (1 + tail)
        if spread is None or score < spread:
            best, spread = name, score
    assert best == BIGO[key], (key, best)
    _BIGO_CACHE[key] = best
    return best


OPS = ["x ← x + 1", "c ← c + 1", "s ← s + 2", "compteur ← compteur + 1", "afficher(i)", "total ← total + i"]


@gen(C3, "inf3-loop-count", cap=130, cat=CX)
def loop_count(rng, d):
    key = rng.choice(list(LV))
    txt, fn = LV[key]
    n = rng.randint(3, 8 + 4 * d) if key not in ("c",) else rng.randint(5, 200)
    op = rng.choice(OPS[:4])
    v = fn(n)
    form = {"a": n * n, "b": n * (n + 1) // 2, "c": (n - 1).bit_length(), "d": n * (n.bit_length()), "e": n * (n - 1) // 2, "f": n ** 3, "g": 2 * n}[key]
    assert v == form
    body = txt.format(op=op)
    wr = {"a": [n, 2 * n, n * n - 1, n * (n + 1) // 2], "b": [n * n, n * (n - 1) // 2, n * (n + 1), (n + 1) * (n + 1) // 2 + n],
          "c": [n, n // 2, (n - 1).bit_length() + 1, n.bit_length() + 1], "d": [n * n, n * (n.bit_length() - 1), n * n.bit_length() + n, n + n.bit_length()],
          "e": [n * n, n * (n + 1) // 2, n * (n - 1), n * (n - 1) // 2 + n], "f": [n * n, 3 * n, n ** 3 - 1, n * n * (n - 1)], "g": [n * n, n, 2 * n + 1, 2 * n - 1]}[key]
    ask = op if "afficher" not in op else op
    return Draft(f"Pour n = {n}, combien de fois l'instruction « {op} » est-elle exécutée dans : {body} ?", fr(v),
                 [fr(x) for x in wr + [v + 1, v - 1] if x != v and x >= 0], f"En comptant les tours de boucle (simulation) on trouve {fr(v)} exécutions.", src=S3)


@gen(C3, "inf3-bigo", cap=70, cat=CX)
def bigo(rng, d):
    key = rng.choice(list(LV))
    txt, fn = LV[key]
    op = rng.choice(OPS)
    nom = rng.choice(["n", "n"])
    best = classify_loop(key)
    body = txt.format(op=op)
    wr = [k for k in CLASSES if k != best and k != "O(1)"]
    rng.shuffle(wr)
    return Draft(f"Quelle est la complexité asymptotique, en fonction de n, de l'algorithme suivant : {body} ?", best,
                 wr + ["O(2ⁿ)"], f"Le nombre d'exécutions de « {op} » croît comme {best} (vérifié en comparant les comptages pour n = 2^k).", src=S3)


GB = {"log n": ("log n", lambda n: math.log(n)), "√n": ("√n", lambda n: 0.5 * math.log(n)), "n": ("n", lambda n: math.log(n)), "n log n": ("n log n", lambda n: math.log(n) + math.log(math.log(n))),
      "n²": ("n²", lambda n: 2 * math.log(n)), "n³": ("n³", lambda n: 3 * math.log(n)), "2ⁿ": ("2ⁿ", None)}
ORDER = ["log n", "√n", "n", "n log n", "n²", "n³", "2ⁿ"]


def growth_key(name, coef):
    N = 2.0 ** 1000 if False else None
    lg = math.log(2) * 1000     # ln n pour n = 2^1000
    if name == "2ⁿ":
        v = 2.0 ** 1000 * math.log(2)
    elif name == "log n":
        v = math.log(lg)
    elif name == "√n":
        v = lg / 2
    elif name == "n":
        v = lg
    elif name == "n log n":
        v = lg + math.log(lg)
    elif name == "n²":
        v = 2 * lg
    else:
        v = 3 * lg
    return v + math.log(coef)


def gtxt(name, coef):
    c = "" if coef == 1 else str(coef)
    if name == "2ⁿ":
        return c + ("·" if c else "") + "2ⁿ"
    if name in ("n log n", "log n", "√n"):
        return (c + (" " if name == "log n" else "·" if name == "√n" else "·")) + name if c else name
    return c + name


@gen(C3, "inf3-growth", cap=140, cat=CX)
def growth(rng, d):
    names = rng.sample(ORDER, 4)
    coefs = {nm: rng.choice([1, 1, 2, 3, 5, 10, 50, 100]) for nm in names}
    keys = {nm: growth_key(nm, coefs[nm]) for nm in names}
    srt = sorted(names, key=lambda nm: keys[nm])
    assert srt == sorted(names, key=lambda nm: ORDER.index(nm))
    fast = rng.random() < 0.5
    right = srt[-1] if fast else srt[0]
    lab = lambda nm: gtxt(nm, coefs[nm])
    return Draft(f"Parmi ces fonctions de n, laquelle croît {'le plus vite' if fast else 'le plus lentement'} quand n tend vers l'infini : {', '.join(lab(x) for x in names)} ?", lab(right),
                 [lab(x) for x in names if x != right], "Les coefficients constants n'influent pas sur la croissance asymptotique : on compare les types de fonctions (logarithme < racine < linéaire < n log n < polynôme < exponentielle).", src=S3)


@gen(C3, "inf3-scale", cap=130, cat=CX)
def scale(rng, d):
    k = rng.choice([1, 2, 3])
    c = rng.choice([2, 3, 4, 5, 10])
    T = rng.choice([1, 2, 3, 5, 10, 20])
    r = T * c ** k
    assert Fraction(T) * Fraction(c * 1000) ** k / Fraction(1000) ** k == r
    form = {1: "n", 2: "n²", 3: "n³"}[k]
    return Draft(f"Le temps d'exécution d'un algorithme est proportionnel à {form}. Il dure {T} s pour n éléments ; combien de secondes pour {c} × n éléments ?", fr(r),
                 [fr(x) for x in (T * c, T * c ** (k + 1), T * c * k, T * c ** 2 if k != 2 else T * c ** 3, T + c, T * c ** k + T) if x != r],
                 f"Multiplier n par {c} multiplie le temps par {c}^{k} = {c ** k} : {T} × {c ** k} = {fr(r)} s.", src=S3)


@gen(C3, "inf3-expscale", cap=40, cat=CX)
def expscale(rng, d):
    m = rng.randint(1, 10)
    T = rng.choice([1, 2, 3, 4, 5])
    r = T * 2 ** m
    assert Fraction(2 ** (20 + m), 2 ** 20) == 2 ** m
    return Draft(f"Le temps d'un algorithme est proportionnel à 2ⁿ ; il dure {T} s pour n éléments. Combien de secondes pour n + {m} éléments ?", fr(r),
                 [fr(x) for x in (T * m, T * (m + 1), T * 2 * m, T + m, T * 2 ** (m + 1), T * m * m) if x != r],
                 f"Chaque élément en plus double le temps : multiplication par 2^{m} = {2 ** m}.", src=S3)


# ---- tris
def bubble_passes(a, k):
    a = a[:]
    n = len(a)
    for p in range(min(k, n - 1)):
        for j in range(n - 1 - p):
            if a[j] > a[j + 1]:
                a[j], a[j + 1] = a[j + 1], a[j]
    return a


def selection_passes(a, k):
    a = a[:]
    n = len(a)
    for i in range(min(k, n - 1)):
        m = min(range(i, n), key=lambda x: a[x])
        a[i], a[m] = a[m], a[i]
    return a


def insertion_passes(a, k):
    a = a[:]
    for i in range(1, min(k, len(a) - 1) + 1):
        x, j = a[i], i - 1
        while j >= 0 and a[j] > x:
            a[j + 1] = a[j]
            j -= 1
        a[j + 1] = x
    return a


def inversions(a):
    return sum(1 for i in range(len(a)) for j in range(i + 1, len(a)) if a[i] > a[j])


def rlist(rng, n, hi=40):
    return rng.sample(range(1, hi), n)


@gen(C3, "inf3-sort-bubble-swaps", cap=110, cat=CT)
def sort_bubble_swaps(rng, d):
    n = rng.randint(4, 6 + d)
    a = rlist(rng, n)
    sw = 0
    b = a[:]
    for p in range(n - 1):
        for j in range(n - 1 - p):
            if b[j] > b[j + 1]:
                b[j], b[j + 1] = b[j + 1], b[j]
                sw += 1
    assert b == sorted(a) and sw == inversions(a)
    return Draft(f"On trie {lst(a)} par tri à bulles (passes complètes, on échange deux voisins s'ils sont dans le mauvais ordre). Combien d'échanges au total ?", str(sw),
                 [str(x) for x in near_ints(rng, sw, 6, lo=0)] + [str(n * (n - 1) // 2)],
                 f"Le tri à bulles fait autant d'échanges que d'inversions (paires dans le mauvais ordre) : {sw}.", src=S3)


@gen(C3, "inf3-sort-select-swaps", cap=110, cat=CT)
def sort_select_swaps(rng, d):
    n = rng.randint(4, 6 + d)
    a = rlist(rng, n)
    b = a[:]
    sw = 0
    for i in range(n - 1):
        m = i
        for j in range(i + 1, n):
            if b[j] < b[m]:
                m = j
        if m != i:
            b[i], b[m] = b[m], b[i]
            sw += 1
    assert b == sorted(a)
    return Draft(f"Tri par sélection de {lst(a)} : à chaque passe on échange le minimum de la partie non triée avec la case courante, seulement s'il n'y est pas déjà. Combien d'échanges ?", str(sw),
                 [str(x) for x in near_ints(rng, sw, 6, lo=0, hi=n)] + [str(n - 1), str(inversions(a))],
                 f"On compte les passes où le minimum n'est pas déjà à sa place : {sw}.", src=S3)


@gen(C3, "inf3-sort-insert-shift", cap=110, cat=CT)
def sort_insert_shift(rng, d):
    n = rng.randint(4, 6 + d)
    a = rlist(rng, n)
    b = a[:]
    sh = 0
    for i in range(1, n):
        x, j = b[i], i - 1
        while j >= 0 and b[j] > x:
            b[j + 1] = b[j]
            j -= 1
            sh += 1
        b[j + 1] = x
    assert b == sorted(a) and sh == inversions(a)
    return Draft(f"Tri par insertion de {lst(a)} : combien de décalages d'éléments (déplacement d'une case vers la droite) sont effectués au total ?", str(sh),
                 [str(x) for x in near_ints(rng, sh, 6, lo=0)] + [str(n - 1)],
                 f"Chaque décalage corrige une inversion : on trouve {sh} décalages.", src=S3)


@gen(C3, "inf3-sort-bubble-comp", cap=110, cat=CT)
def sort_bubble_comp(rng, d):
    n = rng.randint(4, 6 + d)
    a = rlist(rng, n)
    comp, passes, b = 0, 0, a[:]
    while True:
        swapped = False
        for j in range(n - 1 - passes):
            comp += 1
            if b[j] > b[j + 1]:
                b[j], b[j + 1] = b[j + 1], b[j]
                swapped = True
        passes += 1
        if not swapped:
            break
    # recoupement : nombre de passes = 1 + max de déplacements vers la gauche d'un élément
    left = max(sum(1 for x in a[:i] if x > a[i]) for i in range(n))
    assert passes == min(left + 1, n - 1) or passes == left + 1
    assert b == sorted(a)
    return Draft(f"Tri à bulles de {lst(a)} : chaque passe compare les paires voisines de la zone non fixée (n − 1 paires à la passe 1, une de moins ensuite) ; arrêt après une passe sans échange. Combien de comparaisons ?", str(comp),
                 [str(x) for x in near_ints(rng, comp, 6, lo=1)] + [str(n * (n - 1) // 2)],
                 f"On simule les passes jusqu'à une passe sans échange : {passes} passes, {comp} comparaisons.", src=S3)


SORT_LABEL = {"bulles": "tri à bulles", "sélection": "tri par sélection", "insertion": "tri par insertion"}
SORT_PASS = {"bulles": "chaque passe parcourt de gauche à droite les paires voisines de la zone non fixée et échange celles qui sont mal ordonnées",
             "sélection": "la passe p place à l'indice p − 1 (depuis 0) le minimum de la partie restante, par échange",
             "insertion": "la passe p insère l'élément d'indice p (depuis 0) dans la partie gauche déjà triée"}


def sort_state_gen(kind):
    fn = {"bulles": bubble_passes, "sélection": selection_passes, "insertion": insertion_passes}[kind]

    def g(rng, d):
        n = rng.randint(5, 6 + d)
        a = rlist(rng, n)
        k = rng.randint(1, n - 2)
        right = fn(a, k)
        if right == fn(a, k + 1) or right == fn(a, k - 1):
            return None
        others = [bubble_passes(a, k), selection_passes(a, k), insertion_passes(a, k), fn(a, k + 1), fn(a, k - 1)]
        wr = [lst(x) for x in others if x != right]
        return Draft(f"On applique le {SORT_LABEL[kind]} à {lst(a)} ({SORT_PASS[kind]}). Quel est l'état de la liste après {k} passe{'s' if k > 1 else ''} ?", lst(right),
                     wr, f"Simulation de {k} passe{'s' if k > 1 else ''} du {SORT_LABEL[kind]} : {lst(right)}.", src=S3)
    return g


for _k, _t in (("bulles", "inf3-sort-bubble-state"), ("sélection", "inf3-sort-select-state"), ("insertion", "inf3-sort-insert-state")):
    gen(C3, _t, cap=110, cat=CT)(sort_state_gen(_k))


@gen(C3, "inf3-binsearch-max", cap=60, cat=CX)
def binsearch_max(rng, d):
    n = rng.randint(2, 3000)
    worst = 0
    arr = list(range(n))
    for x in range(n):
        lo, hi, c = 0, n - 1, 0
        while lo <= hi:
            mid = (lo + hi) // 2
            c += 1
            if arr[mid] == x:
                break
            if arr[mid] < x:
                lo = mid + 1
            else:
                hi = mid - 1
        worst = max(worst, c)
    exp = n.bit_length()
    assert worst == exp
    return Draft(f"Au maximum, combien de comparaisons (une par élément du milieu examiné) la recherche dichotomique d'une valeur dans un tableau trié de {fr(n)} éléments exige-t-elle ?", str(exp),
                 [str(x) for x in (exp - 1, exp + 1, exp + 2, n // 2, math.ceil(math.log2(n)) + 2 if n > 1 else 3, n.bit_length() * 2) if x != exp and x > 0],
                 f"Chaque comparaison divise l'intervalle par 2 : ⌊log₂ {fr(n)}⌋ + 1 = {exp}.", src=S3)


@gen(C3, "inf3-binsearch-trace", cap=110, cat=CX)
def binsearch_trace(rng, d):
    n = rng.randint(7, 15)
    arr = sorted(rng.sample(range(1, 80), n))
    x = rng.choice(arr)
    lo, hi, c = 0, n - 1, 0
    while True:
        mid = (lo + hi) // 2
        c += 1
        if arr[mid] == x:
            break
        if arr[mid] < x:
            lo = mid + 1
        else:
            hi = mid - 1
    def rec(l, h, k):
        m = (l + h) // 2
        if arr[m] == x:
            return k
        return rec(m + 1, h, k + 1) if arr[m] < x else rec(l, m - 1, k + 1)
    assert rec(0, n - 1, 1) == c
    return Draft(f"Recherche dichotomique de {x} dans {lst(arr)} (indices depuis 0, milieu = division entière de (début + fin) par 2). Combien d'éléments du milieu sont examinés, y compris le dernier ?", str(c),
                 [str(v) for v in (c - 1, c + 1, c + 2, arr.index(x) + 1, n.bit_length() + 1) if v != c and v > 0],
                 f"On simule les intervalles successifs : la valeur est trouvée à la comparaison n° {c}.", src=S3)


# ---- arbres
def bst_insert(root, k):
    if root is None:
        return [k, None, None]
    if k < root[0]:
        root[1] = bst_insert(root[1], k)
    else:
        root[2] = bst_insert(root[2], k)
    return root


def pre(t):
    return [] if t is None else [t[0]] + pre(t[1]) + pre(t[2])


def ino(t):
    return [] if t is None else ino(t[1]) + [t[0]] + ino(t[2])


def post(t):
    return [] if t is None else post(t[1]) + post(t[2]) + [t[0]]


def height(t):
    return -1 if t is None else 1 + max(height(t[1]), height(t[2]))


def size(t):
    return 0 if t is None else 1 + size(t[1]) + size(t[2])


def leaves(t):
    if t is None:
        return 0
    return 1 if t[1] is None and t[2] is None else leaves(t[1]) + leaves(t[2])


def level(t):
    out, q = [], deque([t] if t else [])
    while q:
        x = q.popleft()
        out.append(x[0])
        for c in (x[1], x[2]):
            if c:
                q.append(c)
    return out


def pre_iter(t):
    out, st = [], [t] if t else []
    while st:
        x = st.pop()
        out.append(x[0])
        for c in (x[2], x[1]):
            if c:
                st.append(c)
    return out


def post_iter(t):
    out, st = [], [t] if t else []
    while st:
        x = st.pop()
        out.append(x[0])
        for c in (x[1], x[2]):
            if c:
                st.append(c)
    return out[::-1]


def in_iter(t):
    out, st, x = [], [], t
    while st or x:
        while x:
            st.append(x)
            x = x[1]
        x = st.pop()
        out.append(x[0])
        x = x[2]
    return out


def build_bst(keys):
    r = None
    for k in keys:
        r = bst_insert(r, k)
    return r


@gen(C3, "inf3-tree-maxnodes", cap=70, cat=CT)
def tree_maxnodes(rng, d):
    h = rng.randint(1, 14)
    # arbre binaire parfait de hauteur h (en arêtes) construit par numérotation de tas
    nodes = 2 ** (h + 1) - 1
    assert max(i.bit_length() - 1 for i in range(1, nodes + 1)) == h
    if rng.random() < 0.5:
        return Draft(f"Combien un arbre binaire de hauteur {h} (la hauteur est le nombre d'arêtes du plus long chemin de la racine à une feuille) peut-il contenir de nœuds, au maximum ?", fr(nodes),
                     [fr(x) for x in (2 ** h, 2 ** (h + 1), 2 ** h - 1, 2 ** (h + 1) - 2, 2 * h + 1, h * h + 1) if x != nodes],
                     f"Au niveau k il y a au plus 2^k nœuds : 2^0 + … + 2^{h} = {fr(nodes)}.", src=S3)
    return Draft(f"Combien un arbre binaire de hauteur {h} (nombre d'arêtes du plus long chemin racine-feuille) peut-il avoir de feuilles, au maximum ?", fr(2 ** h),
                 [fr(x) for x in (2 ** (h + 1), 2 ** h - 1, 2 ** (h + 1) - 1, 2 ** (h - 1), h + 1, 2 * h) if x != 2 ** h],
                 f"Le dernier niveau contient au plus 2^{h} = {fr(2 ** h)} nœuds.", src=S3)


@gen(C3, "inf3-tree-minheight", cap=70, cat=CT)
def tree_minheight(rng, d):
    n = rng.randint(2, 5000)
    h = max(i.bit_length() - 1 for i in range(1, n + 1))
    assert h == n.bit_length() - 1
    return Draft(f"Quelle est la hauteur minimale (en arêtes) d'un arbre binaire de {fr(n)} nœuds ?", str(h),
                 [str(x) for x in (h - 1, h + 1, h + 2, n.bit_length() + 1, n // 2 if n // 2 not in (h,) else n // 2 + 1) if x != h and x >= 0],
                 f"Il faut 2^(h+1) − 1 ≥ {fr(n)} ; on obtient h = ⌊log₂ {fr(n)}⌋ = {h}.", src=S3)


@gen(C3, "inf3-bst-height", cap=110, cat=CT)
def bst_height(rng, d):
    n = rng.randint(5, 7 + d)
    keys = rng.sample(range(1, 60), n)
    t = build_bst(keys)
    h = height(t)
    # recoupement : profondeur de chaque clé = nombre d'ancêtres
    dep = {}
    root = keys[0]
    for k in keys:
        c, dpt = root, 0
        node = t
        while node[0] != k:
            node = node[1] if k < node[0] else node[2]
            dpt += 1
        dep[k] = dpt
    assert max(dep.values()) == h
    return Draft(f"On insère successivement {lst(keys)} dans un arbre binaire de recherche initialement vide (plus petit à gauche). Quelle est sa hauteur (nombre d'arêtes du plus long chemin de la racine à une feuille) ?", str(h),
                 [str(x) for x in (h - 1, h + 1, h + 2, n - 1, (n - 1).bit_length()) if x != h and x >= 0],
                 f"On construit l'arbre : la clé la plus profonde est à {h} arêtes de la racine {keys[0]}.", src=S3)


@gen(C3, "inf3-bst-leaves", cap=110, cat=CT)
def bst_leaves(rng, d):
    n = rng.randint(5, 8)
    keys = rng.sample(range(1, 60), n)
    t = build_bst(keys)
    lv = leaves(t)
    fi = sum(1 for k in keys if not any(k == kk for kk in []) and True)
    nodes = {}
    # recoupement : une feuille est une clé qui n'est le parent d'aucune autre
    parents = set()
    for i, k in enumerate(keys[1:], 1):
        node = t
        while True:
            nxt = node[1] if k < node[0] else node[2]
            if nxt[0] == k and nxt[1] is None and nxt[2] is None and False:
                break
            if nxt is None:
                break
            if nxt[0] == k:
                parents.add(node[0])
                break
            node = nxt
    assert lv == n - len(parents)
    return Draft(f"On insère {lst(keys)} dans un arbre binaire de recherche vide (plus petit à gauche, plus grand à droite). Combien l'arbre compte-t-il de feuilles ?", str(lv),
                 [str(x) for x in near_ints(rng, lv, 6, lo=1, hi=n)] + [str(n - 1)],
                 f"Une feuille est un nœud sans fils : on en trouve {lv}.", src=S3)


@gen(C3, "inf3-bst-traversal", cap=130, cat=CT)
def bst_traversal(rng, d):
    n = rng.randint(5, 7 + d // 2)
    keys = rng.sample(range(1, 50), n)
    t = build_bst(keys)
    kind = rng.choice(["préfixe", "infixe", "postfixe"])
    f = {"préfixe": pre, "infixe": ino, "postfixe": post}[kind]
    it = {"préfixe": pre_iter, "infixe": in_iter, "postfixe": post_iter}[kind]
    assert f(t) == it(t)
    assert ino(t) == sorted(keys)
    others = [pre(t), ino(t), post(t), level(t)]
    wr = [lst(x) for x in others if x != f(t)]
    return Draft(f"On insère {lst(keys)} dans cet ordre dans un arbre binaire de recherche vide (plus petit à gauche). Quel est le parcours {kind} de l'arbre ?", lst(f(t)),
                 wr, {"préfixe": "Préfixe : racine, sous-arbre gauche, sous-arbre droit.", "infixe": "Infixe : gauche, racine, droite ; sur un arbre de recherche, on obtient l'ordre croissant.",
                      "postfixe": "Postfixe : gauche, droite, puis racine."}[kind], src=S3)


def rand_tree(rng, letters):
    if not letters:
        return None
    root, rest = letters[0], letters[1:]
    k = rng.randint(0, len(rest))
    return [root, rand_tree(rng, rest[:k]), rand_tree(rng, rest[k:])]


def tshow(t):
    if t[1] is None and t[2] is None:
        return t[0]
    return f"{t[0]}({tshow(t[1]) if t[1] else '∅'},{tshow(t[2]) if t[2] else '∅'})"


@gen(C3, "inf3-tree-notation", cap=130, cat=CT)
def tree_notation(rng, d):
    n = rng.randint(5, 7)
    letters = rng.sample(string.ascii_uppercase[:12], n)
    t = rand_tree(rng, letters)
    kind = rng.choice(["préfixe", "infixe", "postfixe"])
    f = {"préfixe": pre, "infixe": ino, "postfixe": post}[kind]
    it = {"préfixe": pre_iter, "infixe": in_iter, "postfixe": post_iter}[kind]
    assert f(t) == it(t)
    s = lambda x: "".join(x)
    wr = [s(x) for x in (pre(t), ino(t), post(t), level(t)) if x != f(t)]
    return Draft(f"Un arbre binaire est noté racine(fils gauche, fils droit), ∅ signalant un fils absent et une feuille s'écrivant seule : {tshow(t)}. Quel est son parcours {kind} (lettres à la suite) ?", s(f(t)),
                 wr + [s(f(t)[::-1])], f"On applique la règle du parcours {kind} récursivement : {s(f(t))}.", src=S3)


# ---- graphes
def rand_graph(rng, n, extra):
    vs = list(string.ascii_uppercase[:n])
    es = set()
    for i in range(1, n):
        j = rng.randrange(i)
        es.add(tuple(sorted((vs[i], vs[j]))))
    cand = [tuple(sorted(p)) for p in itertools.combinations(vs, 2) if tuple(sorted(p)) not in es]
    rng.shuffle(cand)
    es |= set(cand[:extra])
    return vs, sorted(es)


def adj_of(vs, es):
    ad = {v: [] for v in vs}
    for a, b in es:
        ad[a].append(b)
        ad[b].append(a)
    return {v: sorted(l) for v, l in ad.items()}


def etxt(es):
    return ", ".join(f"{a}–{b}" for a, b in es)


@gen(C3, "inf3-graph-degree", cap=110, cat=CGr)
def graph_degree(rng, d):
    n = rng.randint(4, 6)
    vs, es = rand_graph(rng, n, rng.randint(1, 3))
    ad = adj_of(vs, es)
    v = rng.choice(vs)
    deg = len(ad[v])
    assert deg == sum(1 for e in es if v in e)
    assert sum(len(l) for l in ad.values()) == 2 * len(es)
    return Draft(f"Graphe non orienté de sommets {', '.join(vs)} et d'arêtes {etxt(es)}. Quel est le degré du sommet {v} ?", str(deg),
                 [str(x) for x in near_ints(rng, deg, 6, lo=0, hi=n)] + [str(len(es))],
                 f"Le sommet {v} est extrémité de {deg} arête{'s' if deg > 1 else ''}.", src=S3)


@gen(C3, "inf3-graph-maxdeg", cap=80, cat=CGr)
def graph_maxdeg(rng, d):
    for _ in range(40):
        n = rng.randint(5, 7)
        vs, es = rand_graph(rng, n, rng.randint(1, 4))
        ad = adj_of(vs, es)
        m = max(len(l) for l in ad.values())
        top = [v for v in vs if len(ad[v]) == m]
        if len(top) == 1 and len({len(ad[v]) for v in vs}) >= 2:
            break
    else:
        return None
    assert top[0] == max(vs, key=lambda v: sum(1 for e in es if v in e))
    return Draft(f"Graphe non orienté d'arêtes {etxt(es)}. Quel sommet a le plus grand degré ?", top[0],
                 [v for v in vs if v != top[0]], f"Le sommet {top[0]} a {m} voisins, plus qu'aucun autre.", src=S3)


@gen(C3, "inf3-graph-handshake", cap=110, cat=CGr)
def graph_handshake(rng, d):
    n = rng.randint(4, 8)
    vs, es = rand_graph(rng, n, rng.randint(0, 5))
    ad = adj_of(vs, es)
    degs = [len(ad[v]) for v in vs]
    assert sum(degs) % 2 == 0 and sum(degs) // 2 == len(es)
    return Draft(f"Un graphe simple non orienté a pour degrés {lst(degs)}. Combien a-t-il d'arêtes ?", str(len(es)),
                 [str(x) for x in (sum(degs), len(es) + 1, len(es) - 1, n - 1, sum(degs) // 4 if sum(degs) % 4 == 0 else len(es) + 2) if x != len(es) and x >= 0],
                 f"La somme des degrés vaut 2 fois le nombre d'arêtes : {sum(degs)} ÷ 2 = {len(es)}.", src=S3)


@gen(C3, "inf3-graph-complete", cap=50, cat=CGr)
def graph_complete(rng, d):
    n = rng.randint(3, 40)
    m = len(list(itertools.combinations(range(n), 2)))
    assert m == n * (n - 1) // 2
    return Draft(f"Quel est le nombre maximal d'arêtes d'un graphe simple non orienté à {n} sommets ?", fr(m),
                 [fr(x) for x in (n * (n - 1), n * n, n * (n + 1) // 2, n - 1, 2 * n, n * (n - 2) // 2) if x != m],
                 f"Chaque paire de sommets au plus une fois : {n} × {n - 1} ÷ 2 = {fr(m)}.", src=S3)


def bfs(ad, s):
    seen, q, out = {s}, deque([s]), []
    while q:
        u = q.popleft()
        out.append(u)
        for w in ad[u]:
            if w not in seen:
                seen.add(w)
                q.append(w)
    return out


def bfs_layers(ad, s):
    seen, frontier, out = {s}, [s], []
    while frontier:
        out += frontier
        nxt = []
        for u in frontier:
            for w in ad[u]:
                if w not in seen:
                    seen.add(w)
                    nxt.append(w)
        frontier = nxt
    return out


def dfs(ad, s):
    seen, out = set(), []
    def go(u):
        seen.add(u)
        out.append(u)
        for w in ad[u]:
            if w not in seen:
                go(w)
    go(s)
    return out


def dfs_stack(ad, s):
    seen, out, st = set(), [], [s]
    while st:
        u = st.pop()
        if u in seen:
            continue
        seen.add(u)
        out.append(u)
        for w in reversed(ad[u]):
            if w not in seen:
                st.append(w)
    return out


@gen(C3, "inf3-graph-bfs", cap=130, cat=CGr)
def graph_bfs(rng, d):
    n = rng.randint(5, 7)
    vs, es = rand_graph(rng, n, rng.randint(1, 4))
    ad = adj_of(vs, es)
    s = rng.choice(vs[:2])
    r = bfs(ad, s)
    assert r == bfs_layers(ad, s) and len(r) == n
    wr = [dfs(ad, s), bfs({v: l[::-1] for v, l in ad.items()}, s), sorted(vs), dfs({v: l[::-1] for v, l in ad.items()}, s)]
    return Draft(f"Graphe non orienté d'arêtes {etxt(es)}. Quel est l'ordre de visite d'un parcours en largeur (BFS) depuis {s}, en examinant les voisins par ordre alphabétique ?", " ".join(r),
                 [" ".join(x) for x in wr if x != r], f"On visite {s}, puis ses voisins, puis les voisins de ceux-ci, dans l'ordre alphabétique : {' '.join(r)}.", src=S3)


@gen(C3, "inf3-graph-dfs", cap=130, cat=CGr)
def graph_dfs(rng, d):
    n = rng.randint(5, 7)
    vs, es = rand_graph(rng, n, rng.randint(1, 4))
    ad = adj_of(vs, es)
    s = rng.choice(vs[:2])
    r = dfs(ad, s)
    assert r == dfs_stack(ad, s) and len(r) == n
    wr = [bfs(ad, s), dfs({v: l[::-1] for v, l in ad.items()}, s), sorted(vs), bfs({v: l[::-1] for v, l in ad.items()}, s)]
    return Draft(f"Graphe non orienté d'arêtes {etxt(es)}. Quel est l'ordre de visite d'un parcours en profondeur (DFS récursif) depuis {s}, en choisissant les voisins par ordre alphabétique ?", " ".join(r),
                 [" ".join(x) for x in wr if x != r], f"On descend toujours au premier voisin non visité avant de revenir en arrière : {' '.join(r)}.", src=S3)


def wgraph(rng, n, extra):
    vs, es = rand_graph(rng, n, extra)
    return vs, [(a, b, rng.randint(1, 9)) for a, b in es]


def dijkstra(vs, wes, s):
    ad = {v: [] for v in vs}
    for a, b, w in wes:
        ad[a].append((b, w))
        ad[b].append((a, w))
    dist = {v: math.inf for v in vs}
    dist[s] = 0
    pq = [(0, s)]
    while pq:
        dd, u = heapq.heappop(pq)
        if dd > dist[u]:
            continue
        for v, w in ad[u]:
            if dd + w < dist[v]:
                dist[v] = dd + w
                heapq.heappush(pq, (dist[v], v))
    return dist


def floyd(vs, wes):
    D = {(a, b): (0 if a == b else math.inf) for a in vs for b in vs}
    for a, b, w in wes:
        D[a, b] = min(D[a, b], w)
        D[b, a] = min(D[b, a], w)
    for k in vs:
        for i in vs:
            for j in vs:
                if D[i, k] + D[k, j] < D[i, j]:
                    D[i, j] = D[i, k] + D[k, j]
    return D


def simple_paths(vs, wes, s, t):
    ad = {v: [] for v in vs}
    for a, b, w in wes:
        ad[a].append((b, w))
        ad[b].append((a, w))
    out = []
    def go(u, path, w):
        if u == t:
            out.append((path[:], w))
            return
        for v, x in ad[u]:
            if v not in path:
                path.append(v)
                go(v, path, w + x)
                path.pop()
    go(s, [s], 0)
    return out


def wtxt(wes):
    return ", ".join(f"{a}–{b} ({w})" for a, b, w in wes)


@gen(C3, "inf3-dijkstra-dist", cap=130, cat=CGr)
def dijkstra_dist(rng, d):
    n = rng.randint(5, 6)
    vs, wes = wgraph(rng, n, rng.randint(2, 4))
    s, t = "A", rng.choice(vs[2:])
    dist = dijkstra(vs, wes, s)
    D = floyd(vs, wes)
    assert dist[t] == D[s, t]
    paths = simple_paths(vs, wes, s, t)
    assert min(w for _, w in paths) == dist[t]
    fewest = min(paths, key=lambda p: (len(p[0]), p[1]))[1]
    ws = sorted({w for _, w in paths})
    wr = [fewest, dist[t] + 1, dist[t] - 1, dist[t] + 2] + [w for w in ws if w != dist[t]][:2]
    wr = [x for x in wr if x != dist[t] and x > 0]
    return Draft(f"Graphe non orienté pondéré : {wtxt(wes)}. Quelle est la longueur du plus court chemin de {s} à {t} ?", str(dist[t]),
                 [str(x) for x in wr], f"L'algorithme de Dijkstra depuis {s} donne une distance de {dist[t]} jusqu'à {t}.", src=S3)


@gen(C3, "inf3-dijkstra-path", cap=110, cat=CGr)
def dijkstra_path(rng, d):
    for _ in range(40):
        n = rng.randint(5, 6)
        vs, wes = wgraph(rng, n, rng.randint(3, 5))
        t = rng.choice(vs[3:])
        paths = simple_paths(vs, wes, "A", t)
        ws = [w for _, w in paths]
        mn = min(ws)
        if len(paths) >= 4 and ws.count(mn) == 1 and len({w for w in ws}) >= 4:
            break
    else:
        return None
    best = [p for p, w in paths if w == mn][0]
    assert dijkstra(vs, wes, "A")[t] == mn
    others = [p for p, w in paths if w != mn]
    wr = rng.sample(others, 3)
    sh = lambda p: "–".join(p)
    return Draft(f"Graphe non orienté pondéré : {wtxt(wes)}. Lequel de ces chemins de A à {t} est le plus court (somme des poids minimale) ?", sh(best),
                 [sh(p) for p in wr], f"Le chemin {sh(best)} a un poids total de {mn}, inférieur à celui des autres chemins proposés.", src=S3)


# ---- hachage
@gen(C3, "inf3-hash-index", cap=90, cat=CH)
def hash_index(rng, d):
    m = rng.randint(5, 13)
    k = rng.randint(50, 9999)
    h = k % m
    assert h == k - m * (k // m)
    return Draft(f"Une table de hachage a {m} cases (0 à {m - 1}) et la fonction h(k) = k mod {m}. Dans quelle case la clé {k} est-elle rangée ?", str(h),
                 [str(x) for x in (k // m % m, (h + 1) % m, (h - 1) % m, k % (m + 1) % m, m - h if m - h != h else h + 2) if x != h],
                 f"{k} = {k // m} × {m} + {h} : le reste est {h}.", src=S3)


@gen(C3, "inf3-hash-collision", cap=110, cat=CH)
def hash_collision(rng, d):
    m = rng.randint(5, 9)
    n = rng.randint(5, 9)
    keys = rng.sample(range(10, 200), n)
    occ, coll = set(), 0
    for k in keys:
        if k % m in occ:
            coll += 1
        occ.add(k % m)
    assert coll == n - len({k % m for k in keys})
    return Draft(f"Avec h(k) = k mod {m} et un chaînage, on insère dans l'ordre {lst(keys)}. Combien de clés tombent dans une case déjà occupée au moment de leur insertion ?", str(coll),
                 [str(x) for x in near_ints(rng, coll, 6, lo=0, hi=n)] + [str(n - coll)],
                 f"Il y a {len({k % m for k in keys})} cases utilisées pour {n} clés : {coll} collisions.", src=S3)


@gen(C3, "inf3-hash-chain", cap=110, cat=CH)
def hash_chain(rng, d):
    m = rng.randint(4, 8)
    n = rng.randint(7, 11)
    keys = rng.sample(range(10, 300), n)
    cnt = Counter(k % m for k in keys)
    buckets = [[k for k in keys if k % m == i] for i in range(m)]
    mx = max(len(b) for b in buckets)
    assert mx == max(cnt.values())
    return Draft(f"Table de hachage à chaînage de {m} cases, h(k) = k mod {m}, clés insérées : {lst(keys)}. Quelle est la longueur de la plus longue liste ?", str(mx),
                 [str(x) for x in near_ints(rng, mx, 6, lo=1, hi=n)] + [str(math.ceil(n / m))],
                 f"On répartit les clés par reste modulo {m} ; la case la plus chargée contient {mx} clés.", src=S3)


@gen(C3, "inf3-hash-probe", cap=130, cat=CH)
def hash_probe(rng, d):
    m = rng.randint(7, 11)
    n = rng.randint(4, m - 2)
    keys = rng.sample(range(10, 200), n)
    tab = [None] * m
    pos = {}
    for k in keys:
        i = k % m
        while tab[i] is not None:
            i = (i + 1) % m
        tab[i] = k
        pos[k] = i
    # recoupement : suite de sondage explicite
    tab2 = {}
    for k in keys:
        for step in range(m):
            c = (k % m + step) % m
            if c not in tab2:
                tab2[c] = k
                break
    assert all(tab[i] == tab2.get(i) for i in range(m))
    last = keys[-1]
    return Draft(f"Table de {m} cases (0 à {m - 1}), h(k) = k mod {m}, sondage linéaire (case suivante en cas de collision, retour à 0 après {m - 1}). On insère {lst(keys)}. Dans quelle case se retrouve {last} ?", str(pos[last]),
                 [str(x) for x in ((last % m), (pos[last] + 1) % m, (pos[last] - 1) % m, (last % m + 2) % m, (pos[last] + 2) % m) if x != pos[last]],
                 f"{last} mod {m} = {last % m} ; en cas de case occupée on avance : case {pos[last]}.", src=S3)


# ---- RSA jouet et hachage jouet
PRIMES = [5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43]


def rsa_params(rng):
    for _ in range(100):
        p, q = rng.sample(PRIMES, 2)
        n, phi = p * q, (p - 1) * (q - 1)
        es = [e for e in (3, 5, 7, 11, 13, 17, 19, 23) if e < phi and math.gcd(e, phi) == 1]
        if not es:
            continue
        e = rng.choice(es)
        d = pow(e, -1, phi)
        return p, q, n, phi, e, d
    raise AssertionError("pas de paramètres RSA")


def modexp(b, e, m):
    r = 1
    b %= m
    while e:
        if e & 1:
            r = r * b % m
        b = b * b % m
        e >>= 1
    return r


@gen(C3, "inf3-rsa-encrypt", cap=130, cat=CCr)
def rsa_encrypt(rng, d):
    p, q, n, phi, e, dd = rsa_params(rng)
    m = rng.randint(2, n - 2)
    c = pow(m, e, n)
    if c == m:
        return None
    assert c == modexp(m, e, n) and pow(c, dd, n) == m
    return Draft(f"RSA jouet : clé publique (n = {n}, e = {e}). On chiffre le message m = {m} par c = mᵉ mod n. Que vaut c ?", str(c),
                 [str(x) for x in (m * e % n, (m + e) % n, pow(m, e, n + 1) % n, m ** 2 % n, pow(m, dd, n), (c + 1) % n, (c - 1) % n) if x != c],
                 f"c = {m}^{e} mod {n} = {c}.", src=S3)


@gen(C3, "inf3-rsa-decrypt", cap=130, cat=CCr)
def rsa_decrypt(rng, d):
    p, q, n, phi, e, dd = rsa_params(rng)
    m = rng.randint(2, n - 2)
    c = pow(m, e, n)
    if c == m:
        return None
    assert modexp(c, dd, n) == m
    return Draft(f"RSA jouet : clé privée (n = {n}, d = {dd}). On reçoit le chiffré c = {c}. Quel message m = c^d mod n retrouve-t-on ?", str(m),
                 [str(x) for x in (c * dd % n, (c - dd) % n, (c + 1) % n, c, (m + 1) % n, (m - 1) % n, pow(c, e, n)) if x != m],
                 f"m = {c}^{dd} mod {n} = {m} (c'est le message d'origine).", src=S3)


@gen(C3, "inf3-rsa-key", cap=110, cat=CCr)
def rsa_key(rng, d):
    p, q, n, phi, e, dd = rsa_params(rng)
    brute = next(x for x in range(1, phi + 1) if e * x % phi == 1)
    assert brute == dd
    return Draft(f"RSA jouet avec p = {p}, q = {q} et e = {e}. Quel est le plus petit exposant privé d > 0 tel que e × d ≡ 1 (mod φ(n)) ?", str(dd),
                 [str(x) for x in (phi - dd, dd + phi, e, (n - 1) // e if (n - 1) % e == 0 else dd + 1, pow(e, -1, p * q) if math.gcd(e, n) == 1 and pow(e, -1, n) != dd else dd + 2, dd + 1) if x != dd and x > 0],
                 f"φ(n) = {p - 1} × {q - 1} = {phi} ; l'inverse de {e} modulo {phi} est {dd}.", src=S3)


@gen(C3, "inf3-rsa-phi", cap=90, cat=CCr)
def rsa_phi(rng, d):
    p, q = rng.sample(PRIMES, 2)
    n, phi = p * q, (p - 1) * (q - 1)
    assert phi == sum(1 for x in range(1, n + 1) if math.gcd(x, n) == 1)
    return Draft(f"RSA jouet : n = p × q avec p = {p} et q = {q}. Combien vaut φ(n), le nombre d'entiers de 1 à n premiers avec n ?", str(phi),
                 [str(x) for x in (n - 1, n - p - q, n - p - q + 2, p + q - 2, n + 1, phi + 2, phi - 2) if x != phi],
                 f"φ(n) = ({p} − 1) × ({q} − 1) = {phi}.", src=S3)


@gen(C3, "inf3-toyhash-letters", cap=130, cat=CCr)
def toyhash_letters(rng, d):
    m = rng.randint(5, 13)
    w = "".join(rng.choice(string.ascii_uppercase) for _ in range(rng.randint(3, 6)))
    s = sum(ord(c) - 64 for c in w)
    assert s == sum(string.ascii_uppercase.index(c) + 1 for c in w)
    h = s % m
    return Draft(f"Hachage jouet : h(mot) = (somme des rangs des lettres dans l'alphabet, A = 1 … Z = 26) mod {m}. Que vaut h({w}) ?", str(h),
                 [str(x) for x in (s, (s + 1) % m, (s - 1) % m, s % (m + 1) % m, (s // m) % m, sum(ord(c) - 65 for c in w) % m) if x != h],
                 f"Somme des rangs = {s} ; {s} mod {m} = {h}.", src=S3)


@gen(C3, "inf3-toyhash-fold", cap=130, cat=CCr)
def toyhash_fold(rng, d):
    m = rng.randint(7, 31)
    nb = rng.choice([3, 4])
    n = rng.randint(10 ** (2 * nb - 1), 10 ** (2 * nb) - 1)
    s = str(n)
    blocks = [int(s[i:i + 2]) for i in range(0, len(s), 2)]
    t, x = 0, n
    while x:
        t += x % 100
        x //= 100
    assert t == sum(blocks)
    h = t % m
    return Draft(f"Hachage jouet : on coupe l'entier en blocs de 2 chiffres depuis la gauche, on additionne les blocs, puis on prend le reste modulo {m}. Que vaut h({n}) ?", str(h),
                 [str(x) for x in (t, (t + 1) % m, (t - 1) % m, n % m, sum(int(c) for c in s) % m, (t // m) % m) if x != h],
                 f"Blocs {' + '.join(map(str, blocks))} = {t} ; {t} mod {m} = {h}.", src=S3)


@gen(C3, "inf3-toyhash-collide", cap=130, cat=CH)
def toyhash_collide(rng, d):
    m = rng.randint(7, 13)
    a, b = rng.randint(2, 9), rng.randint(0, 9)
    if math.gcd(a, m) != 1:
        return None
    h = lambda x: (a * x + b) % m
    x0 = rng.randint(10, 99)
    same = [x for x in range(10, 200) if h(x) == h(x0) and x != x0]
    diff = [x for x in range(10, 200) if h(x) != h(x0)]
    if len(same) < 1:
        return None
    r = rng.choice(same)
    wr = rng.sample(diff, 3)
    assert h(r) == h(x0) and all(h(w) != h(x0) for w in wr)
    return Draft(f"Avec h(x) = ({a}x + {b}) mod {m}, quelle clé est en collision avec {x0} (même case) ?", str(r),
                 [str(w) for w in wr], f"h({x0}) = {h(x0)} = h({r}) ; les autres propositions donnent d'autres valeurs.", src=S3)


# ---- force brute
@gen(C3, "inf3-brute-avg", cap=50, cat=CCr)
def brute_avg(rng, d):
    n = rng.randint(8, 64)
    big = n > 20
    f = lambda e: sup(2) if False else (f"2{sup(e)}" if big else fr(2 ** e))
    return Draft(f"Une clé secrète de {n} bits est retrouvée par essai exhaustif de toutes les clés. En moyenne, combien d'essais faut-il ?", f(n - 1),
                 [f(x) for x in (n, n - 2, n + 1, n // 2, n - 3)] if big else [f(x) for x in (n, n - 2, n + 1, n // 2, n - 3)],
                 f"Il y a 2^{n} clés équiprobables ; on en essaie en moyenne la moitié, soit 2^{n - 1}.", src=S3)


@gen(C3, "inf3-brute-time", cap=100, cat=CCr)
def brute_time(rng, d):
    r = rng.randint(4, 20)
    n = r + rng.randint(2, 20)
    rate = 2 ** r
    worst = rng.random() < 0.5
    t = 2 ** (n - r) if worst else 2 ** (n - r - 1)
    assert Fraction(2 ** n, rate) == 2 ** (n - r)
    return Draft(f"Un attaquant teste {fr(rate)} clés par seconde sur un espace de clés de {n} bits. Combien de secondes lui faut-il {'au pire pour essayer toutes les clés' if worst else 'en moyenne pour trouver la clé'} ?", fr(t),
                 [fr(x) for x in (2 ** (n - r) if not worst else 2 ** (n - r - 1), 2 ** (n - r + 1), 2 ** (n - r - 2), n - r, 2 ** n // 1000) if x != t and x > 0],
                 f"2^{n} ÷ 2^{r} = 2^{n - r} = {fr(2 ** (n - r))} s au pire ; la moyenne en est la moitié.", src=S3)


@gen(C3, "inf3-brute-prob", cap=70, cat=CCr)
def brute_prob(rng, d):
    n = rng.randint(6, 30)
    j = rng.randint(0, 4)
    k = 2 ** j
    p = Fraction(k, 2 ** n)
    assert p == Fraction(1, 2 ** (n - j))
    f = lambda e: "1/" + fr(2 ** e)
    return Draft(f"Une clé est tirée uniformément parmi 2{sup(n)} possibles. Un attaquant en essaie {fr(k)} différentes. Quelle est sa probabilité de réussite ?", f(n - j),
                 [f(x) for x in (n, n + j, n - j + 1, n - j - 1 if n - j > 1 else n + 3, n // 2)] if True else [],
                 f"{fr(k)} essais parmi 2^{n} clés : {fr(k)}/2^{n} = 1/2^{n - j}.", src=S3)


@gen(C3, "inf3-brute-space", cap=110, cat=CCr)
def brute_space(rng, d):
    a, nm = rng.choice([(10, "chiffres"), (26, "lettres minuscules"), (36, "lettres minuscules et chiffres"), (52, "lettres minuscules et majuscules"), (62, "lettres et chiffres, majuscules et minuscules")])
    L = rng.randint(3, 8)
    v = a ** L
    assert math.prod([a] * L) == v
    if v <= 60000:
        assert sum(1 for _ in itertools.product(range(a), repeat=L)) == v
    return Draft(f"Combien de mots de passe de {L} caractères peut-on former avec un alphabet de {a} symboles ({nm}), répétitions permises ?", fr(v),
                 [fr(x) for x in (a * L, a ** (L - 1), a ** (L + 1), math.perm(a, L), a ** L - a, a ** L // 2) if x != v],
                 f"Chaque position a {a} choix : {a}^{L} = {fr(v)}.", src=S3)


@gen(C3, "inf3-brute-bits", cap=90, cat=CCr)
def brute_bits(rng, d):
    a, nm = rng.choice([(10, "chiffres"), (26, "lettres minuscules"), (36, "lettres minuscules et chiffres"), (62, "lettres et chiffres, majuscules et minuscules")])
    L = rng.randint(4, 12)
    v = a ** L
    b = 0
    while 2 ** b < v:
        b += 1
    assert b == (v - 1).bit_length()
    return Draft(f"Un mot de passe de {L} symboles tirés parmi {a} ({nm}) a {fr(v)} valeurs possibles. Combien de bits faut-il au minimum pour le numéroter ?", str(b),
                 [str(x) for x in (b - 1, b + 1, b + 2, L * a.bit_length() - 1, b - 2) if x != b and x > 0],
                 f"Il faut le plus petit b tel que 2^b ≥ {fr(v)} : b = {b}.", src=S3)


# ---- Huffman
def huff_cost(freq):
    h = list(freq)
    heapq.heapify(h)
    tot = 0
    while len(h) > 1:
        a, b = heapq.heappop(h), heapq.heappop(h)
        tot += a + b
        heapq.heappush(h, a + b)
    return tot


def huff_cost_lengths(freq):
    nodes = [(f, [i]) for i, f in enumerate(freq)]
    depth = [0] * len(freq)
    while len(nodes) > 1:
        nodes.sort(key=lambda x: x[0])
        (f1, l1), (f2, l2) = nodes[0], nodes[1]
        for i in l1 + l2:
            depth[i] += 1
        nodes = nodes[2:] + [(f1 + f2, l1 + l2)]
    return sum(f * dd for f, dd in zip(freq, depth))


def huff_inputs(rng):
    k = rng.randint(4, 6)
    letters = list(string.ascii_uppercase[:k])
    freq = [rng.randint(1, 20) for _ in range(k)]
    return letters, freq


@gen(C3, "inf3-huffman-cost", cap=130, cat=CCr)
def huffman_cost(rng, d):
    letters, freq = huff_inputs(rng)
    c = huff_cost(freq)
    assert c == huff_cost_lengths(freq)
    fixed = sum(freq) * math.ceil(math.log2(len(freq)))
    txt = ", ".join(f"{a} : {f}" for a, f in zip(letters, freq))
    return Draft(f"Un message est écrit avec des symboles dont les occurrences sont : {txt}. Avec un codage de Huffman, combien de bits compte le message codé ?", str(c),
                 [str(x) for x in (fixed, c + 1, c - 1, c + 2, sum(freq), c + sum(freq) // 2) if x != c and x > 0],
                 f"On fusionne successivement les deux plus petits poids ; la somme des fusions donne {c} bits.", src=S3)


@gen(C3, "inf3-huffman-gain", cap=130, cat=CCr)
def huffman_gain(rng, d):
    letters, freq = huff_inputs(rng)
    c = huff_cost(freq)
    fixed = sum(freq) * math.ceil(math.log2(len(freq)))
    assert c == huff_cost_lengths(freq) and c <= fixed
    gain = fixed - c
    if gain == 0:
        return None
    txt = ", ".join(f"{a} : {f}" for a, f in zip(letters, freq))
    bits = math.ceil(math.log2(len(freq)))
    return Draft(f"Occurrences : {txt}. Un codage de longueur fixe utilise {bits} bits par symbole. Combien de bits économise le codage de Huffman sur l'ensemble du message ?", str(gain),
                 [str(x) for x in (c, fixed, gain + 1, gain - 1, gain + 2, gain + 3) if x != gain and x > 0],
                 f"Longueur fixe : {sum(freq)} × {bits} = {fixed} bits ; Huffman : {c} bits ; économie : {gain} bits.", src=S3)


# ---- automates
def rand_dfa(rng, k):
    delta = {(q, a): rng.randrange(k) for q in range(k) for a in "ab"}
    acc = set(rng.sample(range(k), rng.randint(1, k - 1)))
    return delta, acc


def dfa_show(delta, acc, k):
    tr = ", ".join(f"δ(q{q},{a}) = q{delta[q, a]}" for q in range(k) for a in "ab")
    return f"Automate d'état initial q0, d'états d'acceptation {{{', '.join('q' + str(x) for x in sorted(acc))}}} : {tr}"


def dfa_run(delta, w):
    q = 0
    for ch in w:
        q = delta[q, ch]
    return q


@gen(C3, "inf3-dfa-state", cap=130, cat=CCr)
def dfa_state(rng, d):
    k = 4
    delta, acc = rand_dfa(rng, k)
    w = "".join(rng.choice("ab") for _ in range(rng.randint(4, 8)))
    q = dfa_run(delta, w)
    seq = [0]
    for ch in w:
        seq.append(delta[seq[-1], ch])
    assert seq[-1] == q
    return Draft(f"{dfa_show(delta, acc, k)}. Dans quel état se trouve l'automate après lecture du mot {w} ?", f"q{q}",
                 [f"q{x}" for x in range(k) if x != q], f"Parcours : {' → '.join('q' + str(x) for x in seq)}.", src=S3)


@gen(C3, "inf3-dfa-accept", cap=130, cat=CCr)
def dfa_accept(rng, d):
    k = rng.choice([3, 4])
    for _ in range(40):
        delta, acc = rand_dfa(rng, k)
        words = ["".join(rng.choice("ab") for _ in range(rng.randint(3, 6))) for _ in range(12)]
        words = list(dict.fromkeys(words))
        good = [w for w in words if dfa_run(delta, w) in acc]
        bad = [w for w in words if dfa_run(delta, w) not in acc]
        if good and len(bad) >= 3:
            break
    else:
        return None
    r = rng.choice(good)
    wr = rng.sample(bad, 3)
    return Draft(f"{dfa_show(delta, acc, k)}. Lequel de ces mots est accepté ?", r, wr,
                 f"La lecture de {r} s'achève dans l'état d'acceptation q{dfa_run(delta, r)} ; les autres mots finissent hors de l'ensemble d'acceptation.", src=S3)


@gen(C3, "inf3-dfa-count", cap=110, cat=CCr)
def dfa_count(rng, d):
    k = rng.choice([3, 4])
    delta, acc = rand_dfa(rng, k)
    n = rng.randint(2, 6)
    c = sum(1 for w in itertools.product("ab", repeat=n) if dfa_run(delta, "".join(w)) in acc)
    vec = [1] + [0] * (k - 1)
    for _ in range(n):
        nv = [0] * k
        for q in range(k):
            for a in "ab":
                nv[delta[q, a]] += vec[q]
        vec = nv
    assert c == sum(vec[q] for q in acc)
    return Draft(f"{dfa_show(delta, acc, k)}. Parmi les {2 ** n} mots de longueur {n} sur {{a, b}}, combien sont acceptés ?", str(c),
                 [str(x) for x in near_ints(rng, c, 6, lo=0, hi=2 ** n)] + [str(2 ** n - c)],
                 f"On énumère les {2 ** n} mots et on compte ceux qui terminent dans un état d'acceptation : {c}.", src=S3)


# ---- piles et files
def rand_ops(rng, n, struct):
    ops, size, v = [], 0, rng.sample(range(1, 30), n)
    it = iter(v)
    while len(ops) < n:
        if size == 0 or rng.random() < 0.6:
            ops.append(("push", next(it)))
            size += 1
        else:
            ops.append(("pop", None))
            size -= 1
    return ops


def ops_txt(ops, struct):
    a, b = ("EMPILER", "DÉPILER") if struct == "pile" else ("ENFILER", "DÉFILER")
    return ", ".join(f"{a}({x})" if o == "push" else b for o, x in ops)


def run_ops(ops, struct):
    if struct == "pile":
        s, out = [], []
        for o, x in ops:
            if o == "push":
                s.append(x)
            else:
                out.append(s.pop())
        return s, out
    q, out = deque(), []
    for o, x in ops:
        if o == "push":
            q.append(x)
        else:
            out.append(q.popleft())
    return list(q), out


def run_ops2(ops, struct):
    data, out, head = [], [], 0
    for o, x in ops:
        if o == "push":
            data.append(x)
        elif struct == "pile":
            out.append(data.pop())
        else:
            out.append(data[head])
            head += 1
    return (data if struct == "pile" else data[head:]), out


def opsgen(struct, ask):
    def g(rng, d):
        n = rng.randint(5, 7 + d // 2)
        ops = rand_ops(rng, n, struct)
        cont, out = run_ops(ops, struct)
        assert (cont, out) == run_ops2(ops, struct)
        other = "file" if struct == "pile" else "pile"
        ocont, oout = run_ops(ops, other)
        if not cont:
            return None
        nm = "La pile" if struct == "pile" else "La file"
        if ask == "top":
            r = cont[-1] if struct == "pile" else cont[0]
            wr = [cont[0] if struct == "pile" else cont[-1], out[-1] if out else cont[0] + 1, cont[-2] if len(cont) > 1 else cont[0] + 1, max(cont) + 1]
            q = f"{nm} est vide au départ. On exécute : {ops_txt(ops, struct)}. Quel est " + ("l'élément au sommet de la pile" if struct == "pile" else "l'élément en tête de la file (prochain à sortir)") + " ?"
            expl = "On simule les opérations dans l'ordre : " + ("le dernier empilé non encore retiré est au sommet." if struct == "pile" else "le premier enfilé non encore retiré est en tête.")
            return Draft(q, str(r), [str(x) for x in wr] + [str(max(cont) + 3)], expl, src=S3)
        if ask == "content":
            r = lst(cont)
            wr = [lst(cont[::-1]), lst(ocont), lst(cont[:-1]) if len(cont) > 1 else lst(cont + [0]), lst(sorted(cont))]
            q = f"{nm} est vide au départ. On exécute : {ops_txt(ops, struct)}. Quel est son contenu final, " + ("du fond vers le sommet" if struct == "pile" else "de la tête vers la queue") + " ?"
            return Draft(q, r, [w for w in wr if w != r], f"Simulation : contenu final {r}.", src=S3)
        if ask == "out":
            r = lst(out)
            wr = [lst(oout), lst(out[::-1]), lst(sorted(out)), lst(out[:-1]) if len(out) > 1 else lst(out + [0])]
            q = f"{nm} est vide au départ. On exécute : {ops_txt(ops, struct)}. Quelles valeurs sont retirées, dans l'ordre des retraits ?"
            return Draft(q, r, [w for w in wr if w != r], f"Valeurs retirées successivement : {r}.", src=S3)
    return g


for _s, _a in (("pile", "top"), ("pile", "content"), ("pile", "out"), ("file", "top"), ("file", "content"), ("file", "out")):
    gen(C3, f"inf3-{_s}-{_a}", cap=110, cat=CT)(opsgen(_s, _a))


# ---- notation polonaise inversée
def rnd_arith(rng, nleaf):
    if nleaf == 1:
        return ("n", rng.randint(1, 9))
    k = rng.randint(1, nleaf - 1)
    return ("o", rng.choice("+-*"), rnd_arith(rng, k), rnd_arith(rng, nleaf - k))


def rpn_list(t):
    return [str(t[1])] if t[0] == "n" else rpn_list(t[2]) + rpn_list(t[3]) + [t[1]]


def infix_show(t, parent=None, right=False):
    if t[0] == "n":
        return str(t[1])
    prec = {"+": 1, "-": 1, "*": 2}
    s = f"{infix_show(t[2], t[1], False)} {t[1]} {infix_show(t[3], t[1], True)}"
    if parent is not None and (prec[t[1]] < prec[parent] or (right and prec[t[1]] == prec[parent])):
        s = "(" + s + ")"
    return s


def pyval(t):
    return t[1] if t[0] == "n" else eval(f"({pyval(t[2])}) {t[1]} ({pyval(t[3])})")


def rpn_eval(toks):
    st = []
    for x in toks:
        if x in "+-*" and len(x) == 1 and not x.isdigit():
            b, a = st.pop(), st.pop()
            st.append(a + b if x == "+" else a - b if x == "-" else a * b)
        else:
            st.append(int(x))
    return st[0]


def shunting(src):
    prec = {"+": 1, "-": 1, "*": 2}
    out, st = [], []
    for tok in src.split():
        if tok.lstrip("(").rstrip(")").isdigit() or tok.isdigit():
            lp = len(tok) - len(tok.lstrip("("))
            core = tok.strip("()")
            st += ["("] * lp
            out.append(core)
            for _ in range(len(tok) - len(tok.rstrip(")"))):
                while st[-1] != "(":
                    out.append(st.pop())
                st.pop()
        else:
            while st and st[-1] != "(" and prec[st[-1]] >= prec[tok]:
                out.append(st.pop())
            st.append(tok)
    while st:
        out.append(st.pop())
    return out


@gen(C3, "inf3-rpn-eval", cap=130, cat=CT)
def rpn_evaluation(rng, d):
    nl = rng.randint(3, 4 + d // 2)
    t = rnd_arith(rng, nl)
    toks = rpn_list(t)
    v = rpn_eval(toks)
    assert v == pyval(t)
    if abs(v) > 200:
        return None
    sym = lambda x: "×" if x == "*" else ("−" if x == "-" else x)
    # évaluation fautive : de gauche à droite sans pile (opérandes inversées)
    def wrong_ev(rev):
        st = []
        for x in toks:
            if x in "+-*" and not x.isdigit():
                b, a = st.pop(), st.pop()
                if rev:
                    a, b = b, a
                st.append(a + b if x == "+" else a - b if x == "-" else a * b)
            else:
                st.append(int(x))
        return st[0]
    wr = [wrong_ev(True), v + 1, v - 1, v + 2, v * 2, sum(int(x) for x in toks if x.isdigit())]
    return Draft(f"Quelle est la valeur de l'expression en notation polonaise inversée « {' '.join(sym(x) for x in toks)} » ?", str(v).replace("-", "−"),
                 [str(x).replace("-", "−") for x in wr if x != v], f"On empile les nombres et chaque opérateur dépile les deux derniers : résultat {v}.".replace("-", "−"), src=S3)


def rpn_depth(toks):
    st = mx = 0
    for x in toks:
        st = st + 1 if x.isdigit() else st - 1
        mx = max(mx, st)
    return mx


def tree_depth(t):
    return 1 if t[0] == "n" else max(tree_depth(t[2]), tree_depth(t[3]) + 1)


def sym(x):
    return "×" if x == "*" else ("−" if x == "-" else x)


@gen(C3, "inf3-rpn-depth", cap=100, cat=CT)
def rpn_stack_depth(rng, d):
    t = rnd_arith(rng, rng.randint(3, 5 + d // 2))
    toks = rpn_list(t)
    h = rpn_depth(toks)
    assert h == tree_depth(t) and rpn_eval(toks) == pyval(t)
    return Draft(f"On évalue « {' '.join(sym(x) for x in toks)} » en notation polonaise inversée avec une pile. Quel nombre maximal de valeurs la pile contient-elle en même temps ?", str(h),
                 [str(x) for x in (h - 1, h + 1, h + 2, len([x for x in toks if x.isdigit()]), len(toks) - h) if x != h and x > 0],
                 f"On suit la pile après chaque symbole ; son maximum est {h}.", src=S3)


@gen(C3, "inf3-rpn-prec", cap=100, cat=CT)
def rpn_precedence(rng, d):
    n = rng.randint(3, 5)
    nums = [rng.randint(1, 9) for _ in range(n)]
    ops_ = [rng.choice("+-*") for _ in range(n - 1)]
    if "*" not in ops_ or not ({"+", "-"} & set(ops_)):
        return None
    flat = " ".join(str(nums[0]) if k == 0 else f"{ops_[k - 1]} {nums[k]}" for k in range(n))
    v = eval(flat)
    toks = shunting(flat)
    assert rpn_eval(toks) == v
    left = nums[0]
    for o, x in zip(ops_, nums[1:]):
        left = left + x if o == "+" else left - x if o == "-" else left * x
    right_ = nums[-1]
    for o, x in zip(reversed(ops_), reversed(nums[:-1])):
        right_ = x + right_ if o == "+" else x - right_ if o == "-" else x * right_
    wr = [left, right_, v + 1, v - 1, v + 2, sum(nums)]
    fm = lambda x: str(x).replace("-", "−")
    return Draft(f"Quelle est la valeur de l'expression {flat.replace('*', '×').replace('-', '−')} (la multiplication est prioritaire sur l'addition et la soustraction) ?", fm(v),
                 [fm(x) for x in wr if x != v], f"On effectue d'abord les multiplications ; en notation postfixée : {' '.join(sym(x) for x in toks)} ; résultat {fm(v)}.", src=S3)
