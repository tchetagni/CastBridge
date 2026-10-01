import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *

p = Pack("form2-science", "Basic Science — Form 2", level="Form 2", subject="sciences", cursus="secondary",
         description="An integrated second course in science for Form 2: elements and compounds, acids and bases, forces and speed, heat, light, electricity and magnetism, the human body, disease prevention and ecosystems. Short lessons, worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Basic Science, Form 2 (integrated science) — to be checked against the official syllabus")

# =========================================================== 1 Chemistry ideas
ch = p.chapter("chemistry", "Elements, compounds, acids and bases", "Form 2 Basic Science — Elements, compounds, acids and bases (to be checked)")
mixcomp = grid(["Mixture|parts keep their properties", "Compound|new properties", "Mixture|easy to separate", "Compound|needs a chemical reaction", "Mixture|any proportions", "Compound|fixed proportions"], cols=2, bh=44, size=11, colors=["lightblue", "lightorange"])
atoms = {"H2O": 2 + 1, "CO2": 1 + 2, "Ca(OH)2": 1 + 2 + 2, "H2SO4": 2 + 1 + 4}
assert atoms["Ca(OH)2"] == 5 and atoms["H2SO4"] == 7
build(ch, "elements-compounds", "Elements, compounds and chemical change", 25,
      ["Define element, compound and mixture and give examples.", "Use chemical symbols and simple formulae.", "Distinguish physical changes from chemical changes."],
      [("key", "definition", "Elements and compounds",
        "An **element** is a pure substance made of only one kind of atom; it cannot be split into simpler substances by chemical means. Each has a **symbol**: H (hydrogen), O (oxygen), C (carbon), N (nitrogen), Na (sodium), Cl (chlorine), Fe (iron), Cu (copper). A **compound** has two or more elements **chemically joined** in fixed proportions, for example water, H₂O, and carbon dioxide, CO₂."),
       ("fig", mixcomp, "Mixtures (blue) compared with compounds (orange).", "Six boxes alternating between mixture and compound: parts keep their properties versus new properties, easy to separate versus needs a chemical reaction, any proportions versus fixed proportions."),
       ("key", "retenir", "Chemical and physical change",
        "In a **physical change** (melting, dissolving) no new substance is made and it is usually easy to reverse. In a **chemical change** a **new substance** forms: signs are a colour change, a gas given off, heat or light, or a solid forming. Examples: burning wood, rusting iron, cooking an egg, souring milk. The formula H₂O shows 2 atoms of H and 1 atom of O."),
       ("key", "pieges", "Common mistakes",
        "- Air is a mixture, not a compound.\n- A compound is not just two things stuck together: iron + sulphur heated gives iron sulphide, which is **not magnetic** and looks different.\n- Dissolving salt in water is a physical change; the salt can be recovered."),
       ("example", "Iron and sulphur", "Iron filings and sulphur powder are mixed: a magnet picks out the iron. The mixture is then heated strongly and a black solid forms that the magnet does not attract. Explain.", ["Before heating it is a mixture: each substance keeps its properties.", "Heating causes a chemical reaction: the new compound, iron sulphide, has new properties."], "A mixture became a compound by a chemical change."),
       ("example", "Count the atoms", "How many atoms are in one unit of Ca(OH)₂?", ["Ca gives 1. The bracket (OH) is multiplied by 2: 2 O and 2 H.", "Total = 1 + 2 + 2 = 5."], "5 atoms")],
      [("mcq", "Which of these is an element?", "iron", ["water", "salt", "sugar"], "Iron contains only iron atoms."),
       ("match", "Match each substance with its type.", [("air", "a mixture of gases"), ("water", "a compound of hydrogen and oxygen"), ("oxygen", "an element"), ("salt (sodium chloride)", "a compound of sodium and chlorine")], "Air has several gases; water and salt contain two elements; oxygen has only one."),
       ("num", "How many atoms are in one unit of H₂SO₄?", 7, "2 + 1 + 4 = 7.", 0),
       ("mcq", "Which of these is a chemical change?", "wood burning", ["ice melting", "salt dissolving", "water boiling"], "Burning makes new substances."),
       ("tf", "A compound can be separated into its elements by filtration.", False, "Compounds need chemical methods, not simple physical methods."),
       ("prob", "A teacher heats a piece of blue copper sulphate crystal; it turns white and steam is given off. Adding water turns it blue again.", [("mcq", "Heating it gave off:", "water", ["oxygen", "iron", "salt"], "The water of crystallisation was lost."), ("mcq", "This test is used to:", "detect water", ["detect acids", "detect starch", "detect oxygen"], "White copper sulphate turns blue with water."), ("open", "Is the change from blue to white a physical or a chemical change? Give a reason.", "It is a chemical change because a new substance (white anhydrous copper sulphate) forms and the colour changes; it is reversible by adding water.", ["1 mark: chemical change", "1 mark: reason"], 2)])],
      [("A substance made of only one kind of atom is a(n):", "element", ["compound", "mixture", "solution"], "Elements have one kind of atom."),
       ("The symbol of sodium is:", "Na", ["S", "So", "N"], "Na from the Latin natrium."),
       ("Which sign shows a chemical change has happened?", "a new substance forms", ["a solid gets smaller", "a liquid is stirred", "a bottle is shaken"], "A new substance is the key sign."),
       ("The formula CO₂ shows that the compound has:", "1 carbon and 2 oxygen atoms", ["2 carbon and 1 oxygen atoms", "3 carbon atoms", "2 carbon atoms"], "The subscript refers to the atom before it."),
       ("Salt dissolving in water is a:", "physical change", ["chemical change", "nuclear change", "burning"], "The salt can be recovered by evaporation.")],
      ["Atoms are mentioned only briefly (Form 3 chemistry develops atomic structure). Check that the Form 2 syllabus uses the term 'physical change' rather than 'temporary change'."])

phbar = Draw()
cols_ph = ["red", "red", "red", "orange", "orange", "yellow", "yellow", "green", "cyan", "cyan", "blue", "blue", "purple", "purple", "purple"]
for i, c in enumerate(cols_ph):
    phbar.sh.append(RECT(10 + i * 30, 50, 30, 36, fill=c, stroke="ink", width=1))
    phbar.text(25 + i * 30, 106, str(i), size=11)
phbar.text(70, 30, "acids", size=12, bold=True); phbar.text(235, 30, "neutral", size=12, bold=True); phbar.text(400, 30, "alkalis", size=12, bold=True)
phbar.text(235, 140, "pH scale (colours of universal indicator, approximate)", size=11)
ph_fig = phbar.fig(480, 160)
build(ch, "acids-bases", "Acids, bases and indicators", 25,
      ["Describe acids and alkalis and give common examples.", "Use litmus and universal indicator and the pH scale.", "Describe neutralisation and one use."],
      [("key", "definition", "Acids and alkalis",
        "**Acids** taste sour and have a **pH below 7**: lime and lemon juice (citric acid), vinegar (ethanoic acid), sour milk. **Alkalis** (soluble bases) feel soapy and have a **pH above 7**: soap, wood-ash solution, limewater, many cleaners. Pure water and salt solution are **neutral**, pH 7. Never taste or touch laboratory acids and alkalis: strong ones are corrosive."),
       ("fig", ph_fig, "The pH scale from 0 to 14.", "A strip from pH 0 to 14 coloured red through orange, yellow, green and blue to purple, with acids at the left, neutral in the middle and alkalis at the right."),
       ("key", "methode", "Indicators",
        "An **indicator** changes colour in acid or alkali.\n\n- **Litmus**: acid turns blue litmus **red**; alkali turns red litmus **blue**.\n- **Universal indicator**: red/orange (strong/weak acid), yellow (very weak acid), **green (neutral)**, blue/purple (alkali). It gives an approximate pH."),
       ("key", "retenir", "Neutralisation",
        "**Acid + alkali → salt + water.** When just enough acid and alkali are mixed the pH becomes 7. Uses: **antacid** tablets ease acid indigestion; farmers add **lime** to acid soil; toothpaste is mildly alkaline."),
       ("key", "pieges", "Common mistakes",
        "- Do not test unknown chemicals by taste.\n- pH 7 is neutral; a higher number means more alkaline.\n- Neutralisation does not mean 'nothing is left': a salt and water are formed."),
       ("example", "Read the colour", "A solution turns universal indicator blue-purple. Is it an acid or an alkali? Suggest an example.", ["Blue to purple shows a pH above 7.", "Soap solution or limewater are common alkalis."], "An alkali (e.g. soapy water)."),
       ("example", "Use of neutralisation", "A farmer's soil is too acidic for maize. What can she add and why?", ["Lime (a base) reacts with the acid in the soil.", "This raises the pH towards neutral."], "Lime, to neutralise the acid.")],
      [("mcq", "A solution has pH 3. It is:", "acidic", ["neutral", "alkaline", "a base"], "Below 7 means acidic."),
       ("match", "Match each indicator result with the type of solution.", [("blue litmus turns red", "acid"), ("red litmus turns blue", "alkali"), ("universal indicator turns green", "neutral"), ("universal indicator turns purple", "strong alkali")], "Standard indicator colours."),
       ("tf", "Lime juice is an alkali.", False, "It contains citric acid."),
       ("mcq", "Acid indigestion is eased by an antacid because it:", "neutralises the acid", ["adds more acid", "freezes the stomach", "makes food salty"], "Antacids are weak bases."),
       ("open", "Explain what happens when an acid and an alkali are mixed in exactly the right amounts.", "They neutralise each other to form a salt and water and the solution becomes neutral, pH 7.", ["1 mark: salt and water formed", "1 mark: pH 7 / neutral"]),
       ("prob", "A pupil tests four solutions with universal indicator: P gives red, Q gives green, R gives blue, S gives yellow.", [("mcq", "Which is neutral?", "Q", ["P", "R", "S"], "Green is pH 7."), ("mcq", "Which is the strongest acid?", "P", ["Q", "R", "S"], "Red means a very low pH."), ("open", "Name which two are acids and which one is an alkali.", "P and S are acids; R is an alkali.", ["1 mark: P and S acids", "1 mark: R alkali"], 2)])],
      [("The pH of a neutral solution is:", "7", ["0", "14", "1"], "pH 7."),
       ("Blue litmus paper turns red in:", "an acid", ["an alkali", "pure water", "oil"], "Acids turn blue litmus red."),
       ("Which of these is an acid?", "vinegar", ["soap", "limewater", "wood ash solution"], "Vinegar contains ethanoic acid."),
       ("Acid + alkali gives:", "salt and water", ["hydrogen and oxygen", "only a gas", "a metal"], "Neutralisation."),
       ("Which material could be spread on acid soil?", "lime", ["vinegar", "lemon juice", "battery acid"], "Lime is alkaline.")],
      ["Universal indicator colours are approximate and vary with the brand; check the colour chart used in school.", "Mention of 'base' vs 'alkali' kept simple (alkali = soluble base)."])

# =========================================================== 2 Forces and motion
ch = p.chapter("forces", "Forces and motion", "Form 2 Basic Science — Forces, friction, weight and speed (to be checked)")
def forces_fig():
    d = Draw()
    d.sh.append(RECT(150, 100, 100, 60, fill="lightorange", width=2))
    d.line(100, 190, 300, 190, width=3, color="brown")
    d.line(80, 130, 148, 130, arrow="end", color="red", width=3); d.text(70, 118, "push", size=12, anchor="end")
    d.line(330, 130, 252, 130, arrow="end", color="blue", width=3); d.text(335, 118, "friction", size=12, anchor="start")
    d.line(200, 162, 200, 230, arrow="end", color="green", width=3); d.text(210, 226, "weight", size=12, anchor="start")
    d.line(200, 98, 200, 40, arrow="end", color="purple", width=3); d.text(210, 52, "reaction of table", size=12, anchor="start")
    return d.fig(440, 240)
w_ = 60 * 10; assert w_ == 600
build(ch, "forces", "Forces, friction and weight", 25,
      ["State what forces do and measure them in newtons.", "Describe friction, its advantages and disadvantages.", "Distinguish mass from weight and use W = m × g with g = 10 N/kg."],
      [("key", "definition", "Force",
        "A **force** is a push or a pull. Its unit is the **newton (N)**, measured with a **spring balance (newton meter)**. A force can start or stop motion, change speed or direction, or change the shape of an object. **Balanced forces** do not change the motion of an object; **unbalanced forces** do."),
       ("fig", forces_fig(), "Forces on a box pushed along a table.", "A box with arrows: push to the right, friction to the left, weight downward and the reaction of the table upward."),
       ("key", "retenir", "Friction",
        "**Friction** is a force that opposes motion between surfaces in contact. It is useful for walking, braking and gripping tyres. It is harmful when it wears parts and makes heat. It is **reduced** by oil, grease, polishing, wheels and ball bearings; it is **increased** by rough surfaces and treaded tyres."),
       ("key", "formule", "Mass and weight",
        "**Mass** is the amount of matter in an object: kg, the same everywhere. **Weight** is the pull of gravity on it: newtons, and it changes with place (on the Moon about one sixth of that on Earth).\n\n**W = m × g**, with **g = 10 N/kg** on Earth (used in this course)."),
       ("key", "pieges", "Common mistakes",
        "- Mass is not weight: a 60 kg student has a mass of 60 kg and a weight of 600 N.\n- A balance measures mass; a spring balance measures weight (force).\n- Friction does not always slow things: without it you could not walk."),
       ("example", "Weight of a student", "A student has a mass of 60 kg. Find her weight on Earth (g = 10 N/kg).", ["W = m × g.", "W = 60 × 10 = 600 N."], "600 N"),
       ("example", "Find the mass", "A sack of cassava has a weight of 450 N. Find its mass (g = 10 N/kg).", ["m = W ÷ g.", "m = 450 ÷ 10 = 45 kg."], "45 kg")],
      [("mcq", "The unit of force is the:", "newton", ["kilogram", "joule", "metre"], "Force is in N."),
       ("num", "Find the weight of a 25 kg bag of rice (g = 10 N/kg).", 250, "25 × 10 = 250 N.", 0, "N"),
       ("mcq", "Which of these REDUCES friction?", "oil on moving parts", ["a rough surface", "a treaded tyre", "pressing harder"], "Oil makes surfaces slide."),
       ("num", "A box has weight 120 N. What is its mass? (g = 10 N/kg)", 12, "120 ÷ 10 = 12 kg.", 0, "kg"),
       ("open", "Give two ways in which friction is useful and one way in which it is a nuisance.", "Useful: it lets us walk without slipping and it lets brakes stop a vehicle. Nuisance: it wears out machine parts and makes them hot.", ["1 mark each useful use", "1 mark for a harmful effect"]),
       ("prob", "An astronaut has a mass of 70 kg. Use g = 10 N/kg on Earth and about 1.6 N/kg on the Moon.", [("num", "Weight on Earth in N.", 700, "70 × 10 = 700 N.", 0, "N"), ("num", "Weight on the Moon in N.", 112, "70 × 1.6 = 112 N.", 0.5, "N"), ("mcq", "His mass on the Moon is:", "70 kg", ["112 kg", "11.2 kg", "0 kg"], "Mass does not change.")])],
      [("Weight is measured with a:", "spring balance", ["measuring cylinder", "thermometer", "stopwatch"], "Weight is a force."),
       ("Mass is measured in:", "kilograms", ["newtons", "joules", "watts"], "Mass is in kg."),
       ("Friction acts:", "against the direction of motion", ["in the direction of motion", "upwards only", "only in liquids"], "It opposes motion."),
       ("If g = 10 N/kg, the weight of 5 kg is:", "50 N", ["5 N", "15 N", "0.5 N"], "5 × 10."),
       ("Balanced forces on a moving car mean its speed:", "stays the same", ["increases", "decreases", "becomes zero"], "No resultant force.")],
      ["g = 10 N/kg is used for simplicity (9.8 is more exact); the Moon value 1.6 N/kg is the textbook figure.", "The 'reaction of the table' (normal force) may be beyond the Form 2 syllabus."])

dist = [(0, 0), (2, 100), (2.5, 100), (4, 200)]
v1 = 100 / 2; v2 = 0; v3 = 100 / 1.5; avg = 200 / 4
assert v1 == 50 and abs(v3 - 66.666666) < 1e-3 and avg == 50
dt_fig = segs_graph(0, 4, 0, 200, [(0, 0, 2, 100, "blue"), (2, 100, 2.5, 100, "red"), (2.5, 100, 4, 200, "blue")], xlabel="time (h)", ylabel="distance (km)", grid=1, w=420, h=260)
kmh = 72; assert kmh / 3.6 == 20
build(ch, "speed", "Speed and distance-time graphs", 25,
      ["Calculate speed, distance and time.", "Convert between km/h and m/s.", "Read a distance-time graph."],
      [("key", "formule", "Speed",
        "**Speed = distance ÷ time**, so **distance = speed × time** and **time = distance ÷ speed**. Units: m/s or km/h. **Average speed = total distance ÷ total time**. To change units: **km/h ÷ 3.6 = m/s** and m/s × 3.6 = km/h."),
       ("fig", dt_fig, "Distance-time graph of a bus journey (illustrative numbers).", "A graph with time in hours on the x-axis and distance in km on the y-axis: a rising line to 100 km at 2 h, a flat part until 2.5 h and a rising line to 200 km at 4 h."),
       ("key", "methode", "Reading the graph",
        "On a **distance-time graph** the **slope** is the speed. A **straight rising line** means constant speed; a **steeper line** means faster; a **horizontal line** means stopped. In the graph above: 0 to 2 h: 100 km in 2 h = 50 km/h; 2 to 2.5 h: stopped; 2.5 to 4 h: 100 km in 1.5 h ≈ 66.7 km/h."),
       ("key", "pieges", "Common mistakes",
        "- Use matching units: do not divide km by seconds without converting.\n- A flat line on a distance-time graph means the object is **stopped**, not moving at constant speed.\n- Average speed is total distance ÷ total time, **not** the average of two speeds when times differ."),
       ("example", "Average speed", "In the journey of the graph the bus travels 200 km in 4 h including the stop. Find the average speed.", ["Average speed = total distance ÷ total time.", "200 ÷ 4 = 50 km/h."], "50 km/h"),
       ("example", "Change units", "A motorbike travels at 72 km/h. Express this in m/s.", ["Divide by 3.6.", "72 ÷ 3.6 = 20 m/s."], "20 m/s")],
      [("num", "A runner covers 400 m in 50 s. Find her speed in m/s.", 8, "400 ÷ 50 = 8 m/s.", 0, "m/s"),
       ("num", "A car travels at 60 km/h for 3 hours. How far does it go?", 180, "60 × 3 = 180 km.", 0, "km"),
       ("num", "A taxi travels 150 km at 50 km/h. How long does the journey take?", 3, "150 ÷ 50 = 3 h.", 0, "h"),
       ("mcq", "A horizontal line on a distance-time graph means the object is:", "at rest", ["accelerating", "moving fast", "moving backwards"], "Distance does not change."),
       ("num", "Convert 90 km/h to m/s.", 25, "90 ÷ 3.6 = 25 m/s.", 0, "m/s"),
       ("prob", "A bus leaves a town and travels 120 km in 2 h, rests for 30 minutes, then travels 60 km in 1.5 h.", [("num", "Speed in the first part (km/h).", 60, "120 ÷ 2 = 60 km/h.", 0, "km/h"), ("num", "Total distance (km).", 180, "120 + 60 = 180 km.", 0, "km"), ("num", "Average speed for the whole journey including the rest (km/h). Total time is 4 h.", 45, "180 ÷ 4 = 45 km/h.", 0, "km/h")])],
      [("Speed is calculated as:", "distance ÷ time", ["distance × time", "time ÷ distance", "distance + time"], "Speed = distance/time."),
       ("The slope of a distance-time graph gives the:", "speed", ["mass", "weight", "volume"], "Gradient = speed."),
       ("A speed of 36 km/h equals:", "10 m/s", ["36 m/s", "3.6 m/s", "100 m/s"], "36 ÷ 3.6 = 10."),
       ("A car goes 200 km in 4 h. Its average speed is:", "50 km/h", ["800 km/h", "20 km/h", "4 km/h"], "200 ÷ 4."),
       ("Distance travelled equals:", "speed × time", ["speed ÷ time", "time ÷ speed", "speed − time"], "Rearrange speed = distance/time.")],
      ["Journey numbers are illustrative, not real timetables."])

# =========================================================== 3 Heat, light, electricity, magnetism
ch = p.chapter("energy", "Heat, light, electricity and magnetism", "Form 2 Basic Science — Heat, light, electricity, magnetism (to be checked)")
def heat_fig():
    d = Draw()
    # conduction
    d.sh.append(RECT(15, 80, 120, 14, fill="lightgrey", width=2)); d.sh.append(POLY([(22, 120), (30, 100), (38, 120)], fill="orange", width=1.5))
    d.line(40, 70, 125, 70, arrow="end", color="red"); d.text(75, 140, "conduction", size=12, bold=True); d.text(75, 156, "in solids", size=11)
    # convection
    d.sh.append(POLY([(190, 40), (190, 110), (260, 110), (260, 40)], closed=False, width=2)); d.sh.append(RECT(191, 60, 68, 50, fill="lightblue", stroke="none", width=0))
    d.line(225, 105, 225, 62, arrow="end", color="red"); d.line(200, 62, 200, 100, arrow="end", color="blue"); d.line(250, 62, 250, 100, arrow="end", color="blue")
    d.sh.append(POLY([(215, 130), (225, 115), (235, 130)], fill="orange", width=1.5))
    d.text(225, 150, "convection", size=12, bold=True); d.text(225, 166, "in liquids, gases", size=11)
    # radiation
    d.sh.append(CIRCLE(330, 60, 22, fill="yellow", width=2))
    for x2, y2 in ((390, 50), (395, 75), (385, 100)): d.line(358, 66, x2, y2, arrow="end", color="orange")
    d.text(370, 140, "radiation", size=12, bold=True); d.text(370, 156, "needs no medium", size=11)
    return d.fig(470, 175)
build(ch, "heat", "Heat, temperature and heat transfer", 25,
      ["Distinguish heat from temperature.", "Describe how a thermometer works and give the effects of heating.", "Describe conduction, convection and radiation."],
      [("key", "definition", "Heat and temperature",
        "**Heat** is energy that flows from a hot object to a cooler one (measured in joules). **Temperature** tells how hot something is (measured in degrees Celsius, °C, with a thermometer). Water freezes at 0 °C and boils at 100 °C; normal human body temperature is about 37 °C. A liquid-in-glass thermometer works because the liquid **expands** when heated."),
       ("key", "retenir", "Effects of heating",
        "Most solids, liquids and gases **expand** when heated and **contract** when cooled. Railway lines and bridges are built with small gaps for this reason, and power cables are slack in hot weather. Heating can also change the **state** of a substance and raise its temperature."),
       ("fig", heat_fig(), "Three ways in which heat travels.", "Three pictures: a metal rod heated at one end for conduction, a beaker with rising warm water and sinking cool water for convection, and the Sun sending rays for radiation."),
       ("key", "retenir", "How heat travels",
        "- **Conduction**: through solids; metals are good **conductors**, wood, plastic and air are **insulators**.\n- **Convection**: in liquids and gases; warm fluid is less dense and rises, cool fluid sinks.\n- **Radiation**: heat as waves that need no material; it comes from the Sun to Earth. Dark, dull surfaces absorb it well; shiny, light surfaces reflect it."),
       ("key", "pieges", "Common mistakes",
        "- Heat and temperature are not the same: a bath of warm water holds more heat than a hot cup of tea.\n- Convection cannot happen in solids.\n- A metal spoon feels colder than wood at the same temperature because it conducts heat from your hand faster."),
       ("example", "Why a wooden handle?", "A cooking pot has a metal body and a wooden or plastic handle. Explain.", ["The metal body is a good conductor so the food heats quickly.", "Wood and plastic are insulators, so the handle stays cool to hold."], "Metal conducts heat to the food; the handle insulates the hand."),
       ("example", "Name the process", "A room is heated by a fire. The air near the ceiling is warm. Which process carries the heat there and why?", ["Warm air expands, becomes less dense and rises.", "Heat carried by moving air is convection."], "Convection")],
      [("mcq", "The Sun's heat reaches the Earth mainly by:", "radiation", ["conduction", "convection", "evaporation"], "There is no material between the Sun and Earth."),
       ("match", "Match each material with how it behaves.", [("copper", "good conductor"), ("wood", "poor conductor (insulator)"), ("water being heated from below", "convection currents"), ("a shiny surface", "reflects radiation well")], "Standard properties."),
       ("tf", "Heat is the same as temperature.", False, "Heat is energy; temperature measures how hot."),
       ("mcq", "Why is there a small gap between sections of railway line?", "the metal expands in hot weather", ["to save metal", "to make noise", "for drainage"], "Expansion would cause buckling."),
       ("open", "Explain why cooking pots have handles made of plastic or wood.", "Plastic and wood are insulators so heat does not travel to the handle by conduction; the handle stays cool enough to hold.", ["1 mark: insulators", "1 mark: reduces conduction of heat to the hand"]),
       ("prob", "Water is heated in a beaker over a flame.", [("mcq", "How does heat pass through the glass to the water?", "conduction", ["radiation only", "convection", "evaporation"], "Heat passes through solids by conduction."), ("mcq", "How does warm water circulate inside the beaker?", "convection currents", ["conduction", "radiation only", "expansion of glass"], "Warm water rises and cool water sinks."), ("num", "The water starts at 25 °C and is heated to 100 °C. By how many degrees does its temperature rise?", 75, "100 − 25 = 75 °C.", 0, "°C")])],
      [("Heat travelling through a solid is:", "conduction", ["convection", "radiation", "evaporation"], "Particles pass the energy on."),
       ("Which material is a good insulator?", "plastic", ["copper", "iron", "aluminium"], "Plastic conducts heat poorly."),
       ("Solids, liquids and gases generally _____ when heated.", "expand", ["shrink", "disappear", "turn blue"], "Heating makes particles move further apart."),
       ("The unit of temperature used in this course is the:", "degree Celsius", ["newton", "joule", "metre"], "°C."),
       ("In convection, warm fluid:", "rises", ["sinks", "stays still", "freezes"], "It is less dense.")],
      ["Body temperature 37 °C is an average; check whether the syllabus introduces the Kelvin scale (not at this level)."])

def refl_fig():
    d = Draw(); th = math.radians(40); L = 130
    cx, cy = 200, 170
    dx, dy = L * math.sin(th), L * math.cos(th)
    d.line(60, cy, 340, cy, width=4)
    for x in range(70, 340, 20): d.line(x, cy, x - 10, cy + 12, width=1.2, color="grey")
    d.line(cx, cy, cx, 40, dash=True, color="grey", width=1.5)
    d.line(cx - dx, cy - dy, cx - 2, cy - 2, arrow="end", color="red", width=2.5)
    d.line(cx, cy, cx + dx, cy - dy, arrow="end", color="red", width=2.5)
    d.text(cx - dx - 6, cy - dy - 8, "incident ray", size=11, anchor="end"); d.text(cx + dx + 6, cy - dy - 8, "reflected ray", size=11, anchor="start")
    d.text(cx + 6, 40, "normal", size=11, anchor="start")
    d.text(cx - 40, 142, "i = 40°", size=11, anchor="end"); d.text(cx + 40, 142, "r = 40°", size=11, anchor="start")
    d.text(cx, 205, "plane mirror", size=11)
    return d.fig(400, 215)
build(ch, "light", "Light and reflection", 25,
      ["Distinguish luminous and non-luminous objects and describe how shadows form.", "State the law of reflection.", "Describe the image in a plane mirror."],
      [("key", "definition", "Light travels in straight lines",
        "**Luminous** objects give out their own light (the Sun, a flame, a bulb); **non-luminous** objects such as the Moon are seen because they **reflect** light. Light travels in **straight lines** at about 300 000 km/s (3 × 10⁸ m/s), the fastest speed known. A **shadow** forms when an **opaque** object blocks light. **Transparent** materials let light through (glass); **translucent** ones scatter it (frosted glass)."),
       ("fig", refl_fig(), "Reflection of a ray at a plane mirror.", "A ray hitting a mirror at 40 degrees to the normal and reflecting at 40 degrees on the other side, with the incident ray, reflected ray, normal and angles labelled."),
       ("key", "propriete", "Law of reflection",
        "The **angle of incidence** (i) equals the **angle of reflection** (r), and both are measured from the **normal**, the line at 90° to the mirror. The incident ray, the reflected ray and the normal lie in the same plane.\n\nA plane mirror makes an image that is the **same size**, **as far behind the mirror as the object is in front**, upright, and **laterally inverted** (left and right swapped)."),
       ("key", "pieges", "Common mistakes",
        "- Angles are measured from the **normal**, not from the mirror surface.\n- The image in a plane mirror cannot be put on a screen: it is virtual.\n- The Moon is not luminous: it reflects sunlight."),
       ("example", "Find the angle", "A ray of light strikes a plane mirror at an angle of incidence of 35°. State the angle of reflection and the angle between the incident and reflected rays.", ["Angle of reflection = angle of incidence = 35°.", "The angle between the rays = 35° + 35° = 70°."], "r = 35°; angle between the rays = 70°"),
       ("example", "Distance to the image", "A girl stands 2 m in front of a plane mirror. How far is she from her image?", ["The image is 2 m behind the mirror.", "Distance = 2 + 2 = 4 m."], "4 m")],
      [("mcq", "Which of these is luminous?", "a burning candle", ["the Moon", "a mirror", "a book"], "A flame gives out its own light."),
       ("num", "A ray hits a mirror with an angle of incidence of 25°. Find the angle of reflection.", 25, "r = i = 25°.", 0, "°"),
       ("tf", "The image in a plane mirror is bigger than the object.", False, "It is the same size."),
       ("mcq", "A shadow forms because light:", "travels in straight lines and is blocked", ["bends round objects", "is absorbed by air", "stops by itself"], "An opaque object blocks the straight rays."),
       ("num", "A man stands 3 m from a plane mirror. How far is he from his image in metres?", 6, "3 + 3 = 6 m.", 0, "m"),
       ("prob", "A ray of light hits a plane mirror so that the angle between the ray and the mirror surface is 30°.", [("num", "Angle of incidence (from the normal) in degrees.", 60, "90° − 30° = 60°.", 0, "°"), ("num", "Angle of reflection in degrees.", 60, "r = i = 60°.", 0, "°"), ("num", "Angle between the incident and reflected rays in degrees.", 120, "60° + 60° = 120°.", 0, "°")])],
      [("Light travels in:", "straight lines", ["curves", "circles only", "zigzags"], "That is why shadows have sharp edges."),
       ("The angle of reflection equals the:", "angle of incidence", ["angle of the mirror", "speed of light", "distance to the image"], "Law of reflection."),
       ("The line at right angles to a mirror is the:", "normal", ["axis", "ray", "image"], "Angles are measured from it."),
       ("A material that lets no light pass is:", "opaque", ["transparent", "translucent", "luminous"], "Opaque materials make shadows."),
       ("The Moon can be seen because it:", "reflects sunlight", ["makes its own light", "is hot", "is near"], "It is non-luminous.")],
      ["Speed of light given as about 300 000 km/s; check the syllabus convention (3 × 10⁸ m/s).", "Lateral inversion described simply."])

ser = circuit_loop(60, 40, 340, 150, top=[("bulb", 0.3), ("bulb", 0.7)], left=[("cell", 0.5)], bottom=[("swc", 0.5)])
ser.text(60, 105, "cell", size=11, anchor="end"); ser.text(130, 25, "bulb", size=11); ser.text(270, 25, "bulb", size=11); ser.text(200, 175, "switch", size=11)
ser_fig = ser.fig(400, 190)
par = Draw()
par.wire(50, 30, 280, 30); par.wire(280, 170, 50, 170, [("swc", 0.5)]); par.wire(50, 170, 50, 30, [("cell", 0.5)])
par.wire(280, 30, 280, 170, [("bulb", 0.5)]); par.wire(165, 30, 165, 170, [("bulb", 0.5)])
par.dot(165, 30); par.dot(165, 170)
par.text(40, 105, "cell", size=11, anchor="end"); par.text(177, 105, "bulb", size=11, anchor="start"); par.text(292, 105, "bulb", size=11, anchor="start"); par.text(165, 192, "switch", size=11)
par_fig = par.fig(360, 200)
v3 = 3 * 1.5; assert abs(v3 - 4.5) < 1e-9 and abs(0.3 + 0.2 - 0.5) < 1e-9
build(ch, "electricity", "Simple electric circuits", 25,
      ["Draw and describe a simple series circuit and a parallel circuit.", "Name conductors and insulators.", "State rules for electrical safety."],
      [("key", "definition", "An electric circuit",
        "A **circuit** is a complete path for **electric current**. It needs a **source** (cell or battery), **conducting wires**, a **load** (bulb) and often a **switch**. The current is measured in **amperes (A)** with an **ammeter** placed in the circuit; the push of the cell is the **voltage**, in **volts (V)**. A break in the path, such as an open switch, stops the current."),
       ("fig", ser_fig, "A series circuit: cell, two bulbs and a closed switch.", "A rectangular circuit with a cell on the left, two bulbs on top and a closed switch at the bottom."),
       ("key", "retenir", "Series and parallel",
        "**Series**: components in one loop; the same current flows through all; if one bulb breaks, all go out. **Parallel**: each component has its own branch; if one bulb breaks the other stays on; the **currents in the branches add up**. Houses are wired in parallel. Cells in series add their voltages: three 1.5 V cells give 4.5 V."),
       ("fig", par_fig, "A parallel circuit: two bulbs in separate branches.", "A circuit with a cell, a closed switch and two bulbs each in its own branch between the top and bottom wires."),
       ("key", "retenir", "Conductors and safety",
        "**Conductors** let current pass: metals (copper, aluminium, iron), graphite, salt water. **Insulators** do not: plastic, rubber, glass, dry wood. Safety: never touch bare wires or switches with wet hands; do not overload sockets; do not poke objects into sockets; keep cables away from water; use **fuses** and do not repair mains wiring yourself."),
       ("key", "pieges", "Common mistakes",
        "- Current is not used up by a bulb: the same current leaves the bulb as enters it in a series loop.\n- Pure water is a poor conductor, but tap water and salt water conduct, so water is dangerous near electricity.\n- An ammeter goes in the circuit; it is not attached across a cell."),
       ("example", "Total voltage", "Three cells of 1.5 V each are joined in series. What is the total voltage?", ["Voltages add in series.", "3 × 1.5 = 4.5 V."], "4.5 V"),
       ("example", "Parallel currents", "In a parallel circuit one bulb takes 0.3 A and the other 0.2 A. What current leaves the cell?", ["Branch currents add: 0.3 + 0.2.", "= 0.5 A."], "0.5 A")],
      [("mcq", "Which of these is an electrical insulator?", "rubber", ["copper", "iron", "graphite"], "Rubber does not conduct."),
       ("match", "Match each item with its use.", [("switch", "opens and closes the circuit"), ("ammeter", "measures current"), ("fuse", "melts if the current is too high"), ("cell", "supplies electrical energy")], "Standard uses."),
       ("tf", "If one bulb in a series circuit breaks, the other bulbs still light.", False, "The loop is broken."),
       ("num", "Four 1.5 V cells are joined in series. Find the total voltage.", 6, "4 × 1.5 = 6 V.", 0, "V"),
       ("open", "Give three safety rules when using electricity at home.", "Do not touch switches or wires with wet hands; do not overload sockets; do not poke objects into sockets; keep cables away from water; use proper fuses (any three).", ["1 mark each, maximum 3"]),
       ("prob", "Two lamps are connected in parallel to a battery. Lamp A takes 0.4 A and lamp B takes 0.6 A.", [("num", "Current from the battery in amperes.", 1.0, "0.4 + 0.6 = 1.0 A.", 0.01, "A"), ("mcq", "If lamp A breaks, lamp B will:", "stay on", ["go out", "get brighter than any lamp", "explode"], "Each branch is independent."), ("open", "Why are the lamps in a house wired in parallel?", "So that each lamp works on its own and can be switched on and off without affecting the others, and each gets the full voltage.", ["1 mark: independent operation", "1 mark: full voltage"], 2)])],
      [("A complete path for current is called a:", "circuit", ["fuse", "switch", "magnet"], "A circuit must be complete."),
       ("Current is measured in:", "amperes", ["volts", "ohms only", "newtons"], "Unit A."),
       ("In a parallel circuit the branch currents:", "add up", ["are subtracted", "stay the same", "disappear"], "They sum to the main current."),
       ("Which is a conductor?", "copper", ["plastic", "rubber", "glass"], "Metals conduct."),
       ("A fuse protects a circuit by:", "melting when the current is too large", ["making the current larger", "storing charge", "cooling the wire"], "It breaks the circuit.")],
      ["Ammeter/voltmeter handling is simplified; the sign conventions for current direction are not discussed.", "Electrical safety is general advice; follow local rules and a qualified electrician for wiring."])

def magnet_fig():
    d = Draw()
    d.sh.append(RECT(160, 90, 60, 24, fill="red", width=2)); d.sh.append(RECT(220, 90, 60, 24, fill="blue", width=2))
    d.text(190, 107, "N", size=14, bold=True, color="white"); d.text(250, 107, "S", size=14, bold=True, color="white")
    for k, (a, b) in enumerate(((85, 40), (115, 70))):
        # ellipse arcs above and below, from N end (left) to S end (right)
        for sgn in (-1, 1):
            pts = []
            for t in range(0, 181, 15):
                ang = math.radians(180 - t)
                pts.append((220 + a * math.cos(ang), 102 + sgn * b * math.sin(ang)))
            d.sh.append(polyline(pts, color="grey", width=1.5))
    # arrows on the upper lines (N to S: left to right along the top)
    d.line(215, 62, 230, 62, arrow="end", color="grey", width=1.5); d.line(215, 32, 230, 32, arrow="end", color="grey", width=1.5)
    d.text(190, 140, "North pole", size=11); d.text(250, 140, "South pole", size=11)
    return d.fig(440, 160)
build(ch, "magnetism", "Magnets and magnetism", 20,
      ["State the laws of magnetic poles.", "Distinguish magnetic and non-magnetic materials.", "Describe how to make and test a magnet and use a compass."],
      [("key", "definition", "Magnets and poles",
        "A **magnet** attracts iron and steel. Every magnet has two **poles**, **north (N)** and **south (S)**, where the pull is strongest. A freely hung magnet turns to point north-south. **Like poles repel** (N and N, S and S); **unlike poles attract** (N and S). A broken magnet gives two smaller magnets, each with two poles."),
       ("fig", magnet_fig(), "A bar magnet and its magnetic field lines (simplified).", "A bar magnet with its north pole on the left and south pole on the right, with curved field lines running from north to south around it."),
       ("key", "retenir", "Magnetic materials",
        "**Magnetic** materials are attracted: **iron, steel, nickel, cobalt**. **Non-magnetic**: copper, aluminium, wood, plastic, glass. Only magnets **repel**, so repulsion is the true test for a magnet; attraction alone could be a piece of iron.\n\nA **compass** has a small magnet needle that points to Earth's magnetic north."),
       ("key", "methode", "Making and losing magnetism",
        "To **magnetise** a steel needle, stroke it many times in one direction with one pole of a magnet. It can lose its magnetism if it is hammered, heated or dropped many times. Keep magnets in pairs with soft iron **keepers** to stop them weakening. Strong magnets must be kept away from phones, bank cards and watches."),
       ("key", "pieges", "Common mistakes",
        "- A magnet does not attract every metal: aluminium and copper are not attracted.\n- Attraction does not prove an object is a magnet; repulsion does.\n- The north pole of a compass needle points to Earth's magnetic north, which is a south magnetic pole."),
       ("example", "Attract or repel?", "The N pole of magnet P is brought near the S pole of magnet Q, then near the N pole of Q. What happens in each case?", ["Unlike poles (N and S) attract.", "Like poles (N and N) repel."], "Attraction, then repulsion."),
       ("example", "Sort the materials", "Sort into magnetic and non-magnetic: iron nail, copper wire, steel pin, plastic ruler.", ["Iron and steel are magnetic.", "Copper and plastic are not."], "Magnetic: iron nail, steel pin. Non-magnetic: copper wire, plastic ruler.")],
      [("mcq", "Two north poles brought together will:", "repel", ["attract", "swap poles", "weaken to nothing"], "Like poles repel."),
       ("match", "Match each material with whether a magnet attracts it.", [("iron nail", "magnetic: made of iron"), ("steel paper clip", "magnetic: made of steel"), ("aluminium foil", "not magnetic: aluminium"), ("copper coin", "not magnetic: copper")], "Only iron, steel, nickel and cobalt are magnetic."),
       ("tf", "A bar magnet cut into two pieces gives one north piece and one south piece.", False, "Each piece has both a north and a south pole."),
       ("mcq", "The best test to show that a bar is a magnet is:", "it repels another magnet", ["it attracts an iron nail", "it is heavy", "it is shiny"], "Only magnets repel."),
       ("open", "Describe how to make a steel needle into a magnet.", "Stroke the needle many times in the same direction with one pole of a strong magnet; lift the magnet away at the end of each stroke and return in the air.", ["1 mark: stroke with one pole of a magnet", "1 mark: same direction each time"]),
       ("prob", "A learner has a compass and two bars A and B. A attracts B at one end and repels B at the other end.", [("mcq", "Which is certainly a magnet?", "both A and B", ["only A", "only B", "neither"], "Repulsion needs two magnets."), ("mcq", "A compass needle swings away from end X of bar A. End X must be:", "a north pole", ["a south pole", "iron", "non-magnetic"], "Like poles repel."), ("open", "Name two things that can weaken a magnet.", "Hammering, heating, dropping it repeatedly or keeping it badly (any two).", ["1 mark each, maximum 2"], 2)])],
      [("Unlike magnetic poles:", "attract", ["repel", "cancel", "melt"], "N attracts S."),
       ("Which metal is attracted by a magnet?", "iron", ["aluminium", "copper", "gold"], "Iron is magnetic."),
       ("A compass needle is a small:", "magnet", ["bulb", "cell", "mirror"], "It lines up with Earth's field."),
       ("Which can weaken a magnet?", "hammering it", ["keeping it in a keeper", "stroking it", "painting it blue"], "Hammering scrambles the magnetic order."),
       ("The end of a magnet where the pull is strongest is a:", "pole", ["keeper", "needle", "core"], "Poles are at the ends.")],
      ["Magnet field-line drawing is schematic. Check if the syllabus mentions Earth's magnetic poles naming (geographic vs magnetic)."])

# =========================================================== 4 Human body
ch = p.chapter("body", "The human body", "Form 2 Basic Science — Food, digestion, breathing and circulation (to be checked)")
gut = flow(["Mouth|chewing, saliva", "Gullet|swallowing", "Stomach|churning, acid", "Small intestine|digestion, absorption", "Large intestine|water absorbed"], w=300, vertical=True, bh=36, gap=12, size=11)
assert 8 + 4 + 8 + 12 == 32 and 10 * 37 == 370 and 10 * 17 == 170
build(ch, "food-digestion", "Food, teeth and digestion", 25,
      ["Name the food classes, their sources and uses.", "Describe the path of food through the digestive system.", "Name the types of teeth and explain how to care for them."],
      [("key", "retenir", "Food classes",
        "- **Carbohydrates** (cassava, yam, rice, plantain): energy.\n- **Proteins** (beans, fish, eggs, meat): growth and repair.\n- **Fats and oils** (palm oil, groundnuts): energy store; fat gives about 37 kJ per g against about 17 kJ per g for carbohydrate.\n- **Vitamins and minerals** (fruit, vegetables, green leaves such as eru): health.\n- **Fibre**: helps food pass through the gut.\n- **Water**: needed by all cells."),
       ("fig", gut, "The digestive system: path of food.", "Five boxes joined by arrows: mouth, gullet, stomach, small intestine and large intestine, each with its main job."),
       ("key", "definition", "Digestion",
        "**Digestion** breaks large insoluble food molecules into small soluble ones using **enzymes** (and in the stomach, acid). The small molecules are **absorbed** through the wall of the **small intestine** into the blood. In the large intestine **water** is absorbed; undigested waste leaves as faeces. A **balanced diet** contains all the food classes in the right amounts."),
       ("key", "retenir", "Teeth and care",
        "Adults have 32 teeth: **incisors** (cutting), **canines** (tearing), **premolars and molars** (grinding). Tooth decay happens when bacteria use sugar to make **acid** that attacks the enamel. Brush twice a day, avoid sugary snacks between meals, and see a dentist or health worker if you have toothache."),
       ("key", "pieges", "Common mistakes",
        "- Most absorption of food happens in the small intestine, not in the stomach.\n- Digestion is not the same as absorption: digestion breaks food down; absorption moves it into the blood.\n- A balanced diet is not the same for everyone: it depends on age and activity."),
       ("example", "Energy from fats and carbohydrates", "Use 37 kJ per g for fat and 17 kJ per g for carbohydrate. Find the energy in 10 g of each.", ["Fat: 10 × 37 = 370 kJ.", "Carbohydrate: 10 × 17 = 170 kJ."], "Fat 370 kJ; carbohydrate 170 kJ."),
       ("example", "Order the organs", "Put in order: stomach, mouth, large intestine, small intestine, gullet.", ["Food is chewed in the mouth and swallowed down the gullet.", "Then: stomach, small intestine, large intestine."], "mouth, gullet, stomach, small intestine, large intestine")],
      [("mcq", "Which food class is needed mainly for growth and repair?", "protein", ["fat", "fibre", "water"], "Proteins build and repair tissues."),
       ("match", "Match each organ with its job.", [("mouth", "chewing and mixing with saliva"), ("stomach", "churning food with acid and enzymes"), ("small intestine", "absorbs digested food"), ("large intestine", "absorbs water")], "Standard roles."),
       ("tf", "Most digested food is absorbed in the stomach.", False, "Absorption is mainly in the small intestine."),
       ("num", "How many kJ are in 20 g of carbohydrate if each gram gives 17 kJ?", 340, "20 × 17 = 340 kJ.", 0, "kJ"),
       ("open", "Explain why sugary snacks between meals can damage teeth.", "Bacteria in the mouth use the sugar to make acid, which attacks and dissolves the enamel and causes decay.", ["1 mark: bacteria use sugar", "1 mark: acid attacks enamel"]),
       ("prob", "A child eats a meal of boiled plantain with beans and eru.", [("mcq", "Which food class is mainly provided by the plantain?", "carbohydrate", ["protein", "fat", "water only"], "Plantain is a starchy food."),
                                                                   ("mcq", "Which food class is mainly provided by the beans?", "protein", ["carbohydrate only", "fibre only", "vitamin C only"], "Beans are rich in protein."),
                                                                   ("open", "Why is eru (green leaves) useful in the meal?", "It gives vitamins, minerals and fibre.", ["1 mark for vitamins/minerals", "1 mark for fibre"], 2)])],
      [("Digested food is absorbed mainly in the:", "small intestine", ["mouth", "stomach", "gullet"], "Villi give a large surface."),
       ("Teeth used for tearing food are the:", "canines", ["incisors", "molars", "premolars"], "Pointed teeth."),
       ("A diet containing all food classes in correct amounts is:", "balanced", ["fatty", "dry", "empty"], "Balanced diet."),
       ("Enzymes in digestion:", "break food into small molecules", ["colour the food", "absorb water", "store fat"], "They speed digestion."),
       ("Which food class is the main source of energy in cassava?", "carbohydrate", ["protein", "vitamin", "fibre"], "Cassava is starchy.")],
      ["Energy values (17 and 37 kJ/g) are the standard rounded figures; check which the syllabus prefers. Dietary and dental advice is general and to be reviewed by a health worker."])

breath = bars([("O₂ in", 21, "blue"), ("O₂ out", 16, "green"), ("CO₂ in", 0.04, "orange"), ("CO₂ out", 4, "red")], w=420, h=250)
pulse = 18 * 4; assert pulse == 72
build(ch, "breathing-blood", "Breathing, blood and circulation", 25,
      ["Describe the path of air to the lungs and gas exchange.", "Describe the parts and functions of blood.", "Describe the heart and blood vessels simply."],
      [("key", "definition", "Breathing and gas exchange",
        "Air passes through the **nose**, **windpipe (trachea)** and **bronchi** to tiny air sacs called **alveoli** in the **lungs**. There **oxygen** passes into the blood and **carbon dioxide** passes out. To breathe in, the ribs move **up and out** and the **diaphragm flattens**, so the chest becomes larger and air enters. To breathe out, they relax and air leaves."),
       ("fig", breath, "Approximate percentage of oxygen and carbon dioxide in inhaled (in) and exhaled (out) air.", "A bar chart: oxygen in 21, oxygen out 16, carbon dioxide in 0.04, carbon dioxide out 4 (percent)."),
       ("key", "retenir", "Blood",
        "**Blood** is a transport tissue. **Red blood cells** carry oxygen (they contain haemoglobin). **White blood cells** fight germs. **Platelets** help blood to clot. **Plasma**, the liquid part, carries digested food, carbon dioxide, wastes and hormones. Heat is also carried around the body."),
       ("key", "retenir", "Heart and vessels",
        "The **heart** is a muscular pump with four chambers. **Arteries** carry blood **away** from the heart (thick walls); **veins** carry blood **to** the heart (valves stop backflow); **capillaries** are tiny vessels where exchange with cells happens. The **pulse** shows the heart rate. At rest an adult heart beats roughly 60 to 100 times a minute."),
       ("key", "pieges", "Common mistakes",
        "- The body uses only part of the oxygen we breathe in: exhaled air still contains about 16 % oxygen.\n- Breathing (moving air) is not respiration (releasing energy in cells).\n- Veins do not all carry blue blood; the colour is always some red."),
       ("example", "Pulse rate", "A student counts 18 pulse beats in 15 seconds. Find her pulse rate per minute.", ["There are 4 lots of 15 s in a minute.", "18 × 4 = 72 beats per minute."], "72 per minute"),
       ("example", "Oxygen change", "Inhaled air has about 21 % oxygen and exhaled air about 16 %. Find the percentage points of oxygen used.", ["Subtract: 21 − 16.", "= 5 percentage points."], "About 5 percentage points")],
      [("mcq", "Gas exchange in the lungs takes place in the:", "alveoli", ["trachea", "diaphragm", "ribs"], "Alveoli have thin walls and many capillaries."),
       ("match", "Match each blood part with its function.", [("red blood cells", "carry oxygen"), ("white blood cells", "fight germs"), ("platelets", "help blood to clot"), ("plasma", "carries food and wastes")], "Standard functions."),
       ("num", "A runner's pulse is 25 beats in 15 seconds. Find the beats per minute.", 100, "25 × 4 = 100.", 0),
       ("tf", "Arteries carry blood towards the heart.", False, "Arteries carry blood away; veins carry blood towards the heart."),
       ("open", "Describe what happens to the ribs and diaphragm when you breathe in.", "The ribs move up and out and the diaphragm flattens; the chest volume increases and air rushes in.", ["1 mark: ribs up and out", "1 mark: diaphragm flattens"]),
       ("prob", "A student measures her pulse before and after running on the spot: 20 beats in 15 s at rest and 35 beats in 15 s after exercise.", [("num", "Resting pulse rate per minute.", 80, "20 × 4 = 80.", 0), ("num", "Pulse rate after exercise per minute.", 140, "35 × 4 = 140.", 0), ("open", "Explain why the pulse rate rises during exercise.", "The muscles need more oxygen and food for energy and the heart beats faster to deliver them and remove carbon dioxide.", ["1 mark: muscles need more oxygen", "1 mark: heart beats faster to supply it"], 2)])],
      [("Oxygen is carried in the blood by:", "red blood cells", ["white blood cells", "platelets", "plasma only"], "Haemoglobin binds oxygen."),
       ("When you breathe in, the diaphragm:", "flattens", ["relaxes upwards", "disappears", "rests"], "It moves down."),
       ("Vessels carrying blood away from the heart are:", "arteries", ["veins", "capillaries", "nerves"], "Arteries."),
       ("Tiny air sacs in the lungs are called:", "alveoli", ["bronchi", "ribs", "villi"], "Gas exchange surface."),
       ("Platelets are important for:", "clotting", ["breathing", "digestion", "hearing"], "They plug wounds.")],
      ["Exhaled-air composition (about 16 % O₂, 4 % CO₂) and resting pulse range are standard textbook approximations; check the figures used in class."])

# =========================================================== 5 Health and ecosystems
ch = p.chapter("health-eco", "Health, disease and ecosystems", "Form 2 Basic Science — Disease prevention and ecosystems (to be checked)")
mal = flow(["Mosquito bites|a sick person", "Parasite grows|in mosquito", "Mosquito bites|another person", "That person|may fall ill"], w=480, size=11, bh=48, gap=18)
build(ch, "disease", "Diseases and how to prevent them", 25,
      ["Name types of germs and ways diseases spread.", "Describe malaria, its spread and prevention.", "Explain how hygiene and vaccination protect health."],
      [("key", "definition", "Germs and disease",
        "**Pathogens** are germs that cause disease: **bacteria** (cholera, typhoid, tuberculosis), **viruses** (influenza, measles, polio), **protozoa** (malaria) and **fungi** (ringworm). They spread through **air**, **unsafe water and food**, **contact**, and **vectors** (animals such as mosquitoes). Antibiotics work on bacteria, **not on viruses**."),
       ("fig", mal, "How malaria is passed on.", "Four boxes joined by arrows: a mosquito bites a sick person, the parasite grows in the mosquito, the mosquito bites another person, and that person may fall ill."),
       ("key", "retenir", "Malaria",
        "**Malaria** is caused by a parasite (*Plasmodium*) spread by the bite of the **female *Anopheles* mosquito**. Symptoms include fever, shivering and headache. Prevention: sleep under an **insecticide-treated net**, remove standing water, clear bush around homes, use window screens. **Anyone with fever should go to a health centre for a test and proper treatment**; do not self-treat with leftover drugs."),
       ("key", "retenir", "Other diseases and protection",
        "**Cholera and typhoid** spread through contaminated water and food: drink safe water, wash hands with soap, cook food well and use latrines. **Vaccination** (immunisation) teaches the body to defend itself against a disease before it meets it; children should receive the vaccines recommended by the national programme at health centres."),
       ("key", "pieges", "Common mistakes",
        "- Malaria is not caused by drinking dirty water or by eating oily food: it is carried by mosquitoes.\n- Antibiotics do not cure colds or flu, which are caused by viruses.\n- Vaccines do not cause the disease they protect against."),
       ("example", "Break the chain", "Name two actions that stop a household from catching malaria.", ["Stop mosquitoes biting: sleep under a treated net.", "Stop them breeding: empty containers holding stagnant water."], "Use a treated net and remove standing water."),
       ("example", "Which germ?", "A child has watery diarrhoea after drinking unsafe well water. Which type of disease is this likely to be, and what should the family do first?", ["Water-borne diseases such as cholera are caused by bacteria in unsafe water.", "The child must take fluids and be taken to a health centre quickly."], "A water-borne disease; give fluids and get medical help.")],
      [("mcq", "Malaria is spread by:", "female Anopheles mosquitoes", ["houseflies", "dirty water only", "cold weather"], "The vector is the female Anopheles mosquito."),
       ("match", "Match each disease with how it is mainly spread.", [("malaria", "mosquito bite"), ("cholera", "contaminated water or food"), ("measles", "droplets in the air"), ("ringworm", "contact with an infected person or surface")], "Standard routes."),
       ("tf", "Antibiotics are the right treatment for the common cold.", False, "Colds are caused by viruses."),
       ("mcq", "A mosquito net protects a sleeper because it:", "stops mosquitoes biting at night", ["kills the parasite in the blood", "cools the room", "stops all diseases"], "It is a physical barrier (treated nets also kill)."),
       ("open", "Describe three ways a family can prevent cholera.", "Drink safe water (boiled or treated); wash hands with soap before eating and after the toilet; cook food well and cover it; use a latrine (any three).", ["1 mark each, maximum 3"]),
       ("prob", "In a town the rainy season brings many puddles and a rise in fever cases.", [("mcq", "Why do more mosquitoes appear?", "they breed in standing water", ["rain makes them hungry", "they come from the sea", "they follow cars"], "Larvae need still water."), ("mcq", "Which action helps most?", "empty or cover containers that hold water", ["close all windows only", "avoid walking", "boil the rain"], "It removes breeding sites."), ("open", "What should a person with a fever do?", "Go to a health centre for a test and correct treatment and rest; do not self-medicate.", ["1 mark: seek medical care", "1 mark: avoid self-medication"], 2)])],
      [("Pathogens are:", "germs that cause disease", ["parts of a plant", "types of food", "kinds of rock"], "Bacteria and viruses are examples."),
       ("The parasite that causes malaria is carried by a:", "mosquito", ["housefly", "snail", "frog"], "A vector."),
       ("Vaccination helps by:", "preparing the body to fight a disease", ["curing every disease", "making the body fat", "cleaning the water"], "It stimulates defences."),
       ("Which action helps prevent typhoid?", "washing hands with soap", ["sleeping late", "wearing dark clothes", "eating more salt"], "Hygiene stops spread."),
       ("Standing water around a house encourages:", "mosquito breeding", ["soil erosion", "tooth decay", "rusting"], "Larvae grow in still water.")],
      ["All health content is conservative and general; to be reviewed by a health worker. Check the names of national vaccination programme and advice on fever treatment."])

chain = flow(["Maize|plant", "Grasshopper", "Chicken", "Human"], w=480, size=12, bh=40, gap=22)
build(ch, "ecosystems", "Ecosystems and food chains", 25,
      ["Define habitat, population, community and ecosystem.", "Draw food chains and name producers, consumers and decomposers.", "Describe human effects on the environment and conservation."],
      [("key", "definition", "Habitats and ecosystems",
        "A **habitat** is the place where an organism lives (a pond, a forest floor). All the members of one kind in a habitat form a **population**; all the populations together form a **community**. A community with its non-living surroundings (soil, water, light, temperature) is an **ecosystem**, for example a rainforest or a savannah."),
       ("fig", chain, "A food chain: arrows show the direction of energy flow.", "Four boxes joined by arrows: maize, grasshopper, chicken, human."),
       ("key", "retenir", "Food chains",
        "A **food chain** shows who eats whom; the arrow means 'is eaten by' and shows the flow of energy. **Producers** (green plants) make food using sunlight. **Consumers** eat other organisms: **herbivores** (primary consumers), **carnivores** (secondary or tertiary consumers) and **omnivores**. **Decomposers** (bacteria, fungi) break down dead matter and return nutrients to the soil. Many chains join into a **food web**."),
       ("key", "retenir", "People and the environment",
        "Cutting forests, burning bush, overfishing, hunting and pollution can destroy habitats and reduce the number of species. **Conservation** protects them: protected areas such as Korup (rainforest) and Waza (savannah), replanting trees, fishing rules and cleaning rivers. Energy is lost at each step of a chain, so food chains are short."),
       ("key", "pieges", "Common mistakes",
        "- The arrow points to the animal that **eats**, not the one that is eaten.\n- A food chain always starts with a **producer**, not an animal.\n- Decomposers are not 'waste': they recycle nutrients."),
       ("example", "Name the roles", "In the chain grass → goat → human, name the producer, the primary consumer and the top consumer.", ["Grass makes food by photosynthesis: producer.", "The goat eats grass: primary consumer. The human eats the goat: top consumer."], "Producer: grass; primary consumer: goat; top consumer: human."),
       ("example", "What if the producers disappear?", "Drought kills most of the grass in a grazing area. Predict the effect on goats and humans in the chain grass → goat → human.", ["Goats have less food, so their number falls.", "Humans then have fewer goats to eat."], "Fewer goats and less food for humans.")],
      [("mcq", "The organisms that make food from sunlight are:", "producers", ["consumers", "decomposers", "carnivores"], "Green plants."),
       ("match", "Match each term with its meaning.", [("habitat", "where an organism lives"), ("producer", "makes its own food"), ("decomposer", "breaks down dead matter"), ("herbivore", "eats only plants")], "Standard definitions."),
       ("tf", "A food chain starts with an animal.", False, "It starts with a producer, a green plant."),
       ("mcq", "In the chain algae → tilapia → heron, the tilapia is the:", "primary consumer", ["producer", "decomposer", "top predator only"], "It eats the producer."),
       ("open", "Explain why deforestation can reduce the number of animal species in an area.", "Trees provide food and shelter; when they are cut the animals lose their habitat and food and may die or move away.", ["1 mark: loss of habitat", "1 mark: loss of food/shelter"]),
       ("prob", "Food chain in a pond: algae → tadpole → fish → heron.", [("mcq", "The producer is:", "algae", ["tadpole", "fish", "heron"], "It photosynthesises."), ("mcq", "How many consumers are there?", "3", ["1", "2", "4"], "Tadpole, fish and heron."), ("open", "If all the fish die, what happens to the number of tadpoles and herons? Explain.", "The tadpoles increase for a while because there are fewer predators, while the herons decrease because they lose their food.", ["1 mark: tadpoles increase", "1 mark: herons decrease"], 2)])],
      [("Decomposers include:", "bacteria and fungi", ["lions", "grasses", "birds"], "They break down dead remains."),
       ("The arrow in a food chain shows:", "the flow of energy", ["the way an animal walks", "the colour of food", "the age of animals"], "Energy moves up the chain."),
       ("An animal that eats only plants is a:", "herbivore", ["carnivore", "omnivore", "decomposer"], "Herb = plant."),
       ("A community together with its surroundings forms an:", "ecosystem", ["organ", "atom", "market"], "Living and non-living parts."),
       ("Which action helps conservation?", "replanting trees", ["bush burning", "dumping rubbish", "overfishing"], "It rebuilds habitats.")],
      ["Protected areas named (Korup, Waza) are real national parks; wording to be checked. Energy loss along chains stated qualitatively (no percentage)."])

p.write()
