import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *

p = Pack("form1-science", "Basic Science — Form 1", level="Form 1", subject="sciences", cursus="secondary",
         description="An integrated first course in science for Form 1: how scientists work, apparatus and measurement, matter and mixtures, living things and cells, plants, air, water, energy and a healthy environment. Short lessons, worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Basic Science, Form 1 (integrated science) — to be checked against the official syllabus")

# =========================================================== 1 Science and measurement
ch = p.chapter("method", "Working like a scientist", "Form 1 Basic Science — Scientific method, apparatus, measurement (to be checked)")
steps_fig = flow(["Observe|and ask", "Make a|hypothesis", "Do a fair|test", "Record|results", "Conclude"], w=480, size=11, bh=44, gap=14)
build(ch, "scientific-method", "The scientific method", 20,
      ["List the steps of the scientific method.", "Identify the variable changed, the variable measured and the variables kept the same in a fair test.", "Write a simple hypothesis and a conclusion."],
      [("key", "definition", "How scientists find answers",
        "**Science** is a way of finding out about the world by **observing**, asking questions and testing ideas. A **hypothesis** is an idea that can be tested, for example: *maize grows taller with more water*. An **experiment** tests the hypothesis and the **results** show whether it is supported."),
       ("fig", steps_fig, "The steps of a scientific investigation.", "Five boxes joined by arrows: observe and ask, make a hypothesis, do a fair test, record results, conclude."),
       ("key", "methode", "A fair test",
        "In a **fair test** only one thing is changed.\n\n- The **independent variable** is what you change on purpose.\n- The **dependent variable** is what you measure.\n- The **controlled variables** are kept the same.\n\nRepeat the test and record results in a table so that you can trust them."),
       ("key", "pieges", "Common mistakes",
        "- Changing two things at once: you cannot tell which caused the result.\n- A hypothesis is not a fact; it may be wrong and the test can show it.\n- A conclusion must follow the results, not what you hoped to see."),
       ("example", "Name the variables", "A student grows three identical bean plants in the same soil and gives them 50 mL, 100 mL and 150 mL of water each day. After 10 days she measures the height of each plant. Name the three kinds of variable.", ["She changes the volume of water: this is the independent variable.", "She measures plant height: the dependent variable.", "Same plant, soil, light and days: controlled variables."], "Changed: water volume. Measured: height. Kept the same: plant type, soil, light, time."),
       ("example", "Write a conclusion", "In the test above the heights after 10 days were 12 cm, 18 cm and 21 cm for 50, 100 and 150 mL. What conclusion fits the results?", ["The height increases as the volume of water increases: 12 < 18 < 21.", "State this only for the amounts tested."], "Within 50 to 150 mL per day, more water gave taller plants.")],
      [("mcq", "In a fair test, the variable that you change on purpose is the:", "independent variable", ["dependent variable", "controlled variable", "conclusion"], "You choose and change the independent variable."),
       ("tf", "A hypothesis is a proven fact.", False, "A hypothesis is an idea to be tested; it may be shown wrong."),
       ("mcq", "Why should an experiment be repeated?", "to check that the results can be trusted", ["to waste materials", "to change the hypothesis", "because it is a rule of the school"], "Repeating shows whether a result was a chance error."),
       ("match", "Match each step with what happens in it.", [("hypothesis", "an idea that can be tested"), ("results", "measurements written in a table"), ("conclusion", "what the results show"), ("observation", "noticing something with the senses")], "These are the standard meanings."),
       ("open", "A farmer in Bamenda wants to know whether fertiliser makes maize grow taller. Describe a fair test.", "Plant maize seeds of the same kind in two plots of the same size and soil. Give fertiliser to one plot only. Give both the same water and sunlight. Measure the height of the plants after the same number of weeks and compare the averages.", ["1 mark: one plot with fertiliser, one without", "1 mark: other conditions kept the same", "1 mark: height measured and compared after the same time"]),
       ("prob", "Mary tests which of three soils (sand, clay, loam) lets water pass fastest. She pours 100 mL of water onto 200 g of each soil in identical funnels and measures the volume collected after 5 minutes.", [("mcq", "In Mary's soil test, the independent variable is:", "the type of soil", ["the volume of water collected", "the mass of soil", "the time"], "She changes the soil type."), ("mcq", "The dependent variable is:", "the volume of water collected", ["the type of soil", "the funnel", "the 100 mL of water"], "It is the quantity measured."), ("open", "Name two variables she must keep the same.", "Any two of: volume of water, mass of soil, type of funnel, time allowed.", ["1 mark each, maximum 2"], 2)])],
      [("The first step of the scientific method is to:", "observe and ask a question", ["write the conclusion", "repeat the test", "publish the result"], "Science starts with observation."),
       ("In a fair test, how many variables are changed on purpose?", "one", ["two", "three", "every variable"], "Only one, so the cause is clear."),
       ("A testable idea made before an experiment is a:", "hypothesis", ["conclusion", "result", "measurement"], "It is tested by the experiment."),
       ("Variables that are kept the same during a test are called:", "controlled variables", ["dependent variables", "random variables", "final variables"], "They keep the test fair."),
       ("Results are best recorded in:", "a table", ["a story", "a guess", "the conclusion only"], "A table makes patterns clear.")],
      ["Terminology (independent/dependent/controlled) as in common school texts; check whether the syllabus uses 'manipulated/responding'."])

def apparatus_fig():
    d = Draw(); y = 150
    # beaker
    x = 45; d.sh.append(POLY([(x-22, 70), (x-22, y), (x+22, y), (x+22, 70)], closed=False, width=2)); d.sh.append(RECT(x-21, 110, 42, y-110, fill="lightblue", stroke="none", width=0))
    d.text(x, 175, "beaker")
    # measuring cylinder
    x = 135; d.sh.append(POLY([(x-10, 40), (x-10, y), (x+10, y), (x+10, 40)], closed=False, width=2))
    for k in range(5): d.line(x-10, 60 + k*18, x-3, 60 + k*18, width=1.2)
    d.text(x, 175, "measuring")
    d.text(x, 190, "cylinder")
    # conical flask
    x = 225; d.sh.append(POLY([(x-8, 60), (x-8, 95), (x-28, y), (x+28, y), (x+8, 95), (x+8, 60)], closed=False, width=2))
    d.text(x, 175, "conical flask")
    # test tube
    x = 315; d.sh.append(POLY([(x-9, 55), (x-9, 135), (x-6, 145), (x, 150), (x+6, 145), (x+9, 135), (x+9, 55)], closed=False, width=2))
    d.text(x, 175, "test tube")
    # bunsen burner
    x = 405; d.sh.append(RECT(x-20, 140, 40, 10, fill="lightgrey", width=2)); d.sh.append(RECT(x-5, 80, 10, 60, fill="lightgrey", width=2))
    d.sh.append(POLY([(x-6, 80), (x, 55), (x+6, 80)], fill="orange", width=1.5))
    d.text(x, 175, "Bunsen burner")
    return d.fig(480, 200)

build(ch, "apparatus", "Laboratory apparatus and safety", 20,
      ["Name common laboratory apparatus and state what each is used for.", "State safety rules for a laboratory.", "Recognise the meaning of common hazard signs."],
      [("key", "retenir", "Common apparatus",
        "- **Beaker**: holds and heats liquids roughly.\n- **Measuring cylinder**: measures the volume of a liquid.\n- **Conical flask**: holds liquids and can be swirled.\n- **Test tube**: small reactions.\n- **Bunsen burner**: heating, used with a tripod and gauze.\n- **Filter funnel** and filter paper: separate a solid from a liquid.\n- **Thermometer**: temperature.\n- **Balance**: mass."),
       ("fig", apparatus_fig(), "Five pieces of laboratory apparatus.", "Drawings of a beaker, a measuring cylinder, a conical flask, a test tube and a Bunsen burner with their names under them."),
       ("key", "methode", "Safety rules",
        "1. Listen to the teacher and do not work alone.\n2. Wear eye protection when told to; tie long hair back.\n3. Never taste, and only smell a chemical by wafting the vapour towards you gently.\n4. Point the open end of a heated test tube away from people.\n5. Keep flammable liquids away from flames.\n6. Wash hands after the practical and clear up spills."),
       ("key", "pieges", "Common mistakes",
        "- Do not use a beaker to measure an exact volume: use a measuring cylinder.\n- A hazard sign is a warning, not a decoration: a **flame** means *flammable*, a **skull** means *toxic*, and a sign with a damaged hand means *corrosive*.\n- Do not run in the laboratory."),
       ("example", "Choose the apparatus", "You need 25 mL of water for an experiment. Which piece of apparatus should you use and why?", ["A measuring cylinder is marked in mL.", "A beaker has only rough marks and is not accurate."], "A measuring cylinder."),
       ("example", "Safe heating", "A student heats a test tube of water over a Bunsen burner and points it at his neighbour. Give two things he should change.", ["The mouth of the tube must point away from people because hot liquid can spit out.", "He should also move the tube gently in the flame and wear eye protection."], "Point the tube away from people and wear eye protection.")],
      [("mcq", "Which apparatus measures the volume of a liquid most accurately?", "measuring cylinder", ["beaker", "test tube", "tripod"], "It has an accurate scale in mL."),
       ("match", "Match each apparatus with its use.", [("filter funnel", "separating a solid from a liquid"), ("thermometer", "measuring temperature"), ("balance", "measuring mass"), ("Bunsen burner", "heating")], "Standard uses."),
       ("tf", "It is safe to taste a chemical if it looks like salt.", False, "Never taste anything in the laboratory."),
       ("mcq", "A flame symbol on a bottle means the liquid is:", "flammable", ["alive", "cold", "sweet"], "Keep it away from flames and sparks."),
       ("open", "State three safety rules for a laboratory practical.", "Wear eye protection; do not taste chemicals; point heated test tubes away from people; keep long hair tied; wash hands afterwards (any three).", ["1 mark each, maximum 3"]),
       ("prob", "A student is told to heat 20 mL of water in a beaker on a tripod and gauze.", [("mcq", "Which item goes between the beaker and the flame?", "gauze", ["thermometer", "funnel", "spatula"], "The gauze spreads the heat."), ("mcq", "Which apparatus measures the 20 mL?", "measuring cylinder", ["balance", "tripod", "stopwatch"], "Volume of liquid."), ("open", "Give one safety precaution for this practical.", "Wear eye protection, or keep the hair tied back, or use tongs to move the hot beaker.", ["1 mark for a sensible precaution"], 2)])],
      [("A Bunsen burner is used for:", "heating", ["measuring mass", "measuring length", "filtering"], "It gives a hot flame."),
       ("The corrosive hazard sign warns that a chemical can:", "damage skin and materials", ["float away", "change colour only", "make noise"], "Corrosive chemicals burn skin."),
       ("A test tube that is being heated should point:", "away from everyone", ["at the teacher", "at your face", "at a neighbour"], "Liquid can spit out."),
       ("To separate sand from water you need filter paper and a:", "filter funnel", ["balance", "thermometer", "stopwatch"], "The funnel holds the paper."),
       ("Before leaving the laboratory you should:", "wash your hands", ["taste the chemicals", "leave spills", "run"], "Washing removes traces of chemicals.")],
      ["Hazard symbols described in words only; check which signs the school laboratory actually uses."])

# --- measuring
cyl = Draw()
for x, lvl, stone in ((90, 40, False), (290, 55, True)):
    h_ = (lvl) * 2.0  # px per mL
    cyl.sh.append(RECT(x - 20, 190 - h_, 40, h_, fill="lightblue", stroke="none", width=0))
    cyl.sh.append(POLY([(x - 20, 30), (x - 20, 190), (x + 20, 190), (x + 20, 30)], closed=False, width=2))
    if stone: cyl.sh.append(CIRCLE(x, 180, 9, fill="grey", width=1.5))
cyl.text(90, 215, "start: 40 mL"); cyl.text(290, 215, "with stone: 55 mL")
cyl.line(150, 120, 230, 120, arrow="end"); cyl.text(190, 112, "stone in", size=12)
vol_fig = cyl.fig(400, 230)
assert 55 - 40 == 15 and 1 * 15 == 15
build(ch, "measuring", "Measuring length, mass, volume and time", 25,
      ["Name the SI units and the instruments for length, mass, volume and time.", "Convert between common units.", "Find the volume of an irregular solid by displacement."],
      [("key", "retenir", "Quantities, units and instruments",
        "- **Length**: metre (m); ruler, tape measure.\n- **Mass**: kilogram (kg) or gram (g); balance.\n- **Volume**: cubic metre (m³), litre (L) or millilitre (mL); measuring cylinder.\n- **Time**: second (s); stopwatch or clock.\n\nUseful: 1 km = 1000 m; 1 m = 100 cm; 1 kg = 1000 g; 1 L = 1000 mL; **1 mL = 1 cm³**; 1 min = 60 s."),
       ("fig", vol_fig, "Volume of a stone by displacement: 55 mL − 40 mL.", "Two measuring cylinders: the first has water at 40 mL, the second has a stone in it and water at 55 mL."),
       ("key", "methode", "Good measuring",
        "- Put your eye level with the scale to avoid **parallax error**.\n- For a liquid read the **bottom of the curved surface** (meniscus).\n- Start a ruler measurement from the 0 mark, not the end of the ruler.\n- **Irregular solid**: volume = final level − first level.\n- A **pendulum** swings back and forth; the time for one swing is the **period**: time 20 swings and divide by 20."),
       ("key", "pieges", "Common mistakes",
        "- Mixing units: change cm to m, or g to kg, *before* calculating.\n- Mass is not weight: mass is measured in kg with a balance.\n- 1 L is 1000 mL, not 100 mL."),
       ("example", "Volume of a stone", "A stone is lowered into a measuring cylinder with 40 mL of water. The level rises to 55 mL. Find the volume of the stone.", ["Volume = final level − first level.", "55 − 40 = 15 mL, and 1 mL = 1 cm³."], "15 cm³"),
       ("example", "Period of a pendulum", "A pendulum makes 20 complete swings in 24 s. Find the time for one swing.", ["Period = total time ÷ number of swings.", "24 ÷ 20 = 1.2 s."], "1.2 s")],
      [("mcq", "The SI unit of mass is the:", "kilogram", ["litre", "second", "metre"], "Mass is in kg."),
       ("num", "Convert 2.5 km to metres.", 2500, "2.5 × 1000 = 2500 m.", 0, "m"),
       ("num", "Convert 350 g to kg.", 0.35, "350 ÷ 1000 = 0.35 kg.", 0.001, "kg"),
       ("mcq", "Which instrument measures time?", "stopwatch", ["balance", "ruler", "measuring cylinder"], "A stopwatch measures seconds."),
       ("num", "A stone raises the water in a cylinder from 30 mL to 48 mL. Find its volume in cm³.", 18, "48 − 30 = 18 mL = 18 cm³.", 0, "cm³"),
       ("prob", "A pendulum makes 30 swings in 45 s. A bottle holds 1.5 L of palm oil.", [("num", "Period of the pendulum in seconds.", 1.5, "45 ÷ 30 = 1.5 s.", 0.01, "s"), ("num", "Volume of the oil in mL.", 1500, "1.5 × 1000 = 1500 mL.", 0, "mL"), ("open", "Why is it better to time 30 swings than 1 swing?", "One swing is too short to time accurately; timing many swings reduces the effect of reaction time, and dividing by 30 gives a more accurate period.", ["1 mark: reaction time / small error", "1 mark: divide by the number of swings"], 2)])],
      [("1 mL is equal to:", "1 cm³", ["1 m³", "10 cm³", "100 cm³"], "They are the same volume."),
       ("Parallax error is reduced by:", "keeping your eye level with the scale", ["looking from above", "using a bigger ruler", "measuring once"], "Eye level gives a true reading."),
       ("The volume of an irregular stone is found by:", "displacement of water", ["weighing it", "timing it", "measuring its colour"], "Final level minus first level."),
       ("How many grams are in 2 kg?", "2000", ["20", "200", "20 000"], "1 kg = 1000 g."),
       ("The time for one swing of a pendulum is its:", "period", ["mass", "volume", "level"], "Period = time for one swing.")],
      ["Check whether the syllabus introduces SI prefixes (milli, kilo) formally in Form 1.", "Meniscus reading: bottom of the curve for water."])

# =========================================================== 2 Matter
ch = p.chapter("matter", "Matter and mixtures", "Form 1 Basic Science — States of matter, mixtures and separation (to be checked)")
def particles_fig():
    d = Draw()
    # solid: regular lattice
    for i in range(4):
        for j in range(4): d.sh.append(CIRCLE(30 + i*16, 40 + j*16, 7, fill="lightblue", width=1.5))
    # liquid: touching, irregular
    pts = [(190, 46), (206, 40), (222, 50), (238, 44), (184, 62), (200, 58), (216, 66), (232, 62), (246, 66), (190, 78), (206, 76), (224, 82), (240, 80), (196, 92), (214, 96), (232, 96)]
    for x, y in pts: d.sh.append(CIRCLE(x, y, 7, fill="lightgreen", width=1.5))
    # gas: far apart
    for x, y in [(350, 40), (410, 55), (375, 85), (430, 100), (345, 100), (395, 30)]: d.sh.append(CIRCLE(x, y, 7, fill="lightorange", width=1.5))
    d.text(62, 135, "Solid", bold=True); d.text(62, 152, "fixed pattern", size=11)
    d.text(215, 135, "Liquid", bold=True); d.text(215, 152, "close, can slide", size=11)
    d.text(390, 135, "Gas", bold=True); d.text(390, 152, "far apart, free", size=11)
    return d.fig(480, 170)

build(ch, "states-of-matter", "Solids, liquids and gases", 20,
      ["Describe the properties and particle arrangement of solids, liquids and gases.", "Name the changes of state.", "Explain evaporation and boiling simply."],
      [("key", "definition", "Matter",
        "**Matter** is anything that has mass and takes up space. It is made of tiny **particles**. The three **states of matter** are solid, liquid and gas.\n\n- **Solid**: fixed shape and volume; particles packed in a pattern, only vibrating.\n- **Liquid**: fixed volume, takes the shape of its container; particles close but sliding.\n- **Gas**: no fixed shape or volume; particles far apart and moving fast."),
       ("fig", particles_fig(), "Particles in a solid, a liquid and a gas.", "Three panels: a solid with particles in a regular pattern, a liquid with particles touching but irregular, a gas with particles far apart."),
       ("key", "retenir", "Changes of state",
        "- **Melting**: solid to liquid (ice melts at 0 °C).\n- **Freezing**: liquid to solid.\n- **Evaporation**: liquid to gas at the surface at any temperature.\n- **Boiling**: liquid to gas throughout, at the boiling point (water 100 °C at sea level).\n- **Condensation**: gas to liquid.\n- **Sublimation**: solid straight to gas, e.g. iodine.\n\nThese are **physical changes**: no new substance is made."),
       ("key", "pieges", "Common mistakes",
        "- Gases have mass: a football full of air weighs more than an empty one.\n- Temperature stays constant while a pure substance melts or boils.\n- Evaporation is not boiling: clothes dry on a line well below 100 °C."),
       ("example", "Name the change", "A block of ice in a Douala market is left in the sun and becomes water. Later the puddle disappears. Name each change.", ["Ice (solid) to water (liquid) is melting.", "Water disappearing into the air is evaporation."], "Melting, then evaporation."),
       ("example", "Reading a heating record", "A beaker of water is heated and its temperature recorded each minute: 20, 40, 60, 80, 100, 100, 100 °C. What is the boiling point and how do you know?", ["The temperature rises and then stays constant.", "It stays at 100 °C while the water boils."], "100 °C")],
      [("mcq", "Which state of matter has particles that are far apart and move freely?", "gas", ["solid", "liquid", "ice"], "Gas particles are widely spaced."),
       ("match", "Match each change of state with its description.", [("melting", "solid to liquid"), ("condensation", "gas to liquid"), ("freezing", "liquid to solid"), ("evaporation", "liquid to gas at the surface")], "Standard definitions."),
       ("tf", "A liquid has a fixed volume but no fixed shape.", True, "It takes the shape of its container."),
       ("mcq", "Steam touches a cold window and forms drops. This change is:", "condensation", ["melting", "sublimation", "freezing"], "Gas to liquid."),
       ("open", "Explain why a liquid can be poured but a solid cannot.", "In a liquid the particles are close but can slide over each other, so it flows; in a solid they are held in fixed positions and can only vibrate.", ["1 mark: particles in liquid can move past each other", "1 mark: particles in a solid are fixed"]),
       ("prob", "Ethanol freezes at about −114 °C and boils at about 78 °C.", [("mcq", "Its state at 25 °C is:", "liquid", ["solid", "gas", "plasma"], "25 °C is between −114 °C and 78 °C."), ("mcq", "Its state at 100 °C is:", "gas", ["solid", "liquid", "frozen"], "100 °C is above the boiling point."), ("open", "Name the change that happens when ethanol is cooled from 25 °C to −120 °C.", "Freezing (liquid to solid).", ["1 mark for freezing"], 2)])],
      [("Particles in a solid:", "vibrate in fixed positions", ["fly about freely", "do not exist", "are always far apart"], "That is why a solid keeps its shape."),
       ("Water boils at sea level at:", "100 °C", ["0 °C", "37 °C", "50 °C"], "Boiling point of water."),
       ("Solid to gas without becoming a liquid is called:", "sublimation", ["boiling", "freezing", "dissolving"], "Iodine does this."),
       ("Which of these is a physical change?", "ice melting", ["wood burning", "iron rusting", "food rotting"], "No new substance forms when ice melts."),
       ("Gas particles compared with liquid particles are:", "much further apart", ["packed closer", "not moving", "heavier"], "Gases are easily compressed.")],
      ["Boiling point 100 °C is at normal (sea-level) pressure; in Bamenda or Bafoussam (higher altitude) water boils slightly lower — mention only if the syllabus asks."])

meth = grid(["Filtration|solid from liquid|(sand and water)", "Evaporation|dissolved solid|from a solution", "Distillation|liquid from solution|(pure water)", "Chromatography|separates colours|(ink, dyes)", "Magnet|iron from other solids", "Sieving|different grain sizes|(garri, flour)"], cols=3, bh=56, size=11)
build(ch, "mixtures", "Mixtures and how to separate them", 25,
      ["Distinguish a pure substance from a mixture.", "Define solute, solvent, solution and suspension.", "Choose a suitable method to separate a given mixture."],
      [("key", "definition", "Mixtures and solutions",
        "A **mixture** has two or more substances that are not chemically joined and can be separated. A **solution** forms when a **solute** dissolves in a **solvent** (salt in water). A **suspension** has solid pieces that stay mixed for a while and then settle (muddy water). Water is a good solvent."),
       ("fig", meth, "Methods of separation and what they are used for.", "Six boxes: filtration separates a solid from a liquid; evaporation recovers a dissolved solid; distillation gives pure water; chromatography separates colours; a magnet picks out iron; sieving separates grain sizes."),
       ("key", "methode", "Choosing a method",
        "1. Solid **not dissolved** in a liquid: **filtration**.\n2. Solid **dissolved**: **evaporation** (or crystallisation).\n3. Liquid from a solution: **distillation**.\n4. Two liquids that do not mix (oil and water): leave to settle and use a **separating funnel**.\n5. Different colours: **chromatography**.\n6. A magnetic solid such as iron: **magnet**."),
       ("key", "pieges", "Common mistakes",
        "- Filtration cannot separate salt from salt water because the salt is dissolved and passes through the paper.\n- Evaporation to dryness loses the water; use distillation to keep it.\n- Do not heat a flammable liquid on a naked flame."),
       ("example", "Salt and sand", "A mixture of salt and sand is given. Describe how to obtain both.", ["Add water and stir: the salt dissolves, the sand does not.", "Filter: the sand stays on the paper; wash and dry it.", "Heat the filtrate in an evaporating dish until crystals remain: salt."], "Dissolve, filter (sand), evaporate (salt)."),
       ("example", "Choose the method", "Which method separates palm oil from water in a container?", ["Palm oil and water do not mix and have different densities.", "The oil floats and can be run off with a separating funnel."], "Settling and a separating funnel.")],
      [("mcq", "Muddy water can be cleared by:", "filtration", ["evaporation", "chromatography", "magnet"], "The mud is not dissolved."),
       ("match", "Match each mixture with a method.", [("iron filings and sand", "magnet"), ("salt solution (to get salt)", "evaporation"), ("black ink (to see the colours)", "chromatography"), ("salt solution (to get pure water)", "distillation")], "Each uses a different property."),
       ("tf", "Filtering salt water will remove the salt.", False, "Dissolved salt passes through filter paper."),
       ("mcq", "In sugar solution the sugar is the:", "solute", ["solvent", "filtrate", "residue"], "The substance that dissolves."),
       ("open", "Describe how you would get pure water from salty water.", "Heat the salt water in a flask until it boils; pass the steam through a cooled condenser; collect the condensed liquid, which is pure water; the salt stays in the flask.", ["1 mark: boil", "1 mark: condense", "1 mark: salt remains behind"]),
       ("prob", "A student has a mixture of iron filings, sand and salt.", [("mcq", "Which substance is removed first with a magnet?", "iron filings", ["sand", "salt", "water"], "Only iron is magnetic."), ("mcq", "After adding water and filtering, the residue on the paper is:", "sand", ["salt", "iron", "water"], "Sand does not dissolve."), ("open", "How is the salt obtained from the filtrate?", "Evaporate the water by heating the filtrate until crystals of salt are left.", ["1 mark for evaporation"], 2)])],
      [("A substance that dissolves in a liquid is the:", "solute", ["solvent", "residue", "suspension"], "Solute dissolves in solvent."),
       ("The solid left on the filter paper is the:", "residue", ["filtrate", "solvent", "solution"], "The filtrate is the liquid that passes through."),
       ("To separate the colours in an ink you would use:", "chromatography", ["filtration", "winnowing", "sieving"], "Colours travel different distances."),
       ("Distillation separates a liquid from a solution because:", "the liquid boils and is condensed", ["the solid is magnetic", "the paper traps it", "it freezes first"], "Evaporate then condense."),
       ("Oil and water can be separated using a:", "separating funnel", ["magnet", "sieve", "thermometer"], "They form two layers.")],
      ["Winnowing and sieving are included as local examples; check whether the syllabus wants only the classical laboratory methods."])

# =========================================================== 3 Living things
ch = p.chapter("living", "Living things, cells and plants", "Form 1 Basic Science — Living things, cells, plants (to be checked)")
vg = grid(["Fish|gills, scales, fins|(tilapia)", "Amphibians|moist skin|(frog)", "Reptiles|dry scaly skin|(python)", "Birds|feathers, wings|(parrot)", "Mammals|hair, milk|(goat)", "Invertebrates|no backbone|(insects, snails)"], cols=3, bh=56, size=11)
build(ch, "living-things", "Living things and their groups", 20,
      ["Name the seven characteristics of living things.", "Group animals into vertebrates and invertebrates and name the five vertebrate groups.", "Tell plants and animals apart."],
      [("key", "definition", "Characteristics of living things",
        "Living things carry out seven life processes (**MRS GREN**): **m**ovement, **r**espiration (release of energy from food), **s**ensitivity (responding to changes), **g**rowth, **r**eproduction, **e**xcretion (removing waste) and **n**utrition (taking in food). Non-living things such as stones and cars do not do all of these."),
       ("fig", vg, "Groups of animals with an example from Cameroon.", "Six boxes: fish, amphibians, reptiles, birds, mammals and invertebrates, each with a key feature and an example."),
       ("key", "retenir", "Plants and animals",
        "**Plants** make their own food by photosynthesis, have cell walls and usually do not move from place to place. **Animals** eat other living things, have no cell walls and usually move about.\n\nAnimals with a **backbone** are **vertebrates**; those without are **invertebrates** (insects, worms, snails, spiders)."),
       ("key", "pieges", "Common mistakes",
        "- A bat is a mammal, not a bird: it has fur and feeds its young with milk.\n- A whale lives in water but is a mammal.\n- Plants are alive: they grow, reproduce and respond to light."),
       ("example", "Classify the animal", "An animal has dry scaly skin, a backbone and lays eggs on land. Which group is it?", ["A backbone means a vertebrate.", "Dry scaly skin and eggs laid on land fit the reptiles."], "A reptile (for example a lizard or python)."),
       ("example", "Is it alive?", "A seed does not seem to move or grow. Is it alive? Give a reason.", ["A dormant seed respires very slowly.", "When given water, air and warmth it germinates and grows."], "Yes: it respires and can grow into a plant.")],
      [("mcq", "In the list MRS GREN, excretion means:", "removing waste made by the body", ["taking in food", "making new plants", "moving about"], "Waste such as carbon dioxide and urea is removed."),
       ("match", "Match each animal with its group.", [("tilapia", "fish"), ("frog", "amphibian"), ("hen", "bird"), ("goat", "mammal")], "Standard classification."),
       ("tf", "A spider is classed with the insects.", False, "A spider has eight legs and is not an insect; insects have six."),
       ("mcq", "Which feature is found in mammals only among the animals listed?", "hair or fur and milk for the young", ["feathers", "gills", "scales"], "Mammals have hair and feed milk."),
       ("open", "Give three reasons why a car is not a living thing.", "It does not grow, does not reproduce, does not respire or excrete, and does not respond to changes by itself.", ["1 mark each, maximum 3"]),
       ("prob", "A farmer keeps goats, hens, tilapia and snails.", [("mcq", "Which of these is an invertebrate?", "snail", ["goat", "hen", "tilapia"], "A snail has no backbone."), ("mcq", "Which one breathes using gills?", "tilapia", ["goat", "hen", "snail"], "Fish have gills."), ("open", "Name one feature that shows the hen is a bird.", "It has feathers (or wings and a beak, lays hard-shelled eggs).", ["1 mark for a correct feature"], 2)])],
      [("The R in MRS GREN that means release of energy is:", "respiration", ["reproduction", "response", "rest"], "Respiration releases energy from food."),
       ("An animal with a backbone is a:", "vertebrate", ["invertebrate", "plant", "fungus"], "Vertebrates have a backbone."),
       ("Which animal group has feathers?", "birds", ["reptiles", "fish", "mammals"], "Only birds have feathers."),
       ("Sensitivity means:", "responding to changes around you", ["growing taller", "removing waste", "eating food"], "For example a plant bending towards light."),
       ("Which is NOT a living thing?", "a stone", ["a mushroom", "an ant", "a maize plant"], "A stone has no life processes.")],
      ["Fungi are not covered here; check whether Form 1 Basic Science mentions the five kingdoms."])

def cell_fig():
    d = Draw()
    d.sh.append(RECT(90, 35, 150, 110, fill="lightgreen", width=3))          # cell wall
    d.sh.append(RECT(96, 41, 138, 98, fill="white", width=1.2))              # membrane
    d.sh.append(RECT(160, 55, 60, 70, fill="lightblue", width=1.2))          # vacuole
    d.sh.append(CIRCLE(130, 90, 16, fill="lightorange", width=1.5))          # nucleus
    for x, y in ((115, 55), (140, 125), (110, 120)): d.sh.append(CIRCLE(x, y, 6, fill="green", width=1))
    d.sh += lab(90, 70, 78, 70, "cell wall", anchor="end", size=11)
    d.sh += lab(130, 90, 45, 108, "nucleus", anchor="end", size=11)
    d.sh += lab(190, 90, 258, 60, "vacuole", size=11)
    d.sh += lab(140, 125, 258, 140, "chloroplast", size=11)
    d.text(165, 170, "Plant cell", bold=True)
    d.sh.append(CIRCLE(390, 90, 52, fill="lightyellow", width=2))
    d.sh.append(CIRCLE(390, 90, 14, fill="lightorange", width=1.5))
    d.sh += lab(390, 90, 455, 52, "nucleus", size=11, anchor="end")
    d.text(390, 170, "Animal cell", bold=True)
    d.text(390, 154, "(no wall, no chloroplasts)", size=10)
    return d.fig(480, 190)
mg = (30 / 0.3)
assert abs(mg - 100) < 1e-9 and 10 * 40 == 400
build(ch, "cells", "Cells: the units of life", 25,
      ["State that living things are made of cells.", "Name the parts of plant and animal cells and give their jobs.", "Calculate magnification."],
      [("key", "definition", "The cell",
        "The **cell** is the basic unit of all living things. Most cells are too small to see without a **microscope**. Parts of a cell:\n\n- **Cell membrane**: controls what enters and leaves.\n- **Cytoplasm**: jelly where reactions happen.\n- **Nucleus**: controls the cell.\n\n**Plant cells** also have a **cell wall** (support), **chloroplasts** (photosynthesis) and a large **vacuole** (cell sap)."),
       ("fig", cell_fig(), "A plant cell and an animal cell.", "A rectangular plant cell labelled cell wall, nucleus, vacuole and chloroplast beside a round animal cell with only a nucleus labelled."),
       ("key", "formule", "Magnification",
        "**Magnification = size of the image ÷ actual size** (both in the same unit).\n\nFor a microscope: **total magnification = eyepiece × objective**. Example: ×10 eyepiece and ×40 objective give ×400.\n\nSpecialised cells: **red blood cells** carry oxygen; **root hair cells** absorb water; **nerve cells** carry messages."),
       ("key", "pieges", "Common mistakes",
        "- The cell wall (plants only, rigid) is not the cell membrane (all cells, thin).\n- Animal cells do not have chloroplasts.\n- A cell drawing must show the size: write the magnification beside it."),
       ("example", "Magnification of a drawing", "A cell is drawn 30 mm wide. Its actual width is 0.3 mm. Find the magnification.", ["Magnification = image size ÷ actual size.", "30 ÷ 0.3 = 100."], "×100"),
       ("example", "Total magnification", "Eyepiece ×10 and objective ×40. Find the total magnification.", ["Multiply: 10 × 40.", "10 × 40 = 400."], "×400")],
      [("mcq", "Which part of a cell controls its activities?", "nucleus", ["vacuole", "cell wall", "cytoplasm"], "The nucleus is the control centre."),
       ("num", "A microscope has an eyepiece of ×10 and an objective of ×20. Find the total magnification.", 200, "10 × 20 = 200.", 0),
       ("match", "Match each cell part with its job.", [("cell wall", "supports a plant cell"), ("chloroplast", "carries out photosynthesis"), ("cell membrane", "controls what enters and leaves"), ("nucleus", "controls the cell")], "Standard functions."),
       ("tf", "Animal cells have a cell wall.", False, "Only plant cells have a cell wall."),
       ("num", "A cell is drawn 50 mm long. Its actual length is 0.25 mm. Find the magnification.", 200, "50 ÷ 0.25 = 200.", 0),
       ("prob", "A student looks at onion skin and sees rectangular cells with a clear wall.", [("mcq", "Which feature shows these are plant cells?", "the cell wall", ["a nucleus", "cytoplasm", "a membrane"], "Cell walls are found in plant cells only."), ("mcq", "Why would onion skin not contain chloroplasts?", "it grows underground and gets no light", ["it is dead", "it has no cells", "it is an animal"], "Chloroplasts are found where light reaches the cells."), ("open", "Why is the onion skin cut very thin?", "Light must pass through it so that the cells can be seen.", ["1 mark for light passing through"], 2)])],
      [("The basic unit of living things is the:", "cell", ["atom", "organ", "tissue"], "All living things are made of cells."),
       ("Which cells carry chloroplasts?", "leaf cells of a plant", ["muscle cells", "red blood cells", "nerve cells"], "They carry out photosynthesis."),
       ("Total magnification equals:", "eyepiece × objective", ["eyepiece + objective", "objective − eyepiece", "eyepiece ÷ objective"], "Multiply the two."),
       ("A red blood cell is adapted to:", "carry oxygen", ["make food", "absorb water", "store starch"], "It contains haemoglobin."),
       ("Which part gives support to a plant cell?", "cell wall", ["membrane", "cytoplasm", "nucleus"], "The wall is rigid.")],
      ["Level is introductory; check whether organelles such as mitochondria are expected in Form 1."])

def plant_fig():
    d = Draw()
    d.line(200, 60, 200, 150, width=5, color="green")                       # stem
    d.sh.append(POLY([(200, 110), (260, 90), (270, 105), (200, 120)], fill="lightgreen", width=2))   # leaf R
    d.sh.append(POLY([(200, 90), (140, 70), (132, 85), (200, 100)], fill="lightgreen", width=2))     # leaf L
    d.sh.append(CIRCLE(200, 50, 14, fill="pink", width=2)); d.sh.append(CIRCLE(200, 50, 5, fill="yellow", width=1))
    d.line(100, 150, 300, 150, width=2, color="brown")                     # ground
    for x2, y2 in ((160, 190), (200, 200), (240, 190)): d.line(200, 150, x2, y2, width=3, color="brown")
    d.sh += lab(214, 50, 300, 35, "flower", size=11)
    d.sh += lab(265, 98, 330, 98, "leaf", size=11)
    d.sh += lab(200, 135, 300, 130, "stem", size=11)
    d.sh += lab(180, 175, 90, 185, "roots", size=11, anchor="end")
    return d.fig(400, 215)
build(ch, "plants", "Flowering plants: parts, food and germination", 25,
      ["Name the parts of a flowering plant and their functions.", "Write the word equation for photosynthesis.", "State the conditions needed for germination."],
      [("key", "retenir", "Parts of a plant and their jobs",
        "- **Roots**: hold the plant and absorb water and mineral salts.\n- **Stem**: supports the leaves and carries water and food.\n- **Leaves**: make food by photosynthesis.\n- **Flower**: reproduction; after fertilisation it forms the **fruit** with **seeds**."),
       ("fig", plant_fig(), "A flowering plant.", "A plant drawing with the flower, leaf, stem and roots labelled."),
       ("key", "formule", "Photosynthesis",
        "Green plants make food (glucose) in their leaves using **light** absorbed by **chlorophyll**:\n\n**carbon dioxide + water → glucose + oxygen**\n\nCarbon dioxide enters the leaf through tiny holes called **stomata**. Water comes from the roots. The glucose is used for energy and stored as **starch**."),
       ("key", "methode", "Germination",
        "A seed **germinates** when it has **water**, **air (oxygen)** and **warmth**. It does not need light. To test one factor, set up tubes that differ in only that factor: dry seeds (no water), seeds under oil-covered boiled water (no air), seeds in a refrigerator (cold), and a control with all three."),
       ("key", "pieges", "Common mistakes",
        "- Plants do not eat soil: they make food from carbon dioxide and water.\n- Seeds need oxygen (air) to germinate, not light.\n- The dry-seed tube lacks only water: keep every other condition the same."),
       ("example", "Interpret a germination experiment", "Four tubes hold bean seeds. Tube A is dry, B is moist and warm, C is moist but in a refrigerator, D is under a layer of oil on boiled water. Only B germinates. What do the results show?", ["A fails: water is needed. C fails: warmth is needed.", "D fails: air (oxygen) is needed. B has all three and germinates."], "Water, warmth and air are needed for germination."),
       ("example", "Which part?", "Cassava stems are cut and planted and grow into new plants. Cassava tubers store starch. Which plant part is the tuber and what does it store?", ["Cassava tubers grow underground and are swollen roots.", "They store starch made in the leaves."], "A swollen root storing starch.")],
      [("mcq", "Which part of a plant absorbs water?", "roots", ["flower", "leaf", "fruit"], "Roots absorb water and mineral salts."),
       ("match", "Match each plant part with its job.", [("root", "absorbs water"), ("leaf", "makes food"), ("stem", "supports the plant"), ("flower", "reproduction")], "Standard roles."),
       ("tf", "Seeds need light to germinate.", False, "They need water, air and warmth."),
       ("mcq", "Carbon dioxide enters a leaf through the:", "stomata", ["roots", "veins", "flower"], "Stomata are tiny pores."),
       ("open", "Write the word equation for photosynthesis and name the two conditions.", "carbon dioxide + water → glucose + oxygen; light and chlorophyll are needed.", ["1 mark: reactants", "1 mark: products", "1 mark: light and chlorophyll"]),
       ("prob", "Maize seeds are planted by a farmer in Bafoussam at the start of the rains.", [("mcq", "Which condition does the rain supply?", "water", ["warmth", "light", "salt"], "Moisture is needed for germination."), ("mcq", "Which gas do the seeds take in?", "oxygen", ["nitrogen", "hydrogen", "helium"], "Seeds respire."), ("open", "Why should seeds not be planted too deep in waterlogged soil?", "Waterlogged soil has little air, so the seeds lack oxygen and may not germinate.", ["1 mark: lack of oxygen"], 2)])],
      [("Leaves mainly make food by:", "photosynthesis", ["respiration only", "excretion", "digestion"], "Using light, water and carbon dioxide."),
       ("The pigment that makes leaves green and absorbs light is:", "chlorophyll", ["starch", "iodine", "glucose"], "It absorbs light."),
       ("A seed does NOT need which for germination?", "light", ["water", "air", "warmth"], "Light is not required."),
       ("Plants store the food they make mainly as:", "starch", ["salt", "oxygen", "chlorophyll"], "Starch is the storage form."),
       ("The fruit develops from the:", "flower", ["root", "stem", "leaf"], "After fertilisation the ovary becomes the fruit.")],
      ["Cassava tuber described as a swollen root (standard); check local textbook wording."])

# =========================================================== 4 Air, water, energy, environment
ch = p.chapter("earth", "Air, water, energy and the environment", "Form 1 Basic Science — Air, water, energy, hygiene and environment (to be checked)")
airbars = bars([("nitrogen", 78, "blue"), ("oxygen", 21, "green"), ("other gases", 1, "orange")], w=400, h=240)
used = 21; left = 100 - used
assert left == 79
build(ch, "air", "Air and its composition", 20,
      ["State the composition of air.", "Describe tests for oxygen and carbon dioxide.", "Explain why burning needs oxygen."],
      [("key", "retenir", "What is in air?",
        "Air is a **mixture** of gases: about **78 % nitrogen**, **21 % oxygen**, about **1 % argon and other gases**, and a very small amount (about 0.04 %) of **carbon dioxide**. It also contains **water vapour**, which varies from place to place. Air has mass, takes up space and exerts pressure."),
       ("fig", airbars, "Approximate percentage by volume of the main gases in dry air.", "A bar chart: nitrogen 78, oxygen 21 and other gases 1 (percent)."),
       ("key", "methode", "Tests and uses",
        "- **Oxygen** relights a glowing splint; it is used in respiration and burning.\n- **Carbon dioxide** turns **limewater milky**; plants use it for photosynthesis.\n- **Nitrogen** is unreactive; plants need it in the soil as nitrates.\n- **Burning** (combustion) needs oxygen: a candle in a closed jar goes out when most of the oxygen has been used, and the water level in the jar rises by about one fifth."),
       ("key", "pieges", "Common mistakes",
        "- Air is not pure oxygen: most of it is nitrogen.\n- Carbon dioxide is a gas that puts out flames; oxygen helps things burn.\n- A closed container of air does not contain 'nothing': it contains gas with mass."),
       ("example", "Oxygen used by a candle", "In an experiment, 100 cm³ of air is left over iron wool until 79 cm³ of gas remains. Estimate the percentage of oxygen in the air.", ["Oxygen used = 100 − 79 = 21 cm³.", "Percentage = 21 ÷ 100 × 100 = 21 %."], "About 21 %"),
       ("example", "Identify the gas", "A gas relights a glowing splint. Another turns limewater milky. Name each gas.", ["Relighting a glowing splint is the test for oxygen.", "Turning limewater milky is the test for carbon dioxide."], "Oxygen; carbon dioxide")],
      [("mcq", "Which gas is the most abundant in air?", "nitrogen", ["oxygen", "carbon dioxide", "hydrogen"], "About 78 %."),
       ("tf", "Limewater turns milky when carbon dioxide is bubbled through it.", True, "This is the standard test."),
       ("num", "Dry air contains about 21 % oxygen. How many cm³ of oxygen are there in 500 cm³ of air?", 105, "500 × 21 / 100 = 105 cm³.", 0.5, "cm³"),
       ("mcq", "A glowing splint relights in a gas. The gas is:", "oxygen", ["nitrogen", "carbon dioxide", "water vapour"], "Test for oxygen."),
       ("open", "Explain why a candle goes out when covered by a jar.", "The burning candle uses up the oxygen in the jar; when the oxygen is too low the flame cannot continue and goes out.", ["1 mark: oxygen used up", "1 mark: burning needs oxygen"]),
       ("prob", "A student holds a jar of 200 cm³ of air over a burning candle until the flame goes out.", [("num", "About how many cm³ of oxygen were used up if about one fifth of the air was oxygen?", 40, "200 ÷ 5 = 40 cm³.", 1, "cm³"), ("mcq", "Which gas is formed when the candle burns?", "carbon dioxide", ["hydrogen", "helium", "nitrogen"], "Burning wax gives carbon dioxide and water."), ("open", "How could the student show this gas is present?", "Shake a little limewater in the jar: it turns milky.", ["1 mark: limewater turns milky"], 2)])],
      [("The percentage of oxygen in air is about:", "21 %", ["78 %", "1 %", "50 %"], "About one fifth."),
       ("Carbon dioxide is tested with:", "limewater", ["iodine", "a magnet", "a thermometer"], "It turns milky."),
       ("Air is best described as a:", "mixture of gases", ["single gas", "liquid", "solid"], "It has several gases."),
       ("Which gas do plants take in for photosynthesis?", "carbon dioxide", ["nitrogen", "argon", "helium"], "Plants use carbon dioxide."),
       ("What do burning things need from the air?", "oxygen", ["nitrogen", "argon", "dust"], "Oxygen supports burning.")],
      ["Composition values are standard approximations; carbon dioxide level is about 0.04 % (it changes slowly)."])

def cycle_fig():
    d = Draw()
    d.sh.append(RECT(0, 175, 480, 55, fill="lightblue", stroke="none", width=0))
    d.sh.append(POLY([(300, 175), (380, 130), (480, 150), (480, 175)], fill="lightgreen", width=2))
    d.sh.append(CIRCLE(50, 40, 22, fill="yellow", width=2))
    for cx, cy, r in ((235, 45, 20), (260, 38, 24), (288, 46, 18), (262, 55, 20)): d.sh.append(CIRCLE(cx, cy, r, fill="lightgrey", width=1.5))
    d.line(130, 170, 130, 90, color="orange", arrow="end"); d.text(130, 82, "evaporation", size=11)
    d.line(200, 80, 220, 70, color="blue", width=1.5)
    d.line(265, 80, 265, 130, color="blue", arrow="end"); d.text(300, 108, "rain", size=11)
    d.line(380, 135, 330, 172, color="blue", arrow="end"); d.text(412, 120, "run-off", size=11)
    d.text(262, 20, "condensation", size=11)
    d.text(130, 210, "sea / river / lake", size=11)
    return d.fig(480, 230)
tank = 1000 / (5 * 25)
assert tank == 8
build(ch, "water", "Water and the water cycle", 20,
      ["Describe the water cycle.", "State the uses of water and the steps in making water safe to drink.", "Describe tests for water."],
      [("key", "definition", "The water cycle",
        "The Sun's heat makes water **evaporate** from seas, rivers and lakes (and plants lose water vapour). The vapour rises, cools and **condenses** to form clouds. Water falls as **rain** (precipitation), collects in rivers and underground, and flows back to the sea. The cycle is driven by the Sun."),
       ("fig", cycle_fig(), "The water cycle.", "A picture with the sun, a cloud, the sea and land. Arrows show evaporation upward, condensation to a cloud, rain falling and run-off back to the sea."),
       ("key", "retenir", "Making water safe",
        "Water for drinking must be free of germs and harmful chemicals. In a town it is **screened**, left to settle, **filtered** through sand and gravel, and treated with **chlorine** to kill germs. At home, water of doubtful quality can be **boiled** for several minutes, kept in a clean covered container and used with clean cups. Dirty water can spread diseases such as cholera and typhoid."),
       ("key", "methode", "Tests for water",
        "- **Anhydrous copper(II) sulphate** (white) turns **blue** with water.\n- **Cobalt chloride paper** turns from **blue to pink**.\n- Pure water **boils at 100 °C** and **freezes at 0 °C** at sea level; a solution of salt boils above 100 °C."),
       ("key", "pieges", "Common mistakes",
        "- Clear water is not always safe: germs cannot be seen.\n- Filtering through sand does not kill germs; boiling or chlorine does.\n- Rain falls from clouds that formed by condensation, not by boiling."),
       ("example", "How long will a tank last?", "A tank holds 1000 L. Five people use about 25 L each per day. How many days will it last?", ["Daily use = 5 × 25 = 125 L.", "Days = 1000 ÷ 125 = 8."], "8 days"),
       ("example", "Test a liquid", "White copper sulphate powder turns blue when a few drops of a liquid are added. What does this show?", ["The change from white to blue is the test for water.", "It shows that the liquid contains water, not that it is pure."], "The liquid contains water.")],
      [("mcq", "The change of liquid water into vapour is:", "evaporation", ["condensation", "melting", "freezing"], "Liquid to gas."),
       ("mcq", "Which result proves that a liquid contains water?", "anhydrous copper sulphate turns blue", ["litmus turns red", "iodine turns blue-black", "limewater turns milky"], "White to blue."),
       ("tf", "Clear water is always safe to drink.", False, "Germs and chemicals can be present in clear water."),
       ("num", "A household of 4 uses 30 L of water each per day. How many litres do they use in 7 days?", 840, "4 × 30 × 7 = 840 L.", 0, "L"),
       ("open", "Describe how water could be made safer to drink at home.", "Filter it through a clean cloth to remove dirt, then boil it for several minutes and store it in a clean covered container.", ["1 mark: remove dirt", "1 mark: boil", "1 mark: clean covered storage"]),
       ("prob", "A village collects rainwater from a roof in the rainy season.", [("mcq", "Where did this water come from before it fell?", "clouds formed by condensation of water vapour", ["underground rocks", "the Moon", "boiling springs"], "Water cycle."), ("mcq", "Which step makes the stored water safer?", "boiling it", ["adding salt", "freezing it", "stirring it"], "Heat kills germs."), ("open", "Give one reason for keeping the storage container covered.", "To stop dirt, insects and mosquitoes entering the water.", ["1 mark for a sensible reason"], 2)])],
      [("What drives the water cycle?", "heat from the Sun", ["wind only", "the Moon", "electricity"], "The Sun evaporates water."),
       ("Chlorine is added to water to:", "kill germs", ["add taste", "make it salty", "make it colder"], "It disinfects."),
       ("Cobalt chloride paper turns what colour in water?", "pink", ["black", "green", "yellow"], "Blue to pink."),
       ("Water vapour forms clouds by:", "condensation", ["melting", "evaporation", "sublimation"], "Vapour cools into droplets."),
       ("Pure water at sea level boils at:", "100 °C", ["50 °C", "0 °C", "120 °C"], "Boiling point.")],
      ["Water treatment steps are simplified; health advice (boiling time, chlorine) to be confirmed with a health worker or water authority."])

energy_fl = flow(["Water in|the dam", "Moving|water", "Turbine and|generator", "Electricity|to homes"], w=480, size=11, bh=46, gap=22)
build(ch, "energy", "Forms and sources of energy", 25,
      ["Name the forms of energy and give examples.", "Describe energy changes and state that energy is conserved.", "Distinguish renewable from non-renewable sources."],
      [("key", "definition", "What is energy?",
        "**Energy** is the ability to do work or make something happen. It is measured in **joules (J)**. Forms of energy include **kinetic** (moving things), **potential** (stored by height or stretch), **heat**, **light**, **sound**, **electrical**, **chemical** (food, fuel, batteries) and **nuclear**."),
       ("fig", energy_fl, "Energy changes in a hydroelectric power station.", "Four boxes joined by arrows: water in a dam, moving water, turbine and generator, electricity to homes."),
       ("key", "retenir", "Energy changes and conservation",
        "Energy is **changed** from one form to another but is never created or destroyed: this is the **conservation of energy**. Some energy always becomes unwanted heat. Examples: a torch changes chemical energy to electrical, then to light and heat; a muscle changes chemical energy from food to kinetic energy and heat."),
       ("key", "retenir", "Sources of energy",
        "**Renewable** sources do not run out: sunlight, wind, flowing water (hydroelectricity), biomass. **Non-renewable** sources will run out: coal, oil, natural gas. In Cameroon much electricity comes from dams on rivers such as the Sanaga (Edéa and Song Loulou), as well as gas and oil power stations."),
       ("key", "pieges", "Common mistakes",
        "- Energy is not 'used up': it changes into other forms such as heat.\n- A stretched elastic band stores potential energy; it is not moving.\n- Firewood is a renewable source only if trees are replanted."),
       ("example", "Energy chain", "Write the energy changes when a cook burns charcoal to warm water for a bath.", ["Charcoal stores chemical energy.", "Burning changes it into heat (and light), which warms the water."], "Chemical energy to heat (and light)."),
       ("example", "Classify the sources", "Sort into renewable and non-renewable: sunlight, petrol, wind, coal.", ["Sunlight and wind are replaced naturally.", "Petrol and coal take millions of years to form."], "Renewable: sunlight, wind. Non-renewable: petrol, coal.")],
      [("mcq", "The unit of energy is the:", "joule", ["newton", "watt", "metre"], "Energy is in J."),
       ("match", "Match each item with the energy it mainly stores or has.", [("a moving car", "kinetic"), ("a battery", "chemical"), ("a book on a high shelf", "potential"), ("a glowing bulb", "light")], "Standard examples."),
       ("tf", "Energy can be destroyed when a machine is switched off.", False, "Energy is conserved; it changes to other forms."),
       ("mcq", "Which of these is a renewable source?", "wind", ["coal", "oil", "natural gas"], "Wind is replaced naturally."),
       ("open", "A student rubs her hands together and they get warm. State the energy change.", "Kinetic energy of the moving hands changes into heat energy.", ["1 mark: kinetic to heat"]),
       ("prob", "A hydroelectric station uses water from a high dam to turn a turbine.", [("mcq", "The water in the dam stores:", "potential energy", ["sound energy", "nuclear energy", "light energy"], "It is stored by height."), ("mcq", "The turbine drives a generator which makes:", "electrical energy", ["chemical energy", "potential energy only", "sound only"], "The generator produces electricity."), ("open", "Give one advantage of hydroelectricity.", "It is renewable and gives no smoke or carbon dioxide while running.", ["1 mark for a correct advantage"], 2)])],
      [("The energy stored in food is:", "chemical energy", ["sound energy", "kinetic energy", "nuclear energy"], "Food is a chemical store."),
       ("Energy changes from one form to another but is:", "never destroyed", ["always lost", "doubled", "made from nothing"], "Conservation."),
       ("A stretched spring has:", "potential energy", ["no energy", "sound energy only", "chemical energy"], "Stored by stretching."),
       ("A source that never runs out is:", "renewable", ["non-renewable", "fossil", "wasted"], "Sunlight is an example."),
       ("A bulb changes electrical energy into light and:", "heat", ["food", "water", "chemical energy"], "Some energy is wasted as heat.")],
      ["Check electricity sources: names of stations (Edéa, Song Loulou on the Sanaga; Lagdo on the Benue) are well known but should be confirmed; proportions of the mix not stated."])

threeR = grid(["Reduce|use less", "Reuse|use again", "Recycle|make new items", "Compost|food waste to soil"], cols=2, bh=50, size=11)
build(ch, "hygiene-environment", "Hygiene, waste and the environment", 20,
      ["State good habits of personal and community hygiene.", "Classify waste and describe how to manage it.", "Name types of pollution and their effects."],
      [("key", "retenir", "Personal and community hygiene",
        "**Hygiene** means keeping clean to stay healthy. Wash your hands with soap and clean water **before eating or preparing food and after using the toilet**. Keep nails short, brush your teeth, bathe regularly, wash fruit and vegetables, cover food, use latrines and keep drains and compounds clean. Do not leave water standing where mosquitoes can breed."),
       ("fig", threeR, "Managing waste: reduce, reuse, recycle, compost.", "Four boxes: reduce means use less; reuse means use again; recycle means make new items; compost turns food waste into soil."),
       ("key", "definition", "Waste and pollution",
        "**Biodegradable** waste rots naturally (peelings, leaves). **Non-biodegradable** waste does not (plastic bags, bottles, metal). **Pollution** is the addition of harmful substances to the environment: **air** (smoke, fumes), **water** (sewage, oil, chemicals) and **soil** (rubbish, chemicals). Plastic bags can block drains and cause floods."),
       ("key", "pieges", "Common mistakes",
        "- Burning rubbish is not a safe way to get rid of it: the smoke is harmful.\n- Throwing waste in a river pollutes the water that others drink.\n- Recycling is not the same as reuse: reuse needs no change of the item."),
       ("example", "Sort the waste", "Sort into biodegradable and non-biodegradable: banana peel, plastic bottle, dry leaves, tin can.", ["Peel and leaves rot and are food for decomposers.", "A plastic bottle and a tin can do not rot quickly."], "Biodegradable: banana peel, dry leaves. Non-biodegradable: plastic bottle, tin can."),
       ("example", "Choose an action", "A family wants to reduce the plastic bags it throws away. Suggest two actions.", ["Reduce: carry a basket to the market instead of taking new bags.", "Reuse: use strong bags many times."], "Carry a reusable basket and reuse bags.")],
      [("mcq", "Which of these household wastes is biodegradable?", "banana peel", ["plastic bottle", "tin can", "glass jar"], "It rots naturally."),
       ("tf", "Hands should be washed with soap before eating.", True, "This removes germs."),
       ("match", "Match the pollution with a cause.", [("air pollution", "smoke from burning tyres"), ("water pollution", "sewage in a river"), ("soil pollution", "dumped rubbish and chemicals"), ("noise pollution", "very loud machines")], "Typical causes."),
       ("mcq", "Standing water near a house can be dangerous because:", "mosquitoes can breed in it", ["it is too clean", "it freezes", "it is salty"], "Mosquito larvae develop in still water."),
       ("open", "Describe three ways a community can keep its environment clean.", "Dispose of rubbish in bins or pits; clear drains; use latrines; sweep compounds; avoid dumping waste in rivers (any three).", ["1 mark each, maximum 3"]),
       ("prob", "A market in a town produces vegetable waste, plastic bags and metal cans every day.", [("mcq", "Which waste can be turned into compost?", "vegetable waste", ["plastic bags", "metal cans", "glass"], "It is biodegradable."), ("mcq", "Metal cans are best:", "collected for recycling", ["burnt in the open", "thrown in a stream", "buried near the well"], "Metals can be recycled."), ("open", "Give two problems caused by plastic bags thrown into drains.", "They block the drains so water cannot flow, causing floods and standing water for mosquitoes.", ["1 mark each, maximum 2"], 2)])],
      [("A good time to wash hands is:", "before preparing food", ["after taking a bath only", "never", "only on Sunday"], "Hand washing prevents spread of germs."),
       ("Waste that rots naturally is:", "biodegradable", ["non-biodegradable", "recycled", "plastic"], "Decomposers break it down."),
       ("Which of these is a way of reusing?", "using a bottle again for water", ["burning the bottle", "dumping it in a river", "burying it"], "Same item, used again."),
       ("Smoke from burning waste causes:", "air pollution", ["water pollution", "soil erosion", "noise pollution"], "Smoke harms air quality."),
       ("Compost is made from:", "food and plant waste", ["plastic", "metal", "glass"], "It rots into a soil improver.")],
      ["Health and hygiene advice is conservative but must be reviewed by a health worker; check the local names of waste-collection practice."])

p.write()
