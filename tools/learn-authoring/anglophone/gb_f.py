from biolib import *


def add(p):
    # ------------------------------------------------------------------ variation and selection
    ch = p.chapter("evolution", "Variation, selection and DNA",
                   "GCE O Level Biology — Variation; natural selection; chromosomes, DNA, mitosis and meiosis (to be checked against the official syllabus)")
    hist = bars([("140-144", 2, "blue"), ("145-149", 6, "blue"), ("150-154", 11, "blue"), ("155-159", 8, "blue"), ("160-164", 3, "blue")], unit="number of students (height in cm; teaching data)", w=460, h=240)
    sel = flow(["Variation|in the population", "Overproduction|of offspring", "Struggle for|survival", "Best adapted|survive", "They breed and|pass on alleles"], w=480, size=10, bh=44, gap=14)
    surv = 10
    for hr in range(5): surv *= 2
    assert surv == 320
    build(ch, "selection", "Variation and natural selection", 30,
          ["Distinguish continuous and discontinuous variation and give the causes of variation.",
           "Explain natural selection using the example of resistance.",
           "Distinguish natural selection from selective breeding."],
          [("key", "definition", "Variation",
            "**Variation** is the differences between individuals of the same species. **Continuous variation** shows a range of values with no clear groups (height, mass, skin tone), usually shaped by genes *and* environment. **Discontinuous variation** shows distinct groups with nothing between them (blood group, ability to roll the tongue), usually controlled by genes only."),
           ("fig", hist, "Heights of 30 students: continuous variation.", "A bar chart of heights in 5 cm classes: 2, 6, 11, 8 and 3 students, forming a bell shape with the tallest bar in the middle."),
           ("key", "retenir", "Causes of variation",
            "- **Genetic**: different combinations of alleles from two parents (sexual reproduction) and **mutation**, a random change in a gene or chromosome; ionising radiation and some chemicals increase the rate.\n- **Environmental**: food, light, climate, accidents (a plant grown in shade is taller and paler).\n\nOnly genetic variation is inherited."),
           ("key", "definition", "Natural selection",
            "Organisms produce more offspring than can survive. Those with features that suit the environment are more likely to survive and breed, and pass on their alleles; over many generations the characteristics of the population change. This is **natural selection** (Darwin and Wallace, 19th century). It is a main mechanism of **evolution**."),
           ("fig", sel, "The steps of natural selection.", "Five boxes joined by arrows: variation, overproduction of offspring, struggle for survival, the best adapted survive, they breed and pass on their alleles."),
           ("key", "retenir", "Resistance and selective breeding",
            "**Resistance**: in a population of bacteria or mosquitoes a few have a mutation that protects them against an antibiotic or insecticide. The treatment kills the others; the resistant ones survive, reproduce and the resistant allele becomes common. So antibiotics must be used only when needed and the course completed.\n\n**Selective breeding** is the same idea controlled by people: farmers choose the best cattle or cocoa plants to breed."),
           ("key", "pieges", "Common mistakes",
            "- Individuals do not 'try' to adapt, and acquired features (a bodybuilder's muscles) are *not* inherited.\n- Natural selection acts on *existing* variation; mutation is random.\n- The *population* evolves, not the individual."),
           ("example", "Spread of resistance", "A farmer sprays insecticide. Ten resistant mosquitoes survive and the number doubles every hour in an enclosed place. How many resistant mosquitoes after 5 hours, assuming no deaths?",
            ["Doubling each hour: 10 × 2 × 2 × 2 × 2 × 2.", "10 × 32 = 320."],
            "320 resistant mosquitoes"),
           ("example", "Which type of variation?", "Classify: (a) blood group, (b) height of maize plants, (c) tongue-rolling.",
            ["Blood group and tongue-rolling fall into clear groups: discontinuous.", "Maize height has all values and depends on water and soil as well as genes: continuous."],
            "(a) discontinuous, (b) continuous, (c) discontinuous")],
          [("mcq", "Which is an example of discontinuous variation?", "blood group", ["height", "mass", "length of a leaf"], "Blood group falls into distinct classes."),
           ("tf", "A feature that an animal acquires during its life, such as a scar, can be inherited.", False, "Acquired characteristics are not passed on."),
           ("num", "Twelve resistant bacteria double every 20 minutes. How many are there after 1 hour (3 doublings)?", 96, "12 × 2 × 2 × 2 = 96.", 0),
           ("mcq", "Which of these changes the genes?", "mutation", ["exercise", "diet", "light"], "A mutation is a change in a gene or chromosome."),
           ("open", "Explain how bacteria become resistant to an antibiotic.", "A few bacteria have a mutation that gives resistance. The antibiotic kills the non-resistant ones; the resistant ones survive and reproduce, passing on the allele, so a resistant population results.", ["1 mark: chance mutation", "1 mark: antibiotic kills non-resistant", "1 mark: survivors reproduce and pass on allele"]),
           ("prob", "Maize plants of the same variety are grown in two fields in Bamenda: one with plenty of water and one dry.", [("mcq", "Differences in height between the two fields are mostly due to:", "environment", ["mutation", "inheritance", "natural selection"], "Same variety, different water."), ("mcq", "This is an example of:", "continuous variation", ["discontinuous variation", "a mutation", "selective breeding"], "Height varies continuously."), ("open", "Describe how a farmer could produce a higher-yielding variety by selective breeding.", "Choose the plants with the highest yield, breed them together, select the best offspring and repeat over many generations.", ["1 mark: choose best parents", "1 mark: repeat over generations"], 2)])],
          [("Variation is the:", "differences between individuals of a species", ["growth of a plant", "number of species", "size of a cell"], "Definition."),
           ("Which factor increases mutation rate?", "ionising radiation", ["water", "vitamins", "exercise"], "Radiation damages DNA."),
           ("Natural selection favours:", "individuals best adapted to the environment", ["the biggest always", "the oldest", "random individuals"], "Best adapted survive."),
           ("Selective breeding is carried out by:", "humans", ["wind", "mutation only", "bacteria"], "Artificial selection."),
           ("Which statement about acquired features is correct?", "they are not inherited", ["they are inherited", "they are mutations", "they change the DNA"], "Not inherited.")],
          ["Doubling figures are invented to show exponential growth.", "Naming of Darwin and Wallace is general; check the level of history required."])

    dna = [("A", "T", "blue", "red"), ("T", "A", "red", "blue"), ("C", "G", "green", "orange"), ("G", "C", "orange", "green"), ("A", "T", "blue", "red"), ("C", "G", "green", "orange")]
    sh = [LINE(150, 15, 150, 195, width=4, color="grey"), LINE(250, 15, 250, 195, width=4, color="grey")]
    for i, (a, b, ca, cb) in enumerate(dna):
        y = 35 + i * 28
        sh += [LINE(150, y, 250, y, width=3, color="lightgrey"), T(135, y + 5, a, size=14, anchor="end", color=ca, bold=True), T(265, y + 5, b, size=14, anchor="start", color=cb, bold=True)]
    sh += lab(250, 190, 330, 170, "sugar-phosphate backbone", size=11) + [T(330, 100, "A pairs with T", size=11, anchor="start"), T(330, 116, "C pairs with G", size=11, anchor="start")]
    dnafig = shapes(sh, 480, 210)
    cells = grid(["Body cell|46 chromosomes", "MITOSIS|growth and repair", "2 identical cells|46 each", "Cell in ovary|or testis: 46", "MEIOSIS|makes gametes", "4 different cells|23 each"], cols=3, bh=56, size=11, colors=["lightblue", "lightyellow", "lightgreen", "lightblue", "lightyellow", "lightgreen"])
    cellsn = 2 ** 5
    assert cellsn == 32
    build(ch, "dna", "Chromosomes, DNA, mitosis and meiosis", 30,
          ["Describe the relationship between DNA, genes and chromosomes.",
           "Describe base pairing in DNA.",
           "Compare mitosis and meiosis and state their importance."],
          [("key", "definition", "Chromosomes, genes and DNA",
            "The **nucleus** contains **chromosomes**, long threads made of **DNA** (deoxyribonucleic acid). A **gene** is a length of DNA that carries the instructions to make one protein, and so controls a characteristic. Human body cells have **46 chromosomes** in **23 pairs**; gametes have 23. Sex is decided by the pair of **sex chromosomes**: **XX** female, **XY** male."),
           ("fig", dnafig, "A short stretch of DNA.", "A ladder-shaped DNA molecule with two grey backbones and six rungs showing the base pairs A with T, T with A, C with G, G with C, A with T and C with G."),
           ("key", "retenir", "DNA structure",
            "DNA is a **double helix**: two strands twisted together. Each strand has a backbone and four types of **base**: **A**, **T**, **C** and **G**. The strands are joined by **complementary base pairing**: **A with T** and **C with G**. The order of the bases forms the genetic code. DNA can make an exact copy of itself before a cell divides."),
           ("key", "retenir", "Mitosis and meiosis",
            "- **Mitosis**: one cell divides into **two genetically identical** cells with the **same chromosome number**. Used for growth, repair and asexual reproduction.\n- **Meiosis**: occurs only in the ovaries and testes; makes **four different gametes** with **half** the chromosome number. Fertilisation restores the full number and the mixing of genes gives **variation**."),
           ("fig", cells, "Mitosis and meiosis compared.", "A table of six boxes: a body cell with 46 chromosomes divides by mitosis into 2 identical cells with 46 each; a cell in an ovary or testis with 46 divides by meiosis into 4 different cells with 23 each."),
           ("key", "pieges", "Common mistakes",
            "- Meiosis does not happen in body cells; mitosis does not make gametes.\n- A is paired with T, never with C. (In this level of syllabus, RNA is not required.)\n- A gene is a *part* of a chromosome, not a whole cell."),
           ("example", "Complementary strand", "One strand of DNA has the base sequence ATGCCA. Write the sequence of the complementary strand.",
            ["Pair A with T, T with A, G with C, C with G, C with G, A with T.", "Result: TACGGT."],
            "TACGGT"),
           ("example", "Counting cells", "A single fertilised egg divides by mitosis 5 times. How many cells does it have then, assuming no cell dies? How many chromosomes are in each?",
            ["Each division doubles the number: 2⁵ = 32 cells.", "Mitosis gives identical cells so each has 46 chromosomes."],
            "32 cells, 46 chromosomes each")],
          [("mcq", "A gene is:", "a section of DNA that codes for a protein", ["a whole chromosome", "a type of cell", "a base"], "A gene is a length of DNA."),
           ("num", "A cell divides by mitosis 4 times. How many cells result?", 16, "2⁴ = 16.", 0),
           ("match", "Match each term with its description.", [("mitosis", "two identical cells"), ("meiosis", "four different gametes"), ("XY", "male sex chromosomes"), ("base pairing", "A with T")], "Standard definitions."),
           ("mcq", "The complementary strand to GGTA is:", "CCAT", ["CCTA", "AATG", "GGTA"], "G-C, G-C, T-A, A-T gives CCAT."),
           ("open", "State the importance of mitosis and meiosis.", "Mitosis produces identical cells for growth, repair and asexual reproduction. Meiosis produces gametes with half the chromosome number so that the number stays constant after fertilisation and creates variation.", ["1 mark: mitosis for growth/repair", "1 mark: meiosis halves the number", "1 mark: variation or keeps number constant"]),
           ("prob", "A human skin cell has 46 chromosomes.", [("num", "How many chromosomes in a sperm cell?", 23, "Half of 46.", 0), ("num", "How many chromosomes in the zygote?", 46, "23 + 23 = 46.", 0), ("mcq", "The sex chromosomes of a boy are:", "XY", ["XX", "YY", "XXY"], "Males are XY."), ("open", "Explain why meiosis is needed in sexual reproduction.", "Without halving, the chromosome number would double in every generation.", ["1 mark: halving of chromosome number", "1 mark: stays constant after fertilisation"], 2)])],
          [("Human body cells contain:", "46 chromosomes", ["23 chromosomes", "92 chromosomes", "12 chromosomes"], "23 pairs."),
           ("Base pairing is:", "A with T and C with G", ["A with C", "T with G", "A with G"], "Complementary."),
           ("Mitosis results in:", "two identical cells", ["four gametes", "one cell", "half the chromosomes"], "Identical cells."),
           ("Gametes are made by:", "meiosis", ["mitosis", "digestion", "osmosis"], "Meiosis."),
           ("A female has sex chromosomes:", "XX", ["XY", "YY", "X only"], "XX.")],
          ["RNA and protein synthesis are not covered; check syllabus scope.", "Link to existing monohybrid inheritance lesson is intentional."], prereq=["gceol-biology-genetics"])

    # ------------------------------------------------------------------ diseases
    ch = p.chapter("health", "Diseases, immunity and public health",
                   "GCE O Level Biology — Diseases and their control: malaria, cholera, typhoid, tuberculosis; immunity and vaccination (to be checked against the official syllabus; for health review)")
    mal = flow(["Infected person|parasites in blood", "Female Anopheles|bites, takes blood", "Parasite grows|in the mosquito", "Mosquito bites|a new person"], w=480, size=10, bh=46, gap=18)
    build(ch, "malaria", "Pathogens and malaria", 30,
          ["Define a pathogen and name the main types with an example of each.",
           "Describe the life cycle, symptoms, prevention and treatment of malaria.",
           "Explain what a vector is."],
          [("key", "definition", "Disease and pathogens",
            "A **disease** is a disorder of the body or mind. An **infectious disease** is caused by a **pathogen** that passes from one host to another.\n\n- **Bacteria**: cholera, typhoid, tuberculosis.\n- **Viruses**: HIV, influenza, measles.\n- **Protoctists** (single-celled): malaria (*Plasmodium*).\n- **Fungi**: ringworm, athlete's foot.\n\nA **vector** is an organism that carries a pathogen from one host to another."),
           ("key", "retenir", "Malaria",
            "**Malaria** is caused by *Plasmodium* and carried by the **female *Anopheles* mosquito**. The parasite infects the liver and the **red blood cells**, which burst at intervals.\n\n**Symptoms**: high fever, shivering, sweating, headache, tiredness, anaemia. Severe malaria, especially in young children and pregnant women, can be fatal."),
           ("fig", mal, "Transmission of malaria.", "Four boxes with arrows: an infected person with parasites in the blood, a female Anopheles that bites and takes blood, the parasite growing in the mosquito, the mosquito biting a new person."),
           ("key", "retenir", "Preventing malaria",
            "- Sleep under an **insecticide-treated mosquito net**.\n- Remove or cover **stagnant water** (tins, tyres, blocked gutters) where mosquitoes lay eggs; clear bushes around houses.\n- Use screens on windows and repellents; indoor spraying where recommended.\n- Preventive medicine for pregnant women and young children as advised by health services."),
           ("key", "attention", "Treatment (flag for health review)",
            "Anyone with fever should go to a health centre for a **test**. Treatment uses **antimalarial drugs** prescribed by a health worker, and the whole course must be taken. Antibiotics do not work against malaria because the parasite is not a bacterium."),
           ("example", "Find the vector", "A family lives near a swamp in Douala. Several children have fever and shivering. Name the pathogen, the vector and one prevention method.",
            ["The disease is malaria; the pathogen is *Plasmodium*.", "The vector is the female *Anopheles* mosquito; they should sleep under treated nets and drain stagnant water."],
            "Plasmodium; Anopheles; mosquito nets"),
           ("example", "Why drain puddles?", "Explain why clearing stagnant water reduces malaria cases.",
            ["Mosquito eggs, larvae and pupae live in still water.", "Without breeding sites fewer mosquitoes survive, so fewer people are bitten and the parasite is passed on less."],
            "It removes mosquito breeding sites.")],
          [("mcq", "Malaria is transmitted by:", "female Anopheles mosquito", ["houseflies", "male mosquitoes", "contaminated water"], "Only the female Anopheles feeds on blood and transmits it."),
           ("match", "Match each disease with its pathogen type.", [("malaria", "protoctist"), ("cholera", "bacterium"), ("HIV", "virus"), ("ringworm", "fungus")], "Standard pathogen types."),
           ("tf", "Antibiotics are the correct treatment for malaria.", False, "Malaria is caused by a protoctist and needs antimalarial drugs."),
           ("mcq", "A vector is:", "an organism that carries a pathogen", ["a drug", "a type of virus", "a vaccine"], "Definition."),
           ("open", "Describe three ways of controlling malaria in a village.", "Sleep under treated mosquito nets; drain stagnant water to remove breeding sites; use window screens and repellents; seek early testing and treatment at a health centre.", ["1 mark each, maximum 3"]),
           ("prob", "In a town, cases of malaria rise after the rains.", [("mcq", "A likely reason is:", "more stagnant water for mosquito breeding", ["fewer mosquitoes", "colder weather kills parasites", "more vaccines"], "Rain creates puddles."), ("mcq", "The parasite lives in the person's:", "red blood cells", ["bones", "skin only", "kidneys only"], "Plasmodium infects red cells."), ("open", "Why should a child with fever be taken to a health centre quickly?", "Malaria can become severe in children; a test shows the cause and early treatment prevents complications.", ["1 mark: risk of severe malaria", "1 mark: test and early treatment"], 2)])],
          [("Plasmodium belongs to the:", "protoctists", ["bacteria", "viruses", "fungi"], "Single-celled with nucleus."),
           ("Where do the young stages of a mosquito develop?", "in still water", ["on dry ground", "in tree bark", "inside bones"], "Eggs, larvae and pupae live in water."),
           ("Which is a symptom of malaria?", "fever and shivering", ["broken bones", "hair loss", "poor eyesight"], "Fever."),
           ("A treated bed net:", "protects against mosquito bites", ["cures malaria", "kills the parasite in the body", "is a vaccine"], "Prevention."),
           ("The vector of malaria is the:", "female Anopheles", ["housefly", "tsetse fly", "cockroach"], "Female Anopheles.")],
          ["HEALTH CONTENT: review by a health worker, and check current national malaria guidance (nets, preventive treatments).", "Other vectors (tsetse fly, sleeping sickness) not covered."])

    ab = [(0, 0), (5, 2), (10, 12), (15, 25), (22, 15), (30, 6)]
    ab2 = [(0, 0), (2, 8), (4, 45), (7, 90), (12, 60), (20, 30), (30, 15)]
    ax = lambda t: 70 + t * 11
    ay = lambda v: 200 - v * 1.7
    imm = S(axes(70, 200, 420, 20, xl="days after exposure", yl="antibody level", ticks_x=[(ax(t), str(t)) for t in (0, 10, 20, 30)]) +
            [polyline([(ax(t), ay(v)) for t, v in ab], "blue"), polyline([(ax(t), ay(v)) for t, v in ab2], "red"),
             T(ax(22), ay(15) - 12, "first infection", size=11, anchor="start", color="blue"), T(ax(7) + 10, ay(90), "second exposure", size=11, anchor="start", color="red")], 480, 250)
    wb = grid(["CHOLERA|Vibrio cholerae (bacterium)|dirty water and food", "TYPHOID|Salmonella Typhi (bacterium)|dirty water and food", "TUBERCULOSIS|Mycobacterium (bacterium)|droplets in the air"], cols=1, w=330, bh=52, size=11, colors=["lightblue", "lightgreen", "lightorange"])
    build(ch, "infections", "Cholera, typhoid, tuberculosis and immunity", 30,
          ["Describe the cause, spread, symptoms and prevention of cholera, typhoid and tuberculosis.",
           "Explain how the body defends itself against pathogens.",
           "Explain vaccination and why antibiotics must be used carefully."],
          [("key", "retenir", "Water-borne diseases",
            "**Cholera** (the bacterium *Vibrio cholerae*) and **typhoid** (*Salmonella* Typhi) spread by **contaminated water and food**, usually where sewage mixes with drinking water.\n\n- **Cholera**: sudden watery diarrhoea and vomiting that can cause fatal **dehydration**. Treatment: **oral rehydration solution** (clean water with salts and sugar) and medical care.\n- **Typhoid**: prolonged fever, headache, stomach pain."),
           ("key", "retenir", "Tuberculosis (TB)",
            "**TB** (*Mycobacterium tuberculosis*) infects the lungs. It spreads through **droplets** when an infected person coughs or sneezes. Symptoms: a cough lasting weeks, weight loss, night sweats, tiredness. It is treated by a **long course of several antibiotics** under a health worker's supervision. The **BCG vaccine** protects young children. Poor ventilation and overcrowding help it spread."),
           ("fig", wb, "Three bacterial diseases.", "Three boxes: cholera and typhoid caused by bacteria in dirty water and food, and tuberculosis caused by a bacterium spread by droplets in the air."),
           ("key", "retenir", "Prevention of water-borne disease",
            "Drink **treated or boiled** water; build **latrines** away from wells; wash hands with soap after the toilet and before eating; cover food and keep flies away; wash fruit and vegetables; vaccines exist for cholera and typhoid in some situations. Good sanitation is the most effective protection."),
           ("key", "retenir", "Defence and vaccination",
            "The body's barriers: **skin**, **mucus and cilia**, **stomach acid**, and **tears**. Inside, **phagocytes** engulf microbes and **lymphocytes** make **antibodies**. A **vaccine** contains dead, weakened or part of a pathogen; the body makes antibodies and **memory cells** without getting ill. If the real pathogen arrives, the **second response is faster and stronger**.\n\n**Antibiotics** kill bacteria, not viruses; misuse leads to resistance."),
           ("fig", imm, "Antibody level after first and second exposure (illustrative).", "A graph: the blue curve of the first infection rises slowly to a low peak; the red curve of a second exposure rises faster to a much higher peak."),
           ("key", "pieges", "Common mistakes",
            "- Antibiotics do not work on viruses (colds, flu, HIV, measles).\n- Vaccination does not cure an existing illness; it prepares the body beforehand.\n- Boiling is for killing pathogens in water; filtering alone may not remove them."),
           ("example", "Rainy-season outbreak", "After floods in a town, many people have watery diarrhoea. Name a likely disease, how it spread and what to give.",
            ["Cholera is likely; sewage mixed with drinking water.", "Give oral rehydration solution and take the patient to a health centre; stress clean water and handwashing."],
            "Cholera from contaminated water; oral rehydration and medical care"),
           ("example", "Reading the antibody graph", "Using the graph above: what is the peak antibody level for the first infection and the second exposure, and how many times higher is the second peak?",
            ["First peak is 25 units; second peak is 90 units.", "90 ÷ 25 = 3.6 times higher."],
            "25 and 90, so 3.6 times higher")],
          [("mcq", "Cholera is spread mainly by:", "contaminated water", ["mosquito bites", "droplets from coughing", "blood transfusion only"], "It is a water-borne disease."),
           ("match", "Match the disease to a prevention method.", [("cholera", "clean drinking water"), ("TB", "BCG vaccination of children"), ("typhoid", "washing hands with soap"), ("malaria", "sleeping under a net")], "Standard prevention methods."),
           ("num", "A first antibody peak is 25 units and the second peak is 90 units. How many times higher is the second peak?", 3.6, "90 ÷ 25 = 3.6.", 0.01),
           ("mcq", "Why is a vaccine given before infection?", "it produces memory cells that respond quickly to the real pathogen", ["it kills all the microbes in the blood", "it replaces antibiotics", "it makes the skin thicker"], "Immunity needs antibodies and memory cells before infection."),
           ("open", "Explain why antibiotics should not be used to treat a cold.", "A cold is caused by a virus; antibiotics only kill bacteria, so they do not help, and unnecessary use can lead to antibiotic-resistant bacteria.", ["1 mark: cold is viral", "1 mark: antibiotics work on bacteria only", "1 mark: resistance risk"]),
           ("prob", "A school in Maroua reports typhoid among students sharing a well.", [("mcq", "Which is the most likely source?", "well water contaminated by sewage", ["air from the classroom", "mosquitoes", "chalk dust"], "Typhoid is water- and food-borne."), ("mcq", "Which measure is best to stop the spread?", "boiling or treating drinking water and washing hands", ["closing windows", "using perfumes", "sleeping longer"], "Break the route of transmission."), ("open", "Suggest two measures the school could take.", "Build and use latrines away from the well; supply treated water; handwashing stations with soap; health education.", ["1 mark each, maximum 2"], 2)])],
          [("TB mainly affects the:", "lungs", ["skin", "kidneys", "bones only"], "Lungs."),
           ("Which does NOT help prevent water-borne disease?", "drinking from streams without treatment", ["boiling water", "latrines", "handwashing"], "Untreated water spreads disease."),
           ("Antibodies are made by:", "lymphocytes", ["red blood cells", "platelets", "kidneys"], "Lymphocytes."),
           ("A vaccine contains:", "weakened or dead pathogens or their antigens", ["antibiotics", "vitamins", "antibodies only"], "To trigger immunity."),
           ("Oral rehydration solution is used to treat:", "dehydration caused by diarrhoea", ["TB", "malaria", "broken bones"], "ORS replaces water and salts.")],
          ["HEALTH CONTENT: all disease facts, prevention and treatment statements must be reviewed by a health worker; vaccination schedules (BCG, cholera, typhoid) must be checked against Cameroon's Expanded Programme on Immunisation.", "Antibody graph is illustrative only."])
