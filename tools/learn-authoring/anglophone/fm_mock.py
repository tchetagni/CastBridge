from _kit import *
import math

def build(p):
    LS = p.LS
    f = lambda x: 2 * x ** 3 - 3 * x ** 2 - 11 * x + 6
    assert f(3) == 0 and f(0.5) == 0 and f(-2) == 0 and (5 + 3) * (5 - 1) == 32 == 2 ** 5
    q1 = mockq(LS["polynomials"], P("Question 1. Let $f(x) = 2x^3 - 3x^2 - 11x + 6$.",
        [pn("Find f(3).", 0, "54 − 27 − 33 + 6 = 0, so (x − 3) is a factor.", pts=1),
         pm("Factorising completely, f(x) =", "$(x - 3)(2x - 1)(x + 2)$", ["$(x - 3)(2x + 1)(x - 2)$", "$(x + 3)(2x - 1)(x - 2)$", "$(x - 3)(x - 1)(2x + 2)$"], "Dividing by (x − 3) gives 2x² + 3x − 2 = (2x − 1)(x + 2).", 2),
         pn("Hence find the smallest root of f(x) = 0.", -2, "The roots are 3, 1/2 and −2.", pts=1),
         pn("Solve $\\log_2(x + 3) + \\log_2(x - 1) = 5$ and enter the solution.", 5, "(x + 3)(x − 1) = 32 gives x² + 2x − 35 = (x + 7)(x − 5) = 0, so x = 5 (x = −7 rejected).", pts=1)]))
    y = lambda x: x ** 3 - 3 * x ** 2
    assert y(2) == -4 and 3 * 9 - 6 * 3 == 9 and len([x for x in (0, 120, 240, 360) if abs(2 * math.cos(math.radians(x)) ** 2 - math.cos(math.radians(x)) - 1) < 1e-9]) == 4
    q2 = mockq(LS["applications"], P("Question 2. A curve has equation $y = x^3 - 3x^2$.",
        [pn("Find the gradient of the curve at x = 3.", 9, "dy/dx = 3x² − 6x = 27 − 18 = 9.", pts=1),
         pn("Find the x-coordinate of the minimum point.", 2, "3x² − 6x = 0 gives x = 0 or 2; d²y/dx² = 6x − 6 is positive at x = 2.", pts=1),
         pn("Find the minimum value of y.", -4, "8 − 12 = −4.", pts=1),
         pn("How many solutions has $2\\cos^2 x - \\cos x - 1 = 0$ for 0° ≤ x ≤ 360°?", 4, "(2cos x + 1)(cos x − 1) = 0: cos x = 1 gives 0° and 360°; cos x = −1/2 gives 120° and 240°.", pts=2)]))
    assert (0 * 4 + 1 * 5) == 5 and (4 / 2, 6 / 2) == (2, 3) and 2 ** 2 + 2 ** 2 == 8 and 3 == -2 + 5
    q3 = mockq(LS["circles"], P("Question 3. A(0, 1) and B(4, 5) are two points.",
        [pn("Find the y-coordinate of the midpoint of AB.", 3, "(1 + 5)/2 = 3.", pts=1),
         pm("The equation of the perpendicular bisector of AB is:", "y = −x + 5", ["y = x + 5", "y = −x + 3", "y = x − 1"], "Midpoint (2, 3); gradient of AB is 1, so the bisector has gradient −1: y − 3 = −(x − 2).", 2),
         pn("The circle with AB as diameter has equation $(x - 2)^2 + (y - 3)^2 = r^2$. Find r².", 8, "r² = 2² + 2² = 8 (half of AB = 4√2/2 = 2√2).", pts=1),
         pn("Find the scalar product of the vectors OA and OB (O the origin).", 5, "(0, 1) · (4, 5) = 0 + 5 = 5.", pts=1)]))
    assert math.perm(8, 3) == 336 and math.comb(4, 2) * math.comb(6, 2) == 90 and abs(math.sqrt(8 / 3) - 1.633) < 1e-3
    q4 = mockq(LS["perm-comb"], P("Question 4. This question is about counting, probability and statistics.",
        [pn("In how many ways can 3 different prizes be given to 3 of 8 pupils?", 336, "⁸P₃ = 8 × 7 × 6 = 336.", pts=1),
         pn("A committee of 4 is chosen from 6 men and 4 women. How many have exactly 2 women?", 90, "⁴C₂ × ⁶C₂ = 6 × 15 = 90.", pts=2),
         pn("A bag has 5 red and 3 blue balls; two are drawn without replacement. Find P(both red) as a decimal (3 d.p.).", round(5 / 14, 3), "5/8 × 4/7 = 5/14 = 0.357.", tol=0.001, pts=1),
         pn("Find the standard deviation of 2, 4, 6 (2 d.p.).", 1.63, "Mean 4, variance 8/3 = 2.667, σ = 1.633.", tol=0.01, pts=1)]))
    p.mock("1", "GCE O Level mock — Further Mathematics", 180,
           "Answer all four questions. Show all your working. Calculators may be used. Give non-exact answers to 3 significant figures unless told otherwise. Practice paper out of 20; duration and format to be checked against the official Cameroon GCE Board documents.",
           [("Section A — Algebra and calculus", [q1, q2]), ("Section B — Geometry, counting and statistics", [q3, q4])])
