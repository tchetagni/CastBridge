"""Generator: content/learn/gceal-biology (Biology - GCE A Level, Upper Sixth exam pack). Run: python3 gceal-biology.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _l6 import *

p = Pack("gceal-biology", "Biology — GCE A Level", level="Upper Sixth", subject="biology", cursus="secondary", exam="GCE-AL", series=[],
         description="Upper Sixth Biology for the GCE Advanced Level: respiration, photosynthesis, homeostasis, genetics, evolution, ecology, biotechnology, reproduction, plant hormones and statistics, with worked examples, graded exercises and a mock paper.",
         programRef="Cameroon GCE Board — Advanced Level Biology syllabus (Upper Sixth) — to be checked against the official texts")
NOTE_ORDER = "The split of topics between Lower and Upper Sixth is an editorial choice to be checked against the GCE Board syllabus."

# =====================================================================  CH 1  ENERGY
c1 = p.chapter("energy", "Energy: respiration and photosynthesis", programRef="GCE A Level Biology — Respiration; photosynthesis (to be checked)")

# ---- 1 respiration I
fig = vflow(["Glycolysis (cytoplasm)", "Link reaction (matrix)", "Krebs cycle (matrix)", "Electron transport (cristae)"], w=430, size=12, bw=230,
            notes=["2 pyruvate; net 2 ATP", "acetyl CoA; CO₂", "CO₂; 2 ATP; NADH, FADH₂", "most of the ATP; H₂O"], title="Stages of aerobic respiration")
r1_sc = [
    ("Where does glycolysis take place?", "In the cytoplasm", ["In the mitochondrial matrix", "On the inner mitochondrial membrane", "In the nucleus"], "Glycolysis needs no mitochondria."),
    ("What is the net ATP gain from glycolysis of one glucose molecule?", "2", ["4", "36", "0"], "4 ATP are made but 2 are used to phosphorylate glucose."),
    ("The link reaction produces", "acetyl coenzyme A, CO₂ and reduced NAD", ["lactate and ATP", "ethanol and CO₂", "oxygen and water"], "Pyruvate is decarboxylated and dehydrogenated."),
    ("Which compound combines with acetyl CoA at the start of the Krebs cycle?", "Oxaloacetate (4C)", ["Pyruvate", "Glucose", "RuBP"], "It forms citrate (6C)."),
    ("The Krebs cycle takes place in", "the matrix of the mitochondrion", ["the cristae membrane", "the cytoplasm", "the nucleus"], "Enzymes of the cycle are in the matrix."),
]
lesson(c1, "respiration-1", "Respiration I: ATP, glycolysis, link reaction and Krebs cycle",
       ["State why ATP is the energy currency and describe its synthesis and use.", "Describe glycolysis, the link reaction and the Krebs cycle with their products.", "Calculate the yields of reduced coenzymes and ATP from substrate-level phosphorylation."],
       [("definition", "ATP", "**ATP** (adenosine triphosphate) is the immediate energy source of cells. Hydrolysis of ATP to ADP + Pᵢ releases a small, usable amount of energy; ATP is rebuilt by **phosphorylation** using energy from respiration or light. It is suitable because it is soluble, small, releases energy in manageable amounts and is quickly regenerated."),
        ("retenir", "Glycolysis and the link reaction", "**Glycolysis** (cytoplasm, no oxygen needed): glucose (6C) is phosphorylated using 2 ATP, split into two triose phosphates (3C), and oxidised to **2 pyruvate**, producing 4 ATP (net **2**) and **2 reduced NAD**. **Link reaction** (matrix): each pyruvate loses CO₂ and hydrogen to give **acetyl CoA** (2C) and reduced NAD."),
        ("retenir", "Krebs cycle", "Acetyl CoA (2C) joins oxaloacetate (4C) to make citrate (6C). Through a series of decarboxylation and dehydrogenation steps, citrate returns to oxaloacetate. **Per turn**: 2 CO₂, 3 reduced NAD, 1 reduced FAD and 1 ATP (substrate-level phosphorylation). The cycle turns **twice** per glucose."),
        ("pieges", "Count per glucose, not per pyruvate", "Because one glucose gives **two** pyruvate, the link reaction and the Krebs cycle each run **twice** per glucose. Per glucose, the totals before oxidative phosphorylation are 10 reduced NAD (2 + 2 + 6), 2 reduced FAD and 4 ATP (2 + 2). Many students forget to double the Krebs-cycle figures.")],
       [("Worked example — reduced coenzymes", "Calculate the total number of reduced NAD and reduced FAD produced from one glucose molecule up to the end of the Krebs cycle.", ["Glycolysis: 2 reduced NAD. Link: 2 reduced NAD (one per pyruvate). Krebs: 2 turns × 3 = 6.", "Reduced NAD total = 2 + 2 + 6 = %d." % (2 + 2 + 6), "Reduced FAD = 2 turns × 1 = 2."], "10 reduced NAD and 2 reduced FAD"),
        ("Worked example — CO₂ released", "How many CO₂ molecules are released from one glucose molecule by the link reaction and the Krebs cycle?", ["Link reaction: 1 CO₂ per pyruvate, so 2.", "Krebs cycle: 2 CO₂ per turn × 2 turns = 4.", "Total = 2 + 4 = %d, all six carbon atoms of glucose." % 6], "6 CO₂")],
       [M("Which stage of respiration needs no oxygen directly and occurs in the cytoplasm?", "Glycolysis", ["Krebs cycle", "Link reaction", "Oxidative phosphorylation"], "Glycolysis is the first stage."),
        TF("The Krebs cycle produces most of the ATP by substrate-level phosphorylation.", False, "It makes only 2 ATP per glucose this way; most ATP comes from oxidative phosphorylation."),
        N("How many reduced NAD molecules are made by the Krebs cycle alone from one glucose molecule?", 6, "3 per turn × 2 turns = 6.")],
       [O("Explain why the link reaction and Krebs cycle are said to occur twice for each glucose molecule.", "One glucose molecule is split into two pyruvate molecules in glycolysis. Each pyruvate enters the link reaction and gives one acetyl CoA, which enters one turn of the Krebs cycle, so each stage happens twice per glucose.", ["Two pyruvate from one glucose (1)", "One acetyl CoA per pyruvate (1)", "Two turns (1)"]),
        M("Why is ATP better than glucose as the immediate energy source?", "Energy is released in small, usable amounts in a single-step hydrolysis", ["It stores more energy per molecule", "It cannot be re-made", "It is insoluble"], "Glucose breakdown is a long multi-step process.", tier="approfondissement")],
       P("Cells in a muscle in a runner at Limbe are respiring glucose aerobically.",
         [pn("How many net ATP molecules are made directly in glycolysis per glucose?", 2, "4 made − 2 used = 2.", 0, None, 1),
          pn("How many ATP molecules are made directly in the Krebs cycle per glucose?", 2, "1 per turn × 2 turns.", 0, None, 1),
          pn("How many reduced NAD in total (glycolysis, link and Krebs) per glucose?", 10, "2 + 2 + 6 = 10.", 0, None, 1),
          po("Explain why the cells need a continuous supply of oxidised NAD.", "NAD accepts hydrogen in glycolysis, the link reaction and the Krebs cycle; if it is not re-oxidised these stages stop, so ATP production halts.", ["NAD is the hydrogen acceptor (1)", "Stages stop without it (1)"], 2)]),
       r1_sc, (fig, "The four stages of aerobic respiration, where they occur and their main outputs.", "Four boxes in a vertical chain: glycolysis, link reaction, Krebs cycle and oxidative phosphorylation, with products written beside them."),
       notes=["Net ATP numbers by stage are textbook values; total yield differs between textbooks (see the next lesson).", NOTE_ORDER])

# ---- 2 respiration II
fig = bars([("Glycolysis", 2), ("Krebs (substrate)", 2), ("Oxidative phosph.", 28)], w=420, h=240)
nad = 10 * 2.5; fad = 2 * 1.5; tot = 2 + 2 + nad + fad; assert tot == 32
rq_g = 6 / 6; rq_l = 16 / 23
r2_sc = [
    ("Oxygen is the final electron acceptor in the electron transport chain; it combines with electrons and H⁺ to form", "water", ["carbon dioxide", "ATP", "pyruvate"], "Oxygen is reduced to water."),
    ("ATP synthase makes ATP using", "the proton gradient across the inner mitochondrial membrane", ["substrate-level phosphorylation only", "light energy", "the Calvin cycle"], "Chemiosmosis: protons flow through ATP synthase."),
    ("In anaerobic respiration in animals, pyruvate is converted to", "lactate", ["ethanol and CO₂", "acetyl CoA", "citrate"], "This re-oxidises reduced NAD so glycolysis can continue."),
    ("Yeast in anaerobic conditions produce", "ethanol and carbon dioxide", ["lactate", "water only", "glucose"], "Alcoholic fermentation."),
    ("A respiratory quotient close to 0.7 suggests the main substrate is", "lipid", ["carbohydrate", "protein only", "no respiration"], "Lipids need more oxygen per CO₂ released."),
]
lesson(c1, "respiration-2", "Respiration II: oxidative phosphorylation, anaerobic respiration and RQ",
       ["Explain the electron transport chain and chemiosmosis.", "Describe anaerobic respiration in animals and yeast.", "Calculate the ATP yield and the respiratory quotient (RQ)."],
       [("definition", "Oxidative phosphorylation", "Reduced NAD and reduced FAD deliver electrons to the **electron transport chain** on the **inner mitochondrial membrane**. As electrons pass along, energy is used to pump H⁺ from the matrix into the **intermembrane space**, creating a proton gradient. Protons flow back through **ATP synthase**, which makes ATP (**chemiosmosis**). **Oxygen** is the final electron acceptor and forms **water**."),
        ("pieges", "ATP yield: which figure?", "A modern estimate is about **2.5 ATP** per reduced NAD and **1.5 ATP** per reduced FAD. For one glucose: 10 reduced NAD → 25; 2 reduced FAD → 3; plus 4 by substrate-level phosphorylation = about **32 ATP**. Older textbooks give 36–38 because they used 3 and 2 ATP per NAD and FAD; always follow the figure taught in your course."),
        ("retenir", "Anaerobic respiration", "Without oxygen the chain stops and NAD is not re-oxidised. In **animals** pyruvate accepts hydrogen from reduced NAD to form **lactate** (the NAD can then be reused in glycolysis; lactate is later converted back in the liver). In **yeast and some plants** pyruvate is decarboxylated to ethanal and then reduced to **ethanol**. Only the 2 net ATP of glycolysis are made."),
        ("formule", "Respiratory quotient", "RQ = volume of CO₂ produced ÷ volume of O₂ consumed. Carbohydrate: about 1.0; lipid: about 0.7; protein: about 0.8–0.9. RQ above 1 suggests anaerobic respiration.")],
       [("Worked example — ATP yield", "Using 2.5 ATP per reduced NAD and 1.5 per reduced FAD, calculate the total ATP from one glucose (10 reduced NAD, 2 reduced FAD, 4 ATP by substrate-level phosphorylation).", ["From reduced NAD: 10 × 2.5 = %.0f." % nad, "From reduced FAD: 2 × 1.5 = %.0f." % fad, "Add the 4 ATP made directly: %.0f + %.0f + 4 = %.0f." % (nad, fad, tot)], "32 ATP"),
        ("Worked example — RQ of palmitic acid", "Palmitic acid is respired: C₁₆H₃₂O₂ + 23 O₂ → 16 CO₂ + 16 H₂O. Calculate RQ.", ["RQ = CO₂ produced ÷ O₂ consumed.", "= 16 ÷ 23.", "= %.2f, typical of lipid (about 0.7)." % rq_l], "0.70")],
       [M("Where is the ATP synthase enzyme located in a mitochondrion?", "Inner membrane", ["Matrix", "Outer membrane", "Intermembrane space"], "Protons flow through it from the intermembrane space to the matrix."),
        TF("In animal cells lactate is formed to regenerate oxidised NAD.", True, "This allows glycolysis to continue."),
        N("Glucose is respired: C₆H₁₂O₆ + 6 O₂ → 6 CO₂ + 6 H₂O. Calculate RQ.", rq_g, "6 ÷ 6 = 1.0.")],
       [O("Explain why the electron transport chain stops when oxygen is absent.", "Oxygen is the final electron acceptor; without it electrons cannot be removed from the last carrier, so the chain becomes full of electrons, reduced NAD and FAD cannot be oxidised, the proton gradient is not maintained and ATP synthesis stops.", ["Oxygen final acceptor (1)", "Chain blocked / NAD not re-oxidised (1)", "ATP synthesis stops (1)"]),
        M("The RQ of a germinating castor-oil seed is about 0.7. This suggests that the seed is respiring mainly", "lipids", ["carbohydrates", "ethanol", "lactate"], "Lipids need relatively more oxygen per CO₂ produced.", tier="approfondissement")],
       P("A respirometer experiment with germinating maize grains measured 18 cm³ of O₂ taken up and 18 cm³ of CO₂ produced in 30 minutes.",
         [pn("Calculate the RQ.", 1.0, "18 ÷ 18 = 1.0.", 0, None, 1),
          pm("What substrate does the RQ suggest?", "Carbohydrate", ["Lipid", "Protein only", "Lactate"], "RQ = 1.0 matches carbohydrate.", 1),
          pn("Calculate the rate of oxygen uptake in cm³ per minute.", 18 / 30, "18 ÷ 30 = 0.6.", 0.01, "cm³/min", 1),
          po("Name the compound that carries electrons to the electron transport chain and the location of the chain.", "Reduced NAD (and reduced FAD); the inner mitochondrial membrane (cristae).", ["Reduced NAD/FAD (1)", "Inner membrane (1)"], 2)]),
       r2_sc, (fig, "Approximate ATP yield per glucose from each stage of aerobic respiration (modern figures).", "A bar chart with bars for glycolysis (2), Krebs cycle (2) and oxidative phosphorylation (28)."),
       notes=["Total ATP yield of 32 (2.5 and 1.5 per NAD/FAD) is the modern value; many Cameroonian textbooks still use 36–38. The teacher must decide which to teach.", "RQ values for protein vary between sources."])
assert abs(rq_l - 0.6957) < 1e-3

# ---- 3 photosynthesis
fig = ring(["RuBP (5C)", "+ CO₂ (rubisco)", "2 × GP (3C)", "TP (3C)"], w=430, h=250, bw=130, title="Calvin cycle (stroma)")
ph_sc = [
    ("The light-dependent reactions take place in", "the thylakoid membranes", ["the stroma", "the cytoplasm", "the nucleus"], "Chlorophyll is in the thylakoid membranes."),
    ("Photolysis of water produces", "H⁺, electrons and oxygen", ["glucose", "CO₂", "ATP only"], "Oxygen is released as a by-product."),
    ("The enzyme that fixes CO₂ in the Calvin cycle is", "rubisco", ["amylase", "ATP synthase", "catalase"], "It combines CO₂ with RuBP."),
    ("Which products of the light-dependent stage are used in the Calvin cycle?", "ATP and reduced NADP", ["Oxygen and glucose", "CO₂ and water", "Pyruvate and NAD"], "They supply energy and reducing power."),
    ("The first stable compound of the Calvin cycle is", "glycerate 3-phosphate (GP)", ["RuBP", "pyruvate", "glucose"], "A 3-carbon compound formed from RuBP + CO₂."),
]
atp = 6 * 3; nadph = 6 * 2; assert (atp, nadph) == (18, 12)
lesson(c1, "photosynthesis", "Photosynthesis: light-dependent and light-independent stages",
       ["Describe the structure of the chloroplast and the role of pigments.", "Explain cyclic and non-cyclic photophosphorylation.", "Describe the Calvin cycle and calculate ATP and NADPH requirements."],
       [("definition", "Overview", "Photosynthesis converts light energy to chemical energy in organic molecules: 6CO₂ + 6H₂O → C₆H₁₂O₆ + 6O₂. In the **chloroplast**, the **light-dependent stage** happens in the **thylakoid membranes** (grana) and the **light-independent stage (Calvin cycle)** in the **stroma**. Pigments (chlorophylls a and b, carotenoids) absorb mainly red and blue light; green light is reflected."),
        ("retenir", "Light-dependent stage", "Light excites electrons in **photosystems**; they pass along an electron transport chain, pumping H⁺ into the thylakoid space, and **ATP synthase** makes ATP (**photophosphorylation**). In **non-cyclic** photophosphorylation electrons are replaced by **photolysis** of water (H₂O → 2H⁺ + 2e⁻ + ½O₂) and finally reduce **NADP**. In **cyclic** photophosphorylation electrons return to photosystem I, making only ATP."),
        ("retenir", "Calvin cycle", "CO₂ combines with **RuBP** (5C), catalysed by **rubisco**, to form two **GP** (3C). GP is reduced to **TP** (triose phosphate) using ATP and reduced NADP. Most TP is used to **regenerate RuBP** (needing ATP); the rest forms glucose, starch, amino acids and lipids. Six turns of the cycle fix 6 CO₂ and make one hexose."),
        ("pieges", "Oxygen comes from water", "The oxygen released in photosynthesis comes from **water** (photolysis), not from CO₂. The Calvin cycle is not 'dark': it uses ATP and reduced NADP made in the light, so it stops soon after light is removed. A common error is to say that 'light-independent' reactions can proceed at night indefinitely.")],
       [("Worked example — ATP and NADPH per glucose", "Each turn of the Calvin cycle fixes one CO₂ and needs 3 ATP and 2 reduced NADP. How many ATP and reduced NADP are needed to make one hexose (six CO₂ fixed)?", ["Six CO₂ require 6 turns.", "ATP: 6 × 3 = %d." % atp, "Reduced NADP: 6 × 2 = %d." % nadph], "18 ATP and 12 reduced NADP"),
        ("Worked example — carbon counting", "Six molecules of RuBP combine with six CO₂. How many molecules of GP are formed, and how many carbon atoms do they contain?", ["Each RuBP + CO₂ gives 2 GP, so 6 × 2 = 12 GP.", "Each GP has 3 carbons: 12 × 3 = %d C." % 36, "Check: 6 × 5 + 6 × 1 = 36 carbon atoms."], "12 GP; 36 carbon atoms")],
       [M("Where in the chloroplast does the Calvin cycle occur?", "Stroma", ["Thylakoid membrane", "Granum lumen only", "Outer envelope"], "The enzymes of the Calvin cycle are in the stroma."),
        TF("The oxygen released in photosynthesis is derived from carbon dioxide.", False, "It is derived from water by photolysis."),
        N("How many reduced NADP are needed to fix 3 CO₂ in the Calvin cycle (2 per CO₂)?", 6, "3 × 2 = 6.")],
       [O("Compare cyclic and non-cyclic photophosphorylation.", "Non-cyclic uses photosystems II and I, makes ATP and reduced NADP, and splits water with release of oxygen. Cyclic uses only photosystem I; electrons return to the chlorophyll, so only ATP is made and no oxygen or reduced NADP is formed.", ["Non-cyclic: ATP + NADPH + O₂ (1)", "Cyclic: ATP only (1)", "Electrons from water vs recycled (1)"]),
        M("What happens to the concentration of GP and RuBP when a plant is moved from light to darkness?", "GP rises and RuBP falls", ["Both rise", "GP falls and RuBP rises", "Both fall"], "Without ATP and reduced NADP GP cannot be reduced to TP, so RuBP is not regenerated.", tier="approfondissement")],
       P("An experiment with a cocoa seedling measures the rate of oxygen production at different light intensities.",
         [pm("Which product of the light-dependent stage is the oxygen released?", "A by-product of photolysis", ["Made from CO₂", "A product of the Calvin cycle", "Made in respiration"], "Photolysis of water.", 1),
          pn("Calculate how many ATP are needed for the Calvin cycle to make 2 hexose molecules (18 per hexose).", 36, "2 × 18 = 36.", 0, None, 2),
          po("Explain why chlorophyll appears green.", "It absorbs mainly red and blue wavelengths and reflects or transmits green light.", ["Absorbs red/blue (1)", "Reflects green (1)"], 2)]),
       ph_sc, (fig, "The Calvin cycle in the stroma: carbon fixation, reduction and regeneration of RuBP.", "Four boxes around a ring: RuBP, CO₂ added by rubisco, two GP, and TP, joined by arrows."),
       notes=["Photosystem and carrier detail (Z-scheme) is simplified; check the depth expected.", "3 ATP and 2 reduced NADP per CO₂ fixed is the standard school figure."])

# ---- 4 limiting factors
fig = plot(0, 10, 0, 16, curves=[("8*x/(2+x)", "blue", "low CO₂"), ("14*x/(2+x)", "red", "high CO₂")], xlabel="light intensity", ylabel="rate of photosynthesis", h=260)
lf_sc = [
    ("A limiting factor is", "the factor in shortest supply, which controls the rate", ["any factor affecting the plant", "the factor in greatest supply", "always light"], "Blackman's concept."),
    ("When a graph of rate against light intensity is flat, the limiting factor is", "another factor, such as CO₂ concentration or temperature", ["light intensity", "oxygen concentration", "the colour of the leaf"], "Light is no longer limiting."),
    ("At very high temperatures the rate of photosynthesis falls because", "enzymes are denatured", ["more light is absorbed", "CO₂ becomes unavailable", "chlorophyll is made faster"], "The enzymes of the Calvin cycle denature."),
    ("The light compensation point is where", "photosynthesis rate equals respiration rate", ["there is no light", "rate of respiration is zero", "CO₂ uptake is maximum"], "Net gas exchange is zero."),
    ("In an aquatic plant (e.g. Elodea) experiment, the rate is estimated by counting", "bubbles of oxygen released", ["glucose molecules", "starch grains", "chloroplasts"], "Bubbles per minute approximate the rate."),
]
ratio = (20 / 10) ** 2; assert ratio == 4
lesson(c1, "limiting-factors", "Limiting factors in photosynthesis",
       ["State Blackman's principle of limiting factors.", "Interpret graphs of rate against light intensity, CO₂ and temperature.", "Use the inverse-square law to compare light intensities and calculate rates."],
       [("definition", "Limiting factors", "The **rate of photosynthesis** depends on **light intensity**, **CO₂ concentration** and **temperature** (and water, chlorophyll and mineral supply). The **limiting factor** is the one in shortest supply: increasing it increases the rate, but increasing other factors does not. On a graph, the part that rises shows the limiting factor on the x axis; the plateau shows that another factor has become limiting."),
        ("pieges", "Reading the graph", "Name the limiting factor **for each part** of the curve. On the rising part it is the factor being varied; on the plateau it is some other factor, which you should identify from the information given (for example, the curve with higher CO₂ concentration reaches a higher plateau, so CO₂ is limiting the lower curve). Temperature affects the enzymes: the rate rises with temperature, then **falls sharply** beyond about 40 °C as enzymes denature."),
        ("retenir", "In practice", "An aquatic plant such as *Elodea* is illuminated by a lamp at measured distances; the rate is measured by counting bubbles of oxygen per minute or by a gas syringe. Light intensity is proportional to **1 ÷ d²** (inverse-square law). Keep temperature constant (e.g. with a water bath as heat shield) and CO₂ constant (add sodium hydrogencarbonate). In a greenhouse, growers raise CO₂ and temperature and use artificial light to increase yield."),
        ("retenir", "Compensation point", "At the **light compensation point** the rate of photosynthesis equals the rate of respiration, so there is no net exchange of CO₂ or O₂. Below it the plant loses carbon; above it the plant gains carbon and can grow.")],
       [("Worked example — inverse-square law", "A lamp is moved from 10 cm to 20 cm from a plant. By what factor does light intensity change?", ["Intensity ∝ 1 ÷ d².", "Ratio = (20 ÷ 10)² = %d." % ratio, "The intensity at 20 cm is one quarter of that at 10 cm."], "It falls to ¼"),
        ("Worked example — rate from bubbles", "An Elodea shoot releases 36 bubbles in 3 minutes at 15 cm and 60 bubbles in 3 minutes at 10 cm. Calculate both rates and the % increase.", ["Rate at 15 cm = 36 ÷ 3 = 12 bubbles per minute; at 10 cm = 60 ÷ 3 = 20.", "Increase = 20 − 12 = 8 bubbles per minute.", "%% increase = 8 ÷ 12 × 100 = %.1f %%." % (8 / 12 * 100)], "12 and 20 per minute; 66.7 % increase")],
       [M("Which of these is NOT a normal limiting factor of photosynthesis?", "Oxygen concentration in the air", ["Light intensity", "CO₂ concentration", "Temperature"], "Oxygen is not usually limiting in air."),
        TF("At the compensation point the rate of photosynthesis is zero.", False, "Photosynthesis equals respiration; both are non-zero."),
        N("A plant gives 45 bubbles in 5 minutes. Calculate the rate in bubbles per minute.", 9, "45 ÷ 5 = 9.", unit="per min")],
       [O("Explain why the rate of photosynthesis levels off at high light intensity.", "At high light intensity light is no longer limiting; another factor such as CO₂ concentration or temperature, or the amount of enzymes (rubisco) and chlorophyll, limits the rate.", ["Light not limiting (1)", "Another factor limiting (1)", "Named factor (1)"]),
        M("A lamp is moved from 40 cm to 20 cm from a plant. The light intensity is multiplied by", "4", ["2", "½", "¼"], "(40 ÷ 20)² = 4.", tier="approfondissement")],
       P("A graph shows the rate of photosynthesis of a leaf at two CO₂ concentrations: at low CO₂ the plateau is 8 units, at high CO₂ it is 14 units.",
         [pm("What limits the rate on the plateau of the lower curve?", "CO₂ concentration", ["Light intensity", "Oxygen", "Chlorophyll colour"], "Raising CO₂ raises the plateau.", 1),
          pn("Calculate the percentage increase in the plateau rate when CO₂ is raised.", 75, "(14 − 8) ÷ 8 × 100 = 75 %.", 0.1, "%", 2),
          po("Suggest two ways a greenhouse grower in Buea could increase tomato yield using these principles.", "Raise CO₂ (e.g. burn fuel or add CO₂); provide artificial light on dull days; keep temperature at the optimum without overheating.", ["Two sensible practical measures (2)"], 2)]),
       lf_sc, (fig, "Rate of photosynthesis against light intensity at two CO₂ concentrations.", "Two curves rising and levelling off, a lower blue curve for low CO₂ and a higher red curve for high CO₂."),
       notes=["Curve values in the figure are illustrative, not measured data.", "The inverse-square law assumes a point source in a dark room."])
assert round((14 - 8) / 8 * 100) == 75

# =====================================================================  CH 2  HOMEOSTASIS
c2 = p.chapter("homeostasis", "Homeostasis and coordination", programRef="GCE A Level Biology — Homeostasis; nervous and hormonal coordination; excretion (to be checked)")

# ---- 5 homeostasis and thermoregulation
fig = ring(["Body temperature\nrises", "Receptors detect:\nhypothalamus, skin", "Effectors act:\nsweat, vasodilation", "Temperature falls\nto set point"], w=430, h=260, bw=150, bh=40, title="Negative feedback: thermoregulation (too hot)")
th_sc = [
    ("Negative feedback", "reverses a change and returns the factor to its set point", ["amplifies a change", "has no receptors", "operates only in plants"], "It keeps conditions near a set point."),
    ("The thermoregulatory centre of the brain is the", "hypothalamus", ["cerebellum", "medulla only", "cortex"], "It compares blood temperature with the set point."),
    ("When body temperature falls, the skin arterioles", "constrict (vasoconstriction)", ["dilate", "have no change", "burst"], "Less blood flows near the surface, reducing heat loss."),
    ("An ectotherm", "relies mainly on external heat sources and behaviour", ["makes much metabolic heat", "has a constant core temperature always", "cannot move"], "Lizards bask to warm."),
    ("Shivering raises body temperature because", "muscle contraction releases heat from respiration", ["it cools the skin", "it dilates vessels", "it reduces metabolic rate"], "More respiration in muscle gives more heat."),
]
rate_t = (38.6 - 37.0) / 20; assert abs(rate_t - 0.08) < 1e-9
lesson(c2, "thermoregulation", "Homeostasis and temperature control",
       ["Define homeostasis and negative feedback.", "Describe the control of body temperature in a mammal.", "Compare endotherms and ectotherms."],
       [("definition", "Homeostasis", "**Homeostasis** is the maintenance of a stable internal environment (temperature, blood glucose, water potential, pH) within narrow limits, despite changes outside. A control loop has a **set point**, **receptors** that detect deviation, a **control centre** (coordinator) and **effectors** that bring about a correction. **Negative feedback** reverses a change; **positive feedback** amplifies it (e.g. contractions in childbirth)."),
        ("pieges", "Both directions of correction", "Control needs effectors for **both** directions of change (e.g. warming and cooling). Do not describe only the response to heat. Also remember that the **hypothalamus** detects temperature of the blood and receives input from skin thermoreceptors; it does not itself sweat. Vasodilation does not 'move arterioles closer to the skin': the vessels widen so more blood flows through the skin capillaries."),
        ("retenir", "Response to heat and cold", "**Too hot**: vasodilation, sweating, hairs lie flat, lower metabolic rate, behaviour (shade). **Too cold**: vasoconstriction, no sweat, hair erector muscles raise hairs (traps air), shivering, adrenaline/thyroxine raise metabolic rate, behaviour (curl up, huddle). Brown fat in some young mammals releases heat directly."),
        ("retenir", "Endotherms and ectotherms", "**Endotherms** (birds, mammals) make heat internally and keep a nearly constant core temperature; they need more food. **Ectotherms** (reptiles, amphibians, fish, insects) depend on environmental heat and behaviour; they need less food and can survive on less. Both have enzymes with an optimum temperature, which is why temperature control matters.")],
       [("Worked example — rate of rise", "During exercise a runner's core temperature rises from 37.0 °C to 38.6 °C in 20 minutes. Calculate the mean rate of rise.", ["Rise = 38.6 − 37.0 = 1.6 °C.", "Time = 20 minutes.", "Rate = 1.6 ÷ 20 = %.2f °C per minute." % rate_t], "0.08 °C per minute"),
        ("Worked example — feedback loop", "A person at Mount Cameroon at night begins to shiver and the skin looks pale. Explain with negative feedback.", ["Falling blood temperature is detected by the hypothalamus.", "Effectors: skeletal muscles shiver (heat) and skin arterioles constrict (pallor, less heat loss).", "Blood temperature rises back to the set point and the effectors switch off."], "Correction through shivering and vasoconstriction")],
       [M("Which part of the brain monitors blood temperature?", "Hypothalamus", ["Cerebellum", "Pituitary posterior lobe only", "Medulla"], "It contains the thermoregulatory centre."),
        TF("Sweating is an example of positive feedback.", False, "It cools the body, reversing the rise: negative feedback."),
        N("A patient's temperature falls from 39.0 °C to 37.2 °C over 3 hours. Calculate the mean fall in °C per hour.", 0.6, "(39.0 − 37.2) ÷ 3 = 0.6.", unit="°C/h")],
       [O("Explain how vasodilation of skin arterioles helps to lose heat.", "Dilated arterioles allow more warm blood to flow through the capillaries near the skin surface, so more heat is lost by radiation and convection to the surroundings.", ["More blood to skin capillaries (1)", "Heat radiated from the surface (1)", "Body temperature falls (1)"]),
        M("Why do ectotherms need much less food than endotherms of the same mass?", "They do not use energy to maintain a high body temperature", ["They have a higher metabolic rate", "They produce more heat", "They have more fat"], "Endotherms spend much energy on heat production.", tier="approfondissement")],
       P("A person with a fever has a core temperature that rises quickly with shivering, then falls with sweating as the fever breaks.",
         [pm("Which structure detects the change in blood temperature?", "Hypothalamus", ["Skin only", "Kidney", "Pancreas"], "It is the control centre.", 1),
          pm("Which response raises body temperature?", "Shivering", ["Sweating", "Vasodilation", "Hairs lying flat"], "Shivering generates heat.", 1),
          po("Explain why sweating is only effective if the air is not saturated with water vapour.", "Evaporation, which takes heat from the skin, requires a gradient of water vapour between the skin and the air; in saturated air no net evaporation occurs.", ["Evaporation cools (1)", "No gradient in humid air (1)"], 2)]),
       th_sc, (fig, "Negative feedback control of body temperature when too hot.", "Four boxes around a ring: temperature rises, receptors detect it, effectors act, and the temperature falls back."),
       notes=["Fever example is a simplified description; clinical details are not covered.", "Brown fat and hormonal effects are outlined only."])

# ---- 6 nervous coordination
y = lambda mv: round(40 + (50 - mv) * 170 / 140, 1)
it = [T(215, 16, "Action potential in a neurone", 14, bold=True),
      LINE(50, 40, 50, 215, width=2, arrow="start"), LINE(50, 186, 410, 186, width=1, color="grey", dash=True),
      POLY([60, y(-70), 120, y(-70), 140, y(-55), 165, y(40), 195, y(-80), 230, y(-70), 400, y(-70)], stroke="red", width=3, closed=False),
      T(46, 50, "+40", 11, anchor="end"), T(46, 188, "−70", 11, anchor="end"), T(46, 130, "mV", 11, anchor="end", color="grey"),
      T(290, 176, "resting potential", 11, color="grey"), T(86, 160, "threshold", 11, color="blue"),
      T(215, 70, "depolarisation", 11, anchor="start", color="red"), T(215, 140, "repolarisation", 11, anchor="start", color="red"),
      T(230, 229, "time (ms)", 11, color="grey")]
fig = shapes(it, 430, 240)
ner_sc = [
    ("The resting potential of a neurone is about", "−70 mV, inside negative", ["+70 mV", "0 mV", "+40 mV"], "Maintained by the Na⁺/K⁺ pump and K⁺ leak channels."),
    ("During depolarisation", "voltage-gated Na⁺ channels open and Na⁺ enters the axon", ["K⁺ rushes in", "Na⁺ leaves", "the pump stops all ions"], "The inside becomes positive."),
    ("The refractory period ensures that", "impulses travel in one direction and are discrete", ["impulses travel faster", "no ions move", "synapses are blocked permanently"], "Na⁺ channels cannot reopen immediately."),
    ("Myelinated axons conduct faster because", "impulses jump between nodes of Ranvier (saltatory conduction)", ["myelin is a conductor of charge", "axons are wider", "they have more synapses"], "Depolarisation occurs only at the nodes."),
    ("At a synapse, the neurotransmitter diffuses across the", "synaptic cleft", ["myelin sheath", "node of Ranvier", "dendrite only"], "It binds to receptors on the postsynaptic membrane."),
]
vel = 0.5 / 0.005; assert vel == 100
lesson(c2, "nervous", "Nervous coordination",
       ["Explain the resting potential and the action potential.", "Describe the transmission of impulses along axons and across synapses.", "Calculate the speed of conduction."],
       [("definition", "Resting and action potential", "At rest the inside of a neurone is about **−70 mV** relative to the outside. The **Na⁺/K⁺ pump** moves 3 Na⁺ out and 2 K⁺ in; the membrane is more permeable to K⁺ than Na⁺ (K⁺ leaks out). A stimulus that reaches the **threshold** (about −55 mV) opens **voltage-gated Na⁺ channels**: Na⁺ floods in (**depolarisation**, up to about +40 mV). Na⁺ channels close and **K⁺ channels** open: K⁺ leaves (**repolarisation**), often briefly overshooting (hyperpolarisation)."),
        ("pieges", "All-or-nothing", "An action potential is **all-or-nothing**: below threshold nothing happens; above threshold the size is always the same. A stronger stimulus gives a **higher frequency** of impulses, not a bigger impulse. The **refractory period** (Na⁺ channels temporarily unable to open) makes impulses discrete and one-way. Do not confuse **resting potential** (maintained by the pump) with the action potential (caused by voltage-gated channels)."),
        ("retenir", "Conduction and synapse", "A local circuit of ions depolarises the next section of the axon. In **myelinated** neurones the impulse jumps between **nodes of Ranvier** (saltatory conduction, faster and less energy). At a **cholinergic synapse**: impulse arrives, **Ca²⁺ channels open**, vesicles fuse and release **acetylcholine**, which diffuses across the cleft, binds receptors and opens Na⁺ channels in the postsynaptic membrane. The transmitter is broken down by acetylcholinesterase."),
        ("retenir", "Speed and reflexes", "Speed is greater in **wider** axons, **myelinated** axons and at **higher temperature**. A **reflex arc**: receptor → sensory neurone → relay neurone in the spinal cord → motor neurone → effector. Reflexes are fast and automatic, protecting the body (e.g. withdrawing a hand from a hot object).")],
       [("Worked example — speed", "An impulse travels 0.50 m along a myelinated motor neurone in 5.0 ms. Calculate the speed.", ["Speed = distance ÷ time.", "Convert: 5.0 ms = 0.0050 s.", "Speed = 0.50 ÷ 0.0050 = %.0f m s⁻¹." % vel], "100 m s⁻¹"),
        ("Worked example — frequency", "A neurone fires 25 impulses in 0.5 s when lightly stimulated and 75 when strongly stimulated. Compare the frequencies.", ["Light stimulus: 25 ÷ 0.5 = 50 impulses per second.", "Strong stimulus: 75 ÷ 0.5 = 150 impulses per second.", "The size of each impulse is unchanged; only frequency increases (3 times)."], "50 and 150 impulses per second")],
       [M("What causes repolarisation?", "Efflux of K⁺ through voltage-gated K⁺ channels", ["Influx of Na⁺", "Closure of K⁺ channels", "Influx of Ca²⁺"], "K⁺ leaves, making the inside negative again."),
        TF("A stronger stimulus produces a bigger action potential.", False, "Action potentials are all-or-nothing; strength is coded by frequency."),
        N("An impulse travels 0.2 m in 4 ms. Calculate the speed in m s⁻¹.", 50, "0.2 ÷ 0.004 = 50.", unit="m/s")],
       [O("Explain how myelination increases the speed of conduction.", "Myelin insulates the axon so depolarisation can occur only at the nodes of Ranvier; the impulse jumps from node to node (saltatory conduction), which is faster than depolarising the whole length of the axon.", ["Myelin insulates (1)", "Depolarisation only at nodes (1)", "Jumps node to node (1)"]),
        M("An organophosphate pesticide inhibits acetylcholinesterase. The effect at a cholinergic synapse is", "acetylcholine remains and continues to stimulate the postsynaptic membrane", ["no acetylcholine is released", "receptors are destroyed", "Na⁺ channels never open"], "Without breakdown the transmitter keeps acting.", tier="approfondissement")],
       P("A student studies a reflex: touching a hot surface and withdrawing the hand.",
         [pm("Which neurone carries the impulse from the receptor to the spinal cord?", "Sensory neurone", ["Motor neurone", "Relay neurone only", "Effector"], "Sensory neurones carry impulses towards the CNS.", 1),
          pn("An impulse travels 0.9 m to the muscle at 60 m s⁻¹. Calculate the time in ms.", 15, "0.9 ÷ 60 = 0.015 s = 15 ms.", 0.1, "ms", 2),
          po("Explain why the reflex is protective.", "It is rapid and automatic because there are few synapses and no need for decisions in the brain, so injury is limited.", ["Rapid (1)", "Automatic / few synapses (1)"], 2)]),
       ner_sc, (fig, "The action potential: resting level, threshold, rapid depolarisation to about +40 mV and repolarisation.", "A line graph of membrane potential against time with a sharp red spike from −70 mV up to +40 mV and back below resting level."),
       notes=["Threshold −55 mV, peak +40 mV and the 3:2 pump ratio are standard textbook values.", "Chemical synapse described is the cholinergic type; other transmitters not covered."])
assert abs(0.9 / 60 * 1000 - 15) < 1e-9

# ---- 7 hormonal control and blood glucose
fig = ring(["Blood glucose\ntoo high", "β cells release\ninsulin", "Liver and muscle\ntake up glucose", "Blood glucose\nfalls"], w=430, h=260, bw=170, bh=40, title="Control of blood glucose (high)")
hg_sc = [
    ("Which hormone lowers blood glucose?", "Insulin", ["Glucagon", "Adrenaline", "ADH"], "Insulin promotes uptake and glycogenesis."),
    ("Glucagon is secreted by", "α cells of the islets of Langerhans", ["β cells", "the liver", "the adrenal gland"], "It raises blood glucose by glycogenolysis."),
    ("Glycogenesis is the", "conversion of glucose to glycogen", ["breakdown of glycogen", "conversion of amino acids to glucose", "breakdown of lipids"], "Stimulated by insulin."),
    ("In type 1 diabetes", "the β cells fail to produce enough insulin", ["cells make too much insulin", "glucagon is absent", "the pancreas makes lipase"], "Treatment is by insulin and diet."),
    ("A glucose tolerance test shows a person with diabetes has", "a high glucose level that falls very slowly", ["a low glucose level", "glucose falling quickly", "no glucose in blood"], "Glucose remains high."),
]
rg = (9.0 - 5.0) / 90; assert abs(rg - 0.0444) < 1e-3
lesson(c2, "blood-glucose", "Hormones and the control of blood glucose",
       ["Describe the roles of insulin, glucagon and adrenaline.", "Explain negative feedback in the control of blood glucose.", "Compare type 1 and type 2 diabetes and interpret a glucose tolerance test."],
       [("definition", "Endocrine system", "**Hormones** are chemical messengers made by endocrine glands, carried in the blood to **target cells** with specific receptors. Effects are slower and longer-lasting than nervous effects. In the pancreas the **islets of Langerhans** contain **β cells** (make insulin) and **α cells** (make glucagon). Blood glucose is normally kept at roughly 4–6 mmol dm⁻³ (a typical teaching range; check the figure used locally)."),
        ("retenir", "Insulin and glucagon", "**High glucose**: β cells release **insulin**; target cells (liver, muscle, fat) take up more glucose through more glucose carriers, convert glucose to **glycogen** (glycogenesis) and respire more. **Low glucose**: α cells release **glucagon**: liver converts glycogen to glucose (**glycogenolysis**) and makes glucose from amino acids and glycerol (**gluconeogenesis**). **Adrenaline** also raises glucose."),
        ("pieges", "Insulin and glucagon act as a pair", "Do not say 'insulin turns glucose into glycogen' without saying that it acts on the **liver and muscle cells**, and do not say glucagon is a form of insulin. Negative feedback in both directions: the same pancreas senses the glucose level directly. Type 1 diabetes is an **autoimmune** loss of β cells (insulin injections); type 2 is reduced sensitivity of target cells to insulin, linked to diet, obesity and inactivity (diet, exercise, medicines)."),
        ("retenir", "Glucose tolerance test", "After fasting, the person drinks a glucose solution and blood glucose is measured at intervals. In a healthy person it rises and returns to the fasting level within about two hours; in a person with diabetes it rises higher and stays high. Urine tests and blood tests are used in screening; interpretation belongs to clinicians.")],
       [("Worked example — rate of fall", "After a meal a student's blood glucose falls from 9.0 to 5.0 mmol dm⁻³ in 90 minutes. Calculate the mean rate of fall.", ["Fall = 9.0 − 5.0 = 4.0 mmol dm⁻³.", "Time = 90 min.", "Rate = 4.0 ÷ 90 = %.3f mmol dm⁻³ min⁻¹." % rg], "0.044 mmol dm⁻³ min⁻¹"),
        ("Worked example — percentage change", "A diabetic patient's fasting glucose is 8.0 mmol dm⁻³; after treatment it is 5.6. Calculate the percentage decrease.", ["Decrease = 8.0 − 5.6 = 2.4.", "Percentage = 2.4 ÷ 8.0 × 100.", "= %.0f %%." % (2.4 / 8.0 * 100)], "30 %")],
       [M("Which organ stores glucose as glycogen in response to insulin?", "Liver", ["Kidney", "Pancreas", "Lung"], "Liver and muscle store glycogen."),
        TF("Glucagon causes the conversion of glycogen to glucose.", True, "This is glycogenolysis in the liver."),
        N("Blood glucose falls from 10.0 to 6.5 mmol dm⁻³. What is the percentage decrease?", 35, "(10.0 − 6.5) ÷ 10.0 × 100 = 35 %.", unit="%")],
       [O("Explain how negative feedback keeps blood glucose stable after a meal.", "Rising glucose is detected by β cells, which release insulin. Insulin increases uptake of glucose by cells and conversion to glycogen, lowering blood glucose. When the level returns to normal, insulin secretion falls, so the response switches off.", ["β cells detect (1)", "Insulin lowers glucose (1)", "Secretion decreases at set point (1)"]),
        M("A person has high blood glucose and normal insulin levels but target cells respond poorly. This best describes", "type 2 diabetes", ["type 1 diabetes", "a healthy person", "an insulin overdose"], "Reduced sensitivity of target cells.", tier="approfondissement")],
       P("A glucose tolerance test gave blood glucose (mmol dm⁻³): 0 min 5.0; 30 min 9.0; 60 min 8.0; 120 min 5.2 for person A, and 0 min 8.5; 30 min 14.0; 60 min 15.0; 120 min 13.0 for person B.",
         [pn("By how much did glucose rise in person A in the first 30 minutes?", 4.0, "9.0 − 5.0 = 4.0.", 0.01, "mmol/dm³", 1),
          pm("Which person has a pattern consistent with diabetes?", "Person B", ["Person A", "Both", "Neither"], "B starts high and stays high.", 1),
          po("Explain why the glucose in person A returns to the starting level.", "The glucose is detected by β cells, which release insulin; target cells take up glucose and store it as glycogen, restoring the set point.", ["Insulin release (1)", "Uptake / glycogen formation (1)"], 2)]),
       hg_sc, (fig, "Negative feedback control of blood glucose after a meal.", "Four boxes around a ring: glucose rises, beta cells release insulin, liver and muscle take up glucose, glucose falls."),
       notes=["Normal blood glucose range varies between sources (fasting about 4–6 mmol dm⁻³); health content must be reviewed by a health professional.", "Patient data in the problem are illustrative, not real measurements."])

# ---- 8 kidney
fig = vflow(["Glomerulus", "Proximal tubule", "Loop of Henlé", "Distal tubule, collecting duct", "Ureter"], w=440, size=12, bw=220,
            notes=["ultrafiltration (pressure)", "reabsorbs glucose, amino acids", "gradient in the medulla", "ADH: water reabsorbed", "urine to the bladder"], title="Path through a nephron")
kd_sc = [
    ("Ultrafiltration occurs in the", "glomerulus and Bowman's capsule", ["loop of Henlé", "collecting duct", "bladder"], "High hydrostatic pressure forces small molecules out of the capillaries."),
    ("Which is normally NOT found in the glomerular filtrate?", "Large plasma proteins", ["Glucose", "Urea", "Amino acids"], "The basement membrane stops large proteins."),
    ("All glucose is normally reabsorbed in the", "proximal convoluted tubule", ["collecting duct", "loop of Henlé", "bladder"], "By active transport and facilitated diffusion."),
    ("ADH makes the collecting duct", "more permeable to water", ["less permeable to water", "secrete more urea", "block filtration"], "More aquaporins are inserted; urine is more concentrated."),
    ("A person who drinks a lot of water will have", "a large volume of dilute urine", ["a small volume of concentrated urine", "no urine", "urine with glucose"], "Less ADH is released."),
]
gfr_day = 125 * 60 * 24 / 1000; assert gfr_day == 180
reab = (180 - 1.5) / 180 * 100; assert abs(reab - 99.1667) < 1e-3
lesson(c2, "kidney", "The kidney: excretion and osmoregulation",
       ["Describe the structure of the nephron and the processes of ultrafiltration and selective reabsorption.", "Explain how the loop of Henlé and ADH control water balance.", "Calculate filtration and reabsorption quantities."],
       [("definition", "Nephron and ultrafiltration", "Each kidney has about a million **nephrons**. In the **renal capsule** blood in the **glomerulus** (high hydrostatic pressure because the efferent arteriole is narrower than the afferent) forces water, glucose, amino acids, urea and ions through the capillary wall, basement membrane and podocytes into the **Bowman's capsule** (filtrate). **Blood cells and large proteins** stay in the blood. Normal GFR is about 125 cm³ per minute (textbook value)."),
        ("retenir", "Reabsorption and loop of Henlé", "In the **proximal convoluted tubule** microvilli and many mitochondria allow reabsorption of all glucose and amino acids (active transport), most salts and about 80 % of the water. The **loop of Henlé** (counter-current multiplier) pumps ions into the medulla so that medulla tissue fluid has very low water potential; water leaves the collecting duct by osmosis if the duct is permeable."),
        ("retenir", "ADH and osmoregulation", "If blood water potential falls (dehydration), **osmoreceptors** in the hypothalamus stimulate the posterior pituitary to release **ADH**. ADH makes the distal tubule and collecting duct more permeable to water (more aquaporins), so more water is reabsorbed and a small volume of concentrated urine is produced. If water potential is high, less ADH is released: a large volume of dilute urine."),
        ("pieges", "Filtration is not selective", "Ultrafiltration is selective only by **size**; useful substances like glucose enter the filtrate and are reabsorbed later. Urea is a waste made in the liver from excess amino acids (deamination). Do not say the kidney 'filters out blood cells and keeps them in the nephron'; blood cells never leave the capillary. **Dehydration** gives more ADH, not less.")],
       [("Worked example — filtration per day", "GFR is 125 cm³ per minute. Calculate the volume filtered per day in dm³ (1 dm³ = 1000 cm³).", ["Per hour: 125 × 60 = 7500 cm³.", "Per day: 7500 × 24 = 180 000 cm³.", "= %.0f dm³." % gfr_day], "180 dm³ per day"),
        ("Worked example — percentage reabsorbed", "In one day 180 dm³ is filtered and 1.5 dm³ of urine is produced. Calculate the percentage of the filtrate reabsorbed.", ["Reabsorbed = 180 − 1.5 = 178.5 dm³.", "Percentage = 178.5 ÷ 180 × 100.", "= %.1f %%." % reab], "99.2 %")],
       [M("Which hormone controls the water permeability of the collecting duct?", "ADH", ["Insulin", "Aldosterone only", "Glucagon"], "ADH from the posterior pituitary."),
        TF("Glucose is normally present in urine of a healthy person.", False, "All glucose is reabsorbed in the proximal tubule."),
        N("If GFR is 100 cm³ per minute, how many dm³ are filtered per day?", 144, "100 × 1440 = 144 000 cm³ = 144 dm³.", unit="dm³")],
       [O("Explain why glucose appears in the urine of an untreated diabetic person.", "The blood glucose is so high that the filtrate contains more glucose than the carrier proteins of the proximal tubule can reabsorb, so some remains in the filtrate and passes into the urine.", ["High glucose in filtrate (1)", "Carriers saturated (1)", "Glucose in urine (1)"]),
        M("A person lost much water by sweating on a hot day in Garoua. What is the expected change in ADH release and urine?", "More ADH; small volume of concentrated urine", ["Less ADH; large volume of dilute urine", "No change in ADH", "More ADH; large volume of dilute urine"], "Low water potential stimulates ADH.", tier="approfondissement")],
       P("A student collected data: in a day 150 dm³ is filtered; the person produces 1.2 dm³ of urine.",
         [pn("How many dm³ of filtrate are reabsorbed?", 148.8, "150 − 1.2 = 148.8.", 0.1, "dm³", 1),
          pn("What percentage of the filtrate is reabsorbed? (1 d.p.)", round(148.8 / 150 * 100, 1), "148.8 ÷ 150 × 100 = 99.2 %.", 0.1, "%", 2),
          po("State two substances found in the filtrate but not in the urine of a healthy person.", "Glucose and amino acids (all reabsorbed in the proximal tubule).", ["Glucose (1)", "Amino acids (1)"], 2)]),
       kd_sc, (fig, "The path of filtrate through the nephron with the main process at each stage.", "Five boxes in a vertical chain from glomerulus to ureter with notes on each stage."),
       notes=["GFR of 125 cm³ min⁻¹ is a standard textbook value; counter-current multiplier detail may exceed the syllabus.", "Health-related statements must be reviewed by a health professional."])

# =====================================================================  CH 3  GENETICS
c3 = p.chapter("genetics", "Genetics and inheritance", programRef="GCE A Level Biology — Inheritance; gene interaction; linkage; population genetics (to be checked)")

# ---- 9 Mendelian inheritance
fig = tabfig(["×", "T (tall)", "t (short)"], [["T", "TT", "Tt"], ["t", "Tt", "tt"]], w=330, colw=[80, 125, 125], title="Tt × Tt: gametes along the sides")
mn_sc = [
    ("A heterozygous individual has", "two different alleles of a gene", ["two identical alleles", "no alleles", "three alleles"], "e.g. Tt."),
    ("A cross Tt × Tt gives a phenotypic ratio (complete dominance) of", "3 dominant : 1 recessive", ["1 : 1", "1 : 2 : 1", "all dominant"], "TT, Tt, Tt show the dominant trait; tt the recessive."),
    ("A test cross is between an unknown dominant phenotype and", "a homozygous recessive", ["a homozygous dominant", "a heterozygote", "a clone"], "A 1 : 1 ratio shows the unknown is heterozygous."),
    ("Codominance means", "both alleles are fully expressed in the heterozygote", ["one allele hides the other", "the heterozygote is intermediate", "alleles are on the X chromosome"], "Example: blood group AB."),
    ("Haemophilia is X-linked recessive. A carrier woman and a normal man: the chance that a son is affected is", "1 in 2", ["1 in 4 of sons", "none", "all sons"], "Half the sons receive her Xʰ chromosome."),
]
lesson(c3, "mendelian", "Monohybrid crosses, codominance and sex linkage",
       ["Use genetic terms correctly and draw genetic diagrams for monohybrid crosses.", "Explain codominance, multiple alleles (ABO) and sex linkage.", "Predict ratios and probabilities of offspring."],
       [("definition", "Key terms", "A **gene** is a length of DNA coding for a polypeptide; an **allele** is a version of a gene at the same **locus**. **Genotype** is the alleles present; **phenotype** is the observable trait. **Homozygous**: two identical alleles; **heterozygous**: two different. A **dominant** allele is expressed in the heterozygote; a **recessive** only when homozygous. Mendel's first law (segregation): the two alleles separate into different gametes."),
        ("pieges", "Layout of a genetic diagram", "Always show: parental phenotypes and genotypes, gametes (circled or listed), offspring genotypes, offspring phenotypes and the ratio. Use a **letter that makes dominance clear** (T and t; not T and S). Probabilities for independent events multiply; do not give ratios as a total of observed numbers in a small sample. A 3 : 1 ratio is an expectation, not a certainty."),
        ("retenir", "Codominance and multiple alleles", "In **codominance** both alleles are expressed. **ABO blood groups**: three alleles Iᴬ, Iᴮ (codominant) and i (recessive). Genotypes IᴬIᴬ or Iᴬi = group A; IᴮIᴮ or Iᴮi = B; IᴬIᴮ = AB; ii = O. In **sickle-cell** inheritance HbA and HbS are codominant at the molecular level: HbAHbS people have sickle-cell trait and a degree of protection against malaria."),
        ("retenir", "Sex linkage", "Genes on the **X chromosome** (e.g. haemophilia, red-green colour blindness) show sex-linked inheritance. Males (XY) have one X, so a single recessive allele is expressed. Fathers pass the X to daughters (not sons); carrier mothers pass the allele to half their sons (affected) and half their daughters (carriers). Notation: Xᴴ (normal), Xʰ (affected), Y.")],
       [("Worked example — monohybrid ratio", "Two heterozygous tall pea plants (Tt) are crossed. Among 400 offspring, how many are expected to be short?", ["Gametes T, t from each parent: TT, Tt, Tt, tt.", "Short = tt = ¼ of offspring.", "Expected short = 400 × ¼ = %d." % (400 // 4)], "100 short"),
        ("Worked example — sex-linked cross", "A carrier mother (XᴴXʰ) and a normal father (XᴴY) have children. State the chance that a child is a haemophiliac boy.", ["Offspring: XᴴXᴴ, XᴴXʰ, XᴴY, XʰY (each ¼).", "Only XʰY is an affected son: ¼.", "(Half of sons, but one quarter of all children.)"], "¼ of all children")],
       [M("What are the possible phenotypes of the offspring of Iᴬi × Iᴮi?", "A, B, AB and O", ["A and B only", "AB only", "O only"], "Genotypes IᴬIᴮ, Iᴬi, Iᴮi, ii in equal proportions."),
        TF("A heterozygote with a codominant pair shows both phenotypes.", True, "Both alleles are expressed."),
        N("In a cross between two sickle-cell trait carriers (HbAHbS), what percentage of children are expected to be HbSHbS?", 25, "¼ = 25 %.", unit="%")],
       [O("A woman with blood group A has a child with blood group O. Explain how this is possible and give the woman's genotype.", "The woman must be heterozygous (Iᴬi); the child inherited i from her and i from the father; each parent contributed a recessive allele i, so the child is ii (group O).", ["Woman Iᴬi (1)", "Child ii (1)", "Both parents carry i (1)"]),
        M("A colour-blind man (XᶜY) and a woman homozygous for normal vision (XᴺXᴺ) have a daughter. She will be", "a carrier with normal vision", ["colour blind", "homozygous normal", "unable to carry the allele"], "She receives Xᶜ from her father and Xᴺ from her mother.", tier="approfondissement")],
       P("In a family from Bafoussam, both parents have normal vision; their son is colour blind. Colour blindness is X-linked recessive.",
         [pm("What is the genotype of the mother (Xᶜ = colour-blind allele, Xᴺ = normal allele)?", "XᴺXᶜ (carrier)", ["XᴺXᴺ", "XᶜXᶜ", "XᶜY"], "She must carry the recessive allele to pass it to her son.", 1),
          pn("What is the probability that the next child is a colour-blind boy (as a percentage)?", 25, "¼ = 25 %.", 0, "%", 2),
          po("Explain why colour blindness is more common in males than in females.", "Males have only one X chromosome, so a single recessive allele is expressed; females need two copies.", ["One X in males (1)", "Females need two recessive alleles (1)"], 2)]),
       mn_sc, (fig, "Punnett square for a cross between two heterozygous tall plants (Tt × Tt).", "A two-by-two grid with gametes T and t on each side showing TT, Tt, Tt and tt."),
       notes=["Symbols for colour blindness (XᶜXᶜ) and haemophilia (Xʰ) are conventions; mark schemes accept any clear convention.", "Health examples need sensitive wording; see the review checklist."])

# ---- 10 dihybrid, interaction, linkage
heads = ["×", "RY", "Ry", "rY", "ry"]
gam = ["RY", "Ry", "rY", "ry"]
rows = []
for a in gam:
    r = [a]
    for b in gam:
        al = sorted([a[0], b[0]], key=lambda c: (c.lower(), c.islower())) ; bl = sorted([a[1], b[1]], key=lambda c: (c.lower(), c.islower()))
        r.append("".join(al + bl))
    rows.append(r)
fig = tabfig(heads, rows, w=430, colw=[70, 85, 85, 85, 85], title="RrYy × RrYy: 16 combinations", size=12)
from collections import Counter
cnt = Counter()
for a in gam:
    for b in gam:
        ph = ("R" if "R" in (a[0], b[0]) else "r") + ("Y" if "Y" in (a[1], b[1]) else "y")
        cnt[ph] += 1
assert cnt == {"RY": 9, "Ry": 3, "rY": 3, "ry": 1}
exp9 = [round(1600 * k / 16) for k in (9, 3, 3, 1)]; assert exp9 == [900, 300, 300, 100]
di_sc = [
    ("The expected phenotypic ratio from a dihybrid cross of two double heterozygotes (independent genes) is", "9 : 3 : 3 : 1", ["3 : 1", "1 : 1 : 1 : 1", "1 : 2 : 1"], "Independent assortment."),
    ("Epistasis is when", "one gene masks or modifies the expression of another gene", ["alleles are codominant", "genes are linked", "a gene is on the X chromosome"], "Gene interaction at different loci."),
    ("A recessive epistatic interaction typically gives a ratio of", "9 : 3 : 4", ["9 : 7", "12 : 3 : 1", "3 : 1"], "e.g. coat colour in Labrador retrievers."),
    ("Linked genes", "are on the same chromosome and tend to be inherited together", ["are always on different chromosomes", "assort independently", "are on the Y chromosome only"], "Recombinants arise by crossing over."),
    ("In a test cross of a double heterozygote, unlinked genes give offspring in the ratio", "1 : 1 : 1 : 1", ["9 : 3 : 3 : 1", "3 : 1", "1 : 1"], "Independent assortment."),
]
rf = (40 + 40) / 1000 * 100; assert rf == 8
lab = [round(320 * k / 16) for k in (9, 3, 4)]; assert lab == [180, 60, 80]
lesson(c3, "dihybrid-linkage", "Dihybrid crosses, gene interaction and linkage",
       ["Predict the results of dihybrid crosses with independent assortment.", "Explain epistasis and the modified ratios 9 : 3 : 4, 9 : 7 and 12 : 3 : 1.", "Explain linkage and calculate recombination frequency."],
       [("definition", "Dihybrid inheritance", "Mendel's second law (**independent assortment**): alleles of genes on different chromosomes (or far apart) separate independently. A cross RrYy × RrYy (round/wrinkled, yellow/green seed; R and Y dominant) gives 16 combinations and phenotypes in the ratio **9 round yellow : 3 round green : 3 wrinkled yellow : 1 wrinkled green**. A test cross RrYy × rryy gives 1 : 1 : 1 : 1."),
        ("retenir", "Epistasis (gene interaction)", "In **epistasis** one gene affects the expression of another. Ratios are modifications of 9 : 3 : 3 : 1:\n- **Recessive epistasis 9 : 3 : 4** (Labrador coat: B black, b brown; ee prevents pigment deposition so yellow).\n- **Complementary genes 9 : 7** (two dominant alleles needed for the pigment).\n- **Dominant epistasis 12 : 3 : 1** (a dominant allele at one locus masks the other gene)."),
        ("retenir", "Linkage and recombination", "Genes on the **same chromosome** are **linked** and are inherited together unless **crossing over** separates them. In a test cross of a double heterozygote, **parental** types are most frequent and **recombinants** are fewer. **Recombination frequency** = recombinants ÷ total offspring × 100 %; it is proportional to the distance apart on the chromosome (1 % = 1 map unit). Maximum is 50 % (same as independent assortment)."),
        ("pieges", "Check before assuming 9 : 3 : 3 : 1", "A 9 : 3 : 3 : 1 ratio needs two genes on **different chromosomes**, complete dominance and large samples. If the offspring figures are far from the expected ratio, test with chi-squared (see the statistics lessons) before choosing between linkage and independent assortment. Add the numbers of 9 + 3 + 4 = 16 to see that epistatic ratios are still sixteenths.")],
       [("Worked example — expected numbers", "RrYy × RrYy produces 1600 seeds. Calculate the expected numbers of each phenotype.", ["Ratio 9 : 3 : 3 : 1 with total 16 parts; 1 part = 1600 ÷ 16 = 100.", "Round yellow = 9 × 100 = %d; round green = %d; wrinkled yellow = %d." % (exp9[0], exp9[1], exp9[2]), "Wrinkled green = %d." % exp9[3]], "900 : 300 : 300 : 100"),
        ("Worked example — recombination frequency", "A test cross gives 1000 offspring: 460 AB, 460 ab, 40 Ab, 40 aB. Calculate the recombination frequency.", ["Parental types: AB and ab (460 + 460 = 920).", "Recombinants: Ab and aB = 40 + 40 = 80.", "Frequency = 80 ÷ 1000 × 100 = %.0f %%, so the genes are 8 map units apart." % rf], "8 %")],
       [M("The offspring ratio from RrYy × RrYy that are wrinkled and green is", "1 in 16", ["1 in 4", "3 in 16", "9 in 16"], "Only rryy."),
        TF("Linked genes always assort independently.", False, "They are inherited together unless separated by crossing over."),
        N("A test cross gives 500 offspring, of which 60 are recombinants. Calculate the recombination frequency as a percentage.", 12, "60 ÷ 500 × 100 = 12 %.", unit="%")],
       [O("In Labrador retrievers B (black) is dominant to b (brown), and ee gives yellow regardless of the B alleles. Explain the ratio from BbEe × BbEe.", "Black = B_E_ = 9/16; brown = bbE_ = 3/16; yellow = ee = 3/16 (with B_) + 1/16 (bb) = 4/16, so the ratio is 9 : 3 : 4. The ee genotype is epistatic: it masks the effect of the B gene.", ["Genotype classes (1)", "Yellow = 4/16 combined (1)", "Ratio 9 : 3 : 4 (1)"]),
        M("The recombination frequencies are A–B 8 %, B–C 12 %, A–C 20 %. The order of the genes on the chromosome is", "A – B – C", ["A – C – B", "B – A – C", "C – A – B"], "A–C = A–B + B–C = 20, so B lies between A and C.", tier="approfondissement")],
       P("Labrador puppies from BbEe × BbEe: a breeder in Douala expects 320 puppies in total.",
         [pn("How many black puppies are expected?", lab[0], "9/16 of 320 = 180.", 0, None, 1),
          pn("How many yellow puppies are expected?", lab[2], "4/16 of 320 = 80.", 0, None, 1),
          pn("How many brown puppies are expected?", lab[1], "3/16 of 320 = 60.", 0, None, 1),
          po("Explain why yellow puppies include more than one genotype.", "Any ee puppy is yellow whether it carries B or b, so BBee, Bbee and bbee are all yellow.", ["ee masks B/b (1)", "BBee, Bbee, bbee (1)"], 2)]),
       di_sc, (fig, "The 16 genotypes of a dihybrid cross RrYy × RrYy.", "A five-by-five table with gametes RY, Ry, rY and ry along each side and the sixteen offspring genotypes inside."),
       notes=["Epistatic ratios and the Labrador example are textbook examples; some syllabi expect only 9 : 3 : 4 and 9 : 7.", "Recombination frequency as map units is standard."])

# ---- 11 Hardy-Weinberg
q = 0.2; pp = 0.8
fig = bars([("AA (p²)", round(pp ** 2, 2), "blue"), ("Aa (2pq)", round(2 * pp * q, 2), "orange"), ("aa (q²)", round(q ** 2, 2), "red")], w=400, h=240)
hw_sc = [
    ("In the Hardy–Weinberg equation p + q = 1, p and q are", "allele frequencies", ["genotype frequencies", "numbers of individuals", "mutation rates"], "p is the frequency of the dominant allele, q of the recessive."),
    ("The frequency of heterozygotes is given by", "2pq", ["p²", "q²", "p + q"], "Two ways to form a heterozygote."),
    ("Which is NOT a condition for Hardy–Weinberg equilibrium?", "Strong natural selection", ["A large population", "Random mating", "No migration"], "Selection changes allele frequencies."),
    ("If q² = 0.09, then q is", "0.3", ["0.09", "0.81", "0.7"], "√0.09 = 0.3."),
    ("Evolution at the population level is a change in", "allele frequencies over generations", ["the number of individuals", "body size only", "the number of species only"], "Hardy–Weinberg gives the no-change reference."),
]
q2 = 0.09; q_ = q2 ** 0.5; p_ = 1 - q_; carriers = 2 * p_ * q_ * 1000
assert abs(q_ - 0.3) < 1e-12 and abs(carriers - 420) < 1e-9
counts = {"AA": 98, "Aa": 84, "aa": 18}; n = sum(counts.values()); fA = (2 * 98 + 84) / (2 * n)
assert n == 200 and abs(fA - 0.7) < 1e-12
lesson(c3, "hardy-weinberg", "Population genetics and the Hardy–Weinberg principle",
       ["Calculate allele and genotype frequencies.", "State the conditions for Hardy–Weinberg equilibrium.", "Use p² + 2pq + q² = 1 to estimate the carrier frequency of a recessive condition."],
       [("definition", "Allele frequency", "The **gene pool** is all the alleles in a population. **Allele frequency** is the proportion of a given allele among all alleles of that gene. For one gene with two alleles A and a: p = frequency of A and q = frequency of a, so **p + q = 1**. The **Hardy–Weinberg principle**: in a large, randomly mating population with no selection, mutation, migration or drift, allele frequencies stay constant and genotype frequencies are **p² + 2pq + q² = 1**."),
        ("formule", "Equations", "p + q = 1 (alleles). p² = frequency of AA, 2pq = frequency of Aa, q² = frequency of aa. To find q from a recessive phenotype frequency: q = √(q²). Then p = 1 − q and 2pq gives the heterozygous carriers."),
        ("pieges", "Frequencies are fractions of alleles, not individuals", "The frequency of a **recessive phenotype** equals **q²**, not q. Take the square root first. Never add p² and 2pq together with q (p + q = 1 refers to alleles only). The equation applies only when the assumptions hold: large population, random mating, no selection, no mutation, no migration. If these fail, allele frequencies change (evolution)."),
        ("retenir", "Why it matters", "Hardy–Weinberg gives the expected genotype frequencies if nothing changes; a **difference** between observed and expected indicates evolutionary forces. It is used to estimate carrier frequency of recessive conditions such as cystic fibrosis and, in some regions, to model the sickle-cell allele, whose frequency is higher in areas where malaria has been common because carriers are partly protected (heterozygote advantage).")],
       [("Worked example — carrier frequency", "In a population a recessive condition affects 9 % of individuals (q² = 0.09). Estimate the proportion of carriers and the number among 1000 people.", ["q = √0.09 = %.1f; p = 1 − 0.3 = %.1f." % (q_, p_), "Carriers = 2pq = 2 × 0.7 × 0.3 = %.2f." % (2 * p_ * q_), "Among 1000 people: 0.42 × 1000 = %.0f." % carriers], "42 % (420 per 1000)"),
        ("Worked example — allele frequency from counts", "In a sample of 200 plants: 98 AA, 84 Aa, 18 aa. Calculate the frequency of allele A.", ["Total alleles = 200 × 2 = 400.", "Number of A alleles = 2 × 98 + 84 = 280.", "Frequency of A = 280 ÷ 400 = %.2f." % fA], "0.70")],
       [M("If the frequency of allele a is 0.4, the frequency of allele A is", "0.6", ["0.4", "0.16", "0.36"], "p = 1 − q."),
        TF("The frequency of a recessive phenotype equals q.", False, "It equals q²."),
        N("q = 0.2. Calculate the frequency of heterozygotes (2pq).", 0.32, "p = 0.8; 2 × 0.8 × 0.2 = 0.32.", tol=0.001)],
       [O("State three conditions for a population to be in Hardy–Weinberg equilibrium.", "Large population (no genetic drift); random mating; no natural selection; no mutation; no migration (gene flow).", ["Any three correct conditions (3)"]),
        M("A population of 500 has 20 individuals with a recessive condition (aa). The estimated frequency of the recessive allele q is", "0.2", ["0.04", "0.4", "0.02"], "q² = 20 ÷ 500 = 0.04; q = 0.2.", tier="approfondissement")],
       P("In a village near Kumba 4 % of people show a recessive trait (q² = 0.04).",
         [pn("Calculate q.", 0.2, "√0.04 = 0.2.", 0.001, None, 1),
          pn("Calculate p.", 0.8, "1 − 0.2 = 0.8.", 0.001, None, 1),
          pn("Calculate the percentage of the population that are carriers.", 32, "2pq = 2 × 0.8 × 0.2 = 0.32 = 32 %.", 0.1, "%", 2),
          po("Why would the real frequency of carriers differ from the prediction if there is heterozygote advantage?", "Selection favours heterozygotes, so they are more frequent than the equation predicts: selection violates Hardy–Weinberg.", ["Selection acts (1)", "More heterozygotes (1)"], 2)]),
       hw_sc, (fig, "Genotype frequencies expected for p = 0.8 and q = 0.2: p² = 0.64, 2pq = 0.32, q² = 0.04.", "A bar chart with three bars: AA 0.64, Aa 0.32 and aa 0.04."),
       notes=["Heterozygote advantage and sickle-cell/malaria link are textbook facts; no local prevalence figures are given.", "Example allele frequencies are invented."])

# =====================================================================  CH 4  EVOLUTION
c4 = p.chapter("evolution", "Evolution and speciation", programRef="GCE A Level Biology — Variation, selection and evolution (to be checked)")

# ---- 12 natural selection and speciation
fig = vflow(["One population in one area", "Geographical barrier splits it", "Mutation, drift, different selection", "Populations can no longer interbreed", "Two species"], w=430, size=12, bw=290, title="Allopatric speciation")
ns_sc = [
    ("Natural selection acts on", "variation in phenotypes that is heritable", ["only acquired characteristics", "only mutations in somatic cells", "individual needs"], "Better-adapted individuals leave more offspring."),
    ("A population of bacteria becomes resistant to an antibiotic because", "resistant mutants survive and reproduce", ["bacteria choose to adapt", "the antibiotic causes resistance genes", "bacteria stop dividing"], "Selection pressure favours existing mutants."),
    ("Stabilising selection", "favours the mean phenotype and reduces extremes", ["favours one extreme", "favours both extremes", "has no effect"], "Example: human birth mass."),
    ("Allopatric speciation requires", "geographical isolation", ["living in the same area", "no mutation", "asexual reproduction only"], "Gene flow stops, so populations diverge."),
    ("A reproductive isolating mechanism that acts before fertilisation is", "different courtship behaviour", ["hybrid sterility", "hybrid inviability", "death of the zygote"], "Pre-zygotic barrier."),
]
rare = 0.01; gen = 0
f = 0.01
for gen in range(1, 4):
    f = (f * 2) / (f * 2 + (1 - f))   # resistant individuals survive twice as often (illustrative)
assert f > 0.01
lesson(c4, "selection-speciation", "Natural selection and speciation",
       ["Explain how natural selection leads to adaptation.", "Distinguish stabilising, directional and disruptive selection.", "Describe allopatric and sympatric speciation and isolating mechanisms."],
       [("definition", "Natural selection", "Individuals in a population **vary**, partly because of inherited differences (mutation, meiosis, random fertilisation). More offspring are produced than can survive, so there is a **struggle for existence**. Individuals with advantageous alleles are more likely to survive and reproduce, passing those alleles on, so the **allele frequency** in the population changes over generations. Evolution is this change; it needs time and heritable variation."),
        ("pieges", "Misconceptions", "Organisms do not evolve 'because they need to' and individuals do not evolve; **populations** do. New alleles arise by **random mutation**; the environment only **selects** among existing variation. Characteristics acquired in a lifetime (e.g. strong muscles) are not inherited. **Fitness** means relative reproductive success, not strength. Always link **selection pressure → differential survival → allele frequency change**."),
        ("retenir", "Types of selection", "- **Stabilising**: extremes disadvantaged, mean favoured; variation falls (stable environment).\n- **Directional**: one extreme favoured; the mean shifts (e.g. antibiotic resistance, peppered moth in industrial areas).\n- **Disruptive**: both extremes favoured, intermediates disadvantaged; may lead to two populations.\nOther forces: **genetic drift** (random change, strong in small populations; founder effect, bottleneck) and **gene flow**."),
        ("retenir", "Speciation", "A **species** is a group that can interbreed to give fertile offspring. **Allopatric speciation**: a physical barrier (river, mountain, sea) separates populations, which diverge by mutation, selection and drift until they cannot interbreed. **Sympatric speciation**: new species arise in the same area, e.g. by polyploidy in plants or by ecological specialisation; the cichlid fishes of Cameroon's crater lakes (e.g. Barombi Mbo) are a much-cited case. **Isolating mechanisms**: temporal, behavioural, mechanical, gametic (pre-zygotic) and hybrid inviability or sterility (post-zygotic).")],
       [("Worked example — directional selection", "Resistant bacteria are 1 % of a population. An antibiotic kills all non-resistant bacteria. Describe the change in the surviving population.", ["Start: 1 resistant in every 100 cells.", "After the antibiotic only resistant cells survive and divide, so the fraction of resistant cells in the survivors is 100 %.", "Therefore the allele for resistance rises from rare to common in one treatment."], "Resistance allele becomes common very quickly"),
        ("Worked example — classify selection", "Birth mass in humans is distributed around a mean; very light and very heavy babies have lower survival. Name the type of selection.", ["Both extremes are disadvantaged.", "The mean is favoured and variation is reduced.", "This is stabilising selection."], "Stabilising selection")],
       [M("Which type of selection moves the mean of a population in one direction?", "Directional selection", ["Stabilising selection", "Disruptive selection", "Genetic drift"], "One extreme is favoured."),
        TF("Mutations occur because the organism needs them.", False, "Mutations are random; selection acts afterwards."),
        M("Which is a post-zygotic isolating mechanism?", "Hybrid sterility", ["Different mating seasons", "Different courtship songs", "Incompatible genitalia"], "The hybrid is produced but cannot reproduce (e.g. mule).")],
       [O("Explain how a population of bacteria becomes resistant to an antibiotic.", "A random mutation produces a resistance allele in a few bacteria. The antibiotic acts as a selection pressure, killing non-resistant bacteria, so resistant ones survive, reproduce and pass on the allele, and its frequency rises.", ["Mutation gives variation (1)", "Selection pressure (1)", "Survival, reproduction and allele frequency rises (1)"]),
        M("Two populations of a fish species in separate lakes can no longer interbreed when brought together. This is evidence of", "speciation", ["inbreeding", "acquired characteristics", "asexual reproduction"], "Reproductive isolation has evolved.", tier="approfondissement")],
       P("Squirrels on two sides of a newly formed river valley in Cameroon diverge over many generations.",
         [pm("What type of speciation is this?", "Allopatric", ["Sympatric", "Parapatric only", "Clonal"], "A geographical barrier separates the populations.", 1),
          pm("Which change shows that speciation has occurred?", "The two populations no longer interbreed to produce fertile offspring", ["They look different", "They live in different places", "They eat different foods"], "Reproductive isolation defines separate species.", 1),
          po("Explain the roles of mutation and selection in the divergence.", "Mutation creates new alleles at random in each population; different environments favour different alleles, so allele frequencies diverge.", ["Mutation (1)", "Different selection pressures (1)"], 2)]),
       ns_sc, (fig, "Allopatric speciation: a barrier separates populations, which diverge until they can no longer interbreed.", "Five boxes in a vertical chain from one population through barrier and divergence to two species."),
       notes=["Barombi Mbo cichlids are widely cited as a sympatric-speciation example; check the wording and whether local examples are required.", "The bacterial example is simplified and deliberately qualitative."])

# =====================================================================  CH 5  ECOLOGY
c5 = p.chapter("ecology", "Ecology", programRef="GCE A Level Biology — Ecosystems; populations; energy flow; nutrient cycles (to be checked)")

# ---- 13 populations and sampling
fig = plot(0, 12, 0, 110, curves=[("100/(1+exp(3-0.8*x))", "blue", "population size")], segments=[(0, 100, 12, 100, "red", True, "carrying capacity")], xlabel="time", ylabel="population", h=260)
pop_sc = [
    ("Quadrats are used to estimate", "the abundance of sessile or slow-moving organisms", ["fast animals only", "genetic variation", "soil pH"], "Place them randomly."),
    ("A belt transect is best for studying", "change in species along an environmental gradient", ["a uniform field", "the age of trees only", "the genes of mammals"], "Samples are taken at intervals along a line."),
    ("In the Lincoln index N = (M × n) ÷ m, m is the", "number of marked animals recaptured", ["number first marked", "total second sample", "population size"], "M = first marked, n = second sample."),
    ("The carrying capacity is", "the maximum population an environment can sustain", ["the birth rate", "the death rate", "the lag phase"], "Limited by resources and other factors."),
    ("Which is a density-dependent limiting factor?", "Competition for food", ["A flood", "A forest fire", "A volcanic eruption"], "Its effect depends on population density."),
]
mean_q = (4 + 7 + 6 + 9 + 6) / 5; dens = mean_q / 0.25; est = dens * 200
assert mean_q == 6.4 and abs(dens - 25.6) < 1e-9 and abs(est - 5120) < 1e-9
lincoln = 40 * 50 / 8; assert lincoln == 250
lesson(c5, "populations-sampling", "Populations and sampling methods",
       ["Describe random quadrat, transect and mark-release-recapture methods.", "Calculate density, percentage cover and population estimates.", "Explain population growth, carrying capacity and limiting factors."],
       [("definition", "Ecological terms", "A **population** is all the individuals of one species in a habitat; a **community** is all the populations; an **ecosystem** is the community with its physical environment; a **niche** is the role of a species. **Abundance** is the number of individuals; **frequency** is the proportion of quadrats in which a species occurs; **percentage cover** estimates the area covered; **density** is the number per unit area."),
        ("methode", "Sampling methods", "- **Random quadrats**: use random coordinates (not ‘throwing’) to avoid bias; a large sample gives a reliable mean.\n- **Belt/line transect**: quadrats at intervals along a line across a gradient (e.g. from a river bank to a field).\n- **Mark-release-recapture** for mobile animals: Lincoln index **N = (M × n) ÷ m**. Assumptions: marks do not harm or fade, marked animals mix randomly, no births, deaths or migration."),
        ("pieges", "Scaling up from the quadrat", "Mean number **per quadrat** is not density: divide by the quadrat area (e.g. 0.25 m²) to get density per m², then multiply by the total **area** to estimate the population. Use the same units throughout. Mark-recapture estimates are unreliable if marking changes the animal's behaviour or if there is migration; state these limitations when asked to evaluate."),
        ("retenir", "Population growth", "Populations show a **lag**, an **exponential (log) phase**, then level off near the **carrying capacity** (S-shaped curve). **Density-dependent** factors (competition, predation, disease, food) have more effect as density rises; **density-independent** factors (fire, flood, frost, drought) act regardless. Predator–prey populations often oscillate, with the predator peak following the prey peak.")],
       [("Worked example — population estimate", "Five random 0.25 m² quadrats in a field near Bambili contain 4, 7, 6, 9 and 6 plants. Estimate the number of plants in a 200 m² field.", ["Mean per quadrat = (4 + 7 + 6 + 9 + 6) ÷ 5 = %.1f." % mean_q, "Density = 6.4 ÷ 0.25 = %.1f plants per m²." % dens, "Population = 25.6 × 200 = %.0f." % est], "About 5120 plants"),
        ("Worked example — Lincoln index", "40 fish are caught, marked and released in a pond. Later a sample of 50 contains 8 marked fish. Estimate the population.", ["N = (M × n) ÷ m.", "M = 40, n = 50, m = 8.", "N = (40 × 50) ÷ 8 = %.0f." % lincoln], "250 fish")],
       [M("Why should quadrat positions be chosen using random numbers?", "To avoid bias in the choice of sample sites", ["To make counting quicker", "To count more plants", "To damage fewer plants"], "Random sampling gives a representative sample."),
        TF("The Lincoln index assumes that no marked animals die or migrate.", True, "Otherwise the proportion marked would change."),
        N("60 beetles are marked. A second sample of 30 contains 6 marked beetles. Estimate the population.", 300, "N = 60 × 30 ÷ 6 = 300.")],
       [O("Evaluate the mark-release-recapture technique for estimating the population of a small mammal.", "It is useful for mobile animals that cannot be counted directly, but it assumes marks do not harm or fade, marked animals mix randomly and there is no migration, birth or death. If marked animals become trap-shy or are caught more easily, the estimate is biased.", ["Use for mobile animals (1)", "Assumptions stated (1)", "A source of error (1)"]),
        M("A lag phase occurs at the start of a population's growth because", "few individuals are reproducing and organisms are adjusting", ["resources are exhausted", "predators are numerous", "carrying capacity is reached"], "Growth accelerates only when many individuals are reproducing.", tier="approfondissement")],
       P("In a school compound in Yaoundé, a student counts weeds in ten 1 m × 1 m quadrats: 3, 5, 0, 4, 2, 6, 1, 4, 3, 2. The compound's grassy area is 800 m².",
         [pn("Calculate the mean number per quadrat.", 3.0, "(3+5+0+4+2+6+1+4+3+2) = 30; 30 ÷ 10 = 3.0.", 0.01, None, 1),
          pn("Estimate the number of weeds in the 800 m² area.", 2400, "Density 3 per m²; 3 × 800 = 2400.", 1, None, 2),
          pn("What is the percentage frequency of the weed (quadrats in which it was present)?", 90, "9 of 10 quadrats had weeds: 90 %.", 0.1, "%", 1),
          po("Suggest one reason why the estimate may not be reliable.", "Ten quadrats is a small sample; distribution may be uneven, so more random quadrats are needed.", ["Valid limitation (1)"], 1)]),
       pop_sc, (fig, "S-shaped population growth levelling off at the carrying capacity.", "A sigmoid curve rising from low values and approaching a dashed horizontal line labelled carrying capacity."),
       notes=["Quadrat counts and fish numbers are invented practice data.", "Estimation formulas: density × area, Lincoln index. Both are standard."])
assert (3 + 5 + 0 + 4 + 2 + 6 + 1 + 4 + 3 + 2) == 30

# ---- 14 succession and energy flow
fig = bars([("Producers", 10000), ("Primary cons.", 1000), ("Secondary cons.", 100), ("Tertiary cons.", 10)], w=420, h=240)
ef_sc = [
    ("Net primary productivity (NPP) =", "gross primary productivity − respiration", ["GPP + respiration", "GPP × respiration", "respiration − GPP"], "NPP is the energy available to consumers."),
    ("Energy transfer between trophic levels is usually only about 10 % because", "much is lost in respiration, heat, waste and uneaten parts", ["energy is created", "producers eat consumers", "energy is stored as DNA"], "Most energy is lost as heat in respiration."),
    ("The first species to colonise bare rock are called", "pioneer species", ["climax species", "keystone species", "invasive species"], "Examples: lichens and mosses."),
    ("The final stable community of a succession is the", "climax community", ["pioneer community", "seral stage", "tundra"], "It is in balance with the climate."),
    ("A pyramid of energy is never inverted because", "energy decreases at each trophic level", ["numbers always decrease", "mass is constant", "producers are small"], "Energy is lost as heat at each transfer."),
]
npp = 15000 - 6000; eff = 1500 / 9000 * 100
assert npp == 9000 and abs(eff - 16.6667) < 1e-3
lesson(c5, "succession-energy", "Succession and energy flow",
       ["Describe primary and secondary succession and the idea of a climax community.", "Calculate GPP, NPP and the efficiency of energy transfer.", "Explain why food chains are short and pyramids of energy narrow upwards."],
       [("definition", "Succession", "**Succession** is the gradual change in the species of a community over time. **Primary succession** starts on bare ground with no soil (new lava, rock or sand): **pioneer species** (lichens, mosses) colonise, weathering and organic matter build soil, and larger plants follow until a stable **climax community** forms. **Secondary succession** starts where soil already exists (abandoned farmland, after a forest fire or logging) and is faster."),
        ("pieges", "Climax is not 'no change'", "A climax community is **relatively stable** and in equilibrium with the climate and soil, but it is not fixed: gaps appear when trees fall and small changes occur. Succession is caused by organisms changing their environment (more soil, shade, nutrients), which allows other species to replace earlier ones. Do not say that pioneers 'die out because of competition' without linking it to the environment they have modified. Local example: regrowth on lava from volcanic activity on Mount Cameroon."),
        ("formule", "Productivity and efficiency", "NPP = GPP − R, where GPP is the total energy fixed by photosynthesis and R is the energy used in the producers' respiration. Efficiency of transfer (%) = energy at a trophic level ÷ energy at the previous level × 100. Units are often kJ m⁻² year⁻¹."),
        ("retenir", "Energy flow", "Energy enters as light; only about 1–2 % of incident light energy is fixed by producers. At each transfer around 90 % of energy is lost (respiration, heat, undigested material, uneaten parts, excretion), leaving about 10 % (typically 5–20 %). So food chains rarely have more than four or five trophic levels, and a **pyramid of energy** is always narrower at the top. Eating at a lower trophic level (e.g. maize or cassava instead of meat) feeds more people per hectare.")],
       [("Worked example — NPP", "A cassava field fixes 15 000 kJ m⁻² year⁻¹ (GPP) and the plants use 6000 kJ m⁻² year⁻¹ in respiration. Calculate NPP.", ["NPP = GPP − R.", "= 15 000 − 6000.", "= %d kJ m⁻² year⁻¹." % npp], "9000 kJ m⁻² year⁻¹"),
        ("Worked example — transfer efficiency", "Primary consumers in the field gain 1500 kJ m⁻² year⁻¹ from the 9000 kJ m⁻² year⁻¹ NPP. Calculate the efficiency of transfer.", ["Efficiency = energy gained ÷ energy available × 100.", "= 1500 ÷ 9000 × 100.", "= %.1f %%." % eff], "16.7 %")],
       [M("Which of these is a pioneer species?", "Lichen on bare rock", ["Tall tropical hardwood tree", "Large mammal", "Mature forest herb"], "Pioneers can tolerate harsh conditions."),
        TF("Secondary succession starts from bare rock.", False, "That is primary succession; secondary starts on existing soil."),
        N("Producers fix 8000 kJ m⁻² year⁻¹ and herbivores gain 640. Calculate the transfer efficiency (%).", 8, "640 ÷ 8000 × 100 = 8 %.", unit="%")],
       [O("Explain why most food chains have no more than five trophic levels.", "At each transfer about 90 % of the energy is lost as heat, in respiration, waste and uneaten parts, so very little energy reaches the top consumers and cannot support another level.", ["Energy lost at each level (1)", "Ways of loss named (1)", "Too little energy at top (1)"]),
        M("Which statement best explains why abandoned farmland in Kumba recovers faster than bare volcanic rock?", "Soil and seeds are already present", ["There is no competition", "Pioneer lichens are needed", "The climate is different"], "Secondary succession starts with soil.", tier="approfondissement")],
       P("An ecosystem has the following energy values in kJ m⁻² year⁻¹: producers 12 000; primary consumers 1200; secondary consumers 150; tertiary consumers 12.",
         [pn("Efficiency of transfer from producers to primary consumers (%).", 10, "1200 ÷ 12 000 × 100 = 10 %.", 0.1, "%", 1),
          pn("Efficiency from primary to secondary consumers (%).", 12.5, "150 ÷ 1200 × 100 = 12.5 %.", 0.1, "%", 1),
          po("Explain why the efficiency to tertiary consumers is likely to be lower.", "Higher-level consumers are often larger, more active, warm-blooded and spend more energy hunting, so more energy is lost in respiration and as heat.", ["Valid reason (2)"], 2)]),
       ef_sc, (fig, "A pyramid of energy: energy at each trophic level (illustrative values).", "A bar chart with four bars decreasing by a factor of ten from producers (10 000) to tertiary consumers (10)."),
       notes=["Energy values are invented teaching numbers; 10 % is a rule of thumb.", "Mount Cameroon succession is mentioned in general terms only."])
assert 1200 / 12000 * 100 == 10 and 150 / 1200 * 100 == 12.5

# ---- 15 nutrient cycles
fig = hflow(["N₂\nin air", "NH₄⁺\nin soil", "NO₂⁻\nnitrite", "NO₃⁻\nnitrate"], w=460, size=12, gap=30, title="Nitrogen cycle: soil conversions")
fig["items"] += [T(112, 98, "fixation", 11, color="red"), T(230, 98, "Nitrosomonas", 11, color="red"), T(347, 98, "Nitrobacter", 11, color="red")]
fig["h"] = 125
nc_sc = [
    ("Rhizobium bacteria live in root nodules of legumes and", "fix atmospheric nitrogen into ammonium compounds", ["convert nitrate to N₂", "photosynthesise", "decompose cellulose"], "Nitrogen fixation, a mutualistic relationship."),
    ("Nitrification converts", "ammonium to nitrite and then nitrate", ["nitrate to nitrogen gas", "nitrogen gas to ammonia", "protein to urea"], "Nitrosomonas then Nitrobacter."),
    ("Denitrification by bacteria in waterlogged soil converts", "nitrate to nitrogen gas", ["nitrogen gas to nitrate", "ammonia to nitrite", "protein to amino acids"], "It reduces the nitrogen available to plants."),
    ("Which process returns carbon dioxide to the atmosphere?", "Respiration and combustion", ["Photosynthesis", "Nitrogen fixation", "Ammonification"], "Respiration by all organisms, decomposers and fossil-fuel burning."),
    ("Decomposers release nutrients from dead organisms by", "secreting enzymes and absorbing the products", ["photosynthesis", "fixing nitrogen only", "eating living plants"], "They carry out ammonification of proteins."),
]
lesson(c5, "nutrient-cycles", "Nutrient cycles: nitrogen and carbon",
       ["Describe the stages of the nitrogen cycle and the organisms involved.", "Describe the carbon cycle and the effect of human activities.", "Explain the role of crop rotation and fertilisers."],
       [("definition", "The nitrogen cycle", "Plants need nitrate (NO₃⁻) to make amino acids. **Nitrogen fixation**: N₂ → NH₄⁺ by free-living bacteria (*Azotobacter*) and by *Rhizobium* in **legume root nodules**; lightning also fixes some. **Ammonification**: decomposers convert proteins and urea in dead matter and waste to NH₄⁺. **Nitrification**: *Nitrosomonas* converts NH₄⁺ → NO₂⁻; *Nitrobacter* converts NO₂⁻ → NO₃⁻ (needs oxygen). **Denitrification**: some bacteria in waterlogged soils convert NO₃⁻ → N₂."),
        ("pieges", "Name the process, not just the change", "Exam answers must give **both** the process and the organism: e.g. 'nitrification by nitrifying bacteria in well-aerated soil'. Note that nitrifying bacteria need oxygen, while denitrifying bacteria work in **anaerobic** (waterlogged) soils, which is why poor drainage and flooding reduce soil fertility. Do not confuse nitrification (adds nitrate) with denitrification (removes it)."),
        ("retenir", "Carbon cycle", "Carbon enters living things by **photosynthesis**; it returns to the atmosphere by **respiration**, **decomposition** and **combustion** (wood, fossil fuels). Carbon is stored in biomass, soils, fossil fuels and as dissolved CO₂ and carbonates in the sea. Burning fossil fuels and **deforestation** raise atmospheric CO₂, an important cause of the enhanced greenhouse effect and climate change."),
        ("retenir", "Agriculture", "Farmers add **fertilisers** (nitrate, ammonium, phosphate) and use **crop rotation** with legumes (groundnut, beans, cowpea) to restore nitrate in the soil. Over-use of fertiliser causes **leaching** into streams, **eutrophication** (algal bloom, oxygen depletion, death of fish) and higher costs. Compost and mulch recycle nutrients through decomposers.")],
       [("Worked example — crop rotation", "A farmer in Bamenda plants maize on land that was previously groundnut. Explain why the maize may grow better.", ["Groundnut is a legume with Rhizobium bacteria in root nodules.", "These bacteria fix N₂ into ammonium compounds that are later nitrified to nitrate.", "More nitrate is available to the following maize crop."], "Legume fixed nitrogen increases soil nitrate"),
        ("Worked example — eutrophication", "Fertiliser runs from a farm into a lake. Explain the sequence leading to fish death.", ["Nitrate and phosphate cause rapid algal growth (algal bloom).", "Algae block light, plants below die; decomposers use up oxygen as they break down the dead matter.", "Low oxygen concentration kills fish."], "Eutrophication leading to oxygen depletion")],
       [M("Which bacteria convert nitrite to nitrate?", "Nitrobacter", ["Nitrosomonas", "Rhizobium", "Azotobacter"], "Nitrobacter oxidises nitrite to nitrate."),
        TF("Denitrification increases the nitrate available to plants.", False, "It converts nitrate to nitrogen gas, reducing fertility."),
        M("Which crop could be used in rotation to add nitrogen to the soil?", "Groundnut", ["Maize", "Cassava", "Plantain"], "Legumes have nitrogen-fixing nodules.")],
       [O("Explain why a waterlogged soil is often low in nitrate.", "In oxygen-poor soil denitrifying bacteria convert nitrate to nitrogen gas, and nitrifying bacteria (which need oxygen) are inhibited, so little nitrate forms and nitrate is lost.", ["Denitrification (1)", "Nitrifying bacteria need oxygen (1)", "Nitrate lost / not formed (1)"]),
        M("A large area of forest is cleared and burnt. Which change in the carbon cycle occurs?", "Less CO₂ is removed by photosynthesis and more is released by combustion", ["More photosynthesis occurs", "No carbon enters the atmosphere", "Carbon is stored in the soil only"], "Both processes increase atmospheric CO₂.", tier="approfondissement")],
       P("A farmer applies excess nitrate fertiliser to a field near a stream in Buea.",
         [pm("Which process carries nitrate to the stream?", "Leaching", ["Fixation", "Denitrification", "Photolysis"], "Soluble nitrate is washed through the soil.", 1),
          pm("The resulting rapid growth of algae is called", "an algal bloom (eutrophication)", ["succession", "denitrification", "photosynthesis only"], "Eutrophication.", 1),
          po("Explain how this process reduces oxygen concentration in the stream.", "The dead algae are decomposed by aerobic bacteria that use up the oxygen; shading prevents photosynthesis by submerged plants.", ["Decomposers use oxygen (1)", "Less photosynthesis below (1)"], 2)]),
       nc_sc, (fig, "Main conversions of nitrogen in the soil, with the organisms involved.", "Four boxes in a row: nitrogen gas, ammonium, nitrite and nitrate with the processes and bacteria beneath arrows."),
       notes=["Bacterial genus names are standard textbook examples; check which are named in the syllabus.", "Effects on climate are stated qualitatively; no figures are given."])

# =====================================================================  CH 6  BIOTECHNOLOGY
c6 = p.chapter("biotech", "Biotechnology and genetic engineering", programRef="GCE A Level Biology — Genetic engineering and biotechnology (to be checked)")

# ---- 16 PCR and gel electrophoresis
fig = hflow(["Denature\n95 °C", "Anneal primers\n50–65 °C", "Extend\n72 °C"], w=440, size=12, gap=44, title="One PCR cycle")
copies = 2 ** 30; assert copies == 1073741824
pcr_sc = [
    ("In PCR the DNA strands are separated by", "heating to about 95 °C", ["cooling to 4 °C", "adding helicase", "adding ligase"], "Hydrogen bonds between bases break."),
    ("Taq polymerase is used because it", "is heat-stable (from a thermophilic bacterium)", ["works only at 0 °C", "cuts DNA", "makes RNA"], "It is not denatured at 95 °C."),
    ("In gel electrophoresis DNA moves towards the", "positive electrode (anode)", ["negative electrode", "wells", "middle of the gel"], "DNA is negatively charged because of its phosphate groups."),
    ("Shorter DNA fragments travel", "further through the gel", ["less far", "the same distance", "towards the wells"], "They move more easily through the pores."),
    ("A DNA profile (genetic fingerprint) uses", "short tandem repeats (variable regions) that differ between individuals", ["the whole genome", "the same pattern in all people", "amino acids"], "The lengths of repeats vary."),
]
lesson(c6, "pcr-electrophoresis", "PCR, gel electrophoresis and DNA profiling",
       ["Describe the stages of the polymerase chain reaction.", "Explain how DNA fragments are separated by gel electrophoresis.", "Describe uses of DNA profiling and calculate the number of copies after n cycles."],
       [("definition", "PCR", "The **polymerase chain reaction** copies a short target DNA sequence in a machine called a thermocycler. Each cycle has three steps: **denaturation** (~95 °C: strands separate), **annealing** (~50–65 °C: short **primers** bind to complementary sequences at the ends of the target) and **extension** (~72 °C: **Taq polymerase** adds nucleotides). The reaction mixture contains template DNA, primers, free nucleotides and Taq polymerase. The number of copies doubles each cycle."),
        ("formule", "Number of copies", "Starting with one DNA molecule, after n cycles the number of copies is 2ⁿ (in theory). For example, 10 cycles give 1024 copies; 30 cycles give about 10⁹ copies. In practice the efficiency is slightly lower."),
        ("retenir", "Gel electrophoresis", "DNA is cut into fragments by **restriction enzymes**. Samples are loaded into wells in an **agarose gel** in a buffer and an electric current is applied. DNA is **negatively charged** (phosphate groups), so it moves towards the **positive electrode**. **Shorter fragments move further** because they pass through the gel pores more easily. DNA is made visible with a stain or labelled probe, and sizes are estimated by comparing with a **ladder** of known sizes."),
        ("pieges", "Uses and limits of DNA profiling", "**DNA profiling** compares patterns of variable repeat sequences (short tandem repeats). Uses: forensic identification, paternity tests, tracing disease-causing alleles, identifying species. A profile **matches** someone only with a probability, not with certainty; contamination of samples by even small amounts of foreign DNA can ruin PCR. Ethical issues include privacy, storage of data and consent.")],
       [("Worked example — copies", "How many DNA molecules are produced from one molecule after 10 cycles of PCR?", ["Each cycle doubles the number.", "After n cycles: 2ⁿ.", "2¹⁰ = %d." % 2 ** 10], "1024"),
        ("Worked example — reading a gel", "Fragments of 1500, 800 and 300 base pairs are separated by electrophoresis. Which travels furthest, and where are the wells?", ["Smaller fragments pass more easily through the gel.", "The 300 bp fragment travels furthest and the 1500 bp the shortest distance.", "The wells are at the negative end, where the samples are loaded (DNA moves away towards the positive electrode)."], "300 bp travels furthest")],
       [M("What is the role of primers in PCR?", "They bind to the ends of the target sequence and start synthesis", ["They cut the DNA", "They provide energy", "They bind to polymerase only"], "DNA polymerase needs a primer to begin."),
        TF("The numbers of DNA copies in PCR increase by a factor of 2 each cycle.", True, "Doubling per cycle in ideal conditions."),
        N("How many copies are present after 8 cycles starting from one molecule?", 2 ** 8, "2⁸ = 256.")],
       [O("Explain why Taq polymerase is used in PCR rather than human DNA polymerase.", "Taq polymerase comes from a thermophilic bacterium and is not denatured at 95 °C, so it can be used through repeated cycles without adding fresh enzyme; human DNA polymerase would be denatured.", ["Heat stable (1)", "Not denatured at 95 °C (1)", "Reused through cycles (1)"]),
        M("A DNA sample is cut by a restriction enzyme that recognises three sites on a linear DNA molecule. The number of fragments produced is", "4", ["3", "2", "6"], "A linear molecule cut n times gives n + 1 fragments.", tier="approfondissement")],
       P("A forensic laboratory compares DNA from a sample with two suspects.",
         [pn("A sample has one DNA molecule. How many copies after 20 cycles?", 2 ** 20, "2²⁰ = 1 048 576.", 0, None, 1),
          pm("Why does the DNA move towards the positive electrode?", "It carries negative charges on phosphate groups", ["It is positively charged", "It is uncharged", "It is attracted to the wells"], "Phosphate groups give DNA a negative charge.", 1),
          po("Give two precautions to prevent wrong results.", "Prevent contamination with foreign DNA (clean equipment, gloves) and use controls and replicate samples.", ["Contamination avoided (1)", "Controls/replicates (1)"], 2)]),
       pcr_sc, (fig, "One PCR cycle: denaturation, annealing and extension at three temperatures.", "Three boxes in a row labelled denature 95 °C, anneal primers 50-65 °C and extend 72 °C."),
       notes=["Annealing temperature ranges depend on the primers; the textbook range is given.", "The number of copies 2ⁿ is the theoretical maximum."])
assert 2 ** 20 == 1048576

# ---- 17 genetic engineering and GMOs
fig = vflow(["Isolate the gene (restriction enzyme)", "Cut the plasmid with the same enzyme", "Join with DNA ligase: recombinant plasmid", "Insert into bacteria (transformation)", "Select with a marker gene and grow"], w=430, size=12, bw=320, title="Making recombinant DNA")
ge_sc = [
    ("Restriction enzymes cut DNA at", "specific base sequences (recognition sites)", ["random positions", "the centromere", "any start codon"], "e.g. EcoRI cuts at GAATTC."),
    ("DNA ligase", "joins DNA fragments by forming phosphodiester bonds", ["cuts DNA", "unwinds DNA", "copies RNA"], "It seals sticky ends."),
    ("A plasmid in genetic engineering is used as a", "vector to carry the gene into the host", ["restriction enzyme", "source of energy", "protein"], "Small circular DNA of bacteria."),
    ("Bt maize contains a gene from Bacillus thuringiensis that", "produces a protein toxic to certain insect pests", ["makes the plant glow", "makes it drought resistant", "fixes nitrogen"], "Reduces insect damage and insecticide use."),
    ("A marker gene (e.g. antibiotic resistance) is used to", "identify the cells that have taken up the plasmid", ["cut the plasmid", "repair DNA", "make protein"], "Only transformed cells survive on the medium."),
]
lesson(c6, "genetic-engineering", "Recombinant DNA, GMOs and ethics",
       ["Describe how a gene is transferred into a bacterium using a plasmid vector.", "Describe examples of genetically modified organisms and their benefits and risks.", "Evaluate ethical and social issues."],
       [("definition", "Recombinant DNA technology", "**Genetic engineering** transfers a gene from one organism into another. Steps: (1) obtain the gene (cut with a **restriction enzyme** that leaves **sticky ends**, or make cDNA from mRNA using reverse transcriptase); (2) cut a **plasmid vector** with the same enzyme; (3) join them with **DNA ligase** to make a **recombinant plasmid**; (4) introduce it into host cells (**transformation**); (5) identify transformed cells using **marker genes** (e.g. antibiotic resistance or fluorescence) and culture them."),
        ("retenir", "Examples", "- **Human insulin** made by bacteria (the gene is inserted into a plasmid); no animal source and no allergy to animal insulin.\n- **Bt crops** (e.g. maize, cotton) express an insect-killing protein.\n- **Herbicide-tolerant** crops.\n- **Golden rice** with extra provitamin A (a proposed solution to vitamin A deficiency).\n- **Gene therapy** (replacement of a faulty allele) is still developing and is limited to certain conditions."),
        ("pieges", "Balanced evaluation", "Evaluate with **benefits and risks**: higher yields, reduced pesticide use, improved nutrition, medicines; versus possible harm to non-target organisms, gene flow to wild relatives, resistance in pests, cost and patents controlling seeds, and consumer concerns. Do not simply state that GM food is 'unsafe' or 'safe'; give evidence and mention regulation, testing and labelling. Policies differ between countries; check current Cameroonian regulation before teaching it as fact."),
        ("retenir", "Ethical issues", "Questions include: animal welfare in transgenic animals, ownership (patents) of genes and seeds, access for small farmers, effects on biodiversity, privacy of genetic information, and whether it is acceptable to alter the human germline. A good answer presents more than one viewpoint and uses scientific evidence.")],
       [("Worked example — fragments from a plasmid", "A circular plasmid of 5000 base pairs is cut by one restriction enzyme at 2 sites into two fragments, one of 1800 base pairs. What is the size of the other?", ["A circular molecule cut at 2 sites gives 2 fragments.", "The fragments must add to the plasmid length: 5000 base pairs.", "5000 − 1800 = %d base pairs." % (5000 - 1800)], "3200 base pairs"),
        ("Worked example — why the same enzyme?", "Explain why the gene and the plasmid are cut with the same restriction enzyme.", ["The same enzyme cuts both at the same recognition sequence.", "This leaves complementary sticky ends on both molecules.", "The sticky ends anneal by hydrogen bonds and are sealed by ligase."], "Complementary sticky ends")],
       [M("Which enzyme cuts DNA at a specific sequence?", "Restriction enzyme", ["DNA ligase", "DNA polymerase", "RNA polymerase"], "Restriction endonuclease."),
        TF("A plasmid is a circular DNA molecule found in bacteria.", True, "Plasmids are often used as vectors."),
        N("A circular plasmid is cut at 3 sites by one restriction enzyme. How many fragments are produced?", 3, "A circular molecule cut at n sites gives n fragments.")],
       [O("Discuss the use of Bt maize by farmers in Cameroon.", "Benefits: it kills certain insect larvae, so yield rises and less insecticide is needed. Risks: possible effects on non-target insects, evolution of resistance in pests, seed cost and dependence on seed companies, and gene flow to related plants. Decisions should be based on evidence and national regulations.", ["Benefit (1)", "Risk (1)", "Balanced conclusion (1)"]),
        M("Why is human insulin produced by genetically modified bacteria preferred to pig insulin?", "It is identical to human insulin, so fewer immune reactions occur", ["It is cheaper in every case", "Bacteria produce it faster than any other source", "It lowers glucagon"], "The protein has the human amino-acid sequence.", tier="approfondissement")],
       P("A research team in Yaoundé plans to put a gene for virus resistance into cassava.",
         [pm("Which molecule is cut by the restriction enzyme to obtain a vector?", "Plasmid DNA", ["Cellulose", "Insulin", "ATP"], "The plasmid carries the gene.", 1),
          pm("Which enzyme seals the gene into the vector?", "DNA ligase", ["Restriction enzyme", "Helicase", "Amylase"], "It makes phosphodiester bonds.", 1),
          po("Suggest one benefit and one risk of the GM cassava.", "Benefit: protects yield and food supply from disease. Risk: gene flow to wild relatives or unforeseen effects on other organisms or on seed ownership.", ["Benefit (1)", "Risk (1)"], 2)]),
       ge_sc, (fig, "The main steps in making and selecting recombinant bacteria.", "Five boxes in a vertical chain from isolating the gene through ligase and transformation to selection with a marker gene."),
       notes=["GMO regulation in Cameroon is not stated; check current national rules and avoid presenting any policy position.", "Golden rice and Bt crops are widely used textbook examples."])
assert 5000 - 1800 == 3200

# =====================================================================  CH 7  REPRODUCTION AND PLANT HORMONES
c7 = p.chapter("reproduction", "Reproduction, development and plant hormones", programRef="GCE A Level Biology — Reproduction in humans; plant growth substances (to be checked)")

# ---- 18 human reproduction
fig = timeline(1, 28, events=[(14, "ovulation")], periods=[(1, 5, "menses", "red"), (6, 13, "follicular phase", "orange"), (15, 28, "luteal phase", "green")], w=460, h=200)
rp_sc = [
    ("Which hormone stimulates the development of follicles in the ovary?", "FSH", ["Progesterone", "Insulin", "Testosterone"], "Follicle-stimulating hormone from the anterior pituitary."),
    ("Ovulation is triggered by a surge in", "LH", ["FSH", "progesterone", "oestrogen only"], "LH peaks about a day before ovulation."),
    ("The corpus luteum secretes", "progesterone", ["FSH", "LH", "insulin"], "It maintains the endometrium."),
    ("Menstruation occurs when", "progesterone concentration falls", ["progesterone rises", "FSH rises", "implantation occurs"], "The endometrium breaks down."),
    ("The hormone detected by pregnancy tests is", "hCG", ["FSH", "LH", "oestrogen"], "Produced by the early embryo (trophoblast)."),
]
lesson(c7, "reproduction", "Human reproduction and the menstrual cycle",
       ["Describe gametogenesis in the testis and ovary.", "Explain the hormonal control of the menstrual cycle.", "Describe fertilisation, implantation and the roles of the placenta."],
       [("definition", "Gametes", "In the testes, **spermatogenesis** in the seminiferous tubules produces many small, motile **sperm** (acrosome with enzymes, mitochondria in the mid-piece, flagellum). In the ovary, **oogenesis** gives one large **secondary oocyte** per cycle (with a few small polar bodies) and the oocyte completes meiosis II only after sperm entry. Testosterone from the interstitial cells maintains sperm production and male characteristics."),
        ("retenir", "The menstrual cycle", "**FSH** (anterior pituitary) stimulates follicle growth; the follicle secretes **oestrogen**, which repairs the **endometrium** and, at high concentration, stimulates the **LH surge**. LH causes **ovulation** (about day 14 in a 28-day cycle) and the follicle becomes the **corpus luteum**, which secretes **progesterone** (maintains the endometrium; inhibits FSH and LH by negative feedback). If no pregnancy occurs the corpus luteum degenerates, progesterone falls and **menstruation** starts."),
        ("pieges", "Timing of ovulation", "Ovulation occurs about **14 days before the next menstruation**, not necessarily on day 14. In a cycle of length L days it is at about day L − 14 (a 30-day cycle gives day 16). Oestrogen has both negative feedback (low concentrations inhibit FSH) and **positive feedback** (high concentration stimulates the LH surge). Do not confuse FSH and LH, or progesterone (luteal) with oestrogen (follicular)."),
        ("retenir", "Fertilisation and pregnancy", "A sperm penetrates the oocyte after the **acrosome reaction**; the **cortical reaction** hardens the zona pellucida to prevent entry of other sperm. The zygote divides to form a **blastocyst** that **implants** in the endometrium. The embryo secretes **hCG**, maintaining the corpus luteum; later the **placenta** secretes progesterone and oestrogen. The placenta exchanges gases, nutrients and wastes by diffusion, facilitated diffusion and active transport; the mother's and fetus's blood do not mix.")],
       [("Worked example — day of ovulation", "A woman has a regular cycle of 30 days. On which day of the cycle would ovulation be expected?", ["Ovulation is about 14 days before the next period starts.", "Day = 30 − 14.", "= %d." % (30 - 14)], "About day 16"),
        ("Worked example — gestation", "Pregnancy lasts about 40 weeks from the first day of the last period. How many days is this?", ["1 week = 7 days.", "40 × 7.", "= %d days." % (40 * 7)], "About 280 days")],
       [M("Which structure produces progesterone after ovulation?", "Corpus luteum", ["Follicle before ovulation", "Placenta only", "Pituitary"], "The corpus luteum is the remains of the follicle."),
        TF("The LH surge occurs after ovulation.", False, "It occurs before and triggers ovulation."),
        N("A woman has a 26-day cycle. On which day of the cycle is ovulation expected (about 14 days before the next menstruation)?", 12, "26 − 14 = 12.")],
       [O("Explain how progesterone and oestrogen prevent further ovulation during pregnancy.", "High levels of progesterone and oestrogen from the corpus luteum and then the placenta inhibit the release of FSH and LH by negative feedback, so no new follicles develop and no ovulation occurs.", ["High progesterone/oestrogen (1)", "Negative feedback on FSH and LH (1)", "No follicle development (1)"]),
        M("Why does the early embryo secrete hCG?", "To maintain the corpus luteum so that progesterone keeps the endometrium", ["To start menstruation", "To prevent implantation", "To trigger ovulation"], "hCG acts like LH.", tier="approfondissement")],
       P("A teaching diagram shows blood hormone levels over a 28-day cycle.",
         [pm("Which hormone has a peak just before ovulation?", "LH", ["Progesterone", "Insulin", "ADH"], "The LH surge triggers ovulation.", 1),
          pm("Which hormone is high in the second half of the cycle?", "Progesterone", ["FSH", "LH", "Thyroxine"], "Secreted by the corpus luteum.", 1),
          po("Explain why the endometrium breaks down at the end of the cycle if no fertilisation occurs.", "The corpus luteum degenerates, progesterone concentration falls and the endometrium is no longer maintained.", ["Corpus luteum degenerates (1)", "Progesterone falls (1)"], 2)]),
       rp_sc, (fig, "Phases of the menstrual cycle in a 28-day cycle, with ovulation at about day 14.", "A timeline from day 1 to 28 showing menses, the follicular phase, ovulation near day 14 and the luteal phase."),
       notes=["Contraception and sexual health are deliberately not covered; if required by the syllabus, the content must be written with a health professional.", "Hormone levels are described qualitatively. Review all reproductive-health content with a health worker."])

# ---- 19 plant hormones
fig = shapes([T(215, 16, "Phototropism: auxin moves to the shaded side", 14, bold=True),
              RECT(190, 70, 36, 130, fill="lightgreen", stroke="green"), RECT(190, 70, 18, 130, fill="lightorange", stroke="orange", width=1),
              LINE(30, 85, 120, 100, color="orange", width=4, arrow="end"), LINE(30, 115, 120, 125, color="orange", width=4, arrow="end"),
              T(60, 70, "light", 12, color="orange"),
              T(330, 120, "more auxin on the", 12), T(330, 136, "shaded side", 12),
              LINE(260, 130, 232, 130, arrow="end", width=1.5, color="grey"),
              T(215, 220, "→ faster elongation on shaded side → bends towards light", 12)], 460, 235)
ph2_sc = [
    ("Auxin (IAA) promotes", "cell elongation in shoots", ["cell division only", "fruit ripening only", "stomatal closure"], "It acidifies cell walls and loosens them."),
    ("Gibberellins stimulate", "stem elongation and germination (amylase production)", ["stomatal closure", "leaf fall", "root hair death"], "Amylase mobilises starch in seeds."),
    ("Which hormone causes stomata to close in water stress?", "Abscisic acid (ABA)", ["Gibberellin", "Auxin", "Cytokinin"], "ABA triggers K⁺ loss from guard cells."),
    ("Ethylene is used commercially to", "ripen fruits such as bananas", ["lengthen stems", "kill weeds in every case", "stimulate root hairs"], "It is a gas."),
    ("In phototropism the shoot bends towards light because", "auxin concentration is higher on the shaded side", ["auxin is destroyed on the lit side only", "gibberellin is made by chloroplasts", "the lit side grows faster"], "Greater elongation on the shaded side."),
]
growth = (18.0 - 12.0) / 12.0 * 100; assert growth == 50
lesson(c7, "plant-hormones", "Plant growth substances",
       ["State the sources and effects of auxins, gibberellins, cytokinins, abscisic acid and ethylene.", "Explain phototropism and apical dominance.", "Describe uses in agriculture and interpret growth data."],
       [("definition", "Plant growth regulators", "Plants coordinate growth with **growth substances** (hormones) made in one region and acting in another at very low concentrations. Responses are slower than animal nervous responses. Different tissues respond differently to the same hormone (e.g. a concentration that stimulates shoot growth may inhibit root growth)."),
        ("retenir", "Effects of the main hormones", "- **Auxin (IAA)**: cell elongation, phototropism and gravitropism, **apical dominance**, root initiation.\n- **Gibberellins**: stem elongation, seed germination (amylase production in the aleurone layer), flowering.\n- **Cytokinins**: cell division, delay of leaf senescence, release of lateral buds.\n- **Abscisic acid**: stomatal closure in water stress, seed dormancy.\n- **Ethylene**: fruit ripening, leaf and fruit abscission."),
        ("pieges", "Mechanism of phototropism", "Light causes **redistribution** of auxin to the shaded side of the shoot (by lateral transport); auxin makes the cell walls more extensible by promoting proton pumping (acid growth), so cells on the shaded side **elongate more** and the shoot **bends towards the light**. Do not say that light destroys auxin or that the tip 'senses food'. In roots, high auxin concentrations inhibit elongation, which explains positive gravitropism."),
        ("retenir", "Uses", "**Auxin rooting powder** for cuttings; synthetic auxins as selective herbicides (broad-leaved weeds); **ethylene** to ripen bananas or mangoes during transport; **gibberellins** to increase grape size and to malt barley; **cytokinins** in tissue culture. Always follow safety rules for chemicals; pesticide use must follow local regulations.")],
       [("Worked example — percentage growth", "Coleoptiles treated with auxin grow from 12.0 mm to 18.0 mm in 24 hours. Calculate the percentage increase in length.", ["Increase = 18.0 − 12.0 = 6.0 mm.", "Percentage = 6.0 ÷ 12.0 × 100.", "= %.0f %%." % growth], "50 %"),
        ("Worked example — apical dominance", "A gardener in Buea removes the tip of a tea shoot and side shoots then grow. Explain.", ["The apical bud makes auxin that inhibits lateral buds (apical dominance).", "Removing the tip removes the source of auxin.", "Lateral buds are released and grow (helped by cytokinins)."], "Loss of auxin releases lateral buds")],
       [M("Which hormone is a gas?", "Ethylene", ["Auxin", "Gibberellin", "Cytokinin"], "Ethylene diffuses through air."),
        TF("Abscisic acid promotes stomatal closure.", True, "It conserves water."),
        N("A stem grows from 20 mm to 26 mm after treatment with gibberellin. What is the percentage increase?", 30, "6 ÷ 20 × 100 = 30 %.", unit="%")],
       [O("Describe how auxin causes a shoot to bend towards a light source.", "Auxin produced in the shoot tip moves to the shaded side; on that side it stimulates cell elongation more than on the lit side, so the shaded side grows faster and the shoot curves towards the light.", ["Auxin redistributed to shaded side (1)", "More cell elongation there (1)", "Bends towards light (1)"]),
        M("A farmer sprays unripe bananas with ethylene. The expected effect is", "faster ripening", ["slower ripening", "root growth", "stomatal closure"], "Ethylene stimulates ripening enzymes.", tier="approfondissement")],
       P("Seedlings of maize were grown with the tip of the coleoptile covered by foil in one group and uncovered in another, with light from one side.",
         [pm("In which group would bending be expected?", "The uncovered group", ["The covered group", "Both equally", "Neither"], "The tip senses the light and redistributes auxin.", 1),
          pn("Uncovered coleoptiles bend by 20° in one hour. What bending in degrees is expected in 3 hours if the rate is constant?", 60, "20 × 3 = 60°.", 0, "°", 1),
          po("Explain why the covered group does not bend.", "The tip, which detects the light and makes auxin, is shielded, so auxin is not redistributed and growth is even.", ["Tip shielded (1)", "No redistribution (1)"], 2)]),
       ph2_sc, (fig, "Phototropism: unequal distribution of auxin makes a shoot bend towards the light.", "A shoot with arrows of light from the left and a label showing more auxin on the shaded side."),
       notes=["Behaviour (innate/learned, taxes and kineses) is not covered in this pack; check whether it is on the GCE syllabus.", "Acid-growth detail may exceed the syllabus."])

# =====================================================================  CH 8  PRACTICAL SKILLS AND STATISTICS
c8 = p.chapter("statistics", "Practical skills and statistics", programRef="GCE A Level Biology — Practical assessment; handling data and statistics (to be checked)")

# ---- 20 chi-squared and Spearman
obs = [560, 170, 190, 80]; tot = sum(obs); expv = [tot * k / 16 for k in (9, 3, 3, 1)]
chi4 = sum((o - e) ** 2 / e for o, e in zip(obs, expv)); assert abs(chi4 - 6.5778) < 1e-3
chi2 = sum((o - e) ** 2 / e for o, e in zip([432, 168], [450, 150])); assert abs(chi2 - 2.88) < 1e-9
xr = list(range(1, 11)); yr = [2, 1, 4, 3, 6, 5, 8, 10, 7, 9]
d2 = sum((a - b) ** 2 for a, b in zip(xr, yr)); rs = 1 - 6 * d2 / (10 * 99); assert d2 == 16 and abs(rs - 0.9030303) < 1e-6
fig = tabfig(["Degrees of freedom", "1", "2", "3", "4"], [["Critical value (5 %)", "3.84", "5.99", "7.81", "9.49"]], w=440, colw=[170, 67, 67, 67, 67], title="Chi-squared critical values at p = 0.05")
st_sc = [
    ("The null hypothesis in a chi-squared test states that", "there is no significant difference between observed and expected results", ["the results are all equal", "the hypothesis is proved", "there are errors"], "Any difference is due to chance."),
    ("For a chi-squared test with 4 phenotype classes, the degrees of freedom are", "3", ["4", "2", "5"], "Classes − 1."),
    ("If the calculated χ² is smaller than the critical value at p = 0.05, we", "fail to reject the null hypothesis", ["reject the null hypothesis", "prove the null hypothesis", "must repeat the cross"], "The difference could be due to chance."),
    ("Spearman's rank correlation coefficient tests for", "association between two ranked variables", ["difference between two means", "fit to a ratio", "variation within a sample"], "It measures monotonic association."),
    ("A Spearman coefficient close to −1 indicates", "a strong negative correlation", ["no correlation", "a strong positive correlation", "a causal relationship"], "As one rises the other falls; correlation is not causation."),
]
lesson(c8, "chi-spearman", "Statistics I: chi-squared and Spearman's rank correlation",
       ["State null hypotheses and choose between chi-squared and Spearman's tests.", "Calculate χ² and rs and compare with critical values.", "Draw valid conclusions and state the limitations."],
       [("formule", "Chi-squared", "χ² = Σ (O − E)² ÷ E, where O = observed and E = expected frequency. **Degrees of freedom** = number of classes − 1. Compare χ² with the critical value at **p = 0.05**: if χ² is **less** than the critical value, the difference between observed and expected is **not significant** (chance explains it). Use frequencies, not percentages, with an adequate sample (expected values ≥ 5)."),
        ("formule", "Spearman's rank", "rs = 1 − 6Σd² ÷ n(n² − 1), where d is the difference between the ranks of each pair and n is the number of pairs. rs ranges from −1 (perfect negative) through 0 (none) to +1 (perfect positive). If |rs| is **at least** the critical value for n, the correlation is significant. Rank each variable separately; for ties, use the mean of the ranks."),
        ("pieges", "What a test can and cannot show", "State the **null hypothesis** first, calculate the statistic, compare with the critical value (p = 0.05), then conclude in words about the biology. 'Not significant' does **not** prove that the ratio is exactly 9 : 3 : 3 : 1, only that the results are consistent with it. A significant Spearman correlation does **not** show that one variable causes the other. Use χ² only for counts in categories, not for measurements such as height."),
        ("retenir", "Critical values used here", "Chi-squared at p = 0.05: df 1 = 3.84; df 2 = 5.99; df 3 = 7.81; df 4 = 9.49. Spearman two-tailed at p = 0.05: n = 8, 0.738; n = 10, 0.648. These are standard table values, but check the table printed in the examination booklet used with your syllabus.")],
       [("Worked example — chi-squared for a dihybrid cross", "A dihybrid cross produces 1000 offspring: 560, 170, 190 and 80 in the four classes. Test the 9 : 3 : 3 : 1 ratio.", ["Expected: 562.5, 187.5, 187.5, 62.5.", "χ² = 2.5²÷562.5 + 17.5²÷187.5 + 2.5²÷187.5 + 17.5²÷62.5 = %.2f." % chi4, "df = 3; critical value 7.81. Since %.2f < 7.81 the difference is not significant." % chi4], "χ² = 6.58 < 7.81: consistent with 9 : 3 : 3 : 1"),
        ("Worked example — Spearman's rank", "At 10 sites, rainfall rank (1–10) and plant height rank are: heights 2,1,4,3,6,5,8,10,7,9 against ranks 1–10. Calculate rs. (Σd² = 16.)", ["rs = 1 − 6Σd² ÷ n(n² − 1) with n = 10, so n(n² − 1) = 990.", "rs = 1 − 6 × 16 ÷ 990 = 1 − 0.097.", "rs = %.2f > 0.648, so the positive correlation is significant." % rs], "rs = 0.90")],
       [M("Chi-squared is used with data that are", "counts in categories", ["continuous measurements", "ranks", "percentages only"], "It compares observed and expected frequencies."),
        TF("If calculated χ² is greater than the critical value, the null hypothesis is rejected.", True, "The difference is significant."),
        N("A cross gives 432 tall and 168 short plants. Expected 3 : 1 gives 450 and 150. Calculate χ² (2 d.p.).", chi2, "(18²÷450) + (18²÷150) = 0.72 + 2.16 = 2.88.", tol=0.01)],
       [O("A student concludes 'χ² is below the critical value, so the ratio 3 : 1 is proved'. Criticise this conclusion.", "The test only shows that the observed results are consistent with 3 : 1 (the difference could be due to chance); it cannot prove the ratio. A larger sample or other evidence is needed to be more confident.", ["Not proof (1)", "Consistent with / chance (1)", "More data needed (1)"]),
        M("A Spearman test gives rs = 0.55 for n = 10 (critical value 0.648). The conclusion is", "no significant correlation at p = 0.05", ["a significant positive correlation", "a significant negative correlation", "the variables are unrelated"], "0.55 is less than 0.648.", tier="approfondissement")],
       P("Genetics practical: in a cross of maize, the expected ratio of purple to yellow kernels is 3 : 1. The student counts 330 purple and 120 yellow kernels.",
         [pn("Calculate the expected numbers of purple and yellow (give the expected purple).", 337.5, "450 total; ¾ = 337.5; expected yellow = 112.5.", 0.1, None, 1),
          pn("Calculate χ² (2 d.p.).", round((330 - 337.5) ** 2 / 337.5 + (120 - 112.5) ** 2 / 112.5, 2), "7.5² ÷ 337.5 + 7.5² ÷ 112.5 = 0.1667 + 0.5 = 0.67.", 0.01, None, 2),
          pm("Compare with the critical value 3.84 (df = 1).", "Not significant: consistent with 3 : 1", ["Significant: ratio rejected", "Cannot be compared", "Significant: ratio proved"], "0.67 < 3.84.", 1),
          po("State the null hypothesis.", "There is no significant difference between the observed and expected numbers (3 : 1 ratio).", ["Valid null hypothesis (1)"], 1)]),
       st_sc, (fig, "Critical values of chi-squared at p = 0.05 for 1 to 4 degrees of freedom.", "A table with degrees of freedom 1 to 4 and critical values 3.84, 5.99, 7.81 and 9.49."),
       notes=["Critical values are standard but must be checked against the GCE Board's statistical tables; one- or two-tailed Spearman use may differ.", "Data are invented for practice."])
assert round((330 - 337.5) ** 2 / 337.5 + (120 - 112.5) ** 2 / 112.5, 2) == 0.67

# ---- 21 standard deviation and t-test
import math as m2
sun = [22, 25, 24, 27, 23, 26]; shade = [29, 31, 28, 33, 30, 29]
def msd(v):
    mu = sum(v) / len(v); return mu, m2.sqrt(sum((i - mu) ** 2 for i in v) / (len(v) - 1))
(m1, s1), (m2_, s2) = msd(sun), msd(shade)
tval = (m2_ - m1) / m2.sqrt(s1 ** 2 / 6 + s2 ** 2 / 6)
assert abs(s1 - 1.8708) < 1e-3 and abs(tval - 5.2048) < 1e-3
fig = bars([("Sun leaves (mean 24.5)", 24.5, "orange"), ("Shade leaves (mean 30.0)", 30.0, "green")], w=400, h=240)
tt_sc = [
    ("Standard deviation measures", "the spread of data around the mean", ["the mean", "the range only", "the sample size"], "A small SD means values are close to the mean."),
    ("For a sample, SD is calculated using the divisor", "n − 1", ["n", "n + 1", "n²"], "Corrects bias in a sample estimate."),
    ("The t-test is used to compare", "the means of two samples", ["frequencies in categories", "ranks of two variables", "the modes"], "Data should be continuous and roughly normally distributed."),
    ("For two samples of 6 each, the degrees of freedom for the t-test (n₁ + n₂ − 2) are", "10", ["12", "11", "5"], "6 + 6 − 2 = 10."),
    ("If calculated t is greater than the critical value at p = 0.05", "the difference between the means is significant", ["the samples are identical", "the data are wrong", "there is no difference"], "Reject the null hypothesis."),
]
Lm = lesson(c8, "ttest-sd", "Statistics II: standard deviation and the t-test",
       ["Calculate the mean and standard deviation of a sample.", "Compare two means with the t-test and state the conclusion.", "Evaluate experimental design: sample size, controls, reliability."],
       [("formule", "Standard deviation", "Mean x̄ = Σx ÷ n. Sample standard deviation s = √[Σ(x − x̄)² ÷ (n − 1)]. A **small** s means data are clustered around the mean (precise); a **large** s means they are spread out. About 95 % of values lie within 2 standard deviations of the mean if data are normally distributed. Error bars on graphs often show ± 1 SD."),
        ("formule", "Unpaired t-test", "t = (x̄₁ − x̄₂) ÷ √(s₁²÷n₁ + s₂²÷n₂); degrees of freedom = n₁ + n₂ − 2. Null hypothesis: **no significant difference** between the two means. If |t| is **greater** than the critical value at p = 0.05 for those degrees of freedom, reject the null hypothesis. Two-tailed critical values at p = 0.05: df 8 = 2.31; df 10 = 2.23; df 12 = 2.18; df 18 = 2.10."),
        ("pieges", "Choose the right test", "**t-test**: two means of continuous data. **Chi-squared**: frequencies in categories. **Spearman**: correlation between two ranked variables. Many students use the t-test on counts or chi-squared on measurements. Calculate to enough decimal places, state the null hypothesis and the degrees of freedom, and conclude in words about the biology. Statistics cannot rescue a poor design: use random samples, a sufficient sample size, and control variables."),
        ("retenir", "Evaluating an investigation", "Improve **reliability** with repeats and larger samples; improve **validity** by controlling variables (e.g. leaf age, same plant species, same time of day); reduce bias by random sampling. Anomalous results should be identified, checked and, if justified, excluded with explanation. Identify limitations (e.g. few sites, one season) and suggest improvements.")],
       [("Worked example — mean and SD", "Leaf lengths (mm) in sun: 22, 25, 24, 27, 23, 26. Calculate the mean and the standard deviation.", ["Mean = (22+25+24+27+23+26) ÷ 6 = %.1f." % m1, "Deviations squared: 6.25, 0.25, 0.25, 6.25, 2.25, 2.25; sum = 17.5.", "s = √(17.5 ÷ 5) = %.2f." % s1], "Mean 24.5 mm; s = 1.87 mm"),
        ("Worked example — t-test", "Shade leaves (mm): 29, 31, 28, 33, 30, 29 give mean 30.0 and s = 1.79. Test the difference from sun leaves (mean 24.5, s = 1.87), n = 6 each.", ["t = (30.0 − 24.5) ÷ √(1.87²÷6 + 1.79²÷6) = 5.5 ÷ 1.057 = %.2f." % tval, "df = 6 + 6 − 2 = 10; critical value (p = 0.05) = 2.23.", "%.2f > 2.23: the difference is significant; reject the null hypothesis." % tval], "t = 5.20 > 2.23: significant")],
       [M("Which test compares the means of two samples of continuous data?", "t-test", ["Chi-squared test", "Spearman's rank test", "Simpson's index"], "t-test."),
        TF("A larger standard deviation means that the data are more tightly clustered.", False, "A larger SD means more spread."),
        N("Calculate the mean of 12, 15, 14, 16, 18.", 15, "(12+15+14+16+18) ÷ 5 = 75 ÷ 5 = 15.")],
       [O("A t-test gives t = 1.5 with 10 degrees of freedom (critical value 2.23). State the conclusion and one way to improve the investigation.", "t is less than the critical value, so the difference between means is not significant at p = 0.05 (the null hypothesis is not rejected). Increase the sample size or control other variables to improve reliability.", ["Not significant (1)", "Comparison with critical value (1)", "Improvement (1)"]),
        M("Two samples have the same mean but different standard deviations. The sample with the smaller SD", "has values closer to the mean and is more consistent", ["has the larger mean", "has more individuals", "must be wrong"], "SD measures spread.", tier="approfondissement")],
       P("Two cassava varieties were grown in plots near Bafoussam. Yield per plant (kg): variety A mean 4.2, SD 0.5 (n = 8); variety B mean 3.6, SD 0.6 (n = 8).",
         [pn("Calculate the difference between the means (kg).", round(4.2 - 3.6, 1), "4.2 − 3.6 = 0.6.", 0.01, "kg", 1),
          pn("Calculate t to 2 decimal places.", round((4.2 - 3.6) / m2.sqrt(0.5 ** 2 / 8 + 0.6 ** 2 / 8), 2), "√(0.25÷8 + 0.36÷8) = √0.07625 = 0.2761; t = 0.6 ÷ 0.2761 = 2.17.", 0.02, None, 2),
          pm("Degrees of freedom are 14; critical value is 2.14. The conclusion is that the difference is", "significant", ["not significant", "impossible to test", "negative"], "2.17 > 2.14: the null hypothesis is rejected (just).", 1),
          po("Suggest why the result should be interpreted with caution.", "The calculated t is only just above the critical value and the sample is small; repeating with more plots, seasons and soil types would make the conclusion more reliable.", ["Small sample / marginal (1)", "Suggestion for improvement (1)"], 1)]),
       tt_sc, (fig, "Mean leaf lengths of sun and shade leaves (n = 6 each).", "A bar chart with two bars: sun leaves 24.5 mm and shade leaves 30.0 mm."),
       notes=["Critical t values (df 8, 10, 12, 14, 18) are standard table values; df = 14 value 2.14 (two-tailed, 5 %) is used in the problem.", "Welch's version of the t-test (different formulae for degrees of freedom) is not covered; check which version is expected.", "Yield and leaf data are invented."])
assert abs(m2.sqrt(0.5 ** 2 / 8 + 0.6 ** 2 / 8) - 0.27613) < 1e-4 and round((4.2 - 3.6) / m2.sqrt(0.5 ** 2 / 8 + 0.6 ** 2 / 8), 2) == 2.17

# =====================================================================  MOCK PAPER
q2_obs = [215, 190, 205, 190]; chi_m = sum((o - 200) ** 2 / 200 for o in q2_obs); assert abs(chi_m - 2.25) < 1e-9
linc = 30 * 40 / 5; assert linc == 240
q_m = 0.16 ** 0.5; car = 2 * (1 - q_m) * q_m * 100; assert abs(q_m - 0.4) < 1e-12 and abs(car - 48) < 1e-9
mock_paper(p, Lm, "paper-1", "GCE A Level Biology — mock paper 1", 45,
           "Answer all four questions. Marks are shown for each part; the paper is marked out of 20. Show your working for calculations. This is an original practice paper; its format and level are to be checked against the official texts of the Cameroon GCE Board.",
           [("Question 1 — Energy (5 marks)", P("A student investigates energy transfer in cells and in a cocoa seedling.",
              [pm("In which part of the mitochondrion does the Krebs cycle occur?", "The matrix", ["The inner membrane", "The intermembrane space", "The cytoplasm"], "The enzymes of the Krebs cycle are in the matrix.", 1),
               pn("Each hexose made in the Calvin cycle needs 18 ATP. How many ATP are needed to make 3 hexose molecules?", 54, "3 × 18 = 54.", 0, None, 1),
               pn("Germinating seeds take up 20 cm³ of O₂ and release 14 cm³ of CO₂. Calculate the respiratory quotient.", 0.7, "14 ÷ 20 = 0.70, suggesting lipid as respiratory substrate.", 0.01, None, 1),
               po("Explain why the light-independent stage of photosynthesis soon stops if a plant is placed in the dark.", "The Calvin cycle needs ATP and reduced NADP from the light-dependent stage; in the dark these are not made, so GP cannot be reduced to TP and RuBP is not regenerated.", ["No ATP/reduced NADP produced (1)", "GP not reduced / RuBP not regenerated (1)"], 2)])),
            ("Question 2 — Genetics (5 marks)", P("In maize, purple kernels (P) are dominant to yellow (p) and smooth (S) to wrinkled (s). The genes are on different chromosomes. A plant PpSs is crossed with ppss (a test cross) and gives 800 kernels.",
              [pm("What phenotypic ratio is expected?", "1 : 1 : 1 : 1", ["9 : 3 : 3 : 1", "3 : 1", "1 : 1"], "Independent assortment in a test cross.", 1),
               pn("How many purple wrinkled kernels are expected?", 200, "¼ of 800 = 200.", 0, None, 1),
               pn("The observed numbers are 215, 190, 205 and 190 (expected 200 each). Calculate χ² (2 d.p.).", chi_m, "(15² + 10² + 5² + 10²) ÷ 200 = 450 ÷ 200 = 2.25.", 0.01, None, 2),
               pm("The critical value for 3 degrees of freedom at p = 0.05 is 7.81. What is the conclusion?", "The results are not significantly different from the expected ratio", ["The results are significantly different", "The ratio is proved", "The genes are linked"], "2.25 < 7.81.", 1)])),
            ("Question 3 — Homeostasis and ecology (5 marks)", P("Part of the work of an environmental club near Limbe concerns a freshwater fish population and kidney function in humans.",
              [pm("Which hormone increases the permeability of the collecting duct to water?", "ADH", ["Insulin", "Glucagon", "FSH"], "ADH is released by the posterior pituitary.", 1),
               pn("30 fish are marked and released. A later sample of 40 fish contains 5 marked fish. Estimate the population by the Lincoln index.", 240, "N = 30 × 40 ÷ 5 = 240.", 0, None, 2),
               po("State two assumptions made when using the mark-release-recapture method.", "Marking does not harm or change behaviour of the fish and marks do not fade; marked fish mix randomly; no births, deaths or migration between samples.", ["Any two valid assumptions (2)"], 2)])),
            ("Question 4 — Populations and biotechnology (5 marks)", P("A recessive condition affects 16 % of a large, randomly mating population (q² = 0.16). A DNA fragment from one individual is then copied by PCR and run on a gel.",
              [pn("Calculate q.", 0.4, "√0.16 = 0.4.", 0.001, None, 1),
               pn("Calculate the percentage of the population that are carriers.", 48, "p = 0.6; 2pq = 2 × 0.6 × 0.4 = 0.48 = 48 %.", 0.1, "%", 2),
               pn("How many copies of the fragment are made from one molecule after 12 PCR cycles?", 4096, "2¹² = 4096.", 0, None, 1),
               pm("In gel electrophoresis the shortest DNA fragments", "travel furthest towards the positive electrode", ["stay in the wells", "travel towards the negative electrode", "travel the same distance as long fragments"], "Small fragments move more easily through the gel.", 1)]))])
write_compact(p)
