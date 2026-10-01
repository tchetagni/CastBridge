import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *

p = Pack("form3-physics", "Physics — Form 3", level="Form 3", subject="physics", cursus="secondary",
         description="A first course in O Level Physics for Form 3: measurement, density, motion and its graphs, forces and Newton's laws, moments, pressure, work, energy and power and simple machines. Worked examples with g = 10 m/s², and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Physics, Form 3 (first year of the GCE O Level Physics course) — to be checked against the official syllabus")

# =========================================================== 1 Measurement and matter
ch = p.chapter("measurement", "Measurement and density", "Form 3 Physics — Units, instruments, density (to be checked)")
def ruler_fig():
    d = Draw()
    x0, per_cm = 20, 40
    d.sh.append(RECT(x0, 90, 400, 36, fill="lightyellow", width=2))
    for mm in range(0, 101):
        x = x0 + mm * 4
        d.line(x, 90, x, 90 + (16 if mm % 10 == 0 else (11 if mm % 5 == 0 else 7)), width=1.2)
    for cm in range(0, 11): d.text(x0 + cm * per_cm, 118, str(cm), size=11)
    d.sh.append(RECT(x0 + 40, 40, 252, 36, fill="lightblue", width=2))
    d.line(x0 + 40, 78, x0 + 40, 128, color="red", width=1.2, dash=True); d.line(x0 + 292, 78, x0 + 292, 128, color="red", width=1.2, dash=True)
    d.text(x0 + 166, 62, "block", size=12)
    d.text(220, 150, "left edge at 1.0 cm, right edge at 6.3 cm", size=11)
    return d.fig(440, 165)
length = round(6.3 - 1.0, 1); assert length == 5.3
vol_m3 = 5.0e-2 * 4.0e-2 * 2.0e-2; assert abs(vol_m3 - 4.0e-5) < 1e-12
build(ch, "units-instruments", "Units and measuring instruments", 25,
      ["State the SI base units for length, mass and time and use common prefixes.", "Read a metre rule and name the instruments for small lengths.", "Use standard form and calculate areas and volumes."],
      [("key", "retenir", "SI units and prefixes",
        "The **SI base units** are the **metre (m)**, **kilogram (kg)** and **second (s)**. Derived units: area m², volume m³, speed m/s. Prefixes: **kilo (k) = 10³**, **centi (c) = 10⁻²**, **milli (m) = 10⁻³**, **micro (µ) = 10⁻⁶**. So 1 km = 1000 m, 1 cm = 0.01 m, 1 mm = 0.001 m, 1 g = 0.001 kg. 1 litre = 1000 cm³ = 0.001 m³."),
       ("fig", ruler_fig(), "Reading a metre rule when the object does not start at zero.", "A rule marked 0 to 10 cm with a block whose left edge is at 1.0 cm and right edge at 6.3 cm."),
       ("key", "methode", "Instruments and good practice",
        "- **Metre rule**: reads to 1 mm (0.1 cm). **Vernier callipers** read to 0.1 mm and a **micrometer screw gauge** to 0.01 mm: used for small lengths and diameters.\n- Look **straight down** at the scale to avoid **parallax**; if the end of the rule is worn, start from a whole-number mark and subtract.\n- Check for **zero error** before measuring.\n- A **stopwatch** reads time; timing many swings reduces the error."),
       ("key", "retenir", "Standard form and volumes",
        "Very large or small numbers are written as **a × 10ⁿ** with 1 ≤ a < 10: 4500 m = 4.5 × 10³ m; 0.0032 kg = 3.2 × 10⁻³ kg. **Area of a rectangle** = length × width; **volume of a block** = length × width × height. Convert every length to the same unit before multiplying."),
       ("key", "pieges", "Common mistakes",
        "- Reading a rule from its end when the edge is damaged or when the object is not placed at zero.\n- Mixing units: 40 cm³ is **not** 40 m³ (1 m³ = 1 000 000 cm³).\n- **Accuracy** (close to the true value) and **precision** (readings close to each other) are not the same."),
       ("example", "Length from a rule", "A block lies along a rule from the 1.0 cm mark to the 6.3 cm mark. Find its length.", ["Length = right reading − left reading.", "6.3 − 1.0 = 5.3 cm."], "5.3 cm"),
       ("example", "Volume in m³", "A block is 5.0 cm × 4.0 cm × 2.0 cm. Find its volume in cm³ and in m³.", ["Volume = 5.0 × 4.0 × 2.0 = 40 cm³.", "1 cm³ = 10⁻⁶ m³, so 40 cm³ = 4.0 × 10⁻⁵ m³."], "40 cm³ = 4.0 × 10⁻⁵ m³")],
      [("mcq", "Which instrument is best for measuring the diameter of a thin wire?", "micrometer screw gauge", ["metre rule", "measuring tape", "stopwatch"], "It reads to 0.01 mm."),
       ("num", "Convert 250 mm to metres.", 0.25, "250 × 0.001 = 0.25 m.", 0.001, "m"),
       ("num", "Convert 3.5 litres to cm³.", 3500, "3.5 × 1000 = 3500 cm³.", 0, "cm³"),
       ("mcq", "4500 m written in standard form is:", "4.5 × 10³ m", ["45 × 10³ m", "4.5 × 10⁴ m", "0.45 × 10³ m"], "4500 = 4.5 × 1000."),
       ("open", "A pupil's metre rule has its zero end worn off. Describe how to measure the length of a pencil accurately with it.", "Place the pencil along the rule starting at a clear whole-number mark such as 1.0 cm, read both ends with the eye directly above the marks, and subtract the two readings.", ["1 mark: start from a clear mark", "1 mark: read both ends", "1 mark: subtract"]),
       ("prob", "A rectangular classroom floor is 8.0 m long and 6.5 m wide. A tile is 0.50 m × 0.50 m.", [("num", "Area of the floor in m².", 52, "8.0 × 6.5 = 52 m².", 0.01, "m²"), ("num", "Area of one tile in m².", 0.25, "0.5 × 0.5 = 0.25 m².", 0.001, "m²"), ("num", "Number of tiles needed to cover the floor.", 208, "52 ÷ 0.25 = 208.", 0)])],
      [("In the SI system the base unit of mass is the:", "kilogram", ["gram", "newton", "litre"], "The base unit is the kg."),
       ("The prefix 'milli' means:", "one thousandth", ["one thousand", "one hundredth", "one million"], "10⁻³."),
       ("Parallax error can be reduced by:", "looking straight at the scale", ["using a longer rule", "looking from the side", "pressing harder"], "Eye in line with the mark."),
       ("1 cm³ equals how many m³?", "10⁻⁶", ["10⁻³", "10³", "10⁻²"], "1 m = 100 cm, so 1 m³ = 10⁶ cm³."),
       ("A zero error means the instrument:", "does not read zero when it should", ["is broken", "is too large", "reads exactly"], "Corrections must be made to the readings.")],
      ["The micrometer and vernier callipers are listed from the common O Level syllabus; check which are required in Form 3.", "Reading practice is limited to the metre rule as pure text cannot show vernier scales."])

dens = bars([("ice", 0.9, "lightblue"), ("water", 1.0, "blue"), ("aluminium", 2.7, "grey"), ("iron", 7.9, "orange")], w=420, h=250)
rho1 = 200 / (5 * 4 * 2.5); assert rho1 == 4.0
rho2 = 156 / 20; assert abs(rho2 - 7.8) < 1e-9
m_oil = 0.9 * 500; assert abs(m_oil - 450) < 1e-9
build(ch, "density", "Density", 25,
      ["Define density and use ρ = m / V.", "Convert between g/cm³ and kg/m³.", "Describe how to find the density of regular and irregular solids and decide whether an object floats."],
      [("key", "formule", "Density",
        "**Density = mass ÷ volume**, **ρ = m / V**, so m = ρ × V and V = m / ρ. Units: **g/cm³** or **kg/m³**, with **1 g/cm³ = 1000 kg/m³**. Water has a density of **1.0 g/cm³ (1000 kg/m³)**."),
       ("fig", dens, "Approximate densities in g/cm³.", "A bar chart: ice 0.9, water 1.0, aluminium 2.7 and iron 7.9 grams per cubic centimetre."),
       ("key", "methode", "Measuring density",
        "- **Regular solid**: measure its mass on a balance; calculate its volume from its dimensions; divide.\n- **Irregular solid**: find the volume by **displacement** (final level − first level in a measuring cylinder).\n- **Liquid**: weigh an empty measuring cylinder, add a known volume of liquid, weigh again; the extra mass divided by the volume is the density."),
       ("key", "retenir", "Floating and sinking",
        "An object **floats** in a liquid if its density is **less** than that of the liquid, and **sinks** if it is **greater**. Ice (about 0.9 g/cm³) floats on water; iron (about 7.9 g/cm³) sinks. Oil floats on water because it is less dense. A steel ship floats because its hollow shape gives it an **average density** lower than water."),
       ("key", "pieges", "Common mistakes",
        "- Density is not the same as mass: a large piece of iron and a small piece have the same density.\n- Convert units before dividing: 1 g/cm³ is 1000 kg/m³, not 100.\n- Do not use the mass of the container when finding the density of a liquid."),
       ("example", "Density of a block", "A rectangular block 5 cm × 4 cm × 2.5 cm has a mass of 200 g. Find its density.", ["Volume = 5 × 4 × 2.5 = 50 cm³.", "ρ = 200 ÷ 50 = 4.0 g/cm³ (= 4000 kg/m³)."], "4.0 g/cm³"),
       ("example", "Mass of oil", "Palm oil has a density of about 0.9 g/cm³. What is the mass of 500 cm³ of it?", ["m = ρ × V.", "m = 0.9 × 500 = 450 g."], "450 g")],
      [("num", "A stone has a mass of 156 g and a volume of 20 cm³. Find its density in g/cm³.", 7.8, "156 ÷ 20 = 7.8 g/cm³.", 0.01, "g/cm³"),
       ("num", "Convert a density of 2.7 g/cm³ to kg/m³.", 2700, "2.7 × 1000 = 2700 kg/m³.", 0, "kg/m³"),
       ("mcq", "A solid of density 0.6 g/cm³ is placed in water. It will:", "float", ["sink", "dissolve", "stay in the middle always"], "0.6 is less than 1.0."),
       ("num", "What is the mass of 2.0 m³ of water? (density 1000 kg/m³)", 2000, "1000 × 2.0 = 2000 kg.", 0, "kg"),
       ("open", "Describe how to find the density of a small irregular stone.", "Find the mass with a balance. Partly fill a measuring cylinder with water, note the level, lower in the stone, note the new level; the difference is the volume. Divide mass by volume.", ["1 mark: mass measured", "1 mark: volume by displacement", "1 mark: divide mass by volume"]),
       ("prob", "A metal cube of side 2.0 cm has a mass of 21.6 g. Density of water = 1.0 g/cm³.", [("num", "Volume of the cube in cm³.", 8.0, "2.0 × 2.0 × 2.0 = 8.0 cm³.", 0.01, "cm³"), ("num", "Density of the metal in g/cm³.", 2.7, "21.6 ÷ 8.0 = 2.7 g/cm³.", 0.01, "g/cm³"), ("mcq", "In water the cube will:", "sink", ["float", "dissolve", "rise"], "2.7 g/cm³ is greater than 1.0 g/cm³.")])],
      [("Density equals:", "mass ÷ volume", ["volume ÷ mass", "mass × volume", "mass + volume"], "ρ = m/V."),
       ("The density of water is:", "1000 kg/m³", ["100 kg/m³", "10 kg/m³", "1 kg/m³"], "That is 1 g/cm³."),
       ("Ice floats on water because its density is:", "less than that of water", ["greater than water", "zero", "equal to iron"], "About 0.9 g/cm³."),
       ("The volume of an irregular solid is found by:", "displacement of water", ["weighing it", "using a stopwatch", "using a thermometer"], "Final level minus first level."),
       ("If the mass is 90 g and the volume 30 cm³, the density is:", "3 g/cm³", ["300 g/cm³", "0.33 g/cm³", "60 g/cm³"], "90 ÷ 30 = 3.")],
      ["Densities quoted (ice 0.9, aluminium 2.7, iron 7.9, palm oil about 0.9) are rounded textbook values; palm oil varies with temperature and quality."])

# =========================================================== 2 Motion
ch = p.chapter("motion", "Motion", "Form 3 Physics — Speed, velocity, acceleration, motion graphs, falling bodies (to be checked)")
def tape_fig():
    d = Draw()
    d.text(240, 22, "constant speed: equal gaps", size=12)
    for i in range(9): d.sh.append(CIRCLE(40 + i * 50, 48, 5, fill="blue", width=1))
    d.text(240, 100, "speeding up: gaps get bigger", size=12)
    x = 30
    gaps = [8, 16, 24, 32, 40, 48, 56]; xs = [x]
    for g in gaps: x += g; xs.append(x)
    for xx in xs: d.sh.append(CIRCLE(xx, 126, 5, fill="red", width=1))
    d.text(240, 156, "(the time between two dots is always the same)", size=11)
    return d.fig(480, 170)
a1 = (20 - 0) / 8; assert a1 == 2.5
build(ch, "speed-acceleration", "Speed, velocity and acceleration", 25,
      ["Distinguish distance from displacement and speed from velocity.", "Calculate average speed and acceleration.", "Interpret dot patterns (ticker tape) for steady and increasing speed."],
      [("key", "definition", "Scalars and vectors",
        "A **scalar** has size only (distance, speed, mass, time). A **vector** has size and **direction** (displacement, velocity, force, acceleration). **Distance** is the length of the path travelled; **displacement** is the straight-line distance from start to finish in a given direction. **Speed** = distance ÷ time; **velocity** = displacement ÷ time, in a stated direction."),
       ("fig", tape_fig(), "Dots made at equal time intervals: steady speed and increasing speed.", "Two rows of dots: the top row evenly spaced, the bottom row with spacing that grows."),
       ("key", "formule", "Acceleration",
        "**Acceleration** is the rate of change of velocity: **a = (v − u) ÷ t**, where u is the initial velocity, v the final velocity and t the time. Unit: **m/s²**. A **negative** acceleration is a **deceleration** (slowing down). Uniform acceleration means constant acceleration."),
       ("key", "pieges", "Common mistakes",
        "- Speed and velocity are not the same: a car going round a circuit at 20 m/s has a changing velocity because its direction changes.\n- Acceleration is not speed: a body can be fast with zero acceleration.\n- Use u for the start and v for the end; do not swap them."),
       ("example", "Acceleration of a car", "A car starts from rest and reaches 20 m/s in 8 s. Find its acceleration.", ["u = 0, v = 20 m/s, t = 8 s.", "a = (20 − 0) ÷ 8 = 2.5 m/s²."], "2.5 m/s²"),
       ("example", "Deceleration", "A bus slows from 15 m/s to 5 m/s in 4 s. Find its acceleration.", ["a = (v − u) ÷ t = (5 − 15) ÷ 4.", "= −2.5 m/s², a deceleration of 2.5 m/s²."], "−2.5 m/s²")],
      [("num", "A cyclist travels 1200 m in 4 minutes. Find the average speed in m/s.", 5, "4 min = 240 s; 1200 ÷ 240 = 5 m/s.", 0, "m/s"),
       ("num", "A motorbike speeds up from 4 m/s to 16 m/s in 6 s. Find its acceleration in m/s².", 2, "(16 − 4) ÷ 6 = 2 m/s².", 0, "m/s²"),
       ("mcq", "Which of these is a vector?", "velocity", ["speed", "mass", "time"], "Velocity has direction."),
       ("num", "A train slows from 30 m/s to rest in 20 s. What is its acceleration (m/s²)?", -1.5, "(0 − 30) ÷ 20 = −1.5 m/s².", 0.01, "m/s²"),
       ("open", "A runner completes one lap of a 400 m track in 80 s. State her average speed and her average velocity over the lap, and explain the difference.", "Average speed = 400 ÷ 80 = 5 m/s. Her displacement over one full lap is zero so the average velocity is 0 m/s; speed uses the distance covered, velocity uses displacement.", ["1 mark: speed 5 m/s", "1 mark: velocity 0 m/s", "1 mark: reason (displacement zero)"]),
       ("prob", "A car moves off from rest with uniform acceleration 3 m/s² for 6 s, then travels at constant velocity for 10 s.", [("num", "Velocity reached after 6 s (m/s).", 18, "v = u + at = 0 + 3 × 6 = 18 m/s.", 0, "m/s"), ("num", "Distance travelled in the 10 s at constant velocity (m).", 180, "18 × 10 = 180 m.", 0, "m"), ("num", "Acceleration during the constant-velocity stage (m/s²).", 0, "Velocity does not change so a = 0.", 0, "m/s²")])],
      [("The unit of acceleration is:", "m/s²", ["m/s", "m", "N"], "Change of velocity per second."),
       ("A negative acceleration means the object is:", "slowing down", ["speeding up", "at rest always", "turning"], "Deceleration."),
       ("Velocity differs from speed because velocity has:", "direction", ["more mass", "no unit", "less time"], "It is a vector."),
       ("Acceleration is calculated from:", "(v − u) ÷ t", ["(v + u) × t", "v × t", "u ÷ t"], "Change in velocity over time."),
       ("Dots on a ticker tape that get further apart show:", "speeding up", ["slowing down", "constant speed", "rest"], "More distance in the same time.")],
      ["Convention a = (v − u)/t; check whether the syllabus writes 'retardation' for deceleration."])

# v-t graph: 0->10 in 5 s, 10 for 10 s, 10->0 in 5 s
area = 0.5 * 5 * 10 + 10 * 10 + 0.5 * 5 * 10; assert area == 150
slope1 = 10 / 5; assert slope1 == 2
vt = segs_graph(0, 20, 0, 12, [(0, 0, 5, 10, "blue"), (5, 10, 15, 10, "blue"), (15, 10, 20, 0, "blue")], xlabel="t (s)", ylabel="v (m/s)", grid=2, w=420, h=260)
build(ch, "motion-graphs", "Motion graphs", 25,
      ["Read distance-time and velocity-time graphs.", "Find speed from a gradient and acceleration from the slope of a v-t graph.", "Find distance from the area under a velocity-time graph."],
      [("key", "retenir", "Distance-time graphs",
        "The **gradient** of a distance-time graph is the **speed**. A straight sloping line shows **constant speed**, a steeper line a higher speed, a **horizontal line** an object at rest, and a **curve** a changing speed."),
       ("fig", vt, "A velocity-time graph: speeding up, constant speed, slowing down.", "A graph of velocity (m/s) against time (s): a line rising from 0 to 10 m/s in 5 s, flat at 10 m/s until 15 s, then falling to 0 at 20 s."),
       ("key", "retenir", "Velocity-time graphs",
        "On a **velocity-time graph**: the **gradient** is the **acceleration** (a rising line is positive, a falling line negative, a flat line zero acceleration at constant velocity). The **area under the graph** is the **distance travelled**. For the graph above the area is two triangles and a rectangle."),
       ("key", "pieges", "Common mistakes",
        "- A horizontal line is **rest** on a distance-time graph but **constant velocity** on a velocity-time graph.\n- Read the axes first: the same shape means different things on different graphs.\n- Use the scales to get the gradient: rise ÷ run with units."),
       ("example", "Acceleration and distance from the graph", "Use the graph: find the acceleration in the first 5 s and the total distance travelled in 20 s.", ["Acceleration = gradient = (10 − 0) ÷ 5 = 2 m/s².", "Area = ½ × 5 × 10 + 10 × 10 + ½ × 5 × 10 = 25 + 100 + 25 = 150 m."], "2 m/s²; 150 m"),
       ("example", "Speed from a distance-time graph", "A distance-time graph is a straight line from (0 s, 0 m) to (20 s, 100 m). Find the speed.", ["Speed = gradient = rise ÷ run.", "100 ÷ 20 = 5 m/s."], "5 m/s")],
      [("mcq", "A horizontal line on a velocity-time graph at 8 m/s shows:", "constant velocity", ["rest", "constant acceleration", "deceleration"], "Velocity does not change."),
       ("num", "On a velocity-time graph a line rises from 0 to 12 m/s in 4 s. Find the acceleration in m/s².", 3, "12 ÷ 4 = 3 m/s².", 0, "m/s²"),
       ("num", "A body moves at a steady 6 m/s for 10 s. Find the distance, as the area under its v-t graph.", 60, "6 × 10 = 60 m.", 0, "m"),
       ("mcq", "On a distance-time graph a line that gets steeper means that the object is:", "speeding up", ["slowing down", "at rest", "returning"], "The gradient (speed) increases."),
       ("num", "A v-t graph is a triangle with base 8 s and height 10 m/s. Find the distance travelled.", 40, "½ × 8 × 10 = 40 m.", 0, "m"),
       ("prob", "A train starts from rest and accelerates uniformly to 20 m/s in 10 s. It then travels at 20 m/s for 30 s and brakes uniformly to a stop in 10 s.", [("num", "Acceleration in the first 10 s (m/s²).", 2, "20 ÷ 10 = 2 m/s².", 0, "m/s²"), ("num", "Total distance travelled (m). Use area: two triangles and a rectangle.", 800, "½ × 10 × 20 + 30 × 20 + ½ × 10 × 20 = 100 + 600 + 100 = 800 m.", 0, "m"), ("num", "Average speed for the whole 50 s (m/s).", 16, "800 ÷ 50 = 16 m/s.", 0, "m/s")])],
      [("The area under a velocity-time graph gives the:", "distance travelled", ["acceleration", "speed", "mass"], "Area = distance."),
       ("The gradient of a distance-time graph gives the:", "speed", ["acceleration", "force", "distance"], "Slope = speed."),
       ("A flat line on a distance-time graph means the object is:", "at rest", ["accelerating", "falling", "moving at constant speed"], "Distance stays the same."),
       ("A falling line on a velocity-time graph shows:", "deceleration", ["acceleration", "constant velocity", "rest"], "Velocity decreases."),
       ("A triangle on a v-t graph has an area equal to:", "½ × base × height", ["base × height", "base + height", "base ÷ height"], "Triangle area.")],
      ["Graphs are drawn in the app with plain line segments; check that the syllabus expects area under the v-t graph in Form 3."])

h1 = 0.5 * 10 * 2 ** 2; assert h1 == 20
tfall = math.sqrt(2 * 20 / 10); assert abs(tfall - 2) < 1e-12 and 10 * tfall == 20
dist_fall = [0.5 * 10 * t * t for t in (1, 2, 3)]; assert dist_fall == [5, 20, 45]
fall = bars([("1 s", 5, "lightblue"), ("2 s", 20, "blue"), ("3 s", 45, "purple")], w=400, h=250)
build(ch, "falling-bodies", "Uniform acceleration and falling bodies", 25,
      ["Use v = u + at and s = ½(u + v)t for uniform acceleration.", "State that all bodies fall with the same acceleration in the absence of air resistance (g = 10 m/s²).", "Solve simple free-fall problems."],
      [("key", "formule", "Equations for uniform acceleration",
        "For constant acceleration a, starting at velocity u:\n\n**v = u + at**\n\n**s = ½(u + v) t** (distance = average velocity × time)\n\nSigns: take the direction of motion as positive. Both equations come from the velocity-time graph: the first from its gradient, the second from the area of the trapezium."),
       ("key", "propriete", "Free fall",
        "When air resistance is small, **all objects fall with the same acceleration** called the **acceleration of free fall, g**. In this course **g = 10 m/s²** (a more exact value is 9.8 m/s²). A dropped object starts with u = 0, so **v = gt**, and the distance fallen is **h = ½ g t²**. In air, a feather falls slower than a stone because of **air resistance**, not because it is lighter."),
       ("fig", fall, "Distance fallen from rest (metres) after 1, 2 and 3 s, with g = 10 m/s².", "A bar chart: 5 m after 1 s, 20 m after 2 s, 45 m after 3 s."),
       ("key", "pieges", "Common mistakes",
        "- Distance fallen is **not** proportional to time: it grows with t², so in the second second the object falls farther than in the first.\n- Weight does not change the acceleration of free fall in a vacuum.\n- Remember u = 0 for 'dropped from rest', but not for 'thrown downward'."),
       ("example", "Dropped from a height", "A stone is dropped from a bridge 20 m above a river (g = 10 m/s²). Find the time to reach the water and the speed on impact.", ["h = ½ g t², so 20 = 5 t² and t² = 4, t = 2 s.", "v = g t = 10 × 2 = 20 m/s."], "2 s; 20 m/s"),
       ("example", "Braking", "A car moving at 24 m/s brakes uniformly to rest in 6 s. Find the braking distance.", ["s = ½(u + v)t = ½ × (24 + 0) × 6.", "= 72 m."], "72 m")],
      [("num", "A ball is dropped from rest. What is its velocity after 3 s? (g = 10 m/s²)", 30, "v = gt = 10 × 3 = 30 m/s.", 0, "m/s"),
       ("num", "A car accelerates from 5 m/s with a = 2 m/s² for 4 s. Find the final velocity.", 13, "v = u + at = 5 + 8 = 13 m/s.", 0, "m/s"),
       ("mcq", "In a vacuum a coin and a feather fall:", "with the same acceleration", ["the coin faster", "the feather faster", "not at all"], "Free fall acceleration is the same for all bodies."),
       ("num", "How far does a stone fall from rest in 2 s? (g = 10 m/s²)", 20, "h = ½ × 10 × 4 = 20 m.", 0, "m"),
       ("num", "A bus travelling at 12 m/s brakes to rest uniformly in 5 s. Find the braking distance.", 30, "s = ½ × (12 + 0) × 5 = 30 m.", 0, "m"),
       ("prob", "A mango falls from a branch 5 m above the ground (g = 10 m/s², ignore air resistance).", [("num", "Time to reach the ground (s).", 1, "5 = 5 t², t = 1 s.", 0.01, "s"), ("num", "Speed just before it hits the ground (m/s).", 10, "v = gt = 10 m/s.", 0, "m/s"), ("open", "Why would a light leaf take longer to fall than the mango from the same branch?", "Air resistance affects the light, broad leaf much more, so it accelerates less; without air all would fall together.", ["1 mark: air resistance", "1 mark: bigger effect on a light/broad leaf"], 2)])],
      [("The acceleration of free fall used in this course is:", "10 m/s²", ["1 m/s²", "100 m/s²", "0 m/s²"], "g = 10 m/s²."),
       ("A stone dropped from rest has velocity after 2 s of:", "20 m/s", ["10 m/s", "5 m/s", "40 m/s"], "v = gt."),
       ("The equation v = u + at is correct for:", "constant acceleration", ["any motion", "motion in circles only", "objects at rest only"], "It assumes uniform acceleration."),
       ("A feather falls slower than a stone in air mainly because of:", "air resistance", ["gravity being weaker", "its colour", "its smell"], "Air drag."),
       ("Distance fallen from rest is proportional to:", "time squared", ["time", "mass", "speed only"], "h = ½gt².")],
      ["g = 10 m/s² is used (rounded); s = ut + ½at² and v² = u² + 2as are left to the later course, avoiding duplication with the Form 5 pack."])

# =========================================================== 3 Forces
ch = p.chapter("forces", "Forces and moments", "Form 3 Physics — Forces, Hooke's law, Newton's laws, moments (to be checked)")
kk = 16 / 8; assert kk == 2
hooke = plot(0, 12, 0, 20, segments=[(0, 0, 8, 16, "blue", False), (8, 16, 12, 19, "red", False)], points=[(8, 16, "limit", "red")], grid=2, xlabel="extension (cm)", ylabel="force (N)", w=420, h=260)
build(ch, "forces-hooke", "Forces, weight, friction and Hooke's law", 25,
      ["Find the resultant of forces acting in a straight line.", "Use W = mg and describe friction.", "State and use Hooke's law."],
      [("key", "definition", "Forces",
        "A **force** is a push or a pull, a vector measured in **newtons (N)**. The **resultant** of forces in a line: add forces in the same direction and subtract opposite ones: 10 N to the right and 4 N to the left give **6 N to the right**. **Weight** W = m × g is the gravitational force on a mass (g = 10 N/kg). **Friction** opposes motion and acts along the surfaces; it is larger on rough surfaces."),
       ("fig", hooke, "Extension of a spring against force: straight line up to the elastic limit.", "A graph of force against extension: a straight line from the origin to 16 N at 8 cm, then a flatter curve beyond the limit."),
       ("key", "propriete", "Hooke's law",
        "Within the **elastic limit**, the **extension of a spring is proportional to the force**: **F = k × x**, where k is the **spring constant** (N/cm or N/m). The graph is a straight line through the origin; its gradient is k. Beyond the **elastic limit** the spring does not return to its original length."),
       ("key", "pieges", "Common mistakes",
        "- Extension is the **increase** in length, not the total length: extension = new length − natural length.\n- Hooke's law does not work beyond the elastic limit.\n- Weight is a force in N; mass is in kg."),
       ("example", "Spring constant", "A spring stretches by 4.0 cm when a force of 8.0 N is applied. Find k, and the extension for 10 N (within the limit).", ["k = F ÷ x = 8.0 ÷ 4.0 = 2.0 N/cm.", "x = F ÷ k = 10 ÷ 2.0 = 5.0 cm."], "k = 2.0 N/cm; extension 5.0 cm"),
       ("example", "Resultant force", "A cart is pushed forward with 120 N while friction pulls back with 45 N. Find the resultant force.", ["The forces are in opposite directions: subtract.", "120 − 45 = 75 N forward."], "75 N forward")],
      [("num", "Find the weight of a 12 kg bag of cement. (g = 10 N/kg)", 120, "12 × 10 = 120 N.", 0, "N"),
       ("num", "A spring constant is 5 N/cm. Find the extension when a force of 15 N is applied.", 3, "x = F ÷ k = 15 ÷ 5 = 3 cm.", 0, "cm"),
       ("mcq", "A force of 30 N to the east and one of 50 N to the west act on a box. The resultant is:", "20 N west", ["80 N west", "20 N east", "80 N east"], "50 − 30 = 20 N in the direction of the larger force."),
       ("num", "A spring of natural length 10 cm extends 2 cm for each 1 N. What is its length under 4 N? (in cm)", 18, "extension = 4 × 2 = 8 cm; length = 10 + 8 = 18 cm.", 0, "cm"),
       ("open", "A student loads a spring with weights and records its extension each time. How can she show that the spring obeys Hooke's law?", "Plot a graph of extension against force. A straight line through the origin shows that extension is proportional to force within the elastic limit.", ["1 mark: graph of extension against force", "1 mark: straight line through the origin"]),
       ("prob", "A spring stretches by 6 cm under a load of 12 N. Within the elastic limit:", [("num", "Find the spring constant (N/cm).", 2, "12 ÷ 6 = 2 N/cm.", 0, "N/cm"), ("num", "Find the extension under a load of 20 N (cm).", 10, "20 ÷ 2 = 10 cm.", 0, "cm"), ("num", "Find the load that would give an extension of 7.5 cm (N).", 15, "2 × 7.5 = 15 N.", 0, "N")])],
      [("Hooke's law states that extension is:", "proportional to the force", ["equal to the mass", "inversely proportional to force", "constant"], "Within the elastic limit."),
       ("The weight of a 3 kg object (g = 10 N/kg) is:", "30 N", ["3 N", "0.3 N", "300 N"], "W = mg."),
       ("The resultant of 8 N and 3 N in opposite directions is:", "5 N", ["11 N", "24 N", "2.7 N"], "Subtract."),
       ("The force of friction between two surfaces points:", "against the motion", ["along the motion", "downwards only", "upwards only"], "It opposes sliding."),
       ("Beyond the elastic limit a spring:", "does not return to its original length", ["snaps always", "becomes lighter", "gains charge"], "Permanent stretch.")],
      ["Spring constant units given in N/cm and N/m: check the form used locally."])

carfig = Draw()
carfig.sh.append(RECT(140, 80, 140, 50, fill="lightorange", width=2)); carfig.sh.append(CIRCLE(170, 135, 12, fill="grey", width=2)); carfig.sh.append(CIRCLE(250, 135, 12, fill="grey", width=2))
carfig.line(60, 105, 138, 105, arrow="start", color="blue", width=3); carfig.text(60, 90, "friction 800 N", size=11, anchor="start")
carfig.line(282, 105, 380, 105, arrow="end", color="red", width=3); carfig.text(330, 92, "engine 3000 N", size=11)
carfig.line(210, 148, 210, 200, arrow="end", color="green", width=3); carfig.text(220, 196, "weight", size=11, anchor="start")
carfig.line(210, 78, 210, 30, arrow="end", color="purple", width=3); carfig.text(220, 40, "reaction of road", size=11, anchor="start")
car_fig = carfig.fig(440, 215)
a_car = (3000 - 800) / 1000; assert abs(a_car - 2.2) < 1e-9 and abs(a_car * 5 - 11) < 1e-9
build(ch, "newton-laws", "Newton's laws of motion", 25,
      ["State Newton's three laws of motion.", "Use F = ma to find force, mass or acceleration.", "Explain inertia and action-reaction pairs in everyday situations."],
      [("key", "propriete", "The three laws",
        "**First law**: an object stays at rest, or moves at constant velocity in a straight line, **unless an unbalanced force acts** on it. This property is **inertia**.\n\n**Second law**: the resultant force produces an acceleration in its own direction: **F = m × a**.\n\n**Third law**: if body A exerts a force on B, then B exerts an **equal and opposite force** on A. The two forces act on **different bodies**."),
       ("fig", car_fig, "Forces on a car: the resultant forward force makes it accelerate.", "A car with arrows: engine force 3000 N forward, friction 800 N backward, weight downward and reaction of the road upward."),
       ("key", "formule", "Using F = ma",
        "**F = ma** with F in newtons, m in kilograms and a in m/s². Then a = F ÷ m and m = F ÷ a. **F is the resultant (net) force**: add up all the forces along the line of motion first. **1 N** is the force that gives a mass of 1 kg an acceleration of 1 m/s²."),
       ("key", "pieges", "Common mistakes",
        "- Use the **resultant** force, not just the engine force; friction must be subtracted.\n- The two forces of the third law act on **different objects**, so they do not cancel each other.\n- An object in motion does not need a continuing force to keep moving at constant velocity."),
       ("example", "Acceleration of a car", "A car of mass 1000 kg has an engine force of 3000 N and a friction force of 800 N. Find its acceleration and its speed after 5 s from rest.", ["Resultant = 3000 − 800 = 2200 N; a = 2200 ÷ 1000 = 2.2 m/s².", "v = u + at = 0 + 2.2 × 5 = 11 m/s."], "a = 2.2 m/s²; v = 11 m/s"),
       ("example", "Inertia in a bus", "Passengers lurch forward when a bus brakes suddenly. Explain, using Newton's first law.", ["Passengers were moving with the bus, and no force acts on them to stop.", "The bus slows but their bodies tend to keep moving forward: inertia."], "Their inertia keeps them moving forward when the bus stops.")],
      [("num", "A force of 200 N acts on a 50 kg trolley. Find its acceleration in m/s².", 4, "a = F ÷ m = 200 ÷ 50 = 4 m/s².", 0, "m/s²"),
       ("num", "What resultant force gives a 1500 kg car an acceleration of 2 m/s²?", 3000, "F = ma = 1500 × 2 = 3000 N.", 0, "N"),
       ("mcq", "Seat belts are used in cars because of:", "inertia", ["friction only", "weight only", "magnetism"], "Passengers continue moving forward when the car stops suddenly."),
       ("num", "A resultant force of 60 N gives an object an acceleration of 3 m/s². Find its mass in kg.", 20, "m = F ÷ a = 60 ÷ 3 = 20 kg.", 0, "kg"),
       ("open", "A boy pushes a wall with a force of 50 N. State the force that the wall exerts on him and explain why neither force cancels the other.", "The wall pushes back with 50 N in the opposite direction (third law). The two forces act on different objects (the wall and the boy) so they cannot cancel.", ["1 mark: 50 N opposite", "1 mark: act on different bodies"]),
       ("prob", "A motorbike and rider have a mass of 250 kg. The engine force is 900 N and the resistive forces total 400 N.", [("num", "Resultant force (N).", 500, "900 − 400 = 500 N.", 0, "N"), ("num", "Acceleration (m/s²).", 2, "500 ÷ 250 = 2 m/s².", 0, "m/s²"), ("num", "Speed after 6 s from rest (m/s).", 12, "v = 2 × 6 = 12 m/s.", 0, "m/s")])],
      [("Newton's second law is written:", "F = ma", ["F = m + a", "F = m ÷ a", "F = a ÷ m"], "Resultant force equals mass times acceleration."),
       ("Inertia is the tendency of a body to:", "resist a change in its motion", ["fall faster", "melt", "float"], "First law."),
       ("An object moving at constant velocity has a resultant force of:", "zero", ["very large", "equal to its weight", "negative"], "No acceleration means no resultant force."),
       ("The unit of force is the newton, equal to:", "1 kg m/s²", ["1 kg/m", "1 m/s", "1 kg"], "F = ma."),
       ("The action and reaction forces act on:", "different bodies", ["the same body", "no body", "the ground only"], "Third law.")],
      ["Newton's laws are stated in a simplified student form; check the wording used by the syllabus."])

mom = Draw()
mom.line(40, 120, 400, 120, width=6, color="brown")
mom.sh.append(POLY([(220, 120), (205, 150), (235, 150)], fill="grey", width=2))
mom.line(120, 70, 120, 116, arrow="end", color="blue", width=3); mom.text(120, 62, "40 N", size=12)
mom.line(270, 70, 270, 116, arrow="end", color="red", width=3); mom.text(270, 62, "F = ?", size=12)
mom.line(120, 175, 218, 175, arrow="both", width=1.5); mom.text(170, 192, "0.50 m", size=11)
mom.line(222, 175, 270, 175, arrow="both", width=1.5); mom.text(250, 192, "0.25 m", size=11, anchor="middle")
mom.text(220, 168, "pivot", size=10, anchor="middle")
mom_fig = mom.fig(440, 205)
F = 40 * 0.50 / 0.25; assert F == 80
load = 60 * 1.2 / 0.3; assert abs(load - 240) < 1e-9
build(ch, "moments", "Moments and equilibrium", 25,
      ["Define the moment of a force and calculate it.", "State and use the principle of moments.", "State the conditions for equilibrium."],
      [("key", "formule", "Moment of a force",
        "The **moment** of a force about a pivot is its turning effect: **moment = force × perpendicular distance from the pivot**, unit **newton metre (N m)**. A longer spanner or a door handle far from the hinges needs a smaller force for the same moment. Moments can be **clockwise** or **anticlockwise**."),
       ("fig", mom_fig, "A balanced beam: 40 N at 0.50 m on the left and an unknown force F at 0.25 m on the right.", "A beam resting on a pivot with a 40 N weight 0.50 m to the left and a force F 0.25 m to the right."),
       ("key", "propriete", "Principle of moments and equilibrium",
        "For a body in **equilibrium** (balanced): **sum of clockwise moments = sum of anticlockwise moments** about any pivot, and **total upward force = total downward force**. The **centre of gravity** is the point where the whole weight seems to act; a uniform rod's is at its middle."),
       ("key", "pieges", "Common mistakes",
        "- Use the **perpendicular** distance from the pivot to the line of the force, not the length of the beam.\n- Remember the pivot supports the beam: its upward reaction equals all the downward forces.\n- Convert cm to m before multiplying to get N m."),
       ("example", "Balanced beam", "On a beam balanced at its pivot, a 40 N weight is 0.50 m to the left and a force F is 0.25 m to the right. Find F and the force on the pivot (beam weight ignored).", ["Anticlockwise = 40 × 0.50 = 20 N m. At balance 0.25 × F = 20, so F = 80 N.", "Reaction at the pivot = 40 + 80 = 120 N upward."], "F = 80 N; pivot force 120 N"),
       ("example", "Crowbar", "A crowbar of effort arm 1.2 m and load arm 0.3 m is used. An effort of 60 N is applied. What load can it just lift?", ["Effort moment = 60 × 1.2 = 72 N m.", "Load × 0.3 = 72, so load = 240 N."], "240 N")],
      [("num", "Find the moment of a 25 N force acting at a perpendicular distance of 0.40 m from a pivot (N m).", 10, "25 × 0.40 = 10 N m.", 0, "N m"),
       ("num", "A 30 N child sits 1.5 m from the pivot of a seesaw. How far from the pivot must a 45 N child sit on the other side to balance it? (m)", 1.0, "30 × 1.5 = 45; d = 45 ÷ 45 = 1.0 m.", 0.01, "m"),
       ("mcq", "To loosen a tight nut more easily you should:", "use a longer spanner", ["use a shorter spanner", "use a lighter spanner", "heat the spanner"], "A longer arm gives a larger moment for the same force."),
       ("num", "A uniform metre rule balances on a pivot at its 50 cm mark with a 2 N weight at the 20 cm mark. A weight W is hung at the 80 cm mark. Find W (N).", 2, "Both are 30 cm from the pivot; equal moments mean W = 2 N.", 0.01, "N"),
       ("open", "State the two conditions for a body to be in equilibrium.", "The total upward force equals the total downward force, and the clockwise moments equal the anticlockwise moments about any point.", ["1 mark: forces balance", "1 mark: moments balance"]),
       ("prob", "A uniform plank of weight 200 N and length 4.0 m rests on a support at its middle. A child of weight 300 N sits 1.5 m to the left of the support. Where should an adult of weight 450 N sit on the right to balance the plank?", [("num", "Moment of the child about the support (N m).", 450, "300 × 1.5 = 450 N m.", 0, "N m"), ("num", "Distance of the adult from the support (m).", 1.0, "450 ÷ 450 = 1.0 m.", 0.01, "m"), ("num", "Upward force of the support (N). Weights: plank 200, child 300, adult 450.", 950, "200 + 300 + 450 = 950 N.", 0, "N")])],
      [("The moment of a force is:", "force × perpendicular distance", ["force + distance", "force ÷ distance", "mass × speed"], "Turning effect."),
       ("The unit of moment is the:", "newton metre", ["newton", "joule per second", "metre"], "N m."),
       ("At equilibrium the clockwise moments are:", "equal to the anticlockwise moments", ["zero", "larger", "smaller"], "Principle of moments."),
       ("The centre of gravity of a uniform rod is at its:", "middle", ["end", "quarter point", "pivot only"], "Symmetry."),
       ("A door is easier to open when pushed:", "far from the hinges", ["near the hinges", "at the hinges", "from behind"], "Larger distance, larger moment.")],
      ["N m (moment) and J (energy) have the same base units but are different quantities: stated in text only if the syllabus requires it."])

# =========================================================== 4 Pressure
ch = p.chapter("pressure", "Pressure", "Form 3 Physics — Pressure in solids, liquids and the atmosphere (to be checked)")
p_shoe = 600 / 0.03; assert abs(p_shoe - 20000) < 1e-6
p_dep = 1000 * 10 * 5; assert p_dep == 50000
p_hyd = 100 / 0.002 * 0.04; assert abs(p_hyd - 2000) < 1e-9
def jets_fig():
    d = Draw()
    d.sh.append(RECT(60, 20, 160, 190, fill="lightblue", width=3))
    d.sh.append(RECT(63, 17, 154, 8, fill="white", stroke="none", width=0))
    for y, ln, txt in ((60, 40, "least pressure"), (110, 80, "more pressure"), (160, 120, "greatest pressure")):
        d.sh.append(RECT(216, y - 4, 8, 8, fill="white", stroke="none", width=0))
        d.line(224, y, 224 + ln, y, arrow="end", color="blue", width=3); d.text(224 + ln + 6, y + 4, txt, size=11, anchor="start")
    d.text(140, 120, "water", size=13)
    d.line(40, 25, 40, 205, arrow="end", color="grey", width=1.5); d.text(30, 118, "depth", size=11, anchor="end")
    return d.fig(440, 225)
build(ch, "pressure", "Pressure in solids, liquids and the atmosphere", 30,
      ["Define pressure and calculate it with P = F / A.", "Calculate the pressure at a depth in a liquid with P = ρgh.", "State atmospheric pressure and describe a simple hydraulic system."],
      [("key", "formule", "Pressure",
        "**Pressure = force ÷ area**, **P = F / A**. The SI unit is the **pascal (Pa) = 1 N/m²**. The same force on a **smaller area** gives a **bigger pressure**: a knife is sharp, a nail is pointed, tractors have wide tyres and snow shoes spread the weight. Force is the weight (N) when an object rests on a surface."),
       ("key", "formule", "Pressure in a liquid",
        "Pressure in a liquid **increases with depth** and acts equally in **all directions**; it is the same at the same depth. **P = ρ × g × h** (ρ = density in kg/m³, g = 10 N/kg, h = depth in m). For water at 5 m depth: 1000 × 10 × 5 = **50 000 Pa**. This is why a dam wall is thicker at the bottom."),
       ("fig", jets_fig(), "Water jets from a tank: the deeper the hole, the stronger the jet.", "A tank of water with three holes in its side; the jet from the lowest hole travels farthest, the top one least."),
       ("key", "retenir", "Atmospheric pressure and hydraulics",
        "The air above us exerts **atmospheric pressure**, about **100 000 Pa (10⁵ Pa)** at sea level; it is lower at high altitude (for example on Mount Cameroon). A **barometer** measures it (a mercury barometer reads about 760 mm). In a **hydraulic system** pressure in a liquid is passed on equally: F₂ = F₁ × A₂ ÷ A₁, so a small force on a small piston gives a large force on a large piston."),
       ("key", "pieges", "Common mistakes",
        "- Use the **area in contact**, in m² (1 m² = 10 000 cm²).\n- Liquid pressure depends on **depth** and density, not on the shape or the width of the container.\n- A hydraulic press does not give extra energy: the small piston moves farther."),
       ("example", "Pressure under shoes", "A student weighs 600 N and the area of her shoe soles in contact with the ground is 0.030 m². Find the pressure.", ["P = F ÷ A.", "600 ÷ 0.030 = 20 000 Pa."], "20 000 Pa"),
       ("example", "Pressure at a depth", "A diver is 5.0 m below the surface of a freshwater lake (ρ = 1000 kg/m³, g = 10 N/kg). Find the pressure caused by the water.", ["P = ρ g h.", "1000 × 10 × 5.0 = 50 000 Pa."], "50 000 Pa")],
      [("num", "A force of 200 N acts on an area of 0.5 m². Find the pressure in Pa.", 400, "200 ÷ 0.5 = 400 Pa.", 0, "Pa"),
       ("num", "Find the pressure at a depth of 2 m in water. (ρ = 1000 kg/m³, g = 10 N/kg)", 20000, "1000 × 10 × 2 = 20 000 Pa.", 0, "Pa"),
       ("mcq", "Why do camels have wide feet?", "to reduce pressure on sand", ["to increase pressure on sand", "to make them taller", "to keep them warm"], "A larger area gives a smaller pressure."),
       ("num", "A brick of weight 40 N rests on its face of area 0.02 m². Find the pressure in Pa.", 2000, "40 ÷ 0.02 = 2000 Pa.", 0, "Pa"),
       ("open", "Explain why the base of a dam wall is built much thicker than the top.", "Water pressure increases with depth, so the water pushes much harder on the lower part of the wall; a thicker base can withstand the larger force.", ["1 mark: pressure increases with depth", "1 mark: thicker wall resists larger force"]),
       ("prob", "In a hydraulic jack the small piston has an area of 0.002 m² and the large piston 0.04 m². A force of 100 N is applied to the small piston.", [("num", "Pressure in the liquid (Pa).", 50000, "100 ÷ 0.002 = 50 000 Pa.", 0, "Pa"), ("num", "Force on the large piston (N).", 2000, "50 000 × 0.04 = 2000 N.", 0, "N"), ("mcq", "To raise the load 1 cm the small piston must move:", "20 cm", ["1 cm", "2 cm", "0.05 cm"], "The area ratio is 20, so the distance ratio is also 20.")])],
      [("The unit of pressure is the:", "pascal", ["newton", "joule", "watt"], "Pa = N/m²."),
       ("Pressure equals:", "force ÷ area", ["force × area", "area ÷ force", "mass × area"], "P = F/A."),
       ("Pressure in a liquid increases with:", "depth", ["width of container", "colour", "time"], "P = ρgh."),
       ("Atmospheric pressure at sea level is about:", "100 000 Pa", ["10 Pa", "1000 Pa", "100 Pa"], "About 10⁵ Pa."),
       ("A sharp knife cuts easily because its edge has a:", "small area giving a large pressure", ["large area", "large mass", "low density"], "Small area.")],
      ["Atmospheric pressure quoted as about 10⁵ Pa (standard value 101 325 Pa); the hydraulic jack uses simple numbers."])

# =========================================================== 5 Work, energy and machines
ch = p.chapter("energy", "Work, energy, power and machines", "Form 3 Physics — Work, energy, power, simple machines (to be checked)")
ball = bars([("PE top", 100, "orange"), ("PE half", 50, "orange"), ("KE half", 50, "red"), ("KE end", 100, "red")], w=420, h=250)
W1 = 500 * 2; P1 = W1 / 5; KE1 = 0.5 * 1000 * 20 ** 2; PE1 = 2 * 10 * 5; v_ball = math.sqrt(2 * 10 * 5)
assert W1 == 1000 and P1 == 200 and KE1 == 200000 and PE1 == 100 and abs(v_ball - 10) < 1e-9 and 0.5 * 2 * 10 ** 2 == 100
build(ch, "work-energy-power", "Work, energy and power", 30,
      ["Calculate work done, kinetic energy and potential energy.", "State the principle of conservation of energy and apply it to a falling object.", "Calculate power."],
      [("key", "formule", "Work and power",
        "**Work done = force × distance moved in the direction of the force**, **W = F × d**. The unit is the **joule (J)**: 1 J = 1 N m. **Power** is the rate of doing work: **P = W ÷ t**, unit the **watt (W)** = 1 J/s. A 100 W bulb transfers 100 J of energy each second."),
       ("key", "formule", "Kinetic and potential energy",
        "**Kinetic energy** (moving): **KE = ½ m v²**. **Gravitational potential energy** (height): **PE = m g h** (g = 10 N/kg). Energy is measured in joules. Doing work on an object transfers energy to it: work done = energy transferred."),
       ("fig", ball, "A 2 kg ball dropped from 5 m: potential energy changes to kinetic energy (joules).", "A bar chart: potential energy 100 J at the top and 50 J half-way, kinetic energy 50 J half-way and 100 J at the end."),
       ("key", "propriete", "Conservation of energy",
        "Energy is **never created or destroyed**, only changed from one form to another: **the total energy stays the same** (when no energy is lost as heat by friction). For a ball falling freely, PE lost = KE gained: **m g h = ½ m v²**, so **v = √(2 g h)**. Some energy is always spread as heat, which is why machines are less than 100 % efficient."),
       ("key", "pieges", "Common mistakes",
        "- No work is done if there is no movement, e.g. pushing a wall that does not move.\n- Use the weight (N), not the mass, in W = F × d when lifting; weight = m × 10.\n- In KE = ½ m v² the speed is **squared**: doubling the speed multiplies KE by 4."),
       ("example", "Work and power in lifting", "A porter lifts a 50 kg bag steadily through 2 m in 5 s (g = 10 N/kg). Find the work done and the power.", ["Force = weight = 50 × 10 = 500 N; W = 500 × 2 = 1000 J.", "P = W ÷ t = 1000 ÷ 5 = 200 W."], "1000 J; 200 W"),
       ("example", "Speed of a falling ball", "A 2 kg ball is dropped from 5 m. Find its speed just before it hits the ground (ignore air resistance).", ["PE at the top = 2 × 10 × 5 = 100 J, all becomes KE: ½ × 2 × v² = 100.", "v² = 100, so v = 10 m/s."], "10 m/s")],
      [("num", "A force of 40 N moves a box 6 m along the floor. Find the work done in J.", 240, "40 × 6 = 240 J.", 0, "J"),
       ("num", "Find the kinetic energy of a 1000 kg car moving at 20 m/s (J).", 200000, "½ × 1000 × 400 = 200 000 J.", 0, "J"),
       ("num", "A machine does 3000 J of work in 20 s. Find its power in W.", 150, "3000 ÷ 20 = 150 W.", 0, "W"),
       ("num", "Find the potential energy gained by a 4 kg object raised 3 m. (g = 10 N/kg)", 120, "mgh = 4 × 10 × 3 = 120 J.", 0, "J"),
       ("open", "A student says: 'I held a heavy box for one minute so I did a lot of work.' Is she right? Explain.", "No. Work = force × distance moved; the box did not move, so no work was done on it in the physics sense, although her muscles used energy.", ["1 mark: no work if no movement", "1 mark: uses W = F × d"]),
       ("prob", "A 0.5 kg stone is thrown vertically upward at 20 m/s (g = 10 m/s², ignore air resistance).", [("num", "Initial kinetic energy (J).", 100, "½ × 0.5 × 20² = 100 J.", 0, "J"), ("num", "Maximum height reached (m), where all KE has become PE.", 20, "mgh = 100, so h = 100 ÷ (0.5 × 10) = 20 m.", 0, "m"), ("num", "Kinetic energy at the top (J).", 0, "The stone is momentarily at rest.", 0, "J")])],
      [("The joule is the unit used to measure:", "energy", ["power", "force", "pressure"], "Energy and work are in J."),
       ("Power is calculated by:", "work ÷ time", ["work × time", "force × time", "mass × speed"], "P = W/t."),
       ("Kinetic energy is:", "½mv²", ["mgh", "mv", "Fd"], "Energy of motion."),
       ("When an object falls freely its potential energy:", "changes into kinetic energy", ["is destroyed", "doubles", "becomes mass"], "Conservation of energy."),
       ("A power of 60 W means:", "60 J of energy transferred per second", ["60 J in a minute", "60 N of force", "60 m of height"], "Watt = joule per second.")],
      ["g = 10 N/kg is used; efficiency and machines follow in the next lesson."])

lev = Draw()
lev.line(50, 120, 410, 120, width=6, color="brown")
lev.sh.append(POLY([(160, 120), (145, 150), (175, 150)], fill="grey", width=2))
lev.line(90, 70, 90, 116, arrow="end", color="blue", width=3); lev.text(90, 60, "load", size=12)
lev.line(370, 70, 370, 116, arrow="end", color="red", width=3); lev.text(370, 60, "effort", size=12)
lev.text(160, 170, "pivot (fulcrum)", size=11)
lev_fig = lev.fig(440, 185)
MA1 = 400 / 150; VR1 = 4; eff1 = MA1 / VR1 * 100
assert abs(MA1 - 2.6667) < 1e-3 and abs(eff1 - 66.667) < 1e-2
MA2 = 600 / 300; VR2 = 3 / 1; eff2 = MA2 / VR2 * 100; assert abs(eff2 - 66.667) < 1e-2
build(ch, "machines", "Simple machines and efficiency", 30,
      ["Define a machine, mechanical advantage, velocity ratio and efficiency.", "Name the three classes of lever and give an example of each.", "Calculate MA, VR and efficiency for levers, pulleys and inclined planes."],
      [("key", "definition", "Machines",
        "A **machine** makes work easier by changing the size or direction of a force (it does not give extra energy). Simple machines: **lever**, **pulley**, **inclined plane (ramp)**, **wheel and axle**, **screw**, **wedge**. The **load** is the force to overcome and the **effort** is the force applied."),
       ("fig", lev_fig, "A lever: load and effort on either side of the pivot.", "A beam on a pivot with a load arrow downward on the left and an effort arrow downward on the right."),
       ("key", "formule", "MA, VR and efficiency",
        "**Mechanical advantage MA = load ÷ effort** (no unit). **Velocity ratio VR = distance moved by the effort ÷ distance moved by the load** (for a ramp: length of the slope ÷ height; for a pulley system: the number of supporting ropes). **Efficiency = (work output ÷ work input) × 100 % = (MA ÷ VR) × 100 %**. Efficiency is always **below 100 %** because of friction."),
       ("key", "retenir", "Classes of lever",
        "**Class 1**: pivot between load and effort (seesaw, scissors, crowbar). **Class 2**: load between pivot and effort (wheelbarrow, nutcracker). **Class 3**: effort between pivot and load (tweezers, a person's forearm, fishing rod). Class 2 levers always have MA greater than 1; class 3 levers have MA less than 1 but give speed and range."),
       ("key", "pieges", "Common mistakes",
        "- A machine cannot have an efficiency over 100 %: that would create energy.\n- MA has **no unit**; VR also has none.\n- A big MA means a small effort but a **long** distance for the effort."),
       ("example", "Pulley system", "A pulley system with 4 supporting ropes raises a 400 N load with an effort of 150 N. Find MA, VR and the efficiency.", ["MA = 400 ÷ 150 = 2.67; VR = 4.", "Efficiency = (2.67 ÷ 4) × 100 = 66.7 %."], "MA 2.67; VR 4; efficiency 66.7 %"),
       ("example", "Ramp", "A 600 N barrel is pushed up a ramp 3 m long and 1 m high with an effort of 300 N. Find MA, VR and efficiency.", ["MA = 600 ÷ 300 = 2; VR = 3 ÷ 1 = 3.", "Efficiency = (2 ÷ 3) × 100 = 66.7 %."], "MA 2; VR 3; efficiency 66.7 %")],
      [("num", "A machine lifts a load of 900 N using an effort of 300 N. Find its mechanical advantage.", 3, "MA = 900 ÷ 300 = 3.", 0),
       ("num", "A pulley system has MA 3 and VR 4. Find the efficiency in %.", 75, "(3 ÷ 4) × 100 = 75 %.", 0, "%"),
       ("mcq", "A wheelbarrow is a lever of which class?", "class 2", ["class 1", "class 3", "it is not a lever"], "The load is between the pivot (wheel) and the effort."),
       ("num", "A ramp is 5 m long and 1 m high. What is its velocity ratio?", 5, "VR = 5 ÷ 1 = 5.", 0),
       ("open", "Why is the efficiency of every real machine less than 100 %?", "Some of the input energy is used to overcome friction between moving parts and is transferred as heat, so the useful output work is less than the input work.", ["1 mark: friction", "1 mark: energy wasted as heat"]),
       ("prob", "A lever raises a 240 N load 0.10 m when an effort of 100 N moves 0.30 m.", [("num", "Work done on the load (J).", 24, "240 × 0.10 = 24 J.", 0, "J"), ("num", "Work done by the effort (J).", 30, "100 × 0.30 = 30 J.", 0, "J"), ("num", "Efficiency in %.", 80, "24 ÷ 30 × 100 = 80 %.", 0, "%")])],
      [("Mechanical advantage is:", "load ÷ effort", ["effort ÷ load", "load × effort", "effort − load"], "MA = L/E."),
       ("The efficiency of a machine can never be:", "more than 100 %", ["60 %", "less than 100 %", "80 %"], "That would create energy."),
       ("Scissors are an example of a lever of class:", "1", ["2", "3", "0"], "Pivot between effort and load."),
       ("The VR of a ramp 4 m long and 1 m high is:", "4", ["1", "0.25", "5"], "Length ÷ height."),
       ("A machine makes work easier by:", "changing the size or direction of the force", ["creating energy", "removing friction completely", "reducing the load's weight"], "It cannot create energy.")],
      ["For pulley systems VR = number of supporting ropes (ideal model); check the syllabus definition of efficiency (output/input work)."])

p.write()
