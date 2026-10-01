"""Physique du second cycle francophone (2nde, 1re, Tle) et GCE Physics (Ordinary, Advanced Level), en deux langues.
Chaque modèle calcule sa réponse (valeurs données dans l'énoncé : g, c, Vm… jamais de constante implicite) et la contrôle."""
import math
from fractions import Fraction

from .. import lycee
from ..core import MINUS, Draft, fr
from .lycee_common import N, T, both, numw
from .mathfmt import num, sup

F2, F1, FT = lycee.fr("2nde", "phys"), lycee.fr("1re", "phys"), lycee.fr("tle", "phys")
O5, A6, A12 = lycee.en("f5", "phys"), lycee.en("l6", "phys"), lycee.en("u6", "phys")
SRC_FR = "Programme MINESEC de physique - réponse calculée à partir des données de l'énoncé"
SRC_EN = "GCE Physics syllabus topic - answer computed from the data given in the question"
FR2, FR1, FRT = [(F2, "fr")], [(F1, "fr")], [(FT, "fr")]
EN_O, EN_A = [(O5, "en")], [(A6, "en"), (A12, "en")]


def S(lang):
    return SRC_FR if lang == "fr" else SRC_EN


def cat(lang, fr_c, en_c):
    return fr_c if lang == "fr" else en_c


def rd(x, dec):
    return round(x + 0.0, dec)


# ================================================================================================ mécanique
@both(FR2 + EN_O, "ph-speed", cap=600, cat="Mécanique", diffs=(1, 2))
def speed(rng, d, lang):
    v, t = rng.choice([2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25]), rng.choice([2, 3, 4, 5, 6, 8, 10, 15, 20, 30])
    dist = v * t
    ask = rng.choice(["v", "d", "t"])
    if ask == "v":
        right, wr = v, [v * t, rd(t / v, 2), v + t, dist / (t + 1), 3.6 * v]
        text = T(lang, f"Un mobile parcourt {dist} m en {t} s à vitesse constante. Quelle est sa vitesse, en m/s ?", f"A cyclist covers {dist} m in {t} s at constant speed. What is the speed, in m/s?")
        unit = ""
    elif ask == "d":
        right, wr = dist, [v + t, dist * 2, v / t if t else 1, dist + v, dist - t]
        text = T(lang, f"Un mobile roule à {v} m/s pendant {t} s. Quelle distance parcourt-il, en mètres ?", f"A vehicle moves at {v} m/s for {t} s. How far does it travel, in metres?")
        unit = ""
    else:
        right, wr = t, [v * dist, dist + v, rd(v / dist, 2), dist - v, 2 * t]
        text = T(lang, f"Un mobile roule à {v} m/s et parcourt {dist} m. Combien de temps dure le trajet, en secondes ?", f"A vehicle moves at {v} m/s and covers {dist} m. How long does the journey take, in seconds?")
    return Draft(text, N(lang, right), [N(lang, w, 2) for w in wr], T(lang, "v = d/t, donc d = v·t et t = d/v.", "Use v = d/t, hence d = v·t and t = d/v."), src=S(lang), cat=cat(lang, "Mécanique · 2nde", "Mechanics"))


@both(FR2 + EN_O, "ph-kmh-ms", cap=600, cat="Mécanique", diffs=(1, 2))
def kmh_ms(rng, d, lang):
    ms = rng.choice([5, 10, 15, 20, 25, 30, 40])
    kmh = ms * 3.6
    if rng.random() < 0.5:
        return Draft(T(lang, f"Une vitesse de {ms} m/s vaut combien de km/h ?", f"A speed of {ms} m/s is how many km/h?"), N(lang, kmh, 1),
                     [N(lang, ms / 3.6, 1), N(lang, ms * 6, 1), N(lang, ms * 3, 1), N(lang, ms * 4, 1), N(lang, ms * 36, 1)],
                     T(lang, "1 m/s = 3,6 km/h.", "1 m/s = 3.6 km/h."), src=S(lang), cat=cat(lang, "Mécanique · 2nde", "Mechanics"))
    k = int(kmh) if kmh == int(kmh) else kmh
    return Draft(T(lang, f"Une vitesse de {N(lang, kmh, 1)} km/h vaut combien de m/s ?", f"A speed of {N(lang, kmh, 1)} km/h is how many m/s?"), N(lang, ms),
                 [N(lang, kmh * 3.6, 1), N(lang, kmh / 6, 1), N(lang, kmh / 2, 1), N(lang, kmh - 3.6, 1), N(lang, ms * 3.6 / 2, 1)],
                 T(lang, "On divise par 3,6.", "Divide by 3.6."), src=S(lang), cat=cat(lang, "Mécanique · 2nde", "Mechanics"))


@both(FR2 + FR1, "ph-weight", cap=600, cat="Mécanique", diffs=(1, 2))
def weight(rng, d, lang):
    m, g = rng.choice([2, 5, 8, 10, 12, 20, 25, 40, 60, 75]), rng.choice([10, 9.8, 1.6, 3.7, 24.8])
    names = {10: "sur Terre (g = 10 N/kg)", 9.8: "sur Terre (g = 9,8 N/kg)", 1.6: "sur la Lune (g = 1,6 N/kg)", 3.7: "sur Mars (g = 3,7 N/kg)", 24.8: "sur Jupiter (g = 24,8 N/kg)"}
    P = m * g
    wr = [m, m / g, m * 10 if g != 10 else m * 9.8, P * 2, m + g]
    return Draft(f"Quel est le poids d'un objet de masse {m} kg {names[g]} ?", f"{N('fr', P, 2)} N", [f"{N('fr', w, 2)} N" for w in wr],
                 f"P = m × g = {m} × {N('fr', g)} = {N('fr', P, 2)} N.", src=S("fr"), cat="Mécanique · 2nde, 1re")


@both(FR2, "ph-density", cap=600, cat="Mécanique", diffs=(2,))
def density(rng, d, lang):
    m, v = rng.choice([100, 200, 250, 300, 400, 500, 800, 1000]), rng.choice([10, 20, 25, 40, 50, 100, 125, 200])
    rho = m / v
    return Draft(f"Un objet de masse {m} g occupe un volume de {v} cm³. Quelle est sa masse volumique, en g/cm³ ?", N("fr", rho, 3),
                 [N("fr", v / m, 3), N("fr", m * v, 0), N("fr", rho * 10, 3), N("fr", rho / 10, 3), N("fr", m + v, 0)], "ρ = m/V.", src=S("fr"), cat="Mécanique · 2nde")


@both(EN_O, "ph-pressure", cap=600, cat="Mechanics", diffs=(2,))
def pressure(rng, d, lang):
    F, A = rng.choice([100, 200, 400, 500, 600, 800, 1000]), rng.choice([0.1, 0.2, 0.25, 0.5, 2, 4, 5])
    p = F / A
    return Draft(f"A force of {F} N acts on a surface of area {N('en', A)} m². What is the pressure, in pascals?", N("en", p, 2),
                 [N("en", F * A, 2), N("en", A / F, 4), N("en", p * 10, 2), N("en", p / 10, 2), N("en", F + A, 2)], "Pressure = force ÷ area.", src=SRC_EN, cat="Mechanics")


@both(EN_O, "ph-moments", cap=600, cat="Mechanics", diffs=(3,))
def moments(rng, d, lang):
    F, x = rng.choice([5, 10, 12, 20, 25, 30, 40]), rng.choice([0.2, 0.4, 0.5, 0.8, 1.2, 1.5, 2])
    # balanced beam: F1 x1 = F2 x2
    F2_ = rng.choice([5, 10, 20, 25, 50])
    x2 = F * x / F2_
    return Draft(f"A uniform beam balances on a pivot. A {F} N weight hangs {N('en', x)} m from the pivot on one side. A {F2_} N weight on the other side balances it. How far from the pivot is it, in metres?",
                 N("en", x2, 3), [N("en", F2_ * x / F, 3), N("en", F * x, 3), N("en", x + F / F2_, 3), N("en", x * 2, 3), N("en", x2 * 2, 3)],
                 "Principle of moments: clockwise moment = anticlockwise moment, so F₁x₁ = F₂x₂.", src=SRC_EN, cat="Mechanics")


@both(FR1 + FRT + EN_A, "ph-suvat-v", cap=600, cat="Mécanique", diffs=(2, 3))
def suvat_v(rng, d, lang):
    u, a, t = rng.randint(0, 20), rng.choice([1, 2, 3, 4, 5, 0.5, 2.5]), rng.randint(2, 12)
    v = u + a * t
    wr = [u - a * t, a * t, u * t + a * t * t / 2, u + a * t * t, (u + a) * t]
    return Draft(T(lang, f"Un mobile part avec une vitesse de {u} m/s et une accélération constante de {N(lang, a)} m/s². Quelle est sa vitesse après {t} s, en m/s ?",
                   f"A particle starts at {u} m/s with constant acceleration {N(lang, a)} m/s². What is its speed after {t} s, in m/s?"),
                 N(lang, v, 2), [N(lang, w, 2) for w in wr], "v = u + a t.", src=S(lang), cat=cat(lang, "Cinématique · 1re", "Kinematics"))


@both(FR1 + FRT + EN_A, "ph-suvat-s", cap=600, cat="Mécanique", diffs=(3, 4))
def suvat_s(rng, d, lang):
    u, a, t = rng.randint(0, 15), rng.choice([2, 4, 6, 1, 3]), rng.randint(2, 10)
    s = u * t + a * t * t / 2
    assert s == Fraction(2 * u * t + a * t * t, 2)
    wr = [u * t + a * t * t, (u + a * t) * t, u * t + a * t / 2, a * t * t / 2, u * t - a * t * t / 2]
    return Draft(T(lang, f"Un mobile part avec {u} m/s et une accélération constante de {a} m/s². Quelle distance parcourt-il en {t} s, en mètres ?",
                   f"A car moves with initial speed {u} m/s and constant acceleration {a} m/s². How far does it travel in {t} s, in metres?"),
                 N(lang, s, 2), [N(lang, w, 2) for w in wr], "s = ut + ½at².", src=S(lang), cat=cat(lang, "Cinématique · 1re", "Kinematics"))


@both(FR1 + EN_A, "ph-suvat-brake", cap=600, cat="Mécanique", diffs=(3, 4))
def suvat_brake(rng, d, lang):
    v, s = rng.choice([10, 15, 20, 25, 30]), rng.choice([20, 25, 40, 50, 75, 100])
    a = v * v / (2 * s)
    return Draft(T(lang, f"Un véhicule roulant à {v} m/s freine uniformément et s'arrête sur {s} m. Quelle est la valeur de son décélération, en m/s² ?",
                   f"A vehicle travelling at {v} m/s brakes uniformly and stops in {s} m. What is the magnitude of its deceleration, in m/s²?"),
                 N(lang, a, 3), [N(lang, v * v / s, 3), N(lang, v / s, 3), N(lang, v * v * s / 2, 3), N(lang, 2 * v * v / s, 3), N(lang, a * 2, 3)],
                 "v² = u² + 2as avec v = 0 : a = u²/(2s)." if lang == "fr" else "Use v² = u² + 2as with final speed 0: a = u²/(2s).", src=S(lang), cat=cat(lang, "Cinématique · 1re", "Kinematics"))


@both(FR1 + FRT + EN_A, "ph-free-fall-speed", cap=600, cat="Mécanique", diffs=(3,))
def free_fall_speed(rng, d, lang):
    h = rng.choice([5, 20, 45, 80, 125, 180, 245, 320])
    v = int(math.sqrt(2 * 10 * h))
    assert v * v == 20 * h
    return Draft(T(lang, f"Un objet est lâché sans vitesse initiale d'une hauteur de {h} m (g = 10 m/s², frottements négligés). Quelle est sa vitesse en arrivant au sol, en m/s ?",
                   f"An object is dropped from rest from a height of {h} m (g = 10 m/s², air resistance ignored). What is its speed on reaching the ground, in m/s?"),
                 str(v), [str(w) for w in (v // 2, 2 * v, h, 10 * h // v if 10 * h % v == 0 else v + 5, v + 10)],
                 T(lang, "v = √(2gh) : conservation de l'énergie mécanique.", "v = √(2gh) from conservation of energy."), src=S(lang), cat=cat(lang, "Énergie · 1re", "Energy"))


@both(FR1 + FRT + EN_A, "ph-work", cap=600, cat="Énergie", diffs=(3, 4))
def work(rng, d, lang):
    F, dd, ang = rng.choice([10, 20, 30, 50, 100, 150, 200]), rng.choice([2, 3, 5, 10, 15, 20]), rng.choice([0, 60, 90, 180])
    cos = {0: 1, 60: 0.5, 90: 0, 180: -1}[ang]
    W = F * dd * cos
    assert abs(F * dd * math.cos(math.radians(ang)) - W) < 1e-9
    wr = [F * dd * (1 - cos if ang in (60,) else 1), F * dd / 2 if ang != 60 else F * dd, F + dd, F * dd * 0.866, -W if W else F * dd * 2]
    return Draft(T(lang, f"Une force constante de {F} N déplace son point d'application de {dd} m ; l'angle entre la force et le déplacement est de {ang}°. Quel est son travail, en joules ?",
                   f"A constant force of {F} N moves its point of application {dd} m; the angle between force and displacement is {ang}°. What work does it do, in joules?"),
                 N(lang, W, 1), [N(lang, w, 1) for w in wr], "W = F·d·cos α.", src=S(lang), cat=cat(lang, "Énergie · 1re", "Energy"))


@both(FR1 + FRT + EN_A + EN_O, "ph-kinetic", cap=600, cat="Énergie", diffs=(2, 3))
def kinetic(rng, d, lang):
    m, v = rng.choice([0.5, 1, 2, 4, 5, 10, 20, 50, 1000]), rng.choice([2, 4, 5, 10, 15, 20, 30])
    ek = m * v * v / 2
    wr = [m * v, m * v * v, m * v / 2, ek * 2 if ek else 1, (m * v) ** 2 / 2]
    return Draft(T(lang, f"Quelle est l'énergie cinétique d'un objet de masse {N(lang, m)} kg lancé à {v} m/s, en joules ?", f"What is the kinetic energy of a {N(lang, m)} kg object moving at {v} m/s, in joules?"),
                 N(lang, ek, 2), [N(lang, w, 2) for w in wr], "Ec = ½ m v².", src=S(lang), cat=cat(lang, "Énergie", "Energy"))


@both(FR1 + FRT + EN_A + EN_O, "ph-power-work", cap=600, cat="Énergie", diffs=(3,))
def power_work(rng, d, lang):
    m, h, t = rng.choice([20, 50, 60, 100, 200]), rng.choice([2, 3, 5, 10, 20]), rng.choice([2, 4, 5, 10, 20])
    P = m * 10 * h / t
    return Draft(T(lang, f"Une grue soulève une charge de {m} kg à {h} m de hauteur en {t} s (g = 10 N/kg). Quelle puissance utile fournit-elle, en watts ?",
                   f"A crane lifts a {m} kg load through {h} m in {t} s (g = 10 N/kg). What useful power does it deliver, in watts?"),
                 N(lang, P, 2), [N(lang, w, 2) for w in (m * h / t, m * 10 * h, m * 10 * t / h, P * 2, P / 2)],
                 T(lang, "P = W/t avec W = m g h.", "Power = work done ÷ time, with work = m g h."), src=S(lang), cat=cat(lang, "Énergie", "Energy"))


@both(EN_O + EN_A, "ph-efficiency", cap=600, cat="Energy", diffs=(2, 3))
def efficiency(rng, d, lang):
    inp, eff = rng.choice([200, 400, 500, 800, 1000, 2000]), rng.choice([20, 25, 40, 50, 60, 75, 80])
    out = inp * eff / 100
    return Draft(f"A machine takes in {inp} J of energy and gives out {N('en', out, 1)} J of useful energy. What is its efficiency?", f"{eff} %",
                 [f"{100 - eff} %", f"{N('en', inp / out * 100, 1)} %" if inp != out else "125 %", f"{N('en', out, 1)} %", f"{eff * 2 if eff < 50 else eff // 2} %"], "Efficiency = useful energy out ÷ energy in × 100 %.", src=SRC_EN, cat="Energy")


@both(FR1 + FRT + EN_A, "ph-momentum", cap=600, cat="Mécanique", diffs=(4,))
def momentum(rng, d, lang):
    m1, v1, m2 = rng.choice([1, 2, 3, 4, 5]), rng.choice([2, 3, 4, 6, 8, 10]), rng.choice([1, 2, 3, 4, 5])
    p = m1 * v1
    v = Fraction(p, m1 + m2)
    wr = [Fraction(v1, 2), v1 - v, Fraction(p, m2), v1 + v, Fraction(p * 2, m1 + m2)]
    return Draft(T(lang, f"Un chariot de {m1} kg roulant à {v1} m/s heurte un chariot immobile de {m2} kg ; ils restent accrochés. Quelle est leur vitesse commune, en m/s ?",
                   f"A {m1} kg trolley moving at {v1} m/s collides with a stationary {m2} kg trolley and they stick together. What is their common speed, in m/s?"),
                 N(lang, float(v), 3), [N(lang, float(w), 3) for w in wr],
                 T(lang, "Conservation de la quantité de mouvement : m₁v₁ = (m₁ + m₂)v.", "Conservation of momentum: m₁v₁ = (m₁ + m₂)v."), src=S(lang), cat=cat(lang, "Mécanique · 1re", "Mechanics"))


@both(FR1 + EN_A, "ph-hooke", cap=600, cat="Mécanique", diffs=(2, 3))
def hooke(rng, d, lang):
    k, x = rng.choice([20, 40, 50, 80, 100, 200, 500]), rng.choice([0.01, 0.02, 0.05, 0.1, 0.15, 0.2])
    F = k * x
    ask_x = rng.random() < 0.4
    if ask_x:
        return Draft(T(lang, f"Un ressort de raideur {k} N/m exerce une force de {N(lang, F, 2)} N. De quelle longueur est-il allongé, en mètres ?",
                       f"A spring of stiffness {k} N/m exerts a force of {N(lang, F, 2)} N. What is its extension, in metres?"), N(lang, x, 3),
                     [N(lang, F * k, 3), N(lang, k / F, 3), N(lang, x * 10, 3), N(lang, x / 10, 3), N(lang, F - k, 3)], "F = k·x.", src=S(lang), cat=cat(lang, "Mécanique · 1re", "Mechanics"))
    return Draft(T(lang, f"Un ressort de raideur {k} N/m est allongé de {int(x * 100)} cm. Quelle force exerce-t-il, en newtons ?", f"A spring of stiffness {k} N/m is stretched by {int(x * 100)} cm. What force does it exert, in newtons?"),
                 N(lang, F, 2), [N(lang, k * x * 100, 2), N(lang, k / x, 2), N(lang, F * 10, 2), N(lang, F / 10, 2), N(lang, x / k, 4)],
                 T(lang, "F = k·x avec x en mètres.", "F = kx with the extension in metres."), src=S(lang), cat=cat(lang, "Mécanique · 1re", "Mechanics"))


@both(FR1, "ph-incline", cap=600, cat="Mécanique", diffs=(3,))
def incline(rng, d, lang):
    m, ang = rng.choice([2, 4, 5, 10, 20, 50]), rng.choice([30, 90])
    s = {30: 0.5, 90: 1.0}[ang]
    F = m * 10 * s
    return Draft(f"Un solide de {m} kg est posé sur un plan incliné de {ang}° (sans frottement, g = 10 N/kg). Quelle est la composante du poids parallèle au plan, en newtons ?" if ang != 90 else
                 f"Un solide de {m} kg glisse sur une paroi verticale parfaitement lisse (g = 10 N/kg). Quelle force le fait descendre, en newtons ?",
                 N("fr", F, 1), [N("fr", m * 10 * (1 - s) if s != 1 else m * 5, 1), N("fr", m * 10 * 0.866, 1), N("fr", m * s, 1), N("fr", F * 2, 1), N("fr", m * 10 * s * 0.866, 1)],
                 "Composante parallèle : P sin α.", src=S("fr"), cat="Mécanique · 1re")


# ================================================================================================ électricité
@both(FR2 + EN_O, "ph-resistors", cap=600, cat="Électricité", diffs=(2, 3))
def resistors(rng, d, lang):
    r1, r2 = rng.choice([10, 20, 30, 40, 60, 100, 120, 200]), rng.choice([10, 20, 30, 40, 60, 100, 120, 200])
    series = rng.random() < 0.5
    if series:
        R = r1 + r2
        wr = [r1 * r2 / (r1 + r2), abs(r1 - r2) or r1 + 1, r1 * r2, (r1 + r2) / 2]
        txt = T(lang, "en série", "in series")
    else:
        R = r1 * r2 / (r1 + r2)
        wr = [r1 + r2, abs(r1 - r2) or r1 + 1, (r1 + r2) / 2, r1 * r2]
        txt = T(lang, "en dérivation (parallèle)", "in parallel")
    return Draft(T(lang, f"Deux résistors de {r1} Ω et {r2} Ω sont montés {txt}. Quelle est la résistance équivalente, en ohms ?", f"Two resistors of {r1} Ω and {r2} Ω are connected {txt}. What is the combined resistance, in ohms?"),
                 N(lang, R, 2), [N(lang, w, 2) for w in wr], T(lang, "En série les résistances s'ajoutent ; en dérivation 1/R = 1/R₁ + 1/R₂.", "In series resistances add; in parallel 1/R = 1/R₁ + 1/R₂."),
                 src=S(lang), cat=cat(lang, "Électricité · 2nde", "Electricity"))


@both(FR2 + FR1 + EN_O, "ph-ohm", cap=600, cat="Électricité", diffs=(1, 2))
def ohm(rng, d, lang):
    R, I = rng.choice([5, 10, 20, 22, 47, 50, 100, 220, 470]), rng.choice([0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2])
    U = R * I
    ask = rng.choice(["U", "I", "R"])
    if ask == "U":
        return Draft(T(lang, f"Un résistor de {R} Ω est parcouru par un courant de {N(lang, I)} A. Quelle tension a-t-il à ses bornes, en volts ?", f"A {R} Ω resistor carries a current of {N(lang, I)} A. What is the potential difference across it, in volts?"),
                     N(lang, U, 3), [N(lang, w, 3) for w in (R / I, I / R, U * 10, U / 10, R + I)], "U = R·I.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))
    if ask == "I":
        return Draft(T(lang, f"Un résistor de {R} Ω est soumis à une tension de {N(lang, U, 3)} V. Quelle intensité le traverse, en ampères ?", f"A {R} Ω resistor has {N(lang, U, 3)} V across it. What current flows through it, in amperes?"),
                     N(lang, I, 3), [N(lang, w, 3) for w in (U * R, R / U, I * 10, I / 10, U - R)], "I = U/R.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))
    return Draft(T(lang, f"Un conducteur est parcouru par un courant de {N(lang, I)} A sous une tension de {N(lang, U, 3)} V. Quelle est sa résistance, en ohms ?", f"A conductor carries {N(lang, I)} A when the voltage across it is {N(lang, U, 3)} V. What is its resistance, in ohms?"),
                 N(lang, R), [N(lang, w, 3) for w in (U * I, I / U, R * 10, R / 10, U + I)], "R = U/I.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR2 + EN_O, "ph-energy-cost", cap=600, cat="Électricité", diffs=(2, 3))
def energy_cost(rng, d, lang):
    P, h, tariff = rng.choice([40, 60, 100, 500, 1000, 1500, 2000]), rng.choice([2, 4, 5, 8, 10, 12, 24]), rng.choice([50, 60, 75, 100])
    kwh = P * h / 1000
    cost = kwh * tariff
    if lang == "fr":
        return Draft(f"Un appareil de {P} W fonctionne {h} h. Le kilowattheure coûte {tariff} FCFA. Quel est le coût de cette consommation, en FCFA ?", fr(float(cost), 2, group=False),
                     [fr(float(w), 2, group=False) for w in (P * h * tariff / 100, kwh, P * h * tariff, cost * 10, cost / 10)], f"E = P × t = {fr(float(kwh), 3, group=False)} kWh, puis E × prix.", src=SRC_FR, cat="Électricité · 2nde")
    return Draft(f"A {P} W appliance runs for {h} hours. Electricity costs {tariff} FCFA per kWh. What is the cost, in FCFA?", N("en", cost, 2),
                 [N("en", w, 2) for w in (P * h * tariff / 100, kwh, P * h * tariff, cost * 10, cost / 10)], f"Energy = power × time = {N('en', kwh, 3)} kWh, then multiply by the price.", src=SRC_EN, cat="Electricity")


@both(FR1 + FRT + EN_O + EN_A, "ph-joule", cap=600, cat="Électricité", diffs=(3, 4))
def joule(rng, d, lang):
    R, I, t = rng.choice([5, 10, 20, 50, 100]), rng.choice([0.5, 1, 2, 3, 5]), rng.choice([10, 30, 60, 120, 600])
    P = R * I * I
    Q = P * t
    ask_p = rng.random() < 0.5
    if ask_p:
        return Draft(T(lang, f"Un résistor de {R} Ω est traversé par un courant de {N(lang, I)} A. Quelle puissance dissipe-t-il par effet Joule, en watts ?", f"A {R} Ω resistor carries {N(lang, I)} A. What power does it dissipate, in watts?"),
                     N(lang, P, 2), [N(lang, w, 2) for w in (R * I, R / I, R * I * I * I, P * 2, R * I * 2)], "P = R·I².", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))
    return Draft(T(lang, f"Un résistor de {R} Ω est traversé par {N(lang, I)} A pendant {t} s. Quelle énergie est dissipée sous forme de chaleur, en joules ?", f"A {R} Ω resistor carries {N(lang, I)} A for {t} s. How much energy is transferred as heat, in joules?"),
                 N(lang, Q, 2), [N(lang, w, 2) for w in (R * I * t, P, Q * 2, Q / t * 2, R * t)], "W = R·I²·t.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR1 + FRT + EN_A, "ph-emf", cap=600, cat="Électricité", diffs=(4,))
def emf(rng, d, lang):
    E_, r, R = rng.choice([6, 9, 12, 24]), rng.choice([0.5, 1, 2]), rng.choice([2, 4, 5, 10, 11.5])
    I = E_ / (R + r)
    U = E_ - r * I
    return Draft(T(lang, f"Une pile de force électromotrice {E_} V et de résistance interne {N(lang, r)} Ω alimente un résistor de {N(lang, R)} Ω. Quelle est l'intensité du courant, en ampères ?",
                   f"A cell of e.m.f. {E_} V and internal resistance {N(lang, r)} Ω is connected to a {N(lang, R)} Ω resistor. What current flows, in amperes?"),
                 N(lang, I, 3), [N(lang, w, 3) for w in (E_ / R, E_ / r, E_ * (R + r), U / r, E_ / (R - r) if R != r else 1)],
                 T(lang, "Loi de Pouillet : I = E/(R + r).", "I = E/(R + r)."), src=S(lang), cat=cat(lang, "Électricité · 1re", "Electricity"))


@both(FR1 + FRT + EN_A, "ph-capacitor", cap=600, cat="Électricité", diffs=(3, 4))
def capacitor(rng, d, lang):
    C, V = rng.choice([1, 2, 4.7, 10, 47, 100, 220]), rng.choice([3, 5, 6, 9, 12, 24])
    Q = C * V
    energy = 0.5 * C * V * V
    if rng.random() < 0.5:
        return Draft(T(lang, f"Un condensateur de {N(lang, C)} µF est chargé sous {V} V. Quelle charge porte-t-il, en microcoulombs ?", f"A {N(lang, C)} µF capacitor is charged to {V} V. What charge does it store, in microcoulombs?"),
                     N(lang, Q, 2), [N(lang, w, 2) for w in (C / V, Q * 2, energy, V / C, Q / 2)], "Q = C·V.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))
    return Draft(T(lang, f"Un condensateur de {N(lang, C)} µF est chargé sous {V} V. Quelle énergie stocke-t-il, en microjoules ?", f"A {N(lang, C)} µF capacitor is charged to {V} V. How much energy does it store, in microjoules?"),
                 N(lang, energy, 2), [N(lang, w, 2) for w in (C * V, C * V * V, energy * 2, energy / 2, 0.5 * C * V)], "E = ½·C·V².", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR1 + FRT + EN_A, "ph-laplace", cap=600, cat="Électromagnétisme", diffs=(4,))
def laplace(rng, d, lang):
    B, I, L, ang = rng.choice([0.05, 0.1, 0.2, 0.5, 1]), rng.choice([1, 2, 4, 5, 10]), rng.choice([0.1, 0.2, 0.5, 1]), rng.choice([90, 30])
    s = 1 if ang == 90 else 0.5
    F = B * I * L * s
    return Draft(T(lang, f"Un conducteur rectiligne de longueur {N(lang, L)} m parcouru par {I} A est placé dans un champ magnétique uniforme de {N(lang, B)} T ; l'angle entre le conducteur et le champ est {ang}°. Quelle est la force de Laplace, en newtons ?",
                   f"A straight wire {N(lang, L)} m long carrying {I} A lies in a uniform magnetic field of {N(lang, B)} T at {ang}° to the field. What is the force on it, in newtons?"),
                 N(lang, F, 4), [N(lang, w, 4) for w in (B * I * L * (0.5 if ang == 90 else 1), B * I / L, B * I * L * 0.866, F * 10, F / 10)], "F = B·I·L·sin α.", src=S(lang), cat=cat(lang, "Électromagnétisme", "Electromagnetism"))


# ================================================================================================ optique, ondes
@both(FR2 + FR1 + FRT + EN_A, "ph-lens", cap=600, cat="Optique", diffs=(4,))
def lens(rng, d, lang):
    f, p = rng.choice([5, 10, 12, 15, 20, 25, 30]), rng.choice([20, 30, 40, 45, 60, 75, 90])
    if p <= f:
        return None
    q = Fraction(f * p, p - f)
    wr = [Fraction(f * p, p + f), p - f, f + p, Fraction(p * p, f), Fraction(p, 2)]
    return Draft(T(lang, f"Une lentille convergente de distance focale {f} cm donne l'image d'un objet situé à {p} cm de la lentille. À quelle distance de la lentille se forme l'image, en cm ?",
                   f"A converging lens of focal length {f} cm forms an image of an object {p} cm from the lens. How far from the lens is the image, in cm?"),
                 N(lang, float(q), 2), [N(lang, float(w), 2) for w in wr], T(lang, "Relation de conjugaison : 1/OA' = 1/OA + 1/f' (distances algébriques).", "Lens equation: 1/v = 1/f − 1/u with real-is-positive distances: 1/f = 1/u + 1/v."),
                 src=S(lang), cat=cat(lang, "Optique", "Optics"))


@both(FR2 + FRT + EN_A, "ph-refraction", cap=600, cat="Optique", diffs=(4,))
def refraction(rng, d, lang):
    rows = [(45, 30, "√2", math.sqrt(2)), (60, 30, "√3", math.sqrt(3)), (30, 30, "1", 1.0), (90, 45, "√2", math.sqrt(2)), (90, 30, "2", 2.0), (60, 45, "√3/√2", math.sqrt(3) / math.sqrt(2))]
    i, r, ans, val = rng.choice(rows)
    assert abs(math.sin(math.radians(i)) / math.sin(math.radians(r)) - val) < 1e-9
    pool = [v for v in ("√2", "√3", "1", "2", "1/2", "√3/√2", "√2/2", "√3/2") if v != ans]
    return Draft(T(lang, f"Un rayon lumineux passe de l'air (n = 1) à un milieu transparent. L'angle d'incidence est {i}° et l'angle de réfraction {r}°. Quel est l'indice du milieu ?",
                   f"A ray of light passes from air (n = 1) into a transparent medium. The angle of incidence is {i}° and the angle of refraction is {r}°. What is the refractive index of the medium?"),
                 ans, pool, T(lang, "Loi de Snell-Descartes : sin i = n sin r.", "Snell's law: sin i = n sin r."), src=S(lang), cat=cat(lang, "Optique", "Optics"), diff=3)


@both(FR2 + FRT + EN_O, "ph-wave-speed", cap=600, cat="Ondes", diffs=(2,))
def wave_speed(rng, d, lang):
    f, lam = rng.choice([2, 5, 10, 50, 100, 200, 440, 500]), rng.choice([0.1, 0.2, 0.5, 1, 2, 5])
    v = f * lam
    return Draft(T(lang, f"Une onde de fréquence {f} Hz a pour longueur d'onde {N(lang, lam)} m. Quelle est sa célérité, en m/s ?", f"A wave has frequency {f} Hz and wavelength {N(lang, lam)} m. What is its speed, in m/s?"),
                 N(lang, v, 2), [N(lang, w, 2) for w in (f / lam, lam / f, v * 10, v / 10, f + lam)], "v = f × λ.", src=S(lang), cat=cat(lang, "Ondes", "Waves"))


@both(FRT + EN_A, "ph-young", cap=600, cat="Optique ondulatoire", diffs=(4, 5))
def young(rng, d, lang):
    lam, D, a = rng.choice([500, 600, 650, 550, 400]), rng.choice([1, 1.5, 2, 2.5, 3]), rng.choice([0.2, 0.5, 1, 2])
    i = lam * 1e-9 * D / (a * 1e-3)
    mm = i * 1e3
    return Draft(T(lang, f"Dans une expérience de fentes de Young, la lumière a pour longueur d'onde {lam} nm, les fentes sont distantes de {N(lang, a)} mm et l'écran est à {N(lang, D)} m. Quelle est l'interfrange, en mm ?",
                   f"In a Young's double-slit experiment the wavelength is {lam} nm, the slit separation is {N(lang, a)} mm and the screen is {N(lang, D)} m away. What is the fringe spacing, in mm?"),
                 N(lang, mm, 3), [N(lang, w, 3) for w in (mm * 10, mm / 10, lam * D / a / 1e6 * 2, a * D / (lam * 1e-6), mm * 2)], "i = λD/a.", src=S(lang), cat=cat(lang, "Optique ondulatoire · Tle", "Wave optics"))


@both(FRT + EN_A, "ph-photon", cap=600, cat="Physique moderne", diffs=(4,))
def photon(rng, d, lang):
    lam = rng.choice([400, 500, 600, 620, 700, 310, 248])
    E_eV = 1240 / lam                    # hc = 1240 eV·nm (rounded)
    return Draft(T(lang, f"Quelle est l'énergie d'un photon de longueur d'onde {lam} nm, en eV ? (on prendra hc = 1 240 eV·nm)", f"What is the energy of a photon of wavelength {lam} nm, in eV? (take hc = 1240 eV·nm)"),
                 N(lang, E_eV, 2), [N(lang, w, 2) for w in (E_eV * 1.6, lam / 1240, 1240 * lam / 1000, E_eV / 2, E_eV * 10)], "E = hc/λ.", src=S(lang), cat=cat(lang, "Physique moderne · Tle", "Modern physics"))


@both(EN_A, "ph-photoelectric", cap=600, cat="Modern physics", diffs=(3, 4))
def photoelectric(rng, d, lang):
    hf, phi = rng.choice([4.0, 4.5, 5.0, 6.0, 3.5, 5.5]), rng.choice([2.0, 2.3, 2.5, 3.0, 1.9])
    if hf <= phi:
        return None
    k = hf - phi
    return Draft(f"Photons of energy {N('en', hf)} eV fall on a metal of work function {N('en', phi)} eV. What is the maximum kinetic energy of the emitted electrons, in eV?", N("en", k, 2),
                 [N("en", w, 2) for w in (hf + phi, phi - hf if phi > hf else 0.0, hf / phi, hf * phi, k * 2)], "E_k(max) = hf − φ (Einstein's photoelectric equation).", src=SRC_EN, cat="Modern physics")


# ================================================================================================ mécanique Tle, nucléaire
@both(FRT + EN_A, "ph-projectile", cap=600, cat="Mécanique", diffs=(4, 5))
def projectile(rng, d, lang):
    v0, ang = rng.choice([10, 20, 30, 40]), rng.choice([30, 45, 60])
    sin2 = {30: Fraction(3, 4) ** 0 * Fraction(0), 45: 1, 60: 0}
    R = {45: Fraction(v0 * v0, 10), 30: Fraction(v0 * v0, 10) * Fraction(866, 1000), 60: Fraction(v0 * v0, 10) * Fraction(866, 1000)}
    ask = rng.choice(["H", "R"]) if ang == 45 else "H"
    if ask == "R":
        right = Fraction(v0 * v0, 10)
        assert abs(float(right) - v0 ** 2 * math.sin(math.radians(90)) / 10) < 1e-9
        return Draft(T(lang, f"Un projectile est lancé du sol à {v0} m/s avec un angle de 45° au-dessus de l'horizontale (g = 10 m/s², frottements négligés). Quelle est sa portée, en mètres ?",
                       f"A projectile is launched from the ground at {v0} m/s at 45° above the horizontal (g = 10 m/s², no air resistance). What is its horizontal range, in metres?"),
                     N(lang, float(right), 2), [N(lang, float(w), 2) for w in (right / 2, right * 2, right / 4, Fraction(v0, 10))], "R = v₀² sin(2α)/g, avec sin 90° = 1." if lang == "fr" else "R = v₀² sin(2α)/g with sin 90° = 1.", src=S(lang), cat=cat(lang, "Mécanique · Tle", "Mechanics"))
    s2 = {30: Fraction(1, 4), 45: Fraction(1, 2), 60: Fraction(3, 4)}[ang]
    assert abs(float(s2) - math.sin(math.radians(ang)) ** 2) < 1e-9
    H = Fraction(v0 * v0, 20) * s2
    return Draft(T(lang, f"Un projectile est lancé du sol à {v0} m/s avec un angle de {ang}° au-dessus de l'horizontale (g = 10 m/s², frottements négligés). Quelle altitude maximale atteint-il, en mètres ?",
                   f"A projectile is launched from the ground at {v0} m/s at {ang}° above the horizontal (g = 10 m/s², no air resistance). What maximum height does it reach, in metres?"),
                 N(lang, float(H), 3), [N(lang, float(w), 3) for w in (Fraction(v0 * v0, 20), H * 2, H / 2, Fraction(v0 * v0, 10) * s2 * 2 if s2 != Fraction(1, 2) else H * 4, H * 3)],
                 "H = (v₀ sin α)²/(2g)." if lang == "fr" else "H = (v₀ sin α)²/(2g).", src=S(lang), cat=cat(lang, "Mécanique · Tle", "Mechanics"))


@both(FRT + EN_A, "ph-nuclear-decay", cap=600, cat="Physique nucléaire", diffs=(3, 4))
def nuclear_decay(rng, d, lang):
    nuclides = {("uranium", "U"): (238, 92), ("radium", "Ra"): (226, 88), ("polonium", "Po"): (210, 84), ("radon", "Rn"): (222, 86), ("thorium", "Th"): (232, 90), ("plutonium", "Pu"): (239, 94)}
    (name, sym), (A, Z) = rng.choice(list(nuclides.items()))
    kind = rng.choice(["alpha", "beta-"])
    if kind == "alpha":
        A2, Z2 = A - 4, Z - 2
    else:
        A2, Z2 = A, Z + 1
    ask = rng.choice(["A", "Z"])
    right = A2 if ask == "A" else Z2
    what = T(lang, "le nombre de masse A", "the mass number A") if ask == "A" else T(lang, "le numéro atomique Z", "the atomic number Z")
    typ = T(lang, "alpha (émission d'un noyau d'hélium ⁴₂He)" if kind == "alpha" else "bêta moins (émission d'un électron)",
            "alpha (emission of a helium-4 nucleus)" if kind == "alpha" else "beta-minus (emission of an electron)")
    wr = [right + 2, right - 2, right + 4, right - 4, right + 1, right - 1]
    return Draft(T(lang, f"Un noyau {sym} de nombre de masse {A} et de numéro atomique {Z} subit une désintégration {typ}. Quel est {what} du noyau fils ?",
                   f"A nucleus of {name} ({sym}) with mass number {A} and atomic number {Z} undergoes {typ} decay. What is {what} of the daughter nucleus?"),
                 str(right), [str(w) for w in wr], T(lang, "Lois de conservation de Soddy : le nombre de masse et la charge se conservent.", "Mass number and charge are both conserved in the decay."),
                 src=S(lang), cat=cat(lang, "Physique nucléaire · Tle", "Nuclear physics"))


@both(FRT + EN_A, "ph-mass-defect", cap=600, cat="Physique nucléaire", diffs=(4,))
def mass_defect(rng, d, lang):
    dm = rng.choice([0.01, 0.02, 0.03, 0.05, 0.1, 0.2, 0.0304, 0.1])
    E_ = dm * 931.5
    return Draft(T(lang, f"Le défaut de masse d'un noyau est de {N(lang, dm)} u. Quelle énergie de liaison cela représente-t-il, en MeV ? (1 u correspond à 931,5 MeV)", f"The mass defect of a nucleus is {N(lang, dm)} u. What binding energy does it represent, in MeV? (1 u is equivalent to 931.5 MeV)"),
                 N(lang, E_, 2), [N(lang, w, 2) for w in (dm / 931.5, dm * 931.5 * 1000, dm * 9315, E_ / 2, dm + 931.5)], "E = Δm c² ; 1 u·c² = 931,5 MeV." if lang == "fr" else "E = Δm c², with 1 u·c² = 931.5 MeV.", src=S(lang), cat=cat(lang, "Physique nucléaire · Tle", "Nuclear physics"))


@both(FRT + EN_A, "ph-lc-frequency", cap=600, cat="Électricité", diffs=(5,))
def lc_frequency(rng, d, lang):
    L_mH, C_uF = rng.choice([(100, 10), (10, 10), (1, 1), (250, 10), (40, 25), (10, 1), (25, 4)])
    f = 1 / (2 * math.pi * math.sqrt(L_mH * 1e-3 * C_uF * 1e-6))
    return Draft(T(lang, f"Un circuit LC est formé d'une bobine de {L_mH} mH et d'un condensateur de {C_uF} µF. Quelle est sa fréquence propre, en hertz (arrondie à l'unité) ?", f"An LC circuit has a {L_mH} mH inductor and a {C_uF} µF capacitor. What is its natural frequency, in hertz (to the nearest integer)?"),
                 str(round(f)), [str(round(w)) for w in (f * 2 * math.pi, f / (2 * math.pi), f * 2, f / 2, f * math.sqrt(2))],
                 "f₀ = 1/(2π√(LC))." , src=S(lang), cat=cat(lang, "Électricité · Tle", "Electricity"))


@both(FR1 + EN_A, "ph-spring-period", cap=600, cat="Mécanique", diffs=(4,))
def spring_period(rng, d, lang):
    m, k = rng.choice([(0.25, 25), (1, 100), (0.1, 10), (0.4, 40), (0.5, 50), (1, 4), (2, 8), (0.01, 1)])
    T_ = 2 * math.pi * math.sqrt(m / k)
    return Draft(T(lang, f"Un oscillateur masse-ressort a une masse de {N(lang, m)} kg et un ressort de raideur {k} N/m. Quelle est sa période propre, en secondes (arrondie à 0,01) ?",
                   f"A mass-spring oscillator has mass {N(lang, m)} kg and spring constant {k} N/m. What is its period, in seconds (to 2 decimal places)?"),
                 N(lang, round(T_, 2), 2), [N(lang, round(w, 2), 2) for w in (T_ / (2 * math.pi), 2 * math.pi * m / k, 2 * math.pi * math.sqrt(k / m), math.sqrt(m / k), T_ * 2)], "T = 2π√(m/k).", src=S(lang), cat=cat(lang, "Mécanique · 1re", "Mechanics"))


# ================================================================================================ chaleur, gaz
@both(FR1 + FRT + EN_O + EN_A, "ph-heat", cap=600, cat="Thermique", diffs=(2, 3))
def heat(rng, d, lang):
    m, dT = rng.choice([0.1, 0.2, 0.5, 1, 2, 5]), rng.choice([5, 10, 20, 30, 50, 80])
    c = 4200
    Q = m * c * dT
    return Draft(T(lang, f"Quelle énergie faut-il pour chauffer {N(lang, m)} kg d'eau de {dT} °C ? (capacité thermique massique de l'eau : 4 200 J/(kg·°C))", f"How much energy is needed to heat {N(lang, m)} kg of water by {dT} °C? (specific heat capacity of water: 4200 J/(kg·°C))"),
                 f"{N(lang, Q, 0)} J", [f"{N(lang, w, 0)} J" for w in (m * dT, Q / 1000, Q * 10, m * c, c * dT / m)], "Q = m·c·ΔT.", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(FR1 + EN_A, "ph-latent", cap=600, cat="Thermique", diffs=(3,))
def latent(rng, d, lang):
    m = rng.choice([0.1, 0.2, 0.5, 1, 2])
    L = 334000
    Q = m * L / 1000
    return Draft(T(lang, f"Quelle énergie faut-il pour fondre {N(lang, m)} kg de glace à 0 °C ? (chaleur latente de fusion de la glace : 334 kJ/kg)", f"How much energy is needed to melt {N(lang, m)} kg of ice at 0 °C? (specific latent heat of fusion of ice: 334 kJ/kg)"),
                 f"{N(lang, Q, 1)} kJ", [f"{N(lang, w, 1)} kJ" for w in (Q * 1000, Q / 1000, Q * 4.2, m * 4.2 * 100, Q / 2)], "Q = m·L.", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(FR1 + FRT + EN_A, "ph-boyle", cap=600, cat="Thermique", diffs=(3, 4))
def boyle(rng, d, lang):
    p1, v1, v2 = rng.choice([1, 2, 3, 4, 5]), rng.choice([2, 4, 6, 8, 10, 12]), rng.choice([1, 2, 3, 4, 5, 6])
    p2 = Fraction(p1 * v1, v2)
    return Draft(T(lang, f"Un gaz parfait à température constante occupe {v1} L sous {p1} bar. Quelle est sa pression, en bar, s'il est comprimé à {v2} L ?", f"An ideal gas at constant temperature occupies {v1} L at {p1} bar. What is its pressure, in bar, when it is compressed to {v2} L?"),
                 N(lang, float(p2), 3), [N(lang, float(w), 3) for w in (Fraction(p1 * v2, v1), p1 + v1 - v2, Fraction(v1, v2), p1 * v1 * v2, p2 * 2)], "Loi de Boyle-Mariotte : P₁V₁ = P₂V₂." if lang == "fr" else "Boyle's law: P₁V₁ = P₂V₂.", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(FR1 + FRT + EN_A, "ph-ideal-gas", cap=600, cat="Thermique", diffs=(4,))
def ideal_gas(rng, d, lang):
    n, Tk, V = rng.choice([1, 2, 0.5, 0.1]), rng.choice([273, 300, 350, 400, 500]), rng.choice([10, 20, 25, 50])
    p = n * 8.31 * Tk / (V / 1000)
    return Draft(T(lang, f"Quelle est la pression de {N(lang, n)} mol de gaz parfait à {Tk} K dans {V} L ? (R = 8,31 J/(mol·K), réponse en kPa)", f"What is the pressure of {N(lang, n)} mol of ideal gas at {Tk} K in a {V} L container? (R = 8.31 J/(mol·K); answer in kPa)"),
                 N(lang, p / 1000, 1), [N(lang, w / 1000, 1) for w in (p, p / 1000 * 1000 * 1000, n * Tk * V, p * 2, p / 2)], "PV = nRT, avec V en m³." if lang == "fr" else "PV = nRT with V in m³.", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(EN_A, "ph-decay-halflife", cap=600, cat="Nuclear physics", diffs=(3, 4))
def halflife(rng, d, lang):
    N0, n = rng.choice([800, 1000, 1600, 2000, 4000]), rng.randint(1, 5)
    th = rng.choice([2, 3, 5, 10, 12])
    t = n * th
    rem = N0 / 2 ** n
    return Draft(f"A sample contains {N0} undecayed nuclei of an isotope with half-life {th} days. How many remain undecayed after {t} days?", N("en", rem, 2),
                 [N("en", w, 2) for w in (N0 - rem, N0 / 2 ** (n + 1), N0 / 2 ** (n - 1) if n > 1 else N0, N0 / n, N0 / t)], "After each half-life the number of undecayed nuclei halves.", src=SRC_EN, cat="Nuclear physics")
