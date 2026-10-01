"""Shared helpers of the scope « primaire et premier cycle » (CP..4e, Class 1-6, Form 1-3): bilingual formatting, names,
shopping items, and `reg`, which registers one generator model for several courses at once (one grade profile each)."""
from ..core import NB, fr, gen
from ..courses_pc import GRADE, PC_COURSES

LANG = {k: c["lang"] for k, c in PC_COURSES.items()}
FR_COURSES = [k for k in PC_COURSES if LANG[k] == "fr"]
EN_COURSES = [k for k in PC_COURSES if LANG[k] == "en"]
CAT_EN = {
    "Calcul": "Number work", "Problèmes": "Problem solving", "Géométrie": "Geometry", "Mesures": "Measures", "Fractions": "Fractions",
    "Nombres décimaux": "Decimals", "Algèbre": "Algebra", "Nombres relatifs": "Integers", "Proportionnalité": "Ratio and proportion",
    "Statistiques": "Statistics", "Arithmétique": "Number theory", "Argent": "Money", "Temps": "Time", "Grandeurs": "Measures",
    "Pourcentages": "Percentages", "Numération": "Place value", "Conjugaison": "Verbs", "Orthographe": "Spelling", "Grammaire": "Grammar",
    "Vocabulaire": "Vocabulary",
}


def N(x, lg, dec=None):
    """Number in the language of the course: 1 234,5 (fr) or 1,234.5 (en)."""
    if lg == "fr":
        if isinstance(x, float):
            return fr(x, dec)
        return (("−" if x < 0 else "") + f"{abs(int(x)):,}".replace(",", NB)) if abs(x) >= 1000 else fr(x)
    if isinstance(x, float):
        s = ("%.*f" % (dec if dec is not None else 6, abs(x))).rstrip("0").rstrip(".")
        if s == "":
            s = "0"
        ip, _, fp = s.partition(".")
        ip = f"{int(ip):,}"
        out = ip + ("." + fp if fp else "")
        neg = x < 0 and float(out.replace(",", "")) != 0
        return ("−" if neg else "") + out
    return ("−" if x < 0 else "") + f"{abs(int(x)):,}"


def money(x, lg):
    return N(int(x), lg) + NB + "FCFA" if lg == "fr" else N(int(x), lg) + " FCFA"


def ask(lg, fr_text, en_text):
    return fr_text if lg == "fr" else en_text


def q_end(lg, text):
    """Question mark with the French non-breaking space before it (not in English)."""
    return text + (NB + "?" if lg == "fr" else "?")


NAMES_FR = ["Awa", "Kofi", "Mbarga", "Fanta", "Ngono", "Tchoumi", "Amina", "Junior", "Nkolo", "Bih", "Yannick", "Hawa", "Essomba", "Mireille",
            "Ngassa", "Mballa", "Sali", "Brice", "Clarisse", "Moussa", "Estelle", "Ibrahim", "Nadège", "Samuel"]
NAMES_EN = ["Awa", "Kofi", "Mbarga", "Fanta", "Ngono", "Tabi", "Amina", "Junior", "Nkeng", "Bih", "Yannick", "Neba", "Essomba", "Mirabel",
            "Ngwa", "Mballa", "Sali", "Brice", "Clarisse", "Moussa", "Estelle", "Ibrahim", "Nadine", "Samuel"]
# (fr singular, fr plural, en singular, en plural, price in FCFA)
ITEMS = [("un cahier", "cahiers", "an exercise book", "exercise books", 250), ("un stylo", "stylos", "a pen", "pens", 100),
         ("un pain", "pains", "a loaf of bread", "loaves of bread", 150), ("un sachet d'eau", "sachets d'eau", "a sachet of water", "sachets of water", 50),
         ("une boîte de craies", "boîtes de craies", "a box of chalk", "boxes of chalk", 500), ("un ballon", "ballons", "a ball", "balls", 2500),
         ("un kilo de riz", "kilos de riz", "a kilo of rice", "kilos of rice", 600), ("un kilo d'oignons", "kilos d'oignons", "a kilo of onions", "kilos of onions", 500),
         ("un régime de plantains", "régimes de plantains", "a bunch of plantains", "bunches of plantains", 2000),
         ("une bouteille de jus", "bouteilles de jus", "a bottle of juice", "bottles of juice", 400), ("un beignet", "beignets", "a doughnut", "doughnuts", 50),
         ("un sac d'arachides", "sacs d'arachides", "a bag of groundnuts", "bags of groundnuts", 1500), ("une règle", "règles", "a ruler", "rulers", 200),
         ("un kilo de tomates", "kilos de tomates", "a kilo of tomatoes", "kilos of tomatoes", 800), ("un crayon", "crayons", "a pencil", "pencils", 75),
         ("une gomme", "gommes", "an eraser", "erasers", 100), ("un sachet de bonbons", "sachets de bonbons", "a packet of sweets", "packets of sweets", 25)]


def item(rng, lg):
    it = rng.choice(ITEMS)
    return (it[0], it[1], it[4]) if lg == "fr" else (it[2], it[3], it[4])


def name(rng, lg):
    return rng.choice(NAMES_FR if lg == "fr" else NAMES_EN)


def reg(tpl, fn, courses, cap=200, cat="Calcul", diffs=(1, 2, 3, 4, 5), src_fr=None, src_en=None, region=None):
    """Registers fn(rng, d, g, lg) -> Draft | None for every course in `courses`, g = grade index, lg = 'fr' | 'en'."""
    for c in courses:
        lg = LANG[c]
        g = GRADE[c]
        source = (src_fr or "Programme officiel (MINEDUB/MINESEC), calcul - réponse calculée par programme") if lg == "fr" else \
                 (src_en or "Official syllabus (MINEDUB/MINESEC), computed answer")

        def make(fn=fn, g=g, lg=lg):
            return lambda rng, d: fn(rng, d, g, lg)

        gen(c, "%s-%s" % (c, tpl), cap=cap, diffs=diffs, cat=cat if lg == "fr" else CAT_EN.get(cat, cat), region=region, source=source)(make())


def plaus(rng, right, lo=0, hi=None, unit=1, n=8):
    """Plausible wrong integers: slips of a unit/ten/hundred (forgotten carry, misplaced digit), digit swap, x10 or /10.
    Nearest first, never `right`; `unit` scales the slips (e.g. a price in FCFA)."""
    cands = []
    mag = len(str(abs(right)))
    steps = [unit * k for k in (1, 2, 3, 5, 10)] if right < 40 * unit else [unit, unit * 10, unit * 2, unit * 5, unit * 100 if right >= 400 * unit else unit * 20,
                                                                     unit * 10 ** (mag - 2) if mag > 3 else unit * 4]
    if unit == 1 and right >= 40:
        steps = [1, 10, 2, 5, 100 if right >= 400 else 20, 10 ** (mag - 2) if mag > 3 else 4]
    for st in steps:
        for sg in (1, -1):
            cands.append(right + sg * st)
    s = str(abs(right))
    if len(s) >= 2:
        sw = int(s[1] + s[0] + s[2:])
        if sw != abs(right):
            cands.append(sw)
    cands += [right * 10, right // 10 if right >= 20 else right + 7]
    rng.shuffle(cands)
    head = cands[: len(cands) - 2]
    head.sort(key=lambda v: abs(v - right) + rng.random() * abs(right) * 0.02)
    out = []
    for v in head + cands[-2:]:
        if v == right or v in out or v < lo or (hi is not None and v > hi):
            continue
        out.append(v)
        if len(out) >= n:
            break
    return out


def wrongs_int(rng, right, lg, n=8, lo=None, hi=None, fmt=None, unit=1):
    f = fmt or (lambda v: N(v, lg))
    return [f(v) for v in plaus(rng, right, lo if lo is not None else 0, hi, unit, n)]
