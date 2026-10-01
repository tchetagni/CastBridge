"""Licence 1 Économie: micro and macro exercises with exact computed answers (equilibrium, elasticities, costs, interest,
multipliers, national accounts, accounting, trade). Amounts in FCFA; Cameroon rules (TVA 19,25 %, parity 1 € = 655,957 FCFA)
are flagged for checking against the current Code général des impôts / CEMAC texts."""
import math
from fractions import Fraction

from ..core import Draft, fr, gen, near_ints
from .mathfmt import num, nz

C = "l1-eco"
SRC = "Cours d'introduction à l'économie (micro et macro), L1 - réponse calculée"
CEMAC = "Parité fixe 1 euro = 655,957 FCFA (Zone franc, Banque de France / BEAC) - à vérifier"
TVA = "Taux de TVA au Cameroun : 17,5 % + 10 % de centimes additionnels = 19,25 % (Code général des impôts) - à vérifier"


def cf(n):
    """Coefficient written before a variable: 1 is omitted."""
    return "" if n == 1 else n


def dec(x, d=2):
    """Fraction/float -> French decimal without useless zeros."""
    return fr(float(x), d)


def wrongs_near(rng, v, n=6, kind="int"):
    if kind == "int":
        return [fr(x) for x in near_ints(rng, int(v), n)]
    return [dec(x) for x in (v * 1.1, v * 0.9, v + 1, v - 1, v * 2, v / 2)]


@gen(C, "eco-equilibrium-p", cap=240, cat="Microéconomie")
def equilibrium_p(rng, d):
    b, dd = rng.randint(1, 5), rng.randint(1, 5)
    p = rng.randint(2, 30)
    q = rng.randint(10, 100)
    a, c = q + b * p, q - dd * p
    if c < 0:
        return None
    assert a - b * p == c + dd * p == q
    ask_p = rng.random() < 0.5
    if ask_p:
        return Draft(f"La demande est Qd = {a} − {cf(b)}P et l'offre Qs = {c} + {cf(dd)}P. Quel est le prix d'équilibre ?", fr(p) + " FCFA" if False else str(p), [str(x) for x in near_ints(rng, p, 6, lo=1)] + [dec(Fraction(a + c, b + dd))],
                     f"À l'équilibre Qd = Qs : {a} − {cf(b)}P = {c} + {cf(dd)}P donc P = {a - c} ÷ {b + dd} = {p}.", src=SRC)
    return Draft(f"La demande est Qd = {a} − {cf(b)}P et l'offre Qs = {c} + {cf(dd)}P. Quelle est la quantité d'équilibre ?", str(q), [str(x) for x in near_ints(rng, q, 6, lo=1)] + [str(p)],
                 f"Prix d'équilibre P = {a - c} ÷ {b + dd} = {p} ; quantité Q = {a} − {b} × {p} = {q}.", src=SRC)


@gen(C, "eco-elasticity-point", cap=200, cat="Microéconomie")
def elasticity_point(rng, d):
    b, p = rng.randint(1, 6), rng.randint(2, 20)
    q = rng.randint(5, 80)
    a = q + b * p
    e = Fraction(-b * p, q)
    kind = "élastique" if abs(e) > 1 else ("unitaire" if abs(e) == 1 else "inélastique")
    return Draft(f"La demande est Qd = {a} − {cf(b)}P. Quelle est l'élasticité-prix au point P = {p} ?", dec(e, 3), [dec(x, 3) for x in (-e, Fraction(-b), Fraction(p, q), e * 2, Fraction(-q, b * p))],
                 f"e = (dQ/dP) × (P/Q) = (−{b}) × ({p}/{q}) = {dec(e, 3)} (demande {kind}, |e| {'>' if abs(e) > 1 else '<' if abs(e) < 1 else '='} 1).", src=SRC)


@gen(C, "eco-elasticity-arc", cap=220, cat="Microéconomie")
def elasticity_arc(rng, d):
    p0, q0 = rng.choice([100, 200, 500, 1000]), rng.choice([100, 200, 400, 500, 1000])
    dp = rng.choice([5, 10, 20, 25, -10, -20, -5])
    dq = -rng.choice([5, 10, 15, 20, 30, 40]) * (1 if dp > 0 else -1)
    e = Fraction(dq * 100, q0) / Fraction(dp * 100, p0)
    return Draft(f"Le prix passe de {p0} à {p0 + p0 * dp // 100} FCFA ({'+' if dp > 0 else '−'}{abs(dp)} %) et la quantité demandée de {q0} à {q0 + q0 * dq // 100} ({'+' if dq > 0 else '−'}{abs(dq)} %). Quelle est l'élasticité-prix de la demande ?" if (p0 * dp) % 100 == 0 and (q0 * dq) % 100 == 0 else "", dec(e, 2),
                 [dec(x, 2) for x in (-e, Fraction(dp, dq) if dq else 1, e * 2, Fraction(dq - dp, 1), e + 1)], f"e = variation relative de Q ÷ variation relative de P = ({dq} %) ÷ ({dp} %) = {dec(e, 2)}.", src=SRC) if (p0 * dp) % 100 == 0 and (q0 * dq) % 100 == 0 and dq != 0 else None


@gen(C, "eco-marginal-cost", cap=240, cat="Microéconomie")
def marginal_cost(rng, d):
    a, b, c = rng.randint(1, 6), rng.randint(0, 40), rng.randint(50, 500)
    q = rng.randint(2, 25)
    cm = 2 * a * q + b
    cmean = Fraction(a * q * q + b * q + c, q)
    if rng.random() < 0.5:
        return Draft(f"Le coût total est CT(q) = {a}q² + {b}q + {c}. Quel est le coût marginal pour q = {q} ?", str(cm), [str(x) for x in near_ints(rng, cm, 6, lo=1)] + [str(a * q * q + b * q + c)], f"Cm = dCT/dq = {2 * a}q + {b} ; pour q = {q} : {cm}.", src=SRC)
    return Draft(f"Le coût total est CT(q) = {a}q² + {b}q + {c}. Quel est le coût moyen pour q = {q} ?", dec(cmean), [dec(x) for x in (cm, Fraction(a * q * q + b * q + c), cmean + 5, cmean - 5, a * q + b)], f"CM = CT/q = ({a}×{q}² + {b}×{q} + {c})/{q} = {dec(cmean)}.", src=SRC)


@gen(C, "eco-monopoly", cap=200, cat="Microéconomie")
def monopoly(rng, d):
    b = rng.randint(1, 4)
    c = rng.randint(2, 12)
    q = rng.randint(3, 25)
    a = 2 * b * q + c
    p = a - b * q
    profit = (p - c) * q
    assert a - 2 * b * q == c
    if rng.random() < 0.5:
        return Draft(f"Un monopole fait face à la demande inverse P = {a} − {b}q et a un coût marginal constant de {c}. Quelle quantité maximise son profit ?", str(q), [str(x) for x in near_ints(rng, q, 6, lo=1)] + [str((a - c) // b if (a - c) % b == 0 else q + 4)],
                     f"Recette marginale Rm = {a} − {2 * b}q ; Rm = Cm donne q = ({a} − {c}) ÷ {2 * b} = {q}.", src=SRC)
    return Draft(f"Un monopole fait face à la demande inverse P = {a} − {b}q et a un coût marginal constant de {c}. Quel est son profit maximal ?", str(profit), [str(x) for x in near_ints(rng, profit, 6, lo=1)] + [str((a - c) * q)],
                 f"q* = {q}, P* = {p}, profit = (P − Cm) × q = ({p} − {c}) × {q} = {profit}.", src=SRC)


@gen(C, "eco-competition", cap=200, cat="Microéconomie")
def competition(rng, d):
    a, b = rng.randint(1, 4), rng.randint(0, 20)
    q = rng.randint(2, 30)
    p = 2 * a * q + b
    return Draft(f"Une entreprise en concurrence parfaite a un coût total CT(q) = {a}q² + {b}q + 100 et vend au prix {p}. Quelle quantité maximise son profit ?", str(q), [str(x) for x in near_ints(rng, q, 6, lo=1)] + [str(p // a if p % a == 0 else q + 5)],
                 f"On pose P = Cm : {p} = {2 * a}q + {b} donc q = {q}.", src=SRC)


@gen(C, "eco-surplus", cap=160, cat="Microéconomie")
def surplus(rng, d):
    b, dd = rng.randint(1, 4), rng.randint(1, 4)
    p, q = rng.randint(4, 30), rng.randint(10, 60)
    a = q + b * p
    pmax = Fraction(a, b)
    s = Fraction((pmax - p) * q, 2)
    return Draft(f"La demande est Qd = {a} − {cf(b)}P et le prix d'équilibre est {p} (quantité {q}). Quel est le surplus des consommateurs ?", dec(s), [dec(x) for x in (s * 2, Fraction(q * p, 2), s + 10, s / 2, (pmax - p) * q / 3)],
                 f"Prix maximal P = {a}/{b} = {dec(pmax)} ; surplus = (P_max − P*) × Q* ÷ 2 = {dec(s)}.", src=SRC)


@gen(C, "eco-cobb-douglas", cap=200, cat="Microéconomie")
def cobb_douglas(rng, d):
    px, py = rng.choice([1, 2, 4, 5, 10, 20]), rng.choice([1, 2, 5, 10, 25])
    inc = rng.choice([100, 200, 300, 500, 1000, 2000])
    xq = Fraction(inc, 2 * px)
    return Draft(f"Un consommateur au revenu de {inc} FCFA a la fonction d'utilité U = x·y avec px = {px} et py = {py}. Quelle quantité de x achète-t-il à l'optimum ?", dec(xq), [dec(x) for x in (Fraction(inc, px), Fraction(inc, 2 * py), xq * 2, xq / 2, Fraction(inc, px + py))],
                 f"Pour U = x·y, le consommateur dépense la moitié de son revenu sur chaque bien : x = R/(2·px) = {inc}/{2 * px} = {dec(xq)}.", src=SRC)


@gen(C, "eco-compound", cap=260, cat="Mathématiques financières")
def compound(rng, d):
    c0, i, n = rng.choice([10000, 20000, 50000, 100000, 200000, 500000, 1000000]), rng.choice([5, 8, 10, 12, 15, 20]), rng.randint(2, 6)
    cn = Fraction(c0) * Fraction(100 + i, 100) ** n
    simple = c0 + c0 * i * n // 100
    return Draft(f"On place {fr(c0)} FCFA à {i} % par an à intérêts composés pendant {n} ans. Quel est le capital final ?", f"{fr(round(float(cn), 2), 2)} FCFA" if cn.denominator != 1 else f"{fr(int(cn))} FCFA",
                 [f"{fr(round(x, 2), 2)} FCFA" for x in (simple, float(cn) * 1.05, float(cn) * 0.95, c0 * (1 + i / 100) * n, c0 + i * n)],
                 f"Cn = C0 × (1 + i)ⁿ = {fr(c0)} × {dec(Fraction(100 + i, 100), 2)}^{n} = {fr(round(float(cn), 2), 2)} FCFA.", src=SRC)


@gen(C, "eco-simple-interest", cap=200, cat="Mathématiques financières")
def simple_interest(rng, d):
    c0, i, t = rng.choice([50000, 100000, 250000, 500000, 1000000, 2000000]), rng.choice([4, 5, 6, 8, 10, 12]), rng.choice([0.5, 1, 1.5, 2, 3, 0.25])
    v = c0 * i * t / 100
    return Draft(f"Quel est l'intérêt simple produit par {fr(c0)} FCFA placés à {i} % par an pendant {dec(t)} an(s) ?", f"{fr(v)} FCFA", [f"{fr(x)} FCFA" for x in (v * 2, v / 2, c0 + v, c0 * i / 100, v * 10)],
                 f"I = C × i × t = {fr(c0)} × {i}/100 × {dec(t)} = {fr(v)} FCFA.", src=SRC)


@gen(C, "eco-discount", cap=200, cat="Mathématiques financières")
def discount(rng, d):
    f, i, n = rng.choice([110000, 121000, 133100, 1100000, 1210000, 2200000]), 10, rng.randint(1, 3)
    if f % (11 ** n // math.gcd(11 ** n, 10 ** n) if False else 1):
        return None
    va = Fraction(f) / Fraction(110, 100) ** n
    if va.denominator != 1:
        return None
    return Draft(f"Quelle est la valeur actuelle de {fr(f)} FCFA reçus dans {n} an(s) avec un taux d'actualisation de {i} % ?", f"{fr(int(va))} FCFA", [f"{fr(int(x))} FCFA" for x in (f * 0.9 ** n if False else f - f * i * n / 100, f / 1.2 ** n, f * 1.1 ** n, f / (1 + i * n))],
                 f"VA = F ÷ (1 + i)ⁿ = {fr(f)} ÷ 1,1^{n} = {fr(int(va))} FCFA.", src=SRC)


@gen(C, "eco-npv", cap=200, cat="Mathématiques financières")
def npv(rng, d):
    inv = rng.choice([100000, 200000, 500000, 1000000])
    cf = inv * 11 // 10 * 1 if False else Fraction(inv * 121, 100)
    i = 10
    van = Fraction(cf) / Fraction(11, 10) ** 2 - inv
    cf_int = int(cf) if cf.denominator == 1 else None
    if cf_int is None:
        return None
    return Draft(f"Un projet coûte {fr(inv)} FCFA aujourd'hui et rapporte {fr(cf_int)} FCFA dans 2 ans. Quelle est sa valeur actuelle nette au taux de {i} % ?", f"{fr(int(van))} FCFA", [f"{fr(x)} FCFA" for x in (cf_int - inv, -inv, inv // 10, cf_int // 2 - inv)],
                 f"VAN = {fr(cf_int)} ÷ 1,1² − {fr(inv)} = {fr(cf_int // 1)} ÷ 1,21 − {fr(inv)} = {fr(int(van))} FCFA.", src=SRC)


@gen(C, "eco-inflation", cap=220, cat="Macroéconomie")
def inflation(rng, d):
    i0, i1 = rng.choice([100, 105, 110, 120, 125, 200]), 0
    rate = rng.choice([2, 2.5, 4, 5, 8, 10, 12, 20])
    i1 = i0 * (1 + rate / 100)
    return Draft(f"L'indice des prix passe de {dec(i0)} à {dec(i1)}. Quel est le taux d'inflation ?", f"{dec(rate)} %", [f"{dec(x)} %" for x in (rate * 2, rate / 2, i1 - i0 + 1, rate + 1, (i1 - i0))],
                 f"Taux = (I₁ − I₀)/I₀ × 100 = ({dec(i1)} − {dec(i0)})/{dec(i0)} × 100 = {dec(rate)} %.", src=SRC)


@gen(C, "eco-growth", cap=220, cat="Macroéconomie")
def growth(rng, d):
    y0, g = rng.choice([10000, 20000, 25000, 40000, 50000]), rng.choice([2, 3, 4, 5, 6, 8, 10])
    y1 = y0 * (100 + g) // 100
    return Draft(f"Le PIB d'un pays passe de {fr(y0)} à {fr(y1)} milliards de FCFA. Quel est son taux de croissance ?", f"{g} %", [f"{x} %" for x in (g * 2, g + 1, g - 1, max(1, g // 2), y1 - y0)],
                 f"Taux = (PIB₁ − PIB₀)/PIB₀ × 100 = {fr(y1 - y0)}/{fr(y0)} × 100 = {g} %.", src=SRC)


@gen(C, "eco-multiplier", cap=220, cat="Macroéconomie")
def multiplier(rng, d):
    c = rng.choice([Fraction(1, 2), Fraction(3, 4), Fraction(4, 5), Fraction(2, 3), Fraction(9, 10), Fraction(3, 5)])
    k = 1 / (1 - c)
    di = rng.choice([10, 20, 50, 100, 200])
    if rng.random() < 0.5:
        return Draft(f"La propension marginale à consommer est de {num(c)}. Quel est le multiplicateur keynésien ?", num(k), [num(x) for x in (1 / c, c / (1 - c), 1 - c, k + 1, k * 2)], f"k = 1/(1 − c) = 1/(1 − {num(c)}) = {num(k)}.", src=SRC)
    return Draft(f"La propension marginale à consommer est de {num(c)}. De combien augmente le revenu national si l'investissement augmente de {di} milliards ?", dec(k * di), [dec(x) for x in (di / c, di * c, di * (1 - c), k * di + di, di)],
                 f"ΔY = k × ΔI = {num(k)} × {di} = {dec(k * di)} milliards.", src=SRC)


@gen(C, "eco-keynes-equilibrium", cap=200, cat="Macroéconomie")
def keynes(rng, d):
    c = rng.choice([Fraction(1, 2), Fraction(3, 4), Fraction(4, 5), Fraction(2, 3)])
    a, i, g = rng.choice([100, 200, 300, 500]), rng.choice([100, 200, 300]), rng.choice([100, 200, 400])
    y = (a + i + g) / (1 - c)
    return Draft(f"Dans une économie fermée : C = {a} + {num(c)}Y, I = {i}, G = {g}. Quel est le revenu d'équilibre Y ?", dec(y), [dec(x) for x in (a + i + g, (a + i + g) * c, (a + i + g) / c, y + 100, y / 2)],
                 f"Y = C + I + G = {a} + {num(c)}Y + {i + g}, donc Y = {a + i + g}/(1 − {num(c)}) = {dec(y)}.", src=SRC)


@gen(C, "eco-money-multiplier", cap=120, cat="Macroéconomie")
def money_mult(rng, d):
    r = rng.choice([5, 10, 20, 25])
    base = rng.choice([100, 200, 500, 1000])
    return Draft(f"Les banques doivent garder {r} % de leurs dépôts en réserves. Avec {base} milliards de monnaie centrale, quel est le montant maximal de dépôts créés ?", f"{fr(base * 100 // r)} milliards", [f"{fr(x)} milliards" for x in (base * r // 100, base * (100 - r) // 100, base * 100 // (100 - r), base + r)],
                 f"Multiplicateur = 1/{r} % = {100 // r} ; dépôts = {base} × {100 // r} = {fr(base * 100 // r)} milliards.", src=SRC)


@gen(C, "eco-unemployment", cap=200, cat="Macroéconomie")
def unemployment(rng, d):
    active, unemp = rng.choice([1000, 2000, 5000, 8000, 10000, 20000]), 0
    rate = rng.choice([4, 5, 8, 10, 12, 15, 20, 25])
    unemp = active * rate // 100
    if active * rate % 100:
        return None
    return Draft(f"Un pays compte {fr(active)} milliers d'actifs dont {fr(unemp)} milliers de chômeurs. Quel est le taux de chômage ?", f"{rate} %", [f"{x} %" for x in (rate * 2, rate + 5, max(1, rate - 3), 100 - rate, rate // 2)],
                 f"Taux de chômage = chômeurs ÷ population active × 100 = {fr(unemp)}/{fr(active)} × 100 = {rate} %.", src=SRC)


@gen(C, "eco-index", cap=200, cat="Statistiques")
def index_q(rng, d):
    v0, rate = rng.choice([200, 250, 400, 500, 800, 1000, 2000]), rng.choice([10, 20, 25, 30, 50, 75, -10, -20, -25])
    v1 = v0 + v0 * rate // 100
    if v0 * rate % 100:
        return None
    idx = v1 * 100 / v0
    return Draft(f"Une grandeur vaut {fr(v0)} en année de base (indice 100) puis {fr(v1)}. Quel est son indice ?", dec(idx), [dec(x) for x in (idx - 100, 100 - idx if idx < 100 else idx + 10, v1 / v0, idx * 10, idx / 2)],
                 f"Indice = valeur ÷ valeur de base × 100 = {fr(v1)}/{fr(v0)} × 100 = {dec(idx)}.", src=SRC)


@gen(C, "eco-deflator", cap=200, cat="Macroéconomie")
def deflator(rng, d):
    nominal, defl = rng.choice([1200, 2400, 3000, 5000, 6000, 12000]), rng.choice([100, 110, 120, 125, 150, 200])
    real = nominal * 100 / defl
    return Draft(f"Le PIB nominal est de {fr(nominal)} milliards et le déflateur du PIB de {defl} (base 100). Quel est le PIB réel ?", dec(real), [dec(x) for x in (nominal * defl / 100, nominal - defl, nominal / defl, real + 100, real * 2)],
                 f"PIB réel = PIB nominal ÷ déflateur × 100 = {fr(nominal)} ÷ {defl} × 100 = {dec(real)} milliards.", src=SRC)


@gen(C, "eco-fisher", cap=160, cat="Macroéconomie")
def fisher(rng, d):
    nom, inf = rng.choice([4, 5, 6, 8, 10, 12, 15]), rng.choice([1, 2, 3, 4, 5, 7])
    if inf >= nom + 3:
        return None
    return Draft(f"Le taux d'intérêt nominal est de {nom} % et l'inflation de {inf} %. Quel est (approximativement) le taux d'intérêt réel ?", f"{nom - inf} %", [f"{x} %" for x in (nom + inf, nom * inf, nom - inf + 1, inf - nom if inf > nom else nom)],
                 f"Taux réel ≈ taux nominal − inflation = {nom} − {inf} = {nom - inf} %.", src=SRC)


@gen(C, "eco-quantity-theory", cap=160, cat="Macroéconomie")
def quantity_theory(rng, d):
    m, v, q = rng.choice([100, 200, 400, 500, 1000]), rng.choice([2, 4, 5, 10]), rng.choice([20, 40, 50, 100, 200])
    p = Fraction(m * v, q)
    return Draft(f"Dans l'équation des échanges M·V = P·Q, on a M = {m}, V = {v} et Q = {q}. Quel est le niveau général des prix P ?", dec(p), [dec(x) for x in (Fraction(m * q, v), Fraction(v * q, m), p + 1, p * 2, Fraction(m, q))],
                 f"P = M × V ÷ Q = {m} × {v} ÷ {q} = {dec(p)}.", src=SRC)


@gen(C, "eco-productivity", cap=200, cat="Microéconomie")
def productivity(rng, d):
    q, l = rng.choice([100, 200, 360, 500, 1200, 2400]), rng.choice([5, 8, 10, 12, 20, 40])
    if q % l:
        p = Fraction(q, l)
    else:
        p = Fraction(q // l)
    return Draft(f"Une usine produit {fr(q)} tonnes avec {l} travailleurs. Quelle est la productivité moyenne du travail ?", f"{dec(p)} t/travailleur", [f"{dec(x)} t/travailleur" for x in (Fraction(l, q), p * 2, p + 1, p / 2, q - l)],
                 f"Productivité = production ÷ travail = {fr(q)}/{l} = {dec(p)}.", src=SRC)


@gen(C, "eco-opportunity-cost", cap=200, cat="Microéconomie")
def opportunity_cost(rng, d):
    a, b = rng.choice([10, 12, 20, 30, 40, 60]), rng.choice([20, 24, 30, 40, 60, 90])
    if a == b:
        return None
    return Draft(f"Avec toutes ses ressources, un pays produit soit {a} tonnes de cacao, soit {b} tonnes de café. Quel est le coût d'opportunité d'une tonne de cacao (en tonnes de café) ?", dec(Fraction(b, a)), [dec(x) for x in (Fraction(a, b), Fraction(a + b, a), Fraction(b, a) + 1, Fraction(b, a) * 2, b - a)],
                 f"Produire 1 t de cacao fait renoncer à {b}/{a} = {dec(Fraction(b, a))} t de café.", src=SRC)


@gen(C, "eco-ricardo", cap=200, cat="Commerce international")
def ricardo(rng, d):
    x = [rng.randint(2, 12) for _ in range(4)]   # hours per unit: A cacao, A coffee, B cacao, B coffee
    ac, af, bc, bf = x
    if ac * bf == af * bc:
        return None
    a_cacao = Fraction(ac, af)
    b_cacao = Fraction(bc, bf)
    winner = "A" if a_cacao < b_cacao else "B"
    return Draft(f"Il faut {ac} h au pays A pour 1 t de cacao et {af} h pour 1 t de café ; {bc} h et {bf} h au pays B. Quel pays a l'avantage comparatif pour le cacao ?", f"Le pays {winner}", [f"Le pays {'B' if winner == 'A' else 'A'}", "Aucun des deux", "Égalité parfaite", "Impossible à déterminer"],
                 f"Coût d'opportunité du cacao : A = {ac}/{af} = {dec(a_cacao, 3)} café ; B = {bc}/{bf} = {dec(b_cacao, 3)} café. Il est plus faible au pays {winner}.", src=SRC)


@gen(C, "eco-trade-balance", cap=160, cat="Commerce international")
def trade_balance(rng, d):
    x, m = rng.choice([2000, 3000, 4500, 5200, 7000, 8000]), rng.choice([1800, 2500, 3900, 6000, 6500, 9000])
    if x == m:
        return None
    s = x - m
    return Draft(f"Un pays exporte pour {fr(x)} milliards de FCFA et importe pour {fr(m)} milliards. Quel est le solde de sa balance commerciale ?", f"{'+' if s > 0 else '−'}{fr(abs(s))} milliards", [f"{'+' if t > 0 else '−'}{fr(abs(t))} milliards" for t in (-s, x + m, s * 2, x // 2 - m // 2)],
                 f"Solde = exportations − importations = {fr(x)} − {fr(m)} = {fr(s)} ({'excédent' if s > 0 else 'déficit'}).", src=SRC)


@gen(C, "eco-debt-ratio", cap=160, cat="Macroéconomie")
def debt_ratio(rng, d):
    dette, pib = rng.choice([3000, 4500, 6000, 7200, 9000]), rng.choice([10000, 12000, 15000, 18000, 20000, 24000])
    r = Fraction(dette * 100, pib)
    return Draft(f"La dette publique est de {fr(dette)} milliards de FCFA et le PIB de {fr(pib)} milliards. Quel est le ratio dette/PIB ?", f"{dec(r, 1)} %", [f"{dec(x, 1)} %" for x in (Fraction(pib * 100, dette), r * 2, r / 2, r + 10, dette * 100 / (pib + dette))],
                 f"{fr(dette)}/{fr(pib)} × 100 = {dec(r, 1)} %.", src=SRC)


@gen(C, "eco-tva", cap=240, cat="Fiscalité (Cameroun)", region="CM" if False else None, source=TVA)
def tva(rng, d):
    ht = rng.choice([10000, 20000, 25000, 40000, 50000, 80000, 100000, 120000, 200000, 400000, 1000000])
    ttc = ht * Fraction(11925, 10000)
    tax = ht * Fraction(1925, 10000)
    if rng.random() < 0.5:
        return Draft(f"Au Cameroun, avec la TVA à 19,25 %, quel est le prix TTC d'un bien à {fr(ht)} FCFA hors taxes ?", f"{dec(ttc)} FCFA", [f"{dec(x)} FCFA" for x in (ht * 1.175, ht * 1.19, ht * 1.2, ht - tax, ht + tax / 2)],
                     f"TTC = HT × 1,1925 = {fr(ht)} × 1,1925 = {dec(ttc)} FCFA.", src=TVA, region="CM")
    return Draft(f"Au Cameroun, avec la TVA à 19,25 %, quel est le montant de la TVA sur un bien à {fr(ht)} FCFA hors taxes ?", f"{dec(tax)} FCFA", [f"{dec(x)} FCFA" for x in (ht * 0.175, ht * 0.19, ht * 0.2, ht * 0.1925 / 1.1925, ht / 19.25)],
                 f"TVA = HT × 19,25 % = {fr(ht)} × 0,1925 = {dec(tax)} FCFA.", src=TVA, region="CM")


@gen(C, "eco-tva-ttc-ht", cap=200, cat="Fiscalité (Cameroun)", source=TVA)
def tva_inv(rng, d):
    ht = rng.choice([10000, 20000, 40000, 80000, 200000, 400000, 800000])
    ttc = ht * Fraction(11925, 10000)
    return Draft(f"Au Cameroun (TVA 19,25 %), un article coûte {dec(ttc)} FCFA toutes taxes comprises. Quel est son prix hors taxes ?", f"{fr(ht)} FCFA", [f"{dec(x)} FCFA" for x in (ttc * Fraction(8075, 10000), ttc - ttc * Fraction(1925, 10000), ttc / 1.175, ttc * 0.81, ttc - 1925)],
                 f"HT = TTC ÷ 1,1925 = {dec(ttc)} ÷ 1,1925 = {fr(ht)} FCFA (et non TTC − 19,25 %).", src=TVA, region="CM")


@gen(C, "eco-eur-fcfa", cap=200, cat="Commerce international", source=CEMAC)
def eur_fcfa(rng, d):
    e = rng.choice([1, 2, 5, 10, 20, 50, 100, 200, 500, 1000])
    v = Fraction(6559570, 10000) * e
    return Draft(f"Sachant que 1 euro = 655,957 FCFA (parité fixe), combien de FCFA obtient-on pour {fr(e)} euros ?", f"{dec(v, 3)} FCFA", [f"{dec(x, 3)} FCFA" for x in (e * 650, e * 665.957, v / 10, v * 10, e * 600)],
                 f"{fr(e)} × 655,957 = {dec(v, 3)} FCFA.", src=CEMAC, region="CM")


@gen(C, "eco-fcfa-eur", cap=160, cat="Commerce international", source=CEMAC)
def fcfa_eur(rng, d):
    e = rng.choice([1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000])
    f = Fraction(6559570, 10000) * e
    return Draft(f"Sachant que 1 euro = 655,957 FCFA (parité fixe), combien d'euros valent {dec(f, 3)} FCFA ?", f"{fr(e)} €", [f"{fr(x)} €" for x in near_ints(rng, e, 6, lo=1)] + [f"{fr(int(f))} €"],
                 f"{dec(f, 3)} ÷ 655,957 = {fr(e)} euros.", src=CEMAC, region="CM")


@gen(C, "eco-accounting-result", cap=200, cat="Comptabilité")
def accounting_result(rng, d):
    prod, ch = rng.choice([5000000, 8000000, 12000000, 20000000, 35000000]), rng.choice([3000000, 6000000, 9500000, 14000000, 40000000])
    if prod == ch:
        return None
    r = prod - ch
    return Draft(f"Une entreprise a des produits de {fr(prod)} FCFA et des charges de {fr(ch)} FCFA. Quel est son résultat ?", f"{'Bénéfice' if r > 0 else 'Perte'} de {fr(abs(r))} FCFA", [f"{'Perte' if r > 0 else 'Bénéfice'} de {fr(abs(r))} FCFA", f"Bénéfice de {fr(prod + ch)} FCFA", f"Bénéfice de {fr(prod // 2)} FCFA", "Résultat nul"],
                 f"Résultat = produits − charges = {fr(prod)} − {fr(ch)} = {fr(r)} : {'bénéfice' if r > 0 else 'perte'}.", src=SRC)


@gen(C, "eco-depreciation", cap=200, cat="Comptabilité")
def depreciation(rng, d):
    v, n, y = rng.choice([1000000, 2400000, 5000000, 8000000, 12000000]), rng.choice([4, 5, 8, 10]), 0
    y = rng.randint(1, 3)
    annual = Fraction(v, n)
    vnc = v - annual * y
    if rng.random() < 0.5:
        return Draft(f"Un matériel de {fr(v)} FCFA est amorti linéairement sur {n} ans. Quelle est la dotation annuelle aux amortissements ?", f"{dec(annual, 0)} FCFA", [f"{dec(x, 0)} FCFA" for x in (v * n, annual * 2, annual / 2, v / (n + 1), v * 0.2)],
                     f"Amortissement linéaire = valeur ÷ durée = {fr(v)} ÷ {n} = {dec(annual, 0)} FCFA par an.", src=SRC)
    return Draft(f"Un matériel de {fr(v)} FCFA est amorti linéairement sur {n} ans. Quelle est sa valeur nette comptable après {y} an(s) ?", f"{dec(vnc, 0)} FCFA", [f"{dec(x, 0)} FCFA" for x in (annual * y, v - annual * (y + 1), v - annual * (y - 1) if y > 1 else v, v, vnc * 1.1)],
                 f"VNC = {fr(v)} − {y} × {dec(annual, 0)} = {dec(vnc, 0)} FCFA.", src=SRC)


@gen(C, "eco-breakeven", cap=200, cat="Comptabilité")
def breakeven(rng, d):
    cf, p, cv = rng.choice([100000, 200000, 500000, 1200000, 3000000]), rng.choice([500, 1000, 2000, 2500, 5000]), 0
    cv = p * rng.choice([20, 40, 50, 60]) // 100
    q = Fraction(cf, p - cv)
    return Draft(f"Les charges fixes sont de {fr(cf)} FCFA, le prix de vente unitaire de {fr(p)} FCFA et le coût variable unitaire de {fr(cv)} FCFA. Quel est le seuil de rentabilité (en unités) ?", dec(q, 1), [dec(x, 1) for x in (Fraction(cf, p), Fraction(cf, cv), q * 2, q / 2, Fraction(cf, p + cv))],
                 f"Seuil = charges fixes ÷ marge unitaire = {fr(cf)} ÷ ({fr(p)} − {fr(cv)}) = {dec(q, 1)}.", src=SRC)


@gen(C, "eco-mean", cap=200, cat="Statistiques")
def mean_q(rng, d):
    n = rng.choice([4, 5, 6])
    vals = [rng.randint(5, 60) for _ in range(n)]
    m = Fraction(sum(vals), n)
    return Draft(f"Quelle est la moyenne arithmétique de la série {', '.join(map(str, vals))} ?", dec(m), [dec(x) for x in (m + 1, m - 1, Fraction(max(vals) + min(vals), 2), m * 2, sum(vals))], f"({' + '.join(map(str, vals))}) ÷ {n} = {sum(vals)}/{n} = {dec(m)}.", src=SRC)


@gen(C, "eco-variance", cap=200, cat="Statistiques")
def variance(rng, d):
    vals = [rng.randint(1, 12) for _ in range(rng.choice([3, 4, 5]))]
    n = len(vals)
    m = Fraction(sum(vals), n)
    v = sum((Fraction(x) - m) ** 2 for x in vals) / n
    v2 = Fraction(sum(x * x for x in vals), n) - m * m
    assert v == v2
    return Draft(f"Quelle est la variance de la série {', '.join(map(str, vals))} (formule sur la population) ?", dec(v, 3), [dec(x, 3) for x in (math.sqrt(v), v * n / (n - 1), v + 1, m, Fraction(sum(x * x for x in vals), n))],
                 f"Moyenne = {dec(m, 3)} ; variance = moyenne des carrés − carré de la moyenne = {dec(Fraction(sum(x * x for x in vals), n), 3)} − {dec(m * m, 3)} = {dec(v, 3)}.", src=SRC)


@gen(C, "eco-covariance-sign", cap=100, cat="Statistiques")
def corr_sign(rng, d):
    slope = rng.choice([-5, -3, -2, -1, 1, 2, 3, 4])
    xs = sorted(rng.sample(range(1, 15), 5))
    ys = [slope * x + rng.choice([0]) for x in xs]
    r = "1" if slope > 0 else "−1"
    return Draft(f"Les valeurs de y sont exactement y = {slope}x pour x = {', '.join(map(str, xs))}. Quel est le coefficient de corrélation linéaire entre x et y ?", r, [x for x in ("1", "−1", "0", "0,5", "−0,5") if x != r],
                 f"Les points sont alignés sur une droite de pente {'positive' if slope > 0 else 'négative'} : r = {r}.", src=SRC)


@gen(C, "eco-price-change-revenue", cap=200, cat="Microéconomie")
def revenue_change(rng, d):
    p, q = rng.choice([100, 200, 500, 1000]), rng.choice([100, 200, 500, 1000])
    dp, dq = rng.choice([10, 20, 25, 50]), rng.choice([10, 20, 25, 50])
    r0 = p * q
    r1 = Fraction(p * (100 + dp) * q * (100 - dq), 10000)
    var = (r1 - r0) * 100 / r0
    return Draft(f"Le prix augmente de {dp} % et la quantité vendue baisse de {dq} %. Comment varie la recette (prix × quantité) ?", f"{'+' if var >= 0 else '−'}{dec(abs(var))} %", [f"{'+' if x >= 0 else '−'}{dec(abs(x))} %" for x in (dp - dq, -var if var else 5, (dp - dq) * 2, var + 10, Fraction(dp * dq, 100))],
                 f"Coefficient = (1 + {dp} %) × (1 − {dq} %) = {dec(Fraction(100 + dp, 100) * Fraction(100 - dq, 100), 4)} : variation de {dec(var)} %.", src=SRC)
