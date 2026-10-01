import sys, math; sys.path.insert(0, "/home/user/CastBridge/tools/learn-authoring/anglophone")
from fs_kit import *

p = Pack("form4-physics", "Physics — Form 4", level="Form 4", subject="physics", cursus="secondary",
         description="The second course in O Level Physics for Form 4: temperature and heat, specific and latent heat, reflection and refraction of light, waves and sound, static electricity, current electricity and domestic electricity, and electromagnetism. Worked examples and exercises with answers.",
         programRef="Cameroon Anglophone secondary school Physics, Form 4 (second year of the GCE O Level Physics course) — to be checked against the official syllabus")

# =========================================================== 1 Thermal physics
ch = p.chapter("heat", "Thermal physics", "Form 4 Physics — Temperature, expansion, specific heat, latent heat (to be checked)")
def thermo_fig():
    d = Draw()
    d.sh.append(RECT(190, 30, 20, 130, fill="white", width=2)); d.sh.append(CIRCLE(200, 175, 18, fill="red", width=2))
    d.sh.append(RECT(193, 100, 14, 62, fill="red", stroke="none", width=0))
    d.line(210, 45, 225, 45, width=1.5); d.line(210, 155, 225, 155, width=1.5); d.line(210, 100, 225, 100, width=1.5)
    d.text(232, 49, "100 °C (steam point)", size=11, anchor="start"); d.text(232, 159, "0 °C (ice point)", size=11, anchor="start")
    d.text(232, 104, "50 °C", size=11, anchor="start")
    d.sh += lab(190, 130, 130, 120, "liquid thread", anchor="end", size=11)
    d.sh += lab(200, 190, 130, 196, "bulb", anchor="end", size=11)
    return d.fig(440, 210)
th50 = (12 - 2) / (22 - 2) * 100; assert th50 == 50
assert 25 + 273 == 298 and 100 + 273 == 373
build(ch, "temperature-expansion", "Temperature, thermometers and expansion", 25,
      ["Describe how a liquid-in-glass thermometer works and define the fixed points.", "Convert between degrees Celsius and kelvin.", "Describe expansion of solids, liquids and gases and the bimetallic strip."],
      [("key", "definition", "Temperature and thermometers",
        "**Temperature** measures how hot an object is. A **liquid-in-glass thermometer** uses the regular **expansion** of a liquid (mercury or coloured alcohol) in a narrow tube. Its scale is set using two **fixed points**: the **lower fixed point 0 °C** (pure melting ice) and the **upper fixed point 100 °C** (steam from pure boiling water at standard pressure). The distance between them is divided into 100 equal parts."),
       ("fig", thermo_fig(), "A liquid-in-glass thermometer and its fixed points.", "A thermometer with a bulb and thread, marked 0 °C at the ice point near the bottom, 50 °C and 100 °C at the steam point near the top."),
       ("key", "formule", "The kelvin scale",
        "The **kelvin (K)** is the SI unit of temperature: **T (K) = θ (°C) + 273**. The lowest possible temperature, **absolute zero**, is 0 K = −273 °C. A change of 1 °C equals a change of 1 K. A thermometer is more **sensitive** with a narrower tube or a larger bulb, and has greater **range** when the scale is longer."),
       ("key", "retenir", "Expansion",
        "Solids, liquids and gases **expand** when heated: gases most, solids least. Uses and effects: gaps in railways and bridges, rollers under bridges, tight metal tyres put on wheels hot. A **bimetallic strip** has two metals bonded together; the metal that expands more is on the **outside of the bend** when heated. It is used in thermostats and fire alarms. **Water** is unusual: it contracts from 0 °C to 4 °C and is **densest at 4 °C**, so ice floats and lakes freeze from the top."),
       ("key", "pieges", "Common mistakes",
        "- Add 273, not 100, to change °C to K (use 273 in this course).\n- The expansion of a liquid in a thermometer is due to the liquid, but the glass also expands a little.\n- Ice floats because ice is less dense than liquid water, not because it is colder."),
       ("example", "Unmarked thermometer", "A thermometer's liquid is 2 cm long at 0 °C and 22 cm long at 100 °C. At a certain temperature the length is 12 cm. Find the temperature.", ["Range = 22 − 2 = 20 cm for 100 °C. Rise above 0 °C = 12 − 2 = 10 cm.", "Temperature = (10 ÷ 20) × 100 = 50 °C."], "50 °C"),
       ("example", "Kelvin", "Convert 25 °C and 373 K.", ["25 + 273 = 298 K.", "373 − 273 = 100 °C."], "25 °C = 298 K; 373 K = 100 °C")],
      [("num", "Convert 37 °C to kelvin.", 310, "37 + 273 = 310 K.", 0, "K"),
       ("num", "Convert 300 K to °C.", 27, "300 − 273 = 27 °C.", 0, "°C"),
       ("mcq", "The upper fixed point of the Celsius scale is the temperature of:", "steam from pure boiling water", ["melting ice", "the human body", "a Bunsen flame"], "100 °C at standard pressure."),
       ("mcq", "Railway lines are laid with small gaps because:", "the metal expands in hot weather", ["metal shrinks in rain", "it saves money", "the gaps drain water"], "Expansion would otherwise buckle the track."),
       ("num", "A liquid thread is 4 cm long at 0 °C and 24 cm long at 100 °C. Find the temperature when it is 14 cm long.", 50, "(14 − 4) ÷ 20 × 100 = 50 °C.", 0, "°C"),
       ("prob", "A bimetallic strip is made of brass and iron joined together. Brass expands more than iron for the same rise in temperature.", [("mcq", "When the strip is heated it bends so that the brass is on the:", "outside of the curve", ["inside of the curve", "straight edge", "bottom always"], "The metal that expands more is on the outside."), ("mcq", "A common use of the strip is in a:", "thermostat", ["barometer", "voltmeter", "microscope"], "It breaks a circuit when too hot."), ("open", "Describe how it can act as a fire alarm.", "When the temperature rises the strip bends until it touches a contact, completing a circuit that sounds the alarm.", ["1 mark: bends when heated", "1 mark: completes the alarm circuit"], 2)])],
      [("0 K is also written as:", "−273 °C", ["0 °C", "273 °C", "−100 °C"], "Absolute zero."),
       ("The lower fixed point of the Celsius scale is:", "0 °C, pure melting ice", ["100 °C", "37 °C", "−273 °C"], "Ice point."),
       ("Water has its greatest density at:", "4 °C", ["0 °C", "100 °C", "37 °C"], "Anomalous expansion of water."),
       ("The metal that expands more in a bimetallic strip ends up:", "on the outside of the bend", ["on the inside", "in the middle", "removed"], "Longer outer edge."),
       ("Which usually expands the most for the same temperature rise?", "a gas", ["a solid", "a liquid", "a vacuum"], "Gases expand most.")],
      ["Use of 273 (rather than 273.15) is a common school convention; check the local convention.", "Thermometer 'sensitivity' and 'range' may be beyond the syllabus."])

cvals = bars([("water", 4200, "blue"), ("aluminium", 900, "grey"), ("iron", 450, "orange"), ("copper", 390, "brown")], w=420, h=250)
Q1 = 2 * 4200 * (70 - 20); assert Q1 == 420000
Qk = 1 * 4200 * 80; t_k = Qk / 2000; assert Qk == 336000 and t_k == 168
th_mix = (0.2 * 4200 * 80 + 0.3 * 4200 * 20) / (0.5 * 4200); assert abs(th_mix - 44) < 1e-9
build(ch, "specific-heat", "Heat capacity and specific heat capacity", 30,
      ["Define heat capacity and specific heat capacity.", "Use Q = m c Δθ and E = P t in calculations.", "Solve simple mixing problems."],
      [("key", "formule", "Specific heat capacity",
        "The **specific heat capacity c** of a substance is the energy needed to raise the temperature of **1 kg** by **1 °C** (or 1 K). Unit: **J/(kg °C)**. The energy gained or lost is **Q = m × c × Δθ** (Δθ = change in temperature). The **heat capacity** of a whole object is **C = m c** (J/°C). Water has a large specific heat capacity, **4200 J/(kg °C)**, so it heats and cools slowly."),
       ("fig", cvals, "Approximate specific heat capacities in J/(kg °C).", "A bar chart: water 4200, aluminium 900, iron 450 and copper 390 joules per kilogram per degree Celsius."),
       ("key", "methode", "Electrical heating",
        "An electric heater of power P running for time t supplies energy **E = P t**. If all of it heats the water: **P t = m c Δθ**. In real situations some energy is lost to the surroundings, so the measured temperature rise is smaller. **Mixing**: heat lost by the hot liquid = heat gained by the cold liquid (if no heat is lost)."),
       ("key", "retenir", "Uses",
        "Water's high specific heat capacity makes it a good coolant (car radiators), a good heat store (hot-water bottles) and explains why coastal areas have milder temperatures than inland areas. Metals such as copper heat up quickly because of their low specific heat capacity, so they suit pots and kettles."),
       ("key", "pieges", "Common mistakes",
        "- Use the **change** in temperature Δθ, not the final temperature.\n- Mass must be in kg: 500 g = 0.5 kg.\n- When mixing, the final temperature lies **between** the two starting temperatures."),
       ("example", "Heating water", "Find the energy needed to heat 2 kg of water from 20 °C to 70 °C (c = 4200 J/(kg °C)).", ["Δθ = 70 − 20 = 50 °C.", "Q = m c Δθ = 2 × 4200 × 50 = 420 000 J."], "420 000 J (420 kJ)"),
       ("example", "A 2 kW kettle", "How long does a 2000 W kettle take to heat 1 kg of water from 20 °C to 100 °C, assuming no energy is lost?", ["Q = 1 × 4200 × 80 = 336 000 J.", "t = Q ÷ P = 336 000 ÷ 2000 = 168 s."], "168 s (2 min 48 s)")],
      [("num", "Find the energy needed to raise the temperature of 0.5 kg of water by 40 °C. (c = 4200 J/(kg °C))", 84000, "0.5 × 4200 × 40 = 84 000 J.", 0, "J"),
       ("num", "A 0.2 kg iron bar (c = 450 J/(kg °C)) is heated from 20 °C to 120 °C. Find the energy gained.", 9000, "0.2 × 450 × 100 = 9000 J.", 0, "J"),
       ("mcq", "A substance has a high specific heat capacity. It will:", "heat up slowly", ["heat up quickly", "never heat", "always melt"], "It needs much energy per degree."),
       ("num", "A 1000 W heater runs for 5 minutes and all the energy heats water. Find the energy in J.", 300000, "1000 × 300 = 300 000 J.", 0, "J"),
       ("open", "Explain why a coastal town like Limbe has smaller temperature changes than an inland town.", "The sea has a large mass of water with a high specific heat capacity, so it heats and cools slowly and keeps the air near the coast at a steadier temperature.", ["1 mark: water has high specific heat capacity", "1 mark: slow temperature change of sea"]),
       ("prob", "0.2 kg of hot water at 80 °C is added to 0.3 kg of cold water at 20 °C in an insulated container. (c = 4200 J/(kg °C))", [("num", "Let the final temperature be θ. Energy lost by the hot water equals energy gained by the cold. Find θ in °C.", 44, "0.2 × (80 − θ) = 0.3 × (θ − 20); 16 − 0.2θ = 0.3θ − 6; 22 = 0.5θ; θ = 44 °C.", 0.1, "°C"), ("num", "Energy gained by the cold water (J).", 30240, "0.3 × 4200 × 24 = 30 240 J.", 1, "J"), ("mcq", "The final temperature must be:", "between 20 °C and 80 °C", ["above 80 °C", "below 20 °C", "exactly 50 °C"], "Hot loses and cold gains.")])],
      [("Specific heat capacity is measured in:", "J/(kg °C)", ["J/kg", "W", "N"], "Energy per kg per degree."),
       ("The formula for the energy needed to heat a mass is:", "Q = m c Δθ", ["Q = m + c", "Q = m ÷ c", "Q = c ÷ Δθ"], "Standard equation."),
       ("Which has the highest specific heat capacity?", "water", ["iron", "copper", "aluminium"], "4200 J/(kg °C)."),
       ("A 500 W heater supplies how much energy in 10 s?", "5000 J", ["50 J", "500 J", "50 000 J"], "E = Pt."),
       ("A copper pot heats quickly because copper has:", "a low specific heat capacity", ["a high specific heat capacity", "no mass", "a high density only"], "Little energy needed per degree.")],
      ["c(water) = 4200 J/(kg °C) used (4180 exact); the specific heat capacities of metals are rounded textbook values."])

heatcurve = plot(0, 14, -20, 120, segments=[(0, -10, 1, 0, "blue", False), (1, 0, 3, 0, "red", False), (3, 0, 7, 100, "blue", False), (7, 100, 12, 100, "red", False), (12, 100, 14, 120, "blue", False)],
                 points=[(2, 0, "melting", "red"), (9.5, 100, "boiling", "red")], grid=2, xlabel="heat supplied", ylabel="θ (°C)", w=440, h=270)
Lf, Lv = 3.4e5, 2.3e6
Qm = 0.5 * Lf; assert Qm == 170000
Qv = 0.2 * Lv; assert abs(Qv - 460000) < 1e-6
Qt = 0.1 * Lf + 0.1 * 4200 * 100 + 0.1 * Lv; assert abs(Qt - 306000) < 1e-6
build(ch, "latent-heat", "Change of state and latent heat", 30,
      ["Explain why temperature stays constant during a change of state.", "Define specific latent heat and use Q = m L.", "Distinguish evaporation from boiling."],
      [("key", "definition", "Latent heat",
        "During **melting** and **boiling** the temperature stays constant although heat is supplied. The energy is used to **break the bonds between particles**, not to raise their speed. This hidden energy is the **latent heat**. **Specific latent heat of fusion (L_f)** is the energy to melt 1 kg of solid at its melting point. **Specific latent heat of vaporisation (L_v)** is the energy to change 1 kg of liquid to gas at its boiling point. Unit: **J/kg**."),
       ("fig", heatcurve, "A heating curve: temperature against heat supplied for ice becoming steam (schematic).", "A graph: temperature rises from below 0 °C to 0 °C, stays flat while melting, rises to 100 °C, stays flat while boiling, then rises again as steam."),
       ("key", "formule", "Calculations",
        "**Q = m × L**. In this course: **L_f (ice) = 3.4 × 10⁵ J/kg** and **L_v (water) = 2.3 × 10⁶ J/kg**; c(water) = 4200 J/(kg °C). To go from ice at 0 °C to steam at 100 °C, add the stages: melt (m L_f), warm the water (m c Δθ), boil (m L_v)."),
       ("key", "retenir", "Evaporation and boiling",
        "**Boiling** happens throughout the liquid at one fixed temperature. **Evaporation** happens only at the **surface**, at any temperature, and the faster particles escape, so the liquid **cools**. Evaporation is faster when the liquid is warmer, the surface area larger, the air drier and moving. This is why sweat cools the body and clay water pots keep water cool."),
       ("key", "pieges", "Common mistakes",
        "- The temperature does not change while a pure substance melts or boils: do not use m c Δθ for the change of state.\n- Steam at 100 °C contains much more energy than water at 100 °C.\n- L is per kilogram: use mass in kg."),
       ("example", "Melting ice", "How much energy melts 0.5 kg of ice at 0 °C? (L_f = 3.4 × 10⁵ J/kg)", ["Q = m L_f.", "0.5 × 3.4 × 10⁵ = 1.7 × 10⁵ J = 170 000 J."], "170 000 J"),
       ("example", "Ice to steam", "Find the energy to change 0.1 kg of ice at 0 °C into steam at 100 °C.", ["Melt: 0.1 × 3.4 × 10⁵ = 34 000 J. Warm: 0.1 × 4200 × 100 = 42 000 J. Boil: 0.1 × 2.3 × 10⁶ = 230 000 J.", "Total = 34 000 + 42 000 + 230 000 = 306 000 J."], "306 000 J")],
      [("num", "How much energy is needed to boil away 0.2 kg of water already at 100 °C? (L_v = 2.3 × 10⁶ J/kg)", 460000, "0.2 × 2.3 × 10⁶ = 460 000 J.", 0, "J"),
       ("mcq", "While ice is melting at 0 °C its temperature:", "stays constant", ["rises steadily", "falls", "becomes 100 °C"], "Energy goes into breaking bonds."),
       ("num", "Find the energy needed to melt 2 kg of ice at 0 °C. (L_f = 3.4 × 10⁵ J/kg)", 680000, "2 × 3.4 × 10⁵ = 680 000 J.", 0, "J"),
       ("mcq", "Sweating cools the body because evaporation:", "removes the most energetic particles from the skin", ["adds heat", "makes the skin dry", "raises the pressure"], "The remaining liquid is cooler."),
       ("open", "Explain the difference between boiling and evaporation.", "Boiling occurs throughout the liquid at the boiling point with bubbles of vapour; evaporation occurs only at the surface at any temperature and causes cooling.", ["1 mark: boiling throughout at fixed temperature", "1 mark: evaporation at the surface at any temperature"]),
       ("prob", "A heater of power 1000 W is used to melt 0.5 kg of ice at 0 °C. (L_f = 3.4 × 10⁵ J/kg)", [("num", "Energy needed (J).", 170000, "0.5 × 3.4 × 10⁵ = 170 000 J.", 0, "J"), ("num", "Time taken if all the heater's energy melts the ice (s).", 170, "170 000 ÷ 1000 = 170 s.", 0, "s"), ("mcq", "During this time the temperature of the ice-water mixture:", "stays at 0 °C", ["rises to 20 °C", "falls to −5 °C", "rises to 100 °C"], "Change of state at constant temperature.")])],
      [("Latent heat is the energy used to:", "change the state without changing temperature", ["raise the temperature of a solid", "colour a liquid", "compress a gas"], "Breaks bonds."),
       ("The unit of specific latent heat is:", "J/kg", ["J", "J/°C", "W"], "Energy per kilogram."),
       ("Which change takes the most energy for 1 kg of water?", "boiling at 100 °C", ["melting at 0 °C", "warming from 0 to 1 °C", "freezing"], "L_v is the largest."),
       ("The formula Q = mL is used for:", "a change of state", ["a change in temperature", "a change in volume", "a change in colour"], "No temperature change."),
       ("Evaporation happens:", "at any temperature, at the surface", ["only at 100 °C", "only in solids", "only inside a liquid"], "Surface effect.")],
      ["L_f = 3.4 × 10⁵ J/kg (some texts use 3.3 × 10⁵) and L_v = 2.3 × 10⁶ J/kg (exact about 2.26 × 10⁶): check the values in the class text."])

# =========================================================== 2 Light
ch = p.chapter("light", "Light", "Form 4 Physics — Reflection, curved mirrors, refraction and lenses (to be checked)")
def concave_fig():
    d = Draw(); C = (300, 110); R = 80
    pts = [(C[0] + R * math.cos(math.radians(t)), C[1] + R * math.sin(math.radians(t))) for t in range(-45, 46, 5)]
    d.sh.append(polyline(pts, color="ink", width=4))
    d.line(30, 110, 440, 110, width=1.2, color="grey")
    d.line(260, 110, 260, 70, arrow="end", color="blue", width=3)
    tip = (320, 129)
    d.line(320, 110, 320, 129, arrow="end", color="green", width=3)
    xh1 = C[0] + math.sqrt(R * R - 40 * 40)
    d.line(260, 70, xh1, 70, color="red", width=1.5); d.line(xh1, 70, tip[0], tip[1], color="red", width=1.5, arrow="end")
    t = (120 + math.sqrt(120 ** 2 + 4 * 1.25 * 3200)) / 2.5
    xh2, yh2 = 260 + t, 70 + 0.5 * t
    d.line(260, 70, xh2, yh2, color="orange", width=1.5); d.line(xh2, yh2, tip[0], tip[1], color="orange", width=1.5, arrow="end")
    d.text(300, 150, "C", size=12); d.text(340, 150, "F", size=12)
    d.text(260, 58, "object", size=11); d.text(322, 172, "image", size=11); d.text(120, 100, "principal axis", size=11)
    return d.fig(450, 190)
f_, u_ = 10, 30
v_ = 1 / (1 / f_ - 1 / u_); assert abs(v_ - 15) < 1e-9 and abs(v_ / u_ - 0.5) < 1e-9
build(ch, "reflection-mirrors", "Reflection and curved mirrors", 30,
      ["Use the laws of reflection and describe the image in a plane mirror.", "Describe the action of concave and convex mirrors on parallel rays.", "Use the mirror formula 1/f = 1/u + 1/v and magnification."],
      [("key", "propriete", "Laws of reflection",
        "1. The angle of incidence **i** equals the angle of reflection **r** (both measured from the normal).\n2. The incident ray, reflected ray and normal lie in the same plane.\n\nA **plane mirror** forms a **virtual**, upright image, the same size as the object and as far behind the mirror as the object is in front. A **virtual** image cannot be shown on a screen; a **real** image can. If a plane mirror turns by θ, the reflected ray turns by 2θ."),
       ("key", "retenir", "Curved mirrors",
        "A **concave** (converging) mirror brings parallel rays to the **principal focus F**. The distance from the mirror to F is the **focal length f**; the **centre of curvature C** is at 2f. A **convex** (diverging) mirror spreads parallel rays out as if from a virtual focus behind it. Rays: parallel to the axis → through F; through F → parallel to the axis; through C → back along itself."),
       ("fig", concave_fig(), "Image in a concave mirror when the object is beyond C: real, inverted and smaller.", "A concave mirror on the right, an object arrow beyond C on the left and a smaller inverted image arrow between C and F, with two reflected rays meeting at the image tip."),
       ("key", "formule", "Mirror formula and uses",
        "**1/f = 1/u + 1/v** (real-is-positive: u object distance, v image distance, f focal length of a concave mirror positive). **Magnification m = v / u = image height ÷ object height**. Uses: **concave**: torch and headlight reflectors, shaving mirrors, dentists' mirrors (magnified when the object is within f); **convex**: driving mirrors and shop security mirrors because they give a wide field of view."),
       ("key", "pieges", "Common mistakes",
        "- Measure angles from the **normal**, not from the mirror.\n- The image in a plane mirror is as far **behind** the mirror as the object is in front, so the distance between object and image is twice the distance to the mirror.\n- A real image is inverted; a virtual image formed by a mirror is upright."),
       ("example", "Image in a concave mirror", "An object is 30 cm from a concave mirror of focal length 10 cm. Find the image distance and the magnification.", ["1/v = 1/f − 1/u = 1/10 − 1/30 = 2/30, so v = 15 cm.", "m = v ÷ u = 15 ÷ 30 = 0.5 (half the size, real, inverted)."], "v = 15 cm; m = 0.5"),
       ("example", "Plane mirror distance", "A boy stands 2.5 m from a plane mirror and walks 1 m towards it. How far is he now from his image?", ["He is now 2.5 − 1 = 1.5 m from the mirror.", "The image is 1.5 m behind it: distance = 3.0 m."], "3.0 m")],
      [("num", "A ray makes an angle of 20° with a plane mirror's surface. What is the angle of reflection (from the normal)?", 70, "Angle of incidence = 90° − 20° = 70°, so r = 70°.", 0, "°"),
       ("mcq", "Which mirror gives a wide field of view and is used as a car's wing mirror?", "convex", ["concave", "plane only", "a cracked mirror"], "A convex mirror makes a smaller image of a wider scene."),
       ("num", "A concave mirror has f = 12 cm. An object is placed 36 cm from it. Find the image distance in cm.", 18, "1/v = 1/12 − 1/36 = 2/36, so v = 18 cm.", 0.01, "cm"),
       ("mcq", "The image in a plane mirror is:", "virtual and the same size as the object", ["real and smaller", "real and inverted", "virtual and enlarged"], "Standard properties."),
       ("num", "A plane mirror turns through 15°. Through what angle does the reflected ray turn? (°)", 30, "The reflected ray turns through twice the angle: 30°.", 0, "°"),
       ("prob", "An object 4.0 cm tall is placed 30 cm in front of a concave mirror of focal length 10 cm.", [("num", "Image distance v (cm).", 15, "1/v = 1/10 − 1/30 so v = 15 cm.", 0.01, "cm"), ("num", "Magnification.", 0.5, "m = v ÷ u = 15 ÷ 30 = 0.5.", 0.01), ("num", "Image height (cm).", 2.0, "2.0 cm = 0.5 × 4.0 cm.", 0.01, "cm")])],
      [("The angle of incidence is measured between the incident ray and the:", "normal", ["mirror surface", "image", "focus"], "Normal is perpendicular to the mirror."),
       ("A concave mirror brings parallel rays to the:", "principal focus", ["centre of the Earth", "normal", "object"], "It converges them."),
       ("A virtual image:", "cannot be formed on a screen", ["is always inverted", "is always smaller", "is always real"], "Rays only appear to come from it."),
       ("The magnification of a mirror is:", "v ÷ u", ["u × v", "u − v", "f ÷ v"], "Image distance over object distance."),
       ("A shaving mirror is usually:", "concave", ["convex", "frosted", "curved outward"], "The face is within the focal length, giving an enlarged upright image.")],
      ["Real-is-positive sign convention used; check whether the syllabus uses the Cartesian or the real-is-positive convention.", "The ray diagram is schematic (paraxial rays)."])

def refr_fig():
    d = Draw(); ti = math.radians(45); n = 1.5; tr = math.asin(math.sin(ti) / n)
    d.sh.append(RECT(60, 110, 280, 90, fill="lightblue", width=2))
    d.line(200, 30, 200, 195, color="grey", dash=True, width=1.5)
    L = 100
    d.line(200 - L * math.sin(ti), 110 - L * math.cos(ti), 200, 110, color="red", width=2.5, arrow="end")
    d.line(200, 110, 200 + L * math.sin(tr), 110 + L * math.cos(tr), color="red", width=2.5, arrow="end")
    d.text(205, 36, "normal", size=11, anchor="start"); d.text(50, 60, "air", size=12, anchor="start"); d.text(70, 130, "glass (n = 1.5)", size=11, anchor="start")
    d.text(150, 78, "i = 45°", size=11, anchor="end"); d.text(212, 160, "r = 28°", size=11, anchor="start")
    return d.fig(400, 210)
def lens_fig():
    d = Draw()
    d.line(20, 110, 460, 110, width=1.2, color="grey")
    d.line(240, 30, 240, 190, width=3, arrow="both")
    d.line(60, 110, 60, 70, arrow="end", color="blue", width=3)
    d.line(330, 110, 330, 130, arrow="end", color="green", width=3)
    d.line(60, 70, 240, 70, color="red", width=1.5); d.line(240, 70, 330, 130, color="red", width=1.5, arrow="end")
    d.line(60, 70, 240, 110, color="orange", width=1.5); d.line(240, 110, 330, 130, color="orange", width=1.5, arrow="end")
    for x, t in ((120, "2F"), (180, "F"), (300, "F"), (360, "2F")): d.text(x, 146, t, size=11)
    d.text(60, 58, "object", size=11); d.text(330, 166, "image", size=11)
    return d.fig(470, 190)
si, n_g = math.radians(45), 1.5
rr = math.degrees(math.asin(math.sin(si) / n_g)); assert abs(rr - 28.13) < 0.01
cg = math.degrees(math.asin(1 / 1.5)); cw = math.degrees(math.asin(1 / 1.33)); assert abs(cg - 41.81) < 0.01 and abs(cw - 48.75) < 0.05
vl = 1 / (1 / 15 - 1 / 45); assert abs(vl - 22.5) < 1e-9
rw = math.degrees(math.asin(math.sin(math.radians(30)) / 1.33)); assert abs(rw - 22.08) < 0.05
build(ch, "refraction-lenses", "Refraction and lenses", 30,
      ["Describe refraction and use the refractive index n = sin i / sin r.", "Explain total internal reflection and the critical angle.", "Describe image formation by a converging lens and use 1/f = 1/u + 1/v."],
      [("key", "definition", "Refraction",
        "**Refraction** is the bending of light when it passes from one transparent material to another, because its **speed changes**. Light entering a **denser** medium (air to glass) bends **towards the normal**; leaving it bends **away**. The **refractive index** **n = sin i ÷ sin r** (i in air, r in the material); n = 1.33 for water and about 1.5 for glass. Refraction makes a pool look shallower than it is."),
       ("fig", refr_fig(), "A ray passing from air into glass bends towards the normal.", "A ray hitting the top of a glass block at 45 degrees to the normal and bending to about 28 degrees inside the glass."),
       ("key", "propriete", "Total internal reflection",
        "When light travels from a **denser to a less dense** medium and the angle of incidence is **larger than the critical angle c**, **all** the light is reflected inside: **total internal reflection**. **sin c = 1 / n**. For glass c ≈ 42°; for water c ≈ 49°. Uses: prisms in binoculars, bicycle reflectors, **optical fibres** for telephone and internet signals and for medical viewing."),
       ("fig", lens_fig(), "Image formed by a converging lens when the object is beyond 2F: real, inverted and smaller.", "A converging lens with an object arrow far to the left and a smaller inverted image arrow on the right, with two rays crossing at the image tip."),
       ("key", "formule", "Converging lens",
        "A **converging (convex) lens** brings parallel rays to the **principal focus F**. **1/f = 1/u + 1/v** (real-is-positive), **m = v / u**. Object beyond 2F: small real inverted image (camera). Object between F and 2F: enlarged real image (projector). Object **inside F**: enlarged, upright, **virtual** image: a **magnifying glass**."),
       ("key", "pieges", "Common mistakes",
        "- n = sin i ÷ sin r uses the sines of angles, not the angles themselves.\n- Total internal reflection needs **both** a denser-to-less-dense path **and** an angle above c.\n- A magnifying glass uses the object **inside** the focal length."),
       ("example", "Refraction into glass", "A ray hits glass (n = 1.5) at 45° to the normal. Find the angle of refraction.", ["sin r = sin 45° ÷ 1.5 = 0.7071 ÷ 1.5 = 0.4714.", "r = 28.1°."], "r ≈ 28°"),
       ("example", "Lens image", "An object is 45 cm from a converging lens of focal length 15 cm. Find v and the magnification.", ["1/v = 1/15 − 1/45 = 2/45, so v = 22.5 cm.", "m = 22.5 ÷ 45 = 0.5."], "v = 22.5 cm; m = 0.5")],
      [("mcq", "Light entering water from air bends:", "towards the normal", ["away from the normal", "not at all", "back into the air"], "It slows down in the denser medium."),
       ("num", "Calculate the critical angle for glass of n = 1.5 (to the nearest degree).", 42, "sin c = 1 ÷ 1.5 = 0.667, so c = 41.8° ≈ 42°.", 0.5, "°"),
       ("num", "A ray in air hits water (n = 1.33) at 30° to the normal. Find the angle of refraction in degrees (1 d.p.).", 22.1, "sin r = 0.5 ÷ 1.33 = 0.376, r = 22.1°.", 0.2, "°"),
       ("mcq", "An optical fibre carries light along it by:", "total internal reflection", ["absorption", "diffraction", "dispersion only"], "The light is repeatedly reflected at the walls."),
       ("num", "A convex lens of focal length 20 cm forms a real image 60 cm away. Find the object distance in cm.", 30, "1/u = 1/20 − 1/60 = 2/60, u = 30 cm.", 0.01, "cm"),
       ("prob", "A slide projector lens has f = 10 cm and the slide is 12 cm from it.", [("num", "Image distance v (cm).", 60, "1/v = 1/10 − 1/12 = 2/120, so v = 60 cm.", 0.01, "cm"), ("num", "Magnification.", 5, "m = 60 ÷ 12 = 5.", 0.01), ("mcq", "The image on the screen is:", "real, inverted and enlarged", ["virtual and upright", "real and smaller", "virtual and inverted"], "Object between F and 2F.")])],
      [("The refractive index is calculated as:", "sin i ÷ sin r", ["i ÷ r", "sin i × sin r", "i − r"], "Snell's law."),
       ("Total internal reflection can happen when light goes from:", "glass to air at a large angle", ["air to glass", "air to water", "vacuum to air"], "Dense to less dense."),
       ("A converging lens used as a magnifying glass has the object:", "inside the focal length", ["beyond 2F", "at 2F", "at infinity"], "It gives a virtual enlarged image."),
       ("A pool looks shallower than it is because of:", "refraction", ["reflection", "dispersion", "interference"], "Rays bend away from the normal on leaving water."),
       ("Optical fibres are used for:", "carrying telephone and internet signals", ["measuring mass", "boiling water", "cooling food"], "Light signals travel by total internal reflection.")],
      ["n for water 1.33 and glass 1.5 are standard school values; the critical angle for water is 48.8°, quoted as about 49°.", "Ray diagrams are schematic (paraxial rays)."])

# =========================================================== 3 Waves and sound
ch = p.chapter("waves", "Waves and sound", "Form 4 Physics — Wave properties, electromagnetic spectrum, sound (to be checked)")
wave = plot(0, 13, -3, 3, curves=[("2*sin(x)", "blue")], points=[(1.571, 2, "crest", "red"), (4.712, -2, "trough", "red")],
            segments=[(1.571, 2.6, 7.854, 2.6, "green", False, "wavelength"), (10.996, 0, 10.996, 2, "orange", False, "amplitude")], grid=1, xlabel="distance", ylabel="displacement", w=440, h=270)
em = flow(["Radio|waves", "Micro-|waves", "Infra-|red", "Visible|light", "Ultra-|violet", "X-rays", "Gamma|rays"], w=480, size=10, bh=44, gap=10)
assert abs(340 / 50 - 6.8) < 1e-9 and 2 * 0.5 == 1 and abs(3e8 / 1e8 - 3) < 1e-9 and abs(1 / 250 - 0.004) < 1e-12 and abs(3e8 / 5e-7 - 6e14) < 1
build(ch, "waves", "Wave properties and the electromagnetic spectrum", 30,
      ["Distinguish transverse from longitudinal waves and define amplitude, wavelength, frequency and period.", "Use v = f λ and T = 1/f.", "List the electromagnetic spectrum in order with uses."],
      [("key", "definition", "Waves",
        "A **wave** transfers **energy** from place to place without moving the material along. In a **transverse** wave the vibration is at **right angles** to the direction of travel (water ripples, light). In a **longitudinal** wave the vibration is **along** the direction of travel, forming compressions and rarefactions (sound). **Amplitude** is the maximum displacement; **wavelength λ** is the distance between two crests; **frequency f** is the number of waves per second (hertz, Hz); **period T = 1/f**."),
       ("fig", wave, "A transverse wave: crest, trough, amplitude and wavelength.", "A sine wave with a crest and a trough marked, a green arrow for the wavelength between two crests and an orange line for the amplitude."),
       ("key", "formule", "The wave equation",
        "**v = f × λ** (speed in m/s, frequency in Hz, wavelength in m). Speed depends on the medium; frequency is set by the source. Waves can be **reflected**, **refracted** (change of speed gives a change of direction) and **diffracted** (spread round obstacles and through gaps, most when the gap is about one wavelength). These can be shown in a **ripple tank**."),
       ("fig", em, "The electromagnetic spectrum from low to high frequency.", "Seven boxes in order: radio waves, microwaves, infrared, visible light, ultraviolet, X-rays and gamma rays."),
       ("key", "retenir", "Electromagnetic waves",
        "All electromagnetic waves are **transverse**, travel at **3 × 10⁸ m/s in a vacuum** and carry energy. Uses: **radio** (broadcasting), **microwaves** (cooking, mobile phones), **infrared** (remote controls, heaters), **light** (sight), **ultraviolet** (sterilising), **X-rays** (medical images), **gamma rays** (treating cancer). Too much UV, X-ray or gamma radiation is harmful to living cells."),
       ("key", "pieges", "Common mistakes",
        "- Do not confuse amplitude (height from the middle) with the crest-to-trough height, which is twice as large.\n- Frequency is waves per second, not seconds per wave (that is the period).\n- Sound is longitudinal and needs a medium; electromagnetic waves do not."),
       ("example", "Wave equation", "A sound wave in air has a frequency of 50 Hz and speed 340 m/s. Find its wavelength.", ["λ = v ÷ f.", "340 ÷ 50 = 6.8 m."], "6.8 m"),
       ("example", "Radio wave", "A radio station broadcasts at 100 MHz (1 × 10⁸ Hz). Find the wavelength (v = 3 × 10⁸ m/s).", ["λ = v ÷ f = 3 × 10⁸ ÷ 1 × 10⁸.", "= 3 m."], "3 m")],
      [("num", "A water wave has wavelength 2 m and frequency 0.5 Hz. Find its speed in m/s.", 1, "v = fλ = 0.5 × 2 = 1 m/s.", 0, "m/s"),
       ("num", "A tuning fork has frequency 250 Hz. Find its period in seconds.", 0.004, "T = 1 ÷ 250 = 0.004 s.", 0.0001, "s"),
       ("mcq", "Which of these is a longitudinal wave?", "sound", ["light", "radio waves", "ripples on water"], "Sound has compressions and rarefactions."),
       ("mcq", "Which electromagnetic wave has the highest frequency?", "gamma rays", ["radio waves", "infrared", "visible light"], "Gamma rays are at the end of the spectrum."),
       ("num", "Green light has a wavelength of about 5 × 10⁻⁷ m. Find its frequency in Hz. (v = 3 × 10⁸ m/s)", 6e14, "f = v ÷ λ = 3 × 10⁸ ÷ 5 × 10⁻⁷ = 6 × 10¹⁴ Hz.", 1e12, "Hz"),
       ("prob", "A ripple tank wave has frequency 8 Hz and wavelength 3.0 cm.", [("num", "Speed in cm/s.", 24, "v = 8 × 3.0 = 24 cm/s.", 0.1, "cm/s"), ("num", "Period in seconds.", 0.125, "1 ÷ 8 = 0.125 s.", 0.001, "s"), ("num", "Wavelength (cm) if the frequency is doubled but the speed stays 24 cm/s.", 1.5, "24 ÷ 16 = 1.5 cm.", 0.01, "cm")])],
      [("A wave carries:", "energy", ["matter only", "mass", "colour"], "It transfers energy."),
       ("The wave equation is:", "v = fλ", ["v = f + λ", "v = f ÷ λ", "v = λ ÷ f"], "Speed = frequency × wavelength."),
       ("The unit of frequency is the:", "hertz", ["metre", "newton", "joule"], "Hz = waves per second."),
       ("Which wave is NOT electromagnetic?", "sound", ["light", "X-rays", "radio waves"], "Sound is a mechanical wave."),
       ("All electromagnetic waves travel in a vacuum at:", "3 × 10⁸ m/s", ["340 m/s", "3 × 10⁵ m/s", "3 m/s"], "The speed of light.")],
      ["The electromagnetic spectrum order and uses are standard; check whether the syllabus asks for approximate wavelength ranges."])

def pitch_fig():
    d = Draw()
    d.text(240, 18, "A: low pitch, loud", size=12)
    d.sh.append(polyline([(30 + t, 60 - 30 * math.sin(2 * math.pi * t / 120)) for t in range(0, 421, 6)], color="blue", width=2.5))
    d.text(240, 118, "B: high pitch, quiet", size=12)
    d.sh.append(polyline([(30 + t, 165 - 12 * math.sin(2 * math.pi * t / 40)) for t in range(0, 421, 2)], color="red", width=2.5))
    return d.fig(480, 200)
echo_d = 340 * 0.4 / 2; thunder = 340 * 3; sonar = 1500 * 0.2 / 2
assert abs(echo_d - 68) < 1e-9 and thunder == 1020 and abs(sonar - 150) < 1e-9
build(ch, "sound", "Sound", 30,
      ["Describe how sound is produced and travels.", "Use speed = distance ÷ time for echoes and thunder, with v = 340 m/s in air.", "Relate pitch to frequency and loudness to amplitude."],
      [("key", "definition", "Sound",
        "Sound is made by **vibrating** objects and travels as a **longitudinal wave** of compressions and rarefactions. It needs a **medium** (solid, liquid or gas) and **cannot travel through a vacuum**. Approximate speeds: **air about 340 m/s**, water about 1500 m/s, steel about 5000 m/s: it is fastest in solids. Humans hear frequencies from about **20 Hz to 20 000 Hz**; above 20 000 Hz is **ultrasound**."),
       ("fig", pitch_fig(), "Two sounds on a trace: A is louder (bigger amplitude) and lower (fewer waves); B is quieter and higher pitched.", "Two wave traces: the first with large amplitude and long wavelength, the second with small amplitude and short wavelength."),
       ("key", "retenir", "Pitch, loudness and quality",
        "**Pitch** depends on **frequency**: higher frequency means higher pitch. **Loudness** depends on **amplitude**: bigger amplitude means louder. **Quality (timbre)** is the character of the sound that lets us tell a guitar from a drum even at the same pitch and loudness. Very loud noise can damage hearing."),
       ("key", "formule", "Echoes and speed",
        "An **echo** is a reflection of sound. If the sound travels to a wall and back in time t: **distance to the wall = v × t ÷ 2**. Thunder: the light arrives almost instantly, so **distance = 340 × time between flash and thunder**. **Sonar** uses ultrasound echoes to find the depth of the sea. Medical **ultrasound** scans use high-frequency sound."),
       ("key", "pieges", "Common mistakes",
        "- Divide by 2 for echoes: the sound travels there **and back**.\n- Sound is not faster in air than in solids; it is slower.\n- Loudness is not the same as pitch."),
       ("example", "Echo", "A boy claps and hears an echo from a cliff 0.40 s later. How far away is the cliff? (v = 340 m/s)", ["Total distance = 340 × 0.40 = 136 m (there and back).", "Distance to the cliff = 136 ÷ 2 = 68 m."], "68 m"),
       ("example", "Thunderstorm", "Thunder is heard 3 s after a lightning flash. How far away is the lightning? (v = 340 m/s)", ["Light arrives almost at once.", "Distance = 340 × 3 = 1020 m."], "About 1020 m (just over 1 km)")],
      [("num", "An echo from a cliff is heard 0.6 s after a shout. How far away is the cliff in m? (v = 340 m/s)", 102, "340 × 0.6 ÷ 2 = 102 m.", 0.5, "m"),
       ("num", "Thunder is heard 5 s after the flash. Find the distance (v = 340 m/s).", 1700, "340 × 5 = 1700 m.", 0, "m"),
       ("mcq", "A sound has a higher pitch. Its frequency is:", "higher", ["lower", "zero", "the same always"], "Pitch depends on frequency."),
       ("mcq", "Sound cannot travel through:", "a vacuum", ["water", "steel", "air"], "It needs a medium."),
       ("num", "A ship's sonar sends a pulse that returns after 0.2 s. The speed of sound in sea water is 1500 m/s. Find the depth of the sea in m.", 150, "1500 × 0.2 ÷ 2 = 150 m.", 0.5, "m"),
       ("prob", "A pupil stands 85 m from a tall wall and claps. (v = 340 m/s)", [("num", "Distance travelled by the sound to the wall and back (m).", 170, "85 × 2 = 170 m.", 0, "m"), ("num", "Time before she hears the echo (s).", 0.5, "170 ÷ 340 = 0.5 s.", 0.01, "s"), ("open", "She claps faster until each clap coincides with the echo of the previous one. Explain how she could use this to find the speed of sound.", "Count claps in a measured time to find the time between claps, which equals the echo time. Then speed = (2 × 85) ÷ that time.", ["1 mark: time between claps = echo time", "1 mark: speed = 2d ÷ t"], 2)])],
      [("Sound is a:", "longitudinal wave", ["transverse wave", "light wave", "radio wave"], "Compressions and rarefactions."),
       ("The speed of sound in air is about:", "340 m/s", ["3 × 10⁸ m/s", "34 m/s", "3400 m/s"], "At about room temperature."),
       ("Loudness depends on:", "amplitude", ["wavelength only", "speed", "colour"], "Larger amplitude, louder."),
       ("Humans can hear frequencies from about 20 Hz to:", "20 000 Hz", ["200 Hz", "2000 Hz", "2 000 000 Hz"], "The audible range."),
       ("In an echo calculation the time is multiplied by speed and then:", "divided by 2", ["multiplied by 2", "added to 2", "squared"], "Sound goes there and back.")],
      ["Speeds in water (1500 m/s) and steel (about 5000 m/s) are textbook approximations; ultrasound uses stated qualitatively; hearing safety is conservative advice."])

# =========================================================== 4 Electricity
ch = p.chapter("electrostatics", "Static electricity", "Form 4 Physics — Electric charge and electrostatics (to be checked)")
def scope_fig():
    d = Draw()
    d.sh.append(RECT(160, 80, 100, 110, fill="lightyellow", width=2))
    d.line(210, 38, 210, 125, width=5, color="grey"); d.sh.append(CIRCLE(210, 28, 14, fill="lightgrey", width=2))
    d.line(210, 125, 188, 172, width=3, color="orange"); d.line(210, 125, 232, 172, width=3, color="orange")
    d.sh += lab(224, 28, 300, 18, "metal cap", size=11)
    d.sh += lab(210, 70, 300, 62, "metal rod", size=11)
    d.sh += lab(232, 160, 300, 150, "gold leaf", size=11)
    d.sh += lab(160, 135, 90, 135, "glass case", size=11, anchor="end")
    d.text(210, 208, "like charges on rod and leaf: they repel", size=11)
    return d.fig(440, 220)
ne = 3.2e-9 / 1.6e-19; assert abs(ne - 2e10) < 1e3
build(ch, "static-electricity", "Static electricity", 25,
      ["Explain charging by friction in terms of the movement of electrons.", "State the laws of electric charges and describe the gold-leaf electroscope.", "Describe uses and dangers of static electricity."],
      [("key", "definition", "Electric charge",
        "Atoms contain **positive protons** and **negative electrons**; normally they are **neutral**. Rubbing two insulators together **transfers electrons**: the one that gains electrons becomes **negative**, the one that loses them becomes **positive**. A polythene rod rubbed with a cloth usually becomes negative; a perspex rod rubbed with silk usually becomes positive. Charge is measured in **coulombs (C)**; the charge of one electron is about **1.6 × 10⁻¹⁹ C**."),
       ("key", "propriete", "Laws of charges",
        "**Like charges repel; unlike charges attract.** A charged object also attracts small **uncharged** pieces (paper bits) because it induces opposite charge on the near side. **Conductors** (metals) let charge flow; **insulators** (plastic, glass, dry wood) do not. Charge is **conserved**: it is only moved, never created."),
       ("fig", scope_fig(), "A gold-leaf electroscope with a charged cap: the leaf rises.", "A glass case with a metal rod topped by a cap; at the bottom a gold leaf rises away from the rod; labels for cap, rod, leaf and case."),
       ("key", "methode", "Gold-leaf electroscope",
        "Charge on the cap spreads to the rod and leaf; as both carry the **same** charge the leaf is **repelled** and rises. Touching the cap with a finger **earths** it and the leaf falls. The electroscope shows whether a body is charged (and, with a known charge, the sign by induction), but not directly how much charge there is."),
       ("key", "retenir", "Uses and dangers",
        "Uses: **paint spraying** (charged droplets spread evenly), **photocopiers**, **dust removal** in chimneys. Dangers: sparks can ignite fuel vapour, so **fuel tankers are earthed**; **lightning** can kill, so tall buildings have a **lightning conductor** (a thick metal strip to earth). In a thunderstorm shelter inside a building and stay away from open ground, tall isolated trees and water."),
       ("key", "pieges", "Common mistakes",
        "- Only electrons move in solids; protons stay in the nucleus.\n- A body charged positively has **lost** electrons, it has not gained protons.\n- A charged rod attracts an uncharged object, so attraction alone does not prove an object is charged."),
       ("example", "Which sign?", "A cloth rubs a polythene rod and the rod becomes negative. What charge has the cloth and why?", ["Electrons moved from the cloth to the rod.", "The cloth lost electrons so it is positive, with equal and opposite charge."], "Positive, equal in size."),
       ("example", "Number of electrons", "A rod gains a charge of 3.2 × 10⁻⁹ C. How many electrons has it gained? (e = 1.6 × 10⁻¹⁹ C)", ["n = Q ÷ e.", "3.2 × 10⁻⁹ ÷ 1.6 × 10⁻¹⁹ = 2 × 10¹⁰ electrons."], "2 × 10¹⁰ electrons")],
      [("mcq", "Two negatively charged balloons are brought near each other. They:", "repel", ["attract", "do nothing", "swap charge"], "Like charges repel."),
       ("tf", "A body becomes positive by gaining protons.", False, "It becomes positive by losing electrons."),
       ("match", "Match each item with its role.", [("lightning conductor", "carries lightning current to earth"), ("earthing a fuel tanker", "prevents a dangerous spark"), ("gold-leaf electroscope", "detects charge"), ("paint sprayer", "uses charged droplets")], "Standard applications."),
       ("num", "How many electrons are needed to make 1.6 × 10⁻⁹ C of charge? (e = 1.6 × 10⁻¹⁹ C)", 1e10, "1.6 × 10⁻⁹ ÷ 1.6 × 10⁻¹⁹ = 10¹⁰.", 1e6),
       ("open", "Explain why a plastic ruler rubbed on dry hair can pick up small pieces of paper.", "Rubbing charges the ruler. It induces the opposite charge on the near side of the paper pieces, which are then attracted to it more strongly than the same charges on their far side are repelled.", ["1 mark: ruler charged by friction", "1 mark: induced opposite charge attracts the paper"]),
       ("prob", "A positively charged rod is brought near the cap of a neutral gold-leaf electroscope without touching.", [("mcq", "Electrons in the metal are:", "attracted up to the cap", ["pushed down to the leaf", "removed from the atom", "destroyed"], "Unlike charges attract."), ("mcq", "The leaf rises because the leaf and rod:", "gain the same (positive) charge", ["gain opposite charges", "become neutral", "melt"], "Electrons moved towards the cap, leaving the leaf positive."), ("open", "What happens when the rod is taken away?", "The electrons return and the leaf falls because the electroscope is neutral again.", ["1 mark for leaf falls"], 2)])],
      [("Rubbing a polythene rod with a cloth transfers:", "electrons", ["protons", "neutrons", "atoms"], "Electrons are the mobile charges."),
       ("The unit of electric charge is the:", "coulomb", ["ampere", "volt", "ohm"], "C."),
       ("Which of these cannot let charge flow easily?", "dry plastic", ["copper", "aluminium", "salt water"], "No free electrons."),
       ("A lightning conductor works by:", "carrying the charge safely to the ground", ["stopping the thunder", "attracting rain", "making light"], "Thick metal strip to earth."),
       ("Unlike charges:", "attract", ["repel", "disappear", "melt"], "Opposite charges.")],
      ["Which material becomes positive or negative depends on the pair; the stated pairs (polythene/cloth, perspex/silk) are the usual school ones.", "Thunderstorm safety is general advice; to be reviewed."])

ch = p.chapter("current", "Current electricity", "Form 4 Physics — Current, p.d., resistance, Ohm's law, power and domestic electricity (to be checked)")
def ohm_fig():
    d = circuit_loop(60, 70, 320, 170, top=[("res", 0.3), ("A", 0.75)], left=[("cell", 0.5)], bottom=[("sw", 0.5)])
    d.line(118, 70, 118, 36); d.line(118, 36, 138, 36)
    d.comp("V", 151, 36, "h"); d.line(164, 36, 178, 36); d.line(178, 36, 178, 70)
    d.text(138, 100, "R", size=12)
    d.text(225, 92, "ammeter", size=11); d.text(60, 125, "cell", size=11, anchor="end")
    d.text(190, 192, "switch", size=11)
    d.text(200, 28, "voltmeter across R", size=11, anchor="start")
    return d.fig(400, 205)
vi = plot(0, 3, 0, 6, segments=[(0, 0, 3, 6, "blue", False)], points=[(2, 4, "(2 A, 4 V)", "red")], grid=1, xlabel="I (A)", ylabel="V (V)", w=380, h=250)
I_ = 12 / 4; assert I_ == 3
Rser = 6 + 3; Iser = 12 / Rser; assert abs(Iser - 4 / 3) < 1e-12 and abs(Iser * 6 - 8) < 1e-9 and abs(Iser * 3 - 4) < 1e-9
Rpar = 1 / (1 / 6 + 1 / 3); assert abs(Rpar - 2) < 1e-12 and 12 / 6 + 12 / 3 == 6 and 12 / Rpar == 6
assert 2 * 30 == 60
build(ch, "ohm-circuits", "Current, potential difference, resistance and Ohm's law", 35,
      ["Define current, potential difference and resistance with their units.", "State and use Ohm's law V = IR and the graph of V against I.", "Combine resistors in series and in parallel."],
      [("key", "formule", "Current, p.d. and resistance",
        "**Current I = Q ÷ t** (charge per second): unit **ampere (A)** = 1 C/s, measured with an **ammeter in series**. **Potential difference (p.d.) V** is the energy transferred per coulomb: unit **volt (V)** = 1 J/C, measured with a **voltmeter in parallel**. **Resistance R = V ÷ I**, unit **ohm (Ω)**. **Ohm's law**: for a metal at constant temperature, **V = I R**: current is proportional to p.d."),
       ("fig", ohm_fig(), "Measuring current (ammeter in series) and p.d. (voltmeter across the resistor).", "A circuit with a cell, a resistor R and an ammeter in the top wire, an open switch at the bottom and a voltmeter connected across the resistor."),
       ("fig", vi, "V-I graph for a 2 Ω resistor: a straight line through the origin.", "A graph of V against I: a straight line from the origin through the point 2 A, 4 V; its gradient is the resistance, 2 ohms."),
       ("key", "propriete", "Series and parallel",
        "**Series**: same current everywhere; p.d.s add up to the supply; **R = R₁ + R₂**. **Parallel**: same p.d. across each branch; branch currents add; **1/R = 1/R₁ + 1/R₂** (the total is less than the smallest resistor). The resistance of a wire **increases with length**, **decreases with cross-sectional area**, and depends on the material; for a metal it rises with temperature."),
       ("key", "pieges", "Common mistakes",
        "- An ammeter has very low resistance and goes in **series**; a voltmeter has very high resistance and goes in **parallel**.\n- In parallel use 1/R, then invert the answer.\n- The V-I graph of a filament lamp is a curve: it does not obey Ohm's law because its temperature rises."),
       ("example", "Ohm's law", "A p.d. of 12 V is applied across a 4 Ω resistor. Find the current.", ["I = V ÷ R.", "12 ÷ 4 = 3 A."], "3 A"),
       ("example", "Two resistors", "A 6 Ω and a 3 Ω resistor are connected to a 12 V supply. Find the total resistance and the current when they are (a) in series and (b) in parallel.", ["Series: R = 6 + 3 = 9 Ω; I = 12 ÷ 9 = 1.33 A.", "Parallel: 1/R = 1/6 + 1/3 = 1/2, R = 2 Ω; I = 12 ÷ 2 = 6 A."], "Series 9 Ω, 1.33 A; parallel 2 Ω, 6 A")],
      [("num", "A current of 2 A flows for 30 s. How much charge passes? (C)", 60, "Q = I t = 2 × 30 = 60 C.", 0, "C"),
       ("num", "A resistor passes a current of 0.5 A when the p.d. across it is 6 V. Find its resistance in ohms.", 12, "R = V ÷ I = 6 ÷ 0.5 = 12 Ω.", 0, "Ω"),
       ("num", "Find the total resistance of 10 Ω and 15 Ω in series.", 25, "10 + 15 = 25 Ω.", 0, "Ω"),
       ("num", "Find the total resistance of 6 Ω and 12 Ω in parallel (Ω).", 4, "1/R = 1/6 + 1/12 = 3/12, R = 4 Ω.", 0.01, "Ω"),
       ("open", "Describe how to find the resistance of a resistor in the laboratory.", "Connect the resistor in series with an ammeter and a variable supply or rheostat, with a voltmeter across the resistor. Read V and I for several settings and calculate R = V ÷ I for each, or plot a graph of V against I and find the gradient.", ["1 mark: ammeter in series, voltmeter in parallel", "1 mark: several readings of V and I", "1 mark: R = V ÷ I or gradient"]),
       ("prob", "Two resistors of 6 Ω and 3 Ω are connected in series to a 12 V supply.", [("num", "Total resistance (Ω).", 9, "6 + 3 = 9 Ω.", 0, "Ω"), ("num", "Current in the circuit (A), to 2 d.p.", 1.33, "12 ÷ 9 = 1.33 A.", 0.01, "A"), ("num", "P.d. across the 6 Ω resistor (V), to 1 d.p.", 8.0, "V = I R = 1.33 × 6 = 8.0 V.", 0.1, "V")])],
      [("The SI unit for electric current is the:", "ampere", ["volt", "ohm", "joule"], "A."),
       ("Ohm's law is:", "V = IR", ["V = I ÷ R", "V = R ÷ I", "V = I + R"], "For a conductor at constant temperature."),
       ("An ammeter is connected:", "in series", ["in parallel", "across the supply", "through the earth"], "It measures the current through itself."),
       ("In a series circuit the current is:", "the same everywhere", ["largest at the cell", "zero", "different in each part"], "No branches."),
       ("The total resistance of resistors in parallel is:", "less than the smallest one", ["more than the largest", "equal to their sum", "zero"], "More paths for the current.")],
      ["Units written as A, V, Ω; formulae V = IR, Q = It standard. Check the syllabus for resistivity (ρ = RA/L), which is not included here."])

def mains_fig():
    d = Draw()
    d.wire(70, 60, 340, 60, [("fuse", 0.25), ("sw", 0.55)]); d.wire(340, 60, 340, 170, [("bulb", 0.5)]); d.wire(340, 170, 70, 170); d.wire(70, 170, 70, 60)
    d.text(70, 40, "live", size=12, anchor="start"); d.text(70, 192, "neutral", size=12, anchor="start")
    d.text(130, 90, "fuse", size=11); d.text(230, 90, "switch", size=11)
    d.text(200, 118, "mains supply →", size=11, anchor="middle")
    d.text(300, 118, "lamp", size=11, anchor="end")
    d.sh.append(RECT(30, 100, 20, 40, fill="lightblue", width=2)); d.text(40, 125, "~", size=18)
    return d.fig(400, 205)
E_ = 1.5 * 2; assert E_ == 3 and 3 * 100 == 300
I_bulb = 60 / 220; I_h = 2200 / 220; assert abs(I_bulb - 0.2727) < 1e-3 and I_h == 10
assert 1000 * 3600 == 3.6e6
build(ch, "power-domestic", "Electrical power, energy and domestic electricity", 30,
      ["Calculate electrical power P = VI and energy E = Pt.", "Calculate the energy in kWh and its cost.", "Describe fuses, earthing and safe use of mains electricity."],
      [("key", "formule", "Power and energy",
        "**Electrical power P = V × I**, also **P = I² R = V² ÷ R**; unit the **watt (W)**. **Energy E = P × t**: in joules if P is in W and t in s. The household unit is the **kilowatt-hour (kWh)**: **1 kWh = 1 kW × 1 h = 3.6 × 10⁶ J**. **Cost = number of kWh × price per kWh**. The mains in Cameroon is about 220 V a.c. (alternating current, 50 Hz)."),
       ("fig", mains_fig(), "A mains circuit: the fuse and switch are in the live wire.", "A circuit with a source on the left, a fuse and a switch in the top (live) wire, a lamp on the right and the neutral wire along the bottom."),
       ("key", "retenir", "Fuses, earthing and safety",
        "A **fuse** is a thin wire that **melts** and breaks the circuit if the current is too large; it goes in the **live** wire with a rating **just above** the normal current. The **earth wire** joins the metal case of an appliance to the ground so that a fault current flows to earth and blows the fuse instead of shocking you. **Switches** go in the live wire. A **circuit breaker** does the same job as a fuse and can be reset."),
       ("key", "attention", "Safety at home",
        "Do not touch switches, plugs or wires with wet hands; do not overload sockets with many appliances; replace damaged cables; never push objects into sockets; keep children away; switch off before repairs; and let a **qualified electrician** do any mains wiring. Wire colour codes vary between wiring systems: always follow the local code."),
       ("key", "pieges", "Common mistakes",
        "- Convert to kW and hours for kWh: 1500 W for 2 h = 1.5 kW × 2 h = 3 kWh, not 3000.\n- A fuse protects the **wiring** from overheating; it does not make an appliance safe by itself.\n- A fuse in the neutral wire would leave the appliance live when it blows."),
       ("example", "Cost of using an iron", "An iron rated 1500 W is used for 2 hours. How many kWh are used and what is the cost at an assumed price of 100 FCFA per kWh?", ["Energy = 1.5 kW × 2 h = 3 kWh.", "Cost = 3 × 100 = 300 FCFA (illustrative price)."], "3 kWh; 300 FCFA"),
       ("example", "Current and fuse", "A 2200 W heater runs on 220 V. Find the current and suggest a suitable fuse from 5 A, 10 A and 13 A.", ["I = P ÷ V = 2200 ÷ 220 = 10 A.", "A 10 A fuse would be at the limit; a 13 A fuse is just above the normal current."], "10 A; use a 13 A fuse")],
      [("num", "A lamp is rated 60 W on a 220 V supply. Find the current in A (2 d.p.).", 0.27, "I = 60 ÷ 220 = 0.27 A.", 0.01, "A"),
       ("num", "A 100 W bulb is on for 10 hours. How many kWh are used?", 1, "0.1 kW × 10 h = 1 kWh.", 0, "kWh"),
       ("mcq", "The fuse of an appliance should be placed in the:", "live wire", ["neutral wire", "earth wire", "case"], "So that the appliance is dead when it blows."),
       ("num", "At an assumed price of 100 FCFA per kWh, what is the cost of running a 2 kW heater for 3 hours? (FCFA)", 600, "2 × 3 = 6 kWh; 6 × 100 = 600 FCFA.", 0, "FCFA"),
       ("open", "Explain how the earth wire protects a user of a metal-cased appliance if the live wire touches the case.", "The fault current flows through the earth wire to the ground, which is a large current that melts the fuse and cuts off the supply, so the case does not stay live.", ["1 mark: current flows to earth", "1 mark: large current blows fuse/trips breaker"]),
       ("prob", "A kettle is rated 2000 W, 220 V, and is used for 6 minutes each day. Assume a price of 100 FCFA per kWh (illustrative).", [("num", "Energy used each day in kWh.", 0.2, "2 kW × 0.1 h = 0.2 kWh.", 0.001, "kWh"), ("num", "Cost for 30 days in FCFA.", 600, "0.2 × 30 × 100 = 600 FCFA.", 0, "FCFA"), ("num", "Current drawn in A (to 1 d.p.).", 9.1, "2000 ÷ 220 = 9.09 A.", 0.1, "A")])],
      [("Electrical power equals:", "V × I", ["V ÷ I", "V + I", "I ÷ V"], "P = VI."),
       ("1 kWh is equal to:", "3.6 × 10⁶ J", ["3600 J", "1000 J", "36 J"], "1000 W × 3600 s."),
       ("A fuse works by:", "melting when the current is too large", ["storing charge", "reducing voltage", "making light"], "It breaks the circuit."),
       ("The earth wire is connected to the:", "metal case of the appliance", ["bulb filament", "switch contact", "battery"], "To carry fault current to earth."),
       ("A safe action with electricity is to:", "dry your hands before touching a switch", ["use a wet cloth on a socket", "overload a socket", "poke a wire into a socket"], "Water conducts electricity.")],
      ["Mains voltage (220 V) and the price of electricity are assumed values; the tariff is illustrative and must not be taken as the real price.", "Fuse sizes (5, 10, 13 A) follow a common school example; check the standard in use locally.", "Safety advice general, to be reviewed by a qualified electrician."])

ch = p.chapter("magnetism", "Electromagnetism", "Form 4 Physics — Magnetic effect of a current, motor effect, induction, transformers (to be checked)")
def motor_fig():
    d = Draw()
    d.sh.append(RECT(50, 50, 60, 120, fill="red", width=2)); d.text(80, 118, "N", size=18, bold=True, color="white")
    d.sh.append(RECT(330, 50, 60, 120, fill="blue", width=2)); d.text(360, 118, "S", size=18, bold=True, color="white")
    for y in (80, 110, 140): d.line(115, y, 325, y, arrow="end", color="grey", width=1.5)
    d.sh.append(CIRCLE(220, 110, 12, fill="white", width=2)); d.sh.append(CIRCLE(220, 110, 3, fill="ink", width=1))
    d.line(220, 95, 220, 30, arrow="end", color="green", width=3)
    d.text(232, 42, "force", size=12, anchor="start"); d.text(232, 128, "current out of the page", size=11, anchor="start")
    d.text(220, 195, "magnetic field from N to S", size=11)
    return d.fig(440, 210)
def trans_fig():
    d = Draw()
    d.sh.append(RECT(100, 40, 240, 160, fill="lightgrey", width=2)); d.sh.append(RECT(140, 80, 160, 80, fill="white", width=2))
    for k in range(8):
        y = 60 + k * 10
        d.line(100, y, 140, y + 5, width=2.5, color="red"); d.line(300, y, 340, y + 5, width=2.5, color="blue")
    d.sh += lab(100, 100, 40, 100, "Np", anchor="end", size=12)
    d.sh += lab(340, 100, 400, 100, "Ns", size=12)
    d.text(220, 22, "iron core", size=11); d.text(220, 125, "alternating magnetic field", size=10)
    d.text(60, 130, "primary", size=11, anchor="middle"); d.text(380, 130, "secondary", size=11, anchor="middle")
    return d.fig(440, 215)
Ns_ = 220 * 50 / 500
assert abs(Ns_ - 22) < 1e-9
Vs2 = 12 * 1000 / 100; assert Vs2 == 120
Ip = (22 * 2) / 220; assert abs(Ip - 0.2) < 1e-12
build(ch, "electromagnetism", "Electromagnetism, motors and transformers", 35,
      ["Describe the magnetic field of a current-carrying wire and solenoid and use of electromagnets.", "Describe the motor effect and Fleming's left-hand rule.", "Describe electromagnetic induction and use the transformer equation."],
      [("key", "definition", "Magnetic effect of a current",
        "A **current in a wire produces a magnetic field** in circles around the wire (right-hand grip rule: thumb along the current, fingers show the field). A **coil (solenoid)** gives a field like a bar magnet. An **electromagnet** has a soft-iron core in a coil: it is stronger with **more turns**, a **larger current** and an **iron core**, and it can be switched off. Uses: cranes lifting scrap iron, electric bells, relays, loudspeakers."),
       ("fig", motor_fig(), "A wire carrying a current across a magnetic field feels a force (here upwards).", "A magnet with N on the left and S on the right, field arrows pointing to the right, and a wire with current out of the page with a green force arrow pointing up."),
       ("key", "propriete", "The motor effect",
        "A **current-carrying wire in a magnetic field experiences a force**, at right angles to both. **Fleming's left-hand rule**: **first finger = Field** (N to S), **seCond finger = Current**, **thuMb = Motion** (force). The force is larger for a bigger current, a stronger field or a longer wire. A **d.c. motor** has a coil in a field, a **commutator** that reverses the current every half turn and **brushes**, so the coil keeps turning."),
       ("key", "propriete", "Electromagnetic induction",
        "A **voltage (e.m.f.) is induced** when a magnet moves into or out of a coil, or when a wire cuts magnetic field lines: it is larger for a **faster** movement, a **stronger** magnet and **more turns**. No movement gives no e.m.f. A **generator (dynamo)** turns a coil in a field and gives **alternating current**. A **transformer** has two coils on an iron core: **Vs ÷ Vp = Ns ÷ Np**. A **step-up** transformer has Ns > Np; **step-down** Ns < Np. Transformers only work with **a.c.**"),
       ("fig", trans_fig(), "A transformer: two coils on a laminated iron core.", "A rectangular iron core with a primary coil of Np turns on the left limb and a secondary coil of Ns turns on the right limb."),
       ("key", "pieges", "Common mistakes",
        "- A transformer does not work on d.c.: a changing field is needed.\n- It cannot increase power: for an ideal transformer Vp Ip = Vs Is, so a higher voltage means a lower current.\n- The thumb in Fleming's left-hand rule shows the **force**, not the current."),
       ("example", "Step-down transformer", "A transformer has 500 turns on the primary and 50 turns on the secondary. The primary is connected to 220 V a.c. Find the secondary voltage.", ["Vs ÷ Vp = Ns ÷ Np, so Vs = 220 × 50 ÷ 500.", "= 22 V."], "22 V"),
       ("example", "Current in the primary", "The secondary of that ideal transformer supplies 2 A at 22 V. Find the current in the primary.", ["Power out = 22 × 2 = 44 W; ideal: power in = 44 W.", "Ip = 44 ÷ 220 = 0.2 A."], "0.2 A")],
      [("mcq", "Which change makes an electromagnet stronger?", "more turns of wire", ["a shorter wire", "a smaller current", "removing the iron core"], "More turns, more current and an iron core all strengthen it."),
       ("mcq", "In Fleming's left-hand rule the thumb shows the direction of:", "the force (motion)", ["the current", "the field", "the voltage"], "ThuMb = Motion."),
       ("num", "A transformer has 100 turns on the primary and 1000 turns on the secondary. The primary voltage is 12 V a.c. Find the secondary voltage in V.", 120, "Vs = 12 × 1000 ÷ 100 = 120 V.", 0, "V"),
       ("tf", "A transformer can step up the voltage of a battery (d.c.).", False, "It needs a changing (a.c.) supply."),
       ("open", "Describe how an e.m.f. can be induced in a coil using a bar magnet and state three ways to increase it.", "Push the magnet in and out of the coil: a voltage is induced while it moves. It is increased by moving the magnet faster, using a stronger magnet, or using a coil with more turns.", ["1 mark: moving magnet and coil", "2 marks: any two ways to increase it"]),
       ("prob", "A transformer in a phone charger changes 220 V a.c. to 11 V a.c. The primary coil has 2000 turns.", [("num", "Number of turns on the secondary coil.", 100, "Ns = Np × Vs ÷ Vp = 2000 × 11 ÷ 220 = 100.", 0), ("mcq", "This is a:", "step-down transformer", ["step-up transformer", "d.c. motor", "dynamo"], "Voltage is reduced."), ("num", "If the secondary current is 1 A (ideal), find the primary current in A.", 0.05, "Power out = 11 W; Ip = 11 ÷ 220 = 0.05 A.", 0.001, "A")])],
      [("A current in a straight wire produces:", "a magnetic field around it", ["a gravitational field", "light only", "no effect"], "Oersted's discovery."),
       ("A device that changes the size of an a.c. voltage is a:", "transformer", ["dynamo", "switch", "fuse"], "Two coils on an iron core."),
       ("The commutator in a d.c. motor:", "reverses the current every half turn", ["stores charge", "cools the coil", "makes light"], "It keeps the turning force in one direction."),
       ("The transformer equation is:", "Vs ÷ Vp = Ns ÷ Np", ["Vs × Vp = Ns × Np", "Vs + Vp = Ns + Np", "Vs ÷ Vp = Np ÷ Ns"], "Voltage ratio equals turns ratio."),
       ("An e.m.f. is induced in a coil when:", "a magnet moves in or out of it", ["a magnet is at rest in it", "the coil is cold", "it is switched off"], "A changing field is needed.")],
      ["Right-hand grip rule and Fleming's left-hand rule are standard; check which hand rules the syllabus expects (conventional current assumed).", "Transformers are treated as ideal."])

p.write()
