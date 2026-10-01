"""Physique, deuxième série de modèles (fr 2nde / 1re / Tle et GCE en anglais) : lois de Newton, énergie potentielle, électricité,
ondes sonores, transformateur, poussée d'Archimède, mélange thermique, rendement. Valeurs données dans l'énoncé, réponse recalculée."""
import math
from fractions import Fraction

from ..core import Draft
from .lycee_common import N, T, both
from .lycee_phys import EN_A, EN_O, FR1, FR2, FRT, S, cat, rd


@both(FR2 + FR1 + EN_O + EN_A, "ph-newton2", cap=600, cat="Mécanique", diffs=(2, 3))
def newton2(rng, d, lang):
    m, a = rng.choice([1, 2, 4, 5, 10, 20, 50, 100, 500, 1000]), rng.choice([0.5, 1, 2, 2.5, 4, 5])
    F = m * a
    ask = rng.choice(["F", "a", "m"])
    if ask == "F":
        return Draft(T(lang, f"Quelle force résultante faut-il pour donner à un objet de {m} kg une accélération de {N(lang, a)} m/s² ?", f"What resultant force gives an object of mass {m} kg an acceleration of {N(lang, a)} m/s²?"),
                     f"{N(lang, F, 2)} N", [f"{N(lang, w, 2)} N" for w in (m / a, a / m, F * 10, F / 10, m + a)], "F = m·a.", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))
    if ask == "a":
        return Draft(T(lang, f"Une force résultante de {N(lang, F, 2)} N agit sur un objet de {m} kg. Quelle est son accélération, en m/s² ?", f"A resultant force of {N(lang, F, 2)} N acts on an object of mass {m} kg. What is its acceleration, in m/s²?"),
                     N(lang, a, 3), [N(lang, w, 3) for w in (F * m, m / F, a * 10, a / 10, F - m)], "a = F/m.", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))
    return Draft(T(lang, f"Une force résultante de {N(lang, F, 2)} N donne à un objet une accélération de {N(lang, a)} m/s². Quelle est sa masse, en kg ?", f"A resultant force of {N(lang, F, 2)} N gives an object an acceleration of {N(lang, a)} m/s². What is its mass, in kg?"),
                 N(lang, m, 2), [N(lang, w, 2) for w in (F * a, a / F, m * 10, m / 10, F - a)], "m = F/a.", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))


@both(FR1 + FRT + EN_O + EN_A, "ph-potential-energy", cap=600, cat="Énergie", diffs=(2, 3))
def potential_energy(rng, d, lang):
    m, h = rng.choice([0.5, 1, 2, 5, 10, 20, 50, 100]), rng.choice([2, 3, 5, 10, 20, 50, 100])
    ep = m * 10 * h
    return Draft(T(lang, f"Quelle est l'énergie potentielle de pesanteur d'un objet de {N(lang, m)} kg situé à {h} m au-dessus du sol (g = 10 N/kg), en joules ?", f"What is the gravitational potential energy of a {N(lang, m)} kg object {h} m above the ground (g = 10 N/kg), in joules?"),
                 N(lang, ep, 2), [N(lang, w, 2) for w in (m * h, m * 10, ep / 10, ep * 10, m * 10 * h / 2)], "Ep = m·g·h.", src=S(lang), cat=cat(lang, "Énergie", "Energy"))


@both(FR2 + FR1 + EN_O, "ph-electric-power", cap=600, cat="Électricité", diffs=(1, 2, 3))
def electric_power(rng, d, lang):
    U, I = rng.choice([6, 9, 12, 24, 110, 220]), rng.choice([0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10])
    P = U * I
    ask = rng.choice(["P", "I"])
    if ask == "P":
        return Draft(T(lang, f"Un appareil fonctionne sous {U} V et consomme un courant de {N(lang, I)} A. Quelle est sa puissance, en watts ?", f"An appliance runs on {U} V and draws a current of {N(lang, I)} A. What is its power, in watts?"),
                     N(lang, P, 2), [N(lang, w, 2) for w in (U / I, I / U, P * 10, P / 10, U + I)], "P = U·I.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))
    return Draft(T(lang, f"Un appareil de {N(lang, P, 2)} W est branché sous {U} V. Quelle intensité consomme-t-il, en ampères ?", f"A {N(lang, P, 2)} W appliance is connected to {U} V. What current does it draw, in amperes?"),
                 N(lang, I, 3), [N(lang, w, 3) for w in (P * U, U / P, I * 10, I / 10, P - U)], "I = P/U.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR2 + FR1 + EN_O + EN_A, "ph-charge", cap=600, cat="Électricité", diffs=(2, 3))
def charge(rng, d, lang):
    I, t = rng.choice([0.1, 0.2, 0.5, 1, 2, 5]), rng.choice([10, 20, 30, 60, 120, 300, 600])
    Q = I * t
    return Draft(T(lang, f"Un courant de {N(lang, I)} A circule pendant {t} s. Quelle charge électrique a traversé le circuit, en coulombs ?", f"A current of {N(lang, I)} A flows for {t} s. How much charge passes through the circuit, in coulombs?"),
                 N(lang, Q, 2), [N(lang, w, 2) for w in (I / t, t / I, Q * 10, Q / 10, I + t)], "Q = I·t.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR2 + FR1 + EN_O + EN_A, "ph-period-frequency", cap=600, cat="Ondes", diffs=(1, 2, 3))
def period_frequency(rng, d, lang):
    f = rng.choice([0.5, 1, 2, 4, 5, 10, 20, 25, 50, 100, 200, 500])
    Tp = 1 / f
    if rng.random() < 0.5:
        return Draft(T(lang, f"Un phénomène périodique a une fréquence de {N(lang, f)} Hz. Quelle est sa période, en secondes ?", f"A periodic phenomenon has a frequency of {N(lang, f)} Hz. What is its period, in seconds?"),
                     N(lang, Tp, 4), [N(lang, w, 4) for w in (f, f * 2, Tp * 10, Tp / 10, 2 * math.pi / f)], "T = 1/f.", src=S(lang), cat=cat(lang, "Ondes", "Waves"))
    return Draft(T(lang, f"Un phénomène périodique a une période de {N(lang, Tp, 4)} s. Quelle est sa fréquence, en hertz ?", f"A periodic phenomenon has a period of {N(lang, Tp, 4)} s. What is its frequency, in hertz?"),
                 N(lang, f, 3), [N(lang, w, 3) for w in (Tp, f * 2, f * 10, f / 10, 1 / (2 * Tp))], "f = 1/T.", src=S(lang), cat=cat(lang, "Ondes", "Waves"))


@both(FR2 + FR1 + EN_O + EN_A, "ph-echo", cap=600, cat="Ondes", diffs=(3, 4))
def echo(rng, d, lang):
    v, t = 340, rng.choice([0.2, 0.4, 0.5, 1, 1.5, 2, 3, 4, 5])
    dist = v * t / 2
    return Draft(T(lang, f"Un écho est entendu {N(lang, t)} s après l'émission d'un cri. La vitesse du son dans l'air est de 340 m/s. À quelle distance se trouve l'obstacle, en mètres ?", f"An echo is heard {N(lang, t)} s after a shout. The speed of sound in air is 340 m/s. How far away is the obstacle, in metres?"),
                 N(lang, dist, 2), [N(lang, w, 2) for w in (v * t, dist * 2 if dist * 2 != v * t else dist * 3, v / t, dist / 2, t / v)], "Le son fait un aller-retour : d = v·t/2." if lang == "fr" else "The sound travels there and back: d = v·t/2.", src=S(lang), cat=cat(lang, "Ondes", "Waves"))


@both(FR1 + FRT + EN_A, "ph-transformer", cap=600, cat="Électricité", diffs=(3, 4))
def transformer(rng, d, lang):
    Np, Ns, Vp = rng.choice([100, 200, 500, 1000, 2000]), rng.choice([10, 20, 50, 100, 200, 400, 1000, 4000]), rng.choice([12, 24, 110, 220, 230])
    Vs = Vp * Ns / Np
    return Draft(T(lang, f"Un transformateur idéal a {Np} spires au primaire et {Ns} au secondaire ; la tension au primaire est {Vp} V. Quelle est la tension au secondaire, en volts ?", f"An ideal transformer has {Np} turns on the primary and {Ns} on the secondary; the primary voltage is {Vp} V. What is the secondary voltage, in volts?"),
                 N(lang, Vs, 2), [N(lang, w, 2) for w in (Vp * Np / Ns, Vp + Ns - Np, Vs * 10, Vs / 10, Vp * Ns)], "Vs/Vp = Ns/Np.", src=S(lang), cat=cat(lang, "Électricité", "Electricity"))


@both(FR1 + FRT + EN_O + EN_A, "ph-archimedes", cap=600, cat="Mécanique", diffs=(3, 4))
def archimedes(rng, d, lang):
    rho, V = rng.choice([(1000, "eau"), (1000, "eau"), (800, "huile"), (1200, "eau salée")]), rng.choice([0.001, 0.002, 0.005, 0.01, 0.02, 0.05])
    r, name = rho
    F = r * V * 10
    liquid = {"eau": ("l'eau", "water"), "huile": ("l'huile", "oil"), "eau salée": ("l'eau salée", "salt water")}[name]
    return Draft(T(lang, f"Un objet de volume {N(lang, V * 1000, 3)} L est entièrement immergé dans {liquid[0]} (masse volumique {r} kg/m³ ; g = 10 N/kg). Quelle est la poussée d'Archimède, en newtons ?", f"An object of volume {N(lang, V * 1000, 3)} litres is fully submerged in {liquid[1]} (density {r} kg/m³; g = 10 N/kg). What is the upthrust, in newtons?"),
                 N(lang, F, 2), [N(lang, w, 2) for w in (r * V, F * 10, F / 10, r * V * 1000, F * 2)], "Poussée = ρ·V·g (poids du fluide déplacé)." if lang == "fr" else "Upthrust = ρ·V·g (weight of fluid displaced).", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))


@both(FR1 + FRT + EN_O + EN_A, "ph-heat-mixing", cap=600, cat="Thermique", diffs=(3, 4))
def heat_mixing(rng, d, lang):
    m1, T1, m2, T2 = rng.choice([1, 2, 3, 4]), rng.choice([10, 20, 25, 30]), rng.choice([1, 2, 3, 4]), rng.choice([50, 60, 70, 80, 90])
    Tf = (m1 * T1 + m2 * T2) / (m1 + m2)
    return Draft(T(lang, f"On mélange {m1} kg d'eau à {T1} °C et {m2} kg d'eau à {T2} °C (pas de perte de chaleur). Quelle est la température finale, en °C ?", f"{m1} kg of water at {T1} °C is mixed with {m2} kg of water at {T2} °C (no heat lost). What is the final temperature, in °C?"),
                 N(lang, Tf, 2), [N(lang, w, 2) for w in ((T1 + T2) / 2, T1 + T2, Tf + 5, Tf - 5, abs(T2 - T1))], "m₁(Tf − T₁) = m₂(T₂ − Tf)." if lang == "fr" else "Heat gained = heat lost: m₁(Tf − T₁) = m₂(T₂ − Tf).", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(FR1 + FRT + EN_O + EN_A, "ph-specific-heat", cap=600, cat="Thermique", diffs=(3, 4))
def specific_heat(rng, d, lang):
    m, dT, c = rng.choice([0.1, 0.2, 0.5, 1, 2]), rng.choice([10, 20, 40, 50, 80]), rng.choice([(385, "cuivre", "copper"), (900, "aluminium", "aluminium"), (450, "fer", "iron"), (4200, "eau", "water")])
    Q = m * c[0] * dT
    return Draft(T(lang, f"Il faut {N(lang, Q, 0)} J pour chauffer de {dT} °C un bloc de {c[1]} de {N(lang, m)} kg. Quelle est la capacité thermique massique du {c[1]}, en J/(kg·°C) ?", f"It takes {N(lang, Q, 0)} J to heat a {N(lang, m)} kg block of {c[2]} by {dT} °C. What is the specific heat capacity of {c[2]}, in J/(kg·°C)?"),
                 N(lang, c[0], 1), [N(lang, w, 1) for w in (Q * m * dT, Q / m, Q / dT, c[0] * 10, c[0] / 10)], "c = Q/(m·ΔT).", src=S(lang), cat=cat(lang, "Thermique", "Thermal physics"))


@both(FR2 + FR1 + FRT + EN_A, "ph-lens-magnification", cap=600, cat="Optique", diffs=(4, 5))
def lens_magnification(rng, d, lang):
    f, p = rng.choice([5, 10, 12, 15, 20]), rng.choice([20, 30, 40, 45, 60])
    if p <= f:
        return None
    q = Fraction(f * p, p - f)
    g = q / p
    return Draft(T(lang, f"Une lentille convergente de distance focale {f} cm donne l'image d'un objet situé à {p} cm. Quel est le grandissement (en valeur absolue) ?", f"A converging lens of focal length {f} cm forms an image of an object {p} cm away. What is the magnification (absolute value)?"),
                 N(lang, float(g), 3), [N(lang, float(w), 3) for w in (Fraction(p, q), q - p, q * p, g * 2, g + 1)], "Grandissement = |OA'/OA|.", src=S(lang), cat=cat(lang, "Optique", "Optics"))


@both(FRT + EN_A, "ph-photon-frequency", cap=600, cat="Physique moderne", diffs=(3, 4))
def photon_frequency(rng, d, lang):
    lam = rng.choice([300, 400, 500, 600, 750, 1000])
    f = 3e8 / (lam * 1e-9)
    return Draft(T(lang, f"Quelle est la fréquence d'une onde lumineuse de longueur d'onde {lam} nm dans le vide ? (c = 3,0 × 10⁸ m/s ; réponse en 10¹⁴ Hz)", f"What is the frequency of light of wavelength {lam} nm in a vacuum? (c = 3.0 × 10⁸ m/s; answer in units of 10¹⁴ Hz)"),
                 N(lang, f / 1e14, 2), [N(lang, w, 2) for w in (f / 1e12, f / 1e15, lam / 3, 3 / lam, f / 1e14 * 2)], "f = c/λ.", src=S(lang), cat=cat(lang, "Physique moderne", "Modern physics"))


@both(FR1 + FRT + EN_A, "ph-circular", cap=600, cat="Mécanique", diffs=(3, 4))
def circular(rng, d, lang):
    v, r = rng.choice([2, 4, 5, 10, 15, 20, 30]), rng.choice([1, 2, 4, 5, 10, 25, 50, 100])
    a = v * v / r
    return Draft(T(lang, f"Un mobile décrit un cercle de rayon {r} m à la vitesse constante de {v} m/s. Quelle est son accélération centripète, en m/s² ?", f"A body moves in a circle of radius {r} m at a constant speed of {v} m/s. What is its centripetal acceleration, in m/s²?"),
                 N(lang, a, 3), [N(lang, w, 3) for w in (v / r, v * r, v * v * r, a * 2, a / 2)], "a = v²/r.", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))


@both(FR2 + EN_O, "ph-lever", cap=600, cat="Mécanique", diffs=(2, 3))
def lever(rng, d, lang):
    F, d1, d2 = rng.choice([10, 20, 40, 50, 100]), rng.choice([0.5, 1, 1.5, 2]), rng.choice([0.25, 0.5, 1, 4])
    load = F * d1 / d2
    return Draft(T(lang, f"Un levier est en équilibre : une force de {F} N s'exerce à {N(lang, d1)} m du pivot ; à quelle charge, placée à {N(lang, d2)} m du pivot de l'autre côté, fait-elle équilibre, en newtons ?", f"A lever balances: a force of {F} N acts {N(lang, d1)} m from the pivot. What load, placed {N(lang, d2)} m from the pivot on the other side, balances it, in newtons?"),
                 N(lang, load, 2), [N(lang, w, 2) for w in (F * d2 / d1, F * d1 * d2, F + d1 / d2, load * 2, load / 2)], "Moments égaux : F₁·d₁ = F₂·d₂." if lang == "fr" else "Equal moments: F₁d₁ = F₂d₂.", src=S(lang), cat=cat(lang, "Mécanique", "Mechanics"))
