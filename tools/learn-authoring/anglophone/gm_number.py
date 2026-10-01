from _kit import *

def build(p):
    ch = p.chapter("number", "Number: fractions, ratio, indices and sequences", "O Level Mathematics — Number and numeration (to be checked)")

    # ---------------------------------------------------------------- fractions
    f1 = Fraction(2, 3) + Fraction(3, 4) - Fraction(1, 2); assert f1 == Fraction(11, 12)
    f2 = Fraction(7, 8) * Fraction(4, 21); assert f2 == Fraction(1, 6)
    pc1 = 35 * 24000 / 100; assert pc1 == 8400
    pc2 = 12000 * 1.15; assert abs(pc2 - 13800) < 1e-9
    d1 = Fraction(5, 6) / Fraction(5, 12); assert d1 == 2
    mix = Fraction(7, 2) + Fraction(5, 4); assert mix == Fraction(19, 4)
    pcchg = (1560 - 1200) / 1200 * 100; assert abs(pcchg - 30) < 1e-9
    L = lesson(ch, "fractions", "Fractions, decimals and percentages",
        ["Add, subtract, multiply and divide fractions and mixed numbers.", "Convert between fractions, decimals and percentages.", "Find a percentage of a quantity and a percentage change."],
        [("definition", "Equivalent forms", "A fraction, a decimal and a percentage can describe the same number: $\\frac{3}{8} = 0.375 = 37.5\\%$. To go from a fraction to a percentage, divide then multiply by 100; from a percentage to a fraction, put it over 100 and simplify."),
         ("methode", "Method: operations on fractions", "- **Add or subtract**: write both over the same denominator (the LCM), then combine the numerators.\n- **Multiply**: multiply numerators and denominators; simplify first by cancelling.\n- **Divide**: multiply by the *reciprocal* of the second fraction.\n- **Mixed numbers**: convert to improper fractions first.", ),
         ("formule", "Percentage rules", "Percentage of a quantity, and percentage change:", "\\frac{p}{100} \\times Q \\qquad \\text{change } \\% = \\frac{\\text{new} - \\text{old}}{\\text{old}} \\times 100"),
         ("pieges", "Common mistakes", "- Adding denominators: $\\frac{1}{2} + \\frac{1}{3}$ is **not** $\\frac{2}{5}$.\n- Dividing by the wrong fraction: flip only the *second* fraction.\n- Percentage change is always divided by the **old** (original) value.")],
        [("Example 1: mixed operations", "Work out $\\frac{2}{3} + \\frac{3}{4} - \\frac{1}{2}$.",
          [("The LCM of 3, 4 and 2 is 12:", "\\frac{8}{12} + \\frac{9}{12} - \\frac{6}{12}"), ("Combine the numerators: 8 + 9 − 6 = 11.", "\\frac{11}{12}")], "**11/12**"),
         ("Example 2: percentages in FCFA", "(a) Find 35% of 24 000 FCFA. (b) A bag of rice costs 12 000 FCFA and its price rises by 15%. Find the new price.",
          ["(a) 35% of 24 000 = 0.35 × 24 000 = 8 400 FCFA.", "(b) Multiplier for +15% is 1.15: 12 000 × 1.15 = 13 800 FCFA.", "Check: 15% of 12 000 = 1 800 and 12 000 + 1 800 = 13 800."], "**(a) 8 400 FCFA; (b) 13 800 FCFA.**")],
        [N("Work out $\\frac{5}{6} \\div \\frac{5}{12}$.", 2, "Multiply by the reciprocal: $\\frac{5}{6} \\times \\frac{12}{5} = \\frac{60}{30} = 2$."),
         M("Express $\\frac{3}{8}$ as a percentage.", "37.5%", ["38%", "3.8%", "0.375%"], "3 ÷ 8 = 0.375, and 0.375 × 100 = **37.5%**."),
         M("Write $3\\frac{1}{2} + 1\\frac{1}{4}$ as a single fraction.", "$\\frac{19}{4}$", ["$\\frac{4}{6}$", "$\\frac{18}{4}$", "$4\\frac{1}{6}$"], "$\\frac{7}{2} + \\frac{5}{4} = \\frac{14}{4} + \\frac{5}{4} = \\frac{19}{4} = 4\\frac{3}{4}$.")],
        [N("A trader buys a sack of cassava flour for 1 200 FCFA and sells it for 1 560 FCFA. Find the percentage increase.", 30, "Change = 360. $\\frac{360}{1200} \\times 100 = 30\\%$.", unit="%"),
         M("A phone costs 85 000 FCFA after a 15% reduction. What was the original price?", "100 000 FCFA", ["97 750 FCFA", "72 250 FCFA", "99 000 FCFA"], "85 000 is 85% of the original: 85 000 ÷ 0.85 = **100 000 FCFA**. Taking 15% of 85 000 is the usual error.")],
        P("In a school in Bamenda, $\\frac{3}{5}$ of the 400 students are girls. Of the girls, 25% are in Form 5.",
          [pn("How many girls are there?", 240, "$\\frac{3}{5} \\times 400 = 240$.", pts=1),
           pn("How many girls are in Form 5?", 60, "25% of 240 = 60.", pts=1),
           pn("What fraction of ALL students are Form 5 girls? Enter it as a percentage.", 15, "60 ÷ 400 = 0.15 = 15%.", unit="%", pts=2)]),
        [("Which is the largest: 0.6, $\\frac{5}{8}$, 61%, $\\frac{3}{5}$?", "$\\frac{5}{8}$", ["0.6", "61%", "$\\frac{3}{5}$"], "0.6; 0.625; 0.61; 0.6: the largest is 5/8 = 0.625."),
         ("Simplify $\\frac{3}{4} \\times \\frac{8}{9}$.", "$\\frac{2}{3}$", ["$\\frac{11}{13}$", "$\\frac{27}{32}$", "$\\frac{3}{2}$"], "Cancel: $\\frac{3 \\times 8}{4 \\times 9} = \\frac{24}{36} = \\frac{2}{3}$."),
         ("10% of 4 500 FCFA is:", "450 FCFA", ["45 FCFA", "4 050 FCFA", "4 950 FCFA"], "10% = ÷10: 450."),
         ("To increase a price by 8% you multiply by:", "1.08", ["0.08", "0.92", "8"], "100% + 8% = 108% = 1.08."),
         ("$0.35$ as a fraction in lowest terms is:", "$\\frac{7}{20}$", ["$\\frac{35}{10}$", "$\\frac{3}{5}$", "$\\frac{35}{1000}$"], "0.35 = 35/100 = 7/20.")],
        ill=(frac_bar(3, 8, "3/8 = 0.375 = 37.5%"), "3 of 8 equal parts are shaded", "A bar split into eight equal parts with three parts shaded, showing three eighths"),
        notes=["Calculator use for fractions is allowed at O Level but is to be checked against the current regulations."], minutes=25)

    # ---------------------------------------------------------------- ratio
    r1 = 840000 * 3 / 7; assert r1 == 360000
    inv = 6 * 10 / 4; assert inv == 15
    L = lesson(ch, "ratio", "Ratio, rate and proportion",
        ["Simplify a ratio and share a quantity in a given ratio.", "Solve direct and inverse proportion problems.", "Work with rates such as speed and unit price."],
        [("definition", "Ratio", "A **ratio** compares quantities of the same kind: 12 : 18 simplifies to 2 : 3 (divide both by 6). To **share** an amount in the ratio a : b, add the parts (a + b), find one part, then multiply."),
         ("propriete", "Direct and inverse proportion", "**Direct**: when one quantity doubles, so does the other (cost of rice and mass). Use a unit value. **Inverse**: when one doubles, the other halves (workers and days). The product stays constant: $workers \\times days = constant$."),
         ("pieges", "Common mistakes", "- Mixing units in a ratio: convert to the same unit first (1 m : 40 cm is 100 : 40).\n- Treating an inverse problem as direct: more workers means **fewer** days.\n- Forgetting the total number of parts when sharing.")],
        [("Example 1: sharing in a ratio", "Two partners in Bafoussam share a profit of 840 000 FCFA in the ratio 3 : 4. How much does the first partner get?",
          ["Total parts = 3 + 4 = 7, so one part = 840 000 ÷ 7 = 120 000 FCFA.", "First partner: 3 × 120 000 = 360 000 FCFA.", "Check: the second gets 480 000 and 360 000 + 480 000 = 840 000."], "**360 000 FCFA.**"),
         ("Example 2: inverse proportion", "6 workers can build a wall in 10 days. How long would 4 workers take, working at the same rate?",
          ["Inverse proportion: workers × days is constant: 6 × 10 = 60 worker-days.", "With 4 workers: 60 ÷ 4 = 15 days.", "Fewer workers, so more days: the answer is sensible."], "**15 days.**")],
        [N("Simplify the ratio 45 : 60 and give the first number.", 3, "Divide by 15: 45 : 60 = 3 : 4. The first number is 3."),
         N("Share 2 400 FCFA between Ada and Bih in the ratio 5 : 3. How much does Ada get?", 1500, "8 parts: one part = 300 FCFA; Ada gets 5 × 300 = 1 500 FCFA.", unit="FCFA"),
         M("5 kg of tomatoes cost 3 500 FCFA. What is the cost of 8 kg?", "5 600 FCFA", ["5 000 FCFA", "2 187.5 FCFA", "4 375 FCFA"], "Unit price 700 FCFA/kg; 8 × 700 = 5 600 FCFA.")],
        [N("A car travels 150 km in 2.5 hours at constant speed. How many kilometres does it travel in 4 hours?", 240, "Speed = 150 ÷ 2.5 = 60 km/h; 60 × 4 = 240 km.", unit="km"),
         M("The ratio of boys to girls in a class is 4 : 5 and there are 36 students. How many boys?", "16", ["20", "9", "28"], "9 parts, 36 ÷ 9 = 4 per part: boys = 16. (Girls = 20.)")],
        P("A recipe for puff-puff for 8 people uses 600 g of flour and 4 eggs.",
          [pn("How many grams of flour are needed for 12 people?", 900, "Direct proportion: 600 ÷ 8 = 75 g per person; 75 × 12 = 900.", unit="g", pts=1),
           pn("How many eggs are needed for 12 people?", 6, "4 ÷ 8 = 0.5 egg per person; 0.5 × 12 = 6.", pts=1),
           pn("Flour costs 700 FCFA per kg. Find the cost of the flour for 12 people.", 630, "0.9 kg × 700 = 630 FCFA.", unit="FCFA", pts=2)]),
        [("Which ratio equals 18 : 24?", "3 : 4", ["2 : 3", "6 : 8 : 1", "9 : 8"], "Divide by 6."),
         ("If 5 pens cost 750 FCFA, 9 pens cost:", "1 350 FCFA", ["1 250 FCFA", "833 FCFA", "1 500 FCFA"], "150 per pen × 9."),
         ("It takes 12 men 5 days. How many days for 15 men (same rate)?", "4", ["6.25", "3", "5"], "12 × 5 = 60; 60 ÷ 15 = 4."),
         ("Express 1 m : 40 cm in simplest form.", "5 : 2", ["1 : 40", "25 : 1", "5 : 1"], "100 cm : 40 cm = 5 : 2."),
         ("Divide 90 in the ratio 2 : 3. The larger share is:", "54", ["36", "60", "45"], "5 parts of 18 each: 3 × 18 = 54.")],
        ill=(shapes([RECT(40, 40, 120, 40, fill="lightblue"), RECT(160, 40, 160, 40, fill="lightorange"), T(100, 65, "3 parts", 14), T(240, 65, "4 parts", 14),
                     T(200, 110, "840 000 FCFA shared 3 : 4", 15), T(100, 30, "360 000", 13), T(240, 30, "480 000", 13)], 360, 130),
             "A bar divided into 3 and 4 parts", "A bar split into a 3-part block and a 4-part block, showing 360 000 and 480 000 FCFA"), minutes=25)

    # ---------------------------------------------------------------- standard form & indices
    L = lesson(ch, "indices", "Indices and standard form",
        ["Use the laws of indices including zero, negative and fractional indices.", "Write numbers in standard form and calculate with them."],
        [("propriete", "Laws of indices", "For any non-zero base a:\n- $a^m \\times a^n = a^{m+n}$ and $a^m \\div a^n = a^{m-n}$\n- $(a^m)^n = a^{mn}$\n- $a^0 = 1$ and $a^{-n} = \\frac{1}{a^n}$\n- $a^{1/n} = \\sqrt[n]{a}$ and $a^{m/n} = (\\sqrt[n]{a})^m$"),
         ("definition", "Standard form", "A number is in **standard form** when written $a \\times 10^n$ with $1 \\leq a < 10$ and n an integer. Large numbers have positive n (Cameroon's population is about $2.8 \\times 10^7$); small numbers have negative n ($0.00045 = 4.5 \\times 10^{-4}$)."),
         ("pieges", "Common mistakes", "- $2^3 \\times 2^4$ is $2^7$, not $4^7$: **add** the indices, keep the base.\n- $(-2)^2 = 4$ but $-2^2 = -4$.\n- $3 \\times 10^{-2}$ is 0.03, not −30.\n- In standard form a must be between 1 and 10: $12 \\times 10^3$ is not standard.")],
        [("Example 1: laws of indices", "Evaluate (a) $8^{2/3}$ (b) $\\frac{2^5 \\times 2^{-2}}{2^4}$.",
          [("(a) $8^{1/3} = 2$, so $8^{2/3} = 2^2 = 4$.", ""), ("(b) Numerator: $2^{5-2} = 2^3$; divide by $2^4$: $2^{3-4} = 2^{-1}$.", "2^{-1} = \\frac{1}{2}")], "**(a) 4; (b) 1/2.**"),
         ("Example 2: standard form", "Calculate $(3 \\times 10^5) \\times (4 \\times 10^{-2})$ and give the answer in standard form.",
          ["Multiply the numbers: 3 × 4 = 12; add the powers: $10^{5-2} = 10^3$.", "This gives $12 \\times 10^3$, which is not standard (12 is not less than 10).", "Rewrite: $12 \\times 10^3 = 1.2 \\times 10^4$."], "**$1.2 \\times 10^4$** (= 12 000).")],
        [M("Write 0.00045 in standard form.", "$4.5 \\times 10^{-4}$", ["$4.5 \\times 10^{-3}$", "$45 \\times 10^{-5}$", "$4.5 \\times 10^{4}$"], "Move the decimal point 4 places right to get 4.5, so the power is −4."),
         N("Evaluate $2^{-3} \\times 16$.", 2, "$2^{-3} = \\frac{1}{8}$ and $\\frac{16}{8} = 2$."),
         M("Simplify $\\frac{x^7 \\times x^2}{x^4}$.", "$x^5$", ["$x^{13}$", "$x^{3}$", "$x^{8}$"], "$x^{7+2-4} = x^5$.")],
        [N("Find x if $3^{x} = \\frac{1}{81}$.", -4, "$81 = 3^4$, so $\\frac{1}{81} = 3^{-4}$ and x = −4."),
         N("The distance from Earth to the Sun is about $1.5 \\times 10^8$ km. Light travels at about $3 \\times 10^5$ km/s. How many seconds does light take (to the nearest second)?", 500, "$\\frac{1.5 \\times 10^8}{3 \\times 10^5} = 0.5 \\times 10^3 = 500$ s.", unit="s")],
        P("Cameroon's population is taken as $2.8 \\times 10^7$ and the area of a farm is $4 \\times 10^{-2}$ km².",
          [pm("Write 28 000 000 × 3 in standard form.", "$8.4 \\times 10^7$", ["$84 \\times 10^6$", "$8.4 \\times 10^6$", "$0.84 \\times 10^8$"], "$2.8 \\times 3 = 8.4$, so $8.4 \\times 10^7$. $84 \\times 10^6$ has the right value but is not in standard form.", 2),
           pn("The farm area in m² (1 km² = $10^6$ m²) is $4 \\times 10^{n}$. Find n.", 4, "$4 \\times 10^{-2} \\times 10^6 = 4 \\times 10^4$ m²: n = 4.", pts=2)]),
        [("$a^0$ (a ≠ 0) equals:", "1", ["0", "a", "undefined"], "Any non-zero number to the power 0 is 1."),
         ("$(2^3)^2$ equals:", "64", ["32", "128", "25"], "$2^6 = 64$."),
         ("$16^{1/2}$ equals:", "4", ["8", "256", "1/4"], "Square root of 16."),
         ("$5.6 \\times 10^3$ as an ordinary number:", "5 600", ["56 000", "560", "0.0056"], "Move the point 3 places right."),
         ("$9^{-1}$ equals:", "$\\frac{1}{9}$", ["−9", "−1/9", "9"], "$a^{-1} = 1/a$.")],
        ill=(shapes([T(200, 40, "6 500 000 = 6.5 × 10⁶", 18, bold=True), T(200, 80, "0.0065 = 6.5 × 10⁻³", 18, bold=True),
                     LINE(60, 120, 340, 120, arrow="both"), T(80, 150, "small", 14), T(320, 150, "large", 14),
                     T(200, 150, "10⁻³   1   10⁶", 14)], 400, 170), "Standard form for large and small numbers", "Two numbers written in standard form, one large with a positive power of ten and one small with a negative power"), minutes=25, notes=["The population figure (about 28 million) is approximate: check against a current official estimate before release."])

    # ---------------------------------------------------------------- approximation
    L = lesson(ch, "approximation", "Approximation and estimation",
        ["Round to decimal places and significant figures.", "Estimate a calculation using rounded values.", "Find the percentage error and give bounds of a measurement."],
        [("definition", "Decimal places and significant figures", "To round to **n decimal places**, look at the digit after the nth: 5 or more rounds up. **Significant figures** count from the first non-zero digit: 0.00463 to 2 s.f. is 0.0046; 5 346 to 2 s.f. is 5 300."),
         ("methode", "Estimating and error", "To **estimate**, round every number to 1 significant figure, then calculate. The **bounds** of a measurement correct to the nearest unit are ±0.5 unit: 12 cm to the nearest cm lies between 11.5 and 12.5. Percentage error = $\\frac{\\text{error}}{\\text{true value}} \\times 100$."),
         ("pieges", "Common mistakes", "- Zeros used only as place holders must stay: 5 346 to 2 s.f. is 5 300, not 53.\n- Rounding in the middle of a long calculation: round only at the end.\n- Rounding the estimate to 1 d.p. instead of 1 s.f.")],
        [("Example 1: rounding", "Round 0.0463 (a) to 2 decimal places (b) to 2 significant figures. Round 7 849 (c) to 2 s.f.",
          ["(a) 0.0463: the third decimal is 6, so round up: 0.05.", "(b) The first significant digit is 4: keep 4 and 6, next digit 3 rounds down: 0.046.", "(c) 7 849: keep 7 8, next digit 4 rounds down: 7 800."], "**(a) 0.05 (b) 0.046 (c) 7 800.**"),
         ("Example 2: estimation", "Estimate $\\frac{7.2 \\times 3.9}{0.51}$ using 1 significant figure, and compare with the exact value.",
          ["Round: 7.2 → 7, 3.9 → 4, 0.51 → 0.5.", "Estimate: (7 × 4) ÷ 0.5 = 28 ÷ 0.5 = 56.", "Exact: 28.08 ÷ 0.51 = 55.06 (to 2 d.p.), close to the estimate."], "**Estimate 56; exact ≈ 55.06.**")],
        [M("3.0679 correct to 3 significant figures is:", "3.07", ["3.06", "3.068", "3.1"], "Third s.f. is 6, next digit 7 rounds it up: 3.07."),
         N("Round 45 678 to the nearest hundred.", 45700, "The tens digit is 7, so round up: 45 700."),
         M("Estimate $19.8 \\times 4.1$ by rounding each number to 1 s.f.", "80", ["800", "8", "100"], "20 × 4 = 80.")],
        [N("A rope is measured as 8.4 m to the nearest 0.1 m. What is its greatest possible length? (Give the upper bound.)", 8.45, "Error is ±0.05 so the upper bound is 8.45 m.", tol=0.0001, unit="m"),
         N("A field's length is measured as 50 m but its true length is 48 m. Find the percentage error.", 4.17, "Error = 2; $\\frac{2}{48} \\times 100 = 4.1\\overline{6}$, so 4.17% (2 d.p.).", tol=0.01, unit="%")],
        P("A rectangle in Limbe has length 12.4 m and width 7.6 m, both correct to the nearest 0.1 m.",
          [pn("Write the area using the given values (to 2 d.p.).", 94.24, "12.4 × 7.6 = 94.24 m².", tol=0.01, unit="m²", pts=1),
           pn("Find the upper bound of the area.", 12.45 * 7.65, "Upper bounds: 12.45 and 7.65: area = 95.2425 m².", tol=0.01, unit="m²", pts=2),
           pn("Find the lower bound of the area (to 2 d.p.).", 12.35 * 7.55, "Lower bounds: 12.35 × 7.55 = 93.2425 m².", tol=0.01, unit="m²", pts=2)]),
        [("Round 0.07049 to 3 significant figures.", "0.0705", ["0.0704", "0.070", "0.071"], "Significant digits start at 7: 7, 0, 4, and the next digit 9 rounds the 4 up: 0.0705."),
         ("A length of 6 m to the nearest metre lies between:", "5.5 and 6.5", ["5 and 7", "5.9 and 6.1", "5.95 and 6.05"], "±0.5 for the nearest unit."),
         ("How many significant figures does 0.0307 have?", "3", ["4", "2", "5"], "3, 0, 7 (leading zeros do not count)."),
         ("Estimate 398 ÷ 0.2.", "2 000", ["200", "20", "1 990"], "400 ÷ 0.2 = 2 000."),
         ("Correct to 1 d.p., 12.349 is:", "12.3", ["12.4", "12.35", "12.0"], "The second decimal is 4 → round down.")],
        ill=(num_line(0, 5, [], 1, hollow=[2.5], filled=[2.3, 2.8], arrow_from=2.3, arrow_to=2.5), "Rounding on a number line", "A number line from 0 to 5 showing numbers rounded to the nearest integer"), minutes=25,
        notes=["Bounds (upper/lower) in products may be beyond the O Level syllabus: check."])
    assert abs(12.45*7.65-95.2425)<1e-9

    # ---------------------------------------------------------------- surds
    L = lesson(ch, "surds", "Surds",
        ["Simplify surds using the product rule.", "Add, subtract and multiply surds.", "Rationalise a denominator of the form $\\frac{a}{\\sqrt{b}}$."],
        [("definition", "Surd", "A **surd** is a root that is irrational, such as $\\sqrt{2}$ or $\\sqrt{7}$. $\\sqrt{9}$ is not a surd because it equals 3. Rules: $\\sqrt{ab} = \\sqrt{a}\\sqrt{b}$ and $\\sqrt{\\frac{a}{b}} = \\frac{\\sqrt{a}}{\\sqrt{b}}$."),
         ("methode", "Simplifying and rationalising", "- **Simplify**: take out the largest square factor, e.g. $\\sqrt{50} = \\sqrt{25 \\times 2} = 5\\sqrt{2}$.\n- **Add like surds** only: $3\\sqrt{2} + 5\\sqrt{2} = 8\\sqrt{2}$.\n- **Rationalise** by multiplying top and bottom by the surd: $\\frac{6}{\\sqrt{3}} = \\frac{6\\sqrt{3}}{3} = 2\\sqrt{3}$."),
         ("pieges", "Common mistakes", "- $\\sqrt{a + b}$ is **not** $\\sqrt{a} + \\sqrt{b}$ ($\\sqrt{9 + 16} = 5$, not 7).\n- $\\sqrt{2} + \\sqrt{3}$ cannot be combined.\n- $(2\\sqrt{3})^2 = 4 \\times 3 = 12$, not $4\\sqrt{3}$.")],
        [("Example 1: simplify", "Simplify $\\sqrt{75} + \\sqrt{12} - \\sqrt{27}$.",
          [("$\\sqrt{75} = 5\\sqrt{3}$, $\\sqrt{12} = 2\\sqrt{3}$, $\\sqrt{27} = 3\\sqrt{3}$.", ""), ("Combine like surds: (5 + 2 − 3)$\\sqrt{3}$.", "4\\sqrt{3}")], "**$4\\sqrt{3}$.**"),
         ("Example 2: rationalise", "Express $\\frac{10}{\\sqrt{5}}$ with a rational denominator, and expand $(3 + \\sqrt{2})(3 - \\sqrt{2})$.",
          ["Multiply top and bottom by √5: $\\frac{10\\sqrt{5}}{5} = 2\\sqrt{5}$.", "Difference of squares: $3^2 - (\\sqrt{2})^2 = 9 - 2 = 7$."], "**$2\\sqrt{5}$ and 7.**")],
        [M("Simplify $\\sqrt{72}$.", "$6\\sqrt{2}$", ["$6\\sqrt{3}$", "$8\\sqrt{3}$", "$36\\sqrt{2}$"], "72 = 36 × 2 so $\\sqrt{72} = 6\\sqrt{2}$."),
         M("Express $\\frac{4}{\\sqrt{2}}$ with a rational denominator.", "$2\\sqrt{2}$", ["$4\\sqrt{2}$", "$\\sqrt{2}$", "$2$"], "$\\frac{4\\sqrt{2}}{2} = 2\\sqrt{2}$."),
         N("Evaluate $(\\sqrt{7})^2 + \\sqrt{16}$.", 11, "7 + 4 = 11.")],
        [N("Expand and simplify $(2 + \\sqrt{3})^2$. Enter the integer part (the term without a surd).", 7, "$4 + 4\\sqrt{3} + 3 = 7 + 4\\sqrt{3}$. The integer part is 7."),
         M("Simplify $\\frac{1}{\\sqrt{3} - 1}$ (multiply by $\\sqrt{3} + 1$).", "$\\frac{\\sqrt{3} + 1}{2}$", ["$\\sqrt{3} + 1$", "$\\frac{\\sqrt{3} - 1}{2}$", "$\\frac{1}{2}$"], "$\\frac{\\sqrt{3}+1}{3 - 1} = \\frac{\\sqrt{3}+1}{2}$.")],
        P("A square has area 50 cm².",
          [pm("Its side length in simplest surd form is:", "$5\\sqrt{2}$ cm", ["$25$ cm", "$2\\sqrt{5}$ cm", "$10\\sqrt{5}$ cm"], "Side = $\\sqrt{50} = 5\\sqrt{2}$.", 1),
           pm("Its diagonal (by Pythagoras) is:", "10 cm", ["$5\\sqrt{2}$ cm", "$10\\sqrt{2}$ cm", "$50$ cm"], "$d^2 = 50 + 50 = 100$, so d = 10 cm.", 2),
           pn("Side length to 2 d.p.", round(math.sqrt(50), 2), "√50 = 7.07 cm.", tol=0.01, unit="cm", pts=1)]),
        [("$\\sqrt{2} \\times \\sqrt{8}$ equals:", "4", ["$\\sqrt{10}$", "16", "$2\\sqrt{2}$"], "$\\sqrt{16} = 4$."),
         ("Which is a surd?", "$\\sqrt{12}$", ["$\\sqrt{16}$", "$\\sqrt{25}$", "$\\sqrt{0.25}$"], "12 is not a perfect square."),
         ("$3\\sqrt{5} + 2\\sqrt{5}$ equals:", "$5\\sqrt{5}$", ["$5\\sqrt{10}$", "$6\\sqrt{5}$", "$10\\sqrt{5}$"], "Add coefficients."),
         ("$\\sqrt{45}$ simplified:", "$3\\sqrt{5}$", ["$5\\sqrt{3}$", "$9\\sqrt{5}$", "$15$"], "45 = 9 × 5."),
         ("$\\frac{6}{\\sqrt{3}}$ equals:", "$2\\sqrt{3}$", ["$6\\sqrt{3}$", "$\\sqrt{3}$", "$3\\sqrt{2}$"], "Multiply by √3/√3 giving 6√3/3.")],
        ill=(shapes([POLY([60, 180, 220, 180, 220, 60], fill="lightblue"), T(140, 200, "1", 15), T(238, 125, "1", 15, anchor="start"), T(120, 105, "√2", 18, color="red", bold=True), T(300, 40, "√2² = 1² + 1²", 14)], 420, 220),
             "√2 as the hypotenuse of a right-angled triangle with legs 1", "A right-angled isosceles triangle with two sides of length 1 and hypotenuse root 2"), minutes=25,
        notes=["Surds and rationalisation with binomial denominators: confirm the extent required for GCE O Level (the Cameroon Board syllabus may only require simple surds)."])

    # ---------------------------------------------------------------- number bases
    def tobase(n, b):
        r = ""
        while n: r = "0123456789"[n % b] + r; n //= b
        return r or "0"
    assert tobase(45, 2) == "101101" and int("101101", 2) == 45
    assert int("342", 5) == 97 and tobase(100, 8) == "144"
    assert tobase(int("1101", 2) + int("1011", 2), 2) == "11000"
    assert int("234", 5) + int("143", 5) == int("432", 5) == 117
    assert int("24", 7) + int("13", 7) == int("40", 7) == 28
    assert int("1011", 2) * int("11", 2) == 33
    assert tobase(97, 4) == "1201" and int("1201", 4) == 97
    L = lesson(ch, "bases", "Number bases",
        ["Convert numbers between base 10 and other bases (2, 5, 8...).", "Add, subtract and multiply in a given base."],
        [("definition", "Place value in base b", "In base b the place values are powers of b: …, $b^3$, $b^2$, b, 1, and only the digits 0 to b − 1 are used. So $342_5 = 3 \\times 5^2 + 4 \\times 5 + 2 = 97_{10}$. The base is written as a small subscript."),
         ("methode", "Method: converting and calculating", "- **To base 10**: multiply each digit by its place value and add.\n- **From base 10**: divide repeatedly by b and read the remainders **from the bottom upwards**.\n- **Arithmetic**: work column by column as usual, but carry when you reach b (not 10)."),
         ("pieges", "Common mistakes", "- Reading the remainders from the top: they must be read from the last to the first.\n- Using a digit equal to or larger than the base ($25_2$ does not exist).\n- Carrying 10 instead of b: in base 2, $1 + 1 = 10_2$.")],
        [("Example 1: base 10 to base 2", "Write 45 in base 2.",
          ["Divide by 2 repeatedly: 45 = 22×2 + 1; 22 = 11×2 + 0; 11 = 5×2 + 1; 5 = 2×2 + 1; 2 = 1×2 + 0; 1 = 0×2 + 1.", "Read the remainders from the bottom up: 1 0 1 1 0 1.", "Check: 32 + 8 + 4 + 1 = 45."], "**$45_{10} = 101101_2$.**"),
         ("Example 2: addition in base 5", "Work out $234_5 + 143_5$.",
          ["Units: 4 + 3 = 7 = 1×5 + 2: write 2, carry 1.", "Fives: 3 + 4 + 1 = 8 = 1×5 + 3: write 3, carry 1.", "Twenty-fives: 2 + 1 + 1 = 4. The answer is 432.", "Check in base 10: 69 + 48 = 117 and $432_5 = 100 + 15 + 2 = 117$."], "**$432_5$.**")],
        [N("Convert $342_5$ to base 10.", 97, "3 × 25 + 4 × 5 + 2 = 75 + 20 + 2 = 97."),
         M("Express 100 in base 8.", "144", ["441", "121", "1100100"], "100 = 1×64 + 4×8 + 4. The remainders 4, 4, 1 read upwards give 144."),
         M("$1101_2 + 1011_2 =$", "$11000_2$", ["$10000_2$", "$10110_2$", "$2112_2$"], "13 + 11 = 24 = 16 + 8 = $11000_2$.")],
        [N("$x = 1011_2 \\times 11_2$. Find x as a number in base 10.", 33, "$1011_2 = 11$ and $11_2 = 3$; 11 × 3 = 33."),
         M("In which base is the sum $24 + 13 = 40$ correct?", "Base 7", ["Base 5", "Base 6", "Base 8"], "Units: 4 + 3 = 7 must give 0 carry 1, so the base is 7. Tens: 2 + 1 + 1 = 4. Correct. In base 8 the answer would be 37.")],
        P("In a computer lesson in Yaoundé, students work with binary numbers.",
          [pn("Write $1201_4$ in base 10... (here 97 is given in base 10) Convert 97 to base 4 and enter the number of digits.", 4, "97 = 1×64 + 2×16 + 0×4 + 1, so $97 = 1201_4$: four digits.", pts=1),
           pm("Which is the correct base-2 form of 13?", "$1101_2$", ["$1011_2$", "$1110_2$", "$1001_2$"], "13 = 8 + 4 + 1 = $1101_2$ ($1011_2$ is 11).", 2),
           pm("Work out $110_2 \\times 11_2$.", "$10010_2$", ["$1010_2$", "$10100_2$", "$1100_2$"], "6 × 3 = 18 = 16 + 2 = $10010_2$.", 2)]),
        [("$101_2$ in base 10 is:", "5", ["101", "3", "7"], "4 + 0 + 1."),
         ("The digit 5 can appear in base:", "6", ["5", "4", "2"], "Digits go from 0 to base − 1."),
         ("$10_2 + 1_2 =$", "$11_2$", ["$10_2$", "$12_2$", "$3_2$"], "2 + 1 = 3 = $11_2$."),
         ("$20_5$ in base 10:", "10", ["20", "25", "5"], "2 × 5 = 10."),
         ("$1 + 1$ in base 2 gives:", "$10_2$", ["2", "$2_2$", "$11_2$"], "Reaching the base, you carry.")],
        ill=(shapes([RECT(40, 40, 60, 50, fill="lightblue"), RECT(100, 40, 60, 50, fill="lightgreen"), RECT(160, 40, 60, 50, fill="lightyellow"), RECT(220, 40, 60, 50, fill="lightorange"),
                     T(70, 30, "5³", 14), T(130, 30, "5²", 14), T(190, 30, "5¹", 14), T(250, 30, "5⁰", 14),
                     T(70, 72, "0", 18), T(130, 72, "3", 18), T(190, 72, "4", 18), T(250, 72, "2", 18), T(160, 125, "342₅ = 75 + 20 + 2 = 97", 15)], 320, 150),
             "Place value columns in base 5", "Four columns labelled five cubed, five squared, five and one with the digits 0, 3, 4, 2"), minutes=25,
        notes=["Binary/other bases: check whether the Cameroon syllabus asks for bases other than 2 and 5 and for subtraction/multiplication."])

    # ---------------------------------------------------------------- sequences
    a1, d = 5, 3; nth = lambda n: a1 + (n - 1) * d
    assert nth(20) == 62 and sum(nth(i) for i in range(1, 11)) == 10 * (2 * 5 + 9 * 3) // 2 == 185
    assert 3 * 2 ** 5 == 96
    assert (-1 + 20 * 0) == -1
    n_target = (101 - 5) // 3 + 1; assert nth(n_target) == 101 and n_target == 33
    L = lesson(ch, "sequences", "Sequences and series",
        ["Find the nth term of a linear (arithmetic) sequence and of a geometric sequence.", "Use the formula for the sum of an arithmetic series."],
        [("formule", "Arithmetic sequences", "Constant difference d, first term a. The nth term and the sum of the first n terms are:", "T_n = a + (n-1)d \\qquad S_n = \\frac{n}{2}\\left(2a + (n-1)d\\right)"),
         ("formule", "Geometric sequences", "Constant ratio r (each term is multiplied by r). The nth term is:", "T_n = a r^{n-1}"),
         ("methode", "Finding the rule", "Find the differences between consecutive terms. If they are constant (d), the rule is $T_n = dn + (a - d)$. If the **ratios** are constant, the sequence is geometric. To check, test the rule on the first two terms."),
         ("pieges", "Common mistakes", "- Using $T_n = a + nd$: the first term has no d added (n − 1 differences).\n- Mixing up difference and ratio.\n- Forgetting that n must be a whole number: if the rule gives n = 12.5, the number is not in the sequence.")],
        [("Example 1: arithmetic", "The sequence 5, 8, 11, 14, … continues. Find the 20th term, the sum of the first 10 terms, and which term equals 101.",
          ["a = 5, d = 3. $T_{20} = 5 + 19 \\times 3 = 62$.", "$S_{10} = \\frac{10}{2}(2 \\times 5 + 9 \\times 3) = 5 \\times 37 = 185$.", "$5 + (n - 1) \\times 3 = 101$ gives $n - 1 = 32$, so n = 33."], "**$T_{20} = 62$; $S_{10} = 185$; 101 is the 33rd term.**"),
         ("Example 2: geometric", "A farmer in Buea sells 3 sacks of cocoa in week 1 and doubles the amount each week. How many sacks in week 6?",
          ["Geometric: a = 3, r = 2.", "$T_6 = 3 \\times 2^5 = 3 \\times 32 = 96$."], "**96 sacks.**")],
        [M("Find the nth term of 7, 11, 15, 19, …", "$4n + 3$", ["$4n + 7$", "$7n + 4$", "$n + 4$"], "d = 4 so $T_n = 4n + (7 - 4) = 4n + 3$. Check n = 1: 7."),
         N("Find the 10th term of 2, 6, 18, 54, … (ratio 3).", 2 * 3 ** 9, "$T_{10} = 2 \\times 3^9 = 2 \\times 19683 = 39366$."),
         TF("The sequence 2, 4, 7, 11, … is arithmetic.", False, "Differences 2, 3, 4 are not constant, so it is not arithmetic (they increase by 1: a quadratic sequence).")],
        [N("The first term of an arithmetic sequence is 4 and the 12th term is 48. Find the common difference.", 4, "$4 + 11d = 48$, so $11d = 44$ and d = 4."),
         N("Find the sum of the first 20 natural numbers 1 + 2 + ... + 20.", 210, "$S_{20} = \\frac{20}{2}(1 + 20) = 210$.")],
        P("A student in Bamenda saves 500 FCFA in the first week and 200 FCFA more each week than the week before (500, 700, 900, ...).",
          [pn("How much does she save in week 10?", 500 + 9 * 200, "$T_{10} = 500 + 9 \\times 200 = 2300$ FCFA.", unit="FCFA", pts=1),
           pn("What is the total saved after 10 weeks?", 10 * (2 * 500 + 9 * 200) // 2, "$S_{10} = 5(1000 + 1800) = 14000$ FCFA.", unit="FCFA", pts=2),
           pn("In which week does she first save 3 000 FCFA or more in one week?", 14, "$500 + 200(n-1) \\geq 3000$ gives $n - 1 \\geq 12.5$, so n = 14.", pts=2)]),
        [("The common difference of 20, 17, 14, ... is:", "−3", ["3", "−17", "17"], "Each term is 3 less."),
         ("The next term of 1, 4, 9, 16, … is:", "25", ["23", "24", "20"], "Squares: $5^2$."),
         ("$T_n = 3n - 2$: the 5th term is:", "13", ["17", "15", "11"], "15 − 2 = 13."),
         ("In 3, 6, 12, 24, ... the ratio is:", "2", ["3", "6", "12"], "6 ÷ 3 = 2."),
         ("Sum of 1, 3, 5, 7, 9:", "25", ["24", "20", "35"], "Odd numbers: 5² = 25.")],
        ill=(plot(0, 7, 0, 22, points=[(n, nth(n), None, "red") for n in range(1, 7)], segments=[], grid=1, xlabel="n", ylabel="Tₙ", curves=[("3*x+2", "blue")]),
             "Terms of 5, 8, 11, … lie on a straight line", "A graph of the term number against the term value showing points on a straight line of gradient 3"), minutes=25)

    # ---------------------------------------------------------------- HCF / LCM
    import math as _m
    assert _m.gcd(84, 180) == 12 and 84 * 180 // 12 == 1260 and 84 == 2 ** 2 * 3 * 7 and 180 == 2 ** 2 * 3 ** 2 * 5
    assert _m.lcm(12, 15) == 60 and _m.gcd(360, 240) == 120
    it = [T(150, 20, "84", 18, bold=True), LINE(140, 28, 100, 58), LINE(160, 28, 200, 58), T(95, 78, "2", 16), T(205, 78, "42", 16, bold=True),
          LINE(195, 86, 160, 114), LINE(215, 86, 250, 114), T(155, 134, "2", 16), T(255, 134, "21", 16, bold=True), LINE(250, 142, 220, 170), LINE(262, 142, 292, 170), T(215, 190, "3", 16), T(297, 190, "7", 16)]
    L = lesson(ch, "hcf-lcm", "Prime factors, HCF and LCM",
        ["Write a number as a product of prime factors.", "Find the HCF and LCM using prime factors.", "Solve problems that use HCF and LCM."],
        [("definition", "Prime factors, HCF, LCM", "A **prime** has exactly two factors (2, 3, 5, 7, 11, …). Every whole number greater than 1 is a unique product of primes. The **HCF** is the largest number dividing both; the **LCM** is the smallest number that both divide into."),
         ("methode", "Using prime factors", "Write each number as a product of primes with indices. **HCF** = product of the common primes, each with the **smallest** index. **LCM** = product of all primes that appear, each with the **largest** index. For two numbers a and b: HCF × LCM = a × b."),
         ("pieges", "Common mistakes", "- Calling 1 a prime number: it is not.\n- Taking the largest index for the HCF (it is the smallest) or the smallest for the LCM.\n- Giving the product a × b when the LCM is asked.")],
        [("Example 1: HCF and LCM", "Find the HCF and LCM of 84 and 180.",
          ["84 = 2² × 3 × 7 and 180 = 2² × 3² × 5.", "HCF = 2² × 3 = 12 (smallest indices of the common primes).", "LCM = 2² × 3² × 5 × 7 = 1 260. Check: 12 × 1 260 = 15 120 = 84 × 180."], "**HCF = 12; LCM = 1 260.**", shapes(it, 340, 215)),
         ("Example 2: a problem", "Two buses leave a park in Bamenda together. One returns every 12 minutes, the other every 15 minutes. When do they next leave together?",
          ["Next time together = LCM of 12 and 15.", "12 = 2² × 3 and 15 = 3 × 5: LCM = 2² × 3 × 5 = 60.", "They leave together again after 60 minutes."], "**After 60 minutes.**")],
        [N("Write 60 as a product of primes and enter the number of prime factors counted with repetition.", 4, "60 = 2 × 2 × 3 × 5: four prime factors."),
         N("Find the HCF of 24 and 36.", 12, "24 = 2³ × 3 and 36 = 2² × 3²: 2² × 3 = 12."),
         M("Find the LCM of 6 and 8.", "24", ["48", "2", "14"], "6 = 2×3, 8 = 2³: LCM = 2³ × 3 = 24.")],
        [N("A floor 360 cm by 240 cm is to be covered with square tiles of the largest possible side, with no cutting. Find the side of a tile.", 120, "HCF of 360 and 240 is 120 cm.", unit="cm"),
         N("The HCF of two numbers is 6 and their LCM is 72. One number is 18. Find the other.", 24, "HCF × LCM = product: 6 × 72 = 432; 432 ÷ 18 = 24.")],
        P("In a market in Kumba, a seller has 48 mangoes and 60 oranges to pack into identical bags with no fruit left over.",
          [pn("What is the largest number of bags?", 12, "HCF of 48 and 60 is 12.", pts=2),
           pn("How many mangoes in each bag?", 4, "48 ÷ 12 = 4.", pts=1),
           pn("How many oranges in each bag?", 5, "60 ÷ 12 = 5.", pts=1)]),
        [("Which is prime?", "13", ["9", "15", "21"], "Only 1 and 13."),
         ("2³ × 3 =", "24", ["18", "36", "12"], "8 × 3."),
         ("HCF of 9 and 14:", "1", ["9", "14", "126"], "No common factor."),
         ("LCM of 4 and 5:", "20", ["1", "9", "10"], "Coprime: product."),
         ("The LCM is always:", "a multiple of both numbers", ["a factor of both", "smaller than both", "prime"], "Definition.")],
        minutes=25)
