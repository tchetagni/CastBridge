"""Culture générale: questions answered by calculation (calendar, time zones, centuries, molar masses).
Regions follow the subject: Cameroon holidays -> CM; African time zones -> AF; world time zones, centuries, chemistry -> WORLD.
Time zones use only places that have no daylight-saving time (so the offset is stable); the table is a fact sheet to check."""
import datetime as dt

from ..core import Draft, fr, gen, near_ints

C = "general"
DAYS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]
MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]

CM_DAYS = [(1, 1, "le Jour de l'An"), (11, 2, "la Fête de la Jeunesse"), (8, 3, "la Journée internationale de la femme"), (1, 5, "la Fête du Travail"),
           (20, 5, "la Fête nationale du Cameroun"), (15, 8, "l'Assomption"), (25, 12, "Noël")]
SRC_CAL = "Calendrier grégorien ; jours fériés et fêtes légales du Cameroun (Code du travail, décrets) - date à vérifier"


def date_fr(d, m):
    return ("1er" if d == 1 else str(d)) + " " + MONTHS[m - 1]


@gen(C, "gen-cm-weekday", cap=1100, cat="Calendrier camerounais", region="CM", source=SRC_CAL)
def cm_weekday(rng, d):
    lo, hi = {1: (2020, 2030), 2: (2000, 2040), 3: (1970, 2060), 4: (1920, 2100), 5: (1850, 2200)}[d]
    day, mon, name = rng.choice(CM_DAYS)
    y = rng.randint(lo, hi)
    wd = DAYS[dt.date(y, mon, day).weekday()]
    return Draft(f"Quel jour de la semaine tombe {name} ({date_fr(day, mon)}) en {y} ?", wd, [x for x in DAYS if x != wd],
                 f"Le {date_fr(day, mon)} {y} est un {wd} (calcul du calendrier grégorien).", src=SRC_CAL)


@gen(C, "gen-cm-days-between", cap=800, cat="Calendrier camerounais", region="CM", source=SRC_CAL)
def cm_between(rng, d):
    lo, hi = {1: (2020, 2030), 2: (2000, 2040), 3: (1970, 2060), 4: (1920, 2100), 5: (1850, 2200)}[d]
    (d1, m1, n1), (d2, m2, n2) = sorted(rng.sample(CM_DAYS, 2), key=lambda t: (t[1], t[0]))
    y = rng.randint(lo, hi)
    n = (dt.date(y, m2, d2) - dt.date(y, m1, d1)).days
    leap = (dt.date(y, 3, 1) - dt.date(y, 2, 28)).days == 2
    return Draft(f"En {y}, combien de jours séparent {n1} ({date_fr(d1, m1)}) de {n2} ({date_fr(d2, m2)}) ?", str(n), [str(x) for x in near_ints(rng, n, 6, lo=1)] + [str(n - 1 if leap else n + 1)],
                 f"Du {date_fr(d1, m1)} au {date_fr(d2, m2)} {y}, il y a {n} jours ({'année bissextile' if leap else 'année non bissextile'}).", src=SRC_CAL)


@gen(C, "gen-leap-year", cap=500, cat="Calendrier", region="WORLD", source="Règle de l'année bissextile du calendrier grégorien")
def leap(rng, d):
    y = rng.choice([rng.randint(1600, 2400), rng.choice([1700, 1800, 1900, 2000, 2100, 2200, 2400, 1600, 2300]), rng.randint(1900, 2100)])
    if y % 400 == 0:
        right, yes = "Oui : divisible par 400", True
    elif y % 100 == 0:
        right, yes = "Non : divisible par 100 mais pas par 400", False
    elif y % 4 == 0:
        right, yes = "Oui : divisible par 4 mais pas par 100", True
    else:
        right, yes = "Non : pas divisible par 4", False
    allc = ["Oui : divisible par 400", "Non : divisible par 100 mais pas par 400", "Oui : divisible par 4 mais pas par 100", "Non : pas divisible par 4"]
    assert yes == (dt.date(y, 12, 31).timetuple().tm_yday == 366)
    return Draft(f"L'année {y} est-elle bissextile ?", right, [x for x in allc if x != right], f"Règle grégorienne : bissextile si divisible par 4, sauf les multiples de 100 qui ne le sont que s'ils sont multiples de 400. {y} : {'oui' if yes else 'non'}.", src="Règle de l'année bissextile du calendrier grégorien")


# -------------------------------------------------------------------------------------- time zones
TZ_AF = {"Yaoundé": 1, "Lagos": 1, "Libreville": 1, "N'Djamena": 1, "Bangui": 1, "Brazzaville": 1, "Kinshasa": 1, "Luanda": 1, "Cotonou": 1, "Niamey": 1, "Alger": 1, "Tunis": 1,
         "Dakar": 0, "Bamako": 0, "Abidjan": 0, "Accra": 0, "Ouagadougou": 0, "Conakry": 0, "Lomé": 0, "Nouakchott": 0, "Monrovia": 0, "Freetown": 0,
         "Johannesburg": 2, "Lusaka": 2, "Harare": 2, "Lilongwe": 2, "Maputo": 2, "Gaborone": 2, "Windhoek": 2, "Kigali": 2, "Bujumbura": 2, "Khartoum": 2, "Tripoli": 2,
         "Nairobi": 3, "Addis-Abeba": 3, "Dar es Salaam": 3, "Kampala": 3, "Mogadiscio": 3, "Antananarivo": 3, "Djibouti": 3, "Asmara": 3}
SRC_TZ_AF = "Fuseaux horaires (UTC) des capitales africaines sans heure d'été - à vérifier sur la base IANA tzdata"
TZ_WORLD = {"Tokyo": 9, "Séoul": 9, "Pékin": 8, "Singapour": 8, "Manille": 8, "Bangkok": 7, "Jakarta": 7, "Hanoï": 7, "Dacca": 6, "New Delhi": 5.5, "Islamabad": 5, "Dubaï": 4, "Riyad": 3, "Moscou": 3,
            "Istanbul": 3, "Bagdad": 3, "Brasilia": -3, "Buenos Aires": -3, "Bogota": -5, "Lima": -5, "Caracas": -4, "Honolulu": -10, "Colombo": 5.5, "Kaboul": 4.5, "Katmandou": 5.75,
            "Yaoundé": 1, "Dakar": 0, "Nairobi": 3}
SRC_TZ_W = "Fuseaux horaires (UTC) de villes sans heure d'été - à vérifier sur la base IANA tzdata"


def hhmm(minutes):
    minutes %= 24 * 60
    return f"{minutes // 60} h {minutes % 60:02d}"


def day_shift(total):
    return "" if 0 <= total < 1440 else (" (le lendemain)" if total >= 1440 else " (la veille)")


def tz_q(rng, table, region, src, note):
    a, b = rng.sample(list(table), 2)
    if table[a] == table[b]:
        return None
    h = rng.randint(0, 23)
    m = rng.choice([0, 0, 0, 15, 30, 45])
    local = h * 60 + m
    there = local + int(round((table[b] - table[a]) * 60))
    right = hhmm(there) + day_shift(there)
    wr = []
    for k in (-(table[b] - table[a]), 1, -1, 2, -2):
        t = local + int(round(k * 60))
        wr.append(hhmm(t) + day_shift(t))
    wr += [hhmm(local + int(round((table[b] - table[a]) * 60)) + 30) + day_shift(there + 30)]
    off = lambda z: "UTC" + ("+" if z >= 0 else "−") + (str(abs(int(z))) if z == int(z) else f"{abs(int(z))}:{int(abs(z) % 1 * 60):02d}")
    return Draft(f"Quand il est {hhmm(local)} à {a} ({off(table[a])}), quelle heure est-il à {b} ({off(table[b])}) ?", right, wr,
                 f"Écart = {off(table[b])} − {off(table[a])} = {fr(table[b] - table[a], 2)} h ; {hhmm(local)} + ({fr(table[b] - table[a], 2)} h) = {right}.", src=src, region=region)


@gen(C, "gen-tz-africa", cap=700, cat="Fuseaux horaires", region="AF", source=SRC_TZ_AF)
def tz_africa(rng, d):
    return tz_q(rng, TZ_AF, "AF", SRC_TZ_AF, "")


@gen(C, "gen-tz-world", cap=600, cat="Fuseaux horaires", region="WORLD", source=SRC_TZ_W)
def tz_world(rng, d):
    return tz_q(rng, TZ_WORLD, "WORLD", SRC_TZ_W, "")


# -------------------------------------------------------------------------------------- centuries
def roman(n):
    out = ""
    for v, s in ((1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")):
        while n >= v:
            out += s
            n -= v
    return out


def siecle(n):
    return roman(n) + ("er" if n == 1 else "e")


@gen(C, "gen-century-of-year", cap=300, cat="Histoire (repères)", region="WORLD", source="Convention : le siècle n commence à l'année 100(n−1)+1")
def century_of_year(rng, d):
    y = rng.choice([rng.randint(1, 2100), rng.randint(1000, 2100), rng.choice([1000, 1100, 1200, 1500, 1600, 1700, 1800, 1900, 2000, 1001, 1101, 1901, 2001, 1999, 1800])])
    c = (y - 1) // 100 + 1
    wr = [siecle(c - 1), siecle(c + 1), siecle(c + 2) if c < 21 else siecle(c - 2), siecle(max(1, c - 2))]
    return Draft(f"À quel siècle appartient l'année {y} ?", f"Le {siecle(c)} siècle", [f"Le {w} siècle" for w in wr], f"Le {siecle(c)} siècle va de l'an {(c - 1) * 100 + 1} à l'an {c * 100} : {y} en fait partie.", src="Convention : le siècle n commence à l'année 100(n−1)+1")


@gen(C, "gen-century-span", cap=120, cat="Histoire (repères)", region="WORLD", source="Convention : le siècle n commence à l'année 100(n−1)+1")
def century_span(rng, d):
    c = rng.randint(2, 21)
    a, b = (c - 1) * 100 + 1, c * 100
    wr = [f"de {a - 1} à {b - 1}", f"de {a + 1} à {b + 1}", f"de {a - 100} à {b - 100}", f"de {a + 50} à {b + 50}"]
    return Draft(f"Quelles années couvre le {siecle(c)} siècle ?", f"de {a} à {b}", wr, f"Le {siecle(c)} siècle couvre les années {a} à {b} inclus.", src="Convention : le siècle n commence à l'année 100(n−1)+1")


# -------------------------------------------------------------------------------------- molar masses
AT = {"H": 1, "C": 12, "N": 14, "O": 16, "Na": 23, "Mg": 24, "Al": 27, "Si": 28, "P": 31, "S": 32, "Cl": 35.5, "K": 39, "Ca": 40, "Fe": 56, "Cu": 63.5, "Zn": 65}
SRC_AT = "Masses atomiques usuelles arrondies (tableau périodique de l'IUPAC) - à vérifier"
FORMULAS = {"H₂O": ("eau", {"H": 2, "O": 1}), "CO₂": ("dioxyde de carbone", {"C": 1, "O": 2}), "NaCl": ("chlorure de sodium (sel de cuisine)", {"Na": 1, "Cl": 1}),
            "H₂SO₄": ("acide sulfurique", {"H": 2, "S": 1, "O": 4}), "CaCO₃": ("carbonate de calcium (calcaire)", {"Ca": 1, "C": 1, "O": 3}), "NaOH": ("soude (hydroxyde de sodium)", {"Na": 1, "O": 1, "H": 1}),
            "NH₃": ("ammoniac", {"N": 1, "H": 3}), "CH₄": ("méthane", {"C": 1, "H": 4}), "C₆H₁₂O₆": ("glucose", {"C": 6, "H": 12, "O": 6}), "HNO₃": ("acide nitrique", {"H": 1, "N": 1, "O": 3}),
            "KCl": ("chlorure de potassium", {"K": 1, "Cl": 1}), "CaO": ("chaux vive", {"Ca": 1, "O": 1}), "Fe₂O₃": ("oxyde de fer III (rouille)", {"Fe": 2, "O": 3}), "Al₂O₃": ("alumine", {"Al": 2, "O": 3}),
            "MgO": ("magnésie", {"Mg": 1, "O": 1}), "HCl": ("acide chlorhydrique", {"H": 1, "Cl": 1}), "C₂H₅OH": ("éthanol", {"C": 2, "H": 6, "O": 1}), "C₃H₈": ("propane", {"C": 3, "H": 8}),
            "C₄H₁₀": ("butane", {"C": 4, "H": 10}), "Mg(OH)₂": ("hydroxyde de magnésium", {"Mg": 1, "O": 2, "H": 2}), "CuO": ("oxyde de cuivre II", {"Cu": 1, "O": 1}), "ZnO": ("oxyde de zinc", {"Zn": 1, "O": 1}),
            "SiO₂": ("silice (sable)", {"Si": 1, "O": 2}), "N₂O": ("protoxyde d'azote", {"N": 2, "O": 1}), "SO₂": ("dioxyde de soufre", {"S": 1, "O": 2}), "Ca(OH)₂": ("chaux éteinte", {"Ca": 1, "O": 2, "H": 2}),
            "C₁₂H₂₂O₁₁": ("saccharose (sucre de table)", {"C": 12, "H": 22, "O": 11}), "K₂SO₄": ("sulfate de potassium", {"K": 2, "S": 1, "O": 4}), "Na₂CO₃": ("carbonate de sodium", {"Na": 2, "C": 1, "O": 3}),
            "H₃PO₄": ("acide phosphorique", {"H": 3, "P": 1, "O": 4})}


@gen(C, "gen-molar-mass", cap=200, cat="Sciences", region="WORLD", source=SRC_AT)
def molar_mass(rng, d):
    f = rng.choice(list(FORMULAS))
    name, comp = FORMULAS[f]
    m = sum(AT[k] * n for k, n in comp.items())
    mm = lambda x: fr(x, 1) + " g/mol"
    wr = [mm(m + 1), mm(m - 2), mm(m + 16), mm(m - 16 if m > 16 else m + 32), mm(m * 2), mm(m + 2)]
    return Draft(f"Quelle est la masse molaire de {name}, de formule {f} (masses atomiques : " + ", ".join(f"{k} = {fr(AT[k], 1)}" for k in comp) + ") ?", mm(m), wr,
                 "M = " + " + ".join(f"{n} × {fr(AT[k], 1)}" for k, n in comp.items()) + f" = {fr(m, 1)} g/mol.", src=SRC_AT)
