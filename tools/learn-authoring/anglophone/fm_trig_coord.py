from _kit import *
import math
sin = lambda d: math.sin(math.radians(d)); cos = lambda d: math.cos(math.radians(d)); tan = lambda d: math.tan(math.radians(d))

def sols(f, lo=0, hi=360, step=0.5, target=0, tol=1e-6):
    """roots of f(x)=target on [lo,hi] in whole/half degrees (checked by evaluation)"""
    return [x / 1 for x in [lo + i * step for i in range(int((hi - lo) / step) + 1)] if abs(f(x) - target) < tol]

def build(p):
    ch = p.chapter("trig", "Trigonometry: identities, equations and radians", "O Level Further Mathematics — Trigonometry (to be checked)")
    chk(lambda t: (1 - cos(t) ** 2) / (sin(t) * cos(t)), lambda t: tan(t), pts=(20, 35, 50, 65, 80))
    assert abs(2 * (3 / 5) * (4 / 5) - 24 / 25) < 1e-12 and abs((4 / 5) ** 2 - (3 / 5) ** 2 - 7 / 25) < 1e-12
    r1 = sols(lambda x: 2 * sin(x) ** 2 + cos(x), target=2); assert r1 == [60.0, 90.0, 270.0, 300.0], r1
    assert sols(lambda x: sin(x), target=0.5) == [30.0, 150.0]
    assert sols(lambda x: cos(x), target=-0.5) == [120.0, 240.0]
    assert sols(lambda x: sin(2 * x), target=0.5) == [15.0, 75.0, 195.0, 255.0]
    fig = plot(0, 360, -1.4, 1.4, curves=[("sin(x*pi/180)", "blue", "y = sin x")], segments=[(0, 0.5, 360, 0.5, "orange", True, "y = 0.5")], points=[(30, 0.5, None, "red"), (150, 0.5, None, "red")], grid=90, xlabel="x°", ylabel="y")
    L = lesson(ch, "identities-equations", "Trigonometric identities and equations",
        ["Use sin²θ + cos²θ = 1, tan θ = sin θ ÷ cos θ and the double-angle formulae.", "Prove simple identities.", "Solve trigonometric equations in a given interval."],
        [("formule", "Identities", "True for every angle θ:", "\\sin^2\\theta + \\cos^2\\theta = 1 \\qquad \\tan\\theta = \\frac{\\sin\\theta}{\\cos\\theta}"),
         ("formule", "Double angles", "Useful for equations and for exact values:", "\\sin 2\\theta = 2\\sin\\theta\\cos\\theta \\qquad \\cos 2\\theta = \\cos^2\\theta - \\sin^2\\theta = 2\\cos^2\\theta - 1"),
         ("methode", "Solving equations in 0° ≤ x ≤ 360°", "1. Reduce to one trig function (use sin² = 1 − cos² if needed) and factorise as a quadratic.\n2. Find the **principal value** with the calculator.\n3. Use the symmetry of the graph: sin x = k gives x and 180 − x; cos x = k gives x and 360 − x; tan x = k gives x and x + 180.\n4. Keep only the solutions in the interval (for sin 2x the interval for 2x is doubled)."),
         ("pieges", "Common mistakes", "- Dividing both sides by sin x (or cos x): you lose the solutions where it is 0; factorise instead.\n- Giving only the principal value.\n- $\\sin^2\\theta$ means $(\\sin\\theta)^2$, not sin(θ²).")],
        [("Example 1: identity and ratios", "Show that $\\frac{1 - \\cos^2\\theta}{\\sin\\theta\\cos\\theta} = \\tan\\theta$. If sin θ = 3/5 (θ acute) find cos θ, tan θ and sin 2θ.",
          ["Numerator: $1 - \\cos^2\\theta = \\sin^2\\theta$, so the fraction is $\\frac{\\sin^2\\theta}{\\sin\\theta\\cos\\theta} = \\frac{\\sin\\theta}{\\cos\\theta} = \\tan\\theta$.", "cos θ = √(1 − 9/25) = 4/5 and tan θ = 3/4.", "sin 2θ = 2 × 3/5 × 4/5 = 24/25."], "**Identity proved; cos θ = 4/5, tan θ = 3/4, sin 2θ = 24/25.**", fig),
         ("Example 2: an equation", "Solve $2\\sin^2 x + \\cos x = 2$ for 0° ≤ x ≤ 360°.",
          ["Replace sin² x by 1 − cos² x: $2 - 2\\cos^2 x + \\cos x = 2$, so $\\cos x(1 - 2\\cos x) = 0$.", "cos x = 0 gives x = 90° or 270°. cos x = 1/2 gives x = 60° or 300°.", "Check x = 60°: 2(0.75) + 0.5 = 2."], "**x = 60°, 90°, 270°, 300°.**")],
        [M("sin²θ + cos²θ =", "1", ["0", "2", "tan θ"], "Basic identity."),
         N("Solve sin x = 0.5 for 0° ≤ x ≤ 180° and enter the larger solution.", 150, "x = 30° or 180 − 30 = 150°.", unit="°"),
         N("If cos θ = 0.6 (θ acute), find sin θ.", 0.8, "sin² = 1 − 0.36 = 0.64.", tol=0.001)],
        [N("How many solutions has sin 2x = 0.5 for 0° ≤ x ≤ 360°?", 4, "2x = 30, 150, 390, 510 so x = 15, 75, 195, 255."),
         N("Solve tan x = −1 for 0° ≤ x ≤ 360°; enter the smaller solution.", 135, "Principal value −45°; add 180° to get 135°, and 315°.", unit="°")],
        P("The depth of water at a jetty in Limbe is modelled by h = 3 + 2 sin(30t)° metres, t hours after midnight (0 ≤ t ≤ 12).",
          [pn("Find h when t = 3.", 5, "sin 90° = 1: h = 5 m.", unit="m", pts=1),
           pn("Find the least value of h.", 1, "sin ≥ −1, so h ≥ 1 m.", unit="m", pts=1),
           pn("Find the first time when h = 4 (hours).", 1, "sin(30t) = 0.5 gives 30t = 30, so t = 1 (also t = 5).", unit="h", pts=3)]),
        [("tan θ equals:", "sin θ ÷ cos θ", ["cos θ ÷ sin θ", "sin θ × cos θ", "1 − sin θ"], "Definition."),
         ("sin 2θ equals:", "2 sin θ cos θ", ["2 sin θ", "sin²θ", "sin θ + cos θ"], "Double-angle."),
         ("cos x = k has solutions x and:", "360° − x", ["180° − x", "180° + x", "90° − x"], "Cosine symmetry."),
         ("sin(30°) =", "0.5", ["0.866", "1", "0.707"], "Standard value."),
         ("Maximum value of 3 sin x:", "3", ["1", "−3", "6"], "Amplitude 3.")],
        minutes=35, notes=["Double-angle formulae and trigonometric equations of quadratic form: confirm the syllabus level; the tide model is invented for practice (not real data)."])

    arc = 0.9 * 8; sec = 0.5 * 64 * 0.9; seg = 0.5 * 64 * (0.9 - math.sin(0.9))
    assert abs(arc - 7.2) < 1e-12 and abs(sec - 28.8) < 1e-12 and abs(seg - 3.7335) < 1e-3
    assert abs(150 * math.pi / 180 - 5 * math.pi / 6) < 1e-12 and abs(math.degrees(2) - 114.59) < 0.01
    sx, sy, R, th = 90, 150, 110, 52
    ex, ey = sx + R, sy; fx_, fy_ = sx + R * cos(th), sy - R * sin(th)
    fig = shapes([PATH("M %d %d L %d %d A %d %d 0 0 0 %.1f %.1f Z" % (sx, sy, ex, ey, R, R, fx_, fy_), fill="lightblue"),
                  T(sx + 60, sy - 12, "θ", 18, bold=True), T(sx + 55, sy + 20, "r", 16), T(sx + R - 20, sy - 70, "arc l", 14, color="red")], 260, 190)
    L = lesson(ch, "radians", "Radians, arcs and sectors",
        ["Convert between degrees and radians.", "Use l = rθ and A = ½r²θ.", "Find the area of a segment and use exact values."],
        [("definition", "The radian", "One **radian** is the angle at the centre of a circle subtended by an arc equal in length to the radius. A full turn is 2π radians, so 180° = π radians. Convert: multiply degrees by π/180, or radians by 180/π."),
         ("formule", "Arc, sector, segment", "For a circle of radius r and angle θ in **radians**:", "l = r\\theta \\qquad A_{sector} = \\frac{1}{2}r^2\\theta \\qquad A_{segment} = \\frac{1}{2}r^2(\\theta - \\sin\\theta)"),
         ("propriete", "Exact values", "sin(π/6) = 1/2, cos(π/6) = √3/2, sin(π/4) = cos(π/4) = √2/2, sin(π/3) = √3/2, cos(π/3) = 1/2, tan(π/4) = 1. Keep the calculator in **radian mode** for radian questions."),
         ("pieges", "Common mistakes", "- Using the formulae l = rθ and A = ½r²θ with θ in degrees.\n- Calculator in the wrong mode: sin(0.9) in degree mode gives 0.0157.\n- Writing π = 180 instead of π radians = 180°.")],
        [("Example 1: conversions", "Express 150° in radians (in terms of π) and 2 radians in degrees (2 d.p.).",
          ["150° = 150 × π/180 = 5π/6 ≈ 2.618 rad.", "2 rad = 2 × 180/π ≈ 114.59°."], "**5π/6 rad; 114.59°.**", fig),
         ("Example 2: sector and segment", "A sector has radius 8 cm and angle 0.9 radians. Find the arc length, the sector area and the area of the segment cut off by the chord (2 d.p.).",
          ["Arc l = rθ = 8 × 0.9 = 7.2 cm. Sector A = ½ × 64 × 0.9 = 28.8 cm².", "Triangle area = ½ r² sin θ = 32 × sin 0.9 = 32 × 0.7833 = 25.07 cm².", "Segment = 28.8 − 25.07 ≈ 3.73 cm²."], "**7.2 cm; 28.8 cm²; 3.73 cm².**")],
        [N("Convert 90° to radians and enter the answer as a decimal (to 2 d.p.).", 1.57, "π/2 = 1.5708.", tol=0.01),
         N("An arc of a circle of radius 5 cm subtends an angle of 1.4 rad. Find the arc length.", 7, "l = 5 × 1.4 = 7 cm.", unit="cm"),
         M("π/3 radians equals:", "60°", ["30°", "90°", "120°"], "180° ÷ 3.")],
        [N("A sector of radius 10 cm has area 60 cm². Find θ in radians.", 1.2, "½ × 100 × θ = 60, so θ = 1.2.", tol=0.001),
         N("A sector has radius 6 cm and arc length 9 cm. Find the area of the sector.", 27, "θ = 9/6 = 1.5; A = ½ × 36 × 1.5 = 27 cm².", unit="cm²")],
        P("A circular fan blade of radius 12 cm in a market stall in Douala turns through an angle of 150°.",
          [pn("Write 150° in radians (decimal, 3 d.p.).", round(5 * math.pi / 6, 3), "150 × π/180 = 2.618.", tol=0.001, pts=1),
           pn("Find the length of the arc traced by the tip of the blade (to 1 d.p.).", round(12 * 5 * math.pi / 6, 1), "l = 12 × 2.618 = 31.4 cm.", tol=0.1, unit="cm", pts=2),
           pn("Find the area swept (to 1 d.p.).", round(0.5 * 144 * 5 * math.pi / 6, 1), "A = ½ × 144 × 2.618 = 188.5 cm².", tol=0.1, unit="cm²", pts=2)]),
        [("π radians equals:", "180°", ["90°", "360°", "57.3°"], "Half a turn."),
         ("Arc length with θ in radians:", "rθ", ["r²θ", "½rθ", "θ/r"], "Definition."),
         ("1 radian is about:", "57.3°", ["180°", "3.14°", "100°"], "180/π."),
         ("The area of a sector:", "½r²θ", ["rθ", "πr²θ", "r²θ²"], "Radians."),
         ("2π radians is:", "a full turn", ["a quarter turn", "half a turn", "10°"], "360°.")],
        minutes=30)

    # ================================================================ coordinate geometry
    ch = p.chapter("coordinates", "Coordinate geometry", "O Level Further Mathematics — Coordinate geometry (to be checked)")
    A, B = (1, 2), (5, 6); M_ = ((A[0] + B[0]) / 2, (A[1] + B[1]) / 2); assert M_ == (3, 4) and (B[1] - A[1]) / (B[0] - A[0]) == 1
    assert 4 == -3 + 7 and 12 == 0.5 * abs(0 * (0 - 4) + 6 * (4 - 0) + 2 * (0 - 0))
    ox, oy, s = 40, 175, 24
    px = lambda x: ox + x * s; py = lambda y: oy - y * s
    it = axes_fig(300, 200, ox, oy, s, s, (0, 8), (0, 6))
    it += [LINE(px(1), py(2), px(5), py(6), color="blue", width=3), LINE(px(0), py(7), px(7), py(0), color="red", width=2, dash=True), CIRCLE(px(3), py(4), 5, fill="green", stroke="green"),
           T(px(1) - 8, py(2) + 16, "A", 14), T(px(5) + 8, py(6) - 6, "B", 14), T(px(3) + 16, py(4) - 4, "M", 14, anchor="start"), T(px(5.0), py(2.9), "y = −x + 7", 12, anchor="start", color="red")]
    L = lesson(ch, "lines", "Straight lines: gradient, equation and perpendiculars",
        ["Find the equation of a line in the form y − y₁ = m(x − x₁).", "Find parallel and perpendicular lines and perpendicular bisectors.", "Find the area of a triangle from its vertices."],
        [("formule", "Equations of a line", "Through (x₁, y₁) with gradient m, and through two points:", "y - y_1 = m(x - x_1) \\qquad m = \\frac{y_2 - y_1}{x_2 - x_1}"),
         ("propriete", "Parallel and perpendicular", "Parallel lines: m₁ = m₂. Perpendicular lines: m₁ × m₂ = −1. The **perpendicular bisector** of AB passes through the midpoint of AB with gradient the negative reciprocal of the gradient of AB."),
         ("formule", "Area of a triangle", "For vertices (x₁, y₁), (x₂, y₂), (x₃, y₃):", "\\text{Area} = \\frac{1}{2}\\left|x_1(y_2 - y_3) + x_2(y_3 - y_1) + x_3(y_1 - y_2)\\right|"),
         ("pieges", "Common mistakes", "- Forgetting the absolute value in the area formula (the area is positive).\n- Using the gradient of AB instead of its negative reciprocal for the bisector.\n- Mixing the order of subtraction: use the same order in the numerator and the denominator.")],
        [("Example 1: perpendicular bisector", "Find the equation of the perpendicular bisector of A(1, 2) and B(5, 6).",
          ["Midpoint M = ((1 + 5)/2, (2 + 6)/2) = (3, 4). Gradient of AB = (6 − 2)/(5 − 1) = 1.", "Perpendicular gradient = −1. Use y − 4 = −1(x − 3).", "So y = −x + 7. Check: it passes through (3, 4) since 4 = −3 + 7."], "**y = −x + 7.**", shapes(it, 300, 200)),
         ("Example 2: area", "Find the area of the triangle with vertices (0, 0), (6, 0) and (2, 4).",
          ["Base along the x-axis = 6; height = 4.", "Using the formula: ½|0(0 − 4) + 6(4 − 0) + 2(0 − 0)| = ½ × 24.", "Area = 12 square units."], "**12 square units.**")],
        [N("Find the gradient of the line through (1, 5) and (4, 17).", 4, "12 ÷ 3 = 4."),
         M("A line perpendicular to y = 2x + 3 has gradient:", "−1/2", ["1/2", "−2", "2"], "Negative reciprocal."),
         N("Find the y-intercept of the line through (2, 7) with gradient 3.", 1, "y − 7 = 3(x − 2): y = 3x + 1.")],
        [N("Find k so that the line through (1, 2) and (3, k) is parallel to y = 3x − 1.", 8, "Gradient (k − 2)/2 = 3, so k = 8."),
         N("Find the area of the triangle with vertices (1, 1), (5, 1) and (3, 6).", 10, "Base 4, height 5: ½ × 4 × 5 = 10.")],
        P("Points P(−1, 3), Q(5, 1) and R(2, 7) are the vertices of a triangle.",
          [pm("The midpoint of PQ is:", "(2, 2)", ["(3, 2)", "(2, 4)", "(4, 1)"], "((−1 + 5)/2, (3 + 1)/2).", 1),
           pn("Find the gradient of PQ.", -1 / 3, "(1 − 3)/(5 + 1) = −1/3.", tol=0.001, pts=2),
           pn("Find the area of triangle PQR.", 15, "½|(−1)(1 − 7) + 5(7 − 3) + 2(3 − 1)| = ½|6 + 20 + 4| = 15 square units.", tol=0.001, pts=2)]),
        [("The gradient of a vertical line is:", "undefined", ["0", "1", "−1"], "No run."),
         ("m₁ × m₂ = −1 means the lines are:", "perpendicular", ["parallel", "equal", "horizontal"], "Property."),
         ("The midpoint of (0, 0) and (8, 6):", "(4, 3)", ["(8, 6)", "(3, 4)", "(2, 1.5)"], "Half the sum."),
         ("A line through (0, 3) with gradient 2:", "y = 2x + 3", ["y = 3x + 2", "y = 2x − 3", "x = 2"], "Intercept 3."),
         ("The area formula gives a value that is:", "taken positive", ["always negative", "always 0", "a gradient"], "Absolute value.")],
        minutes=30)

    # circle
    assert 3 ** 2 + 2 ** 2 + 12 == 25 and 36 + 4 - 36 + 8 - 12 == 0 and 3 * 6 + 4 * 2 == 26
    assert 1 + 9 - 16 == -6 and 4 + 1 + 4 == 9 and 9 + 16 == 25 and 15 + 20 == 35 and 3 * 3 + 4 * 5 > 0
    ox, oy, s = 60, 92, 18
    px = lambda x: ox + x * s; py = lambda y: oy - y * s
    it = axes_fig(260, 240, ox, oy, s, s, (-3, 9), (-7, 4), step=1)
    it += [CIRCLE(px(3), py(-2), 5 * s, stroke="blue", width=3), CIRCLE(px(3), py(-2), 3, fill="ink"), CIRCLE(px(6), py(2), 4, fill="red", stroke="red"),
           LINE(px(3), py(-2), px(6), py(2), color="green", width=2), LINE(px(4.2), py(3.4), px(8), py(0.55), color="red", width=2),
           T(px(3) - 14, py(-2) + 16, "C", 14), T(px(6) + 8, py(2) - 8, "P", 14, anchor="start"), T(px(7.4), py(2.4), "tangent", 12, color="red", anchor="start")]
    L = lesson(ch, "circles", "The circle: equation, tangent and intersections",
        ["Write the equation of a circle from its centre and radius, and find the centre and radius from the general equation.", "Find the equation of a tangent at a point on a circle.", "Decide whether a point is inside, on or outside a circle."],
        [("formule", "Equation of a circle", "Centre (a, b), radius r, and the general form with centre (−g, −f) and r² = g² + f² − c:", "(x - a)^2 + (y - b)^2 = r^2 \qquad x^2 + y^2 + 2gx + 2fy + c = 0"),
         ("methode", "Tangent at a point P", "1. Find the centre C and the gradient of the radius CP.\n2. The tangent is perpendicular to CP: its gradient is the **negative reciprocal**.\n3. Use y − y₁ = m(x − x₁) with P.\n4. If CP is horizontal the tangent is vertical (and vice versa)."),
         ("propriete", "Position of a point", "Compute (x − a)² + (y − b)² for the point and compare with r²: equal means **on** the circle, smaller means **inside**, larger means **outside**."),
         ("pieges", "Common mistakes", "- Reading the centre as (−3, 2) instead of (3, −2): the signs are changed in the **bracket form**.\n- Using r instead of r² on the right-hand side.\n- Giving the gradient of the radius instead of the tangent.")],
        [("Example 1: centre and radius", "Find the centre and radius of $x^2 + y^2 - 6x + 4y - 12 = 0$.",
          ["Complete the squares: $(x - 3)^2 + (y + 2)^2 = 12 + 9 + 4 = 25$.", "Centre (3, −2) and r² = 25, so r = 5."], "**Centre (3, −2); radius 5.**", shapes(it, 260, 240)),
         ("Example 2: tangent", "Show that P(6, 2) lies on this circle and find the equation of the tangent at P.",
          ["(6 − 3)² + (2 + 2)² = 9 + 16 = 25 = r²: P is on the circle.", "Gradient of CP = (2 + 2)/(6 − 3) = 4/3, so the tangent has gradient −3/4.", "y − 2 = −(3/4)(x − 6), i.e. 4y − 8 = −3x + 18, so 3x + 4y = 26."], "**3x + 4y = 26.**")],
        [M("The circle $(x - 1)^2 + (y + 3)^2 = 16$ has centre:", "(1, −3)", ["(−1, 3)", "(1, 3)", "(16, 16)"], "Change the sign of the numbers in brackets."),
         N("Find the radius of $x^2 + y^2 - 4x + 2y - 4 = 0$.", 3, "(x − 2)² + (y + 1)² = 9."),
         M("Is the point (5, 3) inside, on or outside $(x - 2)^2 + (y + 1)^2 = 9$?", "outside", ["inside", "on the circle", "cannot tell"], "9 + 16 = 25 > 9.")],
        [N("A circle with centre (2, 1) passes through (5, 5). Find its radius.", 5, "√(9 + 16) = 5."),
         N("Find the equation of the tangent at (5, 5) to this circle in the form 3x + 4y = k and enter k.", 35, "CP gradient 4/3, tangent −3/4: 3x + 4y = 15 + 20 = 35.")],
        P("A circular flower bed in a park in Buea has centre C(2, 1) (units in metres) and passes through P(5, 5).",
          [pn("Find the radius.", 5, "CP = √(3² + 4²) = 5 m.", unit="m", pts=1),
           pm("The equation of the circle is:", "$(x - 2)^2 + (y - 1)^2 = 25$", ["$(x + 2)^2 + (y + 1)^2 = 25$", "$(x - 2)^2 + (y - 1)^2 = 5$", "$x^2 + y^2 = 25$"], "Centre (2, 1) and r² = 25.", 2),
           pn("Find the area of the bed (to 1 d.p.).", round(math.pi * 25, 1), "π × 25 = 78.5 m².", tol=0.1, unit="m²", pts=2)]),
        [("The standard form of a circle uses:", "r²", ["r", "2r", "πr"], "Radius squared."),
         ("Centre of $x^2 + y^2 + 2x - 4y - 4 = 0$:", "(−1, 2)", ["(1, −2)", "(2, −4)", "(−2, 4)"], "(−g, −f)."),
         ("A tangent is ___ to the radius at the contact point:", "perpendicular", ["parallel", "equal", "twice"], "Property."),
         ("The circle x² + y² = 49 has radius:", "7", ["49", "24.5", "3.5"], "√49."),
         ("The point (3, 4) on x² + y² = 25 is:", "on the circle", ["inside", "outside", "the centre"], "9 + 16 = 25.")],
        minutes=30)
