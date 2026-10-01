import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *
from fs_chem import *

AR = {"H": 1, "C": 12, "N": 14, "O": 16, "Na": 23, "Mg": 24, "Al": 27, "Si": 28, "S": 32, "Cl": 35.5, "K": 39, "Ca": 40, "Fe": 56, "Cu": 64, "Zn": 65, "Ag": 108, "Pb": 207, "Br": 80}
def Mr(f):
    return sum(AR[a] * n for a, n in atoms(f).items())

p = Pack("form4-chemistry", "Chemistry — Form 4", level="Form 4", subject="chemistry", cursus="secondary",
         description="The second course in O Level Chemistry for Form 4: salts, the reactivity series, extraction of metals, the mole and reacting masses, energy changes and rates of reaction, carbon and its compounds, hydrocarbons, alcohols and polymers, and soaps and fertilisers. Worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Chemistry, Form 4 (second year of the GCE O Level Chemistry course) — to be checked against the official syllabus")

# =========================================================== 1 Salts and metals
ch = p.chapter("salts-metals", "Salts and metals", "Form 4 Chemistry — Salts, the reactivity series, extraction of metals (to be checked)")
salt_fl = flow(["Warm dilute|acid", "Add excess|insoluble base", "Filter off|excess solid", "Evaporate,|cool, crystallise"], w=480, size=11, bh=46, gap=18)
assert E("CuO + H2SO4 -> CuSO4 + H2O") and E("BaCl2 + Na2SO4 -> BaSO4 + 2NaCl") and E("Pb(NO3)2 + 2KI -> PbI2 + 2KNO3") and E("AgNO3 + NaCl -> AgCl + NaNO3") and E("Na2CO3 + 2HCl -> 2NaCl + H2O + CO2") and E("Zn + H2SO4 -> ZnSO4 + H2")
build(ch, "salts", "Making salts", 30,
      ["Use solubility rules to decide how to make a given salt.", "Describe the preparation of a soluble salt from an insoluble base, metal or carbonate, from an alkali by titration, and of an insoluble salt by precipitation.", "Describe tests for chloride, sulphate and carbonate ions."],
      [("key", "retenir", "Solubility rules",
        "- **All nitrates** and all salts of **sodium, potassium and ammonium** are soluble.\n- **Chlorides** are soluble except **silver chloride** and lead(II) chloride.\n- **Sulphates** are soluble except **barium sulphate** and lead(II) sulphate (calcium sulphate is slightly soluble).\n- **Carbonates** and **hydroxides** are insoluble except those of sodium, potassium and ammonium.\n\nThe rules decide the method: a soluble salt is made by **crystallisation**, an insoluble salt by **precipitation**."),
       ("fig", salt_fl, "Making a soluble salt from an insoluble base, metal or carbonate.", "Four boxes joined by arrows: warm dilute acid, add excess insoluble base, filter off the excess solid, evaporate then cool and crystallise."),
       ("key", "methode", "Soluble salts",
        "**From an insoluble base, metal or carbonate**: add the solid in excess to warm dilute acid until no more reacts; filter; heat the filtrate to the point of crystallisation; cool; filter and dry the crystals. Example: " + E("CuO + H2SO4 -> CuSO4 + H2O") + " (blue copper(II) sulphate crystals).\n\n**From an alkali** (all soluble): use a **titration** to find the exact volumes, then repeat without the indicator and crystallise. Example: " + E("NaOH + HCl -> NaCl + H2O") + "."),
       ("key", "methode", "Insoluble salts and ion tests",
        "**Precipitation**: mix two solutions that contain the ions: " + E("BaCl2 + Na2SO4 -> BaSO4 + 2NaCl") + " (white precipitate), " + E("Pb(NO3)2 + 2KI -> PbI2 + 2KNO3") + " (yellow). Filter, wash with distilled water, dry.\n\n**Tests**: **chloride**: add dilute nitric acid then silver nitrate: **white precipitate**. **Sulphate**: add dilute HCl then barium chloride: **white precipitate**. **Carbonate**: add dilute acid: **bubbles of CO₂** that turn limewater milky."),
       ("key", "pieges", "Common mistakes",
        "- Do not evaporate to dryness: the crystals would form a powder and may decompose; stop at the crystallisation point.\n- An excess of the insoluble solid is used so that all the acid reacts; the excess is then filtered off.\n- You cannot make a soluble salt from an insoluble one by filtering alone."),
       ("example", "Plan a preparation", "Describe how to make zinc sulphate crystals from zinc and dilute sulphuric acid.", ["Add excess zinc to warm dilute sulphuric acid until fizzing stops (" + E("Zn + H2SO4 -> ZnSO4 + H2") + "), then filter off the unreacted zinc.", "Heat the filtrate to the crystallisation point, cool, filter the crystals and dry them."], "Excess zinc + acid, filter, crystallise."),
       ("example", "Choose a method", "Which method makes silver chloride: crystallisation or precipitation? Give the reagents.", ["Silver chloride is an insoluble chloride, so use precipitation.", "Mix silver nitrate solution with sodium chloride solution: " + E("AgNO3 + NaCl -> AgCl + NaNO3") + "."], "Precipitation: AgNO₃ + NaCl")],
      [("mcq", "Which salt is insoluble in water?", "barium sulphate", ["sodium chloride", "potassium nitrate", "ammonium chloride"], "Barium sulphate is the insoluble sulphate."),
       ("match", "Match each salt with a method of preparation.", [("copper(II) sulphate from copper(II) oxide", "excess solid added to acid, then crystallisation"), ("barium sulphate", "precipitation"), ("sodium chloride from sodium hydroxide", "titration, then crystallisation"), ("zinc sulphate from zinc", "excess metal added to acid, then crystallisation")], "Choose by solubility and reactants."),
       ("mcq", "A white precipitate forms when barium chloride is added to a solution acidified with dilute HCl. The solution contains:", "sulphate ions", ["chloride ions", "carbonate ions", "nitrate ions"], "Barium sulphate."),
       ("tf", "All nitrates are soluble in water.", True, "Standard solubility rule."),
       ("open", "Describe how to make a pure dry sample of lead(II) iodide.", "Mix solutions of lead(II) nitrate and potassium iodide; a yellow precipitate forms; filter, wash with distilled water, and dry in a warm oven.", ["1 mark: mix the two solutions", "1 mark: filter and wash", "1 mark: dry"]),
       ("prob", "A student wants crystals of copper(II) sulphate using copper(II) oxide.", [("mcq", "The acid to use is:", "dilute sulphuric acid", ["dilute hydrochloric acid", "dilute nitric acid", "ethanoic acid"], "The sulphate comes from sulphuric acid."), ("mcq", "Why is the copper(II) oxide added in excess?", "to make sure all the acid reacts", ["to make the crystals blue", "to speed up filtering", "to cool the mixture"], "Then no acid is left in the salt."), ("open", "Write the balanced equation.", E("CuO + H2SO4 -> CuSO4 + H2O"), ["1 mark: correct formulae", "1 mark: balanced"], 2)])],
      [("A soluble salt can be separated from its solution by:", "crystallisation", ["filtration", "chromatography", "magnetism"], "Evaporate then cool."),
       ("The test for a chloride ion uses:", "silver nitrate", ["limewater", "litmus", "iodine"], "White silver chloride."),
       ("Which of these salts is soluble?", "sodium carbonate", ["calcium carbonate", "lead(II) iodide", "barium sulphate"], "Group 1 carbonates are soluble."),
       ("A precipitation reaction makes:", "an insoluble salt", ["a soluble salt only", "a gas only", "a metal"], "Solid forms in solution."),
       ("In making copper sulphate from copper oxide the excess solid is removed by:", "filtration", ["distillation", "chromatography", "evaporation"], "Filter the mixture.")],
      ["Solubility rules are the standard school summary (CaSO₄ slightly soluble); check the exact list the syllabus uses. Practicals with hot acid and silver/barium/lead compounds are teacher-led; lead and barium compounds are toxic."])

# reactivity ladder
def ladder_fig():
    d = Draw()
    seq = [("potassium K", "red"), ("sodium Na", "red"), ("calcium Ca", "orange"), ("magnesium Mg", "orange"), ("aluminium Al", "yellow"), ("carbon C (not a metal)", "lightgrey"), ("zinc Zn", "yellow"), ("iron Fe", "yellow"),
           ("lead Pb", "lightgreen"), ("hydrogen H (not a metal)", "lightgrey"), ("copper Cu", "lightblue"), ("silver Ag", "lightblue"), ("gold Au", "lightblue")]
    for i, (n, c) in enumerate(seq):
        d.sh.append(RECT(30, 8 + i * 23, 170, 21, fill=c, width=1.2)); d.text(115, 8 + i * 23 + 15, n, size=11)
    d.line(15, 295, 15, 12, arrow="end", width=2)
    d.text(215, 22, "reacts with cold water", size=11, anchor="start"); d.text(215, 90, "reacts with steam / dilute acid", size=11, anchor="start")
    d.text(215, 170, "reacts slowly with dilute acid", size=11, anchor="start"); d.text(215, 270, "no reaction with dilute acid", size=11, anchor="start")
    d.text(215, 130, "more reactive at the top", size=11, anchor="start", bold=True)
    return d.fig(440, 312)
assert E("2Na + 2H2O -> 2NaOH + H2") and E("Mg + CuSO4 -> MgSO4 + Cu") and E("Fe + CuSO4 -> FeSO4 + Cu") and E("Cu + 2AgNO3 -> Cu(NO3)2 + 2Ag") and E("2Al + Fe2O3 -> Al2O3 + 2Fe") and E("Ca + 2H2O -> Ca(OH)2 + H2") and E("Zn + FeSO4 -> ZnSO4 + Fe")
build(ch, "reactivity", "The reactivity series and displacement", 30,
      ["List the metals in order of reactivity and describe their reactions with water, steam and dilute acid.", "Predict displacement reactions between metals and solutions of metal salts.", "Use the series to explain how metals are extracted and why some metals are stored under oil."],
      [("key", "retenir", "The series",
        "In order of **decreasing reactivity**: **K, Na, Ca, Mg, Al, (C), Zn, Fe, Pb, (H), Cu, Ag, Au**. Mnemonic: *Please Stop Calling Me A Careless Zebra; Instead Try Learning How Copper Saves Gold.* Carbon and hydrogen are non-metals placed for comparison. Reactivity is the tendency to lose electrons and form positive ions."),
       ("fig", ladder_fig(), "The reactivity series with typical reactions.", "A vertical list of metals from potassium at the top to gold at the bottom with notes: the top ones react with cold water, the middle ones with steam or dilute acid, and copper, silver and gold do not react with dilute acid."),
       ("key", "retenir", "Reactions with water and acid",
        "**K, Na, Ca** react with cold water giving hydrogen and a hydroxide: " + E("2Na + 2H2O -> 2NaOH + H2") + " and " + E("Ca + 2H2O -> Ca(OH)2 + H2") + ". Magnesium reacts very slowly with cold water but burns in steam. **Mg, Zn, Fe** react with dilute acid giving hydrogen, quickly for Mg, slowly for Fe. **Cu, Ag, Au** do not react with dilute acid or water. Sodium and potassium are stored under oil."),
       ("key", "methode", "Displacement",
        "A **more reactive metal displaces a less reactive metal** from a solution of its salt: " + E("Mg + CuSO4 -> MgSO4 + Cu") + "; " + E("Fe + CuSO4 -> FeSO4 + Cu") + " (an iron nail gets a brown-red copper coat and the blue colour fades); " + E("Cu + 2AgNO3 -> Cu(NO3)2 + 2Ag") + ". The same idea explains the **thermite** reaction: " + E("2Al + Fe2O3 -> Al2O3 + 2Fe") + ". No reaction occurs if the added metal is **less** reactive."),
       ("key", "pieges", "Common mistakes",
        "- Reactivity is not the same as hardness or density.\n- A displacement needs the free metal to be **higher** in the series than the metal in the salt.\n- Copper does not displace zinc from zinc sulphate."),
       ("example", "Will it react?", "Predict whether zinc reacts with copper(II) sulphate solution, and whether copper reacts with zinc sulphate solution.", ["Zinc is above copper: it displaces copper: Zn + CuSO₄ → ZnSO₄ + Cu.", "Copper is below zinc: no reaction."], "Yes for zinc; no for copper."),
       ("example", "Put in order", "Metal X reacts quickly with dilute acid and displaces Y from Y sulphate; metal Z does not react with acid. Give the order of reactivity.", ["X displaces Y, so X is more reactive than Y.", "Z does not react with acid so it is the least reactive: X > Y > Z."], "X > Y > Z")],
      [("mcq", "Which metal is stored under oil because it reacts with water and air?", "sodium", ["copper", "gold", "iron"], "Sodium is very reactive."),
       ("mcq", "Which metal will displace copper from copper(II) sulphate?", "zinc", ["silver", "gold", "mercury"], "Only metals above copper."),
       ("tf", "Copper reacts with dilute hydrochloric acid to give hydrogen.", False, "Copper is below hydrogen in the series."),
       ("mcq", "The gas formed when magnesium reacts with dilute hydrochloric acid is:", "hydrogen", ["oxygen", "carbon dioxide", "chlorine"], "A metal above hydrogen displaces it."),
       ("open", "An iron nail is placed in blue copper sulphate solution. Describe two observations and explain.", "The nail becomes coated with a brown-red layer of copper and the blue colour of the solution fades. Iron is more reactive than copper and displaces it: Fe + CuSO₄ → FeSO₄ + Cu.", ["1 mark: brown coat on the nail", "1 mark: blue colour fades", "1 mark: iron displaces copper"]),
       ("prob", "Four metals W, X, Y, Z are tested with dilute hydrochloric acid and with salt solutions. W fizzes vigorously; X fizzes slowly; Y does not fizz and Y displaces Z from its salt; Z does not fizz.", [("mcq", "The most reactive is:", "W", ["X", "Y", "Z"], "Most vigorous with acid."), ("mcq", "The order from most to least reactive is:", "W, X, Y, Z", ["Z, Y, X, W", "X, W, Y, Z", "W, X, Z, Y"], "Y displaces Z so Y is more reactive than Z."), ("open", "Suggest one real metal for each of W and Z.", "W could be magnesium (or zinc) and Z could be silver (or gold).", ["1 mark: sensible W", "1 mark: sensible Z"], 2)])],
      [("The most reactive metal in the list is:", "potassium", ["gold", "copper", "iron"], "Top of the series."),
       ("The least reactive is:", "gold", ["sodium", "zinc", "lead"], "Bottom of the series."),
       ("A more reactive metal can:", "displace a less reactive metal from its salt solution", ["never react", "turn into a gas", "become a non-metal"], "Displacement."),
       ("Hydrogen is produced when a metal above hydrogen reacts with:", "dilute acid", ["sand", "ethanol", "oil"], "Metal + acid → salt + hydrogen."),
       ("Which of these metals gives no gas with dilute hydrochloric acid?", "silver", ["magnesium", "zinc", "iron"], "Below hydrogen.")],
      ["The mnemonic and the positions of Pb and H are the common O Level ones; check whether tin is included. Reactions of K and Na are teacher demonstrations only."])

fe_ore = 160; fe = 112; fe_pc = fe / fe_ore * 100; assert abs(fe_pc - 70) < 1e-9 and Mr("Fe2O3") == 160
assert E("Fe2O3 + 3CO -> 2Fe + 3CO2") and E("C + O2 -> CO2") and E("CO2 + C -> 2CO") and E("CaCO3 -> CaO + CO2") and E("CaO + SiO2 -> CaSiO3")
def furnace_fig():
    d = Draw()
    d.sh.append(POLY([(160, 40), (260, 40), (290, 140), (260, 200), (160, 200), (130, 140)], fill="lightorange", width=2))
    d.line(190, 12, 190, 38, arrow="end", width=2); d.text(190, 10, "iron ore, coke, limestone", size=11, anchor="start")
    d.line(70, 170, 130, 170, arrow="end", color="blue", width=2); d.text(66, 188, "hot air", size=11, anchor="end")
    d.line(260, 100, 340, 100, arrow="end", color="grey", width=2); d.text(345, 104, "waste gases", size=11, anchor="start")
    d.line(260, 192, 330, 192, arrow="end", color="red", width=2); d.text(335, 196, "molten iron", size=11, anchor="start")
    d.line(260, 176, 330, 160, arrow="end", color="brown", width=2); d.text(335, 160, "slag", size=11, anchor="start")
    d.text(210, 125, "Fe₂O₃ + CO → Fe", size=11)
    return d.fig(440, 210)
build(ch, "extraction", "Extraction of metals and uses", 30,
      ["Explain why the method of extraction depends on the position of the metal in the reactivity series.", "Describe the extraction of iron in the blast furnace and of aluminium by electrolysis.", "State uses of metals and alloys and the value of recycling."],
      [("key", "definition", "Ores and extraction",
        "An **ore** is a rock containing enough of a metal compound to be worth extracting. The method depends on reactivity: metals **below carbon** (Zn, Fe, Pb, Cu) are obtained by **reducing the oxide with carbon or carbon monoxide**; metals **above carbon** (K, Na, Ca, Mg, Al) are too reactive and are extracted by **electrolysis of the molten compound**; unreactive metals such as **gold** are found **native** (uncombined)."),
       ("fig", furnace_fig(), "The blast furnace for iron (simplified).", "A furnace with iron ore, coke and limestone going in at the top, hot air entering near the bottom, waste gases leaving near the top, and molten iron and slag leaving at the bottom."),
       ("key", "methode", "Iron in the blast furnace",
        "Coke burns in hot air: " + E("C + O2 -> CO2") + "; then " + E("CO2 + C -> 2CO") + ". Carbon monoxide **reduces** the iron(III) oxide: " + E("Fe2O3 + 3CO -> 2Fe + 3CO2") + ". **Limestone** decomposes, " + E("CaCO3 -> CaO + CO2") + ", and removes sand: " + E("CaO + SiO2 -> CaSiO3") + " (**slag**, which floats on the molten iron). Iron is tapped from the bottom."),
       ("key", "retenir", "Aluminium and uses",
        "Aluminium is extracted by **electrolysis of aluminium oxide** (from **bauxite**) dissolved in molten **cryolite**, which lowers the working temperature; aluminium forms at the **negative** electrode. It needs a lot of electricity. **Uses**: aluminium (low density, does not corrode because of a thin oxide layer: aircraft, cans, cables); steel (iron with a little carbon: buildings, vehicles); copper (wires, pipes: good conductor); zinc (galvanising). **Recycling** saves ore and energy and reduces waste."),
       ("key", "pieges", "Common mistakes",
        "- Carbon cannot extract aluminium because aluminium is more reactive than carbon.\n- Iron is **reduced** (it loses oxygen); carbon monoxide is **oxidised**.\n- Aluminium does not corrode quickly although it is reactive: its oxide layer protects it."),
       ("example", "Mass of iron", "Iron(III) oxide, Fe₂O₃ (relative formula mass 160), contains 2 × 56 = 112 of iron. How much iron could be obtained from 1000 kg of pure Fe₂O₃?", ["Fraction of iron = 112 ÷ 160 = 0.70.", "Mass of iron = 0.70 × 1000 = 700 kg."], "700 kg"),
       ("example", "Choose the method", "State the method of extraction for (a) lead and (b) magnesium and explain.", ["Lead is below carbon in the series: heat its oxide with carbon.", "Magnesium is above carbon: electrolysis of the molten compound."], "(a) reduction with carbon; (b) electrolysis")],
      [("mcq", "Which metal is extracted by electrolysis of its molten oxide?", "aluminium", ["iron", "copper", "lead"], "It is above carbon in the series."),
       ("mcq", "In the blast furnace the iron(III) oxide is reduced by:", "carbon monoxide", ["limestone", "hot air only", "slag"], "CO takes the oxygen."),
       ("num", "What mass of iron is in 400 kg of pure Fe₂O₃? (A_r: Fe = 56, O = 16)", 280, "400 × 112 ÷ 160 = 280 kg.", 0.5, "kg"),
       ("tf", "Limestone is added to the blast furnace to remove silica as slag.", True, "CaO + SiO₂ → CaSiO₃."),
       ("open", "Explain why aluminium cannot be extracted by heating its oxide with carbon but copper can.", "Aluminium is more reactive than carbon so carbon cannot take oxygen from aluminium oxide; copper is less reactive than carbon so carbon can reduce copper oxide.", ["1 mark: Al above carbon", "1 mark: Cu below carbon"]),
       ("prob", "A blast furnace produces iron from haematite (Fe₂O₃) using coke and limestone.", [("mcq", "The reducing agent is:", "carbon monoxide", ["oxygen", "iron", "slag"], "CO is made from the coke."), ("open", "Write the equation for the reduction of iron(III) oxide by carbon monoxide.", E("Fe2O3 + 3CO -> 2Fe + 3CO2"), ["1 mark: formulae", "1 mark: balanced"], 2), ("num", "Mass of iron from 320 kg of pure Fe₂O₃ (kg).", 224, "320 × 112 ÷ 160 = 224 kg.", 0.5, "kg")])],
      [("An ore is:", "a rock containing a metal compound worth extracting", ["a pure metal", "a type of acid", "a gas"], "Definition."),
       ("Metals above carbon are extracted by:", "electrolysis", ["heating alone", "filtration", "evaporation"], "Too reactive for carbon."),
       ("Steel is an alloy of iron with a little:", "carbon", ["lead", "sulphur", "gold"], "Hardens the iron."),
       ("Cryolite is used in extracting aluminium to:", "lower the working temperature of the electrolyte", ["colour the aluminium", "add oxygen", "cool the electrodes"], "It dissolves the aluminium oxide at a lower temperature."),
       ("Slag in the blast furnace is:", "calcium silicate", ["pure iron", "carbon", "copper"], "CaSiO₃.")],
      ["Industrial processes are simplified; check whether the syllabus requires the electrode equations for aluminium. Cameroon context (bauxite deposits and the aluminium smelter at Edéa) is deliberately not stated as a fact in the lesson; add it only after confirmation."])

# =========================================================== 2 The mole and reacting quantities
ch = p.chapter("mole", "The mole and calculations", "Form 4 Chemistry — Relative masses, the mole, reacting masses and volumes (to be checked)")
def tri_fig():
    d = Draw()
    d.sh.append(POLY([(200, 20), (110, 160), (290, 160)], fill="lightyellow", width=2))
    d.line(155, 90, 245, 90, width=2); d.line(200, 90, 200, 160, width=2)
    d.text(200, 70, "m", size=20, bold=True); d.text(160, 135, "n", size=20, bold=True); d.text(242, 135, "M", size=20, bold=True)
    d.text(200, 188, "m = n × M     n = m ÷ M     M = m ÷ n", size=12)
    d.text(200, 208, "m: mass in g,  n: moles,  M: molar mass in g/mol", size=11)
    return d.fig(400, 220)
mr_h2so4 = Mr("H2SO4"); assert mr_h2so4 == 98 and abs(4.9 / 98 - 0.05) < 1e-12
assert Mr("CaCO3") == 100 and 0.25 * 100 == 25 and abs(0.5 * 6.0e23 - 3.0e23) < 1
assert abs(Mr("NH4NO3") - 80) < 1e-9 and abs(28 / 80 * 100 - 35) < 1e-9
assert abs(2 * 14 / Mr("CO(NH2)2") * 100 - 46.67) < 0.01 and Mr("CO(NH2)2") == 60
assert abs(2.4 / 24 - 0.1) < 1e-12 and abs(1.6 / 16 - 0.1) < 1e-12
assert abs(2 / 18 * 100 - 11.11) < 0.01
build(ch, "mole-basics", "Relative masses and the mole", 30,
      ["Calculate relative formula masses.", "Use n = m ÷ M to convert between mass and amount in moles.", "Calculate percentage composition and find an empirical formula."],
      [("key", "definition", "Relative masses and the mole",
        "Relative atomic masses (A_r): H 1, C 12, N 14, O 16, Na 23, Mg 24, Al 27, S 32, Cl 35.5, K 39, Ca 40, Fe 56, Cu 64, Zn 65. The **relative formula mass (M_r)** is the sum of the A_r values: M_r(H₂SO₄) = 2 + 32 + 64 = **98**. The **mole** is the amount of substance containing **6.0 × 10²³** particles (the Avogadro constant, more exactly 6.02 × 10²³). The **molar mass M** is the mass of one mole in **g/mol** and equals the M_r (or A_r) in grams."),
       ("fig", tri_fig(), "The mole triangle: cover the quantity you want.", "A triangle divided into three parts with m at the top and n and M at the bottom, with the three formulae m = n × M, n = m ÷ M and M = m ÷ n."),
       ("key", "formule", "Calculations",
        "**n = m ÷ M**, **m = n × M**; number of particles = **n × 6.0 × 10²³**. **Percentage by mass of an element** = (A_r × number of atoms ÷ M_r) × 100. **Empirical formula** (simplest ratio of atoms): (1) write the masses or percentages; (2) divide each by its A_r; (3) divide by the smallest; (4) round to whole numbers. Example: 2.4 g Mg and 1.6 g O → 0.1 mol and 0.1 mol → ratio 1 : 1 → **MgO**."),
       ("key", "pieges", "Common mistakes",
        "- Use the **M_r of the whole formula**: for CaCO₃ it is 40 + 12 + 48 = 100.\n- A bracket multiplies: M_r of Ca(OH)₂ is 40 + 2 × (16 + 1) = 74.\n- The empirical formula is a ratio, not the true number of atoms: the molecular formula may be a multiple of it (CH₂ for C₂H₄)."),
       ("example", "Moles in a mass", "How many moles are in 4.9 g of sulphuric acid, H₂SO₄ (M = 98 g/mol)?", ["n = m ÷ M.", "4.9 ÷ 98 = 0.05 mol."], "0.05 mol"),
       ("example", "Percentage of nitrogen", "Find the percentage of nitrogen in ammonium nitrate, NH₄NO₃ (M_r = 80).", ["There are 2 N atoms: 2 × 14 = 28.", "28 ÷ 80 × 100 = 35 %."], "35 %")],
      [("num", "Find the relative formula mass of calcium carbonate, CaCO₃.", 100, "40 + 12 + 3 × 16 = 100.", 0),
       ("num", "How many moles are in 25 g of CaCO₃?", 0.25, "25 ÷ 100 = 0.25 mol.", 0.001, "mol"),
       ("num", "What is the mass of 0.5 mol of water, H₂O? (A_r: H = 1, O = 16)", 9, "M = 18 g/mol; 0.5 × 18 = 9 g.", 0, "g"),
       ("num", "How many molecules are there in 0.5 mol of a substance? Give your answer as a number multiplied by 10²³ (answer the number only, with 6.0 × 10²³ per mole).", 3.0, "0.5 × 6.0 = 3.0 (× 10²³).", 0.01),
       ("num", "Find the percentage of oxygen in water by mass (A_r: H = 1, O = 16), to 1 d.p.", 88.9, "16 ÷ 18 × 100 = 88.9 %.", 0.1, "%"),
       ("prob", "A fertiliser called urea has the formula CO(NH₂)₂. (A_r: C = 12, O = 16, N = 14, H = 1)", [("num", "Relative formula mass of urea.", 60, "12 + 16 + 2 × (14 + 2) = 60.", 0), ("num", "Percentage of nitrogen in urea (1 d.p.).", 46.7, "28 ÷ 60 × 100 = 46.7 %.", 0.1, "%"), ("mcq", "Compared with ammonium nitrate (35 % N), urea supplies:", "more nitrogen per kilogram", ["less nitrogen per kilogram", "the same nitrogen per kilogram", "no nitrogen"], "46.7 % is higher than 35 %.")])],
      [("The mole is the amount of substance containing:", "6.0 × 10²³ particles", ["6.0 × 10²² particles", "1000 particles", "1 gram"], "Avogadro constant."),
       ("The formula linking moles and mass is:", "n = m ÷ M", ["n = m × M", "n = m − M", "n = m + M"], "Moles = mass ÷ molar mass."),
       ("The M_r of NaCl is:", "58.5", ["35.5", "23", "58"], "23 + 35.5."),
       ("An empirical formula shows:", "the simplest whole-number ratio of atoms", ["the number of molecules", "the colour", "the mass of the sample"], "Simplest ratio."),
       ("How many grams are in 2 mol of NaOH (M = 40 g/mol)?", "80 g", ["20 g", "42 g", "8 g"], "2 × 40.")],
      ["A_r values are the usual school ones (Cl = 35.5, Cu = 64). The Avogadro constant is given as 6.0 × 10²³ for calculations; check if 6.02 is expected."])

tit_fig = Draw()
tit_fig.sh.append(RECT(140, 10, 20, 150, fill="white", width=2))
for k in range(9): tit_fig.line(140, 20 + k * 16, 150, 20 + k * 16, width=1)
tit_fig.sh.append(RECT(141, 20, 18, 60, fill="lightblue", stroke="none", width=0))
tit_fig.sh.append(RECT(136, 162, 28, 8, fill="grey", width=1.5)); tit_fig.line(150, 170, 150, 190, width=2, color="blue")
tit_fig.sh.append(RECT(90, 250, 120, 6, fill="white", width=2))
tit_fig.sh.append(POLY([(135, 205), (135, 220), (110, 250), (190, 250), (165, 220), (165, 205)], closed=False, width=2))
tit_fig.sh.append(POLY([(122, 235), (110, 250), (190, 250), (178, 235)], fill="lightorange", width=1.5))
tit_fig.sh += lab(160, 40, 250, 30, "burette (acid)", size=11)
tit_fig.sh += lab(165, 235, 250, 230, "conical flask: alkali + indicator", size=11)
tit_fig.sh += lab(200, 253, 250, 268, "white tile", size=11)
tit_fig.text(60, 292, "start reading 0.0 cm³, end reading 20.0 cm³", size=11, anchor="start")
tit_fig_ = tit_fig.fig(440, 305)
n_naoh = 0.100 * 25.0 / 1000; c_hcl = n_naoh / (20.0 / 1000); assert abs(n_naoh - 0.0025) < 1e-12 and abs(c_hcl - 0.125) < 1e-12
assert abs(4.8 / 24 * 40 - 8.0) < 1e-9 and abs(0.5 * 56 - 28) < 1e-9 and 0.5 * 24 == 12 and abs(6.5 / 65 * 24 - 2.4) < 1e-9 and abs(6.4 / 8.0 * 100 - 80) < 1e-9
assert abs(0.125 * 36.5 - 4.5625) < 1e-9 and Mr("CaO") == 56 and Mr("MgO") == 40
assert E("2Mg + O2 -> 2MgO") and E("CaCO3 -> CaO + CO2") and E("Zn + 2HCl -> ZnCl2 + H2") and E("NaOH + HCl -> NaCl + H2O")
build(ch, "reacting-quantities", "Reacting masses, gas volumes and concentration", 35,
      ["Use balanced equations to calculate reacting masses and gas volumes.", "Calculate concentration and carry out a titration calculation.", "Calculate percentage yield."],
      [("key", "methode", "Mass and volume calculations",
        "1. Write the **balanced equation**.\n2. Convert the known mass to **moles** (n = m ÷ M).\n3. Use the **mole ratio** from the equation.\n4. Convert the moles of the wanted substance to **mass** (n × M) or **gas volume** (n × 24 dm³ at room temperature and pressure; the molar volume of a gas is **24 dm³/mol** at r.t.p.)."),
       ("key", "formule", "Concentration and titration",
        "**Concentration (mol/dm³) c = n ÷ V** (V in dm³; 1 dm³ = 1000 cm³), so **n = c × V**. Concentration in g/dm³ = c × M. In a **titration** a **burette** adds acid to a measured volume of alkali in a flask with an indicator until it just changes colour (the **end-point**, read to 0.05 cm³); repeat until two results agree within 0.10 cm³ (**concordant**)."),
       ("fig", tit_fig_, "A titration set-up.", "A burette of acid above a conical flask containing alkali and indicator standing on a white tile, with the note start reading 0.0 cm³ and end reading 20.0 cm³."),
       ("key", "formule", "Percentage yield",
        "**Percentage yield = (actual mass obtained ÷ theoretical mass) × 100**. The yield is below 100 % because of incomplete reactions, losses when filtering and transferring and side reactions."),
       ("key", "pieges", "Common mistakes",
        "- Convert cm³ to dm³ **before** using c = n ÷ V: 25 cm³ = 0.025 dm³.\n- Use the **mole ratio**, not the mass ratio, from the equation.\n- Use 24 dm³/mol only for gases at room temperature and pressure."),
       ("example", "Reacting mass", "4.8 g of magnesium burns completely in oxygen. Find the mass of magnesium oxide formed (2Mg + O₂ → 2MgO; A_r: Mg 24, O 16).", ["n(Mg) = 4.8 ÷ 24 = 0.20 mol; ratio Mg : MgO = 1 : 1, so 0.20 mol MgO.", "m = 0.20 × 40 = 8.0 g."], "8.0 g"),
       ("example", "Titration", "25.0 cm³ of 0.100 mol/dm³ NaOH needs 20.0 cm³ of HCl for neutralisation. Find the concentration of the acid (NaOH + HCl → NaCl + H₂O).", ["n(NaOH) = 0.100 × 0.0250 = 0.00250 mol = n(HCl) (1 : 1).", "c(HCl) = 0.00250 ÷ 0.0200 = 0.125 mol/dm³."], "0.125 mol/dm³")],
      [("num", "Zinc (6.5 g) reacts with excess dilute HCl: Zn + 2HCl → ZnCl₂ + H₂. Find the volume of hydrogen at r.t.p. in dm³ (A_r Zn = 65; 24 dm³/mol).", 2.4, "n(Zn) = 0.10 mol; n(H₂) = 0.10 mol; V = 0.10 × 24 = 2.4 dm³.", 0.05, "dm³"),
       ("num", "Heating 50 g of calcium carbonate gives calcium oxide and carbon dioxide. Find the mass of calcium oxide in g (M of CaCO₃ = 100, CaO = 56).", 28, "n(CaCO₃) = 0.50 mol; m(CaO) = 0.50 × 56 = 28 g.", 0.1, "g"),
       ("num", "Find the concentration in mol/dm³ of a solution containing 0.50 mol in 250 cm³.", 2.0, "250 cm³ = 0.250 dm³; 0.50 ÷ 0.250 = 2.0 mol/dm³.", 0.01, "mol/dm³"),
       ("num", "A reaction should give 8.0 g of product (theoretical) but only 6.4 g is obtained. Find the percentage yield.", 80, "6.4 ÷ 8.0 × 100 = 80 %.", 0, "%"),
       ("num", "Calculate the volume, in dm³ at r.t.p., of 0.50 mol of carbon dioxide (24 dm³/mol).", 12, "0.50 × 24 = 12 dm³.", 0, "dm³"),
       ("prob", "In a titration, 20.0 cm³ of sodium hydroxide of unknown concentration is neutralised by 25.0 cm³ of 0.200 mol/dm³ hydrochloric acid (NaOH + HCl → NaCl + H₂O).", [("num", "Moles of HCl used (mol).", 0.005, "0.200 × 0.0250 = 0.00500 mol.", 0.0001, "mol"), ("num", "Moles of NaOH in the flask (mol).", 0.005, "Ratio 1 : 1, so 0.00500 mol.", 0.0001, "mol"), ("num", "Concentration of the NaOH (mol/dm³).", 0.25, "0.00500 ÷ 0.0200 = 0.250 mol/dm³.", 0.001, "mol/dm³")])],
      [("The molar volume of a gas at r.t.p. is about:", "24 dm³/mol", ["22 cm³/mol", "100 dm³/mol", "1 dm³/mol"], "Used in this course."),
       ("Concentration in mol/dm³ is calculated by:", "moles ÷ volume in dm³", ["moles × volume", "mass ÷ moles", "volume ÷ moles"], "c = n/V."),
       ("The end-point of a titration is when:", "the indicator just changes colour", ["the burette is empty", "the flask is full", "the acid stops dissolving"], "Neutralisation point."),
       ("Percentage yield equals:", "actual ÷ theoretical × 100", ["theoretical ÷ actual × 100", "actual + theoretical", "actual × theoretical"], "Definition."),
       ("In 2Mg + O₂ → 2MgO, 2 mol of Mg gives how many mol of MgO?", "2", ["1", "4", "0.5"], "Ratio 1 : 1.")],
      ["Molar volume 24 dm³/mol at r.t.p. (some syllabuses use 22.4 dm³ at s.t.p.); check which one the syllabus uses. All numerical answers were recomputed in the generator with assertions."])

# =========================================================== 3 Energy and rates
ch = p.chapter("energy-rates", "Energy changes and rates of reaction", "Form 4 Chemistry — Exothermic and endothermic reactions, rates (to be checked)")
def level_fig():
    d = Draw()
    d.line(40, 190, 40, 20, arrow="end", width=1.5); d.text(50, 18, "energy", size=11, anchor="start")
    d.line(60, 60, 150, 60, width=3); d.line(210, 130, 300, 130, width=3)
    d.line(150, 62, 210, 128, arrow="end", color="red", width=2)
    d.text(105, 50, "reactants", size=11); d.text(255, 150, "products", size=11)
    d.text(180, 100, "energy released", size=11, anchor="start", color="red")
    d.text(180, 185, "exothermic: ΔH negative", size=12, bold=True)
    d.line(340, 130, 400, 130, width=3); d.line(440, 60, 470, 60, width=3)
    d.line(400, 132, 440, 62, arrow="end", color="blue", width=2)
    d.text(370, 150, "reactants", size=11); d.text(455, 50, "products", size=11)
    return d.fig(480, 200)
dT = 6.5; Qr = 100 * 4.2 * dT; assert abs(Qr - 2730) < 1e-9
build(ch, "energy-changes", "Energy changes in reactions", 25,
      ["Distinguish exothermic from endothermic reactions with examples.", "Explain energy changes in terms of bond breaking and bond making.", "Calculate heat energy from a temperature change."],
      [("key", "definition", "Exothermic and endothermic",
        "An **exothermic** reaction **gives out** heat to the surroundings: the temperature **rises** (combustion, neutralisation, respiration, rusting, many oxidations). An **endothermic** reaction **takes in** heat: the temperature **falls** (dissolving ammonium nitrate, thermal decomposition of calcium carbonate, photosynthesis). Energy is **not lost**; it moves between the chemicals and the surroundings."),
       ("fig", level_fig(), "Energy level diagrams: an exothermic change (left) and an endothermic change (right, schematic).", "Two diagrams: on the left the products are lower than the reactants with an arrow down for energy released; on the right the products are higher than the reactants with an arrow up."),
       ("key", "retenir", "Bonds and energy",
        "Breaking bonds **takes in** energy; making bonds **releases** energy. If more energy is released making the new bonds than is needed to break the old ones, the reaction is **exothermic** (ΔH negative); if not, it is **endothermic** (ΔH positive). **Fuels** are chosen for the energy they release per kilogram and for safe, clean burning."),
       ("key", "formule", "Heat from a temperature change",
        "For solutions, heat energy **Q = m × c × ΔT**, using c = 4.2 J/(g °C) for water and assuming 1 cm³ of solution has a mass of 1 g. Example: 100 cm³ of solution rising by 6.5 °C gains 100 × 4.2 × 6.5 = **2730 J**. Heat losses to the air and the cup make the measured value too low; a lid and an insulated cup reduce this."),
       ("key", "pieges", "Common mistakes",
        "- Exothermic does not mean the reaction is fast, and endothermic does not mean it is slow.\n- Bond breaking is endothermic, bond making is exothermic.\n- ΔT is a temperature change, not the final temperature."),
       ("example", "Classify", "When ammonium nitrate dissolves in water the beaker feels cold. Is this exothermic or endothermic?", ["The temperature of the surroundings falls.", "Energy has been taken in from the surroundings: endothermic."], "Endothermic"),
       ("example", "Heat released", "50 cm³ of acid and 50 cm³ of alkali are mixed and the temperature rises by 6.5 °C. Find the heat released (c = 4.2 J/g °C, 1 cm³ = 1 g).", ["Mass = 50 + 50 = 100 g.", "Q = 100 × 4.2 × 6.5 = 2730 J."], "2730 J")],
      [("mcq", "A reaction in which the temperature of the mixture rises is:", "exothermic", ["endothermic", "not a reaction", "a physical change only"], "Heat is given out."),
       ("mcq", "Which process is endothermic?", "dissolving ammonium nitrate in water", ["burning charcoal", "neutralisation", "respiration"], "The solution gets cold."),
       ("num", "100 g of solution rises in temperature by 5.0 °C. Find the heat gained in J (c = 4.2 J/g °C).", 2100, "100 × 4.2 × 5.0 = 2100 J.", 0, "J"),
       ("tf", "In an exothermic reaction more energy is released making bonds than is taken in breaking bonds.", True, "Net energy is released."),
       ("open", "Explain why a polystyrene cup with a lid gives a better measurement of heat released than an open glass beaker.", "The cup is a poor conductor and the lid reduces losses by evaporation and convection, so less heat escapes and the measured temperature rise is closer to the true value.", ["1 mark: insulation", "1 mark: fewer heat losses to the surroundings"]),
       ("prob", "A student mixes 25 cm³ of hydrochloric acid with 25 cm³ of sodium hydroxide solution. The temperature rises from 24.0 °C to 30.5 °C.", [("num", "Temperature rise (°C).", 6.5, "30.5 − 24.0 = 6.5 °C.", 0.01, "°C"), ("num", "Heat released in J (50 g, c = 4.2 J/g °C).", 1365, "50 × 4.2 × 6.5 = 1365 J.", 1, "J"), ("mcq", "The reaction is:", "exothermic", ["endothermic", "reversible only", "a precipitation"], "Temperature rose.")])],
      [("An endothermic reaction:", "takes in heat from the surroundings", ["gives out heat", "has no energy change", "always explodes"], "Temperature falls."),
       ("Which is exothermic?", "burning a fuel", ["melting ice", "photosynthesis", "dissolving ammonium nitrate"], "Combustion releases heat."),
       ("Making bonds:", "releases energy", ["takes in energy", "needs no energy", "destroys atoms"], "Exothermic step."),
       ("The unit of heat energy used here is the:", "joule", ["watt", "newton", "mole"], "J."),
       ("Q = m c ΔT uses ΔT to mean:", "the temperature change", ["the final temperature only", "the mass of water", "the time"], "Final minus initial.")],
      ["Energy level diagrams are schematic; ΔH sign convention (negative for exothermic) stated. Standard enthalpy calculations are left for the later course."])

rates = plot(0, 60, 0, 45, curves=[("40*(1-exp(-x/8))", "red", "hot"), ("40*(1-exp(-x/20))", "blue", "cold")], grid=10, xlabel="time (s)", ylabel="gas (cm³)", w=440, h=270)
v_hot = 40 * (1 - math.exp(-10 / 8)); v_cold = 40 * (1 - math.exp(-10 / 20))
assert abs(v_hot - 28.5) < 0.1 and abs(v_cold - 15.74) < 0.05
assert E("2H2O2 -> 2H2O + O2") and E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2")
build(ch, "rates", "Rates of reaction", 30,
      ["Describe how to measure the rate of a reaction.", "Explain the effect of concentration, temperature, surface area and a catalyst using collision theory.", "Read a rate graph."],
      [("key", "definition", "Rate of reaction",
        "The **rate** of a reaction is how fast reactants are used up or products formed: **rate = change in amount ÷ time**. It can be followed by the **volume of gas** given off over time (gas syringe), the **loss in mass** of a flask, or the time for a **cloudy precipitate** to hide a cross. Example: " + E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2") + " (marble chips and acid)."),
       ("fig", rates, "Volume of gas against time: the steeper curve is the faster reaction.", "Two curves of gas volume against time that level off at 40 cm³: the red curve for hot acid rises steeply and the blue curve for cold acid rises more slowly."),
       ("key", "retenir", "Factors and collision theory",
        "Reactions happen when particles **collide with enough energy** (activation energy). The rate is **increased** by: **higher concentration** (more collisions per second); **higher temperature** (particles move faster so more collisions and more are energetic enough); **larger surface area** (smaller pieces expose more particles); a **catalyst** (gives an easier route with lower activation energy and is not used up); higher pressure for gases. Examples: manganese(IV) oxide speeds up " + E("2H2O2 -> 2H2O + O2") + "; enzymes are biological catalysts; a car's catalytic converter."),
       ("key", "methode", "Reading the graph",
        "The **gradient** of a volume-time graph is the **rate**: steep means fast. The graph **levels off** when one reactant is used up (the reaction has stopped). Equal final volumes mean the same amount of the limiting reactant. For the curves above the average rate in the first 10 s is about 2.9 cm³/s for the hot acid and 1.6 cm³/s for the cold acid."),
       ("key", "pieges", "Common mistakes",
        "- A catalyst does not change the **amount** of product, only how fast it forms.\n- Heating a reaction does not change the final volume of gas if the amounts of reactants are the same.\n- Powder reacts faster than a lump because of its larger surface area, not because it is 'purer'."),
       ("example", "Compare rates", "Marble chips are added to dilute acid in two experiments: in A the chips are small and in B they are large (same mass, same acid). Which gives gas faster and why?", ["The small chips have a larger surface area.", "More acid particles can collide with the marble each second, so A is faster."], "A, because of the larger surface area."),
       ("example", "Average rate", "40 cm³ of gas is collected in 20 s in an experiment. Find the average rate of production.", ["Average rate = volume ÷ time.", "40 ÷ 20 = 2 cm³/s."], "2 cm³/s")],
      [("mcq", "Which change will NOT increase the rate of reaction between marble chips and acid?", "using a larger single lump of marble", ["warming the acid", "using powdered marble", "using more concentrated acid"], "A lump has a smaller surface area."),
       ("num", "A reaction gives 60 cm³ of gas in 30 s. Find the average rate in cm³/s.", 2, "60 ÷ 30 = 2 cm³/s.", 0, "cm³/s"),
       ("tf", "A catalyst is used up during a reaction.", False, "A catalyst is unchanged at the end."),
       ("mcq", "Increasing the temperature speeds up a reaction mainly because:", "more particles have enough energy to react", ["the particles become bigger", "the reaction becomes exothermic", "the acid is diluted"], "More successful collisions."),
       ("open", "Explain, using collision theory, why increasing the concentration of an acid increases the rate.", "There are more acid particles in the same volume, so collisions between the reacting particles happen more often, giving more successful collisions per second.", ["1 mark: more particles per volume", "1 mark: more frequent collisions"]),
       ("prob", "In an experiment 1.0 g of marble chips is added to 50 cm³ of hydrochloric acid and the gas volumes are recorded: 0 s: 0 cm³; 10 s: 12 cm³; 20 s: 20 cm³; 30 s: 24 cm³; 40 s: 24 cm³.", [("num", "Average rate in the first 10 s (cm³/s).", 1.2, "12 ÷ 10 = 1.2 cm³/s.", 0.01, "cm³/s"), ("mcq", "The reaction stops after:", "about 30 s", ["10 s", "20 s", "never"], "The volume stops changing at 30 s."), ("open", "Suggest why the rate is fastest at the beginning.", "The acid concentration is highest at the start and then falls as the acid is used up, so the collisions become less frequent.", ["1 mark: concentration highest at start", "1 mark: decreases as reactants are used"], 2)])],
      [("A catalyst:", "speeds up a reaction and is not used up", ["slows a reaction", "is consumed", "adds energy to the products"], "Definition."),
       ("The rate of reaction is calculated by:", "change in amount ÷ time", ["time ÷ amount", "amount × time", "mass × volume"], "Rate."),
       ("Powdered chalk reacts faster than a lump because of:", "a larger surface area", ["a lower temperature", "a catalyst", "less mass"], "More particles exposed."),
       ("A volume-time graph that is flat shows that the reaction:", "has stopped", ["is at its fastest", "has not started", "is exothermic"], "No more gas."),
       ("Which factor does NOT change the rate?", "the colour of the container", ["temperature", "concentration", "surface area"], "Not a factor.")],
      ["The curves are drawn from the function V = 40(1 − e^(−t/τ)); only the shape matters. Average rates 28.5/10 and 15.7/10 are quoted as about 2.9 and 1.6 cm³/s."])

# =========================================================== 4 Carbon and organic chemistry
ch = p.chapter("carbon", "Carbon and organic chemistry", "Form 4 Chemistry — Carbon, hydrocarbons, alcohols and polymers (to be checked)")
def cycle_fig():
    d = Draw()
    d.sh.append(RECT(150, 10, 180, 40, fill="lightblue", width=2, radius=8)); d.text(240, 35, "carbon dioxide in air", size=12)
    d.sh.append(RECT(15, 150, 120, 40, fill="lightgreen", width=2, radius=8)); d.text(75, 175, "plants", size=12)
    d.sh.append(RECT(345, 150, 120, 40, fill="lightorange", width=2, radius=8)); d.text(405, 175, "animals", size=12)
    d.sh.append(RECT(175, 195, 130, 40, fill="lightgrey", width=2, radius=8)); d.text(240, 220, "fossil fuels, wood", size=11)
    d.line(180, 52, 90, 148, arrow="end", color="green", width=2); d.text(50, 96, "photosynthesis", size=11)
    d.line(137, 170, 343, 170, arrow="end", color="brown", width=2); d.text(190, 162, "feeding", size=11)
    d.line(400, 148, 300, 52, arrow="end", color="red", width=2); d.text(410, 98, "respiration", size=11, anchor="start")
    d.line(240, 193, 240, 52, arrow="end", color="orange", width=2); d.text(250, 120, "burning", size=11, anchor="start")
    return d.fig(480, 245)
assert E("CaCO3 -> CaO + CO2") and E("CaO + H2O -> Ca(OH)2") and E("CO2 + Ca(OH)2 -> CaCO3 + H2O") and E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2") and E("2NaHCO3 -> Na2CO3 + H2O + CO2")
build(ch, "carbon-co2", "Carbon, carbon dioxide and limestone", 30,
      ["Describe diamond and graphite and link their uses to their structures.", "Describe the preparation, tests and uses of carbon dioxide.", "Describe the limestone reactions and the carbon cycle."],
      [("key", "definition", "Allotropes of carbon",
        "**Allotropes** are different forms of the same element. In **diamond** each carbon atom is bonded to **four** others in a rigid giant covalent structure: it is **very hard**, has a very high melting point and does not conduct: used in drill tips and jewellery. In **graphite** each carbon is bonded to **three** others in flat **layers** that slide over each other, with free electrons between: it is **soft**, slippery and **conducts electricity**: used in pencils, lubricants and electrodes. **Fullerenes** (e.g. C₆₀) are ball-shaped molecules of carbon."),
       ("key", "retenir", "Carbon dioxide",
        "**Preparation**: marble chips and dilute hydrochloric acid: " + E("CaCO3 + 2HCl -> CaCl2 + H2O + CO2") + "; collect by **downward delivery** because CO₂ is denser than air. **Test**: limewater turns milky: " + E("CO2 + Ca(OH)2 -> CaCO3 + H2O") + ". It does not burn and puts out flames; it dissolves in water to give a weak acid. **Uses**: fizzy drinks, fire extinguishers, dry ice for cooling, baking powder (" + E("2NaHCO3 -> Na2CO3 + H2O + CO2") + ")."),
       ("key", "retenir", "Limestone",
        "Limestone and marble are **calcium carbonate**. Strong heating gives **quicklime**: " + E("CaCO3 -> CaO + CO2") + ". Water turns it into **slaked lime**: " + E("CaO + H2O -> Ca(OH)2") + ", used to **neutralise acid soil**, in cement, mortar and glass making. The carbonate reacts with acids, which is why acid rain damages limestone buildings."),
       ("fig", cycle_fig(), "The carbon cycle (simplified).", "Boxes for carbon dioxide in air, plants, animals and fossil fuels or wood, with arrows for photosynthesis into plants, feeding from plants to animals, respiration from animals to the air and burning from fuels to the air."),
       ("key", "retenir", "Carbon cycle and the greenhouse effect",
        "Plants take CO₂ from the air in **photosynthesis**; animals get carbon by **feeding**; **respiration**, **burning** and **decay** return CO₂ to the air. Burning fossil fuels and clearing forests add CO₂ faster than it is removed. CO₂ and methane are **greenhouse gases**, and scientists link their increase to global warming."),
       ("key", "pieges", "Common mistakes",
        "- Graphite conducts but diamond does not, although both are pure carbon.\n- CO₂ does not support burning; oxygen does.\n- Quicklime (CaO) and slaked lime (Ca(OH)₂) are different substances; quicklime reacts violently with water."),
       ("example", "Why is graphite a lubricant?", "Explain why graphite is used as a lubricant but diamond is not.", ["Graphite has layers held together by weak forces, so the layers slide.", "Diamond has a rigid 3D network of strong bonds: it cannot slide."], "Graphite layers slide; diamond is rigid."),
       ("example", "Which gas?", "A gas from marble chips and acid is passed into limewater, which turns milky. Name the gas and the milky substance.", ["Marble is calcium carbonate: acid releases carbon dioxide.", "The milky substance is calcium carbonate."], "Carbon dioxide; calcium carbonate")],
      [("mcq", "Which allotrope of carbon conducts electricity?", "graphite", ["diamond", "both", "neither"], "Free electrons between layers."),
       ("match", "Match each substance with a use.", [("diamond", "cutting tool tips"), ("graphite", "pencil 'lead' and electrodes"), ("quicklime", "making slaked lime"), ("limewater", "testing for carbon dioxide")], "Based on properties."),
       ("tf", "Carbon dioxide is collected by downward delivery because it is denser than air.", True, "Heavier than air."),
       ("mcq", "Which process removes carbon dioxide from the air?", "photosynthesis", ["respiration", "burning", "decay"], "Plants take it in."),
       ("open", "Write the equation for heating limestone and name the two products.", E("CaCO3 -> CaO + CO2") + ": calcium oxide (quicklime) and carbon dioxide.", ["1 mark: equation", "1 mark: names"]),
       ("prob", "A farmer's soil is too acidic for maize. A teacher suggests spreading slaked lime.", [("mcq", "Slaked lime is:", "calcium hydroxide", ["calcium carbonate", "calcium oxide", "calcium chloride"], "Ca(OH)₂."), ("mcq", "It helps because it:", "neutralises the acid in the soil", ["adds acid", "adds nitrogen", "makes the soil sandy"], "It is a base."), ("open", "How is slaked lime made from limestone?", "Heat limestone strongly to make quicklime (CaO); add water to the quicklime to make slaked lime.", ["1 mark: heat to make CaO", "1 mark: add water"], 2)])],
      [("Diamond is:", "very hard", ["soft", "slippery", "a conductor"], "Giant rigid structure."),
       ("The test for carbon dioxide is:", "limewater turns milky", ["glowing splint relights", "damp litmus turns blue", "squeaky pop"], "Calcium carbonate forms."),
       ("Heating limestone gives:", "calcium oxide and carbon dioxide", ["calcium and oxygen", "calcium chloride", "calcium hydroxide"], "Thermal decomposition."),
       ("A greenhouse gas is:", "carbon dioxide", ["argon", "nitrogen", "helium"], "It traps heat."),
       ("Allotropes are:", "different forms of the same element", ["different elements", "mixtures", "isotopes"], "Diamond and graphite.")],
      ["Climate statements are kept general and conservative. Preparation of CO₂ and limewater tests are standard practicals; check the syllabus for C₆₀ and carbon monoxide."])

def bond(d, x1, y1, x2, y2, cut=11):
    dx, dy = x2 - x1, y2 - y1; L = math.hypot(dx, dy); ux, uy = dx / L, dy / L
    d.line(x1 + ux * cut, y1 + uy * cut, x2 - ux * cut, y2 - uy * cut, width=2)
def hc_fig():
    d = Draw()
    for (x, y, t) in ((110, 70, "C"), (170, 70, "C"), (110, 30, "H"), (170, 30, "H"), (110, 110, "H"), (170, 110, "H"), (70, 70, "H"), (210, 70, "H")):
        d.text(x, y + 5, t, size=15, bold=True)
    for a, b in (((110, 70), (170, 70)), ((110, 70), (110, 30)), ((170, 70), (170, 30)), ((110, 70), (110, 110)), ((170, 70), (170, 110)), ((110, 70), (70, 70)), ((170, 70), (210, 70))): bond(d, *a, *b)
    for (x, y, t) in ((330, 70, "C"), (390, 70, "C"), (300, 35, "H"), (300, 105, "H"), (420, 35, "H"), (420, 105, "H")): d.text(x, y + 5, t, size=15, bold=True)
    bond(d, 330, 66, 390, 66); bond(d, 330, 74, 390, 74)
    for a, b in (((330, 70), (300, 35)), ((330, 70), (300, 105)), ((390, 70), (420, 35)), ((390, 70), (420, 105))): bond(d, *a, *b)
    d.text(140, 150, "ethane C₂H₆: single bonds only (saturated)", size=11); d.text(330, 175, "ethene C₂H₄: a C=C double bond (unsaturated)", size=11)
    return d.fig(480, 190)
def column_fig():
    d = Draw()
    d.sh.append(RECT(170, 15, 90, 225, fill="lightyellow", width=2))
    for y in (55, 95, 135, 175, 212): d.line(170, y, 260, y, width=1.2, color="grey")
    for y, t in ((35, "refinery gases (LPG)"), (75, "petrol"), (115, "kerosene"), (155, "diesel"), (193, "fuel oil"), (230, "bitumen")):
        d.line(260, y, 285, y, arrow="end", width=1.5); d.text(290, y + 4, t, size=11, anchor="start")
    d.line(20, 235, 20, 20, arrow="end", color="red", width=2)
    d.text(30, 30, "small molecules", size=10, anchor="start"); d.text(30, 43, "low b.p.", size=10, anchor="start")
    d.text(30, 222, "large molecules", size=10, anchor="start"); d.text(30, 235, "high b.p.", size=10, anchor="start")
    d.line(40, 190, 168, 190, arrow="end", color="blue", width=2); d.text(34, 178, "heated crude oil", size=10, anchor="start")
    return d.fig(440, 250)
assert E("CH4 + 2O2 -> CO2 + 2H2O") and E("C3H8 + 5O2 -> 3CO2 + 4H2O") and E("2C4H10 + 13O2 -> 8CO2 + 10H2O") and E("C2H4 + 3O2 -> 2CO2 + 2H2O") and E("2CH4 + 3O2 -> 2CO + 4H2O")
assert E("C10H22 -> C8H18 + C2H4") and E("C2H4 + Br2 -> C2H4Br2") and E("C2H4 + H2 -> C2H6")
alk = lambda n: "C%dH%d" % (n, 2 * n + 2); alkene = lambda n: "C%dH%d" % (n, 2 * n)
assert alk(5) == "C5H12" and alkene(3) == "C3H6" and alk(4) == "C4H10"
build(ch, "hydrocarbons", "Crude oil, alkanes and alkenes", 35,
      ["Name the main fractions of crude oil and their uses.", "Describe alkanes and alkenes, their general formulae and their reactions.", "Describe cracking and the test for unsaturation."],
      [("key", "definition", "Crude oil and fractions",
        "**Crude oil** is a mixture of **hydrocarbons** (compounds of carbon and hydrogen only), formed over millions of years from the remains of tiny sea organisms. It is separated by **fractional distillation** in a tall column, hot at the bottom and cooler at the top. Small molecules (low boiling points) leave at the top: **refinery gases** (cooking gas, LPG), **petrol**, **kerosene** (jet fuel), **diesel**, **fuel oil**, and at the bottom **bitumen** (roads). Cameroon refines crude oil at the SONARA refinery in Limbe."),
       ("fig", column_fig(), "Fractional distillation of crude oil.", "A tall column with heated crude oil entering near the bottom and fractions leaving at different heights: refinery gases at the top, then petrol, kerosene, diesel, fuel oil and bitumen at the bottom."),
       ("key", "retenir", "Alkanes",
        "**Alkanes** have the general formula **CₙH₂ₙ₊₂**: methane CH₄, ethane C₂H₆, propane C₃H₈, butane C₄H₁₀. They are **saturated** (single bonds only) and fairly unreactive, but burn: " + E("CH4 + 2O2 -> CO2 + 2H2O") + " and " + E("C3H8 + 5O2 -> 3CO2 + 4H2O") + ". With too little oxygen they burn **incompletely**, giving poisonous **carbon monoxide** and black soot: " + E("2CH4 + 3O2 -> 2CO + 4H2O") + "."),
       ("fig", hc_fig(), "Displayed formulae of ethane and ethene.", "Ethane with a single bond between two carbon atoms and six hydrogen atoms; ethene with a double bond between two carbon atoms and four hydrogen atoms."),
       ("key", "retenir", "Alkenes and cracking",
        "**Alkenes** have the formula **CₙH₂ₙ** and a **C=C double bond**: ethene C₂H₄, propene C₃H₆. They are **unsaturated** and more reactive. **Test**: they **decolourise bromine water** (orange to colourless) by addition: " + E("C2H4 + Br2 -> C2H4Br2") + "; with hydrogen (nickel catalyst): " + E("C2H4 + H2 -> C2H6") + ". **Cracking** breaks long alkanes into shorter, more useful alkanes and alkenes by heat and a catalyst: " + E("C10H22 -> C8H18 + C2H4") + "."),
       ("key", "pieges", "Common mistakes",
        "- Alkanes do **not** decolourise bromine water in the dark; alkenes do.\n- Count the carbon atoms to find the formula: 5 carbons in an alkane gives C₅H₁₂ (2 × 5 + 2).\n- Incomplete combustion makes carbon monoxide, which is poisonous and colourless."),
       ("example", "Formula of an alkane", "Write the formula of the alkane with 6 carbon atoms and of the alkene with 4.", ["Alkane: CₙH₂ₙ₊₂ with n = 6: C₆H₁₄.", "Alkene: CₙH₂ₙ with n = 4: C₄H₈."], "C₆H₁₄; C₄H₈"),
       ("example", "Which one is unsaturated?", "Bromine water is shaken separately with ethane and ethene. What is seen and what does it show?", ["With ethene the orange colour disappears; with ethane it stays orange.", "Ethene has a C=C double bond: it is unsaturated."], "Ethene decolourises bromine water: unsaturated.")],
      [("mcq", "Which fraction of crude oil is used to surface roads?", "bitumen", ["petrol", "kerosene", "refinery gases"], "It is the heaviest fraction."),
       ("num", "How many hydrogen atoms are in an alkane with 8 carbon atoms?", 18, "2 × 8 + 2 = 18.", 0),
       ("mcq", "Bromine water is decolourised by:", "ethene", ["ethane", "methane", "propane"], "Alkenes add bromine."),
       ("match", "Match each fraction with a use.", [("refinery gases (LPG)", "cooking gas"), ("petrol", "fuel for cars"), ("kerosene", "jet fuel"), ("bitumen", "road surfaces")], "Standard uses."),
       ("open", "Explain why burning a fuel in a closed room with little air is dangerous.", "There is not enough oxygen so burning is incomplete and carbon monoxide, a poisonous gas that prevents the blood carrying oxygen, is produced.", ["1 mark: incomplete combustion", "1 mark: CO is poisonous"]),
       ("prob", "Dodecane, C₁₂H₂₆, is cracked in the laboratory to give an alkane and ethene.", [("mcq", "Cracking uses:", "heat and a catalyst", ["only cold water", "limewater", "filtration"], "Conditions for cracking."), ("open", "Complete the equation: C₁₂H₂₆ → C₁₀H₂₂ + ?", "C₂H₄ (ethene). 12 C and 26 H on the left; 10 C and 22 H plus 2 C and 4 H on the right.", ["1 mark: C₂H₄", "1 mark: atom counts"], 2), ("mcq", "The alkene formed can be identified because it:", "decolourises bromine water", ["turns limewater milky", "relights a glowing splint", "turns blue litmus red"], "Test for a C=C bond.")])],
      [("Hydrocarbons contain:", "carbon and hydrogen only", ["carbon and oxygen", "carbon only", "hydrogen and oxygen"], "Definition."),
       ("In a refinery crude oil is split into fractions by:", "fractional distillation", ["filtration", "sublimation", "electrolysis"], "Different boiling points."),
       ("The general formula of alkenes is:", "CₙH₂ₙ", ["CₙH₂ₙ₊₂", "CₙHₙ", "CₙH₂ₙ₋₂"], "One double bond."),
       ("Alkanes are described as saturated because they:", "have only single bonds", ["are solids", "have double bonds", "dissolve in water"], "No C=C."),
       ("Cracking is used to make:", "short-chain alkanes and alkenes from long ones", ["longer chains", "bitumen", "water"], "More useful products.")],
      ["The SONARA refinery at Limbe is a well-known fact but must be verified before release. Formation of crude oil is stated qualitatively ('millions of years')."])

def ferm_fig():
    d = Draw()
    d.sh.append(CIRCLE(90, 150, 38, fill="lightyellow", width=2)); d.sh.append(RECT(84, 88, 12, 28, fill="white", width=2))
    d.line(90, 88, 90, 60, width=2); d.line(90, 60, 250, 60, width=2); d.line(250, 60, 250, 140, width=2)
    d.sh.append(POLY([(220, 95), (220, 185), (280, 185), (280, 95)], closed=False, width=2)); d.sh.append(RECT(221, 130, 58, 54, fill="lightgrey", stroke="none", width=0))
    d.text(90, 205, "sugar solution + yeast", size=11); d.text(90, 220, "kept warm", size=11)
    d.text(250, 205, "limewater turns milky", size=11); d.text(250, 220, "(carbon dioxide)", size=11)
    d.text(165, 50, "gas bubbles", size=11)
    return d.fig(400, 232)
poly_fl = flow(["Many ethene|molecules", "Addition|polymerisation", "Poly(ethene)|long chain"], w=480, size=11, bh=46, gap=24)
assert E("C6H12O6 -> 2C2H5OH + 2CO2") and E("C2H5OH + 3O2 -> 2CO2 + 3H2O") and E("C2H5OH + CH3COOH -> CH3COOC2H5 + H2O")
assert Mr("C6H12O6") == 180 and Mr("C2H5OH") == 46
yeth = 180 * 2 * 46 / 180; assert yeth == 92
build(ch, "alcohols-polymers", "Ethanol, ethanoic acid and polymers", 35,
      ["Describe the manufacture of ethanol by fermentation and its uses.", "Describe ethanoic acid and esters.", "Describe addition polymers, their uses and the environmental problem of plastic waste."],
      [("key", "retenir", "Alcohols and fermentation",
        "**Alcohols** contain the **–OH** group: methanol CH₃OH, **ethanol C₂H₅OH**, propanol C₃H₇OH. Ethanol is made by **fermentation**: yeast acting on sugar **without oxygen** at about 25–35 °C: " + E("C6H12O6 -> 2C2H5OH + 2CO2") + ". Palm wine, raffia wine and corn beer are made this way. The alcohol is made stronger by **fractional distillation**. Ethanol is also made from ethene and steam in industry. It burns: " + E("C2H5OH + 3O2 -> 2CO2 + 3H2O") + "."),
       ("fig", ferm_fig(), "Fermentation of sugar: carbon dioxide is given off.", "A flask of sugar solution and yeast with a delivery tube bubbling gas into a test tube of limewater, which turns milky."),
       ("key", "attention", "Uses and health",
        "Ethanol is a **solvent** (perfumes, medicines), a **fuel** (alone or mixed with petrol), an **antiseptic** and the alcohol in drinks. **Alcohol is a drug**: it harms the liver and brain, is dangerous when driving, and is not for children or pregnant women. **Methanol** is poisonous and can cause blindness or death if drunk. Home-made spirits can contain it."),
       ("key", "retenir", "Ethanoic acid and esters",
        "Oxidation of ethanol (for example when wine goes sour) gives **ethanoic acid** CH₃COOH, the acid in **vinegar**: a **weak acid**. An **ester** is made from an alcohol and an acid: " + E("C2H5OH + CH3COOH -> CH3COOC2H5 + H2O") + " (ethyl ethanoate). Esters smell sweet and fruity and are used in perfumes and flavourings."),
       ("fig", poly_fl, "Making poly(ethene).", "Three boxes joined by arrows: many ethene molecules, addition polymerisation, poly(ethene) long chain."),
       ("key", "retenir", "Polymers",
        "A **polymer** is a long molecule made from many small **monomers**. In **addition polymerisation** alkene monomers with C=C bonds join without anything being lost: ethene gives **poly(ethene)** (bags, bottles), propene gives poly(propene), chloroethene gives PVC (pipes). **Natural polymers**: starch, proteins, cellulose. **Plastic waste** does not rot (not biodegradable): it blocks drains, harms animals and pollutes rivers and the sea: reduce, reuse and recycle."),
       ("key", "pieges", "Common mistakes",
        "- Fermentation needs **no oxygen**; with oxygen, vinegar can form instead.\n- Monomers are small molecules; the polymer is the long chain.\n- Burning plastic gives toxic fumes: never burn plastic waste in the open."),
       ("example", "Fermentation", "State two conditions needed for the fermentation of glucose and the gas produced.", ["Yeast (an enzyme source) and a warm temperature of about 30 °C; no oxygen.", "The gas is carbon dioxide, which turns limewater milky."], "Yeast, warmth and no oxygen; carbon dioxide."),
       ("example", "Mass of ethanol", "180 g of glucose (M = 180) ferments completely. Find the mass of ethanol (M = 46) from C₆H₁₂O₆ → 2C₂H₅OH + 2CO₂.", ["n(glucose) = 180 ÷ 180 = 1 mol; ethanol = 2 mol.", "m = 2 × 46 = 92 g."], "92 g")],
      [("mcq", "The enzyme source used in fermentation is:", "yeast", ["limewater", "iron", "chlorine"], "Yeast contains the enzymes."),
       ("num", "How many moles of ethanol are formed when 0.5 mol of glucose ferments completely?", 1.0, "Ratio 1 : 2, so 0.5 × 2 = 1.0 mol.", 0.01, "mol"),
       ("tf", "Poly(ethene) is made from ethane.", False, "It is made from ethene, which has a C=C bond."),
       ("mcq", "Vinegar contains:", "ethanoic acid", ["ethanol only", "methane", "chlorine"], "A weak acid."),
       ("open", "Describe two problems caused by plastic waste and one way to reduce them.", "Plastic does not rot, so it builds up; it blocks drains and causes floods; it harms animals and pollutes rivers and the sea. Reduce by using fewer bags, reusing containers and recycling.", ["1 mark each problem, maximum 2", "1 mark: a way to reduce"]),
       ("prob", "A pupil sets up sugar solution, yeast and water in a flask at 30 °C with a delivery tube leading into limewater.", [("mcq", "The limewater turns:", "milky", ["blue", "pink", "colourless"], "CO₂ is produced."), ("mcq", "The liquid left in the flask contains:", "ethanol", ["methane", "ethanoic acid only", "salt only"], "Fermentation product."), ("open", "Why is the flask kept warm but not hot?", "Yeast enzymes work best at about 30 °C; at a high temperature the enzymes are destroyed and the yeast dies, while in the cold they work very slowly.", ["1 mark: enzymes work best warm", "1 mark: too hot destroys enzymes"], 2)])],
      [("The functional group in alcohols is:", "–OH", ["–COOH", "C=C", "–Cl"], "Hydroxyl."),
       ("Ethanol is made by fermentation using:", "yeast and sugar", ["limestone and acid", "iron and steam", "plastic"], "Anaerobic."),
       ("Monomers join in an addition polymer through their:", "C=C double bonds", ["O–H bonds", "ionic bonds", "metal bonds"], "The double bond opens."),
       ("Which of these is a natural polymer?", "starch", ["PVC", "nylon", "poly(ethene)"], "Made of glucose units."),
       ("An ester has a:", "fruity smell", ["sour taste only", "metallic smell", "no smell"], "Used in flavours.")],
      ["Health and alcohol content is conservative and general; to be reviewed by a health worker. Percentages of alcohol in drinks are intentionally not given. Check whether the syllabus includes esters and condensation polymers (nylon, polyesters) and add if needed."])

barsN = bars([("urea", 46.7, "green"), ("ammonium nitrate", 35.0, "blue"), ("ammonium sulphate", 21.2, "orange")], w=440, h=250)
assert abs(28 / 132 * 100 - 21.2) < 0.05 and Mr("(NH4)2SO4") == 132
assert E("N2 + 3H2 -> 2NH3", arrow="⇌") and E("NH3 + HNO3 -> NH4NO3")
def soap_fig():
    d = Draw(); cx, cy = 110, 100
    d.sh.append(CIRCLE(cx, cy, 24, fill="lightyellow", width=1.5)); d.text(cx, cy + 4, "grease", size=10)
    for k in range(10):
        a = 2 * math.pi * k / 10
        d.line(cx + 28 * math.cos(a), cy + 28 * math.sin(a), cx + 50 * math.cos(a), cy + 50 * math.sin(a), width=2, color="brown")
        d.sh.append(CIRCLE(cx + 56 * math.cos(a), cy + 56 * math.sin(a), 6, fill="lightblue", width=1.5))
    d.text(300, 70, "soap ion", size=12, anchor="start", bold=True)
    d.sh.append(CIRCLE(300, 100, 7, fill="lightblue", width=1.5)); d.line(307, 100, 380, 100, width=2, color="brown")
    d.text(300, 125, "ionic head: likes water", size=11, anchor="start"); d.text(300, 142, "tail: likes grease", size=11, anchor="start")
    d.text(110, 188, "soap lifts grease into the water", size=11)
    return d.fig(480, 200)
build(ch, "soap-fertilisers", "Soaps, detergents and fertilisers", 30,
      ["Describe how soap is made and how it cleans.", "Compare soaps and detergents.", "State the elements plants need and calculate the percentage of nitrogen in fertilisers."],
      [("key", "retenir", "Making soap",
        "Soap is made by boiling a **fat or oil** (palm oil, palm kernel oil, groundnut oil) with a strong alkali such as **sodium hydroxide** (caustic soda) or **potassium hydroxide** (potash made from plant ash): **fat or oil + alkali → soap + glycerol**. This reaction is called **saponification**. Salt is added to make the soap separate and float. Caustic soda is **corrosive**: wear eye protection and gloves; keep children away."),
       ("fig", soap_fig(), "How soap removes grease.", "Grease in the centre surrounded by soap ions whose tails point into the grease and whose heads point out into the water, with a key to the soap ion."),
       ("key", "retenir", "How soap and detergents clean",
        "A soap ion has an **ionic head** that is attracted to **water** and a long **hydrocarbon tail** that is attracted to **grease**. The tails dissolve in the grease and the heads stay in the water, so the grease breaks into tiny droplets that float away. In **hard water** soap reacts with calcium and magnesium ions and forms **scum**, using up soap. **Detergents** (soapless detergents, made from petroleum chemicals) do **not** form scum in hard water."),
       ("key", "retenir", "Fertilisers",
        "Plants need **nitrogen (N)** for leaves and growth, **phosphorus (P)** for roots and **potassium (K)** for flowers and fruit: a fertiliser labelled **NPK** contains all three. Common ones: **ammonium nitrate** NH₄NO₃, **urea** CO(NH₂)₂, **ammonium sulphate** (NH₄)₂SO₄, superphosphate, potassium chloride. Ammonia is made industrially from nitrogen and hydrogen: " + E("N2 + 3H2 -> 2NH3", arrow="⇌") + ", then " + E("NH3 + HNO3 -> NH4NO3") + "."),
       ("fig", barsN, "Percentage of nitrogen by mass in three fertilisers.", "A bar chart: urea 46.7, ammonium nitrate 35.0, ammonium sulphate 21.2 percent nitrogen."),
       ("key", "attention", "Using fertilisers wisely",
        "Too much fertiliser washed into rivers makes algae grow too fast; when they die, bacteria use up the oxygen and fish die (**eutrophication**). Nitrate can also pollute well water. Use the amount recommended, apply before rain with care, and consider organic manure and crop rotation. Farmers should follow the advice of the local agricultural officer."),
       ("key", "pieges", "Common mistakes",
        "- Soap does not clean by dissolving grease like a solvent: it surrounds droplets with its ions.\n- A higher percentage of nitrogen does not mean more is always better: too much harms crops and rivers.\n- Detergents are not soap: they are made from different chemicals."),
       ("example", "Percentage of nitrogen", "Find the percentage of nitrogen in ammonium sulphate, (NH₄)₂SO₄ (M_r = 132; A_r: N 14).", ["There are 2 N atoms: 2 × 14 = 28.", "28 ÷ 132 × 100 = 21.2 %."], "21.2 %"),
       ("example", "Which fertiliser gives more N?", "Which of urea (46.7 % N) or ammonium sulphate (21.2 % N) supplies more nitrogen per 50 kg bag, and how much?", ["Urea: 0.467 × 50 = 23.35 kg N. Ammonium sulphate: 0.212 × 50 = 10.6 kg N.", "Urea gives more."], "Urea: about 23.4 kg against 10.6 kg")],
      [("mcq", "The reaction of fat with sodium hydroxide to make soap is called:", "saponification", ["fermentation", "cracking", "distillation"], "Soap-making."),
       ("num", "Find the percentage of nitrogen in ammonium nitrate, NH₄NO₃ (M_r = 80).", 35, "28 ÷ 80 × 100 = 35 %.", 0.1, "%"),
       ("mcq", "Why do detergents work better than soap in hard water?", "they do not form scum with calcium ions", ["they are acidic", "they are alkaline", "they contain sand"], "No insoluble salt forms."),
       ("tf", "Soap has a tail that is attracted to grease.", True, "The hydrocarbon tail is grease-loving."),
       ("open", "Explain how too much fertiliser in a river can kill fish.", "Excess nitrate and phosphate make algae grow quickly; when they die, bacteria that decompose them use up the dissolved oxygen, so fish suffocate (eutrophication).", ["1 mark: excess nutrients make algae grow", "1 mark: decomposition uses up oxygen"]),
       ("prob", "A farmer in Buea compares a 50 kg bag of urea (46.7 % N) with a 50 kg bag of NPK labelled 20-10-10 (20 % nitrogen).", [("num", "Mass of nitrogen in the urea bag in kg.", 23.35, "0.467 × 50 = 23.35 kg.", 0.05, "kg"), ("num", "Mass of nitrogen in the NPK bag in kg.", 10, "0.20 × 50 = 10 kg.", 0.05, "kg"), ("open", "Give one advantage of the NPK bag even though it has less nitrogen.", "It also supplies phosphorus and potassium, so it feeds the plant with all three main nutrients.", ["1 mark for P and K"], 2)])],
      [("The 'N' in NPK stands for:", "nitrogen", ["neon", "nickel", "sodium"], "Plant nutrient."),
       ("The tail of a soap ion is attracted to:", "grease", ["water", "salt", "sand"], "Hydrocarbon tail."),
       ("Soap-making uses fat and:", "an alkali", ["an acid", "a metal", "a gas"], "Sodium hydroxide."),
       ("Run-off from heavily fertilised fields makes a river too rich in:", "nutrients such as nitrate and phosphate", ["oxygen", "chlorine", "limestone"], "This leads to algal growth and oxygen shortage."),
       ("A fertiliser containing nitrogen is:", "urea", ["salt", "limestone", "sand"], "CO(NH₂)₂.")],
      ["NPK numbers (20-10-10) and the 'kg of N' calculations are illustrative; check local fertiliser products. Soap making with caustic soda is a teacher-led activity and handling advice is general. Industrial ammonia (Haber process) is mentioned only; conditions are covered in Form 5."])

p.write()
