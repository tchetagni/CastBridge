from biolib import *


def add(p):
    def L(slug):
        for c in p.chapters:
            for l in c.lessons:
                if l.slug == slug: return l
        raise KeyError(slug)

    pot = 48 / 16
    assert pot == 3
    q1 = L("transpiration").problem(
        "Question 1 — Transport in plants (5 marks). A student uses a potometer to measure water uptake by a leafy shoot of a tomato plant.",
        [part_mcq("Which change would INCREASE the rate at which the bubble moves?", "placing the shoot in a draught from a fan", ["covering the leaves with a plastic bag", "putting the shoot in a cold dark cupboard", "spreading petroleum jelly on both leaf surfaces"], "Wind removes water vapour from around the leaves, so transpiration and uptake increase. The other changes reduce it.", points=1),
         part_num("In still air the bubble moves 48 mm in 16 minutes. Calculate the rate of movement in mm per minute.", pot, "48 ÷ 16 = 3 mm per minute (1 mark for the working, 1 for the answer).", tolerance=0.01, unit="mm/min", points=2),
         part_open("Explain why the water taken up is only an estimate of the water lost by transpiration.", "Some of the water is used in photosynthesis and some remains in the cells to keep them turgid, so uptake is slightly different from the volume lost as vapour.", ["1 mark: water used in photosynthesis or kept in cells", "1 mark: so uptake is not exactly equal to loss"], points=2)],
        tier="examen", mock=True)
    q2 = L("nervous").problem(
        "Question 2 — Coordination (5 marks). A girl in Limbe touches a hot cooking pot and pulls her hand away at once.",
        [part_mcq("Which neurone carries the impulse from the skin receptor to the spinal cord?", "sensory neurone", ["motor neurone", "relay neurone", "effector neurone"], "Sensory neurones carry impulses from receptors to the CNS.", points=1),
         part_tf("True or false: the response needs a decision by the cerebrum before the hand moves.", False, "A reflex action is automatic; the brain is informed afterwards.", points=1),
         part_open("Describe the pathway of the impulse in this reflex arc, from stimulus to response.", "The heat stimulates receptors in the skin; impulses pass along a sensory neurone to the spinal cord; a relay neurone passes them to a motor neurone; the motor neurone carries them to the arm muscle (effector), which contracts and pulls the hand away.", ["1 mark: receptor in the skin / sensory neurone to spinal cord", "1 mark: relay neurone in the spinal cord", "1 mark: motor neurone to muscle which contracts"], points=3)],
        tier="examen", mock=True)
    en = 40 * 17 + 25 * 17 + 10 * 37
    assert en == 1475
    q3 = L("food-tests").problem(
        "Question 3 — Nutrition (5 marks). A plate of rice, beans and fish stew contains 40 g carbohydrate, 25 g protein and 10 g fat. Use 17 kJ per g for carbohydrate and protein and 37 kJ per g for fat.",
        [part_mcq("A sample of the stew turns purple with Biuret reagent. This shows that it contains:", "protein", ["starch", "reducing sugar", "fat"], "Biuret gives a purple colour with protein.", points=1),
         part_num("Calculate the total energy in the plate, in kJ.", en, "40 × 17 = 680; 25 × 17 = 425; 10 × 37 = 370; total = 1475 kJ (1 mark for the working, 1 for the answer).", tolerance=0, unit="kJ", points=2),
         part_open("Explain what is meant by a balanced diet.", "A diet that contains all the food classes (carbohydrate, protein, fat, vitamins, minerals, fibre and water) in the correct proportions for the person's age, sex and activity.", ["1 mark: all food classes (with fibre and water)", "1 mark: in the right amounts for the person"], points=2)],
        tier="examen", mock=True)
    q4 = L("flower").problem(
        "Question 4 — Reproduction in plants (5 marks). A flower of a hibiscus plant has large red petals and a sticky stigma.",
        [part_mcq("The part of the flower that produces pollen is the:", "anther", ["stigma", "ovary", "style"], "The anther makes the pollen grains.", points=1),
         part_tf("True or false: pollination and fertilisation mean the same thing.", False, "Pollination is the transfer of pollen to the stigma; fertilisation is the fusion of the male and female nuclei.", points=1),
         part_open("Describe what happens from the landing of a pollen grain on the stigma until fertilisation.", "The pollen grain grows a pollen tube down the style to the ovary and into an ovule; the male nucleus passes down the tube and fuses with the egg nucleus to form a zygote.", ["1 mark: pollen tube grows down the style", "1 mark: reaches the ovule", "1 mark: male nucleus fuses with the egg nucleus"], points=3)],
        tier="examen", mock=True)
    p.mock("2", "GCE O Level mock 2 — Biology (systems and health)", 60,
           "Answer all four questions. Marks are shown for each part; the paper is marked out of 20. This is an original practice paper; its format is to be checked against the official texts of the Cameroon GCE Board.",
           [("Question 1 — Transport in plants", [q1]), ("Question 2 — Coordination", [q2]), ("Question 3 — Nutrition", [q3]), ("Question 4 — Reproduction in plants", [q4])])
