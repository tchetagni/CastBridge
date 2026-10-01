"""Generator: content/learn/lower6-biology (Biology - Lower Sixth, GCE A Level syllabus, first year). Run: python3 lower6-biology.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _l6 import *

p = Pack("lower6-biology", "Biology — Lower Sixth", level="Lower Sixth", subject="biology", cursus="secondary",
         description="Lower Sixth Biology notes with worked examples, graded exercises and self-checks: biological molecules, cells, membranes, cell division, nucleic acids, classification, exchange and transport, immunity.",
         programRef="Cameroon GCE Board — Advanced Level Biology syllabus, first year (Lower Sixth) — to be checked against the official texts")
NOTE_ORDER = "The order and grouping of topics between Lower and Upper Sixth is an editorial choice to be checked against the GCE Board syllabus."


def hexagon(cx, cy, r, fill="lightyellow", stroke="ink"):
    pts = []
    for i in range(6):
        pts += [round(cx + r * math.cos(math.radians(60 * i + 30)), 1), round(cy + r * math.sin(math.radians(60 * i + 30)), 1)]
    return POLY(pts, fill=fill, stroke=stroke, width=2)

# =====================================================================  CH 1  MOLECULES
c1 = p.chapter("molecules", "Biological molecules", programRef="GCE A Level Biology — Biological molecules (to be checked)")

# ---- 1 carbohydrates
fig = shapes([T(210, 20, "Condensation: two glucose units make maltose", 14, bold=True),
              hexagon(70, 100, 34, "lightyellow"), T(70, 105, "glucose", 12),
              T(125, 105, "+", 22),
              hexagon(180, 100, 34, "lightyellow"), T(180, 105, "glucose", 12),
              LINE(225, 100, 265, 100, arrow="end", width=3),
              hexagon(310, 100, 34, "lightorange"), hexagon(378, 100, 34, "lightorange"),
              T(344, 105, "O", 14, bold=True, color="red"),
              T(344, 160, "glycosidic bond", 12, color="red"), LINE(344, 146, 344, 112, color="red", arrow="end", width=1.5),
              T(245, 180, "H₂O is released (condensation)", 12, color="blue"),
              T(310, 55, "maltose", 12, color="grey")], 430, 200)
carb_sc = [
    ("Which type of reaction joins two monosaccharides into a disaccharide?", "Condensation, releasing a molecule of water", ["Hydrolysis, using a molecule of water", "Oxidation, releasing carbon dioxide", "Reduction, releasing hydrogen gas"], "A glycosidic bond forms by condensation; water is a product."),
    ("Which polysaccharide is the main energy store in animal cells?", "Glycogen", ["Starch", "Cellulose", "Chitin only"], "Animals store glucose as glycogen, mainly in liver and muscle."),
    ("Which carbohydrate gives a negative Benedict's test until it is hydrolysed?", "Sucrose", ["Glucose", "Maltose", "Fructose"], "Sucrose is a non-reducing sugar; hydrolysis releases glucose and fructose, which are reducing."),
    ("Cellulose differs from starch because it is made of", "β-glucose joined by 1,4 links in straight chains", ["α-glucose joined by 1,4 links in a helix", "fructose joined by 1,6 links", "amino acids joined by peptide bonds"], "Alternate β-glucose units are inverted, giving straight chains that H-bond into fibres."),
    ("Iodine solution turns blue-black with", "Starch", ["Glucose", "Cellulose", "Sucrose"], "Iodine fits inside the amylose helix and gives a blue-black colour."),
]
mal_H = 2 * 12 - 2; mal_O = 2 * 6 - 1; assert (mal_H, mal_O) == (22, 11)
tri_H = 3 * 12 - 2 * 2; assert tri_H == 32
lesson(c1, "carbohydrates", "Carbohydrates",
       ["Describe the structure of α- and β-glucose and the glycosidic bond.", "Relate the structure of starch, glycogen and cellulose to their functions.", "Carry out and interpret the Benedict's and iodine tests."],
       [("definition", "Monomers and polymers", "A **monosaccharide** (e.g. glucose, fructose, galactose) is a single sugar unit. Two units joined make a **disaccharide** (maltose = glucose + glucose; sucrose = glucose + fructose; lactose = glucose + galactose). Many units make a **polysaccharide**. Units are joined by a **glycosidic bond** formed in a **condensation** reaction (water is removed) and broken by **hydrolysis** (water is added)."),
        ("pieges", "Reducing and non-reducing sugars", "**Benedict's test**: heat with the reagent; a blue solution turns green, yellow, orange or brick-red according to the amount of **reducing sugar** (glucose, fructose, maltose, lactose). **Sucrose is non-reducing**: boil it first with dilute hydrochloric acid, neutralise with sodium hydrogencarbonate, then repeat the test. **Iodine** tests for starch (blue-black). Do not say that a negative Benedict's test means 'no sugar'."),
        ("retenir", "Three storage and structural polysaccharides", "- **Starch** (plants): α-glucose; **amylose** is an unbranched helix, **amylopectin** is branched (1,4 and 1,6 links). Compact and insoluble, so it does not affect water potential.\n- **Glycogen** (animals, fungi): like amylopectin but more highly branched, so glucose is released quickly from many ends.\n- **Cellulose** (cell walls): β-glucose in straight chains; hydrogen bonds between chains make strong microfibrils.")],
       [("Worked example — formulae", "Glucose is C₆H₁₂O₆. Work out the molecular formula of maltose.", ["Maltose = two glucose units joined by condensation.", "Add the atoms: C₁₂H₂₄O₁₂.", "Remove one water molecule (H₂O): H 24 − 2 = %d, O 12 − 1 = %d." % (mal_H, mal_O)], "C₁₂H₂₂O₁₁"),
        ("Worked example — identifying a sugar", "A solution gives no colour change with Benedict's reagent. After boiling with dilute acid and neutralising, it gives a brick-red precipitate. What sugar was it?", ["Negative at first, so no reducing sugar was present.", "Positive after acid hydrolysis, so a non-reducing sugar was broken into reducing monosaccharides.", "The common non-reducing sugar is sucrose (glucose + fructose)."], "Sucrose")],
       [M("What is the name of the bond between two glucose units in maltose?", "Glycosidic bond", ["Peptide bond", "Ester bond", "Hydrogen bond"], "Sugar units are linked by glycosidic bonds."),
        TF("Cellulose is a polymer of α-glucose.", False, "Cellulose is made of β-glucose; starch and glycogen are made of α-glucose."),
        N("A trisaccharide is made from three hexose sugars (each C₆H₁₂O₆) by two condensation reactions. How many hydrogen atoms does it contain?", tri_H, "3 × 12 = 36 H, minus 2 H for each of the 2 water molecules removed: 36 − 4 = %d." % tri_H)],
       [O("Explain why starch is suited to energy storage in plant cells.", "Starch is a large insoluble polymer, so it does not dissolve in cell sap and does not change the water potential or draw water into the cell by osmosis. Its helical, branched structure packs many glucose units into a small space. It is easily hydrolysed by amylase to glucose when energy is needed.", ["Insoluble / no osmotic effect (1)", "Compact (1)", "Hydrolysed to glucose when needed (1)"]),
        M("Why can humans not digest cellulose?", "They lack an enzyme that hydrolyses the β-1,4 glycosidic bonds", ["Cellulose is made of amino acids", "Cellulose is too soluble to be absorbed", "Cellulose has no glycosidic bonds"], "Humans lack cellulase; cellulose passes on as dietary fibre.", tier="approfondissement")],
       P("A student tested four solutions (A–D) from a school laboratory in Bamenda. Results: A — blue with Benedict's, blue-black with iodine; B — brick-red with Benedict's, brown with iodine; C — blue with Benedict's, brown with iodine, brick-red after acid hydrolysis; D — blue with both tests, still blue after hydrolysis.",
         [pm("Which solution contained starch?", "A", ["B", "C", "D"], "Only A gave blue-black with iodine.", 1),
          pm("Which solution contained a reducing sugar originally?", "B", ["A", "C", "D"], "B gave a positive Benedict's test without hydrolysis.", 1),
          po("Identify C and explain your reasoning.", "C was a non-reducing sugar such as sucrose: no reaction at first, but acid hydrolysis produced reducing monosaccharides.", ["Negative before / positive after hydrolysis (1)", "Non-reducing sugar, e.g. sucrose (1)"], 2),
          po("Name one carbohydrate that D could be and say why.", "Cellulose (or a non-sugar): it gives no starch or sugar reaction, and even after acid it would need much stronger hydrolysis.", ["Any sensible answer consistent with the data (1)", "Reason linked to the results (1)"], 2)]),
       carb_sc, (fig, "Condensation of two α-glucose units to give maltose; a glycosidic bond forms and water is released.", "Two glucose hexagons, a plus sign, an arrow, then two linked hexagons labelled maltose with an oxygen bridge between them and water released."),
       notes=["Benedict's colour sequence and the acid-hydrolysis procedure follow common school practice; check the exact procedure used in Cameroonian practicals.", NOTE_ORDER])

# ---- 2 lipids
fig = shapes([T(215, 18, "Triglyceride = glycerol + 3 fatty acids", 14, bold=True),
              RECT(20, 50, 60, 120, fill="lightyellow", radius=6), T(50, 105, "Glycerol", 13),
              RECT(130, 50, 230, 28, fill="lightorange", radius=6), T(245, 69, "Fatty acid 1", 13),
              RECT(130, 92, 230, 28, fill="lightorange", radius=6), T(245, 111, "Fatty acid 2", 13),
              RECT(130, 134, 230, 28, fill="lightorange", radius=6), T(245, 153, "Fatty acid 3", 13),
              LINE(82, 64, 128, 64, color="red", width=3), LINE(82, 106, 128, 106, color="red", width=3), LINE(82, 148, 128, 148, color="red", width=3),
              T(105, 44, "ester", 11, color="red"), T(215, 192, "3 ester bonds; 3 H₂O released", 12, color="blue")], 430, 205)
lip_sc = [
    ("What bond joins a fatty acid to glycerol?", "Ester bond", ["Peptide bond", "Glycosidic bond", "Phosphodiester bond only"], "Condensation between –COOH and –OH forms an ester bond."),
    ("A phospholipid differs from a triglyceride because it has", "a phosphate group in place of one fatty acid", ["no glycerol", "four fatty acids", "a peptide bond"], "One fatty acid is replaced by a hydrophilic phosphate head."),
    ("Unsaturated fatty acids contain", "at least one C=C double bond", ["no hydrogen atoms", "only single C–C bonds", "a phosphate group"], "Double bonds put kinks in the chain."),
    ("In the emulsion test, a positive result for lipid is", "a cloudy white emulsion", ["a brick-red precipitate", "a blue-black colour", "a purple colour"], "Lipid dissolved in ethanol forms a white emulsion when poured into water."),
    ("Which role is NOT a function of lipids?", "Acting as the genetic material", ["Energy store", "Thermal insulation", "Waterproofing"], "Genetic material is DNA/RNA."),
]
lesson(c1, "lipids", "Lipids",
       ["Describe the structure of triglycerides and phospholipids.", "Distinguish saturated from unsaturated fatty acids.", "Relate structure to function and carry out the emulsion test."],
       [("definition", "Triglycerides", "A **triglyceride** is made of one **glycerol** and three **fatty acids**. Each fatty acid joins glycerol by an **ester bond** formed in a condensation reaction, so three water molecules are released. Lipids are insoluble in water but dissolve in organic solvents such as ethanol."),
        ("pieges", "Saturated or unsaturated?", "A **saturated** fatty acid has no C=C double bonds (the chain is straight and packs closely, so fats are often solid at room temperature). An **unsaturated** one has one or more double bonds, which kink the chain (oils are usually liquid). Do not say that unsaturated fats 'have no hydrogen'; they simply have fewer hydrogen atoms than the saturated chain of the same length."),
        ("retenir", "Phospholipids and functions", "A **phospholipid** has glycerol, two fatty acids and a **phosphate** head. The head is **hydrophilic**, the tails are **hydrophobic**, so phospholipids form a **bilayer** in water. Functions of triglycerides: energy store (about twice the energy per gram of carbohydrate), insulation, buoyancy, protection of organs, metabolic water. **Emulsion test**: shake with ethanol, then add water; a cloudy white emulsion means lipid is present.")],
       [("Worked example — water released", "How many water molecules are released when one triglyceride is made from glycerol and three fatty acids? How many when 5 triglycerides are made?", ["Each ester bond forms in a condensation reaction releasing one H₂O.", "One triglyceride has 3 ester bonds, so 3 water molecules.", "For 5 triglycerides: 5 × 3 = %d." % (5 * 3)], "3; and 15"),
        ("Worked example — reading the emulsion test", "Seeds from a groundnut are crushed in ethanol; the liquid is poured into water and the water goes cloudy and white. What does this show, and why does it happen?", ["A cloudy white emulsion is the positive result for lipids.", "Lipid dissolves in ethanol but not in water; poured into water it forms tiny droplets that scatter light.", "So groundnuts contain lipid (they are a good source of oil)."], "Lipid is present")],
       [M("How many ester bonds are present in one phospholipid molecule?", "Two", ["One", "Three", "Four"], "Two fatty acids are joined to glycerol by ester bonds; the third position carries phosphate."),
        TF("Phospholipid tails are hydrophilic.", False, "The tails are hydrophobic; the phosphate head is hydrophilic."),
        N("Five triglyceride molecules are formed from glycerol and fatty acids. How many molecules of water are released altogether?", 15, "Each triglyceride has 3 ester bonds, so 5 × 3 = 15.")],
       [O("Explain why a phospholipid bilayer forms spontaneously in water.", "The hydrophilic phosphate heads face the surrounding water while the hydrophobic fatty-acid tails face each other, away from water, in the middle. This arrangement is the most stable and needs no energy input.", ["Heads hydrophilic, face water (1)", "Tails hydrophobic, face inwards (1)", "Stable / spontaneous (1)"]),
        M("Which statement about fats and carbohydrates is correct for the same mass?", "Fats release more energy on respiration because they contain more C–H bonds", ["Carbohydrates contain more C–H bonds", "Both release equal energy", "Fats contain more oxygen"], "Fats are more reduced, with a high ratio of hydrogen to oxygen.", tier="approfondissement")],
       P("Palm oil, widely used in Cameroonian cooking, is rich in triglycerides.",
         [pm("Name the three-carbon alcohol in a triglyceride.", "Glycerol", ["Glucose", "Glycine", "Glycogen"], "Glycerol is the backbone.", 1),
          pm("The reaction that joins fatty acid to glycerol is", "condensation", ["hydrolysis", "oxidation", "phosphorylation"], "Water is released.", 1),
          po("Describe the structural difference between a saturated and an unsaturated fatty acid.", "Saturated: no C=C double bonds, straight chain. Unsaturated: one or more C=C double bonds, which bend the chain.", ["Presence/absence of C=C (1)", "Effect on shape of chain (1)"], 2),
          po("Explain why oils from seeds are liquid but animal fat is often solid.", "Seed oils contain more unsaturated fatty acids whose kinks stop the molecules packing closely, so melting point is lower; animal fats contain more saturated chains that pack tightly.", ["Unsaturated vs saturated (1)", "Packing / melting point (1)"], 2)]),
       lip_sc, (fig, "A triglyceride is a glycerol molecule bound to three fatty acids by ester bonds.", "A box for glycerol on the left joined by three red lines to three fatty acid bars, labelled ester bonds."),
       notes=["The energy figure 'about twice per gram' is a standard approximation (roughly 38 versus 17 kJ per gram); check the value the syllabus expects."])

# ---- 3 proteins
fig = shapes([T(215, 18, "Four levels of protein structure", 14, bold=True),
              RECT(20, 45, 170, 34, fill="lightblue", radius=6), T(105, 67, "Primary: amino acid sequence", 11),
              RECT(20, 95, 170, 34, fill="lightgreen", radius=6), T(105, 117, "Secondary: α-helix, β-sheet", 11),
              RECT(20, 145, 170, 34, fill="lightyellow", radius=6), T(105, 167, "Tertiary: 3-D folding", 11),
              RECT(20, 195, 170, 34, fill="lightorange", radius=6), T(105, 217, "Quaternary: several chains", 11),
              LINE(105, 79, 105, 95, arrow="end"), LINE(105, 129, 105, 145, arrow="end"), LINE(105, 179, 105, 195, arrow="end"),
              T(215, 62, "peptide bonds", 11, anchor="start", color="grey"),
              T(215, 112, "hydrogen bonds", 11, anchor="start", color="grey"),
              T(215, 155, "H bonds, ionic, disulfide,", 11, anchor="start", color="grey"), T(215, 170, "hydrophobic interactions", 11, anchor="start", color="grey"),
              T(215, 217, "e.g. haemoglobin (4 chains)", 11, anchor="start", color="grey")], 430, 245)
pro_sc = [
    ("The bond joining two amino acids is the", "peptide bond", ["glycosidic bond", "ester bond", "ionic bond only"], "–COOH of one amino acid joins –NH₂ of the next with loss of water."),
    ("The primary structure of a protein is", "the sequence of amino acids", ["the α-helix", "the 3-D shape", "the number of chains"], "Sequence determines all higher levels."),
    ("Which test turns purple with a protein?", "Biuret test", ["Benedict's test", "Iodine test", "Emulsion test"], "Biuret reagent (NaOH + copper(II) sulfate) turns lilac/purple with peptide bonds."),
    ("Haemoglobin is an example of a", "globular protein with quaternary structure", ["fibrous protein with no folding", "polysaccharide", "lipid"], "It has four polypeptide chains."),
    ("Denaturation of an enzyme involves", "loss of the 3-D shape of the active site", ["breaking of peptide bonds only", "removal of its amino acids", "addition of lipids"], "Hydrogen and ionic bonds break; the primary sequence stays."),
]
lesson(c1, "proteins", "Amino acids and proteins",
       ["Describe the general structure of an amino acid and the peptide bond.", "Explain the four levels of protein structure and the bonds that hold them.", "Compare fibrous and globular proteins; use the biuret test."],
       [("definition", "Amino acids and peptides", "An **amino acid** has a central carbon bonded to an **amino group** (–NH₂), a **carboxyl group** (–COOH), a hydrogen atom and a variable **R group**. Twenty different R groups occur in living things. Two amino acids join by a **peptide bond** (condensation, water lost) to form a **dipeptide**; many form a **polypeptide**."),
        ("pieges", "Primary is not 'first fold'", "The **primary structure** is only the order of amino acids, held by peptide bonds. Secondary structure (α-helix, β-pleated sheet) is held by **hydrogen bonds** between C=O and N–H groups. Tertiary structure is the further folding held by hydrogen, ionic and disulfide bonds and hydrophobic interactions between R groups. Do not say that heating breaks peptide bonds; denaturation breaks the weaker bonds that hold the shape."),
        ("retenir", "Fibrous and globular proteins", "- **Fibrous** (e.g. collagen, keratin): long chains, often insoluble, structural and strong.\n- **Globular** (e.g. enzymes, haemoglobin, antibodies): compact, folded, usually soluble, with specific shapes for their roles.\n- **Quaternary structure**: two or more polypeptides (haemoglobin has four, plus haem groups with iron).\n**Biuret test**: add NaOH then dilute copper(II) sulfate; lilac/purple means protein.")],
       [("Worked example — counting peptide bonds", "A polypeptide contains 120 amino acids in one chain. How many peptide bonds and how many water molecules were released in its formation?", ["For a single unbranched chain, bonds = amino acids − 1.", "120 − 1 = %d peptide bonds." % (120 - 1), "Each bond releases one water molecule: %d." % (120 - 1)], "119 bonds; 119 water molecules"),
        ("Worked example — number of possible dipeptides", "How many different dipeptides can be built from the 20 common amino acids if order matters?", ["The first position can be any of 20 amino acids.", "The second can also be any of 20.", "20 × 20 = %d" % (20 * 20)], "400")],
       [M("Which part of an amino acid differs between different amino acids?", "The R group", ["The amino group", "The carboxyl group", "The central hydrogen"], "Only the R group varies."),
        TF("Hydrogen bonds hold the primary structure of a protein.", False, "Primary structure is held by peptide bonds; hydrogen bonds form the secondary structure."),
        N("A protein has 4 polypeptide chains containing 141, 141, 146 and 146 amino acids. How many peptide bonds does it contain in total?", 141 + 141 + 146 + 146 - 4, "Each chain has (n − 1) peptide bonds, so the total is 574 − 4 = 570.")],
       [O("Explain why a change of one amino acid in the primary structure can alter the function of a protein.", "A different R group can form different bonds, which changes folding. The tertiary structure and so the shape of the active site or binding site changes, and function is reduced or lost.", ["Different R group / bonding (1)", "Change in 3-D shape (1)", "Function altered (1)"]),
        M("Collagen is suitable for strength because it", "has three polypeptides wound together and cross-linked", ["is a soluble globular protein", "contains haem groups", "has a changing shape"], "Fibrous proteins have repeating sequences and cross-links.", tier="approfondissement")],
       P("A scientist hydrolysed a small peptide and found it contained glycine, alanine and serine.",
         [pm("How many peptide bonds are in a linear tripeptide?", "2", ["1", "3", "4"], "n − 1 = 2.", 1),
          pn("How many different tripeptides can be made using each of these three amino acids exactly once?", 6, "3! = 3 × 2 × 1 = 6.", 0, None, 2),
          po("The peptide gives a purple colour with Biuret reagent. Explain why this is expected.", "The reagent reacts with peptide bonds; the peptide contains peptide bonds, so a lilac/purple colour appears.", ["Reagent detects peptide bonds (1)", "Peptide contains them (1)"], 2)]),
       pro_sc, (fig, "Levels of protein structure and the main bonds involved.", "Four stacked boxes from primary to quaternary structure with arrows and the bonds each level involves."),
       notes=["Fibrous/globular examples and the biuret reagent description are standard but check the colour terms used locally (lilac, purple, violet)."])

# ---- 4 water and ions
fig = shapes([T(215, 18, "Hydrogen bonding between water molecules", 14, bold=True),
              CIRCLE(110, 100, 22, fill="lightblue", stroke="blue"), T(110, 105, "O", 15, bold=True),
              CIRCLE(70, 140, 12, fill="white"), T(70, 145, "H", 12), CIRCLE(150, 140, 12, fill="white"), T(150, 145, "H", 12),
              LINE(100, 119, 76, 131, width=2), LINE(120, 119, 144, 131, width=2),
              CIRCLE(320, 100, 22, fill="lightblue", stroke="blue"), T(320, 105, "O", 15, bold=True),
              CIRCLE(280, 140, 12, fill="white"), T(280, 145, "H", 12), CIRCLE(360, 140, 12, fill="white"), T(360, 145, "H", 12),
              LINE(310, 119, 286, 131, width=2), LINE(330, 119, 354, 131, width=2),
              LINE(150, 128, 296, 108, color="red", dash=True, width=2),
              T(215, 70, "hydrogen bond", 12, color="red"),
              T(110, 60, "δ− on O", 11, color="grey"), T(215, 190, "O is slightly negative, H slightly positive (polar)", 12)], 430, 205)
water_sc = [
    ("Why is water a good solvent for ions and polar molecules?", "Its polar molecules surround and separate charged particles", ["It is non-polar", "It has a low boiling point", "It is denser than ice"], "The δ+ and δ− ends are attracted to ions and polar solutes."),
    ("A high specific heat capacity of water means that", "large heat input causes only a small temperature rise", ["it freezes easily", "it evaporates at 0 °C", "it cannot dissolve salts"], "Hydrogen bonds absorb energy, so aquatic habitats and blood temperature are stable."),
    ("Evaporation of sweat cools the body because", "water has a high latent heat of vaporisation", ["water is non-polar", "sweat contains lipid", "water is denser as a gas"], "Much energy is taken from the skin to break hydrogen bonds."),
    ("Ice floats on water because", "it is less dense than liquid water", ["it is more dense than water", "it has no hydrogen bonds", "its molecules are non-polar"], "Hydrogen bonds hold molecules in an open lattice in ice."),
    ("Iron(II) ions are needed for", "haemoglobin (haem group)", ["the cell wall", "the DNA backbone", "the peptide bond"], "Fe²⁺ binds oxygen in haem."),
]
Q = 500 * 4.18 * 10
lesson(c1, "water-ions", "Water and inorganic ions",
       ["Explain the polarity of water and hydrogen bonding.", "Relate the properties of water to its roles in organisms.", "State the roles of important inorganic ions."],
       [("definition", "A polar molecule", "A water molecule (H₂O) is **polar**: the oxygen atom is slightly negative (δ−) and each hydrogen slightly positive (δ+). The positive hydrogen of one molecule is attracted to the negative oxygen of another, forming a weak **hydrogen bond**. Each bond is weak, but a great many together give water its unusual properties."),
        ("pieges", "Link each property to its cause and use", "Always give three parts: the property, the reason (hydrogen bonds), and the biological benefit. A common error is to say that water 'has a high boiling point so it cools us'. The cooling effect of sweat is due to the high **latent heat of vaporisation**; the stable temperature of lakes and blood is due to the high **specific heat capacity**."),
        ("retenir", "Properties and roles", "- **Solvent**: ions and polar molecules dissolve, so reactions occur in solution; transport in blood and xylem.\n- **High specific heat capacity**: stable body and habitat temperature.\n- **High latent heat of vaporisation**: sweating and transpiration cool.\n- **Cohesion** and **surface tension**: columns of water in xylem; pond-skater habitat.\n- **Ice less dense than water**: ponds freeze from the top.\n- **Metabolite**: used in hydrolysis and photosynthesis."),
        ("retenir", "Some inorganic ions", "- **Na⁺, K⁺**: nerve impulses; K⁺ and Na⁺ with water balance.\n- **Ca²⁺**: bone and teeth, muscle contraction, blood clotting.\n- **Fe²⁺**: haem in haemoglobin.\n- **Mg²⁺**: centre of chlorophyll.\n- **PO₄³⁻**: DNA, ATP, phospholipids.\n- **H⁺**: pH; **Cl⁻**: balance of charge, stomach acid (HCl).\n- **NO₃⁻**: nitrogen for amino acids in plants.")],
       [("Worked example — heat energy", "Specific heat capacity of water = 4.18 J g⁻¹ °C⁻¹. How much energy is needed to warm 500 g of water in a calabash pot from 20 °C to 30 °C?", ["Energy = mass × specific heat capacity × temperature rise.", "Temperature rise = 30 − 20 = 10 °C.", "Energy = 500 × 4.18 × 10 = %d J." % Q], "20 900 J (20.9 kJ)"),
        ("Worked example — explain a property", "Explain why large lakes such as Lake Bamendjin have a more stable temperature than the air above them.", ["Water has a high specific heat capacity.", "Hydrogen bonds absorb much heat before the molecules move faster.", "So the water warms and cools slowly, giving a stable habitat."], "High specific heat capacity due to hydrogen bonds")],
       [M("What type of bond holds water molecules to each other?", "Hydrogen bonds", ["Covalent bonds", "Peptide bonds", "Ionic bonds"], "Intermolecular attractions between δ+ H and δ− O."),
        TF("Sweating cools the body because water has a high latent heat of vaporisation.", True, "Heat is needed to evaporate sweat, and it is taken from the skin."),
        N("How much energy (in J) warms 200 g of water by 15 °C? Use 4.18 J g⁻¹ °C⁻¹.", 200 * 4.18 * 15, "200 × 4.18 × 15 = %.0f J." % (200 * 4.18 * 15), tol=1, unit="J")],
       [O("Explain how cohesion helps water to rise in a tall tree.", "Hydrogen bonds make water molecules stick together (cohesion), so a continuous column in the xylem is pulled up as water evaporates from the leaves; adhesion to the xylem walls also helps.", ["Cohesion by hydrogen bonds (1)", "Continuous column pulled up (1)", "Transpiration pull (1)"]),
        M("Which statement best explains why water is an effective transport medium?", "It is a polar solvent in which many substances dissolve", ["It is non-polar and mixes with lipids", "It has a low specific heat capacity", "It has no hydrogen bonds"], "Dissolved substances can be carried in blood and sap.", tier="approfondissement")],
       P("The table-top experiment: 100 g of water absorbs 4180 J and its temperature rises.",
         [pn("By how many °C does the temperature rise? (4.18 J g⁻¹ °C⁻¹)", 4180 / (100 * 4.18), "ΔT = 4180 / (100 × 4.18) = 10 °C.", 0.01, "°C", 2),
          pm("This is possible because water has a high", "specific heat capacity", ["density", "pH", "melting point only"], "Heat is needed to raise water temperature.", 1),
          po("State two biological advantages of this property.", "It keeps body fluids and aquatic habitats at stable temperatures, so enzymes work and aquatic organisms survive.", ["Stable internal temperature (1)", "Stable aquatic habitat (1)"], 2)]),
       water_sc, (fig, "Two water molecules joined by a hydrogen bond.", "Two water molecules, each with an oxygen and two hydrogens, joined by a red dashed hydrogen bond."),
       notes=["Specific heat capacity of water given as 4.18 J g⁻¹ °C⁻¹ (4.2 is also common); check the value expected in Cameroonian practice."])
assert abs(Q - 20900) < 1e-6

# ---- 5 enzymes
fig = plot(0, 10, 0, 12, curves=[("12*x/(1.5+x)", "blue", "rate")], segments=[(0, 12, 10, 12, "red", True, "Vmax")], xlabel="substrate concentration", ylabel="rate", h=260)
enz_sc = [
    ("An enzyme lowers the activation energy of a reaction by", "providing an alternative pathway through the enzyme-substrate complex", ["adding heat", "changing the equilibrium position", "being used up"], "Enzymes are catalysts and are not used up."),
    ("The induced-fit model says that", "the active site changes shape slightly when the substrate binds", ["the active site is completely rigid", "enzymes are lipids", "the substrate is destroyed"], "The binding changes the enzyme shape for a better fit."),
    ("A competitive inhibitor", "has a shape similar to the substrate and blocks the active site", ["binds far from the active site", "increases Vmax", "is an amino acid"], "Its effect is reduced by raising substrate concentration."),
    ("Why does the rate stop rising at high substrate concentration?", "All active sites are occupied; enzyme concentration is limiting", ["The substrate is denatured", "The temperature falls", "The pH changes"], "Vmax is reached."),
    ("Above the optimum temperature, enzyme activity falls because", "the enzyme is denatured", ["substrate molecules become larger", "the peptide bonds join", "water freezes"], "Hydrogen and ionic bonds break and the active site changes shape."),
]
q10 = round(4.1 / 2.0, 2); assert q10 == 2.05
rate = 18 / 45; assert abs(rate - 0.4) < 1e-9
lesson(c1, "enzymes", "Enzymes in depth",
       ["Explain how enzymes lower activation energy using the lock-and-key and induced-fit models.", "Describe the effects of temperature, pH, enzyme and substrate concentration.", "Explain competitive and non-competitive inhibition and calculate rates and Q₁₀."],
       [("definition", "Enzymes as biological catalysts", "Enzymes are **globular proteins** that speed up reactions without being used up. The substrate binds to the **active site**, forming an **enzyme-substrate complex**. This provides a pathway with a lower **activation energy**. In the **induced-fit** model (now preferred over the rigid lock-and-key model) the active site changes shape slightly as the substrate binds."),
        ("pieges", "Denaturation is not 'killing'", "Enzymes are not alive, so they are not 'killed'. High temperature or extreme pH breaks hydrogen and ionic bonds that hold the tertiary structure, so the active site changes shape and the substrate no longer fits (**denaturation**). Low temperature only slows the enzyme (fewer collisions); the enzyme is not denatured and recovers when warmed."),
        ("retenir", "Factors affecting rate", "- **Temperature**: rate rises (more kinetic energy, more collisions) to an optimum, then falls steeply as the enzyme denatures.\n- **pH**: each enzyme has an optimum pH (pepsin about pH 2, amylase near neutral); extremes change ionisation of R groups.\n- **Enzyme concentration**: rate rises in proportion while substrate is in excess.\n- **Substrate concentration**: rate rises then levels off at **Vmax** when active sites are saturated."),
        ("retenir", "Inhibitors", "- **Competitive**: similar shape to the substrate, binds the active site, and the effect is reduced by more substrate; Vmax can still be reached.\n- **Non-competitive**: binds elsewhere (allosteric site), changing the shape of the active site; more substrate does not overcome it, so Vmax is lowered.\nEnd-product inhibition and the effect of cofactors (e.g. Cl⁻ for amylase) are common examples.")],
       [("Worked example — calculating a rate", "In a catalase experiment with liver extract, 18 cm³ of oxygen is collected in 45 seconds. Calculate the mean rate.", ["Rate = volume of product ÷ time.", "Rate = 18 ÷ 45.", "= %.1f cm³ s⁻¹." % rate], "0.4 cm³ s⁻¹"),
        ("Worked example — temperature coefficient", "The rate of an enzyme reaction is 2.0 units at 20 °C and 4.1 units at 30 °C. Calculate Q₁₀.", ["Q₁₀ = rate at (t + 10) ÷ rate at t.", "Q₁₀ = 4.1 ÷ 2.0.", "= %.2f, so the rate roughly doubles for a 10 °C rise." % q10], "Q₁₀ ≈ 2.05")],
       [M("What is the substrate of the enzyme amylase?", "Starch", ["Protein", "Lipid", "Cellulose"], "Amylase hydrolyses starch to maltose."),
        TF("A competitive inhibitor binds at the active site.", True, "It competes with the substrate for the active site."),
        N("Calculate the rate of reaction (cm³ per minute) if 24 cm³ of gas is released in 3 minutes.", 8, "24 ÷ 3 = 8 cm³ min⁻¹.", unit="cm³ min⁻¹")],
       [O("Explain why the rate of an enzyme-controlled reaction levels off as substrate concentration is increased.", "At first more substrate means more collisions and more enzyme-substrate complexes. Eventually all active sites are occupied, so additional substrate cannot increase the rate; enzyme concentration is the limiting factor.", ["More substrate gives more complexes at first (1)", "All active sites saturated (1)", "Enzyme concentration limits (1)"]),
        M("A non-competitive inhibitor lowers Vmax because it", "changes the shape of the active site so fewer enzymes are active", ["competes with substrate directly", "adds more substrate", "lowers the activation energy further"], "Adding substrate does not displace it.", tier="approfondissement")],
       P("An investigation of enzyme X at different temperatures gave these rates (arbitrary units): 10 °C: 1.5; 20 °C: 3.0; 30 °C: 6.0; 40 °C: 9.0; 50 °C: 2.0; 60 °C: 0.",
         [pm("What is the optimum temperature from this data?", "40 °C", ["30 °C", "50 °C", "60 °C"], "Highest rate at 40 °C.", 1),
          pn("Calculate Q₁₀ between 20 °C and 30 °C.", 2, "6.0 ÷ 3.0 = 2.", 0, None, 1),
          po("Explain the rate at 60 °C.", "The enzyme is denatured: hydrogen and ionic bonds break and the active site changes shape, so no enzyme-substrate complexes form.", ["Denatured (1)", "Bonds broken / shape of active site changed (1)"], 2),
          po("Explain why the rate at 10 °C is low even though the enzyme is not denatured.", "At low temperature molecules have less kinetic energy, so there are fewer collisions and fewer enzyme-substrate complexes.", ["Less kinetic energy (1)", "Fewer successful collisions (1)"], 2)]),
       enz_sc, (fig, "Rate against substrate concentration for a fixed enzyme concentration: the rate levels off at Vmax.", "A rising curve that flattens towards a dashed horizontal line labelled Vmax."),
       notes=["Michaelis–Menten constants (Km) are deliberately omitted; check whether the GCE syllabus expects them.", "Q₁₀ as defined here is a standard textbook measure."])

# =====================================================================  CH 2  CELLS
c2 = p.chapter("cells", "Cell structure, membranes and transport", programRef="GCE A Level Biology — Cell structure; cell membranes and transport (to be checked)")

# ---- 6 ultrastructure
fig = shapes([T(215, 18, "Eukaryotic cell (ultrastructure, animal)", 14, bold=True),
              CIRCLE(215, 125, 98, fill="lightyellow", stroke="ink", width=3),
              CIRCLE(215, 125, 28, fill="lightblue", stroke="blue"), CIRCLE(215, 125, 9, fill="blue", stroke="blue"),
              RECT(135, 160, 36, 16, fill="lightorange", stroke="ink", radius=8),
              RECT(262, 155, 36, 16, fill="lightorange", stroke="ink", radius=8),
              LINE(260, 90, 295, 80, width=2), LINE(150, 90, 175, 100, width=2),
              T(60, 130, "ribosomes", 11), T(215, 238, "plasma membrane", 11, color="grey"),
              T(388, 70, "nucleus", 12, anchor="end", color="blue"), LINE(310, 75, 345, 70, color="blue", width=1),
              T(388, 165, "mitochondrion", 12, anchor="end", color="red"), LINE(300, 165, 306, 165, color="red", width=1)], 430, 255)
ultra_sc = [
    ("Which organelle is the site of aerobic respiration?", "Mitochondrion", ["Golgi body", "Ribosome", "Lysosome"], "Krebs cycle in the matrix; electron transport on cristae."),
    ("Prokaryotic cells differ from eukaryotic cells because they have", "no membrane-bound nucleus", ["a nucleus and mitochondria", "80S ribosomes only", "cellulose walls only"], "DNA lies free in the cytoplasm."),
    ("Which organelle modifies and packages proteins into vesicles?", "Golgi apparatus", ["Mitochondrion", "Nucleolus", "Centriole"], "It processes and sorts proteins and lipids."),
    ("The main advantage of the electron microscope over the light microscope is", "greater resolution", ["it shows living cells in colour", "lower cost", "it needs no preparation"], "Electrons have much shorter wavelengths."),
    ("Magnification is calculated as", "image size ÷ actual size", ["actual size ÷ image size", "image size × actual size", "image size − actual size"], "Keep units consistent."),
]
mag = 45000 / 1500; assert mag == 30
act = 60 / 2000 * 1000; assert abs(act - 30) < 1e-9
lesson(c2, "ultrastructure", "Cell ultrastructure and microscopy",
       ["Describe the structure and function of eukaryotic organelles.", "Compare eukaryotic and prokaryotic cells.", "Calculate magnification and actual size; compare light and electron microscopes."],
       [("definition", "Organelles you must know", "- **Nucleus** (nuclear envelope with pores, chromatin, nucleolus): holds DNA, makes mRNA, nucleolus makes ribosomal RNA.\n- **Mitochondrion**: double membrane, folded inner membrane (cristae), matrix; aerobic respiration.\n- **Rough ER**: ribosomes; makes and transports proteins. **Smooth ER**: lipids.\n- **Golgi apparatus**: modifies, packages and sorts.\n- **Lysosome**: hydrolytic enzymes.\n- **Ribosomes** (80S in eukaryotes): translation."),
        ("pieges", "Resolution is not magnification", "**Magnification** is how much bigger the image is than the object. **Resolution** is the smallest distance between two points that can still be seen as separate. A very high magnification without good resolution just gives a bigger blurry picture. The electron microscope has higher resolution than the light microscope because the wavelength of electrons is much shorter than that of light."),
        ("retenir", "Prokaryotes and plants", "**Prokaryotes** (bacteria): no nucleus (circular DNA free in cytoplasm), no membrane-bound organelles, smaller 70S ribosomes, murein cell wall, sometimes plasmids and flagella. Plant cells add a cellulose wall, chloroplasts and a large vacuole bounded by the tonoplast. **Transmission EM** shows thin sections in 2-D; **scanning EM** shows surfaces in 3-D."),
        ("formule", "Magnification", "Magnification = image size ÷ actual size. Convert to the same units first: 1 mm = 1000 µm. Rearranged: actual size = image size ÷ magnification.")],
       [("Worked example — magnification", "In a photomicrograph a liver cell appears 45 mm long; its actual length is 1500 µm. Calculate the magnification.", ["Convert the image to micrometres: 45 mm = 45 000 µm; actual = 1500 µm.", "Magnification = image ÷ actual = 45 000 ÷ 1500.", "= %d." % mag], "×30"),
        ("Worked example — actual size", "A drawing of a mitochondrion is 60 mm long with magnification ×2000. Find its actual length in micrometres.", ["Actual size = image size ÷ magnification.", "Convert: 60 mm = 60 000 µm.", "Actual = 60 000 ÷ 2000 = %d µm." % round(60000 / 2000)], "30 µm")],
       [M("Which organelle contains hydrolytic enzymes to digest worn-out organelles?", "Lysosome", ["Ribosome", "Chloroplast", "Nucleolus"], "Lysosomes contain digestive enzymes."),
        TF("Bacterial cells have a nucleus surrounded by a nuclear envelope.", False, "Prokaryotes have no nucleus; their DNA lies free."),
        N("An image measures 36 mm and the magnification is ×400. Find the actual size in µm.", 36000 / 400, "36 mm = 36 000 µm; 36 000 ÷ 400 = 90 µm.", unit="µm")],
       [O("Explain the difference between magnification and resolution.", "Magnification is the number of times larger the image is than the real object. Resolution is the ability to distinguish two close points as separate; it limits how much detail can be seen, however large the magnification.", ["Magnification defined (1)", "Resolution defined (1)", "Resolution limits useful detail (1)"]),
        M("Why do cells that secrete large amounts of protein, such as pancreatic cells, have abundant rough ER and Golgi bodies?", "Proteins are made on rough ER and then modified and packaged by the Golgi", ["They need extra chloroplasts", "Smooth ER makes enzymes", "The nucleus digests proteins"], "Rough ER → vesicles → Golgi → secretory vesicles.", tier="approfondissement")],
       P("An electron micrograph of a mitochondrion has an image length of 50 mm and a scale bar of 10 mm representing 2 µm.",
         [pn("Calculate the magnification (a number, without units).", 5000, "Scale bar 10 mm = 10 000 µm represents 2 µm: 10 000 ÷ 2 = ×5000.", 0, None, 1),
          pn("Calculate the actual length of the mitochondrion in µm.", 10, "50 mm = 50 000 µm; 50 000 ÷ 5000 = 10 µm.", 0, "µm", 2),
          po("Name two structures visible inside a mitochondrion and state one function.", "The cristae (folded inner membrane) increase surface area for electron transport; the matrix contains enzymes for the Krebs cycle.", ["Two structures (1)", "A function (1)"], 2)]),
       ultra_sc, (fig, "A simplified animal cell showing the nucleus, mitochondria and ribosomes.", "A large circle for the cell with a blue nucleus and nucleolus inside, small orange mitochondria and labelled ribosomes."),
       notes=["The figure is schematic; check unit conventions (mm versus µm) used in magnification questions.", "Ribosome sizes (80S/70S) and the TEM/SEM distinction are standard but check the syllabus depth."])

# ---- 7 membranes and transport
items = [T(215, 16, "Fluid mosaic model of the plasma membrane", 14, bold=True)]
for i in range(9):
    x = 60 + i * 38
    items += [CIRCLE(x, 70, 10, fill="lightorange"), LINE(x - 3, 80, x - 3, 110, width=2), LINE(x + 3, 80, x + 3, 110, width=2),
              CIRCLE(x, 160, 10, fill="lightorange"), LINE(x - 3, 150, x - 3, 120, width=2), LINE(x + 3, 150, x + 3, 120, width=2)]
items += [RECT(143, 55, 36, 120, fill="lightgreen", stroke="green", radius=10), T(161, 118, "channel", 11),
          T(30, 28, "outside", 11, anchor="start", color="grey"), T(30, 200, "cytoplasm", 11, anchor="start", color="grey"),
          T(390, 70, "head: hydrophilic", 11, anchor="end", color="red"), T(390, 118, "tails: hydrophobic", 11, anchor="end", color="blue")]
fig = shapes([it for it in items if not (it["t"] == "circle" and False)], 430, 215)
mem_sc = [
    ("In the fluid mosaic model, the 'fluid' refers to", "phospholipids and proteins that can move sideways", ["a membrane made of water", "a rigid layer of lipid", "protein only"], "Components drift laterally; cholesterol modulates fluidity."),
    ("Facilitated diffusion needs", "a carrier or channel protein but no ATP", ["ATP and a carrier", "a vesicle", "a cell wall"], "Movement is down the concentration gradient."),
    ("Water moves by osmosis from", "a higher water potential to a lower water potential", ["lower to higher water potential", "lower to higher pressure always", "high solute to low solute concentration"], "Pure water has the highest (zero) water potential."),
    ("Active transport differs from diffusion because it", "uses ATP to move substances against a gradient", ["needs no carrier", "moves water only", "is always passive"], "Carrier proteins pump ions such as Na⁺ and K⁺."),
    ("A plant cell placed in pure water becomes", "turgid", ["plasmolysed", "lysed", "shrunken"], "Water enters until the cell wall resists further uptake."),
]
psi = -500 + 200; assert psi == -300
pct = (4.6 - 5.0) / 5.0 * 100; assert abs(pct + 8) < 1e-9
lesson(c2, "membranes-transport", "Membranes and transport across them",
       ["Describe the fluid mosaic structure of the membrane and the role of its components.", "Compare diffusion, facilitated diffusion, osmosis, active transport and bulk transport.", "Use water potential and percentage change in mass to interpret osmosis experiments."],
       [("definition", "The fluid mosaic model", "The plasma membrane is a **phospholipid bilayer** with **proteins** scattered through it (the mosaic) that move sideways (fluid). **Cholesterol** makes the membrane less fluid at high temperature and stops it becoming rigid when cold. **Glycoproteins and glycolipids** act in cell recognition. Channel and carrier proteins let ions and polar molecules cross; small non-polar molecules (O₂, CO₂) cross directly."),
        ("pieges", "Osmosis and water potential", "Osmosis is the net movement of **water** across a partially permeable membrane from higher water potential (less negative) to lower water potential (more negative). Do not say 'water moves to where there is more water'. Pure water has water potential 0 kPa; solutes make it negative. In a plant cell: **ψ = ψs + ψp** (solute potential + pressure potential)."),
        ("retenir", "Methods of transport", "- **Simple diffusion**: down a gradient, no energy (O₂, CO₂).\n- **Facilitated diffusion**: via channel/carrier proteins, no ATP.\n- **Osmosis**: water through the bilayer and aquaporins.\n- **Active transport**: against a gradient using ATP and carriers (sodium-potassium pump).\n- **Endocytosis / exocytosis**: bulk transport in vesicles. Rate of diffusion rises with a steeper gradient, a larger surface area and a shorter distance (Fick's law)."),
        ("methode", "Method: osmosis in potato", "Cut identical cylinders of potato, weigh them, leave them in sucrose solutions of different concentrations for the same time, blot and reweigh. **% change in mass** = (final − initial) ÷ initial × 100. Plot against concentration; where the line crosses 0% the solution has the same water potential as the potato tissue.")],
       [("Worked example — water potential", "A plant cell has solute potential −500 kPa and pressure potential +200 kPa. Calculate its water potential and say which way water moves if it is placed in a solution at −400 kPa.", ["ψ = ψs + ψp = −500 + 200 = %d kPa." % psi, "Solution: −400 kPa; cell: −300 kPa, so the cell has the higher (less negative) water potential.", "Water leaves the cell by osmosis."], "−300 kPa; water leaves the cell"),
        ("Worked example — percentage change", "A potato cylinder of 5.0 g becomes 4.6 g in a sucrose solution. Calculate the percentage change in mass and explain it.", ["Change = 4.6 − 5.0 = −0.4 g.", "%% change = −0.4 ÷ 5.0 × 100 = %d%%." % round(pct), "A fall in mass means water left the tissue: the solution had a lower water potential than the potato."], "−8 %")],
       [M("Which molecules cross the phospholipid bilayer most easily?", "Small non-polar molecules such as oxygen", ["Sodium ions", "Glucose", "Large proteins"], "The hydrophobic core allows non-polar molecules through."),
        TF("Active transport requires ATP.", True, "Energy from ATP drives carrier proteins against the gradient."),
        N("A cell has ψs = −650 kPa and ψp = +350 kPa. Calculate ψ (kPa).", -650 + 350, "ψ = −650 + 350 = −300 kPa.", unit="kPa")],
       [O("Explain why red blood cells burst in pure water but plant cells do not.", "Water enters by osmosis because the cell contents have a lower water potential. Animal cells have no cell wall, so they swell and lyse. A plant cell wall resists expansion, building up pressure potential until water entry stops (turgid).", ["Osmosis into the cell (1)", "No cell wall in animal cell, so it bursts (1)", "Cell wall resists in plant cell (1)"]),
        M("Which feature of the sodium-potassium pump shows it is an active-transport carrier?", "It moves ions against their concentration gradients using ATP", ["It moves ions down gradients", "It needs no protein", "It moves only water"], "It pumps 3 Na⁺ out and 2 K⁺ in per ATP.", tier="approfondissement")],
       P("Cassava cylinders of mass 6.0 g each were placed in sucrose solutions. After 1 hour the masses were: 0.0 M: 6.6 g; 0.2 M: 6.3 g; 0.4 M: 6.0 g; 0.6 M: 5.5 g.",
         [pn("Calculate the % change in mass at 0.6 M (use a negative sign for a decrease, one decimal place).", round((5.5 - 6.0) / 6.0 * 100, 1), "(5.5 − 6.0) ÷ 6.0 × 100 = −8.3 %.", 0.1, "%", 2),
          pm("At which concentration is the water potential of the solution equal to that of the cassava cells?", "0.4 M", ["0.0 M", "0.2 M", "0.6 M"], "No change in mass at 0.4 M.", 1),
          po("Explain the increase in mass at 0.0 M.", "Pure water has a higher water potential than the cells, so water enters by osmosis and the mass increases.", ["Osmosis (1)", "Higher water potential outside (1)"], 2)]),
       mem_sc, (fig, "The fluid mosaic model: a phospholipid bilayer with a protein channel.", "Two rows of phospholipid heads with tails pointing inwards and a green protein channel crossing the bilayer."),
       notes=["Na⁺/K⁺ pump stoichiometry (3:2) is standard; check whether it is required.", "The percentage change in mass sign convention should match the exam practice."])
assert round((5.5 - 6.0) / 6.0 * 100, 1) == -8.3

# =====================================================================  CH 3  CELL DIVISION
c3 = p.chapter("division", "The cell cycle, mitosis and meiosis", programRef="GCE A Level Biology — Cell cycle; mitosis; meiosis (to be checked)")

# ---- 8 mitosis
fig = hflow(["Interphase\nG1, S, G2", "Prophase", "Metaphase", "Anaphase", "Telophase"], w=470, size=11, gap=14, title="The cell cycle: interphase then mitosis")
mi_sc = [
    ("DNA replication occurs during", "the S phase of interphase", ["prophase", "anaphase", "cytokinesis"], "S = synthesis."),
    ("Chromosomes line up on the equator during", "metaphase", ["prophase", "telophase", "interphase"], "Spindle fibres attach to centromeres."),
    ("Sister chromatids separate in", "anaphase", ["prophase", "metaphase", "telophase"], "Centromeres divide and chromatids are pulled to opposite poles."),
    ("Mitosis produces", "two genetically identical diploid cells", ["four haploid cells", "two haploid gametes", "cells with crossing over"], "Used for growth, repair and asexual reproduction."),
    ("The mitotic index is", "cells in mitosis ÷ total cells", ["cells in interphase ÷ cells in mitosis", "total cells × 100", "chromosome number ÷ 2"], "Often expressed as a percentage."),
]
mi = 18 / 150 * 100; assert abs(mi - 12) < 1e-9
lesson(c3, "mitosis", "The cell cycle and mitosis",
       ["Describe the stages of the cell cycle and of mitosis.", "Calculate the mitotic index and explain its use.", "Explain the role of mitosis and the link with cancer."],
       [("definition", "The cell cycle", "The **cell cycle** is the sequence of events from one cell division to the next. **Interphase** has **G1** (cell growth, protein synthesis), **S** (DNA replication) and **G2** (preparation, organelles doubled). **Mitosis** follows, then **cytokinesis**. Checkpoints control progress; cells that stop dividing enter G0."),
        ("pieges", "Chromosome or chromatid?", "After replication each chromosome consists of **two sister chromatids** joined at the centromere. In anaphase the chromatids separate and each is then called a chromosome. The chromosome number does not double at the start of mitosis; it is the amount of DNA that doubled in S phase. Mitosis gives cells with the **same** number of chromosomes as the parent (2n → 2n)."),
        ("retenir", "Stages of mitosis", "- **Prophase**: chromosomes condense; nuclear envelope breaks; spindle forms.\n- **Metaphase**: chromosomes line up at the equator, attached by centromeres.\n- **Anaphase**: centromeres divide; chromatids pulled to poles.\n- **Telophase**: nuclear envelopes reform; chromosomes uncoil.\n- **Cytokinesis**: cytoplasm divides (cleavage furrow in animals, cell plate in plants)."),
        ("retenir", "Roles and cancer", "Mitosis gives growth, tissue repair and asexual reproduction (e.g. cassava stem cuttings). **Cancer** is uncontrolled division caused by mutation in genes controlling the cell cycle. The **mitotic index** (cells in mitosis ÷ total cells) is higher in tumours and in growing root tips.")],
       [("Worked example — mitotic index", "In a root-tip squash, 150 cells are counted and 18 are in mitosis. Calculate the mitotic index.", ["Mitotic index = cells in mitosis ÷ total cells.", "= 18 ÷ 150 = 0.12.", "As a percentage: %d %%." % round(mi)], "0.12 (12 %)"),
        ("Worked example — DNA content", "A human diploid cell has 46 chromosomes. How many chromatids are present at metaphase, and how many chromosomes in each daughter cell?", ["At metaphase each of the 46 chromosomes has two chromatids: 46 × 2 = %d." % (46 * 2), "Mitosis gives genetically identical cells.", "Each daughter cell has 46 chromosomes."], "92 chromatids; 46 chromosomes per daughter")],
       [M("In which stage does the nuclear envelope reform?", "Telophase", ["Prophase", "Metaphase", "Anaphase"], "Telophase reverses prophase."),
        TF("Mitosis produces four haploid cells.", False, "That describes meiosis; mitosis produces two diploid daughter cells."),
        N("In a sample of 200 root-tip cells, 30 are in mitosis. Calculate the mitotic index as a percentage.", 15, "30 ÷ 200 × 100 = 15 %.", unit="%")],
       [O("Explain why chromosomes must condense before they are separated in mitosis.", "Condensed chromosomes are short and compact, so they can be moved by the spindle without tangling or breaking; they are also easy to line up on the equator.", ["Short/compact (1)", "Prevents tangling or breakage (1)", "Allows accurate separation (1)"]),
        M("A drug that prevents spindle formation would stop the cell cycle at", "metaphase/anaphase, since chromosomes cannot be moved", ["S phase, since DNA cannot be copied", "G1, since no proteins can be made", "cytokinesis only"], "Spindle fibres align and separate chromosomes.", tier="approfondissement")],
       P("A student counted cells in an onion root tip: Interphase 120; Prophase 18; Metaphase 6; Anaphase 4; Telophase 2.",
         [pn("How many cells were counted altogether?", 150, "120 + 18 + 6 + 4 + 2 = 150.", 0, None, 1),
          pn("Calculate the mitotic index as a percentage.", 20, "30 ÷ 150 × 100 = 20 %.", 0.01, "%", 2),
          po("Explain why most cells were in interphase.", "Interphase is the longest part of the cell cycle; the time spent in each stage is proportional to the number of cells seen in it.", ["Interphase lasts longest (1)", "Cells seen ∝ time (1)"], 2)]),
       mi_sc, (fig, "The cell cycle: interphase followed by the four stages of mitosis.", "Five boxes in a row: interphase, prophase, metaphase, anaphase and telophase, joined by arrows."),
       notes=["The squash counts are invented teaching data, not from a real experiment."])

# ---- 9 meiosis
fig = vflow(["Diploid cell (2n) — DNA replicated", "Meiosis I: homologues separate (2 cells, n)", "Meiosis II: chromatids separate", "Four genetically different haploid cells"], w=420, size=12, bw=340, title="Meiosis in outline")
mei_sc = [
    ("Meiosis halves the chromosome number because", "homologous chromosomes separate in meiosis I", ["DNA is replicated twice", "chromatids separate only once", "no spindle is formed"], "Reduction division in meiosis I."),
    ("Crossing over occurs between", "non-sister chromatids of homologous chromosomes", ["sister chromatids", "different species", "centromeres"], "It produces new allele combinations on a chromosome."),
    ("Independent assortment is the", "random orientation of bivalents at metaphase I", ["pairing of homologues", "copying of DNA", "joining of gametes"], "Maternal and paternal chromosomes are shuffled."),
    ("How many cells does one meiosis produce?", "Four", ["Two", "Eight", "One"], "Two divisions, one DNA replication."),
    ("Non-disjunction can cause", "Down syndrome (trisomy 21)", ["cystic fibrosis", "malaria", "diabetes insipidus"], "An extra copy of chromosome 21."),
]
comb = 2 ** 23; assert comb == 8388608
lesson(c3, "meiosis", "Meiosis and genetic variation",
       ["Describe the stages of meiosis and the roles of the two divisions.", "Explain how meiosis produces genetic variation.", "Compare mitosis and meiosis."],
       [("definition", "Meiosis", "**Meiosis** is the cell division that produces four **haploid** (n) gametes or spores from a **diploid** (2n) cell. DNA is replicated once, but the nucleus divides twice. In **meiosis I** homologous chromosomes pair (forming **bivalents**) and separate; in **meiosis II** sister chromatids separate, as in mitosis."),
        ("pieges", "Homologues versus chromatids", "In meiosis I it is **homologous chromosomes** that separate (one maternal, one paternal), not chromatids. The cells at the end of meiosis I are already haploid, but each chromosome still has two chromatids. Chromatids separate in meiosis II. Do not confuse **crossing over** (exchange between non-sister chromatids in prophase I) with independent assortment (random orientation in metaphase I)."),
        ("retenir", "Sources of variation", "- **Crossing over** at chiasmata: new combinations of alleles on each chromosome.\n- **Independent assortment**: for n pairs of chromosomes there are 2ⁿ possible combinations (for humans 2²³ = 8 388 608) before crossing over is counted.\n- **Random fertilisation**: any sperm can fuse with any egg.\n- Mutation provides new alleles."),
        ("retenir", "Mitosis versus meiosis", "Mitosis: one division, two cells, diploid, identical, growth and repair. Meiosis: two divisions, four cells, haploid, genetically different, makes gametes; homologues pair and cross over. **Non-disjunction** (failure of chromosomes to separate) gives gametes with an extra or missing chromosome, e.g. trisomy 21.")],
       [("Worked example — combinations", "An organism has 4 pairs of chromosomes (2n = 8). How many different combinations of chromosomes can its gametes have by independent assortment alone?", ["Each pair can orient in 2 ways.", "For 4 pairs the number is 2⁴.", "2 × 2 × 2 × 2 = %d." % 2 ** 4], "16"),
        ("Worked example — chromosome numbers", "A cell of a maize plant has 20 chromosomes (2n = 20). Give the number of chromosomes in a cell after meiosis I, after meiosis II, and in the zygote.", ["Meiosis I halves the number: 20 → 10 per cell.", "Meiosis II separates chromatids, so each cell still has 10 chromosomes (now single).", "Two gametes with 10 chromosomes fuse: zygote 10 + 10 = 20."], "10; 10; 20")],
       [M("In which stage do homologous chromosomes separate?", "Anaphase I", ["Anaphase II", "Prophase II", "Telophase II"], "Meiosis I separates homologues."),
        TF("Meiosis produces genetically identical cells.", False, "Crossing over and independent assortment make the four cells different."),
        N("An organism has 2n = 12 chromosomes. How many different combinations of chromosomes can its gametes have by independent assortment (ignoring crossing over)?", 2 ** 6, "n = 6 pairs, 2⁶ = 64.")],
       [O("Describe two ways in which meiosis produces genetic variation.", "Crossing over exchanges alleles between non-sister chromatids in prophase I. Independent assortment orients bivalents randomly at metaphase I, so maternal and paternal chromosomes are shuffled into the gametes.", ["Crossing over described (1)", "Independent assortment described (1)", "Both at correct stage (1)"]),
        M("A woman produces an egg with 24 chromosomes instead of 23 because of non-disjunction. The zygote after fertilisation by a normal sperm has", "47 chromosomes", ["46 chromosomes", "45 chromosomes", "48 chromosomes"], "24 + 23 = 47.", tier="approfondissement")],
       P("The chromosome number of a cell of a crop plant is 2n = 14.",
         [pn("How many chromosomes are in each gamete?", 7, "Haploid number n = 14 ÷ 2 = 7.", 0, None, 1),
          pn("How many combinations of chromosomes are possible in the gametes by independent assortment?", 2 ** 7, "2⁷ = 128.", 0, None, 2),
          po("State two differences between mitosis and meiosis.", "Mitosis: one division giving two identical diploid cells; meiosis: two divisions giving four genetically different haploid cells.", ["Two valid differences (2)"], 2)]),
       mei_sc, (fig, "Meiosis in outline: from one diploid cell to four haploid cells.", "Four boxes in a vertical chain from a diploid cell through meiosis I and II to four haploid cells."),
       notes=["Down syndrome is given as an example of non-disjunction; keep the health wording neutral and respectful."])
assert 2 ** 7 == 128

# =====================================================================  CH 4  NUCLEIC ACIDS
c4 = p.chapter("nucleic", "Nucleic acids, replication and protein synthesis", programRef="GCE A Level Biology — Nucleic acids; DNA replication; protein synthesis (to be checked)")

# ---- 10 DNA structure and replication
it = [T(215, 16, "DNA: two antiparallel strands, complementary bases", 14, bold=True),
      LINE(110, 36, 110, 215, color="blue", width=5), LINE(320, 36, 320, 215, color="blue", width=5),
      T(60, 126, "sugar-", 11), T(60, 140, "phosphate", 11), T(372, 126, "sugar-", 11), T(372, 140, "phosphate", 11)]
for i, (a, b, n) in enumerate([("A", "T", 2), ("G", "C", 3), ("T", "A", 2), ("C", "G", 3)]):
    y = 55 + i * 46
    it += [LINE(112, y, 318, y, color="red", width=2, dash=True), RECT(118, y - 12, 26, 24, fill="lightyellow"), T(131, y + 5, a, 14, bold=True),
           RECT(286, y - 12, 26, 24, fill="lightgreen"), T(299, y + 5, b, 14, bold=True), T(215, y - 5, "%d H-bonds" % n, 11, color="red")]
fig = shapes(it, 430, 235)
rep_sc = [
    ("DNA replication is described as semi-conservative because", "each new molecule has one old and one new strand", ["both strands are new", "both strands are old", "the DNA is destroyed"], "Shown by the Meselson–Stahl experiment."),
    ("Which enzyme joins nucleotides to the growing DNA strand?", "DNA polymerase", ["Helicase", "RNA polymerase", "Amylase"], "It adds nucleotides to the 3′ end."),
    ("Which enzyme unwinds and separates the DNA strands?", "Helicase", ["DNA ligase", "DNA polymerase", "Lipase"], "It breaks hydrogen bonds between bases."),
    ("In DNA, adenine pairs with", "thymine, by two hydrogen bonds", ["guanine, by three hydrogen bonds", "cytosine by two hydrogen bonds", "uracil by three hydrogen bonds"], "A–T and G–C."),
    ("In RNA, adenine pairs with", "uracil", ["thymine", "cytosine", "guanine"], "RNA contains uracil instead of thymine."),
]
c_pct = (100 - 2 * 28) / 2; assert c_pct == 22
frac = 2 / 2 ** 3; assert frac == 0.25
lesson(c4, "dna-replication", "DNA structure and replication",
       ["Describe the structure of a nucleotide and of the DNA double helix.", "Apply complementary base pairing and Chargaff's rules.", "Explain semi-conservative replication and the roles of the enzymes."],
       [("definition", "Nucleotides and the double helix", "A **nucleotide** has a pentose sugar (deoxyribose in DNA, ribose in RNA), a phosphate group and a nitrogenous base. Nucleotides join by **phosphodiester bonds** (condensation) into a sugar-phosphate backbone. DNA is a **double helix** of two **antiparallel** strands held by hydrogen bonds between complementary bases: **A–T** (two bonds) and **G–C** (three bonds). The bases are A, T, G, C; RNA has **uracil (U)** instead of T."),
        ("pieges", "Purines and pyrimidines; RNA vs DNA", "**Purines** (A, G) have two rings; **pyrimidines** (C, T, U) have one ring. A purine always pairs with a pyrimidine, so the helix has constant width. DNA is double-stranded, with deoxyribose and thymine; RNA is usually single-stranded with ribose and uracil. In DNA the amount of A equals T and the amount of G equals C (**Chargaff's rule**); this is not true for single-stranded RNA."),
        ("retenir", "Semi-conservative replication", "1. **Helicase** unwinds and breaks hydrogen bonds between bases.\n2. Each strand is a template; free nucleotides pair by complementary bases.\n3. **DNA polymerase** joins nucleotides, always at the 3′ end (so the leading strand is continuous and the lagging strand is made in short **Okazaki fragments**).\n4. **DNA ligase** joins fragments. Each new DNA molecule has one old and one new strand."),
        ("retenir", "Evidence", "In the **Meselson–Stahl** experiment bacteria grown in heavy nitrogen (¹⁵N) were moved to light nitrogen (¹⁴N). After one generation all DNA was of intermediate density, after two generations half intermediate and half light. This fits the semi-conservative model and rules out the conservative one.")],
       [("Worked example — base proportions", "A DNA sample contains 28 % adenine. Work out the percentage of each of the other bases.", ["A = T, so thymine is also 28 %.", "A + T = 56 %, so G + C = 100 − 56 = 44 %.", "G = C, so each is 44 ÷ 2 = %d %%." % c_pct], "T 28 %, G 22 %, C 22 %"),
        ("Worked example — replication and 15N", "A DNA molecule with both strands labelled ¹⁵N replicates three times in ¹⁴N medium. What fraction of the molecules contain any ¹⁵N?", ["After 3 rounds there are 2³ = 8 DNA molecules.", "The two original ¹⁵N strands end up in only two molecules.", "Fraction = 2 ÷ 8 = %.2f (25 %%)." % frac], "0.25")],
       [M("Which bond joins nucleotides in one strand of DNA?", "Phosphodiester bond", ["Hydrogen bond", "Peptide bond", "Glycosidic bond between bases"], "Sugar-phosphate backbone."),
        TF("Guanine pairs with cytosine by three hydrogen bonds.", True, "G–C has three, A–T two."),
        N("A DNA molecule has 1000 base pairs and 300 of the bases are adenine. How many guanine bases are present?", (2000 - 2 * 300) // 2, "Total bases = 2000; A = T = 300 each, so A + T = 600; G + C = 1400, so G = 700.")],
       [O("Explain why DNA replication is called semi-conservative.", "Each of the two new molecules contains one original strand and one newly made strand, because each parent strand acts as a template.", ["Each molecule: one old strand (1)", "One new strand (1)", "Parent strand used as template (1)"]),
        M("Why is the lagging strand made in fragments?", "DNA polymerase can only add nucleotides to the 3′ end, so it works away from the fork in short pieces", ["Helicase cuts it into pieces", "RNA polymerase cannot copy it", "It is made of RNA"], "Fragments are later joined by ligase.", tier="approfondissement")],
       P("A scientist analysed the DNA of a bacterium: 1200 base pairs, with 24 % guanine.",
         [pn("How many nucleotides (bases) are present in the double helix altogether?", 2400, "1200 pairs × 2 = 2400.", 0, None, 1),
          pn("How many guanine bases are present?", 576, "24 % of 2400 = 576.", 0, None, 2),
          pn("What is the percentage of adenine?", 26, "G = C = 24 %, so A + T = 52 %, A = 26 %.", 0, "%", 2)]),
       rep_sc, (fig, "DNA ladder model: sugar-phosphate backbones and complementary base pairs joined by hydrogen bonds.", "Two vertical blue backbones joined by four dashed red rungs labelled with base pairs A-T, G-C, T-A, C-G."),
       notes=["Direction labels 3′/5′ are standard; check how much detail is expected.", "Uses ¹⁵N / ¹⁴N and Meselson–Stahl results as a standard textbook description."])

# ---- 11 protein synthesis
fig = hflow(["DNA\n(template)", "mRNA\n(codons)", "Polypeptide\n(amino acids)"], w=430, size=12, gap=44, title="Transcription, then translation")
fig["items"] += [T(150, 118, "transcription", 11, color="red"), T(298, 118, "translation", 11, color="red")]
fig["h"] = 130
ps_sc = [
    ("Transcription takes place in", "the nucleus of a eukaryotic cell", ["the ribosome", "the Golgi body", "the lysosome"], "mRNA is made on the DNA template in the nucleus."),
    ("The anticodon is found on", "tRNA", ["mRNA", "DNA", "rRNA"], "It pairs with the mRNA codon."),
    ("The genetic code is described as degenerate because", "most amino acids are coded for by more than one codon", ["it differs between species", "codons overlap", "it has no start codon"], "64 codons code for 20 amino acids and stop signals."),
    ("Which codon is the usual start codon?", "AUG", ["UAA", "GGG", "UUU"], "AUG codes for methionine."),
    ("A frameshift mutation is caused by", "the insertion or deletion of a base", ["substitution of one base by another only", "a change in the ribosome", "loss of a tRNA"], "The reading frame shifts, changing every codon afterwards."),
]
n_codons = 150 // 3; assert n_codons == 50
lesson(c4, "protein-synthesis", "Protein synthesis and gene mutations",
       ["Describe transcription and translation.", "Use the genetic code to work out mRNA and amino-acid sequences.", "Explain the effects of gene mutations such as the sickle-cell substitution."],
       [("definition", "From gene to protein", "A **gene** is a sequence of DNA bases coding for a polypeptide. In **transcription** RNA polymerase makes a single-stranded **mRNA** copy of the **template strand**, using complementary bases (A pairs with U). In eukaryotes the primary transcript is **spliced**: non-coding **introns** are removed and **exons** joined. mRNA leaves the nucleus through a nuclear pore."),
        ("pieges", "Codons, anticodons and the template strand", "A **codon** is three bases on mRNA; an **anticodon** is the complementary three bases on tRNA. The mRNA has the same sequence as the *coding* (non-template) strand, with U for T. So if the template is TAC the mRNA codon is AUG and the tRNA anticodon is UAC. Always write the three types clearly and say which strand you are reading."),
        ("retenir", "Translation", "The mRNA binds to a **ribosome** (rRNA and protein). Each **tRNA** carries a specific amino acid and has an anticodon. The ribosome holds two tRNAs at a time, and a **peptide bond** forms between their amino acids. The ribosome moves along the mRNA codon by codon from the start codon **AUG** until a **stop codon** (UAA, UAG, UGA) is reached."),
        ("retenir", "Properties of the code and mutations", "The code is a **triplet** code, **degenerate** (several codons per amino acid), **non-overlapping** and almost **universal**. **Substitution** can be silent, missense or nonsense. **Insertion or deletion** of bases shifts the reading frame (frameshift). In sickle-cell disease a single substitution in the haemoglobin β-chain gene changes glutamic acid to valine, so haemoglobin S forms fibres in low oxygen.")],
       [("Worked example — gene to polypeptide", "The template strand reads TAC CGA AAA. Use these codons: AUG = Met, GCU = Ala, UUU = Phe. Give the mRNA and the amino acids.", ["Complementary bases (T→A, A→U, C→G, G→C): TAC → AUG; CGA → GCU; AAA → UUU.", "mRNA: AUG GCU UUU.", "Read the codons: Met – Ala – Phe."], "mRNA AUG GCU UUU; Met-Ala-Phe"),
        ("Worked example — counting codons", "A coding region of an mRNA is 150 bases long and ends with a stop codon. How many amino acids does the polypeptide contain?", ["Number of codons = 150 ÷ 3 = %d." % n_codons, "The last codon is a stop codon and does not code for an amino acid.", "Amino acids = %d − 1 = %d." % (n_codons, n_codons - 1)], "49")],
       [M("Which RNA carries amino acids to the ribosome?", "tRNA", ["mRNA", "rRNA", "DNA"], "Each tRNA carries one specific amino acid."),
        TF("The mRNA sequence is complementary to the template strand.", True, "It is made by complementary base pairing on the template strand."),
        N("An mRNA coding sequence has 90 bases excluding the stop codon. How many amino acids does it code for?", 30, "90 ÷ 3 = 30.")],
       [O("Explain why a deletion of one base usually has a more severe effect than a substitution.", "A deletion shifts the reading frame so every codon after the deletion changes, giving a different amino-acid sequence, often with an early stop codon. A substitution changes only one codon, and because of the degenerate code may change nothing.", ["Frameshift (1)", "All later codons altered (1)", "Substitution affects one codon / may be silent (1)"]),
        M("Sickle-cell haemoglobin is caused by", "a base substitution that changes one amino acid", ["a deletion of the whole gene", "extra chromosomes", "a bacterial infection"], "Glu → Val in the β-chain.", tier="approfondissement")],
       P("The mRNA sequence AUG GAA UGG UAA is translated (codons: AUG Met; GAA Glu; UGG Trp; UAA stop).",
         [pm("What is the first amino acid?", "Methionine", ["Glutamic acid", "Tryptophan", "Stop"], "AUG = Met.", 1),
          pn("How many amino acids are in the polypeptide?", 3, "Met, Glu, Trp; UAA is a stop.", 0, None, 1),
          pm("Which template DNA triplet gave the mRNA codon GAA?", "CTT", ["GAA", "CUU", "TTC"], "Template bases are complementary to the codon: G→C, A→T, A→T.", 1),
          po("Name the molecule that carries tryptophan and the part of it that pairs with the codon.", "tRNA; its anticodon (ACC).", ["tRNA (1)", "Anticodon (1)"], 2)]),
       ps_sc, (fig, "Genetic information flows from DNA to mRNA (transcription) to a polypeptide (translation).", "Three boxes joined by arrows: DNA, mRNA and polypeptide, labelled transcription and translation."),
       notes=["Codon assignments used (AUG Met, GCU Ala, UUU Phe, GAA Glu, UGG Trp, stop codons) are standard; the question set uses given codons.", "Sickle-cell: Glu to Val at position 6 of the β-globin chain is standard; the position is not stated in the text."])

# =====================================================================  CH 5  CLASSIFICATION
c5 = p.chapter("classification", "Classification and biodiversity", programRef="GCE A Level Biology — Classification; biodiversity (to be checked)")

# ---- 12 classification
fig = vflow(["Domain", "Kingdom", "Phylum", "Class", "Order", "Family", "Genus", "Species"], w=430, size=12, bw=130, bh=24, gap=8,
            notes=["Eukarya", "Animalia", "Chordata", "Mammalia", "Primates", "Hominidae", "Homo", "H. sapiens"], title="Taxonomic hierarchy (human)")
cl_sc = [
    ("The correct binomial format is", "Genus species, e.g. Theobroma cacao", ["species genus, e.g. cacao Theobroma", "Both words begin with a lower-case letter", "Only the genus is written"], "Genus with a capital, species lowercase, both italic."),
    ("Which domain contains bacteria?", "Bacteria", ["Archaea", "Eukarya", "Fungi"], "Three domains: Archaea, Bacteria, Eukarya."),
    ("Which is the correct order from largest to smallest taxon?", "Domain, kingdom, phylum, class, order, family, genus, species", ["Domain, phylum, kingdom, class, order, family, genus, species", "Species, genus, family, order, class, phylum, kingdom, domain", "Kingdom, domain, phylum, class, order, genus, family, species"], "Remember the sequence DKPCOFGS."),
    ("Which provides strong evidence for how closely two species are related?", "Similarity of DNA or protein sequences", ["Similar colour", "Same habitat only", "Similar size"], "Molecular evidence is objective."),
    ("Fungi differ from plants because they", "have chitin cell walls and absorb nutrients", ["photosynthesise", "have cellulose walls", "have chloroplasts"], "They are heterotrophs."),
]
lesson(c5, "classification", "Classification of living things",
       ["State the taxonomic hierarchy and the binomial system.", "Describe the features of the three domains and the five kingdoms.", "Explain why molecular evidence is used in classification."],
       [("definition", "Taxonomy", "**Classification** places organisms in groups based on shared features and evolutionary relationships. The hierarchy is **domain, kingdom, phylum, class, order, family, genus, species**. A **species** is a group of similar organisms that can interbreed to produce fertile offspring. The **binomial system** gives each species a two-part Latin name: **genus** (capital) and **species** (lower case), e.g. *Elaeis guineensis*, the oil palm."),
        ("pieges", "Species definition has limits", "The 'interbreed to give fertile offspring' definition fails for organisms that reproduce asexually, for fossils and for some hybrids. Do not write the binomial with capitals on both words or without italics or underlining. Do not call a group 'a species' just because the organisms look alike. Modern classification is **phylogenetic**: it uses evidence of common ancestry, including DNA sequencing."),
        ("retenir", "Domains and kingdoms", "- **Archaea**: prokaryotes, often in extreme habitats.\n- **Bacteria**: prokaryotes with murein walls.\n- **Eukarya** includes four kingdoms: **Protoctista** (e.g. *Plasmodium*, algae), **Fungi** (chitin walls, absorb food), **Plantae** (cellulose walls, photosynthesis) and **Animalia** (no walls, heterotrophic, nervous coordination)."),
        ("retenir", "Examples from Cameroon", "- Oil palm: *Elaeis guineensis* (Plantae).\n- Cocoa: *Theobroma cacao* (Plantae).\n- African grey parrot: *Psittacus erithacus* (Animalia, Aves).\n- Malaria parasite: *Plasmodium falciparum* (Protoctista).\n- Mushrooms such as oyster mushrooms are Fungi.\nViruses are not living cells and do not fit into the three domains.")],
       [("Worked example — classify an organism", "An organism has a nucleus, cellulose cell walls and chloroplasts, and makes its own food. Name its domain and kingdom.", ["A nucleus shows it is eukaryotic: domain Eukarya.", "Cellulose walls and chloroplasts with autotrophic feeding match plants.", "Kingdom Plantae."], "Eukarya; Plantae"),
        ("Worked example — write a name", "The oil palm belongs to genus Elaeis, species guineensis. Write the correct binomial and say how it is printed.", ["Genus first with a capital letter: Elaeis.", "Species second with a lower-case initial: guineensis.", "Printed in italics (underlined when handwritten)."], "Elaeis guineensis")],
       [M("Which kingdom has cells with chitin walls that absorb food?", "Fungi", ["Plantae", "Animalia", "Protoctista"], "Fungi are heterotrophs with chitin walls."),
        TF("The genus name begins with a lower-case letter in a binomial.", False, "The genus has a capital; the species name is lower case."),
        M("Which is a prokaryote?", "A bacterium", ["A mushroom", "An oil palm", "A parrot"], "Bacteria lack a nucleus.")],
       [O("Why is the biological species concept difficult to apply to bacteria?", "Bacteria reproduce asexually (binary fission), so the idea of interbreeding to give fertile offspring does not apply. They also exchange genes by conjugation across groups.", ["Asexual reproduction (1)", "Interbreeding idea not applicable (1)", "Gene exchange (1)"]),
        M("What does DNA sequencing add to classification?", "An objective measure of how recently species shared a common ancestor", ["It proves species never change", "It only classifies animals", "It shows the colour of organisms"], "More similar sequences indicate closer relationship.", tier="approfondissement")],
       P("A biologist from the University of Buea records two organisms from Mount Cameroon: organism X has a notochord, hair and milk glands; organism Y has chloroplasts and cellulose walls.",
         [pm("Which kingdom is X?", "Animalia", ["Plantae", "Fungi", "Protoctista"], "Hair and milk glands show mammals.", 1),
          pm("Which kingdom is Y?", "Plantae", ["Animalia", "Fungi", "Protoctista"], "Chloroplasts and cellulose walls.", 1),
          po("Give the two parts of a binomial name and explain why such names are useful.", "Genus and species. They are used internationally, so scientists are sure of the organism despite different local names.", ["Genus and species (1)", "Universal (1)"], 2)]),
       cl_sc, (fig, "The taxonomic hierarchy, with the human as an example.", "A vertical list of eight boxes from domain to species with the human taxa written to the right."),
       notes=["The five-kingdom scheme is used in many A Level syllabi even though three domains are also described; check which is preferred by the GCE Board.", "Species names given are standard scientific names."])

# ---- 13 biodiversity
fig = bars([("Sp A", 20), ("Sp B", 10), ("Sp C", 5), ("Sp D", 3), ("Sp E", 2)], w=400, h=240)
bd_sc = [
    ("Species richness is", "the number of different species in an area", ["the total number of individuals", "the mass of an ecosystem", "the genetic variation within one species"], "It ignores how many individuals there are of each species."),
    ("A high Simpson's index of diversity (close to 1) indicates", "a diverse community with no species dominating", ["a community with a single species", "no species at all", "polluted water"], "D near 0 means low diversity."),
    ("Which is an in situ conservation method?", "A national park", ["A seed bank", "A zoo breeding programme", "Frozen embryos"], "In situ = in the natural habitat."),
    ("Deforestation reduces biodiversity mainly through", "habitat loss", ["increased predators only", "more rainfall", "more species"], "Species lose their habitat and food."),
    ("Genetic diversity within a species is increased by", "mutation and gene flow", ["inbreeding", "bottleneck events", "clonal reproduction"], "New alleles raise genetic diversity."),
]
counts = [20, 10, 5, 3, 2]; N_ = sum(counts); D = 1 - sum((n / N_) ** 2 for n in counts)
assert N_ == 40 and abs(D - 0.66375) < 1e-9
D1 = 1 - (10 / 10) ** 2; assert D1 == 0
lesson(c5, "biodiversity", "Biodiversity and its measurement",
       ["Define biodiversity at three levels.", "Calculate Simpson's index of diversity and interpret it.", "Describe threats to biodiversity and methods of conservation."],
       [("definition", "Three levels of biodiversity", "**Biodiversity** is the variety of life. It has three levels: **species diversity** (number and relative abundance of species), **genetic diversity** (variety of alleles within a species) and **ecosystem diversity** (variety of habitats). **Species richness** is the number of species; **evenness** is how equal their abundances are."),
        ("formule", "Simpson's index of diversity", "D = 1 − Σ(n ÷ N)², where **n** is the number of individuals of one species and **N** is the total number of individuals of all species. D lies between 0 (no diversity) and 1 (maximum diversity). A high value means a stable, diverse community."),
        ("pieges", "Richness is not diversity", "Two communities can have the same number of species but very different diversity. A field with 10 species where one species makes up 95 % of the individuals has lower diversity than a field with 10 species of equal numbers. Always count **individuals** of every species and use a **random** sampling method. Report that the formula used (and sample size) can affect the value."),
        ("retenir", "Threats and conservation", "Threats: **habitat destruction** (logging, farming, mining, urban growth), over-harvesting and bushmeat hunting, pollution, invasive species, climate change. **In situ** conservation: national parks and reserves (e.g. Korup National Park, Waza National Park, Dja Faunal Reserve). **Ex situ**: zoos, botanic gardens, seed banks, captive breeding. Community involvement and law enforcement are essential.")],
       [("Worked example — Simpson's index", "Five species in a sample of forest floor have 20, 10, 5, 3 and 2 individuals. Calculate D.", ["N = 20 + 10 + 5 + 3 + 2 = %d." % N_, "Σ(n ÷ N)² = 0.5² + 0.25² + 0.125² + 0.075² + 0.05² = %.5f." % sum((n / N_) ** 2 for n in counts), "D = 1 − %.5f = %.2f (2 d.p.)." % (sum((n / N_) ** 2 for n in counts), D)], "D ≈ 0.66"),
        ("Worked example — one species only", "A field of maize has 50 plants of a single species. Calculate D.", ["N = 50 and n = 50, so n ÷ N = 1.", "Σ(n ÷ N)² = 1² = 1.", "D = 1 − 1 = 0, meaning no diversity."], "D = 0")],
       [M("Which is a measure of genetic diversity?", "The number of different alleles of a gene in a population", ["The number of species in a lake", "The number of habitats in a country", "The total mass of plants"], "Genetic diversity is within a species."),
        TF("A Simpson's index of 0 means maximum diversity.", False, "D = 0 means no diversity (one species)."),
        N("In a sample, species P has 6 individuals and species Q has 4. Calculate D, to 2 decimal places.", round(1 - (0.6 ** 2 + 0.4 ** 2), 2), "N = 10; Σ(n/N)² = 0.36 + 0.16 = 0.52; D = 0.48.", tol=0.005)],
       [O("Explain why a conservation scheme should protect habitats rather than only individual animals.", "Habitats provide food, shelter and breeding sites for many species together; protecting the habitat protects the whole community and the interactions between species, and keeps populations large enough to retain genetic diversity.", ["Habitat supports many species (1)", "Interactions/food webs preserved (1)", "Large populations keep genetic diversity (1)"]),
        M("Which sample is more diverse?", "Site 2: 5 species with 10 individuals each", ["Site 1: 5 species, 46 individuals of one species and 1 of each other", "They are equal because both have 5 species", "Neither can be assessed"], "Even abundance gives higher diversity than dominance by one species.", tier="approfondissement")],
       P("Two plots on Mount Cameroon were sampled. Plot X: species with 15, 15, 15 and 15 individuals. Plot Y: species with 45, 5, 5 and 5 individuals.",
         [pn("Calculate D for plot X (2 d.p.).", 0.75, "N = 60; each n/N = 0.25, Σ = 4 × 0.0625 = 0.25; D = 0.75.", 0.005, None, 2),
          pn("Calculate D for plot Y (2 d.p.).", round(1 - ((45 / 60) ** 2 + 3 * (5 / 60) ** 2), 2), "N = 60; Σ = 0.5625 + 3 × 0.006944 = 0.5833; D = 0.42.", 0.005, None, 2),
          pm("Which plot has the higher diversity?", "X", ["Y", "They are equal", "Cannot tell"], "0.75 > 0.42.", 1)]),
       bd_sc, (fig, "Numbers of individuals of five species in a sample (total 40).", "A bar chart with five bars for species A to E, tallest 20 and shortest 2."),
       notes=["Simpson's index is presented as D = 1 − Σ(n/N)². Some syllabi use D = N(N−1)/Σn(n−1); the teacher must check which form the GCE Board expects.", "Names of reserves (Korup, Waza, Dja) are real Cameroonian protected areas; no figures given."])
assert round(1 - ((45 / 60) ** 2 + 3 * (5 / 60) ** 2), 2) == 0.42

# =====================================================================  CH 6  EXCHANGE AND TRANSPORT
c6 = p.chapter("exchange", "Exchange and transport", programRef="GCE A Level Biology — Exchange surfaces; transport in animals and plants (to be checked)")

# ---- 14 gas exchange
sav = [(6 * s * s) / (s ** 3) for s in (1, 2, 4)]; assert sav == [6.0, 3.0, 1.5]
fig = bars([("1 cm cube", 6), ("2 cm cube", 3), ("4 cm cube", 1.5)], w=400, h=240)
ge_sc = [
    ("Small organisms can rely on diffusion across the body surface because they have", "a large surface area to volume ratio", ["a small surface area to volume ratio", "a waterproof cuticle", "a closed circulatory system"], "Diffusion distances are short and area is large relative to volume."),
    ("In fish gills, the counter-current flow", "keeps a concentration gradient along the whole length of the lamella", ["moves water and blood in the same direction", "pumps blood by active transport", "removes the need for gill filaments"], "Blood always meets water with a higher oxygen concentration."),
    ("Insects obtain oxygen through", "tracheae that open at spiracles", ["lungs with alveoli", "gills", "the skin only"], "Tracheoles carry air close to the cells."),
    ("During inspiration in humans the diaphragm", "contracts and flattens", ["relaxes and domes upward", "contracts and moves upward", "does not move"], "The thorax volume increases and pressure falls."),
    ("Which structural feature of alveoli speeds diffusion?", "A wall one cell thick with a rich capillary network", ["A thick muscular wall", "A small surface area", "No blood supply"], "Short diffusion path and steep gradient maintained by blood flow."),
]
vent = 500 * 12; assert vent == 6000
lesson(c6, "gas-exchange", "Gas exchange surfaces",
       ["Explain the link between surface area to volume ratio and the need for exchange surfaces.", "Describe gas exchange in fish, insects and humans.", "Calculate surface area to volume ratios and pulmonary ventilation."],
       [("definition", "Why exchange surfaces are needed", "As an organism gets larger, its **surface area : volume ratio** falls, so diffusion across the body surface alone cannot supply enough oxygen. Large active organisms have specialised **exchange surfaces** with a large surface area, a thin barrier (short diffusion pathway) and a steep concentration gradient, kept steep by ventilation and blood flow (**Fick's law**: rate ∝ area × gradient ÷ thickness)."),
        ("pieges", "Surface area : volume", "Do not say a bigger organism has less surface area; it has **more** area in total but **less area per unit volume**. For a cube of side s: surface area = 6s², volume = s³, so the ratio is 6 ÷ s. Doubling the side halves the ratio. Fish gills work because water and blood flow in **opposite directions** (counter-current); a parallel flow would equalise concentrations half-way along."),
        ("retenir", "Gas exchange in three groups", "- **Fish**: gill filaments covered in lamellae; counter-current flow; the operculum pumps water over the gills.\n- **Insects**: air enters spiracles and travels down tracheae to tracheoles; muscle movements ventilate; no blood transport of oxygen.\n- **Humans**: trachea, bronchi, bronchioles and alveoli; alveolar epithelium one cell thick, surrounded by capillaries; surfactant lowers surface tension."),
        ("retenir", "Breathing and lung volumes", "**Inspiration**: external intercostal muscles contract (ribs up and out), diaphragm contracts and flattens; thorax volume rises, pressure falls, air flows in. **Expiration** at rest is passive: muscles relax, elastic recoil. **Pulmonary ventilation** = tidal volume × breathing rate. A spirometer records volumes (tidal volume, vital capacity).")],
       [("Worked example — ratio of cubes", "Calculate the surface area : volume ratio of cubes of side 2 cm and 4 cm.", ["2 cm cube: area 6 × 4 = 24 cm²; volume 8 cm³; ratio 24 ÷ 8 = %.1f." % sav[1], "4 cm cube: area 6 × 16 = 96 cm²; volume 64 cm³; ratio 96 ÷ 64 = %.1f." % sav[2], "Doubling the side halves the ratio."], "3 : 1 and 1.5 : 1"),
        ("Worked example — pulmonary ventilation", "A student at rest has a tidal volume of 500 cm³ and breathes 12 times per minute. Calculate the pulmonary ventilation.", ["Pulmonary ventilation = tidal volume × breathing rate.", "= 500 × 12.", "= %d cm³ per minute." % vent], "6000 cm³ min⁻¹ (6 dm³ min⁻¹)")],
       [M("Which gas exchange system uses counter-current flow?", "Fish gills", ["Insect tracheae", "Human alveoli", "Leaf stomata"], "Water and blood flow in opposite directions."),
        TF("A larger organism has a larger surface area to volume ratio.", False, "The ratio falls as size increases."),
        N("A cube has side 5 cm. Calculate surface area ÷ volume.", 6 / 5, "Area 150 cm², volume 125 cm³; ratio = 1.2.", tol=0.01)],
       [O("Explain how the structure of the alveolus is adapted for efficient gas exchange.", "The wall is one cell thick, giving a short diffusion pathway; there are millions of alveoli giving a large surface area; a dense capillary network and ventilation maintain a steep concentration gradient; moist surface lets gases dissolve.", ["Thin wall / short path (1)", "Large surface area (1)", "Steep gradient by blood flow and ventilation (1)"]),
        M("Why does the gill of a fish become less efficient when the water has low oxygen content?", "The concentration gradient between water and blood is reduced", ["The lamellae become thicker", "The blood flows the wrong way", "The gill filaments stop moving"], "Less oxygen in water gives a shallower gradient.", tier="approfondissement")],
       P("A resting adult has a tidal volume of 450 cm³. After exercise it rises to 1500 cm³ and the breathing rate rises from 14 to 30 breaths per minute.",
         [pn("Calculate pulmonary ventilation at rest (cm³ per min).", 450 * 14, "450 × 14 = 6300.", 0, "cm³/min", 1),
          pn("Calculate pulmonary ventilation after exercise (cm³ per min).", 1500 * 30, "1500 × 30 = 45 000.", 0, "cm³/min", 1),
          pn("By how many times has ventilation increased (to 1 d.p.)?", round(45000 / 6300, 1), "45 000 ÷ 6300 = 7.1.", 0.05, None, 1),
          po("Explain why ventilation increases during exercise.", "Muscle respiration produces more CO₂, which lowers blood pH; the brain's ventilation centre increases breathing rate and depth to remove CO₂ and supply O₂.", ["More CO₂ detected (1)", "Rate and depth increase (1)"], 2)]),
       ge_sc, (fig, "Surface area : volume ratio falls as a cube gets larger.", "Three bars for cubes of side 1, 2 and 4 cm with ratios 6, 3 and 1.5."),
       notes=["Typical values for adult tidal volume at rest (about 500 cm³) are approximate teaching values."])

# ---- 15 circulation
it = [T(215, 16, "Double circulation: the heart", 14, bold=True),
      T(110, 40, "from body (vena cava)", 11, color="blue"), T(320, 40, "from lungs (pulmonary vein)", 11, color="red"),
      LINE(110, 46, 110, 62, arrow="end", color="blue"), LINE(320, 46, 320, 62, arrow="end", color="red"),
      RECT(40, 64, 140, 50, fill="lightblue", radius=8), T(110, 94, "Right atrium", 13),
      RECT(250, 64, 140, 50, fill="lightorange", radius=8), T(320, 94, "Left atrium", 13),
      LINE(110, 114, 110, 134, arrow="end"), LINE(320, 114, 320, 134, arrow="end"),
      RECT(40, 136, 140, 60, fill="lightblue", radius=8), T(110, 170, "Right ventricle", 13),
      RECT(250, 136, 140, 60, fill="lightorange", radius=8), T(320, 170, "Left ventricle", 13),
      LINE(110, 196, 110, 214, arrow="end", color="blue"), LINE(320, 196, 320, 214, arrow="end", color="red"),
      T(110, 230, "to lungs (pulmonary artery)", 11, color="blue"), T(320, 230, "to body (aorta)", 11, color="red")]
fig = shapes(it, 430, 245)
ci_sc = [
    ("Which vessel carries oxygenated blood from the lungs to the heart?", "Pulmonary vein", ["Pulmonary artery", "Vena cava", "Aorta"], "The pulmonary vein carries blood back to the left atrium."),
    ("The left ventricle wall is thicker than the right because it", "pumps blood at higher pressure around the whole body", ["pumps blood only to the lungs", "has no valves", "has a smaller volume"], "Greater force is needed for the systemic circulation."),
    ("Atrioventricular valves close when", "ventricular pressure exceeds atrial pressure", ["atria contract", "the ventricles relax", "blood enters the aorta"], "They prevent backflow into the atria."),
    ("Cardiac output =", "stroke volume × heart rate", ["stroke volume ÷ heart rate", "heart rate − stroke volume", "blood pressure × volume"], "Volume pumped per minute."),
    ("Which vessels have walls only one cell thick for exchange?", "Capillaries", ["Arteries", "Veins", "Aorta"], "They allow diffusion and tissue fluid formation."),
]
co = 70 * 72; assert co == 5040
lesson(c6, "circulation", "The heart and circulation",
       ["Describe the structure and double circulation of the mammalian heart.", "Explain the cardiac cycle and the roles of the valves.", "Compare arteries, veins and capillaries; calculate cardiac output."],
       [("definition", "A double circulatory system", "In mammals blood passes through the heart twice in one complete circuit: the **pulmonary circulation** (heart → lungs → heart) and the **systemic circulation** (heart → body → heart). This keeps oxygenated and deoxygenated blood apart and lets blood return to the heart for a pressure boost before travelling to the body, supporting a high metabolic rate."),
        ("pieges", "Left and right on diagrams", "Heart diagrams are drawn as if looking at the person facing you, so the **right** side of the heart is on **your left**. The **left ventricle** has the thicker muscle wall. Arteries carry blood *away* from the heart and veins carry blood *towards* it; it is **not** true that arteries always carry oxygenated blood (the pulmonary artery carries deoxygenated blood)."),
        ("retenir", "The cardiac cycle", "1. **Atrial systole**: atria contract; blood pushed into ventricles.\n2. **Ventricular systole**: ventricles contract; atrioventricular valves close (first sound), semilunar valves open; blood leaves by aorta and pulmonary artery.\n3. **Diastole**: heart relaxes; semilunar valves close (second sound); atria fill.\nThe **sinoatrial node** (the pacemaker) starts each beat."),
        ("retenir", "Blood vessels and output", "- **Arteries**: thick elastic and muscular walls, narrow lumen, high pressure.\n- **Veins**: thin walls, wide lumen, valves, low pressure.\n- **Capillaries**: one-cell-thick wall, huge total surface area.\n**Cardiac output** = stroke volume × heart rate. Tissue fluid forms at the arterial end of capillaries by high hydrostatic pressure; most returns at the venous end by osmosis and the rest by the lymph.")],
       [("Worked example — cardiac output", "A resting man has a stroke volume of 70 cm³ and a heart rate of 72 beats per minute. Calculate cardiac output.", ["Cardiac output = stroke volume × heart rate.", "= 70 × 72.", "= %d cm³ per minute (about 5 dm³ per minute)." % co], "5040 cm³ min⁻¹"),
        ("Worked example — pressure and valves", "In the cardiac cycle the pressure in the left ventricle rises above the pressure in the left atrium. State what happens to the valves and why this matters.", ["Higher pressure in the ventricle pushes the atrioventricular valve shut.", "This prevents blood from flowing backwards into the atrium.", "When ventricular pressure exceeds aortic pressure, the semilunar valve opens and blood leaves."], "AV valve closes; semilunar valve opens")],
       [M("Which chamber pumps blood to the lungs?", "Right ventricle", ["Left ventricle", "Right atrium", "Left atrium"], "Right ventricle → pulmonary artery."),
        TF("The pulmonary artery carries oxygenated blood.", False, "It carries deoxygenated blood from the heart to the lungs."),
        N("Calculate cardiac output (cm³ per minute) if stroke volume is 80 cm³ and heart rate is 60 beats per minute.", 4800, "80 × 60 = 4800.", unit="cm³/min")],
       [O("Explain why veins have valves but arteries do not.", "Blood in veins is at low pressure and could flow backwards; valves close to prevent backflow and keep blood moving towards the heart, helped by muscle contractions. Arteries carry blood at high pressure, so backflow is not a problem.", ["Low pressure in veins (1)", "Valves stop backflow (1)", "Arteries high pressure (1)"]),
        M("An athlete's resting heart rate is lower than average but cardiac output is the same. This is because", "stroke volume is greater", ["blood volume is smaller", "valves leak", "the lungs are smaller"], "Cardiac output = SV × HR.", tier="approfondissement")],
       P("An athlete training in Limbe has a resting heart rate of 55 beats per minute and a stroke volume of 95 cm³; a non-athlete has 75 beats per minute and a stroke volume of 70 cm³.",
         [pn("Cardiac output of the athlete (cm³/min).", 55 * 95, "55 × 95 = 5225.", 0, "cm³/min", 1),
          pn("Cardiac output of the non-athlete (cm³/min).", 75 * 70, "75 × 70 = 5250.", 0, "cm³/min", 1),
          po("Explain why the two outputs are almost equal although the heart rates differ.", "The athlete's heart muscle is stronger, so each beat pumps more blood (larger stroke volume); fewer beats are needed to deliver the same output.", ["Larger stroke volume (1)", "Compensates for lower rate (1)"], 2)]),
       ci_sc, (fig, "The four chambers of the heart and the double circulation.", "Four boxes for the atria and ventricles with arrows to and from the body and lungs."),
       notes=["Athlete heart-rate figures are illustrative.", "Heart diagram orientation (viewer's left = right side of the heart) is standard."])
assert 55 * 95 == 5225 and 75 * 70 == 5250

# ---- 16 plant transport
import math as _m
vol = _m.pi * 0.5 ** 2 * 45; rate_mm3 = vol / 5
assert abs(vol - 35.343) < 0.01
fig = vflow(["Water enters root hair cells by osmosis", "Crosses the cortex to the xylem", "Pulled up the xylem (cohesion-tension)", "Evaporates from mesophyll cells in the leaf", "Diffuses out through the stomata"], w=430, size=12, bw=340, title="The transpiration stream")
pt_sc = [
    ("Water is pulled up the xylem mainly by", "transpiration pull and the cohesion of water molecules", ["root pressure only", "active transport in xylem", "pressure of the phloem"], "Evaporation creates tension that pulls the continuous water column."),
    ("Xylem vessels are adapted for transport because they", "are dead, hollow and lignified", ["have end walls and cytoplasm", "have companion cells", "are alive and permeable"], "Lignin gives strength; no end walls give a continuous tube."),
    ("Transpiration is increased by", "high temperature and low humidity", ["high humidity", "closed stomata", "still, cool air"], "These increase the water potential gradient out of the leaf."),
    ("Phloem transports", "sucrose and amino acids (assimilates)", ["only water and minerals", "only oxygen", "only hormones"], "Translocation can be in both directions."),
    ("A potometer measures", "the rate of water uptake by a cutting", ["the rate of photosynthesis directly", "the mass of the plant", "the rate of respiration"], "Uptake is used as a close estimate of transpiration."),
]
lesson(c6, "plant-transport", "Transport in plants",
       ["Describe the structure of xylem and phloem.", "Explain the cohesion-tension theory and the factors affecting transpiration.", "Describe mass flow in phloem and calculate water uptake from a potometer."],
       [("definition", "Transpiration stream", "**Transpiration** is the loss of water vapour from the leaves, mainly through the **stomata**. Water evaporates from mesophyll cell walls, lowering the water potential in the leaf and **pulling** water up the **xylem**. Water molecules stick together (**cohesion**) and to the vessel walls (**adhesion**), so the column does not break. This is the **cohesion-tension theory**. Water enters roots by osmosis down a water-potential gradient."),
        ("pieges", "Xylem is not powered by living cells", "Xylem vessels are dead, so water movement in them is **passive**. Do not say that xylem pumps water by active transport. **Root pressure** (from active salt uptake into the xylem) contributes a little but cannot lift water to the top of tall trees. Transpiration is a result of gas exchange: stomata must open to let CO₂ in, and water inevitably escapes."),
        ("retenir", "Factors affecting transpiration", "Rate increases with **light** (stomata open), **temperature** (more evaporation), **wind** (removes humid air), and decreases with **humidity**. Xerophytes have adaptations such as thick cuticle, sunken stomata, rolled leaves and hairs. A **potometer** measures water uptake: volume = πr² × distance moved by the air bubble."),
        ("retenir", "Phloem and mass flow", "**Phloem** has living **sieve tube elements** (with perforated sieve plates) and **companion cells**. Sucrose is loaded actively into sieve tubes at the **source** (e.g. leaves), lowering water potential so water enters by osmosis. Pressure rises and sap flows to the **sink** (roots, fruits, growing points) where sucrose is removed. This is the **mass flow** hypothesis.")],
       [("Worked example — potometer", "In a potometer the air bubble moves 45 mm in 5 minutes. The capillary tube has radius 0.5 mm. Calculate the volume of water taken up and the rate.", ["Volume = πr² × distance = π × 0.5² × 45 = %.1f mm³." % vol, "Rate = volume ÷ time = %.1f ÷ 5." % vol, "= %.1f mm³ per minute." % rate_mm3], "35.3 mm³ in total; 7.1 mm³ min⁻¹"),
        ("Worked example — predicting a change", "A leafy shoot is moved from a still, humid room in Buea to a sunny, windy balcony. Predict and explain the change in water uptake.", ["Wind removes humid air near the stomata, steepening the water-potential gradient.", "Light opens the stomata and heat increases evaporation.", "So transpiration and water uptake both increase."], "Water uptake increases")],
       [M("Which tissue transports water from roots to leaves?", "Xylem", ["Phloem", "Cortex only", "Epidermis"], "Xylem carries water and mineral ions upward."),
        TF("Phloem transport requires living cells.", True, "Sieve tubes and companion cells are living; loading uses ATP."),
        N("A bubble in a potometer moves 30 mm in 6 minutes. The capillary radius is 0.4 mm. Calculate the rate of uptake in mm³ per minute (2 d.p.).", round(_m.pi * 0.4 ** 2 * 30 / 6, 2), "Volume = π × 0.16 × 30 = 15.08 mm³; rate = 15.08 ÷ 6 = 2.51.", tol=0.02, unit="mm³/min")],
       [O("Explain how cohesion and adhesion help water to rise in a tall tree.", "Hydrogen bonds make water molecules cohere, so tension from evaporation pulls the whole column upward without breaking; adhesion to the xylem walls helps to hold the column and allows some capillary rise.", ["Cohesion (1)", "Tension / transpiration pull (1)", "Adhesion (1)"]),
        M("Why does the diameter of a tree trunk tend to shrink slightly during the day?", "Tension in the xylem pulls the walls inward when transpiration is high", ["Phloem pressure rises", "Cells die", "The bark loses lignin"], "Strong tension can narrow the vessels.", tier="approfondissement")],
       P("The mass flow of sucrose in phloem: leaves of cassava make sucrose that is moved to the tubers.",
         [pm("In this case the leaves are the", "source", ["sink", "vessel", "stoma"], "Sucrose is made there.", 1),
          pm("The tubers are the", "sink", ["source", "stoma", "xylem"], "Sucrose is removed and stored there.", 1),
          po("Explain how sucrose loading causes water to enter the sieve tube.", "Active loading of sucrose lowers the water potential in the sieve tube, so water enters by osmosis from the xylem, raising the hydrostatic pressure that drives mass flow.", ["Active loading (1)", "Lower water potential, osmosis (1)", "Pressure builds to drive flow (1)"], 3)]),
       pt_sc, (fig, "Water travels from the soil to the atmosphere in the transpiration stream.", "Five boxes in a vertical chain from root hair cells through the xylem to the stomata."),
       notes=["Mass-flow and cohesion-tension are presented as accepted models; the details of loading mechanisms may exceed the syllabus.", "Potometer volume uses V = πr²d; teachers should check units."])

# =====================================================================  CH 7  IMMUNITY
c7 = p.chapter("immunity", "Immunity and disease", programRef="GCE A Level Biology — Infectious disease and immunity (to be checked)")

# ---- 17 pathogens and immune response
fig = vflow(["Antigen of a pathogen is detected", "Matching B cell is selected and divides", "Plasma cells make specific antibodies", "Memory cells remain in the blood"], w=420, size=12, bw=330, title="Clonal selection (humoral response)")
im_sc = [
    ("An antigen is", "a molecule that triggers an immune response", ["a type of antibody", "a white blood cell", "a bacterium only"], "Antigens are usually proteins or glycoproteins on pathogens."),
    ("Which cells produce antibodies?", "Plasma cells (derived from B cells)", ["Phagocytes", "Red blood cells", "T killer cells"], "Plasma cells are factories for one antibody."),
    ("Phagocytes", "engulf pathogens and digest them with lysosome enzymes", ["make antibodies", "make memory cells", "kill only viruses"], "Phagocytosis is non-specific."),
    ("The secondary immune response is faster because of", "memory cells", ["phagocytes", "platelets", "red blood cells"], "Memory cells respond rapidly and strongly on re-exposure."),
    ("Helper T cells", "release cytokines that activate B cells and other cells", ["engulf pathogens", "carry oxygen", "store antibodies"], "They coordinate the immune response."),
]
lesson(c7, "immune-response", "Pathogens and the immune response",
       ["Name types of pathogen and the body's non-specific defences.", "Describe the roles of phagocytes, B cells and T cells in the specific immune response.", "Explain primary and secondary responses and antibody structure."],
       [("definition", "Pathogens and first defences", "A **pathogen** is an organism or virus that causes disease. Types: **bacteria** (e.g. *Vibrio cholerae*), **viruses** (HIV), **fungi** (ringworm) and **protoctists** (*Plasmodium*). Non-specific defences: **skin**, **mucus and cilia**, stomach acid, lysozyme in tears, and **phagocytes**, which engulf pathogens (phagocytosis) and digest them with lysosomal enzymes. **Inflammation** and fever help too."),
        ("pieges", "Antigen and antibody", "An **antigen** is a molecule (often on the surface of a pathogen) that triggers an immune response; an **antibody** is a protein made in response that binds to one specific antigen. Antibodies are **specific**: their variable regions have a shape complementary to one antigen. Do not say the body makes antibodies 'to kill' directly; they label, agglutinate and neutralise, and phagocytes then destroy the pathogen."),
        ("retenir", "Specific response", "- **B cells**: recognise a specific antigen, divide (clonal selection) into **plasma cells** (secrete antibodies) and **memory cells**.\n- **T helper cells** release cytokines that stimulate B cells and others.\n- **T killer cells** attack infected body cells.\nThe response to the first infection (**primary**) is slow; on re-infection, memory cells give a faster, larger **secondary** response."),
        ("retenir", "Antibody structure", "An antibody (immunoglobulin) has **four polypeptide chains**, two heavy and two light, joined by disulfide bonds. The **variable region** at the tips forms the antigen-binding site, so different antibodies fit different antigens. The **constant region** is the same for each class. Antibodies bind to pathogens and cause them to clump (agglutination), neutralise toxins and make phagocytosis easier.")],
       [("Worked example — doubling", "A B cell with the correct receptor divides every 8 hours. Starting from 1 selected cell, how many cells are there after 48 hours?", ["Number of divisions = 48 ÷ 8 = 6.", "Cells double at each division: 2⁶.", "2⁶ = %d cells." % 2 ** 6], "64"),
        ("Worked example — primary and secondary", "After a first exposure to an antigen antibody concentration peaks at 10 units after 14 days. After a second exposure it peaks at 100 units after 5 days. Explain the difference.", ["The first exposure needs selection of rare B cells and clone formation (slow).", "Memory cells from the first exposure respond quickly in the second.", "So more plasma cells form sooner and the peak is higher (secondary response)."], "Memory cells give a faster, bigger response")],
       [M("Which cells engulf pathogens?", "Phagocytes", ["Plasma cells", "Red blood cells", "Platelets"], "Phagocytosis is by neutrophils and macrophages."),
        TF("Antibodies are specific to a particular antigen.", True, "The binding site fits only one antigen shape."),
        N("A selected B cell divides every 6 hours. How many cells after 30 hours?", 2 ** 5, "30 ÷ 6 = 5 divisions; 2⁵ = 32.")],
       [O("Explain why a person who has recovered from measles rarely gets it again.", "Memory B cells specific to the measles virus remain; on re-exposure they divide rapidly into plasma cells and make antibodies quickly, destroying the virus before symptoms occur.", ["Memory cells persist (1)", "Rapid antibody production (1)", "Virus destroyed before symptoms (1)"]),
        M("Why does the common cold virus return repeatedly?", "Many different strains with differing antigens exist and the antigens change", ["Memory cells die at once", "Antibodies are not specific", "Phagocytes cannot engulf viruses"], "Antigenic variation means memory cells may not recognise a new strain.", tier="approfondissement")],
       P("The graph of antibody concentration after two injections of the same antigen shows a slow rise and a low peak after the first, and a fast rise and a high peak after the second.",
         [pm("Which type of cell is responsible for the rapid second response?", "Memory B cells", ["Red blood cells", "Phagocytes", "Platelets"], "They are produced in the first response.", 1),
          pm("Which cells release antibodies?", "Plasma cells", ["Memory cells", "Helper T cells", "Phagocytes"], "Plasma cells secrete antibodies.", 1),
          po("Explain what makes antibodies specific.", "The variable region has a tertiary structure with a shape complementary to the shape of one antigen only.", ["Variable region (1)", "Complementary shape (1)"], 2)]),
       im_sc, (fig, "Clonal selection of B cells: from antigen recognition to plasma cells and memory cells.", "Four boxes in a vertical chain from antigen detection through B-cell division to plasma cells and memory cells."),
       notes=["Time courses in the examples are illustrative.", "Cell-mediated immunity is outlined only; detailed T-cell subsets (e.g. regulatory) are not covered."])

# ---- 18 diseases and vaccination
R0 = 4; thr = 1 - 1 / R0; assert thr == 0.75
fig = bars([("Not immune", 25, "red"), ("Immune (vaccinated)", 75, "green")], w=400, h=240)
vd_sc = [
    ("Malaria is transmitted by", "the female Anopheles mosquito", ["the tsetse fly", "contaminated water", "droplets in the air"], "The vector carries Plasmodium."),
    ("Which disease is caused by a virus that infects helper T cells?", "AIDS (HIV infection)", ["Cholera", "Tuberculosis", "Malaria"], "HIV is a retrovirus."),
    ("Vaccination gives", "artificial active immunity", ["natural passive immunity", "artificial passive immunity", "no immunity"], "The person makes their own antibodies and memory cells."),
    ("Herd immunity works because", "a high proportion of immune people reduces the spread to those not immune", ["everyone makes the same antibodies", "pathogens die in vaccinated people only", "it needs no vaccinated people"], "Fewer susceptible hosts break the chain of transmission."),
    ("Cholera is spread mainly by", "contaminated water and food", ["mosquito bites", "blood transfusions only", "air droplets only"], "Safe water and sanitation prevent it."),
]
lesson(c7, "disease-vaccination", "Infectious disease and vaccination",
       ["Describe the cause and transmission of cholera, malaria, tuberculosis and HIV/AIDS.", "Distinguish active and passive, natural and artificial immunity.", "Explain vaccination and herd immunity; calculate the herd immunity threshold."],
       [("definition", "Four infectious diseases", "- **Cholera**: *Vibrio cholerae* (bacterium); spread by contaminated water and food; severe diarrhoea.\n- **Malaria**: *Plasmodium* (protoctist); spread by the female *Anopheles* mosquito (the **vector**).\n- **Tuberculosis**: *Mycobacterium tuberculosis*; spread by droplets from coughs; affects the lungs.\n- **HIV/AIDS**: a retrovirus infecting helper T cells; spread by body fluids; the immune system is weakened over time."),
        ("pieges", "Active versus passive", "In **active** immunity the body makes its own antibodies and memory cells (after infection or vaccination): slow to start but long-lasting. In **passive** immunity antibodies are received ready-made (across the placenta, in breast milk or by injection): fast but short-lived and no memory cells. **Natural** = through infection or mother; **artificial** = through vaccination or injected antibodies. A vaccine does not cure; it prepares the body."),
        ("retenir", "Vaccination and herd immunity", "A **vaccine** contains weakened, killed or part of a pathogen (antigens), so that the body makes memory cells. **Herd immunity** is the protection of non-immune people when a high proportion of the population is immune. If R₀ is the average number of people one case infects in a fully susceptible population, the threshold is about 1 − 1 ÷ R₀."),
        ("retenir", "Prevention and control", "Control measures include safe drinking water and sanitation (cholera), bed nets, drainage of standing water and prompt treatment (malaria), vaccination programmes, early diagnosis and full courses of antibiotics (TB). **Antibiotic resistance** arises when mutated bacteria survive and multiply, so antibiotics must be used only when necessary. Health advice in this lesson must be checked by a health professional.")],
       [("Worked example — herd immunity", "Suppose a disease has R₀ = 4. Estimate the proportion of the population that must be immune to stop sustained spread.", ["Threshold = 1 − 1 ÷ R₀.", "= 1 − 1 ÷ 4 = 1 − 0.25.", "= %.2f, i.e. 75 %%." % thr], "75 %"),
        ("Worked example — classify immunity", "A newborn baby in Bafoussam receives antibodies from the mother's milk. Classify this immunity.", ["The baby did not make the antibodies: passive.", "They came naturally, not by injection: natural.", "No memory cells are made, so protection is temporary."], "Natural passive immunity")],
       [M("Which vector transmits malaria?", "Anopheles mosquito", ["Housefly", "Tsetse fly", "Cockroach"], "Female Anopheles mosquitoes carry Plasmodium."),
        TF("Vaccination provides passive immunity.", False, "It provides artificial active immunity."),
        N("If R₀ = 5, what percentage of the population must be immune (herd immunity threshold)?", (1 - 1 / 5) * 100, "1 − 1/5 = 0.8, i.e. 80 %.", unit="%")],
       [O("Explain how vaccination protects an individual and the community.", "The vaccine's antigens cause B cells to produce memory cells, so on real infection a fast secondary response occurs; if enough people are immune, transmission falls and non-immune people are protected (herd immunity).", ["Memory cells formed (1)", "Rapid secondary response (1)", "Herd immunity (1)"]),
        M("Why is HIV treatment difficult by the body's own defences alone?", "HIV destroys helper T cells that coordinate the immune response", ["HIV has no genetic material", "It is not specific to any cell", "It lives only in red blood cells"], "Loss of helper T cells weakens both antibody and cell-mediated responses.", tier="approfondissement")],
       P("A health worker discusses a vaccination programme for a disease with R₀ = 8.",
         [pn("Calculate the herd immunity threshold as a percentage.", 87.5, "1 − 1/8 = 0.875 = 87.5 %.", 0.1, "%", 2),
          pm("Which statement about vaccination is correct?", "It stimulates the production of memory cells", ["It gives ready-made antibodies", "It cures an existing infection", "It prevents mutation"], "Active artificial immunity.", 1),
          po("Why might herd immunity fail even with a high vaccination rate?", "Immune people may be clustered, vaccines may not be 100 % effective, and the pathogen may mutate so that antibodies no longer recognise it.", ["Any valid reason (2)"], 2)]),
       vd_sc, (fig, "Illustration of 75 % of a population being immune (herd immunity threshold for R₀ = 4).", "A bar chart with two bars: 25 percent not immune and 75 percent immune."),
       notes=["Herd immunity threshold 1 − 1/R₀ is the standard simple model; R₀ values used are illustrative, not real disease values.", "Health content (disease prevention and treatment) must be reviewed by a health professional before release."])

write_compact(p)
