"""Generates form4-maths (run: python3 form4-maths.py)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from sm_common import *

REF = "Cameroon secondary (general education) Mathematics programme, Form 4 (MINESEC) — to be checked against the official programme"
p = mkpack(4, "", REF, "Fourth year of secondary mathematics: quadratic equations, variation, sequences, trigonometry of any triangle, circle theorems, vectors, loci, similarity, mensuration, number bases and statistics (cumulative frequency, histograms).")

# ===================================================================== ALGEBRA
ch = p.chapter("algebra", "Quadratics, variation and sequences", REF)

for X in range(-8, 9): assert X**2 - 5*X - 14 == (X - 7) * (X + 2)
r1 = (3 + math.sqrt(41)) / 4; r2 = (3 - math.sqrt(41)) / 4
assert abs(r1 - 2.3508) < 1e-4 and abs(r2 + 0.8508) < 1e-4 and abs(2*r1**2 - 3*r1 - 4) < 1e-9
assert abs((-3 + math.sqrt(7)) + 0.3542) < 1e-3 and abs((-3 - math.sqrt(7)) + 5.6458) < 1e-3 and 7 * 10 == 70 and 8 * 10 == 80 and 8 + 10 == 18
qf = plot(-2, 3.5, -6, 12, curves=[("2*x^2-3*x-4", "blue", "y = 2x² − 3x − 4")], points=[(2.35, 0, "2.35", "red"), (-0.85, 0, "−0.85", "red")], grid=1, xlabel="x", ylabel="y")
lesson(ch, "quadratic-equations", "Solving quadratic equations",
  ["Solve quadratic equations by factorising", "Solve by completing the square", "Solve using the quadratic formula", "Use the discriminant to decide the number of roots", "Form and solve quadratic equations from problems"],
  [("methode", "Factorising", "Write the equation as $ax^2 + bx + c = 0$ (zero on one side), factorise, and use the fact that **if a product is zero, one of the factors is zero**. $x^2 - 5x - 14 = 0$ gives $(x - 7)(x + 2) = 0$, so x = 7 or x = −2. Always give **both** roots and check them."),
   ("formule", "The quadratic formula", "When factorising is hard, use the formula for $ax^2 + bx + c = 0$:", "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"),
   ("propriete", "The discriminant", "The number $b^2 - 4ac$ is the **discriminant**. If it is positive there are **two different** real roots; if it is **zero** there is one repeated root; if it is **negative** there are **no real roots** (the curve does not touch the x-axis). For $2x^2 - 3x - 4 = 0$: 9 + 32 = 41, so x = (3 ± √41) ÷ 4 = 2.35 or −0.85 (2 d.p.)."),
   ("methode", "Completing the square", "To solve $x^2 + 6x + 2 = 0$: move the constant: $x^2 + 6x = -2$. Add (half of 6)² = 9 to both sides: $(x + 3)^2 = 7$. Take square roots: $x + 3 = \\pm\\sqrt{7}$, so $x = -3 \\pm 2.646$, that is x = −0.35 or −5.65 (2 d.p.)."),
   ("pieges", "Common mistakes", "- Solving x² = 5x by dividing by x: this loses the root x = 0; write x(x − 5) = 0.\n- Forgetting that −b and the whole numerator are divided by 2a.\n- Using the formula with the equation not equal to zero.")],
  [("Example 1: factorising and formula", "Solve (a) x² − 5x − 14 = 0 and (b) 2x² − 3x − 4 = 0 (2 d.p.).",
    ["(a) (x − 7)(x + 2) = 0, so x = 7 or x = −2.", "(b) a = 2, b = −3, c = −4: discriminant = 9 + 32 = 41.", "(b) x = (3 ± 6.403) ÷ 4, so x = 2.35 or x = −0.85."], "**(a) 7 or −2; (b) 2.35 or −0.85**", qf),
   ("Example 2: a fenced plot", "A rectangular plot at Bamenda has perimeter 36 m and area 80 m². Find its dimensions.",
    ["Half the perimeter: length + width = 18, so length = 18 − w.", "Area: w(18 − w) = 80, so w² − 18w + 80 = 0.", "Factorise: (w − 8)(w − 10) = 0, so w = 8 or w = 10. The plot is 8 m by 10 m."], "**8 m by 10 m**")],
  [N("Solve x² − 9 = 0 and give the positive root.", 3, "x² = 9, so x = 3 or −3."),
   M("The roots of (x − 2)(x + 5) = 0 are:", "2 and −5", ["−2 and 5", "2 and 5", "−2 and −5"], "Each factor equals zero."),
   N("Find the discriminant of x² + 4x + 1.", 12, "16 − 4 = 12.")],
  [N("Solve x² + 2x − 15 = 0 and give the larger root.", 3, "(x + 5)(x − 3) = 0, so x = −5 or 3."),
   O("Explain what a negative discriminant tells you about the graph of y = ax² + bx + c.", "A negative discriminant means no real roots, so the parabola does not cross or touch the x-axis; it lies entirely above or below it.", "1 mark for no real roots, 1 mark for the graph not touching the x-axis, 1 mark for 'above or below'.", points=3)],
  P("A rectangular garden at Kumba is 3 m longer than it is wide, and its area is 70 m².",
    [pm("Which equation does the width w satisfy?", "w² + 3w − 70 = 0", ["w² − 3w − 70 = 0", "w² + 3w + 70 = 0", "2w + 3 = 70"], "w(w + 3) = 70.", 1),
     pn("Find the width (m).", 7, "(w + 10)(w − 7) = 0, so w = 7 (a length cannot be negative).", unit="m", pts=2),
     pn("Find the length (m).", 10, "7 + 3 = 10.", unit="m", pts=1),
     pn("Fencing costs 1 200 FCFA per metre. Find the cost to fence the garden (FCFA).", 40800, "Perimeter 2(7 + 10) = 34 m; 34 × 1 200 = 40 800.", unit="FCFA", pts=2)]),
  [("A quadratic equation has the form:", "ax² + bx + c = 0", ["ax + b = 0", "a/x = 0", "x³ = 0"], "Degree 2."),
   ("If (x − 3)(x − 4) = 0 then:", "x = 3 or x = 4", ["x = 12", "x = 7", "x = 1"], "Zero product rule."),
   ("A discriminant of 0 means:", "one repeated root", ["two roots", "no root", "three roots"], "b² = 4ac."),
   ("To complete the square on x² + 8x we add:", "16", ["8", "4", "64"], "(8/2)²."),
   ("x² = 5x has the roots:", "0 and 5", ["5 only", "0 only", "−5 and 5"], "x(x − 5) = 0.")],
  ill=(qf, "The graph of y = 2x² − 3x − 4 meets the x-axis at about 2.35 and −0.85.", "A U-shaped curve crossing the x-axis at −0.85 and 2.35."),
  notes=["Simultaneous equations with a quadratic and quadratic inequalities are not covered."])

assert 12 / 3 == 4 and 4 * 7 == 28 and 6 * 4 == 24 and 24 / 3 == 8 and 50 / 25 == 2 and 2 * 8**2 == 128 and 3 * 4 / 2 * 1 == 6 and 4 * 5 / 10 == 2
assert (11500 - 7000) / (25 - 10) == 300 and 7000 - 300 * 10 == 4000 and 4000 + 300 * 40 == 16000 and 8 * 12 == 96 and 96 / 6 == 16
vg = plot(1, 6, 0, 28, curves=[("4*x", "blue", "y = 4x"), ("24/x", "red", "y = 24/x")], grid=1, xlabel="x", ylabel="y")
lesson(ch, "variation", "Direct, inverse, joint and partial variation",
  ["Write and solve direct variation y = kx and y = kx²", "Write and solve inverse variation y = k/x", "Deal with joint variation", "Solve partial variation problems"],
  [("definition", "Direct variation", "y **varies directly as** x (y ∝ x) when y = kx: doubling x doubles y. k is the **constant of variation**. If y = 12 when x = 3 then k = 4, so y = 4x and when x = 7, y = 28. The graph is a straight line through the origin. Variants: y ∝ x² means y = kx²; y ∝ √x means y = k√x."),
   ("definition", "Inverse variation", "y **varies inversely as** x (y ∝ 1/x) when y = k/x, or xy = k. If x doubles, y halves. If y = 6 when x = 4 then k = 24, so y = 24/x and when x = 3, y = 8. The graph is a curve called a **hyperbola** that never touches the axes."),
   ("methode", "Joint and partial variation", "**Joint**: y varies as x and inversely as z gives y = kx/z. **Partial**: y is the sum of a fixed part and a varying part: y = a + bx. Use two data pairs to find a and b. Method for every problem: 1. write the equation with k; 2. find k from the given pair; 3. rewrite with k; 4. substitute to answer."),
   ("pieges", "Common mistakes", "- Writing y = x + k instead of y = kx for direct variation.\n- Confusing inverse variation with a negative gradient: xy stays constant.\n- Using only one pair of values to solve a partial variation: two pairs are needed.")],
  [("Example 1: direct and inverse", "(a) y ∝ x and y = 12 when x = 3. Find y when x = 7. (b) y ∝ 1/x and y = 6 when x = 4. Find y when x = 3.",
    ["(a) y = kx; 12 = 3k, so k = 4 and y = 4 × 7 = 28.", "(b) y = k/x; 6 = k/4, so k = 24 and y = 24 ÷ 3 = 8."], "**(a) 28; (b) 8**", vg),
   ("Example 2: workers and partial variation", "8 workers finish a job in 12 days. Days vary inversely with the workers: how long for 6 workers? A cooking gas supplier charges C = a + bn FCFA for n units; the bill is 7 000 for 10 units and 11 500 for 25 units. Find a and b.",
    ["Days × workers = 8 × 12 = 96, so 6 workers need 96 ÷ 6 = 16 days.", "a + 10b = 7 000 and a + 25b = 11 500. Subtract: 15b = 4 500, so b = 300.", "a = 7 000 − 3 000 = 4 000. So C = 4 000 + 300n; for 40 units: 16 000 FCFA."], "**16 days; a = 4 000 FCFA, b = 300 FCFA per unit.**")],
  [N("y varies directly as x and y = 10 when x = 2. Find y when x = 9.", 45, "k = 5, so y = 5 × 9.", ),
   N("y varies inversely as x and y = 5 when x = 6. Find k.", 30, "k = xy = 30.", ),
   M("Which situation shows inverse variation?", "More workers, fewer days for the same job", ["More rice, higher cost", "Longer time, more distance at constant speed", "More pages, more paper"], "Product workers × days is constant.")],
  [N("y ∝ x² and y = 50 when x = 5. Find y when x = 8.", 128, "k = 2; y = 2 × 64 = 128."),
   O("y varies jointly as x and inversely as z, with y = 6 when x = 3 and z = 2. Find y when x = 5 and z = 10, showing k.", "y = kx/z, so 6 = 3k/2 and k = 4. Then y = 4 × 5 ÷ 10 = 2.", "1 mark for the equation, 1 mark for k = 4, 1 mark for y = 2.", points=3)],
  P("The time T hours to travel between two towns varies inversely as the average speed v km/h. At 60 km/h the journey takes 3 hours.",
    [pn("Find the constant k in T = k/v (the distance in km).", 180, "k = vT = 60 × 3.", pts=1),
     pn("How long at 90 km/h (hours)?", 2, "180 ÷ 90.", unit="h", pts=2),
     pn("What speed is needed to complete the journey in 2.5 hours (km/h)?", 72, "180 ÷ 2.5 = 72.", unit="km/h", pts=2)]),
  [("y ∝ x means:", "y = kx", ["y = x + k", "y = k/x", "y = k − x"], "Direct variation."),
   ("In inverse variation the product xy is:", "constant", ["zero", "increasing", "x + y"], "xy = k."),
   ("y = kx has a graph that is:", "a line through the origin", ["a hyperbola", "a circle", "a horizontal line"], "Direct variation."),
   ("A fixed charge plus a charge per unit is:", "partial variation", ["direct variation", "inverse variation", "joint variation"], "y = a + bx."),
   ("If y ∝ x² and x is doubled, y becomes:", "four times larger", ["twice", "half", "the same"], "2² = 4.")],
  ill=(vg, "Graphs of y = 4x (direct) and y = 24/x (inverse) for x from 1 to 6.", "A rising straight line and a falling curve crossing at about x = 2.45."),
  notes=["The gas tariff is invented for practice; it is not a real price list."])

ap = [5 + 3 * i for i in range(10)]; assert ap[-1] == 32 and sum(ap) == 185 and 10 / 2 * (2 * 5 + 9 * 3) == 185
gp = [3 * 2 ** i for i in range(8)]; assert gp[-1] == 384 and sum(gp[:6]) == 189 and 3 * (2**6 - 1) // (2 - 1) == 189
assert 5000 + 11 * 1500 == 21500 and 12 / 2 * (5000 + 21500) == 159000 and abs(600000 * 0.8**3 - 307200) < 1e-6
sq = plot(0, 7, 0, 24, points=[(i, 3 * i + 2, str(3 * i + 2), "red") for i in range(1, 7)], segments=[(i, 3 * i + 2, i + 1, 3 * i + 5, "grey", True) for i in range(1, 6)], grid=1, xlabel="n", ylabel="term")
lesson(ch, "sequences", "Arithmetic and geometric sequences",
  ["Find the nth term of an arithmetic sequence", "Find the sum of an arithmetic series", "Find the nth term and sum of a geometric sequence", "Solve practical problems on savings and depreciation"],
  [("formule", "Arithmetic sequences", "In an **arithmetic** sequence (AP) the **common difference** d is added each time. With first term a:", "T_n = a + (n - 1)d \\qquad S_n = \\frac{n}{2}\\left[2a + (n - 1)d\\right]"),
   ("methode", "AP example", "5, 8, 11, 14, … has a = 5 and d = 3, so T_n = 5 + 3(n − 1) = 3n + 2. The 10th term is 32, and the sum of the first 10 terms is 10 ÷ 2 × (10 + 27) = 185. You can also use S = n ÷ 2 × (first term + last term) = 5 × (5 + 32) = 185."),
   ("formule", "Geometric sequences", "In a **geometric** sequence (GP) each term is multiplied by the **common ratio** r:", "T_n = a r^{\\,n-1} \\qquad S_n = \\frac{a (r^n - 1)}{r - 1}"),
   ("methode", "GP example", "3, 6, 12, 24, … has a = 3 and r = 2. The 8th term is 3 × 2⁷ = 384. The sum of the first 6 terms is 3 × (2⁶ − 1) ÷ (2 − 1) = 189. A ratio **less than 1** means the terms decrease, e.g. depreciation of a machine by 20% a year has r = 0.8."),
   ("pieges", "Common mistakes", "- Using n instead of (n − 1) in the formula for the nth term.\n- Confusing a sequence (a list) with a series (a sum).\n- Treating a sequence as arithmetic when the ratio, not the difference, is constant.")],
  [("Example 1: arithmetic", "A trader saves 5 000 FCFA in the first month and 1 500 FCFA more each month than the month before. How much does she save in month 12 and in total over 12 months?",
    ["a = 5 000 and d = 1 500. T₁₂ = 5 000 + 11 × 1 500 = 21 500.", "S₁₂ = 12 ÷ 2 × (5 000 + 21 500) = 6 × 26 500.", "Total = 159 000 FCFA."], "**Month 12: 21 500 FCFA; total 159 000 FCFA.**", sq),
   ("Example 2: geometric", "Find the 8th term and the sum of the first 6 terms of 3, 6, 12, …. A machine worth 600 000 FCFA loses 20% of its value each year: find its value after 3 years.",
    ["T₈ = 3 × 2⁷ = 384 and S₆ = 3(64 − 1) ÷ 1 = 189.", "Each year the value is multiplied by 0.8: 600 000 × 0.8³ = 600 000 × 0.512.", "Value = 307 200 FCFA."], "**384; 189; 307 200 FCFA.**")],
  [N("Find the 10th term of 5, 8, 11, ....", 32, "5 + 9 × 3 = 32."),
   N("Find the common ratio of 2, 6, 18, ....", 3, "6 ÷ 2 = 3."),
   M("The nth term of 7, 10, 13, … is:", "3n + 4", ["3n + 7", "n + 3", "7n + 3"], "a = 7, d = 3: 7 + 3(n − 1) = 3n + 4.")],
  [N("Find the sum of the first 20 natural numbers 1 + 2 + 3 + ... + 20.", 210, "20 ÷ 2 × (1 + 20) = 210.", ),
   O("The 3rd term of an AP is 11 and the 7th term is 23. Find a and d.", "T₇ − T₃ = 4d = 12, so d = 3. Then a + 2d = 11 gives a = 5.", "1 mark for the subtraction, 1 mark for d = 3, 1 mark for a = 5.", points=3)],
  P("A taxi-moto driver in Douala earns 6 000 FCFA on day 1 and his daily earnings rise by 500 FCFA each day for 14 days.",
    [pn("Find the earnings on day 14 (FCFA).", 12500, "6 000 + 13 × 500 = 12 500.", unit="FCFA", pts=1),
     pn("Find the total earnings over the 14 days (FCFA).", 129500, "14 ÷ 2 × (6 000 + 12 500) = 7 × 18 500.", unit="FCFA", pts=2),
     pn("On which day are the earnings first at least 10 000 FCFA?", 9, "6 000 + (n − 1) × 500 ≥ 10 000 gives n ≥ 9.", pts=2)]),
  [("The common difference of 4, 9, 14, … is:", "5", ["4", "13", "9"], "9 − 4."),
   ("The common ratio of 5, 10, 20, … is:", "2", ["5", "15", "10"], "10 ÷ 5."),
   ("The nth term of an AP is:", "a + (n − 1)d", ["a + nd", "ar^n", "a − d"], "Standard formula."),
   ("The sum of a GP is found with:", "a(rⁿ − 1)/(r − 1)", ["n/2(a + d)", "a + (n − 1)d", "ar"], "GP sum."),
   ("1, 2, 4, 8, … is:", "a geometric sequence", ["an arithmetic sequence", "neither", "a constant sequence"], "Ratio 2.")],
  ill=(sq, "The terms 5, 8, 11, 14, 17, 20 of the sequence 3n + 2 plotted against n.", "Six points rising by 3 each time, joined by dashed lines."),
  notes=["The sum to infinity of a GP and sigma notation are not included; check whether the Form 4 scheme requires them."])

# ===================================================================== GEOMETRY
ch = p.chapter("geometry", "Trigonometry, circles, vectors, loci and similarity", REF)

b_ = 8 * sind(65) / sind(40); c_ = 8 * sind(75) / sind(40)
assert abs(b_ - 11.28) < 0.01 and abs(c_ - 12.02) < 0.01 and 180 - 40 - 65 == 75
a60 = math.sqrt(49 + 81 - 2 * 7 * 9 * cosd(60)); assert abs(a60 ** 2 - 67) < 1e-9 and abs(a60 - 8.185) < 0.001
C_ang = math.degrees(math.acos((25 + 49 - 81) / (2 * 5 * 7))); assert abs(C_ang - 95.74) < 0.01
area_t = 0.5 * 50 * 60 * sind(70); BC = math.sqrt(2500 + 3600 - 2 * 50 * 60 * cosd(70)); assert abs(10 * sind(45) / sind(30) - 14.142) < 1e-3 and abs(math.sqrt(100 - 96 * cosd(50)) - 6.188) < 1e-3 and abs(area_t - 1409.54) < 0.01 and abs(BC - 63.62) < 0.01
tri3 = shapes([POLY([60, 190, 330, 190, 240, 50], fill="lightyellow"), T(48, 205, "A", 15), T(342, 205, "B", 15), T(240, 40, "C", 15), T(195, 208, "c", 14), T(100 + 150, 112, "a", 14, anchor="start"), T(130, 112, "b", 14, anchor="end")], 400, 225)
lesson(ch, "sine-cosine-rules", "Sine rule, cosine rule and area of a triangle",
  ["Use the sine rule to find sides and angles", "Use the cosine rule to find a side or an angle", "Use ½ ab sin C for the area of a triangle", "Solve problems on non-right-angled triangles"],
  [("formule", "Sine rule", "In triangle ABC with sides a, b, c opposite the angles A, B, C, use the sine rule when you know **two angles and a side**, or **two sides and an angle opposite one of them**:", "\\frac{a}{\\sin A} = \\frac{b}{\\sin B} = \\frac{c}{\\sin C}"),
   ("formule", "Cosine rule", "Use the cosine rule when you know **two sides and the angle between them**, or **all three sides**:", "a^2 = b^2 + c^2 - 2bc \\cos A \\qquad \\cos A = \\frac{b^2 + c^2 - a^2}{2bc}"),
   ("formule", "Area", "The area of a triangle from two sides and the **included** angle:", "\\text{Area} = \\frac{1}{2} ab \\sin C"),
   ("pieges", "Common mistakes", "- Using the area formula with an angle that is not between the two sides.\n- Forgetting that for an obtuse angle cos is negative: cos 120° = −0.5, and sin 120° = sin 60°.\n- Not checking that the calculator is in degree mode; also the longest side is opposite the largest angle.")],
  [("Example 1: sine rule", "In triangle ABC, a = 8 cm, A = 40° and B = 65°. Find b and c (2 d.p.).",
    ["C = 180° − 40° − 65° = 75°.", "b = a sin B ÷ sin A = 8 sin 65° ÷ sin 40° = 11.28 cm.", "c = 8 sin 75° ÷ sin 40° = 12.02 cm."], "**b ≈ 11.28 cm; c ≈ 12.02 cm.**", tri3),
   ("Example 2: cosine rule and area", "A plot ABC has AB = 50 m, AC = 60 m and angle A = 70°. Find BC and the area.",
    ["BC² = 50² + 60² − 2 × 50 × 60 × cos 70° = 6 100 − 2 052.1 = 4 047.9, so BC ≈ 63.6 m.", "Area = ½ × 50 × 60 × sin 70° = 1 500 × 0.9397.", "Area ≈ 1 409.5 m²."], "**BC ≈ 63.6 m; area ≈ 1 409.5 m².**")],
  [N("In triangle ABC, A = 30°, B = 45° and a = 10 cm. Find b (2 d.p.).", 14.14, "b = 10 sin 45° ÷ sin 30° = 14.14 cm.", tol=0.01, unit="cm"),
   N("Find sin 150° (it equals sin 30°).", 0.5, "Supplementary angles have equal sines.", tol=0.001),
   M("The cosine rule is used when we know:", "two sides and the included angle", ["two angles and one side only", "one side only", "three angles"], "SAS or SSS.")],
  [N("Triangle with b = 7 cm, c = 9 cm and A = 60°. Find a (to 2 d.p.).", 8.19, "a² = 49 + 81 − 63 = 67, so a = 8.185.", tol=0.01, unit="cm"),
   N("A triangle has sides 5, 7 and 9 cm. Find the largest angle (1 d.p.).", 95.7, "cos C = (25 + 49 − 81) ÷ 70 = −0.1, so C = 95.7°.", tol=0.1, unit="°")],
  P("Two villages A and B are both visible from a hill H. AH = 6 km, BH = 8 km and angle AHB = 50°.",
    [pn("Find the distance AB (km, 2 d.p.).", 6.19, "AB² = 36 + 64 − 96 cos 50° = 100 − 61.71 = 38.29, so AB = 6.19 km.", tol=0.01, unit="km", pts=3),
     pn("Find the area of triangle AHB (km², 1 d.p.).", 18.4, "½ × 6 × 8 × sin 50° = 24 × 0.766 = 18.4.", tol=0.1, unit="km²", pts=2)]),
  [("The sine rule states:", "a/sin A = b/sin B", ["a sin A = b sin B", "a/cos A = b/cos B", "sin a = sin b"], "Ratio of side to sine of opposite angle."),
   ("The area of a triangle is:", "½ ab sin C", ["ab sin C", "½ ab cos C", "½ a + b"], "Included angle."),
   ("cos 120° is:", "−0.5", ["0.5", "−0.866", "0.866"], "Obtuse angles have negative cosine."),
   ("The longest side is opposite:", "the largest angle", ["the smallest angle", "any angle", "the right angle only"], "Property."),
   ("The cosine rule is a² =", "b² + c² − 2bc cos A", ["b² + c²", "b² + c² + 2bc cos A", "bc cos A"], "Includes the minus sign.")],
  ill=(tri3, "Triangle ABC with sides a, b, c opposite the angles A, B, C.", "A triangle with vertices A, B and C and the sides named a, b and c opposite them."),
  notes=["The ambiguous case of the sine rule (SSA) is not developed."])

assert 90 - 38 == 52 and 5 * 38 - 10 == 180 and 2 * 38 + 10 == 86 and 3 * 38 - 20 == 94 and 6**2 + 8**2 == 10**2 and 180 - 95 == 85 and 130 / 2 == 65
cx, cy, r = 200, 125, 90
def cp(deg): return (round(cx + r * cosd(deg), 1), round(cy - r * sind(deg), 1))
Ap, Bp, Cp = cp(205), cp(335), cp(90)
ctf = shapes([CIRCLE(cx, cy, r), LINE(*Ap, cx, cy), LINE(cx, cy, *Bp), LINE(*Ap, *Cp), LINE(*Cp, *Bp), T(cx, cy + 32, "130°", 13, color="red", bold=True), T(cx, cy - r + 34, "65°", 13, color="red", bold=True),
              T(Ap[0] - 12, Ap[1] + 14, "A", 15), T(Bp[0] + 12, Bp[1] + 14, "B", 15), T(Cp[0], Cp[1] - 8, "C", 15), T(cx + 10, cy - 6, "O", 14, anchor="start")], 400, 250)
lesson(ch, "circle-theorems", "Circle theorems",
  ["State the angle theorems of a circle", "State the tangent theorems", "Find unknown angles and lengths using them", "Give reasons for each step"],
  [("propriete", "Angle theorems", "- The angle at the **centre** is **twice** the angle at the circumference on the same arc.\n- The angle in a **semicircle** is 90°.\n- Angles in the **same segment** are equal.\n- **Opposite angles** of a cyclic quadrilateral add up to 180°.\n- The perpendicular from the centre to a chord bisects the chord."),
   ("propriete", "Tangent theorems", "- A tangent is **perpendicular to the radius** at the point of contact.\n- Two tangents from an outside point are **equal in length**.\n- **Alternate segment theorem**: the angle between a tangent and a chord equals the angle in the alternate segment."),
   ("methode", "How to answer", "Mark known angles on the diagram. Look for radii (they make isosceles triangles with equal base angles), diameters (90° angles) and tangents (90° with the radius). **Write the reason** next to each angle you find, using the exact names of the theorems. Check that the angles in each triangle add up to 180°."),
   ("pieges", "Common mistakes", "- Using the 'twice' rule when the angles stand on different arcs.\n- In a cyclic quadrilateral: **opposite** angles (not adjacent) add up to 180°.\n- Forgetting that a tangent makes 90° with the **radius**, not with the chord.")],
  [("Example 1: centre and circumference", "A, B and C lie on a circle with centre O. Angle AOB = 130° and C is on the major arc. Find angle ACB. A cyclic quadrilateral has angle A = 95°: find the opposite angle C.",
    ["Angle at the circumference = half the angle at the centre: 130° ÷ 2 = 65°.", "Opposite angles of a cyclic quadrilateral add up to 180°: C = 180° − 95° = 85°."], "**65°; 85°**", ctf),
   ("Example 2: tangent", "PA is a tangent to a circle with centre O at A. The radius is 6 cm and OP = 10 cm. Find PA. Also, AB is a diameter and angle ABC = 38°: find angle ACB and angle BAC.",
    ["Tangent ⊥ radius: triangle OAP has a right angle at A, so PA² = 10² − 6² = 64 and PA = 8 cm.", "The angle in a semicircle is 90°: angle ACB = 90°.", "Then angle BAC = 180° − 90° − 38° = 52°."], "**PA = 8 cm; ACB = 90°; BAC = 52°.**")],
  [N("The angle at the centre is 100°. Find the angle at the circumference on the same arc.", 50, "Half of 100°.", unit="°"),
   N("A cyclic quadrilateral has angles A = 110° and B = 80°. Find C.", 70, "C = 180 − 110 = 70° (opposite A).", unit="°"),
   M("The angle in a semicircle is:", "90°", ["180°", "45°", "60°"], "The diameter subtends a right angle.")],
  [N("A tangent and a chord meet at 52°. Find the angle in the alternate segment.", 52, "Alternate segment theorem: equal.", unit="°"),
   O("In a cyclic quadrilateral ABCD, angle ABC = 2x + 10 and angle ADC = 3x − 20. Find x and the angle ABC.", "Opposite angles add up to 180°: 5x − 10 = 180 so x = 38. Angle ABC = 2 × 38 + 10 = 86°.", "1 mark for the equation, 1 mark for x = 38, 1 mark for 86°.", points=3)],
  P("A circular fish pond at Limbe has centre O. A post P stands outside the pond, and PT is a tangent from P touching the pond at T. The pond has radius 5 m and OP = 13 m.",
    [pn("Find the length PT (m).", 12, "PT² = 13² − 5² = 144.", unit="m", pts=2),
     pn("A second tangent PS touches the pond at S. State its length (m).", 12, "Tangents from an outside point are equal.", unit="m", pts=1),
     pn("Find the area of the quadrilateral OTPS (m²).", 60, "Two right triangles each ½ × 5 × 12 = 30.", unit="m²", pts=2)]),
  [("The angle at the centre is ___ the angle at the circumference:", "twice", ["half", "equal to", "three times"], "Same arc."),
   ("In circle theorems, a tangent meets the radius at:", "90°", ["45°", "60°", "180°"], "Perpendicular."),
   ("Opposite angles of a cyclic quadrilateral add up to:", "180°", ["90°", "360°", "270°"], "Theorem."),
   ("By a circle theorem, angles in the same segment are:", "equal", ["supplementary", "complementary", "double"], "Theorem."),
   ("Two tangents from an outside point are:", "equal in length", ["parallel", "perpendicular", "different"], "Theorem.")],
  ill=(ctf, "A circle with centre O: the angle at the centre is 130° and the angle at C on the circumference is 65°.", "A circle with a triangle ABC inscribed and the centre joined to A and B."),
  notes=["The figure shows C at the top of the circle on the major arc; angle labels are positioned schematically."])

assert math.hypot(4, 3) == 5 and (1 + 5) / 2 == 3 and (2 + 5) / 2 == 3.5
p2 = (2 * 2 - 1, 2 * 1 + 3); assert p2 == (3, 5) and abs(math.hypot(3, 5) - 5.831) < 1e-3 and (2 - -1, 1 - 3) == (3, -2) and (6, 3) == (3 * 2, 3 * 1)
assert (3 + 6, 4 + 2) == (9, 6) and abs(math.hypot(9, 6) - 10.817) < 1e-3
axv = Axes(0, 7, 0, 7, w=380, h=300, xticks=range(0, 8), yticks=range(0, 8))
axv.line(1, 2, 5, 5, color="blue", width=3, arrow="end"); axv.pt(1, 2, "A", "red", dx=-12, dy=14); axv.pt(5, 5, "B", "red", dx=12, dy=-6); axv.text(3.4, 3.2, "AB = (4, 3)", 12, anchor="start", color="blue")
vf = axv.fig()
lesson(ch, "vectors", "Vectors in two dimensions",
  ["Write a vector in component form", "Add, subtract and multiply a vector by a number", "Find the magnitude of a vector", "Use vectors for position, midpoints and parallel lines"],
  [("definition", "Vectors", "A **vector** has both **size** (magnitude) and **direction**. In component form we write it as a column; here we write it as (x, y): x units to the right (negative: left) and y units up (negative: down). The vector from A to B is written $\\vec{AB}$. The vector from A(1, 2) to B(5, 5) is $\\vec{AB}$ = (5 − 1, 5 − 2) = (4, 3)."),
   ("methode", "Operations", "To add vectors add the components: (3, 4) + (6, 2) = (9, 6). To multiply by a number k, multiply each component: 3(2, 1) = (6, 3). The **negative** −a points the opposite way. **Parallel** vectors are multiples of each other. The **magnitude** of (x, y) is $\\sqrt{x^2 + y^2}$, so |(4, 3)| = 5."),
   ("propriete", "Position vectors and midpoints", "The **position vector** of a point is the vector from the origin O. $\\vec{AB} = \\vec{OB} - \\vec{OA}$. The midpoint M of AB has position vector $\\frac{1}{2}(\\vec{OA} + \\vec{OB})$: for A(1, 2) and B(5, 5), M = (3, 3.5). A point P with AP : PB = 1 : 3 is $\\vec{OA} + \\frac{1}{4}\\vec{AB}$."),
   ("pieges", "Common mistakes", "- Subtracting in the wrong order: $\\vec{AB}$ = B − A, not A − B.\n- Adding magnitudes instead of components: |a + b| is usually less than |a| + |b|.\n- Confusing the coordinates of a point with a vector between two points.")],
  [("Example 1: AB, magnitude, midpoint", "Find $\\vec{AB}$, its magnitude and the midpoint M for A(1, 2) and B(5, 5).",
    ["$\\vec{AB}$ = (5 − 1, 5 − 2) = (4, 3).", "|AB| = √(4² + 3²) = √25 = 5 units.", "M = ((1 + 5) ÷ 2, (2 + 5) ÷ 2) = (3, 3.5)."], "**AB = (4, 3); |AB| = 5; M = (3, 3.5).**", vf),
   ("Example 2: combining vectors", "p = (2, 1) and q = (−1, 3). Find 2p + q, its magnitude and p − q. Show that (6, 3) is parallel to p.",
    ["2p + q = (4, 2) + (−1, 3) = (3, 5); |2p + q| = √(9 + 25) = √34 ≈ 5.83.", "p − q = (2 − (−1), 1 − 3) = (3, −2).", "(6, 3) = 3 × (2, 1) = 3p, so it is parallel to p."], "**(3, 5), 5.83; (3, −2); parallel since (6, 3) = 3p.**")],
  [N("Find the magnitude of the vector (6, 8).", 10, "√(36 + 64) = 10."),
   N("Given a = (3, −1) and b = (2, 4), find the x-component of a + b.", 5, "3 + 2 = 5."),
   M("The vector from P(2, 3) to Q(7, 1) is:", "(5, −2)", ["(−5, 2)", "(9, 4)", "(5, 2)"], "Q − P.")],
  [N("Find the magnitude of 2p + q for p = (2, 1) and q = (−1, 3) (2 d.p.).", 5.83, "(3, 5) has magnitude √34 = 5.83.", tol=0.01),
   O("Show that the points A(1, 1), B(3, 2) and C(7, 4) lie on a straight line.", "AB = (2, 1) and BC = (4, 2) = 2AB. The vectors are parallel and share the point B, so A, B and C are collinear.", "1 mark for AB, 1 mark for BC, 1 mark for BC = 2AB and the conclusion.", points=3)],
  P("A hiker from Buea walks (3, 4) km (3 km east and 4 km north) and then (6, 2) km.",
    [pn("Find the total displacement: x-component (km).", 9, "3 + 6 = 9.", unit="km", pts=1),
     pn("Find the total displacement: y-component (km).", 6, "4 + 2 = 6.", unit="km", pts=1),
     pn("Find the straight-line distance from the start (km, 2 d.p.).", 10.82, "√(81 + 36) = √117 = 10.82.", tol=0.01, unit="km", pts=3)]),
  [("A vector has:", "magnitude and direction", ["magnitude only", "direction only", "no components"], "Definition."),
   ("The magnitude of the vector (3, 4) is:", "5", ["7", "12", "25"], "√(9 + 16)."),
   ("The vector AB equals:", "OB − OA", ["OA − OB", "OA + OB", "OA × OB"], "Position vectors."),
   ("2 × (1, −3) =", "(2, −6)", ["(3, −1)", "(2, −3)", "(1, −6)"], "Multiply each component."),
   ("Two parallel vectors are:", "multiples of each other", ["equal in length only", "perpendicular", "always equal"], "Scalar multiples.")],
  ill=(vf, "The vector from A(1, 2) to B(5, 5) drawn on a grid.", "An arrow from A to B on a coordinate grid; its components are 4 and 3."),
  notes=["Vectors are written (x, y) in this content because the format has no column-vector notation. Vector geometry proofs are not included."])

sA, sB = (150, 130), (250, 130)
off = math.sqrt(80 ** 2 - 50 ** 2); assert abs(off - 62.45) < 0.01 and abs(math.sqrt(64 - 25) - 6.245) < 1e-3 and abs(2 * math.sqrt(39) - 12.49) < 0.01 and abs(3.14 * 36 - 113.04) < 1e-9
lf = shapes([CIRCLE(150, 130, 80, stroke="blue"), LINE(200, 35, 200, 225, color="red", width=2), LINE(150, 130, 250, 130, dash=True, color="grey"), CIRCLE(150, 130, 3, fill="ink"), CIRCLE(250, 130, 3, fill="ink"),
             CIRCLE(200, round(130 - off, 1), 4, fill="green", stroke="green"), CIRCLE(200, round(130 + off, 1), 4, fill="green", stroke="green"),
             T(138, 150, "S", 14), T(262, 150, "M", 14), T(215, round(130 - off, 1) - 4, "P", 14, anchor="start"), T(215, round(130 + off, 1) + 14, "P′", 14, anchor="start")], 400, 260)
lesson(ch, "loci", "Loci",
  ["Define a locus as the set of points satisfying a condition", "Describe and draw the four standard loci", "Combine two loci to find a region", "Solve practical locus problems"],
  [("definition", "What is a locus?", "A **locus** (plural: loci) is the set of **all points** that satisfy a given condition. For example, the locus of points exactly 3 m from a post is a circle of radius 3 m. The locus of points at most 3 m from the post is the circle **and its inside**."),
   ("propriete", "The four standard loci", "- Points at a fixed distance from a **point**: a circle.\n- Points equidistant from **two points** A and B: the **perpendicular bisector** of AB.\n- Points equidistant from **two intersecting lines**: the **angle bisectors**.\n- Points at a fixed distance from a **line**: two parallel lines (with semicircles at the ends of a segment)."),
   ("methode", "Combining loci", "Draw each locus **with the construction marks** (ruler and compasses). Use a **solid** line for 'on the boundary' and a **dashed** line for a boundary that is not included. Then shade the region that satisfies **all** the conditions. 'Nearer to S than to M' means the side of the bisector containing S."),
   ("pieges", "Common mistakes", "- Drawing the whole circle when the condition is 'at most': shade the inside as well.\n- Forgetting that equidistant from two points means a **line**, not a point.\n- Using the wrong bisector side for 'nearer to'.")],
  [("Example 1: a goat", "A goat at Wum is tied by a 6 m rope to a post. Describe the area it can reach and find it (π = 3.14).",
    ["The goat can reach all points at most 6 m from the post: a circle (with its inside) of radius 6 m.", "Area = 3.14 × 6² = 113.04 m².", "The boundary (where the rope is tight) is the locus of points exactly 6 m away."], "**A circle of radius 6 m; about 113 m².**"),
   ("Example 2: a borehole", "A school S and a market M are 10 km apart. A borehole B must be equidistant from S and M and no more than 8 km from S. Describe the possible places and find the length of the bisector in range.",
    ["Equidistant from S and M: the perpendicular bisector of SM, 5 km from S.", "At most 8 km from S: inside a circle of radius 8 km. The circle meets the bisector at distance √(8² − 5²) = √39 ≈ 6.24 km from the midpoint, on each side.", "The possible places form a segment of the bisector of length 2 × 6.24 ≈ 12.5 km."], "**A segment of the perpendicular bisector about 12.5 km long.**", lf)],
  [M("The locus of points 4 cm from a fixed point is:", "a circle", ["a straight line", "a square", "a point"], "Fixed distance from a point."),
   M("The locus of points equidistant from two points A and B is:", "the perpendicular bisector of AB", ["a circle round A", "the line AB", "a point"], "Standard locus."),
   N("A circular region is at most 10 m from a tree (π = 3.14). Find its area (m²).", 314, "3.14 × 100.", unit="m²")],
  [N("A and B are 12 cm apart. Find the distance from A to the midpoint of AB.", 6, "The bisector meets AB at its midpoint.", unit="cm"),
   O("Describe the locus of points that are 2 cm from the line segment AB where AB = 6 cm.", "Two straight lines parallel to AB, 2 cm from it, joined by semicircles of radius 2 cm at the ends A and B (a 'stadium' shape).", "1 mark for two parallel lines, 1 mark for 2 cm distance, 1 mark for the semicircles at the ends.", points=3)],
  P("A market stall in Bafoussam is at S and a second stall is at M, 10 m apart. A customer stands where she is nearer to S than to M and within 8 m of S.",
    [pn("How far from S is the dividing line (the bisector) (m)?", 5, "Half of 10 m.", unit="m", pts=1),
     pn("On the dividing line, how far from the midpoint is the point exactly 8 m from S (m, 2 d.p.)?", 6.24, "√(64 − 25) = 6.245.", tol=0.01, unit="m", pts=2),
     pm("Which statement describes the allowed region?", "Inside the circle of radius 8 m round S and on S's side of the bisector", ["Outside the circle", "On M's side of the bisector", "Only on the bisector"], "Both conditions together.", 2)], figure=lf),
  [("A locus is:", "a set of points satisfying a condition", ["a single point", "a measurement", "an angle"], "Definition."),
   ("The locus of points equidistant from two lines that cross is:", "the angle bisector", ["a circle", "a parallel line", "a point"], "Standard locus."),
   ("'At most 5 cm from P' includes:", "the circle and its inside", ["the circle only", "the outside only", "the centre only"], "At most."),
   ("A dashed boundary line means the points on the line are:", "not included", ["included", "the centre", "at the origin"], "Strict condition."),
   ("The locus of points 3 cm from a straight line is:", "two parallel lines", ["a circle", "a point", "one line"], "One on each side.")],
  ill=(lf, "S and M with their perpendicular bisector (red) and a circle of radius 8 km round S meeting it at P and P′.", "Two points S and M, a vertical bisector line and a circle round S crossing it at two points."),
  notes=["Constructions must be done with ruler and compasses in practice; the figure only shows the result."])

assert 6 * 1.5 == 9 and 8 * 1.5 == 12 and 10 * 1.5 == 15 and 1.5**2 == 2.25 and 1.5**3 == 3.375 and 400 * 3.375 == 1350 and 80 * 2.25 == 180 and 2 / 3 * 18 == 12 and 15 * 2.5 == 37.5
simf = shapes([POLY([30, 150, 110, 150, 30, 90], fill="lightyellow"), POLY([180, 170, 300, 170, 180, 80], fill="lightblue"),
               T(70, 168, "8", 13), T(20, 122, "6", 13, anchor="end"), T(80, 112, "10", 13, anchor="start"), T(240, 188, "12", 13), T(170, 128, "9", 13, anchor="end"), T(250, 112, "15", 13, anchor="start")], 400, 210)
lesson(ch, "similarity", "Similar figures: lengths, areas and volumes",
  ["Recognise similar shapes and find the scale factor", "Calculate unknown lengths using ratios", "Use the area ratio k² and the volume ratio k³", "Solve problems with shadows, maps and models"],
  [("definition", "Similar figures", "Two figures are **similar** if they have the same shape: corresponding angles are equal and corresponding sides are in the same ratio, called the **scale factor** k. Triangles with sides 6, 8, 10 and 9, 12, 15 are similar with k = 15 ÷ 10 = 1.5. Two triangles are similar if they have **equal angles**."),
   ("propriete", "Lengths, areas and volumes", "If the scale factor of lengths is k, then:\n- **areas** are in the ratio k² (surface areas of solids too);\n- **volumes** (and capacities) are in the ratio k³.\nExample: k = 1.5 gives area ratio 2.25 and volume ratio 3.375."),
   ("methode", "Method", "Match corresponding sides (the sides opposite equal angles). Write the scale factor as **new ÷ old**. Multiply lengths by k, areas by k², volumes by k³. To go back, divide. When given an area ratio, take the **square root** to find k; when given a volume ratio, take the **cube root**."),
   ("pieges", "Common mistakes", "- Multiplying areas by k instead of k².\n- Matching sides that do not correspond.\n- Forgetting that the scale factor for a **reduction** is less than 1.")],
  [("Example 1: similar triangles", "Triangles with sides 6, 8, 10 cm and 9, 12, 15 cm are similar. Find the scale factor and the ratio of their areas.",
    ["Scale factor = 9 ÷ 6 = 12 ÷ 8 = 15 ÷ 10 = 1.5.", "Area ratio = 1.5² = 2.25.", "Check: areas ½ × 6 × 8 = 24 and ½ × 9 × 12 = 54; 54 ÷ 24 = 2.25."], "**k = 1.5; area ratio 2.25.**", simf),
   ("Example 2: tins and shadows", "Two similar tins have heights 12 cm and 18 cm. The small tin holds 400 ml and its label has area 80 cm². Find the capacity of the large tin and the label area. A 2 m pole casts a 3 m shadow while a tree casts an 18 m shadow: find the height of the tree.",
    ["k = 18 ÷ 12 = 1.5. Capacity = 400 × 1.5³ = 400 × 3.375 = 1 350 ml.", "Label area = 80 × 1.5² = 80 × 2.25 = 180 cm².", "Tree: the shadow triangles are similar: height = 2 × 18 ÷ 3 = 12 m."], "**1 350 ml; 180 cm²; 12 m.**")],
  [N("Two similar shapes have lengths 4 cm and 10 cm. Find the scale factor from the small to the large.", 2.5, "10 ÷ 4.", ),
   N("A model has scale factor 3. Find the ratio of the areas.", 9, "3² = 9."),
   M("The volume ratio for similar solids with scale factor 2 is:", "8", ["2", "4", "6"], "2³.")],
  [N("The areas of two similar figures are 20 cm² and 45 cm². Find the scale factor from the small to the large.", 1.5, "√(45/20) = 1.5.", ),
   O("A photograph 10 cm by 15 cm is enlarged so that its width becomes 25 cm. Find the new height and explain why the shape is kept.", "k = 25 ÷ 10 = 2.5, so the height is 15 × 2.5 = 37.5 cm. All lengths are multiplied by the same factor, so the shape (the ratios and angles) is unchanged.", "1 mark for k, 1 mark for 37.5 cm, 1 mark for the explanation.", points=3)],
  P("A model of a water tank for a village at Bali is made at scale 1 : 20. The model holds 0.5 litres.",
    [pn("How many times larger is each length of the real tank than the model?", 20, "Scale 1 : 20.", pts=1),
     pn("Find the capacity ratio real : model.", 8000, "20³ = 8 000.", pts=2),
     pn("Find the real capacity in litres.", 4000, "0.5 × 8 000.", unit="l", pts=2)]),
  [("Similar shapes have:", "equal angles and proportional sides", ["equal sides", "equal areas", "equal perimeters only"], "Definition."),
   ("If k = 3, the area ratio is:", "9", ["3", "6", "27"], "k²."),
   ("If k = 2, the volume ratio is:", "8", ["2", "4", "6"], "k³."),
   ("Two similar solids have volumes in ratio 27 : 1. The lengths are in ratio:", "3 : 1", ["27 : 1", "9 : 1", "81 : 1"], "Cube root."),
   ("A scale factor less than 1 gives:", "a reduction", ["an enlargement", "a congruent figure", "a reflection"], "Smaller figure.")],
  ill=(simf, "Two similar right triangles with sides 6, 8, 10 and 9, 12, 15.", "A small and a large right-angled triangle with the lengths of their sides."),
  notes=["The model scale and capacities are invented for practice."])

# ===================================================================== STATISTICS
ch = p.chapter("statistics", "Statistics: cumulative frequency and histograms", REF)

fq = [4, 10, 18, 24, 14, 8, 2]; cf_ = [sum(fq[:i + 1]) for i in range(7)]
assert cf_ == [4, 14, 32, 56, 70, 78, 80] and sum(fq) == 80
med = 30 + (40 - 32) / 24 * 10; q1 = 20 + (20 - 14) / 18 * 10; q3 = 40 + (60 - 56) / 14 * 10
assert abs(med - 33.33) < 0.01 and abs(q1 - 23.33) < 0.01 and abs(q3 - 42.86) < 0.01 and abs((q3 - q1) - 19.52) < 0.01 and 80 - 56 == 24 and 80 - 70 == 10
segs = [(10 * i, ([0] + cf_)[i], 10 * (i + 1), cf_[i], "blue", False) for i in range(7)]
segs += [(0, 40, round(med, 2), 40, "red", True), (round(med, 2), 40, round(med, 2), 0, "red", True)]
cg = plot(0, 70, 0, 90, segments=segs, points=[(10 * (i + 1), cf_[i], None, "blue") for i in range(7)], grid=10, xlabel="mark", ylabel="cumulative frequency", w=420, h=280)
lesson(ch, "cumulative-frequency", "Cumulative frequency, median and quartiles",
  ["Build a cumulative frequency table from grouped data", "Draw a cumulative frequency curve (ogive)", "Estimate the median, quartiles and interquartile range", "Use the curve to estimate how many values lie above or below a given value"],
  [("definition", "Cumulative frequency", "For grouped data the **cumulative frequency** is the running total of the frequencies, up to the **upper boundary** of each class. Marks of 80 pupils in classes of width 10 with frequencies 4, 10, 18, 24, 14, 8, 2 give cumulative frequencies 4, 14, 32, 56, 70, 78, 80 at marks 10, 20, 30, 40, 50, 60, 70."),
   ("methode", "The curve and the estimates", "Plot the cumulative frequency against the **upper boundary** and join the points with a smooth curve (or straight segments in a polygon), starting at (0, 0). The **median** is the value at n ÷ 2, the **lower quartile Q₁** at n ÷ 4 and the **upper quartile Q₃** at 3n ÷ 4. Draw a horizontal line from the frequency to the curve, then down to the axis. The **interquartile range** is Q₃ − Q₁."),
   ("methode", "Reading the curve", "To find how many pupils scored more than 40: read the cumulative frequency at 40 (56) and subtract from the total: 80 − 56 = 24. By interpolating inside the class we may calculate: median position 40, so median = 30 + (40 − 32) ÷ 24 × 10 = 33.3. Q₁ at position 20: 20 + (20 − 14) ÷ 18 × 10 = 23.3. Q₃ at position 60: 42.9."),
   ("pieges", "Common mistakes", "- Plotting at the middle of the class instead of the upper boundary.\n- Forgetting to start the curve at zero.\n- Using the frequency (not the cumulative frequency) to find the median position.")],
  [("Example 1: median and quartiles", "From the cumulative frequencies 4, 14, 32, 56, 70, 78, 80 for classes of width 10 from 0 to 70, estimate the median, the quartiles and the interquartile range.",
    ["Median at position 40 lies in the class 30–40: 30 + (40 − 32) ÷ 24 × 10 = 33.3.", "Q₁ at position 20 is in 20–30: 20 + (20 − 14) ÷ 18 × 10 = 23.3. Q₃ at 60 is in 40–50: 40 + (60 − 56) ÷ 14 × 10 = 42.9.", "IQR = 42.9 − 23.3 = 19.5."], "**Median ≈ 33.3; Q₁ ≈ 23.3; Q₃ ≈ 42.9; IQR ≈ 19.5.**", cg),
   ("Example 2: counting above a mark", "Using the same data, how many pupils scored more than 40? The pass mark is 40: what percentage passed?",
    ["Cumulative frequency at 40 is 56, so 56 pupils scored 40 or less.", "Above 40: 80 − 56 = 24 pupils.", "Percentage = 24 ÷ 80 × 100 = 30%."], "**24 pupils; 30%.**")],
  [N("The cumulative frequencies are 5, 12, 20, 25. Find the frequency of the third class.", 8, "20 − 12 = 8."),
   N("For a set of 100 values, at which cumulative frequency do we read the median?", 50, "n ÷ 2.", ),
   M("On the cumulative frequency graph the points are plotted at:", "the upper class boundary", ["the class midpoint", "the lower class boundary", "the class width"], "Cumulative total up to the end of the class.")],
  [N("Q₁ = 24 and Q₃ = 41. Find the interquartile range.", 17, "41 − 24.", ),
   O("Explain why the interquartile range is better than the range to describe the spread when there are unusual values.", "The range uses only the largest and smallest values, so one extreme value changes it a lot. The IQR measures the spread of the middle half of the data, so extreme values hardly affect it.", "1 mark for the range using the extremes, 1 mark for the IQR being the middle half, 1 mark for the conclusion.", points=3)],
  P("The cumulative frequency curve shows the marks of 80 pupils at a school in Bafoussam. Frequencies by class (width 10) are 4, 10, 18, 24, 14, 8, 2.",
    [pn("How many pupils scored 30 or less?", 32, "4 + 10 + 18.", pts=1),
     pn("Estimate the median mark (1 d.p.).", 33.3, "30 + 8/24 × 10 = 33.3.", tol=0.1, pts=2),
     pn("Estimate the number of pupils scoring more than 50.", 10, "80 − 70.", pts=2)], figure=cg),
  [("Cumulative frequency is:", "a running total", ["the largest frequency", "the class width", "the mean"], "Definition."),
   ("The median is at cumulative frequency:", "n ÷ 2", ["n", "n ÷ 4", "2n"], "Middle value."),
   ("The interquartile range is:", "Q₃ − Q₁", ["Q₁ − Q₃", "Q₃ + Q₁", "median − Q₁ only"], "Spread of the middle half."),
   ("The final cumulative frequency equals:", "the total number of values", ["the median", "the mean", "zero"], "Total."),
   ("To find how many are above a value we:", "subtract its cumulative frequency from the total", ["add it to the total", "multiply by 2", "divide by 2"], "Complement.")],
  ill=(cg, "A cumulative frequency graph for 80 marks with the median read at 33.3.", "A rising curve from 0 to 80 with dashed lines showing the median at cumulative frequency 40."),
  notes=["The curve is drawn as joined straight segments between the plotted points; students in exams draw a smooth curve."])

classes = [(0, 10, 8), (10, 20, 14), (20, 40, 30), (40, 60, 12), (60, 100, 16)]
fd = [f / (b - a) for a, b, f in classes]; assert sum(f for *_, f in classes) == 80 and fd == [0.8, 1.4, 1.5, 0.6, 0.4]
assert 0.6 * 10 == 6 and sum(m * f for m, f in zip([5, 15, 30, 50, 80], [8, 14, 30, 12, 16])) / 80 == 37.875
axh = Axes(0, 100, 0, 1.8, w=420, h=280, xticks=[0, 10, 20, 40, 60, 100], yticks=[0.5, 1.0, 1.5])
for (a, b, f), d in zip(classes, fd): axh.poly([(a, 0), (b, 0), (b, d), (a, d)], fill="lightblue", color="blue")
axh.text(55, 1.72, "frequency density", 12)
hist = axh.fig()
lesson(ch, "histograms", "Histograms with unequal class widths",
  ["Calculate the frequency density", "Draw a histogram with unequal class widths", "Read frequencies from the areas of the bars", "Estimate the mean from grouped data"],
  [("definition", "Frequency density", "In a histogram the **area** of each bar is proportional to the frequency. When the classes have unequal widths, the height of the bar is the **frequency density**: frequency density = frequency ÷ class width. The bars touch, there are no gaps, and the horizontal axis is a continuous scale."),
   ("methode", "Drawing and reading", "Waiting times (minutes) at a clinic with frequencies 8, 14, 30, 12, 16 in the classes 0–10, 10–20, 20–40, 40–60, 60–100 have densities 0.8, 1.4, 1.5, 0.6, 0.4. To find the frequency in part of a class, multiply the density by the **width of that part**: between 50 and 60 minutes, 0.6 × 10 = 6 people."),
   ("methode", "Mean from grouped data", "Use the **midpoint** of each class: estimated mean = Σ(f × midpoint) ÷ Σf. With midpoints 5, 15, 30, 50, 80: (40 + 210 + 900 + 600 + 1 280) ÷ 80 = 3 030 ÷ 80 ≈ 37.9 minutes. This is an **estimate** because we do not know the exact values."),
   ("pieges", "Common mistakes", "- Using the frequency as the height of a bar when widths are unequal.\n- Leaving gaps between the bars.\n- Forgetting to multiply the density by the width when reading frequencies.")],
  [("Example 1: frequency density", "Waiting times of 80 patients at a Bamenda clinic: 0–10 min (8), 10–20 (14), 20–40 (30), 40–60 (12), 60–100 (16). Find the frequency densities and the height of the tallest bar.",
    ["Densities: 8 ÷ 10 = 0.8; 14 ÷ 10 = 1.4; 30 ÷ 20 = 1.5; 12 ÷ 20 = 0.6; 16 ÷ 40 = 0.4.", "The tallest bar is 1.5, for the class 20–40.", "Check: areas 8 + 14 + 30 + 12 + 16 = 80."], "**0.8, 1.4, 1.5, 0.6, 0.4; tallest 1.5.**", hist),
   ("Example 2: estimates", "Use the histogram to estimate how many patients waited between 50 and 60 minutes, and the mean waiting time.",
    ["Between 50 and 60 the density is 0.6, so 0.6 × 10 = 6 patients.", "Mean = (8×5 + 14×15 + 30×30 + 12×50 + 16×80) ÷ 80.", "= 3 030 ÷ 80 ≈ 37.9 minutes."], "**6 patients; mean about 37.9 minutes.**")],
  [N("A class of width 5 has frequency 20. Find the frequency density.", 4, "20 ÷ 5.", ),
   N("A bar has width 20 and height 1.5. Find the frequency.", 30, "Area = 1.5 × 20.", ),
   M("In a histogram the frequency is represented by the:", "area of the bar", ["height of the bar only", "width of the bar", "colour"], "Area ∝ frequency.")],
  [N("A histogram bar is 15 units wide and 0.8 high. How many values does it represent?", 12, "15 × 0.8.", ),
   O("Explain why a bar chart with equal widths can use height for frequency but a histogram with unequal widths cannot.", "If widths are equal, area is proportional to height, so height shows frequency. With unequal widths a wider class would look bigger even for the same frequency, so the area must show the frequency and the height is the density.", "1 mark for equal widths, 1 mark for unequal widths, 1 mark for the area/density idea.", points=3)],
  P("The histogram shows the waiting times of 80 patients (classes 0–10, 10–20, 20–40, 40–60, 60–100; frequencies 8, 14, 30, 12, 16).",
    [pn("Find the frequency density of the class 60–100.", 0.4, "16 ÷ 40.", tol=0.001, pts=1),
     pn("How many waited more than 60 minutes?", 16, "The whole class 60–100.", pts=1),
     pn("Estimate the mean waiting time (minutes, 1 d.p.).", 37.9, "3 030 ÷ 80 = 37.875.", tol=0.1, unit="min", pts=3)], figure=hist),
  [("Frequency density equals:", "frequency ÷ class width", ["frequency × class width", "class width ÷ frequency", "frequency + width"], "Definition."),
   ("The area of a histogram bar represents:", "the frequency", ["the class width", "the mean", "the median"], "Area."),
   ("The bars of a histogram:", "touch each other", ["have gaps", "are all the same colour", "are all equal in height"], "Continuous data."),
   ("To estimate the mean of grouped data we use:", "the class midpoints", ["the upper boundaries", "the frequencies only", "the class widths"], "Midpoints."),
   ("The frequency of a part of a class is:", "density × width of that part", ["density ÷ width", "density + width", "width only"], "Area of the part.")],
  ill=(hist, "A histogram of waiting times with unequal class widths; the tallest bar is 1.5 for 20–40 minutes.", "Five touching bars of different widths and heights."),
  notes=["The waiting times are invented for practice."])

# ===================================================================== MENSURATION, NUMBER BASES
ch = p.chapter("measure-number", "Mensuration and number bases", REF)

pi7 = 22 / 7
assert abs(2 * pi7 * 7 * 27 - 1188) < 1e-9 and abs(pi7 * 3.5**2 * 8 - 308) < 1e-9 and abs(2 / 3 * pi7 * 3.5**3 - 89.833) < 1e-3 and abs(2 * pi7 * 3.5 * 8 - 176) < 1e-9 and abs(2 * pi7 * 3.5**2 - 77) < 1e-9
assert abs(pi7 * 36 * 12 / 3 - pi7 * 9 * 6 / 3 - 396) < 1e-9 and 253 * 800 == 202400
silo = shapes([POLY([130, 190, 270, 190, 270, 100, 130, 100], fill="lightyellow"), PATH("M 130 100 A 70 70 0 0 1 270 100 Z", fill="lightorange"), T(200, 215, "d = 7 m", 13), T(285, 150, "h = 8 m", 13, anchor="start"), T(200, 60, "hemisphere r = 3.5 m", 13)], 400, 235)
lesson(ch, "mensuration", "Mensuration: composite solids, frustum and surface area",
  ["Calculate the surface area of a cylinder and a hemisphere", "Calculate the volume of a composite solid", "Calculate the volume of a frustum of a cone", "Solve practical problems with costs"],
  [("formule", "Cylinder and hemisphere", "For a cylinder of radius r and height h, and a hemisphere of radius r:", "A_{\\text{cyl}} = 2\\pi r h + 2\\pi r^2 \\qquad V_{\\text{hemi}} = \\frac{2}{3}\\pi r^3 \\qquad A_{\\text{curved hemi}} = 2\\pi r^2"),
   ("methode", "Composite solids", "Split the solid into simple shapes, compute each volume (or area), then add. Be careful about **faces that are joined**: they are not on the outside. A silo made of a cylinder (r = 3.5 m, h = 8 m) with a hemisphere on top (π = 22/7): V = 22/7 × 3.5² × 8 + 2/3 × 22/7 × 3.5³ = 308 + 89.8 = 397.8 m³."),
   ("methode", "Frustum of a cone", "A **frustum** is a cone with its top cut off by a plane parallel to the base. Its volume is the **big cone minus the small cone** removed. The two cones are similar. For a big cone with r = 6 and h = 12, and a small cone cut off with r = 3 and h = 6 (π = 22/7): V = 452.6 − 56.6 = 396."),
   ("pieges", "Common mistakes", "- Including the joining face of a composite solid in the outside area.\n- Forgetting the top circle of a closed cylinder (2πr²).\n- Subtracting the wrong height: the frustum's height is the **difference** of the two heights (12 − 6 = 6).")],
  [("Example 1: a silo", "A grain silo at Garoua has a cylindrical part with radius 3.5 m and height 8 m and a hemispherical roof (π = 22/7). Find its volume, and the outside area excluding the base. Painting costs 800 FCFA per m².",
    ["Volume = 308 + 89.8 = 397.8 m³.", "Curved cylinder = 2 × 22/7 × 3.5 × 8 = 176 m²; hemisphere roof = 2 × 22/7 × 3.5² = 77 m²; total 253 m².", "Cost = 253 × 800 = 202 400 FCFA."], "**397.8 m³; 253 m²; 202 400 FCFA.**", silo),
   ("Example 2: a frustum", "A bucket has the shape of a frustum with a base radius of 6 cm and a top radius of 3 cm cut at height 6 cm of a cone of height 12 cm. Find its volume (π = 22/7).",
    ["Big cone: 1/3 × 22/7 × 6² × 12 = 452.57 cm³.", "Small cone removed: 1/3 × 22/7 × 3² × 6 = 56.57 cm³.", "Frustum = 452.57 − 56.57 = 396 cm³."], "**396 cm³**")],
  [N("Find the total surface area of a closed cylinder with r = 7 cm and h = 20 cm (π = 22/7).", 1188, "2 × 22/7 × 7 × 27 = 1 188 cm².", unit="cm²"),
   N("Find the volume of a hemisphere of radius 3 cm (π = 3.14, answer to 1 d.p.).", 56.5, "2/3 × 3.14 × 27 = 56.52.", tol=0.1, unit="cm³"),
   M("The volume of a frustum is found by:", "big cone minus small cone", ["big cone plus small cone", "big cone times 2", "the cylinder volume"], "Cut-off part removed.")],
  [N("A solid is a cylinder (r = 7 cm, h = 10 cm) with a cone of the same radius and height 24 cm on top. Find the total volume (cm³, π = 22/7).", 2772, "Cylinder 1 540 + cone 1 232 = 2 772.", unit="cm³"),
   O("Explain how you would find the volume of a frustum when you only know the radii of the two circles and the height of the frustum.", "Use similar triangles to find the height of the missing small cone, then calculate the big cone and small cone volumes and subtract them.", "1 mark for similar triangles, 1 mark for both cones, 1 mark for subtracting.", points=3)],
  P("A water tower near Bafoussam is a cylinder of radius 3.5 m and height 8 m topped by a hemisphere of the same radius (π = 22/7).",
    [pn("Find the volume of the cylinder (m³).", 308, "22/7 × 12.25 × 8.", unit="m³", pts=1),
     pn("Find the volume of the whole tower (m³, 1 d.p.).", 397.8, "308 + 89.8.", tol=0.1, unit="m³", pts=2),
     pn("How many litres can it hold (nearest 100)?", 397800, "1 m³ = 1 000 litres.", tol=100, unit="l", pts=2)], figure=silo),
  [("The volume of a hemisphere is:", "(2/3)πr³", ["(4/3)πr³", "πr²h", "2πr²"], "Half the sphere."),
   ("The total surface area of a closed cylinder is:", "2πrh + 2πr²", ["πr²h", "2πr + 2πh", "πrh"], "Curved plus two ends."),
   ("A frustum is:", "a cone with the top cut off", ["a cone with no base", "a cylinder", "a sphere cut in half"], "Definition."),
   ("The curved surface area of a hemisphere is:", "2πr²", ["4πr²", "πr²", "3πr²"], "Half of the sphere's area."),
   ("The volume of a composite solid is found by:", "adding the volumes of its parts", ["multiplying them", "subtracting always", "using the biggest part only"], "Split and add.")],
  ill=(silo, "A silo: a cylinder of radius 3.5 m and height 8 m with a hemispherical roof.", "A rectangle topped by a semicircle representing a cylinder with a dome."),
  notes=["The silo and bucket sizes are invented for practice."])

assert int("1101", 2) == 13 and bin(45) == "0b101101" and 2 * 25 + 1 * 5 + 3 == 58 and 4 * 25 == 100 and 11 + 6 == 17 and bin(17) == "0b10001" and 13 * 4 == 52 and 2 * 25 + 0 + 2 == 52 and 43 - 23 == 20 and 2 * 8 + 4 == 20 and 2 * 8 + 1 == 17
tb = ptable(["2⁴", "2³", "2²", "2¹", "2⁰"], [["16", "8", "4", "2", "1"], ["0", "1", "1", "0", "1"]], colw=60)
lesson(ch, "number-bases", "Number bases",
  ["Write numbers in bases 2, 5, 8 and other bases", "Convert between base 10 and other bases", "Add, subtract and multiply in other bases", "Solve simple equations involving bases"],
  [("definition", "Place value in a base", "In **base 10** the places are powers of 10. In **base b** the places are powers of b and the digits go from 0 to b − 1. We write the base as a subscript: 1101₂ is a base-2 (binary) number: 1×8 + 1×4 + 0×2 + 1×1 = 13. In base 5: 213₅ = 2×25 + 1×5 + 3 = 58."),
   ("methode", "Converting base 10 to base b", "Divide repeatedly by b and read the **remainders from the last to the first**. Example: 45 to base 2: 45 ÷ 2 = 22 r 1; 22 ÷ 2 = 11 r 0; 11 ÷ 2 = 5 r 1; 5 ÷ 2 = 2 r 1; 2 ÷ 2 = 1 r 0; 1 ÷ 2 = 0 r 1. Reading upwards: 101101₂. Check: 32 + 8 + 4 + 1 = 45."),
   ("methode", "Calculating in other bases", "Add or multiply column by column, **carrying when a column reaches the base** (not 10). 1011₂ + 110₂: 1 + 0 = 1; 1 + 1 = 10 (write 0 carry 1); 0 + 1 + 1 = 10 (write 0 carry 1); 1 + 1 = 10. The answer is 10001₂ (11 + 6 = 17). Or convert to base 10, calculate, and convert back."),
   ("pieges", "Common mistakes", "- Using a digit not allowed in the base (a 5 in base 5).\n- Reading the remainders from top to bottom instead of bottom to top.\n- Carrying at 10 instead of at the base.")],
  [("Example 1: conversions", "Convert 45 to base 2, 213₅ to base 10, and 100 to base 5.",
    ["45 = 32 + 8 + 4 + 1 = 101101₂ (using the remainder method).", "213₅ = 2 × 25 + 1 × 5 + 3 = 58.", "100 ÷ 25 = 4 remainder 0, so 100 = 4 × 25 + 0 × 5 + 0 = 400₅."], "**101101₂; 58; 400₅**", tb),
   ("Example 2: arithmetic and an unknown base", "Work out 1011₂ + 110₂ and 23₅ × 4₅. Find the base b if 21 in base b equals 17 in base 10.",
    ["1011₂ + 110₂ = 10001₂ (11 + 6 = 17).", "23₅ = 13 and 13 × 4 = 52 = 2 × 25 + 0 × 5 + 2, so 202₅.", "2b + 1 = 17 gives b = 8."], "**10001₂; 202₅; base 8.**")],
  [N("Convert 1101₂ to base 10.", 13, "8 + 4 + 0 + 1.", ),
   N("Convert 213₅ to base 10.", 58, "2 × 25 + 5 + 3.", ),
   M("Which digit cannot appear in a base-5 number?", "5", ["0", "4", "3"], "Base 5 uses the digits 0 to 4.")],
  [N("Express 53₈ − 27₈ in base 8 and give its value in base 10.", 20, "43 − 23 = 20, which is 24₈.", ),
   O("Explain why computers use base 2.", "Electronic circuits have two stable states, on and off, which are naturally represented by the digits 1 and 0, so every number can be stored using only two symbols.", "1 mark for two states, 1 mark for the digits 0 and 1, 1 mark for the conclusion.", points=3)],
  P("A student at a school in Yaoundé learns to use bases with the number 37.",
    [pn("Write 37 in base 2 (it is 100101₂). How many digits does the binary number have?", 6, "37 = 32 + 4 + 1 = 100101₂, which has 6 digits.", pts=1),
     pn("Convert 37 to base 5 and give the sum of the digits of your answer.", 5, "37 = 1 × 25 + 2 × 5 + 2, so 122₅; 1 + 2 + 2 = 5.", pts=2),
     pn("Convert 100101₂ back to base 10.", 37, "32 + 4 + 1.", pts=1),
     pn("In which base b is the number 37 (base 10) written as 45?", 8, "4b = 32, so b = 8.", pts=2)]),
  [("In base 2 the digits used are:", "0 and 1", ["0 to 2", "1 and 2", "0 to 9"], "Binary."),
   ("1010₂ in base 10 is:", "10", ["12", "8", "5"], "8 + 2."),
   ("10₅ in base 10 is:", "5", ["10", "2", "50"], "1 × 5 + 0."),
   ("To change a base-10 number to base 2 we divide by:", "2 repeatedly", ["10 repeatedly", "5 repeatedly", "the number itself"], "Remainder method."),
   ("In base 8 the largest digit is:", "7", ["8", "9", "10"], "Digits 0 to 7.")],
  ill=(tb, "Place values in base 2 and the binary number 01101 = 13.", "A table with powers of 2 and the digits 0, 1, 1, 0, 1 below."),
  notes=["The statement about computers using base 2 is simplified; check it fits the level."])

p.write()
