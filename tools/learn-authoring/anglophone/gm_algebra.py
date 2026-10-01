from _kit import *

def build(p):
    ch = p.chapter("algebra2", "Algebra: expressions, formulae, inequalities and variation", "O Level Mathematics — Algebra (to be checked)")

    # ------------------------------------------------------------- expand / factorise
    chk(lambda x: (x + 3) * (x - 5), lambda x: x * x - 2 * x - 15)
    chk(lambda x: (2 * x - 1) ** 2, lambda x: 4 * x * x - 4 * x + 1)
    chk(lambda x: (3 * x + 2) * (2 * x - 1), lambda x: 6 * x * x + x - 2)
    chk(lambda x: 49 - 4 * x * x, lambda x: (7 - 2 * x) * (7 + 2 * x))
    chk(lambda x: (x - 4) * (x - 5), lambda x: x * x - 9 * x + 20)
    chk(lambda x: 2 * x * x - 8, lambda x: 2 * (x - 2) * (x + 2))
    chk(lambda x: x ** 2 + 5 * x + 6, lambda x: (x + 2) * (x + 3))
    L = lesson(ch, "expand-factorise", "Expansion and factorisation",
        ["Expand brackets, including products of two binomials.", "Factorise by common factor, grouping, difference of two squares and trinomials."],
        [("propriete", "Key identities", "Three results to know by heart:", "(a+b)^2 = a^2 + 2ab + b^2 \\qquad (a-b)^2 = a^2 - 2ab + b^2 \\qquad a^2 - b^2 = (a-b)(a+b)"),
         ("methode", "Method: factorising", "1. Always take out the **highest common factor** first.\n2. Two terms: look for a difference of two squares.\n3. Three terms $ax^2 + bx + c$: find two numbers with product ac and sum b, split the middle term, then group in pairs.\n4. **Check by expanding** your answer."),
         ("pieges", "Common mistakes", "- $(a + b)^2 \\neq a^2 + b^2$: do not forget the middle term 2ab.\n- A sign error when expanding $-(x - 3)$: it is $-x + 3$.\n- Stopping early: $2x^2 - 8 = 2(x^2 - 4)$ is not finished; it is $2(x - 2)(x + 2)$.")],
        [("Example 1: expansion", "Expand and simplify (a) $(x + 3)(x - 5)$ (b) $(2x - 1)^2$.",
          [("(a) Multiply each term: $x^2 - 5x + 3x - 15$.", "x^2 - 2x - 15"), ("(b) $(2x)^2 - 2(2x)(1) + 1^2$.", "4x^2 - 4x + 1")], "**(a) $x^2 - 2x - 15$; (b) $4x^2 - 4x + 1$.**"),
         ("Example 2: factorising", "Factorise (a) $6x^2 + x - 2$ (b) $49 - 4x^2$.",
          ["(a) ac = −12; two numbers with product −12 and sum +1: +4 and −3. Split: $6x^2 + 4x - 3x - 2 = 2x(3x + 2) - (3x + 2)$, giving $(3x + 2)(2x - 1)$.", "(b) $7^2 - (2x)^2 = (7 - 2x)(7 + 2x)$."], "**(a) $(3x + 2)(2x - 1)$; (b) $(7 - 2x)(7 + 2x)$.**")],
        [M("Expand $(x - 4)(x - 5)$.", "$x^2 - 9x + 20$", ["$x^2 + 9x + 20$", "$x^2 - 9x - 20$", "$x^2 - x + 20$"], "$x^2 - 5x - 4x + 20$."),
         M("Factorise $x^2 + 5x + 6$.", "$(x + 2)(x + 3)$", ["$(x + 1)(x + 6)$", "$(x - 2)(x - 3)$", "$(x + 5)(x + 1)$"], "Product 6, sum 5: 2 and 3."),
         N("Evaluate $(x + 4)^2 - (x - 4)^2$ when x = 5.", 80, "The expression equals 16x, which is 80. Direct: 81 − 1 = 80.")],
        [M("Factorise completely $2x^2 - 8$.", "$2(x - 2)(x + 2)$", ["$2(x - 4)$", "$(2x - 4)(x + 2)$", "$2(x^2 - 4)$"], "Take out 2, then difference of squares. $(2x-4)(x+2)$ is not fully factorised (2 is still common); $2(x^2-4)$ stops early."),
         N("Use factorisation to evaluate $97^2 - 3^2$.", 9400, "$(97 - 3)(97 + 3) = 94 \\times 100 = 9400$.")],
        P("A rectangular plot in Garoua has length (x + 5) m and width (x + 3) m.",
          [pm("Its area, expanded, is:", "$x^2 + 8x + 15$", ["$x^2 + 15$", "$x^2 + 8x + 8$", "$2x + 8$"], "$(x + 5)(x + 3) = x^2 + 3x + 5x + 15$.", 2),
           pn("Find the area when x = 7.", 12 * 10, "$12 \\times 10 = 120$ m²; check $49 + 56 + 15 = 120$.", unit="m²", pts=1),
           pn("Find x if the area is 63 m² (positive value).", 4, "$x^2 + 8x + 15 = 63$ gives $x^2 + 8x - 48 = (x + 12)(x - 4) = 0$, so x = 4 (x = −12 is rejected).", pts=2)]),
        [("$(x + 2)(x - 2)$ equals:", "$x^2 - 4$", ["$x^2 + 4$", "$x^2 - 4x$", "$x^2 - 2$"], "Difference of squares."),
         ("The HCF of $12x^2y$ and $18xy^2$ is:", "6xy", ["12xy", "6x²y²", "36xy"], "HCF of 12 and 18 is 6, and of the letters xy."),
         ("$(a - b)^2$ equals:", "$a^2 - 2ab + b^2$", ["$a^2 - b^2$", "$a^2 + b^2$", "$a^2 - 2ab - b^2$"], "Perfect square."),
         ("Factorise $x^2 - 7x + 12$:", "$(x - 3)(x - 4)$", ["$(x + 3)(x + 4)$", "$(x - 2)(x - 6)$", "$(x - 1)(x - 12)$"], "Product 12, sum −7."),
         ("$3(x - 2) - 2(x - 3)$ simplifies to:", "$x$", ["$x - 12$", "$5x$", "$x + 12$"], "$3x - 6 - 2x + 6 = x$.")],
        ill=(shapes([RECT(40, 30, 140, 90, fill="lightblue"), RECT(180, 30, 90, 90, fill="lightyellow"), RECT(40, 120, 140, 60, fill="lightgreen"), RECT(180, 120, 90, 60, fill="lightorange"),
                     T(110, 80, "x²", 18), T(225, 80, "5x", 16), T(110, 155, "3x", 16), T(225, 155, "15", 16), T(110, 20, "x", 14), T(225, 20, "5", 14), T(25, 80, "x", 14), T(25, 155, "3", 14)], 300, 200),
             "Area model for (x + 5)(x + 3)", "A rectangle split into four parts with areas x squared, 5x, 3x and 15 representing the expansion of x plus 5 times x plus 3"), minutes=25)

    # ------------------------------------------------------------- algebraic fractions
    chk(lambda x: (x * x - 9) / (x * x + 5 * x + 6), lambda x: (x - 3) / (x + 2))
    chk(lambda x: 2 / (x + 1) + 3 / (x - 2), lambda x: (5 * x - 1) / ((x + 1) * (x - 2)))
    assert (11 + 1) / 2 - (11 - 2) / 3 == 3
    assert 3 / (-8 - 1) == 2 / (-8 + 2)
    L = lesson(ch, "algebraic-fractions", "Algebraic fractions",
        ["Simplify algebraic fractions by factorising and cancelling.", "Add and subtract algebraic fractions.", "Solve equations that contain fractions."],
        [("methode", "Simplifying and combining", "- **Simplify**: factorise numerator and denominator, then cancel **common factors** (not common terms).\n- **Add or subtract**: use a common denominator (the product or LCM), combine the numerators, then simplify.\n- **Solve**: multiply every term by the common denominator to clear the fractions."),
         ("definition", "Restrictions", "A fraction is undefined when its denominator is zero. In $\\frac{3}{x - 1}$, x cannot equal 1. When a solution makes a denominator zero, it must be rejected."),
         ("pieges", "Common mistakes", "- Cancelling terms instead of factors: in $\\frac{x + 3}{x + 5}$ nothing cancels.\n- Adding numerators and denominators: $\\frac{1}{a} + \\frac{1}{b} \\neq \\frac{2}{a + b}$.\n- Forgetting to multiply **every** term when clearing fractions, including the term without a fraction.")],
        [("Example 1: simplify and add", "(a) Simplify $\\frac{x^2 - 9}{x^2 + 5x + 6}$. (b) Write $\\frac{2}{x + 1} + \\frac{3}{x - 2}$ as a single fraction.",
          [("(a) Factorise: $\\frac{(x - 3)(x + 3)}{(x + 2)(x + 3)}$; cancel (x + 3).", "\\frac{x - 3}{x + 2}"), ("(b) Common denominator (x + 1)(x − 2): numerator $2(x - 2) + 3(x + 1) = 5x - 1$.", "\\frac{5x - 1}{(x + 1)(x - 2)}")], "**(a) $\\frac{x - 3}{x + 2}$; (b) $\\frac{5x - 1}{(x + 1)(x - 2)}$.**"),
         ("Example 2: an equation", "Solve $\\frac{x + 1}{2} - \\frac{x - 2}{3} = 3$.",
          ["Multiply every term by 6: $3(x + 1) - 2(x - 2) = 18$.", "Expand: $3x + 3 - 2x + 4 = 18$, so $x + 7 = 18$.", "x = 11. Check: 12/2 − 9/3 = 6 − 3 = 3."], "**x = 11.**")],
        [M("Simplify $\\frac{6x^2}{3x}$.", "$2x$", ["$2x^2$", "$3x$", "$2$"], "Divide 6 by 3 and cancel one x."),
         N("Solve $\\frac{x}{3} + \\frac{x}{6} = 4$.", 8, "Multiply by 6: 2x + x = 24, so x = 8."),
         M("For which value is $\\frac{5}{x - 4}$ undefined?", "x = 4", ["x = 5", "x = 0", "x = −4"], "The denominator is zero when x = 4.")],
        [N("Solve $\\frac{3}{x - 1} = \\frac{2}{x + 2}$.", -8, "Cross-multiply: 3(x + 2) = 2(x − 1), 3x + 6 = 2x − 2, so x = −8. Check: 3/(−9) = 2/(−6) = −1/3."),
         M("Simplify $\\frac{x^2 - 4}{x^2 - 2x}$.", "$\\frac{x + 2}{x}$", ["$\\frac{x - 2}{x}$", "$\\frac{x + 2}{x - 2}$", "$2$"], "$\\frac{(x - 2)(x + 2)}{x(x - 2)}$; cancel (x − 2).")],
        P("Two taps fill a tank in Maroua. Tap A alone takes x hours and tap B alone takes (x + 2) hours.",
          [pm("The fraction of the tank filled by both in one hour is:", "$\\frac{1}{x} + \\frac{1}{x + 2}$", ["$\\frac{2}{2x + 2}$", "$\\frac{1}{2x + 2}$", "$\\frac{x(x + 2)}{1}$"], "Each hour A fills 1/x and B fills 1/(x + 2). Adding numerators and denominators is wrong.", 1),
           pm("Written as a single fraction this is:", "$\\frac{2x + 2}{x(x + 2)}$", ["$\\frac{2}{x(x + 2)}$", "$\\frac{x + 2}{x}$", "$\\frac{2x + 2}{x + 2}$"], "$\\frac{(x + 2) + x}{x(x + 2)}$.", 2),
           pn("For x = 4 the tank takes how many hours when both taps run? (to 1 d.p.)", round(24 / 10, 1), "In 1 hour: 10/24 of the tank; time = 24 ÷ 10 = 2.4 hours.", tol=0.05, unit="h", pts=2)]),
        [("$\\frac{2}{3} \\div \\frac{x}{4}$ equals:", "$\\frac{8}{3x}$", ["$\\frac{x}{6}$", "$\\frac{2x}{12}$", "$\\frac{3x}{8}$"], "Multiply by the reciprocal $\\frac{4}{x}$."),
         ("$\\frac{a}{b} + \\frac{c}{b} =$", "$\\frac{a + c}{b}$", ["$\\frac{a + c}{2b}$", "$\\frac{ac}{b}$", "$\\frac{a + c}{b^2}$"], "Same denominator."),
         ("Solve $\\frac{x}{5} = 3$:", "15", ["8", "0.6", "5/3"], "Multiply by 5."),
         ("$\\frac{x + 3}{x + 3}$ equals 1 provided that:", "x ≠ −3", ["x ≠ 3", "x ≠ 0", "x > 0"], "The denominator must not be 0."),
         ("$\\frac{1}{2} + \\frac{1}{x}$ with common denominator 2x:", "$\\frac{x + 2}{2x}$", ["$\\frac{2}{x + 2}$", "$\\frac{x + 1}{2x}$", "$\\frac{2}{2x}$"], "$\\frac{x}{2x} + \\frac{2}{2x}$.")],
        ill=(plot(-4, 4, -4, 4, curves=[("1/x", "blue"), ("1/x", "blue")], segments=[(0, -4, 0, 4, "red", True, "x = 0")], grid=1, xlabel="x", ylabel="y"), "y = 1/x is not defined at x = 0", "The hyperbola y equals 1 over x with the line x equals 0 where the fraction is undefined"), minutes=25)
    # fix the two branches of the hyperbola (avoid the asymptote)
    figs = [b for b in L.d["blocks"] if b.get("type") == "illustration"][0]["figure"]
    figs["curves"][0].update({"from": -4, "to": -0.25}); figs["curves"][1].update({"from": 0.25, "to": 4})

    # ------------------------------------------------------------- change of subject
    chk(lambda y: (y + 2) / (3 - y), lambda y: (y + 2) / (3 - y))
    for x in (0.3, 2.0, 5.5):
        y = (3 * x - 2) / (x + 1); assert abs((y + 2) / (3 - y) - x) < 1e-9
    L = lesson(ch, "change-of-subject", "Changing the subject of a formula",
        ["Make a letter the subject of a formula using inverse operations.", "Handle formulae with squares, roots, fractions and the subject appearing twice.", "Substitute values into a formula."],
        [("methode", "Method", "Treat the formula like an equation and do the **same operation to both sides**, undoing operations in the reverse order of BODMAS: first +/−, then ×/÷, then powers and roots. If the new subject appears twice, collect those terms on one side and **factorise**."),
         ("formule", "Examples of results", "From $v = u + at$ and $A = \\pi r^2$:", "a = \\frac{v - u}{t} \\qquad r = \\sqrt{\\frac{A}{\\pi}}"),
         ("pieges", "Common mistakes", "- Operating on only one term: from $y = \\frac{x}{2} + 3$, the next step is $2(y - 3) = x$, not $2y - 3 = x$.\n- Square roots: take the root of the **whole** side.\n- Losing a minus sign when moving a term across.")],
        [("Example 1: roots and squares", "Make l the subject of $T = 2\\pi\\sqrt{\\frac{l}{g}}$.",
          [("Divide by $2\\pi$:", "\\frac{T}{2\\pi} = \\sqrt{\\frac{l}{g}}"), ("Square both sides, then multiply by g:", "l = \\frac{g T^2}{4\\pi^2}")], "**$l = \\frac{g T^2}{4\\pi^2}$.**"),
         ("Example 2: subject appears twice", "Make x the subject of $y = \\frac{3x - 2}{x + 1}$.",
          ["Multiply by (x + 1): $y(x + 1) = 3x - 2$, so $yx + y = 3x - 2$.", "Collect x terms: $yx - 3x = -2 - y$, so $x(y - 3) = -(y + 2)$.", "Divide: $x = \\frac{-(y + 2)}{y - 3} = \\frac{y + 2}{3 - y}$. Check x = 2: y = 4/3 and (4/3 + 2)/(3 − 4/3) = 2."], "**$x = \\frac{y + 2}{3 - y}$.**")],
        [M("Make a the subject of $v = u + at$.", "$a = \\frac{v - u}{t}$", ["$a = v - u - t$", "$a = \\frac{v}{t} - u$", "$a = \\frac{u - v}{t}$"], "Subtract u then divide by t."),
         N("Use $C = \\frac{5}{9}(F - 32)$ to find C when F = 77.", 25, "$\\frac{5}{9} \\times 45 = 25$.", unit="°C"),
         M("Make r the subject of $A = \\pi r^2$.", "$r = \\sqrt{\\frac{A}{\\pi}}$", ["$r = \\frac{A}{\\pi^2}$", "$r = \\frac{\\sqrt{A}}{\\pi}$", "$r = A\\pi$"], "Divide by π, then take the square root.")],
        [M("Make x the subject of $y = \\frac{x}{2} + 3$.", "$x = 2(y - 3)$", ["$x = 2y - 3$", "$x = \\frac{y - 3}{2}$", "$x = 2y + 3$"], "Subtract 3 (from the whole side), then multiply by 2."),
         N("Make b the subject of $a = \\frac{b + c}{2}$ and find b when a = 7, c = 5.", 9, "$b = 2a - c = 14 - 5 = 9$.")],
        P("The volume of a cylinder is $V = \\pi r^2 h$ (use π = 3.14). A water tank in Bafoussam is a cylinder.",
          [pm("Making h the subject gives:", "$h = \\frac{V}{\\pi r^2}$", ["$h = \\frac{V \\pi}{r^2}$", "$h = V - \\pi r^2$", "$h = \\frac{\\pi r^2}{V}$"], "Divide both sides by $\\pi r^2$.", 1),
           pn("Find h in metres when V = 12.56 m³ and r = 1 m.", 12.56 / 3.14, "h = 12.56 ÷ 3.14 = 4 m.", tol=0.01, unit="m", pts=2),
           pn("Find r when V = 28.26 m³ and h = 1 m (r positive).", 3, "$r^2 = 28.26 \\div 3.14 = 9$, so r = 3 m.", tol=0.01, unit="m", pts=2)]),
        [("From $y = mx + c$, x equals:", "$\\frac{y - c}{m}$", ["$y - c - m$", "$\\frac{y}{m} + c$", "$m(y - c)$"], "Subtract c, divide by m."),
         ("From $d = st$, t equals:", "$\\frac{d}{s}$", ["$ds$", "$\\frac{s}{d}$", "$d - s$"], "Divide by s."),
         ("From $a^2 + b^2 = c^2$, b equals:", "$\\sqrt{c^2 - a^2}$", ["$c - a$", "$\\sqrt{c^2} - a$", "$c^2 - a^2$"], "Subtract $a^2$, then square root."),
         ("If $x = \\frac{y}{3}$, then y =", "3x", ["x/3", "x + 3", "x − 3"], "Multiply by 3."),
         ("From $P = 2(l + w)$, w equals:", "$\\frac{P}{2} - l$", ["$2P - l$", "$P - 2l$", "$\\frac{P - l}{2}$"], "Divide by 2 then subtract l.")],
        ill=(shapes([RECT(30, 60, 90, 50, radius=8, fill="lightblue"), T(75, 90, "×3", 18), RECT(160, 60, 90, 50, radius=8, fill="lightyellow"), T(205, 90, "−2", 18),
                     LINE(120, 85, 160, 85, arrow="end"), LINE(250, 85, 290, 85, arrow="end"), T(15, 85, "x", 16), T(305, 85, "y", 16),
                     LINE(290, 140, 30, 140, color="red", arrow="end"), T(160, 165, "inverse: add 2, then ÷3", 14, color="red")], 330, 180),
             "Function machine and its inverse", "A function machine multiplying by 3 then subtracting 2, with the inverse path drawn underneath"), minutes=25)

    # ------------------------------------------------------------- inequalities
    assert 3 * 4 - 5 < 10 and 3 * 5 - 5 == 10
    ints = [x for x in range(-10, 10) if -3 < 2 * x + 1 <= 9]; assert ints == [-1, 0, 1, 2, 3, 4]
    q = [x for x in range(-10, 10) if x * x - x - 6 < 0]; assert q == [-1, 0, 1, 2]
    L = lesson(ch, "inequalities", "Inequalities",
        ["Solve linear inequalities and show the solution on a number line.", "Solve double inequalities and list integer solutions.", "Solve simple quadratic inequalities."],
        [("propriete", "Rules", "Solve like an equation, with one big difference: **multiplying or dividing by a negative number reverses the inequality sign**. For example $-2x \\geq 6$ gives $x \\leq -3$. Adding or subtracting never changes the direction."),
         ("definition", "Number-line notation", "An **open** circle means the end value is not included (< or >). A **filled** circle means it is included (≤ or ≥). A double inequality such as $-2 < x \\leq 4$ means both conditions hold together."),
         ("methode", "Quadratic inequalities", "To solve $x^2 - x - 6 < 0$: factorise $(x + 2)(x - 3) < 0$; the roots −2 and 3 split the line into three regions. The parabola is below the axis **between** the roots, so $-2 < x < 3$. For $> 0$ the answer is outside the roots."),
         ("pieges", "Common mistakes", "- Forgetting to reverse the sign when dividing by a negative number.\n- Using a filled circle for a strict inequality.\n- Quadratic inequality: mixing up *between* the roots (< 0) and *outside* the roots (> 0).")],
        [("Example 1: double inequality", "Solve $-3 < 2x + 1 \\leq 9$ and list the integer values of x.",
          ["Subtract 1 from all three parts: $-4 < 2x \\leq 8$.", "Divide by 2: $-2 < x \\leq 4$.", "Integers: −1, 0, 1, 2, 3, 4 (−2 is excluded, 4 is included)."], "**$-2 < x \\leq 4$; integers −1, 0, 1, 2, 3, 4.**",
          num_line(-4, 6, [], 1, hollow=[-2], filled=[4], arrow_from=-2, arrow_to=4)),
         ("Example 2: reversing the sign", "Solve $5 - 3x > 14$.",
          ["Subtract 5: $-3x > 9$.", "Divide by −3 and reverse the sign: $x < -3$.", "Check x = −4: 5 + 12 = 17 > 14. True."], "**x < −3.**")],
        [M("Solve $3x - 5 < 10$.", "x < 5", ["x > 5", "x < 15", "x < 5/3"], "3x < 15, so x < 5."),
         N("How many integers satisfy $-3 \\leq x < 2$?", 5, "−3, −2, −1, 0, 1: five integers."),
         M("Solve $-2x \\geq 6$.", "x ≤ −3", ["x ≥ −3", "x ≥ 3", "x ≤ 3"], "Divide by −2 and reverse the sign.")],
        [M("Solve $x^2 - x - 6 < 0$.", "$-2 < x < 3$", ["$x < -2$ or $x > 3$", "$-3 < x < 2$", "$x < 3$"], "Roots −2 and 3; the curve is negative between them."),
         N("The largest integer x such that $4x - 3 \\leq 17$ is:", 5, "$4x \\leq 20$, $x \\leq 5$.")],
        P("A taxi driver in Limbe charges 500 FCFA plus 150 FCFA per kilometre. A passenger has at most 3 000 FCFA.",
          [pm("Which inequality describes the distance d km?", "$500 + 150d \\leq 3000$", ["$500 + 150d \\geq 3000$", "$650d \\leq 3000$", "$500d + 150 \\leq 3000$"], "The cost must not exceed 3 000.", 1),
           pn("What is the greatest whole number of kilometres?", 16, "$150d \\leq 2500$, so $d \\leq 16.67$: the greatest whole number is 16.", pts=2),
           pn("How much would a 16 km journey cost?", 500 + 150 * 16, "500 + 2 400 = 2 900 FCFA.", unit="FCFA", pts=1)]),
        [("The solution of $x + 4 \\geq 9$ is:", "x ≥ 5", ["x ≤ 5", "x ≥ 13", "x > 5"], "Subtract 4."),
         ("A filled circle on a number line means:", "the value is included", ["the value is excluded", "the value is negative", "no solution"], "≤ or ≥."),
         ("Solve $-x < 4$:", "x > −4", ["x < −4", "x < 4", "x > 4"], "Multiply by −1: reverse."),
         ("Integers with $1 < x \\leq 4$:", "2, 3, 4", ["1, 2, 3, 4", "2, 3", "1, 2, 3"], "1 excluded, 4 included."),
         ("$x^2 > 9$ has solution:", "x < −3 or x > 3", ["−3 < x < 3", "x > 3", "x > −3"], "Outside the roots.")],
        minutes=25, notes=["Quadratic inequalities may go beyond the basic O Level Mathematics syllabus; check."])

    # ------------------------------------------------------------- variation
    assert 12 / 4 * 7 == 21 and 50 / 25 == 2 and (72 / 2) ** 0.5 == 6 and 6 * 4 / 8 == 3
    L = lesson(ch, "variation", "Direct, inverse and joint variation",
        ["Write and use equations of direct and inverse variation.", "Find the constant of variation from given values.", "Solve problems with squares and joint variation."],
        [("definition", "Variation", "- $y$ varies **directly** as $x$: $y = kx$ (graph: a straight line through the origin).\n- $y$ varies **inversely** as $x$: $y = \\frac{k}{x}$ (graph: a curve; as x grows, y shrinks).\n- y ∝ x² means $y = kx^2$. **Joint**: y ∝ x/z means $y = \\frac{kx}{z}$."),
         ("methode", "Method", "1. Write the **equation with k** from the words.\n2. Substitute the first pair of values to find k.\n3. Write the full law with the value of k.\n4. Use the law to find the unknown."),
         ("pieges", "Common mistakes", "- Skipping step 1 and guessing the formula.\n- Direct and inverse mixed up: more pumps means *less* time (inverse).\n- y ∝ x²: if x doubles, y becomes **four** times bigger, not double.")],
        [("Example 1: direct variation with a square", "y varies as the square of x, and y = 50 when x = 5. Find y when x = 8, and x when y = 72 (x > 0).",
          ["$y = kx^2$. With x = 5: 50 = 25k, so k = 2 and $y = 2x^2$.", "x = 8: $y = 2 \\times 64 = 128$.", "y = 72: $x^2 = 36$, so x = 6."], "**y = 128 when x = 8; x = 6 when y = 72.**"),
         ("Example 2: inverse variation", "The time t hours to fill a tank varies inversely as the number n of pumps. 3 pumps take 8 hours. How long do 6 pumps take, and how many pumps are needed for 3 hours?",
          ["$t = \\frac{k}{n}$. With n = 3, t = 8: k = 24, so $t = \\frac{24}{n}$.", "n = 6: t = 24 ÷ 6 = 4 hours.", "t = 3: n = 24 ÷ 3 = 8 pumps."], "**4 hours; 8 pumps.**")],
        [N("y varies directly as x and y = 12 when x = 4. Find y when x = 7.", 21, "k = 12 ÷ 4 = 3, so y = 3x = 21."),
         M("Which equation says that y varies inversely as x?", "$y = \\frac{k}{x}$", ["$y = kx$", "$y = kx^2$", "$y = k + x$"], "Inverse variation: the product xy is constant."),
         N("y varies inversely as x and y = 6 when x = 4. Find y when x = 8.", 3, "k = 24; y = 24 ÷ 8 = 3.")],
        [N("A varies as r² and A = 12 when r = 2. Find A when r = 5.", 75, "k = 12/4 = 3; A = 3 × 25 = 75."),
         N("y varies directly as x and inversely as z. y = 6 when x = 4 and z = 2. Find y when x = 6 and z = 3.", 6, "$y = \\frac{kx}{z}$: 6 = 4k/2 so k = 3. Then y = 3 × 6 ÷ 3 = 6.")],
        P("The cost C of a gravel delivery in Bamenda varies directly as the volume V m³. 4 m³ cost 60 000 FCFA.",
          [pn("Find the constant k (FCFA per m³).", 15000, "k = 60 000 ÷ 4 = 15 000.", unit="FCFA", pts=1),
           pn("Find the cost of 7.5 m³.", 112500, "C = 15 000 × 7.5 = 112 500 FCFA.", unit="FCFA", pts=2),
           pn("What volume can be bought for 90 000 FCFA?", 6, "V = 90 000 ÷ 15 000 = 6 m³.", unit="m³", pts=1)]),
        [("If y ∝ x and x doubles, y:", "doubles", ["halves", "stays the same", "quadruples"], "Direct variation."),
         ("If y ∝ 1/x and x doubles, y:", "halves", ["doubles", "quadruples", "stays the same"], "Inverse variation."),
         ("If y ∝ x² and x triples, y becomes:", "9 times larger", ["3 times larger", "6 times larger", "27 times larger"], "3² = 9."),
         ("y = kx with y = 20 when x = 5 gives k =", "4", ["100", "25", "0.25"], "20 ÷ 5."),
         ("The graph of y = k/x (k > 0, x > 0) is:", "a decreasing curve", ["a straight line through the origin", "a parabola", "a horizontal line"], "It shrinks as x grows.")],
        ill=(plot(0, 12, 0, 12, curves=[("3*x", "blue", "y = 3x"), ("24/x", "red", "y = 24/x")], grid=1, xlabel="x", ylabel="y"), "Direct (blue) and inverse (red) variation", "Graph with a rising straight line y equals 3x and a falling curve y equals 24 over x"), minutes=25)
    fg = [b for b in L.d["blocks"] if b.get("type") == "illustration"][0]["figure"]
    fg["curves"][0].update({"from": 0, "to": 4}); fg["curves"][1].update({"from": 2, "to": 12})
