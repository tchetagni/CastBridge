from _kit import *
import math
sin = lambda d: math.sin(math.radians(d)); cos = lambda d: math.cos(math.radians(d)); tan = lambda d: math.tan(math.radians(d))
acos = lambda v: math.degrees(math.acos(v)); atan = lambda v: math.degrees(math.atan(v))

def build(p):
    ch = p.chapter("trig2", "Further trigonometry", "O Level Mathematics — Trigonometry: sine and cosine rules, three dimensions (to be checked)")

    # ------------------------------------------------------------- sine & cosine rule
    b1 = 8 * sin(40) / sin(75); c1 = 8 * sin(65) / sin(75); assert abs(b1 - 5.324) < 1e-3 and abs(c1 - 7.506) < 1e-3
    a2 = math.sqrt(49 + 81 - 2 * 7 * 9 * cos(60)); assert abs(a2 ** 2 - 67) < 1e-9 and abs(a2 - 8.185) < 1e-3
    area = 0.5 * 7 * 9 * sin(60); assert abs(area - 27.28) < 0.01
    C3 = acos((25 + 36 - 49) / (2 * 5 * 6)); assert abs(C3 - 78.46) < 0.01
    fig = shapes([POLY([60, 190, 300, 190, 150, 50], fill="lightblue"), T(40, 205, "B", 15), T(318, 205, "C", 15), T(150, 38, "A", 15),
                  T(180, 212, "a", 15, color="red", bold=True), T(90, 112, "c", 15, color="red", bold=True), T(235, 110, "b", 15, color="red", bold=True)], 350, 225)
    L = lesson(ch, "sine-cosine-rule", "Sine rule, cosine rule and area of a triangle",
        ["Use the sine rule to find a side or an angle.", "Use the cosine rule for two sides and the included angle, or three sides.", "Find the area of a triangle using ½ab sin C."],
        [("formule", "Sine rule", "In triangle ABC with sides a, b, c opposite angles A, B, C. Use it when you know a side and its opposite angle plus one more angle or side:", "\\frac{a}{\\sin A} = \\frac{b}{\\sin B} = \\frac{c}{\\sin C}"),
         ("formule", "Cosine rule and area", "Use the cosine rule for two sides and the included angle (to find the third side), or for three sides (to find an angle). The area uses two sides and the included angle:", "a^2 = b^2 + c^2 - 2bc\\cos A \\qquad \\cos A = \\frac{b^2 + c^2 - a^2}{2bc} \\qquad \\text{Area} = \\frac{1}{2}ab\\sin C"),
         ("methode", "Which rule?", "- Know **two angles and a side**, or two sides and a non-included angle: **sine rule**.\n- Know **two sides and the included angle**, or **three sides**: **cosine rule**.\n- Always label the triangle first (side a opposite angle A) and keep your calculator in degree mode."),
         ("pieges", "Common mistakes", "- Mixing up which angle is opposite which side.\n- In the cosine rule, doing $(b^2 + c^2 - 2bc) \\cos A$: multiply 2bc by cos A **before** subtracting.\n- Rounding too early: keep at least 4 significant figures until the end.")],
        [("Example 1: sine rule", "In triangle ABC, a = 8 cm, B = 40° and C = 65°. Find the angle A and the sides b and c (to 2 decimal places).",
          ["A = 180 − 40 − 65 = 75°.", "$b = \\frac{8 \\sin 40^\\circ}{\\sin 75^\\circ} \\approx 5.32$ cm.", "$c = \\frac{8 \\sin 65^\\circ}{\\sin 75^\\circ} \\approx 7.51$ cm."], "**A = 75°, b ≈ 5.32 cm, c ≈ 7.51 cm.**", fig),
         ("Example 2: cosine rule and area", "In a triangle, b = 7 cm, c = 9 cm and the included angle A = 60°. Find a and the area (2 d.p.). Then find the largest angle of a triangle with sides 5, 6 and 7 cm.",
          ["$a^2 = 49 + 81 - 2 \\times 7 \\times 9 \\times \\cos 60^\\circ = 130 - 63 = 67$, so a ≈ 8.19 cm.", "Area = ½ × 7 × 9 × sin 60° ≈ 27.28 cm².", "Largest angle is opposite 7: cos C = (25 + 36 − 49) ÷ 60 = 0.2, so C ≈ 78.46°."], "**a ≈ 8.19 cm; area ≈ 27.28 cm²; largest angle ≈ 78.46°.**")],
        [N("Triangle with a = 10, A = 30°, B = 45°. Find b to 2 d.p.", round(10 * sin(45) / sin(30), 2), "b = 10 sin 45° ÷ sin 30° = 14.14 cm.", tol=0.01, unit="cm"),
         N("Find the area of a triangle with sides 6 cm and 8 cm and included angle 30°.", 12, "½ × 6 × 8 × sin 30° = 12 cm².", unit="cm²"),
         M("To find the third side given two sides and the included angle, use:", "the cosine rule", ["the sine rule", "Pythagoras only", "the area formula only"], "SAS: cosine rule.")],
        [N("Triangle sides 7, 8 and 9. Find the smallest angle (1 d.p.).", round(acos((64 + 81 - 49) / (2 * 8 * 9)), 1), "Smallest angle is opposite 7: cos = 96/144 = 0.667; angle 48.2°.", tol=0.1, unit="°"),
         N("In triangle PQR, p = 12, q = 9 and P = 80°. Find angle Q (1 d.p.).", round(math.degrees(math.asin(9 * sin(80) / 12)), 1), "sin Q = 9 sin 80° ÷ 12 = 0.7386, so Q = 47.6° (Q must be acute because q < p).", tol=0.1, unit="°")],
        P("Two paths in a park in Limbe leave a gate G. Path 1 goes 300 m to A; path 2 goes 400 m to B. The angle AGB is 70°.",
          [pn("Find the distance AB to the nearest metre.", round(math.sqrt(300 ** 2 + 400 ** 2 - 2 * 300 * 400 * cos(70))), "AB² = 90 000 + 160 000 − 240 000 cos 70° = 250 000 − 82 085 = 167 915; AB ≈ 410 m.", tol=1, unit="m", pts=3),
           pn("Find the area of triangle AGB to the nearest 100 m².", round(0.5 * 300 * 400 * sin(70), -2), "½ × 300 × 400 × sin 70° = 56 382; nearest 100: 56 400 m².", tol=50, unit="m²", pts=2)]),
        [("The sine rule links:", "sides and opposite angles", ["only sides", "only angles", "perimeter"], "a/sin A."),
         ("Area = ½ab sin C uses:", "two sides and the included angle", ["three sides", "base and slant", "two angles"], "Standard."),
         ("cos 60° =", "0.5", ["0.866", "1", "0"], "Standard value."),
         ("For three known sides use:", "the cosine rule", ["the sine rule", "the area rule", "tan"], "SSS."),
         ("sin 30° =", "0.5", ["0.866", "1", "0.707"], "Standard value.")],
        minutes=35)

    # ------------------------------------------------------------- 3D
    d = math.sqrt(8 ** 2 + 6 ** 2 + 5 ** 2); assert abs(d - 11.18) < 0.01
    th = atan(5 / 10); assert abs(th - 26.565) < 0.001
    edge = math.sqrt(18 + 16); ang = atan(4 / math.sqrt(18)); assert abs(edge - 5.831) < 1e-3 and abs(ang - 43.31) < 0.01
    fig = shapes([LINE(60, 190, 200, 190), LINE(200, 190, 260, 150), LINE(60, 190, 60, 90), LINE(200, 190, 200, 90), LINE(260, 150, 260, 50), LINE(60, 90, 200, 90), LINE(200, 90, 260, 50), LINE(60, 90, 120, 50), LINE(120, 50, 260, 50),
                  LINE(60, 190, 120, 150, dash=True, color="grey"), LINE(120, 150, 260, 150, dash=True, color="grey"), LINE(120, 150, 120, 50, dash=True, color="grey"),
                  LINE(60, 190, 260, 50, color="red", width=3), LINE(60, 190, 260, 150, color="blue", width=2, dash=True),
                  T(45, 205, "A", 15), T(212, 208, "B", 15), T(275, 160, "C", 15), T(278, 50, "G", 15), T(110, 140, "D", 14, color="grey"),
                  T(150, 205, "8", 14), T(38, 145, "5", 14), T(240, 187, "6", 14, anchor="start")], 320, 230)
    L = lesson(ch, "three-d", "Trigonometry in three dimensions",
        ["Find lengths in a cuboid and pyramid using Pythagoras in 3-D.", "Find the angle between a line and a plane."],
        [("methode", "Method", "1. Draw the 3-D figure and pick out **right-angled triangles** (one at a time, as a plane picture).\n2. Use Pythagoras to find lengths such as a diagonal of the base.\n3. The **angle between a line and a plane** is the angle between the line and its **projection** on the plane (the shadow).\n4. Then use sin, cos or tan in the right-angled triangle."),
         ("formule", "Space diagonal", "For a cuboid of length l, width w and height h, the space diagonal d is:", "d = \\sqrt{l^2 + w^2 + h^2}"),
         ("pieges", "Common mistakes", "- Using a slanted length as a height; heights are perpendicular to the base.\n- Measuring the angle from the wrong line: it must be from the line to its projection.\n- Redrawing the triangle with the wrong sides: always write the sides you are using.")],
        [("Example 1: cuboid", "A cuboid ABCDEFGH has AB = 8 cm, BC = 6 cm and CG = 5 cm. Find the diagonal AG and the angle it makes with the base.",
          ["The base diagonal AC = √(8² + 6²) = 10 cm.", "AG = √(10² + 5²) = √125 ≈ 11.18 cm.", "Angle GAC: tan θ = CG ÷ AC = 5 ÷ 10, so θ ≈ 26.6°."], "**AG ≈ 11.18 cm; angle ≈ 26.6°.**", fig),
         ("Example 2: pyramid", "A pyramid has a square base of side 6 cm and its apex is 4 cm vertically above the centre of the base. Find the length of a sloping edge and the angle it makes with the base.",
          ["Half the base diagonal = ½ × 6√2 = 3√2 ≈ 4.243 cm (its square is 18).", "Edge = √(18 + 16) = √34 ≈ 5.83 cm.", "Angle with base: tan θ = 4 ÷ √18, so θ ≈ 43.3°."], "**Edge ≈ 5.83 cm; angle ≈ 43.3°.**")],
        [N("Find the space diagonal of a cube of side 4 cm (2 d.p.).", round(4 * math.sqrt(3), 2), "√(16 + 16 + 16) = 4√3 ≈ 6.93 cm.", tol=0.01, unit="cm"),
         N("Find the diagonal of the base of a 12 cm by 5 cm rectangle.", 13, "√(144 + 25) = 13.", unit="cm"),
         M("The angle between a line and a plane is measured between:", "the line and its projection on the plane", ["the line and the vertical", "two lines in the plane", "the line and a point"], "Definition.")],
        [N("A vertical pole 6 m high stands at the corner A of a rectangular field 8 m by 15 m. Find the angle of elevation of its top from the opposite corner (1 d.p.).", round(atan(6 / 17), 1), "Field diagonal = 17 m; tan θ = 6/17, θ = 19.4°.", tol=0.1, unit="°"),
         N("A cuboid 9 cm by 12 cm by 8 cm. Find the space diagonal.", 17, "√(81 + 144 + 64) = √289 = 17 cm.", unit="cm")],
        P("A tent in Bamenda is a pyramid with a square base of side 4 m and vertical height 3 m above the centre of the base.",
          [pn("Find the distance from the centre of the base to a corner (2 d.p.).", round(math.sqrt(8), 2), "Half the diagonal: √(2² + 2²) = √8 = 2.83 m.", tol=0.01, unit="m", pts=1),
           pn("Find the length of a sloping edge (2 d.p.).", round(math.sqrt(8 + 9), 2), "√(8 + 9) = √17 = 4.12 m.", tol=0.01, unit="m", pts=2),
           pn("Find the volume of the tent (⅓ × base area × height).", 16, "⅓ × 16 × 3 = 16 m³.", unit="m³", pts=2)]),
        [("Space diagonal of a 1 × 2 × 2 box:", "3", ["5", "√5", "9"], "√(1 + 4 + 4)."),
         ("Heights are measured:", "perpendicular to the base", ["along the slope", "along an edge", "at 45°"], "Vertical."),
         ("A 3-4-5 right triangle has hypotenuse:", "5", ["7", "12", "25"], "Pythagoras."),
         ("tan θ = 1 means θ =", "45°", ["30°", "60°", "90°"], "Equal opposite and adjacent."),
         ("Diagonal of a square of side 5:", "5√2", ["10", "25", "√5"], "√50.")],
        minutes=35)

    # ============================================================= statistics extras
    ch2 = p.chapter("stats2", "Statistics: averages, cumulative frequency and histograms", "O Level Mathematics — Statistics (to be checked)")
    xs = [2, 3, 4, 5, 6]; fs = [3, 5, 8, 6, 2]
    n = sum(fs); sfx = sum(x * f for x, f in zip(xs, fs)); assert (n, sfx) == (24, 95) and abs(sfx / n - 3.9583) < 1e-3
    mids = [144.5, 154.5, 164.5, 174.5, 184.5]; gf = [4, 10, 14, 8, 4]
    gm = sum(m * f for m, f in zip(mids, gf)) / sum(gf); assert gm == 164.0
    L = lesson(ch2, "averages", "Mean, median, mode and range for tables",
        ["Find the mean, median, mode and range from a frequency table.", "Estimate the mean of grouped data using mid-class values.", "Choose a suitable average."],
        [("formule", "The mean from a table", "For a frequency table with values x and frequencies f (and grouped data using the **mid-class value** for x):", "\\bar{x} = \\frac{\\sum fx}{\\sum f}"),
         ("methode", "Median, mode, range", "- **Mode**: the value with the greatest frequency (modal class for grouped data).\n- **Median**: the middle value when ordered: position $\\frac{n+1}{2}$; use the cumulative frequency to find it.\n- **Range** = largest value − smallest value."),
         ("pieges", "Common mistakes", "- Dividing Σfx by the **number of rows** instead of Σf.\n- Reading the highest frequency as the mode: the mode is the **value** with that frequency.\n- Using class boundaries instead of mid-class values for the grouped mean.")],
        [("Example 1: frequency table", "The marks of 24 students out of 6 are: 2 (3 students), 3 (5), 4 (8), 5 (6), 6 (2). Find the mean, median and mode.",
          ["Σf = 24 and Σfx = 6 + 15 + 32 + 30 + 12 = 95. Mean = 95 ÷ 24 ≈ 3.96.", "Cumulative frequencies: 3, 8, 16, 22, 24. The 12th and 13th values are both 4: median 4.", "The highest frequency, 8, is at the mark 4: mode 4."], "**Mean ≈ 3.96; median 4; mode 4.**", bars([("2", 3), ("3", 5), ("4", 8, "green"), ("5", 6), ("6", 2)], unit="students")),
         ("Example 2: grouped data", "Heights (cm) of 40 students: 140–149 (4), 150–159 (10), 160–169 (14), 170–179 (8), 180–189 (4). Estimate the mean and state the modal class.",
          ["Mid-class values: 144.5, 154.5, 164.5, 174.5, 184.5.", "Σfx = 578 + 1 545 + 2 303 + 1 396 + 738 = 6 560, so the mean = 6 560 ÷ 40 = 164.0 cm.", "The largest frequency is 14: modal class 160–169."], "**Mean ≈ 164 cm; modal class 160–169 cm.**")],
        [N("Find the mean of 4, 7, 7, 9, 13.", 8, "40 ÷ 5 = 8."),
         N("Find the median of 3, 9, 4, 12, 8, 6.", 7, "Ordered: 3, 4, 6, 8, 9, 12. The middle two are 6 and 8: median 7."),
         M("The mode of 2, 5, 5, 7, 9, 5, 2 is:", "5", ["2", "7", "9"], "5 occurs three times.")],
        [N("The mean of 5 numbers is 12. A sixth number is added and the mean becomes 13. Find the sixth number.", 18, "Total was 60; new total 78; sixth = 18."),
         N("In the table of Example 1 find the range.", 4, "6 − 2 = 4.")],
        P("The daily number of customers at a bakery in Bafoussam over 20 days: 10 (2 days), 12 (5 days), 15 (8 days), 20 (5 days).",
          [pn("Find the mean number of customers.", (10 * 2 + 12 * 5 + 15 * 8 + 20 * 5) / 20, "Σfx = 20 + 60 + 120 + 100 = 300; 300 ÷ 20 = 15.", tol=0.01, pts=2),
           pn("Find the median.", 15, "Cumulative: 2, 7, 15, 20; the 10th and 11th values are both 15.", pts=1),
           pn("Find the mode.", 15, "Greatest frequency (8) is at 15.", pts=1)]),
        [("The range is:", "largest − smallest", ["largest + smallest", "mean − mode", "half the total"], "Spread."),
         ("The mean from a table is:", "Σfx ÷ Σf", ["Σx ÷ Σf", "Σf ÷ Σx", "Σfx ÷ n rows"], "Standard."),
         ("The mid-class value of 150–159 is:", "154.5", ["150", "159", "155"], "(149.5 + 159.5) ÷ 2."),
         ("Which average is least affected by one very large value?", "median", ["mean", "range", "total"], "Robust."),
         ("The median of 1, 2, 3, 4, 5:", "3", ["2.5", "15", "5"], "Middle.")],
        minutes=30)

    # ---- cumulative frequency
    ub = [20, 40, 60, 80, 100]; cf = [5, 17, 35, 45, 50]; f_ = [5, 12, 18, 10, 5]
    interp = lambda t: next(lo + (t - c0) / (c1 - c0) * (hi - lo) for lo, hi, c0, c1 in zip([0] + ub[:-1], ub, [0] + cf[:-1], cf) if c0 <= t <= c1)
    med, q1, q3 = interp(25), interp(12.5), interp(37.5)
    assert abs(med - 48.89) < 0.01 and q1 == 32.5 and q3 == 65 and q3 - q1 == 32.5
    cf_at = lambda m: next(c0 + (m - lo) / (hi - lo) * (c1 - c0) for lo, hi, c0, c1 in zip([0] + ub[:-1], ub, [0] + cf[:-1], cf) if lo <= m <= hi)
    assert cf_at(70) == 40 and 50 - cf_at(70) == 10
    pts = [(0, 0)] + list(zip(ub, cf))
    segs = [(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], "blue", False) for i in range(len(pts) - 1)]
    segs += [(0, 25, 48.89, 25, "orange", True, "median"), (48.89, 0, 48.89, 25, "orange", True)]
    L = lesson(ch2, "cumulative-frequency", "Cumulative frequency, median and quartiles",
        ["Build a cumulative frequency table and draw the curve (ogive).", "Estimate the median, quartiles and interquartile range from the curve.", "Read the number of values above or below a given value."],
        [("methode", "Method", "1. Add the frequencies to get the **cumulative frequency** (running total) and plot it against the **upper class boundary**.\n2. Join the points with a smooth curve (or straight segments), starting at the lowest boundary with cumulative frequency 0.\n3. For n values, read the **median** at n/2, the **lower quartile** Q1 at n/4 and the **upper quartile** Q3 at 3n/4."),
         ("formule", "Spread", "The interquartile range measures the spread of the middle half of the data:", "\\text{IQR} = Q_3 - Q_1"),
         ("pieges", "Common mistakes", "- Plotting against the class midpoint or the lower boundary: use the **upper** boundary.\n- Reading the median at (n + 1)/2 on a grouped curve: use n/2.\n- Reading the wrong axis: horizontal for the data value, vertical for the cumulative frequency.")],
        [("Example 1: medians and quartiles", "The marks of 50 students (upper boundaries 20, 40, 60, 80, 100) have frequencies 5, 12, 18, 10, 5. Find the cumulative frequencies and estimate the median.",
          ["Cumulative frequencies: 5, 17, 35, 45, 50.", "The median is at 50 ÷ 2 = 25th value, in the class 40–60 (cumulative 17 to 35).", "By interpolation: 40 + (25 − 17) ÷ 18 × 20 ≈ 48.9. Reading from the curve gives about 49."], "**Median ≈ 49 marks.**",
          plot(0, 100, 0, 52, points=[(x, y, None, "red") for x, y in pts], segments=segs, grid=10, xlabel="mark", ylabel="cf")),
         ("Example 2: quartiles and a count", "Using the same data, estimate Q1, Q3, the interquartile range and the number of students who scored more than 70.",
          ["Q1 at the 12.5th value (class 20–40): 20 + (12.5 − 5) ÷ 12 × 20 = 32.5. Q3 at the 37.5th value (class 60–80): 60 + (37.5 − 35) ÷ 10 × 20 = 65.", "IQR = 65 − 32.5 = 32.5.", "At 70 the cumulative frequency is 35 + 10 × (70 − 60) ÷ 20 = 40, so 50 − 40 = 10 students scored more than 70."], "**Q1 ≈ 32.5; Q3 ≈ 65; IQR ≈ 32.5; 10 students above 70.**")],
        [N("Three classes have frequencies 6, 9 and 15. Find the last cumulative frequency.", 30, "Cumulative frequencies: 6, 15, 30. The last one is the total frequency.", ),
         M("A cumulative frequency curve is plotted against the:", "upper class boundary", ["lower class boundary", "class midpoint", "frequency"], "Cumulative counts are complete at the upper end."),
         N("For 80 values, at which cumulative frequency is the lower quartile read?", 20, "n/4 = 20.")],
        [N("Q1 = 32 and Q3 = 58. Find the interquartile range.", 26, "58 − 32 = 26."),
         N("From a curve for 120 students, the cumulative frequency at 40 marks is 30. How many scored more than 40?", 90, "120 − 30 = 90.")],
        P("The times taken (minutes) by 40 pupils to walk to school in Buea: up to 10: 6; up to 20: 20; up to 30: 34; up to 40: 40 (cumulative frequencies).",
          [pn("How many took more than 20 minutes?", 20, "40 − 20 = 20 pupils.", pts=1),
           pn("Estimate the median time (the 20th value).", 20, "The cumulative frequency reaches 20 at 20 minutes: median 20.", pts=2),
           pn("How many took between 10 and 30 minutes?", 28, "34 − 6 = 28 pupils.", pts=1)]),
        [("The median is read at:", "n/2", ["n", "n/4", "(n + 1)/4"], "Half the total."),
         ("Q3 is read at:", "3n/4", ["n/4", "n/2", "n"], "Three quarters."),
         ("The last cumulative frequency equals:", "the total frequency", ["the mean", "the range", "zero"], "n."),
         ("IQR measures:", "spread of the middle half", ["the mean", "the mode", "the maximum"], "Q3 − Q1."),
         ("The curve starts at cumulative frequency:", "0", ["1", "the total", "the median"], "Before the first class.")],
        minutes=30)

    # ---- histograms
    cls = [(10, 20, 12), (20, 30, 18), (30, 50, 24), (50, 80, 18)]
    fd = [f / (b - a) for a, b, f in cls]; assert fd == [1.2, 1.8, 1.2, 0.6]
    ox, oy, sx, sy = 40, 190, 5.0, 60
    it = [LINE(ox, oy, ox + 360, oy, arrow="end"), LINE(ox, oy, ox, 20, arrow="end")]
    for (a, b, f), d_ in zip(cls, fd):
        it.append(RECT(ox + (a - 10) * sx, oy - d_ * sy, (b - a) * sx, d_ * sy, fill="lightblue"))
        it.append(T(ox + (a + b - 20) / 2 * sx, oy - d_ * sy - 8, fx(d_, 1), 13))
    for v in (10, 20, 30, 50, 80): it.append(T(ox + (v - 10) * sx, oy + 18, str(v), 13))
    it.append(T(ox + 190, oy + 40, "age (years)", 13)); it.append(T(ox + 6, 14, "frequency density", 13, anchor="start"))
    L = lesson(ch2, "histograms", "Histograms with unequal class widths",
        ["Calculate frequency density.", "Draw and read a histogram with unequal classes.", "Find frequencies from the areas of the bars."],
        [("formule", "Frequency density", "In a histogram the **area** of each bar (not its height) is proportional to the frequency. So the height is the frequency density:", "\\text{frequency density} = \\frac{\\text{frequency}}{\\text{class width}}"),
         ("propriete", "Reading a histogram", "Frequency of a class = frequency density × class width (the area of the bar). Bars touch each other (continuous data) and there are no gaps. The vertical axis is labelled 'frequency density'."),
         ("pieges", "Common mistakes", "- Drawing the frequency as the height when class widths are unequal.\n- Leaving gaps between bars of continuous data.\n- Mixing up class width and class boundaries: width = upper − lower.")],
        [("Example 1: frequency density", "The ages of 72 people are: 10–20 (12), 20–30 (18), 30–50 (24), 50–80 (18). Find the frequency density of each class.",
          ["Class widths: 10, 10, 20, 30.", "Densities: 12 ÷ 10 = 1.2; 18 ÷ 10 = 1.8; 24 ÷ 20 = 1.2; 18 ÷ 30 = 0.6.", "These are the heights of the bars."], "**1.2, 1.8, 1.2, 0.6.**", shapes(it, 420, 235)),
         ("Example 2: from the histogram", "In a histogram the bar for 30–50 has height 1.2 (frequency density). How many people are in that class, and what fraction of the 72 people is that?",
          ["Frequency = density × width = 1.2 × 20 = 24 people.", "Fraction = 24 ÷ 72 = 1/3."], "**24 people, one third.**")],
        [N("A class 5–15 has frequency 30. Find the frequency density.", 3, "30 ÷ 10 = 3."),
         N("A bar of height 2.5 stands on a class of width 8. Find the frequency.", 20, "2.5 × 8 = 20."),
         M("In a histogram with unequal classes, the frequency is proportional to:", "the area of the bar", ["the height of the bar", "the width of the bar", "the class midpoint"], "Area = frequency (up to a scale).")],
        [N("Frequencies: 0–10 (15), 10–20 (28), 20–40 (24), 40–60 (13). Find the frequency density of the class 20–40.", 1.2, "24 ÷ 20 = 1.2."),
         N("A histogram has bars: 0–2 height 6, 2–5 height 4, 5–10 height 1.6. Find the total frequency.", 6 * 2 + 4 * 3 + 1.6 * 5, "12 + 12 + 8 = 32.")],
        P("The masses (kg) of 60 sacks of cassava at a Bafoussam depot are grouped: 20–30 (9), 30–40 (15), 40–60 (24), 60–100 (12).",
          [pn("Frequency density of 40–60.", 1.2, "24 ÷ 20 = 1.2.", tol=0.001, pts=1),
           pn("Frequency density of 60–100.", 0.3, "12 ÷ 40 = 0.3.", tol=0.001, pts=1),
           pn("Which class has the highest frequency density? Enter its lower boundary.", 30, "Densities 0.9, 1.5, 1.2, 0.3: the highest is 30–40.", pts=2)]),
        [("Frequency density =", "frequency ÷ class width", ["frequency × width", "width ÷ frequency", "frequency + width"], "Definition."),
         ("In a histogram the bars:", "touch each other", ["have gaps", "are circles", "are all equal"], "Continuous data."),
         ("Class 10–25 has width:", "15", ["10", "25", "35"], "25 − 10."),
         ("Frequency = density ×:", "class width", ["class midpoint", "range", "mean"], "Area."),
         ("The vertical axis of the histogram shows:", "frequency density", ["frequency", "cumulative frequency", "class width"], "With unequal classes.")],
        minutes=30)
