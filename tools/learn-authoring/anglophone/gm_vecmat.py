from _kit import *
import math

def build(p):
    ch = p.chapter("vectors-matrices", "Vectors, matrices and transformations", "O Level Mathematics — Vectors, matrices, transformations (to be checked)")

    # ------------------------------------------------------------- vectors
    a, b = (3, -2), (1, 4)
    v = (2 * a[0] - b[0], 2 * a[1] - b[1]); assert v == (5, -8)
    OA, OB = (2, 3), (6, -1); AB = (OB[0] - OA[0], OB[1] - OA[1]); assert AB == (4, -4)
    assert math.isclose(math.hypot(*AB), 4 * math.sqrt(2)) and (OA[0] + OB[0]) / 2 == 4 and (OA[1] + OB[1]) / 2 == 1
    ox, oy, s = 40, 125, 26
    px = lambda x: ox + x * s; py = lambda y: oy - y * s
    it = axes_fig(300, 200, ox, oy, s, s, (0, 7), (-2, 4))
    it += [LINE(px(0), py(0), px(2), py(3), color="blue", width=3, arrow="end"), LINE(px(0), py(0), px(6), py(-1), color="red", width=3, arrow="end"),
           LINE(px(2), py(3), px(6), py(-1), color="green", width=3, arrow="end"),
           T(px(2) - 14, py(3) - 6, "A", 14), T(px(6) + 14, py(-1) + 4, "B", 14), T(px(1) - 14, py(1.5) - 2, "a", 14, color="blue", bold=True), T(px(3) - 4, py(-0.2) + 22, "b", 14, color="red", bold=True),
           T(px(4) + 18, py(1) - 8, "AB = b − a", 13, color="green", anchor="start")]
    L = lesson(ch, "vectors", "Vectors",
        ["Add, subtract and multiply column vectors by a number.", "Find the magnitude of a vector, and the vector between two points.", "Use position vectors, midpoints and parallel vectors."],
        [("definition", "Column vectors", "A vector has a size (magnitude) and a direction. We write its two components as (x, y): x is the horizontal move and y the vertical move (in print, the two numbers are stacked in a column). Add and subtract component by component; multiplying by k multiplies each component."),
         ("formule", "Magnitude and displacement", "For $\\vec{AB}$ with A and B having position vectors a and b, the vector from A to B, its magnitude, and the midpoint M of AB are:", "\\vec{AB} = b - a \\qquad |\\vec{AB}| = \\sqrt{x^2 + y^2} \\qquad \\vec{OM} = \\frac{1}{2}(a + b)"),
         ("propriete", "Parallel vectors", "Two vectors are **parallel** when one is a multiple of the other: (6, −4) = 2 × (3, −2). If $\\vec{AB} = k\\vec{CD}$ then AB and CD are parallel, and if the lines also share a point, A, B and C are collinear."),
         ("pieges", "Common mistakes", "- $\\vec{AB} = a - b$ is wrong: it is **b − a** (end minus start).\n- $|\\vec{AB}| = x + y$ is wrong: use Pythagoras.\n- Reversing a vector changes its sign: $\\vec{BA} = -\\vec{AB}$.")],
        [("Example 1: operations", "Given a = (3, −2) and b = (1, 4) (written as components), find 2a − b and |a|.",
          ["2a = (6, −4); subtract b: (6 − 1, −4 − 4) = (5, −8).", "$|a| = \\sqrt{3^2 + (-2)^2} = \\sqrt{13} \\approx 3.61$."], "**2a − b = (5, −8); |a| ≈ 3.61.**"),
         ("Example 2: between two points", "A has position vector (2, 3) and B has (6, −1). Find $\\vec{AB}$, its magnitude and the midpoint M of AB.",
          ["$\\vec{AB} = (6 - 2, -1 - 3) = (4, -4)$.", "Magnitude: $\\sqrt{16 + 16} = 4\\sqrt{2} \\approx 5.66$.", "Midpoint M = ((2 + 6)/2, (3 + (−1))/2) = (4, 1)."], "**AB = (4, −4); 4√2 ≈ 5.66; M = (4, 1).**", shapes(it, 300, 200))],
        [N("Find the x-component of 3(2, 5) + (−4, 1).", 2, "(6 − 4, 15 + 1) = (2, 16): x-component 2."),
         N("Find |(5, 12)|.", 13, "√(25 + 144) = 13."),
         M("A = (1, 2) and B = (4, 6). The vector AB is:", "(3, 4)", ["(−3, −4)", "(5, 8)", "(4, 6)"], "End minus start: (4 − 1, 6 − 2).")],
        [M("Which vector is parallel to (2, −3)?", "(−6, 9)", ["(3, 2)", "(6, 9)", "(2, 3)"], "(−6, 9) = −3 × (2, −3)."),
         N("OA = (1, 4), OB = (5, 0). Find the y-component of the midpoint of AB.", 2, "(4 + 0) ÷ 2 = 2.")],
        P("A fishing boat leaves port P and moves by (6, 8) km (east, north), then by (−2, 3) km.",
          [pn("Find the total east component (km).", 4, "6 − 2 = 4.", unit="km", pts=1),
           pn("Find the total north component (km).", 11, "8 + 3 = 11.", unit="km", pts=1),
           pn("Find the straight-line distance from P (to 1 d.p.).", round(math.hypot(4, 11), 1), "√(16 + 121) = √137 = 11.7 km.", tol=0.1, unit="km", pts=2)]),
        [("The magnitude of (3, 4) is:", "5", ["7", "12", "25"], "Pythagoras."),
         ("−(2, −1) =", "(−2, 1)", ["(2, −1)", "(−2, −1)", "(1, −2)"], "Reverse both signs."),
         ("Parallel vectors are:", "multiples of each other", ["equal in length", "perpendicular", "always equal"], "Same direction line."),
         ("AB + BC =", "AC", ["BA", "CB", "AB − BC"], "Triangle law."),
         ("The midpoint of (0, 0) and (6, 4):", "(3, 2)", ["(6, 4)", "(2, 3)", "(12, 8)"], "Half the sum.")],
        minutes=30, notes=["Column vector notation is written in plain text because the app's formula subset has no matrix environment (\\binom is not supported): the notation (x, y) read downwards stands for the column vector. Check how the Cameroon Board writes vectors."])

    # ------------------------------------------------------------- matrices
    A = [[2, 1], [3, 4]]; B = [[1, 0], [2, 5]]
    mul = lambda X, Y: [[sum(X[i][k] * Y[k][j] for k in range(2)) for j in range(2)] for i in range(2)]
    AB_ = mul(A, B); assert AB_ == [[4, 5], [11, 20]]
    det = 2 * 4 - 1 * 3; assert det == 5
    x_, y_ = (4 * 7 - 1 * 18) / 5, (-3 * 7 + 2 * 18) / 5; assert (x_, y_) == (2, 3) and 2 * x_ + y_ == 7 and 3 * x_ + 4 * y_ == 18
    mi1, w1 = mat_items(40, 40, A); mi2, w2 = mat_items(w1 + 40, 40, B); mi3, w3 = mat_items(w2 + 60, 40, AB_)
    it = mi1 + mi2 + mi3 + [T(w1 + 20, 80, "×", 20), T(w2 + 30, 80, "=", 20), T(110, 130, "A", 15), T(w2 - 40, 130, "B", 15), T(w3 - 34, 130, "AB", 15)]
    L = lesson(ch, "matrices", "Matrices (2 × 2)",
        ["Add, subtract and multiply 2 × 2 matrices.", "Find the determinant and inverse of a 2 × 2 matrix.", "Solve simultaneous equations using the inverse matrix."],
        [("definition", "Matrices", "A matrix is a rectangular array of numbers. Here a 2 × 2 matrix (2 rows, 2 columns) is written row by row, rows separated by a slash: A = (a b / c d). Add and subtract corresponding entries. To **multiply**, combine each **row** of the first matrix with each **column** of the second: row × column."),
         ("formule", "Determinant and inverse", "For A = (a b / c d) the determinant is below. If it is not 0, the inverse is (1/det) × (d −b / −c a): swap a and d, change the signs of b and c.", "\\det A = ad - bc"),
         ("propriete", "Useful facts", "The identity matrix I = (1 0 / 0 1) leaves any matrix unchanged: AI = IA = A. A matrix with determinant 0 is **singular** and has no inverse. In general AB ≠ BA (order matters). $AA^{-1} = I$."),
         ("pieges", "Common mistakes", "- Multiplying entry by entry instead of row by column.\n- Swapping the entries of the inverse incorrectly: swap a and d, **change the signs** of b and c.\n- Forgetting to divide by the determinant.")],
        [("Example 1: product and determinant", "A = (2 1 / 3 4) and B = (1 0 / 2 5). Find AB and the determinant of A.",
          ["Row 1 × column 1: 2×1 + 1×2 = 4. Row 1 × column 2: 2×0 + 1×5 = 5.", "Row 2 × column 1: 3×1 + 4×2 = 11. Row 2 × column 2: 3×0 + 4×5 = 20.", "det A = 2×4 − 1×3 = 5."], "**AB = (4 5 / 11 20); det A = 5.**", shapes(it, 340, 150)),
         ("Example 2: simultaneous equations", "Solve 2x + y = 7 and 3x + 4y = 18 with the inverse matrix.",
          ["The matrix of coefficients is A = (2 1 / 3 4) with det 5, so $A^{-1} = \\frac{1}{5}$ × (4 −1 / −3 2).", "x = (4×7 − 1×18)/5 = 10/5 = 2 and y = (−3×7 + 2×18)/5 = 15/5 = 3.", "Check: 2×2 + 3 = 7 and 3×2 + 4×3 = 18."], "**x = 2, y = 3.**")],
        [N("Find the determinant of (3 2 / 1 4).", 10, "3×4 − 2×1 = 10."),
         M("Add (1 2 / 3 4) and (5 0 / −1 2).", "(6 2 / 2 6)", ["(5 0 / −3 8)", "(6 2 / 4 6)", "(4 2 / 4 2)"], "1+5=6, 2+0=2, 3+(−1)=2, 4+2=6."),
         N("Find 2 × (3 −1 / 0 4): enter the bottom right entry.", 8, "2 × 4 = 8.")],
        [N("Find the top-left entry of the product (1 2 / 3 4)(5 6 / 7 8).", 19, "1×5 + 2×7 = 19."),
         M("The inverse of (2 1 / 5 3) is:", "(3 −1 / −5 2)", ["(−3 1 / 5 −2)", "(3 1 / 5 2)", "(2 −1 / −5 3)"], "det = 6 − 5 = 1; swap a and d, change signs of b and c.")],
        P("A cook in Yaoundé sells ndolé plates and puff-puff bags. Daily sales (plates, bags): Monday (3, 2), Tuesday (1, 4). Prices in FCFA (ndolé 500, puff-puff 200).",
          [pn("Find Monday's takings (FCFA).", 3 * 500 + 2 * 200, "Row × column: 3×500 + 2×200 = 1 900.", unit="FCFA", pts=1),
           pn("Find Tuesday's takings.", 1 * 500 + 4 * 200, "1×500 + 4×200 = 1 300.", unit="FCFA", pts=1),
           pn("Find the total for the two days.", 3200, "1 900 + 1 300 = 3 200 FCFA.", unit="FCFA", pts=2)]),
        [("A 2 × 2 matrix has:", "2 rows and 2 columns", ["2 entries", "4 rows", "2 rows and 1 column"], "Order."),
         ("The determinant of (a b / c d) is:", "ad − bc", ["ad + bc", "ab − cd", "ac − bd"], "Standard."),
         ("A singular matrix has determinant:", "0", ["1", "−1", "undefined"], "No inverse."),
         ("The identity matrix is:", "(1 0 / 0 1)", ["(0 1 / 1 0)", "(1 1 / 1 1)", "(0 0 / 0 0)"], "Ones on the diagonal."),
         ("In general:", "AB ≠ BA", ["AB = BA always", "AB = A + B", "AB = 0"], "Order matters.")],
        minutes=30, notes=["Matrices are written in plain text '(a b / c d)' because pmatrix is not in the app's LaTeX subset; the figure of Example 1 shows a drawn matrix product. Check that the Cameroon O Level syllabus includes inverse matrices and simultaneous equations."])
    # ------------------------------------------------------------- transformations
    refl = lambda P_: (-P_[0], P_[1]); rot90 = lambda P_: (-P_[1], P_[0])
    assert refl((3, 1)) == (-3, 1) and rot90((2, 1)) == (-1, 2)
    enl = lambda P_, C, k: (C[0] + k * (P_[0] - C[0]), C[1] + k * (P_[1] - C[1])); assert enl((2, 3), (1, 1), 3) == (4, 7)
    ox, oy, s = 180, 150, 30
    px = lambda x: ox + x * s; py = lambda y: oy - y * s
    it = axes_fig(360, 190, ox, oy, s, s, (-5, 5), (-1, 4))
    it += [POLY([px(1), py(1), px(3), py(1), px(1), py(3)], fill="lightblue"), POLY([px(-1), py(1), px(-3), py(1), px(-1), py(3)], fill="lightorange"),
           T(px(1.5), py(1.4), "T", 14), T(px(-1.6), py(1.4), "T'", 14), T(px(5) - 10, py(0) + 20, "x", 14), T(px(0) + 12, py(4) + 6, "y", 14)]
    L = lesson(ch, "transformations", "Transformations",
        ["Reflect, rotate, translate and enlarge shapes on a coordinate grid.", "Describe a transformation fully.", "Use scale factor for lengths and areas under enlargement."],
        [("definition", "The four transformations", "- **Reflection**: in a mirror line (give the equation of the line).\n- **Rotation**: give the centre, the angle and the direction (clockwise or anticlockwise).\n- **Translation**: give the vector (x, y) of the movement.\n- **Enlargement**: give the centre and the scale factor k."),
         ("propriete", "Useful rules", "Reflection in the y-axis: (x, y) → (−x, y). In the x-axis: (x, y) → (x, −y). In y = x: (x, y) → (y, x). Rotation of 90° anticlockwise about O: (x, y) → (−y, x). Under an enlargement of scale factor k the **lengths** are multiplied by k and the **areas** by k²."),
         ("pieges", "Common mistakes", "- Describing a rotation without the centre or direction (three details are needed).\n- Enlargement: measuring distances from the shape instead of from the **centre**.\n- A negative scale factor puts the image on the opposite side of the centre.")],
        [("Example 1: reflection", "The triangle T has vertices (1, 1), (3, 1) and (1, 3). Find its image T' under reflection in the y-axis.",
          ["Change the sign of every x-coordinate.", "(1, 1) → (−1, 1); (3, 1) → (−3, 1); (1, 3) → (−1, 3)."], "**T' = (−1, 1), (−3, 1), (−1, 3).**", shapes(it, 360, 190)),
         ("Example 2: describe a transformation", "Point P(2, 1) is moved to P'(−1, 2). Show that this is a rotation of 90° anticlockwise about the origin. Then enlarge (2, 3) with centre (1, 1) and scale factor 3.",
          ["Rule for 90° anticlockwise about O: (x, y) → (−y, x), so (2, 1) → (−1, 2). Matches P'.", "Enlargement: vector from the centre to (2, 3) is (1, 2). Multiply by 3: (3, 6).", "Add to the centre: (1 + 3, 1 + 6) = (4, 7)."], "**Rotation 90° anticlockwise about (0, 0); the image of (2, 3) is (4, 7).**")],
        [M("(3, −2) is reflected in the x-axis. The image is:", "(3, 2)", ["(−3, −2)", "(−3, 2)", "(2, 3)"], "Change the sign of y."),
         N("Translate (4, 5) by the vector (−3, 2). Enter the new y-coordinate.", 7, "5 + 2 = 7; the new point is (1, 7)."),
         N("A square of side 4 cm is enlarged with scale factor 3. Find the area of the image.", 144, "Side 12 cm; area 144 cm² (= 16 × 9).", unit="cm²")],
        [N("Reflect (3, 2) in the line y = x and enter the x-coordinate of the image.", 2, "Swap the coordinates: (2, 3)."),
         M("Which is a complete description of a rotation?", "centre, angle and direction", ["angle only", "centre only", "mirror line"], "All three needed.")],
        P("Triangle ABC has A(1, 1), B(3, 1), C(1, 2). It is enlarged with centre the origin and scale factor 2 to give A'B'C'.",
          [pn("Find the x-coordinate of C'.", 2, "(1, 2) → (2, 4).", pts=1),
           pn("Find the length of A'B' (AB = 2).", 4, "Length × 2.", unit="units", pts=1),
           pn("The area of ABC is 1 square unit. Find the area of A'B'C'.", 4, "Area × k² = 4.", unit="square units", pts=2)]),
        [("Reflection in the y-axis maps (x, y) to:", "(−x, y)", ["(x, −y)", "(y, x)", "(−x, −y)"], "Mirror on the y-axis."),
         ("A translation is described by:", "a vector", ["a mirror line", "an angle only", "a scale factor"], "Movement."),
         ("Enlargement with k = 3 multiplies lengths by:", "3", ["9", "6", "1/3"], "Scale factor."),
         ("Rotation by 180° about O maps (x, y) to:", "(−x, −y)", ["(y, x)", "(x, −y)", "(−y, x)"], "Both signs change."),
         ("Reflection in y = x maps (1, 5) to:", "(5, 1)", ["(−1, 5)", "(1, −5)", "(−5, −1)"], "Swap.")],
        minutes=30, notes=["Transformation matrices are not included here: check whether the O Level syllabus asks for them (matrix of a transformation)."])
