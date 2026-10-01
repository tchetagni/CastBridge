from _kit import *
import math
cos = lambda d: math.cos(math.radians(d)); sin = lambda d: math.sin(math.radians(d))

def build(p):
    LS = p.LS
    sp = 150000 * 1.2; assert sp == 180000
    vat = sp * 0.1925; tot = sp + vat; assert abs(vat - 34650) < 1e-6 and abs(tot - 214650) < 1e-6
    eur = round(tot / 655.957); assert eur == 327
    q1 = mockq(LS["vat-exchange"], P("Question 1. A shopkeeper in Douala buys a generator for 150 000 FCFA and sells it at a profit of 20% (before VAT).",
        [pn("Find the selling price before VAT (FCFA).", sp, "150 000 × 1.2 = 180 000 FCFA.", unit="FCFA", pts=1),
         pn("VAT at 19.25% is added to this price. Find the VAT (FCFA).", vat, "0.1925 × 180 000 = 34 650 FCFA.", unit="FCFA", pts=2),
         pn("Find the price paid by the customer (FCFA).", tot, "180 000 + 34 650 = 214 650 FCFA.", unit="FCFA", pts=1),
         pn("A customer abroad pays in euros (1 euro = 655.957 FCFA). Find the amount to the nearest euro.", eur, "214 650 ÷ 655.957 = 327.2, so 327 euros.", tol=1, unit="euros", pts=1)]))
    assert (2 * 1.5 - 3) == 0 and 2 * 1.5 ** 2 + 5 * 1.5 - 12 == 0
    least = min(x for x in range(-20, 20) if 3 * (x - 2) <= 5 * x + 4); assert least == -5
    assert (1 + 2) / (1 + 3) == 0.75
    q2 = mockq(LS["expand-factorise"], P("Question 2. This question is about algebra.",
        [pm("Factorise $2x^2 + 5x - 12$.", "$(2x - 3)(x + 4)$", ["$(2x + 3)(x - 4)$", "$(2x - 4)(x + 3)$", "$(x - 3)(2x + 4)$"], "ac = −24; numbers with sum 5: 8 and −3: $2x^2 + 8x - 3x - 12 = (2x - 3)(x + 4)$.", 1),
         pn("Hence solve $2x^2 + 5x - 12 = 0$. Enter the positive root.", 1.5, "2x − 3 = 0 gives x = 1.5 (the other root is −4).", tol=0.001, pts=1),
         pn("Find the smallest integer x satisfying $3(x - 2) \\leq 5x + 4$.", least, "3x − 6 ≤ 5x + 4 gives −10 ≤ 2x, so x ≥ −5: the smallest integer is −5.", pts=2),
         pn("Simplify $\\frac{x^2 - 4}{x^2 + x - 6}$ and evaluate it when x = 1.", 0.75, "$\\frac{(x - 2)(x + 2)}{(x + 3)(x - 2)} = \\frac{x + 2}{x + 3}$; at x = 1: 3/4 = 0.75.", tol=0.001, pts=1)]))
    arc = 90 / 360 * 2 * 22 / 7 * 14; area = 90 / 360 * 22 / 7 * 14 ** 2; assert arc == 22 and abs(area - 154) < 1e-9
    q3 = mockq(LS["area-arc"], P("Question 3. OAB is a sector of a circle of centre O and radius 14 cm; angle AOB = 90°. Use π = 22/7.",
        [pn("Find the length of the arc AB (cm).", 22, "90/360 × 2 × 22/7 × 14 = 22 cm.", unit="cm", pts=1),
         pn("Find the area of the sector (cm²).", 154, "¼ × 22/7 × 196 = 154 cm².", unit="cm²", pts=2),
         pn("Find the perimeter of the sector (cm).", 50, "Arc + 2 radii = 22 + 28 = 50 cm.", unit="cm", pts=1),
         pn("P is a point on the major arc AB. Find angle APB (degrees).", 45, "Angle at the circumference is half the angle at the centre: 90 ÷ 2 = 45°.", unit="°", pts=1)]))
    bc = math.sqrt(81 + 144 - 2 * 9 * 12 * cos(50)); ar = 0.5 * 9 * 12 * sin(50)
    assert abs(bc - 9.28) < 0.01 and abs(ar - 41.37) < 0.01
    q4 = mockq(LS["sine-cosine-rule"], P("Question 4. In triangle ABC, AB = 9 cm, AC = 12 cm and angle BAC = 50°. Also, 40 pupils took a test: 28 of them scored 60 marks or less.",
        [pn("Find BC to 2 decimal places (cm).", round(bc, 2), "BC² = 81 + 144 − 216 cos 50° = 225 − 138.83 = 86.17; BC ≈ 9.28 cm.", tol=0.02, unit="cm", pts=2),
         pn("Find the area of triangle ABC to 2 decimal places (cm²).", round(ar, 2), "½ × 9 × 12 × sin 50° = 54 × 0.7660 = 41.36 cm².", tol=0.03, unit="cm²", pts=2),
         pn("How many pupils scored more than 60 marks?", 12, "40 − 28 = 12.", pts=1)]))
    p.mock("2", "GCE O Level mock 2 — Mathematics (number, algebra, mensuration, trigonometry)", 150,
           "Answer all four questions. Show all your working. Calculators may be used. Give non-exact answers to 2 decimal places unless told otherwise. Practice paper out of 20; duration and format to be checked against the official Cameroon GCE Board documents.",
           [("Section A — Commercial arithmetic and algebra", [q1, q2]), ("Section B — Mensuration and trigonometry", [q3, q4])])
