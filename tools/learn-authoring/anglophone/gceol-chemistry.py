"""Generator for content/learn/gceol-chemistry (GCE O Level Chemistry, Form 5, English). Run: python3 gceol-chemistry.py"""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

# Relative atomic masses used throughout (stated in the lessons): H 1, C 12, N 14, O 16, Na 23, Mg 24, Al 27, S 32, Cl 35.5, K 39, Ca 40, Fe 56, Cu 64, Zn 65, Ag 108
AR = {"H": 1, "C": 12, "N": 14, "O": 16, "Na": 23, "Mg": 24, "Al": 27, "Si": 28, "P": 31, "S": 32, "Cl": 35.5, "K": 39, "Ca": 40, "Fe": 56, "Cu": 64, "Zn": 65, "Br": 80, "Ag": 108, "Pb": 207}
MOLAR_VOL = 24  # dm3 per mole at room temperature and pressure, stated in the lessons

def Mr(**atoms):
    return sum(AR[k] * v for k, v in atoms.items())
def r(x, n=2): return round(x, n)
def close(a, b, t=1e-9): assert abs(a - b) <= t, (a, b)

p = Pack("gceol-chemistry", "Chemistry — GCE O Level", level="Form 5", subject="chemistry", cursus="secondary", exam="GCE-OL",
         description="Key-point summaries, worked calculations, GCE O Level-style exercises with answers and a mock exam: particles, bonding, the mole, acids and salts, electrolysis, rates, metals, non-metals and organic chemistry with Cameroonian examples.",
         programRef="Cameroon GCE Board — Ordinary Level Chemistry syllabus (Forms 4–5) — to be checked against the official texts")

def particles_box(x, y, w, h, pts, rr=6, fill="blue"):
    return [RECT(x, y, w, h)] + [CIRCLE(px, py, rr, fill=fill) for px, py in pts]

def box_arrow(x1, y, x2, label=None):
    return [LINE(x1, y, x2, y, arrow="end", width=2)] + ([T((x1 + x2) // 2, y - 8, label, 12)] if label else [])

_POLY = POLY
def POLY(pts, **kw):
    flat = [c for q in pts for c in (q if isinstance(q, (list, tuple)) else [q])]
    return _POLY(flat, **kw)

# =====================================================================  1. PARTICLES
ch = p.chapter("particles", "Particulate nature of matter", programRef="GCE O Level Chemistry — Kinetic particle theory, states of matter, diffusion (to be checked against the official syllabus)")
L = ch.lesson("states-diffusion", "States of matter, particles and diffusion", minutes=25,
    objectives=["Describe the arrangement and motion of particles in solids, liquids and gases.", "Name the changes of state and explain them with the particle model.", "Explain diffusion and why lighter particles diffuse faster.", "Interpret melting and boiling points to decide the state of a substance."],
    notes=["Boiling point of nitrogen −196 °C (77 K) is a standard value.", "In the ammonia/hydrogen chloride experiment the white ring forms nearer the hydrogen chloride end: standard result."])
L.key("definition", "The particle model", "All matter is made of tiny **particles** (atoms, molecules or ions) that are always moving. In a **solid** the particles are close together in a regular pattern and only **vibrate** about fixed positions. In a **liquid** they are close but irregular and can **slide past** each other. In a **gas** they are far apart, move **randomly and fast**, and fill their container.")
L.key("retenir", "Changes of state", "**Melting** (solid → liquid), **freezing** (liquid → solid), **boiling/evaporation** (liquid → gas), **condensation** (gas → liquid), **sublimation** (solid → gas directly, e.g. iodine). Heating gives particles more energy; at the melting or boiling point the energy overcomes the forces between particles, so the **temperature stays constant** during the change.")
L.key("methode", "Diffusion", "**Diffusion** is the spreading of particles from a region of high concentration to low concentration, by random movement. It is fast in gases, slow in liquids, almost nil in solids. At the same temperature **lighter particles diffuse faster**. Example: ammonia (NH₃) and hydrogen chloride (HCl) gases meet in a tube and form a white ring of ammonium chloride **closer to the HCl end**, because the lighter NH₃ travels further in the same time.")
L.key("pieges", "Watch out", "- The particles themselves do **not** expand when a substance is heated; they move faster or further apart.\n- Dissolving is not melting.\n- A substance is liquid at a given temperature only if that temperature is **between** its melting and boiling points.")
sol = [(30 + 24 * i, 60 + 24 * j) for i in range(4) for j in range(4)]
liq = [(165, 70), (190, 85), (210, 72), (178, 105), (205, 112), (230, 90), (170, 135), (195, 140), (222, 130), (240, 112), (160, 100), (232, 140)]
gas = [(285, 75), (350, 100), (310, 135), (375, 70), (330, 60), (292, 135), (355, 140)]
L.illustration(shapes([*particles_box(20, 50, 100, 100, sol), *particles_box(150, 50, 100, 100, liq), *particles_box(275, 50, 110, 100, gas),
                       T(70, 175, "solid", 14), T(200, 175, "liquid", 14), T(330, 175, "gas", 14)], 400, 220),
    "Particle arrangement in a solid (regular, close), a liquid (close, irregular) and a gas (far apart).", "Three boxes of circles: a regular grid for the solid, close random circles for the liquid and few widely spaced circles for the gas.")
L.example("Example 1 — deciding the state", "A substance melts at −20 °C and boils at 80 °C. What is its state at 25 °C? At 100 °C?", ["At 25 °C: this is above the melting point (−20 °C) and below the boiling point (80 °C), so the substance is a **liquid**.", "At 100 °C: this is above the boiling point, so it is a **gas**."], "Liquid at 25 °C; gas at 100 °C")
L.example("Example 2 — which diffuses faster?", "Cotton wool with ammonia solution is placed at one end of a long glass tube and cotton wool with hydrochloric acid at the other end. Where does the white ring form and why?", ["Ammonia gas (NH₃) is lighter than hydrogen chloride gas (HCl), so its particles diffuse faster and travel further.", "The gases meet nearer the HCl end, where white ammonium chloride (NH₄Cl) forms: NH₃ + HCl → NH₄Cl."], "Nearer the hydrochloric acid end")
L.mcq("Which of these gives the best evidence that particles of a gas move randomly?", "A drop of bromine fills a jar with brown vapour without stirring", ["Ice melts when heated", "A solid has a fixed shape", "Water boils at 100 °C"], "Spreading by itself (diffusion) shows the particles move at random.")
L.tf("During boiling of pure water the temperature keeps rising.", False, "The temperature stays constant at the boiling point while the energy breaks the forces between particles.")
n = -196 + 273; assert n == 77
L.num("Nitrogen boils at −196 °C. Express this in kelvin (add 273).", n, "T = −196 + 273 = 77 K.", unit="K")
L.mcq("Which gas diffuses fastest at the same temperature?", "Hydrogen (H₂)", ["Oxygen (O₂)", "Carbon dioxide (CO₂)", "Chlorine (Cl₂)"], "H₂ has the smallest mass of these molecules, so it moves fastest on average.", tier="approfondissement")
L.open("Explain, using the particle model, why a gas can be compressed but a liquid cannot easily be.", "In a gas the particles are far apart with a lot of empty space, so they can be pushed closer together. In a liquid the particles are already touching, so there is almost no space to compress.", "1 mark: gas particles far apart / empty space; 1 mark: liquid particles already close together.", tier="approfondissement")
L.problem("The table gives data for four substances: P (melts −7 °C, boils 59 °C); Q (melts 801 °C, boils 1413 °C); R (melts −219 °C, boils −183 °C); S (melts 0 °C, boils 100 °C). Room temperature is 25 °C.", [
    part_mcq("Which substance is a gas at room temperature?", "R", ["P", "Q", "S"], "R boils at −183 °C, far below 25 °C, so it is a gas."),
    part_mcq("Which substance is a solid at room temperature?", "Q", ["P", "R", "S"], "Q melts at 801 °C, so it is solid at 25 °C."),
    part_mcq("Which substance is liquid at 25 °C?", "Both P and S", ["Only P", "Only Q", "Only R"], "25 °C lies between the melting and boiling points of both P and S."),
    part_open("Explain why the temperature of S stays at 100 °C while it boils.", "The heat energy supplied is used to separate the particles (overcome the forces between them) rather than to raise their average kinetic energy.", "1 mark: energy used to separate particles; 1 mark: so the temperature does not rise.")], tier="examen")
L.sc("In a gas the particles are", "far apart and move randomly", ["close and fixed", "touching in a regular pattern", "stationary"], "That is the particle model of a gas.")
L.sc("Condensation is the change from", "gas to liquid", ["liquid to gas", "solid to liquid", "solid to gas"], "Gas particles cool and come together.")
L.sc("Diffusion is fastest in", "gases", ["solids", "liquids", "metals"], "Gas particles move freely.")
L.sc("A substance that changes from solid directly to gas is said to", "sublime", ["melt", "condense", "freeze"], "Example: iodine on heating.")
L.sc("Heating a solid makes its particles", "vibrate more", ["stop moving", "become larger", "disappear"], "They gain kinetic energy.")

# =====================================================================  2. SEPARATION
ch = p.chapter("separation", "Elements, mixtures, compounds and separation", programRef="GCE O Level Chemistry — Experimental techniques: purity, separation methods, chromatography (to be checked against the official syllabus)")
L = ch.lesson("separation-techniques", "Mixtures, purity and separation methods", minutes=30,
    objectives=["Distinguish elements, compounds and mixtures.", "Choose a separation method from the properties of the substances.", "Describe filtration, evaporation, crystallisation, distillation and chromatography.", "Calculate Rf values and use melting/boiling points as tests of purity."],
    notes=["Boiling points used: ethanol 78 °C, water 100 °C (standard). Salt-making from sea water is described generally; no production figures are given."])
L.key("definition", "Elements, compounds, mixtures", "An **element** has only one kind of atom. A **compound** has two or more elements **chemically combined** in a fixed ratio and has different properties from its elements (sodium + chlorine → sodium chloride). A **mixture** has substances **not chemically combined**; each keeps its own properties and they can be separated physically. A **pure** substance has a sharp, fixed melting and boiling point.")
L.key("methode", "Choosing a method", "- **Filtration**: insoluble solid from liquid (sand from water).\n- **Evaporation / crystallisation**: dissolved solid from solution (salt from sea water; crystallise slowly for large crystals).\n- **Simple distillation**: solvent from solution (pure water from salt water).\n- **Fractional distillation**: liquids with different boiling points (ethanol from water; crude oil).\n- **Separating funnel**: immiscible liquids (oil and water).\n- **Chromatography**: coloured substances (inks, dyes).\n- **Magnet**: iron from a non-magnetic solid.")
L.formula(r"R_f = \frac{\text{distance moved by the spot}}{\text{distance moved by the solvent}}", "Rf is always between 0 and 1 and has no unit")
L.key("attention", "Distillation set-up", "In **simple distillation** the solution is boiled, the vapour passes through a **condenser** (cooling water enters at the **bottom** and leaves at the top) and the pure liquid is collected. The thermometer bulb is at the **side-arm opening** to read the vapour temperature. Chromatography paper must be marked with a **pencil** line (ink would run) and the start line must be **above** the solvent level.")
L.illustration(shapes([CIRCLE(70, 150, 40), RECT(60, 90, 20, 30), LINE(80, 100, 290, 60, width=3), LINE(80, 112, 290, 72, width=3), RECT(160, 62, 80, 36), LINE(300, 66, 310, 160, width=2), RECT(285, 160, 50, 40),
                       T(70, 215, "salt solution", 12), T(200, 45, "condenser", 12), T(340, 218, "pure water", 12)], 400, 240),
    "Simple distillation: the solution is boiled, the vapour is cooled in the condenser and pure water is collected.", "A round flask on the left connected by a tube through a condenser to a collecting beaker on the right.")
L.example("Example 1 — salt and sand", "Describe how to obtain dry sand and solid salt from a mixture of the two.", ["Add water and stir: salt dissolves, sand does not. Filter: sand stays on the filter paper (wash and dry it).", "Heat the filtrate (salt solution) in an evaporating dish until crystals start to form, then leave to crystallise and dry the crystals."], "Dissolve, filter, evaporate the filtrate")
rf = 3.6 / 9.0; assert abs(rf - 0.4) < 1e-9
L.example("Example 2 — chromatography", "In a chromatogram a dye spot has moved 3.6 cm while the solvent front has moved 9.0 cm. Calculate its Rf value. Another dye has Rf = 0.8: where is it?", ["Rf = 3.6 ÷ 9.0 = 0.40.", "A spot with Rf = 0.8 moves 0.8 × 9.0 = 7.2 cm, so it lies higher up the paper than the first spot."], "Rf = 0.40; the other spot is at 7.2 cm")
L.mcq("Which method separates pure water from sea water?", "Simple distillation", ["Filtration", "Chromatography", "Using a magnet"], "The water evaporates and condenses; the salt stays behind.")
L.num("A spot moves 4.5 cm and the solvent front 6.0 cm. Calculate Rf.", 0.75, "Rf = 4.5 ÷ 6.0 = 0.75.", tolerance=0.01)
L.match("Match each mixture with a suitable separation method.", [["sand and water", "filtration"], ["ethanol and water", "fractional distillation"], ["black ink colours", "chromatography"], ["oil and water", "separating funnel"]], "Each method fits the property difference of the components.")
L.mcq("A pure solid is heated and melts over a range from 78 °C to 85 °C. This shows that the solid is", "impure", ["pure", "an element", "a gas"], "A pure substance melts at one sharp temperature; a range shows impurities.", tier="approfondissement")
L.open("Explain how you would separate a mixture of ethanol (b.p. 78 °C) and water (b.p. 100 °C).", "Use fractional distillation: heat the mixture; ethanol, with the lower boiling point, boils off first and rises up the fractionating column, condenses in the condenser and is collected at about 78 °C. Water, with the higher boiling point, stays in the flask.", "1 mark: fractional distillation; 1 mark: ethanol boils first (lower b.p.); 1 mark: collect at about 78 °C.", tier="approfondissement", difficulty=2)
L.problem("A pupil in Limbe wants to obtain salt from sea water and also test the purity of a green food dye.", [
    part_mcq("Which technique gives solid salt from sea water?", "Evaporation (then crystallisation)", ["Filtration only", "Chromatography", "Fractional distillation"], "Heating drives off the water and leaves the solid salt."),
    part_num("In the chromatogram the dye separates into a yellow spot at 2.4 cm and a blue spot at 4.8 cm; the solvent front is at 8.0 cm. Find the Rf of the blue spot.", 4.8 / 8.0, "Rf = 4.8 ÷ 8.0 = 0.60.", tolerance=0.01),
    part_mcq("What does the chromatogram show about the green dye?", "It is a mixture of at least two dyes", ["It is a pure compound", "It is an element", "It is a gas"], "Two spots mean two dyes."),
    part_open("Why must the start line be drawn in pencil?", "Pencil is insoluble and will not run with the solvent; ink would dissolve and spread, confusing the result.", "1 mark: pencil does not dissolve/move; 1 mark: ink would run.")], tier="examen")
L.sc("A compound is", "two or more elements chemically combined", ["a mixture of elements", "one kind of atom", "a solution"], "Fixed ratio, chemically bonded.")
L.sc("Filtration separates", "an insoluble solid from a liquid", ["two liquids", "dyes", "gases"], "The solid is trapped on the filter paper.")
L.sc("In a condenser the cooling water enters at the", "bottom", ["top", "middle only", "side"], "So that the jacket is full of water.")
L.sc("Rf has", "no units", ["units of cm", "units of mol", "units of g"], "It is a ratio of two distances.")
L.sc("A pure substance has a", "sharp fixed melting point", ["melting range", "no melting point", "variable boiling point"], "Impurities make it melt over a range.")

# =====================================================================  3. ATOMS AND PERIODIC TABLE
ch = p.chapter("atoms", "Atoms and the periodic table", programRef="GCE O Level Chemistry — Atomic structure, isotopes, periodic table, Groups I, VII and 0 (to be checked against the official syllabus)")
L = ch.lesson("atomic-structure", "Atomic structure, electron arrangement and isotopes", minutes=30,
    objectives=["State the relative charge and mass of protons, neutrons and electrons.", "Find numbers of protons, neutrons and electrons from the proton and nucleon numbers.", "Write electron arrangements for elements 1–20.", "Define isotopes and calculate relative atomic mass from isotope abundances."],
    notes=["Electron arrangements up to calcium (Z = 20) follow the 2, 8, 8, 2 pattern taught at this level.", "Abundances of chlorine isotopes (75% and 25%) are the standard rounded values that give Ar = 35.5."])
L.key("definition", "Inside the atom", "The **nucleus** contains **protons** (charge +1, mass 1) and **neutrons** (0, mass 1); **electrons** (charge −1, negligible mass) move around it in **shells**. **Proton number Z** = number of protons = number of electrons in a neutral atom. **Nucleon number A** = protons + neutrons. Neutrons = A − Z.")
L.key("retenir", "Electron arrangement", "Shells hold at most **2, 8, 8** electrons (for the first 20 elements), filled from the inside out. Sodium (Z = 11): **2,8,1**. Chlorine (Z = 17): **2,8,7**. Calcium (Z = 20): **2,8,8,2**. The number of **outer electrons** = the group number (Groups I–VII); full outer shell (2 or 8) = noble gas, very unreactive.")
L.key("formule", "Isotopes and relative atomic mass", "**Isotopes** are atoms of the same element (same Z) with different numbers of neutrons (different A), e.g. chlorine-35 and chlorine-37. They have the same chemical properties. **Relative atomic mass** Ar = average mass of the isotopes (taking their abundance into account) compared with 1/12 of the mass of a carbon-12 atom.")
L.key("pieges", "Watch out", "- Number of neutrons = A − Z, not A + Z.\n- Isotopes differ in neutrons, not in protons or electrons.\n- An ion has a different number of electrons from protons (Na⁺ has 10 electrons but 11 protons).")
cx, cy = 200, 120
els = []
for rad, k, off in [(30, 2, 0.5), (55, 8, 0.2), (80, 1, 0.9)]:
    for i in range(k):
        a = off + 2 * math.pi * i / k; els.append(CIRCLE(round(cx + rad * math.cos(a)), round(cy + rad * math.sin(a)), 4, fill="red"))
L.illustration(shapes([CIRCLE(cx, cy, 30, width=1), CIRCLE(cx, cy, 55, width=1), CIRCLE(cx, cy, 80, width=1), CIRCLE(cx, cy, 10, fill="orange"), *els,
                       T(330, 40, "2,8,1", 16), T(330, 190, "sodium", 14)], 400, 240),
    "Sodium atom, 2,8,1: 11 electrons in three shells round the nucleus.", "Three concentric circles with 2, 8 and 1 red electrons and a central nucleus; text 2,8,1.")
nn = 23 - 11; assert nn == 12
L.example("Example 1 — particles in an atom", "An atom of sodium has proton number 11 and nucleon number 23. State the number of protons, neutrons and electrons and write its electron arrangement.", ["Protons = 11; electrons = 11 (neutral atom); neutrons = 23 − 11 = 12.", "Fill the shells: 2, 8, then 1: electron arrangement 2,8,1."], "11 protons, 12 neutrons, 11 electrons; 2,8,1")
ar_cl = (75 * 35 + 25 * 37) / 100; assert ar_cl == 35.5
L.example("Example 2 — relative atomic mass", "Natural chlorine is 75% chlorine-35 and 25% chlorine-37. Calculate its relative atomic mass.", ["Ar = (75 × 35 + 25 × 37) ÷ 100.", "= (2625 + 925) ÷ 100 = 3550 ÷ 100 = 35.5."], "Ar = 35.5")
L.num("An atom has 17 protons and 18 neutrons. Find its nucleon number.", 35, "A = 17 + 18 = 35.", unit=None)
L.mcq("The electron arrangement of magnesium (Z = 12) is", "2,8,2", ["2,8,1", "2,10", "2,2,8"], "Fill 2, then 8, then the remaining 2.")
L.tf("Isotopes of an element have different numbers of electrons in a neutral atom.", False, "Neutral isotopes have the same number of protons and electrons; only neutrons differ.")
ar_b = (20 * 10 + 80 * 11) / 100; assert ar_b == 10.8
L.num("Boron is 20% boron-10 and 80% boron-11. Calculate its relative atomic mass.", ar_b, "Ar = (20 × 10 + 80 × 11) ÷ 100 = (200 + 880) ÷ 100 = 10.8.", tolerance=0.01, tier="approfondissement")
L.mcq("An atom has electron arrangement 2,8,6. It is in", "Group VI, Period 3", ["Group VI, Period 2", "Group III, Period 6", "Group VIII, Period 3"], "6 outer electrons: Group VI; three shells: Period 3 (sulphur).", tier="approfondissement")
L.problem("Three particles are described by (protons, neutrons, electrons): X (6, 6, 6), Y (6, 8, 6) and Z (11, 12, 10).", [
    part_mcq("Which two particles are isotopes of the same element?", "X and Y", ["X and Z", "Y and Z", "None"], "Same number of protons (6) but different neutrons (6 and 8)."),
    part_num("What is the nucleon number of Y?", 14, "A = 6 + 8 = 14.", unit=None),
    part_mcq("Particle Z is", "a positive ion (Na⁺)", ["a neutral atom", "a negative ion", "a neutron"], "11 protons but 10 electrons: charge +1."),
    part_open("State the electron arrangement of particle X (a neutral atom).", "2,4.", "1 mark for 2,4; 1 mark for stating 6 electrons filling shell 1 then shell 2.")], tier="examen")
L.sc("The nucleon number is the number of", "protons plus neutrons", ["protons only", "electrons", "neutrons only"], "A = Z + neutrons.")
L.sc("A neutral atom has 9 protons. It has", "9 electrons", ["18 electrons", "0 electrons", "10 electrons"], "Electrons = protons for neutral atoms.")
L.sc("Electron arrangement of chlorine (Z = 17)", "2,8,7", ["2,8,8", "2,7,8", "2,9,6"], "7 outer electrons.")
L.sc("Chlorine-35 and chlorine-37 atoms have the same number of", "protons", ["neutrons", "nucleons", "electrons only"], "They are isotopes of one element, so Z is the same.")
L.sc("A noble gas atom has", "a full outer shell", ["one outer electron", "a half full shell", "no electrons"], "That makes it unreactive.")

L = ch.lesson("periodic-table", "The periodic table: Groups I, VII and 0", minutes=30, prerequisites=["gceol-chemistry-atomic-structure"],
    objectives=["Describe how the periodic table is arranged (groups, periods, metals and non-metals).", "Describe the trends and reactions of the alkali metals.", "Describe the trends and displacement reactions of the halogens.", "Explain the inertness and uses of noble gases."],
    notes=["Reactions of Group I metals with water are described qualitatively; a teacher should demonstrate or show video, never let pupils handle potassium.", "Melting points and states of halogens at room temperature (Cl₂ gas, Br₂ liquid, I₂ solid) are standard."])
L.key("definition", "Arrangement", "Elements are arranged in order of **proton number**. **Periods** are horizontal rows; **groups** are vertical columns of elements with the **same number of outer electrons** and similar properties. Metals are on the left and in the middle, non-metals on the right. Between Groups II and III are the **transition metals** (iron, copper, zinc…), which have coloured compounds and are good catalysts.")
L.key("retenir", "Group I — alkali metals (Li, Na, K)", "Soft metals with low melting points and densities, stored under oil. They react with water: **metal + water → metal hydroxide + hydrogen**, e.g. 2Na + 2H₂O → 2NaOH + H₂. The solution is alkaline. Reactivity **increases down the group** (K more violent than Na, Na more than Li), because the outer electron is lost more easily from a larger atom.")
L.key("retenir", "Group VII — halogens (F, Cl, Br, I)", "Chlorine is a pale green **gas**, bromine a red-brown **liquid**, iodine a grey-black **solid**: melting and boiling points **increase down the group**. They form diatomic molecules (Cl₂, Br₂, I₂) and ions X⁻. Reactivity **decreases down the group**, so a more reactive halogen **displaces** a less reactive one: Cl₂ + 2KBr → 2KCl + Br₂.")
L.key("pieges", "Group 0 and watch out", "**Group 0 (noble gases)**: He, Ne, Ar, Kr are monatomic, colourless and **unreactive** because their outer shells are full; used in lamps (neon, argon) and balloons (helium).\n\n- Alkali metals become **more** reactive down the group; halogens become **less** reactive. Do not mix them up.")
rows = [("H", 1), ("He", 8)]
cells = []
x0, y0, cw, chh = 25, 55, 44, 36
period = [["H", "", "", "", "", "", "", "He"], ["Li", "Be", "B", "C", "N", "O", "F", "Ne"], ["Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar"]]
grp = ["I", "II", "III", "IV", "V", "VI", "VII", "0"]
for j, row in enumerate(period):
    for i, s_ in enumerate(row):
        if s_: cells += [RECT(x0 + i * cw, y0 + j * chh, cw, chh, fill=("orange" if i == 0 else ("blue" if i == 6 else ("green" if i == 7 else None)))), T(x0 + i * cw + cw // 2, y0 + j * chh + 24, s_, 14)]
for i, g in enumerate(grp): cells.append(T(x0 + i * cw + cw // 2, 45, g, 12))
L.illustration(shapes(cells + [T(200, 190, "Groups I (orange), VII (blue) and 0 (green)", 12)], 400, 210),
    "The first three periods of the periodic table with Groups I, VII and 0 highlighted.", "A grid with three rows of elements from hydrogen to argon and group numbers I to 0 along the top; Group I, VII and 0 are coloured.")
L.example("Example 1 — finding the group", "An element X has electron arrangement 2,8,6. In which group and period is it, and is it a metal?", ["Outer electrons = 6, so Group VI. Three shells, so Period 3.", "Group VI elements on the right of the table are non-metals: X is sulphur (Z = 16)."], "Group VI, Period 3; non-metal (sulphur)")
L.example("Example 2 — displacement", "Will chlorine displace bromine from potassium bromide solution? Write the equation and say what you see.", ["Chlorine is more reactive than bromine (higher in Group VII), so it displaces bromine: Cl₂ + 2KBr → 2KCl + Br₂.", "The colourless solution turns orange-brown as bromine forms."], "Yes: orange-brown bromine formed")
L.mcq("Which statement about Group I metals is correct?", "Reactivity increases down the group", ["They are hard and dense", "They do not react with water", "Reactivity decreases down the group"], "Atoms get larger and the outer electron is lost more easily.")
L.mcq("Which halogen is a liquid at room temperature?", "bromine", ["chlorine", "iodine", "fluorine"], "Cl₂ gas, Br₂ liquid, I₂ solid.")
L.tf("Neon is chemically unreactive because its outer shell is full.", True, "A full outer shell gives stability.")
L.mcq("Chlorine is bubbled into potassium iodide solution. The solution turns", "brown (iodine formed)", ["colourless", "blue", "green"], "Chlorine displaces iodine: Cl₂ + 2KI → 2KCl + I₂.", tier="approfondissement", difficulty=2)
L.open("Explain why potassium is more reactive than lithium.", "Both lose one outer electron, but the potassium atom is larger, so its outer electron is further from the nucleus and held less strongly, and it is lost more easily.", "1 mark: both lose one electron; 1 mark: potassium's outer electron is further from nucleus/held less strongly.", tier="approfondissement", difficulty=2)
L.problem("A pupil adds a small piece of sodium to water in a trough (the teacher performs the demonstration).", [
    part_mcq("What gas is given off?", "hydrogen", ["oxygen", "carbon dioxide", "chlorine"], "2Na + 2H₂O → 2NaOH + H₂."),
    part_mcq("What colour does universal indicator turn in the resulting solution?", "blue/purple (alkaline)", ["red", "green", "colourless"], "Sodium hydroxide is a strong alkali."),
    part_open("Write the balanced equation for the reaction.", "2Na + 2H₂O → 2NaOH + H₂", "1 mark for correct formulae; 1 mark for balancing."),
    part_open("Predict how potassium would react compared with sodium and explain.", "More vigorously, because it is further down Group I: the larger atom loses its outer electron more easily.", "1 mark: more vigorous; 1 mark: reason.")], tier="examen")
L.sc("Elements in the same group have the same number of", "outer electrons", ["shells", "protons", "neutrons"], "That is why they have similar properties.")
L.sc("Noble gases are", "unreactive", ["very reactive", "metals", "liquids at room temperature"], "Full outer shell.")
L.sc("Which is the most reactive halogen listed?", "chlorine", ["bromine", "iodine", "all equal"], "Reactivity falls down the group.")
L.sc("Alkali metals react with water to form", "hydroxide and hydrogen", ["oxide and oxygen", "chloride and chlorine", "carbonate and CO₂"], "2Na + 2H₂O → 2NaOH + H₂.")
L.sc("Transition metals", "have coloured compounds", ["are gases", "are in Group 0", "are not metals"], "Typical property.")

# =====================================================================  4. BONDING
ch = p.chapter("bonding", "Chemical bonding and structure", programRef="GCE O Level Chemistry — Ionic, covalent and metallic bonding; structure and properties (to be checked against the official syllabus)")
L = ch.lesson("ionic-covalent", "Ionic and covalent bonding", minutes=30, prerequisites=["gceol-chemistry-atomic-structure"],
    objectives=["Explain why atoms form bonds (to obtain a full outer shell).", "Describe ionic bonding as electron transfer and write the formulae of ions.", "Describe covalent bonding as sharing of electrons and draw dot-and-cross diagrams in words.", "Predict the type of bonding from the elements involved."],
    notes=["Dot-and-cross diagrams are described in words and symbols; hand-drawn diagrams are expected in class."])
L.key("definition", "Ionic bonding", "A **metal** atom **loses** outer electrons to form a positive ion (cation); a **non-metal** atom **gains** electrons to form a negative ion (anion). The oppositely charged ions attract strongly (ionic bond) and form a giant lattice. Example: Na (2,8,1) loses 1 electron → Na⁺ (2,8); Cl (2,8,7) gains 1 → Cl⁻ (2,8,8). Formula **NaCl**. Magnesium oxide: Mg²⁺ and O²⁻, formula **MgO**.")
L.key("definition", "Covalent bonding", "Two **non-metal** atoms **share** pairs of electrons so that each gets a full outer shell. A shared pair is one **covalent bond**. Examples: H₂ (1 bond), Cl₂ (1), O₂ (**double** bond, 2 pairs), N₂ (**triple** bond, 3 pairs), H₂O (2 bonds + 2 lone pairs on O), NH₃ (3 bonds), CH₄ (4 bonds), CO₂ (2 double bonds). The result is a **molecule**.")
L.key("methode", "Which type of bond?", "- Metal + non-metal → **ionic**.\n- Non-metal + non-metal → **covalent**.\n- Metal + metal (alloys, or one metal) → **metallic**: positive ions in a 'sea' of delocalised electrons.\n\nThe charge of a metal ion in Groups I, II, III is +1, +2, +3; non-metal ions in Groups VI, VII are 2− and 1−.")
L.key("pieges", "Watch out", "- In ionic bonding electrons are **transferred**; in covalent bonding they are **shared**.\n- Count only the **outer-shell** electrons in diagrams.\n- Ionic compounds are not made of molecules: NaCl is a lattice, not 'a NaCl molecule'.")
L.illustration(shapes([CIRCLE(90, 110, 40), CIRCLE(90, 110, 4, fill="red"), T(90, 105, "Na", 16), CIRCLE(300, 110, 40), T(300, 117, "Cl", 16), LINE(135, 110, 255, 110, arrow="end", color="red", width=3), T(195, 95, "1 electron", 14),
                       T(90, 190, "Na⁺ (2,8)", 14), T(300, 190, "Cl⁻ (2,8,8)", 14)], 400, 220),
    "Formation of sodium chloride: sodium transfers one electron to chlorine.", "Two large circles for sodium and chlorine atoms with a red arrow labelled 1 electron from sodium to chlorine, and the resulting ions Na+ and Cl- below.")
L.example("Example 1 — magnesium oxide", "Explain the formation of magnesium oxide from magnesium (2,8,2) and oxygen (2,6).", ["Magnesium loses its 2 outer electrons → Mg²⁺ (2,8). Oxygen gains 2 electrons → O²⁻ (2,8).", "The ions have equal and opposite charges (2+ and 2−), so they combine 1 : 1: formula MgO."], "Mg²⁺ and O²⁻; MgO")
L.example("Example 2 — water", "Describe the bonding in a molecule of water, H₂O.", ["Oxygen (2,6) needs 2 more electrons; each hydrogen (1) needs 1.", "Oxygen shares one pair with each of two H atoms: 2 covalent bonds; two lone pairs stay on O. Each atom has a full outer shell."], "Two O–H single covalent bonds")
L.mcq("Which compound is formed by ionic bonding?", "sodium chloride (NaCl)", ["carbon dioxide (CO₂)", "water (H₂O)", "methane (CH₄)"], "A metal and a non-metal: electron transfer.")
L.num("How many electrons are shared in the covalent bonds of a nitrogen molecule (N₂), which has a triple bond?", 6, "A triple bond has 3 shared pairs = 6 shared electrons.", unit=None)
L.tf("Atoms in a molecule of chlorine, Cl₂, are held by an ionic bond.", False, "Two non-metal atoms share a pair of electrons: a single covalent bond.")
L.mcq("The formula of calcium chloride is", "CaCl₂", ["CaCl", "Ca₂Cl", "Ca₂Cl₂"], "Ca²⁺ needs two Cl⁻ to balance charge.", tier="approfondissement")
L.open("Explain why sodium atoms and chlorine atoms combine to form NaCl.", "Sodium has one outer electron, which it loses to become Na⁺ with a full shell; chlorine has seven outer electrons and gains one to become Cl⁻ with a full shell. The oppositely charged ions attract each other strongly.", "1 mark: electron transfer Na to Cl; 1 mark: full outer shells; 1 mark: attraction between ions.", tier="approfondissement")
L.problem("Consider these substances: sodium fluoride (NaF), hydrogen chloride (HCl), methane (CH₄) and potassium oxide (K₂O).", [
    part_mcq("Which two are ionic?", "NaF and K₂O", ["HCl and CH₄", "NaF and CH₄", "HCl and K₂O"], "Both contain a Group I metal and a non-metal."),
    part_num("How many covalent bonds does a carbon atom form in methane?", 4, "Carbon (2,4) shares four electrons, one with each H.", unit=None),
    part_mcq("Why is the formula of potassium oxide K₂O and not KO?", "Two K⁺ (1+) are needed to balance one O²⁻ (2−)", ["Oxygen has two atoms", "Potassium has two shells", "To make it stable by heat"], "Total positive charge must equal total negative charge."),
    part_open("Describe, in terms of electrons, how hydrogen chloride is formed from hydrogen and chlorine atoms.", "Each atom needs one more electron; they share one pair of electrons, forming a single covalent bond, so H has a full shell of 2 and Cl a full shell of 8.", "1 mark: sharing a pair; 1 mark: full outer shells.")], tier="examen")
L.sc("In ionic bonding electrons are", "transferred", ["shared", "destroyed", "created"], "From metal to non-metal.")
L.sc("A double covalent bond has", "two shared pairs", ["one shared pair", "four shared pairs", "no shared pairs"], "Two pairs = four electrons.")
L.sc("Which formula is correct for magnesium chloride?", "MgCl₂", ["MgCl", "Mg₂Cl", "MgCl₃"], "Mg²⁺ + 2Cl⁻.")
L.sc("Metallic bonding is the attraction between", "positive ions and delocalised electrons", ["two atoms sharing electrons", "protons and neutrons", "molecules"], "Sea of electrons.")
L.sc("Carbon dioxide has", "covalent bonds", ["ionic bonds", "metallic bonds", "no bonds"], "Two non-metals.")

L = ch.lesson("structure-properties", "Structure and properties of substances", minutes=30, prerequisites=["gceol-chemistry-ionic-covalent"],
    objectives=["Describe giant ionic, simple molecular, giant covalent and metallic structures.", "Relate melting points and electrical conductivity to structure.", "Explain the properties of diamond and graphite.", "Predict properties of a substance from its structure."],
    notes=["Melting points are described qualitatively (high/low); no specific numbers apart from NaCl being 'about 800 °C'.", "Silicon(IV) oxide is mentioned as a giant covalent substance (sand)."])
L.key("retenir", "Four types of structure", "- **Giant ionic** (NaCl, MgO): high melting point, brittle, conducts **only when molten or dissolved** (ions free to move), often soluble in water.\n- **Simple molecular** (H₂O, CO₂, CH₄, I₂): **low** melting and boiling points (weak forces **between** molecules), do **not** conduct.\n- **Giant covalent** (diamond, graphite, silicon(IV) oxide): very high melting points, insoluble.\n- **Metallic**: high melting point, conduct electricity and heat, malleable and ductile.")
L.key("retenir", "Diamond and graphite", "Both are pure carbon (allotropes). **Diamond**: each C bonded to 4 others in a rigid 3-D network, **very hard**, does **not conduct**; used in cutting tools. **Graphite**: layers of carbon atoms, each bonded to 3 others; the layers slide easily (soft, a **lubricant**, used in pencils) and each atom has a free (delocalised) electron so graphite **conducts electricity** (electrodes).")
L.key("pieges", "Watch out", "- When a molecular substance melts, only the weak forces **between molecules** are overcome, not the covalent bonds inside the molecules.\n- Ionic solids do not conduct electricity (ions fixed); molten or dissolved ones do.\n- Metals conduct because of delocalised electrons, not ions.")
ions = []
for i in range(4):
    for j in range(4):
        cx_, cy_ = 100 + 45 * i, 45 + 45 * j
        pos = (i + j) % 2 == 0
        ions += [CIRCLE(cx_, cy_, 17, fill="blue" if pos else "red"), T(cx_, cy_ + 5, "+" if pos else "−", 16)]
L.illustration(shapes(ions + [T(330, 100, "Na⁺ blue", 12), T(330, 130, "Cl⁻ red", 12)], 400, 220),
    "A section of the sodium chloride lattice: positive and negative ions alternate in all directions.", "A square grid of alternating blue plus and red minus circles representing sodium and chloride ions.")
L.example("Example 1 — which is which?", "Substance A: melts at 800 °C, conducts when molten but not as a solid. Substance B: melts at −7 °C and does not conduct. Identify the structure of each.", ["A: high melting point and conducts only when molten (ions free to move): giant ionic.", "B: very low melting point and no conduction: simple molecular."], "A giant ionic; B simple molecular")
L.example("Example 2 — graphite and diamond", "Explain why graphite is used as a lubricant and as an electrode, while diamond is used for cutting.", ["Graphite has layers held by weak forces, so they slide over each other (lubricant); free electrons carry charge (electrode).", "In diamond every carbon is bonded to four others in a rigid network, so it is extremely hard and cuts glass and rock."], "Layers slide + free electrons; rigid network")
L.mcq("Why does a molecular substance such as iodine have a low melting point?", "Only weak forces between molecules need to be overcome", ["Covalent bonds are weak", "It is a metal", "It has ions"], "The strong covalent bonds inside the molecules are not broken.")
L.tf("Solid sodium chloride conducts electricity because it contains ions.", False, "The ions are fixed in the lattice and cannot move; molten or dissolved NaCl conducts.")
L.match("Match the substance with its structure.", [["sodium chloride", "giant ionic"], ["diamond", "giant covalent"], ["carbon dioxide", "simple molecular"], ["copper", "metallic"]], "Based on the bonding in each substance.")
L.mcq("Which substance conducts electricity as a solid?", "graphite", ["diamond", "sodium chloride", "sugar"], "Graphite has delocalised electrons between its layers.", tier="approfondissement")
L.open("Explain why metals can be hammered into shapes without breaking.", "Layers of positive ions can slide over each other; the delocalised electrons still hold them together, so the metal changes shape but does not shatter like an ionic solid.", "1 mark: layers slide; 1 mark: electrons keep the metallic bond.", tier="approfondissement")
L.problem("The table shows properties: P (soluble in water, melts at 750 °C, conducts when dissolved); Q (insoluble, very hard, melts above 3000 °C, no conduction); R (gas at room temperature, no conduction); S (shiny solid, conducts when solid, malleable).", [
    part_mcq("What is the structure of P?", "giant ionic", ["metallic", "simple molecular", "giant covalent"], "Soluble, high m.p., conducts when dissolved."),
    part_mcq("Which of the following could Q be?", "diamond", ["sodium chloride", "chlorine", "copper"], "Giant covalent with very high melting point and no conduction."),
    part_mcq("What is the structure of R?", "simple molecular", ["giant ionic", "metallic", "giant covalent"], "Gas at room temperature: weak forces between molecules."),
    part_open("Why does S conduct when solid?", "It has delocalised electrons that are free to move and carry charge through the metal.", "1 mark: delocalised electrons; 1 mark: free to move/carry charge.")], tier="examen")
L.sc("Ionic compounds conduct electricity when", "molten or dissolved", ["solid", "always", "never"], "Ions can move only when not fixed.")
L.sc("Graphite is used in pencils because", "its layers slide easily", ["it is very hard", "it is an ionic compound", "it is a metal"], "Weak forces between layers.")
L.sc("Simple molecular substances have", "low melting points", ["very high melting points", "free ions", "delocalised electrons"], "Weak intermolecular forces.")
L.sc("Diamond does not conduct because", "it has no free electrons", ["it is a metal", "it contains ions", "it is a liquid"], "All outer electrons are used in bonds.")
L.sc("Which is a giant covalent structure?", "silicon(IV) oxide (sand)", ["carbon dioxide", "water", "iodine"], "A giant network of Si and O atoms.")

# =====================================================================  5. FORMULAE AND EQUATIONS
ch = p.chapter("equations", "Formulae and chemical equations", programRef="GCE O Level Chemistry — Formulae of compounds, word and balanced symbol equations, state symbols, ionic equations (to be checked against the official syllabus)")
L = ch.lesson("formulae-equations", "Writing formulae and balancing equations", minutes=30, prerequisites=["gceol-chemistry-ionic-covalent"],
    objectives=["Write the formulae of compounds from the charges of ions.", "Write and balance symbol equations including state symbols.", "Write simple ionic equations for precipitation.", "Check that atoms and charges balance."],
    notes=["The list of ion charges is the standard school set; iron has two common ions (Fe²⁺ and Fe³⁺)."])
L.key("retenir", "Common ions", "**1+**: H⁺, Na⁺, K⁺, Ag⁺, NH₄⁺. **2+**: Mg²⁺, Ca²⁺, Zn²⁺, Cu²⁺, Fe²⁺, Pb²⁺. **3+**: Al³⁺, Fe³⁺.\n**1−**: Cl⁻, Br⁻, I⁻, OH⁻, NO₃⁻. **2−**: O²⁻, S²⁻, SO₄²⁻, CO₃²⁻.\n\nCompound formula: make the total positive charge equal the total negative charge (use brackets for groups): Al³⁺ + O²⁻ → **Al₂O₃**; Ca²⁺ + OH⁻ → **Ca(OH)₂**; NH₄⁺ + SO₄²⁻ → **(NH₄)₂SO₄**.")
L.key("methode", "Balancing an equation", "1. Write the correct formulae (never change a formula to balance).\n2. Count atoms of each element on both sides.\n3. Put **coefficients** (numbers in front) to balance, one element at a time; leave hydrogen and oxygen until last.\n4. Add **state symbols**: (s) solid, (l) liquid, (g) gas, (aq) in water solution.\n\nExample: Mg(s) + 2HCl(aq) → MgCl₂(aq) + H₂(g).")
L.key("formule", "Ionic equations", "A precipitation reaction can be written showing only the ions that react. Silver nitrate + sodium chloride → silver chloride + sodium nitrate. Full: AgNO₃(aq) + NaCl(aq) → AgCl(s) + NaNO₃(aq). Ionic: **Ag⁺(aq) + Cl⁻(aq) → AgCl(s)**. Na⁺ and NO₃⁻ are **spectator ions** (unchanged). Charges must balance as well as atoms.")
L.key("pieges", "Watch out", "- Subscripts (small numbers) belong to the formula; coefficients are the only thing you change.\n- CO₂ ≠ CO₂² : never write charges on molecules.\n- Always check both sides at the end by counting every element.")
L.illustration(shapes([T(200, 50, "4Fe + 3O₂ → 2Fe₂O₃", 20), T(100, 100, "Left side", 14), T(100, 130, "Fe: 4", 14), T(100, 160, "O: 3 × 2 = 6", 14), T(300, 100, "Right side", 14), T(300, 130, "Fe: 2 × 2 = 4", 14), T(300, 160, "O: 2 × 3 = 6", 14), LINE(200, 90, 200, 175, color="grey", dash=True)], 400, 200),
    "Checking a balanced equation: iron and oxygen atoms are equal on both sides.", "The equation 4Fe + 3O2 → 2Fe2O3 with atom counts for the left and right sides showing 4 iron and 6 oxygen on each side.")
L.example("Example 1 — writing a formula", "Write the formula of (a) aluminium oxide and (b) calcium nitrate.", ["(a) Al³⁺ and O²⁻: lowest common multiple of 3 and 2 is 6: two Al³⁺ (6+) and three O²⁻ (6−) → Al₂O₃.", "(b) Ca²⁺ and NO₃⁻: one Ca²⁺ needs two NO₃⁻ → Ca(NO₃)₂ (brackets round NO₃)."], "Al₂O₃; Ca(NO₃)₂")
L.example("Example 2 — balancing", "Balance: Fe + O₂ → Fe₂O₃.", ["Fe₂O₃ has 2 Fe and 3 O. Make O even: 2Fe₂O₃ (4 Fe, 6 O) and 3O₂ (6 O).", "Then 4 Fe on the left: 4Fe + 3O₂ → 2Fe₂O₃. Check: Fe 4 = 4; O 6 = 6."], "4Fe + 3O₂ → 2Fe₂O₃")
L.mcq("The formula of magnesium nitrate is", "Mg(NO₃)₂", ["MgNO₃", "Mg₂NO₃", "Mg(NO₃)"], "Mg²⁺ with two NO₃⁻.")
L.num("In the balanced equation 2H₂ + O₂ → 2H₂O, how many hydrogen atoms are there in total on the left?", 4, "2 × 2 = 4.", unit=None)
L.mcq("Which equation is balanced?", "2Na + 2H₂O → 2NaOH + H₂", ["Na + H₂O → NaOH + H₂", "2Na + H₂O → 2NaOH + H₂", "Na + 2H₂O → NaOH + H₂"], "Na 2 = 2; H 4 = 4; O 2 = 2.", tier="application")
L.mcq("Balance: Al + O₂ → Al₂O₃. The coefficient of O₂ is", "3", ["2", "1", "4"], "4Al + 3O₂ → 2Al₂O₃.", tier="approfondissement", difficulty=2)
L.open("Write the ionic equation, with state symbols, for the reaction of aqueous barium chloride with aqueous sodium sulphate, which gives a white precipitate of barium sulphate.", "Ba²⁺(aq) + SO₄²⁻(aq) → BaSO₄(s)", "1 mark: correct species; 1 mark: state symbols; 1 mark: balanced charge/atoms.", tier="approfondissement", difficulty=2)
L.problem("Methane burns in air: CH₄ + O₂ → CO₂ + H₂O.", [
    part_num("Balanced: CH₄ + ?O₂ → CO₂ + 2H₂O. What is the coefficient of O₂?", 2, "O on the right: 2 (from CO₂) + 2 (from 2H₂O) = 4, so 2O₂ on the left.", unit=None),
    part_num("How many oxygen atoms are on the left of the balanced equation?", 4, "2 × 2 = 4.", unit=None),
    part_mcq("What does (g) mean in a state symbol?", "gas", ["solid", "in solution", "liquid"], "(g) means gas."),
    part_open("Write the balanced equation with state symbols.", "CH₄(g) + 2O₂(g) → CO₂(g) + 2H₂O(l)", "1 mark balanced; 1 mark state symbols.")], tier="examen")
L.sc("To balance an equation you may change", "the coefficients", ["the subscripts", "the elements", "the charges"], "Subscripts define the compound.")
L.sc("The formula of aluminium chloride is", "AlCl₃", ["Al₃Cl", "AlCl", "AlCl₂"], "Al³⁺ needs three Cl⁻.")
L.sc("(aq) means", "dissolved in water", ["solid", "gas", "liquid"], "Aqueous solution.")
L.sc("Spectator ions are", "unchanged in a reaction", ["precipitated", "formed as gas", "the only ions present"], "They do not take part.")
L.sc("Formula of calcium hydroxide", "Ca(OH)₂", ["CaOH", "Ca₂OH", "CaO₂H"], "One Ca²⁺ and two OH⁻.")

# =====================================================================  6. THE MOLE
ch = p.chapter("mole", "The mole and chemical calculations", programRef="GCE O Level Chemistry — Relative masses, the mole, reacting masses, gas volumes, concentration, titration (to be checked against the official syllabus)")
L = ch.lesson("moles-masses", "Relative masses, moles and formulae", minutes=35, prerequisites=["gceol-chemistry-formulae-equations"],
    objectives=["Calculate relative formula mass Mr.", "Convert between mass and moles with n = m/M.", "Calculate percentage composition and the empirical formula.", "Use the Avogadro constant to count particles."],
    notes=["Relative atomic masses used: H 1, C 12, N 14, O 16, Na 23, Mg 24, Al 27, S 32, Cl 35.5, K 39, Ca 40, Fe 56, Cu 64, Zn 65. The Avogadro constant is taken as 6.0 × 10²³ per mole (6.02 × 10²³ more exactly).", "Check if the Board gives an Ar table in the exam (usually yes)."])
L.key("definition", "The mole", "One **mole** is the amount of substance that contains **6.0 × 10²³** particles (the Avogadro constant). The **molar mass M** (g/mol) of a substance is its Ar or Mr in grams. **Relative formula mass Mr** = sum of the Ar of all the atoms in the formula. Example: Mr(CaCO₃) = 40 + 12 + 3 × 16 = **100**.")
L.formula(r"n = \frac{m}{M}", "n in mol, m in g, M in g/mol")
L.key("methode", "Percentage composition and empirical formula", "**% of an element** = (Ar × number of atoms ÷ Mr) × 100. **Empirical formula** (simplest whole-number ratio of atoms): (1) write the masses (or %); (2) divide each by Ar to get moles; (3) divide by the smallest number; (4) round to whole numbers (multiply if you get ×.5). The **molecular formula** = empirical formula × (Mr ÷ empirical mass).")
L.key("pieges", "Watch out", "- Use Ar of **atoms** for elements like O₂ only if they are atoms: Mr(O₂) = 32, not 16.\n- Brackets multiply: Mr(Ca(OH)₂) = 40 + 2 × (16 + 1) = 74.\n- Write units: mol, g, g/mol.")
L.illustration(shapes([RECT(20, 80, 100, 50, radius=6), T(70, 110, "mass (g)", 14), RECT(150, 80, 100, 50, radius=6), T(200, 110, "moles (mol)", 14), RECT(280, 80, 100, 50, radius=6), T(330, 110, "particles", 14),
                       LINE(120, 95, 150, 95, arrow="end", width=2), LINE(150, 115, 120, 115, arrow="end", width=2), LINE(250, 95, 280, 95, arrow="end", width=2), LINE(280, 115, 250, 115, arrow="end", width=2),
                       T(135, 70, "÷M", 12), T(135, 150, "×M", 12), T(265, 70, "×6.0×10²³", 12), T(265, 150, "÷6.0×10²³", 12)], 400, 180),
    "Mass, moles and number of particles are linked through molar mass and the Avogadro constant.", "Three boxes labelled mass, moles and particles joined by arrows labelled divide by M, multiply by M, multiply and divide by the Avogadro constant.")
mrc = Mr(Ca=1, C=1, O=3); nmol = 25 / mrc; assert mrc == 100 and nmol == 0.25
L.example("Example 1 — moles from mass", "Calculate the number of moles in 25 g of calcium carbonate, CaCO₃ (Ar: Ca 40, C 12, O 16).", ["Mr(CaCO₃) = 40 + 12 + 3 × 16 = 100 g/mol.", "n = m ÷ M = 25 ÷ 100 = 0.25 mol."], "0.25 mol")
nc, nh = 2.4 / 12, 0.4 / 1; assert abs(nc - 0.2) < 1e-9 and abs(nh - 0.4) < 1e-9
L.example("Example 2 — empirical formula", "A hydrocarbon contains 2.4 g of carbon and 0.4 g of hydrogen. Find its empirical formula (Ar: C 12, H 1).", ["Moles: C = 2.4 ÷ 12 = 0.2; H = 0.4 ÷ 1 = 0.4.", "Divide by the smaller (0.2): C = 1, H = 2. The ratio is 1 : 2.", "Empirical formula = CH₂."], "CH₂")
mrca = Mr(Ca=1, O=2, H=2); assert mrca == 74
L.num("Calculate the Mr of calcium hydroxide, Ca(OH)₂ (Ca 40, O 16, H 1).", mrca, "40 + 2 × (16 + 1) = 40 + 34 = 74.", unit=None)
L.num("How many moles are there in 36 g of water (H₂O, Mr = 18)?", 2, "n = 36 ÷ 18 = 2 mol.", unit="mol")
L.mcq("How many particles are in 0.5 mol of any substance?", "3.0 × 10²³", ["6.0 × 10²³", "1.2 × 10²⁴", "0.5"], "0.5 × 6.0 × 10²³ = 3.0 × 10²³.")
pm = 14 * 2 / Mr(N=2, H=4, O=3) * 100; assert abs(pm - 35) < 1e-9
L.num("Calculate the percentage of nitrogen in ammonium nitrate, NH₄NO₃ (N 14, H 1, O 16).", pm, "Mr = 14 + 4 + 14 + 48 = 80; N = 28; % = 28 ÷ 80 × 100 = 35%.", unit="%", tier="approfondissement", tolerance=0.1)
L.mcq("A compound has empirical formula CH₂ and Mr = 56. Its molecular formula is", "C₄H₈", ["CH₂", "C₂H₄", "C₃H₆"], "Mr(CH₂) = 14; 56 ÷ 14 = 4, so C₄H₈.", tier="approfondissement", difficulty=2)
nf = 5.6 / 56; no = 2.4 / 16; assert abs(nf - 0.1) < 1e-9 and abs(no - 0.15) < 1e-9
L.problem("An oxide of iron contains 5.6 g of iron and 2.4 g of oxygen (Ar: Fe 56, O 16).", [
    part_num("Find the moles of iron atoms.", 0.1, "5.6 ÷ 56 = 0.1 mol.", unit="mol", tolerance=0.001),
    part_num("Find the moles of oxygen atoms.", 0.15, "2.4 ÷ 16 = 0.15 mol.", unit="mol", tolerance=0.001),
    part_mcq("What is the simplest ratio Fe : O?", "2 : 3", ["1 : 1", "3 : 2", "1 : 2"], "0.1 : 0.15 = 1 : 1.5 = 2 : 3."),
    part_mcq("The empirical formula is", "Fe₂O₃", ["FeO", "Fe₃O₂", "FeO₂"], "Fe : O = 2 : 3.")], tier="examen")
L.sc("One mole of carbon-12 has a mass of", "12 g", ["6 g", "1 g", "24 g"], "Molar mass equals Ar in grams.")
L.sc("n = m/M. If m = 44 g and M = 44 g/mol, n =", "1 mol", ["44 mol", "0.5 mol", "2 mol"], "44 ÷ 44 = 1.")
L.sc("Mr of CO₂ (C 12, O 16) is", "44", ["28", "60", "56"], "12 + 32 = 44.")
L.sc("The empirical formula shows", "the simplest ratio of atoms", ["the real number of atoms", "the mass", "the state"], "Definition.")
L.sc("The Avogadro constant is about", "6.0 × 10²³ per mole", ["6.0 × 10²³ g", "3.0 × 10¹⁰", "1.6 × 10⁻¹⁹"], "Number of particles per mole.")

L = ch.lesson("moles-reactions", "Reacting masses, gas volumes and solutions", minutes=35, prerequisites=["gceol-chemistry-moles-masses"],
    objectives=["Use mole ratios from a balanced equation to calculate reacting masses.", "Use the molar gas volume 24 dm³ at room temperature and pressure.", "Calculate concentration in mol/dm³ and carry out a titration calculation.", "Calculate percentage yield."],
    notes=["Molar volume of a gas taken as 24 dm³ at room temperature and pressure (24 000 cm³); at 0 °C and 1 atm it is 22.4 dm³ — check which the Board uses.", "Titration figures are illustrative."])
L.key("methode", "Method for reacting masses", "1. Write the balanced equation.\n2. Convert the given mass to moles: n = m ÷ M.\n3. Use the **mole ratio** in the equation to find the moles of the wanted substance.\n4. Convert back: mass = n × M, or gas volume = n × 24 dm³ (room temperature and pressure).")
L.key("formule", "Concentration and titration", "**Concentration** c (mol/dm³) = moles ÷ volume in dm³: $c = \\frac{n}{V}$ (V in dm³ = cm³ ÷ 1000). In a **titration** a solution of known concentration (from the **burette**) reacts exactly with a measured volume (**pipette**) of another; an indicator shows the **end point**. Use $n = cV$ and the mole ratio of the equation.")
L.key("formule", "Percentage yield", "**Percentage yield** = (actual mass obtained ÷ theoretical mass) × 100. The yield is less than 100% because of incomplete reactions, side reactions and losses during separation.")
L.key("pieges", "Watch out", "- Convert cm³ to dm³ (÷ 1000) before using c = n/V.\n- Use the **ratio in the equation**, not 1 : 1 by habit.\n- For gases the volume ratio equals the mole ratio.")
L.illustration(shapes([RECT(185, 30, 30, 120), LINE(200, 150, 200, 165, width=2), POLY([[170, 215], [230, 215], [215, 170], [185, 170]], fill=None), RECT(190, 190, 20, 25, fill="blue", stroke="blue"), T(200, 20, "burette", 12), T(310, 195, "conical flask", 12), T(310, 215, "+ indicator", 12), T(90, 90, "acid of known", 12), T(90, 106, "concentration", 12)], 400, 240),
    "Titration: the solution in the burette is added slowly to the measured volume in the conical flask until the indicator changes colour.", "A burette above a conical flask containing coloured solution, with labels.")
mmg = 4.8 / 24; nh2 = mmg; vh2 = nh2 * MOLAR_VOL; assert abs(vh2 - 4.8) < 1e-9
L.example("Example 1 — gas volume", "Magnesium reacts with dilute hydrochloric acid: Mg + 2HCl → MgCl₂ + H₂. What volume of hydrogen is made from 4.8 g of Mg? (Mg 24; 1 mol of gas = 24 dm³.)", ["n(Mg) = 4.8 ÷ 24 = 0.2 mol. Ratio Mg : H₂ = 1 : 1, so n(H₂) = 0.2 mol.", "Volume = 0.2 × 24 = 4.8 dm³."], "4.8 dm³")
n_naoh = 0.100 * 25.0 / 1000; c_hcl = n_naoh / (20.0 / 1000); assert abs(c_hcl - 0.125) < 1e-9
L.example("Example 2 — titration", "25.0 cm³ of sodium hydroxide of concentration 0.100 mol/dm³ is neutralised by 20.0 cm³ of hydrochloric acid: NaOH + HCl → NaCl + H₂O. Find the concentration of the acid.", ["n(NaOH) = c × V = 0.100 × 25.0/1000 = 0.00250 mol. Ratio NaOH : HCl = 1 : 1, so n(HCl) = 0.00250 mol.", "c(HCl) = n ÷ V = 0.00250 ÷ (20.0/1000) = 0.125 mol/dm³."], "0.125 mol/dm³")
mass_cao = (1000 / Mr(Ca=1, C=1, O=3)) * Mr(Ca=1, O=1); assert mass_cao == 560
L.num("Calculate the mass of calcium oxide made by heating 1000 g of calcium carbonate: CaCO₃ → CaO + CO₂ (Ca 40, C 12, O 16).", mass_cao, "n(CaCO₃) = 1000 ÷ 100 = 10 mol = n(CaO); mass = 10 × 56 = 560 g.", unit="g")
L.num("What volume of CO₂ (in dm³, at 24 dm³ per mole) is produced in the same reaction from 1000 g of CaCO₃?", 10 * 24, "n(CO₂) = 10 mol; volume = 10 × 24 = 240 dm³.", unit="dm³")
L.mcq("What is the concentration of a solution containing 0.5 mol in 250 cm³?", "2 mol/dm³", ["0.5 mol/dm³", "125 mol/dm³", "0.002 mol/dm³"], "250 cm³ = 0.25 dm³; c = 0.5 ÷ 0.25 = 2 mol/dm³.")
nz = 6.5 / 65; vv = nz * MOLAR_VOL; assert abs(vv - 2.4) < 1e-9
L.num("Zinc reacts with sulphuric acid: Zn + H₂SO₄ → ZnSO₄ + H₂. Find the volume of H₂ (dm³) from 6.5 g Zn (Zn 65).", vv, "n(Zn) = 6.5 ÷ 65 = 0.1 mol = n(H₂); V = 0.1 × 24 = 2.4 dm³.", unit="dm³", tier="approfondissement", tolerance=0.01)
L.mcq("In the reaction 2Mg + O₂ → 2MgO, how many moles of O₂ react with 0.4 mol of Mg?", "0.2 mol", ["0.4 mol", "0.8 mol", "0.1 mol"], "Ratio Mg : O₂ = 2 : 1, so 0.4 ÷ 2 = 0.2 mol.", tier="approfondissement")
n_n2 = 28 / 28; n_nh3 = 2 * n_n2; m_nh3 = n_nh3 * Mr(N=1, H=3); yield_ = 28.0 / m_nh3 * 100
close(m_nh3, 34.0); close(yield_, 28 / 34 * 100)
L.problem("In the Haber process nitrogen reacts with hydrogen: N₂ + 3H₂ → 2NH₃. A manufacturer starts with 28 g of nitrogen (N 14, H 1) and obtains 28 g of ammonia.", [
    part_num("How many moles of N₂ are in 28 g?", 1, "Mr(N₂) = 28, so n = 28 ÷ 28 = 1 mol.", unit="mol"),
    part_num("How many moles of NH₃ can be made at most?", 2, "Ratio N₂ : NH₃ = 1 : 2, so 2 mol.", unit="mol"),
    part_num("What is the theoretical mass of NH₃ in g?", m_nh3, "Mr(NH₃) = 14 + 3 = 17; 2 × 17 = 34 g.", unit="g"),
    part_num("Calculate the percentage yield (to the nearest whole number).", round(yield_), "28 ÷ 34 × 100 = 82%.", unit="%", tolerance=1)], tier="examen")
L.sc("Concentration is measured in", "mol/dm³", ["g/mol", "mol/g", "dm³/mol"], "Moles per cubic decimetre.")
L.sc("1 mole of gas at room temperature and pressure has volume", "24 dm³", ["22.4 cm³", "1 dm³", "100 dm³"], "As used in this course.")
L.sc("500 cm³ equals", "0.5 dm³", ["5 dm³", "0.05 dm³", "50 dm³"], "Divide by 1000.")
L.sc("A burette is used to", "add a solution slowly and measure its volume", ["heat a solution", "filter", "weigh"], "Used in titration.")
L.sc("Percentage yield is", "actual yield ÷ theoretical yield × 100", ["theoretical ÷ actual × 100", "actual + theoretical", "mass ÷ Mr"], "Definition.")

# =====================================================================  7. ACIDS, BASES AND SALTS
ch = p.chapter("acids", "Acids, bases and salts", programRef="GCE O Level Chemistry — Acids, bases, pH, neutralisation, preparation of salts, tests for ions and gases (to be checked against the official syllabus)")
L = ch.lesson("acids-bases", "Acids, bases, pH and neutralisation", minutes=30, prerequisites=["gceol-chemistry-formulae-equations"],
    objectives=["Define acids, bases and alkalis in terms of H⁺ and OH⁻.", "Use indicators and the pH scale.", "Write equations for the reactions of acids with metals, bases and carbonates.", "Explain neutralisation and give everyday uses."],
    notes=["pH values in the bar chart are approximate typical values (gastric juice about 1–2, lemon juice about 2, soap about 10, bleach about 12–13).", "Strong/weak acid distinction is based on degree of ionisation; no numerical Ka is required at this level."])
L.key("definition", "Acids and bases", "An **acid** produces **hydrogen ions H⁺** in water (HCl, H₂SO₄, HNO₃, ethanoic acid). A **base** is a substance that neutralises an acid (metal oxides and hydroxides); a base that dissolves in water is an **alkali** and produces **hydroxide ions OH⁻** (NaOH, KOH, Ca(OH)₂, ammonia solution). **Neutralisation**: H⁺ + OH⁻ → H₂O.")
L.key("retenir", "Indicators and pH", "**pH scale**: below 7 acidic, 7 neutral, above 7 alkaline. Universal indicator: red/orange (strong acid) → yellow → green (7) → blue → purple (strong alkali). **Litmus**: red in acid, blue in alkali. **Methyl orange**: red in acid, yellow in alkali. **Phenolphthalein**: colourless in acid, pink in alkali. A **strong** acid (HCl, H₂SO₄) is fully ionised; a **weak** acid (ethanoic acid) is only partly ionised.")
L.key("formule", "Reactions of acids", "- acid + metal → salt + hydrogen: Mg + 2HCl → MgCl₂ + H₂\n- acid + base (oxide/hydroxide) → salt + water: CuO + H₂SO₄ → CuSO₄ + H₂O\n- acid + carbonate → salt + water + carbon dioxide: CaCO₃ + 2HCl → CaCl₂ + H₂O + CO₂\n\nSalt names: hydrochloric acid → **chlorides**; sulphuric → **sulphates**; nitric → **nitrates**.")
L.key("pieges", "Watch out", "- A strong acid is not the same as a concentrated acid: strength is about ionisation, concentration is about how much is dissolved.\n- Copper, silver and gold do not react with dilute acids.\n- Never taste or touch strong acids and alkalis; wear eye protection.")
L.illustration(bars([("acid in stomach", 2, "red"), ("lemon juice", 2.5, "orange"), ("pure water", 7, "green"), ("soap", 10, "blue"), ("bleach", 12.5, "blue")], unit="pH", w=400, h=240),
    "Approximate pH of some familiar substances: acids below 7, alkalis above 7.", "A bar chart of pH: stomach acid about 2, lemon juice about 2.5, pure water 7, soap about 10 and bleach about 12.5.")
L.example("Example 1 — zinc and acid", "Write the balanced equation (with state symbols) for zinc reacting with dilute sulphuric acid and name the products.", ["Zinc is above hydrogen in the reactivity series so it reacts with dilute acid and forms a salt and hydrogen.", "Zn(s) + H₂SO₄(aq) → ZnSO₄(aq) + H₂(g): zinc sulphate and hydrogen."], "Zn + H₂SO₄ → ZnSO₄ + H₂")
nHCl = 0.10; m_cc = nHCl / 2 * Mr(Ca=1, C=1, O=3); v_co2 = nHCl / 2 * MOLAR_VOL; assert m_cc == 5.0 and abs(v_co2 - 1.2) < 1e-9
L.example("Example 2 — antacid sum", "How much calcium carbonate neutralises 0.10 mol of hydrochloric acid (CaCO₃ + 2HCl → CaCl₂ + H₂O + CO₂), and what volume of CO₂ is released? (Ca 40, C 12, O 16; 24 dm³ per mole)", ["Ratio CaCO₃ : HCl = 1 : 2, so n(CaCO₃) = 0.10 ÷ 2 = 0.05 mol; mass = 0.05 × 100 = 5.0 g.", "n(CO₂) = 0.05 mol; volume = 0.05 × 24 = 1.2 dm³."], "5.0 g; 1.2 dm³")
L.mcq("A solution turns universal indicator purple. It is a", "strong alkali", ["strong acid", "neutral solution", "weak acid"], "Purple means pH about 13–14.")
L.mcq("Which gas is produced when magnesium reacts with dilute hydrochloric acid?", "hydrogen", ["oxygen", "carbon dioxide", "chlorine"], "Mg + 2HCl → MgCl₂ + H₂.")
L.tf("A solution of pH 3 is more acidic than a solution of pH 5.", True, "Lower pH means a more acidic solution.")
L.num("What mass of magnesium oxide (Mr 40) is needed to neutralise 0.20 mol of HCl? (MgO + 2HCl → MgCl₂ + H₂O)", 0.20 / 2 * 40, "n(MgO) = 0.20 ÷ 2 = 0.10 mol; mass = 0.10 × 40 = 4.0 g.", unit="g", tier="approfondissement", tolerance=0.01)
L.open("A farmer in the Western Highlands finds that the soil is too acidic. Suggest a substance to add and explain why it helps.", "Add lime (calcium hydroxide or calcium oxide/carbonate, a base): it neutralises the acid in the soil and raises the pH so crops grow better.", "1 mark: lime (a base); 1 mark: neutralises acid / raises pH.", tier="approfondissement")
L.problem("Dilute hydrochloric acid is added to three solids in separate test tubes: copper(II) oxide, magnesium ribbon and sodium carbonate.", [
    part_mcq("Which one fizzes and gives a gas that turns limewater milky?", "sodium carbonate", ["copper(II) oxide", "magnesium ribbon", "the acid alone"], "Carbonate + acid gives CO₂."),
    part_mcq("What is observed when copper(II) oxide is warmed with the acid?", "The black solid dissolves to give a blue solution", ["Hydrogen is given off", "A white precipitate forms", "Nothing happens at all"], "CuO + 2HCl → CuCl₂ + H₂O; copper(II) ions are blue."),
    part_open("Write the equation for the reaction of magnesium with hydrochloric acid.", "Mg(s) + 2HCl(aq) → MgCl₂(aq) + H₂(g)", "1 mark formulae; 1 mark balancing and state symbols."),
    part_open("Name the salt formed from sodium carbonate and hydrochloric acid and give the test for the gas.", "Sodium chloride; carbon dioxide turns limewater milky.", "1 mark each.")], tier="examen")
L.sc("An alkali is a base that", "dissolves in water", ["is a gas", "is an acid", "has pH 1"], "Alkalis give OH⁻ ions.")
L.sc("The pH of pure water is", "7", ["0", "14", "1"], "Neutral.")
L.sc("Acid + carbonate gives salt, water and", "carbon dioxide", ["hydrogen", "oxygen", "chlorine"], "CO₂ fizzes out.")
L.sc("Litmus is red in", "acid", ["alkali", "pure water only", "ammonia"], "Red in acid, blue in alkali.")
L.sc("Neutralisation is the reaction of", "H⁺ with OH⁻", ["Na with Cl", "O with H₂", "C with O₂"], "H⁺ + OH⁻ → H₂O.")

L = ch.lesson("salts-prep", "Preparing salts", minutes=30, prerequisites=["gceol-chemistry-acids-bases"],
    objectives=["State the solubility rules for common salts.", "Describe the preparation of a soluble salt from an acid and an insoluble base, metal or carbonate.", "Describe the preparation by titration and by precipitation.", "Calculate the mass of crystals obtained."],
    notes=["Solubility rules are the standard school set; calcium sulphate and calcium hydroxide are only slightly soluble.", "Hydrated copper(II) sulphate CuSO₄·5H₂O has Mr = 160 + 5 × 18 = 250."])
L.key("retenir", "Solubility rules", "- All **sodium, potassium and ammonium** salts are soluble.\n- All **nitrates** are soluble.\n- **Chlorides** are soluble except silver chloride and lead(II) chloride.\n- **Sulphates** are soluble except barium sulphate and lead(II) sulphate (calcium sulphate is slightly soluble).\n- **Carbonates** and **hydroxides** are insoluble except those of sodium, potassium (and ammonium).")
L.key("methode", "Method A — insoluble base, metal or carbonate in excess", "1. Warm the dilute acid and add the solid **in excess** until no more dissolves.\n2. **Filter** off the excess solid.\n3. Heat the filtrate to evaporate part of the water (crystallisation point).\n4. Leave to cool and crystallise; filter and dry the crystals between filter papers.\n\nExample: CuO + H₂SO₄ → CuSO₄ + H₂O.")
L.key("methode", "Method B and C", "**Titration** (for soluble bases such as NaOH, and for Na⁺, K⁺, NH₄⁺ salts): find the exact volume of acid that neutralises a known volume of alkali with an indicator, repeat **without** indicator and crystallise. **Precipitation** (for insoluble salts): mix two soluble solutions, filter the precipitate, wash with distilled water and dry. Example: lead(II) nitrate + sodium iodide → lead(II) iodide (yellow ppt) + sodium nitrate.")
L.key("pieges", "Watch out", "- Add the solid in **excess**, so that all the acid is used up.\n- Do not evaporate to dryness: it destroys the crystals; stop when crystals begin to form on a glass rod.\n- Filter paper after use should be rinsed; wear eye protection when heating acid.")
L.illustration(shapes([RECT(20, 70, 90, 50, radius=6), T(65, 100, "acid + excess", 12), T(65, 114, "solid, warm", 12), RECT(150, 70, 90, 50, radius=6), T(195, 100, "filter", 14), RECT(280, 70, 100, 50, radius=6), T(330, 100, "evaporate", 12), T(330, 114, "then crystallise", 12),
                       LINE(110, 95, 150, 95, arrow="end"), LINE(240, 95, 280, 95, arrow="end"), T(65, 150, "step 1", 12), T(195, 150, "step 2", 12), T(330, 150, "steps 3-4", 12)], 400, 190),
    "Preparing a soluble salt from an acid and an insoluble solid in excess: react, filter, evaporate and crystallise.", "Three boxes in a row joined by arrows: acid plus excess solid, filter, and evaporate then crystallise.")
mCuO = 8.0; nCuO = mCuO / Mr(Cu=1, O=1); mxt = nCuO * (Mr(Cu=1, S=1, O=4) + 5 * 18); assert nCuO == 0.1 and mxt == 25
L.example("Example 1 — copper(II) sulphate", "Describe how to make crystals of copper(II) sulphate from copper(II) oxide and dilute sulphuric acid, and find the maximum mass of CuSO₄·5H₂O from 8.0 g of CuO (Cu 64, O 16, S 32, H 1).", ["Warm the acid, add CuO until in excess (black solid remains), filter, evaporate to crystallisation point, cool, filter and dry.", "n(CuO) = 8.0 ÷ 80 = 0.10 mol = n(CuSO₄·5H₂O); Mr = 160 + 90 = 250; mass = 0.10 × 250 = 25 g."], "25 g of CuSO₄·5H₂O")
L.example("Example 2 — precipitation", "Choose reagents to prepare insoluble silver chloride and write the ionic equation.", ["Silver chloride is insoluble: mix two soluble salts, silver nitrate (all nitrates soluble) and sodium chloride solution.", "Filter, wash and dry the white precipitate. Ionic equation: Ag⁺(aq) + Cl⁻(aq) → AgCl(s)."], "AgNO₃(aq) + NaCl(aq) → AgCl(s) + NaNO₃(aq)")
L.mcq("Which salt is insoluble in water?", "barium sulphate", ["sodium sulphate", "potassium nitrate", "ammonium chloride"], "Barium sulphate is the insoluble sulphate (used in barium meals for X-rays).")
L.mcq("Which method would you use to prepare sodium nitrate?", "Titration of sodium hydroxide solution with nitric acid", ["Adding excess sodium to acid", "Precipitation", "Filtration"], "Sodium compounds are soluble; use a soluble alkali and titration.")
L.tf("A soluble salt is crystallised by evaporating all the water until the dish is dry.", False, "Evaporate only part of the water, then cool and let crystals form.")
L.num("What mass of zinc oxide (Mr 81) is needed to react with 0.20 mol sulphuric acid? (ZnO + H₂SO₄ → ZnSO₄ + H₂O) Use Zn 65, O 16.", 0.20 * 81, "Ratio 1 : 1; mass = 0.20 × 81 = 16.2 g.", unit="g", tier="approfondissement", tolerance=0.01)
L.open("Explain why the metal oxide is added in excess when making a salt.", "To make sure all the acid is used up so that the solution contains only the salt; the leftover insoluble oxide is simply removed by filtration.", "1 mark: all acid reacts; 1 mark: excess can be filtered off.", tier="approfondissement")
L.problem("A pupil wants to make lead(II) iodide, which is a yellow solid insoluble in water.", [
    part_mcq("Which pair of solutions should be mixed?", "lead(II) nitrate and potassium iodide", ["lead(II) sulphate and potassium iodide", "lead metal and iodine water", "lead(II) carbonate and sodium chloride"], "Lead nitrate is soluble (all nitrates are) and potassium iodide is soluble."),
    part_open("Write the ionic equation.", "Pb²⁺(aq) + 2I⁻(aq) → PbI₂(s)", "1 mark ions; 1 mark balanced; 1 mark state symbols."),
    part_open("Describe how the solid is separated and dried.", "Filter the mixture; wash the residue with distilled water; dry it between filter papers or in a warm oven.", "1 mark filter; 1 mark wash; 1 mark dry."),
    part_mcq("What name is given to the ions Na⁺ and NO₃⁻ that remain in solution?", "spectator ions", ["catalysts", "indicators", "precipitates"], "They do not take part in the reaction.")], tier="examen")
L.sc("All nitrates are", "soluble", ["insoluble", "gases", "acidic"], "Rule of solubility.")
L.sc("Silver chloride is", "an insoluble white solid", ["soluble", "a gas", "yellow and soluble"], "A white precipitate.")
L.sc("To make a salt from an insoluble base you add the base", "in excess", ["in small amounts", "to cold alkali", "after evaporation"], "To use up all the acid.")
L.sc("Crystals are obtained by", "cooling a hot saturated solution", ["filtering a dry solid", "distilling", "chromatography"], "Crystallisation.")
L.sc("Which salt is soluble?", "potassium carbonate", ["calcium carbonate", "lead(II) chloride", "barium sulphate"], "Group I carbonates are soluble.")

L = ch.lesson("tests-analysis", "Tests for gases, cations and anions", minutes=30, prerequisites=["gceol-chemistry-salts-prep"],
    objectives=["State the tests and results for hydrogen, oxygen, carbon dioxide, ammonia and chlorine.", "Identify cations using sodium hydroxide solution.", "Identify chloride, sulphate, carbonate and nitrate ions.", "Plan a simple analysis of an unknown salt."],
    notes=["Colours of hydroxide precipitates (Cu²⁺ light blue, Fe²⁺ green, Fe³⁺ red-brown) and their behaviour in excess NaOH follow the usual school tables; check against the Board's reference list.", "Nitrate test (aluminium with sodium hydroxide, heated, gives ammonia) is included as the usual O-level test."])
L.key("retenir", "Tests for gases", "- **Hydrogen**: lighted splint → squeaky pop.\n- **Oxygen**: glowing splint relights.\n- **Carbon dioxide**: limewater turns milky.\n- **Ammonia**: turns damp red litmus blue; pungent smell.\n- **Chlorine**: bleaches damp litmus paper (turns it white); pale green gas.")
L.key("retenir", "Tests for cations (add NaOH solution drop by drop, then excess)", "- **Cu²⁺**: light blue precipitate, insoluble in excess.\n- **Fe²⁺**: green precipitate; **Fe³⁺**: red-brown precipitate (both insoluble in excess).\n- **Ca²⁺**: white precipitate, insoluble in excess.\n- **Al³⁺, Zn²⁺, Pb²⁺**: white precipitate that **dissolves** in excess NaOH (Al³⁺ and Zn²⁺ also with ammonia: Zn²⁺ dissolves, Al³⁺ does not).\n- **NH₄⁺**: no precipitate; warming gives ammonia gas.")
L.key("retenir", "Tests for anions", "- **Chloride Cl⁻**: add dilute nitric acid then silver nitrate → white precipitate.\n- **Sulphate SO₄²⁻**: add dilute hydrochloric acid then barium chloride → white precipitate.\n- **Carbonate CO₃²⁻**: add dilute acid → fizzing, CO₂ turns limewater milky.\n- **Nitrate NO₃⁻**: warm with aluminium foil and sodium hydroxide → ammonia (turns damp red litmus blue).")
L.key("pieges", "Watch out", "- Always add the acid **before** silver nitrate or barium chloride, to remove carbonates that would also give white precipitates.\n- Say 'dissolves in excess' only if the precipitate disappears; Ca²⁺ and Al³⁺ both give white precipitates, so use the excess test to tell them apart.")
L.illustration(shapes([RECT(40, 60, 50, 110), RECT(41, 120, 48, 49, fill="blue"), T(65, 50, "Cu²⁺", 14), RECT(120, 60, 50, 110), RECT(121, 120, 48, 49, fill="green"), T(145, 50, "Fe²⁺", 14),
                       RECT(200, 60, 50, 110), RECT(201, 120, 48, 49, fill="orange"), T(225, 50, "Fe³⁺", 14), RECT(280, 60, 50, 110), RECT(281, 120, 48, 49, fill="grey"), T(305, 50, "Ca²⁺", 14),
                       T(200, 195, "colour of hydroxide precipitates with NaOH(aq)", 12)], 400, 220),
    "Colours of the precipitates formed when sodium hydroxide solution is added to solutions of some metal ions.", "Four test tubes showing precipitates: blue for copper, green for iron(II), orange-brown for iron(III) and white/grey for calcium.")
L.example("Example 1 — unknown solution", "A solution gives a light blue precipitate with sodium hydroxide, insoluble in excess, and a white precipitate with acidified barium chloride. Identify the salt.", ["Light blue precipitate, insoluble in excess: Cu²⁺ ions.", "White precipitate with acidified barium chloride: SO₄²⁻ ions. The salt is copper(II) sulphate, CuSO₄."], "Copper(II) sulphate")
L.example("Example 2 — white solid", "A white solid fizzes with dilute acid giving a gas that turns limewater milky. Its solution gives a white precipitate with sodium hydroxide that stays in excess. Which compound is it?", ["Fizzing with a gas that turns limewater milky: carbonate ion CO₃²⁻.", "White precipitate insoluble in excess NaOH: calcium ions Ca²⁺. The solid is calcium carbonate."], "Calcium carbonate (CaCO₃)")
L.mcq("What is the test for oxygen?", "A glowing splint relights", ["A lighted splint gives a squeaky pop", "Limewater turns milky", "Damp litmus turns blue"], "Oxygen supports burning.")
L.match("Match the gas with its test.", [["hydrogen", "squeaky pop with a lighted splint"], ["carbon dioxide", "limewater turns milky"], ["ammonia", "turns damp red litmus blue"], ["chlorine", "bleaches damp litmus paper"]], "Standard tests.")
L.mcq("Adding NaOH to a solution gives a green precipitate. The ion is", "Fe²⁺", ["Cu²⁺", "Fe³⁺", "Ca²⁺"], "Iron(II) hydroxide is green.")
L.mcq("A white precipitate forms with NaOH and dissolves in excess. Which ion could it be?", "Zn²⁺ (or Al³⁺ or Pb²⁺)", ["Ca²⁺", "Cu²⁺", "Fe³⁺"], "Amphoteric hydroxides dissolve in excess NaOH.", tier="approfondissement", difficulty=2)
L.open("A pupil adds silver nitrate solution directly to an unknown solution and gets a white precipitate. Why is the result not reliable, and what should be done?", "The white precipitate could be silver carbonate or silver chloride. Add dilute nitric acid first: carbonate reacts and goes, so a precipitate that remains with silver nitrate shows chloride ions.", "1 mark: carbonate also gives a white precipitate; 1 mark: acidify with dilute nitric acid first.", tier="approfondissement", difficulty=2)
L.problem("Solution X is a green salt solution. Tests: (1) NaOH gives a green precipitate insoluble in excess; (2) dilute HNO₃ then AgNO₃ gives a white precipitate.", [
    part_mcq("Which cation is present?", "Fe²⁺", ["Cu²⁺", "Ca²⁺", "Zn²⁺"], "Green hydroxide precipitate insoluble in excess: iron(II)."),
    part_mcq("Which anion is present?", "Cl⁻", ["SO₄²⁻", "CO₃²⁻", "NO₃⁻"], "White precipitate with acidified silver nitrate: chloride."),
    part_open("Name the compound in X.", "Iron(II) chloride (FeCl₂).", "1 mark name; 1 mark formula."),
    part_open("Write the ionic equation for test (2).", "Ag⁺(aq) + Cl⁻(aq) → AgCl(s)", "1 mark ions; 1 mark state symbols.")], tier="examen")
L.sc("Limewater turns milky with", "carbon dioxide", ["hydrogen", "oxygen", "ammonia"], "CO₂ forms insoluble CaCO₃.")
L.sc("Chlorine bleaches", "damp litmus paper", ["limewater", "glowing splint", "silver nitrate"], "Test for chlorine.")
L.sc("A red-brown precipitate with NaOH shows", "Fe³⁺", ["Cu²⁺", "Fe²⁺", "Ca²⁺"], "Iron(III) hydroxide.")
L.sc("A white precipitate with acidified BaCl₂ shows", "sulphate", ["chloride", "nitrate", "carbonate"], "BaSO₄ is insoluble.")
L.sc("Ammonia turns damp red litmus", "blue", ["white", "red", "green"], "It is an alkaline gas.")

# =====================================================================  8. REDOX
ch = p.chapter("redox", "Oxidation and reduction", programRef="GCE O Level Chemistry — Oxidation and reduction (redox): oxygen, hydrogen, electrons, oxidising/reducing agents (to be checked against the official syllabus)")
L = ch.lesson("redox-reactions", "Oxidation, reduction and redox reactions", minutes=30, prerequisites=["gceol-chemistry-formulae-equations"],
    objectives=["Define oxidation and reduction in terms of oxygen, hydrogen and electrons.", "Identify the substance oxidised and reduced in an equation.", "Define oxidising and reducing agents and name common ones.", "Write simple half-equations."],
    notes=["Oxidation numbers are not used here; check whether the syllabus requires them (they are in some syllabi).", "Colour changes for acidified potassium manganate(VII) (purple → colourless) and dichromate(VI) (orange → green) are the standard tests."])
L.key("definition", "Oxidation and reduction", "**Oxidation** = **gain of oxygen**, **loss of hydrogen**, or **loss of electrons**. **Reduction** = **loss of oxygen**, **gain of hydrogen**, or **gain of electrons**. Remember **OIL RIG**: Oxidation Is Loss (of electrons), Reduction Is Gain. They always happen together in a **redox reaction**.")
L.key("retenir", "Oxidising and reducing agents", "An **oxidising agent** oxidises another substance (it gains electrons, so it is itself reduced): oxygen, chlorine, acidified potassium manganate(VII) (purple → colourless), acidified potassium dichromate(VI) (orange → green). A **reducing agent** reduces another substance (it loses electrons, so it is itself oxidised): hydrogen, carbon, carbon monoxide, metals, iodide ions.")
L.key("formule", "Half-equations", "Show electrons being lost or gained:\n\n- Oxidation: Mg → Mg²⁺ + 2e⁻\n- Reduction: Cu²⁺ + 2e⁻ → Cu\n\nAdd the two, cancelling electrons, to get the overall ionic equation: Mg + Cu²⁺ → Mg²⁺ + Cu. A **displacement** reaction is a redox reaction.")
L.key("pieges", "Watch out", "- The substance oxidised is the **reducing agent**; the substance reduced is the **oxidising agent**.\n- Rusting and burning are oxidation; extraction of metals from ores is reduction.\n- Electrons in half-equations must balance on both sides of the arrow, including charge.")
L.illustration(shapes([CIRCLE(90, 110, 36), T(90, 117, "Mg", 16), CIRCLE(310, 110, 36), T(310, 117, "Cu²⁺", 14), LINE(130, 110, 270, 110, arrow="end", color="red", width=3), T(200, 90, "2e⁻", 14),
                       T(90, 175, "oxidised", 14), T(310, 175, "reduced", 14), T(90, 195, "(loses electrons)", 12), T(310, 195, "(gains electrons)", 12)], 400, 220),
    "Magnesium displaces copper: magnesium loses two electrons (oxidation), copper ions gain them (reduction).", "A magnesium atom on the left with a red arrow of 2 electrons to a copper(II) ion on the right.")
L.example("Example 1 — copper oxide and hydrogen", "In CuO + H₂ → Cu + H₂O, identify what is oxidised and reduced, and the oxidising and reducing agents.", ["CuO loses oxygen: copper(II) oxide is **reduced**. H₂ gains oxygen (forms H₂O): hydrogen is **oxidised**.", "The oxidising agent is CuO (it oxidises H₂); the reducing agent is H₂ (it reduces CuO)."], "CuO reduced (oxidising agent); H₂ oxidised (reducing agent)")
L.example("Example 2 — displacement", "Magnesium is added to copper(II) sulphate solution. Write the half-equations and the overall ionic equation.", ["Mg → Mg²⁺ + 2e⁻ (oxidation); Cu²⁺ + 2e⁻ → Cu (reduction).", "Add: Mg + Cu²⁺ → Mg²⁺ + Cu. The blue colour fades and brown copper is deposited."], "Mg + Cu²⁺ → Mg²⁺ + Cu")
L.mcq("In the reaction 2Mg + O₂ → 2MgO, magnesium is", "oxidised", ["reduced", "unchanged", "a catalyst"], "It gains oxygen (and loses electrons).")
L.mcq("A substance that loses electrons is", "oxidised", ["reduced", "neutralised", "decomposed"], "OIL RIG: Oxidation Is Loss.")
L.tf("Rusting of iron is an example of oxidation.", True, "Iron gains oxygen to form iron(III) oxide.")
L.mcq("Acidified potassium manganate(VII) changes from purple to colourless when warmed with a substance. The substance is acting as", "a reducing agent", ["an oxidising agent", "an indicator", "a catalyst"], "The purple MnO₄⁻ is reduced, so the substance is the reducing agent.", tier="approfondissement")
L.open("In the reaction Zn + Cu²⁺ → Zn²⁺ + Cu, state which species is reduced and explain in terms of electrons.", "Cu²⁺ is reduced because it gains two electrons to become Cu atoms; zinc is oxidised because it loses two electrons.", "1 mark: Cu²⁺ reduced; 1 mark: gain of electrons; 1 mark: zinc oxidised.", tier="approfondissement")
L.problem("Iron(III) oxide is reduced to iron by carbon monoxide: Fe₂O₃ + 3CO → 2Fe + 3CO₂.", [
    part_mcq("Which substance is oxidised?", "carbon monoxide", ["iron(III) oxide", "iron", "carbon dioxide"], "CO gains oxygen to become CO₂."),
    part_mcq("What is the reducing agent?", "carbon monoxide", ["iron(III) oxide", "carbon dioxide", "iron"], "It reduces the iron oxide."),
    part_num("How many moles of CO react with 2 mol of Fe₂O₃?", 6, "Ratio Fe₂O₃ : CO = 1 : 3, so 6 mol.", unit="mol"),
    part_open("Write a half-equation for the reduction of Fe³⁺ to Fe.", "Fe³⁺ + 3e⁻ → Fe", "1 mark for formulae; 1 mark for balanced charge.")], tier="examen")
L.sc("Reduction is", "gain of electrons", ["loss of electrons", "gain of oxygen", "loss of protons"], "OIL RIG.")
L.sc("An oxidising agent is itself", "reduced", ["oxidised", "neutral", "a metal"], "It takes electrons from the other substance.")
L.sc("In Mg → Mg²⁺ + 2e⁻ magnesium is", "oxidised", ["reduced", "displaced only", "a catalyst"], "It loses electrons.")
L.sc("Which is a reducing agent?", "hydrogen", ["oxygen", "chlorine", "potassium manganate(VII)"], "Hydrogen removes oxygen from oxides.")
L.sc("Displacement reactions of metals are", "redox reactions", ["acid-base reactions", "precipitation only", "physical changes"], "Electron transfer between atoms and ions.")

# =====================================================================  9. ELECTROLYSIS
ch = p.chapter("electrolysis", "Electrolysis", programRef="GCE O Level Chemistry — Electrolysis of molten and aqueous electrolytes, electroplating, purification of copper (to be checked against the official syllabus)")
L = ch.lesson("electrolysis-basics", "Electrolysis of molten and aqueous electrolytes", minutes=35, prerequisites=["gceol-chemistry-redox-reactions", "gceol-chemistry-structure-properties"],
    objectives=["Define electrolysis, electrolyte, anode and cathode.", "Predict the products at each electrode for molten and aqueous electrolytes.", "Write half-equations for the electrode reactions.", "Describe electroplating and purification of copper."],
    notes=["Selective discharge rules are given in simplified form (less reactive cation, halide before hydroxide in concentrated solution); the Board's exact wording should be checked.", "Quantitative electrolysis (Faraday) is not covered here; check whether it is required."])
L.key("definition", "Electrolysis", "**Electrolysis** is the decomposition of an ionic compound (**electrolyte**, molten or in solution) by an electric current. **Cathode** (−): positive ions (cations) move there and **gain electrons** (reduction). **Anode** (+): negative ions (anions) move there and **lose electrons** (oxidation). Electrodes are usually inert (graphite or platinum). Mnemonic: **PANIC**: Positive Anode, Negative Is Cathode.")
L.key("retenir", "Molten and aqueous", "**Molten** compounds give the metal at the cathode and the non-metal at the anode: molten lead(II) bromide → lead (cathode) + bromine (anode). In **aqueous** solutions water also supplies H⁺ and OH⁻ ions: at the cathode the **less reactive** of the metal and hydrogen is formed (hydrogen unless the metal is below hydrogen, e.g. copper or silver); at the anode a halogen forms from concentrated halide solutions, otherwise oxygen.")
L.key("formule", "Common examples (inert electrodes)", "- **Brine** (concentrated NaCl solution): H₂ at the cathode, Cl₂ at the anode; NaOH remains in solution.\n- **Dilute sulphuric acid**: H₂ (cathode) and O₂ (anode) in the volume ratio **2 : 1**.\n- **Copper(II) sulphate** with graphite: copper at the cathode, O₂ at the anode (blue colour fades).\n- Half-equations: 2H⁺ + 2e⁻ → H₂; 2Cl⁻ → Cl₂ + 2e⁻; 4OH⁻ → 2H₂O + O₂ + 4e⁻; Cu²⁺ + 2e⁻ → Cu.")
L.key("methode", "Uses", "**Electroplating**: the object to be plated is the **cathode**, the plating metal is the **anode**, and the electrolyte is a salt of the plating metal (e.g. nickel or chromium plating of steel, silver plating of cutlery). **Purifying copper**: impure copper is the anode (it dissolves), pure copper the cathode (copper builds up). Aluminium is also extracted by electrolysis (see the extraction lesson).")
L.key("pieges", "Watch out", "- Electrons flow in the wires, ions move in the electrolyte; solid ionic compounds do not conduct.\n- Cations go to the **cathode**; anions go to the **anode**.\n- With copper electrodes, the anode itself dissolves (the reaction is different from graphite).")
L.illustration(shapes([RECT(110, 90, 180, 110), RECT(111, 110, 178, 89, fill="blue", stroke="blue"), RECT(158, 50, 8, 120), RECT(238, 50, 8, 120),
                       LINE(162, 50, 162, 25), LINE(162, 25, 192, 25), LINE(192, 20, 192, 30, width=5), LINE(204, 12, 204, 38, width=2), LINE(204, 25, 242, 25), LINE(242, 25, 242, 50),
                       T(95, 70, "cathode (−)", 12), T(310, 70, "anode (+)", 12), T(200, 225, "electrolyte", 12)], 400, 240),
    "An electrolysis cell: the negative electrode is the cathode, the positive electrode is the anode.", "A beaker of electrolyte with two vertical electrodes joined by wires to a battery, labelled cathode (negative) and anode (positive).")
L.example("Example 1 — dilute sulphuric acid", "Dilute sulphuric acid is electrolysed with inert electrodes. Name the gases and say what volume of oxygen is made when 30 cm³ of hydrogen is collected.", ["Cathode: 2H⁺ + 2e⁻ → H₂. Anode: 4OH⁻ → 2H₂O + O₂ + 4e⁻. Hydrogen and oxygen are formed (water is decomposed).", "Volume ratio H₂ : O₂ = 2 : 1, so oxygen = 30 ÷ 2 = 15 cm³."], "H₂ and O₂; 15 cm³ of oxygen")
L.example("Example 2 — brine", "Concentrated sodium chloride solution is electrolysed using graphite electrodes. Name the products at each electrode and what is left in solution.", ["Cathode: H⁺ is discharged rather than Na⁺ (sodium is more reactive than hydrogen): 2H⁺ + 2e⁻ → H₂.", "Anode: chloride ions give chlorine: 2Cl⁻ → Cl₂ + 2e⁻. Na⁺ and OH⁻ remain, so sodium hydroxide solution is left."], "H₂ at the cathode; Cl₂ at the anode; NaOH in solution")
L.mcq("At which electrode are metals deposited?", "cathode", ["anode", "both", "neither"], "Metal ions gain electrons at the negative electrode.")
L.mcq("Molten lead(II) bromide is electrolysed. What is formed at the anode?", "bromine", ["lead", "oxygen", "hydrogen"], "Br⁻ ions lose electrons: 2Br⁻ → Br₂ + 2e⁻.")
L.tf("Solid sodium chloride conducts electricity during electrolysis.", False, "The ions are fixed in the solid; it must be molten or dissolved.")
L.num("In the electrolysis of dilute sulphuric acid, 24 cm³ of oxygen is collected at the anode. How many cm³ of hydrogen are collected?", 48, "Ratio H₂ : O₂ = 2 : 1, so 2 × 24 = 48 cm³.", unit="cm³", tier="approfondissement")
L.open("Describe how you would silver-plate a metal spoon. State the cathode, the anode and the electrolyte.", "Make the spoon (cleaned) the cathode, a silver rod the anode, and use silver nitrate solution as electrolyte; pass a direct current so silver is deposited on the spoon: Ag⁺ + e⁻ → Ag.", "1 mark: spoon is the cathode; 1 mark: silver anode; 1 mark: silver nitrate; 1 mark: equation or description of deposit.", tier="approfondissement", difficulty=2)
L.problem("Copper(II) sulphate solution is electrolysed in two experiments: (A) graphite electrodes; (B) copper electrodes.", [
    part_mcq("What is deposited at the cathode in both experiments?", "copper", ["hydrogen", "oxygen", "sulphur"], "Cu²⁺ is discharged because copper is less reactive than hydrogen."),
    part_mcq("In experiment (A) what is formed at the anode?", "oxygen", ["copper", "hydrogen", "sulphate"], "OH⁻ ions are discharged: 4OH⁻ → 2H₂O + O₂ + 4e⁻."),
    part_mcq("In experiment (B) what happens to the anode?", "It dissolves (gets smaller)", ["It gets bigger", "Nothing changes", "It becomes oxygen gas"], "Copper atoms lose electrons: Cu → Cu²⁺ + 2e⁻, so the blue colour stays the same."),
    part_open("Write the half-equation for the change at the cathode.", "Cu²⁺ + 2e⁻ → Cu", "1 mark formulae; 1 mark balanced.")], tier="examen")
L.sc("The anode is the", "positive electrode", ["negative electrode", "salt bridge", "electrolyte"], "Anions are attracted to the anode.")
L.sc("An electrolyte is", "an ionic compound molten or in solution", ["a solid metal", "a covalent gas", "a catalyst"], "It conducts by moving ions.")
L.sc("Electrolysis of brine gives chlorine at the", "anode", ["cathode", "battery", "salt"], "Cl⁻ is oxidised at the anode.")
L.sc("In electroplating the object to be plated is the", "cathode", ["anode", "electrolyte", "power supply"], "Metal ions are reduced onto it.")
L.sc("Hydrogen and oxygen from water electrolysis are in volume ratio", "2 : 1", ["1 : 2", "1 : 1", "3 : 1"], "H₂O has twice as many H as O atoms.")

# =====================================================================  10. ENERGY, RATES, EQUILIBRIUM
ch = p.chapter("energetics", "Energy changes and rates of reaction", programRef="GCE O Level Chemistry — Energetics (exothermic/endothermic), rates of reaction, catalysts (to be checked against the official syllabus)")
L = ch.lesson("energy-changes", "Energy changes in chemical reactions", minutes=30, prerequisites=["gceol-chemistry-acids-bases"],
    objectives=["Distinguish exothermic and endothermic reactions with examples.", "Draw and interpret energy level diagrams.", "Explain energy changes in terms of bond breaking and bond making.", "Calculate heat energy released from temperature changes (q = mcΔT)."],
    notes=["Specific heat capacity of water-based solutions taken as 4.2 J/g °C and density 1 g/cm³.", "Neutralisation enthalpy about −57 kJ/mol for strong acid with strong alkali is the standard textbook value; the example data are illustrative.", "Fuels in Cameroonian homes (firewood, charcoal, gas) are mentioned without quoting energy values."])
L.key("definition", "Exothermic and endothermic", "An **exothermic** reaction **releases** heat to the surroundings (temperature rises, ΔH negative): burning fuels, neutralisation, respiration, rusting. An **endothermic** reaction **absorbs** heat (temperature falls, ΔH positive): thermal decomposition of calcium carbonate, photosynthesis, dissolving ammonium nitrate.")
L.key("retenir", "Bond energy idea", "Reactions involve **breaking** bonds in the reactants (needs energy, endothermic) and **making** bonds in the products (releases energy, exothermic). If more energy is released making bonds than is used breaking them, the reaction is exothermic. The extra energy needed to start a reaction is the **activation energy**.")
L.formula(r"q = mc\Delta T", "m in g of solution, c = 4.2 J/g °C, ΔT in °C; ΔH = −q ÷ moles")
L.key("pieges", "Watch out", "- On a diagram of an exothermic reaction the **products are lower** than the reactants; for an endothermic reaction they are higher.\n- ΔH is negative for exothermic reactions.\n- Burning charcoal or gas indoors without ventilation can produce poisonous carbon monoxide.")
L.illustration(plot(0, 10, 0, 100, segments=[(0, 60, 2, 60, "blue", False), (2, 60, 5, 92, "blue", False), (5, 92, 8, 25, "blue", False), (8, 25, 10, 25, "blue", False)],
                    points=[(1, 68, "reactants", "red"), (9, 33, "products", "red"), (5, 97, "peak", "red")], grid=20, xlabel="progress of reaction", ylabel="energy", w=400, h=260),
    "Energy level diagram of an exothermic reaction: products have less energy than reactants; the peak is the activation energy.", "A line starting at a high energy level for reactants, rising to a peak and falling to a lower level for products.")
q = 100 * 4.2 * 6.8; n_ = 1.0 * 50 / 1000; dH = -q / n_ / 1000; close(dH, -57.12, 0.01)
L.example("Example 1 — neutralisation", "50 cm³ of 1.0 mol/dm³ HCl is mixed with 50 cm³ of 1.0 mol/dm³ NaOH. The temperature rises by 6.8 °C. Find the heat released and the energy change per mole of water formed (assume 1 g/cm³ and 4.2 J/g °C).", ["Mass of solution = 100 g. q = mcΔT = 100 × 4.2 × 6.8 = 2856 J.", "Moles of HCl = 1.0 × 0.050 = 0.050 mol (= moles of water formed). ΔH = −2856 ÷ 0.050 = −57 120 J/mol ≈ −57.1 kJ/mol."], "q = 2856 J; ΔH ≈ −57 kJ/mol")
qw = 200 * 4.2 * 12.5; per_g = qw / 0.8; close(qw, 10500); close(per_g, 13125)
L.example("Example 2 — energy of a fuel", "Burning 0.80 g of a fuel in a spirit burner heats 200 g of water by 12.5 °C. Estimate the energy released per gram (ignore heat losses).", ["q = mcΔT = 200 × 4.2 × 12.5 = 10 500 J.", "Energy per gram = 10 500 ÷ 0.80 = 13 125 J/g ≈ 13.1 kJ/g. (The real value is higher because much heat is lost.)"], "≈ 13.1 kJ/g (an underestimate)")
L.mcq("Which of these is an endothermic process?", "dissolving ammonium nitrate in water", ["burning charcoal", "neutralisation", "respiration"], "The temperature falls when ammonium nitrate dissolves.")
L.tf("In an exothermic reaction the products have less energy than the reactants.", True, "The difference is released as heat.")
L.num("A reaction in 50 g of solution raises the temperature by 8 °C (c = 4.2 J/g °C). Find the heat released in joules.", 50 * 4.2 * 8, "q = 50 × 4.2 × 8 = 1680 J.", unit="J")
L.mcq("Breaking bonds is", "endothermic, and making bonds is exothermic", ["exothermic, and making bonds is endothermic", "always exothermic", "neither"], "Energy is needed to break bonds; energy is released when bonds form.", tier="approfondissement")
L.open("A pupil in Bamenda adds a spatula of a white salt to water and the temperature drops by 5 °C. State what this shows and sketch the energy level diagram in words.", "The dissolving is endothermic: it takes in heat from the water. On the energy diagram the products (solution) are higher than the reactants (solid and water).", "1 mark: endothermic; 1 mark: heat absorbed from surroundings; 1 mark: products higher.", tier="approfondissement")
L.problem("In a school experiment, 25 g of water is heated by burning 0.50 g of methanol and its temperature rises from 20.0 °C to 40.0 °C (c = 4.2 J/g °C).", [
    part_num("Calculate the temperature rise in °C.", 20, "40.0 − 20.0 = 20 °C.", unit="°C"),
    part_num("Calculate the heat absorbed by the water in joules.", 25 * 4.2 * 20, "q = 25 × 4.2 × 20 = 2100 J.", unit="J"),
    part_num("Calculate the energy per gram of methanol in J/g.", 2100 / 0.50, "2100 ÷ 0.50 = 4200 J/g.", unit="J/g"),
    part_open("Give two reasons why the true energy value is greater.", "Heat is lost to the air and the apparatus; combustion may be incomplete; some heat warms the container.", "1 mark each; any two.")], tier="examen")
L.sc("An exothermic reaction", "releases heat", ["absorbs heat", "has no energy change", "cools the surroundings"], "Temperature of surroundings rises.")
L.sc("Photosynthesis is", "endothermic", ["exothermic", "a neutralisation", "a combustion"], "Absorbs light energy.")
L.sc("Activation energy is the energy needed to", "start the reaction", ["finish the reaction", "cool the products", "dissolve the salt"], "The energy hurdle.")
L.sc("q = mcΔT gives", "heat energy", ["mass", "concentration", "volume"], "Heat from temperature change.")
L.sc("Neutralisation is usually", "exothermic", ["endothermic", "not a reaction", "a physical change"], "The temperature rises.")

L = ch.lesson("rates-reaction", "Rates of reaction", minutes=30, prerequisites=["gceol-chemistry-energy-changes"],
    objectives=["Describe how rate can be measured.", "State the factors affecting rate: concentration, temperature, surface area, catalyst.", "Explain the factors with collision theory.", "Interpret rate graphs and calculate average rates."],
    notes=["The statement that rate roughly doubles for each 10 °C rise is a rule of thumb, not an exact law; it is used here only for illustration."])
L.key("definition", "Rate of reaction", "The **rate** is how fast reactants are used up or products formed. It can be measured by the volume of gas produced per unit time, loss of mass of the flask, or the time for a cross to disappear. **Average rate** = change in quantity ÷ time taken. On a graph of product against time, the **gradient** is the rate: steep at the start, flat when the reaction has stopped.")
L.key("retenir", "Factors and collision theory", "Reactions occur when particles **collide with enough energy** (activation energy). Rate increases with:\n\n- **Higher concentration** (or pressure of gases): more particles in the same volume, more collisions per second.\n- **Higher temperature**: particles move faster, more collisions and a larger fraction have enough energy (mainly this).\n- **Larger surface area** (smaller pieces, powder): more particles exposed.\n- **A catalyst**: provides a path with lower activation energy and is not used up.")
L.key("methode", "Catalysts", "A **catalyst** speeds up a reaction without being used up or changing the amount of product. Examples: manganese(IV) oxide for H₂O₂ → H₂O + O₂; iron in the Haber process; vanadium(V) oxide in the contact process; **enzymes** in living things (and yeast in fermentation).")
L.key("pieges", "Watch out", "- A catalyst does not change the **amount** of product, only how fast it forms.\n- Temperature raises the rate mainly because more collisions have enough energy, not only because they are more frequent.\n- Fair test: change one factor at a time.")
L.illustration(plot(0, 30, 0, 70, curves=[("60*(1-exp(-0.15*x))", "blue", "fast"), ("60*(1-exp(-0.05*x))", "red", "slow")], grid=10, xlabel="time (s)", ylabel="volume of gas (cm³)", w=400, h=260),
    "Volume of gas against time: the faster reaction (blue) is steeper at first; both reach the same final volume.", "Two rising curves flattening at 60 cm3, one steeper (fast) and one gentler (slow).")
data = [(0, 0), (20, 30), (40, 48), (60, 54)]
rates = [(data[i + 1][1] - data[i][1]) / (data[i + 1][0] - data[i][0]) for i in range(3)]; close(rates[0], 1.5); close(rates[1], 0.9); close(rates[2], 0.3)
L.example("Example 1 — average rates", "Gas collected from a reaction: 0 cm³ at 0 s, 30 cm³ at 20 s, 48 cm³ at 40 s, 54 cm³ at 60 s. Find the average rate for the first 20 s and for 40–60 s and explain the change.", ["First 20 s: 30 ÷ 20 = 1.5 cm³/s. From 40 s to 60 s: (54 − 48) ÷ 20 = 0.3 cm³/s.", "The rate decreases because the reactants are being used up (lower concentration, fewer collisions)."], "1.5 cm³/s then 0.3 cm³/s")
t2 = 240 / 2 ** 3; assert t2 == 30
L.example("Example 2 — effect of temperature", "A reaction takes 240 s at 20 °C. Assuming (as a rough rule) that the rate doubles for every 10 °C rise, how long will it take at 50 °C?", ["Temperature rise 30 °C = three steps of 10 °C, so the rate is multiplied by 2 × 2 × 2 = 8.", "The time is divided by 8: 240 ÷ 8 = 30 s."], "About 30 s (rule of thumb)")
L.mcq("Which change does NOT increase the rate of reaction of marble chips with acid?", "using fewer, larger marble chips", ["using powdered marble", "heating the acid", "using more concentrated acid"], "Larger pieces have a smaller surface area.")
L.tf("A catalyst is used up during a reaction.", False, "It is unchanged at the end, though it may change form temporarily.")
L.num("20 cm³ of gas is produced in 40 s. Find the average rate in cm³/s.", 0.5, "20 ÷ 40 = 0.5 cm³/s.", unit="cm³/s")
L.mcq("Why does a lump of charcoal burn more slowly than charcoal dust?", "The dust has a much larger surface area", ["The dust is hotter", "Lumps have less carbon", "Lumps contain a catalyst"], "More surface exposed to oxygen: more collisions.", tier="approfondissement")
L.open("Explain, using collision theory, why increasing the temperature increases the rate.", "At higher temperature the particles move faster, so they collide more often and, more importantly, a greater fraction of collisions has energy at or above the activation energy, so more collisions are successful.", "1 mark: more frequent collisions; 1 mark: more energetic collisions/greater fraction above activation energy.", tier="approfondissement", difficulty=2)
L.problem("Hydrogen peroxide solution decomposes slowly: 2H₂O₂ → 2H₂O + O₂. A pupil adds manganese(IV) oxide and records the oxygen volume: 0 s: 0 cm³; 10 s: 24 cm³; 20 s: 36 cm³; 30 s: 42 cm³.", [
    part_num("Calculate the average rate in the first 10 s in cm³/s.", 2.4, "24 ÷ 10 = 2.4 cm³/s.", unit="cm³/s", tolerance=0.01),
    part_num("Calculate the average rate between 20 s and 30 s.", 0.6, "(42 − 36) ÷ 10 = 0.6 cm³/s.", unit="cm³/s", tolerance=0.01),
    part_mcq("What is the role of manganese(IV) oxide?", "It is a catalyst: it speeds up the reaction and is not used up", ["It is a reactant", "It is a product", "It lowers the temperature"], "A catalyst gives a lower activation energy path."),
    part_open("Why does the rate decrease with time?", "The hydrogen peroxide is being used up, so its concentration falls and there are fewer collisions per second.", "1 mark: reactant used up; 1 mark: fewer collisions.")], tier="examen")
L.sc("Which does NOT affect the rate?", "the colour of the container", ["temperature", "concentration", "surface area"], "Colour has no effect on collisions.")
L.sc("A catalyst", "lowers the activation energy", ["raises the temperature", "is a product", "is a gas"], "It gives an alternative pathway.")
L.sc("A steep gradient on a graph of gas volume against time means the rate is", "fast", ["slow", "zero", "constant"], "More gas per second.")
L.sc("Powdering a solid reactant", "increases the surface area", ["decreases the surface area", "adds a catalyst", "lowers the temperature"], "More particles are exposed.")
L.sc("The reaction stops when the graph is", "horizontal", ["vertical", "curving upwards", "at zero"], "No more product forms.")

# =====================================================================  11. REVERSIBLE REACTIONS
ch = p.chapter("reversible", "Reversible reactions", programRef="GCE O Level Chemistry — Reversible reactions and equilibrium (basic), Haber and contact processes (to be checked against the official syllabus)")
L = ch.lesson("equilibrium-basics", "Reversible reactions and equilibrium", minutes=30, prerequisites=["gceol-chemistry-rates-reaction", "gceol-chemistry-energy-changes"],
    objectives=["Recognise a reversible reaction (⇌) and give examples.", "Describe dynamic equilibrium in a closed system.", "Predict the effect of changing temperature and pressure (Le Chatelier's principle).", "Explain the conditions used in the Haber process."],
    notes=["Haber process conditions quoted as 'about 450 °C, about 200 atmospheres, iron catalyst' are typical textbook figures; actual plants vary.", "Le Chatelier's principle is stated qualitatively. Equilibrium constants are not part of this lesson."])
L.key("definition", "Reversible reactions", "A **reversible reaction** can go both ways, shown by **⇌**. Examples: hydrated copper(II) sulphate (blue) ⇌ anhydrous copper(II) sulphate (white) + water; ammonium chloride ⇌ ammonia + hydrogen chloride; N₂ + 3H₂ ⇌ 2NH₃. In a **closed system** the forward and reverse reactions eventually proceed at the **same rate**: this is **dynamic equilibrium** (the amounts stay constant but both reactions go on).")
L.key("retenir", "Le Chatelier's principle", "If a condition of a system at equilibrium is changed, the position of equilibrium shifts to **oppose** the change.\n\n- **Higher pressure** favours the side with **fewer gas molecules**.\n- **Higher temperature** favours the **endothermic** direction; lower temperature favours the exothermic direction.\n- Removing a product pulls the equilibrium towards the products.\n- A **catalyst** does not change the position of equilibrium; it only makes it reached faster.")
L.key("formule", "The Haber process", "N₂(g) + 3H₂(g) ⇌ 2NH₃(g); the forward reaction is **exothermic**. Four gas moles on the left, two on the right. Conditions: about **450 °C**, about **200 atmospheres**, **iron** catalyst. High pressure favours ammonia; low temperature would favour ammonia too but is too slow, so a **compromise** temperature is used. Unreacted N₂ and H₂ are recycled and ammonia is removed by cooling and liquefying.")
L.key("pieges", "Watch out", "- At equilibrium the reactions have **not stopped**; the rates are equal.\n- A catalyst does not increase the yield at equilibrium.\n- Do not say 'the reaction goes to completion' for a reversible reaction in a closed system.")
L.illustration(plot(0, 10, 0, 12, curves=[("4+6*exp(-0.5*x)", "blue", "forward"), ("4-4*exp(-0.5*x)", "red", "reverse")], grid=2, xlabel="time", ylabel="rate", w=400, h=260),
    "Rates of the forward and reverse reactions approach each other and become equal at equilibrium.", "A falling blue curve and a rising red curve meeting at the same value.")
L.example("Example 1 — effect of pressure", "How does increasing the pressure affect the yield of ammonia in N₂ + 3H₂ ⇌ 2NH₃?", ["Left side has 1 + 3 = 4 moles of gas, right side has 2 moles.", "Higher pressure favours the side with fewer gas moles: the yield of ammonia **increases**."], "Yield of ammonia increases")
L.example("Example 2 — effect of temperature", "The forward reaction in the Haber process is exothermic. Explain why a very high temperature would give a poor yield and why a moderate temperature of about 450 °C is chosen.", ["Raising the temperature shifts the equilibrium in the endothermic direction, which is the reverse reaction, so less ammonia is present at equilibrium.", "But at low temperature the rate is too slow, so about 450 °C with an iron catalyst is a compromise between yield and rate."], "High T lowers yield; 450 °C is a compromise")
L.mcq("At equilibrium in a closed system", "forward and reverse reactions have equal rates", ["the reactions have stopped", "only the forward reaction occurs", "the concentrations are zero"], "Dynamic equilibrium.")
L.tf("A catalyst increases the yield of ammonia at equilibrium.", False, "It speeds up both directions equally; the equilibrium position is unchanged.")
L.num("In N₂ + 3H₂ ⇌ 2NH₃ how many moles of gas are on the left side?", 4, "1 + 3 = 4 moles of gas.", unit="mol")
L.mcq("Which condition would shift CaCO₃ ⇌ CaO + CO₂ (endothermic forward) towards CaO?", "higher temperature", ["lower temperature", "adding more CO₂ gas", "adding a catalyst"], "Heat favours the endothermic reaction.", tier="approfondissement")
L.open("In the contact process 2SO₂ + O₂ ⇌ 2SO₃ (exothermic), a vanadium(V) oxide catalyst and about 450 °C are used. Explain why a catalyst is used.", "The catalyst speeds up the reaction so equilibrium is reached quickly at a moderate temperature; it does not change the yield.", "1 mark: increases rate; 1 mark: does not change the position of equilibrium/yield.", tier="approfondissement", difficulty=2)
L.problem("Ammonium chloride is warmed gently in a test tube: NH₄Cl(s) ⇌ NH₃(g) + HCl(g). Damp red litmus and damp blue litmus papers are placed in the mouth of the tube.", [
    part_mcq("Which paper changes first, and to what colour?", "Red litmus turns blue", ["Blue litmus turns red", "Both turn white", "Neither changes"], "NH₃ is lighter than HCl, diffuses faster and is alkaline."),
    part_mcq("The white solid reforms on the cooler part of the tube. This shows that the reaction is", "reversible", ["irreversible", "a neutralisation only", "a combustion"], "NH₃ and HCl recombine on cooling to form NH₄Cl."),
    part_open("State two examples of reversible changes in chemistry.", "Heating hydrated copper(II) sulphate (blue to white) and adding water to regenerate it; N₂ + 3H₂ ⇌ 2NH₃.", "1 mark each."),
    part_open("Explain what is meant by dynamic equilibrium.", "In a closed system the forward and backward reactions continue at the same rate, so the concentrations stay constant.", "1 mark: both reactions continue; 1 mark: same rate/constant concentrations.")], tier="examen")
L.sc("The symbol ⇌ means", "the reaction is reversible", ["the reaction is slow", "the reaction is complete", "the substance is a gas"], "It can go both ways.")
L.sc("Increasing the pressure in the Haber process", "favours ammonia", ["favours nitrogen only", "has no effect", "stops the reaction"], "Fewer gas moles on the product side.")
L.sc("The catalyst in the Haber process is", "iron", ["platinum", "copper", "manganese(IV) oxide"], "Iron is used.")
L.sc("Adding a catalyst to a reaction at equilibrium", "does not change the position of equilibrium", ["increases the yield", "decreases the yield", "removes the product"], "It only speeds things up.")
L.sc("Unreacted nitrogen and hydrogen in the Haber process are", "recycled", ["thrown away", "burnt", "dissolved in water"], "They return to the reactor.")

# =====================================================================  12. AIR AND WATER
ch = p.chapter("air-water", "Air and water", programRef="GCE O Level Chemistry — Air, pollution, water, water treatment, hardness (to be checked against the official syllabus)")
L = ch.lesson("air-pollution", "Air, combustion and air pollution", minutes=30, prerequisites=["gceol-chemistry-redox-reactions"],
    objectives=["State the composition of clean dry air and how oxygen is measured.", "Describe the separation of air by fractional distillation of liquid air.", "Name the main air pollutants, their sources and effects.", "Explain rusting, the greenhouse effect and the carbon cycle in outline."],
    notes=["Composition of dry air quoted as about 78% N₂, 21% O₂, about 0.9% argon and about 0.04% carbon dioxide (standard rounded values; CO₂ is slowly rising).", "Pollutant effects are summarised; check the exact list the Board requires."])
L.key("definition", "Composition of air", "Clean dry air is about **78% nitrogen**, **21% oxygen**, about 0.9% **argon** (and other noble gases) and about 0.04% **carbon dioxide**, plus variable water vapour. Oxygen can be measured by passing a known volume of air over heated copper (2Cu + O₂ → 2CuO) and measuring the decrease in volume. Air is separated by **fractional distillation of liquid air**: oxygen (b.p. −183 °C), argon and nitrogen (b.p. −196 °C) boil off at different temperatures.")
L.key("retenir", "Air pollutants", "- **Carbon monoxide** (incomplete combustion of fuels in engines and charcoal fires): poisonous, it reduces the oxygen carried by blood.\n- **Sulphur dioxide** (burning fuels with sulphur): causes **acid rain**, harms lungs and buildings.\n- **Oxides of nitrogen** (hot engines): acid rain and respiratory problems.\n- **Carbon dioxide** and **methane**: greenhouse gases that trap heat and contribute to global warming.\n- **Unburnt hydrocarbons and soot** from engines and burning of bush and waste.")
L.key("methode", "Rusting", "Iron rusts when exposed to **both oxygen (air) and water**: 4Fe + 3O₂ + 2xH₂O → 2Fe₂O₃·xH₂O (hydrated iron(III) oxide). It is quicker with salt (near the coast). Prevention: **paint, oil or grease, plastic coating, galvanising (coating with zinc)**, using stainless steel or **sacrificial protection** (a more reactive metal such as zinc or magnesium).")
L.key("pieges", "Watch out", "- Pure water alone does not rust iron; oxygen and water must both be present.\n- Carbon monoxide has no smell: never cook with charcoal in a closed room.\n- Do not confuse the greenhouse effect (a natural, useful effect) with the **enhanced** greenhouse effect caused by extra CO₂ and methane.")
L.illustration(bars([("nitrogen", 78, "blue"), ("oxygen", 21, "green"), ("argon and others", 1, "grey")], unit="%", w=400, h=240),
    "Approximate composition of clean dry air (% by volume).", "A bar chart: nitrogen 78 percent, oxygen 21 percent, argon and others about 1 percent.")
vleft = 79; vox = 100 - vleft; pox = vox / 100 * 100; assert pox == 21
L.example("Example 1 — measuring oxygen", "100 cm³ of air is passed repeatedly over heated copper until the volume stops changing. After cooling the volume is 79 cm³. Calculate the percentage of oxygen.", ["Oxygen reacted with copper: volume used = 100 − 79 = 21 cm³.", "Percentage of oxygen = 21 ÷ 100 × 100 = 21%."], "21%")
mC = 12; mCO2 = mC / 12 * 44; vCO2 = mC / 12 * MOLAR_VOL; assert mCO2 == 44 and vCO2 == 24
L.example("Example 2 — charcoal burning", "Complete burning of 12 g of carbon: C + O₂ → CO₂. Find the mass and volume (at 24 dm³/mol) of CO₂ formed. Why is burning charcoal in a closed room dangerous?", ["n(C) = 12 ÷ 12 = 1 mol = n(CO₂); mass = 1 × 44 = 44 g; volume = 1 × 24 = 24 dm³.", "With little air the combustion is incomplete: 2C + O₂ → 2CO. Carbon monoxide is poisonous and colourless."], "44 g; 24 dm³; incomplete burning makes poisonous CO")
L.mcq("Which gas makes up most of dry air?", "nitrogen", ["oxygen", "carbon dioxide", "argon"], "About 78%.")
L.mcq("Which pollutant causes acid rain?", "sulphur dioxide", ["argon", "oxygen", "nitrogen"], "SO₂ dissolves in rain to give acids.")
L.tf("Iron rusts in dry air with no water.", False, "Both water and oxygen are needed.")
mCO = 6 / 12 * 28; assert mCO == 14
L.num("Incomplete combustion: 2C + O₂ → 2CO. Find the mass of CO formed from 6 g of carbon (C 12, O 16).", mCO, "n(C) = 6 ÷ 12 = 0.5 mol = n(CO); mass = 0.5 × 28 = 14 g.", unit="g", tier="approfondissement")
L.open("Explain how galvanising protects a corrugated iron roof from rusting.", "A layer of zinc coats the iron and keeps out air and water; even if scratched, zinc is more reactive than iron and corrodes first (sacrificial protection), protecting the iron.", "1 mark: zinc coating excludes air/water; 1 mark: sacrificial protection since zinc is more reactive.", tier="approfondissement")
L.problem("Exhaust gases from old vehicles in a busy town contain carbon monoxide, oxides of nitrogen and unburnt hydrocarbons.", [
    part_mcq("Which gas is poisonous because it combines with haemoglobin?", "carbon monoxide", ["carbon dioxide", "oxygen", "nitrogen"], "CO reduces oxygen transport in blood."),
    part_mcq("What is the main cause of the formation of oxides of nitrogen in engines?", "Nitrogen and oxygen from the air react at high temperature", ["Nitrogen is in the petrol", "They come from the tyres", "From rust"], "Spark and heat make N₂ and O₂ combine."),
    part_open("Suggest two ways of reducing air pollution from vehicles.", "Regular servicing; catalytic converters; using cleaner fuels; fewer older vehicles; public transport.", "1 mark each; any two."),
    part_open("Explain how acid rain forms.", "Sulphur dioxide and oxides of nitrogen dissolve in rain water to form acids, giving rain with a low pH.", "1 mark gases; 1 mark dissolve in rain to form acids.")], tier="examen")
L.sc("Air contains about what percentage of oxygen?", "21%", ["78%", "1%", "50%"], "Standard value.")
L.sc("Air is separated into its gases by", "fractional distillation of liquid air", ["filtration", "chromatography", "electrolysis"], "Different boiling points.")
L.sc("Rusting needs", "oxygen and water", ["oxygen only", "water only", "carbon dioxide only"], "Both.")
L.sc("A greenhouse gas is", "carbon dioxide", ["nitrogen", "argon", "oxygen"], "It traps heat.")
L.sc("Carbon monoxide is formed by", "incomplete combustion", ["complete combustion", "photosynthesis", "electrolysis"], "Limited oxygen supply.")

L = ch.lesson("water-treatment", "Water, treatment and hardness", minutes=30, prerequisites=["gceol-chemistry-acids-bases"],
    objectives=["Describe tests for water.", "Describe the stages of water treatment.", "Explain temporary and permanent hardness and how to soften water.", "Calculate the mass of scale formed from hard water."],
    notes=["The stage list (screening, coagulation with aluminium sulphate, sedimentation, filtration, chlorination) is the standard textbook sequence; local plants may differ.", "Advice on boiling or chlorinating drinking water is general public health advice; a health worker should check the wording for the intended audience."])
L.key("retenir", "Water and its tests", "Water is a good solvent and is essential for life. Tests for water: **anhydrous copper(II) sulphate** turns from white to **blue**; **cobalt chloride paper** turns from blue to **pink**. These show that water is present, not that it is pure; pure water boils at exactly 100 °C and freezes at 0 °C (normal pressure).")
L.key("methode", "Water treatment", "1. **Screening** removes large objects.\n2. **Coagulation**: aluminium sulphate makes small particles stick together (flocs) and settle in the **sedimentation** tank.\n3. **Filtration** through sand and gravel removes remaining solids.\n4. **Chlorination**: a little chlorine kills bacteria.\n\nTo make water safe at home: **boil** it for a few minutes, or add a suitable chlorine product. This helps prevent diseases such as cholera and typhoid.")
L.key("definition", "Hard water", "**Hard water** contains dissolved **calcium** or **magnesium** ions (from rocks such as limestone). It does not lather easily with soap and forms **scum**; it leaves **scale** in kettles and pipes. **Temporary hardness** (calcium hydrogencarbonate) is removed by **boiling**: Ca(HCO₃)₂ → CaCO₃ + H₂O + CO₂. **Permanent hardness** (calcium or magnesium sulphate) is not removed by boiling. Both can be removed by adding **washing soda** (sodium carbonate, which precipitates the calcium as CaCO₃) or by **ion exchange**.")
L.key("pieges", "Watch out", "- Clear water is not always safe to drink: bacteria cannot be seen.\n- Boiling removes only **temporary** hardness.\n- Soapless detergents lather even in hard water because they do not form scum.")
L.illustration(shapes([RECT(10, 90, 62, 40, radius=6), T(41, 115, "screen", 12), RECT(86, 90, 62, 40, radius=6), T(117, 107, "alum &", 11), T(117, 121, "settling", 11), RECT(162, 90, 62, 40, radius=6), T(193, 115, "filter", 12), RECT(238, 90, 62, 40, radius=6), T(269, 115, "chlorine", 12), RECT(314, 90, 76, 40, radius=6), T(352, 107, "storage", 12), T(352, 121, "and supply", 11),
                       LINE(72, 110, 86, 110, arrow="end"), LINE(148, 110, 162, 110, arrow="end"), LINE(224, 110, 238, 110, arrow="end"), LINE(300, 110, 314, 110, arrow="end")], 400, 200),
    "Stages of water treatment: screening, coagulation and settling, filtration, chlorination, storage.", "Five boxes joined by arrows from screening to filtration, chlorine and supply.")
L.example("Example 1 — removing hardness", "A kettle in a region with limestone rocks forms white scale. Name the scale, give the equation and the mass of scale from 8.1 g of calcium hydrogencarbonate (Mr = 162; CaCO₃ Mr = 100).", ["Boiling decomposes temporary hardness: Ca(HCO₃)₂ → CaCO₃ + H₂O + CO₂. The scale is calcium carbonate.", "n(Ca(HCO₃)₂) = 8.1 ÷ 162 = 0.050 mol = n(CaCO₃); mass = 0.050 × 100 = 5.0 g."], "Calcium carbonate; 5.0 g")
mss = 8.1 / Mr(Ca=1, H=2, C=2, O=6) * Mr(Ca=1, C=1, O=3); close(Mr(Ca=1, H=2, C=2, O=6), 162); close(mss, 5.0)
L.example("Example 2 — which treatment?", "A village uses water from a stream. It is cloudy and may contain bacteria. Suggest simple steps to make it safe to drink.", ["Let it settle and pour through clean cloth or sand to remove cloudiness (sedimentation and filtration).", "Then boil it for a few minutes (or chlorinate it) to kill bacteria."], "Settle, filter, then boil or chlorinate")
L.mcq("Which test shows that a liquid contains water?", "Anhydrous copper(II) sulphate turns blue", ["Limewater turns milky", "Litmus turns red", "A glowing splint relights"], "White CuSO₄ becomes blue hydrated CuSO₄·5H₂O.")
L.mcq("What is the purpose of chlorine in water treatment?", "To kill bacteria", ["To remove calcium ions", "To make water hard", "To filter solids"], "Chlorine is a disinfectant.")
L.tf("Boiling removes permanent hardness.", False, "Only temporary hardness (hydrogencarbonates) is removed by boiling.")
L.num("Find the mass of calcium carbonate formed when 16.2 g of calcium hydrogencarbonate (Mr 162) decomposes (CaCO₃ Mr 100).", 10.0, "n = 16.2 ÷ 162 = 0.100 mol; mass of CaCO₃ = 0.100 × 100 = 10.0 g.", unit="g", tier="approfondissement", tolerance=0.01)
L.open("A pupil finds that soap forms little lather but a lot of scum in the water from a borehole. Explain and suggest how to soften it.", "The water is hard (calcium/magnesium ions react with soap to form scum). Boiling removes temporary hardness; adding washing soda (sodium carbonate) precipitates calcium and magnesium carbonates and removes both kinds; ion exchange also works.", "1 mark: hard water/Ca²⁺ Mg²⁺; 1 mark: scum with soap; 1 mark: softening method.", tier="approfondissement")
L.problem("A town's water works treats river water.", [
    part_mcq("Why is aluminium sulphate added?", "It makes tiny particles stick together and settle", ["It kills bacteria", "It softens the water", "It adds fluoride"], "Coagulation (flocculation)."),
    part_mcq("What is the purpose of the sand filter?", "To remove suspended solids", ["To add chlorine", "To remove dissolved salts", "To change the pH"], "Filtration traps solid particles."),
    part_open("Why is chlorine added at the end?", "To kill any remaining bacteria and keep the water safe as it travels through pipes.", "1 mark: kills bacteria; 1 mark: safe supply/protection in pipes."),
    part_open("Name a disease that can spread through unsafe drinking water and one way to prevent it at home.", "Cholera or typhoid; boil or chlorinate drinking water, store it covered and wash hands.", "1 mark each.")], tier="examen")
L.sc("Anhydrous copper(II) sulphate turns blue when it meets", "water", ["oxygen", "carbon dioxide", "nitrogen"], "Hydrated salt forms.")
L.sc("Hard water contains", "calcium or magnesium ions", ["sodium chloride only", "chlorine", "nitrogen"], "From dissolved rocks.")
L.sc("Temporary hardness is removed by", "boiling", ["filtering", "freezing", "chlorine"], "Ca(HCO₃)₂ decomposes.")
L.sc("Washing soda is", "sodium carbonate", ["sodium chloride", "calcium carbonate", "sodium hydroxide"], "It precipitates calcium ions.")
L.sc("Chlorination kills", "bacteria", ["minerals", "sand", "calcium"], "Disinfection.")

# =====================================================================  13. METALS
ch = p.chapter("metals", "Metals and their extraction", programRef="GCE O Level Chemistry — Properties of metals, reactivity series, corrosion, extraction of iron and aluminium, alloys (to be checked against the official syllabus)")
L = ch.lesson("reactivity-series", "Metals and the reactivity series", minutes=30, prerequisites=["gceol-chemistry-acids-bases", "gceol-chemistry-redox-reactions"],
    objectives=["State physical and chemical properties of metals.", "Order metals in the reactivity series from their reactions with water, acid and oxygen.", "Predict displacement reactions.", "Explain how reactivity relates to corrosion and extraction."],
    notes=["Order used: potassium, sodium, calcium, magnesium, aluminium, (carbon), zinc, iron, (hydrogen), copper, silver, gold. Lead and tin sit between iron and hydrogen in most tables.", "Aluminium appears unreactive because of a protective oxide layer."])
L.key("definition", "Properties of metals", "Metals are typically **shiny, malleable, ductile, hard and dense**, good **conductors** of heat and electricity, with high melting points (exceptions: sodium, mercury). They form **positive ions** and their oxides are **basic**. **Alloys** are mixtures of metals (or metal + carbon) that are often harder or more resistant to corrosion than the pure metal.")
L.key("retenir", "The reactivity series", "**K, Na, Ca, Mg, Al, (C), Zn, Fe, (H), Cu, Ag, Au** (most to least reactive). K, Na and Ca react with **cold water** (H₂ + hydroxide); Mg reacts slowly with cold water and quickly with **steam** (oxide + H₂); Zn and Fe react with steam; metals above **hydrogen** react with dilute acids giving H₂; Cu, Ag, Au do not react with dilute acid or water. Reactivity is also the order of ease of **forming positive ions** and of **corrosion**.")
L.key("methode", "Displacement", "A more reactive metal displaces a less reactive metal from a solution of its salt: Zn + CuSO₄ → ZnSO₄ + Cu (blue colour fades, brown copper forms). A reaction occurs **only** if the added metal is **more** reactive than the metal in the solution. Metal oxides can be displaced similarly: magnesium reacts with copper(II) oxide on heating.")
L.key("pieges", "Watch out", "- Carbon and hydrogen are placed in the series for comparison but are not metals.\n- Aluminium is high in the series but protected by a thin layer of aluminium oxide, so it seems unreactive.\n- Never put potassium or sodium in water without teacher supervision.")
items = [("K", 30), ("Na", 52), ("Ca", 74), ("Mg", 96), ("Al", 118), ("Zn", 140), ("Fe", 162), ("Cu", 184), ("Ag", 206)]
L.illustration(shapes([LINE(120, 25, 120, 215, arrow="end", width=3, color="red"), *[T(180, y_ + 5, sy, 16) for sy, y_ in items], T(300, 40, "most reactive", 12), T(300, 205, "least reactive", 12)], 400, 240),
    "The reactivity series (simplified): potassium at the top, silver at the bottom.", "A vertical arrow with the symbols K, Na, Ca, Mg, Al, Zn, Fe, Cu and Ag from most reactive at the top to least reactive at the bottom.")
L.example("Example 1 — will it react?", "Will iron displace copper from copper(II) sulphate solution? Will copper displace zinc from zinc sulphate solution? Give the equation where a reaction occurs.", ["Iron is above copper in the series, so it displaces it: Fe + CuSO₄ → FeSO₄ + Cu (brown copper forms on the iron).", "Copper is below zinc, so no reaction occurs with zinc sulphate."], "Yes for iron; no for copper")
L.example("Example 2 — placing unknown metals", "Metal P reacts slowly with cold water; Q does not react with water but gives bubbles with dilute acid; R does not react with water or acid. Place P, Q, R in order of reactivity.", ["P reacts with cold water, so it is the most reactive. Q reacts with acid but not cold water: less reactive than P.", "R does not react with acid: the least reactive. Order: P > Q > R."], "P > Q > R")
L.mcq("Which metal does not react with dilute hydrochloric acid?", "copper", ["zinc", "magnesium", "iron"], "Copper is below hydrogen.")
L.mcq("Which is the most reactive?", "potassium", ["sodium", "calcium", "magnesium"], "Top of the series.")
L.tf("Zinc will displace magnesium from magnesium sulphate solution.", False, "Zinc is less reactive than magnesium, so no reaction.")
vh = 2.4 / 24 * 24; assert abs(vh - 2.4) < 1e-9
L.num("Magnesium reacts with excess dilute acid: Mg + 2HCl → MgCl₂ + H₂. What volume of hydrogen (dm³, 24 dm³/mol) is produced by 2.4 g of Mg (Mg 24)?", vh, "n(Mg) = 2.4 ÷ 24 = 0.10 mol = n(H₂); V = 0.10 × 24 = 2.4 dm³.", unit="dm³", tier="approfondissement", tolerance=0.01)
L.open("Explain why aluminium pans do not corrode quickly in everyday use although aluminium is high in the reactivity series.", "Aluminium reacts with oxygen in the air to form a thin, tough, unreactive layer of aluminium oxide that sticks to the metal and prevents further reaction.", "1 mark: oxide layer forms; 1 mark: protects the metal from further reaction.", tier="approfondissement")
L.problem("A pupil drops pieces of four metals W, X, Y and Z into dilute sulphuric acid and into copper(II) sulphate solution. Results: W: fizzes strongly in acid, displaces copper; X: no reaction in acid, no displacement; Y: fizzes slowly in acid, displaces copper; Z: no reaction in acid but displaces silver from silver nitrate.", [
    part_mcq("Which metal is the most reactive?", "W", ["X", "Y", "Z"], "It fizzes most strongly with acid."),
    part_mcq("Which metal is below hydrogen in the reactivity series but above silver?", "Z", ["W", "X", "Y"], "It does not react with acid but displaces silver."),
    part_mcq("Which of the following is the order from most to least reactive?", "W > Y > Z > X", ["X > Z > Y > W", "W > X > Y > Z", "Y > W > Z > X"], "Strong acid reaction, weaker acid reaction, displaces silver only, no displacement."),
    part_open("Write an equation for zinc reacting with copper(II) sulphate.", "Zn + CuSO₄ → ZnSO₄ + Cu", "1 mark formulae; 1 mark balanced.")], tier="examen")
L.sc("Metals form", "positive ions", ["negative ions", "no ions", "molecules"], "They lose electrons.")
L.sc("A more reactive metal will", "displace a less reactive metal from its salt solution", ["never react", "form acids", "turn into non-metals"], "Displacement reaction.")
L.sc("Which metal reacts with cold water?", "sodium", ["copper", "silver", "gold"], "Group I metals do.")
L.sc("An alloy is", "a mixture of metals", ["a pure metal", "a compound of non-metals", "an acid"], "For example brass and steel.")
L.sc("Metals are good", "conductors of electricity", ["insulators", "indicators", "solvents"], "Delocalised electrons.")

L = ch.lesson("extraction-metals", "Extraction of iron and aluminium", minutes=35, prerequisites=["gceol-chemistry-reactivity-series", "gceol-chemistry-electrolysis-basics"],
    objectives=["Explain how the method of extraction depends on the position in the reactivity series.", "Describe the blast furnace extraction of iron with equations.", "Describe the extraction of aluminium by electrolysis.", "Calculate masses of metals from ores; state uses of steel and other alloys."],
    notes=["Cameroon has an aluminium smelter at Edéa (ALUCAM) and bauxite deposits; the smelter and its raw material sources should be checked before being named in class.", "Temperature ~950 °C for molten alumina in cryolite and the 1500–2000 °C zone of the blast furnace are typical textbook values."])
L.key("definition", "Choosing the method", "Most metals occur as **ores** (compounds, mainly oxides). Reactive metals (above carbon: K, Na, Ca, Mg, Al) are extracted by **electrolysis** of molten compounds; metals below carbon (Zn, Fe, Pb, Cu) can be extracted by **reduction with carbon or carbon monoxide** (heating the oxide with coke). Unreactive metals (gold, silver) occur as native metals.")
L.key("formule", "Iron: the blast furnace", "Raw materials: **iron ore** (haematite, Fe₂O₃), **coke** (carbon), **limestone** (CaCO₃), hot **air**. Reactions: C + O₂ → CO₂ (heat); CO₂ + C → 2CO; **Fe₂O₃ + 3CO → 2Fe + 3CO₂** (reduction). Limestone: CaCO₃ → CaO + CO₂; CaO + SiO₂ → CaSiO₃ (**slag**, removes sand). Molten iron and slag run off at the bottom (slag is less dense and floats). Iron is made into **steel** by removing carbon and impurities with oxygen.")
L.key("formule", "Aluminium: electrolysis", "Bauxite is purified to **aluminium oxide (alumina), Al₂O₃**. Alumina has a very high melting point, so it is dissolved in molten **cryolite** (about 950 °C) to save energy. **Cathode** (carbon lining): Al³⁺ + 3e⁻ → Al (molten metal collects at the bottom). **Anode** (carbon): 2O²⁻ → O₂ + 4e⁻; the oxygen burns the carbon anodes to CO₂, so they must be replaced. The process needs **large amounts of electricity**, so smelters are located near cheap hydroelectric power.")
L.key("retenir", "Alloys and uses", "**Steel** (iron + carbon): stronger than iron (buildings, tools); **stainless steel** (iron + chromium + nickel): does not rust. **Brass** (copper + zinc), **bronze** (copper + tin). Aluminium (low density, resists corrosion): cooking pots, aircraft, overhead cables. Copper: electrical wiring. Zinc: galvanising.")
L.key("pieges", "Watch out", "- Iron(III) oxide is reduced by carbon monoxide, not by the limestone; limestone only removes the sand as slag.\n- Aluminium is not extracted in the blast furnace: it is above carbon in the reactivity series.\n- The carbon anodes burn away during aluminium electrolysis and must be replaced regularly.")
L.illustration(shapes([POLY([[160, 40], [240, 40], [265, 120], [235, 200], [165, 200], [135, 120]], fill=None), T(200, 25, "ore, coke, limestone in", 12), T(70, 190, "hot air in", 12), LINE(110, 175, 140, 175, arrow="end"),
                       T(330, 170, "slag out", 12), T(330, 200, "molten iron out", 12), LINE(240, 178, 280, 178, arrow="end"), LINE(235, 196, 270, 205, arrow="end"), LINE(200, 45, 200, 90, arrow="end", color="blue")], 400, 240),
    "A blast furnace: raw materials go in at the top, hot air at the bottom; slag and molten iron are tapped from the base.", "A tall furnace shape with arrows for the raw materials entering at the top, hot air entering near the bottom and slag and molten iron leaving.")
mFe2O3 = Mr(Fe=2, O=3); nFe = 160 / mFe2O3 * 2; mFe = nFe * 56; assert mFe2O3 == 160 and mFe == 112
L.example("Example 1 — iron from haematite", "Calculate the mass of iron obtainable from 160 g of iron(III) oxide: Fe₂O₃ + 3CO → 2Fe + 3CO₂ (Fe 56, O 16).", ["Mr(Fe₂O₃) = 2 × 56 + 3 × 16 = 160; n = 160 ÷ 160 = 1 mol.", "Ratio Fe₂O₃ : Fe = 1 : 2, so n(Fe) = 2 mol; mass = 2 × 56 = 112 g."], "112 g of iron")
mAl2O3 = Mr(Al=2, O=3); nAl = 102 / mAl2O3 * 2; mAl = nAl * 27; assert mAl2O3 == 102 and mAl == 54
L.example("Example 2 — aluminium from alumina", "Calculate the mass of aluminium obtainable from 102 kg of aluminium oxide: 2Al₂O₃ → 4Al + 3O₂ (Al 27, O 16).", ["Mr(Al₂O₃) = 2 × 27 + 3 × 16 = 102, so 102 kg is 1000 mol. Ratio Al₂O₃ : Al = 1 : 2, so n(Al) = 2000 mol.", "Mass = 2000 × 27 = 54 000 g = 54 kg."], "54 kg of aluminium")
L.mcq("Why is aluminium extracted by electrolysis and not by heating with carbon?", "Aluminium is more reactive than carbon", ["Aluminium is less reactive than carbon", "It has no oxide", "Carbon is too expensive"], "Carbon cannot reduce the oxides of metals above it in the series.")
L.mcq("What is the role of limestone in the blast furnace?", "To remove silica as slag", ["To reduce iron oxide", "To burn as fuel", "To cool the furnace"], "CaO + SiO₂ → CaSiO₃.")
L.match("Match the alloy with its main metals.", [["brass", "copper and zinc"], ["bronze", "copper and tin"], ["steel", "iron and carbon"]], "Standard compositions.")
mFe3 = 800 / 160 * 2 * 56 / 1000; close(mFe3, 0.56)
L.num("How many kg of iron can be obtained from 800 kg of pure Fe₂O₃ (Mr 160)? (Fe 56)", 560, "800 kg ÷ 160 = 5 kmol of Fe₂O₃, giving 10 kmol of Fe: 10 × 56 = 560 kg.", unit="kg", tier="approfondissement")
L.open("Explain why alumina is dissolved in molten cryolite in the extraction of aluminium.", "Alumina melts at a very high temperature (over 2000 °C); dissolving it in molten cryolite lowers the temperature needed to about 950 °C, saving a lot of energy, and the molten solution conducts electricity.", "1 mark: alumina has very high melting point; 1 mark: cryolite lowers the operating temperature/saves energy.", tier="approfondissement", difficulty=2)
L.problem("Iron is extracted in a blast furnace. Raw materials are haematite (Fe₂O₃), coke and limestone.", [
    part_mcq("Which substance is the reducing agent for iron(III) oxide?", "carbon monoxide", ["limestone", "oxygen", "slag"], "Fe₂O₃ + 3CO → 2Fe + 3CO₂."),
    part_open("Write the equation for the formation of carbon monoxide from coke.", "CO₂ + C → 2CO (after C + O₂ → CO₂)", "1 mark."),
    part_num("How many moles of CO are needed to reduce 2.0 mol of Fe₂O₃?", 6.0, "Ratio 1 : 3, so 6.0 mol.", unit="mol", tolerance=0.01),
    part_open("Name the slag and write the equation for its formation.", "Calcium silicate; CaO + SiO₂ → CaSiO₃", "1 mark name; 1 mark equation.")], tier="examen")
L.sc("The main ore of iron is", "haematite", ["bauxite", "galena", "cryolite"], "Fe₂O₃.")
L.sc("The ore of aluminium is", "bauxite", ["haematite", "limestone", "coke"], "Source of Al₂O₃.")
L.sc("Cryolite is used to", "lower the melting point of alumina", ["reduce iron oxide", "form slag", "purify copper"], "Saves energy.")
L.sc("Steel is", "an alloy of iron and carbon", ["pure iron", "an ore", "a compound of iron and oxygen"], "Harder than pure iron.")
L.sc("Metals above carbon in the reactivity series are extracted by", "electrolysis", ["heating with carbon", "filtration", "distillation"], "Carbon cannot reduce them.")

# =====================================================================  14. NON-METALS
ch = p.chapter("nonmetals", "Hydrogen, oxygen, nitrogen, sulphur and chlorine", programRef="GCE O Level Chemistry — Non-metals: hydrogen, oxygen, nitrogen and ammonia, fertilisers, sulphur and sulphuric acid, chlorine and halogens (to be checked against the official syllabus)")
L = ch.lesson("hydrogen-oxygen", "Hydrogen and oxygen", minutes=30, prerequisites=["gceol-chemistry-tests-analysis", "gceol-chemistry-reactivity-series"],
    objectives=["Describe the laboratory preparation and test of hydrogen.", "Describe the laboratory preparation and test of oxygen.", "Describe the combustion of elements in oxygen and classify oxides.", "Use gas volume ratios in simple calculations."],
    notes=["Gas volumes are compared at the same temperature and pressure (Avogadro's law).", "Oxide classification: acidic (non-metal oxides such as CO₂, SO₂), basic (most metal oxides), amphoteric (Al₂O₃, ZnO) and neutral (H₂O, CO, NO)."])
L.key("retenir", "Hydrogen", "**Preparation**: zinc (or magnesium) + dilute hydrochloric acid: Zn + 2HCl → ZnCl₂ + H₂. Collected over water or by upward delivery (it is the **least dense** gas). **Test**: a lighted splint gives a **squeaky pop**. Colourless, odourless, burns to form water: 2H₂ + O₂ → 2H₂O. **Uses**: making ammonia (Haber process), hardening oils to make margarine, rocket fuel, cutting and welding (oxy-hydrogen).")
L.key("retenir", "Oxygen", "**Preparation**: decomposing hydrogen peroxide with manganese(IV) oxide as catalyst: 2H₂O₂ → 2H₂O + O₂, collected over water. **Test**: a **glowing splint relights**. Elements burn in oxygen to form oxides: magnesium (bright white flame, MgO), sulphur (blue flame, SO₂), carbon (CO₂). **Uses**: respiration, hospitals, steel making, welding, rocket oxidiser.")
L.key("definition", "Classes of oxide", "- **Basic oxides**: most metal oxides (MgO, CuO); react with acids to form salts; alkali if soluble.\n- **Acidic oxides**: non-metal oxides (CO₂, SO₂, NO₂); react with alkalis; dissolve in water to form acids (SO₂ → acid rain).\n- **Amphoteric**: react with both acids and alkalis (Al₂O₃, ZnO).\n- **Neutral**: H₂O, CO, NO.")
L.key("pieges", "Watch out", "- Hydrogen is explosive with air: always test small samples, away from flames.\n- Manganese(IV) oxide is a catalyst: it is not used up.\n- Carbon dioxide is an acidic oxide, but it is not a gas that supports burning.")
L.illustration(shapes([POLY([[60, 130], [120, 130], [135, 60], [45, 60]], fill=None), RECT(70, 40, 14, 25), LINE(77, 40, 77, 30, width=2), LINE(77, 30, 210, 30), LINE(210, 30, 210, 150),
                       RECT(170, 150, 80, 70), RECT(171, 175, 78, 44, fill="blue", stroke="blue"), T(90, 160, "flask", 12), T(285, 105, "gas jar over water", 12), T(285, 125, "(gas collected)", 12), T(90, 20, "solid + liquid", 12)], 400, 240),
    "Apparatus to prepare a gas: reactants in a flask, gas passed along a delivery tube and collected over water in a gas jar.", "A conical flask connected by a delivery tube to an upside-down gas jar full of water in a trough.")
vH2 = 10; vO2 = vH2 / 2; vsteam = vH2; assert vO2 == 5
L.example("Example 1 — gas volumes", "10 cm³ of hydrogen is burnt in excess oxygen: 2H₂ + O₂ → 2H₂O (steam). What volume of oxygen reacts and what volume of steam forms (same temperature and pressure, above 100 °C)?", ["Gas volumes are in the same ratio as the moles: H₂ : O₂ : H₂O = 2 : 1 : 2.", "Oxygen used = 10 ÷ 2 = 5 cm³; steam formed = 10 cm³."], "5 cm³ of oxygen; 10 cm³ of steam")
nH2O2 = 6.8 / Mr(H=2, O=2); vO = nH2O2 / 2 * MOLAR_VOL; close(nH2O2, 0.2); close(vO, 2.4)
L.example("Example 2 — oxygen from hydrogen peroxide", "What volume of oxygen at room temperature and pressure (24 dm³/mol) is obtained from 6.8 g of H₂O₂ (H 1, O 16)?", ["Mr(H₂O₂) = 34; n = 6.8 ÷ 34 = 0.20 mol. Ratio H₂O₂ : O₂ = 2 : 1, so n(O₂) = 0.10 mol.", "Volume = 0.10 × 24 = 2.4 dm³."], "2.4 dm³")
L.mcq("Which gas relights a glowing splint?", "oxygen", ["hydrogen", "carbon dioxide", "nitrogen"], "Oxygen supports combustion.")
L.mcq("What is the test for hydrogen?", "A lighted splint gives a squeaky pop", ["Limewater turns milky", "A glowing splint relights", "Damp litmus is bleached"], "Hydrogen burns explosively with air.")
L.tf("Manganese(IV) oxide is used up when it catalyses the decomposition of hydrogen peroxide.", False, "A catalyst is not used up.")
L.num("What volume of oxygen is needed to burn 20 cm³ of hydrogen completely (2H₂ + O₂ → 2H₂O)?", 10, "Volume ratio H₂ : O₂ = 2 : 1, so 20 ÷ 2 = 10 cm³.", unit="cm³", tier="approfondissement")
L.match("Match the oxide with its type.", [["magnesium oxide", "basic"], ["sulphur dioxide", "acidic"], ["aluminium oxide", "amphoteric"], ["carbon monoxide", "neutral"]], "Based on the behaviour of each oxide with acids and alkalis.", tier="approfondissement")
L.problem("A pupil prepares hydrogen by adding zinc to dilute hydrochloric acid and collects it over water.", [
    part_open("Write the balanced equation.", "Zn + 2HCl → ZnCl₂ + H₂", "1 mark formulae; 1 mark balanced."),
    part_mcq("Why can hydrogen be collected over water?", "It is almost insoluble in water", ["It is soluble", "It reacts with water", "It is heavier than water"], "Insoluble gases can be collected over water."),
    part_num("Calculate the volume of hydrogen (dm³) from 6.5 g of zinc (Zn 65; 24 dm³/mol).", 2.4, "n(Zn) = 0.10 mol = n(H₂); V = 0.10 × 24 = 2.4 dm³.", unit="dm³", tolerance=0.01),
    part_open("Describe the test for hydrogen.", "Hold a lighted splint at the mouth of the tube: the gas burns with a squeaky pop.", "1 mark.")], tier="examen")
L.sc("Hydrogen is the", "least dense gas", ["most dense gas", "heaviest gas", "gas in air"], "It is lighter than air.")
L.sc("Oxygen is collected", "over water", ["only upwards", "in a syringe only", "never"], "It is slightly soluble but can be collected over water.")
L.sc("Burning magnesium in oxygen forms", "magnesium oxide", ["magnesium hydroxide", "magnesium chloride", "carbon"], "2Mg + O₂ → 2MgO.")
L.sc("A non-metal oxide is usually", "acidic", ["basic", "neutral always", "a metal"], "Example: CO₂, SO₂.")
L.sc("2H₂ + O₂ → 2H₂O. Volume ratio H₂ : O₂ is", "2 : 1", ["1 : 2", "1 : 1", "3 : 1"], "From the coefficients.")

L = ch.lesson("nitrogen-fertilisers", "Nitrogen, ammonia and fertilisers", minutes=30, prerequisites=["gceol-chemistry-equilibrium-basics", "gceol-chemistry-moles-masses"],
    objectives=["Describe the properties and uses of nitrogen.", "Describe the preparation, test, properties and uses of ammonia.", "Explain the need for fertilisers and name common ones.", "Calculate the percentage of nitrogen in fertilisers and describe environmental effects."],
    notes=["N:P:K labels refer to nitrogen, phosphorus and potassium; the content values quoted are computed from formulae, not from product labels.", "Fertiliser use in Cameroonian farming is mentioned in general terms; no statistics are given."])
L.key("retenir", "Nitrogen and ammonia", "**Nitrogen** (N₂) makes up 78% of air; it is **unreactive** (strong triple bond), used to fill food packets and as liquid nitrogen coolant. **Ammonia** (NH₃): made in the lab by heating an ammonium salt with an alkali: 2NH₄Cl + Ca(OH)₂ → CaCl₂ + 2H₂O + 2NH₃; dried with **quicklime** (CaO); collected by **upward delivery** (lighter than air). **Test**: turns damp red litmus **blue**. Very soluble in water, giving an alkaline solution; it reacts with acids to form **ammonium salts**: NH₃ + HCl → NH₄Cl.")
L.key("retenir", "Fertilisers", "Plants need **nitrogen (N)** for leaves and protein, **phosphorus (P)** for roots and **potassium (K)** for flowers and fruit. Fertilisers replace nutrients removed by crops. Common ones: **ammonium nitrate** NH₄NO₃, **ammonium sulphate** (NH₄)₂SO₄, **urea** CO(NH₂)₂ and **NPK** mixtures. Made by neutralising ammonia with acids. **Problem**: excess fertiliser washes into rivers and lakes (**eutrophication**): algae grow, then die and rot, using up oxygen so fish die.")
L.key("pieges", "Watch out", "- Ammonia is lighter than air and very soluble: it cannot be collected over water or dried with concentrated sulphuric acid or calcium chloride (both react with it).\n- Compare fertilisers by **% nitrogen** (mass), not by name.\n- Do not store or apply fertilisers where they can run into drinking water.")
pcts = [("ammonium sulphate", round(28 / Mr(N=2, H=8, S=1, O=4) * 100, 1), "orange"), ("ammonium nitrate", round(28 / Mr(N=2, H=4, O=3) * 100, 1), "blue"), ("urea", round(28 / Mr(C=1, O=1, N=2, H=4) * 100, 1), "green")]
close(pcts[0][1], 21.2); close(pcts[1][1], 35.0); close(pcts[2][1], 46.7)
L.illustration(bars(pcts, unit="% N", w=400, h=240), "Percentage of nitrogen by mass in three fertilisers (computed from their formulae).", "A bar chart: ammonium sulphate 21.2 percent, ammonium nitrate 35 percent and urea 46.7 percent nitrogen.")
L.example("Example 1 — % nitrogen in urea", "Calculate the percentage of nitrogen in urea, CO(NH₂)₂ (C 12, O 16, N 14, H 1).", ["Mr = 12 + 16 + 2 × (14 + 2) = 12 + 16 + 32 = 60. Nitrogen in the formula: 2 × 14 = 28.", "% N = 28 ÷ 60 × 100 = 46.7%."], "46.7%")
mN = 100 * 0.35; assert mN == 35
L.example("Example 2 — which fertiliser?", "A farmer has 100 kg of ammonium nitrate (35% N) and 100 kg of ammonium sulphate (21.2% N). Which supplies more nitrogen and how much more?", ["Nitrogen in ammonium nitrate = 100 × 0.35 = 35.0 kg; in ammonium sulphate = 100 × 0.212 = 21.2 kg.", "Difference = 35.0 − 21.2 = 13.8 kg more from ammonium nitrate."], "Ammonium nitrate: 13.8 kg more")
L.mcq("Which gas turns damp red litmus blue?", "ammonia", ["nitrogen", "hydrogen", "oxygen"], "Ammonia is alkaline.")
L.mcq("Why is ammonia collected by upward delivery?", "It is less dense than air", ["It is heavier than air", "It is insoluble", "It reacts with air"], "Light gases rise.")
L.tf("Nitrogen is very reactive because it makes up most of the air.", False, "Nitrogen is unreactive because of its strong triple bond.")
pam = 28 / Mr(N=2, H=4, O=3) * 100
L.num("Calculate the percentage of nitrogen in ammonium nitrate, NH₄NO₃ (N 14, H 1, O 16).", pam, "Mr = 80; N = 28; 28 ÷ 80 × 100 = 35%.", unit="%", tolerance=0.1, tier="approfondissement")
L.open("Explain how excess fertiliser can kill fish in a river.", "Fertiliser runs into the water and causes rapid growth of algae (eutrophication); when the algae die, bacteria decompose them using up dissolved oxygen, so fish and other animals die.", "1 mark: runs into river/algae grow; 1 mark: bacteria use up oxygen; 1 mark: fish die.", tier="approfondissement")
L.problem("Ammonium sulphate is made by neutralising ammonia solution with sulphuric acid: 2NH₃ + H₂SO₄ → (NH₄)₂SO₄ (N 14, H 1, S 32, O 16).", [
    part_num("Calculate the Mr of ammonium sulphate.", 132, "2 × (14 + 4) + 32 + 64 = 36 + 96 = 132.", unit=None),
    part_num("Calculate the mass of ammonium sulphate from 34 g of ammonia (Mr 17).", 132 * 1.0, "n(NH₃) = 34 ÷ 17 = 2 mol; n((NH₄)₂SO₄) = 1 mol; mass = 132 g.", unit="g"),
    part_num("Calculate the percentage of nitrogen (to 1 d.p.).", round(28 / 132 * 100, 1), "28 ÷ 132 × 100 = 21.2%.", unit="%", tolerance=0.1),
    part_open("Name the type of reaction between ammonia and sulphuric acid.", "Neutralisation (acid-base reaction).", "1 mark.")], tier="examen")
L.sc("Nitrogen makes up what fraction of air?", "about 78%", ["about 21%", "about 1%", "about 50%"], "Standard composition.")
L.sc("A fertiliser supplies", "nutrients for plants", ["water only", "oxygen", "pesticides"], "N, P and K.")
L.sc("Ammonia is a", "weak alkali gas", ["strong acid", "metal", "neutral gas"], "It dissolves to give an alkaline solution.")
L.sc("Eutrophication is caused by", "excess nutrients in water", ["lack of nutrients", "lack of light", "high pH of air"], "Algae overgrowth.")
L.sc("Ammonia is dried using", "quicklime (calcium oxide)", ["concentrated sulphuric acid", "calcium chloride", "water"], "The others react with ammonia.")

L = ch.lesson("sulphur-acid", "Sulphur, sulphur dioxide and sulphuric acid", minutes=30, prerequisites=["gceol-chemistry-equilibrium-basics", "gceol-chemistry-acids-bases"],
    objectives=["Describe sulphur and its burning to form sulphur dioxide.", "State properties, uses and hazards of sulphur dioxide.", "Describe the contact process with conditions.", "State properties and uses of dilute and concentrated sulphuric acid."],
    notes=["Contact process conditions quoted: about 450 °C, vanadium(V) oxide catalyst and a pressure of about 1–2 atmospheres (typical textbook values).", "Sulphur dioxide as a food preservative and bleach is mentioned generally; safe limits are not given."])
L.key("definition", "Sulphur and sulphur dioxide", "**Sulphur** is a yellow brittle non-metal; it burns in air with a blue flame to give **sulphur dioxide**: S + O₂ → SO₂. SO₂ is a colourless, choking, **acidic** gas that turns damp blue litmus red and **bleaches** coloured materials; it is a **reducing agent** (decolourises acidified potassium manganate(VII)). **Uses**: bleach for paper, food preservative, making sulphuric acid. **Harm**: causes acid rain and breathing problems.")
L.key("formule", "The contact process", "1. Burn sulphur (or roast sulphide ores): S + O₂ → SO₂.\n2. Convert to sulphur trioxide: **2SO₂ + O₂ ⇌ 2SO₃** (exothermic) with **vanadium(V) oxide** catalyst at about **450 °C** and about 1–2 atmospheres.\n3. Dissolve SO₃ in concentrated H₂SO₄ to make **oleum** (H₂S₂O₇), then add water to make H₂SO₄. (SO₃ is not added directly to water because the reaction is violent and makes a mist.)")
L.key("retenir", "Sulphuric acid", "**Dilute** H₂SO₄: a strong acid (reacts with metals, bases, carbonates to give sulphates). **Concentrated** H₂SO₄: a **dehydrating agent** (turns blue copper(II) sulphate crystals white and sugar into black carbon), and generates a lot of heat with water: **always add acid to water**, never water to acid. **Uses**: fertilisers (ammonium sulphate), detergents, paints, **lead-acid car batteries**, cleaning metals.")
L.key("pieges", "Safety and watch out", "- Concentrated sulphuric acid causes severe burns; wear eye protection.\n- Add the **acid to water slowly**, stirring.\n- The catalyst does not change the equilibrium position; the compromise temperature gives a good rate and yield.")
L.illustration(shapes([RECT(10, 80, 70, 40, radius=6), T(45, 105, "sulphur", 12), RECT(110, 80, 70, 40, radius=6), T(145, 105, "SO₂", 14), RECT(210, 80, 70, 40, radius=6), T(245, 105, "SO₃", 14), RECT(310, 80, 80, 40, radius=6), T(350, 105, "H₂SO₄", 14),
                       LINE(80, 100, 110, 100, arrow="end"), LINE(180, 100, 210, 100, arrow="end"), LINE(280, 100, 310, 100, arrow="end"), T(95, 70, "burn", 11), T(195, 140, "450 °C, V₂O₅", 11), T(295, 70, "oleum", 11)], 400, 190),
    "The contact process: sulphur is burnt to SO₂, converted to SO₃ over a catalyst and made into sulphuric acid.", "Four boxes joined by arrows: sulphur, SO2, SO3 and sulphuric acid with conditions labelled.")
ms = 1000 / 32 * 98; close(ms, 3062.5)
L.example("Example 1 — acid from sulphur", "How much sulphuric acid (kg) could be made from 1000 kg (1 tonne) of sulphur if all the sulphur is converted? (S 32, H 1, O 16)", ["S → SO₂ → SO₃ → H₂SO₄: 1 mol S gives 1 mol H₂SO₄. n(S) = 1 000 000 g ÷ 32 = 31 250 mol.", "Mass H₂SO₄ = 31 250 × 98 = 3 062 500 g = 3062.5 kg."], "3062.5 kg (about 3.06 tonnes)")
pS = 32 / Mr(H=2, S=1, O=4) * 100; close(pS, 32.65, 0.01)
L.example("Example 2 — % sulphur", "Calculate the percentage of sulphur in sulphuric acid, H₂SO₄.", ["Mr = 2 × 1 + 32 + 4 × 16 = 98.", "% S = 32 ÷ 98 × 100 = 32.7%."], "32.7%")
L.mcq("What is the catalyst in the contact process?", "vanadium(V) oxide", ["iron", "platinum", "nickel"], "V₂O₅ is used at about 450 °C.")
L.mcq("Concentrated sulphuric acid turns blue copper(II) sulphate crystals white because it is", "a dehydrating agent", ["an oxidising agent only", "a reducing agent", "an indicator"], "It removes the water of crystallisation.")
L.tf("When diluting concentrated sulphuric acid you should pour water into the acid.", False, "Always add the acid to the water, slowly, to avoid violent spitting.")
L.num("What mass of sulphur dioxide (Mr 64) is formed by burning 16 g of sulphur (S 32)?", 32, "n(S) = 0.5 mol = n(SO₂); mass = 0.5 × 64 = 32 g.", unit="g", tier="approfondissement")
L.open("Explain why the temperature used in the contact process (about 450 °C) is a compromise.", "The forward reaction is exothermic, so a low temperature gives a higher yield of SO₃ but too slow a rate; a high temperature is fast but lowers the yield. 450 °C with the catalyst gives an acceptable rate and yield.", "1 mark: exothermic/low T gives higher yield; 1 mark: but slow; 1 mark: compromise with catalyst.", tier="approfondissement", difficulty=2)
L.problem("Sulphur dioxide is bubbled through acidified potassium manganate(VII) solution and through damp blue litmus paper.", [
    part_mcq("What is seen in the manganate(VII) solution?", "It changes from purple to colourless", ["It turns green", "It turns blue", "A white precipitate forms"], "SO₂ acts as a reducing agent."),
    part_mcq("What happens to the damp blue litmus paper?", "It turns red and is then bleached", ["It turns blue", "It stays blue", "It turns purple"], "SO₂ is acidic and also bleaches."),
    part_open("State two uses of sulphur dioxide.", "Bleaching paper/wood pulp; preserving food; making sulphuric acid.", "1 mark each; any two."),
    part_open("Why is sulphur dioxide a pollutant?", "It dissolves in rain to form acid rain, damaging buildings, plants and aquatic life, and irritates the lungs.", "1 mark each; any two ideas.")], tier="examen")
L.sc("Sulphur burns in air with a", "blue flame", ["green flame", "yellow flame only", "no flame"], "Forms SO₂.")
L.sc("SO₂ in the contact process is converted to SO₃ using", "V₂O₅ catalyst", ["iron catalyst", "UV light", "electrolysis"], "Vanadium(V) oxide.")
L.sc("Concentrated sulphuric acid is a", "dehydrating agent", ["bleach", "reducing gas", "indicator"], "It removes water.")
L.sc("A use of sulphuric acid is in", "lead-acid batteries", ["making plastics only", "bread", "tyres only"], "Electrolyte in car batteries.")
L.sc("SO₂ causes", "acid rain", ["global cooling", "ozone creation", "eutrophication"], "It dissolves in rain water.")

L = ch.lesson("chlorine-halogens", "Chlorine, hydrogen chloride and the halogens", minutes=30, prerequisites=["gceol-chemistry-periodic-table", "gceol-chemistry-electrolysis-basics"],
    objectives=["Describe the preparation, properties, test and uses of chlorine.", "Describe the properties of hydrogen chloride and hydrochloric acid.", "Use silver nitrate to distinguish chloride, bromide and iodide ions.", "Describe the importance of sodium chloride."],
    notes=["Cl₂ + H₂O ⇌ HCl + HOCl is the standard equation for chlorine water; HOCl is the bleaching/disinfecting species.", "Chlorine is toxic: preparations must be a teacher demonstration in a fume cupboard."])
L.key("retenir", "Chlorine", "A pale green, poisonous gas, denser than air. **Lab preparation** (teacher demonstration, fume cupboard): MnO₂ + 4HCl(conc) → MnCl₂ + Cl₂ + 2H₂O (warm). **Test**: **bleaches damp litmus paper** (first red, then white). It reacts with metals: 2Fe + 3Cl₂ → 2FeCl₃; with hydrogen to give HCl; with water: Cl₂ + H₂O ⇌ HCl + HOCl; with cold sodium hydroxide: bleach (sodium chlorate(I)). **Uses**: **disinfecting drinking water and swimming pools**, bleach, making PVC and solvents.")
L.key("retenir", "Hydrogen chloride", "HCl is a colourless gas, **very soluble** in water (forms **hydrochloric acid**, a strong acid), and makes white fumes with ammonia. It is made by reacting sodium chloride with concentrated sulphuric acid. **Sodium chloride** (common salt) is obtained from sea water (evaporation) or rock salt; it is used in food, preserving, and as a raw material: electrolysis of brine gives chlorine, hydrogen and sodium hydroxide.")
L.key("methode", "Halide ions: silver nitrate test", "Add dilute nitric acid, then silver nitrate solution: **chloride** gives a **white** precipitate (AgCl), **bromide** a **cream** precipitate (AgBr), **iodide** a **yellow** precipitate (AgI). The precipitates darken in light. Ionic equation: Ag⁺(aq) + X⁻(aq) → AgX(s).")
L.key("pieges", "Watch out", "- Chlorine is poisonous: never smell directly; the gas is prepared only in a fume cupboard.\n- Distinguish chlorine (Cl₂, gas) from chloride (Cl⁻, ion) and hydrochloric acid (HCl solution).\n- Dry chlorine does not bleach dry litmus paper; the paper must be damp.")
L.illustration(shapes([RECT(60, 60, 40, 110), RECT(61, 120, 38, 49, fill="white"), T(80, 50, "Cl⁻", 14), RECT(160, 60, 40, 110), RECT(161, 120, 38, 49, fill="yellow", stroke="ink"), T(180, 50, "Br⁻", 14), RECT(260, 60, 40, 110), RECT(261, 120, 38, 49, fill="orange"), T(280, 50, "I⁻", 14),
                       T(80, 195, "white", 12), T(180, 195, "cream", 12), T(280, 195, "yellow", 12), T(200, 215, "precipitates with silver nitrate", 12)], 400, 235),
    "Silver halide precipitates: silver chloride is white, silver bromide cream and silver iodide yellow.", "Three test tubes labelled chloride, bromide and iodide with white, cream and yellow precipitates.")
nFe_ = 5.6 / 56; nCl2 = nFe_ * 3 / 2; vCl2 = nCl2 * MOLAR_VOL; close(nCl2, 0.15); close(vCl2, 3.6)
L.example("Example 1 — iron in chlorine", "Hot iron wool burns in chlorine: 2Fe + 3Cl₂ → 2FeCl₃. What volume of chlorine (24 dm³/mol) reacts with 5.6 g of iron (Fe 56)?", ["n(Fe) = 5.6 ÷ 56 = 0.10 mol. Ratio Fe : Cl₂ = 2 : 3, so n(Cl₂) = 0.10 × 3/2 = 0.15 mol.", "Volume = 0.15 × 24 = 3.6 dm³."], "3.6 dm³")
L.example("Example 2 — identifying a halide", "A solution gives a yellow precipitate with acidified silver nitrate. Which ion is present and what is the ionic equation?", ["Yellow precipitate: iodide ions I⁻ (silver iodide).", "Ag⁺(aq) + I⁻(aq) → AgI(s)."], "Iodide: Ag⁺(aq) + I⁻(aq) → AgI(s)")
L.mcq("What is the test for chlorine?", "It bleaches damp litmus paper", ["It relights a glowing splint", "It turns limewater milky", "It gives a squeaky pop"], "Chlorine is a bleach in the presence of water.")
L.mcq("Which ion gives a cream precipitate with silver nitrate?", "bromide", ["chloride", "iodide", "sulphate"], "AgBr is cream.")
L.tf("Chlorine is used to kill bacteria in drinking water.", True, "A small amount of chlorine acts as a disinfectant.")
L.num("What volume of hydrogen chloride (dm³ at 24 dm³/mol) is made from 1 mol of hydrogen reacting with chlorine (H₂ + Cl₂ → 2HCl)?", 48, "n(HCl) = 2 mol; V = 2 × 24 = 48 dm³.", unit="dm³", tier="approfondissement")
L.open("Chlorine is bubbled into potassium bromide solution. State the observation and the reason.", "The solution turns orange-brown because chlorine is more reactive than bromine and displaces it: Cl₂ + 2KBr → 2KCl + Br₂.", "1 mark: orange/brown colour; 1 mark: chlorine more reactive/displacement; 1 mark: equation.", tier="approfondissement")
L.problem("Sodium chloride solution (brine) is electrolysed in industry.", [
    part_mcq("What gas forms at the anode?", "chlorine", ["hydrogen", "oxygen", "nitrogen"], "2Cl⁻ → Cl₂ + 2e⁻."),
    part_mcq("What gas forms at the cathode?", "hydrogen", ["chlorine", "oxygen", "sodium"], "H⁺ is discharged rather than Na⁺."),
    part_mcq("What is left in the solution?", "sodium hydroxide", ["sodium metal", "hydrochloric acid", "nothing"], "Na⁺ and OH⁻ remain."),
    part_open("State one use of each product.", "Chlorine: water treatment/bleach; hydrogen: making ammonia/margarine; sodium hydroxide: soap making.", "1 mark each.")], tier="examen")
L.sc("Chlorine gas is", "pale green", ["colourless", "red-brown", "blue"], "Cl₂ is pale green.")
L.sc("Hydrogen chloride dissolves in water to form", "hydrochloric acid", ["sulphuric acid", "bleach", "ammonia"], "A strong acid.")
L.sc("Silver iodide is", "yellow", ["white", "cream", "blue"], "Yellow precipitate.")
L.sc("An important use of chlorine is", "water treatment", ["fertilisers only", "fuel", "cooking oil"], "Disinfection.")
L.sc("A bleach test for chlorine needs the litmus paper to be", "damp", ["dry", "hot", "red only"], "Water is needed to form the bleaching agent.")

# =====================================================================  15. CARBON COMPOUNDS
ch = p.chapter("organic", "Carbon compounds", programRef="GCE O Level Chemistry — Organic chemistry: alkanes, alkenes, alcohols, carboxylic acids, esters, polymers, fats and soap, petroleum (to be checked against the official syllabus)")
L = ch.lesson("alkanes-petroleum", "Alkanes and petroleum", minutes=35, prerequisites=["gceol-chemistry-ionic-covalent", "gceol-chemistry-moles-reactions"],
    objectives=["Define hydrocarbon, homologous series and general formula.", "Name and write formulae of the first alkanes.", "Describe combustion and substitution reactions of alkanes.", "Describe fractional distillation of crude oil, the uses of fractions and cracking."],
    notes=["Cameroon's refinery SONARA is at Limbe; the statement that it refines crude oil into fuels such as petrol, kerosene, diesel and gas should be checked by a teacher for accuracy and currency.", "Fraction names and uses are standard textbook ones; temperatures are not given."])
L.key("definition", "Hydrocarbons and homologous series", "A **hydrocarbon** contains only carbon and hydrogen. A **homologous series** is a family of compounds with the **same functional group and general formula**, similar chemical properties, and a gradual change in physical properties (boiling point rises with chain length); each member differs from the next by CH₂. **Alkanes** are **saturated** (only single C–C bonds), general formula **CₙH₂ₙ₊₂**: methane CH₄, ethane C₂H₆, propane C₃H₈, butane C₄H₁₀, pentane C₅H₁₂, hexane C₆H₁₄.")
L.key("formule", "Reactions of alkanes", "- **Complete combustion**: alkane + oxygen → carbon dioxide + water, e.g. CH₄ + 2O₂ → CO₂ + 2H₂O (very exothermic); with limited oxygen: carbon monoxide or soot.\n- **Substitution** with chlorine in ultraviolet light: CH₄ + Cl₂ → CH₃Cl + HCl (a hydrogen atom is replaced by chlorine).\nAlkanes are fairly unreactive and do not decolourise bromine water.")
L.key("retenir", "Petroleum and fractional distillation", "**Crude oil** is a mixture of many hydrocarbons. In a **fractionating column** it is heated and the vapours rise; each **fraction** condenses at a different height: **refinery gas** (LPG, cooking gas), **petrol**, **naphtha**, **kerosene** (paraffin, jet fuel), **diesel**, **fuel oil**, **lubricating oil and bitumen** (road surfacing). Going **down** the column: bigger molecules, **higher** boiling point, **more viscous**, **less flammable**. SONARA is Cameroon's oil refinery, at Limbe.")
L.key("formule", "Cracking", "Large, less useful alkanes are broken into smaller, more useful molecules by heat and a catalyst (**cracking**), giving shorter alkanes and **alkenes**: C₁₀H₂₂ → C₈H₁₈ + C₂H₄. The alkene is a raw material for plastics. Fossil fuels are non-renewable and their burning releases CO₂.")
L.key("pieges", "Watch out", "- The general formula CₙH₂ₙ₊₂ works only for alkanes.\n- Alkanes are not 'unsaturated'; the presence of a double bond makes a compound unsaturated.\n- A fraction is a **mixture** of hydrocarbons with similar boiling points, not a pure substance.")
ys = [(35, "refinery gas"), (68, "petrol"), (101, "kerosene"), (134, "diesel"), (167, "fuel oil"), (203, "bitumen")]
L.illustration(shapes([RECT(130, 20, 70, 200), *[LINE(200, y_, 245, y_, arrow="end") for y_, _ in ys], *[T(310, y_ + 5, nm, 13) for y_, nm in ys], LINE(50, 150, 130, 150, arrow="end", color="red", width=3), T(70, 135, "crude oil", 12), T(160, 12, "cooler", 12), T(160, 235, "hotter", 12)], 400, 245),
    "Fractional distillation of crude oil: lighter fractions leave at the top, heavy ones at the bottom.", "A tall fractionating column with crude oil entering at the side and six fractions leaving through arrows from refinery gas at the top to bitumen at the bottom.")
L.example("Example 1 — naming an alkane", "Give the formula and name of the alkane with six carbon atoms and calculate its Mr (C 12, H 1).", ["CₙH₂ₙ₊₂ with n = 6: C₆H₁₄. The name is hexane.", "Mr = 6 × 12 + 14 × 1 = 72 + 14 = 86."], "C₆H₁₄ (hexane); Mr = 86")
nP = 4.4 / Mr(C=3, H=8); nC = 3 * nP; vC = nC * MOLAR_VOL; vO = 5 * nP * MOLAR_VOL; mC = nC * 44; close(nP, 0.1); close(vC, 7.2); close(vO, 12); close(mC, 13.2)
L.example("Example 2 — burning propane", "Cooking gas contains propane. For C₃H₈ + 5O₂ → 3CO₂ + 4H₂O, find the volume of CO₂ (24 dm³/mol) and the mass of CO₂ formed from 4.4 g of propane (C 12, H 1, O 16).", ["Mr(C₃H₈) = 44; n = 4.4 ÷ 44 = 0.10 mol. Ratio C₃H₈ : CO₂ = 1 : 3, so n(CO₂) = 0.30 mol.", "Volume = 0.30 × 24 = 7.2 dm³; mass = 0.30 × 44 = 13.2 g."], "7.2 dm³ and 13.2 g of CO₂")
L.mcq("What is the general formula of the alkanes?", "CₙH₂ₙ₊₂", ["CₙH₂ₙ", "CₙHₙ", "CₙH₂ₙ₋₂"], "Methane CH₄ (n=1), ethane C₂H₆ (n=2).")
L.mcq("Which fraction of crude oil has the highest boiling point?", "bitumen", ["petrol", "kerosene", "refinery gas"], "The largest molecules.")
L.tf("Alkanes decolourise bromine water at once.", False, "Alkanes are saturated; only unsaturated compounds (alkenes) decolourise bromine water.")
L.num("How many hydrogen atoms are in a molecule of the alkane with 8 carbon atoms (octane)?", 18, "CₙH₂ₙ₊₂: 2 × 8 + 2 = 18.", unit=None, tier="approfondissement")
L.open("In cracking, C₁₂H₂₆ breaks down into an alkane with 8 carbon atoms and an alkene. Write the equation and name the alkene.", "C₁₂H₂₆ → C₈H₁₈ + C₄H₈; the alkene is butene (C₄H₈).", "1 mark: C₈H₁₈; 1 mark: C₄H₈; 1 mark: balanced/name.", tier="approfondissement", difficulty=2)
L.problem("A town in Cameroon uses cooking gas (mainly butane, C₄H₁₀) in homes.", [
    part_open("Write the balanced equation for the complete combustion of butane.", "2C₄H₁₀ + 13O₂ → 8CO₂ + 10H₂O", "1 mark products; 1 mark balanced."),
    part_num("How many moles of O₂ are needed to burn 2 mol of butane?", 13, "From the equation 2 : 13.", unit="mol"),
    part_mcq("What is likely to form if butane burns in a poorly ventilated room?", "carbon monoxide", ["hydrogen", "nitrogen", "sulphur"], "Incomplete combustion gives poisonous CO."),
    part_open("Explain why cooking gas should not be used in a closed room.", "It can burn incompletely, producing poisonous carbon monoxide; also a leak can form an explosive mixture with air.", "1 mark each; any two ideas.")], tier="examen")
L.sc("A hydrocarbon contains only", "carbon and hydrogen", ["carbon and oxygen", "hydrogen and oxygen", "metals"], "Definition.")
L.sc("The formula of propane is", "C₃H₈", ["C₃H₆", "C₂H₆", "C₄H₁₀"], "n = 3: 2n + 2 = 8.")
L.sc("Crude oil is separated by", "fractional distillation", ["filtration", "electrolysis", "chromatography"], "Different boiling points.")
L.sc("Cracking produces", "smaller alkanes and alkenes", ["larger alkanes", "metals", "salts"], "Breaks long chains.")
L.sc("Alkanes are described as", "saturated", ["unsaturated", "metallic", "ionic"], "Only single bonds.")

L = ch.lesson("alkenes", "Alkenes", minutes=30, prerequisites=["gceol-chemistry-alkanes-petroleum"],
    objectives=["State the general formula and structure of alkenes.", "Write formulae for ethene and propene.", "Describe addition reactions with hydrogen, bromine and steam.", "Use the bromine water test to distinguish alkanes from alkenes."],
    notes=["Conditions: hydrogenation with nickel catalyst at about 150 °C; hydration with steam and phosphoric acid catalyst at high temperature and pressure (typical school values).", "Isomerism of alkenes is not covered."])
L.key("definition", "Alkenes", "**Alkenes** are hydrocarbons with a **carbon-carbon double bond** (C=C); they are **unsaturated**. General formula **CₙH₂ₙ**: ethene C₂H₄, propene C₃H₆, butene C₄H₈. The double bond makes them much more reactive than alkanes. They are made by **cracking** of larger alkanes.")
L.key("formule", "Addition reactions", "The double bond opens and atoms add on:\n\n- with **hydrogen** (nickel catalyst, about 150 °C): C₂H₄ + H₂ → C₂H₆ (an alkane)\n- with **bromine**: C₂H₄ + Br₂ → C₂H₄Br₂ (1,2-dibromoethane)\n- with **steam** (phosphoric acid catalyst, high T and P): C₂H₄ + H₂O → C₂H₅OH (ethanol)\n\nEthene also undergoes addition **polymerisation** to make poly(ethene).")
L.key("methode", "Test for unsaturation", "Shake the hydrocarbon with **bromine water** (orange-brown). An **alkene** decolourises it quickly (the bromine adds across the double bond); an **alkane** does not (it stays orange). Alkenes also decolourise acidified potassium manganate(VII) (purple → colourless). Alkenes burn with a more smoky flame than alkanes because they contain a higher percentage of carbon.")
L.key("pieges", "Watch out", "- Addition reactions give **one** product; substitution gives two.\n- The test is **decolourises** bromine water, not 'turns it colourless to brown'.\n- Every alkene has the same percentage of carbon by mass (about 85.7%): CₙH₂ₙ has the ratio 12n : 2n.")
L.illustration(shapes([T(60, 80, "C₂H₄", 18), T(60, 110, "ethene", 12), T(200, 80, "+ Br₂", 16), LINE(125, 80, 160, 80, width=0), LINE(250, 80, 290, 80, arrow="end", width=3), T(335, 80, "C₂H₄Br₂", 16), T(335, 110, "dibromoethane", 12),
                       T(200, 160, "orange bromine water becomes colourless", 12)], 400, 190),
    "Addition of bromine to ethene: the orange colour of bromine water disappears.", "The equation of ethene plus bromine giving 1,2-dibromoethane with a note that bromine water becomes colourless.")
nE = 14 / 28; vH = nE * MOLAR_VOL; close(vH, 12)
L.example("Example 1 — hydrogenation", "Calculate the volume of hydrogen (24 dm³/mol) needed to convert 14 g of ethene into ethane: C₂H₄ + H₂ → C₂H₆ (C 12, H 1).", ["Mr(C₂H₄) = 28; n = 14 ÷ 28 = 0.50 mol. Ratio C₂H₄ : H₂ = 1 : 1, so n(H₂) = 0.50 mol.", "Volume = 0.50 × 24 = 12 dm³."], "12 dm³")
nPr = 4.2 / Mr(C=3, H=6); mBr = nPr * 160; close(nPr, 0.1); close(mBr, 16)
L.example("Example 2 — bromine addition", "What mass of bromine (Mr 160) adds to 4.2 g of propene, C₃H₆? (C₃H₆ + Br₂ → C₃H₆Br₂)", ["Mr(C₃H₆) = 42; n = 4.2 ÷ 42 = 0.10 mol. Ratio 1 : 1, so n(Br₂) = 0.10 mol.", "Mass = 0.10 × 160 = 16 g."], "16 g of bromine")
L.mcq("What is the general formula of alkenes?", "CₙH₂ₙ", ["CₙH₂ₙ₊₂", "CₙHₙ", "CₙH₂ₙ₋₂"], "Ethene C₂H₄ fits n = 2.")
L.mcq("A colourless gas decolourises bromine water. It could be", "ethene", ["methane", "ethane", "carbon dioxide"], "Only the unsaturated compound reacts.")
L.tf("Ethene can be converted into ethanol by adding steam.", True, "C₂H₄ + H₂O → C₂H₅OH.")
L.num("How many hydrogen atoms are in a molecule of the alkene with 5 carbon atoms?", 10, "CₙH₂ₙ: 2 × 5 = 10, so C₅H₁₀ (pentene).", unit=None, tier="approfondissement")
L.open("Explain why alkenes are more reactive than alkanes.", "Alkenes contain a C=C double bond, one bond of which is weaker and can open, allowing other atoms to add on (addition reactions); alkanes have only strong single bonds.", "1 mark: C=C double bond; 1 mark: opens to allow addition/weaker bond.", tier="approfondissement")
L.problem("Two colourless liquids, X and Y, are known to be hexane (C₆H₁₄) and hexene (C₆H₁₂). Bromine water is added to each.", [
    part_mcq("In test tube X the orange colour disappears. X is", "hexene", ["hexane", "water", "ethanol"], "Alkenes decolourise bromine water."),
    part_mcq("In test tube Y the colour stays orange. What does this show?", "Y is a saturated hydrocarbon (hexane)", ["Y is unsaturated", "Y is an acid", "Y contains bromine"], "No addition reaction occurs with alkanes."),
    part_num("Calculate the Mr of hexene, C₆H₁₂ (C 12, H 1).", 84, "6 × 12 + 12 = 84.", unit=None),
    part_open("Write the equation for the reaction of hexene with hydrogen.", "C₆H₁₂ + H₂ → C₆H₁₄", "1 mark.")], tier="examen")
L.sc("Alkenes contain a", "C=C double bond", ["C–C single bond only", "O–H bond", "ionic bond"], "Unsaturated.")
L.sc("Ethene reacts with bromine to give", "dibromoethane", ["ethanol", "ethane", "hydrogen bromide only"], "An addition reaction.")
L.sc("The test for an alkene uses", "bromine water", ["limewater", "litmus", "silver nitrate"], "Decolourises.")
L.sc("The formula of propene is", "C₃H₆", ["C₃H₈", "C₂H₄", "C₄H₈"], "CₙH₂ₙ with n = 3.")
L.sc("Ethene is manufactured mainly by", "cracking", ["electrolysis", "distillation of water", "fermentation of urea"], "Large alkanes are cracked.")

L = ch.lesson("alcohols-acids", "Alcohols, carboxylic acids and esters", minutes=35, prerequisites=["gceol-chemistry-alkenes", "gceol-chemistry-acids-bases"],
    objectives=["Describe the manufacture of ethanol by fermentation and from ethene.", "State the properties and uses of ethanol.", "Describe the oxidation of ethanol to ethanoic acid and the properties of ethanoic acid.", "Describe ester formation."],
    notes=["Palm wine ferments through natural yeasts and bacteria in the sap; this is described qualitatively only.", "Alcohol content and health effects of alcoholic drinks are not detailed: a health worker should review any wording added.", "Conditions for fermentation: yeast, about 25–35 °C, absence of air."])
L.key("definition", "Ethanol", "**Ethanol** C₂H₅OH is an **alcohol** (functional group –OH), a colourless liquid, soluble in water, flammable, with a lower boiling point than water (78 °C). **Fermentation**: sugars (glucose) are changed by the enzymes in **yeast** into ethanol and carbon dioxide, without air, at about 25–35 °C: **C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂**. The product is a dilute solution, concentrated by fractional distillation. Ethanol is also made from ethene and steam. Palm wine ferments quickly after tapping because natural yeasts in the sap act on its sugar.")
L.key("retenir", "Uses and combustion of ethanol", "Uses: **solvent** (perfumes, paints), **fuel** (alone or mixed with petrol), alcoholic drinks (harmful in excess and dangerous with driving), antiseptic. Burning: C₂H₅OH + 3O₂ → 2CO₂ + 3H₂O. **Oxidation**: ethanol is oxidised to **ethanoic acid** by acidified potassium dichromate(VI) (orange → green) or by bacteria in air (wine turns to **vinegar**): C₂H₅OH + 2[O] → CH₃COOH + H₂O.")
L.key("retenir", "Ethanoic acid and esters", "**Ethanoic acid** CH₃COOH (carboxylic acid, group –COOH) is a **weak acid**: it reacts with metals (hydrogen), carbonates (carbon dioxide) and alkalis (salt + water). **Vinegar** is a dilute solution. **Esters** (sweet, fruity smell, used in perfumes and flavourings) are made by heating an alcohol with a carboxylic acid with concentrated sulphuric acid as catalyst: ethanol + ethanoic acid ⇌ **ethyl ethanoate** + water.")
L.key("pieges", "Watch out", "- Fermentation needs **no air**; with air ethanol is oxidised to ethanoic acid (vinegar).\n- Too high a temperature kills the yeast; too low is too slow.\n- Ethanoic acid is a **weak** acid, not strong, even though it is called an acid.")
L.illustration(shapes([POLY([[100, 150], [160, 150], [175, 85], [85, 85]], fill=None), RECT(120, 55, 20, 30), LINE(130, 55, 130, 35, width=2), LINE(130, 35, 250, 35, width=2), LINE(250, 35, 250, 150, width=2),
                       RECT(215, 150, 70, 65), RECT(216, 175, 68, 39, fill="white", stroke="ink"), T(130, 125, "yeast +", 12), T(130, 140, "glucose", 12), T(335, 175, "limewater", 12), T(335, 195, "turns milky", 12)], 400, 235),
    "Fermentation: a mixture of glucose solution and yeast in a flask; the gas is carbon dioxide, which turns limewater milky.", "A flask with a delivery tube bubbling into limewater in a test tube.")
mg = 180.0; ngl = mg / Mr(C=6, H=12, O=6); mEt = ngl * 2 * Mr(C=2, H=6, O=1); vCO = ngl * 2 * MOLAR_VOL; close(ngl, 1.0); close(mEt, 92.0); close(vCO, 48)
L.example("Example 1 — fermentation", "Calculate the maximum mass of ethanol and the volume of CO₂ (24 dm³/mol) from 180 g of glucose (C 12, H 1, O 16).", ["Mr(glucose) = 180; n = 180 ÷ 180 = 1 mol. Ratio glucose : ethanol : CO₂ = 1 : 2 : 2.", "n(ethanol) = 2 mol; Mr = 46; mass = 92 g. n(CO₂) = 2 mol; volume = 48 dm³."], "92 g of ethanol and 48 dm³ of CO₂")
L.example("Example 2 — identifying compounds", "Name the functional group in (a) CH₃OH, (b) CH₃COOH, (c) CH₃COOC₂H₅.", ["(a) –OH: an alcohol (methanol). (b) –COOH: a carboxylic acid (ethanoic acid).", "(c) –COO– between two carbon chains: an ester (ethyl ethanoate)."], "(a) alcohol; (b) carboxylic acid; (c) ester")
L.mcq("Fermentation of glucose by yeast gives", "ethanol and carbon dioxide", ["ethanoic acid and water", "ethene and hydrogen", "methane and oxygen"], "C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂.")
L.mcq("Which is the main component of vinegar?", "ethanoic acid", ["ethanol", "methanol", "ethene"], "Oxidation of ethanol gives ethanoic acid.")
L.tf("Fermentation by yeast needs a plentiful supply of air.", False, "It is anaerobic; air would oxidise the ethanol to ethanoic acid.")
mE80 = 80 / 92 * 100
L.num("From 180 g of glucose, 80 g of ethanol is actually obtained. Find the percentage yield (theoretical mass = 92 g), to the nearest whole number.", round(mE80), "80 ÷ 92 × 100 = 87%.", unit="%", tolerance=1, tier="approfondissement")
L.open("Describe a chemical test to show that a clear liquid is ethanoic acid and not ethanol.", "Add sodium carbonate (or hydrogencarbonate): ethanoic acid fizzes and gives CO₂, which turns limewater milky; ethanol does not. Also universal indicator turns orange/red with the acid but stays green with ethanol.", "1 mark: carbonate test with fizzing; 1 mark: CO₂/limewater or indicator result.", tier="approfondissement")
L.problem("Palm sap is collected in a calabash, left in the warm air and ferments to a sweet-sour drink; left longer it turns sour like vinegar.", [
    part_mcq("What causes the initial fermentation?", "Yeasts acting on the sugar in the sap", ["Sunlight alone", "Dissolved nitrogen", "Plastic from the container"], "Yeasts produce ethanol and CO₂ from the sugar."),
    part_mcq("What is formed when the ethanol is oxidised by air and bacteria?", "ethanoic acid", ["methane", "ethene", "glucose"], "C₂H₅OH + 2[O] → CH₃COOH + H₂O."),
    part_open("Write the equation for the fermentation of glucose.", "C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂", "1 mark formulae; 1 mark balanced."),
    part_open("Give two uses of ethanol (other than in drinks).", "Solvent; fuel; antiseptic; making perfumes.", "1 mark each.")], tier="examen")
L.sc("The functional group of alcohols is", "–OH", ["–COOH", "C=C", "–Cl"], "Hydroxyl group.")
L.sc("Ethanol burns to give", "carbon dioxide and water", ["carbon only", "hydrogen only", "ethene"], "Complete combustion.")
L.sc("Esters are known for their", "sweet fruity smells", ["foul smells", "no smell", "metallic taste"], "Used in perfumes and flavours.")
L.sc("Ethanoic acid is a", "weak acid", ["strong acid", "base", "neutral compound"], "Partly ionised.")
L.sc("Yeast contains", "enzymes", ["acids", "metals", "gases"], "Which catalyse fermentation.")

L = ch.lesson("polymers-plastics", "Polymers, plastics and the environment", minutes=30, prerequisites=["gceol-chemistry-alkenes"],
    objectives=["Define monomer, polymer and addition polymerisation.", "Give examples of addition polymers and their uses.", "Describe condensation polymers (nylon, polyesters) and natural polymers in outline.", "Discuss disposal problems and ways of reducing plastic waste."],
    notes=["Condensation polymers and natural polymers (starch, proteins) are covered in outline only; check the depth required.", "Details of any national regulations on plastic bags are not given."])
L.key("definition", "Addition polymers", "A **polymer** is a very large molecule made by joining many small molecules (**monomers**). In **addition polymerisation** unsaturated monomers (alkenes) join with no other product: **n C₂H₄ → (–CH₂–CH₂–)ₙ** (poly(ethene), also called polythene). Poly(propene) from propene; **PVC** (poly(chloroethene)) from chloroethene; PTFE (non-stick coating) from tetrafluoroethene. Because nothing is lost, the **mass of polymer equals the mass of monomer**.")
L.key("retenir", "Uses", "- **Poly(ethene)**: bags, bottles, bowls and packaging.\n- **Poly(propene)**: ropes, crates, containers.\n- **PVC**: water pipes, insulation of electric cables, window frames.\n- **PTFE**: non-stick pans.\n- **Nylon** and **polyesters** (condensation polymers) are made when two different monomers join and **small molecules such as water are lost**; used for fibres and clothing, ropes and fishing nets.")
L.key("retenir", "Natural polymers", "**Starch** (from glucose, in cassava, maize and yam), **proteins** (from amino acids) and **cellulose** are natural polymers. Fats and oils are esters, not polymers.")
L.key("pieges", "Plastic waste and watch out", "Most plastics are **non-biodegradable**: they stay in the environment for years, block drains and gutters (making floods worse), harm animals and, when burned in the open, can release poisonous gases (for example hydrogen chloride from PVC). Solutions: **reduce, reuse, recycle**, use cloth or paper bags, and dispose of waste properly. Do not confuse monomer with polymer or addition with condensation polymerisation.")
chain = []
for i in range(7):
    chain.append(CIRCLE(210 + i * 24, 100, 8, fill="blue"))
    if i: chain.append(LINE(210 + (i - 1) * 24 + 8, 100, 210 + i * 24 - 8, 100, width=2))
L.illustration(shapes([CIRCLE(40, 100, 8, fill="blue"), CIRCLE(70, 100, 8, fill="blue"), CIRCLE(100, 100, 8, fill="blue"), T(70, 75, "monomers", 12), LINE(125, 100, 185, 100, arrow="end", width=3), T(155, 85, "join", 12), *chain, T(290, 75, "polymer chain", 12)], 400, 160),
    "Many small monomer molecules join to form a long polymer chain.", "Three separate blue circles on the left, an arrow labelled join, and a long chain of linked circles on the right.")
n_ = 28000 / 28; assert n_ == 1000
L.example("Example 1 — length of a chain", "A poly(ethene) molecule has relative molecular mass 28 000. How many ethene units (Mr 28) does it contain? What mass of polymer forms from 56 g of ethene?", ["Number of units = 28 000 ÷ 28 = 1000.", "Addition polymerisation has no other product, so 56 g of ethene gives 56 g of poly(ethene)."], "1000 units; 56 g")
L.example("Example 2 — choosing a material", "Why is PVC used for water pipes and the insulation on electric cables?", ["PVC is strong, does not corrode or rot, and is cheap and easy to shape.", "It is an electrical insulator, so it protects against shock, and it does not react with water."], "Strong, non-corroding electrical insulator")
L.mcq("The monomer of poly(ethene) is", "ethene", ["ethane", "ethanol", "ethanoic acid"], "n C₂H₄ → (–CH₂–CH₂–)ₙ.")
L.mcq("A disadvantage of most plastics is that they", "do not decay easily", ["conduct electricity", "are heavy", "dissolve in water"], "They are non-biodegradable.")
L.tf("Nylon is made by addition polymerisation.", False, "Nylon is a condensation polymer: small molecules (water) are lost.")
L.num("A polymer has Mr 140 000 and its repeating unit has Mr 28. Find the number of units.", 5000, "140 000 ÷ 28 = 5000.", unit=None, tier="approfondissement")
L.open("Suggest three ways in which a town in Cameroon could reduce the problems caused by plastic waste.", "Reduce use of single-use plastic bags; reuse containers; collect and recycle plastic bottles; clean drains and bins regularly; educate people not to burn or throw plastic in gutters.", "1 mark each for three valid ideas.", tier="approfondissement")
L.problem("Chloroethene (CH₂=CHCl, Mr 62.5) polymerises to PVC.", [
    part_mcq("What type of polymerisation is this?", "addition polymerisation", ["condensation polymerisation", "fermentation", "cracking"], "The double bond opens; no molecule is lost."),
    part_num("What mass of PVC forms from 125 g of chloroethene?", 125, "No loss in addition polymerisation: 125 g.", unit="g"),
    part_num("How many moles of chloroethene are in 125 g?", 2, "125 ÷ 62.5 = 2 mol.", unit="mol"),
    part_open("Why should PVC not be burned in the open?", "It gives off poisonous gases such as hydrogen chloride (and dioxins), and fumes harm health and the environment.", "1 mark: toxic gases; 1 mark: harm to health/environment.")], tier="examen")
L.sc("A polymer is made of many", "monomers joined together", ["metal ions", "acids only", "water molecules"], "Repeating units.")
L.sc("PVC is used for", "water pipes", ["fuel", "food", "fertiliser"], "Strong and does not corrode.")
L.sc("Addition polymers are made from", "alkenes", ["alkanes", "metals", "alcohols only"], "Unsaturated monomers.")
L.sc("Starch is a", "natural polymer", ["synthetic fibre", "simple sugar", "metal"], "Made of many glucose units.")
L.sc("To reduce plastic waste we should", "reduce, reuse and recycle", ["burn it in the open", "bury it in drains", "throw it in rivers"], "Good waste management.")

L = ch.lesson("soap-fats", "Fats, oils and soap", minutes=30, prerequisites=["gceol-chemistry-alcohols-acids", "gceol-chemistry-moles-reactions"],
    objectives=["Describe fats and oils as esters of propane-1,2,3-triol (glycerol) and fatty acids.", "Describe saponification and write its equation in words and symbols.", "Explain how soap cleans and why soap forms scum in hard water.", "Calculate masses in soap making."],
    notes=["Tristearin (C₅₇H₁₁₀O₆, Mr 890) is used as a model fat; real palm oil and palm kernel oil are mixtures of different triglycerides.", "Caustic soda is dangerous (burns); local soap making should only be done with training and protective equipment.", "Saturated/unsaturated terms are used qualitatively; no percentages for palm oil are quoted."])
L.key("definition", "Fats and oils", "**Fats** (solid) and **oils** (liquid at room temperature) are **esters** of **glycerol** (propane-1,2,3-triol, three –OH groups) and three **fatty acids**. Animal fats and some tropical fats are more **saturated** (no C=C in the fatty acid chains); vegetable oils are often more **unsaturated** (they decolourise bromine water). **Hardening**: oils + hydrogen (nickel catalyst) → solid fats (margarine). Fats are an energy store for the body.")
L.key("formule", "Making soap: saponification", "Boiling a fat or oil with **sodium hydroxide** (caustic soda) gives **soap** and **glycerol**: **fat/oil + 3NaOH → glycerol + 3 sodium salts of fatty acids (soap)**. Example: tristearin + 3NaOH → C₃H₅(OH)₃ + 3C₁₇H₃₅COONa. Salt is added to separate the soap. Palm oil, palm kernel oil or groundnut oil can be used. Potassium hydroxide gives softer soap.")
L.key("retenir", "How soap cleans", "A soap ion has a **water-loving (hydrophilic) head** (–COO⁻) and a **grease-loving (hydrophobic) tail** (hydrocarbon chain). The tails dissolve in the grease; the heads stay in the water. Agitation breaks the grease into droplets surrounded by soap ions, which are washed away. In **hard water**, soap forms **scum** (calcium or magnesium salts of fatty acids), so more soap is used; **soapless detergents** do not form scum.")
L.key("pieges", "Watch out", "- Fats and oils are **esters**, not alkanes.\n- Caustic soda (NaOH) burns skin and eyes: wear gloves and goggles.\n- Detergent waste can pollute rivers if not properly treated.")
tails = []
for k in range(12):
    a = 2 * math.pi * k / 12
    x1_, y1_ = round(200 + 38 * math.cos(a)), round(105 + 38 * math.sin(a)); x2_, y2_ = round(200 + 64 * math.cos(a)), round(105 + 64 * math.sin(a))
    tails += [LINE(x1_, y1_, x2_, y2_, width=2), CIRCLE(round(200 + 70 * math.cos(a)), round(105 + 70 * math.sin(a)), 5, fill="blue")]
L.illustration(shapes([CIRCLE(200, 105, 34, fill="orange"), *tails, T(200, 110, "grease", 12), T(200, 205, "soap ions around a drop of grease", 12), T(335, 40, "head (water)", 12), T(335, 60, "tail (grease)", 12)], 400, 225),
    "Soap ions surround a drop of grease with their tails in the grease and their heads in the water.", "An orange grease drop surrounded by short lines (tails) pointing inward and blue circles (heads) pointing outward.")
Mfat = Mr(C=57, H=110, O=6); Msoap = Mr(C=18, H=35, O=2, Na=1); Mglyc = Mr(C=3, H=8, O=3); close(Mfat, 890); close(Msoap, 306); close(Mglyc, 92)
close(Mfat + 3 * 40, 3 * Msoap + Mglyc)
nf = 89 / Mfat; mNaOH = nf * 3 * 40; msoap = nf * 3 * Msoap; close(nf, 0.1); close(mNaOH, 12.0); close(msoap, 91.8)
L.example("Example 1 — masses in saponification", "Tristearin (Mr 890) reacts with sodium hydroxide: C₅₇H₁₁₀O₆ + 3NaOH → C₃H₈O₃ + 3C₁₇H₃₅COONa (Mr 92 and 306). For 89 g of tristearin find the mass of NaOH needed and the mass of soap formed (Na 23, O 16, H 1).", ["n(tristearin) = 89 ÷ 890 = 0.10 mol. NaOH: 3 × 0.10 = 0.30 mol, mass = 0.30 × 40 = 12.0 g.", "Soap: 3 × 0.10 = 0.30 mol; mass = 0.30 × 306 = 91.8 g (check: 89 + 12 = 101 = 91.8 + 9.2 glycerol)."], "12.0 g of NaOH; 91.8 g of soap")
L.example("Example 2 — scum", "Explain why soap gives little lather and a scum in hard water, and how soapless detergent helps.", ["Calcium and magnesium ions react with soap ions to form an insoluble scum, using up soap before it lathers: Ca²⁺ + 2C₁₇H₃₅COO⁻ → (C₁₇H₃₅COO)₂Ca(s).", "Soapless detergents have calcium salts that are soluble, so they lather and clean in hard water."], "Insoluble calcium salts form scum; detergents do not")
L.mcq("Fats and oils belong to the group of compounds called", "esters", ["alkenes", "alcohols", "acids"], "Esters of glycerol and fatty acids.")
L.mcq("Which reagent is boiled with a fat to make soap?", "sodium hydroxide", ["hydrochloric acid", "sodium chloride", "ethanol"], "Alkaline hydrolysis (saponification).")
L.tf("Soap forms scum in hard water.", True, "Calcium and magnesium ions give insoluble salts with soap.")
L.num("In making soap, 0.50 mol of fat requires how many moles of NaOH (3 NaOH per fat molecule)?", 1.5, "0.50 × 3 = 1.5 mol.", unit="mol", tier="approfondissement", tolerance=0.01)
L.open("Explain how soap removes grease from clothes.", "The hydrocarbon tails dissolve in the grease while the charged heads stay in the water; agitation breaks the grease into small droplets surrounded by soap ions, which are rinsed away with the water.", "1 mark: tail in grease; 1 mark: head in water; 1 mark: droplets washed away.", tier="approfondissement")
L.problem("A group of pupils, supervised by a teacher, makes soap by boiling palm oil with sodium hydroxide solution.", [
    part_mcq("What is the name of this reaction?", "saponification", ["fermentation", "cracking", "neutralisation only"], "Alkaline hydrolysis of an ester."),
    part_mcq("Which other product is formed besides soap?", "glycerol", ["ethanol", "methane", "chlorine"], "Propane-1,2,3-triol."),
    part_open("Why must gloves and goggles be worn?", "Sodium hydroxide (caustic soda) is corrosive and burns skin and eyes.", "1 mark."),
    part_num("How many moles of NaOH are needed for 0.20 mol of palm oil (a triglyceride)?", 0.6, "3 × 0.20 = 0.60 mol.", unit="mol", tolerance=0.01)], tier="examen")
L.sc("Soap is the sodium salt of a", "fatty acid", ["mineral acid", "alcohol", "alkane"], "e.g. sodium stearate.")
L.sc("Glycerol has", "three –OH groups", ["no –OH groups", "one –COOH group", "a C=C bond"], "Propane-1,2,3-triol.")
L.sc("The tail of a soap ion is", "attracted to grease", ["attracted to water", "ionic", "a metal"], "Hydrophobic tail dissolves in grease.")
L.sc("Hardening of oils uses", "hydrogen and a nickel catalyst", ["oxygen only", "chlorine", "sulphur dioxide"], "Addition of H₂ to C=C.")
L.sc("Soapless detergents", "lather in hard water", ["form scum in hard water", "are acids", "cannot clean"], "Their calcium salts are soluble.")

# =====================================================================  16. CHEMISTRY IN CAMEROONIAN LIFE
ch = p.chapter("cameroon", "Chemistry in Cameroonian life", programRef="GCE O Level Chemistry — Applications of chemistry: industrial and everyday processes (to be checked against the official syllabus)")
L = ch.lesson("everyday-chemistry", "Chemistry in Cameroonian life: farming, fuels, building and the home", minutes=30, prerequisites=["gceol-chemistry-soap-fats", "gceol-chemistry-nitrogen-fertilisers", "gceol-chemistry-alkanes-petroleum"],
    objectives=["Link chemical ideas to cocoa and palm-oil processing, soap making and cooking fuels.", "Relate fuels (petrol, diesel, kerosene, gas, charcoal) to hydrocarbons and combustion.", "Explain the chemistry of limestone, cement, galvanised roofing and water safety.", "Carry out simple calculations from local contexts."],
    notes=["This lesson links chapters rather than adding new syllabus content; check that every process described (cocoa fermentation, SONARA, local soap making) is accurate and current for the region.", "No production statistics, prices or company data are given.", "Biogas is mentioned as a small-scale methane source from waste; digester design is not covered."])
L.key("retenir", "Farming and food", "**Cocoa and coffee**: after harvest cocoa beans are fermented for several days in heaps or boxes: microorganisms act on the sugary pulp, giving heat, ethanol and then ethanoic acid, which helps develop the flavour; the beans are then dried. **Palm oil**: oil from palm fruits is a mixture of esters (triglycerides); it is used in cooking and, with sodium hydroxide, in **soap making** (saponification). **Fertilisers** (N, P, K) replace nutrients taken by crops such as maize, cocoa and vegetables; excess causes pollution (eutrophication).")
L.key("retenir", "Fuels", "**Petrol, diesel, kerosene (paraffin) and cooking gas (LPG)** are fractions of **crude oil**, separated in a refinery such as **SONARA at Limbe**; they are mixtures of hydrocarbons which release CO₂ and heat when burned. **Charcoal and firewood** are widely used for cooking: burning in a closed room produces poisonous **carbon monoxide**. **Biogas** (mainly methane) can be produced from animal and plant waste and burned as fuel.")
L.key("retenir", "Building and the home", "**Limestone** (calcium carbonate) is heated to make quicklime: CaCO₃ → CaO + CO₂; limestone is also a raw material for **cement**. **Galvanised iron (zinc-coated)** roofing resists rust because zinc protects the iron. **Aluminium** is used for pots and window frames because it resists corrosion. **Safe water**: settle, filter, then boil or chlorinate.")
L.key("pieges", "Watch out", "- Fuels from crude oil and from fermentation (ethanol) are different families; both release CO₂ when burned.\n- Never burn charcoal or use a gas ring for heating in a closed room.\n- Handle caustic soda, kerosene and fertilisers carefully, away from children and from drinking water.")
L.illustration(shapes([RECT(10, 70, 85, 50, radius=6), T(52, 92, "palm fruit", 12), T(52, 108, "→ palm oil", 11), RECT(120, 70, 85, 50, radius=6), T(162, 92, "oil + NaOH", 12), T(162, 108, "(heated)", 11), RECT(230, 70, 70, 50, radius=6), T(265, 92, "soap +", 12), T(265, 108, "glycerol", 12), RECT(320, 70, 70, 50, radius=6), T(355, 92, "add salt:", 12), T(355, 108, "soap rises", 12),
                       LINE(95, 95, 120, 95, arrow="end"), LINE(205, 95, 230, 95, arrow="end"), LINE(300, 95, 320, 95, arrow="end")], 400, 170),
    "Steps in making soap from palm oil: heat the oil with sodium hydroxide, then separate the soap with salt.", "Four boxes joined by arrows: palm oil, oil with sodium hydroxide heated, soap and glycerol, salt added to separate the soap.")
mcao = 1000 / 100 * 56; vch4 = 1.0; vair = vch4 * 2 * 5; assert mcao == 560 and vair == 10
L.example("Example 1 — lime from limestone", "A kiln heats 1000 kg of pure calcium carbonate: CaCO₃ → CaO + CO₂ (Ca 40, C 12, O 16). Find the mass of quicklime and the mass of CO₂.", ["n(CaCO₃) = 1 000 000 g ÷ 100 = 10 000 mol (10 kmol) = n(CaO) = n(CO₂).", "Mass CaO = 10 kmol × 56 = 560 kg; mass CO₂ = 10 kmol × 44 = 440 kg (check: 560 + 440 = 1000 kg)."], "560 kg of CaO and 440 kg of CO₂")
L.example("Example 2 — air for biogas", "Biogas is mainly methane: CH₄ + 2O₂ → CO₂ + 2H₂O. If air is taken as one-fifth oxygen by volume, what volume of air is needed to burn 1 m³ of methane completely (same temperature and pressure)?", ["Gas volumes follow the mole ratio: 1 m³ of CH₄ needs 2 m³ of O₂.", "Air is one-fifth oxygen, so volume of air = 2 × 5 = 10 m³."], "10 m³ of air")
L.mcq("Why is it dangerous to use a charcoal stove in a closed room?", "Incomplete combustion produces poisonous carbon monoxide", ["Charcoal produces chlorine", "Charcoal explodes with air", "It makes the room too bright"], "CO has no smell and reduces oxygen transport in blood.")
L.mcq("Which of these is a fraction of crude oil?", "kerosene", ["glycerol", "quicklime", "bauxite"], "Kerosene (paraffin) is a fraction.")
L.tf("Soap can be made by heating palm oil with sodium hydroxide.", True, "This is saponification, giving soap and glycerol.")
L.match("Match each everyday process to the chemistry behind it.", [["soap from palm oil", "saponification"], ["cocoa bean fermentation", "action of microorganisms on sugar"], ["galvanised roof", "zinc protects iron from rusting"], ["quicklime from limestone", "thermal decomposition"]], "Each process uses ideas from earlier lessons.", tier="approfondissement")
L.num("How many kg of quicklime (CaO, Mr 56) can be made from 500 kg of calcium carbonate (Mr 100)?", 280, "500 ÷ 100 = 5 kmol; 5 × 56 = 280 kg.", unit="kg", tier="approfondissement")
L.open("A farmer in the Western Highlands applies large amounts of fertiliser and the stream below turns green. Explain what is happening and suggest how to prevent it.", "Excess fertiliser washes into the stream and causes algae to grow rapidly (eutrophication); when the algae die, bacteria use up oxygen and fish die. Use only the amount needed, avoid applying before rain, and keep buffer strips of plants beside streams.", "1 mark: runoff/algae growth; 1 mark: oxygen depletion; 1 mark: prevention.", tier="approfondissement")
L.problem("A household in a Cameroonian town cooks with a gas cylinder (butane, C₄H₁₀), with charcoal, and sometimes with kerosene.", [
    part_mcq("Which two of these fuels are obtained from crude oil?", "butane and kerosene", ["butane and charcoal", "charcoal and kerosene", "only charcoal"], "Charcoal is made from wood."),
    part_mcq("Which gas formed in poor ventilation is poisonous?", "carbon monoxide", ["carbon dioxide", "water vapour", "nitrogen"], "Incomplete combustion."),
    part_num("How many moles of CO₂ are formed when 1 mol of butane burns completely (C₄H₁₀ gives 4CO₂)?", 4, "Each carbon atom gives one CO₂: 4 mol.", unit="mol"),
    part_open("Give two safety rules for cooking at home.", "Cook in a well-ventilated place; keep gas cylinders upright and check for leaks; keep kerosene in labelled containers away from children and flames; never burn charcoal in a closed room.", "1 mark each; any two.")], tier="examen")
L.sc("SONARA is Cameroon's", "oil refinery (at Limbe)", ["cement works", "water treatment plant", "palm oil mill"], "It processes crude oil into fuels.")
L.sc("Fermented cocoa beans are then", "dried", ["frozen", "burnt", "distilled"], "Drying follows fermentation.")
L.sc("Quicklime is formed by heating", "calcium carbonate", ["calcium chloride", "sodium chloride", "sand"], "CaCO₃ → CaO + CO₂.")
L.sc("Galvanised roofing is iron coated with", "zinc", ["copper", "silver", "gold"], "Zinc protects the iron.")
L.sc("Biogas is mainly", "methane", ["oxygen", "chlorine", "nitrogen"], "From the decay of waste without air.")

# =====================================================================  MOCK PAPER
def lesson(slug):
    for c in p.chapters:
        for l in c.lessons:
            if l.slug == slug: return l
    raise KeyError(slug)

mA = [
 lesson("states-diffusion").mcq("Which statement about the particles of a gas is correct?", "They are far apart and move randomly", ["They vibrate in fixed positions", "They are touching in a regular pattern", "They cannot move"], "That is the kinetic model of a gas.", mock=True, tier="examen"),
 lesson("atomic-structure").mcq("Which electron arrangement belongs to calcium (Z = 20)?", "2,8,8,2", ["2,8,10", "2,8,2", "2,8,8"], "Fill shells 2, 8, 8 then 2.", mock=True, tier="examen"),
 lesson("ionic-covalent").mcq("Which compound contains covalent bonds only?", "carbon dioxide", ["sodium chloride", "magnesium oxide", "calcium chloride"], "CO₂ is made of two non-metals.", mock=True, tier="examen"),
 lesson("moles-masses").mcq("What is the relative formula mass of calcium carbonate, CaCO₃ (Ca 40, C 12, O 16)?", "100", ["68", "84", "56"], "40 + 12 + 48 = 100.", mock=True, tier="examen"),
 lesson("acids-bases").mcq("A solution turns universal indicator blue. Its pH is probably", "10", ["2", "7", "5"], "Blue indicates an alkaline solution (pH above 7).", mock=True, tier="examen"),
 lesson("electrolysis-basics").mcq("During electrolysis, metals are deposited at the", "cathode", ["anode", "salt bridge", "battery"], "Cations are reduced at the negative electrode.", mock=True, tier="examen"),
 lesson("reactivity-series").mcq("Which metal would displace copper from copper(II) sulphate solution?", "zinc", ["silver", "gold", "mercury"], "Zinc is above copper in the reactivity series.", mock=True, tier="examen"),
 lesson("alkenes").mcq("Which compound decolourises bromine water?", "ethene", ["ethane", "methane", "propane"], "Only the unsaturated alkene reacts.", mock=True, tier="examen"),
]
nMg = 4.8 / 24; vH2m = nMg * MOLAR_VOL; mMgCl2 = nMg * (24 + 2 * 35.5); pct = 17.1 / mMgCl2 * 100
close(nMg, 0.2); close(vH2m, 4.8); close(mMgCl2, 19.0); close(pct, 90.0)
qB = lesson("moles-reactions").problem("Magnesium reacts with excess dilute hydrochloric acid: Mg + 2HCl → MgCl₂ + H₂. A pupil uses 4.8 g of magnesium (Mg 24, Cl 35.5; 1 mol of gas = 24 dm³).", [
    part_num("Calculate the moles of magnesium.", nMg, "n = 4.8 ÷ 24 = 0.20 mol.", unit="mol", points=1, tolerance=0.001),
    part_num("Calculate the volume of hydrogen formed in dm³.", vH2m, "Ratio Mg : H₂ = 1 : 1, so n(H₂) = 0.20 mol; V = 0.20 × 24 = 4.8 dm³.", unit="dm³", points=1, tolerance=0.01),
    part_num("Calculate the mass of magnesium chloride that can be formed (Mr = 95).", mMgCl2, "n(MgCl₂) = 0.20 mol; mass = 0.20 × 95 = 19.0 g.", unit="g", points=1, tolerance=0.1),
    part_num("The pupil obtains 17.1 g of MgCl₂. Calculate the percentage yield.", pct, "17.1 ÷ 19.0 × 100 = 90%.", unit="%", points=2, tolerance=0.5),
    part_open("Give one reason why the actual yield is less than the theoretical yield.", "Some product is lost during filtering and transfer, or the reaction is not complete.", "1 mark.", points=1)], mock=True, tier="examen")
qC = lesson("electrolysis-basics").problem("Molten lead(II) bromide is electrolysed using inert electrodes.", [
    part_mcq("What is formed at the cathode?", "lead", ["bromine", "hydrogen", "oxygen"], "Pb²⁺ gains electrons: Pb²⁺ + 2e⁻ → Pb.", points=1),
    part_mcq("What is formed at the anode?", "bromine", ["lead", "hydrogen", "oxygen"], "2Br⁻ → Br₂ + 2e⁻.", points=1),
    part_open("Explain why solid lead(II) bromide does not conduct electricity.", "The ions are held in fixed positions in the lattice and cannot move to carry the charge.", "1 mark.", points=1)], mock=True, tier="examen")
qD = lesson("alkenes").problem("Hydrocarbon X has the formula C₄H₈ and decolourises bromine water.", [
    part_mcq("Which homologous series does X belong to?", "alkenes", ["alkanes", "alcohols", "carboxylic acids"], "General formula CₙH₂ₙ with a C=C bond.", points=1),
    part_open("Write the equation for the reaction of X with hydrogen.", "C₄H₈ + H₂ → C₄H₁₀", "1 mark.", points=1),
    part_open("State the name of the polymer made when many molecules of ethene join together.", "Poly(ethene) (polythene).", "1 mark.", points=1)], mock=True, tier="examen")
p.mock("1", "GCE O Level mock — Chemistry", 90,
       "Answer all questions. The paper is marked out of 20: Section A has 8 multiple-choice questions (1 mark each) and Sections B to D are structured questions with the marks shown for each part. Use these relative atomic masses where needed: H 1, C 12, O 16, Mg 24, Cl 35.5; 1 mole of gas occupies 24 dm³ at room temperature and pressure. This is an original practice paper: its format is to be checked against the official texts of the Cameroon GCE Board.",
       [("Section A — Multiple choice (8 marks)", mA), ("Section B — Calculations (6 marks)", [qB]), ("Section C — Electrolysis (3 marks)", [qC]), ("Section D — Carbon compounds (3 marks)", [qD])])
p.write()
