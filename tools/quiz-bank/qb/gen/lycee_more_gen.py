"""Modèles supplémentaires de chimie, d'informatique, d'économie, de géographie et de SVT / Biology (fr et GCE en anglais).
Chaque réponse est recalculée ; les constantes utilisées (volume molaire, F, lapse rate…) sont données dans l'énoncé."""
import math
from fractions import Fraction

from ..core import Draft, fr
from .lycee_chem import EN_A as CH_EN_A, EN_O as CH_EN_O, FR1 as CH_FR1, FR2 as CH_FR2, FRT as CH_FRT, AM, COMP, S as CH_S, mass, nm, parse, sub, de, given
from .lycee_common import N, T, both
from .lycee_info import ALL_FR as INF_FR, EN_ALL as INF_EN, S as INF_S
from .lycee_life_eco_geo import BI, EC, EE, GE, GF, SRC, SV, cat as LC, pct
from .mathfmt import num


def cat(lang, a, b):
    return a if lang == "fr" else b


# ================================================================================================ chimie
@both(CH_FR1 + CH_FRT + CH_EN_A, "ch-limiting-reagent", cap=600, cat="Stœchiométrie", diffs=(4, 5))
def limiting_reagent(rng, d, lang):
    # 2H2 + O2 -> 2H2O : n(H2) = a, n(O2) = b ; water formed = 2 * min(a/2, b)
    a, b = rng.choice([1, 2, 3, 4, 5, 6, 8]), rng.choice([0.5, 1, 1.5, 2, 3, 4])
    water = 2 * min(a / 2, b)
    return Draft(T(lang, f"On fait réagir {a} mol de dihydrogène avec {N(lang, b)} mol de dioxygène selon 2 H₂ + O₂ → 2 H₂O. Combien de moles d'eau se forment au maximum ?", f"{a} mol of hydrogen reacts with {N(lang, b)} mol of oxygen: 2H₂ + O₂ → 2H₂O. What is the maximum amount of water formed, in moles?"),
                 N(lang, water, 3), [N(lang, w, 3) for w in (a + b, a, 2 * b if 2 * b != water else 2 * b + 1, a * b, water + 1, water / 2)], "On cherche le réactif limitant : on compare a/2 et b." if lang == "fr" else "Find the limiting reagent: compare a/2 with b.", src=CH_S(lang), cat=cat(lang, "Stœchiométrie", "Reacting masses"))


@both(CH_FR1 + CH_FRT + CH_EN_A, "ch-percent-yield", cap=600, cat="Stœchiométrie", diffs=(3, 4))
def percent_yield(rng, d, lang):
    theo, y = rng.choice([10, 20, 25, 40, 50, 80, 100]), rng.choice([40, 50, 60, 70, 75, 80, 90])
    actual = theo * y / 100
    return Draft(T(lang, f"Une réaction devrait donner {theo} g de produit ; on en obtient {N(lang, actual, 2)} g. Quel est le rendement ?", f"A reaction should give {theo} g of product but {N(lang, actual, 2)} g is obtained. What is the percentage yield?"),
                 f"{y} %", [f"{w} %" for w in (100 - y, y + 10, y - 10 if y > 20 else y + 20, int(theo / actual * 100) if actual else 99, y * 2 if y < 50 else y // 2)], "rendement = quantité obtenue ÷ quantité théorique × 100." if lang == "fr" else "yield = actual ÷ theoretical × 100.", src=CH_S(lang), cat=cat(lang, "Stœchiométrie", "Reacting masses"))


@both(CH_FR1 + CH_FRT + CH_EN_O + CH_EN_A, "ch-titration-simple", cap=600, cat="Dosages", diffs=(3, 4))
def titration_simple(rng, d, lang):
    ca, va, vb = rng.choice([0.1, 0.2, 0.5, 1]), rng.choice([10, 20, 25]), rng.choice([10, 12.5, 20, 25, 40, 50])
    cb = ca * va / vb
    return Draft(T(lang, f"On dose {va} mL d'acide chlorhydrique de concentration {N(lang, ca)} mol/L par de la soude : l'équivalence est atteinte pour {N(lang, vb)} mL de soude. Quelle est la concentration de la soude, en mol/L ?", f"{va} cm³ of hydrochloric acid of concentration {N(lang, ca)} mol/dm³ is titrated with sodium hydroxide: the end-point is reached at {N(lang, vb)} cm³. What is the concentration of the alkali, in mol/dm³?"),
                 N(lang, cb, 4), [N(lang, w, 4) for w in (ca * vb / va, ca * va * vb, ca + va / vb, cb * 2, cb / 2)], "À l'équivalence : Ca·Va = Cb·Vb (acide et base monoprotiques)." if lang == "fr" else "At the end-point: CaVa = CbVb (monoprotic acid and base).", src=CH_S(lang), cat=cat(lang, "Dosages", "Titration"))


@both(CH_FR1 + CH_FRT + CH_EN_A, "ch-average-mass", cap=600, cat="Atome", diffs=(3, 4))
def average_mass(rng, d, lang):
    iso = rng.choice([(35, 37, "chlore", "chlorine"), (10, 11, "bore", "boron"), (63, 65, "cuivre", "copper"), (6, 7, "lithium", "lithium")])
    p1 = rng.choice([25, 50, 60, 70, 75, 80, 90])
    A1, A2 = iso[0], iso[1]
    avg = (A1 * p1 + A2 * (100 - p1)) / 100
    return Draft(T(lang, f"Le {iso[2]} naturel contient {p1} % de l'isotope de nombre de masse {A1} et {100 - p1} % de celui de nombre de masse {A2}. Quelle est la masse atomique relative moyenne ?", f"Natural {iso[3]} is {p1} % of the isotope with mass number {A1} and {100 - p1} % of mass number {A2}. What is its relative atomic mass?"),
                 N(lang, avg, 2), [N(lang, w, 2) for w in ((A1 + A2) / 2 if (A1 + A2) / 2 != avg else avg + 0.5, A1 * (100 - p1) / 100 + A2 * p1 / 100 if A1 * (100 - p1) / 100 + A2 * p1 / 100 != avg else avg + 1, A1 + A2, avg + 1, avg - 1) if w != avg], "moyenne pondérée par les abondances." if lang == "fr" else "weighted mean of the isotope masses.", src=CH_S(lang), cat=cat(lang, "Atome", "Atomic structure"))


@both(CH_FR1 + CH_FRT + CH_EN_A, "ch-hess", cap=600, cat="Thermochimie", diffs=(4, 5))
def hess(rng, d, lang):
    reac, prod = rng.sample([-100, -200, -50, 0, -300, -75, -250, -150], 2)
    prod2 = rng.choice([-400, -500, -600, -350, -250])
    dH = (prod + prod2) - reac
    return Draft(T(lang, f"Enthalpies standard de formation (kJ/mol) : réactif {num(reac)} ; produits {num(prod)} et {num(prod2)} (coefficients tous égaux à 1). Quelle est l'enthalpie de réaction, en kJ ?", f"Standard enthalpies of formation (kJ/mol): reactant {num(reac)}; products {num(prod)} and {num(prod2)} (all coefficients 1). What is the enthalpy change of reaction, in kJ?"),
                 N(lang, dH), [N(lang, w) for w in (reac - (prod + prod2), prod + prod2 + reac, -dH, prod + prod2, dH + 100) if w != dH], "ΔH = Σ ΔHf(produits) − Σ ΔHf(réactifs)." if lang == "fr" else "ΔH = ΣΔHf(products) − ΣΔHf(reactants).", src=CH_S(lang), cat=cat(lang, "Thermochimie", "Energetics"))


@both(CH_FR1 + CH_FRT + CH_EN_A, "ch-reaction-rate", cap=600, cat="Cinétique", diffs=(3, 4))
def reaction_rate(rng, d, lang):
    c0, c1, t = rng.choice([(1.0, 0.6, 20), (0.8, 0.4, 10), (0.5, 0.3, 5), (2.0, 1.2, 40), (1.0, 0.25, 15), (0.6, 0.2, 8)])
    rate = (c0 - c1) / t
    return Draft(T(lang, f"La concentration d'un réactif passe de {N(lang, c0)} à {N(lang, c1)} mol/L en {t} s. Quelle est sa vitesse moyenne de disparition, en mol/(L·s) ?", f"The concentration of a reactant falls from {N(lang, c0)} to {N(lang, c1)} mol/dm³ in {t} s. What is its mean rate of disappearance, in mol/(dm³·s)?"),
                 N(lang, rate, 4), [N(lang, w, 4) for w in (c1 / t, (c0 + c1) / t, (c0 - c1) * t, rate * 10, t / (c0 - c1)) if round(w, 4) != round(rate, 4)], "v = −Δ[A]/Δt." if lang == "fr" else "rate = −Δ[A]/Δt.", src=CH_S(lang), cat=cat(lang, "Cinétique", "Kinetics"))


@both(CH_FR2 + CH_FR1, "ch-mass-percent", cap=600, cat="Quantité de matière", diffs=(3, 4))
def mass_percent(rng, d, lang):
    c = rng.choice(COMP)
    el = rng.choice(sorted(parse(c[0])))
    M = mass(c[0])
    p = AM[el] * parse(c[0])[el] / M * 100
    return Draft(T(lang, f"Quel est le pourcentage en masse de l'élément {el} dans {nm(c, lang)} ({sub(c[0])}), arrondi à 0,1 ? ({given(c[0], lang)})", f"What is the percentage by mass of {el} in {nm(c, lang)}, {sub(c[0])}, to 1 decimal place? ({given(c[0], lang)})"),
                 f"{N(lang, p, 1)} %", [f"{N(lang, w, 1)} %" for w in (100 - p, p / 2, p * 2 if p < 50 else p / 3, p + 10 if p < 90 else p - 10, AM[el] / M * 100 if parse(c[0])[el] > 1 else p + 5) if round(w, 1) != round(p, 1)],
                 "% massique = (nombre d'atomes × M de l'élément ÷ M du composé) × 100." if lang == "fr" else "% by mass = (atoms × Ar ÷ Mr) × 100.", src=CH_S(lang), cat=cat(lang, "Quantité de matière", "Mole calculations"))


@both(CH_FR2 + CH_FR1 + CH_EN_O, "ch-solution-mass", cap=600, cat="Solutions", diffs=(2, 3))
def solution_mass(rng, d, lang):
    c = rng.choice([x for x in COMP if x[0] in ("NaCl", "NaOH", "KOH", "CuSO4", "Na2SO4", "Na2CO3", "AgNO3", "MgCl2", "C6H12O6")])
    M = mass(c[0])
    C, V = rng.choice([0.1, 0.2, 0.5, 1]), rng.choice([0.1, 0.25, 0.5, 1, 2])
    m = C * V * M
    return Draft(T(lang, f"Quelle masse de {sub(c[0])}, en grammes, faut-il dissoudre pour préparer {N(lang, V)} L de solution à {N(lang, C)} mol/L ? ({given(c[0], lang)})", f"What mass of {sub(c[0])}, in grams, must be dissolved to make {N(lang, V)} dm³ of a {N(lang, C)} mol/dm³ solution? ({given(c[0], lang)})"),
                 f"{N(lang, m, 2)} g", [f"{N(lang, w, 2)} g" for w in (C * V / M, m * 10, m / 10, C * M, V * M, m * 2) if round(w, 2) != round(m, 2)], "m = C × V × M." , src=CH_S(lang), cat=cat(lang, "Solutions", "Solutions"))


# ================================================================================================ informatique
@both(INF_FR + INF_EN, "inf-binary-logic-ops", cap=600, cat="Représentation des nombres", diffs=(2, 3, 4))
def binary_logic_ops(rng, d, lang):
    a, b = rng.randint(1, 255), rng.randint(1, 255)
    op = rng.choice(["AND", "OR", "XOR"])
    r = {"AND": a & b, "OR": a | b, "XOR": a ^ b}[op]
    others = [x for x in ({"AND": a | b, "OR": a & b, "XOR": a | b}[op], a ^ b if op != "XOR" else a & b, (a + b) % 256, 255 - r, r ^ 1, r + 1) if x != r and 0 <= x <= 255]
    fmt = lambda x: format(x, "08b")
    return Draft(T(lang, f"Quel est le résultat de {fmt(a)} {op} {fmt(b)} (opération bit à bit sur 8 bits) ?", f"What is {fmt(a)} {op} {fmt(b)} (bitwise on 8 bits)?"), fmt(r), [fmt(x) for x in others],
                 T(lang, "On applique l'opération bit par bit.", "Apply the operation to each pair of bits."), src=INF_S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))


@both(INF_FR + INF_EN, "inf-storage-count", cap=600, cat="Représentation des données", diffs=(2, 3))
def storage_count(rng, d, lang):
    cap, size = rng.choice([(4, 2), (8, 4), (16, 2), (32, 8), (64, 4), (128, 16), (1, 0.5), (2, 0.25)]), None
    gb, mb = cap
    n = gb * 1024 / mb
    return Draft(T(lang, f"Combien de fichiers de {N(lang, mb)} Mo peut-on stocker sur une clé de {gb} Go ? (1 Go = 1 024 Mo)", f"How many {N(lang, mb)} MB files fit on a {gb} GB flash drive? (1 GB = 1024 MB)"),
                 str(int(n)), [str(int(w)) for w in (gb * 1000 / mb, n * 8, n / 8, gb * mb, n + 1024 / mb) if int(w) != int(n)], "capacité ÷ taille d'un fichier." if lang == "fr" else "capacity ÷ file size.", src=INF_S(lang), cat=cat(lang, "Représentation des données", "Data representation"))


@both(INF_FR + INF_EN, "inf-two-complement-value", cap=600, cat="Représentation des nombres", diffs=(4, 5))
def twos_value(rng, d, lang):
    n = rng.randint(1, 127)
    bits = format((1 << 8) - n, "08b")
    return Draft(T(lang, f"Quelle valeur décimale représente {bits} en complément à deux sur 8 bits ?", f"What denary value does {bits} represent in 8-bit two's complement?"), f"−{n}", [f"{n}", f"−{n + 1}", f"−{n - 1}" if n > 1 else "−2", f"{256 - n}", f"−{128 - n}" if 128 - n != n else "−100"],
                 T(lang, "Bit de poids fort à 1 : on retranche 256.", "The top bit is 1, so subtract 256."), src=INF_S(lang), cat=cat(lang, "Représentation des nombres", "Data representation"))


@both(INF_FR + INF_EN, "inf-subnet-address", cap=600, cat="Réseaux", diffs=(4, 5))
def subnet_address(rng, d, lang):
    p = rng.choice([24, 25, 26, 27, 28])
    last = rng.randint(1, 254)
    block = 2 ** (32 - p) if p >= 24 else 256
    net = (last // block) * block
    ip = f"192.168.1.{last}"
    return Draft(T(lang, f"Quelle est l'adresse réseau de la machine {ip}/{p} ?", f"What is the network address of the host {ip}/{p}?"), f"192.168.1.{net}", [f"192.168.1.{w}" for w in (net + block if net + block < 256 else net - block if net - block >= 0 else net + 1, last, 0 if net != 0 else 1, net + 1, (last // (block * 2)) * block * 2 if (last // (block * 2)) * block * 2 != net else net + 2) if w != net and 0 <= w <= 255],
                 T(lang, "On met à zéro les bits de la partie machine.", "Set the host bits to zero."), src=INF_S(lang), cat=cat(lang, "Réseaux", "Networks"))


@both(INF_FR + INF_EN, "inf-list-index", cap=600, cat="Programmation", diffs=(2, 3))
def list_index(rng, d, lang):
    lst = [rng.randint(1, 20) for _ in range(rng.randint(5, 8))]
    i = rng.randint(0, len(lst) - 1)
    kind = rng.choice(["idx", "len", "slice", "sum"])
    if kind == "idx":
        e, v = f"L[{i}]", lst[i]
    elif kind == "len":
        e, v = "len(L)", len(lst)
    elif kind == "slice":
        a, b = rng.randint(0, 2), rng.randint(3, len(lst))
        e, v = f"L[{a}:{b}]", lst[a:b]
    else:
        e, v = "sum(L)", sum(lst)
    assert eval(e, {"__builtins__": {"len": len, "sum": sum}}, {"L": lst}) == v
    s = str(v)
    wr = [str(x) for x in ((lst[i + 1] if i + 1 < len(lst) else lst[i - 1]) if kind == "idx" else None, v + 1 if isinstance(v, int) else None) if x is not None]
    if isinstance(v, list):
        wr = [str(lst[a - 1:b]) if a > 0 else str(lst[a + 1:b]), str(lst[a:b + 1]), str(lst[a:b - 1]), str(lst[a + 1:b + 1])]
    else:
        wr += [str(v - 1), str(v * 2), str(max(lst))]
    return Draft(T(lang, f"En Python, avec L = {lst}, que vaut {e} ?", f"In Python, with L = {lst}, what is {e}?"), s, [w for w in wr if w != s], T(lang, "Les indices commencent à 0 ; la tranche s'arrête avant l'indice de fin.", "Indices start at 0; a slice stops before its end index."), src=INF_S(lang), cat=cat(lang, "Programmation", "Programming"))


# ================================================================================================ économie
@both(EC + EE, "eco-vat", cap=600, cat="Fiscalité", diffs=(2, 3))
def vat(rng, d, lang):
    ht = rng.choice([1000, 2000, 4000, 5000, 8000, 10000, 20000, 40000, 100000])
    ttc = ht * 19.25 / 100 + ht
    return Draft(T(lang, f"Un article coûte {ht} FCFA hors taxes. La TVA est de 19,25 %. Quel est le prix toutes taxes comprises, en FCFA ?", f"An article costs {ht} FCFA before tax. VAT is 19.25 %. What is the price including VAT, in FCFA?"),
                 N(lang, ttc, 2), [N(lang, w, 2) for w in (ht * 1.1925 + 1000, ht * 19.25 / 100, ht + 19.25, ht * 1.925, ht / 1.1925)], "TTC = HT × 1,1925." if lang == "fr" else "Price incl. VAT = price × 1.1925.", src=SRC[lang], cat=LC(lang, "Fiscalité", "Taxation"))


@both(EC + EE, "eco-average-cost", cap=600, cat="Entreprise", diffs=(2, 3))
def average_cost(rng, d, lang):
    q, tc = rng.choice([10, 20, 50, 100, 200, 500]), None
    fc, vc = rng.choice([1000, 5000, 10000, 20000]), rng.choice([5, 10, 20, 40, 100])
    tc = fc + vc * q
    ac = tc / q
    return Draft(T(lang, f"Une entreprise produit {q} unités avec des coûts fixes de {fc} FCFA et un coût variable de {vc} FCFA par unité. Quel est son coût moyen par unité, en FCFA ?", f"A firm makes {q} units with fixed costs of {fc} FCFA and a variable cost of {vc} FCFA per unit. What is its average cost per unit, in FCFA?"),
                 N(lang, ac, 2), [N(lang, w, 2) for w in (vc, fc / q, tc, ac + vc, ac * 2) if round(w, 2) != round(ac, 2)], "coût moyen = coût total ÷ quantité." if lang == "fr" else "average cost = total cost ÷ output.", src=SRC[lang], cat=LC(lang, "Entreprise", "The firm"))


@both(EC + EE, "eco-marginal-cost", cap=600, cat="Entreprise", diffs=(3, 4))
def marginal_cost(rng, d, lang):
    q1, q2 = rng.choice([(10, 11), (100, 110), (50, 60), (200, 250), (20, 30)])
    tc1 = rng.choice([5000, 8000, 10000, 20000])
    mc = rng.choice([20, 25, 40, 50, 80])
    tc2 = tc1 + mc * (q2 - q1)
    return Draft(T(lang, f"Le coût total passe de {tc1} FCFA pour {q1} unités à {tc2} FCFA pour {q2} unités. Quel est le coût marginal par unité supplémentaire, en FCFA ?", f"Total cost rises from {tc1} FCFA for {q1} units to {tc2} FCFA for {q2} units. What is the marginal cost per extra unit, in FCFA?"),
                 str(mc), [str(w) for w in (tc2 // q2, tc1 // q1, tc2 - tc1, mc + 10, mc * 2) if w != mc], "coût marginal = ΔCT / ΔQ." if lang == "fr" else "marginal cost = ΔTC / ΔQ.", src=SRC[lang], cat=LC(lang, "Entreprise", "The firm"))


@both(EC + EE, "eco-index-number", cap=600, cat="Prix et inflation", diffs=(2, 3))
def index_number(rng, d, lang):
    base, new = rng.choice([200, 250, 400, 500, 800, 1000, 1500]), None
    pct_ = rng.choice([-20, -10, 5, 10, 20, 25, 50])
    new = base * (100 + pct_) // 100
    idx = new / base * 100
    return Draft(T(lang, f"Le prix d'un produit passe de {base} FCFA (année de base) à {new} FCFA. Quel est l'indice des prix de la nouvelle année (base 100) ?", f"The price of a product rises from {base} FCFA (base year) to {new} FCFA. What is the price index of the new year (base year = 100)?"),
                 N(lang, idx, 1), [N(lang, w, 1) for w in (base / new * 100, new - base, idx - 100, idx + 10, 100 + (new - base) / 10) if round(w, 1) != round(idx, 1)], "indice = prix de l'année ÷ prix de base × 100." if lang == "fr" else "index = price ÷ base price × 100.", src=SRC[lang], cat=LC(lang, "Prix et inflation", "Inflation"))


@both(EC + EE, "eco-savings-rate", cap=600, cat="Macroéconomie", diffs=(2, 3))
def savings_rate(rng, d, lang):
    inc, sav = rng.choice([100000, 150000, 200000, 250000, 300000, 500000]), rng.choice([5, 8, 10, 15, 20, 25])
    s = inc * sav // 100
    return Draft(T(lang, f"Un ménage dont le revenu est de {inc} FCFA épargne {s} FCFA. Quel est son taux d'épargne, en pourcentage ?", f"A household with an income of {inc} FCFA saves {s} FCFA. What is its savings rate, in per cent?"),
                 f"{sav} %", [f"{w} %" for w in (100 - sav, sav * 2, sav + 5, sav // 2 if sav > 10 else sav + 10, round(inc / s))], "taux d'épargne = épargne ÷ revenu × 100." if lang == "fr" else "savings rate = savings ÷ income × 100.", src=SRC[lang], cat=LC(lang, "Macroéconomie", "Macroeconomics"))


@both(EC + EE, "eco-simple-interest", cap=600, cat="Monnaie", diffs=(2, 3))
def simple_interest(rng, d, lang):
    cap, r, n = rng.choice([50000, 100000, 200000, 500000, 1000000]), rng.choice([2, 3, 4, 5, 6, 8]), rng.choice([1, 2, 3, 5])
    i = cap * r * n // 100
    return Draft(T(lang, f"On place {cap} FCFA à intérêts simples au taux annuel de {r} % pendant {n} an{'s' if n > 1 else ''}. Quel est le montant des intérêts, en FCFA ?", f"{cap} FCFA is invested at {r} % simple interest per year for {n} year{'s' if n > 1 else ''}. How much interest is earned, in FCFA?"),
                 str(i), [str(w) for w in (cap + i, cap * r // 100, i * 2, i // n if n > 1 else i * 10, cap * ((1 + r / 100) ** n - 1) if n > 1 else i * 3) if int(w) != i], "I = C × t × n ÷ 100." if lang == "fr" else "I = P × r × n ÷ 100.", src=SRC[lang], cat=LC(lang, "Monnaie", "Money"))


# ================================================================================================ géographie
@both(GF + GE, "geo-lapse-rate", cap=600, cat="Climats", diffs=(3, 4))
def lapse_rate(rng, d, lang):
    t0, h = rng.choice([20, 22, 25, 28, 30, 32]), rng.choice([500, 1000, 1500, 2000, 2500, 3000, 4000])
    t = t0 - 0.6 * h / 100
    return Draft(T(lang, f"La température au niveau de la mer est de {t0} °C. En moyenne, elle baisse de 0,6 °C tous les 100 m. Quelle est la température à {h} m d'altitude, en °C ?", f"The temperature at sea level is {t0} °C and falls by 0.6 °C for every 100 m of height. What is the temperature at {h} m, in °C?"),
                 N(lang, t, 1), [N(lang, w, 1) for w in (t0 - 6 * h / 1000 * 10, t0 - 0.6 * h, t0 + 0.6 * h / 100, t0 - h / 100, t - 3) if round(w, 1) != round(t, 1)], "baisse = 0,6 × altitude ÷ 100." if lang == "fr" else "fall = 0.6 × height ÷ 100.", src=SRC[lang], cat=LC(lang, "Climats", "Climate"))


@both(GF + GE, "geo-population-change", cap=600, cat="Population", diffs=(2, 3))
def population_change(rng, d, lang):
    p0, rate, n = rng.choice([1000000, 2000000, 5000000, 10000000, 20000000]), rng.choice([1, 2, 2.5, 3]), rng.choice([1, 2, 3, 5, 10])
    p = p0 * (1 + rate / 100) ** n
    return Draft(T(lang, f"Une population de {p0} habitants croît de {N(lang, rate)} % par an. Combien d'habitants (arrondi à l'unité) y aura-t-il dans {n} an{'s' if n > 1 else ''} ?", f"A population of {p0} grows by {N(lang, rate)} % a year. What will it be in {n} year{'s' if n > 1 else ''} (to the nearest whole number)?"),
                 str(round(p)), [str(round(w)) for w in (p0 * (1 + rate * n / 100), p0 + p0 * rate / 100, p * 1.1, p0 * (1 + rate / 100) ** (n + 1), p0 * rate * n / 100) if round(w) != round(p)], "croissance composée : P × (1 + t)ⁿ." if lang == "fr" else "compound growth: P × (1 + r)ⁿ.", src=SRC[lang], cat=LC(lang, "Population", "Population"))


@both(GF + GE, "geo-literacy", cap=600, cat="Population", diffs=(2, 3))
def literacy(rng, d, lang):
    pop, lit = rng.choice([(2000000, 1500000), (4000000, 3000000), (500000, 350000), (10000000, 7000000), (1000000, 800000)])
    pc = lit / pop * 100
    return Draft(T(lang, f"Sur {pop} adultes, {lit} savent lire et écrire. Quel est le taux d'alphabétisation, en pourcentage ?", f"Out of {pop} adults, {lit} can read and write. What is the literacy rate, in per cent?"),
                 pct(lang, pc, 1), [pct(lang, w, 1) for w in (100 - pc, pc / 2, pop / lit, pc + 10, lit / 10000) if round(w, 1) != round(pc, 1)], "taux = personnes alphabétisées ÷ population adulte × 100." if lang == "fr" else "rate = literate adults ÷ adult population × 100.", src=SRC[lang], cat=LC(lang, "Population", "Population"))


@both(GF + GE, "geo-latitude-distance", cap=600, cat="Cartographie", diffs=(3, 4))
def latitude_distance(rng, d, lang):
    l1, l2 = rng.choice([(0, 5), (2, 10), (4, 13), (10, 12), (3, 6), (0, 10), (5, 12)])
    km = (l2 - l1) * 111
    return Draft(T(lang, f"Deux villes situées sur le même méridien ont pour latitudes {l1}° et {l2}° Nord. Quelle est la distance qui les sépare, en km ? (1° de latitude ≈ 111 km)", f"Two cities on the same meridian lie at latitudes {l1}° N and {l2}° N. What is the distance between them, in km? (1° of latitude ≈ 111 km)"),
                 str(km), [str(w) for w in (km + 111, km - 111 if km > 111 else km + 222, (l2 + l1) * 111, km * 2, (l2 - l1) * 60) if w != km], "distance ≈ écart de latitude × 111 km." if lang == "fr" else "distance ≈ latitude difference × 111 km.", src=SRC[lang], cat=LC(lang, "Cartographie", "Map skills"))


# ================================================================================================ SVT / Biology
@both(SV + BI, "bio-pyramid-biomass", cap=600, cat="Écologie", diffs=(3, 4))
def pyramid_biomass(rng, d, lang):
    producers, eff = rng.choice([10000, 20000, 5000, 8000]), rng.choice([10, 10, 20])
    level = rng.choice([2, 3])
    b = producers * (eff / 100) ** (level - 1)
    return Draft(T(lang, f"La biomasse des producteurs est de {producers} kg. Si {eff} % de la biomasse passe à chaque niveau supérieur, quelle est la biomasse du niveau {level} (producteurs = niveau 1), en kg ?", f"The biomass of the producers is {producers} kg. If {eff} % of the biomass passes to each higher level, what is the biomass at level {level} (producers = level 1), in kg?"),
                 N(lang, b, 2), [N(lang, w, 2) for w in (producers * eff / 100 * level, producers * (eff / 100) ** level, producers - b, producers * eff / 10, b * 10) if round(w, 2) != round(b, 2)], "on multiplie par le rendement à chaque niveau." if lang == "fr" else "multiply by the transfer efficiency at each level.", src=SRC[lang], cat=LC(lang, "Écologie", "Ecology"))


@both(SV + BI, "bio-heart-rate", cap=600, cat="Physiologie", diffs=(2, 3))
def heart_rate(rng, d, lang):
    beats, secs = rng.choice([(20, 15), (25, 20), (10, 10), (30, 20), (18, 15), (40, 30)])
    bpm = beats * 60 / secs
    return Draft(T(lang, f"On compte {beats} battements du cœur en {secs} s. Quelle est la fréquence cardiaque, en battements par minute ?", f"{beats} heartbeats are counted in {secs} seconds. What is the heart rate, in beats per minute?"),
                 N(lang, bpm, 1), [N(lang, w, 1) for w in (beats * secs, beats / secs, beats + 60 - secs, bpm * 2, bpm / 2) if round(w, 1) != round(bpm, 1)], "fréquence = battements × 60 ÷ durée en secondes." if lang == "fr" else "rate = beats × 60 ÷ time in seconds.", src=SRC[lang], cat=LC(lang, "Physiologie", "Physiology"))


@both(SV + BI, "bio-bmi", cap=600, cat="Physiologie", diffs=(3, 4))
def bmi(rng, d, lang):
    h, m = rng.choice([1.5, 1.6, 1.7, 1.8, 2.0]), rng.choice([45, 50, 60, 64, 72, 80, 90])
    imc = m / (h * h)
    return Draft(T(lang, f"Une personne mesure {N(lang, h)} m et pèse {m} kg. Quel est son indice de masse corporelle, arrondi à l'unité (kg/m²) ?", f"A person is {N(lang, h)} m tall and has a mass of {m} kg. What is the body mass index, to the nearest whole number (kg/m²)?"),
                 str(round(imc)), [str(round(w)) for w in (m / h, m * h, m / (h * h) * 10, imc + 5, imc - 4) if round(w) != round(imc)], "IMC = masse ÷ taille²." if lang == "fr" else "BMI = mass ÷ height².", src=SRC[lang], cat=LC(lang, "Physiologie", "Physiology"))


@both(SV + BI, "bio-genetic-probability", cap=600, cat="Génétique", diffs=(3, 4))
def genetic_probability(rng, d, lang):
    n, k = rng.choice([(2, 2), (3, 3), (3, 2), (4, 2), (2, 1), (3, 1)])
    # n children of two heterozygous parents: P(all aa recessive k times) → P(exactly k of n are aa) with p = 1/4
    p = Fraction(1, 4)
    prob = math.comb(n, k) * p ** k * (1 - p) ** (n - k)
    return Draft(T(lang, f"Deux parents hétérozygotes Aa ont {n} enfants. Quelle est la probabilité qu'exactement {k} d'entre eux soient aa (maladie récessive) ?", f"Two heterozygous parents (Aa) have {n} children. What is the probability that exactly {k} of them are aa (recessive condition)?"),
                 num(prob), [num(w) for w in (p ** k, math.comb(n, k) * p ** k, math.comb(n, k) * Fraction(1, 2) ** n, 1 - prob, Fraction(k, n)) if w != prob], "chaque enfant est aa avec la probabilité 1/4 : loi binomiale." if lang == "fr" else "each child is aa with probability 1/4: binomial distribution.", src=SRC[lang], cat=LC(lang, "Génétique", "Genetics"))
