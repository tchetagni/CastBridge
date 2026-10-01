import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *
from fs_chem import *

p = Pack("form3-chemistry", "Chemistry — Form 3", level="Form 3", subject="chemistry", cursus="secondary",
         description="A first course in O Level Chemistry for Form 3: particles and states of matter, elements, compounds and mixtures, separation methods, atomic structure, the periodic table, bonding, formulae and equations, air, water, and acids and bases. Worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Chemistry, Form 3 (first year of the GCE O Level Chemistry course) — to be checked against the official syllabus")

# =========================================================== 1 Matter
ch = p.chapter("matter", "Matter, elements and mixtures", "Form 3 Chemistry — Particles, elements, compounds and mixtures (to be checked)")
def diffusion_fig():
    d = Draw()
    d.sh.append(RECT(50, 80, 380, 36, fill="white", width=2))
    d.sh.append(RECT(40, 84, 14, 28, fill="lightgrey", width=1.5)); d.sh.append(RECT(426, 84, 14, 28, fill="lightgrey", width=1.5))
    x_ring = 50 + 380 * (1.4651 / 2.4651)
    d.sh.append(RECT(x_ring - 6, 80, 12, 36, fill="white", stroke="ink", width=1.5)); d.line(x_ring, 82, x_ring, 114, color="grey", width=6)
    d.text(47, 70, "cotton wool + ammonia", size=11, anchor="start"); d.text(433, 70, "cotton wool + HCl", size=11, anchor="end")
    d.text(x_ring, 140, "white ring of ammonium chloride", size=11)
    d.line(x_ring, 126, x_ring, 118, arrow="end", color="grey", width=1.5)
    d.text(240, 172, "The ring forms nearer the HCl end: ammonia particles are lighter and diffuse faster.", size=11)
    return d.fig(480, 190)
assert 1.2 < math.sqrt(36.5 / 17) < 1.5
build(ch, "matter-states", "Particles, states of matter and diffusion", 25,
      ["Describe the particle model of solids, liquids and gases.", "Explain diffusion and Brownian motion as evidence for moving particles.", "Explain changes of state in terms of particles."],
      [("key", "definition", "The particle model",
        "All matter is made of tiny **particles** (atoms, molecules or ions) that are always **moving**. **Solid**: particles close together in a regular pattern, vibrating about fixed positions. **Liquid**: close together but able to slide past each other. **Gas**: far apart and moving fast in all directions, so gases fill their container and can be **compressed**. Heating makes particles move faster."),
       ("key", "retenir", "Evidence for particles",
        "**Diffusion** is the spreading of particles from a region of high concentration to one of low concentration. A purple crystal of potassium manganate(VII) slowly colours the whole of the water, and the smell of cooking spreads through a house. **Brownian motion** is the random jiggling of tiny smoke or pollen grains seen under a microscope: it is caused by unseen, fast-moving air or water particles hitting them. Gases diffuse faster than liquids; **lighter** gas particles diffuse faster; diffusion is faster when **hot**."),
       ("fig", diffusion_fig(), "Ammonia and hydrogen chloride gases diffusing along a tube.", "A glass tube with cotton wool soaked in ammonia at the left end and hydrochloric acid at the right end, and a white ring forming closer to the right end."),
       ("key", "retenir", "Changes of state",
        "**Melting**: the particles vibrate more and break out of their pattern. **Boiling**: particles escape from the whole liquid. **Condensation**: slowing gas particles join together. During a change of state the temperature stays constant while energy breaks or forms the forces between particles. **Sublimation** (iodine, ammonium chloride): solid to gas without a liquid."),
       ("key", "pieges", "Common mistakes",
        "- Particles do not get bigger when a solid melts: they stay the same size; only their arrangement and movement change.\n- There is no air between the particles: the space between gas particles is empty.\n- Diffusion does not need heating; heating only makes it faster."),
       ("example", "State at room temperature", "Mercury melts at −39 °C and boils at 357 °C. Sodium chloride melts at 801 °C. State each at 25 °C.", ["25 °C lies between −39 °C and 357 °C, so mercury is a liquid.", "25 °C is below 801 °C, so sodium chloride is a solid."], "Mercury: liquid. Sodium chloride: solid."),
       ("example", "Explain a smell", "Explain why the smell of fried plantain reaches the next room.", ["Particles of the vapour move randomly and fast in all directions.", "They diffuse through the air from the kitchen to the room."], "By diffusion of vapour particles.")],
      [("mcq", "Which state has particles that are close together but able to slide past each other?", "liquid", ["solid", "gas", "vacuum"], "Liquid."),
       ("tf", "Brownian motion is evidence that particles of matter are moving.", True, "The grains are knocked about by fast, unseen particles."),
       ("mcq", "Diffusion in a gas is faster when the gas is:", "hotter", ["colder", "more massive", "heavier"], "Particles move faster at higher temperature."),
       ("num", "Substance Y melts at −7 °C and boils at 59 °C. Up to what temperature in °C can Y exist as a liquid?", 59, "A liquid exists up to its boiling point.", 0, "°C"),
       ("open", "Explain, using the particle model, why a gas can be compressed but a liquid cannot easily be.", "Gas particles are far apart with a lot of empty space between them so they can be pushed closer; in a liquid the particles already touch and there is almost no space.", ["1 mark: gas particles far apart", "1 mark: liquid particles already close"]),
       ("prob", "A teacher places a drop of brown bromine liquid in a sealed gas jar; the brown colour slowly spreads through the whole jar.", [("mcq", "This is an example of:", "diffusion", ["filtration", "melting", "neutralisation"], "Particles spread out to fill the space."), ("mcq", "If the jar is warmed the colour spreads:", "faster", ["slower", "not at all", "backwards"], "Particles move faster."), ("open", "Explain why the bromine fills the whole jar.", "The bromine particles are in constant random motion and spread out into all of the space available, as gas particles are far apart and move freely.", ["1 mark: random motion", "1 mark: spread into all the space"], 2)])],
      [("Particles in a gas are:", "far apart and moving fast", ["fixed in a pattern", "touching in layers", "motionless"], "Gas particle model."),
       ("Diffusion is the movement of particles from:", "high to low concentration", ["low to high concentration", "cold to hot only", "solid to solid"], "They spread out."),
       ("Which change of state is the opposite of freezing?", "melting", ["boiling", "condensing", "subliming"], "Liquid to solid is freezing."),
       ("Brownian motion was seen when:", "smoke particles jiggled randomly under a microscope", ["a magnet moved a nail", "water boiled", "ice melted"], "Evidence of moving air particles."),
       ("During boiling the temperature of pure water:", "stays at 100 °C", ["rises continuously", "falls", "becomes zero"], "Energy is used to separate the particles.")],
      ["In the ammonia/hydrogen chloride demonstration the ring forms nearer the HCl end; the relative speeds depend on the relative masses (NH₃ 17, HCl 36.5). Check whether the syllabus asks for 'rates of diffusion' in Form 3 and note safety (fume cupboard)."])

# elements grid
def first20():
    names = [("H", "1"), ("He", "2"), ("Li", "3"), ("Be", "4"), ("B", "5"), ("C", "6"), ("N", "7"), ("O", "8"), ("F", "9"), ("Ne", "10"),
             ("Na", "11"), ("Mg", "12"), ("Al", "13"), ("Si", "14"), ("P", "15"), ("S", "16"), ("Cl", "17"), ("Ar", "18"), ("K", "19"), ("Ca", "20")]
    return ["%s|%s" % n for n in names]
els = grid(first20(), cols=5, w=480, bh=36, gapx=10, gapy=8, size=11, colors=["lightblue", "lightgreen", "lightorange", "lightyellow", "lightgrey"])
assert len(first20()) == 20
build(ch, "elements-mixtures", "Elements, compounds and mixtures", 25,
      ["Define element, compound and mixture and give the symbols of the first 20 elements.", "Compare the properties of metals and non-metals.", "Distinguish compounds from mixtures and name alloys."],
      [("key", "definition", "Elements, compounds, mixtures",
        "An **element** has only one kind of atom and cannot be broken down by chemical means; there are about 118 known. A **compound** has atoms of **two or more elements chemically combined** in a fixed ratio, with properties different from the elements (sodium, a reactive metal, and chlorine, a poisonous gas, form harmless table salt). A **mixture** has substances only **physically mixed** in any proportion; each keeps its own properties and they can be separated."),
       ("fig", els, "The first 20 elements: symbol and atomic number.", "A grid of 20 boxes from H 1 to Ca 20 giving each symbol and atomic number."),
       ("key", "retenir", "Metals and non-metals",
        "**Metals** are shiny, **malleable**, **ductile**, good conductors of heat and electricity, usually solid with high melting points (mercury is liquid) and sonorous. **Non-metals** are dull, brittle if solid, poor conductors (graphite is an exception) and have lower melting points. An **alloy** is a mixture of a metal with another element: **brass** (copper and zinc), **bronze** (copper and tin), **steel** (iron and a little carbon). Alloys are often harder or less likely to rust."),
       ("key", "retenir", "Naming compounds",
        "A compound of two elements ends in **-ide** (sodium chloride, magnesium oxide, iron sulphide). Compounds with oxygen as well end in **-ate** or **-ite** (copper sulphate, calcium carbonate, sodium nitrate). Formulae show the atoms: H₂O has two H and one O; CaCO₃ has one Ca, one C and three O."),
       ("key", "pieges", "Common mistakes",
        "- Air is a mixture, not a compound: the gases in it are not chemically joined.\n- Heating iron and sulphur makes a compound; mixing them only makes a mixture.\n- A mixture can have any proportions; a compound always has a fixed one."),
       ("example", "Classify", "Classify each as element, compound or mixture: oxygen, sea water, carbon dioxide, copper.", ["Oxygen and copper each contain only one kind of atom: elements.", "Carbon dioxide has C and O joined: compound. Sea water has water and salts: mixture."], "Elements: oxygen, copper. Compound: carbon dioxide. Mixture: sea water."),
       ("example", "Count atoms and elements", "How many different elements and how many atoms are in one unit of Al₂(SO₄)₃?", ["Elements: Al, S, O = 3 different elements.", "Atoms: 2 Al + 3 S + 3 × 4 O = 2 + 3 + 12 = 17."], "3 elements; 17 atoms")],
      [("mcq", "Which of these is a compound?", "carbon dioxide", ["oxygen", "air", "copper"], "CO₂ has two elements joined."),
       ("match", "Match each alloy with its main components.", [("brass", "copper and zinc"), ("bronze", "copper and tin"), ("steel", "iron and carbon"), ("solder", "tin and lead")], "Standard alloys."),
       ("num", "How many atoms are in one unit of Ca(OH)₂?", 5, "1 + 2 + 2 = 5.", 0),
       ("mcq", "Which property is typical of a metal?", "conducts electricity", ["dull and brittle", "low melting point only", "does not conduct heat"], "Metals conduct."),
       ("tf", "A mixture has a fixed composition.", False, "A compound has a fixed composition, a mixture does not."),
       ("prob", "The formula of calcium nitrate is Ca(NO₃)₂.", [("num", "How many different elements does it contain?", 3, "Ca, N, O.", 0), ("num", "How many atoms are in one unit?", 9, "1 + 2 + 6 = 9.", 0), ("mcq", "Its name ending -ate shows it contains:", "oxygen as well as other elements", ["only two elements", "no metal", "carbon only"], "The suffix -ate indicates oxygen in the ion.")])],
      [("An element contains:", "one kind of atom", ["two kinds of atom", "only molecules", "no atoms"], "By definition."),
       ("The symbol for chlorine is:", "Cl", ["C", "Ch", "Co"], "Cl."),
       ("Brass is an alloy of copper and:", "zinc", ["tin", "lead", "carbon"], "Brass is Cu + Zn."),
       ("Which is a mixture?", "air", ["water", "salt", "sulphur"], "Air has several gases."),
       ("A metal that is a liquid at room temperature is:", "mercury", ["iron", "copper", "sodium"], "Mercury melts at −39 °C.")],
      ["The number of known elements (118) is the current figure; check the syllabus list of elements (first 20 plus some others such as Fe, Cu, Zn).", "Alloy compositions simplified."])

# =========================================================== 2 Separation
ch = p.chapter("separation", "Separation and purity", "Form 3 Chemistry — Separation methods (to be checked)")
def still_fig():
    d = Draw()
    d.sh.append(CIRCLE(70, 125, 30, fill="lightblue", width=2)); d.line(70, 95, 70, 70, width=2); d.line(70, 70, 170, 62, width=2)
    d.sh.append(POLY([(60, 170), (70, 150), (80, 170)], fill="orange", width=1.5))
    d.sh.append(RECT(170, 55, 140, 30, fill="lightyellow", width=2)); d.sh.append(RECT(176, 63, 128, 14, fill="lightblue", stroke="none", width=0))
    d.line(310, 70, 345, 120, width=2); d.sh.append(RECT(325, 120, 60, 55, fill="white", width=2)); d.sh.append(RECT(327, 150, 56, 23, fill="lightblue", stroke="none", width=0))
    d.line(300, 118, 300, 87, arrow="end", color="blue", width=2); d.line(185, 52, 185, 22, arrow="end", color="blue", width=2)
    d.line(76, 40, 76, 98, width=3, color="red")
    d.text(268, 135, "cold water in", size=11); d.text(185, 16, "water out", size=11, anchor="middle")
    d.text(76, 33, "thermometer", size=11, anchor="start"); d.text(30, 190, "flask + heat", size=11, anchor="start"); d.text(240, 105, "condenser", size=11)
    d.text(355, 192, "pure liquid", size=11)
    return d.fig(440, 200)
def chrom_fig():
    d = Draw()
    d.sh.append(RECT(130, 20, 180, 150, fill="white", width=2))
    d.line(130, 150, 310, 150, color="grey", width=1.2, dash=True); d.line(130, 40, 310, 40, color="blue", width=1.2, dash=True)
    d.text(125, 154, "start line", size=11, anchor="end"); d.text(125, 44, "solvent front", size=11, anchor="end")
    # columns: mixture (two spots), dye 1, dye 2
    d.sh.append(CIRCLE(160, 96, 6, fill="red", width=1)); d.sh.append(CIRCLE(160, 62, 6, fill="blue", width=1))
    d.sh.append(CIRCLE(220, 96, 6, fill="red", width=1)); d.sh.append(CIRCLE(280, 62, 6, fill="blue", width=1))
    d.text(160, 186, "mixture", size=11); d.text(220, 186, "dye 1", size=11); d.text(280, 186, "dye 2", size=11)
    d.text(330, 98, "spot", size=11, anchor="start")
    return d.fig(400, 200)
Rf = 3.6 / 6.0; assert abs(Rf - 0.6) < 1e-9
assert abs((110 - 32) - 78) < 1e-9
build(ch, "separation-methods", "Methods of separation", 30,
      ["Describe filtration, evaporation, crystallisation, simple and fractional distillation, chromatography and use of a separating funnel.", "Choose the correct method for a mixture.", "Calculate Rf values and describe how melting and boiling points show purity."],
      [("key", "methode", "Choosing a method",
        "- **Filtration**: insoluble solid from a liquid (sand from water). The solid is the **residue**, the liquid the **filtrate**.\n- **Evaporation / crystallisation**: dissolved solid from a solution (salt from sea water; salt pans at the coast).\n- **Simple distillation**: pure solvent from a solution (pure water).\n- **Fractional distillation**: liquids with different boiling points (ethanol and water; crude oil).\n- **Separating funnel**: immiscible liquids (oil and water).\n- **Magnet**: iron from other solids. **Sublimation**: iodine or ammonium chloride from sand."),
       ("fig", still_fig(), "Simple distillation of a solution.", "A flask with a thermometer and heat on the left, a slanting condenser with cold water going in at the lower end and out at the upper end, and a beaker collecting pure liquid on the right."),
       ("key", "methode", "Chromatography and purity",
        "In **paper chromatography** a spot of the mixture is placed above the solvent on the start line; the solvent rises and carries the substances by different distances. **Rf = distance moved by the substance ÷ distance moved by the solvent front**. The same substance always gives the same Rf in the same solvent. A **pure** substance has a **sharp** melting or boiling point; an impure one melts over a range and at a lower temperature, boils at a higher temperature."),
       ("fig", chrom_fig(), "A chromatogram: the mixture contains two dyes, as shown by comparing with dye 1 and dye 2.", "A paper strip with a start line and solvent front; the mixture column has two spots matching the single spots of dye 1 and dye 2."),
       ("key", "pieges", "Common mistakes",
        "- Filtration cannot separate a dissolved solid: use evaporation or crystallisation.\n- The start line of a chromatogram is drawn in **pencil** (pencil does not dissolve) and kept **above** the solvent.\n- Distillation uses a thermometer at the top of the flask near the side arm: it reads the boiling point of the vapour."),
       ("example", "Rf value", "In a chromatogram the solvent front moved 6.0 cm and a dye spot moved 3.6 cm. Find its Rf.", ["Rf = 3.6 ÷ 6.0.", "= 0.6."], "Rf = 0.6"),
       ("example", "Separate salt and sand", "Describe how to obtain dry sand and dry salt from a mixture.", ["Add water, stir to dissolve the salt, then filter; wash and dry the residue (sand).", "Heat the filtrate in an evaporating dish until crystals form and dry them (salt)."], "Dissolve, filter, evaporate the filtrate.")],
      [("mcq", "Which method separates ethanol from a mixture of ethanol and water?", "fractional distillation", ["filtration", "chromatography only", "using a magnet"], "Their boiling points differ."),
       ("num", "A spot moves 2.4 cm while the solvent front moves 8.0 cm. Find the Rf.", 0.3, "2.4 ÷ 8.0 = 0.3.", 0.01),
       ("match", "Match each mixture with a separation method.", [("sand and water", "filtration"), ("oil and water", "separating funnel"), ("iron filings and sulphur", "magnet"), ("dyes in black ink", "paper chromatography")], "Based on different properties."),
       ("tf", "A pure substance melts over a wide range of temperatures.", False, "A pure substance has a sharp melting point."),
       ("open", "Describe how to obtain pure water from salt water in the laboratory.", "Heat the salt water in a flask fitted with a thermometer; the water boils and the steam passes through a condenser with cold water, where it condenses and is collected in a beaker; the salt remains in the flask.", ["1 mark: boil", "1 mark: condense in condenser", "1 mark: collect pure water, salt left behind"]),
       ("prob", "A student has a mixture of salt, sand and iron filings.", [("mcq", "Which solid is removed first with a magnet?", "iron", ["sand", "salt", "water"], "Only iron is attracted."), ("mcq", "After adding water and filtering, the residue is:", "sand", ["salt", "iron", "water"], "Sand is insoluble."), ("open", "How is the salt obtained from the filtrate?", "Evaporate most of the water and crystallise the salt, or evaporate to dryness.", ["1 mark for evaporation/crystallisation"], 2)])],
      [("The liquid that passes through a filter is the:", "filtrate", ["residue", "solvent front", "precipitate"], "Filtrate."),
       ("Rf is calculated as:", "distance moved by the spot ÷ distance moved by the solvent", ["distance of solvent ÷ distance of spot", "spot distance × solvent distance", "spot distance − solvent distance"], "Always less than 1."),
       ("Simple distillation is used to obtain:", "pure water from salt water", ["salt from sand", "iron from sand", "sand from water"], "The liquid is boiled off and condensed."),
       ("Crude oil is separated by:", "fractional distillation", ["filtration", "magnetism", "crystallisation"], "Different boiling points."),
       ("Which substance is likely pure?", "one with a sharp boiling point", ["one that boils over a wide range", "one that colours the flame green", "one that floats"], "Purity test.")],
      ["Fractional distillation is mentioned at an introductory level; check the Form 3 syllabus. Safety: heating flammable liquids to be done by the teacher."])

# =========================================================== 3 Atoms and the periodic table
ch = p.chapter("atoms", "Atoms and the periodic table", "Form 3 Chemistry — Atomic structure, electron arrangement, periodic table (to be checked)")
def config(z):
    out = []
    for cap in (2, 8, 8, 2):
        n = min(z, cap); out.append(n); z -= n
        if z == 0: break
    return out
assert config(11) == [2, 8, 1] and config(17) == [2, 8, 7] and config(20) == [2, 8, 8, 2] and config(8) == [2, 6] and config(18) == [2, 8, 8]
def bohr(d, cx, cy, z):
    cf = config(z)
    d.sh.append(CIRCLE(cx, cy, 11, fill="lightorange", width=2))
    for i, n in enumerate(cf):
        r = 24 + 20 * i
        d.sh.append(CIRCLE(cx, cy, r, fill=None, stroke="grey", width=1.2))
        for k in range(n):
            a = -math.pi / 2 + 2 * math.pi * k / n
            d.sh.append(CIRCLE(cx + r * math.cos(a), cy + r * math.sin(a), 3.5, fill="blue", width=1))
def atoms_fig():
    d = Draw(); bohr(d, 120, 85, 11); bohr(d, 360, 85, 17)
    d.text(120, 170, "sodium: 2, 8, 1", size=12, bold=True); d.text(360, 170, "chlorine: 2, 8, 7", size=12, bold=True)
    return d.fig(480, 185)
assert abs(0.75 * 35 + 0.25 * 37 - 35.5) < 1e-9
build(ch, "atomic-structure", "Atomic structure and electron arrangement", 30,
      ["Describe the structure of an atom and the relative mass and charge of protons, neutrons and electrons.", "Use atomic number and mass number and write electron arrangements for the first 20 elements.", "Define isotopes and use relative atomic mass."],
      [("key", "definition", "Inside the atom",
        "An atom has a tiny **nucleus** containing **protons** and **neutrons**, surrounded by **electrons** in shells. **Proton**: relative mass 1, charge +1. **Neutron**: mass 1, charge 0. **Electron**: mass about 1/1840 (negligible), charge −1. Atoms are **neutral**: number of protons = number of electrons. The **atomic number (proton number) Z** = number of protons; the **mass number A** = protons + neutrons, so **neutrons = A − Z**."),
       ("fig", atoms_fig(), "Electron arrangement of sodium (2, 8, 1) and chlorine (2, 8, 7).", "Two atoms drawn with a nucleus and circular shells with dots for electrons: sodium has 2, 8 and 1; chlorine has 2, 8 and 7."),
       ("key", "methode", "Electron arrangement",
        "Electrons fill shells from the nucleus outwards: **first shell 2, second 8, third 8** (for the first 20 elements), then the fourth shell. Sodium (Z = 11): **2, 8, 1**; chlorine (Z = 17): **2, 8, 7**; calcium (Z = 20): **2, 8, 8, 2**. The number of outer-shell electrons is the **group number**; the number of occupied shells is the **period**. An atom with a full outer shell (helium 2, neon 2,8, argon 2,8,8) is **unreactive**."),
       ("key", "definition", "Isotopes",
        "**Isotopes** are atoms of the same element (same Z) with **different numbers of neutrons** (different A). They have the **same chemical properties** because they have the same electrons. Examples: carbon-12 and carbon-14; chlorine-35 and chlorine-37. The **relative atomic mass A_r** is the average mass of the atoms of an element compared with 1/12 of an atom of carbon-12: chlorine is about 35.5 because natural chlorine is about 75 % Cl-35 and 25 % Cl-37."),
       ("key", "pieges", "Common mistakes",
        "- Isotopes have the same number of **protons**, not the same number of neutrons.\n- The mass number is not the atomic number: Na has Z = 11 and A = 23.\n- The second shell holds 8 electrons, not 2: only the first shell holds 2."),
       ("example", "Particles in an atom", "Sodium has atomic number 11 and mass number 23. Give the number of protons, neutrons and electrons and the electron arrangement.", ["Protons = Z = 11; electrons = 11; neutrons = 23 − 11 = 12.", "Electrons fill 2, 8 and then 1 in the third shell: 2, 8, 1."], "11 protons, 12 neutrons, 11 electrons; 2, 8, 1"),
       ("example", "Average mass of chlorine", "Chlorine is 75 % Cl-35 and 25 % Cl-37. Find its relative atomic mass.", ["Average = 0.75 × 35 + 0.25 × 37.", "= 26.25 + 9.25 = 35.5."], "35.5")],
      [("num", "An atom has atomic number 8 and mass number 16. How many neutrons does it have?", 8, "16 − 8 = 8.", 0),
       ("mcq", "The electron arrangement of an atom with 17 electrons is:", "2, 8, 7", ["2, 8, 8", "2, 7, 8", "2, 15"], "Fill 2, 8 then 7."),
       ("mcq", "Isotopes of an element have the same number of:", "protons", ["neutrons", "nucleons", "mass"], "Same atomic number."),
       ("num", "Magnesium has atomic number 12. How many electrons are in its outer shell?", 2, "Arrangement 2, 8, 2.", 0),
       ("open", "Explain why isotopes of chlorine have the same chemical properties.", "They have the same number of electrons (and protons) so they react in the same way; only the number of neutrons differs.", ["1 mark: same number of electrons", "1 mark: only neutrons differ"]),
       ("prob", "Potassium has atomic number 19 and mass number 39.", [("num", "Number of neutrons.", 20, "39 − 19 = 20.", 0), ("mcq", "Its electron arrangement is:", "2, 8, 8, 1", ["2, 8, 9", "2, 8, 8, 2", "2, 17"], "Outer shell 1 electron."), ("mcq", "Which group of the periodic table is it in?", "Group 1", ["Group 2", "Group 7", "Group 0"], "One outer electron.")])],
      [("A proton has a charge of:", "+1", ["−1", "0", "+2"], "Positive."),
       ("The number of neutrons equals:", "mass number − atomic number", ["atomic number + mass number", "atomic number only", "mass number only"], "Nucleons minus protons."),
       ("The maximum number of electrons in the second shell is:", "8", ["2", "18", "10"], "2, 8, 8 for the first 20 elements."),
       ("Which particle has almost no mass?", "electron", ["proton", "neutron", "nucleus"], "About 1/1840."),
       ("An atom with arrangement 2, 8, 8 is:", "unreactive", ["very reactive", "a metal", "a halogen"], "Full outer shell.")],
      ["Third shell capacity of 8 is the usual treatment up to calcium (the full capacity is 18); check the syllabus for isotopes and relative atomic mass (a calculation with percentages)."])

def ptable_fig():
    d = Draw(); x0, y0, bw, gap = 28, 28, 23, 2
    cols = {"H": (0, 0), "He": (17, 0)}
    seq = [("Li", "Be", "B", "C", "N", "O", "F", "Ne"), ("Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar")]
    for r, row in enumerate(seq):
        for k, sym in enumerate(row): cols[sym] = ((0, 1, 12, 13, 14, 15, 16, 17)[k], r + 1)
    cols["K"] = (0, 3); cols["Ca"] = (1, 3)
    kind = lambda s: "metal" if s in ("Li", "Be", "Na", "Mg", "Al", "K", "Ca") else ("noble" if s in ("He", "Ne", "Ar") else ("H" if s == "H" else "non"))
    colr = {"metal": "lightblue", "non": "lightorange", "noble": "lightgreen", "H": "lightyellow"}
    for s, (c, r) in cols.items():
        x = x0 + c * (bw + gap); y = y0 + r * (bw + gap)
        d.sh.append(RECT(x, y, bw, bw, fill=colr[kind(s)], width=1)); d.text(x + bw / 2, y + 15, s, size=10)
    for lab_, c in (("1", 0), ("2", 1), ("13", 12), ("14", 13), ("15", 14), ("16", 15), ("17", 16), ("18", 17)): d.text(x0 + c * (bw + gap) + bw / 2, 20, lab_, size=10)
    for r in range(4): d.text(14, y0 + r * (bw + gap) + 16, str(r + 1), size=10)
    d.text(240, 148, "blue: metals   orange: non-metals   green: noble gases", size=10)
    d.text(240, 162, "first 20 elements only; Groups 3 to 12 are left out", size=10)
    return d.fig(480, 172)
build(ch, "periodic-table", "The periodic table: groups and periods", 30,
      ["Describe the layout of the periodic table in terms of groups and periods.", "Describe the properties and trends of Group 1 (alkali metals), Group 7 (halogens) and Group 0 (noble gases).", "Use trends to predict properties."],
      [("key", "definition", "Groups and periods",
        "The periodic table arranges elements in order of **atomic number**. A vertical column is a **group**: elements in a group have the **same number of outer electrons** and similar properties. A horizontal row is a **period**: it shows the number of **shells**. Across a period there is a change from **metals** (left) to **non-metals** (right), ending in a noble gas."),
       ("fig", ptable_fig(), "The first 20 elements in the periodic table.", "A table showing hydrogen and helium in period 1, lithium to neon in period 2, sodium to argon in period 3, and potassium and calcium in period 4; metals, non-metals and noble gases in different colours."),
       ("key", "retenir", "Group 1: the alkali metals",
        "Lithium, sodium, potassium: **soft** (cut with a knife), shiny when cut but quickly **tarnish** in air, low density (Li, Na and K float on water), stored under oil. They react with water giving hydrogen and an alkali: " + E("2Na + 2H2O -> 2NaOH + H2") + ". **Reactivity increases down the group** (K is more violent than Na, which is more violent than Li). Flame colours: Li red, Na yellow, K lilac. Reactions are teacher demonstrations only."),
       ("key", "retenir", "Groups 7 and 0",
        "**Halogens** (F, Cl, Br, I): coloured, poisonous, **diatomic molecules** (Cl₂, Br₂, I₂). Chlorine is a pale green gas, bromine a red-brown liquid, iodine a grey-black solid. **Reactivity decreases down the group**: a more reactive halogen displaces a less reactive one: " + E("Cl2 + 2KBr -> 2KCl + Br2") + ". **Noble gases** (He, Ne, Ar): colourless, **unreactive** (full outer shell), single atoms. Uses: helium in balloons, neon in signs, argon in light bulbs."),
       ("key", "pieges", "Common mistakes",
        "- Reactivity **increases** down Group 1 but **decreases** down Group 7.\n- Group number = number of outer electrons (for Groups 1 to 7); the period is the number of shells.\n- Hydrogen is not an alkali metal although it sits above Group 1."),
       ("example", "Place an element", "An element has the electron arrangement 2, 8, 6. Give its group, its period and whether it is a metal.", ["Six outer electrons: Group 6 (16 in the 18-column numbering).", "Three shells: period 3. It is sulphur, a non-metal."], "Group 6, period 3, a non-metal (sulphur)"),
       ("example", "Predict a reaction", "Will chlorine react with potassium iodide solution? Why?", ["Chlorine is more reactive than iodine.", "It displaces iodine: Cl₂ + 2KI → 2KCl + I₂; the solution turns brown."], "Yes, chlorine displaces iodine.")],
      [("mcq", "Elements in the same group have similar properties because they have the same number of:", "outer electrons", ["protons", "shells", "neutrons"], "Outer electrons decide chemical behaviour."),
       ("match", "Match each element with its group description.", [("sodium", "alkali metal"), ("chlorine", "halogen"), ("neon", "noble gas"), ("calcium", "alkaline earth metal (Group 2)")], "Standard classification."),
       ("tf", "Reactivity increases down Group 7.", False, "It decreases down the halogens."),
       ("mcq", "Which of these is stored under oil?", "potassium", ["chlorine", "neon", "iodine"], "It reacts with air and water."),
       ("open", "State two ways in which potassium reacts differently from lithium when put in water.", "Potassium reacts more violently, moves faster, melts and may burn with a lilac flame; lithium reacts slowly. This shows reactivity increases down Group 1.", ["1 mark: potassium more vigorous", "1 mark: reactivity increases down the group"]),
       ("prob", "Fluorine, chlorine, bromine and iodine are halogens.", [("mcq", "The most reactive halogen listed is:", "fluorine", ["chlorine", "bromine", "iodine"], "Reactivity decreases down the group."), ("mcq", "Bromine is added to potassium iodide solution. What happens?", "iodine is displaced", ["nothing", "chlorine forms", "the solution freezes"], "Bromine is more reactive than iodine."), ("open", "Write the balanced equation for chlorine displacing bromine from potassium bromide.", E("Cl2 + 2KBr -> 2KCl + Br2"), ["1 mark: correct formulae", "1 mark: balanced"], 2)])],
      [("Elements in a vertical column are in the same:", "group", ["period", "shell", "isotope"], "Group."),
       ("Which gas is unreactive?", "argon", ["chlorine", "fluorine", "oxygen"], "Noble gas."),
       ("Which is a typical property of alkali metals?", "soft and low density", ["hard and dense", "dull and brittle", "gases"], "Cut with a knife."),
       ("The reactivity of the halogens down the group:", "decreases", ["increases", "stays the same", "becomes zero at once"], "Fluorine is most reactive."),
       ("The element in period 3, Group 1 is:", "sodium", ["lithium", "potassium", "magnesium"], "Na.")],
      ["Group numbering: Groups 1 to 7 and 0 are used in the course (also 1 to 18). Alkali metal reactions are teacher demonstrations: safety flagged. Flame colours and colours of halogens are standard."])

# =========================================================== 4 Bonding and equations
ch = p.chapter("bonding", "Bonding, formulae and equations", "Form 3 Chemistry — Chemical bonding, formulae, equations (to be checked)")
def ionic_fig():
    d = Draw()
    d.sh.append(CIRCLE(70, 75, 38, fill="lightorange", width=2)); d.text(70, 80, "Na", size=14, bold=True)
    d.sh.append(CIRCLE(170, 75, 44, fill="lightgreen", width=2)); d.text(170, 80, "Cl", size=14, bold=True)
    d.sh.append(CIRCLE(120, 50, 4, fill="blue", width=1)); d.line(120, 40, 140, 40, arrow="end", color="blue", width=1.5)
    d.text(120, 25, "e⁻ transferred", size=11)
    d.line(225, 75, 270, 75, arrow="end", width=2)
    d.sh.append(CIRCLE(320, 75, 30, fill="lightorange", width=2)); d.text(320, 80, "Na⁺", size=14, bold=True)
    d.sh.append(CIRCLE(405, 75, 46, fill="lightgreen", width=2)); d.text(405, 80, "Cl⁻", size=14, bold=True)
    d.text(70, 145, "2, 8, 1", size=11); d.text(170, 145, "2, 8, 7", size=11); d.text(320, 145, "2, 8", size=11); d.text(405, 145, "2, 8, 8", size=11)
    d.text(240, 172, "metal atom gives an electron to a non-metal atom: ions attract", size=11)
    return d.fig(480, 185)
def water_fig():
    d = Draw()
    d.sh.append(CIRCLE(120, 70, 30, fill="lightorange", width=2)); d.text(120, 75, "O", size=14, bold=True)
    d.sh.append(CIRCLE(80, 130, 20, fill="lightblue", width=2)); d.text(80, 135, "H", size=12, bold=True)
    d.sh.append(CIRCLE(160, 130, 20, fill="lightblue", width=2)); d.text(160, 135, "H", size=12, bold=True)
    d.text(200, 90, "each O–H bond = one shared pair", size=11, anchor="start")
    d.text(200, 112, "H₂O: 2 shared pairs", size=12, anchor="start", bold=True)
    return d.fig(480, 165)
ion_forms = {("Na", 1, "Cl", 1): "NaCl", ("Mg", 2, "O", 2): "MgO", ("Ca", 2, "Cl", 1): "CaCl2", ("Al", 3, "O", 2): "Al2O3", ("Na", 1, "O", 2): "Na2O"}
for (c_, a_, an_, b_), v in ion_forms.items(): assert ionic(c_, a_, an_, b_) == v, v
assert ionic("Ca", 2, "OH", 1, True) == "Ca(OH)2" and ionic("Al", 3, "SO4", 2, True) == "Al2(SO4)3" and ionic("Na", 1, "CO3", 2, True) == "Na2CO3"
build(ch, "bonding", "Chemical bonding: ionic and covalent", 30,
      ["Describe how ionic bonds form by transfer of electrons.", "Describe covalent bonds as shared pairs of electrons.", "Link bonding to properties: melting point and electrical conductivity."],
      [("key", "definition", "Ionic bonding",
        "Atoms bond to get a **full outer shell**. In **ionic bonding** a **metal** atom **gives** electrons to a **non-metal** atom. The metal forms a **positive ion** (cation) and the non-metal a **negative ion** (anion): Na → Na⁺ + e⁻ and Cl + e⁻ → Cl⁻. The oppositely charged ions attract strongly in a **giant lattice**. Formulae come from charges: Na⁺Cl⁻ → NaCl; Mg²⁺O²⁻ → MgO; Ca²⁺ with two Cl⁻ → CaCl₂."),
       ("fig", ionic_fig(), "Ionic bonding in sodium chloride.", "A sodium atom giving one electron to a chlorine atom, forming a sodium ion and a chloride ion, each with a full outer shell."),
       ("key", "definition", "Covalent bonding",
        "In **covalent bonding** two **non-metal** atoms **share pairs of electrons**. Examples: hydrogen H₂ (1 pair), chlorine Cl₂ (1), water H₂O (2 pairs), ammonia NH₃, methane CH₄, oxygen O₂ (a **double bond**, 2 pairs) and nitrogen N₂ (a **triple bond**, 3 pairs), carbon dioxide CO₂ (two double bonds). Most covalent substances form separate **molecules**."),
       ("fig", water_fig(), "A water molecule: oxygen shares one pair of electrons with each hydrogen.", "An oxygen atom bonded to two hydrogen atoms, with the note that each O-H bond is a shared pair of electrons."),
       ("key", "retenir", "Bonding and properties",
        "**Ionic compounds**: high melting points, hard but brittle, **conduct electricity when molten or dissolved** (ions move) but not as solids. **Simple molecular compounds**: low melting and boiling points, **do not conduct**. **Giant covalent structures** (diamond, graphite, silica): very high melting points; diamond is very hard; **graphite** is soft and conducts. **Metals**: positive ions in a sea of free electrons; they conduct and are malleable."),
       ("key", "pieges", "Common mistakes",
        "- Ionic solids do not conduct because their ions cannot move; they conduct when melted or in solution.\n- Covalent does not mean weak: the bonds inside a molecule are strong, but the forces **between** molecules are weak.\n- Atoms bond to reach a full outer shell, not to 'become happy'."),
       ("example", "Ionic formula", "Write the formula of aluminium oxide (Al³⁺ and O²⁻).", ["Cross over the charges: Al gets 2, O gets 3.", "Formula Al₂O₃ (total charge 6+ and 6−)."], "Al₂O₃"),
       ("example", "Explain conductivity", "Why does solid sodium chloride not conduct but molten sodium chloride does?", ["In the solid the ions are fixed in the lattice.", "When molten the ions are free to move and carry charge."], "The ions can move only when molten or dissolved.")],
      [("mcq", "Which compound is formed by transfer of electrons?", "sodium chloride", ["water", "methane", "carbon dioxide"], "A metal and a non-metal."),
       ("mcq", "Which substance has a covalent double bond?", "oxygen, O₂", ["sodium chloride", "hydrogen, H₂", "magnesium"], "O=O."),
       ("num", "How many pairs of electrons are shared between the two nitrogen atoms in N₂?", 3, "N₂ has a triple bond.", 0),
       ("tf", "Solid sodium chloride conducts electricity.", False, "Its ions cannot move."),
       ("open", "Explain why simple molecular compounds such as methane have low boiling points.", "The forces between molecules are weak and need little energy to overcome, even though the bonds inside the molecules are strong.", ["1 mark: weak forces between molecules", "1 mark: bonds within molecules are strong and not broken"]),
       ("prob", "Magnesium (2, 8, 2) reacts with oxygen (2, 6) to make magnesium oxide.", [("mcq", "Magnesium atoms:", "lose 2 electrons", ["gain 2 electrons", "share 2 electrons", "lose 6 electrons"], "To reach 2, 8."), ("mcq", "The formula of magnesium oxide is:", "MgO", ["Mg₂O", "MgO₂", "Mg₂O₂"], "Charges 2+ and 2−."), ("open", "State two properties of magnesium oxide that follow from its ionic bonding.", "High melting point; conducts electricity when molten or dissolved but not when solid; brittle.", ["1 mark each, maximum 2"], 2)])],
      [("In ionic bonding electrons are:", "transferred", ["shared", "destroyed", "unchanged"], "From metal to non-metal."),
       ("In covalent bonding atoms:", "share electrons", ["lose all electrons", "gain neutrons", "form ions"], "Shared pairs."),
       ("Which substance conducts electricity when solid?", "graphite", ["sodium chloride", "diamond", "sugar"], "It has free electrons."),
       ("The formula of calcium chloride is:", "CaCl₂", ["CaCl", "Ca₂Cl", "Ca₂Cl₂"], "Ca²⁺ with two Cl⁻."),
       ("A positive ion is formed when an atom:", "loses electrons", ["gains electrons", "gains protons", "loses protons"], "Electron loss.")],
      ["Dot-and-cross diagrams are drawn as ring models only; check how detailed the Form 3 syllabus expects bonding to be (metallic bonding, intermolecular forces)."])

def eq_fig():
    d = Draw()
    def h2(x, y): d.sh.append(CIRCLE(x - 7, y, 7, fill="lightblue", width=1.5)); d.sh.append(CIRCLE(x + 7, y, 7, fill="lightblue", width=1.5))
    def o2(x, y): d.sh.append(CIRCLE(x - 11, y, 11, fill="lightorange", width=1.5)); d.sh.append(CIRCLE(x + 11, y, 11, fill="lightorange", width=1.5))
    def h2o(x, y): d.sh.append(CIRCLE(x, y, 11, fill="lightorange", width=1.5)); d.sh.append(CIRCLE(x - 11, y + 13, 7, fill="lightblue", width=1.5)); d.sh.append(CIRCLE(x + 11, y + 13, 7, fill="lightblue", width=1.5))
    h2(60, 60); h2(60, 110); o2(150, 85); d.text(105, 88, "+", size=18)
    d.line(210, 85, 250, 85, arrow="end", width=2)
    h2o(320, 60); h2o(320, 110)
    d.text(105, 150, "2 H₂ + O₂", size=13, bold=True); d.text(320, 150, "2 H₂O", size=13, bold=True)
    d.text(210, 176, "4 H and 2 O on each side: the equation is balanced", size=11)
    return d.fig(430, 190)
assert E("2H2 + O2 -> 2H2O") and E("CaCO3 -> CaO + CO2") and E("Mg + 2HCl -> MgCl2 + H2") and E("4Al + 3O2 -> 2Al2O3") and E("Fe2O3 + 3CO -> 2Fe + 3CO2")
build(ch, "formulae-equations", "Formulae and chemical equations", 30,
      ["Write formulae of compounds using valencies.", "Write word equations and balanced symbol equations with state symbols.", "Check that an equation is balanced by counting atoms."],
      [("key", "methode", "Writing a formula",
        "Valencies (combining powers): **H, Na, K, Ag, Cl, OH, NO₃ = 1**; **Mg, Ca, Zn, Cu(II), O, S, CO₃, SO₄ = 2**; **Al = 3** (iron is 2 in iron(II), 3 in iron(III)). **Cross over** the valencies and simplify: magnesium chloride Mg(2) Cl(1) → MgCl₂; aluminium oxide Al(3) O(2) → Al₂O₃; calcium hydroxide Ca(2) OH(1) → Ca(OH)₂; aluminium sulphate Al(3) SO₄(2) → Al₂(SO₄)₃. Use a **bracket** if more than one of a group is needed."),
       ("fig", eq_fig(), "A balanced equation shown with particles.", "Two hydrogen molecules and one oxygen molecule on the left, an arrow, and two water molecules on the right, with the note that there are 4 H and 2 O on each side."),
       ("key", "methode", "Balancing equations",
        "A **word equation** names reactants and products: *magnesium + oxygen → magnesium oxide*. In a **balanced symbol equation** the number of atoms of each element is the **same on both sides** because atoms are not created or destroyed. Steps: (1) write correct formulae; (2) count atoms; (3) change **coefficients** (big numbers in front), **never the subscripts**; (4) recount. Example: " + E("2Mg + O2 -> 2MgO") + " and " + E("4Al + 3O2 -> 2Al2O3") + "."),
       ("key", "retenir", "State symbols and examples",
        "**(s)** solid, **(l)** liquid, **(g)** gas, **(aq)** aqueous (dissolved in water). Examples: " + E("Mg(s) + 2HCl(aq) -> MgCl2(aq) + H2(g)") + "; " + E("CaCO3(s) -> CaO(s) + CO2(g)") + " (heating limestone); " + E("Fe2O3 + 3CO -> 2Fe + 3CO2") + " (making iron)."),
       ("key", "pieges", "Common mistakes",
        "- Never change a subscript to balance an equation: H₂O₂ is hydrogen peroxide, not water.\n- Balance one element at a time and recount all the others.\n- A bracket multiplies everything inside it: Ca(OH)₂ has 2 O and 2 H."),
       ("example", "Balance an equation", "Balance: Al + O₂ → Al₂O₃.", ["Oxygen: 3 O₂ gives 6 O, and 2 Al₂O₃ also gives 6 O.", "Then Al: 2 Al₂O₃ has 4 Al, so write 4 Al: 4Al + 3O₂ → 2Al₂O₃."], E("4Al + 3O2 -> 2Al2O3")),
       ("example", "Formula of aluminium sulphate", "Write the formula of aluminium sulphate (Al³⁺, SO₄²⁻).", ["Cross over: Al gets 2, SO₄ gets 3.", "Brackets are needed for SO₄: Al₂(SO₄)₃."], "Al₂(SO₄)₃")],
      [("mcq", "The formula of calcium hydroxide is:", "Ca(OH)₂", ["CaOH", "Ca₂OH", "CaOH₂"], "Ca is 2 and OH is 1."),
       ("mcq", "Which equation is balanced?", E("2Na + 2H2O -> 2NaOH + H2"), ["Na + H₂O → NaOH + H₂", "2Na + H₂O → 2NaOH + H₂", "Na + 2H₂O → NaOH + H₂"], "Count Na, H and O on each side."),
       ("num", "How many atoms of oxygen are on the left side of 2KClO₃ → 2KCl + 3O₂?", 6, "2 × 3 = 6 O atoms.", 0),
       ("match", "Match each state symbol with its meaning.", [("(s)", "solid"), ("(l)", "liquid"), ("(g)", "gas"), ("(aq)", "dissolved in water")], "Standard symbols."),
       ("open", "Balance this equation and show your counting: Fe₂O₃ + CO → Fe + CO₂.", E("Fe2O3 + 3CO -> 2Fe + 3CO2") + ". Left: Fe 2, O 3 + 3 = 6, C 3. Right: Fe 2, C 3, O 6.", ["1 mark: 3CO and 3CO₂", "1 mark: 2Fe", "1 mark: atoms counted both sides"]),
       ("prob", "Magnesium burns in oxygen with a bright white flame to give a white powder.", [("mcq", "The white powder is:", "magnesium oxide", ["magnesium chloride", "carbon", "water"], "Metal plus oxygen."), ("mcq", "The balanced equation is:", E("2Mg + O2 -> 2MgO"), ["Mg + O₂ → MgO₂", "Mg + O₂ → MgO", "2Mg + 2O₂ → 2MgO"], "Count atoms: 2 Mg and 2 O each side."), ("num", "How many magnesium atoms react with one oxygen molecule?", 2, "Equation 2Mg + O₂ → 2MgO.", 0)])],
      [("Atoms in a balanced equation are:", "the same number on both sides", ["more on the left", "more on the right", "not counted"], "Conservation of atoms."),
       ("A coefficient is:", "a number in front of a formula", ["a small number below", "a state symbol", "a charge"], "It multiplies the whole formula."),
       ("The formula of aluminium oxide is:", "Al₂O₃", ["AlO", "Al₃O₂", "Al₂O"], "Cross-over of 3 and 2."),
       ("State symbol (aq) means:", "dissolved in water", ["solid", "gas", "liquid"], "Aqueous."),
       ("To balance an equation you may change:", "the coefficients", ["the subscripts", "the elements", "the charges only"], "Never change formulae.")],
      ["Valency of Cu (2) and Fe (2 or 3) is given for the common ions; check which transition metals the syllabus uses. All equations are checked by an atom-count in the generator script."])

# =========================================================== 5 Air, water, acids
ch = p.chapter("air-water", "Air and water", "Form 3 Chemistry — Air, combustion, rusting, water and solubility (to be checked)")
def rust_fig():
    d = Draw()
    for x, fill, ttl, res in ((80, None, "dry air", "no rust"), (240, "lightblue", "boiled water + oil", "no rust"), (400, "lightblue", "air + water", "rust forms")):
        d.sh.append(POLY([(x - 30, 30), (x - 30, 125), (x - 20, 145), (x + 20, 145), (x + 30, 125), (x + 30, 30)], closed=False, width=2))
        if fill: d.sh.append(RECT(x - 29, 75, 58, 62, fill=fill, stroke="none", width=0))
        d.line(x - 14, 120, x + 14, 130, color="grey", width=5)
        d.text(x, 168, ttl, size=11); d.text(x, 184, res, size=12, bold=True, color="red" if res == "rust forms" else "green")
    d.sh.append(RECT(51, 118, 58, 14, fill="lightyellow", stroke="none", width=0)); d.text(80, 100, "drying agent", size=10)
    d.sh.append(RECT(211, 75, 58, 12, fill="yellow", stroke="none", width=0)); d.text(240, 66, "layer of oil", size=10)
    return d.fig(480, 200)
used = 60 - 47.4; assert abs(used / 60 - 0.21) < 1e-9
assert E("C + O2 -> CO2") and E("S + O2 -> SO2") and E("CH4 + 2O2 -> CO2 + 2H2O") and E("2H2O2 -> 2H2O + O2") and E("2Mg + O2 -> 2MgO") and E("2CO + O2 -> 2CO2")
build(ch, "air", "Air, combustion and rusting", 30,
      ["State the composition of air and describe how nitrogen and oxygen are obtained from liquid air.", "Describe combustion, tests for oxygen and carbon dioxide and the preparation of oxygen.", "Describe the conditions for rusting and methods of rust prevention; name major air pollutants."],
      [("key", "retenir", "Composition and separation of air",
        "Dry air: about **78 % nitrogen, 21 % oxygen**, about 0.9 % argon, about 0.04 % carbon dioxide and traces of other gases. The gases are separated by **fractional distillation of liquid air**: air is cooled until liquid, then warmed, and the gases boil off at different temperatures (nitrogen at −196 °C, argon at −186 °C, oxygen at −183 °C). Uses: nitrogen in making ammonia and fertilisers, oxygen in medicine and welding, argon in light bulbs."),
       ("key", "methode", "Combustion and tests",
        "**Combustion** is burning in oxygen, giving heat and light. Metals form **oxides**: " + E("2Mg + O2 -> 2MgO") + ". Non-metals form **acidic oxides**: " + E("C + O2 -> CO2") + " and " + E("S + O2 -> SO2") + "; methane: " + E("CH4 + 2O2 -> CO2 + 2H2O") + ". Oxygen can be made in the laboratory by decomposing hydrogen peroxide with manganese(IV) oxide as a **catalyst**: " + E("2H2O2 -> 2H2O + O2") + ". Tests: **oxygen relights a glowing splint**; **carbon dioxide turns limewater milky**."),
       ("fig", rust_fig(), "Rusting needs both air (oxygen) and water.", "Three test tubes with an iron nail: dry air gives no rust; boiled water under oil gives no rust; air and water gives rust."),
       ("key", "retenir", "Rusting",
        "**Rusting** is the slow reaction of iron with **oxygen and water**: iron + oxygen + water → hydrated iron(III) oxide (rust). Salt speeds it up (coastal towns like Limbe and Douala). Prevention: **painting, oiling, plastic coating, galvanising** (zinc coating), **sacrificial protection** with a more reactive metal, and using **stainless steel** (an alloy)."),
       ("key", "attention", "Air pollution",
        "**Carbon monoxide** (from incomplete burning of fuel, charcoal or wood) is **poisonous**: never run an engine or burn charcoal in a closed room. **Sulphur dioxide** and **oxides of nitrogen** cause **acid rain**. **Carbon dioxide** and methane are **greenhouse gases**. Smoke particles harm the lungs. Reduce pollution with cleaner fuels, ventilation, catalytic converters and tree planting."),
       ("key", "pieges", "Common mistakes",
        "- Rusting needs **both** oxygen and water: either alone is not enough.\n- A catalyst is not used up: manganese(IV) oxide is unchanged at the end of the reaction.\n- Carbon monoxide has no smell; you cannot rely on smell to warn you."),
       ("example", "Percentage of oxygen", "In an experiment 60 cm³ of air is passed over heated copper until the volume stops changing at 47.4 cm³. Find the percentage of oxygen.", ["Oxygen used = 60 − 47.4 = 12.6 cm³.", "Percentage = 12.6 ÷ 60 × 100 = 21 %."], "21 %"),
       ("example", "Rust prevention", "Explain why galvanised (zinc-coated) iron roofing sheets last longer.", ["The zinc coating keeps oxygen and water away from the iron.", "If scratched, zinc, being more reactive, corrodes first and protects the iron."], "Zinc coats and protects the iron.")],
      [("mcq", "Which gas makes up about 78 % of air?", "nitrogen", ["oxygen", "argon", "carbon dioxide"], "Nitrogen."),
       ("mcq", "A gas relights a glowing splint. It is:", "oxygen", ["carbon dioxide", "nitrogen", "hydrogen"], "Test for oxygen."),
       ("num", "70 cm³ of air is passed over heated copper and the volume falls to 55.3 cm³. Find the percentage of oxygen (1 d.p.).", 21.0, "(70 − 55.3) ÷ 70 × 100 = 21.0 %.", 0.1, "%"),
       ("tf", "Iron will rust in boiled water covered with oil.", False, "There is no dissolved air."),
       ("open", "Explain two ways to prevent iron gates from rusting.", "Paint or oil them to keep out air and water; galvanise them with zinc, which also protects the iron if scratched.", ["1 mark each, maximum 2"]),
       ("prob", "A pupil burns a piece of magnesium ribbon in air.", [("mcq", "The white product is:", "magnesium oxide", ["magnesium carbonate", "magnesium chloride", "carbon"], "Magnesium + oxygen."), ("mcq", "The balanced equation is:", E("2Mg + O2 -> 2MgO"), ["Mg + O₂ → MgO", "Mg + O₂ → MgO₂", "Mg₂ + O → Mg₂O"], "2 Mg and 2 O on each side."), ("open", "Why should the burning magnesium not be looked at directly?", "The flame is extremely bright and the ultraviolet light can damage eyes.", ["1 mark for the bright light hazard"], 2)])],
      [("Nitrogen and oxygen are separated from air by:", "fractional distillation of liquid air", ["filtration", "evaporation", "magnets"], "Different boiling points."),
       ("Rusting needs:", "oxygen and water", ["oxygen only", "water only", "carbon dioxide"], "Both."),
       ("A catalyst is a substance that:", "speeds up a reaction and is not used up", ["is used up", "slows a reaction", "adds mass to products"], "Definition."),
       ("The gas that causes acid rain is:", "sulphur dioxide", ["nitrogen", "argon", "oxygen"], "SO₂."),
       ("Carbon monoxide is dangerous because it:", "is poisonous", ["is a metal", "causes rust", "is a liquid"], "It stops blood carrying oxygen.")],
      ["Boiling points of N₂ (−196 °C), Ar (−186 °C) and O₂ (−183 °C) are standard; percentage composition rounded. Safety: burning magnesium and any use of gas cylinders are teacher demonstrations."])

KNO3 = [(0, 13), (20, 32), (40, 64), (60, 110), (80, 169), (100, 246)]
sol_fig = plot(0, 100, 0, 250, segments=[(KNO3[i][0], KNO3[i][1], KNO3[i + 1][0], KNO3[i + 1][1], "blue", False) for i in range(5)],
               points=[(t, s, None, "red") for t, s in KNO3], grid=20, xlabel="θ (°C)", ylabel="g per 100 g water", w=440, h=270)
assert 110 - 32 == 78 and 2 * 78 == 156
assert E("Ca(HCO3)2 -> CaCO3 + H2O + CO2") and E("Na2CO3 + CaSO4 -> CaCO3 + Na2SO4")
build(ch, "water", "Water, solubility and hard water", 30,
      ["Describe the solvent properties of water and read a solubility curve.", "Distinguish temporary and permanent hardness and describe how hardness is removed.", "Describe the main steps in water treatment and sources of water pollution."],
      [("key", "definition", "Water as a solvent",
        "Water dissolves many ionic compounds and some covalent ones, so natural water always contains dissolved substances. The **solubility** of a solid is the mass that dissolves in **100 g of water** at a given temperature to make a **saturated solution**. For most solids solubility **increases with temperature**; gases become **less** soluble when hot. Cooling a hot saturated solution makes the extra solute **crystallise**."),
       ("fig", sol_fig, "Solubility curve of potassium nitrate in water (approximate values).", "A graph of solubility in grams per 100 grams of water against temperature, rising from about 13 at 0 degrees to 32 at 20, 64 at 40, 110 at 60, 169 at 80 and 246 at 100 degrees."),
       ("key", "retenir", "Hard and soft water",
        "**Hard water** contains dissolved **calcium and magnesium ions**, from rocks such as limestone; it does not easily make a lather with soap and forms **scum**. **Temporary hardness** (calcium hydrogencarbonate) is removed by **boiling**: " + E("Ca(HCO3)2 -> CaCO3 + H2O + CO2") + "; the solid forms **scale** in kettles and pipes. **Permanent hardness** (calcium sulphate) is not removed by boiling but by **washing soda** (sodium carbonate) or an **ion-exchange** resin: " + E("Na2CO3 + CaSO4 -> CaCO3 + Na2SO4") + "."),
       ("key", "retenir", "Water treatment and pollution",
        "Town supply: **screening**, **coagulation and settling**, **filtration** through sand, and **chlorination** to kill germs. Distilled water is the purest and is used in laboratories. Water is **polluted** by sewage, fertiliser run-off, oil, chemicals from mines and factories and plastic waste. Do not drink water of unknown quality: **boil it** or treat it first."),
       ("key", "pieges", "Common mistakes",
        "- Solubility is quoted **per 100 g of water**, not per litre of solution.\n- Boiling removes only **temporary** hardness.\n- Soft water is not necessarily purer or safer; it simply lathers well."),
       ("example", "Mass that crystallises", "A saturated solution of potassium nitrate containing 100 g of water at 60 °C is cooled to 20 °C. Use the graph values (110 g and 32 g per 100 g) to find the mass of crystals.", ["At 60 °C: 110 g dissolved. At 20 °C: only 32 g can stay dissolved.", "Crystals = 110 − 32 = 78 g."], "78 g"),
       ("example", "Which type of hardness?", "A kettle forms scale, and the water also lathers badly again after boiling. Explain.", ["Scale (CaCO₃) comes from temporary hardness removed by boiling.", "If hardness remains after boiling, part of it is permanent hardness."], "Both temporary and permanent hardness are present.")],
      [("num", "Using 110 g per 100 g at 60 °C and 32 g per 100 g at 20 °C, find the mass of crystals when a saturated solution made with 200 g of water at 60 °C is cooled to 20 °C (g).", 156, "(110 − 32) × 2 = 156 g.", 0, "g"),
       ("mcq", "Boiling removes:", "temporary hardness", ["permanent hardness", "all dissolved salts", "nothing"], "Calcium hydrogencarbonate decomposes."),
       ("mcq", "Which substance can be used to soften permanently hard water?", "washing soda (sodium carbonate)", ["salt", "sugar", "chlorine"], "It precipitates calcium carbonate."),
       ("tf", "Soap makes a good lather in hard water.", False, "It forms scum with the calcium ions."),
       ("open", "Describe how a pupil can compare the hardness of two water samples with soap solution.", "Shake equal volumes of each sample with the same amount of soap solution; the sample needing more soap (or giving less lather and scum) is the harder one.", ["1 mark: equal volumes and same soap", "1 mark: compare lather or amount of soap needed"]),
       ("prob", "Potassium nitrate has a solubility of 64 g per 100 g of water at 40 °C and 169 g per 100 g at 80 °C.", [("num", "Mass that dissolves in 50 g of water at 80 °C (g).", 84.5, "169 ÷ 2 = 84.5 g.", 0.1, "g"), ("num", "Mass of crystals when a saturated solution made with 100 g of water at 80 °C is cooled to 40 °C (g).", 105, "169 − 64 = 105 g.", 0, "g"), ("mcq", "A solution that holds the maximum possible solute at that temperature is:", "saturated", ["dilute", "distilled", "neutral"], "Definition.")])],
      [("Solubility is usually given as the mass that dissolves in:", "100 g of water", ["1 litre of solution", "10 g of water", "any amount of water"], "Standard convention."),
       ("Hard water contains dissolved:", "calcium and magnesium ions", ["sodium chloride only", "oxygen only", "sugar"], "From rocks."),
       ("Scale in kettles is mainly:", "calcium carbonate", ["salt", "iron", "sulphur"], "Formed on boiling."),
       ("The final step of town water treatment is usually:", "chlorination", ["distillation", "evaporation", "freezing"], "Kills germs."),
       ("When a saturated hot solution cools, the solute:", "crystallises", ["evaporates", "burns", "becomes a gas"], "Solubility falls.")],
      ["Solubility values for KNO₃ (13, 32, 64, 110, 169, 246 g per 100 g at 0, 20, 40, 60, 80, 100 °C) are approximate textbook values; check against the data booklet used in school.", "Water treatment and safe-water advice are general and to be reviewed by a public-health worker."])

ch = p.chapter("acids", "Acids, bases and indicators", "Form 3 Chemistry — Acids, bases, pH and neutralisation (to be checked)")
def ind_fig():
    d = Draw()
    d.text(80, 18, "indicator", size=12, bold=True); d.text(240, 18, "in acid", size=12, bold=True); d.text(380, 18, "in alkali", size=12, bold=True)
    rows = (("litmus", "red", "blue"), ("methyl orange", "red", "yellow"), ("phenolphthalein", "white", "pink"), ("universal indicator", "red", "purple"))
    for i, (n, a, b) in enumerate(rows):
        y = 30 + i * 36
        d.text(10, y + 20, n, size=11, anchor="start")
        d.sh.append(RECT(190, y, 100, 28, fill=a, width=1.5)); d.sh.append(RECT(330, y, 100, 28, fill=b, width=1.5))
    d.text(240, 188, "(white = colourless)", size=11)
    return d.fig(480, 200)
assert E("Mg + 2HCl -> MgCl2 + H2") and E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2") and E("NaOH + HCl -> NaCl + H2O") and E("CuO + H2SO4 -> CuSO4 + H2O") and E("Zn + H2SO4 -> ZnSO4 + H2")
build(ch, "acids-bases", "Acids, bases, indicators and pH", 30,
      ["Describe the properties and reactions of acids and bases.", "Use indicators and the pH scale.", "Describe neutralisation and write word and symbol equations for acid reactions."],
      [("key", "definition", "Acids and bases",
        "An **acid** produces **H⁺ ions** in water: hydrochloric acid HCl, sulphuric acid H₂SO₄, nitric acid HNO₃, ethanoic acid (vinegar). A **base** is a metal oxide or hydroxide that reacts with an acid to form a salt and water; a base that dissolves in water is an **alkali** and produces **OH⁻ ions**: sodium hydroxide NaOH, potassium hydroxide KOH, calcium hydroxide Ca(OH)₂ (limewater), ammonia solution. **Strong** acids and alkalis are fully ionised; **weak** ones only partly (ethanoic acid)."),
       ("fig", ind_fig(), "Colours of common indicators in acid and alkali.", "A table with four indicators and the colour of each in acid and in alkali: litmus red and blue, methyl orange red and yellow, phenolphthalein colourless and pink, universal indicator red and purple."),
       ("key", "retenir", "Reactions of acids",
        "- Acid + **metal** → salt + hydrogen: " + E("Mg + 2HCl -> MgCl2 + H2") + " (hydrogen gives a squeaky pop).\n- Acid + **carbonate** → salt + water + carbon dioxide: " + E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2") + ".\n- Acid + **base** → salt + water: " + E("CuO + H2SO4 -> CuSO4 + H2O") + ".\n- Acid + **alkali** (neutralisation): " + E("NaOH + HCl -> NaCl + H2O") + "; ionic equation H⁺ + OH⁻ → H₂O."),
       ("key", "retenir", "pH and salts",
        "**pH 0–6 acidic, 7 neutral, 8–14 alkaline.** Salt names: **chlorides** from HCl, **sulphates** from H₂SO₄, **nitrates** from HNO₃. Everyday: toothpaste and antacids are weak bases, soap is alkaline, lemon juice and vinegar are acidic. Wear eye protection with dilute acids; strong acids and alkalis are corrosive."),
       ("key", "pieges", "Common mistakes",
        "- Not every base is an alkali: copper(II) oxide is a base that does not dissolve.\n- A strong acid is not the same as a concentrated acid: strength is about ionisation, concentration is about how much acid is in the solution.\n- pH 7 is neutral; the pH of a strongly acidic solution is a small number."),
       ("example", "Complete the equation", "Write the balanced equation for zinc reacting with dilute sulphuric acid and name the gas.", ["Zinc + sulphuric acid → zinc sulphate + hydrogen: " + E("Zn + H2SO4 -> ZnSO4 + H2") + ".", "The gas burns with a squeaky pop: hydrogen."], E("Zn + H2SO4 -> ZnSO4 + H2") + "; hydrogen"),
       ("example", "Name the salt", "Name the salt formed when sodium hydroxide neutralises nitric acid.", ["Metal part from the base: sodium. Acid part from HNO₃: nitrate.", "Salt: sodium nitrate (plus water)."], "Sodium nitrate")],
      [("mcq", "A solution of pH 12 is:", "alkaline", ["acidic", "neutral", "an acid"], "Above 7."),
       ("match", "Match each reaction with its products.", [("acid + carbonate", "salt + water + carbon dioxide"), ("acid + metal", "salt + hydrogen"), ("acid + alkali", "salt + water"), ("acid + metal oxide", "salt + water (a base reacting)")], "Standard reactions."),
       ("mcq", "Which ion do all acids produce in water?", "H⁺", ["OH⁻", "Cl⁻", "Na⁺"], "Definition of an acid."),
       ("tf", "Methyl orange is yellow in alkali.", True, "Red in acid, yellow in alkali."),
       ("open", "Describe a test that identifies the gas given off when dilute hydrochloric acid is added to marble chips.", "Pass the gas into limewater: it turns milky, showing carbon dioxide.", ["1 mark: limewater", "1 mark: milky, carbon dioxide"]),
       ("prob", "Dilute hydrochloric acid is added drop by drop to sodium hydroxide solution containing phenolphthalein.", [("mcq", "At the start the solution is:", "pink", ["colourless", "red", "yellow"], "Phenolphthalein is pink in alkali."), ("mcq", "At neutralisation it becomes:", "colourless", ["pink", "blue", "yellow"], "Colourless in acid or neutral."), ("open", "Write the balanced equation for the reaction.", E("NaOH + HCl -> NaCl + H2O"), ["1 mark: formulae", "1 mark: balanced"], 2)])],
      [("On the pH scale a neutral solution has the value:", "7", ["0", "14", "10"], "pH 7."),
       ("Hydrogen gas is identified by:", "a squeaky pop with a lit splint", ["relighting a glowing splint", "turning limewater milky", "bleaching litmus"], "Test for hydrogen."),
       ("Sulphuric acid forms salts called:", "sulphates", ["chlorides", "nitrates", "carbonates"], "From SO₄²⁻."),
       ("A soluble base is called an:", "alkali", ["acid", "salt", "indicator"], "Alkali."),
       ("The reaction of an acid with an alkali is:", "neutralisation", ["decomposition", "combustion", "evaporation"], "Forms a salt and water.")],
      ["pH colours of universal indicator vary by brand; the definition of acid (H⁺ producer, Arrhenius) is used at this level. Practicals with strong acids/alkalis are teacher-led."])

p.write()
