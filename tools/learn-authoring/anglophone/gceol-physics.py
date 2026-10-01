"""Generator for content/learn/gceol-physics (GCE O Level Physics, Form 5, English). Run: python3 gceol-physics.py"""
import sys, math
sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring")
from lessonlib import *

G = 10  # m/s^2, used throughout and stated in the lessons

def r(x, n=2): return round(x, n)
def close(a, b, t=1e-9): assert abs(a - b) <= t, (a, b)

p = Pack("gceol-physics", "Physics — GCE O Level", level="Form 5", subject="physics", cursus="secondary", exam="GCE-OL",
         description="Key-point summaries, worked calculations, GCE O Level-style exercises with answers and a mock exam: measurement, motion, forces, energy, heat, waves, light, electricity, magnetism and atomic physics.",
         programRef="Cameroon GCE Board — Ordinary Level Physics syllabus (Forms 4–5) — to be checked against the official texts")

# ---- circuit drawing helpers (coordinates in a 400x240 frame)
def battery(x, y, label="12 V"):
    """vertical battery symbol centred at (x,y): long thin plate on top (+), short thick below (-)"""
    return [LINE(x - 14, y - 5, x + 14, y - 5, width=2), LINE(x - 8, y + 5, x + 8, y + 5, width=5)]

def resistor(x, y, w=40, h=16): return [RECT(x - w // 2, y - h // 2, w, h)]

def bulb(x, y, rr=14): return [CIRCLE(x, y, rr), LINE(x - 10, y - 10, x + 10, y + 10, width=1), LINE(x - 10, y + 10, x + 10, y - 10, width=1)]

def meter(x, y, letter, rr=13): return [CIRCLE(x, y, rr), T(x, y + 5, letter, size=14, bold=True)]

_POLY = POLY
def POLY(pts, **kw):
    """accept [[x,y],...] or flat list"""
    flat = [c for q in pts for c in (q if isinstance(q, (list, tuple)) else [q])]
    return _POLY(flat, **kw)

# =====================================================================  1. MEASUREMENT
ch = p.chapter("measurement", "Measurement and units", programRef="GCE O Level Physics — Measurement: SI units, instruments, density basics (to be checked against the official syllabus)")
L = ch.lesson("units-instruments", "Units, prefixes and measuring instruments", minutes=25,
    objectives=["Name the SI units of length, mass, time, temperature and current.", "Convert between units using prefixes (kilo, centi, milli, micro), including area and volume.",
                "Choose and read a ruler, measuring cylinder, vernier calipers, micrometer and stopwatch.", "Find the period of a pendulum accurately by timing many swings."],
    notes=["Vernier calipers and micrometer precisions (0.1 mm and 0.01 mm) are the usual school values; check which instruments the Board expects pupils to read.",
           "Whether the syllabus requires reading a vernier scale in a written paper is to be confirmed."])
L.text("Every measurement has a **number** and a **unit**. Scientists use the **SI system**. The base units we need are: length in **metre (m)**, mass in **kilogram (kg)**, time in **second (s)**, electric current in **ampere (A)** and temperature in **kelvin (K)** (in school we often use degrees Celsius, °C).")
L.key("definition", "Prefixes", "A prefix is a multiplier placed before a unit.\n\n- **kilo (k)** = 1000 = $10^3$\n- **centi (c)** = 1/100 = $10^{-2}$\n- **milli (m)** = 1/1000 = $10^{-3}$\n- **micro (µ)** = 1/1 000 000 = $10^{-6}$\n\nExample: 1 km = 1000 m and 1 mm = 0.001 m.")
L.key("pieges", "Area and volume conversions", "A length factor must be **squared** for area and **cubed** for volume.\n\n- 1 m = 100 cm, so 1 m² = 100 × 100 = 10 000 cm² and 1 m³ = 1 000 000 cm³.\n- 1 litre = 1000 cm³ = 0.001 m³ (so 1 cm³ = 1 millilitre).\n\nA very common mistake is to write 1 m² = 100 cm².")
L.illustration(shapes([RECT(40, 60, 50, 150), RECT(41, 130, 48, 79, fill="blue"), T(65, 45, "Before", 14),
                       RECT(200, 60, 50, 150), RECT(201, 110, 48, 99, fill="blue"), CIRCLE(225, 175, 14, fill="orange"), T(225, 45, "After", 14),
                       LINE(100, 130, 135, 130, dash=True), T(160, 135, "40 cm³", 14), LINE(260, 110, 295, 110, dash=True), T(325, 115, "60 cm³", 14)], 400, 240),
    "Volume of an irregular stone by displacement: the water level rises from 40 cm³ to 60 cm³.", "A measuring cylinder with water at 40 cm³ before and 60 cm³ after a stone is lowered in, so the stone has volume 20 cm³.")
L.key("methode", "Measuring accurately", "- **Measuring cylinder**: read at the bottom of the curved surface (meniscus) with your eye level with it.\n- **Irregular solid**: volume = final reading − initial reading.\n- **Vernier calipers / micrometer**: for small lengths (about 0.1 mm and 0.01 mm precision); check for a zero error first.\n- **Timing**: time many events (e.g. 20 swings) and divide, which reduces the reaction-time error.")
v1 = 5 * 4 * 3; assert v1 == 60
close(v1 * 1e-6, 6e-5)
L.example("Example 1 — volume of a block", "A wooden block measures 5 cm by 4 cm by 3 cm. Find its volume in cm³ and in m³.",
    ["Volume = length × width × height = 5 × 4 × 3 = 60 cm³.", "1 cm³ = 10⁻⁶ m³, so 60 cm³ = 60 × 10⁻⁶ m³.", "60 × 10⁻⁶ = 6 × 10⁻⁵ m³."], "60 cm³ = 6 × 10⁻⁵ m³")
T20 = 36.0; per = T20 / 20; fr = 1 / per
L.example("Example 2 — pendulum", "A pupil in Bamenda times 20 complete swings of a pendulum and records 36 s. Find the period and the frequency.",
    ["Period T = total time ÷ number of swings = 36 ÷ 20 = 1.8 s.", "Frequency f = 1 ÷ T = 1 ÷ 1.8 = 0.56 Hz (to 2 significant figures)."], "T = 1.8 s, f ≈ 0.56 Hz")
assert r(fr, 2) == 0.56
L.num("Convert 3.5 km into metres.", 3500, "3.5 × 1000 = 3500 m.", unit="m")
L.mcq("How many cm³ are there in 2 litres?", "2000", ["200", "20", "20 000"], "1 litre = 1000 cm³, so 2 litres = 2000 cm³.")
L.tf("1 m² is equal to 100 cm².", False, "1 m² = 100 cm × 100 cm = 10 000 cm². Area conversion factors are squared.")
L.num("A stone is lowered into a measuring cylinder. The water level rises from 35 cm³ to 52 cm³. Find the volume of the stone.", 17, "52 − 35 = 17 cm³.", unit="cm³", tier="approfondissement")
L.mcq("A pupil measures a short time of 0.4 s with a stopwatch and gets a large percentage error. What is the best improvement?", "Time several repeats together and divide", ["Use a bigger stopwatch", "Take only one reading", "Press the button harder"],
      "Reaction time (about 0.2 s) is a fixed error; spreading it over many events makes its effect small.", tier="approfondissement", difficulty=2)
n_osc = 30; t_tot = 54.0
L.problem("In an experiment a pendulum makes 30 swings in 54 s.", [
    part_num("Find the period in seconds.", t_tot / n_osc, "T = 54 ÷ 30 = 1.8 s.", tolerance=0.01, unit="s"),
    part_num("Find the frequency in hertz (to 2 decimal places).", r(n_osc / t_tot, 2), "f = 30 ÷ 54 = 0.56 Hz.", tolerance=0.01, unit="Hz"),
    part_open("Give one reason why timing 30 swings gives a better period than timing 1 swing.", "The human reaction time error at the start and stop is the same size whatever the total time, so it is a much smaller fraction of 54 s than of 1.8 s.", "1 mark: reaction time / human error; 1 mark: smaller percentage error for the longer time.")],
    tier="examen", figure=None)
L.sc("Which is the SI unit of mass?", "kilogram", ["gram", "newton", "tonne"], "The SI base unit of mass is the kilogram.")
L.sc("0.002 m equals", "2 mm", ["2 cm", "20 mm", "0.2 mm"], "0.002 m = 2 × 10⁻³ m = 2 mm.")
L.sc("1 m³ equals", "1 000 000 cm³", ["100 cm³", "1000 cm³", "10 000 cm³"], "Cube the factor: 100³ = 1 000 000.")
L.sc("Where do you read a water level in a measuring cylinder?", "At the bottom of the meniscus, with the eye level", ["At the top of the meniscus from above", "At the glass edge", "At the top of the cylinder"], "Reading at the bottom of the meniscus at eye level avoids parallax error.")
L.sc("The period of a pendulum is 2 s. Its frequency is", "0.5 Hz", ["2 Hz", "4 Hz", "0.2 Hz"], "f = 1/T = 1/2 = 0.5 Hz.")
# ex-list bug guard: nothing

# =====================================================================  2. MOTION
ch = p.chapter("motion", "Motion in a straight line", programRef="GCE O Level Physics — Kinematics: speed, velocity, acceleration, graphs, equations of motion (to be checked against the official syllabus)")
L = ch.lesson("speed-graphs", "Speed, velocity and motion graphs", minutes=25, prerequisites=["gceol-physics-units-instruments"],
    objectives=["Define speed, velocity, distance and displacement.", "Calculate average speed and convert between km/h and m/s.", "Read distance-time and speed-time graphs: gradient and area.", "Find the distance travelled from the area under a speed-time graph."],
    notes=["Distances between towns used as examples are illustrative round numbers, not exact road distances."])
L.key("definition", "Speed and velocity", "**Speed** = distance ÷ time. **Velocity** is speed in a stated direction (a vector); **distance** is the length of path, **displacement** is the straight-line change of position with direction.")
L.formula(r"\text{average speed} = \frac{\text{total distance}}{\text{total time}}", "Units: m/s (or km/h)")
L.key("retenir", "Reading graphs", "- **Distance-time graph**: gradient = speed. Flat line = at rest. Steeper = faster.\n- **Speed-time graph**: gradient = acceleration; a flat line = constant speed.\n- **Area under a speed-time graph** = distance travelled.\n- To convert: km/h ÷ 3.6 = m/s (and m/s × 3.6 = km/h).")
L.key("pieges", "Average speed is not the average of speeds", "Average speed = **total** distance ÷ **total** time. If you travel 60 km/h for one hour and 40 km/h for another hour the average is 50 km/h, but if you travel the same *distance* at two speeds the average is **not** the middle value.")
segs = [(0, 0, 10, 20, "blue", False), (10, 20, 30, 20, "blue", False), (30, 20, 40, 0, "blue", False)]
L.illustration(plot(0, 40, 0, 25, segments=segs, points=[(10, 20, "A", "red"), (30, 20, "B", "red")], grid=5, xlabel="time (s)", ylabel="speed (m/s)", w=400, h=260),
    "Speed-time graph of a taxi: speeding up, steady, then braking.", "A trapezoid-shaped speed-time graph rising from 0 to 20 m/s in 10 s, flat until 30 s, and falling to 0 at 40 s.")
sp = 150 / 2.5; sp_ms = sp / 3.6
L.example("Example 1 — average speed", "A bus travels 150 km in 2.5 hours. Find its average speed in km/h and in m/s.", ["Speed = distance ÷ time = 150 ÷ 2.5 = 60 km/h.", "Convert: 60 ÷ 3.6 = 16.67 m/s."], "60 km/h ≈ 16.7 m/s")
assert sp == 60 and r(sp_ms, 1) == 16.7
area = 0.5 * (40 + 20) * 20; avg = area / 40; assert area == 600 and avg == 15
acc1 = 20 / 10
L.example("Example 2 — area under the graph", "The graph shows a taxi's speed: 0 to 20 m/s in 10 s, steady until 30 s, then 20 m/s to 0 by 40 s. Find the total distance and the average speed.", ["The shape is a trapezium: parallel sides 40 s (base) and 20 s (top), height 20 m/s.", "Area = ½ × (40 + 20) × 20 = 600 m, so the distance is 600 m.", "Average speed = 600 ÷ 40 = 15 m/s."], "600 m; 15 m/s")
L.num("A cyclist covers 90 m in 15 s. Find the speed in m/s.", 90 / 15, "90 ÷ 15 = 6 m/s.", unit="m/s")
L.num("Convert 108 km/h into m/s.", 108 / 3.6, "108 ÷ 3.6 = 30 m/s.", unit="m/s", tolerance=0.01)
L.mcq("On a distance-time graph, a horizontal line means the object is", "at rest", ["moving at constant speed", "accelerating", "moving backwards"], "Distance does not change with time, so the object is stationary.")
L.num("A runner does the first 100 m in 20 s and the next 100 m in 25 s. Find the average speed over the 200 m (to 2 d.p.).", r(200 / 45, 2), "Total distance 200 m, total time 45 s, so 200 ÷ 45 = 4.44 m/s.", tolerance=0.01, unit="m/s", tier="approfondissement", difficulty=2)
L.mcq("A speed-time graph is a horizontal line at 8 m/s from t = 0 to t = 5 s. The distance travelled is", "40 m", ["13 m", "1.6 m", "8 m"], "Area of the rectangle = 8 × 5 = 40 m.", tier="approfondissement")
dist_ac = 0.5 * 10 * 20
L.problem("A minibus starts from rest, accelerates uniformly to 20 m/s in 10 s, travels at 20 m/s for 20 s and then stops uniformly in 10 s (the graph of Example 2).", [
    part_num("Find the acceleration during the first 10 s.", acc1, "a = change in speed ÷ time = 20 ÷ 10 = 2 m/s².", unit="m/s²"),
    part_num("Find the distance travelled in the first 10 s.", dist_ac, "Area of a triangle = ½ × 10 × 20 = 100 m.", unit="m"),
    part_num("Find the distance travelled at constant speed.", 20 * 20, "20 m/s × 20 s = 400 m.", unit="m"),
    part_mcq("What is the total distance travelled?", "600 m", ["400 m", "500 m", "800 m"], "100 + 400 + 100 = 600 m, same as the trapezium area.")], tier="examen")
L.sc("Speed is defined as", "distance divided by time", ["time divided by distance", "distance multiplied by time", "displacement multiplied by time"], "Speed = distance ÷ time.")
L.sc("The gradient of a distance-time graph gives", "speed", ["acceleration", "distance", "time"], "Gradient = change in distance ÷ change in time = speed.")
L.sc("The area under a speed-time graph gives", "distance travelled", ["acceleration", "average force", "time"], "Area = speed × time = distance.")
L.sc("72 km/h in m/s is", "20 m/s", ["72 m/s", "259 m/s", "7.2 m/s"], "72 ÷ 3.6 = 20 m/s.")
L.sc("Which of these is a vector quantity?", "velocity", ["speed", "distance", "time"], "Velocity has a direction; speed, distance and time do not.")

L = ch.lesson("acceleration-equations", "Acceleration and the equations of motion", minutes=30, prerequisites=["gceol-physics-speed-graphs"],
    objectives=["Define acceleration and deceleration.", "Use v = u + at, s = ut + ½at² and v² = u² + 2as for uniform acceleration.", "Describe free fall and use g = 10 m/s² (air resistance ignored).", "Choose the right equation from the data given."],
    notes=["g = 10 m/s² is used for easy calculation; the Board may allow 9.8 or 10 — check the instruction in past papers."])
L.key("definition", "Acceleration", "**Acceleration** = change in velocity ÷ time taken, unit m/s². Negative acceleration (deceleration) means the object slows down.")
L.formula(r"a = \frac{v - u}{t}", "u = starting speed, v = final speed, t = time")
L.key("formule", "Equations for uniform acceleration", "- $v = u + at$\n- $s = ut + \\frac{1}{2}at^2$\n- $v^2 = u^2 + 2as$\n\nHere $u$ is the initial velocity, $v$ the final velocity, $a$ the constant acceleration, $t$ the time and $s$ the displacement. Starting from rest means $u = 0$.")
L.key("methode", "Free fall", "Near the Earth's surface every freely falling object (air resistance ignored) has acceleration **g = 10 m/s²** in this course (9.8 m/s² is more exact). A heavy and a light stone dropped together reach the ground together.")
L.key("attention", "Method", "1. Write down what is given (u, v, a, t, s) and what is wanted.\n2. Pick the equation that contains these four quantities and **not** the one you do not need.\n3. Use consistent units (m, s). Convert km/h to m/s first.")
L.illustration(plot(0, 4, 0, 40, curves=[("10*x", "blue", None)], points=[(3, 30, "v = 30 m/s at 3 s", "red")], grid=1, xlabel="time (s)", ylabel="speed (m/s)", w=400, h=260),
    "Speed of a freely falling stone dropped from rest: a straight line of gradient g = 10 m/s².", "A straight line through the origin on a speed-time graph, passing through 30 m/s at 3 seconds.")
v = 0 + 2.5 * 8; s = 0 * 8 + 0.5 * 2.5 * 8 ** 2; assert v == 20 and s == 80
L.example("Example 1 — car from rest", "A car starts from rest and accelerates uniformly at 2.5 m/s² for 8 s. Find its final speed and the distance covered.", ["v = u + at = 0 + 2.5 × 8 = 20 m/s.", "s = ut + ½at² = 0 + ½ × 2.5 × 8² = ½ × 2.5 × 64 = 80 m."], "20 m/s; 80 m")
h = 45; t = math.sqrt(2 * h / G); vf = G * t; assert t == 3 and vf == 30
L.example("Example 2 — stone from a cliff", "A stone is dropped from the top of a 45 m cliff. Ignoring air resistance (g = 10 m/s²), find the time to hit the ground and its speed on impact.", ["u = 0, s = 45, a = 10. From s = ½at²: 45 = 5t², so t² = 9 and t = 3 s.", "v = u + at = 0 + 10 × 3 = 30 m/s (check: v² = 2as = 900, v = 30)."], "3 s; 30 m/s")
a = (30 - 10) / 5; assert a == 4
L.num("A car increases its speed from 10 m/s to 30 m/s in 5 s. Find the acceleration.", a, "a = (30 − 10) ÷ 5 = 4 m/s².", unit="m/s²")
L.num("A ball is dropped and falls for 2 s. Find its speed (g = 10 m/s²).", 20, "v = gt = 10 × 2 = 20 m/s.", unit="m/s")
L.mcq("Which equation links u, v, a and s but not t?", "v² = u² + 2as", ["v = u + at", "s = ut + ½at²", "a = v/t"], "v² = u² + 2as contains no time.")
vs = math.sqrt(0 + 2 * 3 * 150)
L.num("A train starts from rest and accelerates at 3 m/s² over a distance of 150 m. Find its final speed.", vs, "v² = u² + 2as = 0 + 2 × 3 × 150 = 900, so v = 30 m/s.", unit="m/s", tier="approfondissement", tolerance=0.01, difficulty=2)
ab = (0 - 20) / 4; sb = 20 * 4 + 0.5 * ab * 16; assert ab == -5 and sb == 40
L.mcq("A bus travelling at 20 m/s brakes uniformly to rest in 4 s. What distance does it travel while braking?", "40 m", ["80 m", "20 m", "5 m"], "Average speed 10 m/s × 4 s = 40 m (or s = ut + ½at² with a = −5: 80 − 40).", tier="approfondissement", difficulty=2)
tu = 20 / 10; hmax = 20 ** 2 / (2 * 10); assert tu == 2 and hmax == 20
L.problem("A ball is thrown straight up from the ground at 20 m/s (g = 10 m/s², air resistance ignored).", [
    part_num("How long does it take to reach the highest point? (the speed is 0 there)", tu, "0 = 20 − 10t gives t = 2 s.", unit="s"),
    part_num("Find the maximum height.", hmax, "v² = u² + 2as: 0 = 400 − 20s, so s = 20 m.", unit="m"),
    part_num("Find the total time before it returns to the ground.", 2 * tu, "Going up and coming down take equal times: 2 × 2 = 4 s.", unit="s")], tier="examen")
L.sc("Acceleration is measured in", "m/s²", ["m/s", "N", "m²/s"], "Change in speed (m/s) per second gives m/s².")
L.sc("An object dropped from rest falls for 3 s (g = 10 m/s²). How far does it fall?", "45 m", ["30 m", "90 m", "15 m"], "s = ½gt² = ½ × 10 × 9 = 45 m.")
L.sc("The acceleration of a car slowing down is", "negative", ["zero", "always positive", "equal to g"], "Speed decreases, so the change in velocity is negative.")
L.sc("A car goes from rest to 12 m/s in 4 s. Its acceleration is", "3 m/s²", ["48 m/s²", "8 m/s²", "0.33 m/s²"], "a = 12 ÷ 4 = 3 m/s².")
L.sc("In free fall (no air resistance) a heavy and a light object", "hit the ground together", ["heavy hits first", "light hits first", "never hit"], "All objects have the same acceleration g without air resistance.")

# =====================================================================  3. FORCES
ch = p.chapter("forces", "Forces and Newton's laws", programRef="GCE O Level Physics — Forces: weight, friction, Newton's laws, F = ma (to be checked against the official syllabus)")
L = ch.lesson("newton-laws", "Forces, weight and Newton's laws", minutes=30, prerequisites=["gceol-physics-acceleration-equations"],
    objectives=["Define force, mass and weight and distinguish them.", "State Newton's three laws of motion.", "Use F = ma and W = mg (g = 10 N/kg).", "Find the resultant of forces along a line and describe friction."],
    notes=["Weight on the Moon: g about 1.6 N/kg is a commonly quoted value; check with the textbook used. Terminal velocity is treated qualitatively only."])
L.key("definition", "Force, mass and weight", "A **force** (unit **newton, N**) can change the speed, direction or shape of an object. **Mass** (kg) is the amount of matter and does not change with place. **Weight** is the pull of gravity on a mass: $W = mg$, with **g = 10 N/kg** here (9.8 N/kg more exactly). Weight is a force.")
L.key("retenir", "Newton's three laws", "1. **First law**: an object stays at rest or moves at constant velocity unless a resultant (net) force acts on it (inertia).\n2. **Second law**: resultant force = mass × acceleration, $F = ma$.\n3. **Third law**: if A pushes B, B pushes A with an equal and opposite force. The two forces act on **different** bodies.")
L.key("pieges", "Common mistakes", "- Weight and mass are different: a 60 kg pupil has the same mass on the Moon but a smaller weight.\n- The two forces of the third law never cancel, because they act on different objects.\n- F in F = ma is the **resultant** force, not just the engine force: subtract friction first.")
L.key("methode", "Friction", "**Friction** is the force that opposes motion between surfaces in contact. It produces heat and wear, but we need it to walk and to brake. It is reduced by lubrication (oil, grease) and wheels, and increased by rough surfaces (tyre treads).")
L.illustration(shapes([RECT(120, 110, 140, 45, radius=8), CIRCLE(155, 165, 15, fill="grey"), CIRCLE(225, 165, 15, fill="grey"), LINE(40, 182, 360, 182, width=2),
                       LINE(262, 132, 340, 132, color="red", width=4, arrow="end"), T(325, 112, "3000 N", 14),
                       LINE(118, 132, 60, 132, color="blue", width=4, arrow="end"), T(75, 112, "600 N", 14), T(190, 90, "car", 14)], 400, 240),
    "Forces on an accelerating car: engine force 3000 N forwards, friction 600 N backwards.", "A car with a long red arrow forwards labelled 3000 N and a shorter blue arrow backwards labelled 600 N.")
m = 1200; Fnet = 3000 - 600; a = Fnet / m; assert a == 2
L.example("Example 1 — resultant force", "A car of mass 1200 kg is pushed forward by an engine force of 3000 N while friction and air resistance total 600 N. Find its acceleration.", ["Resultant force = 3000 − 600 = 2400 N forwards.", "a = F ÷ m = 2400 ÷ 1200 = 2 m/s²."], "2 m/s²")
mass = 50; acc = 2; T_ = mass * (G + acc); assert T_ == 600
L.example("Example 2 — sack lifted by a rope", "A 50 kg sack of cassava is pulled upwards by a rope with an acceleration of 2 m/s² (g = 10 N/kg). Find the tension in the rope.", ["Weight = mg = 50 × 10 = 500 N downwards.", "Resultant upward force = ma = 50 × 2 = 100 N, so T − 500 = 100.", "T = 600 N."], "T = 600 N")
L.num("Find the weight of an 8 kg box (g = 10 N/kg).", 80, "W = mg = 8 × 10 = 80 N.", unit="N")
L.mcq("A book rests on a table. Which force is the third-law pair of the book's weight (pull of Earth on the book)?", "The pull of the book on the Earth", ["The upward push of the table on the book", "The friction of the table", "The mass of the book"], "The pair of 'Earth pulls book' is 'book pulls Earth', acting on a different body. The table's push is a different force (it balances the weight).")
L.num("A force of 1500 N gives a body an acceleration of 3 m/s². Find its mass.", 500, "m = F ÷ a = 1500 ÷ 3 = 500 kg.", unit="kg")
L.num("Forces of 100 N to the right and 40 N to the left act on a 20 kg trolley. Find its acceleration.", (100 - 40) / 20, "Resultant = 100 − 40 = 60 N, a = 60 ÷ 20 = 3 m/s².", unit="m/s²", tier="approfondissement")
L.tf("An astronaut's mass is smaller on the Moon than on Earth.", False, "Mass is the amount of matter and does not change; only the weight is smaller (g is smaller on the Moon).", tier="approfondissement", difficulty=2)
a1 = (2200 - 600) / 800; v5 = a1 * 5; s5 = 0.5 * a1 * 25; assert a1 == 2 and v5 == 10 and s5 == 25
L.problem("A car of mass 800 kg starts from rest. The engine force is 2200 N and the total resistance is 600 N.", [
    part_num("Find the resultant force.", 1600, "2200 − 600 = 1600 N.", unit="N"),
    part_num("Find the acceleration.", a1, "a = 1600 ÷ 800 = 2 m/s².", unit="m/s²"),
    part_num("Find its speed after 5 s.", v5, "v = at = 2 × 5 = 10 m/s.", unit="m/s"),
    part_num("Find the distance travelled in 5 s.", s5, "s = ½at² = ½ × 2 × 25 = 25 m.", unit="m")], tier="examen")
L.sc("The unit of force is the", "newton", ["joule", "watt", "pascal"], "Force is measured in newtons (N).")
L.sc("A mass of 5 kg has a weight of (g = 10 N/kg)", "50 N", ["5 N", "0.5 N", "15 N"], "W = mg = 5 × 10 = 50 N.")
L.sc("A body moves at constant velocity. The resultant force on it is", "zero", ["equal to its weight", "forwards", "equal to its mass"], "By Newton's first law, constant velocity means zero resultant force.")
L.sc("Newton's second law is", "F = ma", ["F = m/a", "F = a/m", "F = mv²"], "Resultant force = mass × acceleration.")
L.sc("Friction", "opposes the motion between surfaces", ["always helps motion", "does not exist on rough surfaces", "is the same as weight"], "Friction acts opposite to the (tendency of) motion.")

# =====================================================================  4. MOMENTS
ch = p.chapter("moments", "Moments and equilibrium", programRef="GCE O Level Physics — Turning effect of forces, equilibrium, centre of gravity (to be checked against the official syllabus)")
L = ch.lesson("moments-equilibrium", "Moments, equilibrium and centre of gravity", minutes=25, prerequisites=["gceol-physics-newton-laws"],
    objectives=["Define the moment of a force and calculate it.", "State and apply the principle of moments.", "Define centre of gravity and relate it to stability.", "State the conditions for equilibrium of a body."])
L.key("definition", "Moment of a force", "The **moment** of a force about a point (pivot) = force × **perpendicular distance** from the pivot to the line of action of the force. Unit: **newton-metre (N m)**. A longer spanner gives a bigger moment for the same force.")
L.formula(r"\text{moment} = F \times d", "d is measured at 90° to the force")
L.key("retenir", "Principle of moments", "A body is in equilibrium when (1) the **resultant force is zero** and (2) the **sum of clockwise moments = sum of anticlockwise moments** about any point.\n\nThe **centre of gravity** is the point where the whole weight of a body seems to act. A tall object with a high centre of gravity and a narrow base topples easily; a low centre of gravity and a wide base give stability.")
L.key("methode", "Solving moment problems", "1. Choose a pivot (often the support).\n2. List each force that tries to turn **clockwise** and **anticlockwise** with its distance.\n3. Set total clockwise = total anticlockwise and solve.\n4. The weight of a uniform beam acts at its **centre**.")
L.key("pieges", "Watch out", "- Use the **perpendicular** distance from the pivot, in metres (or consistently in cm).\n- Do not forget the weight of the beam itself unless the pivot is at its centre.\n- Equal forces do not balance unless the distances are equal too.")
L.illustration(shapes([RECT(60, 118, 280, 10, fill="grey"), POLY([[200, 128], [180, 170], [220, 170]], fill="orange"),
                       LINE(60, 60, 60, 114, color="red", width=3, arrow="end"), T(60, 45, "300 N", 14), LINE(305, 60, 305, 114, color="blue", width=3, arrow="end"), T(305, 45, "F", 14),
                       LINE(60, 195, 200, 195, arrow="both"), T(130, 215, "2 m", 14), LINE(200, 195, 305, 195, arrow="both"), T(255, 215, "1.5 m", 14)], 400, 240),
    "A balanced see-saw: 300 N at 2 m from the pivot balances a force F at 1.5 m on the other side.", "A horizontal plank on a triangular pivot, a 300 N force down on the left at 2 m and an unknown force F down on the right at 1.5 m.")
F = 300 * 2 / 1.5; assert F == 400
L.example("Example 1 — see-saw", "A child of weight 300 N sits 2 m from the pivot of a see-saw. At what force must an older child press down at 1.5 m on the other side to balance it?", ["Anticlockwise moment = 300 × 2 = 600 N m.", "Clockwise moment = F × 1.5.", "Balance: F × 1.5 = 600, so F = 600 ÷ 1.5 = 400 N."], "F = 400 N")
W = 0.5; assert 2 * 10 == W * 40
L.example("Example 2 — uniform metre rule", "A uniform metre rule of weight 2 N is pivoted at the 40 cm mark. A load W hangs from the 0 cm end and the rule balances. Find W.", ["The rule's weight (2 N) acts at its centre, the 50 cm mark, which is 10 cm from the pivot.", "Moment of the rule = 2 × 10 = 20 N cm; moment of the load = W × 40 (the end is 40 cm from the pivot).", "W × 40 = 20, so W = 0.5 N."], "W = 0.5 N")
L.num("Find the moment of a 40 N force acting at 0.5 m from a pivot.", 20, "Moment = F × d = 40 × 0.5 = 20 N m.", unit="N m")
L.mcq("A body is in equilibrium. Which statement is correct?", "Resultant force and resultant moment are both zero", ["Only the resultant force must be zero", "Clockwise moments are bigger", "It must be at rest and light"], "Both conditions are needed for equilibrium.")
L.num("A pupil pushes a door with 15 N at 0.8 m from the hinges. Find the moment about the hinges.", 12, "15 × 0.8 = 12 N m.", unit="N m")
F2 = 20 * 30 / 20
L.num("A 20 N weight hangs 30 cm to the left of a pivot. What weight hung 20 cm to the right balances it?", F2, "20 × 30 = W × 20, so W = 600 ÷ 20 = 30 N.", unit="N", tier="approfondissement")
L.mcq("Which object is most stable?", "A wide, low box", ["A tall narrow box", "A narrow box standing on its corner", "A tall box on a small base"], "A low centre of gravity and a wide base make an object hard to topple.", tier="approfondissement")
d2 = 300 * 1 / 240; assert d2 == 1.25
L.problem("A uniform plank 3 m long, of weight 60 N, is pivoted at its centre. A child of weight 300 N sits 1 m to the left of the pivot, and a child of weight 240 N sits on the right so that the plank balances.", [
    part_num("Find the moment of the 300 N child about the pivot.", 300, "300 × 1 = 300 N m anticlockwise.", unit="N m"),
    part_num("Find the distance from the pivot at which the 240 N child sits.", d2, "240 × d = 300, so d = 1.25 m.", unit="m"),
    part_num("Find the upward force from the pivot.", 600, "Equilibrium: upward force = 300 + 240 + 60 = 600 N (the plank's weight acts at the pivot and has no moment).", unit="N"),
    part_open("Explain why the plank's own weight does not affect the moment equation.", "It is uniform, so its weight acts at its centre, which is the pivot; its distance from the pivot is zero, so its moment is zero.", "1 mark: weight acts at centre; 1 mark: distance zero so moment zero.")], tier="examen")
L.sc("The unit of moment is the", "newton-metre", ["newton per metre", "joule per second", "pascal"], "Moment = force × distance, so N m.")
L.sc("To loosen a tight nut more easily you should", "use a longer spanner", ["use a shorter spanner", "push at the pivot", "push parallel to the spanner"], "A greater distance gives a greater moment.")
L.sc("The weight of a uniform beam acts at its", "centre", ["end", "pivot always", "top surface"], "The centre of gravity of a uniform beam is its centre.")
L.sc("Principle of moments: in equilibrium", "clockwise moments = anticlockwise moments", ["forces are equal on both sides", "distances are equal", "mass is zero"], "Sum of clockwise moments = sum of anticlockwise moments.")
L.sc("A force of 10 N at 3 m from a pivot has a moment of", "30 N m", ["13 N m", "3.3 N m", "0.3 N m"], "10 × 3 = 30 N m.")

# =====================================================================  5. DENSITY
ch = p.chapter("matter", "Density and pressure", programRef="GCE O Level Physics — Density, pressure in solids, liquids and gases (to be checked against the official syllabus)")
L = ch.lesson("density", "Density", minutes=25, prerequisites=["gceol-physics-units-instruments"],
    objectives=["Define density and use ρ = m/V.", "Convert between g/cm³ and kg/m³.", "Predict whether an object floats or sinks in water.", "Describe how to measure the density of a regular and an irregular solid and a liquid."],
    notes=["Density values in the bar chart are rounded textbook values (ice about 920 kg/m³, iron about 7900 kg/m³); check against the school's data table."])
L.key("definition", "Density", "**Density** is the mass per unit volume of a substance: $\\rho = \\frac{m}{V}$. Units: **kg/m³** or **g/cm³**. Water has density 1000 kg/m³ = 1 g/cm³. Hence **1 g/cm³ = 1000 kg/m³**.")
L.formula(r"\rho = \frac{m}{V} \qquad m = \rho V \qquad V = \frac{m}{\rho}", "ρ = density, m = mass, V = volume")
L.key("retenir", "Floating and sinking", "An object **floats** in a liquid if its average density is **less** than the liquid's density, and sinks if it is greater. Ships made of steel float because the hollow hull makes the average density of the ship less than that of water.")
L.key("methode", "Measuring density", "- **Regular solid**: measure the mass with a balance, the volume from its dimensions.\n- **Irregular solid**: mass with a balance, volume by **displacement** in a measuring cylinder.\n- **Liquid**: mass of a measured volume (mass of cylinder with liquid − mass of empty cylinder).")
L.key("pieges", "Watch out", "- Always convert g/cm³ to kg/m³ (× 1000) or the reverse (÷ 1000).\n- Volume in cm³ with mass in g gives g/cm³; volume in m³ with mass in kg gives kg/m³. Do not mix them.\n- Density is not weight: a large stone and a small stone of the same material have the same density.")
L.illustration(bars([("cork", 240, "green"), ("ice", 920, "blue"), ("water", 1000, "blue"), ("aluminium", 2700, "orange"), ("iron", 7900, "red")], unit="kg/m³", w=400, h=240),
    "Approximate densities of some materials (kg/m³).", "A bar chart: cork 240, ice about 920, water 1000, aluminium 2700 and iron about 7900 kilograms per cubic metre.")
d = 540 / 200; assert d == 2.7
L.example("Example 1 — metal block", "A metal block has mass 540 g and volume 200 cm³. Find its density in g/cm³ and in kg/m³.", ["ρ = m ÷ V = 540 ÷ 200 = 2.7 g/cm³.", "Multiply by 1000: 2.7 × 1000 = 2700 kg/m³ (this is about the density of aluminium)."], "2.7 g/cm³ = 2700 kg/m³")
m2 = 920 * 0.005; assert abs(m2 - 4.6) < 1e-9
L.example("Example 2 — cooking oil", "Assume a cooking oil has density 920 kg/m³. Find the mass of 5 litres of it.", ["5 litres = 5 × 0.001 m³ = 0.005 m³.", "m = ρV = 920 × 0.005 = 4.6 kg."], "4.6 kg")
L.num("A liquid has mass 400 g and volume 50 cm³. Find its density in g/cm³.", 8, "ρ = 400 ÷ 50 = 8 g/cm³.", unit="g/cm³")
L.mcq("A solid of density 800 kg/m³ is placed in water (1000 kg/m³). It will", "float", ["sink", "dissolve", "stay at the bottom"], "Its density is less than that of water.")
L.num("Find the mass of water in a tank of volume 2 m³ (density 1000 kg/m³).", 2000, "m = ρV = 1000 × 2 = 2000 kg.", unit="kg")
mb = 2400 * 0.5 * 0.2 * 0.2; assert abs(mb - 48) < 1e-9
L.num("A concrete block 0.5 m × 0.2 m × 0.2 m has density 2400 kg/m³. Find its mass.", mb, "V = 0.5 × 0.2 × 0.2 = 0.02 m³; m = 2400 × 0.02 = 48 kg.", unit="kg", tolerance=0.01, tier="approfondissement")
L.open("Describe how you would find the density of a small irregular stone using a balance and a measuring cylinder.", "Measure the mass m of the stone on the balance. Half-fill the measuring cylinder with water and record the volume V1. Lower the stone in (tied with thread) and record V2. Volume of stone = V2 − V1. Density = m ÷ (V2 − V1).", "1 mark: mass by balance; 1 mark: volume by displacement V2 − V1; 1 mark: ρ = m/V.", tier="approfondissement")
mw = 80 - 30; ml = 70 - 30; rho_l = ml / mw
L.problem("A density bottle has mass 30 g when empty, 80 g when full of water and 70 g when full of another liquid. (Density of water = 1 g/cm³.)", [
    part_num("Find the mass of water in the bottle.", mw, "80 − 30 = 50 g.", unit="g"),
    part_num("Find the volume of the bottle in cm³.", mw / 1, "Water density is 1 g/cm³, so V = 50 ÷ 1 = 50 cm³.", unit="cm³"),
    part_num("Find the density of the liquid in g/cm³.", rho_l, "Mass of liquid 70 − 30 = 40 g in 50 cm³: 40 ÷ 50 = 0.8 g/cm³.", unit="g/cm³"),
    part_num("Express the density of the liquid in kg/m³.", 800, "0.8 × 1000 = 800 kg/m³.", unit="kg/m³")], tier="examen")
L.sc("Density is", "mass per unit volume", ["volume per unit mass", "weight per unit area", "mass times volume"], "ρ = m/V.")
L.sc("The density of water in kg/m³ is", "1000", ["1", "100", "10"], "1 g/cm³ = 1000 kg/m³.")
L.sc("An object floats when its density is", "less than the liquid's", ["more than the liquid's", "zero", "the same as air"], "Less dense objects float.")
L.sc("A body of mass 600 g has volume 200 cm³. Its density is", "3 g/cm³", ["0.33 g/cm³", "800 g/cm³", "120 000 g/cm³"], "600 ÷ 200 = 3.")
L.sc("Volume of an irregular stone is found by", "displacement of water", ["weighing it", "measuring with a ruler only", "its colour"], "Final reading − initial reading in a measuring cylinder.")

L = ch.lesson("pressure", "Pressure in solids, liquids and gases", minutes=30, prerequisites=["gceol-physics-density", "gceol-physics-newton-laws"],
    objectives=["Define pressure and use P = F/A.", "Use P = hρg for pressure in a liquid.", "Describe atmospheric pressure and the barometer.", "Explain the hydraulic press and calculate forces."],
    notes=["Standard atmospheric pressure is about 100 000 Pa (101 kPa) or 760 mm of mercury; check which value the Board's questions assume."])
L.key("definition", "Pressure", "**Pressure** = force acting perpendicular to a surface ÷ area. Unit: **pascal (Pa)** = 1 N/m². A small area gives a large pressure: knives are sharp, tractors have wide tyres.")
L.formula(r"P = \frac{F}{A}", "F in N, A in m², P in Pa")
L.key("formule", "Pressure in a liquid", "Pressure in a liquid increases with **depth** and with **density**, and is the same in all directions at the same depth: $P = h\\rho g$ (h = depth, ρ = density, g = 10 N/kg). It does not depend on the shape of the container. Total pressure = liquid pressure + atmospheric pressure (about $10^5$ Pa).")
L.key("retenir", "Atmosphere and hydraulics", "The air has weight, so it exerts **atmospheric pressure**, measured with a **barometer** (about 760 mm of mercury at sea level). In a **hydraulic press** a liquid transmits pressure equally, so $\\frac{F_1}{A_1} = \\frac{F_2}{A_2}$: a small force on a small piston gives a large force on a large piston (garage lifts, brakes).")
L.key("pieges", "Watch out", "- Convert areas to m² before dividing: 1 cm² = 0.0001 m².\n- Liquid pressure uses the **depth**, not the total amount of water.\n- In a hydraulic press the force is multiplied but the work is not: the small piston moves much further.")
L.illustration(shapes([RECT(40, 150, 60, 70), RECT(40, 126, 60, 14, fill="grey"), RECT(180, 90, 160, 130), RECT(180, 110, 160, 14, fill="grey", stroke="ink"),
                       RECT(100, 175, 80, 45), T(70, 108, "A1", 14), T(260, 80, "A2", 14), LINE(70, 40, 70, 120, color="red", width=3, arrow="end"), T(70, 28, "F1", 14),
                       LINE(260, 190, 260, 130, color="blue", width=3, arrow="start"), T(310, 165, "F2", 14)], 400, 240),
    "A hydraulic press: pressure from the small piston (area A1) is passed through the liquid to the large piston (area A2).", "Two connected cylinders of different widths with pistons, a small downward force F1 on the narrow piston and a larger upward force F2 on the wide piston.")
F_ = 60 * 10; A_ = 2 * 150e-4; P_ = F_ / A_; close(P_, 20000, 1e-6)
L.example("Example 1 — standing pupil", "A pupil of mass 60 kg stands on two shoes, each of area 150 cm². Find the pressure on the ground (g = 10 N/kg).", ["Force = weight = 60 × 10 = 600 N.", "Area = 2 × 150 = 300 cm² = 0.03 m² (1 cm² = 10⁻⁴ m²).", "P = F ÷ A = 600 ÷ 0.03 = 20 000 Pa."], "20 000 Pa")
Pl = 12 * 1000 * G; Pt = Pl + 1e5; assert Pl == 120000 and Pt == 220000
L.example("Example 2 — diver", "A diver in a lake is at a depth of 12 m (water density 1000 kg/m³, g = 10 N/kg, atmospheric pressure 10⁵ Pa). Find the pressure due to the water and the total pressure.", ["Pressure due to water = hρg = 12 × 1000 × 10 = 120 000 Pa.", "Total = 120 000 + 100 000 = 220 000 Pa."], "120 000 Pa; 220 000 Pa")
L.num("A force of 200 N acts on an area of 0.05 m². Find the pressure.", 4000, "P = 200 ÷ 0.05 = 4000 Pa.", unit="Pa")
L.num("Find the pressure due to water at a depth of 5 m (ρ = 1000 kg/m³, g = 10 N/kg).", 50000, "P = hρg = 5 × 1000 × 10 = 50 000 Pa.", unit="Pa")
L.mcq("Why does a sharp knife cut better than a blunt one for the same force?", "A smaller area gives a larger pressure", ["It has more mass", "It has more weight", "Friction is zero"], "P = F/A: the same force on a smaller area makes a bigger pressure.")
F2h = 50 * 200 / 10; assert F2h == 1000
L.num("In a hydraulic press the small piston has area 10 cm² and receives a force of 50 N. The large piston has area 200 cm². Find the force on it.", F2h, "Pressure is the same: 50/10 = 5 N/cm², so F2 = 5 × 200 = 1000 N.", unit="N", tier="approfondissement")
L.tf("The pressure at a given depth in water is larger in a wide lake than in a narrow tank.", False, "Liquid pressure depends only on depth, density and g, not on the shape or width of the container.", tier="approfondissement", difficulty=2)
Pb = 4 * 1000 * 10; Fb = Pb * 2; Wt = 4 * 2 * 1000 * 10; assert Fb == Wt == 80000
L.problem("A rectangular tank in Bamenda has a base of area 2 m² and holds water to a depth of 4 m (ρ = 1000 kg/m³, g = 10 N/kg).", [
    part_num("Find the pressure due to the water on the base.", Pb, "P = hρg = 4 × 1000 × 10 = 40 000 Pa.", unit="Pa"),
    part_num("Find the force of the water on the base.", Fb, "F = P × A = 40 000 × 2 = 80 000 N.", unit="N"),
    part_num("Find the mass of the water in the tank.", 8000, "V = 2 × 4 = 8 m³; m = ρV = 8000 kg.", unit="kg"),
    part_mcq("How does the weight of the water compare with the force on the base?", "They are equal (80 000 N)", ["The weight is twice as large", "The force is zero", "The weight is half"], "W = 8000 × 10 = 80 000 N; for a tank with vertical sides the water pushes down on the base with its whole weight.")], tier="examen")
L.sc("The unit of pressure is the", "pascal", ["newton", "kilogram", "joule"], "1 Pa = 1 N/m².")
L.sc("Liquid pressure increases with", "depth", ["width of container", "colour of liquid", "shape of container"], "P = hρg.")
L.sc("A force of 50 N acts on 2 m². The pressure is", "25 Pa", ["100 Pa", "52 Pa", "0.04 Pa"], "50 ÷ 2 = 25 Pa.")
L.sc("A hydraulic system works because liquids", "transmit pressure equally", ["are compressible", "are very light", "have no pressure"], "Pressure applied to an enclosed liquid is transmitted equally.")
L.sc("Atmospheric pressure is measured with a", "barometer", ["thermometer", "ammeter", "voltmeter"], "A barometer measures atmospheric pressure.")

# =====================================================================  6. ENERGY
ch = p.chapter("energy", "Work, energy, power and machines", programRef="GCE O Level Physics — Work, energy, power, machines, energy sources (to be checked against the official syllabus)")
L = ch.lesson("work-energy-power", "Work, energy and power", minutes=30, prerequisites=["gceol-physics-newton-laws"],
    objectives=["Define work, energy and power with their units.", "Calculate work done W = Fd, kinetic energy ½mv², potential energy mgh and power P = W/t.", "Apply the conservation of energy.", "Compare renewable and non-renewable energy sources."],
    notes=["Statements about Cameroon's electricity (a large share from hydroelectric dams such as those on the Sanaga) are general; no percentages given."])
L.key("definition", "Work, energy, power", "**Work** is done when a force moves an object: $W = Fd$ (d in the direction of the force). **Energy** is the ability to do work; both are measured in **joules (J)**. **Power** is the rate of doing work: $P = \\frac{W}{t}$, in **watts (W)** (1 W = 1 J/s; 1 kW = 1000 W).")
L.key("formule", "Mechanical energy", "- **Kinetic energy** (movement): $E_k = \\frac{1}{2}mv^2$\n- **Gravitational potential energy** (height): $E_p = mgh$ (g = 10 N/kg)\n\n**Conservation of energy**: energy is never created or destroyed; it is changed from one form to another. If friction is ignored, the total of $E_k + E_p$ stays constant.")
L.key("retenir", "Energy sources", "**Non-renewable**: coal, oil and natural gas (fossil fuels), which run out. **Renewable**: hydroelectric (water), solar, wind, biomass (wood, biogas). Much electricity in Cameroon is generated from water flowing through hydroelectric dams; solar panels are common in sunny regions. Fossil fuels release carbon dioxide when burnt.")
L.key("pieges", "Watch out", "- Work needs movement: holding a heavy bag still does no work (in physics).\n- Doubling the speed makes the kinetic energy **four** times bigger (v is squared).\n- Use the vertical height for potential energy, not the length of a slope.")
L.illustration(shapes([LINE(120, 30, 120, 215, width=1), CIRCLE(120, 45, 12, fill="orange"), CIRCLE(120, 120, 12, fill="orange"), CIRCLE(120, 200, 12, fill="orange"),
                       T(250, 50, "all potential energy", 14), T(250, 125, "half PE, half KE", 14), T(250, 205, "all kinetic energy", 14)], 400, 240),
    "A ball falling: potential energy changes into kinetic energy.", "A vertical line with three balls, at the top (all potential energy), half way (half and half) and at the bottom (all kinetic energy).")
W1 = 150 * 40; P1 = W1 / 50; assert W1 == 6000 and P1 == 120
L.example("Example 1 — pushing a handcart", "A man pushes a handcart with a force of 150 N over 40 m in 50 s. Find the work done and his power.", ["Work = Fd = 150 × 40 = 6000 J.", "Power = W ÷ t = 6000 ÷ 50 = 120 W."], "6000 J; 120 W")
m_ = 0.5; h_ = 20; Ep = m_ * G * h_; v_ = math.sqrt(2 * G * h_); Ek = 0.5 * m_ * v_ ** 2; close(Ep, Ek); assert Ep == 100 and v_ == 20
L.example("Example 2 — falling ball", "A ball of mass 0.5 kg is dropped from a height of 20 m (g = 10 N/kg, no air resistance). Find the speed when it reaches the ground.", ["Potential energy at the top = mgh = 0.5 × 10 × 20 = 100 J.", "At the bottom all of it is kinetic: ½mv² = 100, so 0.25 v² = 100 and v² = 400.", "v = 20 m/s."], "20 m/s")
L.num("Find the work done by a force of 50 N moving an object 6 m in the direction of the force.", 300, "W = Fd = 50 × 6 = 300 J.", unit="J")
L.num("Find the kinetic energy of a 2 kg trolley moving at 5 m/s.", 25, "Ek = ½mv² = ½ × 2 × 25 = 25 J.", unit="J")
L.mcq("The watt is the same as", "one joule per second", ["one joule per metre", "one newton per second", "one joule"], "Power = energy ÷ time, so 1 W = 1 J/s.")
Ep2 = 60 * G * 5; P2 = Ep2 / 20
L.num("A pupil of mass 60 kg climbs stairs of vertical height 5 m in 20 s (g = 10 N/kg). Find the power developed.", P2, "Work = mgh = 60 × 10 × 5 = 3000 J; P = 3000 ÷ 20 = 150 W.", unit="W", tier="approfondissement", difficulty=2)
L.tf("If the speed of a car doubles, its kinetic energy doubles.", False, "Ek = ½mv²: doubling v multiplies Ek by 2² = 4.", tier="approfondissement", difficulty=2)
Ek1 = 0.5 * 200 * 15 ** 2; Fb = Ek1 / 25; assert Ek1 == 22500 and Fb == 900
L.problem("A motorbike and rider have a total mass of 200 kg and travel at 15 m/s. They brake to a stop over 25 m on level ground.", [
    part_num("Find the kinetic energy before braking.", Ek1, "½ × 200 × 15² = 100 × 225 = 22 500 J.", unit="J"),
    part_num("State the work done by the braking force in stopping (J).", Ek1, "All the kinetic energy is converted into heat by the work of the brakes and friction: 22 500 J.", unit="J"),
    part_num("Find the average braking force.", Fb, "W = Fd, so F = 22 500 ÷ 25 = 900 N.", unit="N")], tier="examen")
L.sc("Work is calculated by", "force × distance moved", ["force ÷ distance", "mass × speed", "force × time"], "W = Fd.")
L.sc("One kilowatt is", "1000 W", ["100 W", "10 W", "1 000 000 W"], "kilo = 1000.")
L.sc("Which of these is a renewable energy source?", "solar energy", ["coal", "petrol", "natural gas"], "Sunlight is continuously supplied; fossil fuels are finite.")
L.sc("A 2 kg mass is lifted 3 m (g = 10 N/kg). Its gain in potential energy is", "60 J", ["6 J", "23 J", "0.6 J"], "mgh = 2 × 10 × 3 = 60 J.")
L.sc("Energy cannot be created or destroyed. This is the law of", "conservation of energy", ["gravitation", "inertia", "moments"], "Energy only changes form.")

L = ch.lesson("machines-efficiency", "Simple machines and efficiency", minutes=25, prerequisites=["gceol-physics-work-energy-power", "gceol-physics-moments-equilibrium"],
    objectives=["Describe levers, pulleys and inclined planes.", "Define mechanical advantage (MA), velocity ratio (VR) and efficiency.", "Calculate efficiency from work done or from MA/VR.", "Explain why no machine is 100% efficient."])
L.key("definition", "MA, VR and efficiency", "A **machine** makes work easier by changing the size or direction of a force.\n\n- **Mechanical advantage** MA = load ÷ effort (no unit).\n- **Velocity ratio** VR = distance moved by effort ÷ distance moved by load.\n- **Efficiency** = (work output ÷ work input) × 100% = (MA ÷ VR) × 100%.")
L.key("retenir", "Common machines", "- **Lever**: a rigid bar turning about a pivot (crowbar, wheelbarrow, scissors); based on moments.\n- **Pulley system**: for a frictionless system VR = number of rope sections supporting the load.\n- **Inclined plane (ramp)**: VR = length of slope ÷ vertical height.\n- A machine can multiply force but never energy.")
L.key("attention", "Why efficiency is below 100%", "Some input energy is always wasted as heat (friction) and in lifting the moving parts of the machine. So work output < work input, MA < VR and efficiency < 100%. Oiling the moving parts improves efficiency.")
L.illustration(shapes([POLY([[60, 190], [340, 190], [340, 90]], fill="grey"), RECT(150, 128, 36, 28, fill="orange"), LINE(340, 190, 340, 90, color="blue", width=3, arrow="both"), T(375, 145, "2 m", 14), T(200, 215, "5 m (slope)", 14), LINE(160, 120, 250, 90, color="red", width=3, arrow="end"), T(255, 70, "effort", 14)], 400, 240),
    "An inclined plane (ramp): a crate is pushed up a 5 m slope to a height of 2 m.", "A right-angled triangle ramp with a crate on the slope, an effort arrow pointing up the slope, slope length 5 m and height 2 m.")
MA = 400 / 150; VR = 4; eff = MA / VR * 100; assert abs(eff - 66.666) < 0.01
L.example("Example 1 — pulley", "An effort of 150 N lifts a load of 400 N using a pulley system with VR = 4. Find the MA and the efficiency.", ["MA = load ÷ effort = 400 ÷ 150 = 2.67.", "Efficiency = MA ÷ VR × 100 = 2.67 ÷ 4 × 100 = 66.7%."], "MA = 2.67; efficiency ≈ 66.7%")
win = 250 * 5; wout = 500 * 2; eff2 = wout / win * 100; assert win == 1250 and wout == 1000 and eff2 == 80
L.example("Example 2 — ramp", "A crate of weight 500 N is pushed up a ramp 5 m long by an effort of 250 N parallel to the slope. The ramp's top is 2 m above the ground. Find the efficiency.", ["Work input = effort × distance along the slope = 250 × 5 = 1250 J.", "Work output = load × vertical height = 500 × 2 = 1000 J.", "Efficiency = 1000 ÷ 1250 × 100 = 80%."], "80%")
L.num("A load of 300 N is lifted by an effort of 100 N. Find the mechanical advantage.", 3, "MA = 300 ÷ 100 = 3.", unit=None)
L.mcq("A machine has a work input of 500 J and a work output of 400 J. Its efficiency is", "80%", ["125%", "20%", "100%"], "400 ÷ 500 × 100 = 80%.")
L.tf("A machine can have an efficiency of more than 100%.", False, "That would create energy; there are always wasted losses.")
L.num("A machine with MA = 3 and VR = 5. Find its efficiency in %.", 60, "3 ÷ 5 × 100 = 60%.", unit="%", tier="approfondissement")
L.open("Give two ways to improve the efficiency of a pulley system and say why they work.", "Oil the pulley axles (less friction, less energy wasted as heat) and use lighter ropes and pulleys (less work needed to lift the machine's own parts).", "1 mark each way; 1 mark for linking to wasted energy.", tier="approfondissement")
MA3 = 800 / 250; eff3 = MA3 / 4 * 100; win3 = 250 * 2; wout3 = 800 * 0.5
L.problem("A pulley system with VR = 4 is used to raise a 800 N load by 0.5 m with an effort of 250 N.", [
    part_num("Find the distance moved by the effort.", 0.5 * 4, "VR = 4 means the effort moves 4 times as far as the load: 0.5 × 4 = 2 m.", unit="m"),
    part_num("Find the work input.", win3, "250 × 2 = 500 J.", unit="J"),
    part_num("Find the work output.", wout3, "800 × 0.5 = 400 J.", unit="J"),
    part_num("Find the efficiency in %.", eff3, "400 ÷ 500 × 100 = 80% (also MA/VR = 3.2/4 = 0.8).", unit="%")], tier="examen")
L.sc("Mechanical advantage is", "load ÷ effort", ["effort ÷ load", "load × effort", "distance ÷ time"], "MA = load/effort.")
L.sc("A wheelbarrow is an example of a", "lever", ["pulley", "wedge only", "screw only"], "It is a lever with the pivot at the wheel.")
L.sc("For an ideal machine, efficiency is", "100%", ["0%", "50%", "200%"], "Ideal means no energy wasted.")
L.sc("VR of a ramp 4 m long and 1 m high is", "4", ["0.25", "5", "3"], "VR = slope length ÷ height = 4 ÷ 1 = 4.")
L.sc("Wasted energy in a machine appears mainly as", "heat", ["light only", "mass", "force"], "Friction converts useful energy into heat.")

# =====================================================================  7. THERMAL PHYSICS
ch = p.chapter("thermal", "Thermal physics", programRef="GCE O Level Physics — Thermal physics: temperature, expansion, gas laws, heat capacity, change of state, heat transfer (to be checked against the official syllabus)")
L = ch.lesson("temperature-expansion", "Temperature, thermometers and expansion", minutes=25,
    objectives=["Distinguish heat and temperature and describe the kinetic model of matter.", "Describe liquid-in-glass thermometers, fixed points and the Kelvin scale.", "Describe expansion of solids, liquids and gases and its uses and problems.", "Use T(K) = θ(°C) + 273 and the linear scale of a thermometer."],
    notes=["Kelvin conversion uses 273 (273.15 is more exact). Anomalous expansion of water (4 °C) may be in the syllabus; not detailed here."])
L.key("definition", "Heat and temperature", "**Heat** is energy transferred because of a temperature difference (joule). **Temperature** measures how hot something is (°C or K): it is related to the average kinetic energy of the particles. In solids particles vibrate in fixed places, in liquids they slide past each other, in gases they move freely and far apart.")
L.key("retenir", "Thermometers and scales", "A liquid-in-glass thermometer uses the expansion of mercury or coloured alcohol. Fixed points: **0 °C** (pure melting ice) and **100 °C** (steam above boiling water at normal pressure); the scale in between is divided into 100 equal steps. **Kelvin scale**: $T(K) = \\theta(°C) + 273$; 0 K (−273 °C) is the lowest possible temperature.")
L.key("methode", "Expansion", "When heated, most solids, liquids and gases **expand** (particles vibrate or move more). Gases expand most, solids least.\n\n- Uses: **bimetallic strip** (two metals bend as they heat unequally) in thermostats and fire alarms.\n- Problems: gaps are left in railway lines and bridges; telephone and power cables sag in hot weather.")
L.key("pieges", "Watch out", "- Heat and temperature are not the same: a bath of lukewarm water holds more heat than a hot cup of tea.\n- A temperature **difference** has the same number in °C and K, but a temperature itself does not.")
L.illustration(shapes([RECT(60, 40, 14, 150), CIRCLE(67, 205, 16, fill="red"), RECT(61, 120, 12, 70, fill="red"), LINE(80, 190, 100, 190), T(140, 195, "0 °C", 14), LINE(80, 40, 100, 40), T(145, 45, "100 °C", 14),
                       LINE(80, 120, 100, 120, color="blue"), T(160, 125, "35 °C", 14, color="blue")], 400, 240),
    "A thermometer with fixed points at 0 °C and 100 °C; the column length changes linearly with temperature.", "A vertical liquid thermometer with marks at 0 and 100 degrees Celsius and a marked level at 35 degrees Celsius.")
TK = 27 + 273; assert TK == 300
L.example("Example 1 — Kelvin", "The temperature in Buea one morning is 27 °C. Express it in kelvin, and give 400 K in °C.", ["T = θ + 273 = 27 + 273 = 300 K.", "θ = T − 273 = 400 − 273 = 127 °C."], "300 K; 127 °C")
th = (9 - 2) / (22 - 2) * 100; assert th == 35
L.example("Example 2 — uncalibrated thermometer", "The mercury thread of an unmarked thermometer is 2 cm long at 0 °C and 22 cm long at 100 °C. Find the temperature when the thread is 9 cm long.", ["Length change for 100 °C = 22 − 2 = 20 cm, so 0.2 cm per °C.", "Change from the ice point = 9 − 2 = 7 cm, so θ = 7 ÷ 0.2 = 35 °C."], "35 °C")
L.num("Convert 20 °C into kelvin.", 293, "20 + 273 = 293 K.", unit="K")
L.mcq("Which gives the lower fixed point of the Celsius scale?", "Pure melting ice", ["Boiling water", "Room temperature", "Body temperature"], "0 °C is the temperature of pure melting ice.")
L.tf("A bimetallic strip bends when heated because the two metals expand by different amounts.", True, "That is the principle of the thermostat.")
L.num("The mercury thread of an unmarked thermometer is 3 cm at 0 °C and 23 cm at 100 °C. What is its length at 60 °C?", 15, "Length = 3 + 0.2 × 60 = 15 cm.", unit="cm", tier="approfondissement", difficulty=2)
L.mcq("Why are small gaps left between rails of a railway?", "To allow expansion in hot weather", ["To save steel", "To reduce friction of wheels only", "To allow contraction only"], "Without gaps, expansion would buckle the rails.", tier="approfondissement")
L.problem("A pupil in Maroua measures the air temperature as 38 °C.", [
    part_num("Express 38 °C in kelvin.", 311, "38 + 273 = 311 K.", unit="K"),
    part_num("The temperature then falls by 15 °C. What is the new temperature in °C?", 23, "38 − 15 = 23 °C.", unit="°C"),
    part_num("What is the fall of temperature in kelvin?", 15, "A temperature difference is the same in K and °C: 15 K.", unit="K"),
    part_open("Explain why the mercury level rises when the thermometer is placed in warm water.", "The mercury particles gain energy, move/vibrate more and take more space, so the liquid expands more than the glass and rises up the narrow tube.", "1 mark: particles gain energy; 1 mark: liquid expands/rises.")], tier="examen")
L.sc("Absolute zero is", "−273 °C", ["0 °C", "−100 °C", "273 °C"], "0 K = −273 °C.")
L.sc("300 K is", "27 °C", ["573 °C", "−27 °C", "300 °C"], "300 − 273 = 27.")
L.sc("Particles in a gas are", "far apart and moving freely", ["fixed in place", "touching and vibrating only", "stationary"], "That is the kinetic model of a gas.")
L.sc("Which substances usually expand most for the same temperature rise?", "gases", ["solids", "metals", "wood"], "Gases expand much more than liquids or solids.")
L.sc("The upper fixed point of the Celsius scale is", "100 °C", ["0 °C", "37 °C", "273 °C"], "Steam above boiling water at normal pressure.")

L = ch.lesson("gas-laws", "Gas laws", minutes=30, prerequisites=["gceol-physics-temperature-expansion", "gceol-physics-pressure"],
    objectives=["Explain gas pressure using the kinetic model.", "State and use Boyle's law P₁V₁ = P₂V₂.", "Use Charles's law and the pressure law with temperatures in kelvin.", "Solve gas problems with the correct unit conversions."],
    notes=["Whether the Board expects the combined formula P₁V₁/T₁ = P₂V₂/T₂ should be confirmed (it follows from the three laws)."])
L.key("definition", "Gas pressure", "Gas particles move randomly and hit the walls of their container. The impacts of many particles give a steady **pressure**. Heating the gas makes the particles faster, so the pressure (or volume) increases; compressing a gas into a smaller volume means more collisions with the walls.")
L.key("formule", "The three gas laws (fixed mass of gas)", "- **Boyle's law** (constant T): $P_1V_1 = P_2V_2$ — pressure and volume are inversely proportional.\n- **Charles's law** (constant P): $\\frac{V_1}{T_1} = \\frac{V_2}{T_2}$.\n- **Pressure law** (constant V): $\\frac{P_1}{T_1} = \\frac{P_2}{T_2}$.\n\n**T must be in kelvin.** The same units may be used for P (or V) on both sides.")
L.key("pieges", "Watch out", "- Never use °C in Charles's or the pressure law: add 273 first.\n- Boyle's law needs the **temperature to stay constant**; a fast pump heats the air.\n- A graph of P against V at constant T is a **curve**; P against 1/V is a straight line through the origin.")
L.illustration(plot(1, 10, 0, 65, curves=[("60/x", "blue", None)], points=[(2, 30, "P = 30", "red"), (6, 10, "P = 10", "red")], grid=1, xlabel="volume V", ylabel="pressure P", w=400, h=260),
    "Boyle's law: pressure falls as volume rises, PV is constant (here PV = 60 in arbitrary units).", "A downward curve of pressure against volume with two marked points where the product of pressure and volume is 60.")
P2b = 1e5 * 600 / 200; assert P2b == 3e5
L.example("Example 1 — Boyle's law", "Air of volume 600 cm³ at a pressure of 1.0 × 10⁵ Pa is compressed slowly to 200 cm³ at constant temperature. Find the new pressure.", ["P₁V₁ = P₂V₂, so 1.0 × 10⁵ × 600 = P₂ × 200.", "P₂ = 6.0 × 10⁷ ÷ 200 = 3.0 × 10⁵ Pa."], "3.0 × 10⁵ Pa")
P2t = 2.4e5 * (57 + 273) / (27 + 273); assert abs(P2t - 2.64e5) < 1e-6
L.example("Example 2 — car tyre", "A tyre (volume taken as constant) has air at 2.4 × 10⁵ Pa at 27 °C. After a long drive the air is at 57 °C. Find the new pressure.", ["Convert to kelvin: T₁ = 300 K, T₂ = 330 K.", "P₂ = P₁ × T₂ ÷ T₁ = 2.4 × 10⁵ × 330 ÷ 300 = 2.64 × 10⁵ Pa."], "2.64 × 10⁵ Pa")
L.num("A gas at 100 kPa has volume 6 litres. It is compressed at constant temperature to 2 litres. Find the new pressure in kPa.", 300, "100 × 6 = P × 2, so P = 300 kPa.", unit="kPa")
L.mcq("Which law links pressure and temperature at constant volume?", "The pressure law", ["Boyle's law", "Charles's law", "Newton's law"], "P/T = constant at constant volume.")
L.num("Convert 47 °C to kelvin before using a gas law.", 320, "47 + 273 = 320 K.", unit="K")
V2c = 300 * (273 + 100) / (273 + 0); close(V2c, 409.89, 0.01)
L.num("A gas has volume 300 cm³ at 0 °C. It is heated to 100 °C at constant pressure. Find the new volume (to the nearest cm³).", round(V2c), "V₂ = V₁ × T₂/T₁ = 300 × 373 ÷ 273 = 410 cm³.", unit="cm³", tolerance=1, tier="approfondissement", difficulty=2)
L.mcq("The pressure of a fixed mass of gas at constant temperature is doubled. Its volume becomes", "half", ["double", "the same", "four times"], "PV = constant, so if P doubles V halves.", tier="approfondissement")
P1 = 1.0e5; V1 = 80; V2 = 20; Pn = P1 * V1 / V2
L.problem("A bicycle pump holds 80 cm³ of air at atmospheric pressure (1.0 × 10⁵ Pa). The outlet is blocked and the piston is pushed in slowly until the volume is 20 cm³.", [
    part_num("Find the final pressure in pascals.", Pn, "P₂ = 1.0 × 10⁵ × 80 ÷ 20 = 4.0 × 10⁵ Pa.", unit="Pa"),
    part_num("By what factor has the pressure increased?", 4, "4.0 × 10⁵ ÷ 1.0 × 10⁵ = 4.", unit=None),
    part_open("Explain, using the particle model, why the pressure increases.", "The same number of particles are squeezed into a smaller volume, so they hit the walls more often per second, giving a larger force per unit area.", "1 mark: smaller volume / more collisions; 1 mark: larger force per unit area.")], tier="examen")
L.sc("Boyle's law says that at constant temperature", "pressure × volume is constant", ["pressure is constant", "volume is constant", "pressure ÷ volume is constant"], "PV = constant.")
L.sc("In gas laws, temperature must be in", "kelvin", ["degrees Celsius", "fahrenheit", "joules"], "The gas laws need the absolute scale.")
L.sc("Gas pressure is caused by", "particles colliding with the walls", ["particles' weight on the floor", "gas expanding only", "gravity only"], "Many impacts produce a steady force on the walls.")
L.sc("A gas at 300 K and 1 atm is heated to 600 K at constant volume. Its pressure becomes", "2 atm", ["0.5 atm", "1 atm", "4 atm"], "P ∝ T, so P doubles.")
L.sc("A graph of P against V at constant temperature is", "a curve", ["a straight line through the origin", "horizontal", "vertical"], "P ∝ 1/V gives a curve.")

L = ch.lesson("heat-capacity", "Specific heat capacity", minutes=25, prerequisites=["gceol-physics-temperature-expansion", "gceol-physics-work-energy-power"],
    objectives=["Define heat capacity and specific heat capacity.", "Use Q = mcΔθ and Pt = mcΔθ for electric heating.", "Use the method of mixtures (heat lost = heat gained).", "Explain why water is used as a coolant and for heating."],
    notes=["Values used: c(water) = 4200 J/kg °C; c(copper) about 400, c(aluminium) about 900, c(iron) about 450 J/kg °C — rounded school values; check against the school table."])
L.key("definition", "Specific heat capacity", "The **specific heat capacity (c)** of a substance is the energy needed to raise the temperature of **1 kg** by **1 °C** (unit **J/kg °C**). Water: c = **4200 J/kg °C** (this value is used in this course). The **heat capacity** of an object = mass × c (J/°C).")
L.formula(r"Q = mc\Delta\theta", "Q in joules, m in kg, Δθ = change in temperature (°C)")
L.key("methode", "Electric heater and mixtures", "- For an electric heater of power P working for time t (no losses): $Pt = mc\\Delta\\theta$.\n- **Method of mixtures**: when a hot and a cold liquid are mixed, **heat lost by the hot = heat gained by the cold** (if no heat is lost to the surroundings).")
L.key("pieges", "Watch out", "- Mass must be in **kg** (500 g = 0.5 kg).\n- Δθ = final − initial temperature.\n- Real experiments lose heat to the air and the container, so measured c is usually a bit too high.")
L.illustration(bars([("copper", 400, "orange"), ("iron", 450, "red"), ("aluminium", 900, "grey"), ("water", 4200, "blue")], unit="J/kg °C", w=400, h=240),
    "Approximate specific heat capacities: water needs much more energy per kg per degree than metals.", "A bar chart: copper about 400, iron about 450, aluminium about 900 and water 4200 joules per kilogram per degree Celsius.")
Q1 = 2 * 4200 * 50; t1 = Q1 / 2000; assert Q1 == 420000 and t1 == 210
L.example("Example 1 — heating water", "How much energy is needed to heat 2 kg of water from 25 °C to 75 °C? How long does a 2 kW heater take (no losses)?", ["Q = mcΔθ = 2 × 4200 × (75 − 25) = 2 × 4200 × 50 = 420 000 J.", "t = Q ÷ P = 420 000 ÷ 2000 = 210 s."], "420 kJ; 210 s")
Tf = (0.5 * 80 + 1.5 * 20) / 2.0; assert Tf == 35
L.example("Example 2 — mixing water", "0.5 kg of water at 80 °C is mixed with 1.5 kg of water at 20 °C. Find the final temperature (ignore losses).", ["Let the final temperature be θ. Heat lost = 0.5 × 4200 × (80 − θ); heat gained = 1.5 × 4200 × (θ − 20).", "Equate and divide by 4200: 40 − 0.5θ = 1.5θ − 30, so 70 = 2θ.", "θ = 35 °C."], "35 °C")
L.num("How much energy raises the temperature of 3 kg of water by 10 °C? (c = 4200 J/kg °C)", 126000, "Q = 3 × 4200 × 10 = 126 000 J.", unit="J")
L.mcq("Water is a good coolant in a car radiator mainly because it has", "a high specific heat capacity", ["a low density", "a low boiling point", "a dark colour"], "It absorbs a lot of energy for a small temperature rise.")
L.num("Convert 500 g to kg, then find the energy to heat 500 g of water by 20 °C.", 42000, "0.5 × 4200 × 20 = 42 000 J.", unit="J")
cc = 12000 / (0.5 * 60); assert cc == 400
L.num("A 0.5 kg metal block rises from 20 °C to 80 °C when given 12 000 J. Find its specific heat capacity.", cc, "c = Q ÷ (mΔθ) = 12 000 ÷ (0.5 × 60) = 400 J/kg °C.", unit="J/kg °C", tier="approfondissement", difficulty=2)
L.open("Suggest why the coastal city of Limbe has a smaller day-to-night temperature change than an inland Sahel town. (Give a reason based on water.)", "Water has a high specific heat capacity, so large bodies of water warm and cool slowly and moderate the air temperature nearby; dry land heats and cools quickly.", "1 mark: high specific heat capacity of water; 1 mark: slow heating/cooling gives a smaller temperature range.", tier="approfondissement", difficulty=2)
Qa = 1.5 * 4200 * (100 - 20); ta = Qa / 3000; assert Qa == 504000 and ta == 168
L.problem("An electric kettle of power 3 kW heats 1.5 kg of water from 20 °C to 100 °C (c = 4200 J/kg °C). Assume no energy is lost.", [
    part_num("Find the energy needed.", Qa, "Q = 1.5 × 4200 × 80 = 504 000 J.", unit="J"),
    part_num("Find the time taken in seconds.", ta, "t = Q ÷ P = 504 000 ÷ 3000 = 168 s.", unit="s"),
    part_num("In practice the kettle takes 200 s. Find the efficiency in % (to the nearest whole number).", round(ta / 200 * 100), "Efficiency = useful ÷ total = 168 ÷ 200 = 84%.", unit="%", tolerance=1)], tier="examen")
L.sc("The unit of specific heat capacity is", "J/kg °C", ["J/kg", "W/kg", "N/kg"], "Energy per kg per degree.")
L.sc("Q = mcΔθ gives the energy for", "a temperature change", ["a change of state", "a change of speed", "a force"], "It applies when the temperature changes without a change of state.")
L.sc("Water's specific heat capacity (this course) is", "4200 J/kg °C", ["420 J/kg °C", "42 J/kg °C", "4.2 J/kg °C"], "Standard school value.")
L.sc("In the method of mixtures", "heat lost = heat gained", ["mass lost = mass gained", "temperature lost = temperature gained", "power is zero"], "Conservation of energy.")
L.sc("A high specific heat capacity means the substance", "heats up slowly", ["heats up quickly", "cannot be heated", "has no mass"], "More energy is needed per degree.")

L = ch.lesson("change-of-state", "Change of state and latent heat", minutes=25, prerequisites=["gceol-physics-heat-capacity"],
    objectives=["Describe melting, boiling, evaporation, condensation and freezing using particles.", "Explain why temperature stays constant during a change of state.", "Define specific latent heat and use Q = ml.", "Distinguish evaporation from boiling."],
    notes=["Values used: specific latent heat of fusion of ice 336 000 J/kg; of vaporisation of water 2 260 000 J/kg (2.26 × 10⁶). Some school tables use 340 000 and 2 300 000."])
L.key("definition", "Latent heat", "While a substance melts or boils, it takes in energy but the **temperature stays constant**: the energy breaks the forces between particles instead of speeding them up. The **specific latent heat** is the energy needed to change **1 kg** of a substance from one state to another **without a change of temperature**: $Q = ml$ (J/kg).")
L.key("retenir", "Values for water (used here)", "- Specific latent heat of **fusion** (ice → water at 0 °C): **336 000 J/kg**.\n- Specific latent heat of **vaporisation** (water → steam at 100 °C): **2 260 000 J/kg**.\n\nVaporisation needs much more energy because the particles must be pulled completely apart.")
L.key("methode", "Evaporation and boiling", "**Boiling** happens throughout the liquid at one fixed temperature (the boiling point). **Evaporation** happens only at the surface, at any temperature, and it **cools** the liquid because the fastest particles escape. It is faster when warm, windy, with a large surface and dry air (that is why washing dries well in the dry season).")
L.key("pieges", "Watch out", "- Use Q = mcΔθ when the temperature **changes**; use Q = ml when the **state** changes at constant temperature.\n- On a heating curve the flat parts are the changes of state.")
L.illustration(plot(0, 10, -20, 120, segments=[(0, -10, 1, 0, "blue", False), (1, 0, 3, 0, "blue", False, "melting"), (3, 0, 6, 100, "blue", False), (6, 100, 9, 100, "blue", False, "boiling")],
                    grid=20, xlabel="time of heating", ylabel="temperature °C", w=400, h=260),
    "Heating curve of ice to steam: flat parts are melting at 0 °C and boiling at 100 °C.", "A temperature-time graph: a rise from −10 to 0 °C, a flat part at 0 °C, a rise to 100 °C, and a flat part at 100 °C.")
Qm = 0.2 * 336000; assert Qm == 67200
L.example("Example 1 — melting ice", "How much energy melts 0.2 kg of ice already at 0 °C?", ["Q = ml = 0.2 × 336 000.", "Q = 67 200 J."], "67 200 J")
Qt = 0.5 * 336000 + 0.5 * 4200 * 20; assert Qt == 210000
L.example("Example 2 — ice to warm water", "Find the energy to change 0.5 kg of ice at 0 °C into water at 20 °C.", ["Melting: Q₁ = 0.5 × 336 000 = 168 000 J.", "Warming the water from 0 °C to 20 °C: Q₂ = 0.5 × 4200 × 20 = 42 000 J.", "Total = 168 000 + 42 000 = 210 000 J."], "210 000 J")
L.num("Find the energy to boil away 0.5 kg of water already at 100 °C (L = 2.26 × 10⁶ J/kg).", 1130000, "Q = 0.5 × 2 260 000 = 1 130 000 J.", unit="J")
L.mcq("During melting of pure ice, the temperature", "stays at 0 °C", ["rises steadily", "falls", "rises to 100 °C"], "Energy is used to break bonds between particles, not to raise the temperature.")
L.match("Match each change of state with its name.", [["solid → liquid", "melting"], ["liquid → gas", "evaporation / boiling"], ["gas → liquid", "condensation"], ["liquid → solid", "freezing"]], "These are the standard names of the changes of state.")
tb = 1130000 / 2000; assert tb == 565
L.num("An electric heater of 2 kW boils away 0.5 kg of water at 100 °C (L = 2.26 × 10⁶ J/kg). How long does it take with no losses?", tb, "t = Q ÷ P = 1 130 000 ÷ 2000 = 565 s.", unit="s", tier="approfondissement", difficulty=2)
L.mcq("Why does sweating cool the body?", "The fastest particles escape from the liquid, lowering its average energy", ["Sweat is always cold", "It adds heat to the skin", "It stops evaporation"], "Evaporation removes the most energetic particles and takes latent heat from the skin.", tier="approfondissement")
Qs = 0.1 * 4200 * 80 + 0.1 * 2260000; assert Qs == 259600
L.problem("0.1 kg of water at 20 °C is heated and completely turned into steam at 100 °C (c = 4200 J/kg °C; L = 2.26 × 10⁶ J/kg).", [
    part_num("Find the energy to warm the water from 20 °C to 100 °C.", 0.1 * 4200 * 80, "Q₁ = 0.1 × 4200 × 80 = 33 600 J.", unit="J"),
    part_num("Find the energy to turn the water at 100 °C into steam.", 0.1 * 2260000, "Q₂ = 0.1 × 2 260 000 = 226 000 J.", unit="J"),
    part_num("Find the total energy.", Qs, "33 600 + 226 000 = 259 600 J.", unit="J")], tier="examen")
L.sc("The temperature of boiling water is constant at", "100 °C (normal pressure)", ["0 °C", "37 °C", "273 °C"], "Boiling point of water at normal pressure.")
L.sc("Latent heat is energy that", "changes the state without changing temperature", ["raises the temperature only", "is lost as light", "is stored as mass"], "Definition of latent heat.")
L.sc("Evaporation occurs", "at the surface at any temperature", ["only at 100 °C", "only inside the liquid", "only in sunlight"], "Boiling is throughout the liquid; evaporation only at the surface.")
L.sc("Q = ml is used for", "a change of state", ["a temperature rise in the same state", "a current", "a force"], "m = mass, l = specific latent heat.")
L.sc("Steam at 100 °C causes a worse burn than water at 100 °C because", "it releases latent heat when it condenses", ["it is heavier", "it is colder", "it has no energy"], "Condensation releases a large amount of energy.")

L = ch.lesson("heat-transfer", "Conduction, convection and radiation", minutes=25, prerequisites=["gceol-physics-temperature-expansion"],
    objectives=["Describe how conduction, convection and radiation transfer heat.", "Explain which surfaces are good absorbers and emitters.", "Explain the construction of a vacuum flask.", "Apply the ideas to everyday situations such as cooking, houses and clothes."])
L.key("definition", "Three ways heat travels", "- **Conduction**: transfer through a material by vibrating particles passing on energy (best in metals, which also have free electrons). Needs matter; main way in solids.\n- **Convection**: in liquids and gases, warm fluid becomes less dense and rises, cool fluid sinks, setting up a current.\n- **Radiation**: infrared waves; needs no medium; travels through a vacuum (heat from the Sun).")
L.key("retenir", "Surfaces", "Dull **black** surfaces are the best **absorbers and emitters** of radiation; shiny, light-coloured surfaces are the best **reflectors** and the worst emitters. Hence houses in hot regions are painted white, and cooking pots are black on the bottom but shiny on the outside to cut heat loss.")
L.key("methode", "Vacuum flask", "The flask has two glass walls with a **vacuum** between (no conduction or convection), **silvered** surfaces (reflect radiation) and a **stopper** that is a poor conductor (cuts conduction and convection at the top). Hot drinks stay hot and cold drinks stay cold.")
L.key("pieges", "Watch out", "- Convection and conduction are different: convection needs a fluid that can move.\n- Metals feel colder than wood at the same temperature because they conduct heat away from your hand faster, not because they are colder.")
L.illustration(shapes([RECT(130, 40, 140, 170, radius=10), RECT(150, 55, 100, 145, radius=8), LINE(143, 50, 143, 205, color="red", width=2, dash=True), LINE(257, 50, 257, 205, color="red", width=2, dash=True),
                       T(60, 120, "vacuum", 14), LINE(95, 120, 135, 120, arrow="end"), T(320, 120, "hot liquid", 14), LINE(285, 120, 250, 120, arrow="end"), T(200, 30, "stopper", 14)], 400, 240),
    "A vacuum flask: double wall with a vacuum between silvered surfaces.", "A flask drawn as two nested containers with a gap labelled vacuum, a hot liquid inside and a stopper on top.")
L.example("Example 1 — metal and wood", "Explain why a metal spoon feels colder than a wooden spoon when both have been in the same room.", ["Both are at room temperature, which is lower than your hand.", "Metal is a good conductor, so it removes heat from your hand quickly and feels cold; wood is a poor conductor, so little heat leaves your hand."], "Metal conducts heat away from the hand faster.")
L.example("Example 2 — heating water in a kettle", "Explain how the whole of the water in a kettle is heated when the element is at the bottom.", ["Water near the element warms, expands and becomes less dense, so it rises.", "Cooler, denser water sinks to take its place; the circulating convection current heats all the water."], "By convection currents.")
L.mcq("Heat from the Sun reaches the Earth mainly by", "radiation", ["conduction", "convection", "evaporation"], "Radiation can travel through the vacuum of space.")
L.tf("Convection can take place in a solid.", False, "Convection needs a fluid (liquid or gas) whose particles can flow.")
L.match("Match the example with the main method of heat transfer.", [["metal pan heated on a fire", "conduction"], ["warm air rising over a hot road", "convection"], ["warmth felt from the Sun", "radiation"]], "Metal handle/pan: conduction; rising air: convection; the Sun: radiation.")
L.mcq("Why are roofs in hot places often painted white or made of shiny metal?", "They reflect radiation and absorb less heat", ["They are black and absorb more", "They conduct heat better", "They are heavier"], "Light, shiny surfaces reflect most radiation.", tier="approfondissement")
L.open("Explain how the vacuum, the silvering and the stopper of a flask reduce heat loss.", "Vacuum: no particles, so no conduction or convection across the gap. Silvering: reflects radiation back inside (and poor emitter). Stopper: poor conductor and traps air, so little loss by conduction or convection at the top.", "1 mark per feature explained.", tier="approfondissement", difficulty=2)
L.problem("Air conditioners are placed high on the wall in a room in Douala while a heater is better placed low.", [
    part_mcq("Cold air from a high air conditioner moves downwards because", "cold air is denser and sinks", ["cold air is lighter", "cold air has no mass", "heat rises"], "Denser cold air sinks, setting up a convection current that cools the room."),
    part_mcq("A heater should be near the floor because", "warm air rises and circulates through the room", ["warm air sinks", "it conducts through walls", "it is safer"], "Warm air rises, so a low heater sets up a convection current."),
    part_open("State two ways a thick wall helps keep a house cool in the day.", "It is a poor conductor of heat so heat enters slowly; light-coloured outer surface reflects radiation; it has a high heat capacity so it warms slowly.", "1 mark for each valid idea (max 2).")], tier="examen")
L.sc("Metals are good conductors because", "they have free electrons", ["they are heavy", "they are shiny only", "they have no particles"], "Free electrons carry energy rapidly.")
L.sc("Convection happens in", "liquids and gases", ["solids", "vacuum", "metals only"], "Fluids can flow.")
L.sc("Which surface is the best emitter of radiation?", "dull black", ["shiny white", "shiny silver", "dull white"], "Dull black surfaces emit and absorb best.")
L.sc("The vacuum in a flask prevents", "conduction and convection", ["radiation only", "evaporation only", "nothing"], "No particles to carry heat.")
L.sc("Radiation does NOT need", "a medium", ["energy", "a temperature difference", "a source"], "It can travel through a vacuum.")

# =====================================================================  8. WAVES AND SOUND
ch = p.chapter("waves", "Waves", programRef="GCE O Level Physics — General wave properties, electromagnetic spectrum (to be checked against the official syllabus)")
L = ch.lesson("wave-properties", "Wave properties and the electromagnetic spectrum", minutes=30, prerequisites=["gceol-physics-speed-graphs"],
    objectives=["Define amplitude, wavelength, frequency, period and wave speed.", "Distinguish transverse and longitudinal waves.", "Use v = fλ and T = 1/f.", "List the electromagnetic spectrum in order and give uses."],
    notes=["Speed of electromagnetic waves in a vacuum: 3.0 × 10⁸ m/s (3 × 10⁸ used here).", "Uses and hazards of each EM band are given in summary; check the exact list required by the syllabus."])
L.key("definition", "Describing a wave", "A **wave** transfers energy without transferring matter. **Amplitude** = maximum displacement from the rest position; **wavelength λ** = distance between two successive crests; **frequency f** = number of waves per second (hertz, Hz); **period T** = time for one wave = 1/f.")
L.formula(r"v = f\lambda \qquad T = \frac{1}{f}", "v in m/s, f in Hz, λ in m")
L.key("retenir", "Two types of wave", "- **Transverse**: the vibration is at right angles to the direction of travel (water ripples, light, all electromagnetic waves, waves on a rope).\n- **Longitudinal**: the vibration is parallel to the direction of travel, with compressions and rarefactions (sound, a pushed spring).\n\nAll waves can be **reflected, refracted and diffracted**.")
L.key("retenir", "Electromagnetic spectrum", "From long to short wavelength (low to high frequency): **radio waves, microwaves, infrared, visible light, ultraviolet, X-rays, gamma rays**. All are transverse and travel at $3 \\times 10^8$ m/s in a vacuum. Uses: radio and TV communication, microwaves (cooking, mobile phones), infrared (remote controls, heating), ultraviolet (sterilising), X-rays (medical images), gamma rays (cancer treatment). Ultraviolet, X-rays and gamma rays can damage living cells.")
L.key("pieges", "Watch out", "- Convert units first: cm to m, MHz to Hz (1 MHz = 10⁶ Hz), kHz to Hz.\n- Amplitude is measured from the rest line to a crest, not from trough to crest.\n- Changing the frequency of a source does not change the wave speed in the same medium: the wavelength changes.")
L.illustration(plot(0, 8, -1.6, 1.6, curves=[("sin(3.1416*x/2)", "blue", None)], points=[(1, 1, "crest", "red"), (3, -1, "trough", "red")], segments=[(0, 1.3, 4, 1.3, "orange", False, "wavelength")], grid=1, xlabel="distance (cm)", ylabel="displacement (cm)", w=400, h=260),
    "A transverse wave of amplitude 1 cm and wavelength 4 cm.", "A sine curve with crest at 1 cm, trough at 3 cm and an orange bar showing one wavelength of 4 cm.")
vr = 15 * 0.02; assert abs(vr - 0.3) < 1e-9
L.example("Example 1 — ripples", "Water ripples have frequency 15 Hz and wavelength 2 cm. Find the wave speed in m/s.", ["Convert: λ = 2 cm = 0.02 m.", "v = fλ = 15 × 0.02 = 0.3 m/s."], "0.3 m/s")
lam = 3e8 / 100e6; assert lam == 3
L.example("Example 2 — FM radio", "A radio station in Yaoundé broadcasts at 100 MHz (1 MHz = 10⁶ Hz). Find the wavelength (c = 3 × 10⁸ m/s).", ["f = 100 × 10⁶ = 1 × 10⁸ Hz.", "λ = v ÷ f = 3 × 10⁸ ÷ 1 × 10⁸ = 3 m."], "3 m")
L.num("A wave has frequency 5 Hz and wavelength 0.4 m. Find its speed.", 2, "v = fλ = 5 × 0.4 = 2 m/s.", unit="m/s")
L.num("A sound source vibrates at 250 Hz. Find the period in seconds.", 0.004, "T = 1/f = 1/250 = 0.004 s.", unit="s", tolerance=1e-6)
L.mcq("Which of these is a longitudinal wave?", "sound", ["light", "radio waves", "X-rays"], "Sound consists of compressions and rarefactions along the direction of travel.")
L.match("Match each electromagnetic wave with a use.", [["radio waves", "broadcasting"], ["infrared", "TV remote control"], ["ultraviolet", "sterilising"], ["X-rays", "medical images of bones"]], "Standard uses of each band.", tier="approfondissement")
fq = 3e8 / 600; close(fq, 5e5)
L.num("A radio wave of wavelength 600 m travels at 3 × 10⁸ m/s. Find its frequency in kHz.", fq / 1e3, "f = v/λ = 3 × 10⁸ ÷ 600 = 5 × 10⁵ Hz = 500 kHz.", unit="kHz", tier="approfondissement", difficulty=2)
fr = 12 / 3; lm = 5; vv = fr * lm; tcross = 100 / vv
L.problem("In a ripple tank 12 waves pass a point in 3 s. The wavelength is 5 cm.", [
    part_num("Find the frequency in Hz.", fr, "f = 12 ÷ 3 = 4 Hz.", unit="Hz"),
    part_num("Find the wave speed in cm/s.", vv, "v = fλ = 4 × 5 = 20 cm/s.", unit="cm/s"),
    part_num("How long does a wave take to travel 1 m (100 cm)?", tcross, "t = 100 ÷ 20 = 5 s.", unit="s")], tier="examen")
L.sc("The unit of frequency is the", "hertz", ["metre", "second", "watt"], "1 Hz = one wave per second.")
L.sc("Which has the shortest wavelength?", "gamma rays", ["radio waves", "visible light", "infrared"], "Gamma rays are at the high-frequency end of the spectrum.")
L.sc("In a transverse wave the vibrations are", "perpendicular to the direction of travel", ["parallel to the direction of travel", "absent", "circular only"], "Definition of a transverse wave.")
L.sc("v = fλ. If f doubles at constant speed, λ", "halves", ["doubles", "stays the same", "quadruples"], "λ = v/f.")
L.sc("A wave transfers", "energy", ["matter only", "mass", "charge only"], "Waves carry energy without carrying matter.")

ch = p.chapter("sound", "Sound", programRef="GCE O Level Physics — Sound: production, speed, echoes, pitch and loudness (to be checked against the official syllabus)")
L = ch.lesson("sound-echo", "Sound: speed, echoes, pitch and loudness", minutes=25, prerequisites=["gceol-physics-wave-properties"],
    objectives=["Explain how sound is produced and why it needs a medium.", "Use speed of sound v = d/t and echo calculations.", "Relate pitch to frequency and loudness to amplitude.", "Give the audible range and uses of ultrasound."],
    notes=["Speed of sound used: 340 m/s in air (about 330–343 m/s depending on temperature); about 1500 m/s in sea water. Check the Board's preferred value."])
L.key("definition", "Sound", "Sound is produced by **vibrating objects** and travels as a **longitudinal wave** through a medium (solid, liquid or gas). It cannot travel through a vacuum. In air its speed is about **340 m/s** (used here); it is faster in liquids and fastest in solids. The audible range of a normal human ear is about **20 Hz to 20 000 Hz**; above is **ultrasound**.")
L.key("retenir", "Pitch and loudness", "- **Pitch** depends on the **frequency**: high frequency, high pitch.\n- **Loudness** depends on the **amplitude**: bigger amplitude, louder sound.\n\n**Echo**: sound reflected from a hard surface. The sound travels to the surface and back, so distance = speed × time ÷ 2.")
L.key("methode", "Uses of ultrasound", "Ultrasound is used for medical scans (e.g. of a baby in the womb), for cleaning delicate objects, and for **sonar** that measures the depth of the sea or finds fish.")
L.key("pieges", "Watch out", "- In an echo calculation divide by 2: the sound makes a round trip.\n- Light travels so fast (3 × 10⁸ m/s) that, in lightning calculations, its travel time is ignored.\n- Sound cannot travel in a vacuum, unlike light.")
L.illustration(shapes([RECT(40, 120, 20, 50, fill="orange"), RECT(320, 70, 20, 150, fill="grey"), LINE(70, 135, 310, 135, color="blue", width=3, arrow="end"), LINE(310, 155, 70, 155, color="red", width=3, arrow="end"),
                       T(190, 120, "sound goes", 14), T(190, 180, "echo returns", 14), T(190, 215, "distance d each way", 14)], 400, 240),
    "An echo: the sound travels to the wall and back, a total distance of 2d.", "A person on the left, a wall on the right, a blue arrow going to the wall and a red arrow returning.")
d1 = 340 * 0.6 / 2; assert abs(d1 - 102) < 1e-9
L.example("Example 1 — echo", "A pupil shouts and hears an echo from a cliff 0.6 s later (speed of sound 340 m/s). How far is the cliff?", ["Total distance travelled = v × t = 340 × 0.6 = 204 m.", "The cliff is half of that: 204 ÷ 2 = 102 m."], "102 m")
d2 = 340 * 4; assert d2 == 1360
L.example("Example 2 — thunder", "A thunderclap is heard 4 s after the lightning flash is seen. How far away is the storm?", ["The flash arrives almost instantly, so the 4 s is the travel time of the sound.", "d = v × t = 340 × 4 = 1360 m."], "About 1360 m (1.4 km)")
L.num("Find the distance sound travels in 5 s in air (340 m/s).", 1700, "d = vt = 340 × 5 = 1700 m.", unit="m")
L.mcq("The pitch of a sound depends on its", "frequency", ["amplitude", "speed", "wavelength only"], "Higher frequency gives higher pitch.")
L.tf("Sound can travel through a vacuum.", False, "Sound needs a medium of particles to carry the vibrations.")
dsea = 1500 * 0.4 / 2; assert dsea == 300
L.num("A ship's sonar sends a pulse and receives the echo from the sea bed after 0.4 s. If the speed of sound in sea water is 1500 m/s, find the depth.", dsea, "Round trip distance = 1500 × 0.4 = 600 m, depth = 600 ÷ 2 = 300 m.", unit="m", tier="approfondissement", difficulty=2)
L.mcq("An oscilloscope trace of a sound shows taller waves of the same spacing. The sound is", "louder, same pitch", ["quieter, higher pitch", "louder, higher pitch", "same loudness, lower pitch"], "Taller waves = larger amplitude (louder); same spacing = same frequency (pitch).", tier="approfondissement")
techo = 2 * 85 / 340; fk = 440; Tp = 1 / fk; lamk = 340 / fk
L.problem("A boy stands 85 m from a tall wall and claps once (speed of sound 340 m/s). A tuning fork of frequency 440 Hz is also sounded.", [
    part_num("Find the time between the clap and the echo.", techo, "t = 2d ÷ v = 2 × 85 ÷ 340 = 0.5 s.", unit="s", tolerance=0.001),
    part_num("Find the period of the 440 Hz tuning fork in milliseconds (1 ms = 0.001 s), to 2 d.p.", round(Tp * 1000, 2), "T = 1/440 = 0.00227 s = 2.27 ms.", unit="ms", tolerance=0.01),
    part_num("Find the wavelength of its sound in air (to 2 d.p.).", round(lamk, 2), "λ = v ÷ f = 340 ÷ 440 = 0.77 m.", unit="m", tolerance=0.01)], tier="examen")
L.sc("The speed of sound in air is about", "340 m/s", ["3 × 10⁸ m/s", "34 m/s", "3400 m/s"], "About 340 m/s at normal temperature.")
L.sc("Loudness depends on the", "amplitude", ["frequency", "period", "wavelength"], "Larger amplitude, louder sound.")
L.sc("Ultrasound has a frequency", "above 20 000 Hz", ["below 20 Hz", "between 20 and 200 Hz", "of exactly 1000 Hz"], "Above the human audible range.")
L.sc("Sound is a", "longitudinal wave", ["transverse wave", "light wave", "radio wave"], "Compressions and rarefactions along the travel direction.")
L.sc("An echo is heard 2 s after a shout. The wall is (v = 340 m/s)", "340 m away", ["680 m away", "170 m away", "1360 m away"], "d = 340 × 2 ÷ 2 = 340 m.")

# =====================================================================  9. LIGHT
ch = p.chapter("light", "Light", programRef="GCE O Level Physics — Light: reflection, refraction, total internal reflection, lenses, dispersion (to be checked against the official syllabus)")
L = ch.lesson("reflection", "Reflection of light and plane mirrors", minutes=25,
    objectives=["State that light travels in straight lines and explain shadows.", "State and use the laws of reflection.", "Describe the image formed by a plane mirror.", "Use simple ray diagrams, periscopes and rotating mirrors."])
L.key("definition", "Laws of reflection", "Light travels in straight lines (**rectilinear propagation**), which explains shadows and eclipses. When light hits a smooth surface it is **reflected**:\n\n1. The angle of incidence **i** equals the angle of reflection **r**.\n2. The incident ray, the reflected ray and the **normal** all lie in the same plane.\n\nAngles are measured from the **normal** (the line at 90° to the mirror).")
L.key("retenir", "Image in a plane mirror", "The image is **virtual** (cannot be put on a screen), **upright**, the **same size** as the object, as far **behind** the mirror as the object is in front, and **laterally inverted** (left and right swapped). A **periscope** uses two parallel mirrors at 45° to see over obstacles.")
L.key("pieges", "Watch out", "- Angles i and r are measured from the **normal**, not from the mirror surface.\n- A virtual image is not formed by real light rays meeting; it cannot be caught on a screen.\n- If a mirror turns through an angle θ, the reflected ray turns through **2θ**.")
L.illustration(shapes([LINE(60, 170, 340, 170, width=4), LINE(200, 170, 200, 40, dash=True, color="grey"), LINE(90, 60, 200, 170, color="red", width=3, arrow="end"), LINE(200, 170, 310, 60, color="red", width=3, arrow="end"),
                       T(200, 28, "normal", 14), T(165, 115, "i", 16), T(238, 115, "r", 16), T(200, 200, "mirror", 14)], 400, 240),
    "Reflection at a plane mirror: angle of incidence i equals angle of reflection r.", "A horizontal mirror with a vertical dashed normal, an incident ray from upper left and a reflected ray to upper right, with angles i and r marked.")
i1 = 90 - 35; assert i1 == 55
L.example("Example 1 — angle with the mirror", "A ray hits a plane mirror making an angle of 35° with the mirror surface. Find the angle of incidence, the angle of reflection and the angle between the incident and reflected rays.", ["The normal is at 90° to the mirror, so i = 90° − 35° = 55°.", "By the law of reflection r = i = 55°.", "Angle between incident and reflected rays = i + r = 110°."], "i = 55°, r = 55°, angle between rays 110°")
L.example("Example 2 — person and image", "A pupil stands 2 m in front of a plane mirror. (a) How far is the image from the mirror and from the pupil? (b) The pupil walks 0.5 m towards the mirror: how far is she then from her image?", ["(a) The image is 2 m behind the mirror, so it is 2 + 2 = 4 m from the pupil.", "(b) The pupil is now 1.5 m from the mirror, the image is 1.5 m behind it: distance 3 m."], "(a) 2 m and 4 m; (b) 3 m")
L.num("A ray makes an angle of 40° with a plane mirror. Find the angle of reflection.", 50, "i = 90° − 40° = 50°, so r = 50°.", unit="°")
L.tf("The image in a plane mirror is real.", False, "It is virtual: no light actually passes through the image position.")
L.mcq("An object is 3 m in front of a plane mirror. The distance from the object to its image is", "6 m", ["3 m", "1.5 m", "9 m"], "The image is 3 m behind the mirror: total 6 m.")
L.num("A plane mirror is turned through 20°. Through what angle does a fixed reflected ray turn?", 40, "The reflected ray turns through twice the angle of the mirror: 2 × 20° = 40°.", unit="°", tier="approfondissement", difficulty=2)
L.mcq("The word AMBULANCE is painted on the front of an ambulance as reversed writing so that", "drivers see it correctly in their rear-view mirror", ["it is cheaper", "it looks nicer", "light is reflected away"], "Lateral inversion in the mirror turns it the right way round.", tier="approfondissement")
L.problem("A pupil of height 1.6 m stands 1.5 m in front of a vertical plane mirror in a school hall in Garoua.", [
    part_num("How far behind the mirror is her image?", 1.5, "The image is as far behind the mirror as she is in front: 1.5 m.", unit="m"),
    part_num("How far is her image from her?", 3.0, "1.5 + 1.5 = 3.0 m.", unit="m"),
    part_num("What is the height of the image?", 1.6, "The image is the same size as the object: 1.6 m.", unit="m"),
    part_open("State two characteristics of the image apart from its size.", "It is virtual and upright (and laterally inverted).", "1 mark each; any two of virtual, upright, laterally inverted.")], tier="examen")
L.sc("The angle of reflection equals the", "angle of incidence", ["angle of refraction", "critical angle", "angle with the mirror"], "Law of reflection.")
L.sc("The normal is a line", "at 90° to the surface", ["along the surface", "at 45° to the surface", "parallel to the ray"], "Angles are measured from it.")
L.sc("An image in a plane mirror is", "virtual and the same size as the object", ["real and magnified", "real and diminished", "virtual and magnified"], "Standard properties.")
L.sc("A periscope uses", "two plane mirrors", ["two lenses only", "a prism and a bulb", "no mirrors"], "Parallel mirrors at 45°.")
L.sc("A ray hits a mirror with i = 30°. The angle between the incident and reflected rays is", "60°", ["30°", "90°", "15°"], "i + r = 30° + 30° = 60°.")

L = ch.lesson("refraction", "Refraction, total internal reflection and dispersion", minutes=30, prerequisites=["gceol-physics-reflection"],
    objectives=["Explain refraction as a change of speed and direction at a boundary.", "Use n = sin i / sin r and n = c/v.", "State the conditions for total internal reflection and find the critical angle.", "Describe dispersion of white light by a prism."],
    notes=["Refractive indices used: glass 1.5, water 1.33 (about 4/3). Speed of light in vacuum 3 × 10⁸ m/s.", "Apparent depth formula (n = real depth ÷ apparent depth) assumes viewing nearly from above; check if required."])
L.key("definition", "Refraction", "**Refraction** is the change of direction of light when it passes from one transparent medium to another, caused by the change of speed. Going from air into glass or water (slower) the ray bends **towards the normal**; leaving, it bends **away**. The **refractive index** is $n = \\frac{\\sin i}{\\sin r} = \\frac{c}{v}$ (i in the air, r in the medium; c = speed of light in vacuum).")
L.key("formule", "Total internal reflection (TIR)", "TIR happens when light travels from a **denser to a less dense** medium (e.g. glass to air) and the angle of incidence is **greater than the critical angle c**. Then all the light is reflected inside. $\\sin c = \\frac{1}{n}$. Used in **optical fibres**, prism periscopes and reflectors on bicycles.")
L.key("retenir", "Dispersion", "White light passing through a **prism** splits into a spectrum: **red, orange, yellow, green, blue, indigo, violet**. Red is bent least and violet most because the speed (and so the refractive index) depends on the colour. A rainbow is dispersion by raindrops.")
L.key("pieges", "Watch out", "- Calculators must be in degree mode for sine problems.\n- Light bends towards the normal when it enters a **denser** medium, not away.\n- TIR needs **both** conditions: denser to less dense, and i > c.")
L.illustration(shapes([RECT(60, 130, 280, 90, fill="grey", stroke="ink"), LINE(200, 40, 200, 215, dash=True, color="grey"), LINE(95, 50, 200, 130, color="red", width=3, arrow="end"),
                       LINE(200, 130, 245, 215, color="red", width=3, arrow="end"), T(255, 75, "air", 14), T(300, 175, "glass", 14), T(150, 80, "i", 16), T(215, 175, "r", 16)], 400, 240),
    "Refraction at an air-glass boundary: the ray bends towards the normal as it enters glass.", "A glass block with a dashed vertical normal; a ray from upper left bends towards the normal inside the glass.")
n = 1.5; sr = math.sin(math.radians(45)) / n; rr = math.degrees(math.asin(sr)); close(rr, 28.1255, 0.01)
L.example("Example 1 — Snell's law", "A ray in air hits glass (n = 1.5) at an angle of incidence of 45°. Find the angle of refraction.", ["n = sin i ÷ sin r, so sin r = sin 45° ÷ 1.5 = 0.7071 ÷ 1.5 = 0.4714.", "r = sin⁻¹(0.4714) = 28.1°."], "r ≈ 28.1°")
cg = math.degrees(math.asin(1 / 1.5)); cw = math.degrees(math.asin(1 / 1.33)); close(cg, 41.81, 0.01); close(cw, 48.75, 0.05)
L.example("Example 2 — critical angle", "Find the critical angle for glass (n = 1.5) and for water (n = 1.33).", ["sin c = 1/n. Glass: sin c = 1/1.5 = 0.667, so c = 41.8°.", "Water: sin c = 1/1.33 = 0.752, so c = 48.8°."], "c(glass) ≈ 41.8°; c(water) ≈ 48.8°")
L.num("Find the speed of light in glass of refractive index 1.5 (c = 3 × 10⁸ m/s). Give your answer in 10⁸ m/s.", 2, "v = c ÷ n = 3 × 10⁸ ÷ 1.5 = 2 × 10⁸ m/s.", unit="×10⁸ m/s")
L.mcq("A ray passes from air into water. It bends", "towards the normal", ["away from the normal", "not at all", "back into the air"], "Water is optically denser; light slows down and bends towards the normal.")
L.tf("Total internal reflection can occur when light goes from air into glass.", False, "TIR requires travel from a denser to a less dense medium.")
L.num("Find the critical angle (in degrees) of a material of refractive index 2.", 30, "sin c = 1/2 = 0.5, so c = 30°.", unit="°", tier="approfondissement", tolerance=0.1, difficulty=2)
dap = 2.0 / 1.33
L.num("A swimming pool is 2.0 m deep (n = 1.33). Looking vertically down, the apparent depth is real depth ÷ n. Find the apparent depth (to 1 d.p.).", round(dap, 1), "Apparent depth = 2.0 ÷ 1.33 = 1.5 m.", unit="m", tolerance=0.05, tier="approfondissement", difficulty=2)
L.problem("A ray of light in air strikes a glass block (n = 1.5) at an angle of incidence of 30°.", [
    part_num("Find the angle of refraction (to the nearest degree).", round(math.degrees(math.asin(math.sin(math.radians(30)) / 1.5))), "sin r = sin 30° ÷ 1.5 = 0.5 ÷ 1.5 = 0.333; r = 19.5°, about 20°.", unit="°", tolerance=1),
    part_num("Find the speed of light in the glass in 10⁸ m/s.", 2, "v = 3 × 10⁸ ÷ 1.5 = 2 × 10⁸ m/s.", unit="×10⁸ m/s"),
    part_num("Find the critical angle of the glass in degrees (nearest degree).", round(cg), "sin c = 1/1.5, c = 41.8°, about 42°.", unit="°", tolerance=1),
    part_mcq("A ray inside the glass hits the glass-air surface at 50°. What happens?", "Total internal reflection, since 50° is more than 42°", ["It refracts out into the air", "It is absorbed", "It stops"], "i = 50° is greater than the critical angle (about 42°), so TIR occurs.")], tier="examen")
L.sc("The refractive index n equals", "sin i ÷ sin r", ["sin r ÷ sin i", "i ÷ r", "v ÷ c"], "Snell's law with i in the air.")
L.sc("Which colour is refracted most by a prism?", "violet", ["red", "yellow", "green"], "Violet has the highest refractive index.")
L.sc("TIR needs light travelling from", "denser to less dense medium with i > c", ["less dense to denser", "air to water", "any medium to any medium"], "Both conditions are needed.")
L.sc("n = 2. The speed of light in the material is", "c/2", ["2c", "c", "c/4"], "v = c/n.")
L.sc("Optical fibres work because of", "total internal reflection", ["dispersion", "diffraction", "reflection in a plane mirror"], "Light is trapped inside the glass fibre.")

L = ch.lesson("lenses", "Lenses, images and the eye", minutes=30, prerequisites=["gceol-physics-refraction"],
    objectives=["Draw ray diagrams for converging lenses.", "Describe the images formed for different object positions.", "Use 1/f = 1/u + 1/v and magnification m = v/u.", "Describe the camera, magnifying glass and eye, and the correction of sight defects."],
    notes=["Sign convention: 'real-is-positive' is used (u, v, f positive for real objects/images and a converging lens); virtual images have negative v. Check the convention the Board expects.", "Ray-diagram scale and the exact set of instruments required should be checked against the syllabus."])
L.key("definition", "Converging lens", "A **converging (convex) lens** bends parallel rays to meet at the **principal focus F**; the **focal length f** is the distance from the lens to F. Three rays are used: (1) a ray parallel to the axis passes through F; (2) a ray through the optical centre is not deviated; (3) a ray through F emerges parallel to the axis.")
L.key("retenir", "Image positions (converging lens)", "- Object beyond **2f**: image real, inverted, smaller (camera).\n- At **2f**: real, inverted, same size.\n- Between **f and 2f**: real, inverted, magnified (projector).\n- Closer than **f**: virtual, upright, magnified (magnifying glass).")
L.formula(r"\frac{1}{f} = \frac{1}{u} + \frac{1}{v} \qquad m = \frac{v}{u} = \frac{h_i}{h_o}", "real-is-positive convention")
L.key("attention", "The eye", "The eye has a converging lens that forms a real inverted image on the **retina**. **Short sight** (myopia): distant objects blurred, corrected by a **diverging** lens. **Long sight**: near objects blurred, corrected by a **converging** lens. A **camera** is similar: lens, adjustable aperture and a film or sensor.")
L.illustration(shapes([LINE(200, 20, 200, 220, width=3), LINE(40, 120, 380, 120, width=1), T(130, 142, "F", 14), T(270, 142, "F", 14),
                       LINE(60, 120, 60, 80, color="blue", width=3, arrow="end"), LINE(340, 120, 340, 160, color="blue", width=3, arrow="end"),
                       LINE(60, 80, 200, 80, color="red", width=2), LINE(200, 80, 340, 160, color="red", width=2), LINE(60, 80, 200, 120, color="orange", width=2), LINE(200, 120, 340, 160, color="orange", width=2),
                       T(60, 62, "object", 14), T(340, 185, "image", 14)], 400, 240),
    "Object at 2f from a converging lens: the image is real, inverted, same size, at 2f on the other side.", "A converging lens with two focal points marked, an object arrow on the left and an inverted image of the same size on the right, with two construction rays.")
u1 = 40; f1 = 10; v1_ = 1 / (1 / f1 - 1 / u1); m1 = v1_ / u1; close(v1_, 13.333, 0.01); close(m1, 1 / 3, 1e-6)
L.example("Example 1 — camera-like image", "A 6 cm tall object is 40 cm from a converging lens of focal length 10 cm. Find the image distance, the magnification and the image height.", ["1/v = 1/f − 1/u = 1/10 − 1/40 = 3/40, so v = 40/3 = 13.3 cm.", "m = v/u = 13.3 ÷ 40 = 1/3.", "Image height = m × 6 = 2 cm (real, inverted, diminished)."], "v ≈ 13.3 cm; m = 1/3; image 2 cm")
u2 = 15; v2_ = 1 / (1 / f1 - 1 / u2); m2 = v2_ / u2; close(v2_, 30); close(m2, 2)
L.example("Example 2 — projector-like image", "An object is 15 cm from a converging lens of focal length 10 cm. Find v and the magnification.", ["1/v = 1/10 − 1/15 = 1/30, so v = 30 cm.", "m = v/u = 30 ÷ 15 = 2: the image is real, inverted and twice the size of the object."], "v = 30 cm; m = 2")
L.num("A converging lens of f = 5 cm forms an image of an object 10 cm away. Find the image distance.", 10, "1/v = 1/5 − 1/10 = 1/10, so v = 10 cm (the object is at 2f).", unit="cm")
L.mcq("An object is placed closer to a converging lens than its focal length. The image is", "virtual, upright and magnified", ["real, inverted and magnified", "real and diminished", "no image"], "This is how a magnifying glass works.")
L.mcq("A person who cannot see distant objects clearly (short sight) uses a", "diverging lens", ["converging lens", "plane mirror", "prism"], "A diverging lens corrects short sight.")
f3 = 12; u3 = 36; v3 = 1 / (1 / f3 - 1 / u3); assert abs(v3 - 18) < 1e-9
L.num("An object is 36 cm from a converging lens of focal length 12 cm. Find the image distance.", v3, "1/v = 1/12 − 1/36 = 2/36, so v = 18 cm.", unit="cm", tier="approfondissement", difficulty=2, tolerance=0.01)
L.tf("The image formed on the retina is real and inverted.", True, "The brain interprets it as upright.", tier="approfondissement")
f4 = 8; u4 = 12; v4 = 1 / (1 / f4 - 1 / u4); m4 = v4 / u4; assert abs(v4 - 24) < 1e-9 and abs(m4 - 2) < 1e-9
L.problem("An object of height 3 cm is placed 12 cm from a converging lens of focal length 8 cm.", [
    part_num("Find the image distance.", v4, "1/v = 1/8 − 1/12 = 1/24, so v = 24 cm.", unit="cm", tolerance=0.01),
    part_num("Find the magnification.", m4, "m = v/u = 24 ÷ 12 = 2.", unit=None, tolerance=0.01),
    part_num("Find the height of the image in cm.", 3 * m4, "Image height = m × object height = 2 × 3 = 6 cm.", unit="cm", tolerance=0.01),
    part_mcq("Describe the image.", "real, inverted, magnified", ["virtual, upright, magnified", "real, inverted, diminished", "virtual, inverted"], "The object is between f and 2f, so the image is real, inverted and magnified.")], tier="examen")
L.sc("The principal focus of a converging lens is where", "parallel rays meet", ["light is absorbed", "rays diverge", "the image is virtual"], "Definition.")
L.sc("A camera lens forms an image that is", "real, inverted and smaller", ["virtual and upright", "real and magnified", "virtual and small"], "Distant objects are beyond 2f.")
L.sc("Magnification m =", "v/u", ["u/v", "u × v", "f/v"], "Image distance ÷ object distance.")
L.sc("Long sight is corrected by a", "converging lens", ["diverging lens", "plane mirror", "prism"], "A converging lens adds focusing power.")
L.sc("The image on the retina is", "real and inverted", ["virtual and upright", "real and upright", "virtual and inverted"], "Interpreted upright by the brain.")

# =====================================================================  10. ELECTRICITY
ch = p.chapter("static", "Static electricity", programRef="GCE O Level Physics — Electrostatics: charging, conductors and insulators, uses and dangers (to be checked against the official syllabus)")
L = ch.lesson("static-electricity", "Static electricity", minutes=20,
    objectives=["State that there are two kinds of charge and the force between them.", "Explain charging by friction in terms of electrons.", "Distinguish conductors and insulators.", "Describe uses and dangers of static electricity and the lightning conductor."],
    notes=["Charging by induction and the gold-leaf electroscope are described only briefly; add if the Board requires diagrams.", "The charge of an electron (1.6 × 10⁻¹⁹ C) is not used in this lesson."])
L.key("definition", "Charge", "There are two kinds of electric charge, **positive (+)** and **negative (−)**. **Like charges repel; unlike charges attract.** Charge is measured in **coulombs (C)**. An atom is neutral; a body becomes charged when it **gains or loses electrons** (negative charges).")
L.key("retenir", "Charging by friction", "When a polythene rod is rubbed with a cloth, **electrons move from the cloth to the rod**: the rod becomes negative and the cloth positive (equal and opposite). A glass rod rubbed with silk loses electrons and becomes positive. Only **electrons** move; the positive charges stay in the nuclei.")
L.key("methode", "Conductors, insulators, uses and dangers", "**Conductors** (metals, graphite, the human body) allow charge to flow; **insulators** (plastic, glass, rubber, dry air) do not. Uses: paint sprayers, photocopiers, dust removal. Dangers: sparks igniting fuel (tankers are earthed with a chain), and lightning. A **lightning conductor** is a pointed thick metal strip connected to the earth to carry the charge safely.")
L.key("pieges", "Watch out", "- Positive charges do not move in solids; electrons do.\n- A charged object attracts a neutral light object (such as paper bits) by induction: this is not repulsion.\n- Rubbing does not create charge; it only moves it.")
L.illustration(shapes([CIRCLE(70, 110, 24), T(70, 117, "+", 24), CIRCLE(170, 110, 24), T(170, 117, "−", 24), LINE(100, 110, 140, 110, arrow="both", color="red"), T(120, 80, "attract", 14),
                       CIRCLE(250, 110, 24), T(250, 117, "+", 24), CIRCLE(350, 110, 24), T(350, 117, "+", 24), LINE(278, 110, 322, 110, arrow="both", color="blue"), T(300, 160, "repel", 14)], 400, 240),
    "Unlike charges attract; like charges repel.", "Four charged circles: a plus and a minus joined by arrows labelled attract, and two pluses labelled repel.")
L.example("Example 1 — charging a rod", "A polythene rod is rubbed with a dry cloth and becomes negatively charged. Explain what happens to the cloth.", ["Electrons are transferred by friction from the cloth to the rod.", "The cloth loses electrons, so it is left with a positive charge equal in size to the charge on the rod."], "The cloth becomes positively charged.")
L.example("Example 2 — repulsion test", "A charged rod repels a hanging charged ball but attracts it when the rod is replaced by another charged rod. Explain how the test shows the signs of the charges.", ["Repulsion happens only between like charges, so the first rod has the same sign as the ball.", "Attraction occurs between unlike charges (or a charged and neutral object), so a second rod that attracts may have the opposite sign: repulsion is the reliable test."], "Repulsion proves the charges have the same sign.")
L.mcq("Two negatively charged balloons are brought near each other. They", "repel", ["attract", "do nothing", "become neutral"], "Like charges repel.")
L.tf("When a rod is charged by rubbing, protons move from the cloth to the rod.", False, "Only electrons move in a solid; protons stay in the nuclei.")
L.match("Match the material with its type.", [["copper wire", "allows charge to flow easily"], ["dry plastic", "does not allow charge to flow"], ["lightning conductor", "carries charge safely to the earth"]], "Copper is a conductor, plastic is an insulator, and a lightning conductor is a thick metal strip joined to the earth.")
L.mcq("A rod is charged by rubbing and attracts small pieces of paper. This is because", "the paper is neutral but charges in it are rearranged by induction", ["the paper has a net charge opposite to the rod", "the rod is hot", "paper is a conductor of heat"], "Charges shift in the paper, so the nearer part has an opposite charge and attraction wins.", tier="approfondissement", difficulty=2)
L.open("Why are fuel tankers fitted with a metal chain or strap touching the ground?", "Friction between fuel and the tank can build up charge; the chain is a conductor that carries the charge safely to earth so that no spark ignites the fuel vapour.", "1 mark: charge builds up by friction; 1 mark: chain conducts it to earth; 1 mark: prevents spark/fire.", tier="approfondissement")
L.problem("A glass rod is rubbed with silk and becomes positively charged.", [
    part_mcq("What moved when the glass rod was rubbed?", "Electrons moved from the rod to the silk", ["Protons moved from the silk to the rod", "Electrons moved from the silk to the rod", "Nothing moved"], "A positive glass rod has lost electrons; the silk gains them and becomes negative."),
    part_mcq("What is the sign of the charge on the silk?", "negative", ["positive", "neutral", "it depends on colour"], "It gained the electrons that the rod lost."),
    part_mcq("The positive glass rod is brought near a hanging negatively charged ball. The ball", "is attracted", ["is repelled", "does not move", "becomes positive"], "Unlike charges attract.")], tier="examen")
L.sc("Like charges", "repel", ["attract", "cancel", "neutralise"], "Like charges repel.")
L.sc("The particles that move when a rod is charged by friction are", "electrons", ["protons", "neutrons", "nuclei"], "Electrons are free to move.")
L.sc("Which is an insulator?", "dry rubber", ["copper", "graphite", "iron"], "Rubber does not conduct charge.")
L.sc("The unit of charge is the", "coulomb", ["ampere", "volt", "watt"], "1 C = 1 A for 1 s.")
L.sc("A lightning conductor is connected to the", "earth", ["roof only", "TV aerial", "power line"], "It carries the charge to ground.")

ch = p.chapter("current", "Current electricity", programRef="GCE O Level Physics — Current electricity: charge, current, p.d., resistance, Ohm's law, series and parallel circuits (to be checked against the official syllabus)")
L = ch.lesson("current-pd-resistance", "Current, potential difference and resistance", minutes=30, prerequisites=["gceol-physics-static-electricity"],
    objectives=["Define electric current, potential difference and resistance with units.", "Use Q = It, V = IR.", "Connect an ammeter in series and a voltmeter in parallel.", "State Ohm's law and interpret I-V graphs."],
    notes=["Resistivity formula R = ρl/A may be required in some papers; here only the proportionalities (R ∝ l, R ∝ 1/A) are given."])
L.key("definition", "Current, p.d., resistance", "- **Current I** = charge flowing per second: $I = \\frac{Q}{t}$ (ampere, A; 1 A = 1 C/s). Conventional current flows from + to −; the electrons really move the opposite way.\n- **Potential difference V** (volt, V) = energy transferred per coulomb: $V = \\frac{W}{Q}$.\n- **Resistance R** (ohm, Ω) = V ÷ I.")
L.formula(r"V = IR \qquad I = \frac{V}{R} \qquad R = \frac{V}{I}", "Ohm's law for a metal at constant temperature")
L.key("retenir", "Measuring", "An **ammeter** is connected in **series** (in line); a **voltmeter** is connected in **parallel** across the component. **Ohm's law**: for a metallic conductor at constant temperature, the current is directly proportional to the p.d. (the I-V graph is a straight line through the origin). The resistance of a wire increases with its length and decreases with its cross-sectional area; it also depends on the material.")
L.key("pieges", "Watch out", "- A filament lamp does **not** obey Ohm's law: its resistance rises as the filament gets hotter, so its I-V graph curves.\n- Never connect an ammeter in parallel: its resistance is very low and it would carry too much current.\n- Convert mA to A (÷ 1000) and minutes to seconds.")
Y = 70
cir = [LINE(80, Y, 147, Y), *meter(160, Y, "A"), LINE(173, Y, 250, Y), *resistor(270, Y), LINE(290, Y, 330, Y), LINE(330, Y, 330, 190), LINE(330, 190, 80, 190), LINE(80, 190, 80, 155), LINE(80, 145, 80, Y),
       *battery(80, 150), LINE(250, Y, 250, 125), LINE(250, 125, 257, 125), *meter(270, 125, "V"), LINE(283, 125, 290, 125), LINE(290, 125, 290, Y), T(40, 155, "cell", 14), T(270, 40, "R", 14)]
L.illustration(shapes(cir, 400, 240), "A circuit with an ammeter A in series and a voltmeter V in parallel with the resistor R.", "A rectangular circuit with a cell, an ammeter in the top wire, a resistor, and a voltmeter connected across the resistor.")
Q = 0.5 * 120; assert Q == 60
L.example("Example 1 — charge", "A current of 0.5 A flows through a lamp for 2 minutes. How much charge passes through the lamp?", ["Time t = 2 × 60 = 120 s.", "Q = It = 0.5 × 120 = 60 C."], "60 C")
R1 = 12 / 0.4; assert abs(R1 - 30) < 1e-9
L.example("Example 2 — resistance", "A voltmeter reads 12 V across a resistor and the ammeter reads 0.4 A. Find the resistance, and the current if the p.d. is raised to 18 V.", ["R = V ÷ I = 12 ÷ 0.4 = 30 Ω.", "I = V ÷ R = 18 ÷ 30 = 0.6 A."], "R = 30 Ω; I = 0.6 A")
L.num("Find the p.d. across a 20 Ω resistor carrying a current of 0.3 A.", 6, "V = IR = 0.3 × 20 = 6 V.", unit="V")
L.mcq("How should a voltmeter be connected?", "In parallel with the component", ["In series with the component", "Directly across the cell only", "Either way"], "A voltmeter measures the p.d. across a component, so it is connected in parallel.")
L.num("A current of 200 mA flows for 30 s. Find the charge in coulombs.", 6, "I = 0.2 A, Q = 0.2 × 30 = 6 C.", unit="C")
L.num("A resistor has I = 2 A when V = 10 V. Find the current when V = 25 V (Ohm's law).", 5, "R = 10 ÷ 2 = 5 Ω; I = 25 ÷ 5 = 5 A.", unit="A", tier="approfondissement")
L.mcq("A wire of resistance R is replaced by a wire of the same material and area but twice as long. Its resistance is now", "2R", ["R/2", "R", "4R"], "Resistance is proportional to length.", tier="approfondissement", difficulty=2)
Rt = 9 / 0.6; assert abs(Rt - 15) < 1e-9
L.problem("In an experiment a pupil in Bali measures the p.d. and current for a resistor: (2.0 V, 0.1 A), (4.0 V, 0.2 A), (6.0 V, 0.3 A) and (9.0 V, 0.45 A).", [
    part_num("Find the resistance from the first reading in ohms.", 2.0 / 0.1, "R = 2.0 ÷ 0.1 = 20 Ω.", unit="Ω"),
    part_num("Find the resistance from the last reading in ohms.", 9.0 / 0.45, "R = 9.0 ÷ 0.45 = 20 Ω.", unit="Ω"),
    part_mcq("What do the results show?", "The resistor obeys Ohm's law because R is constant", ["Ohm's law fails", "Current is constant", "The resistor is a lamp"], "R is constant at 20 Ω, so I is proportional to V."),
    part_num("What charge flows in 10 s at the 0.3 A reading?", 3, "Q = It = 0.3 × 10 = 3 C.", unit="C")], tier="examen")
L.sc("The unit of resistance is the", "ohm", ["volt", "ampere", "coulomb"], "R = V/I, measured in ohms.")
L.sc("An ammeter is connected", "in series", ["in parallel", "across the cell", "only to a switch"], "It must carry the whole current.")
L.sc("V = 12 V, I = 3 A. R is", "4 Ω", ["36 Ω", "0.25 Ω", "9 Ω"], "R = V/I = 12/3 = 4.")
L.sc("A current of 2 A flows for 5 s. The charge is", "10 C", ["2.5 C", "7 C", "0.4 C"], "Q = It = 10.")
L.sc("An I-V graph of a resistor obeying Ohm's law is", "a straight line through the origin", ["a curve", "horizontal", "a circle"], "I ∝ V.")

L = ch.lesson("series-parallel", "Series and parallel circuits", minutes=30, prerequisites=["gceol-physics-current-pd-resistance"],
    objectives=["State the rules for current and p.d. in series and parallel circuits.", "Calculate the combined resistance of resistors in series and parallel.", "Solve circuit problems step by step.", "Compare series and parallel wiring in the home."])
L.key("retenir", "Series circuit", "Components in one loop. The **current is the same** everywhere; the p.d. **adds up** ($V = V_1 + V_2$); the total resistance is $R = R_1 + R_2$. If one component breaks, all stop (old Christmas lights).")
L.key("formule", "Parallel circuit", "Components on separate branches. The **p.d. is the same** across each branch; the currents **add up** ($I = I_1 + I_2$); the total resistance is smaller than the smallest branch: $\\frac{1}{R} = \\frac{1}{R_1} + \\frac{1}{R_2}$ (for two resistors $R = \\frac{R_1R_2}{R_1+R_2}$). House lights and sockets are in parallel so each works independently at full voltage.")
L.key("pieges", "Watch out", "- Parallel: do not add the resistances; the total is **less** than each one.\n- After using $1/R$, take the reciprocal to get R.\n- Check your answer: the total resistance of two resistors in parallel must be smaller than the smaller one.")
L.illustration(shapes([LINE(70, 60, 300, 60), LINE(70, 190, 300, 190), LINE(300, 60, 300, 190), LINE(70, 60, 70, 145), LINE(70, 155, 70, 190), *battery(70, 150),
                       LINE(140, 60, 140, 105), *resistor(140, 125, 16, 40), LINE(140, 145, 140, 190), LINE(220, 60, 220, 105), *resistor(220, 125, 16, 40), LINE(220, 145, 220, 190),
                       T(172, 130, "6 Ω", 14), T(252, 130, "3 Ω", 14), T(110, 40, "12 V", 14)], 400, 240),
    "Two resistors, 6 Ω and 3 Ω, in parallel across a 12 V cell.", "A battery connected across two parallel branches, one with a 6 ohm resistor and one with a 3 ohm resistor.")
Rs = 4 + 8; Is = 12 / Rs; V4 = Is * 4; V8 = Is * 8; assert Rs == 12 and Is == 1 and V4 == 4 and V8 == 8
L.example("Example 1 — series", "A 4 Ω and an 8 Ω resistor are in series with a 12 V supply. Find the total resistance, the current and the p.d. across each resistor.", ["R = 4 + 8 = 12 Ω.", "I = V ÷ R = 12 ÷ 12 = 1 A (same through both).", "V₁ = IR₁ = 1 × 4 = 4 V and V₂ = 1 × 8 = 8 V; check 4 + 8 = 12 V."], "R = 12 Ω; I = 1 A; 4 V and 8 V")
Rp = 1 / (1 / 6 + 1 / 3); Ip = 12 / Rp; assert abs(Rp - 2) < 1e-9 and abs(Ip - 6) < 1e-9
L.example("Example 2 — parallel", "A 6 Ω and a 3 Ω resistor are in parallel across a 12 V supply. Find the total resistance, each branch current and the total current.", ["1/R = 1/6 + 1/3 = 1/6 + 2/6 = 3/6, so R = 2 Ω.", "I₁ = 12 ÷ 6 = 2 A and I₂ = 12 ÷ 3 = 4 A (same p.d. across each).", "Total current = 2 + 4 = 6 A (check: 12 ÷ 2 = 6 A)."], "R = 2 Ω; 2 A and 4 A; total 6 A")
L.num("Find the total resistance of 5 Ω, 10 Ω and 20 Ω in series.", 35, "R = 5 + 10 + 20 = 35 Ω.", unit="Ω")
L.num("Two 10 Ω resistors are connected in parallel. Find the total resistance.", 5, "1/R = 1/10 + 1/10 = 2/10, so R = 5 Ω.", unit="Ω")
L.mcq("In a series circuit the current is", "the same at every point", ["larger near the cell", "zero in the lamp", "split between components"], "There is only one path, so current is the same everywhere.")
Rp2 = 1 / (1 / 12 + 1 / 4); Rtot = 3 + Rp2; I_tot = 9 / Rtot
L.num("A 3 Ω resistor is in series with a parallel pair of 12 Ω and 4 Ω. The supply is 9 V. Find the total current.", I_tot, "Parallel pair: 1/R = 1/12 + 1/4 = 4/12, R = 3 Ω. Total R = 3 + 3 = 6 Ω; I = 9 ÷ 6 = 1.5 A.", unit="A", tier="approfondissement", difficulty=3, tolerance=0.01)
L.tf("If one lamp in a parallel circuit breaks, the others go out.", False, "Each branch is independent; only the broken lamp's branch stops.", tier="approfondissement")
Rx = 6 + 4; Ix = 20 / Rx; Vx = Ix * 6
L.problem("A 6 Ω and a 4 Ω resistor are connected in series to a 20 V battery.", [
    part_num("Find the total resistance.", Rx, "6 + 4 = 10 Ω.", unit="Ω"),
    part_num("Find the current.", Ix, "I = 20 ÷ 10 = 2 A.", unit="A"),
    part_num("Find the p.d. across the 6 Ω resistor.", Vx, "V = 2 × 6 = 12 V.", unit="V"),
    part_num("The two resistors are now connected in parallel to the same battery. Find the current through the 4 Ω resistor.", 5, "Each branch has the full 20 V: I = 20 ÷ 4 = 5 A.", unit="A")], tier="examen")
L.sc("In a parallel circuit the p.d. across each branch is", "the same", ["different", "zero", "halved"], "All branches are connected across the same two points.")
L.sc("Total resistance of 3 Ω and 6 Ω in series is", "9 Ω", ["2 Ω", "18 Ω", "4.5 Ω"], "3 + 6 = 9.")
L.sc("Total resistance of 3 Ω and 6 Ω in parallel is", "2 Ω", ["9 Ω", "4.5 Ω", "18 Ω"], "1/R = 1/3 + 1/6 = 1/2.")
L.sc("Household appliances are connected in", "parallel", ["series", "a ring of one lamp only", "none"], "So each gets full voltage and works independently.")
L.sc("In a parallel circuit the total current is", "the sum of the branch currents", ["the same as one branch", "the difference", "zero"], "Current splits at junctions.")

L = ch.lesson("power-domestic", "Electrical power, energy and safety at home", minutes=30, prerequisites=["gceol-physics-series-parallel", "gceol-physics-work-energy-power"],
    objectives=["Use P = VI, P = I²R, E = Pt and the kilowatt-hour.", "Calculate the cost of electrical energy.", "Choose a suitable fuse rating.", "Describe live, neutral and earth wires and safety precautions."],
    notes=["Mains voltage taken as 220 V (about 220–240 V in different places); frequency 50 Hz. Check against local practice.", "Electricity prices are NOT real: the exercises assume a tariff of 100 FCFA per kWh for practice only. The real tariff varies by consumer category and changes over time.", "Plug and wire colour codes are not stated because practice differs between countries."])
L.key("formule", "Power and energy", "- Electrical power: $P = VI$ (watts). With Ohm's law also $P = I^2R = \\frac{V^2}{R}$.\n- Electrical energy: $E = Pt$ (joules, with P in W and t in s) $= VIt$.\n- **Kilowatt-hour (kWh)**: the energy used by a 1 kW appliance in 1 hour. Energy (kWh) = power (kW) × time (h); 1 kWh = 3 600 000 J. Cost = kWh × price per kWh.")
L.key("retenir", "House wiring and safety", "Mains has a **live** wire (dangerous voltage), a **neutral** wire (return path) and an **earth** wire (safety, to the metal case). A **fuse** (thin wire that melts) or **circuit breaker** is placed in the **live** wire to cut off the current if it is too high. The fuse rating should be **just above** the normal current of the appliance. The earth wire carries a fault current away so that the case never stays live.")
L.key("pieges", "Safety rules", "- Never touch switches or sockets with wet hands; water conducts.\n- Do not overload a socket with many appliances (overheating and fire).\n- Switches and fuses go in the **live** wire so that the appliance is dead when switched off.\n- Replace a blown fuse with one of the **correct** rating, never with wire or foil.")
L.illustration(shapes([RECT(250, 50, 110, 140, radius=6), T(305, 40, "metal case", 14), LINE(60, 80, 150, 80), RECT(150, 72, 40, 16), LINE(190, 80, 250, 80), T(170, 62, "fuse", 14),
                       LINE(60, 120, 250, 120), LINE(60, 160, 250, 160, dash=True), T(35, 85, "L", 16), T(35, 125, "N", 16), T(35, 165, "E", 16), T(305, 175, "appliance", 14)], 400, 240),
    "Simplified wiring: live (L) through a fuse, neutral (N), and earth (E) connected to the metal case.", "An appliance box with a live wire passing through a fuse, a neutral wire and a dashed earth wire joined to the metal case.")
I_k = 2000 / 220; assert abs(I_k - 9.09) < 0.01
L.example("Example 1 — kettle current", "A kettle is rated 2.0 kW, 220 V. Find the current it draws and the resistance of its element.", ["P = VI, so I = P ÷ V = 2000 ÷ 220 = 9.09 A.", "R = V ÷ I = 220 ÷ 9.09 = 24.2 Ω (or V²/P = 48 400 ÷ 2000 = 24.2 Ω)."], "I ≈ 9.1 A; R ≈ 24.2 Ω")
kwh = 1.5 * 4 * 30; cost = kwh * 100; assert kwh == 180 and cost == 18000
L.example("Example 2 — cost of energy", "A 1.5 kW heater runs 4 hours a day for 30 days. Find the energy used in kWh and its cost at 100 FCFA per kWh (an assumed price for practice).", ["Energy = 1.5 kW × (4 × 30) h = 1.5 × 120 = 180 kWh.", "Cost = 180 × 100 = 18 000 FCFA."], "180 kWh; 18 000 FCFA")
L.num("A lamp draws 0.5 A from a 220 V supply. Find its power.", 110, "P = VI = 220 × 0.5 = 110 W.", unit="W")
L.num("How many kWh does a 100 W lamp use in 10 hours?", 1, "0.1 kW × 10 h = 1 kWh.", unit="kWh")
L.mcq("In which wire must the fuse be placed?", "the live wire", ["the neutral wire", "the earth wire", "any wire"], "So that the appliance is disconnected from the high voltage if the fuse blows.")
Pp = 60 * 20 ; Pw = 3 ** 2 * 5; assert Pw == 45
L.num("A 5 Ω resistor carries 3 A. Find the power it dissipates.", Pw, "P = I²R = 9 × 5 = 45 W.", unit="W", tier="approfondissement")
L.mcq("A toaster is rated 700 W at 220 V. The best fuse from 3 A, 5 A and 13 A is", "5 A", ["3 A", "13 A", "no fuse is needed"], "Normal current I = 700/220 = 3.2 A. A 3 A fuse would blow in normal use; 13 A is far above the normal current so it would not protect the flex. 5 A is the best choice.", tier="approfondissement", difficulty=3)
Ib = 3000 / 220
L.problem("An electric cooker in Douala is rated 3.3 kW on a 220 V supply and is used for 2 hours each day. (Assume 100 FCFA per kWh.)", [
    part_num("Find the current.", 3300 / 220, "I = P ÷ V = 3300 ÷ 220 = 15 A.", unit="A"),
    part_num("Find the energy used per day in kWh.", 3.3 * 2, "3.3 × 2 = 6.6 kWh.", unit="kWh", tolerance=0.01),
    part_num("Find the cost for 30 days in FCFA.", 3.3 * 2 * 30 * 100, "6.6 × 30 × 100 = 19 800 FCFA.", unit="FCFA", tolerance=1),
    part_open("Explain the purpose of the earth wire connected to the metal case.", "If the live wire touches the case, a large current flows to earth through the earth wire, blowing the fuse and making the appliance safe so that the user is not electrocuted.", "1 mark: carries fault current to earth; 1 mark: fuse blows/case not live.")], tier="examen")
L.sc("Electrical power P =", "V × I", ["V ÷ I", "I ÷ V", "V + I"], "P = VI.")
L.sc("1 kWh equals", "1 kW used for 1 hour", ["1 W used for 1 h", "1000 J", "1 kW used for 1 s"], "Definition.")
L.sc("A fuse should be placed in the", "live wire", ["neutral wire", "earth wire", "case"], "To isolate the appliance.")
L.sc("A 60 W bulb works for 5 h. Energy used is", "0.3 kWh", ["3 kWh", "0.03 kWh", "12 kWh"], "0.06 kW × 5 h = 0.3 kWh.")
L.sc("The earth wire is connected to", "the metal case", ["the fuse only", "the switch only", "the neutral wire inside the lamp"], "So a fault current flows to earth.")

# =====================================================================  11. MAGNETISM
ch = p.chapter("magnetism", "Magnetism", programRef="GCE O Level Physics — Magnetism: magnets, fields, magnetisation (to be checked against the official syllabus)")
L = ch.lesson("magnets-fields", "Magnets and magnetic fields", minutes=20,
    objectives=["State the laws of magnetic poles and the magnetic materials.", "Describe the magnetic field of a bar magnet using field lines.", "Distinguish soft iron and steel and describe magnetisation and demagnetisation.", "Describe the use of a plotting compass and the Earth's magnetic field."])
L.key("definition", "Magnets", "A magnet has two **poles**, north (N) and south (S). **Like poles repel; unlike poles attract.** Magnetic materials are **iron, steel, nickel and cobalt**; copper, aluminium and plastic are not. Only a magnet **repels** another magnet; a magnet attracts both magnets and unmagnetised magnetic materials (by induction).")
L.key("retenir", "Magnetic fields", "A **magnetic field** is the region round a magnet in which a magnetic force acts. **Field lines** leave the **N pole** and enter the **S pole**; where they are closest the field is strongest (at the poles). A **plotting compass** shows the field direction (its N end points along the field). The Earth has a magnetic field; a compass needle points roughly to the geographic north.")
L.key("methode", "Magnetising and demagnetising", "- **Soft iron** is easily magnetised and loses its magnetism easily (electromagnets, transformer cores).\n- **Steel** is hard to magnetise but keeps magnetism (permanent magnets, compass needles).\n- Magnetise by stroking with a magnet, or by placing in a coil carrying direct current; demagnetise by an alternating current in a coil, hammering or heating.")
L.key("pieges", "Watch out", "- The only sure test for a magnet is **repulsion**; attraction could be a magnet and an iron bar.\n- Magnetic field lines never cross.\n- The north pole of a compass is attracted to the Earth's magnetic south pole, which is near the geographic north.")
L.illustration(shapes([RECT(150, 105, 50, 30, fill="red"), RECT(200, 105, 50, 30, fill="blue"), T(175, 125, "S", 16), T(225, 125, "N", 16),
                       PATH("M 250 110 C 300 30 100 30 150 110"), PATH("M 250 130 C 300 210 100 210 150 130"), LINE(215, 58, 185, 58, arrow="end"), LINE(185, 182, 215, 182, arrow="start")], 400, 240),
    "Magnetic field lines of a bar magnet: from the north pole round to the south pole.", "A bar magnet with its south pole on the left and north on the right and curved lines leaving N and returning to S, with arrows showing direction.")
L.example("Example 1 — sorting materials", "A pupil tests an iron nail, a copper coin, a steel pin and a plastic ruler with a magnet. Which are attracted?", ["Iron and steel are magnetic materials, so the nail and the pin are attracted.", "Copper and plastic are not magnetic, so the coin and ruler are not attracted."], "The nail and the pin")
L.example("Example 2 — identifying a magnet", "Bar X is attracted to both ends of bar Y. Bar X repels one end of bar Z. Which bars are magnets?", ["Repulsion happens only between two magnets, so X and Z are magnets.", "Y attracts X at both ends but we cannot say it is a magnet: it could be unmagnetised iron attracted by the magnet X."], "X and Z are magnets; Y may be only iron")
L.mcq("Which of these materials is attracted by a magnet?", "nickel", ["copper", "aluminium", "wood"], "Iron, steel, nickel and cobalt are magnetic.")
L.tf("Magnetic field lines go from the south pole to the north pole outside the magnet.", False, "They go from N to S outside the magnet.")
L.mcq("Which is the best material for a permanent magnet?", "steel", ["soft iron", "copper", "aluminium"], "Steel keeps its magnetism.")
L.mcq("The only certain test to show that an object is a magnet is", "repulsion with a known magnet", ["attraction with a known magnet", "its colour", "its weight"], "Both a magnet and unmagnetised iron are attracted; only a magnet can repel.", tier="approfondissement", difficulty=2)
L.open("A pupil wants to make a steel needle into a compass needle. Describe how.", "Stroke the needle many times in the same direction with one pole of a strong magnet (or place it in a coil carrying direct current); then suspend it so it can turn freely, and it will settle in a north-south direction.", "1 mark: stroking/DC coil; 1 mark: same direction; 1 mark: free to turn so it points N-S.", tier="approfondissement")
L.problem("A pupil holds a bar magnet near a hanging magnetic needle. The needle is repelled by the right-hand end of the bar magnet.", [
    part_mcq("The right-hand end of the bar magnet and the near end of the needle are", "poles of the same kind", ["opposite poles", "not poles", "both unmagnetised"], "Repulsion occurs between like poles."),
    part_mcq("What happens if the bar magnet is turned round?", "The needle is attracted", ["The needle is repelled again", "Nothing happens", "The needle loses its magnetism"], "The opposite pole now faces the needle: unlike poles attract."),
    part_open("Explain why soft iron is used for the core of an electromagnet.", "Soft iron is easily magnetised and demagnetised, so the magnetism switches on and off with the current.", "1 mark: easily magnetised; 1 mark: loses magnetism when current stops.")], tier="examen")
L.sc("Like magnetic poles", "repel", ["attract", "cancel", "join"], "Like poles repel.")
L.sc("Field lines leave the", "north pole", ["south pole", "middle", "iron"], "From N to S outside the magnet.")
L.sc("A plotting compass is used to", "show the direction of the magnetic field", ["measure current", "measure mass", "cut wires"], "Its needle lines up with the field.")
L.sc("Which is NOT magnetic?", "aluminium", ["iron", "cobalt", "steel"], "Aluminium is not a magnetic material.")
L.sc("Steel is used for permanent magnets because it", "keeps its magnetism", ["loses magnetism easily", "is not magnetic", "is a liquid"], "It is hard to demagnetise.")

ch = p.chapter("electromagnetism", "Electromagnetism", programRef="GCE O Level Physics — Electromagnetism: magnetic effect of current, motor effect, induction, transformer (to be checked against the official syllabus)")
L = ch.lesson("electromagnets-motors", "Magnetic effect of a current and the motor effect", minutes=30, prerequisites=["gceol-physics-magnets-fields", "gceol-physics-current-pd-resistance"],
    objectives=["Describe the magnetic field of a straight wire and a solenoid.", "State the factors that affect the strength of an electromagnet.", "Use Fleming's left-hand rule for the force on a current-carrying wire.", "Describe a simple d.c. motor and uses of electromagnets."],
    notes=["Rule conventions (right-hand grip rule, Fleming's left-hand rule) are standard but the hand version used in school should match the textbook.", "Details of the commutator and split rings are summarised.", "Which end of a solenoid is N depends on the winding direction; the figure does not show it, so the ends are only labelled as poles."])
L.key("definition", "Magnetic field of a current", "A current in a wire produces a **magnetic field**: circles round a straight wire (**right-hand grip rule**: thumb along the current, fingers curl along the field). A **solenoid** (coil) has a field like a bar magnet. A coil with a **soft iron core** is an **electromagnet**.")
L.key("retenir", "Electromagnet strength and uses", "The field is stronger with **more turns**, a **bigger current** and a **soft iron core**. Reversing the current reverses the poles. It can be switched on and off. Uses: **electric bell**, **relay**, **scrap-metal crane**, loudspeaker.")
L.key("formule", "Motor effect", "A wire carrying a current in a magnetic field feels a **force**. **Fleming's left-hand rule**: first finger = **F**ield (N to S), second finger = **C**urrent (+ to −), thumb = **T**hrust (motion). The force is bigger with a larger current, a stronger field and a longer wire in the field. Reversing the current or the field reverses the force.")
L.key("pieges", "Watch out", "- Use the **left** hand for motors and the **right** hand for the field-direction grip rule.\n- The force is zero if the wire is parallel to the field.\n- In a d.c. motor the commutator reverses the current every half-turn so that the coil keeps turning in the same direction.")
L.illustration(shapes([LINE(120, 150, 280, 150), *[LINE(130 + 20 * k, 110, 130 + 20 * k, 190, width=1) for k in range(7)], T(110, 210, "coil", 14),
                       LINE(40, 150, 120, 150, color="blue", width=3, arrow="end"), T(75, 135, "current", 14), T(300, 150, "pole", 14), T(100, 90, "pole", 14)], 400, 240),
    "A solenoid (coil) carrying a current acts like a bar magnet: one end is a north pole and the other a south pole, depending on the direction of the current.", "A horizontal coil drawn as vertical turns on a line, a current arrow entering from the left and the two ends labelled as poles.")
L.example("Example 1 — strengthening an electromagnet", "A pupil makes an electromagnet with a nail, wire and a cell. Give three ways to make it stronger.", ["Increase the number of turns of wire round the nail.", "Increase the current (more cells, or lower resistance).", "Keep a soft iron core (a thick iron nail)."], "More turns, more current, soft iron core")
L.example("Example 2 — direction of force", "A straight wire carries a current towards you (out of the page). The magnetic field is from left to right (N on the left, S on the right). State the direction of the force on the wire.", ["Left hand: first finger points right (field N to S); second finger points towards you (current).", "The thumb, perpendicular to both, then points up the page: the wire is pushed upwards."], "The force acts upwards")
L.mcq("Which of these makes an electromagnet stronger?", "more turns of wire", ["a copper core", "a smaller current", "a plastic core"], "More turns and more current increase the field.")
L.tf("The force on a current-carrying wire in a magnetic field is zero if the wire is parallel to the field.", True, "The force acts only when the current has a component across the field.")
L.mcq("Which part of an electric bell is the electromagnet used for?", "Pulling the hammer against the gong", ["Holding the batteries", "Cooling the bell", "Making the sound inside the coil"], "The magnet attracts the iron armature so the hammer strikes the gong.")
L.mcq("Soft iron, not steel, is used as the core of a relay because it", "loses its magnetism when the current is switched off", ["is stronger", "keeps its magnetism for ever", "is cheaper than copper"], "The relay must switch off quickly.", tier="approfondissement")
L.open("State three ways to increase the speed of a simple d.c. motor.", "Increase the current, use a stronger magnet (magnetic field), or increase the number of turns on the coil.", "1 mark each; any three.", tier="approfondissement")
L.problem("A simple d.c. motor has a rectangular coil between two permanent magnets.", [
    part_mcq("What rule gives the direction of the force on one side of the coil?", "Fleming's left-hand rule", ["Right-hand grip rule", "Snell's law", "Ohm's law"], "Left-hand rule gives the force on a current in a magnetic field."),
    part_mcq("What is the purpose of the commutator?", "To reverse the current in the coil every half-turn", ["To store charge", "To strengthen the magnets", "To reduce friction"], "Without it the coil would flip back and stop."),
    part_open("What happens to the direction of rotation if both the current and the magnetic field are reversed?", "The rotation stays the same, because reversing both reverses the force twice.", "1 mark: same direction; 1 mark: two reversals cancel.")], tier="examen")
L.sc("A coil with an iron core carrying current is an", "electromagnet", ["permanent magnet", "generator", "resistor"], "Definition.")
L.sc("Fleming's left-hand rule gives the direction of", "the force on a current in a field", ["induced current", "voltage", "frequency"], "Motor rule.")
L.sc("Increasing the number of turns of an electromagnet", "increases its strength", ["decreases it", "has no effect", "reverses its poles"], "More turns, stronger field.")
L.sc("A relay uses a", "small current to switch a large current", ["large current to switch a small one", "lamp only", "transformer"], "An electromagnet closes the second circuit.")
L.sc("A d.c. motor transfers", "electrical energy to kinetic energy", ["heat to light", "kinetic to electrical", "chemical to sound"], "Electrical to mechanical.")

L = ch.lesson("induction-transformers", "Electromagnetic induction and transformers", minutes=30, prerequisites=["gceol-physics-electromagnets-motors"],
    objectives=["State Faraday's observation: an e.m.f. is induced when the magnetic field through a coil changes.", "Describe a simple a.c. generator.", "Use Vp/Vs = Np/Ns and power balance for an ideal transformer.", "Explain why electricity is transmitted at high voltage."],
    notes=["Lenz's law is stated qualitatively; direction rules (Fleming's right-hand rule) depend on the textbook.", "Cameroon's grid voltages are not quoted; only the general principle is given."])
L.key("definition", "Electromagnetic induction", "When a magnet moves into or out of a coil (or the magnetic field through the coil changes), an **e.m.f. is induced** and a current flows in a complete circuit. The e.m.f. is larger with a **faster motion**, a **stronger magnet** and **more turns**. The direction opposes the change that causes it (**Lenz's law**). No motion, no e.m.f.")
L.key("retenir", "Generator", "A **generator** turns a coil in a magnetic field (or a magnet near a coil) so that an alternating e.m.f. is induced: **mechanical energy → electrical energy**. Dynamos on bicycles and hydroelectric turbines work on this principle. The output is **a.c.** (alternating): the current reverses direction regularly.")
L.formula(r"\frac{V_s}{V_p} = \frac{N_s}{N_p}", "ideal transformer: V_p I_p = V_s I_s")
L.key("retenir", "Transformer", "Two coils on a soft-iron core. An **alternating** current in the **primary** coil makes a changing magnetic field that induces an e.m.f. in the **secondary** coil. A **step-up** transformer has more turns on the secondary (higher voltage); a **step-down** has fewer. It works only with a.c. and for an ideal transformer, input power = output power.")
L.key("pieges", "Watch out", "- A transformer does not work with steady d.c.: the field must keep changing.\n- A step-up transformer gives a higher voltage but a **smaller** current.\n- Power lines use high voltage so that the current is small and the heat loss $I^2R$ in the cables is small.")
L.illustration(shapes([RECT(120, 50, 160, 140), RECT(150, 80, 100, 80), *[LINE(130, 70 + 18 * k, 150, 70 + 18 * k, width=3) for k in range(6)], *[LINE(250, 70 + 18 * k, 270, 70 + 18 * k, width=3) for k in range(3)],
                       T(70, 125, "primary", 14), T(335, 125, "secondary", 14), T(200, 30, "soft iron core", 14), LINE(40, 80, 120, 80), LINE(40, 170, 120, 170), LINE(280, 80, 360, 80), LINE(280, 170, 360, 170)], 400, 240),
    "A transformer: primary and secondary coils on a soft-iron core.", "A rectangular iron core with many turns on the left primary side and fewer turns on the right secondary side, with wires attached.")
Ns = 1100 * 12 / 220; assert abs(Ns - 60) < 1e-9
L.example("Example 1 — turns ratio", "A transformer reduces 220 V to 12 V. The primary has 1100 turns. How many turns does the secondary have?", ["Vs/Vp = Ns/Np, so Ns = Np × Vs ÷ Vp.", "Ns = 1100 × 12 ÷ 220 = 60 turns (a step-down transformer)."], "60 turns")
Ip = 12 * 2 / 220; assert abs(Ip - 0.109) < 0.001
L.example("Example 2 — primary current", "The 12 V secondary of an ideal transformer supplies a 2 A load. The primary is connected to 220 V. Find the primary current.", ["Output power = Vs Is = 12 × 2 = 24 W.", "Ideal: input power = 24 W, so Ip = 24 ÷ 220 = 0.11 A."], "0.11 A")
L.num("A step-up transformer has 100 primary turns and 500 secondary turns and a 20 V primary. Find the secondary voltage.", 100, "Vs = 20 × 500 ÷ 100 = 100 V.", unit="V")
L.mcq("A transformer will not work with", "steady direct current", ["alternating current", "a soft iron core", "a primary coil"], "Without a changing field there is no induced e.m.f.")
L.tf("A step-up transformer increases the power available.", False, "Power is not increased; the voltage rises and the current falls (ideal case: equal power).")
Vs2 = 240 * 50 / 1000
L.num("A transformer has 1000 primary turns and 50 secondary turns, with a primary voltage of 240 V. Find the secondary voltage in volts.", Vs2, "Vs = 240 × 50 ÷ 1000 = 12 V.", unit="V", tier="approfondissement", tolerance=0.01)
L.mcq("Why is electricity sent over long distances at very high voltage?", "A smaller current means less heat loss in the cables", ["The cables are thinner", "It is safer to touch", "It makes current constant"], "For the same power P = VI, a larger V gives a smaller I, so I²R loss is smaller.", tier="approfondissement", difficulty=2)
Is = 60 / 12; Ipp = 60 / 240; assert Is == 5 and abs(Ipp - 0.25) < 1e-9
L.problem("An ideal transformer has 1200 turns on the primary and 60 turns on the secondary. The primary is connected to 240 V a.c. and the secondary supplies a 60 W lamp.", [
    part_num("Find the secondary voltage.", 240 * 60 / 1200, "Vs = 240 × 60 ÷ 1200 = 12 V.", unit="V"),
    part_num("Find the secondary current.", Is, "I = P ÷ V = 60 ÷ 12 = 5 A.", unit="A"),
    part_num("Find the primary current.", Ipp, "Pin = Pout = 60 W, so Ip = 60 ÷ 240 = 0.25 A.", unit="A"),
    part_mcq("Is the transformer step-up or step-down?", "step-down", ["step-up", "neither", "it depends on the current"], "The secondary has fewer turns and a smaller voltage.")], tier="examen")
L.sc("A voltage is induced in a coil when the magnetic field through it", "changes", ["is steady", "is zero for ever", "is absent"], "Changing field, induced e.m.f.")
L.sc("A generator converts", "mechanical energy to electrical energy", ["electrical to mechanical", "light to heat", "heat to sound"], "Opposite of a motor.")
L.sc("A step-down transformer has", "fewer turns on the secondary", ["more turns on the secondary", "no core", "no primary"], "Vs < Vp.")
L.sc("A transformer needs", "alternating current", ["direct current only", "no current", "a battery only"], "To produce a changing field.")
L.sc("Power lines use high voltage to", "reduce power losses", ["increase current", "increase cable heating", "reduce the frequency"], "Smaller current, smaller I²R loss.")

# =====================================================================  12. ATOMIC PHYSICS
ch = p.chapter("atomic", "Atomic and nuclear physics", programRef="GCE O Level Physics — Atomic and nuclear physics: structure of the atom, radioactivity, half-life (to be checked against the official syllabus)")
L = ch.lesson("atom-radioactivity", "The atom and radioactivity", minutes=30,
    objectives=["Describe the nuclear model of the atom and define proton number and nucleon number.", "Explain isotopes.", "Describe alpha, beta and gamma radiation and their properties.", "Write simple decay equations (changes in A and Z) and describe safety precautions."],
    notes=["Charges and relative masses (proton +1, mass 1; neutron 0, mass 1; electron −1, mass about 1/1840) are standard values.", "Decay products given in the worked examples (U-238 → Th-234; C-14 → N-14) are standard textbook examples; verify with the periodic table in use.", "Uses of radiation are summarised; check the syllabus list."])
L.key("definition", "The atom", "An atom has a tiny, dense **nucleus** of **protons** (+1) and **neutrons** (0) with **electrons** (−1) orbiting it. The **proton number Z** = number of protons (= number of electrons in a neutral atom); the **nucleon number A** = protons + neutrons. Atoms of the same element with different numbers of neutrons are **isotopes** (e.g. carbon-12 and carbon-14 both have 6 protons).")
L.key("retenir", "Radioactivity", "Some unstable nuclei **decay** by themselves (spontaneously and randomly), emitting radiation:\n\n- **Alpha (α)**: a helium nucleus (2 protons + 2 neutrons); strongly ionising; stopped by paper or a few cm of air.\n- **Beta (β)**: a fast electron from the nucleus; stopped by a few mm of aluminium.\n- **Gamma (γ)**: electromagnetic radiation; weakly ionising, very penetrating; reduced by thick lead or concrete.")
L.key("formule", "Decay changes", "- **Alpha decay**: A decreases by 4 and Z by 2.\n- **Beta decay**: A is unchanged and Z increases by 1 (a neutron becomes a proton).\n- **Gamma emission**: no change in A or Z.\n\nRadiation is detected with a **Geiger-Müller tube** and counter. **Background radiation** (from rocks, space, buildings) must be subtracted from readings.")
L.key("pieges", "Safety and watch out", "Radiation can damage cells. Sources are handled with **tongs**, kept in **lead-lined containers**, at a distance, and for as short a time as possible. Do not confuse **radioactive** with **hot**: radioactive decay is not affected by temperature or chemical state.")
L.illustration(shapes([RECT(30, 80, 20, 100, fill="orange"), T(40, 200, "source", 14), LINE(50, 100, 150, 100, color="red", width=3, arrow="end"), LINE(50, 130, 230, 130, color="blue", width=3, arrow="end"), LINE(50, 160, 350, 160, color="green", width=3, arrow="end"),
                       LINE(150, 90, 150, 110, width=4), LINE(230, 120, 230, 140, width=4), LINE(300, 150, 300, 170, width=6), T(100, 80, "α: paper", 14), T(190, 112, "β: aluminium", 14), T(320, 145, "γ: thick lead", 14)], 400, 240),
    "Penetrating power of alpha, beta and gamma radiation.", "A radioactive source with three arrows: alpha stopped by a thin sheet of paper, beta stopped by aluminium and gamma stopped only by thick lead.")
A1, Z1 = 238, 92; A2, Z2 = A1 - 4, Z1 - 2; assert (A2, Z2) == (234, 90)
L.example("Example 1 — alpha decay", "Uranium-238 (Z = 92) emits an alpha particle. Find the nucleon number and the proton number of the new nucleus.", ["Alpha decay: A decreases by 4 and Z by 2.", "New A = 238 − 4 = 234 and new Z = 92 − 2 = 90, which is thorium-234 (Z = 90)."], "A = 234, Z = 90 (thorium-234)")
A3, Z3 = 14, 6 + 1
L.example("Example 2 — beta decay", "Carbon-14 (Z = 6) is a beta emitter. Find the new nucleus.", ["Beta decay: A stays 14 and Z increases by 1 to 7.", "Z = 7 is nitrogen, so the product is nitrogen-14."], "Nitrogen-14 (A = 14, Z = 7)")
L.num("An atom has 11 protons and 12 neutrons. Find its nucleon number.", 23, "A = 11 + 12 = 23.", unit=None)
L.mcq("Which radiation is the most penetrating?", "gamma", ["alpha", "beta", "all equal"], "Gamma needs thick lead or concrete.")
L.tf("Isotopes of an element have different numbers of protons.", False, "Isotopes have the same number of protons and different numbers of neutrons.")
L.num("A nucleus of A = 226, Z = 88 emits an alpha particle. Find the new nucleon number.", 222, "A decreases by 4: 226 − 4 = 222.", unit=None, tier="approfondissement")
L.mcq("A Geiger-Müller counter gives 30 counts per minute with no source present. This is", "background radiation", ["error", "alpha radiation", "the source decaying"], "Natural background radiation from the surroundings.", tier="approfondissement")
L.problem("A nucleus of polonium-210 has proton number 84 and emits an alpha particle.", [
    part_num("Find the nucleon number of the new nucleus.", 206, "210 − 4 = 206.", unit=None),
    part_num("Find its proton number.", 82, "84 − 2 = 82.", unit=None),
    part_mcq("A sheet of paper is placed between this source and a detector. The count rate", "falls to about the background", ["is unchanged", "doubles", "becomes zero for ever"], "Alpha particles are stopped by paper."),
    part_open("Give two safety precautions when handling radioactive sources.", "Use tongs, keep it in a lead container, keep a distance and handle for as short a time as possible.", "1 mark each; any two.")], tier="examen")
L.sc("A nucleus consists of", "protons and neutrons", ["electrons only", "protons and electrons", "neutrons and electrons"], "Electrons are outside the nucleus.")
L.sc("An alpha particle is", "a helium nucleus", ["an electron", "a photon", "a neutron"], "2 protons + 2 neutrons.")
L.sc("Which radiation is stopped by paper?", "alpha", ["gamma", "beta", "X-rays"], "Alpha has low penetration.")
L.sc("After beta decay the proton number", "increases by 1", ["decreases by 1", "stays the same", "decreases by 2"], "A neutron changes to a proton.")
L.sc("Isotopes have the same number of", "protons", ["neutrons", "nucleons", "electrons but different protons"], "They are the same element.")

L = ch.lesson("half-life", "Half-life and uses of radioisotopes", minutes=25, prerequisites=["gceol-physics-atom-radioactivity"],
    objectives=["Define half-life and read it from a decay curve.", "Calculate the remaining amount or activity after a number of half-lives.", "Describe uses of radioisotopes in medicine, industry and dating.", "Explain why half-life matters for safety and choice of source."],
    notes=["Carbon dating is mentioned only qualitatively; half-life of C-14 (about 5700 years) is not used in calculations.", "Medical and industrial uses are general summaries."])
L.key("definition", "Half-life", "The **half-life** of a radioactive isotope is the time for **half** of the nuclei in a sample to decay (equivalently, for the **count rate** to fall to half). Half-lives range from fractions of a second to billions of years. It is unaffected by temperature, pressure or chemical changes.")
L.key("methode", "Calculating", "After n half-lives the fraction left is $\\left(\\frac{1}{2}\\right)^n$. Method:\n1. Find the number of half-lives n = total time ÷ half-life.\n2. Halve the amount n times.\n3. For count rates, subtract the **background** first.")
L.key("retenir", "Uses", "- **Medicine**: gamma rays kill cancer cells and sterilise equipment; short half-life tracers show how organs work.\n- **Industry**: gauges that control the thickness of sheets; finding leaks in pipes.\n- **Dating**: carbon-14 estimates the age of ancient wood and bones.\n- **Smoke detectors**: use a small alpha source.")
L.key("pieges", "Watch out", "- Half-life does **not** mean the sample is all gone after two half-lives: half of a half remains (a quarter).\n- Use the same units for time and half-life.\n- Choose short half-life sources for medical tracers (so they disappear quickly from the body).")
L.illustration(plot(0, 12, 0, 90, curves=[("80*exp(-0.231*x)", "blue", None)], points=[(0, 80, "80 g", "red"), (3, 40, "40 g", "red"), (6, 20, "20 g", "red"), (9, 10, "10 g", "red")], grid=3, xlabel="time (days)", ylabel="mass left (g)", w=400, h=260),
    "Decay curve of a sample whose half-life is 3 days: the mass halves every 3 days.", "A falling exponential curve starting at 80 g, passing 40 g at 3 days, 20 g at 6 days and 10 g at 9 days.")
n1 = 12 / 3; left = 80 * 0.5 ** n1; assert n1 == 4 and left == 5
L.example("Example 1 — mass remaining", "A sample of 80 g of a radioisotope has a half-life of 3 days. How much remains after 12 days?", ["Number of half-lives = 12 ÷ 3 = 4.", "80 → 40 → 20 → 10 → 5, so 5 g remain (80 × (1/2)⁴ = 5 g)."], "5 g")
n2 = math.log2(400 / 50); hl = 9 / n2; assert abs(n2 - 3) < 1e-9 and abs(hl - 3) < 1e-9
L.example("Example 2 — half-life from data", "A source's count rate (background already subtracted) falls from 400 counts/min to 50 counts/min in 9 hours. Find its half-life.", ["400 → 200 → 100 → 50: the rate halves 3 times.", "3 half-lives = 9 hours, so half-life = 9 ÷ 3 = 3 hours."], "3 hours")
L.num("A sample has half-life 5 minutes and activity 160 counts/min. Find the activity after 15 minutes.", 20, "n = 3 half-lives: 160 → 80 → 40 → 20.", unit="counts/min")
L.mcq("After two half-lives, the fraction of the original radioactive nuclei remaining is", "one quarter", ["one half", "none", "one third"], "(1/2)² = 1/4.")
L.tf("Raising the temperature of a radioactive sample shortens its half-life.", False, "Radioactive decay is not affected by temperature.")
L.num("A source has an activity of 640 counts/min after subtracting the background. Its half-life is 2 hours. What is its activity after 8 hours?", 640 * 0.5 ** 4, "n = 4 half-lives: 640/16 = 40 counts/min.", unit="counts/min", tier="approfondissement")
L.mcq("A hospital wants a tracer to inject into a patient. Which half-life is most suitable?", "a few hours", ["a few million years", "a few seconds", "a few thousand years"], "Long enough for the scan, short enough to leave the body quickly.", tier="approfondissement", difficulty=2)
L.problem("A radioactive sample gave the following corrected count rates: 240 counts/min at time 0, 120 at 6 hours and 60 at 12 hours.", [
    part_num("Find the half-life in hours.", 6, "The rate halves every 6 hours.", unit="hours"),
    part_num("Predict the count rate after 24 hours.", 15, "24 hours = 4 half-lives: 240 → 120 → 60 → 30 → 15 counts/min.", unit="counts/min"),
    part_mcq("What should be done to a raw reading before using it in such a calculation?", "Subtract the background count rate", ["Add the background", "Multiply by the half-life", "Nothing"], "Corrected rate = measured rate − background rate."),
    part_open("Give one use of a radioisotope in medicine and one in industry.", "Medicine: killing cancer cells (or sterilising equipment, or a tracer). Industry: controlling the thickness of metal or paper sheets (or finding leaks).", "1 mark each use.")], tier="examen")
L.sc("Half-life is the time for", "half the nuclei to decay", ["all the nuclei to decay", "the source to cool", "the count to double"], "Definition.")
L.sc("After three half-lives, the fraction left is", "1/8", ["1/6", "1/3", "1/4"], "(1/2)³ = 1/8.")
L.sc("Radioactive decay is", "random and spontaneous", ["controlled by temperature", "caused by chemicals", "predictable for one nucleus"], "We can predict only the average behaviour.")
L.sc("Gamma rays can be used to", "kill cancer cells", ["make food hot", "measure speed", "tune a guitar"], "Radiotherapy.")
L.sc("A smoke detector contains a small", "alpha source", ["gamma source", "X-ray tube", "radio transmitter"], "Alpha is easily blocked by smoke particles.")

# =====================================================================  MOCK PAPER
def lesson(slug):
    for c in p.chapters:
        for l in c.lessons:
            if l.slug == slug: return l
    raise KeyError(slug)

acc = lesson("acceleration-equations")
mA = [
 lesson("speed-graphs").mcq("The gradient of a speed-time graph gives the", "acceleration", ["distance travelled", "average speed", "force"], "Gradient = change in speed ÷ time = acceleration.", mock=True, tier="examen"),
 lesson("newton-laws").mcq("A body of mass 4 kg has a weight of (g = 10 N/kg)", "40 N", ["4 N", "0.4 N", "14 N"], "W = mg = 4 × 10 = 40 N.", mock=True, tier="examen"),
 lesson("pressure").mcq("The pressure due to water at a depth of 3 m (ρ = 1000 kg/m³, g = 10 N/kg) is", "30 000 Pa", ["3000 Pa", "300 Pa", "33 Pa"], "P = hρg = 3 × 1000 × 10 = 30 000 Pa.", mock=True, tier="examen"),
 lesson("temperature-expansion").mcq("A temperature of 25 °C is equal to", "298 K", ["25 K", "−248 K", "273 K"], "T = 25 + 273 = 298 K.", mock=True, tier="examen"),
 lesson("wave-properties").mcq("A wave of frequency 20 Hz travels at 340 m/s. Its wavelength is", "17 m", ["6800 m", "0.059 m", "360 m"], "λ = v ÷ f = 340 ÷ 20 = 17 m.", mock=True, tier="examen"),
 lesson("reflection").mcq("A ray hits a plane mirror at 30° to the mirror surface. The angle of reflection is", "60°", ["30°", "120°", "90°"], "i = 90° − 30° = 60°, so r = 60°.", mock=True, tier="examen"),
 lesson("power-domestic").mcq("A 100 W lamp is switched on for 5 hours. The energy used is", "0.5 kWh", ["5 kWh", "50 kWh", "0.05 kWh"], "0.1 kW × 5 h = 0.5 kWh.", mock=True, tier="examen"),
 lesson("half-life").mcq("A sample of 80 g has a half-life of 2 days. The mass left after 4 days is", "20 g", ["40 g", "10 g", "0 g"], "Two half-lives: 80 → 40 → 20 g.", mock=True, tier="examen"),
]
F_net = 5000 - 1000; a_l = F_net / 2000; v_l = a_l * 10; s_l = 0.5 * a_l * 100; ek = 0.5 * 2000 * v_l ** 2
assert (a_l, v_l, s_l, ek) == (2, 20, 100, 400000) and F_net * s_l == ek
qB = acc.problem("A lorry of mass 2000 kg starts from rest on a level road. The engine force is 5000 N and the total resistance is 1000 N.", [
    part_num("Find the resultant force.", F_net, "5000 − 1000 = 4000 N.", unit="N", points=1),
    part_num("Find the acceleration.", a_l, "a = F ÷ m = 4000 ÷ 2000 = 2 m/s².", unit="m/s²", points=1),
    part_num("Find the speed after 10 s.", v_l, "v = at = 2 × 10 = 20 m/s.", unit="m/s", points=1),
    part_num("Find the distance travelled in 10 s.", s_l, "s = ½at² = ½ × 2 × 100 = 100 m.", unit="m", points=1),
    part_num("Find the kinetic energy of the lorry after 10 s.", ek, "Ek = ½mv² = ½ × 2000 × 400 = 400 000 J (equal to the work done by the resultant force, 4000 × 100).", unit="J", points=2)],
    mock=True, tier="examen")
Rpar = 1 / (1 / 10 + 1 / 15); Itot = 12 / Rpar; Pel = 12 * Itot; assert abs(Rpar - 6) < 1e-9 and abs(Itot - 2) < 1e-9 and abs(Pel - 24) < 1e-9
qC = lesson("series-parallel").problem("Resistors of 10 Ω and 15 Ω are connected in parallel across a 12 V supply.", [
    part_num("Find the total resistance.", Rpar, "1/R = 1/10 + 1/15 = 3/30 + 2/30 = 5/30, so R = 6 Ω.", unit="Ω", points=1),
    part_num("Find the total current.", Itot, "I = V ÷ R = 12 ÷ 6 = 2 A.", unit="A", points=1),
    part_num("Find the total power supplied.", Pel, "P = VI = 12 × 2 = 24 W.", unit="W", points=1)], mock=True, tier="examen")
Qh = 2000 * 100; dT = Qh / (1 * 4200); eff_h = 1 * 4200 * 40 / Qh * 100; assert Qh == 200000 and abs(eff_h - 84) < 1e-9
qD = lesson("heat-capacity").problem("A 2 kW electric heater is placed in 1 kg of water for 100 s (c = 4200 J/kg °C). Assume first that all the energy goes into the water.", [
    part_num("Find the energy supplied by the heater.", Qh, "E = Pt = 2000 × 100 = 200 000 J.", unit="J", points=1),
    part_num("Find the rise in temperature (to 1 decimal place).", round(dT, 1), "Δθ = Q ÷ (mc) = 200 000 ÷ 4200 = 47.6 °C.", unit="°C", points=1, tolerance=0.1),
    part_num("In a real experiment the water rises by only 40 °C. Find the efficiency in %.", eff_h, "Useful energy = 1 × 4200 × 40 = 168 000 J; efficiency = 168 000 ÷ 200 000 = 84%.", unit="%", points=1, tolerance=0.5)], mock=True, tier="examen")
p.mock("1", "GCE O Level mock — Physics", 90,
       "Answer all questions. The paper is marked out of 20: Section A has 8 multiple-choice questions (1 mark each) and Sections B to D are structured questions with the marks shown for each part. Use g = 10 N/kg and c(water) = 4200 J/kg °C. This is an original practice paper: its format is to be checked against the official texts of the Cameroon GCE Board.",
       [("Section A — Multiple choice (8 marks)", mA), ("Section B — Mechanics (6 marks)", [qB]), ("Section C — Electricity (3 marks)", [qC]), ("Section D — Thermal physics (3 marks)", [qD])])
p.write()
