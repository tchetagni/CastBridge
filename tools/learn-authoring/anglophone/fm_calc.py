from _kit import *
import math

def build(p):
    ch = p.chapter("calculus", "Differentiation and integration", "O Level Further Mathematics — Calculus (to be checked)")
    f = lambda x: 3 * x * x - 4 * x + 5; df = lambda x: 6 * x - 4
    assert f(2) == 9 and df(2) == 8 and 8 * 2 - 7 == 9
    h = 1e-6; assert abs((f(2 + h) - f(2 - h)) / (2 * h) - 8) < 1e-5
    g = lambda x: (2 * x + 1) ** 5; assert abs((g(1 + h) - g(1 - h)) / (2 * h) - 10 * 3 ** 4) < 1e-3
    L = lesson(ch, "differentiation", "Differentiation: gradient, tangent and normal",
        ["Differentiate powers of x, sums, and (ax + b)ⁿ.", "Find the gradient of a curve, and the equations of a tangent and a normal.", "Interpret dy/dx as a rate of change."],
        [("formule", "Rules of differentiation", "For a constant a and any number n:", "\\frac{d}{dx}(ax^n) = anx^{n-1} \\qquad \\frac{d}{dx}(ax+b)^n = na(ax+b)^{n-1}"),
         ("definition", "Meaning", "$\\frac{dy}{dx}$ (also written f′(x)) is the **gradient of the curve** at x and the **rate of change** of y with respect to x. The derivative of a constant is 0, and sums are differentiated term by term."),
         ("methode", "Tangent and normal at x = a", "1. Find y at x = a to get the point.\n2. Find $\\frac{dy}{dx}$ and put x = a: this is the tangent gradient m.\n3. Tangent: y − y₁ = m(x − a). The **normal** is perpendicular: gradient −1/m, through the same point."),
         ("pieges", "Common mistakes", "- Differentiating $(2x + 1)^5$ as $5(2x + 1)^4$: forget the factor 2 from the chain rule.\n- Using the gradient of the tangent as the gradient of the normal.\n- Differentiating a constant term and getting that constant.")],
        [("Example 1: tangent and normal", "Find the gradient of $y = 3x^2 - 4x + 5$ at x = 2 and the equations of the tangent and normal there.",
          ["dy/dx = 6x − 4, so at x = 2 the gradient is 8. The point is y = 12 − 8 + 5 = 9, i.e. (2, 9).", "Tangent: y − 9 = 8(x − 2), so y = 8x − 7.", "Normal: gradient −1/8: y − 9 = −(1/8)(x − 2), so x + 8y = 74."], "**Gradient 8; tangent y = 8x − 7; normal x + 8y = 74.**",
          plot(0, 4, 0, 20, curves=[("3*x^2-4*x+5", "blue"), ("8*x-7", "red", "tangent")], points=[(2, 9, "(2, 9)", "green")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: chain rule", "Differentiate (a) $y = 4x^3 - \\frac{6}{x} + 7$ (b) $y = (2x + 1)^5$.",
          ["(a) Write y = 4x³ − 6x⁻¹ + 7; dy/dx = 12x² + 6x⁻² = 12x² + 6/x².", "(b) dy/dx = 5 × 2 × (2x + 1)⁴ = 10(2x + 1)⁴.", "At x = 1 the gradient in (b) is 10 × 81 = 810."], "**(a) 12x² + 6/x²; (b) 10(2x + 1)⁴.**")],
        [N("Differentiate $y = x^3 + 2x$ and find dy/dx when x = 2.", 14, "3x² + 2 = 12 + 2 = 14."),
         M("The derivative of $5x^4$ is:", "$20x^3$", ["$5x^3$", "$20x^4$", "$x^5$"], "Multiply by the power, reduce the power by 1."),
         N("Find the gradient of $y = x^2 - 6x$ at x = 5.", 4, "2x − 6 = 4.")],
        [N("The tangent to $y = x^2 + 3x$ at x = 1 has gradient m. Find the y-intercept of the tangent.", -1, "y = 4 at x = 1; m = 2x + 3 = 5; y − 4 = 5(x − 1) gives y = 5x − 1; intercept −1."),
         N("Differentiate $y = (3x - 2)^4$ and find the gradient at x = 1.", 12, "12(3x − 2)³ = 12 × 1 = 12.")],
        P("The curve is $y = x^2 - 4x + 7$.",
          [pn("Find dy/dx.", 0, "dy/dx = 2x − 4. Enter its value at x = 2.", pts=1),
           pn("Find the gradient at x = 5.", 6, "2(5) − 4 = 6.", pts=1),
           pn("Find the x-coordinate of the point where the gradient is 0.", 2, "2x − 4 = 0, so x = 2 (the minimum).", pts=2),
           pn("Find the y-coordinate there.", 3, "4 − 8 + 7 = 3: the minimum point is (2, 3).", pts=1)]),
        [("dy/dx of a constant is:", "0", ["1", "the constant", "undefined"], "Flat."),
         ("The derivative of x⁷:", "7x⁶", ["x⁶", "6x⁷", "7x⁷"], "Power rule."),
         ("The normal is ___ to the tangent:", "perpendicular", ["parallel", "equal", "the same"], "Definition."),
         ("d/dx of 3x is:", "3", ["3x", "0", "x"], "Gradient of the line."),
         ("The gradient at a minimum point is:", "0", ["1", "−1", "maximum"], "Stationary.")],
        minutes=30)

    s_ = lambda x: x ** 3 - 6 * x ** 2 + 9 * x; assert s_(1) == 4 and s_(3) == 0
    V = lambda x: x * (12 - 2 * x) ** 2; best = max(range(1, 6), key=V); assert best == 2 and V(2) == 128
    dA = 2 * math.pi * 10 * 0.5; assert abs(dA - 31.416) < 1e-3
    L = lesson(ch, "applications", "Applications of differentiation",
        ["Find stationary points and decide their nature.", "Solve maximum and minimum problems.", "Use rates of change and motion in a straight line."],
        [("methode", "Stationary points", "A stationary point has dy/dx = 0. To find its nature use the second derivative: if $\\frac{d^2y}{dx^2} > 0$ it is a **minimum**; if < 0 a **maximum**; if = 0 test the sign of dy/dx on either side."),
         ("methode", "Optimisation", "1. Write the quantity to optimise in terms of **one** variable.\n2. Differentiate and set the derivative to 0.\n3. Solve and confirm max or min.\n4. Answer in the context, with units."),
         ("propriete", "Motion in a line", "If s is the displacement at time t: velocity v = ds/dt and acceleration a = dv/dt. The particle is at rest when v = 0. Rate of change: if both variables depend on t, use $\\frac{dA}{dt} = \\frac{dA}{dr} \\times \\frac{dr}{dt}$."),
         ("pieges", "Common mistakes", "- Giving only x when the question asks for the value of y (or of the area).\n- Forgetting to test the nature of the point.\n- Mixing velocity and acceleration, or leaving out the unit.")],
        [("Example 1: stationary points", "Find the stationary points of $y = x^3 - 6x^2 + 9x$ and determine their nature.",
          ["dy/dx = 3x² − 12x + 9 = 3(x − 1)(x − 3) = 0, so x = 1 or x = 3.", "d²y/dx² = 6x − 12. At x = 1 it is −6 < 0 (maximum, y = 4). At x = 3 it is 6 > 0 (minimum, y = 0).", "Stationary points: maximum (1, 4) and minimum (3, 0)."], "**Maximum (1, 4); minimum (3, 0).**",
          plot(-0.5, 4.5, -2, 6, curves=[("x^3-6*x^2+9*x", "blue")], points=[(1, 4, "max", "red"), (3, 0, "min", "green")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: an open box", "A square sheet of cardboard 12 cm by 12 cm has squares of side x cut from the corners and the sides folded up to make an open box. Find x for the maximum volume and the volume.",
          ["V = x(12 − 2x)². Then dV/dx = (12 − 2x)² − 4x(12 − 2x) = (12 − 2x)(12 − 6x).", "dV/dx = 0 gives x = 6 (no box) or x = 2. So x = 2.", "V = 2 × 8² = 128 cm³. It is a maximum (V(1) = 100 and V(3) = 108 are smaller)."], "**x = 2 cm; V = 128 cm³.**")],
        [N("Find the x-coordinate of the stationary point of $y = x^2 - 8x + 3$.", 4, "2x − 8 = 0."),
         M("At a stationary point d²y/dx² = −5. The point is a:", "maximum", ["minimum", "point of inflexion only", "none"], "Negative second derivative."),
         N("A particle has s = t² + 3t. Find its velocity at t = 4.", 11, "v = 2t + 3 = 11.", unit="m/s")],
        [N("A circular ink spot grows so that its radius increases at 0.5 cm/s. Find the rate of increase of its area when r = 10 cm (to 1 d.p.).", round(dA, 1), "dA/dt = 2πr × dr/dt = 2π × 10 × 0.5 = 31.4 cm²/s.", tol=0.1, unit="cm²/s"),
         N("Two numbers add up to 20. Find the greatest possible product.", 100, "P = x(20 − x); P′ = 20 − 2x = 0 gives x = 10; P = 100.")],
        P("A farmer in Bafoussam has 80 m of fencing for a rectangular pen of width x m and length (40 − x) m.",
          [pm("The area A in terms of x is:", "$A = 40x - x^2$", ["$A = 80x$", "$A = 40 - x^2$", "$A = x^2 + 40$"], "A = x(40 − x).", 1),
           pn("Find the value of x giving the maximum area.", 20, "dA/dx = 40 − 2x = 0.", unit="m", pts=2),
           pn("Find the maximum area.", 400, "20 × 20 = 400 m².", unit="m²", pts=2)]),
        [("A stationary point has:", "dy/dx = 0", ["y = 0", "d²y/dx² = 0", "x = 0"], "Zero gradient."),
         ("Velocity is the derivative of:", "displacement", ["acceleration", "mass", "time"], "v = ds/dt."),
         ("Acceleration is the derivative of:", "velocity", ["displacement", "distance", "speed²"], "a = dv/dt."),
         ("d²y/dx² > 0 gives a:", "minimum", ["maximum", "inflexion", "root"], "Concave up."),
         ("For optimisation we first write the quantity in terms of:", "one variable", ["two variables", "no variable", "the answer"], "Then differentiate.")],
        minutes=35)

    assert Fraction(4) - Fraction(8, 3) == Fraction(4, 3) and (9 + 3) - (1 + 1) == 10 and Fraction(27, 3) == 9 and 2 * 1 + 3 == 5
    ox, oy, sxx, syy = 40, 190, 40, 14
    it = [LINE(ox, oy, ox + 160, oy, arrow="end"), LINE(ox, oy, ox, 20, arrow="end")]
    xs = [i / 20 * 3 for i in range(21)]
    it.append(POLY([ox, oy] + sum([[ox + x * sxx, oy - x * x * syy] for x in xs], []) + [ox + 3 * sxx, oy], fill="lightblue", stroke="blue"))
    it += [T(ox + 3 * sxx, oy + 18, "3", 14), T(ox - 10, oy + 6, "0", 14), T(ox + 60, oy - 40, "A = ∫x² dx = 9", 14, anchor="start"), T(ox + 3 * sxx + 6, oy - 9 * syy + 6, "y = x²", 14, anchor="start")]
    L = lesson(ch, "integration", "Integration and area under a curve",
        ["Integrate powers of x and (ax + b)ⁿ.", "Evaluate definite integrals and find areas under curves.", "Find a curve from its gradient, and displacement from velocity."],
        [("formule", "Indefinite and definite integrals", "Reverse of differentiation (n ≠ −1), with the constant C of integration; and the definite integral:", "\\int x^n\\,dx = \\frac{x^{n+1}}{n+1} + C \\qquad \\int_a^b f(x)\\,dx = F(b) - F(a)"),
         ("propriete", "Area", "The area between the curve y = f(x) and the x-axis from x = a to x = b is $\\int_a^b f(x)\\,dx$ when f(x) ≥ 0. For the area between two curves, integrate (upper − lower). Area below the x-axis counts negative: take its modulus."),
         ("methode", "Finding C", "Given the gradient dy/dx and a point on the curve: integrate to get y with + C, then substitute the point to find C. Likewise for velocity → displacement with the initial condition."),
         ("pieges", "Common mistakes", "- Forgetting + C in an indefinite integral.\n- Integrating $(2x + 1)^3$ as $(2x + 1)^4 \\div 4$: also divide by the 2 from the bracket.\n- Subtracting in the wrong order: F(b) − F(a), not F(a) − F(b).")],
        [("Example 1: integrate", "Find (a) $\\int (6x^2 - 4x + 3)\\,dx$ (b) $\\int_1^3 (2x + 1)\\,dx$ (c) the area under y = x² from x = 0 to 3.",
          ["(a) 2x³ − 2x² + 3x + C.", "(b) [x² + x] from 1 to 3 = (9 + 3) − (1 + 1) = 10.", "(c) [x³/3] from 0 to 3 = 27/3 = 9 square units."], "**(a) 2x³ − 2x² + 3x + C; (b) 10; (c) 9.**", shapes(it, 260, 215)),
         ("Example 2: between curves", "Find the area enclosed by y = x² and y = 2x, and a curve through (1, 5) with dy/dx = 4x.",
          ["Intersection: x² = 2x gives x = 0 and 2; the line is above. Area = ∫₀² (2x − x²) dx = [x² − x³/3] = 4 − 8/3 = 4/3.", "y = 2x² + C; at (1, 5): 5 = 2 + C, so C = 3.", "The curve is y = 2x² + 3."], "**Area 4/3; y = 2x² + 3.**")],
        [N("Evaluate $\\int_0^2 3x^2\\,dx$.", 8, "[x³] from 0 to 2 = 8."),
         M("$\\int 4x^3\\,dx =$", "$x^4 + C$", ["$12x^2 + C$", "$4x^4 + C$", "$x^4$"], "Add 1 to the power, divide by it; do not forget C."),
         N("Find the area under y = 2x from x = 1 to x = 4.", 15, "[x²] = 16 − 1 = 15 square units.")],
        [N("The gradient of a curve is 6x − 2 and it passes through (1, 3). Find its y-coordinate when x = 2.", 10, "y = 3x² − 2x + C; 3 = 3 − 2 + C gives C = 2. At x = 2: 12 − 4 + 2 = 10."),
         N("Find the area between y = x² and the x-axis from x = 1 to x = 2 (to 2 d.p.).", round(7 / 3, 2), "[x³/3] = 8/3 − 1/3 = 7/3 = 2.33 square units.", tol=0.01, unit="square units")],
        P("A particle moves in a straight line starting from the origin O with velocity v = 4t − 3 m/s at time t seconds.",
          [pm("Its displacement s from O is:", "$s = 2t^2 - 3t$", ["$s = 4t^2 - 3t$", "$s = 4$", "$s = 2t^2 - 3$"], "Integrate: 2t² − 3t + C, with C = 0 since s = 0 at t = 0.", 2),
           pn("Find s when t = 3.", 9, "2 × 9 − 9 = 9 m.", unit="m", pts=1),
           pn("At what time is the particle momentarily at rest (v = 0)?", 0.75, "4t − 3 = 0, so t = 0.75 s.", tol=0.001, unit="s", pts=2)]),
        [("∫ x² dx =", "x³/3 + C", ["2x + C", "x³ + C", "x²/2 + C"], "Add 1, divide by 3."),
         ("Differentiation and integration are:", "inverse operations", ["the same", "unrelated", "both give C"], "Fundamental link."),
         ("The area under y = 5 from x = 0 to 4 is:", "20", ["5", "9", "4"], "Rectangle 5 × 4."),
         ("∫ from a to b of f means:", "F(b) − F(a)", ["F(a) − F(b)", "F(a) + F(b)", "F(b) × F(a)"], "Definite integral."),
         ("A constant of integration is needed for:", "indefinite integrals", ["definite integrals", "no integral", "derivatives"], "+ C.")],
        minutes=35)
