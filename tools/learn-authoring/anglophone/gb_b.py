from biolib import *
import math


def add(p):
    # ------------------------------------------------------------------ movement of substances
    ch = p.chapter("membranes", "Movement of substances into and out of cells",
                   "GCE O Level Biology — Diffusion, osmosis and active transport (to be checked against the official syllabus)")

    # surface area : volume of cubes (computed)
    def sav(a): return (6 * a * a) / (a ** 3)
    r1, r2, r4 = sav(1), sav(2), sav(4)
    assert (r1, r2, r4) == (6.0, 3.0, 1.5)
    dots = []
    import random as _r
    rr = _r.Random(3)
    for i in range(14): dots.append(CIRCLE(30 + rr.randint(0, 80), 45 + rr.randint(0, 110), 5, fill="orange", width=1))
    for i in range(3): dots.append(CIRCLE(180 + rr.randint(0, 100), 45 + rr.randint(0, 110), 5, fill="orange", width=1))
    fig_diff = shapes([RECT(20, 30, 270, 140, stroke="grey", width=1.5)] + dots +
                      [LINE(300, 100, 420, 100, arrow="end", width=3, color="blue"),
                       T(360, 85, "net movement", size=12), T(75, 195, "high concentration", size=12), T(235, 195, "low concentration", size=12)], 480, 215)
    fig_sav = bars([("1 cm cube", r1), ("2 cm cube", r2), ("4 cm cube", r4)], unit="ratio", w=400, h=220)

    build(ch, "diffusion", "Diffusion and surface area", 25,
          ["Define diffusion and name the factors that affect its rate.",
           "Give examples of diffusion in living organisms.",
           "Calculate surface area to volume ratio and explain why large organisms need exchange surfaces."],
          [("key", "definition", "Diffusion",
            "**Diffusion** is the net movement of particles (molecules or ions) from a region of **higher concentration** to a region of **lower concentration**, down a concentration gradient, as a result of their random movement.\n\nDiffusion is a **passive** process: it needs no energy from the cell."),
           ("fig", fig_diff, "Particles spread from where they are crowded to where they are few.", "A box with many orange dots on the left and few on the right; a blue arrow points to the right labelled net movement."),
           ("key", "retenir", "Examples and factors",
            "In living things: oxygen enters cells and carbon dioxide leaves them; carbon dioxide enters leaves through stomata; digested food enters the blood in the ileum.\n\nThe rate of diffusion is **faster** when:\n- the concentration gradient is steeper,\n- the temperature is higher,\n- the surface area is larger,\n- the distance is shorter."),
           ("key", "retenir", "Surface area to volume ratio",
            "As an object gets bigger its volume grows faster than its surface area, so the **ratio SA : V falls**. A tiny single-celled organism has enough surface for diffusion alone. A large animal does not, so it needs **exchange surfaces** (lungs, villi) and a transport system."),
           ("fig", fig_sav, "SA:V falls as a cube gets larger (computed from 6a² ÷ a³).", "A bar chart: the 1 cm cube has a ratio of 6, the 2 cm cube 3 and the 4 cm cube 1.5."),
           ("key", "pieges", "Common mistakes",
            "- Diffusion stops moving particles only when the *net* movement is zero (equal concentration); particles still move randomly.\n- Diffusion is not 'osmosis': osmosis is only the movement of *water* through a partially permeable membrane.\n- A larger cell does not have a larger ratio; it has a smaller one."),
           ("example", "Surface area to volume ratio", "A cube of agar has sides of 2 cm. Calculate its surface area, volume and SA : V ratio.",
            ["Surface area = 6 faces × (2 × 2) cm² = 24 cm².", "Volume = 2 × 2 × 2 = 8 cm³.", "Ratio = 24 ÷ 8 = 3."],
            "SA : V = 3 : 1"),
           ("example", "Predict the rate", "Coloured dye is placed in water at 20 °C and in water at 40 °C. In which beaker does it spread faster, and why?",
            ["Higher temperature gives particles more kinetic energy.", "They move faster, so the dye spreads more quickly in the warm water."],
            "In the 40 °C water, because particles move faster at a higher temperature.")],
          [("mcq", "Diffusion is the movement of particles:", "from high to low concentration down a gradient", ["from low to high concentration using energy", "of water only through a membrane", "only in solids"], "Diffusion moves particles down a concentration gradient, without energy."),
           ("tf", "Diffusion requires energy from respiration.", False, "Diffusion is passive; active transport needs energy."),
           ("num", "A cube has sides of 4 cm. Calculate its surface area to volume ratio.", sav(4), "SA = 6 × 16 = 96 cm²; V = 64 cm³; 96 ÷ 64 = 1.5.", 0.01),
           ("mcq", "Why does a large animal need a lung or gills but an amoeba does not?", "the large animal has a small SA:V ratio", ["the amoeba does not need oxygen", "diffusion does not occur in large animals", "the large animal has no cell membranes"], "In large animals surface area is too small compared with volume, so special exchange surfaces are needed."),
           ("open", "State three ways in which the lining of the small intestine is adapted for fast absorption by diffusion.", "It is folded into many villi that give a large surface area; the wall is only one cell thick (short distance); each villus has a rich blood supply that keeps the concentration gradient steep.", ["1 mark: large surface area / villi", "1 mark: thin wall / short distance", "1 mark: blood supply maintains gradient"]),
           ("prob", "Two cubes of agar jelly containing indicator are placed in acid: cube A has sides of 1 cm and cube B has sides of 2 cm.", [("num", "SA : V ratio of cube A.", sav(1), "6 ÷ 1 = 6.", 0.01), ("num", "SA : V ratio of cube B.", sav(2), "24 ÷ 8 = 3.", 0.01), ("mcq", "Which cube changes colour right through first?", "cube A", ["cube B", "both at the same time", "neither"], "The larger ratio gives faster diffusion relative to volume, and the distance to the centre is smaller."), ("open", "Name one factor that would make the acid diffuse faster.", "A higher temperature or a stronger acid (steeper gradient).", ["1 mark for a correct factor"], 1)])],
          [("The best definition of diffusion is:", "net movement down a concentration gradient", ["movement against a gradient", "movement of water only", "movement using ATP"], "Diffusion goes from high to low concentration."),
           ("Which does NOT speed up diffusion?", "a longer distance", ["higher temperature", "larger surface area", "steeper concentration gradient"], "A longer distance slows it."),
           ("A 3 cm cube has a SA:V ratio of:", "2", ["6", "9", "27"], "54 cm² ÷ 27 cm³ = 2."),
           ("Oxygen moves into a cell from the blood by:", "diffusion", ["osmosis", "active transport", "digestion"], "Oxygen moves down its concentration gradient."),
           ("The ratio SA:V of a growing cell:", "decreases", ["increases", "stays equal", "is always 1"], "Volume increases faster than surface area.")],
          ["Cube dimensions and ratios are computed in the script; real cells are not cubes.", "Syllabus may require Fick's law-type reasoning only qualitatively."])

    # osmosis
    pot = [(5.0, 5.7), (5.0, 4.2), (4.0, 4.0)]
    def pc(a, b): return round((b - a) / a * 100, 1)
    assert pc(*pot[0]) == 14.0 and pc(*pot[1]) == -16.0
    fig_cells = shapes([
        RECT(20, 40, 120, 90, fill="lightblue", stroke="blue", width=1.5), RECT(50, 55, 60, 60, fill="lightgreen", stroke="green", width=4, radius=12), T(80, 150, "turgid", size=13), T(80, 170, "in pure water", size=11),
        RECT(180, 40, 120, 90, fill="lightyellow", stroke="orange", width=1.5), RECT(210, 55, 60, 60, fill="lightgreen", stroke="green", width=2, radius=4), T(240, 150, "flaccid", size=13), T(240, 170, "in same strength", size=11),
        RECT(340, 40, 120, 90, fill="lightorange", stroke="orange", width=1.5), RECT(355, 55, 90, 60, stroke="green", width=3), RECT(375, 70, 40, 30, fill="lightgreen", stroke="green", width=1.5, radius=8), T(400, 150, "plasmolysed", size=13), T(400, 170, "in strong solution", size=11)], 480, 190)
    build(ch, "osmosis", "Osmosis and active transport", 25,
          ["Define osmosis and describe its effect on plant and animal cells.",
           "Calculate the percentage change in mass in an osmosis experiment.",
           "Define active transport and give examples."],
          [("key", "definition", "Osmosis",
            "**Osmosis** is the movement of **water molecules** from a region of **higher water potential** (a dilute solution or pure water) to a region of **lower water potential** (a concentrated solution) through a **partially permeable membrane**.\n\nIn simple words: water moves from the dilute solution to the concentrated solution."),
           ("key", "retenir", "Effects on cells",
            "- **Plant cell in pure water**: water enters by osmosis; the vacuole swells and presses on the cell wall; the cell becomes **turgid** (firm). The wall prevents bursting.\n- **Plant cell in a concentrated solution**: water leaves; the cytoplasm shrinks away from the wall; the cell is **plasmolysed**; a tissue becomes **flaccid** and the plant wilts.\n- **Animal cell** (no wall) in pure water can burst; in a very concentrated solution it shrinks."),
           ("fig", fig_cells, "A plant cell in three different surroundings.", "Three beakers: a swollen turgid plant cell in pure water, a limp flaccid cell in a solution of equal strength and a plasmolysed cell whose cytoplasm has pulled away from the wall in a strong solution."),
           ("key", "definition", "Active transport",
            "**Active transport** is the movement of particles through a cell membrane **against** a concentration gradient (from low to high concentration), using **energy from respiration**.\n\nExamples: root hair cells take in nitrate and other mineral ions from the soil; glucose is absorbed in the small intestine and kidney tubules when its concentration is lower outside than inside the cell."),
           ("key", "pieges", "Common mistakes",
            "- Water always moves *towards the more concentrated solution* in osmosis; learners often reverse this.\n- The membrane must be *partially permeable*: it lets water through but not large solute molecules.\n- Active transport needs energy, so it stops if a cell is starved of oxygen or poisoned."),
           ("example", "Percentage change in mass", "A strip of potato of mass 5.0 g is kept in pure water for one hour and its mass becomes 5.7 g. Calculate the percentage change in mass and explain it.",
            ["Change = 5.7 − 5.0 = 0.7 g.", "Percentage change = 0.7 ÷ 5.0 × 100 = 14 %.", "Water entered the potato cells by osmosis because the water outside had a higher water potential."],
            "+14 %: the potato gained water by osmosis."),
           ("example", "A strip in strong sugar solution", "A 5.0 g potato strip is placed in strong sugar solution and weighs 4.2 g afterwards. Find the percentage change.",
            ["Change = 4.2 − 5.0 = −0.8 g.", "−0.8 ÷ 5.0 × 100 = −16 %.", "The negative sign shows loss: water left the cells by osmosis."],
            "−16 %: the strip lost water.")],
          [("mcq", "In osmosis, water moves:", "from a dilute solution to a concentrated solution across a partially permeable membrane", ["from a concentrated solution to a dilute solution", "only through the cell wall", "using energy from respiration"], "Water moves from higher to lower water potential, i.e. dilute to concentrated."),
           ("match", "Match the cell condition with its description.", [("turgid", "swollen and firm in pure water"), ("flaccid", "soft because water has been lost"), ("plasmolysed", "cytoplasm pulled away from the wall"), ("active transport", "uses energy to move ions against a gradient")], "Each term is matched with its definition."),
           ("num", "A potato strip of mass 5.0 g becomes 5.7 g in water. What is its percentage change in mass?", pc(*pot[0]), "(5.7 − 5.0) ÷ 5.0 × 100 = 14 %.", 0.1, "%"),
           ("mcq", "Why can a plant cell not burst in pure water while an animal cell can?", "the plant cell has a strong cellulose cell wall", ["the plant cell has no membrane", "the animal cell contains starch", "the plant cell makes more water"], "The wall resists the pressure of the swollen vacuole."),
           ("open", "Explain why a plant wilts when too much salt fertiliser is put in the soil.", "The soil solution becomes more concentrated than the cell sap, so water leaves the root cells and then the other cells by osmosis. The cells lose turgor and become flaccid, so the plant wilts.", ["1 mark: soil solution more concentrated", "1 mark: water leaves by osmosis", "1 mark: cells lose turgor / become flaccid"]),
           ("prob", "Three potato strips were placed in three solutions for one hour. Strip 1: 5.0 g to 5.7 g. Strip 2: 5.0 g to 4.2 g. Strip 3: 4.0 g to 4.0 g.", [("num", "Percentage change of strip 2.", pc(*pot[1]), "(4.2 − 5.0) ÷ 5.0 × 100 = −16.", 0.1, "%"), ("num", "Percentage change of strip 3.", pc(*pot[2]), "No change in mass = 0 %.", 0.01, "%"), ("mcq", "What does the result of strip 3 suggest?", "the solution had the same water potential as the potato cells", ["the solution was pure water", "the cells were dead", "osmosis only occurs in animals"], "No net movement of water means equal concentrations."), ("open", "Give one reason why percentage change is used instead of change in grams.", "Strips may have different starting masses; percentages allow a fair comparison.", ["1 mark for the idea of comparing strips of different initial mass"], 1)])],
          [("Plasmolysis happens when a plant cell is placed in:", "a concentrated solution", ["pure water", "a dilute solution", "oxygen"], "Water leaves the cell."),
           ("Active transport requires:", "energy from respiration", ["a dilute solution", "a cell wall", "a steep gradient only"], "It works against the gradient so energy is needed."),
           ("A turgid cell is:", "firm because of water in the vacuole", ["shrunken", "dead", "without a cell wall"], "Turgor pressure makes cells firm."),
           ("Root hairs absorb mineral ions mainly by:", "active transport", ["osmosis", "transpiration", "digestion"], "Ions are usually at a lower concentration in the soil than in the root hair."),
           ("The membrane in osmosis is:", "partially permeable", ["fully permeable", "impermeable", "made of cellulose"], "It allows water through but not large solutes.")],
          ["Water potential terminology: some syllabi only use 'dilute to concentrated'; check which is required.", "Potato strip data are invented for practice."])

    # ------------------------------------------------------------------ plant transport
    ch = p.chapter("plant-transport", "Transport in flowering plants",
                   "GCE O Level Biology — Transport in plants: transpiration, xylem and phloem (to be checked against the official syllabus)")
    leaf = shapes([RECT(40, 30, 340, 22, fill="lightgreen", radius=4, stroke="green"), RECT(40, 52, 340, 50, fill="green", radius=2, stroke="green"),
                   RECT(40, 102, 340, 50, fill="lightgreen", stroke="green", radius=2), RECT(40, 152, 340, 18, fill="lightgreen", stroke="green", radius=4),
                   RECT(190, 152, 40, 18, fill="paper", stroke="ink", width=1.5), CIRCLE(198, 161, 5, fill="green", width=1), CIRCLE(222, 161, 5, fill="green", width=1),
                   RECT(100, 105, 40, 28, fill="lightblue", stroke="blue"),
                   LINE(380, 41, 410, 41, width=1.2), T(414, 45, "upper epidermis", size=11, anchor="start"),
                   LINE(380, 77, 410, 77, width=1.2), T(414, 81, "palisade layer", size=11, anchor="start"),
                   LINE(380, 127, 410, 127, width=1.2), T(414, 131, "spongy layer", size=11, anchor="start"),
                   LINE(210, 170, 210, 195, width=1.2), T(210, 210, "stoma with guard cells", size=11),
                   LINE(120, 119, 120, 195, width=1.2, dash=True), T(120, 210, "xylem (vein)", size=11)], 540, 225)
    path = flow(["Soil|water", "Root hair|cell", "Root|xylem", "Stem|xylem", "Leaf|mesophyll", "Stomata|(vapour)"], w=480, size=10, bh=40, gap=14)
    build(ch, "transpiration", "Water transport: transpiration, xylem and phloem", 25,
          ["Define transpiration and list factors that affect its rate.",
           "Describe the path of water through a plant and the roles of xylem and phloem.",
           "Calculate a rate of water uptake from potometer readings."],
          [("key", "definition", "Transpiration",
            "**Transpiration** is the loss of water vapour from the aerial parts of a plant, mainly through the **stomata** of the leaves. Water evaporates from the damp walls of the mesophyll cells and the vapour **diffuses** out through the open stomata.\n\nTranspiration pulls water up the **xylem** (the transpiration stream), supplies water for photosynthesis, keeps cells turgid, carries ions and helps cool the leaf."),
           ("fig", leaf, "Section through a leaf showing where water vapour escapes.", "A leaf cross-section with an upper epidermis, a dark green palisade layer, a spongy layer with a vein containing xylem, and a lower epidermis with a stoma between two guard cells."),
           ("fig", path, "The path taken by water.", "A flow of six boxes: soil water, root hair cell, root xylem, stem xylem, leaf mesophyll, stomata."),
           ("key", "retenir", "Factors that increase transpiration",
            "- **Higher temperature** (faster evaporation).\n- **Lower humidity** of the air (steeper gradient).\n- **Wind** (removes vapour near the leaf).\n- **Bright light** (stomata open).\n\nStomata close at night and in drought, so less water is lost."),
           ("key", "retenir", "Xylem and phloem",
            "- **Xylem**: dead, hollow tubes with walls strengthened with **lignin**; carry **water and mineral ions** upwards from the roots; also give support.\n- **Phloem**: living **sieve tubes**; carry **sucrose and amino acids** (**translocation**) from the leaves to growing parts and storage organs, in both directions.\n\nRoot hairs have a large surface area for absorbing water (osmosis) and ions (active transport)."),
           ("key", "pieges", "Common mistakes",
            "- Xylem carries water *up*; phloem carries food *up and down*.\n- Transpiration is the loss of water *vapour*, not of liquid water.\n- A potometer measures water *uptake*; this is only an estimate of transpiration."),
           ("example", "Rate of water uptake", "In a potometer an air bubble moves 36 mm in 12 minutes. Calculate the rate of uptake in mm per minute. When a fan is switched on the bubble moves 60 mm in 12 minutes: find the new rate.",
            ["Rate before = 36 ÷ 12 = 3 mm/min.", "Rate with the fan = 60 ÷ 12 = 5 mm/min.", "The wind removes water vapour near the leaf, so transpiration increases."],
            "3 mm/min then 5 mm/min; wind increases transpiration."),
           ("example", "Wilting in Garoua", "In the dry season a young maize plant wilts at noon but recovers in the evening. Explain.",
            ["At noon it is hot and dry, so transpiration is fast and water is lost faster than the roots can supply it.", "The cells lose turgor and the leaves droop (wilt).", "In the evening transpiration slows, the cells take in water again and become turgid."],
            "Water loss exceeds uptake at noon; turgor is restored when transpiration slows.")],
          [("mcq", "Which tissue carries water and mineral ions up the stem?", "xylem", ["phloem", "epidermis", "cambium only"], "Xylem transports water and minerals upwards."),
           ("match", "Match the term to its description.", [("stoma", "pore in the leaf surface"), ("phloem", "carries sucrose"), ("xylem", "contains lignin"), ("potometer", "measures water uptake")], "Standard definitions."),
           ("num", "A bubble moves 45 mm in 15 minutes in a potometer. What is the rate in mm per minute?", 3, "45 ÷ 15 = 3 mm/min.", 0.01, "mm/min"),
           ("mcq", "Which condition gives the highest rate of transpiration?", "hot, dry, windy day", ["cool, humid, still night", "cool, windy, humid day", "cold, dry, still night"], "High temperature, low humidity and wind all increase the rate."),
           ("open", "Explain why transpiration is useful to a plant.", "It pulls water and mineral ions up the xylem to the leaves, supplies water for photosynthesis, keeps the cells turgid so the plant is supported, and cools the leaves by evaporation.", ["1 mark each for any three correct uses"]),
           ("prob", "An experiment compares water loss from leafy shoots in a potometer in four conditions.", [("mcq", "A shoot in still, humid shade will have:", "the lowest rate of uptake", ["the highest rate", "no uptake", "uptake only through phloem"], "Still humid air and low light reduce transpiration."), ("mcq", "Coating the lower surface of leaves with petroleum jelly reduces water loss because:", "it blocks the stomata", ["it kills the xylem", "it closes the phloem", "it cools the leaf"], "Most stomata are on the lower surface; blocking them stops vapour escaping."), ("open", "Explain why the bubble position must be reset to the start of the scale for each reading.", "So that the distance moved in a set time can be measured from the same point, giving fair and comparable rates.", ["1 mark for fair test / same starting point"], 2)])],
          [("Water vapour leaves a leaf mainly through the:", "stomata", ["xylem", "phloem", "root hair"], "Stomata are the main exit."),
           ("Transpiration is increased by:", "wind", ["high humidity", "darkness", "cold temperature"], "Wind removes vapour from around the leaf."),
           ("Sucrose is transported in the:", "phloem", ["xylem", "stomata", "root hairs"], "Phloem carries sugars."),
           ("The strengthening substance in xylem walls is:", "lignin", ["cellulose only", "chlorophyll", "starch"], "Lignin strengthens xylem."),
           ("A potometer measures:", "the uptake of water by a shoot", ["the volume of oxygen released", "the rate of photosynthesis", "the loss of mass of a root"], "It is used to estimate transpiration rate.")],
          ["Details on 'cohesion-tension' are intentionally omitted; check whether the syllabus requires them.", "Leaf diagram is schematic (not to scale)."])
