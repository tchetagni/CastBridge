"""Generator: lower6-physics (Physics — Lower Sixth, GCE Advanced Level syllabus, Cameroon GCE Board). python3 lower6-physics.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _pc import *

g = 9.8  # m s^-2 used throughout this pack
p = Pack("lower6-physics", "Physics — Lower Sixth", level="Lower Sixth", subject="physics", cursus="secondary",
         description="Lower Sixth physics for the GCE Advanced Level course: measurement and uncertainties, mechanics, materials and fluids, "
                     "thermal physics, oscillations and waves, and DC electricity, with worked examples, graded exercises and diagrams. Draft.",
         programRef="Cameroon GCE Board — Advanced Level Physics syllabus (Lower Sixth part) — to be checked against the official texts",
         source_note="Original exercise, GCE Advanced Level style (g = 9.8 m/s²)")
SYL = "GCE Advanced Level Physics — %s (to be checked against the official syllabus)"

# =============================================================== 1. MEASUREMENT
ch = p.chapter("ch-measure", "Measurement and uncertainties", SYL % "measurement")

# ---- 1.1 units
def prefix_fig():
    names = [("giga", "G", "10⁹"), ("mega", "M", "10⁶"), ("kilo", "k", "10³"), ("milli", "m", "10⁻³"), ("micro", "μ", "10⁻⁶"), ("nano", "n", "10⁻⁹")]
    it = []
    for i, (n, s, v) in enumerate(names):
        x = 14 + i * 64
        it += [RECT(x, 40, 58, 70, fill="lightblue" if i < 3 else "lightorange", stroke="ink", width=2), T(x + 29, 62, n, 13, bold=True),
               T(x + 29, 84, "(" + s + ")", 13), T(x + 29, 104, v, 13)]
    it += [T(200, 24, "SI prefixes: multiply the unit by the power of ten", 13), T(200, 140, "1 km = 10³ m      1 mm = 10⁻³ m      1 μs = 10⁻⁶ s", 13)]
    return shapes(it, 400, 160)

v1 = 72 / 3.6; assert v1 == 20
rho = 7800 / 1000; assert rho == 7.8
a_cm2 = 250 * 1e-4; assert abs(a_cm2 - 0.025) < 1e-12
lesson(ch, "units", "SI units, prefixes and homogeneity",
  ["State the seven SI base units and give the units of common derived quantities in base units",
   "Use prefixes and convert units correctly, including squared and cubed units",
   "Test whether an equation is homogeneous (consistent in its units)"],
  [("definition", "Base and derived units",
    "The SI system has seven **base units**: metre (m), kilogram (kg), second (s), ampere (A), kelvin (K), mole (mol) and candela (cd). Every other unit is **derived** from them.\n\n"
    "Examples: newton N = kg m s⁻²; joule J = N m = kg m² s⁻²; watt W = J s⁻¹ = kg m² s⁻³; pascal Pa = N m⁻² = kg m⁻¹ s⁻²; coulomb C = A s."),
   ("retenir", "Prefixes and conversions",
    "Giga G = 10⁹, mega M = 10⁶, kilo k = 10³, milli m = 10⁻³, micro μ = 10⁻⁶, nano n = 10⁻⁹. To change units, multiply by the conversion factor written as a fraction equal to 1.\n\n"
    "Squared units: 1 cm = 10⁻² m, so 1 cm² = (10⁻²)² m² = 10⁻⁴ m², and 1 cm³ = 10⁻⁶ m³."),
   ("methode", "Homogeneity test",
    "An equation can only be correct if every term has the same units (the same dimensions). To test it: write the units of each term in base units and compare.\n\n"
    "Pure numbers (2, π, ½) and angles have no units. Homogeneity is **necessary but not sufficient**: a homogeneous equation can still be wrong by a numerical factor."),
   ("pieges", "Common mistakes",
    "- Converting 250 cm² to m² by dividing by 100 instead of 10 000.\n- Writing the kilogram as a derived unit: it is a base unit (the gram is not).\n- Forgetting to convert km/h to m/s: divide by 3.6.\n- Adding quantities of different units (e.g. a speed plus an acceleration).")],
  [("Example 1 — Units of pressure", "Show that the pascal (1 Pa = 1 N m⁻²) can be written kg m⁻¹ s⁻².",
    ["Force: F = ma, so 1 N = 1 kg × 1 m s⁻² = 1 kg m s⁻².", "Pressure = force ÷ area, so 1 Pa = (kg m s⁻²) ÷ m² .", "Subtract the powers of m: m¹ ÷ m² = m⁻¹."],
    "1 Pa = 1 kg m⁻¹ s⁻²"),
   ("Example 2 — Is it homogeneous?", "A student writes v² = u² + 2as and T = 2π√(l/g) for a pendulum. Check both by units (v, u: m s⁻¹; a, g: m s⁻²; s, l: m; T: s).",
    ["v² has units m² s⁻² and u² the same. 2as has m s⁻² × m = m² s⁻²: all three terms agree, so v² = u² + 2as is homogeneous.",
     "For T: l/g has units m ÷ (m s⁻²) = s². The square root gives s, and 2π has no units.", "So both sides of T = 2π√(l/g) are in seconds: the formula is homogeneous."],
    "Both equations are homogeneous (which does not by itself prove they are correct)")],
  [M("Which of these is a base SI unit?", "kilogram", ["newton", "joule", "pascal"], "The kilogram is one of the seven base units; N, J and Pa are derived."),
   N("A car travels at 72 km/h. Express this speed in m s⁻¹.", v1, "72 km/h = 72 000 m ÷ 3600 s = 20 m s⁻¹ (divide by 3.6).", unit="m/s"),
   TF("True or false: 1 cm² equals 10⁻² m².", False, "1 cm² = (10⁻² m)² = 10⁻⁴ m². The factor must be squared.")],
  [N("The density of iron is 7800 kg m⁻³. Express it in g cm⁻³.", rho, "7800 kg = 7.8 × 10⁶ g and 1 m³ = 10⁶ cm³, so density = 7.8 g cm⁻³.", unit="g/cm³", difficulty=2),
   M("Which expression gives the SI base units of energy (the joule)?", "kg m² s⁻²", ["kg m s⁻²", "kg m² s⁻³", "kg m⁻¹ s⁻²"], "E = ½mv²: kg × (m s⁻¹)² = kg m² s⁻². The others are the newton, the watt and the pascal.")],
  P("The drag force on a sphere moving slowly through a fluid is proposed to be F = 6πηrv, where η is the viscosity, r the radius and v the speed.",
    [pm("What are the base units of force F?", "kg m s⁻²", ["kg m² s⁻²", "kg m s⁻¹", "kg m⁻¹ s⁻²"], "F = ma gives kg m s⁻².", 1),
     pn("η has units kg m⁻¹ s⁻¹. How many metres (power of m) appear in the base units of 6πηrv? Give the net power of m.", 1, "η: m⁻¹; r: m¹; v: m¹ → net m⁻¹⁺¹⁺¹ = m¹ (and kg¹ s⁻¹⁻¹ = s⁻²), i.e. kg m s⁻².", 0, None, 2),
     pt("The equation F = 6πηrv is therefore homogeneous.", True, "Its units are kg m s⁻², the same as a force.", 1)]),
  [M("The unit kW h (kilowatt-hour) measures", "energy", ["power", "force", "charge"], "Power × time = energy: 1 kW h = 3.6 × 10⁶ J."),
   M("A volume of 2.5 × 10⁵ cm³ is equal to", "0.25 m³", ["25 m³", "2.5 m³", "0.025 m³"], "1 cm³ = 10⁻⁶ m³, so 2.5 × 10⁵ cm³ = 0.25 m³."),
   M("Which equation is NOT homogeneous? (s = distance, t = time, v = speed, a = acceleration)", "s = v + at", ["s = vt", "v = at", "s = ½at²"], "v and at are both speeds, so v + at is a speed; s is a length, so the two sides have different units."),
   M("A homogeneous equation is", "one whose terms all have the same units", ["always correct", "one with no numbers in it", "one with only base units"], "Homogeneity checks units only; numerical factors such as ½ are not tested."),
   M("1 μs is", "10⁻⁶ s", ["10⁻³ s", "10⁶ s", "10⁻⁹ s"], "Micro = 10⁻⁶.")],
  (prefix_fig(), "SI prefixes used most often in physics.", "Six boxes showing the prefixes giga, mega, kilo, milli, micro and nano with their powers of ten."),
  notes=["Whether the syllabus asks for the candela is unclear; it is listed here only as part of the SI list."], minutes=20)

# ---- 1.2 uncertainties
L_, dL, W_, dW = 20.0, 0.1, 5.0, 0.1
pL, pW = dL / L_ * 100, dW / W_ * 100
area = L_ * W_; dA = area * (pL + pW) / 100
assert abs(pL + pW - 2.5) < 1e-9 and abs(dA - 2.5) < 1e-9
l_, dl_, T_, dT_ = 1.000, 0.002, 2.01, 0.02
g_ = 4 * math.pi ** 2 * l_ / T_ ** 2
pg = dl_ / l_ * 100 + 2 * dT_ / T_ * 100; dg = g_ * pg / 100
assert abs(g_ - 9.77) < 0.01 and abs(dg - 0.21) < 0.01, (g_, dg)
rd = [12.4, 12.6, 12.5, 12.7, 12.3]
mean = sum(rd) / len(rd); half = (max(rd) - min(rd)) / 2
assert abs(mean - 12.5) < 1e-9 and abs(half - 0.2) < 1e-9
r_ = 2.0; dr_ = 0.1; pV = 3 * dr_ / r_ * 100; assert abs(pV - 15) < 1e-9; pV = 15
V_ = 4 / 3 * math.pi * r_ ** 3
def unc_fig():
    pts = [(1, 0.9, None, "blue"), (2, 2.2, None, "blue"), (3, 2.9, None, "blue"), (4, 4.2, None, "blue"), (5, 4.9, None, "blue")]
    return plot(0, 6, 0, 6, points=pts, segments=[(0, 0, 6, 6, "red", False, "best fit"), (0, 0.4, 6, 5.4, "grey", True, None), (0, -0.4, 6, 6.6, "grey", True, None)],
                xlabel="load / N", ylabel="extension / cm", w=380, h=240)
lesson(ch, "uncertainties", "Uncertainties and significant figures",
  ["Distinguish accuracy, precision, random and systematic errors",
   "Express absolute, fractional and percentage uncertainties and combine them for sums, products and powers",
   "Quote a result with a sensible number of significant figures and an uncertainty"],
  [("definition", "Types of error and uncertainty",
    "**Random errors** scatter readings above and below the true value; repeating and averaging reduces them. **Systematic errors** shift every reading the same way (zero error, wrong calibration) and averaging does not remove them.\n\n"
    "The **absolute uncertainty** Δx has the units of x. The **fractional uncertainty** is Δx/x and the **percentage uncertainty** is Δx/x × 100 %. **Precision** describes the scatter of readings; **accuracy** describes closeness to the true value."),
   ("formule", "Combining uncertainties",
    "- Adding or subtracting: **add the absolute uncertainties**.\n- Multiplying or dividing: **add the percentage uncertainties**.\n- Raising to a power n: **multiply** the percentage uncertainty by n (for the square root, by ½).\n- A mean of repeated readings: uncertainty ≈ half the range (a simple method).\n\nThese give a maximum uncertainty; a rough but safe estimate at this level.",
    r"\frac{\Delta (xy)}{xy}=\frac{\Delta x}{x}+\frac{\Delta y}{y}\qquad \frac{\Delta (x^n)}{x^n}=n\,\frac{\Delta x}{x}"),
   ("retenir", "Reporting results",
    "Give the uncertainty to **1 significant figure** (2 at most) and round the value to the same decimal place. Example: g = 9.77 ± 0.21 m s⁻² is written g = 9.8 ± 0.2 m s⁻².\n\n"
    "A final answer should not have more significant figures than the least precise data used in the calculation. Carry extra digits in the middle of a calculation and round only at the end."),
   ("pieges", "Common mistakes",
    "- Subtracting uncertainties when subtracting quantities: they always **add**.\n- Forgetting to multiply by the power: for T² the percentage uncertainty is doubled.\n- Quoting a result as 9.7692 ± 0.2: the value must be rounded to match the uncertainty.\n- Confusing a systematic error (zero error) with a random one.")],
  [("Example 1 — Area of a plate", "A plate measures 20.0 ± 0.1 cm by 5.0 ± 0.1 cm. Find its area with the uncertainty.",
    ["Percentage uncertainties: 0.1/20.0 = 0.5 % and 0.1/5.0 = 2.0 %.", "Area = 20.0 × 5.0 = 100 cm². Multiplying, so add the percentages: 0.5 + 2.0 = 2.5 %.", "Absolute uncertainty = 2.5 % of 100 = 2.5 cm², written to 1 s.f. as 3 cm² (or keep 2.5 and quote 100 ± 3)."],
    "A = (100 ± 3) cm² (2.5 %)"),
   ("Example 2 — g from a pendulum", "A pendulum has length l = 1.000 ± 0.002 m and period T = 2.01 ± 0.02 s. Use g = 4π²l/T² to find g and its uncertainty.",
    ["g = 4π² × 1.000 ÷ 2.01² = 9.77 m s⁻².", "Percentage uncertainties: l: 0.2 %. T: 0.02/2.01 = 1.0 %, and T is squared so it counts twice: 2.0 %.", "Total = 0.2 + 2.0 = 2.2 %, so Δg = 2.2 % of 9.77 ≈ 0.21 m s⁻², quoted as 0.2."],
    "g = (9.8 ± 0.2) m s⁻²", None)],
  [M("Which type of error is a ruler worn so that it starts at 1 mm instead of 0?", "a systematic error", ["a random error", "a parallax-free error", "a precision error"], "Every reading is shifted by the same amount, which is a systematic (zero) error."),
   N("A length is measured as 25.0 ± 0.5 cm. What is the percentage uncertainty?", 2.0, "0.5/25.0 × 100 = 2.0 %.", unit="%"),
   N("Two lengths 12.0 ± 0.2 cm and 8.0 ± 0.1 cm are added. What is the absolute uncertainty in the total (in cm)?", 0.3, "When adding, add the absolute uncertainties: 0.2 + 0.1 = 0.3 cm.", 0.001, "cm")],
  [N(f"The radius of a sphere is {r_} ± {dr_} cm. Its volume is proportional to r³. What is the percentage uncertainty in the volume?", pV, "r³ → multiply the percentage uncertainty by 3: (0.1/2.0 = 5 %) × 3 = 15 %.", unit="%", difficulty=2),
   N("Five readings of a length (cm): 12.4, 12.6, 12.5, 12.7, 12.3. Find half the range, as an estimate of the uncertainty of the mean (in cm).", half, "Range = 12.7 − 12.3 = 0.4 cm, half the range = 0.2 cm (mean 12.5 cm).", 0.001, "cm")],
  P("In an experiment to find the density of a cylinder: mass m = 150.0 ± 0.5 g, diameter d = 2.00 ± 0.02 cm, length h = 6.00 ± 0.05 cm. Density ρ = 4m/(πd²h).",
    [pn("Calculate ρ in g cm⁻³ (3 s.f.).", sg(4 * 150.0 / (math.pi * 2.0 ** 2 * 6.0), 3), "ρ = 4 × 150.0 ÷ (π × 2.00² × 6.00) = 7.96 g cm⁻³.", 0.01, "g/cm³", 1),
     pn("Calculate the percentage uncertainty in ρ (add m once, d twice, h once).", sg(0.5 / 150 * 100 + 2 * 0.02 / 2.0 * 100 + 0.05 / 6 * 100, 3), "m: 0.33 %; d: 1.0 % × 2 = 2.0 %; h: 0.83 %. Total = 3.17 % ≈ 3.2 %.", 0.1, "%", 2),
     pn("Calculate the absolute uncertainty in ρ (g cm⁻³, 2 s.f.).", sg(4 * 150.0 / (math.pi * 2.0 ** 2 * 6.0) * 0.0317, 2), "3.17 % of 7.96 = 0.25 g cm⁻³, so ρ = 8.0 ± 0.3 g cm⁻³ (to match the uncertainty).", 0.02, "g/cm³", 2)]),
  [M("A percentage uncertainty of 4 % in a speed v. What is the percentage uncertainty in v²?", "8 %", ["4 %", "2 %", "16 %"], "A power n multiplies the percentage uncertainty by n: 2 × 4 % = 8 %."),
   M("The best way to reduce a RANDOM error is to", "repeat the readings and take the mean", ["use a different zero", "add the uncertainties", "round the answer more"], "Random scatter averages out; systematic errors do not."),
   M("Which result is correctly quoted?", "(4.5 ± 0.2) m", ["(4.512 ± 0.2) m", "(4.5 ± 0.213) m", "(4 ± 0.2) m"], "The uncertainty has 1 s.f. and the value is rounded to the same decimal place."),
   M("Lengths a = 10.0 ± 0.1 cm and b = 4.0 ± 0.1 cm are subtracted. The uncertainty in a − b is", "0.2 cm", ["0.0 cm", "0.1 cm", "0.4 cm"], "Uncertainties always add, even in a subtraction."),
   M("Precision of a set of readings refers to", "how close the readings are to each other", ["how close the mean is to the true value", "the size of the zero error", "the number of readings"], "Precision = scatter (repeatability); accuracy = closeness to the true value.")],
  (unc_fig(), "Extension against load: the best-fit line (red) and the steepest and shallowest lines (dashed) give the uncertainty in the gradient.", "A straight-line graph with five points, the best-fit line and two dashed lines above and below used to estimate the uncertainty in the gradient."),
  notes=["Rule 'half the range' and 'add percentages' are the simple maximum-uncertainty methods; check whether the syllabus asks for statistical (standard deviation) treatment.",
         "Number of significant figures conventions in the GCE Board mark schemes should be checked."], minutes=25, prereq=["lower6-physics-units"])

# =============================================================== 2. MECHANICS
ch = p.chapter("ch-mechanics", "Kinematics, dynamics, energy and momentum", SYL % "mechanics")
rad = math.radians

# ---- 2.1 vectors
F50x, F50y = 50 * math.cos(rad(30)), 50 * math.sin(rad(30))
assert abs(F50x - 43.30) < 0.01 and abs(F50y - 25.0) < 1e-9
R_ = math.hypot(30, 40); ang = math.degrees(math.atan2(40, 30)); assert R_ == 50 and abs(ang - 53.13) < 0.01
R2 = math.sqrt(2 * 10 ** 2 + 2 * 10 * 10 * math.cos(rad(120))); assert abs(R2 - 10) < 1e-9
Rb = math.hypot(4, 3); assert Rb == 5; tb = 200 / 4; drift = 3 * tb; assert tb == 50 and drift == 150
def vec_fig():
    it = [LINE(40, 200, 330, 200, arrow="end"), LINE(40, 200, 40, 30, arrow="end"), T(335, 218, "x", 13), T(28, 34, "y", 13),
          LINE(40, 200, 240, 85, color="red", width=3, arrow="end"), LINE(240, 200, 240, 85, dash=True, color="blue", width=2), LINE(40, 85, 240, 85, dash=True, color="blue", width=2),
          ANGLE(40, 200, 0, 30, r=36, label="θ"), T(135, 125, "F", 15, color="red", bold=True), T(140, 222, "F cos θ", 13, color="blue"), T(300, 150, "F sin θ", 13, color="blue")]
    return shapes(it, 380, 240)
lesson(ch, "vectors", "Scalars, vectors and resolving forces",
  ["Distinguish scalar and vector quantities and add vectors by the triangle or parallelogram rule",
   "Resolve a vector into perpendicular components and recombine components into a resultant",
   "Solve simple problems on resultant forces and relative velocity"],
  [("definition", "Scalars and vectors",
    "A **scalar** has magnitude only (mass, time, distance, speed, energy, temperature). A **vector** has magnitude and direction (displacement, velocity, acceleration, force, momentum).\n\n"
    "Vectors add by the **triangle rule** (head to tail) or the **parallelogram rule**. The result is the **resultant**. Two vectors at 90° give a resultant of size √(a² + b²)."),
   ("formule", "Resolving a vector",
    "A vector F at angle θ to the x-axis has components **F_x = F cos θ** along x and **F_y = F sin θ** along y. Conversely F = √(F_x² + F_y²) and tan θ = F_y/F_x.\n\n"
    "To add several vectors: resolve each into x and y components, add the components in each direction separately, then recombine.",
    r"F_x=F\cos\theta\qquad F_y=F\sin\theta\qquad F=\sqrt{F_x^{2}+F_y^{2}}"),
   ("methode", "Method for a resultant",
    "1. Draw a clear sketch and choose axes (often along and perpendicular to the motion or slope).\n2. Resolve each vector into components.\n3. Add the x components and the y components separately (take care with signs).\n4. Find the magnitude with Pythagoras and the direction with tan θ."),
   ("pieges", "Common mistakes",
    "- Using cos for the component opposite the angle: the component **adjacent** to the angle uses cos, the opposite one uses sin.\n- Adding magnitudes instead of vectors (3 N + 4 N is not always 7 N).\n- A calculator in radian mode when the angle is in degrees.\n- Forgetting to give a direction with the answer.")],
  [("Example 1 — Resolving a force", "A 50 N force acts at 30° above the horizontal. Find its horizontal and vertical components.",
    ["Horizontal: F cos 30° = 50 × 0.866 = 43.3 N.", "Vertical: F sin 30° = 50 × 0.500 = 25.0 N.", "Check: √(43.3² + 25.0²) = 50.0 N."],
    "43.3 N horizontally and 25.0 N vertically"),
   ("Example 2 — Resultant of two forces", "Two ropes pull a stuck vehicle: 30 N due east and 40 N due north. Find the resultant.",
    ["The forces are at right angles: R = √(30² + 40²) = √2500 = 50 N.", "Direction: tan θ = 40/30, so θ = 53.1° north of east."],
    "50 N at 53° north of east")],
  [M("Which of these is a vector?", "displacement", ["speed", "energy", "mass"], "Displacement has direction. Speed, energy and mass are scalars."),
   N("A force of 80 N acts at 60° above the horizontal. What is its horizontal component?", 40.0, "F cos 60° = 80 × 0.5 = 40 N.", 0.1, "N"),
   TF("True or false: the resultant of two forces always equals the sum of their magnitudes.", False, "It equals the sum only when the forces act in the same direction; otherwise it is smaller.")],
  [N("A boat's velocity is 6 m/s east and a current adds 8 m/s north. Find the magnitude of the resultant velocity.", 10.0, "√(6² + 8²) = 10 m/s.", 0.01, "m/s", difficulty=2),
   M("Two forces of 10 N act on a point with an angle of 120° between them. The magnitude of the resultant is", "10 N", ["20 N", "17 N", "0 N"], "R² = 10² + 10² + 2(10)(10)cos 120° = 200 − 100 = 100, so R = 10 N (the triangle is equilateral).")],
  P("A boatman at the bank of River Sanaga steers his boat at 4 m/s (relative to the water) straight across the river. The current is 3 m/s downstream and the river is 200 m wide.",
    [pn("Find the speed of the boat relative to the bank (m/s).", Rb, "√(4² + 3²) = 5 m/s.", 0.01, "m/s", 1),
     pn("How long does the crossing take (s)?", tb, "The cross-river motion is independent: t = 200 ÷ 4 = 50 s.", 0.01, "s", 1),
     pn("How far downstream is the boat carried (m)?", drift, "Drift = 3 m/s × 50 s = 150 m.", 0.01, "m", 1)]),
  [M("Which pair can NEVER give a resultant of 5 N?", "3 N and 1 N", ["3 N and 4 N", "4 N and 4 N", "3 N and 2 N"], "The resultant of 3 N and 1 N lies between 2 N and 4 N; 3 N and 2 N can give 5 N when parallel."),
   M("A 20 N force acts at 30° to the x-axis. Its y-component is", "10 N", ["17.3 N", "20 N", "34.6 N"], "20 sin 30° = 10 N."),
   M("Vectors are added 'head to tail' in the", "triangle rule", ["scalar rule", "Hooke rule", "inverse-square rule"], "Placing the tail of the second at the head of the first gives the resultant from the first tail to the second head."),
   M("A 5 N force along +x and a 12 N force along +y have a resultant of", "13 N", ["17 N", "7 N", "60 N"], "√(25 + 144) = 13 N."),
   M("Which is a scalar quantity?", "kinetic energy", ["force", "velocity", "momentum"], "Energy has no direction.")],
  (vec_fig(), "Resolving a force F into components along the axes.", "A force vector at angle theta to the horizontal with dashed horizontal and vertical components labelled F cos theta and F sin theta."),
  notes=["Vector product/scalar product are not included; check whether the syllabus asks for them at this stage."], minutes=25)

# ---- 2.2 kinematics
a1 = (20 - 0) / 8; s1 = 0.5 * (0 + 20) * 8; assert a1 == 2.5 and s1 == 80
u2 = 19.6; H2 = u2 ** 2 / (2 * g); t_up = u2 / g; assert abs(H2 - 19.6) < 1e-9 and abs(t_up - 2.0) < 1e-9
area_vt = 0.5 * 4 * 12 + 6 * 12 + 0.5 * 4 * 12; assert area_vt == 120
dec = (0 - 12) / 4; assert dec == -3
def vt_fig():
    return plot(0, 14, 0, 14, segments=[(0, 0, 4, 12, "blue", False, None), (4, 12, 10, 12, "blue", False, None), (10, 12, 14, 0, "blue", False, None)],
                xlabel="t / s", ylabel="v / m s⁻¹", grid=2, w=380, h=240)
lesson(ch, "kinematics", "Motion in a straight line",
  ["Define displacement, velocity and acceleration and use the equations of uniformly accelerated motion",
   "Interpret displacement–time and velocity–time graphs (gradient and area)",
   "Analyse free fall under gravity (g = 9.8 m s⁻²)"],
  [("definition", "Quantities and graphs",
    "**Velocity** = rate of change of displacement; **acceleration** = rate of change of velocity (a vector, unit m s⁻²).\n\n"
    "On a displacement–time graph the **gradient** is velocity. On a velocity–time graph the **gradient** is acceleration and the **area** under the graph is displacement."),
   ("formule", "Equations for constant acceleration (suvat)",
    "With u = initial velocity, v = final velocity, a = acceleration, t = time, s = displacement:\n\n- v = u + at\n- s = ½(u + v)t\n- s = ut + ½at²\n- v² = u² + 2as\n\nThey apply **only when a is constant**. Choose a positive direction and keep signs consistent.",
    r"v=u+at\qquad s=ut+\frac{1}{2}at^{2}\qquad v^{2}=u^{2}+2as"),
   ("retenir", "Free fall",
    "Near the Earth's surface, with air resistance neglected, all objects fall with the same acceleration g = 9.8 m s⁻² downward. For an object thrown up, take up as positive so a = −9.8 m s⁻².\n\n"
    "At the highest point v = 0 (but a is still −9.8 m s⁻²). Time up = time down, if it returns to its starting height."),
   ("pieges", "Common mistakes",
    "- Using the suvat equations when the acceleration is not constant (use graphs instead).\n- Mixing signs: a ball thrown up and then falling has a negative acceleration throughout.\n- Confusing distance and displacement, or speed and velocity.\n- Reading the area under a v–t graph below the axis as positive: it is negative displacement.")],
  [("Example 1 — Accelerating car", "A taxi leaves a stop in Douala and reaches 20 m/s in 8.0 s with constant acceleration. Find the acceleration and the distance covered.",
    ["a = (v − u)/t = (20 − 0)/8.0 = 2.5 m s⁻².", "s = ½(u + v)t = ½ × (0 + 20) × 8.0 = 80 m.", "Check with v² = u² + 2as: 400 = 2 × 2.5 × 80 ✓."],
    "a = 2.5 m s⁻², s = 80 m"),
   ("Example 2 — A stone thrown upward", "A stone is thrown vertically upward at 19.6 m/s. Find the maximum height and the time to reach it (g = 9.8 m s⁻²).",
    ["Take up as positive: u = 19.6, a = −9.8, and v = 0 at the top.", "v² = u² + 2as gives 0 = 19.6² − 2 × 9.8 × s, so s = 384.16 ÷ 19.6 = 19.6 m.", "v = u + at gives 0 = 19.6 − 9.8t, so t = 2.0 s."],
    "H = 19.6 m, time to top = 2.0 s")],
  [N("A car decelerates uniformly from 30 m/s to rest in 6.0 s. What is the magnitude of its acceleration?", 5.0, "a = (0 − 30)/6.0 = −5.0 m s⁻²; magnitude 5.0 m s⁻².", 0.01, "m/s²"),
   N("An object falls from rest for 3.0 s (g = 9.8 m s⁻², no air resistance). How far does it fall?", sg(0.5 * g * 9, 3), "s = ½gt² = 0.5 × 9.8 × 9.0 = 44.1 m.", 0.1, "m"),
   M("On a velocity–time graph, the area under the line represents", "displacement", ["acceleration", "force", "speed"], "The area is v × t summed, which is displacement.")],
  [N("A bus slows from 25 m/s to 10 m/s over a distance of 175 m with constant deceleration. Find the magnitude of the deceleration.", 1.5, "a = (v² − u²)/2s = (100 − 625)/350 = −1.5 m s⁻²; magnitude 1.5.", 0.01, "m/s²", difficulty=2),
   M("A ball is thrown up and returns to the thrower's hand. At the highest point its velocity and acceleration are", "v = 0 and a = 9.8 m/s² downward", ["v = 0 and a = 0", "v = 9.8 m/s and a = 0", "v = 0 and a = 9.8 m/s² upward"], "Gravity acts all the time; only the velocity is momentarily zero.")],
  P("Figure: the velocity–time graph of a minibus between two bus stops: it accelerates uniformly for 4 s to 12 m/s, moves at constant speed for 6 s, then decelerates uniformly to rest in 4 s.",
    [pn("Find the acceleration during the first 4 s (m s⁻²).", 3.0, "Gradient = 12/4 = 3.0 m s⁻².", 0.01, "m/s²", 1),
     pn("Find the total distance between the stops (m).", area_vt, "Area = ½·4·12 + 6·12 + ½·4·12 = 24 + 72 + 24 = 120 m.", 0.5, "m", 2),
     pn("Find the average speed for the whole 14 s (m/s, 2 s.f.).", sg(120 / 14, 2), "Average speed = 120 ÷ 14 = 8.6 m/s.", 0.05, "m/s", 1)],
    figure=vt_fig()),
  [M("A displacement–time graph is a straight line sloping upward. The object has", "constant positive velocity", ["constant acceleration", "zero velocity", "increasing velocity"], "A constant gradient means a constant velocity."),
   M("A car starts from rest with a = 2.0 m s⁻². After 5.0 s its speed is", "10 m/s", ["2.5 m/s", "5.0 m/s", "25 m/s"], "v = at = 2.0 × 5.0 = 10 m/s."),
   M("An object is dropped from rest. Distance fallen in the first 2.0 s (g = 9.8) is", "19.6 m", ["9.8 m", "39.2 m", "4.9 m"], "s = ½ × 9.8 × 4 = 19.6 m."),
   M("The equation s = ut + ½at² applies when", "the acceleration is constant", ["the velocity is constant only", "the object is falling only", "there is no friction only"], "The suvat equations require uniform acceleration."),
   M("The gradient of a velocity–time graph gives", "acceleration", ["displacement", "distance", "force"], "Acceleration = change in velocity ÷ time.")],
  (vt_fig(), "Velocity–time graph of a minibus: the gradient gives acceleration and the area gives distance.", "A trapezium-shaped velocity time graph rising to 12 metres per second at 4 seconds, flat until 10 seconds, then falling to zero at 14 seconds."),
  notes=["g = 9.8 m s⁻² is used; some Cameroonian schools use 10 m s⁻² — check the board's preference."], minutes=30, prereq=["lower6-physics-vectors"])

# ---- 2.3 projectiles
th = rad(30); u3 = 20.0
T3 = 2 * u3 * math.sin(th) / g; R3 = u3 ** 2 * math.sin(2 * th) / g; H3 = (u3 * math.sin(th)) ** 2 / (2 * g)
assert abs(T3 - 2.04) < 0.01 and abs(R3 - 35.3) < 0.1 and abs(H3 - 5.10) < 0.01
h4, v4 = 45.0, 15.0; t4 = math.sqrt(2 * h4 / g); x4 = v4 * t4; vy4 = g * t4; sp4 = math.hypot(v4, vy4)
assert abs(t4 - 3.03) < 0.01 and abs(x4 - 45.4) < 0.1 and abs(sp4 - 33.2) < 0.1, (t4, x4, sp4)
k1 = math.tan(th); k2 = g / (2 * u3 ** 2 * math.cos(th) ** 2)
def proj_fig():
    return plot(0, 36, 0, 6, curves=[("%.4f*x - %.5f*x^2" % (k1, k2), "blue", None)],
                points=[(R3 / 2, H3, "top", "red")], grid=6, xlabel="x / m", ylabel="y / m", w=400, h=240)
lesson(ch, "projectiles", "Projectile motion",
  ["Treat horizontal and vertical motions independently for a projectile",
   "Calculate time of flight, range and maximum height for a launch at an angle",
   "Solve problems on horizontal launch from a height"],
  [("definition", "Independence of motion",
    "A **projectile** moves under gravity only (air resistance neglected). Its horizontal and vertical motions are **independent**:\n\n- horizontal: constant velocity, a = 0, so x = u_x t\n- vertical: constant acceleration g downward.\n\nThe path is a **parabola**."),
   ("formule", "Launch at speed u, angle θ above the horizontal",
    "Components: u_x = u cos θ, u_y = u sin θ.\n\n- Time of flight (level ground): T = 2u sin θ / g\n- Maximum height: H = u² sin² θ / 2g\n- Range: R = u² sin 2θ / g (maximum at θ = 45°)\n\nThe range formula holds only if landing at the launch height.",
    r"T=\frac{2u\sin\theta}{g}\quad H=\frac{u^{2}\sin^{2}\theta}{2g}\quad R=\frac{u^{2}\sin 2\theta}{g}"),
   ("methode", "Method",
    "1. Resolve the initial velocity into horizontal and vertical components.\n2. Use the vertical motion (suvat with a = −g) to find the time.\n3. Use the horizontal motion x = u_x t with that time.\n4. For the final velocity, combine v_x and v_y with Pythagoras."),
   ("pieges", "Common mistakes",
    "- Using the range formula when the projectile lands at a different height.\n- Applying gravity to the horizontal motion.\n- Taking the velocity at the top as zero: only the vertical component is zero there.\n- Using the total speed in the suvat equations instead of one component.")],
  [("Example 1 — Ball kicked at an angle", "A footballer in Bamenda kicks a ball at 20 m/s at 30° above the ground. Find the time of flight, the range and the maximum height (g = 9.8 m s⁻²).",
    ["u_x = 20 cos 30° = 17.32 m/s; u_y = 20 sin 30° = 10 m/s.", "T = 2u_y/g = 20/9.8 = 2.04 s. Range = u_x T = 17.32 × 2.04 = 35.3 m.", "H = u_y²/2g = 100/19.6 = 5.10 m."],
    "T = 2.04 s, R = 35.3 m, H = 5.10 m"),
   ("Example 2 — Horizontal launch", "A stone is thrown horizontally at 15 m/s from the top of a 45 m cliff. How long does it take to land, how far from the base, and what is its speed on landing?",
    ["Vertical: 45 = ½ × 9.8 × t², so t = √(90/9.8) = 3.03 s.", "Horizontal distance = 15 × 3.03 = 45.4 m.", "v_y = gt = 29.7 m/s; speed = √(15² + 29.7²) = 33.3 m/s."],
    "t = 3.03 s, x = 45.4 m, speed = 33.3 m/s")],
  [N("A ball is thrown horizontally from a table 0.80 m high. How long does it take to hit the floor (g = 9.8)? Give seconds to 2 d.p.", sg(math.sqrt(2 * 0.8 / g), 3), "t = √(2h/g) = √(1.6/9.8) = 0.40 s.", 0.01, "s"),
   N("A projectile is launched at 40 m/s at 30° (level ground). Its time of flight is: (g = 9.8, answer in s to 3 s.f.)", sg(2 * 40 * 0.5 / g, 3), "T = 2u sin θ/g = 2 × 40 × 0.5/9.8 = 4.08 s.", 0.02, "s"),
   M("At which launch angle is the range greatest (same speed, level ground, no air resistance)?", "45°", ["30°", "60°", "90°"], "R ∝ sin 2θ, which is maximum when 2θ = 90°.")],
  [N("The range of a projectile launched at 25 m/s at 45° on level ground (g = 9.8) is: (m, 3 s.f.)", sg(25 ** 2 / g, 3), "R = u² sin 90°/g = 625/9.8 = 63.8 m.", 0.2, "m", difficulty=2),
   M("Two stones are released at the same moment from the same height: one is dropped, the other thrown horizontally. Which statement is correct?", "They hit the ground at the same time", ["The dropped stone lands later", "The thrown stone lands later", "They land together only if the thrown one is slow"], "Vertical motion is independent of horizontal velocity.")],
  P("A tennis ball is hit horizontally at 30 m/s from a height of 2.0 m above level ground (g = 9.8 m/s²; ignore air resistance).",
    [pn("Time to hit the ground (s, 2 d.p.).", sg(math.sqrt(2 * 2.0 / g), 3), "t = √(2 × 2.0/9.8) = 0.639 s.", 0.01, "s", 1),
     pn("Horizontal distance travelled before landing (m, 3 s.f.).", sg(30 * math.sqrt(2 * 2.0 / g), 3), "x = 30 × 0.639 = 19.2 m.", 0.2, "m", 1),
     pn("Vertical component of the velocity just before landing (m/s, 3 s.f.).", sg(g * math.sqrt(2 * 2.0 / g), 3), "v_y = gt = 9.8 × 0.639 = 6.26 m/s.", 0.05, "m/s", 1),
     pm("The ball would land at the same time if hit horizontally at 15 m/s because", "the vertical motion does not depend on the horizontal speed", ["the speed is smaller", "gravity is smaller", "the range is unchanged"], "Time depends only on the fall height; horizontal speed changes only the range.", 1)]),
  [M("At the highest point of a projectile's path (no air resistance), the acceleration is", "9.8 m/s² downward", ["zero", "9.8 m/s² upward", "equal to u cos θ"], "Gravity acts throughout."),
   M("The horizontal velocity of a projectile (no air resistance)", "stays constant", ["increases", "decreases", "is zero at the top"], "No horizontal force acts."),
   M("A ball is thrown horizontally at 10 m/s from a 20 m cliff. Time to land: (g = 9.8)", "2.0 s", ["1.0 s", "4.0 s", "2.9 s"], "t = √(2 × 20/9.8) = 2.02 s ≈ 2.0 s."),
   M("A ball thrown with u_y = 14.7 m/s upward is in the air for (level ground, g = 9.8)", "3.0 s", ["1.5 s", "6.0 s", "2.0 s"], "T = 2u_y/g = 29.4/9.8 = 3.0 s."),
   M("The path of a projectile in a uniform field without air resistance is", "a parabola", ["a circle", "a straight line", "an ellipse of small size only"], "x is linear in t and y is quadratic in t, giving a parabola.")],
  (proj_fig(), "Path of a ball launched at 20 m/s at 30°: height y against horizontal distance x.", "A parabolic curve starting at the origin, rising to about 5 metres at 17 metres and returning to the ground at 35 metres."),
  notes=["Range and height formulas rely on level ground; air resistance is ignored."], minutes=30, prereq=["lower6-physics-kinematics"])

# ---- 2.4 Newton's laws
a_blk = (30 - 10) / 5.0; assert a_blk == 4.0
a_inc = g * (math.sin(rad(30)) - 0.20 * math.cos(rad(30))); assert abs(a_inc - 3.20) < 0.01
R_lift = 60 * (g + 1.5); assert abs(R_lift - 678) < 1e-9
a_pul = (3 - 2) * g / 5; T_pul = 3 * (g - a_pul); assert abs(a_pul - 1.96) < 1e-9 and abs(T_pul - 23.52) < 1e-9
W_norm = 10 * g * math.cos(rad(30)); f_max = 0.20 * W_norm; assert abs(W_norm - 84.87) < 0.01 and abs(f_max - 16.97) < 0.01
def incline_fig():
    c, s = math.cos(rad(30)), math.sin(rad(30))
    base = (50, 210); mid = (base[0] + 0.5 * 260 / c * c, 0)
    t = 150  # distance along the slope
    cx, cy = base[0] + t * c, base[1] - t * s
    nx, ny = -s, -c
    pts = []
    for du, dn in [(-22, 0), (22, 0), (22, 26), (-22, 26)]:
        pts += [cx + du * c + dn * nx, cy - du * s + dn * ny]
    gx, gy = cx + 13 * nx, cy + 13 * ny
    it = [POLY([50, 210, 310, 210, 310, 60], fill="lightgrey", width=2), POLY(pts, fill="lightorange", width=2),
          LINE(gx, gy, gx, gy + 70, color="red", width=3, arrow="end"), T(gx - 8, gy + 84, "weight mg", 13, anchor="end", color="red"),
          LINE(gx, gy, gx + 62 * nx, gy + 62 * ny, color="blue", width=3, arrow="end"), T(gx + 62 * nx - 6, gy + 62 * ny - 4, "N", 14, anchor="end", color="blue"),
          LINE(gx, gy, gx + 55 * c * -1, gy + 55 * s, color="green", width=3, arrow="end"), T(gx - 55 * c - 8, gy + 55 * s + 14, "friction f", 13, anchor="end", color="green"),
          ANGLE(50, 210, 0, 30, r=40, label="θ")]
    return shapes(it, 400, 250)
lesson(ch, "newton", "Newton's laws, friction and free-body diagrams",
  ["State Newton's three laws and use F = ma for a body and for connected bodies",
   "Draw free-body diagrams and resolve forces along and perpendicular to a slope",
   "Use friction f ≤ μN and explain terminal velocity"],
  [("definition", "Newton's laws",
    "1st law: a body stays at rest or moves at constant velocity unless a **resultant force** acts on it.\n\n"
    "2nd law: the resultant force is the rate of change of momentum; for constant mass **F = ma** (F in newtons, a in m s⁻²). 1 N is the force giving 1 kg an acceleration of 1 m s⁻².\n\n"
    "3rd law: if A exerts a force on B, B exerts an equal and opposite force on A. The two forces act on **different bodies** and are of the same kind.",),
   ("methode", "Method for dynamics problems",
    "1. Isolate one body and draw a **free-body diagram**: weight mg, normal reaction N, tension, friction, applied forces.\n2. Choose axes (along and perpendicular to the motion).\n3. Along the motion: resultant force = ma. Perpendicular to it: forces balance.\n4. For connected bodies write an equation for each body, then solve together."),
   ("formule", "Friction and slopes",
    "Friction opposes the relative motion; the maximum (limiting) value is **f = μN** where μ is the coefficient of friction and N the normal reaction.\n\n"
    "On a slope of angle θ: weight component down the slope = mg sin θ; perpendicular to the slope = mg cos θ, so N = mg cos θ.",
    r"F=ma\qquad f_{\max}=\mu N\qquad N=mg\cos\theta"),
   ("retenir", "Terminal velocity",
    "A falling object in air meets a drag force that grows with speed. The resultant force and acceleration fall until the drag equals the weight; then a = 0 and the speed is constant: the **terminal velocity**."),
   ("pieges", "Common mistakes",
    "- Calling weight and the normal reaction an action–reaction pair: they act on the same body. The reaction to the weight is the gravitational pull of the body on the Earth.\n- Writing N = mg on a slope: it is mg cos θ.\n- Forgetting friction acts up the slope when the body slides down.\n- Using a mass in newtons or a weight in kilograms.")],
  [("Example 1 — Block with friction", "A 5.0 kg crate is pulled along a rough floor by a horizontal force of 30 N. Friction is 10 N. Find its acceleration.",
    ["Resultant force along the floor = 30 − 10 = 20 N.", "a = F/m = 20/5.0 = 4.0 m s⁻²."],
    "a = 4.0 m s⁻² in the direction of the pull"),
   ("Example 2 — Sliding down a slope", "A 10 kg box slides down a 30° slope with μ = 0.20. Find its acceleration (g = 9.8 m s⁻²).",
    ["Perpendicular to the slope: N = mg cos 30° = 10 × 9.8 × 0.866 = 84.9 N.", "Friction f = μN = 0.20 × 84.9 = 17.0 N up the slope. Down-slope component of weight = mg sin 30° = 49.0 N.", "Resultant = 49.0 − 17.0 = 32.0 N, so a = 32.0/10 = 3.20 m s⁻²."],
    "a = 3.2 m s⁻² down the slope")],
  [N("A resultant force of 12 N acts on a 3.0 kg trolley. Find the acceleration (m s⁻²).", 4.0, "a = F/m = 12/3.0 = 4.0 m s⁻².", 0.01, "m/s²"),
   N("What is the weight of a 60 kg person (g = 9.8 m s⁻²)?", 588, "W = mg = 60 × 9.8 = 588 N.", 0.5, "N"),
   TF("True or false: when you stand on the floor, your weight and the floor's normal reaction form an action–reaction pair (Newton's third law).", False, "They act on the same body (you). The third-law pair of the weight is the gravitational force you exert on the Earth.")],
  [N("A 60 kg student stands in a lift that accelerates upward at 1.5 m s⁻². What force does the floor exert on the student (N)?", R_lift, "R − mg = ma, so R = m(g + a) = 60 × 11.3 = 678 N.", 1, "N", difficulty=2),
   N("A 10 kg box on a 30° slope (μ = 0.20) is held still by static friction. What is the maximum friction (limiting) available (N, 3 s.f.)?", sg(f_max, 3), "N = mg cos 30° = 84.9 N; f_max = 0.20 × 84.9 = 17.0 N.", 0.1, "N")],
  P("Two blocks are joined by a light string over a smooth pulley: A of mass 3.0 kg hangs on one side and B of mass 2.0 kg on the other (g = 9.8 m s⁻²).",
    [pn("Find the acceleration (m s⁻²).", a_pul, "Resultant driving force = (3.0 − 2.0)g = 9.8 N; total mass = 5.0 kg; a = 1.96 m s⁻².", 0.01, "m/s²", 2),
     pn("Find the tension in the string (N, 3 s.f.).", sg(T_pul, 3), "For A: mg − T = ma, so T = 3.0 × (9.8 − 1.96) = 23.5 N.", 0.1, "N", 2),
     pt("The tension is equal to the weight of block B plus its mass times acceleration.", True, "For B: T − mg = ma, so T = 2.0 × (9.8 + 1.96) = 23.5 N, matching A.", 1)]),
  [M("A skydiver reaches terminal velocity when", "air resistance equals weight", ["air resistance is zero", "weight is zero", "the speed is zero"], "The resultant force is zero, so a = 0 and the speed is constant."),
   M("A 2.0 kg trolley is pulled by 10 N against friction of 4.0 N. Its acceleration is", "3.0 m/s²", ["5.0 m/s²", "7.0 m/s²", "2.0 m/s²"], "(10 − 4)/2.0 = 3.0 m s⁻²."),
   M("Newton's first law says that a body with no resultant force", "keeps moving at constant velocity", ["slows down gradually", "falls", "must be at rest"], "No resultant force means no change in velocity."),
   M("The SI unit newton equals", "kg m s⁻²", ["kg m² s⁻²", "kg s⁻²", "kg m s⁻¹"], "F = ma."),
   M("On a slope of angle θ, the normal reaction on a mass m is", "mg cos θ", ["mg sin θ", "mg", "mg tan θ"], "The weight component perpendicular to the slope is mg cos θ.")],
  (incline_fig(), "Forces on a box on a slope (free-body diagram).", "A box on an inclined plane with arrows for weight vertically down, normal reaction perpendicular to the plane and friction up the slope."),
  notes=["Static versus kinetic friction: the lesson uses a single μ; check whether the syllabus distinguishes them.", "Some boards phrase the second law as F = dp/dt; both forms are given."], minutes=35, prereq=["lower6-physics-kinematics", "lower6-physics-vectors"])

# ---- 2.5 momentum
v_c = 2 * 3 / (2 + 1); KEi = 0.5 * 2 * 9; KEf = 0.5 * 3 * v_c ** 2; assert v_c == 2 and KEi - KEf == 3
J_w = 0.15 * (15 + 20); F_w = J_w / 0.01; assert abs(J_w - 5.25) < 1e-9 and abs(F_w - 525) < 1e-6
v_rec = 0.02 * 400 / 4.0; assert abs(v_rec - 2.0) < 1e-9
def coll_fig():
    it = [T(100, 20, "before", 14, bold=True), RECT(40, 50, 50, 30, fill="lightblue"), T(65, 70, "2 kg", 13), LINE(40, 100, 90, 100, color="red", width=3, arrow="end"), T(65, 120, "3 m/s", 12, color="red"),
          RECT(150, 50, 36, 30, fill="lightorange"), T(168, 70, "1 kg", 13), T(168, 100, "at rest", 12),
          T(300, 20, "after", 14, bold=True), RECT(250, 50, 86, 30, fill="lightgreen"), T(293, 70, "3 kg together", 12), LINE(250, 100, 336, 100, color="red", width=3, arrow="end"), T(293, 120, "2 m/s", 12, color="red")]
    return shapes(it, 400, 140)
lesson(ch, "momentum", "Momentum, impulse and collisions",
  ["Define momentum and impulse and apply the conservation of linear momentum",
   "Distinguish elastic and inelastic collisions using kinetic energy",
   "Use impulse = force × time = change of momentum, including F–t graphs"],
  [("definition", "Momentum and impulse",
    "**Linear momentum** p = mv (a vector, unit kg m s⁻¹ or N s). **Impulse** = force × time of contact = FΔt = Δp, the change of momentum. On a force–time graph the **area** under the curve is the impulse.\n\n"
    "Newton's second law in its general form: F = Δp/Δt."),
   ("formule", "Conservation of momentum",
    "In a closed system (no external resultant force) **total momentum before = total momentum after**: m₁u₁ + m₂u₂ = m₁v₁ + m₂v₂.\n\n"
    "Momentum is a vector: choose a positive direction and give velocities signs. It is conserved in **all** collisions and explosions.",
    r"m_1u_1+m_2u_2=m_1v_1+m_2v_2"),
   ("retenir", "Elastic and inelastic collisions",
    "In an **elastic** collision total kinetic energy is also conserved (the relative speed of approach equals the relative speed of separation). In an **inelastic** collision some kinetic energy is changed to heat, sound or deformation; if the bodies stick together the loss is the greatest ('perfectly inelastic').\n\nTotal energy is always conserved."),
   ("pieges", "Common mistakes",
    "- Forgetting that momentum is a vector: a ball bouncing back from a wall has a change of momentum m(v + u), not m(v − u).\n- Saying kinetic energy is conserved in every collision: only in elastic ones.\n- Mixing units (grams with kg).\n- Using the wrong sign for a body moving in the negative direction.")],
  [("Example 1 — Trolleys that stick", "A 2.0 kg trolley moving at 3.0 m/s hits a stationary 1.0 kg trolley and they stick together. Find their common speed and the kinetic energy lost.",
    ["Momentum before = 2.0 × 3.0 = 6.0 kg m/s. After: (2.0 + 1.0)v = 6.0, so v = 2.0 m/s.", "KE before = ½ × 2.0 × 3.0² = 9.0 J; KE after = ½ × 3.0 × 2.0² = 6.0 J.", "Energy lost = 9.0 − 6.0 = 3.0 J (to heat and sound)."],
    "v = 2.0 m/s; 3.0 J of kinetic energy is lost"),
   ("Example 2 — Ball and wall", "A 0.15 kg ball hits a wall at 20 m/s and rebounds at 15 m/s. The contact lasts 0.010 s. Find the impulse and the average force.",
    ["Take the rebound direction as positive: change of momentum = 0.15 × 15 − 0.15 × (−20) = 2.25 + 3.00 = 5.25 N s.", "F = impulse/time = 5.25 ÷ 0.010 = 525 N, directed away from the wall."],
    "Impulse 5.25 N s; average force 525 N")],
  [N("Calculate the momentum of a 1200 kg car moving at 15 m/s (kg m/s).", 1200 * 15, "p = mv = 1200 × 15 = 18 000 kg m/s.", 1, "kg m/s"),
   N("A 0.50 kg ball is struck by a force of 40 N for 0.020 s. What is the change of momentum (N s)?", 0.8, "Impulse = FΔt = 40 × 0.020 = 0.80 N s.", 0.005, "N s"),
   TF("True or false: kinetic energy is conserved in all collisions.", False, "Only in elastic collisions. Momentum is conserved in all collisions when no external force acts.")],
  [N("A rifle of mass 4.0 kg fires a 0.020 kg bullet at 400 m/s. What is the recoil speed of the rifle (m/s)?", v_rec, "Total momentum is zero: 4.0 v = 0.020 × 400, so v = 2.0 m/s opposite to the bullet.", 0.01, "m/s", difficulty=2),
   M("A 1.0 kg mass moving at 4.0 m/s collides elastically head-on with a stationary 1.0 kg mass. Afterwards:", "the first stops and the second moves at 4.0 m/s", ["both move at 2.0 m/s", "both move at 4.0 m/s", "the first rebounds at 4.0 m/s"], "Equal masses exchange velocities in a head-on elastic collision: momentum and kinetic energy are both conserved.")],
  P("A 1500 kg minibus travelling at 10 m/s hits the back of a stationary 1000 kg car. They move together after the impact.",
    [pn("Find the speed immediately after the collision (m/s).", 1500 * 10 / 2500, "1500 × 10 = (1500 + 1000)v, so v = 6.0 m/s.", 0.01, "m/s", 1),
     pn("Find the kinetic energy lost (J).", 0.5 * 1500 * 100 - 0.5 * 2500 * 36, "KE before = ½ × 1500 × 100 = 75 000 J; after = ½ × 2500 × 36 = 45 000 J; loss = 30 000 J.", 10, "J", 2),
     pm("This collision is best described as", "perfectly inelastic", ["elastic", "explosive", "one where momentum is lost"], "The bodies stick together and kinetic energy is lost; momentum is conserved.", 1)]),
  [M("The unit of impulse is equivalent to", "kg m s⁻¹", ["kg m s⁻²", "kg m² s⁻²", "kg s⁻¹"], "Impulse = change of momentum = N s = kg m s⁻¹."),
   M("The area under a force–time graph gives", "impulse", ["work done", "power", "acceleration"], "Area = F × t = impulse."),
   M("In an explosion of a body initially at rest, the total momentum afterwards is", "zero", ["equal to the total kinetic energy", "equal to the kinetic energy lost", "different for each piece"], "It is conserved and was zero before."),
   M("A ball of mass 0.2 kg moving at 10 m/s stops in 0.05 s. The average force is", "40 N", ["2 N", "100 N", "0.4 N"], "F = Δp/t = 2.0/0.05 = 40 N."),
   M("A 3 kg trolley at 2 m/s hits and joins a 1 kg trolley at rest. The common speed is", "1.5 m/s", ["2 m/s", "1 m/s", "0.67 m/s"], "6 = 4v, so v = 1.5 m/s.")],
  (coll_fig(), "Two trolleys before and after a perfectly inelastic collision.", "A 2 kilogram trolley moving at 3 metres per second and a stationary 1 kilogram trolley before the collision, then a 3 kilogram combined body at 2 metres per second after."),
  notes=["Collisions in two dimensions are not covered; check the syllabus.", "Elastic head-on collision speeds (general formula) are not derived here."], minutes=30, prereq=["lower6-physics-newton"])

# ---- 2.6 work, energy, power
E_h = 60 * g * 1000; assert abs(E_h - 588000) < 1e-6
P_use = 120 * g * 15 / 60; P_in = P_use / 0.60; assert abs(P_use - 294) < 1e-9 and abs(P_in - 490) < 1e-9
v_rc = math.sqrt(2 * g * 20); assert abs(v_rc - 19.8) < 0.01
dKE = 0.5 * 1500 * (20 ** 2 - 10 ** 2); Fnet = dKE / 50; t_ = 50 / 15; P_av = dKE / t_
assert dKE == 225000 and Fnet == 4500 and abs(P_av - 67500) < 1e-6
def energy_fig():
    return bars([("PE top", 196, "orange"), ("KE halfway", 98, "blue"), ("PE halfway", 98, "orange"), ("KE bottom", 196, "blue")], unit="J", w=400, h=240)
lesson(ch, "energy", "Work, energy and power",
  ["Calculate work done by a constant force, including at an angle to the displacement",
   "Use kinetic energy ½mv² and gravitational potential energy mgh with conservation of energy",
   "Calculate power and efficiency, including P = Fv"],
  [("definition", "Work, energy and power",
    "**Work** done by a force = force × displacement in the direction of the force: **W = Fs cos θ** (joule). **Energy** is the capacity to do work. **Power** is the rate of doing work, P = W/t (watt, 1 W = 1 J s⁻¹).\n\n"
    "Work done by the resultant force equals the change in kinetic energy (the **work–energy theorem**)."),
   ("formule", "Key formulae",
    "- Kinetic energy: E_k = ½mv²\n- Gravitational potential energy (near the surface): ΔE_p = mgΔh\n- Power: P = W/t = Fv (for a force along the velocity)\n- Efficiency = useful output ÷ total input (× 100 %)\n\nWhen only gravity does work (no friction), E_k + E_p stays constant.",
    r"W=Fs\cos\theta\quad E_k=\frac{1}{2}mv^{2}\quad P=\frac{W}{t}=Fv"),
   ("retenir", "Conservation of energy",
    "Energy is never created or destroyed; it changes form. Friction and air resistance convert mechanical energy to thermal energy, which is not 'lost' but no longer useful.\n\nFor a body falling through height h from rest with no friction: mgh = ½mv², so v = √(2gh)."),
   ("pieges", "Common mistakes",
    "- A force perpendicular to the motion (for example the normal reaction, or the tension on a circular path) does **no work**.\n- Forgetting the cos θ when the force is at an angle.\n- Mixing kW h with J: 1 kW h = 3.6 × 10⁶ J.\n- Saying power is energy (it is energy per second).")],
  [("Example 1 — A pump", "A pump raises 120 kg of water each minute through 15 m. Its efficiency is 60 %. Find the useful power and the power input (g = 9.8 m s⁻²).",
    ["Useful energy per minute = mgh = 120 × 9.8 × 15 = 17 640 J.", "Useful power = 17 640 ÷ 60 s = 294 W.", "Input power = 294 ÷ 0.60 = 490 W."],
    "Useful power 294 W; input 490 W"),
   ("Example 2 — Roller coaster", "A car starts from rest at the top of a frictionless track 20 m above the bottom. Find its speed at the bottom (g = 9.8).",
    ["Loss of potential energy = gain of kinetic energy: mgh = ½mv².", "The mass cancels: v = √(2gh) = √(2 × 9.8 × 20) = √392 = 19.8 m/s."],
    "v = 19.8 m/s (about 71 km/h)")],
  [N("A force of 50 N pulls a trolley 10 m along the ground at 60° above the horizontal. How much work does it do (J)?", sg(50 * 10 * math.cos(rad(60)), 3), "W = Fs cos θ = 50 × 10 × 0.5 = 250 J.", 0.5, "J"),
   N("Calculate the kinetic energy of a 1200 kg car moving at 15 m/s (J).", 0.5 * 1200 * 15 ** 2, "E_k = ½mv² = 0.5 × 1200 × 225 = 135 000 J.", 10, "J"),
   TF("True or false: the normal reaction does work on a box sliding along a horizontal floor.", False, "The force is perpendicular to the displacement (cos 90° = 0), so no work is done.")],
  [N("A car engine delivers 40 kW at a constant speed of 20 m/s. Find the total resistive force (N).", 40000 / 20, "At constant speed the driving force equals the resistance: F = P/v = 40 000/20 = 2000 N.", 1, "N", difficulty=2),
   N("A 2.0 kg ball is thrown up at 15 m/s. Find the maximum height by energy conservation (m, 3 s.f.; g = 9.8).", sg(15 ** 2 / (2 * g), 3), "½mv² = mgh, so h = v²/2g = 225/19.6 = 11.5 m.", 0.1, "m")],
  P("A 1500 kg car accelerates uniformly from 10 m/s to 20 m/s over a distance of 50 m on a straight road.",
    [pn("Find the increase in kinetic energy (J).", dKE, "ΔE_k = ½ × 1500 × (20² − 10²) = 225 000 J.", 10, "J", 1),
     pn("Find the average resultant force (N) using work = force × distance.", Fnet, "F = ΔE_k/s = 225 000/50 = 4500 N.", 1, "N", 1),
     pn("Find the average power developed by the resultant force (W). The average speed is 15 m/s.", P_av, "t = 50/15 = 3.33 s; P = 225 000/3.33 = 67 500 W (or F × v_avg = 4500 × 15).", 100, "W", 2)]),
  [M("A hiker of mass 60 kg climbs 1000 m up Mount Cameroon's slopes. The gain in potential energy (g = 9.8) is", "588 kJ", ["59 kJ", "6000 kJ", "58.8 J"], "mgh = 60 × 9.8 × 1000 = 588 000 J = 588 kJ."),
   M("A 5 kW motor runs for 2.0 hours. The energy it uses is", "10 kW h", ["2.5 kW h", "7 kW h", "10 kW"], "Energy = power × time = 5 × 2.0 = 10 kW h."),
   M("A body's speed doubles. Its kinetic energy is multiplied by", "4", ["2", "8", "1/2"], "E_k ∝ v², so doubling v gives 4 times the energy."),
   M("The efficiency of a machine is 40 %. For an input of 500 J the useful output is", "200 J", ["20 J", "1250 J", "460 J"], "0.40 × 500 = 200 J."),
   M("The unit watt is the same as", "J s⁻¹", ["J s", "N s", "kg m s⁻²"], "Power = energy ÷ time.")],
  (energy_fig(), "A 2.0 kg object falling 10 m: energy at the top, halfway and at the bottom (g = 9.8 m s⁻²).", "A bar chart showing 196 joules of potential energy at the top, 98 joules each of kinetic and potential energy halfway, and 196 joules of kinetic energy at the bottom."),
  notes=["Bars figure uses m = 2.0 kg and h = 10 m: mgh = 2.0 × 9.8 × 10 = 196 J."], minutes=30, prereq=["lower6-physics-newton"])

# ---- 2.7 circular motion
Fc = 1200 * 15 ** 2 / 50; mu_min = 15 ** 2 / (50 * g); assert Fc == 5400 and abs(mu_min - 0.459) < 0.001
w_r = 3000 * 2 * math.pi / 60; vt_r = w_r * 0.10; a_r = w_r ** 2 * 0.10
assert abs(w_r - 314.16) < 0.01 and abs(vt_r - 31.42) < 0.01 and abs(a_r - 9870) < 5
w_s = 2.0 * 2 * math.pi; v_s = w_s * 0.80; T_s = 0.20 * w_s ** 2 * 0.80
assert abs(w_s - 12.57) < 0.01 and abs(v_s - 10.05) < 0.01 and abs(T_s - 25.3) < 0.05
def circ_fig():
    it = [CIRCLE(190, 130, 85, stroke="grey", width=2), dot(190, 130), LINE(190, 130, 275, 130, dash=True, color="grey", width=2), T(232, 122, "r", 14),
          CIRCLE(275, 130, 8, fill="orange"), LINE(275, 130, 275, 60, color="blue", width=3, arrow="end"), T(290, 80, "v", 15, anchor="start", color="blue"),
          LINE(267, 130, 205, 130, color="red", width=3, arrow="end"), T(236, 150, "F, a", 14, color="red", bold=True), ANGLE(190, 130, 0, 0, r=1)]
    it = it[:-1]
    return shapes(it + [T(190, 232, "force and acceleration point to the centre", 13)], 380, 250)
lesson(ch, "circular", "Uniform circular motion",
  ["Use angular displacement in radians, angular velocity ω, period T and frequency f",
   "Explain why a body moving in a circle at constant speed accelerates, and use a = v²/r and F = mv²/r",
   "Identify the force providing the centripetal force in simple situations"],
  [("definition", "Angular quantities",
    "An angle in **radians** = arc length ÷ radius (2π rad = 360°). **Angular velocity** ω = Δθ/Δt (rad s⁻¹). Period T = time for one revolution; frequency f = 1/T.\n\n"
    "**ω = 2π/T = 2πf** and the linear speed on a circle of radius r is **v = ωr**."),
   ("formule", "Centripetal acceleration and force",
    "A body moving at constant speed in a circle has a velocity that keeps changing direction, so it **accelerates**. The acceleration points to the **centre**: a = v²/r = ω²r.\n\n"
    "The resultant force towards the centre (centripetal force) is **F = mv²/r = mω²r**. It is not a new force: it is provided by tension, friction, gravity, a normal reaction…",
    r"a=\frac{v^{2}}{r}=\omega^{2}r\qquad F=\frac{mv^{2}}{r}=m\omega^{2}r"),
   ("methode", "Method",
    "1. Draw the free-body diagram of the body and identify the forces pointing to the centre.\n2. The resultant towards the centre equals mv²/r.\n3. Convert rpm to rad s⁻¹: multiply by 2π/60.\n4. Remember: if the centripetal force vanishes the body moves off along the **tangent**, not outward."),
   ("pieges", "Common mistakes",
    "- 'Centrifugal force' is not a real force acting on the body; do not add it to the free-body diagram.\n- Using degrees in v = ωr: ω must be in rad s⁻¹.\n- Saying the acceleration is zero because the speed is constant.\n- Forgetting that the centripetal force does no work (it is perpendicular to the motion).")],
  [("Example 1 — Car on a bend", "A 1200 kg car takes a flat circular bend of radius 50 m at 15 m/s. Find the centripetal force and the least coefficient of friction needed (g = 9.8).",
    ["F = mv²/r = 1200 × 15²/50 = 5400 N, provided by friction between tyres and road.", "Friction limit: μmg ≥ mv²/r, so μ ≥ v²/(rg) = 225/(50 × 9.8) = 0.46."],
    "F = 5400 N, μ ≥ 0.46"),
   ("Example 2 — A spinning rotor", "A rotor turns at 3000 revolutions per minute. Find ω, the speed of a point 0.10 m from the axis, and its acceleration.",
    ["ω = 3000 × 2π/60 = 314 rad s⁻¹.", "v = ωr = 314 × 0.10 = 31.4 m/s.", "a = ω²r = (314.2)² × 0.10 = 9.87 × 10³ m s⁻² (about 1000 g)."],
    "ω = 314 rad/s, v = 31.4 m/s, a = 9.9 × 10³ m/s²")],
  [N("A wheel turns at 120 revolutions per minute. Find ω in rad s⁻¹ (3 s.f.).", sg(120 * 2 * math.pi / 60, 3), "ω = 120 × 2π/60 = 12.6 rad s⁻¹.", 0.05, "rad/s"),
   N("A point on a roundabout of radius 3.0 m moves at 6.0 m/s. Find its centripetal acceleration (m s⁻²).", 12.0, "a = v²/r = 36/3.0 = 12 m s⁻².", 0.05, "m/s²"),
   TF("True or false: a body moving in a circle at constant speed has zero acceleration.", False, "Its velocity changes direction, so it accelerates towards the centre (a = v²/r).")],
  [N("A 0.50 kg ball on a string moves in a horizontal circle of radius 1.0 m at 4.0 m/s (ignore gravity's effect on the string angle). Find the tension (N).", 0.5 * 16 / 1.0, "T = mv²/r = 0.50 × 16/1.0 = 8.0 N.", 0.05, "N", difficulty=2),
   N("A bucket of water is swung in a vertical circle of radius 0.80 m. Find the minimum speed at the top for the water to stay in (m/s, 2 s.f.; g = 9.8).", sg(math.sqrt(g * 0.8), 2), "At the top, mg = mv²/r (N = 0), so v = √(gr) = √(7.84) = 2.8 m/s.", 0.05, "m/s")],
  P("A 0.20 kg stone on a 0.80 m string is whirled in a horizontal circle at 2.0 revolutions per second (neglect the weight of the stone's effect on the angle of the string).",
    [pn("Angular velocity (rad s⁻¹, 3 s.f.).", sg(w_s, 3), "ω = 2π × 2.0 = 12.6 rad s⁻¹.", 0.05, "rad/s", 1),
     pn("Speed of the stone (m/s, 3 s.f.).", sg(v_s, 3), "v = ωr = 12.57 × 0.80 = 10.1 m/s.", 0.05, "m/s", 1),
     pn("Tension in the string (N, 3 s.f.).", sg(T_s, 3), "T = mω²r = 0.20 × 12.57² × 0.80 = 25.3 N.", 0.2, "N", 2)]),
  [M("The direction of the acceleration of a body in uniform circular motion is", "towards the centre", ["along the tangent", "away from the centre", "opposite to the velocity"], "a = v²/r points to the centre."),
   M("The period of a motor turning at 50 revolutions per second is", "0.020 s", ["50 s", "0.50 s", "314 s"], "T = 1/f = 1/50 = 0.020 s."),
   M("If the speed of a body in a circle doubles at the same radius, the centripetal force is multiplied by", "4", ["2", "8", "1/2"], "F ∝ v², so 2² = 4."),
   M("The centripetal force on the Moon orbiting the Earth is provided by", "gravity", ["friction", "tension", "air pressure"], "The gravitational attraction of the Earth acts towards the centre."),
   M("A stone whirled on a string is released. It then moves", "along the tangent", ["towards the centre", "away along the radius", "in a smaller circle"], "With no force, it moves in a straight line in the direction of its velocity at release.")],
  (circ_fig(), "Uniform circular motion: the velocity is tangential; the resultant force and acceleration point to the centre.", "A circle with a body on it, an arrow along the tangent for velocity and an arrow towards the centre for force and acceleration."),
  notes=["Banked tracks and conical pendulum are not included; check the syllabus.", "Use of 'centrifugal force' in the board's mark scheme should be checked."], minutes=30, prereq=["lower6-physics-newton", "lower6-physics-energy"])

# ---- 2.8 gravitation
G_, Me, Re = 6.67e-11, 6.0e24, 6.4e6
g_s = G_ * Me / Re ** 2; assert abs(g_s - 9.77) < 0.01
g_R = g_s / 4
r_i = Re + 400e3; v_i = math.sqrt(G_ * Me / r_i); T_i = 2 * math.pi * r_i / v_i
assert abs(v_i - 7672) < 5 and abs(T_i / 60 - 92.8) < 0.5, (v_i, T_i / 60)
Tgeo = 24 * 3600.0; r_g = (G_ * Me * Tgeo ** 2 / (4 * math.pi ** 2)) ** (1 / 3); h_g = r_g - Re; v_g = 2 * math.pi * r_g / Tgeo
assert abs(r_g - 4.23e7) < 0.05e7 and abs(v_g - 3070) < 40, (r_g, v_g)
v_esc = math.sqrt(2 * G_ * Me / Re); assert abs(v_esc - 11180) < 100
def grav_fig():
    it = [CIRCLE(170, 125, 38, fill="lightblue", width=2), T(170, 130, "Earth", 14), CIRCLE(170, 125, 100, stroke="grey", width=1),
          CIRCLE(270, 125, 8, fill="orange"), T(285, 108, "satellite", 13, anchor="start"), LINE(170, 125, 270, 125, dash=True, color="grey", width=1),
          LINE(262, 125, 215, 125, color="red", width=3, arrow="end"), T(230, 148, "F", 15, color="red", bold=True), T(222, 108, "r", 14),
          T(190, 238, "F = GMm / r²", 14, bold=True)]
    return shapes(it, 400, 250)
lesson(ch, "gravitation", "Gravitational fields and orbits",
  ["State Newton's law of gravitation and define gravitational field strength",
   "Derive and use orbital speed and period for circular orbits, including the geostationary orbit",
   "Use gravitational potential and calculate escape speed"],
  [("definition", "Law and field",
    "**Newton's law**: two point masses attract with a force **F = GMm/r²** along the line joining them (G = 6.67 × 10⁻¹¹ N m² kg⁻²).\n\n"
    "**Gravitational field strength** g = force per unit mass = GM/r², directed towards the mass M. At the Earth's surface it is about 9.8 N kg⁻¹ (= m s⁻²). Outside a spherical body, the mass acts as if concentrated at its centre."),
   ("formule", "Circular orbits",
    "For a satellite in a circular orbit of radius r the gravitational force provides the centripetal force: GMm/r² = mv²/r. So **v = √(GM/r)** and **T = 2πr/v**, giving **T² = (4π²/GM) r³** (Kepler's third law).\n\n"
    "The mass of the satellite cancels: every satellite at the same radius has the same speed.",
    r"v=\sqrt{\frac{GM}{r}}\qquad \frac{T^{2}}{r^{3}}=\frac{4\pi^{2}}{GM}"),
   ("retenir", "Potential and escape speed",
    "**Gravitational potential** V = −GM/r (J kg⁻¹): work done per unit mass to bring a mass from infinity (V = 0 at infinity); it is negative. A mass m has potential energy −GMm/r.\n\n"
    "To escape from the surface the kinetic energy must be at least GMm/R: **v_esc = √(2GM/R)** ≈ 11 km s⁻¹ for the Earth. A **geostationary** satellite has T = 24 h, orbits above the equator, and stays above one point."),
   ("pieges", "Common mistakes",
    "- Using the height above the surface as r: r is measured from the **centre** (r = R + h).\n- Thinking astronauts are 'beyond gravity': in orbit they are in free fall, so they feel weightless.\n- Forgetting that g varies as 1/r² (doubling r divides g by 4).\n- Mixing km and m in GM/r.")],
  [("Example 1 — g above the Earth", "Using G = 6.67 × 10⁻¹¹, M_E = 6.0 × 10²⁴ kg and R_E = 6.4 × 10⁶ m, find g at the surface and at a height of one Earth radius above it.",
    ["Surface: g = GM/R² = 6.67 × 10⁻¹¹ × 6.0 × 10²⁴ ÷ (6.4 × 10⁶)² = 9.77 m s⁻².", "At a height R above the surface, r = 2R, so g is divided by 2² = 4: 9.77/4 = 2.44 m s⁻²."],
    "9.77 m s⁻² at the surface, 2.44 m s⁻² at height R"),
   ("Example 2 — A low satellite", "A satellite orbits 400 km above the Earth (same data). Find its speed and period.",
    ["r = 6.4 × 10⁶ + 0.4 × 10⁶ = 6.8 × 10⁶ m.", "v = √(GM/r) = √(4.002 × 10¹⁴ ÷ 6.8 × 10⁶) = 7.67 × 10³ m/s.", "T = 2πr/v = 2π × 6.8 × 10⁶ ÷ 7672 = 5570 s ≈ 93 minutes."],
    "v ≈ 7.7 km/s; T ≈ 93 min")],
  [N("Two 1000 kg cars are 2.0 m apart. Find the gravitational force between them (N; G = 6.67 × 10⁻¹¹), in units of 10⁻⁵ N to 3 s.f.", sg(G_ * 1000 * 1000 / 4 * 1e5, 3), "F = GMm/r² = 6.67 × 10⁻¹¹ × 10⁶/4 = 1.67 × 10⁻⁵ N, so 1.67 in these units (a tiny force).", 0.02, "× 10⁻⁵ N"),
   M("The gravitational field strength at a distance 3R from the centre of a planet (R = radius) is what fraction of its surface value?", "1/9", ["1/3", "1/6", "1/27"], "g ∝ 1/r², so (1/3)² = 1/9."),
   TF("True or false: astronauts in orbit feel weightless because gravity is zero there.", False, "At 400 km gravity is still about 90 % of its surface value; they are in free fall around the Earth.")],
  [N("Use T² = 4π²r³/GM to find the radius of a circular orbit (in 10⁷ m, 3 s.f.) for a satellite of period 24 h (geostationary). G = 6.67 × 10⁻¹¹, M = 6.0 × 10²⁴ kg.", sg(r_g / 1e7, 3), "r = (GMT²/4π²)^(1/3) = 4.23 × 10⁷ m.", 0.03, "× 10⁷ m", difficulty=2),
   N("Escape speed from the Earth's surface (km s⁻¹, 3 s.f.; G, M, R as in the lesson).", sg(v_esc / 1000, 3), "v = √(2GM/R) = √(2 × 4.002 × 10¹⁴ ÷ 6.4 × 10⁶) = 11.2 km/s.", 0.1, "km/s")],
  P("A communications satellite is placed in a geostationary orbit (period 24 h). Use G = 6.67 × 10⁻¹¹ N m² kg⁻², M_E = 6.0 × 10²⁴ kg and R_E = 6.4 × 10⁶ m.",
    [pn("Orbital radius from the Earth's centre (10⁷ m, 3 s.f.).", sg(r_g / 1e7, 3), "r³ = GMT²/4π² gives r = 4.23 × 10⁷ m.", 0.03, "× 10⁷ m", 2),
     pn("Height above the Earth's surface (10⁷ m, 3 s.f.).", sg(h_g / 1e7, 3), "h = r − R = 4.23 × 10⁷ − 0.64 × 10⁷ = 3.59 × 10⁷ m (about 36 000 km).", 0.03, "× 10⁷ m", 1),
     pn("Orbital speed (m/s, 3 s.f.).", sg(v_g, 3), "v = 2πr/T = 2π × 4.23 × 10⁷ ÷ 86 400 = 3.08 × 10³ m/s.", 40, "m/s", 1)]),
  [M("The SI unit of gravitational field strength is equivalent to", "N kg⁻¹", ["N kg", "N m", "kg m⁻¹"], "g = F/m, so N per kg (= m s⁻²)."),
   M("If the radius of a circular orbit increases, the orbital speed", "decreases", ["increases", "stays the same", "becomes zero"], "v = √(GM/r) decreases as r increases."),
   M("A satellite's period is T at radius r. At radius 4r its period is", "8T", ["2T", "4T", "16T"], "T² ∝ r³, so T ∝ r^{3/2} = 4^{3/2} = 8."),
   M("The gravitational potential at infinity is taken as", "zero", ["maximum negative", "positive", "g"], "Potentials near a mass are negative relative to zero at infinity."),
   M("A geostationary satellite has a period of", "24 hours", ["12 hours", "90 minutes", "1 year"], "It matches the Earth's rotation.")],
  (grav_fig(), "A satellite in a circular orbit: gravity provides the centripetal force.", "The Earth at the centre, a circular orbit, a satellite and an arrow towards the Earth's centre labelled F."),
  notes=["Constants used: G = 6.67 × 10⁻¹¹, M_E = 6.0 × 10²⁴ kg, R_E = 6.4 × 10⁶ m. The geostationary period is taken as 24 h (the exact sidereal day is 23 h 56 min).", "Gravitational potential may be placed in Upper Sixth by some schools; check the syllabus."], minutes=35, prereq=["lower6-physics-circular"])

# =============================================================== 3. MATERIALS AND FLUIDS
ch = p.chapter("ch-materials", "Materials and fluids", SYL % "properties of matter")

# ---- 3.1 Hooke, Young
F_s = 200 * 0.15; E_s = 0.5 * 200 * 0.15 ** 2; assert F_s == 30 and abs(E_s - 2.25) < 1e-12
A_w = math.pi * (0.40e-3) ** 2; sig = 100 / A_w; Ey = 2.0e11; eps = sig / Ey; ext = eps * 2.0
assert abs(sig - 1.989e8) < 2e5 and abs(ext * 1000 - 1.989) < 0.01, (sig, ext)
k_ser = 1 / (1 / 200 + 1 / 200); k_par = 400; assert k_ser == 100
def fe_fig():
    it = [LINE(50, 210, 50, 25, arrow="end"), LINE(50, 210, 360, 210, arrow="end"), T(52, 18, "force F", 13, anchor="start"), T(360, 232, "extension x", 13, anchor="end"),
          LINE(50, 210, 190, 90, color="blue", width=3), PATH("M 190 90 C 230 62 260 58 320 52", stroke="blue", width=3),
          CIRCLE(190, 90, 5, fill="red", stroke="red"), T(200, 112, "limit of proportionality", 12, anchor="start", color="red"),
          T(85, 130, "gradient = k", 13, anchor="start", color="blue")]
    return shapes(it, 400, 250)
lesson(ch, "elasticity", "Hooke's law, stress, strain and the Young modulus",
  ["State Hooke's law, use F = kx and calculate the energy stored in a stretched spring or wire",
   "Define stress, strain and the Young modulus and use them for a wire",
   "Interpret force–extension and stress–strain graphs (limit of proportionality, elastic limit, plastic deformation)"],
  [("definition", "Hooke's law",
    "Within the **limit of proportionality** the extension x of a spring or wire is directly proportional to the force F: **F = kx**, where k (N m⁻¹) is the **force constant** (stiffness).\n\n"
    "Springs in **series**: 1/k = 1/k₁ + 1/k₂ (softer). Springs in **parallel**: k = k₁ + k₂ (stiffer).\n\nThe energy stored (elastic potential energy) is the area under the F–x graph: **E = ½Fx = ½kx²**."),
   ("formule", "Stress, strain and Young modulus",
    "**Stress** σ = F/A (Pa = N m⁻²). **Strain** ε = extension/original length = x/L (no unit). In the proportional region the ratio is constant:\n\n**Young modulus E = σ/ε = FL/(Ax)** (Pa). It depends on the material, not on the size of the sample.\n\nTypical value for steel: about 2 × 10¹¹ Pa (a textbook value; check your table).",
    r"E=\frac{\sigma}{\varepsilon}=\frac{F/A}{x/L}=\frac{FL}{Ax}"),
   ("retenir", "Behaviour beyond Hooke's law",
    "Beyond the **elastic limit** the material is permanently stretched (**plastic deformation**) and does not return to its original length when the load is removed. At the **yield point** the extension increases with little extra force, and the material finally breaks at the breaking stress. A **brittle** material (glass) breaks with little plastic deformation; a **ductile** one (copper) stretches a lot."),
   ("pieges", "Common mistakes",
    "- Using the total length of the wire after stretching as L: use the **original** length.\n- Converting diameter instead of radius: A = π(d/2)², with d in metres (0.80 mm = 0.80 × 10⁻³ m).\n- Mixing up the limit of proportionality (Hooke's law fails) with the elastic limit (permanent deformation starts); they are close together but not identical.\n- Forgetting the ½ in the energy stored.")],
  [("Example 1 — A spring", "A spring has force constant k = 200 N/m and is stretched by 0.15 m. Find the force applied and the energy stored.",
    ["F = kx = 200 × 0.15 = 30 N.", "E = ½kx² = ½ × 200 × 0.15² = 2.25 J (also ½Fx = ½ × 30 × 0.15)."], "F = 30 N and E = 2.25 J"),
   ("Example 2 — A steel wire", "A steel wire of length 2.0 m and diameter 0.80 mm carries a load of 100 N. Take E = 2.0 × 10¹¹ Pa. Find the stress, the strain and the extension.",
    ["A = π(0.40 × 10⁻³)² = 5.03 × 10⁻⁷ m², so stress = 100/5.03 × 10⁻⁷ = 1.99 × 10⁸ Pa.", "Strain = stress/E = 1.99 × 10⁸/2.0 × 10¹¹ = 9.9 × 10⁻⁴.", "Extension = strain × L = 9.9 × 10⁻⁴ × 2.0 = 1.99 × 10⁻³ m ≈ 2.0 mm."],
    "Stress 2.0 × 10⁸ Pa, strain 1.0 × 10⁻³, extension 2.0 mm")],
  [N("A spring (k = 80 N/m) is stretched by 0.25 m. Find the force (N).", 80 * 0.25, "F = kx = 80 × 0.25 = 20 N.", 0.05, "N"),
   N("A 150 N force acts on a wire of cross-section 3.0 × 10⁻⁶ m². Find the stress (10⁷ Pa).", 150 / 3.0e-6 / 1e7, "σ = F/A = 150/3.0 × 10⁻⁶ = 5.0 × 10⁷ Pa, i.e. 5.0 in units of 10⁷ Pa.", 0.01, "× 10⁷ Pa"),
   TF("True or false: the Young modulus of a material depends on the length of the wire used.", False, "E is a property of the material; length and area cancel out in FL/Ax.")],
  [N("Two identical springs, each k = 200 N/m, are joined in series. What is the force constant of the combination (N/m)?", k_ser, "1/k = 1/200 + 1/200 = 1/100, so k = 100 N/m. (In parallel it would be 400 N/m.)", 0.5, "N/m", difficulty=2),
   N("A wire of original length 1.50 m and cross-section 1.0 × 10⁻⁶ m² extends by 1.5 mm under a load of 80 N. Find the Young modulus (10¹¹ Pa, 3 s.f.).", sg(80 * 1.5 / (1.0e-6 * 1.5e-3) / 1e11, 3), "E = FL/Ax = 80 × 1.5/(1.0 × 10⁻⁶ × 1.5 × 10⁻³) = 8.0 × 10¹⁰ Pa = 0.800 × 10¹¹ Pa.", 0.01, "× 10¹¹ Pa")],
  P("A student loads a spring with weights and measures the extension. The force–extension graph is a straight line through the origin up to 6.0 N, where the extension is 0.12 m.",
    [pn("Find the force constant of the spring (N/m).", 6.0 / 0.12, "k = F/x = 6.0/0.12 = 50 N/m.", 0.5, "N/m", 1),
     pn("Find the energy stored at an extension of 0.12 m (J).", 0.5 * 6.0 * 0.12, "E = ½Fx = ½ × 6.0 × 0.12 = 0.36 J.", 0.005, "J", 1),
     pm("Beyond 6.0 N the line begins to curve. This point is called the", "limit of proportionality", ["breaking point", "yield stress of the load", "centre of mass"], "Beyond it F is no longer proportional to x, so Hooke's law no longer applies.", 1),
     pn("A second identical spring is placed in parallel with the first. What load gives the same extension of 0.12 m (N)?", 12.0, "k doubles to 100 N/m, so F = 100 × 0.12 = 12 N.", 0.05, "N", 1)], figure=fe_fig()),
  [M("The unit of the Young modulus is the same as that of", "pressure", ["force", "energy", "strain"], "E = stress ÷ strain and strain has no unit, so E is in Pa."),
   M("The area under a force–extension graph (straight-line part) gives", "energy stored", ["force constant", "stress", "strain"], "Area = ½Fx, the elastic potential energy."),
   M("A wire's length and radius are both doubled (same material, same load). The extension", "is halved", ["doubles", "quadruples", "is unchanged"], "x = FL/AE: L ×2 and A ×4 gives x × 2/4 = ½ the original extension."),
   M("A material that stretches a great deal before breaking is", "ductile", ["brittle", "elastic only", "rigid"], "Ductile materials show large plastic deformation, e.g. copper."),
   M("Springs in parallel have a combined force constant that is", "the sum of the individual constants", ["less than either", "the average of the two", "the product"], "Each spring takes part of the load, so k = k₁ + k₂.")],
  (fe_fig(), "Force–extension graph: linear up to the limit of proportionality, then curving.", "A graph of force against extension, a straight line to a red point marked limit of proportionality and a curve flattening beyond it."),
  notes=["Young modulus of steel is quoted as about 2 × 10¹¹ Pa; the data sheet of the board may give a different rounded value."], minutes=30, prereq=["lower6-physics-newton", "lower6-physics-energy"])

# ---- 3.2 Density, pressure, fluids
P10 = 1000 * g * 10; assert abs(P10 - 98000) < 1e-9
Ptot = P10 + 1.0e5
Fu = 1000 * 2.0e-3 * g; Wa = 15.6 * g - Fu; assert abs(Fu - 19.6) < 1e-9 and abs(Wa - 133.28) < 1e-9
v2c = 2.0 * (4.0 / 2.0) ** 2; assert v2c == 8.0
def pipe_fig():
    it = [POLY([40, 80, 160, 80, 220, 110, 340, 110, 340, 150, 220, 150, 160, 180, 40, 180], fill="lightblue", width=2),
          LINE(60, 130, 140, 130, color="red", width=3, arrow="end"), T(100, 115, "v₁, A₁", 13, color="red"),
          LINE(240, 130, 320, 130, color="red", width=3, arrow="end"), T(280, 105, "v₂, A₂", 13, color="red"),
          T(190, 215, "A₁v₁ = A₂v₂ : narrower pipe, faster flow", 13)]
    return shapes(it, 380, 240)
lesson(ch, "fluids", "Density, pressure and fluids",
  ["Define density and pressure and use p = ρgh for the pressure due to a liquid column",
   "Use Archimedes' principle to find upthrust and explain floating",
   "Apply the equation of continuity (A₁v₁ = A₂v₂) and the idea of terminal velocity in a viscous fluid"],
  [("definition", "Density and pressure",
    "**Density** ρ = m/V (kg m⁻³); fresh water 1000 kg m⁻³. **Pressure** p = F/A (pascal, Pa = N m⁻²).\n\n"
    "In a liquid at rest the pressure at depth h is **p = ρgh** (plus the atmospheric pressure at the surface, about 1.0 × 10⁵ Pa). It depends on depth and density, not on the shape of the container, and acts equally in all directions."),
   ("formule", "Upthrust and floating",
    "**Archimedes' principle**: the upthrust on a body fully or partly immersed in a fluid equals the weight of the fluid displaced: **U = ρ_fluid V_displaced g**.\n\n"
    "A body **floats** when upthrust = weight. For a floating body: ρ_body/ρ_fluid = fraction of its volume submerged. A body sinks if its average density exceeds the fluid's.",
    r"p=\rho g h\qquad U=\rho_{f}V g"),
   ("retenir", "Fluids in motion",
    "For an incompressible fluid in a pipe, the **equation of continuity** says the volume flow rate is constant: **A₁v₁ = A₂v₂** (narrower section, faster flow).\n\n"
    "For smooth (streamline) flow of a non-viscous liquid, Bernoulli's equation relates pressure and speed: p + ½ρv² + ρgh = constant (included for completeness; check the syllabus). In a **viscous** fluid a falling sphere reaches a terminal velocity when weight = upthrust + viscous drag."),
   ("pieges", "Common mistakes",
    "- Forgetting atmospheric pressure when the question asks for the **total (absolute)** pressure; the gauge pressure is ρgh alone.\n- Using the volume of the whole body for upthrust when only part is submerged.\n- Confusing density and relative density (relative density has no unit).\n- Converting cm³ to m³ wrongly (1 cm³ = 10⁻⁶ m³).")],
  [("Example 1 — Pressure in water", "Find the extra pressure and the total pressure 10 m below the surface of a lake (ρ = 1000 kg m⁻³, g = 9.8, atmospheric pressure 1.0 × 10⁵ Pa).",
    ["Extra pressure p = ρgh = 1000 × 9.8 × 10 = 9.8 × 10⁴ Pa.", "Total pressure = 9.8 × 10⁴ + 1.0 × 10⁵ = 1.98 × 10⁵ Pa (about twice atmospheric)."], "9.8 × 10⁴ Pa extra; 1.98 × 10⁵ Pa total"),
   ("Example 2 — A submerged block", "A steel block of mass 15.6 kg and volume 2.0 × 10⁻³ m³ is lowered fully into water. Find the upthrust and its apparent weight in water.",
    ["U = ρVg = 1000 × 2.0 × 10⁻³ × 9.8 = 19.6 N.", "True weight = 15.6 × 9.8 = 152.9 N.", "Apparent weight = 152.9 − 19.6 = 133.3 N."], "U = 19.6 N; apparent weight = 133 N")],
  [N("Find the mass of 0.50 m³ of a liquid of density 800 kg m⁻³ (kg).", 400, "m = ρV = 800 × 0.50 = 400 kg.", 0.5, "kg"),
   N("A force of 200 N acts on an area of 0.040 m². Find the pressure (Pa).", 5000, "p = F/A = 200/0.040 = 5000 Pa.", 5, "Pa"),
   TF("True or false: the pressure at a given depth in a liquid depends on the shape of the container.", False, "p = ρgh depends only on density and depth.")],
  [N("Water flows at 2.0 m/s in a pipe of diameter 4.0 cm into a section of diameter 2.0 cm. What is the speed in the narrow section (m/s)?", v2c, "A ∝ d², so A₂ = A₁/4 and v₂ = 4 × 2.0 = 8.0 m/s.", 0.05, "m/s", difficulty=2),
   N("A cube of wood (density 600 kg m⁻³) floats in water. What fraction of its volume is submerged (give a decimal)?", 0.6, "Fraction = ρ_wood/ρ_water = 600/1000 = 0.60.", 0.005, None)],
  P("A hydrometer-like glass tube has a cross-sectional area of 2.0 × 10⁻⁴ m² and a mass of 0.060 kg. It floats upright in water (ρ = 1000 kg m⁻³, g = 9.8).",
    [pn("What is the upthrust on the tube when floating (N)?", sg(0.060 * g, 3), "Floating means U = weight = 0.060 × 9.8 = 0.588 N.", 0.005, "N", 1),
     pn("Find the volume of water displaced (10⁻⁵ m³, 2 s.f.).", 6.0, "V = m/ρ = 0.060/1000 = 6.0 × 10⁻⁵ m³.", 0.05, "× 10⁻⁵ m³", 1),
     pn("Find the length of the tube below the water surface (m, 2 s.f.).", 0.30, "L = V/A = 6.0 × 10⁻⁵/2.0 × 10⁻⁴ = 0.30 m.", 0.005, "m", 2)]),
  [M("The pressure due to a liquid column of height h and density ρ is", "ρgh", ["ρh/g", "ρg/h", "h/ρg"], "p = ρgh."),
   M("An object floats when", "upthrust equals weight", ["upthrust is zero", "density is zero", "it is hollow"], "Equilibrium requires equal and opposite forces."),
   M("The SI unit of pressure, the pascal, is", "N m⁻²", ["N m", "N kg⁻¹", "kg m s⁻²"], "p = F/A."),
   M("An iron nail sinks in water because", "its density is greater than that of water", ["it has no upthrust", "water has no pressure", "it is heavy only"], "Weight exceeds the upthrust for a body denser than the fluid."),
   M("In a pipe the cross-section halves. The flow speed (incompressible fluid) becomes", "double", ["half", "four times", "unchanged"], "A₁v₁ = A₂v₂ so v doubles.")],
  (pipe_fig(), "Equation of continuity: the fluid speeds up in the narrow section.", "A pipe narrowing from a wide section on the left to a narrow section on the right, with arrows showing faster flow in the narrow part."),
  notes=["Bernoulli's equation may not be on the syllabus at this level; it is only mentioned.", "Atmospheric pressure is taken as 1.0 × 10⁵ Pa (1.01 × 10⁵ Pa in more precise tables)."], minutes=30, prereq=["lower6-physics-energy", "lower6-physics-elasticity"])

# =============================================================== 4. THERMAL PHYSICS
ch = p.chapter("ch-thermal", "Thermal physics", SYL % "thermal physics")
cw, Lf, Lv = 4200.0, 3.34e5, 2.26e6
Q1 = 2.0 * cw * 75; t1 = Q1 / 3000; assert Q1 == 630000 and t1 == 210
Q2 = 0.20 * Lf + 0.20 * cw * 20; assert abs(Q2 - 83600) < 1e-6
th_mix = (0.30 * 80 + 0.50 * 20) / 0.80; assert th_mix == 42.5
Q_boil = 1.0 * cw * 80; t_b = Q_boil / 1500; t_b8 = t_b / 0.8; t_ev = 0.10 * Lv / 1500
assert abs(t_b - 224) < 1e-9 and abs(t_b8 - 280) < 1e-9 and abs(t_ev - 150.67) < 0.01
def heat_fig():
    it = [LINE(60, 220, 60, 20, arrow="end"), LINE(60, 220, 370, 220, arrow="end"), T(66, 16, "temperature", 12, anchor="start"), T(370, 240, "energy supplied", 12, anchor="end"),
          T(52, 164, "0 °C", 12, anchor="end"), T(52, 64, "100 °C", 12, anchor="end"),
          POLY([70, 195, 105, 160, 165, 160, 245, 64, 315, 64, 345, 40], stroke="red", width=3, closed=False),
          T(76, 180, "ice", 12, anchor="start"), T(135, 180, "melting", 12), T(185, 108, "water", 12, anchor="end"), T(280, 84, "boiling", 12), T(345, 62, "steam", 12, anchor="start")]
    return shapes(it, 400, 250)
lesson(ch, "heat", "Specific heat capacity and latent heat",
  ["Define and use specific heat capacity (Q = mcΔθ) and specific latent heat (Q = mL)",
   "Solve problems on heating, melting, boiling and mixing, including electrical heating",
   "Interpret a heating curve and explain why temperature stays constant during a change of state"],
  [("definition", "Heat capacities and latent heats",
    "The **specific heat capacity** c of a substance is the energy needed to raise the temperature of 1 kg by 1 K: **Q = mcΔθ** (J kg⁻¹ K⁻¹). For water c ≈ 4200 J kg⁻¹ K⁻¹. The **heat capacity** of a body is C = mc (J K⁻¹).\n\n"
    "The **specific latent heat** L is the energy needed to change the state of 1 kg without a change of temperature: **Q = mL**. For ice L_f ≈ 3.34 × 10⁵ J kg⁻¹ (fusion) and for water L_v ≈ 2.26 × 10⁶ J kg⁻¹ (vaporisation)."),
   ("methode", "Method",
    "1. Split the process into stages: heating a solid, melting, heating the liquid, boiling, heating the vapour.\n2. Use mcΔθ for the stages with a temperature change and mL for the changes of state.\n3. Add the energies. For mixing: heat lost by the hot body = heat gained by the cold body (no losses).\n4. For electrical heating: Pt = mcΔθ (+ losses). Efficiency = useful energy ÷ energy supplied."),
   ("retenir", "Why the temperature is constant at a change of state",
    "During melting or boiling the energy supplied increases the **potential energy** of the molecules (their separation) and not their mean kinetic energy, so the temperature does not rise. Latent heat of vaporisation is much larger than that of fusion because the molecules must be separated completely."),
   ("pieges", "Common mistakes",
    "- Forgetting the latent heat stage (heating ice at 0 °C to water at 20 °C also needs the energy to melt it).\n- Using Δθ in kelvin and in °C inconsistently: a **change** of 1 K = a change of 1 °C, but absolute temperatures differ by 273.\n- Mixing J and kJ, or g and kg (c is per kilogram).\n- In mixing problems, forgetting that the final temperature lies between the two initial ones.")],
  [("Example 1 — Electric kettle", "A 3.0 kW kettle heats 2.0 kg of water from 25 °C to 100 °C (c = 4200 J kg⁻¹ K⁻¹). Find the energy and the time, assuming no losses.",
    ["Q = mcΔθ = 2.0 × 4200 × 75 = 6.3 × 10⁵ J.", "t = Q/P = 6.3 × 10⁵ ÷ 3000 = 210 s (3.5 minutes)."], "6.3 × 10⁵ J, 210 s"),
   ("Example 2 — Ice to warm water", "How much energy is needed to change 0.20 kg of ice at 0 °C into water at 20 °C? (L_f = 3.34 × 10⁵ J kg⁻¹)",
    ["Melting: Q₁ = mL = 0.20 × 3.34 × 10⁵ = 6.68 × 10⁴ J.", "Warming the water: Q₂ = 0.20 × 4200 × 20 = 1.68 × 10⁴ J.", "Total = 6.68 × 10⁴ + 1.68 × 10⁴ = 8.36 × 10⁴ J."], "8.36 × 10⁴ J")],
  [N("How much energy is needed to heat 0.50 kg of water by 40 K (J)?", 0.50 * cw * 40, "Q = mcΔθ = 0.50 × 4200 × 40 = 84 000 J.", 10, "J"),
   N("How much energy melts 0.30 kg of ice at 0 °C (J; L_f = 3.34 × 10⁵ J kg⁻¹)?", sg(0.30 * Lf, 3), "Q = mL = 0.30 × 3.34 × 10⁵ = 1.00 × 10⁵ J.", 500, "J"),
   TF("True or false: the temperature of boiling water rises while it is boiling if more heat is supplied.", False, "The energy goes into separating molecules (latent heat), so the temperature stays constant at the boiling point.")],
  [N("0.30 kg of water at 80 °C is mixed with 0.50 kg of water at 20 °C in an insulated container. Find the final temperature (°C).", th_mix, "0.30c(80 − θ) = 0.50c(θ − 20), so 24 − 0.3θ = 0.5θ − 10 and θ = 34/0.8 = 42.5 °C.", 0.1, "°C", difficulty=2),
   N("A 60 W heater warms a 0.50 kg block of metal by 12 K in 100 s with no losses. Find c of the metal (J kg⁻¹ K⁻¹).", 60 * 100 / (0.50 * 12), "c = Pt/(mΔθ) = 6000/(0.50 × 12) = 1000 J kg⁻¹ K⁻¹.", 5, "J/(kg K)")],
  P("A 1.5 kW electric kettle contains 1.0 kg of water at 20 °C (c = 4200 J kg⁻¹ K⁻¹, L_v = 2.26 × 10⁶ J kg⁻¹).",
    [pn("Energy to bring the water to 100 °C (kJ).", Q_boil / 1000, "Q = 1.0 × 4200 × 80 = 336 000 J = 336 kJ.", 1, "kJ", 1),
     pn("Time to reach 100 °C if 20 % of the energy is lost (s).", t_b8, "Useful power = 0.80 × 1500 = 1200 W; t = 336 000/1200 = 280 s.", 1, "s", 2),
     pn("Extra energy to boil away 0.10 kg of water at 100 °C (kJ).", 0.10 * Lv / 1000, "Q = mL = 0.10 × 2.26 × 10⁶ = 2.26 × 10⁵ J = 226 kJ.", 1, "kJ", 1),
     pm("The extra energy in the last part increases", "the potential energy of the water molecules", ["the temperature of the water", "the mass of water", "the mean kinetic energy of the molecules"], "At constant temperature only the potential energy of the molecules changes.", 1)]),
  [M("The SI unit of specific heat capacity is", "J kg⁻¹ K⁻¹", ["J kg⁻¹", "J K⁻¹", "J kg K"], "c = Q/(mΔθ)."),
   M("Which has the larger latent heat for water?", "vaporisation", ["fusion", "they are equal", "cannot be compared"], "2.26 × 10⁶ J kg⁻¹ is about seven times 3.34 × 10⁵ J kg⁻¹."),
   M("Water of mass m and c = 4200 gains 8400 J. Its temperature rise is 2 K when m is", "1.0 kg", ["0.5 kg", "2.0 kg", "4.2 kg"], "Δθ = 8400/(1.0 × 4200) = 2 K."),
   M("A cold drink at 0 °C in a warm room warms up. The heat flows", "from the room to the drink", ["from the drink to the room", "in neither direction", "only by radiation"], "Heat flows from the hotter to the colder body."),
   M("During melting, the energy supplied is used to", "increase the potential energy of molecules", ["raise the temperature", "increase the kinetic energy of molecules", "reduce the mass"], "Bonds are loosened: potential energy rises, temperature is constant.")],
  (heat_fig(), "Heating curve of ice to steam (schematic, not to scale).", "Temperature against energy supplied: ice warming, a flat melting stage at 0 degrees, water warming, a flat boiling stage at 100 degrees, then steam warming."),
  notes=["c = 4200 J kg⁻¹ K⁻¹, L_f = 3.34 × 10⁵ J kg⁻¹, L_v = 2.26 × 10⁶ J kg⁻¹ are textbook values; the board's data sheet may round differently (e.g. 4180, 3.3 × 10⁵)."], minutes=30, prereq=["lower6-physics-energy"])

# ---- 4.2 ideal gas, kinetic theory
R_, k_B, N_A = 8.31, 1.38e-23, 6.02e23
n1 = 2.0e5 * 25e-3 / (R_ * 300); assert abs(n1 - 2.006) < 0.01
M_N2 = 0.028; c_rms = math.sqrt(3 * R_ * 300 / M_N2); KEm = 1.5 * k_B * 300
assert abs(c_rms - 517) < 1 and abs(KEm - 6.21e-21) < 0.02e-21
M_He = 0.004; c_he = math.sqrt(3 * R_ * 300 / M_He); assert abs(c_he / c_rms - math.sqrt(7)) < 1e-9
p_tyre = 2.2e5 * 330 / 300; assert abs(p_tyre - 2.42e5) < 1
p2_b = 1.0e5 * 4.0 / 1.0; p3_b = p2_b * 400 / 300; assert p2_b == 4.0e5 and abs(p3_b - 5.333e5) < 1e3
U1 = 1.5 * R_ * 300; assert abs(U1 - 3739.5) < 0.1
def iso_fig():
    return plot(1, 8, 0, 25, curves=[("24/x", "red", "higher T"), ("12/x", "blue", "lower T")], grid=2, xlabel="V", ylabel="p", w=380, h=250)
lesson(ch, "gases", "Ideal gases and kinetic theory",
  ["State the gas laws and use pV = nRT and pV = NkT with temperatures in kelvin",
   "State the assumptions of the kinetic theory and use p = ⅓ρ⟨c²⟩ to relate pressure to molecular motion",
   "Relate mean molecular kinetic energy to absolute temperature: ½m⟨c²⟩ = (3/2)kT"],
  [("definition", "The ideal gas",
    "An **ideal gas** obeys **pV = nRT** exactly (n in mol, T in K, R = 8.31 J mol⁻¹ K⁻¹); in terms of the number of molecules N: **pV = NkT** (k = 1.38 × 10⁻²³ J K⁻¹ = R/N_A, N_A = 6.02 × 10²³ mol⁻¹).\n\n"
    "Special cases: Boyle (T constant) pV = constant; pressure law (V constant) p/T = constant; Charles (p constant) V/T = constant. **T (K) = θ (°C) + 273**."),
   ("formule", "Kinetic theory of gases",
    "Assumptions: many identical molecules in random motion; negligible volume compared with the container; no intermolecular forces except during (elastic) collisions; collision time negligible.\n\n"
    "Result: **pV = ⅓Nm⟨c²⟩**, i.e. p = ⅓ρ⟨c²⟩. Comparing with pV = NkT: **½m⟨c²⟩ = (3/2)kT**. The r.m.s. speed is c_rms = √⟨c²⟩ = √(3RT/M) (M = molar mass in kg mol⁻¹).",
    r"pV=nRT\qquad pV=\frac{1}{3}Nm\langle c^{2}\rangle\qquad \frac{1}{2}m\langle c^{2}\rangle=\frac{3}{2}kT"),
   ("retenir", "Internal energy",
    "For an ideal gas the internal energy is the total kinetic energy of the molecules (no potential energy): for a monatomic gas **U = (3/2)nRT**. It depends only on temperature. A real gas behaves ideally at low pressure and high temperature."),
   ("pieges", "Common mistakes",
    "- Using °C instead of kelvin in gas-law calculations (always add 273).\n- Using g mol⁻¹ instead of kg mol⁻¹ for M in c_rms.\n- Using volume in dm³ or cm³ with R in SI: convert to m³ (1 dm³ = 10⁻³ m³).\n- Writing ⟨c²⟩ as (⟨c⟩)²: the mean of the squares is larger.")],
  [("Example 1 — Amount of gas", "A cylinder of volume 25 dm³ holds an ideal gas at 2.0 × 10⁵ Pa and 27 °C. Find the number of moles and molecules.",
    ["T = 27 + 273 = 300 K and V = 25 × 10⁻³ m³.", "n = pV/RT = 2.0 × 10⁵ × 25 × 10⁻³ ÷ (8.31 × 300) = 2.01 mol.", "N = nN_A = 2.01 × 6.02 × 10²³ = 1.21 × 10²⁴ molecules."], "n = 2.0 mol, N = 1.2 × 10²⁴ molecules"),
   ("Example 2 — Speed of molecules", "Find the r.m.s. speed of nitrogen molecules (M = 0.028 kg mol⁻¹) at 300 K and the mean kinetic energy of a molecule.",
    ["c_rms = √(3RT/M) = √(3 × 8.31 × 300 ÷ 0.028) = √(2.67 × 10⁵) = 517 m/s.", "Mean kinetic energy = (3/2)kT = 1.5 × 1.38 × 10⁻²³ × 300 = 6.21 × 10⁻²¹ J."], "517 m/s; 6.2 × 10⁻²¹ J")],
  [N("A gas at 27 °C has pressure 1.0 × 10⁵ Pa. At constant volume it is heated to 127 °C. Find the new pressure (10⁵ Pa, 3 s.f.).", sg(1.0 * 400 / 300, 3), "p₂ = p₁T₂/T₁ = 1.0 × 10⁵ × 400/300 = 1.33 × 10⁵ Pa.", 0.01, "× 10⁵ Pa"),
   N("Convert 27 °C to kelvin (K).", 300, "T = 27 + 273 = 300 K.", 0, "K"),
   TF("True or false: at the same temperature, hydrogen and oxygen molecules have the same mean kinetic energy.", True, "Mean KE = (3/2)kT depends only on temperature; the lighter hydrogen molecules simply move faster.")],
  [N("A gas of volume 4.0 dm³ at 1.0 × 10⁵ Pa is compressed isothermally to 1.0 dm³. Find the final pressure (10⁵ Pa).", p2_b / 1e5, "Boyle: p₂ = p₁V₁/V₂ = 1.0 × 10⁵ × 4.0/1.0 = 4.0 × 10⁵ Pa.", 0.01, "× 10⁵ Pa", difficulty=2),
   N("Find the total internal energy of 2.0 mol of a monatomic ideal gas at 300 K (J, 3 s.f.).", sg(1.5 * 2.0 * R_ * 300, 3), "U = (3/2)nRT = 1.5 × 2.0 × 8.31 × 300 = 7480 J.", 10, "J")],
  P("A car tyre contains air at an absolute pressure of 2.2 × 10⁵ Pa at 27 °C. After a long journey the air in the tyre is at 57 °C. Assume the volume is constant.",
    [pn("Absolute temperature at the start (K).", 300, "27 + 273 = 300 K.", 0, "K", 1),
     pn("Final pressure (10⁵ Pa, 3 s.f.).", sg(p_tyre / 1e5, 3), "p₂ = 2.2 × 10⁵ × 330/300 = 2.42 × 10⁵ Pa.", 0.01, "× 10⁵ Pa", 2),
     pm("The pressure increases because", "the molecules move faster and hit the walls harder and more often", ["the molecules get bigger", "there are more molecules", "the volume of the tyre decreases"], "Higher T means a larger mean speed, so a larger momentum change per collision and more collisions.", 1)]),
  [M("Which temperature is the same as 0 °C?", "273 K", ["0 K", "100 K", "373 K"], "T = θ + 273."),
   M("The kinetic theory assumes that molecules", "exert no forces on each other except in collisions", ["attract strongly", "have large volume", "never collide"], "This is the ideal-gas assumption."),
   M("The mean kinetic energy of a gas molecule is proportional to", "the absolute temperature", ["the pressure", "the volume", "the molar mass"], "½m⟨c²⟩ = (3/2)kT."),
   M("The temperature of a gas is doubled from 300 K to 600 K. The r.m.s. speed is multiplied by", "√2", ["2", "4", "1/2"], "c_rms ∝ √T."),
   M("The product pV for a fixed mass of ideal gas at constant temperature is", "constant", ["proportional to T²", "inversely proportional to p", "zero"], "Boyle's law.")],
  (iso_fig(), "Isotherms of an ideal gas: pV = constant at each temperature.", "Two hyperbola-shaped curves of pressure against volume, the upper one for a higher temperature."),
  notes=["R = 8.31 J mol⁻¹ K⁻¹, k = 1.38 × 10⁻²³ J K⁻¹, N_A = 6.02 × 10²³ mol⁻¹ and T = θ + 273 are used.", "The derivation of p = ⅓ρ⟨c²⟩ is stated, not derived; check whether the board expects the derivation.", "Real gases and Van der Waals are not included."], minutes=35, prereq=["lower6-physics-heat", "lower6-physics-momentum"])

# =============================================================== 5. OSCILLATIONS AND WAVES
ch = p.chapter("ch-waves", "Oscillations and waves", SYL % "oscillations and waves")
m_, k_ = 0.50, 200.0; w_ = math.sqrt(k_ / m_); T_s = 2 * math.pi / w_; A_ = 0.05
vmax = w_ * A_; amax = w_ ** 2 * A_; x_t = A_ * math.cos(w_ * 0.1); v_x = w_ * math.sqrt(A_ ** 2 - 0.03 ** 2); E_tot = 0.5 * k_ * A_ ** 2
assert w_ == 20 and abs(T_s - 0.3142) < 1e-4 and abs(vmax - 1.0) < 1e-12 and abs(amax - 20) < 1e-9 and abs(x_t + 0.0208) < 1e-4
assert abs(v_x - 0.80) < 1e-9 and abs(E_tot - 0.25) < 1e-12 and abs(0.5 * k_ * (A_ ** 2 - 0.03 ** 2) - 0.16) < 1e-12
T_p = 2 * math.pi * math.sqrt(0.80 / g); assert abs(T_p - 1.795) < 0.001
w_b = 2 * math.pi / 4.0; vb = w_b * 0.50; ab = w_b ** 2 * 0.50; xb = 0.5 * math.cos(w_b * 0.5)
assert abs(vb - 0.785) < 0.001 and abs(ab - 1.234) < 0.001 and abs(xb - 0.3536) < 0.001
def shm_fig():
    return plot(0, 2, -1.3, 1.3, curves=[("cos(2*pi*x)", "blue", "x")], points=[(0, 1, "A", "red"), (0.5, -1, "−A", "red")],
                segments=[(0, -1.15, 1, -1.15, "green", False, "T")], grid=1, xlabel="t / T", ylabel="x / A", w=400, h=250)
lesson(ch, "shm", "Simple harmonic motion",
  ["Define SHM (a = −ω²x) and use x = A cos ωt, v = ±ω√(A² − x²), v_max = ωA and a_max = ω²A",
   "Use T = 2π√(m/k) for a mass on a spring and T = 2π√(l/g) for a simple pendulum",
   "Describe the energy changes in SHM, damping and resonance"],
  [("definition", "Definition of SHM",
    "A body performs **simple harmonic motion** when its acceleration is proportional to its displacement from the equilibrium position and always directed towards it: **a = −ω²x**.\n\n"
    "Solution: x = A cos ωt (if released from x = A at t = 0), with amplitude A, angular frequency ω = 2π/T = 2πf. Then v = −ωA sin ωt and **v = ±ω√(A² − x²)**."),
   ("formule", "Maxima and periods",
    "- Maximum speed (at equilibrium): v_max = ωA\n- Maximum acceleration (at the extremes): a_max = ω²A\n- Mass on a spring: ω² = k/m, so **T = 2π√(m/k)**\n- Simple pendulum (small angles, below about 10°): **T = 2π√(l/g)**\n\nThe period does not depend on the amplitude (for these two cases) or on the mass of the pendulum bob.",
    r"a=-\omega^{2}x\qquad T=2\pi\sqrt{\frac{m}{k}}\qquad T=2\pi\sqrt{\frac{l}{g}}"),
   ("retenir", "Energy, damping and resonance",
    "Total energy E = ½mω²A² = ½kA² is constant (no damping); it changes between kinetic E_k = ½mω²(A² − x²) and potential ½mω²x². Kinetic energy is maximum at the centre; potential energy at the ends.\n\n"
    "**Damping** (friction, air) removes energy and the amplitude decays: light, critical (fastest return without oscillation) and heavy. **Resonance**: when a driving frequency equals the natural frequency the amplitude becomes large."),
   ("pieges", "Common mistakes",
    "- Using the pendulum formula for a large amplitude.\n- Thinking acceleration is greatest at the centre: it is zero there (the speed is greatest).\n- Setting the calculator in degrees for cos ωt: ωt is in **radians**.\n- Forgetting that a = −ω²x has a minus sign: the acceleration is opposite to the displacement.")],
  [("Example 1 — Mass on a spring", "A 0.50 kg mass on a spring of k = 200 N/m oscillates with amplitude 5.0 cm. Find ω, T, the maximum speed and the maximum acceleration.",
    ["ω = √(k/m) = √(200/0.50) = 20 rad/s; T = 2π/ω = 0.314 s.", "v_max = ωA = 20 × 0.050 = 1.0 m/s.", "a_max = ω²A = 400 × 0.050 = 20 m/s²."], "ω = 20 rad/s, T = 0.31 s, v_max = 1.0 m/s, a_max = 20 m/s²"),
   ("Example 2 — Speed and energy at a point", "For the same oscillator, find the speed and the kinetic energy when the displacement is 3.0 cm.",
    ["v = ω√(A² − x²) = 20 × √(0.050² − 0.030²) = 20 × 0.040 = 0.80 m/s.", "E_k = ½mv² = ½ × 0.50 × 0.80² = 0.16 J. Total energy ½kA² = 0.25 J, so E_p = 0.09 J."], "v = 0.80 m/s, E_k = 0.16 J")],
  [N("Find the period of a simple pendulum of length 0.80 m (g = 9.8; seconds, 3 s.f.).", sg(T_p, 3), "T = 2π√(l/g) = 2π√(0.80/9.8) = 1.80 s.", 0.01, "s"),
   N("An oscillator has frequency 5.0 Hz. Find ω (rad/s, 3 s.f.).", sg(2 * math.pi * 5, 3), "ω = 2πf = 31.4 rad/s.", 0.05, "rad/s"),
   TF("True or false: the acceleration of a body in SHM is greatest when it passes through the equilibrium position.", False, "At equilibrium x = 0 so a = 0; the acceleration is largest at the extremes.")],
  [N("A mass oscillates with amplitude 0.10 m and period 2.0 s. Find its maximum speed (m/s, 3 s.f.).", sg(2 * math.pi / 2.0 * 0.10, 3), "ω = 2π/2.0 = 3.14 rad/s; v_max = ωA = 0.314 m/s.", 0.005, "m/s", difficulty=2),
   N("To double the period of a pendulum, by what factor must the length be multiplied?", 4, "T ∝ √l, so T doubles when l is multiplied by 4.", 0, None)],
  P("A buoy in the sea off Limbe bobs vertically in SHM with period 4.0 s and amplitude 0.50 m. Take x = A cos ωt.",
    [pn("Angular frequency (rad/s, 3 s.f.).", sg(w_b, 3), "ω = 2π/4.0 = 1.57 rad/s.", 0.01, "rad/s", 1),
     pn("Maximum speed (m/s, 3 s.f.).", sg(vb, 3), "v_max = ωA = 1.571 × 0.50 = 0.785 m/s.", 0.005, "m/s", 1),
     pn("Maximum acceleration (m/s², 3 s.f.).", sg(ab, 3), "a_max = ω²A = 2.467 × 0.50 = 1.23 m/s².", 0.01, "m/s²", 1),
     pn("Displacement at t = 0.50 s (m, 3 s.f.).", sg(xb, 3), "x = 0.50 cos(1.571 × 0.50) = 0.50 cos(π/4) = 0.354 m.", 0.005, "m", 2)]),
  [M("In SHM the total energy is", "constant if there is no damping", ["maximum at the ends only", "zero at the centre", "proportional to x"], "Energy shifts between kinetic and potential but the sum is fixed."),
   M("The period of a mass–spring system is doubled when the mass is multiplied by", "4", ["2", "8", "16"], "T ∝ √m."),
   M("Resonance occurs when", "the driving frequency equals the natural frequency", ["damping is greatest", "the amplitude is zero", "the mass is doubled"], "Energy transfer is then most efficient."),
   M("At the equilibrium position of an oscillating mass, which is true?", "speed is maximum, acceleration is zero", ["speed zero, acceleration maximum", "both are zero", "both are maximum"], "x = 0 gives a = 0 and the greatest kinetic energy."),
   M("A pendulum of length 1.0 m has T = 2.0 s (g ≈ 9.8). Its frequency is", "0.50 Hz", ["2.0 Hz", "0.20 Hz", "4.0 Hz"], "f = 1/T = 0.50 Hz.")],
  (shm_fig(), "Displacement–time graph of SHM starting at the maximum: x = A cos ωt.", "A cosine curve over two periods with its maximum amplitude A and the period T marked."),
  notes=["Derivation of T = 2π√(m/k) from a = −ω²x is shown only in outline.", "Damping types are described qualitatively; check how much the syllabus expects."], minutes=35, prereq=["lower6-physics-circular", "lower6-physics-energy"])

# ---- 5.2 waves, superposition, interference
lam = 6.0e-7; a_ = 0.50e-3; D_ = 2.0; w_f = lam * D_ / a_; assert abs(w_f - 2.4e-3) < 1e-9
lam_s = 340 / 170; dpath = 5.0 - 4.0; assert lam_s == 2.0 and dpath / lam_s == 0.5
phase = 2 * math.pi * 0.5 / lam_s; assert abs(phase - math.pi / 2) < 1e-9
lam_r = 3.0e8 / 100e6; assert abs(lam_r - 3.0) < 1e-9
def yds_fig():
    it = [CIRCLE(40, 120, 5, fill="orange"), T(40, 145, "source", 12), LINE(120, 20, 120, 100), LINE(120, 140, 120, 220), LINE(120, 100, 120, 140, color="grey", width=1, dash=True),
          T(100, 122, "a", 13, anchor="end"), LINE(110, 100, 110, 140, color="purple", width=1),
          LINE(330, 20, 330, 220, width=3), LINE(120, 100, 330, 120, dash=True, color="grey", width=1), LINE(120, 140, 330, 120, dash=True, color="grey", width=1),
          LINE(120, 232, 330, 232, color="blue", width=2, arrow="both"), T(225, 224, "D", 13, color="blue"),
          T(345, 70, "bright", 12, anchor="start"), T(345, 124, "bright", 12, anchor="start"), T(345, 178, "bright", 12, anchor="start"), LINE(338, 120, 338, 178, color="red", width=2, arrow="both"), T(160, 80, "double slit", 12, anchor="start")]
    return shapes(it, 420, 250)
lesson(ch, "waves", "Progressive waves, superposition and interference",
  ["Use v = fλ and relate phase difference to path difference; distinguish transverse and longitudinal waves",
   "State the principle of superposition and the conditions for constructive and destructive interference",
   "Use Young's double-slit formula λ = ax/D and describe diffraction at a gap"],
  [("definition", "Waves",
    "A **progressive wave** transfers energy without transferring matter. **Transverse**: vibration ⟂ direction of travel (light, water ripples, waves on a string). **Longitudinal**: vibration ∥ direction of travel (sound; compressions and rarefactions). Only transverse waves can be **polarised**.\n\n"
    "**v = fλ**. A path difference Δx gives a phase difference **Δφ = 2πΔx/λ**. The intensity of a wave is proportional to the (amplitude)²."),
   ("formule", "Superposition and interference",
    "**Principle of superposition**: when two or more waves meet, the resultant displacement is the vector sum of the individual displacements.\n\n"
    "For two **coherent** sources (constant phase difference, same frequency): constructive interference when the path difference is **nλ** (n = 0, 1, 2…); destructive when it is **(n + ½)λ**.",
    r"\Delta x=n\lambda\ \text{(bright)}\qquad \Delta x=\left(n+\frac{1}{2}\right)\lambda\ \text{(dark)}"),
   ("formule", "Young's double slit and diffraction",
    "Two narrow slits a apart, screen at distance D (D ≫ a): fringe separation **w = λD/a** (fringes are equally spaced). Laser light and slit separations of a fraction of a millimetre give visible fringes.\n\n"
    "**Diffraction**: waves spread when passing through a gap or round an obstacle; the effect is greatest when the gap width is comparable to the wavelength. Single-slit diffraction gives a wide central maximum.",
    r"w=\frac{\lambda D}{a}"),
   ("pieges", "Common mistakes",
    "- Forgetting to convert nm to m (1 nm = 10⁻⁹ m) or mm to m.\n- Using the slit-to-screen distance in cm with a in mm without converting.\n- Thinking two torch beams interfere: they are not coherent.\n- Saying light waves are longitudinal: light is transverse (polarisation proves it).")],
  [("Example 1 — Fringe separation", "Light of wavelength 600 nm passes through two slits 0.50 mm apart onto a screen 2.0 m away. Find the fringe separation.",
    ["λ = 6.0 × 10⁻⁷ m, a = 5.0 × 10⁻⁴ m, D = 2.0 m.", "w = λD/a = 6.0 × 10⁻⁷ × 2.0 ÷ 5.0 × 10⁻⁴ = 2.4 × 10⁻³ m = 2.4 mm."], "w = 2.4 mm"),
   ("Example 2 — Two loudspeakers", "Two in-phase loudspeakers emit a note of frequency 170 Hz (speed of sound 340 m/s). A listener is 4.0 m from one and 5.0 m from the other. What does she hear?",
    ["λ = v/f = 340/170 = 2.0 m.", "Path difference = 5.0 − 4.0 = 1.0 m = λ/2 (half a wavelength).", "The waves arrive in antiphase: destructive interference, so a quiet spot (minimum)."], "A minimum of loudness (destructive interference)")],
  [N("A radio station broadcasts at 100 MHz (c = 3.0 × 10⁸ m/s). Find the wavelength (m).", lam_r, "λ = c/f = 3.0 × 10⁸ ÷ 1.0 × 10⁸ = 3.0 m.", 0.01, "m"),
   N("Water waves have frequency 2.0 Hz and wavelength 0.15 m. Find their speed (m/s).", 0.30, "v = fλ = 2.0 × 0.15 = 0.30 m/s.", 0.005, "m/s"),
   TF("True or false: sound waves in air are transverse.", False, "Sound is longitudinal (compressions and rarefactions).")],
  [N("Light of wavelength 5.0 × 10⁻⁷ m passes through slits 0.40 mm apart; the screen is 1.6 m away. Find the fringe separation (mm).", 5.0e-7 * 1.6 / 0.40e-3 * 1000, "w = λD/a = 5.0 × 10⁻⁷ × 1.6 ÷ 4.0 × 10⁻⁴ = 2.0 × 10⁻³ m = 2.0 mm.", 0.02, "mm", difficulty=2),
   N("Find the phase difference (in radians, 3 s.f.) corresponding to a path difference of 0.50 m for a wave of wavelength 2.0 m.", sg(phase, 3), "Δφ = 2πΔx/λ = 2π × 0.50/2.0 = π/2 = 1.57 rad.", 0.01, "rad")],
  P("In a Young's slits experiment the fringe separation is 3.0 mm when the screen is 1.5 m from slits 0.30 mm apart.",
    [pn("Find the wavelength of the light (nm).", 3.0e-3 * 0.30e-3 / 1.5 * 1e9, "λ = wa/D = 3.0 × 10⁻³ × 3.0 × 10⁻⁴ ÷ 1.5 = 6.0 × 10⁻⁷ m = 600 nm.", 1, "nm", 2),
     pn("What would the fringe separation become if the screen is moved to 3.0 m (mm)?", 6.0, "w ∝ D, so doubling D doubles w: 6.0 mm.", 0.05, "mm", 1),
     pm("Using white light instead of single-colour light, the central fringe is", "white, with coloured fringes on either side", ["completely dark", "red only", "the same as with one colour"], "At the centre all wavelengths have zero path difference, so all add; the other fringes spread into colours because w ∝ λ.", 1)]),
  [M("Constructive interference occurs when the path difference is", "a whole number of wavelengths", ["an odd number of half wavelengths", "zero always", "a quarter wavelength"], "Waves arrive in phase when Δx = nλ."),
   M("Diffraction is most noticeable when the gap is", "of the same order as the wavelength", ["much wider than the wavelength", "much narrower than anything", "exactly 1 mm"], "Then the spreading is largest."),
   M("Which wave can be polarised?", "light", ["sound in air", "a slinky compression wave", "none"], "Only transverse waves can."),
   M("Two coherent sources have", "a constant phase difference", ["different frequencies", "random phase", "equal amplitudes only"], "That is what makes a stable interference pattern."),
   M("If the slit separation is halved, the fringe separation", "doubles", ["halves", "stays the same", "quadruples"], "w ∝ 1/a.")],
  (yds_fig(), "Young's double-slit arrangement (not to scale): bright fringes of separation w on the screen.", "A source, a barrier with two slits a distance a apart, a screen at distance D and bright fringes marked on the screen."),
  notes=["Diffraction grating and single-slit minimum formula are placed in the Upper Sixth pack; check whether the syllabus puts them here.", "The approximation D ≫ a is assumed."], minutes=35, prereq=["lower6-physics-shm"])

# ---- 5.3 standing waves
v_str = math.sqrt(40 / 1.0e-3); f1s = v_str / (2 * 0.65); assert abs(v_str - 200) < 1e-9 and abs(f1s - 153.8) < 0.1
f1c = 340 / (4 * 0.34); f1o = 340 / (2 * 0.34); assert abs(f1c - 250) < 1e-9 and abs(f1o - 500) < 1e-9
def wave_poly(x0, x1, y0, amp, half_waves, n=40):
    pts = []
    for i in range(n + 1):
        t = i / n
        pts += [x0 + (x1 - x0) * t, y0 - amp * math.sin(math.pi * half_waves * t)]
    return POLY([round(v, 1) for v in pts], stroke="blue", width=2, closed=False)
def standing_fig():
    it = []
    for i, (y, lab) in enumerate([(50, "n = 1:  L = λ/2"), (125, "n = 2:  L = λ"), (200, "n = 3:  L = 3λ/2")]):
        it += [wave_poly(60, 260, y, 22, i + 1), wave_poly(60, 260, y, -22, i + 1), CIRCLE(60, y, 4, fill="ink"), CIRCLE(260, y, 4, fill="ink"), T(275, y + 5, lab, 12, anchor="start")]
    return shapes(it, 400, 250)
lesson(ch, "standing", "Standing waves on strings and in pipes",
  ["Explain how a standing wave forms and describe nodes, antinodes and the harmonics",
   "Use f = nv/2L for a string, and the pipe formulae for open and closed pipes",
   "Use v = √(T/μ) for the speed of waves on a stretched string"],
  [("definition", "Formation of standing waves",
    "A **standing (stationary) wave** forms when two progressive waves of the same frequency and amplitude travelling in opposite directions superpose (typically a wave and its reflection). It has **nodes** (zero amplitude) and **antinodes** (maximum amplitude), a distance λ/2 apart. Energy is not transferred along the wave.\n\n"
    "Between two adjacent nodes all points vibrate in phase; points on either side of a node vibrate in antiphase."),
   ("formule", "Strings and pipes",
    "**String fixed at both ends**: a node at each end, so L = nλ/2 and **f_n = nv/2L** (n = 1, 2, 3…). Wave speed on the string: **v = √(T/μ)** (T = tension, μ = mass per unit length).\n\n"
    "**Pipe open at both ends** (antinode at each end): f_n = nv/2L (all harmonics). **Pipe closed at one end** (node at closed end, antinode at the open end): **f = (2n − 1)v/4L**: odd harmonics only; the fundamental is v/4L.",
    r"f_n=\frac{nv}{2L}\qquad v=\sqrt{\frac{T}{\mu}}\qquad f=\frac{(2n-1)v}{4L}"),
   ("retenir", "Practical notes",
    "In a real pipe the antinode forms slightly outside the open end (**end correction**), so the effective length is a little longer than the pipe. A stretched string produces higher notes with higher tension, shorter length or smaller mass per unit length. Resonance tubes can be used to measure the speed of sound."),
   ("pieges", "Common mistakes",
    "- Treating the closed end of a pipe as an antinode (it is a node).\n- Using f = nv/2L for a closed pipe.\n- Confusing the distance between adjacent nodes (λ/2) with the wavelength.\n- Using μ in g/m: convert to kg/m.")],
  [("Example 1 — A guitar string", "A string 0.65 m long has mass per unit length 1.0 × 10⁻³ kg/m and tension 40 N. Find the wave speed and the fundamental frequency.",
    ["v = √(T/μ) = √(40/1.0 × 10⁻³) = √(4.0 × 10⁴) = 200 m/s.", "f₁ = v/2L = 200 ÷ (2 × 0.65) = 154 Hz; the next harmonics are 308 Hz and 462 Hz."], "v = 200 m/s; f₁ = 154 Hz"),
   ("Example 2 — Pipes", "A pipe 0.34 m long (speed of sound 340 m/s) is open at both ends, then closed at one end. Find the fundamental frequency in each case.",
    ["Open pipe: f₁ = v/2L = 340 ÷ 0.68 = 500 Hz.", "Closed pipe: f₁ = v/4L = 340 ÷ 1.36 = 250 Hz. The next resonance is 3f₁ = 750 Hz."], "Open: 500 Hz; closed: 250 Hz")],
  [N("The distance between two adjacent nodes in a standing wave is 0.20 m and the frequency is 850 Hz. Find the wave speed (m/s).", 340, "λ = 2 × 0.20 = 0.40 m; v = fλ = 850 × 0.40 = 340 m/s.", 1, "m/s"),
   N("A string of length 0.80 m vibrates in its fundamental mode at 200 Hz. Find the speed of waves on it (m/s).", 2 * 0.80 * 200, "v = 2Lf₁ = 2 × 0.80 × 200 = 320 m/s.", 0.5, "m/s"),
   TF("True or false: a pipe closed at one end can resonate at 2f₁ (twice its fundamental).", False, "A closed pipe supports only odd harmonics: f₁, 3f₁, 5f₁…")],
  [N("The tension in a string is quadrupled (same string). By what factor does the wave speed change?", 2, "v ∝ √T, so multiplying T by 4 multiplies v by 2.", 0, None, difficulty=2),
   N("An open pipe has fundamental 425 Hz; speed of sound is 340 m/s. Find its length (m).", 340 / (2 * 425), "L = v/2f = 340/850 = 0.40 m.", 0.005, "m")],
  P("A closed pipe is 0.85 m long. Take the speed of sound as 340 m/s and ignore end correction.",
    [pn("Fundamental frequency (Hz).", 340 / (4 * 0.85), "f₁ = v/4L = 340/3.4 = 100 Hz.", 0.5, "Hz", 1),
     pn("Frequency of the second resonance (Hz).", 300, "Next resonance is the third harmonic: 3 × 100 = 300 Hz.", 0.5, "Hz", 2),
     pm("At the closed end of the pipe there is a", "displacement node", ["displacement antinode", "pressure node only", "maximum amplitude of air movement"], "Air cannot move at a closed end.", 1)]),
  [M("In a standing wave, adjacent nodes are separated by", "λ/2", ["λ", "λ/4", "2λ"], "Nodes occur every half wavelength."),
   M("A standing wave transfers", "no net energy along the medium", ["energy at speed v", "momentum only", "mass"], "The two travelling waves carry equal energy in opposite directions."),
   M("A string fixed at both ends supports frequencies", "f₁, 2f₁, 3f₁…", ["f₁, 3f₁, 5f₁…", "only f₁", "f₁, 4f₁, 9f₁…"], "f_n = nv/2L."),
   M("Which quantity is the SI unit of μ in v = √(T/μ)?", "kg m⁻¹", ["kg m", "N m⁻¹", "kg"], "μ is mass per unit length."),
   M("A closed pipe of length L resonates at its fundamental when its length is", "λ/4", ["λ/2", "λ", "3λ/4"], "Node at the closed end and antinode at the open end: a quarter wavelength.")],
  (standing_fig(), "The first three harmonics of a string fixed at both ends (the envelope of the vibration).", "Three stacked drawings of a string fixed at both ends showing one, two and three half wavelengths."),
  notes=["End correction is mentioned without a formula; check the syllabus for its use.", "The speed of sound in air is taken as 340 m/s."], minutes=30, prereq=["lower6-physics-waves"])

# =============================================================== 6. DC ELECTRICITY
ch = p.chapter("ch-electricity", "Direct-current electricity", SYL % "current electricity")
e_ch = 1.6e-19
rho_cu = 1.7e-8; A_cu = math.pi * (0.25e-3) ** 2; R_cu = rho_cu * 3.0 / A_cu; assert abs(R_cu - 0.2597) < 0.001
n_cu = 8.5e28; v_dr = 2.0 / (n_cu * 1.0e-6 * e_ch); assert abs(v_dr - 1.47e-4) < 0.01e-4
Ik = 2200 / 220; Rk = 220 / Ik; assert Ik == 10 and Rk == 22
Q_ = 0.5 * 120; Ne = Q_ / e_ch; assert Q_ == 60
def iv_fig():
    return plot(-4, 4, -3, 3, curves=[("0.3*x", "blue", "resistor"), ("2*x/(1+abs(x))", "red", "lamp"), ("0.01*(exp(2.2*x)-1)", "green", "diode")], grid=2, xlabel="V", ylabel="I", w=400, h=260)
lesson(ch, "resistance", "Current, potential difference, resistance and resistivity",
  ["Define current, potential difference, resistance and resistivity and use I = Q/t, V = W/Q, R = V/I, R = ρL/A, P = IV",
   "Describe the I–V characteristics of a metallic conductor, a filament lamp and a diode",
   "Use I = nAvq to relate current to the drift velocity of charge carriers"],
  [("definition", "Basic definitions",
    "**Current** I = Q/t (ampere, A = C s⁻¹). **Potential difference** V = W/Q (volt, V = J C⁻¹): energy transferred per unit charge between two points. **Resistance** R = V/I (ohm, Ω = V A⁻¹).\n\n"
    "**Ohm's law**: for a metallic conductor at constant temperature, I ∝ V. **Power**: P = IV = I²R = V²/R. Energy W = IVt."),
   ("formule", "Resistivity and drift velocity",
    "For a uniform wire: **R = ρL/A**, where ρ is the **resistivity** (Ω m), a property of the material at a given temperature (copper ≈ 1.7 × 10⁻⁸ Ω m).\n\n"
    "Charge carriers drift slowly: **I = nAvq**, where n is the number of free charge carriers per unit volume (m⁻³), A the cross-sectional area, v the mean drift velocity and q the charge on each carrier (e = 1.6 × 10⁻¹⁹ C for electrons).",
    r"R=\frac{\rho L}{A}\qquad I=nAvq\qquad P=IV=I^{2}R"),
   ("retenir", "I–V characteristics",
    "- **Metallic conductor (constant temperature)**: straight line through the origin (ohmic).\n- **Filament lamp**: the curve flattens as I increases; resistance rises with temperature because the lattice ions vibrate more and impede the electrons.\n- **Diode**: conducts in one direction (forward) once the threshold voltage (about 0.6 V for silicon) is reached; almost no current in reverse."),
   ("pieges", "Common mistakes",
    "- Using the diameter instead of the radius in A = πr².\n- Forgetting to convert mm to m (0.50 mm = 0.50 × 10⁻³ m).\n- Saying 'a filament lamp does not obey V = IR': V = IR is the **definition** of resistance and always holds; only Ohm's law (constant R) fails.\n- Thinking electrons travel at the speed of the signal: the drift speed is a fraction of a millimetre per second.")],
  [("Example 1 — Resistance of a wire", "A copper wire is 3.0 m long and has diameter 0.50 mm (ρ = 1.7 × 10⁻⁸ Ω m). Find its resistance.",
    ["Radius = 0.25 × 10⁻³ m, so A = π(0.25 × 10⁻³)² = 1.96 × 10⁻⁷ m².", "R = ρL/A = 1.7 × 10⁻⁸ × 3.0 ÷ 1.96 × 10⁻⁷ = 0.26 Ω."], "R = 0.26 Ω"),
   ("Example 2 — Drift velocity", "A current of 2.0 A flows in a copper wire of cross-section 1.0 × 10⁻⁶ m². Copper has 8.5 × 10²⁸ free electrons per m³. Find the drift speed.",
    ["I = nAvq, so v = I/(nAq).", "v = 2.0 ÷ (8.5 × 10²⁸ × 1.0 × 10⁻⁶ × 1.6 × 10⁻¹⁹) = 1.47 × 10⁻⁴ m/s, about 0.15 mm/s."], "v = 1.5 × 10⁻⁴ m/s")],
  [N("A current of 0.50 A flows for 2.0 minutes. How much charge passes (C)?", Q_, "Q = It = 0.50 × 120 = 60 C.", 0.1, "C"),
   N("A 2.2 kW kettle runs on 220 V. Find the current (A).", Ik, "I = P/V = 2200/220 = 10 A.", 0.05, "A"),
   TF("True or false: a filament lamp obeys Ohm's law.", False, "Its resistance increases with temperature, so I is not proportional to V (although V = IR still defines R at each point).")],
  [N("A resistor of resistance 22 Ω carries 10 A. Find the energy dissipated in 30 minutes (kW h, 3 s.f.).", sg(10 ** 2 * 22 * 1800 / 3.6e6, 3), "P = I²R = 2200 W = 2.2 kW; energy = 2.2 × 0.5 h = 1.10 kW h.", 0.01, "kW h", difficulty=2),
   N("A wire of resistance 4.0 Ω is stretched to twice its length (same volume). Find the new resistance (Ω).", 16.0, "L doubles and A halves, so R = ρL/A is multiplied by 4: 16 Ω.", 0.1, "Ω")],
  P("A heating element is made from nichrome wire of resistivity 1.1 × 10⁻⁶ Ω m, length 1.5 m and cross-sectional area 2.0 × 10⁻⁷ m². It is connected to a 220 V supply.",
    [pn("Find the resistance of the wire (Ω, 3 s.f.).", sg(1.1e-6 * 1.5 / 2.0e-7, 3), "R = ρL/A = 1.1 × 10⁻⁶ × 1.5 ÷ 2.0 × 10⁻⁷ = 8.25 Ω.", 0.05, "Ω", 1),
     pn("Find the current (A, 3 s.f.).", sg(220 / 8.25, 3), "I = V/R = 220/8.25 = 26.7 A.", 0.1, "A", 1),
     pn("Find the power dissipated (kW, 3 s.f.).", sg(220 ** 2 / 8.25 / 1000, 3), "P = V²/R = 48400/8.25 = 5.87 kW.", 0.05, "kW", 2),
     pm("If the wire is replaced by one of half the length, the power", "doubles", ["halves", "stays the same", "quadruples"], "R halves, so P = V²/R doubles.", 1)]),
  [M("The unit of resistivity is", "Ω m", ["Ω", "Ω m⁻¹", "Ω m²"], "ρ = RA/L."),
   M("One volt equals", "one joule per coulomb", ["one watt per second", "one ampere per ohm", "one coulomb per joule"], "V = W/Q."),
   M("Which component has an I–V graph that is not symmetric about the origin?", "diode", ["resistor", "filament lamp", "a metal wire"], "A diode conducts in one direction only."),
   M("When the temperature of a metal conductor increases, its resistance", "increases", ["decreases", "is unchanged", "becomes zero"], "Lattice vibrations increase and impede electron flow more."),
   M("A 60 W bulb works on 220 V. The current is about", "0.27 A", ["3.7 A", "13 A", "0.0045 A"], "I = P/V = 60/220 = 0.27 A.")],
  (iv_fig(), "I–V characteristics: ohmic resistor (straight line), filament lamp (flattening) and diode (one direction).", "Three curves on I–V axes: a straight line, an S-shaped curve flattening at high values and a curve that rises steeply only for positive voltage."),
  notes=["Mains voltage in Cameroon is taken as 220 V for the examples (to be checked); no tariff is stated.", "Resistivity values for copper and nichrome and n for copper are textbook values; check the board's data sheet.", "The threshold of silicon diodes (about 0.6 V) is a typical value."], minutes=30, prereq=["lower6-physics-energy"])

# ---- 6.2 circuits, emf, internal resistance
R_par = 6 * 3 / (6 + 3); R_tot = 4 + R_par; I_t = 12 / R_tot; V_p = I_t * R_par
assert R_par == 2 and R_tot == 6 and I_t == 2 and V_p == 4
I_ir = 12.0 / (5.5 + 0.5); V_ir = I_ir * 5.5; assert I_ir == 2 and V_ir == 11
Rp2 = 4 * 12 / (4 + 12); I9 = 9.0 / (Rp2 + 1.0); V9 = I9 * Rp2; I4 = V9 / 4; assert Rp2 == 3 and abs(I9 - 2.25) < 1e-12 and abs(V9 - 6.75) < 1e-12 and abs(I4 - 1.6875) < 1e-12
def circuit_fig():
    it = branch((60, 50), (300, 50), [(90, "res", "4 Ω")]) + branch((60, 50), (60, 190), [(0.5, "cell", "12 V")], lab_side="left")
    it += branch((60, 190), (300, 190)) + branch((200, 50), (200, 190), [(0.5, "res", "6 Ω")]) + branch((300, 50), (300, 190), [(0.5, "res", "3 Ω")])
    it += [dot(200, 50), dot(200, 190)]
    return shapes(it, 400, 240)
lesson(ch, "circuits", "DC circuits, Kirchhoff's laws and internal resistance",
  ["Combine resistors in series and parallel and apply Kirchhoff's two laws",
   "Define emf and internal resistance and use E = V + Ir = I(R + r)",
   "Determine E and r from a graph of terminal pd against current"],
  [("formule", "Resistors in series and parallel",
    "Series: **R = R₁ + R₂ + …** (same current, voltages add). Parallel: **1/R = 1/R₁ + 1/R₂ + …** (same voltage, currents add; the total is smaller than the smallest resistor).\n\n"
    "**Kirchhoff's first law**: the sum of currents entering a junction equals the sum leaving (conservation of charge). **Second law**: around a closed loop, the sum of emfs equals the sum of the potential drops IR (conservation of energy).",
    r"R_s=R_1+R_2\qquad \frac{1}{R_p}=\frac{1}{R_1}+\frac{1}{R_2}"),
   ("definition", "emf and internal resistance",
    "The **emf** E of a source is the energy transferred to each coulomb of charge in the source (J C⁻¹), equal to the terminal pd when no current flows. A real source has **internal resistance** r, so with a current I the **terminal pd** is **V = E − Ir** (the term Ir is the 'lost volts').\n\n"
    "For an external resistance R: E = I(R + r). Power delivered to the external circuit = IV; power wasted inside the source = I²r."),
   ("methode", "Method for circuits",
    "1. Simplify parallel groups, then series, to find the total resistance (including r).\n2. Find the main current I = E/(R_total).\n3. Work back: pd across a parallel group = I × R_group, then the current in each branch = V/R.\n4. Check: the currents at a junction add up and Σ IR = E around the loop."),
   ("pieges", "Common mistakes",
    "- Forgetting the internal resistance when finding the current.\n- Adding resistors in parallel with the series formula (or taking 1/R instead of R).\n- Assuming the terminal pd equals E while the cell is delivering current.\n- Voltage and current meters: an ideal ammeter is in series (low resistance), an ideal voltmeter in parallel (very high resistance).")],
  [("Example 1 — Series–parallel circuit", "A 12 V battery of negligible internal resistance supplies a 4 Ω resistor in series with a 6 Ω resistor in parallel with a 3 Ω resistor. Find the main current and the current in each parallel resistor.",
    ["Parallel: 6 × 3 ÷ (6 + 3) = 2 Ω. Total R = 4 + 2 = 6 Ω.", "Main current I = 12/6 = 2.0 A; pd across the parallel group = 2.0 × 2 = 4.0 V.", "Currents: 4.0/6 = 0.67 A and 4.0/3 = 1.33 A (sum = 2.0 A ✓)."], "I = 2.0 A; 0.67 A and 1.33 A in the 6 Ω and 3 Ω resistors"),
   ("Example 2 — A real battery", "A battery of emf 12.0 V and internal resistance 0.50 Ω is connected to a 5.5 Ω resistor. Find the current, the terminal pd and the lost volts.",
    ["I = E/(R + r) = 12.0/(5.5 + 0.50) = 2.0 A.", "Terminal pd V = IR = 2.0 × 5.5 = 11.0 V.", "Lost volts = Ir = 2.0 × 0.50 = 1.0 V (E = V + Ir: 12.0 = 11.0 + 1.0 ✓)."], "I = 2.0 A, V = 11.0 V, lost volts = 1.0 V")],
  [N("Find the resistance of 10 Ω and 15 Ω in parallel (Ω).", 6.0, "10 × 15 ÷ 25 = 6.0 Ω.", 0.05, "Ω"),
   N("A 6.0 V cell of internal resistance 1.0 Ω drives 2.0 A through a resistor. Find the terminal pd (V).", 4.0, "V = E − Ir = 6.0 − 2.0 × 1.0 = 4.0 V.", 0.05, "V"),
   TF("True or false: three 6 Ω resistors in parallel have a total resistance of 18 Ω.", False, "In parallel: 1/R = 3/6 so R = 2 Ω. (Series would give 18 Ω.)")],
  [N("In the circuit of Example 1, find the total power supplied by the 12 V battery (W).", 12 * 2.0, "P = EI = 12 × 2.0 = 24 W.", 0.1, "W", difficulty=2),
   N("A cell has emf 1.5 V and the terminal pd falls to 1.2 V when it supplies 0.60 A. Find its internal resistance (Ω).", 0.5, "r = (E − V)/I = 0.30/0.60 = 0.50 Ω.", 0.005, "Ω")],
  P("A battery of emf 9.0 V and internal resistance 1.0 Ω is connected to a 4.0 Ω resistor in parallel with a 12 Ω resistor.",
    [pn("Total external resistance (Ω).", Rp2, "4 × 12 ÷ 16 = 3.0 Ω.", 0.05, "Ω", 1),
     pn("Current supplied by the battery (A).", I9, "I = E/(R + r) = 9.0/4.0 = 2.25 A.", 0.005, "A", 1),
     pn("Terminal pd (V).", V9, "V = IR = 2.25 × 3.0 = 6.75 V.", 0.01, "V", 1),
     pn("Current in the 4.0 Ω resistor (A, 3 s.f.).", sg(I4, 3), "I = V/R = 6.75/4.0 = 1.69 A.", 0.01, "A", 1)]),
  [M("Which is the correct expression for terminal pd of a cell that delivers a current?", "V = E − Ir", ["V = E + Ir", "V = E/Ir", "V = Ir"], "Part of the emf is 'lost' across the internal resistance."),
   M("Kirchhoff's first law expresses conservation of", "charge", ["energy", "momentum", "mass"], "Charge entering a junction equals charge leaving."),
   M("Two identical resistors of 8 Ω in parallel have a resistance of", "4 Ω", ["16 Ω", "8 Ω", "2 Ω"], "R/2 for two equal resistors."),
   M("An ideal voltmeter has", "infinite resistance", ["zero resistance", "resistance equal to the circuit", "a resistance of 1 Ω"], "It must not draw current from the circuit."),
   M("A graph of terminal pd V against current I for a cell has a gradient of", "−r", ["+E", "−E", "+r"], "V = E − Ir: intercept E, gradient −r.")],
  (circuit_fig(), "A 12 V cell, a 4 Ω resistor in series with 6 Ω and 3 Ω resistors in parallel.", "A circuit diagram with a cell on the left, a resistor on the top wire and two resistors in parallel branches on the right."),
  notes=["Second law is stated in the 'sum of emfs = sum of IR' form; sign conventions are not developed.", "The maximum-power-transfer theorem is not included."], minutes=35, prereq=["lower6-physics-resistance"])

# ---- 6.3 potential divider, potentiometer
Vd = 9.0 * 3 / (6 + 3); assert Vd == 3
Vl_d = 9.0 * 10 / (100 + 10); Vl_b = 9.0 * 10 / (1 + 10); assert abs(Vl_d - 0.818) < 0.001 and abs(Vl_b - 8.182) < 0.001
Vld = 9.0 * 1.5 / (6 + 1.5); assert abs(Vld - 1.8) < 1e-12
E1 = 1.5 * 62.5 / 75.0; r_x = 4.0 * (62.5 - 50.0) / 50.0; assert abs(E1 - 1.25) < 1e-12 and abs(r_x - 1.0) < 1e-12
def pot_fig():
    it = [LINE(60, 150, 340, 150, width=5), T(60, 172, "A", 14, bold=True), T(340, 172, "B", 14, bold=True)]
    it += branch((60, 150), (60, 220)) + branch((60, 220), (340, 220), [(0.5, "cell", "driver cell")], lab_side="down") + branch((340, 220), (340, 150))
    it += branch((60, 150), (60, 70)) + branch((60, 70), (210, 70), [(0.3, "cell", "E"), (0.75, "meter", "G")])
    it += [LINE(210, 70, 210, 145, arrow="end", color="red", width=2), T(228, 108, "jockey", 12, anchor="start", color="red"),
           LINE(60, 188, 210, 188, color="blue", width=2, arrow="both"), T(135, 204, "l", 14, color="blue")]
    return shapes(it, 400, 250)
lesson(ch, "divider", "Potential dividers and the potentiometer",
  ["Use the potential-divider formula V_out = V·R₂/(R₁ + R₂) and explain the effect of loading",
   "Describe how an LDR or thermistor in a divider makes a sensor circuit",
   "Use a potentiometer to compare emfs and to measure an internal resistance"],
  [("formule", "Potential divider",
    "Two resistors R₁ and R₂ in series across a supply V share it in proportion to their resistances: the pd across R₂ is **V_out = V × R₂/(R₁ + R₂)**.\n\n"
    "A **load** connected across R₂ is in parallel with it, reducing the effective R₂ and so V_out. A load of high resistance compared with R₂ has little effect.",
    r"V_{out}=V\,\frac{R_2}{R_1+R_2}"),
   ("retenir", "Sensor circuits",
    "A **light-dependent resistor (LDR)** has a resistance that falls as the light intensity rises; a **thermistor (NTC)** has a resistance that falls as the temperature rises. Placed in a divider with a fixed resistor, they make the output voltage change with light or temperature (for example to switch on a security light at night)."),
   ("methode", "The potentiometer",
    "A uniform resistance wire AB is connected to a driver cell, so the pd along the wire is proportional to length. A cell of emf E₁ is connected through a galvanometer to a jockey; at the **balance (null) point** no current flows in the cell, so the pd across length l₁ equals E₁.\n\n"
    "Comparing emfs: **E₁/E₂ = l₁/l₂**. Internal resistance: with a resistor R across the cell the balance length falls to l₂: **r = R(l₁ − l₂)/l₂**."),
   ("pieges", "Common mistakes",
    "- Forgetting that a voltmeter or load in parallel lowers the output of a divider.\n- Using the length of the wire in cm in one place and m in another (ratios are fine in the same unit).\n- Believing that the potentiometer draws current at balance: it does not, which is why it measures the true emf.\n- The driver cell must have a larger emf than the cells being measured.")],
  [("Example 1 — Dividers", "A 9.0 V supply is connected across a 6.0 kΩ and a 3.0 kΩ resistor in series. Find the pd across the 3.0 kΩ resistor, and then when a 3.0 kΩ load is connected across it.",
    ["V_out = 9.0 × 3.0/(6.0 + 3.0) = 3.0 V.", "With the load, the 3.0 kΩ in parallel with 3.0 kΩ is 1.5 kΩ.", "V_out = 9.0 × 1.5/(6.0 + 1.5) = 1.8 V (loading lowers the output)."], "3.0 V unloaded; 1.8 V loaded"),
   ("Example 2 — Potentiometer", "A potentiometer wire is 1.000 m long. A standard cell of 1.50 V balances at 75.0 cm. A test cell balances at 62.5 cm, and at 50.0 cm when a 4.0 Ω resistor is connected across it. Find its emf and internal resistance.",
    ["E₁ = 1.50 × 62.5/75.0 = 1.25 V.", "With R across the cell: r = R(l₁ − l₂)/l₂ = 4.0 × (62.5 − 50.0)/50.0 = 1.0 Ω."], "E = 1.25 V, r = 1.0 Ω")],
  [N("A 12 V supply is across 8.0 kΩ and 4.0 kΩ in series. Find the pd across the 4.0 kΩ resistor (V).", 12 * 4 / 12, "V = 12 × 4.0/12.0 = 4.0 V.", 0.05, "V"),
   N("A cell balances at 40.0 cm on a potentiometer wire. A 1.50 V standard cell balances at 60.0 cm. Find the emf of the first cell (V).", 1.50 * 40 / 60, "E = 1.50 × 40.0/60.0 = 1.00 V.", 0.01, "V"),
   TF("True or false: at the balance point of a potentiometer, current flows through the cell being tested.", False, "At balance the galvanometer reads zero, so no current flows in the cell: it measures the true emf.")],
  [N("In a light-sensing divider, an LDR (100 kΩ in the dark, 1.0 kΩ in bright light) is connected in series with a 10 kΩ resistor across 9.0 V. Find the pd across the 10 kΩ resistor in bright light (V, 3 s.f.).", sg(Vl_b, 3), "V = 9.0 × 10/(1.0 + 10) = 8.18 V (in the dark it would be only 0.818 V).", 0.02, "V", difficulty=2),
   N("A cell of emf 1.25 V gives a balance length of 62.5 cm. With a 4.0 Ω resistor across it the balance length is 50.0 cm. Find the internal resistance (Ω).", r_x, "r = R(l₁ − l₂)/l₂ = 4.0 × 12.5/50.0 = 1.0 Ω.", 0.01, "Ω")],
  P("A thermistor-based fire alarm uses a divider: a 5.0 V supply, a fixed 2.0 kΩ resistor and a thermistor in series. The output is taken across the fixed resistor.",
    [pn("The thermistor resistance falls from 8.0 kΩ (cool) to 2.0 kΩ (hot). Output pd when cool (V).", 5.0 * 2.0 / (8.0 + 2.0), "V = 5.0 × 2.0/10.0 = 1.0 V.", 0.01, "V", 1),
     pn("Output pd when hot (V).", 5.0 * 2.0 / (2.0 + 2.0), "V = 5.0 × 2.0/4.0 = 2.5 V.", 0.01, "V", 1),
     pm("The output pd rises as the temperature rises because", "the thermistor's resistance falls so it takes a smaller share of the supply", ["the supply voltage increases", "the fixed resistor's resistance rises", "the current decreases"], "The thermistor's share falls, so the fixed resistor's share rises.", 1),
     pt("The alarm circuit would work equally if the output were taken across the thermistor.", False, "Then the output would fall as the temperature rises (the thermistor's share decreases), so the logic would be reversed.", 1)]),
  [M("The balance (null) point of a potentiometer is where", "no current flows through the galvanometer", ["the wire is hottest", "the driver cell is disconnected", "the voltage is zero everywhere"], "The unknown pd equals the wire's pd over length l."),
   M("Two equal resistors in series across 10 V. The pd across each is", "5 V", ["10 V", "2.5 V", "20 V"], "They share equally."),
   M("A load in parallel with R₂ in a divider makes V_out", "decrease", ["increase", "stay the same", "equal V"], "The effective R₂ falls."),
   M("The resistance of an LDR", "falls as light increases", ["rises as light increases", "is constant", "is zero in dark"], "More light frees more charge carriers."),
   M("In a potentiometer experiment, the driver cell must have", "a larger emf than the cell being tested", ["a smaller emf", "no internal resistance at all", "the same emf exactly"], "Otherwise no balance point can be found on the wire.")],
  (pot_fig(), "A potentiometer: a test cell and galvanometer are connected from A to the jockey touching the wire AB.", "A long resistance wire AB with a driver cell below it and a test cell with galvanometer connected from end A to a sliding jockey on the wire."),
  notes=["The ideal-meter assumptions are used throughout.", "The galvanometer is shown with the symbol G; whether the board expects a protective resistor is not stated."], minutes=30, prereq=["lower6-physics-circuits"])
p.write()
