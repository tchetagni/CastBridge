"""Generates form3-maths (run: python3 form3-maths.py)."""
import sys
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from sm_common import *

REF = "Cameroon secondary (general education) Mathematics programme, Form 3 (MINESEC) — to be checked against the official programme"
p = mkpack(3, "", REF, "Third year of secondary mathematics: factorisation, simultaneous equations, inequalities, graphs, Pythagoras, trigonometry, circles, solids, probability, transformations, matrices and commercial arithmetic.")

# ===================================================================== ALGEBRA
ch = p.chapter("algebra", "Algebra", REF)

for X in range(-6, 7):
    assert X**2 + 7*X + 12 == (X + 3) * (X + 4) and X**2 - 5*X - 14 == (X - 7) * (X + 2) and X**2 - 49 == (X - 7) * (X + 7)
    assert 2*X**2 + 5*X + 3 == (2*X + 3) * (X + 1) and 3*X**2 + 10*X + 8 == (3*X + 4) * (X + 2)
for A_, X, Y in [(1, 2, 3), (4, -1, 5), (2, 7, -3)]:
    assert 2*X + 2*Y + A_*X + A_*Y == (2 + A_) * (X + Y)
assert 20**2 - 49 == 13 * 27 == 351
dsq = shapes([RECT(60, 30, 150, 150, fill="lightblue", width=2), RECT(150, 30, 60, 60, fill="white", width=2), T(105, 110, "x²", 18), T(180, 65, "7²", 14), T(135, 22, "x", 13), T(222, 110, "x", 13, anchor="start"),
              T(200, 205, "x² − 49 = (x − 7)(x + 7)", 14)], 400, 220)
lesson(ch, "factorisation", "Factorisation",
  ["Factorise by grouping", "Factorise a difference of two squares", "Factorise quadratic trinomials of the form x² + bx + c and ax² + bx + c", "Use factorisation to simplify and calculate"],
  [("methode", "Grouping and difference of squares", "**Grouping**: factorise pairs of terms, then the common bracket. 2x + 2y + ax + ay = 2(x + y) + a(x + y) = (2 + a)(x + y). **Difference of two squares**: $a^2 - b^2 = (a - b)(a + b)$, for example $x^2 - 49 = (x - 7)(x + 7)$ and $9y^2 - 4 = (3y - 2)(3y + 2)$."),
   ("methode", "Trinomials x² + bx + c", "Find two numbers that **multiply to c** and **add to b**. For $x^2 + 7x + 12$: 3 × 4 = 12 and 3 + 4 = 7, so $(x + 3)(x + 4)$. For $x^2 - 5x - 14$: −7 × 2 = −14 and −7 + 2 = −5, so $(x - 7)(x + 2)$. If c is negative the numbers have different signs."),
   ("methode", "Trinomials ax² + bx + c", "Multiply a × c, find two numbers that multiply to ac and add to b, split the middle term and group. For $2x^2 + 5x + 3$: ac = 6, the numbers are 2 and 3, so $2x^2 + 2x + 3x + 3 = 2x(x + 1) + 3(x + 1) = (2x + 3)(x + 1)$."),
   ("pieges", "Common mistakes", "- Writing $x^2 + 49 = (x + 7)(x + 7)$: a **sum** of squares does not factorise this way, and $(x+7)^2 = x^2 + 14x + 49$.\n- Forgetting to take out a common factor first: $2x^2 - 8 = 2(x - 2)(x + 2)$.\n- Not checking by expanding the answer.")],
  [("Example 1: difference of two squares", "A square plot of side 20 m has a square corner of side 7 m taken for a path. Find the remaining area by factorising x² − 49 with x = 20.",
    ["Remaining area = x² − 7² = (x − 7)(x + 7).", "With x = 20: 13 × 27 = 351.", "Check: 400 − 49 = 351 m²."], "**(x − 7)(x + 7); 351 m²**", dsq),
   ("Example 2: trinomials", "Factorise (a) x² + 7x + 12, (b) x² − 5x − 14, (c) 2x² + 5x + 3.",
    ["(a) 3 × 4 = 12 and 3 + 4 = 7: (x + 3)(x + 4).", "(b) −7 × 2 = −14 and −7 + 2 = −5: (x − 7)(x + 2).", "(c) ac = 6; 2 and 3 add to 5: 2x² + 2x + 3x + 3 = 2x(x + 1) + 3(x + 1) = (2x + 3)(x + 1)."], "**(x + 3)(x + 4); (x − 7)(x + 2); (2x + 3)(x + 1)**")],
  [M("Factorise x² − 25.", "(x − 5)(x + 5)", ["(x − 5)²", "(x + 5)²", "x(x − 25)"], "Difference of two squares."),
   M("Factorise x² + 5x + 6.", "(x + 2)(x + 3)", ["(x + 1)(x + 6)", "(x − 2)(x − 3)", "(x + 5)(x + 1)"], "2 × 3 = 6 and 2 + 3 = 5."),
   N("Evaluate 52² − 48² by factorising (answer).", 400, "(52 − 48)(52 + 48) = 4 × 100 = 400.")],
  [M("Factorise 3x² + 10x + 8.", "(3x + 4)(x + 2)", ["(3x + 2)(x + 4)", "(3x + 8)(x + 1)", "(x + 4)(x + 2)"], "ac = 24; 4 and 6 add to 10: 3x² + 4x + 6x + 8 = x(3x + 4) + 2(3x + 4)."),
   O("Factorise 2x² − 18 completely and explain the order of your steps.", "First take out the common factor 2: 2(x² − 9). Then x² − 9 is a difference of two squares: 2(x − 3)(x + 3).", "1 mark for taking out 2, 1 mark for recognising the difference of squares, 1 mark for the final answer.", points=3)],
  P("A rectangular piece of land at Bafoussam has area x² + 9x + 20 m².",
    [pm("Factorise the area expression.", "(x + 4)(x + 5)", ["(x + 2)(x + 10)", "(x + 1)(x + 20)", "(x − 4)(x − 5)"], "4 × 5 = 20 and 4 + 5 = 9.", 1),
     pn("When x = 6 the width is x + 4 m. Find the length x + 5 (m).", 11, "6 + 5 = 11.", unit="m", pts=1),
     pn("Find the area when x = 6 (m²).", 110, "10 × 11 = 110, and 36 + 54 + 20 = 110.", unit="m²", pts=2)]),
  [("x² − 9 factorises as:", "(x − 3)(x + 3)", ["(x − 3)²", "(x − 9)(x + 1)", "x(x − 9)"], "Difference of two squares."),
   ("x² + 6x + 8 equals:", "(x + 2)(x + 4)", ["(x + 1)(x + 8)", "(x + 3)(x + 5)", "(x − 2)(x − 4)"], "2 × 4 = 8; 2 + 4 = 6."),
   ("The first step in factorising 5x² − 20 is to:", "take out the common factor 5", ["add 20", "divide by x", "square it"], "5(x² − 4)."),
   ("x² − 4x + 4 factorises as:", "(x − 2)²", ["(x + 2)²", "(x − 4)(x − 1)", "(x − 2)(x + 2)"], "−2 × −2 = 4 and −2 + −2 = −4."),
   ("After factorising a trinomial, we check by:", "expand the brackets", ["adding the terms", "dividing by x", "taking a square root"], "The expansion must give the original.")],
  ill=(dsq, "A square x by x with a 7 by 7 corner removed: x² − 49.", "A big square with a smaller square cut from the top-right corner."),
  notes=["The completing the square method and the quadratic formula are Form 4 content."])

assert 7 + 3 == 10 and 7 - 3 == 4 and 2 * 3 + 3 * 2 == 12 and 3 * 3 - 2 == 7 and 2 * 400 + 3 * 700 == 2900 and 4 * 400 + 700 == 2300 and 3 * 5000 + 2 * 3000 == 21000 and 5000 + 4 * 3000 == 17000
sg = plot(0, 12, -5, 12, curves=[("10-x", "blue", "x + y = 10"), ("x-4", "red", "x − y = 4")], points=[(7, 3, "(7, 3)", "green")], grid=1, xlabel="x", ylabel="y")
lesson(ch, "simultaneous-equations", "Simultaneous equations",
  ["Solve two linear equations by elimination", "Solve by substitution", "Solve by drawing the graphs", "Form simultaneous equations from word problems"],
  [("definition", "What are simultaneous equations?", "Two equations with two unknowns, such as x + y = 10 and x − y = 4, are solved **simultaneously** when we find the pair of values (x, y) that makes both true. Graphically the solution is the point where the two straight lines **cross**."),
   ("methode", "Elimination", "Make the coefficients of one letter equal (multiply an equation if needed). If the signs are different, **add** the equations; if they are the same, **subtract**. Solve for the remaining letter, then substitute back. x + y = 10 and x − y = 4: add to get 2x = 14, so x = 7 and y = 3."),
   ("methode", "Substitution", "Make one letter the subject of one equation and replace it in the other. For 2x + 3y = 12 and 3x − y = 7: from the second y = 3x − 7. Then 2x + 3(3x − 7) = 12, so 11x = 33 and x = 3, y = 2. Always **check in both original equations**."),
   ("pieges", "Common mistakes", "- Adding when the signs are the same (you must subtract to eliminate).\n- Multiplying only one side of an equation by a number.\n- Finding x but forgetting to find y, or checking in only one equation.")],
  [("Example 1: elimination and graph", "Solve x + y = 10 and x − y = 4.",
    ["Add the equations: 2x = 14, so x = 7.", "Substitute in x + y = 10: y = 3.", "Check in the second: 7 − 3 = 4. The graphs cross at (7, 3)."], "**x = 7, y = 3**", sg),
   ("Example 2: a shopping problem", "Two pens and three books cost 2 900 FCFA. Four pens and one book cost 2 300 FCFA. Find the price of a pen and of a book.",
    ["Let a pen cost p and a book b: 2p + 3b = 2 900 and 4p + b = 2 300.", "From the second, b = 2 300 − 4p. So 2p + 6 900 − 12p = 2 900, giving −10p = −4 000 and p = 400.", "b = 2 300 − 1 600 = 700. Check: 800 + 2 100 = 2 900."], "**Pen 400 FCFA, book 700 FCFA.**")],
  [N("Solve x + y = 9 and x − y = 3: find x.", 6, "Add: 2x = 12, x = 6."),
   N("Solve 2x + y = 11 and x + y = 7: find y.", 3, "Subtract: x = 4, so y = 3.", ),
   M("The solution of y = x + 1 and y = 3 − x is:", "(1, 2)", ["(2, 1)", "(1, 1)", "(2, 3)"], "x + 1 = 3 − x gives x = 1 and y = 2.")],
  [N("Solve 3x + 2y = 16 and 5x − 2y = 8: find x.", 3, "Add: 8x = 24, x = 3; then y = 3.5."),
   O("Solve 2x + 3y = 12 and 3x − y = 7 by substitution, showing the check.", "y = 3x − 7. Then 2x + 9x − 21 = 12 gives 11x = 33, so x = 3 and y = 2. Check: 6 + 6 = 12 and 9 − 2 = 7.", "1 mark for y = 3x − 7, 1 mark for x = 3, 1 mark for y = 2 and the check.", points=3)],
  P("At a market in Bafoussam, 3 bags of cassava and 2 bags of maize cost 21 000 FCFA; 1 bag of cassava and 4 bags of maize cost 17 000 FCFA.",
    [pm("Which pair of equations is correct (c = cassava, m = maize)?", "3c + 2m = 21 000 and c + 4m = 17 000", ["3c + 2m = 17 000 and c + 4m = 21 000", "3c + 4m = 21 000 and c + 2m = 17 000", "2c + 3m = 21 000 and 4c + m = 17 000"], "Read each purchase carefully.", 1),
     pn("Find the price of a bag of maize (FCFA).", 3000, "c = 17 000 − 4m; 51 000 − 12m + 2m = 21 000, so m = 3 000.", unit="FCFA", pts=2),
     pn("Find the price of a bag of cassava (FCFA).", 5000, "c = 17 000 − 12 000 = 5 000.", unit="FCFA", pts=2)]),
  [("In elimination, if the signs of a letter are different we:", "add the equations", ["subtract them", "divide them", "square them"], "Opposite signs cancel when added."),
   ("The solution of simultaneous equations is:", "the point where the lines cross", ["the gradient", "the origin", "the y-intercept"], "Both equations hold there."),
   ("Solve x + y = 5, x − y = 1:", "x = 3, y = 2", ["x = 2, y = 3", "x = 4, y = 1", "x = 6, y = −1"], "Add: 2x = 6."),
   ("After finding x we must:", "find y and check in both equations", ["stop", "square x", "divide by y"], "A pair of values is needed."),
   ("Parallel lines give:", "no solution", ["one solution", "two solutions", "infinitely many solutions always"], "They never meet.")],
  ill=(sg, "The lines x + y = 10 and x − y = 4 cross at (7, 3).", "A graph with two straight lines crossing at the point (7, 3)."),
  notes=["Simultaneous equations with one quadratic equation are left for Form 4."])

assert [i for i in range(-10, 11) if -3 < 2 * i + 1 <= 9] == [-1, 0, 1, 2, 3, 4]
pts_ = [(x, y) for x in range(0, 8) for y in range(0, 8) if x >= 1 and y >= 1 and x + y <= 6]; assert len(pts_) == 15
ax = Axes(0, 7, 0, 7, w=380, h=300, xticks=range(0, 8), yticks=range(0, 8))
ax.poly([(1, 1), (5, 1), (1, 5)], fill="lightblue", color="blue")
ax.text(4.35, 3.4, "x + y = 6", 12)
for (a_, b_) in [(1, 1), (2, 2), (3, 2)]: ax.pt(a_, b_, None, "red", r=3)
regf = ax.fig()
lesson(ch, "inequality-regions", "Compound inequalities and regions on a graph",
  ["Solve compound inequalities", "Draw boundary lines for inequalities in x and y", "Shade the region that satisfies several inequalities", "Use integer points in a region to solve problems"],
  [("methode", "Compound inequalities", "Solve all parts at once: $-3 < 2x + 1 \\leq 9$. Subtract 1 from every part: $-4 < 2x \\leq 8$. Divide every part by 2: $-2 < x \\leq 4$. The integers that satisfy it are −1, 0, 1, 2, 3, 4. Reverse **both** signs if you divide by a negative number."),
   ("methode", "Regions", "To show $y \\leq 2x + 1$ on a graph: draw the **boundary line** y = 2x + 1 (a **solid** line for ≤ or ≥, a **dashed** line for < or >). Pick a **test point** such as (0, 0): if it satisfies the inequality, shade that side; if not, shade the other side."),
   ("methode", "Several inequalities", "The solution of a **set** of inequalities is the part of the plane that satisfies all of them: shade (or leave unshaded) consistently and mark the **common region**. A point on a solid boundary is included. In problems, only **whole-number points** may make sense (you cannot buy half a sack)."),
   ("pieges", "Common mistakes", "- Dashed vs solid lines swapped.\n- Not testing a point: guessing which side to shade.\n- Counting a point on a dashed boundary as part of the region.")],
  [("Example 1: compound inequality", "Solve −3 < 2x + 1 ≤ 9 and list the integer solutions.",
    ["Subtract 1 from each part: −4 < 2x ≤ 8.", "Divide each part by 2: −2 < x ≤ 4.", "Integers: −1, 0, 1, 2, 3, 4, so 6 integers."], "**−2 < x ≤ 4; six integers.**"),
   ("Example 2: a region", "A trader can carry at most 6 sacks in total, with at least 1 sack of maize (x) and at least 1 sack of beans (y). List the conditions and find how many whole-number combinations are possible.",
    ["Conditions: x ≥ 1, y ≥ 1 and x + y ≤ 6. The region is a triangle with vertices (1, 1), (5, 1), (1, 5).", "Count the whole-number points by x: x = 1 gives 5, x = 2 gives 4, x = 3 gives 3, x = 4 gives 2, x = 5 gives 1.", "Total = 5 + 4 + 3 + 2 + 1 = 15 combinations."], "**15 combinations.**", regf)],
  [M("Solve 1 < x + 3 < 6:", "−2 < x < 3", ["4 < x < 9", "−2 < x < 6", "1 < x < 3"], "Subtract 3 from every part."),
   N("How many integers satisfy −1 ≤ x < 4?", 5, "−1, 0, 1, 2, 3."),
   M("The inequality y > 2x is drawn with a:", "dashed boundary line", ["solid boundary line", "curved boundary line", "no boundary line"], "Strict inequalities use a dashed line.")],
  [N("Solve 3 ≤ 2x − 1 < 11 and give the largest integer solution.", 5, "4 ≤ 2x < 12, so 2 ≤ x < 6; the largest integer is 5."),
   O("Explain how to decide which side of the line y = x to shade for y < x.", "Draw y = x as a dashed line. Test a point not on it, such as (1, 0): 0 < 1 is true, so shade the side containing (1, 0), below the line.", "1 mark for the dashed line, 1 mark for the test point, 1 mark for the correct side.", points=3)],
  P("A hall in Buea can hold at most 6 tables. The organiser needs at least 1 round table (x) and at least 1 square table (y).",
    [pm("Which inequality describes the limit on tables?", "x + y ≤ 6", ["x + y ≥ 6", "x − y ≤ 6", "xy ≤ 6"], "At most 6 tables in total.", 1),
     pn("How many combinations use exactly 6 tables?", 5, "x + y = 6 with x, y ≥ 1: x = 1 to 5.", pts=2),
     pn("How many combinations use 6 tables or fewer (with the minimum of one of each)?", 15, "5 + 4 + 3 + 2 + 1 = 15.", pts=2)], figure=regf),
  [("On an inequality region graph, a solid boundary line is used for:", "≤ and ≥", ["< and >", "= only", "no inequalities"], "The points on the line are included."),
   ("Solve 2 < 3x ≤ 12:", "2/3 < x ≤ 4", ["2 < x ≤ 12", "6 < x ≤ 36", "0 < x ≤ 4"], "Divide every part by 3."),
   ("To decide which side to shade we use:", "a test point", ["a ruler only", "the gradient only", "the y-intercept only"], "Test a point that is not on the line."),
   ("The integers in −2 ≤ x < 1 are:", "−2, −1, 0", ["−2, −1, 0, 1", "−1, 0", "−3, −2, −1, 0"], "1 is excluded."),
   ("The region for a set of inequalities is:", "where all are true", ["where any is true", "where none is true", "the origin only"], "Common region.")],
  ill=(regf, "The triangle x ≥ 1, y ≥ 1, x + y ≤ 6 with a few integer points.", "A shaded triangle with vertices (1, 1), (5, 1) and (1, 5) on a grid."),
  notes=["Linear programming (maximising profit) is not included."])

for X in range(-1, 6): assert X**2 - 4*X + 3 == (X - 1) * (X - 3)
assert [x*x - 4*x + 3 for x in range(-1, 6)] == [8, 3, 0, -1, 0, 3, 8] and [6*t - t*t for t in range(0, 7)] == [0, 5, 8, 9, 8, 5, 0]
qg = plot(-1.5, 5.5, -2, 9, curves=[("x^2-4*x+3", "blue", "y = x² − 4x + 3")], points=[(1, 0, "1", "red"), (3, 0, "3", "red"), (2, -1, "min (2, −1)", "green")], grid=1, xlabel="x", ylabel="y")
bg = plot(0, 7, -1, 11, curves=[("6*x-x^2", "blue", "h = 6t − t²")], points=[(3, 9, "max", "green")], grid=1, xlabel="t (s)", ylabel="h (m)")
lesson(ch, "quadratic-graphs", "Graphs of quadratic functions",
  ["Complete a table of values and plot y = ax² + bx + c", "Describe the shape, axis of symmetry, turning point and roots", "Read solutions of equations from the graph", "Use a quadratic graph to solve a problem"],
  [("definition", "Quadratic graphs", "A function $y = ax^2 + bx + c$ (a not 0) has a smooth U-shaped curve called a **parabola**. If a > 0 it opens upwards with a **minimum** turning point; if a < 0 it opens downwards with a **maximum**. It is **symmetrical** about a vertical line through the turning point."),
   ("methode", "Drawing", "Make a table of values with whole numbers of x, plot the points, and join them with a **smooth curve** (not with a ruler). For y = x² − 4x + 3: x = −1, 0, 1, 2, 3, 4, 5 give y = 8, 3, 0, −1, 0, 3, 8. The curve cuts the x-axis at x = 1 and x = 3 (the **roots**) and its minimum point is (2, −1)."),
   ("methode", "Reading the graph", "The solutions of x² − 4x + 3 = 0 are the x-values where the curve meets the x-axis (y = 0). To solve x² − 4x + 3 = 3, draw the line y = 3 and read where it meets the curve: x = 0 and x = 4. The axis of symmetry is halfway between the roots: x = 2."),
   ("pieges", "Common mistakes", "- Joining points with straight segments.\n- A pointed bottom: the curve must be smooth and round.\n- Mis-calculating negative squares: (−1)² = 1, so y = 1 + 4 + 3 = 8 at x = −1.")],
  [("Example 1: table and roots", "For y = x² − 4x + 3 find the roots, the turning point and the axis of symmetry from the graph.",
    ["The curve crosses the x-axis at 1 and 3: roots 1 and 3.", "The lowest point is (2, −1): a minimum.", "The axis of symmetry is x = 2, halfway between 1 and 3."], "**Roots 1 and 3; minimum (2, −1); axis x = 2.**", qg),
   ("Example 2: a thrown ball", "A ball thrown from the ground at Limbe beach has height h = 6t − t² metres after t seconds. Find the maximum height and when it lands.",
    ["Table for t = 0 to 6: h = 0, 5, 8, 9, 8, 5, 0.", "The highest point is at t = 3 s with h = 9 m.", "It lands when h = 0 again, at t = 6 s."], "**Maximum 9 m at 3 s; lands after 6 s.**", bg)],
  [N("Find y when x = −2 in y = x² − 4x + 3.", 15, "4 + 8 + 3 = 15."),
   M("The curve y = x² + 1 has a:", "minimum turning point", ["maximum turning point", "no turning point", "straight section"], "a = 1 is positive."),
   N("The roots of a curve are 2 and 8. Find the x-coordinate of the axis of symmetry.", 5, "Halfway: (2 + 8) ÷ 2 = 5.")],
  [N("For y = 6x − x², find y when x = 4.", 8, "24 − 16 = 8."),
   O("Explain how to use the graph of y = x² − 4x + 3 to solve x² − 4x + 3 = 3.", "Draw the horizontal line y = 3 and read the x-coordinates where it cuts the curve: x = 0 and x = 4.", "1 mark for the line y = 3, 1 mark for reading the intersections, 1 mark for the two values.", points=3)],
  P("A footballer at Yaoundé kicks a ball that follows h = 6t − t² (h metres, t seconds).",
    [pn("Find the height at t = 2 s (m).", 8, "12 − 4 = 8.", unit="m", pts=1),
     pn("Find the maximum height (m).", 9, "At t = 3: 18 − 9 = 9.", unit="m", pts=2),
     pn("For how many seconds is the ball in the air?", 6, "h = 0 at t = 0 and t = 6.", unit="s", pts=2)], figure=bg),
  [("The graph of y = x² has the shape of:", "a parabola", ["a straight line", "a circle", "a triangle"], "U-shaped curve."),
   ("A quadratic with a < 0 has:", "a maximum point", ["a minimum point", "no turning point", "two turning points"], "Opens downwards."),
   ("The roots of a quadratic graph are where it crosses:", "the x-axis", ["the y-axis", "the origin only", "the line y = x"], "y = 0."),
   ("The axis of symmetry of y = x² − 4x + 3 is:", "x = 2", ["x = 3", "x = 1", "y = 2"], "Halfway between 1 and 3."),
   ("Points on a quadratic graph are joined with:", "a smooth curve", ["straight lines", "dashes", "arrows"], "Smooth curve.")],
  ill=(qg, "The parabola y = x² − 4x + 3 with roots 1 and 3 and minimum (2, −1).", "A U-shaped curve crossing the x-axis at 1 and 3 with its lowest point at (2, −1)."),
  notes=["Solving by formula and completing the square is Form 4; here only graphical and factorised roots appear."])

# ===================================================================== GEOMETRY
ch = p.chapter("geometry", "Pythagoras, trigonometry, circles and solids", REF)

assert 12**2 + 5**2 == 13**2 and abs(2.5**2 - 0.7**2 - 2.4**2) < 1e-9 and 8**2 + 15**2 == 17**2 and math.hypot(6, 8) == 10 and 120**2 + 50**2 == 130**2 and 120 + 50 - 130 == 40
lad = right_tri(0.7, 2.4, "0.7 m", "h", "ladder 2.5 m", scale=60, ox=100)
lad["items"][0] = POLY([100, 190, 142, 190, 100, 46], fill="lightyellow")
lad["items"][2:] = [T(121, 210, "0.7 m", 13), T(86, 118, "h", 14, anchor="end"), T(135, 110, "ladder 2.5 m", 13, anchor="start")]
rt_ = right_tri(150, 80, "a", "b", "c (hypotenuse)", scale=1, ox=60)
lesson(ch, "pythagoras", "Pythagoras' theorem",
  ["State and use Pythagoras' theorem", "Find a missing side of a right-angled triangle", "Use the converse to test for a right angle", "Solve practical problems and find distances between points"],
  [("formule", "Pythagoras' theorem", "In a right-angled triangle the square of the **hypotenuse** (the side opposite the right angle, the longest side) equals the sum of the squares of the other two sides:", "a^2 + b^2 = c^2"),
   ("methode", "Method", "Draw the triangle and mark the hypotenuse. To find the **hypotenuse**: add the squares, then take the square root. To find a **shorter side**: subtract the squares, then take the square root. Examples: 5² + 12² = 169, so c = 13. And 2.5² − 0.7² = 6.25 − 0.49 = 5.76, so the side is 2.4."),
   ("propriete", "Converse and triples", "If the sides of a triangle satisfy $a^2 + b^2 = c^2$, the triangle is right-angled. Whole-number sets are called **Pythagorean triples**: (3, 4, 5), (5, 12, 13), (8, 15, 17), (7, 24, 25). For points, the distance between (x₁, y₁) and (x₂, y₂) is $\\sqrt{(x_2 - x_1)^2 + (y_2 - y_1)^2}$."),
   ("pieges", "Common mistakes", "- Adding the squares when looking for a shorter side (you must subtract).\n- Forgetting the square root at the end.\n- Using the theorem on a triangle with no right angle.")],
  [("Example 1: a ladder", "A ladder 2.5 m long leans against a wall with its foot 0.7 m from the wall. How high does it reach?",
    ["The ladder is the hypotenuse: h² = 2.5² − 0.7².", "h² = 6.25 − 0.49 = 5.76.", "h = 2.4 m."], "**2.4 m**", lad),
   ("Example 2: a field", "A rectangular field at Bamenda is 120 m by 50 m. Find the diagonal and how much shorter it is to walk along the diagonal than along two sides.",
    ["Diagonal² = 120² + 50² = 14 400 + 2 500 = 16 900, so the diagonal is 130 m.", "Along two sides: 120 + 50 = 170 m.", "Saving = 170 − 130 = 40 m."], "**130 m; saves 40 m.**")],
  [N("A right-angled triangle has shorter sides 6 cm and 8 cm. Find the hypotenuse.", 10, "36 + 64 = 100, so 10 cm.", unit="cm"),
   N("The hypotenuse is 13 cm and one side 5 cm. Find the other side.", 12, "169 − 25 = 144, so 12 cm.", unit="cm"),
   M("Which set of lengths forms a right-angled triangle?", "8, 15, 17", ["6, 7, 8", "5, 10, 12", "9, 10, 14"], "8² + 15² = 289 = 17².")],
  [N("Find the distance between A(1, 2) and B(7, 10).", 10, "√(6² + 8²) = 10.", ),
   O("A triangle has sides 7 cm, 24 cm and 25 cm. Is it right-angled? Show your working.", "7² + 24² = 49 + 576 = 625 = 25². So the converse of Pythagoras' theorem shows it is right-angled, with the right angle opposite the 25 cm side.", "1 mark for each square, 1 mark for the comparison and conclusion.", points=3)],
  P("A telephone mast at Kumba is held by a steel cable from the top to a point on the ground 9 m from the base. The mast is 12 m high.",
    [pn("Find the length of the cable (m).", 15, "√(9² + 12²) = √225 = 15.", unit="m", pts=2),
     pn("The cable is 15 m. A second mast of height 20 m has its cable fixed 15 m from its base. Find that cable length (m).", 25, "√(15² + 20²) = √625 = 25.", unit="m", pts=2),
     pm("Which triple do both masts follow?", "Multiples of (3, 4, 5)", ["Multiples of (5, 12, 13)", "Multiples of (8, 15, 17)", "No triple"], "9-12-15 and 15-20-25 are 3-4-5 scaled.", 1)]),
  [("The hypotenuse is:", "the longest side opposite the right angle", ["the shortest side", "any side", "the side next to the right angle"], "Definition."),
   ("In a triangle with sides 3, 4, 5 the hypotenuse is:", "5", ["3", "4", "12"], "5² = 3² + 4²."),
   ("To find a shorter side you:", "subtract the squares and take the square root", ["add the squares", "multiply the sides", "divide by 2"], "c² − b² = a²."),
   ("A triangle with sides 5, 12, 13 is:", "right-angled", ["isosceles", "equilateral", "not possible"], "25 + 144 = 169."),
   ("Pythagoras' theorem applies to:", "right-angled triangles only", ["all triangles", "circles", "rectangles only"], "Right angle needed.")],
  ill=(rt_, "A right-angled triangle with legs a and b and hypotenuse c: a² + b² = c².", "A right-angled triangle with the sides labelled a, b and the hypotenuse c."),
  notes=["Surds are only touched lightly (square roots); full surd simplification is outside this lesson."])

th = 35; hgt = 30 * tand(th); assert abs(hgt - 21.0) < 0.01
a30 = 10 * sind(30); b30 = 10 * cosd(30); assert abs(a30 - 5) < 1e-9 and abs(b30 - 8.66) < 0.01
ang_ = math.degrees(math.asin(3.5 / 4)); assert abs(ang_ - 61.04) < 0.01
ramp = math.degrees(math.asin(0.2)); assert abs(ramp - 11.54) < 0.01
trf = right_tri(200, 110, "adjacent", "opposite", "hypotenuse", scale=1, ox=60)
trf["items"].append(T(222, 180, "θ", 15, color="red", bold=True))
elf = shapes([LINE(300, 180, 300, 55, width=3), LINE(60, 180, 300, 180, width=2), LINE(60, 180, 300, 55, color="red", dash=True, width=2), T(112, 172, "35°", 13, color="red", bold=True),
              T(312, 120, "h", 15, anchor="start"), T(180, 200, "30 m", 13), T(50, 195, "eye", 12, anchor="end")], 400, 220)
lesson(ch, "right-trigonometry", "Trigonometry in right-angled triangles",
  ["Name the sides opposite, adjacent and hypotenuse", "Use sine, cosine and tangent to find sides", "Use the inverse functions to find angles", "Solve problems with angles of elevation and depression"],
  [("formule", "The three ratios", "Label the sides from the angle θ you are using. The ratios are (remember SOH-CAH-TOA):", "\\sin \\theta = \\frac{\\text{opp}}{\\text{hyp}} \\qquad \\cos \\theta = \\frac{\\text{adj}}{\\text{hyp}} \\qquad \\tan \\theta = \\frac{\\text{opp}}{\\text{adj}}"),
   ("methode", "Finding a side or an angle", "To find a **side**: choose the ratio that has the known side and the wanted side, write the equation and solve. For example sin 30° = x ÷ 10 gives x = 5. To find an **angle**: use the inverse key: if sin θ = 0.875 then θ = sin⁻¹(0.875) ≈ 61.0°. Make sure the calculator is in **degree** mode."),
   ("definition", "Elevation and depression", "The **angle of elevation** is the angle **above** the horizontal when you look up at an object. The **angle of depression** is the angle **below** the horizontal when you look down. The two angles are equal for the same line of sight, because they are alternate angles."),
   ("pieges", "Common mistakes", "- Calling a side 'opposite' without checking which angle you are using.\n- Calculator in radian mode: sin 30° must give 0.5.\n- Rounding too early: keep 3 or 4 digits until the final step.")],
  [("Example 1: a tower", "From a point 30 m from the base of a tower at Bafoussam, the angle of elevation of the top is 35°. Find the height (to 3 s.f.).",
    ["The known side (30 m) is adjacent and the wanted side is opposite: use tan.", "tan 35° = h ÷ 30, so h = 30 × tan 35° = 30 × 0.7002.", "h ≈ 21.0 m."], "**About 21.0 m.**", elf),
   ("Example 2: angles", "A 4 m ladder reaches 3.5 m up a wall. Find the angle it makes with the ground. A 6 m market ramp rises 1.2 m: find the angle of the ramp.",
    ["Ladder: sin θ = 3.5 ÷ 4 = 0.875, so θ = sin⁻¹ 0.875 ≈ 61.0°.", "Ramp: sin θ = 1.2 ÷ 6 = 0.2, so θ = sin⁻¹ 0.2 ≈ 11.5°."], "**About 61.0°; about 11.5°.**", trf)],
  [N("In a right triangle the hypotenuse is 10 cm and the angle is 30°. Find the opposite side.", 5, "10 × sin 30° = 5 cm.", unit="cm"),
   M("tan θ equals:", "opposite ÷ adjacent", ["adjacent ÷ hypotenuse", "opposite ÷ hypotenuse", "hypotenuse ÷ adjacent"], "TOA."),
   N("If sin θ = 0.5, find θ in degrees.", 30, "sin⁻¹ 0.5 = 30°.", unit="°")],
  [N("A girl 50 m from a tree sees the top at an angle of elevation of 40°. Find the height of the tree (m, to 1 d.p.).", 42, "50 × tan 40° = 50 × 0.8391 = 41.96 m.", tol=0.1, unit="m"),
   O("Explain what SOH-CAH-TOA helps you remember and use it to choose the ratio when you know the hypotenuse and want the adjacent side.", "It helps to remember sin = opposite/hypotenuse, cos = adjacent/hypotenuse, tan = opposite/adjacent. With the hypotenuse and the adjacent side we use cosine.", "1 mark for the three ratios, 1 mark for cosine, 1 mark for the reason.", points=3)],
  P("A surveyor at Limbe looks up at the top of a lighthouse 80 m away on level ground. The angle of elevation is 25°. Her eye is 1.5 m above the ground.",
    [pn("Find the height of the lighthouse above her eye level (m, 1 d.p.).", 37.3, "80 × tan 25° = 37.3 m.", tol=0.1, unit="m", pts=2),
     pn("Find the total height of the lighthouse (m, 1 d.p.).", 38.8, "37.3 + 1.5 = 38.8 m.", tol=0.1, unit="m", pts=2),
     pm("What is the angle of depression from the top back to her eye?", "25°", ["65°", "90°", "155°"], "Alternate angles are equal.", 1)]),
  [("sin θ is:", "opposite ÷ hypotenuse", ["adjacent ÷ hypotenuse", "opposite ÷ adjacent", "hypotenuse ÷ opposite"], "SOH."),
   ("The angle of elevation is measured:", "above the horizontal", ["below the horizontal", "from the vertical", "from the ground at the object"], "Definition."),
   ("cos 60° equals:", "0.5", ["1", "0.866", "0"], "Standard value."),
   ("To find an angle from its sine we use:", "sin⁻¹", ["sin", "cos", "tan"], "Inverse function."),
   ("In a right triangle with tan θ = 1 the angle is:", "45°", ["30°", "60°", "90°"], "Opposite equals adjacent.")],
  ill=(trf, "A right-angled triangle with θ, opposite, adjacent and hypotenuse labelled.", "A right-angled triangle showing the adjacent side along the base, the opposite side vertical and the hypotenuse."),
  notes=["Values like tan 35° are from calculator tables; the content assumes degrees mode. Check whether four-figure tables are still used in exams."])

pi7 = 22 / 7
assert abs(60 / 360 * 2 * pi7 * 21 - 22) < 1e-9 and abs(60 / 360 * pi7 * 441 - 231) < 1e-9 and abs(11 / (2 * pi7 * 7) * 360 - 90) < 1e-9 and abs(90 / 360 * pi7 * 196 - 154) < 1e-9 and abs(22 + 28 - 50) < 1e-9
sc1 = sector_fig(90, 60, "r = 21 cm", "60°")
lesson(ch, "arcs-sectors", "Arc length and area of a sector",
  ["Find the length of an arc", "Find the area of a sector", "Find the perimeter of a sector", "Find the angle or the radius from given arc length or area"],
  [("formule", "Arc and sector", "A sector with angle θ at the centre of a circle of radius r is the fraction $\\frac{\\theta}{360}$ of the whole circle. Therefore:", "\\text{arc} = \\frac{\\theta}{360} \\times 2 \\pi r \\qquad \\text{area} = \\frac{\\theta}{360} \\times \\pi r^2"),
   ("methode", "Method", "Write the fraction θ/360 first and simplify it. Example: r = 21 cm and θ = 60° (π = 22/7): fraction 1/6. Arc = 1/6 × 2 × 22/7 × 21 = 22 cm. Area = 1/6 × 22/7 × 441 = 231 cm². The **perimeter** of a sector is the arc **plus the two radii**: 22 + 21 + 21 = 64 cm."),
   ("methode", "Working backwards", "If the arc is known, solve for θ: θ = arc ÷ (2πr) × 360. An arc of 11 cm on a circle of radius 7 cm (circumference 44 cm) gives θ = 11 ÷ 44 × 360 = 90°. Use π = 22/7 when r is a multiple of 7, else 3.14."),
   ("pieges", "Common mistakes", "- Forgetting to add the two radii for the perimeter of a sector.\n- Using the area formula for the arc.\n- Using θ/180 instead of θ/360.")],
  [("Example 1: arc and area", "A sector of a circle has radius 21 cm and angle 60°. Find the arc length, area and perimeter (π = 22/7).",
    ["Fraction = 60/360 = 1/6.", "Arc = 1/6 × 2 × 22/7 × 21 = 22 cm; area = 1/6 × 22/7 × 21² = 231 cm².", "Perimeter = 22 + 21 + 21 = 64 cm."], "**Arc 22 cm, area 231 cm², perimeter 64 cm.**", sc1),
   ("Example 2: a flower bed", "A circular lawn at Buea has radius 14 m. A quarter of it (a 90° sector) is planted with roses. Find the area of the roses and the length of fencing along its three edges (π = 22/7).",
    ["Area = 1/4 × 22/7 × 14² = 154 m².", "Arc = 1/4 × 2 × 22/7 × 14 = 22 m.", "Fencing = 22 + 14 + 14 = 50 m."], "**154 m²; 50 m**")],
  [N("Find the arc length for r = 7 cm and θ = 90° (π = 22/7).", 11, "1/4 × 44 = 11 cm.", unit="cm"),
   N("Find the area of a sector with r = 14 cm and θ = 90° (π = 22/7).", 154, "1/4 × 22/7 × 196 = 154 cm².", unit="cm²"),
   M("A sector of angle 180° is:", "a semicircle", ["a quarter circle", "a full circle", "a chord"], "180/360 = 1/2.")],
  [N("An arc is 22 cm long on a circle of radius 21 cm (π = 22/7). Find the angle at the centre.", 60, "Circumference 132; 22 ÷ 132 × 360 = 60°.", unit="°"),
   O("A sector has radius 10 cm and angle 72°. Show that its area is one-fifth of the circle's area.", "72 ÷ 360 = 1/5, so the sector area = 1/5 × πr² = 1/5 × π × 100 = 20π cm², one-fifth of the 100π cm² circle.", "1 mark for 72/360 = 1/5, 1 mark for the circle area, 1 mark for the conclusion.", points=3)],
  P("A windscreen wiper at Douala sweeps a sector of angle 120° with radius 42 cm (use π = 22/7).",
    [pn("Find the arc length swept by the tip (cm).", 88, "1/3 × 2 × 22/7 × 42 = 88.", unit="cm", pts=2),
     pn("Find the area swept (cm²).", 1848, "1/3 × 22/7 × 1 764 = 1 848.", unit="cm²", pts=2),
     pn("Find the perimeter of the swept sector (cm).", 172, "88 + 42 + 42 = 172.", unit="cm", pts=1)]),
  [("The area of a sector is:", "θ/360 × πr²", ["θ/360 × 2πr", "θ × πr²", "360/θ × πr²"], "Fraction of the circle's area."),
   ("The perimeter of a sector is the arc plus:", "two radii", ["one radius", "the diameter", "the chord"], "Two straight edges."),
   ("A 90° sector is:", "a quarter of the circle", ["half", "a third", "an eighth"], "90/360."),
   ("An arc of a circle is:", "part of the circumference", ["a straight line", "the centre", "a radius"], "Definition."),
   ("A 45° sector has fraction:", "1/8", ["1/4", "1/2", "1/6"], "45/360.")],
  ill=(sc1, "A sector of radius 21 cm and angle 60°.", "A sector shape with its radius labelled 21 cm and angle 60 degrees."),
  notes=["Segment areas and chord lengths are left for Form 4."])

pyr = 6 * 6 * 10 / 3; cone_v = 22 / 7 * 49 * 24 / 3; cone_a = 22 / 7 * 7 * 25; sph_v = 4 / 3 * 22 / 7 * 343; sph_a = 4 * 22 / 7 * 49
assert pyr == 120 and abs(cone_v - 1232) < 1e-9 and abs(cone_a - 550) < 1e-9 and abs(sph_v - 1437.333) < 1e-3 and abs(sph_a - 616) < 1e-9 and 7**2 + 24**2 == 25**2
heap = 22 / 7 * 2.1**2 * 2 / 3; assert abs(heap - 9.24) < 1e-9
cone_fig = shapes([LINE(200, 30, 110, 170, width=2), LINE(200, 30, 290, 170, width=2), PATH("M 110 170 A 90 18 0 0 0 290 170", stroke="ink"), PATH("M 110 170 A 90 18 0 0 1 290 170", stroke="grey"),
                   LINE(200, 30, 200, 170, dash=True, color="grey"), LINE(200, 170, 290, 170, color="red", width=2), T(190, 105, "h = 24", 13, anchor="end"), T(245, 190, "r = 7", 13, color="red"), T(262, 90, "l = 25", 13, anchor="start")], 400, 210)
lesson(ch, "solids", "Cones, pyramids and spheres",
  ["Calculate the volume of a pyramid, cone and sphere", "Calculate the curved and total surface area of a cone and the area of a sphere", "Use the slant height with Pythagoras", "Solve practical problems on heaps, tanks and containers"],
  [("formule", "Volumes", "A pyramid or cone has one-third of the volume of the prism or cylinder with the same base and height. For a sphere of radius r:", "V_{\\text{pyramid}} = \\frac{1}{3} A h \\qquad V_{\\text{cone}} = \\frac{1}{3}\\pi r^2 h \\qquad V_{\\text{sphere}} = \\frac{4}{3}\\pi r^3"),
   ("formule", "Surface areas", "For a cone with slant height l and a sphere of radius r:", "A_{\\text{curved cone}} = \\pi r l \\qquad A_{\\text{sphere}} = 4 \\pi r^2"),
   ("methode", "The slant height", "The slant height l, the height h and the radius r form a right-angled triangle: $l^2 = r^2 + h^2$. A cone with r = 7 and h = 24 has $l = \\sqrt{49 + 576} = 25$. Then V = 1/3 × 22/7 × 49 × 24 = 1 232 and the curved area is 22/7 × 7 × 25 = 550. Add the base πr² = 154 for the total surface area: 704."),
   ("pieges", "Common mistakes", "- Using the slant height instead of the vertical height in the volume of a cone.\n- Forgetting the factor 1/3 for cones and pyramids.\n- Mixing units: cm and m in the same calculation.")],
  [("Example 1: a cone", "A cone has radius 7 cm and height 24 cm (π = 22/7). Find the slant height, the volume and the curved surface area.",
    ["l² = 7² + 24² = 625, so l = 25 cm.", "V = 1/3 × 22/7 × 49 × 24 = 1 232 cm³.", "Curved area = π r l = 22/7 × 7 × 25 = 550 cm²."], "**l = 25 cm; V = 1 232 cm³; curved area 550 cm².**", cone_fig),
   ("Example 2: a grain heap", "A heap of maize at a store in Bamenda is cone-shaped with radius 2.1 m and height 2 m (π = 22/7). Find its volume. How many 0.5 m³ bags could it fill?",
    ["V = 1/3 × 22/7 × 2.1² × 2 = 1/3 × 22/7 × 4.41 × 2 = 9.24 m³.", "Number of bags = 9.24 ÷ 0.5 = 18.48.", "So 18 full bags."], "**9.24 m³; 18 full bags.**")],
  [N("Find the volume of a pyramid with a square base of side 6 cm and height 10 cm.", 120, "1/3 × 36 × 10 = 120 cm³.", unit="cm³"),
   N("Find the surface area of a sphere of radius 7 cm (π = 22/7).", 616, "4 × 22/7 × 49 = 616 cm².", unit="cm²"),
   M("The volume of a cone is:", "$\\frac{1}{3}\\pi r^2 h$", ["$\\pi r^2 h$", "$\\frac{4}{3}\\pi r^3$", "$\\pi r l$"], "One-third of the cylinder.")],
  [N("Find the volume of a sphere of radius 7 cm (π = 22/7), to the nearest cm³.", 1437, "4/3 × 22/7 × 343 = 1 437.3.", tol=1, unit="cm³"),
   O("A cone has radius 5 cm and slant height 13 cm. Find its height and explain your method.", "The height h satisfies h² = l² − r² = 169 − 25 = 144, so h = 12 cm. We used Pythagoras in the right-angled triangle formed by h, r and l.", "1 mark for the triangle, 1 mark for 144, 1 mark for h = 12.", points=3)],
  P("A cone-shaped roof of a traditional hut near Bafut has radius 3.5 m, height 2.4 m and slant height 4.25 m (π = 22/7).",
    [pn("Find the area of thatch needed to cover the curved surface (m²).", 46.75, "22/7 × 3.5 × 4.25 = 46.75.", tol=0.01, unit="m²", pts=2),
     pn("Check the height using Pythagoras: find h when r = 3.5 and l = 4.25 (m, 1 d.p.).", 2.4, "h² = 18.0625 − 12.25 = 5.8125, so h ≈ 2.41.", tol=0.05, unit="m", pts=2),
     pn("Find the volume of air in the cone (m³, 1 d.p.), taking h = 2.4 m.", 30.8, "1/3 × 22/7 × 12.25 × 2.4 = 30.8.", tol=0.05, unit="m³", pts=2)]),
  [("The volume of a sphere is:", "$\\frac{4}{3}\\pi r^3$", ["$4\\pi r^2$", "$\\frac{1}{3}\\pi r^2 h$", "$\\pi r^2 h$"], "Sphere volume."),
   ("The surface area of a sphere is:", "$4\\pi r^2$", ["$\\frac{4}{3}\\pi r^3$", "$2\\pi r$", "$\\pi r^2$"], "Sphere area."),
   ("For a cone the slant height satisfies:", "l² = r² + h²", ["l = r + h", "l² = h² − r²", "l = r × h"], "Pythagoras."),
   ("A pyramid has volume:", "1/3 × base area × height", ["base area × height", "2/3 × base area", "base area + height"], "One-third rule."),
   ("1 m³ holds:", "1 000 litres", ["100 litres", "10 litres", "1 000 000 litres"], "Conversion.")],
  ill=(cone_fig, "A cone with radius 7, height 24 and slant height 25.", "A cone with its height as a dashed line, the radius and the slant height labelled."),
  notes=["The Bafut hut figures are invented for practice; check if the 'traditional hut' context is suitable. Frustums and composite solids are left for Form 4."])

# ===================================================================== PROBABILITY, TRANSFORMATIONS, MATRICES
ch = p.chapter("prob-transf", "Probability, transformations and matrices", REF)

F_ = Fraction
assert F_(5, 8) * F_(4, 7) == F_(5, 14) and F_(5, 8) * F_(3, 7) + F_(3, 8) * F_(5, 7) == F_(15, 28) and F_(5, 8) ** 2 == F_(25, 64)
dice = [(a, b) for a in range(1, 7) for b in range(1, 7)]
assert sum(1 for a, b in dice if a + b == 7) == 6 and sum(1 for a, b in dice if a == b) == 6 and sum(1 for a, b in dice if a + b >= 10) == 6
assert abs(5 / 14 - 0.357) < 0.001 and abs(15 / 28 - 0.536) < 0.001
tree = shapes([LINE(30, 130, 170, 60), LINE(30, 130, 170, 200), LINE(170, 60, 330, 25), LINE(170, 60, 330, 95), LINE(170, 200, 330, 165), LINE(170, 200, 330, 235),
               T(90, 82, "5/8", 13), T(90, 188, "3/8", 13), T(250, 36, "4/7", 12), T(250, 88, "3/7", 12), T(250, 172, "5/7", 12), T(250, 226, "2/7", 12),
               T(160, 56, "R", 14, bold=True, anchor="end"), T(160, 214, "B", 14, bold=True, anchor="end"), T(342, 30, "R", 14, anchor="start"), T(342, 100, "B", 14, anchor="start"), T(342, 170, "R", 14, anchor="start"), T(342, 240, "B", 14, anchor="start")], 420, 260)
lesson(ch, "probability", "Probability: single and combined events",
  ["Calculate the probability of an event", "Use the complement and mutually exclusive events", "Use tree diagrams for independent events and for events without replacement", "Apply the AND and OR rules"],
  [("formule", "Probability", "The probability of an event is a number from 0 (impossible) to 1 (certain):", "P(A) = \\frac{\\text{number of favourable outcomes}}{\\text{total number of equally likely outcomes}}"),
   ("propriete", "Rules", "- **Complement**: P(not A) = 1 − P(A).\n- **Mutually exclusive** events cannot happen together: P(A or B) = P(A) + P(B).\n- **Independent** events do not affect each other: P(A and B) = P(A) × P(B).\n- Probabilities of all outcomes add up to 1. Give answers as fractions, decimals or percentages."),
   ("methode", "Tree diagrams", "Draw a branch for each outcome and write the probability on it. **Multiply along the branches** for 'and'; **add the results** of different routes for 'or'. If an object is **not replaced**, the second set of branches has changed numbers: from a bag of 5 red (R) and 3 blue (B) balls, after taking a red ball there are 4 red and 3 blue left."),
   ("pieges", "Common mistakes", "- Adding probabilities along a branch instead of multiplying.\n- Leaving the denominator unchanged when an object is not replaced.\n- A probability larger than 1 or negative: check the answer.")],
  [("Example 1: single events", "A bag at a Douala stall holds 5 red and 3 blue beads. One is taken at random. Find P(red) and P(not red).",
    ["Total beads = 8; favourable = 5, so P(red) = 5/8.", "P(not red) = 1 − 5/8 = 3/8, and this equals P(blue)."], "**5/8 and 3/8**"),
   ("Example 2: two draws, no replacement", "Two beads are taken, one after the other, without replacement. Find P(two reds) and P(one of each colour).",
    ["Two reds: 5/8 × 4/7 = 20/56 = 5/14 ≈ 0.357.", "Red then blue: 5/8 × 3/7 = 15/56; blue then red: 3/8 × 5/7 = 15/56.", "One of each = 15/56 + 15/56 = 30/56 = 15/28 ≈ 0.536."], "**5/14 and 15/28**", tree)],
  [N("A fair die is rolled. Find P(a number greater than 4) as a decimal to 3 d.p.", 0.333, "Outcomes 5 and 6: 2/6 = 0.333.", tol=0.001),
   N("P(rain tomorrow) = 0.3. Find P(no rain).", 0.7, "1 − 0.3.", tol=0.001),
   M("The probability of an impossible event is:", "0", ["1", "0.5", "−1"], "Impossible means 0.")],
  [N("Two fair coins are tossed. Find P(two heads) as a decimal.", 0.25, "1/2 × 1/2 = 1/4.", tol=0.001),
   O("A student says: 'P(head) = 1/2, so if I toss a coin twice I must get exactly one head.' Is she right? Explain.", "No. Each toss is independent: P(2 heads) = 1/4, P(1 head) = 1/2 and P(0 heads) = 1/4. Getting exactly one head is only likely, not certain.", "1 mark for independence, 1 mark for the correct probabilities, 1 mark for the conclusion.", points=3)],
  P("Two fair dice are rolled together (36 equally likely outcomes).",
    [pn("Find the number of outcomes that give a total of 7.", 6, "(1,6), (2,5), (3,4), (4,3), (5,2), (6,1).", pts=1),
     pn("Find P(total 7) as a decimal to 3 d.p.", 0.167, "6/36 = 1/6.", tol=0.001, pts=2),
     pn("Find P(a double, i.e. both dice show the same number) to 3 d.p.", 0.167, "(1,1) to (6,6): 6/36.", tol=0.001, pts=2)]),
  [("A probability cannot be:", "greater than 1", ["0.5", "0", "1"], "The scale is 0 to 1."),
   ("P(A) = 0.4 means P(not A) =", "0.6", ["0.4", "−0.4", "1.4"], "1 − P(A)."),
   ("For independent events, P(A and B) =", "P(A) × P(B)", ["P(A) + P(B)", "P(A) − P(B)", "P(A) ÷ P(B)"], "Multiply."),
   ("In a tree diagram we multiply along:", "a branch (route)", ["a vertical line", "the outcomes", "nothing"], "'And' rule."),
   ("Mutually exclusive events:", "cannot happen together", ["always happen together", "are independent", "have probability 1"], "Definition.")],
  ill=(tree, "Tree diagram for two draws without replacement from 5 red and 3 blue beads.", "A tree diagram with probabilities 5/8 and 3/8 on the first branches and 4/7, 3/7, 5/7, 2/7 on the second."),
  notes=["Conditional probability notation is not used; the tree diagram handles it informally."])

T1 = [(1, 1), (4, 1), (1, 3)]
refl = [(-x, y) for x, y in T1]; assert refl == [(-1, 1), (-4, 1), (-1, 3)]
trans = [(x + 3, y - 2) for x, y in T1]; assert trans == [(4, -1), (7, -1), (4, 1)]
enl = [(2 * x, 2 * y) for x, y in T1]; assert enl == [(2, 2), (8, 2), (2, 6)]
rot = [(-y, x) for x, y in T1]; assert rot == [(-1, 1), (-1, 4), (-3, 1)]
areaT = 0.5 * 3 * 2; areaE = 0.5 * 6 * 4; assert areaT == 3 and areaE == 12 and areaE / areaT == 4
ax = Axes(-5, 5, -3, 4, w=400, h=290, xticks=range(-5, 6), yticks=range(-3, 5))
ax.poly(T1, fill="lightblue", color="blue"); ax.poly(refl, fill="lightorange", color="red")
ax.text(1.9, 1.5, "T", 14, bold=True); ax.text(-1.9, 1.5, "T′", 14, bold=True)
trf1 = ax.fig()
lesson(ch, "transformations", "Transformations of the plane",
  ["Reflect a shape in a line", "Translate a shape by a vector", "Rotate a shape about a point through a given angle", "Enlarge a shape and relate scale factor to area"],
  [("definition", "The four transformations", "A transformation moves a shape (the **object**) to a new position (the **image**). A **reflection** flips it over a mirror line. A **translation** slides it, described by a vector such as 3 units right and 2 units down, written (3, −2). A **rotation** turns it about a centre through an angle, clockwise or anticlockwise. An **enlargement** changes its size by a scale factor k from a centre. Reflection, translation and rotation keep the size and shape (they are **congruent**)."),
   ("propriete", "Coordinate rules", "Reflection in the y-axis: (x, y) → (−x, y); in the x-axis: (x, y) → (x, −y). Translation by (a, b): (x, y) → (x + a, y + b). Rotation of 90° anticlockwise about the origin: (x, y) → (−y, x). Enlargement with scale factor k and centre the origin: (x, y) → (kx, ky)."),
   ("propriete", "Scale factors", "Under an enlargement of scale factor k, every **length** is multiplied by k, but angles stay the same and the **area is multiplied by k²**. A scale factor between 0 and 1 makes the image smaller; a **negative** scale factor puts the image on the opposite side of the centre."),
   ("pieges", "Common mistakes", "- Reflecting in the wrong axis, or measuring the distance to the mirror line incorrectly.\n- Rotating in the wrong direction (clockwise vs anticlockwise).\n- Multiplying the area by k instead of k²; a scale factor 2 gives 4 times the area.")],
  [("Example 1: reflection and translation", "Triangle T has vertices (1, 1), (4, 1) and (1, 3). Find its reflection in the y-axis and its translation by (3, −2).",
    ["Reflection (x, y) → (−x, y): (−1, 1), (−4, 1), (−1, 3).", "Translation (x + 3, y − 2): (4, −1), (7, −1), (4, 1).", "Both images are congruent to T."], "**(−1, 1), (−4, 1), (−1, 3); (4, −1), (7, −1), (4, 1).**", trf1),
   ("Example 2: rotation and enlargement", "Rotate T by 90° anticlockwise about the origin, then enlarge T by scale factor 2 with centre the origin. Compare the areas.",
    ["Rotation (x, y) → (−y, x): (−1, 1), (−1, 4), (−3, 1).", "Enlargement (x, y) → (2x, 2y): (2, 2), (8, 2), (2, 6).", "Area of T = ½ × 3 × 2 = 3 and area of the enlargement = ½ × 6 × 4 = 12, which is 2² = 4 times as large."], "**(−1, 1), (−1, 4), (−3, 1); (2, 2), (8, 2), (2, 6); area × 4.**")],
  [N("Reflect the point (3, 2) in the y-axis. What is the x-coordinate of the image?", -3, "(x, y) → (−x, y).", ),
   N("Translate (2, 5) by (−4, 3). Find the y-coordinate of the image.", 8, "5 + 3 = 8.", ),
   M("Which transformation does NOT change the size of a shape?", "Rotation", ["Enlargement with k = 2", "Enlargement with k = 0.5", "Enlargement with k = 3"], "Congruent transformations keep size.")],
  [N("A shape has area 5 cm². It is enlarged with scale factor 3. Find the area of the image (cm²).", 45, "5 × 3² = 45.", unit="cm²"),
   O("Describe fully the single transformation that maps (2, 1) → (−2, 1) and (3, 4) → (−3, 4).", "It is a reflection in the y-axis (the line x = 0), since (x, y) → (−x, y).", "1 mark for 'reflection', 1 mark for the mirror line x = 0 (y-axis), 1 mark for a rule or check.", points=3)],
  P("A signboard triangle of a shop in Bamenda has vertices (1, 1), (4, 1) and (1, 3), with 1 unit = 1 m.",
    [pn("Find its area (m²).", 3, "½ × 3 × 2 = 3.", unit="m²", pts=1),
     pn("A larger sign is an enlargement with scale factor 2. Find its area (m²).", 12, "3 × 2² = 12.", unit="m²", pts=2),
     pn("The larger sign is painted with paint costing 1 500 FCFA per m². Find the cost (FCFA).", 18000, "12 × 1 500 = 18 000.", unit="FCFA", pts=2)], figure=trf1),
  [("An enlargement of scale factor 3 multiplies areas by:", "9", ["3", "6", "27"], "k²."),
   ("A translation changes:", "the position only", ["the size", "the shape", "the angles"], "It slides the shape."),
   ("Reflection in the x-axis sends (2, 3) to:", "(2, −3)", ["(−2, 3)", "(−2, −3)", "(3, 2)"], "(x, y) → (x, −y)."),
   ("A scale factor of 0.5 makes the image:", "smaller", ["larger", "the same size", "inverted only"], "Between 0 and 1."),
   ("Rotation 90° anticlockwise about O sends (x, y) to:", "(−y, x)", ["(y, −x)", "(−x, −y)", "(x, −y)"], "Standard rule.")],
  ill=(trf1, "Triangle T and its reflection T′ in the y-axis.", "A blue triangle on the right of the y-axis and its orange mirror image on the left."),
  notes=["Column vectors are written in the form (3, −2) because the content format has no matrix notation; check conventions. Combined transformations and negative scale factors are only mentioned."])

A = [[2, 3], [1, 4]]; B = [[1, 0], [5, 2]]
def mm(X, Y): return [[sum(X[i][k] * Y[k][j] for k in range(2)) for j in range(2)] for i in range(2)]
AB = mm(A, B); BA = mm(B, A); assert AB == [[17, 6], [21, 8]] and BA == [[2, 3], [12, 23]]
add_ = [[A[i][j] + B[i][j] for j in range(2)] for i in range(2)]; assert add_ == [[3, 3], [6, 6]]
two = [[2 * v for v in r] for r in A]; assert two == [[4, 6], [2, 8]] and 2 * 4 - 3 * 1 == 5
assert 500 * 4 + 300 * 6 == 3800
mf = matfig([A, "×", B, "=", AB], h=120)
lesson(ch, "matrices", "Introduction to matrices",
  ["Describe a matrix by its order", "Add, subtract and multiply by a number", "Multiply two matrices", "Find the determinant of a 2 × 2 matrix"],
  [("definition", "Matrices", "A **matrix** is a rectangular array of numbers in rows and columns inside brackets. A matrix with 2 rows and 3 columns has **order** 2 × 3 (rows first). Each number is an **element**. A **square** matrix has the same number of rows and columns. Matrices store tables of data such as sales or prices."),
   ("methode", "Adding and scalar multiplication", "Matrices of the **same order** are added (or subtracted) element by element. Multiplying by a number multiplies **every element**. If A has rows (2, 3) and (1, 4) and B has rows (1, 0) and (5, 2), then A + B has rows (3, 3) and (6, 6), and 2A has rows (4, 6) and (2, 8)."),
   ("methode", "Multiplication", "To multiply matrices, take a **row of the first** times a **column of the second**: multiply the entries in pairs and add. A × B is possible only when the number of **columns of A equals the number of rows of B**. For the A and B above, the first row of AB is (2×1 + 3×5, 2×0 + 3×2) = (17, 6). In general **AB is not equal to BA**."),
   ("formule", "Determinant", "The determinant of a 2 × 2 matrix with rows (a, b) and (c, d) is:", "\\det = ad - bc"),
   ("pieges", "Common mistakes", "- Adding matrices of different orders.\n- Multiplying element by element instead of row × column.\n- Assuming AB = BA.")],
  [("Example 1: multiplication", "A has rows (2, 3) and (1, 4); B has rows (1, 0) and (5, 2). Find AB, BA and det A.",
    ["Row 1 × columns: 2×1 + 3×5 = 17 and 2×0 + 3×2 = 6; row 2: 1×1 + 4×5 = 21 and 1×0 + 4×2 = 8, so AB has rows (17, 6) and (21, 8).", "BA: row 1 is (1×2 + 0×1, 1×3 + 0×4) = (2, 3); row 2 is (5×2 + 2×1, 5×3 + 2×4) = (12, 23). So AB ≠ BA.", "det A = 2×4 − 3×1 = 5."], "**AB = (17, 6 / 21, 8); BA = (2, 3 / 12, 23); det A = 5.**", mf),
   ("Example 2: a sales problem", "A trader sells 4 kg of rice at 500 FCFA/kg and 6 kg of beans at 300 FCFA/kg. Use a row matrix of prices times a column matrix of quantities to find the takings.",
    ["Prices (500, 300) are a 1 × 2 matrix; quantities (4 above 6) are a 2 × 1 matrix.", "Row × column: 500 × 4 + 300 × 6 = 2 000 + 1 800.", "The product is a 1 × 1 matrix: 3 800 FCFA."], "**3 800 FCFA**")],
  [N("Find the sum of the elements of A + B where A has rows (1, 2), (3, 4) and B has rows (5, 6), (7, 8).", 36, "A + B has rows (6, 8) and (10, 12): sum 36."),
   N("Find the determinant of the matrix with rows (3, 2) and (4, 5).", 7, "3×5 − 2×4 = 7."),
   M("A matrix with 3 rows and 2 columns has order:", "3 × 2", ["2 × 3", "3 + 2", "6"], "Rows first.")],
  [N("Find the top-left element of the product of the matrices with rows (1, 2), (3, 4) and (0, 1), (1, 0).", 2, "1×0 + 2×1 = 2.", ),
   O("A and B are 2 × 2 matrices. Explain why AB and BA are usually different, using any example.", "Matrix multiplication is row times column, so changing the order changes which rows and columns are paired. E.g. with A = rows (2, 3), (1, 4) and B = rows (1, 0), (5, 2), AB has top-left 17 but BA has top-left 2.", "1 mark for row × column, 1 mark for an example, 1 mark for the comparison.", points=3)],
  P("A shop in Bafoussam records sales (kg) in a matrix: rice and beans on Monday (4, 6) and on Tuesday (3, 8). Prices are 500 FCFA/kg (rice) and 300 FCFA/kg (beans).",
    [pn("Find Monday's takings (FCFA).", 3800, "500 × 4 + 300 × 6.", unit="FCFA", pts=1),
     pn("Find Tuesday's takings (FCFA).", 3900, "500 × 3 + 300 × 8 = 1 500 + 2 400.", unit="FCFA", pts=2),
     pn("Find the total for the two days (FCFA).", 7700, "3 800 + 3 900.", unit="FCFA", pts=2)]),
  [("The order of a matrix with 2 rows and 4 columns is:", "2 × 4", ["4 × 2", "8", "2 + 4"], "Rows × columns."),
   ("To add two matrices they must have:", "the same order", ["the same elements", "the same determinant", "an odd order"], "Element by element."),
   ("The determinant of (2, 1 / 3, 4) is:", "5", ["11", "−5", "8"], "2×4 − 1×3."),
   ("AB = BA is:", "not generally true", ["always true", "true only for 1 × 1", "never true"], "Non-commutative."),
   ("2 × (matrix with rows (1, 2), (3, 4)) has top-right element:", "4", ["2", "6", "8"], "2 × 2.")],
  ill=(mf, "The product of two 2 × 2 matrices A × B = AB.", "Three matrices written side by side showing A times B equals the result."),
  notes=["Matrix inverses and solving simultaneous equations with matrices are left for Form 4-5. Matrices are described in words in the text because the content format has no matrix notation."])

# ===================================================================== COMMERCIAL
ch = p.chapter("commercial", "Commercial arithmetic", REF)

assert abs(20000 * 0.1925 - 3850) < 1e-9 and abs(20000 * 1.1925 - 23850) < 1e-6 and abs(47700 / 1.1925 - 40000) < 1e-6
ci = [500000.0]
for _ in range(3): ci.append(ci[-1] * 1.05)
assert ci[1:] == [525000.0, 551250.0, 578812.5] and 578812.5 - 500000 - 75000 == 3812.5
assert abs(100 * 655.957 - 65595.7) < 1e-6 and abs(100000 / 655.957 - 152.449) < 0.001 and 30000 + 6 * 17500 == 135000 and 135000 - 120000 == 15000
cit = ptable(["Year", "Start", "Interest", "End"], [["1", "500 000", "25 000", "525 000"], ["2", "525 000", "26 250", "551 250"], ["3", "551 250", "27 562.5", "578 812.5"]], colw=95)
lesson(ch, "commercial-arithmetic", "VAT, compound interest, hire purchase and exchange",
  ["Calculate VAT and prices with and without VAT", "Calculate compound interest and compare with simple interest", "Compare cash price and hire purchase", "Convert between currencies using an exchange rate"],
  [("methode", "VAT", "**Value Added Tax (VAT)** is a percentage added to the price of goods and services. In Cameroon the standard rate is commonly given as 19.25% (check the current rate). Price with VAT = price without VAT × 1.1925. A price of 20 000 FCFA has VAT 20 000 × 0.1925 = 3 850 FCFA, total 23 850 FCFA. To find the price **before** VAT from a price including VAT, **divide** by 1.1925: 47 700 ÷ 1.1925 = 40 000 FCFA."),
   ("formule", "Compound interest", "With compound interest, the interest of each year is added to the money and earns interest the next year. After n years at R% per year:", "A = P \\left(1 + \\frac{R}{100}\\right)^n"),
   ("methode", "Example and comparison", "500 000 FCFA at 5% for 3 years: 500 000 × 1.05³ = 578 812.5 FCFA. The compound interest is 78 812.5 FCFA. Simple interest would be 500 000 × 5 × 3 ÷ 100 = 75 000 FCFA, so compound interest earns 3 812.5 FCFA more."),
   ("methode", "Hire purchase and exchange", "**Hire purchase** means paying a deposit and then regular instalments: total = deposit + number of instalments × amount. Compare with the cash price to see the extra cost. For **exchange**, multiply by the rate to change into the other currency, divide to change back. The CFA franc is linked to the euro at 1 euro = 655.957 FCFA."),
   ("pieges", "Common mistakes", "- Removing VAT by subtracting 19.25% of the total instead of dividing by 1.1925.\n- Using the simple-interest formula when 'compound' is asked.\n- Multiplying when you should divide in an exchange problem: check whether the answer should be larger or smaller.")],
  [("Example 1: VAT", "A generator costs 40 000 FCFA before VAT. Find the selling price with VAT at 19.25%. A phone is sold at 47 700 FCFA including VAT: find the price before VAT.",
    ["VAT = 40 000 × 0.1925 = 7 700; price = 47 700 FCFA.", "Price before VAT = 47 700 ÷ 1.1925 = 40 000 FCFA.", "The two examples are inverses of each other."], "**47 700 FCFA; 40 000 FCFA.**"),
   ("Example 2: compound interest", "A farmer in Bafoussam deposits 500 000 FCFA at 5% compound interest per year. How much after 3 years? How does this compare with simple interest?",
    ["Year 1: 500 000 × 1.05 = 525 000; year 2: 551 250; year 3: 578 812.5 FCFA.", "Compound interest = 78 812.5 FCFA; simple interest = 75 000 FCFA.", "Compound earns 3 812.5 FCFA more."], "**578 812.5 FCFA; 3 812.5 FCFA more than simple interest.**", cit)],
  [N("A bag of cement costs 6 000 FCFA before VAT. Find the VAT at 19.25% (FCFA).", 1155, "6 000 × 0.1925 = 1 155.", unit="FCFA"),
   N("Find the amount after 2 years if 200 000 FCFA is invested at 10% compound interest per year (FCFA).", 242000, "200 000 × 1.1² = 242 000.", unit="FCFA"),
   M("To find the price before VAT from a price including VAT at 19.25% we:", "divide by 1.1925", ["multiply by 0.8075", "subtract 19.25", "multiply by 1.1925"], "Reverse the multiplication.")],
  [N("A television has a cash price of 120 000 FCFA. Hire purchase: deposit 30 000 FCFA plus 6 monthly payments of 17 500 FCFA. How much extra does hire purchase cost (FCFA)?", 15000, "30 000 + 6 × 17 500 = 135 000; 135 000 − 120 000 = 15 000.", unit="FCFA"),
   O("Explain, using the first two years of a 5% investment, why compound interest grows faster than simple interest.", "With compound interest the first year's interest (5% of the capital) is added to the capital, so the second year's interest is 5% of a larger amount. With simple interest it is always 5% of the original capital.", "1 mark for the idea of adding interest, 1 mark for a larger base, 1 mark for the comparison.", points=3)],
  P("A traveller in Douala exchanges money. Use 1 euro = 655.957 FCFA.",
    [pn("How many FCFA for 100 euros (to the nearest FCFA)?", 65596, "100 × 655.957 = 65 595.7.", tol=1, unit="FCFA", pts=2),
     pn("How many euros for 100 000 FCFA (to 2 d.p.)?", 152.45, "100 000 ÷ 655.957 = 152.449.", tol=0.01, unit="euros", pts=2),
     pm("When changing FCFA into euros the number should get:", "smaller", ["larger", "stay the same", "become zero"], "One euro is worth many francs.", 1)]),
  [("VAT is a tax added to:", "the price of goods and services", ["salaries only", "bank loans only", "exports only"], "Definition."),
   ("Compound interest is calculated on:", "the original amount plus earlier interest", ["the original amount only", "the interest only", "the time only"], "Interest on interest."),
   ("A total price after 20% VAT on 1 000 FCFA is:", "1 200 FCFA", ["1 020 FCFA", "800 FCFA", "1 002 FCFA"], "1 000 × 1.2."),
   ("A deposit is:", "an initial payment", ["the final payment", "the interest", "the tax"], "Part of hire purchase."),
   ("An exchange rate tells us:", "the value of one currency in another", ["the interest rate", "the VAT rate", "the discount"], "Definition.")],
  ill=(cit, "Compound interest on 500 000 FCFA at 5% for three years.", "A table with the year, the starting amount, the interest and the end amount for three years."),
  notes=["The Cameroon VAT rate of 19.25% and the fixed 655.957 FCFA per euro rate are widely published but must be re-checked at release; rates and tax rules change. Salary, income-tax and customs calculations are not included."])

p.write()
