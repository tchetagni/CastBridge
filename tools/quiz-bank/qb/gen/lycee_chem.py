"""Chimie du second cycle francophone (2nde, 1re, Tle) et GCE Chemistry (Ordinary, Advanced Level), en deux langues.
Les masses molaires viennent d'un parseur de formules, l'équilibrage est vérifié atome par atome, le pH par calcul logarithmique."""
import math
import re
from fractions import Fraction

from .. import lycee
from ..core import MINUS, Draft
from .lycee_common import N, T, both
from .mathfmt import num, sup

F2, F1, FT = lycee.fr("2nde", "chim"), lycee.fr("1re", "chim"), lycee.fr("tle", "chim")
O5, A6, A12 = lycee.en("f5", "chem"), lycee.en("l6", "chem"), lycee.en("u6", "chem")
SRC_FR = "Programme MINESEC de chimie - réponse calculée (masses molaires données dans l'énoncé)"
SRC_EN = "GCE Chemistry syllabus topic - answer computed (relative atomic masses given in the question)"
FR2, FR1, FRT = [(F2, "fr")], [(F1, "fr")], [(FT, "fr")]
EN_O, EN_A = [(O5, "en")], [(A6, "en"), (A12, "en")]

AM = {"H": 1, "C": 12, "N": 14, "O": 16, "Na": 23, "Mg": 24, "Al": 27, "Si": 28, "P": 31, "S": 32, "Cl": 35.5, "K": 39, "Ca": 40, "Mn": 55, "Fe": 56, "Cu": 63.5, "Zn": 65, "Br": 80, "Ag": 108}
# (formula, French name, English name) — all common school compounds
COMP = [("H2O", "l'eau", "water"), ("CO2", "le dioxyde de carbone", "carbon dioxide"), ("NaCl", "le chlorure de sodium", "sodium chloride"), ("CaCO3", "le carbonate de calcium", "calcium carbonate"),
        ("H2SO4", "l'acide sulfurique", "sulfuric acid"), ("NaOH", "l'hydroxyde de sodium", "sodium hydroxide"), ("NH3", "l'ammoniac", "ammonia"), ("CH4", "le méthane", "methane"),
        ("HCl", "le chlorure d'hydrogène", "hydrogen chloride"), ("C6H12O6", "le glucose", "glucose"), ("KOH", "l'hydroxyde de potassium", "potassium hydroxide"),
        ("Ca(OH)2", "l'hydroxyde de calcium", "calcium hydroxide"), ("MgO", "l'oxyde de magnésium", "magnesium oxide"), ("HNO3", "l'acide nitrique", "nitric acid"),
        ("Na2CO3", "le carbonate de sodium", "sodium carbonate"), ("Fe2O3", "l'oxyde de fer(III)", "iron(III) oxide"), ("Al2O3", "l'oxyde d'aluminium", "aluminium oxide"),
        ("C2H5OH", "l'éthanol", "ethanol"), ("C3H8", "le propane", "propane"), ("KMnO4", "le permanganate de potassium", "potassium permanganate"), ("NaHCO3", "l'hydrogénocarbonate de sodium", "sodium hydrogencarbonate"),
        ("Mg(OH)2", "l'hydroxyde de magnésium", "magnesium hydroxide"), ("H3PO4", "l'acide phosphorique", "phosphoric acid"), ("CaO", "l'oxyde de calcium", "calcium oxide"),
        ("ZnO", "l'oxyde de zinc", "zinc oxide"), ("SO2", "le dioxyde de soufre", "sulfur dioxide"), ("CuO", "l'oxyde de cuivre(II)", "copper(II) oxide"),
        ("CuSO4", "le sulfate de cuivre(II)", "copper(II) sulfate"), ("C4H10", "le butane", "butane"), ("Na2SO4", "le sulfate de sodium", "sodium sulfate"),
        ("AgNO3", "le nitrate d'argent", "silver nitrate"), ("CH3COOH", "l'acide éthanoïque", "ethanoic acid"), ("MgCl2", "le chlorure de magnésium", "magnesium chloride")]
TOK = re.compile(r"([A-Z][a-z]?)(\d*)|\(|\)(\d*)")


def parse(formula):
    """Atom counts of a formula such as Ca(OH)2 or CH3COOH."""
    stack = [{}]
    for m in TOK.finditer(formula):
        t = m.group(0)
        if t == "(":
            stack.append({})
        elif t.startswith(")"):
            grp = stack.pop()
            k = int(m.group(3) or 1)
            for a, n in grp.items():
                stack[-1][a] = stack[-1].get(a, 0) + n * k
        else:
            stack[-1][m.group(1)] = stack[-1].get(m.group(1), 0) + int(m.group(2) or 1)
    return stack[0]


def mass(formula):
    return sum(AM[a] * n for a, n in parse(formula).items())


def sub(formula):
    return re.sub(r"(\d+)", lambda m: "₀₁₂₃₄₅₆₇₈₉"[int(m.group(1))] if len(m.group(1)) == 1 else "".join("₀₁₂₃₄₅₆₇₈₉"[int(c)] for c in m.group(1)), formula)


def given(formula, lang):
    els = sorted(set(parse(formula)) if isinstance(formula, str) else {e for f in formula for e in parse(f)}, key=lambda e: ("CHO".find(e) if e in "CHO" else 9, e))
    sep = " ; "
    return T(lang, "masses molaires atomiques : ", "relative atomic masses: ") + sep.join("%s = %s" % (e, N(lang, AM[e])) for e in els)


def nm(c, lang):
    return c[1] if lang == "fr" else c[2]


def de(c):
    """French « de + nom » with the elision: de l'eau, du méthane, de la ..."""
    n = c[1]
    if n.startswith("l'"):
        return "de " + n
    if n.startswith("le "):
        return "du " + n[3:]
    return "de " + n


def cat(lang, a, b):
    return a if lang == "fr" else b


def S(lang):
    return SRC_FR if lang == "fr" else SRC_EN


# ---------------------------------------------------------------------------------------------- structure de l'atome
ELEMENTS = [("H", "l'hydrogène", "hydrogen", 1, 1), ("He", "l'hélium", "helium", 2, 4), ("Li", "le lithium", "lithium", 3, 7), ("Be", "le béryllium", "beryllium", 4, 9),
            ("B", "le bore", "boron", 5, 11), ("C", "le carbone", "carbon", 6, 12), ("N", "l'azote", "nitrogen", 7, 14), ("O", "l'oxygène", "oxygen", 8, 16), ("F", "le fluor", "fluorine", 9, 19),
            ("Ne", "le néon", "neon", 10, 20), ("Na", "le sodium", "sodium", 11, 23), ("Mg", "le magnésium", "magnesium", 12, 24), ("Al", "l'aluminium", "aluminium", 13, 27),
            ("Si", "le silicium", "silicon", 14, 28), ("P", "le phosphore", "phosphorus", 15, 31), ("S", "le soufre", "sulfur", 16, 32), ("Cl", "le chlore", "chlorine", 17, 35),
            ("Ar", "l'argon", "argon", 18, 40), ("K", "le potassium", "potassium", 19, 39), ("Ca", "le calcium", "calcium", 20, 40)]
SMALL = [e for e in ELEMENTS if e[3] <= 18]


def shells(z):
    out, left = [], z
    for cap in (2, 8, 8, 18):
        k = min(cap, left)
        out.append(k)
        left -= k
        if left == 0:
            break
    return out


@both(FR2 + EN_O, "ch-neutrons", cap=100, cat="Atome")
def neutrons(rng, d, lang):
    sym, nf, ne, z, a = rng.choice(ELEMENTS)
    ask = rng.choice(["n", "e", "p"])
    nn = a - z
    if ask == "n":
        right, what = nn, T(lang, "de neutrons contient le noyau", "neutrons does the nucleus contain")
    elif ask == "p":
        right, what = z, T(lang, "de protons contient le noyau", "protons does the nucleus contain")
    else:
        right, what = z, T(lang, "d'électrons possède l'atome neutre", "electrons does the neutral atom have")
    wr = [a, a + z, nn + 1, z + 1, a - 1, z - 1, nn - 1, 2 * z]
    return Draft(T(lang, f"Un atome ({sym}) a pour nombre de masse A = {a} et pour numéro atomique Z = {z}. Combien {what} ?",
                   f"An atom ({sym}) has mass number A = {a} and atomic number Z = {z}. How many {what}?"),
                 str(right), [str(w) for w in wr if w >= 0], T(lang, "Z = nombre de protons = nombre d'électrons de l'atome neutre ; A − Z = nombre de neutrons.", "Z = number of protons = number of electrons in the neutral atom; neutrons = A − Z."),
                 src=S(lang), cat=cat(lang, "Atome · 2nde", "Atomic structure"))


@both(FR2 + EN_O, "ch-electron-shells", cap=100, cat="Atome")
def electron_shells(rng, d, lang):
    sym, nf, ne, z, a = rng.choice(SMALL)
    sh = shells(z)
    letters = "KLMN"

    def alt(x):
        return "".join(f"({letters[i]}){k}" for i, k in enumerate(x))
    wr = []
    if z > 2:
        wr.append(alt([2, z - 2]) if z - 2 > 0 else alt([z]))
    if z > 10:
        wr.append(alt([2, 8, z - 11, 1]) if z - 11 > 0 else alt([2, 8, z - 10]))
        wr.append(alt([2, 6, z - 8]))
    wr += [alt([x for x in (z - 1, 1) if x]), alt([x for x in (z - 3, 3) if x]), alt([2, 8 if z > 10 else max(1, z - 3), 2])]
    right_alt = alt(sh)
    return Draft(T(lang, f"Quelle est la structure électronique de l'atome de numéro atomique Z = {z} ({sym}) ?", f"What is the electronic structure of the atom with atomic number Z = {z} ({sym})?"),
                 right_alt, [w for w in wr if w != right_alt], T(lang, "On remplit les couches dans l'ordre K (2 électrons max), L (8), M (8 pour Z ≤ 18).", "Fill the shells in order: K holds 2, L holds 8, M holds 8 (for Z ≤ 18)."),
                 src=S(lang), cat=cat(lang, "Atome · 2nde", "Atomic structure"))


@both(FR2 + EN_O, "ch-valence", cap=60, cat="Atome")
def valence(rng, d, lang):
    sym, nf, ne, z, a = rng.choice([e for e in SMALL if e[3] > 2])
    v = shells(z)[-1]
    wr = [v + 1, v - 1 if v > 1 else v + 2, 8 - v if 8 - v != v else v + 3, z, 2]
    return Draft(T(lang, f"Combien d'électrons de valence possède l'atome de numéro atomique Z = {z} ({sym}) ?", f"How many outer-shell (valence) electrons does the atom with atomic number Z = {z} ({sym}) have?"),
                 str(v), [str(w) for w in wr], T(lang, "Ce sont les électrons de la dernière couche occupée.", "They are the electrons in the outermost occupied shell."), src=S(lang), cat=cat(lang, "Atome · 2nde", "Atomic structure"))


ION = [("Na", 11, 1), ("Mg", 12, 2), ("Al", 13, 3), ("K", 19, 1), ("Ca", 20, 2), ("Cl", 17, -1), ("O", 8, -2), ("F", 9, -1), ("S", 16, -2), ("Li", 3, 1), ("N", 7, -3), ("Be", 4, 2)]


@both(FR2 + EN_O, "ch-ion-electrons", cap=60, cat="Atome")
def ion_electrons(rng, d, lang):
    sym, z, q = rng.choice(ION)
    n = z - q
    chg = (str(abs(q)) if abs(q) > 1 else "") + ("+" if q > 0 else "−")
    ion = sym + "".join({"0": "⁰", "1": "¹", "2": "²", "3": "³"}.get(c, c) for c in chg[:-1]) + ("⁺" if q > 0 else "⁻")
    wr = [z, z + q, n + 1, n - 1, z + abs(q) + 1]
    return Draft(T(lang, f"L'atome {sym} a pour numéro atomique Z = {z}. Combien d'électrons possède l'ion {ion} ?", f"The atom {sym} has atomic number Z = {z}. How many electrons does the ion {ion} have?"),
                 str(n), [str(w) for w in wr if w >= 0], T(lang, "Un cation a perdu des électrons, un anion en a gagné : n = Z − charge.", "A cation has lost electrons, an anion has gained them: electrons = Z − charge."), src=S(lang), cat=cat(lang, "Atome · 2nde", "Atomic structure"))


# ---------------------------------------------------------------------------------------------- quantité de matière
@both(FR2 + FR1 + EN_O + EN_A, "ch-molar-mass", cap=200, cat="Quantité de matière")
def molar_mass(rng, d, lang):
    c = rng.choice(COMP)
    M = mass(c[0])
    wr = [M + 1, M - 1, M * 2, M / 2, M + 16, M - 2]
    return Draft(T(lang, f"Quelle est la masse molaire {de(c)} ({sub(c[0])}), en g/mol ? ({given(c[0], lang)})", f"What is the relative formula mass (molar mass, g/mol) of {nm(c, lang)}, {sub(c[0])}? ({given(c[0], lang)})"),
                 N(lang, M, 1), [N(lang, w, 1) for w in wr], T(lang, "On additionne les masses molaires atomiques de tous les atomes de la formule.", "Add the relative atomic masses of every atom in the formula."), src=S(lang), cat=cat(lang, "Quantité de matière", "Mole calculations"))


@both(FR2 + FR1 + EN_O + EN_A, "ch-mole-from-mass", cap=240, cat="Quantité de matière")
def mole_from_mass(rng, d, lang):
    c = rng.choice(COMP)
    M = mass(c[0])
    n = rng.choice([0.1, 0.2, 0.25, 0.5, 1, 2, 3, 4, 5])
    m = M * n
    if rng.random() < 0.5:
        return Draft(T(lang, f"Combien de moles contiennent {N(lang, m, 2)} g de {sub(c[0])} ? ({given(c[0], lang)})", f"How many moles are there in {N(lang, m, 2)} g of {sub(c[0])}? ({given(c[0], lang)})"),
                     N(lang, n, 3), [N(lang, w, 3) for w in (n * 2, n / 2, m * M, M / m, n + 1, m / 100)], "n = m/M.", src=S(lang), cat=cat(lang, "Quantité de matière", "Mole calculations"))
    return Draft(T(lang, f"Quelle est la masse de {N(lang, n, 3)} mol de {sub(c[0])}, en grammes ? ({given(c[0], lang)})",
                   f"What is the mass of {N(lang, n, 3)} mol of {sub(c[0])}, in grams? ({given(c[0], lang)})"),
                 N(lang, m, 2), [N(lang, w, 2) for w in (n / M, M / n, m * 2, m / 2, m + M, M)], "m = n × M.", src=S(lang), cat=cat(lang, "Quantité de matière", "Mole calculations"))


@both(FR2 + FR1 + EN_O + EN_A, "ch-concentration", cap=240, cat="Solutions")
def concentration(rng, d, lang):
    n, V = rng.choice([0.1, 0.2, 0.25, 0.5, 1, 2, 0.05]), rng.choice([0.1, 0.2, 0.25, 0.5, 1, 2, 5])
    C = n / V
    k = rng.choice(["C", "n", "V"])
    if k == "C":
        return Draft(T(lang, f"On dissout {N(lang, n, 3)} mol de soluté dans {N(lang, V, 3)} L de solution. Quelle est la concentration molaire, en mol/L ?", f"{N(lang, n, 3)} mol of solute is dissolved to make {N(lang, V, 3)} dm³ of solution. What is the concentration, in mol/dm³?"),
                     N(lang, C, 3), [N(lang, w, 3) for w in (n * V, V / n, C * 10, C / 10, n + V)], "C = n/V.", src=S(lang), cat=cat(lang, "Solutions", "Solutions"))
    if k == "n":
        return Draft(T(lang, f"Quelle quantité de matière de soluté contiennent {N(lang, V, 3)} L d'une solution de concentration {N(lang, C, 3)} mol/L ?", f"How many moles of solute are in {N(lang, V, 3)} dm³ of a solution of concentration {N(lang, C, 3)} mol/dm³?"),
                     N(lang, n, 3), [N(lang, w, 3) for w in (C / V, V / C, n * 10, n / 10, C + V)], "n = C × V.", src=S(lang), cat=cat(lang, "Solutions", "Solutions"))
    return Draft(T(lang, f"Quel volume de solution à {N(lang, C, 3)} mol/L contient {N(lang, n, 3)} mol de soluté, en litres ?", f"What volume of a {N(lang, C, 3)} mol/dm³ solution contains {N(lang, n, 3)} mol of solute, in dm³?"),
                 N(lang, V, 3), [N(lang, w, 3) for w in (n * C, C / n, V * 10, V / 10, n + C)], "V = n/C.", src=S(lang), cat=cat(lang, "Solutions", "Solutions"))


@both(FR2 + FR1 + EN_O + EN_A, "ch-dilution", cap=200, cat="Solutions")
def dilution(rng, d, lang):
    C1, V1, f = rng.choice([1, 2, 0.5, 5, 10, 0.2]), rng.choice([10, 20, 25, 50, 100]), rng.choice([2, 4, 5, 10, 20])
    V2 = V1 * f
    C2 = C1 / f
    if rng.random() < 0.5:
        return Draft(T(lang, f"On dilue {V1} mL d'une solution à {N(lang, C1)} mol/L avec de l'eau jusqu'à {V2} mL. Quelle est la nouvelle concentration, en mol/L ?", f"{V1} cm³ of a {N(lang, C1)} mol/dm³ solution is diluted with water to {V2} cm³. What is the new concentration, in mol/dm³?"),
                     N(lang, C2, 4), [N(lang, w, 4) for w in (C1 * f, C1 * V1, C2 * 10, C2 / 10, C1 + f)], "C₁V₁ = C₂V₂.", src=S(lang), cat=cat(lang, "Solutions", "Solutions"))
    return Draft(T(lang, f"On veut préparer {V2} mL de solution à {N(lang, C2, 4)} mol/L à partir d'une solution mère à {N(lang, C1)} mol/L. Quel volume de solution mère faut-il prélever, en mL ?", f"A technician wants {V2} cm³ of a {N(lang, C2, 4)} mol/dm³ solution from a stock solution of {N(lang, C1)} mol/dm³. What volume of stock solution is needed, in cm³?"),
                 N(lang, V1), [N(lang, w, 3) for w in (V2 - V1, V2 * f, V1 * f / 2, V1 / 2, V2 / C1)], "C₁V₁ = C₂V₂ : V₁ = C₂V₂/C₁.", src=S(lang), cat=cat(lang, "Solutions", "Solutions"))


@both(FR2 + FR1 + EN_O + EN_A, "ch-gas-volume", cap=160, cat="Quantité de matière")
def gas_volume(rng, d, lang):
    n = rng.choice([0.1, 0.25, 0.5, 1, 2, 3, 0.05])
    Vm = 24
    V = n * Vm
    return Draft(T(lang, f"Quel volume occupent {N(lang, n, 3)} mol de gaz dans des conditions où le volume molaire vaut 24 L/mol ?", f"What volume do {N(lang, n, 3)} mol of gas occupy at room temperature and pressure (molar volume 24 dm³/mol)?"),
                 N(lang, V, 2), [N(lang, w, 2) for w in (n / Vm, Vm / n, V * 10, V / 10, n * 22.4)], "V = n × Vm.", src=S(lang), cat=cat(lang, "Quantité de matière", "Mole calculations"))


@both(EN_O + EN_A, "ch-percent-mass", cap=160, cat="Mole calculations")
def percent_mass(rng, d, lang):
    c = rng.choice(COMP)
    el = rng.choice(sorted(parse(c[0])))
    M = mass(c[0])
    pct = AM[el] * parse(c[0])[el] / M * 100
    return Draft(f"What is the percentage by mass of {el} in {nm(c, lang)}, {sub(c[0])}, to 1 decimal place? ({given(c[0], lang)})", f"{N('en', pct, 1)} %",
                 [f"{N('en', w, 1)} %" for w in (100 - pct, pct / 2, pct * 2 if pct < 50 else pct / 3, AM[el] / M * 100 if parse(c[0])[el] > 1 else pct + 5, pct + 10 if pct < 90 else pct - 10)],
                 "% by mass = (atoms × Ar ÷ Mr) × 100.", src=SRC_EN, cat="Mole calculations")


@both(EN_A + EN_O, "ch-empirical", cap=80, cat="Mole calculations")
def empirical(rng, d, lang):
    ratios = [(1, 2), (1, 3), (1, 4), (2, 5), (2, 3), (3, 8), (1, 1), (2, 6)]
    # CxHy given masses: choose the empirical formula (c, h) coprime
    c, h = rng.choice([(1, 2), (1, 4), (2, 5), (1, 3), (1, 1), (3, 8), (2, 3), (3, 4)])
    k = rng.choice([1, 2, 3])
    mC, mH = 12 * c * k, 1 * h * k
    emp = f"C{sub(str(c)) if c > 1 else ''}H{sub(str(h)) if h > 1 else ''}"
    emp = "C" + (sub(str(c)) if c > 1 else "") + "H" + (sub(str(h)) if h > 1 else "")
    wrongs = ["C" + (sub(str(h)) if h > 1 else "") + "H" + (sub(str(c)) if c > 1 else ""), "C" + (sub(str(c * k)) if c * k > 1 else "") + "H" + (sub(str(h * k)) if h * k > 1 else "") if k > 1 else "C2H" + sub(str(h + 1)),
              "C" + (sub(str(c + 1))) + "H" + (sub(str(h))), "C" + (sub(str(c))) + "H" + sub(str(h + 2)), "CH" + sub(str(h + c)) if c + h > 1 else "CH2"]
    return Draft(f"A hydrocarbon contains {mC} g of carbon and {mH} g of hydrogen. (Ar: C = 12, H = 1.) What is its empirical formula?", emp, wrongs, "Divide each mass by Ar to get moles, then divide by the smallest to get the simplest whole-number ratio.", src=SRC_EN, cat="Mole calculations", diff=3)


# ---------------------------------------------------------------------------------------------- équations
REACTIONS = [
    ("2H2 + O2 -> 2H2O", ["H2", "O2", "H2O"]), ("N2 + 3H2 -> 2NH3", ["N2", "H2", "NH3"]), ("CH4 + 2O2 -> CO2 + 2H2O", ["CH4", "O2", "CO2", "H2O"]),
    ("2Mg + O2 -> 2MgO", ["Mg", "O2", "MgO"]), ("C3H8 + 5O2 -> 3CO2 + 4H2O", ["C3H8", "O2", "CO2", "H2O"]), ("2Na + 2H2O -> 2NaOH + H2", ["Na", "H2O", "NaOH", "H2"]),
    ("4Al + 3O2 -> 2Al2O3", ["Al", "O2", "Al2O3"]), ("CaCO3 -> CaO + CO2", ["CaCO3", "CaO", "CO2"]), ("HCl + NaOH -> NaCl + H2O", ["HCl", "NaOH", "NaCl", "H2O"]),
    ("2HCl + Mg -> MgCl2 + H2", ["HCl", "Mg", "MgCl2", "H2"]), ("4Fe + 3O2 -> 2Fe2O3", ["Fe", "O2", "Fe2O3"]), ("2C2H5OH + 6O2 -> 4CO2 + 6H2O", ["C2H5OH", "O2", "CO2", "H2O"]),
    ("C2H5OH + 3O2 -> 2CO2 + 3H2O", ["C2H5OH", "O2", "CO2", "H2O"]), ("2KOH + H2SO4 -> K2SO4 + 2H2O", ["KOH", "H2SO4", "K2SO4", "H2O"]), ("Zn + 2HCl -> ZnCl2 + H2", ["Zn", "HCl", "ZnCl2", "H2"]),
    ("C6H12O6 + 6O2 -> 6CO2 + 6H2O", ["C6H12O6", "O2", "CO2", "H2O"]), ("2NaOH + H2SO4 -> Na2SO4 + 2H2O", ["NaOH", "H2SO4", "Na2SO4", "H2O"]), ("CaCO3 + 2HCl -> CaCl2 + CO2 + H2O", ["CaCO3", "HCl", "CaCl2", "CO2", "H2O"]),
    ("2H2O2 -> 2H2O + O2", ["H2O2", "H2O", "O2"]), ("Cu + 2AgNO3 -> Cu(NO3)2 + 2Ag", ["Cu", "AgNO3", "Cu(NO3)2", "Ag"]), ("2Al + 6HCl -> 2AlCl3 + 3H2", ["Al", "HCl", "AlCl3", "H2"]),
    ("Ca(OH)2 + 2HCl -> CaCl2 + 2H2O", ["Ca(OH)2", "HCl", "CaCl2", "H2O"]), ("C4H10 + 13/2O2 -> 4CO2 + 5H2O", None), ("2C4H10 + 13O2 -> 8CO2 + 10H2O", ["C4H10", "O2", "CO2", "H2O"]),
]
REACTIONS = [r for r in REACTIONS if r[1]]


def split_eq(eq):
    left, right = eq.split(" -> ")
    side = lambda s: [(int(re.match(r"\d*", t).group(0) or 1), re.sub(r"^\d+", "", t)) for t in s.split(" + ")]
    return side(left), side(right)


def count(side):
    tot = {}
    for k, f in side:
        for a, n in parse(f).items():
            tot[a] = tot.get(a, 0) + n * k
    return tot


def show(side):
    return " + ".join((str(k) if k > 1 else "") + sub(f) for k, f in side)


@both(FR2 + FR1 + EN_O + EN_A, "ch-balance", cap=120, cat="Équations chimiques")
def balance(rng, d, lang):
    eq, _ = rng.choice(REACTIONS)
    L, R = split_eq(eq)
    assert count(L) == count(R), eq                          # the equation is balanced atom by atom
    allsp = [(0, i, k, f) for i, (k, f) in enumerate(L)] + [(1, i, k, f) for i, (k, f) in enumerate(R)]
    side, i, k, f = rng.choice(allsp)
    unknown = [("a" if (s, j) == (side, i) else None) for s, j, _, _ in allsp]

    def render(sd, s):
        return " + ".join(("x" if (s, j) == (side, i) else (str(kk) if kk > 1 else "")) + sub(ff) for j, (kk, ff) in enumerate(sd))
    eq_txt = render(L, 0) + " → " + render(R, 1)
    wrongs = [str(w) for w in (k + 1, k * 2, max(1, k - 1), k + 2, 3 if k != 3 else 5, 1 if k != 1 else 2) if w != k]
    return Draft(T(lang, f"Dans l'équation {eq_txt}, quel coefficient doit remplacer x devant {sub(f)} pour que l'équation soit équilibrée ?", f"In the equation {eq_txt}, what coefficient must replace x in front of {sub(f)} to balance it?"),
                 str(k), wrongs, T(lang, "On compte les atomes de chaque élément de part et d'autre : ils doivent être égaux.", "Count the atoms of each element on both sides: they must be equal."), src=S(lang), cat=cat(lang, "Équations chimiques", "Chemical equations"))


STO = [("2H2 + O2 -> 2H2O", "H2", "H2O"), ("CaCO3 -> CaO + CO2", "CaCO3", "CaO"), ("CaCO3 -> CaO + CO2", "CaCO3", "CO2"), ("2Mg + O2 -> 2MgO", "Mg", "MgO"),
       ("C3H8 + 5O2 -> 3CO2 + 4H2O", "C3H8", "CO2"), ("Zn + 2HCl -> ZnCl2 + H2", "Zn", "H2"), ("N2 + 3H2 -> 2NH3", "N2", "NH3"), ("CH4 + 2O2 -> CO2 + 2H2O", "CH4", "H2O"),
       ("2Na + 2H2O -> 2NaOH + H2", "Na", "NaOH"), ("4Al + 3O2 -> 2Al2O3", "Al", "Al2O3"), ("2KOH + H2SO4 -> K2SO4 + 2H2O", "KOH", "K2SO4")]


@both(FR1 + FRT + EN_A, "ch-stoichiometry", cap=240, cat="Stœchiométrie")
def stoichiometry(rng, d, lang):
    eq, a, b = rng.choice(STO)
    L, R = split_eq(eq)
    ka = next(k for k, f in L if f == a)
    kb = next(k for k, f in R if f == b)
    assert count(L) == count(R)
    Ma, Mb = mass(a), mass(b)
    n_a = rng.choice([0.5, 1, 2, 4, 0.25, 3])
    ma = n_a * Ma
    mb = n_a * kb / ka * Mb
    wr = [ma * Mb / Ma, n_a * Mb, ma * kb / ka, n_a * ka / kb * Mb, mb * 2, mb / 2]
    return Draft(T(lang, f"Soit la réaction {eq.replace(' -> ', ' → ')}. Quelle masse de {sub(b)} obtient-on à partir de {N(lang, ma, 2)} g de {sub(a)} ? ({given([a, b], lang)})",
                   f"Consider the reaction {eq.replace(' -> ', ' → ')}. What mass of {sub(b)} forms from {N(lang, ma, 2)} g of {sub(a)}? ({given([a, b], lang)})"),
                 N(lang, mb, 2), [N(lang, w, 2) for w in wr], T(lang, "n(réactif) = m/M, puis le rapport des coefficients, puis m = n × M.", "Moles of reactant = m/M, then use the mole ratio from the equation, then m = n × M."), src=S(lang), cat=cat(lang, "Stœchiométrie", "Reacting masses"))


# ---------------------------------------------------------------------------------------------- acides, bases
@both(FR1 + FRT + EN_A + EN_O, "ch-ph-strong-acid", cap=160, cat="Acides et bases")
def ph_strong_acid(rng, d, lang):
    k = rng.randint(1, 6)
    c = 10 ** -k
    if rng.random() < 0.5:
        return Draft(T(lang, f"Quel est le pH d'une solution d'acide fort (acide chlorhydrique) de concentration 10{sup(-k)} mol/L ?", f"What is the pH of a strong monoprotic acid (hydrochloric acid) at a concentration of 10{sup(-k)} mol/dm³?"),
                     str(k), [str(w) for w in (14 - k, k + 1, k - 1 if k > 1 else k + 2, 7 - k if 7 - k > 0 and 7 - k != k else k + 3, 2 * k if 2 * k != k else k + 4)], "pH = −log[H₃O⁺]" if lang == "fr" else "pH = −log[H⁺].", src=S(lang), cat=cat(lang, "Acides et bases", "Acids and bases"))
    return Draft(T(lang, f"Une solution a pour pH {k}. Quelle est sa concentration en ions H₃O⁺, en mol/L ?", f"A solution has pH {k}. What is its hydrogen ion concentration, in mol/dm³?"),
                 f"10{sup(-k)}", [f"10{sup(-(14 - k))}", f"10{sup(k)}", f"{k}×10{sup(-1)}", f"10{sup(-(k + 1))}", f"{k}"], "[H₃O⁺] = 10⁻ᵖᴴ." if lang == "fr" else "[H⁺] = 10⁻ᵖᴴ.", src=S(lang), cat=cat(lang, "Acides et bases", "Acids and bases"))


@both(FR1 + FRT + EN_A + EN_O, "ch-ph-strong-base", cap=100, cat="Acides et bases")
def ph_strong_base(rng, d, lang):
    k = rng.randint(1, 5)
    ph = 14 - k
    return Draft(T(lang, f"Quel est le pH à 25 °C d'une solution d'hydroxyde de sodium (base forte) de concentration 10{sup(-k)} mol/L ?", f"What is the pH at 25 °C of a sodium hydroxide solution (strong base) of concentration 10{sup(-k)} mol/dm³?"),
                 str(ph), [str(w) for w in (k, 14 + k, 7 + k, k + 1, 7 - k if 7 - k != ph else 0) if w != ph], "[OH⁻] = 10⁻ᵏ donc pOH = k et pH = 14 − k à 25 °C." if lang == "fr" else "pOH = k, so pH = 14 − k at 25 °C.", src=S(lang), cat=cat(lang, "Acides et bases", "Acids and bases"))


@both(FRT + EN_A, "ch-henderson", cap=100, cat="Acides et bases")
def henderson(rng, d, lang):
    pka = rng.choice([3.75, 4.2, 4.76, 4.8, 9.25, 10.3, 2.5])
    ratio = rng.choice([10, 100, 0.1, 0.01, 1])
    pH = pka + math.log10(ratio)
    return Draft(T(lang, f"Un acide faible a un pKa de {N(lang, pka, 2)}. Dans une solution tampon, le rapport [base conjuguée]/[acide] vaut {N(lang, ratio)}. Quel est le pH ?", f"A weak acid has pKa {N(lang, pka, 2)}. In a buffer the ratio [conjugate base]/[acid] is {N(lang, ratio)}. What is the pH?"),
                 N(lang, pH, 2), [N(lang, w, 2) for w in (pka - math.log10(ratio) if ratio != 1 else pka + 1, pka * ratio if ratio != 1 else pka + 2, pka + ratio, pka + 2 * math.log10(ratio) if ratio != 1 else pka - 1, 14 - pH if pH != 7 else 6)],
                 "pH = pKa + log([A⁻]/[HA]).", src=S(lang), cat=cat(lang, "Acides et bases · Tle", "Acids and bases"), diff=4)


@both(FRT + EN_A, "ch-equilibrium-kc", cap=140, cat="Équilibres")
def equilibrium_kc(rng, d, lang):
    a, b, c, e = rng.choice([0.1, 0.2, 0.5, 1, 2]), rng.choice([0.1, 0.2, 0.5, 1, 2]), rng.choice([0.1, 0.2, 0.4, 0.5, 1, 2, 4]), rng.choice([0.1, 0.2, 0.4, 0.5, 1, 2, 4])
    Kc = c * e / (a * b)
    return Draft(T(lang, f"À l'équilibre A + B ⇌ C + D, les concentrations (mol/L) sont [A] = {N(lang, a)}, [B] = {N(lang, b)}, [C] = {N(lang, c)} et [D] = {N(lang, e)}. Quelle est la constante d'équilibre Kc ?",
                   f"At equilibrium for A + B ⇌ C + D the concentrations (mol/dm³) are [A] = {N(lang, a)}, [B] = {N(lang, b)}, [C] = {N(lang, c)} and [D] = {N(lang, e)}. What is Kc?"),
                 N(lang, Kc, 3), [N(lang, w, 3) for w in (a * b / (c * e), (c + e) / (a + b), c * e * a * b, Kc * 2, Kc / 2)], "Kc = [C][D] / ([A][B]).", src=S(lang), cat=cat(lang, "Équilibres · Tle", "Equilibria"), diff=3)


# ---------------------------------------------------------------------------------------------- oxydoréduction, électrochimie
OXI = [("KMnO4", "Mn", 7, {"K": 1, "O": -2}), ("K2Cr2O7", "Cr", 6, {"K": 1, "O": -2}), ("H2SO4", "S", 6, {"H": 1, "O": -2}), ("HNO3", "N", 5, {"H": 1, "O": -2}), ("Fe2O3", "Fe", 3, {"O": -2}),
       ("HClO4", "Cl", 7, {"H": 1, "O": -2}), ("SO2", "S", 4, {"O": -2}), ("NH3", "N", -3, {"H": 1}), ("H2S", "S", -2, {"H": 1}), ("MnO2", "Mn", 4, {"O": -2}), ("Na2SO4", "S", 6, {"Na": 1, "O": -2}),
       ("CO2", "C", 4, {"O": -2}), ("CH4", "C", -4, {"H": 1}), ("NaClO", "Cl", 1, {"Na": 1, "O": -2}), ("FeSO4", "Fe", 2, {"S": 6, "O": -2}), ("Fe3O4", "Fe", Fraction(8, 3), {"O": -2})]
OXI = [o for o in OXI if o[2] == int(o[2])]


def solve_ox(formula, el, known):
    atoms = parse(formula)
    s = sum(known[a] * n for a, n in atoms.items() if a != el)
    assert (-s) % atoms[el] == 0
    return -s // atoms[el]


@both(FRT + EN_A, "ch-oxidation-number", cap=80, cat="Oxydoréduction")
def oxidation_number(rng, d, lang):
    f, el, n, known = rng.choice(OXI)
    assert solve_ox(f, el, known) == n
    signed = lambda v: ("+" if v > 0 else MINUS if v < 0 else "") + str(abs(v))
    wr = [signed(w) for w in (n + 1, n - 1, -n, n + 2, n - 2, 0) if w != n]
    return Draft(T(lang, f"Quel est le nombre d'oxydation de l'élément {el} dans {sub(f)} ?", f"What is the oxidation number of {el} in {sub(f)}?"), signed(n), wr,
                 T(lang, "La somme des nombres d'oxydation est nulle dans une molécule neutre (O : −II, H : +I, métaux alcalins : +I).", "The oxidation numbers in a neutral compound add up to zero (O is −2, H is +1, Group 1 metals are +1)."), src=S(lang), cat=cat(lang, "Oxydoréduction · Tle", "Redox"), diff=3)


POT = {"Mg": ("Mg²⁺/Mg", -2.37), "Al": ("Al³⁺/Al", -1.66), "Zn": ("Zn²⁺/Zn", -0.76), "Fe": ("Fe²⁺/Fe", -0.44), "Ni": ("Ni²⁺/Ni", -0.25), "Pb": ("Pb²⁺/Pb", -0.13), "Cu": ("Cu²⁺/Cu", 0.34), "Ag": ("Ag⁺/Ag", 0.80)}


@both(FRT + EN_A, "ch-cell-emf", cap=80, cat="Électrochimie")
def cell_emf(rng, d, lang):
    a, b = rng.sample(sorted(POT), 2)
    ea, eb = POT[a][1], POT[b][1]
    hi, lo = (a, b) if ea > eb else (b, a)
    emf = abs(ea - eb)
    wr = [emf + 0.1, ea + eb if abs(ea + eb) != emf else emf + 0.5, emf - 0.1 if emf > 0.2 else emf + 0.2, abs(ea) + abs(eb) if abs(abs(ea) + abs(eb) - emf) > 0.01 else emf + 0.3, emf * 2]
    return Draft(T(lang, f"Une pile est formée des couples {POT[a][0]} (E° = {N(lang, ea, 2)} V) et {POT[b][0]} (E° = {N(lang, eb, 2)} V). Quelle est sa force électromotrice standard, en volts ?", f"A cell is made from the couples {POT[a][0]} (E° = {N('en', ea, 2)} V) and {POT[b][0]} (E° = {N('en', eb, 2)} V). What is its standard e.m.f., in volts?"),
                 N(lang, emf, 2), [N(lang, w, 2) for w in wr if w > 0], T(lang, "E = E°(pôle +) − E°(pôle −) = différence des potentiels standard.", "E = E°(positive electrode) − E°(negative electrode)."), src=S(lang), cat=cat(lang, "Électrochimie · Tle", "Electrochemistry"), diff=3)


@both(FRT + EN_A, "ch-faraday", cap=120, cat="Électrochimie")
def faraday(rng, d, lang):
    el = rng.choice([("Cu", 2), ("Ag", 1), ("Zn", 2), ("Al", 3)])
    sym, z = el
    I, t = rng.choice([1, 2, 5, 10]), rng.choice([965, 1930, 3860, 9650, 19300])
    n_e = I * t / 96500
    m = n_e / z * AM[sym]
    return Draft(T(lang, f"Quelle masse de {sym} se dépose à la cathode lors de l'électrolyse d'un sel de {sym} (ion de charge {z}+) par un courant de {I} A pendant {t} s ? (M({sym}) = {N(lang, AM[sym])} g/mol ; 1 F = 96 500 C/mol)",
                   f"What mass of {sym} is deposited at the cathode when a current of {I} A passes for {t} s through a solution of its {z}+ ion? (Ar of {sym} = {N(lang, AM[sym])}; 1 F = 96 500 C/mol)"),
                 f"{N(lang, m, 3)} g", [f"{N(lang, w, 3)} g" for w in (m * z, m / z, m * z * z, I * t / 96500 * AM[sym], m * 10, m / 10)], "m = M·I·t/(z·F).", src=S(lang), cat=cat(lang, "Électrochimie · Tle", "Electrochemistry"), diff=4)


# ---------------------------------------------------------------------------------------------- chimie organique
ALK = ["méthane", "éthane", "propane", "butane", "pentane", "hexane", "heptane", "octane", "nonane", "décane"]
ALK_EN = ["methane", "ethane", "propane", "butane", "pentane", "hexane", "heptane", "octane", "nonane", "decane"]


def cf(c, h, o=0):
    return "C" + (sub(str(c)) if c > 1 else "") + "H" + (sub(str(h)) if h > 1 else "") + ("O" if o else "")


@both(FR1 + FRT + EN_O + EN_A, "ch-alkane-formula", cap=80, cat="Chimie organique")
def alkane_formula(rng, d, lang):
    n = rng.randint(1, 10)
    right = cf(n, 2 * n + 2)
    assert parse(f"C{n}H{2 * n + 2}")["H"] == 2 * n + 2
    names = ALK if lang == "fr" else ALK_EN
    wr = [cf(n, 2 * n), cf(n, 2 * n + 1), cf(n, 2 * n - 2) if n > 1 else cf(2, 2), cf(n + 1, 2 * n + 2), cf(n, 2 * n + 4), cf(n, n + 2)]
    return Draft(T(lang, f"Quelle est la formule brute du {names[n - 1]}, alcane à {n} atome{'s' if n > 1 else ''} de carbone ?", f"What is the molecular formula of {names[n - 1]}, the alkane with {n} carbon atom{'s' if n > 1 else ''}?"),
                 right, wr, T(lang, "Les alcanes ont pour formule générale CₙH₂ₙ₊₂.", "Alkanes have the general formula CₙH₂ₙ₊₂."), src=S(lang), cat=cat(lang, "Chimie organique", "Organic chemistry"))


@both(FR1 + FRT + EN_O + EN_A, "ch-alkane-name", cap=60, cat="Chimie organique")
def alkane_name(rng, d, lang):
    n = rng.randint(1, 10)
    names = ALK if lang == "fr" else ALK_EN
    wr = [names[(n - 1 + k) % 10] for k in (1, -1, 2, -2, 3)]
    return Draft(T(lang, f"Comment s'appelle l'alcane de formule {cf(n, 2 * n + 2)} ?", f"What is the name of the alkane with formula {cf(n, 2 * n + 2)}?"), names[n - 1], wr,
                 T(lang, "Méthane, éthane, propane, butane, pentane, hexane, heptane, octane, nonane, décane pour 1 à 10 carbones.", "Methane, ethane, propane, butane, pentane, hexane, heptane, octane, nonane, decane for 1 to 10 carbons."), src=S(lang), cat=cat(lang, "Chimie organique", "Organic chemistry"))


@both(FR1 + FRT + EN_A, "ch-alkane-mass", cap=80, cat="Chimie organique")
def alkane_mass(rng, d, lang):
    n = rng.randint(1, 10)
    M = 12 * n + 2 * n + 2
    return Draft(T(lang, f"Quelle est la masse molaire de l'alcane à {n} atome{'s' if n > 1 else ''} de carbone, en g/mol ? (C = 12 ; H = 1)", f"What is the molar mass of the alkane with {n} carbon atom{'s' if n > 1 else ''}, in g/mol? (C = 12; H = 1)"),
                 str(M), [str(w) for w in (14 * n, 12 * n + 2 * n, M + 2, M - 2, 12 * n + n)], "M = 14n + 2 pour CₙH₂ₙ₊₂." if lang == "fr" else "M = 14n + 2 for CₙH₂ₙ₊₂.", src=S(lang), cat=cat(lang, "Chimie organique", "Organic chemistry"))


@both(FR1 + FRT + EN_O + EN_A, "ch-combustion", cap=80, cat="Chimie organique")
def combustion(rng, d, lang):
    n = rng.randint(1, 8)
    o2 = Fraction(3 * n + 1, 2)
    h2o = n + 1
    ask = rng.choice(["co2", "h2o"])
    right = n if ask == "co2" else h2o
    what = "CO₂" if ask == "co2" else "H₂O"
    return Draft(T(lang, f"Dans la combustion complète d'une mole de {cf(n, 2 * n + 2)}, combien de moles de {what} se forment-elles ?", f"In the complete combustion of one mole of {cf(n, 2 * n + 2)}, how many moles of {what} are formed?"),
                 str(right), [str(w) for w in (right + 1, right - 1 if right > 1 else right + 2, 2 * right, right + 2, n + h2o) if w != right], T(lang, "Chaque carbone donne un CO₂ ; les 2n + 2 hydrogènes donnent n + 1 H₂O.", "Each carbon gives one CO₂; the 2n + 2 hydrogens give n + 1 H₂O."), src=S(lang), cat=cat(lang, "Chimie organique", "Organic chemistry"))
