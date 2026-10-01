"""L2 Économie et gestion financière : exercices calculés (micro, macro, statistiques appliquées, mathématiques financières,
finance d'entreprise, commerce international). Toutes les données (prix, taux, montants, taux de change) viennent de l'énoncé :
aucun taux ni seuil légal n'est supposé connu. Chaque réponse est recalculée par une seconde méthode (balayage, simulation,
substitution dans les équations, bibliothèque `statistics`) : une assertion qui échoue est un bogue, jamais une question.
Les arrondis sont toujours précisés dans l'énoncé, et les cas d'arrondi limite (…5 exact) sont écartés."""
import math
import statistics
from fractions import Fraction as F

from ..core import NB, Draft, fr, gen

C = "l2-eco"
SRC = "Exercice d'économie et de gestion financière (L2), réponse calculée à partir des données de l'énoncé"
MIC, MAC, FIN, STA, INT = "Microéconomie", "Macroéconomie", "Mathématiques financières", "Statistiques appliquées", "Commerce international"
GES = "Finance d'entreprise"


# ------------------------------------------------------------------------------------------------ outils
def ff(x, dec=2):
    return fr(float(x), dec)


def pc(x, dec=1):
    return fr(float(x), dec) + NB + "%"


def rd(x, dec=2):
    """Arrondi (demi supérieur) d'une fraction ; None si la valeur est trop proche d'un cas limite."""
    x = F(x)
    s = x * 10 ** dec
    fl = s.numerator // s.denominator
    if abs((s - fl) - F(1, 2)) < F(1, 1000):
        return None
    return F(int(fl + (1 if s - fl > F(1, 2) else 0)), 10 ** dec)


def nw(right, extras, fmt):
    """Mauvaises réponses : erreurs typiques d'abord, puis variations de repli ; jamais égales à la bonne."""
    r = F(right)
    rs = fmt(r)
    out = []
    pad = [r * k for k in (F(11, 10), F(9, 10), 2, F(1, 2), F(6, 5), F(4, 5))] + [r + k for k in (1, -1, 2, -2)]
    for x in list(extras) + pad:
        try:
            s = fmt(F(x))
        except (ValueError, ZeroDivisionError, OverflowError):
            continue
        if s != rs and s not in out:
            out.append(s)
    return out


def D(text, right, wrongs, expl):
    return Draft(text, right, wrongs, expl, src=SRC)


def money(x):
    return fr(int(x)) + NB + "FCFA"


def cf(n):
    """Coefficient écrit devant une variable : 1 est omis."""
    return "" if n == 1 else n


def sqrt_int(n):
    r = math.isqrt(n)
    return r if r * r == n else None


# ================================================================================================ MICROÉCONOMIE
@gen(C, "l2e-taxe-incidence", cap=60, cat=MIC, source=SRC)
def taxe_incidence(rng, d):
    b, dd = rng.randint(1, 5), rng.randint(1, 5)
    p0, q0 = rng.randint(6, 40), rng.randint(40, 200)
    a, c = q0 + b * p0, q0 - dd * p0
    if c < 0:
        return None
    t = rng.randint(1, max(1, p0 // 3))
    pc_ = F(a - c + dd * t, b + dd)        # prix payé par les acheteurs
    pv = pc_ - t
    q1 = a - b * pc_
    assert q1 == c + dd * pv                # substitution dans les deux équations
    assert a - b * pc_ == c + dd * (pc_ - t)
    ask = rng.choice(["pc", "pv", "q", "rec", "part", "dwl"])
    ent = f"Sur un marché, la demande est Qd = {a} − {cf(b)}P et l'offre Qs = {c} + {cf(dd)}P (P en FCFA). Une taxe de {t} FCFA par unité est versée par les vendeurs. "
    q0f = F(q0)
    if ask == "pc":
        v = rd(pc_, 2)
        if v is None:
            return None
        return D(ent + "Quel est le prix payé par les acheteurs à l'équilibre (arrondi à 0,01) ?", ff(v), nw(v, [p0, p0 + t, pv, F(p0) + F(t, 2), p0 - t], ff),
                 f"Qd(Pc) = Qs(Pc − t) donne Pc = ({a} − {c} + {dd}×{t}) ÷ ({b} + {dd}).")
    if ask == "pv":
        v = rd(pv, 2)
        if v is None:
            return None
        return D(ent + "Quel est le prix net reçu par les vendeurs à l'équilibre (arrondi à 0,01) ?", ff(v), nw(v, [p0, pc_, p0 - t, F(p0) - F(t, 2), pc_ + t], ff),
                 "Le prix reçu par les vendeurs est le prix payé par les acheteurs diminué de la taxe unitaire.")
    if ask == "q":
        v = rd(q1, 2)
        if v is None:
            return None
        return D(ent + "Quelle est la quantité échangée à l'équilibre avec la taxe (arrondie à 0,01) ?", ff(v), nw(v, [q0, q0 - b * t, q0 - dd * t, q0 - t, q0 + t], ff),
                 "On reporte le prix payé par les acheteurs dans la demande : Q = a − b·Pc.")
    if ask == "rec":
        v = rd(t * q1, 2)
        if v is None:
            return None
        return D(ent + "Quelle est la recette fiscale (arrondie à 0,01 FCFA) ?", ff(v), nw(v, [t * q0, t * (q0 - b * t), t * (q0 + b * t), t * pc_], ff),
                 "Recette = taxe unitaire × quantité échangée après taxe.")
    if ask == "part":
        share = F(dd, b + dd)
        assert (pc_ - p0) / t == share          # incidence : variation du prix payé / taxe
        v = rd(share * 100, 1)
        if v is None:
            return None
        return D(ent + "Quelle part de la taxe supportent les acheteurs (hausse du prix payé ÷ taxe, en %, arrondie à 0,1) ?", pc(v), nw(v, [100 - share * 100, F(b, b + dd) * 100, 50, F(dd, b) * 100], pc),
                 "La part des acheteurs est d/(b + d) : la partie qui supporte le plus la taxe est la moins élastique.")
    dwl = F(t * t * b * dd, 2 * (b + dd))
    assert dwl == t * (q0f - q1) / 2        # triangle de perte sèche
    v = rd(dwl, 2)
    if v is None:
        return None
    return D(ent + "Quelle est la perte sèche de la taxe (aire du triangle, arrondie à 0,01) ?", ff(v), nw(v, [t * (q0f - q1), t * q0 / 2, t * t, t * q1 / 2], ff),
             "Perte sèche = ½ × taxe × baisse de la quantité échangée.")


@gen(C, "l2e-subvention", cap=60, cat=MIC, source=SRC)
def subvention(rng, d):
    b, dd = rng.randint(1, 5), rng.randint(1, 5)
    p0, q0 = rng.randint(8, 40), rng.randint(40, 200)
    a, c = q0 + b * p0, q0 - dd * p0
    if c < 0:
        return None
    s = rng.randint(1, max(1, p0 // 3))
    pa = F(a - c - dd * s, b + dd)                     # prix payé par les acheteurs
    pr = pa + s                                         # prix reçu par les vendeurs
    q1 = a - b * pa
    assert q1 == c + dd * pr
    ask = rng.choice(["pa", "q", "cout"])
    ent = f"La demande est Qd = {a} − {cf(b)}P et l'offre Qs = {c} + {cf(dd)}P (P en FCFA). L'État verse {s} FCFA par unité vendue aux producteurs. "
    if ask == "pa":
        v = rd(pa, 2)
        if v is None:
            return None
        return D(ent + "Quel est le prix payé par les acheteurs à l'équilibre (arrondi à 0,01) ?", ff(v), nw(v, [p0, p0 + s, pr, p0 - s, F(p0) - F(s, 2)], ff),
                 "Le prix reçu par les vendeurs vaut le prix payé + subvention ; on résout Qd(P) = Qs(P + s).")
    if ask == "q":
        v = rd(q1, 2)
        if v is None:
            return None
        return D(ent + "Quelle est la quantité échangée à l'équilibre (arrondie à 0,01) ?", ff(v), nw(v, [q0, q0 + b * s, q0 + dd * s, q0 - b * s, q0 + s], ff),
                 "La subvention baisse le prix payé et augmente la quantité échangée.")
    v = rd(s * q1, 2)
    if v is None:
        return None
    return D(ent + "Quel est le coût budgétaire de la subvention pour l'État (arrondi à 0,01 FCFA) ?", ff(v), nw(v, [s * q0, s * pa, s * (q0 - b * s), s * (q0 + b * s) + 100], ff),
             "Coût = subvention unitaire × quantité échangée après subvention.")


@gen(C, "l2e-surplus", cap=60, cat=MIC, source=SRC)
def surplus(rng, d):
    B, Dd, cc = rng.randint(1, 6), rng.randint(1, 6), rng.randint(1, 20)
    qs = rng.randint(2, 30)
    A = cc + (B + Dd) * qs
    ps = cc + Dd * qs
    assert A - B * qs == ps
    sc = F(B * qs * qs, 2)
    sp = F(Dd * qs * qs, 2)
    # contrôle par intégration (trapèzes exacts pour une droite)
    n = 60
    h = F(qs, n)
    inte = sum((((A - B * (i * h)) - ps) + ((A - B * ((i + 1) * h)) - ps)) / 2 * h for i in range(n))
    assert inte == sc
    inte2 = sum(((ps - (cc + Dd * (i * h))) + (ps - (cc + Dd * ((i + 1) * h)))) / 2 * h for i in range(n))
    assert inte2 == sp
    ask = rng.choice(["sc", "sp", "tot"])
    ent = f"Demande inverse P = {A} − {cf(B)}q et offre inverse P = {cc} + {cf(Dd)}q (P en FCFA, q en unités). "
    if ask == "sc":
        return D(ent + "Quel est le surplus du consommateur à l'équilibre ?", ff(sc, 1), nw(sc, [sp, B * qs * qs, F((A - ps) * (qs), 1), sc + sp], lambda x: ff(x, 1)),
                 f"Équilibre : q = {qs}, P = {ps} ; surplus = ½ × (P max − P*) × q = ½ × {A - ps} × {qs}.")
    if ask == "sp":
        return D(ent + "Quel est le surplus du producteur à l'équilibre ?", ff(sp, 1), nw(sp, [sc, Dd * qs * qs, ps * qs - cc * qs, sc + sp], lambda x: ff(x, 1)),
                 f"Équilibre : q = {qs}, P = {ps} ; surplus = ½ × (P* − {cc}) × q = ½ × {ps - cc} × {qs}.")
    return D(ent + "Quel est le surplus total (consommateur + producteur) à l'équilibre ?", ff(sc + sp, 1), nw(sc + sp, [sc, sp, (A - cc) * qs, F(A * qs, 2)], lambda x: ff(x, 1)),
             f"Surplus total = ½ × ({A} − {cc}) × {qs} (triangle entre les deux courbes jusqu'à q = {qs}).")


@gen(C, "l2e-prix-plafond", cap=60, cat=MIC, source=SRC)
def prix_plafond(rng, d):
    b, dd = rng.randint(1, 5), rng.randint(1, 5)
    ps, qs = rng.randint(10, 40), rng.randint(40, 200)
    a, c = qs + b * ps, qs - dd * ps
    if c < 0:
        return None
    pm = ps - rng.randint(1, max(1, ps // 3))
    qd, qo = a - b * pm, c + dd * pm
    pen = qd - qo
    assert pen == (b + dd) * (ps - pm) and qo < qs < qd
    ask = rng.choice(["pen", "q"])
    ent = f"La demande est Qd = {a} − {cf(b)}P et l'offre Qs = {c} + {cf(dd)}P (P en FCFA). L'État impose un prix maximal de {pm} FCFA. "
    if ask == "pen":
        return D(ent + "Quelle est la pénurie (demande excédentaire) ?", fr(pen), [fr(x) for x in (qs - qo, qd - qs, qd, qo, pen * 2, pen + 10) if x != pen],
                 f"À P = {pm} : Qd = {qd}, Qs = {qo} ; pénurie = {qd} − {qo}.")
    return D(ent + "Quelle quantité est effectivement échangée ?", fr(qo), [fr(x) for x in (qd, qs, qd - qo, (qd + qo) // 2, qo + 10) if x != qo],
             "Quand le prix plafonné est inférieur à l'équilibre, la quantité échangée est la plus petite des deux : l'offre.")


@gen(C, "l2e-profit-concurrence", cap=60, cat=MIC, source=SRC)
def profit_concurrence(rng, d):
    a, b = rng.randint(1, 4), rng.randint(1, 30)
    qs = rng.randint(2, 20)
    f_ = rng.randrange(20, 400, 10)
    p = b + 2 * a * qs
    ct = lambda q: a * q * q + b * q + f_
    # contrôle par balayage
    prof = {q: p * q - ct(q) for q in range(0, 4 * qs + 6)}
    best = max(prof, key=prof.get)
    assert best == qs and sorted(prof.values())[-2] < prof[qs]
    ask = rng.choice(["q", "pi", "rt"])
    ent = f"Une entreprise en concurrence parfaite vend au prix de {p} FCFA ; son coût total est CT(q) = {a}q² + {b}q + {f_}. "
    if ask == "q":
        return D(ent + "Quelle quantité maximise son profit ?", fr(qs), [fr(x) for x in (qs + 1, qs - 1, p // (2 * a) if p % (2 * a) else qs + 2, (p - b) // a if (p - b) % a == 0 else qs + 3, qs * 2) if x != qs and x > 0],
                 f"Profit maximal quand prix = coût marginal : {p} = {2 * a}q + {b}, donc q = {qs}.")
    if ask == "pi":
        return D(ent + "Quel est le profit maximal ?", fr(prof[qs]), [fr(x) for x in (p * qs, p * qs - ct(qs) + f_, prof[qs + 1] if prof[qs + 1] != prof[qs] else prof[qs] + 10, prof[max(0, qs - 2)], prof[qs] + f_ if f_ else prof[qs] + 7, -f_) if x != prof[qs]],
                 f"q* = {qs} ; profit = recette {fr(p * qs)} − coût {fr(ct(qs))} = {fr(prof[qs])}.")
    return D(ent + "Quelle est la recette totale au niveau de production qui maximise le profit ?", fr(p * qs), [fr(x) for x in (ct(qs), prof[qs], p * (qs + 1), p * (qs - 1), p * qs + f_) if x != p * qs],
             f"Recette = prix × quantité optimale = {p} × {qs}.")


@gen(C, "l2e-seuil-fermeture", cap=50, cat=MIC, source=SRC)
def seuil_fermeture(rng, d):
    u = rng.randint(2, 12)
    n = u * u + rng.randint(1, 30)
    f_ = rng.randrange(50, 600, 10)
    cvm = lambda q: q * q - 2 * u * q + n
    best = min(range(1, 4 * u + 3), key=cvm)
    assert best == u
    mn = n - u * u
    assert cvm(u) == mn
    ask = rng.choice(["p", "q"])
    ent = f"Le coût total d'une entreprise concurrentielle est CT(q) = q³ − {2 * u}q² + {n}q + {f_}. "
    if ask == "p":
        return D(ent + "En dessous de quel prix cesse-t-elle de produire à court terme (minimum du coût variable moyen) ?", fr(mn), [fr(x) for x in (n, n - 2 * u, mn + u, n + f_ // 10, n - u) if x != mn and x > 0],
                 f"CVM = q² − {2 * u}q + {n}, minimal pour q = {u} : valeur {n} − {u * u} = {mn}.")
    return D(ent + "Pour quelle quantité le coût variable moyen est-il minimal ?", fr(u), [fr(x) for x in (u + 1, u - 1, 2 * u, mn, u * u) if x != u and x > 0],
             "On annule la dérivée du CVM = q² − 2uq + n : 2q − 2u = 0, donc q = u.")


@gen(C, "l2e-monopole", cap=60, cat=MIC, source=SRC)
def monopole(rng, d):
    B, c = rng.randint(1, 6), rng.randint(2, 30)
    qs = rng.randint(2, 25)
    A = c + 2 * B * qs
    f_ = rng.randrange(0, 300, 10)
    p = A - B * qs
    assert p == (A + c) / 2
    pi = lambda q: (A - B * q) * q - c * q - f_
    best = max(range(0, 4 * qs + 5), key=pi)
    assert best == qs                           # contrôle par balayage
    profit = pi(qs)
    ask = rng.choice(["q", "p", "pi", "lerner"])
    ent = f"Un monopole fait face à la demande inverse P = {A} − {cf(B)}q ; son coût total est CT(q) = {c}q" + (f" + {f_}" if f_ else "") + ". "
    if ask == "q":
        return D(ent + "Quelle quantité maximise son profit ?", fr(qs), [fr(x) for x in (2 * qs, qs + 1, qs - 1, (A - c) // B if (A - c) % B == 0 else qs + 3, qs // 2 + 1) if x != qs and x > 0],
                 f"Recette marginale = coût marginal : {A} − {2 * B}q = {c}, donc q = {qs}.")
    if ask == "p":
        return D(ent + "Quel prix pratique-t-il ?", fr(p), [fr(x) for x in (c, A, A - B * 2 * qs, c + B * qs, p + B) if x != p and x > 0],
                 f"q* = {qs} ; le prix se lit sur la demande : {A} − {B}×{qs} = {p}.")
    if ask == "pi":
        return D(ent + "Quel est son profit maximal ?", fr(profit), [fr(x) for x in (p * qs, (p - c) * qs + f_, pi(qs + 1), (A - c) * qs - f_, profit + f_ if f_ else profit + 20) if x != profit],
                 f"Profit = ({p} − {c}) × {qs}" + (f" − {f_}" if f_ else "") + f" = {fr(profit)}.")
    lern = F(p - c, p)
    v = rd(lern * 100, 1)
    if v is None:
        return None
    return D(ent + "Quel est l'indice de Lerner (P − Cm) ÷ P, en % arrondi à 0,1 ?", pc(v), nw(v, [F(c, p) * 100, F(p - c, c) * 100, F(p - c, A) * 100, 100 - lern * 100], pc),
             f"Cm = {c}, P = {p} : ({p} − {c}) ÷ {p}.")


@gen(C, "l2e-couts-moyens", cap=60, cat=MIC, source=SRC)
def couts_moyens(rng, d):
    a, b = rng.randint(1, 5), rng.randint(0, 40)
    u = rng.randint(2, 15)
    f_ = a * u * u
    cm = lambda q: F(a * q * q + b * q + f_, q)
    best = min(range(1, 4 * u + 2), key=cm)
    assert best == u
    cmin = 2 * a * u + b
    assert cm(u) == cmin and 2 * a * u + b == cmin   # Cm(u) = CM(u) au minimum
    ask = rng.choice(["q", "cm"])
    ent = f"Le coût total est CT(q) = {a}q² + {b}q + {f_}. " if b else f"Le coût total est CT(q) = {a}q² + {f_}. "
    if ask == "q":
        return D(ent + "Pour quelle quantité le coût moyen est-il minimal ?", fr(u), [fr(x) for x in (u + 1, u - 1, 2 * u, f_, a * u) if x != u and x > 0],
                 f"Le coût moyen est minimal quand il égale le coût marginal : {a}q + {b} + {f_}/q = {2 * a}q + {b}, soit q² = {f_}/{a} = {u * u}.")
    return D(ent + "Quel est le coût moyen minimal ?", fr(cmin), [fr(x) for x in (cmin + a, cmin - a if cmin > a else cmin + 2, a * u + b, f_ // u if f_ % u == 0 else cmin + 3, 2 * cmin) if x != cmin and x > 0],
             f"Au minimum, CM = Cm = {2 * a}×{u} + {b} = {cmin}.")


@gen(C, "l2e-cout-marginal-tableau", cap=60, cat=MIC, source=SRC)
def cout_marginal_tableau(rng, d):
    a, b, f_ = rng.randint(1, 5), rng.randint(2, 30), rng.randrange(20, 200, 10)
    ct = [f_ + b * q + a * q * q for q in range(0, 6)]
    k = rng.randint(2, 5)
    ask = rng.choice(["cm", "cmoy", "cvm"])
    tab = " ; ".join(f"q = {q} : {ct[q]}" for q in range(0, 6))
    ent = f"Coût total en FCFA selon la quantité produite : {tab}. "
    if ask == "cm":
        cm = ct[k] - ct[k - 1]
        assert cm == b + a * (2 * k - 1)
        return D(ent + f"Quel est le coût marginal de la {k}{'e' if k > 1 else 're'} unité ?", fr(cm), [fr(x) for x in (ct[k] // k, ct[k], ct[k + 1] - ct[k] if k < 5 else ct[k] - ct[k - 2], (ct[k] - ct[0]) // k + 1, ct[k] - ct[0]) if x != cm and x > 0],
                 f"Coût marginal = CT({k}) − CT({k - 1}) = {ct[k]} − {ct[k - 1]} = {cm}.")
    if ask == "cmoy":
        v = rd(F(ct[k], k), 2)
        if v is None:
            return None
        return D(ent + f"Quel est le coût moyen (arrondi à 0,01) pour q = {k} ?", ff(v), nw(v, [ct[k] - ct[k - 1], F(ct[k] - f_, k), ct[k], F(ct[k - 1], k - 1)], ff),
                 f"Coût moyen = CT({k}) ÷ {k}.")
    v = rd(F(ct[k] - ct[0], k), 2)
    if v is None:
        return None
    return D(ent + f"Quel est le coût variable moyen (arrondi à 0,01) pour q = {k} ?", ff(v), nw(v, [F(ct[k], k), ct[k] - ct[k - 1], ct[k] - ct[0], F(ct[0], k)], ff),
             f"Coût variable = CT({k}) − CT(0) = {ct[k] - ct[0]} (le coût fixe est CT(0)) ; on divise par {k}.")


@gen(C, "l2e-cournot", cap=60, cat=MIC, source=SRC)
def cournot(rng, d):
    B = rng.randint(1, 4)
    q1, q2 = rng.randint(2, 20), rng.randint(2, 20)
    sym = d <= 2
    if sym:
        q2 = q1
    A = B * (2 * q1 + q2) + rng.randint(5, 30)
    c1 = A - B * (2 * q1 + q2)
    c2 = A - B * (q1 + 2 * q2)
    if c1 <= 0 or c2 <= 0 or (sym and c1 != c2):
        return None
    # contrôle : convergence des meilleures réponses
    x1 = x2 = F(0)
    for _ in range(300):
        x1 = (A - c1 - B * x2) / (2 * B)
        x2 = (A - c2 - B * x1) / (2 * B)
    assert abs(float(x1) - q1) < 1e-6 and abs(float(x2) - q2) < 1e-6
    # contrôle exact : les deux conditions du premier ordre sont satisfaites
    assert A - 2 * B * q1 - B * q2 == c1 and A - B * q1 - 2 * B * q2 == c2
    P = A - B * (q1 + q2)
    ask = rng.choice(["q1", "Q", "P", "pi1"])
    cost = f"coût marginal constant de {c1} pour chacune" if sym else f"coûts marginaux constants : {c1} pour l'entreprise 1 et {c2} pour l'entreprise 2"
    ent = f"Deux entreprises se font concurrence en quantités (Cournot) ; la demande inverse est P = {A} − {cf(B)}(q1 + q2) ; {cost}, sans coût fixe. "
    if ask == "q1":
        return D(ent + "Quelle est la quantité d'équilibre de l'entreprise 1 ?", fr(q1), [fr(x) for x in (q2, q1 + 1, q1 - 1, (A - c1) // (2 * B) if (A - c1) % (2 * B) == 0 else q1 + 2, (A - c1) // B if (A - c1) % B == 0 else q1 + 3) if x != q1 and x > 0],
                 f"Meilleures réponses q1 = ({A} − {c1} − {B}q2) ÷ {2 * B} et q2 = ({A} − {c2} − {B}q1) ÷ {2 * B} : l'intersection donne q1 = {q1}.")
    if ask == "Q":
        return D(ent + "Quelle est la production totale à l'équilibre ?", fr(q1 + q2), [fr(x) for x in (q1, q2 + 2 * q1, (A - c1) // B if (A - c1) % B == 0 else q1 + q2 + 4, (q1 + q2) * 2 // 3 + 1, q1 + q2 + 1) if x != q1 + q2 and x > 0],
                 f"Q = q1 + q2 = {q1} + {q2}.")
    if ask == "P":
        return D(ent + "Quel est le prix d'équilibre ?", fr(P), [fr(x) for x in (c1, A - B * q1, A, (A + c1) // 2 if (A + c1) % 2 == 0 else P + 3, P + B) if x != P and x > 0],
                 f"P = {A} − {B}×({q1} + {q2}) = {P}.")
    pi1 = (P - c1) * q1
    return D(ent + "Quel est le profit de l'entreprise 1 à l'équilibre ?", fr(pi1), [fr(x) for x in (P * q1, (P - c2) * q2, (P - c1) * (q1 + q2), pi1 + B * q1, (P - c1) * q1 + 10) if x != pi1 and x > 0],
             f"Profit = (P − c1) × q1 = ({P} − {c1}) × {q1}.")


@gen(C, "l2e-cout-minimal", cap=50, cat=MIC, source=SRC)
def cout_minimal(rng, d):
    a, b = rng.randint(1, 5), rng.randint(1, 5)
    if a == b:
        return None
    w, r = a * a, b * b
    Q = a * b * rng.randint(1, 6)
    L, K = Q * b // a, Q * a // b           # fonction Q = racine(K·L) : L* = Q·sqrt(r/w), K* = Q·sqrt(w/r)
    assert L * K == Q * Q
    cost = w * L + r * K
    # contrôle par balayage sur L (K = Q²/L)
    best = min(range(1, 6 * Q * Q + 1), key=lambda l: w * l + r * F(Q * Q, l))
    assert best == L and cost == 2 * Q * a * b
    ask = rng.choice(["c", "L", "K"])
    ent = f"Une entreprise produit Q = √(K·L) ; le travail L coûte {w} FCFA l'unité et le capital K coûte {r} FCFA l'unité. Elle veut produire {Q} unités au moindre coût. "
    if ask == "c":
        return D(ent + "Quel est le coût minimal ?", fr(cost), [fr(x) for x in (Q * (w + r), Q * w, Q * r, w * Q + r * Q * Q // max(1, a * b), cost + 2 * Q) if x != cost and x > 0],
                 f"Au minimum, L* = {L} et K* = {K} : coût = {w}×{L} + {r}×{K} = {fr(cost)}.")
    if ask == "L":
        return D(ent + "Quelle quantité de travail utilise-t-elle ?", fr(L), [fr(x) for x in (K, Q, L + 1, L * 2, Q * a // b + 1) if x != L and x > 0],
                 "Le minimum de coût est atteint quand le rapport des productivités marginales égale celui des prix : r·K = w·L.")
    return D(ent + "Quelle quantité de capital utilise-t-elle ?", fr(K), [fr(x) for x in (L, Q, K + 1, K * 2, Q * b // a + 1) if x != K and x > 0],
             "Le minimum de coût est atteint quand w·L = r·K ; avec Q² = K·L on en déduit K.")


@gen(C, "l2e-consommateur", cap=60, cat=MIC, source=SRC)
def consommateur(rng, d):
    al = rng.choice([F(1, 2), F(1, 3), F(2, 3), F(1, 4), F(3, 4), F(1, 5), F(2, 5), F(3, 5)])
    px, py = rng.choice([2, 4, 5, 10, 20, 25]), rng.choice([2, 4, 5, 10, 20, 25])
    rev = rng.randrange(1000, 20001, 500)
    x = al * rev / px
    y = (1 - al) * rev / py
    if x.denominator != 1 or y.denominator != 1:
        return None
    x, y = int(x), int(y)
    assert px * x + py * y == rev
    # contrôle par balayage de la droite de budget
    best = max(range(1, rev // px), key=lambda xx: float(al) * math.log(xx) + float(1 - al) * math.log((rev - px * xx) / py) if rev - px * xx > 0 else -1e9)
    assert best == x
    ask = rng.choice(["x", "y", "dep"])
    ent = (f"Un consommateur a un revenu de {fr(rev)} FCFA ; son utilité est U = x^{ff(al, 2)}·y^{ff(1 - al, 2)} ; le bien x coûte {px} FCFA l'unité et le bien y {py} FCFA. ")
    if ask == "x":
        return D(ent + "Quelle quantité de x choisit-il ?", fr(x), [fr(i) for i in (rev // px, y, x + 1, (rev // px) // 2, x * 2) if i != x and i > 0],
                 f"Avec une Cobb-Douglas, il consacre la part {al} de son revenu à x : {fr(int(al * rev))} ÷ {px} = {fr(x)}.")
    if ask == "y":
        return D(ent + "Quelle quantité de y choisit-il ?", fr(y), [fr(i) for i in (rev // py, x, y + 1, (rev // py) // 2, y * 2) if i != y and i > 0],
                 f"Il consacre la part {1 - al} de son revenu à y : {fr(int((1 - al) * rev))} ÷ {py} = {fr(y)}.")
    dep = int(al * rev)
    return D(ent + "Quelle somme dépense-t-il pour le bien x ?", money(dep), [money(i) for i in (rev // 2, int((1 - al) * rev), x, rev - dep + 500) if i != dep and i > 0],
             f"La dépense en x vaut la part {al} du revenu, soit {fr(dep)} FCFA.")


@gen(C, "l2e-elast-croisee", cap=60, cat=MIC, source=SRC)
def elast_croisee(rng, d):
    a, b = rng.randrange(100, 400, 10), rng.randint(1, 6)
    c = rng.choice([-5, -4, -3, -2, -1, 1, 2, 3, 4, 5])
    pa, pb = rng.randint(5, 30), rng.randint(5, 30)
    q = a - b * pa + c * pb
    if q <= 10:
        return None
    e = F(c * pb, q)
    # contrôle par variation finie : un peu plus de Pb
    q2 = a - b * pa + c * (pb + 1)
    assert F(q2 - q, q) / F(1, pb) == e
    v = rd(e, 2)
    if v is None:
        return None
    ent = f"La demande du bien A est QA = {a} − {cf(b)}PA {'+' if c > 0 else '−'} {cf(abs(c))}PB. Au point PA = {pa} et PB = {pb}, "
    return D(ent + "quelle est l'élasticité croisée de la demande de A par rapport au prix de B (arrondie à 0,01) ?", ff(v),
             nw(v, [-e, F(-b * pa, q), F(c, 1), F(c * pa, q), F(pb, q)], ff),
             f"QA = {q} ; élasticité croisée = (∂QA/∂PB) × PB/QA = {c} × {pb} ÷ {q}.")


@gen(C, "l2e-elast-nature", cap=60, cat=MIC, source=SRC)
def elast_nature(rng, d):
    if rng.random() < 0.5:
        x = rng.choice([2, 3, 4, 5, 6, 8, 10, 12])
        y = rng.choice([-8, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15])
        e = F(y, x)
        ent = f"Le prix du bien B augmente de {x} % ; la quantité demandée du bien A varie de {'+' if y > 0 else '−' if y < 0 else ''}{abs(y)} %. "
        right = "Substituts" if e > 0 else "Compléments" if e < 0 else "Biens indépendants"
        wr = [w for w in ("Substituts", "Compléments", "Biens indépendants", "Biens de Giffen") if w != right]
        return D(ent + f"Que peut-on dire de A et B (élasticité croisée = {ff(e, 2)}) ?", right, wr,
                 "Élasticité croisée positive : substituts ; négative : compléments ; nulle : biens indépendants.")
    x = rng.choice([2, 4, 5, 10, 20])
    y = rng.choice([-6, -4, -2, 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40])
    e = F(y, x)
    if e in (0, 1):
        return None
    ent = f"Quand le revenu augmente de {x} %, la quantité demandée d'un bien varie de {'+' if y > 0 else '−'}{abs(y)} %. "
    right = "Bien inférieur" if e < 0 else ("Bien de première nécessité (normal)" if e < 1 else "Bien de luxe")
    allw = ["Bien inférieur", "Bien de première nécessité (normal)", "Bien de luxe", "Bien à demande rigide aux prix"]
    return D(ent + f"Quelle est la nature de ce bien (élasticité-revenu = {ff(e, 2)}) ?", right, [w for w in allw if w != right],
             "Élasticité-revenu < 0 : bien inférieur ; entre 0 et 1 : bien normal de première nécessité ; > 1 : bien de luxe.")


@gen(C, "l2e-elast-revenu", cap=60, cat=MIC, source=SRC)
def elast_revenu(rng, d):
    a, b = rng.randrange(10, 200, 10), rng.randint(1, 8)
    r = rng.randrange(20, 200, 10)
    q = a + b * r
    e = F(b * r, q)
    # contrôle par variation de 1 unité de revenu
    assert F((a + b * (r + 1)) - q, q) * r == e
    v = rd(e, 2)
    if v is None:
        return None
    return D(f"La demande d'un bien est Q = {a} + {cf(b)}R, où R est le revenu (en milliers de FCFA). Pour R = {r}, quelle est l'élasticité-revenu (arrondie à 0,01) ?", ff(v),
             nw(v, [b, F(b * q, r), F(r, q), F(q, b * r), e + 1], ff), f"Q = {q} ; η = (dQ/dR) × R/Q = {b} × {r} ÷ {q}.")


@gen(C, "l2e-elast-milieu", cap=60, cat=MIC, source=SRC)
def elast_milieu(rng, d):
    p0, p1 = rng.randrange(50, 500, 10), rng.randrange(50, 500, 10)
    q0, q1 = rng.randrange(50, 600, 10), rng.randrange(50, 600, 10)
    if p0 == p1 or q0 == q1 or (p1 - p0) * (q1 - q0) > 0 and rng.random() < 0.5:
        return None
    e = F(q1 - q0, F(q0 + q1, 2)) / F(p1 - p0, F(p0 + p1, 2))
    # 2e méthode : (ΔQ/ΔP) × (P moyen / Q moyen)
    assert e == F(q1 - q0, p1 - p0) * F(p0 + p1, q0 + q1)
    v = rd(e, 2)
    if v is None:
        return None
    return D(f"Le prix passe de {p0} à {p1} FCFA et la quantité de {q0} à {q1} unités. Quelle est l'élasticité-prix selon la méthode du point milieu (arrondie à 0,01) ?", ff(v),
             nw(v, [F(q1 - q0, q0) / F(p1 - p0, p0), -e, F(q1 - q0, p1 - p0), F(q1 - q0, q1) / F(p1 - p0, p1)], ff),
             "Élasticité = (ΔQ ÷ Q moyen) ÷ (ΔP ÷ P moyen), avec les moyennes des deux points.")


# ================================================================================================ MACROÉCONOMIE
@gen(C, "l2e-indice-lp", cap=60, cat=STA, source=SRC)
def indice_lp(rng, d):
    n = 3
    p0 = [rng.randrange(100, 2001, 50) for _ in range(n)]
    p1 = [max(50, p + rng.randrange(-100, 401, 50)) for p in p0]
    q0 = [rng.randint(2, 20) for _ in range(n)]
    q1 = [max(1, q + rng.randint(-3, 5)) for q in q0]
    if p0 == p1 or q0 == q1:
        return None
    L = F(sum(a * b for a, b in zip(p1, q0)), sum(a * b for a, b in zip(p0, q0))) * 100
    P = F(sum(a * b for a, b in zip(p1, q1)), sum(a * b for a, b in zip(p0, q1))) * 100
    # 2e méthode : moyenne pondérée des rapports de prix
    w0 = [F(a * b, sum(x * y for x, y in zip(p0, q0))) for a, b in zip(p0, q0)]
    assert L == sum(w * F(a, b) for w, a, b in zip(w0, p1, p0)) * 100
    w1 = [F(a * b, sum(x * y for x, y in zip(p1, q1))) for a, b in zip(p1, q1)]
    assert P == 1 / sum(w * F(b, a) for w, a, b in zip(w1, p1, p0)) * 100
    ask = rng.choice(["L", "P"])
    f3 = lambda xs: ", ".join(fr(x) for x in xs)
    ent = f"Trois biens. Prix année 0 : {f3(p0)} FCFA ; année 1 : {f3(p1)}. Quantités année 0 : {f3(q0)} ; année 1 : {f3(q1)}. "
    v = rd(L if ask == "L" else P, 1)
    if v is None:
        return None
    if ask == "L":
        return D(ent + "Quel est l'indice des prix de Laspeyres (base 100 en année 0, arrondi à 0,1) ?", ff(v, 1), nw(v, [P, F(L + P, 2), F(sum(p1), sum(p0)) * 100, F(sum(a * b for a, b in zip(p0, q1)), sum(a * b for a, b in zip(p0, q0))) * 100], lambda x: ff(x, 1)),
                 "Laspeyres valorise les quantités de l'année de base aux prix des deux années : Σp1q0 ÷ Σp0q0 × 100.")
    return D(ent + "Quel est l'indice des prix de Paasche (base 100 en année 0, arrondi à 0,1) ?", ff(v, 1), nw(v, [L, F(L + P, 2), F(sum(p1), sum(p0)) * 100, F(sum(a * b for a, b in zip(p1, q0)), sum(a * b for a, b in zip(p0, q0))) * 100 + 1], lambda x: ff(x, 1)),
             "Paasche valorise les quantités de l'année courante aux prix des deux années : Σp1q1 ÷ Σp0q1 × 100.")


@gen(C, "l2e-indice-fisher", cap=60, cat=STA, source=SRC)
def indice_fisher(rng, d):
    n = 3
    p0 = [rng.randrange(100, 1501, 50) for _ in range(n)]
    p1 = [max(50, p + rng.randrange(-100, 401, 50)) for p in p0]
    q0 = [rng.randint(2, 20) for _ in range(n)]
    q1 = [max(1, q + rng.randint(-3, 5)) for q in q0]
    L = F(sum(a * b for a, b in zip(p1, q0)), sum(a * b for a, b in zip(p0, q0))) * 100
    P = F(sum(a * b for a, b in zip(p1, q1)), sum(a * b for a, b in zip(p0, q1))) * 100
    fish = math.sqrt(float(L) * float(P))
    assert abs(fish * fish - float(L * P)) < 1e-6
    assert min(L, P) <= F(fish) <= max(L, P)       # la moyenne géométrique est comprise entre les deux indices
    v = rd(F(fish), 1)
    if v is None:
        return None
    f3 = lambda xs: ", ".join(fr(x) for x in xs)
    return D(f"Trois biens. Prix année 0 : {f3(p0)} ; année 1 : {f3(p1)}. Quantités année 0 : {f3(q0)} ; année 1 : {f3(q1)}. Quel est l'indice de Fisher (moyenne géométrique de Laspeyres et Paasche, base 100, arrondi à 0,1) ?",
             ff(v, 1), nw(v, [F((float(L) + float(P)) / 2), L, P, F(float(L) * float(P)) / 100], lambda x: ff(x, 1)),
             f"Laspeyres = {ff(L, 2)}, Paasche = {ff(P, 2)} ; Fisher = √(L × P).")


@gen(C, "l2e-salaire-reel", cap=60, cat=MAC, source=SRC)
def salaire_reel(rng, d):
    w0 = rng.randrange(80000, 400001, 5000)
    gw = rng.choice([2, 4, 5, 6, 8, 10, 12, 15, 20])
    ip = rng.choice([100 + k for k in (2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25)])
    w1 = w0 * (100 + gw) // 100
    if w1 * 100 != w0 * (100 + gw):
        return None
    real = F(w1 * 100, ip)
    var = (F(100 + gw, ip) * 100) - 100
    # contrôle : variation du salaire réel = (1+gw)/(1+π) − 1
    assert real == w0 * F(100 + gw, ip)
    ask = rng.choice(["real", "var"])
    ent = f"Un salaire passe de {money(w0)} à {money(w1)} ; l'indice des prix passe de 100 à {ip}. "
    if ask == "real":
        v = rd(real, 0)
        if v is None:
            return None
        return D(ent + "Quel est le salaire réel (en FCFA de l'année de base, arrondi à l'unité) ?", money(v), [money(x) for x in (w1, w1 * ip // 100, w0, w1 - w0 * (ip - 100) // 100, w1 * (200 - ip) // 100) if x != int(v) and x > 0],
                 f"Salaire réel = salaire nominal ÷ indice × 100 = {fr(w1)} ÷ {ip} × 100.")
    v = rd(var, 2)
    if v is None:
        return None
    return D(ent + "De combien varie le pouvoir d'achat du salaire (en %, arrondi à 0,01) ?", pc(v, 2), nw(v, [gw - (ip - 100), -(ip - 100), gw, ip - 100 - gw, var * 2], lambda x: pc(x, 2)),
             f"Le pouvoir d'achat varie comme (1 + {gw} %) ÷ ({ip}/100) − 1.")


@gen(C, "l2e-tcam", cap=60, cat=MAC, source=SRC)
def tcam(rng, d):
    g = rng.choice([2, 3, 4, 5, 6, 8, 10, 12, 15, 20])
    n = rng.randint(2, 5)
    v0 = rng.randrange(100, 10001, 100)
    vn = F(v0 * (100 + g) ** n, 100 ** n)
    if vn.denominator != 1:
        return None
    vn = int(vn)
    # contrôle : racine n-ième numérique
    assert abs((vn / v0) ** (1 / n) - 1 - g / 100) < 1e-9
    simple = F(vn - v0, v0 * n) * 100
    tot = F(vn - v0, v0) * 100
    return D(f"Le PIB d'un pays passe de {fr(v0)} à {fr(vn)} milliards de FCFA en {n} ans. Quel est le taux de croissance annuel moyen (géométrique) ?", pc(g, 1),
             [x for x in (pc(rd(simple, 1) or simple, 1), pc(rd(tot, 1) or tot, 1), pc(g + 1, 1), pc(g - 1 if g > 1 else g + 2, 1)) if x != pc(g, 1)],
             f"(1 + g)^{n} = {fr(vn)} ÷ {fr(v0)}, donc g = {g} % ; la moyenne arithmétique des variations serait plus élevée.")


@gen(C, "l2e-croissance-projection", cap=60, cat=MAC, source=SRC)
def croissance_projection(rng, d):
    v0 = rng.randrange(1000, 20000, 500)
    g = rng.choice([F(2), F(3), F(4), F(5), F(6), F(5, 2), F(7, 2), F(9, 2), F(8), F(10)])
    n = rng.randint(2, 8)
    vn = v0 * (1 + g / 100) ** n
    x = F(v0)
    for _ in range(n):
        x = x * (100 + g) / 100
    assert x == vn
    v = rd(vn, 1)
    if v is None:
        return None
    return D(f"Un PIB de {fr(v0)} milliards de FCFA croît de {ff(g, 1)} % par an. Quel sera son niveau après {n} ans (arrondi à 0,1 milliard) ?", ff(v, 1),
             nw(v, [v0 * (1 + g * n / 100), v0 * (1 + g / 100) ** (n - 1), v0 * (1 + g / 100) ** (n + 1), v0 + v0 * g / 100], lambda x: ff(x, 1)),
             f"Valeur finale = {fr(v0)} × (1 + {ff(g / 100, 3)})^{n} (intérêts composés, et non une addition de {n} fois le même gain).")


@gen(C, "l2e-pib-reel", cap=60, cat=MAC, source=SRC)
def pib_reel(rng, d):
    dfl = rng.choice([105, 110, 112, 115, 120, 125, 130, 140, 150, 160, 175, 200])
    reel = rng.randrange(400, 6000, 20)
    nom = F(reel * dfl, 100)
    if nom.denominator != 1:
        return None
    nom = int(nom)
    ask = rng.choice(["reel", "dfl"])
    if ask == "reel":
        assert F(nom * 100, dfl) == reel
        return D(f"Le PIB nominal est de {fr(nom)} milliards de FCFA et le déflateur du PIB de {dfl} (base 100 l'année de référence). Quel est le PIB réel (en milliards de FCFA de l'année de référence) ?", fr(reel),
                 [fr(x) for x in (nom * dfl // 100, nom - dfl, nom * (200 - dfl) // 100, nom // 100 * 100 + 20, reel + 40) if x != reel and x > 0],
                 f"PIB réel = PIB nominal ÷ déflateur × 100 = {fr(nom)} ÷ {dfl} × 100.")
    assert F(nom, reel) * 100 == dfl
    return D(f"Le PIB nominal est de {fr(nom)} et le PIB réel de {fr(reel)} milliards de FCFA (prix de l'année de référence). Quel est le déflateur du PIB (base 100) ?", fr(dfl),
             [fr(x) for x in (nom - reel, F(reel, nom) * 100, dfl + 10, dfl - 10, 100 + (dfl - 100) // 2) if x != dfl and x > 0],
             f"Déflateur = PIB nominal ÷ PIB réel × 100 = {fr(nom)} ÷ {fr(reel)} × 100.")


@gen(C, "l2e-croissance-reelle", cap=60, cat=MAC, source=SRC)
def croissance_reelle(rng, d):
    gn = rng.randint(3, 20)
    pi = rng.randint(1, 12)
    if pi >= gn:
        return None
    gr = F(100 + gn, 100 + pi) * 100 - 100
    # contrôle : sur un PIB de 1 000 en base
    nom1 = F(1000 * (100 + gn), 100)
    defl = F(100 + pi)
    assert nom1 * 100 / defl == 1000 * (1 + gr / 100)
    v = rd(gr, 2)
    if v is None:
        return None
    return D(f"Le PIB nominal augmente de {gn} % et le déflateur du PIB de {pi} %. Quel est le taux de croissance du PIB réel (arrondi à 0,01 %) ?", pc(v, 2), nw(v, [gn - pi, F(gn, 100 + pi) * 100, gn + pi, F(gn + pi, 1) - 1], lambda x: pc(x, 2)),
             f"1 + g = (1 + {gn} %) ÷ (1 + {pi} %) ; la soustraction {gn} − {pi} n'est qu'une approximation.")


@gen(C, "l2e-taux-reel", cap=60, cat=MAC, source=SRC)
def taux_reel(rng, d):
    i = rng.randint(3, 20)
    pi = rng.randint(1, 12)
    r = F(100 + i, 100 + pi) * 100 - 100
    if i == pi:
        return None
    v = rd(r, 2)
    if v is None:
        return None
    return D(f"Un placement rapporte {i} % par an en valeur nominale et l'inflation est de {pi} %. Quel est le taux d'intérêt réel exact, 1 + r = (1 + i) ÷ (1 + π) (en %, arrondi à 0,01) ?", pc(v, 2),
             nw(v, [i - pi, i + pi, F(i * 100, 100 + pi), F(100 + i, pi)], lambda x: pc(x, 2)), f"r = ({100 + i} ÷ {100 + pi} − 1) × 100 ; la différence {i} − {pi} est seulement approchée.")


def mult_vals(rng):
    c = rng.choice([F(1, 2), F(3, 5), F(3, 4), F(4, 5), F(9, 10), F(2, 3)])
    t = rng.choice([F(1, 10), F(1, 5), F(1, 4), F(3, 10), F(2, 5), F(1, 2)])
    return c, t


@gen(C, "l2e-mult-fiscal", cap=60, cat=MAC, source=SRC)
def mult_fiscal(rng, d):
    c, t = mult_vals(rng)
    k = 1 / (1 - c * (1 - t))
    c0, i0, g = rng.randrange(50, 400, 10), rng.randrange(50, 400, 10), rng.randrange(50, 400, 10)
    y = k * (c0 + i0 + g)
    # contrôle : tours de dépense successifs
    tot, flux = F(0), F(c0 + i0 + g)
    for _ in range(400):
        tot += flux
        flux = flux * c * (1 - t)
    assert abs(float(tot - y)) < 1e-6
    assert y == c0 + c * (1 - t) * y + i0 + g
    ask = rng.choice(["k", "y", "dy"])
    ent = f"C = {c0} + {ff(c, 2)}·(Y − T), avec un impôt proportionnel T = {ff(t, 2)}·Y ; I = {i0} ; G = {g}. "
    if ask == "k":
        v = rd(k, 2)
        if v is None:
            return None
        return D(f"Dans une économie fermée, C = c0 + {ff(c, 2)}·(Y − T) avec un impôt proportionnel T = {ff(t, 2)}·Y. Quel est le multiplicateur des dépenses autonomes (arrondi à 0,01) ?", ff(v), nw(v, [1 / (1 - c), 1 / (1 - c * t), c / (1 - c * (1 - t)), 1 / c], ff),
                 f"k = 1 ÷ (1 − c(1 − t)) = 1 ÷ (1 − {ff(c, 2)} × {ff(1 - t, 2)}).")
    if ask == "y":
        v = rd(y, 1)
        if v is None:
            return None
        return D("Économie fermée. " + ent + "Quel est le revenu d'équilibre (arrondi à 0,1) ?", ff(v, 1), nw(v, [(c0 + i0 + g) / (1 - c), (c0 + i0 + g), c0 + i0 + g + c * y, y * (1 - t)], lambda x: ff(x, 1)),
                 f"Y = (c0 + I + G) ÷ (1 − c(1 − t)) = {c0 + i0 + g} × {ff(k, 3)}.")
    dg = rng.choice([10, 20, 40, 50, 100])
    v = rd(k * dg, 1)
    if v is None:
        return None
    return D(f"Dans une économie fermée, C = c0 + {ff(c, 2)}·(Y − T) avec un impôt proportionnel T = {ff(t, 2)}·Y. De combien le revenu d'équilibre varie-t-il si G augmente de {dg} (arrondi à 0,1) ?", ff(v, 1),
             nw(v, [dg / (1 - c), dg, dg * c, k], lambda x: ff(x, 1)), f"ΔY = k × ΔG = {ff(k, 3)} × {dg}.")


@gen(C, "l2e-mult-ouvert", cap=60, cat=MAC, source=SRC)
def mult_ouvert(rng, d):
    c, t = mult_vals(rng)
    m = rng.choice([F(1, 10), F(1, 5), F(3, 20), F(1, 4), F(3, 10)])
    den = 1 - c * (1 - t) + m
    if den <= 0:
        return None
    k = 1 / den
    kf = 1 / (1 - c * (1 - t))
    assert k < kf                                  # les importations réduisent le multiplicateur
    # contrôle par itérations : fuite = 1 − c(1−t) + m de chaque unité de revenu
    tot, flux = F(0), F(1)
    for _ in range(600):
        tot += flux
        flux *= (c * (1 - t) - m)
    assert abs(float(tot - k)) < 1e-6
    v = rd(k, 2)
    if v is None:
        return None
    return D(f"Économie ouverte : propension marginale à consommer {ff(c, 2)}, taux d'imposition proportionnel {ff(t, 2)}, propension marginale à importer {ff(m, 2)} (importations = m·Y). Quel est le multiplicateur de la dépense autonome (arrondi à 0,01) ?",
             ff(v), nw(v, [kf, 1 / (1 - c + m), 1 / (1 - c * (1 - t) - m), 1 / m], ff), f"k = 1 ÷ (1 − c(1 − t) + m) = 1 ÷ {ff(den, 3)}.")


@gen(C, "l2e-mult-forfaitaire", cap=60, cat=MAC, source=SRC)
def mult_forfaitaire(rng, d):
    c = rng.choice([F(1, 2), F(3, 5), F(3, 4), F(4, 5), F(9, 10)])
    dx = rng.choice([10, 20, 30, 40, 50, 60, 100, 200])
    kg = 1 / (1 - c)
    kt = -c / (1 - c)
    assert kg + kt == 1                            # multiplicateur du budget équilibré
    ask = rng.choice(["g", "t", "eq"])
    ent = f"Économie fermée, C = c0 + {ff(c, 2)}·(Y − T) avec un impôt forfaitaire T. "
    if ask == "g":
        v = kg * dx
        return D(ent + f"Quelle est la variation du revenu d'équilibre si G augmente de {dx} ?", ff(v, 1), nw(v, [kt * dx, dx, c * dx, dx / c], lambda x: ff(x, 1)), f"ΔY = ΔG ÷ (1 − c) = {dx} ÷ {ff(1 - c, 2)}.")
    if ask == "t":
        v = kt * dx
        return D(ent + f"Quelle est la variation du revenu d'équilibre si T augmente de {dx} ?", ff(v, 1), nw(v, [kg * dx, -dx, c * dx, -dx / c], lambda x: ff(x, 1)), f"ΔY = −c ÷ (1 − c) × ΔT = −{ff(c, 2)} ÷ {ff(1 - c, 2)} × {dx}.")
    return D(ent + f"G et T augmentent simultanément de {dx} (budget équilibré). Quelle est la variation du revenu d'équilibre ?", ff(dx, 1), nw(dx, [kg * dx, kt * dx, 0, dx * c], lambda x: ff(x, 1)),
             "Multiplicateur du budget équilibré : 1/(1 − c) − c/(1 − c) = 1.")


@gen(C, "l2e-islm", cap=60, cat=MAC, source=SRC)
def islm(rng, d):
    c = rng.choice([F(3, 5), F(3, 4), F(4, 5)])
    dd, h = rng.randrange(10, 41, 5), rng.randrange(10, 61, 5)
    k = rng.choice([F(1, 4), F(1, 2), F(1, 5)])
    ys, rs = rng.randrange(600, 2001, 100), rng.randint(2, 12)
    t_, i0, g = rng.randrange(100, 301, 20), rng.randrange(100, 401, 20), rng.randrange(100, 401, 20)
    A = (1 - c) * ys + dd * rs
    c0 = A - (-c * t_ + i0 + g)
    ms = k * ys - h * rs
    if c0 <= 0 or ms <= 0 or c0.denominator != 1 or ms.denominator != 1:
        return None
    c0, ms = int(c0), int(ms)
    # résolution indépendante : règle de Cramer sur le système (1−c)Y + d·r = A ; kY − h·r = M
    a11, a12, a21, a22 = 1 - c, F(dd), k, F(-h)
    b1, b2 = F(c0) - c * t_ + i0 + g, F(ms)
    det = a11 * a22 - a12 * a21
    yy, rr = (b1 * a22 - a12 * b2) / det, (a11 * b2 - a21 * b1) / det
    assert yy == ys and rr == rs
    ask = rng.choice(["y", "r"])
    ent = (f"IS : C = {c0} + {ff(c, 2)}·(Y − T), I = {i0} − {dd}·r, G = {g}, T = {t_} ; LM : offre de monnaie réelle {ms} = {ff(k, 2)}·Y − {h}·r (r en points de %). ")
    if ask == "y":
        ysimple = F(c0 - c * t_ + i0 + g, 1) / (1 - c)
        return D(ent + "Quel est le revenu d'équilibre Y ?", fr(ys), [fr(int(x)) for x in (ysimple, ys + 100, ys - 100, ms / k, ys * 2) if int(x) != ys and x > 0],
                 f"On résout les deux équations : on trouve Y = {fr(ys)} et r = {rs} (vérification : IS et LM sont satisfaites).")
    return D(ent + "Quel est le taux d'intérêt d'équilibre r (en points de pourcentage) ?", fr(rs), [fr(x) for x in (rs + 1, rs - 1 if rs > 1 else rs + 3, rs * 2, rs + 2, (k * ys - ms) // 1 // h if h else rs + 4) if x != rs and x > 0],
             f"On remplace Y = {fr(ys)} dans LM : {ms} = {ff(k, 2)}×{fr(ys)} − {h}r, donc r = {rs}.")


@gen(C, "l2e-balance-paiements", cap=60, cat=INT, source=SRC)
def balance_paiements(rng, d):
    x, m = rng.randrange(50, 400, 10), rng.randrange(50, 400, 10)
    xs, ms_ = rng.randrange(10, 150, 5), rng.randrange(10, 150, 5)
    rr, rv = rng.randrange(5, 80, 5), rng.randrange(5, 80, 5)
    tr, tv = rng.randrange(5, 60, 5), rng.randrange(5, 60, 5)
    cc = (x - m) + (xs - ms_) + (rr - rv) + (tr - tv)
    cc2 = (x + xs + rr + tr) - (m + ms_ + rv + tv)
    assert cc == cc2
    ck = rng.randrange(-20, 40, 5)
    ent = f"Transactions courantes (milliards de FCFA), crédits/débits : biens {x}/{m} ; services {xs}/{ms_} ; revenus {rr}/{rv} ; transferts {tr}/{tv}. "
    ask = rng.choice(["cc", "bc", "fin"])
    sg = lambda v: fr(v)
    if ask == "cc":
        return D(ent + "Quel est le solde du compte courant ?", sg(cc), [sg(v) for v in (x - m, x + xs + rr + tr, (x - m) + (xs - ms_), cc + 2 * (tr - tv), -cc) if v != cc],
                 "Solde courant = (exportations − importations de biens) + (services) + (revenus nets) + (transferts nets).")
    if ask == "bc":
        return D(ent + "Quel est le solde de la balance commerciale (biens seuls) ?", sg(x - m), [sg(v) for v in (cc, (x - m) + (xs - ms_), xs - ms_, x + m, m - x) if v != x - m],
                 "La balance commerciale ne retient que les échanges de biens : exportations − importations de biens.")
    if cc + ck == 0:
        return None
    tag = lambda v: ("Capacité de financement de " if v > 0 else "Besoin de financement de ") + fr(abs(v)) + " milliards"
    return D(ent + f"Le solde du compte de capital est de {fr(ck)}. Que vaut le solde combiné (courant + capital) ?", tag(cc + ck),
             [tag(v) for v in (-(cc + ck), cc, ck, cc - ck) if v != cc + ck and v != 0][:3] + [tag(cc + ck + 40)],
             "Capacité (+) ou besoin (−) de financement de la nation = solde courant + solde du compte de capital.")


# ================================================================================================ CHANGE
@gen(C, "l2e-change-croise", cap=60, cat=INT, source=SRC)
def change_croise(rng, d):
    usd = rng.choice([480, 500, 520, 540, 560, 580, 600, 620, 640])
    eur = rng.choice([620, 640, 650, 655, 660, 680, 700, 720])
    gbp = rng.choice([740, 760, 780, 800, 820, 840])
    mnt = rng.choice([100, 200, 500, 1000, 2000, 5000])
    which = rng.choice(["eur_usd", "usd_eur", "conv", "gbp_eur"])
    ent = f"On observe : 1 dollar = {usd} FCFA ; 1 euro = {eur} FCFA" + (f" ; 1 livre = {gbp} FCFA" if which == "gbp_eur" else "") + ". "
    if which == "eur_usd":
        r = F(eur, usd)
        assert F(eur) / usd == 1 / (F(usd) / eur)
        v = rd(r, 3)
        if v is None:
            return None
        return D(ent + "Combien de dollars faut-il pour acheter 1 euro (cours croisé, arrondi à 0,001) ?", ff(v, 3), nw(v, [F(usd, eur), F(eur - usd), F(eur + usd, 2), F(eur * usd, 1000)], lambda x: ff(x, 3)),
                 f"Cours croisé EUR/USD = {eur} ÷ {usd} : on passe par le FCFA.")
    if which == "usd_eur":
        r = F(usd, eur)
        v = rd(r, 3)
        if v is None:
            return None
        return D(ent + "Combien d'euros vaut 1 dollar (cours croisé, arrondi à 0,001) ?", ff(v, 3), nw(v, [F(eur, usd), F(usd - eur), F(usd * eur, 1000)], lambda x: ff(x, 3)),
                 f"1 dollar = {usd} FCFA = {usd} ÷ {eur} euros.")
    if which == "conv":
        r = F(mnt * usd, eur)
        v = rd(r, 2)
        if v is None:
            return None
        return D(ent + f"Combien d'euros obtient-on pour {fr(mnt)} dollars (via le FCFA, arrondi à 0,01) ?", ff(v, 2), nw(v, [F(mnt * eur, usd), mnt * (usd - eur), F(mnt, usd) * eur, mnt * usd], lambda x: ff(x, 2)),
                 f"{fr(mnt)} dollars = {fr(mnt * usd)} FCFA = {fr(mnt * usd)} ÷ {eur} euros.")
    r = F(gbp, eur)
    v = rd(r, 3)
    if v is None:
        return None
    return D(ent + "Combien d'euros vaut 1 livre (cours croisé, arrondi à 0,001) ?", ff(v, 3), nw(v, [F(eur, gbp), F(gbp - eur), F(gbp, usd)], lambda x: ff(x, 3)), f"1 livre = {gbp} FCFA = {gbp} ÷ {eur} euros.")


@gen(C, "l2e-arbitrage-triangulaire", cap=60, cat=INT, source=SRC)
def arbitrage(rng, d):
    a = F(rng.randint(100, 130), 100)           # 1 EUR = a USD
    b = F(rng.randint(60, 90), 100)             # 1 USD = b GBP
    c = F(rng.randint(100, 140), 100)           # 1 GBP = c EUR
    prod = a * b * c
    if prod == 1:
        return None
    m = rng.choice([1000000, 2000000, 5000000])
    fin = m * prod
    assert fin.denominator == 1
    fin = int(fin)
    # contrôle étape par étape
    x = m * a
    x *= b
    x *= c
    assert x == fin
    gain = fin - m
    right = (f"Gain de {fr(gain)} €" if gain > 0 else f"Perte de {fr(-gain)} €")
    wr = [(f"Gain de {fr(abs(gain))} €" if gain < 0 else f"Perte de {fr(abs(gain))} €"), "Aucun gain ni perte"]
    other = int(m * (1 / (a * b * c)) - m) if (m / (a * b * c)).denominator == 1 else gain + 3000
    wr.append(f"Gain de {fr(abs(other))} €" if other > 0 else f"Perte de {fr(abs(other))} €")
    wr.append(f"Gain de {fr(abs(gain) * 2)} €" if gain > 0 else f"Perte de {fr(abs(gain) * 2)} €")
    return D(f"On observe : 1 euro = {ff(a, 2)} dollar ; 1 dollar = {ff(b, 2)} livre ; 1 livre = {ff(c, 2)} euro. Sans frais, on convertit {fr(m)} € en dollars, puis en livres, puis de nouveau en euros. Quel est le résultat ?", right, [w for w in wr if w != right],
             f"Le produit des trois cours vaut {ff(prod, 6)} : {fr(m)} × {ff(prod, 6)} = {fr(fin)} €.")


@gen(C, "l2e-ppa-absolue", cap=60, cat=INT, source=SRC)
def ppa_absolue(rng, d):
    pd = rng.randrange(20000, 120000, 1000)
    pf = rng.randrange(30, 200, 5)
    e = F(pd, pf)
    ask = rng.choice(["ppa", "eval"])
    if ask == "ppa":
        v = rd(e, 2)
        if v is None:
            return None
        return D(f"Un même panier de biens coûte {fr(pd)} FCFA dans un pays et {fr(pf)} dollars aux États-Unis. Quel est le cours du dollar en FCFA selon la parité des pouvoirs d'achat absolue (arrondi à 0,01) ?", ff(v), nw(v, [F(pf, pd), pd - pf, F(pd, pf) / 100, F(pd + pf, 2)], ff),
                 f"PPA absolue : cours = prix intérieur ÷ prix étranger = {fr(pd)} ÷ {fr(pf)}.")
    mk = e * rng.choice([F(4, 5), F(6, 5), F(3, 2), F(7, 10), F(11, 10)])
    mk = F(int(mk))
    if mk == e or mk <= 0:
        return None
    sous = mk > e
    right = "Le FCFA est sous-évalué par rapport à la PPA" if sous else "Le FCFA est surévalué par rapport à la PPA"
    wr = ["Le FCFA est surévalué par rapport à la PPA" if sous else "Le FCFA est sous-évalué par rapport à la PPA", "Le FCFA est exactement à sa valeur d'équilibre selon la PPA",
          "On ne peut rien conclure sans connaître l'inflation", ]
    return D(f"Un même panier coûte {fr(pd)} FCFA localement et {fr(pf)} dollars aux États-Unis ; le cours observé est de {fr(int(mk))} FCFA pour 1 dollar. Quelle conclusion tire-t-on avec la PPA absolue ?", right, wr,
             f"Cours PPA = {ff(e, 2)} ; le dollar vaut {'plus' if sous else 'moins'} de FCFA sur le marché ({fr(int(mk))}) : la monnaie locale est {'sous' if sous else 'sur'}évaluée.")


@gen(C, "l2e-ppa-relative", cap=60, cat=INT, source=SRC)
def ppa_relative(rng, d):
    e0 = rng.randrange(400, 800, 10)
    pi = rng.choice([2, 3, 4, 5, 6, 8, 10, 12, 15])
    pf = rng.choice([0, 1, 2, 3, 4])
    if pi <= pf:
        return None
    e1 = e0 * F(100 + pi, 100 + pf)
    # contrôle : variation relative du cours = écart d'inflation (exact)
    assert e1 / e0 == F(100 + pi, 100 + pf)
    v = rd(e1, 2)
    if v is None:
        return None
    return D(f"Le dollar vaut {e0} FCFA. Pendant un an, l'inflation est de {pi} % dans la zone du FCFA et de {pf} % aux États-Unis. Selon la PPA relative (e1 = e0 × (1 + π) ÷ (1 + π*)), quel est le nouveau cours du dollar (arrondi à 0,01) ?", ff(v),
             nw(v, [e0, e0 * (1 + F(pi - pf, 100)), e0 * F(100 + pf, 100 + pi), e0 * (1 - F(pi - pf, 100))], ff),
             f"e1 = {e0} × {100 + pi}/{100 + pf} : la monnaie du pays à inflation plus forte se déprécie.")
