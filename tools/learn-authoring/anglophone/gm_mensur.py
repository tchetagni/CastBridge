from _kit import *
import math

def build(p):
    ch = p.chapter("mensuration", "Mensuration: lengths, areas and volumes", "O Level Mathematics — Mensuration (to be checked)")
    pi = math.pi

    # ------------------------------------------------------------- area / arcs
    arc = 150 / 360 * 2 * pi * 12; sec = 150 / 360 * pi * 144
    assert abs(arc - 31.416) < 1e-3 and abs(sec - 188.496) < 1e-3
    trap = 0.5 * (8 + 14) * 6; assert trap == 66
    comp = 10 * 6 + 0.5 * pi * 3 ** 2; assert abs(comp - 74.137) < 1e-3
    sx, sy, R = 110, 165, 100
    a2 = math.radians(150)
    ex, ey = sx + R * math.cos(0), sy
    fx_, fy_ = sx + R * math.cos(a2), sy - R * math.sin(a2)
    fig = shapes([PATH("M %d %d L %.1f %.1f A %d %d 0 0 0 %.1f %.1f Z" % (sx, sy, ex, ey, R, R, fx_, fy_), fill="lightblue"),
                  T(sx + 28, sy - 14, "150°", 14, bold=True), T(sx + 50, sy + 22, "12 cm", 14), T(sx + R - 70, sy - R + 15, "arc", 13, color="red")], 260, 200)
    L = lesson(ch, "area-arc", "Perimeter, area, arcs and sectors",
        ["Calculate the area of triangles, parallelograms, trapezia and circles.", "Calculate arc length and sector area.", "Solve problems on composite shapes."],
        [("formule", "Area formulae", "Triangle, parallelogram, trapezium (a and b parallel sides, h the height), circle:", "\\frac{1}{2}bh \\qquad bh \\qquad \\frac{1}{2}(a + b)h \\qquad \\pi r^2"),
         ("formule", "Circle: arc and sector", "For a sector of angle θ (in degrees) of a circle of radius r:", "\\text{arc} = \\frac{\\theta}{360} \\times 2\\pi r \\qquad \\text{sector area} = \\frac{\\theta}{360} \\times \\pi r^2"),
         ("pieges", "Common mistakes", "- Using the slant side instead of the perpendicular height.\n- Confusing circumference $2\\pi r$ and area $\\pi r^2$; using the diameter in place of the radius.\n- Forgetting the straight sides when the perimeter of a sector is required: perimeter = arc + 2r.")],
        [("Example 1: sector", "A sector of a circle of radius 12 cm has angle 150°. Find the arc length and the area (π from the calculator, 1 d.p.).",
          ["Fraction of the circle: 150/360 = 5/12.", "Arc = 5/12 × 2π × 12 = 10π ≈ 31.4 cm.", "Area = 5/12 × π × 144 = 60π ≈ 188.5 cm²."], "**Arc ≈ 31.4 cm; area ≈ 188.5 cm².**", fig),
         ("Example 2: composite shape", "A window is a 10 m by 6 m rectangle with a semicircle of diameter 6 m on one of the 6 m sides. Find its area.",
          ["Rectangle: 10 × 6 = 60 m².", "Semicircle: radius 3 m: ½ × π × 9 = 14.14 m².", "Total ≈ 74.14 m²."], "**≈ 74.14 m².**")],
        [N("Find the area of a trapezium with parallel sides 8 cm and 14 cm and height 6 cm.", 66, "½ × (8 + 14) × 6 = 66 cm².", unit="cm²"),
         N("Find the circumference of a circle of radius 7 cm (π = 22/7).", 44, "2 × 22/7 × 7 = 44 cm.", unit="cm"),
         M("Find the area of a circle of diameter 10 cm (to 1 d.p.).", "78.5 cm²", ["314.2 cm²", "31.4 cm²", "157.1 cm²"], "r = 5: π × 25 = 78.5. The diameter was not squared; 31.4 is the circumference.")],
        [N("A sector has radius 9 cm and angle 80°. Find the arc length (1 d.p.).", round(80 / 360 * 2 * pi * 9, 1), "80/360 × 2π × 9 = 12.57, so 12.6 cm.", tol=0.1, unit="cm"),
         N("The area of a circle is 154 cm². Find the radius (π = 22/7).", 7, "r² = 154 × 7/22 = 49, r = 7 cm.", unit="cm")],
        P("A circular fish pond in Kumba has radius 3.5 m and is surrounded by a path 1 m wide.",
          [pn("Area of the pond (π = 22/7).", 38.5, "22/7 × 3.5² = 38.5 m².", tol=0.01, unit="m²", pts=1),
           pn("Radius of the pond plus path.", 4.5, "3.5 + 1 = 4.5 m.", unit="m", pts=1),
           pn("Area of the path (π = 22/7, nearest m²).", round(22 / 7 * (4.5 ** 2 - 3.5 ** 2)), "22/7 × (20.25 − 12.25) = 22/7 × 8 = 25.14 ≈ 25 m².", tol=0.5, unit="m²", pts=2)]),
        [("Area of a triangle:", "½ × base × height", ["base × height", "½ × side²", "2 × base × height"], "Standard."),
         ("Circumference of a circle:", "2πr", ["πr²", "πd²", "4πr"], "Standard."),
         ("A quarter-circle sector has angle:", "90°", ["45°", "180°", "270°"], "1/4 of 360."),
         ("Area of a parallelogram base 6 and height 4:", "24", ["10", "12", "20"], "bh."),
         ("Perimeter of a sector of radius r and arc l:", "l + 2r", ["l + r", "2πr", "l × r"], "Arc plus two radii.")],
        minutes=30)

    # ------------------------------------------------------------- volume & surface area
    cyl = pi * 0.5 ** 2 * 2; assert abs(cyl * 1000 - 1570.8) < 0.1
    cone_v, cone_c, cone_t = pi * 9 * 4 / 3, pi * 3 * 5, pi * 3 * 5 + pi * 9
    assert abs(cone_v - 37.699) < 1e-3 and abs(cone_c - 47.124) < 1e-3 and abs(cone_t - 75.398) < 1e-3
    sph = 4 / 3 * pi * 27; assert abs(sph - 113.097) < 1e-3
    fig = shapes([PATH("M 90 160 A 60 15 0 0 0 210 160 A 60 15 0 0 0 90 160", fill="lightyellow"), LINE(90, 160, 150, 30), LINE(210, 160, 150, 30),
                  LINE(150, 30, 150, 160, dash=True, color="red"), LINE(150, 160, 210, 160, dash=True, color="blue"),
                  T(156, 100, "h = 4", 14, anchor="start", color="red"), T(185, 188, "r = 3", 14, color="blue"), T(212, 90, "l = 5", 14, anchor="start")], 300, 200)
    L = lesson(ch, "volume-surface", "Volume and surface area of solids",
        ["Calculate volumes and surface areas of prisms, cylinders, cones, pyramids and spheres.", "Convert between cm³, m³ and litres."],
        [("formule", "Volumes", "Prism: area of cross-section × length. Cylinder, cone, pyramid and sphere:", "V = \\pi r^2 h \\qquad V = \\frac{1}{3}\\pi r^2 h \\qquad V = \\frac{1}{3} A h \\qquad V = \\frac{4}{3}\\pi r^3"),
         ("formule", "Surface areas", "Cylinder (closed), cone (slant height l, with base) and sphere:", "2\\pi r^2 + 2\\pi r h \\qquad \\pi r l + \\pi r^2 \\qquad 4\\pi r^2"),
         ("propriete", "Units", "1 m³ = 1 000 000 cm³ and 1 litre = 1 000 cm³, so 1 m³ = 1 000 litres. The slant height of a cone is found by Pythagoras: $l^2 = r^2 + h^2$."),
         ("pieges", "Common mistakes", "- Using the slant height in the volume formula (use the perpendicular height h).\n- Forgetting the ⅓ for cones and pyramids.\n- Converting units wrongly: 1 m³ is 1 000 litres, not 100.")],
        [("Example 1: a cylinder", "A water tank in Bamenda is a cylinder of radius 0.5 m and height 2 m. Find its capacity in litres (to the nearest litre).",
          ["V = π × 0.5² × 2 = 0.5π m³ ≈ 1.5708 m³.", "1 m³ = 1 000 litres: 1.5708 × 1 000 = 1 570.8 litres.", "To the nearest litre: 1 571 litres."], "**≈ 1 571 litres.**"),
         ("Example 2: a cone", "A cone has base radius 3 cm and height 4 cm. Find the slant height, the volume and the total surface area (1 d.p.).",
          ["Slant height: $l = \\sqrt{3^2 + 4^2} = 5$ cm.", "Volume = ⅓ × π × 9 × 4 = 12π ≈ 37.7 cm³.", "Curved area πrl = 15π; base π r² = 9π; total = 24π ≈ 75.4 cm²."], "**l = 5 cm; V ≈ 37.7 cm³; surface ≈ 75.4 cm².**", fig)],
        [N("Find the volume of a cuboid 8 cm by 5 cm by 3 cm.", 120, "8 × 5 × 3 = 120 cm³.", unit="cm³"),
         N("Convert 2.5 m³ to litres.", 2500, "× 1 000.", unit="litres"),
         M("Find the volume of a sphere of radius 3 cm (to 1 d.p.).", "113.1 cm³", ["37.7 cm³", "339.3 cm³", "56.5 cm³"], "4/3 × π × 27 = 36π = 113.1.")],
        [N("A solid metal cylinder of radius 4 cm and height 10 cm is melted and recast into small spheres of radius 2 cm, with no metal wasted. How many spheres are made?", 15, "Cylinder: 160π cm³. Sphere: 4/3 × π × 8 = 32π/3 cm³. 160π ÷ (32π/3) = 15 spheres.", unit="spheres"),
         N("A square-based pyramid has base 6 cm by 6 cm and height 7 cm. Find its volume.", 84, "⅓ × 36 × 7 = 84 cm³.", unit="cm³")],
        P("A cylindrical drum for palm oil in Douala has radius 30 cm and height 90 cm (π = 3.14).",
          [pn("Find the volume in cm³.", 3.14 * 900 * 90, "3.14 × 30² × 90 = 3.14 × 81 000 = 254 340 cm³.", tol=1, unit="cm³", pts=1),
           pn("Find its capacity in litres.", 254.34, "254 340 ÷ 1 000 = 254.34 litres.", tol=0.01, unit="litres", pts=1),
           pn("Find the curved surface area in cm² (2πrh).", 2 * 3.14 * 30 * 90, "2 × 3.14 × 30 × 90 = 16 956 cm².", tol=1, unit="cm²", pts=2)]),
        [("Volume of a cone:", "⅓πr²h", ["πr²h", "⅓πr²l", "⅔πr²h"], "Third of the cylinder."),
         ("1 litre equals:", "1 000 cm³", ["100 cm³", "10 000 cm³", "1 m³"], "Standard."),
         ("Surface area of a sphere:", "4πr²", ["4πr³/3", "2πr²", "πr²"], "Standard."),
         ("The slant height of a cone with r = 6, h = 8 is:", "10", ["14", "2", "7"], "√(36 + 64)."),
         ("A cube of side 5 cm has surface area:", "150 cm²", ["125 cm²", "25 cm²", "30 cm²"], "6 × 25.")],
        minutes=30)
