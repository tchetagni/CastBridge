import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *
from fs_chem import E

p = Pack("form3-biology", "Biology — Form 3", level="Form 3", subject="biology", cursus="secondary",
         description="A first course in O Level Biology for Form 3: the microscope and cells, classification, photosynthesis, diet and digestion, movement of substances, blood and circulation, respiration and gas exchange, reproduction and ecology. Short lessons, worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Biology, Form 3 (first year of the GCE O Level Biology course) — to be checked against the official syllabus")

# =========================================================== 1 Cells
ch = p.chapter("cells", "The microscope and cells", "Form 3 Biology — Microscope, cells and organisation (to be checked)")
def scope_fig():
    d = Draw()
    d.sh.append(RECT(60, 205, 150, 14, fill="lightgrey", width=2)); d.line(185, 205, 185, 70, width=10, color="grey")
    d.sh.append(RECT(110, 55, 90, 12, fill="lightgrey", width=2, radius=3)); d.line(125, 60, 125, 40, width=14, color="ink")
    d.line(122, 70, 122, 130, width=12, color="grey"); d.sh.append(RECT(112, 130, 20, 10, fill="lightblue", width=1.5))
    d.sh.append(RECT(80, 155, 110, 6, fill="lightgrey", width=2)); d.sh.append(CIRCLE(120, 195, 8, fill="yellow", width=1.5))
    d.sh += lab(125, 36, 260, 22, "eyepiece lens", size=11); d.sh += lab(122, 100, 260, 85, "body tube", size=11)
    d.sh += lab(122, 136, 260, 125, "objective lens", size=11); d.sh += lab(190, 158, 260, 160, "stage", size=11)
    d.sh += lab(128, 196, 260, 200, "mirror or lamp", size=11); d.sh += lab(185, 120, 260, 50, "arm", size=11)
    return d.fig(400, 230)
act = 45 / 150; assert abs(act - 0.3) < 1e-12 and abs(act * 1000 - 300) < 1e-9
assert 20 / 0.05 == 400
build(ch, "microscope", "The light microscope and measuring cells", 30,
      ["Name the parts of a light microscope and state their functions.", "Calculate magnification and the actual size of a specimen.", "Describe good rules for biological drawings and use units such as mm and µm."],
      [("key", "definition", "How a light microscope works",
        "A **light microscope** shines light **through a thin specimen** on the **stage**. The **objective lens** forms an enlarged image which the **eyepiece lens** enlarges again. A **mirror or lamp** supplies light; the **coarse** and **fine focusing knobs** move the lens to make the image sharp. **Total magnification = eyepiece magnification × objective magnification**, e.g. ×10 × ×40 = ×400."),
       ("fig", scope_fig(), "A light microscope.", "A microscope with eyepiece lens, body tube, objective lens, arm, stage and mirror or lamp labelled."),
       ("key", "formule", "Sizes and units",
        "**Magnification = image size ÷ actual size**, so **actual size = image size ÷ magnification**. Use one unit: **1 mm = 1000 µm** (micrometres). Most animal and plant cells are 10 to 100 µm across; the smallest thing seen with a light microscope is about 0.2 µm. Example: a drawing 45 mm wide at ×150 is 45 ÷ 150 = **0.30 mm = 300 µm** in real life."),
       ("key", "methode", "Using the microscope and drawing",
        "Start with the **lowest-power objective**, lower it close to the slide while looking from the side, then focus **upwards**. Change to a higher power and use only the **fine focus**. **Drawing rules**: use a sharp pencil; draw large, clear single lines, no shading; draw only what you see; label with **ruled lines that do not cross**; give a **title** and the magnification."),
       ("key", "pieges", "Common mistakes",
        "- Focusing **downwards** while looking through the eyepiece can break the slide.\n- Do not add the magnifications: **multiply** them.\n- Do not forget to convert mm to µm: 0.3 mm is 300 µm, not 3 µm."),
       ("example", "Actual size", "A student draws a cell 20 mm long and the magnification is ×400. Find the actual length in mm and in µm.", ["Actual = 20 ÷ 400 = 0.05 mm.", "0.05 × 1000 = 50 µm."], "0.05 mm = 50 µm"),
       ("example", "Total magnification", "A microscope has eyepiece ×10 and objectives ×4, ×10 and ×40. Give the total magnifications.", ["Multiply each objective by 10.", "×4 gives ×40; ×10 gives ×100; ×40 gives ×400."], "×40, ×100 and ×400")],
      [("mcq", "Which part of the microscope should be used FIRST when looking for a specimen?", "the lowest-power objective lens", ["the highest-power objective lens", "the mirror only", "the stage clips only"], "It has the widest field of view."),
       ("num", "An eyepiece lens is ×10 and the objective lens is ×25. What is the total magnification?", 250, "10 × 25 = 250.", 0),
       ("num", "A cell is drawn 30 mm wide at a magnification of ×150. What is its actual width in µm?", 200, "30 ÷ 150 = 0.2 mm = 200 µm.", 0, "µm"),
       ("mcq", "Biological drawings should be done with:", "a sharp pencil and clear single lines", ["coloured pens and shading", "thick wax crayons", "a ruler to draw every part"], "Clear, accurate lines."),
       ("open", "Describe how to focus a microscope on a slide, starting with the objective lens.", "Place the slide on the stage, turn to the lowest-power objective and lower it close to the slide while looking from the side; then look through the eyepiece and focus upwards with the coarse knob; use the fine knob to sharpen the image and change to a stronger lens only after centring the specimen.", ["1 mark: lowest power first", "1 mark: focus upwards, away from the slide", "1 mark: fine focus for high power"]),
       ("prob", "A plant cell image measured 36 mm across in a photograph labelled ×300.", [("num", "Actual width of the cell in mm.", 0.12, "36 ÷ 300 = 0.12 mm.", 0.001, "mm"), ("num", "Actual width in µm.", 120, "0.12 × 1000 = 120 µm.", 0.5, "µm"), ("mcq", "To make the image larger, you should:", "use a higher-power objective", ["use a lower-power objective", "add a thicker slide", "close the lamp"], "Total magnification would increase.")])],
      [("The lens nearest to the specimen is the:", "objective lens", ["eyepiece lens", "condenser", "mirror"], "It forms the first image."),
       ("1 mm is equal to:", "1000 µm", ["100 µm", "10 µm", "10 000 µm"], "Kilo to milli: 10³."),
       ("A drawing that is 10 mm across of a cell that is really 0.1 mm across has a magnification of:", "×100", ["×10", "×1000", "×0.01"], "10 ÷ 0.1."),
       ("Why must a specimen be thin?", "so that light can pass through it", ["so it fits the stage", "so it is cheaper", "so it floats"], "A thick specimen blocks light."),
       ("Which should be used to sharpen the image at high power?", "the fine focus knob", ["the coarse focus knob", "the mirror", "the stage"], "To avoid hitting the slide.")],
      ["Content overlaps in a simplified form with form4-biology (microscope): intentional, as Form 3 is the first biology course. Check whether µm and the condenser/iris diaphragm are required."])

levels = flow(["cell|(muscle cell)", "tissue|(muscle)", "organ|(heart)", "system|(circulatory)", "organism|(person)"], w=480, size=10, bh=44, gap=14)
cellsg = grid(["red blood cell|carries oxygen", "white blood cell|fights germs", "nerve cell|carries impulses", "root hair cell|absorbs water", "palisade cell|photosynthesis", "sperm cell|fertilises the egg"], cols=3, bh=52, size=10, colors=["lightorange", "lightyellow", "lightblue", "lightgreen", "lightgreen", "lightgrey"])
build(ch, "cell-structure", "Cell structure and organisation", 30,
      ["Name the parts of animal and plant cells and state their functions.", "Explain how specialised cells are adapted to their jobs.", "Describe the levels of organisation in living things."],
      [("key", "definition", "Parts of a cell",
        "- **Cell membrane**: controls what enters and leaves.\n- **Cytoplasm**: where most chemical reactions happen.\n- **Nucleus**: contains the **DNA** (genes) and controls the cell.\n- **Mitochondria**: release energy in **respiration**.\n\n**Plant cells** also have a **cell wall** of **cellulose** (support), **chloroplasts** with chlorophyll (photosynthesis) and a large **permanent vacuole** with cell sap. Animal cells have none of these three, but small temporary vacuoles. **Bacterial cells** have a cell wall but **no nucleus**."),
       ("fig", cellsg, "Six specialised cells and their jobs.", "Six boxes: red blood cell carries oxygen; white blood cell fights germs; nerve cell carries impulses; root hair cell absorbs water; palisade cell does photosynthesis; sperm cell fertilises the egg."),
       ("key", "retenir", "Specialised cells: structure fits function",
        "A **red blood cell** has no nucleus and is a flattened disc, giving more room for **haemoglobin** and a large surface area. A **root hair cell** has a long thin extension that increases the surface for absorbing water. A **palisade cell** is packed with chloroplasts near the top of the leaf. A **nerve cell** has a long fibre to carry messages. A **sperm cell** has a tail to swim and many mitochondria."),
       ("fig", levels, "Levels of organisation from cell to organism.", "Five boxes joined by arrows: cell (muscle cell), tissue (muscle), organ (heart), system (circulatory), organism (person)."),
       ("key", "pieges", "Common mistakes",
        "- The cell wall is **not** the cell membrane: all cells have a membrane, only plants and bacteria have a wall.\n- A tissue is a group of **similar cells**; an organ has **several tissues**.\n- Mature red blood cells have no nucleus, but they are still cells."),
       ("example", "Identify the cell", "A cell has a nucleus, a cell wall, many chloroplasts and a large vacuole. Is it an animal or a plant cell? Which job does it probably do?", ["The cell wall, chloroplasts and large vacuole are plant features.", "Many chloroplasts show it makes food by photosynthesis, like a palisade cell."], "A plant cell, probably a palisade cell, making food."),
       ("example", "Adaptation", "Explain how a red blood cell is adapted to carry oxygen.", ["It contains haemoglobin which combines with oxygen.", "It has no nucleus and is disc-shaped, giving more space for haemoglobin and a large surface area."], "Haemoglobin, no nucleus, large surface area.")],
      [("mcq", "Which part of a cell contains the genes?", "nucleus", ["cytoplasm", "vacuole", "cell wall"], "DNA is in the nucleus."),
       ("match", "Match each cell part with its function.", [("mitochondria", "release energy by respiration"), ("chloroplast", "carries out photosynthesis"), ("cell wall", "supports a plant cell"), ("cell membrane", "controls what enters and leaves")], "Standard functions."),
       ("tf", "An animal cell has a cell wall made of cellulose.", False, "Only plant cells do."),
       ("mcq", "Cells of the same kind that work together form a:", "tissue", ["organ", "system", "nucleus"], "Cells, then tissue, then organ."),
       ("open", "Describe two ways in which a root hair cell is adapted for absorbing water.", "It has a long thin extension that gives a large surface area, and a thin wall; it has a large vacuole with a concentrated cell sap, helping water to enter by osmosis.", ["1 mark each, maximum 2"]),
       ("prob", "Arrange the following: organ, cell, organism, tissue, system. Then use the heart as an example.", [("mcq", "The correct order from smallest to largest is:", "cell, tissue, organ, system, organism", ["organ, cell, system, tissue, organism", "cell, organ, tissue, system, organism", "tissue, cell, organ, organism, system"], "Levels of organisation."), ("mcq", "In the human body the heart is classed as an:", "organ", ["tissue", "cell", "organism"], "It has muscle and other tissues."), ("open", "Name the system that the heart belongs to and one other organ in that system.", "The circulatory system; the blood vessels (arteries, veins) are also part of it.", ["1 mark: circulatory system", "1 mark: another correct part"], 2)])],
      [("The organelle that releases energy for the cell is the:", "mitochondrion", ["chloroplast", "nucleus", "vacuole"], "Respiration happens there."),
       ("Which of these structures would be missing from a human cheek cell?", "chloroplast", ["nucleus", "mitochondrion", "cell membrane"], "For photosynthesis."),
       ("A group of different tissues working together is an:", "organ", ["organelle", "organism", "atom"], "For example the stomach."),
       ("Red blood cells contain the pigment:", "haemoglobin", ["chlorophyll", "melanin", "iodine"], "It carries oxygen."),
       ("The permanent vacuole of a plant cell contains:", "cell sap", ["DNA", "chlorophyll", "blood"], "Water with sugars and salts.")],
      ["Some syllabuses introduce organelles (ribosomes, endoplasmic reticulum) only later; check the level. Bacterial cell mention is minimal."])

# =========================================================== 2 Classification
ch = p.chapter("classification", "Living things and their classification", "Form 3 Biology — Characteristics of living things, classification (to be checked)")
def key_fig():
    d = Draw()
    qs = ["Has feathers?", "Has hair or fur?", "Has fins and gills?"]
    leaves = ["bird (hen)", "mammal (goat)", "fish (tilapia)"]
    for i, (q, lf) in enumerate(zip(qs, leaves)):
        y = 12 + i * 70
        d.sh.append(RECT(20, y, 150, 34, fill="lightyellow", width=1.5, radius=6)); d.text(95, y + 22, q, size=12)
        d.line(170, y + 17, 270, y + 17, arrow="end", width=1.5); d.text(220, y + 12, "yes", size=11)
        d.sh.append(RECT(272, y, 130, 34, fill="lightgreen", width=1.5, radius=6)); d.text(337, y + 22, lf, size=12)
        d.line(95, y + 34, 95, y + 70, arrow="end", width=1.5); d.text(111, y + 56, "no", size=11, anchor="start")
    d.sh.append(RECT(20, 222, 150, 34, fill="lightgreen", width=1.5, radius=6)); d.text(95, 244, "amphibian (frog)", size=12)
    return d.fig(430, 270)
vgrid = grid(["Fish|gills, scales, fins", "Amphibians|moist skin, eggs in water", "Reptiles|dry scaly skin, eggs on land", "Birds|feathers, wings, beak", "Mammals|hair, milk, live young*"], cols=3, bh=56, size=10)
build(ch, "classification", "Classifying living things and using keys", 30,
      ["List the seven characteristics of living things.", "Describe the five kingdoms and the vertebrate groups.", "Use and construct a dichotomous key and write scientific names correctly."],
      [("key", "definition", "Living things and kingdoms",
        "All living things show **MRS GREN**: movement, respiration, sensitivity, growth, reproduction, excretion, nutrition. **Classification** puts organisms into groups. The **five kingdoms**: **animals** (many cells, no walls, feed on others), **plants** (green, cell walls, photosynthesis), **fungi** (mushrooms, moulds, yeast: feed on dead matter with threads called hyphae), **protoctists** (mostly single-celled, e.g. *Amoeba*, the malaria parasite) and **prokaryotes** (bacteria, no nucleus)."),
       ("fig", vgrid, "The five groups of vertebrates (*most mammals give birth to live young).", "Five boxes: fish, amphibians, reptiles, birds and mammals with key features of each."),
       ("key", "retenir", "Vertebrates, invertebrates and names",
        "**Vertebrates** have a backbone: fish, amphibians, reptiles, birds, mammals. **Invertebrates** do not: **insects** (6 legs, 3 body parts, usually wings), **arachnids** (8 legs, 2 body parts), **crustaceans** (crabs, prawns), **molluscs** (snails), **annelids** (earthworms), **myriapods** (centipedes, millipedes). Each species has a two-part **scientific name** in italics (genus then species, e.g. *Homo sapiens*), used worldwide."),
       ("key", "methode", "A dichotomous key",
        "A **dichotomous key** is a series of paired questions with only two answers (yes or no); each answer leads to the next question or to a name. Make one by choosing clear **visible features** (feathers, hair, fins), starting with the feature that separates the most organisms. Features such as size or colour are poor choices because they vary."),
       ("fig", key_fig(), "A key to four animals.", "A flow chart: has feathers, yes bird (hen); no, has hair or fur, yes mammal (goat); no, has fins and gills, yes fish (tilapia); no, amphibian (frog)."),
       ("key", "pieges", "Common mistakes",
        "- A bat is a **mammal** (hair, milk) even though it flies; a whale is a mammal, not a fish.\n- A spider is not an insect: it has 8 legs.\n- Scientific names: genus has a capital letter, species is lower case, both are underlined or in italics."),
       ("example", "Use the key", "Use the key in the figure to name an animal that has no feathers and has fur.", ["Question 1 (feathers?): no, go to question 2.", "Question 2 (hair or fur?): yes, it is a mammal (goat)."], "A mammal (goat)"),
       ("example", "Group the animal", "An animal has 8 legs and two body parts. Which group is it and is it a vertebrate?", ["Eight legs and two body parts are the features of an arachnid.", "It has no backbone so it is an invertebrate."], "An arachnid (invertebrate)")],
      [("mcq", "Which kingdom contains organisms that feed on dead matter using hyphae?", "fungi", ["plants", "animals", "protoctists"], "Moulds and mushrooms."),
       ("match", "Match each animal with its group.", [("tilapia", "fish"), ("frog", "amphibian"), ("lizard", "reptile"), ("parrot", "bird")], "Standard groups."),
       ("tf", "Insects have eight legs.", False, "Insects have six legs; spiders have eight."),
       ("mcq", "Which feature is best to use in a dichotomous key?", "presence of feathers", ["how big the animal is", "colour of the animal", "how fast it runs"], "A clear feature that does not vary."),
       ("open", "Describe three differences between plants and animals.", "Plants make their own food by photosynthesis and animals feed on other organisms; plant cells have cell walls and chloroplasts and animal cells do not; plants do not move from place to place and animals usually do.", ["1 mark each, maximum 3"]),
       ("prob", "A student collects a housefly, a centipede, a crab and a snail.", [("mcq", "Which one is an insect?", "housefly", ["centipede", "crab", "snail"], "Six legs and three body parts."), ("mcq", "Which one is a mollusc?", "snail", ["housefly", "centipede", "crab"], "Soft body, usually with a shell."), ("open", "State one feature that all four share and one that separates the vertebrates from them.", "They all lack a backbone (are invertebrates) and all show MRS GREN; vertebrates have a backbone.", ["1 mark: a shared feature", "1 mark: backbone"], 2)])],
      [("The 'G' in MRS GREN stands for:", "growth", ["gills", "genes", "gravity"], "Growth."),
       ("The first part of a scientific name is the:", "genus", ["species", "family", "kingdom"], "Binomial naming."),
       ("Fish, birds and mammals all belong to the group called:", "vertebrates", ["invertebrates", "protoctists", "fungi"], "They have a backbone."),
       ("Bacteria belong to the kingdom of:", "prokaryotes", ["fungi", "plants", "animals"], "No nucleus."),
       ("A dichotomous key uses questions that have:", "two possible answers", ["three answers", "no answers", "ten answers"], "Yes or no.")],
      ["Five-kingdom scheme as in common school texts (some courses use three domains). Check whether annelids and myriapods are required at this level."])

# =========================================================== 3 Nutrition
ch = p.chapter("nutrition", "Nutrition in plants and humans", "Form 3 Biology — Photosynthesis, diet and digestion (to be checked)")
def leaf_fig():
    d = Draw()
    layers = [("waxy cuticle and upper epidermis", "lightyellow", 20, 12), ("palisade mesophyll (many chloroplasts)", "lightgreen", 32, 46), ("spongy mesophyll (air spaces)", "lightgreen", 78, 40), ("lower epidermis with stomata", "lightyellow", 118, 12)]
    d.sh.append(RECT(30, 20, 220, 12, fill="lightyellow", width=1.5)); d.sh.append(RECT(30, 32, 220, 46, fill="lightgreen", width=1.5))
    d.sh.append(RECT(30, 78, 220, 40, fill="lightgreen", width=1.5)); d.sh.append(RECT(30, 118, 220, 12, fill="lightyellow", width=1.5))
    for x in range(40, 250, 18): d.sh.append(RECT(x, 36, 10, 36, fill="green", width=1))
    d.sh.append(CIRCLE(140, 98, 12, fill="lightblue", width=1.5))
    d.sh.append(RECT(100, 118, 30, 12, fill="white", stroke="ink", width=1.5)); d.text(115, 150, "stomata", size=11)
    d.line(115, 134, 115, 144, width=1, color="grey")
    d.sh += lab(250, 26, 270, 24, "cuticle and epidermis", size=11)
    d.sh += lab(250, 54, 270, 54, "palisade cells", size=11)
    d.sh += lab(250, 98, 270, 90, "spongy mesophyll", size=11)
    d.sh += lab(152, 98, 270, 120, "vein (xylem, phloem)", size=11)
    d.sh += lab(250, 124, 270, 150, "lower epidermis", size=11)
    d.text(140, 14, "sunlight →", size=11)
    return d.fig(430, 170)
assert E("6CO2 + 6H2O -> C6H12O6 + 6O2") and E("C6H12O6 + 6O2 -> 6CO2 + 6H2O")
assert (10 / 20) ** 2 == 0.25 and 40 * 0.25 == 10
light = plot(0, 120, 0, 12, curves=[("10*(1-exp(-x/30))", "green")], points=[(100, 9.6, "plateau", "red")], grid=20, xlabel="light intensity", ylabel="rate", w=420, h=250)
build(ch, "photosynthesis", "Photosynthesis and the leaf", 30,
      ["Write the word and balanced symbol equations for photosynthesis.", "Describe the structure of a leaf and how it is adapted for photosynthesis.", "Explain the effect of light, carbon dioxide and temperature on the rate and describe the starch test."],
      [("key", "formule", "Photosynthesis",
        "**Photosynthesis** is the process by which green plants make **glucose** from **carbon dioxide** and **water** using **light energy** absorbed by **chlorophyll** in chloroplasts: **carbon dioxide + water → glucose + oxygen**, or " + E("6CO2 + 6H2O -> C6H12O6 + 6O2") + ". Light energy is changed into chemical energy stored in glucose. The glucose is used for respiration, stored as **starch**, made into **cellulose** for cell walls, and with nitrates makes **amino acids and proteins**."),
       ("fig", leaf_fig(), "Cross-section of a leaf.", "A leaf section showing the cuticle and upper epidermis, palisade cells with many chloroplasts, spongy mesophyll with air spaces, a vein and the lower epidermis with a stoma."),
       ("key", "retenir", "Leaf adaptations and minerals",
        "The leaf is **thin and flat** (large area, short diffusion path), has **palisade cells** packed with chloroplasts near the light, **air spaces** in the spongy layer for gases, **stomata** with **guard cells** for gas exchange, and **veins**: **xylem** brings water and minerals, **phloem** carries sugars away. Plants also need **nitrate** (for proteins) and **magnesium** (to make chlorophyll) from the soil; shortage of nitrate stunts growth and of magnesium makes leaves yellow."),
       ("fig", light, "Rate of photosynthesis against light intensity (schematic).", "A curve that rises steeply and then levels off, with the plateau labelled."),
       ("key", "methode", "Limiting factors and the starch test",
        "The rate is limited by the factor in **shortest supply**: **light intensity**, **carbon dioxide concentration** or **temperature** (enzymes work best at about 25–35 °C). Once light is no longer limiting, the curve **levels off** (CO₂ or temperature limits). **Starch test**: dip the leaf in boiling water, boil it in ethanol in a hot water bath to remove chlorophyll, rinse, add iodine: **blue-black** means starch."),
       ("key", "pieges", "Common mistakes",
        "- Plants do respire all the time; they carry out photosynthesis only in the light. Both use gases.\n- Never heat ethanol over a flame: use a hot water bath.\n- Oxygen is a **product**, not a raw material."),
       ("example", "Pondweed bubbles", "A lamp is 10 cm from a pondweed and it gives off 40 bubbles per minute. Light intensity is proportional to 1 ÷ (distance)². Predict the rate at 20 cm if the rate is proportional to the light intensity.", ["Doubling the distance makes the intensity (1/2)² = 1/4.", "Rate = 40 × 1/4 = 10 bubbles per minute (illustrative)."], "About 10 bubbles per minute"),
       ("example", "Interpret the starch test", "A variegated leaf with green and white parts is tested with iodine after a sunny day. Only the green parts turn blue-black. What does this show?", ["Green parts contain chlorophyll and make starch.", "White parts have no chlorophyll: chlorophyll is needed for photosynthesis."], "Chlorophyll is needed for photosynthesis.")],
      [("mcq", "Which gas is a product of photosynthesis?", "oxygen", ["carbon dioxide", "nitrogen", "hydrogen"], "Oxygen is released."),
       ("mcq", "Which cells in a leaf contain the most chloroplasts?", "palisade cells", ["guard cells only", "root cells", "xylem vessels"], "Near the upper surface."),
       ("tf", "Plants obtain most of their mass from the soil.", False, "Most of the mass comes from carbon dioxide (and water)."),
       ("mcq", "Magnesium is needed by plants to make:", "chlorophyll", ["starch", "cellulose", "iodine"], "Its shortage makes leaves yellow."),
       ("open", "Explain two ways in which a leaf is adapted for photosynthesis.", "It is thin and flat with a large surface area to absorb light, and short diffusion distance for gases; it has many chloroplasts in palisade cells; stomata let carbon dioxide in; veins bring water.", ["1 mark each, maximum 2"]),
       ("prob", "In an experiment on a pondweed, bubbles are counted at different light levels. The rate rises with light up to a point, then stays constant even if the lamp is brought closer.", [("mcq", "A likely limiting factor at the plateau is:", "carbon dioxide concentration or temperature", ["light intensity", "the colour of the lamp", "the size of the beaker"], "Light is no longer limiting."), ("mcq", "To increase the rate further, the student could:", "add sodium hydrogencarbonate to the water", ["bring the lamp even closer", "use a bigger beaker", "keep it in darkness"], "It supplies carbon dioxide."), ("open", "Why is a glass tank of water placed between the lamp and the pondweed?", "To absorb the heat from the lamp so that the temperature stays constant and only light intensity changes.", ["1 mark for keeping the temperature constant"], 2)])],
      [("Photosynthesis takes place mainly in the:", "chloroplasts", ["mitochondria", "nucleus", "vacuole"], "They contain chlorophyll."),
       ("Which of these is NOT needed for photosynthesis?", "oxygen", ["light", "water", "carbon dioxide"], "Oxygen is released."),
       ("Carbon dioxide enters the leaf through the:", "stomata", ["xylem", "cuticle", "root"], "Open pores."),
       ("In the starch test the leaf is boiled in ethanol to:", "remove the chlorophyll", ["make starch", "cool the leaf", "add iodine"], "So that the colour change is visible."),
       ("Plants store glucose mainly as:", "starch", ["protein", "chlorophyll", "water"], "Insoluble store.")],
      ["Light intensity ∝ 1/d² is the inverse-square law (assumed for a point source); the bubble numbers are illustrative. Photosynthesis rate curves are schematic."])

energy_bars = bars([("carbohydrate", 17, "orange"), ("protein", 17, "red"), ("fat", 37, "yellow")], w=400, h=240)
Em = 60 * 17 + 20 * 17 + 25 * 37; assert Em == 2285
build(ch, "diet", "Food, diet and deficiency diseases", 30,
      ["List the food classes, their sources and their functions.", "Describe the laboratory tests for starch, reducing sugars, protein and fat.", "Explain a balanced diet and name deficiency diseases and their causes."],
      [("key", "retenir", "Food classes",
        "- **Carbohydrates** (cassava, yam, rice, plantain): energy.\n- **Proteins** (beans, fish, eggs, meat, groundnuts): growth and repair.\n- **Fats and oils** (palm oil, groundnuts): energy store, insulation, protect organs.\n- **Vitamins** (fruit, vegetables) and **minerals** (iron in meat and leaves, calcium in milk and fish): health.\n- **Fibre**: moves food through the gut.\n- **Water**: solvent, transport, cools the body."),
       ("fig", energy_bars, "Energy released per gram of food class (kJ/g).", "A bar chart: carbohydrate 17, protein 17 and fat 37 kilojoules per gram."),
       ("key", "methode", "Food tests",
        "- **Starch**: add iodine solution; **blue-black**.\n- **Reducing sugar**: heat with **Benedict's solution**; blue → green → yellow → **brick-red**.\n- **Protein**: **biuret** test: sodium hydroxide then a little copper sulphate; **purple/violet**.\n- **Fat**: rub on paper (translucent stain) or shake with ethanol and add water: **milky white emulsion**."),
       ("key", "retenir", "Balanced diet and deficiency",
        "A **balanced diet** has all the food classes in the right amounts for age, sex and activity. **Kwashiorkor** (protein shortage: swollen belly), **marasmus** (shortage of energy and protein), **scurvy** (vitamin C), **rickets** (vitamin D or calcium: soft bones), **anaemia** (iron) and **goitre** (iodine) are deficiency conditions. Too much energy food leads to **obesity**."),
       ("key", "pieges", "Common mistakes",
        "- Benedict's solution is heated, iodine is not.\n- The biuret test is for protein, not for sugar.\n- Fibre and water give no energy but are essential."),
       ("example", "Energy in a meal", "A meal has 60 g carbohydrate, 20 g protein and 25 g fat. Use 17 kJ/g for carbohydrate and protein and 37 kJ/g for fat. Find the total energy.", ["Carbohydrate 60 × 17 = 1020 kJ; protein 20 × 17 = 340 kJ; fat 25 × 37 = 925 kJ.", "Total = 1020 + 340 + 925 = 2285 kJ."], "2285 kJ"),
       ("example", "Which test?", "A food sample turns brick-red after heating with Benedict's solution. What does it contain?", ["Benedict's solution tests for reducing sugars.", "Brick-red is the strong positive result."], "A reducing sugar (e.g. glucose).")],
      [("mcq", "In a plate of boiled cassava, the food class that gives most of the energy is:", "carbohydrate", ["protein", "fat", "fibre"], "Cassava is starchy."),
       ("match", "Match each test with its positive result.", [("iodine on starch", "blue-black"), ("Benedict's with glucose (heated)", "brick-red"), ("biuret with protein", "purple"), ("ethanol emulsion with fat", "milky white")], "Standard results."),
       ("num", "How much energy is in 40 g of fat at 37 kJ/g (kJ)?", 1480, "40 × 37 = 1480 kJ.", 0, "kJ"),
       ("mcq", "A child with a swollen belly and thin limbs may be lacking:", "protein", ["vitamin C", "iron", "fibre"], "Kwashiorkor."),
       ("open", "Explain why a person who eats only cassava is at risk of a deficiency disease.", "Cassava is mostly carbohydrate and lacks enough protein, vitamins and some minerals; a lack of protein can cause kwashiorkor and a lack of other nutrients other deficiency diseases. A varied diet is needed.", ["1 mark: cassava is mostly carbohydrate", "1 mark: lack of protein or other nutrients causes disease"]),
       ("prob", "A pupil tests a food sample: iodine gives no change; Benedict's gives no change after heating; biuret gives purple; the paper test leaves a translucent mark.", [("mcq", "The sample contains protein and:", "fat", ["starch", "reducing sugar", "no other food class"], "Translucent mark means fat."), ("mcq", "The sample is most likely:", "groundnuts", ["cassava flour", "sugar cane juice", "salt"], "Rich in protein and fat."), ("open", "State what the results show about starch and sugar.", "No starch and no reducing sugar are present.", ["1 mark for both"], 2)])],
      [("Proteins are needed mainly for:", "growth and repair", ["energy only", "colouring food", "making bile"], "Building tissues."),
       ("The test for starch uses:", "iodine solution", ["Benedict's solution", "limewater", "ethanol only"], "Blue-black."),
       ("Scurvy is caused by a shortage of:", "vitamin C", ["vitamin D", "iron", "calcium"], "Fruit prevents it."),
       ("Fat provides how much energy per gram compared with carbohydrate?", "about twice as much", ["half as much", "the same", "none"], "37 kJ against 17 kJ."),
       ("A balanced diet contains:", "all food classes in the right amounts", ["only protein", "no fat", "only fruit"], "Variety.")],
      ["Energy values 17 and 37 kJ/g are the usual rounded figures. Deficiency diseases and their causes are standard; health content to be reviewed by a health worker. Content overlaps with form4-biology (food classes) in simplified form."])

enz_t = plot(0, 70, 0, 10, segments=[(0, 0, 10, 1, "blue", False), (10, 1, 20, 3, "blue", False), (20, 3, 30, 6.5, "blue", False), (30, 6.5, 37, 10, "blue", False), (37, 10, 45, 6, "red", False), (45, 6, 55, 1, "red", False), (55, 1, 60, 0, "red", False)],
                points=[(37, 10, "optimum", "red")], grid=10, xlabel="temperature (°C)", ylabel="enzyme activity", w=420, h=250)
build(ch, "digestion", "Digestion and enzymes", 30,
      ["Describe the parts of the alimentary canal and their functions.", "Name the main digestive enzymes, substrates and products.", "Explain the effects of temperature and pH on enzymes and the role of bile and the villi."],
      [("key", "definition", "Digestion",
        "**Digestion** is the breakdown of large insoluble food molecules into small soluble ones that can be absorbed. **Physical digestion**: chewing and churning. **Chemical digestion**: **enzymes**, which are **biological catalysts made of protein**. Path: **mouth** (teeth, saliva) → **oesophagus** (peristalsis) → **stomach** (acid, churning) → **small intestine** (digestion and absorption) → **large intestine** (water absorbed) → **rectum** → **anus** (egestion)."),
       ("key", "retenir", "Enzymes and what they do",
        "**Amylase** (saliva, pancreas): starch → sugars (maltose, then glucose). **Protease** (stomach, pancreas, small intestine): proteins → **amino acids**. **Lipase** (pancreas, small intestine): fats → **fatty acids and glycerol**. **Bile**, made in the **liver** and stored in the **gall bladder**, is not an enzyme: it **emulsifies fat** into tiny droplets and neutralises stomach acid. The stomach acid (hydrochloric acid) kills germs and gives the right pH for protease."),
       ("fig", enz_t, "Enzyme activity against temperature (schematic).", "A curve that rises to a peak at 37 degrees Celsius, the optimum, then falls to zero as the enzyme is destroyed at high temperature."),
       ("key", "retenir", "Absorption, enzymes and conditions",
        "The small intestine is **long**, with a wall folded into **villi** that have a large surface area, thin walls and a network of **capillaries**, so digested food passes quickly into the blood. Enzymes work best at their **optimum temperature** (about 37 °C in humans) and **pH**; high temperatures **denature** them (their shape is destroyed permanently); low temperatures slow them but do not destroy them. Stomach protease prefers acid; amylase in saliva prefers a nearly neutral pH."),
       ("key", "pieges", "Common mistakes",
        "- Bile is **not** an enzyme and does not digest fat chemically; it breaks fat into droplets.\n- Enzymes are **denatured**, not 'killed', by heat: they are not alive.\n- Absorption is not digestion: digestion breaks food down, absorption moves it into the blood."),
       ("example", "Which enzyme?", "Name the enzyme and the product when egg white (protein) is digested in the stomach.", ["The enzyme that digests protein is protease (pepsin).", "The final products are amino acids."], "Protease; amino acids"),
       ("example", "Explain the graph", "Using the graph, explain why enzyme activity falls above 40 °C.", ["High temperature changes the shape of the enzyme (denatures it).", "The substrate no longer fits the active site, so the reaction stops."], "The enzyme is denatured.")],
      [("mcq", "Which enzyme digests starch?", "amylase", ["protease", "lipase", "bile"], "Amylase."),
       ("match", "Match each organ with its digestive role.", [("liver", "makes bile"), ("stomach", "churns food with acid"), ("small intestine", "absorbs digested food"), ("large intestine", "absorbs water")], "Standard roles."),
       ("tf", "Bile contains the enzyme lipase.", False, "Bile is not an enzyme; it emulsifies fat."),
       ("mcq", "Villi increase absorption because they have:", "a large surface area", ["a thick wall", "no blood supply", "acid inside"], "More area for absorption."),
       ("open", "Explain why enzymes work slowly at 5 °C and not at all after boiling.", "At 5 °C the molecules move slowly so there are few collisions between enzyme and substrate; boiling denatures the enzyme by changing the shape of its active site permanently.", ["1 mark: slow because of few collisions", "1 mark: boiling denatures it"]),
       ("prob", "A student mixes starch solution with saliva at 37 °C and tests samples with iodine every minute.", [("mcq", "At the start the iodine turns:", "blue-black", ["brown", "red", "colourless"], "Starch is present."), ("mcq", "Later the iodine stays brown because:", "starch has been digested by amylase", ["the saliva is cold", "the enzyme is made of starch", "iodine is used up"], "Amylase changes starch into sugar."), ("open", "How could she show that the saliva does not work at 90 °C?", "Repeat the experiment with the mixture kept at 90 °C and show that iodine still turns blue-black, as the enzyme is denatured.", ["1 mark: repeat at 90 °C", "1 mark: starch not digested"], 2)])],
      [("The products of protein digestion are:", "amino acids", ["fatty acids", "glucose", "glycerol"], "Protease."),
       ("Enzymes are made of:", "protein", ["starch", "fat", "minerals"], "Biological catalysts."),
       ("Bile is made in the:", "liver", ["stomach", "pancreas", "kidney"], "Stored in the gall bladder."),
       ("The optimum temperature for human enzymes is about:", "37 °C", ["0 °C", "100 °C", "10 °C"], "Body temperature."),
       ("Most of the water from undigested food is reabsorbed in the:", "large intestine", ["mouth", "stomach", "oesophagus"], "Some also in the small intestine.")],
      ["Enzyme activity curve is schematic. 'Bile emulsifies fat' and enzyme locations are standard textbook statements; check whether the syllabus names maltase."])

# =========================================================== 4 Transport
ch = p.chapter("transport", "Movement of substances and transport", "Form 3 Biology — Diffusion, osmosis, blood and circulation (to be checked)")
sav = lambda s: (6 * s * s, s ** 3, 6 * s * s / s ** 3)
assert sav(1) == (6, 1, 6.0) and sav(2) == (24, 8, 3.0) and sav(3)[2] == 2.0
pot = [(0.0, 5.0, 5.6), (0.2, 5.0, 5.3), (0.4, 5.0, 5.0), (0.6, 5.0, 4.7), (0.8, 5.0, 4.4)]
pct = [(c, round((f - i) / i * 100, 1)) for c, i, f in pot]
assert pct == [(0.0, 12.0), (0.2, 6.0), (0.4, 0.0), (0.6, -6.0), (0.8, -12.0)]
potg = plot(0, 0.8, -15, 15, segments=[(pct[k][0], pct[k][1], pct[k + 1][0], pct[k + 1][1], "blue", False) for k in range(4)],
            points=[(c, v, None, "red") for c, v in pct], grid=0.2, xlabel="sugar (mol/dm³)", ylabel="mass change (%)", w=420, h=260)
build(ch, "diffusion-osmosis", "Diffusion, osmosis and active transport", 30,
      ["Define diffusion, osmosis and active transport and give examples.", "Explain the effect of surface area to volume ratio and the factors affecting diffusion.", "Interpret a potato osmosis experiment and its effect on plant and animal cells."],
      [("key", "definition", "Diffusion",
        "**Diffusion** is the **net movement of particles from a region of higher concentration to a region of lower concentration**, down a concentration gradient, because of their random movement. No energy from the cell is needed. Examples: oxygen from the alveoli into the blood; carbon dioxide into a leaf; digested food into the blood. Diffusion is faster with a **steeper gradient**, a **larger surface area**, a **higher temperature** and a **shorter distance**."),
       ("key", "formule", "Surface area to volume ratio",
        "Small objects have a large surface area compared with their volume. For a cube of side s: surface area = 6s², volume = s³, ratio = 6 ÷ s. A 1 cm cube has a ratio of **6**, a 2 cm cube **3**. Single-celled organisms can get all the oxygen they need by diffusion, but large organisms have a small ratio and need **special exchange surfaces** (lungs, villi) and **transport systems**."),
       ("key", "definition", "Osmosis and active transport",
        "**Osmosis** is the **net movement of water molecules from a dilute solution (high water potential) to a more concentrated solution (low water potential) through a partially permeable membrane**. A plant cell in pure water takes in water and becomes **turgid** (firm, supported by the cell wall); in a strong solution it loses water, becomes **flaccid** and may be **plasmolysed**. An animal cell in pure water **bursts**; in strong solution it **shrinks**. **Active transport** moves substances **against** a concentration gradient and **needs energy from respiration**: root hair cells take up mineral ions this way."),
       ("fig", potg, "Percentage change in mass of potato strips in sugar solutions of different concentration.", "A graph with sugar concentration on the x-axis and percentage change in mass on the y-axis: a falling line from plus 12 percent at zero to minus 12 percent at 0.8 mol per dm cubed, crossing zero at 0.4."),
       ("key", "pieges", "Common mistakes",
        "- Osmosis is about **water** moving, not the sugar or salt.\n- Water moves from the **dilute** to the **concentrated** solution.\n- Diffusion needs no energy; **active transport** does.\n- The line crosses zero where the solution has the same concentration as the potato cell sap (here 0.4 mol/dm³)."),
       ("example", "Percentage change in mass", "A potato strip of mass 5.0 g has a mass of 5.6 g after 30 minutes in pure water. Find the percentage change.", ["Change = 5.6 − 5.0 = 0.6 g.", "0.6 ÷ 5.0 × 100 = +12 %: it gained water."], "+12 %"),
       ("example", "Find the concentration of the cell sap", "Using the graph, estimate the concentration of the potato cell sap and explain.", ["The mass does not change where the line crosses zero, at 0.4 mol/dm³.", "There is no net movement of water, so the solution and the cell sap have equal concentration."], "About 0.4 mol/dm³")],
      [("mcq", "Diffusion is the net movement of particles from a region of:", "high to low concentration", ["low to high concentration", "cold to hot", "large to small"], "Down a concentration gradient."),
       ("match", "Match each term with its meaning.", [("osmosis", "movement of water across a partially permeable membrane"), ("active transport", "movement against a gradient using energy"), ("turgid", "a plant cell full of water"), ("plasmolysis", "membrane pulling away from the cell wall")], "Standard definitions."),
       ("num", "Find the surface area to volume ratio of a cube of side 3 cm.", 2, "SA = 54; V = 27; 54 ÷ 27 = 2.", 0),
       ("num", "A potato strip drops from 4.0 g to 3.6 g in strong salt solution. Find the percentage change in mass.", -10, "(3.6 − 4.0) ÷ 4.0 × 100 = −10 %.", 0.1, "%"),
       ("open", "Explain why red blood cells burst if placed in pure water but plant cells do not.", "Water enters by osmosis. An animal cell has no cell wall, so the membrane bursts; a plant cell has a strong cell wall which stops it bursting, so it becomes turgid.", ["1 mark: water enters by osmosis", "1 mark: animal cell has no wall / plant cell wall resists"]),
       ("prob", "Equal strips of potato are placed in sugar solutions of 0.0, 0.2, 0.4, 0.6 and 0.8 mol/dm³. Mass changes: +12 %, +6 %, 0 %, −6 %, −12 %.", [("mcq", "In which solution is there no net movement of water?", "0.4 mol/dm³", ["0.0 mol/dm³", "0.8 mol/dm³", "0.2 mol/dm³"], "No change in mass."), ("mcq", "The strip in 0.8 mol/dm³ became:", "flaccid", ["turgid", "larger", "harder"], "It lost water."), ("open", "Name one way to make the results reliable.", "Use several strips for each solution and average the results; dry each strip the same way before weighing; keep the temperature and times the same.", ["1 mark for a sensible control or repeat"], 2)])],
      [("Water moves in osmosis through a:", "partially permeable membrane", ["solid wall", "waterproof sheet", "metal plate"], "Allows water through but not some solutes."),
       ("An animal cell placed in pure water will:", "swell and may burst", ["shrink", "stay the same", "photosynthesise"], "No cell wall."),
       ("Which of these is needed for active transport but not for diffusion?", "energy from respiration", ["a steep concentration gradient", "a cell wall", "light"], "Moves against the gradient."),
       ("Which increases the rate of diffusion?", "a steeper concentration gradient", ["a thicker membrane", "a lower temperature", "a smaller surface area"], "Bigger gradient."),
       ("A turgid plant cell is:", "firm and full of water", ["limp and shrunken", "dead", "without a wall"], "It supports the plant.")],
      ["Water potential is introduced in simple terms ('dilute' and 'concentrated'); check whether the term is needed at Form 3. The potato results are a model data set, not measured values."])

def heart_fig():
    d = Draw()
    for x, y, t, c in ((130, 70, "right atrium", "lightblue"), (230, 70, "left atrium", "lightorange"), (130, 120, "right ventricle", "lightblue"), (230, 120, "left ventricle", "lightorange")):
        d.sh.append(RECT(x, y, 95, 45, fill=c, width=2)); d.text(x + 47, y + 27, t, size=10)
    d.line(30, 90, 128, 90, arrow="end", color="blue", width=2); d.text(75, 82, "from body", size=10)
    d.line(128, 150, 70, 190, arrow="end", color="blue", width=2); d.text(68, 205, "to lungs", size=10)
    d.line(300, 190, 280, 168, arrow="end", color="red", width=2); d.text(325, 205, "from lungs", size=10)
    d.line(300, 118, 395, 100, arrow="end", color="red", width=2); d.text(375, 130, "to body", size=10)
    d.text(180, 20, "blue: low oxygen   orange: high oxygen", size=10)
    return d.fig(440, 218)
sv = 70 * 72; assert sv == 5040
build(ch, "blood-circulation", "Blood and the circulatory system", 30,
      ["Describe the components of blood and their functions.", "Describe the structure of the heart and the double circulation.", "Compare arteries, veins and capillaries and calculate heart output."],
      [("key", "retenir", "Blood",
        "**Red blood cells** carry oxygen as **oxyhaemoglobin**. **White blood cells** defend the body: some **engulf** germs (phagocytes), others make **antibodies** (lymphocytes). **Platelets** help the blood to **clot**. **Plasma** carries digested food, carbon dioxide, urea, hormones and heat. A clot over a wound keeps germs out and stops bleeding."),
       ("fig", heart_fig(), "The heart in outline: the right side pumps blood to the lungs, the left side to the body.", "A heart with four chambers: right atrium and right ventricle in blue, left atrium and left ventricle in orange; arrows for blood from the body to the right atrium, from the right ventricle to the lungs, from the lungs to the left atrium and from the left ventricle to the body."),
       ("key", "retenir", "The heart and double circulation",
        "The heart is a **muscular pump** with four chambers: two **atria** (receive blood) and two **ventricles** (pump blood out); **valves** stop backflow. The **right side** receives low-oxygen blood from the body (vena cava) and sends it to the **lungs** (pulmonary artery). The **left side** receives oxygenated blood from the lungs and pumps it to the **body** (aorta); its wall is **thicker** because it pumps further. Blood passes through the heart **twice** in each complete circuit: a **double circulation**."),
       ("key", "retenir", "Blood vessels",
        "**Arteries**: carry blood **away** from the heart; thick, muscular, elastic walls for high pressure; a **pulse** can be felt. **Veins**: carry blood **to** the heart; thinner walls, wide space, **valves**. **Capillaries**: tiny, walls one cell thick, where substances are exchanged between blood and cells. A healthy lifestyle (exercise, no smoking, a balanced diet) reduces the risk of heart disease."),
       ("key", "pieges", "Common mistakes",
        "- Not all arteries carry oxygenated blood: the pulmonary artery carries low-oxygen blood to the lungs.\n- Veins have valves; arteries do not (except at the heart exits).\n- The left of the diagram is the person's right side."),
       ("example", "Cardiac output", "The heart pumps 70 mL of blood with each beat and beats 72 times per minute. Find the volume pumped per minute in mL and in litres.", ["70 × 72 = 5040 mL per minute.", "5040 ÷ 1000 = 5.04 litres per minute."], "5040 mL (about 5 litres) per minute"),
       ("example", "Why is the left ventricle thicker?", "Explain why the left ventricle has a thicker wall than the right.", ["The left ventricle pumps blood all round the body, a long way against resistance.", "The right ventricle pumps only to the nearby lungs."], "It must produce higher pressure to reach the whole body.")],
      [("mcq", "Which blood cells carry oxygen?", "red blood cells", ["white blood cells", "platelets", "plasma"], "They contain haemoglobin."),
       ("match", "Match each vessel with a feature.", [("artery", "thick elastic wall, high pressure"), ("vein", "valves to prevent backflow"), ("capillary", "wall one cell thick"), ("aorta", "carries oxygenated blood from the left ventricle")], "Standard features."),
       ("tf", "The pulmonary artery carries oxygenated blood.", False, "It carries blood from the heart to the lungs, low in oxygen."),
       ("num", "A heart beats 80 times per minute and pumps 60 mL each beat. Find the volume pumped per minute in mL.", 4800, "80 × 60 = 4800 mL.", 0, "mL"),
       ("open", "Describe two ways in which a capillary is adapted for exchange of substances.", "Its wall is only one cell thick so the diffusion distance is short; it is very narrow and has a large total surface area close to every cell; it has small gaps in the wall.", ["1 mark each, maximum 2"]),
       ("prob", "A student counts a pulse of 18 beats in 15 seconds at rest and 33 beats in 15 seconds after running.", [("num", "Resting pulse rate per minute.", 72, "18 × 4 = 72.", 0), ("num", "Pulse rate after exercise per minute.", 132, "33 × 4 = 132.", 0), ("open", "Explain why the pulse rate increases during exercise.", "The muscles need more oxygen and glucose for respiration and carbon dioxide must be removed faster, so the heart beats faster to deliver more blood.", ["1 mark: muscles need more oxygen/glucose", "1 mark: heart beats faster to supply it"], 2)])],
      [("A clot that seals a wound forms with the help of:", "platelets", ["red blood cells", "digestive enzymes", "limewater"], "They help seal wounds."),
       ("The purpose of the valves found in veins is to:", "prevent backflow of blood", ["carry oxygen", "make blood", "increase pressure"], "Keep blood moving one way."),
       ("Which chamber pumps blood to the whole body?", "left ventricle", ["right atrium", "right ventricle", "left atrium"], "Thick muscular wall."),
       ("Substances are exchanged between blood and body cells in the:", "capillaries", ["arteries", "veins", "valves"], "Thin walls."),
       ("Plasma is the:", "liquid part of the blood", ["red part of the blood", "valve of the heart", "clot"], "Carries dissolved substances.")],
      ["Heart diagram simplified; stroke volume 70 mL and heart rates are typical textbook values used for calculation only. Health advice conservative and to be reviewed by a health worker."])

# =========================================================== 5 Respiration
ch = p.chapter("respiration", "Respiration and gas exchange", "Form 3 Biology — Respiration and breathing (to be checked)")
def alv_fig():
    d = Draw()
    d.sh.append(CIRCLE(160, 105, 62, fill="lightorange", width=2)); d.sh.append(CIRCLE(160, 105, 48, fill="lightblue", width=2))
    d.text(160, 110, "air", size=12)
    d.line(130, 78, 130, 50, arrow="end", color="blue", width=2); d.line(190, 50, 190, 78, arrow="end", color="red", width=2)
    d.text(125, 42, "oxygen in", size=11, anchor="end"); d.text(195, 42, "carbon dioxide out", size=11, anchor="start")
    d.sh += lab(112, 150, 40, 175, "alveolus wall (one cell thick)", size=11, anchor="start")
    d.sh += lab(160, 165, 260, 175, "capillary with blood", size=11)
    return d.fig(440, 200)
assert E("C6H12O6 + 6O2 -> 6CO2 + 6H2O") and E("C6H12O6 -> 2C2H5OH + 2CO2")
mv = 15 * 500 / 1000; mv2 = 30 * 500 / 1000; assert mv == 7.5 and mv2 == 15
build(ch, "respiration", "Respiration, breathing and gas exchange", 30,
      ["Write the equations for aerobic and anaerobic respiration and distinguish them.", "Describe the gas exchange system and breathing movements.", "Compare inhaled and exhaled air and describe the effects of exercise and smoking."],
      [("key", "formule", "Respiration",
        "**Respiration** is the release of energy from food in every living cell. **Aerobic respiration** (with oxygen) takes place in the **mitochondria**: glucose + oxygen → carbon dioxide + water + energy, or " + E("C6H12O6 + 6O2 -> 6CO2 + 6H2O") + " + energy. **Anaerobic respiration** (without oxygen) releases much less energy: in **muscles** glucose → **lactic acid** (causing cramp and an **oxygen debt**); in **yeast** " + E("C6H12O6 -> 2C2H5OH + 2CO2") + " (fermentation). Some of the energy appears as **heat**."),
       ("key", "retenir", "Breathing and gas exchange",
        "Air passes through the **nose** (hairs and mucus filter it), **trachea** (rings of cartilage), **bronchi**, **bronchioles** to the **alveoli**. Alveoli have a **large surface area**, **thin walls**, a **rich blood supply** and a moist lining, so oxygen **diffuses into the blood** and carbon dioxide diffuses out. **Breathing in**: ribs move up and out, diaphragm flattens, volume increases, pressure falls and air flows in. **Breathing out**: the reverse."),
       ("fig", alv_fig(), "Gas exchange at an alveolus.", "An air sac surrounded by a capillary with arrows showing oxygen passing into the blood and carbon dioxide passing out."),
       ("key", "retenir", "Inhaled and exhaled air; exercise",
        "Inhaled air: about 21 % oxygen, 0.04 % carbon dioxide. Exhaled air: about 16 % oxygen, 4 % carbon dioxide, and more water vapour. Exhaled air turns **limewater milky faster** than inhaled air. During **exercise** muscles respire faster, so **breathing rate and depth increase**. Example: 15 breaths of 500 mL each minute give 7.5 L per minute; 30 breaths give 15 L per minute."),
       ("key", "attention", "Smoking and health",
        "Tobacco smoke contains **tar** (causes cancer and damages the lungs), **nicotine** (addictive) and **carbon monoxide** (stops the blood carrying oxygen). Smoking increases the risk of lung cancer, bronchitis and heart disease. The best advice is not to start, and anyone who smokes should seek help from a health worker to stop."),
       ("key", "pieges", "Common mistakes",
        "- **Breathing** (moving air) is not the same as **respiration** (releasing energy in cells).\n- Plants respire all the time, not only at night.\n- Anaerobic respiration releases less energy, not more."),
       ("example", "Breathing volume", "A boy breathes 20 times per minute, taking in 400 mL each breath. Find the volume of air breathed per minute.", ["Volume per minute = 20 × 400 = 8000 mL.", "= 8.0 litres per minute."], "8.0 litres per minute"),
       ("example", "Why do muscles get cramp?", "Explain why a sprinter's muscles may ache after a 100 m race.", ["The muscles respire anaerobically when oxygen cannot be supplied fast enough, making lactic acid.", "Lactic acid causes the ache; the oxygen debt is repaid by breathing hard afterwards."], "Lactic acid from anaerobic respiration.")],
      [("mcq", "Aerobic respiration releases energy from glucose using:", "oxygen", ["carbon dioxide", "nitrogen", "limewater"], "Aerobic means with oxygen."),
       ("match", "Match each part with its function.", [("trachea", "carries air to the bronchi"), ("alveoli", "gas exchange surface"), ("diaphragm", "muscle that flattens to breathe in"), ("ribs", "protect the lungs and move to change chest volume")], "Standard functions."),
       ("tf", "Anaerobic respiration in yeast makes lactic acid.", False, "Yeast makes ethanol and carbon dioxide."),
       ("num", "A girl breathes 12 times per minute and takes in 500 mL each time. Find the volume breathed per minute in mL.", 6000, "12 × 500 = 6000 mL.", 0, "mL"),
       ("open", "Describe three features of the alveoli that make gas exchange efficient.", "A large surface area from millions of alveoli; walls only one cell thick for a short diffusion distance; a rich blood supply of capillaries that keeps a steep concentration gradient; moist surface in which gases dissolve.", ["1 mark each, maximum 3"]),
       ("prob", "A student breathes through a tube into limewater for 30 s at rest and again after exercise. The limewater turns milky faster after exercise.", [("mcq", "This shows that exhaled air after exercise contains more:", "carbon dioxide", ["oxygen", "nitrogen", "hydrogen"], "Muscles respired faster."), ("mcq", "The gas in the limewater test is:", "carbon dioxide", ["oxygen", "nitrogen", "water vapour"], "Limewater turns milky with CO₂."), ("open", "Name two changes in the body during exercise that deliver more oxygen to the muscles.", "Breathing rate and depth increase and the heart beats faster.", ["1 mark each"], 2)])],
      [("Aerobic respiration occurs mostly in the:", "mitochondria", ["nucleus", "vacuole", "cell wall"], "Energy is released there."),
       ("Breathed-out air is shown to contain more carbon dioxide than fresh air by using:", "limewater, which turns milky faster", ["iodine solution", "a glowing splint", "Benedict's solution"], "Calcium carbonate forms."),
       ("When you breathe in the diaphragm:", "flattens", ["relaxes upwards", "disappears", "rests"], "It moves down."),
       ("Yeast respires anaerobically to make:", "ethanol and carbon dioxide", ["lactic acid", "oxygen", "starch"], "Fermentation."),
       ("Carbon monoxide in cigarette smoke:", "reduces the oxygen carried by the blood", ["improves breathing", "kills germs", "makes the lungs bigger"], "It binds haemoglobin.")],
      ["Smoking and health content is conservative and to be reviewed by a health worker. Composition of inhaled and exhaled air uses the usual textbook approximations (21/16 % O₂, 0.04/4 % CO₂)."])

# =========================================================== 6 Reproduction
ch = p.chapter("reproduction", "Reproduction", "Form 3 Biology — Reproduction in flowering plants and humans (to be checked)")
def flower_fig():
    d = Draw()
    d.sh.append(POLY([(190, 190), (160, 183), (185, 172)], fill="green", width=1.5)); d.sh.append(POLY([(210, 190), (240, 183), (215, 172)], fill="green", width=1.5))
    d.sh.append(POLY([(190, 182), (135, 150), (125, 100), (172, 132)], fill="pink", width=1.5)); d.sh.append(POLY([(210, 182), (265, 150), (275, 100), (228, 132)], fill="pink", width=1.5))
    d.line(186, 172, 165, 108, width=2); d.sh.append(CIRCLE(165, 102, 7, fill="yellow", width=1.5))
    d.line(214, 172, 235, 108, width=2); d.sh.append(CIRCLE(235, 102, 7, fill="yellow", width=1.5))
    d.sh.append(CIRCLE(200, 160, 18, fill="lightgreen", width=2)); d.sh.append(CIRCLE(200, 163, 5, fill="yellow", width=1.2))
    d.line(200, 142, 200, 90, width=4, color="green"); d.sh.append(CIRCLE(200, 84, 7, fill="orange", width=1.5))
    d.sh += lab(208, 84, 300, 62, "stigma", size=11); d.sh += lab(203, 115, 300, 100, "style", size=11)
    d.sh += lab(218, 160, 300, 140, "ovary", size=11); d.sh += lab(204, 165, 300, 182, "ovule", size=11)
    d.sh += lab(158, 102, 60, 80, "anther", size=11); d.sh += lab(172, 135, 60, 115, "filament", size=11)
    d.sh += lab(140, 130, 60, 150, "petal", size=11); d.sh += lab(165, 182, 60, 195, "sepal", size=11)
    return d.fig(400, 210)
assert 164 / 200 * 100 == 82
build(ch, "plant-reproduction", "Reproduction in flowering plants", 30,
      ["Name the parts of a flower and state their functions.", "Distinguish pollination from fertilisation and describe seed and fruit formation and dispersal.", "Describe asexual (vegetative) reproduction and calculate a germination percentage."],
      [("key", "definition", "Parts of a flower",
        "- **Sepals**: protect the bud.\n- **Petals**: attract insects (in insect-pollinated flowers).\n- **Stamen** (male): **anther** makes pollen; **filament** holds it up.\n- **Carpel** (female): **stigma** receives pollen; **style** joins it to the **ovary**; the ovary contains **ovules**, each with an egg cell."),
       ("fig", flower_fig(), "A flower cut in half.", "A flower section with the stigma, style, ovary, ovule, anther, filament, petal and sepal labelled."),
       ("key", "retenir", "Pollination and fertilisation",
        "**Pollination** is the transfer of pollen from an **anther** to a **stigma**. **Insect-pollinated** flowers have bright petals, scent, nectar and sticky pollen. **Wind-pollinated** flowers (maize, grasses) are small and dull with feathery stigmas and anthers hanging out. After landing, the pollen grain grows a **pollen tube** down the style to the ovule; the male nucleus joins the female nucleus: **fertilisation**. The **ovule** becomes the **seed** and the **ovary** becomes the **fruit**."),
       ("key", "retenir", "Dispersal and germination",
        "Seeds are **dispersed** by **wind** (light, winged or hairy seeds), **animals** (fleshy fruits eaten, hooked fruits), **water** (coconut) or **explosion** (pods). Dispersal reduces competition. A seed **germinates** with **water, oxygen and a suitable temperature**; it does not need light. **Asexual reproduction** from one parent gives identical offspring: cassava stem cuttings, banana suckers and yam tubers. It is fast but gives no variation, so disease can affect all plants."),
       ("key", "pieges", "Common mistakes",
        "- Pollination is not fertilisation: pollen landing on the stigma is only the first step.\n- A wind-pollinated flower does not need bright petals or nectar.\n- Seeds are not produced by vegetative reproduction."),
       ("example", "Germination percentage", "A farmer plants 200 maize seeds and 164 germinate. Find the germination percentage.", ["Percentage = 164 ÷ 200 × 100.", "= 82 %."], "82 %"),
       ("example", "Insect or wind?", "A flower has small green petals, long feathery stigmas and anthers hanging outside. How is it pollinated and why?", ["Small dull flowers with feathery stigmas and exposed anthers are features of wind pollination.", "No need for bright petals or nectar to attract insects."], "By wind")],
      [("mcq", "Which part of the flower makes pollen?", "anther", ["stigma", "ovary", "petal"], "Male part."),
       ("match", "Match each flower part with its function.", [("stigma", "receives pollen"), ("ovary", "contains ovules"), ("petal", "attracts insects"), ("anther", "produces pollen")], "Standard functions."),
       ("tf", "A seed needs light to germinate.", False, "Water, oxygen and warmth are needed."),
       ("num", "Out of 250 bean seeds, 200 germinate. Find the germination percentage.", 80, "200 ÷ 250 × 100 = 80 %.", 0, "%"),
       ("open", "Explain why farmers plant cassava from stem cuttings, and give one disadvantage.", "Cuttings grow quickly into plants identical to the parent, keeping a good variety; a disadvantage is that there is no variation, so a disease can affect all the plants.", ["1 mark: fast and identical", "1 mark: disadvantage"]),
       ("prob", "A flower is visited by bees and has bright petals, a sweet scent and sticky pollen.", [("mcq", "It is probably pollinated by:", "insects", ["wind", "water", "no agent"], "These features attract insects."), ("mcq", "After pollination the next step is:", "growth of the pollen tube to the ovule", ["falling of the seed", "formation of the petals", "germination"], "Then fertilisation."), ("open", "What does the ovary become after fertilisation?", "The fruit (and the ovules become seeds).", ["1 mark for fruit"], 2)])],
      [("The female part of a flower is the:", "carpel", ["stamen", "petal", "sepal"], "Stigma, style and ovary."),
       ("Transfer of pollen to a stigma is called:", "pollination", ["fertilisation", "germination", "dispersal"], "First step."),
       ("After fertilisation the ovule becomes a:", "seed", ["fruit", "petal", "stigma"], "Seed."),
       ("Which of these is a way of vegetative propagation?", "planting cassava stem cuttings", ["sowing maize seeds", "pollinating flowers", "burning the bush"], "One parent, no seeds."),
       ("Seeds with hooks are mainly dispersed by:", "animals", ["wind", "water", "gravity only"], "They stick to fur or clothes.")],
      ["Flower diagram is schematic. Check whether the Form 3 syllabus asks for seed structure and germination details (covered partly in the Basic Science pack)."])

fl = flow(["Egg and|sperm meet", "Fertilisation|(oviduct)", "Embryo|implants in uterus", "Foetus grows|(about 9 months)", "Birth"], w=480, size=10, bh=46, gap=14)
assert 40 * 7 == 280
build(ch, "human-reproduction", "Human reproduction in outline", 25,
      ["Name the main parts of the male and female reproductive systems and their functions.", "Describe fertilisation, pregnancy and the role of the placenta.", "State the main changes at puberty and give basic reproductive health advice."],
      [("key", "retenir", "The reproductive systems",
        "**Male**: **testes** make **sperm** (and the hormone testosterone), **sperm ducts** carry sperm, the **penis** places sperm in the female body. **Female**: **ovaries** release an **egg** about once a month (**ovulation**) and make oestrogen; the **oviduct** carries the egg, and is where **fertilisation** usually occurs; the **uterus** (womb) holds the developing baby; the **vagina** receives the sperm. Fertilisation is **internal**."),
       ("fig", fl, "From fertilisation to birth.", "Five boxes joined by arrows: egg and sperm meet, fertilisation in the oviduct, embryo implants in the uterus, foetus grows for about 9 months, birth."),
       ("key", "retenir", "Pregnancy",
        "The fertilised egg divides to form an **embryo** that **implants** in the lining of the uterus. A **placenta** forms: the baby's blood and the mother's blood come close but **do not mix**; **oxygen and food pass to the baby** and **carbon dioxide and urea pass to the mother**. The baby floats in **amniotic fluid** in the **amnion**, which protects it. Pregnancy lasts about **40 weeks** (about 280 days). **Breast milk** gives the best food and antibodies for a newborn."),
       ("key", "retenir", "Puberty and menstruation",
        "At **puberty** (usually between about 10 and 16 years) the body changes under **hormones**: in boys the voice deepens, hair grows on the face and body and sperm production begins; in girls the breasts develop, hips widen and **menstruation** begins. The **menstrual cycle** is about 28 days on average and varies between girls and women; ovulation is around the middle of the cycle."),
       ("key", "attention", "Reproductive health",
        "Some infections, including **HIV**, spread by sexual contact; HIV also spreads through blood and from mother to child. Prevention includes abstaining from sex, being faithful to one tested partner and correct use of condoms; people should seek testing and care at a health centre, where effective treatment is available. Pregnant women should attend **antenatal clinics** and follow a health worker's advice. Questions about growing up should be discussed with a parent, teacher or health worker."),
       ("key", "pieges", "Common mistakes",
        "- The placenta passes substances between mother and baby but the two bloods **do not mix**.\n- Fertilisation takes place in the oviduct, not in the uterus.\n- HIV is not spread by hugging, sharing food or mosquito bites."),
       ("example", "Pregnancy duration", "A pregnancy lasts about 40 weeks. How many days is that?", ["1 week = 7 days.", "40 × 7 = 280 days."], "About 280 days"),
       ("example", "Trace the egg", "Give the order of the places visited by an egg from its release to the start of pregnancy.", ["It is released from the ovary and enters the oviduct where it can be fertilised.", "The embryo then moves to the uterus and implants."], "ovary, oviduct (fertilisation), uterus")],
      [("mcq", "Where does fertilisation usually take place?", "in the oviduct", ["in the uterus", "in the ovary", "in the vagina"], "Egg meets sperm there."),
       ("match", "Match each organ with its function.", [("testis", "makes sperm"), ("ovary", "releases eggs"), ("uterus", "holds the developing baby"), ("placenta", "passes food and oxygen to the baby")], "Standard functions."),
       ("tf", "The mother's blood and the baby's blood mix in the placenta.", False, "Substances pass across but the bloods stay separate."),
       ("num", "How many days are there in a pregnancy of 38 weeks?", 266, "38 × 7 = 266.", 0, "days"),
       ("open", "Name two substances that pass from the mother to the baby and two that pass from the baby to the mother through the placenta.", "To the baby: oxygen and digested food (glucose, amino acids). To the mother: carbon dioxide and urea.", ["1 mark for two to baby", "1 mark for two to mother"]),
       ("prob", "A pupil's health talk covers reproductive health.", [("mcq", "Which action is advised to prevent HIV infection?", "correct and consistent condom use, or abstaining from sex", ["sharing razor blades", "hugging", "using a mosquito net"], "Basic prevention."), ("mcq", "Where should a person go for an HIV test and advice?", "a health centre", ["a market", "a mechanic", "a bus station"], "Testing and care are provided by health services."), ("open", "Give one reason why pregnant women should attend antenatal clinics.", "To check the health of mother and baby, receive advice, vaccines and treatment, and prepare for safe delivery.", ["1 mark for one sensible reason"], 2)])],
      [("In a male, sperm cells are produced in the:", "testes", ["ovaries", "uterus", "oviduct"], "Male gonads."),
       ("The foetus develops in the:", "uterus", ["ovary", "oviduct", "bladder"], "The womb."),
       ("A pregnancy lasts about:", "40 weeks", ["10 weeks", "20 weeks", "60 weeks"], "About 9 months."),
       ("The placenta allows:", "exchange of oxygen and food without mixing blood", ["mixing of the two bloods", "growth of hair", "formation of eggs"], "Exchange across a thin barrier."),
       ("Breast milk provides:", "food and antibodies for the baby", ["only water", "no protection", "only vitamins"], "Best food for newborns.")],
      ["Human reproduction content is conservative, outlined only and flagged for review by a health worker and the school's policy; some schools teach this in Form 4 or 5. Menstrual cycle length and puberty ages are averages."])

# =========================================================== 7 Ecology
ch = p.chapter("ecology", "Introduction to ecology", "Form 3 Biology — Ecosystems, food webs and the environment (to be checked)")
def web_fig():
    d = Draw()
    def box(x, y, w, t, c): d.sh.append(RECT(x, y, w, 32, fill=c, width=1.5, radius=6)); d.text(x + w / 2, y + 21, t, size=12)
    box(20, 205, 100, "grass", "lightgreen"); box(160, 205, 100, "maize", "lightgreen")
    box(20, 145, 110, "grasshopper", "lightyellow"); box(160, 145, 100, "rat", "lightyellow")
    box(20, 85, 110, "frog", "lightorange"); box(160, 85, 100, "snake", "lightorange"); box(320, 22, 100, "hawk", "lightblue")
    d.line(70, 203, 70, 179, arrow="end", width=1.8); d.line(210, 203, 210, 179, arrow="end", width=1.8); d.line(112, 203, 178, 179, arrow="end", width=1.8)
    d.line(75, 143, 75, 119, arrow="end", width=1.8); d.line(132, 101, 158, 101, arrow="end", width=1.8); d.line(210, 143, 210, 119, arrow="end", width=1.8)
    d.line(262, 92, 328, 58, arrow="end", width=1.8)
    d.text(285, 228, "arrow = 'is eaten by'", size=11, anchor="start")
    return d.fig(440, 250)
cts = [3, 5, 4, 6, 2, 4, 5, 3]; mean = sum(cts) / len(cts); est = mean * 400 / 1
assert mean == 4.0 and est == 1600
build(ch, "ecology", "Ecosystems, food webs and the environment", 30,
      ["Define ecosystem terms and construct food chains and webs.", "Describe energy flow and sampling with quadrats and estimate a population.", "Describe human effects on the environment and conservation."],
      [("key", "definition", "Ecosystem terms",
        "A **habitat** is where an organism lives; a **population** is all the organisms of one species in a habitat; a **community** is all the populations together; an **ecosystem** is the community plus its non-living environment (soil, water, light, temperature). **Producers** (green plants) make food; **consumers** eat; **decomposers** (bacteria, fungi) break down dead matter and return nutrients to the soil. **Herbivores** are primary consumers; **carnivores** are secondary or tertiary consumers."),
       ("fig", web_fig(), "A food web: arrows show the direction of energy flow.", "A web: grass feeds grasshopper and rat, maize feeds rat, grasshopper is eaten by frog, frog and rat are eaten by snake, snake is eaten by hawk."),
       ("key", "retenir", "Energy flow",
        "Energy enters ecosystems as **sunlight** captured by producers. At each step in a food chain only a small part of the energy (roughly **one tenth**) passes to the next level; most is used in respiration and lost as **heat**, or is in parts that are not eaten. So food chains are **short** and the **numbers or mass** of organisms usually fall at each level (**pyramid of numbers or biomass**)."),
       ("key", "methode", "Sampling with quadrats",
        "A **quadrat** is a square frame (often 1 m × 1 m) placed **at random** many times in an area. Count the organisms in each, find the **mean** per quadrat and estimate the total: **population ≈ mean per quadrat × (total area ÷ quadrat area)**. More quadrats give a more reliable estimate. Example: counts 3, 5, 4, 6, 2, 4, 5, 3 in 1 m² quadrats give a mean of 4 per m²; in 400 m² the estimate is 1600."),
       ("key", "retenir", "People and the environment",
        "**Deforestation**, bush burning, overfishing, pollution and urban growth destroy habitats and reduce biodiversity. **Conservation**: protected areas (for example Korup rainforest, Waza savannah and the Mount Cameroon region), tree planting, controlled hunting and fishing, sustainable farming, waste management and education. **Nitrogen cycle**: decomposers make ammonia, **nitrifying bacteria** make nitrates for plants; **legumes** (beans, groundnuts) have **nitrogen-fixing bacteria** in their root nodules and improve the soil."),
       ("key", "pieges", "Common mistakes",
        "- Arrows in food chains point towards the **consumer**, not the food.\n- A food chain always starts with a **producer**.\n- Quadrats must be placed **at random**; throwing them where plants look thickest gives a biased estimate."),
       ("example", "Population estimate", "Eight 1 m² quadrats give plant counts of 3, 5, 4, 6, 2, 4, 5 and 3. Estimate the number of plants in a field of 400 m².", ["Mean = (3 + 5 + 4 + 6 + 2 + 4 + 5 + 3) ÷ 8 = 32 ÷ 8 = 4 per m².", "Estimate = 4 × 400 = 1600 plants."], "About 1600 plants"),
       ("example", "Effect on the food web", "In the web, all the frogs die from a disease. Predict two effects.", ["Grasshoppers are no longer eaten by frogs, so their number rises.", "Snakes have less food and turn to rats more, or their number falls."], "More grasshoppers; less food for snakes.")],
      [("mcq", "In a food chain, the producer is always a:", "green plant", ["herbivore", "carnivore", "decomposer"], "It makes food by photosynthesis."),
       ("match", "Match each term with its meaning.", [("habitat", "where an organism lives"), ("decomposer", "breaks down dead matter"), ("population", "all the organisms of one species in an area"), ("ecosystem", "community and non-living environment")], "Standard definitions."),
       ("tf", "Most of the energy at one level of a food chain passes to the next level.", False, "Only about one tenth does."),
       ("num", "Five 1 m² quadrats contain 6, 4, 5, 7 and 3 plants. Estimate the number in 200 m².", 1000, "Mean = 25 ÷ 5 = 5 per m²; 5 × 200 = 1000.", 0),
       ("open", "Explain why quadrats should be placed at random.", "Random placement avoids bias, so the sample represents the whole area and the estimate of population is fair.", ["1 mark: avoids bias", "1 mark: representative sample"]),
       ("prob", "Food chain: algae → tadpole → fish → heron.", [("mcq", "The tertiary consumer is:", "heron", ["fish", "tadpole", "algae"], "Primary tadpole, secondary fish, tertiary heron."), ("mcq", "Most of the energy lost between the levels leaves as:", "heat", ["light", "sound", "food"], "From respiration."), ("open", "Suggest why there are fewer herons than tadpoles in the pond.", "Only about one tenth of the energy passes up each level, so less energy is available at the top and fewer large animals can be supported.", ["1 mark: energy lost at each level", "1 mark: fewer organisms at the top"], 2)])],
      [("Organisms that break down dead plants and animals are:", "decomposers", ["producers", "herbivores", "predators"], "Bacteria and fungi."),
       ("The source of energy for most food chains is:", "the Sun", ["the soil", "the wind", "the rain"], "Captured by producers."),
       ("Bacteria in root nodules of beans:", "fix nitrogen", ["make chlorophyll", "digest starch", "pollinate flowers"], "They improve soil fertility."),
       ("An organism that eats only plants is a:", "herbivore", ["carnivore", "omnivore", "decomposer"], "Primary consumer."),
       ("Which activity protects biodiversity?", "creating protected areas", ["burning forests", "overfishing", "dumping waste"], "Conservation.")],
      ["The 'one tenth' energy transfer is an approximate textbook figure. Protected areas named (Korup, Waza, Mount Cameroon) are real but wording must be checked. Nitrogen cycle appears only in outline."])

p.write()
