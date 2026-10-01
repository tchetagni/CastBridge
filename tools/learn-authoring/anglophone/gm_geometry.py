from _kit import *
import math
sin = lambda d: math.sin(math.radians(d)); cos = lambda d: math.cos(math.radians(d)); tan = lambda d: math.tan(math.radians(d))

def build(p):
    ch = p.chapter("geometry", "Geometry: angles, circles, similarity and loci", "O Level Mathematics — Geometry (to be checked)")

    # ------------------------------------------------------------- angles & polygons
    assert 360 / 30 == 12 and (12 - 2) * 180 == 1800 and 180 - 360 / 12 == 150
    assert (6 - 2) * 180 == 720 and 100 + 110 + 120 + 70 + 140 == 540
    it = [LINE(30, 70, 330, 70, width=2), LINE(30, 160, 330, 160, width=2), LINE(110, 20, 250, 210, color="red", width=2),
          T(140, 65 - 8, "a", 14), T(205, 90, "b", 14), T(165, 185, "c", 14), T(110, 145, "d", 14),
          T(60, 60, ">", 14), T(60, 150, ">", 14)]
    L = lesson(ch, "angles-polygons", "Angles and polygons",
        ["Use angle facts on straight lines, around a point, in triangles and with parallel lines.", "Find interior and exterior angles of polygons."],
        [("propriete", "Angle facts", "- Angles on a straight line add up to 180°; around a point, 360°.\n- Angles in a triangle: 180°; in a quadrilateral: 360°.\n- **Parallel lines**: corresponding angles are equal (F shape); alternate angles are equal (Z shape); co-interior angles add up to 180° (C or U shape).\n- Vertically opposite angles are equal."),
         ("formule", "Polygons", "For a polygon with n sides, the sum of the interior angles and each exterior angle of a **regular** polygon are:", "S = (n - 2) \\times 180^\\circ \\qquad \\text{exterior angle} = \\frac{360^\\circ}{n}"),
         ("pieges", "Common mistakes", "- Calling an angle 'alternate' when the lines are not parallel: the rules only work for parallel lines.\n- Confusing interior and exterior angles: at each vertex they add up to 180°.\n- Using n × 180 instead of (n − 2) × 180.")],
        [("Example 1: regular polygon", "Each exterior angle of a regular polygon is 30°. Find the number of sides and the sum of the interior angles.",
          ["n = 360 ÷ 30 = 12 sides.", "Sum of interior angles = (12 − 2) × 180 = 1 800°.", "Each interior angle = 180 − 30 = 150°; check 12 × 150 = 1 800."], "**12 sides; interior sum 1 800°.**",
          shapes(it, 360, 230)),
         ("Example 2: unknown angles", "The angles of a pentagon are 100°, 110°, 120°, x and 2x. Find x.",
          ["Sum of the interior angles of a pentagon = (5 − 2) × 180 = 540°.", "100 + 110 + 120 + 3x = 540, so 330 + 3x = 540 and 3x = 210.", "x = 70°, so the angles are 70° and 140°."], "**x = 70°.**")],
        [N("Find the sum of the interior angles of a hexagon.", 720, "(6 − 2) × 180 = 720°.", unit="°"),
         M("Two parallel lines are cut by a transversal. Co-interior angles add up to:", "180°", ["90°", "360°", "they are equal"], "Co-interior (allied) angles are supplementary."),
         N("Find the exterior angle of a regular octagon.", 45, "360 ÷ 8 = 45°.", unit="°")],
        [N("Each interior angle of a regular polygon is 150°. How many sides has it?", 12, "Exterior angle 30°; 360 ÷ 30 = 12.") ,
         N("Triangle ABC has angle A = 2x, B = 3x and C = 4x + 9. Find x.", 19, "9x + 9 = 180, so x = 19. Angles 38°, 57°, 85°.")],
        P("A tile pattern in a Douala market uses regular pentagons and other shapes.",
          [pn("Find each interior angle of a regular pentagon.", 108, "(5 − 2) × 180 ÷ 5 = 108°.", unit="°", pts=1),
           pn("Three regular pentagons are fitted around a point leaving a gap. Find the gap angle.", 36, "360 − 3 × 108 = 36°.", unit="°", pts=2),
           pm("Can regular pentagons alone tile a floor with no gaps?", "No", ["Yes", "Only in pairs", "Only with 4 around a point"], "108 does not divide 360, so they leave gaps.", 1)]),
        [("Angles on a straight line sum to:", "180°", ["90°", "360°", "270°"], "Straight line."),
         ("Alternate angles lie in a:", "Z shape", ["F shape", "C shape", "T shape"], "Equal when lines are parallel."),
         ("Sum of angles in a quadrilateral:", "360°", ["180°", "540°", "720°"], "(4 − 2) × 180."),
         ("Each interior angle of a regular hexagon:", "120°", ["60°", "108°", "135°"], "720 ÷ 6."),
         ("A triangle has angles 50° and 60°; the third is:", "70°", ["80°", "110°", "90°"], "180 − 110.")],
        minutes=30, notes=["In the example figure the angle labels a-d only mark the four angles formed by the transversal; the lesson text states the rules."])

    # ------------------------------------------------------------- circle theorems
    assert math.isclose(math.sqrt(13 ** 2 - 5 ** 2), 12)
    cx, cy, r = 200, 125, 90
    A = (cx + r * cos(210), cy - r * sin(210)); B = (cx + r * cos(330), cy - r * sin(330)); C = (cx, cy - r)
    fig = shapes([CIRCLE(cx, cy, r), LINE(*A, cx, cy), LINE(cx, cy, *B), LINE(*A, *C), LINE(*C, *B), T(cx, cy + 24, "70°", 14, color="red", bold=True), T(cx, cy - r + 36, "35°", 14, color="red", bold=True),
                  T(cx - r - 8, cy + 50, "A", 15), T(cx + r + 8, cy + 50, "B", 15), T(cx, cy - r - 8, "C", 15), T(cx + 4, cy + 4, "O", 14, anchor="start")], 400, 250)
    L = lesson(ch, "circle-theorems", "Circle theorems",
        ["State and use the main circle theorems.", "Find unknown angles in diagrams with circles, chords and tangents."],
        [("propriete", "Angle theorems", "- The angle at the **centre** is twice the angle at the circumference on the same arc.\n- The angle in a **semicircle** is 90°.\n- Angles in the **same segment** are equal.\n- **Opposite angles of a cyclic quadrilateral** add up to 180°."),
         ("propriete", "Tangent theorems", "- A tangent is perpendicular to the radius at the point of contact.\n- Tangents drawn from an outside point are equal in length.\n- **Alternate segment theorem**: the angle between a tangent and a chord equals the angle in the alternate segment.\n- A line from the centre perpendicular to a chord bisects it."),
         ("pieges", "Common mistakes", "- Using the 'twice' rule when the angles stand on different arcs.\n- In a cyclic quadrilateral it is **opposite** angles that sum to 180°, not adjacent ones.\n- Forgetting that the radius makes triangles isosceles: OA = OB.")],
        [("Example 1: centre and circumference", "In the figure, A, B and C are on a circle with centre O and angle ACB = 35°. Find angle AOB.",
          ["The angle at the centre is twice the angle at the circumference when both stand on the same arc AB.", "Angle AOB = 2 × 35° = 70°.", "Triangle AOB is isosceles (OA = OB), so each base angle = (180 − 70) ÷ 2 = 55°."], "**Angle AOB = 70°.**", fig),
         ("Example 2: tangent", "From a point P outside a circle of centre O and radius 5 cm, a tangent PT touches the circle at T. OP = 13 cm. Find PT.",
          ["The tangent is perpendicular to the radius: angle OTP = 90°.", "By Pythagoras: $PT^2 = OP^2 - OT^2 = 169 - 25 = 144$.", "PT = 12 cm."], "**PT = 12 cm.**")],
        [N("ABCD is a cyclic quadrilateral with angle A = 105°. Find angle C.", 75, "Opposite angles add up to 180°: 180 − 105 = 75°.", unit="°"),
         N("The angle at the centre standing on an arc is 124°. Find the angle at the circumference on the same arc.", 62, "Half: 62°.", unit="°"),
         M("The angle in a semicircle is:", "90°", ["180°", "45°", "60°"], "AB is a diameter.")],
        [N("A tangent and a chord make an angle of 48°. Find the angle in the alternate segment.", 48, "By the alternate segment theorem it is equal: 48°.", unit="°"),
         N("Two tangents from P touch a circle (centre O) at S and T, and angle SPT = 50°. Find angle SOT.", 130, "Quadrilateral OSPT has angles 90° (S) and 90° (T) and 50° at P: 360 − 230 = 130°.", unit="°")],
        P("AB is a diameter of a circle and C is on the circle with angle CAB = 38°.",
          [pn("Find angle ACB.", 90, "Angle in a semicircle.", unit="°", pts=1),
           pn("Find angle ABC.", 52, "180 − 90 − 38 = 52°.", unit="°", pts=1),
           pn("O is the centre. Find angle COB.", 76, "Angle at the centre = 2 × angle CAB = 76°.", unit="°", pts=2)]),
        [("Angles in the same segment are:", "equal", ["supplementary", "complementary", "double"], "Same arc."),
         ("A tangent meets the radius at:", "90°", ["45°", "180°", "60°"], "Perpendicular."),
         ("Opposite angles of a cyclic quadrilateral sum to:", "180°", ["90°", "360°", "270°"], "Supplementary."),
         ("Angle at centre 80°; at circumference:", "40°", ["160°", "80°", "100°"], "Half."),
         ("Tangents from one point to a circle are:", "equal in length", ["parallel", "perpendicular", "always 90° apart"], "Equal.")],
        minutes=30)

    # ------------------------------------------------------------- congruence & similarity
    assert 40 * 1.5 ** 2 == 90 and abs(500 * 1.5 ** 3 - 1687.5) < 1e-9 and 12 / 8 == 1.5
    fig = shapes([POLY([30, 170, 130, 170, 30, 110], fill="lightblue"), POLY([180, 170, 330, 170, 180, 80], fill="lightyellow"),
                  T(80, 190, "8", 14), T(255, 190, "12", 14), T(15, 140, "6", 14), T(165, 125, "9", 14), T(100, 120, "10", 14, color="grey"), T(280, 115, "15", 14, color="grey")], 360, 205)
    L = lesson(ch, "similarity", "Congruence and similarity",
        ["Use the conditions for congruent triangles.", "Use scale factors for lengths, areas and volumes of similar shapes."],
        [("propriete", "Congruent triangles", "Triangles are congruent (same shape and size) if: **SSS** (three sides), **SAS** (two sides and the included angle), **ASA** (two angles and a side) or **RHS** (right angle, hypotenuse, one side). SSA does not prove congruence in general."),
         ("formule", "Similar shapes", "Similar shapes have equal angles and corresponding sides in the same ratio, the scale factor k. Lengths scale by k, areas by k², volumes by k³:", "\\frac{A_2}{A_1} = k^2 \\qquad \\frac{V_2}{V_1} = k^3"),
         ("pieges", "Common mistakes", "- Using k for areas: if lengths double, areas become **four** times larger and volumes **eight** times larger.\n- Matching sides in the wrong order: match them using the equal angles.\n- Using SSA (two sides and a non-included angle) as a proof.")],
        [("Example 1: scale factor", "Two similar triangles have corresponding sides 8 cm and 12 cm. The smaller has area 40 cm². Find the scale factor and the area of the larger.",
          ["Scale factor k = 12 ÷ 8 = 1.5.", "Area scales by k² = 2.25.", "Larger area = 40 × 2.25 = 90 cm²."], "**k = 1.5; area 90 cm².**", fig),
         ("Example 2: volumes", "A bottle holds 500 ml. A similar bottle is 1.5 times as tall. Find its capacity.",
          ["Linear scale factor k = 1.5, so volume scale factor k³ = 3.375.", "Capacity = 500 × 3.375 = 1 687.5 ml."], "**1 687.5 ml.**")],
        [N("Two similar shapes have lengths in the ratio 2 : 3. The smaller has area 20 cm². Find the larger area.", 45, "k² = 9/4; 20 × 9/4 = 45 cm².", unit="cm²"),
         M("Which condition does NOT prove two triangles congruent?", "SSA (two sides and a non-included angle)", ["SSS", "SAS", "ASA"], "SSA is not a valid test (except as RHS)."),
         N("A model of a house has scale 1 : 50. A wall in the model is 12 cm. How long is the real wall, in metres?", 6, "12 × 50 = 600 cm = 6 m.", unit="m")],
        [N("The areas of two similar triangles are 16 cm² and 36 cm². The shorter side of the smaller is 4 cm. Find the corresponding side of the larger.", 6, "k² = 36/16 = 9/4, k = 3/2: 4 × 1.5 = 6 cm.", unit="cm"),
         N("Two similar cans hold 1 litre and 8 litres. The small can is 10 cm tall. Find the height of the large can.", 20, "k³ = 8, k = 2: 20 cm.", unit="cm")],
        P("A tree in Limbe casts a shadow 12 m long while a 1.5 m pole casts a shadow 2 m long at the same time.",
          [pm("The triangles formed are similar because:", "they have equal angles (sun at the same angle)", ["they have equal sides", "they are right-angled isosceles", "the shadows are equal"], "Both are right-angled with the same sun angle: AA.", 1),
           pn("Find the height of the tree.", 9, "Scale factor 12 ÷ 2 = 6; 1.5 × 6 = 9 m.", unit="m", pts=2),
           pn("A scale drawing uses 1 cm for 3 m. How long is the tree in the drawing (cm)?", 3, "9 ÷ 3 = 3 cm.", unit="cm", pts=1)]),
        [("Similar shapes have equal:", "angles", ["areas", "perimeters", "volumes"], "Same shape."),
         ("Lengths ×3 means areas ×:", "9", ["3", "6", "27"], "k² = 9."),
         ("Lengths ×2 means volumes ×:", "8", ["2", "4", "6"], "k³."),
         ("RHS stands for:", "right angle, hypotenuse, side", ["right angle, height, side", "ratio, height, side", "none"], "Right-angled triangle test."),
         ("Scale 1 : 100, 3 cm on the plan is:", "3 m", ["30 cm", "300 m", "0.3 m"], "300 cm.")],
        minutes=30)

    # ------------------------------------------------------------- constructions & loci
    assert math.isclose(math.sqrt(25 - 16), 3)
    fig = shapes([CIRCLE(100, 110, 80, stroke="grey", width=1), LINE(164, 20, 164, 200, color="red", width=2, dash=True), LINE(100, 110, 228, 110, width=2),
                  CIRCLE(100, 110, 4, fill="ink"), CIRCLE(228, 110, 4, fill="ink"), CIRCLE(164, 62, 5, fill="green", stroke="green"), CIRCLE(164, 158, 5, fill="green", stroke="green"),
                  T(100, 132, "A", 15), T(228, 132, "B", 15), T(180, 60, "P", 14, anchor="start"), T(180, 160, "Q", 14, anchor="start"), T(120, 18, "equidistant from A and B", 12, anchor="start", color="red")], 340, 220)
    L = lesson(ch, "constructions-loci", "Constructions and loci",
        ["Construct perpendicular bisectors, angle bisectors and triangles with ruler and compasses.", "Describe and draw loci and combine them to locate a point."],
        [("definition", "Locus", "A **locus** is the set of all points that satisfy a given rule. Four basic loci: points at a fixed distance from a point (a circle); from a line (two parallel lines with semicircular ends); equidistant from two points (the perpendicular bisector of the segment); equidistant from two lines (the angle bisector)."),
         ("methode", "Constructions", "- **Perpendicular bisector** of AB: arcs of equal radius (more than half of AB) from A and from B; join the two crossing points.\n- **Angle bisector**: an arc cuts both arms; from those two points draw equal arcs; join the vertex to where they cross.\n- **Triangle with three sides**: draw one side, then arcs of the other two lengths from its ends."),
         ("pieges", "Common mistakes", "- Rubbing out construction arcs: they must stay visible.\n- Changing the compass setting between the two arcs of a bisector.\n- Forgetting that a locus 'at a fixed distance from a line' has two sides.")],
        [("Example 1: combining loci", "A and B are 8 m apart. A treasure is buried 5 m from A and equidistant from A and B. How far apart are the two possible positions?",
          ["Equidistant from A and B: the perpendicular bisector, 4 m from A. Take A = (0, 0), B = (8, 0).", "5 m from A: circle of radius 5. On the bisector (x = 4): $y^2 = 25 - 16 = 9$, so y = ±3.", "The points are (4, 3) and (4, −3): 6 m apart."], "**6 m.**", fig),
         ("Example 2: angle bisector", "Two roads meet at 60°. A well is to be equidistant from both roads. At what angle to each road is the line of possible positions?",
          ["The points equidistant from two lines lie on the angle bisector.", "The bisector divides 60° into two equal parts: 30° and 30°."], "**30° to each road.**")],
        [M("The locus of points 3 cm from a fixed point O is:", "a circle of radius 3 cm", ["a straight line", "two parallel lines", "a perpendicular bisector"], "Fixed distance from a point."),
         M("The locus of points equidistant from two fixed points is:", "the perpendicular bisector of the line joining them", ["a circle", "the angle bisector", "a parallel line"], "Standard locus."),
         N("A goat is tied to a post by a 4 m rope. Find the area it can graze (π = 3.14).", 50.24, "Area = πr² = 3.14 × 16 = 50.24 m².", tol=0.01, unit="m²")],
        [N("Points A(0, 0) and B(6, 0). Find the y-coordinate (positive) of the point equidistant from A and B that is 5 from A.", 4, "x = 3; y² = 25 − 9 = 16, y = 4."),
         O("Describe how to construct the bisector of an angle ABC using compasses only for marking.", "Place the compass point at B and draw an arc crossing both arms. From each crossing point draw arcs of the same radius to meet inside the angle. Join B to the meeting point; this line bisects the angle.", ["Arc from vertex cutting both arms (1 mark)", "Two equal arcs meeting inside the angle (1 mark)", "Line joining vertex to the intersection (1 mark)"])],
        P("A radio mast stands at M in Garoua. The company wants a point P that is no more than 200 m from M and nearer to the road R than 100 m (the road is a straight line, M is 150 m from the road).",
          [pm("The set of points within 200 m of M is:", "the inside of a circle of radius 200 m", ["a strip 200 m wide", "a line", "the outside of the circle"], "Fixed distance from a point.", 1),
           pm("The set of points within 100 m of the road is:", "a strip 100 m either side of the road", ["a circle", "one line", "a half-plane"], "Locus from a line.", 1),
           pn("Shortest distance from M to the 100 m strip edge on its side (m).", 50, "150 − 100 = 50 m.", unit="m", pts=2)]),
        [("The locus of points equidistant from two lines is the:", "angle bisector", ["perpendicular bisector", "median", "diameter"], "Standard."),
         ("To bisect a line you construct a:", "perpendicular bisector", ["tangent", "parallel", "median"], "Standard."),
         ("A triangle with sides 3, 4, 5 has a right angle opposite:", "5", ["3", "4", "none"], "Pythagoras."),
         ("Locus of points 2 cm from a line (both sides):", "two parallel lines with semicircular ends", ["a circle", "one line", "a square"], "Rounded ends at the ends of the segment."),
         ("Construction arcs should be:", "left on the diagram", ["rubbed out", "drawn in ink only", "avoided"], "They show method.")],
        minutes=30)
