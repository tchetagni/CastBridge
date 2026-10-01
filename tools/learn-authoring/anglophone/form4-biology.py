import sys; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from biolib import *

p = Pack("form4-biology", "Biology — Form 4", level="Form 4", subject="biology", cursus="secondary",
         description="A first course in O Level Biology for Form 4: the microscope and cells, classification, nutrition and digestion, transport, respiration and a first look at ecology. Short lessons, simple language, worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Biology, Form 4 (GCE O Level course, first year) — to be checked against the official syllabus")

# ------------------------------------------------------------------ cells
ch = p.chapter("cells", "The microscope and cells", "Form 4 Biology — Cells, microscope, tissues (to be checked)")
mic = S([RECT(210, 200, 120, 14, fill="lightgrey", width=2), LINE(280, 200, 280, 70, width=10, color="grey"), CIRCLE(260, 60, 20, fill="lightgrey", width=2),
         LINE(240, 50, 190, 120, width=12, color="ink"), RECT(185, 130, 40, 6, fill="lightblue", width=1.5), RECT(150, 150, 130, 6, fill="lightgrey", width=2),
         LINE(205, 136, 205, 148, width=3, color="blue"),
         lab(250, 40, 330, 20, "eyepiece lens", size=12), lab(215, 100, 330, 70, "body tube", size=12), lab(205, 134, 50, 110, "objective lens", size=12, anchor="start"), lab(215, 153, 50, 160, "stage", size=12, anchor="start"), lab(250, 206, 50, 205, "mirror or lamp", size=12, anchor="start"), lab(280, 120, 330, 120, "focusing knob", size=12)], 460, 230)
tot = 10 * 40
assert tot == 400
build(ch, "microscope", "Using the light microscope", 20,
      ["Name the main parts of a light microscope.", "Calculate total magnification.", "Describe how to prepare and focus a slide."],
      [("key", "definition", "The light microscope",
        "A **microscope** lets us see things that are too small for the eye. Light passes through a thin specimen on the **stage**. The **objective lens** makes a first image and the **eyepiece lens** makes it bigger. A **mirror or lamp** gives light. The **focusing knobs** move the lens to make the image sharp."),
       ("fig", mic, "A light microscope (schematic).", "A simple drawing of a microscope with eyepiece lens, body tube, objective lens, stage, mirror or lamp and focusing knob labelled."),
       ("key", "formule", "Total magnification",
        "**Total magnification = eyepiece magnification × objective magnification.**\n\nExample: eyepiece ×10 and objective ×40 gives ×400.\n\nStart with the **lowest power** objective; find the specimen; then change to a stronger lens and use the fine focus only."),
       ("key", "pieges", "Common mistakes",
        "- Never use the coarse focus with a high-power lens: you can break the slide.\n- A specimen must be **thin** so that light passes through it.\n- Do not add the two magnifications; **multiply** them."),
       ("example", "Total magnification", "A student uses an eyepiece lens of ×10 and an objective lens of ×40. Find the total magnification.", ["Multiply: 10 × 40.", "10 × 40 = 400."], "×400"),
       ("example", "Which lens first?", "You have objectives of ×4, ×10 and ×40. Which should you use first, and why?", ["The ×4 lens has the widest field of view and is furthest from the slide.", "It is easiest to find the specimen with it, then switch to a stronger lens."], "The ×4 lens, then ×10, then ×40.")],
      [("mcq", "Which part of the microscope holds the slide?", "stage", ["eyepiece", "mirror", "body tube"], "The slide sits on the stage."),
       ("num", "An eyepiece of ×10 is used with an objective of ×20. What is the total magnification?", 200, "10 × 20 = 200.", 0),
       ("tf", "You should always start with the highest power objective.", False, "Start with the lowest power."),
       ("mcq", "Why must a specimen be very thin?", "so that light can pass through it", ["so it looks bigger", "so it is cheaper", "so it does not move"], "A thick specimen blocks the light."),
       ("open", "Describe how to focus a slide.", "Place the slide on the stage; select the lowest power objective; look from the side and lower the lens close to the slide; look through the eyepiece and focus slowly upwards with the coarse knob, then use the fine knob; change to a higher power and refocus only with the fine knob.", ["1 mark: lowest power first", "1 mark: focus slowly", "1 mark: fine focus at high power"]),
       ("prob", "A student looks at onion skin with an eyepiece of ×10 and an objective of ×40.", [("num", "Total magnification.", 400, "10 × 40 = 400.", 0), ("mcq", "Which two things show that the cells are plant cells?", "cell walls and a large vacuole", ["a nucleus and cytoplasm", "a tail and cilia", "red colour"], "Cell walls and a large vacuole are plant features."), ("open", "Why does the student use a thin piece of onion skin?", "Light must pass through it for the cells to be seen.", ["1 mark for light passing through"], 2)])],
      [("Total magnification is found by:", "multiplying eyepiece and objective", ["adding them", "dividing them", "subtracting them"], "Multiply."),
       ("The part that holds the specimen is the:", "stage", ["eyepiece", "mirror", "tube"], "Stage."),
       ("The first objective lens used is the:", "lowest power", ["highest power", "oil lens", "none"], "Start low."),
       ("The lens nearest the eye is the:", "eyepiece", ["objective", "mirror", "stage"], "Eyepiece."),
       ("A microscope is used to:", "see very small things", ["see the stars", "weigh objects", "cut tissue"], "Magnification.")],
      ["Parts and procedure are standard; check whether the syllabus asks for the iris diaphragm and condenser."])

ch2cell = grid(["Cell wall|only in plants", "Chloroplasts|only in plants", "Large vacuole|plants", "Nucleus|both", "Cell membrane|both", "Cytoplasm|both"], cols=3, bh=52, size=11, colors=["lightgreen", "lightgreen", "lightgreen", "lightblue", "lightblue", "lightblue"])
build(ch, "cell-types", "Plant and animal cells", 20,
      ["Name the parts of a cell and give their jobs.", "List the differences between plant and animal cells.", "Explain what tissues, organs and systems are."],
      [("key", "definition", "Parts of a cell",
        "- **Cell membrane**: controls what goes in and out.\n- **Cytoplasm**: where chemical reactions happen.\n- **Nucleus**: controls the cell and holds the genes.\n- **Mitochondria**: release energy (respiration).\n\nPlant cells also have a **cell wall** (support), **chloroplasts** (photosynthesis) and a large **vacuole** (cell sap)."),
       ("fig", ch2cell, "Cell parts found in plants and in both.", "Six boxes: cell wall, chloroplasts and large vacuole in green for plants only; nucleus, cell membrane and cytoplasm in blue for both."),
       ("key", "retenir", "Levels of organisation",
        "Cells of the same kind form a **tissue** (muscle tissue). Different tissues form an **organ** (the heart). Organs that work together form an **organ system** (the circulatory system). Many systems make up an **organism**.\n\nSome cells are specialised: a **red blood cell** carries oxygen; a **root hair cell** absorbs water; a **nerve cell** carries messages."),
       ("key", "pieges", "Common mistakes",
        "- Animal cells do not have a cell wall or chloroplasts.\n- The cell wall is not the same as the cell membrane.\n- A tissue is a group of *similar cells*; an organ contains *several tissues*."),
       ("example", "Plant or animal?", "A cell has a nucleus, a cell wall and chloroplasts. What kind of cell is it, and what is the job of the chloroplasts?", ["A cell wall and chloroplasts are found only in plant cells.", "Chloroplasts contain chlorophyll and carry out photosynthesis."], "A plant cell; chloroplasts make food by photosynthesis."),
       ("example", "Order the levels", "Arrange: organ, cell, organism, tissue, system.", ["Smallest to largest: cell, then tissue, then organ.", "Then system and finally the whole organism."], "cell, tissue, organ, system, organism")],
      [("mcq", "Which part controls the activities of the cell?", "nucleus", ["vacuole", "cell wall", "cytoplasm"], "The nucleus is the control centre."),
       ("match", "Match the part with its job.", [("mitochondrion", "releases energy"), ("cell wall", "gives support to a plant cell"), ("chloroplast", "carries out photosynthesis"), ("cell membrane", "controls what enters the cell")], "Standard functions."),
       ("tf", "Animal cells have chloroplasts.", False, "Only plant cells have chloroplasts."),
       ("mcq", "A group of similar cells that do the same job is a:", "tissue", ["organ", "system", "nucleus"], "Definition."),
       ("open", "State three differences between a plant cell and an animal cell.", "Plant cells have a cell wall, chloroplasts and a large permanent vacuole; animal cells do not.", ["1 mark each, maximum 3"]),
       ("prob", "A root hair cell has a long thin extension.", [("mcq", "Its job is to absorb:", "water and mineral salts", ["light", "oxygen only", "sugar"], "Root hairs absorb water and mineral ions."), ("mcq", "Why is the long extension useful?", "it gives a large surface area", ["it makes food", "it carries messages", "it stores starch"], "A large surface area increases absorption."), ("open", "Name one other specialised cell and its job.", "A red blood cell carries oxygen (or a nerve cell carries impulses).", ["1 mark for a correct cell and job"], 2)])],
      [("Chloroplasts are found in:", "plant cells", ["animal cells only", "red blood cells", "bacteria only"], "Plants."),
       ("Mitochondria are the site of:", "respiration", ["photosynthesis", "digestion", "excretion"], "Energy release."),
       ("The heart is an example of an:", "organ", ["tissue", "cell", "system"], "Organ."),
       ("A red blood cell is specialised to:", "carry oxygen", ["make food", "absorb water", "send messages"], "Oxygen transport."),
       ("The cell wall is made of:", "cellulose", ["protein", "fat", "glucose"], "Plant cell walls.")],
      ["Level: introductory; check whether Form 4 syllabus includes cell specialisation examples."])

# ------------------------------------------------------------------ classification
ch = p.chapter("classification", "Living things and their classification", "Form 4 Biology — Characteristics of living things; classification (to be checked)")
vert = grid(["Fish|gills, scales, fins", "Amphibians|moist skin, eggs in water", "Reptiles|dry scaly skin", "Birds|feathers, beak, wings", "Mammals|hair, milk for young"], cols=3, bh=52, size=11)
build(ch, "classify", "Characteristics of living things and groups of animals", 25,
      ["List the seven characteristics of living things.", "Name the five groups of vertebrates and their features.", "Use a simple key."],
      [("key", "definition", "What is alive?",
        "All living things show seven life processes (**MRS GREN**): **m**ovement, **r**espiration, **s**ensitivity, **g**rowth, **r**eproduction, **e**xcretion and **n**utrition. A thing that does not show all of them is not alive."),
       ("fig", vert, "The five groups of vertebrates.", "Five boxes: fish with gills, scales and fins; amphibians with moist skin and eggs in water; reptiles with dry scaly skin; birds with feathers, a beak and wings; mammals with hair and milk for their young."),
       ("key", "retenir", "Groups of animals",
        "Animals with a **backbone** are **vertebrates**: fish, amphibians, reptiles, birds, mammals. Animals without a backbone are **invertebrates**: insects (6 legs, 3 body parts), spiders (8 legs, 2 body parts), crustaceans (crabs), worms and snails.\n\nScientists name each species with two words (genus and species), for example *Homo sapiens*."),
       ("key", "methode", "Using a key",
        "A **key** is a list of paired questions. Answer yes or no at each step; the answer sends you to the next question or to the name.\n\nExample: **1a** Has feathers: bird. **1b** No feathers: go to 2. **2a** Has fur: mammal. **2b** Has scales: fish or reptile."),
       ("key", "pieges", "Common mistakes",
        "- A bat flies but is a mammal; a whale lives in water but is a mammal.\n- A spider has 8 legs, so it is not an insect.\n- Plants are alive: they grow, respire, reproduce and respond to light."),
       ("example", "Classify the animal", "An animal has feathers, a beak and lays eggs. Which group is it, and is it a vertebrate?", ["Feathers and a beak mean it is a bird.", "Birds have a backbone, so they are vertebrates."], "A bird; yes, a vertebrate."),
       ("example", "Use the key", "Use the key above to name an animal with fur and no feathers.", ["Start at 1: no feathers, go to 2.", "At 2: fur means mammal."], "A mammal")],
      [("mcq", "Which of these is a vertebrate?", "frog", ["spider", "snail", "crab"], "A frog has a backbone."),
       ("match", "Match each animal to its group.", [("goat", "mammal"), ("lizard", "reptile"), ("hen", "bird"), ("tilapia", "fish")], "Standard groups."),
       ("tf", "A spider is an insect.", False, "Spiders have 8 legs and are arachnids."),
       ("mcq", "Excretion means:", "removing waste made by the body", ["taking in food", "making new individuals", "moving"], "Waste products such as CO₂ and urea."),
       ("open", "Explain why a car is not a living thing.", "A car does not grow, reproduce, respire or respond to stimuli by itself; living things show all seven life processes.", ["1 mark: lacks several life processes", "1 mark: names at least two"]),
       ("prob", "A student finds a small animal with six legs and three body parts.", [("mcq", "The animal is an:", "insect", ["arachnid", "reptile", "crustacean"], "Six legs, three parts."), ("tf", "It has a backbone.", False, "Insects are invertebrates."), ("open", "Name two other animals of the same group.", "Housefly and mosquito (or ant, bee, butterfly).", ["1 mark each, maximum 2"], 2)])],
      [("The seven life processes include:", "growth and sensitivity", ["rusting", "melting", "floating"], "MRS GREN."),
       ("Mammals feed their young with:", "milk", ["eggs", "pollen", "nectar"], "Mammary glands."),
       ("Animals without a backbone are:", "invertebrates", ["vertebrates", "birds", "mammals"], "Invertebrates."),
       ("Which group has feathers?", "birds", ["reptiles", "fish", "amphibians"], "Birds."),
       ("A key uses:", "paired statements", ["a microscope", "a thermometer", "a balance"], "It is a series of choices.")],
      ["Content overlaps in simplified form with the Form 5 pack (gceol-biology): intentional, this pack is a standalone introduction."])

# ------------------------------------------------------------------ nutrition
ch = p.chapter("nutrition", "Nutrition in plants and humans", "Form 4 Biology — Photosynthesis, food classes and digestion (to be checked)")
phot = flow(["Carbon dioxide|+ water", "Light and|chlorophyll", "Glucose|+ oxygen"], w=460, size=11, bh=48, gap=30)
build(ch, "photosynthesis", "Photosynthesis", 25,
      ["Write the word equation for photosynthesis.", "State what plants need for photosynthesis.", "Describe how to test a leaf for starch."],
      [("key", "definition", "Photosynthesis",
        "**Photosynthesis** is how green plants make food. They use **light energy**, absorbed by **chlorophyll** in the chloroplasts, to change **carbon dioxide** and **water** into **glucose**. Oxygen is released as a by-product."),
       ("fig", phot, "Photosynthesis in a leaf.", "Three boxes with arrows: carbon dioxide and water, light and chlorophyll, glucose and oxygen."),
       ("formula", "\\text{carbon dioxide} + \\text{water} \\to \\text{glucose} + \\text{oxygen}", "Word equation (light and chlorophyll are the conditions)"),
       ("key", "retenir", "What happens to the glucose",
        "Glucose is used in **respiration**, changed to **starch** for storage, changed to **cellulose** for cell walls, and combined with nitrogen from nitrates to make **proteins**. Carbon dioxide enters through the **stomata**; water comes from the roots. The leaf is thin and flat with many chloroplasts to catch light."),
       ("key", "methode", "Testing a leaf for starch",
        "1. Dip the leaf in boiling water for a short time to kill it.\n2. Boil it in ethanol (in a water bath, never over a flame) to remove the chlorophyll.\n3. Rinse it in warm water to soften it.\n4. Add iodine solution: **blue-black** means starch is present; brown means none."),
       ("key", "pieges", "Common mistakes",
        "- Plants do not take in oxygen *for* photosynthesis; oxygen is a product.\n- Photosynthesis needs light and cannot happen in the dark.\n- Never heat ethanol directly in a flame."),
       ("example", "Interpret the starch test", "A leaf from a plant kept in the dark for 2 days is tested with iodine and stays brown. A leaf from a plant in light turns blue-black. What do the results show?", ["The leaf from the dark had no starch.", "Starch is made only when there is light, so light is needed for photosynthesis."], "Light is needed for photosynthesis."),
       ("example", "Why is the leaf boiled in ethanol?", "Explain this step of the starch test.", ["Chlorophyll is green and would hide the colour of the iodine test.", "Ethanol dissolves the chlorophyll so the colour change can be seen."], "To remove the chlorophyll so the colour is visible.")],
      [("mcq", "Which gas is a product of photosynthesis?", "oxygen", ["carbon dioxide", "nitrogen", "hydrogen"], "Oxygen is released."),
       ("match", "Match the item with its role.", [("chlorophyll", "absorbs light"), ("stomata", "let carbon dioxide in"), ("roots", "take in water"), ("iodine", "tests for starch")], "Standard roles."),
       ("tf", "A leaf in the dark can carry out photosynthesis.", False, "Light is required."),
       ("mcq", "A positive starch test with iodine gives:", "blue-black", ["brown", "red", "no change"], "Blue-black."),
       ("open", "State the raw materials and products of photosynthesis.", "Raw materials: carbon dioxide and water (with light and chlorophyll); products: glucose and oxygen.", ["1 mark: raw materials", "1 mark: products", "1 mark: light and chlorophyll"]),
       ("prob", "A plant with green and white leaf parts (variegated) is tested for starch after a sunny day.", [("mcq", "Which part turns blue-black?", "the green parts", ["the white parts", "all parts equally", "neither"], "Only green parts contain chlorophyll."), ("mcq", "This shows that photosynthesis needs:", "chlorophyll", ["oxygen", "soil", "wind"], "Chlorophyll is needed."), ("open", "Why was the plant first placed in the dark before the experiment?", "To use up any starch already in the leaves so that new starch must be made in the light.", ["1 mark: remove existing starch"], 2)])],
      [("The green pigment in leaves is:", "chlorophyll", ["haemoglobin", "iodine", "starch"], "Chlorophyll."),
       ("Carbon dioxide enters leaves through:", "stomata", ["roots", "veins", "petals"], "Stomata."),
       ("Glucose is stored as:", "starch", ["protein", "chlorophyll", "water"], "Starch."),
       ("Photosynthesis needs:", "light", ["darkness", "cold only", "noise"], "Light energy."),
       ("Ethanol is used to:", "remove chlorophyll", ["add colour", "make starch", "cool the leaf"], "The leaf becomes pale.")],
      ["Photosynthesis in the Form 5 pack goes further (limiting factors); this lesson stays at word-equation level."])

diet = grid(["Carbohydrate|energy|cassava, rice, yam", "Protein|growth, repair|beans, fish, eggs", "Fat|energy store|palm oil, groundnuts", "Vitamins|health|fruit, vegetables", "Minerals|bones, blood|milk, green leaves", "Water and fibre|transport, gut|water, vegetables"], cols=3, bh=64, size=10)
en = 50 * 17 + 10 * 17 + 20 * 37
assert en == 1760
build(ch, "food", "Food classes and a balanced diet", 25,
      ["Name the food classes with a source and use of each.", "Describe the tests for starch, reducing sugar, protein and fat.", "Explain what a balanced diet is."],
      [("key", "retenir", "Food classes",
        "The body needs **carbohydrates**, **proteins**, **fats**, **vitamins**, **minerals**, **fibre** and **water**. Carbohydrates and fats give energy; proteins build and repair the body; vitamins and minerals keep us healthy; fibre helps food move through the gut."),
       ("fig", diet, "Food classes, their jobs and sources.", "Six boxes giving food class, use and examples: carbohydrate for energy from cassava, protein for growth from beans, fat for energy store from palm oil, vitamins from fruit, minerals from milk, and water and fibre from vegetables."),
       ("key", "methode", "Food tests",
        "- **Starch**: add iodine; **blue-black**.\n- **Reducing sugar**: add Benedict's solution and heat in a water bath; blue → green → yellow → **brick-red**.\n- **Protein**: add sodium hydroxide then a little copper sulphate; **purple**.\n- **Fat**: rub on paper; a **translucent spot** remains; or shake with ethanol, then add water: **cloudy white**."),
       ("key", "definition", "Balanced diet",
        "A **balanced diet** has all the food classes in the right amounts for a person's age, sex and activity. Fat gives about twice as much energy per gram as carbohydrate or protein.\n\nDiet problems: too little protein causes **kwashiorkor**; too little vitamin C causes **scurvy**; too little iron causes **anaemia**; too much energy food can cause obesity."),
       ("key", "pieges", "Common mistakes",
        "- Benedict's solution must be heated; the test is not for starch.\n- A purple colour means protein; blue means none.\n- A balanced diet is not the same for everybody."),
       ("example", "Energy of a meal", "Use 17 kJ per g for carbohydrate and protein and 37 kJ per g for fat. A meal has 50 g carbohydrate, 10 g protein and 20 g fat. Find its energy.", ["50 × 17 = 850; 10 × 17 = 170; 20 × 37 = 740.", "Total = 850 + 170 + 740 = 1760 kJ."], "1760 kJ"),
       ("example", "Which food test?", "A sample turns purple with sodium hydroxide and copper sulphate. What does it contain?", ["The reagents used are the Biuret test for protein.", "Purple is the positive result for protein; blue would mean none."], "Protein.")],
      [("mcq", "Which food class is needed for growth and repair?", "protein", ["fat", "fibre", "water"], "Protein."),
       ("match", "Match the test with its result.", [("iodine", "blue-black for starch"), ("Benedict's and heat", "brick-red for much sugar"), ("Biuret", "purple for protein"), ("ethanol then water", "cloudy for fat")], "Standard results."),
       ("num", "A snack has 20 g carbohydrate and 10 g fat. Using 17 and 37 kJ/g, find its energy in kJ.", 20 * 17 + 10 * 37, "340 + 370 = 710.", 0, "kJ"),
       ("mcq", "Lack of vitamin C causes:", "scurvy", ["rickets", "kwashiorkor", "goitre"], "Scurvy."),
       ("open", "What is a balanced diet?", "A diet containing all the food classes (with fibre and water) in the right amounts for the person's age, sex and activity.", ["1 mark: all food classes", "1 mark: right amounts for the person"]),
       ("prob", "A student tests a bean seed paste: iodine turns it blue-black, and sodium hydroxide with copper sulphate gives purple.", [("mcq", "The beans contain starch and:", "protein", ["fat only", "vitamin C only", "nothing else"], "Purple means protein."), ("tf", "Heating is needed for the iodine test.", False, "Only Benedict's test is heated."), ("open", "Name one food rich in carbohydrate and one rich in protein from your area.", "Carbohydrate: cassava, plantain, maize. Protein: beans, fish, eggs.", ["1 mark each"], 2)])],
      [("Starch turns iodine:", "blue-black", ["red", "green", "colourless"], "Starch test."),
       ("Which gives most energy per gram?", "fat", ["protein", "fibre", "water"], "Fat."),
       ("Fibre helps:", "move food along the gut", ["build bones", "make blood", "clot blood"], "Peristalsis."),
       ("A diet lacking iron may cause:", "anaemia", ["scurvy", "rickets", "goitre"], "Anaemia."),
       ("Benedict's test needs:", "heating", ["cooling", "light", "iodine"], "Water bath.")],
      ["Energy values 17 and 37 kJ/g are approximate textbook values.", "HEALTH CONTENT (deficiencies) to be reviewed by a nutritionist or health worker."])

gut = flow(["Mouth", "Gullet|(oesophagus)", "Stomach", "Small intestine|(duodenum, ileum)", "Large intestine|(colon)", "Rectum|and anus"], w=480, size=10, bh=44, gap=14)
build(ch, "digestion", "Human digestion and absorption", 30,
      ["Name the parts of the alimentary canal.", "State what the main digestive enzymes do.", "Explain how the small intestine is adapted to absorb food."],
      [("key", "definition", "Digestion",
        "**Digestion** is the breaking down of large insoluble food molecules into small soluble ones that can be absorbed into the blood. It uses **teeth** (mechanical digestion) and **enzymes** (chemical digestion). **Absorption** is the movement of digested food through the gut wall into the blood."),
       ("fig", gut, "The alimentary canal in order.", "Six boxes joined by arrows: mouth, gullet, stomach, small intestine, large intestine, rectum and anus."),
       ("key", "retenir", "Enzymes",
        "- **Amylase** (saliva, pancreas): starch → sugars (maltose, then glucose).\n- **Protease** (stomach, pancreas, small intestine): proteins → amino acids.\n- **Lipase** (pancreas, small intestine): fats → fatty acids and glycerol.\n\nThe liver makes **bile**, stored in the gall bladder: bile is not an enzyme but breaks fat into small droplets. Stomach acid kills germs."),
       ("key", "retenir", "Absorption",
        "Most absorption happens in the **small intestine**. Its lining has thousands of tiny **villi** that give a **large surface area**. Each villus has thin walls and a network of **capillaries** that carry glucose and amino acids away in the blood. The **large intestine** absorbs water; the leftover **faeces** leave through the anus (egestion)."),
       ("key", "pieges", "Common mistakes",
        "- Bile does not contain enzymes.\n- Food does not pass through the liver, gall bladder or pancreas; juices pass *to* the gut.\n- Digestion is not absorption."),
       ("example", "Name the enzyme", "Which enzyme breaks down (a) starch, (b) protein, and what are the final products?", ["(a) Amylase breaks starch into sugars (glucose).", "(b) Protease breaks protein into amino acids."], "(a) amylase, glucose; (b) protease, amino acids"),
       ("example", "Why villi?", "Explain why villi help absorption.", ["They give a very large surface area.", "Thin walls and many capillaries let digested food pass quickly into the blood."], "They increase surface area and speed absorption.")],
      [("mcq", "Where does most absorption of food take place?", "small intestine", ["stomach", "mouth", "rectum"], "Villi are in the small intestine."),
       ("match", "Match the enzyme with its substrate.", [("amylase", "starch"), ("protease", "protein"), ("lipase", "fat"), ("bile", "emulsifies fat but is not an enzyme")], "Standard facts."),
       ("tf", "Bile is an enzyme that digests fats.", False, "Bile breaks fat into droplets but is not an enzyme."),
       ("mcq", "Proteins are finally digested into:", "amino acids", ["glucose", "fatty acids", "starch"], "Amino acids."),
       ("open", "Explain two ways the small intestine is adapted for absorption.", "Villi increase the surface area; thin walls and a rich blood supply let food diffuse quickly and be carried away.", ["1 mark each, maximum 2, with explanation"], 2),
       ("prob", "A student chews a piece of cassava for several minutes and notices a sweet taste.", [("mcq", "The enzyme that makes it sweet is:", "amylase", ["protease", "lipase", "bile"], "Amylase breaks starch into sugar."), ("mcq", "This enzyme comes from the:", "salivary glands", ["liver", "stomach", "kidney"], "Saliva."), ("open", "Why does food need to be chewed?", "Chewing breaks food into small pieces with a large surface area so that enzymes can act faster.", ["1 mark: larger surface area for enzymes"], 2)])],
      [("Enzymes are:", "biological catalysts", ["hormones", "vitamins", "minerals"], "They speed up reactions."),
       ("Which organ makes bile?", "liver", ["stomach", "pancreas", "mouth"], "Liver."),
       ("Villi increase:", "surface area", ["temperature", "speed of water", "size of cells"], "Surface area."),
       ("Water is mainly absorbed in the:", "large intestine", ["mouth", "gullet", "stomach"], "Colon."),
       ("Teeth carry out:", "mechanical digestion", ["chemical digestion", "absorption", "excretion"], "Chewing.")],
      ["Enzyme detail (optimum pH/temperature) is not included at Form 4; see the Form 5 pack."])

# ------------------------------------------------------------------ transport
ch = p.chapter("transport", "Movement of substances and transport", "Form 4 Biology — Diffusion, osmosis, transport in plants and humans (to be checked)")
os = grid(["Pure water outside|water enters the cell|cell becomes turgid", "Strong solution outside|water leaves the cell|cell becomes flaccid"], cols=1, w=330, bh=60, size=11, colors=["lightblue", "lightorange"])
pc = (4.6 - 4.0) / 4.0 * 100
assert round(pc, 1) == 15.0
build(ch, "diffusion", "Diffusion and osmosis", 25,
      ["Define diffusion and osmosis.", "Predict what happens to plant cells in water and in strong solutions.", "Calculate a percentage change in mass."],
      [("key", "definition", "Diffusion",
        "**Diffusion** is the movement of particles from a place where there are many of them to a place where there are fewer, until they are spread evenly. It happens in gases and liquids and needs no energy. Oxygen enters cells and carbon dioxide leaves them by diffusion."),
       ("key", "definition", "Osmosis",
        "**Osmosis** is the movement of **water** through a **partially permeable membrane** from a **dilute solution** to a **concentrated solution**. A plant cell in pure water takes in water and becomes **turgid** (firm). In a strong solution it loses water and becomes **flaccid**; the plant wilts."),
       ("fig", os, "Osmosis in a plant cell.", "Two boxes: with pure water outside the cell takes in water and becomes turgid; with a strong solution outside the cell loses water and becomes flaccid."),
       ("key", "pieges", "Common mistakes",
        "- Osmosis is about *water* only; diffusion is about any particles.\n- Water moves from the *dilute* to the *concentrated* solution.\n- The membrane must be partially permeable."),
       ("example", "Potato strip", "A potato strip has mass 4.0 g. After 1 hour in pure water it weighs 4.6 g. Calculate the percentage change in mass.", ["Change = 4.6 − 4.0 = 0.6 g.", "0.6 ÷ 4.0 × 100 = 15 %."], "+15 %"),
       ("example", "Scent in a room", "Explain why the smell of fried plantain reaches the other side of a room.", ["The scent particles move from the kitchen, where there are many, to the room, where there are few.", "This spreading is diffusion."], "Diffusion down a concentration gradient.")],
      [("mcq", "Osmosis is the movement of:", "water through a partially permeable membrane", ["salt only", "air only", "food only"], "Definition."),
       ("num", "A strip of 5.0 g becomes 5.5 g in water. What is the percentage change?", 10, "0.5 ÷ 5.0 × 100 = 10 %.", 0.1, "%"),
       ("tf", "A cell in a very strong salt solution will take in water.", False, "It loses water."),
       ("mcq", "A turgid cell is:", "firm and full of water", ["shrunken", "dead", "empty"], "Turgid means swollen with water."),
       ("open", "Explain why lettuce leaves become crisp in water.", "Water enters the cells by osmosis; the cells become turgid and press on their walls, making the leaf firm.", ["1 mark: water enters by osmosis", "1 mark: cells turgid"]),
       ("prob", "Three potato strips are placed in water (A), weak sugar (B) and strong sugar (C).", [("mcq", "Strip C will:", "lose mass", ["gain mass", "stay the same", "dissolve"], "Water leaves by osmosis."), ("mcq", "Strip A will:", "gain mass", ["lose mass", "become flaccid", "dissolve"], "Water enters."), ("open", "Why should the strips have the same starting size?", "To make the test fair so differences are due only to the solution.", ["1 mark: fair test"], 2)])],
      [("Diffusion needs:", "no energy", ["light", "ATP", "enzymes only"], "It is passive."),
       ("Wilting is caused by:", "loss of water from cells", ["too much light", "too much oxygen", "photosynthesis"], "Cells become flaccid."),
       ("Water moves in osmosis from:", "dilute to concentrated solution", ["concentrated to dilute", "gas to solid", "warm to cold"], "Dilute to concentrated."),
       ("Oxygen enters cells by:", "diffusion", ["osmosis", "digestion", "pollination"], "Diffusion."),
       ("A plant cell does not burst in water because of its:", "cell wall", ["nucleus", "vacuole", "membrane"], "Strong wall.")],
      ["Potato data invented for practice.", "Water potential not used at Form 4."])

heart = flow(["Body", "Right|atrium", "Right|ventricle", "Lungs", "Left|atrium", "Left|ventricle"], w=480, size=10, bh=44, gap=14)
ph = 72 * 60
assert ph == 4320
build(ch, "circulation", "Blood and the heart", 30,
      ["Name the parts of the blood and give their jobs.", "Trace the path of blood through the heart.", "Compare arteries, veins and capillaries."],
      [("key", "retenir", "Blood",
        "Blood is made of **plasma** (the liquid, which carries food, urea, hormones and heat), **red blood cells** (carry oxygen with **haemoglobin**), **white blood cells** (fight germs) and **platelets** (help blood to clot). The heart pumps the blood round the **blood vessels**."),
       ("fig", heart, "The path of blood through the heart (the aorta leaves the left ventricle).", "Six boxes with arrows in order: body, right atrium, right ventricle, lungs, left atrium, left ventricle, then back to the body."),
       ("key", "retenir", "Heart and blood vessels",
        "The heart has four chambers: two **atria** (receive blood) and two **ventricles** (pump blood out). The **left ventricle** has the thickest wall because it pumps blood all round the body. **Valves** stop blood going backwards.\n\n- **Arteries**: carry blood away from the heart; thick walls.\n- **Veins**: carry blood to the heart; have valves.\n- **Capillaries**: very thin; where substances pass between blood and cells."),
       ("key", "attention", "Looking after your heart (flag for health review)",
        "Regular exercise, a balanced diet without too much fat and salt, and not smoking help keep the heart healthy. Your **pulse** (felt at the wrist) shows how many times the heart beats each minute; it rises during exercise."),
       ("example", "Pulse in an hour", "A resting heart beats 72 times per minute. How many beats in one hour?", ["One hour has 60 minutes.", "72 × 60 = 4320."], "4320 beats"),
       ("example", "Which vessel?", "A vessel with thick walls carries blood from the heart to the body. What is it called, and which chamber does the blood come from?", ["Thick walls and carrying blood away from the heart: an artery.", "Blood to the body leaves the left ventricle through the aorta."], "An artery (the aorta) from the left ventricle")],
      [("mcq", "Which blood cells carry oxygen?", "red blood cells", ["white blood cells", "platelets", "plasma"], "Haemoglobin."),
       ("num", "A girl's pulse is 80 beats per minute. How many beats in 5 minutes?", 400, "80 × 5 = 400.", 0),
       ("match", "Match each blood part with its job.", [("red cell", "carries oxygen"), ("white cell", "fights germs"), ("platelet", "helps clotting"), ("plasma", "carries food and urea")], "Standard jobs."),
       ("mcq", "Valves in veins:", "stop blood flowing backwards", ["make blood red", "make food", "thicken walls"], "They keep one-way flow."),
       ("open", "Explain why the left ventricle has a thicker wall than the right ventricle.", "It pumps blood to the whole body, which is farther, so it needs more force; the right ventricle only pumps to the nearby lungs.", ["1 mark: pumps to the whole body", "1 mark: needs more force/pressure"]),
       ("prob", "A boy measures his pulse before and after running for two minutes at school in Bafoussam: 70 beats per minute before and 130 after.", [("num", "By how many beats per minute did the pulse increase?", 60, "130 − 70 = 60.", 0, "beats/min"), ("mcq", "The pulse increased because:", "muscles need more oxygen and glucose", ["the heart is tired", "the blood is cold", "he is breathing less"], "Faster heart rate supplies muscles."), ("open", "Name two things to keep constant if comparing two classmates.", "Same exercise, same time, same resting period (and measure pulse for the same length of time).", ["1 mark each, maximum 2"], 2)])],
      [("Which chamber pumps blood to the body?", "left ventricle", ["right atrium", "left atrium", "right ventricle"], "Left ventricle."),
       ("Blood clots with the help of:", "platelets", ["plasma", "red cells", "bile"], "Platelets."),
       ("Which vessels have valves?", "veins", ["arteries", "capillaries", "none"], "Veins."),
       ("Exchange with cells happens in:", "capillaries", ["arteries", "veins", "the heart"], "Thin walls."),
       ("Exercise makes the pulse:", "increase", ["decrease", "stop", "stay the same"], "More oxygen needed.")],
      ["Heart described at an introductory level; double circulation and valves' names are in the Form 5 pack.", "Health advice to be reviewed by a health worker."])

# ------------------------------------------------------------------ respiration
ch = p.chapter("respiration", "Respiration and breathing", "Form 4 Biology — Respiration and gas exchange (to be checked)")
airs = bars([("O₂ in", 21, "blue"), ("O₂ out", 16, "lightblue"), ("CO₂ in", 0.04, "red"), ("CO₂ out", 4, "orange")], unit="% of the air (approximate)", w=420, h=230)
build(ch, "respiration", "Respiration and breathing", 30,
      ["Define respiration and write its word equation.", "Compare aerobic and anaerobic respiration.", "Describe breathing in and out."],
      [("key", "definition", "Respiration",
        "**Respiration** is the release of energy from food in living cells. Every living cell respires all the time. It is *not* the same as **breathing**, which is how air gets in and out of the lungs. **Aerobic respiration** uses oxygen and takes place in the mitochondria."),
       ("formula", "\\text{glucose} + \\text{oxygen} \\to \\text{carbon dioxide} + \\text{water} + \\text{energy}", "Aerobic respiration"),
       ("key", "retenir", "Without oxygen",
        "**Anaerobic respiration** releases a small amount of energy without oxygen. In **muscles** during hard exercise glucose becomes **lactic acid** (which causes tiredness and cramp). In **yeast** glucose becomes **alcohol (ethanol)** and **carbon dioxide**: this is **fermentation**, used for bread, palm wine and beer."),
       ("fig", airs, "Inhaled and exhaled air (approximate).", "A bar chart: oxygen is about 21 percent in and 16 percent out; carbon dioxide is about 0.04 percent in and 4 percent out."),
       ("key", "retenir", "Breathing",
        "**Breathing in**: the ribs move up and out, the **diaphragm** flattens, the chest gets bigger and air rushes in. **Breathing out**: the ribs move down, the diaphragm rises, the chest gets smaller and air is pushed out. Gas exchange happens in the tiny air sacs of the lungs (**alveoli**), which have thin walls and many capillaries."),
       ("key", "pieges", "Common mistakes",
        "- Breathing and respiration are different things.\n- Plants respire day and night.\n- Limewater turns *milky* with carbon dioxide; it does not test oxygen."),
       ("example", "Breaths in a minute", "A boy takes 15 breaths each minute and each breath moves 500 cm³ of air. How much air does he breathe in one minute?", ["Volume per minute = 500 × 15.", "500 × 15 = 7500 cm³."], "7500 cm³"),
       ("example", "After a race", "A girl is breathing fast after running. Explain why.", ["Her muscles used oxygen quickly and made some lactic acid.", "She needs extra oxygen to break down the lactic acid and to supply the muscles."], "To get more oxygen for the muscles and remove lactic acid.")],
      [("mcq", "Which gas is used in aerobic respiration?", "oxygen", ["carbon dioxide", "nitrogen", "methane"], "Oxygen."),
       ("num", "A girl takes 18 breaths per minute, each of 400 cm³. What is the volume of air breathed per minute?", 7200, "18 × 400 = 7200 cm³.", 0, "cm³"),
       ("tf", "Respiration is the same as breathing.", False, "Breathing moves air; respiration releases energy in cells."),
       ("mcq", "Yeast makes carbon dioxide and:", "alcohol", ["lactic acid", "oxygen", "glucose"], "Fermentation."),
       ("open", "Give the word equation for aerobic respiration.", "Glucose + oxygen → carbon dioxide + water + energy.", ["1 mark: reactants", "1 mark: products and energy"]),
       ("prob", "A student bubbles exhaled air and normal air through two tubes of limewater.", [("mcq", "Which tube turns milky first?", "the one with exhaled air", ["the one with normal air", "both at once", "neither"], "Exhaled air has more carbon dioxide."), ("mcq", "The gas that turns limewater milky is:", "carbon dioxide", ["oxygen", "nitrogen", "hydrogen"], "Test for CO₂."), ("open", "Where does the extra carbon dioxide in exhaled air come from?", "From respiration in the body cells.", ["1 mark for respiration in cells"], 2)])],
      [("Respiration occurs in:", "all living cells", ["lungs only", "plants only", "leaves only"], "Every cell respires."),
       ("Lactic acid is made in:", "muscles", ["yeast", "leaves", "bones"], "Anaerobic respiration."),
       ("Breathing in makes the chest:", "bigger", ["smaller", "flat", "hot"], "More space."),
       ("The test for carbon dioxide uses:", "limewater", ["iodine", "Benedict's", "litmus"], "Limewater turns milky."),
       ("Aerobic respiration releases:", "energy", ["only heat", "only light", "no energy"], "Energy.")],
      ["Percentages of gases are approximate.", "Introductory level; Form 5 pack adds the chemical equation and oxygen debt."])

# ------------------------------------------------------------------ ecology
ch = p.chapter("ecology", "Introduction to ecology", "Form 4 Biology — Ecosystems, food chains and energy flow (to be checked)")
food = flow(["Sun", "Grass|(producer)", "Grasshopper|(primary consumer)", "Lizard|(secondary consumer)", "Hawk|(tertiary consumer)"], w=500, size=10, bh=44, gap=14)
pyr = S([RECT(180, 20, 40, 40, fill="orange", width=1.5), RECT(150, 60, 100, 40, fill="lightorange", width=1.5), RECT(110, 100, 180, 40, fill="lightgreen", width=1.5), RECT(60, 140, 280, 40, fill="green", width=1.5),
         T(200, 45, "hawk", size=12), T(200, 85, "lizards", size=12), T(200, 125, "grasshoppers", size=12), T(200, 165, "grass", size=12)], 400, 195)
e1 = 10000
e2 = e1 * 0.1
e3 = e2 * 0.1
assert (e2, e3) == (1000, 100)
build(ch, "ecosystem", "Ecosystems, food chains and energy flow", 30,
      ["Define habitat, population, community and ecosystem.", "Construct a food chain and a pyramid of numbers.", "Describe energy flow and the effects of humans on ecosystems."],
      [("key", "definition", "Ecology words",
        "- **Habitat**: where an organism lives (a pond, a forest floor).\n- **Population**: all the organisms of one species in an area.\n- **Community**: all the populations living together.\n- **Ecosystem**: a community together with its non-living surroundings (soil, water, light, climate)."),
       ("key", "retenir", "Food chains",
        "A **food chain** shows who eats whom. The arrow means 'is eaten by' and shows the direction of energy flow. **Producers** (green plants) make food by photosynthesis; **consumers** eat other organisms (herbivores are primary consumers, carnivores eat animals); **decomposers** (bacteria and fungi) break down dead matter and return nutrients to the soil. Many chains linked together form a **food web**."),
       ("fig", food, "A food chain in the savannah.", "Boxes in a row: sun, grass as producer, grasshopper as primary consumer, lizard as secondary consumer and hawk as tertiary consumer."),
       ("key", "retenir", "Energy flow and pyramids",
        "The Sun is the source of energy. At each step only about **10 %** of the energy passes to the next level; the rest is lost as heat, in movement, and in waste. So food chains are short and there are fewer organisms at higher levels. A **pyramid of numbers** shows this: the producers form the widest layer."),
       ("fig", pyr, "A pyramid of numbers.", "A four-layer pyramid: the widest layer is grass, then grasshoppers, then lizards, and a small top layer for the hawk."),
       ("key", "pieges", "Common mistakes",
        "- Arrows point to the *eater*, not away from it.\n- A food chain always starts with a producer (and energy from the Sun).\n- Decomposers are not 'wasteful': they recycle nutrients."),
       ("example", "Energy at each level", "The grass in a field holds 10 000 kJ of energy. Assuming 10 % is passed on at each step, how much reaches the grasshoppers and then the lizards?", ["Grasshoppers: 10 000 × 0.1 = 1000 kJ.", "Lizards: 1000 × 0.1 = 100 kJ."], "1000 kJ and then 100 kJ"),
       ("example", "Write a food chain", "Rice, a rat, a snake and an eagle are in one area. Write the food chain and name the producer and the top consumer.", ["The producer is rice; the rat eats rice; the snake eats the rat; the eagle eats the snake.", "Chain: rice → rat → snake → eagle."], "rice → rat → snake → eagle; producer rice, top consumer eagle")],
      [("mcq", "Which organism is a producer?", "grass", ["lion", "fungus", "hawk"], "Plants make their own food."),
       ("match", "Match the term with its meaning.", [("habitat", "where an organism lives"), ("producer", "makes food by photosynthesis"), ("decomposer", "breaks down dead matter"), ("herbivore", "eats plants only")], "Standard definitions."),
       ("num", "A producer has 5000 kJ of energy. Assume 10 % passes to each next level. How much energy reaches the secondary consumer (two steps)?", 50, "5000 × 0.1 × 0.1 = 50 kJ.", 0, "kJ"),
       ("mcq", "In a food chain the arrow means:", "is eaten by", ["eats", "lives with", "grows near"], "It shows energy flow to the eater."),
       ("open", "Explain why there are fewer hawks than grasshoppers.", "Energy is lost at each step (about 90 %), so less energy is available at the top and fewer animals can be supported.", ["1 mark: energy lost at each step", "1 mark: less energy supports fewer animals"]),
       ("prob", "A forest food web has trees, caterpillars, birds, and a snake that eats birds.", [("mcq", "The producer is:", "the tree", ["the caterpillar", "the bird", "the snake"], "Trees photosynthesise."), ("mcq", "The tertiary consumer is:", "the snake", ["the caterpillar", "the bird", "the tree"], "Tree → caterpillar → bird → snake."), ("open", "What would happen to the caterpillars if all the birds were killed? Give a reason.", "Their numbers would increase at first because fewer are eaten, until food becomes limiting.", ["1 mark: increase", "1 mark: reason (fewer predators)"], 2)])],
      [("Decomposers are:", "bacteria and fungi", ["lions", "grasses", "birds"], "They break down dead matter."),
       ("A herbivore eats:", "plants", ["animals only", "dead matter", "rocks"], "Plants."),
       ("The first organism in a food chain is the:", "producer", ["consumer", "decomposer", "predator"], "Producer."),
       ("About how much energy passes to the next level?", "10 %", ["90 %", "50 %", "100 %"], "About one tenth."),
       ("A community plus its surroundings is an:", "ecosystem", ["habitat", "population", "organism"], "Ecosystem.")],
      ["The 10 % rule is an approximation; it varies between 5 and 20 %.", "Food chain organisms are typical savannah examples; the Form 5 pack covers energy efficiency, pyramids of biomass and conservation."])

p.write()
