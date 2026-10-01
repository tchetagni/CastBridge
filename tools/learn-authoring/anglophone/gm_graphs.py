from _kit import *
import math

def build(p):
    ch = p.chapter("graphs", "Functions and graphs", "O Level Mathematics — Functions and graphs (to be checked)")

    # ------------------------------------------------------------- linear graphs
    m = (11 - 2) / (4 - 1); assert m == 3 and 2 == 3 * 1 - 1 and 11 == 3 * 4 - 1
    assert math.isclose(math.hypot(6, 6), 6 * math.sqrt(2)) and abs(math.hypot(6, 6) - 8.49) < 0.01
    L = lesson(ch, "linear-graphs", "Straight-line graphs and gradient",
        ["Find the gradient of a line from two points and read it from y = mx + c.", "Find the equation of a line, parallel and perpendicular lines.", "Find the midpoint and the length of a line segment."],
        [("formule", "Gradient and equation", "For two points $(x_1, y_1)$ and $(x_2, y_2)$ the gradient is the rise over the run. A line with gradient m and y-intercept c has equation y = mx + c:", "m = \\frac{y_2 - y_1}{x_2 - x_1}"),
         ("propriete", "Parallel and perpendicular", "Parallel lines have the **same gradient**. Perpendicular lines have gradients whose product is −1: if one has gradient m, the other has $-\\frac{1}{m}$. A horizontal line has gradient 0; a vertical line has no gradient."),
         ("formule", "Midpoint and distance", "Midpoint M and length AB of the segment joining $(x_1, y_1)$ and $(x_2, y_2)$:", "M = \\left(\\frac{x_1 + x_2}{2}, \\frac{y_1 + y_2}{2}\\right) \\qquad AB = \\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}"),
         ("pieges", "Common mistakes", "- Subtracting coordinates in different orders: $\\frac{y_2 - y_1}{x_1 - x_2}$ gives the wrong sign.\n- Using gradient 'rise = horizontal': always vertical change over horizontal change.\n- A negative reciprocal means both flipping **and** changing the sign.")],
        [("Example 1: equation of a line", "Find the equation of the line through A(1, 2) and B(4, 11).",
          [("Gradient: $\\frac{11 - 2}{4 - 1} = 3$.", ""), ("Use y = 3x + c with A(1, 2): 2 = 3 + c, so c = −1.", "y = 3x - 1"), ("Check B: 3 × 4 − 1 = 11. Correct.", "")], "**y = 3x − 1.**",
          plot(-1, 6, -3, 13, curves=[("3*x-1", "blue", "y = 3x − 1")], points=[(1, 2, "A", "red"), (4, 11, "B", "red")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: parallel, perpendicular, midpoint", "(a) Line parallel to y = 2x + 5 through (3, 1). (b) Midpoint and length of the segment from P(2, −1) to Q(8, 5).",
          ["(a) Gradient 2: y = 2x + c; 1 = 6 + c, so c = −5: y = 2x − 5.", "(b) Midpoint: ((2 + 8)/2, (−1 + 5)/2) = (5, 2).", "(b) Length: $\\sqrt{6^2 + 6^2} = \\sqrt{72} = 6\\sqrt{2} \\approx 8.49$."], "**(a) y = 2x − 5; (b) (5, 2) and 6√2 ≈ 8.49.**")],
        [N("Find the gradient of the line through (2, 3) and (6, 11).", 2, "(11 − 3) ÷ (6 − 2) = 2."),
         M("The line y = −3x + 4 has gradient and y-intercept:", "−3 and 4", ["3 and 4", "4 and −3", "−3 and −4"], "Compare with y = mx + c."),
         N("Find the x-coordinate of the midpoint of (−4, 1) and (10, 7).", 3, "(−4 + 10) ÷ 2 = 3.")],
        [M("A line perpendicular to y = 2x + 1 has gradient:", "$-\\frac{1}{2}$", ["$\\frac{1}{2}$", "−2", "2"], "Product of gradients must be −1."),
         N("Find the distance between A(1, 1) and B(4, 5).", 5, "$\\sqrt{3^2 + 4^2} = 5$.")],
        P("A taxi-moto rider in Bafoussam charges a fixed 300 FCFA plus 100 FCFA per km. Graph: cost C against distance d.",
          [pm("The equation of the graph is:", "C = 100d + 300", ["C = 300d + 100", "C = 100d − 300", "C = 400d"], "Gradient 100, intercept 300.", 1),
           pn("What is the cost of a 12 km journey?", 1500, "100 × 12 + 300 = 1 500 FCFA.", unit="FCFA", pts=1),
           pn("The gradient of the graph is:", 100, "It is the cost per kilometre, 100 FCFA/km.", unit="FCFA/km", pts=1),
           pn("How many kilometres for a cost of 2 000 FCFA?", 17, "100d + 300 = 2000 gives d = 17.", unit="km", pts=1)]),
        [("The gradient of a horizontal line is:", "0", ["1", "undefined", "−1"], "No rise."),
         ("A line through the origin with gradient 4:", "y = 4x", ["y = x + 4", "y = 4", "x = 4"], "c = 0."),
         ("Lines y = 3x + 1 and y = 3x − 5 are:", "parallel", ["perpendicular", "identical", "intersecting at the origin"], "Same gradient."),
         ("The y-intercept of y = 2x − 7 is:", "−7", ["7", "2", "−2"], "x = 0."),
         ("Gradient through (0, 0) and (5, −10):", "−2", ["2", "−0.5", "0.5"], "−10 ÷ 5.")],
        minutes=30)

    # ------------------------------------------------------------- quadratic graphs
    f = lambda x: x * x - 2 * x - 3
    assert [f(x) for x in range(-2, 5)] == [5, 0, -3, -4, -3, 0, 5]
    assert abs(f(1 + math.sqrt(6)) - 2) < 1e-9
    r = (3 + math.sqrt(17)) / 2; assert abs((r * r - 3 * r - 2)) < 1e-9
    L = lesson(ch, "quadratic-graphs", "Quadratic graphs and graphical solutions",
        ["Draw the graph of a quadratic function from a table of values.", "Read roots, the turning point and the line of symmetry.", "Solve equations graphically by drawing a second graph."],
        [("definition", "The parabola", "The graph of $y = ax^2 + bx + c$ is a smooth U-shaped curve (**parabola**). If a > 0 it opens upwards with a minimum; if a < 0 it opens downwards with a maximum. It is symmetrical about a vertical line through its **turning point**, at $x = -\\frac{b}{2a}$."),
         ("methode", "Method: using the graph", "1. Make a table of values and plot the points; join with a **smooth curve**.\n2. The **roots** of $ax^2 + bx + c = 0$ are where the curve meets the x-axis.\n3. To solve $f(x) = k$, draw the line y = k and read the x-values where it cuts the curve.\n4. To solve $f(x) = mx + n$, draw that line too."),
         ("pieges", "Common mistakes", "- Joining points with straight segments: the curve must be smooth, flat at the turning point.\n- Reading only one root: there are usually two (symmetrical about the turning point).\n- Choosing a scale that is too small: use the whole sheet.")],
        [("Example 1: table and roots", "Complete the table for $y = x^2 - 2x - 3$ for x from −2 to 4 and find the roots, the minimum point and the solutions of $x^2 - 2x - 3 = 2$.",
          ["Values of y for x = −2, −1, 0, 1, 2, 3, 4: 5, 0, −3, −4, −3, 0, 5.", "Roots: the curve meets the x-axis at x = −1 and x = 3. Minimum point (1, −4).", "Line y = 2 cuts the curve where $x = 1 \\pm \\sqrt{6}$, i.e. x ≈ −1.45 and x ≈ 3.45."], "**Roots −1 and 3; minimum (1, −4); x ≈ −1.45 or 3.45.**",
          plot(-2, 4.5, -5, 6, curves=[("x^2-2*x-3", "blue", "y = x² − 2x − 3")], points=[(-1, 0, None, "red"), (3, 0, None, "red"), (1, -4, "min", "green")], segments=[(-2, 2, 4.5, 2, "orange", True, "y = 2")], grid=1, xlabel="x", ylabel="y")),
         ("Example 2: a second graph", "Using the same curve, which line should be drawn to solve $x^2 - 3x - 2 = 0$? Give the solutions to 1 d.p.",
          ["Start from $x^2 - 3x - 2 = 0$ and write it as $x^2 - 2x - 3 = x - 1$ (add x and subtract 1 on both sides). So draw y = x − 1.", "The line cuts the curve at x ≈ −0.6 and x ≈ 3.6 (exactly $\\frac{3 \\pm \\sqrt{17}}{2}$)."], "**Draw y = x − 1; x ≈ −0.6 or 3.6.**")],
        [N("For $y = x^2 - 2x - 3$ find y when x = −2.", 5, "4 + 4 − 3 = 5."),
         M("The turning point of $y = x^2 - 4x + 1$ has x-coordinate:", "2", ["−2", "4", "1"], "$x = -\\frac{b}{2a} = \\frac{4}{2}$."),
         TF("The graph of $y = -x^2 + 4$ has a maximum point.", True, "a < 0, so it opens downward; the maximum is (0, 4).")],
        [N("The curve $y = x^2 - 6x + 5$ has roots 1 and 5. Find the y-coordinate of its minimum point.", -4, "x = 3 by symmetry: 9 − 18 + 5 = −4."),
         M("Which line, drawn with $y = x^2 - x - 6$, solves $x^2 - x - 6 = 4$?", "y = 4", ["y = −2", "x = 4", "y = x + 4"], "Solve f(x) = 4: the horizontal line y = 4.")],
        P("A ball thrown from a roof in Buea has height h = −t² + 4t + 5 metres after t seconds.",
          [pn("Find h when t = 2.", 9, "−4 + 8 + 5 = 9 m.", unit="m", pts=1),
           pn("At what time is the height greatest?", 2, "t = −b/(2a) = −4/(−2) = 2 s.", unit="s", pts=1),
           pn("Find the time when the ball lands (h = 0, t > 0).", 5, "t² − 4t − 5 = (t − 5)(t + 1) = 0 gives t = 5 s.", unit="s", pts=2)]),
        [("A parabola with a > 0 opens:", "upwards", ["downwards", "to the right", "it is a line"], "Minimum point."),
         ("The roots of a quadratic are where its graph meets:", "the x-axis", ["the y-axis", "the origin", "its maximum"], "y = 0."),
         ("The line of symmetry of y = x² − 2x − 3 is:", "x = 1", ["x = −1", "y = 1", "x = 3"], "Midway between −1 and 3."),
         ("y = x² − 4: the y-intercept is:", "−4", ["4", "0", "2"], "x = 0."),
         ("A quadratic graph is drawn as:", "a smooth curve", ["straight segments", "a straight line", "isolated dots only"], "Join the points smoothly.")],
        minutes=30)

    # ------------------------------------------------------------- travel graphs
    area = 0.5 * (30 + 60) * 20; assert area == 900 and area / 60 == 15
    speed_fig = plot(0, 60, 0, 25, curves=[("2*x", "blue"), ("20", "blue"), ("20-(x-40)", "blue")], grid=10, xlabel="t (s)", ylabel="v (m/s)")
    for c, (lo, hi) in zip(speed_fig["curves"], [(0, 10), (10, 40), (40, 60)]): c.update({"from": lo, "to": hi})
    L = lesson(ch, "travel-graphs", "Distance-time and speed-time graphs",
        ["Interpret distance-time graphs: gradient = speed.", "Interpret speed-time graphs: gradient = acceleration, area = distance.", "Calculate average speed."],
        [("propriete", "Two kinds of graph", "**Distance-time**: gradient = speed; a horizontal line means stopped. **Speed-time**: gradient = acceleration (negative = deceleration); a horizontal line means constant speed; **area under the graph = distance travelled**."),
         ("formule", "Formulae", "Speed, distance and time, and average speed:", "\\text{speed} = \\frac{\\text{distance}}{\\text{time}} \\qquad \\text{average speed} = \\frac{\\text{total distance}}{\\text{total time}}"),
         ("pieges", "Common mistakes", "- Confusing the two graphs: on a speed-time graph a horizontal line is **not** stopped (it is steady speed).\n- Mixing units: km/h with minutes, m/s with km.\n- Average speed is not the average of two speeds unless times are equal.")],
        [("Example 1: speed-time graph", "A vehicle accelerates from rest to 20 m/s in 10 s, travels at 20 m/s for 30 s, then slows to rest in 20 s. Find the acceleration, the total distance and the average speed.",
          ["Acceleration = (20 − 0) ÷ 10 = 2 m/s².", "Distance = area of the trapezium = ½ × (30 + 60) × 20 = 900 m.", "Average speed = 900 ÷ 60 = 15 m/s."], "**2 m/s²; 900 m; 15 m/s.**",
          speed_fig),
         ("Example 2: distance-time graph", "A cyclist rides 24 km in 1.5 hours, rests for 0.5 hour, then rides back 24 km in 2 hours. Find the speed on each stage and the average speed for the whole trip.",
          ["Out: 24 ÷ 1.5 = 16 km/h. Rest: 0 km/h. Back: 24 ÷ 2 = 12 km/h.", "Total distance 48 km; total time 1.5 + 0.5 + 2 = 4 hours.", "Average speed = 48 ÷ 4 = 12 km/h (not (16 + 12) ÷ 2)."], "**16, 0 and 12 km/h; average 12 km/h.**")],
        [N("A car covers 180 km in 2.5 hours. Find its average speed.", 72, "180 ÷ 2.5 = 72.", unit="km/h"),
         M("On a distance-time graph, a horizontal line means:", "the object is stationary", ["constant speed", "acceleration", "going backwards"], "Distance does not change."),
         N("The speed rises from 4 m/s to 16 m/s in 3 s. Find the acceleration.", 4, "(16 − 4) ÷ 3 = 4 m/s².", unit="m/s²")],
        [N("Speed-time: constant 12 m/s for 20 s. Find the distance.", 240, "Area of rectangle 12 × 20 = 240 m.", unit="m"),
         N("A bus travels 60 km at 40 km/h then 60 km at 60 km/h. Find the average speed.", 48, "Times: 1.5 h and 1 h; total 120 km in 2.5 h = 48 km/h.", unit="km/h")],
        P("A train leaves a station: it accelerates uniformly from rest for 20 s reaching 15 m/s, runs at 15 m/s for 40 s, then decelerates uniformly to rest in 10 s.",
          [pn("Find the acceleration in the first 20 s.", 0.75, "15 ÷ 20 = 0.75 m/s².", tol=0.001, unit="m/s²", pts=1),
           pn("Find the deceleration in the last 10 s (as a positive number).", 1.5, "15 ÷ 10 = 1.5 m/s².", tol=0.001, unit="m/s²", pts=1),
           pn("Find the total distance.", 0.5 * (40 + 70) * 15, "Trapezium: ½ × (40 + 70) × 15 = 825 m.", unit="m", pts=2)]),
        [("The gradient of a distance-time graph gives:", "speed", ["acceleration", "distance", "time"], "Rise over run."),
         ("The area under a speed-time graph gives:", "distance", ["speed", "acceleration", "time"], "Speed × time."),
         ("A negative gradient on a speed-time graph shows:", "deceleration", ["acceleration", "constant speed", "stationary"], "Speed is falling."),
         ("Speed 20 m/s for 5 s gives distance:", "100 m", ["4 m", "25 m", "15 m"], "20 × 5."),
         ("To convert 36 km/h to m/s:", "10 m/s", ["36 m/s", "3.6 m/s", "100 m/s"], "36 × 1000 ÷ 3600.")],
        minutes=30)

    # ------------------------------------------------------------- regions
    pts = [(0, 0), (4, 0), (4, 2), (0, 6)]
    prof = [3 * x + 2 * y for x, y in pts]; assert prof == [0, 12, 16, 12]
    assert all(3 * 1 + 4 * 1 <= 12 for _ in [0])
    ox, oy, s = 40, 200, 28
    it = axes_fig(330, 230, ox, oy, s, s, (0, 8), (0, 6))
    it.append(POLY([ox + 0 * s, oy - 0 * s, ox + 4 * s, oy, ox + 4 * s, oy - 2 * s, ox, oy - 6 * s], fill="lightblue"))
    it += [T(ox - 10, oy + 6, "0", 12), T(ox + 4 * s, oy + 18, "4", 12), T(ox - 12, oy - 6 * s + 5, "6", 12),
           T(ox + 4 * s + 8, oy - 2 * s - 8, "(4, 2)", 12, anchor="start", color="red", bold=True), T(ox + 1.2 * s, oy - 2.2 * s, "x + y ≤ 6", 12, anchor="start"), T(ox + 4 * s + 6, oy - 5 * s, "x = 4", 12, anchor="start"),
           LINE(ox + 4 * s, oy, ox + 4 * s, oy - 6 * s, color="red", width=2), LINE(ox, oy - 6 * s, ox + 6 * s, oy, color="green", width=2)]
    L = lesson(ch, "regions", "Inequalities on a graph and linear programming",
        ["Draw a straight line from an inequality and shade the region.", "Write inequalities describing a region.", "Find the maximum or minimum of a quantity at a vertex of the region."],
        [("methode", "Method: shading a region", "1. Draw the boundary line: **solid** for ≤ or ≥, **dashed** for < or >.\n2. Pick a test point (the origin if it is not on the line) and test the inequality.\n3. If true, shade (or keep) the side containing the point; otherwise the other side.\n4. The region that satisfies **all** inequalities is where the shadings overlap."),
         ("propriete", "Linear programming", "To maximise or minimise a quantity such as P = 3x + 2y over a region bounded by lines, evaluate it at the **vertices** (corners) of the region: the largest and smallest values occur there."),
         ("pieges", "Common mistakes", "- Drawing a solid line for a strict inequality.\n- Choosing a test point that lies on the line.\n- Forgetting that x ≥ 0 and y ≥ 0 are often implied by the context (numbers of items).")],
        [("Example 1: describing a region", "The triangle with vertices (0, 0), (4, 0) and (0, 3) is shaded. Give three inequalities that describe it.",
          ["The sides on the axes give x ≥ 0 and y ≥ 0.", "The slanted side passes through (4, 0) and (0, 3): 3x + 4y = 12.", "The origin gives 0 ≤ 12, so the region is 3x + 4y ≤ 12."], "**x ≥ 0, y ≥ 0, 3x + 4y ≤ 12.**"),
         ("Example 2: linear programming", "A trader makes profit P = 3x + 2y (in thousands of FCFA) selling x boxes of cocoa and y boxes of coffee with x + y ≤ 6, x ≤ 4, x ≥ 0, y ≥ 0. Find the maximum profit.",
          ["The vertices of the region are (0, 0), (4, 0), (4, 2) and (0, 6).", "P at the vertices: 0, 12, 16, 12.", "The maximum is 16 at (4, 2): 16 000 FCFA."], "**16 000 FCFA with x = 4, y = 2.**",
          shapes(it, 330, 230))],
        [M("Does (1, 1) satisfy $3x + 4y \\leq 12$?", "Yes", ["No", "Only if x > y", "Only on the line"], "3 + 4 = 7 ≤ 12."),
         M("The line for y > 2x + 1 should be drawn:", "dashed", ["solid", "vertical", "thick only"], "Strict inequality: the boundary is not included."),
         N("Evaluate P = 3x + 2y at the vertex (4, 2).", 16, "12 + 4 = 16.")],
        [M("Which point is in the region x + y ≤ 6, x ≥ 0, y ≥ 0?", "(2, 3)", ["(4, 4)", "(−1, 2)", "(7, 0)"], "2 + 3 = 5 ≤ 6 and both coordinates positive; the others break one condition."),
         N("Find the minimum of C = 2x + 3y at the vertices (1, 4), (3, 2) and (6, 1).", min(2 * x + 3 * y for x, y in [(1, 4), (3, 2), (6, 1)]), "C = 14 at (1, 4), 12 at (3, 2) and 15 at (6, 1): the minimum is 12.")],
        P("A school in Bamenda buys x notebooks and y pens with x + y ≤ 10 and 200x + 100y ≤ 1500.",
          [pm("Which pair of numbers (x, y) is allowed?", "(5, 4)", ["(8, 5)", "(6, 6)", "(10, 2)"], "(5, 4): 9 ≤ 10 and 1 400 ≤ 1 500. (8, 5) has 13 items; (6, 6) has 12 items; (10, 2) costs 2 200.", 2),
           pn("What is the greatest number of notebooks if no pens are bought?", 7, "200x ≤ 1500 gives x ≤ 7.5, so 7 (and x ≤ 10 holds).", pts=2),
           pn("Maximum number of pens if no notebooks are bought?", 10, "y ≤ 10 and 100y ≤ 1500 gives y ≤ 15: the limit is 10.", pts=1)]),
        [("A solid boundary line is used for:", "≤ or ≥", ["< or >", "= only", "never"], "Included."),
         ("The test point most often used:", "the origin (0, 0)", ["(1, 1)", "(−5, 5)", "a point on the line"], "Easy to substitute, provided it is not on the line."),
         ("Linear programming: optimum values occur at:", "vertices", ["midpoints", "the origin only", "random points"], "Corners of the region."),
         ("x ≥ 0 means shade:", "right of the y-axis", ["left of the y-axis", "above the x-axis", "below the x-axis"], "x positive."),
         ("y < 3 is a:", "dashed horizontal line", ["solid vertical line", "dashed vertical line", "solid horizontal line"], "y = 3 is horizontal; strict inequality.")],
        minutes=30, notes=["Linear programming may be at the edge of the O Level syllabus: check."])

    # ------------------------------------------------------------- functions
    f = lambda x: 3 * x - 2; assert f(5) == 13 and f(4) == 10 and (10 + 2) / 3 == 4
    fg = lambda x: x * x + 4; gf = lambda x: (x + 4) ** 2
    assert gf(-1) == 9 and fg(3) == 13 and gf(-1.5) == fg(-1.5) == 6.25
    it = [RECT(40, 30, 90, 130, radius=20), RECT(230, 30, 90, 130, radius=20)]
    for i, (a, b) in enumerate([(1, 5), (2, 7), (3, 9)]):
        y = 60 + i * 40
        it += [T(85, y + 5, str(a), 16), T(275, y + 5, str(b), 16), LINE(105, y, 250, y, arrow="end", color="blue")]
    it += [T(85, 20, "domain", 13), T(275, 20, "range", 13), T(180, 185, "f(x) = 2x + 3", 15, bold=True)]
    L = lesson(ch, "functions", "Functions and mappings",
        ["Use function notation f(x) and evaluate functions.", "Find composite and inverse functions.", "State the domain and range of a simple function."],
        [("definition", "Function", "A **function** assigns to every input x of the **domain** exactly one output f(x); the set of outputs is the **range**. We write f(x) = 3x − 2 or f: x → 3x − 2. A function maps each input to one output only."),
         ("methode", "Composite and inverse", "- **fg(x)** means 'do g first, then f': fg(x) = f(g(x)). The order matters.\n- **Inverse** f⁻¹ undoes f: write y = f(x), make x the subject, then swap the names. Check that f⁻¹(f(x)) = x.\n- The graph of f⁻¹ is the reflection of the graph of f in the line y = x."),
         ("pieges", "Common mistakes", "- Reading fg as 'f first': g is applied first.\n- Confusing $f^{-1}(x)$ with $\\frac{1}{f(x)}$.\n- Forgetting that some inputs may be excluded: $\\frac{1}{x}$ is not defined at x = 0.")],
        [("Example 1: evaluate and invert", "f(x) = 3x − 2. Find f(5), the value of x when f(x) = 10, and the inverse function.",
          ["f(5) = 3 × 5 − 2 = 13.", "3x − 2 = 10 gives x = 4.", "Let y = 3x − 2, so x = (y + 2)/3. Hence f⁻¹(x) = (x + 2)/3; check f⁻¹(10) = 4."], "**f(5) = 13; x = 4; f⁻¹(x) = (x + 2)/3.**", shapes(it, 360, 200)),
         ("Example 2: composite functions", "f(x) = x + 4 and g(x) = x². Find gf(−1), fg(3), and solve fg(x) = gf(x).",
          ["gf(x) = g(x + 4) = (x + 4)², so gf(−1) = 9. fg(x) = x² + 4, so fg(3) = 13.", "Solve x² + 4 = (x + 4)² = x² + 8x + 16, so 8x = −12.", "x = −1.5. Check: both equal 6.25."], "**gf(−1) = 9; fg(3) = 13; x = −1.5.**")],
        [N("f(x) = 2x + 3. Find f(4).", 11, "2 × 4 + 3 = 11."),
         M("The inverse of f(x) = 2x + 3 is:", "$\\frac{x - 3}{2}$", ["$\\frac{x + 3}{2}$", "$2x - 3$", "$\\frac{1}{2x + 3}$"], "Subtract 3 then divide by 2: the reverse of the steps."),
         N("f(x) = x + 1, g(x) = 2x. Find fg(3).", 7, "g(3) = 6; f(6) = 7.")],
        [N("f(x) = x² − 1 with domain {−2, −1, 0, 1, 2}. How many different values are in the range?", 3, "f = 3, 0, −1, 0, 3: range {−1, 0, 3}, three values."),
         N("f(x) = 5 − 2x. Find f⁻¹(1).", 2, "x = (5 − y)/2 = (5 − 1)/2 = 2. Check f(2) = 1.")],
        P("A taxi fare in Yaoundé is f(d) = 500 + 150d FCFA for d km.",
          [pn("Find f(8).", 1700, "500 + 1 200 = 1 700 FCFA.", unit="FCFA", pts=1),
           pn("How many km for a fare of 2 000 FCFA (use the inverse)?", 10, "d = (F − 500)/150 = 1 500/150 = 10.", unit="km", pts=2),
           pm("The inverse function is:", "$f^{-1}(F) = \\frac{F - 500}{150}$", ["$f^{-1}(F) = \\frac{150}{F - 500}$", "$f^{-1}(F) = 150F + 500$", "$f^{-1}(F) = \\frac{F + 500}{150}$"], "Subtract 500 then divide by 150.", 1)]),
        [("f(x) = x², f(−3) =", "9", ["−9", "6", "−6"], "Square."),
         ("The domain is the set of:", "inputs", ["outputs", "gradients", "roots"], "Definition."),
         ("fg(x) means:", "apply g first", ["apply f first", "multiply f and g", "divide f by g"], "Right to left."),
         ("The graph of f⁻¹ is the reflection of f in:", "y = x", ["the x-axis", "the y-axis", "x = 1"], "Standard."),
         ("f(x) = 1/x is undefined at:", "x = 0", ["x = 1", "x = −1", "no value"], "Division by zero.")],
        minutes=30)
