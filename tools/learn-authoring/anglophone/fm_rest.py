from _kit import *
import math
acos = lambda v: math.degrees(math.acos(v))

def build(p):
    # ================================================================ vectors & matrices
    ch = p.chapter("vectors-matrices", "Vectors and matrices", "O Level Further Mathematics — Vectors and matrices (to be checked)")
    a, b = (3, 4), (5, -12); dot = a[0] * b[0] + a[1] * b[1]; assert dot == -33 and abs(acos(dot / (5 * 13)) - 120.51) < 0.01
    assert 3 * 4 - 12 == 0 and ((2 * 1 + 7) / 3, (2 * 2 + 5) / 3) == (3, 3)
    AB, AC = (6, 4), (2, 6); cosA = (12 + 24) / (math.sqrt(52) * math.sqrt(40)); angA = acos(cosA); assert abs(angA - 37.87) < 0.01
    ox, oy, s = 30, 180, 20
    px = lambda x: ox + x * s; py = lambda y: oy - y * s
    it = axes_fig(260, 200, ox, oy, s, s, (0, 10), (0, 8))
    it += [LINE(px(2), py(1), px(8), py(5), color="blue", width=3, arrow="end"), LINE(px(2), py(1), px(4), py(7), color="red", width=3, arrow="end"), LINE(px(8), py(5), px(4), py(7), color="grey", width=2, dash=True),
           T(px(2) - 12, py(1) + 14, "A", 14), T(px(8) + 10, py(5), "B", 14), T(px(4) - 4, py(7) - 8, "C", 14), T(px(5) + 8, py(3) + 14, "AB", 13, color="blue"), T(px(2.3) - 18, py(4) - 4, "AC", 13, color="red")]
    L = lesson(ch, "vectors-fm", "Vectors: scalar product, ratios and angles",
        ["Use the scalar (dot) product to find the angle between two vectors.", "Use the condition for perpendicular vectors.", "Find the position vector of a point dividing a line in a given ratio."],
        [("formule", "Scalar product", "For a = (a₁, a₂) and b = (b₁, b₂) the scalar product and the angle θ between them are:", "a \\cdot b = a_1 b_1 + a_2 b_2 = |a||b|\\cos\\theta"),
         ("propriete", "Perpendicular and parallel", "Vectors are **perpendicular** when a · b = 0 and **parallel** when one is a multiple of the other. For three dimensions add a third component: $|a| = \\sqrt{a_1^2 + a_2^2 + a_3^2}$."),
         ("formule", "Ratio division", "If P lies on AB with AP : PB = m : n, the position vector of P is:", "p = \\frac{n\\,a + m\\,b}{m + n}"),
         ("pieges", "Common mistakes", "- Computing a · b as a vector: the scalar product is a **number**.\n- Using the angle between AB and AC at the wrong vertex: both vectors must start at A.\n- Swapping the ratio: AP : PB = 1 : 2 gives p = (2a + b)/3, not (a + 2b)/3.")],
        [("Example 1: angle between vectors", "Find the scalar product and the angle between a = (3, 4) and b = (5, −12). For which k are (k, 2) and (3, −6) perpendicular?",
          ["a · b = 15 − 48 = −33; |a| = 5 and |b| = 13.", "cos θ = −33 ÷ 65 = −0.5077, so θ ≈ 120.5°.", "(k, 2) · (3, −6) = 3k − 12 = 0, so k = 4."], "**a · b = −33; θ ≈ 120.5°; k = 4.**", shapes(it, 260, 200)),
         ("Example 2: ratio and angle in a triangle", "A(2, 1), B(8, 5), C(4, 7). Find the angle BAC. If A = (1, 2) and B = (7, 5), find the point P with AP : PB = 1 : 2.",
          ["AB = (6, 4), AC = (2, 6): AB · AC = 12 + 24 = 36; |AB| = √52, |AC| = √40; cos A = 36 ÷ 45.61 = 0.789, so A ≈ 37.9°.", "p = (2a + b)/3 = ((2 + 7)/3, (4 + 5)/3).", "P = (3, 3). Check: AP = (2, 1) and PB = (4, 2) = 2 AP."], "**Angle BAC ≈ 37.9°; P = (3, 3).**")],
        [N("Find a · b for a = (2, 3) and b = (4, −1).", 5, "8 − 3 = 5."),
         M("Vectors (2, 1) and (−1, 2) are:", "perpendicular", ["parallel", "equal", "opposite"], "−2 + 2 = 0."),
         N("Find |(2, 3, 6)|.", 7, "√(4 + 9 + 36) = 7.")],
        [N("Find the angle between (1, 0) and (1, 1) in degrees.", 45, "cos θ = 1/√2.", unit="°"),
         N("A = (0, 0), B = (9, 6). P divides AB so that AP : PB = 2 : 1. Find the x-coordinate of P.", 6, "p = (1 × a + 2 × b)/3 = (18, 12)/3 = (6, 4).")],
        P("In a survey map of a plot in Bamenda (units in metres), the corners are A(2, 1), B(8, 5) and C(4, 7).",
          [pn("Find the length AB to 2 d.p.", round(math.sqrt(52), 2), "√(36 + 16) = √52 = 7.21 m.", tol=0.01, unit="m", pts=1),
           pn("Find the angle BAC in degrees (1 d.p.).", round(angA, 1), "cos A = 36/(√52 √40) = 0.789; A = 37.9°.", tol=0.1, unit="°", pts=2),
           pn("Find the area of triangle ABC using ½|AB||AC| sin A (to the nearest m²).", round(0.5 * math.sqrt(52) * math.sqrt(40) * math.sin(math.radians(angA))), "½ × 7.211 × 6.325 × 0.6143 = 14 m². (Check: ½|6·6 − 4·2| = 14.)", tol=0.5, unit="m²", pts=2)]),
        [("a · b is:", "a number", ["a vector", "a matrix", "an angle"], "Scalar product."),
         ("a · b = 0 means:", "perpendicular", ["parallel", "equal", "zero length"], "Cos 90° = 0."),
         ("AP : PB = 1 : 1 means P is the:", "midpoint", ["end A", "end B", "centre of a circle"], "Equal parts."),
         ("(3, 4) · (3, 4) =", "25", ["7", "12", "5"], "|a|²."),
         ("cos θ = (a · b) ÷ ?", "|a||b|", ["|a| + |b|", "a × b", "|a| − |b|"], "Definition.")],
        minutes=35, notes=["Scalar product and ratio division may lie beyond the O Level syllabus: confirm."])

    A_ = [[3, 1], [5, 2]]; assert 3 * 2 - 1 * 5 == 1 and (2 * 11 - 19, -5 * 11 + 3 * 19) == (3, 2) and 3 * 3 + 2 == 11 and 5 * 3 + 2 * 2 == 19
    rot = lambda P_: (-P_[1], P_[0]); assert rot((3, 1)) == (-1, 3) and 2 * 3 == 6 and 4 * (2 * 3 - 1 * 1) == 20
    m1, w1 = mat_items(30, 40, [[0, -1], [1, 0]]); m2, w2 = mat_items(w1 + 20, 40, [[3], [1]], cw=30); m3, w3 = mat_items(w2 + 50, 40, [[-1], [3]], cw=34)
    it = m1 + m2 + m3 + [T(w1 + 10, 70, "×", 20), T(w2 + 25, 70, "=", 20), T(60, 110, "rotation 90° anticlockwise", 13, anchor="start")]
    L = lesson(ch, "matrices-fm", "Matrices: inverse, equations and transformations",
        ["Find the inverse of a 2 × 2 matrix and use it to solve simultaneous equations.", "Find the matrix of a transformation and the image of a point.", "Use the determinant as an area scale factor."],
        [("formule", "Inverse and equations", "For A = (a b / c d) with det A = ad − bc ≠ 0, the inverse is (1/det) × (d −b / −c a). To solve AX = B multiply on the **left**: X = A⁻¹B.", "\\det A = ad - bc \\qquad AA^{-1} = A^{-1}A = I"),
         ("propriete", "Transformation matrices", "The image of the point (x, y) is the column (x / y) multiplied on the left by the matrix. The **columns** of the matrix are the images of (1, 0) and (0, 1). Examples: reflection in the x-axis (1 0 / 0 −1); rotation 90° anticlockwise about O (0 −1 / 1 0); enlargement scale factor k (k 0 / 0 k); reflection in y = x (0 1 / 1 0)."),
         ("propriete", "Area factor", "A transformation with matrix M multiplies all areas by |det M|. If det M = 0 the image collapses to a line or a point. If det M < 0 the shape is reflected (orientation reversed)."),
         ("pieges", "Common mistakes", "- Multiplying on the right: for X = A⁻¹B the inverse comes first.\n- Writing the images of (1, 0) and (0, 1) as rows instead of columns.\n- Forgetting that AB ≠ BA: the first transformation is on the **right** in BA.")],
        [("Example 1: solving equations", "Use the inverse of A = (3 1 / 5 2) to solve 3x + y = 11 and 5x + 2y = 19.",
          ["det A = 6 − 5 = 1, so A⁻¹ = (2 −1 / −5 3).", "x = 2 × 11 − 1 × 19 = 3 and y = −5 × 11 + 3 × 19 = 2.", "Check: 9 + 2 = 11 and 15 + 4 = 19."], "**x = 3, y = 2.**"),
         ("Example 2: transformations", "Find the image of (3, 1) under the 90° anticlockwise rotation matrix R = (0 −1 / 1 0). A matrix with determinant 5 maps a triangle of area 4 cm². Find the image area.",
          ["Image = R × (3 / 1) = (0×3 − 1×1 / 1×3 + 0×1) = (−1 / 3).", "Area of the image = |det M| × 4 = 5 × 4.", "The image area is 20 cm²."], "**(−1, 3); 20 cm².**", shapes(it, 330, 130))],
        [N("Find the determinant of (4 2 / 3 5).", 14, "20 − 6 = 14."),
         M("The image of (2, 3) under reflection in y = x (matrix (0 1 / 1 0)) is:", "(3, 2)", ["(−2, 3)", "(2, −3)", "(−3, −2)"], "The coordinates are swapped."),
         N("The matrix (2 0 / 0 3) maps the unit square (area 1) to a rectangle. Find its area.", 6, "det = 6.")],
        [N("Find the top-right entry of the inverse of (2 1 / 4 3).", -0.5, "det = 2; inverse = ½(3 −1 / −4 2): top right = −1/2.", tol=0.001),
         N("Solve 2x + y = 7, 4x + 3y = 19 using matrices: enter x.", 1, "det = 2; x = (3 × 7 − 1 × 19)/2 = 1 and y = (−4 × 7 + 2 × 19)/2 = 5.")],
        P("A designer in Yaoundé transforms a logo with the matrix M = (2 1 / 1 3). The logo's area is 3 cm².",
          [pn("Find det M.", 5, "6 − 1 = 5.", pts=1),
           pn("Find the area of the image.", 15, "5 × 3 = 15 cm².", unit="cm²", pts=1),
           pn("Find the image of the point (1, 2): enter the x-coordinate.", 4, "(2×1 + 1×2, 1×1 + 3×2) = (4, 7).", pts=2)]),
        [("A matrix has an inverse when:", "det ≠ 0", ["det = 0", "it is 1 × 1", "a = d"], "Non-singular."),
         ("The first column of a transformation matrix is the image of:", "(1, 0)", ["(0, 1)", "(1, 1)", "(0, 0)"], "Columns."),
         ("To solve AX = B:", "X = A⁻¹B", ["X = BA⁻¹", "X = A + B", "X = B/A⁻¹"], "Left-multiply."),
         ("|det M| gives the:", "area scale factor", ["length scale factor", "angle", "volume only"], "Area."),
         ("The enlargement matrix with k = 3:", "(3 0 / 0 3)", ["(3 3 / 3 3)", "(1 0 / 0 3)", "(0 3 / 3 0)"], "Diagonal.")],
        minutes=35)

    # ================================================================ probability & statistics
    ch = p.chapter("prob-stats", "Permutations, combinations, probability and statistics", "O Level Further Mathematics — Permutations, combinations, probability, statistics (to be checked)")
    C = math.comb; P_ = math.perm
    assert P_(5, 3) == 60 and C(9, 3) == 84 and math.factorial(8) // 2 == 20160 and C(9, 3) - C(5, 3) == 74 and P_(7, 2) == 42 and 3 * 5 * 4 * 3 == 180
    it = [RECT(40 + i * 90, 50, 70, 50, fill="lightblue") for i in range(3)] + [T(75, 82, "5", 20, bold=True), T(165, 82, "4", 20, bold=True), T(255, 82, "3", 20, bold=True),
          T(75, 40, "1st chair", 12), T(165, 40, "2nd chair", 12), T(255, 40, "3rd chair", 12), T(180, 140, "5 × 4 × 3 = 60 = ⁵P₃", 16, bold=True)]
    L = lesson(ch, "perm-comb", "Permutations and combinations",
        ["Use the multiplication principle, factorials, permutations and combinations.", "Count arrangements with repeated letters.", "Count selections with conditions."],
        [("formule", "Permutations and combinations", "A **permutation** counts ordered arrangements, a **combination** counts unordered selections of r items from n (written ⁿPᵣ and ⁿCᵣ):", "^nP_r = \\frac{n!}{(n-r)!} \\qquad ^nC_r = \\frac{n!}{r!(n-r)!}"),
         ("propriete", "Useful rules", "- n! = n(n − 1)…1 and 0! = 1.\n- n objects with p identical of one kind: n!/p! arrangements.\n- ⁿCᵣ = ⁿCₙ₋ᵣ and ⁿPᵣ = r! × ⁿCᵣ.\n- 'At least one' = total − none."),
         ("methode", "Order or not?", "Ask: does the **order** matter? Seats, ranks, passwords, president/secretary: permutation. Committees, teams, hands of cards: combination. For a restricted position (for example the last digit must be even), fill that position **first**."),
         ("pieges", "Common mistakes", "- Using a permutation when order does not matter (a committee).\n- Forgetting repeated letters: CAMEROON has two Os.\n- For 'at least one', adding many cases instead of using total − none.")],
        [("Example 1: arrangements", "In how many ways can 5 pupils fill 3 prefect posts (distinct posts)? In how many ways can the letters of CAMEROON be arranged?",
          ["Order matters: ⁵P₃ = 5 × 4 × 3 = 60.", "CAMEROON has 8 letters with the letter O twice: 8! ÷ 2! = 40 320 ÷ 2.", "= 20 160 arrangements."], "**60; 20 160.**", shapes(it, 340, 160)),
         ("Example 2: selections", "A committee of 3 is chosen from 5 men and 4 women. How many committees (a) in all (b) with at least one woman? How many even 4-digit numbers can be made from 1, 2, 3, 4, 5, 6 without repeats?",
          ["(a) ⁹C₃ = 84. (b) No women: ⁵C₃ = 10, so at least one woman: 84 − 10 = 74.", "Last digit even: 3 choices (2, 4, 6).", "Then 5 × 4 × 3 for the other digits: 3 × 60 = 180."], "**84; 74; 180.**")],
        [N("Find ⁶C₂.", 15, "6 × 5 ÷ 2 = 15."),
         N("Find ⁷P₂.", 42, "7 × 6 = 42."),
         M("A team of 5 is chosen from 8 players. The number of ways is:", "56", ["6 720", "40", "13"], "⁸C₅ = ⁸C₃ = 56 (order does not matter).")],
        [N("How many different 3-letter arrangements can be made from the letters of PLANT (no repeats)?", 60, "⁵P₃ = 60."),
         N("How many ways can 4 boys and 2 girls stand in a row if the two girls must stand together (treat them as one block, and the girls can swap)?", 240, "Block + 4 boys = 5 objects: 5! = 120; the girls swap: × 2 = 240.")],
        P("A school in Limbe chooses a committee of 4 from 6 boys and 4 girls.",
          [pn("How many committees can be chosen in all?", 210, "¹⁰C₄ = 210.", pts=1),
           pn("How many have exactly 2 girls?", 90, "⁴C₂ × ⁶C₂ = 6 × 15 = 90.", pts=2),
           pn("How many have at least one girl?", 195, "210 − ⁶C₄ = 210 − 15 = 195.", pts=2)]),
        [("0! equals:", "1", ["0", "undefined", "10"], "By definition."),
         ("⁵C₂ =", "10", ["20", "5", "25"], "5 × 4 ÷ 2."),
         ("Order matters in a:", "permutation", ["combination", "committee", "team"], "Arrangement."),
         ("⁸C₀ =", "1", ["8", "0", "80"], "One empty selection."),
         ("Letters of AAB arranged:", "3", ["6", "1", "9"], "3!/2!.")],
        minutes=30)

    assert abs(5 / 8 * 4 / 7 - 5 / 14) < 1e-12 and abs(2 * 5 / 8 * 3 / 7 - 15 / 28) < 1e-12 and abs(1 - 3 / 8 * 2 / 7 - 25 / 28) < 1e-12
    assert abs((0.4 + 0.5 - 0.4 * 0.5) - 0.7) < 1e-12 and abs(0.2 / 0.5 - 0.4) < 1e-12
    it = [T(40, 105, "start", 13), LINE(70, 100, 160, 50), LINE(70, 100, 160, 150), T(105, 60, "R 5/8", 13), T(105, 150, "B 3/8", 13),
          LINE(220, 50, 310, 20), LINE(220, 50, 310, 80), LINE(220, 150, 310, 120), LINE(220, 150, 310, 180),
          T(190, 55, "R", 14, bold=True), T(190, 155, "B", 14, bold=True), T(330, 20, "R 4/7", 13, anchor="start"), T(330, 80, "B 3/7", 13, anchor="start"), T(330, 120, "R 5/7", 13, anchor="start"), T(330, 180, "B 2/7", 13, anchor="start")]
    L = lesson(ch, "probability-fm", "Probability: addition, conditional and tree diagrams",
        ["Use P(A ∪ B) = P(A) + P(B) − P(A ∩ B).", "Use independence and conditional probability.", "Use tree diagrams with and without replacement."],
        [("formule", "Rules", "Addition rule; independent events; conditional probability:", "P(A \\cup B) = P(A) + P(B) - P(A \\cap B) \\qquad P(A \\cap B) = P(A)P(B) \\ \\text{(independent)} \\qquad P(A|B) = \\frac{P(A \\cap B)}{P(B)}"),
         ("methode", "Tree diagrams", "Draw a branch for each outcome and write its probability. **Multiply** along a path (AND) and **add** the paths that give the event (OR). Without replacement the second-stage probabilities change; with replacement they stay the same. 'At least one' = 1 − P(none)."),
         ("pieges", "Common mistakes", "- Adding instead of multiplying along a branch.\n- Not changing the denominator when items are not replaced.\n- Treating events as independent when they are not (mutually exclusive events with P > 0 are **not** independent).")],
        [("Example 1: without replacement", "A bag has 5 red and 3 blue balls. Two are drawn without replacement. Find P(both red), P(one of each) and P(at least one red).",
          ["P(RR) = 5/8 × 4/7 = 20/56 = 5/14.", "P(one of each) = 5/8 × 3/7 + 3/8 × 5/7 = 30/56 = 15/28.", "P(at least one red) = 1 − P(BB) = 1 − 3/8 × 2/7 = 1 − 6/56 = 25/28."], "**5/14; 15/28; 25/28.**", shapes(it, 400, 200)),
         ("Example 2: independent events", "P(A) = 0.4 and P(B) = 0.5 are independent. Find P(A ∩ B), P(A ∪ B), and P(A | B).",
          ["P(A ∩ B) = 0.4 × 0.5 = 0.2.", "P(A ∪ B) = 0.4 + 0.5 − 0.2 = 0.7.", "P(A | B) = 0.2 ÷ 0.5 = 0.4 = P(A), as expected for independent events."], "**0.2; 0.7; 0.4.**")],
        [N("A fair die is rolled. Find P(an even number or a 5) as a fraction in sixths: enter the numerator.", 4, "2, 4, 6 or 5: 4 outcomes out of 6."),
         N("P(A) = 0.6, P(B) = 0.3, P(A ∩ B) = 0.1. Find P(A ∪ B).", 0.8, "0.6 + 0.3 − 0.1 = 0.8.", tol=0.0001),
         M("Two events are independent when:", "P(A ∩ B) = P(A) × P(B)", ["P(A ∩ B) = 0", "P(A) = P(B)", "P(A ∪ B) = 1"], "Definition.")],
        [N("A box in a shop in Bafoussam holds 4 good and 2 faulty bulbs. Two are taken without replacement. Find P(both good) as a decimal (3 d.p.).", round(4 / 6 * 3 / 5, 3), "4/6 × 3/5 = 12/30 = 0.4.", tol=0.001),
         N("P(B) = 0.5 and P(A ∩ B) = 0.15. Find P(A | B).", 0.3, "0.15 ÷ 0.5 = 0.3.", tol=0.0001)],
        P("In a class of 30 in Garoua, 18 like football (F), 12 like athletics (A) and 6 like both.",
          [pn("Find P(F) as a decimal.", 0.6, "18/30.", tol=0.001, pts=1),
           pn("Find P(F ∪ A) as a decimal.", 0.8, "(18 + 12 − 6)/30 = 24/30.", tol=0.001, pts=2),
           pn("Find P(A | F) as a decimal.", 1 / 3, "P(A ∩ F)/P(F) = 6/18 = 1/3.", tol=0.001, pts=2)]),
        [("P(A) + P(not A) =", "1", ["0", "P(A)²", "2"], "Complement."),
         ("Along a tree path we:", "multiply", ["add", "subtract", "divide"], "AND rule."),
         ("P(at least one) =", "1 − P(none)", ["P(none)", "P(one)", "1 + P(none)"], "Complement."),
         ("Mutually exclusive: P(A ∩ B) =", "0", ["1", "P(A)P(B)", "0.5"], "Cannot occur together."),
         ("Without replacement, the second probability:", "changes", ["stays the same", "is 0", "is 1"], "Fewer items.")],
        minutes=30)

    x = [4, 6, 8, 10, 12]; mu = sum(x) / 5; var = sum((v - mu) ** 2 for v in x) / 5
    assert mu == 8 and var == 8 and abs(math.sqrt(8) - 2.828) < 1e-3
    xs = [2, 3, 4, 5]; fs = [1, 3, 4, 2]; n = sum(fs); m = sum(a * b for a, b in zip(xs, fs)) / n
    v2 = sum(a * a * b for a, b in zip(xs, fs)) / n - m * m; assert (n, m) == (10, 3.7) and abs(v2 - 0.81) < 1e-9 and abs(math.sqrt(v2) - 0.9) < 1e-9
    L = lesson(ch, "statistics-fm", "Variance and standard deviation",
        ["Calculate the variance and standard deviation of raw data and of a frequency table.", "Describe the effect of adding to or multiplying the data.", "Compare two sets of data using mean and standard deviation."],
        [("formule", "Variance and standard deviation", "For n values with mean x̄ (and, for a table, frequencies f with Σf = n):", "\\sigma^2 = \\frac{\\sum f(x - \\bar{x})^2}{\\sum f} = \\frac{\\sum fx^2}{\\sum f} - \\bar{x}^2 \\qquad \\sigma = \\sqrt{\\sigma^2}"),
         ("propriete", "Effect of changing the data", "If every value is increased by k, the mean increases by k but the standard deviation is **unchanged**. If every value is multiplied by k, the mean and the standard deviation are both multiplied by k (the variance by k²)."),
         ("methode", "Comparing", "The data set with the **smaller standard deviation** is more consistent (less spread). Compare the means first, then the spread: 'similar mean but smaller σ means more consistent'."),
         ("pieges", "Common mistakes", "- Forgetting to square root: the variance is in squared units.\n- Subtracting the mean squared from Σx² without dividing by n first: it is Σfx²/Σf − x̄².\n- Using σ when the question gives the sample (n − 1) version: check which one the course uses.")],
        [("Example 1: raw data", "Find the mean, variance and standard deviation of 4, 6, 8, 10, 12.",
          ["Mean = 40 ÷ 5 = 8.", "Deviations squared: 16, 4, 0, 4, 16, total 40; variance = 40 ÷ 5 = 8.", "Standard deviation = √8 ≈ 2.83."], "**Mean 8; variance 8; σ ≈ 2.83.**", bars([("4", 1), ("6", 1), ("8", 1), ("10", 1), ("12", 1)])),
         ("Example 2: a table", "x = 2, 3, 4, 5 with frequencies 1, 3, 4, 2. Find the mean and standard deviation. If the data are doubled and 5 added, find the new mean and σ.",
          ["Σf = 10; Σfx = 2 + 9 + 16 + 10 = 37, so the mean = 3.7. Σfx² = 4 + 27 + 64 + 50 = 145.", "Variance = 14.5 − 3.7² = 14.5 − 13.69 = 0.81, so σ = 0.9.", "New mean = 2 × 3.7 + 5 = 12.4 and new σ = 2 × 0.9 = 1.8."], "**Mean 3.7; σ = 0.9; new mean 12.4; new σ = 1.8.**")],
        [N("Find the variance of 2, 4, 6.", 8 / 3, "Mean 4; (4 + 0 + 4)/3 = 8/3 ≈ 2.67.", tol=0.01),
         N("The mean of a data set is 20 and σ = 3. Each value is increased by 10. Find the new σ.", 3, "Adding a constant does not change the spread."),
         M("The data set with the smaller standard deviation is:", "more consistent", ["larger", "wrong", "always higher"], "Less spread.")],
        [N("The data are multiplied by 3. The old σ was 1.5. Find the new variance.", 20.25, "New σ = 4.5; variance 20.25."),
         N("Two classes: Class A has mean 60 and σ = 5, class B has mean 60 and σ = 12. Which class is more consistent? Enter the σ of that class.", 5, "Smaller σ: class A.")],
        P("Marks of 5 pupils in a test at a school in Kumba: 12, 14, 15, 17, 17.",
          [pn("Find the mean.", 15, "75 ÷ 5 = 15.", pts=1),
           pn("Find the variance.", 3.6, "Squared deviations from 15: 9, 1, 0, 4, 4, total 18; variance = 18 ÷ 5 = 3.6.", tol=0.001, pts=2),
           pn("Find the standard deviation to 2 d.p.", round(math.sqrt(3.6), 2), "σ = √3.6 = 1.90.", tol=0.01, pts=2)]),
        [("σ² is called the:", "variance", ["mean", "range", "mode"], "Definition."),
         ("σ is the square root of the:", "variance", ["mean", "range", "total"], "Definition."),
         ("Adding 5 to all data changes σ by:", "0", ["5", "25", "√5"], "Spread unchanged."),
         ("Multiplying all data by 2 makes σ:", "2σ", ["σ + 2", "4σ", "σ"], "Scales."),
         ("Σfx²/Σf − x̄² equals:", "the variance", ["the mean", "σ", "the range"], "Formula.")],
        minutes=30)
