from _kit import *
import math

def build(p):
    ch = p.chapter("quadratics", "Quadratic functions and simultaneous equations", "O Level Further Mathematics — Quadratics and equations (to be checked)")
    chk(lambda x: (x - 3) ** 2 - 4, lambda x: x * x - 6 * x + 5)
    chk(lambda x: 2 * (x + 2) ** 2 - 5, lambda x: 2 * x * x + 8 * x + 3)
    assert 2 * 3 ** 2 - 7 * 3 + 3 == 0 and 2 * .5 ** 2 - 7 * .5 + 3 == 0 and 3 + .5 == 7 / 2 and 3 * .5 == 3 / 2 and 36 - 36 == 0
    assert 64 - 4 * 4 ** 2 == 0 and 8 * 8 - 8 * 8 == 0
    L = lesson(ch, "quadratic-functions", "Quadratic functions, discriminant and inequalities",
        ["Complete the square and find the turning point.", "Use the discriminant to decide the nature of the roots.", "Use sum and product of roots; solve quadratic inequalities."],
        [("formule", "Completed square and discriminant", "$ax^2 + bx + c = a(x + p)^2 + q$ with turning point (−p, q). The discriminant decides the roots: Δ > 0 two distinct real roots; Δ = 0 equal roots (the graph touches the x-axis); Δ < 0 no real roots.", "\\Delta = b^2 - 4ac"),
         ("formule", "Sum and product of roots", "If α and β are the roots of $ax^2 + bx + c = 0$ then:", "\\alpha + \\beta = -\\frac{b}{a} \\qquad \\alpha\\beta = \\frac{c}{a}"),
         ("methode", "Inequalities", "To solve $ax^2 + bx + c > 0$ or < 0: find the roots, sketch the parabola, and read the region above the axis (> 0) or below it (< 0). For a > 0: > 0 means **outside** the roots, < 0 means **between** them."),
         ("pieges", "Common mistakes", "- Writing $(x + 3)^2 - 4$ for $x^2 - 6x + 5$: the sign inside the bracket is half of b with its sign changed.\n- Using Δ < 0 as 'one root'.\n- Writing an 'outside the roots' answer as a single inequality $2 > x > 3$.")],
        [("Example 1: completing the square", "Write $x^2 - 6x + 5$ in the form $(x - p)^2 + q$. State the minimum value, and solve $x^2 - 5x + 6 > 0$.",
          ["$x^2 - 6x + 5 = (x - 3)^2 - 9 + 5 = (x - 3)^2 - 4$. Minimum value −4 at x = 3.", "$x^2 - 5x + 6 = (x - 2)(x - 3)$; roots 2 and 3.", "The parabola is above the x-axis outside the roots: x < 2 or x > 3."], "**(x − 3)² − 4; minimum −4; x < 2 or x > 3.**",
          plot(-1, 7, -5, 8, curves=[("(x-3)^2-4", "blue", "y = (x − 3)² − 4")], points=[(3, -4, "min", "red"), (1, 0, None, "green"), (5, 0, None, "green")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: discriminant and roots", "(a) Find the values of k for which $x^2 + kx + 9 = 0$ has equal roots. (b) The roots of $2x^2 - 7x + 3 = 0$ are α and β. Find α + β and αβ, and the equation with roots 2α and 2β.",
          ["(a) Δ = k² − 36 = 0, so k = 6 or k = −6.", "(b) α + β = 7/2 and αβ = 3/2.", "New roots: sum 2(α + β) = 7, product 4αβ = 6, so $x^2 - 7x + 6 = 0$ (roots 1 and 6)."], "**k = ±6; 7/2 and 3/2; x² − 7x + 6 = 0.**")],
        [N("Find the discriminant of $x^2 - 4x + 5$.", -4, "16 − 20 = −4: no real roots."),
         M("The minimum value of $x^2 + 4x + 7$ is:", "3", ["7", "−3", "−2"], "(x + 2)² + 3."),
         N("The roots of $x^2 - 9x + 20 = 0$ add up to:", 9, "−b/a = 9.")],
        [N("For which positive value of k does $kx^2 + 8x + 2 = 0$ have equal roots?", 8, "Δ = 64 − 8k = 0, so k = 8."),
         M("Solve $x^2 - 2x - 8 \\leq 0$.", "−2 ≤ x ≤ 4", ["x ≤ −2 or x ≥ 4", "−4 ≤ x ≤ 2", "x ≤ 4"], "Roots −2 and 4; the parabola is below the axis between them.")],
        P("A farmer in Bafoussam fences a rectangular plot against a wall using 40 m of fence. If the width is x m the length is (40 − 2x) m (three sides fenced).",
          [pm("The area A in terms of x is:", "$A = 40x - 2x^2$", ["$A = 40 - 2x$", "$A = 2x^2 - 40x$", "$A = 40x + 2x^2$"], "A = x(40 − 2x).", 1),
           pn("Complete the square: $A = 200 - 2(x - 10)^2$. State the maximum area.", 200, "(x − 10)² ≥ 0, so A ≤ 200 with equality at x = 10.", unit="m²", pts=2),
           pn("What dimensions give the maximum? Enter the length of the plot.", 20, "x = 10, length 40 − 20 = 20 m.", unit="m", pts=2)]),
        [("If Δ = 0 the quadratic has:", "equal roots", ["two distinct roots", "no real roots", "three roots"], "Touches the axis."),
         ("The sum of the roots of $2x^2 - 6x + 1 = 0$:", "3", ["−3", "1/2", "6"], "−b/a = 6/2."),
         ("$(x + 5)^2 - 25$ expands to:", "$x^2 + 10x$", ["$x^2 + 25$", "$x^2 - 10x$", "$x^2 + 5x$"], "Expand."),
         ("If a > 0, f(x) < 0 holds:", "between the roots", ["outside the roots", "only at the roots", "never"], "Below the axis."),
         ("Δ < 0 means the graph:", "does not cross the x-axis", ["touches the axis", "crosses twice", "is a line"], "No real roots.")],
        minutes=30)

    assert 6 * 8 == 48 and 6 + 8 == 14 and 6 ** 2 + 8 ** 2 == 10 ** 2 and 3 ** 2 - 2 == 2 * 3 + 1
    assert 3 ** 2 + 4 ** 2 == 25 and (-4) ** 2 + (-3) ** 2 == 25 and (3, 4)[1] == 3 + 1 and -3 == -4 + 1
    fig = plot(-6, 6, -6, 6, curves=[("sqrt(25-x^2)", "blue"), ("-sqrt(25-x^2)", "blue"), ("x+1", "red", "y = x + 1")], points=[(3, 4, "(3, 4)", "green"), (-4, -3, "(−4, −3)", "green")], grid=1, xlabel="x", ylabel="y", w=360, h=300)
    fig["curves"][0].update({"from": -5, "to": 5}); fig["curves"][1].update({"from": -5, "to": 5}); fig["curves"][2].update({"from": -5.5, "to": 5})
    L = lesson(ch, "simultaneous", "Simultaneous equations: linear and quadratic",
        ["Solve a linear equation with a quadratic equation by substitution.", "Interpret the solutions as intersection points.", "Use the discriminant to decide whether a line cuts, touches or misses a curve."],
        [("methode", "Method", "1. Make one letter the subject of the **linear** equation (e.g. y = x + 1).\n2. Substitute into the quadratic equation to get a quadratic in one letter.\n3. Solve it (factorise or formula).\n4. Substitute each value into the **linear** equation to find the matching value of the other letter."),
         ("propriete", "Line and curve", "For a line meeting a curve, the substitution gives a quadratic in x. Its discriminant tells how many intersections: Δ > 0 two points (the line cuts the curve), Δ = 0 one point (the line is a **tangent**), Δ < 0 none."),
         ("pieges", "Common mistakes", "- Finding the x-values but forgetting the y-values: each solution is a **pair**.\n- Substituting back into the quadratic and creating wrong pairs: use the linear equation.\n- Expanding $(x + 1)^2$ as $x^2 + 1$.")],
        [("Example 1: a line and a circle", "Solve y = x + 1 and x² + y² = 25.",
          ["Substitute: $x^2 + (x + 1)^2 = 25$, so $2x^2 + 2x - 24 = 0$ and $x^2 + x - 12 = 0$.", "$(x + 4)(x - 3) = 0$, so x = −4 or x = 3.", "y = x + 1 gives y = −3 and y = 4. Check (3, 4): 9 + 16 = 25."], "**(3, 4) and (−4, −3).**", fig),
         ("Example 2: tangent condition", "For which value of c is the line y = 2x + c a tangent to the curve y = x²? Also solve x + y = 7 and xy = 12.",
          ["Equate: $x^2 = 2x + c$, i.e. $x^2 - 2x - c = 0$. Tangent: Δ = 4 + 4c = 0, so c = −1.", "From x + y = 7: y = 7 − x. Then x(7 − x) = 12, so $x^2 - 7x + 12 = 0$.", "x = 3 (y = 4) or x = 4 (y = 3)."], "**c = −1; (3, 4) and (4, 3).**")],
        [N("Solve y = x + 2 and y = x²: the larger value of x.", 2, "x² = x + 2: (x − 2)(x + 1) = 0, so x = 2 or −1."),
         N("x + y = 5 and xy = 6. Find the larger of x and y.", 3, "Numbers with sum 5 and product 6: 2 and 3."),
         M("The line y = 3 meets $y = x^2 - 1$ at x =", "±2", ["±3", "±4", "2 only"], "x² = 4.")],
        [N("The line y = x + k touches the circle x² + y² = 8. Find the positive value of k.", 4, "Substitute: 2x² + 2kx + k² − 8 = 0; Δ = 4k² − 8(k² − 8) = 64 − 4k² = 0, so k = 4."),
         N("Solve y = 2x + 1 and y = x² − 2 and give the larger value of x.", 3, "x² − 2 = 2x + 1, so x² − 2x − 3 = (x − 3)(x + 1) = 0; x = 3 or −1. Check x = 3: y = 7 and 9 − 2 = 7.")],
        P("A rectangle in a Yaoundé school yard has perimeter 28 m and area 48 m².",
          [pm("If the length is x and width y then x + y = 14 and:", "xy = 48", ["x + y = 48", "xy = 28", "x − y = 48"], "Area = length × width.", 1),
           pn("Find the length (the larger side).", 8, "y = 14 − x: x(14 − x) = 48 → x² − 14x + 48 = (x − 6)(x − 8) = 0.", unit="m", pts=2),
           pn("Find the diagonal in metres.", 10, "√(8² + 6²) = 10.", unit="m", pts=2)]),
        [("The solution of two simultaneous equations is:", "a pair (x, y)", ["one number", "an equation", "a curve"], "Intersection."),
         ("Δ = 0 for line and curve means:", "tangent", ["two intersections", "no intersection", "parallel"], "One point."),
         ("To eliminate y from y = x + 1 and x² + y² = 25:", "substitute y", ["add them", "multiply them", "divide them"], "Substitution."),
         ("Intersection points of a line and a circle can be at most:", "2", ["1", "3", "4"], "Quadratic."),
         ("If x = 3 and y = x + 1 then y =", "4", ["2", "3", "1"], "Linear equation.")],
        minutes=30)

    # ================================================================ binomial & progressions
    ch = p.chapter("series", "Binomial expansion and progressions", "O Level Further Mathematics — Binomial theorem; arithmetic and geometric progressions (to be checked)")
    C = math.comb
    def expand(a, b, n): return [C(n, k) * a ** (n - k) * b ** k for k in range(n + 1)]
    assert expand(2, 1, 4) == [16, 32, 24, 8, 1] and C(6, 3) * 2 ** 3 == 160 and C(6, 3) == 20
    assert expand(3, -2, 5)[2] == 1080 and abs(sum(expand(1, 0.02, 5)[:4]) - 1.10408) < 1e-9 and abs(1.02 ** 5 - 1.10408) < 1e-4
    rows = [[C(r, k) for k in range(r + 1)] for r in range(6)]
    it = []
    for r, row in enumerate(rows):
        for k, v in enumerate(row): it.append(T(200 + (k - r / 2) * 40, 30 + r * 30, str(v), 16, bold=(r == 4)))
        it.append(T(40, 30 + r * 30, "n=%d" % r, 13))
    L = lesson(ch, "binomial", "The binomial expansion",
        ["Expand $(a + b)^n$ for a positive integer n using Pascal's triangle and combinations.", "Find a required term or coefficient.", "Use the first terms to approximate powers."],
        [("formule", "Binomial theorem", "For a positive integer n, where C(n, r) = n! ÷ (r!(n − r)!) is the number of ways of choosing r from n (also the entries of Pascal's triangle):", "(a + b)^n = a^n + \\frac{n!}{1!(n-1)!}a^{n-1}b + \\frac{n!}{2!(n-2)!}a^{n-2}b^2 + \\cdots + b^n"),
         ("propriete", "General term", "The term in $b^r$ is C(n, r) × $a^{n-r}$ × $b^r$, which is term number r + 1. The coefficients are symmetric: C(n, r) = C(n, n − r). For approximations, $(1 + x)^n \\approx 1 + nx + \\frac{n(n-1)}{2}x^2$ when x is small."),
         ("pieges", "Common mistakes", "- Forgetting the powers of the number: in $(3 - 2x)^5$ the coefficient includes $3^{5-r}$ and $(-2)^r$.\n- Losing the minus sign when b is negative: signs alternate.\n- Counting terms: there are n + 1 terms in the expansion of $(a + b)^n$.")],
        [("Example 1: full expansion", "Expand $(2 + x)^4$ and hence find the coefficient of x³ in $(1 + 2x)^6$.",
          ["Row 4 of Pascal: 1, 4, 6, 4, 1. So $(2 + x)^4 = 16 + 4(8)x + 6(4)x^2 + 4(2)x^3 + x^4 = 16 + 32x + 24x^2 + 8x^3 + x^4$.", "In $(1 + 2x)^6$ the x³ term is C(6, 3) × 1³ × (2x)³ = 20 × 8x³.", "The coefficient is 160."], "**16 + 32x + 24x² + 8x³ + x⁴; coefficient 160.**", shapes(it, 400, 190)),
         ("Example 2: coefficient and approximation", "Find the coefficient of x² in $(3 - 2x)^5$, and use the first three terms of $(1 + 0.02)^5$ to estimate 1.02⁵.",
          ["Term in x²: C(5, 2) × 3³ × (−2x)² = 10 × 27 × 4x² = 1 080x².", "$(1 + 0.02)^5 \\approx 1 + 5(0.02) + 10(0.02)^2 = 1 + 0.1 + 0.004$.", "Estimate: 1.104 (exact 1.10408)."], "**1 080; 1.104.**")],
        [N("Find C(6, 2).", 15, "6! ÷ (2! 4!) = 15."),
         M("The expansion of $(1 + x)^5$ has coefficients:", "1, 5, 10, 10, 5, 1", ["1, 5, 5, 10, 10, 1", "1, 4, 6, 4, 1", "5, 10, 10, 5"], "Row 5 of Pascal's triangle."),
         N("Find the coefficient of x² in $(1 + x)^7$.", 21, "C(7, 2) = 21.")],
        [N("Find the term independent of x in $\\left(x + \\frac{1}{x}\\right)^6$.", 20, "The term with x³ × x⁻³: C(6, 3) = 20."),
         N("Find the coefficient of x³ in $(2 - x)^5$.", -40, "C(5, 3) × 2² × (−1)³ = 10 × 4 × (−1) = −40.")],
        P("The expression $(1 + 3x)^4$ is expanded in ascending powers of x.",
          [pm("The first three terms are:", "$1 + 12x + 54x^2$", ["$1 + 4x + 6x^2$", "$1 + 12x + 27x^2$", "$1 + 3x + 9x^2$"], "C(4,1) × 3 = 12; C(4,2) × 9 = 54.", 2),
           pn("Find the coefficient of x³.", 108, "C(4, 3) × 27 = 4 × 27 = 108.", pts=1),
           pn("Use the first three terms with x = 0.01 to estimate $1.03^4$ to 4 decimal places.", 1.1254, "1 + 0.12 + 0.0054 = 1.1254 (exact 1.12551).", tol=0.001, pts=2)]),
        [("The expansion of $(a + b)^n$ has how many terms?", "n + 1", ["n", "n − 1", "2n"], "From $a^n$ to $b^n$."),
         ("C(5, 0) =", "1", ["0", "5", "120"], "Only one way."),
         ("C(8, 2) = C(8, ?)", "6", ["4", "2", "8"], "C(n, r) = C(n, n − r)."),
         ("The 2nd term of $(1 + x)^6$ is:", "6x", ["x", "15x²", "6"], "C(6,1)x."),
         ("In $(2 + x)^3$ the constant term is:", "8", ["2", "6", "12"], "2³.")],
        minutes=30)

    assert 3 + 4 * 7 == 31 and 3 + 2 * 4 == 11 and 10 * (2 * 3 + 19 * 4) == 820
    assert 5 * 2 ** 7 == 640 and 5 * (2 ** 8 - 1) == 1275 and abs(12 / (1 - 1 / 3) - 18) < 1e-9 and abs(0.4 / 0.9 - 4 / 9) < 1e-12
    L = lesson(ch, "progressions", "Arithmetic and geometric progressions",
        ["Use the nth term and the sum formulae of arithmetic and geometric progressions.", "Find the sum to infinity of a convergent geometric progression.", "Solve problems with savings, growth and recurring decimals."],
        [("formule", "Arithmetic progression (AP)", "First term a, common difference d:", "T_n = a + (n-1)d \\qquad S_n = \\frac{n}{2}\\left[2a + (n-1)d\\right]"),
         ("formule", "Geometric progression (GP)", "First term a, common ratio r. The sum to infinity exists only when −1 < r < 1:", "T_n = ar^{n-1} \\qquad S_n = \\frac{a(r^n - 1)}{r - 1} \\qquad S_\\infty = \\frac{a}{1 - r}"),
         ("methode", "Method", "Translate the words into equations for a and d (or a and r), solve, then apply the formula. For three numbers in AP use a − d, a, a + d; in GP use a/r, a, ar. A recurring decimal such as 0.444… is a GP with a = 0.4 and r = 0.1."),
         ("pieges", "Common mistakes", "- Using $S_\\infty$ when |r| ≥ 1.\n- In the GP sum formula the exponent is n, not n − 1: $S_n$ uses $r^n$, while $T_n$ uses $r^{n-1}$.\n- Mixing T_n (a single term) with S_n (a sum).")],
        [("Example 1: AP", "The 3rd term of an AP is 11 and the 8th term is 31. Find a, d and the sum of the first 20 terms.",
          ["a + 2d = 11 and a + 7d = 31. Subtract: 5d = 20, so d = 4 and a = 3.", "$S_{20} = \\frac{20}{2}[6 + 19 \\times 4] = 10 \\times 82 = 820$.", "Check T₃ = 3 + 8 = 11."], "**a = 3, d = 4, S₂₀ = 820.**", bars([("T1", 12), ("T2", 4), ("T3", 1.33), ("T4", 0.44), ("T5", 0.15)])),
         ("Example 2: GP", "Find the 8th term and the sum of the first 8 terms of 5, 10, 20, …; then the sum to infinity of 12, 4, 4/3, … and 0.444… as a fraction.",
          ["a = 5, r = 2: T₈ = 5 × 2⁷ = 640 and S₈ = 5(2⁸ − 1)/(2 − 1) = 1 275.", "12, 4, 4/3: r = 1/3 so S∞ = 12 ÷ (1 − 1/3) = 18.", "0.444… = 0.4 + 0.04 + …: a = 0.4, r = 0.1, S∞ = 0.4 ÷ 0.9 = 4/9."], "**640; 1 275; 18; 4/9.**")],
        [N("Find the 15th term of the AP 4, 9, 14, …", 74, "4 + 14 × 5 = 74."),
         N("Find the sum of the GP 2, 6, 18, 54, 162.", 242, "S₅ = 2(3⁵ − 1)/2 = 242."),
         M("Which GP has a sum to infinity?", "8, 4, 2, 1, …", ["2, 4, 8, 16, …", "1, 1, 1, …", "3, −6, 12, …"], "Only |r| < 1: r = 1/2 for the first option.")],
        [N("The sum of the first n terms of an AP is 3n² + n. Find the first term.", 4, "S₁ = 3 + 1 = 4."),
         N("The numbers x − 2, x + 1 and 3x − 3 are the first three terms of a GP with common ratio 2. Find x.", 5, "x + 1 = 2(x − 2) gives x = 5. Terms: 3, 6, 12, and 3x − 3 = 12 as required.")],
        P("Ngwa saves 2 000 FCFA in January and increases the amount saved by 500 FCFA each month.",
          [pn("How much does he save in December (month 12)?", 2000 + 11 * 500, "T₁₂ = 2 000 + 11 × 500 = 7 500 FCFA.", unit="FCFA", pts=1),
           pn("Find the total saved in the year.", 12 * (2 * 2000 + 11 * 500) // 2, "S₁₂ = 6 × (4 000 + 5 500) = 57 000 FCFA.", unit="FCFA", pts=2),
           pn("In which month does he first save at least 5 000 FCFA in the month?", 7, "2 000 + 500(n − 1) ≥ 5 000 gives n ≥ 7: July.", pts=2)]),
        [("In an AP, d is the:", "constant difference", ["constant ratio", "first term", "sum"], "Definition."),
         ("In a GP with a = 3, r = 2, T₃ =", "12", ["18", "9", "6"], "3 × 4."),
         ("S∞ for a = 1, r = 1/2:", "2", ["1", "1.5", "∞"], "1 ÷ 0.5."),
         ("The sum of 1 + 2 + … + 10:", "55", ["50", "100", "45"], "10 × 11 ÷ 2."),
         ("A GP converges when:", "|r| < 1", ["r > 1", "r = 1", "r = 0 only"], "Terms shrink.")],
        minutes=30)
