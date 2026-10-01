from _kit import *
import math

def build(p):
    # ================================================================ sets and logic
    ch = p.chapter("sets-logic", "Sets and logic", "O Level Further/Additional Mathematics — Sets and logic (to be checked)")
    assert 60 - (35 + 28 - 10) == 7
    u3 = 22 + 20 + 18 - 8 - 6 - 7 + 3; assert u3 == 42 and 50 - u3 == 8
    it = [CIRCLE(140, 110, 80, stroke="blue"), CIRCLE(250, 110, 80, stroke="red"), RECT(20, 15, 360, 190, stroke="ink", width=1),
          T(100, 115, "25", 18, bold=True), T(195, 115, "10", 18, bold=True), T(290, 115, "18", 18, bold=True), T(350, 190, "7", 16),
          T(100, 40, "F", 15, color="blue", bold=True), T(290, 40, "V", 15, color="red", bold=True), T(40, 40, "ξ", 15)]
    L = lesson(ch, "sets-logic", "Sets, Venn diagrams and logic",
        ["Use set notation, the counting formula and De Morgan's laws.", "Solve three-set Venn diagram problems.", "Use statements, negation, implication, converse and contrapositive."],
        [("formule", "Counting and De Morgan", "For two sets, and the two laws of De Morgan (the complement of a union is the intersection of complements, and vice versa):", "n(A \\cup B) = n(A) + n(B) - n(A \\cap B) \\qquad (A \\cup B)' = A' \\cap B' \\qquad (A \\cap B)' = A' \\cup B'"),
         ("formule", "Three sets", "The inclusion–exclusion principle for three sets. Always fill a Venn diagram from the **centre outwards** (A ∩ B ∩ C first):", "n(A \\cup B \\cup C) = n(A) + n(B) + n(C) - n(A \\cap B) - n(B \\cap C) - n(A \\cap C) + n(A \\cap B \\cap C)"),
         ("definition", "Logic", "A **statement** is true or false. The negation ¬p reverses the truth value. For p ⇒ q (if p then q): the **converse** is q ⇒ p, the **contrapositive** is ¬q ⇒ ¬p (always equivalent to p ⇒ q), and p ⇒ q is false only when p is true and q is false."),
         ("pieges", "Common mistakes", "- Adding n(A) + n(B) without subtracting the overlap.\n- Believing the converse is equivalent: 'if it rains the road is wet' does not mean 'if the road is wet it rained'.\n- Forgetting the elements outside all the circles (the 'neither' region).")],
        [("Example 1: two sets", "In a class of 60 students, 35 play football (F), 28 play volleyball (V) and 10 play both. How many play neither?",
          ["n(F ∪ V) = 35 + 28 − 10 = 53.", "Neither: 60 − 53 = 7.", "Regions: only F = 25, both = 10, only V = 18, neither = 7: total 60."], "**7 students.**", shapes(it, 400, 220)),
         ("Example 2: three sets", "Of 50 pupils, n(A) = 22, n(B) = 20, n(C) = 18, n(A∩B) = 8, n(B∩C) = 6, n(A∩C) = 7 and n(A∩B∩C) = 3. How many are in none of the sets? Then write the contrapositive of 'if x > 3 then x² > 9'.",
          ["Union = 22 + 20 + 18 − 8 − 6 − 7 + 3 = 42, so none = 50 − 42 = 8.", "Contrapositive: 'if x² ≤ 9 then x ≤ 3'. It is true for x > 3 ⇒ x² > 9."], "**8 pupils; contrapositive: if x² ≤ 9 then x ≤ 3.**")],
        [N("n(A) = 14, n(B) = 11 and n(A ∩ B) = 5. Find n(A ∪ B).", 20, "14 + 11 − 5 = 20."),
         M("(A ∪ B)′ equals:", "A′ ∩ B′", ["A′ ∪ B′", "A ∩ B", "A′ ∩ B"], "De Morgan's law."),
         M("The contrapositive of 'if a number is divisible by 4 then it is even' is:", "if a number is not even then it is not divisible by 4", ["if a number is even then it is divisible by 4", "if a number is not divisible by 4 then it is not even", "a number is even or divisible by 4"], "Negate and swap both parts. The first wrong option is the converse, the second the inverse.")],
        [N("In a survey of 100 people in Buea, 60 like plantain, 50 like cassava and 15 like neither. How many like both?", 25, "n(P ∪ C) = 85, so both = 60 + 50 − 85 = 25."),
         M("p is true and q is false. The statement p ⇒ q is:", "false", ["true", "neither", "depends on r"], "An implication is false only when the premise is true and the conclusion false.")],
        P("In a school of 80 students in Bamenda every student studies at least one of Physics (P), Chemistry (C) and Biology (B). n(P) = 45, n(C) = 40, n(B) = 30, n(P∩C) = 15, n(C∩B) = 12 and n(P∩B) = 10.",
          [pn("Find n(P ∩ C ∩ B).", 2, "80 = 45 + 40 + 30 − 15 − 12 − 10 + x = 78 + x, so x = 2.", pts=2),
           pn("How many study Physics only?", 22, "n(P) − n(P∩C) − n(P∩B) + n(P∩C∩B) = 45 − 15 − 10 + 2 = 22.", pts=1),
           pn("How many study exactly two of the three subjects?", 31, "(15 − 2) + (12 − 2) + (10 − 2) = 13 + 10 + 8 = 31.", pts=2)]),
        [("n(A ∪ B) = n(A) + n(B) −", "n(A ∩ B)", ["n(A ∪ B)", "n(A′)", "0"], "Overlap counted twice."),
         ("¬(¬p) is:", "p", ["¬p", "false", "p ⇒ q"], "Double negation."),
         ("The converse of p ⇒ q:", "q ⇒ p", ["¬p ⇒ ¬q", "¬q ⇒ ¬p", "p ∧ q"], "Swap."),
         ("A ∩ A′ =", "∅", ["A", "ξ", "A′"], "Nothing is in both."),
         ("A ∪ A′ =", "ξ", ["∅", "A", "A′"], "Everything.")],
        minutes=30, notes=["The O Level further-mathematics sets/logic syllabus may be lighter than this lesson (logic may be absent): to be checked."])

    # ================================================================ indices & surds, logarithms
    ch = p.chapter("indices-logs", "Indices, surds and logarithms", "O Level Further Mathematics — Indices, surds, logarithms (to be checked)")
    chk(lambda x: (3 + math.sqrt(5)) / 4, lambda x: 1 / (3 - math.sqrt(5)))
    assert math.isclose((2 + math.sqrt(3)) / (2 - math.sqrt(3)), 7 + 4 * math.sqrt(3))
    assert math.isclose((math.sqrt(3) + math.sqrt(2)) ** 2, 5 + 2 * math.sqrt(6))
    assert [x for x in (0, 1) if 9 ** x - 4 * 3 ** x + 3 == 0] == [0, 1]
    L = lesson(ch, "indices-surds", "Indices, surds and exponential equations",
        ["Solve equations with unknown indices.", "Rationalise denominators with surds and find unknown rational numbers.", "Use the quadratic-in-$a^x$ trick."],
        [("propriete", "Equal bases", "If $a^x = a^y$ (a > 0, a ≠ 1) then x = y. So write both sides with the **same base**: $4^x = 8$ becomes $2^{2x} = 2^3$ and x = 3/2. When the bases cannot match, take logarithms."),
         ("methode", "Rationalising and equating", "Multiply top and bottom by the **conjugate**: for $\\frac{1}{a - \\sqrt{b}}$ multiply by $a + \\sqrt{b}$, using $(a - \\sqrt{b})(a + \\sqrt{b}) = a^2 - b$. If $p + q\\sqrt{3} = r + s\\sqrt{3}$ with rational p, q, r, s then p = r and q = s."),
         ("pieges", "Common mistakes", "- Adding the indices when bases are different.\n- Multiplying by the same surd instead of the conjugate: $\\frac{1}{3 - \\sqrt{5}} \\times \\frac{\\sqrt{5}}{\\sqrt{5}}$ does not remove the root.\n- Forgetting that $3^x > 0$: negative solutions for $3^x$ are rejected.")],
        [("Example 1: exponential equations", "Solve (a) $2^{x+1} = 32$ (b) $9^x - 4 \\times 3^x + 3 = 0$.",
          ["(a) 32 = 2⁵, so x + 1 = 5 and x = 4.", "(b) Put u = 3ˣ, so 9ˣ = u². Then u² − 4u + 3 = 0, (u − 1)(u − 3) = 0, so u = 1 or 3.", "3ˣ = 1 gives x = 0; 3ˣ = 3 gives x = 1."], "**(a) x = 4; (b) x = 0 or x = 1.**",
          plot(-2, 4, 0, 18, curves=[("2^x", "blue", "y = 2ˣ")], points=[(4, 16, None, "red")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: surds", "Express $\\frac{1}{3 - \\sqrt{5}}$ with a rational denominator, and find rational a and b with $\\frac{2 + \\sqrt{3}}{2 - \\sqrt{3}} = a + b\\sqrt{3}$.",
          ["Multiply by the conjugate: $\\frac{3 + \\sqrt{5}}{9 - 5} = \\frac{3 + \\sqrt{5}}{4}$.", "$\\frac{(2 + \\sqrt{3})^2}{4 - 3} = 4 + 4\\sqrt{3} + 3 = 7 + 4\\sqrt{3}$.", "So a = 7 and b = 4."], "**$\\frac{3 + \\sqrt{5}}{4}$; a = 7, b = 4.**")],
        [N("Solve $3^{2x} = 81$.", 2, "81 = 3⁴, so 2x = 4 and x = 2."),
         N("Solve $4^x = 8$.", 1.5, "2²ˣ = 2³ gives x = 3/2.", tol=0.001),
         M("$\\frac{2}{\\sqrt{7} - 2}$ with a rational denominator is:", "$\\frac{2(\\sqrt{7} + 2)}{3}$", ["$\\frac{2(\\sqrt{7} - 2)}{3}$", "$\\frac{2\\sqrt{7}}{5}$", "$\\sqrt{7} + 2$"], "Multiply by √7 + 2; the denominator is 7 − 4 = 3.")],
        [N("Solve $2^{2x} - 6 \\times 2^x + 8 = 0$ and give the larger solution.", 2, "u = 2ˣ: u² − 6u + 8 = (u − 2)(u − 4) = 0, so x = 1 or x = 2."),
         N("Simplify $(\\sqrt{5} + \\sqrt{2})(\\sqrt{5} - \\sqrt{2})$.", 3, "Difference of squares: 5 − 2 = 3.")],
        P("The population of bacteria in a lab sample doubles every hour: N = 100 × 2ᵗ after t hours.",
          [pn("Find N when t = 5.", 3200, "100 × 32 = 3 200.", pts=1),
           pn("After how many hours does N reach 6 400?", 6, "2ᵗ = 64 = 2⁶, so t = 6.", unit="h", pts=2),
           pn("Find N when t = 0.5 to the nearest whole number.", round(100 * 2 ** 0.5), "100 × √2 = 141.4, so 141.", tol=1, pts=1)]),
        [("$a^0 =$", "1", ["0", "a", "undefined"], "For a ≠ 0."),
         ("$2^x = 2^5$ means x =", "5", ["25", "10", "32"], "Equal bases."),
         ("The conjugate of $3 + \\sqrt{2}$ is:", "$3 - \\sqrt{2}$", ["$-3 - \\sqrt{2}$", "$\\sqrt{2} - 3$", "$3\\sqrt{2}$"], "Change the sign between."),
         ("$8^{1/3} =$", "2", ["8/3", "24", "512"], "Cube root."),
         ("$3^x = -9$ has:", "no real solution", ["x = 2", "x = −2", "x = 3"], "Powers of 3 are positive.")],
        minutes=30)

    g = math.log10
    assert abs(math.log(7, 2) - 2.8074) < 1e-4 and 4 * (4 - 2) == 8
    assert abs(math.log10(5000) - 3.69897) < 1e-5
    L = lesson(ch, "logarithms", "Logarithms",
        ["State and use the laws of logarithms.", "Solve equations involving logarithms and exponentials.", "Change the base of a logarithm."],
        [("definition", "Definition", "$\\log_a b = c$ means $a^c = b$ (a > 0, a ≠ 1, b > 0). Examples: $\\log_2 8 = 3$ because $2^3 = 8$; $\\log 1000 = 3$ (log with no base means base 10); $\\log_a 1 = 0$ and $\\log_a a = 1$."),
         ("propriete", "Laws of logarithms", "- $\\log_a(xy) = \\log_a x + \\log_a y$\n- $\\log_a\\left(\\frac{x}{y}\\right) = \\log_a x - \\log_a y$\n- $\\log_a(x^n) = n\\log_a x$\n- Change of base: $\\log_a b = \\frac{\\log b}{\\log a}$"),
         ("methode", "Method: solving", "To solve $a^x = b$, take logs of both sides: $x = \\frac{\\log b}{\\log a}$. For an equation with logs, combine them into **one** log, convert to an index equation, solve, then **reject** values that make a log argument zero or negative."),
         ("pieges", "Common mistakes", "- $\\log(x + y) \\neq \\log x + \\log y$.\n- $\\frac{\\log x}{\\log y} \\neq \\log x - \\log y$.\n- Forgetting to check the solutions: the argument of a log must be positive.")],
        [("Example 1: an exponential equation", "Solve $2^x = 7$ to 3 significant figures, and find $\\log_2 8 + \\log_2 4$.",
          ["Take logs: $x \\log 2 = \\log 7$, so $x = \\frac{\\log 7}{\\log 2} = \\frac{0.8451}{0.3010} \\approx 2.81$.", "$\\log_2 8 + \\log_2 4 = 3 + 2 = 5$, or $\\log_2 32 = 5$."], "**x ≈ 2.81; the sum is 5.**",
          plot(0.1, 10, -1, 3, curves=[("log(x)", "blue", "y = log x")], points=[(1, 0, None, "red"), (10, 1, None, "red")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: a logarithm equation", "Solve $\\log_2 x + \\log_2(x - 2) = 3$.",
          ["Combine: $\\log_2[x(x - 2)] = 3$, so $x(x - 2) = 2^3 = 8$.", "$x^2 - 2x - 8 = 0$, $(x - 4)(x + 2) = 0$, so x = 4 or x = −2.", "x = −2 makes $\\log_2 x$ undefined: reject. Check x = 4: log₂4 + log₂2 = 2 + 1 = 3."], "**x = 4.**")],
        [M("$\\log_3 81 =$", "4", ["27", "3", "81/3"], "3⁴ = 81."),
         N("Evaluate $\\log 2 + \\log 5$.", 1, "log(2 × 5) = log 10 = 1."),
         N("Solve $10^x = 5000$ to 2 decimal places.", round(g(5000), 2), "x = log 5000 = 3.70.", tol=0.01)],
        [N("Solve $3^x = 20$ to 2 decimal places.", round(math.log(20, 3), 2), "x = log 20 ÷ log 3 = 1.3010 ÷ 0.4771 = 2.73.", tol=0.01),
         N("Solve $\\log_5(x + 4) = 2$.", 21, "x + 4 = 25, so x = 21.")],
        P("A bank account in Douala grows by 5% a year: the balance is $B = 200\\,000 \\times 1.05^n$ FCFA after n years.",
          [pn("Find the balance after 4 years (nearest FCFA).", round(200000 * 1.05 ** 4), "200 000 × 1.2155 = 243 101 FCFA.", tol=1, unit="FCFA", pts=1),
           pn("After how many whole years does the balance first exceed 300 000 FCFA?", 9, "1.05ⁿ > 1.5: n > log 1.5 ÷ log 1.05 = 8.31, so n = 9 years.", unit="years", pts=3),
           pn("Check: balance after 9 years (nearest 1 000).", round(200000 * 1.05 ** 9, -3), "200 000 × 1.5513 = 310 266, about 310 000 FCFA.", tol=500, unit="FCFA", pts=1)]),
        [("$\\log_a 1 =$", "0", ["1", "a", "undefined"], "a⁰ = 1."),
         ("$\\log 100 =$", "2", ["10", "100", "1/2"], "10² = 100."),
         ("$\\log x^3 =$", "3 log x", ["(log x)³", "log 3x", "x log 3"], "Power law."),
         ("$\\log_a x - \\log_a y =$", "$\\log_a(x/y)$", ["$\\log_a(x - y)$", "$\\log_a(xy)$", "$\\frac{\\log x}{\\log y}$"], "Quotient law."),
         ("$\\log_2 16 =$", "4", ["8", "2", "32"], "2⁴ = 16.")],
        minutes=30, notes=["Natural logarithms (ln, base e) may also appear; the lesson uses base 10 and general bases only."])

    # ================================================================ polynomials, partial fractions
    ch = p.chapter("polynomials", "Polynomials and partial fractions", "O Level Further Mathematics — Polynomials (to be checked)")
    chk(lambda x: (x - 1) * (x + 2) * (x - 3), lambda x: x ** 3 - 2 * x ** 2 - 5 * x + 6)
    chk(lambda x: (x - 2) * (x + 2) * (x + 3), lambda x: x ** 3 + 3 * x ** 2 - 4 * x - 12)
    P_ = lambda x: 2 * x ** 3 - 3 * x ** 2 + x - 5; assert P_(2) == 1
    L = lesson(ch, "polynomials", "Polynomials: remainder and factor theorems",
        ["Divide polynomials and use the remainder theorem.", "Use the factor theorem to factorise cubics and solve cubic equations."],
        [("propriete", "Remainder and factor theorems", "When a polynomial P(x) is divided by (x − a) the remainder is P(a). If P(a) = 0 then (x − a) is a **factor**. For a factor (ax − b), test x = b/a."),
         ("methode", "Method: factorising a cubic", "1. Try x = ±1, ±2, ±3 … (factors of the constant term) until P(a) = 0.\n2. Divide by (x − a) (long division or inspection) to get a quadratic.\n3. Factorise the quadratic.\n4. Roots of P(x) = 0 are the values making each factor zero."),
         ("pieges", "Common mistakes", "- Testing x = a for the factor (x + a): the factor (x + 2) is tested with x = −2.\n- Losing a sign in the long division.\n- Stopping after finding one root: a cubic can have up to three.")],
        [("Example 1: factorising", "Factorise $x^3 - 2x^2 - 5x + 6$ completely and solve the equation = 0.",
          ["P(1) = 1 − 2 − 5 + 6 = 0, so (x − 1) is a factor. Divide: $x^3 - 2x^2 - 5x + 6 = (x - 1)(x^2 - x - 6)$.", "$x^2 - x - 6 = (x - 3)(x + 2)$.", "So P(x) = (x − 1)(x − 3)(x + 2) and the roots are 1, 3 and −2."], "**(x − 1)(x − 3)(x + 2); x = 1, 3, −2.**",
          plot(-3, 4, -8, 10, curves=[("x^3-2*x^2-5*x+6", "blue")], points=[(1, 0, None, "red"), (3, 0, None, "red"), (-2, 0, None, "red")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: finding an unknown", "(x − 2) is a factor of $x^3 + ax^2 - 4x - 12$. Find a and factorise fully. Also find the remainder when $2x^3 - 3x^2 + x - 5$ is divided by (x − 2).",
          ["P(2) = 8 + 4a − 8 − 12 = 4a − 12 = 0, so a = 3.", "$x^3 + 3x^2 - 4x - 12 = (x - 2)(x^2 + 5x + 6) = (x - 2)(x + 2)(x + 3)$.", "Remainder: 2(8) − 3(4) + 2 − 5 = 16 − 12 + 2 − 5 = 1."], "**a = 3; (x − 2)(x + 2)(x + 3); remainder 1.**")],
        [N("Find the remainder when $x^3 + 2x - 5$ is divided by (x − 1).", -2, "P(1) = 1 + 2 − 5 = −2."),
         M("Which is a factor of $x^3 - 7x + 6$?", "(x − 1)", ["(x + 1)", "(x − 6)", "(x + 6)"], "P(1) = 1 − 7 + 6 = 0; P(−1) = −1 + 7 + 6 = 12 is not zero."),
         N("(x + 3) is a factor of $x^2 + kx + 3$. Find k.", 4, "P(−3) = 9 − 3k + 3 = 0, so k = 4.")],
        [N("Find k if $x^3 + kx^2 - x - 2$ has remainder 0 when divided by (x + 1).", 2, "P(−1) = −1 + k + 1 − 2 = k − 2 = 0, so k = 2."),
         N("Solve $x^3 - 6x^2 + 11x - 6 = 0$ and enter the largest root.", 3, "P(1) = 0: (x − 1)(x² − 5x + 6) = (x − 1)(x − 2)(x − 3). Largest root 3.")],
        P("The volume of a block of soap in Bafoussam is $V = x^3 + 3x^2 - 4x - 12$ cm³ with x in cm.",
          [pn("Find V when x = 3.", 27 + 27 - 12 - 12, "27 + 27 − 12 − 12 = 30 cm³.", unit="cm³", pts=1),
           pm("Written as three factors, V =", "(x − 2)(x + 2)(x + 3)", ["(x − 1)(x + 4)(x − 3)", "(x + 2)(x + 3)(x + 4)", "(x − 2)(x − 3)(x + 2)"], "By the factor theorem and division (see the lesson).", 2),
           pn("Find the length of the shortest edge when x = 3 (the factors are the edges, in cm).", 1, "The edges are 3 − 2 = 1, 3 + 2 = 5 and 3 + 3 = 6; shortest 1 cm. Check 1 × 5 × 6 = 30.", unit="cm", pts=1)]),
        [("P(a) = 0 means:", "(x − a) is a factor", ["(x + a) is a factor", "a = 0", "P has no roots"], "Factor theorem."),
         ("Remainder of P(x) ÷ (x − 3):", "P(3)", ["P(−3)", "P(0)", "P(1/3)"], "Remainder theorem."),
         ("A cubic can have at most:", "3 real roots", ["2 real roots", "4 real roots", "1 real root"], "Degree 3."),
         ("To test the factor (x + 4) use:", "x = −4", ["x = 4", "x = 0", "x = 1/4"], "x + 4 = 0."),
         ("(x − 1)(x − 2)(x − 3) has constant term:", "−6", ["6", "−1", "0"], "−1 × −2 × −3.")],
        minutes=30)

    chk(lambda x: 2 / (x - 1) + 3 / (x + 2), lambda x: (5 * x + 1) / ((x - 1) * (x + 2)))
    chk(lambda x: 3 / (x + 1) + 2 / (x + 1) ** 2, lambda x: (3 * x + 5) / (x + 1) ** 2)
    it = plot(-5, 5, -10, 10, curves=[("(5*x+1)/((x-1)*(x+2))", "blue"), ("(5*x+1)/((x-1)*(x+2))", "blue"), ("(5*x+1)/((x-1)*(x+2))", "blue")],
              segments=[(1, -10, 1, 10, "red", True), (-2, -10, -2, 10, "red", True)], grid=1, xlabel="x", ylabel="y")
    for c, (lo, hi) in zip(it["curves"], [(-5, -2.25), (-1.75, 0.8), (1.25, 5)]): c.update({"from": lo, "to": hi})
    L = lesson(ch, "partial-fractions", "Partial fractions",
        ["Split a proper fraction with linear factors into partial fractions.", "Handle repeated linear factors.", "Use partial fractions to add or simplify."],
        [("methode", "Method: linear factors", "For $\\frac{px + q}{(x - a)(x - b)}$ write $\\frac{A}{x - a} + \\frac{B}{x - b}$. Multiply by the denominator: $px + q = A(x - b) + B(x - a)$. Substitute x = a to find A and x = b to find B (the **cover-up** method)."),
         ("propriete", "Repeated factors", "For $\\frac{px + q}{(x - a)^2}$ write $\\frac{A}{x - a} + \\frac{B}{(x - a)^2}$. Compare coefficients, or substitute x = a for B and another value for A. The fraction must be **proper** (degree of the top less than that of the bottom); if not, divide first."),
         ("pieges", "Common mistakes", "- Writing $\\frac{Ax + B}{(x - a)(x - b)}$ for a linear numerator: you need one constant per linear factor.\n- Forgetting the second term $\\frac{B}{(x - a)^2}$ for a squared factor.\n- Not checking by substituting a simple value such as x = 0.")],
        [("Example 1: two linear factors", "Express $\\frac{5x + 1}{(x - 1)(x + 2)}$ in partial fractions.",
          ["Write $\\frac{5x + 1}{(x - 1)(x + 2)} = \\frac{A}{x - 1} + \\frac{B}{x + 2}$, so $5x + 1 = A(x + 2) + B(x - 1)$.", "x = 1: 6 = 3A, so A = 2. x = −2: −9 = −3B, so B = 3.", "Check x = 0: LHS = 1/(−2) = −0.5; RHS = −2 + 1.5 = −0.5."], "**$\\frac{2}{x - 1} + \\frac{3}{x + 2}$.**", it),
         ("Example 2: repeated factor", "Express $\\frac{3x + 5}{(x + 1)^2}$ in partial fractions.",
          ["Write $\\frac{3x + 5}{(x + 1)^2} = \\frac{A}{x + 1} + \\frac{B}{(x + 1)^2}$, so $3x + 5 = A(x + 1) + B$.", "Compare x: A = 3. Constants: 5 = A + B, so B = 2.", "Check x = 0: 5 = 3 + 2."], "**$\\frac{3}{x + 1} + \\frac{2}{(x + 1)^2}$.**")],
        [N("In $\\frac{7}{(x - 2)(x + 5)} = \\frac{A}{x - 2} + \\frac{B}{x + 5}$ find A.", 1, "x = 2: 7 = 7A, A = 1."),
         N("In the same identity find B.", -1, "x = −5: 7 = −7B, so B = −1."),
         M("The form of the partial fractions of $\\frac{x + 4}{x(x + 2)^2}$ is:", "$\\frac{A}{x} + \\frac{B}{x + 2} + \\frac{C}{(x + 2)^2}$", ["$\\frac{A}{x} + \\frac{B}{(x + 2)^2}$", "$\\frac{A}{x(x + 2)^2}$", "$\\frac{Ax + B}{x(x + 2)}$"], "One term for x and one for each power of (x + 2).")],
        [N("Write $\\frac{4}{(x - 1)(x + 3)}$ as $\\frac{A}{x - 1} + \\frac{B}{x + 3}$ and find A.", 1, "x = 1: 4 = 4A, so A = 1 (and B = −1)."),
         N("Hence evaluate $\\frac{4}{(x - 1)(x + 3)}$ at x = 5 as a decimal from the partial fractions $\\frac{1}{x - 1} - \\frac{1}{x + 3}$.", 0.125, "1/4 − 1/8 = 1/8 = 0.125.", tol=0.0001)],
        P("Let $f(x) = \\frac{2}{x(x + 2)}$.",
          [pm("Which is the partial fraction form of f(x)?", "$\\frac{1}{x} - \\frac{1}{x + 2}$", ["$\\frac{1}{x} + \\frac{1}{x + 2}$", "$\\frac{2}{x} - \\frac{2}{x + 2}$", "$\\frac{1}{2x} - \\frac{1}{x + 2}$"], "x = 0 gives A = 1; x = −2 gives B = −1.", 2),
           pn("Find f(3) directly.", 2 / 15, "2 ÷ (3 × 5) = 2/15 ≈ 0.1333.", tol=0.001, pts=1),
           pn("Check with the partial fractions: 1/3 − 1/5 as a decimal.", 2 / 15, "1/3 − 1/5 = 2/15 ≈ 0.1333, the same value.", tol=0.001, pts=1)]),
        [("Partial fractions apply to:", "proper fractions", ["any expression", "only polynomials", "only decimals"], "Degree of top < degree of bottom."),
         ("For $\\frac{1}{(x - 1)(x - 2)}$ the terms are:", "A/(x − 1) + B/(x − 2)", ["A/(x² − 3x + 2)", "(Ax + B)/(x − 1)", "A(x − 1)(x − 2)"], "One constant per linear factor."),
         ("Cover-up: A = (value at x = a) when:", "factor (x − a) is covered", ["denominator is zero", "x = 0", "never"], "Method."),
         ("A repeated factor (x − 1)² gives:", "two terms", ["one term", "three terms", "no term"], "A/(x − 1) + B/(x − 1)²."),
         ("To check partial fractions:", "substitute a value of x", ["add the denominators", "take logs", "differentiate"], "Easy value such as 0.")],
        minutes=30, notes=["Partial fractions may be beyond the Cameroon O Level further-maths syllabus (more typical of A Level): flagged as optional; confirm with the syllabus."])
