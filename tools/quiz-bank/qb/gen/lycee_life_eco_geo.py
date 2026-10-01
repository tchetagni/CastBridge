"""SVT / Biology, économie / Economics, géographie / Geography : les exercices de calcul du second cycle (fr) et du GCE (en).
Les croisements génétiques sont ÉNUMÉRÉS (échiquier de Punnett), les autres réponses recalculées ; aucune donnée statistique réelle n'est utilisée."""
import math
from fractions import Fraction
from itertools import product

from .. import lycee
from ..core import Draft
from .lycee_common import N, T, both
from .mathfmt import num

SV = [(lycee.fr(l, "svt"), "fr") for l in ("2nde", "1re", "tle")]
BI = [(lycee.en(l, "bio"), "en") for l in ("f5", "l6", "u6")]
EC = [(lycee.fr(l, "eco"), "fr") for l in ("2nde", "1re", "tle")]
EE = [(lycee.en(l, "econ"), "en") for l in ("f5", "l6", "u6")]
GF = [(lycee.fr(l, "geo"), "fr") for l in ("2nde", "1re", "tle")]
GE = [(lycee.en(l, "geo"), "en") for l in ("f5", "l6", "u6")]
SRC = {"fr": "Programme MINESEC - réponse calculée (exercice de calcul, valeurs fictives)", "en": "GCE syllabus topic - answer computed (worked-calculation question, fictitious values)"}


def cat(lang, a, b):
    return a if lang == "fr" else b


def pct(lang, x, dec=1):
    return N(lang, x, dec) + (" %")


def frac_txt(f):
    return num(Fraction(f))


# ================================================================================================ SVT / Biology
def cross(g1, g2):
    """Punnett square of two single-locus genotypes (strings like 'Aa'); returns the list of offspring genotypes."""
    return ["".join(sorted(a + b, key=lambda c: (c.islower(), c))) for a, b in product(g1, g2)]


@both(SV + BI, "bio-mendel-monohybrid", cap=600, cat="Génétique", diffs=(3, 4))
def mendel_mono(rng, d, lang):
    p1, p2 = rng.choice([("Aa", "Aa"), ("Aa", "aa"), ("AA", "Aa"), ("AA", "aa"), ("Aa", "Aa")])
    kids = cross(p1, p2)
    dom = Fraction(sum(1 for k in kids if "A" in k), 4)
    n = rng.choice([40, 80, 120, 200, 400])
    right = dom * n
    wr = [n - right, n // 2 if n // 2 != right else n // 4, Fraction(n, 4) if Fraction(n, 4) != right else n * 3 // 4, n, n * 3 // 4 if n * 3 // 4 != right else n // 3]
    return Draft(T(lang, f"On croise deux plantes de génotypes {p1} et {p2} (allèle A dominant sur a). Sur {n} descendants, combien ont en moyenne le phénotype dominant ?", f"Two plants of genotypes {p1} and {p2} are crossed (allele A is dominant over a). Out of {n} offspring, how many are expected to show the dominant phenotype?"),
                 str(int(right)), [str(int(w)) for w in wr if w != right], T(lang, "On dresse l'échiquier de croisement : chaque combinaison de gamètes a la même probabilité.", "Draw the Punnett square: each gamete combination is equally likely."), src=SRC[lang], cat=cat(lang, "Génétique", "Genetics"), diff=3)


@both(SV + BI, "bio-mendel-dihybrid", cap=600, cat="Génétique", diffs=(4, 5))
def mendel_di(rng, d, lang):
    n = rng.choice([160, 320, 480, 640, 800])
    gam = ["AB", "Ab", "aB", "ab"]
    count = {"double dominant": 0, "double recessive": 0}
    for g1, g2 in product(gam, gam):
        kid = ("A" in g1 + g2, "B" in g1 + g2)
        a_dom = "A" in (g1[0] + g2[0])
        b_dom = "B" in (g1[1] + g2[1])
        if a_dom and b_dom:
            count["double dominant"] += 1
        if not a_dom and not b_dom:
            count["double recessive"] += 1
    assert count == {"double dominant": 9, "double recessive": 1}
    ask = rng.choice(["double dominant", "double recessive"])
    right = n * count[ask] // 16
    wr = [n * 3 // 16, n * 9 // 16 if ask != "double dominant" else n // 16, n // 4, n * 6 // 16 if n * 6 // 16 != right else n // 2, n // 16 + n // 8]
    fr_ask = "dominants pour les deux caractères" if ask == "double dominant" else "récessifs pour les deux caractères"
    return Draft(T(lang, f"On croise deux individus AaBb × AaBb (deux gènes indépendants, A et B dominants). Sur {n} descendants, combien sont en moyenne {fr_ask} ?", f"Two AaBb individuals are crossed (two independent genes, A and B dominant). Out of {n} offspring, how many are expected to show {ask.replace('double ', 'both ')} characters?"),
                 str(right), [str(w) for w in wr if w != right], T(lang, "Le dihybridisme donne les proportions 9/16, 3/16, 3/16 et 1/16.", "A dihybrid cross gives the ratio 9 : 3 : 3 : 1."), src=SRC[lang], cat=cat(lang, "Génétique", "Genetics"), diff=4)


@both(SV + BI, "bio-hardy-weinberg", cap=600, cat="Génétique des populations", diffs=(5,))
def hardy_weinberg(rng, d, lang):
    q = rng.choice([0.1, 0.2, 0.3, 0.4, 0.5, 0.6])
    p = 1 - q
    het = 2 * p * q
    q2 = q * q
    return Draft(T(lang, f"Dans une population à l'équilibre de Hardy-Weinberg, la fréquence du génotype récessif aa est {N(lang, q2, 2)}. Quelle est la fréquence des hétérozygotes Aa ?", f"In a population in Hardy-Weinberg equilibrium the frequency of the homozygous recessive genotype aa is {N(lang, q2, 2)}. What is the frequency of heterozygotes Aa?"),
                 N(lang, het, 2), [N(lang, w, 2) for w in (q, p * p, 2 * q, q2 * 2, 2 * p * p, p) if round(w, 2) != round(het, 2)], "q = √q² ; p = 1 − q ; hétérozygotes = 2pq." if lang == "fr" else "q = √q²; p = 1 − q; heterozygotes = 2pq.", src=SRC[lang], cat=cat(lang, "Génétique des populations", "Population genetics"), diff=5)


@both(SV + BI, "bio-magnification", cap=600, cat="Cellule et microscopie", diffs=(3,))
def magnification(rng, d, lang):
    mag, size = rng.choice([40, 100, 400, 1000, 50, 200]), rng.choice([2, 4, 5, 8, 10, 20, 40, 60])
    actual = size / mag * 1000 if size / mag < 1 else size / mag
    unit_img = "mm"
    real_um = size * 1000 / mag
    return Draft(T(lang, f"L'image d'une cellule mesure {size} mm sur le dessin et le grossissement est ×{mag}. Quelle est la taille réelle de la cellule, en micromètres ?", f"A cell measures {size} mm in the drawing and the magnification is ×{mag}. What is the real size of the cell, in micrometres?"),
                 N(lang, real_um, 2), [N(lang, w, 2) for w in (size * mag, size * mag / 1000, real_um / 1000, real_um * 10, size / mag / 1000 if mag else 1)], "taille réelle = taille de l'image ÷ grossissement (1 mm = 1 000 µm)." if lang == "fr" else "real size = image size ÷ magnification (1 mm = 1000 µm).", src=SRC[lang], cat=cat(lang, "Cellule", "Cell biology"), diff=3)


@both(SV + BI, "bio-dna-chargaff", cap=600, cat="Génétique moléculaire", diffs=(3,))
def chargaff(rng, d, lang):
    a = rng.choice([15, 20, 22, 25, 28, 30, 32, 35])
    g = (100 - 2 * a) // 2
    assert 2 * a + 2 * g == 100
    return Draft(T(lang, f"Dans un ADN double brin, l'adénine représente {a} % des bases. Quel pourcentage représente la guanine ?", f"In double-stranded DNA, adenine makes up {a} % of the bases. What percentage is guanine?"), f"{g} %", [f"{w} %" for w in (a, 100 - a, 50 - g if 50 - g != g else g + 5, 100 - 2 * a, g + 5)],
                 "A = T et G = C : 2A + 2G = 100 %." if lang == "fr" else "A = T and G = C, so 2A + 2G = 100 %.", src=SRC[lang], cat=cat(lang, "Génétique moléculaire", "Molecular genetics"), diff=3)


@both(SV + BI, "bio-hydrogen-bonds", cap=600, cat="Génétique moléculaire", diffs=(3,))
def hydrogen_bonds(rng, d, lang):
    at, gc = rng.randint(2, 20), rng.randint(2, 20)
    hb = 2 * at + 3 * gc
    return Draft(T(lang, f"Un fragment d'ADN contient {at} paires A–T et {gc} paires G–C. Combien de liaisons hydrogène relient ses deux brins ?", f"A DNA fragment has {at} A–T base pairs and {gc} G–C base pairs. How many hydrogen bonds hold its two strands together?"), str(hb), [str(w) for w in (3 * at + 2 * gc, at + gc, 2 * (at + gc), 3 * (at + gc), hb + 2)],
                 "A–T : 2 liaisons ; G–C : 3 liaisons." if lang == "fr" else "A–T has 2 bonds; G–C has 3.", src=SRC[lang], cat=cat(lang, "Génétique moléculaire", "Molecular genetics"), diff=3)


@both(SV + BI, "bio-codons", cap=600, cat="Génétique moléculaire", diffs=(2,))
def codons(rng, d, lang):
    aa = rng.choice([10, 20, 50, 100, 150, 200, 300, 450])
    nt = 3 * aa
    return Draft(T(lang, f"Un ARN messager code une protéine de {aa} acides aminés (on ne compte pas le codon stop). Combien de nucléotides la région codante comporte-t-elle, sans le codon stop ?", f"An mRNA codes for a protein of {aa} amino acids (the stop codon is not counted). How many nucleotides does the coding region contain, excluding the stop codon?"),
                 str(nt), [str(w) for w in (aa, 2 * aa, nt + 3, nt - 3, 4 * aa, aa // 3)], "Un acide aminé est codé par un codon de 3 nucléotides." if lang == "fr" else "Each amino acid is coded by a codon of 3 nucleotides.", src=SRC[lang], cat=cat(lang, "Génétique moléculaire", "Molecular genetics"), diff=2)


@both(SV + BI, "bio-doubling", cap=600, cat="Croissance", diffs=(3,))
def doubling(rng, d, lang):
    n0, td, t = rng.choice([2, 5, 10, 100, 1000]), rng.choice([20, 30, 60, 90]), None
    k = rng.randint(2, 8)
    t = td * k
    right = n0 * 2 ** k
    return Draft(T(lang, f"Une culture bactérienne contient {n0} bactéries et leur nombre double toutes les {td} minutes. Combien y en a-t-il après {t} minutes ?", f"A bacterial culture starts with {n0} bacteria and the number doubles every {td} minutes. How many are there after {t} minutes?"),
                 str(right), [str(w) for w in (n0 * 2 * k, n0 * k * k, right * 2, right // 2, n0 + 2 * k, n0 * 2 ** (k + 1) // 2 * 3 if n0 else 4)], "N = N₀ × 2ⁿ où n est le nombre de doublements." if lang == "fr" else "N = N₀ × 2ⁿ where n is the number of doublings.", src=SRC[lang], cat=cat(lang, "Croissance", "Growth"), diff=3)


@both(SV + BI, "bio-lincoln", cap=600, cat="Écologie", diffs=(3, 4))
def lincoln(rng, d, lang):
    M, C, R = rng.choice([20, 30, 40, 50, 60, 100]), rng.choice([20, 30, 40, 50, 60, 80]), rng.choice([2, 3, 4, 5, 6, 8, 10])
    if (M * C) % R:
        return None
    n = M * C // R
    return Draft(T(lang, f"Méthode capture-marquage-recapture : on marque {M} animaux et on les relâche ; plus tard, sur {C} animaux capturés, {R} sont marqués. Quelle est l'estimation de la taille de la population ?", f"Mark-release-recapture: {M} animals are marked and released; later a sample of {C} contains {R} marked animals. What is the estimated population size?"),
                 str(n), [str(w) for w in (M + C, M * R, C * R, n * 2, n // 2, M * C)], "N ≈ (M × C)/R (indice de Lincoln-Petersen)." if lang == "fr" else "N ≈ (M × C)/R (Lincoln index).", src=SRC[lang], cat=cat(lang, "Écologie", "Ecology"), diff=3)


@both(SV + BI, "bio-quadrat", cap=600, cat="Écologie", diffs=(4,))
def quadrat(rng, d, lang):
    area, qa, counts = rng.choice([100, 200, 400, 1000]), rng.choice([1, 2, 4, 0.25]), None
    k = rng.randint(4, 6)
    counts = [rng.randint(2, 15) for _ in range(k)]
    counts[-1] += (-sum(counts)) % k
    mean = sum(counts) // k
    est = mean * area / qa
    return Draft(T(lang, f"Dans un champ de {area} m², on compte des plantes dans {k} quadrats de {N(lang, qa)} m² : {', '.join(map(str, counts))}. Quelle est l'estimation du nombre total de plantes dans le champ ?", f"In a field of {area} m², plants are counted in {k} quadrats of {N(lang, qa)} m² each: {', '.join(map(str, counts))}. What is the estimated total number of plants in the field?"),
                 N(lang, est, 1), [N(lang, w, 1) for w in (mean * area, sum(counts) * area / qa, mean * qa, est / 2, sum(counts))], "moyenne par quadrat × (aire du champ ÷ aire d'un quadrat)." if lang == "fr" else "mean per quadrat × (field area ÷ quadrat area).", src=SRC[lang], cat=cat(lang, "Écologie", "Ecology"), diff=4)


@both(SV + BI, "bio-trophic", cap=600, cat="Écologie", diffs=(3, 4))
def trophic(rng, d, lang):
    e, eff = rng.choice([1000, 2000, 5000, 10000, 20000]), rng.choice([10, 10, 20, 15, 5])
    k = rng.choice([1, 2, 3])
    nxt = e * (eff / 100) ** k
    return Draft(T(lang, f"Les producteurs fixent {e} kJ d'énergie. Le transfert vers chaque niveau trophique supérieur est de {eff} %. Quelle énergie atteint le niveau {k + 1} (le producteur étant le niveau 1), en kJ ?", f"Producers capture {e} kJ of energy and {eff} % is transferred to each next trophic level. How much energy reaches trophic level {k + 1} (producers are level 1), in kJ?"),
                 N(lang, nxt, 3), [N(lang, w, 3) for w in (e * (eff / 100) ** (k + 1), e * (eff / 100) ** (k - 1) if k > 1 else e * eff / 10, e * eff / 100 * k, e - e * eff / 100 * k, nxt * 10)], "On multiplie par le rendement à chaque transfert." if lang == "fr" else "Multiply by the transfer efficiency at each step.", src=SRC[lang], cat=cat(lang, "Écologie", "Ecology"), diff=3)


@both(SV + BI, "bio-cardiac-output", cap=600, cat="Physiologie", diffs=(2,))
def cardiac(rng, d, lang):
    hr, sv = rng.choice([60, 65, 70, 72, 75, 80, 90, 100]), rng.choice([60, 65, 70, 75, 80, 90])
    co = hr * sv / 1000
    return Draft(T(lang, f"Le cœur bat {hr} fois par minute et éjecte {sv} mL de sang à chaque battement. Quel est son débit cardiaque, en L/min ?", f"The heart beats {hr} times per minute and ejects {sv} mL of blood per beat. What is the cardiac output, in L/min?"),
                 N(lang, co, 3), [N(lang, w, 3) for w in (hr * sv, co * 10, co / 10, hr / sv, (hr + sv) / 100)], "débit = fréquence cardiaque × volume d'éjection systolique." if lang == "fr" else "cardiac output = heart rate × stroke volume.", src=SRC[lang], cat=cat(lang, "Physiologie", "Physiology"), diff=2)


@both(SV + BI, "bio-surface-volume", cap=600, cat="Cellule", diffs=(4,))
def surface_volume(rng, d, lang):
    a = rng.choice([1, 2, 3, 4, 5, 6, 10])
    ratio = Fraction(6 * a * a, a ** 3)
    return Draft(T(lang, f"Un cube-cellule a une arête de {a} µm. Quel est le rapport surface / volume, en µm⁻¹ ?", f"A cube-shaped cell has sides of {a} µm. What is its surface area to volume ratio, in µm⁻¹?"), num(ratio),
                 [num(Fraction(a ** 3, 6 * a * a)), num(Fraction(a * a, a ** 3)), num(Fraction(6, 1) * a), num(Fraction(a, 6)), num(ratio * 2)], "surface = 6a², volume = a³ ; le rapport est 6/a : il diminue quand la cellule grossit." if lang == "fr" else "surface = 6a², volume = a³; the ratio is 6/a and falls as the cell grows.", src=SRC[lang], cat=cat(lang, "Cellule", "Cell biology"), diff=3)


@both(SV + BI, "bio-osmosis", cap=600, cat="Cellule", diffs=(3, 4))
def osmosis(rng, d, lang):
    m0, m1 = rng.choice([10, 12, 15, 20, 25, 30]), None
    delta = rng.choice([-3, -2, -1, 1, 2, 3, 4, -4])
    m1 = m0 + delta
    ch = (m1 - m0) / m0 * 100
    return Draft(T(lang, f"Un morceau de pomme de terre de {m0} g est placé dans une solution et sa masse devient {m1} g. Quelle est la variation de masse en pourcentage ?", f"A piece of potato of mass {m0} g is placed in a solution and its mass becomes {m1} g. What is the percentage change in mass?"),
                 pct(lang, ch, 1).replace("-", "−"), [pct(lang, w, 1).replace("-", "−") for w in (-ch, (m1 - m0) / m1 * 100, ch * 2, ch / 2, delta)], "variation = (masse finale − masse initiale) ÷ masse initiale × 100." if lang == "fr" else "% change = (final − initial) ÷ initial × 100.", src=SRC[lang], cat=cat(lang, "Cellule", "Cell biology"), diff=3)


# ================================================================================================ économie / Economics
@both(EC + EE, "eco-inflation", cap=600, cat="Prix et inflation", diffs=(2,))
def inflation(rng, d, lang):
    p0, up = rng.choice([100, 110, 120, 125, 200, 250, 400]), rng.choice([2, 3, 4, 5, 6, 8, 10, 12])
    p1 = p0 * (100 + up) / 100
    if p1 != int(p1):
        p1 = round(p1, 2)
    right = (p1 - p0) / p0 * 100
    return Draft(T(lang, f"L'indice des prix à la consommation passe de {N(lang, p0)} à {N(lang, p1, 2)} en un an. Quel est le taux d'inflation, en pourcentage ?", f"The consumer price index rises from {N(lang, p0)} to {N(lang, p1, 2)} in one year. What is the inflation rate, in per cent?"),
                 pct(lang, right, 1), [pct(lang, w, 1) for w in (p1 - p0, (p1 - p0) / p1 * 100, right * 2, right / 2, p1 / p0)], "taux = (indice final − indice initial) ÷ indice initial × 100." if lang == "fr" else "rate = (new index − old index) ÷ old index × 100.", src=SRC[lang], cat=cat(lang, "Prix et inflation", "Inflation"), diff=2)


@both(EC + EE, "eco-real-value", cap=600, cat="Prix et inflation", diffs=(3,))
def real_value(rng, d, lang):
    w, idx = rng.choice([100000, 150000, 200000, 250000, 300000, 500000]), rng.choice([105, 110, 120, 125, 130, 150, 200])
    real = w * 100 / idx
    return Draft(T(lang, f"Un salaire nominal de {w} FCFA est versé alors que l'indice des prix est {idx} (base 100 l'année de référence). Quel est le salaire réel, en FCFA aux prix de l'année de référence ?", f"A nominal wage of {w} FCFA is paid when the price index is {idx} (base year = 100). What is the real wage, in base-year FCFA?"),
                 N(lang, real, 0) if real == int(real) else N(lang, real, 2), [N(lang, x, 2) for x in (w * idx / 100, w - idx, w / idx, real * 2, w * 0.1)], "valeur réelle = valeur nominale ÷ indice des prix × 100." if lang == "fr" else "real value = nominal value ÷ price index × 100.", src=SRC[lang], cat=cat(lang, "Prix et inflation", "Inflation"), diff=3)


@both(EC + EE, "eco-growth", cap=600, cat="Croissance et production", diffs=(2,))
def growth(rng, d, lang):
    g0, g = rng.choice([1000, 2000, 5000, 8000, 12000, 20000]), rng.choice([-4, -2, 1, 2, 3, 4, 5, 6, 8])
    g1 = g0 * (100 + g) / 100
    right = g
    return Draft(T(lang, f"Le PIB d'un pays passe de {g0} à {N(lang, g1, 1)} milliards de FCFA. Quel est le taux de croissance, en pourcentage ?", f"A country's GDP rises from {g0} to {N(lang, g1, 1)} billion FCFA. What is the growth rate, in per cent?"),
                 pct(lang, right, 1).replace("-", "−"), [pct(lang, w, 1).replace("-", "−") for w in (-right, right * 2, right / 2, (g1 - g0), right + 1)], "taux = (PIB final − PIB initial) ÷ PIB initial × 100." if lang == "fr" else "growth = (final GDP − initial GDP) ÷ initial GDP × 100.", src=SRC[lang], cat=cat(lang, "Croissance et production", "Growth and output"), diff=2)


@both(EC + EE, "eco-gdp-per-capita", cap=600, cat="Croissance et production", diffs=(3,))
def gdp_per_capita(rng, d, lang):
    pop, gdp = rng.choice([2, 4, 5, 10, 20, 25]), rng.choice([2000, 4000, 5000, 10000, 20000, 30000])
    pc = gdp * 1000 / pop
    return Draft(T(lang, f"Un pays de {pop} millions d'habitants a un PIB de {gdp} milliards de FCFA. Quel est son PIB par habitant, en FCFA ?", f"A country of {pop} million people has a GDP of {gdp} billion FCFA. What is its GDP per head, in FCFA?"),
                 N(lang, pc, 0), [N(lang, w, 0) for w in (pc * 1000, pc / 1000, gdp * pop, gdp / pop, pc * 10)], "PIB par habitant = PIB ÷ population (milliards ÷ millions = ×1 000)." if lang == "fr" else "GDP per head = GDP ÷ population (billion ÷ million = ×1000).", src=SRC[lang], cat=cat(lang, "Croissance et production", "Growth and output"), diff=3)


@both(EC + EE, "eco-unemployment", cap=600, cat="Emploi", diffs=(2,))
def unemployment(rng, d, lang):
    active, un = rng.choice([1000, 2000, 5000, 8000, 10000]), rng.choice([100, 200, 250, 400, 500, 800, 1000])
    if un >= active:
        return None
    pop = active * rng.choice([2, 3])
    right = un / active * 100
    return Draft(T(lang, f"Un pays compte {pop} habitants, dont {active} actifs (population active). {un} actifs sont au chômage. Quel est le taux de chômage, en pourcentage ?", f"A country has {pop} inhabitants, of whom {active} are in the labour force. {un} of them are unemployed. What is the unemployment rate, in per cent?"),
                 pct(lang, right, 1), [pct(lang, w, 1) for w in (un / pop * 100, active / pop * 100, (active - un) / active * 100, right * 2, un / 10)], "taux de chômage = chômeurs ÷ population active × 100." if lang == "fr" else "unemployment rate = unemployed ÷ labour force × 100.", src=SRC[lang], cat=cat(lang, "Emploi", "Employment"), diff=2)


@both(EC + EE, "eco-multiplier", cap=600, cat="Macroéconomie", diffs=(4,))
def multiplier(rng, d, lang):
    c, dI = rng.choice([Fraction(1, 2), Fraction(3, 4), Fraction(4, 5), Fraction(9, 10), Fraction(2, 3)]), rng.choice([10, 20, 50, 100, 200])
    k = 1 / (1 - c)
    dY = k * dI
    cs = f"{c.numerator}/{c.denominator}"
    return Draft(T(lang, f"La propension marginale à consommer est de {cs} et l'investissement augmente de {dI} milliards de FCFA. De combien augmente le revenu national, en milliards ?", f"The marginal propensity to consume is {cs} and investment rises by {dI} billion FCFA. By how much does national income rise, in billion FCFA?"),
                 num(dY), [num(w) for w in (dI * c, dI / (1 - c) / 2, dI * (1 + c), dI, dY * 2, dI * (1 - c))], "multiplicateur k = 1/(1 − c) ; ΔY = k × ΔI." if lang == "fr" else "multiplier k = 1/(1 − MPC); ΔY = k × ΔI.", src=SRC[lang], cat=cat(lang, "Macroéconomie", "Macroeconomics"), diff=4)


@both(EC + EE, "eco-price-elasticity", cap=600, cat="Microéconomie", diffs=(4,))
def elasticity(rng, d, lang):
    dp, dq = rng.choice([5, 10, 20, 25]), rng.choice([-2, -4, -5, -10, -15, -20, -30, -50])
    e = dq / dp
    kind = "élastique" if abs(e) > 1 else ("à élasticité unitaire" if abs(e) == 1 else "inélastique")
    kind_en = "elastic" if abs(e) > 1 else ("unit elastic" if abs(e) == 1 else "inelastic")
    names = {"élastique": ["inélastique", "à élasticité unitaire", "parfaitement inélastique"], "inélastique": ["élastique", "à élasticité unitaire", "parfaitement élastique"], "à élasticité unitaire": ["élastique", "inélastique", "parfaitement élastique"]}
    names_en = {"elastic": ["inelastic", "unit elastic", "perfectly inelastic"], "inelastic": ["elastic", "unit elastic", "perfectly elastic"], "unit elastic": ["elastic", "inelastic", "perfectly elastic"]}
    ans, wr = (kind, names[kind]) if lang == "fr" else (kind_en, names_en[kind_en])
    return Draft(T(lang, f"Quand le prix d'un bien augmente de {dp} %, la quantité demandée baisse de {abs(dq)} %. La demande est-elle … ?", f"When the price of a good rises by {dp} %, the quantity demanded falls by {abs(dq)} %. Is demand …?"),
                 ans, wr, T(lang, f"Élasticité-prix = {num(dq)} ÷ {dp} = {N(lang, e, 2)} : en valeur absolue {'supérieure' if abs(e) > 1 else 'égale' if abs(e) == 1 else 'inférieure'} à 1.", f"Price elasticity = {num(dq)} ÷ {dp} = {N(lang, e, 2)}: its absolute value is {'greater than' if abs(e) > 1 else 'equal to' if abs(e) == 1 else 'less than'} 1."), src=SRC[lang], cat=cat(lang, "Microéconomie", "Microeconomics"), diff=4)


@both(EC + EE, "eco-breakeven", cap=600, cat="Entreprise", diffs=(3, 4))
def breakeven(rng, d, lang):
    fc, p, vc = rng.choice([10000, 20000, 50000, 100000, 120000]), rng.choice([100, 150, 200, 250, 300, 500]), None
    vc = rng.choice([20, 40, 50, 60, 80, 100])
    if p <= vc or fc % (p - vc):
        return None
    q = fc // (p - vc)
    return Draft(T(lang, f"Une entreprise a des coûts fixes de {fc} FCFA, un prix de vente unitaire de {p} FCFA et un coût variable unitaire de {vc} FCFA. Quel est son seuil de rentabilité, en unités ?", f"A firm has fixed costs of {fc} FCFA, sells at {p} FCFA per unit and has variable costs of {vc} FCFA per unit. What is its break-even output, in units?"),
                 str(q), [str(w) for w in (fc // p, fc // vc, fc // (p + vc) if fc % (p + vc) == 0 else q + 50, q * 2, q // 2, (p - vc) * fc // 100)], "seuil = coûts fixes ÷ (prix − coût variable unitaire)." if lang == "fr" else "break-even = fixed costs ÷ (price − variable cost per unit).", src=SRC[lang], cat=cat(lang, "Entreprise", "The firm"), diff=3)


@both(EC + EE, "eco-profit", cap=600, cat="Entreprise", diffs=(2,))
def profit(rng, d, lang):
    q, p, fc, vc = rng.choice([100, 200, 500, 1000]), rng.choice([50, 100, 200, 500]), rng.choice([5000, 10000, 20000, 50000]), rng.choice([10, 20, 30, 40, 80])
    tr, tc = q * p, fc + vc * q
    pr = tr - tc
    return Draft(T(lang, f"Une entreprise vend {q} unités à {p} FCFA. Ses coûts fixes sont de {fc} FCFA et son coût variable de {vc} FCFA par unité. Quel est son profit, en FCFA ?", f"A firm sells {q} units at {p} FCFA each. Its fixed costs are {fc} FCFA and its variable cost is {vc} FCFA per unit. What is its profit, in FCFA?"),
                 num(pr), [num(w) for w in (tr - fc, tr - vc * q, tr + tc, -pr if pr else 5, tr, pr + fc)], "profit = recette totale − coût total." if lang == "fr" else "profit = total revenue − total cost.", src=SRC[lang], cat=cat(lang, "Entreprise", "The firm"), diff=2)


@both(EC + EE, "eco-equilibrium", cap=600, cat="Microéconomie", diffs=(4,))
def equilibrium(rng, d, lang):
    pstar, q = rng.randint(5, 40), rng.randint(20, 120)
    b, dd = rng.randint(1, 5), rng.randint(1, 5)
    a = q + b * pstar            # Qd = a - bP
    c = q - dd * pstar           # Qs = c + dP
    if c < 0:
        return None
    return Draft(T(lang, f"La demande d'un bien est Qd = {a} − {b}P et l'offre Qs = {c} + {dd}P. Quel est le prix d'équilibre ?", f"The demand for a good is Qd = {a} − {b}P and supply is Qs = {c} + {dd}P. What is the equilibrium price?"),
                 str(pstar), [str(w) for w in (q, pstar + 1, pstar - 1 if pstar > 1 else 7, a - c, (a + c) // 2, pstar * 2)], "À l'équilibre Qd = Qs : on résout l'équation en P." if lang == "fr" else "At equilibrium Qd = Qs; solve for P.", src=SRC[lang], cat=cat(lang, "Microéconomie", "Microeconomics"), diff=3)


@both(EC + EE, "eco-gdp-expenditure", cap=600, cat="Macroéconomie", diffs=(3,))
def gdp_expenditure(rng, d, lang):
    C, I, G, X, M = rng.randint(100, 900), rng.randint(50, 400), rng.randint(50, 400), rng.randint(20, 300), rng.randint(20, 300)
    y = C + I + G + X - M
    return Draft(T(lang, f"Consommation : {C} ; investissement : {I} ; dépenses publiques : {G} ; exportations : {X} ; importations : {M} (milliards de FCFA). Quel est le PIB par la méthode des dépenses ?", f"Consumption: {C}; investment: {I}; government spending: {G}; exports: {X}; imports: {M} (billion FCFA). What is GDP by the expenditure method?"),
                 str(y), [str(w) for w in (C + I + G + X + M, C + I + G, C + I + G + M - X, y + 2 * M, C + I + G - X + M)], "PIB = C + I + G + (X − M)." if lang == "fr" else "GDP = C + I + G + (X − M).", src=SRC[lang], cat=cat(lang, "Macroéconomie", "Macroeconomics"), diff=3)


@both(EC + EE, "eco-opportunity-cost", cap=600, cat="Échanges internationaux", diffs=(3,))
def opportunity_cost(rng, d, lang):
    a, b = rng.choice([(10, 20), (20, 40), (30, 60), (12, 36), (15, 45), (8, 24)])
    return Draft(T(lang, f"Avec toutes ses ressources, un pays peut produire soit {a} tonnes de cacao, soit {b} tonnes de café. Quel est le coût d'opportunité d'une tonne de cacao, en tonnes de café ?", f"With all its resources a country can produce either {a} tonnes of cocoa or {b} tonnes of coffee. What is the opportunity cost of one tonne of cocoa, in tonnes of coffee?"),
                 N(lang, b / a, 2), [N(lang, w, 2) for w in (a / b, a + b, a * b, b - a, b / a + 1)], "coût d'opportunité = ce à quoi on renonce : b ÷ a." if lang == "fr" else "opportunity cost = what is given up: b ÷ a.", src=SRC[lang], cat=cat(lang, "Échanges internationaux", "International trade"), diff=3)


@both(EC + EE, "eco-trade-balance", cap=600, cat="Échanges internationaux", diffs=(2,))
def trade_balance(rng, d, lang):
    x, m = rng.randint(100, 900), rng.randint(100, 900)
    if x == m:
        return None
    bal = x - m
    return Draft(T(lang, f"Un pays exporte pour {x} milliards de FCFA et importe pour {m} milliards. Quel est le solde de sa balance commerciale, en milliards de FCFA ?", f"A country exports goods worth {x} billion FCFA and imports goods worth {m} billion. What is its trade balance, in billion FCFA?"),
                 num(bal), [num(w) for w in (-bal, x + m, x * m // 100, abs(bal) + 50, bal + 100)], "solde = exportations − importations." if lang == "fr" else "balance = exports − imports.", src=SRC[lang], cat=cat(lang, "Échanges internationaux", "International trade"), diff=2)


@both(EC + EE, "eco-exchange", cap=600, cat="Monnaie", diffs=(1,))
def exchange(rng, d, lang):
    e = rng.choice([1, 2, 5, 10, 20, 50, 100, 200, 500])
    fcfa = e * 656
    return Draft(T(lang, f"En prenant 1 euro = 656 FCFA (valeur arrondie de la parité fixe), combien de FCFA obtient-on pour {e} euros ?", f"Taking 1 euro = 656 FCFA (rounded from the fixed parity), how many FCFA do you get for {e} euros?"),
                 str(fcfa), [str(w) for w in (e * 565, e * 665, e * 600, e * 656 + 656, round(e / 656, 3) if e < 656 else 1, e * 6560)], "On multiplie le montant en euros par le taux." if lang == "fr" else "Multiply the amount in euros by the rate.", src=SRC[lang], cat=cat(lang, "Monnaie", "Money"), diff=1)


# ================================================================================================ géographie / Geography
@both(GF + GE, "geo-density", cap=600, cat="Population", diffs=(2,))
def density(rng, d, lang):
    pop, area = rng.choice([2000000, 5000000, 12000000, 24000000, 1500000, 800000]), rng.choice([10000, 20000, 50000, 100000, 200000, 475000, 5000])
    dens = pop / area
    return Draft(T(lang, f"Une région compte {pop} habitants sur {area} km². Quelle est sa densité de population, en habitants par km² ?", f"A region has {pop} inhabitants living on {area} km². What is its population density, in people per km²?"),
                 N(lang, dens, 1), [N(lang, w, 1) for w in (area / pop, pop * area / 1000, dens * 10, dens / 10, pop / (area * 100))], "densité = population ÷ superficie." if lang == "fr" else "density = population ÷ area.", src=SRC[lang], cat=cat(lang, "Population", "Population"), diff=2)


@both(GF + GE, "geo-natural-increase", cap=600, cat="Population", diffs=(3,))
def natural_increase(rng, d, lang):
    b, dt = rng.choice([25, 30, 35, 40, 42, 45, 20, 15]), rng.choice([5, 8, 10, 12, 15, 20])
    if b <= dt:
        return None
    ni = (b - dt) / 10
    return Draft(T(lang, f"Dans un pays, le taux de natalité est de {b} ‰ et le taux de mortalité de {dt} ‰. Quel est le taux d'accroissement naturel, en pourcentage ?", f"In a country the birth rate is {b} per thousand and the death rate is {dt} per thousand. What is the natural increase, in per cent?"),
                 pct(lang, ni, 2), [pct(lang, w, 2) for w in (b - dt, (b + dt) / 10, ni * 10, ni / 10, b / dt)], "accroissement naturel = (natalité − mortalité) ÷ 10 en %." if lang == "fr" else "natural increase = (birth rate − death rate) ÷ 10, in %.", src=SRC[lang], cat=cat(lang, "Population", "Population"), diff=3)


@both(GF + GE, "geo-map-scale", cap=600, cat="Cartographie", diffs=(2,))
def map_scale(rng, d, lang):
    denom, cm = rng.choice([25000, 50000, 100000, 200000, 500000, 1000000]), rng.choice([2, 3, 4, 5, 8, 10, 12])
    km = cm * denom / 100000
    return Draft(T(lang, f"Sur une carte à l'échelle 1/{denom}, deux villes sont distantes de {cm} cm. Quelle est la distance réelle, en km ?", f"On a map of scale 1:{denom}, two towns are {cm} cm apart. What is the real distance, in km?"),
                 N(lang, km, 3), [N(lang, w, 3) for w in (km * 10, km / 10, cm * denom, cm / denom, km * 100)], "distance réelle = distance sur la carte × dénominateur de l'échelle (1 km = 100 000 cm)." if lang == "fr" else "real distance = map distance × scale denominator (1 km = 100 000 cm).", src=SRC[lang], cat=cat(lang, "Cartographie", "Map skills"), diff=2)


@both(GF + GE, "geo-time-longitude", cap=600, cat="Cartographie", diffs=(3, 4))
def time_longitude(rng, d, lang):
    l1, l2 = rng.choice([0, 15, 30, 45, 60, 75, 90, 105, 120]), rng.choice([0, 15, 30, 45, 60, 75, 90, 105, 120, 135])
    if l1 == l2:
        return None
    h = abs(l1 - l2) // 15
    return Draft(T(lang, f"Deux villes sont situées sur les longitudes {l1}° Est et {l2}° Est. Quelle est la différence d'heure solaire entre elles, en heures ? (la Terre tourne de 15° par heure)", f"Two cities lie on longitudes {l1}° East and {l2}° East. What is the difference in solar time between them, in hours? (the Earth turns 15° each hour)"),
                 str(h), [str(w) for w in (abs(l1 - l2), h + 1, h - 1 if h > 1 else h + 2, h * 2, (l1 + l2) // 15 if (l1 + l2) // 15 != h else h + 3)], "différence = écart de longitude ÷ 15." if lang == "fr" else "time difference = difference in longitude ÷ 15.", src=SRC[lang], cat=cat(lang, "Cartographie", "Map skills"), diff=3)


@both(GF + GE, "geo-slope", cap=600, cat="Relief", diffs=(3,))
def slope(rng, d, lang):
    dh, dist = rng.choice([50, 100, 150, 200, 250, 300, 500]), rng.choice([500, 1000, 2000, 2500, 5000])
    s = dh / dist * 100
    return Draft(T(lang, f"Entre deux points distants de {dist} m en projection horizontale, l'altitude passe de 400 m à {400 + dh} m. Quelle est la pente moyenne, en pourcentage ?", f"Between two points {dist} m apart horizontally, the height rises from 400 m to {400 + dh} m. What is the average gradient, in per cent?"),
                 pct(lang, s, 1), [pct(lang, w, 1) for w in (dist / dh, s * 10, s / 10, (400 + dh) / dist * 100, dh / (dist + dh) * 100)], "pente = dénivelé ÷ distance horizontale × 100." if lang == "fr" else "gradient = height gain ÷ horizontal distance × 100.", src=SRC[lang], cat=cat(lang, "Relief", "Relief"), diff=3)


@both(GF + GE, "geo-climate-mean", cap=600, cat="Climats", diffs=(2,))
def climate_mean(rng, d, lang):
    base = rng.randint(18, 30)
    temps = [base + rng.randint(-3, 3) for _ in range(12)]
    temps[-1] += (-sum(temps)) % 12
    mean = sum(temps) // 12
    amp = max(temps) - min(temps)
    which = rng.choice(["mean", "amp"])
    seq = ", ".join(map(str, temps))
    if which == "mean":
        return Draft(T(lang, f"Les températures moyennes mensuelles d'une station (janvier à décembre, en °C) sont : {seq}. Quelle est la température moyenne annuelle, en °C ?", f"The mean monthly temperatures of a station (January to December, in °C) are: {seq}. What is the mean annual temperature, in °C?"),
                     str(mean), [str(w) for w in (mean + 1, mean - 1, sum(temps), max(temps), sorted(temps)[5] if sorted(temps)[5] != mean else mean + 2, mean + 2)], "moyenne annuelle = somme des 12 valeurs ÷ 12." if lang == "fr" else "annual mean = sum of the 12 values ÷ 12.", src=SRC[lang], cat=cat(lang, "Climats", "Climate"), diff=2)
    return Draft(T(lang, f"Les températures moyennes mensuelles d'une station (en °C) sont : {seq}. Quelle est l'amplitude thermique annuelle, en °C ?", f"The mean monthly temperatures of a station (°C) are: {seq}. What is the annual temperature range, in °C?"),
                 str(amp), [str(w) for w in (amp + 1, amp - 1 if amp > 1 else amp + 2, max(temps), min(temps), mean, amp + 3)], "amplitude = température maximale − température minimale." if lang == "fr" else "range = highest − lowest monthly temperature.", src=SRC[lang], cat=cat(lang, "Climats", "Climate"), diff=2)


@both(GF + GE, "geo-urbanisation", cap=600, cat="Population", diffs=(2,))
def urbanisation(rng, d, lang):
    tot, rate = rng.choice([10, 20, 25, 40, 50]), rng.choice([20, 30, 40, 50, 60, 70, 80])
    urban = tot * rate / 100
    return Draft(T(lang, f"Un pays compte {tot} millions d'habitants dont {N(lang, urban, 1)} millions en ville. Quel est son taux d'urbanisation, en pourcentage ?", f"A country has {tot} million inhabitants, of whom {N(lang, urban, 1)} million live in towns. What is its urbanisation rate, in per cent?"),
                 pct(lang, rate, 1), [pct(lang, w, 1) for w in (100 - rate, urban, rate * 2 if rate < 50 else rate / 2, tot / urban, rate + 10)], "taux = population urbaine ÷ population totale × 100." if lang == "fr" else "rate = urban population ÷ total population × 100.", src=SRC[lang], cat=cat(lang, "Population", "Population"), diff=2)


@both(GF + GE, "geo-dependency", cap=600, cat="Population", diffs=(4,))
def dependency(rng, d, lang):
    y, a, o = rng.choice([40, 45, 30, 20]), None, None
    o = rng.choice([3, 4, 5, 10, 15])
    a = 100 - y - o
    ratio = (y + o) / a * 100
    return Draft(T(lang, f"Dans un pays, {y} % de la population a moins de 15 ans, {o} % a plus de 64 ans, et le reste a entre 15 et 64 ans. Quel est le rapport de dépendance (jeunes + vieux pour 100 actifs) ?", f"In a country {y} % of people are under 15, {o} % are over 64, and the rest are aged 15–64. What is the dependency ratio (young plus old per 100 of working age)?"),
                 N(lang, ratio, 1), [N(lang, w, 1) for w in (y + o, a / (y + o) * 100, y / a * 100, ratio / 2, ratio + 10)], "rapport = (moins de 15 ans + plus de 64 ans) ÷ (15-64 ans) × 100." if lang == "fr" else "ratio = (under 15 + over 64) ÷ (15–64) × 100.", src=SRC[lang], cat=cat(lang, "Population", "Population"), diff=4)
